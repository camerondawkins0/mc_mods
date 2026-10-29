package com.slfrontier.crafting;

import com.slfrontier.block.CraftingStationBlock;
import com.slfrontier.data.PlayerData;
import com.slfrontier.entity.npc.FrontierNpc;
import com.slfrontier.network.CraftingNetwork;
import com.slfrontier.registry.ModAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Server side of Frontier crafting. Opening a station (a block or an NPC smith) starts a
 * session; a craft request is checked against that session, runs for the recipe's time
 * with sounds and particles at the station, and is checked again before the materials are
 * taken and the result is given. Nothing the client says is trusted.
 */
public final class FrontierCrafting {
    /** Most crafts one request can batch. */
    public static final int MAX_BATCH = 32;
    public static final double BLOCK_RANGE = 8;
    public static final double NPC_RANGE = 10;

    /**
     * Where a player is crafting: with an NPC ({@code npcEntity}), at a station block, or (no
     * block, no NPC) at a spot they must stay near.
     */
    private record Session(Station station, ResourceKey<Level> dimension, @Nullable BlockPos pos, boolean block, int npcEntity,
                           String npcId) {
    }

    private static final class Job {
        final Session session;
        final FrontierRecipe recipe;
        final int quantity;
        final int total;
        int elapsed;

        Job(Session session, FrontierRecipe recipe, int quantity, int total) {
            this.session = session;
            this.recipe = recipe;
            this.quantity = quantity;
            this.total = total;
        }
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final Map<UUID, Job> JOBS = new HashMap<>();

    private FrontierCrafting() {
    }

    // ---- Opening ---------------------------------------------------------------------

    /**
     * Opens the crafting screen for a station run by {@code npc} (a smith). Without an NPC the
     * station is wherever the player stands now, and they must stay close to that spot.
     */
    public static void open(ServerPlayer player, Station station, @Nullable Entity npc) {
        if (npc == null) {
            BlockPos here = player.blockPosition();
            start(player, new Session(station, player.level().dimension(), here, false, -1, ""), here);
            return;
        }
        String npcId = npc instanceof FrontierNpc f ? f.npcId() : BuiltInRegistries.ENTITY_TYPE.getKey(npc.getType()).getPath();
        start(player, new Session(station, player.level().dimension(), null, false, npc.getId(), npcId), npc.blockPosition());
    }

    /** Opens the crafting screen for the station block at {@code pos}. */
    public static void openAt(ServerPlayer player, Station station, BlockPos pos) {
        start(player, new Session(station, player.level().dimension(), pos.immutable(), true, -1, ""), pos);
    }

    /** Opens a station run by the nearest NPC with this id (e.g. "bilac"), if one is close enough. */
    public static void openNear(ServerPlayer player, Station station, String npcId) {
        player.level().getEntitiesOfClass(FrontierNpc.class, player.getBoundingBox().inflate(NPC_RANGE), e -> e.npcId().equals(npcId))
                .stream().min((a, b) -> Double.compare(a.distanceToSqr(player), b.distanceToSqr(player)))
                .ifPresent(npc -> open(player, station, npc));
    }

    private static void start(ServerPlayer player, Session session, BlockPos anchor) {
        if (JOBS.containsKey(player.getUUID())) cancel(player);
        SESSIONS.put(player.getUUID(), session);
        PacketDistributor.sendToPlayer(player, new CraftingNetwork.OpenCrafting(session.station().ordinal(), session.npcEntity(),
                session.npcId(), anchor));
    }

    // ---- Requests --------------------------------------------------------------------

    public static void request(ServerPlayer player, ResourceLocation id, int quantity) {
        if (JOBS.containsKey(player.getUUID())) {
            fail(player, id, "message.slfrontier.craft.busy");
            return;
        }
        Session session = SESSIONS.get(player.getUUID());
        FrontierRecipe recipe = FrontierRecipes.get(id);
        String problem = check(player, session, recipe);
        if (problem != null) {
            fail(player, id, problem);
            return;
        }
        int qty = Math.min(Math.max(1, quantity), recipe.maxCrafts(player, MAX_BATCH));
        if (qty <= 0) {
            fail(player, id, "message.slfrontier.craft.missing");
            return;
        }
        int total = recipe.batchTicks(qty);
        JOBS.put(player.getUUID(), new Job(session, recipe, qty, total));
        PacketDistributor.sendToPlayer(player, new CraftingNetwork.CraftStatus(CraftingNetwork.CraftStatus.STARTED, id, qty, total, ""));
        Vec3 at = stationPos(player.serverLevel(), session, player);
        if (session.station() == Station.ALCHEMY) {
            player.serverLevel().playSound(null, at.x, at.y, at.z, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 0.7f, 1.1f);
        }
    }

    public static void cancel(ServerPlayer player) {
        Job job = JOBS.remove(player.getUUID());
        if (job != null) {
            PacketDistributor.sendToPlayer(player, new CraftingNetwork.CraftStatus(CraftingNetwork.CraftStatus.CANCELLED,
                    job.recipe.id(), job.quantity, 0, "message.slfrontier.craft.cancelled"));
        }
    }

    public static void forget(ServerPlayer player) {
        JOBS.remove(player.getUUID());
        SESSIONS.remove(player.getUUID());
    }

    /**
     * Everything a craft needs except materials: an open station the player is still at, a
     * known recipe for that station, and the level and story progress it asks for.
     *
     * @return a message key ({@code key|arg|@argKey}) describing the problem, or null
     */
    @Nullable
    private static String check(ServerPlayer player, @Nullable Session session, @Nullable FrontierRecipe recipe) {
        if (session == null || !stillValid(player, session)) return "message.slfrontier.craft.too_far";
        if (recipe == null) return "message.slfrontier.craft.unknown";
        PlayerData data = ModAttachments.get(player);
        return switch (recipe.lock(data, session.station(), session.npcId())) {
            case NONE -> null;
            case STATION -> "gui.slfrontier.craft.lock.station|@" + recipe.station().nameKey();
            case NPC -> "gui.slfrontier.craft.lock.npc|@entity.slfrontier." + recipe.npc();
            case STAGE -> "gui.slfrontier.craft.lock.stage|" + roman(recipe.requiresStage().act);
            case LEVEL -> "gui.slfrontier.craft.lock.level|" + recipe.minLevel();
        };
    }

    public static String roman(int act) {
        return switch (act) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            default -> String.valueOf(act);
        };
    }

    /** The player is still at the station block, or next to the NPC who runs it. */
    private static boolean stillValid(ServerPlayer player, Session session) {
        if (!player.isAlive() || player.level().dimension() != session.dimension()) return false;
        Level level = player.level();
        if (session.pos() != null) {
            boolean near = player.distanceToSqr(Vec3.atCenterOf(session.pos())) <= BLOCK_RANGE * BLOCK_RANGE;
            return near && (!session.block() || level.getBlockState(session.pos()).getBlock() instanceof CraftingStationBlock block
                    && block.station() == session.station());
        }
        Entity npc = level.getEntity(session.npcEntity());
        return npc instanceof FrontierNpc f && f.isAlive() && f.npcId().equals(session.npcId())
                && npc.distanceToSqr(player) <= NPC_RANGE * NPC_RANGE;
    }

    // ---- Ticking ---------------------------------------------------------------------

    public static void tick(MinecraftServer server) {
        if (JOBS.isEmpty()) return;
        Iterator<Map.Entry<UUID, Job>> it = JOBS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Job> e = it.next();
            Job job = e.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
            if (player == null) {
                it.remove();
                continue;
            }
            job.elapsed++;
            if (job.elapsed % 10 == 0 && !stillValid(player, job.session)) {
                it.remove();
                fail(player, job.recipe.id(), "message.slfrontier.craft.too_far");
                continue;
            }
            workFx(player, job);
            if (job.elapsed >= job.total) {
                it.remove();
                finish(player, job);
            }
        }
    }

    private static void finish(ServerPlayer player, Job job) {
        // Re-check everything: the recipe may have been reloaded and the inventory may have changed.
        FrontierRecipe recipe = FrontierRecipes.get(job.recipe.id());
        String problem = check(player, job.session, recipe);
        if (problem != null) {
            fail(player, job.recipe.id(), problem);
            return;
        }
        int qty = Math.min(job.quantity, recipe.maxCrafts(player, MAX_BATCH));
        if (qty <= 0) {
            fail(player, recipe.id(), "message.slfrontier.craft.missing");
            return;
        }
        recipe.consume(player, qty);
        int count = recipe.result().getCount() * qty;
        for (int left = count; left > 0; ) {
            ItemStack stack = recipe.result().copyWithCount(Math.min(left, recipe.result().getMaxStackSize()));
            left -= stack.getCount();
            if (!player.getInventory().add(stack)) player.drop(stack, false);
        }
        player.containerMenu.broadcastChanges();
        doneFx(player, job.session);
        String message = "message.slfrontier.craft.done|" + count + "|@" + recipe.result().getDescriptionId();
        PacketDistributor.sendToPlayer(player, new CraftingNetwork.CraftStatus(CraftingNetwork.CraftStatus.DONE, recipe.id(), qty, 0, message));
        player.displayClientMessage(Component.translatable("message.slfrontier.craft.done", count, recipe.result().getHoverName()), true);
    }

    private static void fail(ServerPlayer player, ResourceLocation id, String message) {
        PacketDistributor.sendToPlayer(player, new CraftingNetwork.CraftStatus(CraftingNetwork.CraftStatus.FAILED, id, 0, 0, message));
    }

    // ---- Effects ---------------------------------------------------------------------

    /** Top of the station block, or the NPC's hands. */
    private static Vec3 stationPos(ServerLevel level, Session session, ServerPlayer player) {
        if (session.pos() != null) return Vec3.atCenterOf(session.pos()).add(0, 0.55, 0);
        Entity npc = level.getEntity(session.npcEntity());
        if (npc == null) return player.position();
        Vec3 look = npc.getLookAngle();
        return npc.position().add(look.x * 0.6, npc.getBbHeight() * 0.55, look.z * 0.6);
    }

    private static void workFx(ServerPlayer player, Job job) {
        ServerLevel level = player.serverLevel();
        Vec3 at = stationPos(level, job.session, player);
        var rnd = level.random;
        switch (job.session.station()) {
            case FORGE -> {
                if (job.elapsed % 8 == 4) {
                    sound(level, at, SoundEvents.ANVIL_USE, 0.25f, 1.3f + rnd.nextFloat() * 0.4f);
                    particles(level, ParticleTypes.CRIT, at, 6, 0.15, 0.25);
                    particles(level, ParticleTypes.FLAME, at, 2, 0.2, 0.02);
                }
                if (job.elapsed % 16 == 0) particles(level, ParticleTypes.LAVA, at, 1, 0.1, 0);
                if (job.elapsed % 6 == 0) particles(level, ParticleTypes.SMOKE, at, 1, 0.2, 0.02);
            }
            case ALCHEMY -> {
                if (job.elapsed % 5 == 0) {
                    sound(level, at, SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, 0.5f, 0.8f + rnd.nextFloat() * 0.6f);
                    particles(level, ParticleTypes.BUBBLE_POP, at, 3, 0.18, 0.02);
                }
                if (job.elapsed % 7 == 0) particles(level, ParticleTypes.WITCH, at, 2, 0.2, 0.01);
                if (job.elapsed % 20 == 10) sound(level, at, SoundEvents.BREWING_STAND_BREW, 0.35f, 1.4f);
            }
            case WORKBENCH -> {
                if (job.elapsed % 9 == 3) {
                    sound(level, at, rnd.nextBoolean() ? SoundEvents.VILLAGER_WORK_FLETCHER : SoundEvents.WOOD_HIT, 0.5f,
                            0.9f + rnd.nextFloat() * 0.3f);
                    particles(level, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.OAK_PLANKS.defaultBlockState()), at, 4, 0.2, 0.05);
                }
            }
        }
    }

    private static void doneFx(ServerPlayer player, Session session) {
        ServerLevel level = player.serverLevel();
        Vec3 at = stationPos(level, session, player);
        switch (session.station()) {
            case FORGE -> {
                sound(level, at, SoundEvents.ANVIL_USE, 0.6f, 1.0f);
                sound(level, at, SoundEvents.SMITHING_TABLE_USE, 0.8f, 1.0f);
                particles(level, ParticleTypes.LAVA, at, 4, 0.2, 0);
                particles(level, ParticleTypes.CRIT, at, 14, 0.3, 0.35);
            }
            case ALCHEMY -> {
                sound(level, at, SoundEvents.BOTTLE_FILL, 0.8f, 1.0f);
                sound(level, at, SoundEvents.AMETHYST_BLOCK_CHIME, 0.9f, 1.3f);
                particles(level, ParticleTypes.WITCH, at, 12, 0.3, 0.05);
                particles(level, ParticleTypes.HAPPY_VILLAGER, at, 6, 0.3, 0.02);
            }
            case WORKBENCH -> {
                sound(level, at, SoundEvents.VILLAGER_WORK_TOOLSMITH, 0.8f, 1.1f);
                particles(level, ParticleTypes.CRIT, at, 10, 0.3, 0.2);
            }
        }
        player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.6f, 1.3f);
    }

    private static void sound(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.BLOCKS, volume, pitch);
    }

    private static void particles(ServerLevel level, ParticleOptions type, Vec3 at, int count, double spread, double speed) {
        level.sendParticles(type, at.x, at.y, at.z, count, spread, spread * 0.5, spread, speed);
    }
}

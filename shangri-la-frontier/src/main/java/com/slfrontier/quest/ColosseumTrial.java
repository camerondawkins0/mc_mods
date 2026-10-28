package com.slfrontier.quest;

import com.slfrontier.fx.Fx;
import com.slfrontier.network.ModNetwork;
import com.slfrontier.registry.ModEntities;
import com.slfrontier.world.Frontier;
import com.slfrontier.world.Landmarks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The Vorpal Colosseum trial in Rabituza: three rounds in the sand ring, ending with the
 * Aberrant Woodmage. Leaving the arena, logging out or dying forfeits the trial (it can
 * simply be started again).
 */
public final class ColosseumTrial {
    private static final double ARENA_RADIUS = 30;
    private static final int BREAK_SECONDS = 5;

    private static final List<List<Supplier<? extends EntityType<? extends Mob>>>> ROUNDS = List.of(
            List.of(ModEntities.GOBLIN, ModEntities.GOBLIN, ModEntities.GOBLIN, ModEntities.VORPAL_BUNNY),
            List.of(ModEntities.REDCAP_GOBLIN, ModEntities.REDCAP_GOBLIN, ModEntities.GOBLIN, ModEntities.GOBLIN),
            List.of(ModEntities.WOODMAGE));

    private static final class Trial {
        final UUID player;
        final BlockPos centre;
        int round = -1;
        int countdown = 3;
        final List<UUID> foes = new ArrayList<>();

        Trial(UUID player, BlockPos centre) {
            this.player = player;
            this.centre = centre;
        }
    }

    private static final Map<UUID, Trial> ACTIVE = new HashMap<>();

    private ColosseumTrial() {
    }

    public static boolean inTrial(ServerPlayer player) {
        return ACTIVE.containsKey(player.getUUID());
    }

    public static void start(ServerPlayer player) {
        if (ACTIVE.containsKey(player.getUUID()) || !Frontier.isIn(player)) return;
        ServerLevel level = player.serverLevel();
        BlockPos centre = Landmarks.marker(level, Landmarks.Site.RABITUZA, "arena");
        if (centre == null) centre = Landmarks.ensure(level, Landmarks.Site.RABITUZA);
        Trial trial = new Trial(player.getUUID(), centre);
        ACTIVE.put(player.getUUID(), trial);
        player.teleportTo(level, centre.getX() + 0.5, centre.getY(), centre.getZ() + 6.5, 180f, 0f);
        level.playSound(null, centre, SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 2f, 1.2f);
        ModNetwork.announce(player, "announce.slfrontier.colosseum_start", "announce.slfrontier.colosseum_start.sub", 0xFFD34F);
    }

    /** Runs once a second. */
    public static void tick(MinecraftServer server) {
        ACTIVE.values().removeIf(trial -> !step(server, trial));
    }

    /** @return false once the trial is over (won or forfeited). */
    private static boolean step(MinecraftServer server, Trial trial) {
        ServerPlayer player = server.getPlayerList().getPlayer(trial.player);
        ServerLevel level = server.getLevel(Frontier.LEVEL);
        if (level == null) return false;
        if (player == null || !player.isAlive() || player.level() != level
                || player.position().distanceTo(Vec3.atBottomCenterOf(trial.centre)) > ARENA_RADIUS) {
            forfeit(level, trial, player);
            return false;
        }
        if (trial.round >= 0) {
            trial.foes.removeIf(id -> {
                Entity e = level.getEntity(id);
                return e == null || !e.isAlive();
            });
            if (!trial.foes.isEmpty()) return true;
            // Round cleared.
            if (trial.round == ROUNDS.size() - 1) {
                level.playSound(null, trial.centre, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.5f, 1f);
                Fx.pillar(level, Vec3.atBottomCenterOf(trial.centre), 3f, 18f, 0xFFFFD34F, 40);
                Story.onColosseumCleared(player);
                return false;
            }
            if (trial.countdown == BREAK_SECONDS) {
                player.displayClientMessage(Component.translatable("message.slfrontier.colosseum.round_clear", trial.round + 1)
                        .withStyle(ChatFormatting.GOLD), true);
            }
        }
        if (--trial.countdown > 0) return true;
        trial.round++;
        trial.countdown = BREAK_SECONDS;
        spawnRound(level, trial, player);
        return true;
    }

    private static void spawnRound(ServerLevel level, Trial trial, ServerPlayer player) {
        List<Supplier<? extends EntityType<? extends Mob>>> round = ROUNDS.get(trial.round);
        boolean last = trial.round == ROUNDS.size() - 1;
        for (int i = 0; i < round.size(); i++) {
            Mob mob = round.get(i).get().create(level);
            if (mob == null) continue;
            double a = Math.PI * 2 * i / round.size();
            double r = last ? 0 : 8;
            mob.moveTo(trial.centre.getX() + 0.5 + Math.cos(a) * r, trial.centre.getY(), trial.centre.getZ() + 0.5 - 6 + Math.sin(a) * r,
                    0, 0);
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(trial.centre), MobSpawnType.EVENT, null);
            mob.setTarget(player);
            mob.setPersistenceRequired();
            level.addFreshEntity(mob);
            level.sendParticles(ParticleTypes.CLOUD, mob.getX(), mob.getY() + 0.5, mob.getZ(), 12, 0.3, 0.3, 0.3, 0.02);
            trial.foes.add(mob.getUUID());
        }
        level.playSound(null, trial.centre, SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 1.5f, last ? 0.7f : 1.1f);
        ModNetwork.announce(player, last ? "announce.slfrontier.colosseum_final" : "announce.slfrontier.colosseum_round",
                last ? "entity.slfrontier.woodmage" : "announce.slfrontier.colosseum_round.sub", 0xFFD34F);
        player.displayClientMessage(Component.translatable("message.slfrontier.colosseum.round", trial.round + 1, ROUNDS.size())
                .withStyle(ChatFormatting.YELLOW), true);
    }

    private static void forfeit(ServerLevel level, Trial trial, ServerPlayer player) {
        for (UUID id : trial.foes) {
            Entity e = level.getEntity(id);
            if (e != null) e.discard();
        }
        if (player != null) {
            player.sendSystemMessage(Component.translatable("message.slfrontier.colosseum.forfeit").withStyle(ChatFormatting.GRAY));
        }
    }
}

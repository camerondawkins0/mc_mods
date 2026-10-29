package com.slfrontier.world;

import com.slfrontier.SlfConfig;
import com.slfrontier.data.PlayerData;
import com.slfrontier.entity.FrontierBoss;
import com.slfrontier.network.ModNetwork;
import com.slfrontier.network.OverlayNetwork;
import com.slfrontier.registry.ModAttachments;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

/** Server side of fast travel: every request from the map is validated here. */
public final class FastTravel {
    private FastTravel() {
    }

    /** Why fast travel to {@code id} is not possible right now, or null if it is. */
    @Nullable
    public static Component refusal(ServerPlayer player, String id) {
        if (!Frontier.isIn(player)) return Component.translatable("message.slfrontier.travel.outside");
        if (!SlfConfig.FAST_TRAVEL.get()) return Component.translatable("message.slfrontier.travel.disabled");
        Waypoints.Waypoint wp = Waypoints.get(player.serverLevel()).byId(id);
        if (wp == null) return Component.translatable("message.slfrontier.travel.unknown");
        PlayerData data = ModAttachments.get(player);
        if (SlfConfig.FAST_TRAVEL_NEEDS_ATTUNEMENT.get() && !data.knowsWaypoint(id)) {
            return Component.translatable("message.slfrontier.travel.not_attuned");
        }
        long now = player.serverLevel().getGameTime();
        if (now < data.fastTravelReadyAt() && !player.isCreative()) {
            return Component.translatable("message.slfrontier.travel.cooldown", (data.fastTravelReadyAt() - now + 19) / 20);
        }
        if (!SlfConfig.FAST_TRAVEL_IN_COMBAT.get() && !player.isCreative() && inCombat(player)) {
            return Component.translatable("message.slfrontier.travel.combat");
        }
        return null;
    }

    public static boolean inCombat(ServerPlayer player) {
        if (player.getLastHurtByMob() != null) return true;
        return !player.serverLevel().getEntitiesOfClass(FrontierBoss.class, player.getBoundingBox().inflate(40),
                b -> b.getTarget() == player).isEmpty();
    }

    public static void request(ServerPlayer player, String id) {
        Component refusal = refusal(player, id);
        if (refusal != null) {
            player.displayClientMessage(refusal.copy().withStyle(ChatFormatting.RED), true);
            return;
        }
        ServerLevel level = player.serverLevel();
        Waypoints.Waypoint wp = Waypoints.get(level).byId(id);
        if (wp == null) return;
        BlockPos dest = arrival(level, wp);
        OverlayNetwork.travel(player, OverlayNetwork.TRAVEL, wp.nameKey(), dest);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY() + 1, player.getZ(), 60, 0.4, 0.9, 0.4, 0.05);
        level.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8f, 1.5f);
        player.teleportTo(level, dest.getX() + 0.5, dest.getY(), dest.getZ() + 0.5, player.getYRot(), player.getXRot());
        player.resetFallDistance();
        level.sendParticles(ParticleTypes.END_ROD, dest.getX() + 0.5, dest.getY() + 1, dest.getZ() + 0.5, 50, 0.4, 1.0, 0.4, 0.06);
        level.playSound(null, dest, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1f, 1.6f);
        PlayerData data = ModAttachments.get(player);
        data.setFastTravelReadyAt(level.getGameTime() + SlfConfig.FAST_TRAVEL_COOLDOWN.get() * 20L);
        ModNetwork.announce(player, "announce.slfrontier.fast_travel", wp.nameKey(), 0x7FE7FF);
        ModNetwork.sync(player);
    }

    /** Where a traveller lands: town spawn points as-is, otherwise beside the crystal on solid ground. */
    public static BlockPos arrival(ServerLevel level, Waypoints.Waypoint wp) {
        if (wp.kind() == Waypoints.Kind.TOWN) return wp.pos();
        BlockPos base = wp.pos();
        level.getChunk(base);
        for (int[] o : new int[][]{{0, 2}, {2, 0}, {0, -2}, {-2, 0}, {2, 2}, {-2, -2}}) {
            BlockPos p = base.offset(o[0], 0, o[1]);
            for (int dy = 2; dy >= -2; dy--) {
                BlockPos feet = p.above(dy);
                if (level.getBlockState(feet.below()).isSolid() && level.getBlockState(feet).isAir() && level.getBlockState(feet.above()).isAir()) {
                    return feet;
                }
            }
        }
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, base.getX(), base.getZ() + 2);
        return new BlockPos(base.getX(), Math.max(y, base.getY() + 1), base.getZ() + 2);
    }
}

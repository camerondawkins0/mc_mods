package com.slfrontier.skill;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Tracks the airborne half of Crescent Vorpal until the player lands. */
public final class PendingSlam {
    private record Leap(int ticksLeft, ResourceKey<Level> dimension, Vec3 from) {
    }

    /** A slam is called off if the avatar ends up further than this from where it leapt (a teleport). */
    private static final double MAX_DRIFT = 24;
    private static final Map<UUID, Leap> PENDING = new ConcurrentHashMap<>();

    private PendingSlam() {
    }

    public static void schedule(ServerPlayer player, int maxTicks) {
        PENDING.put(player.getUUID(), new Leap(maxTicks, player.level().dimension(), player.position()));
    }

    /** Calls off a pending slam (death, logout, travel). */
    public static void clear(ServerPlayer player) {
        PENDING.remove(player.getUUID());
    }

    public static void tick(ServerPlayer player) {
        Leap leap = PENDING.get(player.getUUID());
        if (leap == null) return;
        if (!player.isAlive() || player.level().dimension() != leap.dimension()
                || player.position().subtract(leap.from()).horizontalDistanceSqr() > MAX_DRIFT * MAX_DRIFT) {
            // Teleported away (fast travel, a waypoint, another dimension) or died mid-leap: no slam at the destination.
            PENDING.remove(player.getUUID());
            return;
        }
        int left = leap.ticksLeft() - 1;
        // Wait a few ticks so the leap actually leaves the ground, then slam on landing.
        if ((left < 10 && player.onGround()) || left <= -40) {
            PENDING.remove(player.getUUID());
            player.fallDistance = 0;
            SkillExecutor.crescentSlam(player);
        } else {
            PENDING.put(player.getUUID(), new Leap(left, leap.dimension(), leap.from()));
        }
    }
}

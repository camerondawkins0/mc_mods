package com.slfrontier.skill;

import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Tracks the airborne half of Crescent Vorpal until the player lands. */
public final class PendingSlam {
    private static final Map<UUID, Integer> PENDING = new ConcurrentHashMap<>();

    private PendingSlam() {
    }

    public static void schedule(ServerPlayer player, int maxTicks) {
        PENDING.put(player.getUUID(), maxTicks);
    }

    public static void tick(ServerPlayer player) {
        Integer left = PENDING.get(player.getUUID());
        if (left == null) return;
        left--;
        // Wait a few ticks so the leap actually leaves the ground, then slam on landing.
        if ((left < 10 && player.onGround()) || left <= -40) {
            PENDING.remove(player.getUUID());
            player.fallDistance = 0;
            SkillExecutor.crescentSlam(player);
        } else {
            PENDING.put(player.getUUID(), left);
        }
    }
}

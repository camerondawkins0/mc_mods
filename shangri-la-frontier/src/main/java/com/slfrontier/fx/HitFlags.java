package com.slfrontier.fx;

import net.minecraft.world.entity.Entity;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remembers, for the hit being processed right now, what kind of hit it was (critical,
 * weak point, skill, combo finisher), so the damage event can tell the attacker's client
 * (damage numbers, and how hard the hit lands: hit-stop, flashes, FOV punch).
 */
public final class HitFlags {
    public static final int CRIT = 1, WEAK = 2, SKILL = 4, FINISHER = 8, KILL = 16;
    private static final Map<Integer, Integer> PENDING = new ConcurrentHashMap<>();

    private HitFlags() {
    }

    public static void mark(Entity target, boolean crit, boolean weak) {
        int flags = (crit ? 1 : 0) | (weak ? 2 : 0);
        if (flags != 0) PENDING.merge(target.getId(), flags, (a, b) -> a | b);
    }

    public static void add(Entity target, int flags) {
        if (flags != 0) PENDING.merge(target.getId(), flags, (a, b) -> a | b);
    }

    public static int take(Entity target) {
        Integer f = PENDING.remove(target.getId());
        return f == null ? 0 : f;
    }
}

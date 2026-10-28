package com.slfrontier.fx;

import net.minecraft.world.entity.Entity;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remembers, for the hit being processed right now, whether it was a critical or a
 * weak-point hit, so the damage event can tell the attacker's client (damage numbers).
 */
public final class HitFlags {
    private static final Map<Integer, Integer> PENDING = new ConcurrentHashMap<>();

    private HitFlags() {
    }

    public static void mark(Entity target, boolean crit, boolean weak) {
        int flags = (crit ? 1 : 0) | (weak ? 2 : 0);
        if (flags != 0) PENDING.merge(target.getId(), flags, (a, b) -> a | b);
    }

    public static int take(Entity target) {
        Integer f = PENDING.remove(target.getId());
        return f == null ? 0 : f;
    }
}

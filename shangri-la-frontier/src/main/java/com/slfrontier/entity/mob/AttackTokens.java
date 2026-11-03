package com.slfrontier.entity.mob;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Attack tokens: at most {@link #MAX} monsters fight a player in melee at once; the rest circle
 * at a distance until one of them steps back. A pack is dangerous without becoming a blender.
 */
final class AttackTokens {
    static final int MAX = 2;
    private static final Map<LivingEntity, List<Mob>> HOLDERS = new WeakHashMap<>();
    /** When a monster last had to wait for a token, per target (so holders know to make room). */
    private static final Map<LivingEntity, Long> WAITING = new WeakHashMap<>();

    private AttackTokens() {
    }

    static boolean tryAcquire(Mob mob, LivingEntity target) {
        List<Mob> holders = HOLDERS.computeIfAbsent(target, k -> new ArrayList<>());
        holders.removeIf(m -> m.isRemoved() || !m.isAlive() || m.getTarget() != target || m.distanceToSqr(target) > 16 * 16);
        if (holders.contains(mob)) return true;
        if (holders.size() < MAX) {
            holders.add(mob);
            return true;
        }
        WAITING.put(target, mob.level().getGameTime());
        return false;
    }

    static void release(Mob mob, LivingEntity target) {
        List<Mob> holders = HOLDERS.get(target);
        if (holders != null) holders.remove(mob);
    }

    /** Whether another monster asked for a token on this target in the last two seconds. */
    static boolean othersWaiting(Mob mob, LivingEntity target) {
        Long at = WAITING.get(target);
        return at != null && mob.level().getGameTime() - at < 40;
    }
}

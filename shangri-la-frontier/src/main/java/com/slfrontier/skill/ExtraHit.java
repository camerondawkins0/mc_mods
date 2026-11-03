package com.slfrontier.skill;

import com.slfrontier.mixin.LivingEntityAccess;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Hits that come on top of the normal swing rhythm: the off-hand blade's follow-up, skills,
 * Emul's Magic Edge and the allies' blows. Vanilla gives a creature about ten ticks of hit
 * immunity after each hit and, during them, only lets a harder hit through for the difference;
 * an extra hit that set those would eat (part of) the player's next main-hand swing. So an extra
 * hit lands on its own and then puts the target's immunity back exactly as it found it.
 *
 * <p>While one is being dealt, {@link #current()} says which kind it is, so the damage handlers
 * can tell it apart from a main-hand swing (a skill rolls its own crits and weak points).
 */
public final class ExtraHit {
    public enum Kind { OFFHAND, SKILL, ALLY }

    @Nullable
    private static Kind current;
    private static boolean finisher;

    private ExtraHit() {
    }

    /** The kind of extra hit being dealt right now, or null during an ordinary hit. */
    @Nullable
    public static Kind current() {
        return current;
    }

    /** Whether the off-hand hit being dealt right now is the second blade of a cross-slash. */
    public static boolean isFinisher() {
        return current == Kind.OFFHAND && finisher;
    }

    public static boolean deal(LivingEntity target, DamageSource source, float damage, Kind kind) {
        return deal(target, source, damage, kind, false);
    }

    /** Deals the hit, ignoring and then restoring the target's hit immunity. @return whether it was hurt */
    public static boolean deal(LivingEntity target, DamageSource source, float damage, Kind kind, boolean asFinisher) {
        LivingEntityAccess access = (LivingEntityAccess) target;
        int immunity = target.invulnerableTime;
        float lastHurt = access.slfrontier$getLastHurt();
        Kind outerKind = current;
        boolean outerFinisher = finisher;
        current = kind;
        finisher = asFinisher;
        target.invulnerableTime = 0;
        try {
            return target.hurtOrSimulate(source, damage);
        } finally {
            current = outerKind;
            finisher = outerFinisher;
            target.invulnerableTime = immunity;
            access.slfrontier$setLastHurt(lastHurt);
        }
    }
}

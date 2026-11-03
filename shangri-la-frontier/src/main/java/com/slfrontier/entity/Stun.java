package com.slfrontier.entity;

import com.slfrontier.registry.ModSounds;
import com.slfrontier.Sfx;
import com.slfrontier.fx.Fx;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Short stuns: a perfect parry knocks the attacker off balance. A stunned monster stops in its
 * tracks and can't land a hit until it recovers; a boss also pauses its current attack.
 */
public final class Stun {
    /** How long a perfect parry stuns the attacker. */
    public static final int PARRY_TICKS = 10;

    private static final Map<LivingEntity, Long> UNTIL = new WeakHashMap<>();

    private Stun() {
    }

    public static void apply(LivingEntity target, int ticks) {
        if (!(target.level() instanceof ServerLevel level) || !target.isAlive()) return;
        UNTIL.merge(target, level.getGameTime() + ticks, Math::max);
        target.setDeltaMovement(0, Math.min(0, target.getDeltaMovement().y), 0);
        target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks, 9, false, false, false));
        if (target instanceof Mob mob) {
            mob.getNavigation().stop();
            mob.stopUsingItem();
        }
        if (target instanceof FrontierBoss boss) boss.stun(ticks);
        // Sparks circle its head while it reels.
        double top = target.getY() + target.getBbHeight() + 0.25;
        Fx.ring(level, target.position().add(0, target.getBbHeight() + 0.25, 0), Math.max(0.45f, target.getBbWidth() * 0.45f),
                0xFFFFE070, ticks, 0);
        level.sendParticles(ParticleTypes.CRIT, target.getX(), top, target.getZ(), 12, target.getBbWidth() * 0.3, 0.1,
                target.getBbWidth() * 0.3, 0.15);
        Sfx.at(target, ModSounds.COMBAT_STUN, SoundSource.HOSTILE, 0.9f, 1f);
    }

    public static boolean isStunned(Entity entity) {
        if (UNTIL.isEmpty()) return false;
        Long until = UNTIL.get(entity);
        if (until == null) return false;
        if (entity.level().getGameTime() >= until || !entity.isAlive()) {
            UNTIL.remove(entity);
            return false;
        }
        return true;
    }

    /** Called every tick for mobs: keep a stunned one from walking or drawing a bow. */
    public static void tick(Mob mob) {
        if (!isStunned(mob)) return;
        mob.getNavigation().stop();
        mob.stopUsingItem();
    }
}

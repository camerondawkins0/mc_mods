package com.slfrontier.entity.boss;

import com.slfrontier.entity.FrontierBoss;
import com.slfrontier.fx.Fx;
import com.slfrontier.quest.Story;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The Ravenous Snake, area boss of the Rampant Forest that guards the bridge to Secondil.
 * A great green serpent with flowing white hair on its head and tail: it lunges, sweeps its
 * tail, slithers through the arena and opens its tail hair like a flower to spray poison.
 */
public class RavenousSnakeEntity extends FrontierBoss {
    public static final int BITE = 0, TAIL = 1, POISON = 2, CHARGE = 3;
    private static final int GREEN = 0xFF8AE070, VENOM = 0xFFB040E0, WARN = 0xFFFFD04A;

    private final List<Vec3> marks = new ArrayList<>();
    private Vec3 aim = Vec3.ZERO;

    public RavenousSnakeEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level, 12, 400, BossEvent.BossBarColor.GREEN);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 220)
                .add(Attributes.ATTACK_DAMAGE, 7)
                .add(Attributes.ARMOR, 4)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.9)
                .add(Attributes.FOLLOW_RANGE, 40)
                .add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override
    protected int chooseAttack(LivingEntity target, double distance) {
        int roll = random.nextInt(10);
        if (roll < 2) return POISON;
        if (distance > 7 && roll < 6) return CHARGE;
        if (distance < 5) return roll < 6 ? BITE : TAIL;
        return -1;
    }

    @Override
    protected int attackLength(int attack) {
        return switch (attack) {
            case BITE -> 16;
            case TAIL -> 24;
            case POISON -> 36;
            case CHARGE -> 22;
            default -> 10;
        };
    }

    @Override
    protected int cooldownAfter(int attack) {
        return 25 + random.nextInt(25);
    }

    @Override
    protected void tickAttack(int attack, int tick, LivingEntity target) {
        switch (attack) {
            case BITE -> {
                if (tick == 1) Fx.coneAt(level(), position(), getYRot(), 5.5f, 40, WARN, 8);
                if (tick == 9) {
                    dashToward(position().add(getLookAngle()), 0.6, 0.05);
                    hitAround(5.0, 1.0f, 0.5, 0.5);
                    playSound(SoundEvents.EVOKER_FANGS_ATTACK, 1.5f, 0.8f);
                }
            }
            case TAIL -> {
                if (tick == 1) Fx.telegraph(level(), position(), 6f, WARN, 12);
                if (tick == 13) {
                    hitAround(6.0, 0.9f, -1, 1.1);
                    Fx.arc(this, 6f, 0, 360, GREEN, 10);
                    playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.5f, 0.6f);
                }
            }
            case POISON -> {
                // The tail hair opens like a flower and sprays venom where its prey stands.
                if (tick == 4) {
                    marks.clear();
                    for (LivingEntity e : enemiesNear(20)) marks.add(e.position());
                    for (Vec3 m : marks) Fx.telegraph(level(), m, 3f, VENOM, 20);
                    playSound(SoundEvents.SPIDER_AMBIENT, 1.5f, 0.6f);
                }
                if (tick == 24 && level() instanceof ServerLevel server) {
                    for (Vec3 m : marks) {
                        AreaEffectCloud cloud = new AreaEffectCloud(server, m.x, m.y, m.z);
                        cloud.setOwner(this);
                        cloud.setRadius(3f);
                        cloud.setDuration(100);
                        cloud.setRadiusPerTick(-0.01f);
                        cloud.setParticle(ParticleTypes.WITCH);
                        cloud.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 1));
                        server.addFreshEntity(cloud);
                        Fx.ring(server, m, 3.2f, VENOM, 12, 0);
                    }
                    playSound(SoundEvents.LINGERING_POTION_THROW, 1.5f, 0.6f);
                }
            }
            case CHARGE -> {
                if (tick == 1 && target != null) {
                    aim = target.position();
                    Fx.coneAt(level(), position(), Fx.yawTo(position(), aim), (float) Math.min(16, distanceTo(target) + 4), 18, WARN, 8);
                }
                if (tick == 8) dashToward(aim, 1.3, 0.05);
                if (tick >= 9 && tick <= 18) hitAround(2.4, 1.1f, -1, 1.0);
            }
            default -> {
            }
        }
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel server) Story.onRavenousSnakeSlain(server, this, source.getEntity());
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.SNIFFER_IDLE;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.SPIDER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.SPIDER_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return 0.5f;
    }
}

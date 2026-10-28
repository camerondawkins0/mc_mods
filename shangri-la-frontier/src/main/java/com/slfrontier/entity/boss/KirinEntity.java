package com.slfrontier.entity.boss;

import com.slfrontier.entity.FrontierBoss;
import com.slfrontier.fx.Fx;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Kirin, Wezaemon's tactical horse-mech, summoned in the second phase of the Tombguard
 * fight. It gallops through the arena, rains missiles on marked spots, sweeps a laser
 * from its beak and, when damaged, calls down lightning. It falls apart if its master
 * is defeated.
 */
public class KirinEntity extends FrontierBoss {
    public static final int CHARGE = 0, MISSILES = 1, LASER = 2, LIGHTNING = 3;
    private static final int CYAN = 0xFF6FF0FF, WHITE = 0xFFEFFAFF, MISSILE = 0xFFFF8A4A, BOLT = 0xFFFFF07A;

    @Nullable
    private UUID master;
    private final List<Vec3> marks = new ArrayList<>();
    private Vec3 aim = Vec3.ZERO;

    public KirinEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level, 50, 900, BossEvent.BossBarColor.WHITE);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 300)
                .add(Attributes.ATTACK_DAMAGE, 10)
                .add(Attributes.ARMOR, 14)
                .add(Attributes.MOVEMENT_SPEED, 0.36)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8)
                .add(Attributes.FOLLOW_RANGE, 48)
                .add(Attributes.STEP_HEIGHT, 1.5);
    }

    public void setMaster(WezaemonEntity wezaemon) {
        this.master = wezaemon.getUUID();
    }

    @Override
    protected double chaseSpeed() {
        return 1.2;
    }

    @Override
    protected int chooseAttack(LivingEntity target, double distance) {
        int roll = random.nextInt(10);
        if (getHealth() < getMaxHealth() * 0.5f && roll < 2) return LIGHTNING;
        if (distance > 8 && roll < 5) return CHARGE;
        if (roll < 3) return MISSILES;
        if (distance < 14) return LASER;
        return CHARGE;
    }

    @Override
    protected int attackLength(int attack) {
        return switch (attack) {
            case CHARGE -> 30;
            case MISSILES -> 40;
            case LASER -> 34;
            case LIGHTNING -> 44;
            default -> 10;
        };
    }

    @Override
    protected int cooldownAfter(int attack) {
        return 30 + random.nextInt(30);
    }

    private Vec3 head() {
        return position().add(getLookAngle().multiply(1.6, 0, 1.6)).add(0, getBbHeight() * 0.95, 0);
    }

    @Override
    protected void tickAttack(int attack, int tick, LivingEntity target) {
        switch (attack) {
            case CHARGE -> {
                if (tick == 1 && target != null) {
                    aim = target.position();
                    Fx.coneAt(level(), position(), Fx.yawTo(position(), aim), (float) Math.min(18, distanceTo(target) + 4), 16, CYAN, 9);
                    playSound(SoundEvents.HORSE_ANGRY, 2f, 0.5f);
                }
                if (tick == 10) {
                    dashToward(aim, 1.5, 0.1);
                    playSound(SoundEvents.IRON_GOLEM_ATTACK, 1.5f, 0.6f);
                }
                if (tick >= 11 && tick <= 22) {
                    if (tick % 3 == 0) particles(ParticleTypes.CLOUD, getX(), getY() + 0.3, getZ(), 4, 0.6, 0.02);
                    hitAround(2.2, 1.2f, -1, 1.4);
                }
            }
            case MISSILES -> {
                // Mark the ground under (and around) each enemy, then the warheads land.
                if (tick == 6) {
                    marks.clear();
                    for (LivingEntity e : enemiesNear(24)) {
                        marks.add(e.position());
                        marks.add(e.position().add(random.nextGaussian() * 2.5, 0, random.nextGaussian() * 2.5));
                    }
                    for (Vec3 m : marks) Fx.telegraph(level(), m, 2.4f, MISSILE, 22);
                    playSound(SoundEvents.FIREWORK_ROCKET_LAUNCH, 2f, 0.6f);
                    particles(ParticleTypes.FIREWORK, getX(), getY() + 2.5, getZ(), 20, 0.5, 0.2);
                }
                if (tick == 28) {
                    for (Vec3 m : marks) {
                        Fx.flash(level(), m.add(0, 0.8, 0), 2.5f, MISSILE, 8, 0.3f);
                        Fx.ring(level(), m, 2.6f, MISSILE, 10, 0);
                        if (level() instanceof ServerLevel server) {
                            server.sendParticles(ParticleTypes.EXPLOSION, m.x, m.y + 0.5, m.z, 1, 0, 0, 0, 0);
                        }
                        hitAt(m, 2.4, 0.9f, 0.6);
                    }
                    playSound(SoundEvents.GENERIC_EXPLODE.value(), 2f, 1.1f);
                }
            }
            case LASER -> {
                // The head sweeps from one side to the other; the beam cuts everything it crosses.
                if (tick == 1 && target != null) {
                    aim = target.position();
                    Fx.coneAt(level(), position(), Fx.yawTo(position(), aim), 14, 90, CYAN, 9);
                    playSound(SoundEvents.BEACON_ACTIVATE, 2f, 1.6f);
                }
                if (tick >= 10 && tick <= 26 && tick % 2 == 0) {
                    float base = Fx.yawTo(position(), aim);
                    float yaw = base - 45 + 90 * (tick - 10) / 16f;
                    Vec3 dir = Vec3.directionFromRotation(8, yaw);
                    Vec3 from = head();
                    Vec3 to = from.add(dir.scale(14));
                    Fx.beam(level(), from, to, 0.35f, CYAN, 4);
                    hitLine(from, to, 0.9, 0.35f, 0.2);
                }
            }
            case LIGHTNING -> {
                if (tick == 4) {
                    marks.clear();
                    for (LivingEntity e : enemiesNear(28)) {
                        for (int i = 0; i < 3; i++) {
                            marks.add(e.position().add(random.nextGaussian() * 3, 0, random.nextGaussian() * 3));
                        }
                    }
                    for (Vec3 m : marks) Fx.telegraph(level(), m, 2.0f, BOLT, 26);
                }
                if (tick == 30 && level() instanceof ServerLevel server) {
                    for (Vec3 m : marks) {
                        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(server);
                        if (bolt != null) {
                            bolt.moveTo(m.x, m.y, m.z);
                            bolt.setVisualOnly(true);
                            server.addFreshEntity(bolt);
                        }
                        hitAt(m, 2.0, 1.0f, 0.3);
                    }
                }
            }
            default -> {
            }
        }
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!(level() instanceof ServerLevel server) || tickCount % 20 != 0) return;
        if (master == null) return;  // spawned on its own (spawn egg, command)
        Entity m = server.getEntity(master);
        if (m == null || !m.isAlive()) {
            // Without the Tombguard, Kirin falls apart.
            server.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1, getZ(), 40, 1, 1, 1, 0.02);
            Fx.flash(server, position().add(0, 1.2, 0), 3f, WHITE, 10, 0);
            playSound(SoundEvents.IRON_GOLEM_DEATH, 1.5f, 0.6f);
            discard();
        } else if (getTarget() == null && m instanceof WezaemonEntity w && w.getTarget() != null) {
            setTarget(w.getTarget());
        }
    }

    @Override
    public boolean isAlliedTo(Entity other) {
        return other instanceof WezaemonEntity || super.isAlliedTo(other);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (master != null) tag.putUUID("Master", master);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("Master")) master = tag.getUUID("Master");
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.IRON_GOLEM_STEP;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.IRON_GOLEM_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.IRON_GOLEM_DEATH;
    }
}

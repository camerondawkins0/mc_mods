package com.slfrontier.entity.boss;

import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
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
    private double chargeLength = 8;

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
    protected int bossPhase() {
        return getHealth() < getMaxHealth() * 0.5f ? 2 : 1;
    }

    @Override
    protected String phaseHint(int phase) {
        return phase == 2 ? "message.slfrontier.phase.kirin.2" : null;
    }

    @Override
    protected int chooseAttack(LivingEntity target, double distance) {
        int lightning = bossPhase() >= 2 ? 2 : 0;
        if (distance > 8) return pick(CHARGE, 4, MISSILES, 2, LASER, distance < 14 ? 2 : 0, LIGHTNING, lightning, -1, 1);
        return pick(LASER, 3, MISSILES, 2, CHARGE, 1, LIGHTNING, lightning);
    }

    @Override
    protected int attackLength(int attack) {
        return switch (attack) {
            case CHARGE -> 14;
            case MISSILES -> 40;
            case LASER -> 30;
            case LIGHTNING -> 44;
            default -> 10;
        };
    }

    @Override
    protected int cooldownAfter(int attack) {
        return 30 + random.nextInt(30);
    }

    /** After the laser its head vents steam for a moment: it stands still, open to a counter. */
    @Override
    protected int recoveryAfter(int attack) {
        return attack == LASER ? 15 : 0;
    }

    @Override
    protected void tickRecovery(int attack, int ticksLeft) {
        if (attack != LASER) return;
        Vec3 h = head();
        if (ticksLeft % 3 == 0) particles(ParticleTypes.CLOUD, h.x, h.y, h.z, 4, 0.3, 0.05);
        if (ticksLeft == 14) playSound(SoundEvents.FIRE_EXTINGUISH, 1.5f, 0.6f);
    }

    /** Wezaemon looks after his mount: it resets with him, not on its own. */
    @Override
    protected boolean canReset() {
        return master == null;
    }

    private Vec3 head() {
        return position().add(getLookAngle().multiply(1.6, 0, 1.6)).add(0, getBbHeight() * 0.95, 0);
    }

    @Override
    protected void tickAttack(int attack, int tick, LivingEntity target) {
        switch (attack) {
            case CHARGE -> {
                // Gallops down the whole lane it showed; a wall stops it cold.
                if (tick == 1) {
                    aim = target != null ? target.position() : position().add(facing().scale(8));
                    double dx = aim.x - getX(), dz = aim.z - getZ();
                    chargeLength = Math.min(18, Math.sqrt(dx * dx + dz * dz) + 4);
                    tellCone(Fx.yawTo(position(), aim), (float) chargeLength, 16, true, 9);
                    playSound(SoundEvents.HORSE_ANGRY, 2f, 0.5f);
                }
                if (tick == 10) {
                    playSound(SoundEvents.IRON_GOLEM_ATTACK, 1.5f, 0.6f);
                    startCharge(facing(), 1.4, chargeLength, 0.1, 2.2, 1.2f, 1.4, 0);
                }
                if (tick > 10 && tick % 3 == 0) particles(ParticleTypes.CLOUD, getX(), getY() + 0.3, getZ(), 4, 0.6, 0.02);
            }
            case MISSILES -> {
                // Mark the ground under (and around) each enemy, then the warheads land.
                if (tick == 6) {
                    marks.clear();
                    for (LivingEntity e : enemiesNear(24)) {
                        marks.add(e.position());
                        marks.add(e.position().add(random.nextGaussian() * 2.5, 0, random.nextGaussian() * 2.5));
                    }
                    for (Vec3 m : marks) Fx.telegraph(level(), m, 2.4f, tellColor(false), 22);
                    tellSound(false);
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
                        hitAt(m, 2.4, 0.9f, 0.6, UNPARRYABLE);
                    }
                    playSound(SoundEvents.GENERIC_EXPLODE.value(), 2f, 1.1f);
                }
            }
            case LASER -> {
                // The head sweeps from one side to the other; the beam cuts everything it crosses.
                if (tick == 1) {
                    aim = target != null ? target.position() : position().add(facing().scale(8));
                    tellCone(Fx.yawTo(position(), aim), 14, 90, false, 9);
                    playSound(SoundEvents.BEACON_ACTIVATE, 2f, 1.6f);
                }
                if (tick >= 10 && tick <= 26 && tick % 2 == 0) {
                    float base = Fx.yawTo(position(), aim);
                    float yaw = base - 45 + 90 * (tick - 10) / 16f;
                    Vec3 dir = Vec3.directionFromRotation(8, yaw);
                    Vec3 from = head();
                    Vec3 to = from.add(dir.scale(14));
                    Fx.beam(level(), from, to, 0.35f, CYAN, 4);
                    hitLine(from, to, 0.9, 0.35f, 0.2, UNPARRYABLE);
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
                    for (Vec3 m : marks) Fx.telegraph(level(), m, 2.0f, tellColor(false), 26);
                    tellSound(false);
                }
                if (tick == 30 && level() instanceof ServerLevel server) {
                    for (Vec3 m : marks) {
                        LightningBolt bolt = net.minecraft.world.entity.EntityTypes.LIGHTNING_BOLT.create(server, EntitySpawnReason.EVENT);
                        if (bolt != null) {
                            bolt.snapTo(m.x, m.y, m.z);
                            bolt.setVisualOnly(true);
                            server.addFreshEntity(bolt);
                        }
                        hitAt(m, 2.0, 1.0f, 0.3, UNPARRYABLE);
                    }
                }
            }
            default -> {
            }
        }
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
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
    protected boolean considersEntityAsAlly(Entity other) {
        return other instanceof WezaemonEntity || super.considersEntityAsAlly(other);
    }

    @Override
    public void addAdditionalSaveData(ValueOutput tag) {
        super.addAdditionalSaveData(tag);
        if (master != null) tag.store("Master", net.minecraft.core.UUIDUtil.CODEC, master);
    }

    @Override
    public void readAdditionalSaveData(ValueInput tag) {
        super.readAdditionalSaveData(tag);
        tag.read("Master", net.minecraft.core.UUIDUtil.CODEC).ifPresent(id -> master = id);
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

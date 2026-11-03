package com.slfrontier.entity.boss;

import com.slfrontier.entity.FrontierBoss;
import com.slfrontier.entity.mob.EmpireBeeEntity;
import com.slfrontier.fx.Fx;
import com.slfrontier.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Queen of the Empire Bees. Her parts let Bilac forge the Empire Bee Twinblades. */
public class EmpireBeeQueenEntity extends FrontierBoss {
    private static final int HONEY = 0xFFFFC84A;
    public static final int STING_DIVE = 0, SUMMON = 1, BARRAGE = 2;
    private static final double DIVE_SPEED = 1.3;

    private Vec3 aim = Vec3.ZERO;
    private Vec3 diveDir = Vec3.ZERO;
    private double diveLength;
    /** Ticks left of a sting dive in flight. */
    private int diving;

    public EmpireBeeQueenEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level, 25, 900, BossEvent.BossBarColor.YELLOW);
        this.moveControl = new FlyingMoveControl(this, 20, true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 240)
                .add(Attributes.ATTACK_DAMAGE, 10)
                .add(Attributes.ARMOR, 6)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FLYING_SPEED, 0.5)
                .add(Attributes.FOLLOW_RANGE, 32);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation nav = new FlyingPathNavigation(this, level);
        nav.setCanOpenDoors(false);
        nav.setCanFloat(true);
        return nav;
    }

    @Override
    protected int chooseAttack(LivingEntity target, double distance) {
        int summon = nearbyBees() < 6 ? 2 : 0;
        if (distance > 6) return pick(BARRAGE, 4, STING_DIVE, distance < 12 ? 3 : 0, SUMMON, summon, -1, 2);
        return pick(STING_DIVE, 4, BARRAGE, 1, SUMMON, summon, -1, 2);
    }

    private int nearbyBees() {
        return level().getEntitiesOfClass(EmpireBeeEntity.class, getBoundingBox().inflate(24)).size();
    }

    @Override
    protected int attackLength(int attack) {
        return switch (attack) {
            case STING_DIVE -> 30;
            case SUMMON -> 30;
            case BARRAGE -> 36;
            default -> 10;
        };
    }

    /** After a dive she hangs low, catching her breath: close enough to cut. */
    @Override
    protected int recoveryAfter(int attack) {
        return attack == STING_DIVE ? 16 : 0;
    }

    @Override
    protected void tickRecovery(int attack, int ticksLeft) {
        if (ticksLeft % 4 == 0) particles(ParticleTypes.FALLING_HONEY, getX(), getY() + 0.4, getZ(), 3, 0.5, 0);
    }

    @Override
    protected void onAttackEnd(int attack, boolean interrupted) {
        diving = 0;
    }

    @Override
    protected void tickAttack(int attack, int tick, LivingEntity target) {
        switch (attack) {
            case STING_DIVE -> {
                // She shows the lane, then dives down exactly that lane (no re-aiming mid-air).
                if (tick == 1) {
                    aim = target != null ? target.position().add(0, target.getBbHeight() * 0.6, 0) : position().add(facing().scale(6));
                    diveLength = target != null ? Math.min(12, distanceTo(target) + 3) : 6;
                    float yaw = Fx.yawTo(position(), aim);
                    lockAim(yaw);
                    Fx.coneAt(level(), new Vec3(getX(), target != null ? target.getY() : getY(), getZ()), yaw, (float) diveLength, 24,
                            tellColor(true), 10);
                    tellSound(true);
                }
                if (tick == 11) {
                    diveDir = aim.subtract(position()).normalize();
                    diving = (int) Math.ceil(diveLength / DIVE_SPEED);
                    playSound(SoundEvents.BEE_LOOP_AGGRESSIVE, 2f, 0.6f);
                }
                if (tick >= 11 && diving > 0) {
                    diving--;
                    setDeltaMovement(diveDir.scale(DIVE_SPEED));
                    needsSync = true;
                    if (hitAround(2.4, 1.0f, -1, 0.5) > 0) diving = 0;
                }
            }
            case SUMMON -> {
                if (tick == 4) Fx.ring(level(), position(), 4f, HONEY, 14, 0);
                if (tick == 15 && level() instanceof ServerLevel server) {
                    for (int i = 0; i < 3; i++) {
                        EmpireBeeEntity bee = ModEntities.EMPIRE_BEE.get().create(server, EntitySpawnReason.EVENT);
                        if (bee == null) continue;
                        bee.snapTo(getX() + random.nextGaussian(), getY() + 0.5, getZ() + random.nextGaussian(), getYRot(), 0);
                        bee.finalizeSpawn(server, server.getCurrentDifficultyAt(blockPosition()), EntitySpawnReason.MOB_SUMMONED, null);
                        bee.setTarget(target);
                        server.addFreshEntity(bee);
                        trackSummon(bee, true);
                    }
                    playSound(SoundEvents.BEEHIVE_EXIT, 2f, 0.8f);
                }
            }
            case BARRAGE -> {
                // Three volleys, each shown as a lane first, then a fan of stingers down that lane.
                if (tick == 1 || tick == 12 || tick == 23) {
                    aim = target != null ? target.position().add(0, target.getBbHeight() * 0.4, 0) : position().add(facing().scale(8));
                    float yaw = Fx.yawTo(position(), aim);
                    lockAim(yaw);
                    double len = Math.min(20, Math.sqrt(distanceToSqr(aim)) + 4);
                    Fx.coneAt(level(), new Vec3(getX(), target != null ? target.getY() : getY(), getZ()), yaw, (float) len, 16,
                            tellColor(true), 10);
                    tellSound(true);
                    particles(ParticleTypes.WAX_ON, getX(), getY() + 0.3, getZ(), 6, 0.3, 0.05);
                }
                if (tick == 11 || tick == 22 || tick == 33) {
                    for (int i = -1; i <= 1; i++) shootStinger(aim, i * 6f);
                    playSound(SoundEvents.BEE_STING, 1.5f, 1.2f);
                }
            }
            default -> {
            }
        }
    }

    /** A stinger toward {@code at}, turned {@code spread} degrees to the side. */
    private void shootStinger(Vec3 at, float spread) {
        Arrow stinger = new Arrow(level(), this, new ItemStack(Items.ARROW), null);
        stinger.pickup = AbstractArrow.Pickup.DISALLOWED;
        stinger.setBaseDamage(3.0);
        stinger.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 0));
        double dx = at.x - getX(), dz = at.z - getZ();
        double rad = Math.toRadians(spread), cos = Math.cos(rad), sin = Math.sin(rad);
        double rx = dx * cos - dz * sin, rz = dx * sin + dz * cos;
        double dy = at.y - stinger.getY();
        stinger.shoot(rx, dy + Math.sqrt(rx * rx + rz * rz) * 0.1, rz, 1.8f, 1f);
        level().addFreshEntity(stinger);
    }

    @Override
    protected void onHitTarget(LivingEntity target) {
        if (attack == STING_DIVE) target.addEffect(new MobEffectInstance(MobEffects.POISON, 120, 1), this);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        // Hover: gently fight gravity so she floats at the target's head height (low while she recovers).
        if (!level().isClientSide() && diving <= 0) {
            Vec3 m = getDeltaMovement();
            LivingEntity target = getTarget();
            double wantY = target != null ? target.getY() + (isRecovering() ? 0.8 : 2.5) : getY();
            double lift = Math.max(-0.05, Math.min(0.08, (wantY - getY()) * 0.05));
            setDeltaMovement(m.x, m.y * 0.6 + lift, m.z);
        }
        if (level() instanceof ServerLevel server && tickCount % 10 == 0) {
            server.sendParticles(ParticleTypes.FALLING_HONEY, getX(), getY(), getZ(), 1, 0.4, 0.2, 0.4, 0);
        }
    }

    @Override
    public boolean isNoGravity() {
        return true;
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.BEE_LOOP_AGGRESSIVE;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.BEE_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.BEE_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return 0.6f;
    }
}

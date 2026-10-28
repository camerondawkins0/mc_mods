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
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Queen of the Empire Bees. Her parts let Bilac forge the Empire Bee Twinblades. */
public class EmpireBeeQueenEntity extends FrontierBoss {
    private static final int HONEY = 0xFFFFC84A;
    public static final int STING_DIVE = 0, SUMMON = 1, BARRAGE = 2;

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
        int roll = random.nextInt(10);
        if (roll < 2 && nearbyBees() < 6) return SUMMON;
        if (distance > 6 && roll < 6) return BARRAGE;
        if (distance < 10) return STING_DIVE;
        return -1;
    }

    private int nearbyBees() {
        return level().getEntitiesOfClass(EmpireBeeEntity.class, getBoundingBox().inflate(24)).size();
    }

    @Override
    protected int attackLength(int attack) {
        return switch (attack) {
            case STING_DIVE -> 28;
            case SUMMON -> 30;
            case BARRAGE -> 34;
            default -> 10;
        };
    }

    @Override
    protected void tickAttack(int attack, int tick, LivingEntity target) {
        switch (attack) {
            case STING_DIVE -> {
                if (tick == 1 && target != null) {
                    Fx.coneAt(level(), new Vec3(getX(), target.getY(), getZ()), Fx.yawTo(position(), target.position()),
                            (float) Math.min(12, distanceTo(target) + 3), 24, HONEY, 9);
                }
                if (tick == 10 && target != null) {
                    Vec3 d = target.getEyePosition().subtract(position()).normalize();
                    setDeltaMovement(d.scale(1.3));
                    hasImpulse = true;
                    playSound(SoundEvents.BEE_LOOP_AGGRESSIVE, 2f, 0.6f);
                }
                if (tick >= 12 && tick <= 22 && hitAround(2.4, 1.0f, -1, 0.5) > 0) {
                    if (target != null) target.addEffect(new MobEffectInstance(MobEffects.POISON, 120, 1), this);
                    attackTick = 23;
                }
            }
            case SUMMON -> {
                if (tick == 4) Fx.ring(level(), position(), 4f, HONEY, 14, 0);
                if (tick == 15 && level() instanceof ServerLevel server) {
                    for (int i = 0; i < 3; i++) {
                        EmpireBeeEntity bee = ModEntities.EMPIRE_BEE.get().create(server);
                        if (bee == null) continue;
                        bee.moveTo(getX() + random.nextGaussian(), getY() + 0.5, getZ() + random.nextGaussian(), getYRot(), 0);
                        bee.finalizeSpawn(server, server.getCurrentDifficultyAt(blockPosition()), MobSpawnType.MOB_SUMMONED, null);
                        bee.setTarget(target);
                        server.addFreshEntity(bee);
                    }
                    playSound(SoundEvents.BEEHIVE_EXIT, 2f, 0.8f);
                }
            }
            case BARRAGE -> {
                if (target != null && tick >= 10 && tick <= 30 && tick % 5 == 0) {
                    Arrow stinger = new Arrow(level(), this, new ItemStack(Items.ARROW), null);
                    stinger.pickup = AbstractArrow.Pickup.DISALLOWED;
                    stinger.setBaseDamage(3.0);
                    stinger.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 0));
                    double dx = target.getX() - getX();
                    double dy = target.getY(0.4) - stinger.getY();
                    double dz = target.getZ() - getZ();
                    stinger.shoot(dx, dy + Math.sqrt(dx * dx + dz * dz) * 0.1, dz, 1.8f, 4f);
                    level().addFreshEntity(stinger);
                    playSound(SoundEvents.BEE_STING, 1.5f, 1.2f);
                }
            }
            default -> {
            }
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        // Hover: gently fight gravity so she floats at the target's head height.
        if (!level().isClientSide) {
            Vec3 m = getDeltaMovement();
            LivingEntity target = getTarget();
            double wantY = target != null ? target.getY() + 2.5 : getY();
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

package com.slfrontier.entity.boss;

import com.slfrontier.entity.FrontierBoss;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Mud Digger, area boss of the Diremarsh Waste — a shark-headed, mole-bodied chimera
 * nicknamed the "Solo Killer". It burrows underground and erupts beneath its prey.
 */
public class MudDiggerEntity extends FrontierBoss {
    public static final int BITE = 0, BURROW = 1, TAIL = 2;
    private static final EntityDataAccessor<Boolean> BURROWED = SynchedEntityData.defineId(MudDiggerEntity.class, EntityDataSerializers.BOOLEAN);

    public MudDiggerEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level, 20, 600, BossEvent.BossBarColor.YELLOW);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 260)
                .add(Attributes.ATTACK_DAMAGE, 11)
                .add(Attributes.ARMOR, 8)
                .add(Attributes.MOVEMENT_SPEED, 0.28)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8)
                .add(Attributes.FOLLOW_RANGE, 32);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(BURROWED, false);
    }

    public boolean isBurrowed() {
        return entityData.get(BURROWED);
    }

    @Override
    protected int chooseAttack(LivingEntity target, double distance) {
        int roll = random.nextInt(10);
        if (distance > 8 || roll < 2) return BURROW;
        if (distance < 3.5) return roll < 6 ? BITE : TAIL;
        return -1;
    }

    @Override
    protected int attackLength(int attack) {
        return switch (attack) {
            case BITE -> 16;
            case BURROW -> 64;
            case TAIL -> 22;
            default -> 10;
        };
    }

    @Override
    protected void tickAttack(int attack, int tick, LivingEntity target) {
        switch (attack) {
            case BITE -> {
                if (tick == 9) hitAround(3.5, 1.0f, 0.3, 0.4);
            }
            case TAIL -> {
                if (tick == 12) {
                    hitAround(4.2, 0.9f, -1, 1.0);
                    particles(ParticleTypes.SWEEP_ATTACK, getX(), getY() + 0.6, getZ(), 4, 1.5, 0);
                }
            }
            case BURROW -> {
                BlockParticleOption mud = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.MUD.defaultBlockState());
                if (tick == 6) {
                    entityData.set(BURROWED, true);
                    setInvisible(true);
                    playSound(SoundEvents.ROOTED_DIRT_BREAK, 2f, 0.5f);
                }
                if (tick > 6 && tick < 46) {
                    if (target != null) {
                        Vec3 to = target.position().subtract(position());
                        Vec3 step = to.length() > 0.4 ? to.normalize().scale(0.45) : Vec3.ZERO;
                        setDeltaMovement(step.x, getDeltaMovement().y, step.z);
                    }
                    particles(mud, getX(), getY() + 0.1, getZ(), 6, 0.6, 0.1);
                }
                if (tick == 50) {
                    entityData.set(BURROWED, false);
                    setInvisible(false);
                    setDeltaMovement(0, 0.6, 0);
                    hasImpulse = true;
                    for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(3, 1, 3), this::isEnemy)) {
                        e.setDeltaMovement(e.getDeltaMovement().add(0, 0.9, 0));
                        e.hurtMarked = true;
                    }
                    hitAround(3.2, 1.3f, -1, 0.2);
                    particles(mud, getX(), getY() + 0.5, getZ(), 80, 1.5, 0.3);
                    playSound(SoundEvents.ROOTED_DIRT_BREAK, 3f, 0.4f);
                }
            }
            default -> {
            }
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isBurrowed() && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        return super.hurt(source, amount);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.RAVAGER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.RAVAGER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.RAVAGER_DEATH;
    }
}

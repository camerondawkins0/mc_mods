package com.slfrontier.entity.mob;

import com.slfrontier.entity.FrontierMonster;
import com.slfrontier.registry.ModEntities;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Redcap Goblins prowl the Diremarsh Waste at night: faster than goblins, they feint by
 * hopping back before lunging, and a wounded one shrieks for more Redcaps.
 */
public class RedcapGoblinEntity extends FrontierMonster {
    private boolean calledForHelp;
    private int feintCooldown = 40;

    public RedcapGoblinEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level, 12, 30);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 26)
                .add(Attributes.ATTACK_DAMAGE, 6)
                .add(Attributes.MOVEMENT_SPEED, 0.34)
                .add(Attributes.ARMOR, 2)
                .add(Attributes.FOLLOW_RANGE, 28);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2, false));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8f));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this, RedcapGoblinEntity.class).setAlertOthers());
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        LivingEntity target = getTarget();
        if (feintCooldown > 0) feintCooldown--;
        // The feint: a hop back just before it would be in reach, then a sudden lunge.
        if (target != null && feintCooldown == 0 && onGround() && distanceTo(target) < 3.2) {
            Vec3 away = position().subtract(target.position()).normalize();
            setDeltaMovement(away.x * 0.6, 0.35, away.z * 0.6);
            hasImpulse = true;
            feintCooldown = 60 + random.nextInt(40);
        } else if (target != null && feintCooldown == 50 && distanceTo(target) < 7) {
            Vec3 to = target.position().subtract(position()).normalize();
            setDeltaMovement(to.x * 0.9, 0.25, to.z * 0.9);
            hasImpulse = true;
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && !calledForHelp && isAlive() && getHealth() < getMaxHealth() * 0.5f && level() instanceof ServerLevel server) {
            calledForHelp = true;
            if (random.nextFloat() < 0.4f) {
                RedcapGoblinEntity friend = ModEntities.REDCAP_GOBLIN.get().create(server);
                if (friend != null) {
                    friend.moveTo(getX() + random.nextGaussian() * 2, getY(), getZ() + random.nextGaussian() * 2, getYRot(), 0);
                    friend.finalizeSpawn(server, server.getCurrentDifficultyAt(blockPosition()), MobSpawnType.REINFORCEMENT, null);
                    friend.calledForHelp = true;
                    friend.setTarget(getTarget());
                    server.addFreshEntity(friend);
                    server.sendParticles(ParticleTypes.LARGE_SMOKE, friend.getX(), friend.getY() + 0.5, friend.getZ(), 12, 0.3, 0.3, 0.3, 0.01);
                }
                playSound(SoundEvents.PILLAGER_CELEBRATE, 1.2f, 1.6f);
            }
        }
        return hurt;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.PILLAGER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PILLAGER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PILLAGER_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return 1.6f;
    }
}

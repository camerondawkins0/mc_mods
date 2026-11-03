package com.slfrontier.entity.mob;

import com.slfrontier.entity.FrontierMonster;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * A wild Vorpal Bunny. Cute, fast, and famous for landing critical hits that send
 * careless beginners straight back to the respawn point.
 */
public class VorpalBunnyEntity extends FrontierMonster {
    public VorpalBunnyEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level, 5, 18);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 10)
                .add(Attributes.ATTACK_DAMAGE, 4)
                .add(Attributes.MOVEMENT_SPEED, 0.36)
                .add(Attributes.JUMP_STRENGTH, 0.6)
                .add(Attributes.FOLLOW_RANGE, 20);
    }

    /** A quick chopper slash, and one time in four the famous "vorpal" leap-chop: a long tell, a lunge, a big hit. */
    static final WindupMeleeGoal.Attack SLASH = WindupMeleeGoal.Attack.swing(8, 5, 1.9f);
    static final WindupMeleeGoal.Attack LEAP_CHOP = new WindupMeleeGoal.Attack(12, 1, 2.5f, 1.8f, 50, 0.85, true);

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(3, new WindupMeleeGoal(this, 1.2, SLASH, LEAP_CHOP, 0.25f));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.9));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6f));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.RABBIT_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.RABBIT_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.RABBIT_DEATH;
    }
}

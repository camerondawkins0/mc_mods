package com.slfrontier.entity.mob;

import com.slfrontier.entity.FrontierMonster;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * A five-meter beetle of the Prismatic Forest Grotto. Its shell is the material
 * Vysache needs to awaken the Vorpal Choppers into the Moonblades.
 */
public class QuadBeetleEntity extends FrontierMonster {
    public static final int ANIM_CHARGE = 2;

    public QuadBeetleEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level, 15, 150);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 80)
                .add(Attributes.ATTACK_DAMAGE, 9)
                .add(Attributes.ARMOR, 10)
                .add(Attributes.ATTACK_KNOCKBACK, 1.5)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8)
                .add(Attributes.MOVEMENT_SPEED, 0.22)
                .add(Attributes.FOLLOW_RANGE, 24);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new ChargeGoal());
        goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.0, false));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.6));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 10f));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    /** Lowers its horn, then charges in a straight line. */
    private class ChargeGoal extends Goal {
        private int ticks;
        private int cooldown = 60;
        private Vec3 direction = Vec3.ZERO;

        ChargeGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = getTarget();
            if (cooldown > 0) {
                cooldown--;
                return false;
            }
            return target != null && onGround() && distanceTo(target) > 4 && distanceTo(target) < 14;
        }

        @Override
        public void start() {
            ticks = 0;
            getNavigation().stop();
            playAnim(ANIM_CHARGE);
            LivingEntity target = getTarget();
            if (target != null) {
                Vec3 d = target.position().subtract(position());
                direction = new Vec3(d.x, 0, d.z).normalize();
            }
        }

        @Override
        public boolean canContinueToUse() {
            return ticks < 40;
        }

        @Override
        public void tick() {
            ticks++;
            LivingEntity target = getTarget();
            if (ticks < 15) {
                if (target != null) getLookControl().setLookAt(target, 30f, 30f);
                if (level() instanceof ServerLevel server && ticks % 3 == 0) {
                    server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, getBlockStateOn()), getX(), getY(), getZ(), 6, 0.5, 0.1, 0.5, 0.1);
                }
                return;
            }
            setDeltaMovement(direction.x * 0.7, getDeltaMovement().y, direction.z * 0.7);
            setYRot((float) (Math.atan2(direction.z, direction.x) * 180 / Math.PI) - 90f);
            yBodyRot = getYRot();
            for (LivingEntity hit : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(0.4),
                    e -> e != QuadBeetleEntity.this && e instanceof Player p && !p.isCreative())) {
                hit.hurt(damageSources().mobAttack(QuadBeetleEntity.this), (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * 1.4f);
                hit.knockback(1.5, -direction.x, -direction.z);
                level().playSound(null, getX(), getY(), getZ(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 0.6f, 0.6f);
                ticks = 40;
            }
            if (horizontalCollision) ticks = 40;
        }

        @Override
        public void stop() {
            cooldown = 80 + random.nextInt(60);
        }
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getAmbientSound() {
        return SoundEvents.SILVERFISH_AMBIENT;
    }

    @Override
    public float getVoicePitch() {
        return 0.4f;
    }
}

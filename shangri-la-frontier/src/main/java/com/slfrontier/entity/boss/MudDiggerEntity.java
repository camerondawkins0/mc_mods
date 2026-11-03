package com.slfrontier.entity.boss;

import net.minecraft.server.level.ServerLevel;
import com.slfrontier.entity.FrontierBoss;
import com.slfrontier.fx.Fx;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
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
    public static final int BITE = 0, BURROW = 1, TAIL = 2, QUAKE = 3;
    private static final int MUD = 0xFFB89A5A;
    private static final float BITE_RANGE = 4f, BITE_ANGLE = 100;
    private boolean quaked;
    private Vec3 aim = Vec3.ZERO;
    private static final EntityDataAccessor<Boolean> BURROWED = SynchedEntityData.defineId(MudDiggerEntity.class, EntityDataSerializers.BOOLEAN);

    public MudDiggerEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level, 20, 600, BossEvent.BossBarColor.YELLOW);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 450)
                .add(Attributes.ATTACK_DAMAGE, 13)
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
    protected int bossPhase() {
        return getHealth() < getMaxHealth() * 0.2f ? 2 : 1;
    }

    @Override
    protected String phaseHint(int phase) {
        return phase == 2 ? "message.slfrontier.phase.mud_digger.2" : null;
    }

    @Override
    protected int chooseAttack(LivingEntity target, double distance) {
        if (!quaked && getHealth() < getMaxHealth() * 0.2f) return QUAKE;
        if (distance < 3.5) return pick(BITE, 5, TAIL, 3, BURROW, 1);
        if (distance <= 8) return pick(BURROW, 2, -1, 5);
        return pick(BURROW, 5, -1, 2);
    }

    /** Out of reach up on a pillar: it digs under them and erupts right there. */
    @Override
    protected int gapCloser(LivingEntity target, double distance) {
        return BURROW;
    }

    @Override
    protected int attackLength(int attack) {
        return switch (attack) {
            case BITE -> 18;
            case BURROW -> 52;
            case TAIL -> 22;
            case QUAKE -> 70;
            default -> 10;
        };
    }

    /** After an eruption it sits half-buried for a moment: its back is open. */
    @Override
    protected int recoveryAfter(int attack) {
        return switch (attack) {
            case BURROW -> 25;
            case QUAKE -> 30;
            default -> 0;
        };
    }

    @Override
    protected void tickRecovery(int attack, int ticksLeft) {
        if (ticksLeft % 4 == 0) {
            BlockParticleOption mud = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.MUD.defaultBlockState());
            particles(mud, getX(), getY() + 0.2, getZ(), 8, 0.9, 0.05);
            if (ticksLeft % 12 == 0) playSound(SoundEvents.MUD_BREAK, 1.2f, 0.6f);
        }
    }

    @Override
    protected void onAttackEnd(int attack, boolean interrupted) {
        if (isBurrowed()) unburrow();
    }

    @Override
    protected void onReset() {
        quaked = false;
        if (isBurrowed()) unburrow();
    }

    private void burrow() {
        entityData.set(BURROWED, true);
        setInvisible(true);
    }

    private void unburrow() {
        entityData.set(BURROWED, false);
        setInvisible(false);
    }

    @Override
    protected int animDelay(int attack) {
        return attack == BITE ? 2 : 0;
    }

    @Override
    protected void tickAttack(int attack, int tick, LivingEntity target) {
        switch (attack) {
            case BITE -> {
                if (tick == 1) tellCone(yawToward(target), BITE_RANGE, BITE_ANGLE, true, 10);
                if (tick == 11) {
                    coneAttack(BITE_RANGE, BITE_ANGLE, 1.0f, 0.4, 0);
                    playSound(SoundEvents.RAVAGER_ATTACK, 1.5f, 0.8f);
                }
            }
            case TAIL -> {
                if (tick == 1) tellCircle(position(), 4.2f, true, 11);
                if (tick == 12) {
                    Fx.arc(this, 4.2f, 0, 360, MUD, 10);
                    hitAround(4.2, 0.9f, -1, 1.0);
                    particles(ParticleTypes.SWEEP_ATTACK, getX(), getY() + 0.6, getZ(), 4, 1.5, 0);
                }
            }
            case BURROW -> {
                BlockParticleOption mud = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.MUD.defaultBlockState());
                if (tick == 6) {
                    burrow();
                    playSound(SoundEvents.ROOTED_DIRT_BREAK, 2f, 0.5f);
                }
                if (tick == 34) {
                    aim = target != null ? target.position() : position();
                    tellCircle(aim, 3.2f, false, 16);
                }
                if (tick > 6 && tick < 46) {
                    if (target != null && tick < 34) {
                        Vec3 to = target.position().subtract(position());
                        Vec3 step = to.length() > 0.4 ? to.normalize().scale(0.45) : Vec3.ZERO;
                        setDeltaMovement(step.x, getDeltaMovement().y, step.z);
                    }
                    particles(mud, getX(), getY() + 0.1, getZ(), 6, 0.6, 0.1);
                }
                if (tick == 50) {
                    teleportTo(aim.x, aim.y, aim.z);
                    Fx.ring(level(), position(), 3.6f, MUD, 12, 0.6f);
                    unburrow();
                    setDeltaMovement(0, 0.6, 0);
                    needsSync = true;
                    for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(3, 1, 3), this::isEnemy)) {
                        e.setDeltaMovement(e.getDeltaMovement().add(0, 0.9, 0));
                        e.hurtMarked = true;
                    }
                    hitAround(3.2, 1.3f, -1, 0.2, UNPARRYABLE);
                    particles(mud, getX(), getY() + 0.5, getZ(), 80, 1.5, 0.3);
                    playSound(SoundEvents.ROOTED_DIRT_BREAK, 3f, 0.4f);
                }
            }
            case QUAKE -> {
                // Its last resort: dive, shake the whole swamp so nobody can run, then launch its prey.
                BlockParticleOption mud = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.MUD.defaultBlockState());
                // (The phase cue at 20% HP already told everyone what is coming.)
                if (tick == 1) quaked = true;
                if (tick == 8) {
                    burrow();
                    playSound(SoundEvents.ROOTED_DIRT_BREAK, 2f, 0.4f);
                }
                if (tick >= 20 && tick <= 50 && tick % 10 == 0) {
                    Fx.ring(level(), position(), 14f, MUD, 14, 0.9f);
                    particles(mud, getX(), getY() + 0.2, getZ(), 60, 6, 0.2);
                    playSound(SoundEvents.ROOTED_DIRT_BREAK, 3f, 0.3f);
                    for (LivingEntity e : enemiesNear(16)) {
                        if (e.onGround()) e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 5), this);
                    }
                }
                if (tick == 52) {
                    aim = target != null ? target.position() : position();
                    tellCircle(aim, 3.0f, false, 14);
                }
                if (tick == 66) {
                    teleportTo(aim.x, aim.y, aim.z);
                    unburrow();
                    setDeltaMovement(0, 0.9, 0);
                    needsSync = true;
                    for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(3, 1, 3), this::isEnemy)) {
                        e.setDeltaMovement(e.getDeltaMovement().add(0, 1.2, 0));
                        e.hurtMarked = true;
                    }
                    hitAt(aim, 3.0, 1.2f, 0.2, UNPARRYABLE);
                    Fx.flash(level(), aim.add(0, 1, 0), 3f, MUD, 8, 1.0f);
                    particles(mud, getX(), getY() + 0.5, getZ(), 120, 2.0, 0.4);
                    playSound(SoundEvents.ROOTED_DIRT_BREAK, 3f, 0.4f);
                }
            }
            default -> {
            }
        }
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (isBurrowed() && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            shrugOff(source);
            return false;
        }
        return super.hurtServer(level, source, amount);
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof net.minecraft.server.level.ServerLevel server) {
            com.slfrontier.quest.Story.onMudDiggerSlain(server, this, source.getEntity());
        }
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

package com.slfrontier.entity.boss;

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
    private static final int MUD = 0xFFB89A5A, WARN = 0xFFFF6A3A;
    private boolean quaked;
    private Vec3 aim = Vec3.ZERO;
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
        if (!quaked && getHealth() < getMaxHealth() * 0.2f) return QUAKE;
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
            case QUAKE -> 80;
            default -> 10;
        };
    }

    @Override
    protected void tickAttack(int attack, int tick, LivingEntity target) {
        switch (attack) {
            case BITE -> {
                if (tick == 1) Fx.coneAt(level(), position(), getYRot(), 4f, 60, WARN, 8);
                if (tick == 9) hitAround(3.5, 1.0f, 0.3, 0.4);
            }
            case TAIL -> {
                if (tick == 1) Fx.telegraph(level(), position(), 4.2f, WARN, 11);
                if (tick == 12) {
                    Fx.arc(this, 4.2f, 0, 360, MUD, 10);
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
                if (tick == 34 && target != null) {
                    aim = target.position();
                    Fx.telegraph(level(), aim, 3.2f, WARN, 16);
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
            case QUAKE -> {
                // Its last resort: dive, shake the whole swamp so nobody can run, then launch its prey.
                BlockParticleOption mud = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.MUD.defaultBlockState());
                if (tick == 1) {
                    quaked = true;
                    for (net.minecraft.server.level.ServerPlayer p : playersNear(24)) {
                        p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.slfrontier.mud_digger.quake")
                                .withStyle(net.minecraft.ChatFormatting.GOLD, net.minecraft.ChatFormatting.BOLD), true);
                    }
                }
                if (tick == 8) {
                    entityData.set(BURROWED, true);
                    setInvisible(true);
                    playSound(SoundEvents.ROOTED_DIRT_BREAK, 2f, 0.4f);
                }
                if (tick >= 20 && tick <= 50 && tick % 10 == 0) {
                    Fx.ring(level(), position(), 14f, MUD, 14, 0.9f);
                    particles(mud, getX(), getY() + 0.2, getZ(), 60, 6, 0.2);
                    playSound(SoundEvents.ROOTED_DIRT_BREAK, 3f, 0.3f);
                    for (LivingEntity e : enemiesNear(16)) {
                        if (e.onGround()) e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 5), this);
                    }
                }
                if (tick == 52 && target != null) {
                    aim = target.position();
                    Fx.telegraph(level(), aim, 3.0f, WARN, 14);
                }
                if (tick == 66) {
                    teleportTo(aim.x, aim.y, aim.z);
                    entityData.set(BURROWED, false);
                    setInvisible(false);
                    setDeltaMovement(0, 0.9, 0);
                    hasImpulse = true;
                    for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(3, 1, 3), this::isEnemy)) {
                        e.setDeltaMovement(e.getDeltaMovement().add(0, 1.2, 0));
                        e.hurtMarked = true;
                    }
                    hitAt(aim, 3.0, 1.2f, 0.2);
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
    public boolean hurt(DamageSource source, float amount) {
        if (isBurrowed() && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        return super.hurt(source, amount);
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

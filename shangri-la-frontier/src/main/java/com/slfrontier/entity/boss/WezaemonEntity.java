package com.slfrontier.entity.boss;

import com.slfrontier.entity.FrontierBoss;
import com.slfrontier.quest.Story;
import com.slfrontier.registry.ModEffects;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Wezaemon the Tombguard, one of the Seven Colossi. An armored undead samurai who
 * guards the grave of Setsuna of Distant Days in the Hidden Garden.
 *
 * <ul>
 *   <li>Phase 1: iaijutsu dash and three-cut combos.</li>
 *   <li>Phase 2: adds Sky Clear, a telegraphed 360° cut. Parry it to knock his sword
 *       away and break his stance.</li>
 *   <li>Phase 3: a grave aura burns everyone nearby — unless he is drenched in Holy Water.</li>
 * </ul>
 */
public class WezaemonEntity extends FrontierBoss {
    public static final int IAI = 0, TRIPLE = 1, SKY_CLEAR = 2, STAGGER = 3;
    private static final EntityDataAccessor<Boolean> DISARMED = SynchedEntityData.defineId(WezaemonEntity.class, EntityDataSerializers.BOOLEAN);
    private static final DustParticleOptions SKY = new DustParticleOptions(new Vector3f(0.55f, 0.85f, 1f), 1.6f);

    private int disarmTicks;
    private Vec3 dashDir = Vec3.ZERO;

    public WezaemonEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level, 50, 3000, BossEvent.BossBarColor.BLUE);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 750)
                .add(Attributes.ATTACK_DAMAGE, 14)
                .add(Attributes.ARMOR, 16)
                .add(Attributes.ARMOR_TOUGHNESS, 8)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.9)
                .add(Attributes.FOLLOW_RANGE, 48);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DISARMED, false);
    }

    public boolean isDisarmed() {
        return entityData.get(DISARMED);
    }

    public int phase() {
        float f = getHealth() / getMaxHealth();
        return f > 0.66f ? 1 : f > 0.33f ? 2 : 3;
    }

    /** Called when a player perfectly parries Sky Clear. */
    public void onParried(ServerPlayer by) {
        if (attack != SKY_CLEAR || isDisarmed()) return;
        entityData.set(DISARMED, true);
        disarmTicks = 120;
        startAttack(STAGGER);
        playSound(SoundEvents.ANVIL_LAND, 2f, 0.5f);
        particles(ParticleTypes.FLASH, getX(), getY() + 2, getZ(), 1, 0, 0);
        by.serverLevel().getServer().getPlayerList().broadcastSystemMessage(
                Component.translatable("message.slfrontier.wezaemon.parried", by.getDisplayName()).withStyle(ChatFormatting.AQUA), false);
    }

    @Override
    protected int chooseAttack(LivingEntity target, double distance) {
        if (isDisarmed()) return -1;
        int roll = random.nextInt(10);
        if (phase() >= 2 && roll < 3 && distance < 7) return SKY_CLEAR;
        if (distance > 5 || roll == 3) return IAI;
        if (distance < 4) return TRIPLE;
        return -1;
    }

    @Override
    protected int attackLength(int attack) {
        return switch (attack) {
            case IAI -> 28;
            case TRIPLE -> 32;
            case SKY_CLEAR -> 42;
            case STAGGER -> 100;
            default -> 10;
        };
    }

    @Override
    protected void tickAttack(int attack, int tick, LivingEntity target) {
        switch (attack) {
            case IAI -> {
                if (tick < 12 && tick % 3 == 0) particles(ParticleTypes.ENCHANTED_HIT, getX(), getY() + 1.2, getZ(), 6, 0.4, 0.1);
                if (tick == 12 && target != null) {
                    Vec3 d = target.position().subtract(position());
                    dashDir = new Vec3(d.x, 0, d.z).normalize();
                    setDeltaMovement(dashDir.x * 1.8, 0.05, dashDir.z * 1.8);
                    hasImpulse = true;
                    playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 2f, 1.5f);
                }
                if (tick >= 13 && tick <= 18) {
                    particles(ParticleTypes.SWEEP_ATTACK, getX(), getY() + 1.2, getZ(), 1, 0.2, 0);
                    hitAround(2.5, 1.3f, -1, 0.8);
                }
            }
            case TRIPLE -> {
                if (tick == 8 || tick == 16 || tick == 24) {
                    hitAround(4.0, 0.7f, 0.2, 0.4);
                    particles(ParticleTypes.SWEEP_ATTACK, getX(), getY() + 1.4, getZ(), 2, 1.2, 0);
                    playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.5f, 1.2f);
                }
            }
            case SKY_CLEAR -> {
                if (tick % 6 == 0 && tick < 30) ring(SKY, 7, 40, 0.15);
                if (tick == 2) {
                    for (ServerPlayer p : playersNear(20)) {
                        p.displayClientMessage(Component.translatable("message.slfrontier.wezaemon.sky_clear").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), true);
                    }
                }
                if (tick == 32) {
                    hitAround(7.2, 2.0f, -1, 1.2);
                    ring(ParticleTypes.SWEEP_ATTACK, 5, 16, 1.2);
                    ring(SKY, 7, 60, 1.2);
                    playSound(SoundEvents.WARDEN_SONIC_BOOM, 1.5f, 1.4f);
                }
            }
            case STAGGER -> {
                if (tick % 10 == 0) particles(ParticleTypes.CRIT, getX(), getY() + 2.6, getZ(), 6, 0.4, 0.1);
            }
            default -> {
            }
        }
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!(level() instanceof ServerLevel server)) return;
        if (disarmTicks > 0 && --disarmTicks == 0) {
            entityData.set(DISARMED, false);
            playSound(SoundEvents.ARMOR_EQUIP_IRON.value(), 2f, 0.6f);
        }
        if (phase() == 3 && tickCount % 20 == 0) {
            if (hasEffect(ModEffects.DRENCHED)) {
                server.sendParticles(ParticleTypes.FALLING_WATER, getX(), getY() + 2, getZ(), 10, 0.5, 0.5, 0.5, 0);
            } else {
                ring(ParticleTypes.SOUL_FIRE_FLAME, 5, 24, 0.3);
                for (ServerPlayer p : playersNear(5)) {
                    if (!p.isCreative()) p.hurt(damageSources().indirectMagic(this, this), 3f);
                }
            }
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isDisarmed()) amount *= 1.5f;
        return super.hurt(source, amount);
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel server) {
            Story.onWezaemonSlain(server, this, source.getEntity());
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.SKELETON_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.IRON_GOLEM_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.SKELETON_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return 0.5f;
    }
}

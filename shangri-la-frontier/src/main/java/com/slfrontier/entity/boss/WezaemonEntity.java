package com.slfrontier.entity.boss;

import com.slfrontier.entity.FrontierBoss;
import com.slfrontier.fx.Fx;
import com.slfrontier.quest.Story;
import com.slfrontier.registry.ModEffects;
import com.slfrontier.registry.ModEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
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
 *   <li>Phase 1: iaijutsu dash, three-cut combos and Tachikaze, a flying wind blade.</li>
 *   <li>Phase 2: summons Kirin, his tactical mount, and adds Sky Clear (a telegraphed 360°
 *       cut; parry it to knock his sword away and break his stance) and Raisho, a zig-zag
 *       of lightning-fast thrusts.</li>
 *   <li>Phase 3: a grave aura burns everyone nearby — unless he is drenched in Holy Water —
 *       and he unleashes Tensei, a heaven-splitting cleave.</li>
 * </ul>
 * Every attack is telegraphed on the ground before it lands.
 */
public class WezaemonEntity extends FrontierBoss {
    public static final int IAI = 0, TRIPLE = 1, SKY_CLEAR = 2, STAGGER = 3, TACHIKAZE = 4, RAISHO = 5, TENSEI = 6;
    private static final EntityDataAccessor<Boolean> DISARMED = SynchedEntityData.defineId(WezaemonEntity.class, EntityDataSerializers.BOOLEAN);
    private static final DustParticleOptions SKY = new DustParticleOptions(new Vector3f(0.55f, 0.85f, 1f), 1.6f);
    private static final int CYAN = 0xFF8FE8FF, WHITE = 0xFFEFFAFF, THUNDER = 0xFFFFF07A, GRAVE = 0xFF6A9CFF;

    private int disarmTicks;
    private int tenseiCooldown = 200;
    private boolean kirinSummoned;
    private Vec3 dashDir = Vec3.ZERO;
    private Vec3 aim = Vec3.ZERO;

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
        Fx.flash(level(), position().add(0, 2, 0), 3f, WHITE, 10, 0.6f);
        by.serverLevel().getServer().getPlayerList().broadcastSystemMessage(
                Component.translatable("message.slfrontier.wezaemon.parried", by.getDisplayName()).withStyle(ChatFormatting.AQUA), false);
    }

    @Override
    protected int chooseAttack(LivingEntity target, double distance) {
        if (isDisarmed()) return -1;
        int roll = random.nextInt(12);
        if (phase() == 3 && tenseiCooldown <= 0 && distance < 12) return TENSEI;
        if (phase() >= 2 && roll < 3 && distance < 7) return SKY_CLEAR;
        if (phase() >= 2 && roll == 3 && distance < 14) return RAISHO;
        if (distance > 6 && distance < 16 && roll < 7) return TACHIKAZE;
        if (distance > 5 || roll == 4) return IAI;
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
            case TACHIKAZE -> 26;
            case RAISHO -> 32;
            case TENSEI -> 52;
            default -> 10;
        };
    }

    private Vec3 chest() {
        return position().add(0, getBbHeight() * 0.55, 0);
    }

    @Override
    protected void tickAttack(int attack, int tick, LivingEntity target) {
        switch (attack) {
            case IAI -> {
                if (tick == 1 && target != null) {
                    aim = target.position();
                    Fx.coneAt(level(), position(), Fx.yawTo(position(), aim), (float) Math.min(14, distanceTo(target) + 3), 22, CYAN, 11);
                }
                if (tick < 12 && tick % 3 == 0) particles(ParticleTypes.ENCHANTED_HIT, getX(), getY() + 1.2, getZ(), 6, 0.4, 0.1);
                if (tick == 12) {
                    Vec3 d = aim.subtract(position());
                    dashDir = new Vec3(d.x, 0, d.z).normalize();
                    setDeltaMovement(dashDir.x * 1.8, 0.05, dashDir.z * 1.8);
                    hasImpulse = true;
                    playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 2f, 1.5f);
                    Fx.arcAt(level(), chest(), Fx.yawTo(position(), aim), 3.2f, 8, 200, WHITE, 8);
                }
                if (tick >= 13 && tick <= 18) {
                    particles(ParticleTypes.SWEEP_ATTACK, getX(), getY() + 1.2, getZ(), 1, 0.2, 0);
                    hitAround(2.5, 1.3f, -1, 0.8);
                }
            }
            case TRIPLE -> {
                if (tick == 8 || tick == 16 || tick == 24) {
                    hitAround(4.0, 0.7f, 0.2, 0.4);
                    float tilt = tick == 8 ? 35 : tick == 16 ? -35 : 80;
                    Fx.arc(this, 3.6f, tilt, 170, WHITE, 8);
                    playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.5f, 1.2f);
                }
            }
            case SKY_CLEAR -> {
                if (tick == 1) Fx.telegraph(level(), position(), 7.2f, CYAN, 31);
                if (tick % 6 == 0 && tick < 30) ring(SKY, 7, 40, 0.15);
                if (tick == 2) {
                    for (ServerPlayer p : playersNear(20)) {
                        p.displayClientMessage(Component.translatable("message.slfrontier.wezaemon.sky_clear").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), true);
                    }
                }
                if (tick == 32) {
                    hitAround(7.2, 2.0f, -1, 1.2);
                    Fx.arc(this, 7f, 0, 360, CYAN, 12);
                    Fx.ring(level(), position(), 7.5f, CYAN, 14, 0.8f);
                    Fx.flash(level(), chest(), 4f, WHITE, 8, 0);
                    playSound(SoundEvents.WARDEN_SONIC_BOOM, 1.5f, 1.4f);
                }
            }
            case STAGGER -> {
                if (tick % 10 == 0) particles(ParticleTypes.CRIT, getX(), getY() + 2.6, getZ(), 6, 0.4, 0.1);
            }
            case TACHIKAZE -> {
                // A wide draw that releases a flying blade of wind along a line.
                if (tick == 1 && target != null) {
                    aim = target.position();
                    Fx.coneAt(level(), position(), Fx.yawTo(position(), aim), 16, 14, CYAN, 10);
                }
                if (tick == 11) {
                    float yaw = Fx.yawTo(position(), aim);
                    Vec3 dir = Vec3.directionFromRotation(0, yaw);
                    Fx.waveAt(level(), chest(), yaw, 3.2f, 0, 16, CYAN, 14, 0.3f);
                    hitLine(chest(), chest().add(dir.scale(16)), 1.6, 1.1f, 0.9);
                    playSound(SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), 2f, 0.8f);
                }
            }
            case RAISHO -> {
                // Thunder step: three zig-zag dashes, each ending in a thrust.
                if (target != null && (tick == 1 || tick == 11 || tick == 21)) {
                    aim = target.position();
                    Fx.coneAt(level(), position(), Fx.yawTo(position(), aim), 8, 18, THUNDER, 7);
                }
                if (target != null && (tick == 7 || tick == 17 || tick == 27)) {
                    Vec3 to = aim.subtract(position());
                    Vec3 side = new Vec3(-to.z, 0, to.x).normalize().scale(tick == 17 ? -2.2 : 2.2);
                    Vec3 goal = aim.add(side.scale(0.4));
                    dashToward(goal, 1.6, 0.1);
                    Fx.flash(level(), chest(), 1.6f, THUNDER, 6, 0.25f);
                    Fx.pillar(level(), position(), 0.5f, 4f, THUNDER, 8);
                    playSound(SoundEvents.TRIDENT_THUNDER.value(), 0.8f, 1.6f);
                }
                if ((tick >= 8 && tick <= 10) || (tick >= 18 && tick <= 20) || (tick >= 28 && tick <= 30)) {
                    if (hitAround(2.6, 0.8f, 0.0, 0.5) > 0) particles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 1.5, getZ(), 12, 0.6, 0.2);
                }
            }
            case TENSEI -> {
                // Raise the sword to the heavens, then split the ground in front of him.
                if (tick == 1) {
                    tenseiCooldown = 360;
                    aim = target != null ? target.position() : position().add(getLookAngle());
                    Fx.coneAt(level(), position(), Fx.yawTo(position(), aim), 18, 40, GRAVE, 39);
                    Fx.telegraph(level(), position(), 4f, GRAVE, 39);
                    for (ServerPlayer p : playersNear(24)) {
                        p.displayClientMessage(Component.translatable("message.slfrontier.wezaemon.tensei").withStyle(ChatFormatting.BLUE, ChatFormatting.BOLD), true);
                    }
                }
                if (tick == 4) Fx.pillar(level(), position(), 1.2f, 14f, GRAVE, 36);
                if (tick < 40 && tick % 4 == 0) particles(ParticleTypes.SOUL_FIRE_FLAME, getX(), getY() + 3, getZ(), 6, 0.6, 0.05);
                if (tick == 40) {
                    float yaw = Fx.yawTo(position(), aim);
                    Vec3 dir = Vec3.directionFromRotation(0, yaw);
                    hitAround(4.5, 1.8f, -1, 1.4);
                    hitLine(position().add(0, 1, 0), position().add(0, 1, 0).add(dir.scale(18)), 2.2, 2.4f, 1.6);
                    Fx.waveAt(level(), chest(), yaw, 4.5f, 90, 18, GRAVE, 16, 1.2f);
                    Fx.arc(this, 5f, 90, 180, WHITE, 10);
                    Fx.flash(level(), chest(), 5f, GRAVE, 10, 0);
                    Fx.ring(level(), position(), 6f, GRAVE, 16, 0);
                    playSound(SoundEvents.WARDEN_SONIC_BOOM, 2f, 0.7f);
                    playSound(SoundEvents.GENERIC_EXPLODE.value(), 1.5f, 0.6f);
                }
            }
            default -> {
            }
        }
    }

    /** Phase two: Kirin, the tactical horse-mech, answers its master's call. */
    private void summonKirin(ServerLevel server) {
        kirinSummoned = true;
        KirinEntity kirin = ModEntities.KIRIN.get().create(server);
        if (kirin == null) return;
        Vec3 at = position().add(getLookAngle().multiply(-3, 0, -3));
        kirin.moveTo(at.x, getY(), at.z, getYRot(), 0);
        kirin.setMaster(this);
        kirin.setTarget(getTarget());
        server.addFreshEntity(kirin);
        Fx.pillar(server, kirin.position(), 1.5f, 10f, CYAN, 20);
        Fx.ring(server, kirin.position(), 5f, CYAN, 16, 0.5f);
        playSound(SoundEvents.IRON_GOLEM_REPAIR, 2f, 0.6f);
        for (ServerPlayer p : playersNear(32)) {
            p.displayClientMessage(Component.translatable("message.slfrontier.wezaemon.kirin").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), true);
        }
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!(level() instanceof ServerLevel server)) return;
        if (tenseiCooldown > 0) tenseiCooldown--;
        if (!kirinSummoned && phase() >= 2 && getTarget() != null) summonKirin(server);
        if (phase() == 3 && tickCount % 4 == 0) {
            // Ghostly blue and red flames.
            particles(random.nextBoolean() ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME,
                    getX(), getY() + 1 + random.nextDouble() * 1.5, getZ(), 2, 0.4, 0.02);
        }
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
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("KirinSummoned", kirinSummoned);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        kirinSummoned = tag.getBoolean("KirinSummoned");
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

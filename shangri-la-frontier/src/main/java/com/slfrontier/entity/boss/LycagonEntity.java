package com.slfrontier.entity.boss;

import com.slfrontier.entity.FrontierBoss;
import com.slfrontier.entity.mob.ShadowWolfEntity;
import com.slfrontier.fx.Fx;
import com.slfrontier.quest.QuestStage;
import com.slfrontier.quest.Story;
import com.slfrontier.registry.ModAttachments;
import com.slfrontier.registry.ModEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.UUID;

/**
 * Lycagon the Nightslayer, one of the Seven Colossi. A giant black wolf that hunts
 * only at night.
 *
 * <p>The first time a player meets it (story stage NIGHTSLAYER_ENCOUNTER) it cannot
 * really be beaten: it toys with the player, then takes their legs, leaves them at
 * 1 HP and brands them with Lycagon's Mark before vanishing. With the mark, the
 * player cannot wear chest or leg armor in the Frontier until Lycagon is slain.
 */
public class LycagonEntity extends FrontierBoss {
    public static final int BITE = 0, CLAW = 1, SHADOW_DASH = 2, HOWL = 3, NIGHT_REND = 4, VANISH = 5, SHADOW_CLONE = 6;
    private static final int PURPLE = 0xFFB040E0, PINK = 0xFFFF60E0;
    private static final DustParticleOptions BLOOD = new DustParticleOptions(new Vector3f(0.7f, 0.05f, 0.1f), 1.5f);
    private static final DustParticleOptions AURA = new DustParticleOptions(new Vector3f(0.45f, 0.12f, 0.75f), 2.2f);
    private static final DustParticleOptions AURA_PINK = new DustParticleOptions(new Vector3f(0.85f, 0.25f, 0.85f), 1.4f);

    public enum Mode { NORMAL, ENCOUNTER, FINAL }

    private Mode mode = Mode.NORMAL;
    private UUID summoner;
    private int encounterTicks;
    private int cloneCooldown = 300;
    private Vec3 aim = Vec3.ZERO;

    public LycagonEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level, 60, 4000, BossEvent.BossBarColor.PURPLE);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 900)
                .add(Attributes.ATTACK_DAMAGE, 16)
                .add(Attributes.ARMOR, 12)
                .add(Attributes.ARMOR_TOUGHNESS, 6)
                .add(Attributes.MOVEMENT_SPEED, 0.34)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.FOLLOW_RANGE, 64)
                .add(Attributes.STEP_HEIGHT, 1.5);
    }

    /** Sets the fight up for the player who touched the altar. */
    public void configureFor(ServerPlayer player) {
        summoner = player.getUUID();
        QuestStage stage = ModAttachments.get(player).stage();
        if (stage == QuestStage.NIGHTSLAYER_ENCOUNTER) mode = Mode.ENCOUNTER;
        else if (stage == QuestStage.NIGHTSLAYER_FINAL) mode = Mode.FINAL;
        else mode = Mode.NORMAL;
    }

    public Mode mode() {
        return mode;
    }

    private boolean phaseTwo() {
        return getHealth() < getMaxHealth() * 0.5f;
    }

    @Override
    protected int chooseAttack(LivingEntity target, double distance) {
        int roll = random.nextInt(12);
        if (phaseTwo() && cloneCooldown <= 0 && mode != Mode.ENCOUNTER) return SHADOW_CLONE;
        if (phaseTwo() && roll == 0) return HOWL;
        if (phaseTwo() && roll == 1 && distance < 8) return NIGHT_REND;
        if (roll == 2 || (distance > 10 && roll < 6)) return VANISH;
        if (distance > 7 || roll == 3) return SHADOW_DASH;
        if (distance < 4.5) return roll < 7 ? BITE : CLAW;
        return -1;
    }

    @Override
    protected int attackLength(int attack) {
        return switch (attack) {
            case BITE -> 14;
            case CLAW -> 24;
            case SHADOW_DASH -> 22;
            case HOWL -> 30;
            case NIGHT_REND -> 36;
            case VANISH -> 34;
            case SHADOW_CLONE -> 32;
            default -> 10;
        };
    }

    @Override
    protected int cooldownAfter(int attack) {
        return (phaseTwo() ? 8 : 16) + random.nextInt(14);
    }

    @Override
    protected void tickAttack(int attack, int tick, LivingEntity target) {
        switch (attack) {
            case BITE -> {
                if (tick == 1) Fx.coneAt(level(), position(), getYRot(), 5f, 70, PURPLE, 7);
                if (tick == 8) hitAround(4.5, 1.0f, 0.3, 0.6);
            }
            case CLAW -> {
                if (tick == 1) Fx.coneAt(level(), position(), getYRot(), 5f, 150, PURPLE, 16);
                if (tick == 8 || tick == 17) {
                    hitAround(4.5, 0.8f, 0.0, 0.8);
                    Fx.arc(this, 4.5f, tick == 8 ? 40 : -40, 150, PURPLE, 8);
                }
            }
            case SHADOW_DASH -> {
                if (tick == 2 && target != null) Fx.coneAt(level(), position(), Fx.yawTo(position(), target.position()), 7, 30, PINK, 8);
                if (tick == 1 && target != null) {
                    particles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1, getZ(), 40, 1.0, 0.02);
                    Vec3 behind = target.position().subtract(target.getLookAngle().multiply(1, 0, 1).normalize().scale(4));
                    teleportTo(behind.x, target.getY(), behind.z);
                    particles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1, getZ(), 40, 1.0, 0.02);
                    playSound(SoundEvents.ENDERMAN_TELEPORT, 1.5f, 0.5f);
                }
                if (tick == 10 && target != null) dashToward(target.position(), 1.4, 0.35);
                if (tick >= 12 && tick <= 16 && hitAround(3.5, 1.2f, -1, 1.2) > 0) attackTick = 17;
            }
            case HOWL -> {
                if (tick == 10) {
                    Fx.ring(level(), position(), 14f, PURPLE, 24, 0.5f);
                    Fx.pillar(level(), position(), 1.6f, 12f, PURPLE, 20);
                    playSound(SoundEvents.WOLF_HOWL, 5f, 0.4f);
                    for (ServerPlayer p : playersNear(24)) {
                        p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 140, 0), this);
                    }
                    summonShadows(2);
                }
            }
            case NIGHT_REND -> {
                if (tick == 4) {
                    setDeltaMovement(0, 1.1, 0);
                    hasImpulse = true;
                }
                if (tick == 4) Fx.telegraph(level(), position(), 6.5f, PURPLE, 22);
                if (tick == 8) ring(BLOOD, 6, 36, 0.2);
                if (tick == 26) {
                    hitAround(6.5, 1.4f, -1, 1.5);
                    Fx.ring(level(), position(), 7f, PURPLE, 14, 1.0f);
                    Fx.flash(level(), position().add(0, 1, 0), 4f, PINK, 8, 0);
                    ring(ParticleTypes.EXPLOSION, 4, 8, 0.3);
                    playSound(SoundEvents.DRAGON_FIREBALL_EXPLODE, 2f, 0.6f);
                }
            }
            case VANISH -> {
                // Melts into the shadows, then erupts under its prey.
                if (tick == 14) {
                    particles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1, getZ(), 80, 1.4, 0.03);
                    setInvisible(true);
                    playSound(SoundEvents.ENDERMAN_TELEPORT, 1.5f, 0.4f);
                }
                if (tick == 16 && target != null) {
                    aim = target.position();
                    Fx.telegraph(level(), aim, 3.8f, PINK, 12);
                }
                if (tick == 28) {
                    teleportTo(aim.x, aim.y, aim.z);
                    setInvisible(false);
                    setDeltaMovement(0, 0.5, 0);
                    hasImpulse = true;
                    hitAt(aim, 3.8, 1.3f, 1.2);
                    Fx.ring(level(), aim, 4f, PINK, 10, 0.6f);
                    particles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1, getZ(), 60, 1.4, 0.03);
                    playSound(SoundEvents.WOLF_GROWL, 3f, 0.4f);
                }
            }
            case SHADOW_CLONE -> {
                if (tick == 20) {
                    cloneCooldown = 600;
                    spawnClone();
                }
            }
            default -> {
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide && !isInvisible()) aura(random.nextInt(3) + 2);
    }

    /** The flickering purple shadow-flame that edges Lycagon's silhouette. */
    private void aura(int count) {
        float yaw = yBodyRot * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(yaw), fz = Mth.cos(yaw);
        float size = getBbHeight() / 3.4f;
        for (int i = 0; i < count; i++) {
            double along = (random.nextDouble() * 5.5 - 3.0) * size;
            double side = (random.nextDouble() - 0.5) * 2.6 * size;
            double x = getX() + fx * along - fz * side;
            double z = getZ() + fz * along + fx * side;
            double y = getY() + (0.4 + random.nextDouble() * 2.8) * size;
            level().addParticle(random.nextInt(3) == 0 ? AURA_PINK : AURA, x, y, z, 0, 0.03, 0);
        }
        if (random.nextInt(3) == 0) {
            level().addParticle(ParticleTypes.REVERSE_PORTAL, getX() + (random.nextDouble() - 0.5) * 2 * size,
                    getY() + random.nextDouble() * 3 * size, getZ() + (random.nextDouble() - 0.5) * 2 * size, 0, 0.05, 0);
        }
    }

    /** Splits off a short-lived shadow of itself that hunts alongside it. */
    private void spawnClone() {
        if (!(level() instanceof ServerLevel server)) return;
        LycagonShadowEntity clone = ModEntities.LYCAGON_SHADOW.get().create(server);
        if (clone == null) return;
        clone.moveTo(getX(), getY(), getZ(), getYRot(), 0);
        clone.makeClone(20 * 15);
        clone.setTarget(getTarget());
        server.addFreshEntity(clone);
        Fx.flash(server, position().add(0, 2, 0), 4f, PURPLE, 10, 0.4f);
        server.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1.2, getZ(), 60, 1.2, 1.2, 1.2, 0.02);
    }

    private void summonShadows(int count) {
        if (!(level() instanceof ServerLevel server)) return;
        for (int i = 0; i < count; i++) {
            ShadowWolfEntity wolf = ModEntities.SHADOW_WOLF.get().create(server);
            if (wolf == null) continue;
            double a = random.nextDouble() * Math.PI * 2;
            wolf.moveTo(getX() + Math.cos(a) * 3, getY(), getZ() + Math.sin(a) * 3, getYRot(), 0);
            wolf.finalizeSpawn(server, server.getCurrentDifficultyAt(blockPosition()), MobSpawnType.MOB_SUMMONED, null);
            wolf.setTarget(getTarget());
            server.addFreshEntity(wolf);
            server.sendParticles(ParticleTypes.LARGE_SMOKE, wolf.getX(), wolf.getY() + 0.5, wolf.getZ(), 20, 0.4, 0.4, 0.4, 0.01);
        }
    }

    /** The first encounter is scripted: a blow that would kill the summoner marks them instead. */
    @Override
    protected float adjustDamage(LivingEntity target, float damage) {
        if (mode == Mode.ENCOUNTER && target instanceof ServerPlayer player && player.getUUID().equals(summoner)
                && damage >= player.getHealth() - 1) {
            markAndVanish(player);
            return 0;
        }
        return damage;
    }

    @Override
    protected void onHitTarget(LivingEntity target) {
        if (mode == Mode.ENCOUNTER && target instanceof ServerPlayer player && player.getUUID().equals(summoner)
                && player.getHealth() <= player.getMaxHealth() * 0.35f) {
            markAndVanish(player);
        }
    }

    /** The scripted end of the first encounter. */
    private void markAndVanish(ServerPlayer player) {
        if (isRemoved()) return;
        player.setHealth(1f);
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 4), this);
        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 400, 1), this);
        playSound(SoundEvents.WOLF_HOWL, 5f, 0.3f);
        Story.onMarkedByLycagon(player);
        vanish();
    }

    private void vanish() {
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1.2, getZ(), 120, 1.4, 1.4, 1.4, 0.03);
            server.sendParticles(ParticleTypes.SOUL, getX(), getY() + 1.2, getZ(), 40, 1.4, 1.4, 1.4, 0.03);
        }
        discard();
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!(level() instanceof ServerLevel server)) return;
        if (cloneCooldown > 0) cloneCooldown--;
        if (tickCount % 3 == 0) {
            server.sendParticles(ParticleTypes.SMOKE, getX(), getY() + 1.2, getZ(), 2, 0.8, 0.6, 0.8, 0.0);
        }
        // The Nightslayer recedes with the dawn, or if nobody is left to hunt.
        if ((server.isDay() && tickCount > 40) || idleTicks > 600) {
            server.getServer().getPlayerList().broadcastSystemMessage(
                    Component.translatable("message.slfrontier.lycagon.recedes").withStyle(ChatFormatting.DARK_PURPLE), false);
            vanish();
            return;
        }
        if (mode == Mode.ENCOUNTER) {
            encounterTicks++;
            Player player = summoner == null ? null : server.getPlayerByUUID(summoner);
            if (player instanceof ServerPlayer sp && (encounterTicks > 20 * 90 || getHealth() < getMaxHealth() * 0.6f)) {
                markAndVanish(sp);
            }
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (mode == Mode.ENCOUNTER) amount *= 0.2f;
        return super.hurt(source, amount);
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel server) {
            Story.onLycagonSlain(server, this, source.getEntity());
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("Mode", mode.name());
        if (summoner != null) tag.putUUID("Summoner", summoner);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        try {
            mode = Mode.valueOf(tag.getString("Mode"));
        } catch (IllegalArgumentException e) {
            mode = Mode.NORMAL;
        }
        if (tag.hasUUID("Summoner")) summoner = tag.getUUID("Summoner");
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.WOLF_GROWL;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.WOLF_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.WOLF_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return 0.45f;
    }

    @Override
    protected float getSoundVolume() {
        return 3f;
    }

    @Override
    public SoundSource getSoundSource() {
        return SoundSource.HOSTILE;
    }
}

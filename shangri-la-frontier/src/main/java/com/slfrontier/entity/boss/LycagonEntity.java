package com.slfrontier.entity.boss;

import com.slfrontier.entity.FrontierBoss;
import com.slfrontier.entity.mob.ShadowWolfEntity;
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
    public static final int BITE = 0, CLAW = 1, SHADOW_DASH = 2, HOWL = 3, NIGHT_REND = 4;
    private static final DustParticleOptions BLOOD = new DustParticleOptions(new Vector3f(0.7f, 0.05f, 0.1f), 1.5f);

    public enum Mode { NORMAL, ENCOUNTER, FINAL }

    private Mode mode = Mode.NORMAL;
    private UUID summoner;
    private int encounterTicks;

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
        int roll = random.nextInt(10);
        if (phaseTwo() && roll == 0) return HOWL;
        if (phaseTwo() && roll == 1 && distance < 8) return NIGHT_REND;
        if (distance > 7 || roll == 2) return SHADOW_DASH;
        if (distance < 4.5) return roll < 6 ? BITE : CLAW;
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
                if (tick == 8) hitAround(4.5, 1.0f, 0.3, 0.6);
            }
            case CLAW -> {
                if (tick == 8 || tick == 17) {
                    hitAround(4.5, 0.8f, 0.0, 0.8);
                    particles(ParticleTypes.SWEEP_ATTACK, getX(), getY() + 1.5, getZ(), 3, 1.5, 0);
                }
            }
            case SHADOW_DASH -> {
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
                if (tick == 8) ring(BLOOD, 6, 36, 0.2);
                if (tick == 26) {
                    hitAround(6.5, 1.4f, -1, 1.5);
                    ring(ParticleTypes.EXPLOSION, 4, 8, 0.3);
                    playSound(SoundEvents.DRAGON_FIREBALL_EXPLODE, 2f, 0.6f);
                }
            }
            default -> {
            }
        }
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

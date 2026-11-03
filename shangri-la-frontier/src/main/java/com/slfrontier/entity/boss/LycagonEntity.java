package com.slfrontier.entity.boss;

import com.slfrontier.entity.WolfVoice;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import com.slfrontier.entity.FrontierBoss;
import com.slfrontier.entity.mob.ShadowWolfEntity;
import com.slfrontier.fx.Fx;
import com.slfrontier.quest.QuestStage;
import com.slfrontier.quest.Story;
import com.slfrontier.registry.ModAttachments;
import com.slfrontier.registry.ModEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Lycagon the Nightslayer, one of the Seven Colossi. A giant black wolf that hunts
 * only at night.
 *
 * <p>The first time a player meets it (story stage NIGHTSLAYER_ENCOUNTER) it cannot
 * really be beaten: it toys with the player, then takes their legs, leaves them at
 * 1 HP and brands them with Lycagon's Mark before vanishing. With the mark, the
 * player cannot wear chest or leg armor in the Frontier until Lycagon is slain.
 *
 * <p>In a group this is decided per player: an encounter-stage player is always branded
 * instead of killed, but if someone at the final chapter (or who already slew it) is in the
 * fight, it fights in earnest and only lets the branded players go.
 */
public class LycagonEntity extends FrontierBoss {
    public static final int BITE = 0, CLAW = 1, SHADOW_DASH = 2, HOWL = 3, NIGHT_REND = 4, VANISH = 5, SHADOW_CLONE = 6, BACKSTEP = 7;
    private static final float BITE_RANGE = 5f, BITE_ANGLE = 110, CLAW_RANGE = 5f, CLAW_ANGLE = 150;
    private static final int PURPLE = 0xFFB040E0, PINK = 0xFFFF60E0;
    private static final DustParticleOptions BLOOD = new DustParticleOptions(0xB20D1A, 1.5f);
    private static final DustParticleOptions AURA = new DustParticleOptions(0x731FBF, 2.2f);
    private static final DustParticleOptions AURA_PINK = new DustParticleOptions(0xD940D9, 1.4f);

    public enum Mode { NORMAL, ENCOUNTER, FINAL }

    private Mode mode = Mode.NORMAL;
    private UUID summoner;
    private int encounterTicks;
    private int cloneCooldown = 300;
    private Vec3 aim = Vec3.ZERO;
    private double dashLength = 6;
    /** Encounter-stage players it already marked this fight: it leaves them be. */
    private final Set<UUID> spared = new HashSet<>();
    /** Playing out the scripted first encounter (see updateScripted). */
    private boolean scripted;
    /** In the middle of its bite, claw, backstep string. */
    private boolean inString;

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
        scripted = mode == Mode.ENCOUNTER;
        // Someone who already slew it gets a repeat fight: a quarter of the EXP and no Tearjewels.
        setRepeatKill(stage == QuestStage.COMPLETE || BossAltar.hasSlain(player, BossAltar.NIGHTSLAYER));
    }

    public Mode mode() {
        return mode;
    }

    private boolean phaseTwo() {
        return getHealth() < getMaxHealth() * 0.5f;
    }

    @Override
    protected int bossPhase() {
        return phaseTwo() ? 2 : 1;
    }

    @Override
    protected String phaseHint(int phase) {
        return phase == 2 ? "message.slfrontier.phase.lycagon.2" : null;
    }

    // ---- The scripted first encounter, per player --------------------------------------

    /**
     * A player at the encounter stage of the story: when this player is about to fall, Lycagon
     * takes their legs and brands them instead (once per fight).
     */
    private boolean isEncounterPlayer(ServerPlayer player) {
        return ModAttachments.get(player).stage() == QuestStage.NIGHTSLAYER_ENCOUNTER && !spared.contains(player.getUUID());
    }

    /** Someone who came to hunt it for real (the final chapter, or a veteran). */
    private static boolean isHunter(ServerPlayer player) {
        QuestStage stage = ModAttachments.get(player).stage();
        return stage == QuestStage.NIGHTSLAYER_FINAL || stage == QuestStage.COMPLETE;
    }

    /**
     * The encounter only plays out as the scripted, unwinnable meeting (shrugging off blows,
     * leaving at 60% or after 90 s) when it was woken by an encounter-stage player and nobody
     * here has come to hunt it for real; otherwise it fights in earnest.
     */
    private boolean updateScripted() {
        if (mode != Mode.ENCOUNTER) return scripted = false;
        for (ServerPlayer p : playersNear(64)) {
            if (isHunter(p)) return scripted = false;
        }
        return scripted = true;
    }

    // ---- Attacks ------------------------------------------------------------------------

    @Override
    protected int chooseAttack(LivingEntity target, double distance) {
        boolean p2 = phaseTwo();
        if (p2 && cloneCooldown <= 0 && mode != Mode.ENCOUNTER) return SHADOW_CLONE;
        if (distance < 4.5) return pick(BITE, 5, CLAW, 3, NIGHT_REND, p2 ? 2 : 0, HOWL, p2 ? 1 : 0, VANISH, 1);
        if (distance <= 7) return pick(SHADOW_DASH, 2, -1, 4, NIGHT_REND, p2 && distance < 8 ? 1 : 0, VANISH, 1);
        if (distance <= 10) return pick(SHADOW_DASH, 4, VANISH, 1, HOWL, p2 ? 1 : 0, -1, 1);
        return pick(VANISH, 3, SHADOW_DASH, 3, HOWL, p2 ? 1 : 0);
    }

    /** Prey perched out of its reach: it melts into the shadows and erupts right under them. */
    @Override
    protected int gapCloser(LivingEntity target, double distance) {
        return distance < 40 ? VANISH : -1;
    }

    @Override
    protected int attackLength(int attack) {
        return switch (attack) {
            case BITE -> 16;
            case CLAW -> 26;
            case SHADOW_DASH -> 14;
            case HOWL -> 30;
            case NIGHT_REND -> 30;
            case VANISH -> 34;
            case SHADOW_CLONE -> 32;
            case BACKSTEP -> 12;
            default -> 10;
        };
    }

    @Override
    protected int animSlot(int attack) {
        return attack == BACKSTEP ? 0 : attack + 1;
    }

    @Override
    protected int cooldownAfter(int attack) {
        return (phaseTwo() ? 8 : 16) + random.nextInt(14);
    }

    /** Landing lag after Night Rend: it is planted for a moment. */
    @Override
    protected int recoveryAfter(int attack) {
        return attack == NIGHT_REND ? 20 : 0;
    }

    @Override
    protected void tickRecovery(int attack, int ticksLeft) {
        if (attack == NIGHT_REND && ticksLeft % 4 == 0) particles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 0.3, getZ(), 4, 1.2, 0.01);
    }

    @Override
    protected void onAttackEnd(int attack, boolean interrupted) {
        if (attack == VANISH && isInvisible()) setInvisible(false);
        if (interrupted) inString = false;
    }

    private double flatDistance(Vec3 to) {
        double dx = to.x - getX(), dz = to.z - getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** A clear spot about five blocks to one side of the target, where it can see it coming. */
    private Vec3 flankSpot(LivingEntity target) {
        Vec3 look = target.getLookAngle().multiply(1, 0, 1);
        if (look.lengthSqr() < 1e-4) look = position().subtract(target.position()).multiply(1, 0, 1);
        look = look.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : look.normalize();
        Vec3 side = new Vec3(-look.z, 0, look.x);
        int first = random.nextBoolean() ? 1 : -1;
        for (int sign : new int[]{first, -first}) {
            // Slightly ahead of them as well, so it steps into view rather than out of it.
            Vec3 spot = target.position().add(side.scale(5 * sign)).add(look.scale(1.5));
            BlockPos pos = BlockPos.containing(spot.x, target.getY() + 1, spot.z);
            for (int dy = 0; dy < 4 && level().getBlockState(pos.below()).isAir(); dy++) pos = pos.below();
            Vec3 at = new Vec3(spot.x, pos.getY(), spot.z);
            if (level().noCollision(this, getType().getDimensions().makeBoundingBox(at))) return at;
        }
        return position();
    }

    @Override
    protected int animDelay(int attack) {
        return attack == BITE ? 3 : attack == CLAW ? 2 : 0;
    }

    @Override
    protected void tickAttack(int attack, int tick, LivingEntity target) {
        switch (attack) {
            case BITE -> {
                if (tick == 1) tellCone(yawToward(target), BITE_RANGE, BITE_ANGLE, true, 10);
                if (tick == 11) {
                    coneAttack(BITE_RANGE, BITE_ANGLE, 1.0f, 0.6, 0);
                    playSound(WolfVoice.GROWL, 2f, 0.6f);
                }
                // Its string: bite, then claw, then a hop back out of reach.
                if (tick == 16 && target != null && distanceTo(target) < 6) {
                    inString = true;
                    followWith(CLAW, 3);
                }
            }
            case CLAW -> {
                if (tick == 1) tellCone(yawToward(target), CLAW_RANGE, CLAW_ANGLE, true, 10);
                if (tick == 11 || tick == 22) {
                    coneAttack(CLAW_RANGE, CLAW_ANGLE, 0.8f, 0.8, COMBO);
                    Fx.arc(this, 4.5f, tick == 11 ? 40 : -40, CLAW_ANGLE, PURPLE, 8);
                    playSound(net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_SWEEP, 1.5f, 0.5f);
                }
                if (tick == 11 && attack == CLAW) {
                    // The other paw (unless that swipe was parried): a fresh cone toward wherever the prey went.
                    float yaw = yawToward(target);
                    lockAim(yaw);
                    Fx.coneAt(level(), position(), yaw, CLAW_RANGE, CLAW_ANGLE, tellColor(true), 11);
                }
                if (tick == 26 && inString) {
                    inString = false;
                    followWith(BACKSTEP, 2);
                }
            }
            case BACKSTEP -> {
                if (tick == 2) {
                    Vec3 back = facing().scale(-0.9);
                    setDeltaMovement(back.x, 0.42, back.z);
                    needsSync = true;
                    particles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 0.3, getZ(), 12, 0.8, 0.01);
                }
            }
            case SHADOW_DASH -> {
                // Steps out of the shadows to the side of its prey (in view, with a sound), shows the lane, then lunges.
                if (tick == 1) {
                    particles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1, getZ(), 40, 1.0, 0.02);
                    if (target != null) {
                        Vec3 spot = flankSpot(target);
                        teleportTo(spot.x, spot.y, spot.z);
                    }
                    particles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1, getZ(), 40, 1.0, 0.02);
                    playSound(net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT, 1.5f, 0.5f);
                    playSound(WolfVoice.GROWL, 2.5f, 0.5f);
                }
                if (tick == 2) {
                    aim = target != null ? target.position() : position().add(facing().scale(6));
                    dashLength = Math.min(9, flatDistance(aim) + 2.5);
                    tellCone(Fx.yawTo(position(), aim), (float) dashLength, 30, true, 9);
                }
                if (tick == 11) startCharge(facing(), 1.4, dashLength, 0.3, 2.4, 1.2f, 1.2, 0);
            }
            case HOWL -> {
                if (tick == 10) {
                    Fx.ring(level(), position(), 14f, PURPLE, 24, 0.5f);
                    Fx.pillar(level(), position(), 1.6f, 12f, PURPLE, 20);
                    playSound(WolfVoice.HOWL, 5f, 0.4f);
                    for (ServerPlayer p : playersNear(24)) {
                        p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 140, 0), this);
                    }
                    summonShadows(2);
                }
            }
            case NIGHT_REND -> {
                if (tick == 4) {
                    // The slam lands on the circle it leaves behind, whether or not it is down yet.
                    aim = position();
                    setDeltaMovement(0, 1.1, 0);
                    needsSync = true;
                    tellCircle(aim, 6.5f, false, 22);
                }
                if (tick == 8) ring(BLOOD, 6, 36, 0.2);
                if (tick == 26) {
                    hitAt(aim, 6.5, 1.4f, 1.5, UNPARRYABLE);
                    Fx.ring(level(), aim, 7f, PURPLE, 14, 1.0f);
                    Fx.flash(level(), aim.add(0, 1, 0), 4f, PINK, 8, 0);
                    ring(ParticleTypes.EXPLOSION, 4, 8, 0.3);
                    playSound(net.minecraft.sounds.SoundEvents.DRAGON_FIREBALL_EXPLODE, 2f, 0.6f);
                }
            }
            case VANISH -> {
                // Melts into the shadows, then erupts under its prey.
                if (tick == 14) {
                    particles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1, getZ(), 80, 1.4, 0.03);
                    setInvisible(true);
                    playSound(net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT, 1.5f, 0.4f);
                }
                if (tick == 16) {
                    aim = target != null ? target.position() : position();
                    tellCircle(aim, 3.8f, false, 12);
                }
                if (tick == 28) {
                    teleportTo(aim.x, aim.y, aim.z);
                    setInvisible(false);
                    setDeltaMovement(0, 0.5, 0);
                    needsSync = true;
                    hitAt(aim, 3.8, 1.3f, 1.2, UNPARRYABLE);
                    Fx.ring(level(), aim, 4f, PINK, 10, 0.6f);
                    particles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1, getZ(), 60, 1.4, 0.03);
                    playSound(WolfVoice.GROWL, 3f, 0.4f);
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
        if (level().isClientSide() && !isInvisible()) aura(random.nextInt(3) + 2);
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

    /** Splits off a short-lived shadow of itself that hunts alongside it (worth nothing when broken). */
    private void spawnClone() {
        if (!(level() instanceof ServerLevel server)) return;
        LycagonShadowEntity clone = ModEntities.LYCAGON_SHADOW.get().create(server, EntitySpawnReason.EVENT);
        if (clone == null) return;
        clone.snapTo(getX(), getY(), getZ(), getYRot(), 0);
        clone.makeClone(20 * 15);
        clone.setTarget(getTarget());
        server.addFreshEntity(clone);
        trackSummon(clone, true);
        Fx.flash(server, position().add(0, 2, 0), 4f, PURPLE, 10, 0.4f);
        server.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1.2, getZ(), 60, 1.2, 1.2, 1.2, 0.02);
    }

    private void summonShadows(int count) {
        if (!(level() instanceof ServerLevel server)) return;
        for (int i = 0; i < count; i++) {
            ShadowWolfEntity wolf = ModEntities.SHADOW_WOLF.get().create(server, EntitySpawnReason.EVENT);
            if (wolf == null) continue;
            double a = random.nextDouble() * Math.PI * 2;
            wolf.snapTo(getX() + Math.cos(a) * 3, getY(), getZ() + Math.sin(a) * 3, getYRot(), 0);
            wolf.finalizeSpawn(server, server.getCurrentDifficultyAt(blockPosition()), EntitySpawnReason.MOB_SUMMONED, null);
            wolf.setTarget(getTarget());
            server.addFreshEntity(wolf);
            trackSummon(wolf, true);
            server.sendParticles(ParticleTypes.LARGE_SMOKE, wolf.getX(), wolf.getY() + 0.5, wolf.getZ(), 20, 0.4, 0.4, 0.4, 0.01);
        }
    }

    // ---- Marking encounter-stage players --------------------------------------------------

    /** A blow that would kill an encounter-stage player marks them instead; marked players are left alone. */
    @Override
    protected float adjustDamage(LivingEntity target, float damage) {
        if (target instanceof ServerPlayer player) {
            if (spared.contains(player.getUUID())) return 0;
            if (isEncounterPlayer(player) && damage >= player.getHealth() - 1) {
                mark(player);
                return 0;
            }
        }
        return damage;
    }

    @Override
    protected void onHitTarget(LivingEntity target) {
        if (target instanceof ServerPlayer player && isEncounterPlayer(player) && player.getHealth() <= player.getMaxHealth() * 0.35f) {
            mark(player);
        }
    }

    @Override
    protected boolean isEnemy(LivingEntity e) {
        return !(e instanceof ServerPlayer p && spared.contains(p.getUUID())) && super.isEnemy(e);
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return !(target instanceof ServerPlayer p && spared.contains(p.getUUID())) && super.canAttack(target);
    }

    /**
     * Takes an encounter-stage player's legs and brands them. In the scripted encounter everyone
     * else at that stage is branded too and it vanishes; with a hunter present it lets the marked
     * player go and keeps fighting the others.
     */
    private void mark(ServerPlayer player) {
        if (isRemoved() || spared.contains(player.getUUID())) return;
        brand(player);
        if (updateScripted()) {
            for (ServerPlayer other : encounterPlayers()) brand(other);
            vanish();
        } else if (getTarget() == player) {
            setTarget(null);
        }
    }

    private void brand(ServerPlayer player) {
        if (!spared.add(player.getUUID())) return;
        player.setHealth(1f);
        player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 200, 4), this);
        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 400, 1), this);
        playSound(WolfVoice.HOWL, 5f, 0.3f);
        Story.onMarkedByLycagon(player);
    }

    /** Encounter-stage players in the fight (and the summoner, wherever they are, as before). */
    private List<ServerPlayer> encounterPlayers() {
        List<ServerPlayer> players = new ArrayList<>();
        for (ServerPlayer p : playersNear(64)) {
            if (isEncounterPlayer(p)) players.add(p);
        }
        if (summoner != null && level() instanceof ServerLevel server
                && server.getServer().getPlayerList().getPlayer(summoner) instanceof ServerPlayer sp
                && !players.contains(sp) && isEncounterPlayer(sp)) {
            players.add(sp);
        }
        return players;
    }

    private void vanish() {
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1.2, getZ(), 120, 1.4, 1.4, 1.4, 0.03);
            server.sendParticles(ParticleTypes.SOUL, getX(), getY() + 1.2, getZ(), 40, 1.4, 1.4, 1.4, 0.03);
        }
        despawnSummons(false);
        discard();
    }

    @Override
    protected void onReset() {
        cloneCooldown = 300;
        inString = false;
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (!(level() instanceof ServerLevel server) || isRemoved()) return;
        if (cloneCooldown > 0) cloneCooldown--;
        if (tickCount % 3 == 0) {
            server.sendParticles(ParticleTypes.SMOKE, getX(), getY() + 1.2, getZ(), 2, 0.8, 0.6, 0.8, 0.0);
        }
        if (getTarget() instanceof ServerPlayer p && spared.contains(p.getUUID())) setTarget(null);
        // The Nightslayer recedes with the dawn, or if nobody is left to hunt.
        if ((server.isBrightOutside() && tickCount > 40) || idleTicks > 600) {
            server.getServer().getPlayerList().broadcastSystemMessage(
                    Component.translatable("message.slfrontier.lycagon.recedes").withStyle(ChatFormatting.DARK_PURPLE), false);
            vanish();
            return;
        }
        if (mode == Mode.ENCOUNTER) {
            if (tickCount % 10 == 0) updateScripted();
            if (scripted) {
                encounterTicks++;
                if (encounterTicks > 20 * 90 || getHealth() < getMaxHealth() * 0.6f) {
                    List<ServerPlayer> branded = encounterPlayers();
                    if (!branded.isEmpty()) {
                        for (ServerPlayer p : branded) brand(p);
                        vanish();
                    }
                }
            }
        }
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (scripted) amount *= 0.2f;
        return super.hurtServer(level, source, amount);
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel server) {
            BossAltar.recordKill(server, this, source.getEntity(), BossAltar.NIGHTSLAYER);
            Story.onLycagonSlain(server, this, source.getEntity());
        }
    }

    @Override
    public void addAdditionalSaveData(ValueOutput tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("Mode", mode.name());
        if (summoner != null) tag.store("Summoner", net.minecraft.core.UUIDUtil.CODEC, summoner);
        if (!spared.isEmpty()) tag.store("Spared", net.minecraft.core.UUIDUtil.CODEC.listOf(), List.copyOf(spared));
    }

    @Override
    public void readAdditionalSaveData(ValueInput tag) {
        super.readAdditionalSaveData(tag);
        try {
            mode = Mode.valueOf(tag.getStringOr("Mode", ""));
        } catch (IllegalArgumentException e) {
            mode = Mode.NORMAL;
        }
        scripted = mode == Mode.ENCOUNTER;
        tag.read("Summoner", net.minecraft.core.UUIDUtil.CODEC).ifPresent(id -> summoner = id);
        spared.clear();
        tag.read("Spared", net.minecraft.core.UUIDUtil.CODEC.listOf()).ifPresent(spared::addAll);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return WolfVoice.GROWL;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return WolfVoice.HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return WolfVoice.DEATH;
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

package com.slfrontier.entity.boss;

import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
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
    private static final DustParticleOptions SKY = new DustParticleOptions(0x8CD9FF, 1.6f);
    private static final int CYAN = 0xFF8FE8FF, WHITE = 0xFFEFFAFF, THUNDER = 0xFFFFF07A, GRAVE = 0xFF6A9CFF;

    private static final float CUT_RANGE = 4f, CUT_ANGLE = 160, TENSEI_RANGE = 18, TENSEI_ANGLE = 30;

    private int disarmTicks;
    private double iaiLength = 8, raishoLength = 6;
    private int tenseiCooldown = 200;
    private boolean kirinSummoned;
    private Vec3 aim = Vec3.ZERO;

    public WezaemonEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level, 50, 3000, BossEvent.BossBarColor.BLUE);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 950)
                .add(Attributes.ATTACK_DAMAGE, 14)
                .add(Attributes.ARMOR, 10)
                .add(Attributes.ARMOR_TOUGHNESS, 4)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.9)
                .add(Attributes.FOLLOW_RANGE, 48)
                // Iai and Raisho run their whole lane: a one-block rise shouldn't count as a wall.
                .add(Attributes.STEP_HEIGHT, 1.0);
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

    @Override
    protected int bossPhase() {
        return phase();
    }

    /** A perfect parry of Sky Clear knocks his sword away and breaks his stance; any other parry just staggers him. */
    @Override
    public void onParried(ServerPlayer by) {
        if (attack != SKY_CLEAR || isDisarmed()) {
            super.onParried(by);
            return;
        }
        entityData.set(DISARMED, true);
        disarmTicks = 120;
        startAttack(STAGGER);
        com.slfrontier.entity.Stun.apply(this, com.slfrontier.entity.Stun.PARRY_TICKS);
        playSound(SoundEvents.ANVIL_LAND, 2f, 0.5f);
        Fx.flash(level(), position().add(0, 2, 0), 3f, WHITE, 10, 0.6f);
        by.level().getServer().getPlayerList().broadcastSystemMessage(
                Component.translatable("message.slfrontier.wezaemon.parried", by.getDisplayName()).withStyle(ChatFormatting.AQUA), false);
    }

    @Override
    protected String phaseHint(int phase) {
        return phase >= 2 ? "message.slfrontier.phase.wezaemon." + phase : null;
    }

    @Override
    protected int chooseAttack(LivingEntity target, double distance) {
        if (isDisarmed()) return -1;
        int phase = phase();
        if (phase == 3 && tenseiCooldown <= 0 && distance < 12) return TENSEI;
        boolean p2 = phase >= 2;
        if (distance < 4) return pick(TRIPLE, 4, IAI, 1, SKY_CLEAR, p2 ? 2 : 0, RAISHO, p2 ? 1 : 0);
        if (distance <= 6) return pick(IAI, 3, -1, 3, SKY_CLEAR, p2 ? 2 : 0, RAISHO, p2 ? 1 : 0, TACHIKAZE, 1);
        return pick(TACHIKAZE, distance < 16 ? 4 : 0, IAI, distance < 14 ? 3 : 0, RAISHO, p2 && distance < 14 ? 2 : 0, -1, 2);
    }

    /** Someone perched out of his reach: the wind blade is aimed straight at them. */
    @Override
    protected int gapCloser(LivingEntity target, double distance) {
        return distance < 20 && !isDisarmed() ? TACHIKAZE : -1;
    }

    @Override
    protected boolean canInterrupt(int attack) {
        return attack != STAGGER;
    }

    @Override
    protected int attackLength(int attack) {
        return switch (attack) {
            case IAI -> 16;
            case TRIPLE -> 33;
            case SKY_CLEAR -> 42;
            case STAGGER -> 100;
            case TACHIKAZE -> 26;
            case RAISHO -> 38;
            case TENSEI -> 52;
            default -> 10;
        };
    }

    private Vec3 chest() {
        return position().add(0, getBbHeight() * 0.55, 0);
    }

    private double flatDistance(Vec3 to) {
        double dx = to.x - getX(), dz = to.z - getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Shows the next cut of the triple combo (each cut its own gold cone; one glint for the whole string). */
    private void tellCut(LivingEntity target, int duration, boolean sound) {
        float yaw = yawToward(target);
        lockAim(yaw);
        Fx.coneAt(level(), position(), yaw, CUT_RANGE, CUT_ANGLE, tellColor(true), duration);
        if (sound) tellSound(true);
    }

    @Override
    protected int animDelay(int attack) {
        return attack == TRIPLE ? 3 : 0;
    }

    @Override
    protected void tickAttack(int attack, int tick, LivingEntity target) {
        switch (attack) {
            case IAI -> {
                // Iaijutsu: a dash the whole length of the lane he showed; if you're still close, the triple cut follows.
                if (tick == 1) {
                    aim = target != null ? target.position() : position().add(facing().scale(6));
                    iaiLength = Math.min(14, flatDistance(aim) + 3);
                    tellCone(Fx.yawTo(position(), aim), (float) iaiLength, 22, true, 11);
                }
                if (tick < 12 && tick % 3 == 0) particles(ParticleTypes.ENCHANTED_HIT, getX(), getY() + 1.2, getZ(), 6, 0.4, 0.1);
                if (tick == 12) {
                    playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 2f, 1.5f);
                    Fx.arcAt(level(), chest(), Fx.yawTo(position(), aim), 3.2f, 8, 200, WHITE, 8);
                    startCharge(facing(), 1.6, iaiLength, 0.05, 2.2, 1.3f, 0.8, 0);
                }
                if (tick == 13) particles(ParticleTypes.SWEEP_ATTACK, getX(), getY() + 1.2, getZ(), 2, 0.3, 0);
                if (tick == 16 && target != null && distanceTo(target) < 5) followWith(TRIPLE, 4);
            }
            case TRIPLE -> {
                // Three cuts nine ticks apart; each lands (the hurt cooldown doesn't swallow the combo).
                if (tick == 1) tellCut(target, 10, true);
                if (tick == 11 || tick == 20 || tick == 29) {
                    coneAttack(CUT_RANGE, CUT_ANGLE, 0.7f, 0.4, COMBO);
                    float tilt = tick == 11 ? 35 : tick == 20 ? -35 : 80;
                    Fx.arc(this, 3.6f, tilt, CUT_ANGLE, WHITE, 8);
                    playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.5f, 1.2f);
                    if (tick < 29 && attack == TRIPLE) tellCut(target, 9, false);  // unless that cut was parried
                }
            }
            case SKY_CLEAR -> {
                if (tick == 1) tellCircle(position(), 7.2f, true, 31);
                if (tick % 6 == 0 && tick < 30) ring(SKY, 7, 40, 0.15);
                if (tick == 2) {
                    for (ServerPlayer p : playersNear(20)) {
                        p.sendOverlayMessage(Component.translatable("message.slfrontier.wezaemon.sky_clear").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
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
                // A wide draw that releases a flying blade of wind, aimed in 3D: a pillar is no refuge.
                if (tick == 1) {
                    aim = target != null ? target.position().add(0, target.getBbHeight() * 0.5, 0) : chest().add(facing().scale(16));
                    tellCone(Fx.yawTo(position(), aim), 16, 14, true, 10);
                    Vec3 dir = aim.subtract(chest()).normalize();
                    Fx.beam(level(), chest(), chest().add(dir.scale(16)), 0.08f, tellColor(true), 10);
                }
                if (tick == 11) {
                    float yaw = Fx.yawTo(position(), aim);
                    Vec3 dir = aim.subtract(chest()).normalize();
                    Vec3 end = chest().add(dir.scale(16));
                    Fx.waveAt(level(), chest(), yaw, 3.2f, 0, 16, CYAN, 14, 0.3f);
                    if (Math.abs(dir.y) > 0.15) Fx.beam(level(), chest(), end, 0.5f, CYAN, 6);
                    hitLine(chest(), end, 1.6, 1.1f, 0.9);
                    playSound(SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), 2f, 0.8f);
                }
            }
            case RAISHO -> {
                // Thunder step: three zig-zag dashes, each shown for 8 ticks, each ending in a thrust.
                if (tick == 1 || tick == 13 || tick == 25) {
                    Vec3 at = target != null ? target.position() : position().add(facing().scale(6));
                    Vec3 to = at.subtract(position());
                    Vec3 side = new Vec3(-to.z, 0, to.x);
                    side = side.lengthSqr() > 1e-6 ? side.normalize().scale(tick == 13 ? -0.9 : 0.9) : Vec3.ZERO;
                    aim = at.add(side);
                    raishoLength = Math.min(8, flatDistance(aim) + 1.5);
                    tellCone(Fx.yawTo(position(), aim), (float) raishoLength, 18, true, 8);
                }
                if (tick == 9 || tick == 21 || tick == 33) {
                    Fx.flash(level(), chest(), 1.6f, THUNDER, 6, 0.25f);
                    Fx.pillar(level(), position(), 0.5f, 4f, THUNDER, 8);
                    playSound(SoundEvents.TRIDENT_THUNDER.value(), 0.8f, 1.6f);
                    startCharge(facing(), 1.6, raishoLength, 0.1, 2.2, 0.8f, 0.5, 0);
                }
                if (tick == 10 || tick == 22 || tick == 34) particles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 1.5, getZ(), 12, 0.6, 0.2);
            }
            case TENSEI -> {
                // Raise the sword to the heavens, then split the ground in front of him. It can't be parried.
                if (tick == 1) {
                    tenseiCooldown = 360;
                    aim = target != null ? target.position() : position().add(getLookAngle());
                    tellCone(Fx.yawTo(position(), aim), TENSEI_RANGE, TENSEI_ANGLE, false, 39);
                    Fx.telegraph(level(), position(), 4.5f, tellColor(false), 39);
                    for (ServerPlayer p : playersNear(24)) {
                        p.sendOverlayMessage(Component.translatable("message.slfrontier.wezaemon.tensei").withStyle(ChatFormatting.BLUE, ChatFormatting.BOLD));
                    }
                }
                if (tick == 4) Fx.pillar(level(), position(), 1.2f, 14f, GRAVE, 36);
                if (tick < 40 && tick % 4 == 0) particles(ParticleTypes.SOUL_FIRE_FLAME, getX(), getY() + 3, getZ(), 6, 0.6, 0.05);
                if (tick == 40) {
                    float yaw = Fx.yawTo(position(), aim);
                    hitAround(4.5, 1.8f, -1, 1.4, UNPARRYABLE);
                    coneAttack(TENSEI_RANGE, TENSEI_ANGLE, 1.9f, 1.6, UNPARRYABLE);
                    Fx.waveAt(level(), chest(), yaw, 4.5f, 90, TENSEI_RANGE, GRAVE, 16, 1.2f);
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
        KirinEntity kirin = ModEntities.KIRIN.get().create(server, EntitySpawnReason.EVENT);
        if (kirin == null) return;
        Vec3 at = position().add(getLookAngle().multiply(-3, 0, -3));
        kirin.snapTo(at.x, getY(), at.z, getYRot(), 0);
        kirin.setMaster(this);
        kirin.setTarget(getTarget());
        server.addFreshEntity(kirin);
        trackSummon(kirin, false);
        Fx.pillar(server, kirin.position(), 1.5f, 10f, CYAN, 20);
        Fx.ring(server, kirin.position(), 5f, CYAN, 16, 0.5f);
        playSound(SoundEvents.IRON_GOLEM_REPAIR, 2f, 0.6f);
        // The phase cue holds the action bar; the arrival goes to chat.
        for (ServerPlayer p : playersNear(32)) {
            p.sendSystemMessage(Component.translatable("message.slfrontier.wezaemon.kirin").withStyle(ChatFormatting.AQUA));
        }
    }

    @Override
    protected void onReset() {
        kirinSummoned = false;
        tenseiCooldown = 200;
        disarmTicks = 0;
        entityData.set(DISARMED, false);
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
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
                // The grave aura: magic, so a parry can't turn it aside. Only Holy Water quiets it.
                ring(ParticleTypes.SOUL_FIRE_FLAME, 5, 24, 0.3);
                for (ServerPlayer p : playersNear(5)) {
                    if (!p.isCreative()) p.hurtOrSimulate(unparryable(damageSources().indirectMagic(this, this)), 3f);
                }
            }
        }
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (isDisarmed()) amount *= 1.5f;
        return super.hurtServer(level, source, amount);
    }

    @Override
    public void addAdditionalSaveData(ValueOutput tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("KirinSummoned", kirinSummoned);
    }

    @Override
    public void readAdditionalSaveData(ValueInput tag) {
        super.readAdditionalSaveData(tag);
        kirinSummoned = tag.getBooleanOr("KirinSummoned", false);
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

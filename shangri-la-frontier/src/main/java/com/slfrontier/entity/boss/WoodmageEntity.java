package com.slfrontier.entity.boss;

import com.slfrontier.entity.FrontierBoss;
import com.slfrontier.fx.Fx;
import com.slfrontier.quest.Story;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The Aberrant Woodmage, final round of the Vorpal Colosseum. A figure of dark wood in a
 * torn white coat whose four living chains whip and seize, whose staff raises spears of
 * roots and fires bolts of blue light. Its wooden body shrugs off blades: magic (Emul's
 * Magic Edge) hurts it fully, and so does a blow landed while it is casting.
 */
public class WoodmageEntity extends FrontierBoss {
    public static final int ROOTS = 0, CHAINS = 1, BOLT = 2, GRASP = 3;
    private static final int ROOT = 0xFFB07A3A, BLUE = 0xFF4AA8FF, GUARD = 0xFFBFF4FF;
    private static final float CHAIN_RANGE = 5.5f, CHAIN_ANGLE = 220;

    private final List<Vec3> marks = new ArrayList<>();
    private Vec3 aim = Vec3.ZERO;
    private final java.util.Set<UUID> warned = new java.util.HashSet<>();

    public WoodmageEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level, 28, 1200, BossEvent.BossBarColor.RED);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 340)
                .add(Attributes.ATTACK_DAMAGE, 10)
                .add(Attributes.ARMOR, 8)
                .add(Attributes.MOVEMENT_SPEED, 0.24)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.FOLLOW_RANGE, 40);
    }

    /** While it casts, its guard is down. */
    public boolean isCasting() {
        return attack == BOLT || attack == ROOTS;
    }

    @Override
    protected int chooseAttack(LivingEntity target, double distance) {
        if (distance < 5) return pick(CHAINS, 4, ROOTS, 2, BOLT, 1);
        if (distance <= 6) return pick(ROOTS, 2, BOLT, 3, -1, 1);
        return pick(GRASP, 3, BOLT, 3, ROOTS, 2);
    }

    @Override
    protected int attackLength(int attack) {
        return switch (attack) {
            case ROOTS -> 36;
            case CHAINS -> 24;
            case BOLT -> 20;
            case GRASP -> 32;
            default -> 10;
        };
    }

    private Vec3 orb() {
        return position().add(getLookAngle().multiply(0.9, 0, 0.9)).add(0, getBbHeight() * 0.95, 0);
    }

    /** Its guard drops while it casts: a pale light rises around it and its bark cracks open. */
    private void guardDown(int duration) {
        Fx.pillar(level(), position(), 0.9f, getBbHeight() + 0.6f, GUARD, duration);
        Fx.ring(level(), position(), 1.4f, GUARD, 10, 0);
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.SHIELD_BREAK, getSoundSource(), 1.2f, 0.7f);
    }

    @Override
    protected int animDelay(int attack) {
        return attack == CHAINS ? 1 : 0;
    }

    @Override
    protected void tickAttack(int attack, int tick, LivingEntity target) {
        if (isCasting() && tick % 4 == 0) {
            particles(ParticleTypes.ENCHANT, getX(), getY() + 1.4, getZ(), 6, 0.6, 0.4);
            particles(ParticleTypes.WAX_OFF, getX(), getY() + 1.0, getZ(), 2, 0.5, 0.02);
        }
        switch (attack) {
            case ROOTS -> {
                // Spears of roots erupt under everyone in reach.
                if (tick == 1) guardDown(attackLength(ROOTS));
                if (tick == 6) {
                    marks.clear();
                    for (LivingEntity e : enemiesNear(18)) {
                        marks.add(e.position());
                        marks.add(e.position().add(random.nextGaussian() * 2, 0, random.nextGaussian() * 2));
                    }
                    for (Vec3 m : marks) Fx.telegraph(level(), m, 1.8f, tellColor(false), 20);
                    tellSound(false);
                }
                if (tick == 26 && level() instanceof ServerLevel server) {
                    BlockParticleOption wood = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DARK_OAK_LOG.defaultBlockState());
                    for (Vec3 m : marks) {
                        Fx.pillar(server, m, 0.7f, 3.5f, ROOT, 12);
                        server.sendParticles(wood, m.x, m.y + 0.5, m.z, 30, 0.5, 0.8, 0.5, 0.1);
                        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class,
                                new net.minecraft.world.phys.AABB(m, m).inflate(1.8, 2, 1.8), this::isEnemy)) {
                            e.setDeltaMovement(e.getDeltaMovement().add(0, 0.7, 0));
                            e.hurtMarked = true;
                        }
                        hitAt(m, 1.8, 1.0f, 0, UNPARRYABLE);
                    }
                    playSound(SoundEvents.ROOTED_DIRT_BREAK, 2f, 0.5f);
                }
            }
            case CHAINS -> {
                if (tick == 1) tellCone(yawToward(target), CHAIN_RANGE, CHAIN_ANGLE, true, 10);
                if (tick == 11) {
                    coneAttack(CHAIN_RANGE, CHAIN_ANGLE, 1.0f, 1.0, 0);
                    Fx.arc(this, 5f, 15, CHAIN_ANGLE, 0xFF9AA0AA, 8);
                    playSound(SoundEvents.CHAIN_BREAK, 2f, 0.6f);
                }
            }
            case BOLT -> {
                if (tick == 1) {
                    guardDown(attackLength(BOLT));
                    aim = target != null ? target.getEyePosition() : orb().add(facing().scale(16));
                    tellCone(Fx.yawTo(position(), aim), 16, 10, false, 11);
                    playSound(SoundEvents.EVOKER_PREPARE_ATTACK, 1.5f, 1.2f);
                }
                if (tick == 12) {
                    Vec3 from = orb();
                    Vec3 dir = aim.subtract(from).normalize();
                    Vec3 to = from.add(dir.scale(16));
                    Fx.beam(level(), from, to, 0.45f, BLUE, 8);
                    Fx.flash(level(), from, 1.5f, BLUE, 6, 0);
                    hitLine(from, to, 1.0, 1.2f, 0.5, UNPARRYABLE);
                    playSound(SoundEvents.ILLUSIONER_CAST_SPELL, 1.5f, 1.2f);
                }
            }
            case GRASP -> {
                // The chains seize the target and drag it in (no damage, so nothing to parry: dodge it).
                if (tick == 1) {
                    aim = target != null ? target.position() : position().add(facing().scale(8));
                    tellCone(Fx.yawTo(position(), aim), 16, 12, false, 12);
                }
                if (tick == 13 && target != null && target.position().distanceTo(aim) < 2.5 && target.distanceTo(this) < 17) {
                    Vec3 pull = position().subtract(target.position()).normalize().scale(1.6);
                    target.setDeltaMovement(pull.x, 0.35, pull.z);
                    target.hurtMarked = true;
                    target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 50, 3), this);
                    Fx.beam(level(), orb(), target.position().add(0, 1, 0), 0.25f, 0xFFB0B4BC, 10);
                    playSound(SoundEvents.CHAIN_PLACE, 2f, 0.5f);
                }
            }
            default -> {
            }
        }
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean physical = !source.is(DamageTypeTags.WITCH_RESISTANT_TO) && !source.is(DamageTypeTags.IS_FIRE)
                && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
        if (physical && !isCasting()) {
            amount *= 0.35f;
            if (source.getEntity() instanceof ServerPlayer player && warned.add(player.getUUID())) {
                player.sendSystemMessage(Component.translatable("message.slfrontier.woodmage.resists").withStyle(ChatFormatting.GOLD));
            }
        }
        return super.hurtServer(level, source, amount);
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel server) Story.onWoodmageSlain(server, this, source.getEntity());
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.EVOKER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.WOOD_BREAK;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.EVOKER_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return 0.6f;
    }
}

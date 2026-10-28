package com.slfrontier.skill;

import com.slfrontier.data.PlayerData;
import com.slfrontier.data.Stat;
import com.slfrontier.entity.npc.EmulEntity;
import com.slfrontier.fx.Fx;
import com.slfrontier.fx.HitFlags;
import com.slfrontier.entity.npc.FrontierNpc;
import com.slfrontier.item.MoonbladeItem;
import com.slfrontier.network.ModNetwork;
import com.slfrontier.progression.Progression;
import com.slfrontier.registry.ModAttachments;
import com.slfrontier.registry.ModItems;
import com.slfrontier.world.Frontier;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.Comparator;
import java.util.List;

/** Server-side implementation of every active skill. */
public final class SkillExecutor {
    private static final DustParticleOptions MOON_WHITE = new DustParticleOptions(new Vector3f(0.95f, 0.95f, 1f), 1.4f);
    private static final DustParticleOptions MOON_BLACK = new DustParticleOptions(new Vector3f(0.15f, 0.1f, 0.25f), 1.4f);
    private static final DustParticleOptions VORPAL_RED = new DustParticleOptions(new Vector3f(0.9f, 0.15f, 0.2f), 1.2f);
    private static final DustParticleOptions SKY_BLUE = new DustParticleOptions(new Vector3f(0.55f, 0.85f, 1f), 1.6f);
    private static final DustParticleOptions STEP_GREEN = new DustParticleOptions(new Vector3f(0.45f, 1f, 0.4f), 1.1f);

    private SkillExecutor() {
    }

    public static void trySlot(ServerPlayer player, int slot) {
        PlayerData data = ModAttachments.get(player);
        Skill skill = data.equipped(slot);
        if (skill == null) {
            actionbar(player, Component.translatable("message.slfrontier.empty_slot", slot + 1));
            return;
        }
        use(player, skill);
    }

    public static boolean use(ServerPlayer player, Skill skill) {
        PlayerData data = ModAttachments.get(player);
        if (!Frontier.isIn(player)) {
            actionbar(player, Component.translatable("message.slfrontier.outside_frontier"));
            return false;
        }
        if (!data.hasSkill(skill) || skill.passive || player.isSpectator()) return false;
        if (data.cooldown(skill) > 0) {
            actionbar(player, Component.translatable("message.slfrontier.cooldown",
                    Component.translatable(skill.nameKey()), String.format("%.1f", data.cooldown(skill) / 20f)));
            return false;
        }
        if (data.stamina() < skill.staminaCost) {
            actionbar(player, Component.translatable("message.slfrontier.no_stamina"));
            return false;
        }
        boolean used = switch (skill) {
            case TAP_STEP -> dash(player, data, 1.1, 6);
            case SLIDE_STEP -> dash(player, data, 1.6, 10);
            case DRILL_PIERCER -> pierce(player, false);
            case GLOWING_PIERCE -> pierce(player, true);
            case PERFECT_PARRY, REPEL_COUNTER -> parry(player, data);
            case CYCLONE_SLASH -> cyclone(player);
            case RABBIT_GUN -> rabbitGun(player);
            case MOON_REFLECTION -> moonReflection(player, data);
            case CRESCENT_VORPAL -> crescentVorpal(player, data);
            case SKY_CLEAR -> skyClear(player);
            default -> false;
        };
        if (!used) return false;
        data.setStamina(data.stamina() - skill.staminaCost);
        data.setCooldown(skill, Math.round(skill.cooldown * Progression.cooldownScale(data)));
        if (skill != Skill.PERFECT_PARRY) {
            countUse(player, data, skill);
        }
        return true;
    }

    /** Counts a use toward evolving the skill (successful parries count for Perfect Parry). */
    public static void countUse(ServerPlayer player, PlayerData data, Skill skill) {
        int uses = data.addUse(skill);
        Skill evolution = skill.evolution();
        if (evolution != null && uses >= skill.evolutionUses() && !data.hasSkill(evolution)) {
            data.unlock(evolution);
            data.replaceEquipped(skill, evolution);
            ModNetwork.announce(player, "announce.slfrontier.skill_evolved", evolution.nameKey(), 0x7FE7FF);
            Progression.announceSkill(player, evolution);
        }
    }

    // ---- Movement ------------------------------------------------------------------

    private static boolean dash(ServerPlayer player, PlayerData data, double power, int iframes) {
        Vec3 dir = horizontalLook(player);
        if (player.isShiftKeyDown()) dir = dir.scale(-1);
        power *= 1 + data.stat(Stat.AGI) * 0.005;
        push(player, dir.x * power, player.onGround() ? 0.2 : 0.05, dir.z * power);
        data.iframeTicks = iframes;
        ServerLevel level = player.serverLevel();
        // The legs glow green as the avatar glides (as in the anime).
        level.sendParticles(STEP_GREEN, player.getX(), player.getY() + 0.3, player.getZ(), 16, 0.25, 0.2, 0.25, 0);
        level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.1, player.getZ(), 6, 0.3, 0.05, 0.3, 0.02);
        Fx.ring(level, player.position(), power > 1.3 ? 1.6f : 1.1f, 0xC07FFF6A, 10, 0);
        sound(player, SoundEvents.PLAYER_ATTACK_SWEEP, 0.6f, 1.8f);
        ModNetwork.animate(player, player.isShiftKeyDown() ? PlayerAnims.BACKSTEP : PlayerAnims.DASH, 8);
        return true;
    }

    public static void doubleJump(ServerPlayer player) {
        PlayerData data = ModAttachments.get(player);
        if (!Frontier.isIn(player) || !data.hasSkill(Skill.DOUBLE_JUMP) || player.onGround() || data.airJumpUsed
                || player.isFallFlying() || player.getAbilities().flying || player.isInWater()) return;
        if (data.stamina() < Skill.DOUBLE_JUMP.staminaCost) return;
        data.airJumpUsed = true;
        data.setStamina(data.stamina() - Skill.DOUBLE_JUMP.staminaCost);
        Vec3 motion = player.getDeltaMovement();
        double up = 0.62 + data.stat(Stat.AGI) * 0.002;
        push(player, motion.x * 1.2, up, motion.z * 1.2);
        player.fallDistance = 0;
        player.serverLevel().sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY(), player.getZ(), 12, 0.4, 0.05, 0.4, 0.01);
        // Green halo rings around the ankles, like the Boat Leap skills.
        Fx.ring(player.level(), player.position(), 0.9f, 0xE07FFF6A, 9, 0);
        Fx.ring(player.level(), player.position().add(0, 0.25, 0), 0.6f, 0xE0B0FFA0, 7, 0);
        sound(player, SoundEvents.PHANTOM_FLAP, 0.6f, 1.6f);
        ModNetwork.animate(player, PlayerAnims.LEAP, 10);
    }

    // ---- Attacks -------------------------------------------------------------------

    private static boolean pierce(ServerPlayer player, boolean glowing) {
        double range = glowing ? 7 : 4.5;
        Vec3 dir = player.getLookAngle();
        List<LivingEntity> line = entitiesInLine(player, dir, range);
        push(player, dir.x * (glowing ? 1.5 : 1.1), 0.1, dir.z * (glowing ? 1.5 : 1.1));
        float damage = attack(player) * (glowing ? 1.9f : 1.5f);
        int hit = 0;
        for (LivingEntity target : line) {
            strike(player, target, damage, true);
            if (glowing) {
                target.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.GLOWING, 100));
            }
            hit++;
            if (!glowing) break;
        }
        trail(player, dir, range, glowing ? ParticleTypes.END_ROD : ParticleTypes.CRIT, 18);
        // A drilling blue spiral; Glowing Pierce adds a golden twister.
        Fx.spiral(player, dir, (float) range, glowing ? 0xFFFFD34F : 0xFF5FB8FF, 12);
        if (glowing) Fx.spiral(player, dir, (float) range * 0.8f, 0xFF7FE7FF, 10);
        if (!line.isEmpty()) Fx.flash(player.level(), line.get(0).position().add(0, line.get(0).getBbHeight() * 0.6, 0), 0.9f,
                glowing ? 0xFFFFE08A : 0xFF9FD0FF, 6, 0.4f);
        sound(player, SoundEvents.PLAYER_ATTACK_STRONG, 0.8f, glowing ? 1.4f : 1.1f);
        ModNetwork.animate(player, PlayerAnims.PIERCE, 11);
        return true;
    }

    private static boolean parry(ServerPlayer player, PlayerData data) {
        data.parryTicks = Math.min(20, 10 + data.stat(Stat.TEC) / 5);
        sound(player, SoundEvents.ARMOR_EQUIP_IRON.value(), 0.8f, 1.6f);
        // Blades crossed into an X that flares into a blue shield.
        Fx.arc(player, 0.9f, 62, 70, 0xFF7FD8FF, data.parryTicks + 2);
        Fx.arc(player, 0.9f, -62, 70, 0xFF7FD8FF, data.parryTicks + 2);
        ModNetwork.animate(player, PlayerAnims.PARRY, data.parryTicks + 2);
        return true;
    }

    private static boolean cyclone(ServerPlayer player) {
        float damage = attack(player) * 1.2f;
        for (LivingEntity target : around(player, 3.5)) {
            strike(player, target, damage, false);
            knockAway(player, target, 0.6);
        }
        ring(player, 3.0, VORPAL_RED, 24, 1.0);
        Fx.arc(player, 3.0f, 0, 360, 0xFFFF5A6A, 12);
        Fx.arc(player, 2.4f, 8, 300, 0xFFFFFFFF, 10);
        Fx.ring(player.level(), player.position(), 3.5f, 0xB0FF5A6A, 12, 0.3f);
        sound(player, SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 0.8f);
        ModNetwork.animate(player, PlayerAnims.SPIN, 11);
        return true;
    }

    private static boolean rabbitGun(ServerPlayer player) {
        EmulEntity emul = EmulEntity.findCompanion(player, 24);
        if (emul == null) {
            actionbar(player, Component.translatable("message.slfrontier.needs_emul"));
            return false;
        }
        LivingEntity target = lookTarget(player, 16);
        if (target == null) {
            actionbar(player, Component.translatable("message.slfrontier.no_target"));
            return false;
        }
        emul.castMagicEdge(target, 10f);
        Vec3 to = target.position().subtract(player.position());
        Vec3 dir = to.normalize();
        push(player, dir.x * 1.8, 0.35, dir.z * 1.8);
        ModAttachments.get(player).iframeTicks = 10;
        strike(player, target, attack(player) * 2f, true);
        trail(player, dir, Math.min(16, to.length()), ParticleTypes.END_ROD, 24);
        Fx.spiral(player, dir, (float) Math.min(16, to.length()), 0xFFB57CFF, 12);
        Fx.flash(player.level(), target.position().add(0, target.getBbHeight() * 0.6, 0), 1.3f, 0xFFC9A0FF, 8, 0.5f);
        sound(player, SoundEvents.FIREWORK_ROCKET_LAUNCH, 1f, 1.2f);
        ModNetwork.animate(player, PlayerAnims.RABBIT_GUN, 12);
        return true;
    }

    private static boolean moonReflection(ServerPlayer player, PlayerData data) {
        if (!(player.getMainHandItem().getItem() instanceof MoonbladeItem)) {
            actionbar(player, Component.translatable("message.slfrontier.needs_moonblade"));
            return false;
        }
        float damage = attack(player) * 2f;
        Vec3 look = horizontalLook(player);
        for (LivingEntity target : around(player, 6)) {
            Vec3 to = target.position().subtract(player.position()).normalize();
            if (to.x * look.x + to.z * look.z > 0.5) {
                strike(player, target, damage, false);
            }
        }
        data.parryTicks = 6;
        arc(player, 4.5, MOON_WHITE, MOON_BLACK);
        // Waxing (white) and Waning (night violet) cross into a crescent, then fly forward.
        Fx.arc(player, 2.6f, 35, 150, 0xFFEFF4FF, 12);
        Fx.arc(player, 2.6f, -35, 150, 0xFF8E5BE0, 12);
        Fx.wave(player, 2.2f, 10, 6f, 0xFFDDE6FF, 14);
        sound(player, SoundEvents.AMETHYST_BLOCK_CHIME, 1.5f, 0.7f);
        sound(player, SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 1.3f);
        ModNetwork.animate(player, PlayerAnims.CRESCENT, 12);
        return true;
    }

    private static boolean crescentVorpal(ServerPlayer player, PlayerData data) {
        if (!player.getMainHandItem().is(ModItems.TWIN_MOON.get())) {
            actionbar(player, Component.translatable("message.slfrontier.needs_twin_moon"));
            return false;
        }
        Vec3 look = horizontalLook(player);
        push(player, look.x * 0.7, 0.95, look.z * 0.7);
        data.iframeTicks = 14;
        PendingSlam.schedule(player, 14);
        sound(player, SoundEvents.ENDER_DRAGON_FLAP, 1f, 1.4f);
        Fx.ring(player.level(), player.position(), 1.4f, 0xE0C9A0FF, 10, 0);
        ModNetwork.animate(player, PlayerAnims.LEAP, 14);
        return true;
    }

    /** The landing half of Crescent Vorpal. */
    public static void crescentSlam(ServerPlayer player) {
        float damage = attack(player) * 3f;
        for (LivingEntity target : around(player, 5)) {
            strike(player, target, damage, true);
            knockAway(player, target, 1.0);
        }
        ring(player, 5, MOON_WHITE, 40, 0.3);
        ring(player, 4, MOON_BLACK, 32, 0.3);
        player.serverLevel().sendParticles(ParticleTypes.EXPLOSION, player.getX(), player.getY() + 0.2, player.getZ(), 3, 1, 0.1, 1, 0);
        // The Twin Moon crashes down: a vertical crescent and a two-tone shockwave.
        Fx.arc(player, 2.4f, 90, 180, 0xFFEFF4FF, 10);
        Fx.ring(player.level(), player.position(), 5.5f, 0xFFEFF4FF, 16, 1.4f);
        Fx.ring(player.level(), player.position(), 4.2f, 0xFF8E5BE0, 18, 0);
        Fx.flash(player.level(), player.position().add(0, 0.5, 0), 2.5f, 0xFFDDE6FF, 8, 0);
        sound(player, SoundEvents.DRAGON_FIREBALL_EXPLODE, 0.8f, 1.3f);
        ModNetwork.animate(player, PlayerAnims.SLAM, 12);
    }

    private static boolean skyClear(ServerPlayer player) {
        float damage = attack(player) * 2.5f;
        for (LivingEntity target : around(player, 7)) {
            strike(player, target, damage, false);
            knockAway(player, target, 0.8);
        }
        ring(player, 7, SKY_BLUE, 60, 1.1);
        // A full circle cut so clean it clears the sky.
        Fx.arc(player, 6.5f, 0, 360, 0xFF8FD8FF, 14);
        Fx.arc(player, 5.8f, 4, 360, 0xFFFFFFFF, 12);
        Fx.ring(player.level(), player.position(), 7.5f, 0xE08FD8FF, 18, 1.0f);
        Fx.pillar(player.level(), player.position(), 1.2f, 18f, 0xC08FD8FF, 20);
        player.serverLevel().setWeatherParameters(6000, 0, false, false);
        sound(player, SoundEvents.WARDEN_SONIC_BOOM, 0.6f, 1.6f);
        ModNetwork.animate(player, PlayerAnims.SKY_CLEAR, 16);
        return true;
    }

    // ---- Combat helpers ------------------------------------------------------------

    public static float attack(Player player) {
        return (float) Math.max(1.0, player.getAttributeValue(Attributes.ATTACK_DAMAGE));
    }

    /**
     * Deals skill damage, rolling for a critical hit and doubling damage on weak points
     * (striking from behind) when {@code weakPoints} is set.
     */
    public static void strike(ServerPlayer player, LivingEntity target, float damage, boolean weakPoints) {
        boolean crit = player.getRandom().nextFloat() < Progression.critChance(player);
        if (crit) damage *= 1.5f;
        if (weakPoints && isBehind(player, target)) {
            damage *= 1.5f;
            crit = true;
        }
        target.invulnerableTime = 0;
        HitFlags.mark(target, crit, weakPoints && isBehind(player, target));
        target.hurt(player.damageSources().playerAttack(player), damage);
        if (crit) {
            player.serverLevel().sendParticles(ParticleTypes.CRIT, target.getX(), target.getY(0.6), target.getZ(), 12, 0.3, 0.3, 0.3, 0.3);
            player.serverLevel().sendParticles(VORPAL_RED, target.getX(), target.getY(0.6), target.getZ(), 8, 0.3, 0.3, 0.3, 0);
        }
    }

    public static boolean isBehind(Entity attacker, LivingEntity target) {
        Vec3 facing = Vec3.directionFromRotation(0, target.getYRot());
        Vec3 toAttacker = attacker.position().subtract(target.position()).normalize();
        return facing.x * toAttacker.x + facing.z * toAttacker.z < -0.35;
    }

    public static boolean isValidTarget(Player player, Entity e) {
        if (!(e instanceof LivingEntity living) || e == player || !living.isAlive()) return false;
        if (e instanceof Player || e instanceof FrontierNpc) return false;
        if (e instanceof EmulEntity) return false;
        if (living instanceof net.minecraft.world.entity.TamableAnimal tame && tame.isOwnedBy(player)) return false;
        return living.isAttackable();
    }

    public static List<LivingEntity> around(ServerPlayer player, double radius) {
        AABB box = player.getBoundingBox().inflate(radius, 1.5, radius);
        return player.level().getEntitiesOfClass(LivingEntity.class, box,
                e -> isValidTarget(player, e) && e.distanceToSqr(player) <= radius * radius + 2);
    }

    public static List<LivingEntity> entitiesInLine(ServerPlayer player, Vec3 dir, double range) {
        Vec3 start = player.getEyePosition();
        Vec3 end = start.add(dir.scale(range));
        AABB box = new AABB(start, end).inflate(1.2);
        return player.level().getEntitiesOfClass(LivingEntity.class, box, e -> {
                    if (!isValidTarget(player, e)) return false;
                    return e.getBoundingBox().inflate(0.6).clip(start, end).isPresent()
                            || e.getBoundingBox().inflate(0.6).contains(start);
                }).stream()
                .sorted(Comparator.comparingDouble(e -> e.distanceToSqr(player)))
                .toList();
    }

    @Nullable
    public static LivingEntity lookTarget(ServerPlayer player, double range) {
        List<LivingEntity> line = entitiesInLine(player, player.getLookAngle(), range);
        return line.isEmpty() ? null : line.get(0);
    }

    private static void knockAway(Entity from, LivingEntity target, double strength) {
        Vec3 d = target.position().subtract(from.position());
        target.knockback(strength, -d.x, -d.z);
    }

    public static void push(ServerPlayer player, double x, double y, double z) {
        player.setDeltaMovement(x, y, z);
        player.hurtMarked = true;
    }

    private static Vec3 horizontalLook(Player player) {
        float yaw = player.getYRot() * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
    }

    // ---- Effects -------------------------------------------------------------------

    private static void trail(ServerPlayer player, Vec3 dir, double length, ParticleOptions particle, int count) {
        Vec3 start = player.getEyePosition().subtract(0, 0.4, 0);
        for (int i = 0; i < count; i++) {
            Vec3 p = start.add(dir.scale(length * i / count));
            player.serverLevel().sendParticles(particle, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0);
        }
    }

    private static void ring(ServerPlayer player, double radius, ParticleOptions particle, int count, double height) {
        for (int i = 0; i < count; i++) {
            double a = Math.PI * 2 * i / count;
            player.serverLevel().sendParticles(particle, player.getX() + Math.cos(a) * radius,
                    player.getY() + height, player.getZ() + Math.sin(a) * radius, 1, 0, 0, 0, 0);
        }
    }

    /** A crescent of white (Waxing) and black (Waning) light in front of the player. */
    private static void arc(ServerPlayer player, double radius, ParticleOptions first, ParticleOptions second) {
        float yaw = player.getYRot() * Mth.DEG_TO_RAD;
        for (int i = 0; i <= 30; i++) {
            double a = yaw + Math.PI / 2 + (i / 30.0 - 0.5) * Math.PI * 0.9;
            double r = radius * (0.85 + 0.15 * Math.sin(i / 30.0 * Math.PI));
            double x = player.getX() + Math.cos(a) * r;
            double z = player.getZ() + Math.sin(a) * r;
            double y = player.getY() + 1.0 + Math.sin(i / 30.0 * Math.PI) * 0.6;
            player.serverLevel().sendParticles(i % 2 == 0 ? first : second, x, y, z, 2, 0.05, 0.05, 0.05, 0);
        }
    }

    private static void sound(ServerPlayer player, net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
    }

    public static void actionbar(ServerPlayer player, Component message) {
        player.displayClientMessage(message.copy().withStyle(ChatFormatting.YELLOW), true);
    }
}

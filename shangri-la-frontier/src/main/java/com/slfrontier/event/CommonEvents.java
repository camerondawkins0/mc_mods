package com.slfrontier.event;

import com.slfrontier.SLFrontier;
import com.slfrontier.command.SlfCommand;
import com.slfrontier.data.PlayerData;
import com.slfrontier.entity.boss.WezaemonEntity;
import com.slfrontier.item.FrontierWeapon;
import com.slfrontier.item.MoonbladeItem;
import com.slfrontier.item.TwinBladeItem;
import com.slfrontier.network.ModNetwork;
import com.slfrontier.progression.Progression;
import com.slfrontier.quest.Story;
import com.slfrontier.registry.ModAttachments;
import com.slfrontier.registry.ModEffects;
import com.slfrontier.registry.ModItems;
import com.slfrontier.skill.PendingSlam;
import com.slfrontier.skill.PlayerAnims;
import com.slfrontier.skill.Skill;
import com.slfrontier.skill.SkillExecutor;
import com.slfrontier.world.Frontier;
import com.slfrontier.world.FrontierTravel;
import com.slfrontier.world.MapSync;
import com.slfrontier.world.Waypoints;
import com.slfrontier.SlfConfig;
import com.slfrontier.world.TownData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import com.slfrontier.fx.Fx;
import com.slfrontier.fx.HitFlags;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Game-bus handlers: stamina, the Lycagon curse, combat modifiers, deaths and syncing. */
@EventBusSubscriber(modid = SLFrontier.MODID)
public final class CommonEvents {
    private static final float SPRINT_DRAIN = 0.3f;

    private CommonEvents() {
    }

    // ---- Player lifecycle ------------------------------------------------------------

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Progression.applyStats(player);
            ModNetwork.sync(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Story.forget(player);
            MapSync.forget(player);
        }
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Progression.applyStats(player);
            ModNetwork.sync(player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PlayerData data = ModAttachments.get(player);
        if (data.flag("died_in_frontier")) {
            data.setFlag("died_in_frontier", false);
            Waypoints.Waypoint wp = data.respawnWaypoint().isEmpty() || player.getServer() == null ? null
                    : Waypoints.get(player.getServer().overworld()).byId(data.respawnWaypoint());
            if (SlfConfig.RESPAWN_AT_WAYPOINT.get() && wp != null) {
                // Like an MMO save point: wake up at the last waypoint you attuned to.
                FrontierTravel.toWaypoint(player, wp);
            } else if (!Frontier.isIn(player)) {
                // No bed in the Frontier: respawn in Fastera like a fresh avatar.
                FrontierTravel.toFastera(player);
            }
            player.addEffect(new MobEffectInstance(ModEffects.DEATH_PENALTY, 20 * 120, 0));
            player.sendSystemMessage(Component.translatable("message.slfrontier.death_penalty").withStyle(ChatFormatting.RED));
        }
        data.setStamina(data.maxStamina());
        Progression.applyStats(player);
        ModNetwork.sync(player);
    }

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        SlfCommand.register(event.getDispatcher());
    }

    // ---- Ticking ---------------------------------------------------------------------

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PlayerData data = ModAttachments.get(player);
        data.tickCooldowns();
        if (data.parryTicks > 0) data.parryTicks--;
        if (data.iframeTicks > 0) data.iframeTicks--;
        if (player.onGround()) data.airJumpUsed = false;
        PendingSlam.tick(player);
        tickOffhand(player, data);

        boolean inFrontier = Frontier.isIn(player);
        if (inFrontier) {
            float stamina = data.stamina();
            if (player.isSprinting()) {
                stamina -= SPRINT_DRAIN;
                if (stamina <= 0) {
                    player.setSprinting(false);
                }
            } else {
                boolean still = player.getDeltaMovement().horizontalDistanceSqr() < 0.001;
                stamina += still ? 1.0f : 0.5f;
            }
            data.setStamina(stamina);

            if (data.cursed() && player.tickCount % 10 == 0 && !player.isCreative()) {
                enforceCurse(player);
            }
        }
        if (player.tickCount % 40 == 0) {
            Progression.applyStats(player);
        }
        if (player.tickCount % 20 == 0) {
            Story.tickSideQuests(player);
            if (inFrontier && player.tickCount % 200 == 0) FrontierTravel.registerTownWaypoints(player.serverLevel());
            MapSync.tick(player);
        }
        if (data.syncHash() != data.lastSyncedHash) {
            ModNetwork.sync(player);
        }
    }

    /** Lycagon's Mark: the body and legs cannot hold armor while in the Frontier. */
    private static void enforceCurse(ServerPlayer player) {
        boolean removed = false;
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.CHEST, EquipmentSlot.LEGS}) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.isEmpty()) continue;
            player.setItemSlot(slot, ItemStack.EMPTY);
            if (!player.getInventory().add(stack)) player.drop(stack, false);
            removed = true;
        }
        if (removed) {
            player.displayClientMessage(Component.translatable("message.slfrontier.curse_armor").withStyle(ChatFormatting.DARK_RED), true);
            player.level().playSound(null, player.blockPosition(), SoundEvents.WOLF_GROWL, SoundSource.PLAYERS, 0.8f, 0.5f);
        }
    }

    // ---- Dual wielding ---------------------------------------------------------------

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide || !(event.getTarget() instanceof LivingEntity target)) return;
        if (player.getMainHandItem().getItem() instanceof TwinBladeItem && player.getOffhandItem().getItem() instanceof TwinBladeItem
                && player.getAttackStrengthScale(0.5f) > 0.8f) {
            PlayerData data = ModAttachments.get(player);
            data.offhandTargetId = target.getId();
            data.offhandDelay = 4;
        }
    }

    private static void tickOffhand(ServerPlayer player, PlayerData data) {
        if (data.offhandTargetId < 0 || --data.offhandDelay > 0) return;
        Entity entity = player.level().getEntity(data.offhandTargetId);
        data.offhandTargetId = -1;
        ItemStack off = player.getOffhandItem();
        if (!(entity instanceof LivingEntity target) || !target.isAlive() || !(off.getItem() instanceof TwinBladeItem)
                || target.distanceToSqr(player) > 5 * 5) return;
        float damage = (float) (weaponDamage(off) * 0.6);
        player.swing(InteractionHand.OFF_HAND, true);
        target.invulnerableTime = 0;
        target.hurt(player.damageSources().playerAttack(player), damage);
        off.hurtAndBreak(1, player, EquipmentSlot.OFFHAND);
        player.serverLevel().sendParticles(ParticleTypes.SWEEP_ATTACK, target.getX(), target.getY(0.6), target.getZ(), 1, 0, 0, 0, 0);
        Fx.arc(player, 1.6f, -40, 120, 0xFFD8E8FF, 6);
        player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_WEAK, SoundSource.PLAYERS, 0.8f, 1.4f);
        ModNetwork.animate(player, PlayerAnims.OFFHAND, 6);
    }

    private static double weaponDamage(ItemStack stack) {
        double damage = 1;
        ItemAttributeModifiers mods = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        for (ItemAttributeModifiers.Entry entry : mods.modifiers()) {
            if (entry.attribute().value() == Attributes.ATTACK_DAMAGE.value()) damage += entry.modifier().amount();
        }
        return damage;
    }

    // ---- Damage ----------------------------------------------------------------------

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        DamageSource source = event.getSource();

        // Defensive skills: dash i-frames and parries.
        if (victim instanceof ServerPlayer player && Frontier.isIn(player) && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            PlayerData data = ModAttachments.get(player);
            if (data.iframeTicks > 0) {
                event.setCanceled(true);
                return;
            }
            Entity attacker = source.getEntity();
            if (data.parryTicks > 0 && (attacker != null || source.getDirectEntity() != null)) {
                event.setCanceled(true);
                onParry(player, data, attacker);
                return;
            }
        }

        // Offensive bonuses for the player's own attacks.
        if (source.getEntity() instanceof ServerPlayer player && source.getDirectEntity() == player && Frontier.isIn(player)) {
            float amount = event.getAmount();
            ItemStack weapon = player.getMainHandItem();
            if (weapon.getItem() instanceof MoonbladeItem moon) amount *= moon.timeBonus(player.level());
            boolean weak = weapon.getItem() instanceof FrontierWeapon && SkillExecutor.isBehind(player, victim);
            if (weak) amount *= 1.25f;
            boolean crit = player.getRandom().nextFloat() < Progression.critChance(player);
            if (crit) {
                amount *= 1.5f;
                player.serverLevel().sendParticles(ParticleTypes.CRIT, victim.getX(), victim.getY(0.6), victim.getZ(), 10, 0.3, 0.3, 0.3, 0.3);
            }
            HitFlags.mark(victim, crit, weak);
            if (weapon.is(ModItems.EMPIRE_BEE_TWINBLADES.get())) {
                victim.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 0), player);
            }
            event.setAmount(amount);
        }
    }

    private static void onParry(ServerPlayer player, PlayerData data, Entity attacker) {
        ServerLevel level = player.serverLevel();
        level.playSound(null, player.blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1f, 1.4f);
        level.playSound(null, player.blockPosition(), SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, 0.4f, 1.8f);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY(1.0), player.getZ(), 16, 0.4, 0.4, 0.4, 0.3);
        player.displayClientMessage(Component.translatable("message.slfrontier.parry").withStyle(ChatFormatting.AQUA), true);
        data.parryTicks = Math.min(data.parryTicks, 4);
        Fx.flash(level, player.position().add(player.getLookAngle().scale(0.8)).add(0, 1.3, 0), 1.4f, 0xFFBFE8FF, 6, 0.8f);
        ModNetwork.animate(player, PlayerAnims.PARRY_HIT, 7);
        if (data.hasSkill(Skill.PERFECT_PARRY)) SkillExecutor.countUse(player, data, Skill.PERFECT_PARRY);
        if (attacker instanceof WezaemonEntity wezaemon) wezaemon.onParried(player);
        if (data.hasSkill(Skill.REPEL_COUNTER) && attacker instanceof LivingEntity living && living.distanceToSqr(player) < 6 * 6) {
            SkillExecutor.strike(player, living, SkillExecutor.attack(player) * 2f, false);
            living.knockback(1.0, player.getX() - living.getX(), player.getZ() - living.getZ());
            Fx.arc(player, 2.2f, -20, 220, 0xFFFF5A6A, 9);
            ModNetwork.animate(player, PlayerAnims.COUNTER, 9);
        }
    }

    /** Tells the attacking player how much damage landed, for floating damage numbers. */
    @SubscribeEvent
    public static void onDamageDealt(LivingDamageEvent.Post event) {
        LivingEntity victim = event.getEntity();
        int flags = HitFlags.take(victim);
        if (victim.level().isClientSide || !(event.getSource().getEntity() instanceof ServerPlayer player) || victim == player) return;
        if (!Frontier.isIn(player) || event.getNewDamage() <= 0) return;
        PacketDistributor.sendToPlayer(player, new ModNetwork.Hit(victim.getId(), event.getNewDamage(), flags));
    }

    // ---- Deaths ----------------------------------------------------------------------

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead.level().isClientSide) return;

        if (dead instanceof ServerPlayer player) {
            int slot = Progression.findItem(player, ModItems.REBIRTH_TEARJEWEL.get());
            if (slot >= 0) {
                player.getInventory().getItem(slot).shrink(1);
                event.setCanceled(true);
                player.setHealth(player.getMaxHealth() * 0.5f);
                player.removeAllEffects();
                player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 1));
                player.serverLevel().sendParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY(1.0), player.getZ(), 60, 0.5, 0.8, 0.5, 0.3);
                player.level().playSound(null, player.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1f, 1.2f);
                player.displayClientMessage(Component.translatable("message.slfrontier.tearjewel").withStyle(ChatFormatting.LIGHT_PURPLE), true);
                return;
            }
            if (Frontier.isIn(player)) {
                PlayerData data = ModAttachments.get(player);
                data.setFlag("died_in_frontier", true);
                data.setFrontierPos(TownData.get(player.serverLevel()).fastera() != null
                        ? TownData.get(player.serverLevel()).fastera().asLong() : BlockPos.ZERO.asLong());
            }
            return;
        }

        if (event.getSource().getEntity() instanceof ServerPlayer killer && Frontier.isIn(killer)) {
            Progression.grantExp(killer, Progression.expFor(dead));
            Story.onKill(killer, dead);
        }
    }
}

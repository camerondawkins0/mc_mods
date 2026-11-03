package com.slfrontier.event;

import com.slfrontier.registry.ModSounds;
import com.slfrontier.Sfx;
import com.slfrontier.entity.WolfVoice;
import com.slfrontier.entity.Stun;

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
import net.minecraft.util.Mth;
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
            // Loading clamped the health to the base maximum before VIT's bonus was back: restore it.
            PlayerData data = ModAttachments.get(player);
            if (Frontier.isIn(player) && player.isAlive() && data.savedHealth() > player.getHealth()) {
                player.setHealth(Math.min(data.savedHealth(), player.getMaxHealth()));
            }
            ModNetwork.sync(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            rememberHealth(player);
            PendingSlam.clear(player);
            Story.forget(player);
            MapSync.forget(player);
        }
    }

    /** Notes the avatar's health in the Frontier, for {@link #onLogin}. */
    private static void rememberHealth(ServerPlayer player) {
        if (Frontier.isIn(player) && player.isAlive()) ModAttachments.get(player).setSavedHealth(player.getHealth());
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PendingSlam.clear(player);
            Progression.applyStats(player);
            ModNetwork.sync(player);
            if (Frontier.isIn(player) && player.level().getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL) {
                player.sendSystemMessage(Component.translatable("message.slfrontier.peaceful").withStyle(ChatFormatting.GOLD));
            }
        }
    }

    // ---- Keeping your things -----------------------------------------------------------

    private static final String KEPT = "slfrontier_kept";

    /**
     * Dying in the Frontier costs you a death penalty, not your gear: the inventory and experience
     * are set aside before anything drops and handed back on respawn. Runs last, so a revive
     * (Rebirth Tearjewel, totems) or another mod cancelling the death wins.
     */
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
    public static void keepInventory(LivingDeathEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player) || !Frontier.isIn(player)) return;
        if (player.level().getGameRules().get(net.minecraft.world.level.gamerules.GameRules.KEEP_INVENTORY)) return;
        net.minecraft.nbt.CompoundTag kept = new net.minecraft.nbt.CompoundTag();
        kept.put("Items", saveItems(player));
        kept.putInt("XpLevel", player.experienceLevel);
        kept.putFloat("XpProgress", player.experienceProgress);
        kept.putInt("XpTotal", player.totalExperience);
        player.getPersistentData().put(KEPT, kept);
        player.getInventory().clearContent();
    }

    /** Every non-empty inventory slot (bag, armor, off hand) as {Slot, Item} entries. */
    private static net.minecraft.nbt.ListTag saveItems(ServerPlayer player) {
        var ops = player.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);
        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) continue;
            net.minecraft.nbt.CompoundTag entry = new net.minecraft.nbt.CompoundTag();
            entry.putInt("Slot", slot);
            entry.store("Item", ItemStack.CODEC, ops, stack);
            list.add(entry);
        }
        return list;
    }

    /** Data version of Minecraft 1.21.1, which version 0.2 of the mod ran on. */
    private static final int LEGACY_DATA_VERSION = 3955;

    private static void loadItems(Player player, net.minecraft.nbt.ListTag list) {
        var ops = player.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);
        var inventory = player.getInventory();
        inventory.clearContent();
        for (int i = 0; i < list.size(); i++) {
            net.minecraft.nbt.CompoundTag entry = list.getCompoundOrEmpty(i);
            if (entry.contains("Item")) {
                int slot = entry.getIntOr("Slot", -1);
                if (slot >= 0 && slot < inventory.getContainerSize()) {
                    entry.read("Item", ItemStack.CODEC, ops).ifPresent(stack -> inventory.setItem(slot, stack));
                }
                continue;
            }
            // Kept at a death under version 0.2 (Minecraft 1.21.1): Inventory.save's layout, a byte slot
            // (armor 100-103, off hand 150) with the stack's fields alongside, in 1.21.1's item format.
            int raw = entry.getIntOr("Slot", -1) & 0xFF;
            int slot = raw < 36 ? raw : raw >= 100 && raw <= 103 ? 36 + raw - 100 : raw == 150 ? 40 : -1;
            net.minecraft.nbt.CompoundTag stackTag = entry.copy();
            stackTag.remove("Slot");
            ItemStack stack = ItemStack.EMPTY;
            try {
                var fixed = net.minecraft.util.datafix.DataFixers.getDataFixer().update(net.minecraft.util.datafix.fixes.References.ITEM_STACK,
                        new com.mojang.serialization.Dynamic<>(net.minecraft.nbt.NbtOps.INSTANCE, (net.minecraft.nbt.Tag) stackTag),
                        LEGACY_DATA_VERSION, net.minecraft.SharedConstants.getCurrentVersion().dataVersion().version());
                stack = ItemStack.CODEC.parse(ops, fixed.getValue()).result().orElse(ItemStack.EMPTY);
            } catch (RuntimeException e) {
                SLFrontier.LOGGER.warn("Could not upgrade an item kept at death: {}", stackTag, e);
            }
            if (stack.isEmpty()) {
                SLFrontier.LOGGER.warn("Dropped an unreadable item kept at death (slot {}): {}", raw, stackTag);
            } else if (slot >= 0 && slot < inventory.getContainerSize() && inventory.getItem(slot).isEmpty()) {
                inventory.setItem(slot, stack);
            } else if (!inventory.add(stack)) {
                player.drop(stack, false);
            }
        }
    }

    @SubscribeEvent
    public static void noExperienceOrbs(net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.getPersistentData().contains(KEPT)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void restoreInventory(PlayerEvent.Clone event) {
        if (!event.isWasDeath()) return;
        net.minecraft.nbt.CompoundTag kept = event.getOriginal().getPersistentData().getCompoundOrEmpty(KEPT);
        event.getEntity().getPersistentData().remove(KEPT);
        if (kept.isEmpty()) return;
        var player = event.getEntity();
        loadItems(player, kept.getListOrEmpty("Items"));
        player.experienceLevel = kept.getIntOr("XpLevel", 0);
        player.experienceProgress = kept.getFloatOr("XpProgress", 0f);
        player.totalExperience = kept.getIntOr("XpTotal", 0);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PlayerData data = ModAttachments.get(player);
        if (data.flag("died_in_frontier")) {
            data.setFlag("died_in_frontier", false);
            Waypoints.Waypoint wp = data.respawnWaypoint().isEmpty() || player.level().getServer() == null ? null
                    : Waypoints.get(player.level().getServer().overworld()).byId(data.respawnWaypoint());
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
        // A new avatar body starts whole: full health including VIT's bonus.
        if (Frontier.isIn(player)) player.setHealth(player.getMaxHealth());
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
            } else if (data.staminaRegenPause > 0) {
                // A beat after every skill before stamina comes back, so spamming dashes costs something.
                data.staminaRegenPause--;
            } else {
                boolean still = player.getDeltaMovement().horizontalDistanceSqr() < 0.001;
                stamina += (still ? 1.0f : 0.5f) * data.staminaRegen();
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
            rememberHealth(player);
            if (!data.curseOverflow().isEmpty()) returnCurseOverflow(player, data);
            Story.tickSideQuests(player);
            if (inFrontier && player.tickCount % 200 == 0) FrontierTravel.registerTownWaypoints(player.level());
            if (inFrontier && player.tickCount % 40 == 0) com.slfrontier.world.Landmarks.tick(player.level());
            if (inFrontier) Story.tickPlayer(player);
            MapSync.tick(player);
        }
        if (data.syncHash() != data.lastSyncedHash) {
            ModNetwork.sync(player);
        } else {
            ModNetwork.syncStamina(player, data);
        }
    }

    /**
     * Lycagon's Mark: the body and legs cannot hold armor while in the Frontier. Armor that doesn't
     * fit in the bag is kept safe (not dropped where it could be lost) and handed back when there's room.
     */
    private static void enforceCurse(ServerPlayer player) {
        boolean removed = false;
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.CHEST, EquipmentSlot.LEGS}) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.isEmpty()) continue;
            player.setItemSlot(slot, ItemStack.EMPTY);
            if (!player.getInventory().add(stack)) {
                ModAttachments.get(player).curseOverflow().add(stack);
                player.sendSystemMessage(Component.translatable("message.slfrontier.curse_armor_kept", stack.getHoverName())
                        .withStyle(ChatFormatting.GRAY));
            }
            removed = true;
        }
        if (removed) {
            player.sendOverlayMessage(Component.translatable("message.slfrontier.curse_armor").withStyle(ChatFormatting.DARK_RED));
            player.level().playSound(null, player.blockPosition(), WolfVoice.GROWL, SoundSource.PLAYERS, 0.8f, 0.5f);
        }
    }

    /** Hands back armor the Mark took off as soon as the bag has room for it. */
    private static void returnCurseOverflow(ServerPlayer player, PlayerData data) {
        var it = data.curseOverflow().iterator();
        while (it.hasNext()) {
            ItemStack stack = it.next();
            if (stack.isEmpty()) {
                it.remove();
                continue;
            }
            Component name = stack.getHoverName();
            if (!player.getInventory().add(stack)) break;
            it.remove();
            player.sendSystemMessage(Component.translatable("message.slfrontier.curse_armor_returned", name).withStyle(ChatFormatting.GRAY));
        }
    }

    // ---- Dual wielding ---------------------------------------------------------------

    @SubscribeEvent
    public static void onServerTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 20 == 0) com.slfrontier.quest.ColosseumTrial.tick(event.getServer());
        if (event.getServer().getTickCount() % 10 == 0) com.slfrontier.entity.FrontierBoss.sweepBossBars();
    }

    @SubscribeEvent
    public static void onEntityTick(net.neoforged.neoforge.event.tick.EntityTickEvent.Post event) {
        if (event.getEntity() instanceof net.minecraft.world.entity.Mob mob && !mob.level().isClientSide()) Stun.tick(mob);
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide() || !(event.getTarget() instanceof LivingEntity target)) return;
        if (player.getMainHandItem().getItem() instanceof TwinBladeItem && player.getOffhandItem().getItem() instanceof TwinBladeItem
                && player.getAttackStrengthScale(0.5f) > 0.8f) {
            PlayerData data = ModAttachments.get(player);
            long now = player.level().getGameTime();
            data.comboStage = now - data.comboLastHit <= COMBO_WINDOW ? data.comboStage + 1 : 1;
            data.comboLastHit = now;
            if (data.comboStage >= 3 && player instanceof ServerPlayer sp && Frontier.isIn(sp)) {
                crossSlash(sp, data, target);
            } else {
                data.offhandTargetId = target.getId();
                data.offhandDelay = 4;
                data.offhandFinisher = false;
            }
        }
    }

    /** Ticks between twin-blade hits that still count as one combo (forgiving enough for some lag). */
    private static final int COMBO_WINDOW = 40;

    /**
     * Third hit in a row: both blades cut an X together for +20% damage: this swing's main-hand hit
     * and the off-hand blade right behind it (instead of the usual follow-up a beat later).
     */
    private static void crossSlash(ServerPlayer player, PlayerData data, LivingEntity target) {
        data.comboStage = 0;
        // Only this swing on this target is the finisher; a hit that never lands lets it lapse.
        data.finisherTargetId = target.getId();
        data.finisherTick = player.level().getGameTime();
        data.offhandTargetId = target.getId();
        data.offhandDelay = 2;
        data.offhandFinisher = true;
        ServerLevel level = player.level();
        Fx.arc(player, 2.2f, 52, 150, 0xFFEAF4FF, 8);
        Fx.arc(player, 2.2f, -52, 150, 0xFF9FD0FF, 8);
        Fx.flash(level, target.position().add(0, target.getBbHeight() * 0.6, 0), 1.6f, 0xFFE8F4FF, 7, 0.6f);
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, target.getX(), target.getY(0.6), target.getZ(), 2, 0.3, 0.2, 0.3, 0);
        Sfx.at(player, ModSounds.COMBAT_CROSS_SLASH, SoundSource.PLAYERS, 1.1f, 1f);
        target.knockback(0.5, player.getX() - target.getX(), player.getZ() - target.getZ(), target.damageSources().generic(), 0f);
        ModNetwork.animate(player, PlayerAnims.CROSS_SLASH, 9);
    }

    /** The off-hand blade's share of what it would deal in the main hand (strength included). */
    public static final float OFFHAND_SHARE = 0.3f;

    /**
     * The off-hand blade's follow-up. It is an extra hit on top of the main-hand swing (see
     * {@link com.slfrontier.skill.ExtraHit}): it no longer resets the target's hit immunity, so the
     * next main-hand swing lands in full and every swing deals main hand + off hand.
     */
    private static void tickOffhand(ServerPlayer player, PlayerData data) {
        if (data.offhandTargetId < 0 || --data.offhandDelay > 0) return;
        Entity entity = player.level().getEntity(data.offhandTargetId);
        boolean finisher = data.offhandFinisher;
        data.offhandTargetId = -1;
        data.offhandFinisher = false;
        ItemStack off = player.getOffhandItem();
        if (!player.isAlive() || !(entity instanceof LivingEntity target) || !target.isAlive() || !(off.getItem() instanceof TwinBladeItem)
                || target.distanceToSqr(player) > 5 * 5) return;
        double strength = ModAttachments.get(player).stat(com.slfrontier.data.Stat.STR) * Progression.STR_DAMAGE;
        float damage = (float) ((weaponDamage(off) + strength) * OFFHAND_SHARE);
        player.swing(InteractionHand.OFF_HAND, true);
        Sfx.at(player, ModSounds.COMBAT_SLASH, SoundSource.PLAYERS, 0.7f, 1.05f + player.getRandom().nextFloat() * 0.1f);
        com.slfrontier.skill.ExtraHit.deal(target, player.damageSources().playerAttack(player), damage,
                com.slfrontier.skill.ExtraHit.Kind.OFFHAND, finisher);
        off.hurtAndBreak(1, player, EquipmentSlot.OFFHAND);
        player.level().sendParticles(ParticleTypes.SWEEP_ATTACK, target.getX(), target.getY(0.6), target.getZ(), 1, 0, 0, 0, 0);
        if (!finisher) {
            Fx.arc(player, 1.6f, -40, 120, 0xFFD8E8FF, 6);
            player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_WEAK, SoundSource.PLAYERS, 0.8f, 1.4f);
            ModNetwork.animate(player, PlayerAnims.OFFHAND, 6);
        }
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

        // A monster reeling from a parry can't land a blow (projectiles already in flight still can).
        if (source.getEntity() != null && source.getDirectEntity() == source.getEntity() && Stun.isStunned(source.getEntity())) {
            event.setCanceled(true);
            return;
        }

        // Defensive skills: dash and attack-skill i-frames, and parries.
        if (victim instanceof ServerPlayer player && Frontier.isIn(player) && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            PlayerData data = ModAttachments.get(player);
            if (data.iframeTicks > 0) {
                event.setCanceled(true);
                return;
            }
            Entity attacker = source.getEntity();
            if (data.parryTicks > 0 && (attacker != null || source.getDirectEntity() != null)) {
                if (com.slfrontier.entity.FrontierBoss.isUnparryable(source)) {
                    // Eruptions, beams and magic (crimson telegraphs) go straight through a parry, spending it.
                    data.parryTicks = 0;
                    Sfx.personal(player, ModSounds.COMBAT_PARRY_FAIL, 1f, 1f);
                    player.sendOverlayMessage(Component.translatable("message.slfrontier.parry.unparryable").withStyle(ChatFormatting.RED));
                } else {
                    event.setCanceled(true);
                    onParry(player, data, attacker, source.getDirectEntity() == attacker);
                    return;
                }
            }
        }

        // Offensive bonuses for the player's own blade hits (main hand and off hand). Skills roll their
        // own crits and weak points in SkillExecutor.strike, so they are left alone here.
        com.slfrontier.skill.ExtraHit.Kind extra = com.slfrontier.skill.ExtraHit.current();
        if (source.getEntity() instanceof ServerPlayer player && source.getDirectEntity() == player && Frontier.isIn(player)
                && extra != com.slfrontier.skill.ExtraHit.Kind.SKILL) {
            boolean offhand = extra == com.slfrontier.skill.ExtraHit.Kind.OFFHAND;
            float amount = event.getAmount();
            ItemStack weapon = offhand ? player.getOffhandItem() : player.getMainHandItem();
            amount *= Progression.weaponBonus(weapon, player.level());
            boolean weak = weapon.getItem() instanceof FrontierWeapon && SkillExecutor.isBehind(player, victim);
            if (weak) amount *= 1.25f;
            boolean crit = player.getRandom().nextFloat() < Progression.critChance(player, weapon);
            if (crit) {
                amount *= Progression.critMultiplier(player);
                player.level().sendParticles(ParticleTypes.CRIT, victim.getX(), victim.getY(0.6), victim.getZ(), 10, 0.3, 0.3, 0.3, 0.3);
            }
            HitFlags.mark(victim, crit, weak);
            PlayerData attackerData = ModAttachments.get(player);
            boolean finisher = offhand ? com.slfrontier.skill.ExtraHit.isFinisher()
                    : attackerData.finisherTargetId == victim.getId() && attackerData.finisherTick == player.level().getGameTime();
            if (!offhand) attackerData.finisherTargetId = -1;
            if (finisher) {
                amount *= 1.2f;
                HitFlags.add(victim, HitFlags.FINISHER);
            }
            if (weapon.is(ModItems.EMPIRE_BEE_TWINBLADES.get())) {
                victim.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 0), player);
            }
            if (!offhand) {
                // Hit immunity follows the swing: a full-strength swing always lands, however fast the
                // blade (vanilla's fixed ten ticks wasted every other swing above two swings a second).
                int earliestFullSwing = (int) Math.ceil(player.getCurrentItemAttackStrengthDelay() - 0.5f);
                event.getContainer().setPostAttackInvulnerabilityTicks(Mth.clamp(10 + earliestFullSwing - 1, 10, 20));
            }
            event.setAmount(amount);
        }
    }

    /** @param melee the attacker struck in person (not with an arrow or other projectile) */
    private static void onParry(ServerPlayer player, PlayerData data, Entity attacker, boolean melee) {
        ServerLevel level = player.level();
        Sfx.at(player, ModSounds.COMBAT_PARRY, SoundSource.PLAYERS, 1.2f, 0.95f + player.getRandom().nextFloat() * 0.1f);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY(1.0), player.getZ(), 16, 0.4, 0.4, 0.4, 0.3);
        player.sendOverlayMessage(Component.translatable("message.slfrontier.parry").withStyle(ChatFormatting.AQUA));
        data.parryTicks = Math.min(data.parryTicks, 4);
        Fx.flash(level, player.position().add(player.getLookAngle().scale(0.8)).add(0, 1.3, 0), 1.4f, 0xFFBFE8FF, 6, 0.8f);
        ModNetwork.animate(player, PlayerAnims.PARRY_HIT, 7);
        if (data.hasSkill(Skill.PERFECT_PARRY)) SkillExecutor.countUse(player, data, Skill.PERFECT_PARRY);
        // A perfect parry knocks a nearby attacker off balance. A boss only reels from a parried blow
        // (not a parried stinger), and the attack it was making is broken off (Wezaemon's Sky Clear disarms him).
        if (attacker instanceof com.slfrontier.entity.FrontierBoss boss) {
            if (melee && boss.distanceToSqr(player) < 8 * 8) boss.onParried(player);
        } else if (attacker instanceof LivingEntity living && living != player && living.distanceToSqr(player) < 8 * 8) {
            Stun.apply(living, Stun.PARRY_TICKS);
        }
        if (data.hasSkill(Skill.REPEL_COUNTER) && attacker instanceof LivingEntity living && living.distanceToSqr(player) < 6 * 6) {
            SkillExecutor.strike(player, living, SkillExecutor.attack(player) * 2f, false);
            living.knockback(1.0, player.getX() - living.getX(), player.getZ() - living.getZ(), living.damageSources().generic(), 0f);
            Fx.arc(player, 2.2f, -20, 220, 0xFFFF5A6A, 9);
            ModNetwork.animate(player, PlayerAnims.COUNTER, 9);
        }
    }

    /** Tells the attacking player how much damage landed, for floating damage numbers. */
    @SubscribeEvent
    public static void onDamageDealt(LivingDamageEvent.Post event) {
        LivingEntity victim = event.getEntity();
        int flags = HitFlags.take(victim);
        if (victim.level().isClientSide() || !(event.getSource().getEntity() instanceof ServerPlayer player) || victim == player) return;
        if (!Frontier.isIn(player) || event.getHealthDamage() <= 0) return;
        com.slfrontier.progression.KillCredit.record(victim, player);
        if (victim.isDeadOrDying()) {
            flags |= HitFlags.KILL;
        } else if (victim instanceof com.slfrontier.entity.FrontierMonster
                && ((flags & HitFlags.FINISHER) != 0 || (flags & (HitFlags.CRIT | HitFlags.WEAK)) == (HitFlags.CRIT | HitFlags.WEAK))) {
            // A cross-slash finisher or a critical blow to a weak point staggers a regular monster
            // (and interrupts whatever it was winding up).
            Stun.apply(victim, 8);
        }
        PacketDistributor.sendToPlayer(player, new ModNetwork.Hit(victim.getId(), event.getHealthDamage(), flags));
    }

    // ---- Deaths ----------------------------------------------------------------------

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead.level().isClientSide()) return;

        if (dead instanceof ServerPlayer player) {
            int slot = Progression.findItem(player, ModItems.REBIRTH_TEARJEWEL.get());
            if (slot >= 0) {
                player.getInventory().getItem(slot).shrink(1);
                event.setCanceled(true);
                player.setHealth(player.getMaxHealth() * 0.5f);
                player.removeAllEffects();
                player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 1));
                player.level().sendParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY(1.0), player.getZ(), 60, 0.5, 0.8, 0.5, 0.3);
                player.level().playSound(null, player.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1f, 1.2f);
                player.sendOverlayMessage(Component.translatable("message.slfrontier.tearjewel").withStyle(ChatFormatting.LIGHT_PURPLE));
                return;
            }
            PendingSlam.clear(player);
            if (Frontier.isIn(player)) {
                PlayerData data = ModAttachments.get(player);
                data.setFlag("died_in_frontier", true);
                data.setFrontierPos(TownData.get(player.level()).fastera() != null
                        ? TownData.get(player.level()).fastera().asLong() : BlockPos.ZERO.asLong());
            }
            return;
        }

        com.slfrontier.quest.ColosseumTrial.onDeath(dead);
        // The hive's queen is part of its template and never comes back: remember it for the whole world.
        if (dead.getType() == com.slfrontier.registry.ModEntities.EMPIRE_BEE_QUEEN.get() && dead.level() instanceof ServerLevel server) {
            com.slfrontier.quest.WorldStory.set(server, com.slfrontier.quest.WorldStory.BEE_QUEEN_SLAIN);
        }

        // Everyone who fought it shares the kill (the last blow may be an ally's, Emul's or a friend's).
        int exp = Progression.expFor(dead);
        for (ServerPlayer player : com.slfrontier.progression.KillCredit.creditFor(dead, event.getSource().getEntity())) {
            Progression.grantExp(player, exp);
            Story.onKill(player, dead);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(net.neoforged.neoforge.event.server.ServerStoppingEvent event) {
        Story.resetTimers();
        com.slfrontier.quest.ColosseumTrial.shutdown(event.getServer());
    }

    /** A Colosseum foe whose trial no longer runs (the game stopped mid-round) leaves when it loads again. */
    @SubscribeEvent
    public static void onEntityJoin(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && com.slfrontier.quest.ColosseumTrial.isOrphanFoe(event.getEntity())) {
            event.setCanceled(true);
        }
    }
}

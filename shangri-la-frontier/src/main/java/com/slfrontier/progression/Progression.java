package com.slfrontier.progression;

import com.slfrontier.registry.ModSounds;
import com.slfrontier.Sfx;
import com.slfrontier.SLFrontier;
import com.slfrontier.data.PlayerData;
import com.slfrontier.data.Stat;
import com.slfrontier.entity.LeveledMob;
import com.slfrontier.network.ModNetwork;
import com.slfrontier.registry.ModAttachments;
import com.slfrontier.registry.ModItems;
import com.slfrontier.skill.Skill;
import com.slfrontier.world.Frontier;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Experience, level-ups and turning stat points into real attribute bonuses. */
public final class Progression {
    private static final Identifier VIT_ID = SLFrontier.id("stat_vit");
    private static final Identifier STR_ID = SLFrontier.id("stat_str");
    private static final Identifier AGI_ID = SLFrontier.id("stat_agi");
    private static final Identifier AGI_JUMP_ID = SLFrontier.id("stat_agi_jump");
    /** Jump strength per AGI point, and the cap: 1.25 blocks base, at most ~1.85 (never a 2-block wall). */
    private static final double JUMP_PER_AGI = 0.0015, JUMP_CAP = 0.10;
    private static final Identifier DEX_ID = SLFrontier.id("stat_dex");
    private static final Identifier LUC_ID = SLFrontier.id("stat_luc");
    private static final Identifier MASK_ID = SLFrontier.id("bird_mask_agility");
    /** Attack damage per point of STR (the off-hand blade gets its share too). */
    public static final double STR_DAMAGE = 0.25;
    /** Per point of LUC: critical chance, and critical damage on top of x1.5. */
    public static final float LUC_CRIT_CHANCE = 0.01f, LUC_CRIT_DAMAGE = 0.005f;

    private Progression() {
    }

    /** Experience granted for defeating an entity inside the Frontier. */
    public static int expFor(LivingEntity killed) {
        // Lycagon's short-lived shadow clones are part of its moveset, not a kill (summoned monsters give
        // nothing either: see FrontierMonster#frontierExp).
        if (killed instanceof com.slfrontier.entity.boss.LycagonShadowEntity shadow && shadow.isClone()) return 0;
        if (killed instanceof LeveledMob leveled) {
            return leveled.frontierExp();
        }
        float hp = killed.getMaxHealth();
        int base = Math.max(1, Math.round(hp / 2f));
        return killed instanceof Enemy ? base : Math.max(1, base / 3);
    }

    /**
     * Adds experience. The Vorpal Soul Collar's extra stat points are earned, not snatched: a level
     * pays 5 points instead of 3 only if most of that level's experience was earned with the collar
     * carried (picking it up just before a level-up, or a big reward, no longer counts).
     */
    public static void grantExp(ServerPlayer player, int amount) {
        PlayerData data = ModAttachments.get(player);
        if (data.level() >= PlayerData.MAX_LEVEL || amount <= 0) return;
        boolean collar = hasCollar(player);
        if (collar) amount = Math.max(1, Math.round(amount * 0.7f));
        // Saves from before this was tracked: count the level so far as the collar is now.
        if (data.collarExp() < 0) data.setCollarExp(collar ? data.exp() : 0);
        int levelsGained = 0;
        int left = amount;
        while (left > 0 && data.level() < PlayerData.MAX_LEVEL) {
            int need = PlayerData.expToNext(data.level());
            int take = Math.min(left, need - data.exp());
            left -= take;
            data.setExp(data.exp() + take);
            if (collar) data.setCollarExp(data.collarExp() + take);
            if (data.exp() < need) break;
            boolean collarLevel = data.collarExp() * 2 > need;
            data.setLevel(data.level() + 1);
            data.setCollarExp(0);
            data.addStatPoints(collarLevel ? 5 : 3);
            levelsGained++;
        }
        if (data.level() >= PlayerData.MAX_LEVEL) data.setExp(0);
        if (levelsGained > 0) {
            onLevelUp(player, data);
        }
    }

    private static void onLevelUp(ServerPlayer player, PlayerData data) {
        Sfx.personal(player, ModSounds.PLAYER_LEVEL_UP, 1f, 1f);
        player.setHealth(player.getMaxHealth());
        data.setStamina(data.maxStamina());
        ModNetwork.announce(player, "announce.slfrontier.level_up", "announce.slfrontier.level_up.sub", 0xFFD34F);
        player.sendSystemMessage(Component.translatable("message.slfrontier.level_up", data.level(), data.statPoints())
                .withStyle(ChatFormatting.GOLD));
        for (Skill skill : Skill.values()) {
            if (skill.unlockLevel > 0 && skill.unlockLevel <= data.level() && data.unlock(skill)) {
                announceSkill(player, skill);
            }
        }
        applyStats(player);
    }

    public static void announceSkill(ServerPlayer player, Skill skill) {
        player.sendSystemMessage(Component.translatable("message.slfrontier.skill_learned",
                Component.translatable(skill.nameKey()).withStyle(ChatFormatting.AQUA)).withStyle(ChatFormatting.GREEN));
        Sfx.personal(player, ModSounds.PLAYER_SKILL_LEARNED, 1f, 1f);
    }

    public static boolean hasCollar(Player player) {
        return hasItem(player, ModItems.VORPAL_SOUL_COLLAR.get());
    }

    public static boolean hasItem(Player player, net.minecraft.world.item.Item item) {
        return findItem(player, item) >= 0;
    }

    /** @return the inventory slot holding the item, or -1 */
    public static int findItem(Player player, net.minecraft.world.item.Item item) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && stack.is(item)) return i;
        }
        return -1;
    }

    /** Counts how many of an item the player carries. */
    public static int countItem(Player player, net.minecraft.world.item.Item item) {
        var inv = player.getInventory();
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.is(item)) n += stack.getCount();
        }
        return n;
    }

    /** Removes up to {@code count} of an item; returns how many were removed. */
    public static int takeItem(Player player, net.minecraft.world.item.Item item, int count) {
        var inv = player.getInventory();
        int left = count;
        for (int i = 0; i < inv.getContainerSize() && left > 0; i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.is(item)) {
                int take = Math.min(left, stack.getCount());
                stack.shrink(take);
                left -= take;
            }
        }
        return count - left;
    }

    /**
     * Re-applies every stat bonus. Bonuses exist only while the avatar is inside the
     * Frontier — outside, you're just yourself again.
     */
    public static void applyStats(ServerPlayer player) {
        PlayerData data = ModAttachments.get(player);
        boolean active = Frontier.isIn(player);
        set(player, Attributes.MAX_HEALTH, VIT_ID, active ? data.stat(Stat.VIT) * 1.0 : 0, AttributeModifier.Operation.ADD_VALUE);
        set(player, Attributes.ATTACK_DAMAGE, STR_ID, active ? data.stat(Stat.STR) * STR_DAMAGE : 0, AttributeModifier.Operation.ADD_VALUE);
        set(player, Attributes.MOVEMENT_SPEED, AGI_ID, active ? Math.min(0.6, data.stat(Stat.AGI) * 0.004) : 0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        set(player, Attributes.JUMP_STRENGTH, AGI_JUMP_ID, active ? Math.min(JUMP_CAP, data.stat(Stat.AGI) * JUMP_PER_AGI) : 0,
                AttributeModifier.Operation.ADD_VALUE);
        set(player, Attributes.ATTACK_SPEED, DEX_ID, active ? Math.min(1.0, data.stat(Stat.DEX) * 0.01) : 0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        set(player, Attributes.LUCK, LUC_ID, active ? data.stat(Stat.LUC) * 0.1 : 0, AttributeModifier.Operation.ADD_VALUE);
        boolean mask = active && player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).is(ModItems.BIRD_MASK.get());
        set(player, Attributes.MOVEMENT_SPEED, MASK_ID, mask ? 0.05 : 0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
        data.setStamina(data.stamina());
    }

    private static void set(Player player, Holder<Attribute> attribute, Identifier id, double amount,
                            AttributeModifier.Operation op) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) return;
        AttributeModifier existing = instance.getModifier(id);
        if (amount == 0) {
            if (existing != null) instance.removeModifier(id);
            return;
        }
        if (existing != null && existing.amount() == amount) return;
        instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, op));
    }

    /** Critical hit chance from Luck plus the main-hand weapon's bonus (0..1). */
    public static float critChance(Player player) {
        return critChance(player, player.getMainHandItem());
    }

    /** Critical hit chance of a hit with {@code weapon} (the off-hand blade rolls with its own bonus). */
    public static float critChance(Player player, ItemStack weapon) {
        PlayerData data = ModAttachments.get(player);
        float chance = 0.05f + data.stat(Stat.LUC) * LUC_CRIT_CHANCE;
        if (weapon.getItem() instanceof com.slfrontier.item.FrontierWeapon frontier) {
            chance += frontier.critBonus();
        }
        return Math.min(chance, 0.75f);
    }

    /** How much a critical hit multiplies damage: x1.5, plus a little per point of Luck. */
    public static float critMultiplier(Player player) {
        return 1.5f + ModAttachments.get(player).stat(Stat.LUC) * LUC_CRIT_DAMAGE;
    }

    /** A weapon's own damage bonus right now: a Moonblade in its hour, the Twin Moon always. */
    public static float weaponBonus(ItemStack weapon, net.minecraft.world.level.Level level) {
        if (weapon.getItem() instanceof com.slfrontier.item.MoonbladeItem moon) return moon.timeBonus(level);
        if (weapon.getItem() instanceof com.slfrontier.item.TwinMoonItem twin) return twin.damageBonus();
        return 1f;
    }

    /** Cooldown multiplier from Technique. */
    public static float cooldownScale(PlayerData data) {
        return Math.max(0.5f, 1f - data.stat(Stat.TEC) * 0.01f);
    }
}

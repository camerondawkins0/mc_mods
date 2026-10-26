package com.slfrontier.progression;

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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
    private static final ResourceLocation VIT_ID = SLFrontier.id("stat_vit");
    private static final ResourceLocation STR_ID = SLFrontier.id("stat_str");
    private static final ResourceLocation AGI_ID = SLFrontier.id("stat_agi");
    private static final ResourceLocation DEX_ID = SLFrontier.id("stat_dex");
    private static final ResourceLocation LUC_ID = SLFrontier.id("stat_luc");
    private static final ResourceLocation MASK_ID = SLFrontier.id("bird_mask_agility");

    private Progression() {
    }

    /** Experience granted for defeating an entity inside the Frontier. */
    public static int expFor(LivingEntity killed) {
        if (killed instanceof LeveledMob leveled) {
            return leveled.frontierExp();
        }
        float hp = killed.getMaxHealth();
        int base = Math.max(1, Math.round(hp / 2f));
        return killed instanceof Enemy ? base : Math.max(1, base / 3);
    }

    public static void grantExp(ServerPlayer player, int amount) {
        PlayerData data = ModAttachments.get(player);
        if (data.level() >= PlayerData.MAX_LEVEL || amount <= 0) return;
        boolean collar = hasCollar(player);
        if (collar) amount = Math.max(1, Math.round(amount * 0.7f));
        int exp = data.exp() + amount;
        int levelsGained = 0;
        while (data.level() < PlayerData.MAX_LEVEL && exp >= PlayerData.expToNext(data.level())) {
            exp -= PlayerData.expToNext(data.level());
            data.setLevel(data.level() + 1);
            data.addStatPoints(collar ? 5 : 3);
            levelsGained++;
        }
        data.setExp(data.level() >= PlayerData.MAX_LEVEL ? 0 : exp);
        if (levelsGained > 0) {
            onLevelUp(player, data);
        }
    }

    private static void onLevelUp(ServerPlayer player, PlayerData data) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1f, 1.2f);
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
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1f, 1.4f);
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
        set(player, Attributes.ATTACK_DAMAGE, STR_ID, active ? data.stat(Stat.STR) * 0.25 : 0, AttributeModifier.Operation.ADD_VALUE);
        set(player, Attributes.MOVEMENT_SPEED, AGI_ID, active ? Math.min(0.6, data.stat(Stat.AGI) * 0.004) : 0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        set(player, Attributes.ATTACK_SPEED, DEX_ID, active ? Math.min(1.0, data.stat(Stat.DEX) * 0.01) : 0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        set(player, Attributes.LUCK, LUC_ID, active ? data.stat(Stat.LUC) * 0.1 : 0, AttributeModifier.Operation.ADD_VALUE);
        boolean mask = active && player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).is(ModItems.BIRD_MASK.get());
        set(player, Attributes.MOVEMENT_SPEED, MASK_ID, mask ? 0.05 : 0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
        data.setStamina(data.stamina());
    }

    private static void set(Player player, Holder<Attribute> attribute, ResourceLocation id, double amount,
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

    /** Critical hit chance from Luck plus weapon bonuses (0..1). */
    public static float critChance(Player player) {
        PlayerData data = ModAttachments.get(player);
        float chance = 0.05f + data.stat(Stat.LUC) * 0.005f;
        if (player.getMainHandItem().getItem() instanceof com.slfrontier.item.FrontierWeapon weapon) {
            chance += weapon.critBonus();
        }
        return Math.min(chance, 0.75f);
    }

    /** Cooldown multiplier from Technique. */
    public static float cooldownScale(PlayerData data) {
        return Math.max(0.5f, 1f - data.stat(Stat.TEC) * 0.01f);
    }
}

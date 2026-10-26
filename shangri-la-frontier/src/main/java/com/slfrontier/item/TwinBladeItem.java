package com.slfrontier.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * Twin blades are wielded one per hand. Holding a second twin blade in the off hand
 * adds a follow-up off-hand strike to every main-hand hit (see CombatEvents).
 */
public class TwinBladeItem extends SwordItem implements FrontierWeapon {
    private final float critBonus;
    private final String loreKey;

    public TwinBladeItem(Tier tier, int damage, float speed, float critBonus, String loreKey, Properties properties) {
        super(tier, properties.attributes(SwordItem.createAttributes(tier, damage, speed)));
        this.critBonus = critBonus;
        this.loreKey = loreKey;
    }

    @Override
    public Kind kind() {
        return Kind.TWIN_BLADES;
    }

    @Override
    public float critBonus() {
        return critBonus;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.slfrontier.twin_blades").withStyle(ChatFormatting.BLUE));
        if (critBonus > 0) {
            tooltip.add(Component.translatable("tooltip.slfrontier.crit_bonus", Math.round(critBonus * 100)).withStyle(ChatFormatting.RED));
        }
        if (loreKey != null) {
            tooltip.add(Component.translatable(loreKey).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
    }
}

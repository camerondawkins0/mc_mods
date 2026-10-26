package com.slfrontier.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** A plain item with a lore line taken from {@code item.slfrontier.<id>.lore}. */
public class TooltipItem extends Item {
    private final boolean foil;

    public TooltipItem(Properties properties) {
        this(properties, false);
    }

    public TooltipItem(Properties properties, boolean foil) {
        super(properties);
        this.foil = foil;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return foil || super.isFoil(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(getDescriptionId() + ".lore").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }
}

package com.slfrontier.inventory;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Tidies the 27 bag slots of a player inventory: partial stacks are merged, then everything is
 * ordered by category, name and count (largest first). Hotbar, armor and off hand are untouched.
 */
public final class BagSort {
    public static final int FIRST = 9, END = 36;

    private static final Comparator<ItemStack> ORDER = Comparator
            .comparingInt((ItemStack s) -> ItemCategory.of(s).ordinal())
            .thenComparing(s -> s.getHoverName().getString(), String.CASE_INSENSITIVE_ORDER)
            .thenComparing(Comparator.comparingInt(ItemStack::getCount).reversed())
            .thenComparing(s -> BuiltInRegistries.ITEM.getKey(s.getItem()).toString());

    private BagSort() {
    }

    public static void sort(Inventory inventory) {
        List<ItemStack> stacks = new ArrayList<>();
        for (int i = FIRST; i < END; i++) {
            ItemStack stack = inventory.getItem(i).copy();
            if (stack.isEmpty()) continue;
            for (ItemStack into : stacks) {
                int room = inventory.getMaxStackSize(into) - into.getCount();
                if (room > 0 && ItemStack.isSameItemSameComponents(into, stack)) {
                    int moved = Math.min(room, stack.getCount());
                    into.grow(moved);
                    stack.shrink(moved);
                    if (stack.isEmpty()) break;
                }
            }
            if (!stack.isEmpty()) stacks.add(stack);
        }
        stacks.sort(ORDER);
        for (int i = FIRST; i < END; i++) {
            int n = i - FIRST;
            inventory.setItem(i, n < stacks.size() ? stacks.get(n) : ItemStack.EMPTY);
        }
        inventory.setChanged();
    }
}

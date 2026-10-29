package com.slfrontier.inventory;

import com.mojang.datafixers.util.Pair;
import com.slfrontier.registry.ModMenus;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/**
 * The Frontier inventory: the whole player inventory (armor, off hand, bag and hotbar) without
 * the 2x2 crafting grid. Slot positions are laid out for {@code FrontierInventoryScreen}.
 */
public class FrontierInventoryMenu extends AbstractContainerMenu {
    // Menu slot ranges.
    public static final int ARMOR_START = 0, OFFHAND = 4, BAG_START = 5, HOTBAR_START = 32, END = 41;
    public static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final ResourceLocation[] ARMOR_ICONS = {InventoryMenu.EMPTY_ARMOR_SLOT_HELMET,
            InventoryMenu.EMPTY_ARMOR_SLOT_CHESTPLATE, InventoryMenu.EMPTY_ARMOR_SLOT_LEGGINGS, InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS};

    // Window layout, relative to the window's top-left corner. Slot coordinates are the item's top-left.
    public static final int WIDTH = 500, COMPACT_WIDTH = 330, HEIGHT = 222;
    public static final int EQUIP_X = 8, EQUIP_W = 120, ARMOR_X = 13, HAND_X = 107, ARMOR_Y = 85, ARMOR_PITCH = 24;
    public static final int MAIN_HAND_Y = ARMOR_Y + ARMOR_PITCH, OFFHAND_Y = ARMOR_Y + 2 * ARMOR_PITCH;
    public static final int BAG_X = 134, BAG_W = 188, GRID_X = 140, GRID_Y = 58, HOTBAR_Y = 143, PITCH = 20;
    public static final int DETAILS_X = 328, DETAILS_W = 164;

    private final Player owner;
    private ItemCategory filter = ItemCategory.ALL;

    public FrontierInventoryMenu(int containerId, Inventory inventory) {
        super(ModMenus.FRONTIER_INVENTORY.get(), containerId);
        this.owner = inventory.player;
        for (int i = 0; i < ARMOR.length; i++) {
            addSlot(new GearSlot(inventory, owner, ARMOR[i], 39 - i, ARMOR_X, ARMOR_Y + i * ARMOR_PITCH, ARMOR_ICONS[i]));
        }
        addSlot(new GearSlot(inventory, owner, EquipmentSlot.OFFHAND, Inventory.SLOT_OFFHAND, HAND_X, OFFHAND_Y,
                InventoryMenu.EMPTY_ARMOR_SLOT_SHIELD));
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new BagSlot(inventory, 9 + row * 9 + col, GRID_X + col * PITCH, GRID_Y + row * PITCH));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, GRID_X + col * PITCH, HOTBAR_Y));
        }
    }

    public ItemCategory filter() {
        return filter;
    }

    /** Client side only: bag slots holding items of another category stop being shown and clickable. */
    public void setFilter(ItemCategory filter) {
        this.filter = filter;
    }

    public static int armorSlot(EquipmentSlot slot) {
        return ARMOR_START + 3 - slot.getIndex();
    }

    @Override
    public boolean stillValid(Player player) {
        return player.isAlive();
    }

    /** Shift-click: equipment goes to the bag, armor and shields to their free slot, bag and hotbar swap. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        EquipmentSlot target = player.getEquipmentSlotForItem(stack);
        if (index < BAG_START) {
            if (!moveItemStackTo(stack, BAG_START, END, false)) return ItemStack.EMPTY;
        } else if (target.getType() == EquipmentSlot.Type.HUMANOID_ARMOR && !slots.get(armorSlot(target)).hasItem()) {
            int armor = armorSlot(target);
            if (!moveItemStackTo(stack, armor, armor + 1, false)) return ItemStack.EMPTY;
        } else if (target == EquipmentSlot.OFFHAND && !slots.get(OFFHAND).hasItem()) {
            if (!moveItemStackTo(stack, OFFHAND, OFFHAND + 1, false)) return ItemStack.EMPTY;
        } else if (index < HOTBAR_START) {
            if (!moveItemStackTo(stack, HOTBAR_START, END, false)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, BAG_START, HOTBAR_START, false)) {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY, original);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return original;
    }

    /** An armor or off-hand slot, with the same rules as the vanilla inventory's. */
    private static final class GearSlot extends Slot {
        private final Player owner;
        private final EquipmentSlot equipment;
        private final ResourceLocation emptyIcon;

        GearSlot(Inventory inventory, Player owner, EquipmentSlot equipment, int index, int x, int y, ResourceLocation emptyIcon) {
            super(inventory, index, x, y);
            this.owner = owner;
            this.equipment = equipment;
            this.emptyIcon = emptyIcon;
        }

        private boolean armor() {
            return equipment.getType() == EquipmentSlot.Type.HUMANOID_ARMOR;
        }

        @Override
        public void setByPlayer(ItemStack stack, ItemStack previous) {
            owner.onEquipItem(equipment, previous, stack);
            super.setByPlayer(stack, previous);
        }

        @Override
        public int getMaxStackSize() {
            return armor() ? 1 : super.getMaxStackSize();
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return !armor() || stack.canEquip(equipment, owner);
        }

        @Override
        public boolean mayPickup(Player player) {
            ItemStack stack = getItem();
            if (armor() && !stack.isEmpty() && !player.isCreative()
                    && EnchantmentHelper.has(stack, EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE)) {
                return false;
            }
            return super.mayPickup(player);
        }

        @Override
        public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
            return Pair.of(InventoryMenu.BLOCK_ATLAS, emptyIcon);
        }
    }

    /** A bag slot; hidden while the bag is filtered to a category its item isn't in. */
    private final class BagSlot extends Slot {
        BagSlot(Inventory inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }

        @Override
        public boolean isActive() {
            return filter == ItemCategory.ALL || !hasItem() || filter.matches(getItem());
        }
    }
}

package com.slfrontier.inventory;

import com.slfrontier.SLFrontier;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ElytraItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.UseAnim;
import org.jetbrains.annotations.Nullable;

/**
 * The bag categories of the Frontier inventory. An item belongs to the category whose
 * {@code slfrontier:category/<id>} item tag lists it (key items win); items no tag mentions
 * fall back to a guess from their type, and everything else counts as a material.
 */
public enum ItemCategory {
    ALL("all"),
    WEAPONS("weapons"),
    ARMOR("armor"),
    CONSUMABLES("consumables"),
    MATERIALS("materials"),
    KEY_ITEMS("key_items");

    private static final ItemCategory[] TAG_ORDER = {KEY_ITEMS, WEAPONS, ARMOR, CONSUMABLES, MATERIALS};

    public final String id;
    @Nullable
    public final TagKey<Item> tag;

    ItemCategory(String id) {
        this.id = id;
        this.tag = id.equals("all") ? null : TagKey.create(Registries.ITEM, SLFrontier.id("category/" + id));
    }

    /** Plural name, used for the bag tabs ("Weapons"). */
    public String nameKey() {
        return "gui.slfrontier.category." + id;
    }

    /** Singular name, used for the item details ("Weapon"). */
    public String singularKey() {
        return nameKey() + ".one";
    }

    public boolean matches(ItemStack stack) {
        return this == ALL || of(stack) == this;
    }

    /** The category an item is filed under; never {@link #ALL}. */
    public static ItemCategory of(ItemStack stack) {
        for (ItemCategory c : TAG_ORDER) {
            if (stack.is(c.tag)) return c;
        }
        Item item = stack.getItem();
        if (item instanceof SwordItem || item instanceof AxeItem || item instanceof TridentItem || item instanceof MaceItem
                || item instanceof ProjectileWeaponItem) {
            return WEAPONS;
        }
        if (item instanceof ArmorItem || item instanceof ElytraItem || item instanceof ShieldItem) return ARMOR;
        if (stack.has(DataComponents.FOOD) || item instanceof PotionItem || stack.getUseAnimation() == UseAnim.DRINK) {
            return CONSUMABLES;
        }
        return MATERIALS;
    }
}

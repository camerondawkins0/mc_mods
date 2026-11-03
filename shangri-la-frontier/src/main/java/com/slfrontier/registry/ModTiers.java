package com.slfrontier.registry;

import com.slfrontier.SLFrontier;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;

/**
 * Weapon materials. Damage values here are the material bonus; each item adds its own base.
 * Repair items are the item tags {@code slfrontier:tool_materials/<name>}.
 */
public final class ModTiers {
    public static final ToolMaterial MERCENARY = new ToolMaterial(BlockTags.INCORRECT_FOR_STONE_TOOL, 220, 4f, 1f, 10, repairs("mercenary"));
    public static final ToolMaterial MARSH = new ToolMaterial(BlockTags.INCORRECT_FOR_IRON_TOOL, 400, 6f, 2f, 12, repairs("marsh"));
    public static final ToolMaterial VORPAL = new ToolMaterial(BlockTags.INCORRECT_FOR_IRON_TOOL, 600, 6f, 2f, 18, repairs("vorpal"));
    public static final ToolMaterial BEE = new ToolMaterial(BlockTags.INCORRECT_FOR_DIAMOND_TOOL, 900, 7f, 3f, 14, repairs("bee"));
    public static final ToolMaterial MOON = new ToolMaterial(BlockTags.INCORRECT_FOR_NETHERITE_TOOL, 1800, 8f, 4f, 20, repairs("moon"));
    public static final ToolMaterial TOMBGUARD = new ToolMaterial(BlockTags.INCORRECT_FOR_NETHERITE_TOOL, 2200, 8f, 5f, 15, repairs("tombguard"));

    private ModTiers() {
    }

    private static TagKey<Item> repairs(String name) {
        return TagKey.create(Registries.ITEM, SLFrontier.id("tool_materials/" + name));
    }
}

package com.slfrontier.registry;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.SimpleTier;

/** Weapon tiers. Damage values here are the tier bonus; each item adds its own base. */
public final class ModTiers {
    public static final Tier MERCENARY = new SimpleTier(BlockTags.INCORRECT_FOR_STONE_TOOL, 220, 4f, 1f, 10,
            () -> Ingredient.of(Items.IRON_INGOT));
    public static final Tier MARSH = new SimpleTier(BlockTags.INCORRECT_FOR_IRON_TOOL, 400, 6f, 2f, 12,
            () -> Ingredient.of(ModItems.GRAY_ORE_CHUNK.get()));
    public static final Tier VORPAL = new SimpleTier(BlockTags.INCORRECT_FOR_IRON_TOOL, 150, 6f, 2f, 18,
            () -> Ingredient.of(Items.RABBIT_FOOT));
    public static final Tier BEE = new SimpleTier(BlockTags.INCORRECT_FOR_DIAMOND_TOOL, 900, 7f, 3f, 14,
            () -> Ingredient.of(ModItems.EMPIRE_BEE_STINGER.get()));
    public static final Tier MOON = new SimpleTier(BlockTags.INCORRECT_FOR_NETHERITE_TOOL, 1800, 8f, 4f, 20,
            () -> Ingredient.of(ModItems.QUAD_BEETLE_SHELL.get()));
    public static final Tier TOMBGUARD = new SimpleTier(BlockTags.INCORRECT_FOR_NETHERITE_TOOL, 2200, 8f, 5f, 15,
            () -> Ingredient.of(ModItems.TOMBGUARD_FRAGMENT.get()));

    private ModTiers() {
    }
}

package com.slfrontier.registry;

import com.slfrontier.SLFrontier;
import com.slfrontier.block.CraftingStationBlock;
import com.slfrontier.crafting.Station;
import com.slfrontier.item.FrontierConsumableItem;
import com.slfrontier.item.TooltipItem;
import com.slfrontier.item.WhetstoneItem;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Blocks and items of the Frontier crafting system: the three stations, materials and consumables. */
public final class CraftingRegistry {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(SLFrontier.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SLFrontier.MODID);

    // ---- Stations -----------------------------------------------------------------
    public static final DeferredBlock<CraftingStationBlock> FRONTIER_FORGE = BLOCKS.register("frontier_forge",
            () -> new CraftingStationBlock(Station.FORGE, BlockBehaviour.Properties.ofFullCopy(Blocks.STONE_BRICKS)
                    .mapColor(MapColor.COLOR_GRAY).strength(3.5f).lightLevel(s -> 12).requiresCorrectToolForDrops()));
    public static final DeferredBlock<CraftingStationBlock> ALCHEMY_TABLE = BLOCKS.register("alchemy_table",
            () -> new CraftingStationBlock(Station.ALCHEMY, BlockBehaviour.Properties.ofFullCopy(Blocks.CRAFTING_TABLE)
                    .mapColor(MapColor.WOOD).strength(2.5f).sound(SoundType.WOOD).lightLevel(s -> 6).noOcclusion()));
    public static final DeferredBlock<CraftingStationBlock> FRONTIER_WORKBENCH = BLOCKS.register("frontier_workbench",
            () -> new CraftingStationBlock(Station.WORKBENCH, BlockBehaviour.Properties.ofFullCopy(Blocks.CRAFTING_TABLE)));

    public static final DeferredItem<BlockItem> FRONTIER_FORGE_ITEM = ITEMS.registerSimpleBlockItem(FRONTIER_FORGE);
    public static final DeferredItem<BlockItem> ALCHEMY_TABLE_ITEM = ITEMS.registerSimpleBlockItem(ALCHEMY_TABLE);
    public static final DeferredItem<BlockItem> FRONTIER_WORKBENCH_ITEM = ITEMS.registerSimpleBlockItem(FRONTIER_WORKBENCH);

    // ---- Materials ----------------------------------------------------------------
    /** Drops from grass in the Frontier and from Vorpal Bunnies; the base of most remedies. */
    public static final DeferredItem<Item> FRONTIER_HERB = material("frontier_herb");
    public static final DeferredItem<Item> GRAY_STEEL_INGOT = material("gray_steel_ingot");
    public static final DeferredItem<Item> LEATHER_GRIP = material("leather_grip");
    public static final DeferredItem<Item> WHETSTONE = ITEMS.register("whetstone",
            () -> new WhetstoneItem(new Item.Properties().stacksTo(16)));

    // ---- Consumables --------------------------------------------------------------
    public static final DeferredItem<Item> HEALING_POTION = consumable("healing_potion", 16, Rarity.COMMON,
            FrontierConsumableItem.drink().heal(8));
    public static final DeferredItem<Item> GREATER_HEALING_POTION = consumable("greater_healing_potion", 16, Rarity.UNCOMMON,
            FrontierConsumableItem.drink().heal(16).effect(MobEffects.REGENERATION, 20 * 8, 0));
    public static final DeferredItem<Item> STAMINA_DRAUGHT = consumable("stamina_draught", 16, Rarity.COMMON,
            FrontierConsumableItem.drink().fullStamina());
    public static final DeferredItem<Item> VIGOR_ELIXIR = consumable("vigor_elixir", 16, Rarity.UNCOMMON,
            FrontierConsumableItem.drink().fullStamina().effect(MobEffects.MOVEMENT_SPEED, 20 * 60, 0));
    public static final DeferredItem<Item> ANTIDOTE = consumable("antidote", 16, Rarity.COMMON,
            FrontierConsumableItem.drink().cures(MobEffects.POISON).cures(MobEffects.HUNGER));
    public static final DeferredItem<Item> NIGHTSIGHT_TONIC = consumable("nightsight_tonic", 16, Rarity.COMMON,
            FrontierConsumableItem.drink().effect(MobEffects.NIGHT_VISION, 20 * 240, 0));
    public static final DeferredItem<Item> IRONHIDE_TONIC = consumable("ironhide_tonic", 16, Rarity.UNCOMMON,
            FrontierConsumableItem.drink().effect(MobEffects.DAMAGE_RESISTANCE, 20 * 90, 0));
    public static final DeferredItem<Item> BANDAGE = consumable("bandage", 32, Rarity.COMMON,
            FrontierConsumableItem.apply(20).effect(MobEffects.REGENERATION, 20 * 6, 1).sound(SoundEvents.ARMOR_EQUIP_LEATHER.value()));
    public static final DeferredItem<Item> TRAIL_RATION = ITEMS.register("trail_ration",
            () -> new FrontierConsumableItem(new Item.Properties().stacksTo(16)
                    .food(new FoodProperties.Builder().nutrition(7).saturationModifier(0.7f).build()),
                    FrontierConsumableItem.apply(32).stamina(30)));

    private CraftingRegistry() {
    }

    private static DeferredItem<Item> material(String name) {
        return ITEMS.register(name, () -> new TooltipItem(new Item.Properties()));
    }

    private static DeferredItem<Item> consumable(String name, int stack, Rarity rarity, FrontierConsumableItem.Spec spec) {
        return ITEMS.register(name, () -> new FrontierConsumableItem(new Item.Properties().stacksTo(stack).rarity(rarity), spec));
    }

    /** Lists every crafting item in the mod's creative tab, just before the spawn eggs. */
    public static void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (!event.getTabKey().equals(ModCreativeTabs.MAIN.getKey())) return;
        ItemStack anchor = ModItems.SPAWN_EGGS.isEmpty() ? ItemStack.EMPTY : new ItemStack(ModItems.SPAWN_EGGS.get(0).get());
        boolean hasAnchor = !anchor.isEmpty() && event.getParentEntries().contains(anchor);
        for (var entry : ITEMS.getEntries()) {
            ItemStack stack = new ItemStack(entry.get());
            if (hasAnchor) event.insertBefore(anchor, stack, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
            else event.accept(stack);
        }
    }
}

package com.slfrontier.registry;

import com.slfrontier.SLFrontier;
import com.slfrontier.item.FrontierCompassItem;
import com.slfrontier.item.FullDiveHeadsetItem;
import com.slfrontier.item.HolyWaterItem;
import com.slfrontier.item.KatanaItem;
import com.slfrontier.item.MoonbladeItem;
import com.slfrontier.item.ReallocationTonicItem;
import com.slfrontier.item.SkillBookItem;
import com.slfrontier.item.TooltipItem;
import com.slfrontier.item.TwinBladeItem;
import com.slfrontier.item.TwinMoonItem;
import com.slfrontier.item.VorpalBellItem;
import com.slfrontier.skill.Skill;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.equipment.ArmorType;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SLFrontier.MODID);
    /** Spawn eggs, kept separately so the creative tab can list them last. */
    public static final List<DeferredItem<? extends Item>> SPAWN_EGGS = new ArrayList<>();

    // ---- Key items ----------------------------------------------------------------
    public static final DeferredItem<Item> FULLDIVE_HEADSET = ITEMS.registerItem("fulldive_headset",
            FullDiveHeadsetItem::new, p -> p.stacksTo(1).rarity(Rarity.RARE));
    public static final DeferredItem<Item> FRONTIER_COMPASS = ITEMS.registerItem("frontier_compass",
            FrontierCompassItem::new, p -> p.stacksTo(1));
    public static final DeferredItem<Item> HOLY_WATER = ITEMS.registerItem("holy_water",
            HolyWaterItem::new, p -> p.stacksTo(16));
    public static final DeferredItem<Item> REBIRTH_TEARJEWEL = ITEMS.registerItem("rebirth_tearjewel",
            p -> new TooltipItem(p, true), p -> p.stacksTo(4).rarity(Rarity.EPIC));
    public static final DeferredItem<Item> VORPAL_SOUL_COLLAR = ITEMS.registerItem("vorpal_soul_collar",
            TooltipItem::new, p -> p.stacksTo(1).rarity(Rarity.RARE));
    public static final DeferredItem<Item> SEITEN_SKILLBOOK = ITEMS.registerItem("seiten_skillbook",
            p -> new SkillBookItem(Skill.SKY_CLEAR, p), p -> p.stacksTo(1).rarity(Rarity.EPIC));
    public static final DeferredItem<Item> VORPAL_BELL = ITEMS.registerItem("vorpal_bell",
            VorpalBellItem::new, p -> p.stacksTo(1).rarity(Rarity.UNCOMMON));
    public static final DeferredItem<Item> REALLOCATION_TONIC = ITEMS.registerItem("reallocation_tonic",
            ReallocationTonicItem::new, p -> p.stacksTo(16));

    // ---- Materials ----------------------------------------------------------------
    public static final DeferredItem<Item> GRAY_ORE_CHUNK = material("gray_ore_chunk");
    public static final DeferredItem<Item> QUAD_BEETLE_SHELL = material("quad_beetle_shell");
    public static final DeferredItem<Item> EMPIRE_BEE_STINGER = material("empire_bee_stinger");
    public static final DeferredItem<Item> EMPIRE_BEE_QUEEN_CROWN = material("empire_bee_queen_crown");
    public static final DeferredItem<Item> MUD_DIGGER_FIN = material("mud_digger_fin");
    public static final DeferredItem<Item> PRISMATIC_SHARD = material("prismatic_shard");
    public static final DeferredItem<Item> NIGHTSLAYER_FUR = material("nightslayer_fur");
    public static final DeferredItem<Item> LYCAGON_FANG = ITEMS.registerItem("lycagon_fang",
            p -> new TooltipItem(p, true), p -> p.rarity(Rarity.EPIC));
    public static final DeferredItem<Item> TOMBGUARD_FRAGMENT = material("tombguard_fragment");
    public static final DeferredItem<Item> GOBLIN_TOOTH = material("goblin_tooth");

    // ---- Weapons ------------------------------------------------------------------
    public static final DeferredItem<Item> MERCENARY_TWIN_BLADES = ITEMS.registerItem("mercenary_twin_blades",
            p -> new TwinBladeItem(ModTiers.MERCENARY, 2, -2.0f, 0f, "lore.slfrontier.mercenary_twin_blades", p));
    public static final DeferredItem<Item> MARSH_DAGGER = ITEMS.registerItem("marsh_dagger",
            p -> new TwinBladeItem(ModTiers.MARSH, 2, -1.8f, 0.03f, "lore.slfrontier.marsh_dagger", p));
    public static final DeferredItem<Item> DUCUSRAM = ITEMS.registerItem("ducusram",
            p -> new TwinBladeItem(ModTiers.MARSH, 4, -2.0f, 0.05f, "lore.slfrontier.ducusram", p), p -> p.rarity(Rarity.UNCOMMON));
    public static final DeferredItem<Item> VORPAL_CHOPPERS = ITEMS.registerItem("vorpal_choppers",
            p -> new TwinBladeItem(ModTiers.VORPAL, 3, -1.6f, 0.12f, "lore.slfrontier.vorpal_choppers", p), p -> p.rarity(Rarity.UNCOMMON));
    public static final DeferredItem<Item> EMPIRE_BEE_TWINBLADES = ITEMS.registerItem("empire_bee_twinblades",
            p -> new TwinBladeItem(ModTiers.BEE, 4, -1.6f, 0.06f, "lore.slfrontier.empire_bee_twinblades", p), p -> p.rarity(Rarity.RARE));
    public static final DeferredItem<Item> MOONBLADE_WAXING = ITEMS.registerItem("moonblade_waxing",
            p -> new MoonbladeItem(ModTiers.MOON, 4, -1.8f, true, p), p -> p.rarity(Rarity.EPIC));
    public static final DeferredItem<Item> MOONBLADE_WANING = ITEMS.registerItem("moonblade_waning",
            p -> new MoonbladeItem(ModTiers.MOON, 4, -1.8f, false, p), p -> p.rarity(Rarity.EPIC));
    public static final DeferredItem<Item> TWIN_MOON = ITEMS.registerItem("twin_moon",
            p -> new TwinMoonItem(ModTiers.MOON, 10, -2.9f, p), p -> p.stacksTo(1).rarity(Rarity.EPIC));
    public static final DeferredItem<Item> TOMBGUARD_KATANA = ITEMS.registerItem("tombguard_katana",
            p -> new KatanaItem(ModTiers.TOMBGUARD, 6, -2.3f, p), p -> p.rarity(Rarity.EPIC));

    // ---- Armor --------------------------------------------------------------------
    public static final DeferredItem<Item> BIRD_MASK = ITEMS.registerItem("bird_mask",
            Item::new, p -> p.humanoidArmor(ModArmorMaterials.BIRD_MASK, ArmorType.HELMET));
    public static final DeferredItem<Item> BATTLEHORN_HELM = ITEMS.registerItem("battlehorn_helm",
            Item::new, p -> p.humanoidArmor(ModArmorMaterials.BATTLEHORN, ArmorType.HELMET).rarity(Rarity.UNCOMMON));

    // ---- Block items --------------------------------------------------------------
    public static final DeferredItem<?> GRAY_ORE = ITEMS.registerSimpleBlockItem(ModBlocks.GRAY_ORE);
    public static final DeferredItem<?> RED_SPIDER_LILY = ITEMS.registerSimpleBlockItem(ModBlocks.RED_SPIDER_LILY);
    public static final DeferredItem<?> PRISMATIC_CRYSTAL = ITEMS.registerSimpleBlockItem(ModBlocks.PRISMATIC_CRYSTAL);
    public static final DeferredItem<?> PRISMATIC_CLUSTER = ITEMS.registerSimpleBlockItem(ModBlocks.PRISMATIC_CLUSTER);
    public static final DeferredItem<?> NIGHTSLAYER_ALTAR = ITEMS.registerSimpleBlockItem(ModBlocks.NIGHTSLAYER_ALTAR);
    public static final DeferredItem<?> SHADOW_ALTAR = ITEMS.registerSimpleBlockItem(ModBlocks.SHADOW_ALTAR);
    public static final DeferredItem<?> WAYPOINT_CRYSTAL = ITEMS.registerSimpleBlockItem(ModBlocks.WAYPOINT_CRYSTAL);

    // ---- Spawn eggs ---------------------------------------------------------------
    static {
        egg("goblin", ModEntities.GOBLIN, 0x4F7A2A, 0x2E1F12);
        egg("vorpal_bunny", ModEntities.VORPAL_BUNNY, 0xF2EEE6, 0xC0283A);
        egg("empire_bee", ModEntities.EMPIRE_BEE, 0xE8B530, 0x2B1D0E);
        egg("quad_beetle", ModEntities.QUAD_BEETLE, 0x2F4A6B, 0x9FC3E8);
        egg("shadow_wolf", ModEntities.SHADOW_WOLF, 0x15121C, 0x6E2FA8);
        egg("mud_digger", ModEntities.MUD_DIGGER, 0x5B4632, 0x8A9BA8);
        egg("empire_bee_queen", ModEntities.EMPIRE_BEE_QUEEN, 0xF2C230, 0x8E1C1C);
        egg("lycagon", ModEntities.LYCAGON, 0x0B0910, 0xB01E2E);
        egg("wezaemon", ModEntities.WEZAEMON, 0x3A3F4A, 0xC9B07A);
        egg("frontier_guide", ModEntities.GUIDE, 0x3B6EA8, 0xF0D7A8);
        egg("frontier_blacksmith", ModEntities.BLACKSMITH, 0x6B4A2E, 0xD08A3A);
        egg("emul", ModEntities.EMUL, 0xF5F1F8, 0x6F5BD1);
        egg("vysache", ModEntities.VYSACHE, 0xEDE7DE, 0x7A1E2C);
        egg("bilac", ModEntities.BILAC, 0xF7EFE7, 0xD0692A);
        egg("setsuna", ModEntities.SETSUNA, 0xE9E4F5, 0xC23046);
        egg("redcap_goblin", ModEntities.REDCAP_GOBLIN, 0x5A5A2E, 0xA01C26);
        egg("ravenous_snake", ModEntities.RAVENOUS_SNAKE, 0x3E7A4A, 0xF2F2EE);
        egg("woodmage", ModEntities.WOODMAGE, 0x3A2618, 0xE8E6E0);
        egg("kirin", ModEntities.KIRIN, 0xE8ECF0, 0x4AE0F0);
        egg("lycagon_shadow", ModEntities.LYCAGON_SHADOW, 0x0A0814, 0xB070FF);
        egg("pencilgon", ModEntities.PENCILGON, 0x7A3ABF, 0xE8C24A);
        egg("oikatzo", ModEntities.OIKATZO, 0xF2D04A, 0xC8302A);
    }

    private ModItems() {
    }

    private static DeferredItem<Item> material(String name) {
        return ITEMS.registerItem(name, TooltipItem::new);
    }

    /** The two colours are read by tools/gen_assets.py to tint the egg's item model. */
    private static <T extends Mob> void egg(String name, Supplier<EntityType<T>> type, int bg, int fg) {
        SPAWN_EGGS.add(ITEMS.registerItem(name + "_spawn_egg", SpawnEggItem::new, p -> p.spawnEgg(type.get())));
    }
}

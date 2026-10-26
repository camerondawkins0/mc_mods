"""Generates every JSON resource and structure template for the mod.

  python3 tools/gen_data.py

Writes into src/main/resources: lang, item/block models, blockstates, loot tables,
recipes, tags, the Frontier dimension (noise settings, biomes, features) and the
structures (jigsaw definitions + .nbt templates).
"""
import copy
import json
import os
import re
import shutil
import sys

sys.path.insert(0, os.path.dirname(__file__))
import lang  # noqa: E402
import structures  # noqa: E402

ROOT = os.path.join(os.path.dirname(__file__), "..")
RES = os.path.join(ROOT, "src/main/resources")
ASSETS = os.path.join(RES, "assets/slfrontier")
DATA = os.path.join(RES, "data/slfrontier")
NS = "slfrontier"


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


def ns(x):
    return f"{NS}:{x}"


# ---------------------------------------------------------------------------------------
# Lang
# ---------------------------------------------------------------------------------------

def gen_lang():
    entries = lang.build()
    # Escape stray percent signs so Minecraft's formatter leaves them alone.
    def escape(text):
        return re.sub(r"%%|%s|%", lambda m: m.group(0) if m.group(0) in ("%%", "%s") else "%%", text)

    fixed = {k: escape(v) for k, v in entries.items()}
    write(os.path.join(ASSETS, "lang/en_us.json"), dict(sorted(fixed.items())))


# ---------------------------------------------------------------------------------------
# Models & blockstates
# ---------------------------------------------------------------------------------------

HANDHELD = ["mercenary_twin_blades", "marsh_dagger", "ducusram", "vorpal_choppers", "empire_bee_twinblades",
            "moonblade_waxing", "moonblade_waning", "tombguard_katana"]
GENERATED = ["fulldive_headset", "holy_water", "rebirth_tearjewel", "vorpal_soul_collar", "seiten_skillbook",
             "vorpal_bell", "reallocation_tonic", "gray_ore_chunk", "quad_beetle_shell", "empire_bee_stinger",
             "empire_bee_queen_crown", "mud_digger_fin", "prismatic_shard", "nightslayer_fur", "lycagon_fang",
             "tombguard_fragment", "goblin_tooth", "bird_mask", "battlehorn_helm"]
EGGS = ["goblin", "vorpal_bunny", "empire_bee", "quad_beetle", "shadow_wolf", "mud_digger", "empire_bee_queen",
        "lycagon", "wezaemon", "frontier_guide", "frontier_blacksmith", "emul", "vysache", "bilac", "setsuna"]


def gen_models():
    m = os.path.join(ASSETS, "models")
    for name in HANDHELD:
        write(f"{m}/item/{name}.json", {"parent": "minecraft:item/handheld", "textures": {"layer0": ns(f"item/{name}")}})
    for name in GENERATED:
        write(f"{m}/item/{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": ns(f"item/{name}")}})
    # The Twin Moon is drawn bigger in the hand.
    write(f"{m}/item/twin_moon.json", {
        "parent": "minecraft:item/handheld",
        "textures": {"layer0": ns("item/twin_moon")},
        "display": {
            "thirdperson_righthand": {"rotation": [0, -90, 55], "translation": [0, 6, 1], "scale": [1.5, 1.5, 0.85]},
            "thirdperson_lefthand": {"rotation": [0, 90, -55], "translation": [0, 6, 1], "scale": [1.5, 1.5, 0.85]},
            "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 4.2, 1.13], "scale": [1.1, 1.1, 1.1]},
            "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 4.2, 1.13], "scale": [1.1, 1.1, 1.1]},
        },
    })
    for name in EGGS:
        write(f"{m}/item/{name}_spawn_egg.json", {"parent": "minecraft:item/template_spawn_egg"})
    # The compass reuses vanilla needle sprites; Java gives it the "angle" property.
    overrides = []
    angles = [0.0] + [0.015625 + 0.03125 * i for i in range(31)] + [0.984375]
    for i, a in enumerate(angles):
        if i == 0:
            model = "minecraft:item/compass"
        elif i == len(angles) - 1:
            model = "minecraft:item/compass"
        else:
            model = f"minecraft:item/compass_{(i + 16) % 32:02d}"
        overrides.append({"predicate": {"angle": round(a, 6)}, "model": model})
    write(f"{m}/item/frontier_compass.json", {"parent": "minecraft:item/generated",
                                              "textures": {"layer0": "minecraft:item/compass_16"},
                                              "overrides": overrides})

    bs = os.path.join(ASSETS, "blockstates")
    write(f"{m}/block/gray_ore.json", {"parent": "minecraft:block/cube_all", "textures": {"all": ns("block/gray_ore")}})
    write(f"{bs}/gray_ore.json", {"variants": {"": {"model": ns("block/gray_ore")}}})
    write(f"{m}/item/gray_ore.json", {"parent": ns("block/gray_ore")})

    write(f"{m}/block/prismatic_crystal.json", {"parent": "minecraft:block/cube_all", "render_type": "minecraft:translucent",
                                                "textures": {"all": ns("block/prismatic_crystal")}})
    write(f"{bs}/prismatic_crystal.json", {"variants": {"": {"model": ns("block/prismatic_crystal")}}})
    write(f"{m}/item/prismatic_crystal.json", {"parent": ns("block/prismatic_crystal")})

    write(f"{m}/block/red_spider_lily.json", {"parent": "minecraft:block/cross", "render_type": "minecraft:cutout",
                                              "textures": {"cross": ns("block/red_spider_lily")}})
    write(f"{bs}/red_spider_lily.json", {"variants": {"": {"model": ns("block/red_spider_lily")}}})
    write(f"{m}/item/red_spider_lily.json", {"parent": "minecraft:item/generated",
                                             "textures": {"layer0": ns("block/red_spider_lily")}})

    write(f"{m}/block/prismatic_cluster.json", {"parent": "minecraft:block/cross", "render_type": "minecraft:cutout",
                                                "textures": {"cross": ns("block/prismatic_cluster")}})
    rot = {"down": {"x": 180}, "east": {"x": 90, "y": 90}, "north": {"x": 90}, "south": {"x": 90, "y": 180},
           "up": {}, "west": {"x": 90, "y": 270}}
    write(f"{bs}/prismatic_cluster.json", {"variants": {
        f"facing={f}": dict({"model": ns("block/prismatic_cluster")}, **r) for f, r in rot.items()}})
    write(f"{m}/item/prismatic_cluster.json", {"parent": "minecraft:item/generated",
                                               "textures": {"layer0": ns("block/prismatic_cluster")}})

    write(f"{m}/block/nightslayer_altar.json", {"parent": "minecraft:block/cube_bottom_top", "textures": {
        "top": ns("block/nightslayer_altar_top"), "bottom": ns("block/nightslayer_altar_side"),
        "side": ns("block/nightslayer_altar_side")}})
    write(f"{bs}/nightslayer_altar.json", {"variants": {"": {"model": ns("block/nightslayer_altar")}}})
    write(f"{m}/item/nightslayer_altar.json", {"parent": ns("block/nightslayer_altar")})


# ---------------------------------------------------------------------------------------
# Loot tables
# ---------------------------------------------------------------------------------------

def item_entry(name, mn=1, mx=1, looting=0, weight=None):
    e = {"type": "minecraft:item", "name": name}
    fns = []
    if (mn, mx) != (1, 1):
        fns.append({"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": float(mn), "max": float(mx)},
                    "add": False})
    if looting:
        fns.append({"function": "minecraft:enchanted_count_increase", "enchantment": "minecraft:looting",
                    "count": {"type": "minecraft:uniform", "min": 0.0, "max": float(looting)}})
    if fns:
        e["functions"] = fns
    if weight:
        e["weight"] = weight
    return e


def pool(entries, rolls=1.0, chance=None, player=False):
    p = {"rolls": rolls, "bonus_rolls": 0.0, "entries": entries}
    cond = []
    if player:
        cond.append({"condition": "minecraft:killed_by_player"})
    if chance is not None:
        cond.append({"condition": "minecraft:random_chance", "chance": chance})
    if cond:
        p["conditions"] = cond
    return p


def entity_loot(name, *pools):
    write(os.path.join(DATA, f"loot_table/entities/{name}.json"), {"type": "minecraft:entity", "pools": list(pools)})


def block_loot(name, entries):
    write(os.path.join(DATA, f"loot_table/blocks/{name}.json"), {
        "type": "minecraft:block",
        "pools": [{"rolls": 1.0, "bonus_rolls": 0.0, "entries": entries,
                   "conditions": [{"condition": "minecraft:survives_explosion"}]}]})


def silk_or(block, drop, mn=1, mx=1):
    return [{"type": "minecraft:alternatives", "children": [
        {"type": "minecraft:item", "name": block, "conditions": [{"condition": "minecraft:match_tool", "predicate": {
            "predicates": {"minecraft:enchantments": [{"enchantments": "minecraft:silk_touch", "levels": {"min": 1}}]}}}]},
        {"type": "minecraft:item", "name": drop, "functions": [
            {"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": float(mn), "max": float(mx)}, "add": False},
            {"function": "minecraft:apply_bonus", "enchantment": "minecraft:fortune", "formula": "minecraft:ore_drops"},
            {"function": "minecraft:explosion_decay"}]}]}]


def gen_loot():
    block_loot("gray_ore", silk_or(ns("gray_ore"), ns("gray_ore_chunk")))
    block_loot("red_spider_lily", [{"type": "minecraft:item", "name": ns("red_spider_lily")}])
    block_loot("prismatic_crystal", silk_or(ns("prismatic_crystal"), ns("prismatic_shard"), 1, 2))
    block_loot("prismatic_cluster", silk_or(ns("prismatic_cluster"), ns("prismatic_shard"), 1, 1))
    block_loot("nightslayer_altar", [{"type": "minecraft:item", "name": ns("nightslayer_altar")}])

    entity_loot("goblin", pool([item_entry(ns("goblin_tooth"), 0, 1, 1)]),
                pool([item_entry("minecraft:bread")], chance=0.2))
    entity_loot("vorpal_bunny", pool([item_entry("minecraft:rabbit_hide", 0, 1, 1)]),
                pool([item_entry("minecraft:rabbit_foot")], chance=0.15, player=True))
    entity_loot("empire_bee", pool([item_entry(ns("empire_bee_stinger"), 0, 1, 1)]),
                pool([item_entry("minecraft:honeycomb")], chance=0.3))
    entity_loot("quad_beetle", pool([item_entry(ns("quad_beetle_shell"), 1, 1, 1)]))
    entity_loot("shadow_wolf", pool([item_entry(ns("nightslayer_fur"), 0, 1, 1)], chance=0.6))
    entity_loot("mud_digger", pool([item_entry(ns("mud_digger_fin"))]),
                pool([item_entry(ns("gray_ore_chunk"), 3, 6)]),
                pool([item_entry(ns("rebirth_tearjewel"))], chance=0.25))
    entity_loot("empire_bee_queen", pool([item_entry(ns("empire_bee_queen_crown"))]),
                pool([item_entry(ns("empire_bee_stinger"), 3, 6)]),
                pool([item_entry("minecraft:honey_bottle", 2, 4)]))
    entity_loot("lycagon", pool([item_entry(ns("nightslayer_fur"), 4, 8)]),
                pool([item_entry(ns("rebirth_tearjewel"), 1, 2)]),
                pool([item_entry("minecraft:nether_star")], chance=0.5))
    entity_loot("wezaemon", pool([item_entry(ns("tombguard_katana"))]),
                pool([item_entry(ns("tombguard_fragment"), 3, 6)]),
                pool([item_entry(ns("rebirth_tearjewel"))]))

    write(os.path.join(DATA, "loot_table/chests/divinity_ruins.json"), {
        "type": "minecraft:chest",
        "pools": [
            {"rolls": {"type": "minecraft:uniform", "min": 4.0, "max": 7.0}, "bonus_rolls": 0.0, "entries": [
                item_entry("minecraft:iron_ingot", 2, 6, weight=20),
                item_entry("minecraft:copper_ingot", 3, 10, weight=20),
                item_entry(ns("gray_ore_chunk"), 1, 4, weight=15),
                item_entry(ns("holy_water"), 1, 3, weight=10),
                item_entry(ns("prismatic_shard"), 1, 3, weight=10),
                item_entry("minecraft:bread", 2, 5, weight=15),
                item_entry("minecraft:redstone", 4, 12, weight=10),
                item_entry("minecraft:diamond", 1, 2, weight=4),
                item_entry("minecraft:golden_apple", 1, 1, weight=4),
                item_entry(ns("reallocation_tonic"), 1, 1, weight=3),
            ]},
            {"rolls": 1.0, "bonus_rolls": 0.0, "conditions": [{"condition": "minecraft:random_chance", "chance": 0.3}],
             "entries": [item_entry(ns("rebirth_tearjewel"))]},
        ]})


# ---------------------------------------------------------------------------------------
# Recipes & tags
# ---------------------------------------------------------------------------------------

def gen_recipes():
    r = os.path.join(DATA, "recipe")
    write(f"{r}/fulldive_headset.json", {
        "type": "minecraft:crafting_shaped", "category": "equipment",
        "pattern": ["III", "GRG", "I I"],
        "key": {"I": {"item": "minecraft:iron_ingot"}, "G": {"item": "minecraft:glass_pane"},
                "R": {"item": "minecraft:redstone_block"}},
        "result": {"id": ns("fulldive_headset"), "count": 1}})
    write(f"{r}/frontier_compass.json", {
        "type": "minecraft:crafting_shapeless", "category": "equipment",
        "ingredients": [{"item": "minecraft:compass"}, {"item": "minecraft:amethyst_shard"}],
        "result": {"id": ns("frontier_compass"), "count": 1}})
    write(f"{r}/holy_water.json", {
        "type": "minecraft:crafting_shapeless", "category": "misc",
        "ingredients": [{"item": "minecraft:glass_bottle"}, {"item": ns("gray_ore_chunk")}, {"item": "minecraft:glowstone_dust"}],
        "result": {"id": ns("holy_water"), "count": 2}})
    write(f"{r}/mercenary_twin_blades.json", {
        "type": "minecraft:crafting_shaped", "category": "equipment",
        "pattern": [" I", "S "],
        "key": {"I": {"item": "minecraft:iron_ingot"}, "S": {"item": "minecraft:stick"}},
        "result": {"id": ns("mercenary_twin_blades"), "count": 1}})
    write(f"{r}/bird_mask.json", {
        "type": "minecraft:crafting_shaped", "category": "equipment",
        "pattern": ["FFF", "FBF"],
        "key": {"F": {"item": "minecraft:feather"}, "B": {"item": "minecraft:blue_dye"}},
        "result": {"id": ns("bird_mask"), "count": 1}})
    write(f"{r}/gray_ore_chunk_from_smelting.json", {
        "type": "minecraft:smelting", "category": "misc", "ingredient": {"item": ns("gray_ore")},
        "result": {"id": ns("gray_ore_chunk")}, "experience": 0.7, "cookingtime": 200})


def tag(path, values, replace=False):
    write(path, {"replace": replace, "values": values})


def gen_tags():
    mc = os.path.join(RES, "data/minecraft/tags")
    tag(f"{mc}/block/mineable/pickaxe.json", [ns("gray_ore"), ns("prismatic_crystal"), ns("prismatic_cluster"), ns("nightslayer_altar")])
    tag(f"{mc}/block/needs_stone_tool.json", [ns("gray_ore")])
    tag(f"{mc}/block/small_flowers.json", [ns("red_spider_lily")])
    tag(f"{mc}/item/small_flowers.json", [ns("red_spider_lily")])
    tag(f"{mc}/entity_type/undead.json", [ns("wezaemon")])
    tag(f"{mc}/entity_type/inverted_healing_and_harm.json", [ns("wezaemon")])
    tag(f"{mc}/item/swords.json", [ns(x) for x in HANDHELD] + [ns("twin_moon")])
    tag(f"{mc}/item/head_armor.json", [ns("bird_mask"), ns("battlehorn_helm")])
    for s in ("wezaemon_tomb", "nightslayer_den", "empire_bee_hive", "mud_digger_lair", "divinity_ruins"):
        tag(os.path.join(DATA, f"tags/worldgen/structure/{s}.json"), [ns(s)])


# ---------------------------------------------------------------------------------------
# The Frontier dimension
# ---------------------------------------------------------------------------------------

LAND_BIOMES = ["fastera_plains", "diremarsh_waste", "prismatic_forest_grotto", "hidden_garden", "nightslayer_woods"]

# Placed features available to every biome, in one global order (avoids feature-order cycles).
STEP_ORDER = {
    2: ["minecraft:amethyst_geode"],
    3: ["minecraft:monster_room"],
    6: ["minecraft:ore_dirt", "minecraft:ore_gravel", "minecraft:ore_granite_upper", "minecraft:ore_diorite_upper",
        "minecraft:ore_andesite_upper", "minecraft:ore_coal_upper", "minecraft:ore_coal_lower", "minecraft:ore_iron_upper",
        "minecraft:ore_iron_middle", "minecraft:ore_iron_small", "minecraft:ore_gold", "minecraft:ore_redstone",
        "minecraft:ore_diamond", "minecraft:ore_lapis", "minecraft:ore_copper", ns("ore_gray"), ns("ore_gray_surface"),
        ns("mud_disk"), "minecraft:disk_sand", "minecraft:disk_clay", "minecraft:disk_gravel"],
    8: ["minecraft:spring_water"],
    9: ["minecraft:glow_lichen", ns("prismatic_pillars"), ns("prismatic_clusters"), "minecraft:trees_plains",
        "minecraft:trees_swamp", "minecraft:trees_cherry", "minecraft:dark_forest_vegetation", "minecraft:trees_birch_and_oak",
        "minecraft:flower_plains", "minecraft:flower_swamp", "minecraft:flower_cherry", ns("spider_lily_patch"),
        "minecraft:patch_grass_plain", "minecraft:patch_grass_normal", "minecraft:patch_grass_forest",
        "minecraft:patch_tall_grass", "minecraft:patch_waterlily", "minecraft:patch_sugar_cane",
        "minecraft:brown_mushroom_normal", "minecraft:patch_dead_bush", "minecraft:patch_berry_common"],
    10: ["minecraft:freeze_top_layer"],
}

COMMON = {"minecraft:amethyst_geode", "minecraft:monster_room", "minecraft:ore_dirt", "minecraft:ore_gravel",
          "minecraft:ore_granite_upper", "minecraft:ore_diorite_upper", "minecraft:ore_andesite_upper",
          "minecraft:ore_coal_upper", "minecraft:ore_coal_lower", "minecraft:ore_iron_upper", "minecraft:ore_iron_middle",
          "minecraft:ore_iron_small", "minecraft:ore_gold", "minecraft:ore_redstone", "minecraft:ore_diamond",
          "minecraft:ore_lapis", "minecraft:ore_copper", "minecraft:spring_water", "minecraft:glow_lichen",
          "minecraft:freeze_top_layer", "minecraft:disk_sand", "minecraft:disk_clay", "minecraft:disk_gravel"}

BIOME_DEFS = {
    "fastera_plains": {
        "temperature": 0.8, "downfall": 0.4, "sky": 0x78A7FF, "fog": 0xC0D8FF, "water": 0x3F76E4, "grass": 0x79C05A,
        "features": {"minecraft:trees_plains", "minecraft:flower_plains", "minecraft:patch_grass_plain",
                     "minecraft:patch_sugar_cane", "minecraft:patch_tall_grass"},
        "monsters": [("goblin", 100, 2, 4), ("vorpal_bunny", 35, 1, 2)],
        "creatures": [("minecraft:sheep", 12, 2, 4), ("minecraft:cow", 8, 2, 3), ("minecraft:pig", 10, 2, 4),
                      ("minecraft:chicken", 10, 2, 4)],
    },
    "diremarsh_waste": {
        "temperature": 0.8, "downfall": 0.9, "sky": 0x7A8A6E, "fog": 0x8E9A7A, "water": 0x4C5E3A, "grass": 0x6A7042,
        "foliage": 0x6A7042, "water_fog": 0x23301A,
        "features": {"minecraft:trees_swamp", "minecraft:flower_swamp", "minecraft:patch_grass_normal",
                     "minecraft:patch_waterlily", "minecraft:brown_mushroom_normal", "minecraft:patch_dead_bush",
                     ns("ore_gray"), ns("ore_gray_surface"), ns("mud_disk")},
        "monsters": [("goblin", 40, 2, 3), ("vorpal_bunny", 20, 1, 2)],
        "creatures": [("minecraft:frog", 10, 2, 4)],
    },
    "prismatic_forest_grotto": {
        "temperature": 0.6, "downfall": 0.8, "sky": 0x9FD8FF, "fog": 0xD8C8FF, "water": 0x7FE7FF, "grass": 0x7FD8B0,
        "foliage": 0x9F7FE0, "particle": ("minecraft:end_rod", 0.0008),
        "features": {"minecraft:trees_birch_and_oak", "minecraft:flower_cherry", "minecraft:patch_grass_forest",
                     ns("prismatic_pillars"), ns("prismatic_clusters")},
        "monsters": [("empire_bee", 60, 2, 3), ("quad_beetle", 25, 1, 1)],
        "creatures": [("minecraft:rabbit", 8, 2, 3)],
    },
    "hidden_garden": {
        "temperature": 0.6, "downfall": 0.6, "sky": 0xF2B8C8, "fog": 0xF8D8E0, "water": 0x5DB7EF, "grass": 0x8FBF5A,
        "foliage": 0xE89AB8, "particle": ("minecraft:cherry_leaves", 0.003),
        "features": {"minecraft:trees_cherry", "minecraft:flower_cherry", "minecraft:patch_grass_plain",
                     ns("spider_lily_patch")},
        "monsters": [("vorpal_bunny", 15, 1, 2)],
        "creatures": [("minecraft:rabbit", 10, 2, 3)],
    },
    "nightslayer_woods": {
        "temperature": 0.5, "downfall": 0.7, "sky": 0x2A2440, "fog": 0x3A3050, "water": 0x2A2A50, "grass": 0x3E5A3A,
        "foliage": 0x2E4A2E, "particle": ("minecraft:ash", 0.006),
        "features": {"minecraft:dark_forest_vegetation", "minecraft:patch_grass_forest", "minecraft:brown_mushroom_normal",
                     "minecraft:patch_berry_common"},
        "monsters": [("shadow_wolf", 60, 2, 3), ("goblin", 30, 2, 3)],
        "creatures": [],
    },
    "frontier_sea": {
        "temperature": 0.5, "downfall": 0.5, "sky": 0x7BA4FF, "fog": 0xC0D8FF, "water": 0x3F76E4, "grass": 0x79C05A,
        "features": {"minecraft:patch_tall_grass"},
        "monsters": [],
        "creatures": [],
        "water_creatures": [("minecraft:squid", 3, 1, 4)],
        "water_ambient": [("minecraft:cod", 10, 3, 6)],
    },
}

# Climate points for the multi-noise biome source.
BIOME_POINTS = {
    "frontier_sea": dict(temperature=0.0, humidity=0.0, continentalness=-0.7, erosion=0.0, weirdness=0.0, offset=0.0),
    "fastera_plains": dict(temperature=0.1, humidity=-0.1, continentalness=0.25, erosion=0.35, weirdness=0.0, offset=0.0),
    "diremarsh_waste": dict(temperature=0.55, humidity=0.6, continentalness=0.1, erosion=0.6, weirdness=0.1, offset=0.0),
    "prismatic_forest_grotto": dict(temperature=-0.4, humidity=0.45, continentalness=0.35, erosion=-0.2, weirdness=0.5, offset=0.0),
    "hidden_garden": dict(temperature=0.35, humidity=-0.5, continentalness=0.45, erosion=0.0, weirdness=-0.55, offset=0.0),
    "nightslayer_woods": dict(temperature=-0.45, humidity=-0.35, continentalness=0.3, erosion=0.1, weirdness=-0.2, offset=0.0),
}


def spawner(entry):
    eid, w, mn, mx = entry
    return {"type": eid if ":" in eid else ns(eid), "weight": w, "minCount": mn, "maxCount": mx}


def gen_biomes():
    for name, b in BIOME_DEFS.items():
        feats = b["features"] | COMMON
        steps = [[] for _ in range(11)]
        for step, order in STEP_ORDER.items():
            steps[step] = [f for f in order if f in feats]
        effects = {
            "sky_color": b["sky"], "fog_color": b["fog"], "water_color": b["water"],
            "water_fog_color": b.get("water_fog", 0x050533),
            "grass_color": b["grass"],
            "mood_sound": {"sound": "minecraft:ambient.cave", "tick_delay": 6000, "block_search_extent": 8, "offset": 2.0},
        }
        if "foliage" in b:
            effects["foliage_color"] = b["foliage"]
        if "particle" in b:
            effects["particle"] = {"options": {"type": b["particle"][0]}, "probability": b["particle"][1]}
        biome = {
            "has_precipitation": True,
            "temperature": b["temperature"],
            "downfall": b["downfall"],
            "effects": effects,
            "spawners": {
                "monster": [spawner(e) for e in b["monsters"]],
                "creature": [spawner(e) for e in b["creatures"]],
                "ambient": [], "axolotls": [], "underground_water_creature": [], "misc": [],
                "water_ambient": [spawner(e) for e in b.get("water_ambient", [])],
                "water_creature": [spawner(e) for e in b.get("water_creatures", [])],
            },
            "spawn_costs": {},
            "carvers": {"air": ["minecraft:cave", "minecraft:cave_extra_underground", "minecraft:canyon"]},
            "features": steps,
        }
        write(os.path.join(DATA, f"worldgen/biome/{name}.json"), biome)


def placed(name, feature, placement):
    write(os.path.join(DATA, f"worldgen/placed_feature/{name}.json"), {"feature": feature, "placement": placement})


def configured(name, cfg):
    write(os.path.join(DATA, f"worldgen/configured_feature/{name}.json"), cfg)


def state(name, props=None):
    s = {"Name": name}
    if props:
        s["Properties"] = props
    return s


def gen_features():
    configured("ore_gray", {"type": "minecraft:ore", "config": {"size": 7, "discard_chance_on_air_exposure": 0.0, "targets": [
        {"target": {"predicate_type": "minecraft:tag_match", "tag": "minecraft:stone_ore_replaceables"}, "state": state(ns("gray_ore"))},
        {"target": {"predicate_type": "minecraft:tag_match", "tag": "minecraft:deepslate_ore_replaceables"}, "state": state(ns("gray_ore"))},
    ]}})
    placed("ore_gray", ns("ore_gray"), [
        {"type": "minecraft:count", "count": 18}, {"type": "minecraft:in_square"},
        {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform", "min_inclusive": {"absolute": 0},
                                                       "max_inclusive": {"absolute": 90}}},
        {"type": "minecraft:biome"}])
    # Gray ore poking out of the mud near the surface so beginners can find it.
    configured("ore_gray_surface", {"type": "minecraft:ore", "config": {"size": 5, "discard_chance_on_air_exposure": 0.0, "targets": [
        {"target": {"predicate_type": "minecraft:block_match", "block": "minecraft:mud"}, "state": state(ns("gray_ore"))},
        {"target": {"predicate_type": "minecraft:block_match", "block": "minecraft:dirt"}, "state": state(ns("gray_ore"))},
        {"target": {"predicate_type": "minecraft:tag_match", "tag": "minecraft:stone_ore_replaceables"}, "state": state(ns("gray_ore"))},
    ]}})
    placed("ore_gray_surface", ns("ore_gray_surface"), [
        {"type": "minecraft:count", "count": 6}, {"type": "minecraft:in_square"},
        {"type": "minecraft:heightmap", "heightmap": "OCEAN_FLOOR_WG"},
        {"type": "minecraft:random_offset", "xz_spread": 0, "y_spread": -2},
        {"type": "minecraft:biome"}])
    configured("mud_disk", {"type": "minecraft:disk", "config": {
        "half_height": 2, "radius": {"type": "minecraft:uniform", "min_inclusive": 3, "max_inclusive": 6},
        "state_provider": {"fallback": {"type": "minecraft:simple_state_provider", "state": state("minecraft:mud")}, "rules": []},
        "target": {"type": "minecraft:matching_blocks", "blocks": ["minecraft:dirt", "minecraft:grass_block", "minecraft:clay"]}}})
    placed("mud_disk", ns("mud_disk"), [
        {"type": "minecraft:count", "count": 3}, {"type": "minecraft:in_square"},
        {"type": "minecraft:heightmap", "heightmap": "OCEAN_FLOOR_WG"}, {"type": "minecraft:biome"}])
    configured("prismatic_pillar", {"type": "minecraft:block_column", "config": {
        "direction": "up", "prioritize_tip": False,
        "allowed_placement": {"type": "minecraft:matching_blocks", "blocks": "minecraft:air"},
        "layers": [
            {"height": {"type": "minecraft:uniform", "min_inclusive": 1, "max_inclusive": 4},
             "provider": {"type": "minecraft:simple_state_provider", "state": state(ns("prismatic_crystal"))}},
            {"height": 1, "provider": {"type": "minecraft:simple_state_provider",
                                       "state": state(ns("prismatic_cluster"), {"facing": "up", "waterlogged": "false"})}},
        ]}})
    placed("prismatic_pillars", ns("prismatic_pillar"), [
        {"type": "minecraft:count", "count": 3}, {"type": "minecraft:in_square"},
        {"type": "minecraft:heightmap", "heightmap": "WORLD_SURFACE_WG"},
        {"type": "minecraft:block_predicate_filter", "predicate": {"type": "minecraft:matching_blocks", "offset": [0, -1, 0],
                                                                   "blocks": ["minecraft:grass_block", "minecraft:moss_block",
                                                                              "minecraft:dirt"]}},
        {"type": "minecraft:biome"}])
    configured("prismatic_clusters", {"type": "minecraft:random_patch", "config": {
        "tries": 24, "xz_spread": 6, "y_spread": 2,
        "feature": {"feature": {"type": "minecraft:simple_block", "config": {"to_place": {
            "type": "minecraft:simple_state_provider", "state": state(ns("prismatic_cluster"), {"facing": "up", "waterlogged": "false"})}}},
            "placement": [{"type": "minecraft:block_predicate_filter", "predicate": {"type": "minecraft:all_of", "predicates": [
                {"type": "minecraft:matching_blocks", "blocks": "minecraft:air"},
                {"type": "minecraft:matching_blocks", "offset": [0, -1, 0],
                 "blocks": ["minecraft:grass_block", "minecraft:moss_block", "minecraft:stone"]}]}}]}}})
    placed("prismatic_clusters", ns("prismatic_clusters"), [
        {"type": "minecraft:count", "count": 2}, {"type": "minecraft:in_square"},
        {"type": "minecraft:heightmap", "heightmap": "MOTION_BLOCKING"}, {"type": "minecraft:biome"}])
    configured("spider_lily_patch", {"type": "minecraft:flower", "config": {
        "tries": 96, "xz_spread": 7, "y_spread": 2,
        "feature": {"feature": {"type": "minecraft:simple_block", "config": {"to_place": {
            "type": "minecraft:simple_state_provider", "state": state(ns("red_spider_lily"))}}},
            "placement": [{"type": "minecraft:block_predicate_filter", "predicate": {
                "type": "minecraft:matching_blocks", "blocks": "minecraft:air"}}]}}})
    placed("spider_lily_patch", ns("spider_lily_patch"), [
        {"type": "minecraft:count", "count": 4}, {"type": "minecraft:in_square"},
        {"type": "minecraft:heightmap", "heightmap": "MOTION_BLOCKING"}, {"type": "minecraft:biome"}])


def gen_dimension():
    write(os.path.join(DATA, "dimension_type/frontier.json"), {
        "ultrawarm": False, "natural": True, "coordinate_scale": 1.0, "has_skylight": True, "has_ceiling": False,
        "ambient_light": 0.0, "piglin_safe": False, "bed_works": True, "respawn_anchor_works": False, "has_raids": False,
        "logical_height": 384, "height": 384, "min_y": -64, "infiniburn": "#minecraft:infiniburn_overworld",
        "effects": "minecraft:overworld", "monster_spawn_block_light_limit": 0,
        "monster_spawn_light_level": {"type": "minecraft:uniform", "min_inclusive": 0, "max_inclusive": 7}})

    # Noise settings: vanilla overworld terrain, with our own surface rules for the custom biomes.
    with open(os.path.join(os.path.dirname(__file__), "vanilla_overworld_noise_settings.json")) as f:
        noise = json.load(f)
    noise = copy.deepcopy(noise)

    def top(biome, block_rule):
        return {"type": "minecraft:condition", "if_true": {"type": "minecraft:biome", "biome_is": [ns(biome)]},
                "then_run": block_rule}

    floor = {"type": "minecraft:stone_depth", "add_surface_depth": False, "offset": 0, "secondary_depth_range": 0,
             "surface_type": "floor"}
    dry = {"type": "minecraft:water", "add_stone_depth": False, "offset": 0, "surface_depth_multiplier": 0}
    noisy = lambda lo, hi: {"type": "minecraft:noise_threshold", "noise": "minecraft:surface", "min_threshold": lo,
                            "max_threshold": hi}
    block = lambda name, props=None: {"type": "minecraft:block", "result_state": state(name, props)}
    custom = {"type": "minecraft:condition", "if_true": {"type": "minecraft:above_preliminary_surface"},
              "then_run": {"type": "minecraft:condition", "if_true": floor,
                           "then_run": {"type": "minecraft:condition", "if_true": dry,
                                        "then_run": {"type": "minecraft:sequence", "sequence": [
                                            top("diremarsh_waste", block("minecraft:mud")),
                                            top("nightslayer_woods", {"type": "minecraft:sequence", "sequence": [
                                                {"type": "minecraft:condition", "if_true": noisy(0.25, 10.0),
                                                 "then_run": block("minecraft:coarse_dirt")},
                                                block("minecraft:podzol", {"snowy": "false"})]}),
                                            top("prismatic_forest_grotto", {"type": "minecraft:condition",
                                                                            "if_true": noisy(0.3, 10.0),
                                                                            "then_run": block("minecraft:moss_block")}),
                                        ]}}}}
    noise["surface_rule"] = {"type": "minecraft:sequence", "sequence": [custom, noise["surface_rule"]]}
    write(os.path.join(DATA, "worldgen/noise_settings/frontier.json"), noise)

    biomes = []
    for name, p in BIOME_POINTS.items():
        biomes.append({"biome": ns(name), "parameters": {
            "temperature": p["temperature"], "humidity": p["humidity"], "continentalness": p["continentalness"],
            "erosion": p["erosion"], "weirdness": p["weirdness"], "depth": 0.0, "offset": p["offset"]}})
    write(os.path.join(DATA, "dimension/frontier.json"), {
        "type": ns("frontier"),
        "generator": {"type": "minecraft:noise", "settings": ns("frontier"),
                      "biome_source": {"type": "minecraft:multi_noise", "biomes": biomes}}})


STRUCTURES = {
    # name: (biomes, step, spacing, separation, salt)
    "wezaemon_tomb": (["hidden_garden"], "surface_structures", 24, 8, 7712301),
    "nightslayer_den": (["nightslayer_woods"], "surface_structures", 20, 6, 7712302),
    "empire_bee_hive": (["prismatic_forest_grotto"], "surface_structures", 18, 6, 7712303),
    "mud_digger_lair": (["diremarsh_waste"], "surface_structures", 18, 6, 7712304),
    "divinity_ruins": (LAND_BIOMES, "surface_structures", 28, 10, 7712305),
}


def gen_structures():
    for name, (biomes, step, spacing, sep, salt) in STRUCTURES.items():
        tag(os.path.join(DATA, f"tags/worldgen/biome/has_structure/{name}.json"), [ns(b) for b in biomes])
        write(os.path.join(DATA, f"worldgen/template_pool/{name}.json"), {
            "fallback": "minecraft:empty",
            "elements": [{"weight": 1, "element": {"element_type": "minecraft:single_pool_element", "location": ns(name),
                                                   "processors": "minecraft:empty", "projection": "rigid"}}]})
        write(os.path.join(DATA, f"worldgen/structure/{name}.json"), {
            "type": "minecraft:jigsaw", "biomes": f"#{NS}:has_structure/{name}", "step": step, "spawn_overrides": {},
            "terrain_adaptation": "beard_thin", "start_pool": ns(name), "size": 1, "start_height": {"absolute": 0},
            "project_start_to_heightmap": "WORLD_SURFACE_WG", "max_distance_from_center": 80, "use_expansion_hack": False})
        write(os.path.join(DATA, f"worldgen/structure_set/{name}.json"), {
            "structures": [{"structure": ns(name), "weight": 1}],
            "placement": {"type": "minecraft:random_spread", "spacing": spacing, "separation": sep, "salt": salt}})
    out = os.path.join(DATA, "structure")
    if os.path.isdir(out):
        shutil.rmtree(out)
    structures.build_all(out)


if __name__ == "__main__":
    gen_lang()
    gen_models()
    gen_loot()
    gen_recipes()
    gen_tags()
    gen_features()
    gen_biomes()
    gen_dimension()
    gen_structures()
    print("data generated")

"""Data for Frontier crafting, called from gen_data.py.

Writes into src/main/resources:
  * data/slfrontier/frontier_recipe/*.json   the Frontier recipes (loaded by crafting/FrontierRecipes.java)
  * the three station blocks: blockstates, block/item models, loot tables, their vanilla recipes
  * item models for the crafting materials and consumables, and the item tags recipes use
  * Frontier Herb drops (global loot modifiers on grass in the Frontier and on Vorpal Bunnies)

Recipe fields: station (forge / alchemy / workbench), category (weapon, armor, consumable,
material, gear), result {item, count}, ingredients [{item | tag, count}], min_level (avatar
level), optional requires_stage (QuestStage name), optional npc (only that NPC smith can make
it), time (ticks for one craft).
"""
import json
import os
import shutil

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
# Recipes
# ---------------------------------------------------------------------------------------

def recipe(rid, station, category, result, ingredients, level=1, time=40, count=1, stage=None, npc=None):
    """ingredients: [(id or #tag, count)]; ids without a namespace are slfrontier items."""
    def full(x):
        return x if ":" in x else ns(x)
    out = {
        "id": ns(rid),
        "station": station,
        "category": category,
        "result": {"item": full(result), "count": count},
        "ingredients": [{"tag": full(i[1:])} if i.startswith("#") else {"item": full(i)} for i, _ in ingredients],
        "min_level": level,
        "time": time,
    }
    for entry, (_, n) in zip(out["ingredients"], ingredients):
        entry["count"] = n
    if stage:
        out["requires_stage"] = stage
    if npc:
        out["npc"] = npc
    return rid, out


RECIPES = [
    # ---- Workbench: camp basics -------------------------------------------------------
    recipe("leather_grip", "workbench", "material", "leather_grip", [("#hides", 2)], time=30),
    recipe("bandage", "workbench", "consumable", "bandage", [("#minecraft:wool", 1), ("frontier_herb", 2)], count=3, time=30),
    recipe("trail_ration", "workbench", "consumable", "trail_ration",
           [("minecraft:bread", 1), ("#cooked_meats", 1), ("frontier_herb", 1)], count=2),
    recipe("goblin_tooth_arrows", "workbench", "gear", "minecraft:arrow",
           [("goblin_tooth", 1), ("minecraft:stick", 1), ("minecraft:feather", 1)], count=4, time=20),
    recipe("frontier_compass", "workbench", "gear", "frontier_compass", [("minecraft:compass", 1), ("#crystal_shards", 1)]),
    recipe("bird_mask", "workbench", "armor", "bird_mask", [("minecraft:feather", 5), ("minecraft:blue_dye", 1)], level=3, time=50),

    # ---- Forge: metalwork (any forge) -------------------------------------------------
    recipe("gray_steel_ingot", "forge", "material", "gray_steel_ingot", [("gray_ore_chunk", 2), ("#minecraft:coals", 1)], level=3),
    recipe("whetstone", "forge", "gear", "whetstone", [("gray_steel_ingot", 1), ("minecraft:flint", 1)], count=2, level=3, time=30),
    recipe("mercenary_twin_blades", "forge", "weapon", "mercenary_twin_blades",
           [("minecraft:iron_ingot", 2), ("leather_grip", 1)], time=50),
    recipe("chainmail_helmet", "forge", "armor", "minecraft:chainmail_helmet", [("gray_steel_ingot", 3), ("leather_grip", 1)],
           level=6, time=60),
    recipe("chainmail_boots", "forge", "armor", "minecraft:chainmail_boots", [("gray_steel_ingot", 2), ("leather_grip", 1)],
           level=6, time=50),
    recipe("chainmail_leggings", "forge", "armor", "minecraft:chainmail_leggings", [("gray_steel_ingot", 4), ("leather_grip", 1)],
           level=8, time=70),
    recipe("chainmail_chestplate", "forge", "armor", "minecraft:chainmail_chestplate", [("gray_steel_ingot", 5), ("leather_grip", 1)],
           level=10, time=80),

    # ---- Forge: the Secondil smith's speciality ----------------------------------------
    recipe("marsh_dagger", "forge", "weapon", "marsh_dagger", [("gray_steel_ingot", 2), ("leather_grip", 1)],
           level=6, time=60, stage="NIGHTSLAYER_ENCOUNTER", npc="frontier_blacksmith"),

    # ---- Forge: Bilac's work in Rabituza ------------------------------------------------
    recipe("ducusram", "forge", "weapon", "ducusram", [("marsh_dagger", 1), ("gray_ore_chunk", 3)], level=8, time=70, npc="bilac"),
    recipe("battlehorn_helm", "forge", "armor", "battlehorn_helm", [("quad_beetle_shell", 2)], level=12, time=70, npc="bilac"),
    recipe("empire_bee_twinblades", "forge", "weapon", "empire_bee_twinblades",
           [("empire_bee_queen_crown", 1), ("empire_bee_stinger", 4)], count=2, level=20, time=90, npc="bilac"),
    recipe("reallocation_tonic", "forge", "consumable", "reallocation_tonic", [("prismatic_shard", 4)], time=50, npc="bilac"),
    recipe("tombguard_katana", "forge", "weapon", "tombguard_katana",
           [("tombguard_fragment", 6), ("gray_steel_ingot", 2), ("leather_grip", 1)],
           level=40, time=120, stage="LYCAGON_SHADOW", npc="bilac"),

    # ---- Alchemy ------------------------------------------------------------------------
    recipe("healing_potion", "alchemy", "consumable", "healing_potion", [("minecraft:glass_bottle", 1), ("frontier_herb", 2)]),
    recipe("holy_water", "alchemy", "consumable", "holy_water",
           [("minecraft:glass_bottle", 1), ("gray_ore_chunk", 1), ("#glowing_reagents", 1)], count=2, time=50),
    recipe("stamina_draught", "alchemy", "consumable", "stamina_draught",
           [("minecraft:glass_bottle", 1), ("frontier_herb", 1), ("minecraft:sugar", 1)], level=3),
    recipe("antidote", "alchemy", "consumable", "antidote",
           [("minecraft:glass_bottle", 1), ("frontier_herb", 1), ("#venoms", 1)], level=5),
    recipe("nightsight_tonic", "alchemy", "consumable", "nightsight_tonic",
           [("minecraft:glass_bottle", 1), ("frontier_herb", 1), ("nightslayer_fur", 1)], level=8, time=50),
    recipe("vigor_elixir", "alchemy", "consumable", "vigor_elixir",
           [("stamina_draught", 1), ("minecraft:honeycomb", 1), ("prismatic_shard", 1)], level=12, time=60),
    recipe("ironhide_tonic", "alchemy", "consumable", "ironhide_tonic",
           [("minecraft:glass_bottle", 2), ("frontier_herb", 1), ("quad_beetle_shell", 1)], count=2, level=14, time=60),
    recipe("greater_healing_potion", "alchemy", "consumable", "greater_healing_potion",
           [("healing_potion", 1), ("prismatic_shard", 1), ("frontier_herb", 2)], level=15, time=60),
    recipe("rebirth_tearjewel", "alchemy", "gear", "rebirth_tearjewel",
           [("prismatic_shard", 4), ("tombguard_fragment", 2), ("nightslayer_fur", 1)], level=35, time=120, stage="LYCAGON_SHADOW"),
]

ITEM_TAGS = {
    "hides": ["minecraft:leather", "minecraft:rabbit_hide"],
    "cooked_meats": ["minecraft:cooked_beef", "minecraft:cooked_porkchop", "minecraft:cooked_mutton", "minecraft:cooked_chicken",
                     "minecraft:cooked_rabbit", "minecraft:cooked_cod", "minecraft:cooked_salmon"],
    "crystal_shards": ["minecraft:amethyst_shard", ns("prismatic_shard")],
    "venoms": [ns("empire_bee_stinger"), "minecraft:spider_eye"],
    "glowing_reagents": ["minecraft:glowstone_dust", ns("prismatic_shard")],
}


def gen_recipes():
    out = os.path.join(DATA, "frontier_recipe")
    if os.path.isdir(out):
        shutil.rmtree(out)
    seen = set()
    for rid, body in RECIPES:
        assert rid not in seen, rid
        seen.add(rid)
        write(os.path.join(out, f"{rid}.json"), body)
    for name, values in ITEM_TAGS.items():
        write(os.path.join(DATA, f"tags/item/{name}.json"), {"replace": False, "values": values})


# ---------------------------------------------------------------------------------------
# Station blocks
# ---------------------------------------------------------------------------------------

STATIONS = ["frontier_forge", "alchemy_table", "frontier_workbench"]
FACING_Y = {"north": 0, "east": 90, "south": 180, "west": 270}
ALL_FACES = ("north", "south", "east", "west", "up", "down")
GLOW = {"block_light": 15, "sky_light": 15}


def faces(tex, cull=True, only=ALL_FACES, uv=None):
    out = {}
    for f in only:
        face = {"texture": tex}
        if cull:
            face["cullface"] = f
        if uv:
            face["uv"] = uv
        out[f] = face
    return out


def box(frm, to, tex, only=ALL_FACES, uv=None, **extra):
    e = {"from": frm, "to": to, "faces": faces(tex, False, only, uv)}
    e.update(extra)
    return e


def gen_station_blocks():
    m = os.path.join(ASSETS, "models")
    bs = os.path.join(ASSETS, "blockstates")

    # Frontier Forge: stone-brick furnace with a glowing mouth (an emissive overlay on the front).
    body = {"from": [0, 0, 0], "to": [16, 16, 16], "faces": {
        "north": {"texture": "#front", "cullface": "north"}, "south": {"texture": "#side", "cullface": "south"},
        "east": {"texture": "#side", "cullface": "east"}, "west": {"texture": "#side", "cullface": "west"},
        "up": {"texture": "#top", "cullface": "up"}, "down": {"texture": "#bottom", "cullface": "down"}}}
    write(f"{m}/block/frontier_forge.json", {
        "parent": "minecraft:block/block", "render_type": "minecraft:cutout",
        "textures": {"front": ns("block/frontier_forge_front"), "side": ns("block/frontier_forge_side"),
                     "top": ns("block/frontier_forge_top"), "bottom": "minecraft:block/smooth_stone",
                     "glow": ns("block/frontier_forge_glow"), "glow_top": ns("block/frontier_forge_glow_top"),
                     "particle": ns("block/frontier_forge_side")},
        "elements": [body,
                     {"from": [0, 0, 0], "to": [16, 16, 16], "shade": False, "neoforge_data": GLOW,
                      "faces": {"north": {"texture": "#glow", "cullface": "north"}, "up": {"texture": "#glow_top", "cullface": "up"}}}]})

    # Alchemy Table: a tabletop on four legs with a lower shelf, glowing flasks and a mortar on top.
    flask = ns("block/alchemy_flask")
    write(f"{m}/block/alchemy_table.json", {
        "parent": "minecraft:block/block", "render_type": "minecraft:cutout",
        "textures": {"top": ns("block/alchemy_table_top"), "side": ns("block/alchemy_table_side"),
                     "wood": "minecraft:block/dark_oak_planks", "flask": flask, "stone": "minecraft:block/polished_andesite",
                     "particle": ns("block/alchemy_table_side")},
        "elements": [
            {"from": [0, 10, 0], "to": [16, 13, 16], "faces": {
                "up": {"texture": "#top"}, "down": {"texture": "#wood"},
                **{f: {"texture": "#side", "uv": [0, 3, 16, 6]} for f in ("north", "south", "east", "west")}}},
            box([1, 0, 1], [3, 10, 3], "#wood"), box([13, 0, 1], [15, 10, 3], "#wood"),
            box([1, 0, 13], [3, 10, 15], "#wood"), box([13, 0, 13], [15, 10, 15], "#wood"),
            {"from": [2, 3, 2], "to": [14, 4, 14], "faces": {
                "up": {"texture": "#wood"}, "down": {"texture": "#wood"},
                **{f: {"texture": "#wood", "uv": [0, 0, 12, 1]} for f in ("north", "south", "east", "west")}}},
            # Round purple flask, tall green vial, small amber bottle (glowing), a stone mortar.
            box([3, 13, 3], [7, 16, 7], "#flask", ("north", "south", "east", "west", "up"), [0, 0, 4, 3],
                shade=False, neoforge_data=GLOW),
            box([4, 13, 10], [6, 16, 12], "#flask", ("north", "south", "east", "west", "up"), [4, 0, 6, 3],
                shade=False, neoforge_data=GLOW),
            box([11, 13, 11], [13, 15, 13], "#flask", ("north", "south", "east", "west", "up"), [8, 0, 10, 2],
                shade=False, neoforge_data=GLOW),
            box([9, 13, 3], [13, 14.5, 7], "#stone", ("north", "south", "east", "west", "up")),
        ]})

    # Frontier Workbench: a sturdy bench with a tool rack on the front.
    write(f"{m}/block/frontier_workbench.json", {
        "parent": "minecraft:block/orientable_with_bottom",
        "textures": {"top": ns("block/frontier_workbench_top"), "front": ns("block/frontier_workbench_front"),
                     "side": ns("block/frontier_workbench_side"), "bottom": "minecraft:block/spruce_planks",
                     "particle": ns("block/frontier_workbench_side")}})

    for name in STATIONS:
        write(f"{bs}/{name}.json", {"variants": {
            f"facing={f}": ({"model": ns(f"block/{name}"), "y": y} if y else {"model": ns(f"block/{name}")})
            for f, y in FACING_Y.items()}})
        write(f"{m}/item/{name}.json", {"parent": ns(f"block/{name}")})
        write(os.path.join(DATA, f"loot_table/blocks/{name}.json"), {
            "type": "minecraft:block",
            "pools": [{"rolls": 1.0, "bonus_rolls": 0.0, "entries": [{"type": "minecraft:item", "name": ns(name)}],
                       "conditions": [{"condition": "minecraft:survives_explosion"}]}]})


def gen_vanilla_recipes():
    """The stations are the only things made at a vanilla crafting table, so players can set up camp."""
    r = os.path.join(DATA, "recipe")
    write(f"{r}/frontier_workbench.json", {
        "type": "minecraft:crafting_shaped", "category": "misc",
        "pattern": [" I ", "PTP", "P P"],
        "key": {"I": {"item": "minecraft:iron_ingot"}, "T": {"item": "minecraft:crafting_table"}, "P": {"tag": "minecraft:planks"}},
        "result": {"id": ns("frontier_workbench"), "count": 1}})
    write(f"{r}/frontier_forge.json", {
        "type": "minecraft:crafting_shaped", "category": "misc",
        "pattern": ["SIS", "SFS", "SSS"],
        "key": {"S": {"tag": "minecraft:stone_crafting_materials"}, "I": {"item": "minecraft:iron_ingot"},
                "F": {"item": "minecraft:furnace"}},
        "result": {"id": ns("frontier_forge"), "count": 1}})
    write(f"{r}/alchemy_table.json", {
        "type": "minecraft:crafting_shaped", "category": "misc",
        "pattern": ["BHB", "PPP", "P P"],
        "key": {"B": {"item": "minecraft:glass_bottle"}, "H": {"item": ns("frontier_herb")}, "P": {"tag": "minecraft:planks"}},
        "result": {"id": ns("alchemy_table"), "count": 1}})


# ---------------------------------------------------------------------------------------
# Items
# ---------------------------------------------------------------------------------------

ITEMS = ["frontier_herb", "gray_steel_ingot", "leather_grip", "whetstone", "healing_potion", "greater_healing_potion",
         "stamina_draught", "vigor_elixir", "antidote", "nightsight_tonic", "ironhide_tonic", "bandage", "trail_ration"]


def gen_item_models():
    for name in ITEMS:
        write(os.path.join(ASSETS, f"models/item/{name}.json"),
              {"parent": "minecraft:item/generated", "textures": {"layer0": ns(f"item/{name}")}})


# ---------------------------------------------------------------------------------------
# Frontier Herb drops
# ---------------------------------------------------------------------------------------

def gen_herb_drops():
    """Grass broken in the Frontier and Vorpal Bunnies can drop Frontier Herbs (NeoForge add_table modifiers)."""
    lt = os.path.join(DATA, "loot_table/gameplay")
    write(f"{lt}/frontier_herb_from_grass.json", {"type": "minecraft:block", "pools": [{
        "rolls": 1.0, "bonus_rolls": 0.0,
        "conditions": [{"condition": "minecraft:random_chance", "chance": 0.15}],
        "entries": [{"type": "minecraft:item", "name": ns("frontier_herb")}]}]})
    write(f"{lt}/frontier_herb_from_bunny.json", {"type": "minecraft:entity", "pools": [{
        "rolls": 1.0, "bonus_rolls": 0.0,
        "conditions": [{"condition": "minecraft:random_chance", "chance": 0.5}],
        "entries": [{"type": "minecraft:item", "name": ns("frontier_herb"), "functions": [
            {"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 1.0, "max": 2.0}, "add": False}]}]}]})

    lm = os.path.join(DATA, "loot_modifiers")
    grass = ["minecraft:blocks/short_grass", "minecraft:blocks/tall_grass", "minecraft:blocks/fern", "minecraft:blocks/large_fern"]
    write(f"{lm}/frontier_herb_from_grass.json", {
        "type": "neoforge:add_table",
        "conditions": [
            {"condition": "minecraft:any_of", "terms": [{"condition": "neoforge:loot_table_id", "loot_table_id": g} for g in grass]},
            {"condition": "minecraft:location_check", "predicate": {"dimension": ns("frontier")}}],
        "table": ns("gameplay/frontier_herb_from_grass")})
    write(f"{lm}/frontier_herb_from_bunny.json", {
        "type": "neoforge:add_table",
        "conditions": [{"condition": "neoforge:loot_table_id", "loot_table_id": ns("entities/vorpal_bunny")}],
        "table": ns("gameplay/frontier_herb_from_bunny")})
    write(os.path.join(RES, "data/neoforge/loot_modifiers/global_loot_modifiers.json"), {
        "replace": False, "entries": [ns("frontier_herb_from_grass"), ns("frontier_herb_from_bunny")]})


def build():
    gen_recipes()
    gen_station_blocks()
    gen_vanilla_recipes()
    gen_item_models()
    gen_herb_drops()

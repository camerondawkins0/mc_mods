"""The Frontier inventory: item category tags and the inventory's GUI icons.

  python3 tools/inventory.py

gen_tags() writes data/slfrontier/tags/item/category/<category>.json (run by gen_data.py) and
gen_icons() draws textures/gui/icon/{bag,sort,category_*}.png (run by gen_assets.py).

Every mod item gets a category: the lists below name the weapons, armor, consumables and key
items, and every other item registered in ModItems.java is a material. Vanilla and other mods'
items join through their usual tags; ItemCategory.java guesses from the item type for anything
still untagged.
"""
import json
import math
import os
import re
import sys

sys.path.insert(0, os.path.dirname(__file__))

ROOT = os.path.join(os.path.dirname(__file__), "..")
DATA = os.path.join(ROOT, "src/main/resources/data/slfrontier")
REGISTRY = os.path.join(ROOT, "src/main/java/com/slfrontier/registry")
NS = "slfrontier"

WEAPONS = ["mercenary_twin_blades", "marsh_dagger", "ducusram", "vorpal_choppers", "empire_bee_twinblades",
           "moonblade_waxing", "moonblade_waning", "twin_moon", "tombguard_katana"]
ARMOR = ["bird_mask", "battlehorn_helm"]
CONSUMABLES = ["holy_water", "reallocation_tonic", "rebirth_tearjewel"]
# Story items: shown with a "Key item" badge.
KEY_ITEMS = ["fulldive_headset", "frontier_compass", "vorpal_soul_collar", "vorpal_bell", "seiten_skillbook", "lycagon_fang"]


def optional(tag):
    return {"id": tag, "required": False}


# Vanilla items and the common (c:) tags other mods fill.
EXTRA = {
    "weapons": ["#minecraft:swords", "#minecraft:axes", "minecraft:trident", "minecraft:mace", "minecraft:bow",
                "minecraft:crossbow", optional("#c:tools/melee_weapon"), optional("#c:tools/ranged_weapon")],
    "armor": ["#minecraft:head_armor", "#minecraft:chest_armor", "#minecraft:leg_armor", "#minecraft:foot_armor",
              "minecraft:shield", "minecraft:elytra", optional("#c:armors"), optional("#c:tools/shield")],
    "consumables": ["minecraft:potion", "minecraft:splash_potion", "minecraft:lingering_potion", "minecraft:milk_bucket",
                    "minecraft:honey_bottle", "minecraft:ominous_bottle", optional("#c:foods"), optional("#c:potions"),
                    optional("#c:drinks")],
    "materials": [],
    "key_items": [],
}


def mod_items():
    """Every item id registered in ModItems.java, in registration order."""
    with open(os.path.join(REGISTRY, "ModItems.java")) as f:
        items_src = f.read()
    with open(os.path.join(REGISTRY, "ModBlocks.java")) as f:
        blocks = dict(re.findall(r'(\w+)\s*=\s*BLOCKS\.\w+\(\s*"(\w+)"', f.read()))
    names = []
    for m in re.finditer(r'ITEMS\.register\(\s*"(\w+)"|\bmaterial\(\s*"(\w+)"\s*\)|\begg\(\s*"(\w+)"'
                         r'|registerSimpleBlockItem\(\s*ModBlocks\.(\w+)\s*\)', items_src):
        item, material, egg, block = m.groups()
        if egg:
            names.append(f"{egg}_spawn_egg")
        elif block:
            names.append(blocks.get(block, block.lower()))
        else:
            names.append(item or material)
    return list(dict.fromkeys(names))


def gen_tags():
    items = mod_items()
    named = {"weapons": WEAPONS, "armor": ARMOR, "consumables": CONSUMABLES, "key_items": KEY_ITEMS}
    for cat, ids in named.items():
        for i in ids:
            if i not in items:
                print(f"inventory: {i} ({cat}) is not registered in ModItems.java")
    listed = {i for ids in named.values() for i in ids}
    named["materials"] = [i for i in items if i not in listed]
    for cat in ("weapons", "armor", "consumables", "materials", "key_items"):
        values = [f"{NS}:{i}" for i in named[cat]] + EXTRA[cat]
        path = os.path.join(DATA, f"tags/item/category/{cat}.json")
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w") as f:
            json.dump({"replace": False, "values": values}, f, indent=2, ensure_ascii=False)
            f.write("\n")


# ---------------------------------------------------------------------------------------
# Icons (16x16, same style as tools/icons.py)
# ---------------------------------------------------------------------------------------

def gen_icons():
    from icons import Glyph, save

    g = Glyph(16)  # bag: the Items tab
    g.circle(8, 10.5, 5.3)
    g.poly([(4.5, 5.5), (11.5, 5.5), (13, 9), (3, 9)])
    g.poly([(5.5, 2.5), (10.5, 2.5), (9.5, 5), (6.5, 5)])
    g.line([(4.5, 5.6), (11.5, 5.6)], 0.9, erase=True)
    g.circle(8, 10.5, 1.3, erase=True)
    save(g.render("#FFF6E0", "#F2C27A", "#C98A3A"), "textures/gui/icon/bag.png")

    g = Glyph(16)  # sort: arrows up and down
    g.line([(5, 14), (5, 4)], 1.8)
    g.poly([(5, 1), (8.5, 5), (1.5, 5)])
    g.line([(11, 2), (11, 12)], 1.8)
    g.poly([(11, 15), (14.5, 11), (7.5, 11)])
    save(g.render("#FFFFFF", "#7FE7FF", "#2A9FD8"), "textures/gui/icon/sort.png")

    g = Glyph(16)  # all: a grid of four cells
    for (x, y) in ((2, 2), (9, 2), (2, 9), (9, 9)):
        g.poly([(x, y), (x + 5, y), (x + 5, y + 5), (x, y + 5)])
    save(g.render("#FFFFFF", "#5FE0FF", "#2A9FD8"), "textures/gui/icon/category_all.png")

    g = Glyph(16)  # weapons: a sword
    g.poly([(14.5, 1.5), (14, 4.5), (6.5, 12), (4, 9.5), (11.5, 2)])
    g.line([(2.5, 8.5), (7.5, 13.5)], 1.8)
    g.line([(4.2, 11.8), (2, 14)], 1.8)
    g.circle(1.8, 14.2, 1.2)
    save(g.render("#FFE0E0", "#FF4A5A", "#B01E2E"), "textures/gui/icon/category_weapons.png")

    g = Glyph(16)  # armor: a chestplate
    g.poly([(1, 5), (4.5, 1.5), (6.5, 1.5), (8, 3.5), (9.5, 1.5), (11.5, 1.5), (15, 5), (12.5, 7.5), (12.5, 14.5),
            (3.5, 14.5), (3.5, 7.5)])
    g.line([(8, 5.5), (8, 13)], 0.9, erase=True)
    save(g.render("#FFFFFF", "#8FD8FF", "#3A8FE0"), "textures/gui/icon/category_armor.png")

    g = Glyph(16)  # consumables: a potion flask
    g.circle(8, 10.5, 4.8)
    g.poly([(6.5, 3), (9.5, 3), (9.5, 7), (6.5, 7)])
    g.poly([(5.5, 1), (10.5, 1), (10.5, 3), (5.5, 3)])
    g.circle(6.3, 9.3, 1.1, erase=True)
    save(g.render("#F0FFE0", "#7FE07F", "#2E9A4A"), "textures/gui/icon/category_consumables.png")

    g = Glyph(16)  # materials: a cut gem
    g.poly([(8, 1.5), (13.5, 5), (13.5, 11), (8, 14.5), (2.5, 11), (2.5, 5)])
    g.line([(2.5, 5), (8, 8), (13.5, 5)], 0.8, erase=True)
    g.line([(8, 8), (8, 14.5)], 0.8, erase=True)
    save(g.render("#FFF0DC", "#E0A060", "#A0602A"), "textures/gui/icon/category_materials.png")

    g = Glyph(16)  # key items: a key
    g.circle(5, 5, 3.8)
    g.circle(5, 5, 1.6, erase=True)
    g.line([(7.5, 7.5), (14, 14)], 2.0)
    for t in (0.55, 0.8):
        px, py = 7.5 + 6.5 * t, 7.5 + 6.5 * t
        g.line([(px, py), (px + 1.9 * math.cos(-math.pi / 4), py + 1.9 * math.sin(-math.pi / 4))], 1.6)
    save(g.render("#FFF6C8", "#F2B530", "#C98A10"), "textures/gui/icon/category_key_items.png")


if __name__ == "__main__":
    gen_tags()
    gen_icons()
    print("inventory data generated")

"""Builds the structure templates (.nbt) for towns and dungeons.

Every structure is described in code so it can be tweaked and rebuilt:
  python3 tools/gen_data.py
"""
import math
import os
import random

import nbt
from nbt import Byte, DoubleList, Float, FloatList, IntList

DATA_VERSION = 3955  # Minecraft 1.21.1

LEAVES = lambda kind: (f"minecraft:{kind}_leaves", {"persistent": "true", "distance": "1", "waterlogged": "false"})


class Template:
    def __init__(self, sx, sy, sz, seed):
        self.size = (sx, sy, sz)
        self.blocks = {}
        self.entities = []
        self.rnd = random.Random(seed)

    def set(self, x, y, z, block, props=None, nbt_data=None):
        if isinstance(block, tuple):
            block, props = block
        if 0 <= x < self.size[0] and 0 <= y < self.size[1] and 0 <= z < self.size[2]:
            self.blocks[(x, y, z)] = (block, props or {}, nbt_data)

    def get(self, x, y, z):
        b = self.blocks.get((x, y, z))
        return b[0] if b else None

    def fill(self, x0, y0, z0, x1, y1, z1, block, props=None):
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    self.set(x, y, z, block, props)

    def walls(self, x0, y0, z0, x1, y1, z1, block, props=None):
        for x in range(x0, x1 + 1):
            for y in range(y0, y1 + 1):
                for z in range(z0, z1 + 1):
                    if x in (x0, x1) or z in (z0, z1):
                        self.set(x, y, z, block, props)

    def disc(self, cx, y, cz, r, block, props=None, chance=1.0):
        for x in range(int(cx - r - 1), int(cx + r + 2)):
            for z in range(int(cz - r - 1), int(cz + r + 2)):
                if math.hypot(x - cx, z - cz) <= r and self.rnd.random() < chance:
                    self.set(x, y, z, block, props)

    def ring(self, cx, y, cz, r, block, props=None, width=0.6):
        for x in range(int(cx - r - 2), int(cx + r + 3)):
            for z in range(int(cz - r - 2), int(cz + r + 3)):
                if abs(math.hypot(x - cx, z - cz) - r) <= width:
                    self.set(x, y, z, block, props)

    def entity(self, x, y, z, entity_id, yaw=0.0, extra=None):
        data = {"id": entity_id, "PersistenceRequired": Byte(1), "Rotation": FloatList([yaw, 0.0])}
        if extra:
            data.update(extra)
        self.entities.append({
            "pos": DoubleList([x + 0.5, float(y), z + 0.5]),
            "blockPos": IntList([x, y, z]),
            "nbt": data,
        })

    def clear_above(self, height=None):
        """Fill every unset cell above the floor with air so terrain is carved out."""
        sx, sy, sz = self.size
        for x in range(sx):
            for z in range(sz):
                for y in range(1, height or sy):
                    if (x, y, z) not in self.blocks:
                        self.blocks[(x, y, z)] = ("minecraft:air", {}, None)

    def save(self, path):
        palette = []
        index = {}
        blocks = []
        for (x, y, z), (name, props, data) in sorted(self.blocks.items(), key=lambda kv: (kv[0][1], kv[0][2], kv[0][0])):
            key = (name, tuple(sorted(props.items())))
            if key not in index:
                index[key] = len(palette)
                entry = {"Name": name}
                if props:
                    entry["Properties"] = dict(props)
                palette.append(entry)
            b = {"pos": IntList([x, y, z]), "state": index[key]}
            if data:
                b["nbt"] = data
            blocks.append(b)
        root = {
            "DataVersion": DATA_VERSION,
            "size": IntList(list(self.size)),
            "palette": palette,
            "blocks": blocks,
            "entities": self.entities,
        }
        os.makedirs(os.path.dirname(path), exist_ok=True)
        nbt.write(path, root)


# ---------------------------------------------------------------------------------------
# Reusable bits
# ---------------------------------------------------------------------------------------

def lantern(t, x, y, z, soul=False):
    t.set(x, y, z, "minecraft:soul_lantern" if soul else "minecraft:lantern", {"hanging": "false", "waterlogged": "false"})


def tree(t, x, y, z, log="oak", leaves="oak", height=5, radius=2):
    for i in range(height):
        t.set(x, y + i, z, f"minecraft:{log}_log", {"axis": "y"})
    top = y + height
    for dy in range(-2, 2):
        r = radius if dy < 0 else radius - 1
        for dx in range(-r, r + 1):
            for dz in range(-r, r + 1):
                if abs(dx) + abs(dz) <= r + 1 and t.get(x + dx, top + dy, z + dz) is None:
                    t.set(x + dx, top + dy, z + dz, *LEAVES(leaves))


# ---------------------------------------------------------------------------------------
# Fastera — the starting town
# ---------------------------------------------------------------------------------------

# ---------------------------------------------------------------------------------------
# Worldgen structures
# ---------------------------------------------------------------------------------------

def wezaemon_tomb():
    t = Template(31, 28, 31, "tomb")
    t.fill(0, 0, 0, 30, 0, 30, "minecraft:grass_block", {"snowy": "false"})
    t.disc(15, 0, 15, 14, "minecraft:podzol", {"snowy": "false"}, chance=0.25)
    # path from the torii gate to the grave
    t.fill(14, 0, 16, 16, 0, 30, "minecraft:gravel")
    t.fill(15, 0, 16, 15, 0, 30, "minecraft:polished_andesite")
    # torii gate
    for x in (11, 19):
        t.fill(x, 1, 27, x, 6, 27, "minecraft:red_terracotta")
    t.fill(10, 7, 27, 20, 7, 27, "minecraft:dark_oak_planks")
    t.fill(11, 5, 27, 19, 5, 27, "minecraft:red_terracotta")
    # Setsuna's grave
    t.fill(13, 0, 11, 17, 0, 15, "minecraft:polished_diorite")
    t.fill(14, 1, 12, 16, 1, 13, "minecraft:polished_diorite")
    t.set(15, 2, 12, "minecraft:quartz_pillar", {"axis": "y"})
    t.set(15, 3, 12, "minecraft:chiseled_quartz_block")
    for (x, z) in ((13, 11), (17, 11), (13, 15), (17, 15)):
        t.set(x, 1, z, "minecraft:stone_brick_wall")
        lantern(t, x, 2, z, soul=True)
    t.entity(15, 1, 15, "slfrontier:setsuna", yaw=0.0)
    # rows of old tombstones
    for z in (4, 8, 20, 24):
        for x in range(3, 28, 4):
            if 12 <= x <= 18 and z >= 20:
                continue
            t.set(x, 1, z, t.rnd.choice(["minecraft:mossy_stone_brick_wall", "minecraft:stone_brick_wall"]))
            t.set(x, 2, z, t.rnd.choice(["minecraft:chiseled_stone_bricks", "minecraft:mossy_stone_bricks", "minecraft:cracked_stone_bricks"]))
    # the huge, dead, gnarled tree Setsuna appears beside on full-moon nights
    dead_tree(t, 21, 1, 9, 19)
    tree(t, 6, 1, 14, "cherry", "cherry", 4, 2)
    t.set(26, 0, 27, "minecraft:polished_andesite")
    t.set(26, 1, 27, "slfrontier:waypoint_crystal")
    # red spider lilies everywhere
    for x in range(31):
        for z in range(31):
            if t.get(x, 1, z) is None and t.get(x, 0, z) in ("minecraft:grass_block", "minecraft:podzol") and t.rnd.random() < 0.35:
                t.set(x, 1, z, "slfrontier:red_spider_lily")
    t.clear_above()
    return t


def nightslayer_den():
    t = Template(27, 14, 27, "den")
    c = 13
    t.fill(0, 0, 0, 26, 0, 26, "minecraft:podzol", {"snowy": "false"})
    t.disc(c, 0, c, 12, "minecraft:coarse_dirt", chance=0.4)
    t.disc(c, 0, c, 10, "minecraft:blackstone")
    t.disc(c, 0, c, 10, "minecraft:cracked_polished_blackstone_bricks", chance=0.3)
    t.disc(c, 0, c, 3, "minecraft:polished_blackstone")
    t.ring(c, 0, c, 10, "minecraft:polished_blackstone_bricks", width=0.6)
    for a in range(0, 360, 45):
        x = round(c + 10 * math.cos(math.radians(a)))
        z = round(c + 10 * math.sin(math.radians(a)))
        h = t.rnd.randint(2, 5)
        t.fill(x, 1, z, x, h, z, "minecraft:polished_blackstone_bricks")
        lantern(t, x, h + 1, z, soul=True)
    t.set(c, 1, c, "slfrontier:nightslayer_altar")
    t.set(2, 0, c, "minecraft:polished_blackstone")
    t.set(2, 1, c, "slfrontier:waypoint_crystal")
    for (dx, dz) in ((-2, 0), (2, 0), (0, -2), (0, 2)):
        t.set(c + dx, 1, c + dz, "minecraft:polished_blackstone_slab", {"type": "bottom", "waterlogged": "false"})
    # dead trees and bones
    for (x, z) in ((2, 3), (23, 4), (3, 22), (22, 23)):
        t.fill(x, 1, z, x, t.rnd.randint(4, 7), z, "minecraft:dark_oak_log", {"axis": "y"})
    for _ in range(12):
        x, z = t.rnd.randint(1, 25), t.rnd.randint(1, 25)
        if t.get(x, 1, z) is None and math.hypot(x - c, z - c) > 4:
            t.set(x, 1, z, t.rnd.choice(["minecraft:bone_block", "minecraft:cobweb", "minecraft:skeleton_skull"]))
    t.clear_above()
    return t


def empire_bee_hive():
    t = Template(23, 20, 23, "hive")
    c = 11
    t.disc(c, 0, c, 11, "minecraft:grass_block", {"snowy": "false"})
    t.disc(c, 0, c, 9, "minecraft:honeycomb_block")
    for x in range(23):
        for y in range(1, 20):
            for z in range(23):
                d = math.sqrt((x - c) ** 2 + ((y - 1) * 0.85) ** 2 + (z - c) ** 2)
                if 8 <= d <= 9.2:
                    t.set(x, y, z, "minecraft:bee_nest" if t.rnd.random() < 0.08 else "minecraft:honeycomb_block")
                elif d < 8:
                    t.set(x, y, z, "minecraft:air")
    # entrance to the south
    for y in (1, 2, 3):
        for x in (c - 1, c, c + 1):
            for z in range(c + 6, 23):
                t.set(x, y, z, "minecraft:air")
    for _ in range(14):
        x, z = t.rnd.randint(4, 18), t.rnd.randint(4, 18)
        t.set(x, 1, z, "minecraft:honey_block")
    for (x, z) in ((1, 4), (21, 6), (3, 20), (20, 19)):
        t.set(x, 1, z, "slfrontier:prismatic_crystal")
        t.set(x, 2, z, "slfrontier:prismatic_cluster", {"facing": "up", "waterlogged": "false"})
    t.entity(c, 3, c, "slfrontier:empire_bee_queen", yaw=180.0)
    t.set(c + 4, 0, 21, "minecraft:honeycomb_block")
    t.set(c + 4, 1, 21, "slfrontier:waypoint_crystal")
    t.entity(c - 3, 4, c - 2, "slfrontier:empire_bee")
    t.entity(c + 3, 4, c - 2, "slfrontier:empire_bee")
    t.clear_above()
    return t


def mud_digger_lair():
    t = Template(25, 8, 25, "lair")
    c = 12
    t.fill(0, 0, 0, 24, 0, 24, "minecraft:mud")
    t.disc(c, 0, c, 11, "minecraft:packed_mud", chance=0.3)
    t.ring(c, 1, c, 10, "minecraft:mud", width=1.2)
    for a in range(0, 360, 30):
        x = round(c + 10 * math.cos(math.radians(a)))
        z = round(c + 10 * math.sin(math.radians(a)))
        t.set(x, 1, z, "slfrontier:gray_ore")
        if t.rnd.random() < 0.5:
            t.set(x, 2, z, "slfrontier:gray_ore")
    for _ in range(10):
        x, z = t.rnd.randint(2, 22), t.rnd.randint(2, 22)
        if t.get(x, 1, z) is None:
            t.set(x, 1, z, t.rnd.choice(["minecraft:bone_block", "minecraft:dead_bush"]))
    t.entity(c, 1, c, "slfrontier:mud_digger", yaw=180.0)
    t.set(1, 0, 1, "minecraft:packed_mud")
    t.set(1, 1, 1, "slfrontier:waypoint_crystal")
    t.clear_above()
    return t


def divinity_ruins():
    t = Template(21, 12, 21, "ruins")
    t.fill(0, 0, 0, 20, 0, 20, "minecraft:polished_andesite")
    t.disc(10, 0, 10, 10, "minecraft:cracked_stone_bricks", chance=0.4)
    t.disc(10, 0, 10, 10, "minecraft:weathered_cut_copper", chance=0.2)
    for (x0, z0, x1, z1) in ((2, 2, 18, 2), (2, 18, 18, 18), (2, 2, 2, 18), (18, 2, 18, 18)):
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                h = t.rnd.randint(0, 5)
                for y in range(1, h + 1):
                    t.set(x, y, z, t.rnd.choice(["minecraft:oxidized_cut_copper", "minecraft:weathered_copper",
                                                  "minecraft:cracked_stone_bricks", "minecraft:tinted_glass"]))
    for (x, z) in ((5, 5), (15, 5), (5, 15), (15, 15)):
        t.fill(x, 1, z, x, 6, z, "minecraft:iron_bars")
        t.set(x, 7, z, "minecraft:redstone_lamp", {"lit": "false"})
    t.set(10, 1, 10, "minecraft:chest", {"facing": "south", "type": "single", "waterlogged": "false"},
          {"LootTable": "slfrontier:chests/divinity_ruins"})
    t.set(10, 1, 9, "minecraft:heavy_core", {"waterlogged": "false"})
    t.set(1, 1, 10, "slfrontier:waypoint_crystal")
    for y in range(2, 7):
        t.set(10, y, 12, "minecraft:chain", {"axis": "y", "waterlogged": "false"})
    t.clear_above()
    return t


def dead_tree(t, x, y, z, height):
    """A huge dead tree with twisted, bare branches."""
    rnd = t.rnd
    wood = ("minecraft:stripped_dark_oak_wood", {"axis": "y"})
    for i in range(height):
        r = 1.6 - i / height
        for dx in range(-2, 3):
            for dz in range(-2, 3):
                if math.hypot(dx, dz) <= r + 0.3:
                    t.set(x + dx + (i // 5) % 2, y + i, z + dz, *wood)
    for k in range(7):
        ang = math.radians(k * 51 + rnd.uniform(-15, 15))
        by = y + int(height * rnd.uniform(0.45, 0.95))
        px, pz, py = float(x), float(z), float(by)
        for i in range(rnd.randint(4, 8)):
            px += math.cos(ang)
            pz += math.sin(ang)
            py += rnd.choice([0, 0.5, 1])
            ang += rnd.uniform(-0.5, 0.5)
            t.set(round(px), round(py), round(pz), *wood)
    for a in range(0, 360, 60):
        for i in range(2, 5):
            t.set(round(x + math.cos(math.radians(a)) * i), y - (0 if i < 4 else 0), round(z + math.sin(math.radians(a)) * i), *wood)


def shadow_altar():
    """The broken altar in the Lightless Barrens, ringed by toppled black monoliths."""
    t = Template(41, 22, 41, "shadow_altar")
    c = 20
    t.fill(0, 0, 0, 40, 0, 40, "minecraft:tuff")
    t.disc(c, 0, c, 19, "minecraft:cobbled_deepslate", chance=0.5)
    t.disc(c, 0, c, 14, "minecraft:deepslate_tiles")
    t.disc(c, 0, c, 14, "minecraft:cracked_deepslate_tiles", chance=0.35)
    t.disc(c, 0, c, 5, "minecraft:polished_blackstone")
    t.ring(c, 0, c, 14, "minecraft:polished_deepslate", width=0.6)
    t.ring(c, 0, c, 5, "minecraft:chiseled_polished_blackstone", width=0.5)
    # Monoliths: some standing, some leaning, some fallen, with violet veins
    for i, a in enumerate(range(0, 360, 30)):
        ax, az = c + 14 * math.cos(math.radians(a)), c + 14 * math.sin(math.radians(a))
        h = t.rnd.randint(5, 13)
        lean = t.rnd.choice([0, 0, 0.25, 0.45])
        fallen = i % 4 == 3
        for y in range(1, h + 1):
            if fallen:
                px = round(ax + math.cos(math.radians(a + 90)) * y * 0.9)
                pz = round(az + math.sin(math.radians(a + 90)) * y * 0.9)
                t.set(px, 1, pz, "minecraft:deepslate_bricks")
                continue
            ox = round(ax + math.cos(math.radians(a)) * y * lean)
            oz = round(az + math.sin(math.radians(a)) * y * lean)
            for dx in (0, 1):
                t.set(ox + dx, y, oz, "minecraft:polished_deepslate" if y % 4 else "minecraft:amethyst_block")
            if y == h:
                t.set(ox, y + 1, oz, "minecraft:deepslate_tile_slab", {"type": "bottom", "waterlogged": "false"})
    # The altar on a stepped dais
    t.disc(c, 1, c, 3, "minecraft:polished_blackstone_bricks")
    t.disc(c, 2, c, 1.6, "minecraft:polished_blackstone_bricks")
    t.set(c, 3, c, "slfrontier:shadow_altar")
    for (dx, dz) in ((-3, -3), (3, -3), (-3, 3), (3, 3)):
        t.set(c + dx, 1, c + dz, "minecraft:polished_blackstone_wall")
        t.set(c + dx, 2, c + dz, "minecraft:soul_lantern", {"hanging": "false", "waterlogged": "false"})
    # Dead trees and ash
    for (x, z) in ((4, 5), (35, 7), (6, 34), (34, 33)):
        dead_tree(t, x, 1, z, t.rnd.randint(7, 10))
    for _ in range(40):
        x, z = t.rnd.randint(1, 39), t.rnd.randint(1, 39)
        if t.get(x, 1, z) is None and math.hypot(x - c, z - c) > 6:
            t.set(x, 1, z, t.rnd.choice(["minecraft:dead_bush", "minecraft:bone_block", "minecraft:cobweb", "minecraft:wither_rose"]))
    t.set(c, 0, 2, "minecraft:polished_blackstone")
    t.set(c, 1, 2, "slfrontier:waypoint_crystal")
    t.clear_above()
    return t


ALL = {
    "wezaemon_tomb": wezaemon_tomb,
    "nightslayer_den": nightslayer_den,
    "empire_bee_hive": empire_bee_hive,
    "mud_digger_lair": mud_digger_lair,
    "divinity_ruins": divinity_ruins,
    "shadow_altar": shadow_altar,
}


def build_all(out_dir):
    import towns
    for name, fn in {**ALL, **towns.ALL}.items():
        t = fn()
        t.save(os.path.join(out_dir, name + ".nbt"))
        print(f"structure {name}: {len(t.blocks)} blocks, {len(t.entities)} entities")

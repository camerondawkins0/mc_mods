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

def door(t, x, y, z, facing, wood="oak"):
    t.set(x, y, z, f"minecraft:{wood}_door", {"facing": facing, "half": "lower", "hinge": "left", "open": "false", "powered": "false"})
    t.set(x, y + 1, z, f"minecraft:{wood}_door", {"facing": facing, "half": "upper", "hinge": "left", "open": "false", "powered": "false"})


def bed(t, x, y, z, facing, color="red"):
    dx, dz = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}[facing]
    t.set(x, y, z, f"minecraft:{color}_bed", {"facing": facing, "part": "foot", "occupied": "false"})
    t.set(x + dx, y, z + dz, f"minecraft:{color}_bed", {"facing": facing, "part": "head", "occupied": "false"})


def lantern(t, x, y, z, soul=False):
    t.set(x, y, z, "minecraft:soul_lantern" if soul else "minecraft:lantern", {"hanging": "false", "waterlogged": "false"})


def lamp_post(t, x, y, z, height=3, soul=False):
    for i in range(height):
        t.set(x, y + i, z, "minecraft:oak_fence")
    lantern(t, x, y + height, z, soul)


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


def house(t, x0, z0, door_side, seed_color="red", w=9, d=9, h=4):
    x1, z1 = x0 + w - 1, z0 + d - 1
    t.fill(x0, 0, z0, x1, 0, z1, "minecraft:oak_planks")
    t.walls(x0, 1, z0, x1, h, z1, "minecraft:oak_planks")
    for (cx, cz) in ((x0, z0), (x0, z1), (x1, z0), (x1, z1)):
        t.fill(cx, 1, cz, cx, h, cz, "minecraft:oak_log", {"axis": "y"})
    # roof: two stepped layers of spruce
    t.fill(x0 - 1, h + 1, z0 - 1, x1 + 1, h + 1, z1 + 1, "minecraft:spruce_planks")
    t.fill(x0 + 1, h + 2, z0 + 1, x1 - 1, h + 2, z1 - 1, "minecraft:spruce_planks")
    t.fill(x0 + 3, h + 3, z0 + 3, x1 - 3, h + 3, z1 - 3, "minecraft:spruce_planks")
    # interior air
    t.fill(x0 + 1, 1, z0 + 1, x1 - 1, h, z1 - 1, "minecraft:air")
    mx, mz = (x0 + x1) // 2, (z0 + z1) // 2
    if door_side == "east":
        door(t, x1, 1, mz, "east")
        t.set(x0, 2, mz, "minecraft:glass_pane")
    elif door_side == "west":
        door(t, x0, 1, mz, "west")
        t.set(x1, 2, mz, "minecraft:glass_pane")
    elif door_side == "south":
        door(t, mx, 1, z1, "south")
        t.set(mx, 2, z0, "minecraft:glass_pane")
    else:
        door(t, mx, 1, z0, "north")
        t.set(mx, 2, z1, "minecraft:glass_pane")
    for side_x in (x0, x1):
        t.set(side_x, 2, z0 + 2, "minecraft:glass_pane")
        t.set(side_x, 2, z1 - 2, "minecraft:glass_pane")
    bed(t, x0 + 1, 1, z0 + 1, "south", seed_color)
    t.set(x1 - 1, 1, z0 + 1, "minecraft:crafting_table")
    t.set(x1 - 1, 1, z1 - 1, "minecraft:barrel", {"facing": "up", "open": "false"})
    lantern(t, x0 + 1, 1, z1 - 1)


# ---------------------------------------------------------------------------------------
# Fastera — the starting town
# ---------------------------------------------------------------------------------------

def fastera():
    t = Template(41, 18, 41, "fastera")
    c = 20
    t.fill(0, 0, 0, 40, 0, 40, "minecraft:grass_block", {"snowy": "false"})
    # roads
    t.fill(19, 0, 0, 21, 0, 40, "minecraft:dirt_path")
    t.fill(0, 0, 19, 40, 0, 21, "minecraft:dirt_path")
    # plaza
    t.disc(c, 0, c, 8.5, "minecraft:stone_bricks")
    t.disc(c, 0, c, 8.5, "minecraft:cobblestone", chance=0.25)
    t.ring(c, 0, c, 8.5, "minecraft:polished_andesite", width=0.5)
    t.set(c, 0, c, "minecraft:chiseled_stone_bricks")
    # fountain north of the plaza center
    fz = 13
    t.fill(c - 2, 0, fz - 2, c + 2, 0, fz + 2, "minecraft:stone_bricks")
    t.walls(c - 2, 1, fz - 2, c + 2, 1, fz + 2, "minecraft:stone_brick_wall")
    t.fill(c - 1, 1, fz - 1, c + 1, 1, fz + 1, "minecraft:water", {"level": "0"})
    t.set(c, 1, fz, "minecraft:chiseled_stone_bricks")
    t.set(c, 2, fz, "minecraft:chiseled_stone_bricks")
    lantern(t, c, 3, fz)
    # lamp posts around the plaza
    for (lx, lz) in ((c - 7, c - 7), (c + 7, c - 7), (c - 7, c + 7), (c + 7, c + 7)):
        lamp_post(t, lx, 1, lz)
    # houses (beds inside let players set a Frontier respawn point)
    house(t, 2, 2, "east", "red")
    house(t, 2, 30, "east", "blue")
    house(t, 30, 30, "west", "yellow")
    # blacksmith: an open forge on the east side of the road
    bx0, bz0 = 29, 3
    t.fill(bx0, 0, bz0, bx0 + 9, 0, bz0 + 9, "minecraft:cobblestone")
    for (px, pz) in ((bx0, bz0), (bx0 + 9, bz0), (bx0, bz0 + 9), (bx0 + 9, bz0 + 9)):
        t.fill(px, 1, pz, px, 4, pz, "minecraft:spruce_log", {"axis": "y"})
    t.fill(bx0, 5, bz0, bx0 + 9, 5, bz0 + 9, "minecraft:spruce_planks")
    t.fill(bx0 + 1, 6, bz0 + 1, bx0 + 8, 6, bz0 + 8, "minecraft:spruce_slab", {"type": "bottom", "waterlogged": "false"})
    t.fill(bx0 + 7, 1, bz0 + 1, bx0 + 8, 1, bz0 + 8, "minecraft:bricks")
    t.set(bx0 + 8, 2, bz0 + 2, "minecraft:furnace", {"facing": "west", "lit": "false"})
    t.set(bx0 + 8, 2, bz0 + 4, "minecraft:blast_furnace", {"facing": "west", "lit": "false"})
    t.set(bx0 + 8, 2, bz0 + 6, "minecraft:lava_cauldron")
    t.set(bx0 + 4, 1, bz0 + 4, "minecraft:anvil", {"facing": "north"})
    t.set(bx0 + 4, 1, bz0 + 7, "minecraft:smithing_table")
    t.set(bx0 + 2, 1, bz0 + 2, "minecraft:grindstone", {"face": "floor", "facing": "north"})
    t.fill(bx0 + 8, 2, bz0 + 1, bx0 + 8, 4, bz0 + 1, "minecraft:bricks")
    t.entity(bx0 + 4, 1, bz0 + 5, "slfrontier:frontier_blacksmith", yaw=90.0)
    # notice board / guide stand by the fountain
    t.set(c + 3, 1, fz + 3, "minecraft:lectern", {"facing": "south", "has_book": "false", "powered": "false"})
    t.entity(c, 1, fz + 4, "slfrontier:frontier_guide", yaw=0.0)
    # hedges and trees for a bit of life
    for x in range(0, 41):
        for z in (0, 40):
            if not 18 <= x <= 22:
                t.set(x, 1, z, *LEAVES("oak"))
    for z in range(0, 41):
        for x in (0, 40):
            if not 18 <= z <= 22:
                t.set(x, 1, z, *LEAVES("oak"))
    tree(t, 14, 1, 33)
    tree(t, 26, 1, 35, "birch", "birch")
    tree(t, 34, 1, 18)
    for _ in range(40):
        x, z = t.rnd.randint(1, 39), t.rnd.randint(1, 39)
        if t.get(x, 0, z) == "minecraft:grass_block" and t.get(x, 1, z) is None:
            t.set(x, 1, z, t.rnd.choice(["minecraft:short_grass", "minecraft:dandelion", "minecraft:poppy",
                                          "minecraft:cornflower", "minecraft:short_grass"]))
    t.clear_above()
    return t


# ---------------------------------------------------------------------------------------
# Rabituza — the hidden nation of the Vorpal Bunnies
# ---------------------------------------------------------------------------------------

def burrow(t, cx, cz, r, door_facing):
    for x in range(cx - r, cx + r + 1):
        for z in range(cz - r, cz + r + 1):
            for y in range(1, r + 1):
                d = math.sqrt((x - cx) ** 2 + (y * 1.3) ** 2 + (z - cz) ** 2)
                if d <= r:
                    inner = d <= r - 1.2
                    if inner:
                        t.set(x, y, z, "minecraft:air")
                    else:
                        top = math.sqrt((x - cx) ** 2 + ((y + 1) * 1.3) ** 2 + (z - cz) ** 2) > r
                        t.set(x, y, z, "minecraft:grass_block" if top else "minecraft:dirt", {"snowy": "false"} if top else None)
    dx, dz = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}[door_facing]
    for i in range(r - 1, r + 1):
        t.set(cx + dx * i, 1, cz + dz * i, "minecraft:air")
        t.set(cx + dx * i, 2, cz + dz * i, "minecraft:air")
    door(t, cx + dx * (r - 1), 1, cz + dz * (r - 1), door_facing, "spruce")
    lantern(t, cx, 1, cz)
    t.set(cx - dz, 1, cz - dx, "minecraft:hay_block", {"axis": "y"})


def rabituza():
    t = Template(41, 18, 41, "rabituza")
    c = 20
    t.fill(0, 0, 0, 40, 0, 40, "minecraft:grass_block", {"snowy": "false"})
    # Vorpal Colosseum in the middle
    t.disc(c, 0, c, 9, "minecraft:smooth_sandstone")
    t.ring(c, 0, c, 9, "minecraft:stone_bricks", width=0.7)
    for y in (1, 2):
        t.ring(c, y, c, 9.5, "minecraft:stone_bricks", width=0.6)
    t.ring(c, 3, c, 9.5, "minecraft:stone_brick_wall", width=0.6)
    for (gx, gz) in ((c, c - 9), (c, c + 9), (c - 9, c), (c + 9, c)):
        for dx in (-1, 0, 1):
            for y in (1, 2, 3):
                if gx == c:
                    t.set(gx + dx, y, gz, "minecraft:air")
                    t.set(gx + dx, y, gz + (1 if gz > c else -1), "minecraft:air")
                else:
                    t.set(gx, y, gz + dx, "minecraft:air")
                    t.set(gx + (1 if gx > c else -1), y, gz + dx, "minecraft:air")
    for a in range(0, 360, 45):
        x = round(c + 8 * math.cos(math.radians(a)))
        z = round(c + 8 * math.sin(math.radians(a)))
        lamp_post(t, x, 1, z, 2)
    # Vysache's throne to the north
    t.fill(15, 0, 1, 25, 0, 6, "minecraft:polished_blackstone")
    t.fill(16, 1, 1, 24, 1, 3, "minecraft:polished_blackstone_bricks")
    t.fill(18, 1, 2, 22, 1, 2, "minecraft:gold_block")
    t.set(20, 2, 2, "minecraft:quartz_stairs", {"facing": "north", "half": "bottom", "shape": "straight", "waterlogged": "false"})
    t.fill(19, 3, 1, 21, 4, 1, "minecraft:red_wool")
    t.fill(20, 1, 4, 20, 1, 10, "minecraft:red_carpet")
    t.entity(20, 1, 5, "slfrontier:vysache", yaw=0.0)
    for x in (16, 24):
        t.fill(x, 2, 1, x, 4, 1, "minecraft:polished_blackstone_wall")
        lantern(t, x, 5, 1)
    # Bilac's forge to the east
    t.fill(33, 0, 15, 39, 0, 25, "minecraft:stone_bricks")
    t.fill(38, 1, 15, 39, 4, 25, "minecraft:bricks")
    t.set(37, 1, 17, "minecraft:blast_furnace", {"facing": "west", "lit": "true"})
    t.set(37, 1, 19, "minecraft:lava_cauldron")
    t.set(37, 1, 21, "minecraft:furnace", {"facing": "west", "lit": "true"})
    t.set(35, 1, 18, "minecraft:anvil", {"facing": "east"})
    t.set(35, 1, 22, "minecraft:smithing_table")
    t.fill(33, 5, 15, 39, 5, 25, "minecraft:spruce_planks")
    for (px, pz) in ((33, 15), (33, 25)):
        t.fill(px, 1, pz, px, 4, pz, "minecraft:spruce_log", {"axis": "y"})
    t.entity(35, 1, 20, "slfrontier:bilac", yaw=90.0)
    # burrows
    burrow(t, 7, 7, 4, "south")
    burrow(t, 7, 33, 4, "north")
    burrow(t, 31, 34, 4, "north")
    # carrot fields
    for (fx, fz) in ((2, 15), (2, 24)):
        t.fill(fx, 0, fz, fx + 7, 0, fz + 3, "minecraft:farmland", {"moisture": "7"})
        t.fill(fx, 1, fz, fx + 7, 1, fz + 3, "minecraft:carrots", {"age": "7"})
        t.fill(fx, 0, fz + 1, fx + 7, 0, fz + 1, "minecraft:water", {"level": "0"})
        t.fill(fx, 1, fz + 1, fx + 7, 1, fz + 1, "minecraft:air")
    # cherry trees
    for (x, z) in ((3, 3), (37, 3), (27, 38), (14, 37)):
        tree(t, x, 1, z, "cherry", "cherry", 4, 2)
    for _ in range(50):
        x, z = t.rnd.randint(1, 39), t.rnd.randint(1, 39)
        if t.get(x, 0, z) == "minecraft:grass_block" and t.get(x, 1, z) is None:
            t.set(x, 1, z, t.rnd.choice(["minecraft:short_grass", "minecraft:pink_petals", "minecraft:allium"]))
    t.clear_above()
    return t


# ---------------------------------------------------------------------------------------
# Worldgen structures
# ---------------------------------------------------------------------------------------

def wezaemon_tomb():
    t = Template(31, 12, 31, "tomb")
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
    # a lone cherry tree over the grave
    tree(t, 22, 1, 12, "cherry", "cherry", 5, 3)
    tree(t, 6, 1, 14, "cherry", "cherry", 4, 2)
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
    for y in range(2, 7):
        t.set(10, y, 12, "minecraft:chain", {"axis": "y", "waterlogged": "false"})
    t.clear_above()
    return t


ALL = {
    "town/fastera": fastera,
    "town/rabituza": rabituza,
    "wezaemon_tomb": wezaemon_tomb,
    "nightslayer_den": nightslayer_den,
    "empire_bee_hive": empire_bee_hive,
    "mud_digger_lair": mud_digger_lair,
    "divinity_ruins": divinity_ruins,
}


def build_all(out_dir):
    for name, fn in ALL.items():
        t = fn()
        t.save(os.path.join(out_dir, name + ".nbt"))
        print(f"structure {name}: {len(t.blocks)} blocks, {len(t.entities)} entities")

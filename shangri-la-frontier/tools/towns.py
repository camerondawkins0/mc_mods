"""The hand-placed towns and landmarks of the Frontier, built from tools/townkit.py.

Town templates don't carve terrain themselves: Landmarks levels and clears the footprint
before placing them, so only real blocks are stored. The Serpent Bridge sits partly below
ground; its "ground" marker tells Landmarks which template layer is the surface.
"""
import math

from structures import Template, LEAVES
import townkit as k


def _grass(t, x0, z0, x1, z1, y=0):
    t.fill(x0, y, z0, x1, y, z1, "minecraft:grass_block", {"snowy": "false"})


# ---------------------------------------------------------------------------------------
# Firstia: the walled starting town
# ---------------------------------------------------------------------------------------

def firstia():
    S = 112
    t = Template(S, 40, S, "firstia")
    c = S // 2
    _grass(t, 0, 0, S - 1, S - 1)
    wall = "minecraft:stone_bricks"
    # Curtain wall with mossy patches
    for (x0, z0, x1, z1, inner) in ((4, 4, 107, 4, (0, 1)), (4, 106, 107, 106, (0, -1)),
                                    (4, 4, 4, 107, (1, 0)), (106, 4, 106, 107, (-1, 0))):
        k.wall_line(t, x0, z0, x1, z1, 7, wall, cap="minecraft:stone_bricks", thick=2, inner_dir=inner)
    for _ in range(260):
        x, z = t.rnd.randint(4, 107), t.rnd.choice([4, 5, 106, 107])
        y = t.rnd.randint(1, 6)
        if t.get(x, y, z) == wall:
            t.set(x, y, z, t.rnd.choice(["minecraft:mossy_stone_bricks", "minecraft:cracked_stone_bricks"]))
    # Corner towers with slate cones
    for (tx, tz) in ((6, 6), (105, 6), (6, 105), (105, 105)):
        k.round_tower(t, tx, tz, 5, 13, wall, roof="minecraft:deepslate_tiles", roof_height=10,
                      trim="minecraft:chiseled_stone_bricks")
    # Gates: east (towards Secondil) and west
    for gx, towers_x in ((106, 101), (4, 3)):
        for z in range(c - 3, c + 4):
            for x in (gx, gx + (1 if gx == 106 else -1) * -1):
                for y in range(1, 6):
                    t.set(x, y, z, "minecraft:air")
        for tz in (c - 12, c + 5):
            k.square_tower(t, towers_x, tz, 8, 15, wall, trim="minecraft:chiseled_stone_bricks")
        for z in range(c - 4, c + 5):
            for y in range(6, 10):
                for x in range(towers_x + 2, towers_x + 6):
                    t.set(x, y, z, wall)
            if z % 2 == 0:
                t.set(towers_x + 2, 10, z, wall)
                t.set(towers_x + 5, 10, z, wall)
        for z in range(c - 3, c + 4):
            t.set(towers_x + 3, 5, z, "minecraft:iron_bars", {"east": "false", "west": "false", "north": "true", "south": "true",
                                                              "waterlogged": "false"})
        face = "east" if gx == 106 else "west"
        bx = towers_x + 8 if gx == 106 else towers_x - 1
        for tz in (c - 9, c + 8):
            k.banner(t, bx, 12, tz, face, "blue", 3, trim="white")
    # Streets
    k.road(t, 0, c - 2, S - 1, c + 2, "minecraft:stone_bricks", "minecraft:polished_andesite",
           mix=["minecraft:cobblestone", "minecraft:andesite"])
    k.road(t, c - 2, 6, c + 2, S - 7, "minecraft:stone_bricks", "minecraft:polished_andesite",
           mix=["minecraft:cobblestone", "minecraft:andesite"])
    for z in (30, 82):
        k.road(t, 8, z - 1, S - 9, z + 1, "minecraft:dirt_path", None, mix=["minecraft:coarse_dirt", "minecraft:gravel"])
    # Plaza with a grand fountain
    t.disc(c, 0, c, 15, "minecraft:stone_bricks")
    t.disc(c, 0, c, 15, "minecraft:polished_andesite", chance=0.3)
    t.ring(c, 0, c, 15, "minecraft:chiseled_stone_bricks", width=0.5)
    k.fountain(t, c, c, 5, 3)
    for a in range(0, 360, 45):
        # Offset from the axes so no lamp stands in the roads (or in front of the spawn point).
        x = round(c + 12 * math.cos(math.radians(a + 22.5)))
        z = round(c + 12 * math.sin(math.radians(a + 22.5)))
        k.lamp_post(t, x, 1, z, 3)
    t.entity(c, 1, c + 8, "slfrontier:frontier_guide", yaw=180.0)
    k.waypoint(t, c + 9, 0, c - 9)
    k.marker(t, c, 1, c + 10, "spawn")
    # North-west: the inn
    inn_door = k.house(t, 12, 12, 17, 12, "timber", floors=3, door_side="south", bed_color="blue")
    k.sign(t, 20, 3, 24, "south", ["Inn"], wood="spruce")
    for (x, z) in ((14, 13), (18, 13), (22, 13), (26, 13)):
        k.bed(t, x, 5, z, "south", "blue")
        k.bed(t, x, 9, z, "south", "cyan")
    k.house(t, 34, 12, 11, 9, "timber", floors=2, door_side="south", bed_color="red")
    k.house(t, 12, 34, 9, 11, "timber_red", floors=2, door_side="east", bed_color="lime")
    k.house(t, 26, 36, 11, 9, "timber", floors=2, door_side="south", bed_color="yellow")
    # North-east: chapel with a bell tower
    t.fill(64, 0, 10, 92, 0, 26, "minecraft:polished_andesite")
    k.house(t, 64, 12, 21, 13, "timber_red", floors=2, door_side="south", furnish=False, h=5)
    k.square_tower(t, 86, 12, 7, 24, "minecraft:stone_bricks", roof="minecraft:deepslate_tiles",
                   trim="minecraft:chiseled_stone_bricks")
    t.set(89, 20, 15, "minecraft:bell", {"attachment": "ceiling", "facing": "north", "powered": "false"})
    for x in range(66, 84, 3):
        t.set(x, 3, 12, "minecraft:purple_stained_glass_pane", {"waterlogged": "false"})
    k.house(t, 70, 34, 11, 9, "timber", floors=2, door_side="south", bed_color="orange")
    k.house(t, 86, 32, 11, 11, "timber_red", floors=2, door_side="west", bed_color="purple")
    # South-west: houses round a well and gardens
    k.house(t, 12, 64, 11, 9, "timber", floors=2, door_side="north", bed_color="green")
    k.house(t, 28, 64, 9, 9, "timber_red", floors=2, door_side="north", bed_color="brown")
    k.house(t, 12, 88, 13, 11, "timber", floors=2, door_side="north", bed_color="light_blue")
    k.house(t, 32, 88, 11, 11, "timber", floors=3, door_side="north", bed_color="pink")
    t.walls(42, 1, 64, 46, 1, 68, "minecraft:cobblestone_wall")
    t.fill(43, 0, 65, 45, 0, 67, "minecraft:water", {"level": "0"})
    for (x, z) in ((42, 64), (46, 64), (42, 68), (46, 68)):
        t.fill(x, 1, z, x, 3, z, "minecraft:spruce_fence", {"waterlogged": "false"})
    t.fill(42, 4, 64, 46, 4, 68, "minecraft:spruce_slab", {"type": "bottom", "waterlogged": "false"})
    for fx in (14, 22):
        t.fill(fx, 0, 77, fx + 6, 0, 82, "minecraft:farmland", {"moisture": "7"})
        t.fill(fx, 1, 77, fx + 6, 1, 82, "minecraft:wheat", {"age": "7"})
        t.fill(fx + 3, 0, 77, fx + 3, 0, 82, "minecraft:water", {"level": "0"})
        t.fill(fx + 3, 1, 77, fx + 3, 1, 82, "minecraft:air")
    # South-east: market square and guild hall
    t.fill(62, 0, 62, 98, 0, 78, "minecraft:cobblestone")
    t.fill(62, 0, 62, 98, 0, 78, "minecraft:stone_bricks")
    for i, (x, z) in enumerate(((64, 64), (70, 64), (76, 64), (64, 72), (70, 72), (76, 72))):
        k.market_stall(t, x, z, ["red", "yellow", "blue", "lime", "orange", "white"][i])
    k.house(t, 84, 62, 15, 15, "timber_red", floors=2, door_side="west", furnish=False, h=5)
    k.sign(t, 83, 3, 69, "west", ["Adventurers'", "Guild"], wood="spruce")
    k.house(t, 64, 86, 11, 11, "timber", floors=2, door_side="north", bed_color="magenta")
    k.house(t, 82, 86, 13, 11, "timber", floors=2, door_side="north", bed_color="gray")
    # A weapon shop with an open forge (no smith: he lives in Secondil)
    t.fill(38, 0, 46, 48, 0, 52, "minecraft:cobblestone")
    for (px, pz) in ((38, 46), (48, 46), (38, 52), (48, 52)):
        t.fill(px, 1, pz, px, 4, pz, "minecraft:spruce_log", {"axis": "y"})
    t.fill(38, 5, 46, 48, 5, 52, "minecraft:spruce_planks")
    t.set(40, 1, 48, "minecraft:anvil", {"facing": "north"})
    t.set(46, 1, 47, "minecraft:blast_furnace", {"facing": "west", "lit": "false"})
    t.set(46, 1, 50, "minecraft:grindstone", {"face": "floor", "facing": "north"})
    # Street lamps and trees
    for x in range(10, S - 10, 10):
        if abs(x - c) > 16:
            k.street_lamp(t, x, 1, c - 3, "south")
            k.street_lamp(t, x, 1, c + 3, "north")
    for z in range(10, S - 10, 10):
        if abs(z - c) > 16:
            k.street_lamp(t, c - 3, 1, z, "east")
    for (x, z) in ((50, 22), (96, 48), (24, 52), (52, 96), (98, 98), (10, 100), (100, 10), (30, 100)):
        if t.get(x, 1, z) is None:
            k.tree(t, x, 1, z, t.rnd.choice(["oak", "birch"]), "oak", 6, 3)
    k.flowers(t, 6, 6, S - 7, S - 7, 0.08)
    return t


# ---------------------------------------------------------------------------------------
# Secondil: tan brick walls, a twin-towered gate, red-and-gold banners, a castle on a hill
# ---------------------------------------------------------------------------------------

def secondil():
    S = 128
    t = Template(S, 56, S, "secondil")
    c = S // 2
    _grass(t, 0, 0, S - 1, S - 1)
    wall, cap = "minecraft:cut_sandstone", "minecraft:smooth_sandstone"
    for (x0, z0, x1, z1, inner) in ((4, 4, 123, 4, (0, 1)), (4, 121, 123, 121, (0, -1)),
                                    (4, 4, 4, 123, (1, 0)), (121, 4, 121, 123, (-1, 0))):
        k.wall_line(t, x0, z0, x1, z1, 10, wall, cap=cap, thick=3, inner_dir=inner)
    for (tx, tz) in ((2, 2), (117, 2), (2, 117), (117, 117)):
        k.square_tower(t, tx, tz, 9, 16, wall, trim="minecraft:chiseled_sandstone")
    # The west gatehouse: two square crenellated towers joined by a crenellated wall
    for tz in (c - 14, c + 6):
        k.square_tower(t, 0, tz, 9, 19, wall, trim="minecraft:chiseled_sandstone")
    for z in range(c - 5, c + 6):
        for x in range(1, 8):
            for y in range(1, 14):
                arch = abs(z - c) <= 3 and y <= 7 - (1 if abs(z - c) == 3 else 0)
                t.set(x, y, z, "minecraft:air" if arch else wall)
            if z % 2 == 0:
                t.set(x if x in (1, 7) else 1, 14, z, cap)
        t.set(1, 14, z, cap if z % 2 == 0 else "minecraft:air")
        t.set(7, 14, z, cap if z % 2 == 0 else "minecraft:air")
    # The diamond emblem above the arch, and long red-and-gold banners on the towers
    for dz in range(-2, 3):
        for dy in range(-2, 3):
            if abs(dz) + abs(dy) <= 2:
                t.set(0, 10 + dy, c + dz, "minecraft:gold_block" if abs(dz) + abs(dy) == 2 else "minecraft:red_terracotta")
    # East gate (towards the Diremarsh)
    for z in range(c - 2, c + 3):
        for x in range(121, 124):
            for y in range(1, 6):
                t.set(x, y, z, "minecraft:air")
    # Streets: the main street runs from the gate to the inn, then round the castle hill
    k.road(t, 0, c - 3, 46, c + 3, "minecraft:smooth_sandstone", "minecraft:cut_sandstone",
           mix=["minecraft:sandstone", "minecraft:sand"])
    k.road(t, 40, 16, 46, S - 17, "minecraft:smooth_sandstone", "minecraft:cut_sandstone", mix=["minecraft:sandstone"])
    k.road(t, 40, 16, S - 9, 22, "minecraft:smooth_sandstone", "minecraft:cut_sandstone", mix=["minecraft:sandstone"])
    k.road(t, 40, S - 23, S - 9, S - 17, "minecraft:smooth_sandstone", "minecraft:cut_sandstone", mix=["minecraft:sandstone"])
    k.road(t, S - 15, c - 2, S - 1, c + 2, "minecraft:smooth_sandstone", "minecraft:cut_sandstone")
    # Gate plaza: waypoint and spawn
    t.disc(18, 0, c, 8, "minecraft:smooth_sandstone")
    t.ring(18, 0, c, 8, "minecraft:chiseled_sandstone", width=0.5)
    k.waypoint(t, 18, 0, c - 5)
    k.marker(t, 20, 1, c, "spawn")
    # The white inn straight ahead from the gate
    k.house(t, 47, c - 8, 13, 17, "plaster", floors=3, door_side="west", bed_color="white", roof_along=False)
    k.sign(t, 46, 3, c - 2, "west", ["Inn"], wood="birch")
    for z in range(c - 6, c + 8, 3):
        k.bed(t, 57, 5, z, "west", "white")
        k.bed(t, 57, 9, z, "west", "red")
    # Market stalls along the main street, with street lamps
    for i, x in enumerate(range(12, 38, 6)):
        k.market_stall(t, x, c - 9, ["red", "yellow", "orange", "lime", "white"][i % 5])
        k.market_stall(t, x, c + 6, ["blue", "red", "yellow", "pink", "white"][i % 5])
    for x in range(10, 46, 8):
        k.street_lamp(t, x, 1, c - 4, "south")
        k.street_lamp(t, x, 1, c + 4, "north")
    # The blacksmith's forge on the main street
    bx0, bz0 = 26, c - 22
    t.fill(bx0, 0, bz0, bx0 + 11, 0, bz0 + 9, "minecraft:cobblestone")
    for (px, pz) in ((bx0, bz0), (bx0 + 11, bz0), (bx0, bz0 + 9), (bx0 + 11, bz0 + 9)):
        t.fill(px, 1, pz, px, 4, pz, "minecraft:spruce_log", {"axis": "y"})
    t.fill(bx0, 5, bz0, bx0 + 11, 5, bz0 + 9, "minecraft:spruce_planks")
    k.gable_roof(t, bx0, bz0, bx0 + 11, bz0 + 9, 6, "minecraft:brick_stairs", along_x=True)
    t.fill(bx0 + 9, 1, bz0 + 1, bx0 + 10, 4, bz0 + 3, "minecraft:bricks")
    t.set(bx0 + 9, 1, bz0 + 4, "minecraft:blast_furnace", {"facing": "west", "lit": "true"})
    t.set(bx0 + 9, 1, bz0 + 6, "minecraft:lava_cauldron")
    t.set(bx0 + 5, 1, bz0 + 5, "minecraft:anvil", {"facing": "north"})
    t.set(bx0 + 3, 1, bz0 + 7, "minecraft:smithing_table")
    t.set(bx0 + 2, 1, bz0 + 2, "minecraft:grindstone", {"face": "floor", "facing": "north"})
    t.entity(bx0 + 5, 1, bz0 + 7, "slfrontier:frontier_blacksmith", yaw=180.0)
    k.sign(t, bx0 + 6, 3, bz0 + 10, "south", ["Smithy"], wood="spruce")
    # The castle on its hill
    hx0, hz0, hx1, hz1 = 64, 30, 116, 98
    for i, h in enumerate((3, 6, 9)):
        m = i * 5
        t.fill(hx0 + m, 0, hz0 + m, hx1 - m, h, hz1 - m, "minecraft:stone_bricks" if i == 0 else "minecraft:sandstone")
        t.fill(hx0 + m + 1, h, hz0 + m + 1, hx1 - m - 1, h, hz1 - m - 1, "minecraft:grass_block", {"snowy": "false"})
    for y in range(1, 10):
        for z in range(c - 2, c + 3):
            t.set(hx0 - 1 + y, y, z, "minecraft:smooth_sandstone_stairs",
                  {"facing": "east", "half": "bottom", "shape": "straight", "waterlogged": "false"})
            for yy in range(y + 1, 10):
                t.set(hx0 - 1 + y, yy, z, "minecraft:air")
    kx0, kz0 = 80, 46
    for y in range(10, 30):
        for x in range(kx0, kx0 + 22):
            for z in range(kz0, kz0 + 36):
                edge = x in (kx0, kx0 + 21) or z in (kz0, kz0 + 35)
                if edge:
                    t.set(x, y, z, "minecraft:chiseled_sandstone" if y % 6 == 3 else "minecraft:smooth_sandstone")
                elif y in (10, 19):
                    t.set(x, y, z, "minecraft:spruce_planks")
    for x in range(kx0, kx0 + 22):
        for z in range(kz0, kz0 + 36):
            if (x in (kx0, kx0 + 21) or z in (kz0, kz0 + 35)) and (x + z) % 2 == 0:
                t.set(x, 30, z, "minecraft:smooth_sandstone")
    for y in range(12, 28, 4):
        for z in range(kz0 + 3, kz0 + 33, 4):
            t.set(kx0, y, z, "minecraft:glass_pane", {"waterlogged": "false"})
            t.set(kx0 + 21, y, z, "minecraft:glass_pane", {"waterlogged": "false"})
    k.door(t, kx0, 11, c, "west", "dark_oak")
    k.door(t, kx0, 11, c - 1, "west", "dark_oak")
    for (tx, tz) in ((kx0, kz0), (kx0 + 21, kz0), (kx0, kz0 + 35), (kx0 + 21, kz0 + 35)):
        k.round_tower(t, tx, tz, 4, 36, "minecraft:smooth_sandstone", roof="minecraft:red_terracotta", roof_height=9,
                      trim="minecraft:chiseled_sandstone")
    for tz in (kz0 + 8, kz0 + 27):
        k.banner(t, kx0 - 1, 26, tz, "west", "red", 4, trim="yellow")
    t.fill(kx0 + 1, 11, c - 1, kx0 + 20, 11, c, "minecraft:red_carpet")
    # Houses in the remaining blocks
    for (x, z, w, d, side, style) in (
            (12, 14, 11, 9, "south", "tan"), (26, 12, 11, 9, "south", "brick"), (12, 38, 9, 11, "east", "brick"),
            (12, 78, 9, 11, "east", "tan"), (12, 98, 11, 9, "north", "brick"), (26, 100, 11, 9, "north", "tan"),
            (50, 26, 9, 11, "east", "brick"), (50, 88, 9, 11, "east", "tan"), (64, 104, 11, 9, "north", "brick"),
            (80, 104, 11, 9, "north", "tan"), (96, 104, 11, 9, "north", "brick"), (64, 8, 11, 7, "south", "tan"),
            (80, 8, 11, 7, "south", "brick"), (96, 8, 11, 7, "south", "tan")):
        k.house(t, x, z, w, d, style, floors=2, door_side=side, bed_color=t.rnd.choice(["red", "yellow", "orange", "brown"]))
    for (x, z) in ((38, 28), (38, 98), (60, 40), (60, 86), (112, 40), (112, 86)):
        if t.get(x, 1, z) is None:
            k.tree(t, x, 1, z, "oak", "oak", 6, 3)
    k.flowers(t, 6, 6, S - 7, S - 7, 0.06)
    # Long red-and-gold banners hang inside the gate passage and on the gate towers' inner faces.
    for z in (c - 4, c + 4):
        k.banner(t, 8, 12, z, "east", "red", 4, trim="yellow")
    for tz in (c - 11, c + 9):
        k.banner(t, 9, 16, tz, "east", "red", 5, trim="yellow")
    return t


# ---------------------------------------------------------------------------------------
# Thirdrema: a stone city in a valley between two cliffs
# ---------------------------------------------------------------------------------------

def thirdrema():
    W, H, D = 112, 84, 160
    t = Template(W, H, D, "thirdrema")
    rnd = t.rnd
    floor_x0, floor_x1 = 20, W - 21
    tiers = [(0, 52, 18), (52, 102, 9), (102, D, 0)]  # (z0, z1, height): castle, upper, lower tier

    def tier_height(z):
        for (z0, z1, h) in tiers:
            if z0 <= z < z1:
                return h
        return 0

    # Cliffs on both sides, forested on top
    for x in list(range(0, floor_x0)) + list(range(floor_x1 + 1, W)):
        edge = floor_x0 - 1 - x if x < floor_x0 else x - floor_x1 - 1
        for z in range(D):
            n = math.sin(z * 0.11 + x * 0.3) * 4 + math.sin(z * 0.037) * 6
            top = int(38 + n + min(edge, 8) * 2.4 - max(0, z - D + 30) * 1.2)
            top = max(top, tier_height(z) + 2)
            for y in range(0, top):
                layer = (y + int(n)) % 9
                b = "minecraft:stone" if layer < 5 else "minecraft:andesite" if layer < 7 else "minecraft:tuff"
                if edge == 0 and rnd.random() < 0.08:
                    b = "minecraft:mossy_cobblestone"
                t.set(x, y, z, b)
            t.set(x, top, z, "minecraft:grass_block", {"snowy": "false"})
            if edge > 3 and rnd.random() < 0.035:
                k.tree(t, x, top + 1, z, "spruce", "spruce", rnd.randint(6, 9), 2)
    # Terraces and retaining walls
    for (z0, z1, h) in tiers:
        for z in range(z0, z1):
            for x in range(floor_x0, floor_x1 + 1):
                for y in range(0, h):
                    t.set(x, y, z, "minecraft:stone_bricks" if (z == z1 - 1 and h) else "minecraft:dirt")
                t.set(x, h, z, "minecraft:grass_block", {"snowy": "false"})
    # Grand stairs between tiers along the central avenue
    cx = W // 2
    for (zfront, hlow, hhigh) in ((102, 0, 9), (52, 9, 18)):
        for i, y in enumerate(range(hlow + 1, hhigh + 1)):
            z = zfront + (hhigh - hlow) - i
            for x in range(cx - 4, cx + 5):
                t.set(x, y, z, "minecraft:polished_andesite_stairs", {"facing": "north", "half": "bottom", "shape": "straight",
                                                                      "waterlogged": "false"})
                for yy in range(y + 1, hhigh + 2):
                    if t.get(x, yy, z) and yy <= hhigh:
                        t.set(x, yy, z, "minecraft:air")
    # The central avenue and cross streets
    for z in range(0, D):
        h = tier_height(z)
        for x in range(cx - 3, cx + 4):
            if t.get(x, h, z) == "minecraft:grass_block":
                t.set(x, h, z, "minecraft:polished_andesite" if x in (cx - 3, cx + 3) else "minecraft:stone_bricks")
    for (zc, h) in ((70, 9), (88, 9), (118, 0), (136, 0)):
        for x in range(floor_x0, floor_x1 + 1):
            for z in range(zc - 1, zc + 2):
                if t.get(x, h, z) == "minecraft:grass_block":
                    t.set(x, h, z, "minecraft:stone_bricks")
    # The curtain wall and gate across the valley mouth
    gz = D - 8
    for x in range(0, W):
        for y in range(1, 13):
            for z in range(gz, gz + 3):
                if abs(x - cx) <= 3 and y <= 7:
                    t.set(x, y, z, "minecraft:air")
                elif t.get(x, y, z) is None or y <= 12:
                    t.set(x, y, z, "minecraft:stone_bricks")
        if x % 2 == 0:
            t.set(x, 13, gz, "minecraft:stone_bricks")
    for tx in (cx - 8, cx + 8):
        k.round_tower(t, tx, gz + 1, 4, 18, "minecraft:white_concrete", roof="minecraft:orange_terracotta", roof_height=10,
                      trim="minecraft:calcite")
    # Lower plaza: fountain, waypoint, spawn
    pz = 128
    t.disc(cx, 0, pz, 11, "minecraft:polished_andesite")
    t.ring(cx, 0, pz, 11, "minecraft:stone_bricks", width=0.5)
    k.fountain(t, cx, pz, 4, 2, block="minecraft:calcite", trim="minecraft:chiseled_quartz_block")
    k.waypoint(t, cx + 7, 0, pz + 6)
    k.marker(t, cx, 1, pz + 14, "spawn")
    # White houses with orange (and some blue) roofs, stepping up the terraces
    def town_block(x0, x1, z0, z1, h, door):
        x = x0
        while x + 7 <= x1:
            w = rnd.choice([7, 9, 9, 11])
            if x + w - 1 > x1:
                break
            d = rnd.choice([7, 9])
            style = "plaster_blue" if rnd.random() < 0.2 else "plaster"
            z = z0 if door == "south" else z1 - d + 1
            if z + d - 1 <= z1:
                _house_on(t, x, z, w, d, h, style, rnd.choice([2, 2, 3]), door)
            x += w + 2
    for (z0, z1, h) in ((104, 115, 0), (121, 133, 0), (139, 147, 0), (56, 67, 9), (73, 85, 9), (91, 99, 9)):
        if h == 0 and z0 <= pz + 11 and z1 >= pz - 11:
            town_block(floor_x0 + 1, cx - 14, z0, z1, h, "south")
            town_block(cx + 14, floor_x1 - 1, z0, z1, h, "south")
        else:
            town_block(floor_x0 + 1, cx - 5, z0, z1, h, "south")
            town_block(cx + 5, floor_x1 - 1, z0, z1, h, "south")
    # The Serpent's Apple: a back-alley bar on the lower tier
    bx0, bz0 = floor_x0 + 1, 149 - 10
    for y in range(1, 6):
        for x in range(bx0, bx0 + 12):
            for z in range(bz0, bz0 + 8):
                edge = x in (bx0, bx0 + 11) or z in (bz0, bz0 + 7)
                t.set(x, y, z, ("minecraft:dark_oak_planks" if y < 5 else "minecraft:stripped_dark_oak_log") if edge else "minecraft:air")
    t.fill(bx0, 0, bz0, bx0 + 11, 0, bz0 + 7, "minecraft:spruce_planks")
    k.gable_roof(t, bx0, bz0, bx0 + 11, bz0 + 7, 6, "minecraft:dark_oak_stairs", along_x=True)
    k.gable_fill(t, bx0, bz0, bx0 + 11, bz0 + 7, 6, "minecraft:dark_oak_planks", along_x=True)
    k.door(t, bx0 + 6, 1, bz0 + 7, "south", "dark_oak")
    k.sign(t, bx0 + 7, 3, bz0 + 8, "south", ["The", "Serpent's", "Apple"], wood="dark_oak", color="red", glow=True)
    t.fill(bx0 + 1, 1, bz0 + 1, bx0 + 7, 1, bz0 + 1, "minecraft:dark_oak_slab", {"type": "top", "waterlogged": "false"})
    for x in range(bx0 + 1, bx0 + 8, 2):
        t.set(x, 2, bz0 + 1, "minecraft:potted_red_tulip" if x % 4 == 0 else "minecraft:brewing_stand",
              {"has_bottle_0": "false", "has_bottle_1": "false", "has_bottle_2": "false"} if x % 4 else None)
    for (x, z) in ((bx0 + 9, bz0 + 2), (bx0 + 9, bz0 + 5)):
        t.set(x, 1, z, "minecraft:spruce_fence", {"waterlogged": "false"})
        t.set(x, 2, z, "minecraft:spruce_pressure_plate", {"powered": "false"})
    t.set(bx0 + 10, 1, bz0 + 1, "minecraft:barrel", {"facing": "up", "open": "false"})
    t.set(bx0 + 10, 2, bz0 + 1, "minecraft:barrel", {"facing": "up", "open": "false"})
    k.lantern(t, bx0 + 4, 4, bz0 + 4, hanging=True)
    k.lantern(t, bx0 + 8, 4, bz0 + 4, hanging=True)
    t.entity(bx0 + 8, 1, bz0 + 3, "slfrontier:pencilgon", yaw=180.0)
    t.entity(bx0 + 5, 1, bz0 + 4, "slfrontier:oikatzo", yaw=90.0)
    # The white fairy-tale castle with orange spires at the head of the valley
    cy = 18
    kx0, kx1, kz0, kz1 = cx - 18, cx + 18, 8, 36
    for y in range(cy + 1, cy + 26):
        for x in range(kx0, kx1 + 1):
            for z in range(kz0, kz1 + 1):
                edge = x in (kx0, kx1) or z in (kz0, kz1)
                if edge:
                    t.set(x, y, z, "minecraft:chiseled_quartz_block" if y % 7 == 0 else "minecraft:calcite")
                elif y in (cy + 1, cy + 13):
                    t.set(x, y, z, "minecraft:birch_planks")
                else:
                    t.set(x, y, z, "minecraft:air")
    for y in range(cy + 4, cy + 24, 5):
        for x in range(kx0 + 3, kx1 - 2, 4):
            t.set(x, y, kz1, "minecraft:glass_pane", {"waterlogged": "false"})
            t.set(x, y + 1, kz1, "minecraft:glass_pane", {"waterlogged": "false"})
    k.gable_roof(t, kx0, kz0, kx1, kz1, cy + 26, "minecraft:acacia_stairs", along_x=True)
    k.gable_fill(t, kx0, kz0, kx1, kz1, cy + 26, "minecraft:calcite", along_x=True)
    for dx in (-1, 0, 1):
        k.door(t, cx + dx, cy + 2, kz1, "south", "birch")
    for (tx, tz, r, h) in ((kx0, kz0, 5, 40), (kx1, kz0, 5, 40), (kx0, kz1, 4, 32), (kx1, kz1, 4, 32), (cx, kz0 - 2, 6, 48)):
        _castle_tower(t, tx, tz, r, cy, h)
    t.fill(cx - 4, cy, kz1 + 1, cx + 4, cy, 51, "minecraft:polished_andesite")
    for z in range(kz1 + 3, 50, 5):
        k.lamp_post(t, cx - 5, cy + 1, z, 4)
        k.lamp_post(t, cx + 5, cy + 1, z, 4)
    for bx in (cx - 7, cx + 7):
        k.banner(t, bx, cy + 18, kz1 + 1, "south", "orange", 3, trim="white")
    # Street lamps on the lower tier
    for z in range(106, 150, 8):
        k.street_lamp(t, cx - 4, 1, z, "east")
        k.street_lamp(t, cx + 4, 1, z, "west")
    for z in range(56, 100, 8):
        k.street_lamp(t, cx - 4, 10, z, "east")
        k.street_lamp(t, cx + 4, 10, z, "west")
    return t


def _house_on(t, x0, z0, w, d, base, style, floors, door_side):
    """A house standing on a terrace at height `base`."""
    sub = Template(w, floors * 4 + 8, d, f"h{x0}{z0}")
    sub.rnd = t.rnd
    k.house(sub, 0, 0, w, d, style, floors=floors, door_side=door_side, bed_color=t.rnd.choice(["white", "orange", "light_blue"]))
    for (x, y, z), (name, props, data) in sub.blocks.items():
        t.set(x0 + x, base + y, z0 + z, name, props, data)


def _castle_tower(t, cx, cz, r, base, height):
    for y in range(base + 1, base + height + 1):
        for x in range(cx - r - 1, cx + r + 2):
            for z in range(cz - r - 1, cz + r + 2):
                d = math.hypot(x - cx, z - cz)
                if r - 0.9 <= d <= r + 0.4:
                    t.set(x, y, z, "minecraft:chiseled_quartz_block" if (y - base) % 9 == 0 else "minecraft:white_concrete")
                elif d < r - 0.9:
                    t.set(x, y, z, "minecraft:air")
    for y in range(base + 6, base + height - 2, 6):
        for a in range(0, 360, 60):
            x = round(cx + r * math.cos(math.radians(a)))
            z = round(cz + r * math.sin(math.radians(a)))
            t.set(x, y, z, "minecraft:glass_pane", {"waterlogged": "false"})
    t.disc(cx, base + height, cz, r + 1.2, "minecraft:calcite")
    k.cone_roof(t, cx, cz, r + 1, base + height + 1, "minecraft:orange_terracotta", int(r * 2.6))


# ---------------------------------------------------------------------------------------
# Rabituza: the hidden nation, its giant tree, red palace and the Vorpal Colosseum
# ---------------------------------------------------------------------------------------

def rabituza():
    S = 128
    t = Template(S, 76, S, "rabituza")
    c = S // 2
    rnd = t.rnd
    _grass(t, 0, 0, S - 1, S - 1)
    tx, tz = c, c + 2
    # Paths
    k.road(t, c - 2, 0, c + 2, S - 1, "minecraft:dirt_path", None, mix=["minecraft:coarse_dirt", "minecraft:mud_bricks"])
    k.road(t, 0, c + 24, S - 1, c + 27, "minecraft:dirt_path", None, mix=["minecraft:coarse_dirt"])
    # The palace terrace, north of the tree
    px0, px1, pz0, pz1 = c - 22, c + 22, 10, 40
    t.fill(px0, 0, pz0, px1, 3, pz1, "minecraft:polished_blackstone_bricks")
    t.fill(px0 + 1, 3, pz0 + 1, px1 - 1, 3, pz1 - 1, "minecraft:dark_oak_planks")
    for i in range(1, 4):
        for x in range(c - 4, c + 5):
            t.set(x, i, pz1 + 4 - i, "minecraft:polished_blackstone_brick_stairs",
                  {"facing": "north", "half": "bottom", "shape": "straight", "waterlogged": "false"})
    # The red palace: two storeys of red walls under tiered pagoda roofs
    _pagoda(t, px0 + 3, pz0 + 3, px1 - 3, pz1 - 4, 4, 3)
    k.door(t, c, 4, pz1 - 4, "south", "dark_oak")
    k.door(t, c - 1, 4, pz1 - 4, "south", "dark_oak")
    t.fill(c - 1, 4, pz0 + 6, c, 4, pz1 - 5, "minecraft:red_carpet")
    t.fill(c - 4, 4, pz0 + 4, c + 3, 4, pz0 + 6, "minecraft:gold_block")
    t.set(c, 5, pz0 + 5, "minecraft:quartz_stairs", {"facing": "south", "half": "bottom", "shape": "straight", "waterlogged": "false"})
    t.fill(c - 1, 5, pz0 + 4, c + 1, 8, pz0 + 4, "minecraft:red_wool")
    t.entity(c, 5, pz0 + 7, "slfrontier:vysache", yaw=180.0)
    for x in (c - 6, c + 6):
        t.fill(x, 4, pz0 + 6, x, 6, pz0 + 6, "minecraft:polished_blackstone_wall")
        k.lantern(t, x, 7, pz0 + 6, soul=False)
    # The giant tree with its cherry crown
    k.giant_tree(t, tx, tz + 6, 1, 6, 44, 26, "oak", "cherry")
    # Palace plaza, waypoint and spawn
    t.disc(c, 0, pz1 + 9, 7, "minecraft:mud_bricks")
    t.ring(c, 0, pz1 + 9, 7, "minecraft:packed_mud", width=0.5)
    k.waypoint(t, c + 5, 0, pz1 + 6)
    k.marker(t, c - 3, 1, pz1 + 10, "spawn")
    # The Vorpal Colosseum
    ax, az = c + 30, c + 42
    _colosseum(t, ax, az, 21, 17)
    k.marker(t, ax, 1, az, "arena")
    # Bilac's forge
    fx0, fz0 = 8, c - 18
    t.fill(fx0, 0, fz0, fx0 + 12, 0, fz0 + 10, "minecraft:stone_bricks")
    t.fill(fx0 + 10, 1, fz0, fx0 + 12, 5, fz0 + 10, "minecraft:bricks")
    t.set(fx0 + 9, 1, fz0 + 2, "minecraft:blast_furnace", {"facing": "west", "lit": "true"})
    t.set(fx0 + 9, 1, fz0 + 5, "minecraft:lava_cauldron")
    t.set(fx0 + 9, 1, fz0 + 8, "minecraft:furnace", {"facing": "west", "lit": "true"})
    t.set(fx0 + 5, 1, fz0 + 4, "minecraft:anvil", {"facing": "east"})
    t.set(fx0 + 5, 1, fz0 + 7, "minecraft:smithing_table")
    for (px, pz) in ((fx0, fz0), (fx0, fz0 + 10)):
        t.fill(px, 1, pz, px, 4, pz, "minecraft:spruce_log", {"axis": "y"})
    t.fill(fx0, 5, fz0, fx0 + 12, 5, fz0 + 10, "minecraft:spruce_planks")
    k.gable_roof(t, fx0, fz0, fx0 + 12, fz0 + 10, 6, "minecraft:spruce_stairs", along_x=False)
    t.entity(fx0 + 5, 1, fz0 + 6, "slfrontier:bilac", yaw=90.0)
    # Burrows in grassy mounds, carrot fields and cherry trees
    for (bx, bz, r, side) in ((14, 14, 5, "south"), (26, 8, 4, "south"), (110, 14, 5, "south"), (100, 26, 4, "west"),
                              (14, 100, 5, "north"), (28, 112, 4, "north"), (12, 72, 4, "east")):
        _burrow(t, bx, bz, r, side)
    for (fx, fz) in ((30, 84), (30, 96), (6, 84)):
        t.fill(fx, 0, fz, fx + 10, 0, fz + 4, "minecraft:farmland", {"moisture": "7"})
        t.fill(fx, 1, fz, fx + 10, 1, fz + 4, "minecraft:carrots", {"age": "7"})
        t.fill(fx, 0, fz + 2, fx + 10, 0, fz + 2, "minecraft:water", {"level": "0"})
        t.fill(fx, 1, fz + 2, fx + 10, 1, fz + 2, "minecraft:air")
    for (x, z) in ((6, 40), (44, 116), (120, 50), (118, 120), (40, 60), (60, 116)):
        if t.get(x, 1, z) is None:
            k.tree(t, x, 1, z, "cherry", "cherry", 5, 3)
    for z in range(4, S - 4, 9):
        if t.get(c - 3, 1, z) is None:
            k.lamp_post(t, c - 3, 1, z, 2)
    k.flowers(t, 2, 2, S - 3, S - 3, 0.12, ["minecraft:short_grass", "minecraft:pink_petals", "minecraft:allium",
                                             "minecraft:short_grass", "minecraft:azure_bluet"])
    return t


def _pagoda(t, x0, z0, x1, z1, base, storeys):
    """Red walls, dark frames and tiered roofs that step in with each storey."""
    for s in range(storeys):
        inset = s * 3
        a0, b0, a1, b1 = x0 + inset, z0 + inset, x1 - inset, z1 - inset
        if a1 - a0 < 4 or b1 - b0 < 4:
            break
        y0 = base + s * 6
        for y in range(y0, y0 + 5):
            for x in range(a0, a1 + 1):
                for z in range(b0, b1 + 1):
                    edge = x in (a0, a1) or z in (b0, b1)
                    corner = x in (a0, a1) and z in (b0, b1)
                    if corner or (edge and (x - a0) % 6 == 0 and z in (b0, b1)) or (edge and (z - b0) % 6 == 0 and x in (a0, a1)):
                        t.set(x, y, z, "minecraft:stripped_dark_oak_log", {"axis": "y"})
                    elif edge:
                        t.set(x, y, z, "minecraft:red_terracotta" if y != y0 + 2 else "minecraft:red_stained_glass_pane",
                              {"waterlogged": "false"} if y == y0 + 2 else None)
                    elif y == y0 and s > 0:
                        t.set(x, y, z, "minecraft:dark_oak_planks")
                    else:
                        t.set(x, y, z, "minecraft:air")
        # Flared roof: an overhanging ring of upturned eaves
        ry = y0 + 5
        for ring in range(3):
            for x in range(a0 - 3 + ring, a1 + 4 - ring):
                for z in (b0 - 3 + ring, b1 + 3 - ring):
                    t.set(x, ry + ring, z, "minecraft:deepslate_tile_stairs",
                          {"facing": "south" if z < (b0 + b1) // 2 else "north", "half": "bottom", "shape": "straight",
                           "waterlogged": "false"})
            for z in range(b0 - 2 + ring, b1 + 3 - ring):
                for x in (a0 - 3 + ring, a1 + 3 - ring):
                    t.set(x, ry + ring, z, "minecraft:deepslate_tile_stairs",
                          {"facing": "east" if x < (a0 + a1) // 2 else "west", "half": "bottom", "shape": "straight",
                           "waterlogged": "false"})
        t.fill(a0, ry, b0, a1, ry, b1, "minecraft:dark_oak_planks")
        for (cx_, cz_) in ((a0 - 3, b0 - 3), (a1 + 3, b0 - 3), (a0 - 3, b1 + 3), (a1 + 3, b1 + 3)):
            t.set(cx_, ry + 1, cz_, "minecraft:gold_block")
    top_y = base + storeys * 6 + 3
    k.pyramid_roof(t, x0 + storeys * 3 - 2, z0 + storeys * 3 - 2, x1 - storeys * 3 + 2, z1 - storeys * 3 + 2, top_y,
                   "minecraft:deepslate_tiles")


def _colosseum(t, cx, cz, rx, rz):
    """An oval arena: a sand floor, five tiers of seats and an arcaded outer wall."""
    inner_x, inner_z = rx - 6, rz - 6
    for x in range(cx - rx - 2, cx + rx + 3):
        for z in range(cz - rz - 2, cz + rz + 3):
            e_floor = ((x - cx) / inner_x) ** 2 + ((z - cz) / inner_z) ** 2
            e_outer = ((x - cx) / (rx + 1)) ** 2 + ((z - cz) / (rz + 1)) ** 2
            if e_floor <= 1:
                t.set(x, 0, z, "minecraft:sand")
                continue
            if e_outer > 1:
                continue
            # 0 at the floor's edge, 1 at the outer wall
            frac = (math.sqrt(e_floor) - 1) / (math.sqrt(((rx + 1) / inner_x) ** 2) - 1)
            tier = min(5, int(frac * 5) + 1)
            t.fill(x, 0, z, x, tier, z, "minecraft:stone_bricks")
            if e_outer > 0.82:
                angle = math.degrees(math.atan2(z - cz, x - cx)) % 24
                for y in range(tier + 1, 11):
                    arch = y <= 8 and 6 <= angle <= 18
                    t.set(x, y, z, "minecraft:air" if arch else "minecraft:smooth_stone")
                t.set(x, 11, z, "minecraft:smooth_stone_slab", {"type": "bottom", "waterlogged": "false"})
    # Gates to the north and south: tunnels through the stands
    for sign in (-1, 1):
        for dz in range(inner_z - 1, rz + 3):
            z = cz + sign * dz
            for x in range(cx - 2, cx + 3):
                t.set(x, 0, z, "minecraft:smooth_sandstone")
                for y in range(1, 6):
                    t.set(x, y, z, "minecraft:air")
    # Lamps round the edge of the sand
    for a in range(0, 360, 30):
        x = round(cx + (inner_x + 1) * math.cos(math.radians(a)))
        z = round(cz + (inner_z + 1) * math.sin(math.radians(a)))
        if abs(x - cx) > 3:
            k.lamp_post(t, x, 2, z, 2)


def _burrow(t, cx, cz, r, door_facing):
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
    dx, dz = k.FACING[door_facing]
    for i in range(r - 1, r + 1):
        t.set(cx + dx * i, 1, cz + dz * i, "minecraft:air")
        t.set(cx + dx * i, 2, cz + dz * i, "minecraft:air")
    t.set(cx + dx * (r - 1), 1, cz + dz * (r - 1), "minecraft:spruce_door",
          {"facing": door_facing, "half": "lower", "hinge": "left", "open": "false", "powered": "false"})
    t.set(cx + dx * (r - 1), 2, cz + dz * (r - 1), "minecraft:spruce_door",
          {"facing": door_facing, "half": "upper", "hinge": "left", "open": "false", "powered": "false"})
    k.lantern(t, cx, 1, cz)
    t.set(cx - dz, 1, cz - dx, "minecraft:hay_block", {"axis": "y"})


# ---------------------------------------------------------------------------------------
# The Serpent Bridge: a great stone bridge over a river gorge, the Ravenous Snake's arena
# ---------------------------------------------------------------------------------------

def serpent_bridge():
    W, H, D = 84, 40, 56
    G = 16  # template layer that lines up with the natural ground
    t = Template(W, H, D, "serpent_bridge")
    rnd = t.rnd
    g0, g1 = 24, 59  # gorge across x
    cz = D // 2
    water = 5
    for x in range(W):
        for z in range(D):
            wob = int(2 * math.sin(z * 0.25) + 1.5 * math.sin(z * 0.11 + 1))
            edge = min(x - (g0 + wob), (g1 + wob) - x)  # < 0 outside the gorge
            if edge < 0:
                surface = G
            else:
                surface = max(1 + min(3, edge // 4), G - edge * 3)
            for y in range(0, surface):
                if surface == G:
                    t.set(x, y, z, "minecraft:stone" if y < G - 3 else "minecraft:dirt")
                else:
                    t.set(x, y, z, "minecraft:mossy_cobblestone" if (x * 7 + y * 3 + z) % 11 == 0 else "minecraft:stone")
            if surface == G:
                t.set(x, G, z, "minecraft:grass_block", {"snowy": "false"})
                continue
            t.set(x, surface, z, ("minecraft:gravel" if rnd.random() < 0.5 else "minecraft:sand") if surface <= water
                  else "minecraft:stone")
            for y in range(surface + 1, G + 8):
                t.set(x, y, z, "minecraft:water" if y <= water else "minecraft:air", {"level": "0"} if y <= water else None)
    # The bridge deck, its parapets and the widened arena platform in the middle
    bx0, bx1 = 12, W - 13
    mid = (bx0 + bx1) // 2
    for x in range(bx0, bx1 + 1):
        for z in range(cz - 5, cz + 6):
            t.set(x, G, z, "minecraft:polished_andesite" if z in (cz - 5, cz + 5) else "minecraft:stone_bricks")
            t.set(x, G - 1, z, "minecraft:stone_bricks")
            for y in range(G + 1, G + 6):
                t.set(x, y, z, "minecraft:air")
    t.disc(mid, G, cz, 10, "minecraft:stone_bricks")
    t.disc(mid, G - 1, cz, 10, "minecraft:stone_bricks")
    t.ring(mid, G, cz, 6, "minecraft:chiseled_stone_bricks", width=0.5)
    for x in range(bx0, bx1 + 1):
        for z in range(cz - 12, cz + 13):
            on_disc = math.hypot(x - mid, z - cz) <= 10.3
            on_deck = abs(z - cz) <= 5
            if on_disc or on_deck:
                neighbours = [(x + 1, z), (x - 1, z), (x, z + 1), (x, z - 1)]
                edge = any(not (math.hypot(nx - mid, nz - cz) <= 10.3 or (abs(nz - cz) <= 5 and bx0 <= nx <= bx1))
                           for nx, nz in neighbours)
                if edge and not (x in (bx0, bx1) and on_deck):
                    t.set(x, G + 1, z, "minecraft:stone_brick_wall")
    # Piers and arches down to the river
    for px in (mid - 16, mid, mid + 16):
        for y in range(0, G - 1):
            for x in range(px - 2, px + 3):
                for z in range(cz - 5, cz + 6):
                    t.set(x, y, z, "minecraft:stone_bricks" if y > 2 else "minecraft:mossy_stone_bricks")
    for (a, b) in ((mid - 16, mid), (mid, mid + 16), (bx0 + 6, mid - 16), (mid + 16, bx1 - 6)):
        span = b - a
        for x in range(a + 2, b - 1):
            u = (x - a) / span
            top = int(G - 2 - math.sin(u * math.pi) * (G - 7))
            for y in range(top, G - 1):
                for z in range(cz - 5, cz + 6):
                    t.set(x, y, z, "minecraft:stone_bricks")
    # Lamps, statues and ruined towers at each end
    for x in range(bx0 + 4, bx1 - 3, 8):
        if abs(x - mid) > 10:
            k.lamp_post(t, x, G + 2, cz - 5, 2, post="minecraft:stone_brick_wall")
            k.lamp_post(t, x, G + 2, cz + 5, 2, post="minecraft:stone_brick_wall")
    for (sx, face) in ((bx0 - 2, "east"), (bx1 + 2, "west")):
        for sz in (cz - 8, cz + 8):
            k.round_tower(t, sx, sz, 3, 9 + rnd.randint(0, 5), "minecraft:stone_bricks", windows=False)
            for _ in range(10):
                y = rnd.randint(G + 6, G + 14)
                t.set(sx + rnd.randint(-3, 3), y, sz + rnd.randint(-3, 3), "minecraft:air")
    k.waypoint(t, bx0 - 6, G, cz)
    k.marker(t, 0, G, 0, "ground")
    k.marker(t, mid, G + 1, cz, "arena")
    k.marker(t, bx0 - 3, G + 1, cz, "spawn")
    for x in range(W):
        for z in range(D):
            if t.get(x, G, z) == "minecraft:grass_block" and t.get(x, G + 1, z) is None and rnd.random() < 0.15:
                t.set(x, G + 1, z, rnd.choice(["minecraft:short_grass", "minecraft:fern", "minecraft:short_grass"]))
    return t


ALL = {
    "town/firstia": firstia,
    "town/secondil": secondil,
    "town/thirdrema": thirdrema,
    "town/rabituza": rabituza,
    "landmark/serpent_bridge": serpent_bridge,
}

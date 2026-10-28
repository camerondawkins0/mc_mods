"""Building blocks for the town and landmark templates in tools/structures.py.

Coordinates are template-local: x/z across the footprint, y up from the template floor
(y = 0 is the ground layer). Everything writes into a structures.Template.
"""
import math

FACING = {"north": (0, -1), "south": (0, 1), "west": (-1, 0), "east": (1, 0)}
OPPOSITE = {"north": "south", "south": "north", "east": "west", "west": "east"}


def stairs(name, facing, half="bottom"):
    return (name, {"facing": facing, "half": half, "shape": "straight", "waterlogged": "false"})


def slab(name, kind="bottom"):
    return (name, {"type": kind, "waterlogged": "false"})


def log(name, axis="y"):
    return (name, {"axis": axis})


def lantern(t, x, y, z, soul=False, hanging=False):
    t.set(x, y, z, "minecraft:soul_lantern" if soul else "minecraft:lantern", {"hanging": "true" if hanging else "false",
                                                                                "waterlogged": "false"})


def lamp_post(t, x, y, z, height=3, soul=False, post="minecraft:dark_oak_fence"):
    for i in range(height):
        t.set(x, y + i, z, post, {"waterlogged": "false"})
    lantern(t, x, y + height, z, soul)


def street_lamp(t, x, y, z, arm="east", height=4):
    """A taller lamp with an arm and a hanging lantern."""
    for i in range(height):
        t.set(x, y + i, z, "minecraft:dark_oak_fence", {"waterlogged": "false"})
    dx, dz = FACING[arm]
    t.set(x, y + height, z, "minecraft:dark_oak_fence", {"waterlogged": "false"})
    t.set(x + dx, y + height, z + dz, "minecraft:dark_oak_fence", {"waterlogged": "false"})
    lantern(t, x + dx, y + height - 1, z + dz, hanging=True)


def door(t, x, y, z, facing, wood="oak"):
    t.set(x, y, z, f"minecraft:{wood}_door", {"facing": facing, "half": "lower", "hinge": "left", "open": "false", "powered": "false"})
    t.set(x, y + 1, z, f"minecraft:{wood}_door", {"facing": facing, "half": "upper", "hinge": "left", "open": "false", "powered": "false"})


def bed(t, x, y, z, facing, color="red"):
    dx, dz = FACING[facing]
    t.set(x, y, z, f"minecraft:{color}_bed", {"facing": facing, "part": "foot", "occupied": "false"})
    t.set(x + dx, y, z + dz, f"minecraft:{color}_bed", {"facing": facing, "part": "head", "occupied": "false"})


def window(t, x, y, z, axis_x=True, pane="minecraft:glass_pane"):
    t.set(x, y, z, pane, {"waterlogged": "false"})


def banner(t, x, y, z, facing, color="red", count=3, trim=None):
    """A long hanging banner, like the canon city banners: wall banners stacked down a wall.
    (x, y, z) is the air cell in front of the wall, facing away from it."""
    for i in range(count):
        c = trim if trim and i == count - 1 else color
        t.set(x, y - 2 * i, z, f"minecraft:{c}_wall_banner", {"facing": facing})


def sign(t, x, y, z, facing, lines, wood="dark_oak", color="black", glow=False):
    msgs = [f'{{"text":"{line}"}}' for line in (list(lines) + ["", "", "", ""])[:4]]
    text = {"messages": msgs, "color": color, "has_glowing_text": 1 if glow else 0}
    t.set(x, y, z, f"minecraft:{wood}_wall_sign", {"facing": facing, "waterlogged": "false"},
          {"id": "minecraft:sign", "front_text": text, "back_text": {"messages": ['""'] * 4, "color": "black",
                                                                     "has_glowing_text": 0}, "is_waxed": 1})


def marker(t, x, y, z, name):
    """A data marker read by Landmarks when the template is placed (then removed)."""
    t.set(x, y, z, "minecraft:structure_block", {"mode": "data"},
          {"id": "minecraft:structure_block", "mode": "DATA", "metadata": name, "name": "", "author": "slfrontier",
           "posX": 0, "posY": 1, "posZ": 0, "sizeX": 0, "sizeY": 0, "sizeZ": 0, "rotation": "NONE", "mirror": "NONE",
           "ignoreEntities": 1, "powered": 0, "showair": 0, "showboundingbox": 0, "integrity": 1.0, "seed": 0})


def waypoint(t, x, y, z):
    """A waypoint crystal on a little stone plinth."""
    t.set(x, y, z, "minecraft:polished_andesite")
    t.set(x, y + 1, z, "slfrontier:waypoint_crystal")


# ---------------------------------------------------------------------------------------
# Walls and towers
# ---------------------------------------------------------------------------------------

def crenellate(t, x, y, z, block):
    t.set(x, y, z, block)


def wall_line(t, x0, z0, x1, z1, height, block, cap=None, walkway=True, thick=2, inner_dir=None):
    """A straight curtain wall from (x0, z0) to (x1, z1) along x or z, `thick` blocks deep
    (growing toward inner_dir), with crenellations on the outer edge and a walkway."""
    if x0 == x1:
        span = range(min(z0, z1), max(z0, z1) + 1)
        cells = [(x0, z) for z in span]
        step = (1, 0) if inner_dir is None else inner_dir
    else:
        span = range(min(x0, x1), max(x0, x1) + 1)
        cells = [(x, z0) for x in span]
        step = (0, 1) if inner_dir is None else inner_dir
    for i, (x, z) in enumerate(cells):
        for k in range(thick):
            for y in range(1, height + 1):
                t.set(x + step[0] * k, y, z + step[1] * k, block)
        # merlons on the outer edge
        if i % 2 == 0:
            t.set(x, height + 1, z, cap or block)
        if walkway:
            t.set(x + step[0] * (thick - 1), height + 1, z + step[1] * (thick - 1), *slab("minecraft:stone_brick_slab"))


def square_tower(t, x0, z0, size, height, block, roof=None, trim=None, floors=3):
    x1, z1 = x0 + size - 1, z0 + size - 1
    for y in range(1, height + 1):
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                edge = x in (x0, x1) or z in (z0, z1)
                t.set(x, y, z, (trim if trim and y % 6 == 0 else block) if edge else "minecraft:air")
    # floors and arrow slits
    for f in range(1, floors):
        fy = height * f // floors
        t.fill(x0 + 1, fy, z0 + 1, x1 - 1, fy, z1 - 1, "minecraft:spruce_planks")
        mid = (x0 + x1) // 2, (z0 + z1) // 2
        for (wx, wz) in ((mid[0], z0), (mid[0], z1), (x0, mid[1]), (x1, mid[1])):
            t.set(wx, fy + 2, wz, "minecraft:air")
    # top: platform with merlons, or a pyramid roof
    t.fill(x0, height, z0, x1, height, z1, block)
    if roof:
        pyramid_roof(t, x0 - 1, z0 - 1, x1 + 1, z1 + 1, height + 1, roof)
    else:
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if (x in (x0, x1) or z in (z0, z1)) and (x + z) % 2 == 0:
                    t.set(x, height + 1, z, block)


def round_tower(t, cx, cz, r, height, block, roof=None, roof_height=None, trim=None, windows=True):
    for y in range(1, height + 1):
        for x in range(cx - r - 1, cx + r + 2):
            for z in range(cz - r - 1, cz + r + 2):
                d = math.hypot(x - cx, z - cz)
                if r - 0.9 <= d <= r + 0.4:
                    t.set(x, y, z, trim if trim and y % 7 == 0 else block)
                elif d < r - 0.9:
                    t.set(x, y, z, "minecraft:air")
    if windows:
        for y in range(4, height - 1, 5):
            for a in range(0, 360, 90):
                x = round(cx + r * math.cos(math.radians(a + 45)))
                z = round(cz + r * math.sin(math.radians(a + 45)))
                t.set(x, y, z, "minecraft:glass_pane", {"waterlogged": "false"})
    t.disc(cx, height, cz, r, block)
    if roof:
        cone_roof(t, cx, cz, r + 1, height + 1, roof, roof_height or int(r * 2.2))
    else:
        for a in range(0, 360, 30):
            x = round(cx + r * math.cos(math.radians(a)))
            z = round(cz + r * math.sin(math.radians(a)))
            t.set(x, height + 1, z, block)


def cone_roof(t, cx, cz, r, y0, block, height):
    for i in range(height):
        rr = r * (1 - i / height)
        if rr < 0.5:
            t.set(cx, y0 + i, cz, block)
            continue
        t.disc(cx, y0 + i, cz, rr, block)
    t.set(cx, y0 + height, cz, "minecraft:lightning_rod", {"facing": "up", "powered": "false", "waterlogged": "false"})


def pyramid_roof(t, x0, z0, x1, z1, y0, block):
    i = 0
    while x0 + i <= x1 - i and z0 + i <= z1 - i:
        t.walls(x0 + i, y0 + i, z0 + i, x1 - i, y0 + i, z1 - i, block)
        i += 1


def gable_roof(t, x0, z0, x1, z1, y0, stair, ridge=None, along_x=True, overhang=1):
    """A pitched roof of stairs over the rectangle, ridge running along x (or z)."""
    x0, z0, x1, z1 = x0 - overhang, z0 - overhang, x1 + overhang, z1 + overhang
    if along_x:
        depth = z1 - z0 + 1
        for i in range((depth + 1) // 2):
            y = y0 + i
            za, zb = z0 + i, z1 - i
            for x in range(x0, x1 + 1):
                if za == zb:
                    t.set(x, y, za, *slab(ridge or stair.replace("_stairs", "_slab")))
                else:
                    t.set(x, y, za, *stairs(stair, "south"))
                    t.set(x, y, zb, *stairs(stair, "north"))
        return y0 + (depth + 1) // 2
    width = x1 - x0 + 1
    for i in range((width + 1) // 2):
        y = y0 + i
        xa, xb = x0 + i, x1 - i
        for z in range(z0, z1 + 1):
            if xa == xb:
                t.set(xa, y, z, *slab(ridge or stair.replace("_stairs", "_slab")))
            else:
                t.set(xa, y, z, *stairs(stair, "east"))
                t.set(xb, y, z, *stairs(stair, "west"))
    return y0 + (width + 1) // 2


def gable_fill(t, x0, z0, x1, z1, y0, block, along_x=True):
    """Fills the triangular gable ends under a gable_roof."""
    if along_x:
        depth = z1 - z0 + 1
        for i in range((depth + 1) // 2):
            for x in (x0, x1):
                for z in range(z0 + i + 1, z1 - i):
                    t.set(x, y0 + i, z, block)
    else:
        width = x1 - x0 + 1
        for i in range((width + 1) // 2):
            for z in (z0, z1):
                for x in range(x0 + i + 1, x1 - i):
                    t.set(x, y0 + i, z, block)


# ---------------------------------------------------------------------------------------
# Houses
# ---------------------------------------------------------------------------------------

STYLES = {
    # wall, frame, roof stairs, floor, window, door wood, base
    "timber": dict(wall="minecraft:white_terracotta", frame="minecraft:stripped_dark_oak_log", roof="minecraft:spruce_stairs",
                   floor="minecraft:spruce_planks", door="spruce", base="minecraft:cobblestone"),
    "timber_red": dict(wall="minecraft:smooth_sandstone", frame="minecraft:stripped_spruce_log", roof="minecraft:dark_oak_stairs",
                       floor="minecraft:oak_planks", door="oak", base="minecraft:stone_bricks"),
    "brick": dict(wall="minecraft:cut_sandstone", frame="minecraft:chiseled_sandstone", roof="minecraft:brick_stairs",
                  floor="minecraft:spruce_planks", door="spruce", base="minecraft:sandstone"),
    "tan": dict(wall="minecraft:smooth_sandstone", frame="minecraft:stripped_oak_log", roof="minecraft:spruce_stairs",
                floor="minecraft:oak_planks", door="oak", base="minecraft:cut_sandstone"),
    "plaster": dict(wall="minecraft:white_concrete", frame="minecraft:calcite", roof="minecraft:acacia_stairs",
                    floor="minecraft:birch_planks", door="birch", base="minecraft:polished_andesite"),
    "plaster_blue": dict(wall="minecraft:white_concrete", frame="minecraft:calcite",
                         roof="minecraft:waxed_oxidized_cut_copper_stairs", floor="minecraft:birch_planks", door="birch",
                         base="minecraft:polished_andesite"),
    "palace": dict(wall="minecraft:red_terracotta", frame="minecraft:stripped_dark_oak_log", roof="minecraft:deepslate_tile_stairs",
                   floor="minecraft:dark_oak_planks", door="dark_oak", base="minecraft:polished_blackstone_bricks"),
}


def house(t, x0, z0, w, d, style="timber", floors=2, door_side="south", bed_color="red", furnish=True, h=4, roof_along=None):
    """A framed house with a pitched roof, door, windows and a bit of furniture."""
    s = STYLES[style]
    x1, z1 = x0 + w - 1, z0 + d - 1
    top = floors * h
    t.fill(x0, 0, z0, x1, 0, z1, s["base"])
    for y in range(1, top + 1):
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                edge = x in (x0, x1) or z in (z0, z1)
                corner = x in (x0, x1) and z in (z0, z1)
                if corner:
                    t.set(x, y, z, *log(s["frame"])) if s["frame"].endswith("_log") else t.set(x, y, z, s["frame"])
                elif edge:
                    floor_line = y % h == 0
                    if floor_line and s["frame"].endswith("_log"):
                        axis = "x" if z in (z0, z1) else "z"
                        t.set(x, y, z, *log(s["frame"], axis))
                    else:
                        t.set(x, y, z, s["base"] if y == 1 else s["wall"])
                else:
                    t.set(x, y, z, s["floor"] if y % h == 0 else "minecraft:air")
    # windows
    for f in range(floors):
        wy = f * h + 2
        for x in range(x0 + 2, x1 - 1, 3):
            for z in (z0, z1):
                t.set(x, wy, z, "minecraft:glass_pane", {"waterlogged": "false"})
        for z in range(z0 + 2, z1 - 1, 3):
            for x in (x0, x1):
                t.set(x, wy, z, "minecraft:glass_pane", {"waterlogged": "false"})
    # door
    dx, dz = FACING[door_side]
    if door_side in ("south", "north"):
        door_x, door_z = (x0 + x1) // 2, z1 if door_side == "south" else z0
    else:
        door_x, door_z = x1 if door_side == "east" else x0, (z0 + z1) // 2
    door(t, door_x, 1, door_z, door_side, s["door"])
    # roof
    along = roof_along if roof_along is not None else (w >= d)
    gable_roof(t, x0, z0, x1, z1, top + 1, s["roof"], along_x=along)
    gable_fill(t, x0, z0, x1, z1, top + 1, s["wall"], along_x=along)
    if furnish and w >= 5 and d >= 5:
        bed(t, x0 + 1, 1, z0 + 1, "south" if d > 4 else "east", bed_color)
        t.set(x1 - 1, 1, z0 + 1, "minecraft:crafting_table")
        t.set(x1 - 1, 1, z1 - 1, "minecraft:barrel", {"facing": "up", "open": "false"})
        lantern(t, (x0 + x1) // 2, h - 1, (z0 + z1) // 2, hanging=True)
        if floors > 1:
            # ladder up to the next floor
            t.fill(x0 + 1, 1, z1 - 1, x0 + 1, h, z1 - 1, "minecraft:ladder", {"facing": "north", "waterlogged": "false"})
            t.set(x0 + 1, h, z1 - 1, "minecraft:ladder", {"facing": "north", "waterlogged": "false"})
    return door_x + dx, door_z + dz


# ---------------------------------------------------------------------------------------
# Decorations
# ---------------------------------------------------------------------------------------

def fountain(t, cx, cz, r=4, tiers=2, block="minecraft:stone_bricks", trim="minecraft:chiseled_stone_bricks"):
    t.disc(cx, 0, cz, r + 0.5, block)
    t.ring(cx, 1, cz, r, "minecraft:stone_brick_wall" if "stone" in block else block, width=0.5)
    t.disc(cx, 1, cz, r - 0.7, "minecraft:water", {"level": "0"})
    for i in range(1, tiers + 1):
        rr = max(1, r - 1.5 * i)
        t.disc(cx, i + 1, cz, rr, trim if i == tiers else block)
        t.set(cx, i + 1, cz, trim)
    t.set(cx, tiers + 2, cz, "minecraft:water", {"level": "0"})


def market_stall(t, x, z, color="red", facing="south"):
    for (px, pz) in ((x, z), (x + 3, z), (x, z + 2), (x + 3, z + 2)):
        for y in (1, 2):
            t.set(px, y, pz, "minecraft:spruce_fence", {"waterlogged": "false"})
    t.fill(x, 3, z, x + 3, 3, z + 2, f"minecraft:{color}_wool")
    t.fill(x + 1, 1, z + (2 if facing == "south" else 0), x + 2, 1, z + (2 if facing == "south" else 0),
           "minecraft:barrel", {"facing": "up", "open": "false"})
    for (px, item) in ((x + 1, "minecraft:melon"), (x + 2, "minecraft:pumpkin")):
        t.set(px, 1, z + 1, item)


def tree(t, x, y, z, log_name="oak", leaves="oak", height=5, radius=2):
    leaf = (f"minecraft:{leaves}_leaves", {"persistent": "true", "distance": "1", "waterlogged": "false"})
    for i in range(height):
        t.set(x, y + i, z, f"minecraft:{log_name}_log", {"axis": "y"})
    for dy in range(-1, 2):
        rr = radius + (0 if dy < 1 else -1)
        for dx in range(-rr, rr + 1):
            for dz in range(-rr, rr + 1):
                if abs(dx) + abs(dz) <= rr + 1 and (dx, dz) != (0, 0) or dy == 1:
                    if t.get(x + dx, y + height + dy - 1, z + dz) is None:
                        t.set(x + dx, y + height + dy - 1, z + dz, *leaf)


def giant_tree(t, cx, cz, y0, trunk_r, height, canopy_r, log_name="oak", leaves="oak", seed=0, roots=True):
    """A colossal tree: flared trunk, roots, a few great branches and layered canopy blobs."""
    rnd = t.rnd
    wood = (f"minecraft:{log_name}_wood", {"axis": "y"})
    leaf = (f"minecraft:{leaves}_leaves", {"persistent": "true", "distance": "1", "waterlogged": "false"})
    for y in range(height):
        flare = trunk_r + max(0, 3 - y) * 0.9
        r = flare * (1 - 0.35 * y / height)
        for x in range(int(cx - r - 1), int(cx + r + 2)):
            for z in range(int(cz - r - 1), int(cz + r + 2)):
                if math.hypot(x - cx, z - cz) <= r:
                    t.set(x, y0 + y, z, *wood)
    if roots:
        for a in range(0, 360, 45):
            ang = math.radians(a + rnd.uniform(-15, 15))
            length = trunk_r + rnd.randint(4, 8)
            for i in range(int(length)):
                x = round(cx + math.cos(ang) * (trunk_r + i))
                z = round(cz + math.sin(ang) * (trunk_r + i))
                yy = y0 + max(0, 2 - i // 2)
                t.set(x, yy, z, *wood)
                t.set(x, y0 - 1, z, *wood)
    blobs = [(cx, y0 + height, cz, canopy_r)]
    for k in range(6):
        ang = math.radians(k * 60 + rnd.uniform(-20, 20))
        by = y0 + int(height * rnd.uniform(0.55, 0.85))
        length = canopy_r * rnd.uniform(0.6, 0.95)
        steps = int(length)
        x = z = 0
        for i in range(steps):
            x = round(cx + math.cos(ang) * i)
            z = round(cz + math.sin(ang) * i)
            y = by + i // 3
            t.set(x, y, z, (f"minecraft:{log_name}_wood", {"axis": "y"}))
            t.set(x, y + 1, z, (f"minecraft:{log_name}_wood", {"axis": "y"}))
        blobs.append((x, by + steps // 3 + 1, z, canopy_r * rnd.uniform(0.45, 0.6)))
    for (bx, by, bz, br) in blobs:
        for x in range(int(bx - br - 1), int(bx + br + 2)):
            for z in range(int(bz - br - 1), int(bz + br + 2)):
                for y in range(int(by - br * 0.5), int(by + br * 0.6) + 1):
                    d = math.sqrt((x - bx) ** 2 + ((y - by) * 1.7) ** 2 + (z - bz) ** 2)
                    if d <= br and rnd.random() < (0.92 if d < br - 1 else 0.55) and t.get(x, y, z) is None:
                        t.set(x, y, z, *leaf)


def flowers(t, x0, z0, x1, z1, chance=0.2, kinds=None, ground=("minecraft:grass_block",)):
    kinds = kinds or ["minecraft:short_grass", "minecraft:dandelion", "minecraft:poppy", "minecraft:cornflower",
                      "minecraft:oxeye_daisy", "minecraft:short_grass", "minecraft:short_grass"]
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if t.get(x, 0, z) in ground and t.get(x, 1, z) is None and t.rnd.random() < chance:
                t.set(x, 1, z, t.rnd.choice(kinds))


def road(t, x0, z0, x1, z1, block="minecraft:stone_bricks", edge="minecraft:polished_andesite", mix=None, y=0):
    for x in range(min(x0, x1), max(x0, x1) + 1):
        for z in range(min(z0, z1), max(z0, z1) + 1):
            on_edge = x in (min(x0, x1), max(x0, x1)) and x0 != x1 and abs(x1 - x0) < abs(z1 - z0) \
                or z in (min(z0, z1), max(z0, z1)) and z0 != z1 and abs(z1 - z0) < abs(x1 - x0)
            b = edge if (on_edge and edge) else block
            if mix and t.rnd.random() < 0.25:
                b = t.rnd.choice(mix)
            t.set(x, y, z, b)

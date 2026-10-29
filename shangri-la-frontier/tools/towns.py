"""The hand-placed towns and landmarks of the Frontier, built from tools/townkit.py.

Town templates don't carve terrain themselves: Landmarks levels and clears the footprint
before placing them, so only real blocks are stored (air is dropped at the end). The Serpent
Bridge sits partly below ground; its "ground" marker tells Landmarks which template layer is
the surface.

Fixed points other code relies on (template-local x, y, z):
  * gates/road exits where the world's roads join: Firstia (0|111, z=56), Secondil (0|127, z=64),
    Thirdrema's south gate (x=56, z=159), Rabituza's roads (x=64 N-S, z=88..91 E-W), the
    Serpent Bridge deck (z=28, x=0..83), the goblin camp's west entrance (x=0, z=24);
  * the data markers ("spawn", "arena", "ground") and the NPCs.
Every template is checked with townkit.audit() as it is built: water that could spill, blocks
that would pop off, blocked roads/doorways and stuck NPCs all fail the build.
"""
import math

from structures import Template
import townkit as k

MAIN_PAVING = ["minecraft:stone_bricks", "minecraft:cobblestone", "minecraft:andesite", "minecraft:cracked_stone_bricks",
               "minecraft:mossy_stone_bricks", "minecraft:polished_andesite"]
LANE = ["minecraft:dirt_path", "minecraft:coarse_dirt", "minecraft:gravel", "minecraft:cobblestone", "minecraft:packed_mud"]


def _grass(t, x0, z0, x1, z1, y=0):
    t.fill(x0, y, z0, x1, y, z1, "minecraft:grass_block", {"snowy": "false"})


def _finish(t, ground_y=0, strict=True, entrances=None):
    """Drops stored air above the ground (Landmarks clears the site anyway) and audits; the
    `entrances` (edge columns where the world's roads arrive) must lead to the markers and NPCs."""
    t.ground_y = ground_y
    t.pruned = k.prune(t, ground_y)
    problems = k.audit(t, ground_y, entrances)
    for p in [p for p, v in t.blocks.items() if v[0] == "minecraft:air" and p[1] > ground_y]:
        del t.blocks[p]
    if problems and strict:
        raise AssertionError("template problems:\n  " + "\n  ".join(problems))
    return t


STATIONS = []  # (template, kind, x, y, z, facing) for the record


def _station(t, x, y, z, kind, facing):
    """One of the Frontier crafting stations (frontier_forge, alchemy_table, frontier_workbench),
    facing the walkway players arrive from. Clears whatever furniture stood there."""
    for dy in (0, 1):
        cur = t.get(x, y + dy, z)
        if dy and cur and not cur.endswith("_carpet"):
            t.set(x, y + dy, z, "minecraft:air")
    t.set(x, y, z, f"slfrontier:{kind}", {"facing": facing})
    STATIONS.append((t.size, kind, x, y, z, facing))


def _lamps_along(t, cells, every=8, height=4, arm=None, y=1, double=False, post="minecraft:dark_oak_fence",
                 base="minecraft:stone_brick_wall", soul=False):
    """Street lamps at free cells along a line (skipping anything in the way)."""
    for i, (x, z) in enumerate(cells):
        if i % every:
            continue
        if k.free(t, x, z, y, height + 2) and all(k.is_air(t, x + d[0], y + height - 1, z + d[1]) for d in k.FACING.values()):
            if arm:
                k.street_lamp(t, x, y, z, arm, height, post=post, base=base, double=double, soul=soul)
            else:
                k.lamp_post(t, x, y, z, height - 1, soul=soul, post=post, base=base)


def _scatter_trees(t, spots, kinds=("oak", "birch"), y=1, size=(5, 7)):
    for (x, z) in spots:
        if k.free(t, x, z, y, 6, margin=1) and t.get(x, y - 1, z) in ("minecraft:grass_block", "minecraft:podzol",
                                                                       "minecraft:moss_block", "minecraft:dirt"):
            kind = t.rnd.choice(kinds)
            k.tree(t, x, y, z, kind, kind if kind != "birch" else "birch", t.rnd.randint(*size), 2)


# ---------------------------------------------------------------------------------------
# Firstia: the walled starting town
# ---------------------------------------------------------------------------------------

def firstia():
    S = 112
    t = Template(S, 48, S, "firstia")
    c = S // 2
    t.rnd.seed("firstia")
    _grass(t, 0, 0, S - 1, S - 1)
    # Roads first, so everything else keeps off them.
    k.road(t, 0, c - 2, S - 1, c + 2, "minecraft:stone_bricks", "minecraft:polished_andesite", mix=MAIN_PAVING, chance=0.35)
    k.road(t, c - 2, 8, c + 2, S - 9, "minecraft:stone_bricks", "minecraft:polished_andesite", mix=MAIN_PAVING, chance=0.35)
    for z in (30, 82):
        k.road(t, 8, z - 1, S - 9, z + 1, "minecraft:dirt_path", "minecraft:cobblestone", mix=LANE, chance=0.3)
    # Curtain wall (corbelled parapet, battered foot, buttresses) with towers
    gates = ((c - 8, c + 8),)
    k.curtain_wall(t, 4, 4, S - 5, 4, 8, "firstia", "north", gaps=(), buttress=12)
    k.curtain_wall(t, 4, S - 5, S - 5, S - 5, 8, "firstia", "south", gaps=(), buttress=12)
    k.curtain_wall(t, 4, 4, 4, S - 5, 8, "firstia", "west", gaps=gates, buttress=12)
    k.curtain_wall(t, S - 5, 4, S - 5, S - 5, 8, "firstia", "east", gaps=gates, buttress=12)
    for (tx, tz) in ((6, 6), (S - 7, 6), (6, S - 7), (S - 7, S - 7)):
        k.round_tower(t, tx, tz, 5, 14, "firstia", roof_h=13, flag=("blue", [("stripe_center", "white"), ("border", "yellow")]))
    for (tx, tz, lo) in ((28, 1, 4), (78, 1, 4), (28, S - 8, S - 7), (78, S - 8, S - 7)):
        _wall_tower(t, tx, tz, 7, 12, 8, "x", "firstia", lo, lo + 2)
    for (tx, tz, lo) in ((1, 26, 4), (1, 78, 4), (S - 8, 26, S - 7), (S - 8, 78, S - 7)):
        _wall_tower(t, tx, tz, 7, 12, 8, "z", "firstia", lo, lo + 2)
    for (gx, out) in ((4, "west"), (S - 5, "east")):
        k.gatehouse(t, gx, c, out, "firstia", passage=5, depth=8, height=11, tower=6, tower_h=16,
                    banner_colors=("blue", "white"), patterns=[("stripe_center", "white"), ("border", "yellow")])
    for (x, z, d) in ((14, 7, "east"), (S - 15, 7, "west"), (14, S - 8, "east"), (S - 15, S - 8, "west")):
        k.wall_stairs(t, x, z, 8, d)
    # The fountain plaza (the crossroads around the fountain are open paving, not road)
    for x in range(c - 8, c + 9):
        for z in range(c - 8, c + 9):
            if math.hypot(x - c, z - c) <= 7.5:
                k.unreserve(t, x, z, x, z)
    k.pave_disc(t, c, c, 15, ["minecraft:stone_bricks", "minecraft:stone_bricks", "minecraft:polished_andesite",
                              "minecraft:andesite", "minecraft:cracked_stone_bricks"])
    t.ring(c, 0, c, 15, "minecraft:chiseled_stone_bricks", width=0.5)
    t.ring(c, 0, c, 10, "minecraft:polished_andesite", width=0.5)
    k.fountain(t, c, c, 5, block="minecraft:stone_bricks", rim="minecraft:polished_andesite",
               floor="minecraft:prismarine_bricks", trim="minecraft:chiseled_stone_bricks")
    for a in range(0, 360, 45):
        x = round(c + 12 * math.cos(math.radians(a + 22.5)))
        z = round(c + 12 * math.sin(math.radians(a + 22.5)))
        k.lamp_post(t, x, 1, z, 3, base="minecraft:stone_brick_wall")
    for a in (45, 135, 225, 315):
        bx = round(c + 9 * math.cos(math.radians(a)))
        bz = round(c + 9 * math.sin(math.radians(a)))
        face = k._toward(bx, bz, c, c)
        k.bench(t, bx, bz, face, 2, "spruce")
    for (px, pz) in ((c - 14, c - 9), (c + 12, c - 9), (c - 14, c + 7), (c + 12, c + 7)):
        k.planter(t, px, pz, px + 2, pz + 2, "minecraft:stone_bricks", bush_kind="azalea")
    t.entity(c, 1, c + 8, "slfrontier:frontier_guide", yaw=180.0)
    k.waypoint(t, c + 9, 0, c - 9)
    k.marker(t, c, 1, c + 10, "spawn")
    k.reserve(t, c - 1, c + 7, c + 1, c + 11)
    # crafting stations on the plaza, either side of where newcomers arrive
    _station(t, c - 8, 1, c + 8, "frontier_workbench", "east")
    _station(t, c + 8, 1, c + 8, "alchemy_table", "west")
    # North-west: houses on the lane and the inn on the main road
    k.building(t, 14, 15, 13, 9, "firstia", floors=2, door_side="south", jetty=1, dormers=True, bed_color="blue")
    k.building(t, 30, 13, 9, 13, "firstia", floors=3, door_side="south", jetty=1, along="z", bed_color="green",
               overrides={"infill": ["minecraft:calcite"], "frame": "minecraft:stripped_spruce_log",
                          "roof": "minecraft:dark_oak_stairs", "roof_full": "minecraft:dark_oak_planks",
                          "ridge": "minecraft:spruce_slab"})
    k.building(t, 42, 14, 9, 11, "firstia", floors=2, door_side="east", jetty=1, along="z", bed_color="yellow",
               overrides={"infill": ["minecraft:smooth_sandstone"], "roof": "minecraft:mud_brick_stairs",
                          "roof_full": "minecraft:mud_bricks", "ridge": "minecraft:mud_brick_slab"})
    k.building(t, 10, 35, 21, 13, "firstia", floors=3, ground_h=5, h=4, door_side="south", jetty=1, cross=True,
               dormers=True, use="inn", sign=["The Prancing", "Pony Inn"], bed_color="blue", back_door=True,
               overrides={"roof": "minecraft:deepslate_tile_stairs", "roof_full": "minecraft:deepslate_tiles",
                          "ridge": "minecraft:deepslate_tile_slab"})
    _forge_pavilion(t, 34, 40, 9, 9)
    # North-east: the chapel with its bell tower, spire and churchyard
    _chapel(t, 61, 10)
    k.building(t, 62, 33, 11, 9, "firstia", floors=2, door_side="south", jetty=1, bed_color="orange", use="shop",
               sign=["Bakery"])
    k.building(t, 76, 34, 11, 11, "firstia", floors=2, door_side="south", jetty=1, cross=True, bed_color="purple",
               use="shop", sign=["Tailor"],
               overrides={"infill": ["minecraft:calcite"]})
    k.building(t, 90, 33, 11, 13, "firstia", floors=2, door_side="west", jetty=1, along="z", dormers=True,
               bed_color="cyan", overrides={"roof": "minecraft:dark_oak_stairs", "roof_full": "minecraft:dark_oak_planks"})
    # South-west: houses round a well and kitchen gardens
    k.building(t, 12, 62, 13, 9, "firstia", floors=2, door_side="north", jetty=1, dormers=True, bed_color="lime")
    k.building(t, 28, 62, 9, 11, "firstia", floors=2, door_side="north", jetty=1, along="z", bed_color="brown",
               use="shop", sign=["Apothecary"],
               overrides={"infill": ["minecraft:smooth_sandstone"]})
    k.well(t, 42, 70)
    _gardens(t, 12, 75, 36, 79)
    k.building(t, 12, 86, 13, 9, "firstia", floors=2, door_side="north", jetty=1, bed_color="light_blue",
               overrides={"infill": ["minecraft:calcite"], "roof": "minecraft:dark_oak_stairs",
                          "roof_full": "minecraft:dark_oak_planks"})
    k.building(t, 28, 86, 11, 13, "firstia", floors=3, door_side="north", jetty=1, along="z", bed_color="pink")
    k.building(t, 42, 86, 9, 11, "firstia", floors=2, door_side="east", jetty=1, along="z", bed_color="gray",
               overrides={"infill": ["minecraft:smooth_sandstone"]})
    # South-east: the market square and the Adventurers' Guild
    for x in range(62, 82):
        for z in range(62, 80):
            if math.hypot(x - c, z - c) > 15.5 and not k.is_reserved(t, x, z):
                k.put(t, x, 0, z, ["minecraft:cobblestone", "minecraft:stone_bricks", "minecraft:andesite",
                                   "minecraft:mossy_cobblestone"])
    for i, (x, z, f) in enumerate(((66, 64, "south"), (71, 64, "south"), (76, 64, "south"),
                                   (66, 74, "north"), (71, 74, "north"), (76, 74, "north"))):
        k.market_stall(t, x, z, ["red", "yellow", "blue", "lime", "orange", "cyan"][i], f, stripe="white")
    k.reserve(t, 62, 68, 82, 72)
    k.building(t, 84, 61, 17, 17, "firstia", floors=2, ground_h=5, h=5, door_side="west", jetty=1, roof_kind="hip",
               cross=True, use="guild", sign=["Adventurers'", "Guild"], bed_color="red",
               overrides={"roof": "minecraft:deepslate_tile_stairs", "roof_full": "minecraft:deepslate_tiles",
                          "ridge": "minecraft:deepslate_tile_slab", "infill": ["minecraft:calcite"]})
    for z in (64, 74):
        k.banner(t, 83, 4, z, "west", "blue", 1, patterns=[("stripe_center", "white"), ("border", "yellow")])
    k.building(t, 62, 86, 11, 11, "firstia", floors=2, door_side="north", jetty=1, bed_color="magenta")
    k.building(t, 78, 86, 13, 9, "firstia", floors=2, door_side="north", jetty=1, dormers=True, bed_color="white",
               overrides={"infill": ["minecraft:calcite"], "roof": "minecraft:dark_oak_stairs",
                          "roof_full": "minecraft:dark_oak_planks"})
    k.building(t, 94, 86, 9, 13, "firstia", floors=2, door_side="west", along="z", use="store", bed_color="black",
               sign=["General", "Store"],
               overrides={"infill": ["minecraft:white_terracotta"]})
    # Street furniture: lamps along every road, benches, planters, trees, flowers
    _lamps_along(t, [(x, c - 3) for x in range(10, S - 10) if abs(x - c) > 16], 9, 4, arm="south")
    _lamps_along(t, [(x, c + 3) for x in range(14, S - 10) if abs(x - c) > 16], 9, 4, arm="north")
    _lamps_along(t, [(c - 3, z) for z in range(10, S - 10) if abs(z - c) > 16], 9, 4, arm="east")
    _lamps_along(t, [(c + 3, z) for z in range(14, S - 10) if abs(z - c) > 16], 9, 4, arm="west")
    for z in (28, 84):
        _lamps_along(t, [(x, z) for x in range(10, S - 10)], 12, 3)
    _scatter_trees(t, [(10, 10 + 1), (24, 9), (40, 9), (50, 30), (60, 26), (100, 12), (96, 26), (9, 52), (52, 44),
                       (9, 60), (100, 50), (100, 60), (52, 100), (9, 100), (40, 100), (75, 100), (100, 100),
                       (60, 98), (88, 100), (36, 52), (22, 52), (88, 51)])
    k.flowers(t, 6, 6, S - 7, S - 7, 0.06)
    return _finish(t, entrances={"west gate": [(0, z) for z in range(c - 2, c + 3)],
                                 "east gate": [(S - 1, z) for z in range(c - 2, c + 3)]})


def _wall_tower(t, x0, z0, size, height, walk, axis, style, lo, hi, roof_kind="crenel", flag=None):
    """A square tower astride a curtain wall (running along `axis`), with doorways where the
    wall walk (cells lo..hi across the wall) runs through it."""
    k.square_tower(t, x0, z0, size, height, style, roof_kind=roof_kind, floors=False, flag=flag)
    x1, z1 = x0 + size - 1, z0 + size - 1
    t.fill(x0 + 1, walk, z0 + 1, x1 - 1, walk, z1 - 1, k.CASTLE[style]["walk"])
    for y in (walk + 1, walk + 2):
        for m in range(lo, hi + 1):
            if axis == "x":
                t.set(x0, y, m, "minecraft:air")
                t.set(x1, y, m, "minecraft:air")
            else:
                t.set(m, y, z0, "minecraft:air")
                t.set(m, y, z1, "minecraft:air")


def _forge_pavilion(t, x0, z0, w, d):
    """Firstia's weapon shop: an open timber shed round a brick forge (the smith himself lives
    in Secondil), with an anvil, grindstone and racks of blades."""
    x1, z1 = x0 + w - 1, z0 + d - 1
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            k.put(t, x, 0, z, ["minecraft:cobblestone", "minecraft:stone_bricks", "minecraft:gravel"]
                  if 0 < x - x0 < w - 1 and 0 < z - z0 < d - 1 else "minecraft:stone_bricks")
    for (px, pz) in ((x0, z0), (x1, z0), (x0, z1), (x1, z1), (x0, (z0 + z1) // 2), (x1, (z0 + z1) // 2)):
        for y in range(1, 5):
            t.set(px, y, pz, "minecraft:stripped_spruce_log", {"axis": "y"})
    for x in range(x0, x1 + 1):
        for z in (z0, z1):
            t.set(x, 5, z, "minecraft:stripped_spruce_log", {"axis": "x"})
    for z in range(z0, z1 + 1):
        for x in (x0, x1):
            t.set(x, 5, z, "minecraft:stripped_spruce_log", {"axis": "z"})
    for (px, pz) in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
        for (dx, dz) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if x0 <= px + dx <= x1 and z0 <= pz + dz <= z1:
                t.set(px + dx, 4, pz + dz, *k.stairs("minecraft:spruce_stairs", k.facing_of(-dx, -dz), "top"))
    s = dict(k.STYLES["firstia"])
    k.roof(t, [k.hip_part(x0, z0, x1, z1, 6, 1, 1)], 6, s)
    # the forge: a brick hearth with a chimney at the back
    for x in range(x0 + 1, x0 + 4):
        for z in range(z0 + 1, z0 + 3):
            for y in range(1, 5):
                t.set(x, y, z, "minecraft:bricks")
    t.set(x0 + 2, 1, z0 + 2, "minecraft:blast_furnace", {"facing": "south", "lit": "true"})
    k.chimney(t, x0 + 2, z0 + 1, 5, 12, "minecraft:bricks")
    t.set(x0 + 4, 1, z0 + 1, "minecraft:water_cauldron", {"level": "3"})
    t.set(x0 + 4, 1, z0 + 4, "minecraft:anvil", {"facing": "east"})
    t.set(x0 + 6, 1, z0 + 1, "minecraft:grindstone", {"face": "floor", "facing": "south"})
    t.set(x0 + 7, 1, z0 + 1, "minecraft:smithing_table")
    t.set(x0 + 7, 1, z0 + 3, *k.barrel("up"))
    t.set(x0 + 7, 1, z0 + 4, *k.barrel("west"))
    t.set(x0 + 1, 1, z0 + 6, "minecraft:chest", {"facing": "east", "type": "single", "waterlogged": "false"})
    # weapon racks: fences with iron bars "blades" against the back wall
    for z in range(z0 + 4, z0 + 7):
        t.set(x0 + 1, 1, z, *k.fence("minecraft:spruce_fence"))
        t.set(x0 + 1, 2, z, "minecraft:iron_bars", {"waterlogged": "false"})
    for x in range(x0 + 1, x1):
        t.set(x, 5, (z0 + z1) // 2, "minecraft:stripped_spruce_log", {"axis": "x"})
    k.lantern(t, x0 + 4, 4, (z0 + z1) // 2, hanging=True)
    k.ceiling_sign(t, (x0 + x1) // 2, 4, z1, 0, ["Weapons", "& Armour"], wood="spruce")
    for x in range(x0 + 1, x1):
        k.reserve(t, x, z1 + 1, x, z1 + 1)


def _chapel(t, x0, z0):
    """Firstia's chapel: a buttressed stone nave under a steep slate roof with tall stained
    windows, a chancel, a west bell tower with an open belfry and a spire, and a churchyard."""
    tw = 7
    tz0 = z0 + 2
    nave_x0 = x0 + tw - 1
    info = k.building(t, nave_x0, z0, 21, 11, "firstia_stone", floors=1, h=8, door_side="west", door_at=z0 + 5,
                      pitch=2, furnish_it=False, chimney_on=False, lamps=False,
                      overrides={"pane": "minecraft:purple_stained_glass_pane"})
    nx1 = nave_x0 + 20
    # stained glass in many colours
    glass = ["minecraft:purple_stained_glass_pane", "minecraft:blue_stained_glass_pane", "minecraft:red_stained_glass_pane",
             "minecraft:yellow_stained_glass_pane", "minecraft:light_blue_stained_glass_pane"]
    for (x, y, z), v in list(t.blocks.items()):
        if v[0] == "minecraft:purple_stained_glass_pane" and nave_x0 <= x <= nx1 + 6:
            t.set(x, y, z, t.rnd.choice(glass), {"waterlogged": "false"})
    # buttresses between the windows
    for x in range(nave_x0 + 4, nx1, 4):
        for (z, f) in ((z0 - 1, "south"), (z0 + 11, "north")):
            for y in range(1, 6):
                t.set(x, y, z, "minecraft:stone_bricks")
            t.set(x, 6, z, *k.stairs("minecraft:stone_brick_stairs", f))
    # chancel at the east end
    k.building(t, nx1, z0 + 2, 6, 7, "firstia_stone", floors=1, h=7, door_side="east", pitch=2, furnish_it=False,
               chimney_on=False, lamps=False, entrance=False, overrides={"pane": "minecraft:yellow_stained_glass_pane"})
    for z in range(z0 + 3, z0 + 8):
        for y in range(1, 6):
            t.set(nx1, y, z, "minecraft:air")
    for y in (1, 2):
        t.set(nx1 + 5, y, z0 + 5, "minecraft:stone_bricks")
    # interior: pews facing the altar, chandeliers
    for x in range(nave_x0 + 3, nx1 - 2, 2):
        for z in list(range(z0 + 1, z0 + 4)) + list(range(z0 + 7, z0 + 10)):
            t.set(x, 1, z, *k.stairs("minecraft:dark_oak_stairs", "west"))
    for x in range(nave_x0 + 1, nx1 + 4):
        for z in range(z0 + 4, z0 + 7):
            if t.get(x, 1, z) in (None, "minecraft:air"):
                t.set(x, 1, z, "minecraft:red_carpet")
    t.fill(nx1 + 3, 1, z0 + 4, nx1 + 3, 1, z0 + 6, "minecraft:quartz_block")
    t.set(nx1 + 3, 2, z0 + 5, *k.candle("white", 3))
    t.set(nx1 + 3, 2, z0 + 4, *k.candle("white", 1))
    t.set(nx1 + 3, 2, z0 + 6, *k.candle("white", 1))
    t.set(nx1 + 1, 1, z0 + 5, "minecraft:lectern", {"facing": "west", "has_book": "false", "powered": "false"})
    for x in range(nave_x0 + 4, nx1 - 1, 6):
        for y in (8, 7):
            t.set(x, y, z0 + 5, *k.chain())
        k.lantern(t, x, 6, z0 + 5, hanging=True)
    # the bell tower over the west door
    peak = k.square_tower(t, x0, tz0, tw, 21, "firstia", roof_kind="pyramid", pitch=3, mach=False, slits=True,
                          floors=False, flag=None)
    t.set(x0 + tw // 2, peak + 1, tz0 + tw // 2, "minecraft:gold_block")
    t.set(x0 + tw // 2, peak + 2, tz0 + tw // 2, "minecraft:lightning_rod", {"facing": "up", "powered": "false",
                                                                           "waterlogged": "false"})
    for f in (6, 11, 16):
        t.fill(x0 + 1, f, tz0 + 1, x0 + tw - 2, f, tz0 + tw - 2, "minecraft:spruce_planks")
    cz = tz0 + tw // 2
    for y in range(1, 17):
        t.set(x0 + tw - 2, y, tz0 + 1, "minecraft:ladder", {"facing": "west", "waterlogged": "false"})
    for y in (1, 2, 3):
        for z in (cz - 1, cz, cz + 1):
            t.set(x0 + tw - 1, y, z, "minecraft:air")
    for y in range(1, 4):
        t.set(x0 + 1, y, cz, "minecraft:air")
    k.door(t, x0, 1, cz, "west", "dark_oak")
    for z in (cz - 1, cz + 1):
        t.set(x0 - 1, 3, z, *k.stairs("minecraft:stone_brick_stairs", "east"))
    t.set(x0 - 1, 3, cz, *k.stairs("minecraft:stone_brick_stairs", "east"))
    k.reserve(t, x0 - 1, cz, x0 - 1, cz)
    k.reserve(t, x0 + 1, cz, x0 + 1, cz)
    # belfry: tall openings on every face, the bell hanging in the middle
    for y in range(17, 20):
        for (x, z) in ((x0 + 2, tz0), (x0 + 4, tz0), (x0 + 2, tz0 + tw - 1), (x0 + 4, tz0 + tw - 1),
                       (x0, cz - 1), (x0, cz + 1), (x0 + tw - 1, cz - 1), (x0 + tw - 1, cz + 1)):
            t.set(x, y, z, "minecraft:air")
    t.set(x0 + 3, 20, cz, "minecraft:bell", {"attachment": "ceiling", "facing": "north", "powered": "false"})
    t.set(x0 + 3, 21, cz, "minecraft:stone_bricks")
    # churchyard: gravestones and a yew
    gx0, gz0 = nave_x0 + 2, z0 + 13
    for x in range(gx0, gx0 + 20, 3):
        for z in (gz0 + 1, gz0 + 4):
            if t.get(x, 1, z) is None:
                t.set(x, 1, z, *k.wall_block(t.rnd.choice(["minecraft:stone_brick_wall", "minecraft:mossy_stone_brick_wall"])))
                t.set(x, 2, z, *k.slab(t.rnd.choice(["minecraft:stone_brick_slab", "minecraft:mossy_stone_brick_slab"])))
                if t.rnd.random() < 0.4 and t.get(x, 1, z + 1) is None:
                    t.set(x, 1, z + 1, t.rnd.choice(["minecraft:poppy", "minecraft:lily_of_the_valley", "minecraft:allium"]))
    k.conifer(t, gx0 + 21, 1, gz0 + 3, "spruce", 8)
    return info


def _gardens(t, x0, z0, x1, z1, crops=None):
    """Raised kitchen-garden beds (farmland at y=1 on a log frame) with a water channel boxed
    in between them, a scarecrow and a compost bin."""
    crops = crops or ["minecraft:wheat", "minecraft:carrots", "minecraft:potatoes", "minecraft:beetroots"]
    x = x0
    i = 0
    while x + 6 <= x1:
        bx0, bx1 = x, x + 6
        for xx in range(bx0, bx1 + 1):
            for zz in range(z0, z1 + 1):
                border = xx in (bx0, bx1) or zz in (z0, z1)
                if border:
                    t.set(xx, 1, zz, "minecraft:spruce_log", {"axis": "x" if zz in (z0, z1) else "z"})
                elif xx == (bx0 + bx1) // 2:
                    t.set(xx, 1, zz, "minecraft:water", {"level": "0"})
                else:
                    t.set(xx, 1, zz, "minecraft:farmland", {"moisture": "7"})
                    crop = crops[i % len(crops)]
                    t.set(xx, 2, zz, crop, {"age": "7" if crop != "minecraft:beetroots" else "3"})
        x += 8
        i += 1
    # scarecrow
    sx, sz = x1 - 1, z0 - 2
    if t.get(sx, 1, sz) is None:
        t.set(sx, 1, sz, *k.fence("minecraft:spruce_fence"))
        t.set(sx, 2, sz, "minecraft:hay_block", {"axis": "y"})
        t.set(sx, 3, sz, "minecraft:carved_pumpkin", {"facing": "south"})
    t.set(x0 - 1, 1, z0, "minecraft:composter", {"level": "6"})


# ---------------------------------------------------------------------------------------
# Secondil: sandstone walls, a twin-towered gate, red tile roofs, a castle on a terraced hill
# ---------------------------------------------------------------------------------------

SANDY = ["minecraft:smooth_sandstone", "minecraft:smooth_sandstone", "minecraft:sandstone", "minecraft:cut_sandstone"]
SECONDIL_WHITE = {"infill": ["minecraft:white_concrete", "minecraft:calcite"], "ground": ["minecraft:smooth_sandstone"],
                  "shutter": "minecraft:crimson_trapdoor"}


def secondil():
    S = 128
    t = Template(S, 64, S, "secondil")
    c = S // 2
    _grass(t, 0, 0, S - 1, S - 1)
    paving = ["minecraft:smooth_sandstone", "minecraft:sandstone", "minecraft:cut_sandstone", "minecraft:smooth_sandstone"]
    # Streets: the main street from the west gate, a north-south street, two cross streets,
    # and a lane behind the castle hill to the east gate.
    k.road(t, 0, c - 3, 46, c + 3, "minecraft:smooth_sandstone", "minecraft:cut_sandstone", mix=paving)
    k.road(t, 40, 16, 46, S - 17, "minecraft:smooth_sandstone", "minecraft:cut_sandstone", mix=paving)
    k.road(t, 40, 16, S - 10, 22, "minecraft:smooth_sandstone", "minecraft:cut_sandstone", mix=paving)
    k.road(t, 40, S - 23, S - 10, S - 17, "minecraft:smooth_sandstone", "minecraft:cut_sandstone", mix=paving)
    k.road(t, S - 11, 16, S - 8, S - 17, "minecraft:smooth_sandstone", "minecraft:cut_sandstone", mix=paving)
    k.road(t, S - 11, c - 2, S - 1, c + 2, "minecraft:smooth_sandstone", "minecraft:cut_sandstone", mix=paving)
    # Walls and towers
    k.curtain_wall(t, 4, 4, S - 5, 4, 10, "secondil", "north", buttress=10)
    k.curtain_wall(t, 4, S - 5, S - 5, S - 5, 10, "secondil", "south", buttress=10)
    k.curtain_wall(t, 4, 4, 4, S - 5, 10, "secondil", "west", gaps=((c - 12, c + 12),), buttress=10)
    k.curtain_wall(t, S - 5, 4, S - 5, S - 5, 10, "secondil", "east", gaps=((c - 6, c + 6),), buttress=10)
    flag = ("red", [("rhombus", "yellow"), ("border", "yellow")])
    for (tx, tz) in ((1, 1), (S - 10, 1), (1, S - 10), (S - 10, S - 10)):
        k.square_tower(t, tx, tz, 9, 17, "secondil", roof_kind="pyramid", pitch=2, flag=flag)
    for (tx, tz, lo, ax) in ((40, 1, 4, "x"), (84, 1, 4, "x"), (40, S - 8, S - 7, "x"), (84, S - 8, S - 7, "x"),
                             (1, 30, 4, "z"), (1, 92, 4, "z"), (S - 7, 30, S - 7, "z"), (S - 7, 92, S - 7, "z")):
        _wall_tower(t, tx, tz, 7, 14, 10, ax, "secondil", lo, lo + 2)
    _secondil_gate(t, c)
    k.gatehouse(t, S - 5, c, "east", "secondil", passage=5, depth=3, height=12, tower=6, tower_h=16, tower_out=4,
                tower_depth=7,
                banner_colors=("red", "yellow"), patterns=[("rhombus", "yellow"), ("border", "yellow")])
    for (x, z, d) in ((14, 7, "east"), (S - 15, 7, "west"), (14, S - 8, "east"), (S - 15, S - 8, "west")):
        k.wall_stairs(t, x, z, 10, d, "secondil")
    # The gate plaza: waypoint and spawn
    k.pave_disc(t, 18, c, 8, ["minecraft:smooth_sandstone", "minecraft:cut_sandstone", "minecraft:sandstone"])
    t.ring(18, 0, c, 8, "minecraft:chiseled_sandstone", width=0.5)
    t.ring(18, 0, c, 5, "minecraft:red_terracotta", width=0.5)
    k.waypoint(t, 18, 0, c - 5, base="minecraft:chiseled_sandstone")
    k.marker(t, 20, 1, c, "spawn")
    for (x, z) in ((12, c - 6), (24, c - 6), (12, c + 6), (24, c + 6)):
        k.lamp_post(t, x, 1, z, 3, base="minecraft:sandstone_wall")
    # Market stalls along the main street, with lamps
    cols = ["red", "yellow", "orange", "lime", "white", "blue", "pink"]
    for i, x in enumerate(range(26, 38, 5)):
        k.market_stall(t, x, c - 8, cols[i % 7], "south", stripe="white", wood="oak")
        k.market_stall(t, x, c + 5, cols[(i + 3) % 7], "north", stripe="white", wood="oak")
    _lamps_along(t, [(x, c - 4) for x in range(26, 46)], 7, 4, arm="south", post="minecraft:oak_fence",
                 base="minecraft:sandstone_wall")
    _lamps_along(t, [(x, c + 4) for x in range(26, 46)], 7, 4, arm="north", post="minecraft:oak_fence",
                 base="minecraft:sandstone_wall")
    # The smithy on the main street (the blacksmith works here)
    k.building(t, 25, 43, 13, 9, "secondil", floors=1, h=6, door_side="south", use="smithy", sign=["Smithy"],
               chimney_on=True, overrides={"ground": ["minecraft:bricks", "minecraft:bricks", "minecraft:mud_bricks"],
                                           "plinth": ["minecraft:stone_bricks", "minecraft:cobblestone"]})
    for x in range(26, 31):
        for y in range(1, 4):
            t.set(x, y, 44, "minecraft:bricks")
    t.set(28, 1, 45, "minecraft:lava_cauldron")
    t.set(28, 4, 44, "minecraft:bricks")
    t.set(27, 1, 45, "minecraft:blast_furnace", {"facing": "south", "lit": "true"})
    t.set(29, 1, 45, "minecraft:blast_furnace", {"facing": "south", "lit": "true"})
    k.chimney(t, 28, 44, 4, 16, "minecraft:bricks")
    t.set(31, 1, 49, "minecraft:air")
    t.set(31, 2, 49, "minecraft:air")
    t.entity(31, 1, 49, "slfrontier:frontier_blacksmith", yaw=180.0)
    _station(t, 33, 1, 49, "frontier_forge", "south")
    _station(t, 29, 1, 49, "frontier_workbench", "south")
    # The white inn straight ahead from the gate
    k.building(t, 47, c - 8, 13, 17, "secondil", floors=3, ground_h=5, door_side="west", along="z", use="inn",
               bed_color="white", sign=["Inn"], cross=True, balcony=False, overrides=SECONDIL_WHITE)
    # A market square with a fountain south of the main street
    for x in range(14, 39):
        for z in range(74, 93):
            k.put(t, x, 0, z, paving)
    k.reserve(t, 14, 74, 38, 92)
    for x in range(14, 39):
        for z in range(74, 93):
            if math.hypot(x - 26, z - 83) <= 7:
                k.unreserve(t, x, z, x, z)
    k.fountain(t, 26, 83, 4, block="minecraft:smooth_sandstone", rim="minecraft:cut_sandstone",
               floor="minecraft:prismarine_bricks", trim="minecraft:chiseled_sandstone", coping="minecraft:smooth_sandstone_slab")
    k.road(t, 19, c + 4, 21, 73, "minecraft:smooth_sandstone", None, mix=paving)
    for (x, z) in ((19, 78), (33, 78), (19, 88), (33, 88)):
        k.unreserve(t, x, z, x, z)
        k.lamp_post(t, x, 1, z, 3, post="minecraft:oak_fence", base="minecraft:sandstone_wall")
    for i, (x, z, f) in enumerate(((14, 88, "north"), (34, 75, "south"))):
        k.unreserve(t, x, z, x + 3, z + 2)
        k.market_stall(t, x, z, cols[i + 2], f, stripe="white", wood="oak")
    # Houses
    houses = [
        (12, 12, 13, 9, "south", {}), (28, 12, 9, 11, "south", SECONDIL_WHITE), (12, 26, 9, 11, "east", {}),
        (26, 27, 11, 9, "east", SECONDIL_WHITE), (12, 42, 9, 11, "east", {}),
        (8, 72, 5, 11, "east", None), (8, 96, 13, 9, "north", {}), (24, 96, 13, 9, "north", SECONDIL_WHITE),
        (8, 108, 9, 9, "north", SECONDIL_WHITE), (22, 108, 13, 9, "north", {}),
        (50, 26, 9, 13, "west", {}), (50, 42, 9, 11, "west", SECONDIL_WHITE), (50, 76, 9, 11, "west", {}),
        (50, 90, 9, 11, "west", SECONDIL_WHITE), (62, 6, 11, 9, "south", {}), (76, 6, 13, 9, "south", SECONDIL_WHITE),
        (92, 6, 11, 9, "south", {}), (62, 112, 11, 9, "north", SECONDIL_WHITE), (78, 112, 11, 9, "north", {}),
        (94, 112, 11, 9, "north", SECONDIL_WHITE),
    ]
    for i, (x, z, w, d, side, ov) in enumerate(houses):
        if ov is None:
            continue
        shop = {1: ["Bakery"], 4: ["Tailor"], 6: ["Potions"], 11: ["Fletcher"], 15: ["Grocer"], 17: ["Cartographer"]}
        k.building(t, x, z, w, d, "secondil", floors=2 + (i % 3 == 0), door_side=side, overrides=ov,
                   sign=shop.get(i), use="shop" if i in shop else "home",
                   bed_color=["red", "yellow", "orange", "brown"][i % 4], balcony=bool(ov) and i % 2 == 0,
                   awning=["red", "white"] if i % 5 == 1 and not ov else None, cross=i % 4 == 2,
                   roof_kind="hip" if i % 3 == 1 else "gable")
    # The castle on its terraced hill
    _secondil_castle(t, c)
    _scatter_trees(t, [(38, 28), (38, 100), (60, 40), (60, 86), (10, 60), (110, 26), (110, 100), (60, 104),
                       (24, 40), (12, 90)])
    k.flowers(t, 6, 6, S - 7, S - 7, 0.05)
    # Long red-and-gold banners inside the gate passage
    return _finish(t, entrances={"west gate": [(0, z) for z in range(c - 3, c + 4)],
                                 "east gate": [(S - 1, z) for z in range(c - 2, c + 3)]})


def _secondil_gate(t, c):
    """The twin-towered west gate: a deep arched passage (7 wide) between two round towers,
    a gilded diamond over the arch, a raised portcullis and long red-and-gold banners."""
    s = k.CASTLE["secondil"]
    pw = 7
    hw = pw // 2
    depth = 9
    for x in range(1, 1 + depth):
        for z in range(c - hw - 2, c + hw + 3):
            for y in range(1, 15):
                k.put(t, x, y, z, s["body"])
            k.put(t, x, 14, z, s["walk"])
    cols = [[(x, z) for x in range(1, 1 + depth)] for z in range(c - hw, c + hw + 1)]
    tops = k.arch_opening(t, cols, 0, 6, (0, 1), s["corbel"])
    for i, col in enumerate(cols):
        for (x, z) in (col[0], col[-1]):
            t.set(x, tops[i] + 1, z, s["trim"])
    k.portcullis(t, [col[1] for col in cols], tops)
    k.reserve(t, 0, c - hw, depth + 1, c + hw)
    # merlons and machicolations over the arch
    for z in range(c - hw - 2, c + hw + 3):
        t.set(0, 13, z, *k.stairs(s["corbel"], "east", "top"))
        t.set(0, 14, z, s["trim"])
        if z % 2 == 0:
            k.put(t, 0, 15, z, s["body"])
            t.set(0, 16, z, *k.slab(s["cap"]))
        else:
            t.set(0, 15, z, *k.slab(s["cap"]))
    # the gilded diamond
    for dz in range(-2, 3):
        for dy in range(-2, 3):
            if abs(dz) + abs(dy) <= 2:
                t.set(1, 11 + dy, c + dz, "minecraft:gold_block" if abs(dz) + abs(dy) == 2 else "minecraft:red_terracotta")
    for sgn in (-1, 1):
        tz = c + sgn * (hw + 6)
        k.round_tower(t, 5, tz, 5, 20, "secondil", roof_h=13, flag=("red", [("rhombus", "yellow"), ("border", "yellow")]))
    for z in (c - hw - 1, c + hw + 1):
        k.banner(t, 0, 10, z, "west", "red", 2, trim="yellow", patterns=[("rhombus", "yellow")])
    for z in (c - hw - 1, c + hw + 1):
        k.banner(t, depth + 1, 12, z, "east", "red", 3, trim="yellow", patterns=[("rhombus", "yellow")])
    for (x, z) in ((depth // 2, c),):
        k.lantern(t, x, tops[hw], z, hanging=True)


def _secondil_castle(t, c):
    """Three terraces with buttressed retaining walls, balustrades and gardens; a grand stair;
    an inner curtain on the top terrace; and the keep with round corner towers, a central
    donjon and long banners."""
    hx0, hz0, hx1, hz1 = 64, 30, 116, 98
    tiers = ((0, 3), (5, 6), (10, 9))
    for i, (m, h) in enumerate(tiers):
        x0, z0, x1, z1 = hx0 + m, hz0 + m, hx1 - m, hz1 - m
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                edge = x in (x0, x1) or z in (z0, z1)
                for y in range(0, h):
                    k.put(t, x, y, z, ["minecraft:stone_bricks", "minecraft:cut_sandstone"][min(i, 1)] if edge
                          else "minecraft:dirt")
                if edge:
                    k.put(t, x, h, z, "minecraft:smooth_sandstone")
                    t.set(x, h + 1, z, *k.wall_block("minecraft:sandstone_wall"))
                else:
                    t.set(x, h, z, "minecraft:grass_block", {"snowy": "false"})
        # buttresses on the retaining wall
        for x in range(x0 + 3, x1 - 1, 6):
            for (z, f) in ((z0 - 1, "south"), (z1 + 1, "north")):
                for y in range(1, h):
                    t.set(x, y, z, "minecraft:stone_bricks" if i == 0 else "minecraft:cut_sandstone")
                t.set(x, h, z, *k.stairs("minecraft:sandstone_stairs", f))
        for z in range(z0 + 3, z1 - 1, 6):
            if abs(z - c) <= 5:
                continue
            for (x, f) in ((x0 - 1, "east"), (x1 + 1, "west")):
                if k.is_reserved(t, x, z):
                    continue
                for y in range(1, h):
                    t.set(x, y, z, "minecraft:stone_bricks" if i == 0 else "minecraft:cut_sandstone")
                t.set(x, h, z, *k.stairs("minecraft:sandstone_stairs", f))
    # The grand stair up the west face
    for y in range(1, 10):
        x = hx0 - 1 + y
        for z in range(c - 3, c + 4):
            t.set(x, y, z, *k.stairs("minecraft:smooth_sandstone_stairs", "east"))
            for yy in range(y + 1, 12):
                t.set(x, yy, z, "minecraft:air")
        for z in (c - 4, c + 4):
            t.set(x, y, z, "minecraft:cut_sandstone")
            t.set(x, y + 1, z, *k.wall_block("minecraft:sandstone_wall"))
        for z in range(c - 3, c + 4):
            for yy in range(0, y):
                t.set(x, yy, z, "minecraft:cut_sandstone")
    for x in (hx0 + 9, hx0 + 10):
        for z in range(c - 3, c + 4):
            for yy in range(0, 10):
                t.set(x, yy, z, "minecraft:cut_sandstone")
            for yy in range(10, 13):
                t.set(x, yy, z, "minecraft:air")
    k.reserve(t, hx0 - 2, c - 3, hx0 - 1, c + 3)
    for z in (c - 4, c + 4):
        k.lantern(t, hx0 + 8, 10, z)
    # Inner curtain on the top terrace, gate in the west side
    top = 9
    ix0, iz0, ix1, iz1 = hx0 + 12, hz0 + 12, hx1 - 12, hz1 - 12
    s = k.CASTLE["secondil"]
    for (a0, b0, a1, b1, out) in ((ix0, iz0, ix1, iz0, "north"), (ix0, iz1, ix1, iz1, "south"),
                                  (ix0, iz0, ix0, iz1, "west"), (ix1, iz0, ix1, iz1, "east")):
        for x in range(min(a0, a1), max(a0, a1) + 1):
            for z in range(min(b0, b1), max(b0, b1) + 1):
                if out == "west" and abs(z - c) <= 3:
                    continue
                for y in range(top + 1, top + 7):
                    k.put(t, x, y, z, s["body"])
                if (x + z) % 2 == 0:
                    k.put(t, x, top + 7, z, s["body"])
                    t.set(x, top + 8, z, *k.slab(s["cap"]))
    for (tx, tz) in ((ix0, iz0), (ix1, iz0), (ix0, iz1), (ix1, iz1)):
        k.round_tower(t, tx, tz, 3, 12, "secondil", base=top, roof_h=9, mach=False, batter=False,
                      flag=("red", [("rhombus", "yellow")]))
    for z in range(c - 3, c + 4):
        for x in range(ix0 - 1, ix0 + 2):
            t.set(x, top + 6, z, s["trim"])
    for z in (c - 4, c + 4):
        for y in range(top + 1, top + 10):
            k.put(t, ix0, y, z, s["trim"])
        k.lantern(t, ix0, top + 10, z)
    k.road(t, hx0 + 9, c - 3, ix0 + 6, c + 3, "minecraft:smooth_sandstone", "minecraft:chiseled_sandstone",
           y=top, mix=["minecraft:cut_sandstone"])
    # The keep: a great hall with round towers at the corners and a donjon in the middle
    kx0, kz0, w, d = ix0 + 7, c - 13, 21, 27
    keep_style = {"ground": SANDY, "infill": SANDY, "frame": None, "quoin": "minecraft:chiseled_sandstone",
                  "pane": "minecraft:glass_pane", "shutter": None, "hood": "minecraft:smooth_sandstone_stairs",
                  "carpet": "red", "course": "minecraft:chiseled_sandstone"}
    k.building(t, kx0, kz0, w, d, "secondil", floors=3, h=5, y=top, door_side="west", along="z", roof_kind="hip",
               pitch=2, use="guild", bed_color="red", overrides=keep_style, chimney_on=False, lamps=True)
    for (tx, tz) in ((kx0, kz0), (kx0 + w - 1, kz0), (kx0, kz0 + d - 1), (kx0 + w - 1, kz0 + d - 1)):
        k.round_tower(t, tx, tz, 4, 24, "secondil", base=top, roof_h=12, flag=("red", [("rhombus", "yellow")]))
    k.square_tower(t, kx0 + 7, c - 3, 7, 30, "secondil", base=top, roof_kind="pyramid", pitch=3,
                   flag=("red", [("rhombus", "yellow"), ("border", "yellow")]), floors=False)
    for z in (kz0 + 6, kz0 + d - 7):
        k.banner(t, kx0 - 1, top + 13, z, "west", "red", 4, trim="yellow", patterns=[("rhombus", "yellow")])
    t.fill(kx0 + 1, top + 1, c - 1, kx0 + 8, top + 1, c + 1, "minecraft:red_carpet")
    # terrace gardens
    for (x, z) in ((hx0 + 3, hz0 + 3), (hx1 - 3, hz0 + 3), (hx0 + 3, hz1 - 3), (hx1 - 3, hz1 - 3),
                   (hx0 + 8, hz0 + 8), (hx1 - 8, hz1 - 8), (hx0 + 8, hz1 - 8), (hx1 - 8, hz0 + 8)):
        y = 3 if (x - hx0 < 5 or hx1 - x < 5 or z - hz0 < 5 or hz1 - z < 5) else 6
        if t.get(x, y, z) == "minecraft:grass_block" and k.free(t, x, z, y + 1, 6):
            k.tree(t, x, y + 1, z, "oak", "oak", 5, 2)
    k.flowers(t, hx0, hz0, hx1, hz1, 0.15, ground=("minecraft:grass_block",), y=3)
    k.flowers(t, hx0, hz0, hx1, hz1, 0.15, ground=("minecraft:grass_block",), y=6)
    k.flowers(t, hx0, hz0, hx1, hz1, 0.1, ground=("minecraft:grass_block",), y=9)


# ---------------------------------------------------------------------------------------
# Thirdrema: a grand white city climbing in three terraces to a fairy-tale castle
# ---------------------------------------------------------------------------------------

WHITE = ["minecraft:calcite", "minecraft:calcite", "minecraft:white_concrete", "minecraft:polished_diorite"]
TEAL = {"shutter": "minecraft:warped_trapdoor", "door": "warped"}
COPPER_ROOF = {"roof": "minecraft:waxed_oxidized_cut_copper_stairs", "roof_full": "minecraft:waxed_oxidized_cut_copper",
               "ridge": "minecraft:waxed_oxidized_cut_copper_slab"}


def thirdrema():
    W, H, D = 112, 90, 160
    t = Template(W, H, D, "thirdrema")
    cx = W // 2
    tiers = [(0, 52, 18), (52, 102, 9), (102, D, 0)]  # (z0, z1, surface height): castle, upper, lower

    def th(z):
        for (z0, z1, h) in tiers:
            if z0 <= z < z1:
                return h
        return 0
    _grass(t, 0, 0, W - 1, D - 1)
    # Terraces, faced with white stone and buttressed retaining walls
    wx0, wx1 = 3, W - 4
    for (z0, z1, h) in tiers:
        if not h:
            continue
        for z in range(z0, z1):
            for x in range(wx0, wx1 + 1):
                front = z == z1 - 1
                for y in range(0, h):
                    t.set(x, y, z, k.pick(t, ["minecraft:stone_bricks", "minecraft:polished_diorite", "minecraft:calcite"])
                          if front else "minecraft:dirt")
                t.set(x, h, z, "minecraft:grass_block", {"snowy": "false"})
        _retaining_face(t, wx0, wx1, z1 - 1, th(z1), h, cx)
    # The central avenue up through the city, cross streets on each tier
    avenue = ["minecraft:polished_diorite", "minecraft:smooth_stone", "minecraft:calcite", "minecraft:polished_andesite"]
    for (z0, z1, h) in tiers:
        k.road(t, cx - 4, max(z0, 37), cx + 4, min(z1, 151) - 1, "minecraft:polished_diorite", "minecraft:quartz_bricks",
               mix=avenue, y=h, chance=0.4)
    for (zc, h) in ((70, 9), (88, 9), (118, 0), (136, 0)):
        k.road(t, wx0 + 3, zc - 1, wx1 - 3, zc + 1, "minecraft:smooth_stone", "minecraft:polished_diorite",
               mix=["minecraft:polished_andesite", "minecraft:stone_bricks"], y=h)
    # Grand stairs between the terraces, with balustrades and lamps
    for (zfront, hlow, hhigh) in ((102, 0, 9), (52, 9, 18)):
        for y in range(hlow + 1, hhigh + 1):
            z = zfront + (hhigh - y)
            for x in range(cx - 4, cx + 5):
                t.set(x, y, z, *k.stairs("minecraft:polished_diorite_stairs", "north"))
                for yy in range(hlow, y):
                    t.set(x, yy, z, "minecraft:polished_diorite")
                for yy in range(y + 1, hhigh + 4):
                    t.set(x, yy, z, "minecraft:air")
            for x in (cx - 5, cx + 5):
                for yy in range(hlow, y + 1):
                    t.set(x, yy, z, "minecraft:quartz_bricks")
                t.set(x, y + 1, z, *k.wall_block("minecraft:diorite_wall"))
        k.unreserve(t, cx - 4, zfront, cx + 4, zfront + (hhigh - hlow))
        k.reserve(t, cx - 4, zfront + (hhigh - hlow), cx + 4, zfront + (hhigh - hlow) + 1, hlow)
        for x in (cx - 5, cx + 5):
            t.set(x, hhigh + 1, zfront, "minecraft:quartz_pillar", {"axis": "y"})
            k.lantern(t, x, hhigh + 2, zfront)
            t.set(x, hlow + 1, zfront + (hhigh - hlow) + 1, "minecraft:quartz_pillar", {"axis": "y"})
            k.lantern(t, x, hlow + 2, zfront + (hhigh - hlow) + 1)
    # Side walls stepping up with the terraces, towers at every step
    s = k.CASTLE["thirdrema"]
    gz = D - 8
    for z in range(0, gz + 1):
        h = th(z)
        for (x0, out) in ((wx0 - 3, "west"), (wx1 + 1, "east")):
            for x in range(x0, x0 + 3):
                for y in range(1, h + 6):
                    k.put(t, x, y, z, s["body"])
            ox = x0 if out == "west" else x0 + 2
            for x in range(x0, x0 + 3):
                k.put(t, x, h + 5, z, s["walk"])
            if z % 2 == 0:
                k.put(t, ox, h + 6, z, s["body"])
                t.set(ox, h + 7, z, *k.slab(s["cap"]))
            else:
                t.set(ox, h + 6, z, *k.slab(s["cap"]))
    for z in (4, 51, 101):
        for x in (4, W - 5):
            k.round_tower(t, x, z, 4, th(z) + 14, "thirdrema", roof_h=14, roof_over=True,
                          flag=("cyan", [("rhombus", "white"), ("border", "orange")]))
    # Curtain wall and gate across the valley mouth (the wall fills z 152..154)
    k.curtain_wall(t, 0, gz + 2, W - 1, gz + 2, 11, "thirdrema", "south", thick=3, gaps=((cx - 9, cx + 9),), buttress=9)
    k.gatehouse(t, cx, gz + 2, "south", "thirdrema", passage=5, depth=3, height=13, tower=6, tower_h=18,
                round_towers=False, tower_out=2, tower_depth=6, banner_colors=("cyan", "white"),
                patterns=[("rhombus", "white"), ("border", "orange")])
    k.road(t, cx - 2, gz + 3, cx + 2, D - 1, "minecraft:polished_diorite", "minecraft:quartz_bricks", mix=avenue)
    # Lower plaza: fountain, waypoint, spawn
    pz = 128
    k.pave_disc(t, cx, pz, 11, ["minecraft:polished_diorite", "minecraft:calcite", "minecraft:polished_andesite",
                                "minecraft:smooth_stone"])
    t.ring(cx, 0, pz, 11, "minecraft:quartz_bricks", width=0.5)
    t.ring(cx, 0, pz, 8, "minecraft:cyan_terracotta", width=0.5)
    for x in range(cx - 8, cx + 9):
        for z in range(pz - 8, pz + 9):
            if math.hypot(x - cx, z - pz) <= 7.5:
                k.unreserve(t, x, z, x, z)
    k.fountain(t, cx, pz, 5, block="minecraft:calcite", rim="minecraft:quartz_bricks", floor="minecraft:prismarine_bricks",
               trim="minecraft:chiseled_quartz_block", coping="minecraft:quartz_slab")
    k.waypoint(t, cx + 7, 0, pz + 6, base="minecraft:quartz_bricks")
    k.marker(t, cx, 1, pz + 14, "spawn")
    for a in range(0, 360, 45):
        x = round(cx + 10 * math.cos(math.radians(a + 22.5)))
        z = round(pz + 10 * math.sin(math.radians(a + 22.5)))
        if k.free(t, x, z, 1, 4):
            k.lamp_post(t, x, 1, z, 3, post="minecraft:birch_fence", base="minecraft:diorite_wall")
    for a in (45, 135, 225, 315):
        bx = round(cx + 7 * math.cos(math.radians(a)))
        bz = round(pz + 7 * math.sin(math.radians(a)))
        k.bench(t, bx, bz, k._toward(bx, bz, cx, pz), 2, "birch")
    # the artisans' corner of the plaza: all three crafting stations
    _station(t, cx - 10, 1, pz - 1, "frontier_workbench", "east")
    _station(t, cx - 10, 1, pz + 1, "frontier_forge", "east")
    _station(t, cx + 10, 1, pz, "alchemy_table", "west")
    # Houses: tall white townhouses with orange roofs (a few copper-green), terrace by terrace
    rnd = t.rnd

    def row(x0, x1, z0, z1, h, door):
        x = x0
        while x + 9 <= x1 + 1:
            w = rnd.choice([9, 9, 11, 13])
            if x + w - 1 > x1:
                w = x1 - x + 1
                if w < 9:
                    break
            d = min(z1 - z0 + 1, rnd.choice([9, 11]))
            z = z1 - d + 1 if door == "south" else z0
            ov = dict(TEAL)
            if rnd.random() < 0.2:
                ov.update(COPPER_ROOF)
            kind = rnd.choice(["gable", "hip", "gable", "flat"])
            sign = rnd.choice([None, None, None, ["Jeweller"], ["Library"], ["Bakery"], ["Tea House"], ["Tailor"],
                               ["Potions"], ["Bank"]])
            k.building(t, x, z, w, d, "thirdrema", floors=rnd.choice([2, 3, 3]), door_side=door, y=h, overrides=ov,
                       sign=sign, use="shop" if sign else "home",
                       roof_kind=kind, balcony=kind != "flat" and rnd.random() < 0.5, cross=kind == "gable" and w >= 11,
                       awning=["cyan", "white"] if rnd.random() < 0.25 else None,
                       bed_color=rnd.choice(["white", "orange", "light_blue", "cyan"]))
            x += w + 2

    # lower tier: three bands of houses either side of the avenue
    row(8, cx - 8, 104, 115, 0, "south")
    row(cx + 8, wx1 - 2, 104, 115, 0, "south")
    row(8, cx - 15, 121, 133, 0, "north")
    row(cx + 15, wx1 - 2, 121, 133, 0, "north")
    row(8, 19, 139, 149, 0, "north")
    row(36, cx - 8, 139, 149, 0, "north")
    row(cx + 8, wx1 - 2, 139, 149, 0, "north")
    _serpents_apple(t, 21, 139)
    # upper tier
    row(8, cx - 8, 54, 67, 9, "south")
    row(cx + 8, wx1 - 2, 54, 67, 9, "south")
    row(8, cx - 8, 73, 85, 9, "north")
    row(cx + 8, wx1 - 2, 73, 85, 9, "north")
    row(8, cx - 8, 91, 99, 9, "north")
    row(cx + 8, wx1 - 2, 91, 99, 9, "north")
    # the castle on the top terrace
    _thirdrema_castle(t, cx, 18)
    # street lamps along the avenue on each terrace
    for (z0, z1, h) in tiers:
        avenue_cells = [z for z in range(max(z0, 38), z1) if abs(z - 128) > 12]
        _lamps_along(t, [(cx - 5, z) for z in avenue_cells], 8, 4, arm="east", y=h + 1,
                     post="minecraft:birch_fence", base="minecraft:diorite_wall")
        _lamps_along(t, [(cx + 5, z) for z in avenue_cells], 8, 4, arm="west", y=h + 1,
                     post="minecraft:birch_fence", base="minecraft:diorite_wall")
    for (h, zs) in ((0, range(104, 150)), (9, range(54, 100))):
        for z in zs:
            for x in range(wx0 + 1, wx1):
                if t.get(x, h + 1, z) is None and t.get(x, h, z) == "minecraft:grass_block" and not k.is_reserved(t, x, z) \
                        and rnd.random() < 0.012 and k.free(t, x, z, h + 1, 6, margin=1):
                    k.tree(t, x, h + 1, z, "birch", "birch", rnd.randint(5, 6), 2)
        k.flowers(t, wx0, 0, wx1, D - 1, 0.06, y=h)
    k.flowers(t, wx0, 0, wx1, 51, 0.08, y=18)
    return _finish(t, entrances={"south gate": [(x, D - 1) for x in range(cx - 2, cx + 3)]})


def _retaining_face(t, x0, x1, z, below, h, cx):
    """Dresses the front of a terrace: a blind arcade with buttresses, a quartz cornice and a
    balustrade along the top (the stair gap in the middle stays open)."""
    for x in range(x0, x1 + 1):
        if abs(x - cx) <= 5:
            continue
        t.set(x, h, z, "minecraft:quartz_bricks")
        t.set(x, h + 1, z, *k.wall_block("minecraft:diorite_wall") if x % 3 else ("minecraft:quartz_pillar", {"axis": "y"}))
        if x % 3 == 0:
            k.lantern(t, x, h + 2, z)
        bay = (x - x0) % 6
        if bay == 0:
            for y in range(below + 1, h):
                t.set(x, y, z + 1, "minecraft:quartz_bricks")
            t.set(x, h - 1, z + 1, *k.stairs("minecraft:quartz_stairs", "north"))
        elif bay in (2, 3, 4):
            for y in range(below + 1, below + h - below - 2):
                t.set(x, y, z, "minecraft:smooth_quartz")
            t.set(x, below + h - below - 2, z, *k.stairs("minecraft:quartz_stairs",
                                                            "west" if bay == 2 else "east" if bay == 4 else "south", "top"))


def _serpents_apple(t, x0, z0):
    """The Serpent's Apple: a dark-timbered back-alley tavern with a red glowing sign, a long bar
    with bottles and barrels, booths, and its two famous regulars."""
    info = k.building(t, x0, z0, 12, 8, "firstia", floors=2, door_side="south", door_at=x0 + 6, use="tavern",
                      chimney_on=True, lamps=True, furnish_it=False, dormers=False,
                      overrides={"infill": ["minecraft:dark_oak_planks", "minecraft:spruce_planks"],
                                 "frame": "minecraft:stripped_dark_oak_log", "ground": ["minecraft:deepslate_bricks",
                                                                                         "minecraft:cobbled_deepslate"],
                                 "plinth": ["minecraft:deepslate_bricks"], "quoin": "minecraft:polished_deepslate",
                                 "roof": "minecraft:dark_oak_stairs", "roof_full": "minecraft:dark_oak_planks",
                                 "ridge": "minecraft:deepslate_tile_slab", "door": "dark_oak",
                                 "shutter": "minecraft:dark_oak_trapdoor", "pane": "minecraft:red_stained_glass_pane",
                                 "floor": "minecraft:dark_oak_planks", "chimney": "minecraft:deepslate_bricks"})
    k.sign(t, x0 + 8, 3, z0 + 8, "south", ["The", "Serpent's", "Apple"], wood="dark_oak", color="red", glow=True)
    # the bar along the back wall, with bottles (brewing stands), pots and barrels
    t.fill(x0 + 2, 1, z0 + 2, x0 + 7, 1, z0 + 2, "minecraft:dark_oak_slab", {"type": "top", "waterlogged": "false"})
    for x in range(x0 + 3, x0 + 8, 2):
        t.set(x, 2, z0 + 2, "minecraft:brewing_stand" if x % 4 else "minecraft:potted_red_tulip",
              {"has_bottle_0": "true", "has_bottle_1": "true", "has_bottle_2": "false"} if x % 4 else None)
    for x in range(x0 + 2, x0 + 9):
        t.set(x, 1, z0 + 1, *k.barrel("south"))
        t.set(x, 2, z0 + 1, "minecraft:bookshelf" if x % 2 else "minecraft:barrel",
              None if x % 2 else {"facing": "south", "open": "false"})
    # booths by the windows
    for (x, f) in ((x0 + 9, "west"),):
        for z in (z0 + 3, z0 + 6):
            t.set(x + 1, 1, z, *k.stairs("minecraft:dark_oak_stairs", "east"))
            t.set(x, 1, z, *k.fence("minecraft:dark_oak_fence"))
            t.set(x, 2, z, "minecraft:dark_oak_pressure_plate", {"powered": "false"})
    for x in (x0 + 2, x0 + 5):
        t.set(x, 1, z0 + 5, "minecraft:red_carpet")
    k.lantern(t, x0 + 4, 4, z0 + 4, hanging=True)
    k.lantern(t, x0 + 8, 4, z0 + 4, hanging=True)
    t.entity(x0 + 8, 1, z0 + 3, "slfrontier:pencilgon", yaw=180.0)
    t.entity(x0 + 5, 1, z0 + 4, "slfrontier:oikatzo", yaw=90.0)
    return info


def _thirdrema_castle(t, cx, base):
    """The white fairy-tale castle: a palace block with tall windows and an orange roof, round
    towers with steep, overhanging orange spires, a soaring central keep, banners and gardens."""
    kx0, kx1, kz0, kz1 = cx - 16, cx + 16, 10, 32
    k.building(t, kx0, kz0, kx1 - kx0 + 1, kz1 - kz0 + 1, "thirdrema", floors=3, h=6, y=base, door_side="south",
               roof_kind="hip", pitch=2, cross=True, dormers=True, use="guild", bed_color="cyan", chimney_on=False,
               lamps=False, door_at=cx,
               overrides={"ground": WHITE, "infill": WHITE, "quoin": "minecraft:chiseled_quartz_block",
                          "course": "minecraft:chiseled_quartz_block", "door": "warped",
                          "shutter": "minecraft:warped_trapdoor", "pane": "minecraft:light_blue_stained_glass_pane"})
    # a grand entrance: triple doors under a pillared quartz portico
    for x in range(cx - 2, cx + 3):
        for y in range(base + 1, base + 5):
            if abs(x - cx) <= 1 and y > base + 2:
                continue
            t.set(x, y, kz1 + 1, "minecraft:air")
    for dx in (-1, 0, 1):
        k.door(t, cx + dx, base + 1, kz1, "south", "warped", hinge="left" if dx < 0 else "right")
        t.set(cx + dx, base + 3, kz1, "minecraft:chiseled_quartz_block")
    for x in (cx - 3, cx + 3):
        for y in range(base + 1, base + 6):
            t.set(x, y, kz1 + 3, "minecraft:quartz_pillar", {"axis": "y"})
    for x in range(cx - 4, cx + 5):
        for z in range(kz1 + 1, kz1 + 4):
            t.set(x, base + 6, z, *k.slab("minecraft:quartz_slab", "double" if abs(x - cx) == 4 or z == kz1 + 3 else "top"))
        t.set(x, base + 7, kz1 + 3, *k.stairs("minecraft:quartz_stairs", "north"))
    for x in (cx - 2, cx + 2):
        k.lantern(t, x, base + 5, kz1 + 2, hanging=True)
    for (tx, tz, r, h) in ((kx0, kz0, 5, 34), (kx1, kz0, 5, 34), (kx0, kz1, 4, 28), (kx1, kz1, 4, 28)):
        k.round_tower(t, tx, tz, r, h, "thirdrema", base=base, roof_h=int(r * 3), roof_over=True,
                      flag=("cyan", [("rhombus", "white"), ("border", "orange")]))
    k.round_tower(t, cx, kz0 - 2, 6, 40, "thirdrema", base=base, roof_h=18, roof_over=True,
                  flag=("orange", [("rhombus", "white"), ("border", "cyan")]))
    for bx in (cx - 7, cx + 7):
        k.banner(t, bx, base + 16, kz1 + 1, "south", "cyan", 3, trim="white", patterns=[("rhombus", "white"),
                                                                                     ("border", "orange")])
    # forecourt with gardens, a carpet of quartz and lamps
    for z in range(kz1 + 1, 52):
        for x in range(cx - 4, cx + 5):
            t.set(x, base, z, "minecraft:quartz_bricks" if abs(x - cx) == 4 else "minecraft:polished_diorite")
    k.reserve(t, cx - 2, kz1 + 1, cx + 2, 51, base)
    for z in range(kz1 + 3, 50, 5):
        for x in (cx - 5, cx + 5):
            k.lamp_post(t, x, base + 1, z, 4, post="minecraft:birch_fence", base="minecraft:diorite_wall")
    for (x0, x1) in ((cx - 24, cx - 8), (cx + 8, cx + 24)):
        for z0 in (kz1 + 3, kz1 + 10):
            k.flower_bed(t, x0, z0, x1, z0 + 4, edge="minecraft:birch_trapdoor", y=base,
                         kinds=["minecraft:white_tulip", "minecraft:orange_tulip", "minecraft:cornflower",
                                "minecraft:lily_of_the_valley", "minecraft:azure_bluet"])
    # the west parterre: gravel walks between box hedges round a fountain
    gx0, gz0, gx1, gz1 = 8, 8, 30, 48
    fx, fz = (gx0 + gx1) // 2, (gz0 + gz1) // 2
    for x in range(gx0, gx1 + 1):
        for z in range(gz0, gz1 + 1):
            walk = (x - gx0) % 6 in (0, 1) or (z - gz0) % 6 in (0, 1) or math.hypot(x - fx, z - fz) < 6.5
            if walk:
                t.set(x, base, z, "minecraft:gravel" if (x + z) % 5 else "minecraft:polished_diorite")
            elif (x - gx0) % 6 in (2, 5) or (z - gz0) % 6 in (2, 5):
                t.set(x, base + 1, z, "minecraft:azalea_leaves", {"persistent": "true", "distance": "1",
                                                                 "waterlogged": "false"})
            else:
                t.set(x, base + 1, z, t.rnd.choice(["minecraft:white_tulip", "minecraft:orange_tulip",
                                                    "minecraft:cornflower", "minecraft:lily_of_the_valley"]))
    k.fountain(k.lifted(t, base), fx, fz, 4, block="minecraft:calcite", rim="minecraft:quartz_bricks",
               floor="minecraft:prismarine_bricks", trim="minecraft:chiseled_quartz_block",
               coping="minecraft:quartz_slab", apron="minecraft:polished_diorite", lamp=False)
    # the east garden: an orchard of birches round a copper-domed gazebo
    ex, ez = cx + 34, 26
    for (dx, dz) in ((-2, -2), (2, -2), (-2, 2), (2, 2)):
        for y in range(base + 1, base + 5):
            t.set(ex + dx, y, ez + dz, "minecraft:quartz_pillar", {"axis": "y"})
    for dx in range(-3, 4):
        for dz in range(-3, 4):
            t.set(ex + dx, base, ez + dz, "minecraft:polished_diorite" if (dx + dz) % 2 else "minecraft:quartz_bricks")
            if max(abs(dx), abs(dz)) <= 3:
                t.set(ex + dx, base + 5, ez + dz, *k.slab("minecraft:quartz_slab"))
    k.cone_roof(t, ex, ez, 3, base + 6, 5, {"roof": "minecraft:waxed_oxidized_cut_copper_stairs",
                                            "roof_full": "minecraft:waxed_oxidized_cut_copper",
                                            "ridge": "minecraft:waxed_oxidized_cut_copper_slab",
                                            "roof_under": "minecraft:quartz_stairs"}, finial="minecraft:gold_block")
    k.lantern(t, ex, base + 4, ez, hanging=True)
    k.bench(t, ex - 1, ez + 1, "north", 3, "birch", y=base + 1, arms=False)
    for (x, z) in ((cx + 24, 8), (cx + 30, 12), (cx + 42, 10), (cx + 46, 20), (cx + 46, 32), (cx + 42, 44),
                   (cx + 26, 46), (cx + 34, 40), (cx - 26, 4), (cx + 26, 4)):
        if k.free(t, x, z, base + 1, 7, margin=1) and t.get(x, base, z) == "minecraft:grass_block":
            k.tree(t, x, base + 1, z, "birch", "birch", 6, 2)


# ---------------------------------------------------------------------------------------
# Rabituza: the hidden rabbit nation, its giant cherry tree, red palace and the Vorpal Colosseum
# ---------------------------------------------------------------------------------------

PAGODA = {"post": "minecraft:red_concrete", "panel": ["minecraft:white_concrete", "minecraft:white_concrete",
                                                     "minecraft:calcite"],
          "lattice": "minecraft:red_stained_glass_pane", "beam": "minecraft:stripped_dark_oak_log",
          "floor": "minecraft:dark_oak_planks", "deck": "minecraft:dark_oak_slab", "rail": "minecraft:dark_oak_fence",
          "roof": "minecraft:deepslate_tile_stairs", "roof_full": "minecraft:deepslate_tiles",
          "ridge": "minecraft:deepslate_tile_slab", "roof_under": "minecraft:dark_oak_stairs", "door": "dark_oak"}
RABBIT_PATH = ["minecraft:dirt_path", "minecraft:dirt_path", "minecraft:coarse_dirt", "minecraft:packed_mud"]


def rabituza():
    S = 128
    t = Template(S, 80, S, "rabituza")
    c = S // 2
    rnd = t.rnd
    _grass(t, 0, 0, S - 1, S - 1)
    tx, tz = c, c + 8  # the giant tree
    ring_r = 13
    # Paths. The north road forks round the palace terrace; the south road meets a ring path
    # round the giant tree; the east-west road runs below the tree.
    path = dict(block="minecraft:dirt_path", edge=None, mix=RABBIT_PATH)
    k.road(t, c - 2, 0, c + 2, 6, "minecraft:dirt_path", "minecraft:mud_bricks", mix=RABBIT_PATH)
    k.road(t, 37, 4, 90, 6, **path)
    k.road(t, 37, 4, 39, 50, **path)
    k.road(t, 88, 4, 90, 50, **path)
    k.road(t, 37, 48, 57, 50, **path)
    k.road(t, 71, 48, 90, 50, **path)
    k.road(t, c - 2, 44, c + 2, tz - ring_r, "minecraft:dirt_path", "minecraft:mud_bricks", mix=RABBIT_PATH)
    k.road(t, c - 2, tz + ring_r, c + 2, S - 1, "minecraft:dirt_path", "minecraft:mud_bricks", mix=RABBIT_PATH)
    k.road(t, 0, 87, S - 1, 91, "minecraft:dirt_path", "minecraft:mud_bricks", mix=RABBIT_PATH)
    for x in range(tx - ring_r - 3, tx + ring_r + 4):
        for z in range(tz - ring_r - 3, tz + ring_r + 4):
            if ring_r - 1.5 <= math.hypot(x - tx, z - tz) <= ring_r + 1.5:
                k.put(t, x, 0, z, ["minecraft:mud_bricks", "minecraft:packed_mud", "minecraft:dirt_path"])
                k.reserve(t, x, z, x, z)
    # The palace terrace, its stair and the red pagoda palace
    px0, px1, pz0, pz1 = c - 22, c + 22, 10, 40
    for x in range(px0, px1 + 1):
        for z in range(pz0, pz1 + 1):
            edge = x in (px0, px1) or z in (pz0, pz1)
            for y in range(0, 3):
                t.set(x, y, z, "minecraft:polished_blackstone_bricks" if edge or y < 2 else "minecraft:dirt")
            t.set(x, 3, z, "minecraft:polished_blackstone_bricks" if edge else "minecraft:dark_oak_planks")
            if edge:
                if (x + z) % 2 == 0:
                    t.set(x, 4, z, *k.wall_block("minecraft:polished_blackstone_brick_wall"))
                else:
                    t.set(x, 4, z, *k.slab("minecraft:polished_blackstone_brick_slab"))
    for i in range(1, 4):
        for x in range(c - 4, c + 5):
            t.set(x, i, pz1 + 4 - i, *k.stairs("minecraft:polished_blackstone_brick_stairs", "north"))
            for yy in range(i + 1, 6):
                t.set(x, yy, pz1 + 4 - i, "minecraft:air")
    for x in range(c - 4, c + 5):
        t.set(x, 4, pz1, "minecraft:air")
    for x in (c - 6, c + 6):
        k.stone_lantern(t, x, pz1 + 2, 1)
    _palace(t, c, pz0, pz1)
    # The plaza before the palace: waypoint, spawn and a torii over the approach
    k.pave_disc(t, c, pz1 + 9, 7, ["minecraft:mud_bricks", "minecraft:packed_mud", "minecraft:mud_bricks"])
    t.ring(c, 0, pz1 + 9, 7, "minecraft:polished_andesite", width=0.5)
    k.waypoint(t, c + 5, 0, pz1 + 6, base="minecraft:polished_andesite")
    k.marker(t, c - 3, 1, pz1 + 10, "spawn")
    for (x, z) in ((c - 6, pz1 + 13), (c + 6, pz1 + 13)):
        k.stone_lantern(t, x, z)
    k.torii(t, c, pz1 + 16, "z", span=5, height=7)
    k.torii(t, c, 2, "z", span=5, height=7)
    k.torii(t, c, S - 3, "z", span=5, height=7)
    k.torii(t, 3, 89, "x", span=4, height=7)
    k.torii(t, S - 4, 89, "x", span=4, height=7)
    # The giant tree with its cherry crown, lanterns round the ring path and a shrine
    k.giant_tree(t, tx, tz, 1, 6, 44, 26, "oak", "cherry", keep=lambda x, z: k.is_reserved(t, x, z))
    for a in range(0, 360, 30):
        x = round(tx + (ring_r + 2.5) * math.cos(math.radians(a + 15)))
        z = round(tz + (ring_r + 2.5) * math.sin(math.radians(a + 15)))
        if k.free(t, x, z, 1, 4):
            k.stone_lantern(t, x, z)
    _shrine(t, 46, 70)
    # The Vorpal Colosseum, south of the east-west road. The "arena" marker is the trial's centre:
    # its foes appear 6..14 blocks north of it and the challenger 6 south, all on the sand.
    ax, az = 98, 110
    _colosseum(t, ax, az, 22, 16, 16, 11)
    k.marker(t, ax, 1, az + 4, "arena")
    k.road(t, ax - 2, 92, ax + 2, 93, "minecraft:smooth_sandstone", None)
    # Bilac's forge
    _bilac_forge(t, 8, c - 18)
    # Japanese houses
    for (x, z, w, d, side) in ((10, 8, 9, 7, "south"), (22, 10, 9, 9, "east"), (96, 10, 9, 7, "south"),
                               (106, 22, 9, 9, "west"), (8, 24, 9, 9, "east"), (94, 24, 9, 9, "south"),
                               (100, 48, 11, 9, "west"), (100, 62, 9, 9, "west"), (100, 74, 11, 9, "north"),
                               (81, 76, 9, 9, "north"), (14, 68, 9, 9, "east"), (8, 96, 9, 9, "north"),
                               (22, 112, 9, 9, "north"), (42, 112, 9, 9, "east"), (44, 96, 9, 9, "north")):
        _japanese_house(t, x, z, w, d, side)
    # Burrows in grassy mounds, carrot fields, a koi pond and cherry trees
    for (bx, bz, r, side) in ((28, 46, 5, "east"), (30, 60, 4, "east"), (90, 60, 5, "west"), (96, 40, 4, "south"),
                              (36, 122, 4, "north"), (16, 120, 4, "north"), (57, 117, 4, "east")):
        _burrow(t, bx, bz, r, side)
    _gardens(t, 4, 80, 26, 85, crops=["minecraft:carrots"])
    _gardens(t, 20, 99, 40, 105, crops=["minecraft:carrots"])
    _gardens(t, 30, 78, 46, 84, crops=["minecraft:carrots", "minecraft:potatoes"])
    _koi_pond(t, 28, 30)
    for (x, z) in ((6, 40), (44, 108), (120, 50), (122, 38), (40, 60), (66, 104), (30, 118), (80, 58), (112, 84),
                   (36, 22), (118, 8), (3, 60), (84, 66), (76, 98), (8, 110), (50, 88 - 10)):
        if k.free(t, x, z, 1, 6, margin=1):
            k.cherry_tree(t, x, 1, z, rnd.randint(5, 6), 3)
    # lantern-lit paths
    for z in range(10, S - 4, 8):
        for x in (c - 3, c + 3):
            if k.free(t, x, z, 1, 4):
                k.stone_lantern(t, x, z)
    for x in range(6, S - 4, 9):
        for z in (86, 92):
            if k.free(t, x, z, 1, 4):
                k.stone_lantern(t, x, z)
    k.flowers(t, 2, 2, S - 3, S - 3, 0.12, ["minecraft:short_grass", "minecraft:pink_petals", "minecraft:allium",
                                             "minecraft:short_grass", "minecraft:azure_bluet", "minecraft:lily_of_the_valley"])
    return _finish(t, entrances={"north road": [(x, 0) for x in range(c - 2, c + 3)],
                                 "south road": [(x, S - 1) for x in range(c - 2, c + 3)],
                                 "west road": [(0, z) for z in range(87, 92)],
                                 "east road": [(S - 1, z) for z in range(87, 92)]})


def _palace(t, c, pz0, pz1):
    """The red-lacquer palace: three pagoda storeys stepping in, each with a railed gallery
    and a flaring tile skirt with gilded tips, crowned by a gold-and-copper spire; inside, the
    throne hall with a gold dais, red carpet, vermilion pillars and lanterns."""
    tiers = [(c - 16, pz0 + 4, c + 16, pz1 - 6, 6), (c - 11, pz0 + 8, c + 11, pz1 - 10, 5),
             (c - 6, pz0 + 11, c + 6, pz1 - 13, 5)]
    y0 = 3
    top = y0
    for i, (x0, z0, x1, z1, h) in enumerate(tiers):
        inner = tiers[i + 1][:4] if i + 1 < len(tiers) else None
        doors = ((c - 1, z1, "south"), (c, z1, "south"), (c + 1, z1, "south")) if i == 0 else ()
        k.pagoda_tier(t, x0, z0, x1, z1, y0, h, PAGODA, inner=inner, doors=doors, balcony=True)
        top = y0 + h
        if inner:
            nx0, nz0, nx1, nz1 = inner
            for yy in range(y0 + h + 1, y0 + h + 4):
                t.fill(nx0, yy, nz0, nx1, yy, nz1, "minecraft:dark_oak_planks")
            y0 = y0 + h + 3
    x0, z0, x1, z1, h = tiers[-1]
    field = k.roof(t, [k.hip_part(x0, z0, x1, z1, top + 1, 1, 3)], top + 1, PAGODA, upturn=True)
    peak = max(v[0] for v in field.values())
    spire = ["minecraft:gold_block", "minecraft:waxed_cut_copper", "minecraft:gold_block", "minecraft:waxed_cut_copper",
             "minecraft:gold_block"]
    sz = (z0 + z1) // 2
    for i, b in enumerate(spire):
        t.set(c, peak + 1 + i, sz, b)
    t.set(c, peak + 1 + len(spire), sz, "minecraft:lightning_rod", {"facing": "up", "powered": "false",
                                                                   "waterlogged": "false"})
    # an opening in the gallery rail before the doors
    bx0, bz0, bx1, bz1, bh = tiers[0]
    for x in range(c - 1, c + 2):
        t.set(x, 4, bz1 + 2, "minecraft:air")
        t.set(x, 3, bz1 + 2, *k.slab("minecraft:dark_oak_slab", "top"))
        t.set(x, 3, bz1 + 3, "minecraft:dark_oak_planks")
    k.reserve(t, c - 1, bz1 + 1, c + 1, bz1 + 3, 3)
    # the throne hall: carpet, a gold dais with a quartz throne, vermilion pillars, lanterns
    for x in range(c - 1, c + 2):
        for z in range(pz0 + 9, bz1):
            t.set(x, 4, z, "minecraft:red_carpet")
    t.fill(c - 4, 4, pz0 + 5, c + 4, 4, pz0 + 7, "minecraft:gold_block")
    t.fill(c - 3, 4, pz0 + 8, c + 3, 4, pz0 + 8, "minecraft:quartz_stairs",
           {"facing": "north", "half": "bottom", "shape": "straight", "waterlogged": "false"})
    t.set(c, 5, pz0 + 5, "minecraft:quartz_stairs", {"facing": "south", "half": "bottom", "shape": "straight",
                                                     "waterlogged": "false"})
    for x in (c - 1, c + 1):
        t.set(x, 5, pz0 + 5, "minecraft:red_wool")
        t.set(x, 6, pz0 + 5, "minecraft:red_wool")
    for x in (c - 4, c + 4):
        t.set(x, 5, pz0 + 6, "minecraft:gold_block")
        k.lantern(t, x, 6, pz0 + 6)
    for x in range(bx0 + 3, bx1 - 2, 4):
        if abs(x - c) <= 5:
            continue
        for z in (bz0 + 3, bz1 - 3):
            for y in range(4, 3 + bh):
                t.set(x, y, z, "minecraft:red_concrete")
    for x in range(bx0 + 4, bx1 - 2, 6):
        k.lantern(t, x, 3 + bh - 1, (bz0 + bz1) // 2, hanging=True)
    t.entity(c, 5, pz0 + 7, "slfrontier:vysache", yaw=180.0)


def _japanese_house(t, x0, z0, w, d, side):
    """A raised house: stone footing, a cherry-wood engawa veranda all round with corner posts,
    dark posts and white walls, bamboo shutters, a hipped tile roof with upturned eaves, a step
    up to the door and a stone lantern beside it."""
    info = k.building(t, x0, z0, w, d, "rabituza", floors=1, h=5, y=1, door_side=side, roof_kind="hip", overhang=2,
                      upturn=True, use="japanese", lamps=False, chimney_on=False,
                      bed_color=t.rnd.choice(["pink", "white", "red"]))
    x1, z1 = x0 + w - 1, z0 + d - 1
    for x in range(x0 - 1, x1 + 2):
        for z in range(z0 - 1, z1 + 2):
            if x in (x0 - 1, x1 + 1) or z in (z0 - 1, z1 + 1):
                if k.is_air(t, x, 1, z) or t.get(x, 1, z) in ("minecraft:short_grass",):
                    t.set(x, 1, z, *k.slab("minecraft:cherry_slab", "top"))
                if x in (x0 - 1, x1 + 1) and z in (z0 - 1, z1 + 1):
                    for y in range(2, 8):
                        t.set(x, y, z, *k.fence("minecraft:dark_oak_fence"))
    ox, oz = k.FACING[side]
    dx, dz = info["door"]
    t.set(dx + ox, 1, dz + oz, *k.stairs("minecraft:cherry_stairs", k.OPPOSITE[side]))
    px, pz = (1, 0) if side in ("north", "south") else (0, 1)
    lx, lz = dx + ox * 2 + px * 2, dz + oz * 2 + pz * 2
    if k.free(t, lx, lz, 1, 4):
        k.stone_lantern(t, lx, lz)
    return info


def _burrow(t, cx, cz, r, door_facing):
    """A rabbit burrow: a grassy mound with a round, framed door, little round windows, a
    stove-pipe chimney, a lantern on a post and a carrot patch."""
    for x in range(cx - r - 1, cx + r + 2):
        for z in range(cz - r - 1, cz + r + 2):
            for y in range(1, r + 1):
                d = math.sqrt((x - cx) ** 2 + (y * 1.3) ** 2 + (z - cz) ** 2)
                if d <= r:
                    if d <= r - 1.2:
                        t.set(x, y, z, "minecraft:air")
                    elif math.sqrt((x - cx) ** 2 + ((y + 1) * 1.3) ** 2 + (z - cz) ** 2) > r:
                        t.set(x, y, z, "minecraft:grass_block", {"snowy": "false"})
                    else:
                        t.set(x, y, z, "minecraft:rooted_dirt")
    for x in range(cx - r + 1, cx + r):
        for z in range(cz - r + 1, cz + r):
            if math.hypot(x - cx, z - cz) <= r - 1:
                t.set(x, 0, z, "minecraft:spruce_planks")
    dx, dz = k.FACING[door_facing]
    px, pz = (1, 0) if door_facing in ("north", "south") else (0, 1)
    fx, fz = cx + dx * (r - 1), cz + dz * (r - 1)
    for i in range(r - 2, r + 1):
        for y in (1, 2):
            t.set(cx + dx * i, y, cz + dz * i, "minecraft:air")
    for s in (-1, 1):
        for y in (1, 2):
            t.set(fx + px * s, y, fz + pz * s, "minecraft:stripped_spruce_log", {"axis": "y"})
        t.set(fx + px * s, 3, fz + pz * s, *k.stairs("minecraft:spruce_stairs", k.facing_of(-px * s, -pz * s), "top"))
    t.set(fx, 3, fz, "minecraft:stripped_spruce_log", {"axis": "x" if px else "z"})
    k.door(t, fx, 1, fz, door_facing, "spruce")
    k.reserve(t, fx + dx, fz + dz, fx + dx, fz + dz)
    k.reserve(t, fx - dx, fz - dz, fx - dx, fz - dz)
    for i in range(r, r + 3):
        if not k.is_reserved(t, cx + dx * i, cz + dz * i) or i == r:
            k.put(t, cx + dx * i, 0, cz + dz * i, ["minecraft:dirt_path", "minecraft:coarse_dirt"])
    for s in (-1, 1):
        t.set(cx + px * s * (r - 1), 2, cz + pz * s * (r - 1), *k.pane("minecraft:glass_pane"))
    t.set(cx + px, r, cz + pz, "minecraft:bricks")
    t.set(cx + px, r + 1, cz + pz, *k.wall_block("minecraft:brick_wall"))
    lx, lz = fx + dx + px * 2, fz + dz + pz * 2
    if k.free(t, lx, lz, 1, 3):
        k.lamp_post(t, lx, 1, lz, 2, post="minecraft:spruce_fence")
    k.lantern(t, cx, 1, cz)
    t.set(cx - px, 1, cz - pz, "minecraft:hay_block", {"axis": "y"})
    t.set(cx + px, 1, cz + pz, *k.barrel("up"))
    gx, gz = fx + dx * 2 - px * 3, fz + dz * 2 - pz * 3
    for a in range(-1, 2):
        for b in range(0, 2):
            x, z = gx + px * a + dx * b, gz + pz * a + dz * b
            if k.free(t, x, z, 1, 2) and t.get(x, 0, z) == "minecraft:grass_block":
                t.set(x, 0, z, "minecraft:farmland", {"moisture": "7"})
                t.set(x, 1, z, "minecraft:carrots", {"age": "7"})


def _shrine(t, x, z):
    """A little roadside shrine facing the great tree: a stone base, a roofed offering box,
    a tiny torii, pots of offerings and a pair of stone lanterns."""
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            t.set(x + dx, 0, z + dz, "minecraft:polished_andesite")
    t.set(x, 1, z, "minecraft:polished_andesite")
    t.set(x, 2, z, "minecraft:spruce_planks")
    t.set(x, 3, z - 1, *k.stairs("minecraft:dark_oak_stairs", "south"))
    t.set(x, 3, z + 1, *k.stairs("minecraft:dark_oak_stairs", "north"))
    t.set(x, 3, z, *k.slab("minecraft:dark_oak_slab", "bottom"))
    for dz in (-1, 1):
        t.set(x + 2, 1, z + dz, "minecraft:red_concrete")
        t.set(x + 2, 2, z + dz, "minecraft:red_concrete")
    for dz in (-2, -1, 0, 1, 2):
        t.set(x + 2, 3, z + dz, "minecraft:polished_blackstone")
    t.set(x + 1, 1, z - 1, "minecraft:decorated_pot", {"facing": "east", "cracked": "false", "waterlogged": "false"})
    t.set(x + 1, 1, z + 1, "minecraft:potted_flowering_azalea_bush")
    for dz in (-3, 3):
        k.stone_lantern(t, x + 1, z + dz)


def _koi_pond(t, cx, cz):
    """A garden pond with mossy boulders, lily pads and a red arched bridge across it."""
    cells = [(x, z) for x in range(cx - 6, cx + 7) for z in range(cz - 4, cz + 5)
             if ((x - cx) / 6.2) ** 2 + ((z - cz) / 4.2) ** 2 <= 1]
    rims = k.basin(t, cells, 1, ["minecraft:mossy_cobblestone", "minecraft:stone", "minecraft:andesite"],
                   "minecraft:mud")
    for (x, z) in sorted(rims):
        if t.rnd.random() < 0.3:
            t.set(x, 2, z, "minecraft:moss_carpet")
    for (x, z) in t.rnd.sample(sorted(c for c in cells if abs(c[0] - cx) > 1), 6):
        t.set(x, 2, z, "minecraft:lily_pad")
    # the bridge runs north-south over the narrow middle
    for z in range(cz - 7, cz + 8):
        off = abs(z - cz)
        for x in (cx - 1, cx, cx + 1):
            if off == 7:
                t.set(x, 1, z, *k.stairs("minecraft:crimson_stairs", "north" if z > cz else "south"))
            elif off == 6:
                t.set(x, 2, z, *k.stairs("minecraft:crimson_stairs", "north" if z > cz else "south"))
            elif off == 2:
                t.set(x, 2, z, *k.slab("minecraft:crimson_slab", "top"))
                t.set(x, 3, z, *k.stairs("minecraft:crimson_stairs", "north" if z > cz else "south"))
            elif off < 2:
                t.set(x, 3, z, *k.slab("minecraft:crimson_slab", "top"))
            else:
                t.set(x, 2, z, *k.slab("minecraft:crimson_slab", "top"))
        if off <= 6:
            ry = 4 if off <= 2 else 3
            for x in (cx - 2, cx + 2):
                t.set(x, ry, z, *k.fence("minecraft:crimson_fence"))
                t.set(x, ry - 1, z, *k.slab("minecraft:crimson_slab", "top"))
    for (x, z) in ((cx - 4, cz - 6), (cx + 4, cz + 6)):
        k.stone_lantern(t, x, z)


def _bilac_forge(t, x0, z0):
    """Bilac's forge: a timber workshop with a brick hearth wall, a blast furnace, a lava
    crucible, an anvil and a tall chimney."""
    k.building(t, x0, z0, 13, 11, "rabituza", floors=1, h=6, door_side="east", roof_kind="hip", overhang=2,
               upturn=True, use="smithy", lamps=False, chimney_on=False, sign=["Bilac's", "Forge"],
               overrides={"infill": ["minecraft:spruce_planks", "minecraft:stripped_spruce_log"],
                          "plinth": ["minecraft:stone_bricks", "minecraft:cobblestone"]})
    hx = x0 + 1
    for z in range(z0 + 1, z0 + 10):
        for y in range(1, 4):
            t.set(hx, y, z, "minecraft:bricks")
    t.set(hx + 1, 1, z0 + 3, "minecraft:blast_furnace", {"facing": "east", "lit": "true"})
    t.set(hx + 1, 1, z0 + 5, "minecraft:bricks")
    t.set(hx + 1, 1, z0 + 7, "minecraft:furnace", {"facing": "east", "lit": "true"})
    t.set(hx + 1, 2, z0 + 5, "minecraft:bricks")
    t.set(hx, 1, z0 + 5, "minecraft:lava_cauldron")
    k.chimney(t, x0, z0 + 5, 1, 13, "minecraft:bricks")
    t.set(x0 + 5, 1, z0 + 4, "minecraft:anvil", {"facing": "east"})
    for y in (1, 2):
        t.set(x0 + 5, y, z0 + 6, "minecraft:air")
    t.entity(x0 + 5, 1, z0 + 6, "slfrontier:bilac", yaw=90.0)
    _station(t, x0 + 6, 1, z0 + 7, "frontier_forge", "east")


def _colosseum(t, cx, cz, rx, rz, ix, iz):
    """The Vorpal Colosseum: an oval sand floor (ix x iz half-axes) ringed by stepped stone
    stands and an arcaded outer wall (rx x rz), gates north and south under red arches, a low
    parapet and lamps round the sand."""
    for x in range(cx - rx - 2, cx + rx + 3):
        for z in range(cz - rz - 2, cz + rz + 3):
            e_floor = ((x - cx) / ix) ** 2 + ((z - cz) / iz) ** 2
            e_outer = ((x - cx) / (rx + 1)) ** 2 + ((z - cz) / (rz + 1)) ** 2
            if e_floor <= 1:
                t.set(x, 0, z, "minecraft:sand" if (x * 7 + z * 3) % 9 else "minecraft:smooth_sandstone")
                k.reserve(t, x, z, x, z)
                continue
            if e_outer > 1:
                continue
            frac = (math.sqrt(e_floor) - 1) / ((rx + 1) / ix - 1)
            tier = min(5, int(frac * 5) + 1)
            t.fill(x, 0, z, x, tier, z, "minecraft:stone_bricks")
            if e_outer > 0.84:
                angle = math.degrees(math.atan2(z - cz, x - cx)) % 24
                for y in range(tier + 1, 11):
                    arch = y <= 8 and 6 <= angle <= 18
                    t.set(x, y, z, "minecraft:air" if arch else ("minecraft:polished_andesite" if y == 9
                                                                 else "minecraft:smooth_stone"))
                t.set(x, 11, z, *k.slab("minecraft:smooth_stone_slab"))
    for x in range(cx - ix - 2, cx + ix + 3):
        for z in range(cz - iz - 2, cz + iz + 3):
            e_floor = ((x - cx) / ix) ** 2 + ((z - cz) / iz) ** 2
            if 1 < e_floor <= 1.3 and t.get(x, 1, z) == "minecraft:stone_bricks" and t.get(x, 2, z) is None:
                t.set(x, 2, z, *k.wall_block("minecraft:red_sandstone_wall"))
    for sgn in (-1, 1):
        for dz in range(iz - 1, rz + 3):
            z = cz + sgn * dz
            for x in range(cx - 2, cx + 3):
                t.set(x, 0, z, "minecraft:smooth_sandstone")
                for y in range(1, 6):
                    t.set(x, y, z, "minecraft:air")
                k.reserve(t, x, z, x, z)
        z = cz + sgn * (rz + 1)
        for x in range(cx - 3, cx + 4):
            t.set(x, 6, z, "minecraft:chiseled_red_sandstone")
        for x in (cx - 3, cx + 3):
            for y in range(1, 6):
                t.set(x, y, z, "minecraft:red_sandstone")
            k.banner(t, x, 4, z + sgn, "south" if sgn > 0 else "north", "red", 1,
                     patterns=[("rhombus", "white"), ("border", "black")])
    for a in range(0, 360, 30):
        x = round(cx + (ix + 1.5) * math.cos(math.radians(a)))
        z = round(cz + (iz + 1.5) * math.sin(math.radians(a)))
        if abs(x - cx) > 3 and t.get(x, 2, z) == "minecraft:red_sandstone_wall":
            k.lantern(t, x, 3, z)


# ---------------------------------------------------------------------------------------
# The Serpent Bridge: a great stone bridge over a river gorge, the Ravenous Snake's arena
# ---------------------------------------------------------------------------------------

SERPENT = ["minecraft:dark_prismarine", "minecraft:prismarine_bricks", "minecraft:dark_prismarine"]


def serpent_bridge():
    W, H, D = 84, 44, 56
    G = 16  # template layer that lines up with the natural ground
    t = Template(W, H, D, "serpent_bridge")
    rnd = t.rnd
    g0, g1 = 24, 59  # gorge across x
    cz = D // 2
    water = 5
    # The gorge and its river (unchanged: the world's river is aligned to it).
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
    bx0, bx1 = 12, W - 13
    mid = (bx0 + bx1) // 2
    # Approach roads on both banks, the full width of the deck, out to the template edges
    for (x0, x1) in ((0, bx0 - 1), (bx1 + 1, W - 1)):
        k.road(t, x0, cz - 5, x1, cz + 5, "minecraft:stone_bricks", "minecraft:polished_andesite",
               mix=["minecraft:cobblestone", "minecraft:andesite", "minecraft:mossy_stone_bricks", "minecraft:gravel"],
               y=G)
    # Piers with pointed cutwaters (upstream and downstream), standing in the river
    piers = (mid - 16, mid, mid + 16)
    for px in piers:
        for y in range(0, G - 1):
            for x in range(px - 2, px + 3):
                for z in range(cz - 6, cz + 7):
                    t.set(x, y, z, "minecraft:mossy_stone_bricks" if y <= water + 1 else "minecraft:stone_bricks")
            for (sgn, f) in ((-1, "south"), (1, "north")):
                for i, half in enumerate((2, 1, 0)):
                    z = cz + sgn * (7 + i)
                    for x in range(px - half, px + half + 1):
                        t.set(x, y, z, "minecraft:mossy_stone_bricks" if y <= water + 1 else "minecraft:stone_bricks")
        for (sgn, f) in ((-1, "south"), (1, "north")):
            for i, half in enumerate((2, 1, 0)):
                z = cz + sgn * (7 + i)
                for x in range(px - half, px + half + 1):
                    t.set(x, G - 1, z, *k.stairs("minecraft:stone_brick_stairs", f))
    # pedestrian refuges: the deck bulges out over each cutwater
    refuges = {(x, cz + sgn * dz) for px in piers for x in range(px - 2, px + 3) for sgn in (-1, 1) for dz in (6, 7)}
    # Arches between the piers and out to the banks: a ring of voussoirs on each face,
    # spandrels above, a corbelled cornice under the deck edge
    spans = ((bx0 + 2, piers[0] - 2), (piers[0] + 2, piers[1] - 2), (piers[1] + 2, piers[2] - 2), (piers[2] + 2, bx1 - 2))
    for (a, b) in spans:
        span = b - a
        for x in range(a + 1, b):
            u = (x - a) / span
            top = int(G - 2 - math.sin(u * math.pi) * (G - 9))
            for y in range(top, G - 1):
                for z in range(cz - 5, cz + 6):
                    t.set(x, y, z, "minecraft:stone_bricks")
            for z in (cz - 5, cz + 5):
                t.set(x, top, z, "minecraft:chiseled_stone_bricks" if x % 3 == 0 else "minecraft:polished_andesite")
                if top + 1 < G - 1:
                    t.set(x, top + 1, z, "minecraft:polished_andesite")
    for x in range(bx0, bx1 + 1):
        for (z, f) in ((cz - 6, "south"), (cz + 6, "north")):
            if t.get(x, G - 1, z) in (None, "minecraft:air", "minecraft:water"):
                t.set(x, G - 1, z, *k.stairs("minecraft:stone_brick_stairs", f, "top"))
    # the serpent carved along both faces: a body winding through the spandrels
    for x in range(bx0 + 2, bx1 - 1):
        yy = G - 4 + round(1.6 * math.sin(x * 0.45))
        for z in (cz - 5, cz + 5):
            if t.get(x, yy, z) == "minecraft:stone_bricks":
                t.set(x, yy, z, SERPENT[x % 3])
            if t.get(x, yy + 1, z) == "minecraft:stone_bricks" and x % 4 == 0:
                t.set(x, yy + 1, z, "minecraft:prismarine")
    # The deck: paved, with balustrades, lamp pillars and the round arena platform in the middle
    for x in range(bx0, bx1 + 1):
        for z in range(cz - 5, cz + 6):
            t.set(x, G, z, "minecraft:polished_andesite" if z in (cz - 5, cz + 5) else
                  k.pick(t, ["minecraft:stone_bricks", "minecraft:stone_bricks", "minecraft:cracked_stone_bricks",
                             "minecraft:andesite"]))
            t.set(x, G - 1, z, "minecraft:stone_bricks")
            for y in range(G + 1, G + 7):
                t.set(x, y, z, "minecraft:air")
    for x in range(mid - 11, mid + 12):
        for z in range(cz - 11, cz + 12):
            d = math.hypot(x - mid, z - cz)
            if d <= 10:
                t.set(x, G, z, "minecraft:stone_bricks" if d > 7 else "minecraft:polished_andesite")
                t.set(x, G - 1, z, "minecraft:stone_bricks")
                for y in range(G + 1, G + 7):
                    t.set(x, y, z, "minecraft:air")
    for (x, z) in refuges:
        t.set(x, G, z, "minecraft:polished_andesite")
        for y in range(G + 1, G + 7):
            t.set(x, y, z, "minecraft:air")
    for x in range(mid - 11, mid + 12):
        for z in range(cz - 11, cz + 12):
            d = math.hypot(x - mid, z - cz)
            if 9.6 < d <= 10.4 and abs(z - cz) > 5:
                t.set(x, G - 1, z, *k.stairs("minecraft:stone_brick_stairs", k._toward(x, z, mid, cz), "top"))
    # a coiled serpent inlaid in the arena floor
    for i in range(0, 220):
        a = i * 0.11
        r = 1.2 + a * 0.85
        if r > 7:
            break
        x = round(mid + r * math.cos(a))
        z = round(cz + r * math.sin(a))
        t.set(x, G, z, "minecraft:dark_prismarine")
    t.set(mid, G, cz, "minecraft:sea_lantern")
    t.ring(mid, G, cz, 7.5, "minecraft:chiseled_stone_bricks", width=0.5)
    # balustrades: capped walls between lamp pillars
    rail = []
    for x in range(bx0, bx1 + 1):
        for z in range(cz - 12, cz + 13):
            on_disc = math.hypot(x - mid, z - cz) <= 10.3
            on_deck = abs(z - cz) <= 5
            if on_disc or on_deck or (x, z) in refuges:
                nbs = [(x + 1, z), (x - 1, z), (x, z + 1), (x, z - 1)]
                edge = any(not (math.hypot(nx - mid, nz - cz) <= 10.3 or (abs(nz - cz) <= 5 and bx0 <= nx <= bx1)
                                or (nx, nz) in refuges) for nx, nz in nbs)
                if edge and not (x in (bx0, bx1) and on_deck):
                    rail.append((x, z))
    for (x, z) in rail:
        pillar = (x % 4 == 0 and abs(z - cz) == 5) or (abs(z - cz) > 5 and (x + z) % 5 == 0) \
            or ((x, z) in refuges and x in [p_ - 2 for p_ in piers] + [p_ + 2 for p_ in piers] and abs(z - cz) == 7)
        if pillar:
            t.set(x, G + 1, z, "minecraft:chiseled_stone_bricks")
            t.set(x, G + 2, z, *k.wall_block("minecraft:stone_brick_wall"))
            k.lantern(t, x, G + 3, z)
        else:
            t.set(x, G + 1, z, *k.wall_block("minecraft:stone_brick_wall"))
            t.set(x, G + 2, z, *k.slab("minecraft:stone_brick_slab"))
    # serpent statues on the arena's north and south edges, rearing over the river
    for sgn in (-1, 1):
        _serpent_statue(t, mid, cz + sgn * 10, G, "north" if sgn < 0 else "south")
    # Gate towers at each end: square towers either side of the road joined by an arch
    for (gx, out) in ((bx0 - 4, "west"), (bx1 + 4, "east")):
        for sgn in (-1, 1):
            k.square_tower(t, gx - 3, cz + sgn * 9 - 3, 7, 14, "firstia", roof_kind="pyramid", base=G, pitch=2,
                           flag=("green", [("flower", "lime"), ("border", "black")]))
        for x in range(gx - 1, gx + 2):
            for z in range(cz - 6, cz + 7):
                for y in range(G + 8, G + 12):
                    t.set(x, y, z, "minecraft:stone_bricks")
            for z in (cz - 5, cz + 5):
                t.set(x, G + 7, z, *k.stairs("minecraft:stone_brick_stairs", "north" if z < cz else "south", "top"))
        for z in range(cz - 6, cz + 7):
            t.set(gx, G + 11, z, "minecraft:smooth_stone")
            if z % 2 == 0:
                t.set(gx - 1, G + 12, z, "minecraft:stone_bricks")
                t.set(gx + 1, G + 12, z, "minecraft:stone_bricks")
        face = gx - 2 if out == "west" else gx + 2
        k.banner(t, face, G + 10, cz - 3, out, "green", 1, patterns=[("flower", "lime"), ("border", "black")])
        k.banner(t, face, G + 10, cz + 3, out, "green", 1, patterns=[("flower", "lime"), ("border", "black")])
        k.lantern(t, gx, G + 7, cz, hanging=True)
    # Lamps along the approaches, the waypoint beside the west road, markers
    for x in (2, 8, W - 9, W - 3):
        for z in (cz - 7, cz + 7):
            if k.free(t, x, z, G + 1, 5):
                k.street_lamp(t, x, G + 1, z, "south" if z < cz else "north", 4)
    k.waypoint(t, bx0 - 9, G, cz - 8)
    k.marker(t, 0, G, 0, "ground")
    k.marker(t, mid, G + 1, cz, "arena")
    k.marker(t, bx0 - 3, G + 1, cz, "spawn")
    k.reserve(t, bx0, cz - 4, bx1, cz + 4, G)
    for x in range(mid - 9, mid + 10):
        for z in range(cz - 9, cz + 10):
            if math.hypot(x - mid, z - cz) <= 8.6:
                k.reserve(t, x, z, x, z, G)
    for x in range(W):
        for z in range(D):
            if t.get(x, G, z) == "minecraft:grass_block" and t.get(x, G + 1, z) is None and rnd.random() < 0.15 \
                    and not k.is_reserved(t, x, z):
                t.set(x, G + 1, z, rnd.choice(["minecraft:short_grass", "minecraft:fern", "minecraft:short_grass"]))
    for (x, z) in ((3, 4), (10, 50), (75, 6), (80, 48), (4, 46), (78, 18)):
        if k.free(t, x, z, G + 1, 7, margin=1):
            k.tree(t, x, G + 1, z, "oak", "oak", 6, 2)
    return _finish(t, ground_y=G, entrances={"west bank": [(0, z) for z in range(cz - 5, cz + 6)],
                                             "east bank": [(W - 1, z) for z in range(cz - 5, cz + 6)]})


def _serpent_statue(t, x, z, g, facing):
    """A great stone serpent rearing up from a plinth at the edge of the arena, its head
    reaching out over the water, with glowing eyes and pale fangs."""
    fx, fz = k.FACING[facing]
    px, pz = (1, 0) if facing in ("north", "south") else (0, 1)
    # plinth (its outer half stands on a corbelled balcony over the river)
    for a in (-1, 0, 1):
        for b in (0, 1):
            t.set(x + px * a + fx * b, g, z + pz * a + fz * b, "minecraft:stone_bricks")
            t.set(x + px * a + fx * b, g - 1, z + pz * a + fz * b, "minecraft:stone_bricks")
            t.set(x + px * a + fx * b, g + 1, z + pz * a + fz * b, "minecraft:chiseled_stone_bricks")
            t.set(x + px * a + fx * b, g + 2, z + pz * a + fz * b, "minecraft:air")
    # a coiled base and a rising, leaning neck
    body = [(0, 0, 2), (1, 0, 2), (1, 1, 2), (0, 1, 2), (-1, 1, 2), (-1, 0, 3), (0, 0, 3), (0, 0, 4), (0, 1, 5), (0, 1, 6),
            (0, 1, 7), (0, 2, 8), (0, 2, 9)]
    for (a, b, dy) in body:
        t.set(x + px * a + fx * b, g + dy, z + pz * a + fz * b, "minecraft:dark_prismarine")
    # head
    hx, hz, hy = x + fx * 3, z + fz * 3, g + 9
    for a in (-1, 0, 1):
        for b in (0, 1):
            t.set(hx + px * a + fx * b, hy, hz + pz * a + fz * b, "minecraft:prismarine_bricks")
    t.set(hx, hy + 1, hz, "minecraft:dark_prismarine")
    t.set(hx + fx, hy + 1, hz + fz, *k.stairs("minecraft:prismarine_brick_stairs", k.OPPOSITE[facing]))
    for a in (-1, 1):
        t.set(hx + px * a, hy + 1, hz + pz * a, "minecraft:sea_lantern")
    t.set(hx + fx * 2, hy, hz + fz * 2, *k.stairs("minecraft:prismarine_brick_stairs", k.OPPOSITE[facing], "top"))
    for a in (-1, 1):
        t.set(hx + fx * 2 + px * a, hy - 1, hz + fz * 2 + pz * a, "minecraft:end_rod", {"facing": "down"})


# ---------------------------------------------------------------------------------------
# The goblin war camp, at the edge of a forest
# ---------------------------------------------------------------------------------------

GOBLIN_BANNER = [("skull", "white"), ("stripe_bottom", "red"), ("border", "black")]


def goblin_camp():
    S = 48
    t = Template(S, 24, S, "goblin_camp")
    c = S // 2
    rnd = t.rnd
    _grass(t, 0, 0, S - 1, S - 1)
    # Trampled ground inside, coarse dirt and bones scattered about
    for x in range(S):
        for z in range(S):
            d = math.hypot(x - c, z - c)
            if d < 15 and rnd.random() < 0.75 or d < 18 and rnd.random() < 0.3:
                b = rnd.choice(["minecraft:coarse_dirt", "minecraft:coarse_dirt", "minecraft:dirt", "minecraft:podzol",
                                "minecraft:rooted_dirt", "minecraft:packed_mud"])
                t.set(x, 0, z, b, {"snowy": "false"} if b == "minecraft:podzol" else None)
    # The trail in from the west, through the gate to the middle of the camp
    k.road(t, 0, c - 2, c, c + 2, "minecraft:dirt_path", None, mix=["minecraft:coarse_dirt", "minecraft:gravel",
                                                                     "minecraft:packed_mud"], chance=0.35)
    k.reserve(t, c - 1, c - 1, c + 1, c + 1)
    # The palisade: sharpened logs of uneven height, with broken gaps
    r = 17
    gaps = [(150, 160), (300, 310)]
    for a10 in range(0, 3600, 4):
        a = a10 / 10
        if 170 <= a <= 190:  # the gate (west)
            continue
        if any(g0 <= a <= g1 for g0, g1 in gaps):
            continue
        x = round(c + r * math.cos(math.radians(a)))
        z = round(c + r * math.sin(math.radians(a)) * 0.95)
        if t.get(x, 1, z) is not None or k.is_reserved(t, x, z):
            continue
        h = rnd.choice([4, 4, 5, 5, 6, 3])
        wood = rnd.choice(["minecraft:spruce_log", "minecraft:spruce_log", "minecraft:dark_oak_log", "minecraft:stripped_spruce_log"])
        for y in range(1, h + 1):
            t.set(x, y, z, wood, {"axis": "y"})
        if rnd.random() < 0.6:
            t.set(x, h + 1, z, "minecraft:pointed_dripstone", {"thickness": "tip", "vertical_direction": "up",
                                                              "waterlogged": "false"})
        elif rnd.random() < 0.3:
            t.set(x, h + 1, z, *k.skull(rnd.randint(0, 15)))
    # fallen logs by the gaps
    for (g0, g1) in gaps:
        a = math.radians((g0 + g1) / 2)
        x = round(c + (r + 2) * math.cos(a))
        z = round(c + (r + 2) * math.sin(a))
        for i in range(-2, 2):
            t.set(x + i, 1, z, "minecraft:spruce_log", {"axis": "x"})
    # the gate: two tall posts, a lintel log with skulls and banners
    gx = c - r
    for z in (c - 3, c + 3):
        for y in range(1, 7):
            t.set(gx, y, z, "minecraft:dark_oak_log", {"axis": "y"})
        t.set(gx, 7, z, *k.skull(4))
    for z in range(c - 3, c + 4):
        t.set(gx, 6, z, "minecraft:dark_oak_log", {"axis": "z"})
    for z in (c - 2, c + 2):
        k.banner(t, gx - 1, 5, z, "west", "black", 1, patterns=GOBLIN_BANNER)
    t.set(gx - 1, 6, c, *k.skull(wall_facing="west"))
    # Tents of patched hide round the camp
    for (x, z, ax, n, big) in ((13, 14, "x", 5, False), (13, 33, "x", 5, False), (32, 12, "z", 5, False),
                               (34, 34, "z", 5, False), (34, 23, "z", 7, True)):
        _tent(t, x, z, ax, n, big)
    # Skull totems
    for (x, z) in ((19, 19), (19, 29), (29, 19), (30, 29), (27, 9), (11, 24 - 6)):
        if k.free(t, x, z, 1, 4):
            _totem(t, x, z)
    # Fire pits: lit campfires ringed by stone, logs to sit on a step away
    for (x, z) in ((c + 3, c - 4), (16, 24 + 6), (25, 37)):
        _fire_pit(t, x, z)
    # A lookout platform by the gate
    _lookout(t, 9, 30)
    # crates, barrels, hay, woodpiles, a crude forge and a cage
    for (x, z) in ((21, 12), (37, 18), (20, 36), (10, 10)):
        if k.free(t, x, z, 1, 2) and k.free(t, x + 1, z, 1, 2) and k.free(t, x, z + 1, 1, 2):
            k.crate_pile(t, x, z)
    for (x, z) in ((27, 15), (17, 31)):
        if k.free(t, x, z, 1, 2):
            t.set(x, 1, z, "minecraft:hay_block", {"axis": "y"})
    k.woodpile(t, 26, 33, 3, "x")
    # a crude forge: a battered anvil, a grindstone and a rack of blades
    t.set(27, 1, 29, "minecraft:damaged_anvil", {"facing": "north"})
    t.set(28, 1, 31, "minecraft:grindstone", {"face": "floor", "facing": "west"})
    for z in (30, 31, 32):
        t.set(30, 1, z, *k.fence("minecraft:spruce_fence"))
        t.set(30, 2, z, "minecraft:iron_bars", {"waterlogged": "false"})
    # a cage of iron bars with a prisoner's skull in it
    for dx in range(3):
        for dz in range(3):
            if dx in (0, 2) or dz in (0, 2):
                for y in (1, 2):
                    t.set(14 + dx, y, 18 + dz, "minecraft:iron_bars", {"waterlogged": "false"})
            t.set(14 + dx, 3, 18 + dz, *k.slab("minecraft:spruce_slab"))
    t.set(15, 1, 19, *k.skull(3))
    # bones and skulls everywhere
    for _ in range(46):
        x, z = rnd.randint(3, S - 4), rnd.randint(3, S - 4)
        if k.free(t, x, z, 1, 2) and math.hypot(x - c, z - c) > 2:
            roll = rnd.random()
            if roll < 0.4:
                t.set(x, 1, z, "minecraft:bone_block", {"axis": rnd.choice(["x", "y", "z"])})
            elif roll < 0.7:
                t.set(x, 1, z, *k.skull(rnd.randint(0, 15)))
            elif roll < 0.85 and t.get(x, 0, z) in ("minecraft:coarse_dirt", "minecraft:dirt", "minecraft:podzol",
                                                     "minecraft:grass_block"):
                t.set(x, 1, z, "minecraft:dead_bush")
            else:
                t.set(x, 1, z, "minecraft:cobweb")
    # The forest edge: thick conifers to the east and in the corners
    for _ in range(140):
        x = rnd.randint(0, S - 1)
        z = rnd.randint(0, S - 1)
        east = x >= 41 or (x >= 36 and (z < 5 or z > 42))
        corner = math.hypot(x - c, z - c) > 23
        if not (east or corner and rnd.random() < 0.12):
            continue
        if k.free(t, x, z, 1, 10, margin=1) and t.get(x, 0, z) in ("minecraft:grass_block", "minecraft:podzol",
                                                                   "minecraft:coarse_dirt", "minecraft:dirt"):
            kind = rnd.choice(["spruce", "spruce", "dark_oak"])
            if kind == "spruce":
                k.conifer(t, x, 1, z, "spruce", rnd.randint(8, 12))
            else:
                k.tree(t, x, 1, z, "dark_oak", "dark_oak", rnd.randint(5, 7), 2)
    for x in range(36, S):
        for z in range(S):
            if t.get(x, 0, z) == "minecraft:grass_block" and rnd.random() < 0.3:
                t.set(x, 0, z, "minecraft:podzol", {"snowy": "false"})
    k.flowers(t, 0, 0, S - 1, S - 1, 0.18, ["minecraft:short_grass", "minecraft:fern", "minecraft:short_grass",
                                             "minecraft:dead_bush"], ground=("minecraft:grass_block", "minecraft:podzol"))
    k.marker(t, c, 1, c, "spawn")
    return _finish(t, entrances={"west trail": [(0, z) for z in range(c - 2, c + 3)]})


def _tent(t, x, z, axis, n, big=False):
    """An A-frame tent of patched hides on a ridge pole, open at the front, with a bedroll,
    a chest and a banner on a pole."""
    rnd = t.rnd
    hides = ["minecraft:brown_wool", "minecraft:brown_wool", "minecraft:light_gray_wool", "minecraft:white_wool",
             "minecraft:gray_wool"]
    half = 3 if big else 2
    main = rnd.choice(["minecraft:brown_wool", "minecraft:brown_wool", "minecraft:light_gray_wool", "minecraft:gray_wool"])
    ax, az = (1, 0) if axis == "x" else (0, 1)
    px, pz = (0, 1) if axis == "x" else (1, 0)
    for i in range(n):
        for w in range(-half, half + 1):
            y = half + 1 - abs(w)
            cx, cz = x + ax * i + px * w, z + az * i + pz * w
            t.set(cx, y, cz, main if rnd.random() < 0.82 else rnd.choice(hides))
            for yy in range(1, y):
                if i == n - 1 and abs(w) < half:
                    t.set(cx, yy, cz, main if rnd.random() < 0.82 else rnd.choice(hides))
    for i in (0, n - 1):
        for yy in range(1, half + 1):
            t.set(x + ax * i, yy, z + az * i, *k.fence("minecraft:spruce_fence"))
    t.set(x - ax, half + 1, z - az, *k.fence("minecraft:spruce_fence"))
    for i in range(1, n - 1):
        t.set(x + ax * i, 1, z + az * i, "minecraft:brown_carpet")
    t.set(x + ax * (n - 2) + px, 1, z + az * (n - 2) + pz,
          "minecraft:chest", {"facing": k.facing_of(-ax, -az), "type": "single", "waterlogged": "false"})
    fx, fz = x - ax * 2 + px * (half - 1), z - az * 2 + pz * (half - 1)
    if t.get(fx, 1, fz) is None:
        t.set(fx, 1, fz, *k.fence("minecraft:spruce_fence"))
        t.set(fx, 2, fz, *k.fence("minecraft:spruce_fence"))
        k.standing_banner(t, fx, 3, fz, 4 if axis == "x" else 0, "black" if big else "brown", GOBLIN_BANNER)
    if big:
        for w in (-half - 1, half + 1):
            sx, sz = x - ax + px * w, z - az + pz * w
            if t.get(sx, 1, sz) is None:
                _totem(t, sx, sz)


def _totem(t, x, z):
    """A skull totem: a bone pillar on a stone, crowned and hung with skulls."""
    rnd = t.rnd
    t.set(x, 1, z, "minecraft:cobblestone")
    h = rnd.randint(2, 3)
    for y in range(2, 2 + h):
        t.set(x, y, z, "minecraft:bone_block", {"axis": "y"})
    t.set(x, 2 + h, z, *k.skull(rnd.randint(0, 15)))
    for f in k.FACING:
        if rnd.random() < 0.5:
            dx, dz = k.FACING[f]
            if t.get(x + dx, 1 + h, z + dz) is None:
                t.set(x + dx, 1 + h, z + dz, *k.skull(wall_facing=f))


def _fire_pit(t, x, z):
    """A lit campfire in a ring of stones, with log seats a step away."""
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            t.set(x + dx, 0, z + dz, "minecraft:cobblestone")
            if (dx, dz) != (0, 0):
                t.set(x + dx, 1, z + dz, *k.slab("minecraft:cobblestone_slab") if abs(dx) + abs(dz) == 1
                      else ("minecraft:cobblestone", None))
    t.set(x, 1, z, *k.campfire(True))
    for (dx, dz, ax) in ((0, -3, "x"), (0, 3, "x"), (-3, 0, "z"), (3, 0, "z")):
        sx, sz = x + dx, z + dz
        if k.free(t, sx, sz, 1, 2) and t.rnd.random() < 0.7:
            t.set(sx, 1, sz, "minecraft:stripped_spruce_log", {"axis": ax})


def _lookout(t, x0, z0):
    """A rickety lookout: four log stilts, a plank platform with a fence rail, a ladder and a
    flag."""
    for (dx, dz) in ((0, 0), (3, 0), (0, 3), (3, 3)):
        for y in range(1, 8):
            t.set(x0 + dx, y, z0 + dz, "minecraft:spruce_log", {"axis": "y"})
    for dx in range(-1, 5):
        for dz in range(-1, 5):
            t.set(x0 + dx, 7, z0 + dz, "minecraft:spruce_planks")
            if dx in (-1, 4) or dz in (-1, 4):
                t.set(x0 + dx, 8, z0 + dz, *k.fence("minecraft:spruce_fence"))
    for y in range(1, 8):
        t.set(x0 + 1, y, z0, "minecraft:ladder", {"facing": "east", "waterlogged": "false"})
    for y in (8, 9, 10):
        t.set(x0 + 3, y, z0 + 3, *k.fence("minecraft:spruce_fence"))
    k.standing_banner(t, x0 + 3, 11, z0 + 3, 4, "black", GOBLIN_BANNER)
    t.set(x0, 8, z0 + 3, *k.barrel("up"))


ALL = {
    "town/firstia": firstia,
    "town/secondil": secondil,
    "town/thirdrema": thirdrema,
    "town/rabituza": rabituza,
    "landmark/serpent_bridge": serpent_bridge,
    "landmark/goblin_camp": goblin_camp,
}

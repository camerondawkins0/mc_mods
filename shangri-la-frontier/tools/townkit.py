"""Building blocks for the town and landmark templates in tools/towns.py.

Coordinates are template-local: x/z across the footprint, y up from the template floor
(y = 0 is the ground layer, people walk at y = 1). Everything writes into a structures.Template.

The vocabulary, roughly from small to large:
  block states ........ stairs, slab, log, trapdoor, fence, wall_block, pane, chain, ...
  streets ............. road / paving (with curbs), reserve() keeps roads and doorways clear,
                        lamp_post, street_lamp, bench, planter, well, fountain, tree, ...
  building parts ...... window (frames, sills, shutters, flower boxes), doorway, hanging_sign,
                        roof (gable / hip / cross gables / dormers on a height field),
                        chimney, balcony, awning, staircase, furnish (interiors by room use)
  buildings ........... building (half-timbered, jettied, plinths, roofs, interiors...)
  fortifications ...... curtain_wall, round_tower, square_tower, arch, portcullis
  checks .............. audit(t): spilling water, unsupported blocks, blocked roads and doors

Templates are placed with Minecraft's shape updates on, so fence/wall/pane connections and
stair corner shapes are computed in game; blocks that cannot survive where they stand (a
lantern on a bottom slab, a flower on stone, a door on a slab) would pop off. audit() catches
those, and any water that could flow out of its basin.
"""
import math

FACING = {"north": (0, -1), "south": (0, 1), "west": (-1, 0), "east": (1, 0)}
OPPOSITE = {"north": "south", "south": "north", "east": "west", "west": "east"}
CW = {"north": "east", "east": "south", "south": "west", "west": "north"}
CCW = {v: k for k, v in CW.items()}
AIR = "minecraft:air"


def facing_of(dx, dz):
    for k, v in FACING.items():
        if v == (dx, dz):
            return k
    raise ValueError((dx, dz))


# ---------------------------------------------------------------------------------------
# Block states
# ---------------------------------------------------------------------------------------

def stairs(name, facing, half="bottom", shape="straight"):
    return (name, {"facing": facing, "half": half, "shape": shape, "waterlogged": "false"})


def slab(name, kind="bottom"):
    return (name, {"type": kind, "waterlogged": "false"})


def log(name, axis="y"):
    return (name, {"axis": axis})


def trapdoor(name, facing="north", half="bottom", open_=False):
    return (name, {"facing": facing, "half": half, "open": "true" if open_ else "false", "powered": "false",
                   "waterlogged": "false"})


def fence(name):
    return (name, {"waterlogged": "false"})


def wall_block(name):
    return (name, {"waterlogged": "false"})


def pane(name="minecraft:glass_pane"):
    return (name, {"waterlogged": "false"})


def chain(axis="y"):
    return ("minecraft:chain", {"axis": axis, "waterlogged": "false"})


def barrel(facing="up"):
    return ("minecraft:barrel", {"facing": facing, "open": "false"})


def chest(t, x, y, z, facing, loot=None):
    t.set(x, y, z, "minecraft:chest", {"facing": facing, "type": "single", "waterlogged": "false"},
          {"LootTable": loot} if loot else None)


def candle(color=None, count=1, lit=True):
    return (f"minecraft:{color}_candle" if color else "minecraft:candle",
            {"candles": str(count), "lit": "true" if lit else "false", "waterlogged": "false"})


def campfire(lit=True, facing="north"):
    return ("minecraft:campfire", {"facing": facing, "lit": "true" if lit else "false", "signal_fire": "false",
                                   "waterlogged": "false"})


def skull(rotation=0, wall_facing=None):
    if wall_facing:
        return ("minecraft:skeleton_wall_skull", {"facing": wall_facing, "powered": "false"})
    return ("minecraft:skeleton_skull", {"rotation": str(rotation % 16), "powered": "false"})


def wood_parts(wood):
    """Stairs/slab/fence/trapdoor/door names for a wood type (oak, spruce, dark_oak, cherry...)."""
    stem = wood in ("crimson", "warped")
    bamboo = wood == "bamboo"
    return dict(planks=f"minecraft:{wood}_planks", stairs=f"minecraft:{wood}_stairs", slab=f"minecraft:{wood}_slab",
                fence=f"minecraft:{wood}_fence", trapdoor=f"minecraft:{wood}_trapdoor", door=wood,
                log=f"minecraft:{wood}_{'stem' if stem else 'block' if bamboo else 'log'}",
                stripped=f"minecraft:stripped_{wood}_{'stem' if stem else 'block' if bamboo else 'log'}",
                pressure_plate=f"minecraft:{wood}_pressure_plate", button=f"minecraft:{wood}_button")


def _stone_set(name):
    """Stairs/slab/wall for a stone block, where Minecraft has them."""
    base = name.split(":")[-1]
    table = {
        "stone_bricks": ("stone_brick", True), "mossy_stone_bricks": ("mossy_stone_brick", True),
        "cobblestone": ("cobblestone", True), "mossy_cobblestone": ("mossy_cobblestone", True),
        "bricks": ("brick", True), "sandstone": ("sandstone", True), "smooth_sandstone": ("smooth_sandstone", False),
        "cut_sandstone": ("cut_sandstone", False), "red_sandstone": ("red_sandstone", True),
        "smooth_red_sandstone": ("smooth_red_sandstone", False), "andesite": ("andesite", True),
        "polished_andesite": ("polished_andesite", False), "diorite": ("diorite", True),
        "polished_diorite": ("polished_diorite", False), "granite": ("granite", True),
        "polished_granite": ("polished_granite", False), "deepslate_tiles": ("deepslate_tile", True),
        "deepslate_bricks": ("deepslate_brick", True), "polished_deepslate": ("polished_deepslate", True),
        "cobbled_deepslate": ("cobbled_deepslate", True), "polished_blackstone_bricks": ("polished_blackstone_brick", True),
        "polished_blackstone": ("polished_blackstone", True), "blackstone": ("blackstone", True),
        "mud_bricks": ("mud_brick", True), "quartz_block": ("quartz", False), "smooth_quartz": ("smooth_quartz", False),
        "prismarine": ("prismarine", True), "prismarine_bricks": ("prismarine_brick", False),
        "dark_prismarine": ("dark_prismarine", False), "tuff_bricks": ("tuff_brick", True),
        "polished_tuff": ("polished_tuff", True), "tuff": ("tuff", True), "stone": ("stone", False),
        "smooth_stone": ("smooth_stone", False), "end_stone_bricks": ("end_stone_brick", True),
        "red_nether_bricks": ("red_nether_brick", True), "nether_bricks": ("nether_brick", True),
    }
    stem, has_wall = table.get(base, (None, False))
    if not stem:
        return None
    out = {"stairs": f"minecraft:{stem}_stairs", "slab": f"minecraft:{stem}_slab"}
    if base == "smooth_stone":
        out["stairs"] = "minecraft:stone_stairs"
    if has_wall:
        out["wall"] = f"minecraft:{stem}_wall"
    return out


def stairs_of(block):
    s = _stone_set(block)
    if s:
        return s["stairs"]
    if block.endswith("_planks"):
        return block.replace("_planks", "_stairs")
    if "cut_copper" in block:
        return block + "_stairs" if not block.endswith("_stairs") else block
    return None


def slab_of(block):
    s = _stone_set(block)
    if s:
        return s["slab"]
    if block.endswith("_planks"):
        return block.replace("_planks", "_slab")
    if "cut_copper" in block:
        return block + "_slab"
    return None


def wall_of(block):
    s = _stone_set(block)
    return s.get("wall") if s else None


def pick(t, choice):
    """A block from a palette: a name, or a list (first entry most likely)."""
    if isinstance(choice, (list, tuple)) and choice and isinstance(choice[0], str) and not isinstance(choice, tuple):
        if len(choice) == 1 or t.rnd.random() < 0.6:
            return choice[0]
        return t.rnd.choice(choice[1:])
    return choice


def put(t, x, y, z, block):
    """set() that also accepts (name, props) tuples and palettes."""
    b = pick(t, block)
    if isinstance(b, tuple):
        t.set(x, y, z, b[0], b[1])
    else:
        t.set(x, y, z, b)


def is_air(t, x, y, z):
    n = t.get(x, y, z)
    return n is None or n == AIR


# ---------------------------------------------------------------------------------------
# Keeping roads and doorways clear
# ---------------------------------------------------------------------------------------

def reserve(t, x0, z0, x1, z1, y=0):
    """Marks floor cells (walking surface at y, people at y+1..y+2) that must stay clear."""
    if not hasattr(t, "reserved"):
        t.reserved = {}
    for x in range(min(x0, x1), max(x0, x1) + 1):
        for z in range(min(z0, z1), max(z0, z1) + 1):
            t.reserved[(x, z)] = y


def unreserve(t, x0, z0, x1, z1):
    for x in range(min(x0, x1), max(x0, x1) + 1):
        for z in range(min(z0, z1), max(z0, z1) + 1):
            getattr(t, "reserved", {}).pop((x, z), None)


def is_reserved(t, x, z):
    return (x, z) in getattr(t, "reserved", {})


def free(t, x, z, y=1, height=2, margin=0):
    """Whether a decoration may stand at (x, y, z): nothing there, not on a road or doorway."""
    for dx in range(-margin, margin + 1):
        for dz in range(-margin, margin + 1):
            if is_reserved(t, x + dx, z + dz):
                return False
    if not (0 <= x < t.size[0] and 0 <= z < t.size[2]):
        return False
    return all(is_air(t, x, y + i, z) for i in range(height))


# ---------------------------------------------------------------------------------------
# Audit: water, supports, clear roads
# ---------------------------------------------------------------------------------------

PASSABLE_WORDS = ("carpet", "pressure_plate", "short_grass", "tall_grass", "fern", "flower", "tulip", "poppy",
                  "dandelion", "orchid", "allium", "bluet", "daisy", "cornflower", "lily_of_the_valley", "petals",
                  "torch", "wall_sign", "wall_banner", "wall_hanging_sign", "rail", "button", "sapling", "dead_bush",
                  "lily_pad", "spider_lily", "wheat", "carrots", "potatoes", "beetroots", "structure_block", "vine",
                  "redstone_wire", "glow_lichen", "moss_carpet", "snow")
WATER_REPLACEABLE = ("short_grass", "tall_grass", "fern", "dandelion", "poppy", "orchid", "allium", "bluet", "tulip",
                     "daisy", "cornflower", "lily_of_the_valley", "petals", "torch", "rail", "sapling", "dead_bush",
                     "wheat", "carrots", "potatoes", "beetroots", "spider_lily", "vine", "snow", "redstone_wire")
SOIL = ("grass_block", "dirt", "coarse_dirt", "podzol", "rooted_dirt", "moss_block", "mud", "farmland", "mycelium",
        "muddy_mangrove_roots", "dirt_path")
GRAVITY = ("sand", "gravel", "concrete_powder", "anvil", "dragon_egg", "scaffolding", "pointed_dripstone")


def _short(name):
    return name.split(":")[-1] if name else ""


def passable(name):
    n = _short(name)
    return not n or n == "air" or any(w in n for w in PASSABLE_WORDS)


def _full_cube(name, props):
    n = _short(name)
    if not n or n == "air":
        return False
    if n.endswith("_slab"):
        return (props or {}).get("type") == "double"
    partial = ("_stairs", "_fence", "_wall", "pane", "iron_bars", "door", "lantern", "torch", "sign", "banner", "carpet",
               "pressure_plate", "button", "flower_pot", "potted_", "chain", "_bed", "skull", "head", "candle", "rail",
               "lectern", "anvil", "grindstone", "stonecutter", "bell", "campfire", "chest", "cake", "brewing_stand",
               "end_rod", "lightning_rod", "dripstone", "ladder", "lily_pad", "water", "lava", "farmland", "dirt_path",
               "scaffolding", "decorated_pot", "hopper", "cauldron", "composter", "leaves", "glass", "bamboo",
               "structure_block", "_petals", "short_grass", "fern")
    if n in ("sea_lantern", "jack_o_lantern", "glass", "tinted_glass") or n.endswith("_stained_glass"):
        return True
    if any(p in n for p in partial) or n in PLANT_NAMES:
        return False
    return True


PLANT_NAMES = ("dandelion", "poppy", "blue_orchid", "allium", "azure_bluet", "red_tulip", "orange_tulip", "white_tulip",
               "pink_tulip", "oxeye_daisy", "cornflower", "lily_of_the_valley", "short_grass", "fern", "dead_bush",
               "pink_petals", "wheat", "carrots", "potatoes", "beetroots", "sweet_berry_bush", "torchflower",
               "red_spider_lily", "azalea", "flowering_azalea", "wither_rose")


def _sturdy_up(name, props):
    n, p = _short(name), props or {}
    if n.endswith("_slab"):
        return p.get("type") in ("top", "double")
    if n.endswith("_stairs"):
        return p.get("half") == "top"
    if n.endswith("_trapdoor"):
        return p.get("open") == "false" and p.get("half") == "top"
    return _full_cube(name, props) or "glass" in n and "pane" not in n or n.endswith("_leaves")


def _sturdy_down(name, props):
    n, p = _short(name), props or {}
    if n.endswith("_slab"):
        return p.get("type") in ("bottom", "double")
    if n.endswith("_stairs"):
        return p.get("half") == "bottom"
    if n.endswith("_trapdoor"):
        return p.get("open") == "false" and p.get("half") == "bottom"
    return _full_cube(name, props) or "glass" in n and "pane" not in n or n.endswith("_leaves")


def _center_up(name, props):
    n = _short(name)
    return _sturdy_up(name, props) or n.endswith("_fence") or n.endswith("_wall") and "sign" not in n \
        and "banner" not in n and "torch" not in n or n in ("chain", "iron_bars", "end_rod", "lightning_rod") \
        or n.endswith("glass_pane") or n == "glass_pane"


def _center_down(name, props):
    n = _short(name)
    return _sturdy_down(name, props) or n.endswith("_fence") or n.endswith("_wall") and "sign" not in n \
        and "banner" not in n and "torch" not in n or n in ("chain", "iron_bars", "end_rod", "lightning_rod") \
        or n.endswith("glass_pane") or n == "glass_pane"


def _solidish(name):
    """Minecraft's legacy isSolid(): what wall signs and banners can hang on."""
    n = _short(name)
    return bool(n) and n != "air" and not passable(name) and n not in ("water", "lava") and "lantern" not in n \
        and not n.startswith("potted_")


def water_spills(t, ground_y=0):
    """Simulates Minecraft water flow from every source in the template (conservatively: flowing
    water tries every side). Returns the cells water would reach that are not either template
    water or a free-falling stream that ends in template water."""
    sx, sy, sz = t.size
    blocks = t.blocks

    def state(p):
        v = blocks.get(p)
        return (v[0], v[1]) if v else (None, None)

    def is_source(p):
        n, pr = state(p)
        return n == "minecraft:water" and (pr or {}).get("level", "0") == "0" or (pr or {}).get("waterlogged") == "true"

    def can_hold(p):
        x, y, z = p
        if not (0 <= x < sx and 0 <= z < sz and 0 <= y < sy):
            return y >= ground_y + 1  # outside the template: air above the ground
        n, _ = state(p)
        if n is None:
            return y > ground_y
        sn = _short(n)
        return sn == "air" or any(w in sn for w in WATER_REPLACEABLE)

    sources = {p for p in blocks if is_source(p)}
    level = {p: 8 for p in sources}  # 8 = source / falling, 1..7 flowing
    falling = set()
    queue = list(sources)
    while queue:
        p = queue.pop()
        a = level[p]
        x, y, z = p
        below = (x, y - 1, z)
        src = p in sources
        if below not in sources and can_hold(below):
            if level.get(below, 0) < 8 or below not in falling:
                level[below] = 8
                falling.add(below)
                queue.append(below)
            n_src = sum(1 for d in FACING.values() if (x + d[0], y, z + d[1]) in sources)
            if not (src and n_src >= 3):
                continue
        elif not src and (below in level or below in sources):
            continue  # a stream landing in water stays put
        na = (8 if src or p in falling else a) - 1
        if na <= 0:
            continue
        for d in FACING.values():
            q = (x + d[0], y, z + d[1])
            if q in sources or not can_hold(q):
                continue
            if level.get(q, 0) < na:
                level[q] = na
                queue.append(q)
    spills = []
    for p in level:
        if p in sources:
            continue
        # Fine: a falling stream, or the lip of a spout above one, that ends in template water.
        q = p if p in falling else (p[0], p[1] - 1, p[2])
        while q in falling and q not in sources:
            q = (q[0], q[1] - 1, q[2])
        if q not in sources:
            spills.append(p)
    # Waterloggable blocks beside two sources can become sources themselves.
    for p, (n, pr, _) in blocks.items():
        if p in sources or "waterlogged" not in (pr or {}):
            continue
        x, y, z = p
        n_src = sum(1 for d in FACING.values() if (x + d[0], y, z + d[1]) in sources)
        if n_src >= 2:
            spills.append(p)
    return sorted(spills)


def _floor(state):
    """Something solid enough to stand on (a dirt path or a slab will do)."""
    n = _short(state[0])
    return bool(n) and n != "air" and not passable(state[0]) and n not in ("water", "lava") and "carpet" not in n


def _support_problem(name, props, nm, x, y, z):
    """Why a block would pop off (or fall) where it stands, or None. nm(x, y, z) -> (name, props)."""
    n, p = _short(name), props or {}
    below = nm(x, y - 1, z)
    above = nm(x, y + 1, z)
    if n.endswith("_door"):
        if p.get("half") == "lower":
            if not _sturdy_up(*below):
                return "door without a sturdy floor"
            if _short(above[0]) != n or above[1].get("half") != "upper":
                return "door missing its upper half"
        elif _short(below[0]) != n:
            return "door missing its lower half"
    elif n in PLANT_NAMES and n not in ("wheat", "carrots", "potatoes", "beetroots"):
        if not any(s == _short(below[0]) for s in SOIL):
            if not (n == "dead_bush" and ("sand" in below[0] or "terracotta" in below[0])):
                return f"{n} not on soil"
    elif n in ("wheat", "carrots", "potatoes", "beetroots"):
        if _short(below[0]) != "farmland":
            return f"{n} not on farmland"
    elif n == "farmland":
        if _full_cube(*above):
            return "farmland under a solid block"
    elif "carpet" in n or n.endswith("pressure_plate"):
        if below[0] == AIR:
            return f"{n} over air"
        if n.endswith("pressure_plate") and not _center_up(*below):
            return f"{n} without support"
    elif "lantern" in n:
        if p.get("hanging") == "true":
            if not _center_down(*above):
                return "hanging lantern without support above"
        elif not _center_up(*below):
            return "standing lantern without support below"
    elif n in ("torch", "soul_torch") or "candle" in n and "cake" not in n:
        if not _center_up(*below):
            return f"{n} without support"
    elif n.endswith("wall_torch") or n == "ladder":
        fx, fz = FACING[p["facing"]]
        if not _full_cube(*nm(x - fx, y, z - fz)):
            return f"{n} not on a full block"
    elif n.endswith("_wall_sign") or n.endswith("_wall_banner"):
        fx, fz = FACING[p["facing"]]
        if not _solidish(nm(x - fx, y, z - fz)[0]):
            return f"{n} hanging on nothing"
    elif n.endswith("_wall_hanging_sign"):
        fx, fz = FACING[CW[p["facing"]]]
        if not (_full_cube(*nm(x + fx, y, z + fz)) or _full_cube(*nm(x - fx, y, z - fz))):
            return "wall hanging sign without a wall"
    elif n.endswith("_hanging_sign"):
        if not _center_down(*above):
            return "hanging sign without support"
    elif n.endswith("_banner") or n.endswith("_sign"):
        if not _solidish(below[0]):
            return f"{n} standing on nothing"
    elif n == "lily_pad":
        if below[0] != "minecraft:water":
            return "lily pad not on water"
    elif n == "bell":
        att = p.get("attachment")
        if att == "ceiling" and not _center_down(*above) or att == "floor" and not _sturdy_up(*below):
            return "bell without support"
    elif any(g in n for g in GRAVITY) and "sandstone" not in n and n != "suspicious_sand" or n in ("sand", "red_sand"):
        if below[0] == AIR or passable(below[0]):
            return f"{n} would fall"
    elif n.endswith("_bed"):
        fx, fz = FACING[p["facing"]]
        s = 1 if p.get("part") == "foot" else -1
        if _short(nm(x + fx * s, y, z + fz * s)[0]) != n:
            return "bed missing its other half"
    elif n.endswith("_button") and p.get("face") == "wall":
        fx, fz = FACING[p["facing"]]
        if not _full_cube(*nm(x - fx, y, z - fz)):
            return "button on nothing"
    return None


def _decoration(name):
    n = _short(name)
    return n in PLANT_NAMES and n not in ("wheat", "carrots", "potatoes", "beetroots") or "lantern" in n \
        or "carpet" in n or n.endswith("pressure_plate") or n.endswith("_banner") or n.endswith("_sign") \
        or n.endswith("wall_torch") or n in ("torch", "lily_pad") or "candle" in n or n.endswith("_bed")


def prune(t, ground_y=0):
    """Removes small decorations that could not survive where they ended up (a flower whose box
    a later wall replaced, a lantern whose post was cut away...). Returns how many went."""
    gone = 0

    def nm(x, y, z):
        v = t.blocks.get((x, y, z))
        if v is None:
            return (AIR if y > ground_y else "minecraft:stone"), {}
        return v[0], v[1]
    for _ in range(4):
        doomed = [p for p, (name, props, _) in t.blocks.items()
                  if _decoration(name) and _support_problem(name, props, nm, *p)]
        if not doomed:
            break
        for p in doomed:
            del t.blocks[p]
        gone += len(doomed)
    return gone


NOT_A_FLOOR = ("_fence", "_wall", "pane", "iron_bars", "_door", "lantern", "torch", "_sign", "banner", "chain",
               "rod", "dripstone", "skull", "candle", "potted_", "flower_pot")


def reachable(t, starts, ground_y=0, max_drop=3):
    """Where someone walking in from `starts` (a list of (x, z) columns) can stand: steps up
    of one block (jumping or stairs), drops of up to `max_drop`, doors opened, ladders climbed.
    Returns a set of (x, y, z) feet positions."""
    blocks = t.blocks
    sx, sy, sz = t.size

    def name(x, y, z):
        v = blocks.get((x, y, z))
        return (v[0], v[1]) if v else (None, {})

    def clear(x, y, z):
        n, pr = name(x, y, z)
        if n is None:
            return y > ground_y
        return passable(n) or n.endswith("_door") or n == "minecraft:ladder" or n.endswith("_fence_gate") \
            or (n.endswith("_trapdoor") and (pr or {}).get("open") == "true")

    def floor(x, y, z):
        n, _ = name(x, y, z)
        if n is None:
            return y <= ground_y
        sn = _short(n)
        if sn in ("sea_lantern", "jack_o_lantern"):
            return True
        return not passable(n) and sn not in ("water", "lava") and not any(w in sn for w in NOT_A_FLOOR)

    def ladder(x, y, z):
        return name(x, y, z)[0] == "minecraft:ladder"

    def stand(x, y, z):
        return 0 <= x < sx and 0 <= z < sz and 0 < y < sy and clear(x, y, z) and clear(x, y + 1, z) \
            and (floor(x, y - 1, z) or ladder(x, y, z))

    seen = set()
    queue = []
    for (x, z) in starts:
        for y in range(ground_y + 1, sy - 1):
            if stand(x, y, z):
                seen.add((x, y, z))
                queue.append((x, y, z))
                break
    while queue:
        x, y, z = queue.pop()
        nexts = []
        for (dx, dz) in FACING.values():
            nx, nz = x + dx, z + dz
            for ny in [y + 1] + [y - d for d in range(0, max_drop + 1)]:
                if ny == y + 1 and not clear(x, y + 2, z):
                    continue
                if stand(nx, ny, nz):
                    nexts.append((nx, ny, nz))
                    break
                if ny <= y and not clear(nx, ny, nz):
                    break
        if ladder(x, y, z) or ladder(x, y + 1, z):
            for ny in (y + 1, y - 1):
                if stand(x, ny, z) or (ladder(x, ny, z) and clear(x, ny + 1, z)):
                    nexts.append((x, ny, z))
        for q in nexts:
            if q not in seen:
                seen.add(q)
                queue.append(q)
    return seen


def check_access(t, entrances, ground_y=0):
    """Every entrance (a list of edge columns) must be at least five blocks wide and lead to the
    spawn and arena markers and to every NPC."""
    out = []
    goals = {}
    for (x, y, z), (n, _, data) in t.blocks.items():
        if n == "minecraft:structure_block" and data and data.get("metadata") in ("spawn", "arena"):
            goals[data["metadata"]] = (x, y, z)
    for e in t.entities:
        x, y, z = (int(v) for v in e["blockPos"])
        goals[e["nbt"]["id"]] = (x, y, z)
    for label, cols in entrances.items():
        reach = reachable(t, cols, ground_y)
        width = sum(1 for (x, z) in cols if any((x, y, z) in reach for y in range(ground_y + 1, ground_y + 4)))
        if width < 5:
            out.append(f"entrance {label} is only {width} blocks wide")
        for g, pos in goals.items():
            if pos not in reach:
                out.append(f"{g} at {pos} cannot be reached from the {label} entrance")
    return out


def audit(t, ground_y=None, entrances=None):
    """Problems a template would have in game. Returns a list of strings (empty is good)."""
    gy = ground_y if ground_y is not None else getattr(t, "ground_y", 0)
    out = []
    spills = water_spills(t, gy)
    if spills:
        out.append(f"water could spill at {len(spills)} cells, e.g. {spills[:6]}")
    blocks = t.blocks

    def nm(x, y, z):
        v = blocks.get((x, y, z))
        if v is None:
            return (AIR if y > gy else "minecraft:stone"), {}
        return v[0], v[1]

    issues = {}
    entities_be = 0
    for (x, y, z), (name, props, data) in blocks.items():
        n, p = _short(name), props or {}
        if data is not None or n in ("chest", "barrel", "lectern", "bell", "campfire", "decorated_pot", "brewing_stand",
                                     "blast_furnace", "furnace", "smoker", "skeleton_skull", "skeleton_wall_skull") \
                or n.endswith("banner") or n.endswith("_sign") or n.endswith("_bed"):
            entities_be += 1
        msg = _support_problem(name, props, nm, x, y, z)
        if msg:
            issues.setdefault(msg, []).append((x, y, z))
        if n.endswith("_door") and p.get("half") == "lower":
            fx, fz = FACING[p["facing"]]
            for s in (1, -1):
                for dy in (0, 1):
                    q = nm(x + fx * s, y + dy, z + fz * s)
                    if not passable(q[0]) and not (q[0].endswith("_trapdoor") and q[1].get("open") == "true"):
                        issues.setdefault("doorway blocked", []).append((x + fx * s, y + dy, z + fz * s))
        if n == "campfire" and p.get("lit") == "true":
            for d in ((1, 0, 0), (-1, 0, 0), (0, 0, 1), (0, 0, -1), (0, 1, 0), (0, -1, 0)):
                nb = _short(nm(x + d[0], y + d[1], z + d[2])[0])
                if any(w in nb for w in ("planks", "log", "wood", "wool", "leaves", "hay", "fence", "stairs", "slab",
                                         "carpet", "bookshelf", "banner", "_stem", "bamboo")) \
                        and not any(s in nb for s in ("brick", "stone", "andesite", "deepslate", "sandstone", "tuff")):
                    issues.setdefault("lit campfire next to something flammable", []).append((x, y, z))
    for msg, cells in issues.items():
        out.append(f"{msg}: {len(cells)}, e.g. {cells[:5]}")
    # Roads and doorways stay walkable.
    blocked = []
    for (x, z), y in getattr(t, "reserved", {}).items():
        for dy in (1, 2):
            v = blocks.get((x, y + dy, z))
            if v and not passable(v[0]) and not (v[0].endswith("_trapdoor") and (v[1] or {}).get("open") == "true"):
                blocked.append((x, y + dy, z, _short(v[0])))
        v = blocks.get((x, y, z))
        if (v is None and y > gy) or v is not None and (passable(v[0]) and y > gy or _short(v[0]) in ("water", "lava")):
            blocked.append((x, y, z, "no floor"))
    if blocked:
        n_show = 40 if __import__("os").environ.get("TOWN_AUDIT_VERBOSE") else 6
        out.append(f"reserved road/door cells blocked: {len(blocked)}, e.g. {blocked[:n_show]}")
    # Markers and NPCs stand somewhere sensible.
    for (x, y, z), (name, props, data) in blocks.items():
        if name == "minecraft:structure_block" and data and data.get("metadata") in ("spawn", "arena"):
            if not passable(nm(x, y + 1, z)[0]):
                out.append(f"marker {data['metadata']} at {(x, y, z)} has no headroom")
            if not _floor(nm(x, y - 1, z)):
                out.append(f"marker {data['metadata']} at {(x, y, z)} has no floor")
    for e in t.entities:
        x, y, z = (int(v) for v in e["blockPos"])
        if not (passable(nm(x, y, z)[0]) and passable(nm(x, y + 1, z)[0])) or not _floor(nm(x, y - 1, z)):
            out.append(f"entity {e['nbt']['id']} at {(x, y, z)} is stuck or floating")
    if entities_be > 2500:
        out.append(f"{entities_be} block entities is a lot")
    t.block_entities = entities_be
    if entrances:
        out += check_access(t, entrances, gy)
    return out


# ---------------------------------------------------------------------------------------
# Small things: lights, signs, banners, markers
# ---------------------------------------------------------------------------------------

def lantern(t, x, y, z, soul=False, hanging=False):
    t.set(x, y, z, "minecraft:soul_lantern" if soul else "minecraft:lantern",
          {"hanging": "true" if hanging else "false", "waterlogged": "false"})


def lamp_post(t, x, y, z, height=3, soul=False, post="minecraft:dark_oak_fence", base=None):
    """A post with a lantern on top; `base` is an optional plinth block (a stone wall looks good)."""
    for i in range(height):
        t.set(x, y + i, z, base if (base and i == 0) else post, {"waterlogged": "false"})
    lantern(t, x, y + height, z, soul)


def street_lamp(t, x, y, z, arm="east", height=4, post="minecraft:dark_oak_fence", base="minecraft:stone_brick_wall",
                double=False, soul=False, cap=None):
    """A tall lamp: plinth, post, an arm (or two) with hanging lanterns and a cap."""
    for i in range(height + 1):
        t.set(x, y + i, z, base if i == 0 else post, {"waterlogged": "false"})
    arms = [arm, OPPOSITE[arm]] if double else [arm]
    for a in arms:
        dx, dz = FACING[a]
        t.set(x + dx, y + height, z + dz, post, {"waterlogged": "false"})
        lantern(t, x + dx, y + height - 1, z + dz, soul, hanging=True)
    t.set(x, y + height + 1, z, *slab(cap or ("minecraft:dark_oak_slab" if "oak" in post else "minecraft:stone_brick_slab")))


def door(t, x, y, z, facing, wood="oak", hinge="left"):
    for half, dy in (("lower", 0), ("upper", 1)):
        t.set(x, y + dy, z, f"minecraft:{wood}_door", {"facing": facing, "half": half, "hinge": hinge, "open": "false",
                                                        "powered": "false"})


def bed(t, x, y, z, facing, color="red"):
    """Foot at (x, y, z), head one step towards `facing`."""
    dx, dz = FACING[facing]
    t.set(x, y, z, f"minecraft:{color}_bed", {"facing": facing, "part": "foot", "occupied": "false"})
    t.set(x + dx, y, z + dz, f"minecraft:{color}_bed", {"facing": facing, "part": "head", "occupied": "false"})


def _banner_nbt(patterns):
    if not patterns:
        return None
    return {"id": "minecraft:banner", "patterns": [{"pattern": f"minecraft:{p}", "color": c} for p, c in patterns]}


def banner(t, x, y, z, facing, color="red", count=1, trim=None, patterns=None):
    """Wall banners stacked down a wall, every other block, like long hanging city banners.
    (x, y, z) is the air cell in front of the wall; `facing` points away from the wall."""
    for i in range(count):
        c = trim if trim and i == count - 1 else color
        t.set(x, y - 2 * i, z, f"minecraft:{c}_wall_banner", {"facing": facing}, _banner_nbt(patterns))


def standing_banner(t, x, y, z, rotation, color="red", patterns=None):
    t.set(x, y, z, f"minecraft:{color}_banner", {"rotation": str(rotation % 16)}, _banner_nbt(patterns))


def _sign_text(lines, color="black", glow=False):
    msgs = [f'{{"text":"{line}"}}' for line in (list(lines) + ["", "", "", ""])[:4]]
    return {"messages": msgs, "color": color, "has_glowing_text": 1 if glow else 0}


def sign(t, x, y, z, facing, lines, wood="dark_oak", color="black", glow=False):
    t.set(x, y, z, f"minecraft:{wood}_wall_sign", {"facing": facing, "waterlogged": "false"},
          {"id": "minecraft:sign", "front_text": _sign_text(lines, color, glow),
           "back_text": _sign_text([], "black"), "is_waxed": 1})


def hanging_sign(t, x, y, z, facing, lines, wood="spruce", color="black", glow=False):
    """A shop sign hanging from an iron bracket that sticks out of a wall. `facing` is the
    direction the text faces (it hangs from the wall beside it, at right angles)."""
    text = _sign_text(lines, color, glow)
    t.set(x, y, z, f"minecraft:{wood}_wall_hanging_sign", {"facing": facing, "waterlogged": "false"},
          {"id": "minecraft:hanging_sign", "front_text": text, "back_text": text, "is_waxed": 1})


def ceiling_sign(t, x, y, z, rotation, lines, wood="spruce", color="black", glow=False):
    """A sign hanging on chains from the block above it (rotation 0 faces south)."""
    text = _sign_text(lines, color, glow)
    t.set(x, y, z, f"minecraft:{wood}_hanging_sign", {"rotation": str(rotation % 16), "attached": "false",
                                                       "waterlogged": "false"},
          {"id": "minecraft:hanging_sign", "front_text": text, "back_text": text, "is_waxed": 1})


def marker(t, x, y, z, name):
    """A data marker read by Landmarks when the template is placed (then removed)."""
    t.set(x, y, z, "minecraft:structure_block", {"mode": "data"},
          {"id": "minecraft:structure_block", "mode": "DATA", "metadata": name, "name": "", "author": "slfrontier",
           "posX": 0, "posY": 1, "posZ": 0, "sizeX": 0, "sizeY": 0, "sizeZ": 0, "rotation": "NONE", "mirror": "NONE",
           "ignoreEntities": 1, "powered": 0, "showair": 0, "showboundingbox": 0, "integrity": 1.0, "seed": 0})


def waypoint(t, x, y, z, base="minecraft:polished_andesite", trim=None):
    """A waypoint crystal on a little plinth, optionally with a trim ring at floor level."""
    t.set(x, y, z, base)
    t.set(x, y + 1, z, "slfrontier:waypoint_crystal")
    if trim:
        for d in list(FACING.values()) + [(1, 1), (1, -1), (-1, 1), (-1, -1)]:
            t.set(x + d[0], y, z + d[1], trim)


# ---------------------------------------------------------------------------------------
# Streets
# ---------------------------------------------------------------------------------------

def road(t, x0, z0, x1, z1, block="minecraft:stone_bricks", edge="minecraft:polished_andesite", mix=None, y=0,
         keep_clear=True, chance=0.25):
    """Paves a rectangle; the long edges become curb stones. The road is reserved (kept clear)."""
    xa, xb, za, zb = min(x0, x1), max(x0, x1), min(z0, z1), max(z0, z1)
    for x in range(xa, xb + 1):
        for z in range(za, zb + 1):
            on_edge = (x in (xa, xb) and xa != xb and (xb - xa) < (zb - za)) or \
                      (z in (za, zb) and za != zb and (zb - za) < (xb - xa))
            b = edge if (on_edge and edge) else block
            if mix and not (on_edge and edge) and t.rnd.random() < chance:
                b = t.rnd.choice(mix)
            put(t, x, y, z, b)
    if keep_clear:
        reserve(t, xa, za, xb, zb, y)


def pave_disc(t, cx, cz, r, palette, y=0, rings=None, keep_clear=False):
    """A round plaza: random paving from `palette`, with optional (radius, block) rings."""
    for x in range(int(cx - r - 1), int(cx + r + 2)):
        for z in range(int(cz - r - 1), int(cz + r + 2)):
            d = math.hypot(x - cx, z - cz)
            if d <= r:
                put(t, x, y, z, palette)
                if keep_clear:
                    reserve(t, x, z, x, z, y)
    for (rr, block) in rings or ():
        t.ring(cx, y, cz, rr, block, width=0.5)


def bench(t, x, z, facing, length=2, wood="spruce", y=1, arms=True):
    """A bench people sit on looking towards `facing`, with trapdoor armrests."""
    back = OPPOSITE[facing]
    dx, dz = (1, 0) if facing in ("north", "south") else (0, 1)
    for i in range(length):
        t.set(x + dx * i, y, z + dz * i, *stairs(f"minecraft:{wood}_stairs", back))
    if arms:
        for (ex, ez, f) in ((x - dx, z - dz, facing_of(-dx, -dz)), (x + dx * length, z + dz * length, facing_of(dx, dz))):
            if is_air(t, ex, y, ez) and not is_reserved(t, ex, ez):
                t.set(ex, y, ez, *trapdoor(f"minecraft:{wood}_trapdoor", f, "bottom", True))


def flower_bed(t, x0, z0, x1, z1, edge="minecraft:spruce_trapdoor", soil="minecraft:grass_block", kinds=None,
               chance=0.8, y=0):
    """A ground-level bed of flowers with a low trapdoor edging."""
    kinds = kinds or ["minecraft:poppy", "minecraft:dandelion", "minecraft:cornflower", "minecraft:allium",
                      "minecraft:oxeye_daisy", "minecraft:azure_bluet", "minecraft:red_tulip", "minecraft:white_tulip"]
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            t.set(x, y, z, soil, {"snowy": "false"} if "grass_block" in soil or "podzol" in soil else None)
            border = x in (x0, x1) or z in (z0, z1)
            if border and edge:
                f = "north" if z == z0 else "south" if z == z1 else "west" if x == x0 else "east"
                t.set(x, y + 1, z, *trapdoor(edge, f, "bottom", True))
            elif t.rnd.random() < chance:
                t.set(x, y + 1, z, t.rnd.choice(kinds))


def planter(t, x0, z0, x1, z1, rim="minecraft:stone_bricks", soil="minecraft:grass_block", kinds=None, y=1,
            bush_kind=None, chance=0.75):
    """A raised stone planter (rim at y) full of flowers, with an optional bush in the middle."""
    kinds = kinds or ["minecraft:poppy", "minecraft:dandelion", "minecraft:cornflower", "minecraft:allium",
                      "minecraft:oxeye_daisy", "minecraft:azure_bluet", "minecraft:red_tulip", "minecraft:short_grass"]
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if x in (x0, x1) or z in (z0, z1):
                put(t, x, y, z, rim)
            else:
                t.set(x, y, z, soil, {"snowy": "false"} if "grass_block" in soil else None)
                if t.rnd.random() < chance:
                    t.set(x, y + 1, z, t.rnd.choice(kinds))
    if bush_kind and x1 - x0 >= 2 and z1 - z0 >= 2:
        t.set((x0 + x1) // 2, y + 1, (z0 + z1) // 2, *_leaf(bush_kind))


def crate_pile(t, x, z, y=1, n=3):
    """Barrels lying about, some stacked."""
    cells = [(x, z), (x + 1, z), (x, z + 1)][:n]
    for i, (cx, cz) in enumerate(cells):
        t.set(cx, y, cz, *barrel(t.rnd.choice(["up", "north", "east", "south", "west"])))
        if i == 0 and t.rnd.random() < 0.6:
            t.set(cx, y + 1, cz, *barrel(t.rnd.choice(["up", "east"])))


def woodpile(t, x, z, length=3, axis="x", y=1, wood="spruce", height=2):
    dx, dz = (1, 0) if axis == "x" else (0, 1)
    for i in range(length):
        for j in range(height if i not in (0, length - 1) else max(1, height - 1)):
            t.set(x + dx * i, y + j, z + dz * i, f"minecraft:{wood}_log", {"axis": "z" if axis == "x" else "x"})


# ---------------------------------------------------------------------------------------
# Nature
# ---------------------------------------------------------------------------------------

def _leaf(kind):
    return (f"minecraft:{kind}_leaves", {"persistent": "true", "distance": "1", "waterlogged": "false"})


def tree(t, x, y, z, log_name="oak", leaves="oak", height=5, radius=2):
    """A rounded broadleaf tree: trunk, a couple of branches and a lumpy crown."""
    rnd = t.rnd
    leaf = _leaf(leaves)
    lg = f"minecraft:{log_name}_log"
    for i in range(height):
        t.set(x, y + i, z, lg, {"axis": "y"})
    top = y + height
    blobs = [(x, top, z, radius + 0.6)]
    for k in range(rnd.randint(2, 3)):
        a = rnd.uniform(0, math.tau)
        bx, bz = round(x + math.cos(a) * 1.6), round(z + math.sin(a) * 1.6)
        by = top - 1 - rnd.randint(0, 1)
        if t.get(bx, by, bz) is None:
            t.set(bx, by, bz, lg, {"axis": "x" if abs(bx - x) >= abs(bz - z) else "z"})
        blobs.append((bx + math.cos(a) * 0.8, by + 0.5, bz + math.sin(a) * 0.8, radius * 0.75 + 0.4))
    for (bx, by, bz, br) in blobs:
        for xx in range(int(bx - br - 1), int(bx + br + 2)):
            for zz in range(int(bz - br - 1), int(bz + br + 2)):
                for yy in range(max(y + 2, int(by - br * 0.6)), int(by + br * 0.8) + 1):
                    dd = math.sqrt((xx - bx) ** 2 + ((yy - by) * 1.3) ** 2 + (zz - bz) ** 2)
                    if dd <= br and (dd < br - 0.7 or rnd.random() < 0.7) and t.get(xx, yy, zz) is None:
                        t.set(xx, yy, zz, *leaf)


def conifer(t, x, y, z, kind="spruce", height=9):
    """A tall spruce/fir: a narrow trunk with layered, tapering rings of needles."""
    leaf = _leaf(kind)
    for i in range(height):
        t.set(x, y + i, z, f"minecraft:{kind}_log", {"axis": "y"})
    for i in range(2, height + 1):
        frac = (height - i) / height
        r = 0.6 + frac * 2.9 * (0.7 if i % 2 else 1.0)
        for dx in range(-3, 4):
            for dz in range(-3, 4):
                if (dx, dz) != (0, 0) and math.hypot(dx, dz) <= r and t.get(x + dx, y + i, z + dz) is None:
                    t.set(x + dx, y + i, z + dz, *leaf)
    t.set(x, y + height, z, *leaf)
    t.set(x, y + height + 1, z, *leaf)


def cherry_tree(t, x, y, z, height=5, spread=4):
    """A wide cherry tree: a trunk splitting into angled limbs under a pink, drooping canopy."""
    rnd = t.rnd
    leaf = _leaf("cherry")
    for i in range(height - 2):
        t.set(x, y + i, z, "minecraft:cherry_log", {"axis": "y"})
    tips = []
    for k in range(3):
        a = k * math.tau / 3 + rnd.uniform(-0.4, 0.4)
        px, pz, py = float(x), float(z), float(y + height - 3)
        for i in range(spread):
            px += math.cos(a) * 0.9
            pz += math.sin(a) * 0.9
            py += 0.7
            t.set(round(px), round(py), round(pz), "minecraft:cherry_wood", {"axis": "y"})
        tips.append((px, py, pz))
    for (bx, by, bz) in tips + [(x, y + height + 0.5, z)]:
        br = 2.6
        for xx in range(int(bx - br - 1), int(bx + br + 2)):
            for zz in range(int(bz - br - 1), int(bz + br + 2)):
                for yy in range(int(by - 1.5), int(by + 2)):
                    dd = math.sqrt((xx - bx) ** 2 + ((yy - by) * 1.6) ** 2 + (zz - bz) ** 2)
                    if dd <= br and (dd < br - 0.6 or rnd.random() < 0.6) and t.get(xx, yy, zz) is None:
                        t.set(xx, yy, zz, *leaf)
                # drooping fringes
                yb = int(by - 1.5)
                if math.hypot(xx - bx, zz - bz) <= br and rnd.random() < 0.2 and t.get(xx, yb - 1, zz) is None \
                        and t.get(xx, yb, zz) == "minecraft:cherry_leaves":
                    t.set(xx, yb - 1, zz, *leaf)


def bush(t, x, y, z, kind="oak", r=1):
    leaf = _leaf(kind)
    for dx in range(-r, r + 1):
        for dz in range(-r, r + 1):
            if abs(dx) + abs(dz) <= r and t.get(x + dx, y, z + dz) is None and not is_reserved(t, x + dx, z + dz):
                t.set(x + dx, y, z + dz, *leaf)
    t.set(x, y + 1, z, *leaf)


def flowers(t, x0, z0, x1, z1, chance=0.2, kinds=None, ground=("minecraft:grass_block",), y=0):
    kinds = kinds or ["minecraft:short_grass", "minecraft:dandelion", "minecraft:poppy", "minecraft:cornflower",
                      "minecraft:oxeye_daisy", "minecraft:short_grass", "minecraft:short_grass"]
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if t.get(x, y, z) in ground and t.get(x, y + 1, z) is None and not is_reserved(t, x, z) \
                    and t.rnd.random() < chance:
                t.set(x, y + 1, z, t.rnd.choice(kinds))


def giant_tree(t, cx, cz, y0, trunk_r, height, canopy_r, log_name="oak", leaves="oak", roots=True, keep=None):
    """A colossal tree: flared trunk, roots, a few great branches and layered canopy blobs.
    `keep(x, z)` -> True for floor cells the roots must leave alone (paths)."""
    rnd = t.rnd
    wood = (f"minecraft:{log_name}_wood", {"axis": "y"})
    leaf = _leaf(leaves)
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
            length = trunk_r + rnd.randint(3, 6)
            for i in range(int(length)):
                x = round(cx + math.cos(ang) * (trunk_r + i))
                z = round(cz + math.sin(ang) * (trunk_r + i))
                if keep and keep(x, z):
                    break
                yy = y0 + max(0, 2 - i // 2)
                for k2 in range(y0, yy + 1):
                    t.set(x, k2, z, *wood)
    blobs = [(cx, y0 + height, cz, canopy_r)]
    for k2 in range(6):
        ang = math.radians(k2 * 60 + rnd.uniform(-20, 20))
        by = y0 + int(height * rnd.uniform(0.55, 0.85))
        length = canopy_r * rnd.uniform(0.6, 0.95)
        steps = int(length)
        x = z = 0
        for i in range(steps):
            x = round(cx + math.cos(ang) * i)
            z = round(cz + math.sin(ang) * i)
            y = by + i // 3
            t.set(x, y, z, *wood)
            t.set(x, y + 1, z, *wood)
        blobs.append((x, by + steps // 3 + 1, z, canopy_r * rnd.uniform(0.45, 0.6)))
    for (bx, by, bz, br) in blobs:
        for x in range(int(bx - br - 1), int(bx + br + 2)):
            for z in range(int(bz - br - 1), int(bz + br + 2)):
                for y in range(int(by - br * 0.5), int(by + br * 0.6) + 1):
                    d = math.sqrt((x - bx) ** 2 + ((y - by) * 1.7) ** 2 + (z - bz) ** 2)
                    if d <= br and rnd.random() < (0.92 if d < br - 1 else 0.55) and t.get(x, y, z) is None:
                        t.set(x, y, z, *leaf)


# ---------------------------------------------------------------------------------------
# Water features: every source is boxed in so nothing ever flows out
# ---------------------------------------------------------------------------------------

def basin(t, cells, y, rim, floor=None):
    """Water at y over `cells`, a solid floor under it and a full-block rim on every side
    (diagonals too), so the water has nowhere to go. Returns the rim cells."""
    cells = set(cells)
    rimcells = set()
    for (x, z) in cells:
        if floor:
            put(t, x, y - 1, z, floor)
        t.set(x, y, z, "minecraft:water", {"level": "0"})
    for (x, z) in cells:
        for d in list(FACING.values()) + [(1, 1), (1, -1), (-1, 1), (-1, -1)]:
            q = (x + d[0], z + d[1])
            if q not in cells:
                rimcells.add(q)
    for (x, z) in rimcells:
        cur = t.blocks.get((x, y, z))
        if cur is None or not _full_cube(cur[0], cur[1]):
            put(t, x, y, z, rim)
    return rimcells


def fountain(t, cx, cz, r=5, block="minecraft:stone_bricks", rim="minecraft:polished_andesite", coping=None,
             floor="minecraft:prismarine_bricks", trim="minecraft:chiseled_stone_bricks", crown=True, lilies=4,
             posts=True, lamp=True, apron=None):
    """A round fountain that never overflows: a rimmed basin at y=1 with a coping one block above
    the water, a central pillar carrying a bowl whose four spouts pour straight down into the
    basin, and a crown cup on top spilling into the bowl. Every stream falls into standing water."""
    coping = coping or slab_of(rim) or slab_of(block)
    wall = wall_of(block) or "minecraft:stone_brick_wall"
    st = stairs_of(block) or "minecraft:stone_brick_stairs"
    rnd = t.rnd
    for x in range(int(cx - r - 2), int(cx + r + 3)):
        for z in range(int(cz - r - 2), int(cz + r + 3)):
            d = math.hypot(x - cx, z - cz)
            if d <= r + 1.3:
                put(t, x, 0, z, apron or (trim if d > r + 0.4 else block))
    water = [(x, z) for x in range(cx - r, cx + r + 1) for z in range(cz - r, cz + r + 1)
             if math.hypot(x - cx, z - cz) <= r - 0.9 and max(abs(x - cx), abs(z - cz)) > 1]
    rims = basin(t, water, 1, rim, floor)
    for (x, z) in rims:
        if max(abs(x - cx), abs(z - cz)) > 1:
            t.set(x, 2, z, *slab(coping))
    if posts:
        for a in (45, 135, 225, 315):
            px = round(cx + r * 0.98 * math.cos(math.radians(a)))
            pz = round(cz + r * 0.98 * math.sin(math.radians(a)))
            if (px, pz) not in rims:
                continue
            t.set(px, 2, pz, trim)
            if lamp:
                t.set(px, 3, pz, *wall_block(wall))
                lantern(t, px, 4, pz)
    # Central pillar (3x3 to y=3), a bowl at y=4..5 and a slim column with the crown cup at y=7.
    for y in range(1, 4):
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                t.set(cx + dx, y, cz + dz, trim if (dx == 0 and dz == 0) or y == 3 else block)
    spouts = {(cx + 2, cz), (cx - 2, cz), (cx, cz + 2), (cx, cz - 2)}
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            if (abs(dx) == 2 and abs(dz) == 2) or (cx + dx, cz + dz) in spouts:
                continue
            t.set(cx + dx, 4, cz + dz, block)
            if max(abs(dx), abs(dz)) == 2:
                t.set(cx + dx, 5, cz + dz, rim)
                f = facing_of(-(dx // 2) if abs(dx) == 2 else 0, -(dz // 2) if abs(dz) == 2 else 0)
                t.set(cx + dx, 3, cz + dz, *stairs(st, f, "top"))
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            if (dx, dz) != (0, 0):
                t.set(cx + dx, 5, cz + dz, "minecraft:water", {"level": "0"})
    t.set(cx, 5, cz, trim)
    t.set(cx, 6, cz, block)
    if crown:
        t.set(cx, 7, cz, "minecraft:water", {"level": "0"})
        for dx, dz in ((1, 1), (1, -1), (-1, 1), (-1, -1)):
            t.set(cx + dx, 7, cz + dz, *wall_block(wall))
    pads = sorted(c for c in water if c not in spouts and max(abs(c[0] - cx), abs(c[1] - cz)) >= 3)
    for (x, z) in rnd.sample(pads, min(lilies, len(pads))):
        t.set(x, 2, z, "minecraft:lily_pad")


def well(t, cx, cz, block="minecraft:cobblestone", wood="spruce", roof_block=None, y=0, axis="x"):
    """A covered well: a 3x3 water shaft boxed in by a stone kerb, two posts, a little roof and a
    chain with a lantern over the water."""
    roof_block = roof_block or f"minecraft:{wood}_stairs"
    wl = wall_of(block) or "minecraft:cobblestone_wall"
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            t.set(cx + dx, y, cz + dz, block)
    basin(t, [(cx + dx, cz + dz) for dx in (-1, 0, 1) for dz in (-1, 0, 1)], y + 1, block, block)
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            if max(abs(dx), abs(dz)) == 2:
                t.set(cx + dx, y + 2, cz + dz, *wall_block(wl))
    ax, az = (1, 0) if axis == "x" else (0, 1)
    px, pz = (0, 1) if axis == "x" else (1, 0)
    for s in (-2, 2):
        for yy in range(y + 2, y + 5):
            t.set(cx + ax * s, yy, cz + az * s, f"minecraft:{wood}_log", {"axis": "y"})
    for s in range(-2, 3):
        t.set(cx + ax * s, y + 5, cz + az * s, f"minecraft:{wood}_log", {"axis": axis})
    for s in range(-3, 4):
        t.set(cx + ax * s - px, y + 5, cz + az * s - pz, *stairs(roof_block, facing_of(px, pz)))
        t.set(cx + ax * s + px, y + 5, cz + az * s + pz, *stairs(roof_block, facing_of(-px, -pz)))
        t.set(cx + ax * s, y + 6, cz + az * s, *slab(slab_of(f"minecraft:{wood}_planks")))
    t.set(cx, y + 4, cz, *chain())
    t.set(cx, y + 3, cz, *chain())
    lantern(t, cx, y + 2, cz, hanging=True)


# ---------------------------------------------------------------------------------------
# Roofs, built on a height field
# ---------------------------------------------------------------------------------------
#
# Every roof part (a gable, a hip, a cross gable, a dormer) gives a height for each cell it
# covers; the roof is the highest of them. Each cell then gets a stair facing up the slope, a
# ridge slab on top, solid blocks under steep steps, and thick undersides at the eaves and
# rakes. Valleys, cross gables and dormers come out right without special cases.

def gable_part(x0, z0, x1, z1, along, y0, pitch=1, overhang=1):
    X0, Z0, X1, Z1 = x0 - overhang, z0 - overhang, x1 + overhang, z1 + overhang

    def f(x, z):
        if X0 <= x <= X1 and Z0 <= z <= Z1:
            return y0 + pitch * (min(z - Z0, Z1 - z) if along == "x" else min(x - X0, X1 - x))
        return None
    f.bbox, f.along = (X0, Z0, X1, Z1), along
    return f


def hip_part(x0, z0, x1, z1, y0, pitch=1, overhang=1):
    X0, Z0, X1, Z1 = x0 - overhang, z0 - overhang, x1 + overhang, z1 + overhang

    def f(x, z):
        if X0 <= x <= X1 and Z0 <= z <= Z1:
            return y0 + pitch * min(z - Z0, Z1 - z, x - X0, X1 - x)
        return None
    f.bbox, f.along = (X0, Z0, X1, Z1), "x" if X1 - X0 >= Z1 - Z0 else "z"
    return f


def roof(t, parts, y0, s, walls=(), thick=True, gable_windows=True, finial=None, upturn=False, cheeks=()):
    """Lays the roof described by `parts` (see gable_part/hip_part); y0 is the eave height.
    `walls` are the wall rectangles under it: their gable ends are filled with s["gable"]."""
    field = {}
    for f in parts:
        X0, Z0, X1, Z1 = f.bbox
        for x in range(X0, X1 + 1):
            for z in range(Z0, Z1 + 1):
                hgt = f(x, z)
                if hgt is not None and (x, z) not in field or hgt is not None and hgt > field[(x, z)][0]:
                    field[(x, z)] = (hgt, f.along)
    roof_st, full, ridge = s["roof"], s.get("roof_full") or s["roof"].replace("_stairs", ""), s.get("ridge")
    ridge = ridge or slab_of(full) or roof_st.replace("_stairs", "_slab")
    under = s.get("roof_under") or roof_st
    peaks = []
    for (x, z), (hgt, al) in field.items():
        nbs = {d: field.get((x + dx, z + dz)) for d, (dx, dz) in FACING.items()}
        across = ("north", "south") if al == "x" else ("west", "east")
        higher = [d for d, v in nbs.items() if v and v[0] > hgt]
        face = None
        if higher:
            face = max(higher, key=lambda d: (nbs[d][0], d in across))
        else:
            cand = [d for d, v in nbs.items() if v and v[0] == hgt and (not nbs[OPPOSITE[d]] or nbs[OPPOSITE[d]][0] < hgt)]
            if cand:
                face = sorted(cand, key=lambda d: d not in across)[0]
        if face:
            t.set(x, hgt, z, *stairs(roof_st, face))
        else:
            t.set(x, hgt, z, *slab(ridge))
            if not any(v and v[0] == hgt for v in nbs.values()):
                peaks.append((x, hgt, z))
        lower = [v[0] for v in nbs.values() if v and v[0] < hgt]
        lowest = min(lower) if lower else hgt - 1
        if any(v is None for v in nbs.values()):
            lowest = min(lowest, hgt - 1)
        for k in range(1, hgt - lowest):
            if is_air(t, x, hgt - k, z):
                t.set(x, hgt - k, z, full)
        if thick and any(v is None for v in nbs.values()):
            yb = hgt - max(1, hgt - lowest)
            if face and is_air(t, x, yb, z):
                eave = nbs[OPPOSITE[face]] is None
                t.set(x, yb, z, *stairs(under, face if eave else OPPOSITE[face], "top"))
            elif not face and is_air(t, x, hgt - 1, z):
                t.set(x, hgt - 1, z, full)
    # gable ends
    gable = s.get("gable") or s.get("infill")
    for (wx0, wz0, wx1, wz1) in walls:
        runs = {}
        for x in range(wx0, wx1 + 1):
            for z in range(wz0, wz1 + 1):
                if not (x in (wx0, wx1) or z in (wz0, wz1)) or (x, z) not in field:
                    continue
                top = field[(x, z)][0]
                for yy in range(y0, top):
                    if is_air(t, x, yy, z):
                        if yy == y0 and s.get("frame") and s.get("gable_beam", True):
                            ax = "x" if z in (wz0, wz1) and x not in (wx0, wx1) else "z"
                            t.set(x, yy, z, *log(s["frame"], ax))
                        else:
                            put(t, x, yy, z, gable)
                side = "north" if z == wz0 else "south" if z == wz1 else "west" if x == wx0 else "east"
                runs.setdefault(side, []).append((x, z, top))
        if gable_windows:
            for side, cells in runs.items():
                cells.sort()
                for i, (x, z, top) in enumerate(cells):
                    left = cells[i - 1][2] if i > 0 else -1
                    right = cells[i + 1][2] if i + 1 < len(cells) else -1
                    if top - y0 >= 4 and top >= left and top >= right and (top > left or top > right) \
                            and 0 < i < len(cells) - 1:
                        for yy in range(y0 + 1, min(top - 1, y0 + 3)):
                            t.set(x, yy, z, *pane(s.get("pane", "minecraft:glass_pane")))
    for (x, z, lo, hi) in cheeks:
        for yy in range(lo, hi):
            if is_air(t, x, yy, z) or (t.get(x, yy, z) or "").endswith("_stairs"):
                put(t, x, yy, z, gable)
    if finial:
        for (x, hgt, z) in peaks:
            put(t, x, hgt + 1, z, finial)
    if upturn:
        X0 = min(x for x, z in field)
        X1 = max(x for x, z in field)
        Z0 = min(z for x, z in field)
        Z1 = max(z for x, z in field)
        for (cx_, cz_) in ((X0, Z0), (X1, Z0), (X0, Z1), (X1, Z1)):
            if (cx_, cz_) in field:
                hgt = field[(cx_, cz_)][0]
                t.set(cx_, hgt + 1, cz_, *stairs(roof_st, "east" if cx_ == X0 else "west", "bottom"))
    return field


# ---------------------------------------------------------------------------------------
# Building parts
# ---------------------------------------------------------------------------------------

POTS = ["minecraft:potted_red_tulip", "minecraft:potted_poppy", "minecraft:potted_cornflower", "minecraft:potted_allium",
        "minecraft:potted_azure_bluet", "minecraft:potted_dandelion", "minecraft:potted_oxeye_daisy",
        "minecraft:potted_fern", "minecraft:potted_azalea_bush", "minecraft:potted_flowering_azalea_bush"]
BOX_FLOWERS = ["minecraft:red_tulip", "minecraft:poppy", "minecraft:cornflower", "minecraft:allium", "minecraft:azure_bluet",
               "minecraft:oxeye_daisy", "minecraft:pink_tulip", "minecraft:lily_of_the_valley", "minecraft:dandelion"]


def window_dressing(t, cells, wy0, wy1, side, s, upper=True, flowerbox=None):
    """Glass in the wall cells plus what goes outside: a sill (or a flower box on upper
    floors), a hood over the top and open shutters on either side."""
    ox, oz = FACING[side]
    pn = s.get("pane", "minecraft:glass_pane")
    for (x, z) in cells:
        for yy in range(wy0, wy1 + 1):
            t.set(x, yy, z, *pane(pn))
    rnd = t.rnd
    if flowerbox is None:
        flowerbox = upper and s.get("flowerboxes", True) and rnd.random() < 0.55
    for (x, z) in cells:
        X, Z = x + ox, z + oz
        if upper:
            if flowerbox:
                t.set(X, wy0 - 1, Z, s.get("box", "minecraft:moss_block"))
                if is_air(t, X, wy0, Z):
                    t.set(X, wy0, Z, rnd.choice(s.get("box_flowers", BOX_FLOWERS)))
            elif s.get("sill"):
                if is_air(t, X, wy0 - 1, Z):
                    t.set(X, wy0 - 1, Z, *trapdoor(s["sill"], side, "top"))
                if rnd.random() < 0.4 and is_air(t, X, wy0, Z):
                    t.set(X, wy0, Z, rnd.choice(POTS))
        if s.get("hood") and is_air(t, X, wy1 + 1, Z):
            t.set(X, wy1 + 1, Z, *stairs(s["hood"], OPPOSITE[side], "top"))
    if s.get("shutter"):
        xs = sorted(cells)
        (ax, az), (bx, bz) = xs[0], xs[-1]
        px, pz = (1, 0) if side in ("north", "south") else (0, 1)
        for (sx_, sz_) in ((ax - px + ox, az - pz + oz), (bx + px + ox, bz + pz + oz)):
            for yy in range(wy0, wy1 + 1):
                if is_air(t, sx_, yy, sz_):
                    t.set(sx_, yy, sz_, *trapdoor(s["shutter"], side, "bottom", True))


def chimney(t, x, z, y0, top, block="minecraft:bricks", smoke=True):
    """A chimney stack from y0 up to `top`, with a capped crown and a smoking campfire."""
    for yy in range(y0, top + 1):
        t.set(x, yy, z, block)
    if smoke:
        t.set(x, top + 1, z, *campfire(True))
    else:
        t.set(x, top + 1, z, *slab(slab_of(block) or "minecraft:brick_slab"))


def staircase(t, x, z, y0, rise, direction, block, under=None):
    """A straight run of `rise` stairs climbing towards `direction` from (x, y0+1, z); clears
    headroom above it (cutting the floor overhead)."""
    dx, dz = FACING[direction]
    for i in range(rise):
        sx_, sz_ = x + dx * i, z + dz * i
        t.set(sx_, y0 + 1 + i, sz_, *stairs(block, direction))
        for k in range(y0 + 2 + i, y0 + 4 + i):
            if k <= y0 + rise:
                t.set(sx_, k, sz_, AIR)
        if under:
            for k in range(y0 + 1, y0 + 1 + i):
                t.set(sx_, k, sz_, under)


# -- interiors ---------------------------------------------------------------------------

ROOMS = {
    "home0": ["smoker", "crafting_table", "barrel", "chest", "bookshelf", "water_cauldron", "barrel", "flower"],
    "home1": ["bed", "chest", "bookshelf", "flower", "bed", "barrel", "loom", "lectern"],
    "home2": ["bed", "chest", "barrel", "bookshelf", "flower"],
    "inn1": ["bed", "chest", "bed", "flower", "bed", "chest", "bed", "barrel", "bed", "bed"],
    "guild0": ["bookshelf", "lectern", "bookshelf", "chest", "cartography_table", "bookshelf", "chest", "barrel",
               "bookshelf", "flower"],
    "guild1": ["bookshelf", "chest", "bed", "lectern", "bookshelf", "chest", "barrel", "bookshelf"],
    "shop0": ["barrel", "chest", "decorated_pot", "barrel", "fletching_table", "barrel", "flower", "chest"],
    "smithy0": ["anvil", "grindstone", "smithing_table", "blast_furnace", "chest", "barrel", "water_cauldron", "barrel"],
    "store0": ["barrel", "hay", "barrel", "chest", "barrel", "hay", "composter", "barrel"],
    "tavern0": ["barrel", "barrel", "brewing_stand", "barrel", "chest", "flower"],
    "japanese0": ["chest", "barrel", "flower", "bookshelf", "decorated_pot"],
    "japanese1": ["bed", "chest", "flower", "bed"],
}


def place_item(t, x, y, z, item, face, bed_color="red", wall=None):
    """One piece of furniture at (x, y, z); `face` points into the room."""
    rnd = t.rnd
    if item == "smoker":
        t.set(x, y, z, "minecraft:smoker", {"facing": face, "lit": "true"})
    elif item == "furnace":
        t.set(x, y, z, "minecraft:furnace", {"facing": face, "lit": "false"})
    elif item == "blast_furnace":
        t.set(x, y, z, "minecraft:blast_furnace", {"facing": face, "lit": "true"})
    elif item in ("crafting_table", "cartography_table", "fletching_table", "smithing_table", "bookshelf"):
        t.set(x, y, z, f"minecraft:{item}")
        if item == "bookshelf" and rnd.random() < 0.7 and is_air(t, x, y + 1, z):
            t.set(x, y + 1, z, "minecraft:bookshelf")
    elif item == "barrel":
        t.set(x, y, z, *barrel(rnd.choice(["up", face])))
    elif item == "chest":
        t.set(x, y, z, "minecraft:chest", {"facing": face, "type": "single", "waterlogged": "false"})
    elif item == "water_cauldron":
        t.set(x, y, z, "minecraft:water_cauldron", {"level": "3"})
    elif item == "composter":
        t.set(x, y, z, "minecraft:composter", {"level": "5"})
    elif item == "lectern":
        t.set(x, y, z, "minecraft:lectern", {"facing": face, "has_book": "false", "powered": "false"})
    elif item in ("loom", "stonecutter"):
        t.set(x, y, z, f"minecraft:{item}", {"facing": face})
    elif item == "anvil":
        t.set(x, y, z, "minecraft:anvil", {"facing": CW[face]})
    elif item == "grindstone":
        t.set(x, y, z, "minecraft:grindstone", {"face": "floor", "facing": face})
    elif item == "decorated_pot":
        t.set(x, y, z, "minecraft:decorated_pot", {"facing": face, "cracked": "false", "waterlogged": "false"})
    elif item == "brewing_stand":
        t.set(x, y, z, "minecraft:brewing_stand", {"has_bottle_0": "true", "has_bottle_1": "false", "has_bottle_2": "true"})
    elif item == "hay":
        t.set(x, y, z, "minecraft:hay_block", {"axis": "y"})
    elif item == "flower":
        t.set(x, y, z, rnd.choice(POTS))
    elif item == "bed":
        # head against the wall, foot one step into the room
        bx, bz = x + FACING[face][0], z + FACING[face][1]
        t.set(bx, y, bz, f"minecraft:{bed_color}_bed", {"facing": OPPOSITE[face], "part": "foot", "occupied": "false"})
        t.set(x, y, z, f"minecraft:{bed_color}_bed", {"facing": OPPOSITE[face], "part": "head", "occupied": "false"})


def furnish(t, x0, z0, x1, z1, fy, kind, ceiling=None, keep=(), bed_color="red", wood="spruce", carpet="red",
            table=True, lamp=True):
    """Fills a room (interior cells x0..x1, z0..z1, floor block at fy) with furniture along the
    walls and a table with chairs in the middle, keeping `keep` cells (doors, ladders) and the
    cells beside them free."""
    rnd = t.rnd
    y = fy + 1
    keep = set(keep)
    blocked = set()
    for (kx, kz) in keep:
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                blocked.add((kx + dx, kz + dz))
    w, d = x1 - x0 + 1, z1 - z0 + 1
    if w < 2 or d < 2:
        return
    slots = []
    for x in range(x0, x1 + 1):
        slots.append((x, z0, "south"))
        slots.append((x, z1, "north"))
    for z in range(z0 + 1, z1):
        slots.append((x0, z, "east"))
        slots.append((x1, z, "west"))
    rnd.shuffle(slots)
    used = set()
    items = list(ROOMS.get(kind, ROOMS["home0"]))
    for item in items:
        for (x, z, face) in slots:
            if (x, z) in used or (x, z) in blocked or not is_air(t, x, y, z) or not is_air(t, x, y + 1, z):
                continue
            if item == "bed":
                bx, bz = x + FACING[face][0], z + FACING[face][1]
                if not (x0 <= bx <= x1 and z0 <= bz <= z1) or (bx, bz) in used or (bx, bz) in blocked \
                        or not is_air(t, bx, y, bz):
                    continue
                used.add((bx, bz))
            corner = (x in (x0, x1)) and (z in (z0, z1))
            if item != "flower" and corner and rnd.random() < 0.5:
                continue
            place_item(t, x, y, z, item, face, bed_color)
            used.add((x, z))
            break
    # a table with chairs and a rug in the middle
    cx, cz = (x0 + x1) // 2, (z0 + z1) // 2
    if table and w >= 4 and d >= 4 and all(is_air(t, cx + a, y, cz + b) and (cx + a, cz + b) not in blocked
                                            for a in (-1, 0, 1) for b in (-1, 0, 1)):
        if carpet:
            for a in (-1, 0, 1):
                for b in (-1, 0, 1):
                    t.set(cx + a, y, cz + b, f"minecraft:{carpet}_carpet")
        t.set(cx, y, cz, *fence(f"minecraft:{wood}_fence"))
        t.set(cx, y + 1, cz, f"minecraft:{wood}_pressure_plate", {"powered": "false"})
        for f2 in ("north", "south", "east", "west"):
            if rnd.random() < 0.8:
                t.set(cx + FACING[f2][0], y, cz + FACING[f2][1], *stairs(f"minecraft:{wood}_stairs", f2))
    elif carpet and w >= 3 and d >= 3:
        for a in (-1, 0, 1):
            for b in (-1, 0, 1):
                if is_air(t, cx + a, y, cz + b) and (cx + a, cz + b) not in keep:
                    t.set(cx + a, y, cz + b, f"minecraft:{carpet}_carpet")
    if lamp and ceiling is not None and is_air(t, cx, ceiling - 1, cz):
        lantern(t, cx, ceiling - 1, cz, hanging=True)


# ---------------------------------------------------------------------------------------
# Buildings
# ---------------------------------------------------------------------------------------

STONE = ["minecraft:stone_bricks", "minecraft:cobblestone", "minecraft:mossy_stone_bricks", "minecraft:cracked_stone_bricks",
         "minecraft:andesite"]
RUBBLE = ["minecraft:cobblestone", "minecraft:mossy_cobblestone", "minecraft:stone_bricks", "minecraft:andesite",
          "minecraft:tuff"]

STYLES = {
    # Firstia: jettied half-timbering over rubble-stone ground floors, steep timber roofs.
    "firstia": dict(plinth=RUBBLE, ground=STONE, quoin="minecraft:polished_andesite", infill=["minecraft:white_terracotta"],
                    frame="minecraft:stripped_dark_oak_log", bracket="minecraft:dark_oak_stairs",
                    roof="minecraft:spruce_stairs", roof_full="minecraft:spruce_planks", ridge="minecraft:dark_oak_slab",
                    roof_under="minecraft:dark_oak_stairs", floor="minecraft:spruce_planks", door="spruce",
                    shutter="minecraft:spruce_trapdoor", sill="minecraft:spruce_trapdoor", hood="minecraft:dark_oak_stairs",
                    chimney="minecraft:bricks", wood="spruce", carpet="red", lintel="minecraft:stone_brick_stairs"),
    # Firstia's stone buildings: chapel, guild hall
    "firstia_stone": dict(plinth=RUBBLE, ground=STONE, quoin="minecraft:polished_andesite", infill=STONE,
                          frame=None, bracket="minecraft:stone_brick_stairs", roof="minecraft:deepslate_tile_stairs",
                          roof_full="minecraft:deepslate_tiles", ridge="minecraft:deepslate_tile_slab",
                          roof_under="minecraft:stone_brick_stairs", floor="minecraft:dark_oak_planks", door="dark_oak",
                          shutter=None, sill=None, hood="minecraft:stone_brick_stairs", chimney="minecraft:stone_bricks",
                          wood="dark_oak", carpet="red", gable=STONE, braces=False, flowerboxes=False),
    # Secondil: sandstone with oak framing and red tile roofs
    "secondil": dict(plinth=["minecraft:sandstone", "minecraft:cut_sandstone", "minecraft:stone_bricks"],
                     ground=["minecraft:smooth_sandstone", "minecraft:sandstone", "minecraft:cut_sandstone"],
                     quoin="minecraft:chiseled_sandstone", infill=["minecraft:smooth_sandstone", "minecraft:cut_sandstone"],
                     frame="minecraft:stripped_oak_log", bracket="minecraft:oak_stairs", roof="minecraft:brick_stairs",
                     roof_full="minecraft:bricks", ridge="minecraft:mud_brick_slab", roof_under="minecraft:spruce_stairs",
                     floor="minecraft:oak_planks", door="oak", shutter="minecraft:jungle_trapdoor",
                     sill="minecraft:oak_trapdoor", hood="minecraft:smooth_sandstone_stairs",
                     chimney="minecraft:mud_bricks", wood="oak", carpet="orange", box="minecraft:rooted_dirt"),
    # Thirdrema: white stone, orange roofs, teal shutters
    "thirdrema": dict(plinth=["minecraft:polished_diorite", "minecraft:stone_bricks", "minecraft:smooth_stone"],
                      ground=["minecraft:calcite", "minecraft:white_concrete", "minecraft:calcite"], quoin="minecraft:quartz_bricks",
                      infill=["minecraft:white_concrete", "minecraft:calcite"], frame=None,
                      bracket="minecraft:quartz_stairs", roof="minecraft:smooth_red_sandstone_stairs",
                      roof_full="minecraft:smooth_red_sandstone", ridge="minecraft:cut_red_sandstone_slab",
                      roof_under="minecraft:quartz_stairs", floor="minecraft:birch_planks", door="warped",
                      shutter="minecraft:warped_trapdoor", sill="minecraft:birch_trapdoor", hood="minecraft:quartz_stairs",
                      chimney="minecraft:polished_diorite", wood="birch", carpet="cyan", braces=False,
                      gable=["minecraft:white_concrete", "minecraft:calcite"], box="minecraft:moss_block"),
    # Rabituza: dark timber posts, white plaster, slate tiles
    "rabituza": dict(plinth=["minecraft:polished_andesite", "minecraft:stone_bricks", "minecraft:andesite"], ground=None,
                     quoin=None, infill=["minecraft:white_concrete", "minecraft:calcite"],
                     frame="minecraft:stripped_dark_oak_log", bracket="minecraft:dark_oak_stairs",
                     roof="minecraft:deepslate_tile_stairs", roof_full="minecraft:deepslate_tiles",
                     ridge="minecraft:polished_blackstone_slab", roof_under="minecraft:dark_oak_stairs",
                     floor="minecraft:cherry_planks", door="bamboo", shutter="minecraft:bamboo_trapdoor",
                     sill="minecraft:bamboo_trapdoor", hood=None, chimney=None, wood="cherry", carpet="pink",
                     braces=False, bay=3, flowerboxes=False, pane="minecraft:white_stained_glass_pane"),
}


def _posts(n, bay=4):
    k = max(1, round((n - 1) / bay))
    return sorted({round(i * (n - 1) / k) for i in range(k + 1)})


def _side_cells(rect, side):
    x0, z0, x1, z1 = rect
    if side == "north":
        return [(x, z0) for x in range(x0, x1 + 1)]
    if side == "south":
        return [(x, z1) for x in range(x0, x1 + 1)]
    if side == "west":
        return [(x0, z) for z in range(z0, z1 + 1)]
    return [(x1, z) for z in range(z0, z1 + 1)]


def tavern_floor(t, x0, z0, x1, z1, fy, back, wood="spruce", keep=(), carpet="red"):
    """A taproom: a bar counter across the back with barrels and shelves behind it, and
    tables with chairs in front. (x0..x1, z0..z1) is the room's interior."""
    rnd = t.rnd
    y = fy + 1
    fx, fz = FACING[back]
    if back in ("north", "south"):
        wall_line = z0 if back == "north" else z1
        counter_line = wall_line - 2 * fz
        span = [(x, None) for x in range(x0 + 1, x1)]
        cell = lambda a, line: (a, line)
    else:
        wall_line = x0 if back == "west" else x1
        counter_line = wall_line - 2 * fx
        span = [(z, None) for z in range(z0 + 1, z1)]
        cell = lambda a, line: (line, a)
    keep = set(keep)
    for i, (a, _) in enumerate(span):
        cx, cz = cell(a, counter_line)
        near = [cell(a + o, line) for o in (-1, 0, 1) for line in (wall_line, wall_line - fx - fz, counter_line)]
        if any(p in keep for p in near) or i == len(span) - 1:
            continue
        t.set(cx, y, cz, *slab(f"minecraft:{wood}_slab", "top"))
        if i % 3 == 1:
            lantern(t, cx, y + 1, cz)
        elif i % 3 == 2 and rnd.random() < 0.6:
            t.set(cx, y + 1, cz, rnd.choice(POTS))
        bx, bz = cell(a, wall_line)
        t.set(bx, y, bz, *barrel(OPPOSITE[back]))
        t.set(bx, y + 1, bz, *barrel(OPPOSITE[back]) if i % 2 else ("minecraft:bookshelf", None))
    # tables with chairs, keeping a lane down the middle
    tables = []
    if back in ("north", "south"):
        zt = [z for z in range(z0 + 1, z1) if abs(z - counter_line) >= 3]
        for x in (x0 + 2, x1 - 2):
            for z in zt[::4][:2]:
                tables.append((x, z))
    else:
        xt = [x for x in range(x0 + 1, x1) if abs(x - counter_line) >= 3]
        for z in (z0 + 2, z1 - 2):
            for x in xt[::4][:2]:
                tables.append((x, z))
    for (tx_, tz_) in tables:
        if (tx_, tz_) in keep or not is_air(t, tx_, y, tz_):
            continue
        t.set(tx_, y, tz_, *fence(f"minecraft:{wood}_fence"))
        t.set(tx_, y + 1, tz_, f"minecraft:{wood}_pressure_plate", {"powered": "false"})
        for f2 in FACING:
            cx, cz = tx_ + FACING[f2][0], tz_ + FACING[f2][1]
            if x0 <= cx <= x1 and z0 <= cz <= z1 and is_air(t, cx, y, cz) and (cx, cz) not in keep:
                t.set(cx, y, cz, *stairs(f"minecraft:{wood}_stairs", f2))


def building(t, x0, z0, w, d, style="firstia", floors=2, h=4, door_side="south", door_at=None, roof_kind="gable",
             along=None, pitch=1, jetty=0, y=0, use="home", bed_color="red", chimney_on=True, dormers=False,
             cross=False, sign=None, awning=None, balcony=False, furnish_it=True, overrides=None, overhang=1,
             upturn=False, finial=None, lamps=True, back_door=False, ground_h=None, entrance=True):
    """A building on the w x d footprint at (x0, z0), standing on floor y: a stone plinth;
    storeys of masonry or timber framing (posts every few blocks, plates, diagonal braces);
    upper floors jettied out over the street on carved brackets; framed windows with sills,
    hoods, shutters and flower boxes; a door with a hood or the jetty over it, lamps, a hanging
    shop sign or a striped awning; a gable or hipped roof (optionally cross-gabled, with dormers)
    with thick eaves; a chimney with a fireplace; a balcony; and furnished rooms.
    Returns a dict with the door's outside cell ("door") and the roof's top ("top")."""
    s = dict(STYLES[style])
    s.update(overrides or {})
    x1, z1 = x0 + w - 1, z0 + d - 1
    along = along or ("x" if w >= d else "z")
    jsides = (door_side, OPPOSITE[door_side]) if jetty else ()
    gh = ground_h or h
    frame = s.get("frame")

    def rect(f):
        if f == 0 or not jetty:
            return (x0, z0, x1, z1)
        j = min(f, 2) * jetty
        e = {side: j for side in jsides}
        return (x0 - e.get("west", 0), z0 - e.get("north", 0), x1 + e.get("east", 0), z1 + e.get("south", 0))

    def base_of(f):
        return y if f == 0 else y + gh + (f - 1) * h

    def height_of(f):
        return gh if f == 0 else h

    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            put(t, x, y, z, s["plinth"] if (x in (x0, x1) or z in (z0, z1)) else s["floor"])
    # the ladder goes up a wall that is never jettied, in the corner furthest from the door
    lad_wall = CW[door_side]
    back = OPPOSITE[door_side]
    ix0, iz0, ix1, iz1 = x0 + 1, z0 + 1, x1 - 1, z1 - 1
    lx = {"west": ix0, "east": ix1}.get(lad_wall, {"west": ix0, "east": ix1}.get(back))
    lz = {"north": iz0, "south": iz1}.get(lad_wall, {"north": iz0, "south": iz1}.get(back))
    ladder_facing = OPPOSITE[lad_wall]
    door_cell = door_out = chimney_cell = None
    chim_side = OPPOSITE[lad_wall]
    chim_cells = _side_cells((x0, z0, x1, z1), chim_side)
    chim_posts = [p for p in _posts(len(chim_cells), s.get("bay", 4)) if 0 < p < len(chim_cells) - 1]
    chim_idx = min(chim_posts, key=lambda p: abs(p - (len(chim_cells) - 1) / 2)) if chim_posts \
        else (len(chim_cells) - 1) // 2
    has_chimney = bool(chimney_on and s.get("chimney") and roof_kind in ("gable", "hip"))
    for f in range(floors):
        R = rect(f)
        yb, hh = base_of(f), height_of(f)
        yt = yb + hh
        masonry = f == 0 and bool(s.get("ground"))
        for side in ("north", "south", "west", "east"):
            cells = _side_cells(R, side)
            n = len(cells)
            posts = _posts(n, s.get("bay", 4))
            bays = list(zip(posts[:-1], posts[1:]))
            ax = "x" if side in ("north", "south") else "z"
            door_idx = None
            if f == 0 and side == door_side and entrance:
                if door_at is not None:
                    door_idx = next(i for i, c in enumerate(cells) if (c[0] if ax == "x" else c[1]) == door_at)
                else:
                    door_idx = (n - 1) // 2
                if door_idx in posts:
                    door_idx += 1 if door_idx < n - 2 else -1
            for i, (cx, cz) in enumerate(cells):
                corner = i in (0, n - 1)
                for yy in range(yb + 1, yt):
                    r = yy - yb
                    if masonry:
                        if corner and s.get("quoin") and r % 2:
                            put(t, cx, yy, cz, s["quoin"])
                        else:
                            put(t, cx, yy, cz, s["plinth"] if r == 1 else s["ground"])
                    elif frame and i in posts:
                        t.set(cx, yy, cz, *log(frame, "y"))
                    elif f == 0 and r == 1:
                        put(t, cx, yy, cz, s["plinth"])
                    else:
                        put(t, cx, yy, cz, s["infill"])
                if frame:
                    t.set(cx, yt, cz, *log(frame, ax))
                else:
                    put(t, cx, yt, cz, s.get("course") or s.get("quoin") or s["infill"])
            for bi, (a, b) in enumerate(bays):
                inner = list(range(a + 1, b))
                if not inner:
                    continue
                if door_idx is not None and a < door_idx < b:
                    kind = "door"
                elif len(bays) >= 3 and bi in (0, len(bays) - 1) and frame and not masonry and s.get("braces", True):
                    kind = "brace"
                else:
                    kind = "window"
                if kind == "brace":
                    rows = list(range(yb + (2 if f == 0 else 1), yt))
                    m = len(rows)
                    for k_, yy in enumerate(rows):
                        j = round(k_ * (len(inner) - 1) / max(1, m - 1))
                        idx = inner[-1 - j] if bi == 0 else inner[j]
                        t.set(cells[idx][0], yy, cells[idx][1], *log(frame, "y"))
                elif kind == "window":
                    if len(inner) == 1:
                        wc = [inner[0]]
                    elif len(inner) in (2, 4):
                        wc = [inner[len(inner) // 2 - 1], inner[len(inner) // 2]]
                    elif len(inner) == 3:
                        wc = [inner[1]]
                    else:
                        wc = [inner[1], inner[-2]]
                    groups = [[cells[i] for i in wc]] if len(wc) == 2 and wc[1] - wc[0] == 1 else [[cells[i]] for i in wc]
                    wy0 = yb + 2
                    wy1 = yb + 2 if hh <= 4 else yt - 2
                    for g in groups:
                        if (masonry or not frame) and s.get("quoin"):
                            for (gx, gz) in g:
                                put(t, gx, wy0 - 1, gz, s["quoin"])
                        window_dressing(t, g, wy0, wy1, side, s, upper=f > 0, flowerbox=None if f > 0 else False)
                elif kind == "door":
                    dcx, dcz = cells[door_idx]
                    door(t, dcx, yb + 1, dcz, side, s["door"])
                    ox_, oz_ = FACING[side]
                    door_cell, door_out = (dcx, dcz), (dcx + ox_, dcz + oz_)
                    reserve(t, dcx + ox_, dcz + oz_, dcx + ox_, dcz + oz_, yb)
                    reserve(t, dcx - ox_, dcz - oz_, dcx - ox_, dcz - oz_, yb)
                    for j in (door_idx - 1, door_idx + 1):
                        jx, jz = cells[j]
                        for yy in range(yb + 1, yb + 3):
                            if frame and not masonry:
                                t.set(jx, yy, jz, *log(frame, "y"))
                            elif s.get("quoin"):
                                put(t, jx, yy, jz, s["quoin"])
                    if hh >= 4:
                        for j in (door_idx - 1, door_idx, door_idx + 1):
                            if frame and not masonry:
                                t.set(cells[j][0], yb + 3, cells[j][1], *log(frame, ax))
                            elif s.get("quoin"):
                                put(t, cells[j][0], yb + 3, cells[j][1], s["quoin"])
                    for j in inner:
                        if abs(j - door_idx) >= 2 and hh >= 4:
                            window_dressing(t, [cells[j]], yb + 2, yb + 2, side, s, upper=False, flowerbox=False)
        # the floor above (or the attic floor), with beams round the jettied edge
        Rn = rect(f + 1) if f + 1 < floors else R
        for x in range(Rn[0], Rn[2] + 1):
            for z in range(Rn[1], Rn[3] + 1):
                edge = x in (Rn[0], Rn[2]) or z in (Rn[1], Rn[3])
                if not edge:
                    t.set(x, yt, z, s["floor"])
                elif Rn != R:
                    if frame:
                        t.set(x, yt, z, *log(frame, "x" if z in (Rn[1], Rn[3]) and x not in (Rn[0], Rn[2]) else "z"))
                    else:
                        put(t, x, yt, z, s.get("course") or s.get("quoin") or s["infill"])
        if f + 1 < floors and jetty:
            for side in jsides:
                cells = _side_cells(R, side)
                ox_, oz_ = FACING[side]
                for i in _posts(len(cells), s.get("bay", 4)):
                    bx_, bz_ = cells[i][0] + ox_, cells[i][1] + oz_
                    if is_air(t, bx_, yt - 1, bz_):
                        t.set(bx_, yt - 1, bz_, *stairs(s.get("bracket") or "minecraft:dark_oak_stairs",
                                                        OPPOSITE[side], "top"))
    top_rect = rect(floors - 1)
    eave = base_of(floors - 1) + height_of(floors - 1) + 1
    if floors > 1:
        wx, wz = lx + FACING[lad_wall][0], lz + FACING[lad_wall][1]
        for yy in range(y + 1, base_of(floors - 1) + 1):
            t.set(lx, yy, lz, "minecraft:ladder", {"facing": ladder_facing, "waterlogged": "false"})
            cur = t.blocks.get((wx, yy, wz))
            if not cur or not _full_cube(cur[0], cur[1]):
                if frame:
                    t.set(wx, yy, wz, *log(frame, "y"))
                else:
                    put(t, wx, yy, wz, s["infill"])
    # roof
    tx0, tz0, tx1, tz1 = top_rect
    parts = []
    if roof_kind == "hip":
        parts.append(hip_part(tx0, tz0, tx1, tz1, eave, pitch, overhang))
    elif roof_kind == "gable":
        parts.append(gable_part(tx0, tz0, tx1, tz1, along, eave, pitch, overhang))
    if cross and parts:
        half = 2 if (tx1 - tx0 if along == "x" else tz1 - tz0) < 14 else 3
        if along == "x":
            mx = (tx0 + tx1) // 2
            parts.append(gable_part(mx - half, tz0, mx + half, tz1, "z", eave, pitch, overhang))
        else:
            mz = (tz0 + tz1) // 2
            parts.append(gable_part(tx0, mz - half, tx1, mz + half, "x", eave, pitch, overhang))
    dormer_windows = []
    if dormers and parts:
        for side in (("north", "south") if along == "x" else ("west", "east")):
            cells = _side_cells(top_rect, side)
            posts = _posts(len(cells), s.get("bay", 4))
            centres = [(a + b) // 2 for a, b in zip(posts[:-1], posts[1:]) if b - a >= 4]
            if cross:
                centres = [c for c in centres if abs(c - (len(cells) - 1) // 2) > 3]
            mid_c = (len(cells) - 1) // 2
            chosen = []
            for c_ in sorted(centres, key=lambda c_: abs(c_ - mid_c)):
                if all(abs(c_ - o) >= 7 for o in chosen):
                    chosen.append(c_)
            for ci in chosen:
                dcx, dcz = cells[ci]
                ox_, oz_ = FACING[side]
                if along == "x":
                    za, zb = (tz0, tz0 + 2) if side == "north" else (tz1 - 2, tz1)
                    parts.append(gable_part(dcx - 1, za, dcx + 1, zb, "z", eave + 1, pitch, 1))
                else:
                    xa, xb = (tx0, tx0 + 2) if side == "west" else (tx1 - 2, tx1)
                    parts.append(gable_part(xa, dcz - 1, xb, dcz + 1, "x", eave + 1, pitch, 1))
                dormer_windows.append((dcx, dcz, side))
    field = {}
    if parts:
        field = roof(t, parts, eave, s, walls=[top_rect], finial=finial, upturn=upturn)
        for (dcx, dcz, side) in dormer_windows:
            for yy in range(eave + 1, eave + 1 + max(1, pitch)):
                t.set(dcx, yy, dcz, *pane(s.get("pane", "minecraft:glass_pane")))
            if s.get("shutter"):
                px, pz = (1, 0) if side in ("north", "south") else (0, 1)
                ox_, oz_ = FACING[side]
                for k_ in (-1, 1):
                    sx_, sz_ = dcx + px * k_ + ox_, dcz + pz * k_ + oz_
                    if is_air(t, sx_, eave + 1, sz_):
                        t.set(sx_, eave + 1, sz_, *trapdoor(s["shutter"], side, "bottom", True))
    elif roof_kind == "flat":
        _flat_roof(t, top_rect, eave - 1, s)
    # chimney stack with a fireplace (a lit smoker) at its foot
    if has_chimney and field:
        ccx, ccz = chim_cells[chim_idx]
        tops = [field[(ccx + a, ccz + b)][0] for a in (-1, 0, 1) for b in (-1, 0, 1) if (ccx + a, ccz + b) in field]
        top = max(tops) + 2 if tops else eave + 2
        chimney(t, ccx, ccz, y + 2, top, s["chimney"])
        t.set(ccx, y + 1, ccz, "minecraft:blast_furnace" if use == "smithy" else "minecraft:smoker",
              {"facing": OPPOSITE[chim_side], "lit": "true"})
        ox_, oz_ = FACING[chim_side]
        px, pz = (1, 0) if chim_side in ("north", "south") else (0, 1)
        for yy in range(y + 1, top + 1):
            for k_ in (-1, 0, 1):
                ex, ez = ccx + px * k_ + ox_, ccz + pz * k_ + oz_
                nm_ = _short(t.get(ex, yy, ez))
                if nm_.endswith("_trapdoor") or nm_ in ("moss_block", "rooted_dirt") or nm_.startswith("potted_") \
                        or "minecraft:" + nm_ in BOX_FLOWERS or (k_ == 0 and nm_.endswith("_stairs") and yy < eave):
                    t.set(ex, yy, ez, AIR)
        chimney_cell = (ccx - ox_, ccz - oz_)
    # a balcony off the top floor
    if balcony and floors >= 2 and not jetty:
        cells = _side_cells(top_rect, door_side)
        bx, bz = cells[(len(cells) - 1) // 2]
        ox_, oz_ = FACING[door_side]
        px, pz = (1, 0) if door_side in ("north", "south") else (0, 1)
        yb = base_of(floors - 1)
        for k_ in range(-1, 2):
            for dep in (1, 2):
                for yy in range(yb, yb + 3):
                    cx_, cz_ = bx + px * k_ + ox_ * dep, bz + pz * k_ + oz_ * dep
                    if t.get(cx_, yy, cz_) not in (None, AIR):
                        t.set(cx_, yy, cz_, AIR)
        for k_ in range(-1, 2):
            for dep in (1, 2):
                cx_, cz_ = bx + px * k_ + ox_ * dep, bz + pz * k_ + oz_ * dep
                t.set(cx_, yb, cz_, *slab(slab_of(s["floor"]) or "minecraft:spruce_slab", "top"))
                if dep == 2 or abs(k_) == 1:
                    t.set(cx_, yb + 1, cz_, *fence(f"minecraft:{s.get('wood', 'spruce')}_fence"))
                    if dep == 2 and k_ != 0 and t.rnd.random() < 0.7:
                        t.set(cx_, yb + 2, cz_, t.rnd.choice(POTS))
            t.set(bx + px * k_ + ox_, yb - 1, bz + pz * k_ + oz_,
                  *stairs(s.get("bracket") or "minecraft:dark_oak_stairs", OPPOSITE[door_side], "top"))
        door(t, bx, yb + 1, bz, door_side, s["door"])
    # outside the door: a hood (or the jetty overhead), lamps, an awning, a hanging sign
    if door_cell:
        cells = _side_cells((x0, z0, x1, z1), door_side)
        di = cells.index(door_cell)
        ox_, oz_ = FACING[door_side]
        jettied = door_side in jsides and floors > 1
        if awning and not jettied:
            for i, (cx_, cz_) in enumerate(cells[1:-1], 1):
                col = awning[i % len(awning)]
                if is_air(t, cx_ + ox_, y + gh, cz_ + oz_):
                    t.set(cx_ + ox_, y + gh, cz_ + oz_, f"minecraft:{col}_wool")
                if is_air(t, cx_ + 2 * ox_, y + gh - 1, cz_ + 2 * oz_):
                    t.set(cx_ + 2 * ox_, y + gh - 1, cz_ + 2 * oz_, f"minecraft:{col}_wool")
        elif not jettied:
            for j in (di - 1, di, di + 1):
                hx, hz = cells[j][0] + ox_, cells[j][1] + oz_
                if is_air(t, hx, y + 3, hz) or _short(t.get(hx, y + 3, hz)).endswith("_stairs"):
                    t.set(hx, y + 3, hz, *stairs(s.get("bracket") or "minecraft:dark_oak_stairs", OPPOSITE[door_side]))
        if lamps and not awning:
            for j in (di - 1, di + 1):
                hx, hz = cells[j][0] + ox_, cells[j][1] + oz_
                ly = y + gh - 1 if jettied else y + 2
                if ly == y + 2 and is_reserved(t, hx, hz):
                    continue
                above = t.blocks.get((hx, ly + 1, hz))
                if is_air(t, hx, ly, hz) and above and _center_down(above[0], above[1]):
                    lantern(t, hx, ly, hz, hanging=True)
        if sign:
            for j in (di + 2, di - 2, di + 3, di - 3):
                if 0 < j < len(cells) - 1:
                    sx_, sz_ = cells[j][0] + ox_, cells[j][1] + oz_
                    sy = y + 3
                    wallb = t.blocks.get((cells[j][0], sy, cells[j][1]))
                    if is_air(t, sx_, sy, sz_) and wallb and _full_cube(wallb[0], wallb[1]):
                        hanging_sign(t, sx_, sy, sz_, CW[door_side], sign, wood=s.get("sign_wood", s.get("wood", "spruce")))
                        break
        if back_door:
            bcells = _side_cells((x0, z0, x1, z1), back)
            bi = (len(bcells) - 1) // 2
            if bi in _posts(len(bcells), s.get("bay", 4)):
                bi += 1
            bx_, bz_ = bcells[bi]
            door(t, bx_, y + 1, bz_, back, s["door"])
            reserve(t, bx_ + FACING[back][0], bz_ + FACING[back][1], bx_ + FACING[back][0], bz_ + FACING[back][1], y)
            reserve(t, bx_ - FACING[back][0], bz_ - FACING[back][1], bx_ - FACING[back][0], bz_ - FACING[back][1], y)
            for yy in (y + 1, y + 2):
                ex, ez = bx_ + FACING[back][0], bz_ + FACING[back][1]
                if not passable(t.get(ex, yy, ez)):
                    t.set(ex, yy, ez, AIR)
    # rooms
    if furnish_it:
        inside_door = (door_cell[0] - FACING[door_side][0], door_cell[1] - FACING[door_side][1]) if door_cell else None
        for f in range(floors):
            R = rect(f)
            yb, hh = base_of(f), height_of(f)
            keep = [(lx, lz)]
            if f == 0 and inside_door:
                keep.append(inside_door)
            if f == 0 and back_door:
                bcells = _side_cells((x0, z0, x1, z1), back)
                bi = (len(bcells) - 1) // 2
                if bi in _posts(len(bcells), s.get("bay", 4)):
                    bi += 1
                keep.append((bcells[bi][0] - FACING[back][0], bcells[bi][1] - FACING[back][1]))
            if chimney_cell and f == 0:
                keep.append(chimney_cell)
            if balcony and f == floors - 1:
                cells = _side_cells(top_rect, door_side)
                bx, bz = cells[(len(cells) - 1) // 2]
                keep.append((bx - FACING[door_side][0], bz - FACING[door_side][1]))
            if f == 0 and use in ("inn", "tavern"):
                tavern_floor(t, R[0] + 1, R[1] + 1, R[2] - 1, R[3] - 1, yb, back, s.get("wood", "spruce"), keep)
                lantern(t, (R[0] + R[2]) // 2, yb + hh - 1, (R[1] + R[3]) // 2, hanging=True)
                continue
            kind = f"{use}{min(f, 2)}"
            if kind not in ROOMS:
                kind = f"{use}{min(f, 1)}" if f"{use}{min(f, 1)}" in ROOMS else ("home1" if f else "home0")
            furnish(t, R[0] + 1, R[1] + 1, R[2] - 1, R[3] - 1, yb, kind, ceiling=yb + hh, keep=keep,
                    bed_color=bed_color, wood=s.get("wood", "spruce"), carpet=s.get("carpet", "red"))
    top_y = max((v[0] for v in field.values()), default=eave)
    return {"door": door_out, "door_cell": door_cell, "eave": eave, "top": top_y, "rect": (x0, z0, x1, z1),
            "top_rect": top_rect, "field": field}


def _flat_roof(t, rect, yr, s):
    """A flat roof terrace: paved, with a parapet, flowering shrubs in planters and a little
    pergola over a table."""
    x0, z0, x1, z1 = rect
    rnd = t.rnd
    wood = s.get("wood", "spruce")
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            edge = x in (x0, x1) or z in (z0, z1)
            t.set(x, yr, z, s.get("terrace", "minecraft:smooth_stone") if not edge or (x + z) % 2 else
                  s.get("quoin") or "minecraft:smooth_stone")
            if edge:
                put(t, x, yr + 1, z, s.get("quoin") or s["infill"])
                if (x + z) % 2 == 0:
                    t.set(x, yr + 2, z, *slab(slab_of(s.get("quoin") or "") or "minecraft:smooth_stone_slab"))
    for (px, pz) in ((x0 + 1, z0 + 1), (x1 - 1, z1 - 1), (x1 - 1, z0 + 1)):
        t.set(px, yr + 1, pz, "minecraft:moss_block")
        t.set(px, yr + 2, pz, rnd.choice(["minecraft:flowering_azalea", "minecraft:azalea"]))
    if x1 - x0 >= 6 and z1 - z0 >= 6:
        ax, az = x0 + 2, z1 - 4
        for (a, b) in ((0, 0), (2, 0), (0, 2), (2, 2)):
            for y in (yr + 1, yr + 2):
                t.set(ax + a, y, az + b, *fence(f"minecraft:{wood}_fence"))
        for a in range(-1, 4):
            for b in range(-1, 4):
                if (a + b) % 2 == 0 or a in (0, 2) and b in (0, 2):
                    t.set(ax + a, yr + 3, az + b, *slab(f"minecraft:{wood}_slab"))
        t.set(ax + 1, yr + 1, az + 1, *fence(f"minecraft:{wood}_fence"))
        t.set(ax + 1, yr + 2, az + 1, f"minecraft:{wood}_pressure_plate", {"powered": "false"})
        t.set(ax + 1, yr + 1, az, *stairs(f"minecraft:{wood}_stairs", "north"))
        t.set(ax + 1, yr + 1, az + 2, *stairs(f"minecraft:{wood}_stairs", "south"))


class _Shifted:
    """A view of a template moved `dy` blocks up, so ground-level builders (fountains, wells)
    can be used on a terrace."""

    class _Blocks:
        def __init__(self, blocks, dy):
            self.b, self.dy = blocks, dy

        def get(self, p, default=None):
            return self.b.get((p[0], p[1] + self.dy, p[2]), default)

        def __getitem__(self, p):
            return self.b[(p[0], p[1] + self.dy, p[2])]

        def __contains__(self, p):
            return (p[0], p[1] + self.dy, p[2]) in self.b

    def __init__(self, t, dy):
        self._t, self._dy, self.rnd, self.size = t, dy, t.rnd, t.size
        self.blocks = _Shifted._Blocks(t.blocks, dy)

    def set(self, x, y, z, block, props=None, nbt_data=None):
        self._t.set(x, y + self._dy, z, block, props, nbt_data)

    def get(self, x, y, z):
        return self._t.get(x, y + self._dy, z)


def lifted(t, dy):
    return _Shifted(t, dy)


# ---------------------------------------------------------------------------------------
# Fortifications
# ---------------------------------------------------------------------------------------

CASTLE = {
    "firstia": dict(body=["minecraft:stone_bricks", "minecraft:stone_bricks", "minecraft:mossy_stone_bricks",
                          "minecraft:cracked_stone_bricks", "minecraft:andesite"],
                    trim="minecraft:polished_andesite", corbel="minecraft:stone_brick_stairs",
                    cap="minecraft:stone_brick_slab", walk="minecraft:smooth_stone",
                    roof="minecraft:deepslate_tile_stairs", roof_full="minecraft:deepslate_tiles",
                    ridge="minecraft:deepslate_tile_slab", floor="minecraft:spruce_planks"),
    "secondil": dict(body=["minecraft:cut_sandstone", "minecraft:cut_sandstone", "minecraft:sandstone",
                           "minecraft:smooth_sandstone"],
                     trim="minecraft:chiseled_sandstone", corbel="minecraft:sandstone_stairs",
                     cap="minecraft:smooth_sandstone_slab", walk="minecraft:smooth_sandstone",
                     roof="minecraft:brick_stairs", roof_full="minecraft:bricks", ridge="minecraft:brick_slab",
                     floor="minecraft:oak_planks"),
    "thirdrema": dict(body=["minecraft:calcite", "minecraft:calcite", "minecraft:white_concrete", "minecraft:polished_diorite"],
                      trim="minecraft:quartz_bricks", corbel="minecraft:quartz_stairs", cap="minecraft:quartz_slab",
                      walk="minecraft:polished_diorite", roof="minecraft:smooth_red_sandstone_stairs",
                      roof_full="minecraft:orange_terracotta", ridge="minecraft:cut_red_sandstone_slab",
                      floor="minecraft:birch_planks"),
}


def cone_part(cx, cz, r, y0, height):
    rr = r + 0.35

    def f(x, z):
        dd = math.hypot(x - cx, z - cz)
        if dd <= rr:
            return y0 + int((rr - dd) * height / rr)
        return None
    f.bbox, f.along = (int(cx - r - 1), int(cz - r - 1), int(cx + r + 1), int(cz + r + 1)), "x"
    return f


def cone_roof(t, cx, cz, r, y0, height, s, finial=None):
    """A steep conical roof with stair-stepped edges (laid with the roof height field)."""
    field = roof(t, [cone_part(cx, cz, r, y0, height)], y0, s, walls=(), finial=None)
    top = max(v[0] for v in field.values())
    if finial:
        put(t, cx, top + 1, cz, finial)
    return top


def _ring_cells(cx, cz, r):
    return [(x, z) for x in range(int(cx - r - 2), int(cx + r + 3)) for z in range(int(cz - r - 2), int(cz + r + 3))
            if r - 0.9 <= math.hypot(x - cx, z - cz) <= r + 0.4]


def _toward(cx, cz, x, z):
    dx, dz = cx - x, cz - z
    if abs(dx) >= abs(dz):
        return "east" if dx > 0 else "west"
    return "south" if dz > 0 else "north"


def round_tower(t, cx, cz, r, height, style="firstia", roof_h=None, roof_over=False, base=0, slits=True, mach=True,
                flag=None, floors=True, door_side=None, batter=True):
    """A round tower: battered base, banded masonry, arrow slits, a corbelled machicolation
    crown with merlons, and a conical roof (inside the parapet, or over it) with a flag."""
    s = CASTLE[style]
    top = base + height
    for y in range(base + 1, top + 1):
        band = (y - base) % 7 == 0
        for (x, z) in _ring_cells(cx, cz, r):
            put(t, x, y, z, s["trim"] if band else s["body"])
        for x in range(cx - r, cx + r + 1):
            for z in range(cz - r, cz + r + 1):
                if math.hypot(x - cx, z - cz) < r - 0.9:
                    t.set(x, y, z, AIR)
    if batter:
        for (x, z) in _ring_cells(cx, cz, r + 1):
            if math.hypot(x - cx, z - cz) > r + 0.4 and is_air(t, x, base + 1, z) and not is_reserved(t, x, z):
                t.set(x, base + 1, z, *stairs(s["corbel"], _toward(cx, cz, x, z)))
    if floors:
        for fy in range(base + 5, top - 1, 5):
            for x in range(cx - r, cx + r + 1):
                for z in range(cz - r, cz + r + 1):
                    if math.hypot(x - cx, z - cz) < r - 0.9:
                        t.set(x, fy, z, s["floor"])
    if slits:
        k = 0
        for y in range(base + 3, top - 2, 4):
            for a in range(0, 360, 90):
                ang = math.radians(a + (45 if k % 2 else 0))
                x, z = round(cx + r * math.cos(ang)), round(cz + r * math.sin(ang))
                for yy in (y, y + 1):
                    t.set(x, yy, z, AIR)
            k += 1
    if door_side:
        dx, dz = FACING[door_side]
        x, z = cx + dx * r, cz + dz * r
        door(t, x, base + 1, z, door_side, "spruce")
    # machicolations: a corbelled ring with a parapet floor and merlons
    rr = r + 1 if mach else r
    if mach:
        for (x, z) in _ring_cells(cx, cz, r + 1):
            if math.hypot(x - cx, z - cz) > r + 0.4:
                t.set(x, top - 1, z, *stairs(s["corbel"], _toward(cx, cz, x, z), "top"))
    for x in range(cx - rr - 1, cx + rr + 2):
        for z in range(cz - rr - 1, cz + rr + 2):
            dd = math.hypot(x - cx, z - cz)
            if dd <= rr + 0.4:
                put(t, x, top, z, s["trim"] if dd > rr - 0.6 else s["walk"])
    roof_h = roof_h or int(r * 2.4)
    rs = {"roof": s["roof"], "roof_full": s["roof_full"], "ridge": s["ridge"], "roof_under": s["corbel"]}
    if roof_over:
        peak = cone_roof(t, cx, cz, rr + 1, top + 1, roof_h, rs)
    else:
        for (x, z) in _ring_cells(cx, cz, rr):
            if (x + z) % 2 == 0:
                put(t, x, top + 1, z, s["body"])
                t.set(x, top + 2, z, *slab(s["cap"]))
        peak = top + 1
        if roof_h > 0:
            peak = cone_roof(t, cx, cz, r - 1, top + 1, roof_h, rs)
    if flag:
        t.set(cx, peak + 1, cz, *fence("minecraft:spruce_fence"))
        standing_banner(t, cx, peak + 2, cz, 4, flag[0], flag[1] if len(flag) > 1 else None)
    return peak


def square_tower(t, x0, z0, size, height, style="firstia", roof_kind="pyramid", base=0, slits=True, mach=True,
                 flag=None, pitch=2, floors=True, sizez=None):
    """A square tower: quoins, banding, arrow slits, a machicolated parapet and either merlons
    or a steep pyramid roof."""
    s = CASTLE[style]
    sz = sizez or size
    x1, z1 = x0 + size - 1, z0 + sz - 1
    top = base + height
    for y in range(base + 1, top + 1):
        band = (y - base) % 7 == 0
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                edge = x in (x0, x1) or z in (z0, z1)
                corner = x in (x0, x1) and z in (z0, z1)
                if edge:
                    put(t, x, y, z, s["trim"] if band or (corner and y % 2) else s["body"])
                else:
                    t.set(x, y, z, AIR)
    if floors:
        for fy in range(base + 5, top - 1, 5):
            t.fill(x0 + 1, fy, z0 + 1, x1 - 1, fy, z1 - 1, s["floor"])
    if slits:
        for y in range(base + 3, top - 2, 5):
            for (x, z) in (((x0 + x1) // 2, z0), ((x0 + x1) // 2, z1), (x0, (z0 + z1) // 2), (x1, (z0 + z1) // 2)):
                t.set(x, y, z, AIR)
                t.set(x, y + 1, z, AIR)
    e = 1 if mach else 0
    if mach:
        for x in range(x0 - 1, x1 + 2):
            for z in range(z0 - 1, z1 + 2):
                if x in (x0 - 1, x1 + 1) or z in (z0 - 1, z1 + 1):
                    if x in (x0 - 1, x1 + 1) and z in (z0 - 1, z1 + 1):
                        put(t, x, top - 1, z, s["trim"])
                    else:
                        f = "east" if x == x0 - 1 else "west" if x == x1 + 1 else "south" if z == z0 - 1 else "north"
                        t.set(x, top - 1, z, *stairs(s["corbel"], f, "top"))
    for x in range(x0 - e, x1 + e + 1):
        for z in range(z0 - e, z1 + e + 1):
            edge = x in (x0 - e, x1 + e) or z in (z0 - e, z1 + e)
            put(t, x, top, z, s["trim"] if edge else s["walk"])
    rs = {"roof": s["roof"], "roof_full": s["roof_full"], "ridge": s["ridge"], "roof_under": s["corbel"]}
    peak = top
    if roof_kind == "pyramid":
        field = roof(t, [hip_part(x0, z0, x1, z1, top + 1, pitch, e)], top + 1, rs)
        peak = max(v[0] for v in field.values())
    else:
        for x in range(x0 - e, x1 + e + 1):
            for z in range(z0 - e, z1 + e + 1):
                if (x in (x0 - e, x1 + e) or z in (z0 - e, z1 + e)) and (x + z) % 2 == 0:
                    put(t, x, top + 1, z, s["body"])
                    t.set(x, top + 2, z, *slab(s["cap"]))
    if flag:
        cx, cz = (x0 + x1) // 2, (z0 + z1) // 2
        t.set(cx, peak + 1, cz, *fence("minecraft:spruce_fence"))
        standing_banner(t, cx, peak + 2, cz, 4, flag[0], flag[1] if len(flag) > 1 else None)
    return peak


def curtain_wall(t, x0, z0, x1, z1, height, style="firstia", out="north", thick=3, gaps=(), batter=True,
                 buttress=0, lamps=0, corbels=True):
    """A straight curtain wall `thick` blocks deep (growing away from `out`), with a battered
    foot, a corbelled parapet with merlons and capped embrasures, and a paved wall walk.
    `gaps` are (a, b) ranges along the wall left open (gates)."""
    s = CASTLE[style]
    ox, oz = FACING[out]
    ix, iz = -ox, -oz
    if z0 == z1:
        line = [(x, z0) for x in range(min(x0, x1), max(x0, x1) + 1)]
        coord = lambda c: c[0]
    else:
        line = [(x0, z) for z in range(min(z0, z1), max(z0, z1) + 1)]
        coord = lambda c: c[1]
    for i, (x, z) in enumerate(line):
        a = coord((x, z))
        if any(g0 <= a <= g1 for g0, g1 in gaps):
            continue
        for k in range(thick):
            cx, cz = x + ix * k, z + iz * k
            for y in range(1, height + 1):
                put(t, cx, y, cz, s["body"] if k == 0 else s["body"][0] if isinstance(s["body"], list) else s["body"])
            if k > 0:
                put(t, cx, height, cz, s["walk"])
        if corbels:
            t.set(x + ox, height, z + oz, *stairs(s["corbel"], OPPOSITE[out], "top"))
            ex, ez = x + ox, z + oz
        else:
            ex, ez = x, z
        if i % 2 == 0:
            put(t, ex, height + 1, ez, s["body"])
            t.set(ex, height + 2, ez, *slab(s["cap"]))
            if lamps and i % lamps == 0:
                put(t, ex, height + 2, ez, s["trim"])
                lantern(t, ex, height + 3, ez)
        else:
            t.set(ex, height + 1, ez, *slab(s["cap"]))
        if corbels:
            put(t, x, height, z, s["walk"])
        if batter and not is_reserved(t, x + ox, z + oz) and is_air(t, x + ox, 1, z + oz):
            t.set(x + ox, 1, z + oz, *stairs(s["corbel"], OPPOSITE[out]))
        if buttress and i % buttress == buttress // 2 and 0 < i < len(line) - 1:
            bx, bz = x + ox, z + oz
            if not is_reserved(t, bx, bz):
                for y in range(1, height - 3):
                    put(t, bx, y, bz, s["body"])
                t.set(bx, height - 3, bz, *stairs(s["corbel"], OPPOSITE[out]))


def wall_stairs(t, x, z, height, direction, style="firstia"):
    """A flight of steps up the inside face of a wall to its walk (climbing towards `direction`)."""
    s = CASTLE[style]
    dx, dz = FACING[direction]
    for i in range(height):
        sx_, sz_ = x + dx * i, z + dz * i
        t.set(sx_, i + 1, sz_, *stairs(s["corbel"], direction))
        for k in range(1, i + 1):
            put(t, sx_, k, sz_, s["body"])


def arch_opening(t, cells, y0, spring, across, stair):
    """Carves an arched opening. `cells` are the opening's columns across its width (in order,
    stepping by `across` = (dx, dz)), each a list of (x, z) cells through the wall's depth.
    The shoulders get upside-down stairs so the crown reads as a round arch. Returns the top
    height of each column."""
    w = len(cells)
    half = w / 2
    tops = []
    for i, col in enumerate(cells):
        u = i - (w - 1) / 2
        top = y0 + spring + int(math.sqrt(max(0, half * half - u * u)))
        tops.append(top)
        for (x, z) in col:
            for y in range(y0 + 1, top + 1):
                t.set(x, y, z, AIR)
    for i in (0, w - 1):
        if tops[i] < max(tops):
            sgn = -1 if i == 0 else 1
            f = facing_of(across[0] * sgn, across[1] * sgn)
            for (x, z) in cells[i]:
                t.set(x, tops[i] + 1, z, *stairs(stair, f, "top"))
    return tops


def portcullis(t, cells, tops, rows=2):
    """Iron bars hanging in the top of an archway (raised, so the passage stays open)."""
    for (x, z), top in zip(cells, tops):
        for y in range(top - rows + 1, top + 1):
            t.set(x, y, z, "minecraft:iron_bars", {"waterlogged": "false"})


def gatehouse(t, cx, cz, out, style="firstia", passage=5, depth=8, height=11, tower=6, tower_h=16, wall_thick=3,
              banner_colors=("blue", "white"), patterns=None, arch_spring=4, round_towers=False, tower_out=1,
              tower_depth=None):
    """A gatehouse straddling a wall: an arched passage `passage` wide running along `out`'s axis
    (from the outside face at the wall line inward `depth` blocks), flanking towers, a raised
    portcullis, machicolations over the arch, crenellations and banners. (cx, cz) is the centre
    of the outer face of the passage."""
    s = CASTLE[style]
    ox, oz = FACING[out]
    ix, iz = -ox, -oz
    px, pz = (0, 1) if out in ("east", "west") else (1, 0)
    hw = passage // 2
    # gate block over the passage
    for dpt in range(depth):
        for u in range(-hw - 1, hw + 2):
            x, z = cx + ix * dpt + px * u, cz + iz * dpt + pz * u
            for y in range(1, height + 1):
                put(t, x, y, z, s["body"])
            put(t, x, height, z, s["walk"])
    cols = [[(cx + ix * dpt + px * u, cz + iz * dpt + pz * u) for dpt in range(depth)] for u in range(-hw, hw + 1)]
    tops = arch_opening(t, cols, 0, arch_spring, (px, pz), s["corbel"])
    for i, col in enumerate(cols):
        for (x, z) in (col[0], col[-1]):
            put(t, x, tops[i] + 1, z, s["trim"])
    portcullis(t, [c[1] for c in cols], tops)
    portcullis(t, [c[-2] for c in cols], tops, rows=1)
    reserve(t, *cols[0][0], *cols[-1][-1], 0)
    # flanking towers
    tz_list = []
    for sgn in (-1, 1):
        if round_towers:
            r = tower // 2
            tcx = cx + ix * (depth // 2) + px * sgn * (hw + 1 + r)
            tcz = cz + iz * (depth // 2) + pz * sgn * (hw + 1 + r)
            round_tower(t, tcx, tcz, r, tower_h, style, roof_h=int(r * 2.6), flag=(banner_colors[0], patterns))
            tz_list.append((tcx, tcz))
        else:
            a = hw + 1
            td = tower_depth or depth + 1
            fx0, fz0 = cx + ox * tower_out, cz + oz * tower_out
            corners = [(fx0 + px * sgn * a, fz0 + pz * sgn * a),
                       (fx0 + ix * (td - 1) + px * sgn * (a + tower - 1), fz0 + iz * (td - 1) + pz * sgn * (a + tower - 1))]
            xs = [c[0] for c in corners]
            zs = [c[1] for c in corners]
            tx0, tz0 = min(xs), min(zs)
            square_tower(t, tx0, tz0, max(xs) - tx0 + 1, tower_h, style, roof_kind="pyramid", sizez=max(zs) - tz0 + 1,
                         flag=(banner_colors[0], patterns))
            tz_list.append(((min(xs) + max(xs)) // 2, (min(zs) + max(zs)) // 2))
            # long banners on the outer face
            fx = (min(xs) if ox < 0 else max(xs)) + ox if ox else None
            fz = (min(zs) if oz < 0 else max(zs)) + oz if oz else None
            mid_u = ((min(zs) + max(zs)) // 2 + 1) if ox else ((min(xs) + max(xs)) // 2 + 1)
            bx = fx if ox else mid_u
            bz = mid_u if ox else fz
            banner(t, bx, tower_h - 3, bz, out, banner_colors[0], 3, trim=banner_colors[1], patterns=patterns)
    # machicolations and merlons over the arch on the outer face
    for u in range(-hw - 1, hw + 2):
        x, z = cx + px * u + ox, cz + pz * u + oz
        t.set(x, height - 1, z, *stairs(s["corbel"], OPPOSITE[out], "top"))
        put(t, x, height, z, s["trim"])
        if u % 2 == 0:
            put(t, x, height + 1, z, s["body"])
            t.set(x, height + 2, z, *slab(s["cap"]))
        else:
            t.set(x, height + 1, z, *slab(s["cap"]))
        x2, z2 = cx + px * u + ix * depth, cz + pz * u + iz * depth
        if u % 2 == 0:
            put(t, x2 - ix, height + 1, z2 - iz, s["body"])
    # lanterns hanging from the vault
    for dpt in (2, depth - 3):
        x, z = cx + ix * dpt, cz + iz * dpt
        if is_air(t, x, tops[hw], z):
            lantern(t, x, tops[hw], z, hanging=True)
    return tops


# ---------------------------------------------------------------------------------------
# Market stalls
# ---------------------------------------------------------------------------------------

GOODS = ["minecraft:melon", "minecraft:pumpkin", "minecraft:hay_block", "minecraft:carved_pumpkin", "minecraft:barrel",
         "minecraft:decorated_pot", "minecraft:flower_pot", "minecraft:potted_red_tulip", "minecraft:cake"]


def market_stall(t, x, z, color="red", facing="south", wood="spruce", stripe="white", goods=None, w=4, d=3):
    """A stall: four posts, a striped sloping canopy, a counter with wares on the open side,
    crates behind. (x, z) is the north-west corner; it opens towards `facing`."""
    rnd = t.rnd
    x1, z1 = x + w - 1, z + d - 1
    goods = goods or GOODS
    for (px, pz) in ((x, z), (x1, z), (x, z1), (x1, z1)):
        for y in (1, 2):
            t.set(px, y, pz, *fence(f"minecraft:{wood}_fence"))
    for xx in range(x, x1 + 1):
        for zz in range(z, z1 + 1):
            front = {"south": zz == z1, "north": zz == z, "east": xx == x1, "west": xx == x}[facing]
            back = {"south": zz == z, "north": zz == z1, "east": xx == x, "west": xx == x1}[facing]
            stripe_i = xx if facing in ("north", "south") else zz
            col = color if stripe_i % 2 == 0 else stripe
            t.set(xx, 4 if back else 3, zz, f"minecraft:{col}_wool")
            if not front and not back:
                t.set(xx, 3, zz, f"minecraft:{col}_wool")
    # counter along the open side, crates at the back
    fx, fz = FACING[facing]
    for xx in range(x, x1 + 1):
        for zz in range(z, z1 + 1):
            front = {"south": zz == z1, "north": zz == z, "east": xx == x1, "west": xx == x}[facing]
            back = {"south": zz == z, "north": zz == z1, "east": xx == x, "west": xx == x1}[facing]
            corner = (xx in (x, x1)) and (zz in (z, z1))
            if corner:
                continue
            if front:
                t.set(xx, 1, zz, *slab(f"minecraft:{wood}_slab", "top"))
                g = rnd.choice(goods)
                if g == "minecraft:barrel":
                    t.set(xx, 2, zz, *barrel("up"))
                elif g == "minecraft:decorated_pot":
                    t.set(xx, 2, zz, "minecraft:decorated_pot", {"facing": facing, "cracked": "false", "waterlogged": "false"})
                elif g == "minecraft:carved_pumpkin":
                    t.set(xx, 2, zz, g, {"facing": facing})
                elif g == "minecraft:hay_block":
                    t.set(xx, 2, zz, g, {"axis": "y"})
                elif g == "minecraft:cake":
                    t.set(xx, 2, zz, g, {"bites": "0"})
                else:
                    t.set(xx, 2, zz, g)
            elif back and rnd.random() < 0.7:
                t.set(xx, 1, zz, *barrel(rnd.choice(["up", OPPOSITE[facing]])))


# ---------------------------------------------------------------------------------------
# Japanese pieces (Rabituza)
# ---------------------------------------------------------------------------------------

def torii(t, cx, cz, axis="z", span=5, height=6, y=0, pillar="minecraft:red_concrete",
          top="minecraft:polished_blackstone", top_stair="minecraft:polished_blackstone_stairs", plaque=None):
    """A torii over a path running along `axis`: two vermilion pillars on black feet, the nuki
    tie beam, and the black kasagi lintel with upswept ends."""
    px, pz = (1, 0) if axis == "z" else (0, 1)
    hw = span // 2 + 1
    for sgn in (-1, 1):
        x, z = cx + px * sgn * hw, cz + pz * sgn * hw
        t.set(x, y + 1, z, top)
        for yy in range(y + 2, y + height + 1):
            t.set(x, yy, z, pillar)
    for u in range(-hw - 1, hw + 2):
        t.set(cx + px * u, y + height - 1, cz + pz * u, pillar)
    t.set(cx, y + height, cz, plaque or top)
    for u in range(-hw - 1, hw + 2):
        t.set(cx + px * u, y + height + 1, cz + pz * u, pillar)
    for u in range(-hw - 2, hw + 3):
        t.set(cx + px * u, y + height + 2, cz + pz * u, top)
    for sgn in (-1, 1):
        u = sgn * (hw + 3)
        f = facing_of(px * sgn, pz * sgn)
        t.set(cx + px * u, y + height + 2, cz + pz * u, *stairs(top_stair, OPPOSITE[f], "top"))
        t.set(cx + px * u, y + height + 3, cz + pz * u, *stairs(top_stair, f))
    reserve(t, cx - px * (hw - 1), cz - pz * (hw - 1), cx + px * (hw - 1), cz + pz * (hw - 1), y)


def stone_lantern(t, x, z, y=1, stone="minecraft:andesite", soul=False):
    """A toro: a stone post holding a lantern under a little stone roof."""
    t.set(x, y, z, "minecraft:polished_andesite")
    t.set(x, y + 1, z, *wall_block(wall_of(stone) or "minecraft:andesite_wall"))
    lantern(t, x, y + 2, z, soul)
    t.set(x, y + 3, z, *slab("minecraft:polished_andesite_slab"))


def pagoda_tier(t, x0, z0, x1, z1, y0, h, s, inner=None, balcony=True, windows=True, doors=(), overhang=2,
                gold="minecraft:gold_block"):
    """One storey of a pagoda: vermilion posts, white panels with lattice windows, a railed
    balcony round it and a flared tile skirt with upturned, gilded corners. `inner` is the
    rectangle of the storey above (the skirt stops at its walls)."""
    post = s.get("post", "minecraft:red_concrete")
    panel = s.get("panel", ["minecraft:white_concrete"])
    for y in range(y0 + 1, y0 + h):
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                edge = x in (x0, x1) or z in (z0, z1)
                if not edge:
                    t.set(x, y, z, AIR)
                    continue
                corner = x in (x0, x1) and z in (z0, z1)
                along = (x - x0) if z in (z0, z1) else (z - z0)
                if corner or along % 3 == 0:
                    t.set(x, y, z, post)
                elif windows and y - y0 in (2, 3) and along % 3 != 0:
                    t.set(x, y, z, *pane(s.get("lattice", "minecraft:red_stained_glass_pane")))
                else:
                    put(t, x, y, z, panel)
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if x in (x0, x1) or z in (z0, z1):
                t.set(x, y0 + h, z, *log(s.get("beam", "minecraft:stripped_dark_oak_log"),
                                         "x" if z in (z0, z1) and x not in (x0, x1) else "z"))
            else:
                t.set(x, y0 + h, z, s.get("floor", "minecraft:dark_oak_planks"))
    for (dx_, dz_, side) in doors:
        door(t, dx_, y0 + 1, dz_, side, s.get("door", "dark_oak"))
    if balcony:
        for x in range(x0 - 1, x1 + 2):
            for z in range(z0 - 1, z1 + 2):
                if x in (x0 - 1, x1 + 1) or z in (z0 - 1, z1 + 1):
                    t.set(x, y0, z, *slab(s.get("deck", "minecraft:dark_oak_slab"), "top"))
        for x in range(x0 - 2, x1 + 3):
            for z in range(z0 - 2, z1 + 3):
                if x in (x0 - 2, x1 + 2) or z in (z0 - 2, z1 + 2):
                    t.set(x, y0, z, *slab(s.get("deck", "minecraft:dark_oak_slab"), "top"))
                    t.set(x, y0 + 1, z, *fence(s.get("rail", "minecraft:dark_oak_fence")))
                    if x in (x0 - 2, x1 + 2) and z in (z0 - 2, z1 + 2):
                        t.set(x, y0 + 1, z, *wall_block("minecraft:red_nether_brick_wall"))
                        lantern(t, x, y0 + 2, z)
    # the skirt roof
    rs = {"roof": s.get("roof", "minecraft:deepslate_tile_stairs"), "roof_full": s.get("roof_full", "minecraft:deepslate_tiles"),
          "ridge": s.get("ridge", "minecraft:deepslate_tile_slab"), "roof_under": s.get("roof_under", "minecraft:dark_oak_stairs")}
    part = hip_part(x0, z0, x1, z1, y0 + h + 1, 1, overhang + (1 if balcony else 0))
    if inner:
        ax0, az0, ax1, az1 = inner

        def clipped(x, z, f=part):
            if ax0 <= x <= ax1 and az0 <= z <= az1:
                return None
            v = f(x, z)
            if v is None:
                return None
            return min(v, y0 + h + 3)
        clipped.bbox, clipped.along = part.bbox, part.along
        field = roof(t, [clipped], y0 + h + 1, rs, upturn=True)
    else:
        field = roof(t, [part], y0 + h + 1, rs, upturn=True)
    X0, Z0, X1, Z1 = part.bbox
    for (cx_, cz_) in ((X0, Z0), (X1, Z0), (X0, Z1), (X1, Z1)):
        if (cx_, cz_) in field:
            t.set(cx_, field[(cx_, cz_)][0] + 2, cz_, gold)
    return field

"""Designs the Frontier: one hand-laid continent with its regions, rivers, valleys, roads and the
plots where the towns and set pieces stand.

The terrain generator reads the result instead of vanilla noise:
  data/slfrontier/worldmap/frontier.bin   height, roughness, crag and biome grids (4-block cells)
  data/slfrontier/worldmap/sites.json     where every town, landmark and dungeon template goes
  data/slfrontier/worldmap/roads.json     road centre lines (painted by the frontier_roads feature)
plus a picture of the map: docs/worldmap.png.

Coordinates are blocks: x east, z south, Firstia at the origin. Needs numpy and Pillow.
"""
import json
import math
import os
import struct
import zlib

import numpy as np
from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.join(os.path.dirname(__file__), "..")
DATA = os.path.join(ROOT, "src/main/resources/data/slfrontier")

X0, Z0, CELL = -1536, -1536, 4
W, H = 1024, 896  # cells: x up to 2560, z up to 2048
SEA = 63  # water fills y < SEA, so the water surface block is y = 62

from frontier_biomes import BIOMES

B = {name: i for i, name in enumerate(BIOMES)}

xs = X0 + np.arange(W) * CELL
zs = Z0 + np.arange(H) * CELL
X, Z = np.meshgrid(xs.astype(np.float64), zs.astype(np.float64))


# ---- Noise ---------------------------------------------------------------------------------

def _perlin(x, z, seed):
    rng = np.random.default_rng(seed)
    perm = rng.permutation(256)
    perm = np.concatenate([perm, perm])
    angles = rng.random(256) * 2 * np.pi
    gx, gz = np.cos(angles), np.sin(angles)
    xi, zi = np.floor(x).astype(np.int64), np.floor(z).astype(np.int64)
    xf, zf = x - xi, z - zi
    xi &= 255
    zi &= 255

    def grad(ix, iz, dx, dz):
        h = perm[perm[ix] + iz]
        return gx[h] * dx + gz[h] * dz

    u = xf * xf * xf * (xf * (xf * 6 - 15) + 10)
    v = zf * zf * zf * (zf * (zf * 6 - 15) + 10)
    n00 = grad(xi, zi, xf, zf)
    n10 = grad(xi + 1, zi, xf - 1, zf)
    n01 = grad(xi, zi + 1, xf, zf - 1)
    n11 = grad(xi + 1, zi + 1, xf - 1, zf - 1)
    a = n00 + u * (n10 - n00)
    b = n01 + u * (n11 - n01)
    return (a + v * (b - a)) * 1.4


def fbm(scale, seed, octaves=4, x=None, z=None):
    x = X if x is None else x
    z = Z if z is None else z
    total, amp, norm = 0.0, 1.0, 0.0
    for o in range(octaves):
        f = (2 ** o) / scale
        total = total + amp * _perlin(x * f + 37.1 * o, z * f - 91.7 * o, seed + o * 101)
        norm += amp
        amp *= 0.5
    return total / norm


def ridged(scale, seed, octaves=4):
    total, amp, norm = 0.0, 1.0, 0.0
    for o in range(octaves):
        f = (2 ** o) / scale
        n = 1 - np.abs(_perlin(X * f + 13.3 * o, Z * f + 71.9 * o, seed + o * 57))
        total = total + amp * n * n
        norm += amp
        amp *= 0.5
    return total / norm


def smoothstep(t):
    t = np.clip(t, 0, 1)
    return t * t * (3 - 2 * t)


# ---- Shapes --------------------------------------------------------------------------------

WARP_X = fbm(560, 11, 3) * 150 + fbm(150, 13, 3) * 40
WARP_Z = fbm(560, 12, 3) * 150 + fbm(150, 14, 3) * 40


def polygon_sd(points, warp=True):
    """Signed distance to a polygon (positive inside), on warped coordinates for natural edges."""
    px = X + WARP_X if warp else X
    pz = Z + WARP_Z if warp else Z
    pts = np.array(points, dtype=np.float64)
    dist = np.full(X.shape, np.inf)
    inside = np.zeros(X.shape, dtype=bool)
    n = len(pts)
    for i in range(n):
        ax, az = pts[i]
        bx, bz = pts[(i + 1) % n]
        dx, dz = bx - ax, bz - az
        t = np.clip(((px - ax) * dx + (pz - az) * dz) / (dx * dx + dz * dz), 0, 1)
        d = np.hypot(px - (ax + t * dx), pz - (az + t * dz))
        dist = np.minimum(dist, d)
        crosses = ((az > pz) != (bz > pz)) & (px < (bx - ax) * (pz - az) / (bz - az + 1e-9) + ax)
        inside ^= crosses
    return np.where(inside, dist, -dist)


def circle_sd(cx, cz, r, wobble=0.0, seed=0):
    d = r - np.hypot(X - cx, Z - cz)
    if wobble:
        d = d + fbm(140, seed, 3) * wobble
    return d


def spline(points, step=6.0):
    """Catmull-Rom through the control points, resampled every `step` blocks."""
    pts = [points[0]] + list(points) + [points[-1]]
    out = []
    for i in range(1, len(pts) - 2):
        p0, p1, p2, p3 = (np.array(p, dtype=np.float64) for p in pts[i - 1:i + 3])
        seg = max(2, int(np.hypot(*(p2 - p1)) / step))
        for k in range(seg):
            t = k / seg
            out.append(0.5 * ((2 * p1) + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t * t
                              + (-p0 + 3 * p1 - 3 * p2 + p3) * t * t * t))
    out.append(np.array(points[-1], dtype=np.float64))
    return np.array(out)


def polyline_distance(line, values=None):
    """Distance from every cell to a polyline, plus a per-vertex value interpolated along it."""
    dist = np.full(X.shape, 1e6)
    val = np.zeros(X.shape)
    for i in range(len(line) - 1):
        ax, az = line[i]
        bx, bz = line[i + 1]
        dx, dz = bx - ax, bz - az
        ln = dx * dx + dz * dz
        if ln < 1e-9:
            continue
        # Only cells near this segment matter; keep it cheap with a bounding window.
        pad = 260
        x0 = int(max(0, (min(ax, bx) - pad - X0) // CELL))
        x1 = int(min(W, (max(ax, bx) + pad - X0) // CELL + 1))
        z0 = int(max(0, (min(az, bz) - pad - Z0) // CELL))
        z1 = int(min(H, (max(az, bz) + pad - Z0) // CELL + 1))
        if x0 >= x1 or z0 >= z1:
            continue
        sx, sz = X[z0:z1, x0:x1], Z[z0:z1, x0:x1]
        t = np.clip(((sx - ax) * dx + (sz - az) * dz) / ln, 0, 1)
        d = np.hypot(sx - (ax + t * dx), sz - (az + t * dz))
        win = dist[z0:z1, x0:x1]
        closer = d < win
        win[closer] = d[closer]
        if values is not None:
            v = values[i] + t * (values[i + 1] - values[i])
            val[z0:z1, x0:x1][closer] = v[closer]
    return dist, val


def resample(values, control, line):
    """Spreads per-control-point values over a resampled spline (by arc length)."""
    ctrl = np.array(control, dtype=np.float64)
    cl = np.concatenate([[0], np.cumsum(np.hypot(*np.diff(ctrl, axis=0).T))])
    ll = np.concatenate([[0], np.cumsum(np.hypot(*np.diff(line, axis=0).T))])
    return np.interp(ll / ll[-1] * cl[-1], cl, values)


# ---- The continent -------------------------------------------------------------------------

CONTINENT = [(-1180, -1290), (-760, -1350), (-300, -1400), (180, -1360), (700, -1430), (1150, -1390), (1700, -1400),
             (2100, -1330), (2360, -1180), (2440, -900), (2380, -620), (2470, -380), (2420, -100), (2500, 180),
             (2460, 470), (2340, 760), (2180, 880), (1950, 960), (1760, 900), (1520, 940), (1330, 1060), (1100, 1010),
             (880, 1080), (700, 1120), (520, 1030), (300, 980), (120, 1060), (-120, 1100), (-260, 960), (-430, 940),
             (-560, 1060), (-820, 1030), (-1080, 940), (-1250, 760), (-1200, 560), (-1330, 380), (-1380, 100),
             (-1290, -160), (-1350, -420), (-1260, -700), (-1360, -980)]
RABITUZA_ISLE = (1650, 1500, 250)

REGIONS = {
    "frontier_peaks": [(-1400, -1550), (2550, -1550), (2550, -830), (1250, -830), (1150, -1080), (420, -1080),
                       (300, -910), (-460, -910), (-1400, -910)],
    "prismatic_forest_grotto": [(-1400, -910), (-460, -910), (-430, -380), (-460, -150), (-1400, -120)],
    "fastera_plains": [(-1400, -120), (-460, -150), (-430, -380), (320, -330), (350, 200), (420, 600), (380, 1150),
                       (-380, 1150), (-420, 330), (-1400, 330)],
    "lightless_barrens": [(-1400, 330), (-420, 330), (-380, 1150), (-1400, 1150)],
    "rampant_forest": [(320, -430), (1220, -430), (1300, -150), (1450, 100), (880, 130), (820, 520), (700, 1150),
                       (380, 1150), (420, 600), (350, 200), (320, -330)],
    "nightslayer_woods": [(300, -430), (1220, -430), (1250, -830), (1150, -1080), (420, -1080), (300, -910)],
    "diremarsh_waste": [(880, 130), (1450, 100), (1600, 450), (1550, 1150), (1050, 1150), (700, 1150), (820, 520)],
    "thirdrema_highlands": [(1250, -830), (2550, -830), (2550, 1150), (1650, 1150), (1600, 450), (1450, 100),
                            (1300, -150), (1220, -430)],
}

# Base height and how much the land rolls, per region (blocks).
def region_heights():
    return {
        "frontier_peaks": 138 + 50 * ridged(360, 21) + 18 * fbm(150, 22),
        "prismatic_forest_grotto": 75 + 9 * fbm(220, 23) + 4 * fbm(70, 24),
        "fastera_plains": 68 + 3 * fbm(420, 25) + 2 * fbm(130, 26),
        "lightless_barrens": 69 + 9 * ridged(240, 27) + 3 * fbm(60, 28),
        "rampant_forest": 73 + 7 * fbm(300, 29) + 4 * fbm(90, 30),
        "nightslayer_woods": 78 + 10 * fbm(260, 31) + 5 * fbm(80, 32),
        "diremarsh_waste": 63.4 + 1.6 * fbm(140, 33) + 1.3 * fbm(40, 34),
        "thirdrema_highlands": 104 + 24 * fbm(400, 35) + 20 * ridged(220, 36),
    }


ROUGH = {"frontier_peaks": 10, "prismatic_forest_grotto": 4, "fastera_plains": 1.5, "lightless_barrens": 5,
         "rampant_forest": 3, "nightslayer_woods": 4, "diremarsh_waste": 0.6, "thirdrema_highlands": 7}
CRAG = {"frontier_peaks": 0.9, "prismatic_forest_grotto": 0.25, "lightless_barrens": 0.45, "thirdrema_highlands": 0.4}

# Rivers run at sea level: (control points, widths, bank steepness per point)
SERPENT_X = 560
RIVERS = [
    ([(560, -1180), (600, -1000), (545, -820), (590, -640), (540, -450), (575, -260), (560, -150), (SERPENT_X, -64),
      (SERPENT_X, 4), (562, 90), (575, 180), (540, 330), (585, 520), (560, 720), (600, 900), (610, 1120)],
     [6, 8, 10, 12, 16, 22, 30, 35, 35, 32, 28, 28, 30, 34, 38, 46],
     [1.4, 1.4, 1.2, 0.9, 0.6, 0.6, 1.5, 3.5, 3.5, 1.5, 0.6, 0.45, 0.4, 0.35, 0.3, 0.3]),
    ([(-470, -560), (-390, -400), (-300, -250), (-250, -60), (-300, 150), (-230, 380), (-270, 620), (-220, 850),
      (-300, 1140)],
     [6, 7, 8, 9, 10, 11, 12, 14, 18], [0.6, 0.5, 0.45, 0.4, 0.4, 0.4, 0.4, 0.35, 0.3]),
    ([(1500, -250), (1420, -50), (1330, 150), (1180, 330), (1080, 560), (1010, 800), (1050, 1140)],
     [8, 10, 12, 14, 18, 22, 28], [0.9, 0.7, 0.5, 0.4, 0.35, 0.3, 0.3]),
]

# Valleys: (control points, floor height, half width, wall steepness)
VALLEYS = [
    ([(1850, -420), (1850, -120), (1850, 140), (1830, 330), (1760, 470), (1680, 520)], 73.0, 66, 1.5),  # Thirdrema
    ([(1180, -760), (1300, -860), (1430, -960), (1520, -1030)], 95.0, 16, 1.1),  # pass to the Hidden Garden
]
GARDEN = (1560, -1050, 150, 97.0)  # x, z, radius, floor
LAKES = [(-250, -330, 70, 57.0), (130, 380, 55, 58.0), (840, -880, 60, 58.0), (-900, 120, 80, 57.0),
]  # x, z, radius, bed

# Sites: template, size (x, z), centre, how the template meets the ground, biome painted under it.
#   ground "layer0": template y=0 replaces the top terrain block (towns)
#   ground "on":     template y=0 is the first air layer (dungeons)
#   ground int:      the template layer that lines up with the top terrain block (the bridge)
SITES = [
    dict(id="fastera", template="town/firstia", size=(112, 112), center=(0, 0), ground="layer0", biome="firstia",
         town=True),
    dict(id="goblin_camp", template="landmark/goblin_camp", size=(48, 48), center=(290, -210), ground="layer0",
         biome="goblin_camp"),
    dict(id="serpent_bridge", template="landmark/serpent_bridge", size=(84, 56), origin=(518, -58), ground=16,
         top=73, biome="serpent_bridge"),
    dict(id="secondil", template="town/secondil", size=(128, 128), center=(1000, -30), ground="layer0",
         biome="secondil", town=True),
    dict(id="thirdrema", template="town/thirdrema", size=(112, 160), center=(1850, 40), ground="layer0", top=72,
         biome="thirdrema", town=True),
    dict(id="rabituza", template="town/rabituza", size=(128, 128), center=(1650, 1500), ground="layer0",
         biome="rabituza", town=True),
    dict(id="nightslayer_den", template="nightslayer_den", size=(27, 27), center=(760, -720), ground="on",
         biome="nightslayer_den"),
    dict(id="wezaemon_tomb", template="wezaemon_tomb", size=(31, 31), center=(1560, -1060), ground="on", top=96,
         biome="wezaemon_tomb"),
    dict(id="empire_bee_hive", template="empire_bee_hive", size=(23, 23), center=(-820, -460), ground="on",
         biome="empire_bee_hive"),
    dict(id="mud_digger_lair", template="mud_digger_lair", size=(25, 25), center=(1290, 360), ground="on", top=63,
         biome="mud_digger_lair"),
    dict(id="shadow_altar", template="shadow_altar", size=(41, 41), center=(-760, 640), ground="on",
         biome="shadow_altar"),
    dict(id="divinity_ruins", template="divinity_ruins", size=(21, 21), center=(2120, -520), ground="on",
         biome="divinity_ruins"),
]
SITE_MARGIN = 26  # blocks of blending around a plot

# Roads: (style, width, control points). Gates: Firstia W/E at z=0, Secondil W/E at z=-30,
# Thirdrema's south gate at (1850, 119), the camp's entrance on its west side.
ROADS = [
    ("main", 5, [(56, 0), (150, -10), (260, -36), (420, -28), (518, -30)]),
    ("main", 5, [(601, -30), (700, -42), (820, -24), (936, -30)]),
    ("main", 5, [(1063, -30), (1150, 18), (1235, 150), (1310, 262), (1400, 300), (1520, 330), (1650, 400),
                 (1760, 470), (1822, 330), (1850, 190), (1850, 119)]),
    ("trail", 3, [(240, -34), (232, -90), (238, -160), (266, -210)]),
    ("trail", 3, [(-56, 0), (-180, -28), (-262, -64), (-420, -200), (-620, -330), (-760, -430), (-808, -446)]),
    ("trail", 3, [(-160, -24), (-205, 120), (-290, 300), (-420, 470), (-600, 580), (-738, 626)]),
    ("trail", 3, [(820, -24), (812, -150), (770, -400), (748, -600), (760, -704)]),
    ("trail", 3, [(1320, 270), (1300, 330), (1290, 345)]),
]


def site_rect(s):
    sx, sz = s["size"]
    if "origin" in s:
        return s["origin"][0], s["origin"][1], sx, sz
    cx, cz = s["center"]
    return cx - sx // 2, cz - sz // 2, sx, sz


def rect_distance(x0, z0, sx, sz):
    dx = np.maximum(0, np.maximum(x0 - X, X - (x0 + sx - 1)))
    dz = np.maximum(0, np.maximum(z0 - Z, Z - (z0 + sz - 1)))
    return np.hypot(dx, dz)


# ---- Building the grids --------------------------------------------------------------------

def build():
    land = np.maximum(polygon_sd(CONTINENT), circle_sd(*RABITUZA_ISLE, wobble=45, seed=40))

    # Regions: the biome goes to whichever region the cell is deepest inside; the height is a soft
    # blend so borders roll instead of stepping.
    sds = {name: polygon_sd(pts) for name, pts in REGIONS.items()}
    heights = region_heights()
    names = list(REGIONS)
    stack = np.stack([sds[n] for n in names])
    # Each region pushes its border around with its own noise, so neighbours interlock.
    jitter = np.stack([fbm(130, 60 + i, 3) * 45 + fbm(45, 80 + i, 2) * 12 for i in range(len(names))])
    owner = np.argmax(stack + jitter, axis=0)
    weights = np.exp(np.clip(stack - stack.max(axis=0), -900, 0) / 45.0)
    weights /= weights.sum(axis=0)
    h = sum(weights[i] * heights[n] for i, n in enumerate(names))
    rough = sum(weights[i] * ROUGH.get(n, 2) for i, n in enumerate(names))
    crag = sum(weights[i] * CRAG.get(n, 0) for i, n in enumerate(names))
    biome = np.array([B[n] for n in names])[owner]

    # The Hidden Garden: a high meadow ringed by the peaks.
    gx, gz, gr, gfloor = GARDEN
    gd = np.hypot(X - gx, Z - gz)
    garden = smoothstep((gd - gr) / 70.0)
    h = np.where(gd < gr + 220, np.minimum(h, gfloor + 1.6 * fbm(90, 41) + np.maximum(0, gd - gr) * 0.9), h)
    rough = np.where(gd < gr, rough * garden + 1.0 * (1 - garden), rough)
    crag = crag * np.where(gd < gr + 40, garden, 1)
    biome = np.where(gd < gr + fbm(60, 42) * 18, B["hidden_garden"], biome)

    # Lakes (water fills to sea level; a raised tarn gets a basin at its own height).
    for lx, lz, lr, bed in LAKES:
        d = np.hypot(X - lx, Z - lz) + fbm(70, int(lx) & 255, 3) * 22
        if bed < SEA:
            h = np.where(d < lr, np.minimum(h, bed + (62.4 - bed) * (d / lr) ** 2),
                         np.minimum(h, 62.6 + np.maximum(0, d - lr) * 0.25))
        else:
            h = np.where(d < lr, np.minimum(h, bed), h)
        rough = rough * smoothstep((d - lr) / 30)
        crag = crag * smoothstep((d - lr) / 30)

    # Valleys carve the land down to a floor with steep walls.
    for pts, floor, half, steep in VALLEYS:
        d, _ = polyline_distance(spline(pts, 8))
        wall = np.maximum(0, d - half)
        h = np.minimum(h, floor + wall * steep + 2.5 * fbm(80, 43) * smoothstep(wall / 12))
        near = smoothstep(wall / 30)
        rough = rough * (0.3 + 0.7 * near)
        crag = crag * near

    # Coasts: beaches where the land meets the sea, a shelf, then deep water.
    coast = smoothstep(land / 90.0)
    h = np.where(land > 0, 64.2 + (h - 64.2) * coast, h)
    sea_floor = 62.0 - np.clip(-land, 0, 400) * 0.07 - 3 + 2 * fbm(160, 44)
    h = np.where(land > 0, h, np.maximum(np.minimum(sea_floor, 61), 30))
    rough = np.where(land > 0, rough * (0.35 + 0.65 * coast), 1.5)
    crag = np.where(land > 0, crag * coast, 0)
    biome = np.where(land > 0, biome, B["frontier_sea"])
    shore = (land > -6) & (land < 34) & (h < 67.5)
    biome = np.where(shore, B["frontier_shore"], biome)

    # Rabituza's island belongs to the rabbits.
    rx, rz, rr = RABITUZA_ISLE
    isle = np.hypot(X - rx, Z - rz) < rr + 80
    h = np.where(isle & (land > 0), 64.2 + (68 + 4 * fbm(120, 45) - 64.2) * coast, h)
    biome = np.where(isle & (land > 0) & ~shore, B["rabbit_isle"], biome)

    # Roads: smooth the ground along them and keep it gentle.
    road_lines = []
    road_d = np.full(X.shape, 1e6)
    for style, width, pts in ROADS:
        line = spline(pts, 6)
        road_lines.append((style, width, line))
        d, _ = polyline_distance(line)
        road_d = np.minimum(road_d, d)
    blur = h.copy()
    for _ in range(4):
        blur = (blur + np.roll(blur, 1, 0) + np.roll(blur, -1, 0) + np.roll(blur, 1, 1) + np.roll(blur, -1, 1)) / 5
    near_road = 1 - smoothstep((road_d - 4) / 14)
    h = h + (blur - h) * near_road * 0.85
    rough = rough * (1 - 0.85 * near_road)
    crag = crag * (1 - near_road)

    # Rivers last, so they cut through everything (the roads bridge them).
    river_any = np.zeros(X.shape, dtype=bool)
    for pts, widths, steep in RIVERS:
        line = spline(pts, 5)
        wv = resample(widths, pts, line)
        sv = resample(steep, pts, line)
        d, wi = polyline_distance(line, wv)
        _, st = polyline_distance(line, sv)
        half = wi / 2
        inside = d < half
        depth = np.minimum(6, wi / 4)
        bed = 62.4 - depth * (1 - (d / np.maximum(half, 1)) ** 2)
        bank = np.where(d < 1e5, 62.6 + np.maximum(0, d - half) * st, np.inf)
        h = np.where(inside, np.minimum(h, bed), np.minimum(h, bank))
        close = smoothstep((d - half) / 18)
        rough = rough * close
        crag = crag * close
        river_any |= inside

    # Plots: every site sits on flat ground at a fixed height.
    sites_out = []
    for s in SITES:
        x0, z0, sx, sz = site_rect(s)
        d = rect_distance(x0, z0, sx, sz)
        if "top" in s:
            top = s["top"]
        else:
            mask = d == 0
            top = int(round(float(np.median(h[mask])))) - 1
            top = max(top, 64)
        plot = top + 1.0
        blend = smoothstep(d / SITE_MARGIN)
        keep_river = river_any & (d > 0) if s["id"] == "serpent_bridge" else np.zeros(X.shape, dtype=bool)
        h = np.where(keep_river, h, plot + (h - plot) * blend)
        rough = rough * blend
        crag = crag * blend
        under = (d <= 6) & ~(river_any & (s["id"] == "serpent_bridge"))
        biome = np.where(under, B[s["biome"]], biome)
        if s["ground"] == "layer0":
            oy = top
        elif s["ground"] == "on":
            oy = top + 1
        else:
            oy = top - s["ground"]
        sites_out.append({"id": s["id"], "template": "slfrontier:" + s["template"], "x": x0, "y": oy, "z": z0,
                          "size_x": sx, "size_z": sz, "top": top, "town": bool(s.get("town"))})

    rough = np.clip(rough, 0, 30)
    crag = np.clip(crag, 0, 1)
    return h, rough, crag, biome, sites_out, road_lines


# ---- Output --------------------------------------------------------------------------------

def write_bin(path, h, rough, crag, biome):
    payload = b"".join([
        np.clip(np.round(h * 16), 0, 32767).astype(">i2").tobytes(),
        np.clip(np.round(rough * 8), 0, 255).astype(np.uint8).tobytes(),
        np.clip(np.round(crag * 255), 0, 255).astype(np.uint8).tobytes(),
        biome.astype(np.uint8).tobytes(),
    ])
    packed = zlib.compress(payload, 9)
    with open(path, "wb") as f:
        f.write(b"SLFW")
        f.write(struct.pack(">iiiiiii", 1, X0, Z0, CELL, W, H, len(BIOMES)))
        for name in BIOMES:
            enc = name.encode()
            f.write(struct.pack(">H", len(enc)) + enc)
        f.write(struct.pack(">i", len(packed)))
        f.write(packed)


COLORS = {"frontier_sea": (52, 92, 160), "frontier_shore": (222, 208, 160), "fastera_plains": (132, 186, 92),
          "rampant_forest": (62, 132, 56), "nightslayer_woods": (52, 62, 74), "diremarsh_waste": (104, 112, 70),
          "prismatic_forest_grotto": (126, 160, 210), "lightless_barrens": (78, 72, 88),
          "thirdrema_highlands": (148, 150, 104), "hidden_garden": (226, 160, 184), "frontier_peaks": (180, 180, 186),
          "goblin_camp": (150, 110, 70), "serpent_bridge": (170, 170, 170), "rabbit_isle": (236, 176, 196)}
TOWN_COLOR = (238, 214, 120)
DUNGEON_COLOR = (200, 80, 90)


def render(path, h, biome, sites, road_lines, scale=1):
    base = np.zeros((H, W, 3))
    for name, i in B.items():
        c = COLORS.get(name, TOWN_COLOR if name in ("firstia", "secondil", "thirdrema", "rabituza") else DUNGEON_COLOR)
        base[biome == i] = c
    water = h < SEA - 0.5
    deep = np.clip((SEA - h) / 30, 0, 1)[..., None]
    base = np.where(water[..., None], np.array([60, 110, 190]) * (1 - deep) + np.array([24, 52, 110]) * deep, base)
    gz, gx = np.gradient(h)
    shade = np.clip(1 + (gx - gz) * 0.12, 0.55, 1.35)[..., None]
    alt = np.clip((h - 64) / 140, 0, 1)[..., None]
    img = np.where(water[..., None], base, base * shade * (0.9 + 0.25 * alt))
    im = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8))
    if scale != 1:
        im = im.resize((W * scale, H * scale), Image.NEAREST)
    draw = ImageDraw.Draw(im)
    to_px = lambda x, z: ((x - X0) / CELL * scale, (z - Z0) / CELL * scale)
    for style, width, line in road_lines:
        pts = [to_px(x, z) for x, z in line]
        draw.line(pts, fill=(236, 214, 160) if style == "main" else (196, 170, 120), width=2 if style == "main" else 1)
    font = ImageFont.load_default()
    for s in sites:
        x0, y0 = to_px(s["x"], s["z"])
        x1, y1 = to_px(s["x"] + s["size_x"], s["z"] + s["size_z"])
        draw.rectangle([x0, y0, x1, y1], outline=(255, 255, 255) if s["town"] else (255, 120, 120))
        draw.text((x1 + 3, y0), LABELS.get(s["id"], s["id"].replace("_", " ").title()), fill=(255, 255, 255), font=font)
    for name, label in REGION_LABELS.items():
        if name in B and (biome == B[name]).any():
            zs, xs = np.nonzero(biome == B[name])
            x, y = float(np.median(xs)) * scale, float(np.median(zs)) * scale
            w = draw.textlength(label, font=font)
            draw.text((x - w / 2, y + 14), label, fill=(255, 244, 200), font=font, stroke_width=1, stroke_fill=(40, 34, 24))
    im.save(path)


LABELS = {"fastera": "Firstia", "wezaemon_tomb": "Wezaemon's Tomb", "nightslayer_den": "Nightslayer's Den",
          "mud_digger_lair": "Mud Digger's Lair", "goblin_camp": "Goblin War Camp"}
REGION_LABELS = {"fastera_plains": "Fastera Plains", "rampant_forest": "Rampant Forest",
                 "nightslayer_woods": "Nightslayer Woods", "diremarsh_waste": "Diremarsh Waste",
                 "prismatic_forest_grotto": "Prismatic Forest Grotto", "lightless_barrens": "Lightless Barrens",
                 "thirdrema_highlands": "Thirdrema Highlands", "frontier_peaks": "Northern Peaks",
                 "hidden_garden": "Hidden Garden"}


def main():
    h, rough, crag, biome, sites, road_lines = build()
    out = os.path.join(DATA, "worldmap")
    os.makedirs(out, exist_ok=True)
    write_bin(os.path.join(out, "frontier.bin"), h, rough, crag, biome)
    with open(os.path.join(out, "sites.json"), "w") as f:
        json.dump(sites, f, indent=1)
    with open(os.path.join(out, "roads.json"), "w") as f:
        json.dump([{"style": style, "width": width, "points": [[round(float(x), 1), round(float(z), 1)] for x, z in line]}
                   for style, width, line in road_lines], f)
    os.makedirs(os.path.join(ROOT, "docs"), exist_ok=True)
    render(os.path.join(ROOT, "docs/worldmap.png"), h, biome, sites, road_lines)
    land = h >= SEA
    print(f"land {land.mean():.0%}, height {h.min():.0f}..{h.max():.0f}, sites {len(sites)}")


if __name__ == "__main__":
    main()

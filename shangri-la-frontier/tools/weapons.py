"""3D item models for the Frontier's weapons (and a few other held items).

  python3 tools/gen_assets.py    (calls build_all() from here)

Every weapon is modelled standing upright in a 16-unit item space: the grip sits on
the vertical axis x = z = 8 a little below the centre, the blade points up (+Y).
Display transforms are vanilla's ``item/handheld`` ones with an extra -45 degree roll
applied first, so the upright model is held exactly the way vanilla holds its
diagonal sword sprites.

Geometry is built from:
  * ``extrude``: a 2D silhouette (polygon / circle / custom test) sliced into thin rows
    and extruded in thickness layers.  Several layers of shrinking thickness give a
    blade a bevelled edge and a raised ridge without any rotated elements, so curved
    blades (katanas, crescent moonblades) are possible despite Minecraft's 22.5 degree
    rotation limit.
  * ``box`` / ``prism`` for guards, grips and pommels.

Textures are painted from model-space positions, so gradients, edge highlights and
patterns run continuously across all the little elements a silhouette is made of.
"""
import hashlib
import json
import math
import os
import random

from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), "..")
ASSETS = os.path.join(ROOT, "src/main/resources/assets/slfrontier")
NS = "slfrontier"

DENSITY = 4  # texels per model unit
ROW = 0.25   # silhouette slicing resolution (model units)


def hex_rgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))


def scale(c, f):
    return tuple(c[i] * f for i in range(3))


def clamp_rgb(c):
    return tuple(max(0, min(255, int(round(v)))) for v in c)


def hash_noise(*args):
    """Deterministic value noise in [-1, 1] for integer lattice coordinates."""
    h = hashlib.md5(repr(args).encode()).digest()
    return (h[0] / 255.0) * 2 - 1


def smooth_noise(x, y, seed=0, freq=1.0):
    """Bilinear value noise, continuous in x/y."""
    x *= freq
    y *= freq
    x0, y0 = math.floor(x), math.floor(y)
    fx, fy = x - x0, y - y0
    fx, fy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
    n00 = hash_noise(seed, x0, y0)
    n10 = hash_noise(seed, x0 + 1, y0)
    n01 = hash_noise(seed, x0, y0 + 1)
    n11 = hash_noise(seed, x0 + 1, y0 + 1)
    return (n00 * (1 - fx) + n10 * fx) * (1 - fy) + (n01 * (1 - fx) + n11 * fx) * fy


# ---------------------------------------------------------------------------------------
# 2D shapes
# ---------------------------------------------------------------------------------------

class Shape:
    """A 2D region with an inside test and a (sampled) distance-to-edge function."""

    def __init__(self, inside, bounds):
        self.inside = inside
        self.bounds = bounds  # (x0, y0, x1, y1)
        self._dist = {}

    def edge_distance(self, x, y, step=0.125, max_r=3.0):
        """Approximate distance from (x, y) to the outside of the shape."""
        key = (round(x / step), round(y / step))
        if key in self._dist:
            return self._dist[key]
        cx, cy = key[0] * step, key[1] * step
        if not self.inside(cx, cy):
            self._dist[key] = 0.0
            return 0.0
        best = max_r
        r = step
        while r < best:
            for i in range(16):
                a = math.pi * 2 * i / 16
                if not self.inside(cx + math.cos(a) * r, cy + math.sin(a) * r):
                    best = r
                    break
            r += step
        self._dist[key] = best
        return best

    def offset(self, d):
        """The shape shrunk by d (only points at least d from the edge)."""
        return Shape(lambda x, y: self.inside(x, y) and self.edge_distance(x, y) >= d - 1e-6, self.bounds)

    def __or__(self, other):
        b = (min(self.bounds[0], other.bounds[0]), min(self.bounds[1], other.bounds[1]),
             max(self.bounds[2], other.bounds[2]), max(self.bounds[3], other.bounds[3]))
        return Shape(lambda x, y: self.inside(x, y) or other.inside(x, y), b)

    def __sub__(self, other):
        return Shape(lambda x, y: self.inside(x, y) and not other.inside(x, y), self.bounds)

    def __and__(self, other):
        return Shape(lambda x, y: self.inside(x, y) and other.inside(x, y), self.bounds)


def polygon(points):
    xs = [p[0] for p in points]
    ys = [p[1] for p in points]

    def inside(x, y):
        c = False
        n = len(points)
        j = n - 1
        for i in range(n):
            xi, yi = points[i]
            xj, yj = points[j]
            if (yi > y) != (yj > y) and x < (xj - xi) * (y - yi) / (yj - yi + 1e-12) + xi:
                c = not c
            j = i
        return c

    return Shape(inside, (min(xs), min(ys), max(xs), max(ys)))


def circle(cx, cy, r):
    return Shape(lambda x, y: (x - cx) ** 2 + (y - cy) ** 2 <= r * r, (cx - r, cy - r, cx + r, cy + r))


def ellipse(cx, cy, rx, ry):
    return Shape(lambda x, y: ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 <= 1, (cx - rx, cy - ry, cx + rx, cy + ry))


def rect(x0, y0, x1, y1):
    return Shape(lambda x, y: x0 <= x <= x1 and y0 <= y <= y1, (x0, y0, x1, y1))


def curved_blade(y0, y1, width_at, center_at, tip=None):
    """A blade whose centreline is x = center_at(t) and width width_at(t), t in 0..1 along y."""
    def inside(x, y):
        if y < y0 or y > y1:
            return False
        t = (y - y0) / (y1 - y0)
        w = width_at(t)
        return abs(x - center_at(t)) <= w / 2
    # Generous bounds; slicing only keeps covered rows.
    xs = [center_at(t / 20) for t in range(21)]
    ws = max(width_at(t / 20) for t in range(21))
    return Shape(inside, (min(xs) - ws, y0, max(xs) + ws, y1))


# ---------------------------------------------------------------------------------------
# Model builder
# ---------------------------------------------------------------------------------------

class Model:
    def __init__(self, name):
        self.name = name
        self.elements = []  # dicts with from, to, mat, glow, shape
        self.extra = {}

    def box(self, frm, to, mat, glow=False, shape=None, rot=None):
        frm = tuple(float(v) for v in frm)
        to = tuple(float(v) for v in to)
        if any(to[i] - frm[i] <= 1e-6 for i in range(3)):
            return
        self.elements.append({"from": frm, "to": to, "mat": mat, "glow": glow, "shape": shape, "rot": rot})

    def cbox(self, w, y0, y1, d, mat, cx=8.0, cz=8.0, **kw):
        """Box centred on the item axis."""
        self.box((cx - w / 2, y0, cz - d / 2), (cx + w / 2, y1, cz + d / 2), mat, **kw)

    def prism(self, r, y0, y1, mat, cx=8.0, cz=8.0, **kw):
        """An octagonal prism around the vertical axis (a box plus a narrower cross)."""
        a = r * 0.77
        self.box((cx - a, y0, cz - r), (cx + a, y1, cz + r), mat, **kw)
        self.box((cx - r, y0, cz - a), (cx + r, y1, cz + a), mat, **kw)

    def extrude(self, shape, z_half, mat, cz=8.0, glow=False, plane="xy", y_center=None, row=ROW, paint_shape=None):
        """Slice ``shape`` into rows and extrude each run +-z_half around cz.

        plane "xy": shape coordinates are (x, y), extruded along z (blades).
        plane "xz": shape coordinates are (x, z), extruded along y around y_center (guards, discs).
        """
        x0, y0, x1, y1 = shape.bounds
        ys = math.floor(y0 / row) * row
        y = ys
        rows = []
        while y < y1 - 1e-9:
            yc = y + row / 2
            runs = []
            start = None
            x = math.floor(x0 / row) * row
            while x <= x1 + row:
                inside = shape.inside(x + row / 2, yc)
                if inside and start is None:
                    start = x
                if not inside and start is not None:
                    runs.append((round(start, 4), round(x, 4)))
                    start = None
                x += row
            if start is not None:
                runs.append((round(start, 4), round(x, 4)))
            rows.append((y, runs))
            y += row
        # Merge identical runs in consecutive rows into taller boxes.
        open_runs = {}
        spans = []
        for (y, runs) in rows:
            nxt = {}
            for r in runs:
                if r in open_runs:
                    nxt[r] = open_runs[r]
                else:
                    nxt[r] = y
            for r, ystart in open_runs.items():
                if r not in nxt:
                    spans.append((r, ystart, y))
            open_runs = nxt
        end = rows[-1][0] + row if rows else 0
        for r, ystart in open_runs.items():
            spans.append((r, ystart, end))
        ps = paint_shape or shape
        for ((a, b), ya, yb) in spans:
            if plane == "xy":
                self.box((a, ya, cz - z_half), (b, yb, cz + z_half), mat, glow, ps)
            else:
                self.box((a, y_center - z_half, ya), (b, y_center + z_half, yb), mat, glow, ps)

    def bevelled(self, shape, layers, mat, cz=8.0, glow=False, edge_mat=None):
        """Extrude a blade in layers: [(inset, half_thickness), ...] from the edge inward."""
        for i, (inset, half) in enumerate(layers):
            s = shape if inset <= 0 else shape.offset(inset)
            m = edge_mat if (i == 0 and edge_mat) else mat
            self.extrude(s, half, m, cz=cz, glow=glow, paint_shape=shape)


# ---------------------------------------------------------------------------------------
# Materials: functions of model-space position and face normal
# ---------------------------------------------------------------------------------------

class Ctx:
    def __init__(self, el, face, seed):
        self.el = el
        self.face = face
        self.seed = seed


def steel(base, light=None, dark=None, edge="#FFFFFF", streak=0.06, ridge=True, warm=None):
    base = hex_rgb(base)
    light = hex_rgb(light) if light else mix(base, (255, 255, 255), 0.45)
    dark = hex_rgb(dark) if dark else scale(base, 0.62)
    edge = hex_rgb(edge)

    def paint(p, n, ctx):
        x, y, z = p
        shape = ctx.el["shape"]
        d = shape.edge_distance(x, y) if shape else 1.0
        # Bevel shading: bright cutting edge, a darker band, then the flat with a ridge.
        if d < 0.3:
            c = mix(edge, light, d / 0.3)
        elif d < 0.65:
            c = mix(dark, base, (d - 0.3) / 0.35)
        else:
            c = mix(base, light, min(1.0, (d - 0.65) / 1.6) * (0.55 if ridge else 0.2))
        # Brushed streaks along the blade and a soft shimmer.
        s = smooth_noise(x * 6, y * 0.7, ctx.seed) * streak + smooth_noise(x, y * 0.25, ctx.seed + 9) * streak * 0.6
        c = scale(c, 1 + s)
        if warm:
            c = mix(c, hex_rgb(warm), 0.12 + 0.1 * smooth_noise(x, y * 0.4, ctx.seed + 3))
        if n[1] != 0:  # top/bottom of a slice: the polished edge
            c = mix(c, edge, 0.35)
        return c

    return paint


def flat(color, noise=0.05, top=None):
    color = hex_rgb(color)
    top = hex_rgb(top) if top else None

    def paint(p, n, ctx):
        x, y, z = p
        c = scale(color, 1 + smooth_noise(x * 3, y * 3 + z * 2, ctx.seed) * noise)
        if top and n[1] > 0:
            c = mix(c, top, 0.6)
        return c

    return paint


def metal(color, highlight=None, noise=0.05):
    """Polished fittings (guards, pommels): bright top, darker underside, specular streak."""
    color = hex_rgb(color)
    hi = hex_rgb(highlight) if highlight else mix(color, (255, 255, 255), 0.5)

    def paint(p, n, ctx):
        x, y, z = p
        el = ctx.el
        y0, y1 = el["from"][1], el["to"][1]
        t = (y - y0) / max(0.01, (y1 - y0))
        c = mix(scale(color, 0.72), color, t)
        if n[1] > 0:
            c = mix(color, hi, 0.5)
        elif n[1] < 0:
            c = scale(color, 0.6)
        # specular band across the face
        band = abs(((x + z) * 0.5 + y * 0.2) % 2.0 - 1.0)
        if band < 0.18:
            c = mix(c, hi, 0.45)
        c = scale(c, 1 + smooth_noise(x * 4, y * 4 + z * 4, ctx.seed) * noise)
        return c

    return paint


def wrap(base, band, diamond=None, width=1.0):
    """Grip wrapping: crossing diagonal bands; with ``diamond`` it becomes a katana tsuka-ito."""
    base = hex_rgb(base)
    band = hex_rgb(band)
    dia = hex_rgb(diamond) if diamond else None

    def paint(p, n, ctx):
        x, y, z = p
        u = x + z  # wraps around the grip
        a = (u + y) % (2 * width)
        b = (u - y) % (2 * width)
        on_a = a < width * 0.55
        on_b = b < width * 0.55
        if dia is not None:
            if on_a or on_b:
                c = band
            else:
                c = dia
        else:
            c = band if on_a else base
        if n[1] != 0:
            c = scale(base, 0.8)
        return scale(c, 1 + smooth_noise(x * 8, y * 8 + z * 8, ctx.seed) * 0.06)

    return paint


def gem(color, core="#FFFFFF"):
    color = hex_rgb(color)
    core = hex_rgb(core)

    def paint(p, n, ctx):
        el = ctx.el
        cx = (el["from"][0] + el["to"][0]) / 2
        cy = (el["from"][1] + el["to"][1]) / 2
        cz = (el["from"][2] + el["to"][2]) / 2
        r = max(0.01, max(el["to"][i] - el["from"][i] for i in range(3)) / 2)
        x, y, z = p
        d = math.sqrt((x - cx) ** 2 + (y - cy) ** 2 + (z - cz) ** 2) / r
        c = mix(core, color, min(1.0, d * 1.1))
        if (x - cx) < -0.1 and (y - cy) > 0.1:
            c = mix(c, (255, 255, 255), 0.35)
        return c

    return paint


def crystal(c1, c2, glint="#FFFFFF"):
    c1, c2, gl = hex_rgb(c1), hex_rgb(c2), hex_rgb(glint)

    def paint(p, n, ctx):
        x, y, z = p
        t = 0.5 + 0.5 * math.sin(x * 1.3 + y * 0.9)
        c = mix(c1, c2, t)
        shape = ctx.el["shape"]
        d = shape.edge_distance(x, y) if shape else 1.0
        if d < 0.3:
            c = mix(gl, c, d / 0.3)
        if hash_noise(ctx.seed, round(x * 4), round(y * 4), round(z * 4)) > 0.93:
            c = gl
        return c

    return paint


def stripes(c1, c2, period=2.0, axis=1, edge=None):
    """Alternating bands along an axis (bee stripes)."""
    c1, c2 = hex_rgb(c1), hex_rgb(c2)
    edge = hex_rgb(edge) if edge else None

    def paint(p, n, ctx):
        v = p[axis]
        c = c1 if (v % period) < period * 0.6 else c2
        shape = ctx.el["shape"]
        if edge and shape and shape.edge_distance(p[0], p[1]) < 0.3:
            c = mix(edge, c, 0.3)
        return scale(c, 1 + smooth_noise(p[0] * 3, p[1] * 3, ctx.seed) * 0.06)

    return paint


def crackle(base, vein, edge, alpha=255):
    """Translucent stone-glass (the Marsh Dagger's jade): white crackle veins and a pale edge."""
    base, vein, edge = hex_rgb(base), hex_rgb(vein), hex_rgb(edge)

    def paint(p, n, ctx):
        x, y, z = p
        f = abs(smooth_noise(x * 2.3, y * 2.3 + z, ctx.seed) - smooth_noise(x * 2.3 + 5.3, y * 2.3 - 2.1, ctx.seed + 1))
        depth = 0.5 + 0.5 * smooth_noise(x * 1.1, y * 0.7, ctx.seed + 2)
        c = mix(scale(base, 0.75), base, depth)
        a = alpha
        if f < 0.05:
            c = mix(vein, c, f / 0.05 * 0.5)
            a = 255
        shape = ctx.el["shape"]
        if shape and shape.edge_distance(x, y) < 0.3:
            c = mix(edge, c, shape.edge_distance(x, y) / 0.3)
            a = 255
        return c + (a,)

    return paint


def edged(base, edge_color, edge_pts, width=0.45, sheen=None):
    """A blade colour with a stripe along one curved edge (Togetsu's inner-edge line)."""
    base, ec = hex_rgb(base), hex_rgb(edge_color)
    sh = hex_rgb(sheen) if sheen else mix(base, (255, 255, 255), 0.35)

    def paint(p, n, ctx):
        x, y, z = p
        d = min(math.hypot(x - ex, y - ey) for ex, ey in edge_pts)
        c = mix(base, sh, 0.5 + 0.5 * math.sin(x * 0.9 - y * 0.55))
        c = mix(base, c, 0.6)
        if d < width:
            c = mix(ec, c, (d / width) ** 2)
        return scale(c, 1 + smooth_noise(x * 5, y * 5 + z * 5, ctx.seed) * 0.03)

    return paint


def radial(center, split, outer, inner, edge="#FFFFFF"):
    """Two-tone moon: ``outer`` beyond ``split`` units from ``center``, ``inner`` inside it."""
    o, i, e = hex_rgb(outer), hex_rgb(inner), hex_rgb(edge)

    def paint(p, n, ctx):
        x, y, z = p
        r = math.hypot(x - center[0], y - center[1])
        c = o if r > split else i
        band = abs(r - split)
        if band < 0.25:
            c = mix(e, c, band / 0.25)
        shape = ctx.el["shape"]
        if shape and shape.edge_distance(x, y) < 0.22:
            c = mix(e, c, 0.4)
        sheen = 0.5 + 0.5 * math.sin(x * 0.7 + y * 0.5)
        return mix(c, (255, 255, 255), 0.12 * sheen if r > split else 0.05 * sheen)

    return paint


def bezier(p0, p1, p2, n=24):
    pts = []
    for k in range(n + 1):
        t = k / n
        pts.append(((1 - t) ** 2 * p0[0] + 2 * (1 - t) * t * p1[0] + t * t * p2[0],
                    (1 - t) ** 2 * p0[1] + 2 * (1 - t) * t * p1[1] + t * t * p2[1]))
    return pts


def moonsteel(base, glow_color, dark_mode=False):
    """Moonblade steel: pearl (Waxing) or night (Waning) with a luminous edge and star glints."""
    base = hex_rgb(base)
    gl = hex_rgb(glow_color)

    def paint(p, n, ctx):
        x, y, z = p
        shape = ctx.el["shape"]
        d = shape.edge_distance(x, y) if shape else 1.0
        sheen = 0.5 + 0.5 * math.sin((x * 0.8 - y * 0.6))
        if dark_mode:
            c = mix(base, mix(base, gl, 0.25), sheen)
        else:
            c = mix(base, (255, 255, 255), 0.25 * sheen)
        if d < 0.35:
            c = mix(gl, c, d / 0.35)
        if hash_noise(ctx.seed, round(x * 4), round(y * 4), round(z * 4)) > (0.95 if dark_mode else 0.97):
            c = mix(c, (255, 255, 255), 0.85)
        return c

    return paint


# ---------------------------------------------------------------------------------------
# Texture packing + JSON
# ---------------------------------------------------------------------------------------

FACES = ("north", "south", "east", "west", "up", "down")
NORMALS = {"north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0), "west": (-1, 0, 0), "up": (0, 1, 0), "down": (0, -1, 0)}


def face_size(el, face):
    sx = el["to"][0] - el["from"][0]
    sy = el["to"][1] - el["from"][1]
    sz = el["to"][2] - el["from"][2]
    return {"north": (sx, sy), "south": (sx, sy), "east": (sz, sy), "west": (sz, sy), "up": (sx, sz), "down": (sx, sz)}[face]


def face_point(el, face, u, v):
    """Model-space point at fractional (u, v) on a face, matching Minecraft's default UV orientation."""
    (x0, y0, z0), (x1, y1, z1) = el["from"], el["to"]
    if face == "north":
        return (x1 - (x1 - x0) * u, y1 - (y1 - y0) * v, z0)
    if face == "south":
        return (x0 + (x1 - x0) * u, y1 - (y1 - y0) * v, z1)
    if face == "east":
        return (x1, y1 - (y1 - y0) * v, z1 - (z1 - z0) * u)
    if face == "west":
        return (x0, y1 - (y1 - y0) * v, z0 + (z1 - z0) * u)
    if face == "up":
        return (x0 + (x1 - x0) * u, y1, z0 + (z1 - z0) * v)
    return (x0 + (x1 - x0) * u, y0, z1 - (z1 - z0) * v)


def hidden_face(el, face, elements):
    """True if the face lies flush against another element that fully covers it."""
    (x0, y0, z0), (x1, y1, z1) = el["from"], el["to"]
    eps = 1e-6
    for o in elements:
        if o is el or o.get("rot"):
            continue
        (a0, b0, c0), (a1, b1, c1) = o["from"], o["to"]
        if face == "up" and abs(b0 - y1) < eps and a0 <= x0 + eps and a1 >= x1 - eps and c0 <= z0 + eps and c1 >= z1 - eps:
            return True
        if face == "down" and abs(b1 - y0) < eps and a0 <= x0 + eps and a1 >= x1 - eps and c0 <= z0 + eps and c1 >= z1 - eps:
            return True
        if face == "east" and abs(a0 - x1) < eps and b0 <= y0 + eps and b1 >= y1 - eps and c0 <= z0 + eps and c1 >= z1 - eps:
            return True
        if face == "west" and abs(a1 - x0) < eps and b0 <= y0 + eps and b1 >= y1 - eps and c0 <= z0 + eps and c1 >= z1 - eps:
            return True
        if face == "south" and abs(c0 - z1) < eps and a0 <= x0 + eps and a1 >= x1 - eps and b0 <= y0 + eps and b1 >= y1 - eps:
            return True
        if face == "north" and abs(c1 - z0) < eps and a0 <= x0 + eps and a1 >= x1 - eps and b0 <= y0 + eps and b1 >= y1 - eps:
            return True
        # Inside a thicker layer of the same silhouette (bevel layers overlap).
        if face in ("north", "south", "east", "west", "up", "down") and a0 <= x0 + eps and a1 >= x1 - eps \
                and b0 <= y0 + eps and b1 >= y1 - eps and c0 <= z0 + eps and c1 >= z1 - eps:
            if (a0 < x0 - eps or a1 > x1 + eps or c0 < z0 - eps or c1 > z1 + eps or b0 < y0 - eps or b1 > y1 + eps):
                p = face_point(el, face, 0.5, 0.5)
                n = NORMALS[face]
                q = (p[0] + n[0] * 1e-3, p[1] + n[1] * 1e-3, p[2] + n[2] * 1e-3)
                if a0 < q[0] < a1 and b0 < q[1] < b1 and c0 < q[2] < c1:
                    return True
    return False


def pack(rects, start=32):
    """Shelf-pack (key, w, h) rects; returns placements and atlas size (square, power of two)."""
    size = start
    while True:
        placed = {}
        x = y = row = 0
        ok = True
        for key, w, h in sorted(rects, key=lambda r: (-r[2], -r[1])):
            if w > size:
                ok = False
                break
            if x + w > size:
                x = 0
                y += row
                row = 0
            placed[key] = (x, y)
            x += w
            row = max(row, h)
            if y + row > size:
                ok = False
                break
        if ok:
            return placed, size
        size *= 2
        if size > 1024:
            raise RuntimeError("weapon texture too big")


def r4(v):
    return round(v, 4)


def build_model(model, materials, display):
    els = model.elements
    rects = []
    faces_of = []
    for i, el in enumerate(els):
        faces = []
        for f in FACES:
            if hidden_face(el, f, els):
                continue
            w, h = face_size(el, f)
            tw, th = max(1, round(w * DENSITY)), max(1, round(h * DENSITY))
            rects.append(((i, f), tw, th))
            faces.append((f, tw, th))
        faces_of.append(faces)
    placed, size = pack(rects)
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    seed = int(hashlib.md5(model.name.encode()).hexdigest()[:6], 16)
    json_els = []
    for i, el in enumerate(els):
        if not faces_of[i]:
            continue
        paint = materials[el["mat"]]
        jfaces = {}
        for (f, tw, th) in faces_of[i]:
            px, py = placed[(i, f)]
            ctx = Ctx(el, f, seed)
            for tx in range(tw):
                for ty in range(th):
                    p = face_point(el, f, (tx + 0.5) / tw, (ty + 0.5) / th)
                    c = paint(p, NORMALS[f], ctx)
                    alpha = int(c[3]) if len(c) == 4 else 255
                    img.putpixel((px + tx, py + ty), clamp_rgb(c[:3]) + (alpha,))
            k = 16.0 / size
            jfaces[f] = {"uv": [r4(px * k), r4(py * k), r4((px + tw) * k), r4((py + th) * k)], "texture": "#0"}
        j = {"from": [r4(v) for v in el["from"]], "to": [r4(v) for v in el["to"]], "faces": jfaces}
        if el["rot"]:
            axis, angle, origin = el["rot"]
            j["rotation"] = {"angle": angle, "axis": axis, "origin": list(origin)}
        if el["glow"]:
            j["shade"] = False
            j["neoforge_data"] = {"block_light": 15, "sky_light": 15}
        json_els.append(j)
    tex = f"{NS}:item/weapon/{model.name}"
    out = {
        "credit": "Generated by tools/weapons.py",
        "texture_size": [size, size],
        "gui_light": "front",
        "textures": {"0": tex, "particle": tex},
        "elements": json_els,
        "display": display,
    }
    if model.extra.get("translucent"):
        out["render_type"] = "minecraft:translucent"
    return out, img


def handheld_display(gui_scale=1.0, hand_scale=1.0, fp_scale=1.0, roll=-45, ground=0.5, fixed=1.0, lift=0.0, flip=True):
    """Vanilla item/handheld transforms with an extra roll applied first (see module doc).

    Single-edged models carry their cutting edge on +x. With ``flip`` the model is also turned
    half a turn about its own blade axis (Ry(-90)·Rz(a)·Ry(180) = Ry(90)·Rz(-a)), so the edge
    leads: away from you in first person, down toward the ground in third person. Models whose
    edge is on -x (the mirrored Waning moonblade) pass ``flip=False``.

    Minecraft negates the y and z rotations of the left-hand transforms when it applies them, so
    the left hand is written with the signs reversed and ends up with the same rotation."""
    def t(rot, tr, s):
        return {"rotation": [rot[0], rot[1], rot[2]], "translation": [tr[0], tr[1], tr[2]], "scale": [s, s, s]}

    def hand(z, left):
        y, z = (90, -z) if flip else (-90, z)
        return (0, -y, -z) if left else (0, y, z)

    return {
        "thirdperson_righthand": t(hand(55 + roll, False), (0, 4.0 + lift, 0.5), 0.85 * hand_scale),
        "thirdperson_lefthand": t(hand(55 + roll, True), (0, 4.0 + lift, 0.5), 0.85 * hand_scale),
        "firstperson_righthand": t(hand(25 + roll, False), (1.13, 3.2 + lift * 0.5, 1.13), 0.68 * fp_scale),
        "firstperson_lefthand": t(hand(25 + roll, True), (1.13, 3.2 + lift * 0.5, 1.13), 0.68 * fp_scale),
        "ground": t((0, 0, roll), (0, 2, 0), ground),
        "gui": t((0, 0, roll), (0, 0, 0), gui_scale),
        "head": t((0, 180, roll), (0, 13, 7), 1.0),
        "fixed": t((0, 180, roll), (0, 0, 0), fixed),
    }


# ---------------------------------------------------------------------------------------
# The weapons
# ---------------------------------------------------------------------------------------

# Vertical layout shared by one-handed blades (model units; grip centred on x = z = 8):
#   pommel ~ -1.5 .. 0, grip 0 .. 3, guard 3 .. 4, blade 4 .. 18.5
BLADE_LAYERS = [(0.0, 0.25), (0.45, 0.42), (0.9, 0.55)]


def straight_blade(y0, y1, w_base, w_tip=0.0, tip_len=2.0, cx=8.0, fuller=False):
    """A straight double-edged blade with a pointed tip."""
    pts = [(cx - w_base / 2, y0), (cx + w_base / 2, y0), (cx + max(w_tip, w_base * 0.8) / 2, y1 - tip_len),
           (cx, y1), (cx - max(w_tip, w_base * 0.8) / 2, y1 - tip_len)]
    return polygon(pts)


def grip_assembly(m, grip_y0, grip_y1, grip_r, mat_grip, mat_guard, mat_pommel, guard_w, guard_h=0.8, guard_d=1.6,
                  pommel_r=0.7, pommel_h=0.9):
    m.prism(grip_r, grip_y0, grip_y1, mat_grip)
    m.cbox(guard_w, grip_y1, grip_y1 + guard_h, guard_d, mat_guard)
    m.prism(pommel_r, grip_y0 - pommel_h, grip_y0, mat_pommel)


def mercenary_twin_blades():
    """The starter Mercenary Twinblades: plain single-edged steel short swords with a dark fuller
    along the spine, a short dark crossguard and a black grip."""
    m = Model("mercenary_twin_blades")
    blade = polygon([(7.0, 3.8), (9.2, 3.8), (9.25, 12.8), (8.7, 15.4), (7.5, 17.5), (7.0, 16.6)])
    m.bevelled(blade, BLADE_LAYERS[:2], "steel")
    m.cbox(0.45, 4.6, 14.0, 1.15, "fuller", cx=7.55)
    grip_assembly(m, 0.0, 3.0, 0.55, "grip", "guard", "guard", guard_w=3.8)
    m.cbox(0.8, 3.0, 3.8, 1.9, "guard")
    mats = {"steel": steel("#B4BCC4", edge="#F2F6FA"), "fuller": steel("#4E545C", ridge=False),
            "grip": wrap("#141414", "#2E2E30"), "guard": metal("#34343A", "#7A7A84")}
    return m, mats, handheld_display()


def marsh_dagger():
    """The Marsh Dagger: a wavy, serpentine kris blade of translucent green stone-glass with white
    crackle veins, a chunky two-tone green guard, a dark cord grip and a round pommel."""
    m = Model("marsh_dagger")
    m.extra["translucent"] = True
    kris = curved_blade(4.1, 16.2, lambda t: 2.5 * (1 - t) ** 0.65 + 0.2,
                        lambda t: 8.0 + 0.7 * math.sin(t * 3 * math.pi) * (1 - 0.45 * t))
    m.bevelled(kris, [(0.0, 0.22), (0.45, 0.4)], "jade")
    grip_assembly(m, 0.1, 3.0, 0.5, "grip", "guard", "pommel", guard_w=4.4, guard_h=0.9, guard_d=1.9, pommel_r=0.85,
                  pommel_h=1.2)
    m.cbox(3.0, 3.9, 4.4, 1.4, "guard2")
    m.box((5.3, 3.4, 7.4), (6.0, 4.6, 8.6), "guard2")
    m.box((10.0, 3.4, 7.4), (10.7, 4.6, 8.6), "guard2")
    mats = {"jade": crackle("#3FB874", "#F2FFF6", "#E8FFF0", alpha=205), "grip": wrap("#16201A", "#2F4A38"),
            "guard": metal("#24482E", "#5A9A6A"), "guard2": metal("#3E7A50", "#9AD8A8"), "pommel": metal("#24482E", "#5A9A6A")}
    return m, mats, handheld_display()


def ducusram():
    """Bilac's reforge of the Marsh Dagger: a long, double-edged blade with gold fittings."""
    m = Model("ducusram")
    blade = polygon([(6.75, 4.0), (9.25, 4.0), (9.55, 6.5), (9.2, 15.0), (8.0, 19.4), (6.8, 15.0), (6.45, 6.5)])
    m.bevelled(blade, BLADE_LAYERS, "steel")
    m.cbox(0.35, 5.0, 15.5, 1.15, "fuller")
    grip_assembly(m, -0.2, 3.0, 0.55, "grip", "guard", "pommel", guard_w=5.2, guard_h=0.9, guard_d=1.6, pommel_r=0.8)
    # Swept guard tips and a gem in the guard
    m.box((5.0, 3.6, 7.6), (5.8, 4.9, 8.4), "guard")
    m.box((10.2, 3.6, 7.6), (11.0, 4.9, 8.4), "guard")
    m.cbox(0.9, 3.15, 3.75, 1.9, "gem")
    mats = {"steel": steel("#8E9EA8", light="#D2DEE6", edge="#F4FAFF"), "fuller": steel("#6E7E88", ridge=False),
            "grip": wrap("#2A3038", "#C9B07A", width=0.8), "guard": metal("#C9B07A", "#FFF0C0"),
            "pommel": metal("#C9B07A", "#FFF0C0"), "gem": gem("#2F7FD0", "#BFE8FF")}
    return m, mats, handheld_display()


def empire_bee_twinblades():
    """Bilac's Empire Bee Twinblade (the long blade): a straight violet blade carrying Crush
    Poison, a gold crossguard over a purple shell, a black-and-gold spiral grip with a cream
    bulb, and a looping black knuckle-bow that ends in a pink tip."""
    m = Model("empire_bee_twinblades")
    blade = straight_blade(4.2, 19.6, 2.1, tip_len=3.2)
    m.bevelled(blade, BLADE_LAYERS, "violet")
    m.cbox(0.4, 5.0, 15.5, 1.15, "venom", glow=True)
    m.cbox(4.6, 3.4, 4.2, 1.7, "gold")
    m.extrude(ellipse(8.0, 8.0, 1.9, 1.5), 0.32, "shell", plane="xz", y_center=3.2)
    m.prism(0.55, 0.0, 3.0, "grip")
    m.prism(0.85, -1.5, 0.0, "bulb")
    m.cbox(0.7, -2.2, -1.5, 0.7, "pink")
    # The knuckle-bow loops from the guard round to the bulb.
    m.box((10.2, 0.0, 7.7), (10.75, 3.4, 8.3), "bow")
    m.box((9.0, 3.0, 7.7), (10.75, 3.45, 8.3), "bow")
    m.box((8.8, -0.45, 7.7), (10.75, 0.0, 8.3), "bow")
    m.box((10.75, -0.9, 7.75), (11.35, -0.3, 8.25), "pink")
    mats = {"violet": steel("#7A3AC8", light="#B886F2", dark="#3E1A6A", edge="#EEDCFF"), "venom": flat("#C25AF0", 0.08),
            "gold": metal("#D8B040", "#FFF0A0"), "shell": metal("#5A2A8A", "#B07AE0"), "grip": wrap("#141016", "#D8B040", width=0.55),
            "bulb": metal("#E8D8A8", "#FFFFFF"), "pink": gem("#F07AB0", "#FFE0F0"), "bow": metal("#18161A", "#5A5660")}
    return m, mats, handheld_display()


def tombguard_katana():
    """Wezaemon's katana: a long, slim, curved silver blade, a round black tsuba and a hilt
    wrapped in red cord over pale rayskin."""
    m = Model("tombguard_katana")
    curve = lambda t: 8.0 + 1.3 * t * t  # sori: the blade curves back toward the tip

    def width(t):
        if t > 0.94:  # kissaki
            return 1.9 * max(0.05, (1 - t) / 0.06) ** 0.6
        return 1.9 - 0.25 * t

    blade = curved_blade(4.2, 25.5, width, curve)
    m.bevelled(blade, [(0.0, 0.2), (0.5, 0.36)], "steel")
    # hamon line along the edge side
    m.extrude(curved_blade(4.6, 24.5, lambda t: 0.3, lambda t: curve(t) + 0.55), 0.22, "hamon")
    m.cbox(1.6, 3.6, 4.6, 1.2, "habaki")
    # Round tsuba (a disc in the XZ plane) with seppa washers
    m.extrude(circle(8.0, 8.0, 1.9), 0.3, "tsuba", plane="xz", y_center=3.4)
    m.cbox(1.6, 2.9, 3.1, 1.2, "habaki")
    m.prism(0.55, -4.6, 2.9, "tsuka")
    m.prism(0.62, -5.4, -4.6, "kashira")
    mats = {"steel": steel("#D8E4EC", light="#F6FBFF", edge="#FFFFFF", streak=0.04), "hamon": flat("#F4F8FF", 0.03),
            "habaki": metal("#C9B07A", "#FFF0C0"), "tsuba": metal("#1E1C1A", "#6A6058"),
            "tsuka": wrap("#E8E0D0", "#B8303A", diamond="#E8E0D0", width=0.55), "kashira": metal("#1E1C1A", "#C9B07A")}
    return m, mats, handheld_display(hand_scale=1.0, fp_scale=0.95, lift=1.2, gui_scale=0.78)


def moonblade(name, waxing):
    """Togetsu, the Moonblades: thick talon-curved crescent blades, wide at the base with a
    barb by the guard and a bevel line down the face, tapering to a hooked point. Waxing is
    pearl white with a dark inner-edge stripe; Waning is gunmetal black. Both share a silver
    collar, a maroon cord grip, a silver hex pommel, a red bead and a white hook charm."""
    m = Model(name)
    sx = 1 if waxing else -1  # the two blades are mirror images

    def X(pts):
        return [(8 + sx * (x - 8), y) for (x, y) in pts]

    spine = X(bezier((6.3, 4.6), (4.6, 15.6), (12.3, 19.8)))
    edge = X(bezier((9.7, 4.6), (8.9, 12.8), (12.3, 19.8)))
    blade = polygon(spine + edge[::-1])
    m.bevelled(blade, [(0.0, 0.24), (0.42, 0.42), (0.85, 0.52)], "moon")
    barb = polygon(X([(9.4, 4.6), (11.2, 4.85), (11.05, 5.45), (9.5, 6.7)]))
    m.extrude(barb, 0.36, "moon")
    line = polygon(X(bezier((7.85, 4.7), (6.6, 14.4), (12.1, 19.45))) + X(bezier((8.2, 4.7), (6.95, 14.2), (12.1, 19.45)))[::-1])
    m.extrude(line, 0.55, "bevel")
    # Fittings
    m.cbox(2.2, 3.7, 4.6, 1.5, "silver")
    m.prism(0.5, 0.3, 3.7, "grip")
    m.prism(0.78, -0.6, 0.3, "silver")
    m.cbox(0.6, -1.3, -0.6, 0.6, "bead")
    m.box((8.15, -2.7, 7.85), (8.45, -1.3, 8.15), "charm")
    m.box((7.45, -3.0, 7.85), (8.45, -2.7, 8.15), "charm")
    m.box((7.35, -3.0, 7.85), (7.65, -2.35, 8.15), "charm")
    if waxing:
        mats = {"moon": edged("#F2F0EA", "#34343E", edge, width=0.5, sheen="#FFFFFF"), "bevel": flat("#D8D8E0", 0.03),
                "silver": metal("#C8CCD4", "#FFFFFF"), "grip": wrap("#4A0E18", "#8A2030", width=0.6),
                "bead": gem("#D02030", "#FF9AA0"), "charm": flat("#F4F4F0", 0.02)}
    else:
        mats = {"moon": edged("#3A3C44", "#9AA0AC", edge, width=0.4, sheen="#6A6E7A"), "bevel": flat("#24262C", 0.03),
                "silver": metal("#C8CCD4", "#FFFFFF"), "grip": wrap("#4A0E18", "#8A2030", width=0.6),
                "bead": gem("#D02030", "#FF9AA0"), "charm": flat("#F4F4F0", 0.02)}
    return m, mats, handheld_display(flip=waxing)


def twin_moon():
    """Sogengetsu, the Twin Moon: Waxing and Waning locked into one huge, nearly circular crescent,
    white on the outer arc and black on the inner, on a red-wrapped handle with a white ribbon."""
    m = Model("twin_moon")
    cx, cy = 8.0, 16.6
    outer = ellipse(cx, cy, 7.3, 7.5)
    inner = ellipse(cx + 2.2, cy + 0.3, 4.6, 5.2)
    moon = outer - inner
    m.bevelled(moon, [(0.0, 0.26), (0.45, 0.44), (0.95, 0.56)], "moon")
    # The neck joining the handle to the bottom of the ring
    m.cbox(1.6, 7.6, 9.6, 1.4, "silver")
    m.cbox(3.4, 6.9, 7.6, 1.8, "silver")
    m.prism(0.62, 0.6, 6.9, "grip")
    m.prism(0.85, -0.6, 0.6, "silver")
    m.cbox(0.7, -1.4, -0.6, 0.7, "bead")
    # The white ribbon tied below the guard
    m.box((8.7, 3.6, 7.92), (9.25, 6.9, 8.08), "ribbon")
    m.box((9.1, 1.4, 7.92), (9.65, 3.8, 8.08), "ribbon")
    m.box((7.0, 4.4, 7.92), (7.4, 6.9, 8.08), "ribbon")
    mats = {"moon": radial((cx, cy), 5.2, "#F2F0EA", "#24242C", edge="#E8F2FF"), "silver": metal("#C8CCD4", "#FFFFFF"),
            "grip": wrap("#4A0E18", "#A82434", width=0.6), "bead": gem("#D02030", "#FF9AA0"), "ribbon": flat("#FAFAF6", 0.02)}
    return m, mats, handheld_display(hand_scale=1.2, fp_scale=0.95, lift=2.0, gui_scale=0.6)


def vorpal_choppers():
    """The Vorpal Choppers ("fatal kitchen knives"): long, slim crimson blades whose edge is
    slightly wavy and jagged like a cleaver, on dark brown wooden handles."""
    m = Model("vorpal_choppers")
    pts = [(6.9, 3.9), (6.9, 17.8), (7.25, 19.2)]
    for i in range(41):
        y = 19.0 - i * (15.1 / 40)
        rise = min(1.0, (19.2 - y) / 4.2) ** 0.55
        jag = 0.16 * math.sin(y * 4.4) + (0.08 if int(y * 2.2) % 2 else -0.04)
        pts.append((7.25 + (9.5 - 7.25) * rise + (jag if y < 15.5 else 0), y))
    pts.append((9.5, 3.9))
    blade = polygon(pts)
    m.bevelled(blade, [(0.0, 0.2), (0.45, 0.36), (0.9, 0.44)], "crimson")
    m.cbox(1.7, 3.5, 4.0, 1.3, "bolster")
    m.prism(0.58, -0.8, 3.5, "wood")
    for y in (0.4, 2.2):
        m.cbox(0.38, y, y + 0.38, 1.24, "rivet")
    m.prism(0.66, -1.2, -0.8, "bolster")
    mats = {"crimson": steel("#B01E2E", light="#E85060", dark="#5A0A14", edge="#FFC0C8", warm="#C8303A"),
            "bolster": metal("#2A2422", "#8A7A70"), "wood": stripes("#4A2C18", "#3A2010", period=0.9, axis=1),
            "rivet": metal("#B8B0A8", "#FFFFFF")}
    return m, mats, handheld_display()


WEAPONS = {
    "mercenary_twin_blades": mercenary_twin_blades,
    "marsh_dagger": marsh_dagger,
    "ducusram": ducusram,
    "vorpal_choppers": vorpal_choppers,
    "empire_bee_twinblades": empire_bee_twinblades,
    "moonblade_waxing": lambda: moonblade("moonblade_waxing", True),
    "moonblade_waning": lambda: moonblade("moonblade_waning", False),
    "twin_moon": twin_moon,
    "tombguard_katana": tombguard_katana,
}


def build_all():
    for name, fn in WEAPONS.items():
        model, mats, display = fn()
        out, img = build_model(model, mats, display)
        mp = os.path.join(ASSETS, f"models/item/{name}.json")
        os.makedirs(os.path.dirname(mp), exist_ok=True)
        with open(mp, "w") as f:
            json.dump(out, f, indent=1)
            f.write("\n")
        tp = os.path.join(ASSETS, f"textures/item/weapon/{name}.png")
        os.makedirs(os.path.dirname(tp), exist_ok=True)
        img.save(tp)
        print(f"weapon {name}: {len(out['elements'])} elements, {img.size[0]}px texture")


if __name__ == "__main__":
    build_all()

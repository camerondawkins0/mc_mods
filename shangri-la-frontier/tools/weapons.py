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
                    img.putpixel((px + tx, py + ty), clamp_rgb(c) + (255,))
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
    return out, img


def handheld_display(gui_scale=1.0, hand_scale=1.0, fp_scale=1.0, roll=-45, ground=0.5, fixed=1.0, lift=0.0):
    """Vanilla item/handheld transforms with an extra roll applied first (see module doc)."""
    def t(rot, tr, s):
        return {"rotation": [rot[0], rot[1], rot[2]], "translation": [tr[0], tr[1], tr[2]], "scale": [s, s, s]}
    return {
        "thirdperson_righthand": t((0, -90, 55 + roll), (0, 4.0 + lift, 0.5), 0.85 * hand_scale),
        "thirdperson_lefthand": t((0, 90, -55 + roll), (0, 4.0 + lift, 0.5), 0.85 * hand_scale),
        "firstperson_righthand": t((0, -90, 25 + roll), (1.13, 3.2 + lift * 0.5, 1.13), 0.68 * fp_scale),
        "firstperson_lefthand": t((0, 90, -25 + roll), (1.13, 3.2 + lift * 0.5, 1.13), 0.68 * fp_scale),
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
    """Cheap, honest steel short swords with a leather grip and a plain cross-guard."""
    m = Model("mercenary_twin_blades")
    blade = straight_blade(3.8, 17.5, 2.2, tip_len=3.0)
    m.bevelled(blade, BLADE_LAYERS[:2], "steel")
    m.cbox(0.5, 4.0, 13.5, 1.25, "fuller")
    grip_assembly(m, 0.0, 3.0, 0.55, "grip", "guard", "guard", guard_w=4.6)
    m.cbox(0.8, 3.0, 3.8, 1.9, "guard")
    mats = {"steel": steel("#AEB6BF", edge="#F2F6FA"), "fuller": steel("#8C949C", ridge=False),
            "grip": wrap("#5A3A22", "#7A5233"), "guard": metal("#6E6A64")}
    return m, mats, handheld_display()


def marsh_dagger():
    """Gray-Ore steel: a broad, leaf-shaped dagger with a greenish sheen from the marsh."""
    m = Model("marsh_dagger")
    leaf = polygon([(6.6, 3.8), (9.4, 3.8), (10.1, 7.5), (9.6, 11.0), (8.0, 15.2), (6.4, 11.0), (5.9, 7.5)])
    m.bevelled(leaf, BLADE_LAYERS, "steel")
    grip_assembly(m, 0.2, 3.0, 0.5, "grip", "guard", "pommel", guard_w=3.6, guard_h=0.8, guard_d=1.4, pommel_r=0.75)
    # Curled quillons
    m.box((5.6, 3.8, 7.5), (6.4, 4.8, 8.5), "guard")
    m.box((9.6, 3.8, 7.5), (10.4, 4.8, 8.5), "guard")
    mats = {"steel": steel("#7F8E86", light="#B9C7BF", edge="#DDE8E2", warm="#6E8A60"),
            "grip": wrap("#2E3A2E", "#4E6A4A"), "guard": metal("#4E5E56"), "pommel": metal("#5E6E66")}
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
    """Forged from the Empire Bee Queen's crown and stingers: amber stinger blades."""
    m = Model("empire_bee_twinblades")
    sting = curved_blade(3.6, 18.0, lambda t: 2.6 * (1 - t) ** 0.7 + 0.15, lambda t: 8.0 + 0.6 * math.sin(t * math.pi) * 0.5)
    m.bevelled(sting, [(0.0, 0.3), (0.45, 0.5)], "chitin")
    # Black venom grooves
    m.cbox(0.4, 5.0, 13.0, 1.05, "venom", glow=True)
    m.prism(0.6, 0.0, 3.0, "grip")
    # Wing-shaped guard
    wing = polygon([(8.0, 3.0), (11.6, 4.4), (12.0, 5.4), (9.2, 4.3), (8.0, 4.0), (6.8, 4.3), (4.0, 5.4), (4.4, 4.4)])
    m.extrude(wing, 0.45, "wing")
    m.prism(0.8, -1.2, 0.0, "pommel")
    mats = {"chitin": stripes("#E8B530", "#2B1D0E", period=2.6, edge="#FFE890"), "venom": flat("#B6F04A", 0.1),
            "grip": wrap("#2B1D0E", "#6B4A1E"), "wing": crystal("#E6F4FF", "#BFD8F0"),
            "pommel": metal("#F2C230", "#FFF2A0")}
    return m, mats, handheld_display()


def tombguard_katana():
    """Wezaemon's katana: a long curved blade, round tsuba, black-and-gold tsuka-ito."""
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
            "habaki": metal("#C9B07A", "#FFF0C0"), "tsuba": metal("#2A2620", "#7A6A50"),
            "tsuka": wrap("#1A1410", "#C9B07A", diamond="#7A1E2C", width=0.55), "kashira": metal("#2A2620", "#C9B07A")}
    return m, mats, handheld_display(hand_scale=1.0, fp_scale=0.95, lift=1.2, gui_scale=0.78)


def crescent(y0, y1, cx, outer_r, inner_r, inner_dx, inner_dy=0.0):
    """A crescent: a disc minus an offset disc, clipped to y0..y1."""
    cy = (y0 + y1) / 2
    outer = ellipse(cx, cy, outer_r, (y1 - y0) / 2)
    inner = ellipse(cx + inner_dx, cy + inner_dy, inner_r, (y1 - y0) / 2 * 0.98)
    return outer - inner


def moonblade(name, waxing):
    """Moonblades (Togetsu): crescent blades of moonlight. Waxing is pearl-white, Waning night-black."""
    m = Model(name)
    side = -1 if waxing else 1  # the crescents face each other when held in both hands
    blade = crescent(3.6, 19.0, 8.0 - side * 1.4, 3.2, 3.3, -side * 1.8, 0.0)
    m.bevelled(blade, [(0.0, 0.22), (0.4, 0.4), (0.85, 0.5)], "moon")
    # Luminous inner edge
    edge = crescent(3.8, 18.8, 8.0 - side * 1.4, 3.0, 3.3, -side * 1.95, 0.0) - crescent(3.8, 18.8, 8.0 - side * 1.4, 3.0, 3.3, -side * 1.6, 0.0)
    m.extrude(edge, 0.26, "edge", glow=True)
    m.cbox(2.8, 3.0, 3.9, 1.5, "guard")
    m.cbox(1.2, 3.9, 4.4, 1.2, "guard")
    m.prism(0.5, 0.0, 3.0, "grip")
    m.prism(0.7, -1.0, 0.0, "guard")
    m.cbox(0.8, -1.9, -1.0, 0.8, "gem", glow=True)
    if waxing:
        mats = {"moon": moonsteel("#E6E8F4", "#BFE8FF"), "edge": flat("#E8FBFF", 0.02),
                "guard": metal("#C9CEDD", "#FFFFFF"), "grip": wrap("#3A3E58", "#9FC3E8"), "gem": gem("#9FE2FF", "#FFFFFF")}
    else:
        mats = {"moon": moonsteel("#241E34", "#8E5BE0", dark_mode=True), "edge": flat("#C9A0FF", 0.02),
                "guard": metal("#3A2E50", "#8E7BB8"), "grip": wrap("#141018", "#6E2FA8"), "gem": gem("#B57CFF", "#F2E6FF")}
    return m, mats, handheld_display()


def twin_moon():
    """Twin Moon: Waxing and Waning joined back to back into one great moon-blade."""
    m = Model("twin_moon")
    # Two crescents back to back around a shared spine.
    light = crescent(4.0, 27.0, 6.2, 4.4, 4.5, 2.4)
    dark = crescent(4.0, 27.0, 9.8, 4.4, 4.5, -2.4)
    m.bevelled(light, [(0.0, 0.24), (0.45, 0.42), (0.95, 0.55)], "wax")
    m.bevelled(dark, [(0.0, 0.24), (0.45, 0.42), (0.95, 0.55)], "wane")
    m.cbox(0.9, 4.0, 25.5, 1.3, "spine", glow=True)
    m.cbox(5.4, 2.8, 4.0, 1.8, "guard")
    m.cbox(2.2, 4.0, 5.0, 1.5, "guard")
    m.prism(0.62, -3.4, 2.8, "grip")
    m.prism(0.85, -4.6, -3.4, "guard")
    m.cbox(1.0, -5.6, -4.6, 1.0, "gem", glow=True)
    mats = {"wax": moonsteel("#E6E8F4", "#BFE8FF"), "wane": moonsteel("#241E34", "#8E5BE0", dark_mode=True),
            "spine": flat("#DDEBFF", 0.03), "guard": metal("#8A8FB0", "#FFFFFF"), "grip": wrap("#1A1626", "#9FC3E8"),
            "gem": gem("#C9B0FF", "#FFFFFF")}
    return m, mats, handheld_display(hand_scale=1.25, fp_scale=1.0, lift=2.0, gui_scale=0.66)


def vorpal_choppers():
    """The Vorpal Choppers Vysache gives: short, heavy cleaver-knives of the Vorpal Bunnies."""
    m = Model("vorpal_choppers")
    cleaver = polygon([(6.5, 3.8), (9.0, 3.8), (11.2, 9.0), (11.4, 14.6), (10.4, 16.0), (7.4, 16.0), (6.2, 13.0), (6.5, 6.0)])
    m.bevelled(cleaver, [(0.0, 0.25), (0.45, 0.42), (1.0, 0.52)], "steel")
    # Rabbit-ear notches near the spine and a red binding
    m.cbox(0.45, 13.6, 15.4, 1.1, "red", cx=7.2)
    m.cbox(0.45, 13.6, 15.4, 1.1, "red", cx=8.1)
    m.cbox(3.2, 3.0, 3.8, 1.5, "guard")
    m.prism(0.55, 0.0, 3.0, "grip")
    m.prism(0.7, -1.0, 0.0, "guard")
    mats = {"steel": steel("#E8E4F4", light="#FFFFFF", edge="#FFFFFF", warm="#F2D8E8"), "red": flat("#C0283A", 0.05),
            "guard": metal("#C0283A", "#FF8090"), "grip": wrap("#6F5BD1", "#B8A8F8")}
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

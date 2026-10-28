"""Quick software renders of generated models, for iterating without launching Minecraft.

  python3 tools/preview.py items  out.png     # every 3D weapon model, front + 3/4 view
  python3 tools/preview.py mobs   out.png     # every creature in tools/models.py
  python3 tools/preview.py structure out.png town/firstia   # an isometric view of a template

Rendering is orthographic point-splatting with a z-buffer and Minecraft-style face
shading. It is only a preview: lighting, render types and animations are not exact.
"""
import json
import math
import os
import sys

from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(__file__), "..")
ASSETS = os.path.join(ROOT, "src/main/resources/assets/slfrontier")

SHADE = {"up": 1.0, "down": 0.5, "north": 0.8, "south": 0.8, "east": 0.6, "west": 0.6}


def rot_y(p, a):
    c, s = math.cos(a), math.sin(a)
    return (p[0] * c + p[2] * s, p[1], -p[0] * s + p[2] * c)


def rot_x(p, a):
    c, s = math.cos(a), math.sin(a)
    return (p[0], p[1] * c - p[2] * s, p[1] * s + p[2] * c)


def rot_z(p, a):
    c, s = math.cos(a), math.sin(a)
    return (p[0] * c - p[1] * s, p[0] * s + p[1] * c, p[2])


class Canvas:
    def __init__(self, w, h, bg=(32, 36, 48)):
        self.w, self.h = w, h
        self.img = Image.new("RGB", (w, h), bg)
        self.z = [[-1e9] * w for _ in range(h)]

    def splat(self, x, y, depth, color):
        ix, iy = int(x), int(y)
        if 0 <= ix < self.w and 0 <= iy < self.h and depth > self.z[iy][ix]:
            self.z[iy][ix] = depth
            self.img.putpixel((ix, iy), color)


def render_quads(canvas, quads, view, center, scale, offset):
    """quads: list of (corner_fn(u, v) -> model point, tex_fn(u, v) -> rgba, face_name, size_hint)."""
    yaw, pitch, roll = view

    def project(p):
        q = (p[0] - center[0], p[1] - center[1], p[2] - center[2])
        q = rot_z(q, roll)
        q = rot_y(q, yaw)
        q = rot_x(q, pitch)
        return (offset[0] + q[0] * scale, offset[1] - q[1] * scale, q[2])

    for corner, tex, face, (fw, fh) in quads:
        n = max(2, int(max(fw, fh) * scale * 1.6) + 2)
        nu = max(2, int(fw * scale * 1.6) + 2)
        nv = max(2, int(fh * scale * 1.6) + 2)
        for i in range(nu):
            for j in range(nv):
                u, v = (i + 0.5) / nu, (j + 0.5) / nv
                rgba = tex(u, v)
                if rgba[3] < 128:
                    continue
                sx, sy, d = project(corner(u, v))
                f = SHADE[face]
                canvas.splat(sx, sy, d, tuple(int(c * f) for c in rgba[:3]))


# ---------------------------------------------------------------------------------------
# JSON item models
# ---------------------------------------------------------------------------------------

def item_quads(model_path):
    with open(model_path) as f:
        m = json.load(f)
    tex_id = m["textures"]["0"].split(":")[1]
    tex = Image.open(os.path.join(ASSETS, "textures", tex_id + ".png")).convert("RGBA")
    tw, th = tex.size
    quads = []
    for el in m["elements"]:
        (x0, y0, z0), (x1, y1, z1) = el["from"], el["to"]
        for face, fd in el["faces"].items():
            u0, v0, u1, v1 = fd["uv"]

            def corner(u, v, face=face, x0=x0, y0=y0, z0=z0, x1=x1, y1=y1, z1=z1):
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

            def texel(u, v, u0=u0, v0=v0, u1=u1, v1=v1):
                px = (u0 + (u1 - u0) * u) / 16 * tw
                py = (v0 + (v1 - v0) * v) / 16 * th
                return tex.getpixel((min(tw - 1, int(px)), min(th - 1, int(py))))

            size = {"north": (x1 - x0, y1 - y0), "south": (x1 - x0, y1 - y0), "east": (z1 - z0, y1 - y0),
                    "west": (z1 - z0, y1 - y0), "up": (x1 - x0, z1 - z0), "down": (x1 - x0, z1 - z0)}[face]
            quads.append((corner, texel, face, size))
    return quads


def preview_items(out):
    sys.path.insert(0, os.path.dirname(__file__))
    import weapons
    names = list(weapons.WEAPONS)
    cell = int(os.environ.get("CELL", 220))
    img = Image.new("RGB", (cell * len(names), cell * 2 + 20), (32, 36, 48))
    draw = ImageDraw.Draw(img)
    for i, name in enumerate(names):
        quads = item_quads(os.path.join(ASSETS, f"models/item/{name}.json"))
        for row, view in enumerate([(0.0, 0.0, -math.pi / 4), (math.radians(35), math.radians(20), -math.pi / 4)]):
            c = Canvas(cell, cell)
            render_quads(c, quads, view, (8, 10, 8), cell / 30.0, (cell / 2, cell / 2))
            img.paste(c.img, (i * cell, row * cell))
        draw.text((i * cell + 4, cell * 2 + 4), name, fill=(220, 220, 220))
    img.save(out)
    print("wrote", out)


# ---------------------------------------------------------------------------------------
# Entity models (tools/models.py specs + generated textures)
# ---------------------------------------------------------------------------------------

def entity_quads(model, uv, tex, part_pose=None, res=1):
    """Box-UV cubes from a models.py spec, with part pivots/rotations applied (Y up, facing -Z).
    {@code res} is texels per model unit (tools/creatures.py paints at RES)."""
    tw, th = tex.size
    parts = {p["name"]: p for p in model}

    def chain(name):
        out = []
        while name != "root":
            p = parts[name]
            out.append(p)
            name = p["parent"]
        return out

    def transform(pt, name):
        for p in chain(name):
            rx, ry, rz = (math.radians(a) for a in p["rot"])
            if part_pose and p["name"] in part_pose:
                ax, ay, az = part_pose[p["name"]]
                rx, ry, rz = rx + math.radians(ax), ry + math.radians(ay), rz + math.radians(az)
            px, py, pz = p["pivot"]
            q = (pt[0] - px, pt[1] - py, pt[2] - pz)
            # Minecraft applies Z, then Y, then X (ZYX) in model space where Y points down;
            # in our Y-up spec the X and Z rotations flip sign.
            q = rot_z(q, -rz)
            q = rot_y(q, ry)
            q = rot_x(q, -rx)
            pt = (q[0] + px, q[1] + py, q[2] + pz)
        return pt

    quads = []
    for p in model:
        for i, c in enumerate(p["cubes"]):
            (fx, fy, fz) = c["from"]
            (sx, sy, sz) = c["size"]
            u, v = uv[(p["name"], i)]
            w, h, d = c["size"]
            infl = c.get("inflate", 0.0)
            x0, y0, z0 = fx - infl, fy - infl, fz - infl
            x1, y1, z1 = fx + sx + infl, fy + sy + infl, fz + sz + infl
            rects = {
                "up": (u + d, v, w, d), "down": (u + d + w, v, w, d), "east": (u, v + d, d, h),
                "north": (u + d, v + d, w, h), "west": (u + d + w, v + d, d, h), "south": (u + d + w + d, v + d, w, h),
            }
            # In the spec, "front" faces -Z (north) and "right" is the entity's right (-X side seen from front = west in MC
            # box UV terms the 'right' strip is at u..u+d which Minecraft draws on the +X? we keep MC's layout).
            for face, (rx0, ry0, rw, rh) in rects.items():
                if rw <= 0 or rh <= 0:
                    continue

                def corner(a, b, face=face, x0=x0, y0=y0, z0=z0, x1=x1, y1=y1, z1=z1, name=p["name"]):
                    if face == "north":
                        pt = (x1 - (x1 - x0) * a, y1 - (y1 - y0) * b, z0)
                    elif face == "south":
                        pt = (x0 + (x1 - x0) * a, y1 - (y1 - y0) * b, z1)
                    elif face == "east":
                        pt = (x0, y1 - (y1 - y0) * b, z1 - (z1 - z0) * a)
                    elif face == "west":
                        pt = (x1, y1 - (y1 - y0) * b, z0 + (z1 - z0) * a)
                    elif face == "up":
                        pt = (x0 + (x1 - x0) * a, y1, z0 + (z1 - z0) * b)
                    else:
                        pt = (x0 + (x1 - x0) * a, y0, z1 - (z1 - z0) * b)
                    return transform(pt, name)

                def texel(a, b, rx0=rx0, ry0=ry0, rw=rw, rh=rh):
                    px = (rx0 + a * rw) * res
                    py = (ry0 + b * rh) * res
                    return tex.getpixel((min(tw - 1, int(px)), min(th - 1, int(py))))

                size = {"north": (sx, sy), "south": (sx, sy), "east": (sz, sy), "west": (sz, sy), "up": (sx, sz),
                        "down": (sx, sz)}[face]
                quads.append((corner, texel, face, size))
    return quads


def preview_mobs(out, only=None):
    sys.path.insert(0, os.path.dirname(__file__))
    import creatures
    import models
    layouts = {}
    for name, model in models.MODELS.items():
        layouts[name] = creatures.pack(model)
    variants = [v for v in models.VARIANTS if not only or v[1] in only]
    cell = int(os.environ.get("PREVIEW_CELL", 300))
    bg = tuple(int(x) for x in os.environ.get("PREVIEW_BG", "32,36,48").split(","))
    img = Image.new("RGB", (cell * 3, (cell + 20) * len(variants)), bg)
    draw = ImageDraw.Draw(img)
    for i, (model_name, tex_name, _) in enumerate(variants):
        model = models.MODELS[model_name]
        uv, tw, th = layouts[model_name]
        tex = Image.open(os.path.join(ASSETS, f"textures/entity/{tex_name}.png")).convert("RGBA")
        quads = entity_quads(model, uv, tex, res=tex.size[0] / tw)
        # fit
        pts = []
        for corner, _, _, _ in quads[::3]:
            pts.append(corner(0, 0))
            pts.append(corner(1, 1))
        lo = [min(p[k] for p in pts) for k in range(3)]
        hi = [max(p[k] for p in pts) for k in range(3)]
        center = [(lo[k] + hi[k]) / 2 for k in range(3)]
        extent = max(hi[k] - lo[k] for k in range(3))
        sc = (cell * 0.8) / max(1, extent)
        views = [(math.radians(180 + 30), math.radians(15), 0), (math.radians(90 + 180), math.radians(5), 0),
                 (math.radians(-35), math.radians(20), 0)]
        for col, view in enumerate(views):
            c = Canvas(cell, cell, bg)
            render_quads(c, quads, view, center, sc, (cell / 2, cell / 2))
            img.paste(c.img, (col * cell, i * (cell + 20)))
        draw.text((4, i * (cell + 20) + cell + 2), tex_name, fill=(220, 220, 220))
    img.save(out)
    print("wrote", out)


# ---------------------------------------------------------------------------------------
# Structure templates (towns and landmarks)
# ---------------------------------------------------------------------------------------

BLOCK_COLORS = [
    ("cherry_leaves", (232, 168, 200)), ("spruce_leaves", (52, 84, 52)), ("leaves", (64, 118, 44)),
    ("grass_block", (98, 162, 62)), ("short_grass", (98, 162, 62)), ("fern", (80, 140, 60)), ("pink_petals", (240, 170, 200)),
    ("water", (58, 92, 204)), ("lava", (230, 110, 30)),
    ("chiseled_sandstone", (205, 188, 138)), ("smooth_sandstone", (224, 212, 164)), ("cut_sandstone", (216, 202, 152)),
    ("sandstone", (214, 200, 150)), ("red_sand", (190, 100, 40)), ("sand", (219, 207, 163)), ("gravel", (130, 125, 122)),
    ("dirt_path", (148, 122, 65)), ("coarse_dirt", (119, 85, 59)), ("podzol", (92, 64, 30)), ("farmland", (100, 70, 40)),
    ("mud_brick", (137, 103, 79)), ("packed_mud", (142, 106, 79)), ("mud", (60, 57, 60)), ("dirt", (134, 96, 67)),
    ("white_concrete", (207, 213, 214)), ("calcite", (223, 224, 220)), ("quartz", (235, 229, 222)),
    ("orange_terracotta", (160, 83, 37)), ("red_terracotta", (143, 61, 46)), ("white_terracotta", (209, 178, 161)),
    ("acacia", (168, 90, 50)), ("oxidized_cut_copper", (82, 162, 132)), ("deepslate_tile", (54, 54, 56)),
    ("deepslate", (80, 80, 82)), ("blackstone", (42, 36, 40)), ("tuff", (108, 109, 102)),
    ("dark_oak", (66, 43, 20)), ("spruce", (114, 84, 48)), ("birch", (192, 175, 121)), ("cherry", (226, 178, 172)),
    ("oak", (162, 130, 78)), ("mossy", (100, 120, 90)), ("cracked_stone", (118, 117, 118)), ("chiseled_stone", (120, 119, 120)),
    ("stone_brick", (122, 121, 122)), ("cobblestone", (110, 110, 110)), ("andesite", (136, 136, 137)), ("smooth_stone", (158, 158, 158)),
    ("stone", (125, 125, 125)), ("bricks", (150, 97, 83)), ("brick", (150, 97, 83)),
    ("glass", (200, 222, 232)), ("lantern", (255, 200, 100)), ("gold", (240, 200, 60)), ("iron_bars", (140, 140, 140)),
    ("red_wool", (160, 39, 34)), ("red_carpet", (160, 39, 34)), ("yellow_wool", (248, 197, 39)), ("blue_wool", (53, 57, 157)),
    ("white_wool", (233, 236, 236)), ("orange_wool", (240, 118, 19)), ("lime_wool", (112, 185, 25)), ("pink_wool", (237, 141, 172)),
    ("banner", (180, 40, 40)), ("bed", (180, 40, 40)), ("hay", (190, 160, 40)), ("carrots", (230, 130, 30)), ("wheat", (200, 180, 80)),
    ("waypoint", (120, 230, 255)), ("structure_block", (255, 0, 255)), ("lightning_rod", (200, 120, 90)),
]


def block_color(name):
    n = name.split(":")[-1]
    for key, col in BLOCK_COLORS:
        if key in n:
            return col
    return (150, 150, 150)


def preview_structure(t, out, scale=3):
    """Isometric render of a structures.Template: top, south and east faces of exposed blocks."""
    sx, sy, sz = t.size
    solid = {p: v[0] for p, v in t.blocks.items() if v[0] not in ("minecraft:air", "minecraft:structure_void")}
    w = int((sx + sz) * scale * 0.9) + 40
    h = int((sx + sz) * scale * 0.45 + sy * scale) + 40
    img = Image.new("RGB", (w, h), (150, 170, 190))
    draw = ImageDraw.Draw(img)
    ox, oy = sz * scale * 0.9 + 20, sy * scale + 20

    def proj(x, y, z):
        return (ox + (x - z) * scale * 0.9, oy + (x + z) * scale * 0.45 - y * scale)

    order = sorted(solid.items(), key=lambda kv: (kv[0][0] + kv[0][2], kv[0][1]))
    for (x, y, z), name in order:
        top = (x, y + 1, z) not in solid
        south = (x, y, z + 1) not in solid
        east = (x + 1, y, z) not in solid
        if not (top or south or east):
            continue
        col = block_color(name)
        if top:
            draw.polygon([proj(x, y + 1, z), proj(x + 1, y + 1, z), proj(x + 1, y + 1, z + 1), proj(x, y + 1, z + 1)], fill=col)
        if south:
            c2 = tuple(int(v * 0.8) for v in col)
            draw.polygon([proj(x, y + 1, z + 1), proj(x + 1, y + 1, z + 1), proj(x + 1, y, z + 1), proj(x, y, z + 1)], fill=c2)
        if east:
            c3 = tuple(int(v * 0.65) for v in col)
            draw.polygon([proj(x + 1, y + 1, z), proj(x + 1, y + 1, z + 1), proj(x + 1, y, z + 1), proj(x + 1, y, z)], fill=c3)
    img.save(out)
    print("wrote", out, len(solid), "blocks")


if __name__ == "__main__":
    kind = sys.argv[1] if len(sys.argv) > 1 else "items"
    out = sys.argv[2] if len(sys.argv) > 2 else f"/tmp/preview_{kind}.png"
    if kind == "items":
        preview_items(out)
    elif kind == "structure":
        sys.path.insert(0, os.path.dirname(__file__))
        import structures
        import towns
        fn = {**structures.ALL, **towns.ALL}[sys.argv[3]]
        preview_structure(fn(), out, int(os.environ.get("PREVIEW_SCALE", 3)))
    else:
        preview_mobs(out, sys.argv[3:] or None)

"""Quick software renders of generated models, for iterating without launching Minecraft.

  python3 tools/preview.py items  out.png     # every 3D weapon model, front + 3/4 view
  python3 tools/preview.py mobs   out.png     # every creature in tools/models.py

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

def entity_quads(model, uv, tex, part_pose=None):
    """Box-UV cubes from a models.py spec, with part pivots/rotations applied (Y up, facing -Z)."""
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
            w, h, d = (int(round(s)) for s in c["size"])
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
                    px = rx0 + a * rw
                    py = ry0 + b * rh
                    return tex.getpixel((min(tw - 1, int(px)), min(th - 1, int(py))))

                size = {"north": (sx, sy), "south": (sx, sy), "east": (sz, sy), "west": (sz, sy), "up": (sx, sz),
                        "down": (sx, sz)}[face]
                quads.append((corner, texel, face, size))
    return quads


def preview_mobs(out, only=None):
    sys.path.insert(0, os.path.dirname(__file__))
    import gen_assets
    import models
    layouts = {}
    for name, model in models.MODELS.items():
        layouts[name] = gen_assets.pack(model)
    variants = [v for v in models.VARIANTS if not only or v[1] in only]
    cell = 300
    img = Image.new("RGB", (cell * 2, (cell + 20) * len(variants)), (32, 36, 48))
    draw = ImageDraw.Draw(img)
    for i, (model_name, tex_name, _) in enumerate(variants):
        model = models.MODELS[model_name]
        uv, tw, th = layouts[model_name]
        tex = Image.open(os.path.join(ASSETS, f"textures/entity/{tex_name}.png")).convert("RGBA")
        quads = entity_quads(model, uv, tex)
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
        for col, view in enumerate([(math.radians(180 + 30), math.radians(15), 0), (math.radians(90 + 180), math.radians(5), 0)]):
            c = Canvas(cell, cell)
            render_quads(c, quads, view, center, sc, (cell / 2, cell / 2))
            img.paste(c.img, (col * cell, i * (cell + 20)))
        draw.text((4, i * (cell + 20) + cell + 2), tex_name, fill=(220, 220, 220))
    img.save(out)
    print("wrote", out)


if __name__ == "__main__":
    kind = sys.argv[1] if len(sys.argv) > 1 else "items"
    out = sys.argv[2] if len(sys.argv) > 2 else f"/tmp/preview_{kind}.png"
    if kind == "items":
        preview_items(out)
    else:
        preview_mobs(out, sys.argv[3:] or None)

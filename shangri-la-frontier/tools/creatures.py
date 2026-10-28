"""Entity models: Java geometry (ModelDefs.java) plus textures painted from tools/models.py.

Textures are painted at RES texels per model unit (the LayerDefinition keeps the 1x size;
Minecraft normalises UVs, so a 2x image simply gives twice the detail). Each material
has a style that decides how it is painted. Materials marked glow are also written to a
<name>_glow.png emissive texture, drawn full-bright by the renderer.
"""
import hashlib
import math
import os
import random

from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), "..")
ASSETS = os.path.join(ROOT, "src/main/resources/assets/slfrontier")
JAVA_OUT = os.path.join(ROOT, "src/main/java/com/slfrontier/client/model/ModelDefs.java")
RES = 2


def hex_rgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def clamp(v):
    return max(0, min(255, int(round(v))))


def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))


def scale(c, f):
    return tuple(c[i] * f for i in range(3))


def h01(*args):
    d = hashlib.md5(repr(args).encode()).digest()
    return d[0] / 255.0


def vnoise(x, y, seed=0):
    x0, y0 = math.floor(x), math.floor(y)
    fx, fy = x - x0, y - y0
    fx, fy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
    a, b = h01(seed, x0, y0), h01(seed, x0 + 1, y0)
    c, d = h01(seed, x0, y0 + 1), h01(seed, x0 + 1, y0 + 1)
    return ((a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy) * 2 - 1


# ---------------------------------------------------------------------------------------
# Materials
# ---------------------------------------------------------------------------------------

class Mat:
    """A material: base colour, noise amount, paint style and options."""

    def __init__(self, color, noise=8, style="flat", **opts):
        self.color = None if color is None else hex_rgb(color)
        self.noise = noise
        self.style = style
        self.opts = opts
        self.glow = opts.get("glow", False)
        self.alt = hex_rgb(opts["alt"]) if "alt" in opts else None


def as_mat(spec):
    if isinstance(spec, Mat):
        return spec
    if spec is None or spec[0] is None:
        return Mat(None)
    if len(spec) == 2:
        return Mat(spec[0], spec[1])
    if len(spec) == 3:
        return Mat(spec[0], spec[1], spec[2])
    return Mat(spec[0], spec[1], spec[2], **spec[3])


FACE_SHADE = {"top": 1.10, "bottom": 0.70, "front": 1.0, "back": 0.90, "left": 0.94, "right": 0.94}


def paint_texel(mat, face, u, v, fw, fh, wx, wy, wz, seed, rnd):
    """Colour (r, g, b, a) of one texel. u/v are 0..1 across the face, fw/fh face size in texels,
    (wx, wy, wz) the model-space point (for patterns continuous across boxes)."""
    if mat.color is None:
        return (0, 0, 0, 0)
    base = mat.color
    st = mat.style
    side = face not in ("top", "bottom")
    shade = FACE_SHADE[face]
    n = (rnd.random() * 2 - 1) * mat.noise
    alpha = 255
    if st == "flat":
        c = scale(base, shade)
    elif st in ("fur", "shag", "hair"):
        # Strands run down the sides; tips are lighter, roots darker.
        streak = vnoise(wx * 2.2 + wz * 2.2, wy * 0.45, seed)
        fine = vnoise(wx * 7 + wz * 7, wy * 1.6, seed + 1)
        k = 1 + 0.16 * streak + 0.10 * fine
        if st == "hair":
            k = 1 + 0.22 * vnoise(wx * 4 + wz * 4, wy * 0.3, seed + 7) + 0.08 * fine
        c = scale(base, shade * k)
        if side and fh > 2:
            c = scale(c, 1.06 - 0.16 * v)
        tip = mat.opts.get("tip")
        if tip and side and v > 0.55:
            c = mix(c, hex_rgb(tip), (v - 0.55) / 0.45 * 0.6)
        if st == "shag" and side and fh >= 4:
            # Ragged lower edge: fur hangs in uneven locks.
            ragged = 0.78 + 0.22 * h01(seed, round(wx * RES), round(wz * RES))
            if v > ragged:
                alpha = 0
    elif st == "armor":
        # Smooth lacquered plates: bright bevel on top edges, dark lower edge, soft sheen.
        c = scale(base, shade)
        if side:
            c = scale(c, 1.05 - 0.12 * v)
            px_v = v * fh
            if px_v < 1:
                c = mix(c, (255, 255, 255), 0.35)
            elif px_v > fh - 1:
                c = scale(c, 0.72)
            if u * fw < 1 or u * fw > fw - 1:
                c = scale(c, 0.85)
        sheen = 0.5 + 0.5 * math.sin((wx * 0.6 + wy * 0.9 + wz * 0.6))
        c = mix(c, (255, 255, 255), 0.06 * sheen * (1 if mat.opts.get("gloss", 1) else 0))
        n *= 0.4
    elif st == "metal":
        c = scale(base, shade * (1 + 0.12 * vnoise(wx * 0.5, wy * 6 + wz * 0.5, seed)))
        if side and v < 1.0 / max(1, fh):
            c = mix(c, (255, 255, 255), 0.3)
    elif st == "rib":
        # Ribbed segments: horizontal bulging bands.
        t = (wy * 1.25) % 1.0
        c = scale(base, shade * (1.15 - 0.35 * abs(t - 0.5) * 2))
    elif st == "screw":
        # A screw/drill: diagonal thread lines wrapping around.
        t = (wy * 1.6 + (wx + wz) * 0.8) % 2.0
        c = scale(base, shade * (1.12 if t < 0.6 else 0.82))
    elif st == "cloth":
        fold = math.sin(wx * 1.4 + wz * 1.4 + vnoise(wx, wy * 0.3, seed) * 2)
        c = scale(base, shade * (1 + 0.09 * fold))
        if side and fh > 3:
            c = scale(c, 1.03 - 0.10 * v)
            if v * fh > fh - 1:
                c = scale(c, 0.8)
    elif st == "skin":
        c = scale(base, shade * (1 + 0.04 * vnoise(wx * 2, wy * 2 + wz * 2, seed)))
        if side and fh > 3:
            c = scale(c, 1.04 - 0.1 * v)
    elif st == "chitin":
        c = scale(base, shade)
        if face == "top" or (side and v < 0.3):
            c = mix(c, (255, 255, 255), 0.18 * (1 - v / 0.3 if side else 0.6))
        if side and v > 0.8:
            c = scale(c, 0.75)
        band = 0.5 + 0.5 * math.sin(wx * 0.8 + wz * 0.5 + wy * 0.2)
        c = mix(c, (255, 255, 255), 0.07 * band)
        n *= 0.5
    elif st == "scale":
        # Overlapping scales: rows of half-circles.
        sx, sy = wx * 1.4 + wz * 1.4, wy * 1.8
        row = math.floor(sy)
        cx = (sx + (0.5 if row % 2 else 0)) % 1.0 - 0.5
        cy = sy % 1.0
        edge = math.hypot(cx, cy - 0.15) > 0.48
        c = scale(base, shade * (0.78 if edge else 1.0 + 0.12 * (1 - cy)))
    elif st == "camo":
        p = vnoise(wx * 0.35 + wz * 0.35, wy * 0.35, seed)
        alt = mat.alt or scale(base, 0.7)
        c = scale(alt if p > 0.15 else base, shade)
        if p > 0.55:
            c = scale(c, 0.85)
    elif st == "wood":
        grain = math.sin((wx + wz) * 3 + vnoise(wx * 0.5 + wz * 0.5, wy * 0.15, seed) * 4)
        c = scale(base, shade * (1 + 0.14 * grain))
    elif st == "glow":
        c = base
        n = 0
    elif st == "feather":
        vane = abs(((wx + wz) * 2.5) % 1.0 - 0.5)
        c = scale(base, shade * (0.85 + 0.3 * vane))
        tip = mat.opts.get("tip")
        if tip:
            c = mix(c, hex_rgb(tip), v)
    elif st == "glass":
        c = scale(base, shade)
        alpha = mat.opts.get("alpha", 140)
        if (u * fw < 1 or u * fw > fw - 1 or v * fh < 1 or v * fh > fh - 1):
            alpha = min(255, alpha + 60)
    else:
        c = scale(base, shade)
    if mat.opts.get("translucent"):
        alpha = min(alpha, mat.opts["translucent"])
    c = tuple(clamp(ch + n) for ch in c)
    return c + (alpha,)


# ---------------------------------------------------------------------------------------
# Box UV layout
# ---------------------------------------------------------------------------------------

def face_rects(u, v, w, h, d):
    """Texture rects (in model units) of a box at texOffs (u, v), Minecraft's layout."""
    return {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h),
        "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h),
        "back": (u + d + w + d, v + d, w, h),
    }


def face_world(cube, face, u, v):
    """Approximate model-space point for a (u, v) on a face of a spec cube (Y up)."""
    (x0, y0, z0), (sx, sy, sz) = cube["from"], cube["size"]
    x1, y1, z1 = x0 + sx, y0 + sy, z0 + sz
    if face == "front":
        return x0 + sx * u, y1 - sy * v, z0
    if face == "back":
        return x1 - sx * u, y1 - sy * v, z1
    if face == "right":
        return x0, y1 - sy * v, z1 - sz * u
    if face == "left":
        return x1, y1 - sy * v, z0 + sz * u
    if face == "top":
        return x0 + sx * u, y1, z0 + sz * v
    return x0 + sx * u, y0, z0 + sz * v


def paint_box(img, glow, u, v, cube, palette, seed, rnd):
    w, h, d = cube["size"]
    rects = face_rects(u, v, w, h, d)
    mat = as_mat(palette.get(cube["mat"], ("#FF00FF", 0)))
    for face, (fx, fy, fw, fh) in rects.items():
        tx0, ty0 = round(fx * RES), round(fy * RES)
        tw, th = round(fw * RES), round(fh * RES)
        if tw <= 0 or th <= 0:
            continue
        for x in range(tw):
            for y in range(th):
                uu, vv = (x + 0.5) / tw, (y + 0.5) / th
                wx, wy, wz = face_world(cube, face, uu, vv)
                c = paint_texel(mat, face, uu, vv, tw, th, wx, wy, wz, seed, rnd)
                img.putpixel((tx0 + x, ty0 + y), c)
                if mat.glow and c[3] > 0:
                    glow.putpixel((tx0 + x, ty0 + y), c)
    for (face, dx, dy, dw, dh, dmat_name) in cube["decals"]:
        fx, fy, fw, fh = rects[face]
        tx0, ty0 = round(fx * RES), round(fy * RES)
        tw, th = round(fw * RES), round(fh * RES)
        dmat = as_mat(palette.get(dmat_name, ("#FF00FF", 0))) if dmat_name else Mat(None)
        for x in range(round(dx * RES), round((dx + dw) * RES)):
            for y in range(round(dy * RES), round((dy + dh) * RES)):
                if 0 <= x < tw and 0 <= y < th:
                    uu, vv = (x + 0.5) / tw, (y + 0.5) / th
                    wx, wy, wz = face_world(cube, face, uu, vv)
                    c = paint_texel(dmat, face, uu, vv, tw, th, wx, wy, wz, seed + 3, rnd)
                    if dmat.style not in ("glow",) and dmat.color is not None and face not in ("top", "bottom"):
                        c = c[:3] + (255,)
                    img.putpixel((tx0 + x, ty0 + y), c)
                    if dmat.glow and c[3] > 0:
                        glow.putpixel((tx0 + x, ty0 + y), c)


# ---------------------------------------------------------------------------------------
# Packing + Java generation
# ---------------------------------------------------------------------------------------

def box_extent(c):
    w, h, d = c["size"]
    return math.ceil(2 * (w + d) - 1e-6), math.ceil(d + h - 1e-6)


def pack(model):
    boxes = []
    for p in model:
        for i, c in enumerate(p["cubes"]):
            bw, bh = box_extent(c)
            boxes.append((p["name"], i, max(1, bw), max(1, bh)))
    for width in (32, 64, 128, 256, 512):
        placed = {}
        x = y = row = 0
        ok = True
        for name, i, bw, bh in sorted(boxes, key=lambda b: (-b[3], -b[2])):
            if bw > width:
                ok = False
                break
            if x + bw > width:
                x = 0
                y += row
                row = 0
            placed[(name, i)] = (x, y)
            x += bw
            row = max(row, bh)
        used = y + row
        if ok and used <= width:
            height = 16
            while height < used:
                height *= 2
            return placed, width, height
    raise RuntimeError("model too big")


def fmt(f):
    s = f"{float(f):.4f}".rstrip("0").rstrip(".")
    if s in ("-0", ""):
        s = "0"
    return s + "F"


def camel(s):
    parts = s.split("_")
    return parts[0] + "".join(x.title() for x in parts[1:])


def java_for(name, model, uv, tw, th):
    pivots = {"root": (0, 0, 0)}
    for p in model:
        pivots[p["name"]] = p["pivot"]
    lines = [f"    public static LayerDefinition {camel(name)}() {{",
             "        MeshDefinition mesh = new MeshDefinition();",
             "        PartDefinition root = mesh.getRoot().addOrReplaceChild(\"root\", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));"]
    for p in model:
        px, py, pz = p["pivot"]
        qx, qy, qz = pivots[p["parent"]]
        cubes = "CubeListBuilder.create()"
        for i, c in enumerate(p["cubes"]):
            fx, fy, fz = c["from"]
            sx, sy, sz = c["size"]
            u, v = uv[(p["name"], i)]
            ox, oy, oz = fx - px, -(fy + sy - py), fz - pz
            cubes += (f"\n                .texOffs({u}, {v}).addBox({fmt(ox)}, {fmt(oy)}, {fmt(oz)}, "
                      f"{fmt(sx)}, {fmt(sy)}, {fmt(sz)}, new CubeDeformation({fmt(c['inflate'])}))")
        rx, ry, rz = (math.radians(a) for a in p["rot"])
        pose = (f"PartPose.offsetAndRotation({fmt(px - qx)}, {fmt(-(py - qy))}, {fmt(pz - qz)}, "
                f"{fmt(rx)}, {fmt(ry)}, {fmt(rz)})")
        parent_var = "root" if p["parent"] == "root" else "p_" + p["parent"]
        lines.append(f"        PartDefinition p_{p['name']} = {parent_var}.addOrReplaceChild(\"{p['name']}\", {cubes}, {pose});")
    lines.append(f"        return LayerDefinition.create(mesh, {tw}, {th});")
    lines.append("    }")
    return "\n".join(lines)


def check(name, model):
    names = {"root"}
    for p in model:
        if p["parent"] not in names:
            raise ValueError(f"{name}: part {p['name']} declared before its parent {p['parent']}")
        if p["name"] in names:
            raise ValueError(f"{name}: duplicate part {p['name']}")
        names.add(p["name"])


def build_all(models, variants):
    methods = []
    layouts = {}
    for name, model in models.items():
        check(name, model)
        uv, tw, th = pack(model)
        layouts[name] = (uv, tw, th)
        methods.append(java_for(name, model, uv, tw, th))
    java = f"""package com.slfrontier.client.model;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * Entity geometry. GENERATED by tools/gen_assets.py from tools/models.py — edit the
 * spec and re-run the script instead of editing this file.
 */
@SuppressWarnings("unused")
public final class ModelDefs {{
    private ModelDefs() {{
    }}

{chr(10).join(methods)}
}}
"""
    with open(JAVA_OUT, "w") as f:
        f.write(java)
    glowing = []
    for model_name, tex_name, palette in variants:
        uv, tw, th = layouts[model_name]
        img = Image.new("RGBA", (tw * RES, th * RES), (0, 0, 0, 0))
        glow = Image.new("RGBA", (tw * RES, th * RES), (0, 0, 0, 0))
        seed = int(hashlib.md5(tex_name.encode()).hexdigest()[:6], 16)
        rnd = random.Random(seed)
        for p in models[model_name]:
            for i, c in enumerate(p["cubes"]):
                u, v = uv[(p["name"], i)]
                paint_box(img, glow, u, v, c, palette, seed, rnd)
        path = os.path.join(ASSETS, f"textures/entity/{tex_name}.png")
        os.makedirs(os.path.dirname(path), exist_ok=True)
        img.save(path)
        gpath = os.path.join(ASSETS, f"textures/entity/{tex_name}_glow.png")
        if glow.getbbox():
            glow.save(gpath)
            glowing.append(tex_name)
        elif os.path.exists(gpath):
            os.remove(gpath)
    return layouts, glowing

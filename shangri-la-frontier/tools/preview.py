"""Quick software renders of generated models, for iterating without launching Minecraft.

  python3 tools/preview.py items  out.png     # every 3D weapon model, front + 3/4 view
  python3 tools/preview.py mobs   out.png     # every creature in tools/models.py
  python3 tools/preview.py structure out.png town/firstia   # an isometric view of a template
  python3 tools/preview.py check town/firstia               # audit a template (water, supports, roads)

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
#
#   python3 tools/preview.py structure out.png town/firstia
#   python3 tools/preview.py check town/firstia            # water / support / road audit
#
# Environment knobs for "structure":
#   PREVIEW_SCALE=4              pixels per block
#   PREVIEW_ROT=0..3             view from the SE (0), SW (1), NW (2) or NE (3) corner
#   PREVIEW_CROP=x0,z0,x1,z1     only this part of the footprint (template-local, inclusive)
#   PREVIEW_YMIN / PREVIEW_YMAX  cut the template above/below (YMAX shows interiors)
#   PREVIEW_BG=r,g,b             background colour
#   MC_RESOURCES_JAR=path        vanilla client resources jar; block colours then come from the
#                                real textures (auto-detected under build/moddev/artifacts)

import glob
import io
import zipfile

# Fallback colours (substring match, first hit wins) when no resources jar is available.
BLOCK_COLORS = [
    ("cherry_leaves", (232, 168, 200)), ("spruce_leaves", (52, 84, 52)), ("azalea_leaves", (90, 125, 45)),
    ("leaves", (64, 118, 44)), ("moss", (89, 110, 45)), ("vine", (60, 110, 40)), ("lily_pad", (40, 120, 40)),
    ("grass_block", (98, 162, 62)), ("short_grass", (98, 162, 62)), ("fern", (80, 140, 60)), ("pink_petals", (240, 170, 200)),
    ("bamboo", (130, 160, 60)), ("water", (58, 92, 204)), ("lava", (230, 110, 30)),
    ("red_sandstone", (186, 99, 29)), ("chiseled_sandstone", (205, 188, 138)), ("smooth_sandstone", (224, 212, 164)),
    ("cut_sandstone", (216, 202, 152)), ("sandstone", (214, 200, 150)), ("red_sand", (190, 100, 40)), ("sand", (219, 207, 163)),
    ("gravel", (130, 125, 122)), ("dirt_path", (148, 122, 65)), ("coarse_dirt", (119, 85, 59)), ("rooted_dirt", (144, 103, 76)),
    ("podzol", (92, 64, 30)), ("farmland", (100, 70, 40)),
    ("mud_brick", (137, 103, 79)), ("packed_mud", (142, 106, 79)), ("mud", (60, 57, 60)), ("dirt", (134, 96, 67)),
    ("white_concrete", (207, 213, 214)), ("calcite", (223, 224, 220)), ("quartz", (235, 229, 222)), ("diorite", (188, 188, 188)),
    ("bone", (229, 225, 207)), ("skull", (200, 200, 190)),
    ("orange_terracotta", (160, 83, 37)), ("red_terracotta", (143, 61, 46)), ("white_terracotta", (209, 178, 161)),
    ("yellow_terracotta", (186, 133, 35)), ("cyan_terracotta", (86, 91, 91)), ("terracotta", (152, 94, 67)),
    ("red_concrete", (142, 32, 32)), ("black", (25, 25, 30)), ("gray_wool", (62, 68, 71)), ("brown_wool", (114, 71, 40)),
    ("light_gray_wool", (142, 142, 134)), ("cyan_wool", (21, 137, 145)), ("purple", (122, 42, 172)), ("green_wool", (84, 109, 27)),
    ("acacia", (168, 90, 50)), ("oxidized", (82, 162, 132)), ("weathered", (108, 153, 110)), ("copper", (192, 107, 79)),
    ("prismarine", (70, 120, 110)), ("warped", (43, 104, 99)), ("crimson", (101, 48, 70)),
    ("deepslate_tile", (54, 54, 56)), ("deepslate", (80, 80, 82)), ("blackstone", (42, 36, 40)), ("tuff", (108, 109, 102)),
    ("dark_oak", (66, 43, 20)), ("spruce", (114, 84, 48)), ("birch", (192, 175, 121)), ("cherry", (226, 178, 172)),
    ("mangrove", (117, 54, 48)), ("jungle", (160, 115, 80)), ("oak", (162, 130, 78)),
    ("mossy", (100, 120, 90)), ("cracked_stone", (118, 117, 118)), ("chiseled_stone", (120, 119, 120)),
    ("stone_brick", (122, 121, 122)), ("cobblestone", (110, 110, 110)), ("andesite", (136, 136, 137)),
    ("smooth_stone", (158, 158, 158)), ("granite", (149, 103, 85)),
    ("stone", (125, 125, 125)), ("bricks", (150, 97, 83)), ("brick", (150, 97, 83)),
    ("glass", (200, 222, 232)), ("lantern", (255, 200, 100)), ("torch", (255, 210, 110)), ("campfire", (230, 150, 60)),
    ("gold", (240, 200, 60)), ("iron", (140, 140, 140)), ("chain", (60, 60, 70)), ("anvil", (70, 70, 70)),
    ("red_wool", (160, 39, 34)), ("red_carpet", (160, 39, 34)), ("yellow", (248, 197, 39)), ("blue", (53, 57, 157)),
    ("white_wool", (233, 236, 236)), ("white_carpet", (233, 236, 236)), ("orange", (240, 118, 19)), ("lime", (112, 185, 25)),
    ("pink", (237, 141, 172)), ("magenta", (189, 68, 179)), ("cyan", (21, 137, 145)), ("green", (84, 109, 27)),
    ("brown", (114, 71, 40)), ("white", (233, 236, 236)), ("red", (160, 39, 34)),
    ("banner", (180, 40, 40)), ("bed", (180, 40, 40)), ("hay", (190, 160, 40)), ("carrots", (230, 130, 30)),
    ("wheat", (200, 180, 80)), ("pumpkin", (198, 118, 24)), ("melon", (110, 145, 30)), ("barrel", (120, 88, 50)),
    ("chest", (160, 115, 50)), ("bookshelf", (120, 90, 60)), ("flower_pot", (124, 68, 52)), ("potted", (124, 68, 52)),
    ("dripstone", (130, 100, 85)), ("waypoint", (120, 230, 255)), ("structure_block", (255, 0, 255)),
    ("frontier_forge", (96, 72, 64)), ("alchemy_table", (128, 70, 150)), ("frontier_workbench", (170, 125, 70)),
    ("lightning_rod", (200, 120, 90)), ("end_rod", (240, 236, 220)), ("bell", (230, 190, 60)),
]


def block_color(name):
    n = name.split(":")[-1]
    for key, col in BLOCK_COLORS:
        if key in n:
            return col
    return (150, 150, 150)


# -- colours from the real textures --------------------------------------------------------

TINTS = {"grass_block_top": (145, 189, 89), "short_grass": (145, 189, 89), "tall_grass": (145, 189, 89),
         "fern": (145, 189, 89), "large_fern": (145, 189, 89), "grass_block_side_overlay": (145, 189, 89),
         "oak_leaves": (119, 171, 47), "dark_oak_leaves": (119, 171, 47), "jungle_leaves": (119, 171, 47),
         "acacia_leaves": (119, 171, 47), "mangrove_leaves": (146, 193, 98), "spruce_leaves": (97, 153, 97),
         "birch_leaves": (128, 167, 85), "vine": (119, 171, 47), "lily_pad": (32, 128, 48), "water_still": (63, 118, 228),
         "water_flow": (63, 118, 228), "sugar_cane": (145, 189, 89)}
# Blocks drawn by block-entity renderers: borrow a texture that looks like them.
TEXTURE_ALIAS = {"_bed": "{color}_wool", "_banner": "{color}_wool", "_wall_banner": "{color}_wool",
                 "skeleton_skull": "bone_block_side", "skeleton_wall_skull": "bone_block_side",
                 "wither_skeleton_skull": "blackstone", "zombie_head": "green_wool", "chest": "oak_planks",
                 "trapped_chest": "oak_planks", "ender_chest": "obsidian", "bell": "gold_block", "water": "water_still",
                 "lava": "lava_still", "decorated_pot": "terracotta", "conduit": "prismarine"}
COLORS16 = ["white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan", "purple",
            "blue", "brown", "green", "red", "black"]


class TextureColours:
    def __init__(self, jar):
        self.zip = zipfile.ZipFile(jar)
        self.names = set(self.zip.namelist())
        self.tex_cache, self.model_cache, self.block_cache = {}, {}, {}

    def _json(self, path):
        return json.loads(self.zip.read(path)) if path in self.names else None

    def tex(self, tid):
        tid = tid.split(":")[-1]
        if not tid.startswith("block/"):
            tid = "block/" + tid
        if tid in self.tex_cache:
            return self.tex_cache[tid]
        path = f"assets/minecraft/textures/{tid}.png"
        col = None
        if path in self.names:
            img = Image.open(io.BytesIO(self.zip.read(path))).convert("RGBA")
            w, h = img.size
            img = img.crop((0, 0, w, min(w, h)))
            px = [p for p in img.getdata() if p[3] > 90]
            if px:
                col = tuple(sum(p[k] for p in px) // len(px) for k in range(3))
                short = tid.split("/")[-1]
                tint = TINTS.get(short)
                if tint:
                    col = tuple(min(255, c * tc // 200) for c, tc in zip(col, tint))
        self.tex_cache[tid] = col
        return col

    def model_textures(self, mid):
        mid = mid.split(":")[-1]
        if mid in self.model_cache:
            return self.model_cache[mid]
        tex = {}
        chain = []
        cur = mid
        while cur:
            m = self._json(f"assets/minecraft/models/{cur.split(':')[-1]}.json")
            if not m:
                break
            chain.append(m.get("textures", {}))
            cur = m.get("parent")
        for t in reversed(chain):
            tex.update(t)
        for _ in range(4):
            for k, v in list(tex.items()):
                if isinstance(v, str) and v.startswith("#"):
                    tex[k] = tex.get(v[1:], v)
        self.model_cache[mid] = tex
        return tex

    def faces(self, name, props):
        """{"up": rgb, "side": rgb, "end": rgb} for a block state, or None."""
        key = (name, tuple(sorted((props or {}).items())))
        if key in self.block_cache:
            return self.block_cache[key]
        n = name.split(":")[-1]
        out = None
        alias = None
        for suffix, pattern in TEXTURE_ALIAS.items():
            if n == suffix or (suffix.startswith("_") and n.endswith(suffix)):
                color = next((c for c in COLORS16 if n.startswith(c + "_")), "white")
                alias = pattern.format(color=color)
                break
        if alias:
            c = self.tex(alias)
            out = {"up": c, "side": c, "end": c} if c else None
        else:
            bs = self._json(f"assets/minecraft/blockstates/{n}.json")
            model = None
            if bs and "variants" in bs:
                best, score = None, -1
                for vk, vv in bs["variants"].items():
                    conds = dict(p.split("=") for p in vk.split(",") if "=" in p)
                    s = sum(1 for k2, v2 in conds.items() if (props or {}).get(k2) == v2)
                    if s > score:
                        best, score = vv, s
                best = best[0] if isinstance(best, list) else best
                model = best and best.get("model")
            elif bs and "multipart" in bs:
                ap = bs["multipart"][0]["apply"]
                model = (ap[0] if isinstance(ap, list) else ap).get("model")
            if model:
                tx = self.model_textures(model)

                def pick(*keys):
                    for k in keys:
                        if k in tx and not str(tx[k]).startswith("#"):
                            c = self.tex(tx[k])
                            if c:
                                return c
                    return None
                up = pick("up", "top", "end", "all", "texture", "wall", "cross", "plant", "pane", "lantern", "particle")
                side = pick("north", "side", "all", "texture", "front", "wall", "cross", "plant", "pane", "edge", "lantern",
                            "particle")
                end = pick("end", "up", "top") or up
                if up or side:
                    out = {"up": up or side, "side": side or up, "end": end or up or side}
        self.block_cache[key] = out
        return out


def _find_jar():
    jar = os.environ.get("MC_RESOURCES_JAR")
    if jar and os.path.exists(jar):
        return jar
    for pat in (os.path.join(ROOT, "build/moddev/artifacts/*client-extra*.jar"),
                os.path.join(ROOT, "../../shangri-la-frontier/build/moddev/artifacts/*client-extra*.jar")):
        hits = glob.glob(pat)
        if hits:
            return hits[0]
    return None


# -- block shapes ------------------------------------------------------------------------

PLANTS = ("short_grass", "tall_grass", "fern", "dandelion", "poppy", "orchid", "allium", "azure_bluet", "tulip", "daisy",
          "cornflower", "lily_of_the_valley", "sapling", "dead_bush", "wheat", "carrots", "potatoes", "beetroots",
          "sweet_berry", "torchflower", "pitcher", "mushroom", "wither_rose", "sunflower", "lilac", "peony", "rose_bush",
          "bamboo_sapling", "propagule", "spider_lily", "cobweb", "azalea", "sugar_cane")
FULL_NOT = ("air", "structure_void")


def _connects(nb_name):
    if not nb_name:
        return False
    n = nb_name.split(":")[-1]
    return not any(s in n for s in ("_stairs", "_slab", "door", "lantern", "torch", "_sign", "banner", "carpet", "pressure",
                                    "button", "flower", "potted", "chain", "_bed", "skull", "head", "candle", "rail"))


def shapes(name, props, nb):
    """Boxes (x0, y0, z0, x1, y1, z1) in block units for a block state. nb(dx, dz) -> neighbour name."""
    n = name.split(":")[-1]
    p = props or {}
    f = p.get("facing")
    if n in ("water", "lava"):
        return [(0, 0, 0, 1, 0.875, 1)]
    if n.endswith("_stairs"):
        top = p.get("half") == "top"
        slab = (0, 0.5, 0, 1, 1, 1) if top else (0, 0, 0, 1, 0.5, 1)
        y0, y1 = (0, 0.5) if top else (0.5, 1)
        back = {"north": (0, y0, 0, 1, y1, 0.5), "south": (0, y0, 0.5, 1, y1, 1),
                "west": (0, y0, 0, 0.5, y1, 1), "east": (0.5, y0, 0, 1, y1, 1)}.get(f, (0, y0, 0, 1, y1, 1))
        return [slab, back]
    if n.endswith("_slab"):
        t = p.get("type", "bottom")
        return [(0, 0, 0, 1, 1, 1)] if t == "double" else [(0, 0.5, 0, 1, 1, 1)] if t == "top" else [(0, 0, 0, 1, 0.5, 1)]
    if "carpet" in n or n.endswith("pressure_plate") or n in ("pink_petals", "lily_pad", "rail", "snow"):
        return [(0, 0, 0, 1, 0.07, 1)]
    if n.endswith("_trapdoor"):
        if p.get("open") == "true":
            return [{"north": (0, 0, 0.81, 1, 1, 1), "south": (0, 0, 0, 1, 1, 0.19),
                     "west": (0.81, 0, 0, 1, 1, 1), "east": (0, 0, 0, 0.19, 1, 1)}.get(f, (0, 0, 0, 1, 1, 0.19))]
        return [(0, 0.81, 0, 1, 1, 1)] if p.get("half") == "top" else [(0, 0, 0, 1, 0.19, 1)]
    if n.endswith("_door"):
        return [(0, 0, 0.4, 1, 1, 0.6)] if f in ("north", "south") else [(0.4, 0, 0, 0.6, 1, 1)]
    if n.endswith("_fence_gate"):
        return [(0, 0.3, 0.44, 1, 1, 0.56)] if f in ("north", "south") else [(0.44, 0.3, 0, 0.56, 1, 1)]
    thin = None
    if n.endswith("_fence"):
        thin, arm, arm_y = 0.125, 0.06, (0.4, 0.9)
    elif n.endswith("_wall") and not any(s in n for s in ("banner", "sign", "torch", "skull", "head", "fan")):
        thin, arm, arm_y = 0.25, 0.19, (0, 0.81)
    elif n == "iron_bars" or n.endswith("glass_pane"):
        thin, arm, arm_y = 0.06, 0.06, (0, 1)
    if thin is not None:
        c0, c1 = 0.5 - thin, 0.5 + thin
        out = [(c0, 0, c0, c1, 1, c1)]
        for (dx, dz) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if _connects(nb(dx, dz)):
                a0, a1 = 0.5 - arm, 0.5 + arm
                if dx:
                    out.append((0.5 if dx > 0 else 0, arm_y[0], a0, 1 if dx > 0 else 0.5, arm_y[1], a1))
                else:
                    out.append((a0, arm_y[0], 0.5 if dz > 0 else 0, a1, arm_y[1], 1 if dz > 0 else 0.5))
        return out
    if "lantern" in n:
        return [(0.31, 0.12, 0.31, 0.69, 0.62, 0.69)] if p.get("hanging") == "true" else [(0.31, 0, 0.31, 0.69, 0.5, 0.69)]
    if n == "chain" or "torch" in n or n in ("end_rod", "lightning_rod", "pointed_dripstone", "brewing_stand"):
        ax = p.get("axis") or ("y" if f in (None, "up", "down") else "x" if f in ("east", "west") else "z")
        if "wall_torch" in n:
            return [{"north": (0.44, 0.2, 0.6, 0.56, 0.9, 0.9), "south": (0.44, 0.2, 0.1, 0.56, 0.9, 0.4),
                     "west": (0.6, 0.2, 0.44, 0.9, 0.9, 0.56), "east": (0.1, 0.2, 0.44, 0.4, 0.9, 0.56)}.get(f)]
        return [{"y": (0.42, 0, 0.42, 0.58, 1, 0.58), "x": (0, 0.42, 0.42, 1, 0.58, 0.58),
                 "z": (0.42, 0.42, 0, 0.58, 0.58, 1)}[ax]]
    if n.endswith("_bed"):
        return [(0, 0, 0, 1, 0.56, 1)]
    if n.endswith("_wall_sign") or n.endswith("_wall_banner") or n == "ladder" or n.endswith("wall_hanging_sign"):
        if n.endswith("wall_hanging_sign"):
            return [(0, 0, 0.44, 1, 0.8, 0.56)] if f in ("north", "south") else [(0.44, 0, 0, 0.56, 0.8, 1)]
        return [{"north": (0, 0, 0.87, 1, 1, 1), "south": (0, 0, 0, 1, 1, 0.13),
                 "west": (0.87, 0, 0, 1, 1, 1), "east": (0, 0, 0, 0.13, 1, 1)}.get(f, (0, 0, 0, 1, 1, 0.13))]
    if n.endswith("_sign") or n.endswith("_banner"):
        return [(0.44, 0, 0.1, 0.56, 1, 0.9)]
    if "skull" in n or n.endswith("_head"):
        return [(0.25, 0, 0.25, 0.75, 0.5, 0.75)]
    if n.startswith("potted_") or n == "flower_pot":
        out = [(0.31, 0, 0.31, 0.69, 0.375, 0.69)]
        if n.startswith("potted_"):
            out.append((0.36, 0.375, 0.36, 0.64, 0.85, 0.64))
        return out
    if n == "campfire" or n == "soul_campfire":
        return [(0, 0, 0, 1, 0.44, 1)]
    if "candle" in n and "cake" not in n:
        return [(0.4, 0, 0.4, 0.6, 0.45, 0.6)]
    if n.endswith("_button"):
        return [(0.3, 0, 0.3, 0.7, 0.15, 0.7)]
    if n == "bell":
        return [(0.25, 0.2, 0.25, 0.75, 0.85, 0.75)]
    if n in ("chest", "trapped_chest", "ender_chest"):
        return [(0.06, 0, 0.06, 0.94, 0.875, 0.94)]
    if n == "cake":
        return [(0.06, 0, 0.06, 0.94, 0.5, 0.94)]
    if n in ("dirt_path", "farmland"):
        return [(0, 0, 0, 1, 0.94, 1)]
    if n in ("stonecutter",):
        return [(0, 0, 0, 1, 0.56, 1)]
    if n == "lectern":
        return [(0.2, 0, 0.2, 0.8, 0.9, 0.8)]
    if n in ("anvil", "chipped_anvil", "damaged_anvil", "grindstone"):
        return [(0.12, 0, 0.12, 0.88, 1, 0.88)]
    if n == "decorated_pot":
        return [(0.12, 0, 0.12, 0.88, 1, 0.88)]
    if any(k in n for k in PLANTS) or n.endswith("_flower"):
        return [(0.2, 0, 0.2, 0.8, 0.75, 0.8)]
    return None  # a full cube


def _rot_box(b, k):
    x0, y0, z0, x1, y1, z1 = b
    for _ in range(k):
        # (x, z) -> (1 - z, x)
        x0, z0, x1, z1 = 1 - z1, x0, 1 - z0, x1
    return (x0, y0, z0, x1, y1, z1)


def preview_structure(t, out, scale=3):
    """Isometric render of a structures.Template: top, south and east faces, with real block shapes."""
    rot = int(os.environ.get("PREVIEW_ROT", 0)) % 4
    crop = os.environ.get("PREVIEW_CROP")
    ymin = int(os.environ.get("PREVIEW_YMIN", -999))
    ymax = int(os.environ.get("PREVIEW_YMAX", 9999))
    bg = tuple(int(x) for x in os.environ.get("PREVIEW_BG", "150,170,190").split(","))
    jar = _find_jar()
    tc = TextureColours(jar) if jar else None
    cx0, cz0, cx1, cz1 = 0, 0, t.size[0] - 1, t.size[2] - 1
    if crop:
        cx0, cz0, cx1, cz1 = (int(v) for v in crop.split(","))
    blocks = {}
    for (x, y, z), v in t.blocks.items():
        if v[0] in ("minecraft:air", "minecraft:structure_void") or not (cx0 <= x <= cx1 and cz0 <= z <= cz1):
            continue
        if not (ymin <= y <= ymax):
            continue
        blocks[(x, y, z)] = v
    sx, sz = cx1 - cx0 + 1, cz1 - cz0 + 1
    ys = [p[1] for p in blocks] or [0]
    ylo, yhi = min(ys), max(ys)
    sy = yhi - ylo + 1

    def rotp(x, z):
        x, z = x - cx0, z - cz0
        w, d = sx, sz
        for _ in range(rot):
            x, z, w, d = d - 1 - z, x, d, w
        return x, z

    rw, rd = (sx, sz) if rot % 2 == 0 else (sz, sx)
    full = set()
    items = []
    for (x, y, z), (name, props, _) in blocks.items():
        def nb(dx, dz, x=x, y=y, z=z):
            v = t.blocks.get((x + dx, y, z + dz))
            return v[0] if v and v[0] != "minecraft:air" else None
        s = shapes(name, props, nb)
        rx, rz = rotp(x, z)
        if s is None:
            full.add((rx, y, rz))
            boxes = [(0, 0, 0, 1, 1, 1)]
        else:
            boxes = [_rot_box(b, rot) for b in s if b]
        items.append(((rx, y, rz), name, props, boxes, s is None))
    w = int((rw + rd) * scale * 0.9) + 40
    h = int((rw + rd) * scale * 0.45 + sy * scale) + 40
    img = Image.new("RGB", (w, h), bg)
    draw = ImageDraw.Draw(img)
    ox, oy = rd * scale * 0.9 + 20, (yhi + 1) * scale + 20

    def proj(x, y, z):
        return (ox + (x - z) * scale * 0.9, oy + (x + z) * scale * 0.45 - y * scale)

    items.sort(key=lambda it: (it[0][0] + it[0][2], it[0][1]))
    outline = scale >= 7
    for (x, y, z), name, props, boxes, is_full in items:
        if is_full and (x, y + 1, z) in full and (x, y, z + 1) in full and (x + 1, y, z) in full:
            continue
        faces = tc.faces(name, props) if tc else None
        base = block_color(name)
        up = (faces and faces["up"]) or base
        side = (faces and faces["side"]) or base
        axis = (props or {}).get("axis")
        if axis in ("x", "z") and rot % 2:
            axis = "z" if axis == "x" else "x"
        if axis in ("x", "z") and faces:
            up = side
        jitter = 0.94 + ((x * 73856093 ^ y * 19349663 ^ z * 83492791) & 15) / 128
        for (bx0, by0, bz0, bx1, by1, bz1) in sorted(boxes, key=lambda b: (b[0] + b[2], b[1])):
            X0, Y0, Z0, X1, Y1, Z1 = x + bx0, y + by0, z + bz0, x + bx1, y + by1, z + bz1
            top = not (is_full and (x, y + 1, z) in full)
            south = not (is_full and (x, y, z + 1) in full)
            east = not (is_full and (x + 1, y, z) in full)
            # logs lying along z/x show their rings on the matching face
            south_col = (faces["end"] if faces and axis == "z" else side)
            east_col = (faces["end"] if faces and axis == "x" else side)
            for vis, poly, col, shade in (
                    (top, [proj(X0, Y1, Z0), proj(X1, Y1, Z0), proj(X1, Y1, Z1), proj(X0, Y1, Z1)], up, 1.0),
                    (south, [proj(X0, Y1, Z1), proj(X1, Y1, Z1), proj(X1, Y0, Z1), proj(X0, Y0, Z1)], south_col, 0.8),
                    (east, [proj(X1, Y1, Z0), proj(X1, Y1, Z1), proj(X1, Y0, Z1), proj(X1, Y0, Z0)], east_col, 0.64)):
                if not vis:
                    continue
                c = tuple(max(0, min(255, int(v * shade * jitter))) for v in col)
                if outline:
                    draw.polygon(poly, fill=c, outline=tuple(int(v * 0.7) for v in c))
                else:
                    draw.polygon(poly, fill=c)
    img.save(out)
    print("wrote", out, len(blocks), "blocks", "(textures)" if tc else "(fallback colours)")


def check_states(t, report):
    """Every block state against the vanilla blocks.json report (python3 tools/preview.py check ...)."""
    with open(report) as f:
        defs = json.load(f)
    problems = {}
    for (x, y, z), (name, props, _) in t.blocks.items():
        if not name.startswith("minecraft:"):
            continue
        d = defs.get(name)
        if d is None:
            problems.setdefault(f"unknown block {name}", (x, y, z))
            continue
        allowed = d.get("properties", {})
        for k, v in (props or {}).items():
            if k not in allowed:
                problems.setdefault(f"{name}: no property {k}", (x, y, z))
            elif str(v) not in allowed[k]:
                problems.setdefault(f"{name}: bad value {k}={v}", (x, y, z))
    return [f"{msg} (first at {pos})" for msg, pos in problems.items()]


if __name__ == "__main__":
    kind = sys.argv[1] if len(sys.argv) > 1 else "items"
    out = sys.argv[2] if len(sys.argv) > 2 else f"/tmp/preview_{kind}.png"
    if kind == "items":
        preview_items(out)
    elif kind in ("structure", "check"):
        sys.path.insert(0, os.path.dirname(__file__))
        import structures
        import towns
        import townkit
        names = sys.argv[3:] if kind == "structure" else sys.argv[2:]
        allfns = {**structures.ALL, **towns.ALL}
        for name in names or list(towns.ALL):
            t = allfns[name]()
            if kind == "structure":
                preview_structure(t, out, int(os.environ.get("PREVIEW_SCALE", 3)))
                continue
            problems = townkit.audit(t)
            report = os.environ.get("MC_BLOCKS_REPORT")
            if report and os.path.exists(report):
                problems += check_states(t, report)
            print(f"{name}: {len(t.blocks)} blocks, {len(t.entities)} entities, size {t.size}")
            for p in problems:
                print("   ", p)
    else:
        preview_mobs(out, sys.argv[3:] or None)

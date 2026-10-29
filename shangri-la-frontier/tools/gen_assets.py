"""Generates the mod's art and model geometry.

  python3 tools/gen_assets.py

Outputs:
  * src/main/java/com/slfrontier/client/model/ModelDefs.java  (entity geometry, via tools/creatures.py)
  * textures for entities, items, blocks, armor, effects, GUI icons and the logo

Everything is procedural pixel art so the whole mod can be rebuilt from source.
Swap any PNG for hand-drawn art later; the generator only overwrites when re-run.
"""
import hashlib
import math
import os
import random
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))
from models import MODELS, VARIANTS  # noqa: E402
import crafting_assets  # noqa: E402
import creatures  # noqa: E402
import icons  # noqa: E402
import weapons  # noqa: E402

ROOT = os.path.join(os.path.dirname(__file__), "..")
ASSETS = os.path.join(ROOT, "src/main/resources/assets/slfrontier")


def hex_rgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def shade(rgb, f):
    return tuple(max(0, min(255, int(c * f))) for c in rgb)


def rng_for(name):
    return random.Random(int(hashlib.md5(name.encode()).hexdigest()[:8], 16))


def noisy(rgb, noise, rnd):
    if noise <= 0:
        return rgb
    d = rnd.randint(-noise, noise)
    return tuple(max(0, min(255, c + d)) for c in rgb)


# ---------------------------------------------------------------------------------------
# Box UV layout (Minecraft ModelPart.Cube): faces of a w*h*d box at texture offset (u, v)
# ---------------------------------------------------------------------------------------

def face_rects(u, v, w, h, d):
    return {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h),
        "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h),
        "back": (u + d + w + d, v + d, w, h),
    }


FACE_SHADE = {"top": 1.12, "bottom": 0.72, "front": 1.0, "back": 0.86, "left": 0.92, "right": 0.92}


def paint_box(img, u, v, size, mat, decals, palette, rnd):
    w, h, d = (int(round(s)) for s in size)
    rects = face_rects(u, v, w, h, d)
    base_color, noise = palette.get(mat, ("#FF00FF", 0))
    for face, (fx, fy, fw, fh) in rects.items():
        if fw <= 0 or fh <= 0:
            continue
        for x in range(fw):
            for y in range(fh):
                if base_color is None:
                    img.putpixel((fx + x, fy + y), (0, 0, 0, 0))
                    continue
                c = shade(hex_rgb(base_color), FACE_SHADE[face])
                # Soft vertical gradient on side faces so big boxes don't look flat.
                if face not in ("top", "bottom") and fh > 3:
                    c = shade(c, 1.06 - 0.12 * y / max(1, fh - 1))
                img.putpixel((fx + x, fy + y), noisy(c, noise, rnd) + (255,))
        # Darker outline on the face edges for a pixel-art look.
        if base_color is not None and fw > 2 and fh > 2:
            edge = shade(hex_rgb(base_color), FACE_SHADE[face] * 0.78)
            for x in range(fw):
                for y in (0, fh - 1):
                    if rnd.random() < 0.55:
                        img.putpixel((fx + x, fy + y), edge + (255,))
            for y in range(fh):
                for x in (0, fw - 1):
                    if rnd.random() < 0.55:
                        img.putpixel((fx + x, fy + y), edge + (255,))
    for (face, x0, y0, dw, dh, dmat) in decals:
        fx, fy, fw, fh = rects[face]
        color = palette.get(dmat, ("#FF00FF", 0)) if dmat else (None, 0)
        for x in range(int(x0), int(x0 + dw)):
            for y in range(int(y0), int(y0 + dh)):
                if 0 <= x < fw and 0 <= y < fh:
                    if color[0] is None:
                        img.putpixel((fx + x, fy + y), (0, 0, 0, 0))
                    else:
                        c = shade(hex_rgb(color[0]), FACE_SHADE[face] if face in ("top", "bottom") else 1.0)
                        img.putpixel((fx + x, fy + y), noisy(c, color[1], rnd) + (255,))


# ---------------------------------------------------------------------------------------
# Pixel-art helpers for items and blocks
# ---------------------------------------------------------------------------------------

class Canvas:
    def __init__(self, size=16, seed="x"):
        self.size = size
        self.img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        self.rnd = rng_for(seed)

    def px(self, x, y, color, noise=0):
        if 0 <= x < self.size and 0 <= y < self.size:
            c = hex_rgb(color) if isinstance(color, str) else color
            self.img.putpixel((int(x), int(y)), noisy(c, noise, self.rnd) + (255,))

    def rect(self, x, y, w, h, color, noise=0):
        for i in range(w):
            for j in range(h):
                self.px(x + i, y + j, color, noise)

    def line(self, x0, y0, x1, y1, color, noise=0):
        steps = max(abs(x1 - x0), abs(y1 - y0)) + 1
        for i in range(steps):
            t = i / max(1, steps - 1)
            self.px(round(x0 + (x1 - x0) * t), round(y0 + (y1 - y0) * t), color, noise)

    def circle(self, cx, cy, r, color, noise=0, fill=True):
        for x in range(self.size):
            for y in range(self.size):
                d = math.hypot(x - cx, y - cy)
                if (fill and d <= r) or (not fill and abs(d - r) < 0.6):
                    self.px(x, y, color, noise)

    def outline(self, color="#1A1A1A"):
        src = self.img.copy()
        for x in range(self.size):
            for y in range(self.size):
                if src.getpixel((x, y))[3] == 0:
                    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                        nx, ny = x + dx, y + dy
                        if 0 <= nx < self.size and 0 <= ny < self.size and src.getpixel((nx, ny))[3] > 0:
                            self.img.putpixel((x, y), hex_rgb(color) + (255,))
                            break

    def fill_noise(self, color, noise):
        for x in range(self.size):
            for y in range(self.size):
                self.px(x, y, color, noise)


def item(name, painter, size=16):
    c = Canvas(size, name)
    painter(c)
    c.outline()
    save(c.img, f"textures/item/{name}.png")


def save(img, rel):
    path = os.path.join(ASSETS, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


def generate_items():
    # Weapons are 3D models: see tools/weapons.py.
    weapons.build_all()

    item("fulldive_headset", headset)
    item("holy_water", lambda c: bottle(c, "#9FE2FF", "#F4FBFF"))
    item("reallocation_tonic", lambda c: bottle(c, "#C77DFF", "#F2D8FF"))
    item("rebirth_tearjewel", tearjewel)
    item("vorpal_soul_collar", collar)
    item("seiten_skillbook", lambda c: book(c, "#7FC8F0", "#E8F4FF"))
    item("vorpal_bell", bell)

    item("gray_ore_chunk", lambda c: chunk(c, "#8A8E94", "#C8CCD2"))
    item("quad_beetle_shell", shell)
    item("empire_bee_stinger", stinger)
    item("empire_bee_queen_crown", crown)
    item("mud_digger_fin", fin)
    item("prismatic_shard", shard)
    item("nightslayer_fur", fur)
    item("lycagon_fang", fang)
    item("tombguard_fragment", lambda c: chunk(c, "#3A3F4A", "#7A1E2C"))
    item("goblin_tooth", tooth)
    item("bird_mask", bird_mask_icon)
    item("battlehorn_helm", battlehorn_icon)


def headset(c):
    c.rect(2, 5, 12, 6, "#2A2E3A", 6)
    c.rect(3, 6, 10, 3, "#7FE7FF", 10)
    c.rect(1, 6, 1, 4, "#1A1E28")
    c.rect(14, 6, 1, 4, "#1A1E28")
    c.line(1, 6, 4, 2, "#1A1E28")
    c.line(14, 6, 11, 2, "#1A1E28")
    c.line(4, 2, 11, 2, "#1A1E28")
    c.px(7, 7, "#FFFFFF")
    c.px(12, 9, "#FF4FA0")


def bottle(c, liquid, shine):
    c.rect(6, 2, 4, 2, "#8A6A4A")
    c.rect(7, 4, 2, 2, "#C8E8F0")
    c.circle(8, 10, 4.5, liquid, 10)
    c.px(6, 8, shine)
    c.px(6, 9, shine)


def tearjewel(c):
    for y in range(3, 15):
        half = int(4.5 * math.sin(min(1.0, (y - 3) / 9) * math.pi / 1.2))
        for x in range(8 - half, 8 + half + 1):
            c.px(x, y, "#E05AA0" if x < 8 else "#B03A7A", 8)
    c.px(6, 8, "#FFD8F0")
    c.px(6, 9, "#FFD8F0")


def collar(c):
    c.circle(8, 8, 6, "#6F5BD1", 6, fill=False)
    c.circle(8, 8, 5, "#8E7BE8", 6, fill=False)
    c.rect(7, 12, 3, 3, "#C9A227")
    c.px(8, 13, "#FFF3B0")


def book(c, cover, pages):
    c.rect(3, 2, 10, 12, cover, 8)
    c.rect(12, 3, 1, 10, pages)
    c.rect(3, 2, 1, 12, "#3A6A8A")
    c.line(6, 6, 10, 6, "#FFFFFF")
    c.circle(8, 9, 1.5, "#FFFFFF")


def bell(c):
    for y in range(4, 12):
        half = 2 + (y - 4) // 2
        c.rect(8 - half, y, half * 2, 1, "#E8C86A", 8)
    c.rect(4, 12, 8, 1, "#C9A227")
    c.px(7, 13, "#6F5BD1")
    c.px(8, 13, "#6F5BD1")
    c.line(6, 1, 9, 1, "#F2A7C3")
    c.px(6, 2, "#F2A7C3")
    c.px(9, 2, "#F2A7C3")


def chunk(c, color, spec):
    c.circle(8, 9, 5, color, 12)
    for _ in range(6):
        c.px(c.rnd.randint(5, 11), c.rnd.randint(6, 12), spec)


def shell(c):
    c.circle(8, 8, 6, "#2F4A6B", 10)
    c.line(8, 2, 8, 14, "#16243A")
    c.px(5, 5, "#6F9CC8")
    c.px(5, 6, "#6F9CC8")
    c.px(11, 5, "#6F9CC8")


def stinger(c):
    for i in range(10):
        w = max(0, 3 - i // 3)
        c.rect(3 + i, 12 - i - w // 2, 1, max(1, w), "#2B1D0E" if i < 3 else "#E8B530", 6)
    c.px(13, 2, "#FFFFFF")


def crown(c):
    c.rect(2, 8, 12, 5, "#F2D04A", 10)
    for x in (2, 6, 9, 13):
        c.rect(x, 4, 1, 4, "#F2D04A")
    c.px(4, 10, "#C0283A")
    c.px(8, 10, "#C0283A")
    c.px(11, 10, "#C0283A")


def fin(c):
    for y in range(3, 14):
        w = (y - 2)
        c.rect(3, y, min(w, 10), 1, "#6B7D8C", 10)
    c.line(3, 13, 13, 13, "#C8B8A0")


def shard(c):
    for y in range(2, 15):
        w = 3 - abs(y - 8) // 3
        c.rect(8 - w, y, w * 2, 1, "#7FE7FF" if y < 8 else "#F2A7F0", 10)
    c.px(7, 5, "#FFFFFF")


def fur(c):
    for i in range(6):
        c.line(3 + i * 2, 13, 5 + i * 2, 3 + i % 3, "#0E0B14", 8)
    c.line(4, 12, 12, 12, "#B01E2E")


def fang(c):
    for y in range(2, 15):
        w = max(1, 4 - (y - 2) // 3)
        c.rect(7 - w // 2, y, w, 1, "#E8E4D8", 6)
    c.rect(5, 2, 5, 2, "#B01E2E")


def tooth(c):
    for y in range(4, 13):
        w = max(1, 3 - (y - 4) // 3)
        c.rect(8 - w // 2, y, w, 1, "#E8E4C8", 6)


def bird_mask_icon(c):
    c.circle(7, 8, 5, "#3B7FD9", 8)
    c.rect(11, 8, 4, 2, "#F2C230")
    c.px(14, 9, "#C9A227")
    c.rect(5, 6, 2, 2, "#FFFFFF")
    c.px(6, 7, "#101010")
    c.line(3, 3, 6, 4, "#2A5FA8")


def battlehorn_icon(c):
    c.rect(3, 7, 10, 6, "#2F4A6B", 10)
    c.rect(4, 9, 8, 2, "#16243A")
    c.line(8, 7, 8, 1, "#9FC3E8")
    c.px(7, 2, "#9FC3E8")
    c.line(3, 7, 1, 4, "#9FC3E8")
    c.line(12, 7, 14, 4, "#9FC3E8")


def generate_blocks():
    # Gray ore
    c = Canvas(16, "gray_ore")
    c.fill_noise((125, 125, 125), 10)
    for _ in range(9):
        x, y = c.rnd.randint(1, 13), c.rnd.randint(1, 13)
        c.rect(x, y, 2, 2, "#C8CCD2", 6)
        c.px(x, y, "#ECEFF4")
    save(c.img, "textures/block/gray_ore.png")

    # Red spider lily (cross plant)
    c = Canvas(16, "lily")
    c.line(8, 15, 8, 7, "#3E7A2A")
    c.px(7, 12, "#3E7A2A")
    for a in range(0, 360, 30):
        r = 5
        c.line(8, 6, round(8 + r * math.cos(math.radians(a))), round(6 + r * 0.7 * math.sin(math.radians(a)) - 1), "#C0182A")
    c.circle(8, 6, 1.4, "#E0303A")
    for a in range(15, 360, 60):
        c.px(round(8 + 6 * math.cos(math.radians(a))), round(5 + 4 * math.sin(math.radians(a))), "#FF6A6A")
    save(c.img, "textures/block/red_spider_lily.png")

    # Prismatic crystal (translucent)
    img = Image.new("RGBA", (16, 16))
    rnd = rng_for("crystal")
    for x in range(16):
        for y in range(16):
            t = (x + y) / 30
            col = (int(127 + 100 * t), int(231 - 60 * t), int(255 - 15 * t))
            a = 150 if (x in (0, 15) or y in (0, 15)) else 110
            img.putpixel((x, y), noisy(col, 8, rnd) + (a,))
    for i in range(16):
        img.putpixel((i, i), (255, 255, 255, 180))
    save(img, "textures/block/prismatic_crystal.png")

    c = Canvas(16, "cluster")
    for (x0, h, col) in ((4, 9, "#7FE7FF"), (8, 13, "#C8A0FF"), (11, 8, "#F2A7F0")):
        for y in range(16 - h, 16):
            w = 1 if y < 16 - h + 2 else 2
            c.rect(x0 - w // 2, y, w + 1, 1, col, 10)
        c.px(x0, 16 - h, "#FFFFFF")
    save(c.img, "textures/block/prismatic_cluster.png")

    # Waypoint crystal: bright cyan facets with a white core
    img = Image.new("RGBA", (16, 16))
    rnd = rng_for("waypoint")
    for x in range(16):
        for y in range(16):
            t = abs(x - 7.5) / 8 + y / 32
            col = (int(150 + 90 * (1 - t)), int(230 + 25 * (1 - t)), 255)
            if (x + y) % 5 == 0:
                col = (min(255, col[0] + 40), 255, 255)
            img.putpixel((x, y), noisy(col, 6, rnd) + (255,))
    save(img, "textures/block/waypoint_crystal.png")

    # Nightslayer altar
    c = Canvas(16, "altar_side")
    c.fill_noise((28, 24, 36), 6)
    c.rect(0, 0, 16, 2, "#3A3248")
    c.rect(0, 14, 16, 2, "#3A3248")
    for (x0, y0, x1, y1) in ((4, 4, 8, 11), (8, 11, 12, 4), (6, 7, 10, 7)):
        c.line(x0, y0, x1, y1, "#B01E2E")
    save(c.img, "textures/block/nightslayer_altar_side.png")
    c = Canvas(16, "altar_top")
    c.fill_noise((22, 18, 30), 6)
    c.circle(8, 8, 5.5, "#B01E2E", 0, fill=False)
    c.circle(8, 8, 2, "#FF2A3A")
    for a in range(0, 360, 90):
        c.line(8, 8, round(8 + 7 * math.cos(math.radians(a + 45))), round(8 + 7 * math.sin(math.radians(a + 45))), "#5A1020")
    save(c.img, "textures/block/nightslayer_altar_top.png")

    # Shadow altar (Lightless Barrens): cracked black stone veined with violet light
    c = Canvas(16, "shadow_altar_side")
    c.fill_noise((18, 14, 26), 5)
    c.rect(0, 0, 16, 2, "#2A2238")
    c.rect(0, 14, 16, 2, "#2A2238")
    for (x0, y0, x1, y1) in ((3, 3, 6, 8), (6, 8, 4, 12), (12, 2, 10, 7), (10, 7, 13, 12), (7, 5, 9, 10)):
        c.line(x0, y0, x1, y1, "#B070FF")
    save(c.img, "textures/block/shadow_altar_side.png")
    c = Canvas(16, "shadow_altar_top")
    c.fill_noise((16, 12, 24), 5)
    c.circle(8, 8, 6, "#6A3AA8", 0, fill=False)
    c.circle(8, 8, 3.5, "#2A1440")
    c.circle(8, 8, 1.6, "#D8A0FF")
    for a in range(0, 360, 60):
        c.line(round(8 + 3 * math.cos(math.radians(a))), round(8 + 3 * math.sin(math.radians(a))),
               round(8 + 7 * math.cos(math.radians(a + 20))), round(8 + 7 * math.sin(math.radians(a + 20))), "#8A4AD8")
    save(c.img, "textures/block/shadow_altar_top.png")


def generate_effects():
    c = Canvas(18, "death_penalty")
    c.circle(9, 8, 6, "#D8D8E0", 4)
    c.rect(6, 6, 2, 3, "#1A1A1A")
    c.rect(10, 6, 2, 3, "#1A1A1A")
    c.rect(6, 13, 6, 3, "#D8D8E0")
    c.px(7, 14, "#1A1A1A")
    c.px(10, 14, "#1A1A1A")
    c.outline("#3A3A48")
    save(c.img, "textures/mob_effect/death_penalty.png")
    c = Canvas(18, "drenched")
    for y in range(3, 16):
        w = max(1, int(5 * math.sin((y - 3) / 12 * math.pi)))
        c.rect(9 - w, y, w * 2, 1, "#9FE2FF", 8)
    c.px(7, 9, "#FFFFFF")
    c.outline("#2A6A8A")
    save(c.img, "textures/mob_effect/drenched.png")


def generate_armor():
    # Armor layer textures use the 64x32 biped layout; we only paint the helmet.
    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    rnd = rng_for("bird")
    pal = {"blue": ("#3B7FD9", 10), "beak": ("#F2C230", 6), "eye": ("#FFFFFF", 0), "pupil": ("#101010", 0),
           "dark": ("#2A5FA8", 6)}
    paint_box(img, 0, 0, (8, 8, 8), "blue", [("front", 1, 2, 2, 2, "eye"), ("front", 5, 2, 2, 2, "eye"),
                                              ("front", 2, 3, 1, 1, "pupil"), ("front", 5, 3, 1, 1, "pupil"),
                                              ("front", 3, 4, 2, 3, "beak"), ("top", 0, 0, 8, 2, "dark")], pal, rnd)
    save(img, "textures/models/armor/bird_mask_layer_1.png")
    save(Image.new("RGBA", (64, 32), (0, 0, 0, 0)), "textures/models/armor/bird_mask_layer_2.png")
    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    rnd = rng_for("horn")
    pal = {"shell": ("#2F4A6B", 12), "horn": ("#9FC3E8", 8), "visor": ("#16243A", 4)}
    paint_box(img, 0, 0, (8, 8, 8), "shell", [("front", 0, 3, 8, 2, "visor"), ("front", 3, 0, 2, 3, "horn"),
                                               ("top", 3, 0, 2, 8, "horn")], pal, rnd)
    paint_box(img, 32, 0, (8, 8, 8), "shell", [("front", 0, 2, 8, 6, None), ("top", 3, 2, 2, 4, "horn")], pal, rnd)
    save(img, "textures/models/armor/battlehorn_layer_1.png")
    save(Image.new("RGBA", (64, 32), (0, 0, 0, 0)), "textures/models/armor/battlehorn_layer_2.png")


def generate_logo():
    size = 128
    img = Image.new("RGBA", (size, size), (14, 18, 32, 255))
    rnd = rng_for("logo")
    for y in range(size):
        for x in range(size):
            t = y / size
            img.putpixel((x, y), noisy((int(20 + 30 * t), int(40 + 60 * (1 - t)), int(90 + 80 * (1 - t))), 4, rnd) + (255,))
    # Moon
    for y in range(size):
        for x in range(size):
            d = math.hypot(x - 64, y - 52)
            d2 = math.hypot(x - 78, y - 44)
            if d < 34 and d2 > 30:
                img.putpixel((x, y), (240, 240, 255, 255))
    # Bird-mask silhouette
    for y in range(size):
        for x in range(size):
            if math.hypot(x - 64, y - 96) < 16:
                img.putpixel((x, y), (59, 127, 217, 255))
            if 78 <= x <= 92 and 94 <= y <= 99:
                img.putpixel((x, y), (242, 194, 48, 255))
    for (x, y) in ((58, 92), (59, 92), (58, 93), (59, 93)):
        img.putpixel((x, y), (255, 255, 255, 255))
    os.makedirs(os.path.join(ROOT, "src/main/resources"), exist_ok=True)
    img.save(os.path.join(ROOT, "src/main/resources/slfrontier_logo.png"))


if __name__ == "__main__":
    creatures.build_all(MODELS, VARIANTS)
    generate_items()
    generate_blocks()
    generate_effects()
    generate_armor()
    generate_logo()
    icons.build_all()
    crafting_assets.build_all()
    print("assets generated")

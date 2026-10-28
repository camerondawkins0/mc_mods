"""GUI icons: skills, stats, quest/map markers.

Icons are drawn as vector glyphs at 4x resolution, then downsampled, filled with a
gradient and given a dark outline plus a soft glow so they read on the dark,
holographic panels of the Frontier UI.

Outputs textures/gui/skill/<id>.png (32x32), textures/gui/stat/<id>.png and
textures/gui/icon/<id>.png (16x16).
"""
import math
import os

from PIL import Image, ImageChops, ImageDraw, ImageFilter

ROOT = os.path.join(os.path.dirname(__file__), "..")
ASSETS = os.path.join(ROOT, "src/main/resources/assets/slfrontier")
SS = 4  # supersampling


def hex_rgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


class Glyph:
    """Draw on a supersampled mask with coordinates in final-pixel units."""

    def __init__(self, size):
        self.size = size
        self.mask = Image.new("L", (size * SS, size * SS), 0)
        self.d = ImageDraw.Draw(self.mask)
        self.cut = Image.new("L", (size * SS, size * SS), 0)
        self.dc = ImageDraw.Draw(self.cut)

    def _p(self, pts):
        return [(x * SS, y * SS) for x, y in pts]

    def poly(self, pts, erase=False):
        (self.dc if erase else self.d).polygon(self._p(pts), fill=255)

    def line(self, pts, w, erase=False):
        (self.dc if erase else self.d).line(self._p(pts), fill=255, width=max(1, int(w * SS)), joint="curve")
        for (x, y) in pts[:1] + pts[-1:]:
            r = w * SS / 2
            (self.dc if erase else self.d).ellipse((x * SS - r, y * SS - r, x * SS + r, y * SS + r), fill=255)

    def circle(self, cx, cy, r, erase=False):
        (self.dc if erase else self.d).ellipse(((cx - r) * SS, (cy - r) * SS, (cx + r) * SS, (cy + r) * SS), fill=255)

    def ring(self, cx, cy, r, w, a0=0, a1=360, erase=False):
        (self.dc if erase else self.d).arc(((cx - r) * SS, (cy - r) * SS, (cx + r) * SS, (cy + r) * SS), a0, a1,
                                           fill=255, width=max(1, int(w * SS)))

    def arc_band(self, cx, cy, r_out, r_in, a0, a1, steps=48):
        """A thick arc (crescent band) between two radii, angles in degrees (0 = east, clockwise)."""
        pts = []
        for i in range(steps + 1):
            a = math.radians(a0 + (a1 - a0) * i / steps)
            pts.append((cx + math.cos(a) * r_out, cy + math.sin(a) * r_out))
        for i in range(steps, -1, -1):
            a = math.radians(a0 + (a1 - a0) * i / steps)
            pts.append((cx + math.cos(a) * r_in, cy + math.sin(a) * r_in))
        self.poly(pts)

    def crescent(self, cx, cy, r, dx, dy, r2=None):
        self.circle(cx, cy, r)
        self.circle(cx + dx, cy + dy, r2 or r, erase=True)

    def render(self, c1, c2, glow, outline="#0A1018", angle=90):
        m = ImageChops.subtract(self.mask, self.cut)
        size = self.size
        m = m.resize((size, size), Image.LANCZOS)
        c1, c2, gl, ol = hex_rgb(c1), hex_rgb(c2), hex_rgb(glow), hex_rgb(outline)
        # Gradient fill
        fill = Image.new("RGBA", (size, size))
        ang = math.radians(angle)
        for y in range(size):
            for x in range(size):
                t = ((x - size / 2) * math.cos(ang) + (y - size / 2) * math.sin(ang)) / size + 0.5
                t = max(0.0, min(1.0, t))
                fill.putpixel((x, y), tuple(int(c1[i] + (c2[i] - c1[i]) * t) for i in range(3)) + (255,))
        out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        # Glow
        g = m.filter(ImageFilter.GaussianBlur(size / 14))
        glow_img = Image.new("RGBA", (size, size), gl + (0,))
        glow_img.putalpha(g.point(lambda v: int(v * 0.75)))
        out = Image.alpha_composite(out, glow_img)
        # Outline
        o = m.filter(ImageFilter.MaxFilter(3))
        ol_img = Image.new("RGBA", (size, size), ol + (0,))
        ol_img.putalpha(o.point(lambda v: min(255, int(v * 1.3))))
        out = Image.alpha_composite(out, ol_img)
        fill.putalpha(m)
        out = Image.alpha_composite(out, fill)
        # Crisp alpha so icons stay sharp in the GUI
        a = out.getchannel("A").point(lambda v: 0 if v < 24 else min(255, int(v * 1.15)))
        out.putalpha(a)
        return out


def save(img, rel):
    path = os.path.join(ASSETS, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


# ---------------------------------------------------------------------------------------
# Skill icons (32x32)
# ---------------------------------------------------------------------------------------

CYAN = ("#FFFFFF", "#5FE0FF", "#2A9FD8")
GOLD = ("#FFF6C8", "#F2B530", "#C98A10")
MOON = ("#FFFFFF", "#B8C8FF", "#7FA0FF")
RED = ("#FFE0E0", "#FF4A5A", "#B01E2E")
SKY = ("#FFFFFF", "#8FD8FF", "#3A8FE0")
VIOLET = ("#F2E6FF", "#B57CFF", "#6E2FA8")
GREEN = ("#F0FFE0", "#7FE07F", "#2E9A4A")


def speed_lines(g, x0, x1, ys, w=1.3):
    for y in ys:
        g.line([(x0, y), (x1, y)], w)


def boot(g, x, y, s=1.0):
    g.poly([(x, y), (x + 6 * s, y), (x + 6 * s, y + 6 * s), (x + 10 * s, y + 8 * s), (x + 10 * s, y + 10 * s), (x, y + 10 * s)])


def skill_icons():
    out = {}

    g = Glyph(32)  # Tap Step: a boot dashing with motion lines
    boot(g, 14, 9, 1.2)
    speed_lines(g, 3, 11, (12, 16, 20))
    out["tap_step"] = g.render(*CYAN)

    g = Glyph(32)  # Slide Step: longer streak, two afterimages
    boot(g, 17, 8, 1.25)
    g.poly([(5, 10), (13, 10), (13, 19), (17, 21), (17, 22), (5, 22)])
    g.poly([(7, 12), (11, 12), (11, 20), (7, 20)], erase=True)
    speed_lines(g, 1, 4, (11, 15, 19, 23), 1.1)
    out["slide_step"] = g.render(*SKY)

    g = Glyph(32)  # Drill Piercer: spiral drill point
    g.poly([(4, 28), (22, 10), (28, 4), (24, 14), (10, 28)])
    for t in (0.25, 0.5, 0.72):
        x, y = 4 + 22 * t, 28 - 22 * t
        g.line([(x - 3, y - 3), (x + 3, y + 3)], 1.1, erase=True)
    out["drill_piercer"] = g.render(*CYAN, angle=135)

    g = Glyph(32)  # Glowing Pierce: spear of light with a starburst at the tip
    g.poly([(3, 29), (23, 9), (25, 11), (5, 31)])
    g.poly([(21, 11), (29, 3), (27, 13)])
    for a in range(0, 360, 45):
        r = 5 if a % 90 == 0 else 3
        g.line([(25, 7), (25 + math.cos(math.radians(a)) * r, 7 + math.sin(math.radians(a)) * r)], 1.2)
    out["glowing_pierce"] = g.render(*GOLD, angle=135)

    g = Glyph(32)  # Perfect Parry: two crossing blades with a spark
    g.poly([(4, 26), (22, 8), (25, 7), (24, 10), (6, 28)])
    g.poly([(28, 26), (10, 8), (7, 7), (8, 10), (26, 28)])
    for a in range(0, 360, 60):
        g.line([(16, 16), (16 + math.cos(math.radians(a + 30)) * 6, 16 + math.sin(math.radians(a + 30)) * 6)], 1.4)
    out["perfect_parry"] = g.render(*CYAN)

    g = Glyph(32)  # Repel Counter: shield with a returning arrow
    g.poly([(8, 6), (24, 6), (24, 17), (16, 27), (8, 17)])
    g.poly([(11, 9), (21, 9), (21, 16), (16, 23), (11, 16)], erase=True)
    g.ring(16, 15, 11, 1.6, 200, 330)
    g.poly([(24, 3), (29, 7), (23, 10)])
    out["repel_counter"] = g.render(*RED)

    g = Glyph(32)  # Cyclone Slash: three curved sweeps around a center
    for k in range(3):
        a0 = k * 120
        g.arc_band(16, 16, 13, 9, a0, a0 + 95)
        a = math.radians(a0 + 95)
        g.poly([(16 + math.cos(a) * 14.5, 16 + math.sin(a) * 14.5), (16 + math.cos(a) * 7.5, 16 + math.sin(a) * 7.5),
                (16 + math.cos(a + 0.45) * 11, 16 + math.sin(a + 0.45) * 11)])
    g.circle(16, 16, 3)
    out["cyclone_slash"] = g.render(*RED, angle=45)

    g = Glyph(32)  # Double Jump: two stacked chevrons
    for y in (6, 15):
        g.poly([(5, y + 9), (16, y), (27, y + 9), (27, y + 13), (16, y + 4), (5, y + 13)])
    out["double_jump"] = g.render(*GREEN, angle=-90)

    g = Glyph(32)  # Rabbit Gun: a rabbit head launching forward
    g.circle(19, 19, 7)
    g.poly([(15, 14), (13, 2), (17, 2), (19, 13)])
    g.poly([(21, 13), (23, 2), (27, 3), (24, 15)])
    g.circle(21, 18, 1.3, erase=True)
    speed_lines(g, 2, 10, (14, 19, 24), 1.3)
    out["rabbit_gun"] = g.render(*VIOLET)

    g = Glyph(32)  # Moon Reflection: a crescent wave
    g.crescent(14, 16, 12, 5, -2, 11)
    for i, (x, y) in enumerate(((26, 6), (28, 14), (25, 24))):
        g.circle(x, y, 1.3 - i * 0.2)
    out["moon_reflection"] = g.render(*MOON, angle=45)

    g = Glyph(32)  # Crescent Vorpal: a falling crescent moon over a ground shockwave
    g.crescent(13, 12, 10, 5, -3, 9)
    g.poly([(18, 18), (24, 24), (22, 25)])
    g.arc_band(16, 34, 15, 12, 205, 335)
    g.line([(5, 26), (2, 23)], 1.3)
    g.line([(27, 26), (30, 23)], 1.3)
    out["crescent_vorpal"] = g.render(*VIOLET, angle=45)

    g = Glyph(32)  # Sky Clear: a full-circle cut with a sun breaking through
    g.ring(16, 16, 12, 2.4)
    g.circle(16, 16, 4.5)
    for a in range(0, 360, 45):
        ar = math.radians(a)
        g.line([(16 + math.cos(ar) * 6.5, 16 + math.sin(ar) * 6.5), (16 + math.cos(ar) * 9, 16 + math.sin(ar) * 9)], 1.4)
    g.poly([(1, 18), (31, 13), (31, 15), (1, 20)], erase=True)
    out["sky_clear"] = g.render(*SKY)

    g = Glyph(32)  # Iai (Tombguard katana draw): sheathed blade with a flash
    g.poly([(3, 25), (25, 10), (27, 11), (5, 27)])
    g.poly([(20, 14), (30, 4), (29, 8)])
    g.line([(4, 16), (28, 22)], 1.2)
    out["iaijutsu"] = g.render(*SKY)

    g = Glyph(32)  # Shell Bash / generic heavy strike
    g.poly([(6, 6), (26, 6), (26, 14), (16, 26), (6, 14)])
    g.poly([(10, 9), (22, 9), (22, 13), (16, 21), (10, 13)], erase=True)
    out["generic"] = g.render(*CYAN)

    for k, img in out.items():
        save(img, f"textures/gui/skill/{k}.png")
    return out


# ---------------------------------------------------------------------------------------
# Stat icons (16x16)
# ---------------------------------------------------------------------------------------

def stat_icons():
    g = Glyph(16)  # VIT: heart
    g.circle(5.5, 6, 3.4)
    g.circle(10.5, 6, 3.4)
    g.poly([(2.2, 7), (13.8, 7), (8, 14)])
    save(g.render("#FFD0D0", "#FF4A5A", "#B01E2E"), "textures/gui/stat/vit.png")

    g = Glyph(16)  # STR: fist / sword blade
    g.poly([(3, 13), (11, 5), (13, 3), (12, 6), (4, 14)])
    g.poly([(2, 10), (6, 14), (5, 15), (1, 11)])
    save(g.render("#FFE8C8", "#FF9A3A", "#C9601A", angle=135), "textures/gui/stat/str.png")

    g = Glyph(16)  # AGI: wing
    g.poly([(2, 12), (6, 4), (14, 2), (11, 6), (13, 6), (9, 9), (11, 9), (6, 12)])
    save(g.render("#F0FFE0", "#7FE07F", "#2E9A4A"), "textures/gui/stat/agi.png")

    g = Glyph(16)  # DEX: twin daggers crossed, hilts down
    g.poly([(4, 12), (12, 2), (13, 3), (5, 13)])
    g.poly([(12, 12), (4, 2), (3, 3), (11, 13)])
    g.line([(2, 11), (6, 14)], 1.6)
    g.line([(14, 11), (10, 14)], 1.6)
    save(g.render("#E0F4FF", "#5FE0FF", "#2A9FD8"), "textures/gui/stat/dex.png")

    g = Glyph(16)  # TEC: gear
    for a in range(0, 360, 45):
        ar = math.radians(a)
        g.poly([(8 + math.cos(ar - 0.25) * 4, 8 + math.sin(ar - 0.25) * 4), (8 + math.cos(ar - 0.18) * 7, 8 + math.sin(ar - 0.18) * 7),
                (8 + math.cos(ar + 0.18) * 7, 8 + math.sin(ar + 0.18) * 7), (8 + math.cos(ar + 0.25) * 4, 8 + math.sin(ar + 0.25) * 4)])
    g.circle(8, 8, 4.8)
    g.circle(8, 8, 2, erase=True)
    save(g.render("#EDE4FF", "#A88BFF", "#6E4FD8"), "textures/gui/stat/tec.png")

    g = Glyph(16)  # LUC: four-leaf clover / star
    for (cx, cy) in ((8, 4.5), (11.5, 8), (8, 11.5), (4.5, 8)):
        g.circle(cx, cy, 3)
    save(g.render("#FFF6C8", "#F2C230", "#C98A10"), "textures/gui/stat/luc.png")

    g = Glyph(16)  # STA: lightning bolt
    g.poly([(9, 1), (3, 9), (7.5, 9), (6, 15), (13, 6), (8.5, 6), (10, 1)])
    save(g.render("#E8FFE0", "#9FF05A", "#4E9A1A"), "textures/gui/stat/sta.png")


# ---------------------------------------------------------------------------------------
# UI / map icons (16x16)
# ---------------------------------------------------------------------------------------

def ui_icons():
    g = Glyph(16)  # main quest: star
    pts = []
    for i in range(10):
        a = math.radians(-90 + i * 36)
        r = 7 if i % 2 == 0 else 3
        pts.append((8 + math.cos(a) * r, 8.5 + math.sin(a) * r))
    g.poly(pts)
    save(g.render("#FFF6C8", "#F2B530", "#C98A10"), "textures/gui/icon/main_quest.png")

    g = Glyph(16)  # side quest: exclamation in a diamond
    g.poly([(8, 1), (15, 8), (8, 15), (1, 8)])
    g.poly([(7, 4), (9, 4), (8.6, 9.5), (7.4, 9.5)], erase=True)
    g.circle(8, 11.6, 1.1, erase=True)
    save(g.render("#E0F4FF", "#5FE0FF", "#2A9FD8"), "textures/gui/icon/side_quest.png")

    g = Glyph(16)  # waypoint: floating crystal over a base
    g.poly([(8, 1), (12, 6), (8, 11), (4, 6)])
    g.poly([(3, 13), (13, 13), (11, 15), (5, 15)])
    save(g.render("#FFFFFF", "#7FE7FF", "#2A9FD8"), "textures/gui/icon/waypoint.png")

    g = Glyph(16)  # town: house with a roof
    g.poly([(1, 8), (8, 2), (15, 8)])
    g.poly([(3, 8), (13, 8), (13, 15), (3, 15)])
    g.poly([(7, 11), (9, 11), (9, 15), (7, 15)], erase=True)
    save(g.render("#FFF6E0", "#F2C27A", "#C98A3A"), "textures/gui/icon/town.png")

    g = Glyph(16)  # boss: skull
    g.circle(8, 7, 6)
    g.poly([(4, 10), (12, 10), (11, 15), (5, 15)])
    g.circle(5.6, 7.2, 1.7, erase=True)
    g.circle(10.4, 7.2, 1.7, erase=True)
    g.poly([(7.2, 12), (8.8, 12), (8.8, 15), (7.2, 15)], erase=True)
    save(g.render("#FFE0E0", "#FF4A5A", "#B01E2E"), "textures/gui/icon/boss.png")

    g = Glyph(16)  # target: diamond reticle
    g.ring(8, 8, 6, 1.6)
    g.circle(8, 8, 2)
    for (a, b) in (((8, 0), (8, 4)), ((8, 12), (8, 16)), ((0, 8), (4, 8)), ((12, 8), (16, 8))):
        g.line([a, b], 1.4)
    save(g.render("#FFF6C8", "#F2B530", "#C98A10"), "textures/gui/icon/target.png")

    g = Glyph(16)  # player: arrow pointing up (rotated in code)
    g.poly([(8, 1), (14, 14), (8, 11), (2, 14)])
    save(g.render("#FFFFFF", "#FFE06A", "#F2B530", outline="#1A1A1A"), "textures/gui/icon/player.png")

    g = Glyph(16)  # lock
    g.ring(8, 6, 4, 1.8, 180, 360)
    g.poly([(3, 7), (13, 7), (13, 15), (3, 15)])
    g.circle(8, 10.5, 1.4, erase=True)
    save(g.render("#E0E6F0", "#8A9AB0", "#4A5A70"), "textures/gui/icon/lock.png")

    g = Glyph(16)  # check
    g.line([(2.5, 8.5), (6.5, 12.5), (13.5, 3.5)], 2.6)
    save(g.render("#E8FFE0", "#7FE07F", "#2E9A4A"), "textures/gui/icon/check.png")

    g = Glyph(16)  # pin (custom map marker)
    g.circle(8, 6, 5)
    g.poly([(4, 8), (12, 8), (8, 15)])
    g.circle(8, 6, 2, erase=True)
    save(g.render("#FFE0F0", "#FF6AB0", "#C0306A"), "textures/gui/icon/pin.png")

    g = Glyph(16)  # dungeon / ruins: broken pillar
    g.poly([(3, 3), (13, 3), (13, 5), (3, 5)])
    g.poly([(5, 5), (7, 5), (7, 13), (5, 13)])
    g.poly([(9, 8), (11, 7), (11, 13), (9, 13)])
    g.poly([(2, 13), (14, 13), (14, 15), (2, 15)])
    save(g.render("#F0F0F0", "#B8B8C8", "#6A6A80"), "textures/gui/icon/ruins.png")


def build_all():
    skill_icons()
    stat_icons()
    ui_icons()


if __name__ == "__main__":
    build_all()
    print("icons generated")

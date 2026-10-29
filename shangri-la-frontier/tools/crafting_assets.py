"""Art for Frontier crafting, called from gen_assets.py.

Outputs textures for the crafting materials and consumables (textures/item), the three
station blocks (textures/block) and the crafting screen's GUI icons (textures/gui/icon).
"""
import math

import icons


def build_all():
    # gen_assets is the entry point that imports this module, so borrow its helpers lazily.
    from gen_assets import Canvas, bottle, item, save
    items(item, bottle)
    blocks(Canvas, save)
    gui_icons()


# ---------------------------------------------------------------------------------------
# Items (16x16 pixel art)
# ---------------------------------------------------------------------------------------

def items(item, bottle):
    item("frontier_herb", herb)
    item("gray_steel_ingot", lambda c: ingot(c, "#8C96A4", "#C8D2DE", "#5A6270"))
    item("leather_grip", grip)
    item("whetstone", whetstone)
    item("healing_potion", lambda c: bottle(c, "#E0303A", "#FFC0C0"))
    item("greater_healing_potion", lambda c: big_flask(c, "#B0102A", "#FF8A9A", "#F2C230"))
    item("stamina_draught", lambda c: bottle(c, "#8FE04A", "#EAFFD0"))
    item("vigor_elixir", lambda c: big_flask(c, "#F2A020", "#FFF0B0", "#C9A227"))
    item("antidote", lambda c: tall_flask(c, "#30C8A8", "#D0FFF4"))
    item("nightsight_tonic", lambda c: tall_flask(c, "#3A3FA8", "#C8A0FF"))
    item("ironhide_tonic", lambda c: tall_flask(c, "#5A7FA8", "#D8E8FF"))
    item("bandage", bandage)
    item("trail_ration", ration)


def herb(c):
    c.line(8, 15, 8, 7, "#3E7A2A")
    c.line(8, 12, 5, 9, "#3E7A2A")
    c.line(8, 10, 11, 7, "#3E7A2A")
    for (x, y, col) in ((4, 8, "#5FB83A"), (3, 9, "#4E9A2E"), (5, 8, "#7FD84A"), (11, 6, "#5FB83A"), (12, 6, "#7FD84A"),
                        (12, 7, "#4E9A2E"), (7, 5, "#5FB83A"), (8, 4, "#7FD84A"), (9, 5, "#5FB83A"), (8, 3, "#B8F070"),
                        (8, 6, "#4E9A2E"), (6, 11, "#5FB83A"), (10, 9, "#7FD84A")):
        c.px(x, y, col)
    for (x, y) in ((4, 7), (12, 5), (7, 4), (9, 4)):
        c.px(x, y, "#B8F070")


def ingot(c, base, light, dark):
    for y in range(6, 9):  # top face, narrower toward the back
        inset = 8 - y
        c.rect(3 + inset, y, 10 - inset, 1, light, 6)
    c.rect(2, 9, 12, 4, base, 6)
    c.rect(2, 12, 12, 1, dark)
    c.px(4, 7, "#FFFFFF")
    c.px(5, 7, "#FFFFFF")
    c.line(3, 10, 12, 10, light)


def grip(c):
    # A diagonal roll of hide, wound with darker strips, brass caps at the ends.
    for i in range(9):
        c.rect(3 + i, 10 - i, 3, 3, "#4E2E18" if i % 3 == 0 else "#8A5A32", 8)
    c.line(4, 12, 12, 4, "#B8804A")
    c.rect(2, 12, 2, 2, "#C9A227")
    c.rect(12, 2, 2, 2, "#C9A227")


def whetstone(c):
    for i in range(9):
        c.rect(3 + i, 11 - i, 4, 3, "#7A8088", 8)
    c.line(4, 11, 12, 3, "#B8C0C8")
    c.line(5, 13, 14, 4, "#4A5058")


def big_flask(c, liquid, shine, cork):
    c.rect(6, 1, 4, 2, cork)
    c.rect(7, 3, 2, 2, "#C8E8F0")
    c.circle(8, 10, 5.2, liquid, 8)
    c.circle(8, 10, 2.6, shine, 4)
    c.circle(8, 10, 1.8, liquid, 6)
    c.px(5, 8, "#FFFFFF")
    c.px(11, 12, "#FFFFFF")


def tall_flask(c, liquid, shine):
    c.rect(6, 1, 4, 2, "#8A6A4A")
    c.rect(7, 3, 2, 3, "#C8E8F0")
    c.rect(5, 6, 6, 9, liquid, 8)
    c.rect(5, 6, 6, 1, shine)
    c.px(6, 8, shine)
    c.px(6, 9, shine)


def bandage(c):
    c.circle(7, 8, 5, "#F2EEE6", 4)
    c.circle(7, 8, 3, "#D8D2C8", 2, fill=False)
    c.circle(7, 8, 1.2, "#B8B0A4")
    c.rect(11, 10, 4, 3, "#F2EEE6", 4)
    c.rect(4, 7, 1, 3, "#D0303A")
    c.rect(3, 8, 3, 1, "#D0303A")


def ration(c):
    c.rect(2, 5, 12, 8, "#B88A5A", 10)
    c.rect(2, 5, 12, 1, "#D8AA7A")
    c.line(8, 5, 8, 12, "#6A4A2A")
    c.line(2, 9, 13, 9, "#6A4A2A")
    c.px(9, 4, "#6A4A2A")
    c.px(10, 3, "#6A4A2A")
    c.px(4, 7, "#5FB83A")
    c.px(5, 7, "#7FD84A")


# ---------------------------------------------------------------------------------------
# Blocks
# ---------------------------------------------------------------------------------------

def bricks(c, mortar="#4A4A50", stone=(118, 118, 126)):
    c.fill_noise(stone, 8)
    for y in (0, 4, 8, 12):
        c.rect(0, y, 16, 1, mortar)
        offset = 0 if (y // 4) % 2 == 0 else 4
        for x in range(offset, 16, 8):
            c.rect(x, y, 1, 4, mortar)


def blocks(Canvas, save):
    # Frontier Forge: stone bricks with an iron band, a fire mouth on the front and a grate on top.
    c = Canvas(16, "forge_side")
    bricks(c)
    c.rect(0, 1, 16, 2, "#3A3E46", 4)
    for x in (2, 7, 13):
        c.px(x, 2, "#9AA2AE")
    save(c.img, "textures/block/frontier_forge_side.png")

    c = Canvas(16, "forge_front")
    bricks(c)
    c.rect(0, 1, 16, 2, "#3A3E46", 4)
    mouth = [(x, y) for x in range(3, 13) for y in range(6, 15) if not (y == 6 and x in (3, 4, 11, 12)) and not (y == 7 and x in (3, 12))]
    for (x, y) in mouth:
        c.px(x, y, "#1A0E08", 4)
    for x in range(2, 14):
        if (x, 6) not in mouth and not (x in (2, 13)):
            c.px(x, 5, "#5A5A62")
    c.rect(2, 15, 12, 1, "#3A3E46")
    save(c.img, "textures/block/frontier_forge_front.png")

    img = Canvas(16, "forge_glow")
    for (x, y) in mouth:
        if y >= 11:
            img.px(x, y, "#FF6A1A" if y == 11 else ("#FFB030" if y == 12 else "#FFE080"), 12)
        elif y >= 9 and img.rnd.random() < 0.35:
            img.px(x, y, "#FF4A10", 10)
    save(img.img, "textures/block/frontier_forge_glow.png")

    c = Canvas(16, "forge_top")
    bricks(c)
    c.rect(3, 3, 10, 10, "#1A1A1E")
    for x in range(3, 13, 2):
        c.rect(x, 3, 1, 10, "#4A4E58")
    c.rect(3, 3, 10, 1, "#5A5E68")
    c.rect(3, 12, 10, 1, "#5A5E68")
    save(c.img, "textures/block/frontier_forge_top.png")

    img = Canvas(16, "forge_glow_top")
    for x in range(4, 13, 2):
        for y in range(4, 12):
            if img.rnd.random() < 0.7:
                img.px(x, y, "#FF6A1A" if img.rnd.random() < 0.6 else "#FFB030", 14)
    save(img.img, "textures/block/frontier_forge_glow_top.png")

    # Alchemy Table: dark wood, a faint violet sigil on top, a trimmed edge.
    c = Canvas(16, "alchemy_top")
    c.fill_noise((74, 52, 36), 6)
    for y in (3, 7, 11, 15):
        c.rect(0, y, 16, 1, "#3A2618")
    c.circle(8, 8, 5.5, "#8E5AD8", 0, fill=False)
    for a in range(0, 360, 120):
        c.line(round(8 + 5 * math.cos(math.radians(a - 90))), round(8 + 5 * math.sin(math.radians(a - 90))),
               round(8 + 5 * math.cos(math.radians(a + 30))), round(8 + 5 * math.sin(math.radians(a + 30))), "#6E3FB8")
    c.px(4, 12, "#3E6A2A")
    c.px(12, 3, "#5A2A6A")
    save(c.img, "textures/block/alchemy_table_top.png")

    c = Canvas(16, "alchemy_side")
    c.fill_noise((74, 52, 36), 6)
    c.rect(0, 3, 16, 1, "#B08A4A")
    c.rect(0, 5, 16, 1, "#3A2618")
    for x in (0, 15):
        c.rect(x, 0, 1, 16, "#3A2618")
    save(c.img, "textures/block/alchemy_table_side.png")

    c = Canvas(16, "alchemy_flask")
    for (x0, w, liquid, top) in ((0, 4, "#B57CFF", "#E8D0FF"), (4, 2, "#7FE07F", "#D8FFD0"), (8, 2, "#F2B530", "#FFF0B0"),
                                 (12, 4, "#7FE7FF", "#E0FAFF")):
        c.rect(x0, 0, w, 16, liquid, 10)
        c.rect(x0, 0, w, 1, top)
    save(c.img, "textures/block/alchemy_flask.png")

    # Frontier Workbench: spruce planks, a leather mat on top, tools racked on the front.
    def planks(seed):
        p = Canvas(16, seed)
        p.fill_noise((122, 90, 58), 7)
        for y in (3, 7, 11, 15):
            p.rect(0, y, 16, 1, "#5A3E24")
        return p

    c = planks("bench_top")
    c.rect(3, 3, 10, 9, "#8A4A2A", 6)
    c.rect(3, 3, 10, 1, "#A86A3E")
    c.line(5, 10, 10, 5, "#9AA2AE")
    c.rect(9, 4, 3, 2, "#5A5E68")
    c.rect(0, 0, 16, 1, "#4A3220")
    c.rect(0, 15, 16, 1, "#4A3220")
    save(c.img, "textures/block/frontier_workbench_top.png")

    c = planks("bench_side")
    c.rect(0, 0, 2, 16, "#4A3220")
    c.rect(14, 0, 2, 16, "#4A3220")
    c.rect(0, 0, 16, 2, "#6A4A2E")
    save(c.img, "textures/block/frontier_workbench_side.png")

    c = planks("bench_front")
    c.rect(0, 0, 2, 16, "#4A3220")
    c.rect(14, 0, 2, 16, "#4A3220")
    c.rect(0, 0, 16, 2, "#6A4A2E")
    c.rect(2, 4, 12, 1, "#3A2618")
    # Hammer, saw and a pouch hanging from the rack.
    c.rect(4, 5, 1, 7, "#6A4A2E")
    c.rect(3, 5, 3, 2, "#7A8088")
    c.rect(7, 5, 4, 6, "#B8C0C8", 4)
    for y in range(6, 11, 2):
        c.px(10, y, "#7A8088")
    c.rect(7, 4, 4, 1, "#4E2E18")
    c.rect(11, 6, 2, 4, "#8A4A2A")
    c.px(12, 5, "#C9A227")
    save(c.img, "textures/block/frontier_workbench_front.png")


# ---------------------------------------------------------------------------------------
# GUI icons (16x16 glyphs, same look as tools/icons.py)
# ---------------------------------------------------------------------------------------

CYAN = ("#FFFFFF", "#5FE0FF", "#2A9FD8")
FORGE = ("#FFE8C8", "#FF9A3A", "#C9601A")
ALCHEMY = ("#F2E6FF", "#B57CFF", "#6E2FA8")
WORKBENCH = ("#F0FFE0", "#7FE07F", "#2E9A4A")


def gui_icons():
    G = icons.Glyph
    out = "textures/gui/icon/"

    g = G(16)  # all: four tiles
    for (x, y) in ((2, 2), (9, 2), (2, 9), (9, 9)):
        g.poly([(x, y), (x + 5, y), (x + 5, y + 5), (x, y + 5)])
    icons.save(g.render(*CYAN), out + "cat_all.png")

    g = G(16)  # weapon: a sword
    g.poly([(13.5, 1.5), (14.5, 2.5), (6, 11), (5, 10)])
    g.poly([(3, 9), (4, 8), (8, 12), (7, 13)])
    g.line([(5.5, 10.5), (2, 14)], 1.8)
    icons.save(g.render("#FFE8E8", "#FF7A6A", "#C0303A", angle=135), out + "cat_weapon.png")

    g = G(16)  # armor: a shield
    g.poly([(3, 2), (13, 2), (13, 8), (8, 15), (3, 8)])
    g.line([(8, 4), (8, 12)], 1.2, erase=True)
    g.line([(5, 7), (11, 7)], 1.2, erase=True)
    icons.save(g.render("#E8F0FF", "#8AA8D8", "#3A5A8A"), out + "cat_armor.png")

    g = G(16)  # consumable: a potion
    g.circle(8, 10.5, 4.6)
    g.poly([(6.5, 2), (9.5, 2), (9.5, 7), (6.5, 7)])
    g.poly([(5.5, 1), (10.5, 1), (10.5, 2.6), (5.5, 2.6)])
    g.circle(6.6, 9.6, 1.1, erase=True)
    icons.save(g.render("#FFE0F0", "#FF6AB0", "#C0306A"), out + "cat_consumable.png")

    g = G(16)  # material: an ingot
    g.poly([(4, 5), (12, 5), (15, 11), (1, 11)])
    g.line([(4.5, 7), (11.5, 7)], 1, erase=True)
    icons.save(g.render("#F0F4F8", "#B8C0CC", "#6A7280"), out + "cat_material.png")

    g = G(16)  # gear: a cog
    for i in range(8):
        a = math.radians(i * 45)
        cx, cy = 8 + math.cos(a) * 6, 8 + math.sin(a) * 6
        g.circle(cx, cy, 1.6)
    g.circle(8, 8, 5.4)
    g.circle(8, 8, 2.2, erase=True)
    icons.save(g.render("#FFF6C8", "#F2B530", "#C98A10"), out + "cat_gear.png")

    g = G(16)  # forge: an anvil
    g.poly([(1, 4), (14, 4), (14, 6), (11, 7), (11, 9), (5, 9), (5, 7), (1, 6)])
    g.poly([(6, 9), (10, 9), (10, 11), (6, 11)])
    g.poly([(3, 11), (13, 11), (13, 14), (3, 14)])
    icons.save(g.render(*FORGE), out + "station_forge.png")

    g = G(16)  # alchemy: an erlenmeyer flask with bubbles
    g.poly([(6, 1), (10, 1), (10, 6), (14, 14), (2, 14), (6, 6)])
    g.circle(7, 10.5, 1, erase=True)
    g.circle(9.5, 12, 0.8, erase=True)
    g.circle(12.5, 3, 1.2)
    icons.save(g.render(*ALCHEMY), out + "station_alchemy.png")

    g = G(16)  # workbench: a hammer
    g.poly([(3, 2), (11, 2), (12, 4), (11, 6), (3, 6)])
    g.line([(7, 6), (7, 15)], 2.2)
    icons.save(g.render(*WORKBENCH), out + "station_workbench.png")

    g = G(16)  # hourglass
    g.poly([(3, 1), (13, 1), (13, 3), (9, 8), (13, 13), (13, 15), (3, 15), (3, 13), (7, 8), (3, 3)])
    g.poly([(5, 3.2), (11, 3.2), (8, 6.5)], erase=True)
    icons.save(g.render("#FFF6C8", "#F2C27A", "#C98A3A"), out + "hourglass.png")

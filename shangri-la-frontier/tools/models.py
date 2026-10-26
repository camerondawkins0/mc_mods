"""Creature model specs, shared by the Java code generator and the texture painter.

Coordinates are in pixels with Y pointing UP from the ground and the creature facing
-Z (north), the same way you would build it in Blockbench. Each part has a pivot and
optional rotation (degrees, in Minecraft model space); cubes are (from, size, material,
decals). Decals are (face, x, y, w, h, material) rectangles painted on a cube face,
where face is one of top/bottom/front/back/left/right.
"""

def cube(frm, size, mat, decals=None, inflate=0.0):
    return {"from": frm, "size": size, "mat": mat, "decals": decals or [], "inflate": inflate}


def part(name, parent, pivot, cubes, rot=(0, 0, 0)):
    return {"name": name, "parent": parent, "pivot": pivot, "cubes": cubes, "rot": rot}


VORPAL_BUNNY = [
    part("body", "root", (0, 5, 1), [
        cube((-3, 2, -3), (6, 6, 8), "fur"),
        cube((-3.5, 6, -3.5), (7, 2, 2), "scarf"),
    ]),
    part("head", "body", (0, 7, -3), [
        cube((-2.5, 5, -8), (5, 5, 5), "fur", [("front", 0, 1, 1, 2, "eye"), ("front", 4, 1, 1, 2, "eye"),
                                                ("front", 2, 3, 1, 1, "pink")]),
    ]),
    part("left_ear", "head", (1.5, 10, -5), [cube((0.5, 10, -5.5), (2, 6, 1), "fur", [("front", 0, 1, 2, 4, "pink")])]),
    part("right_ear", "head", (-1.5, 10, -5), [cube((-2.5, 10, -5.5), (2, 6, 1), "fur", [("front", 0, 1, 2, 4, "pink")])]),
    part("front_left_leg", "root", (2, 3, -2), [cube((1, 0, -3), (2, 3, 2), "fur")]),
    part("front_right_leg", "root", (-2, 3, -2), [cube((-3, 0, -3), (2, 3, 2), "fur")]),
    part("back_left_leg", "root", (2, 3, 3), [cube((1, 0, 1), (2, 3, 4), "fur")]),
    part("back_right_leg", "root", (-2, 3, 3), [cube((-3, 0, 1), (2, 3, 4), "fur")]),
    part("tail", "body", (0, 6, 5), [cube((-1.5, 5, 5), (3, 3, 2), "tail")]),
]

RABBITFOLK = [
    part("left_leg", "root", (1.5, 6, 0), [cube((0.5, 0, -1.5), (2, 6, 3), "fur")]),
    part("right_leg", "root", (-1.5, 6, 0), [cube((-2.5, 0, -1.5), (2, 6, 3), "fur")]),
    part("body", "root", (0, 6, 0), [
        cube((-3, 6, -2), (6, 6, 4), "cloth", [("front", 2, 0, 2, 6, "cloth2")]),
        cube((-3.5, 4.5, -2.5), (7, 3, 5), "cloth2"),
    ]),
    part("head", "body", (0, 12, 0), [
        cube((-3.5, 12, -3.5), (7, 6, 7), "fur", [("front", 1, 2, 1, 2, "eye"), ("front", 5, 2, 1, 2, "eye"),
                                                   ("front", 3, 4, 1, 1, "pink"), ("front", 0, 4, 1, 1, "blush"),
                                                   ("front", 6, 4, 1, 1, "blush")]),
        cube((-4, 16.5, -4), (8, 2, 8), "hat"),
    ]),
    part("left_ear", "head", (1.5, 18, 0), [cube((0.5, 18, -0.5), (2, 7, 1), "fur", [("front", 0, 1, 2, 5, "pink")])]),
    part("right_ear", "head", (-1.5, 18, 0), [cube((-2.5, 18, -0.5), (2, 7, 1), "fur", [("front", 0, 1, 2, 5, "pink")])]),
    part("left_arm", "body", (3.5, 11.5, 0), [cube((3, 6, -1), (2, 6, 2), "fur")]),
    part("right_arm", "body", (-3.5, 11.5, 0), [cube((-5, 6, -1), (2, 6, 2), "fur")]),
    part("tail", "body", (0, 7, 2), [cube((-1, 6, 2), (2, 2, 2), "fur")]),
]

EMPIRE_BEE = [
    part("body", "root", (0, 6, 0), [
        cube((-3.5, 3, -3), (7, 7, 10), "yellow", [
            ("top", 0, 3, 7, 2, "black"), ("top", 0, 7, 7, 2, "black"),
            ("left", 3, 0, 2, 7, "black"), ("left", 7, 0, 2, 7, "black"),
            ("right", 3, 0, 2, 7, "black"), ("right", 7, 0, 2, 7, "black"),
            ("bottom", 0, 3, 7, 2, "black"), ("bottom", 0, 7, 7, 2, "black")]),
        cube((-0.5, 5, 7), (1, 1, 3), "black"),
        cube((-2.5, 1, -2), (5, 2, 4), "black"),
    ]),
    part("head", "body", (0, 7, -3), [
        cube((-3, 4, -8), (6, 6, 5), "black", [("front", 0, 1, 2, 3, "eye"), ("front", 4, 1, 2, 3, "eye")]),
        cube((1, 10, -8), (1, 3, 1), "black"),
        cube((-2, 10, -8), (1, 3, 1), "black"),
        cube((-2.5, 10, -7), (5, 2, 4), "crown", [("front", 0, 0, 1, 1, "gem"), ("front", 2, 0, 1, 1, "gem"),
                                                   ("front", 4, 0, 1, 1, "gem")]),
    ]),
    part("left_wing", "body", (1.5, 10, -1), [cube((1.5, 10, -1), (8, 0, 6), "wing")]),
    part("right_wing", "body", (-1.5, 10, -1), [cube((-9.5, 10, -1), (8, 0, 6), "wing")]),
]

QUAD_BEETLE = [
    part("body", "root", (0, 10, 4), [
        cube((-11, 5, -6), (22, 12, 28), "shell", [("top", 10, 0, 2, 28, "dark"), ("top", 4, 4, 3, 10, "shine"),
                                                   ("top", 15, 4, 3, 10, "shine")]),
        cube((-10, 3, -5), (20, 3, 26), "under"),
    ]),
    part("head", "body", (0, 10, -6), [
        cube((-7, 4, -14), (14, 10, 8), "shell2", [("front", 1, 3, 2, 2, "eye"), ("front", 11, 3, 2, 2, "eye")]),
    ]),
    part("horn_main", "head", (0, 10, -14), [cube((-1.5, 8.5, -26), (3, 3, 12), "horn")], rot=(-15, 0, 0)),
    part("horn_up", "head", (0, 14, -12), [cube((-1, 14, -13), (2, 9, 2), "horn")], rot=(-25, 0, 0)),
    part("horn_left", "head", (5, 9, -14), [cube((4.5, 8, -19), (2, 2, 5), "horn")], rot=(0, -20, 0)),
    part("horn_right", "head", (-5, 9, -14), [cube((-6.5, 8, -19), (2, 2, 5), "horn")], rot=(0, 20, 0)),
] + [
    part(f"leg_{side}{i}", "root", (sx * 10.5, 7, z), [cube((sx * 10.5 - (0 if sx > 0 else 3), 0, z - 1), (3, 8, 2), "leg")],
         rot=(0, 0, -20 * sx))
    for i, z in enumerate((-2, 6, 14)) for side, sx in (("l", 1), ("r", -1))
]

MUD_DIGGER = [
    part("body", "root", (0, 12, 6), [
        cube((-12, 4, -6), (24, 17, 24), "fur", [("top", 6, 4, 12, 12, "fur2")]),
        cube((-10, 3, -4), (20, 3, 20), "belly"),
    ]),
    part("dorsal_fin", "body", (0, 21, 4), [cube((-1, 21, 0), (2, 10, 9), "shark")], rot=(-15, 0, 0)),
    part("head", "body", (0, 13, -6), [
        cube((-9, 8, -24), (18, 10, 18), "shark", [("front", 2, 2, 2, 2, "eye"), ("front", 14, 2, 2, 2, "eye"),
                                                   ("left", 3, 3, 1, 4, "gill"), ("left", 5, 3, 1, 4, "gill"),
                                                   ("right", 12, 3, 1, 4, "gill"), ("right", 14, 3, 1, 4, "gill"),
                                                   ("front", 0, 9, 18, 1, "teeth")]),
    ]),
    part("jaw", "head", (0, 8, -8), [cube((-8, 4, -23), (16, 4, 16), "belly", [("front", 0, 0, 16, 1, "teeth"),
                                                                               ("top", 0, 0, 16, 1, "teeth")])]),
    part("tail", "body", (0, 13, 18), [cube((-3, 10, 18), (6, 6, 12), "fur")]),
    part("tail_fin", "tail", (0, 13, 29), [cube((-1, 7, 28), (2, 12, 5), "shark")]),
    part("claw_fl", "root", (11, 7, -2), [cube((9, 0, -8), (7, 7, 9), "fur", [("front", 0, 4, 7, 3, "claw")])]),
    part("claw_fr", "root", (-11, 7, -2), [cube((-16, 0, -8), (7, 7, 9), "fur", [("front", 0, 4, 7, 3, "claw")])]),
    part("leg_bl", "root", (10, 6, 14), [cube((9, 0, 12), (6, 6, 6), "fur")]),
    part("leg_br", "root", (-10, 6, 14), [cube((-15, 0, 12), (6, 6, 6), "fur")]),
]

LYCAGON = [
    part("body", "root", (0, 26, 4), [
        cube((-10, 18, -10), (20, 17, 32), "fur", [("top", 9, 2, 2, 26, "mark"), ("left", 4, 4, 20, 2, "mark"),
                                                   ("right", 8, 4, 20, 2, "mark")]),
    ]),
    part("mane", "body", (0, 28, -10), [cube((-12, 19, -17), (24, 21, 11), "mane", [("front", 10, 14, 4, 6, "mark")])]),
    part("head", "body", (0, 32, -16), [
        cube((-8, 27, -29), (16, 13, 13), "fur", [("front", 2, 4, 4, 2, "eye"), ("front", 10, 4, 4, 2, "eye"),
                                                  ("front", 3, 3, 2, 1, "mark"), ("front", 11, 3, 2, 1, "mark")]),
    ]),
    part("snout", "head", (0, 30, -29), [cube((-4.5, 27, -38), (9, 6, 9), "fur", [("front", 3, 0, 3, 2, "nose"),
                                                                                    ("front", 0, 5, 9, 1, "teeth")])]),
    part("jaw", "head", (0, 28, -29), [cube((-4, 24, -37), (8, 3, 8), "jaw", [("front", 0, 0, 8, 1, "teeth"),
                                                                                ("top", 0, 0, 8, 1, "teeth")])]),
    part("left_ear", "head", (5, 40, -22), [cube((3, 40, -24), (4, 7, 3), "fur", [("front", 1, 1, 2, 5, "mark")])]),
    part("right_ear", "head", (-5, 40, -22), [cube((-7, 40, -24), (4, 7, 3), "fur", [("front", 1, 1, 2, 5, "mark")])]),
    part("front_left_leg", "root", (6.5, 22, -6), [cube((3.5, 0, -9), (6, 22, 6), "fur", [("front", 0, 20, 6, 2, "claw")])]),
    part("front_right_leg", "root", (-6.5, 22, -6), [cube((-9.5, 0, -9), (6, 22, 6), "fur", [("front", 0, 20, 6, 2, "claw")])]),
    part("back_left_leg", "root", (6.5, 22, 16), [cube((3.5, 0, 13), (6, 22, 6), "fur", [("front", 0, 20, 6, 2, "claw")])]),
    part("back_right_leg", "root", (-6.5, 22, 16), [cube((-9.5, 0, 13), (6, 22, 6), "fur", [("front", 0, 20, 6, 2, "claw")])]),
    part("tail", "body", (0, 32, 22), [cube((-3, 29, 22), (6, 6, 18), "mane")], rot=(-35, 0, 0)),
]

WEZAEMON = [
    part("left_leg", "root", (3, 20, 0), [
        cube((1, 0, -3), (5, 20, 6), "under"),
        cube((0.5, 9, -3.5), (6, 11, 7), "armor", [("front", 0, 2, 6, 1, "lacquer"), ("front", 0, 6, 6, 1, "lacquer")]),
        cube((0.5, 0, -4), (6, 3, 8), "armor"),
    ]),
    part("right_leg", "root", (-3, 20, 0), [
        cube((-6, 0, -3), (5, 20, 6), "under"),
        cube((-6.5, 9, -3.5), (6, 11, 7), "armor", [("front", 0, 2, 6, 1, "lacquer"), ("front", 0, 6, 6, 1, "lacquer")]),
        cube((-6.5, 0, -4), (6, 3, 8), "armor"),
    ]),
    part("body", "root", (0, 20, 0), [
        cube((-7, 20, -4), (14, 16, 8), "armor", [("front", 0, 3, 14, 1, "lacquer"), ("front", 0, 7, 14, 1, "lacquer"),
                                                  ("front", 0, 11, 14, 1, "lacquer"), ("front", 6, 0, 2, 16, "gold")]),
        cube((-8, 15, -4.5), (16, 6, 9), "lacquer", [("front", 0, 2, 16, 1, "armor"), ("front", 4, 0, 1, 6, "armor"),
                                                     ("front", 11, 0, 1, 6, "armor")]),
    ]),
    part("head", "body", (0, 36, 0), [
        cube((-4, 36, -4), (8, 8, 8), "bone", [("front", 1, 3, 2, 2, "eye"), ("front", 5, 3, 2, 2, "eye"),
                                               ("front", 3, 5, 2, 1, "dark"), ("front", 1, 6, 6, 1, "dark")]),
        cube((-5, 42, -5), (10, 4, 10), "armor", [("front", 0, 3, 10, 1, "gold")]),
        cube((-6, 38, -1), (12, 5, 7), "armor"),
        cube((-4, 46, -5.5), (8, 4, 1), "gold"),
    ]),
    part("right_arm", "body", (-8, 34, 0), [
        cube((-11, 20, -2.5), (4, 15, 5), "under"),
        cube((-12.5, 29, -3.5), (6, 7, 7), "lacquer", [("front", 0, 2, 6, 1, "gold")]),
    ]),
    part("katana", "right_arm", (-9, 21, 0), [
        cube((-10, 20, -3), (1, 2, 7), "handle"),
        cube((-11, 19, -5), (3, 4, 1), "gold"),
        cube((-10, 20.5, -35), (1, 1, 30), "steel"),
    ]),
    part("left_arm", "body", (8, 34, 0), [
        cube((7, 20, -2.5), (4, 15, 5), "under"),
        cube((6.5, 29, -3.5), (6, 7, 7), "lacquer", [("front", 0, 2, 6, 1, "gold")]),
    ]),
]

MODELS = {
    "vorpal_bunny": VORPAL_BUNNY,
    "rabbitfolk": RABBITFOLK,
    "empire_bee": EMPIRE_BEE,
    "quad_beetle": QUAD_BEETLE,
    "mud_digger": MUD_DIGGER,
    "lycagon": LYCAGON,
    "wezaemon": WEZAEMON,
}

# Texture variants: (model, texture name, palette). Palette maps material -> (hex color, noise).
# A color of None makes the material fully transparent (e.g. the crown on worker bees).
VARIANTS = [
    ("vorpal_bunny", "vorpal_bunny", {
        "fur": ("#F2EEE6", 10), "tail": ("#FFFFFF", 6), "scarf": ("#C0283A", 8), "eye": ("#C0283A", 0),
        "pink": ("#F2A7B8", 0)}),
    ("rabbitfolk", "emul", {
        "fur": ("#EEEAF5", 8), "cloth": ("#6F5BD1", 10), "cloth2": ("#8E7BE8", 8), "pink": ("#F2A7C3", 0),
        "eye": ("#3A2E7A", 0), "blush": ("#F6C2D6", 0), "hat": (None, 0)}),
    ("rabbitfolk", "bilac", {
        "fur": ("#F4E7D2", 8), "cloth": ("#8A4A22", 10), "cloth2": ("#C8692A", 8), "pink": ("#F0A9A0", 0),
        "eye": ("#2E1F12", 0), "blush": ("#F2C4B0", 0), "hat": ("#D0692A", 10)}),
    ("rabbitfolk", "vysache", {
        "fur": ("#DCD6CC", 10), "cloth": ("#7A1E2C", 10), "cloth2": ("#4A0F18", 8), "pink": ("#D9A0A0", 0),
        "eye": ("#C9A227", 0), "blush": ("#DCD6CC", 0), "hat": ("#2A1A1A", 6)}),
    ("empire_bee", "empire_bee", {
        "yellow": ("#E8B530", 12), "black": ("#2B1D0E", 8), "eye": ("#B01E2E", 0), "wing": ("#D8F0FF", 6),
        "crown": (None, 0), "gem": (None, 0)}),
    ("empire_bee", "empire_bee_queen", {
        "yellow": ("#F2C230", 12), "black": ("#3A1408", 8), "eye": ("#E0303A", 0), "wing": ("#FFE8F4", 6),
        "crown": ("#F2D04A", 10), "gem": ("#C0283A", 0)}),
    ("quad_beetle", "quad_beetle", {
        "shell": ("#2F4A6B", 14), "shell2": ("#243A55", 12), "dark": ("#16243A", 6), "shine": ("#6F9CC8", 10),
        "under": ("#1A1F2A", 8), "horn": ("#9FC3E8", 10), "eye": ("#E8F4FF", 0), "leg": ("#1A1F2A", 8)}),
    ("mud_digger", "mud_digger", {
        "fur": ("#5B4632", 16), "fur2": ("#4A3826", 14), "belly": ("#C8B8A0", 10), "shark": ("#6B7D8C", 12),
        "eye": ("#101010", 0), "gill": ("#3E4A55", 0), "teeth": ("#F4F4F0", 0), "claw": ("#E8E4D8", 6)}),
    ("lycagon", "lycagon", {
        "fur": ("#0E0B14", 10), "mane": ("#08060C", 8), "mark": ("#B01E2E", 10), "eye": ("#FF2A3A", 0),
        "nose": ("#1E1E1E", 0), "teeth": ("#E8E4D8", 0), "jaw": ("#1A1420", 8), "claw": ("#CFC8D8", 0)}),
    ("lycagon", "shadow_wolf", {
        "fur": ("#2A2340", 14), "mane": ("#1A1428", 12), "mark": ("#6E2FA8", 10), "eye": ("#B57CFF", 0),
        "nose": ("#101010", 0), "teeth": ("#D8D4E8", 0), "jaw": ("#221C34", 8), "claw": ("#9C94B8", 0)}),
    ("wezaemon", "wezaemon", {
        "armor": ("#3A3F4A", 12), "under": ("#1E2128", 8), "lacquer": ("#7A1E2C", 10), "gold": ("#C9B07A", 8),
        "bone": ("#E6DFCF", 10), "eye": ("#6FD8FF", 0), "dark": ("#2A2620", 0), "handle": ("#2A1A12", 6),
        "steel": ("#D8E4EC", 6)}),
]

# Vanilla-layout (64x64, legacy mirrored limbs) skins for HumanoidModel creatures.
# Each entry: box name -> material, plus decals.
HUMANOID_SKINS = {
    "frontier_guide": {
        "palette": {"skin": ("#F0CDA8", 4), "hair": ("#6B4226", 10), "coat": ("#3B6EA8", 10), "trim": ("#E8C86A", 4),
                    "pants": ("#3A3226", 8), "eye": ("#2A3A6A", 0), "boot": ("#2A1E14", 6), "hat": (None, 0)},
        "head": ("skin", [("top", 0, 0, 8, 8, "hair"), ("front", 0, 0, 8, 2, "hair"), ("left", 0, 0, 8, 4, "hair"),
                          ("right", 0, 0, 8, 4, "hair"), ("back", 0, 0, 8, 6, "hair"),
                          ("front", 1, 4, 2, 1, "eye"), ("front", 5, 4, 2, 1, "eye")]),
        "hat": ("hat", []),
        "body": ("coat", [("front", 3, 0, 2, 12, "trim"), ("front", 0, 8, 8, 1, "trim")]),
        "arm": ("coat", [("bottom", 0, 0, 4, 4, "skin"), ("front", 0, 10, 4, 2, "skin"), ("back", 0, 10, 4, 2, "skin"),
                         ("left", 0, 10, 4, 2, "skin"), ("right", 0, 10, 4, 2, "skin")]),
        "leg": ("pants", [("front", 0, 9, 4, 3, "boot"), ("back", 0, 9, 4, 3, "boot"), ("left", 0, 9, 4, 3, "boot"),
                          ("right", 0, 9, 4, 3, "boot")]),
    },
    "frontier_blacksmith": {
        "palette": {"skin": ("#D9A878", 6), "hair": ("#3A2A1E", 10), "shirt": ("#8A7A66", 10), "apron": ("#6B4A2E", 12),
                    "pants": ("#2E2A26", 8), "eye": ("#1E1E1E", 0), "beard": ("#5A3A22", 10), "boot": ("#1E1610", 6),
                    "hat": (None, 0)},
        "head": ("skin", [("front", 0, 5, 8, 3, "beard"), ("front", 1, 3, 2, 1, "eye"), ("front", 5, 3, 2, 1, "eye"),
                          ("left", 0, 5, 3, 3, "beard"), ("right", 5, 5, 3, 3, "beard"), ("back", 0, 0, 8, 2, "hair")]),
        "hat": ("hat", []),
        "body": ("shirt", [("front", 1, 2, 6, 10, "apron")]),
        "arm": ("skin", [("top", 0, 0, 4, 4, "shirt"), ("front", 0, 0, 4, 3, "shirt"), ("back", 0, 0, 4, 3, "shirt"),
                         ("left", 0, 0, 4, 3, "shirt"), ("right", 0, 0, 4, 3, "shirt")]),
        "leg": ("pants", [("front", 0, 9, 4, 3, "boot"), ("back", 0, 9, 4, 3, "boot"), ("left", 0, 9, 4, 3, "boot"),
                          ("right", 0, 9, 4, 3, "boot")]),
    },
    "setsuna": {
        "palette": {"skin": ("#F6E8E0", 4), "hair": ("#1A1420", 8), "kimono": ("#F2EEF8", 8), "obi": ("#C23046", 6),
                    "eye": ("#B0304A", 0), "flower": ("#C23046", 0), "hat": ("#1A1420", 8)},
        "head": ("skin", [("top", 0, 0, 8, 8, "hair"), ("front", 0, 0, 8, 2, "hair"), ("left", 0, 0, 8, 8, "hair"),
                          ("right", 0, 0, 8, 8, "hair"), ("back", 0, 0, 8, 8, "hair"),
                          ("front", 1, 4, 2, 1, "eye"), ("front", 5, 4, 2, 1, "eye")]),
        "hat": ("hat", [("front", 0, 2, 8, 6, None), ("left", 0, 0, 2, 2, "flower")]),
        "body": ("kimono", [("front", 0, 6, 8, 2, "obi"), ("back", 0, 6, 8, 2, "obi"), ("left", 0, 6, 4, 2, "obi"),
                            ("right", 0, 6, 4, 2, "obi"), ("front", 3, 0, 2, 6, "obi")]),
        "arm": ("kimono", [("bottom", 0, 0, 4, 4, "skin")]),
        "leg": ("kimono", [("front", 0, 10, 4, 2, "obi")]),
    },
    "goblin": {
        "palette": {"skin": ("#5E8A34", 14), "dark": ("#3E5E22", 10), "cloth": ("#6B4A2E", 12), "eye": ("#E8D020", 0),
                    "teeth": ("#E8E4C8", 0), "hat": (None, 0)},
        "head": ("skin", [("front", 1, 3, 2, 1, "eye"), ("front", 5, 3, 2, 1, "eye"), ("front", 2, 6, 4, 1, "teeth"),
                          ("top", 2, 0, 4, 8, "dark")]),
        "hat": ("hat", []),
        "body": ("skin", [("front", 0, 8, 8, 4, "cloth"), ("back", 0, 8, 8, 4, "cloth"), ("left", 0, 8, 4, 4, "cloth"),
                          ("right", 0, 8, 4, 4, "cloth"), ("front", 1, 0, 6, 1, "dark")]),
        "arm": ("skin", []),
        "leg": ("skin", [("front", 0, 0, 4, 4, "cloth"), ("back", 0, 0, 4, 4, "cloth"), ("left", 0, 0, 4, 4, "cloth"),
                         ("right", 0, 0, 4, 4, "cloth")]),
    },
}

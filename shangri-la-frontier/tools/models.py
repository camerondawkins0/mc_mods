"""Creature model specs, shared by the Java generator and the texture painter (tools/creatures.py).

Coordinates are in pixels with Y pointing UP from the ground and the creature facing -Z
(north), the way you would build it in Blockbench. A part has a pivot, an optional
rotation (degrees) and cubes; a cube is (from, size, material, decals). Decals are
(face, x, y, w, h, material) rectangles painted on a cube face (face is
top/bottom/front/back/left/right; "right" is the creature's right, the -X side).
A decal material of None cuts the texels out (ragged hems, notches).

Sizes may be multiples of 0.5: textures are painted at 2 texels per unit.

Designs follow the anime/manga (see the lore notes in the commit history): Lycagon's
navy-black shag, orange eyes and pale scythe marks; Wezaemon's white power armour,
crescent crest, cyan eye slit, teal ribbon and screw legs; Emul's top hat and tailcoat;
Vysache's kimono, cape and kiseru; Bilac's overalls; and so on.
"""


def cube(frm, size, mat, decals=None, inflate=0.0):
    return {"from": tuple(frm), "size": tuple(size), "mat": mat, "decals": decals or [], "inflate": inflate}


def part(name, parent, pivot, cubes, rot=(0, 0, 0)):
    return {"name": name, "parent": parent, "pivot": tuple(pivot), "cubes": cubes, "rot": tuple(rot)}


C = cube


def _mirror_decals(c, decals):
    w, h, d = c["size"]
    out = []
    for (face, x, y, dw, dh, mat) in decals:
        if face in ("front", "back", "top", "bottom"):
            out.append((face, w - x - dw, y, dw, dh, mat))
        elif face == "left":
            out.append(("right", d - x - dw, y, dw, dh, mat))
        else:
            out.append(("left", d - x - dw, y, dw, dh, mat))
    return out


def mirror_cube(c):
    (x, y, z), (w, h, d) = c["from"], c["size"]
    return cube((-x - w, y, z), (w, h, d), c["mat"], _mirror_decals(c, c["decals"]), c["inflate"])


def mirror(p, name, parent=None):
    """The part mirrored across X (left <-> right)."""
    px, py, pz = p["pivot"]
    rx, ry, rz = p["rot"]
    return part(name, parent or p["parent"], (-px, py, pz), [mirror_cube(c) for c in p["cubes"]], (rx, -ry, -rz))


def pair(left, right_name, parent_map=None):
    """[left, mirrored right]; parent_map renames parents for the mirrored part."""
    parent = (parent_map or {}).get(left["parent"], left["parent"])
    return [left, mirror(left, right_name, parent)]


# =======================================================================================
# Lycagon the Nightslayer (and its shadows)
# =======================================================================================

def lycagon():
    eyes = [("front", 2, 5, 3, 2, "eye"), ("front", 11, 5, 3, 2, "eye")]
    marks = [("front", 1, 1, 1, 1, "mark"), ("front", 2, 0, 2, 1, "mark"), ("front", 4, 1, 1.5, 1, "mark"),
             ("front", 10.5, 1, 1.5, 1, "mark"), ("front", 12, 0, 2, 1, "mark"), ("front", 14, 1, 1, 1, "mark")]
    under = [("front", 1.5, 7.5, 1, 1, "mark"), ("front", 3, 8, 2, 0.5, "mark"),
             ("front", 13.5, 7.5, 1, 1, "mark"), ("front", 11, 8, 2, 0.5, "mark")]
    m = [
        part("body", "root", (0, 28, 4), [
            C((-11, 18, -16), (22, 19, 15), "fur"),
            C((-9.5, 20, -2), (19, 15, 16), "fur", [("top", 9, 0, 1, 16, "fur2")]),
            C((-10.5, 19, 13), (21, 16, 12), "fur"),
            C((-8, 15, -15), (16, 4, 13), "shag"),
            C((-7, 16, 0), (14, 4, 12), "shag"),
        ]),
        part("spine", "body", (0, 35, -2), [
            C((-2, 34, -8), (4, 4, 6), "shag"), C((-2.5, 34, -1), (5, 4, 6), "shag"),
            C((-2, 33.5, 6), (4, 4, 6), "shag"), C((-1.5, 33, 13), (3, 3.5, 6), "shag"),
        ], rot=(-8, 0, 0)),
        part("mane", "body", (0, 30, -14), [
            C((-13.5, 20, -21), (27, 22, 10), "mane"),
            C((-12, 35, -12), (24, 9, 9), "mane"),
            C((-14.5, 24, -18), (3, 15, 8), "mane"), C((11.5, 24, -18), (3, 15, 8), "mane"),
            C((-10, 17, -21.5), (20, 5, 7), "mane"),
        ]),
        part("head", "mane", (0, 35, -20), [
            C((-8, 29, -33), (16, 13, 13), "fur", eyes + under + [("front", 0, 12, 16, 1, "fur2")]),
            C((-8.5, 38.5, -34), (17, 3.5, 6), "fur", marks),
            C((-11, 29, -30), (3, 10, 9), "shag"), C((8, 29, -30), (3, 10, 9), "shag"),
        ]),
        part("snout", "head", (0, 33, -33), [
            C((-4.5, 30.5, -43), (9, 6.5, 10), "fur", [("top", 3.5, 2, 2, 8, "fur2")]),
            C((-2.5, 35, -44), (5, 2.5, 3), "nose"),
            C((-4, 30.5, -43.1), (8, 1, 0.5), "teeth"),
        ]),
        part("jaw", "head", (0, 30.5, -32), [
            C((-4, 27, -42), (8, 3.5, 10), "jaw", [("top", 0, 0, 8, 1, "teeth"), ("front", 0, 0, 8, 1, "teeth")]),
            C((-3.8, 29.5, -42.3), (1, 2.5, 1), "teeth"), C((2.8, 29.5, -42.3), (1, 2.5, 1), "teeth"),
        ]),
    ]
    m += pair(part("left_ear", "head", (5, 41, -26), [
        C((3, 41, -28), (5, 7, 3), "fur", [("front", 1, 1, 3, 5, "inner")]),
        C((3.75, 48, -27.5), (3.5, 4, 2), "fur"),
    ], rot=(-12, 0, 14)), "right_ear")
    m += pair(part("front_left_leg", "body", (7, 22, -9), [
        C((3, 11, -13), (8, 14, 8), "fur"),
        C((2.5, 17, -14), (9, 8, 3), "shag"),
    ]), "front_right_leg")
    m += pair(part("front_left_shin", "front_left_leg", (7, 12, -9), [
        C((4, 3, -11.5), (6, 10, 6), "fur"),
        C((3.5, 0, -14.5), (7, 3.5, 8), "fur", [("front", 0, 2.5, 7, 1, "claw"), ("top", 0, 0, 7, 1.5, "claw")]),
    ]), "front_right_shin", {"front_left_leg": "front_right_leg"})
    m += pair(part("back_left_leg", "body", (7, 24, 19), [
        C((2.5, 12, 13), (9, 15, 12), "fur"),
        C((2, 16, 22), (10, 9, 4), "shag"),
    ]), "back_right_leg")
    m += pair(part("back_left_shin", "back_left_leg", (7, 13, 22), [
        C((4, 3, 19.5), (6, 11, 6), "fur"),
        C((3.5, 0, 16.5), (7, 3.5, 8), "fur", [("front", 0, 2.5, 7, 1, "claw")]),
    ], rot=(18, 0, 0)), "back_right_shin", {"back_left_leg": "back_right_leg"})
    m += [
        part("tail", "body", (0, 31, 24), [C((-3.5, 28, 24), (7, 7, 10), "shag")], rot=(30, 0, 0)),
        part("tail2", "tail", (0, 31.5, 33), [C((-5, 27, 33), (10, 10, 11), "shag")], rot=(42, 0, 0)),
        part("tail3", "tail2", (0, 32, 43), [C((-4, 28, 43), (8, 8, 9), "shag", [("back", 2, 2, 4, 4, "tip")])], rot=(48, 0, 0)),
    ]
    return m


# =======================================================================================
# Wezaemon the Tombguard
# =======================================================================================

def wezaemon():
    slit = [("front", 1, 2, 5, 1, "eye")]
    plates = [("front", 0, 3, 13, 0.5, "seam"), ("front", 6.25, 0, 0.5, 9, "seam"), ("front", 1, 6, 4, 0.5, "seam"),
              ("front", 8, 6, 4, 0.5, "seam")]
    m = []
    m += pair(part("left_leg", "root", (3.5, 24, 0), [
        C((1.5, 14, -2.5), (4, 10, 5), "thigh"),
        C((1, 15, -3.5), (5, 8, 1.5), "armor"),
        C((5.5, 15, -2.5), (1, 8, 5), "armor"),
        C((1.2, 11.5, -3.3), (4.6, 3.5, 5.6), "armor", [("front", 1.5, 1, 1.6, 1.5, "seam")]),
        C((1.75, 8, -2.25), (3.5, 3.5, 4.5), "screw"),
        C((2.1, 5, -1.9), (2.8, 3, 3.8), "screw"),
        C((2.45, 2.5, -1.5), (2.1, 2.5, 3), "screw"),
        C((2.75, 0, -1.1), (1.5, 2.5, 2.2), "screw"),
    ]), "right_leg")
    m += [
        part("body", "root", (0, 24, 0), [
            C((-5, 21, -3), (10, 5, 6), "under"),
            C((-5.5, 26, -3.5), (11, 5, 7), "under", [("front", 0, 2, 11, 0.5, "band")]),
            C((-6.5, 31, -4.5), (13, 9, 8), "armor", plates),
            C((-4.5, 39.5, -3.5), (9, 2, 7), "armor"),
            C((-4, 30, 3.5), (8, 9, 3.5), "armor", [("back", 1, 1, 6, 0.5, "seam"), ("back", 3, 3, 2, 2, "eye")]),
            C((-5.5, 16, -4), (5, 6, 1), "armor", [("front", 0, 2, 5, 0.5, "seam"), ("front", 3.5, 3, 0.5, 2, "eye")]),
            C((0.5, 16, -4), (5, 6, 1), "armor", [("front", 0, 2, 5, 0.5, "seam"), ("front", 1, 3, 0.5, 2, "eye")]),
            C((-5.5, 16, 3), (11, 6, 1), "armor"),
            C((5, 14.5, -3.5), (1.5, 8, 7), "armor", [("left", 2.75, 2.5, 1.5, 1.5, "bolt"), ("left", 0, 6, 7, 0.5, "seam")]),
            C((-6, 16.5, -3), (1, 5.5, 6), "armor"),
        ]),
        part("scabbard", "body", (5.5, 21, 0), [
            C((5.5, 20.5, -12), (1.5, 1.5, 22), "sheath", [("top", 0, 3, 1.5, 0.5, "gold")]),
        ], rot=(-12, -8, 0)),
        part("head", "body", (0, 41.5, 0), [
            C((-1.5, 40.5, -1.5), (3, 2, 3), "under"),
            C((-4, 42.5, -4.5), (8, 7, 9), "armor", [("top", 3.5, 0, 1, 9, "seam")]),
            C((-3.5, 41.5, -5.2), (7, 5, 1), "face", slit),
            C((4, 43.5, -3), (1.5, 4, 5), "armor"), C((-5.5, 43.5, -3), (1.5, 4, 5), "armor"),
            C((-4.5, 41.5, 2.5), (9, 3, 3), "armor"),
            C((-1, 49, -5.5), (2, 2, 2), "crest"),
        ]),
    ]
    # The black crescent crest: three segments a side bending outward.
    m += pair(part("crest_l1", "head", (0.5, 50, -5), [C((0.5, 50, -5.5), (1.5, 4, 1), "crest")], rot=(10, 0, 62)), "crest_r1")
    m += pair(part("crest_l2", "crest_l1", (1.5, 53.5, -5), [C((1.2, 53.5, -5.4), (1.2, 4, 0.8), "crest")], rot=(0, 0, -32)),
              "crest_r2", {"crest_l1": "crest_r1"})
    m += pair(part("crest_l3", "crest_l2", (2, 57, -5), [C((1.7, 57, -5.3), (0.8, 3, 0.6), "crest")], rot=(0, 0, -28)),
              "crest_r3", {"crest_l2": "crest_r2"})
    m += [
        part("ribbon", "head", (0, 46, 4.5), [C((-1, 33, 4.5), (2, 13, 0.5), "ribbon")], rot=(18, 0, 0)),
        part("ribbon2", "ribbon", (0, 33, 4.75), [C((-1, 21, 4.5), (2, 12, 0.5), "ribbon")], rot=(15, 0, 0)),
    ]
    arm = part("left_arm", "body", (7.5, 38.5, 0), [
        C((6, 33, -4), (5.5, 7, 8), "armor", [("front", 0, 2, 5.5, 0.5, "seam")]),
        C((7, 29, -2), (4, 9, 4), "under"),
        C((6.8, 31, -2.2), (4.4, 2, 4.4), "band"),
        C((6.6, 22, -2.4), (4.8, 7, 4.8), "armor", [("front", 0, 1, 4.8, 0.5, "seam")]),
        C((7.2, 20, -1.8), (3.6, 2.5, 3.6), "under"),
    ], rot=(0, 0, -6))
    m += pair(arm, "right_arm")
    m += [
        part("katana", "right_arm", (-9, 21, 0), [
            C((-9.6, 20.2, -3.5), (1.2, 1.5, 9), "hilt", [("top", 0, 1, 1.2, 0.5, "gold"), ("top", 0, 4, 1.2, 0.5, "gold")]),
            C((-10.4, 19.6, -5), (2.8, 2.7, 0.8), "gold"),
            C((-9.45, 20.5, -37), (0.9, 1.1, 32), "steel", [("top", 0, 0, 0.9, 32, "edge")]),
        ]),
    ]
    return m


# =======================================================================================
# Kirin, Wezaemon's tactical mount
# =======================================================================================

def kirin():
    """Kirin, Wezaemon's tactical mount: a white armoured robot horse on a black frame threaded with
    glowing cables, a beaked dragon-like head with swept-back blade ears, a tall blade fin along the
    neck and a tail of three cables ending in glowing plugs."""
    m = [
        part("body", "root", (0, 22, 4), [
            C((-5.5, 16, -10), (11, 10, 26), "armor", [("left", 2, 4, 22, 0.5, "seam"), ("right", 2, 4, 22, 0.5, "seam"),
                                                       ("left", 9, 6.5, 7, 1, "cable"), ("right", 10, 6.5, 7, 1, "cable")]),
            C((-4, 14, -8), (8, 3, 22), "joint", [("bottom", 1, 2, 6, 18, "cable")]),
            C((-6, 20.5, -8), (12, 6, 9), "armor", [("front", 1, 1.5, 10, 0.5, "seam"), ("front", 5, 3, 2, 1.5, "eye")]),
            C((-6, 19, 9), (12, 7, 8), "armor", [("back", 1, 1.5, 10, 0.5, "seam")]),
        ]),
        part("back_fin", "body", (0, 26, 2), [
            C((-0.5, 26, -1), (1, 3, 12), "armor"),
            C((-0.4, 28.5, 2), (0.8, 3, 8), "armor"),
            C((-0.3, 31, 5), (0.6, 2.5, 4), "armor"),
        ], rot=(-6, 0, 0)),
        part("neck", "body", (0, 25, -9), [
            C((-3, 23, -16), (6, 10, 7), "armor"),
            C((-2, 26, -12), (4, 9, 4), "joint", [("left", 0, 1, 4, 7, "cable"), ("right", 0, 1, 4, 7, "cable")]),
            C((-0.5, 31, -12), (1, 5, 5), "armor"),
        ], rot=(30, 0, 0)),
        part("neck_fin", "neck", (0, 35.5, -10), [
            C((-0.45, 35, -11), (0.9, 6, 4.5), "armor"),
            C((-0.35, 40.5, -9.5), (0.7, 5, 3), "armor"),
            C((-0.25, 45, -8), (0.5, 3, 1.5), "armor"),
        ], rot=(-45, 0, 0)),
        part("head", "neck", (0, 33, -14), [
            C((-3, 31, -22), (6, 6, 9), "armor", [("left", 1, 1.5, 2.5, 1, "eye"), ("right", 5.5, 1.5, 2.5, 1, "eye"),
                                                  ("top", 2.75, 0, 0.5, 9, "seam")]),
            C((-2.5, 31.5, -28.5), (5, 4, 6.5), "armor", [("front", 0, 0, 5, 0.5, "seam")]),
            C((-1.5, 30.5, -31), (3, 3.5, 2.5), "armor"),
            C((-2, 29, -28), (4, 2.5, 6.5), "joint", [("front", 0, 2, 4, 0.5, "teeth"), ("left", 0, 2, 6.5, 0.5, "teeth"),
                                                      ("right", 0, 2, 6.5, 0.5, "teeth")]),
        ], rot=(-25, 0, 0)),
        part("tail", "body", (0, 24, 16), [
            C((-2, 23, 15), (1, 1, 14), "cable"), C((-0.5, 24, 15), (1, 1, 15), "cable"), C((1, 23, 15), (1, 1, 13), "cable"),
            C((-2.25, 22.75, 28.5), (1.5, 1.5, 1.5), "eye"), C((-0.75, 23.75, 29.5), (1.5, 1.5, 1.5), "eye"),
            C((0.75, 22.75, 27.5), (1.5, 1.5, 1.5), "eye"),
        ], rot=(-30, 0, 0)),
    ]
    m += pair(part("left_blade_ear", "head", (2.25, 36.5, -17), [
        C((1.75, 36, -17), (1, 2, 7), "armor"),
        C((1.95, 36.75, -10.5), (0.6, 1.25, 4), "armor"),
    ], rot=(28, 18, 0)), "right_blade_ear")
    leg = [
        C((2, 10, -1.75), (3.5, 7, 4), "armor", [("front", 0.5, 1, 2.5, 0.5, "seam")]),
        C((2.5, 5, -1.25), (2.5, 5.5, 3), "joint", [("front", 0.75, 0.5, 1, 4.5, "cable")]),
        C((2.1, 1.5, -1.5), (3.3, 4, 3.5), "armor"),
        C((2.25, 0, -1.75), (3, 1.5, 4), "hoof"),
    ]
    for nm, z in (("front_left_leg", -6), ("back_left_leg", 13)):
        cubes = [cube((c["from"][0], c["from"][1], c["from"][2] + z), c["size"], c["mat"], c["decals"]) for c in leg]
        m += pair(part(nm, "body", (3.75, 17, z), cubes), nm.replace("left", "right"))
    return m


# =======================================================================================
# Vorpal Bunnies: the monster, Emul, Vysache and Bilac
# =======================================================================================

def vorpal_bunny():
    eye = [("front", 1, 2, 1, 1.5, "eye"), ("front", 4, 2, 1, 1.5, "eye"), ("front", 2.5, 3.5, 1, 1, "nose")]
    m = []
    m += pair(part("left_leg", "body", (1.8, 5, 1), [
        C((0.5, 1.5, -1), (2.8, 4, 4), "fur"),
        C((0.6, 0, -3.5), (2.6, 1.5, 5.5), "fur", [("front", 0, 0, 2.6, 0.5, "pad")]),
    ]), "right_leg")
    m = [part("body", "root", (0, 5, 0), [
        C((-3, 5, -2.5), (6, 7, 5), "fur", [("front", 1, 1, 4, 5, "belly")]),
        C((-3.5, 10.5, -3), (7, 2, 6), "scarf"),
    ])] + m
    m += [
        part("scarf_tail", "body", (1.5, 11, 3), [C((0.5, 5.5, 3), (2.5, 6, 0.5), "scarf", [("back", 0, 5, 1, 1, None)])],
             rot=(20, 20, 0)),
        part("head", "body", (0, 12, 0), [
            C((-3, 12, -3.5), (6, 5.5, 6), "fur", eye),
            C((-1.5, 12.5, -4.2), (3, 2, 1), "belly"),
        ]),
        part("tail", "body", (0, 6.5, 2.5), [C((-1.25, 5.5, 2.5), (2.5, 2.5, 2), "puff")]),
    ]
    m += pair(part("left_ear", "head", (1.5, 17, -0.5), [C((0.6, 17, -1), (1.8, 8, 1), "fur", [("front", 0.4, 1, 1, 6, "inner")])],
                   rot=(-8, 0, 10)), "right_ear")
    m += [part("left_arm", "body", (3, 10.5, 0), [C((2.9, 6.5, -1), (1.6, 4, 2), "fur")], rot=(-10, 0, 0)),
          part("right_arm", "body", (-3, 10.5, 0), [C((-4.5, 6.5, -1), (1.6, 4, 2), "fur")], rot=(-40, 0, 0)),
          part("knife", "right_arm", (-3.7, 7, 0), [
              C((-4.2, 6.3, -2), (1, 1.2, 2.5), "grip"),
              C((-4.0, 6.1, -8), (0.6, 1.5, 6), "blade", [("left", 0, 1, 6, 0.5, "edge"), ("right", 0, 1, 6, 0.5, "edge"),
                                                         ("bottom", 0, 0, 0.6, 6, "edge")]),
              C((-3.95, 6.4, -9.5), (0.5, 1, 1.5), "blade"),
          ])]
    return m


def emul():
    face = [("front", 1.5, 3, 2, 3, "eye"), ("front", 6.5, 3, 2, 3, "eye"), ("front", 2, 3.5, 0.5, 1, "shine"),
            ("front", 7, 3.5, 0.5, 1, "shine"), ("front", 4.5, 6, 1, 0.5, "nose"), ("front", 4, 7, 2, 0.5, "mouth"),
            ("front", 0.5, 6.5, 1.5, 1, "blush"), ("front", 8, 6.5, 1.5, 1, "blush")]
    m = [
        part("body", "root", (0, 4, 0), [
            C((-4.5, 3.5, -3.5), (9, 7.5, 7), "coat", [("front", 3, 0, 3, 7.5, "fur"), ("front", 4.25, 2, 0.5, 0.5, "gold"),
                                                         ("front", 4.25, 4, 0.5, 0.5, "gold")]),
            C((-4.8, 10, -3.8), (9.6, 1.5, 7.6), "puff"),
            C((-2, 10, -4.2), (4, 1.5, 1), "bow"),
            C((-0.5, 9.75, -4.4), (1, 2, 1.2), "bow"),
        ]),
        part("coat_tails", "body", (0, 6, 3.5), [
            C((-4.5, 0.5, 3.5), (9, 6, 0.5), "tails", [("back", 1, 5, 1, 1, None), ("back", 3.5, 4.5, 2, 1.5, None),
                                                       ("back", 7, 5, 1, 1, None), ("front", 1, 5, 1, 1, None),
                                                       ("front", 3.5, 4.5, 2, 1.5, None), ("front", 7, 5, 1, 1, None)]),
        ], rot=(12, 0, 0)),
        part("head", "body", (0, 11, 0), [
            C((-5, 11, -5), (10, 9, 9), "fur", face),
            C((-5.5, 11.5, -4), (1, 4, 6), "fur"), C((4.5, 11.5, -4), (1, 4, 6), "fur"),
        ]),
        part("hat", "head", (1, 20, -0.5), [
            C((-2.5, 19.75, -4), (7, 0.75, 7), "hat"),
            C((-1, 20.5, -2.5), (4, 5, 4), "hat", [("front", 0, 0.75, 4, 0.75, "band"), ("left", 0, 0.75, 4, 0.75, "band"),
                                                     ("right", 0, 0.75, 4, 0.75, "band"), ("back", 0, 0.75, 4, 0.75, "band"),
                                                     ("front", 1.5, 1.6, 1, 1, "gem")]),
        ], rot=(0, 0, -14)),
        part("tail", "body", (0, 5, 3.5), [C((-1.5, 4, 3), (3, 3, 2), "puff")]),
    ]
    m += pair(part("left_ear", "head", (4.5, 18.5, -1), [
        C((4, 18.5, -3), (3, 12, 1), "fur", [("front", 0.5, 1, 2, 10, "inner")]),
    ], rot=(10, 0, 158)), "right_ear")
    m += pair(part("left_arm", "body", (4.5, 9.5, 0), [
        C((4.4, 6, -1.5), (2, 4, 3), "coat", [("front", 0, 2.5, 2, 0.5, "gold")]),
        C((4.5, 5, -1.25), (1.8, 1.5, 2.5), "fur"),
    ], rot=(0, 0, -15)), "right_arm")
    m += pair(part("left_leg", "body", (2, 4, 0), [
        C((0.7, 2, -1.5), (2.6, 2.5, 3), "fur"),
        C((0.5, 0, -3), (3, 2, 5), "fur", [("top", 0, 0, 3, 1, "pad")]),
    ]), "right_leg")
    return m


def vysache():
    face = [("front", 1.5, 2.5, 1.5, 1, "eye"), ("front", 6, 2.5, 1.5, 1, "eye"), ("front", 1, 1.5, 2.5, 0.5, "brow"),
            ("front", 5.5, 1.5, 2.5, 0.5, "brow"), ("front", 2, 0.5, 0.5, 4, "scar")]
    hakama = [("front", 1, 3, 2, 2, "swirl"), ("front", 3, 8, 2.5, 2, "swirl"), ("left", 2, 5, 3, 2, "swirl"),
              ("back", 2, 2, 2, 2, "swirl")]
    m = [
        part("body", "root", (0, 14, 0), [
            C((-6.5, 14, -4), (13, 12, 8), "kimono", [("front", 2, 0, 1, 12, "tie"), ("front", 10, 0, 1, 12, "tie"),
                                                         ("front", 4, 0, 0.5, 12, "lining"), ("front", 8.5, 0, 0.5, 12, "lining"),
                                                         ("front", 4.5, 5, 4, 4, "fluff")]),
            C((-1.5, 16.25, -4.6), (3, 2.5, 1), "tie"),
            C((-6.8, 16, -4.3), (13.6, 2.5, 8.6), "tie"),
            C((-7.5, 24, -5), (15, 4, 10), "ruff"),
            C((-5, 22.5, -4.6), (10, 1.5, 1), "jade", [("front", 1, 0, 1, 1.5, "bead"), ("front", 4.5, 0, 1, 1.5, "bead"),
                                                      ("front", 8, 0, 1, 1.5, "bead")]),
            C((-1.5, 19, -4.8), (3, 3, 1), "ruff"),
        ]),
        part("cape", "body", (0, 27, 4), [
            C((-8.5, 6, 4), (17, 21, 1), "cape", [("back", 3, 4, 4, 4, "motif"), ("back", 10, 9, 4, 4, "motif"),
                                                  ("back", 0, 19, 17, 2, "trim"), ("front", 0, 19, 17, 2, "trim")]),
            C((-8.5, 24, -4.5), (17, 3, 9), "cape"),
        ], rot=(10, 0, 0)),
        part("head", "body", (0, 27, 0), [
            C((-4.5, 27, -5), (9, 8, 8), "fur", face),
            C((-2.5, 27.5, -6.5), (5, 3, 2), "muzzle", [("front", 2, 0, 1, 1, "nose")]),
            C((-5.5, 27, -3.5), (1, 5, 5), "muzzle"), C((4.5, 27, -3.5), (1, 5, 5), "muzzle"),
        ]),
        part("pipe", "head", (-1, 28.5, -6), [
            C((-1.4, 28.2, -15), (0.8, 0.8, 9), "pipe"),
            C((-1.6, 27.9, -16.5), (1.2, 1.6, 1.5), "gold"),
            C((-1.6, 28, -6.5), (1.2, 1.2, 1), "gold"),
        ], rot=(-8, 18, 0)),
    ]
    m += [part("left_ear", "head", (2.5, 35, -1), [C((1.2, 35, -2), (2.8, 12, 1.5), "fur", [("front", 0.6, 1, 1.6, 10, "inner")])],
               rot=(-6, 0, 8)),
          part("right_ear", "head", (-2.5, 35, -1), [C((-4, 35, -2), (2.8, 12, 1.5), "fur", [
              ("front", 0.6, 1, 1.6, 10, "inner"), ("front", 0, 7, 1, 1.5, None), ("back", 1.8, 7, 1, 1.5, None)])],
               rot=(-6, 0, -8))]
    m += pair(part("left_arm", "body", (7, 25, 0), [
        C((6.5, 15, -3), (5, 10, 6), "kimono", [("front", 0, 8.5, 5, 1.5, "tie")]),
        C((7, 13, -2), (3.5, 2.5, 3.5), "muzzle"),
    ], rot=(0, 0, -6)), "right_arm")
    m += pair(part("left_leg", "root", (3, 14, 0), [
        C((0.3, 2, -3.5), (6.2, 12, 7), "hakama", hakama),
        C((1, 0, -4.5), (4, 2, 5.5), "muzzle"),
    ]), "right_leg")
    return m


def bilac():
    face = [("front", 1, 2.5, 1.5, 1.5, "eye"), ("front", 4.5, 2.5, 1.5, 1.5, "eye"), ("front", 3, 4.5, 1, 0.5, "nose")]
    m = [
        part("body", "root", (0, 9, 0), [
            C((-3.5, 9, -2.2), (7, 7, 4.4), "fur", [("front", 0, 2, 7, 0.75, "wrap"), ("front", 0, 3.5, 7, 0.75, "wrap"),
                                                      ("front", 0, 5, 7, 0.75, "wrap")]),
            C((-3.2, 9, -2.6), (6.4, 3, 0.5), "overalls", [("front", 2.5, 1, 1.4, 1, "pocket")]),
            C((-2.8, 12, -2.5), (1, 4, 0.4), "overalls"), C((1.8, 12, -2.5), (1, 4, 0.4), "overalls"),
        ]),
        part("head", "body", (0, 16, 0), [
            C((-3.5, 16, -3.5), (7, 6.5, 7), "fur", face),
            C((-2, 16.5, -4.2), (4, 2, 1), "muzzle"),
            C((-2.5, 22, -2), (5, 1.5, 4), "fur"),
            C((-1, 23, -1.5), (2, 2.5, 2), "muzzle"),
        ]),
        part("hammer", "right_arm", (-4.3, 4.5, 0), [
            C((-4.8, -2.5, -0.5), (1, 9, 1), "wood"),
            C((-6.5, -4.5, -2), (4.4, 2.5, 4), "iron", [("top", 0, 0, 4.4, 0.5, "shine")]),
        ]),
    ]
    m += pair(part("left_ear", "head", (2, 22, -1), [C((0.8, 22, -1.6), (2.4, 10, 1.2), "fur", [("front", 0.5, 1, 1.4, 8, "inner")])],
                   rot=(0, 0, 12)), "right_ear")
    m += pair(part("left_arm", "body", (3.8, 15.5, 0), [
        C((3.5, 6, -1), (2, 9.5, 2), "fur", [("front", 0, 0.5, 2, 1, "wrap")]),
        C((3.3, 4.5, -1.2), (2.4, 1.5, 2.4), "fur"),
    ], rot=(0, 0, -6)), "right_arm")
    m.insert(3, m.pop(2))  # hammer after arms
    m = [p for p in m if p["name"] != "hammer"] + [p for p in m if p["name"] == "hammer"]
    m += pair(part("left_leg", "root", (2, 9, 0), [
        C((-0.3, 2, -2.8), (4.8, 7, 5.6), "overalls", [("left", 1, 2, 2.5, 2, "pocket"), ("front", 0, 6, 4.8, 1, "pocket")]),
        C((0.5, 0, -3), (3.6, 2, 5.2), "fur"),
    ]), "right_leg")
    return m


# =======================================================================================
# Humanoid NPCs: shared rig + outfits
# =======================================================================================

def humanoid(slim=False, height=0):
    """Standard 32-pixel biped: head, body, arms, legs (vanilla proportions)."""
    aw = 3 if slim else 4
    return {
        "head": [C((-4, 24, -4), (8, 8, 8), "skin")],
        "body": [C((-4, 12, -2), (8, 12, 4), "shirt")],
        "left_arm": [C((4, 12, -2), (aw, 12, 4), "sleeve")],
        "left_leg": [C((0, 0, -2), (4, 12, 4), "pants")],
    }


def biped(spec):
    """Builds a biped from per-part cube lists (left side given; right side mirrored)."""
    aw = spec.get("arm_width", 4)
    m = [
        part("body", "root", (0, 12, 0), spec["body"]),
        part("head", "body", (0, 24, 0), spec["head"]),
    ]
    m += pair(part("left_arm", "body", (4 + aw / 2 - 0.5, 22, 0), spec["left_arm"]), "right_arm")
    m += pair(part("left_leg", "root", (2, 12, 0), spec["left_leg"]), "right_leg")
    if "right_arm_extra" in spec:
        right = next(p for p in m if p["name"] == "right_arm")
        right["cubes"] = right["cubes"] + spec["right_arm_extra"]
    for extra in spec.get("parts", []):
        m.append(extra)
    return m


def face(eyes="eye", y=3, mouth=True, x0=1.5, w=1.5):
    d = [("front", x0, y, w, 1, eyes), ("front", 8 - x0 - w, y, w, 1, eyes),
         ("front", x0, y, 0.5, 1, "white"), ("front", 8 - x0 - 0.5, y, 0.5, 1, "white")]
    if mouth:
        d.append(("front", 3.5, y + 2.5, 1, 0.5, "mouth"))
    return d


def setsuna():
    """Setsuna of the Distant Days: light-brown bob with blunt bangs and a long braid over her right
    shoulder, a white lab coat with red sleeve bands and mint trim over a pale green turtleneck,
    tan trousers and brown boots."""
    return biped({
        "arm_width": 3,
        "head": [C((-4, 24, -4), (8, 8, 8), "skin", face(y=4, x0=1.5) + [("front", 0, 0, 8, 2.5, "hair"),
                                                                        ("front", 5.5, 5, 0.5, 0.5, "mark")]),
                 C((-4.25, 26, -4.25), (8.5, 6.25, 8.5), "hair", [("front", 0.5, 2.25, 7.5, 4, None)])],
        "body": [C((-4, 12, -2), (8, 12, 4), "turtleneck", [("front", 0, 3, 8, 1, "belt")]),
                 C((-4.3, 11.5, -2.4), (8.6, 12.7, 4.8), "coat", [("front", 2.8, 0, 3, 12.7, None),
                                                                 ("front", 2.3, 0, 0.5, 12.7, "trim"),
                                                                 ("front", 5.8, 0, 0.5, 12.7, "trim"),
                                                                 ("front", 6.5, 9, 0.75, 0.75, "stripe")]),
                 C((-3, 23, -2.6), (6, 2.5, 5.2), "turtleneck", [("front", 0, 2, 6, 0.5, "collar")])],
        "left_arm": [C((3.6, 12, -1.6), (3.2, 10.5, 3.2), "coat", [("left", 0, 4, 3.2, 0.5, "stripe"), ("left", 0, 5, 3.2, 0.5, "stripe"),
                                                                   ("front", 0, 4, 3.2, 0.5, "stripe"), ("front", 0, 5, 3.2, 0.5, "stripe"),
                                                                   ("back", 0, 4, 3.2, 0.5, "stripe"), ("back", 0, 5, 3.2, 0.5, "stripe")]),
                     C((3.85, 10.5, -1.35), (2.7, 1.5, 2.7), "skin")],
        "left_leg": [C((0.1, 2, -1.9), (3.8, 10, 3.8), "trousers"), C((0, 0, -2.3), (4, 2.5, 4.6), "boots")],
        "parts": [
            part("coat_skirt", "body", (0, 12, 0), [C((-4.5, 4, -2.6), (9, 8, 5.2), "coat", [("front", 3.5, 0, 2, 8, None),
                                                                                         ("front", 3, 0, 0.5, 8, "trim"),
                                                                                         ("front", 5.5, 0, 0.5, 8, "trim")])]),
            part("braid", "head", (-3, 26, -1.5), [C((-4.5, 15, -2.5), (2, 11, 2), "hair", [("front", 0, 2, 2, 0.5, "hair2"),
                                                                                         ("front", 0, 5, 2, 0.5, "hair2"),
                                                                                         ("front", 0, 8, 2, 0.5, "hair2")])],
                 rot=(-14, 0, -8)),
        ],
    })


def guide():
    return biped({
        "head": [C((-4, 24, -4), (8, 8, 8), "skin", face(y=4) + [("front", 0, 0, 8, 2, "hair")]),
                 C((-4.25, 28, -4.25), (8.5, 4.25, 8.5), "hair", [("front", 1, 2, 6, 2.25, None)]),
                 C((-5, 31.5, -5), (10, 1, 10), "hat"), C((-3.5, 32.5, -3.5), (7, 2.5, 7), "hat", [("front", 0, 1.5, 7, 1, "trim")])],
        "body": [C((-4, 12, -2), (8, 12, 4), "shirt"),
                 C((-4.3, 11, -2.3), (8.6, 12.5, 4.6), "coat", [("front", 3, 0, 2, 12.5, "shirt"), ("front", 0, 3.5, 8.6, 1, "belt")]),
                 C((-4.6, 21.5, -2.6), (9.2, 2.5, 5.2), "scarf"),
                 C((2.5, 10, 2), (3, 4, 1.5), "satchel")],
        "left_arm": [C((4, 12, -2), (4, 12, 4), "coat", [("front", 0, 10, 4, 2, "skin")])],
        "left_leg": [C((0, 0, -2), (4, 12, 4), "pants", [("front", 0, 8, 4, 4, "boots"), ("back", 0, 8, 4, 4, "boots")])],
    })


def smith():
    return biped({
        "head": [C((-4, 24, -4), (8, 8, 8), "skin", face(y=3.5) + [("front", 0, 5, 8, 3, "beard"), ("front", 2, 2.5, 4, 0.5, "brow")]),
                 C((-4.2, 29.5, -4.2), (8.4, 2, 8.4), "bandana", [("front", 0, 0, 8.4, 2, "bandana")]),
                 C((-4.3, 21.5, -4.6), (8.6, 4, 2), "beard")],
        "body": [C((-4.5, 12, -2.5), (9, 12, 5), "shirt"),
                 C((-4, 9, -2.9), (8, 13, 1), "apron", [("front", 3, 3, 2, 2, "metal")]),
                 C((-4.7, 13, -2.7), (9.4, 1.5, 5.4), "belt")],
        "left_arm": [C((4.5, 12, -2), (4.5, 12, 4.5), "skin", [("front", 0, 0, 4.5, 4, "shirt"), ("back", 0, 0, 4.5, 4, "shirt"),
                                                                ("left", 0, 0, 4.5, 4, "shirt")]),
                     C((4.3, 12, -2.2), (4.9, 4, 4.9), "glove")],
        "left_leg": [C((0, 0, -2), (4, 12, 4), "pants", [("front", 0, 9, 4, 3, "boots")])],
        "right_arm_extra": [C((-8, 0, -0.5), (1, 11, 1), "wood"), C((-9.5, -2, -2), (4, 2.5, 4), "metal")],
    })


def pencilgon():
    """Arthur Pencilgon: knee-length violet ponytail with a white flower ornament, a white top with
    grey-white chest armour and a gold brooch, a dark-red short cape, long white gloves, a white
    tabard skirt trimmed in dark red, red leggings under grey knee guards, black heeled boots and a
    golden spear with a trident-like head."""
    return biped({
        "arm_width": 3,
        "head": [C((-4, 24, -4), (8, 8, 8), "skin", face(eyes="eye", y=4) + [("front", 0, 0, 8, 2, "hair"), ("front", 6, 2, 2, 2, "hair")]),
                 C((-4.25, 26, -4.25), (8.5, 6.25, 8.5), "hair", [("front", 1, 2, 5, 4.25, None)]),
                 C((2.5, 30.5, -2), (1.5, 1.5, 1.5), "flower")],
        "body": [C((-4, 12, -2), (8, 12, 4), "white", [("front", 0, 8.5, 8, 1, "red")]),
                 C((-4.3, 17.5, -2.4), (8.6, 6, 4.8), "armor", [("front", 3.3, 1.5, 2, 2, "gold")]),
                 C((-4.6, 21.5, -2.7), (9.2, 2.5, 5.4), "red"),
                 C((-4.5, 6, -2.5), (9, 6.5, 5), "white", [("front", 0, 5.5, 9, 1, "red"), ("back", 0, 5.5, 9, 1, "red"),
                                                           ("left", 0, 5.5, 5, 1, "red"), ("right", 0, 5.5, 5, 1, "red"),
                                                           ("left", 0, 0, 2, 6.5, None), ("right", 3, 0, 2, 6.5, None)])],
        "left_arm": [C((4, 12, -2), (3, 12, 4), "white"), C((3.8, 18.5, -2.3), (3.4, 5.5, 4.6), "armor")],
        "left_leg": [C((0, 0, -2), (4, 12, 4), "stocking", [("front", 0, 3, 4, 1, "wrap"), ("front", 0, 7, 4, 1, "wrap")]),
                     C((-0.2, 5, -2.5), (4.4, 2.5, 1), "armor"),
                     C((-0.2, 0, -2.4), (4.4, 4.5, 4.6), "boots")],
        "right_arm_extra": [C((-7, -8, -0.5), (1, 34, 1), "gold", [("front", 0, 10, 1, 1, "red")]),
                            C((-7.5, 26, -1), (2, 2, 2), "gold"),
                            C((-7.25, 28, -0.75), (1.5, 6, 1.5), "gold"),
                            C((-9.5, 27, -0.5), (2, 1, 1), "gold"), C((-9.5, 28, -0.5), (1, 3, 1), "gold"),
                            C((-5.5, 27, -0.5), (2, 1, 1), "gold"), C((-4.5, 28, -0.5), (1, 3, 1), "gold")],
        "parts": [part("ponytail", "head", (0, 31, 3.5), [C((-1.5, 9, 3.5), (3, 22, 2.5), "hair"),
                                                          C((-1, 6, 3.6), (2, 3, 2), "hair"),
                                                          C((-1.25, 30, 3), (2.5, 1.5, 1.5), "flower")], rot=(8, 0, 0))],
    })


def oikatzo():
    """Oikatzo: messy blonde twin tails, hazel eyes and a red clip on the forehead, a red high collar
    over a cream sleeveless tunic, red-bandaged forearms with silver knuckle gauntlets, a red belt
    with a gold X buckle, dark brown shorts, bandaged calves and tall khaki boots."""
    return biped({
        "arm_width": 3,
        "head": [C((-4, 24, -4), (8, 8, 8), "skin", face(eyes="eye", y=4) + [("front", 0, 0, 8, 2, "hair"), ("front", 2.5, 0, 1, 3, "hair"),
                                                                            ("front", 5, 5.5, 1, 0.5, "scar")]),
                 C((-4.25, 26, -4.25), (8.5, 6.25, 8.5), "hair", [("front", 0.5, 2, 7, 4.25, None)]),
                 C((-2.5, 30, -4.4), (1.5, 1, 0.5), "red"),
                 C((-0.5, 32, -1), (1, 2, 1), "hair")],
        "body": [C((-4, 12, -2), (8, 12, 4), "tunic", [("front", 0, 8.5, 8, 1, "red"), ("front", 3.5, 8.5, 1, 1, "gold")]),
                 C((-3.5, 22, -2.6), (7, 2.5, 5.2), "red"),
                 C((-4.6, 21.5, -2.4), (2, 2, 4.8), "tunic"), C((2.6, 21.5, -2.4), (2, 2, 4.8), "tunic"),
                 C((-4.2, 9, -2.2), (8.4, 4, 4.4), "shorts")],
        "left_arm": [C((4, 12, -2), (3, 12, 4), "skin", [("front", 0, 5, 3, 7, "red"), ("back", 0, 5, 3, 7, "red"),
                                                         ("left", 0, 5, 4, 7, "red"), ("right", 0, 5, 4, 7, "red")]),
                     C((3.8, 16.5, -2.2), (3.4, 1.5, 4.4), "tunic"),
                     C((3.6, 11.5, -2.4), (3.8, 4.5, 4.8), "gauntlet", [("front", 0, 0.5, 3.8, 0.5, "steel"), ("front", 0, 2, 3.8, 0.5, "steel")])],
        "left_leg": [C((0, 0, -2), (4, 12, 4), "skin", [("front", 0, 0, 4, 3, "shorts"), ("front", 0, 4, 4, 3, "wrap")]),
                     C((-0.15, 8.5, -2.15), (4.3, 1.5, 4.3), "tunic"),
                     C((-0.2, 0, -2.3), (4.4, 5.5, 4.6), "boots", [("front", 0, 0, 4.4, 1, "trim")])],
        "parts": [
            part("left_tail", "head", (4, 30, 1), [C((3.5, 21, 0), (2.5, 9, 2.5), "hair"), C((3.75, 19, 0.25), (2, 2, 2), "hair")],
                 rot=(10, 0, -20)),
            part("right_tail", "head", (-4, 30, 1), [C((-6, 21, 0), (2.5, 9, 2.5), "hair"), C((-5.75, 19, 0.25), (2, 2, 2), "hair")],
                 rot=(10, 0, 20)),
        ],
    })


# =======================================================================================
# Goblins
# =======================================================================================

def goblin():
    """Goblins of the Rampant Forest: knee-high, olive skin, huge bat-like ears, round red eyes, a
    dark brow chevron and an underbite; a ragged tunic, a black sash, a three-claw necklace, bandaged
    shins and a stone hand axe. The hood is only painted for Redcaps."""
    eyes = [("front", 1, 2, 2, 1.5, "eye"), ("front", 4, 2, 2, 1.5, "eye"), ("front", 1.5, 2.5, 1, 0.5, "pupil"),
            ("front", 4.5, 2.5, 1, 0.5, "pupil"), ("front", 1, 1.2, 2, 0.5, "dark"), ("front", 4, 1.2, 2, 0.5, "dark"),
            ("front", 3, 0.7, 1, 0.5, "dark"), ("front", 1.5, 5.2, 4, 0.75, "mouth"),
            ("front", 2, 5, 0.5, 0.75, "teeth"), ("front", 3.25, 5, 0.5, 0.75, "teeth"), ("front", 4.5, 5, 0.5, 0.75, "teeth"),
            ("front", 3, 5.6, 1, 0.9, "tongue")]
    tunic_hem = [("front", 0.5, 5, 1, 1, None), ("front", 3, 5.2, 1.5, 0.8, None), ("front", 6, 5, 1, 1, None),
                 ("back", 1, 5, 1.5, 1, None), ("back", 5, 5.2, 1, 0.8, None)]
    m = [
        part("body", "root", (0, 8, 0), [
            C((-3.5, 8, -2), (7, 7, 4), "skin", [("front", 1.5, 1, 4, 0.5, "rope"), ("front", 2, 1.5, 0.75, 1, "claw"),
                                                 ("front", 3.1, 1.5, 0.75, 1.25, "claw"), ("front", 4.25, 1.5, 0.75, 1, "claw")]),
            C((-3.8, 6, -2.3), (7.6, 6, 4.6), "tunic", tunic_hem),
            C((-3.9, 8, -2.4), (7.8, 1, 4.8), "sash"),
        ], rot=(14, 0, 0)),
        part("head", "body", (0, 15, -1), [
            C((-3.5, 14.5, -4.5), (7, 6.5, 6.5), "skin", eyes),
            C((-0.8, 16, -5.7), (1.6, 2, 1.5), "skin"),
            C((-2.5, 20.5, -3.5), (5, 1, 5), "dark"),
            C((-3.8, 17, -3.5), (7.6, 4.3, 6), "hood", [("front", 0, 0, 7.6, 4.3, None)]),
            C((-1.5, 20.5, 2.5), (3, 2.5, 2), "hood"),
        ], rot=(-12, 0, 0)),
    ]
    m += pair(part("left_ear", "head", (3.5, 18, -1.5), [C((3.5, 17, -2.5), (6.5, 2.5, 1), "skin", [("front", 1, 0.5, 4.5, 1.5, "inner")])],
                   rot=(0, -25, -15)), "right_ear")
    m += pair(part("left_arm", "body", (3.8, 14, -0.5), [
        C((3.8, 7.5, -1.5), (2, 6.5, 2), "skin"),
        C((3.6, 6, -1.7), (2.4, 1.6, 2.4), "skin", [("front", 0, 1, 2.4, 0.6, "claw")]),
    ]), "right_arm")
    m.append(part("axe", "right_arm", (-4.8, 6.8, -0.5), [
        C((-5.3, 2, -1), (1, 10, 1), "wood", [("left", 0, 0, 1, 4, "wraps"), ("left", 0, 6, 1, 1, "rope"),
                                              ("right", 0, 0, 1, 4, "wraps")]),
        C((-6.9, 8.5, -1.5), (3.2, 3.5, 1), "stone", [("front", 0, 0, 3.2, 0.5, "edge"), ("front", 2.2, 1, 1, 1.5, "rope")]),
    ], rot=(70, 0, 0)))
    m += pair(part("left_leg", "root", (2, 8, 0), [
        C((0.8, 4, -1.2), (2.6, 4.5, 2.6), "skin"),
        C((1, 1, -1), (2.2, 3, 2.2), "wraps"),
        C((0.6, 0, -2.6), (3, 1, 4), "skin", [("front", 0, 0, 3, 1, "claw"), ("front", 0.9, 0, 0.4, 1, "skin"),
                                              ("front", 1.9, 0, 0.4, 1, "skin")]),
    ]), "right_leg")
    return m


# =======================================================================================
# Bugs: Empire Bees and the Quad Beetle
# =======================================================================================

def empire_bee():
    """Empire Bees: basketball-sized, a striped abdomen, a fluffy white ruff, a yellow face with big
    black compound eyes, black legs banded white, translucent wings and a pale stinger. The queen
    (a texture variant) wears a crown."""
    bands = lambda y: [(f, 0, y, 0.8, 0.5, "joint") for f in ("front", "back", "left", "right")]
    m = [
        part("body", "root", (0, 8, 0), [
            C((-3, 5, -3), (6, 6, 6), "chitin"),
            C((-3.8, 4.5, -3.6), (7.6, 7, 4), "ruff"),
        ]),
        part("abdomen", "body", (0, 8, 3), [
            C((-3.5, 3.5, 3), (7, 7, 9), "stripe", [("top", 0, 1.5, 7, 1.5, "band"), ("top", 0, 5, 7, 1.5, "band"),
                                                     ("left", 1.5, 0, 1.5, 7, "band"), ("left", 5, 0, 1.5, 7, "band"),
                                                     ("right", 1.5, 0, 1.5, 7, "band"), ("right", 5, 0, 1.5, 7, "band"),
                                                     ("bottom", 0, 1.5, 7, 1.5, "band"), ("bottom", 0, 5, 7, 1.5, "band")]),
            C((-2, 5, 12), (4, 4, 2), "stripe", [("back", 0, 0, 4, 4, "band")]),
            C((-0.5, 6.5, 14), (1, 1, 3), "sting"),
        ], rot=(-14, 0, 0)),
        part("head", "body", (0, 9, -3), [
            C((-3, 6, -8), (6, 6, 5), "face", [("front", 2, 0.5, 2, 1, "chitin")]),
            C((-3.7, 7, -7.8), (2, 4.5, 3.5), "eye"), C((1.7, 7, -7.8), (2, 4.5, 3.5), "eye"),
            C((-1.5, 6, -8.6), (3, 1, 1), "mandible"),
        ]),
        part("crown", "head", (0, 12, -5.5), [
            C((-2.5, 12, -7), (5, 2, 4), "crown", [("front", 0, 0, 1, 1, "gem"), ("front", 2, 0, 1, 1, "gem"), ("front", 4, 0, 1, 1, "gem")]),
        ]),
    ]
    m += pair(part("left_antenna", "head", (1, 12, -7), [C((0.6, 12, -7.5), (0.8, 4, 0.8), "chitin"),
                                                          C((0.45, 15.5, -7.65), (1.1, 1, 1.1), "chitin")], rot=(35, 0, 20)),
              "right_antenna")
    m += pair(part("left_wing", "body", (1.5, 11, -1), [C((1.5, 11, -1.5), (9, 0.5, 6), "wing", [("top", 0, 0, 9, 0.5, "vein")])],
                   rot=(0, -10, -12)), "right_wing")
    for i, z in enumerate((-2.5, 0, 2.5)):
        m += pair(part(f"leg_l{i}", "body", (2.5, 5.5, z), [C((2.3, 1, z - 0.4), (0.8, 4.5, 0.8), "chitin", bands(2))], rot=(0, 0, -25)),
                  f"leg_r{i}")
    return m


def quad_beetle():
    """The Quad Beetle of the Prismatic Forest Grotto: a glossy midnight-blue beetle with glowing
    yellow-green eyes, two long flattened horns sweeping up and forward like scythes and a lower pair
    of curved mandible horns framing the mouth."""
    m = [
        part("body", "root", (0, 10, 4), [
            C((-11, 6, -6), (22, 11, 27), "shell", [("top", 10.75, 0, 0.5, 27, "seam"), ("back", 10.75, 0, 0.5, 11, "seam"),
                                                     ("top", 3, 3, 2.5, 19, "shine"), ("top", 16.5, 3, 2.5, 19, "shine"),
                                                     ("left", 2, 2, 22, 1, "shine"), ("right", 3, 2, 22, 1, "shine")]),
            C((-10, 4, -5), (20, 3, 25), "under"),
            C((-9, 15.5, -5), (18, 2, 22), "shell"),
            C((-8.5, 8, -10), (17, 8.5, 4.5), "shell2", [("top", 2, 1, 13, 1, "shine")]),
        ]),
        part("head", "body", (0, 10, -9), [
            C((-7, 5, -17), (14, 9, 8), "shell2", [("front", 0.5, 2.5, 3, 2.5, "eye"), ("front", 10.5, 2.5, 3, 2.5, "eye"),
                                                   ("top", 2, 1, 10, 1, "shine")]),
            C((-4, 4, -18), (8, 3, 2), "under"),
        ]),
    ]
    m += pair(part("horn_l", "head", (3.5, 13, -14), [C((2, 13, -16.5), (3, 11, 5), "horn", [("left", 1, 0, 1.5, 11, "shine")])],
                   rot=(20, 0, 16)), "horn_r")
    m += pair(part("horn_l2", "horn_l", (3.5, 24, -14), [C((2.25, 24, -16), (2.5, 10, 4), "horn", [("left", 1, 0, 1.5, 10, "shine")])],
                   rot=(30, 0, -8)), "horn_r2", {"horn_l": "horn_r"})
    m += pair(part("horn_l3", "horn_l2", (3.5, 34, -14), [C((2.5, 34, -15.5), (2, 9, 3), "horn", [("front", 0, 6.5, 2, 2.5, "hornlight")])],
                   rot=(38, 0, -12)), "horn_r3", {"horn_l2": "horn_r2"})
    m += pair(part("tusk_l", "head", (4, 6, -17), [C((3, 5.25, -24.5), (2, 1.5, 7.5), "horn")], rot=(8, -28, 0)), "tusk_r")
    m += pair(part("tusk_l2", "tusk_l", (4, 6, -24.5), [C((3.25, 5.4, -29), (1.5, 1.2, 4.5), "horn", [("top", 0, 0, 1.5, 1.5, "hornlight")])],
                   rot=(0, 45, 0)), "tusk_r2", {"tusk_l": "tusk_r"})
    for i, z in enumerate((-2, 6, 14)):
        leg = part(f"leg_l{i}", "body", (10.5, 8, z), [
            C((10.5, 7, z - 1), (8, 2, 2), "under", [("top", 3.5, 0, 1, 2, "shell")]),
            C((17.5, 0, z - 0.8), (1.6, 8, 1.6), "under", [("front", 0, 3.5, 1.6, 0.5, "shell"), ("front", 0, 7, 1.6, 1, "claw")]),
        ], rot=(0, (i - 1) * -25, -18))
        m += pair(leg, f"leg_r{i}")
    return m


# =======================================================================================
# The Mud Digger
# =======================================================================================

def mud_digger():
    """The Mud Digger, area boss of the Diremarsh Waste: a semi-upright chimera with a great shark's
    head, gills, dorsal fin and tail on the squat body of a mole, short digging claws, long webbed
    hind legs and two long catfish barbels."""
    y = 6
    m = [
        part("body", "root", (0, 10 + y, 8), [
            C((-10, 4 + y, -8), (20, 15, 20), "camo"),
            C((-8.5, 2.5 + y, -6), (17, 3, 16), "belly"),
            C((-9, 6 + y, -14), (18, 12, 7), "camo", [("bottom", 2, 1, 14, 5, "belly")]),
        ], rot=(-26, 0, 0)),
        part("dorsal_fin", "body", (0, 19 + y, 2), [
            C((-1, 18 + y, -2), (2, 10, 9), "fin", [("left", 0, 0, 9, 2, "fintip"), ("right", 0, 0, 9, 2, "fintip"),
                                                    ("top", 0, 0, 2, 9, "fintip")]),
        ], rot=(-25, 0, 0)),
        part("head", "body", (0, 13 + y, -14), [
            C((-8, 6 + y, -29), (16, 11, 15), "camo", [("left", 3, 3, 0.5, 5, "gill"), ("left", 4.5, 3, 0.5, 5, "gill"),
                                                       ("left", 6, 3, 0.5, 5, "gill"), ("right", 8.5, 3, 0.5, 5, "gill"),
                                                       ("right", 10, 3, 0.5, 5, "gill"), ("right", 11.5, 3, 0.5, 5, "gill")]),
            C((-6.5, 8 + y, -35), (13, 8, 6), "camo", [("left", 1.5, 1.5, 1.5, 1.5, "eye"), ("right", 3, 1.5, 1.5, 1.5, "eye"),
                                                       ("front", 0, 7, 13, 1, "teeth"), ("bottom", 0, 0, 13, 1, "teeth")]),
        ]),
        part("jaw", "head", (0, 8 + y, -14), [
            C((-7.5, 3.5 + y, -34.5), (15, 4.5, 20.5), "belly", [("front", 0, 0, 15, 1, "teeth"), ("top", 0, 0, 15, 1, "teeth"),
                                                                ("left", 0, 0, 12, 0.75, "teeth"), ("right", 8.5, 0, 12, 0.75, "teeth")]),
        ]),
        part("tail", "body", (0, 12 + y, 12), [C((-5, 8 + y, 12), (10, 8, 10), "camo")], rot=(18, 0, 0)),
        part("tail2", "tail", (0, 12 + y, 21), [C((-3, 9.5 + y, 21), (6, 5, 9), "camo")], rot=(10, 0, 0)),
        part("tail_fin", "tail2", (0, 12 + y, 29), [
            C((-1, 3 + y, 28.5), (2, 19, 6), "fin", [("left", 0, 0, 6, 3, "fintip"), ("right", 0, 0, 6, 3, "fintip"),
                                                     ("left", 0, 16, 6, 3, "fintip"), ("right", 0, 16, 6, 3, "fintip")]),
        ], rot=(-20, 0, 0)),
    ]
    m += pair(part("whisker_l", "head", (5.5, 11 + y, -33), [C((5.5, 10.75 + y, -33.25), (15, 0.5, 0.5), "whisker")],
                   rot=(0, -40, 22)), "whisker_r")
    m += pair(part("claw_fl", "body", (8.5, 8 + y, -10), [
        C((7, 1 + y, -13), (6, 8, 6), "camo"),
        C((6.5, -2 + y, -17), (7, 3.5, 6), "claw", [("front", 0.5, 0, 1, 3.5, "clawdark"), ("front", 3, 0, 1, 3.5, "clawdark"),
                                                    ("front", 5.5, 0, 1, 3.5, "clawdark")]),
    ], rot=(20, 0, -10)), "claw_fr")
    m += pair(part("leg_bl", "root", (7.5, 14, 10), [
        C((4.5, 7, 6.5), (6, 8, 7), "camo"),
        C((5.5, 1.5, 8.5), (4, 6, 4), "camo"),
        C((3.5, 0, 3.5), (8, 1.5, 9), "web", [("top", 1.5, 0, 0.5, 6, "clawdark"), ("top", 3.75, 0, 0.5, 6, "clawdark"),
                                              ("top", 6, 0, 0.5, 6, "clawdark")]),
    ]), "leg_br")
    return m


# =======================================================================================
# New story bosses
# =======================================================================================

def ravenous_snake():
    """The Ravenous Snake guarding the bridge to Secondil: green diamond scales, pale belly bands,
    yellow eyes and flowing white hair on its head and tail, tied with red beads. The tail hair opens
    like a flower to spray poison."""
    m = [
        part("head", "root", (0, 6, -6), [
            C((-5, 1.5, -17), (10, 8, 11), "scales", [("left", 1, 2, 2.5, 1.5, "eye"), ("right", 7.5, 2, 2.5, 1.5, "eye"),
                                                       ("front", 0, 6.5, 10, 1.5, "belly")]),
            C((-4, 2, -22), (8, 5.5, 5), "scales", [("front", 2, 1, 1, 1, "nostril"), ("front", 5, 1, 1, 1, "nostril")]),
            C((-5.5, 8, -15.5), (11, 1.5, 8), "scales"),
        ]),
        part("jaw", "head", (0, 2.5, -8), [
            C((-4.5, 0, -21.5), (9, 2.5, 13.5), "belly", [("front", 0, 0, 9, 0.75, "fang"), ("top", 0, 0, 9, 0.75, "fang")]),
        ]),
        part("tongue", "jaw", (0, 1.5, -21.5), [C((-0.5, 1.2, -26), (1, 0.5, 5), "tongue", [("top", 0, 0, 0.3, 1.5, None),
                                                                                           ("top", 0.7, 0, 0.3, 1.5, None)])]),
        part("hair", "head", (0, 9.5, -10), [
            C((-3.5, 8.5, -11), (7, 3, 12), "hair", [("top", 0, 3, 7, 1, "bead"), ("top", 0, 8, 7, 1, "bead")]),
        ], rot=(10, 0, 0)),
        part("hair2", "hair", (0, 10, 1), [
            C((-3, 8, 0.5), (6, 3, 12), "hair", [("top", 0, 5, 6, 1, "bead"), ("left", 0, 0, 12, 3, "hair")]),
        ], rot=(6, 0, 0)),
    ]
    prev = "head"
    sizes = [(10, 9), (11, 9.5), (11.5, 10), (11, 9.5), (10, 9), (9, 8), (8, 7), (6.5, 6), (5, 5), (3.5, 3.5)]
    bend = [0, 18, 18, -12, -24, -18, 12, 24, 18, 0]
    z = -6
    for i, (w, h) in enumerate(sizes):
        name = f"seg{i}"
        decals = [("bottom", 0, 0, w, 10, "belly"), ("left", 0, h - 1.5, 10, 1.5, "belly"), ("right", 0, h - 1.5, 10, 1.5, "belly")]
        m.append(part(name, prev, (0, h / 2, z), [C((-w / 2, 0, z), (w, h, 10), "scales", decals)], rot=(0, bend[i], 0)))
        prev = name
        z += 10
    # The tail plume: three locks of white hair that open like a flower.
    for nm, rot in (("tail_hair", (25, 0, 0)), ("tail_hair_l", (-5, 30, 0)), ("tail_hair_r", (-5, -30, 0))):
        m.append(part(nm, prev, (0, 1.75, z), [
            C((-1.5, 0.5, z), (3, 2.5, 9), "hair", [("top", 0, 1, 3, 1, "bead")]),
            C((-2, 0.25, z + 8), (4, 3, 5), "hair"),
        ], rot=rot))
    return m


def woodmage():
    """The Aberrant Woodmage (Vorpal Colosseum, round 10): a figure of dark wood with a bony
    ribcage, branch hair and glowing red eyes, in a torn white lab coat and a purple pointed hat.
    Four living chains hang from its body and it carries a staff tipped with a blue orb."""
    ribs = [("front", 2, 2 + i * 2, 6, 0.75, "bone") for i in range(4)] + [("front", 4.75, 1.5, 0.5, 8.5, "bone")]
    m = [
        part("body", "root", (0, 18, 0), [
            C((-5, 18, -3.5), (10, 14, 7), "bark", ribs),
            C((-7, 14, -5), (14, 18, 10), "coat", [("front", 3.5, 0, 7, 18, None), ("front", 0, 16, 2, 2, None),
                                                   ("front", 12, 15.5, 2, 2.5, None), ("back", 2, 15, 2, 3, None),
                                                   ("back", 9, 16, 3, 2, None), ("left", 4, 16, 2, 2, None),
                                                   ("right", 6, 15.5, 3, 2.5, None)]),
            C((-7.5, 31, -5.5), (15, 2.5, 11), "coat", [("front", 4, 0, 7, 2.5, None)]),
        ]),
        part("head", "body", (0, 33, 0), [
            C((-3.5, 33, -4), (7, 8, 7), "bark", [("front", 1, 3, 1.5, 1.5, "eye"), ("front", 4.5, 3, 1.5, 1.5, "eye"),
                                                  ("front", 2, 6, 3, 0.5, "bark2"), ("front", 1, 2.5, 2, 0.5, "bark2")]),
            C((3.5, 38.5, -2), (3.5, 1, 1), "twig"), C((5.5, 39.5, -1.5), (1, 2, 1), "twig"),
            C((-7, 38, -1), (3.5, 1, 1), "twig"), C((-7, 39, -0.5), (1, 2.5, 1), "twig"),
            C((-1, 39, 3), (1, 1, 3.5), "twig"), C((1.5, 37, 3), (1, 1, 2.5), "twig"),
        ]),
        part("hat", "head", (0, 41, 0), [
            C((-8, 40.5, -8), (16, 1, 16), "hat", [("top", 0, 0, 1.5, 1.5, None), ("top", 14.5, 14.5, 1.5, 1.5, None),
                                                   ("top", 15, 0, 1, 2, None)]),
            C((-5, 41.5, -5), (10, 4, 10), "hat", [("front", 0, 2.5, 10, 1, "band"), ("left", 0, 2.5, 10, 1, "band"),
                                                   ("right", 0, 2.5, 10, 1, "band"), ("back", 0, 2.5, 10, 1, "band")]),
        ]),
        part("hat2", "hat", (0, 45.5, 0), [C((-3.5, 45.5, -3.5), (7, 4, 7), "hat")], rot=(-10, 0, 8)),
        part("hat3", "hat2", (0, 49.5, 0), [C((-2, 49.5, -2), (4, 4, 4), "hat")], rot=(-14, 0, 10)),
        part("hat4", "hat3", (0, 53.5, 0), [C((-1, 53.5, -1), (2, 4, 2), "hat")], rot=(-18, 0, 12)),
    ]
    arm = part("left_arm", "body", (7.5, 31, 0), [
        C((6.5, 21, -2.5), (4.5, 11, 5), "coat", [("front", 0, 9.5, 2, 1.5, None), ("back", 2.5, 10, 2, 1, None)]),
        C((7, 18, -1.5), (3.5, 3, 3), "bark"),
        C((7.25, 15.5, -1.25), (1, 2.5, 1), "twig"), C((9.25, 15.5, -1.25), (1, 2.5, 1), "twig"),
        C((8.25, 16, 0.75), (1, 2, 1), "twig"),
    ], rot=(0, 0, -8))
    m += pair(arm, "right_arm")
    right = next(p for p in m if p["name"] == "right_arm")
    right["rot"] = (-30, 0, 8)
    m.append(part("staff", "right_arm", (-8.75, 19.5, 0), [
        C((-9.25, 4, -0.5), (1, 30, 1), "staffwood", [("front", 0, 22, 1, 1, "band")]),
        C((-10.75, 33.5, -1.5), (3, 1, 3), "staffwood"),
        C((-10.75, 34.5, -1.5), (3, 3, 3), "orb"),
        C((-10.25, 37.5, -1), (0.75, 1.5, 0.75), "staffwood"), C((-8.5, 37.5, 0.25), (0.75, 1.5, 0.75), "staffwood"),
    ], rot=(30, 0, 0)))
    # Four living chains hanging from the body.
    for nm, (x, z) in (("chain_fl", (-3.5, -4.5)), ("chain_fr", (3.5, -4.5)), ("chain_bl", (-3, 4.5)), ("chain_br", (3, 4.5))):
        m.append(part(nm, "body", (x, 19, z), [
            C((x - 0.5, 13, z - 0.5), (1, 6, 1), "chain"),
            C((x - 0.75, 7, z - 0.75), (1.5, 6, 1.5), "chain"),
            C((x - 1, 4, z - 1), (2, 3, 2), "iron"),
        ], rot=(0, 0, -6 if x > 0 else 6)))
    m += pair(part("left_leg", "root", (3, 18, 0), [
        C((0.5, 6, -3), (5, 12, 6), "bark"),
        C((0, 0, -4.5), (6, 6, 9), "bark", [("front", 1, 3, 1, 3, "bark2"), ("front", 4, 2, 1, 4, "bark2")]),
        C((5, 0, -2), (3, 2, 3), "bark2"),
    ]), "right_leg")
    return m


MODELS = {
    "lycagon": lycagon(),
    "wezaemon": wezaemon(),
    "kirin": kirin(),
    "vorpal_bunny": vorpal_bunny(),
    "emul": emul(),
    "vysache": vysache(),
    "bilac": bilac(),
    "setsuna": setsuna(),
    "guide": guide(),
    "smith": smith(),
    "pencilgon": pencilgon(),
    "oikatzo": oikatzo(),
    "goblin": goblin(),
    "empire_bee": empire_bee(),
    "quad_beetle": quad_beetle(),
    "mud_digger": mud_digger(),
    "ravenous_snake": ravenous_snake(),
    "woodmage": woodmage(),
}

# Materials: name -> (hex colour, noise[, style[, options]]). Styles are painted by
# tools/creatures.py (fur, shag, hair, armor, metal, screw, cloth, skin, chitin, scale,
# camo, wood, glow, feather, glass). A colour of None leaves texels transparent.
SKIN = ("#F0CDA8", 3, "skin")
EYE_DARK = ("#2A2A3A", 0)
WHITE = ("#FFFFFF", 0)

VARIANTS = [
    ("lycagon", "lycagon", {
        "fur": ("#1A1626", 6, "fur"), "fur2": ("#272038", 6, "fur"), "mane": ("#110E1A", 6, "shag", {"tip": "#3E1A58"}),
        "shag": ("#16121F", 6, "shag", {"tip": "#4A1C66"}), "mark": ("#EDE3A0", 0, "glow", {"glow": True}),
        "eye": ("#FF8A1E", 0, "glow", {"glow": True}), "nose": ("#08080A", 2), "teeth": ("#E8E2D0", 4),
        "jaw": ("#1A1424", 6, "fur"), "claw": ("#D8D0E0", 4), "inner": ("#2A1438", 6, "fur"), "tip": ("#B040E0", 6, "glow", {"glow": True})}),
    ("lycagon", "shadow_wolf", {
        "fur": ("#241C3C", 8, "fur"), "fur2": ("#2E2448", 8, "fur"), "mane": ("#1A1430", 8, "shag", {"tip": "#5A2A90"}),
        "shag": ("#201838", 8, "shag", {"tip": "#7A3AC0"}), "mark": ("#C090FF", 0, "glow", {"glow": True}),
        "eye": ("#C890FF", 0, "glow", {"glow": True}), "nose": ("#0A0A10", 2), "teeth": ("#D0C8E8", 4),
        "jaw": ("#1E1834", 6, "fur"), "claw": ("#A098C0", 4), "inner": ("#3A2060", 6, "fur"), "tip": ("#B070FF", 6, "glow", {"glow": True})}),
    ("lycagon", "lycagon_shadow", {
        "fur": ("#0A0814", 4, "fur", {"translucent": 200}), "fur2": ("#120E22", 4, "fur", {"translucent": 200}),
        "mane": ("#06050C", 4, "shag", {"tip": "#5A1E90", "translucent": 200}), "shag": ("#08060E", 4, "shag", {"tip": "#7A2AC0", "translucent": 200}),
        "mark": ("#B070FF", 0, "glow", {"glow": True}), "eye": ("#FF60E0", 0, "glow", {"glow": True}), "nose": ("#000000", 0),
        "teeth": ("#C8B8E0", 2), "jaw": ("#0C0A16", 4, "fur"), "claw": ("#9080C0", 2), "inner": ("#2A1450", 4, "fur"),
        "tip": ("#D070FF", 6, "glow", {"glow": True})}),
    ("wezaemon", "wezaemon", {
        "armor": ("#E8ECF0", 4, "armor"), "under": ("#1C1F27", 6, "cloth"), "screw": ("#8C9099", 4, "screw"),
        "thigh": ("#3A3E46", 4, "rib"), "band": ("#D8553A", 4, "cloth"), "crest": ("#141418", 2, "armor"),
        "face": ("#F2F4F6", 2, "armor"), "eye": ("#6FF0FF", 0, "glow", {"glow": True}), "seam": ("#9AA2AE", 2),
        "bolt": ("#5A606A", 2, "metal"), "ribbon": ("#2FB5A8", 6, "cloth"), "sheath": ("#121216", 3, "armor"),
        "gold": ("#C9B07A", 4, "metal"), "hilt": ("#B8303A", 6, "cloth"), "steel": ("#D8E2EA", 4, "metal"), "edge": ("#FFFFFF", 0)}),
    ("kirin", "kirin", {
        "armor": ("#E8ECF0", 4, "armor"), "joint": ("#1E2128", 6, "metal"), "cable": ("#4AE0F0", 0, "glow", {"glow": True}),
        "eye": ("#8FF6FF", 0, "glow", {"glow": True}), "seam": ("#A0A8B4", 2), "teeth": ("#D8DCE0", 2),
        "hoof": ("#30343C", 4, "metal")}),
    ("vorpal_bunny", "vorpal_bunny", {
        "fur": ("#B6A994", 8, "fur"), "belly": ("#E8E2D6", 6, "fur"), "scarf": ("#9A1E28", 8, "cloth"),
        "eye": ("#C0102A", 0), "nose": ("#E8A0A8", 0), "pad": ("#F4F0EA", 0), "inner": ("#E8B0B8", 4, "fur"),
        "puff": ("#F2F0EC", 6, "fur"), "grip": ("#3A2418", 4, "wood"), "blade": ("#B0182A", 6, "metal"), "edge": ("#FF6A70", 0)}),
    ("emul", "emul", {
        "fur": ("#F6F2F4", 5, "fur"), "coat": ("#2E5FBF", 6, "cloth"), "tails": ("#8A1E2A", 6, "cloth"),
        "bow": ("#D8283A", 4, "cloth"), "gold": ("#E8C24A", 2, "metal"), "eye": ("#E0502A", 0), "shine": WHITE,
        "nose": ("#F09AB0", 0), "mouth": ("#B04A5A", 0), "blush": ("#F8C4D4", 0), "inner": ("#F4B4C8", 4, "fur"),
        "hat": ("#1E2A5A", 4, "cloth"), "band": ("#F4F4F4", 2), "gem": ("#5AC8FF", 0), "puff": ("#FFFFFF", 4, "fur"),
        "pad": ("#F4B8C8", 0)}),
    ("vysache", "vysache", {
        "fur": ("#CFCBC6", 8, "fur"), "muzzle": ("#EEEAE4", 6, "fur"), "ruff": ("#F4F2EE", 6, "shag"),
        "kimono": ("#1A1A20", 6, "cloth"), "tie": ("#1E9A8A", 4, "cloth"), "jade": ("#1A1A20", 4), "bead": ("#3FBF7A", 2),
        "lining": ("#6A3A9A", 2),
        "fluff": ("#F4F2EE", 6, "fur"), "cape": ("#F0A8C8", 6, "cloth"), "motif": ("#C8306A", 2), "trim": ("#FFFFFF", 2),
        "hakama": ("#16161C", 6, "cloth"), "swirl": ("#D8402A", 2), "eye": ("#8A1A1A", 0), "brow": ("#6A6660", 0),
        "scar": ("#8A6A66", 0), "nose": ("#3A2A2A", 0), "inner": ("#E8B0B8", 4, "fur"), "pipe": ("#1A1210", 2, "wood"),
        "gold": ("#D8B04A", 2, "metal")}),
    ("bilac", "bilac", {
        "fur": ("#6A3E22", 8, "fur"), "muzzle": ("#A8744A", 6, "fur"), "wrap": ("#EDE6DA", 4, "cloth"),
        "overalls": ("#F07A1E", 8, "cloth"), "pocket": ("#C85A10", 2), "eye": ("#F2C230", 0), "nose": ("#2A1A12", 0),
        "inner": ("#C8907A", 4, "fur"), "wood": ("#6A4A2A", 4, "wood"), "iron": ("#5A5E66", 6, "metal"), "shine": ("#B8C0C8", 0)}),
    ("setsuna", "setsuna", {
        "skin": ("#F6E2D4", 3, "skin", {"translucent": 190}), "hair": ("#8A5A36", 6, "hair", {"translucent": 190}),
        "hair2": ("#6A4428", 2, "flat", {"translucent": 190}), "eye": ("#3A2A26", 0, "flat", {"translucent": 190}),
        "mark": ("#6A4A3A", 0, "flat", {"translucent": 190}), "trim": ("#7AD0B8", 0, "flat", {"translucent": 190}),
        "belt": ("#6A4428", 2, "flat", {"translucent": 190}), "collar": ("#2A3A30", 0, "flat", {"translucent": 190}),
        "white": ("#FFFFFF", 0, "flat", {"translucent": 190}), "mouth": ("#C08080", 0, "flat", {"translucent": 190}),
        "coat": ("#F4F4F2", 4, "cloth", {"translucent": 190}), "stripe": ("#C02A3A", 0, "flat", {"translucent": 190}),
        "turtleneck": ("#A8D8B8", 6, "cloth", {"translucent": 190}), "trousers": ("#B49A72", 6, "cloth", {"translucent": 190}),
        "boots": ("#5A3A22", 4, "cloth", {"translucent": 190})}),
    ("guide", "frontier_guide", {
        "skin": SKIN, "hair": ("#6B4226", 6, "hair"), "eye": ("#2A3A6A", 0), "white": WHITE, "mouth": ("#A06050", 0),
        "hat": ("#4A3020", 4, "cloth"), "trim": ("#E8C86A", 2), "shirt": ("#E8E0D0", 4, "cloth"), "coat": ("#2E5E9E", 6, "cloth"),
        "belt": ("#3A2616", 2), "scarf": ("#D8A040", 6, "cloth"), "satchel": ("#7A5030", 6, "cloth"), "pants": ("#3A3226", 6, "cloth"),
        "boots": ("#2A1E14", 4, "cloth"), "sleeve": ("#2E5E9E", 6, "cloth")}),
    ("smith", "frontier_blacksmith", {
        "skin": ("#D9A878", 4, "skin"), "beard": ("#5A3A22", 8, "hair"), "eye": ("#1E1E1E", 0), "white": WHITE,
        "mouth": ("#7A4A3A", 0), "brow": ("#3A2616", 0), "bandana": ("#A8322A", 6, "cloth"), "shirt": ("#8A7A66", 6, "cloth"),
        "apron": ("#5A3E28", 8, "cloth"), "metal": ("#7A7E86", 6, "metal"), "belt": ("#2A1E14", 2), "glove": ("#3A2A1E", 6, "cloth"),
        "pants": ("#2E2A26", 6, "cloth"), "boots": ("#1E1610", 4, "cloth"), "wood": ("#6A4A2A", 4, "wood")}),
    ("pencilgon", "pencilgon", {
        "skin": SKIN, "hair": ("#7A3ABF", 6, "hair"), "eye": ("#5AA8E8", 0), "white": ("#F4F2F0", 4, "cloth"),
        "mouth": ("#C07070", 0), "flower": ("#FFFFFF", 2), "gold": ("#E8C24A", 2, "metal"), "armor": ("#D8DCE0", 3, "armor"),
        "red": ("#8A1A26", 6, "cloth"), "stocking": ("#C84A5A", 4, "cloth"), "wrap": ("#F4F2F0", 2),
        "boots": ("#1E1E24", 4, "armor")}),
    ("oikatzo", "oikatzo", {
        "skin": SKIN, "hair": ("#F2D04A", 6, "hair"), "eye": ("#B08A2A", 0), "white": WHITE, "mouth": ("#C07070", 0),
        "scar": ("#D89A80", 0), "tunic": ("#E8DCC0", 6, "cloth"), "red": ("#C8302A", 4, "cloth"), "gold": ("#E8C24A", 2),
        "shorts": ("#3A2A20", 4, "cloth"), "gauntlet": ("#C8CED6", 4, "armor"), "steel": ("#8A929E", 2),
        "wrap": ("#F2EEE6", 2), "boots": ("#C8B48A", 6, "cloth"), "trim": ("#A8946A", 2)}),
    ("goblin", "goblin", {
        "skin": ("#6E7E3A", 8, "skin"), "dark": ("#46521F", 6, "skin"), "eye": ("#C8202A", 0), "pupil": ("#3A0A0A", 0),
        "mouth": ("#2A1A12", 0), "teeth": ("#E8E4C8", 2), "tongue": ("#2A1A12", 0), "inner": ("#B87A6A", 4, "skin"),
        "tunic": ("#9A6A5E", 8, "cloth"), "sash": ("#1E1A1A", 4, "cloth"), "rope": ("#3A2616", 4), "claw": ("#E8E2D0", 2),
        "wraps": ("#C8B898", 6, "cloth"), "wood": ("#6A4A2A", 4, "wood"), "stone": ("#8A8A86", 8, "metal"),
        "edge": ("#C8C8C0", 0), "hood": (None, 0)}),
    ("goblin", "redcap_goblin", {
        "skin": ("#5A5A2E", 8, "skin"), "dark": ("#A8202A", 6, "cloth"), "eye": ("#F2ECD8", 0), "pupil": ("#1A0A08", 0),
        "mouth": ("#1A0A08", 0), "teeth": ("#F2F0E0", 2), "tongue": ("#C8405A", 0), "inner": ("#9A5A4A", 4, "skin"),
        "tunic": ("#3A2048", 8, "cloth"), "sash": ("#1A1018", 4, "cloth"), "rope": ("#A8202A", 4), "claw": ("#E8E0C8", 2),
        "wraps": ("#5A2A2A", 6, "cloth"), "wood": ("#4A3220", 4, "wood"), "stone": ("#8A9098", 6, "metal"),
        "edge": ("#E8E8F0", 0), "hood": ("#A01C26", 6, "cloth")}),
    ("empire_bee", "empire_bee", {
        "chitin": ("#1E1C1A", 4, "chitin"), "ruff": ("#E0F0F4", 6, "shag"), "stripe": ("#C8D040", 8, "chitin"),
        "band": ("#1E1C18", 4, "chitin"), "eye": ("#101418", 2, "chitin"), "mandible": ("#3A2A1A", 2),
        "face": ("#E8C840", 4, "chitin"), "joint": ("#E8E8E0", 0), "sting": ("#E8E0D0", 2),
        "wing": ("#D8F0FF", 4, "glass", {"alpha": 120}), "vein": ("#A0C0D8", 0, "glass", {"alpha": 180}),
        "crown": (None, 0), "gem": (None, 0)}),
    ("empire_bee", "empire_bee_queen", {
        "chitin": ("#1E1614", 4, "chitin"), "ruff": ("#F8F6F0", 6, "shag"), "stripe": ("#F0DC98", 8, "chitin"),
        "band": ("#D8502A", 4, "chitin"), "eye": ("#8A1010", 2, "chitin"), "mandible": ("#5A2A1A", 2),
        "face": ("#F0D060", 4, "chitin"), "joint": ("#F4F4EC", 0), "sting": ("#F0E8D8", 2),
        "wing": ("#FFE8F4", 4, "glass", {"alpha": 130}), "vein": ("#E0B0C8", 0, "glass", {"alpha": 190}),
        "crown": ("#F2D04A", 4, "metal"), "gem": ("#C0283A", 0, "glow", {"glow": True})}),
    ("quad_beetle", "quad_beetle", {
        "shell": ("#162E5C", 6, "chitin"), "shell2": ("#12264E", 6, "chitin"), "seam": ("#08142A", 2),
        "shine": ("#3A64B0", 4, "chitin"), "under": ("#0C1224", 6, "chitin"), "horn": ("#1C3C78", 6, "chitin"),
        "hornlight": ("#4A78C8", 2, "chitin"), "eye": ("#C8F04A", 0, "glow", {"glow": True}), "claw": ("#5A6A8A", 2)}),
    ("mud_digger", "mud_digger", {
        "camo": ("#5E6E3E", 8, "camo", {"alt": "#3E4A28"}), "belly": ("#C8C0A0", 6, "skin"), "fin": ("#4E5E36", 6, "camo", {"alt": "#34401E"}),
        "fintip": ("#8A4A26", 6, "camo", {"alt": "#6A361A"}), "eye": ("#3A7AE0", 0), "gill": ("#2A3220", 0),
        "teeth": ("#F4F2EA", 2), "whisker": ("#2A2A22", 2), "claw": ("#D8D0B8", 4, "chitin"), "clawdark": ("#8A8270", 2),
        "web": ("#4E5A32", 6, "skin")}),
    ("ravenous_snake", "ravenous_snake", {
        "scales": ("#3E7A4A", 8, "scale"), "belly": ("#D8E0B0", 6, "scale"), "eye": ("#F2E040", 0, "glow", {"glow": True}),
        "nostril": ("#1A2A1A", 0), "fang": ("#F4F2EA", 0), "tongue": ("#C02A4A", 2),
        "hair": ("#F2F2EE", 4, "hair", {"tip": "#D8DCE8"}), "bead": ("#C8202A", 2)}),
    ("woodmage", "woodmage", {
        "bark": ("#3A2618", 8, "wood"), "bark2": ("#22160C", 4, "wood"), "twig": ("#4A3220", 6, "wood"),
        "bone": ("#E8E0C8", 4), "coat": ("#E8E6E0", 6, "cloth"), "eye": ("#FF3A3A", 0, "glow", {"glow": True}),
        "hat": ("#3A2458", 6, "cloth"), "band": ("#7A4AA8", 2), "chain": ("#7A7E86", 6, "metal"), "iron": ("#4A4E56", 6, "metal"),
        "staffwood": ("#5A3A22", 4, "wood"), "orb": ("#4AA8FF", 0, "glow", {"glow": True})}),
]

# Draws the pictures of the exercises the bundled photo set doesn't have, start (0.webp) and end (1.webp)
# position, 480x320 like the photos:  python3 tools/draw_exercises.py app/src/main/assets/exercises
import os
import sys

from PIL import Image, ImageDraw

S = 3  # drawn at 3x, then scaled down for smooth edges
W, H = 480, 320

SKIN = (232, 180, 138)
SHIRT = (214, 69, 65)
SHORTS = (43, 47, 58)
SHOE = (30, 30, 34)
HAIR = (84, 58, 40)
STEEL = (150, 156, 166)
DARK = (70, 74, 84)
PAD = (52, 56, 66)
WOOD = (196, 146, 92)
WOOD_D = (160, 112, 64)
CABLE = (20, 20, 24)


def P(x, y):
    return (x * S, y * S)


def limb(d, a, b, w, color):
    d.line([P(*a), P(*b)], fill=color, width=int(w * S))
    for p in (a, b):
        r = w * S / 2
        d.ellipse([p[0] * S - r, p[1] * S - r, p[0] * S + r, p[1] * S + r], fill=color)


def room():
    img = Image.new("RGB", (W * S, H * S), (226, 223, 218))
    d = ImageDraw.Draw(img)
    d.rectangle([P(0, 282), P(W, H)], fill=(58, 60, 66))
    d.rectangle([P(0, 280), P(W, 284)], fill=(90, 92, 98))
    return img, d


def shoe(d, ankle):
    x, y = ankle
    d.rounded_rectangle([P(x - 6, y - 4), P(x + 16, y + 3)], radius=3 * S, fill=SHOE)


def head(d, at):
    hx, hy = at
    d.ellipse([P(hx - 15, hy - 15), P(hx + 15, hy + 15)], fill=SKIN)
    d.pieslice([P(hx - 16, hy - 17), P(hx + 16, hy + 13)], 180, 360, fill=HAIR)
    d.ellipse([P(hx + 11, hy - 2), P(hx + 18, hy + 5)], fill=SKIN)  # nose: facing right


def figure(d, f, feet=True):
    """Far leg, body, near leg, arm, head; f holds the joints."""
    limb(d, f["hip"], f["back_knee"], 16, SHORTS)
    limb(d, f["back_knee"], f["back_ankle"], 13, SKIN)
    if feet:
        shoe(d, f["back_ankle"])
    limb(d, f["hip"], f["shoulder"], 26, SHIRT)
    limb(d, f["hip"], f["front_knee"], 17, SHORTS)
    limb(d, f["front_knee"], f["front_ankle"], 13, SKIN)
    if feet:
        shoe(d, f["front_ankle"])
    limb(d, f["shoulder"], f["elbow"], 11, SKIN)
    limb(d, f["elbow"], f["hand"], 10, SKIN)
    head(d, f["head"])


def done(img, path):
    img.resize((W, H), Image.LANCZOS).save(path, "WEBP", quality=86)


# ---------- Cable Step-Up: ankle strap on the trailing leg, low pulley behind ----------

def step_up(f):
    img, d = room()
    # cable tower with weight stack and low pulley
    d.rectangle([P(30, 30), P(40, 282)], fill=STEEL)
    d.rectangle([P(104, 30), P(114, 282)], fill=STEEL)
    d.rectangle([P(30, 26), P(114, 36)], fill=STEEL)
    for i in range(10):
        y = 150 + i * 12
        d.rectangle([P(48, y), P(96, y + 10)], fill=DARK)
    d.rectangle([P(70, 36), P(73, 150)], fill=(120, 124, 132))
    px, py = 114, 266
    d.ellipse([P(px - 9, py - 9), P(px + 9, py + 9)], fill=DARK)
    d.ellipse([P(px - 3, py - 3), P(px + 3, py + 3)], fill=STEEL)
    # box
    d.rectangle([P(200, 220), P(320, 282)], fill=WOOD)
    d.rectangle([P(200, 220), P(320, 226)], fill=WOOD_D)
    d.rectangle([P(200, 220), P(205, 282)], fill=WOOD_D)
    ax, ay = f["back_ankle"]
    # cable to the strap on the trailing ankle, drawn before the leg so the strap sits on top
    d.line([P(px, py), P(ax - 2, ay - 6)], fill=CABLE, width=int(2.2 * S))
    figure(d, f)
    # ankle strap
    d.rounded_rectangle([P(ax - 8, ay - 11), P(ax + 8, ay - 3)], radius=2 * S, fill=DARK)
    return img


STEP_START = dict(hip=(190, 178), shoulder=(197, 112), head=(201, 86), elbow=(204, 146), hand=(212, 176),
                  back_knee=(181, 229), back_ankle=(174, 275), front_knee=(236, 170), front_ankle=(234, 215))
STEP_END = dict(hip=(244, 118), shoulder=(248, 52), head=(251, 26), elbow=(240, 86), hand=(250, 112),
                back_knee=(288, 122), back_ankle=(282, 170), front_knee=(246, 166), front_ankle=(244, 215))


# ---------- Assisted Chest Dip: kneeling on the counterweighted pad, leaning forward ----------

def dip(f, pad_y):
    img, d = room()
    # machine frame, weight stack on the right
    d.rectangle([P(110, 24), P(122, 282)], fill=STEEL)
    d.rectangle([P(360, 24), P(372, 282)], fill=STEEL)
    d.rectangle([P(110, 20), P(440, 30)], fill=STEEL)
    d.rectangle([P(428, 24), P(440, 282)], fill=STEEL)
    for i in range(10):
        y = 150 + i * 12
        d.rectangle([P(380, y), P(420, y + 10)], fill=DARK)
    d.rectangle([P(398, 30), P(401, 150)], fill=(120, 124, 132))
    # dip handles
    d.rectangle([P(122, 146), P(268, 154)], fill=DARK)
    d.rounded_rectangle([P(240, 142), P(272, 158)], radius=4 * S, fill=PAD)
    # knee pad on its lever arm
    d.line([P(122, 205), P(160, pad_y + 4)], fill=STEEL, width=int(8 * S))
    d.rounded_rectangle([P(150, pad_y), P(240, pad_y + 12)], radius=5 * S, fill=PAD)
    figure(d, f, feet=False)
    return img


DIP_TOP = dict(hip=(214, 162), shoulder=(246, 96), head=(258, 72), elbow=(249, 124), hand=(252, 150),
               front_knee=(226, 214), front_ankle=(172, 214), back_knee=(220, 214), back_ankle=(166, 214))
DIP_BOTTOM = dict(hip=(200, 190), shoulder=(236, 128), head=(252, 106), elbow=(214, 158), hand=(252, 150),
                  front_knee=(212, 244), front_ankle=(158, 244), back_knee=(206, 244), back_ankle=(152, 244))


if __name__ == "__main__":
    out = sys.argv[1]
    for name, frames in {
        "Cable_Step-Up": [step_up(STEP_START), step_up(STEP_END)],
        "Assisted_Chest_Dip": [dip(DIP_TOP, 222), dip(DIP_BOTTOM, 252)],
    }.items():
        os.makedirs(os.path.join(out, name), exist_ok=True)
        for i, img in enumerate(frames):
            done(img, os.path.join(out, name, f"{i}.webp"))

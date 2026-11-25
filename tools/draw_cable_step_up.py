# Draws the Cable Step-Up pictures (assets/exercises/Cable_Step-Up/0.webp and 1.webp): python3 tools/draw_cable_step_up.py <out dir>
import sys
from PIL import Image, ImageDraw
S = 3  # supersample
W, H = 480, 320
out = sys.argv[1]

def P(x, y): return (x * S, y * S)

def limb(d, a, b, w, color):
    d.line([P(*a), P(*b)], fill=color, width=int(w * S))
    for p in (a, b):
        r = w * S / 2
        d.ellipse([p[0]*S - r, p[1]*S - r, p[0]*S + r, p[1]*S + r], fill=color)

SKIN = (232, 180, 138); SHIRT = (214, 69, 65); SHORTS = (43, 47, 58); SHOE = (30, 30, 34)
STEEL = (150, 156, 166); DARK = (70, 74, 84); WOOD = (196, 146, 92); WOOD_D = (160, 112, 64)

def scene(fig):
    img = Image.new("RGB", (W * S, H * S), (238, 236, 232))
    d = ImageDraw.Draw(img)
    # wall and floor
    d.rectangle([P(0, 0), P(W, 282)], fill=(226, 223, 218))
    d.rectangle([P(0, 282), P(W, H)], fill=(58, 60, 66))
    d.rectangle([P(0, 280), P(W, 284)], fill=(90, 92, 98))
    # cable tower: frame, weight stack, low pulley
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
    # plyo box
    d.rectangle([P(200, 220), P(320, 282)], fill=WOOD)
    d.rectangle([P(200, 220), P(320, 226)], fill=WOOD_D)
    d.rectangle([P(200, 220), P(205, 282)], fill=WOOD_D)
    hand = fig["hand"]
    # cable from the pulley to the handle
    d.line([P(px, py), P(hand[0], hand[1] + 8)], fill=(20, 20, 24), width=int(2.2 * S))
    # far leg, then body, near leg, arm, head
    hip, sh = fig["hip"], fig["shoulder"]
    bk, ba = fig["back_knee"], fig["back_ankle"]
    fk, fa = fig["front_knee"], fig["front_ankle"]
    limb(d, hip, bk, 16, SHORTS); limb(d, bk, ba, 13, SKIN)
    d.rounded_rectangle([P(ba[0] - 6, ba[1] - 4), P(ba[0] + 16, ba[1] + 3)], radius=3 * S, fill=SHOE)
    limb(d, hip, sh, 26, SHIRT)
    limb(d, hip, fk, 17, SHORTS); limb(d, fk, fa, 13, SKIN)
    d.rounded_rectangle([P(fa[0] - 6, fa[1] - 4), P(fa[0] + 16, fa[1] + 3)], radius=3 * S, fill=SHOE)
    limb(d, sh, fig["elbow"], 11, SHIRT if False else SKIN); limb(d, fig["elbow"], hand, 10, SKIN)
    # D-handle in the hand
    d.rounded_rectangle([P(hand[0] - 7, hand[1] + 2), P(hand[0] + 7, hand[1] + 9)], radius=2 * S, outline=DARK, width=int(2.5 * S))
    hx, hy = fig["head"]
    d.ellipse([P(hx - 15, hy - 15), P(hx + 15, hy + 15)], fill=SKIN)
    d.pieslice([P(hx - 16, hy - 17), P(hx + 16, hy + 13)], 180, 360, fill=(84, 58, 40))
    d.ellipse([P(hx + 11, hy - 2), P(hx + 18, hy + 5)], fill=SKIN)  # nose: facing right
    return img.resize((W, H), Image.LANCZOS)

start = dict(hip=(190, 178), shoulder=(197, 112), head=(201, 86), elbow=(193, 146), hand=(190, 180),
             back_knee=(181, 229), back_ankle=(174, 275), front_knee=(236, 170), front_ankle=(234, 215))
end = dict(hip=(244, 118), shoulder=(248, 52), head=(251, 26), elbow=(245, 86), hand=(242, 118),
           back_knee=(288, 122), back_ankle=(282, 170), front_knee=(246, 166), front_ankle=(244, 215))
scene(start).save(out + "/0.webp", "WEBP", quality=86)
scene(end).save(out + "/1.webp", "WEBP", quality=86)

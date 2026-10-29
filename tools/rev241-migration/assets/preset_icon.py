"""Bank Presets button (sprites 24500 normal / 24501 hover): one crisp steel platebody with a gold collar on Jagex's
36x36 stone button frame (sprite 170), 1px dark outline, soft drop shadow, centred. The hover frame lifts the stone.
Usage: python preset_icon.py <frame170_4x.png> <outdir> [previewdir]"""
import sys
from PIL import Image

frame4 = Image.open(sys.argv[1]).convert('RGBA')
out = sys.argv[2]
frame = frame4.resize((36, 36), Image.NEAREST)

# the platebody, 22 x 21, '#' body; shading is computed per pixel below
SHAPE = [
    "......##......##......",
    "..######......######..",
    ".########....########.",
    "##########..##########",
    "######################",
    "######################",
    "######################",
    "#################.####",
    "####.############.####",
    "####.############.####",
    "####..##########..####",
    "####..##########..####",
    ".###..##########..###.",
    ".###..##########..###.",
    "......##########......",
    "......##########......",
    "......##########......",
    "......##########......",
    "......##########......",
    ".......########.......",
    "......................",
]
# carve the armpits properly: columns 4/17 gaps are the arm/torso split
W, H = len(SHAPE[0]), len(SHAPE)
OX, OY = (36 - W) // 2, (36 - H) // 2 + 1
cells = set()
for y, row in enumerate(SHAPE):
    for x, c in enumerate(row):
        if c == '#':
            cells.add((x, y))
# symmetric: mirror the left half onto the right so the art is exactly centred
cells = {(x, y) for (x, y) in cells if x < W // 2} | {(W - 1 - x, y) for (x, y) in cells if x < W // 2}

LIGHT, MID, DARK, DEEP = (206, 211, 216), (158, 165, 173), (108, 115, 124), (72, 78, 86)
GOLD, GOLD_D = (236, 190, 74), (168, 122, 36)
OUTLINE = (16, 16, 18)


def colour(x, y):
    cx = W / 2 - 0.5
    if y <= 1 and (x, y) in cells and 6 <= x <= 15:
        return GOLD  # collar
    if y == 2 and 7 <= x <= 14:
        return GOLD_D
    torso = 6 <= x <= 15
    if torso and y in (9, 13, 17) and 7 <= x <= 14:
        return DEEP  # the overlapping plate bands
    if torso and y == 19:
        return GOLD_D  # gold hem
    if abs(x - cx) < 0.6:
        return DARK  # centre seam
    if not torso and y >= 6:
        return MID if x < cx else DARK  # sleeves sit a shade behind the chest
    edge = not all((x + dx, y + dy) in cells for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
    left = x < cx
    if y <= 4 and not edge:
        return LIGHT if left else MID  # shoulder plates catch the light
    if edge:
        return MID if left else DEEP
    return MID if left else DARK


def draw(img, lift):
    px = img.load()
    if lift:
        for y in range(4, 32):
            for x in range(4, 32):
                r, g, b, a = px[x, y]
                px[x, y] = (min(255, r + 14), min(255, g + 14), min(255, b + 12), a)
    # drop shadow (1 px down-right), then outline, then the body
    for (x, y) in cells:
        sx, sy = OX + x + 1, OY + y + 1
        r, g, b, a = px[sx, sy]
        px[sx, sy] = (r * 6 // 10, g * 6 // 10, b * 6 // 10, a)
    for (x, y) in cells:
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            n = (x + dx, y + dy)
            if n not in cells:
                px[OX + n[0], OY + n[1]] = OUTLINE + (255,)
    for (x, y) in cells:
        c = colour(x, y)
        if lift:
            c = tuple(min(255, v + 18) for v in c)
        px[OX + x, OY + y] = c + (255,)
    return img


for sid, lift in ((24500, False), (24501, True)):
    img = draw(frame.copy(), lift)
    img.save('%s/%d.png' % (out, sid))
    if len(sys.argv) > 3:
        img.resize((144, 144), Image.NEAREST).save('%s/new_%d.png' % (sys.argv[3], sid))
print('ok')

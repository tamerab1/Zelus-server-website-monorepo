"""Karn Upgrader (1158), crucible design, round 7 (user 2026-11-14: round 6 "too small, not clean, looks broken").
Round 6 shrank the whole "upgrader idea" picture (crucible_ref.png, 1270 x 949) into the window, so every part came out
tiny and the text landed on the art. Now the window is laid out for the game: a clean parchment panel in a stone and
gold frame fills it, and the picture's parts are cut out at a uniform scale (circles stay round) and placed where the
components are (RestyleForge uses the same game coordinates):
  title plaque (re-lettered KARN UPGRADER) top centre; tabs, search and a 3-wide icon catalog on the left;
  the fire ring (base item) in the centre, four reagent sockets under it joined by glowing lines; the outcome frame,
  success bar and stats card on the right; the Forge button bottom centre.
Drawn at 2x (1024 x 668) and scaled down. 25100 background; 25118/25119/25120 catalog slot / selected / hover."""
import sys, os, math, random
from PIL import Image, ImageDraw, ImageFilter, ImageEnhance, ImageChops
sys.path.insert(0, os.path.dirname(__file__))
from refkit import Ref, save, brighter, gold_title, fill_avg

here = os.path.dirname(os.path.abspath(__file__))
png = os.path.join(here, "..", "png")
src = os.path.join(here, "crucible_ref.png")
W, H = 1024, 668          # 2x of 512 x 334
random.seed(7)
import PIL.Image as _I


def z(v):                 # game px -> canvas px
    return int(round(v * 2))


r = Ref(src)
fill_avg(r, (352, 44, 928, 96), (336, 56, 346, 84))
gold_title(r, (350, 38, 930, 102), "KARN UPGRADER", 50)
ref = r.im.convert("RGB")


def cut(box, width):
    """a part of the picture, scaled uniformly to the given canvas width"""
    part = ref.crop(box)
    k = width / part.width
    return part.resize((width, max(1, round(part.height * k))), Image.LANCZOS)


def round_mask(size, inset=0):
    m = Image.new("L", size, 0)
    ImageDraw.Draw(m).ellipse((inset, inset, size[0] - 1 - inset, size[1] - 1 - inset), fill=255)
    return m.filter(ImageFilter.GaussianBlur(1.5))


def paste_round(canvas, img, centre, inset=2):
    x, y = centre[0] - img.width // 2, centre[1] - img.height // 2
    canvas.paste(img, (x, y), round_mask(img.size, inset))


def paste_shape(canvas, img, xy, grow=3):
    """paste only the ornament: gold / red (saturated, not dark) pixels, holes closed, edges softened"""
    hsv = img.convert("HSV")
    sat, val = hsv.getchannel(1), hsv.getchannel(2)
    m = ImageChops.multiply(sat.point(lambda v: 255 if v > 80 else 0), val.point(lambda v: 255 if v > 45 else 0))
    m = m.filter(ImageFilter.MaxFilter(2 * grow + 1)).filter(ImageFilter.MinFilter(2 * grow + 1))
    m = m.filter(ImageFilter.MaxFilter(3)).filter(ImageFilter.GaussianBlur(1.2))
    canvas.paste(img, xy, m)


def paste_soft(canvas, img, xy, feather=6):
    m = Image.new("L", img.size, 0)
    ImageDraw.Draw(m).rectangle((feather, feather, img.width - 1 - feather, img.height - 1 - feather), fill=255)
    canvas.paste(img, xy, m.filter(ImageFilter.GaussianBlur(feather / 2)))


# ---- backdrop: the picture's forge, blurred and dimmed
# the surround: the picture's stone blocks (from its base), tiled, with the forge's ember glow in the corners
tile = ref.crop((40, 842, 250, 942))
back = Image.new("RGB", (W, H))
for ty in range(0, H, tile.height):
    for tx in range(0, W, tile.width):
        back.paste(tile if (tx // tile.width + ty // tile.height) % 2 == 0 else tile.transpose(Image.FLIP_LEFT_RIGHT), (tx, ty))
back = ImageEnhance.Brightness(back).enhance(1.05)
ember = Image.new("L", (W, H), 0)
ed = ImageDraw.Draw(ember)
for ex, ey in ((0, 0), (W, 0), (0, H), (W, H)):
    ed.ellipse((ex - z(70), ey - z(50), ex + z(70), ey + z(50)), fill=150)
ember = ember.filter(ImageFilter.GaussianBlur(z(18)))
back = Image.composite(ImageChops.screen(back, Image.new("RGB", (W, H), (190, 80, 20))), back, ember)
ImageDraw.Draw(back).rectangle((0, 0, W - 1, H - 1), outline=(212, 165, 60), width=3)
ImageDraw.Draw(back).rectangle((3, 3, W - 4, H - 4), outline=(30, 20, 16), width=2)
cv = back.copy()

# ---- the panel: stone frame, gold + crimson lines, parchment inside
P = (z(6), z(24), z(506), z(330))
d = ImageDraw.Draw(cv)
d.rounded_rectangle(P, z(10), outline=(20, 14, 12), width=3)
inner = (P[0] + z(6), P[1] + z(6), P[2] - z(6), P[3] - z(6))
d.rounded_rectangle(inner, z(7), outline=(212, 165, 60), width=z(1.5))

# parchment: the picture's own parchment (a clean patch beside the rune socket, enlarged so it reads as a smooth,
# warm sheet), brightened a touch, with a soft darker rim
pw, ph = inner[2] - inner[0] - z(4), inner[3] - inner[1] - z(4)
patch = ref.crop((1000, 650, 1080, 712)).filter(ImageFilter.GaussianBlur(1))
parch = Image.blend(Image.new("RGB", (pw, ph), (242, 231, 204)), patch.resize((pw, ph), Image.BICUBIC), 0.1)
grain = Image.effect_noise((pw, ph), 6).convert("RGB")
parch = ImageChops.multiply(parch, ImageEnhance.Brightness(grain).enhance(2.05))
vig = Image.new("L", (pw, ph), 0)
ImageDraw.Draw(vig).rounded_rectangle((z(8), z(8), pw - z(8), ph - z(8)), z(12), fill=255)
vig = vig.filter(ImageFilter.GaussianBlur(z(9)))
edge = ImageEnhance.Brightness(parch).enhance(0.88)
parch = Image.composite(parch, edge, vig)
pm = Image.new("L", (pw, ph), 0)
ImageDraw.Draw(pm).rounded_rectangle((0, 0, pw - 1, ph - 1), z(6), fill=255)
cv.paste(parch, (inner[0] + z(2), inner[1] + z(2)), pm)
d = ImageDraw.Draw(cv)
d.rounded_rectangle((inner[0] + z(2), inner[1] + z(2), inner[2] - z(2), inner[3] - z(2)), z(6), outline=(140, 30, 34), width=2)

# side gems from the pillars
gem = cut((1122, 372, 1192, 462), z(16))
for gx_ in (P[0] - z(2), P[2] - z(14)):
    paste_soft(cv, gem, (gx_, z(170) - gem.height // 2), 3)

# ---- the rune circle behind the crucible (faint ink, like the picture)
from PIL import ImageFont
rc = Image.new("RGBA", (W, H), (0, 0, 0, 0))
rd = ImageDraw.Draw(rc)
cx_, cy_ = z(262), z(92)
for rad, wdt in ((z(66), 3), (z(56), 2)):
    rd.ellipse((cx_ - rad, cy_ - rad, cx_ + rad, cy_ + rad), outline=(120, 84, 50, 90), width=wdt)
rune_font = ImageFont.truetype("C:/Windows/Fonts/seguihis.ttf", z(7))
runes = "\u16a0\u16a2\u16a6\u16a8\u16b1\u16b2\u16b7\u16b9\u16ba\u16be\u16c1\u16c3\u16c7\u16c8\u16c9\u16ca\u16cf\u16d2\u16d6\u16d7\u16da\u16dc\u16de\u16df"
for i in range(36):
    ang = 2 * math.pi * i / 36
    gl = Image.new("RGBA", (z(10), z(10)), (0, 0, 0, 0))
    ImageDraw.Draw(gl).text((z(5), z(5)), runes[i % len(runes)], font=rune_font, fill=(110, 76, 44, 120), anchor="mm")
    gl = gl.rotate(-math.degrees(ang), resample=Image.BICUBIC)
    rx, ry = cx_ + math.sin(ang) * z(61), cy_ - math.cos(ang) * z(61)
    rc.alpha_composite(gl, (int(rx - gl.width / 2), int(ry - gl.height / 2)))
cv = Image.alpha_composite(cv.convert("RGBA"), rc).convert("RGB")

# ---- glowing lines: sockets -> ring, and the arrow ring -> outcome (drawn first, under the parts)
RING = (z(262), z(92))
SOCK = [(z(180 + 56 * i), z(198)) for i in range(4)]
OUT = (z(440), z(66))


def curve(a, b, bend):
    mx, my = (a[0] + b[0]) / 2, (a[1] + b[1]) / 2 + bend
    return [((1 - t) ** 2 * a[0] + 2 * (1 - t) * t * mx + t * t * b[0], (1 - t) ** 2 * a[1] + 2 * (1 - t) * t * my + t * t * b[1])
            for t in [i / 48 for i in range(49)]]


lines = [curve(p, RING, z(8)) for p in SOCK] + [[SOCK[i] for i in range(4)]]
arc = curve((RING[0] + z(46), RING[1] - z(8)), (OUT[0] - z(30), OUT[1]), -z(30))


def stroke(paths, colour, width, blur, alpha):
    layer = Image.new("RGBA", (W, H), colour + (0,))
    a = Image.new("L", (W, H), 0)
    ad = ImageDraw.Draw(a)
    for pts in paths:
        ad.line(pts, fill=alpha, width=width, joint="curve")
    if blur:
        a = a.filter(ImageFilter.GaussianBlur(blur))
    layer.putalpha(a)
    return layer


cv = cv.convert("RGBA")
cv = Image.alpha_composite(cv, stroke(lines, (255, 90, 40), z(5), z(2.5), 200))
cv = Image.alpha_composite(cv, stroke(lines, (170, 24, 28), z(1.6), 0, 255))
cv = Image.alpha_composite(cv, stroke([arc], (255, 150, 30), z(6), z(3), 220))
cv = Image.alpha_composite(cv, stroke([arc], (255, 214, 110), z(2), 0, 255))
tip = arc[-1]
ImageDraw.Draw(cv).polygon([(tip[0] + z(7), tip[1]), (tip[0] - z(3), tip[1] - z(7)), (tip[0] - z(3), tip[1] + z(7))], fill=(255, 200, 80, 255))
cv = cv.convert("RGB")

# ---- the parts
def well(img, box, round_=True):
    """a lighter well: warm bronze, brightest in the middle, where the item sits"""
    x0, y0, x1, y1 = [int(v) for v in box]
    w_, h_ = x1 - x0, y1 - y0
    g = Image.new("RGB", (w_, h_), (58, 40, 30))
    m = Image.new("L", (w_, h_), 0)
    ImageDraw.Draw(m).ellipse((-w_ * 0.2, -h_ * 0.2, w_ * 1.2, h_ * 1.2), fill=255)
    g = Image.composite(Image.new("RGB", (w_, h_), (92, 66, 48)), g, m.filter(ImageFilter.GaussianBlur(min(w_, h_) / 4)))
    shape = Image.new("L", (w_, h_), 0)
    sd = ImageDraw.Draw(shape)
    (sd.ellipse if round_ else sd.rectangle)((0, 0, w_ - 1, h_ - 1), fill=255)
    img.paste(g, (x0, y0), shape)


ring = cut((498, 140, 792, 452), z(104))                          # the fire ring (spiked frame)
c = ring.copy()
well(c, (c.width * 0.29, c.height * 0.28, c.width * 0.71, c.height * 0.69))
paste_round(cv, c, RING, 1)
sock = cut((322, 409, 434, 521), z(52))                            # a reagent socket ring
s = sock.copy()
well(s, (s.width * 0.2, s.height * 0.2, s.width * 0.8, s.height * 0.8))
for p in SOCK:
    paste_round(cv, s, p, 1)
out = cut((940, 248, 1042, 352), z(50))                            # the outcome frame
o = out.copy()
well(o, (o.width * 0.12, o.height * 0.12, o.width * 0.88, o.height * 0.88), False)
paste_soft(cv, o, (OUT[0] - o.width // 2, OUT[1] - o.height // 2), 2)
card = cut((262, 670, 484, 822), z(112))                           # the stats card
cc = card.copy()
fill = cut((300, 712, 440, 790), cc.width - z(14)).resize((cc.width - z(14), cc.height - z(14)))
cc.paste(Image.new("RGB", fill.size, (241, 230, 204)), (z(7), z(7)))
paste_soft(cv, cc, (z(381), z(168)), 2)
btn = cut((468, 812, 832, 930), z(150))                            # the Forge button, text removed
b = btn.copy()
bd = ImageDraw.Draw(b)
bd.rectangle((b.width * 0.24, b.height * 0.26, b.width * 0.76, b.height * 0.80), fill=(120, 18, 22))
for x0, x1 in ((0, 0.17), (0.83, 1)):                                # the fire arc behind the top corners: dropped
    bd.rectangle((b.width * x0, 0, b.width * x1, b.height * 0.2), fill=(0, 0, 0))
b = b.filter(ImageFilter.SMOOTH_MORE) if False else b
paste_shape(cv, b, (z(262) - b.width // 2, z(328) - b.height + z(4)))
plaque = cut((282, 12, 1000, 124), z(262))                         # the title plaque
paste_shape(cv, plaque, ((W - plaque.width) // 2, 0))

# ---- left column: tabs, search, the catalog well
def tab_mask(w, h=17):
    m = Image.new("L", (z(w), z(h)), 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, z(w) - 1, z(h) - 1), z(4), fill=255)
    return m


def tab_art(w, active, h=17):
    """a crafted tab: vertical gradient body, gold bevel (light top, dark bottom), an inner hairline, tiny end studs"""
    W2, H2 = z(w), z(h)
    top, bot = ((246, 214, 132), (196, 142, 52)) if active else ((156, 38, 44), (92, 16, 22))
    im = Image.new("RGB", (W2, H2))
    for y in range(H2):
        t = y / (H2 - 1)
        im.paste(tuple(int(top[k] + (bot[k] - top[k]) * t) for k in range(3)), (0, y, W2, y + 1))
    dd = ImageDraw.Draw(im)
    dd.rounded_rectangle((0, 0, W2 - 1, H2 - 1), z(4), outline=(120, 82, 28), width=2)
    dd.line((z(3), 3, W2 - z(3), 3), fill=(255, 236, 170) if active else (226, 178, 84), width=2)
    dd.line((z(3), H2 - 4, W2 - z(3), H2 - 4), fill=(140, 96, 30) if active else (60, 10, 14), width=2)
    dd.rounded_rectangle((4, 4, W2 - 5, H2 - 5), z(3), outline=(255, 230, 150) if active else (190, 140, 60), width=1)
    return im


d = ImageDraw.Draw(cv)
for x0, w in ((17, 48), (67, 39), (108, 39)):                     # tabs (Weapons is the widest label)
    cv.paste(tab_art(w, False), (z(x0), z(44)), tab_mask(w))
d = ImageDraw.Draw(cv)
d.rounded_rectangle((z(18), z(65), z(145), z(81)), z(4), fill=(46, 30, 22), outline=(170, 130, 60), width=2)
d.ellipse((z(22), z(68), z(31), z(77)), outline=(212, 165, 60), width=3)
d.line((z(30), z(76), z(33), z(79)), fill=(212, 165, 60), width=3)
d.rounded_rectangle((z(16), z(86), z(147), z(321)), z(5), fill=(64, 44, 32), outline=(212, 165, 60), width=3)
d.rounded_rectangle((z(18), z(88), z(145), z(319)), z(4), outline=(110, 24, 30), width=2)
# the success bar track (20 segments of 5 px at x 388.., y 140)
d.rounded_rectangle((z(386), z(138), z(490), z(149)), z(3), fill=(40, 26, 20), outline=(212, 165, 60), width=2)
# the base name plate under the ring
d.rounded_rectangle((z(196), z(146), z(328), z(170)), z(5), fill=(244, 234, 210), outline=(190, 150, 80), width=2)

final = cv.resize((512, 334), Image.LANCZOS)
save(final, png, 25100)

# ---- catalog slot sprites (36 x 34): a dark slot with a gold rim; selected = crimson + bright gold; hover = lifted
def slot(rim, fillc, glow=False):
    im = Image.new("RGB", (72, 68), (64, 44, 32))
    dd = ImageDraw.Draw(im)
    dd.rounded_rectangle((1, 1, 70, 66), 8, fill=fillc, outline=rim, width=4)
    well(im, (8, 8, 64, 60))
    dd.rounded_rectangle((7, 7, 64, 60), 5, outline=(rim[0] // 2, rim[1] // 2, rim[2] // 2), width=2)
    if glow:
        im = ImageChops.screen(im, Image.new("RGB", im.size, (40, 10, 6)))
    return im.resize((36, 34), Image.LANCZOS)


save(slot((170, 128, 56), (66, 42, 30)), png, 25118)
save(slot((255, 214, 110), (150, 34, 40), True), png, 25119)
save(slot((236, 190, 90), (80, 54, 38)), png, 25120)
for w, sid in ((48, 25121), (39, 25122)):                          # the active tab
    save(tab_art(w, True).resize((w, 17), Image.LANCZOS), png, sid)
print("crucible round 7 drawn")

"""Karn Battle Pass v2 (user 2026-11-16, ref/battlepass2_ref.png "KARN Battle Pass Rewards Screen (1)"): the mock-up's
own purple / gold look, rebuilt at game scale instead of shrinking the whole picture (the round-6 lesson):

  * the art pieces (logo + banners, hooded figure, castle, tier badge, FREE / ELITE shields, icons) are cut from the
    mock-up and placed at their game size; frames, bars, cells and buttons are drawn (3x, then scaled down) so they
    stay crisp;
  * every number / word that changes is a game text component (BuildKarnHub.battlePass2), never baked lettering;
  * the Missions view is a separate panel sprite laid over the reward grid.

Window 512 x 334 (the main modal). Writes png/<sprite>.png (<= 254 colours, on/off alpha) for cache stage 42.
Coordinates are GAME pixels; S = 3 (drawing scale). Usage: python build_bp2.py [preview]"""
import os, sys, random
import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageEnhance

HERE = os.path.dirname(os.path.abspath(__file__))
PNG = os.path.join(HERE, "png")
REF = Image.open(os.path.join(HERE, "ref", "battlepass2_ref.png")).convert("RGB")
S = 3
W, H = 512, 334
random.seed(7)

# palette sampled from the mock-up
BG_TOP, BG_BOT = (20, 12, 30), (9, 6, 14)
PURPLE, PURPLE_LT, PURPLE_DK = (150, 60, 220), (205, 140, 255), (58, 22, 92)
GOLD, GOLD_LT, GOLD_DK, GOLD_XDK = (222, 172, 70), (255, 226, 140), (150, 102, 32), (92, 60, 18)
SLATE_T, SLATE_B = (46, 66, 92), (28, 40, 58)
BRONZE_T, BRONZE_B = (94, 64, 26), (48, 30, 12)
INK = (14, 10, 18)

# sprite ids (BuildKarnHub.battlePass2 + the server use the same numbers)
BG2 = 25270
TAB, TAB_ON, TAB_H = 25271, 25272, 25273          # 70 x 20
RANK, RANK_H = 25274, 25275                       # 92 x 20
UPGRADE, UPGRADE_H = 25276, 25277                 # 132 x 24
BADGE = 25278                                     # 46 x 40
XP_SEG = 25279                                    # 13 x 6
CELL, CELL_ELITE, CELL_GLOW = 25280, 25281, 25282  # 44 x 42
TICK, LOCK = 25283, 25284                         # 12 x 12, 10 x 12
HDR_ON, HDR_OFF, HDR_MILE = 25285, 25286, 25287   # 56 x 15
ARROW_L, ARROW_L_H, ARROW_R, ARROW_R_H = 25288, 25289, 25290, 25291  # 16 x 16
CLOSE, CLOSE_H = 25292, 25293                     # 20 x 20
MISSIONS_BG = 25294                               # 340 x 192
SUB, SUB_ON, SUB_H = 25295, 25296, 25297          # 72 x 16
CLAIM, CLAIM_H = 25298, 25299                     # 98 x 22
ELITE_LOCK = 25300                                # 12 x 14 (over the ELITE shield while Elite is locked)

# layout (game px) shared with BuildKarnHub.battlePass2
GRID_X, GRID_Y, GRID_W, GRID_H = 164, 96, 342, 192   # reward grid / missions panel
LABEL_W = 48
COL_X0, COL_W, COL_GAP = 214, 56, 2                  # 5 tier columns
HDR_Y, HDR_H = 98, 15
FREE_Y, ELITE_Y, ROW_H = 115, 190, 74
CARD = (6, 92, 158, 304)


def s(v):
    return int(round(v * S))


def sb(b):
    return tuple(s(v) for v in b)


def crop(box, size):
    return REF.crop(box).resize(size, Image.LANCZOS)


def feather(img, radius):
    """alpha that fades the edges of a rectangular cut-out"""
    m = Image.new("L", img.size, 0)
    ImageDraw.Draw(m).rectangle((radius, radius, img.width - radius - 1, img.height - radius - 1), fill=255)
    m = m.filter(ImageFilter.GaussianBlur(radius / 2))
    out = img.convert("RGBA")
    out.putalpha(m)
    return out


def vgrad(size, top, bot):
    w, h = size
    a = np.linspace(0, 1, h)[:, None, None]
    arr = (np.array(top)[None, None, :] * (1 - a) + np.array(bot)[None, None, :] * a)
    arr = np.repeat(arr, w, axis=1)
    return Image.fromarray(arr.astype(np.uint8), "RGB")


def noise(img, amount=6, seed=1):
    rng = np.random.default_rng(seed)
    a = np.array(img).astype(np.int16)
    n = rng.integers(-amount, amount + 1, a.shape[:2])[..., None]
    a[..., :3] = np.clip(a[..., :3] + n, 0, 255)
    return Image.fromarray(a.astype(np.uint8), img.mode)


def glow(canvas, box, colour, radius, strength=1.0):
    """additive soft glow around a box (canvas px)"""
    layer = Image.new("RGB", canvas.size, (0, 0, 0))
    ImageDraw.Draw(layer).rounded_rectangle(box, s(6), outline=colour, width=s(3))
    layer = layer.filter(ImageFilter.GaussianBlur(radius))
    arr = np.clip(np.array(canvas).astype(np.int16) + (np.array(layer).astype(np.int16) * strength).astype(np.int16), 0, 255)
    canvas.paste(Image.fromarray(arr.astype(np.uint8), "RGB"))


def gold_frame(d, box, r=6, inner=True):
    x0, y0, x1, y1 = box
    d.rounded_rectangle(box, s(r), outline=GOLD_XDK, width=s(2))
    d.rounded_rectangle((x0 + s(1), y0 + s(1), x1 - s(1), y1 - s(1)), s(r), outline=GOLD, width=max(1, s(1)))
    if inner:
        d.rounded_rectangle((x0 + s(2.3), y0 + s(2.3), x1 - s(2.3), y1 - s(2.3)), s(r - 1), outline=GOLD_DK, width=1)


def save(img, sid, size=None):
    if size and img.size != size:
        img = img.resize(size, Image.LANCZOS)
    img = img.convert("RGBA")
    mask = img.getchannel("A").point(lambda v: 255 if v >= 110 else 0)
    rgb = Image.new("RGB", img.size, (0, 0, 0))
    rgb.paste(img.convert("RGB"), mask=mask)
    q = rgb.quantize(colors=254, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.FLOYDSTEINBERG).convert("RGB")
    out = Image.new("RGBA", img.size, (0, 0, 0, 0))
    out.paste(q, mask=mask)
    a = np.array(out)
    black = (a[..., 3] > 0) & (a[..., 0] == 0) & (a[..., 1] == 0) & (a[..., 2] == 0)
    a[black, 0:3] = 1  # pure black is transparent in sprites
    Image.fromarray(a, "RGBA").save(os.path.join(PNG, f"{sid}.png"))
    return Image.fromarray(a, "RGBA")


# ================================================================= small sprites (drawn at 3x)
def plate(w, h, top, bot, edge, edge2, r=4, shine=True):
    img = Image.new("RGBA", (s(w), s(h)), (0, 0, 0, 0))
    body = vgrad((s(w), s(h)), top, bot).convert("RGBA")
    m = Image.new("L", body.size, 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, s(w) - 1, s(h) - 1), s(r), fill=255)
    img.paste(body, (0, 0), m)
    d = ImageDraw.Draw(img)
    d.rounded_rectangle((0, 0, s(w) - 1, s(h) - 1), s(r), outline=edge, width=s(1))
    d.rounded_rectangle((s(1), s(1), s(w) - s(1) - 1, s(h) - s(1) - 1), s(r - 1), outline=edge2, width=1)
    if shine:
        d.line((s(r), s(1.5), s(w) - s(r), s(1.5)), fill=tuple(min(255, c + 60) for c in edge2), width=1)
    return img


def gold_button(w, h, hover=False):
    top = (246, 206, 108) if hover else (232, 182, 76)
    bot = (164, 108, 30) if hover else (138, 90, 24)
    img = plate(w, h, top, bot, GOLD_XDK, GOLD_LT if hover else (255, 216, 120), r=5)
    d = ImageDraw.Draw(img)
    d.rounded_rectangle((s(2.5), s(2.5), s(w) - s(2.5) - 1, s(h) - s(2.5) - 1), s(4), outline=(120, 78, 20), width=1)
    return img


def dark_button(w, h, on=False, hover=False):
    if on:
        return plate(w, h, (92, 62, 24), (52, 34, 14), GOLD, GOLD_LT, r=4)
    top = (54, 30, 70) if hover else (36, 22, 48)
    return plate(w, h, top, (16, 10, 24), GOLD_DK if not hover else GOLD, (120, 84, 40) if not hover else GOLD_LT, r=4)


def icon_on(img, icon, x, y):
    img.alpha_composite(icon.convert("RGBA"), (x, y))
    return img


def cell(kind):
    w, h = 44, 42
    img = Image.new("RGBA", (s(w), s(h)), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    if kind == "glow":
        for i, a in enumerate((60, 110, 200)):
            d.rounded_rectangle((s(i), s(i), s(w) - s(i) - 1, s(h) - s(i) - 1), s(5), outline=(255, 214, 110, a), width=s(1))
        return img
    fill_t, fill_b = ((26, 22, 34), (12, 10, 18)) if kind == "free" else ((40, 16, 62), (16, 6, 28))
    body = vgrad((s(w), s(h)), fill_t, fill_b).convert("RGBA")
    m = Image.new("L", body.size, 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, s(w) - 1, s(h) - 1), s(5), fill=255)
    img.paste(body, (0, 0), m)
    if kind == "free":
        d.rounded_rectangle((0, 0, s(w) - 1, s(h) - 1), s(5), outline=(96, 104, 118), width=s(1))
        d.rounded_rectangle((s(1), s(1), s(w) - s(1) - 1, s(h) - s(1) - 1), s(4), outline=(150, 156, 170), width=1)
    else:
        d.rounded_rectangle((0, 0, s(w) - 1, s(h) - 1), s(5), outline=(132, 52, 210), width=s(1))
        d.rounded_rectangle((s(1), s(1), s(w) - s(1) - 1, s(h) - s(1) - 1), s(4), outline=(212, 150, 255), width=1)
    return img


def header(kind):
    w, h = 56, 15
    if kind == "on":
        return plate(w, h, (244, 196, 90), (170, 112, 30), GOLD_XDK, GOLD_LT, r=2)
    if kind == "mile":
        return plate(w, h, (255, 224, 130), (196, 136, 40), GOLD_XDK, (255, 240, 190), r=2)
    return plate(w, h, (70, 70, 78), (36, 36, 44), (20, 20, 26), (110, 110, 122), r=2)


def arrow(left, hover):
    img = dark_button(16, 16, hover=hover)
    d = ImageDraw.Draw(img)
    c = GOLD_LT if hover else GOLD
    if left:
        d.polygon([(s(10.5), s(4)), (s(5), s(8)), (s(10.5), s(12))], fill=c)
    else:
        d.polygon([(s(5.5), s(4)), (s(11), s(8)), (s(5.5), s(12))], fill=c)
    return img


def badge():
    b = crop((572, 184, 724, 308), (s(46), s(40))).convert("RGBA")
    # the baked "2": cover the number with the shield's own navy so the tier is live text
    d = ImageDraw.Draw(b)
    navy = REF.getpixel((620, 290))
    d.rounded_rectangle((s(12.5), s(16.5), s(33.5), s(35)), s(3), fill=navy + (255,))
    # transparent outside the shield (the mock-up's dark backdrop)
    a = np.array(b)
    lum = a[..., :3].astype(np.int16).sum(axis=2)
    a[..., 3] = np.where(lum < 75, 0, 255)
    return Image.fromarray(a, "RGBA")


def elite_shield():
    e = crop((538, 568, 672, 728), (s(44), s(52))).convert("RGBA")
    # the baked padlock (lower right) goes: Elite unlocked shows the bare shield, the lock is its own sprite
    left = e.crop((0, s(34), s(22), s(52))).transpose(Image.FLIP_LEFT_RIGHT)
    e.paste(left, (s(22), s(34)))
    a = np.array(e)
    lum = a[..., :3].astype(np.int16).sum(axis=2)
    a[..., 3] = np.where(lum < 70, 0, 255)
    return Image.fromarray(a, "RGBA")


def keyed(box, size, cutoff=70):
    c = crop(box, size).convert("RGBA")
    a = np.array(c)
    lum = a[..., :3].astype(np.int16).sum(axis=2)
    a[..., 3] = np.where(lum < cutoff, 0, 255)
    return Image.fromarray(a, "RGBA")


# ================================================================= the background (3x canvas)
def background():
    c = vgrad((s(W), s(H)), BG_TOP, BG_BOT)
    c = noise(c, 4, 3)
    # purple ambience: left torch-glow and the top-right storm
    glow(c, sb((-40, 120, 40, 260)), PURPLE_DK, s(30), 1.2)
    glow(c, sb((300, -30, 520, 60)), PURPLE_DK, s(36), 1.0)
    d = ImageDraw.Draw(c)

    # castle band (top right), feathered into the backdrop
    castle = feather(crop((930, 62, 1360, 150), (s(176), s(36))), s(5))
    c.paste(castle, (s(326), s(26)), castle)

    # logo + banners
    logo = crop((5, 0, 600, 232), (s(164), s(64))).convert("RGBA")
    lm = Image.new("L", logo.size, 255).filter(ImageFilter.GaussianBlur(0))
    lmd = ImageDraw.Draw(lm)
    lm = feather(logo, s(3))
    c.paste(lm, (s(4), s(3)), lm)

    # season laurels + calendar (the text itself is live)
    for box, x in (((626, 92, 664, 150), 210), ((892, 92, 932, 150), 280)):
        l = keyed(box, (s(10), s(16)))
        c.paste(l, (s(x), s(24)), l)
    cal = keyed((674, 143, 705, 170), (s(9), s(8)))
    c.paste(cal, (s(208), s(47)), cal)

    # XP bar track + milestone diamonds
    tx0, ty0, tx1, ty1 = 228, 75, 500, 85
    d.rounded_rectangle(sb((tx0, ty0, tx1, ty1)), s(4), fill=(14, 10, 20), outline=GOLD_DK, width=s(1))
    d.rounded_rectangle(sb((tx0 + 1, ty0 + 1, tx1 - 1, ty1 - 1)), s(3), outline=(60, 44, 24), width=1)
    for i in range(6):
        cx, cy = s(234 + 52.6 * i), s(90)
        r = s(2.6)
        d.polygon([(cx, cy - r), (cx + r, cy), (cx, cy + r), (cx - r, cy)], fill=GOLD_DK, outline=GOLD)

    # left card: figure art, glow frame, text area
    x0, y0, x1, y1 = CARD
    glow(c, sb(CARD), PURPLE, s(5), 0.9)
    d = ImageDraw.Draw(c)
    d.rounded_rectangle(sb(CARD), s(6), fill=(18, 10, 28))
    fig = crop((58, 288, 494, 514), (s(150), s(78))).convert("RGBA")
    fm = Image.new("L", fig.size, 255)
    fmd = ImageDraw.Draw(fm)
    for i in range(s(14)):  # fade the figure's lower edge into the card
        fmd.line((0, fig.height - 1 - i, fig.width, fig.height - 1 - i), fill=int(255 * i / s(14)))
    c.paste(fig, (s(7), s(93)), fm)
    d = ImageDraw.Draw(c)
    gold_frame(d, sb(CARD), 6)
    d.line(sb((22, 222, 142, 222)), fill=GOLD_DK, width=1)
    cx, cy = s(82), s(222)
    d.polygon([(cx, cy - s(2.5)), (cx + s(2.5), cy), (cx, cy + s(2.5)), (cx - s(2.5), cy)], fill=GOLD)

    # reward grid frame
    gx0, gy0 = GRID_X, GRID_Y
    gx1, gy1 = GRID_X + GRID_W, GRID_Y + GRID_H
    d.rounded_rectangle(sb((gx0, gy0, gx1, gy1)), s(5), fill=(14, 10, 20))
    # label column
    d.rectangle(sb((gx0 + 2, FREE_Y, COL_X0 - 2, FREE_Y + ROW_H - 1)), fill=(22, 26, 36))
    d.rectangle(sb((gx0 + 2, ELITE_Y, COL_X0 - 2, ELITE_Y + ROW_H - 1)), fill=(40, 22, 14))
    # tier columns: slate free row, bronze elite row; the 5th column is the gold milestone column
    for i in range(5):
        cx0 = COL_X0 + i * (COL_W + COL_GAP)
        if i == 4:
            mile = vgrad((s(COL_W), s(ELITE_Y + ROW_H - FREE_Y)), (196, 140, 48), (120, 78, 22))
            mile = noise(mile, 5, 9)
            c.paste(mile, (s(cx0), s(FREE_Y)))
            d = ImageDraw.Draw(c)
            d.rectangle(sb((cx0, FREE_Y, cx0 + COL_W - 1, ELITE_Y + ROW_H - 1)), outline=GOLD_LT, width=s(1))
            d.line(sb((cx0 + 2, ELITE_Y - 1, cx0 + COL_W - 3, ELITE_Y - 1)), fill=GOLD_XDK, width=s(1))
            continue
        fr = noise(vgrad((s(COL_W), s(ROW_H)), SLATE_T, SLATE_B), 4, 10 + i)
        el = noise(vgrad((s(COL_W), s(ROW_H)), BRONZE_T, BRONZE_B), 4, 20 + i)
        c.paste(fr, (s(cx0), s(FREE_Y)))
        c.paste(el, (s(cx0), s(ELITE_Y)))
        d = ImageDraw.Draw(c)
        d.rectangle(sb((cx0, FREE_Y, cx0 + COL_W - 1, FREE_Y + ROW_H - 1)), outline=(70, 90, 116), width=1)
        d.rectangle(sb((cx0, ELITE_Y, cx0 + COL_W - 1, ELITE_Y + ROW_H - 1)), outline=(120, 84, 34), width=1)
    d = ImageDraw.Draw(c)
    gold_frame(d, sb((gx0, gy0, gx1, gy1)), 5)
    # FREE / ELITE shields (ELITE drawn without its padlock; the lock is ELITE_LOCK)
    fr = keyed((550, 398, 660, 502), (s(40), s(38)), 60)
    c.paste(fr, (s(168), s(FREE_Y + 18)), fr)
    el = elite_shield()
    c.paste(el, (s(166), s(ELITE_Y + 11)), el)

    # bottom hint rule
    d.line(sb((176, 316, 400, 316)), fill=GOLD_XDK, width=1)
    for x in (172, 404):
        cx, cy = s(x), s(316)
        d.polygon([(cx, cy - s(2.5)), (cx + s(2.5), cy), (cx, cy + s(2.5)), (cx - s(2.5), cy)], fill=GOLD)

    # outer frame
    gold_frame(d, (0, 0, s(W) - 1, s(H) - 1), 7)
    return c


def missions_panel():
    w, h = GRID_W, GRID_H
    img = Image.new("RGBA", (s(w), s(h)), (0, 0, 0, 0))
    body = noise(vgrad((s(w), s(h)), (24, 14, 36), (12, 8, 18)), 4, 31).convert("RGBA")
    m = Image.new("L", body.size, 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, s(w) - 1, s(h) - 1), s(5), fill=255)
    img.paste(body, (0, 0), m)
    d = ImageDraw.Draw(img)
    for r in range(5):  # challenge rows (BuildKarnHub: y 42 + 29 r, 30 tall)
        y0 = 40 + 29 * r
        fill = (40, 22, 56) if r % 2 == 0 else (32, 18, 46)
        d.rounded_rectangle(sb((8, y0, w - 8, y0 + 26)), s(3), fill=fill, outline=(84, 50, 110), width=1)
        d.rounded_rectangle(sb((150, y0 + 16, 262, y0 + 22)), s(2), fill=(10, 6, 14), outline=GOLD_XDK, width=1)
    d.line(sb((8, 34, w - 8, 34)), fill=GOLD_DK, width=s(1))
    gold_frame(d, (0, 0, s(w) - 1, s(h) - 1), 5)
    return img


def xp_seg():
    img = vgrad((s(13), s(6)), (255, 222, 120), (196, 132, 30)).convert("RGBA")
    return img


def main():
    os.makedirs(PNG, exist_ok=True)
    bg = background()
    save(bg.resize((W, H), Image.LANCZOS), BG2)
    save(dark_button(76, 20), TAB, (76, 20))
    save(dark_button(76, 20, on=True), TAB_ON, (76, 20))
    save(dark_button(76, 20, hover=True), TAB_H, (76, 20))
    save(gold_button(100, 20), RANK, (100, 20))
    save(gold_button(100, 20, True), RANK_H, (100, 20))
    save(gold_button(132, 24), UPGRADE, (132, 24))
    save(gold_button(132, 24, True), UPGRADE_H, (132, 24))
    save(gold_button(98, 22), CLAIM, (98, 22))
    save(gold_button(98, 22, True), CLAIM_H, (98, 22))
    save(badge(), BADGE, (46, 40))
    save(xp_seg(), XP_SEG, (13, 6))
    save(cell("free"), CELL, (44, 42))
    save(cell("elite"), CELL_ELITE, (44, 42))
    save(cell("glow"), CELL_GLOW, (44, 42))
    save(keyed((820, 381, 858, 413), (s(12), s(12)), 90), TICK, (12, 12))
    save(keyed((1190, 383, 1222, 415), (s(10), s(12)), 90), LOCK, (10, 12))
    save(keyed((1190, 383, 1222, 415), (s(12), s(14)), 90), ELITE_LOCK, (12, 14))
    save(header("on"), HDR_ON, (56, 15))
    save(header("off"), HDR_OFF, (56, 15))
    save(header("mile"), HDR_MILE, (56, 15))
    save(arrow(True, False), ARROW_L, (16, 16))
    save(arrow(True, True), ARROW_L_H, (16, 16))
    save(arrow(False, False), ARROW_R, (16, 16))
    save(arrow(False, True), ARROW_R_H, (16, 16))
    cl = keyed((1595, 5, 1661, 67), (s(20), s(20)), 40)
    save(cl, CLOSE, (20, 20))
    save(ImageEnhance.Brightness(cl).enhance(1.35), CLOSE_H, (20, 20))
    save(missions_panel(), MISSIONS_BG, (GRID_W, GRID_H))
    save(dark_button(72, 16), SUB, (72, 16))
    save(dark_button(72, 16, on=True), SUB_ON, (72, 16))
    save(dark_button(72, 16, hover=True), SUB_H, (72, 16))
    # tab / button icons (BuildKarnHub draws them as separate sprites at 14 x 14)
    for sid, box in ((25301, (918, 16, 964, 54)), (25302, (1155, 16, 1199, 54)), (25303, (1386, 18, 1436, 52)),
                     (25304, (1393, 160, 1430, 194)), (25305, (148, 756, 214, 802))):
        save(keyed(box, (s(14), s(13 if sid != 25305 else 10)), 60), sid, (14, 13 if sid != 25305 else 10))
    print("battle pass v2 art written to", PNG)


if __name__ == "__main__":
    main()

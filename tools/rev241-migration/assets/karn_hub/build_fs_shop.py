"""The Five Seals Token Shop window 1192 (user 2026-11-16: "make a new design shop interface for this minigame, not the
osrs interface ... unique one, match Karn"): Karn crimson and gold, drawn with the Battle Pass v2 helpers (build_bp2.py).
Backgrounds and buttons only; every word and item is a game component (BuildKarnHub.fiveSealsShop).
  25314 the window (512 x 334): crimson frame, the KARN crest, tab strip, the shard purse, the footer panel
  25315 a reward card (116 x 100)          25316 an upgrade card (156 x 206)
  25317 / 25318 / 25319 a tab (120 x 22): idle / hover / selected
  25320 / 25321 the Buy button (84 x 20)   25322 / 25323 the Unlock button (104 x 22)
Layout (game px) shared with BuildKarnHub.fiveSealsShop: reward cards at x 14 + 122 c, y 84 + 106 r; upgrade cards at
x 14 + 162 c, y 84. Usage: python build_fs_shop.py"""
import os
from PIL import Image, ImageDraw
import build_bp2 as bp

BG, CARD, CARD_BIG = 25314, 25315, 25316
TAB, TAB_H, TAB_ON = 25317, 25318, 25319
BUY, BUY_H, UNLOCK, UNLOCK_H = 25320, 25321, 25322, 25323

CRIMSON_TOP, CRIMSON_BOT = (34, 8, 12), (10, 3, 5)
CRIMSON, CRIMSON_LT, CRIMSON_DK = (170, 28, 40), (220, 70, 80), (70, 10, 16)
PANEL, PANEL_HDR, WELL = (22, 7, 10), (78, 14, 22), (12, 4, 6)


def frame():
    c = bp.noise(bp.vgrad((bp.s(512), bp.s(334)), CRIMSON_TOP, CRIMSON_BOT), 4, 52)
    bp.glow(c, bp.sb((-40, 140, 40, 280)), CRIMSON_DK, bp.s(30), 1.3)
    bp.glow(c, bp.sb((300, -30, 520, 60)), CRIMSON_DK, bp.s(36), 1.1)
    logo = bp.feather(bp.crop((5, 0, 600, 158), (bp.s(110), bp.s(30))).convert("RGBA"), bp.s(3))  # the KARN crest
    c.paste(logo, (bp.s(6), bp.s(9)), logo)
    d = ImageDraw.Draw(c)
    d.line(bp.sb((10, 48, 502, 48)), fill=bp.GOLD_DK, width=bp.s(1))
    return c


def panel(c, box, fill=PANEL):
    bp.glow(c, bp.sb(box), CRIMSON, bp.s(3), 0.5)
    d = ImageDraw.Draw(c)
    d.rounded_rectangle(bp.sb(box), bp.s(5), fill=fill)
    bp.gold_frame(d, bp.sb(box), 5)


def background():
    c = frame()
    panel(c, (10, 80, 502, 294), fill=(16, 5, 8))     # the card bay
    panel(c, (300, 52, 502, 76))                      # the shard purse (an item icon + the balance text sit on it)
    panel(c, (10, 298, 502, 328))                     # the footer: the selected card's details
    d = ImageDraw.Draw(c)
    bp.gold_frame(d, (0, 0, bp.s(512) - 1, bp.s(334) - 1), 7)
    bp.save(c.resize((512, 334), Image.LANCZOS), BG)


def card(w, h, sid, well_y, well_h):
    img = bp.plate(w, h, (52, 12, 18), (18, 5, 8), bp.GOLD_XDK, bp.GOLD_DK, r=5)
    d = ImageDraw.Draw(img)
    # the item well: a dark recess with a crimson glow ring
    wx0, wx1 = bp.s(w / 2 - 24), bp.s(w / 2 + 24)
    d.rounded_rectangle((wx0, bp.s(well_y), wx1, bp.s(well_y + well_h)), bp.s(6), fill=WELL, outline=CRIMSON_DK, width=bp.s(1))
    d.rounded_rectangle((wx0 + bp.s(1.5), bp.s(well_y + 1.5), wx1 - bp.s(1.5), bp.s(well_y + well_h - 1.5)), bp.s(5), outline=(120, 22, 34), width=1)
    # a gold rule under the well, and corner studs
    y = bp.s(well_y + well_h + 3)
    d.line((bp.s(10), y, bp.s(w - 10), y), fill=bp.GOLD_DK, width=bp.s(0.7))
    for x in (bp.s(6), bp.s(w - 6)):
        for yy in (bp.s(6), bp.s(h - 6)):
            d.ellipse((x - bp.s(1.6), yy - bp.s(1.6), x + bp.s(1.6), yy + bp.s(1.6)), fill=bp.GOLD, outline=bp.GOLD_XDK)
    bp.save(img.resize((w, h), Image.LANCZOS), sid)


def tab(sid, on=False, hover=False):
    if on:
        img = bp.plate(120, 22, (150, 26, 38), (70, 10, 16), bp.GOLD, bp.GOLD_LT, r=5)
    else:
        img = bp.plate(120, 22, (64, 16, 22) if hover else (42, 10, 15), (18, 5, 8), bp.GOLD if hover else bp.GOLD_DK,
                       (170, 120, 50) if hover else (110, 74, 30), r=5)
    bp.save(img.resize((120, 22), Image.LANCZOS), sid)


def button(w, h, sid, hover=False):
    bp.save(bp.gold_button(w, h, hover).resize((w, h), Image.LANCZOS), sid)


if __name__ == "__main__":
    os.chdir(bp.HERE)
    background()
    card(116, 100, CARD, 4, 36)
    card(156, 206, CARD_BIG, 6, 38)
    tab(TAB)
    tab(TAB_H, hover=True)
    tab(TAB_ON, on=True)
    button(84, 20, BUY)
    button(84, 20, BUY_H, hover=True)
    button(104, 22, UNLOCK)
    button(104, 22, UNLOCK_H, hover=True)
    print("five seals shop art written")

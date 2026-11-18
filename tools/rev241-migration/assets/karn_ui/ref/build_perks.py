"""Karn Perks (1151) from the user's reference (perks_ref.png, 2026-11-14): sprites + background.
25150 background (512 x 334); 25190 row card, 25191 row card hover, 25192 selected row card; 25177-25182 ornate
scrollbar (shared by the three windows); 25193 / 25194 half button (normal / hover), 25195 / 25196 full button."""
import sys, os
from PIL import Image, ImageDraw
sys.path.insert(0, os.path.dirname(__file__))
from refkit import Ref, save, brighter

here = os.path.dirname(os.path.abspath(__file__))
png = os.path.join(here, "..", "png")
r = Ref(os.path.join(here, "perks_ref.png"))

ROW = (72, 470, 872, 592)          # "Accurate Blows" card
SEL = (68, 334, 868, 456)          # the highlighted "A Slaying Experience" card, cut to the same size
ROW_SIZE = (round((ROW[2] - ROW[0]) * r.sx), round((ROW[3] - ROW[1]) * r.sy))

# ---- sprites first (from a copy, before the background is cleaned)
c = Ref(os.path.join(here, "perks_ref.png"))
for box in [(120, 497, 190, 566), (232, 482, 705, 586), (725, 495, 852, 576)]:   # the icon inside the hex, name / desc, badge text
    c.erase(*box)
for box in [(120, 360, 190, 430), (232, 343, 705, 447), (722, 355, 855, 440)]:
    c.erase(*box)
row = c.sprite(ROW, ROW_SIZE)
save(row, png, 25190)
save(brighter(row, 1.18), png, 25191)
save(c.sprite(SEL, ROW_SIZE), png, 25192)

# ---- background
r.erase(118, 168, 292, 210)                     # Points: 688
r.erase(380, 170, 888, 208)                     # Tree Lv 1 + the xp bar
r.copy((980, 165, 1040, 216), (912, 165), (60, 51))   # loadout 1 as a normal square
for cx in (942, 1010, 1075, 1142, 1208):
    r.erase(cx - 15, 176, cx + 15, 206)         # the digits
r.erase(1330, 172, 1498, 207)                   # "Search..."
r.erase(1500, 172, 1536, 207)                   # the clear x
r.copy((655, 235, 797, 291), (45, 235), (125, 56))    # "All" as a normal tab
r.erase(55, 243, 160, 283)
r.text((107, 263), "All", 25, (238, 226, 205))
r.erase(66, 322, 882, 900)                      # the list rows
r.erase(888, 318, 930, 900)                     # the scrollbar
r.erase(1460, 245, 1536, 285)                   # 3 / 3
r.erase(985, 293, 1545, 420)                    # the equipped slots
r.erase(1133, 522, 1430, 614)                   # Select a perk + underline
r.erase(993, 655, 1320, 702)                    # Pick a perk...
r.erase(993, 710, 1508, 742)                    # the divider
r.erase(993, 744, 1495, 860)                    # the body text
save(r.background(), png, 25150)

# ---- ornate scrollbar (25177 up, 25178 down, 25179-25181 thumb top / middle / bottom, 25182 track)
o = Ref(os.path.join(here, "perks_ref.png"))
save(o.sprite((892, 326, 926, 358), (16, 16)), png, 25177)
save(o.sprite((892, 861, 926, 893), (16, 16)), png, 25178)
for i, h in enumerate((4, 5, 4)):
    im = Image.new("RGBA", (16, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rectangle((3, 0, 12, h - 1), fill=(60, 10, 14))
    for x in range(4, 12):
        t = (x - 4) / 7
        d.line((x, 1 if i == 0 else 0, x, h - (2 if i == 2 else 1)), fill=(int(214 - 80 * t), int(40 - 20 * t), int(46 - 20 * t)))
    d.line((3, 0, 3, h - 1), fill=(150, 110, 40))
    d.line((12, 0, 12, h - 1), fill=(150, 110, 40))
    if i == 0:
        d.line((4, 0, 11, 0), fill=(212, 175, 55))
    if i == 2:
        d.line((4, h - 1, 11, h - 1), fill=(150, 110, 40))
    save(im, png, 25179 + i)
tr = Image.new("RGBA", (16, 5), (0, 0, 0, 0))
d = ImageDraw.Draw(tr)
d.rectangle((4, 0, 11, 4), fill=(22, 15, 13))
d.line((4, 0, 4, 4), fill=(110, 80, 34))
d.line((11, 0, 11, 4), fill=(110, 80, 34))
save(tr, png, 25182)


# ---- buttons: crimson with a gold rim, in the reference's style
def button(w, h, hover):
    im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rounded_rectangle((0, 0, w - 1, h - 1), 3, fill=(40, 8, 10))
    for y in range(1, h - 1):
        t = y / max(1, h - 2)
        base = (200, 40, 46) if hover else (170, 26, 34)
        d.line((1, y, w - 2, y), fill=tuple(int(base[k] * (1 - 0.45 * t)) for k in range(3)))
    d.rounded_rectangle((0, 0, w - 1, h - 1), 3, outline=(232, 190, 80) if hover else (212, 165, 55))
    d.line((3, 1, w - 4, 1), fill=(255, 210, 170))
    return im


save(button(79, 16, False), png, 25193)   # RestylePerks: half = (gx(1500) - gx(995) - 4) / 2
save(button(79, 16, True), png, 25194)
save(button(162, 15, False), png, 25195)
save(button(162, 15, True), png, 25196)
print("perks: background + sprites written; row", ROW_SIZE)

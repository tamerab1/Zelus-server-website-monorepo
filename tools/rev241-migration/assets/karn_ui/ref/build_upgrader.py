"""Karn Upgrader (1158) from the user's reference (upgrader_ref.png, 2026-11-14): the title re-lettered KARN UPGRADER
(the reference says Relic Forge), three category tabs (ours; the reference's fourth "Relics" tab is removed), six
reagent slots. 25100 background; 25114 recipe card, 25115 card hover, 25116 selected card; 25117 a reagent slot frame."""
import sys, os
sys.path.insert(0, os.path.dirname(__file__))
from refkit import Ref, save, brighter, gold_title, fill_avg

here = os.path.dirname(os.path.abspath(__file__))
png = os.path.join(here, "..", "png")
src = os.path.join(here, "upgrader_ref.png")
r = Ref(src)

CARD = (62, 432, 574, 530)       # "Twisted Zaryte Bow"
SEL = (58, 322, 570, 420)        # the highlighted first card, cut to the same size
CARD_SIZE = (round((CARD[2] - CARD[0]) * r.sx), round((CARD[3] - CARD[1]) * r.sy))
SLOT = (707, 588, 808, 668)      # a reagent slot frame

c = Ref(src)
fill_avg(c, (90, 444, 180, 522), (84, 470, 88, 500))
for b in [(208, 440, 562, 480), (226, 494, 330, 518), (420, 488, 548, 522)]:
    c.erase(*b)
fill_avg(c, (86, 332, 176, 412), (80, 360, 84, 390))
for b in [(208, 330, 560, 372), (226, 384, 330, 410), (420, 380, 548, 414)]:
    c.erase(*b)
fill_avg(c, (716, 598, 800, 660), (712, 610, 714, 650))
card = c.sprite(CARD, CARD_SIZE)
save(card, png, 25114)
save(brighter(card, 1.18), png, 25115)
save(c.sprite(SEL, CARD_SIZE), png, 25116)
slot = c.sprite(SLOT, (38, 34))   # RestyleForge SLOT_W x SLOT_H
save(slot, png, 25117)

# ---- the background
r.erase(498, 38, 748, 122)                       # RELIC
r.erase(842, 38, 1098, 122)                      # FORGE
gold_title(r, (490, 30, 752, 128), "KARN", 74)
gold_title(r, (850, 34, 1104, 124), "UPGRADER", 40)
r.erase(78, 162, 525, 200)                       # the subtitle
r.erase(1102, 164, 1372, 200)                    # Search recipes...
r.erase(1420, 164, 1510, 200)                    # Clear
r.copy((208, 235, 345, 291), (50, 235), (145, 56))   # Weapons as a normal tab
r.erase(62, 245, 186, 282)
r.text((123, 263), "Weapons", 24, (238, 226, 205))
r.erase(500, 232, 642, 294)                      # the fourth tab (Relics): we have three categories
r.erase(52, 312, 585, 930)                       # the recipe cards
r.erase(592, 318, 638, 925)                      # the scrollbar
fill_avg(r, (764, 344, 868, 434), (770, 346, 790, 352))   # the base item (inside the frame)
r.erase(682, 450, 948, 550, lr=True)             # its name (from the side edges: the frame is above)
r.erase(955, 328, 1258, 372)                     # Success Rate
r.fill(962, 384, 1250, 411, (26, 16, 20))       # the bar: an empty track (our segments fill it)
r.erase(985, 448, 1228, 505)                     # Base / Donator
fill_avg(r, (1348, 346, 1454, 434), (1352, 348, 1370, 353))  # the outcome item (inside the glowing frame)
r.erase(1300, 452, 1500, 518)                    # its name
fill_avg(r, (690, 586, 1000, 712), (1030, 600, 1080, 700))  # the reagent row (our six slot frames are drawn on it)
r.erase(700, 770, 1060, 912)                     # the stat lines
r.erase(1200, 768, 1510, 838)                    # the failure note (the (i) stays)
r.erase(1252, 858, 1488, 908)                    # "Upgrade Item" (the button and its anvil stay)
save(r.background(), png, 25100)
print("upgrader: card", CARD_SIZE)

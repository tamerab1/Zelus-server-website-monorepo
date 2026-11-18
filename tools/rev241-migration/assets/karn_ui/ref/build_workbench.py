"""Karn Workbench (1153) from the user's reference (workbench_ref.png, 2026-11-14). The reference's quantity selector
(- 1 +) is left out: the workbench has no quantity (the art keeps its Add Minimum and Upgrade Item buttons).
25200 background; 25208 perk card, 25209 card hover, 25210 selected card."""
import sys, os
sys.path.insert(0, os.path.dirname(__file__))
from refkit import Ref, save, brighter, fill_avg

here = os.path.dirname(os.path.abspath(__file__))
png = os.path.join(here, "..", "png")
src = os.path.join(here, "workbench_ref.png")
r = Ref(src)

CARD = (44, 333, 404, 430)       # "Precision Strike"
SEL = (42, 222, 402, 319)        # the highlighted "Quick Step", cut to the same size
# 2026-11-15 (user: "texts too small"): cards 40 tall (were 33) so the name can take two p12 lines and the tier line p12
CARD_SIZE = (round((CARD[2] - CARD[0]) * r.sx), 40)

c = Ref(src)
fill_avg(c, (56, 342, 146, 422), (50, 360, 53, 400))
c.erase(158, 338, 398, 380)
c.erase(158, 382, 398, 424)
fill_avg(c, (54, 230, 144, 310), (48, 250, 51, 290))
c.erase(156, 228, 396, 270)
c.erase(156, 272, 396, 312)
card = c.sprite(CARD, CARD_SIZE)
save(card, png, 25208)
save(brighter(card, 1.18), png, 25209)
save(c.sprite(SEL, CARD_SIZE), png, 25210)

# ---- the background
r.erase(40, 214, 410, 912)                       # the perk cards
r.erase(330, 147, 1290, 181, lr=True)            # the painted steps line: live game text now (WorkbenchHub SUB_HEADER, p12)
r.erase(414, 220, 460, 910)                      # the scrollbar
r.copy((1518, 220, 1532, 314), (1084, 220), (436, 94))   # the header band rebuilt from its clean right end (perk icon, name, tier gone)
r.erase(1098, 330, 1525, 452)                    # the description
r.erase(1133, 468, 1347, 508, lr=True)           # Roll Odds (Base)
r.erase(1236, 515, 1345, 694)                    # the percentages (the coloured Lv pills stay)
r.erase(1086, 724, 1324, 786)                    # the quantity selector (not a workbench feature)
r.erase(1362, 738, 1528, 774)                    # Add Minimum (the button stays)
r.erase(1240, 815, 1488, 882)                    # Upgrade Item (the button and its anvil stay)
r.fill(708, 306, 822, 418, (40, 24, 14))         # the boots in the target frame
r.erase(596, 452, 936, 494)                      # Boots of Karn
r.erase(596, 498, 938, 554)                      # the hint
fill_avg(r, (522, 634, 616, 708), (518, 640, 520, 700))   # the two required materials
fill_avg(r, (656, 634, 752, 708), (652, 640, 654, 700))
r.erase(506, 714, 772, 750, lr=True)             # their counts
save(r.background(), png, 25200)
print("workbench: card", CARD_SIZE)

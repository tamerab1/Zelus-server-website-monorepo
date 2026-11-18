"""The Five Seals lobby windows (user 2026-11-16: "party boards like raids, custom design, and the scoreboard"), in the
Karn crimson / gold (round 2, 2026-11-16: matched to the Token Shop 1192, build_fs_shop.py; build_bp2.py helpers): the Party Board 1190 and the Hall of Seals 1191.
Backgrounds only; every word is a game text component (BuildKarnHub.fiveSealsParty / fiveSealsHall).
Writes png/25306.png (party) and png/25307.png (hall). Usage: python build_fs_ui.py"""
import os
from PIL import Image, ImageDraw
import build_bp2 as bp

S = bp.S
PARTY_BG, HALL_BG = 25306, 25307


def frame():
    c = bp.noise(bp.vgrad((bp.s(512), bp.s(334)), (34, 8, 12), (10, 3, 5)), 4, 41)
    bp.glow(c, bp.sb((-40, 120, 40, 260)), (70, 10, 16), bp.s(30), 1.3)
    bp.glow(c, bp.sb((300, -30, 520, 60)), (70, 10, 16), bp.s(36), 1.1)
    logo = bp.feather(bp.crop((5, 0, 600, 158), (bp.s(110), bp.s(30))).convert("RGBA"), bp.s(3))  # the KARN crest only
    c.paste(logo, (bp.s(6), bp.s(9)), logo)
    d = ImageDraw.Draw(c)
    d.line(bp.sb((10, 48, 502, 48)), fill=bp.GOLD_DK, width=bp.s(1))
    return c


def panel(c, box, header=True):
    d = ImageDraw.Draw(c)
    x0, y0, x1, y1 = box
    bp.glow(c, bp.sb(box), (170, 28, 40), bp.s(3), 0.5)
    d = ImageDraw.Draw(c)
    d.rounded_rectangle(bp.sb(box), bp.s(5), fill=(22, 7, 10))
    if header:
        d.rounded_rectangle(bp.sb((x0 + 3, y0 + 3, x1 - 3, y0 + 21)), bp.s(4), fill=(78, 14, 22))
        d.line(bp.sb((x0 + 3, y0 + 22, x1 - 3, y0 + 22)), fill=bp.GOLD_DK, width=bp.s(1))
    bp.gold_frame(d, bp.sb(box), 5)


def rows(c, x0, x1, y0, n, pitch, h):
    d = ImageDraw.Draw(c)
    for r in range(n):
        y = y0 + r * pitch
        fill = (44, 12, 18) if r % 2 == 0 else (34, 9, 14)
        d.rounded_rectangle(bp.sb((x0, y, x1, y + h)), bp.s(3), fill=fill, outline=(110, 40, 46), width=1)


def party():
    c = frame()
    panel(c, (10, 54, 304, 270))          # members (BuildKarnHub: rows y 82 + 37 r, 34 tall)
    rows(c, 16, 298, 82, 5, 37, 33)
    panel(c, (312, 54, 502, 270))         # run info
    d = ImageDraw.Draw(c)
    d.line(bp.sb((176, 316, 400, 316)), fill=bp.GOLD_XDK, width=1)
    bp.gold_frame(d, (0, 0, bp.s(512) - 1, bp.s(334) - 1), 7)
    bp.save(c.resize((512, 334), Image.LANCZOS), PARTY_BG)


def hall():
    c = frame()
    for box in ((10, 54, 252, 292), (260, 54, 502, 292)):   # solo / group (rows y 82 + 40 r, 36 tall)
        panel(c, box)
        rows(c, box[0] + 6, box[2] - 6, 82, 5, 40, 36)
    d = ImageDraw.Draw(c)
    bp.gold_frame(d, (0, 0, bp.s(512) - 1, bp.s(334) - 1), 7)
    bp.save(c.resize((512, 334), Image.LANCZOS), HALL_BG)


if __name__ == "__main__":
    os.chdir(bp.HERE)
    party()
    hall()
    print("five seals ui art written")

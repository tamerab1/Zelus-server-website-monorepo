"""The Karn blackjack card back (user 2026-11-16: "blackjack uses Zelus cards, can we make Karn logo cards?").
Sprite 5830 (87 x 107): the dealer's hole card and the three-card deck in the corner of interface 998.
Keeps the old card's exact silhouette (its alpha), so it lines up with the face cards (5829): a silver rim, deep Karn
crimson with a faint diamond lattice, a thin gold inner border, the K emblem (banners/png/k_emblem.png) and the KARN
wordmark (banners/png/logo_0..2.png) beneath it. Drawn at 4x and reduced for clean edges.
Usage: python make_card.py <old 5830.png>  -> png/5830.png"""
import os, sys
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.dirname(HERE)
W, H, S = 87, 107, 4

old = Image.open(sys.argv[1]).convert("RGBA")
assert old.size == (W, H), old.size
alpha = np.array(old)[:, :, 3] > 0

ys, xs = np.where(alpha)
X0, X1, Y0, Y1 = xs.min(), xs.max() + 1, ys.min(), ys.max() + 1  # the card itself (53 x 78 at 18,15)
CW, CH = X1 - X0, Y1 - Y0
big = Image.new("RGBA", (W * S, H * S), (0, 0, 0, 0))
cardbig = Image.new("RGBA", (CW * S, CH * S), (0, 0, 0, 0))
d = ImageDraw.Draw(cardbig)
# silver rim + crimson field (radial: brighter in the middle)
d.rounded_rectangle([0, 0, CW * S - 1, CH * S - 1], radius=6 * S, fill=(210, 210, 218, 255))
yy, xx = np.mgrid[0:CH * S, 0:CW * S]
cx, cy = CW * S / 2, CH * S * 0.42
r = np.sqrt(((xx - cx) / (CW * S / 2)) ** 2 + ((yy - cy) / (CH * S / 2)) ** 2)
t = np.clip(r, 0, 1)[..., None]
field = (np.array([160, 20, 28]) * (1 - t) + np.array([56, 4, 10]) * t)
lat = ((xx + yy) // (5 * S) + (xx - yy) // (5 * S)) % 2  # faint diamond lattice
field = field * (1 - 0.08 * lat[..., None])
fimg = Image.fromarray(np.clip(field, 0, 255).astype(np.uint8), "RGB").convert("RGBA")
mask = Image.new("L", cardbig.size, 0)
ImageDraw.Draw(mask).rounded_rectangle([3 * S, 3 * S, CW * S - 3 * S - 1, CH * S - 3 * S - 1], radius=4 * S, fill=255)
cardbig.paste(fimg, (0, 0), mask)
d.rounded_rectangle([5 * S, 5 * S, CW * S - 5 * S - 1, CH * S - 5 * S - 1], radius=3 * S, outline=(214, 162, 62, 255), width=S)

# K emblem with a soft dark glow behind it
emb = Image.open(os.path.join(ASSETS, "banners", "png", "k_emblem.png")).convert("RGBA")
es = 44 * S
emb = emb.resize((es, es), Image.LANCZOS)
ex, ey = (CW * S - es) // 2, 9 * S
glow = Image.new("RGBA", cardbig.size, (0, 0, 0, 0))
glow.paste((20, 0, 0, 190), (ex, ey), emb.split()[3].filter(ImageFilter.GaussianBlur(3 * S)))
cardbig = Image.alpha_composite(cardbig, glow)
cardbig.alpha_composite(emb, (ex, ey))

# KARN wordmark from the three banner tiles
tiles = [Image.open(os.path.join(ASSETS, "banners", "png", f"logo_{i}.png")).convert("RGBA") for i in range(3)]
word = Image.new("RGBA", (128 * 3, 128), (0, 0, 0, 0))
for i, tl in enumerate(tiles):
    word.alpha_composite(tl, (128 * i, 0))
word = word.crop(word.getbbox())
ww = 40 * S
word = word.resize((ww, int(word.height * ww / word.width)), Image.LANCZOS)
cardbig.alpha_composite(word, ((CW * S - ww) // 2, 56 * S))
big.alpha_composite(cardbig, (X0 * S, Y0 * S))

card = big.resize((W, H), Image.LANCZOS)
a = np.array(card)
a[:, :, 3] = np.where(alpha, 255, 0)  # the old card's exact silhouette
rgb = Image.fromarray(a[:, :, :3], "RGB").quantize(colors=250, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE).convert("RGB")
out = np.dstack([np.array(rgb), a[:, :, 3]])
out[(out[:, :, 3] > 0) & (out[:, :, :3].sum(axis=2) == 0)] = [1, 1, 1, 255]  # pure black = transparent in sprites
os.makedirs(os.path.join(HERE, "png"), exist_ok=True)
Image.fromarray(out.astype(np.uint8), "RGBA").save(os.path.join(HERE, "png", "5830.png"))
print("wrote png/5830.png")

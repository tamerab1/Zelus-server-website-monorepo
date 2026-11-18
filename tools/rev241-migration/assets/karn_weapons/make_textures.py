"""The Karn weapons' animated textures (user 2026-11-15: "nice custom textures, moving red colour, clean, unique"):
128 x 128, seamlessly tileable (every pattern term has a whole number of periods across the tile), so the client's
texture scroll reads as a continuous flow.
  25262 (texture 514) the Karn sword: dark crimson with bright red flowing veins and a few gold sparks
  25263 (texture 515) the Karn bow:   ember red with molten orange-gold ribbons
Usage: python make_textures.py"""
import math, os, random
import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "png")
os.makedirs(OUT, exist_ok=True)
N = 128


def field(seed, terms):
    rnd = random.Random(seed)
    y, x = np.mgrid[0:N, 0:N] / N * 2 * math.pi
    f = np.zeros((N, N))
    for _ in range(terms):
        kx, ky = rnd.randint(-3, 3), rnd.randint(1, 4)
        ph = rnd.random() * 2 * math.pi
        f += np.sin(kx * x + ky * y + ph) / (abs(kx) + ky)
    return (f - f.min()) / (f.max() - f.min())


def ramp(v, stops):
    out = np.zeros(v.shape + (3,))
    for (a, ca), (b, cb) in zip(stops, stops[1:]):
        m = (v >= a) & (v <= b)
        t = ((v - a) / max(1e-6, b - a))[m][:, None]
        out[m] = np.array(ca) * (1 - t) + np.array(cb) * t
    return out


def save(rgb, sid):
    img = Image.fromarray(np.clip(rgb, 1, 255).astype(np.uint8), "RGB")
    img = img.quantize(colors=250, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE).convert("RGB")
    img.save(os.path.join(OUT, f"{sid}.png"))
    a = np.array(img).reshape(-1, 3).mean(0)
    print(sid, "average rgb", [int(c) for c in a])


# the sword: veins = where two flowing fields cross (thin bright ridges)
v = field(3, 9)
w = field(11, 7)
ridge = 1 - np.abs(v - w) * 3.2
ridge = np.clip(ridge, 0, 1) ** 3
sword = ramp(ridge, [(0, (34, 2, 8)), (0.35, (96, 8, 18)), (0.7, (200, 22, 34)), (0.92, (255, 70, 60)), (1, (255, 196, 96))])
save(sword, 25262)

# the bow: banded molten flow
b = field(21, 8)
bands = (np.sin(b * 6 * math.pi) * 0.5 + 0.5) ** 2
bow = ramp(bands * 0.85 + b * 0.15, [(0, (52, 6, 6)), (0.4, (150, 18, 20)), (0.75, (230, 70, 30)), (1, (255, 190, 80))])
save(bow, 25263)

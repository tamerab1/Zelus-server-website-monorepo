"""
The Five Seals' Karnwood tree textures (user 2026-11-16: "custom tree and rocks with colors that match karn").

Inputs are the two magic-tree textures exported from the rev 241 cache (src/tex8_sprite455.png: the shared green leaves,
src/tex34_sprite479.png: the magic tree's drifting yellow sparkles). Outputs, read by cache stage 51 (PatchKarnwood):
  png/25308.png  texture 272 - Karn crimson leaves (hue moved from green to crimson, gold kept on the brightest tips)
  png/25309.png  texture 273 - ember sparkles (yellow stars -> gold cores fading to crimson)
Transparent pixels stay transparent (the leaf cut-outs and the sparkle background).
Usage: python make_textures.py
"""
import colorsys
import os

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))


def leaves(src, dst):
    im = Image.open(src).convert("RGBA")
    px = im.load()
    # the source leaves are dark overall: split by their own lightness spread (brightest third gold, middle crimson)
    ls = sorted(colorsys.rgb_to_hls(*(v / 255 for v in px[x, y][:3]))[1]
                for y in range(im.height) for x in range(im.width) if px[x, y][3])
    gold_at, crimson_at = ls[len(ls) * 2 // 3], ls[len(ls) // 4]
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            h, l, s = colorsys.rgb_to_hls(r / 255, g / 255, b / 255)
            if l >= gold_at:  # the lit leaves: Karn gold (round 2, user: "fully red")
                nh, ns, nl = 0.115, 0.9, min(0.68, 0.42 + l * 0.6)
            elif l >= crimson_at:  # mid leaves: rich crimson
                nh, ns, nl = 0.985, 0.85, min(0.45, 0.2 + l * 0.8)
            else:  # the shadowed depths: dark crimson
                nh, ns, nl = 0.985, 0.75, 0.12 + l * 0.5
            nr, ng, nb = colorsys.hls_to_rgb(nh, nl, ns)
            px[x, y] = (int(nr * 255), int(ng * 255), int(nb * 255), 255)
    im.save(dst)


def embers(src, dst):
    im = Image.open(src).convert("RGBA")
    px = im.load()
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            h, l, s = colorsys.rgb_to_hls(r / 255, g / 255, b / 255)
            if l > 0.88:  # the star cores: rich gold
                nh, ns, nl = 0.11, 1.0, 0.6
            elif l > 0.75:  # the star bodies: ember orange
                nh, ns, nl = 0.05, 1.0, 0.5
            else:  # the trails: ember crimson
                nh, ns, nl = 0.0, 0.95, 0.42
            nr, ng, nb = colorsys.hls_to_rgb(nh, nl, ns)
            px[x, y] = (int(nr * 255), int(ng * 255), int(nb * 255), 255)
    im.save(dst)


def pad128(path):
    """textures must be 128x128 (the GPU texture loader throws on anything else; the cache stores the source sprites
    trimmed, 121 / 118 wide, at offset 0,0 inside a 128x128 frame)"""
    im = Image.open(path).convert("RGBA")
    if im.size != (128, 128):
        out = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
        out.paste(im, (0, 0))
        out.save(path)


if __name__ == "__main__":
    os.makedirs(os.path.join(HERE, "png"), exist_ok=True)
    leaves(os.path.join(HERE, "src", "tex8_sprite455.png"), os.path.join(HERE, "png", "25308.png"))
    embers(os.path.join(HERE, "src", "tex34_sprite479.png"), os.path.join(HERE, "png", "25309.png"))
    pad128(os.path.join(HERE, "png", "25308.png"))
    pad128(os.path.join(HERE, "png", "25309.png"))
    print("ok")

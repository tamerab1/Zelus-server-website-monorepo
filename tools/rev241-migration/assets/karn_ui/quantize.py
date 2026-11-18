"""png/*.png (full colour, from MakeKarnUiArt) -> png_q/*.png with at most 254 colours (Floyd-Steinberg dither), alpha cut
at 128 (sprites have on/off transparency) and pure black lifted to 1,1,1 (0 is the transparent index in a sprite).
Usage: python quantize.py <assets/karn_ui>"""
import os
import sys

from PIL import Image

root = sys.argv[1]
src, dst = os.path.join(root, "png"), os.path.join(root, "png_q")
os.makedirs(dst, exist_ok=True)
for name in sorted(os.listdir(src)):
    im = Image.open(os.path.join(src, name)).convert("RGBA")
    mask = im.getchannel("A").point(lambda v: 255 if v >= 128 else 0)
    rgb = Image.new("RGB", im.size, (0, 0, 0))
    rgb.paste(im.convert("RGB"), mask=mask)
    q = rgb.quantize(colors=254, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.FLOYDSTEINBERG).convert("RGB")
    out = Image.new("RGBA", im.size, (0, 0, 0, 0))
    out.paste(q, mask=mask)
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = px[x, y]
            if a and r == g == b == 0:
                px[x, y] = (1, 1, 1, 255)
    out.save(os.path.join(dst, name))
print("quantized", len(os.listdir(dst)), "images to", dst)

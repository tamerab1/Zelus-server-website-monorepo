"""Shared helpers for building the Karn interfaces from the user's reference art (2026-11-14).

The references are 1589 x 990 mock-ups. Dynamic content (names, numbers, item icons, rows) is erased at full resolution,
then the image is scaled to the 512 x 334 interface as the window background; reusable pieces (row cards, slots, tabs,
buttons) are cut out as sprites. Coordinates in the build scripts are REFERENCE pixels; G() converts to game pixels.
"""
import os, random
from PIL import Image, ImageDraw, ImageFont, ImageFilter

GW, GH = 512, 334


class Ref:
    def __init__(self, path):
        self.im = Image.open(path).convert("RGB")
        self.W, self.H = self.im.size
        self.sx, self.sy = GW / self.W, GH / self.H

    def g(self, x, y):
        return round(x * self.sx), round(y * self.sy)

    def gbox(self, x0, y0, x1, y1):
        a, b = self.g(x0, y0)
        c, d = self.g(x1, y1)
        return a, b, c - a, d - b

    def erase(self, x0, y0, x1, y1, seed=1, lr=False, tb=False):
        """fill the box from its border colours (bilinear blend of the four edges; lr: the left / right edges only) with a little grain"""
        im, px = self.im, self.im.load()
        x0, y0, x1, y1 = max(1, x0), max(1, y0), min(self.W - 2, x1), min(self.H - 2, y1)
        top = [px[x, y0 - 1] for x in range(x0, x1 + 1)]
        bot = [px[x, y1 + 1] for x in range(x0, x1 + 1)]
        lef = [px[x0 - 1, y] for y in range(y0, y1 + 1)]
        rig = [px[x1 + 1, y] for y in range(y0, y1 + 1)]
        w, h = x1 - x0, y1 - y0
        rnd = random.Random(seed)
        for j in range(h + 1):
            v = j / h if h else 0
            for i in range(w + 1):
                u = i / w if w else 0
                c = [0, 0, 0]
                for k in range(3):
                    tbv = top[i][k] * (1 - v) + bot[i][k] * v
                    side = lef[j][k] * (1 - u) + rig[j][k] * u
                    c[k] = side if lr else tbv if tb else (tbv + side) / 2
                n = rnd.randint(-3, 3)
                px[x0 + i, y0 + j] = tuple(max(0, min(255, int(c[k] + n))) for k in range(3))
        # soften the seams
        region = im.crop((x0 - 2, y0 - 2, x1 + 3, y1 + 3)).filter(ImageFilter.GaussianBlur(1.2))
        im.paste(region, (x0 - 2, y0 - 2))

    def fill(self, x0, y0, x1, y1, rgb):
        ImageDraw.Draw(self.im).rectangle((x0, y0, x1, y1), fill=rgb)

    def copy(self, src, dst_xy, size=None):
        """copy a reference box (x0,y0,x1,y1) to dst (x, y), optionally resized to (w, h) reference px"""
        part = self.im.crop(src)
        if size:
            part = part.resize(size, Image.LANCZOS)
        self.im.paste(part, dst_xy)

    def text(self, xy, s, size, rgb, font="georgiab.ttf", anchor="mm"):
        f = ImageFont.truetype(os.path.join("C:/Windows/Fonts", font), size)
        d = ImageDraw.Draw(self.im)
        x, y = xy
        d.text((x + 2, y + 2), s, font=f, fill=(0, 0, 0), anchor=anchor)
        d.text(xy, s, font=f, fill=rgb, anchor=anchor)

    def background(self):
        return self.im.resize((GW, GH), Image.LANCZOS)

    def sprite(self, box, size):
        """a cut-out at game size (w, h)"""
        return self.im.crop(box).resize(size, Image.LANCZOS)


def save(img, png_dir, sprite_id):
    img.convert("RGBA").save(os.path.join(png_dir, f"{sprite_id}.png"))


def brighter(img, f=1.15):
    from PIL import ImageEnhance
    return ImageEnhance.Brightness(img).enhance(f)


def gold_title(ref, box, s, size, font="georgiab.ttf"):
    """ornate title lettering: a dark outline, a vertical gold gradient fill, centred in a reference box"""
    from PIL import Image, ImageDraw, ImageFont
    x0, y0, x1, y1 = box
    f = ImageFont.truetype(os.path.join("C:/Windows/Fonts", font), size)
    w, h = x1 - x0, y1 - y0
    mask = Image.new("L", (w, h), 0)
    md = ImageDraw.Draw(mask)
    md.text((w / 2, h / 2), s, font=f, fill=255, anchor="mm")
    outline = Image.new("L", (w, h), 0)
    od = ImageDraw.Draw(outline)
    od.text((w / 2, h / 2), s, font=f, fill=255, anchor="mm", stroke_width=max(2, size // 14))
    grad = Image.new("RGB", (w, h))
    gd = ImageDraw.Draw(grad)
    for y in range(h):
        t = y / max(1, h - 1)
        top, mid, bot = (255, 236, 160), (232, 170, 60), (150, 90, 20)
        c = [int(top[k] + (mid[k] - top[k]) * (t / 0.5)) if t < 0.5 else int(mid[k] + (bot[k] - mid[k]) * ((t - 0.5) / 0.5)) for k in range(3)]
        gd.line((0, y, w, y), fill=tuple(c))
    region = ref.im.crop(box)
    region.paste((40, 18, 8), (0, 0), outline)
    region.paste(grad, (0, 0), mask)
    ref.im.paste(region, (x0, y0))


def fill_avg(ref, box, sample):
    """fill a box with the average colour of a sample box (for slot interiors / rows where edge blending streaks)"""
    from PIL import ImageStat, ImageDraw
    m = ImageStat.Stat(ref.im.crop(sample)).mean
    ImageDraw.Draw(ref.im).rectangle(box, fill=tuple(int(v) for v in m[:3]))


def copy_feather(ref, src, dst_xy, feather=10):
    """paste a patch with soft edges (a blurred mask), so a copied texture blends into its surroundings"""
    from PIL import Image, ImageDraw, ImageFilter
    part = ref.im.crop(src)
    w, h = part.size
    mask = Image.new("L", (w, h), 0)
    ImageDraw.Draw(mask).rectangle((feather, feather, w - feather - 1, h - feather - 1), fill=255)
    mask = mask.filter(ImageFilter.GaussianBlur(feather / 2))
    ref.im.paste(part, dst_xy, mask)

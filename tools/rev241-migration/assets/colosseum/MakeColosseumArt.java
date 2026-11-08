import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import javax.imageio.ImageIO;

/**
 * Paints the Colosseum's two windows (2026-11-08), to art/&lt;sprite&gt;.png for cache stage 21: the Arbiter's Reliquary (1183,
 * sprites 24792-24799), a violet vault door with a gabled top, and the Trial picker (1182, sprites 24802-24811), a crimson arena
 * gate with battlements. A sprite group holds at most 255 colours, so each window is mapped onto its own palette.
 * Usage: MakeColosseumArt &lt;assets/colosseum&gt;
 */
public final class MakeColosseumArt {

	static final int W = 488, H = 308;
	static final int[] VAULT = palette(new int[][] { { 0x0f0b14, 0x4a3f55, 70 }, { 0x2a1440, 0xa872e8, 70 }, { 0x3a2a10, 0xf4dc9a, 80 }, { 0xf4dc9a, 0xffffff, 20 } });
	static final int[] ARENA = palette(new int[][] { { 0x120d0c, 0x5a4c44, 70 }, { 0x3a0a0a, 0xd8402c, 70 }, { 0x3a2a10, 0xecc67a, 80 }, { 0xecc67a, 0xfff4d0, 20 } });
	static File out;
	static int[] palette;

	public static void main(String[] a) throws Exception {
		out = new File(a[0], "art");
		out.mkdirs();
		palette = VAULT;
		vault(24792);
		button(24793, 128, 26, 8, new Color(0x4a3f55), new Color(0x241c2c), 0xb58a44);
		button(24794, 128, 26, 8, new Color(0x6a5a7c), new Color(0x362a44), 0xe8c878);
		button(24795, 128, 26, 8, new Color(0x7a48b8), new Color(0x3a1e60), 0xf4dc9a);
		card(24796, new Color(0x2c2436), new Color(0x1a1420), 0x8a6a34);
		card(24797, new Color(0x45355a), new Color(0x2a2036), 0xe8c878);
		button(24798, 52, 20, 5, new Color(0x7a48b8), new Color(0x3a1e60), 0xf4dc9a);
		button(24799, 52, 20, 5, new Color(0xa872e8), new Color(0x5a2e90), 0xffffff);
		palette = ARENA;
		arena(24802);
		button(24803, 160, 19, 5, new Color(0x3a302c), new Color(0x221a18), 0x7a5c30);
		button(24804, 160, 19, 5, new Color(0x5a4c44), new Color(0x362c28), 0xc89a50);
		button(24805, 160, 19, 5, new Color(0xa02c20), new Color(0x5a1410), 0xecc67a);
		ring(24806, new Color(0x2a2220), 0x8a6a34);
		button(24807, 134, 28, 8, new Color(0xc0382a), new Color(0x6a1610), 0xecc67a);
		button(24808, 134, 28, 8, new Color(0xe0543c), new Color(0x8a2018), 0xfff4d0);
		button(24809, 134, 28, 8, new Color(0x8a6a34), new Color(0x4a3618), 0xecc67a);
		button(24810, 134, 28, 8, new Color(0xb58a44), new Color(0x6a4e24), 0xfff4d0);
		ring(24811, new Color(0x5a1410), 0xecc67a);
	}

	static int[] palette(int[][] ramps) {
		List<Integer> p = new ArrayList<>();
		for (int[] r : ramps)
			for (int i = 0; i < r[2]; i++) {
				double t = i / (r[2] - 1.0);
				p.add((int) ((r[0] >> 16 & 255) + t * ((r[1] >> 16 & 255) - (r[0] >> 16 & 255))) << 16
						| (int) ((r[0] >> 8 & 255) + t * ((r[1] >> 8 & 255) - (r[0] >> 8 & 255))) << 8 | (int) ((r[0] & 255) + t * ((r[1] & 255) - (r[0] & 255))));
			}
		return p.stream().mapToInt(Integer::intValue).toArray();
	}

	static Graphics2D g(BufferedImage im) {
		Graphics2D g = im.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
		return g;
	}

	static void save(BufferedImage im, int id) throws Exception {
		BufferedImage done = new BufferedImage(im.getWidth(), im.getHeight(), BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < im.getHeight(); y++)
			for (int x = 0; x < im.getWidth(); x++) {
				int c = im.getRGB(x, y);
				if ((c >>> 24) < 128)
					continue;
				int best = 0, bestD = Integer.MAX_VALUE;
				for (int p : palette) {
					int dr = (c >> 16 & 255) - (p >> 16 & 255), dg = (c >> 8 & 255) - (p >> 8 & 255), db = (c & 255) - (p & 255);
					int d = 3 * dr * dr + 4 * dg * dg + 2 * db * db;
					if (d < bestD) {
						bestD = d;
						best = p;
					}
				}
				done.setRGB(x, y, 0xff000000 | best);
			}
		ImageIO.write(done, "png", new File(out, id + ".png"));
	}

	static Color a(int rgb, int alpha) {
		return new Color(rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, alpha);
	}

	/** stone inside the shape, then a rim drawn inward from its edge */
	static void slab(Graphics2D g, Shape s, int top, int bottom, int lightSpeck, int darkSpeck, int seed, int rimDark, int rimMid, int rimBright) {
		g.setClip(s);
		g.setPaint(new GradientPaint(0, 0, new Color(top), 0, H, new Color(bottom)));
		g.fillRect(0, 0, W, H);
		Random r = new Random(seed);
		for (int i = 0; i < 1500; i++) {
			g.setColor(r.nextBoolean() ? a(lightSpeck, 12) : a(darkSpeck, 22));
			int d = 3 + r.nextInt(9);
			g.fillOval(r.nextInt(W), r.nextInt(H), d, d / 2 + 1);
		}
		g.setStroke(new BasicStroke(12f));
		g.setColor(new Color(rimDark));
		g.draw(s);
		g.setStroke(new BasicStroke(7f));
		g.setColor(new Color(rimMid));
		g.draw(s);
		g.setStroke(new BasicStroke(2.4f));
		g.setColor(new Color(rimBright));
		g.draw(s);
		g.setClip(null);
	}

	static void panel(Graphics2D g, Shape s, int top, int bottom, int rim) {
		g.setPaint(new GradientPaint(0, s.getBounds().y, new Color(top), 0, s.getBounds().y + s.getBounds().height, new Color(bottom)));
		g.fill(s);
		g.setStroke(new BasicStroke(1.5f));
		g.setColor(new Color(rim));
		g.draw(s);
	}

	static Shape round(int x, int y, int w, int h, int r) {
		return new RoundRectangle2D.Float(x, y, w, h, r, r);
	}

	/** the Reliquary: square shoulders and a gable over the title */
	static void vault(int id) throws Exception {
		BufferedImage im = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = g(im);
		Polygon door = new Polygon(new int[] { 0, 170, 244, 318, W - 1, W - 1, 0 }, new int[] { 12, 12, 0, 12, 12, H - 1, H - 1 }, 7);
		slab(g, door, 0x241c2c, 0x120d18, 0x4a3f55, 0x0f0b14, 1183, 0x2a1440, 0x8a6a34, 0xf4dc9a);
		plaque(g, new Polygon(new int[] { 150, 244, 338, 324, 164 }, new int[] { 18, 8, 18, 36, 36 }, 5), 0x3a1e60, 0x1a1420, 0xf4dc9a);
		panel(g, round(14, 76, W - 28, 220, 12), 0x0f0b14, 0x1a1420, 0x8a6a34);
		g.dispose();
		save(im, id);
	}

	/** the Trial picker: a gate with battlements, crimson banners either side of the title */
	static void arena(int id) throws Exception {
		BufferedImage im = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = g(im);
		Polygon gate = new Polygon();
		gate.addPoint(0, H - 1);
		for (int x = 0; x < W; x += 40) {
			gate.addPoint(x, 12);
			gate.addPoint(Math.min(x + 20, W - 1), 12);
			if (x + 20 < W - 1) {
				gate.addPoint(x + 20, 20);
				gate.addPoint(Math.min(x + 40, W - 1), 20);
			}
		}
		gate.addPoint(W - 1, H - 1);
		slab(g, gate, 0x2a2220, 0x161110, 0x5a4c44, 0x120d0c, 1182, 0x3a2a10, 0x7a5c30, 0xecc67a);
		// faint coursed stone
		g.setClip(gate);
		g.setStroke(new BasicStroke(1f));
		g.setColor(a(0x120d0c, 110));
		for (int y = 40; y < H - 8; y += 26)
			g.drawLine(10, y, W - 10, y);
		g.setClip(null);
		banner(g, 112); // clear of the balance, which ends about x 104 here at 5 digits
		banner(g, 376);
		plaque(g, new Polygon(new int[] { 136, 352, 366, 352, 136, 122 }, new int[] { 8, 8, 21, 34, 34, 21 }, 6), 0x6a1610, 0x2a0c08, 0xecc67a);
		// the list, the stage, the facts
		panel(g, round(14, 40, 174, 256, 10), 0x161110, 0x201816, 0x7a5c30);
		sand(g, 194, 62, 136, 116);
		panel(g, round(338, 62, 134, 116, 10), 0x161110, 0x201816, 0x7a5c30);
		g.dispose();
		save(im, id);
	}

	static void plaque(Graphics2D g, Polygon p, int top, int bottom, int rim) {
		g.setPaint(new GradientPaint(0, p.getBounds().y, new Color(top), 0, p.getBounds().y + p.getBounds().height, new Color(bottom)));
		g.fill(p);
		g.setStroke(new BasicStroke(1.5f));
		g.setColor(new Color(rim));
		g.draw(p);
	}

	static void banner(Graphics2D g, int x) {
		Polygon b = new Polygon(new int[] { x - 8, x + 8, x + 8, x, x - 8 }, new int[] { 8, 8, 30, 36, 30 }, 5);
		g.setPaint(new GradientPaint(0, 8, new Color(0xc0382a), 0, 36, new Color(0x5a1410)));
		g.fill(b);
		g.setStroke(new BasicStroke(1.1f));
		g.setColor(new Color(0xecc67a));
		g.draw(b);
	}

	/** the arena floor the boss stands on */
	static void sand(Graphics2D g, int x, int y, int w, int h) {
		panel(g, round(x, y, w, h, 10), 0x201816, 0x161110, 0x7a5c30);
		g.setPaint(new GradientPaint(0, y + h / 2, new Color(0x3a2a10), 0, y + h, new Color(0x7a5c30)));
		g.fill(new Ellipse2D.Float(x + 12, y + h - 42, w - 24, 34));
		g.setStroke(new BasicStroke(1.2f));
		g.setColor(new Color(0xb58a44));
		g.draw(new Ellipse2D.Float(x + 12, y + h - 42, w - 24, 34));
	}

	/** a button: cut corners, gradient face, thin rim */
	static void button(int id, int w, int h, int cut, Color top, Color bottom, int rim) throws Exception {
		BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = g(im);
		Polygon s = new Polygon(new int[] { cut, w - 1 - cut, w - 1, w - 1 - cut, cut, 0 }, new int[] { 0, 0, h / 2, h - 1, h - 1, h / 2 }, 6);
		g.setPaint(new GradientPaint(0, 0, top, 0, h, bottom));
		g.fill(s);
		g.setStroke(new BasicStroke(1.3f));
		g.setColor(new Color(rim));
		g.draw(s);
		g.dispose();
		save(im, id);
	}

	static void card(int id, Color top, Color bottom, int rim) throws Exception {
		int w = 210, h = 48;
		BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = g(im);
		Shape s = round(0, 0, w - 1, h - 1, 10);
		g.setPaint(new GradientPaint(0, 0, top, 0, h, bottom));
		g.fill(s);
		g.setStroke(new BasicStroke(1.2f));
		g.setColor(new Color(rim));
		g.draw(s);
		g.setColor(new Color(0x0f0b14));
		g.fill(new Ellipse2D.Float(6, 4, 40, 40));
		g.setColor(new Color(rim));
		g.draw(new Ellipse2D.Float(6.5f, 4.5f, 39, 39));
		g.dispose();
		save(im, id);
	}

	static void ring(int id, Color fill, int rim) throws Exception {
		BufferedImage im = new BufferedImage(38, 38, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = g(im);
		g.setColor(fill);
		g.fill(new Ellipse2D.Float(1, 1, 36, 36));
		g.setStroke(new BasicStroke(1.6f));
		g.setColor(new Color(rim));
		g.draw(new Ellipse2D.Float(1, 1, 36, 36));
		g.dispose();
		save(im, id);
	}
}

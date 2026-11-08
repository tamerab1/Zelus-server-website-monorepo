import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import javax.imageio.ImageIO;

/**
 * Paints the Ember Crucible's art (interface 1184, 2026-11-08): the obsidian window and its parts, to forge/&lt;sprite&gt;.png
 * for cache stage 21. Every sprite is mapped onto one 254-colour palette of obsidian, ember and bronze ramps, because a sprite
 * group holds at most 255 colours. Usage: MakeForgeArt &lt;assets/colosseum&gt;
 */
public final class MakeForgeArt {

	static final int W = 488, H = 308;
	/** window origin on the 512 x 334 root */
	static final int OX = 12, OY = 10;
	static final int[] PALETTE = palette();
	static File out;

	public static void main(String[] a) throws Exception {
		out = new File(a[0], "forge");
		out.mkdirs();
		window();
		button(24777, 140, 36, new Color(0xc2541a), new Color(0x6b2408));
		button(24778, 140, 36, new Color(0x4a4347), new Color(0x262225));
		button(24779, 100, 28, new Color(0xc2541a), new Color(0x6b2408));
		button(24780, 100, 28, new Color(0x4a4347), new Color(0x262225));
		gem(24781, new Color(0xffc24a), new Color(0xb5651a)); // reached
		gem(24782, new Color(0xff5a24), new Color(0x8a1f0a)); // the next tier
		ring(24783);
		confirmPanel(24784);
		closeButton(24787, new Color(0x4a2410), new Color(0x1c0f08), 0xfff0a8);
		button(24788, 140, 36, new Color(0xe8742a), new Color(0x8a3210)); // the hover states
		button(24789, 100, 28, new Color(0xe8742a), new Color(0x8a3210));
		button(24790, 100, 28, new Color(0x6a6268), new Color(0x3a3438));
		closeButton(24791, new Color(0xc2541a), new Color(0x6b2408), 0xffffff);
		chevron(24785, false);
		chevron(24786, true);
	}

	static int[] palette() {
		List<Integer> p = new ArrayList<>();
		ramp(p, 0x0d0a0c, 0x4d474d, 70);  // obsidian
		ramp(p, 0x3a0e06, 0xff7a1e, 74);  // ember
		ramp(p, 0xff7a1e, 0xfff0a8, 36);  // white-hot
		ramp(p, 0x2e2010, 0xe8bb62, 74);  // bronze
		int[] out = new int[p.size()];
		for (int i = 0; i < out.length; i++)
			out[i] = p.get(i);
		return out;
	}

	static void ramp(List<Integer> p, int from, int to, int n) {
		for (int i = 0; i < n; i++) {
			double t = i / (n - 1.0);
			int r = (int) ((from >> 16 & 255) + t * ((to >> 16 & 255) - (from >> 16 & 255)));
			int g = (int) ((from >> 8 & 255) + t * ((to >> 8 & 255) - (from >> 8 & 255)));
			int b = (int) ((from & 255) + t * ((to & 255) - (from & 255)));
			p.add(r << 16 | g << 8 | b);
		}
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
				for (int p : PALETTE) {
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

	/** the window's outline: a slab with cut corners */
	static Shape slab(int inset) {
		int c = 18 + inset / 2, x0 = inset, y0 = inset, x1 = W - 1 - inset, y1 = H - 1 - inset;
		Polygon s = new Polygon();
		s.addPoint(x0 + c, y0);
		s.addPoint(x1 - c, y0);
		s.addPoint(x1, y0 + c);
		s.addPoint(x1, y1 - c);
		s.addPoint(x1 - c, y1);
		s.addPoint(x0 + c, y1);
		s.addPoint(x0, y1 - c);
		s.addPoint(x0, y0 + c);
		return s;
	}

	static void window() throws Exception {
		BufferedImage im = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = g(im);
		Random r = new Random(1184);
		// the rock: mottled obsidian
		g.setClip(slab(0));
		g.setPaint(new GradientPaint(0, 0, new Color(0x2a2428), 0, H, new Color(0x151114)));
		g.fillRect(0, 0, W, H);
		for (int i = 0; i < 2600; i++) {
			int x = r.nextInt(W), y = r.nextInt(H), s = 2 + r.nextInt(9);
			g.setColor(r.nextBoolean() ? a(0x4d474d, 14) : a(0x0d0a0c, 26));
			g.fillOval(x, y, s, s / 2 + 1);
		}
		// a few faint lava veins in the corners only, clear of everything the window shows
		vein(g, 20, 44, 0.5);
		vein(g, W - 20, 44, Math.PI - 0.5);
		vein(g, 14, 180, 0.3);
		vein(g, W - 14, 180, Math.PI - 0.3);
		g.setClip(null);
		// the forged rim
		for (int[] b : new int[][] { { 0, 0x2e2010 }, { 2, 0x8a6a34 }, { 3, 0xe8bb62 }, { 4, 0x8a6a34 }, { 6, 0x2e2010 } }) {
			g.setStroke(new BasicStroke(b[0] == 3 ? 1.2f : 2f));
			g.setColor(new Color(b[1]));
			g.draw(slab(b[0]));
		}
		// rivets on the cut corners
		for (int[] c : new int[][] { { 16, 16 }, { W - 17, 16 }, { 16, H - 17 }, { W - 17, H - 17 } })
			rivet(g, c[0], c[1], 5);
		// the title cartouche
		cartouche(g);
		pedestal(g, 114, 110);
		pedestal(g, 374, 110);
		// the tier track: chain + five dormant gems
		int ty = 180;
		g.setStroke(new BasicStroke(3f));
		g.setColor(new Color(0x2e2010));
		g.drawLine(132, ty, 356, ty);
		g.setStroke(new BasicStroke(1f));
		g.setColor(new Color(0x8a6a34));
		g.drawLine(132, ty, 356, ty);
		for (int i = 0; i < 5; i++)
			dormantGem(g, 132 + i * 56, ty);
		// the carousel trough
		trough(g);
		g.dispose();
		save(im, 24776);
	}

	/** a short, dim crack */
	static void vein(Graphics2D g, double x, double y, double dir) {
		Random r = new Random((long) (x * 31 + y));
		Path2D p = new Path2D.Double();
		p.moveTo(x, y);
		for (int i = 0; i < 4; i++) {
			dir += (r.nextDouble() - 0.5) * 0.9;
			x += Math.cos(dir) * (4 + r.nextInt(5));
			y += Math.sin(dir) * (4 + r.nextInt(5));
			p.lineTo(x, y);
		}
		g.setStroke(new BasicStroke(1.2f));
		g.setColor(a(0xb5481a, 170));
		g.draw(p);
	}

	static void rivet(Graphics2D g, int x, int y, int r) {
		g.setColor(new Color(0x2e2010));
		g.fillOval(x - r - 1, y - r - 1, 2 * r + 2, 2 * r + 2);
		g.setPaint(new RadialGradientPaint(x - 1.5f, y - 1.5f, r + 1f, new float[] { 0f, 1f }, new Color[] { new Color(0xf4d68c), new Color(0x6e5026) }));
		g.fillOval(x - r, y - r, 2 * r, 2 * r);
	}

	static void cartouche(Graphics2D g) {
		Polygon p = new Polygon(new int[] { 136, 352, 366, 352, 136, 122 }, new int[] { 7, 7, 19, 33, 33, 19 }, 6);
		g.setPaint(new GradientPaint(0, 7, new Color(0x4a2410), 0, 33, new Color(0x1c0f08)));
		g.fill(p);
		g.setStroke(new BasicStroke(1.6f));
		g.setColor(new Color(0xe8bb62));
		g.draw(p);
	}

	static void pedestal(Graphics2D g, int cx, int cy) {
		int ro = 46;
		// the glow on the rock
		g.setPaint(new RadialGradientPaint(cx, cy, ro + 15, new float[] { 0.7f, 1f }, new Color[] { a(0xff5a14, 70), a(0xff5a14, 0) }));
		g.fill(new Ellipse2D.Float(cx - ro - 15, cy - ro - 15, 2 * ro + 30, 2 * ro + 30));
		// basalt ring
		g.setPaint(new RadialGradientPaint(cx - 10f, cy - 14f, ro * 1.5f, new float[] { 0f, 1f }, new Color[] { new Color(0x5a5158), new Color(0x1b171a) }));
		g.fill(new Ellipse2D.Float(cx - ro, cy - ro, 2 * ro, 2 * ro));
		g.setStroke(new BasicStroke(1.6f));
		g.setColor(new Color(0xe8bb62));
		g.draw(new Ellipse2D.Float(cx - ro + 1, cy - ro + 1, 2 * ro - 2, 2 * ro - 2));
		// the well the icon sits in
		int ri = ro - 10;
		g.setPaint(new RadialGradientPaint(cx, cy + 6f, ri, new float[] { 0f, 0.75f, 1f }, new Color[] { new Color(0x2e1610), new Color(0x1a0f0e), new Color(0x0d0a0c) }));
		g.fill(new Ellipse2D.Float(cx - ri, cy - ri, 2 * ri, 2 * ri));
		g.setStroke(new BasicStroke(1.4f));
		g.setColor(new Color(0xff7a1e));
		g.draw(new Ellipse2D.Float(cx - ri, cy - ri, 2 * ri, 2 * ri));
	}

	static void dormantGem(Graphics2D g, int cx, int cy) {
		Polygon p = diamond(cx, cy, 11);
		g.setColor(new Color(0x0d0a0c));
		g.fill(diamond(cx, cy, 13));
		g.setPaint(new GradientPaint(0, cy - 11, new Color(0x4d474d), 0, cy + 11, new Color(0x1b171a)));
		g.fill(p);
		g.setStroke(new BasicStroke(1.2f));
		g.setColor(new Color(0x8a6a34));
		g.draw(p);
	}

	static Polygon diamond(int cx, int cy, int r) {
		return new Polygon(new int[] { cx, cx + r, cx, cx - r }, new int[] { cy - r, cy, cy + r, cy }, 4);
	}

	static void trough(Graphics2D g) {
		Shape s = new RoundRectangle2D.Float(24, 206, W - 48, 84, 18, 18);
		g.setPaint(new GradientPaint(0, 206, new Color(0x151114), 0, 290, new Color(0x1f191c)));
		g.fill(s);
		g.setStroke(new BasicStroke(1.5f));
		g.setColor(new Color(0x8a6a34));
		g.draw(s);
		// six sockets (x as on the root: 99 + i * 54 - OX)
		for (int i = 0; i < 6; i++) {
			int x = 99 + i * 54 - OX, y = 228 - OY;
			g.setPaint(new GradientPaint(0, y, new Color(0x0d0a0c), 0, y + 44, new Color(0x332c30)));
			g.fill(new Ellipse2D.Float(x, y, 44, 44));
			g.setStroke(new BasicStroke(1.6f));
			g.setColor(new Color(0xb58a44));
			g.draw(new Ellipse2D.Float(x + 0.5f, y + 0.5f, 43, 43));
			g.setStroke(new BasicStroke(1f));
			g.setColor(new Color(0x4a2410));
			g.draw(new Ellipse2D.Float(x + 3, y + 3, 38, 38));
		}
	}

	static void button(int id, int w, int h, Color top, Color bottom) throws Exception {
		BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = g(im);
		Polygon s = new Polygon(new int[] { 9, w - 10, w - 1, w - 10, 9, 0 }, new int[] { 0, 0, h / 2, h - 1, h - 1, h / 2 }, 6);
		g.setPaint(new GradientPaint(0, 0, top, 0, h, bottom));
		g.fill(s);
		g.setStroke(new BasicStroke(1.6f));
		g.setColor(new Color(0xe8bb62));
		g.draw(s);
		g.setStroke(new BasicStroke(1f));
		g.setColor(a(0xffe9a8, 90));
		g.drawLine(11, 2, w - 12, 2);
		g.dispose();
		save(im, id);
	}

	static void gem(int id, Color light, Color dark) throws Exception {
		BufferedImage im = new BufferedImage(26, 26, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = g(im);
		Polygon p = diamond(13, 13, 11);
		g.setPaint(new GradientPaint(0, 2, light, 0, 24, dark));
		g.fill(p);
		g.setStroke(new BasicStroke(1.2f));
		g.setColor(new Color(0xfff0a8));
		g.draw(p);
		g.setColor(a(0xffffff, 120));
		g.drawLine(9, 11, 13, 6);
		g.dispose();
		save(im, id);
	}

	/** the selected socket's burning ring */
	static void ring(int id) throws Exception {
		BufferedImage im = new BufferedImage(50, 50, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = g(im);
		g.setStroke(new BasicStroke(3f));
		g.setColor(new Color(0xff7a1e));
		g.draw(new Ellipse2D.Float(3.5f, 3.5f, 43, 43));
		g.setStroke(new BasicStroke(1f));
		g.setColor(new Color(0xfff0a8));
		g.draw(new Ellipse2D.Float(3.5f, 3.5f, 43, 43));
		g.dispose();
		save(im, id);
	}

	static void confirmPanel(int id) throws Exception {
		int w = 300, h = 160;
		BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = g(im);
		Polygon s = new Polygon(new int[] { 14, w - 15, w - 1, w - 1, w - 15, 14, 0, 0 }, new int[] { 0, 0, 14, h - 15, h - 1, h - 1, h - 15, 14 }, 8);
		g.setPaint(new GradientPaint(0, 0, new Color(0x332126), 0, h, new Color(0x120d0f)));
		g.fill(s);
		g.setStroke(new BasicStroke(2.4f));
		g.setColor(new Color(0xff7a1e));
		g.draw(s);
		g.setStroke(new BasicStroke(1f));
		g.setColor(new Color(0xe8bb62));
		Polygon in = new Polygon(new int[] { 16, w - 17, w - 5, w - 5, w - 17, 16, 4, 4 }, new int[] { 4, 4, 16, h - 17, h - 5, h - 5, h - 17, 16 }, 8);
		g.draw(in);
		g.setColor(new Color(0x8a6a34));
		g.drawLine(40, 34, w - 41, 34);
		g.dispose();
		save(im, id);
	}

	static void closeButton(int id, Color top, Color bottom, int cross) throws Exception {
		BufferedImage im = new BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = g(im);
		g.setPaint(new GradientPaint(0, 0, top, 0, 20, bottom));
		g.fill(new Ellipse2D.Float(1, 1, 18, 18));
		g.setStroke(new BasicStroke(1.4f));
		g.setColor(new Color(0xe8bb62));
		g.draw(new Ellipse2D.Float(1, 1, 18, 18));
		g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.setColor(new Color(cross));
		g.drawLine(7, 7, 13, 13);
		g.drawLine(13, 7, 7, 13);
		g.dispose();
		save(im, id);
	}

	static void chevron(int id, boolean right) throws Exception {
		BufferedImage im = new BufferedImage(26, 36, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = g(im);
		Path2D p = new Path2D.Double();
		int a = right ? 7 : 19, b = right ? 19 : 7;
		p.moveTo(a, 5);
		p.lineTo(b, 18);
		p.lineTo(a, 31);
		g.setStroke(new BasicStroke(7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.setColor(new Color(0x2e2010));
		g.draw(p);
		g.setStroke(new BasicStroke(3.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.setColor(new Color(0xff9a3a));
		g.draw(p);
		g.dispose();
		save(im, id);
	}
}

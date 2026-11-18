import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.Random;

/**
 * The Karn interface art kit (2026-11-12, round 2 of the Karn UI): the shared painting primitives every window's art is
 * built from -- a cleaner, deeper crimson than round 1's brown-burgundy. Everything is painted in full colour; quantize.py
 * brings each image to a 254-colour sprite (on/off transparency, so soft edges are painted onto their own backgrounds).
 * Icons are drawn as vector paths at 4x and box-filtered down, so they stay crisp at 12-24 px.
 */
public final class KarnArt {

	private KarnArt() {
	}

	// ---- palette: obsidian + clean crimson + forged gold ----
	public static final Color VOID = new Color(0x0c0809), BASE = new Color(0x150f12), BASE_HI = new Color(0x1c1418),
			PANEL = new Color(0x1d1519), PANEL_HI = new Color(0x271c21), WELL = new Color(0x0f0a0c),
			LINE = new Color(0x3b2a30), LINE_HI = new Color(0x5a434b), LINE_DK = new Color(0x070405),
			CRIMSON = new Color(0xc3242c), CRIMSON_HI = new Color(0xe5424a), CRIMSON_DK = new Color(0x6e1015), CRIMSON_DEEP = new Color(0x2e090c),
			GOLD = new Color(0xd9a94f), GOLD_HI = new Color(0xf5d58e), GOLD_DK = new Color(0x7d5d24), GOLD_DEEP = new Color(0x3d2c10),
			TEXT = new Color(0xeee3d2), MUTED = new Color(0xa79a8e);

	public static BufferedImage image(int w, int h) {
		return new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
	}

	public static Graphics2D g(BufferedImage im) {
		Graphics2D g = im.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
		g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
		return g;
	}

	public static Color mix(Color a, Color b, double t) {
		t = Math.max(0, Math.min(1, t));
		return new Color((int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * t), (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
				(int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
	}

	public static Color alpha(Color c, int a) {
		return new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
	}

	public static void vgrad(Graphics2D g, double x, double y, double w, double h, Color top, Color bottom) {
		g.setPaint(new GradientPaint((float) x, (float) y, top, (float) x, (float) (y + h), bottom));
		g.fill(new Rectangle2D.Double(x, y, w, h));
	}

	/** Fine film grain (luminance only) in a box, so large flat areas don't band after quantizing. */
	public static void grain(BufferedImage im, int x0, int y0, int w, int h, int amount, long seed) {
		Random r = new Random(seed);
		for (int y = Math.max(0, y0); y < Math.min(im.getHeight(), y0 + h); y++)
			for (int x = Math.max(0, x0); x < Math.min(im.getWidth(), x0 + w); x++) {
				int p = im.getRGB(x, y);
				if ((p >>> 24) == 0)
					continue;
				int d = (int) Math.round(r.nextGaussian() * amount / 2.0);
				int rr = clamp((p >> 16 & 0xff) + d), gg = clamp((p >> 8 & 0xff) + d), bb = clamp((p & 0xff) + d);
				im.setRGB(x, y, p & 0xff000000 | rr << 16 | gg << 8 | bb);
			}
	}

	static int clamp(int v) {
		return Math.max(0, Math.min(255, v));
	}

	/** A soft radial light (e.g. a crimson glow from the header), additive-looking but painted as alpha. */
	public static void glow(Graphics2D g, double cx, double cy, double rx, double ry, Color c, int alpha) {
		// an elliptical falloff: a circular gradient in a space scaled to the ellipse
		AffineTransform t = AffineTransform.getTranslateInstance(cx, cy);
		t.scale(rx / Math.max(rx, ry), ry / Math.max(rx, ry));
		float r = (float) Math.max(rx, ry);
		RadialGradientPaint p = new RadialGradientPaint(new Point2D.Double(0, 0), r, new Point2D.Double(0, 0), new float[] { 0f, 1f },
				new Color[] { alpha(c, alpha), alpha(c, 0) }, MultipleGradientPaint.CycleMethod.NO_CYCLE,
				MultipleGradientPaint.ColorSpaceType.SRGB, t);
		g.setPaint(p);
		g.fill(new Rectangle2D.Double(cx - rx, cy - ry, rx * 2, ry * 2));
	}

	/** A 1px line that fades out at both ends. */
	public static void hairline(Graphics2D g, double x0, double x1, double y, Color c, int alpha) {
		double w = x1 - x0;
		g.setPaint(new LinearGradientPaint((float) x0, 0, (float) x1, 0, new float[] { 0f, 0.2f, 0.8f, 1f },
				new Color[] { alpha(c, 0), alpha(c, alpha), alpha(c, alpha), alpha(c, 0) }));
		g.fill(new Rectangle2D.Double(x0, y, w, 1));
	}

	public static void vhairline(Graphics2D g, double x, double y0, double y1, Color c, int alpha) {
		g.setPaint(new LinearGradientPaint(0, (float) y0, 0, (float) y1, new float[] { 0f, 0.2f, 0.8f, 1f },
				new Color[] { alpha(c, 0), alpha(c, alpha), alpha(c, alpha), alpha(c, 0) }));
		g.fill(new Rectangle2D.Double(x, y0, 1, y1 - y0));
	}

	/** A diamond gem: dark rim, body gradient, a small specular highlight. */
	public static void gem(Graphics2D g, double cx, double cy, double r, Color body) {
		Path2D d = diamond(cx, cy, r);
		g.setColor(LINE_DK);
		g.fill(diamond(cx, cy, r + 1.2));
		g.setPaint(new GradientPaint((float) cx, (float) (cy - r), mix(body, Color.WHITE, 0.35), (float) cx, (float) (cy + r), mix(body, Color.BLACK, 0.45)));
		g.fill(d);
		g.setColor(GOLD);
		g.setStroke(new BasicStroke((float) Math.max(0.8, r / 5)));
		g.draw(d);
		g.setColor(alpha(Color.WHITE, 170));
		g.fill(new Ellipse2D.Double(cx - r * 0.38, cy - r * 0.62, r * 0.42, r * 0.34));
	}

	static Path2D diamond(double cx, double cy, double r) {
		Path2D p = new Path2D.Double();
		p.moveTo(cx, cy - r);
		p.lineTo(cx + r, cy);
		p.lineTo(cx, cy + r);
		p.lineTo(cx - r, cy);
		p.closePath();
		return p;
	}

	/**
	 * A recessed well: darker fill, an inner shadow along the top and left, a faint lit edge along the bottom and right,
	 * and a 1px border. The standard container for lists and slots.
	 */
	public static void well(Graphics2D g, int x, int y, int w, int h, Color fill, Color border) {
		g.setColor(border);
		g.fillRect(x, y, w, h);
		g.setColor(fill);
		g.fillRect(x + 1, y + 1, w - 2, h - 2);
		g.setColor(alpha(Color.BLACK, 120));
		g.fillRect(x + 1, y + 1, w - 2, 2);
		g.fillRect(x + 1, y + 1, 2, h - 2);
		g.setColor(alpha(Color.WHITE, 14));
		g.fillRect(x + 1, y + h - 2, w - 2, 1);
		g.fillRect(x + w - 2, y + 1, 1, h - 2);
	}

	/** A raised plate: subtle top-lit gradient, a 1px highlight along the top, border. */
	public static void plate(Graphics2D g, int x, int y, int w, int h, Color top, Color bottom, Color border) {
		g.setColor(border);
		g.fillRect(x, y, w, h);
		vgrad(g, x + 1, y + 1, w - 2, h - 2, top, bottom);
		g.setColor(alpha(Color.WHITE, 22));
		g.fillRect(x + 1, y + 1, w - 2, 1);
		g.setColor(alpha(Color.BLACK, 70));
		g.fillRect(x + 1, y + h - 2, w - 2, 1);
	}

	/** A small chamfered corner cut on a filled rectangle (the classic "clipped corner" plate). */
	public static Path2D chamfer(double x, double y, double w, double h, double c) {
		Path2D p = new Path2D.Double();
		p.moveTo(x + c, y);
		p.lineTo(x + w - c, y);
		p.lineTo(x + w, y + c);
		p.lineTo(x + w, y + h - c);
		p.lineTo(x + w - c, y + h);
		p.lineTo(x + c, y + h);
		p.lineTo(x, y + h - c);
		p.lineTo(x, y + c);
		p.closePath();
		return p;
	}

	/** A painted heading in a book serif with a gold gradient and a hard drop shadow, centred on cx. */
	public static void heading(Graphics2D g, String text, double cx, double baseline, float size, Color top, Color bottom) {
		Font f = new Font("Book Antiqua", Font.BOLD, 10).deriveFont(size);
		g.setFont(f);
		FontMetrics fm = g.getFontMetrics();
		double x = cx - fm.stringWidth(text) / 2.0;
		Shape s = f.createGlyphVector(g.getFontRenderContext(), text).getOutline((float) x, (float) baseline);
		g.setColor(new Color(0, 0, 0, 200));
		g.fill(AffineTransform.getTranslateInstance(1.2, 1.4).createTransformedShape(s));
		g.setPaint(new GradientPaint(0, (float) (baseline - size * 0.8), top, 0, (float) baseline, bottom));
		g.fill(s);
	}

	// ---- buttons ----

	public enum Style { CRIMSON, DARK, GOLD }

	/** A button face: chamfered plate in the style, lit top edge, crisp border; hover is brighter. */
	public static BufferedImage button(int w, int h, Style st, boolean hover) {
		BufferedImage im = image(w, h);
		Graphics2D g = g(im);
		Color top, bottom, border, edge;
		switch (st) {
		case CRIMSON -> {
			top = hover ? new Color(0xe0414a) : new Color(0xbf2a32);
			bottom = hover ? new Color(0x8e161d) : new Color(0x6c1015);
			border = hover ? GOLD_HI : GOLD;
			edge = new Color(255, 200, 190, hover ? 110 : 80);
		}
		case GOLD -> {
			top = hover ? new Color(0xf6d58a) : new Color(0xe3b45c);
			bottom = hover ? new Color(0xb38233) : new Color(0x9a6e28);
			border = hover ? new Color(0xfff0c8) : new Color(0x5a3f12);
			edge = new Color(255, 255, 230, 120);
		}
		default -> {
			top = hover ? new Color(0x33242a) : new Color(0x261b20);
			bottom = hover ? new Color(0x1e1519) : new Color(0x171013);
			border = hover ? GOLD_DK : LINE_HI;
			edge = new Color(255, 255, 255, hover ? 30 : 18);
		}
		}
		double c = Math.min(4, h / 5.0);
		g.setColor(LINE_DK);
		g.fill(chamfer(0, 0, w, h, c + 0.5));
		g.setPaint(new GradientPaint(0, 1, top, 0, h - 1, bottom));
		g.fill(chamfer(1, 1, w - 2, h - 2, c));
		g.setColor(border);
		g.setStroke(new BasicStroke(1f));
		g.draw(chamfer(1.5, 1.5, w - 3, h - 3, c - 0.5));
		g.setColor(edge);
		g.fill(new Rectangle2D.Double(c + 2, 2.5, w - 2 * c - 4, 1));
		g.dispose();
		return im;
	}

	/** The round close button: crimson enamel in a gold ring with a cream X. */
	public static BufferedImage close(int size, boolean hover) {
		BufferedImage big = image(size * 4, size * 4);
		Graphics2D g = g(big);
		double s = size * 4;
		g.setColor(LINE_DK);
		g.fill(new Ellipse2D.Double(0, 0, s, s));
		g.setPaint(new GradientPaint(0, 0, GOLD_HI, 0, (float) s, GOLD_DK));
		g.fill(new Ellipse2D.Double(3, 3, s - 6, s - 6));
		g.setPaint(new RadialGradientPaint(new Point2D.Double(s / 2, s * 0.38), (float) (s * 0.45), new float[] { 0, 1 },
				new Color[] { hover ? new Color(0xf0565c) : new Color(0xcc2d35), hover ? new Color(0x8a141b) : new Color(0x5e0c11) }));
		g.fill(new Ellipse2D.Double(8, 8, s - 16, s - 16));
		g.setStroke(new BasicStroke((float) (s * 0.11), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.setColor(new Color(0, 0, 0, 120));
		double a = s * 0.33, b = s * 0.67;
		g.draw(new Line2D.Double(a, a + 2, b, b + 2));
		g.draw(new Line2D.Double(b, a + 2, a, b + 2));
		g.setColor(hover ? Color.WHITE : TEXT);
		g.draw(new Line2D.Double(a, a, b, b));
		g.draw(new Line2D.Double(b, a, a, b));
		g.dispose();
		return down(big, 4);
	}

	/**
	 * The window body every round 2 window starts from (487 x 306 in the frame's place at 14,9): a crisp dark-metal rim
	 * with a gold hairline inside it, obsidian body with grain, a header band of the given height with a soft glow of the
	 * accent colour at its centre and a gold divider with a centre gem under it, and corner gems in the accent colour.
	 */
	public static BufferedImage window(int w, int h, int header, Color accent, long seed) {
		BufferedImage im = image(w, h);
		Graphics2D g = g(im);
		// rim: outer black, 3px dark metal, gold hairline
		g.setColor(LINE_DK);
		g.fill(chamfer(0, 0, w, h, 7));
		g.setPaint(new GradientPaint(0, 0, new Color(0x3a2a31), 0, h, new Color(0x1a1215)));
		g.fill(chamfer(1, 1, w - 2, h - 2, 6.5));
		g.setColor(GOLD_DK);
		g.fill(chamfer(4, 4, w - 8, h - 8, 4.5));
		g.setPaint(new GradientPaint(0, 4, GOLD, 0, 40, GOLD_DK));
		g.fill(new Rectangle2D.Double(9, 4, w - 18, 1));
		// body
		g.setPaint(new GradientPaint(0, 5, new Color(0x1a1216), 0, h - 5, new Color(0x110c0e)));
		g.fill(chamfer(5, 5, w - 10, h - 10, 4));
		// header band
		g.setPaint(new GradientPaint(0, 5, new Color(0x241a1f), 0, header, new Color(0x170f13)));
		g.fill(new Rectangle2D.Double(5, 5, w - 10, header - 5));
		glow(g, w / 2.0, 5, w * 0.42, header * 1.6, accent, 70);
		g.setColor(LINE_DK);
		g.fillRect(5, header, w - 10, 1);
		hairline(g, 10, w - 10, header + 1, GOLD, 200);
		gem(g, w / 2.0, header + 1.5, 3.4, accent);
		g.dispose();
		grain(im, 5, 5, w - 10, h - 10, 5, seed);
		g = g(im);
		for (double[] c : new double[][] { { 6, 6 }, { w - 7, 6 }, { 6, h - 7 }, { w - 7, h - 7 } })
			gem(g, c[0], c[1], 3.6, accent);
		g.dispose();
		return im;
	}

	/** Box-filter downscale by an integer factor (alpha-weighted), for crisp anti-aliased small art. */
	public static BufferedImage down(BufferedImage big, int f) {
		int w = big.getWidth() / f, h = big.getHeight() / f;
		BufferedImage im = image(w, h);
		for (int y = 0; y < h; y++)
			for (int x = 0; x < w; x++) {
				double a = 0, r = 0, gg = 0, b = 0;
				for (int yy = 0; yy < f; yy++)
					for (int xx = 0; xx < f; xx++) {
						int p = big.getRGB(x * f + xx, y * f + yy);
						double pa = (p >>> 24) / 255.0;
						a += pa;
						r += (p >> 16 & 0xff) * pa;
						gg += (p >> 8 & 0xff) * pa;
						b += (p & 0xff) * pa;
					}
				if (a <= 0)
					continue;
				int n = f * f;
				im.setRGB(x, y, (int) Math.round(a / n * 255) << 24 | (int) Math.round(r / a) << 16 | (int) Math.round(gg / a) << 8 | (int) Math.round(b / a));
			}
		return im;
	}

	/** Draws a small image onto a background image (alpha blend), e.g. an icon onto a button face. */
	public static void stamp(BufferedImage dst, BufferedImage src, int x, int y) {
		Graphics2D g = dst.createGraphics();
		g.drawImage(src, x, y, null);
		g.dispose();
	}

	/** An icon with a 1px dark outline/drop shadow so it reads on any panel. */
	public static BufferedImage icon(String name, int size, Color top, Color bottom) {
		int f = 4;
		BufferedImage big = image(size * f, size * f);
		Graphics2D g = g(big);
		g.scale(size * f / 24.0, size * f / 24.0); // icons are authored on a 24 x 24 grid
		Shape s = KarnIcons.shape(name);
		g.setColor(new Color(0, 0, 0, 230));
		g.fill(AffineTransform.getTranslateInstance(0.9, 1.1).createTransformedShape(s));
		g.setPaint(new GradientPaint(0, 2, top, 0, 22, bottom));
		g.fill(s);
		Shape detail = KarnIcons.detail(name);
		if (detail != null) {
			g.setColor(new Color(0, 0, 0, 150));
			g.fill(detail);
		}
		g.dispose();
		return down(big, f);
	}
}

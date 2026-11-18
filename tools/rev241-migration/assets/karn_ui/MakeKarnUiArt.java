import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Random;

/**
 * The Karn window art (2026-11-11), painted in full colour (Java2D); quantize.py brings each to a 254-colour sprite.
 * The style is the login screen's: dark glass, a gold double frame with crimson inner lines and red gems at the corners,
 * a crimson glow from the top, the K emblem in the header.
 * <ul>
 * <li>24816 the Teleports window (1152) and 24817 the Soul Tree (1176): the window background, 487 x 306, at 14,9 in the
 * frame (the stone sprite 4517 they replace). The Teleports art also carries its three panels; the Soul Tree's panels are
 * components (its two pages split the same area differently), so its art only lays a faint plate under them.</li>
 * <li>24818 / 24819 the close button (16 x 16) and its hover.</li>
 * <li>24820-24833 the buttons at each size the two windows use, normal + hover (crimson, gold rim).</li>
 * </ul>
 * Usage: MakeKarnUiArt &lt;assets/karn_ui&gt;
 */
public class MakeKarnUiArt {
	static final Color GOLD = new Color(0xe3b15a), GOLD_DARK = new Color(0x7a5418), GOLD_LIGHT = new Color(0xffe6a8),
			CRIMSON = new Color(0x8e1016), CRIMSON_GLOW = new Color(0xd8261c), CRIMSON_DEEP = new Color(0x3a0a0d);
	static final int W = 487, H = 306;
	/** the Teleports panels, relative to the art (window 24,62 / 144,62 / 338,62 minus the art's 14,9) */
	/** round 2 layout: the list (root 24,105 244x197) and the preview card (274,105 214x197), under the category tabs */
	static final int[][] TP_PANELS = { { 10, 96, 244, 197 }, { 260, 96, 214, 197 } };
	/** the Soul Tree's page area and dock (24,84 464x166 / 24,252 464x50) */
	static final int[][] ST_PLATES = { { 10, 73, 464, 166 }, { 10, 243, 464, 50 } };
	/** 24842 the starter (1150): account list (root 24,56 196x214), difficulty bar (226,56 262x40), summary (226,100 262x202) */
	static final int[][] START_PANELS = { { 10, 47, 196, 214 }, { 212, 47, 262, 40 }, { 212, 91, 262, 202 } };
	/** button sizes: id, w, h (hover = id + 1) */
	static final int[][] BUTTONS = { { 24820, 57, 17 }, { 24840, 198, 20 }, { 24844, 60, 30 }, { 24846, 196, 24 }, { 24848, 134, 19 },
			{ 24850, 112, 24 }, { 24822, 112, 19 }, { 24824, 112, 20 }, { 24826, 152, 18 }, { 24828, 204, 16 },
			{ 24830, 112, 22 }, { 24832, 104, 24 } };

	public static void main(String[] a) throws Exception {
		File dir = new File(a[0]), png = new File(dir, "png");
		png.mkdirs();
		BufferedImage emblem = ImageIO.read(new File(dir, "k_emblem_src.png"));
		ImageIO.write(window(emblem, TP_PANELS, true), "png", new File(png, "24816.png"));
		ImageIO.write(window(emblem, ST_PLATES, false), "png", new File(png, "24817.png"));
		ImageIO.write(window(emblem, START_PANELS, true), "png", new File(png, "24842.png"));
		ImageIO.write(window(emblem, new int[0][], false), "png", new File(png, "24843.png")); // the soul weapon pick (1175): cards are components
		ImageIO.write(close(false), "png", new File(png, "24818.png"));
		ImageIO.write(close(true), "png", new File(png, "24819.png"));
		scrollbar(png);
		for (int[] b : BUTTONS) {
			ImageIO.write(button(b[1], b[2], false), "png", new File(png, b[0] + ".png"));
			ImageIO.write(button(b[1], b[2], true), "png", new File(png, (b[0] + 1) + ".png"));
		}
		System.out.println("painted " + (4 + BUTTONS.length * 2) + " images to " + png);
	}

	static Graphics2D g(BufferedImage im) {
		Graphics2D g = im.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
		g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
		return g;
	}

	static BufferedImage window(BufferedImage emblem, int[][] panels, boolean solidPanels) {
		BufferedImage im = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = g(im);
		Shape outer = new RoundRectangle2D.Float(0.5f, 0.5f, W - 1, H - 1, 18, 18);
		g.setClip(outer);
		// base: deep red-black, darker towards the bottom
		g.setPaint(new GradientPaint(0, 0, new Color(0x4e2c30), 0, H, new Color(0x2a171a)));
		g.fillRect(0, 0, W, H);
		// a crimson glow from the top centre, and a fainter one low on each side
		glow(g, W / 2f, -30, 300, new Color(216, 38, 28, 95));
		glow(g, 0, H, 220, new Color(142, 16, 22, 45));
		glow(g, W, H, 220, new Color(142, 16, 22, 45));
		// a fine grain so the dark doesn't band once it is down to 254 colours
		Random r = new Random(1185);
		for (int i = 0; i < 9000; i++) {
			int x = r.nextInt(W), y = r.nextInt(H), v = r.nextInt(18);
			g.setColor(new Color(255, 230, 210, v));
			g.fillRect(x, y, 1, 1);
		}
		// the header band
		g.setPaint(new GradientPaint(0, 0, new Color(96, 40, 44, 200), 0, 42, new Color(60, 24, 28, 0)));
		g.fillRect(0, 0, W, 40);
		divider(g, 18, W - 18, 42); // root y 51: the title (12-32) and the subtitle (33-46) sit above it
		// the panels
		for (int[] p : panels)
			panel(g, p[0], p[1], p[2], p[3], solidPanels);
		g.setClip(null);
		// the frame: gold outer edge, a dark gap, a crimson inner line
		g.setStroke(new BasicStroke(2.4f));
		g.setPaint(new GradientPaint(0, 0, GOLD, 0, H, GOLD_DARK));
		g.draw(new RoundRectangle2D.Float(1.2f, 1.2f, W - 2.4f, H - 2.4f, 18, 18));
		g.setStroke(new BasicStroke(1f));
		g.setColor(new Color(0, 0, 0, 160));
		g.draw(new RoundRectangle2D.Float(3.5f, 3.5f, W - 7, H - 7, 15, 15));
		g.setColor(CRIMSON);
		g.draw(new RoundRectangle2D.Float(5.5f, 5.5f, W - 11, H - 11, 13, 13));
		// gems at the corners and the middle of the top and bottom edges
		for (float[] p : new float[][] { { 8, 8 }, { W - 8, 8 }, { 8, H - 8 }, { W - 8, H - 8 } })
			gem(g, p[0], p[1], 6);
		gem(g, W / 2f, H - 3, 5);
		// the K emblem in the header, left
		g.drawImage(emblem, 12, 3, 34, 34, null);
		g.dispose();
		return im;
	}

	static void glow(Graphics2D g, float x, float y, float radius, Color c) {
		g.setPaint(new RadialGradientPaint(new Point2D.Float(x, y), radius, new float[] { 0f, 1f },
				new Color[] { c, new Color(c.getRed(), c.getGreen(), c.getBlue(), 0) }));
		g.fill(new Ellipse2D.Float(x - radius, y - radius, radius * 2, radius * 2));
	}

	static void divider(Graphics2D g, int x0, int x1, int y) {
		float mid = (x0 + x1) / 2f;
		g.setPaint(new LinearGradientPaint(x0, y, x1, y, new float[] { 0f, 0.5f, 1f },
				new Color[] { new Color(0, 0, 0, 0), GOLD, new Color(0, 0, 0, 0) }));
		g.fillRect(x0, y, x1 - x0, 1);
		g.setPaint(new LinearGradientPaint(x0, y, x1, y, new float[] { 0f, 0.5f, 1f },
				new Color[] { new Color(0, 0, 0, 0), new Color(142, 16, 22, 200), new Color(0, 0, 0, 0) }));
		g.fillRect(x0, y + 1, x1 - x0, 1);
		gem(g, mid, y + 0.5f, 3.5f);
	}

	/** A panel: dark glass (solid) or a faint plate, bronze edge, crimson inner line, a soft light along the top. */
	static void panel(Graphics2D g, int x, int y, int w, int h, boolean solid) {
		RoundRectangle2D r = new RoundRectangle2D.Float(x + 0.5f, y + 0.5f, w - 1, h - 1, 10, 10);
		g.setPaint(new GradientPaint(x, y, new Color(66, 38, 42, solid ? 240 : 150), x, y + h, new Color(46, 26, 29, solid ? 245 : 165)));
		g.fill(r);
		g.setPaint(new GradientPaint(x, y, new Color(255, 214, 180, 40), x, y + 22, new Color(255, 214, 180, 0)));
		g.fill(new RoundRectangle2D.Float(x + 2, y + 2, w - 4, 18, 8, 8));
		g.setStroke(new BasicStroke(1f));
		g.setPaint(new GradientPaint(x, y, new Color(0xc9a24a), x, y + h, new Color(0x7a5a2a)));
		g.draw(r);
		g.setColor(new Color(130, 28, 32, 170));
		g.draw(new RoundRectangle2D.Float(x + 2.5f, y + 2.5f, w - 5, h - 5, 7, 7));
	}

	static void gem(Graphics2D g, float x, float y, float r) {
		Path2D d = new Path2D.Float();
		d.moveTo(x, y - r);
		d.lineTo(x + r, y);
		d.lineTo(x, y + r);
		d.lineTo(x - r, y);
		d.closePath();
		g.setPaint(new GradientPaint(x, y - r, CRIMSON_GLOW, x, y + r, CRIMSON_DEEP));
		g.fill(d);
		g.setStroke(new BasicStroke(1.2f));
		g.setColor(GOLD);
		g.draw(d);
		g.setColor(new Color(255, 220, 200, 170));
		g.fill(new Ellipse2D.Float(x - r * 0.35f, y - r * 0.55f, r * 0.45f, r * 0.35f));
	}

	/** 24834 up, 24835 down (16x16), 24836 / 24837 / 24838 dragger top / sides / bottom and 24839 track (16x5, tiled). */
	static void scrollbar(File png) throws Exception {
		ImageIO.write(arrow(true), "png", new File(png, "24834.png"));
		ImageIO.write(arrow(false), "png", new File(png, "24835.png"));
		ImageIO.write(dragger(0), "png", new File(png, "24836.png"));
		ImageIO.write(dragger(1), "png", new File(png, "24837.png"));
		ImageIO.write(dragger(2), "png", new File(png, "24838.png"));
		BufferedImage track = new BufferedImage(16, 5, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = g(track);
		g.setColor(new Color(0x24141a));
		g.fillRect(3, 0, 10, 5);
		g.setColor(new Color(0x5a3e1a));
		g.fillRect(3, 0, 1, 5);
		g.fillRect(12, 0, 1, 5);
		g.setColor(new Color(0x3a2228));
		g.fillRect(7, 0, 2, 5);
		g.dispose();
		ImageIO.write(track, "png", new File(png, "24839.png"));
	}

	static BufferedImage arrow(boolean up) {
		BufferedImage im = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = g(im);
		RoundRectangle2D box = new RoundRectangle2D.Float(1.5f, 1.5f, 13, 13, 5, 5);
		g.setPaint(new GradientPaint(0, 1, new Color(0x9a2024), 0, 15, new Color(0x4e0c10)));
		g.fill(box);
		g.setStroke(new BasicStroke(1f));
		g.setPaint(new GradientPaint(0, 1, GOLD_LIGHT, 0, 15, GOLD_DARK));
		g.draw(box);
		Path2D chevron = new Path2D.Float();
		float c = up ? 6.5f : 9.5f, t = up ? 3.2f : -3.2f;
		chevron.moveTo(4.5f, c + t);
		chevron.lineTo(8f, c);
		chevron.lineTo(11.5f, c + t);
		g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.setColor(GOLD_LIGHT);
		g.draw(chevron);
		g.dispose();
		return im;
	}

	/** 0 = top cap, 1 = the body (tiled), 2 = bottom cap: crimson with gold rims. */
	static BufferedImage dragger(int part) {
		BufferedImage im = new BufferedImage(16, 5, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = g(im);
		g.setPaint(new GradientPaint(2, 0, new Color(0xc0302c), 14, 0, new Color(0x6e1014)));
		if (part == 0) {
			g.fill(new RoundRectangle2D.Float(2, 0.5f, 12, 9, 6, 6));
			g.setColor(GOLD);
			g.draw(new RoundRectangle2D.Float(2.5f, 0.5f, 11, 9, 6, 6));
		} else if (part == 2) {
			g.fill(new RoundRectangle2D.Float(2, -4.5f, 12, 9, 6, 6));
			g.setColor(GOLD_DARK);
			g.draw(new RoundRectangle2D.Float(2.5f, -4.5f, 11, 9, 6, 6));
		} else {
			g.fillRect(2, 0, 12, 5);
			g.setColor(GOLD);
			g.fillRect(2, 0, 1, 5);
			g.setColor(GOLD_DARK);
			g.fillRect(13, 0, 1, 5);
			g.setColor(new Color(255, 210, 190, 60));
			g.fillRect(5, 0, 1, 5);
		}
		g.dispose();
		return im;
	}

	static BufferedImage close(boolean hover) {
		BufferedImage im = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = g(im);
		g.setPaint(new GradientPaint(0, 0, hover ? CRIMSON_GLOW : new Color(0x5a1014), 0, 16, hover ? CRIMSON : CRIMSON_DEEP));
		g.fill(new Ellipse2D.Float(0.8f, 0.8f, 14.4f, 14.4f));
		g.setStroke(new BasicStroke(1.3f));
		g.setColor(hover ? GOLD_LIGHT : GOLD);
		g.draw(new Ellipse2D.Float(0.8f, 0.8f, 14.4f, 14.4f));
		g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.setColor(hover ? Color.WHITE : new Color(0xf0e6d8));
		g.draw(new Line2D.Float(5.2f, 5.2f, 10.8f, 10.8f));
		g.draw(new Line2D.Float(10.8f, 5.2f, 5.2f, 10.8f));
		g.dispose();
		return im;
	}

	/** A button: crimson body, gold rim, light along the top; hover is a lit version. The label is the window's text. */
	static BufferedImage button(int w, int h, boolean hover) {
		BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = g(im);
		float arc = Math.min(8, h - 6);
		RoundRectangle2D body = new RoundRectangle2D.Float(0.5f, 0.5f, w - 1, h - 1, arc, arc);
		g.setPaint(new GradientPaint(0, 0, hover ? new Color(0xc8302a) : new Color(0x96181c), 0, h,
				hover ? new Color(0x6e1014) : new Color(0x4a0a0d)));
		g.fill(body);
		g.setPaint(new GradientPaint(0, 1, new Color(255, 220, 190, hover ? 90 : 60), 0, h / 2f, new Color(255, 220, 190, 0)));
		g.fill(new RoundRectangle2D.Float(2, 1.5f, w - 4, h / 2f, arc - 2, arc - 2));
		g.setStroke(new BasicStroke(1f));
		g.setPaint(new GradientPaint(0, 0, hover ? GOLD_LIGHT : GOLD, 0, h, GOLD_DARK));
		g.draw(body);
		g.dispose();
		return im;
	}
}

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.Random;

/**
 * The Karn parchment look (user, 2026-11-14 round 3): light, high-contrast and readable. Warm parchment / alabaster
 * surfaces with dark ink text, bronze / stone dividers, recessed item slots, Karn crimson + imperial gold accents.
 * Shared by the Perks (1151), Relic Forge (1158) and Karn Forge (1153) redesigns; each has its own layout.
 */
final class KarnParchment {

	private KarnParchment() {
	}

	static final Color PARCH = new Color(0xEDE4D3), ALABASTER = new Color(0xF5EFE4), INK = new Color(0x1E1916),
		DIVIDER = new Color(0xB8A88E), SLOT = new Color(0xDFD3BE), SLOT_EDGE = new Color(0x9C8B70), CRIMSON = new Color(0x9E1B24),
		CRIMSON_DK = new Color(0x6E1018), GOLD = new Color(0xD4AF37), BRONZE = new Color(0xB8860B), SHADOW = new Color(0x5A4A3A);
	/** the same, as 0xRRGGBB for component colours and server <col=> tags */
	static final int I_PARCH = 0xEDE4D3, I_ALABASTER = 0xF5EFE4, I_INK = 0x1E1916, I_DIVIDER = 0xB8A88E, I_SLOT = 0xDFD3BE,
		I_SLOT_EDGE = 0x9C8B70, I_CRIMSON = 0x9E1B24, I_GOLD = 0xD4AF37, I_BRONZE = 0xB8860B, I_MUTED = 0x6B5D4F,
		I_HOVER = 0xFBF6EC, I_SELECT = 0xEED9D3, I_GREEN = 0x1F7A2E, I_RED = 0xB0202A;

	static Graphics2D g(BufferedImage im) {
		Graphics2D g = im.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		return g;
	}

	static BufferedImage image(int w, int h) {
		return new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
	}

	/**
	 * The window: a crimson frame with a gold inner rule, a crimson title band with the heading in gold, and a parchment
	 * body with a faint grain. {@code w x h} is the background sprite (the interface's comp 1, drawn at 14,9).
	 */
	static BufferedImage window(int w, int h, String title, long seed) {
		BufferedImage im = image(w, h);
		Graphics2D g = g(im);
		RoundRectangle2D outer = new RoundRectangle2D.Double(0, 0, w, h, 10, 10);
		g.setColor(new Color(0x3A0A0F));
		g.fill(outer);
		g.setPaint(new GradientPaint(0, 0, new Color(0xB3222C), 0, 30, CRIMSON_DK));
		g.fill(new RoundRectangle2D.Double(2, 2, w - 4, h - 4, 9, 9));
		// parchment body
		int bx = 6, by = 30, bw = w - 12, bh = h - 36;
		g.setColor(PARCH);
		g.fillRect(bx, by, bw, bh);
		Random r = new Random(seed);
		for (int i = 0; i < bw * bh / 18; i++) { // grain
			int x = bx + r.nextInt(bw), y = by + r.nextInt(bh);
			g.setColor(new Color(0x8a, 0x70, 0x50, 10 + r.nextInt(12)));
			g.fillRect(x, y, 1, 1);
		}
		g.setColor(GOLD);
		g.drawRect(bx - 1, by - 1, bw + 1, bh + 1);
		g.setColor(new Color(0x7A5A20));
		g.drawRect(bx - 2, by - 2, bw + 3, bh + 3);
		// title
		g.setFont(new Font("Serif", Font.BOLD, 19));
		FontMetrics fm = g.getFontMetrics();
		int tx = (w - fm.stringWidth(title)) / 2;
		g.setColor(new Color(0, 0, 0, 140));
		g.drawString(title, tx + 1, 23);
		g.setColor(GOLD);
		g.drawString(title, tx, 22);
		g.setColor(GOLD);
		diamond(g, tx - 12, 15, 4);
		diamond(g, tx + fm.stringWidth(title) + 12, 15, 4);
		g.dispose();
		return im;
	}

	/** three distinct looks (user: "don't use the same template for the 3 interfaces") */
	enum Look { CODEX, FORGE, DRAFT }

	/**
	 * FORGE (Relic Forge): a bronze / gold metal frame with rivets, a light stone body. DRAFT (Karn Forge): an ink title
	 * band, grid paper. CODEX (Perks) is {@link #window(int, int, String, long)}.
	 */
	static BufferedImage window(int w, int h, String title, long seed, Look look) {
		if (look == Look.CODEX)
			return window(w, h, title, seed);
		BufferedImage im = image(w, h);
		Graphics2D g = g(im);
		int bx = 6, by = 30, bw = w - 12, bh = h - 36;
		Random r = new Random(seed);
		if (look == Look.FORGE) {
			g.setColor(new Color(0x2A1A06));
			g.fill(new RoundRectangle2D.Double(0, 0, w, h, 8, 8));
			g.setPaint(new GradientPaint(0, 0, new Color(0xC99A3A), 0, 30, new Color(0x6A4510)));
			g.fill(new RoundRectangle2D.Double(2, 2, w - 4, h - 4, 7, 7));
			g.setColor(new Color(0xE9E1D3)); // light stone
			g.fillRect(bx, by, bw, bh);
			for (int i = 0; i < bw * bh / 40; i++) { // stone mottling
				int x = bx + r.nextInt(bw), y = by + r.nextInt(bh), d = 1 + r.nextInt(3);
				g.setColor(new Color(0x7a, 0x6a, 0x58, 6 + r.nextInt(10)));
				g.fillOval(x, y, d, d);
			}
			g.setColor(GOLD);
			g.drawRect(bx - 1, by - 1, bw + 1, bh + 1);
			g.setColor(new Color(0x5A3A0C));
			g.drawRect(bx - 2, by - 2, bw + 3, bh + 3);
			for (int[] c : new int[][] { { 7, 7 }, { w - 8, 7 }, { 7, h - 8 }, { w - 8, h - 8 } }) // rivets
				rivet(g, c[0], c[1]);
			titleText(g, w, title, GOLD.brighter(), new Color(0x3A2408));
		} else {
			g.setColor(INK);
			g.fill(new RoundRectangle2D.Double(0, 0, w, h, 6, 6));
			g.setPaint(new GradientPaint(0, 0, new Color(0x3A322C), 0, 30, new Color(0x1E1916)));
			g.fill(new RoundRectangle2D.Double(2, 2, w - 4, h - 4, 5, 5));
			g.setColor(new Color(0xF3ECDD)); // drafting paper
			g.fillRect(bx, by, bw, bh);
			for (int x = bx; x < bx + bw; x += 8) {
				g.setColor(new Color(0xc8, 0xb8, 0x9c, (x - bx) % 40 == 0 ? 70 : 32));
				g.drawLine(x, by, x, by + bh - 1);
			}
			for (int y = by; y < by + bh; y += 8) {
				g.setColor(new Color(0xc8, 0xb8, 0x9c, (y - by) % 40 == 0 ? 70 : 32));
				g.drawLine(bx, y, bx + bw - 1, y);
			}
			g.setColor(CRIMSON);
			g.fillRect(bx, by - 2, bw, 2);
			titleText(g, w, title, ALABASTER, CRIMSON);
		}
		g.dispose();
		return im;
	}

	static void titleText(Graphics2D g, int w, String title, Color text, Color gems) {
		g.setFont(new Font("Serif", Font.BOLD, 19));
		FontMetrics fm = g.getFontMetrics();
		int tx = (w - fm.stringWidth(title)) / 2;
		g.setColor(new Color(0, 0, 0, 150));
		g.drawString(title, tx + 1, 23);
		g.setColor(text);
		g.drawString(title, tx, 22);
		g.setColor(gems);
		diamond(g, tx - 12, 15, 4);
		diamond(g, tx + fm.stringWidth(title) + 12, 15, 4);
	}

	static void rivet(Graphics2D g, int cx, int cy) {
		g.setColor(new Color(0x3A2408));
		g.fillOval(cx - 3, cy - 3, 6, 6);
		g.setColor(new Color(0xE8C060));
		g.fillOval(cx - 2, cy - 2, 3, 3);
	}

	/** FORGE panel: alabaster stone, a double bronze rule, rivets in the corners */
	static void forgePanel(Graphics2D g, int x, int y, int w, int h) {
		g.setColor(new Color(0, 0, 0, 34));
		g.fillRect(x + 1, y + 2, w, h);
		g.setColor(new Color(0xF7F2E8));
		g.fillRect(x, y, w, h);
		g.setColor(BRONZE);
		g.drawRect(x, y, w - 1, h - 1);
		g.setColor(new Color(0xD9C7A0));
		g.drawRect(x + 2, y + 2, w - 5, h - 5);
		for (int[] c : new int[][] { { x + 4, y + 4 }, { x + w - 5, y + 4 }, { x + 4, y + h - 5 }, { x + w - 5, y + h - 5 } })
			rivet(g, c[0], c[1]);
	}

	/** DRAFT panel: white paper with a thin ink border and drafting corner marks */
	static void draftPanel(Graphics2D g, int x, int y, int w, int h) {
		g.setColor(new Color(0, 0, 0, 22));
		g.fillRect(x + 2, y + 2, w, h);
		g.setColor(new Color(0xFCF9F2));
		g.fillRect(x, y, w, h);
		g.setColor(new Color(0x4A4038));
		g.drawRect(x, y, w - 1, h - 1);
		g.setColor(CRIMSON);
		int m = 6;
		for (int[] c : new int[][] { { x, y, 1, 1 }, { x + w - 1, y, -1, 1 }, { x, y + h - 1, 1, -1 }, { x + w - 1, y + h - 1, -1, -1 } }) {
			g.drawLine(c[0], c[1], c[0] + c[2] * m, c[1]);
			g.drawLine(c[0], c[1], c[0], c[1] + c[3] * m);
		}
	}

	/** a gold forge button (ink label) */
	static BufferedImage goldButton(int w, int h, boolean hover) {
		BufferedImage im = image(w, h);
		Graphics2D g = g(im);
		double c = Math.min(4, h / 4.0);
		g.setColor(new Color(0x3A2408));
		g.fill(new RoundRectangle2D.Double(0, 0, w, h, c * 2, c * 2));
		g.setPaint(new GradientPaint(0, 1, hover ? new Color(0xF2D27A) : new Color(0xE6C25A), 0, h - 1, hover ? new Color(0xB8860B) : new Color(0x9C7414)));
		g.fill(new RoundRectangle2D.Double(1, 1, w - 2, h - 2, c * 2, c * 2));
		g.setColor(new Color(255, 250, 220, hover ? 140 : 100));
		g.fillRect((int) c + 1, 2, (int) (w - 2 * c - 2), 1);
		g.dispose();
		return im;
	}

	/** an ink drafting button (alabaster label) */
	static BufferedImage inkButton(int w, int h, boolean hover) {
		BufferedImage im = image(w, h);
		Graphics2D g = g(im);
		g.setColor(hover ? new Color(0x3A322C) : new Color(0x2A231F));
		g.fillRect(0, 0, w, h);
		g.setColor(hover ? GOLD : new Color(0x8A7A68));
		g.drawRect(0, 0, w - 1, h - 1);
		g.dispose();
		return im;
	}

	/** the light scrollbar set (sprites 25171-25176): gold arrows on parchment, a crimson thumb in a parchment groove */
	static void scrollbar() throws Exception {
		for (int up = 0; up < 2; up++) {
			BufferedImage im = image(16, 16);
			Graphics2D g = g(im);
			g.setColor(PARCH);
			g.fillRect(0, 0, 16, 16);
			g.setColor(SLOT_EDGE);
			g.drawRect(0, 0, 15, 15);
			Path2D p = new Path2D.Double();
			double cy = up == 0 ? 6 : 10, t = up == 0 ? 4 : -4;
			p.moveTo(4, cy + t);
			p.lineTo(8, cy);
			p.lineTo(12, cy + t);
			g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			g.setColor(CRIMSON);
			g.draw(p);
			g.dispose();
			MakeKarnUi2.save(25171 + up, im);
		}
		for (int part = 0; part < 3; part++) {
			int h = part == 1 ? 5 : 4;
			BufferedImage im = image(16, h);
			Graphics2D g = g(im);
			g.setColor(new Color(0x6E1018));
			g.fillRect(3, 0, 10, h);
			g.setPaint(new GradientPaint(4, 0, new Color(0xC42A35), 12, 0, new Color(0x8A1520)));
			g.fillRect(4, part == 0 ? 1 : 0, 8, h - (part == 1 ? 0 : 1));
			g.dispose();
			MakeKarnUi2.save(25173 + part, im);
		}
		BufferedImage track = image(16, 5);
		Graphics2D g = g(track);
		g.setColor(new Color(0xE2D6C0));
		g.fillRect(3, 0, 10, 5);
		g.setColor(DIVIDER);
		g.fillRect(3, 0, 1, 5);
		g.fillRect(12, 0, 1, 5);
		g.dispose();
		MakeKarnUi2.save(25176, track);
	}

	static void diamond(Graphics2D g, double cx, double cy, double r) {
		Path2D p = new Path2D.Double();
		p.moveTo(cx, cy - r);
		p.lineTo(cx + r, cy);
		p.lineTo(cx, cy + r);
		p.lineTo(cx - r, cy);
		p.closePath();
		g.fill(p);
	}

	/** A raised alabaster panel with a bronze border; x, y in sprite coordinates. Optional small-caps heading band. */
	static void panel(Graphics2D g, int x, int y, int w, int h) {
		g.setColor(new Color(0, 0, 0, 28));
		g.fillRect(x + 1, y + 2, w, h);
		g.setColor(ALABASTER);
		g.fillRect(x, y, w, h);
		g.setColor(DIVIDER);
		g.drawRect(x, y, w - 1, h - 1);
	}

	/** A heading band across the top of a panel (the heading text itself is game text). */
	static void band(Graphics2D g, int x, int y, int w, int h) {
		g.setColor(new Color(0xE6D9C2));
		g.fillRect(x + 1, y + 1, w - 2, h);
		g.setColor(DIVIDER);
		g.drawLine(x + 1, y + h + 1, x + w - 2, y + h + 1);
	}

	/** A recessed item slot: #DFD3BE with a 1px #9C8B70 border and an inner top shadow. */
	static void slot(Graphics2D g, int x, int y, int w, int h) {
		g.setColor(SLOT);
		g.fillRect(x, y, w, h);
		g.setColor(new Color(0xC9BBA2));
		g.drawLine(x + 1, y + 1, x + w - 2, y + 1);
		g.drawLine(x + 1, y + 1, x + 1, y + h - 2);
		g.setColor(SLOT_EDGE);
		g.drawRect(x, y, w - 1, h - 1);
	}

	/** A crimson action button with a gold rim (label is game text). */
	static BufferedImage button(int w, int h, boolean hover) {
		BufferedImage im = image(w, h);
		Graphics2D g = g(im);
		double c = Math.min(4, h / 4.0);
		g.setColor(new Color(0x3A0A0F));
		g.fill(new RoundRectangle2D.Double(0, 0, w, h, c * 2, c * 2));
		g.setPaint(new GradientPaint(0, 1, hover ? new Color(0xC42A35) : new Color(0xB0222C), 0, h - 1, hover ? new Color(0x8A1520) : CRIMSON_DK));
		g.fill(new RoundRectangle2D.Double(1, 1, w - 2, h - 2, c * 2, c * 2));
		g.setColor(hover ? new Color(0xF0D070) : GOLD);
		g.draw(new RoundRectangle2D.Double(1.5, 1.5, w - 3, h - 3, c * 2, c * 2));
		g.setColor(new Color(255, 230, 200, hover ? 90 : 55));
		g.fillRect((int) c + 1, 2, (int) (w - 2 * c - 2), 1);
		g.dispose();
		return im;
	}

	/** A search field: alabaster well, bronze border, a crimson magnifier. */
	static BufferedImage field(int w, int h, boolean hover) {
		BufferedImage im = image(w, h);
		Graphics2D g = g(im);
		g.setColor(hover ? new Color(0xFFFBF3) : ALABASTER);
		g.fillRect(0, 0, w, h);
		g.setColor(hover ? CRIMSON : SLOT_EDGE);
		g.drawRect(0, 0, w - 1, h - 1);
		g.setStroke(new BasicStroke(1.6f));
		g.setColor(CRIMSON);
		g.draw(new Ellipse2D.Double(4, 3, 7, 7));
		g.draw(new Line2D.Double(10, 9.5, 13, 12.5));
		g.dispose();
		return im;
	}
}

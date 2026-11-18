import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Round 2 of the Karn interface art (2026-11-12): every window gets its own design on the KarnArt kit. Writes full-colour
 * PNGs to png/ (quantize.py then makes png_q/ for cache stage 30). Sprite ids (all free in the rev 241 cache):
 * <ul>
 * <li>24860 Titles (1160) window, 24861/24862 the round close button (shared by every round 2 window),
 * 24863-24880 the nine category tabs (normal, hover), 24881-24884 page arrows, 24885-24888 Clear / Apply.</li>
 * </ul>
 * Usage: MakeKarnUi2 &lt;assets/karn_ui&gt; [only]
 */
public class MakeKarnUi2 {
	static File png;

	public static void main(String[] a) throws Exception {
		png = new File(a[0], "png");
		png.mkdirs();
		String only = a.length > 1 ? a[1] : "";
		save(24861, KarnArt.close(18, false));
		save(24862, KarnArt.close(18, true));
		scrollbar();
		if (only.isEmpty() || only.equals("titles"))
			titles();
		if (only.isEmpty() || only.equals("streaks"))
			KarnArtStreaks.paint();
		if (only.isEmpty() || only.equals("teleports"))
			KarnArtTeleports.paint();
		if (only.isEmpty() || only.equals("soultree"))
			KarnArtSoulTree.paint();
		if (only.isEmpty() || only.equals("starter"))
			KarnArtStarter.paint(new File(a[0]));
		if (only.isEmpty() || only.equals("forge"))
			KarnArtForge.paint();
		if (only.isEmpty() || only.equals("perks"))
			KarnArtPerks.paint();
		if (only.isEmpty() || only.equals("workbench"))
			KarnArtWorkbench.paint();
	}

	static void save(int id, BufferedImage im) throws Exception {
		ImageIO.write(im, "png", new File(png, id + ".png"));
	}

	/**
	 * The round 2 scrollbar (ScrollbarClientScript.withKarnGraphics): 24852 / 24853 up / down arrows (16 x 16), 24854-24856
	 * the thumb's top cap, body (tiled) and bottom cap (16 wide), 24857 the groove (16 x 5, tiled).
	 */
	static void scrollbar() throws Exception {
		save(24852, scrollArrow(true));
		save(24853, scrollArrow(false));
		for (int part = 0; part < 3; part++) {
			int h = part == 1 ? 5 : 4;
			BufferedImage im = KarnArt.image(16, h);
			Graphics2D g = KarnArt.g(im);
			g.setColor(KarnArt.LINE_DK);
			g.fillRect(3, 0, 10, h);
			g.setPaint(new GradientPaint(4, 0, new Color(0xd23a42), 12, 0, new Color(0x7a141a)));
			g.fillRect(4, part == 0 ? 1 : 0, 8, h - (part == 1 ? 0 : 1));
			g.setColor(KarnArt.GOLD_DK);
			g.fillRect(3, 0, 1, h);
			g.fillRect(12, 0, 1, h);
			if (part == 0) {
				g.setColor(KarnArt.GOLD);
				g.fillRect(4, 0, 8, 1);
			}
			if (part == 2) {
				g.setColor(KarnArt.GOLD_DK);
				g.fillRect(4, h - 1, 8, 1);
			}
			g.dispose();
			save(24854 + part, im);
		}
		BufferedImage track = KarnArt.image(16, 5);
		Graphics2D g = KarnArt.g(track);
		g.setColor(new Color(0x0a0607));
		g.fillRect(4, 0, 8, 5);
		g.setColor(KarnArt.LINE);
		g.fillRect(4, 0, 1, 5);
		g.fillRect(11, 0, 1, 5);
		g.dispose();
		save(24857, track);
	}

	static BufferedImage scrollArrow(boolean up) {
		BufferedImage im = KarnArt.button(16, 16, KarnArt.Style.DARK, false);
		BufferedImage big = KarnArt.image(64, 64);
		Graphics2D g = KarnArt.g(big);
		Path2D p = new Path2D.Double();
		double c = up ? 26 : 38, t = up ? 13 : -13;
		p.moveTo(18, c + t);
		p.lineTo(32, c);
		p.lineTo(46, c + t);
		g.setStroke(new BasicStroke(7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.setColor(KarnArt.GOLD_HI);
		g.draw(p);
		g.dispose();
		KarnArt.stamp(im, KarnArt.down(big, 4), 0, 0);
		return im;
	}

	// ---------------------------------------------------------------- Titles (1160): "Hall of Titles"

	/** Window art origin in the root (the frame's place). */
	static final int OX = 14, OY = 9;
	static final String[] TITLE_TABS = { "grid", "check", "lock", "scroll", "crown", "skull", "swords", "pickaxe", "gem" };

	static void titles() throws Exception {
		BufferedImage im = KarnArt.window(487, 306, 31, KarnArt.CRIMSON, 1160);
		Graphics2D g = KarnArt.g(im);
		// heading flanked by two small crowns
		KarnArt.heading(g, "Hall of Titles", 243.5, 24, 17f, KarnArt.GOLD_HI, KarnArt.GOLD);
		BufferedImage crown = KarnArt.icon("crown", 14, KarnArt.GOLD_HI, KarnArt.GOLD_DK);
		g.drawImage(crown, 168, 11, null);
		g.drawImage(crown, 305, 11, null);
		// tab strip: a shallow recess behind the nine tabs (root y 46-78)
		KarnArt.well(g, 8, 35, 471, 36, new Color(0x120c0f), KarnArt.LINE);
		// title list well (root 22,82 470x144)
		KarnArt.well(g, 8, 73, 471, 144, KarnArt.WELL, KarnArt.LINE);
		// scrollbar groove on the right of the list
		g.setColor(new Color(0x0a0607));
		g.fillRect(462, 76, 16, 138);
		// details plaque (root 22,230 470x78)
		KarnArt.plate(g, 8, 221, 471, 78, new Color(0x261b21), new Color(0x170f13), KarnArt.LINE_HI);
		KarnArt.hairline(g, 14, 473, 221, KarnArt.GOLD, 170);
		// a faint crimson light in the plaque's left corner and a divider before the right column
		KarnArt.glow(g, 40, 260, 120, 50, KarnArt.CRIMSON, 34);
		KarnArt.vhairline(g, 288, 227, 294, KarnArt.GOLD_DK, 200);
		g.dispose();
		save(24860, im);

		// nine category tabs, 50 x 32, icon baked in (top), the label is game text under it
		for (int t = 0; t < 9; t++)
			for (int hover = 0; hover < 2; hover++)
				save(24863 + t * 2 + hover, tab(TITLE_TABS[t], 50, 32, hover == 1));
		// page arrows 16 x 16
		save(24881, arrow("arrow_left", false));
		save(24882, arrow("arrow_left", true));
		save(24883, arrow("arrow_right", false));
		save(24884, arrow("arrow_right", true));
		// Clear (dark) 76 x 20, Apply (crimson) 90 x 20
		save(24885, KarnArt.button(76, 20, KarnArt.Style.DARK, false));
		save(24886, KarnArt.button(76, 20, KarnArt.Style.DARK, true));
		save(24887, KarnArt.button(90, 20, KarnArt.Style.CRIMSON, false));
		save(24888, KarnArt.button(90, 20, KarnArt.Style.CRIMSON, true));
	}

	/** A category tab: dark chamfered plate, the icon centred in its top part (hover: lighter plate, brighter icon). */
	static BufferedImage tab(String icon, int w, int h, boolean hover) {
		BufferedImage im = KarnArt.button(w, h, KarnArt.Style.DARK, hover);
		BufferedImage ic = hover ? KarnArt.icon(icon, 14, KarnArt.GOLD_HI, KarnArt.GOLD)
				: KarnArt.icon(icon, 14, KarnArt.GOLD, KarnArt.GOLD_DK);
		KarnArt.stamp(im, ic, (w - 14) / 2, 4);
		return im;
	}

	/** A small round page arrow button. */
	static BufferedImage arrow(String icon, boolean hover) {
		BufferedImage im = KarnArt.button(16, 16, KarnArt.Style.DARK, hover);
		BufferedImage ic = KarnArt.icon(icon, 12, hover ? KarnArt.GOLD_HI : KarnArt.GOLD, hover ? KarnArt.GOLD : KarnArt.GOLD_DK);
		KarnArt.stamp(im, ic, 2, 2);
		return im;
	}
}

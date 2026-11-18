import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;

/**
 * Round 2 art for Karn Teleports, interface 1152: "clean ruby" -- flatter than round 1, a cooler ruby accent, vector
 * category icons. 24940 window, 24941-24954 the seven category tabs (normal, hover), 24955/24956 the search field,
 * 24957/24958 a row's Teleport button, 24959/24960 the preview's Teleport button, 24961/24962 favourite star on/off,
 * 24963 a drop slot.
 */
final class KarnArtTeleports {

	private KarnArtTeleports() {
	}

	static final Color RUBY = new Color(0xd3263f), RUBY_DK = new Color(0x6a0f1c);
	/** Favorites, Monsters, Bosses, Minigames, Wilderness, Skilling, Cities. */
	static final String[] ICONS = { "star", "skull", "dragon", "mask", "swords", "pickaxe", "castle" };

	static void paint() throws Exception {
		BufferedImage im = KarnArt.window(487, 306, 30, RUBY, 1152);
		Graphics2D g = KarnArt.g(im);
		KarnArt.heading(g, "Karn Teleports", 243.5, 23, 17f, KarnArt.GOLD_HI, KarnArt.GOLD);
		KarnArt.gem(g, 166, 17, 4, RUBY);
		KarnArt.gem(g, 321, 17, 4, RUBY);
		// category strip (root 25,54 461x48)
		KarnArt.well(g, 11, 45, 461, 48, new Color(0x271c21), KarnArt.LINE_HI);
		// list panel (root 24,105 244x197)
		KarnArt.well(g, 10, 96, 244, 198, new Color(0x2a1f24), KarnArt.LINE_HI);
		g.setColor(new Color(0x1b1316));
		g.fillRect(229, 128, 18, 160); // scrollbar groove
		// preview panel (root 274,105 214x197) with a lit stage for the model
		// round 3 (2026-11-14): lighter stone, and a tall model stage (layer y 30-104 = art y 126-200)
		KarnArt.plate(g, 260, 96, 214, 198, new Color(0x4a3840), new Color(0x2d2128), KarnArt.LINE_HI);
		KarnArt.hairline(g, 266, 468, 96, KarnArt.GOLD, 180);
		KarnArt.glow(g, 367, 163, 110, 44, RUBY, 60);
		g.setColor(new Color(0, 0, 0, 80));
		g.fill(new Ellipse2D.Double(296, 191, 142, 12));
		g.setColor(KarnArt.alpha(KarnArt.GOLD_DK, 150));
		g.draw(new Ellipse2D.Double(296, 191, 142, 12));
		KarnArt.hairline(g, 270, 464, 125, KarnArt.LINE_HI, 200);
		KarnArt.hairline(g, 270, 464, 225, KarnArt.LINE_HI, 200);
		g.dispose();
		MakeKarnUi2.save(24940, im);

		for (int t = 0; t < ICONS.length; t++)
			for (int hover = 0; hover < 2; hover++)
				MakeKarnUi2.save(24941 + t * 2 + hover, tab(ICONS[t], 61, 44, hover == 1));
		MakeKarnUi2.save(24955, search(228, 20, false));
		MakeKarnUi2.save(24956, search(228, 20, true));
		MakeKarnUi2.save(24957, ruby(57, 17, false));
		MakeKarnUi2.save(24958, ruby(57, 17, true));
		MakeKarnUi2.save(24959, ruby(198, 20, false));
		MakeKarnUi2.save(24960, ruby(198, 20, true));
		MakeKarnUi2.save(24961, KarnArt.icon("star", 16, new Color(0xffe08a), new Color(0xc98a1c)));
		MakeKarnUi2.save(24962, KarnArt.icon("star", 16, new Color(0x6a5a5e), new Color(0x3a2e32)));
		MakeKarnUi2.save(24963, slot(36, 32));
	}

	/** A category tab: a flat dark plate, the icon centred in the upper part (the label is game text under it). */
	static BufferedImage tab(String icon, int w, int h, boolean hover) {
		BufferedImage im = KarnArt.image(w, h);
		Graphics2D g = KarnArt.g(im);
		g.setColor(KarnArt.LINE_DK);
		g.fill(KarnArt.chamfer(0, 0, w, h, 4));
		g.setPaint(new GradientPaint(0, 0, hover ? new Color(0x34222a) : new Color(0x241a1f), 0, h, hover ? new Color(0x1d1317) : new Color(0x161013)));
		g.fill(KarnArt.chamfer(1, 1, w - 2, h - 2, 3.5));
		g.setColor(hover ? KarnArt.GOLD_DK : KarnArt.LINE_HI);
		g.draw(KarnArt.chamfer(1.5, 1.5, w - 3, h - 3, 3));
		g.setColor(new Color(255, 255, 255, hover ? 26 : 14));
		g.fillRect(4, 2, w - 8, 1);
		g.dispose();
		BufferedImage ic = hover ? KarnArt.icon(icon, 20, KarnArt.GOLD_HI, KarnArt.GOLD) : KarnArt.icon(icon, 20, KarnArt.GOLD, KarnArt.GOLD_DK);
		KarnArt.stamp(im, ic, (w - 20) / 2, 6);
		return im;
	}

	/** The search field: a recessed input with a magnifier at the left. */
	static BufferedImage search(int w, int h, boolean hover) {
		BufferedImage im = KarnArt.image(w, h);
		Graphics2D g = KarnArt.g(im);
		KarnArt.well(g, 0, 0, w, h, hover ? new Color(0x1c1216) : new Color(0x140d10), hover ? KarnArt.GOLD_DK : KarnArt.LINE_HI);
		g.dispose();
		KarnArt.stamp(im, KarnArt.icon("search", 13, hover ? KarnArt.GOLD_HI : KarnArt.GOLD, KarnArt.GOLD_DK), 5, 4);
		return im;
	}

	/** A ruby button (rows and the preview's Teleport). */
	static BufferedImage ruby(int w, int h, boolean hover) {
		BufferedImage im = KarnArt.image(w, h);
		Graphics2D g = KarnArt.g(im);
		double c = Math.min(4, h / 5.0);
		g.setColor(KarnArt.LINE_DK);
		g.fill(KarnArt.chamfer(0, 0, w, h, c + 0.5));
		g.setPaint(new GradientPaint(0, 1, hover ? new Color(0xf0485e) : new Color(0xcf2a42), 0, h - 1, hover ? new Color(0x8e1426) : new Color(0x6a0f1c)));
		g.fill(KarnArt.chamfer(1, 1, w - 2, h - 2, c));
		g.setColor(hover ? KarnArt.GOLD_HI : KarnArt.GOLD);
		g.draw(KarnArt.chamfer(1.5, 1.5, w - 3, h - 3, c - 0.5));
		g.setColor(new Color(255, 210, 210, hover ? 120 : 80));
		g.fill(new Rectangle2D.Double(c + 2, 2.5, w - 2 * c - 4, 1));
		g.dispose();
		return im;
	}

	/** A drop slot in the preview: a small recessed square. */
	static BufferedImage slot(int w, int h) {
		BufferedImage im = KarnArt.image(w, h);
		Graphics2D g = KarnArt.g(im);
		// round 3: a light stone tile with a thin gold edge (the dark ruby well read as clutter)
		KarnArt.well(g, 0, 0, w, h, new Color(0x3d2e35), KarnArt.GOLD_DK);
		g.dispose();
		return im;
	}
}

import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * Round 2 art for the Soul Tree, interface 1176: "Soul Sanctum" -- an ember-red accent, flat clean panels (the panels
 * themselves are components: the Weapon and Talents pages split the area differently). 24990 window, 24991/24992 the
 * page tabs (112 x 20), 24993/24994 Forms (152 x 18), 24995/24996 Rank Up (204 x 16), 24997/24998 Reset (112 x 22),
 * 24999/25000 Confirm (104 x 24), 25001/25002 Cancel (104 x 24).
 */
final class KarnArtSoulTree {

	private KarnArtSoulTree() {
	}

	static final Color EMBER = new Color(0xe2532e);

	static void paint() throws Exception {
		BufferedImage im = KarnArt.window(487, 306, 42, EMBER, 1176);
		Graphics2D g = KarnArt.g(im);
		KarnArt.heading(g, "Soul Tree", 243.5, 21, 17f, new Color(0xffd9a0), new Color(0xe0904a));
		BufferedImage flame = KarnArt.icon("flame", 14, new Color(0xffc062), new Color(0xd23a24));
		g.drawImage(flame, 190, 8, null);
		g.drawImage(flame, 283, 8, null);
		// the page area (root 24,84 464x166) and the dock (root 24,252 464x50) sit on a faint recess
		KarnArt.well(g, 8, 73, 471, 170, new Color(0x0f0a0c), KarnArt.LINE);
		KarnArt.well(g, 8, 241, 471, 56, new Color(0x0f0a0c), KarnArt.LINE);
		g.dispose();
		MakeKarnUi2.save(24990, im);
		MakeKarnUi2.save(24991, KarnArt.button(112, 20, KarnArt.Style.DARK, false));
		MakeKarnUi2.save(24992, KarnArt.button(112, 20, KarnArt.Style.DARK, true));
		MakeKarnUi2.save(24993, ember(152, 18, false));
		MakeKarnUi2.save(24994, ember(152, 18, true));
		MakeKarnUi2.save(24995, ember(204, 16, false));
		MakeKarnUi2.save(24996, ember(204, 16, true));
		MakeKarnUi2.save(24997, KarnArt.button(112, 22, KarnArt.Style.DARK, false));
		MakeKarnUi2.save(24998, KarnArt.button(112, 22, KarnArt.Style.DARK, true));
		MakeKarnUi2.save(24999, ember(104, 24, false));
		MakeKarnUi2.save(25000, ember(104, 24, true));
		MakeKarnUi2.save(25001, KarnArt.button(104, 24, KarnArt.Style.DARK, false));
		MakeKarnUi2.save(25002, KarnArt.button(104, 24, KarnArt.Style.DARK, true));
	}

	/** An ember button: the crimson button shifted toward orange. */
	static BufferedImage ember(int w, int h, boolean hover) {
		BufferedImage im = KarnArt.image(w, h);
		Graphics2D g = KarnArt.g(im);
		double c = Math.min(4, h / 5.0);
		g.setColor(KarnArt.LINE_DK);
		g.fill(KarnArt.chamfer(0, 0, w, h, c + 0.5));
		g.setPaint(new GradientPaint(0, 1, hover ? new Color(0xf0703e) : new Color(0xd04a2a), 0, h - 1, hover ? new Color(0x9a2a16) : new Color(0x7a1e10)));
		g.fill(KarnArt.chamfer(1, 1, w - 2, h - 2, c));
		g.setColor(hover ? KarnArt.GOLD_HI : KarnArt.GOLD);
		g.draw(KarnArt.chamfer(1.5, 1.5, w - 3, h - 3, c - 0.5));
		g.setColor(new Color(255, 230, 200, hover ? 120 : 80));
		g.fillRect((int) c + 2, 2, (int) (w - 2 * c - 4), 1);
		g.dispose();
		return im;
	}
}

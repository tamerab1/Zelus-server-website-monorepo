import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Round 2 art for the new-player screens, "The Gate of Karn" -- the first thing a new player sees, so it carries the real
 * KARN logo in its header and the citadel artwork (the login screen's) behind darkened glass panels.
 * <ul>
 * <li>1150 Select your path: 25030 window, 25031/25032 account row, 25033-25040 the four difficulty plates (Easy,
 * Intermediate, Hard, Extreme: a gem in the difficulty's colour), 25041 kit slot, 25042/25043 Confirm.</li>
 * <li>1175 Choose your Soulbound weapon: 25044 window, 25045 weapon card (150 x 196), 25046/25047 Select (134 x 19),
 * 25048/25049 Bind, 25050/25051 Back (112 x 24).</li>
 * </ul>
 * Inputs (in assets/karn_ui): karn_logo_src.png (the login logo), karn_bg_src.png (the citadel art).
 */
final class KarnArtStarter {

	private KarnArtStarter() {
	}

	static final Color[] DIFFICULTY = { new Color(0x4cc85a), new Color(0xe8d04a), new Color(0xf08a2a), new Color(0xe0303a) };
	static BufferedImage logo, bg;

	static void load(File dir) throws Exception {
		if (logo == null) {
			logo = ImageIO.read(new File(dir, "karn_logo_src.png"));
			bg = ImageIO.read(new File(dir, "karn_bg_src.png"));
		}
	}

	/** The window with the citadel behind everything: cover-scaled, darkened, a crimson wash, the logo in the header. */
	static BufferedImage gateWindow(int header, long seed) {
		int w = 487, h = 306;
		BufferedImage im = KarnArt.window(w, h, header, KarnArt.CRIMSON, seed);
		Graphics2D g = KarnArt.g(im);
		Shape body = KarnArt.chamfer(5, header + 2, w - 10, h - header - 7, 4);
		g.setClip(body);
		double s = Math.max((w - 10) / (double) bg.getWidth(), (h - header) / (double) bg.getHeight()) * 1.05;
		double bw = bg.getWidth() * s, bh = bg.getHeight() * s;
		g.drawImage(bg, (int) (w / 2.0 - bw / 2), (int) (header + 2 - bh * 0.18), (int) bw, (int) bh, null);
		g.setColor(new Color(10, 5, 7, 178));
		g.fill(body);
		g.setPaint(new GradientPaint(0, header, new Color(60, 8, 12, 90), 0, h, new Color(10, 4, 6, 120)));
		g.fill(body);
		g.setClip(null);
		// the logo, centred in the header
		double lh = header - 18, lw = logo.getWidth() * lh / logo.getHeight(); // the steps line sits under the logo
		BufferedImage small = scale(logo, (int) Math.round(lw), (int) Math.round(lh));
		g.drawImage(small, (int) Math.round(w / 2.0 - lw / 2), 3, null);
		g.dispose();
		KarnArt.grain(im, 5, header + 2, w - 10, h - header - 7, 4, seed);
		return im;
	}

	/** High-quality downscale (progressive halving, then bicubic), keeping alpha. */
	static BufferedImage scale(BufferedImage src, int w, int h) {
		BufferedImage cur = src;
		while (cur.getWidth() / 2 >= w && cur.getHeight() / 2 >= h) {
			BufferedImage half = KarnArt.image(cur.getWidth() / 2, cur.getHeight() / 2);
			Graphics2D g = KarnArt.g(half);
			g.drawImage(cur, 0, 0, half.getWidth(), half.getHeight(), null);
			g.dispose();
			cur = half;
		}
		BufferedImage out = KarnArt.image(w, h);
		Graphics2D g = KarnArt.g(out);
		g.drawImage(cur, 0, 0, w, h, null);
		g.dispose();
		return out;
	}

	/** A glass panel: a translucent dark fill over the art, a crisp border, a gold hairline on top. */
	static void glass(Graphics2D g, int x, int y, int w, int h) {
		g.setColor(new Color(6, 3, 4, 175));
		g.fillRect(x, y, w, h);
		g.setColor(KarnArt.LINE_DK);
		g.drawRect(x, y, w - 1, h - 1);
		g.setColor(new Color(0x4a3640));
		g.drawRect(x + 1, y + 1, w - 3, h - 3);
		KarnArt.hairline(g, x + 4, x + w - 4, y + 1, KarnArt.GOLD, 200);
	}

	static void paint(File dir) throws Exception {
		load(dir);
		// ---- 1150: Select your path (header 54: logo + the steps line under it) ----
		BufferedImage im = gateWindow(54, 1150);
		Graphics2D g = KarnArt.g(im);
		glass(g, 10, 57, 196, 206);  // account types (root 24,66)
		glass(g, 212, 57, 262, 40);  // difficulty (root 226,66)
		glass(g, 212, 101, 262, 192); // summary (root 226,110)
		// summary: the rates strip and the kit well
		KarnArt.well(g, 218, 125, 250, 17, new Color(0x120a0d), KarnArt.LINE);
		KarnArt.well(g, 215, 224, 242, 70, new Color(0x0d0809), KarnArt.LINE);
		g.dispose();
		MakeKarnUi2.save(25030, im);
		MakeKarnUi2.save(25031, accountRow(184, 25, false));
		MakeKarnUi2.save(25032, accountRow(184, 25, true));
		for (int d = 0; d < 4; d++)
			for (int hover = 0; hover < 2; hover++)
				MakeKarnUi2.save(25033 + d * 2 + hover, difficulty(60, 30, DIFFICULTY[d], hover == 1));
		MakeKarnUi2.save(25041, slot(36, 32));
		MakeKarnUi2.save(25042, confirm(196, 24, false));
		MakeKarnUi2.save(25043, confirm(196, 24, true));

		// ---- 1175: Choose your Soulbound weapon ----
		BufferedImage pick = gateWindow(54, 1175);
		g = KarnArt.g(pick);
		glass(g, 10, 255, 464, 28); // the footer note strip (root 24,264)
		g.dispose();
		MakeKarnUi2.save(25044, pick);
		MakeKarnUi2.save(25045, weaponCard(150, 194));
		MakeKarnUi2.save(25046, confirm(134, 19, false));
		MakeKarnUi2.save(25047, confirm(134, 19, true));
		MakeKarnUi2.save(25048, confirm(112, 24, false));
		MakeKarnUi2.save(25049, confirm(112, 24, true));
		MakeKarnUi2.save(25050, KarnArt.button(112, 24, KarnArt.Style.DARK, false));
		MakeKarnUi2.save(25051, KarnArt.button(112, 24, KarnArt.Style.DARK, true));
	}

	/** An account type row: a dark plate with a recessed icon pocket at the left. */
	static BufferedImage accountRow(int w, int h, boolean hover) {
		BufferedImage im = KarnArt.image(w, h);
		Graphics2D g = KarnArt.g(im);
		g.setColor(KarnArt.LINE_DK);
		g.fill(KarnArt.chamfer(0, 0, w, h, 3));
		g.setPaint(new GradientPaint(0, 0, hover ? new Color(0x34232a) : new Color(0x22181c), 0, h, hover ? new Color(0x1e1418) : new Color(0x150f12)));
		g.fill(KarnArt.chamfer(1, 1, w - 2, h - 2, 2.5));
		g.setColor(hover ? KarnArt.GOLD_DK : new Color(0x3e2d34));
		g.draw(KarnArt.chamfer(1.5, 1.5, w - 3, h - 3, 2));
		g.setColor(new Color(0x0c0809));
		g.fillRect(3, 3, 34, h - 6);
		g.setColor(new Color(255, 255, 255, hover ? 24 : 12));
		g.fillRect(40, 2, w - 44, 1);
		g.dispose();
		return im;
	}

	/** A difficulty plate: dark, a small gem in the difficulty's colour at the top left, a matching base line. */
	static BufferedImage difficulty(int w, int h, Color c, boolean hover) {
		BufferedImage im = KarnArt.button(w, h, KarnArt.Style.DARK, hover);
		Graphics2D g = KarnArt.g(im);
		g.setColor(KarnArt.alpha(c, hover ? 230 : 170));
		g.fillRect(6, h - 4, w - 12, 1);
		g.setColor(KarnArt.alpha(c, hover ? 120 : 70));
		g.fillRect(10, h - 3, w - 20, 1);
		g.dispose();
		return im;
	}

	static BufferedImage slot(int w, int h) {
		BufferedImage im = KarnArt.image(w, h);
		Graphics2D g = KarnArt.g(im);
		KarnArt.well(g, 0, 0, w, h, new Color(0x140c0f), new Color(0x4a3640));
		g.dispose();
		return im;
	}

	/** The call to action: crimson with a gold rim and a warm inner light. */
	static BufferedImage confirm(int w, int h, boolean hover) {
		BufferedImage im = KarnArt.button(w, h, KarnArt.Style.CRIMSON, hover);
		Graphics2D g = KarnArt.g(im);
		Shape inner = KarnArt.chamfer(2, 2, w - 4, h - 4, 3);
		g.setClip(inner);
		KarnArt.glow(g, w / 2.0, h * 0.2, w * 0.45, h * 0.9, new Color(0xff9a6a), hover ? 70 : 40);
		g.dispose();
		return im;
	}

	/** A Soulbound weapon card: glass with a gold rim, a pedestal glow behind where the weapon model stands. */
	static BufferedImage weaponCard(int w, int h) {
		BufferedImage im = KarnArt.image(w, h);
		Graphics2D g = KarnArt.g(im);
		g.setColor(KarnArt.LINE_DK);
		g.fill(KarnArt.chamfer(0, 0, w, h, 8));
		g.setPaint(new GradientPaint(0, 0, new Color(0x2a1a20), 0, h, new Color(0x110a0d)));
		g.fill(KarnArt.chamfer(1, 1, w - 2, h - 2, 7.5));
		g.setPaint(new GradientPaint(0, 0, KarnArt.GOLD_HI, 0, h, KarnArt.GOLD_DK));
		g.setStroke(new BasicStroke(1.2f));
		g.draw(KarnArt.chamfer(1.5, 1.5, w - 3, h - 3, 7));
		KarnArt.glow(g, w / 2.0, 62, w * 0.42, 44, KarnArt.CRIMSON, 70);
		g.setColor(new Color(0, 0, 0, 110));
		g.fill(new Ellipse2D.Double(w / 2.0 - 40, 96, 80, 10));
		g.setColor(KarnArt.alpha(KarnArt.GOLD_DK, 170));
		g.draw(new Ellipse2D.Double(w / 2.0 - 40, 96, 80, 10));
		KarnArt.gem(g, w / 2.0, 3, 3, KarnArt.CRIMSON);
		g.dispose();
		return im;
	}
}

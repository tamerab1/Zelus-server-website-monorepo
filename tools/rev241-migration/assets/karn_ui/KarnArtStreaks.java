import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;

/**
 * Round 2 art for the streak calendar, interface 1109 (daily login + daily vote share it, the server sends the title):
 * "Ember Calendar". 24900 window, 24901 day card, 24902 milestone card (days 7/14/21/28), 24903 today's reward card,
 * 24904/24905 the claim button, 24906 the lock.
 */
final class KarnArtStreaks {

	private KarnArtStreaks() {
	}

	static void paint() throws Exception {
		BufferedImage im = KarnArt.window(487, 306, 33, KarnArt.CRIMSON, 1109);
		Graphics2D g = KarnArt.g(im);
		BufferedImage flame = KarnArt.icon("flame", 18, new Color(0xffc062), new Color(0xd23a24));
		g.drawImage(flame, 243 - 122, 8, null);
		g.drawImage(flame, 243 + 104, 8, null);
		// the streak ribbon (root 22,42 470x26): crimson with gold edges and notched ends
		Path2D rib = new Path2D.Double();
		double x0 = 10, x1 = 477, y0 = 34, y1 = 58, n = 8;
		rib.moveTo(x0, y0);
		rib.lineTo(x1, y0);
		rib.lineTo(x1 - n, (y0 + y1) / 2);
		rib.lineTo(x1, y1);
		rib.lineTo(x0, y1);
		rib.lineTo(x0 + n, (y0 + y1) / 2);
		rib.closePath();
		g.setColor(KarnArt.LINE_DK);
		g.fill(AffineTransform.getTranslateInstance(0, 1).createTransformedShape(rib));
		g.setPaint(new GradientPaint(0, (float) y0, new Color(0x7a1218), 0, (float) y1, new Color(0x3c080c)));
		g.fill(rib);
		KarnArt.glow(g, 243, 46, 170, 14, new Color(0xff5a3c), 60);
		KarnArt.hairline(g, x0 + 6, x1 - 6, y0, KarnArt.GOLD, 220);
		KarnArt.hairline(g, x0 + 6, x1 - 6, y1 - 1, KarnArt.GOLD_DK, 220);
		// the calendar well (root 22,72 318x234) and the reward panel (root 344,72 148x234)
		KarnArt.well(g, 8, 63, 318, 235, KarnArt.WELL, KarnArt.LINE);
		g.setColor(new Color(0x0a0607));
		g.fillRect(310, 66, 14, 229);
		KarnArt.plate(g, 330, 63, 149, 235, new Color(0x271b21), new Color(0x150e11), KarnArt.LINE_HI);
		KarnArt.glow(g, 404, 150, 70, 80, KarnArt.CRIMSON, 40);
		KarnArt.hairline(g, 338, 471, 89, KarnArt.GOLD, 190);
		g.dispose();
		MakeKarnUi2.save(24900, im);
		MakeKarnUi2.save(24901, dayCard(68, 72, false));
		MakeKarnUi2.save(24902, dayCard(68, 72, true));
		MakeKarnUi2.save(24903, rewardCard(84, 92));
		MakeKarnUi2.save(24904, KarnArt.button(120, 30, KarnArt.Style.CRIMSON, false));
		MakeKarnUi2.save(24905, KarnArt.button(120, 30, KarnArt.Style.CRIMSON, true));
		MakeKarnUi2.save(24906, KarnArt.icon("lock", 15, new Color(0xf0c070), new Color(0x8a5a20)));
	}

	/** A calendar day: "Day N" band on top, a round recessed slot for the reward item, a status band at the bottom. */
	static BufferedImage dayCard(int w, int h, boolean milestone) {
		BufferedImage im = KarnArt.image(w, h);
		Graphics2D g = KarnArt.g(im);
		Color edge = milestone ? KarnArt.GOLD : KarnArt.LINE_HI;
		g.setColor(KarnArt.LINE_DK);
		g.fill(KarnArt.chamfer(0, 0, w, h, 5));
		g.setPaint(new GradientPaint(0, 0, milestone ? new Color(0x3a1418) : new Color(0x22181c), 0, h,
				milestone ? new Color(0x1e0a0d) : new Color(0x140e11)));
		g.fill(KarnArt.chamfer(1, 1, w - 2, h - 2, 4.5));
		g.setColor(edge);
		g.setStroke(new BasicStroke(1f));
		g.draw(KarnArt.chamfer(1.5, 1.5, w - 3, h - 3, 4));
		// top band
		g.setColor(milestone ? new Color(0x5a1a20) : new Color(0x2c2025));
		g.fillRect(3, 3, w - 6, 13);
		KarnArt.hairline(g, 4, w - 4, 16, milestone ? KarnArt.GOLD : KarnArt.GOLD_DK, 230);
		// item slot
		double cx = w / 2.0, cy = h / 2.0 + 1;
		g.setColor(KarnArt.LINE_DK);
		g.fill(new Ellipse2D.Double(cx - 20, cy - 19, 40, 38));
		g.setPaint(new RadialGradientPaint(new Point2D.Double(cx, cy - 4), 22f, new float[] { 0, 1 },
				new Color[] { milestone ? new Color(0x3a1216) : new Color(0x241a1e), new Color(0x0c0809) }));
		g.fill(new Ellipse2D.Double(cx - 19, cy - 18, 38, 36));
		g.setColor(milestone ? KarnArt.alpha(KarnArt.GOLD, 160) : KarnArt.alpha(KarnArt.LINE_HI, 200));
		g.draw(new Ellipse2D.Double(cx - 19, cy - 18, 38, 36));
		// bottom band
		g.setColor(new Color(0x0e090b));
		g.fillRect(3, h - 16, w - 6, 13);
		KarnArt.hairline(g, 4, w - 4, h - 17, milestone ? KarnArt.GOLD_DK : KarnArt.LINE_HI, 200);
		if (milestone) {
			KarnArt.gem(g, 7, 9, 2.6, KarnArt.CRIMSON);
			KarnArt.gem(g, w - 7, 9, 2.6, KarnArt.CRIMSON);
		}
		g.dispose();
		return im;
	}

	/** Today's reward: a larger card with a gold-rimmed slot and a crimson glow behind the item. */
	static BufferedImage rewardCard(int w, int h) {
		BufferedImage im = KarnArt.image(w, h);
		Graphics2D g = KarnArt.g(im);
		g.setColor(KarnArt.LINE_DK);
		g.fill(KarnArt.chamfer(0, 0, w, h, 7));
		g.setPaint(new GradientPaint(0, 0, new Color(0x3a1419), 0, h, new Color(0x18090c)));
		g.fill(KarnArt.chamfer(1, 1, w - 2, h - 2, 6.5));
		g.setColor(KarnArt.GOLD);
		g.draw(KarnArt.chamfer(1.5, 1.5, w - 3, h - 3, 6));
		double cx = w / 2.0, cy = h / 2.0;
		KarnArt.glow(g, cx, cy, 36, 36, new Color(0xff5a3c), 90);
		g.setColor(KarnArt.LINE_DK);
		g.fill(new Ellipse2D.Double(cx - 25, cy - 25, 50, 50));
		g.setPaint(new RadialGradientPaint(new Point2D.Double(cx, cy - 6), 28f, new float[] { 0, 1 },
				new Color[] { new Color(0x4a161b), new Color(0x0e0709) }));
		g.fill(new Ellipse2D.Double(cx - 24, cy - 24, 48, 48));
		g.setPaint(new GradientPaint(0, (float) (cy - 24), KarnArt.GOLD_HI, 0, (float) (cy + 24), KarnArt.GOLD_DK));
		g.setStroke(new BasicStroke(1.6f));
		g.draw(new Ellipse2D.Double(cx - 24, cy - 24, 48, 48));
		KarnArt.gem(g, cx, 7, 3, KarnArt.CRIMSON);
		KarnArt.gem(g, cx, h - 7, 3, KarnArt.CRIMSON);
		g.dispose();
		return im;
	}
}

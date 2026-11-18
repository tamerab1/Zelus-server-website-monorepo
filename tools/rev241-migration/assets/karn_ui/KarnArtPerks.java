import java.awt.image.BufferedImage;

/**
 * Karn Perks, interface 1151: since round 5 (2026-11-14) the window art is built from the user's reference by
 * ref/build_perks.py (25150, 25177-25182, 25190-25196). This only paints the crimson buttons of the set / combo views at
 * their own sizes (25153-25166, normal + hover).
 */
final class KarnArtPerks {

	private KarnArtPerks() {
	}

	/** The stone button sizes in 1151 (w, h), in sprite order from 25153 (hover = + 1). */
	static final int[][] SIZES = { { 116, 18 }, { 122, 20 }, { 124, 21 }, { 160, 21 }, { 74, 21 }, { 78, 21 }, { 94, 21 } };

	static void paint() throws Exception {
		for (int i = 0; i < SIZES.length; i++)
			for (int hover = 0; hover < 2; hover++) {
				BufferedImage b = KarnParchment.button(SIZES[i][0], SIZES[i][1], hover == 1);
				MakeKarnUi2.save(25153 + i * 2 + hover, b);
			}
	}
}

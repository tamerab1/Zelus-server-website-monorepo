/**
 * Round 2 restyle of the Soul Tree, interface 1176 (server: SoulTreeInterface), from the round 1 export in base_1176/.
 * Art: KarnArtSoulTree. Every panel collapses from five stacked bevel rects to one flat fill + one crisp border.
 */
final class RestyleSoulTree {

	private RestyleSoulTree() {
	}

	static final int FILL = 0x150e11, INNER = 0x120c0f, BORDER = 0x3b2a30, CARD = 0x1d1519, CARD_BORDER = 0x46333a;

	/** A five-rect panel (fill, gold border, crimson inner border, fill, inner fill) as one fill + one border. */
	static void panel(RestyleKarnUi2.G g, int first) {
		g.colour(first, FILL);
		g.colour(first + 1, BORDER);
		g.hide(first + 2);
		g.colour(first + 3, FILL);
		g.colour(first + 4, INNER);
	}

	static RestyleKarnUi2.G apply(RestyleKarnUi2.G g) {
		g.sprite(1, 24990, -1).pos(1, 14, 9, 487, 306);
		g.sprite(2, RestyleKarnUi2.CLOSE, RestyleKarnUi2.CLOSE_HOVER).pos(2, 472, 14, 18, 18);
		g.hide(3); // the heading is painted
		// subtitle + points row inside the header (root y 33-46)
		g.pos(4, 28, 34, 230, 13).colour(4, 0xa79a8e);
		g.pos(5, 240, 34, 210, 13);
		g.xy(6, 454, 32);
		// page tabs: dark plates, active = ember veil + gold underline
		for (int t = 0; t < 2; t++) {
			int c = 7 + t * 4, x = 144 + t * 112;
			g.sprite(c, 24991, 24992).pos(c, x + 2, 58, 108, 20);
			g.pos(c + 1, x + 3, 59, 106, 18).colour(c + 1, 0x7a2412).opacity(c + 1, 140);
			g.filled(c + 2, true).pos(c + 2, x + 30, 76, 52, 2).colour(c + 2, 0xd9a94f);
			g.colour(c + 3, 0xf3e6d2);
		}
		// weapon page
		panel(g, 17);
		panel(g, 38);
		g.colour(44, BORDER);
		g.sprite(46, 24993, 24994);
		g.colour(47, 0xfff0e0);
		// talents page
		panel(g, 50);
		for (int card : new int[] { 59, 79, 99, 119, 139, 159 }) {
			g.colour(card, CARD);
			g.colour(card + 1, CARD_BORDER);
			g.colour(card + 16, 0x120c0f);
			g.colour(card + 17, CARD_BORDER);
		}
		panel(g, 180);
		g.colour(190, BORDER);
		g.colour(194, 0x120c0f).colour(195, CARD_BORDER);
		g.sprite(196, 24995, 24996);
		g.colour(197, 0xfff0e0);
		// dock: three aura toggles + Reset
		panel(g, 199);
		for (int c : new int[] { 203, 214, 225 }) {
			g.hoverColour(c, CARD, 0x2a1c22);
			g.colour(c + 1, CARD_BORDER);
		}
		g.sprite(236, 24997, 24998);
		g.colour(237, 0xf3e6d2);
		// confirm dialog
		panel(g, 242);
		g.colour(250, BORDER);
		g.sprite(252, 24999, 25000);
		g.colour(253, 0xfff0e0);
		g.sprite(254, 25001, 25002);
		g.colour(255, 0xf3e6d2);
		return g;
	}
}

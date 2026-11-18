import net.runelite.cache.definitions.InterfaceDefinition;

/**
 * Round 2 restyle of interface 1109, the streak calendar shared by DailyLoginInterface and DailyVoteInterface (the server
 * sends the title, the streak line, the 28 days and today's reward; only the Claim layer 468 is clickable). Art:
 * KarnArtStreaks. Component ids are unchanged.
 */
final class RestyleStreaks {

	private RestyleStreaks() {
	}

	/** clientscript 63 (scrollbar init) sprite arguments of the round 2 Karn scrollbar: fill, top, sides, bottom, up, down. */
	static final int[] SCROLLBAR = { 24857, 24854, 24855, 24856, 24852, 24853 };

	static void absolute(InterfaceDefinition d) {
		d.widthMode = d.heightMode = d.xPositionMode = d.yPositionMode = 0;
	}

	static RestyleKarnUi2.G apply(RestyleKarnUi2.G g) {
		int group = 1109 << 16;
		// window: the art replaces the tiled stone frame pieces (2-12)
		g.sprite(1, 24900, -1).pos(1, 14, 9, 487, 306);
		g.hide(2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 16, 17, 18); // + the old grey box behind everything
		g.sprite(13, RestyleKarnUi2.CLOSE, RestyleKarnUi2.CLOSE_HOVER).pos(13, 472, 15, 18, 18);
		// title ("Daily Login Streak" / "Daily Vote Streak") in the quill font between the two flames
		g.pos(14, 136, 11, 240, 26).font(14, 497).colour(14, 0xe8c27a).align(14, 1, 1);
		// streak ribbon (root 22,42 470x26): text only, the ribbon is painted
		g.pos(19, 22, 43, 470, 24);
		g.hide(20, 21);
		absolute(g.d(22));
		g.pos(22, 0, 0, 470, 24).colour(22, 0xf5d58e).align(22, 1, 1);
		// the calendar: painted well, a scroll list of 7 rows x 4 days
		g.hide(24, 25);
		g.pos(23, 22, 72, 318, 234);
		g.pos(29, 25, 75, 296, 228);
		g.d(29).scrollHeight = 7 * 80 + 4;
		g.pos(470, 324, 75, 14, 228);
		Object[] sb = g.d(470).onLoadListener;
		for (int i = 0; i < SCROLLBAR.length; i++)
			sb[3 + i] = SCROLLBAR[i];
		for (int r = 0; r < 8; r++) {
			int row = 30 + r * 57;
			if (r == 7) {
				g.hide(row); // days 29-32: the server fills 28
				continue;
			}
			for (int k = 0; k < 4; k++) {
				int cell = row + 1 + k * 14, day = r * 4 + k;
				boolean milestone = (day + 1) % 7 == 0;
				g.pos(cell, 3 + k * 74, 4, 68, 72);
				// the card: border rect 32 becomes the card sprite, the old bevel rects go
				InterfaceDefinition card = g.d(cell + 1);
				card.type = 5;
				absolute(card);
				g.sprite(cell + 1, milestone ? 24902 : 24901, -1).pos(cell + 1, 0, 0, 68, 72);
				g.hide(cell + 2, cell + 3, cell + 4, cell + 5, cell + 6, cell + 7);
				absolute(g.d(cell + 8));
				absolute(g.d(cell + 9));
				g.pos(cell + 8, 0, 4, 68, 11).colour(cell + 8, milestone ? 0xf5d58e : 0xd9a94f);
				g.pos(cell + 9, 0, 57, 68, 12);
				// the item sits in the round slot (the card's centre, one px low)
				g.d(cell + 10).originalY = 1;
				// locked: a dark veil + the Karn lock
				g.colour(cell + 12, 0x0c0809).opacity(cell + 12, 96);
				g.sprite(cell + 13, 24906, -1).size(cell + 13, 15, 15);
			}
		}
		// today's reward (root 344,72 148x234): the right box layer 26 carries the claim button's sprite
		g.pos(26, 344, 72, 148, 234);
		InterfaceDefinition btn = g.d(27);
		btn.type = 5;
		absolute(btn);
		g.sprite(27, 24904, -1).pos(27, 14, 190, 120, 30).opacity(27, 0);
		g.hide(28);
		g.pos(458, 344, 72, 148, 234);
		absolute(g.d(459));
		g.pos(459, 0, 8, 148, 16).font(459, 496).colour(459, 0xf5d58e).text(459, "Today's Reward");
		absolute(g.d(460));
		g.pos(460, 32, 56, 84, 92);
		InterfaceDefinition rcard = g.d(461);
		rcard.type = 5;
		absolute(rcard);
		g.sprite(461, 24903, -1).pos(461, 0, 0, 84, 92).opacity(461, 0);
		g.hide(462, 463);
		g.colour(466, 0x0c0809).opacity(466, 96);
		g.sprite(467, 24906, -1).size(467, 15, 15);
		// the claim button: no more stone-button scripts; hover swaps the sprite under it
		InterfaceDefinition claim = g.d(468);
		absolute(claim);
		g.pos(468, 14, 190, 120, 30);
		claim.onLoadListener = null;
		claim.onMouseOverListener = new Object[] { 44, group | 27, 24905 };
		claim.onMouseLeaveListener = new Object[] { 44, group | 27, 24904 };
		claim.hasListener = true;
		g.colour(469, 0xf8eee0).font(469, 496);
		return g;
	}
}

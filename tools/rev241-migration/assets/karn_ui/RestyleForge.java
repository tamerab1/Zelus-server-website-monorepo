import net.runelite.cache.definitions.InterfaceDefinition;

/**
 * Round 7 (user, 2026-11-14): the Karn Upgrader, interface 1158 (server: RelicForge), crucible design laid out for the
 * game window (round 6 shrank the whole "upgrader idea" picture and came out "too small, not clean"). The background
 * (ref/build_crucible.py) is drawn in these same game coordinates:
 * <ul>
 * <li>left (x 16-147): three tabs, search, and the catalog as a 3-wide grid of item icons (scroll y 90-318)</li>
 * <li>centre: the base item in the fire ring (262, 92), its name on the plate under it; four reagent sockets at y 196,
 * x 184 + 54n, each with its name and owned / needed under it; the Forge button bottom centre</li>
 * <li>right: the outcome frame (440, 66) with its name under it, the success % and its 20-segment bar, the breakdown,
 * the stats card, the failure note</li>
 * </ul>
 */
final class RestyleForge {

	private RestyleForge() {
	}

	static final int INK = 0x2A1D14, ON_DARK = 0xE8DCC6;
	static final int SLOT = 25118, SLOT_SELECTED = 25119, SLOT_HOVER = 25120;
	static final int COLS = 3, COL_PITCH = 41, ROW_PITCH = 36;

	static void clear(RestyleKarnUi2.G g, int id) {
		InterfaceDefinition d = g.d(id);
		d.onMouseOverListener = null;
		d.onMouseLeaveListener = null;
		if (d.type == 3) {
			d.filled = true;
			d.opacity = 255;
		} else
			d.spriteId = -1;
	}

	static void sprite(RestyleKarnUi2.G g, int id, int sprite, int hover) {
		InterfaceDefinition d = g.d(id);
		d.type = 5;
		d.filled = false;
		d.opacity = 0;
		g.sprite(id, sprite, hover);
	}

	static void item(RestyleKarnUi2.G g, int id, int cx, int cy) {
		g.pos(id, cx - 18, cy - 16, 36, 32);
	}

	static void text(RestyleKarnUi2.G g, int id, int x, int y, int w, int h, int colour, int font, int ax, int ay) {
		g.pos(id, x, y, w, h).colour(id, colour).font(id, font).align(id, ax, ay);
	}

	static RestyleKarnUi2.G apply(RestyleKarnUi2.G g) {
		g.sprite(1, 25100, -1).pos(1, 0, 0, 512, 334);
		g.sprite(2, RestyleKarnUi2.CLOSE, RestyleKarnUi2.CLOSE_HOVER).pos(2, 488, 4, 18, 18);
		g.hide(3, 4);

		// the left column lives in layer 16, which now spans the window
		g.pos(16, 0, 0, 512, 334);
		g.hide(17, 18, 19);
		int[][] tabs = { { 17, 48 }, { 67, 39 }, { 108, 39 } }; // x, width (Weapons is the widest label)
		for (int t = 0; t < 3; t++) {
			int tab = 20 + t * 20, x = tabs[t][0], w = tabs[t][1];
			g.pos(tab, x, 44, w, 17);
			clear(g, tab);
			for (int k = 1; k <= 8; k++)
				g.hide(tab + k);
			g.pos(tab + 9, x, 44, w, 17);
			sprite(g, tab + 10, w > 40 ? 25121 : 25122, -1); // the active tab: gold (shown by the server)
			g.pos(tab + 10, 0, 0, w, 17);
			for (int k = 11; k <= 18; k++)
				g.hide(tab + k);
			g.show(tab + 19);
			text(g, tab + 19, x, 44, w, 17, ON_DARK, 494, 1, 1);
		}
		// search (painted pill) + its X
		g.pos(5, 18, 65, 110, 16);
		clear(g, 5);
		text(g, 6, 36, 65, 92, 16, ON_DARK, 494, 0, 1);
		g.hide(7, 8, 11, 12, 13, 14, 15);
		g.pos(9, 128, 65, 17, 16);
		clear(g, 9);
		text(g, 10, 128, 65, 17, 16, ON_DARK, 494, 1, 1);
		// the catalog: a 3-wide grid of icon slots, the scrollbar on its right
		g.pos(80, 20, 91, 108, 227).pos(81, 128, 91, 16, 227);
		for (int i = 0; i < 40; i++) {
			int card = 82 + i * 11;
			g.pos(card, (i % COLS) * 36, (i / COLS) * ROW_PITCH, 36, 34);
			sprite(g, card + 1, SLOT, SLOT_HOVER);
			g.pos(card + 1, 0, 0, 36, 34);
			sprite(g, card + 2, SLOT_SELECTED, -1);
			g.pos(card + 2, 0, 0, 36, 34);
			g.pos(card + 6, 0, 1, 36, 32);
			for (int k : new int[] { 3, 4, 5, 7, 8, 9, 10 }) {
				g.pos(card + k, 0, 0, 1, 1);
				g.hide(card + k);
			}
		}

		// the centre and right: layers 522 and 551 span the window
		g.pos(522, 0, 0, 512, 334).pos(551, 0, 0, 512, 334);
		g.hide(523, 524, 525, 526, 527, 528, 529, 530, 531, 535, 536, 537, 538, 539, 540, 541, 542, 543, 544, 547, 548, 549);
		g.hide(552, 553, 554, 555, 581, 582);
		item(g, 532, 262, 92);
		item(g, 533, 262, 92);
		text(g, 534, 198, 147, 128, 23, INK, 494, 1, 1);
		item(g, 545, 440, 66);
		text(g, 546, 380, 94, 120, 26, INK, 494, 1, 0);
		text(g, 580, 380, 120, 120, 16, INK, 496, 1, 1);
		for (int s = 0; s < 20; s++)
			g.pos(583 + s, 388 + s * 5, 140, 4, 8).colour(583 + s, s < 6 ? 0xD8343A : s < 12 ? 0xE0A030 : 0x4CC85A).opacity(583 + s, 0);
		text(g, 603, 380, 151, 120, 14, INK, 494, 1, 1);
		// the four sockets (5 and 6 unused: recipes use <= 3 + the catalyst)
		for (int r = 0; r < 6; r++) {
			int slot = 556 + r * 4;
			g.pos(slot, 0, 0, 1, 1).pos(slot + 1, 0, 0, 1, 1);
			g.hide(slot, slot + 1);
			if (r >= 4) {
				g.pos(slot + 2, 0, 0, 1, 1).pos(slot + 3, 0, 0, 1, 1);
				g.hide(slot + 2, slot + 3);
				continue;
			}
			int cx = 180 + 56 * r;
			item(g, slot + 2, cx, 198);
			text(g, slot + 3, cx - 28, 225, 56, 36, INK, 494, 1, 0);
		}
		text(g, 550, 388, 175, 98, 70, INK, 494, 0, 0);
		text(g, 604, 380, 252, 120, 40, INK, 494, 1, 0);
		int[] forge = { 197, 286, 130, 40 };
		g.pos(605, forge[0], forge[1], forge[2], forge[3]);
		clear(g, 605);
		for (int k = 606; k <= 613; k++)
			g.hide(k);
		g.pos(614, forge[0], forge[1], forge[2], forge[3]);
		g.pos(615, 0, 0, forge[2], forge[3]).colour(615, 0x14060E).opacity(615, 110);
		for (int k = 616; k <= 623; k++)
			g.hide(k);
		text(g, 624, forge[0] + 20, forge[1] + 9, forge[2] - 40, forge[3] - 13, 0xFFE08A, 495, 1, 1);
		for (InterfaceDefinition d : g.c.values())
			if (d.type == 4)
				d.textShadowed = false;
		for (int id : new int[] { 6, 10, 624 }) // (tab labels unshadowed: dark ink on the gold active tab)
			g.d(id).textShadowed = true; // light text on the dark pills / the button
		return g;
	}
}

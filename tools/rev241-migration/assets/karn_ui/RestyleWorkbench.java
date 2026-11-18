import net.runelite.cache.definitions.InterfaceDefinition;

/**
 * Round 5 (user, 2026-11-14): the Karn Workbench, interface 1153 (server: WorkbenchHub), built from the user's reference
 * art (assets/karn_ui/ref/workbench_ref.png, 1586 x 992; ref/build_workbench.py cleans it into the 512 x 334 background
 * 25200 and cuts the perk cards 25208-25210). Every component sits where the reference shows it; painted parts (the
 * steps line, panel titles, slot frames, the coloured Lv pills, the two buttons) keep their art and their click
 * components are transparent on top. The reference's quantity selector is not a workbench feature and was left out.
 */
final class RestyleWorkbench {

	private RestyleWorkbench() {
	}

	static final double SX = 512.0 / 1586, SY = 334.0 / 992;
	static final int TEXT = 0xE8DCC6, GOLD = 0xF5C55A;
	static final int CARD = 25208, CARD_HOVER = 25209, CARD_SELECTED = 25210, CARD_W = 116, CARD_H = 40, CARD_PITCH = 44; // round 6 (2026-11-15): taller cards, p12 text

	static int gx(int x) {
		return (int) Math.round(x * SX);
	}

	static int gy(int y) {
		return (int) Math.round(y * SY);
	}

	static int[] box(int x0, int y0, int x1, int y1, int ox, int oy) {
		return new int[] { gx(x0) - ox, gy(y0) - oy, gx(x1) - gx(x0), gy(y1) - gy(y0) };
	}

	static void at(RestyleKarnUi2.G g, int id, int[] b) {
		g.pos(id, b[0], b[1], b[2], b[3]);
	}

	static void clear(RestyleKarnUi2.G g, int id) {
		InterfaceDefinition d = g.d(id);
		if (d.type == 3) {
			d.filled = true;
			g.hoverColour(id, 0, 0);
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

	/** an item at a reference slot centre */
	static void item(RestyleKarnUi2.G g, int id, int cx, int cy, int ox, int oy) {
		g.pos(id, gx(cx) - ox - 18, gy(cy) - oy - 16, 36, 32);
	}

	static RestyleKarnUi2.G apply(RestyleKarnUi2.G g) {
		g.sprite(1, 25200, -1).pos(1, 0, 0, 512, 334);
		InterfaceDefinition closeX = g.d(2); // the painted close X: no sprite, no hover swap (it showed the old button)
		closeX.spriteId = -1;
		closeX.onMouseOverListener = null;
		closeX.onMouseLeaveListener = null;
		at(g, 2, box(1497, 122, 1556, 175, 0, 0));
		g.hide(3);
		// round 6: the steps line is live text (the painted one was too small to read)
		g.pos(4, 104, 49, 304, 14).colour(4, TEXT).font(4, 495).align(4, 1, 1);

		// left: the perk cards (layer 5: scroll 9, bar 10)
		int[] left = box(35, 210, 462, 915, 0, 0);
		at(g, 5, left);
		g.hide(6, 7, 8);
		int[] list = box(42, 218, 405, 910, left[0], left[1]);
		g.pos(9, list[0], list[1], CARD_W, list[3]);
		int[] bar = box(412, 222, 460, 908, left[0], left[1]);
		g.pos(10, bar[0] + (bar[2] - 16) / 2, bar[1], 16, bar[3]);
		for (int i = 0; i < 19; i++) {
			int card = 11 + i * 9;
			g.pos(card, 0, i * CARD_PITCH, CARD_W, CARD_H);
			sprite(g, card + 1, CARD, CARD_HOVER);
			g.pos(card + 1, 0, 0, CARD_W, CARD_H);
			sprite(g, card + 2, CARD_SELECTED, -1);
			g.pos(card + 2, 0, 0, CARD_W, CARD_H);
			g.pos(card + 3, 0, 0, 1, 1).pos(card + 4, 0, 0, 1, 1).pos(card + 5, 0, 0, 1, 1);
			g.hide(card + 3, card + 4, card + 5);
			g.pos(card + 6, 4, 5, 30, 30);
			g.pos(card + 7, 37, 1, 78, 26).colour(card + 7, GOLD).font(card + 7, 495).align(card + 7, 0, 1); // one or two lines
			g.d(card + 7).lineHeight = 12;
			g.pos(card + 8, 37, 26, 78, 13).colour(card + 8, TEXT).font(card + 8, 494);
		}

		// right: the perk (layer 182)
		int[] right = box(1065, 210, 1552, 915, 0, 0);
		at(g, 182, right);
		g.hide(183, 184, 185, 188, 192, 193);
		at(g, 186, box(1095, 226, 1535, 268, right[0], right[1]));
		g.colour(186, GOLD).font(186, 496).align(186, 1, 1);
		at(g, 187, box(1095, 268, 1535, 306, right[0], right[1]));
		g.colour(187, TEXT).font(187, 495).align(187, 1, 1);
		int[] desc = box(1100, 330, 1505, 452, right[0], right[1]);
		at(g, 189, desc);
		g.pos(190, gx(1508) - right[0], desc[1], 16, desc[3]);
		g.pos(191, 1, 0, desc[2] - 2, 600).colour(191, TEXT).font(191, 495);
		at(g, 194, box(1135, 470, 1530, 506, right[0], right[1]));
		g.colour(194, GOLD).font(194, 495).align(194, 0, 1);
		int[] rows = { 532, 568, 604, 640, 676 };
		for (int k = 0; k < 5; k++) {
			at(g, 195 + k, box(1240, rows[k] - 16, 1345, rows[k] + 16, right[0], right[1]));
			g.colour(195 + k, TEXT).font(195 + k, 495).align(195 + k, 0, 1);
			g.hide(200 + k * 2, 201 + k * 2);
		}

		// the blueprint (layer 210 spans the centre and the right column, for the two buttons)
		int[] bp = box(480, 210, 1552, 915, 0, 0);
		at(g, 210, bp);
		int ox = bp[0], oy = bp[1];
		// round 6: the target's text block = name (b12), upgrades line, perks / hint (p12); 219 ("Item") stays hidden
		g.hide(211, 212, 213, 214, 215, 216, 217, 218, 219, 224, 237);
		item(g, 220, 765, 362, ox, oy);
		int tx = gx(498) - ox, tw = gx(1042) - gx(498), ty = gy(434) - oy;
		g.pos(221, tx, ty, tw, 14).colour(221, GOLD).font(221, 496).align(221, 1, 1);
		g.pos(222, tx, ty + 14, tw, 13).colour(222, TEXT).font(222, 495).align(222, 1, 1);
		g.pos(223, tx, ty + 27, tw, 13).colour(223, TEXT).font(223, 495).align(223, 1, 1);
		int[] sx = { 568, 703, 836, 969 }, req = { 225, 228, 231, 234 }, add = { 238, 241, 244, 247 };
		for (int k = 0; k < 4; k++) {
			g.hide(req[k], req[k] + 1, add[k], add[k] + 1);
			item(g, req[k] + 2, sx[k], 670, ox, oy);
			item(g, add[k] + 2, sx[k], 852, ox, oy);
		}
		int[] min = box(1352, 730, 1537, 780, ox, oy);
		at(g, 250, min);
		clear(g, 250);
		for (int k = 251; k <= 258; k++)
			g.hide(k);
		at(g, 259, min);
		g.pos(260, 0, 0, min[2], min[3]).colour(260, 0x0A0606).opacity(260, 110);
		for (int k = 261; k <= 268; k++)
			g.hide(k);
		at(g, 269, min);
		g.colour(269, TEXT).font(269, 495).align(269, 1, 1);
		int[] up = box(1092, 805, 1530, 895, ox, oy);
		at(g, 270, up);
		clear(g, 270);
		for (int k = 271; k <= 278; k++)
			g.hide(k);
		at(g, 279, up);
		g.pos(280, 0, 0, up[2], up[3]).colour(280, 0x0A0606).opacity(280, 110);
		for (int k = 281; k <= 288; k++)
			g.hide(k);
		at(g, 289, box(1205, 815, 1522, 885, ox, oy));
		g.colour(289, 0xF5E6C8).font(289, 496).align(289, 1, 1);
		for (InterfaceDefinition d : g.c.values())
			if (d.type == 4)
				d.textShadowed = true;
		return g;
	}
}

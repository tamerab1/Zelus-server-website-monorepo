import net.runelite.cache.definitions.InterfaceDefinition;

import java.util.HashMap;
import java.util.Map;

/**
 * Round 5 (user, 2026-11-14): Karn Perks, interface 1151 (server: PerkHub), built from the user's reference art
 * (assets/karn_ui/ref/perks_ref.png, 1589 x 990; ref/build_perks.py cleans it into the 512 x 334 background 25150 and cuts
 * the row cards / scrollbar / buttons). Every component sits where the reference shows it: gx/gy convert a reference
 * pixel to a game pixel. Painted parts (tabs with their icons, loadout squares, search box, close button, panel titles,
 * the inspector's icon frame) keep their art; their click components are transparent on top.
 */
final class RestylePerks {

	private RestylePerks() {
	}

	static final double SX = 512.0 / 1589, SY = 334.0 / 990;

	static int gx(int x) {
		return (int) Math.round(x * SX);
	}

	static int gy(int y) {
		return (int) Math.round(y * SY);
	}

	/** game box (x, y, w, h) of a reference box, relative to a game origin (ox, oy) */
	static int[] box(int x0, int y0, int x1, int y1, int ox, int oy) {
		return new int[] { gx(x0) - ox, gy(y0) - oy, gx(x1) - gx(x0), gy(y1) - gy(y0) };
	}

	static void at(RestyleKarnUi2.G g, int id, int[] b) {
		g.pos(id, b[0], b[1], b[2], b[3]);
	}

	/** the dark reference palette: panels near-black brown, gold rims, ivory / gold text */
	static final int TEXT = 0xE8DCC6, GOLD = 0xF5C55A, MUTED = 0xA89A88, DARK = 0x1C1512;
	static final Map<Integer, Integer> PALETTE = new HashMap<>();

	static {
		int[][] m = {
				{ 0x36302a, DARK }, { 0x000000, 0x0A0706 }, { 0x60574b, 0x8A6A2A }, { 0x4b443a, 0x241B17 }, { 0x575045, 0x2E231D },
				{ 0x2b2620, 0x6A5020 }, { 0x2b251d, 0x3A1A14 }, { 0x15120f, 0xD4A73A }, { 0x221c16, 0x14100E }, { 0x2e271f, 0x241B17 },
				{ 0x5a4d3a, 0x8A6A2A }, { 0x2a2520, 0x181210 }, { 0x1a1612, 0x0E0A09 }, { 0x5c2a22, 0x8A1A20 }, { 0x74352b, 0xA82830 },
				{ 0x433b31, 0x241B17 }, { 0x25201b, 0x181210 }, { 0x2f2924, 0x241B17 },
				{ 0xd8d0c0, TEXT }, { 0xff9900, 0xF5E6C8 }, { 0xff981f, GOLD }, { 0xffff00, 0xF5D58E } };
		for (int[] p : m)
			PALETTE.put(p[0], p[1]);
	}

	static final int ROW = 25190, ROW_HOVER = 25191, ROW_SELECTED = 25192, BTN_HALF = 25193, BTN_FULL = 25195;
	static final int ROWS = 52, ROW_W = 258, ROW_H = 41, ROW_PITCH = 46;

	static RestyleKarnUi2.G apply(RestyleKarnUi2.G g) {
		g.remap(PALETTE);
		g.sprite(1, 25150, -1).pos(1, 0, 0, 512, 334);
		for (int panel : new int[] { 5, 40, 82, 110, 637 })
			g.hide(panel + 1, panel + 2, panel + 3, panel + 4);
		for (InterfaceDefinition d : g.c.values()) { // the set / combo views' stone buttons
			if (d.type != 5 || d.spriteId != 4525)
				continue;
			for (int i = 0; i < KarnArtPerks.SIZES.length; i++)
				if (KarnArtPerks.SIZES[i][0] == d.originalWidth && KarnArtPerks.SIZES[i][1] == d.originalHeight)
					g.sprite(d.id & 0xffff, 25153 + i * 2, 25154 + i * 2);
		}
		layout(g);
		for (InterfaceDefinition d : g.c.values())
			if (d.type == 4)
				d.textShadowed = true; // dark art: the shadow helps
		return g;
	}

	/** a click area over painted art: an invisible rectangle / no sprite */
	static void clear(RestyleKarnUi2.G g, int id) {
		InterfaceDefinition d = g.d(id);
		if (d.type == 3) {
			d.filled = true;
			d.opacity = 255;
			g.hoverColour(id, 0, 0);
			d.opacity = 255;
		} else if (d.type == 5) {
			d.spriteId = -1;
		}
	}

	static void layout(RestyleKarnUi2.G g) {
		InterfaceDefinition closeX = g.d(2); // the painted close X: no sprite, no hover swap (it showed the old button)
		closeX.spriteId = -1;
		closeX.onMouseOverListener = null;
		closeX.onMouseLeaveListener = null;
		at(g, 2, box(1510, 100, 1565, 155, 0, 0));
		// header: points, tree, loadouts, search
		at(g, 4, box(118, 168, 298, 210, 0, 0));
		g.colour(4, TEXT).font(4, 494).align(4, 0, 1);
		g.show(3);
		at(g, 3, box(388, 168, 885, 210, 0, 0));
		g.colour(3, TEXT).font(3, 494).align(3, 1, 1);
		int[] lo = box(912, 166, 1242, 214, 0, 0);
		at(g, 82, lo);
		g.hide(87, 88, 89);
		int[] sq = { 918, 985, 1050, 1117, 1183 };
		for (int i = 0; i < 5; i++) {
			int b = 90 + i * 4;
			int[] s = box(sq[i], 168, sq[i] + 50, 212, lo[0], lo[1]);
			at(g, b, s);
			clear(g, b);
			at(g, b + 1, s);
			g.colour(b + 1, TEXT).font(b + 1, 496).align(b + 1, 1, 1);
			g.hide(b + 2, b + 3);
		}
		at(g, 855, box(1272, 165, 1500, 213, 0, 0));
		clear(g, 855);
		at(g, 856, box(1330, 168, 1495, 210, 0, 0));
		g.colour(856, MUTED).align(856, 0, 1);
		g.hide(857, 858);
		at(g, 859, box(1500, 170, 1536, 208, 0, 0));
		clear(g, 859);
		at(g, 860, box(1500, 170, 1536, 208, 0, 0));
		g.colour(860, MUTED).align(860, 1, 1);

		// tabs (painted labels + icons): click areas; the active one gets a crimson veil + gold rim
		int[] tb = box(45, 235, 940, 290, 0, 0);
		at(g, 5, tb);
		int[][] tabs = { { 45, 170 }, { 183, 333 }, { 345, 495 }, { 507, 645 }, { 655, 797 }, { 807, 940 } };
		for (int k = 0; k < 6; k++) {
			int b = 10 + k * 5;
			int[] t = box(tabs[k][0], 237, tabs[k][1], 289, tb[0], tb[1]);
			at(g, b, t);
			clear(g, b);
			at(g, b + 1, new int[] { t[0], t[1], 1, 1 });
			g.hide(b + 1, b + 4);
			at(g, b + 2, t);
			g.colour(b + 2, 0xB01E26).opacity(b + 2, 150);
			at(g, b + 3, t);
			g.colour(b + 3, 0xF0C060);
		}

		// the list: one card per row, ornate scrollbar
		int[] list = box(68, 322, 935, 900, 0, 0);
		at(g, 110, list);
		g.pos(115, 0, 4, ROW_W, list[3] - 8).pos(116, ROW_W + 4, 4, 16, list[3] - 8);
		for (int i = 0; i < ROWS; i++) {
			int r = 117 + i * 10;
			g.pos(r, 0, i * ROW_PITCH, ROW_W, ROW_H);
			InterfaceDefinition card = g.d(r + 1);
			card.type = 5;
			card.filled = false;
			g.sprite(r + 1, ROW, ROW_HOVER).pos(r + 1, 0, 0, ROW_W, ROW_H);
			g.pos(r + 2, 0, 0, 1, 1);
			g.hide(r + 2);
			InterfaceDefinition sel = g.d(r + 3);
			sel.type = 5;
			sel.filled = false;
			g.sprite(r + 3, ROW_SELECTED, -1).pos(r + 3, 0, 0, ROW_W, ROW_H);
			g.pos(r + 4, 0, 0, 1, 1).colour(r + 4, DARK).opacity(r + 4, 255);
			g.pos(r + 5, 11, 4, 32, 32);
			g.pos(r + 6, 53, 5, 150, 15).colour(r + 6, GOLD).font(r + 6, 495);
			g.pos(r + 7, 53, 22, 150, 14).colour(r + 7, TEXT).font(r + 7, 494);
			g.pos(r + 8, 206, 6, 48, 30);
			clear(g, r + 8);
			g.pos(r + 9, 206, 6, 48, 30).colour(r + 9, TEXT).font(r + 9, 494).align(r + 9, 1, 1);
		}
		g.d(115).scrollHeight = ROWS * ROW_PITCH;

		// equipped perks: five slots at the reference's slot centres, the level under each
		int[] eq = box(965, 235, 1550, 425, 0, 0);
		at(g, 40, eq);
		g.hide(45);
		at(g, 46, box(1380, 245, 1536, 285, eq[0], eq[1]));
		g.colour(46, GOLD).font(46, 495).align(46, 2, 1);
		int[] cx = { 1035, 1150, 1263, 1377, 1490 };
		for (int k = 0; k < 5; k++) {
			int b = 47 + k * 7, x = gx(cx[k]) - eq[0] - 17, y = gy(340) - eq[1] - 17;
			g.pos(b, x, y, 34, 34).colour(b, 0x14100E);
			g.pos(b + 1, x, y, 34, 1).colour(b + 1, 0x0A0706).pos(b + 2, x, y, 1, 34).colour(b + 2, 0x0A0706);
			g.pos(b + 3, x, y, 34, 34).colour(b + 3, 0xB8862E);
			g.pos(b + 4, x + 1, y + 1, 32, 32);
			g.pos(b + 5, x - 4, y + 36, 42, 11).colour(b + 5, TEXT).align(b + 5, 1, 0);
			g.pos(b + 6, x + 2, y + 6, 29, 22);
		}

		// the Perk Inspector (title band, icon frame and the hooded figure are painted)
		int[] in = box(965, 512, 1550, 905, 0, 0);
		at(g, 637, in);
		for (int id : new int[] { 642, 643 }) { // shown by PerkHub: invisible, on the painted frame
			at(g, id, box(1000, 528, 1115, 645, in[0], in[1]));
			g.d(id).opacity = 255;
		}
		g.pos(644, gx(1057) - in[0] - 16, gy(586) - in[1] - 16, 32, 32);
		at(g, 645, box(1135, 525, 1500, 612, in[0], in[1]));
		g.colour(645, GOLD).font(645, 496).align(645, 0, 1);
		at(g, 646, box(995, 655, 1500, 702, in[0], in[1]));
		g.colour(646, TEXT);
		at(g, 647, box(995, 706, 1500, 780, in[0], in[1]));
		g.colour(647, TEXT);
		at(g, 653, box(995, 706, 1500, 790, in[0], in[1]));
		for (int k = 0; k < 5; k++) {
			int b = 654 + k * 4, x = k * 33;
			g.pos(b, x, 0, 32, 32).colour(b, 0x14100E).pos(b + 1, x, 0, 32, 32).colour(b + 1, 0xB8862E).pos(b + 2, x, 0, 32, 32);
			g.pos(b + 3, x - 3, 32, 38, 11).colour(b + 3, TEXT).align(b + 3, 1, 0);
		}
		at(g, 648, box(995, 780, 1500, 815, in[0], in[1]));
		g.colour(648, MUTED);
		int bx = gx(995) - in[0], bw = (gx(1500) - gx(995) - 4) / 2, row1 = gy(818) - in[1], row2 = gy(864) - in[1];
		for (int[] h : new int[][] { { 649, 0 }, { 651, 1 }, { 861, 0 }, { 863, 1 } })
			button(g, h[0], bx + h[1] * (bw + 4), row1, bw, 16, BTN_HALF);
		button(g, 674, bx, row2, bw * 2 + 4, 15, BTN_FULL);
		button(g, 853, bx, row2, bw * 2 + 4, 15, BTN_FULL);
	}

	static void button(RestyleKarnUi2.G g, int id, int x, int y, int w, int h, int sprite) {
		g.sprite(id, sprite, sprite + 1).pos(id, x, y, w, h).pos(id + 1, x + 1, y, w - 2, h).colour(id + 1, 0xF5E6C8)
			.font(id + 1, 494).align(id + 1, 1, 1);
	}
}

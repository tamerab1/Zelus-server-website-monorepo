import net.runelite.cache.definitions.InterfaceDefinition;

/**
 * Round 2 restyle of Karn Teleports, interface 1152 (server: TeleportNexus), from the round 1 export in base_1152/.
 * Art: KarnArtTeleports. The category boxes become icon tab sprites (the server no longer sends item icons), the rows
 * go flat (hairline separators, a ruby accent bar when selected), new search field, buttons, stars and drop slots.
 */
final class RestyleTeleports {

	private RestyleTeleports() {
	}

	static RestyleKarnUi2.G apply(RestyleKarnUi2.G g) {
		g.sprite(1, 24940, -1).pos(1, 14, 9, 487, 306);
		g.sprite(2, RestyleKarnUi2.CLOSE, RestyleKarnUi2.CLOSE_HOVER).pos(2, 472, 14, 18, 18);
		g.hide(3); // the heading is painted
		g.pos(4, 22, 41, 470, 12).colour(4, 0xa79a8e);
		// seven category tabs (layer 5 at 27,56): the box becomes the icon tab sprite
		for (int c = 0; c < 7; c++) {
			int box = 10 + c * 6, x = c * 66;
			InterfaceDefinition d = g.d(box);
			d.type = 5;
			d.filled = false;
			g.sprite(box, 24941 + c * 2, 24942 + c * 2).pos(box, x, 0, 61, 44);
			g.hide(box + 1, box + 4); // old border, item icon
			// active: a ruby veil + a gold underline
			g.pos(box + 2, x + 1, 1, 59, 42).colour(box + 2, 0x7a1222).opacity(box + 2, 150);
			InterfaceDefinition under = g.d(box + 3);
			under.filled = true;
			g.pos(box + 3, x + 14, 40, 33, 2).colour(box + 3, 0xd9a94f);
			g.pos(box + 5, x, 28, 61, 13).colour(box + 5, 0xeee3d2);
		}
		// search field (list layer 52 at 24,105)
		InterfaceDefinition search = g.d(57);
		search.type = 5;
		search.filled = false;
		g.sprite(57, 24955, 24956).pos(57, 8, 8, 228, 20);
		g.pos(58, 30, 8, 200, 20);
		// rows: flat, a hairline under each, selected = deep ruby fill + a bright accent bar on the left
		for (int r = 0; r < 80; r++) {
			int row = 61 + r * 11;
			g.hoverColour(row + 1, 0x2e2227, 0x402c34);
			InterfaceDefinition line = g.d(row + 2);
			line.filled = true;
			g.pos(row + 2, 4, 35, 202, 1).colour(row + 2, 0x45323a);
			g.pos(row + 3, 0, 0, 210, 36).colour(row + 3, 0x4a1422);
			InterfaceDefinition bar = g.d(row + 4);
			bar.filled = true;
			g.pos(row + 4, 0, 2, 3, 32).colour(row + 4, 0xe0425a);
			g.pos(row + 5, 9, 3, 122, 14).colour(row + 5, 0xf5d58e);
			g.pos(row + 6, 9, 19, 122, 14).colour(row + 6, 0xa79a8e);
			g.sprite(row + 7, 24957, 24958).pos(row + 7, 132, 10, 57, 17);
			g.pos(row + 8, 132, 10, 57, 17).colour(row + 8, 0xfff0e0);
			g.sprite(row + 9, 24961, -1).pos(row + 9, 192, 10, 16, 16);
			g.sprite(row + 10, 24962, -1).pos(row + 10, 192, 10, 16, 16).opacity(row + 10, 0);
		}
		// preview (layer 941 at 274,105)
		// round 3 (2026-11-14): a tall model stage (y 30-104), the description cut to two lines
		g.pos(947, 8, 4, 198, 14).colour(947, 0xf5d58e);
		g.pos(948, 8, 17, 198, 12).colour(948, 0xb5a89c);
		g.pos(946, 8, 60, 198, 14).colour(946, 0xb0a397);
		for (int m = 949; m <= 954; m++)
			g.pos(m, 8, 30, 198, 74);
		g.pos(955, 10, 106, 194, 24).colour(955, 0xe2d8c9);
		for (int k = 0; k < 4; k++) {
			int fill = 956 + k * 3;
			InterfaceDefinition slot = g.d(fill);
			slot.type = 5;
			slot.filled = false;
			// four slots spread evenly across the card (36 wide, 12 apart, centred in the 198 px content width)
			g.sprite(fill, 24963, -1).pos(fill, 20 + k * 48, 143, 36, 32);
			g.hide(fill + 1);
			g.xy(fill + 2, 20 + k * 48, 143);
		}
		g.pos(968, 8, 130, 198, 12);
		g.sprite(969, 24959, 24960).pos(969, 8, 177, 198, 20);
		g.pos(970, 8, 177, 198, 20).colour(970, 0xfff0e0);
		g.sprite(971, 24961, -1).pos(971, 190, 4, 16, 16); // favourite star: top-right, beside the name
		g.sprite(972, 24962, -1).pos(972, 190, 4, 16, 16).opacity(972, 0);
		return g;
	}
}

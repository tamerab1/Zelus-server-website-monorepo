import net.runelite.cache.definitions.InterfaceDefinition;

/**
 * Round 2 restyle of the new-player screens, "The Gate of Karn" (art: KarnArtStarter), from the round 1 exports:
 * 1150 Select your path (server: StarterSelectInterface) and 1175 Choose your Soulbound weapon (server:
 * SoulStarterInterface). Component ids are unchanged.
 */
final class RestyleStarter {

	private RestyleStarter() {
	}

	static void toSprite(RestyleKarnUi2.G g, int id) {
		InterfaceDefinition d = g.d(id);
		d.type = 5;
		d.filled = false;
		d.opacity = 0;
		d.widthMode = d.heightMode = d.xPositionMode = d.yPositionMode = 0;
	}

	static RestyleKarnUi2.G path(RestyleKarnUi2.G g) {
		g.sprite(1, 25030, -1).pos(1, 14, 9, 487, 306);
		g.sprite(2, RestyleKarnUi2.CLOSE, RestyleKarnUi2.CLOSE_HOVER).pos(2, 472, 14, 18, 18);
		g.hide(3); // the KARN logo is painted in the header
		g.pos(4, 21, 48, 473, 13).colour(4, 0xeee3d2); // the steps line under the logo
		// account types (root 24,66 196x206)
		g.pos(5, 24, 66, 196, 206);
		g.colour(9, 0xf5d58e).font(9, 496).pos(9, 8, 3, 120, 16);
		g.pos(10, 100, 4, 88, 14).colour(10, 0xa79a8e);
		for (int i = 0; i < 7; i++) {
			int row = 11 + i * 6, y = 22 + i * 26;
			toSprite(g, row);
			g.sprite(row, 25031, 25032).pos(row, 6, y, 184, 25);
			g.hide(row + 1);
			g.pos(row + 2, 7, y + 1, 182, 23).colour(row + 2, 0x7a1018).opacity(row + 2, 150);
			g.filled(row + 3, true).pos(row + 3, 6, y + 1, 3, 23).colour(row + 3, 0xf5d58e);
			g.pos(row + 4, 9, y, 32, 25);
			g.pos(row + 5, 46, y, 140, 25);
		}
		// difficulty (root 226,60 262x40): four plates, a gem in each difficulty's colour
		g.pos(53, 226, 66, 262, 40);
		for (int d = 0; d < 4; d++) {
			int b = 57 + d * 5, x = 5 + d * 63;
			g.sprite(b, 25033 + d * 2, 25034 + d * 2).pos(b, x, 5, 60, 30);
			g.hide(b + 1);
			g.pos(b + 2, x + 1, 6, 58, 28).colour(b + 2, 0x7a1018).opacity(b + 2, 140);
			g.filled(b + 3, true).pos(b + 3, x + 14, 31, 32, 2).colour(b + 3, 0xf5d58e);
			g.pos(b + 4, x, 5, 60, 30);
		}
		// summary (root 226,104 262x198)
		g.pos(77, 226, 110, 262, 192);
		g.colour(81, 0xf5d58e);
		g.size(84, 246, 58);
		g.xy(85, 6, 104);
		g.xy(86, 8, 106);
		g.xy(87, 4, 123);
		g.xy(88, 242, 123);
		g.hide(82); // the rates strip is painted
		g.colour(85, 0x4a3640);
		g.colour(86, 0xd9a94f);
		for (int k = 0; k < 30; k++) {
			int slot = 89 + k * 6;
			toSprite(g, slot + 1);
			g.sprite(slot + 1, 25041, -1).pos(slot + 1, 0, 0, 36, 32);
			g.hide(slot + 2, slot + 3, slot + 4);
		}
		// the call to action
		g.sprite(269, 25042, 25043).pos(269, 24, 276, 196, 24);
		g.pos(270, 25, 279, 194, 18).colour(270, 0xfff4e6);
		return g;
	}

	static RestyleKarnUi2.G weapon(RestyleKarnUi2.G g) {
		g.sprite(1, 25044, -1).pos(1, 14, 9, 487, 306);
		g.sprite(2, RestyleKarnUi2.CLOSE, RestyleKarnUi2.CLOSE_HOVER).pos(2, 472, 14, 18, 18);
		g.hide(3);
		g.pos(4, 21, 48, 473, 13).align(4, 1, 0);
		for (int k = 0; k < 3; k++) {
			int c = 5 + k * 49;
			g.pos(c, 24 + k * 157, 66, 150, 194);
			toSprite(g, c + 1);
			g.sprite(c + 1, 25045, -1).pos(c + 1, 0, 0, 150, 194);
			g.hide(c + 2, c + 3, c + 4);
			g.colour(c + 9, 0xf5d58e);
			g.hide(c + 11, c + 12, c + 13, c + 14, c + 15, c + 16, c + 17); // the model box: the card paints a pedestal
			g.colour(c + 21, 0x6a4a2a);
			g.sprite(c + 43, 25046, 25047);
			g.colour(c + 46, 0x2e0a0e).colour(c + 47, 0xd9a94f).colour(c + 48, 0xd9a94f); // selected: veil + gold trims
		}
		g.hide(152, 153, 154); // the footer strip is painted
		for (int c : new int[] { 152, 153, 154 })
			g.d(c).originalY = 264;
		for (int c = 155; c <= 172; c++)
			g.d(c).originalY += 3;
		// confirm binding dialog
		g.colour(176, 0x150e11).colour(177, 0x4a3640);
		g.hide(178);
		g.colour(179, 0x150e11);
		g.colour(184, 0x3b2a30);
		g.sprite(186, 25048, 25049);
		g.colour(187, 0xfff4e6);
		g.sprite(188, 25050, 25051);
		g.colour(189, 0xeee3d2);
		return g;
	}
}

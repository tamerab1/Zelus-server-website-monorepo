import net.runelite.cache.fs.Store;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;

/**
 * Stage 50 (user, 2026-11-16): NPC {@link #NPC} "Keeper of the Seals" (renamed 2026-11-17), The Five Seals' lobby host behind the home reception
 * desk. A copy of Taala (13996, the Hueycoatl camp leader: models, recolours, stance) with its own name and the
 * options Start-Solo / Party-Lobby / Reward-Shop and (round 2) Karn crimson / gold robes; her multi-npc transform (106 / 118) is dropped so the
 * copy always looks the same. Idempotent. Usage: PatchFiveSealsNpc apply &lt;cache241&gt;
 */
public final class PatchFiveSealsNpc {

	private PatchFiveSealsNpc() {
	}

	static final int NPC = 30003, TEMPLATE = 13996;
	static final String NAME = "Keeper of the Seals"; // user 2026-11-17: a name that matches the minigame (was "Minigame Manager")
	static final String[] OPS = { "Start-Solo", "Party-Lobby", "Reward-Shop" }; // the Hall of Seals is read at its board (user 2026-11-16)

	public static void main(String[] a) throws Exception {
		Store dst = new Store(new File(a[1]));
		dst.load();
		Map<Integer, byte[]> npcs = RoundTripDefs.files(dst, 9);
		byte[] template = npcs.get(TEMPLATE);
		if (template == null)
			throw new IllegalStateException("template npc " + TEMPLATE + " missing");
		DefRewriter.ParamKinds pk = DefRewriter.paramKinds(RoundTripDefs.files(dst, 11));
		// round 2 (user 2026-11-16): Taala's blue robes in the Karn crimson and gold
		java.util.List<Integer> models = new java.util.ArrayList<>();
		for (byte[] seg : DefRewriter.segments("npc", template, DefRewriter.IDENTITY, pk))
			if ((seg[0] & 0xff) == 61)
				for (int k = 0; k < (seg[1] & 0xff); k++)
					models.add(PatchFiveSealsArt.i32(seg, 2 + k * 4));
		java.util.List<int[]> pairs = PatchFiveSealsArt.pairs(PatchFiveSealsArt.colours(dst, models), PatchFiveSealsNpc::karn);
		ByteArrayOutputStream b = new ByteArrayOutputStream();
		boolean named = false;
		for (byte[] seg : DefRewriter.segments("npc", template, DefRewriter.IDENTITY, pk)) {
			int op = seg[0] & 0xff;
			if (op == 0)
				break;
			if (op >= 30 && op <= 34 || op == 40 || op == 106 || op == 118)
				continue; // our own options below; no transforms
			if (op == 2) {
				string(b, 2, NAME);
				named = true;
				continue;
			}
			b.writeBytes(seg);
		}
		if (!named)
			string(b, 2, NAME);
		for (int i = 0; i < OPS.length; i++)
			string(b, 30 + i, OPS[i]);
		PatchFiveSealsArt.recolour(b, pairs);
		b.write(0);
		byte[] def = b.toByteArray();
		byte[] have = npcs.get(NPC);
		int changed = have != null && java.util.Arrays.equals(have, def) ? 0
			: PatchCustoms27.putConfigs(dst, 9, new TreeMap<>(Map.of(NPC, def)));
		dst.close();
		System.out.println("RESULT five seals npc: " + changed + " change(s), npc " + NPC + " '" + NAME + "' from " + TEMPLATE);
	}

	/** Taala's colours -> Karn: the blue robes / hood crimson, the pale trims and checks gold, the brown leather charcoal */
	static int karn(int c) {
		int h = c >> 10, s = c >> 7 & 7, l = c & 127;
		if (h >= 33 && h <= 37) {
			if (l >= 80)
				return PatchFiveSealsArt.hsl(8, 6, 58);      // pale trims -> gold
			if (h == 37)
				return PatchFiveSealsArt.hsl(0, 5, 10);      // the check accents -> near-black crimson
			return PatchFiveSealsArt.hsl(0, 7, l * 6 / 10);  // robes / hood -> crimson
		}
		if (h == 3 && s <= 1)
			return PatchFiveSealsArt.hsl(0, 2, l * 6 / 10);  // brown leather -> charcoal
		return -1;
	}

	private static void string(ByteArrayOutputStream b, int op, String s) {
		b.write(op);
		b.writeBytes(s.getBytes(StandardCharsets.ISO_8859_1));
		b.write(0);
	}
}

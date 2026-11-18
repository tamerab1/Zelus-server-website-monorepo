import net.runelite.cache.fs.Store;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;

/**
 * Stage 32 of the rev 241 cache build (2026-11-11): the home Portal Nexus (object 33410, model 36052) becomes "Karn
 * Teleports" in the Karn colours. Same id, so its map placement and server actions (HomeHandler) are untouched; only the
 * name (op 2) and the recolour list (op 40) are replaced. The old Zelus reskin turned it white marble / gold / teal; now:
 * gold trim stays gold, the grey stone body is dark crimson, the marble is a lighter burgundy, the portal core deep
 * blood-red and its glow ember orange. Find colours are the model's own (packed 16-bit HSL); no replacement equals a
 * find colour (the client applies the pairs in order). Idempotent. Usage: PatchKarnNexus apply &lt;cache241&gt;
 */
public final class PatchKarnNexus {

	private PatchKarnNexus() {
	}

	static final int NEXUS = 33410;
	static final String NAME = "Karn Teleports";
	/** 2026-11-11: two sizes bigger -- 2x2 to 4x4 tiles (ops 14 / 15) and the model doubled (ops 65-67, 128 = normal); the map
	 * loc moves one tile south-west with it (stage 29 mv:) so it keeps its centre. */
	static final int SIZE = 4, SCALE = 256;

	/** {find, replace}; h s l packed as h << 10 | s << 7 | l. */
	static final int[][] RECOLOUR = {
			// gold trim (orange 7-9 + two white flecks): rich gold, as before
			{8123, 9016}, {9152, 8902}, {10178, 9814}, {119, 8922}, {127, 9828},
			// grey stone body: dark crimson stone
			{24811, hsl(1, 4, 44)}, {26851, hsl(1, 4, 34)},
			// light marble: lighter burgundy
			{32993, hsl(1, 4, 60)}, {33001, hsl(1, 4, 66)}, {33118, hsl(1, 4, 56)},
			// portal core: deep blood-red
			{32675, hsl(0, 7, 36)}, {33688, hsl(0, 7, 26)}, {33692, hsl(0, 7, 30)}, {34706, hsl(0, 7, 20)},
			// portal glow: ember orange
			{33715, hsl(3, 7, 56)}, {33728, hsl(3, 7, 70)}, {33740, hsl(3, 7, 84)},
	};

	static int hsl(int h, int s, int l) {
		return h << 10 | s << 7 | l;
	}

	public static void main(String[] a) throws Exception {
		if (a.length != 2 || !a[0].equals("apply"))
			throw new IllegalArgumentException("apply <cache241>");
		for (int[] p : RECOLOUR)
			for (int[] q : RECOLOUR)
				if (p[1] == q[0])
					throw new IllegalStateException("replacement " + p[1] + " is also a find colour");
		Store dst = new Store(new File(a[1]));
		dst.load();
		Map<Integer, byte[]> locs = RoundTripDefs.files(dst, PatchColosseum.LOC_GROUP);
		DefRewriter.ParamKinds pk = DefRewriter.paramKinds(RoundTripDefs.files(dst, 11));
		byte[] def = locs.get(NEXUS);
		if (def == null)
			throw new IllegalStateException("object " + NEXUS + " missing");
		byte[] karn = karn(def, pk);
		int changed = 0;
		if (!Arrays.equals(def, karn)) {
			Map<Integer, byte[]> put = new TreeMap<>();
			put.put(NEXUS, karn);
			changed = PatchCustoms27.putConfigs(dst, PatchColosseum.LOC_GROUP, put);
		}
		dst.close();
		System.out.println("RESULT karn nexus: " + changed + " change(s); object " + NEXUS + " = " + NAME);
	}

	static byte[] karn(byte[] def, DefRewriter.ParamKinds pk) {
		ByteArrayOutputStream b = new ByteArrayOutputStream();
		b.write(2);
		b.writeBytes(NAME.getBytes(StandardCharsets.ISO_8859_1));
		b.write(0);
		b.write(14);
		b.write(SIZE);
		b.write(15);
		b.write(SIZE);
		for (int op = 65; op <= 67; op++) {
			b.write(op);
			b.write(SCALE >> 8);
			b.write(SCALE & 0xff);
		}
		b.write(40);
		b.write(RECOLOUR.length);
		for (int[] p : RECOLOUR) {
			b.write(p[0] >> 8 & 0xff);
			b.write(p[0] & 0xff);
			b.write(p[1] >> 8 & 0xff);
			b.write(p[1] & 0xff);
		}
		for (byte[] seg : DefRewriter.segments("obj", def, DefRewriter.IDENTITY, pk)) {
			int op = seg[0] & 0xff;
			if (op == 0)
				break;
			if (op == 2 || op == 40 || op == 14 || op == 15 || (op >= 65 && op <= 67))
				continue; // replaced above
			b.writeBytes(seg);
		}
		b.write(0);
		return b.toByteArray();
	}
}

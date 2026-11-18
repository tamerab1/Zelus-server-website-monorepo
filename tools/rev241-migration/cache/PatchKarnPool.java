import net.runelite.cache.fs.Store;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;

/**
 * Stage 33 of the rev 241 cache build (2026-11-12): the home's Ornate pool of Rejuvenation (object 29241, model 32101) in
 * the Karn colours. Recoloured in place (user's call), so every 29241 changes, the POH ornate pool included; its map
 * placement and the Drink action (KronosRejuvinationPool) are untouched. Only the recolour list (op 40) is replaced.
 * The pool was grey stone, gold trim and cyan water; now: the gold stays gold, the stone is dark crimson (as the Karn
 * Teleports nexus, stage 32) and the water is blood-red, lightest at the surface. Find colours are the model's own
 * (packed 16-bit HSL); no replacement equals a find colour. Idempotent. Usage: PatchKarnPool apply &lt;cache241&gt;
 */
public final class PatchKarnPool {

	private PatchKarnPool() {
	}

	static final int POOL = 29241;

	/** {find, replace}; h s l packed as h << 10 | s << 7 | l. */
	static final int[][] RECOLOUR = {
			// grey stone (darkest to lightest): dark crimson stone
			{53, hsl(1, 3, 20)}, {61, hsl(1, 3, 26)}, {70, hsl(1, 4, 32)}, {78, hsl(1, 4, 38)}, {86, hsl(1, 4, 44)},
			// pale rim highlight + gold trim: gold, as the vanilla recolour had it
			{90, 8792}, {8121, 9022}, {8128, 8906},
			// water surface (light): bright blood-red
			{61594, hsl(0, 7, 42)}, {61598, hsl(0, 7, 48)}, {61603, hsl(1, 7, 58)},
			// water depth (dark): deep blood-red
			{61714, hsl(0, 7, 24)}, {61718, hsl(0, 7, 30)},
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
		byte[] def = locs.get(POOL);
		if (def == null)
			throw new IllegalStateException("object " + POOL + " missing");
		byte[] karn = karn(def, pk);
		int changed = 0;
		if (!Arrays.equals(def, karn)) {
			Map<Integer, byte[]> put = new TreeMap<>();
			put.put(POOL, karn);
			changed = PatchCustoms27.putConfigs(dst, PatchColosseum.LOC_GROUP, put);
		}
		dst.close();
		System.out.println("RESULT karn pool: " + changed + " change(s); object " + POOL + " recoloured");
	}

	static byte[] karn(byte[] def, DefRewriter.ParamKinds pk) {
		ByteArrayOutputStream b = new ByteArrayOutputStream();
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
			if (op == 40)
				continue; // replaced above
			b.writeBytes(seg);
		}
		b.write(0);
		return b.toByteArray();
	}
}

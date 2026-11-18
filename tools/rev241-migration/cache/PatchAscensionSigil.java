import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.loaders.ModelLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Store;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Stage 55 of the rev 241 cache build (user 2026-11-17: "remove the Karn partyhat from the Five Seals rewards and swap with
 * something ... that can be upgraded"): the Ascension sigil, tiers 1-5, items {@link #FIRST}..{@link #FIRST}+4.
 * <p>
 * Each tier is the Deadman "Sigil of the formidable fighter" (25996, model 42892: a 4-shade silver disc around one glowing
 * core colour) recoloured in the Karn palette, every tier its own look: bronze / silver / gold disc with a crimson core,
 * then a crimson disc with a gold core, then a radiant gold disc with a white-gold core. Inventory option 1 "Check" (the
 * server shows the tier and its bonus); Deadman's Inspect / Unattune / Destroy, its category and params are dropped, and
 * so are the noted form and the placeholder.
 * <p>
 * Idempotent. Usage: PatchAscensionSigil apply &lt;cache241&gt;
 */
public final class PatchAscensionSigil {

	private PatchAscensionSigil() {
	}

	static final int FIRST = 42777, TIERS = 5, BASE = 25996;
	/** {disc hue, disc saturation, disc lightness shift, core hue, core saturation, core lightness} per tier */
	private static final int[][] PALETTE = {
		{ 4, 3, 0, 0, 6, 58 },     // 1: bronze, crimson core
		{ 10, 0, 10, 0, 7, 64 },   // 2: silver, crimson core
		{ 8, 5, 8, 0, 7, 66 },     // 3: gold, crimson core
		{ 0, 5, 0, 8, 7, 70 },     // 4: crimson, gold core
		{ 8, 6, 14, 9, 3, 100 },   // 5: radiant gold, white-gold core
	};
	private static final String[] DESC = {
		"A bronze sigil of the Five Seals. Its crimson core stirs.",
		"A silver sigil of the Five Seals. Its crimson core glows.",
		"A golden sigil of the Five Seals. Its crimson core burns.",
		"A crimson sigil of the Five Seals, cored with gold.",
		"The radiant sigil of the Five Seals. It shines like the Sovereign's crown.",
	};

	static int hsl(int h, int s, int l) {
		return h << 10 | s << 7 | Math.max(0, Math.min(127, l));
	}

	public static void main(String[] a) throws Exception {
		if (a.length != 2 || !a[0].equals("apply"))
			throw new IllegalArgumentException("apply <cache241>");
		Store dst = new Store(new File(a[1]));
		dst.load();
		DefRewriter.ParamKinds pk = DefRewriter.paramKinds(RoundTripDefs.files(dst, 11));
		Map<Integer, byte[]> items = RoundTripDefs.files(dst, 10);
		for (int t = 0; t < TIERS; t++)
			if (items.containsKey(FIRST + t) && !new String(items.get(FIRST + t), StandardCharsets.ISO_8859_1).contains("Ascension sigil"))
				throw new IllegalStateException("item " + (FIRST + t) + " is taken by something else");
		byte[] src = items.get(BASE);
		int model = -1;
		for (byte[] seg : DefRewriter.segments("item", src, DefRewriter.IDENTITY, pk))
			if ((seg[0] & 0xff) == 44)
				model = int32(seg, 1);
		TreeSet<Integer> own = colours(dst, model);
		if (own.size() != 5)
			throw new IllegalStateException("sigil model " + model + ": expected 4 disc shades + 1 core colour, got " + own);

		Map<Integer, byte[]> put = new TreeMap<>();
		for (int t = 0; t < TIERS; t++) {
			int[] p = PALETTE[t];
			List<int[]> pairs = new ArrayList<>();
			Set<Integer> used = new HashSet<>();
			for (int c : own) {
				int h = c >> 10, l = c & 127;
				int k = h == 30 ? hsl(p[3], p[4], p[5]) : hsl(p[0], p[1], l + p[2]); // hue 30 = the glowing core
				while (own.contains(k) || used.contains(k))
					k++; // never map onto one of the model's own colours (it would be recoloured again)
				used.add(k);
				pairs.add(new int[] { c, k });
			}
			ByteArrayOutputStream b = new ByteArrayOutputStream();
			b.write(40);
			b.write(pairs.size());
			for (int[] q : pairs) {
				b.write(q[0] >> 8);
				b.write(q[0] & 0xff);
				b.write(q[1] >> 8);
				b.write(q[1] & 0xff);
			}
			string(b, 2, "Ascension sigil (tier " + (t + 1) + ")");
			string(b, 3, DESC[t]);
			string(b, 35, "Check");
			for (byte[] seg : DefRewriter.segments("item", src, DefRewriter.IDENTITY, pk)) {
				int op = seg[0] & 0xff;
				if (op == 0)
					break;
				switch (op) {
					case 2, 3, 35, 36, 37, 38, 39, 40, 41, 94, 97, 98, 148, 149, 249 -> {
					} // ours above / Deadman's options, category and params / no noted form, no placeholder
					default -> b.writeBytes(seg);
				}
			}
			b.write(0);
			put.put(FIRST + t, b.toByteArray());
		}
		put.entrySet().removeIf(e -> Arrays.equals(e.getValue(), items.get(e.getKey())));
		int changed = put.isEmpty() ? 0 : PatchCustoms27.putConfigs(dst, 10, put);
		dst.close();
		System.out.println("RESULT ascension sigil: " + changed + " change(s); items " + FIRST + "-" + (FIRST + TIERS - 1)
			+ " from " + BASE + " (model " + model + ", colours " + own + ")");
	}

	private static TreeSet<Integer> colours(Store s, int model) throws Exception {
		Archive ar = s.findIndex(7).getArchive(model);
		ModelDefinition md = new ModelLoader().load(model, ar.decompress(s.getStorage().loadArchive(ar)));
		TreeSet<Integer> own = new TreeSet<>();
		for (int f = 0; f < md.faceCount; f++)
			if (md.faceTextures == null || md.faceTextures[f] == -1)
				own.add(md.faceColors[f] & 0xffff);
		return own;
	}

	private static int int32(byte[] seg, int o) {
		return (seg[o] & 0xff) << 24 | (seg[o + 1] & 0xff) << 16 | (seg[o + 2] & 0xff) << 8 | seg[o + 3] & 0xff;
	}

	private static void string(ByteArrayOutputStream b, int op, String s) {
		b.write(op);
		b.writeBytes(s.getBytes(StandardCharsets.ISO_8859_1));
		b.write(0);
	}
}

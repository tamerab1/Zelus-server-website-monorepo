import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.loaders.ModelLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Store;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Stage 34 of the rev 241 cache build (2026-11-12): the home's Statue of Fortune (objects 65473 dormant / 65474 charged /
 * 65475 blessed, the server swaps them; models 65277-65279) becomes the "Karn Statue" in the Karn colours. Same ids, so
 * StatueOfFortune and its actions are untouched; the name (op 2) is replaced and a recolour list (op 40) is added.
 *
 * The models carry the old Zelus teal: h31 s7 = the dark body, h30 = the glowing seams / flame / shards (lighter = more
 * charged), h8-9 = gold, h20 s1 = pale highlights. The recolour is built from each model's own colours, keeping every
 * lightness so the three states still differ: body -> dark crimson stone (as Karn Teleports, stage 32), glow -> blood-red
 * at the dark end through ember orange at the bright end, gold unchanged, highlights -> warm pale gold. No replacement equals
 * a find colour of the same model. Idempotent. Usage: PatchKarnStatue apply &lt;cache241&gt;
 */
public final class PatchKarnStatue {

	private PatchKarnStatue() {
	}

	static final int[] LOCS = {65473, 65474, 65475};
	static final String NAME = "Karn Statue";

	static int hsl(int h, int s, int l) {
		return h << 10 | s << 7 | l;
	}

	/** The Karn colour for one of the statue's own colours, or -1 to leave it. */
	static int karn(int c) {
		int h = c >> 10, s = c >> 7 & 7, l = c & 127;
		if (h == 31 && s == 7) // dark teal body: dark crimson stone, a touch lighter so it doesn't go black
			return hsl(1, 5, Math.min(127, l + 6));
		if (h == 30) { // teal glow: blood-red -> ember orange as it brightens
			if (l < 60)
				return hsl(0, 7, l);
			if (l < 85)
				return hsl(2, 7, l);
			return hsl(4, 6, l);
		}
		if (h == 20 && s == 1) // pale highlights: warm pale gold
			return hsl(7, 2, l);
		return -1; // gold (h8-9) stays
	}

	public static void main(String[] a) throws Exception {
		if (a.length != 2 || !a[0].equals("apply"))
			throw new IllegalArgumentException("apply <cache241>");
		Store dst = new Store(new File(a[1]));
		dst.load();
		Map<Integer, byte[]> locs = RoundTripDefs.files(dst, PatchColosseum.LOC_GROUP);
		DefRewriter.ParamKinds pk = DefRewriter.paramKinds(RoundTripDefs.files(dst, 11));
		Map<Integer, byte[]> put = new TreeMap<>();
		for (int id : LOCS) {
			byte[] def = locs.get(id);
			if (def == null)
				throw new IllegalStateException("object " + id + " missing");
			int model = model(def, pk);
			int[][] pairs = recolour(dst, model);
			byte[] karn = rewrite(def, pairs, pk);
			if (!Arrays.equals(def, karn))
				put.put(id, karn);
			System.out.println("  object " + id + " model " + model + ": " + pairs.length + " colours");
		}
		int changed = put.isEmpty() ? 0 : PatchCustoms27.putConfigs(dst, PatchColosseum.LOC_GROUP, put);
		dst.close();
		System.out.println("RESULT karn statue: " + changed + " change(s); objects " + Arrays.toString(LOCS) + " = " + NAME);
	}

	/** The single model of a rev 241 loc record (op 6: count, then 4-byte model + type per entry). */
	static int model(byte[] def, DefRewriter.ParamKinds pk) {
		for (byte[] seg : DefRewriter.segments("obj", def, DefRewriter.IDENTITY, pk))
			if ((seg[0] & 0xff) == 6 && (seg[1] & 0xff) == 1)
				return (seg[2] & 0xff) << 24 | (seg[3] & 0xff) << 16 | (seg[4] & 0xff) << 8 | seg[5] & 0xff;
		throw new IllegalStateException("no single op-6 model");
	}

	static int[][] recolour(Store s, int model) throws Exception {
		Archive ar = s.findIndex(7).getArchive(model);
		ModelDefinition md = new ModelLoader().load(model, ar.decompress(s.getStorage().loadArchive(ar)));
		TreeSet<Integer> own = new TreeSet<>();
		for (int i = 0; i < md.faceCount; i++)
			own.add(md.faceColors[i] & 0xffff);
		List<int[]> pairs = new ArrayList<>();
		for (int c : own) {
			int k = karn(c);
			if (k < 0)
				continue;
			if (own.contains(k))
				throw new IllegalStateException("replacement " + k + " is also a colour of model " + model);
			pairs.add(new int[] {c, k});
		}
		if (pairs.size() > 255)
			throw new IllegalStateException("too many colours");
		return pairs.toArray(new int[0][]);
	}

	static byte[] rewrite(byte[] def, int[][] pairs, DefRewriter.ParamKinds pk) {
		ByteArrayOutputStream b = new ByteArrayOutputStream();
		b.write(2);
		b.writeBytes(NAME.getBytes(StandardCharsets.ISO_8859_1));
		b.write(0);
		b.write(40);
		b.write(pairs.length);
		for (int[] p : pairs) {
			b.write(p[0] >> 8 & 0xff);
			b.write(p[0] & 0xff);
			b.write(p[1] >> 8 & 0xff);
			b.write(p[1] & 0xff);
		}
		for (byte[] seg : DefRewriter.segments("obj", def, DefRewriter.IDENTITY, pk)) {
			int op = seg[0] & 0xff;
			if (op == 0)
				break;
			if (op == 2 || op == 40)
				continue; // replaced above
			b.writeBytes(seg);
		}
		b.write(0);
		return b.toByteArray();
	}
}

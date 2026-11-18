import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.loaders.ModelLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Store;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Stage 38 of the rev 241 cache build (user, 2026-11-14): "Ava's of Karn", item {@link #ITEM}, the top Ava's device.
 * Round 2 (user: "bad colours"): built on the Masori assembler (27374: models 46525 / 46502 / 46507, 267 faces, already
 * in OSRS gold) instead of Ava's assembler; its record is copied (framing, options, params) with our name, description
 * and recolour (op 40, replacing Masori's), so the meshes are untouched. The models have no texture coordinates, so the
 * look is colour only:
 * <ul>
 * <li>Masori's dark teal (h32) -> Karn crimson (h0 s7, the same darkness steps)</li>
 * <li>the dull red trim (h0 s3) -> rich crimson; the brown straps (h5 s7) -> dark crimson leather</li>
 * <li>the gold (h6) and black unchanged</li>
 * </ul>
 * The placeholder link (ops 148 / 149) is dropped. Idempotent. Usage: PatchKarnAva apply &lt;cache241&gt;
 */
public final class PatchKarnAva {

	private PatchKarnAva() {
	}

	static final int SOURCE = 27374, ITEM = 42767;
	static final int[] MODELS = { 46525, 46502, 46507 };
	static final String NAME = "Ava's of Karn", DESC = "Ava's finest work, forged in the colours of Karn.";

	static int hsl(int h, int s, int l) {
		return h << 10 | s << 7 | Math.max(0, Math.min(127, l));
	}

	/** the Karn colour for one of the model's colours, or -1 to keep it */
	static int karn(int c) {
		int h = c >> 10, s = c >> 7 & 7, l = c & 127;
		if (h == 32)
			return hsl(0, 7, l + 10); // dark teal panels -> crimson (a touch lighter: crimson reads darker than teal)
		if (h == 0 && s == 3)
			return hsl(0, 7, l);       // dull red trim -> rich crimson
		if (h == 5 && s == 7)
			return hsl(1, 6, l + 4);   // brown straps -> dark crimson leather
		return -1;
	}

	public static void main(String[] a) throws Exception {
		if (a.length != 2 || !a[0].equals("apply"))
			throw new IllegalArgumentException("apply <cache241>");
		Store dst = new Store(new File(a[1]));
		dst.load();
		TreeSet<Integer> own = new TreeSet<>();
		for (int model : MODELS) {
			Archive ar = dst.findIndex(7).getArchive(model);
			ModelDefinition md = new ModelLoader().load(model, ar.decompress(dst.getStorage().loadArchive(ar)));
			for (int f = 0; f < md.faceCount; f++)
				own.add(md.faceColors[f] & 0xffff);
		}
		List<int[]> pairs = new ArrayList<>();
		for (int c : own) {
			int k = karn(c);
			if (k < 0)
				continue;
			if (own.contains(k))
				throw new IllegalStateException("replacement " + k + " is also one of the model's colours");
			pairs.add(new int[] { c, k });
		}
		Map<Integer, byte[]> items = RoundTripDefs.files(dst, 10);
		DefRewriter.ParamKinds pk = DefRewriter.paramKinds(RoundTripDefs.files(dst, 11));
		byte[] source = items.get(SOURCE), have = items.get(ITEM);
		if (source == null)
			throw new IllegalStateException("item " + SOURCE + " missing");
		if (have != null && !new String(have, StandardCharsets.ISO_8859_1).contains(NAME))
			throw new IllegalStateException("item " + ITEM + " is taken by something else");
		ByteArrayOutputStream b = new ByteArrayOutputStream();
		b.write(40);
		b.write(pairs.size());
		for (int[] p : pairs) {
			b.write(p[0] >> 8);
			b.write(p[0] & 0xff);
			b.write(p[1] >> 8);
			b.write(p[1] & 0xff);
		}
		for (byte[] seg : DefRewriter.segments("item", source, DefRewriter.IDENTITY, pk)) {
			int op = seg[0] & 0xff;
			if (op == 0)
				break;
			switch (op) {
				case 40, 148, 149 -> {
				}
				case 2, 3 -> {
					b.write(op);
					b.writeBytes((op == 2 ? NAME : DESC).getBytes(StandardCharsets.ISO_8859_1));
					b.write(0);
				}
				default -> b.writeBytes(seg);
			}
		}
		b.write(0);
		int changed = PatchCustoms27.putConfigs(dst, 10, new TreeMap<>(Map.of(ITEM, b.toByteArray())));
		dst.close();
		StringBuilder sb = new StringBuilder();
		for (int[] p : pairs)
			sb.append(' ').append(p[0]).append("->").append(p[1]);
		System.out.println("RESULT karn ava: " + changed + " change(s); item " + ITEM + " \"" + NAME + "\", " + pairs.size() + " colours:" + sb);
	}
}

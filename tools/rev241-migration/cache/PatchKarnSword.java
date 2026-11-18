import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.loaders.ModelLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Store;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Stage 44 of the rev 241 cache build (user 2026-11-15): the Karn sword, item {@link #ITEM}, the server's best sword.
 * Built on the Fleshrender Sword (59537: models 64619 inventory / 64620 worn). Its meshes carry no texture coordinates,
 * so the Karn look is a full recolour (op 40) of its 28 flat colours into the Karn palette:
 * <ul>
 * <li>the vivid red accent (h62 s7) -> Karn crimson;</li>
 * <li>the pale flesh highlights (h0 s2-3, light) -> bright gold;</li>
 * <li>the flesh mid / dark tones (h0 s1-3) -> deep crimson-black steel;</li>
 * <li>the hilt's greys (h2-3 s0) and browns (h1 s2) -> dark gold / bronze.</li>
 * </ul>
 * Its inventory framing is a clean diagonal (the usual sword pose). The rest of the record (options, worn data, params)
 * is the Fleshrender's; the placeholder / note links are dropped. Idempotent. Usage: PatchKarnSword apply &lt;cache241&gt;
 */
public final class PatchKarnSword {

	private PatchKarnSword() {
	}

	static final int SOURCE = 59537, ITEM = 42773;
	static final int[] MODELS = { 64619, 64620 };
	static final String NAME = "Karn sword", DESC = "The blade of Karn, forged in crimson and gold.";
	/** inventory framing: zoom, pitch (xan2d), yaw (yan2d) -- a clean diagonal like the OSRS swords */
	static final int ZOOM = 2200, XAN = 444, YAN = 1056, XOFF = 0, YOFF = 0;

	static int hsl(int h, int s, int l) {
		return h << 10 | s << 7 | Math.max(1, Math.min(127, l));
	}

	/** the Karn colour for one of the model's colours, or -1 to keep it */
	static int karn(int c) {
		int h = c >> 10, s = c >> 7 & 7, l = c & 127;
		if (h == 62 && s == 7)
			return hsl(0, 7, l);                       // vivid red accent -> crimson
		if (h == 0 && s >= 2 && s <= 3 && l >= 70)
			return hsl(8, 5, Math.min(110, l + 6));    // pale highlights -> bright gold
		if (h == 0 && s <= 3 && l < 70)
			return hsl(0, 6, Math.max(14, l - 4));     // flesh mid / dark -> deep crimson steel
		if (h == 0 && s == 6)
			return hsl(0, 7, l);                       // deep red -> crimson
		if ((h == 2 || h == 3) && s == 0)
			return hsl(7, 4, l + 6);                   // hilt greys -> dark gold
		if (h == 1 && s == 2)
			return hsl(7, 5, l + 10);                  // hilt browns -> bronze gold
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
		// pairs; a replacement must not be one of the model's own colours (it would be recoloured twice / ambiguously)
		List<int[]> pairs = new ArrayList<>();
		Set<Integer> used = new HashSet<>();
		for (int c : own) {
			int k = karn(c);
			if (k < 0)
				continue;
			while (own.contains(k) || used.contains(k))
				k = (k & ~127) | Math.min(127, (k & 127) + 1);
			used.add(k);
			pairs.add(new int[] { c, k });
		}
		if (pairs.size() > 255)
			throw new IllegalStateException("too many colours: " + pairs.size());
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
		b.write(4); // zoom2d
		b.write(ZOOM >> 8);
		b.write(ZOOM & 0xff);
		b.write(5); // xan2d
		b.write(XAN >> 8);
		b.write(XAN & 0xff);
		b.write(6); // yan2d
		b.write(YAN >> 8);
		b.write(YAN & 0xff);
		b.write(7); // xOffset2d
		b.write(XOFF >> 8 & 0xff);
		b.write(XOFF & 0xff);
		b.write(8); // yOffset2d
		b.write(YOFF >> 8 & 0xff);
		b.write(YOFF & 0xff);
		for (byte[] seg : DefRewriter.segments("item", source, DefRewriter.IDENTITY, pk)) {
			int op = seg[0] & 0xff;
			if (op == 0)
				break;
			switch (op) {
				case 4, 5, 6, 7, 8, 40, 97, 98, 148, 149 -> {
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
		System.out.println("RESULT karn sword: " + changed + " change(s); item " + ITEM + " \"" + NAME + "\", " + pairs.size() + " colours");
	}
}

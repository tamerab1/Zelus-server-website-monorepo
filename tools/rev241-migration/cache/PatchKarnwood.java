import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.loaders.ModelLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Store;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.IntUnaryOperator;

/**
 * Stage 51 of the rev 241 cache build (user 2026-11-16): The Five Seals' own gathering spots and room-2 creature, in the
 * Karn crimson and gold.
 * <ul>
 * <li>textures 272 (sprite 25308: the shared leaf texture 8 turned crimson) and 273 (sprite 25309: the magic tree's
 * drifting sparkles 34 turned to gold / ember embers, same scroll), from assets/karnwood (make_textures.py);</li>
 * <li>objects {@link #TREE} "Karnwood tree" (magic tree 10834: crimson leaves, dark ember bark, embers) and {@link #STUMP}
 * its stump (9713); {@link #ROCK} "Bloodgold rocks" (Urt salt rocks 33254: black-crimson stone, gold veins) and
 * {@link #ROCK_EMPTY} the mined-out rock (33253, dull veins);</li>
 * <li>items {@link #LOGS} "Karnwood logs" (magic logs 1513) and {@link #ORE} "Bloodgold ore" (runite ore 451): no noted
 * form, no placeholder (minigame-only resources);</li>
 * <li>npc {@link #ANKOU} "Crimson Ankou": the Ankou (2514: stats, animations, Attack) wearing the Crimson Ankou set
 * (42714-42722's male worn models + the set's own recolour).</li>
 * </ul>
 * Object ids stay below 65536 (spawned locs travel as 16 bits). Idempotent. Usage: PatchKarnwood apply &lt;cache241&gt; &lt;assets/karnwood&gt;
 */
public final class PatchKarnwood {

	private PatchKarnwood() {
	}

	static final int TREE = 63307, STUMP = 63308, ROCK = 63309, ROCK_EMPTY = 63310;
	static final int LOGS = 42775, ORE = 42776, ANKOU = 30261;
	static final int TEX_LEAVES = 272, TEX_EMBERS = 273, SPR_LEAVES = 25308, SPR_EMBERS = 25309;
	static final int[] ANKOU_SET = { 42714, 42716, 42718, 42720, 42722 };

	static int hsl(int h, int s, int l) {
		return h << 10 | s << 7 | Math.max(0, Math.min(127, l));
	}

	public static void main(String[] a) throws Exception {
		if (a.length != 3 || !a[0].equals("apply"))
			throw new IllegalArgumentException("apply <cache241> <assets/karnwood>");
		File dir = new File(a[2]);
		Store dst = new Store(new File(a[1]));
		dst.load();
		int changed = 0;

		/* textures */
		changed += PatchSolarmane.putSingle(dst, 8, SPR_LEAVES, BuildBinary241.pngSprite(new File(dir, "png/" + SPR_LEAVES + ".png").toPath())) ? 1 : 0;
		changed += PatchSolarmane.putSingle(dst, 8, SPR_EMBERS, BuildBinary241.pngSprite(new File(dir, "png/" + SPR_EMBERS + ".png").toPath())) ? 1 : 0;
		Map<Integer, byte[]> tex = PatchSolarmane.textures(dst);
		Map<Integer, byte[]> putTex = new TreeMap<>();
		// {sprite u16, average colour u16, 0, direction, speed}: leaves still like texture 8, embers scroll like texture 34
		texture(tex, putTex, TEX_LEAVES, SPR_LEAVES, hsl(0, 6, 24), 0, 0);
		texture(tex, putTex, TEX_EMBERS, SPR_EMBERS, hsl(3, 7, 50), 1, 2);
		if (!putTex.isEmpty())
			changed += PatchCustoms27.putTextures(dst, putTex);

		DefRewriter.ParamKinds pk = DefRewriter.paramKinds(RoundTripDefs.files(dst, 11));

		/* objects */
		Map<Integer, byte[]> locs = RoundTripDefs.files(dst, PatchColosseum.LOC_GROUP);
		for (int id : new int[] { TREE, STUMP, ROCK, ROCK_EMPTY })
			if (locs.containsKey(id) && !owned(locs.get(id)))
				throw new IllegalStateException("object " + id + " is taken by something else");
		Map<Integer, byte[]> putLocs = new TreeMap<>();
		putLocs.put(TREE, loc(dst, locs.get(10834), pk, "Karnwood tree", c -> {
			int h = c >> 10, s = c >> 7 & 7, l = c & 127;
			return h == 8 ? hsl(0, 2, l - 13) : -1; // the bark: charcoal-black ember wood
		}, new int[][] { { 8, TEX_LEAVES }, { 34, TEX_EMBERS } }));
		putLocs.put(STUMP, loc(dst, locs.get(9713), pk, "Karnwood stump", c -> {
			int h = c >> 10, s = c >> 7 & 7, l = c & 127;
			if (h == 8)
				return hsl(0, 2, l - 13);         // bark: charcoal-black
			if (h == 28)
				return hsl(8, 3, l - 8);          // the pale cut face: pale gold
			if (h >= 30)
				return hsl(0, 6, l - 2);          // moss -> crimson
			return -1;
		}, null));
		// round 2 (user: "fully red"): the salt rock (33254 / mined 33253, model 35864: body h7 + veins h19) instead of the
		// one-colour runite rock: black-crimson stone threaded with gold
		putLocs.put(ROCK, loc(dst, locs.get(33254), pk, "Bloodgold rocks", c -> {
			int h = c >> 10;
			if (h == 7)
				return hsl(0, 3, 16);             // the body: black-crimson stone
			if (h == 19)
				return hsl(8, 7, 60);             // the veins: bright gold
			return -1;
		}, null));
		putLocs.put(ROCK_EMPTY, loc(dst, locs.get(33253), pk, "Rocks", c -> {
			int h = c >> 10;
			if (h == 7)
				return hsl(0, 3, 16);
			if (h == 19)
				return hsl(0, 4, 26);             // spent veins: dull dark red
			return -1;
		}, null));
		putLocs.entrySet().removeIf(e -> Arrays.equals(e.getValue(), locs.get(e.getKey())));
		if (!putLocs.isEmpty())
			changed += PatchCustoms27.putConfigs(dst, PatchColosseum.LOC_GROUP, putLocs);

		/* items */
		Map<Integer, byte[]> items = RoundTripDefs.files(dst, 10);
		for (int id : new int[] { LOGS, ORE })
			if (items.containsKey(id) && !owned(items.get(id)))
				throw new IllegalStateException("item " + id + " is taken by something else");
		Map<Integer, byte[]> putItems = new TreeMap<>();
		putItems.put(LOGS, item(dst, items.get(1513), pk, "Karnwood logs", "Ember-veined logs from a Karnwood tree. The seal hungers for them.", c -> {
			int h = c >> 10, s = c >> 7 & 7, l = c & 127;
			if (h == 8)
				return hsl(0, 4, l - 4);          // bark
			if (h == 28)
				return hsl(8, 3, l - 10);         // the cut ends: pale gold
			if (h == 30)
				return hsl(0, 6, l);              // moss -> crimson
			return -1;
		}, new int[][] { { 34, TEX_EMBERS } }));
		putItems.put(ORE, item(dst, items.get(451), pk, "Bloodgold ore", "Blood-red stone threaded with gold. The seal hungers for it.", c -> {
			int h = c >> 10;
			if (h == 5)
				return hsl(0, 6, 30);             // the stone: blood crimson
			if (h == 6)
				return hsl(8, 7, 48);             // the ore: gold
			return -1;
		}, null));
		putItems.entrySet().removeIf(e -> Arrays.equals(e.getValue(), items.get(e.getKey())));
		if (!putItems.isEmpty())
			changed += PatchCustoms27.putConfigs(dst, 10, putItems);

		/* the Crimson Ankou */
		Map<Integer, byte[]> npcs = RoundTripDefs.files(dst, 9);
		if (npcs.containsKey(ANKOU) && !new String(npcs.get(ANKOU), StandardCharsets.ISO_8859_1).contains("Crimson Ankou"))
			throw new IllegalStateException("npc " + ANKOU + " is taken by something else");
		List<Integer> models = new ArrayList<>();
		byte[] recolour = null;
		for (int id : ANKOU_SET) {
			for (byte[] seg : DefRewriter.segments("item", items.get(id), DefRewriter.IDENTITY, pk)) {
				int op = seg[0] & 0xff;
				if (op == 0)
					break;
				if (op == 45 || op == 46) // male worn models (45 also carries a u8 offset)
					models.add(int32(seg, 1));
				if (op == 40 && recolour == null)
					recolour = seg;
			}
		}
		if (models.size() != 6 || recolour == null)
			throw new IllegalStateException("Crimson Ankou set: expected 6 worn models + a recolour, got " + models);
		ByteArrayOutputStream b = new ByteArrayOutputStream();
		for (byte[] seg : DefRewriter.segments("npc", npcs.get(2514), DefRewriter.IDENTITY, pk)) {
			int op = seg[0] & 0xff;
			if (op == 0)
				break;
			switch (op) {
				case 2 -> string(b, 2, "Crimson Ankou");
				case 40 -> b.writeBytes(recolour);
				case 61 -> {
					b.write(61);
					b.write(models.size());
					for (int m : models) {
						b.write(m >>> 24);
						b.write(m >>> 16 & 0xff);
						b.write(m >>> 8 & 0xff);
						b.write(m & 0xff);
					}
				}
				default -> b.writeBytes(seg);
			}
		}
		b.write(0);
		if (!Arrays.equals(b.toByteArray(), npcs.get(ANKOU)))
			changed += PatchCustoms27.putConfigs(dst, 9, new TreeMap<>(Map.of(ANKOU, b.toByteArray())));
		dst.close();
		System.out.println("RESULT karnwood: " + changed + " change(s); textures 272/273, objects " + TREE + "-" + ROCK_EMPTY
			+ ", items " + LOGS + "/" + ORE + ", npc " + ANKOU + " (models " + models + ")");
	}

	/** our own records carry one of our names */
	private static boolean owned(byte[] def) {
		String s = new String(def, StandardCharsets.ISO_8859_1);
		return s.contains("Karnwood") || s.contains("Bloodgold") || s.contains("Rocks");
	}

	private static void texture(Map<Integer, byte[]> have, Map<Integer, byte[]> put, int id, int sprite, int avg, int dir, int speed) {
		byte[] rec = { (byte) (sprite >> 8), (byte) sprite, (byte) (avg >> 8), (byte) avg, 0, (byte) dir, (byte) speed };
		byte[] old = have.get(id);
		if (old != null && (old[0] != rec[0] || old[1] != rec[1]))
			throw new IllegalStateException("texture " + id + " is taken by something else");
		if (!Arrays.equals(have.get(id), rec))
			put.put(id, rec);
	}

	private static int int32(byte[] seg, int o) {
		return (seg[o] & 0xff) << 24 | (seg[o + 1] & 0xff) << 16 | (seg[o + 2] & 0xff) << 8 | seg[o + 3] & 0xff;
	}

	private static void string(ByteArrayOutputStream b, int op, String s) {
		b.write(op);
		b.writeBytes(s.getBytes(StandardCharsets.ISO_8859_1));
		b.write(0);
	}

	/** the model's own colours -> recolour pairs (no replacement may equal one of the model's colours) */
	private static List<int[]> pairs(Store s, Collection<Integer> models, IntUnaryOperator karn) throws Exception {
		TreeSet<Integer> own = new TreeSet<>();
		for (int model : models) {
			Archive ar = s.findIndex(7).getArchive(model);
			ModelDefinition md = new ModelLoader().load(model, ar.decompress(s.getStorage().loadArchive(ar)));
			for (int f = 0; f < md.faceCount; f++)
				if (md.faceTextures == null || md.faceTextures[f] == -1)
					own.add(md.faceColors[f] & 0xffff);
		}
		List<int[]> out = new ArrayList<>();
		for (int c : own) {
			int k = karn.applyAsInt(c);
			if (k < 0 || k == c)
				continue;
			if (own.contains(k))
				throw new IllegalStateException("replacement " + k + " is also a colour of models " + models);
			out.add(new int[] { c, k });
		}
		return out;
	}

	private static void recolourOps(ByteArrayOutputStream b, List<int[]> pairs, int[][] retex) {
		if (!pairs.isEmpty()) {
			b.write(40);
			b.write(pairs.size());
			for (int[] p : pairs) {
				b.write(p[0] >> 8);
				b.write(p[0] & 0xff);
				b.write(p[1] >> 8);
				b.write(p[1] & 0xff);
			}
		}
		if (retex != null) {
			b.write(41);
			b.write(retex.length);
			for (int[] p : retex) {
				b.write(p[0] >> 8);
				b.write(p[0] & 0xff);
				b.write(p[1] >> 8);
				b.write(p[1] & 0xff);
			}
		}
	}

	private static byte[] loc(Store s, byte[] src, DefRewriter.ParamKinds pk, String name, IntUnaryOperator karn, int[][] retex) throws Exception {
		List<Integer> models = new ArrayList<>();
		for (byte[] seg : DefRewriter.segments("obj", src, DefRewriter.IDENTITY, pk))
			if ((seg[0] & 0xff) == 7)
				for (int k = 0; k < (seg[1] & 0xff); k++)
					models.add(int32(seg, 2 + k * 4));
			else if ((seg[0] & 0xff) == 6)
				for (int k = 0; k < (seg[1] & 0xff); k++)
					models.add(int32(seg, 2 + k * 5));
		ByteArrayOutputStream b = new ByteArrayOutputStream();
		string(b, 2, name);
		recolourOps(b, pairs(s, models, karn), retex);
		for (byte[] seg : DefRewriter.segments("obj", src, DefRewriter.IDENTITY, pk)) {
			int op = seg[0] & 0xff;
			if (op == 0)
				break;
			if (op == 2 || op == 40 || op == 41)
				continue;
			b.writeBytes(seg);
		}
		b.write(0);
		return b.toByteArray();
	}

	private static byte[] item(Store s, byte[] src, DefRewriter.ParamKinds pk, String name, String desc, IntUnaryOperator karn, int[][] retex) throws Exception {
		List<Integer> models = new ArrayList<>();
		for (byte[] seg : DefRewriter.segments("item", src, DefRewriter.IDENTITY, pk))
			if ((seg[0] & 0xff) == 44)
				models.add(int32(seg, 1));
		ByteArrayOutputStream b = new ByteArrayOutputStream();
		recolourOps(b, pairs(s, models, karn), retex);
		for (byte[] seg : DefRewriter.segments("item", src, DefRewriter.IDENTITY, pk)) {
			int op = seg[0] & 0xff;
			if (op == 0)
				break;
			switch (op) {
				case 40, 41, 97, 98, 148, 149 -> {
				} // ours above; no noted form, no placeholder
				case 2 -> string(b, 2, name);
				case 3 -> string(b, 3, desc);
				default -> b.writeBytes(seg);
			}
		}
		b.write(0);
		return b.toByteArray();
	}
}

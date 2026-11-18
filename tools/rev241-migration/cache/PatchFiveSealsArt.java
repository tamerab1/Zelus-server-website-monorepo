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
 * Stage 52 of the rev 241 cache build (user 2026-11-16: "all this minigame should be custom unique with custom designs"):
 * The Five Seals' own creatures and objects, recoloured copies in the Karn crimson and gold (stage 51 has the Karnwood /
 * Bloodgold spots and the Crimson Ankou).
 * <ul>
 * <li>npcs: 30262 Seal Guardian (ice warrior 2841: crimson plate, gold trim), 30263 Warden of Carnage (demonic gorilla
 * 7144: crimson-black hide, gold demon glow), 30264 Arcane Warden (wizard 3257: crimson robes, gold trims), 30265 Shadow
 * Stalker (shadow hound 3449: crimson-black, burning eyes), 30266 The Sovereign (tormented demon 13599: crimson hide, gold
 * fire and horns). Stats / animations stay the base's; the server copies the base's combat data.</li>
 * <li>objects: 63311 / 63312 Arcane conduit dormant / lit (wilderness obelisk 14829), 63313 / 63314 Shadow node active /
 * neutralised (same obelisk), 63315 Seal supply altar (prayer altar 409), 63316 Sovereign's vault (Grand Gold Chest
 * 20946), 63317 Seal altar (the rooms' altar 39642; stage 53 puts it in the map), 63319 Shadow bars (portcullis
 * 3303 at 0.7x: Seal 4's walkway bars, spawned while they're down).</li>
 * </ul>
 * A replacement colour never equals one of the model's own colours (nudged one lightness step if it would). Object ids stay
 * below 65536. Idempotent. Usage: PatchFiveSealsArt apply &lt;cache241&gt;
 */
public final class PatchFiveSealsArt {

	private PatchFiveSealsArt() {
	}

	static int hsl(int h, int s, int l) {
		return h << 10 | s << 7 | Math.max(1, Math.min(126, l));
	}

	static int H(int c) {
		return c >> 10;
	}

	static int S(int c) {
		return c >> 7 & 7;
	}

	static int L(int c) {
		return c & 127;
	}


	public static void main(String[] a) throws Exception {
		if (a.length != 2 || !a[0].equals("apply"))
			throw new IllegalArgumentException("apply <cache241>");
		Store dst = new Store(new File(a[1]));
		dst.load();
		DefRewriter.ParamKinds pk = DefRewriter.paramKinds(RoundTripDefs.files(dst, 11));
		int changed = 0;

		/* ---------------------------------------------------------------- npcs */
		Map<Integer, byte[]> npcs = RoundTripDefs.files(dst, 9);
		Map<Integer, byte[]> putNpcs = new TreeMap<>();
		npc(dst, npcs, pk, putNpcs, 30262, 2841, "Seal Guardian", c -> {
			if (H(c) == 0 && S(c) == 0)
				return hsl(0, 6, L(c) * 6 / 10 + 4);  // ice-grey plate -> crimson plate
			return hsl(8, 6, 46);                    // skin / trims -> gold
		}, null);
		npc(dst, npcs, pk, putNpcs, 30263, 7144, "Warden of Carnage", c -> {
			int h = H(c), l = L(c);
			if (h == 0 && S(c) == 0)
				return l < 50 ? hsl(0, 3, l) : hsl(8, 3, l - 12); // black fur -> crimson-black; pale -> pale gold
			if (h >= 2 && h <= 7)
				return hsl(8, 4, l - 18);           // pale hide / bone -> dull gold
			if (h == 10)
				return hsl(0, 5, l + 2);            // brown hide -> dark crimson
			if (h == 19 || h == 21)
				return hsl(8, 7, l + 12);           // demonic green glow -> gold glow
			return -1;
		}, null);
		// the wizard's own recolour, kept in order (it chains), with Karn targets
		npc(dst, npcs, pk, putNpcs, 30264, 3257, "Arcane Warden", null, new int[][] {
			{ hsl(8, 4, 37), hsl(0, 7, 24) },        // robe -> crimson
			{ hsl(24, 5, 22), hsl(0, 7, 18) },       // hat -> dark crimson
			{ hsl(6, 5, 14), 127 },                  // beard -> white (as the wizard)
			{ 123, hsl(8, 7, 50) },                  // trims -> gold
			{ 127, 123 },
			{ 109, 16 },
			{ hsl(37, 7, 30), hsl(8, 6, 40) },       // the staff / sash -> gold
		});
		npc(dst, npcs, pk, putNpcs, 30265, 3449, "Shadow Stalker", c -> {
			if (H(c) == 5 && S(c) == 1)
				return hsl(0, 3, L(c) / 3 + 1);      // grey-brown hide -> crimson-black
			if (H(c) == 5 && S(c) == 7)
				return hsl(0, 7, 46);                // the eyes -> burning crimson
			return -1;
		}, null);
		npc(dst, npcs, pk, putNpcs, 30266, 13599, "The Sovereign", c -> {
			int h = H(c), s = S(c), l = L(c);
			if ((h == 4 || h == 6 || h == 10 || h == 19) && s >= 6)
				return hsl(8, 6, l - 8);             // fire -> gold fire
			if (h == 7)
				return hsl(0, 4, l - 6);             // dull hide -> dark crimson
			if (h == 8 && l >= 60)
				return hsl(8, 4, l - 14);            // bone horns -> pale gold
			if (h == 2)
				return hsl(0, 6, l);                 // dark orange -> crimson
			if (h == 62)
				return hsl(8, 5, 58);
			return -1;
		}, null);
		if (!putNpcs.isEmpty())
			changed += PatchCustoms27.putConfigs(dst, 9, putNpcs);

		/* ---------------------------------------------------------------- objects */
		Map<Integer, byte[]> locs = RoundTripDefs.files(dst, PatchColosseum.LOC_GROUP);
		Map<Integer, byte[]> putLocs = new TreeMap<>();
		// the wilderness obelisk: greys l24 / l37 = the body, l41-l53 = the carved top and runes
		loc(dst, locs, pk, putLocs, 63311, 14829, "Arcane conduit", new String[] { "Channel" },
			c -> L(c) <= 37 ? hsl(42, 3, L(c) - 4) : hsl(42, 4, L(c) - 22));      // dark navy stone, dim runes
		loc(dst, locs, pk, putLocs, 63312, 14829, "Arcane conduit", new String[] { "Channel" },
			c -> L(c) <= 37 ? hsl(8, 5, L(c) + 14) : hsl(8, 7, L(c) + 38));       // lit: the whole obelisk glows gold
		loc(dst, locs, pk, putLocs, 63313, 14829, "Shadow node", new String[] { "Channel" },
			c -> L(c) <= 37 ? hsl(0, 2, L(c) - 14) : hsl(0, 7, L(c) - 10));       // black-crimson, burning runes
		loc(dst, locs, pk, putLocs, 63314, 14829, "Shadow node", new String[] { "Inspect" },
			c -> L(c) <= 37 ? hsl(8, 4, L(c) + 8) : hsl(8, 7, L(c) + 30));        // neutralised: gold stone
		loc(dst, locs, pk, putLocs, 63315, 409, "Seal supply altar", new String[] { "Pray-at" }, c -> {
			if (H(c) == 0 && S(c) == 0)
				return L(c) > 100 ? hsl(0, 7, 30) : -1; // the white cloth -> crimson
			if (H(c) == 42)
				return hsl(0, 2, L(c) - 22);         // grey stone -> dark red stone
			return -1;                               // the gold stays
		});
		loc(dst, locs, pk, putLocs, 63316, 20946, "Sovereign's vault", new String[] { "Claim" }, c -> {
			if (H(c) == 42)
				return hsl(0, 7, 28);                // the grey bands -> crimson
			return -1;                               // the gold stays
		});
		loc(dst, locs, pk, putLocs, 63317, 39642, "Seal altar", new String[] { "Pray-at" }, c -> {
			if (H(c) == 0 && S(c) == 0)
				return hsl(0, 3, L(c) - 4);          // grey stone -> dark crimson stone
			if (H(c) == 8)
				return hsl(8, 6, 46);                // the metal -> gold
			return -1;
		});
		// round 3: Seal 4's walkway bars, spawned while the cycle has them down (portcullis 3303, 2 x 4 tiles, at 0.7x)
		loc(dst, locs, pk, putLocs, 63319, 3303, "Shadow bars", new String[0], c -> {
			if (H(c) == 0 && S(c) == 0)
				return L(c) < 30 ? hsl(50, 3, 12) : hsl(0, 7, 38);   // violet-black bars, crimson spikes
			return -1;
		});
		putLocs.computeIfPresent(63319, (k, v) -> scaled(v, 90));
		// the vault: a Grand Gold Chest at 1.75x (ops 65 / 66 / 67 = scale x / height / y, 128 = 1x)
		putLocs.computeIfPresent(63316, (k, v) -> scaled(v, 224));
		putLocs.entrySet().removeIf(e -> Arrays.equals(e.getValue(), locs.get(e.getKey())));
		if (!putLocs.isEmpty())
			changed += PatchCustoms27.putConfigs(dst, PatchColosseum.LOC_GROUP, putLocs);
		dst.close();
		System.out.println("RESULT five seals art: " + changed + " change(s); npcs 30262-30266, objects 63311-63317");
	}

	/* ---------------------------------------------------------------- helpers */

	/** a loc record with ops 65-67 (model scale) inserted before its terminator */
	static byte[] scaled(byte[] def, int scale) {
		ByteArrayOutputStream b = new ByteArrayOutputStream();
		b.write(def, 0, def.length - 1);
		for (int op = 65; op <= 67; op++) {
			b.write(op);
			b.write(scale >> 8);
			b.write(scale & 0xff);
		}
		b.write(0);
		return b.toByteArray();
	}

	static int i32(byte[] b, int o) {
		return (b[o] & 0xff) << 24 | (b[o + 1] & 0xff) << 16 | (b[o + 2] & 0xff) << 8 | b[o + 3] & 0xff;
	}

	static void string(ByteArrayOutputStream b, int op, String s) {
		b.write(op);
		b.writeBytes(s.getBytes(StandardCharsets.ISO_8859_1));
		b.write(0);
	}

	static Set<Integer> colours(Store s, Collection<Integer> models) throws Exception {
		TreeSet<Integer> own = new TreeSet<>();
		for (int model : models) {
			Archive ar = s.findIndex(7).getArchive(model);
			ModelDefinition md = new ModelLoader().load(model, ar.decompress(s.getStorage().loadArchive(ar)));
			for (int f = 0; f < md.faceCount; f++)
				if (md.faceTextures == null || md.faceTextures[f] == -1)
					own.add(md.faceColors[f] & 0xffff);
		}
		return own;
	}

	/** the model's colours -> recolour pairs; a target that is one of the model's own colours is nudged a step lighter */
	static List<int[]> pairs(Set<Integer> own, IntUnaryOperator karn) {
		List<int[]> out = new ArrayList<>();
		for (int c : own) {
			int k = karn.applyAsInt(c);
			if (k < 0 || k == c)
				continue;
			while (own.contains(k))
				k = (k & ~127) | Math.min(126, (k & 127) + 1);
			out.add(new int[] { c, k });
		}
		if (out.size() > 255)
			throw new IllegalStateException("too many colours");
		return out;
	}

	static void recolour(ByteArrayOutputStream b, List<int[]> pairs) {
		if (pairs.isEmpty())
			return;
		b.write(40);
		b.write(pairs.size());
		for (int[] p : pairs) {
			b.write(p[0] >> 8);
			b.write(p[0] & 0xff);
			b.write(p[1] >> 8);
			b.write(p[1] & 0xff);
		}
	}

	static void check(Map<Integer, byte[]> defs, int id) {
		byte[] have = defs.get(id);
		if (have != null && !ours(have))
			throw new IllegalStateException("id " + id + " is taken by something else");
	}

	/** our records, recognised by their names */
	static boolean ours(byte[] def) {
		String s = new String(def, StandardCharsets.ISO_8859_1);
		for (String n : new String[] { "Seal Guardian", "Warden of Carnage", "Arcane Warden", "Shadow Stalker", "The Sovereign",
			"Arcane conduit", "Shadow node", "Seal supply altar", "Sovereign's vault", "Seal altar", "Shadow bars" })
			if (s.contains(n))
				return true;
		return false;
	}

	static void npc(Store s, Map<Integer, byte[]> npcs, DefRewriter.ParamKinds pk, Map<Integer, byte[]> put, int id, int base,
		String name, IntUnaryOperator karn, int[][] fixed) throws Exception {
		check(npcs, id);
		byte[] src = npcs.get(base);
		if (src == null)
			throw new IllegalStateException("npc " + base + " missing");
		List<Integer> models = new ArrayList<>();
		for (byte[] seg : DefRewriter.segments("npc", src, DefRewriter.IDENTITY, pk))
			if ((seg[0] & 0xff) == 61)
				for (int k = 0; k < (seg[1] & 0xff); k++)
					models.add(i32(seg, 2 + k * 4));
		List<int[]> pairs = fixed != null ? Arrays.asList(fixed) : pairs(colours(s, models), karn);
		ByteArrayOutputStream b = new ByteArrayOutputStream();
		boolean named = false;
		for (byte[] seg : DefRewriter.segments("npc", src, DefRewriter.IDENTITY, pk)) {
			int op = seg[0] & 0xff;
			if (op == 0)
				break;
			if (op == 40 || op == 106 || op == 118)
				continue;                            // ours below; no transforms
			if (op == 2) {
				string(b, 2, name);
				named = true;
				continue;
			}
			b.writeBytes(seg);
		}
		if (!named)
			string(b, 2, name);
		recolour(b, pairs);
		b.write(0);
		byte[] def = b.toByteArray();
		if (!Arrays.equals(def, npcs.get(id)))
			put.put(id, def);
		System.out.println("  npc " + id + " " + name + " (from " + base + "): " + pairs.size() + " colours");
	}

	static void loc(Store s, Map<Integer, byte[]> locs, DefRewriter.ParamKinds pk, Map<Integer, byte[]> put, int id, int base,
		String name, String[] options, IntUnaryOperator karn) throws Exception {
		check(locs, id);
		byte[] src = locs.get(base);
		if (src == null)
			throw new IllegalStateException("object " + base + " missing");
		List<Integer> models = new ArrayList<>();
		for (byte[] seg : DefRewriter.segments("obj", src, DefRewriter.IDENTITY, pk)) {
			int op = seg[0] & 0xff;
			if (op == 7)
				for (int k = 0; k < (seg[1] & 0xff); k++)
					models.add(i32(seg, 2 + k * 4));
			else if (op == 6)
				for (int k = 0; k < (seg[1] & 0xff); k++)
					models.add(i32(seg, 2 + k * 5));
		}
		List<int[]> pairs = pairs(colours(s, models), karn);
		ByteArrayOutputStream b = new ByteArrayOutputStream();
		string(b, 2, name);
		for (int i = 0; i < options.length; i++)
			string(b, 30 + i, options[i]);
		recolour(b, pairs);
		for (byte[] seg : DefRewriter.segments("obj", src, DefRewriter.IDENTITY, pk)) {
			int op = seg[0] & 0xff;
			if (op == 0)
				break;
			if (op == 2 || op == 40 || op >= 30 && op <= 34 || op == 77 || op == 92)
				continue;                            // ours above; no transforms
			b.writeBytes(seg);
		}
		b.write(0);
		put.put(id, b.toByteArray()); // unchanged records are dropped by the caller
		System.out.println("  object " + id + " " + name + " (from " + base + "): " + pairs.size() + " colours");
	}
}

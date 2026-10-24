import java.util.*;

/**
 * Three-way merge of one config definition at opcode level.
 *   base   = vanilla rev 231 (what our cache started from)
 *   ours   = our cache, ids already remapped through the manifest
 *   theirs = vanilla rev 241
 * A field only we changed takes our value; one only Jagex changed takes theirs; both changed to
 * different values = conflict (ours wins, reported). Opcodes that set the same field are one
 * key (e.g. item model u16 op 1 / i32 op 44); params (249) merge per param key.
 */
public final class ConfigMerge {

	public static final class Result {
		public byte[] bytes;
		public final List<String> conflicts = new ArrayList<>();
		public final Set<String> oursChanged = new TreeSet<>();
		public final Set<String> artifacts = new TreeSet<>();
		public boolean theirsMissing;
	}

	// ---- packer artifacts
	//
	// Our cache was rebuilt by the toml packer, which re-encoded vanilla definitions lossily.
	// These differences were confirmed by sampling base/ours/241 bytes (2026-10-25) and are not
	// Zelus edits, so the merge takes the vanilla rev 241 value for them:
	//  item: examine (3), weight (75), wearpos (13/14/27) dropped; explicit default "Drop" (39) added
	//  obj : clip type 1 + no-projectile-block (27+18) collapsed into op 17 (clip type 0);
	//        offsetHeight -1 (71) dropped; door flag (19) rewritten; ambient (29) shifted by +10;
	//        ambient sound fields (78/79) written in the wrong order
	//  seq : frame sound weight/loops (14) written in swapped order
	//  enum/param: explicit default int type char ('i', ops 1/2) added
	static final byte[] DROP_OP = {39, 'D', 'r', 'o', 'p', 0};

	static boolean isPackerArtifact(String type, String key, byte[] base, byte[] ours) {
		String k = key.replaceAll("#\\d+$", "");
		switch (type) {
			case "item":
				if (ours == null && Set.of("op3", "op75", "op13", "op14", "op27").contains(k)) return true;
				if (k.equals("op39") && base == null && Arrays.equals(ours, DROP_OP)) return true;
				return false;
			case "obj":
				if (k.equals("clip") || k.equals("op18") || k.equals("op19")) return true;
				if (k.equals("op71") && ours == null) return true;
				if (k.equals("op29") && base != null && ours != null && (ours[1] & 0xff) - (base[1] & 0xff) == 10) return true;
				if ((k.equals("op78") || k.equals("op79")) && base != null && ours != null && sameBytesUnordered(base, ours)) return true;
				return false;
			case "seq":
				return k.equals("op14") && base != null && ours != null && Arrays.equals(base, swapSoundWeightLoops(ours));
			case "enum":
				return (k.equals("op1") || k.equals("op2")) && base == null && ours != null && ours.length == 2 && ours[1] == 'i';
			case "param":
				return k.equals("op1") && base == null && ours != null && ours.length == 2 && ours[1] == 'i';
			default:
				return false;
		}
	}

	static boolean sameBytesUnordered(byte[] a, byte[] b) {
		if (a.length != b.length) return false;
		byte[] x = a.clone(), y = b.clone();
		Arrays.sort(x);
		Arrays.sort(y);
		return Arrays.equals(x, y);
	}

	/** seq op14 entries are (u16 frame, u16 sound, u8 weight, u8 loops, u8 location, u8 retain). */
	static byte[] swapSoundWeightLoops(byte[] seg) {
		byte[] s = seg.clone();
		int n = ((s[1] & 0xff) << 8) | (s[2] & 0xff);
		for (int i = 0; i < n; i++) {
			int o = 3 + i * 8 + 4;
			byte t = s[o];
			s[o] = s[o + 1];
			s[o + 1] = t;
		}
		return s;
	}

	// ---- field keys

	static String fieldKey(String type, byte[] seg, Map<String, Integer> seen) {
		int op = seg[0] & 0xff;
		String k = switch (type) {
			case "item" -> switch (op) {
				case 1, 44 -> "model"; case 23, 45 -> "maleModel0"; case 24, 46 -> "maleModel1"; case 78, 47 -> "maleModel2";
				case 25, 48 -> "femaleModel0"; case 26, 49 -> "femaleModel1"; case 79, 50 -> "femaleModel2";
				case 90, 51 -> "maleHead0"; case 92, 52 -> "maleHead1"; case 91, 53 -> "femaleHead0"; case 93, 54 -> "femaleHead1";
				case 11, 160 -> "stackable";
				case 43 -> "subops:" + (seg[1] & 0xff);
				case 200 -> "subop:" + (seg[1] & 0xff) + ":" + (seg[2] & 0xff);
				case 201 -> "cond:" + (seg[1] & 0xff);
				case 202 -> "condsub:" + (seg[1] & 0xff) + ":" + (((seg[2] & 0xff) << 8) | (seg[3] & 0xff));
				default -> "op" + op;
			};
			case "npc" -> switch (op) {
				case 1, 61 -> "models"; case 60, 62 -> "heads"; case 14, 17 -> "walk"; case 114, 115 -> "run"; case 116, 117 -> "crawl";
				case 106, 118 -> "multi";
				case 251 -> "subop:" + (seg[1] & 0xff) + ":" + (seg[2] & 0xff);
				case 252 -> "cond:" + (seg[1] & 0xff);
				case 253 -> "condsub:" + (seg[1] & 0xff) + ":" + (((seg[2] & 0xff) << 8) | (seg[3] & 0xff));
				default -> "op" + op;
			};
			case "obj" -> switch (op) {
				case 1, 5, 6, 7 -> "models"; case 77, 92 -> "multi"; case 17, 27 -> "clip";
				case 100 -> "subop:" + (seg[1] & 0xff) + ":" + (seg[2] & 0xff);
				case 101 -> "cond:" + (seg[1] & 0xff);
				case 102 -> "condsub:" + (seg[1] & 0xff) + ":" + (((seg[2] & 0xff) << 8) | (seg[3] & 0xff));
				default -> "op" + op;
			};
			case "spotanim" -> (op == 1 || op == 3) ? "model" : "op" + op;
			case "kit" -> (op == 2 || op == 5) ? "models" : (op >= 60 && op < 80) ? "head" + ((op - 60) % 10) : "op" + op;
			default -> "op" + op;
		};
		// conditional ops on the same index form a list, so they keep one key each; any other field
		// written twice is overwritten by the client (last write wins), so it keeps a single key
		if (!k.startsWith("cond")) return k;
		int n = seen.merge(k, 1, Integer::sum);
		return n == 1 ? k : k + "#" + n;
	}

	/**
	 * Value used to decide whether two segments of the same field are equal. Model fields compare
	 * by decoded model ids (Jagex re-encodes u16 model opcodes as i32 ones between revisions).
	 */
	static String canonical(String type, String key, byte[] seg) {
		if (seg == null) return null;
		int op = seg[0] & 0xff;
		boolean wide;
		boolean list;
		boolean shapes = false;
		switch (type) {
			case "item" -> {
				if (op >= 44 && op <= 54) { wide = true; list = false; }
				else if (Set.of(1, 23, 24, 25, 26, 78, 79, 90, 91, 92, 93).contains(op)) { wide = false; list = false; }
				else return hexOf(seg);
				int v = wide ? i32(seg, 1) : u16(seg, 1);
				String extra = (op == 23 || op == 25 || op == 45 || op == 48) ? "+" + (seg[seg.length - 1] & 0xff) : "";
				return "model" + v + extra;
			}
			case "npc" -> {
				if (op == 1 || op == 60) { wide = false; list = true; }
				else if (op == 61 || op == 62) { wide = true; list = true; }
				else return hexOf(seg);
			}
			case "obj" -> {
				if (op == 1 || op == 5) { wide = false; list = true; shapes = op == 1; }
				else if (op == 6 || op == 7) { wide = true; list = true; shapes = op == 6; }
				else return hexOf(seg);
			}
			case "spotanim" -> {
				if (op == 1) return "model" + u16(seg, 1);
				if (op == 3) return "model" + i32(seg, 1);
				return hexOf(seg);
			}
			default -> { return hexOf(seg); }
		}
		StringBuilder sb = new StringBuilder("models");
		int n = seg[1] & 0xff, p = 2;
		for (int i = 0; i < n; i++) {
			int v = wide ? i32(seg, p) : u16(seg, p);
			p += wide ? 4 : 2;
			sb.append(',').append(v);
			if (shapes) sb.append(':').append(seg[p++] & 0xff);
			else if (type.equals("obj")) sb.append(":10"); // ops 5/7 imply the centrepiece shape
		}
		return sb.toString();
	}

	static int u16(byte[] b, int p) { return ((b[p] & 0xff) << 8) | (b[p + 1] & 0xff); }
	static int i32(byte[] b, int p) { return ((b[p] & 0xff) << 24) | ((b[p + 1] & 0xff) << 16) | ((b[p + 2] & 0xff) << 8) | (b[p + 3] & 0xff); }
	static String hexOf(byte[] b) { return HexFormat.of().formatHex(b); }

	static boolean same(String type, String key, byte[] a, byte[] b) {
		return Objects.equals(canonical(type, key, a), canonical(type, key, b));
	}

	/** key -> segment, preserving order; params (249) expand to one key per param id. */
	static LinkedHashMap<String, byte[]> keyed(String type, List<byte[]> segs) {
		LinkedHashMap<String, byte[]> m = new LinkedHashMap<>();
		Map<String, Integer> seen = new HashMap<>();
		for (byte[] s : segs) {
			if ((s[0] & 0xff) == 249) {
				for (var e : paramEntries(s).entrySet()) m.put("param:" + e.getKey(), e.getValue());
			} else if (type.equals("enum") && (s[0] & 0xff) >= 5 && (s[0] & 0xff) <= 7) {
				// enum tables merge per key: "tbl<op>:<key>" -> entry bytes (i32 key + value)
				int op = s[0] & 0xff, n = u16(s, 1), p = 3;
				for (int i = 0; i < n; i++) {
					int start = p, key = i32(s, p);
					p += 4;
					if (op == 5) { while (s[p++] != 0) { } }
					else p += op == 6 ? 4 : 8;
					m.put("tbl" + op + ":" + key, Arrays.copyOfRange(s, start, p));
				}
			} else {
				m.put(fieldKey(type, s, seen), s);
			}
		}
		return m;
	}

	/** Splits a 249 segment into param id -> entry bytes (type, key, value). */
	static LinkedHashMap<Integer, byte[]> paramEntries(byte[] s) {
		LinkedHashMap<Integer, byte[]> m = new LinkedHashMap<>();
		int p = 1, n = s[p++] & 0xff;
		for (int i = 0; i < n; i++) {
			int start = p, type = s[p++] & 0xff;
			int key = ((s[p] & 0xff) << 16) | ((s[p + 1] & 0xff) << 8) | (s[p + 2] & 0xff);
			p += 3;
			if (type == 0) p += 4;
			else if (type == 1) { while (s[p++] != 0) { } }
			else if (type == 2) p += 8;
			m.put(key, Arrays.copyOfRange(s, start, p));
		}
		return m;
	}

	static byte[] assemble(LinkedHashMap<String, byte[]> fields) {
		java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
		List<byte[]> params = new ArrayList<>();
		TreeMap<Integer, List<byte[]>> tables = new TreeMap<>();
		for (var e : fields.entrySet()) {
			if (e.getKey().startsWith("param:")) params.add(e.getValue());
			else if (e.getKey().startsWith("tbl")) tables.computeIfAbsent(e.getKey().charAt(3) - '0', x -> new ArrayList<>()).add(e.getValue());
			else out.writeBytes(e.getValue());
		}
		for (var t : tables.entrySet()) {
			out.write(t.getKey());
			out.write(t.getValue().size() >> 8);
			out.write(t.getValue().size());
			for (byte[] entry : t.getValue()) out.writeBytes(entry);
		}
		if (!params.isEmpty()) {
			out.write(249);
			out.write(params.size());
			for (byte[] p : params) out.writeBytes(p);
		}
		out.write(0);
		return out.toByteArray();
	}

	public static Result merge(String type, byte[] base, byte[] ours, byte[] theirs, DefRewriter.Ids manifest, DefRewriter.ParamKinds pk) {
		Result r = new Result();
		var b = keyed(type, DefRewriter.segments(type, base, DefRewriter.IDENTITY, pk));
		var o = keyed(type, DefRewriter.segments(type, ours, manifest, pk));
		if (theirs == null) {
			r.theirsMissing = true;
			theirs = base;
		}
		var t = keyed(type, DefRewriter.segments(type, theirs, DefRewriter.IDENTITY, pk));

		Set<String> keys = new LinkedHashSet<>();
		keys.addAll(t.keySet());
		keys.addAll(o.keySet());
		keys.addAll(b.keySet());
		LinkedHashMap<String, byte[]> out = new LinkedHashMap<>();
		for (String k : keys) {
			byte[] bv = b.get(k), ov = o.get(k), tv = t.get(k);
			boolean oursChanged = !same(type, k, bv, ov) && !isPackerArtifact(type, k, bv, ov);
			if (!same(type, k, bv, ov) && !oursChanged) r.artifacts.add(k);
			boolean theirsChanged = !same(type, k, bv, tv);
			byte[] pick;
			if (!oursChanged) pick = tv;
			else {
				r.oursChanged.add(k.replaceAll("#\\d+$", ""));
				if (theirsChanged && !same(type, k, ov, tv)) r.conflicts.add(k);
				pick = ov;
			}
			if (pick != null) out.put(k, pick);
		}
		r.bytes = assemble(out);
		return r;
	}

	/** Same definition semantically? (field-keyed equality, order-insensitive) */
	public static boolean sameFields(String type, byte[] a, byte[] b, DefRewriter.ParamKinds pk) {
		var x = keyed(type, DefRewriter.segments(type, a, DefRewriter.IDENTITY, pk));
		var y = keyed(type, DefRewriter.segments(type, b, DefRewriter.IDENTITY, pk));
		if (!x.keySet().equals(y.keySet())) return false;
		for (var e : x.entrySet()) if (!same(type, e.getKey(), e.getValue(), y.get(e.getKey()))) return false;
		return true;
	}
}

import java.io.ByteArrayOutputStream;
import java.util.Map;

/**
 * Byte-level rewriter for config definitions: walks each definition's opcode stream with the
 * exact rev 241 grammar (ported from abextm/cache2 at its "rev241" commit, plus RuneLite 1.13.4's
 * loaders for spotanim / varbit / inv / overlay / kit), copies every byte, and passes only the id
 * fields through {@link Ids}. With an identity mapping the output must equal the input byte for
 * byte -- that round trip over every definition of both caches is what proves the grammar.
 *
 * Rev 231 and rev 241 share these grammars (both caches are past the configs-index revisions
 * 4106 / 4470 where the sound / animation opcodes changed).
 */
public final class DefRewriter {

	/** Maps an id of a given kind ("item", "model", "seq", ...) to its new value. */
	public interface Ids {
		int map(String kind, int id);
	}

	public static final Ids IDENTITY = (kind, id) -> id;

	/** Param id -> remap kind of its int values (null = not an id). */
	public interface ParamKinds {
		String kindOf(int paramId);
	}

	private final Ids ids;
	private final ParamKinds params;
	private In in;
	private final Out out = new Out();

	private DefRewriter(byte[] data, Ids ids, ParamKinds params) {
		this.in = new In(data);
		this.ids = ids;
		this.params = params;
	}

	public static byte[] rewrite(String type, byte[] data, Ids ids, ParamKinds params) {
		return rewriteWith(new DefRewriter(data, ids, params), type, data);
	}

	private static byte[] rewriteWith(DefRewriter r, String type, byte[] data) {
		switch (type) {
			case "item" -> r.item();
			case "npc" -> r.npc();
			case "obj" -> r.obj();
			case "seq" -> r.seq();
			case "spotanim" -> r.spotanim();
			case "varbit" -> r.varbit();
			case "enum" -> r.enumDef();
			case "struct", "inv" -> r.paramsOnly(type);
			case "param" -> r.param();
			case "overlay" -> r.overlay();
			case "kit" -> r.kit();
			default -> throw new IllegalArgumentException("no grammar for " + type);
		}
		if (r.in.pos != data.length) {
			throw new IllegalStateException(type + ": " + (data.length - r.in.pos) + " bytes left over");
		}
		return r.out.toByteArray();
	}

	// ---------------------------------------------------------------- helpers

	private final java.util.List<Integer> opStarts = new java.util.ArrayList<>();

	private int op() {
		opStarts.add(out.size());
		int op = in.u8();
		out.u8(op);
		return op;
	}

	/**
	 * Rewrites like {@link #rewrite} but returns one byte[] per opcode (opcode byte + payload),
	 * without the 0 terminator -- the unit a three-way merge compares.
	 */
	public static java.util.List<byte[]> segments(String type, byte[] data, Ids ids, ParamKinds params) {
		DefRewriter r = new DefRewriter(data, ids, params);
		byte[] all = rewriteWith(r, type, data);
		java.util.List<byte[]> segs = new java.util.ArrayList<>();
		for (int i = 0; i < r.opStarts.size() - 1; i++) {
			segs.add(java.util.Arrays.copyOfRange(all, r.opStarts.get(i), r.opStarts.get(i + 1)));
		}
		return segs; // the last start is the terminator
	}

	private void copy(int n) {
		for (int i = 0; i < n; i++) out.u8(in.u8());
	}

	private void string() {
		int c;
		do {
			c = in.u8();
			out.u8(c);
		} while (c != 0);
	}

	/** u16 id; 0xFFFF (= -1) is passed through untouched. */
	private void id16(String kind) {
		int v = in.u16();
		out.u16(v == 0xFFFF ? v : check16(kind, ids.map(kind, v)));
	}

	private void model32() {
		out.i32(ids.map("model", in.i32()));
	}

	private static int check16(String kind, int v) {
		if (v < 0 || v > 0xFFFE) throw new IllegalStateException(kind + " id " + v + " does not fit u16");
		return v;
	}

	private void recolor() { // u8 count, (u16 from, u16 to) pairs
		int n = in.u8();
		out.u8(n);
		copy(n * 4);
	}

	private void retexture() { // u8 count, (u16 from, u16 to) texture pairs
		int n = in.u8();
		out.u8(n);
		for (int i = 0; i < n; i++) {
			id16("texture");
			id16("texture");
		}
	}

	private void params() {
		int n = in.u8();
		out.u8(n);
		for (int i = 0; i < n; i++) {
			int type = in.u8();
			out.u8(type);
			int key = in.u24();
			out.u24(key);
			switch (type) {
				case 0 -> {
					int v = in.i32();
					String kind = params.kindOf(key);
					out.i32(kind == null || v == -1 ? v : ids.map(kind, v));
				}
				case 1 -> string();
				case 2 -> copy(8);
				default -> throw new IllegalStateException("bad param type " + type);
			}
		}
	}

	private void conditionalOp(boolean sub) { // index, [subIndex], varp, varbit, min, max, text
		copy(1);
		if (sub) copy(2);
		id16("varp");
		id16("varbit");
		copy(8);
		string();
	}

	private void subOp() { // u8 index, u8 subIndex, string
		copy(2);
		string();
	}

	private void multi(String childKind, boolean oob) { // varbit, varp, [oob child], count+1, children
		id16("varbit");
		id16("varp");
		if (oob) id16(childKind);
		int n = in.u8();
		out.u8(n);
		for (int i = 0; i <= n; i++) id16(childKind);
	}

	// ---------------------------------------------------------------- types

	private void item() {
		for (int op; (op = op()) != 0; ) {
			switch (op) {
				case 1, 24, 26, 78, 79, 90, 91, 92, 93 -> id16("model");
				case 23, 25 -> { id16("model"); copy(1); }
				case 2, 3 -> string();
				case 4, 5, 6, 7, 8, 94, 95, 99, 110, 111, 112 -> copy(2);
				case 75 -> copy(2);
				case 11, 15, 16, 65, 160, 251 -> { }
				case 12 -> copy(4);
				case 13, 14, 27, 42, 113, 114, 115 -> copy(1);
				case 30, 31, 32, 33, 34, 35, 36, 37, 38, 39 -> string();
				case 40 -> recolor();
				case 41 -> retexture();
				case 43 -> {
					copy(1);
					for (int sub; (sub = in.u8()) != 0; ) {
						out.u8(sub);
						string();
					}
					out.u8(0);
				}
				case 44, 46, 47, 49, 50, 51, 52, 53, 54 -> model32();
				case 45, 48 -> { model32(); copy(1); }
				case 97, 98, 139, 140, 148, 149 -> id16("item");
				case 100, 101, 102, 103, 104, 105, 106, 107, 108, 109 -> { id16("item"); copy(2); }
				case 161 -> {
					int n = in.u16();
					out.u16(n);
					for (int i = 0; i < n; i++) id16("seq");
				}
				case 200 -> subOp();
				case 201 -> conditionalOp(false);
				case 202 -> conditionalOp(true);
				case 249 -> params();
				default -> throw new IllegalStateException("item: unknown opcode " + op);
			}
		}
	}

	private void npc() {
		for (int op; (op = op()) != 0; ) {
			switch (op) {
				case 1, 60 -> {
					int n = in.u8();
					out.u8(n);
					for (int i = 0; i < n; i++) id16("model");
				}
				case 61, 62 -> {
					int n = in.u8();
					out.u8(n);
					for (int i = 0; i < n; i++) model32();
				}
				case 2 -> string();
				case 12, 100, 101, 112 -> copy(1);
				case 13, 14, 15, 16, 114, 116 -> id16("seq");
				case 17, 115, 117 -> { id16("seq"); id16("seq"); id16("seq"); id16("seq"); }
				case 18, 42, 74, 75, 76, 77, 78, 79, 95, 97, 98, 103, 124, 126, 146 -> copy(2);
				case 30, 31, 32, 33, 34 -> string();
				case 40 -> recolor();
				case 41 -> retexture();
				case 93, 99, 107, 109, 111, 122, 123, 129, 130, 145, 147 -> { }
				case 102 -> {
					int bits = in.u8();
					out.u8(bits);
					for (int b = bits; b != 0; b >>= 1) {
						if ((b & 1) != 0) {
							bigSmart2();       // sprite group (custom sprites don't collide)
							smartMinusOne();   // sprite index
						}
					}
				}
				case 106 -> multi("npc", false);
				case 118 -> multi("npc", true);
				case 249 -> params();
				case 251 -> subOp();
				case 252 -> conditionalOp(false);
				case 253 -> conditionalOp(true);
				default -> throw new IllegalStateException("npc: unknown opcode " + op);
			}
		}
	}

	private void obj() {
		for (int op; (op = op()) != 0; ) {
			switch (op) {
				case 1, 5 -> {
					int n = in.u8();
					out.u8(n);
					for (int i = 0; i < n; i++) {
						id16("model");
						if (op == 1) copy(1);
					}
				}
				case 6, 7 -> {
					int n = in.u8();
					out.u8(n);
					for (int i = 0; i < n; i++) {
						model32();
						if (op == 6) copy(1);
					}
				}
				case 2 -> string();
				case 14, 15, 19, 28, 29, 39, 69, 75, 81, 91, 95, 96 -> copy(1);
				case 17, 18, 21, 22, 23, 27, 62, 64, 73, 74, 89, 90, 94 -> { }
				case 24 -> id16("seq");
				case 30, 31, 32, 33, 34 -> string();
				case 40 -> recolor();
				case 41 -> retexture();
				case 42, 60, 61, 65, 66, 67, 68, 70, 71, 72, 82 -> copy(2);
				case 77 -> multi("obj", false);
				case 92 -> multi("obj", true);
				case 78 -> { id16("sound"); copy(2); }
				case 79 -> {
					copy(4);
					copy(2);
					int n = in.u8();
					out.u8(n);
					for (int i = 0; i < n; i++) id16("sound");
				}
				case 93 -> copy(6);
				case 100 -> subOp();
				case 101 -> conditionalOp(false);
				case 102 -> conditionalOp(true);
				case 249 -> params();
				default -> throw new IllegalStateException("obj: unknown opcode " + op);
			}
		}
	}

	private void frameIds(int n) { // n x u16 file, then n x u16 frame group
		copy(n * 2);
		for (int i = 0; i < n; i++) id16("frames");
	}

	private void handItem() { // 0 none, >=512 item+512, >=256 kit+256
		int v = in.u16();
		if (v >= 512) out.u16(check16("item", ids.map("item", v - 512) + 512));
		else out.u16(v);
	}

	private void seq() {
		for (int op; (op = op()) != 0; ) {
			switch (op) {
				case 1 -> {
					int n = in.u16();
					out.u16(n);
					copy(n * 2);   // frame lengths
					frameIds(n);
				}
				case 2 -> copy(2);
				case 3 -> {
					int n = in.u8();
					out.u8(n);
					copy(n);
				}
				case 4, 19 -> { }
				case 5, 8, 9, 10, 11, 16 -> copy(1);
				case 6, 7 -> handItem();
				case 12 -> {
					int n = in.u8();
					out.u8(n);
					frameIds(n);
				}
				case 13 -> copy(4);    // animMaya id
				case 14 -> {           // frame sounds: u16 count, (u16 frame, u16 sound, weight, loops, location, retain)
					int n = in.u16();
					out.u16(n);
					for (int i = 0; i < n; i++) {
						copy(2);
						id16("sound");
						copy(4);
					}
				}
				case 15 -> copy(4);
				case 17 -> {
					int n = in.u8();
					out.u8(n);
					copy(n);
				}
				case 18 -> string();
				default -> throw new IllegalStateException("seq: unknown opcode " + op);
			}
		}
	}

	private void spotanim() {
		for (int op; (op = op()) != 0; ) {
			switch (op) {
				case 1 -> id16("model");
				case 2 -> id16("seq");
				case 3 -> model32();
				case 4, 5, 6, 42 -> copy(2);
				case 7, 8 -> copy(1);
				case 9 -> string();
				case 10 -> { }
				case 40 -> recolor();
				case 41 -> retexture();
				default -> throw new IllegalStateException("spotanim: unknown opcode " + op);
			}
		}
	}

	private void varbit() {
		for (int op; (op = op()) != 0; ) {
			if (op == 1) {
				id16("varp");
				copy(2);
			} else {
				throw new IllegalStateException("varbit: unknown opcode " + op);
			}
		}
	}

	private String keyKind, valueKind;

	private void enumDef() {
		for (int op; (op = op()) != 0; ) {
			switch (op) {
				case 1 -> { int c = in.u8(); out.u8(c); keyKind = kindOfChar(c); }
				case 2 -> { int c = in.u8(); out.u8(c); valueKind = kindOfChar(c); }
				case 3 -> string();
				case 4 -> { int v = in.i32(); out.i32(valueKind == null || v == -1 ? v : ids.map(valueKind, v)); }
				case 5, 6, 7 -> {
					int n = in.u16();
					out.u16(n);
					for (int i = 0; i < n; i++) {
						int k = in.i32();
						out.i32(keyKind == null || k == -1 ? k : ids.map(keyKind, k));
						if (op == 5) string();
						else if (op == 7) copy(8);
						else {
							int v = in.i32();
							out.i32(valueKind == null || v == -1 ? v : ids.map(valueKind, v));
						}
					}
				}
				case 8 -> copy(8);
				default -> throw new IllegalStateException("enum: unknown opcode " + op);
			}
		}
	}

	private void paramsOnly(String type) {
		for (int op; (op = op()) != 0; ) {
			switch (op) {
				case 2 -> { if (!type.equals("inv")) throw new IllegalStateException(type + ": opcode 2"); copy(2); }
				case 249 -> params();
				default -> throw new IllegalStateException(type + ": unknown opcode " + op);
			}
		}
	}

	private void param() {
		for (int op; (op = op()) != 0; ) {
			switch (op) {
				case 1, 8 -> copy(1);
				case 2 -> copy(4);
				case 4 -> { }
				case 5 -> string();
				case 7 -> copy(8);
				default -> throw new IllegalStateException("param: unknown opcode " + op);
			}
		}
	}

	/** Overlay texture is a single byte: the caller must keep overlay textures below 256. */
	private void overlay() {
		for (int op; (op = op()) != 0; ) {
			switch (op) {
				case 1, 7 -> copy(3);
				case 2 -> {
					int t = ids.map("texture", in.u8());
					if (t > 255) throw new IllegalStateException("overlay texture " + t + " does not fit u8");
					out.u8(t);
				}
				case 5 -> { }
				default -> throw new IllegalStateException("overlay: unknown opcode " + op);
			}
		}
	}

	private void kit() {
		for (int op; (op = op()) != 0; ) {
			if (op == 1) copy(1);
			else if (op == 2) {
				int n = in.u8();
				out.u8(n);
				for (int i = 0; i < n; i++) id16("model");
			} else if (op == 3) {
				// nonSelectable flag
			} else if (op == 5) {
				int n = in.u8();
				out.u8(n);
				for (int i = 0; i < n; i++) model32();
			} else if (op == 40) recolor();
			else if (op == 41) retexture();
			else if (op >= 60 && op < 70) id16("model");
			else if (op >= 70 && op < 80) model32();
			else throw new IllegalStateException("kit: unknown opcode " + op);
		}
	}

	// script var type char -> remap kind (RuneLite ScriptVarType)
	public static String kindOfChar(int c) {
		return switch (c) {
			case 'A' -> "seq";
			case 'O', 'o' -> "item";
			case 'P' -> "sound";
			case 'l' -> "obj";
			case 'm' -> "model";
			case 'n' -> "npc";
			case 't' -> "spotanim";
			case 'v' -> "inv";
			case 'x' -> "texture";
			case '7' -> "varp";
			default -> null;
		};
	}

	// script var type id (param opcode 8) -> remap kind
	public static String kindOfTypeId(int id) {
		return switch (id) {
			case 6 -> "seq";
			case 13, 33 -> "item";
			case 14 -> "sound";
			case 30 -> "obj";
			case 31 -> "model";
			case 32 -> "npc";
			case 37 -> "spotanim";
			case 39 -> "inv";
			case 40 -> "texture";
			case 209 -> "varp";
			default -> null;
		};
	}

	private void bigSmart2() { // BigSmart2: top bit set -> i32, else u16
		if ((in.peek() & 0x80) != 0) copy(4);
		else copy(2);
	}

	private void smartMinusOne() {
		if (in.peek() < 128) copy(1);
		else copy(2);
	}

	// ---------------------------------------------------------------- io

	static final class In {
		final byte[] b;
		int pos;
		In(byte[] b) { this.b = b; }
		int peek() { return b[pos] & 0xff; }
		int u8() { return b[pos++] & 0xff; }
		int u16() { return (u8() << 8) | u8(); }
		int u24() { return (u8() << 16) | (u8() << 8) | u8(); }
		int i32() { return (u8() << 24) | (u8() << 16) | (u8() << 8) | u8(); }
	}

	static final class Out extends ByteArrayOutputStream {
		void u8(int v) { write(v); }
		void u16(int v) { write(v >> 8); write(v); }
		void u24(int v) { write(v >> 16); write(v >> 8); write(v); }
		void i32(int v) { write(v >> 24); write(v >> 16); write(v >> 8); write(v); }
	}

	/** Builds param id -> remap kind from param definitions (opcode 1 type char / opcode 8 type id). */
	public static ParamKinds paramKinds(Map<Integer, byte[]> paramDefs) {
		Map<Integer, String> kinds = new java.util.HashMap<>();
		for (var e : paramDefs.entrySet()) {
			In r = new In(e.getValue());
			String kind = null;
			for (int op; (op = r.u8()) != 0; ) {
				switch (op) {
					case 1 -> kind = kindOfChar(r.u8());
					case 8 -> kind = kindOfTypeId(r.u8());
					case 2 -> r.pos += 4;
					case 4 -> { }
					case 5 -> { while (r.u8() != 0) { } }
					case 7 -> r.pos += 8;
					default -> throw new IllegalStateException("param " + e.getKey() + ": opcode " + op);
				}
			}
			if (kind != null) kinds.put(e.getKey(), kind);
		}
		return kinds::get;
	}
}

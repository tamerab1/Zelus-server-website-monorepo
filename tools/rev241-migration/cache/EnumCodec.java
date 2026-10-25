import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Enum definition codec (config group 8): 1 keyType char, 2 valType char, 3 default string,
 * 4 default int, 5 string table (u16 n, i32 key, string), 6 int table (u16 n, i32 key, i32 value), 0 end.
 * Tables are ordered entry lists (Jagex enums can repeat a key), so encode(decode(b)) == b for all of them.
 */
public final class EnumCodec {
	public static final class Enum {
		public final List<Integer> order = new ArrayList<>(); // opcodes in source order
		public int keyType, valType, defaultInt; public String defaultString;
		public final List<Map.Entry<Integer, String>> strings = new ArrayList<>();
		public final List<Map.Entry<Integer, Integer>> ints = new ArrayList<>();
		@Override public String toString() {
			return "key=" + (char) keyType + " val=" + (char) valType + (defaultString != null ? " defS=\"" + defaultString + "\"" : "") + (order.contains(4) ? " defI=" + defaultInt : "")
				+ (strings.isEmpty() ? "" : " strings=" + strings) + (ints.isEmpty() ? "" : " ints=" + ints);
		}
	}

	public static Enum decode(byte[] b) {
		Enum e = new Enum();
		DefRewriter.In in = new DefRewriter.In(b);
		for (int op; (op = in.u8()) != 0; ) {
			e.order.add(op);
			switch (op) {
				case 1 -> e.keyType = in.u8();
				case 2 -> e.valType = in.u8();
				case 3 -> e.defaultString = str(in);
				case 4 -> e.defaultInt = in.i32();
				case 5 -> { int n = in.u16(); for (int i = 0; i < n; i++) { int k = in.i32(); e.strings.add(new AbstractMap.SimpleEntry<>(k, str(in))); } }
				case 6 -> { int n = in.u16(); for (int i = 0; i < n; i++) { int k = in.i32(); e.ints.add(new AbstractMap.SimpleEntry<>(k, in.i32())); } }
				default -> throw new IllegalStateException("enum opcode " + op);
			}
		}
		if (in.pos != b.length) throw new IllegalStateException("enum trailing bytes");
		return e;
	}

	/** replaces the value of a key that must occur exactly once and currently hold {@code expect} */
	public static <V> void set(List<Map.Entry<Integer, V>> table, int key, V expect, V value) {
		Map.Entry<Integer, V> hit = null;
		for (var x : table) if (x.getKey() == key) { if (hit != null) throw new IllegalStateException("enum key " + key + " occurs more than once"); hit = x; }
		if (hit == null || !Objects.equals(hit.getValue(), expect)) throw new IllegalStateException("enum key " + key + " is " + (hit == null ? "absent" : hit.getValue()) + ", expected " + expect);
		hit.setValue(value);
	}

	static String str(DefRewriter.In in) {
		ByteArrayOutputStream o = new ByteArrayOutputStream();
		for (int c; (c = in.u8()) != 0; ) o.write(c);
		return new String(o.toByteArray(), StandardCharsets.ISO_8859_1);
	}

	public static byte[] encode(Enum e) {
		DefRewriter.Out o = new DefRewriter.Out();
		for (int op : e.order) {
			o.u8(op);
			switch (op) {
				case 1 -> o.u8(e.keyType);
				case 2 -> o.u8(e.valType);
				case 3 -> s(o, e.defaultString);
				case 4 -> o.i32(e.defaultInt);
				case 5 -> { o.u16(e.strings.size()); for (var x : e.strings) { o.i32(x.getKey()); s(o, x.getValue()); } }
				case 6 -> { o.u16(e.ints.size()); for (var x : e.ints) { o.i32(x.getKey()); o.i32(x.getValue()); } }
			}
		}
		o.u8(0);
		return o.toByteArray();
	}

	static void s(DefRewriter.Out o, String v) { for (byte c : v.getBytes(StandardCharsets.ISO_8859_1)) o.u8(c & 0xff); o.u8(0); }
}

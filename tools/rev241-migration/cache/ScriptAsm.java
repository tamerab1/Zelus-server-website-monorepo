import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/**
 * Editable clientscript model (same byte layout as {@link ScriptRewriter}). Jump operands and switch
 * offsets are held as ABSOLUTE instruction indexes while decoded, so instructions can be inserted or
 * removed and every branch stays pointed at the same instruction; encode() turns them back into
 * relative offsets (target = index + 1 + offset). encode(decode(b)) == b for every script.
 */
public final class ScriptAsm {
	public static final class Insn {
		public int op, i; public String s; public long l;
		public Insn(int op, int i) { this.op = op; this.i = i; }
		public static Insn str(String s) { Insn n = new Insn(ScriptRewriter.SCONST, 0); n.s = s; return n; }
		Insn copy() { Insn n = new Insn(op, i); n.s = s; n.l = l; return n; }
		boolean same(Insn o) { return op == o.op && i == o.i && l == o.l && Objects.equals(s, o.s); }
	}

	public String name; // null = unnamed
	public List<Insn> code = new ArrayList<>();
	public int localInt, localObj, localLong, intArgs, objArgs, longArgs;
	public List<LinkedHashMap<Integer, Integer>> switches = new ArrayList<>(); // key -> absolute target

	static final Set<Integer> JUMPS = Set.of(6, 7, 8, 9, 10, 31, 32, 68, 69, 70, 71, 72, 73);
	static final int SWITCH = 60;

	public static boolean isJump(int op) { return JUMPS.contains(op); }

	public static ScriptAsm decode(byte[] b, boolean rev237) {
		ScriptAsm s = new ScriptAsm();
		DefRewriter.In in = new DefRewriter.In(b);
		int switchLength = ((b[b.length - 2] & 0xff) << 8) | (b[b.length - 1] & 0xff);
		int endIdx = b.length - 2 - switchLength - (rev237 ? 16 : 12);
		if (in.peek() == 0) in.u8(); else s.name = cstr(in);
		while (in.pos < endIdx) {
			int op = in.u16();
			Insn n = new Insn(op, 0);
			if (op == ScriptRewriter.SCONST) n.s = cstr(in);
			else if (op == ScriptRewriter.LCONST) { long v = 0; for (int k = 0; k < 8; k++) v = (v << 8) | in.u8(); n.l = v; }
			else if (op == ScriptRewriter.RETURN || op == ScriptRewriter.POP_INT || op == ScriptRewriter.POP_OBJECT || op == ScriptRewriter.PUSH_NULL || op >= 100) n.i = in.u8();
			else n.i = in.i32();
			s.code.add(n);
		}
		if (in.i32() != s.code.size()) throw new IllegalStateException("opcode count");
		s.localInt = in.u16(); s.localObj = in.u16(); if (rev237) s.localLong = in.u16();
		s.intArgs = in.u16(); s.objArgs = in.u16(); if (rev237) s.longArgs = in.u16();
		int tables = in.u8();
		for (int t = 0; t < tables; t++) {
			LinkedHashMap<Integer, Integer> m = new LinkedHashMap<>();
			int count = in.u16();
			for (int k = 0; k < count; k++) m.put(in.i32(), in.i32());
			s.switches.add(m);
		}
		// relative -> absolute
		for (int idx = 0; idx < s.code.size(); idx++) {
			Insn n = s.code.get(idx);
			if (isJump(n.op)) n.i = idx + 1 + n.i;
			else if (n.op == SWITCH) { var m = s.switches.get(n.i); final int at = idx; m.replaceAll((k, v) -> at + 1 + v); }
		}
		return s;
	}

	static String cstr(DefRewriter.In in) {
		java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
		int c; while ((c = in.u8()) != 0) o.write(c);
		return new String(o.toByteArray(), StandardCharsets.ISO_8859_1); // cp1252 bytes kept as-is
	}

	public byte[] encode(boolean rev237) {
		DefRewriter.Out o = new DefRewriter.Out();
		if (name == null) o.u8(0); else { for (byte c : name.getBytes(StandardCharsets.ISO_8859_1)) o.u8(c & 0xff); o.u8(0); }
		// switch tables referenced by more than one SWITCH would break absolute->relative; Jagex never does that
		Map<Integer, Integer> switchAt = new HashMap<>();
		for (int idx = 0; idx < code.size(); idx++) if (code.get(idx).op == SWITCH && switchAt.put(code.get(idx).i, idx) != null) throw new IllegalStateException("shared switch table");
		for (int idx = 0; idx < code.size(); idx++) {
			Insn n = code.get(idx);
			o.u16(n.op);
			if (n.op == ScriptRewriter.SCONST) { for (byte c : n.s.getBytes(StandardCharsets.ISO_8859_1)) o.u8(c & 0xff); o.u8(0); }
			else if (n.op == ScriptRewriter.LCONST) { if (!rev237) throw new IllegalStateException(); for (int k = 7; k >= 0; k--) o.u8((int) (n.l >>> (k * 8)) & 0xff); }
			else if (n.op == ScriptRewriter.RETURN || n.op == ScriptRewriter.POP_INT || n.op == ScriptRewriter.POP_OBJECT || n.op == ScriptRewriter.PUSH_NULL || n.op >= 100) o.u8(n.i);
			else if (isJump(n.op)) { if (n.i < 0 || n.i > code.size()) throw new IllegalStateException("jump out of range at " + idx); o.i32(n.i - idx - 1); }
			else o.i32(n.i);
		}
		o.i32(code.size());
		o.u16(localInt); o.u16(localObj); if (rev237) o.u16(localLong);
		o.u16(intArgs); o.u16(objArgs); if (rev237) o.u16(longArgs);
		int start = o.size();
		o.u8(switches.size());
		for (int t = 0; t < switches.size(); t++) {
			Integer at = switchAt.get(t);
			o.u16(switches.get(t).size());
			for (var e : switches.get(t).entrySet()) { o.i32(e.getKey()); o.i32(at == null ? e.getValue() : e.getValue() - at - 1); }
		}
		o.u16(o.size() - start);
		return o.toByteArray();
	}

	/**
	 * Inserts code before instruction {@code at}. Branches that targeted {@code at} keep targeting the
	 * original instruction unless {@code retarget} is true (then they land on the inserted code).
	 */
	public void insert(int at, boolean retarget, Insn... add) {
		int n = add.length;
		for (Insn x : code) if (isJump(x.op) && (x.i > at || (x.i == at && !retarget))) x.i += n;
		for (var m : switches) m.replaceAll((k, v) -> v > at || (v == at && !retarget) ? v + n : v);
		List<Insn> ins = new ArrayList<>();
		for (Insn x : add) {
			Insn c = x.copy();
			if (isJump(c.op) && c.i >= at) c.i += n; // inserted jumps are written against the pre-insert indexes
			ins.add(c);
		}
		code.addAll(at, ins);
	}

	/** Removes instructions [from, to); nothing may branch into the removed range. */
	public void delete(int from, int to) {
		int n = to - from;
		for (int idx = 0; idx < code.size(); idx++) {
			if (idx >= from && idx < to) continue;
			Insn x = code.get(idx);
			if (isJump(x.op)) { if (x.i > from && x.i < to) throw new IllegalStateException("branch into deleted range"); if (x.i >= to) x.i -= n; }
		}
		for (var m : switches) for (var e : m.entrySet()) { int v = e.getValue(); if (v > from && v < to) throw new IllegalStateException("switch into deleted range"); if (v >= to) e.setValue(v - n); }
		code.subList(from, to).clear();
	}

	// ------------------------------------------------------------ disassembly

	static Map<Integer, String> NAMES;

	public static void loadNames(Path opcodesJava) throws Exception {
		NAMES = new HashMap<>();
		Matcher m = Pattern.compile("int\\s+([A-Z0-9_]+)\\s*=\\s*(\\d+);").matcher(Files.readString(opcodesJava));
		while (m.find()) NAMES.putIfAbsent(Integer.parseInt(m.group(2)), m.group(1).toLowerCase());
	}

	public String line(int idx) {
		Insn n = code.get(idx);
		String nm = NAMES == null ? "op" + n.op : NAMES.getOrDefault(n.op, "op" + n.op);
		String arg;
		if (n.op == ScriptRewriter.SCONST) arg = "\"" + n.s + "\"";
		else if (n.op == ScriptRewriter.LCONST) arg = String.valueOf(n.l);
		else if (isJump(n.op)) arg = "-> " + n.i;
		else if (n.op == SWITCH) arg = "table " + n.i + " " + switches.get(n.i);
		else arg = String.valueOf(n.i);
		return String.format("%4d  %-28s %s", idx, nm, arg);
	}

	public String dump() {
		StringBuilder b = new StringBuilder();
		b.append(String.format("; args int=%d obj=%d long=%d  locals int=%d obj=%d long=%d%n", intArgs, objArgs, longArgs, localInt, localObj, localLong));
		for (int idx = 0; idx < code.size(); idx++) b.append(line(idx)).append('\n');
		return b.toString();
	}

	/** instruction equality ignoring jump targets (which shift when code moves) */
	public static boolean sameShape(Insn a, Insn b) {
		if (isJump(a.op) || a.op == SWITCH) return a.op == b.op;
		return a.same(b);
	}
}

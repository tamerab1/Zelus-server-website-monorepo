import java.util.ArrayList;
import java.util.List;

/**
 * Byte-level rewriter for clientscripts (index 12), ported from RuneLite 1.13.4's ScriptLoader.
 *
 * Layout: [name string or 0] [instructions] [footer] [switch tables] [u16 switch block length].
 * Footer since rev 237: numOpcodes i32, localInt u16, localObj u16, localLong u16, intArgs u16,
 * objArgs u16, longArgs u16 (16 bytes); before rev 237 the two long counts did not exist (12 bytes)
 * and there was no LCONST. Instructions: u16 opcode + operand: string (SCONST), i64 (LCONST),
 * u8 (RETURN / POP_INT / POP_OBJECT / PUSH_NULL and every opcode >= 100), else i32.
 *
 * Typed operands are remapped directly: GET/SET_VARP (varp), GET/SET_VARBIT (varbit), INVOKE
 * (script). ICONST values are untyped; {@link IconstMapper} decides them per instruction.
 */
public final class ScriptRewriter {
	static final int ICONST = 0, GET_VARP = 1, SET_VARP = 2, SCONST = 3, RETURN = 21, GET_VARBIT = 25, SET_VARBIT = 27,
		POP_INT = 38, POP_OBJECT = 39, INVOKE = 40, SWITCH = 60, LCONST = 61, PUSH_NULL = 63;

	public interface IconstMapper {
		/** instruction index, value -> new value */
		int map(int index, int value);
	}

	public record Instruction(int index, int opcode, int intOperand) {}

	public final List<Instruction> instructions = new ArrayList<>();
	public final List<int[]> switchKeys = new ArrayList<>();
	private final DefRewriter.Out out = new DefRewriter.Out();

	public static ScriptRewriter run(byte[] b, DefRewriter.Ids ids, boolean inRev237, boolean outRev237, IconstMapper iconsts) {
		ScriptRewriter r = new ScriptRewriter();
		r.walk(b, ids, inRev237, outRev237, iconsts);
		return r;
	}

	public byte[] bytes() { return out.toByteArray(); }

	private void walk(byte[] b, DefRewriter.Ids ids, boolean inRev237, boolean outRev237, IconstMapper iconsts) {
		DefRewriter.In in = new DefRewriter.In(b);
		int switchLength = ((b[b.length - 2] & 0xff) << 8) | (b[b.length - 1] & 0xff);
		int endIdx = b.length - 2 - switchLength - (inRev237 ? 16 : 12);
		if (endIdx < 1) throw new IllegalStateException("bad script footer");

		// name
		if (in.peek() == 0) out.u8(in.u8());
		else { int c; do { c = in.u8(); out.u8(c); } while (c != 0); }

		int index = 0;
		while (in.pos < endIdx) {
			int op = in.u16();
			out.u16(op);
			switch (op) {
				case SCONST -> { int c; do { c = in.u8(); out.u8(c); } while (c != 0); }
				case RETURN, POP_INT, POP_OBJECT, PUSH_NULL -> out.u8(in.u8());
				case LCONST -> {
					if (!inRev237) throw new IllegalStateException("LCONST in a pre-237 script");
					for (int i = 0; i < 8; i++) out.u8(in.u8());
				}
				default -> {
					if (op < 100) {
						int v = in.i32();
						int n = switch (op) {
							case GET_VARP, SET_VARP -> ids.map("varp", v);
							case GET_VARBIT, SET_VARBIT -> ids.map("varbit", v);
							case INVOKE -> ids.map("clientscript", v);
							case ICONST -> iconsts.map(index, v);
							default -> v;
						};
						instructions.add(new Instruction(index, op, v));
						out.i32(n);
						index++;
						continue;
					}
					out.u8(in.u8());
				}
			}
			instructions.add(new Instruction(index, op, 0));
			index++;
		}
		if (in.pos != endIdx) throw new IllegalStateException("instructions overrun the footer");

		// footer
		int numOpcodes = in.i32();
		if (numOpcodes != index) throw new IllegalStateException("opcode count " + numOpcodes + " != " + index);
		out.i32(numOpcodes);
		int localInt = in.u16(), localObj = in.u16(), localLong = inRev237 ? in.u16() : 0;
		int intArgs = in.u16(), objArgs = in.u16(), longArgs = inRev237 ? in.u16() : 0;
		if (!outRev237 && (localLong != 0 || longArgs != 0)) throw new IllegalStateException("script uses longs; cannot write pre-237");
		out.u16(localInt); out.u16(localObj); if (outRev237) out.u16(localLong);
		out.u16(intArgs); out.u16(objArgs); if (outRev237) out.u16(longArgs);

		// switch tables (copied; keys reported)
		int start = out.size();
		int n = in.u8();
		out.u8(n);
		for (int s = 0; s < n; s++) {
			int count = in.u16();
			out.u16(count);
			for (int i = 0; i < count; i++) {
				int key = in.i32(), off = in.i32();
				switchKeys.add(new int[]{s, key});
				out.i32(key);
				out.i32(off);
			}
		}
		int len = out.size() - start;
		in.u16(); // original switch length
		out.u16(len);
		if (in.pos != b.length) throw new IllegalStateException("script trailing bytes");
	}
}

import io.ruin.api.buffer.InBuffer;
import net.runelite.cache.fs.*;
import java.io.File;
import java.lang.reflect.*;
import java.util.*;

/**
 * Drives the server's own config decoders (io.ruin.cache) one opcode at a time over every def in a
 * cache and checks each opcode's byte length against DefRewriter's segments. The server decoders
 * skip unknown opcodes silently, so a wrong length means the rest of that def is read out of step.
 * (NPCType System.exit()s on an unknown opcode: if this run stops after "CHECK item", that's why.)
 * Needs the kronos-server + kronos-api classes on the classpath. args: cache
 */
public class ServerDecoderCheck {
	record Target(String type, String cls, String method, int arity) {}

	static final Target[] TARGETS = {
			new Target("item", "io.ruin.cache.ObjType", "decode", 2),
			new Target("npc", "io.ruin.cache.NPCType", "decode", 2),
			new Target("obj", "io.ruin.cache.LocType", "decode", 3),
			new Target("seq", "io.ruin.cache.SeqType", "decode", 2),
			new Target("spotanim", "io.ruin.cache.SpotAnimType", "method4212", 2),
			new Target("kit", "io.ruin.cache.IdkType", "method707", 2),
			new Target("param", "io.ruin.cache.ParamType", "decode", 2),
	};

	public static void main(String[] args) throws Exception {
		Store s = new Store(new File(args[0]));
		s.load();
		DefRewriter.ParamKinds pk = DefRewriter.paramKinds(RoundTripDefs.files(s, 11));
		Field pos = InBuffer.class.getDeclaredField("position");
		pos.setAccessible(true);
		for (Target t : TARGETS) {
			Class<?> c = Class.forName(t.cls, false, ServerDecoderCheck.class.getClassLoader());
			Constructor<?> ctor = c.getDeclaredConstructor();
			ctor.setAccessible(true);
			Method m = t.arity == 2 ? c.getDeclaredMethod(t.method, InBuffer.class, int.class)
					: c.getDeclaredMethod(t.method, InBuffer.class, int.class, int.class);
			m.setAccessible(true);
			Field idField = null;
			try { idField = c.getDeclaredField("id"); idField.setAccessible(true); } catch (NoSuchFieldException ignored) {}
			Map<Integer, int[]> bad = new TreeMap<>(); // opcode -> {defs, example id}
			int defs = 0, badDefs = 0;
			for (var e : RoundTripDefs.files(s, RoundTripDefs.GROUPS.get(t.type)).entrySet()) {
				defs++;
				List<byte[]> segs = DefRewriter.segments(t.type, e.getValue(), DefRewriter.IDENTITY, pk);
				Object def = ctor.newInstance();
				if (idField != null && idField.getType() == int.class) idField.setInt(def, e.getKey());
				int last = -1;
				int start = 0;
				boolean defBad = false;
				for (byte[] seg : segs) {
					int op = seg[0] & 0xff;
					InBuffer in = new InBuffer(e.getValue());
					pos.setInt(in, start + 1);
					int got;
					try {
						if (t.arity == 2) m.invoke(def, in, op); else m.invoke(def, in, op, last);
						got = pos.getInt(in) - start;
					} catch (InvocationTargetException ex) {
						got = -1;
					}
					if (got != seg.length) {
						int read = got;
						int[] b = bad.computeIfAbsent(op, k -> new int[]{0, e.getKey(), seg.length, read});
						b[0]++;
						defBad = true;
					}
					start += seg.length;
					last = op;
				}
				if (defBad) badDefs++;
			}
			StringBuilder sb = new StringBuilder("CHECK " + t.type + ": " + defs + " defs, " + badDefs + " misread");
			bad.forEach((op, b) -> sb.append("\n   op ").append(op).append(": ").append(b[0])
					.append(" defs (e.g. id ").append(b[1]).append(" expects ").append(b[2]).append(" bytes, server read ")
					.append(b[3] < 0 ? "EXCEPTION" : String.valueOf(b[3])).append(')'));
			System.out.println(sb);
		}
	}
}

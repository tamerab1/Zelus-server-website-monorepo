import net.runelite.cache.fs.*;
import java.io.File;
import java.util.*;

/**
 * Which opcodes each config type really uses in a cache, with def counts. The server's own
 * decoders (io.ruin.cache) skip unknown opcodes silently, so every opcode listed here must be
 * handled there or the rest of that def is read out of step.
 * args: cache
 */
public class OpcodeCensus {
	public static void main(String[] args) throws Exception {
		Store s = new Store(new File(args[0]));
		s.load();
		DefRewriter.ParamKinds pk = DefRewriter.paramKinds(RoundTripDefs.files(s, 11));
		for (var g : RoundTripDefs.GROUPS.entrySet()) {
			Map<Integer, Integer> ops = new TreeMap<>();
			for (byte[] data : RoundTripDefs.files(s, g.getValue()).values()) {
				Set<Integer> seen = new HashSet<>();
				for (byte[] seg : DefRewriter.segments(g.getKey(), data, DefRewriter.IDENTITY, pk))
					seen.add(seg[0] & 0xff);
				for (int op : seen) ops.merge(op, 1, Integer::sum);
			}
			StringBuilder sb = new StringBuilder("OPS " + g.getKey() + ":");
			ops.forEach((op, n) -> sb.append(' ').append(op).append('=').append(n));
			System.out.println(sb);
		}
	}
}

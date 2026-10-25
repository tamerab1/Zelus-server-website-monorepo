import net.runelite.cache.fs.*;
import java.io.File;
import java.util.*;

/**
 * Rev 241 sub-options / conditional options refer to an option slot (their first byte). The client keeps
 * options in a list built from the plain option opcodes, and one of these opcodes for a slot that has no
 * plain option before it crashes the definition load (IndexOutOfBounds in EntityOps). Lists every
 * definition where that happens. objects: options 30-34, sub/conditional 100/101/102; npcs: 30-34 /
 * 251/252/253; items (inventory options): 35-39 / 200/201/202. args: cache
 */
public class OpsCheck241 {
	record Kind(String type, int group, int opLo, int opHi, int sub, int cond, int condSub) {}

	public static void main(String[] a) throws Exception {
		Store s = new Store(new File(a[0]));
		s.load();
		DefRewriter.ParamKinds pk = DefRewriter.paramKinds(RoundTripDefs.files(s, 11));
		Kind[] kinds = {new Kind("obj", 6, 30, 34, 100, 101, 102), new Kind("npc", 9, 30, 34, 251, 252, 253), new Kind("item", 10, 35, 39, 200, 201, 202)};
		int bad = 0;
		for (Kind k : kinds) {
			for (var e : RoundTripDefs.files(s, k.group).entrySet()) {
				Set<Integer> slots = new HashSet<>();
				for (byte[] seg : DefRewriter.segments(k.type, e.getValue(), DefRewriter.IDENTITY, pk)) {
					int op = seg[0] & 0xff;
					if (op >= k.opLo && op <= k.opHi) slots.add(op - k.opLo);
					else if (op == k.sub || op == k.cond || op == k.condSub) {
						int slot = seg[1] & 0xff;
						if (!slots.contains(slot)) {
							bad++;
							System.out.println(k.type + " " + e.getKey() + ": opcode " + op + " for slot " + slot + " with options only in " + slots);
						}
					}
				}
			}
		}
		System.out.println("definitions with an option opcode for a slot that has no option: " + bad);
	}
}

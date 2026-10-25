import net.runelite.cache.fs.*;
import java.io.File;
import java.util.*;

/**
 * Vanilla 231 -> 241 interface component shifts. The server hard-codes vanilla component ids
 * (bank, settings, spellbooks...), so a component Jagex moved to another id breaks its handler.
 * Components are compared with the parent field blanked (a renumbered parent changes it) after
 * converting 231 to the wide model format. Per group: same / moved (old -> new id) / changed in
 * place / added. args: vanilla231 vanilla241
 */
public class InterfaceShift {
	static byte[] sig(byte[] comp, boolean wide) {
		InterfaceRewriter r = InterfaceRewriter.run(comp, DefRewriter.IDENTITY, wide, true, v -> false);
		byte[] b = r.bytes().clone();
		if (r.parentPos >= 0) { b[r.parentPos] = 0; b[r.parentPos + 1] = 0; }
		return b;
	}

	public static void main(String[] a) throws Exception {
		Store s231 = new Store(new File(a[0])); s231.load();
		Store s241 = new Store(new File(a[1])); s241.load();
		var c231 = RoundTripInterfaces.components(s231);
		var c241 = RoundTripInterfaces.components(s241);
		int identical = 0;
		for (var g : c231.entrySet()) {
			int id = g.getKey();
			Map<Integer, byte[]> old = g.getValue(), neu = c241.get(id);
			if (neu == null) { System.out.println("GROUP " + id + ": removed in 241"); continue; }
			Map<Integer, byte[]> so = new TreeMap<>(), sn = new TreeMap<>();
			try {
				for (var e : old.entrySet()) so.put(e.getKey(), sig(e.getValue(), false));
				for (var e : neu.entrySet()) sn.put(e.getKey(), sig(e.getValue(), true));
			} catch (Exception ex) {
				System.out.println("GROUP " + id + ": unreadable " + ex.getMessage());
				continue;
			}
			Map<String, List<Integer>> bySig = new HashMap<>();
			sn.forEach((k, v) -> bySig.computeIfAbsent(Arrays.toString(v), x -> new ArrayList<>()).add(k));
			List<String> moved = new ArrayList<>();
			int same = 0, changed = 0;
			for (var e : so.entrySet()) {
				byte[] n = sn.get(e.getKey());
				if (n != null && Arrays.equals(n, e.getValue())) { same++; continue; }
				List<Integer> at = bySig.getOrDefault(Arrays.toString(e.getValue()), List.of());
				if (!at.isEmpty() && !at.contains(e.getKey())) moved.add(e.getKey() + "->" + (at.size() == 1 ? at.get(0) : at.toString()));
				else changed++;
			}
			int added = (int) sn.keySet().stream().filter(k -> !so.containsKey(k)).count();
			if (moved.isEmpty() && changed == 0 && added == 0) { identical++; continue; }
			System.out.println("GROUP " + id + ": " + old.size() + "->" + neu.size() + " comps, same " + same + ", changed " + changed
					+ ", added " + added + (moved.isEmpty() ? "" : ", MOVED " + moved.size() + " " + moved));
		}
		System.out.println("identical groups: " + identical + " of " + c231.size());
	}
}

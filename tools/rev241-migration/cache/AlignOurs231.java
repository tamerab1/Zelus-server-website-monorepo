import net.runelite.cache.fs.*;
import java.io.File;
import java.nio.file.*;
import java.util.*;

/**
 * Which of OUR live-cache component ids corresponds to which vanilla 231 component, for every vanilla
 * interface group our cache differs in. Ours and base are compared with the parent field blanked; a component
 * matching exactly one base component aligns to it, an unmatched one keeps its id when the base has it
 * ("edited in place") unless ALIGN_OVERRIDES says otherwise, and one beyond the base is ours. A group where
 * fewer than half of our components align is one Zelus overwrote with its own interface ("overwritten").
 *
 * args: localCache v231Cache out.json      (prints a summary line per group)
 */
public class AlignOurs231 {
	/** Zelus's cache lacks vanilla 218:193 (MONSTER_INSPECT), so ours 193-202 are vanilla 194-203. */
	static final Map<Integer, Map<Integer, Integer>> ALIGN_OVERRIDES = Map.of(218, Map.of(193, 194, 202, 203));

	public static void main(String[] a) throws Exception {
		Store local = new Store(new File(a[0])); local.load();
		Store v231 = new Store(new File(a[1])); v231.load();
		var L = RoundTripInterfaces.components(local);
		var B = RoundTripInterfaces.components(v231);
		StringBuilder json = new StringBuilder("{\n");
		boolean first = true;
		for (var g : L.entrySet()) {
			int gid = g.getKey();
			if (!B.containsKey(gid)) continue;
			Map<Integer, byte[]> base = B.get(gid), ours = g.getValue();
			boolean same = base.keySet().equals(ours.keySet());
			if (same) for (int k : base.keySet()) if (!BuildInterfaces241.sameIgnoringDroppedTail(base.get(k), ours.get(k))) { same = false; break; }
			if (same) continue;
			Map<String, List<Integer>> bySig = new HashMap<>();
			for (var e : base.entrySet()) bySig.computeIfAbsent(Arrays.toString(InterfaceShift.sig(e.getValue(), false)), x -> new ArrayList<>()).add(e.getKey());
			Map<Integer, Integer> align = new TreeMap<>();
			List<Integer> edited = new ArrayList<>(), custom = new ArrayList<>();
			int matched = 0;
			Map<Integer, Integer> over = ALIGN_OVERRIDES.getOrDefault(gid, Map.of());
			for (var e : ours.entrySet()) {
				int k = e.getKey();
				List<Integer> at = bySig.getOrDefault(Arrays.toString(InterfaceShift.sig(e.getValue(), false)), List.of());
				// beyond the vanilla range = ours, even when it is a copy of a vanilla component (e.g. orbs 56 = 44)
				if (k > Collections.max(base.keySet())) custom.add(k);
				else if (over.containsKey(k)) { align.put(k, over.get(k)); edited.add(k); }
				else if (at.size() == 1) { align.put(k, at.get(0)); matched++; }
				else if (at.contains(k)) { align.put(k, k); matched++; }
				else if (base.containsKey(k)) { align.put(k, k); edited.add(k); }
				else custom.add(k);
			}
			boolean overwritten = BuildInterfaces241.OURS_OUTRIGHT.contains(gid) || matched * 2 < Math.min(ours.size(), base.size());
			StringBuilder runs = new StringBuilder();
			int shifted = 0;
			for (var e : align.entrySet()) if (!e.getKey().equals(e.getValue())) shifted++;
			System.out.println("GROUP " + gid + " ours " + ours.size() + " base " + base.size() + ": "
				+ (overwritten ? "OVERWRITTEN" : "aligned (" + matched + " matched, " + shifted + " shifted, edited " + edited + ", custom " + custom + ")"));
			if (!first) json.append(",\n");
			first = false;
			json.append("  \"").append(gid).append("\": {\"mode\": \"").append(overwritten ? "overwritten" : "aligned").append("\", \"ours\": ").append(ours.keySet());
			if (!overwritten) {
				json.append(", \"edited\": ").append(edited).append(", \"custom\": ").append(custom).append(", \"align\": {");
				StringJoiner sj = new StringJoiner(", ");
				for (var e : align.entrySet()) if (!e.getKey().equals(e.getValue())) sj.add("\"" + e.getKey() + "\": " + e.getValue());
				json.append(sj).append("}");
			}
			json.append("}");
		}
		json.append("\n}\n");
		Files.writeString(Path.of(a[2]), json.toString());
	}
}

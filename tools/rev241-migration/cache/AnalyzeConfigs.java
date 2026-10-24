import net.runelite.cache.fs.*;
import net.runelite.cache.index.FileData;

import java.io.File;
import java.nio.file.Path;
import java.util.*;

/** Read-only: what the config merge would do. args: local v231 v241 manifest */
public class AnalyzeConfigs {
	public static void main(String[] a) throws Exception {
		Store local = new Store(new File(a[0])); local.load();
		Store v231 = new Store(new File(a[1])); v231.load();
		Store v241 = new Store(new File(a[2])); v241.load();
		Manifest manifest = new Manifest(Path.of(a[3]));

		Map<Integer, byte[]> params = new HashMap<>(RoundTripDefs.files(v241, 11));
		RoundTripDefs.files(local, 11).forEach(params::putIfAbsent); // our custom params
		DefRewriter.ParamKinds pk = DefRewriter.paramKinds(params);

		for (var g : RoundTripDefs.GROUPS.entrySet()) {
			String type = g.getKey();
			var l = RoundTripDefs.files(local, g.getValue());
			var b = RoundTripDefs.files(v231, g.getValue());
			var t = RoundTripDefs.files(v241, g.getValue());
			int custom = 0, emptyCustom = 0, same = 0, artifactOnly = 0, edited = 0, conflicted = 0, theirsMissing = 0;
			Map<String, Integer> keyHist = new TreeMap<>();
			List<String> conflictSamples = new ArrayList<>();
			for (var e : l.entrySet()) {
				int id = e.getKey();
				byte[] ours = e.getValue();
				if (!b.containsKey(id)) {
					if (ours.length == 1 && ours[0] == 0) emptyCustom++;
					else custom++;
					continue;
				}
				if (ConfigMerge.sameFields(type, b.get(id), ours, pk)) { same++; continue; }
				ConfigMerge.Result r = ConfigMerge.merge(type, b.get(id), ours, t.get(id), manifest, pk);
				if (r.oursChanged.isEmpty()) { artifactOnly++; continue; }
				edited++;
				if (r.theirsMissing) theirsMissing++;
				for (String k : r.oursChanged) keyHist.merge(k.startsWith("param:") ? "param" : k, 1, Integer::sum);
				if (!r.conflicts.isEmpty()) {
					conflicted++;
					if (conflictSamples.size() < 6) conflictSamples.add(id + r.conflicts.toString());
				}
			}
			List<Map.Entry<String, Integer>> top = new ArrayList<>(keyHist.entrySet());
			top.sort((x, y) -> y.getValue() - x.getValue());
			System.out.printf("RESULT %-8s custom=%d (empty %d) vanillaSame=%d packerArtifactOnly=%d REAL-EDITS=%d conflicts=%d deletedIn241=%d%n", type, custom, emptyCustom, same, artifactOnly, edited, conflicted, theirsMissing);
			System.out.println("RESULT    fields we changed (defs): " + top.subList(0, Math.min(16, top.size())));
			if (!conflictSamples.isEmpty()) System.out.println("RESULT    conflict samples: " + conflictSamples);
		}
	}
}

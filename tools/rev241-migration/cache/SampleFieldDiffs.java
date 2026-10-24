import net.runelite.cache.fs.Store;

import java.io.File;
import java.nio.file.Path;
import java.util.*;

/** Read-only: show base/ours/theirs bytes for a field key. args: local v231 v241 manifest type key [n] */
public class SampleFieldDiffs {
	public static void main(String[] a) throws Exception {
		Store local = new Store(new File(a[0])); local.load();
		Store v231 = new Store(new File(a[1])); v231.load();
		Store v241 = new Store(new File(a[2])); v241.load();
		Manifest m = new Manifest(Path.of(a[3]));
		String type = a[4], key = a[5];
		int n = a.length > 6 ? Integer.parseInt(a[6]) : 5;
		int group = RoundTripDefs.GROUPS.get(type);
		Map<Integer, byte[]> params = new HashMap<>(RoundTripDefs.files(v241, 11));
		RoundTripDefs.files(local, 11).forEach(params::putIfAbsent);
		DefRewriter.ParamKinds pk = DefRewriter.paramKinds(params);
		var l = RoundTripDefs.files(local, group);
		var b = RoundTripDefs.files(v231, group);
		var t = RoundTripDefs.files(v241, group);
		Map<String, Integer> pattern = new TreeMap<>();
		int shown = 0;
		for (var e : l.entrySet()) {
			byte[] base = b.get(e.getKey());
			if (base == null) continue;
			var bk = ConfigMerge.keyed(type, DefRewriter.segments(type, base, DefRewriter.IDENTITY, pk));
			var ok = ConfigMerge.keyed(type, DefRewriter.segments(type, e.getValue(), m, pk));
			byte[] bv = bk.get(key), ov = ok.get(key);
			if (Arrays.equals(bv, ov)) continue;
			String kind = (bv == null ? "added" : ov == null ? "removed" : "changed");
			pattern.merge(kind, 1, Integer::sum);
			if (shown++ < n) {
				byte[] tv = t.containsKey(e.getKey()) ? ConfigMerge.keyed(type, DefRewriter.segments(type, t.get(e.getKey()), DefRewriter.IDENTITY, pk)).get(key) : null;
				System.out.println("RESULT " + type + " " + e.getKey() + " " + kind + "  base=" + hex(bv) + "  ours=" + hex(ov) + "  241=" + hex(tv));
			}
		}
		System.out.println("RESULT pattern " + pattern);
	}

	static String hex(byte[] b) {
		if (b == null) return "-";
		StringBuilder s = new StringBuilder();
		for (int i = 0; i < Math.min(b.length, 24); i++) s.append(String.format("%02x", b[i]));
		if (b.length > 24) s.append("..(" + b.length + ")");
		String txt = new String(b, java.nio.charset.StandardCharsets.ISO_8859_1).replaceAll("[^ -~]", ".");
		return s + (b.length > 3 ? " '" + txt.substring(0, Math.min(20, txt.length())) + "'" : "");
	}
}

import net.runelite.cache.fs.Store;

import java.io.File;
import java.nio.file.*;
import java.util.*;

/**
 * Three-way view of a vanilla script Zelus edited: the Zelus edit (vanilla 231 -> ours, as an
 * instruction-level diff with context) and the same region in vanilla 241. Also round-trips every
 * script of the given caches through {@link ScriptAsm} when called with "roundtrip".
 *
 * args: opcodesJava local v231 v241 outDir id...      |  roundtrip local v231 v241
 */
public class ScriptDiff {
	public static void main(String[] a) throws Exception {
		if (a[0].equals("roundtrip")) {
			String[] names = {"ours(231)", "231", "241"};
			for (int c = 1; c <= 3; c++) {
				Store s = new Store(new File(a[c])); s.load();
				boolean r237 = c == 3;
				int ok = 0;
				for (var e : RoundTripScripts.scripts(s).entrySet()) {
					if (e.getValue().length <= 2) continue;
					if (!Arrays.equals(ScriptAsm.decode(e.getValue(), r237).encode(r237), e.getValue())) throw new IllegalStateException(names[c - 1] + " script " + e.getKey());
					ok++;
				}
				System.out.println("RESULT ScriptAsm round trip " + names[c - 1] + ": " + ok + " scripts identical");
			}
			return;
		}
		ScriptAsm.loadNames(Path.of(a[0]));
		Store l = new Store(new File(a[1])); l.load();
		Store b = new Store(new File(a[2])); b.load();
		Store t = new Store(new File(a[3])); t.load();
		var L = RoundTripScripts.scripts(l); var B = RoundTripScripts.scripts(b); var T = RoundTripScripts.scripts(t);
		Path out = Path.of(a[4]); Files.createDirectories(out);
		for (int k = 5; k < a.length; k++) {
			int id = Integer.parseInt(a[k]);
			ScriptAsm sb = ScriptAsm.decode(B.get(id), false), so = ScriptAsm.decode(L.get(id), false), st = ScriptAsm.decode(T.get(id), true);
			Files.writeString(out.resolve(id + "_231.txt"), sb.dump());
			Files.writeString(out.resolve(id + "_ours.txt"), so.dump());
			Files.writeString(out.resolve(id + "_241.txt"), st.dump());
			StringBuilder d = new StringBuilder("== script " + id + ": Zelus edit (231 -> ours)\n" + header(sb, so));
			for (int[] h : hunks(sb, so)) {
				d.append(String.format("@@ 231 [%d,%d) -> ours [%d,%d)%n", h[0], h[1], h[2], h[3]));
				for (int i = Math.max(0, h[0] - 3); i < h[0]; i++) d.append("   ").append(sb.line(i)).append('\n');
				for (int i = h[0]; i < h[1]; i++) d.append(" - ").append(sb.line(i)).append('\n');
				for (int i = h[2]; i < h[3]; i++) d.append(" + ").append(so.line(i)).append('\n');
				for (int i = h[1]; i < Math.min(sb.code.size(), h[1] + 3); i++) d.append("   ").append(sb.line(i)).append('\n');
			}
			d.append("\n== Jagex change (231 -> 241)\n" + header(sb, st));
			for (int[] h : hunks(sb, st)) d.append(String.format("@@ 231 [%d,%d) -> 241 [%d,%d)%n", h[0], h[1], h[2], h[3]));
			Files.writeString(out.resolve(id + "_diff.txt"), d.toString());
			System.out.println(d);
		}
	}

	static String header(ScriptAsm x, ScriptAsm y) {
		return String.format("; args int %d->%d obj %d->%d, locals int %d->%d obj %d->%d, %d -> %d instructions%n",
			x.intArgs, y.intArgs, x.objArgs, y.objArgs, x.localInt, y.localInt, x.localObj, y.localObj, x.code.size(), y.code.size());
	}

	/** LCS diff over instruction shapes -> hunks {aFrom, aTo, bFrom, bTo} */
	static List<int[]> hunks(ScriptAsm x, ScriptAsm y) {
		int n = x.code.size(), m = y.code.size();
		int[][] dp = new int[n + 1][m + 1];
		for (int i = n - 1; i >= 0; i--) for (int j = m - 1; j >= 0; j--)
			dp[i][j] = ScriptAsm.sameShape(x.code.get(i), y.code.get(j)) ? dp[i + 1][j + 1] + 1 : Math.max(dp[i + 1][j], dp[i][j + 1]);
		List<int[]> r = new ArrayList<>();
		int i = 0, j = 0;
		while (i < n || j < m) {
			if (i < n && j < m && ScriptAsm.sameShape(x.code.get(i), y.code.get(j))) { i++; j++; continue; }
			int si = i, sj = j;
			while ((i < n || j < m) && !(i < n && j < m && ScriptAsm.sameShape(x.code.get(i), y.code.get(j)))) {
				if (j < m && (i == n || dp[i][j + 1] >= dp[i + 1][j])) j++; else i++;
			}
			r.add(new int[]{si, i, sj, j});
		}
		return r;
	}
}

import net.runelite.cache.fs.*;
import net.runelite.cache.index.FileData;
import net.runelite.cache.index.IndexData;

import java.io.File;
import java.nio.file.*;
import java.util.*;

/**
 * Stage 5 of the rev 241 cache build: clientscripts (index 12).
 *
 *  custom scripts (absent from vanilla 231): converted to the rev 237+ layout (16-byte footer) and
 *    written at their manifest id. Typed operands are remapped (varp, varbit, invoked script).
 *    ICONST values are remapped only when they equal a moving script id (scripts pass script ids
 *    for dynamic hooks) or a moved interface component (stage 4 componentRemap). Other constants
 *    that happen to equal a moving id were checked to be sizes/positions/colours, not ids.
 *  vanilla scripts Zelus really edited (instructions differ, not just re-encoding):
 *    - identical to vanilla 241 -> 241 (Zelus had imported Jagex's newer version)
 *    - single-operand tweaks are ported onto 241 when the surrounding instructions match exactly
 *      once; anything else keeps 241 and is listed with its diff for manual porting.
 *
 * args: localCache v231Cache pristineV241Cache outDir manifest interfacesReport.json report.json
 */
public class BuildScripts241 {
	static List<long[]> seq(byte[] d, boolean r237) {
		List<long[]> r = new ArrayList<>();
		for (var i : ScriptRewriter.run(d, DefRewriter.IDENTITY, r237, r237, (x, v) -> v).instructions) r.add(new long[]{i.opcode(), i.intOperand()});
		return r;
	}

	static boolean eq(long[] a, long[] b) { return a[0] == b[0] && a[1] == b[1]; }

	public static void main(String[] a) throws Exception {
		Store local = new Store(new File(a[0])); local.load();
		Store v231 = new Store(new File(a[1])); v231.load();
		Store v241 = new Store(new File(a[2])); v241.load();
		Store out = new Store(new File(a[3])); out.load();
		Manifest manifest = new Manifest(Path.of(a[4]));

		Map<Integer, Integer> componentMoves = new HashMap<>();
		var ifr = com.google.gson.JsonParser.parseString(Files.readString(Path.of(a[5]))).getAsJsonObject();
		for (var e : ifr.getAsJsonArray("componentRemap")) componentMoves.put(e.getAsJsonObject().get("from").getAsInt(), e.getAsJsonObject().get("to").getAsInt());
		Map<Integer, Integer> scriptMap = manifest.maps.get("clientscript");

		var L = RoundTripScripts.scripts(local);
		var B = RoundTripScripts.scripts(v231);
		var T = RoundTripScripts.scripts(v241);
		Map<Integer, byte[]> written = new TreeMap<>();
		List<String> iconstRemaps = new ArrayList<>(), edits = new ArrayList<>(), missingCalls = new ArrayList<>();
		int customs = 0, imported = 0;

		for (var e : L.entrySet()) {
			int id = e.getKey();
			byte[] d = e.getValue();
			if (d.length <= 2) continue;
			if (!B.containsKey(id)) {
				int target = manifest.map("clientscript", id);
				if (target == id && T.containsKey(id) && manifest.isImported("clientscript", id)) { imported++; continue; }
				if (T.containsKey(target)) throw new IllegalStateException("script " + id + " -> " + target + " is vanilla 241");
				final int sid = id;
				// a call to a script our cache never had already failed on live; record what the id holds in 241
				DefRewriter.Ids ids = (kind, ref) -> {
					if (kind.equals("clientscript") && !L.containsKey(ref))
						missingCalls.add("    \"script " + sid + " -> " + ref + "\": \"missing in live 231" + (T.containsKey(ref) ? "; a different Jagex script in 241" : "; missing in 241 too") + "\"");
					return manifest.map(kind, ref);
				};
				ScriptRewriter r = ScriptRewriter.run(d, ids, false, true, (index, v) -> {
					Integer n = null;
					if (scriptMap.containsKey(v) && scriptMap.get(v).intValue() != v) n = scriptMap.get(v);
					else if (componentMoves.containsKey(v)) n = componentMoves.get(v);
					if (n == null) return v;
					iconstRemaps.add("    \"script " + sid + " instr " + index + ": iconst " + v + " -> " + n + "\"");
					return n;
				});
				written.put(target, r.bytes());
				customs++;
				continue;
			}
			// vanilla id
			byte[] b = B.get(id);
			if (Arrays.equals(d, b)) continue;
			List<long[]> so = seq(d, false), sb = seq(b, false);
			if (sameSeq(so, sb)) continue; // re-encoded only
			byte[] tBytes = T.get(id);
			byte[] oursConverted = ScriptRewriter.run(d, manifest, false, true, (i, v) -> v).bytes();
			if (tBytes != null && Arrays.equals(oursConverted, tBytes)) { edits.add("    \"" + id + "\": \"identical to vanilla 241 (Jagex's newer version imported early): 241 kept\""); continue; }
			boolean jagexChanged = tBytes == null || !Arrays.equals(ScriptRewriter.run(b, DefRewriter.IDENTITY, false, true, (i, v) -> v).bytes(), tBytes);
			if (!jagexChanged) { written.put(id, oursConverted); edits.add("    \"" + id + "\": \"Zelus edit, Jagex unchanged: ours\""); continue; }

			// both changed: try to port single-operand tweaks onto 241
			List<int[]> tweaks = new ArrayList<>();
			boolean onlyTweaks = so.size() == sb.size();
			if (onlyTweaks) for (int i = 0; i < so.size(); i++) {
				if (eq(so.get(i), sb.get(i))) continue;
				if (so.get(i)[0] != sb.get(i)[0]) { onlyTweaks = false; break; }
				tweaks.add(new int[]{i, (int) sb.get(i)[1], (int) so.get(i)[1]});
			}
			if (!onlyTweaks || tweaks.isEmpty()) {
				edits.add("    \"" + id + "\": \"STRUCTURAL Zelus edit on a script Jagex also changed: 241 kept, port by hand (" + sb.size() + " -> ours " + so.size() + " instructions, 241 has " + seq(tBytes, true).size() + ")\"");
				continue;
			}
			List<long[]> st = seq(tBytes, true);
			Map<Integer, Integer> patchAt = new HashMap<>(); // instruction index in 241 -> new operand
			List<String> notes = new ArrayList<>();
			boolean allPorted = true;
			for (int[] tw : tweaks) {
				int found = -1, hits = 0;
				for (int j = 0; j < st.size(); j++) {
					if (st.get(j)[0] != sb.get(tw[0])[0] || st.get(j)[1] != tw[1]) continue;
					boolean ctx = true;
					for (int k = 1; k <= 3 && ctx; k++) {
						int bi = tw[0] - k, tj = j - k;
						if (bi >= 0 && (tj < 0 || !eq(sb.get(bi), st.get(tj)))) ctx = false;
						bi = tw[0] + k; tj = j + k;
						if (bi < sb.size() && (tj >= st.size() || !eq(sb.get(bi), st.get(tj)))) ctx = false;
					}
					if (ctx) { found = j; hits++; }
				}
				if (hits == 1) { patchAt.put(found, tw[2]); notes.add("instr " + tw[0] + " " + tw[1] + "->" + tw[2] + " ported at 241 instr " + found); }
				else {
					// Jagex may have made the same change already
					boolean already = false;
					for (int j = 0; j < st.size(); j++) if (st.get(j)[0] == sb.get(tw[0])[0] && st.get(j)[1] == tw[2]) already = true;
					notes.add("instr " + tw[0] + " " + tw[1] + "->" + tw[2] + (already ? " (241 already has " + tw[2] + ")" : " NOT PORTED (context " + (hits == 0 ? "not found" : "ambiguous") + ")"));
					if (!already) allPorted = false;
				}
			}
			if (!patchAt.isEmpty()) {
				byte[] patched = ScriptRewriter.run(tBytes, DefRewriter.IDENTITY, true, true, (index, v) -> patchAt.getOrDefault(index, v)).bytes();
				written.put(id, patched);
			}
			edits.add("    \"" + id + "\": \"" + (allPorted ? "ported onto 241" : "PARTIALLY ported onto 241") + ": " + String.join("; ", notes) + "\"");
		}

		// ---- write
		Index index = out.findIndex(12);
		for (var e : written.entrySet()) {
			Archive ar = index.getArchive(e.getKey());
			if (ar == null) {
				ar = index.addArchive(e.getKey());
				ar.setCompression(net.runelite.cache.fs.jagex.CompressionType.GZ);
			}
			FileData f = new FileData();
			f.setId(0);
			Container c = new Container(ar.getCompression(), -1);
			c.compress(e.getValue(), null);
			out.getStorage().store(12, e.getKey(), c.data);
			ar.setFileData(new FileData[]{f});
			ar.setCrc(c.crc);
			ar.setRevision(ar.getRevision() + 1);
			ar.setCompressedSize(c.data.length);
			ar.setDecompressedSize(e.getValue().length);
		}
		IndexData idx = index.toIndexData();
		byte[] rawIndex = idx.writeIndexData();
		Container ic = new Container(index.getCompression(), -1);
		ic.compress(rawIndex, null);
		out.getStorage().store(255, 12, ic.data);
		index.setCrc(ic.crc);
		out.close();

		// ---- verify
		Store check = new Store(new File(a[3]));
		check.load();
		var C = RoundTripScripts.scripts(check);
		for (var e : written.entrySet()) {
			if (!Arrays.equals(C.get(e.getKey()), e.getValue())) throw new IllegalStateException("script " + e.getKey() + " differs after write");
			ScriptRewriter.run(e.getValue(), DefRewriter.IDENTITY, true, true, (i, v) -> v); // valid 241 layout
		}
		Files.writeString(Path.of(a[6]), "{\n  \"stage\": \"clientscripts\",\n  \"customScriptsWritten\": " + customs + ",\n  \"importedVanillaKept\": " + imported
			+ ",\n  \"vanillaEdits\": {\n" + String.join(",\n", edits) + "\n  },\n  \"iconstRemaps\": [\n" + String.join(",\n", iconstRemaps)
			+ "\n  ],\n  \"callsToScriptsMissingInLive231\": {\n" + String.join(",\n", missingCalls) + "\n  }\n}\n");
		System.out.println("RESULT scripts: custom " + customs + " written, imported vanilla kept " + imported + ", vanilla edits handled " + edits.size()
			+ ", ICONST remaps " + iconstRemaps.size() + ", groups written+verified " + written.size());
	}

	static boolean sameSeq(List<long[]> x, List<long[]> y) {
		if (x.size() != y.size()) return false;
		for (int i = 0; i < x.size(); i++) if (!eq(x.get(i), y.get(i))) return false;
		return true;
	}
}

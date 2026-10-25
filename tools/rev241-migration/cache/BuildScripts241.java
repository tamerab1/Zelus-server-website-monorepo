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
		Map<Integer, java.util.function.Consumer<EnumCodec.Enum>> enumEdits = new TreeMap<>();
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
			ScriptAsm ao = ScriptAsm.decode(d, false), ab = ScriptAsm.decode(b, false);
			if (sameScript(ao, ab)) continue; // re-encoded only (instructions, text, args, switches equal)
			byte[] tBytes = T.get(id);
			if (tBytes == null) throw new IllegalStateException("edited vanilla script " + id + " was deleted in 241");
			ScriptAsm at = ScriptAsm.decode(tBytes, true);
			byte[] oursConverted = ScriptRewriter.run(d, manifest, false, true, (i, v) -> v).bytes();
			if (Arrays.equals(oursConverted, tBytes)) { edits.add("    \"" + id + "\": \"identical to vanilla 241 (Jagex's newer version imported early): 241 kept\""); continue; }
			if (sameScript(ab, at)) { written.put(id, oursConverted); edits.add("    \"" + id + "\": \"Zelus edit, Jagex unchanged: ours\""); continue; }

			// both changed
			HandPort hp = HAND_PORTS.get(id);
			if (hp != null) {
				hp.apply(ao, ab, at, enumEdits);
				edits.add("    \"" + id + "\": \"" + hp.note() + ": 241 kept\"");
				continue;
			}
			// single-operand tweaks (numbers or text) ported onto 241 where the surrounding instructions match once
			if (ao.code.size() != ab.code.size()) throw new IllegalStateException("script " + id + ": structural Zelus edit on a script Jagex also changed; add a HAND_PORTS entry");
			List<String> notes = new ArrayList<>();
			ScriptAsm pristine = ScriptAsm.decode(tBytes, true); // contexts are matched against unpatched 241
			for (int i = 0; i < ao.code.size(); i++) {
				ScriptAsm.Insn o = ao.code.get(i), x = ab.code.get(i);
				if (ScriptAsm.sameShape(o, x)) continue;
				if (o.op != x.op || ScriptAsm.isJump(o.op) || o.op == ScriptAsm.SWITCH) throw new IllegalStateException("script " + id + " instr " + i + ": not an operand tweak; add a HAND_PORTS entry");
				int found = -1, hits = 0;
				for (int j = 0; j < at.code.size(); j++) {
					if (!ScriptAsm.sameShape(pristine.code.get(j), x)) continue;
					boolean ctx = true;
					for (int k = 1; k <= 3 && ctx; k++) {
						if (i - k >= 0 && (j - k < 0 || !ScriptAsm.sameShape(ab.code.get(i - k), pristine.code.get(j - k)))) ctx = false;
						if (i + k < ab.code.size() && (j + k >= pristine.code.size() || !ScriptAsm.sameShape(ab.code.get(i + k), pristine.code.get(j + k)))) ctx = false;
					}
					if (ctx) { found = j; hits++; }
				}
				String what = o.s != null ? "'" + x.s + "' -> '" + o.s + "'" : x.i + " -> " + o.i;
				if (hits == 1) {
					ScriptAsm.Insn t = at.code.get(found);
					t.i = o.i; t.s = o.s; t.l = o.l;
					notes.add("instr " + i + " " + what + " ported at 241 instr " + found);
					continue;
				}
				boolean already = false;
				for (ScriptAsm.Insn t : pristine.code) if (ScriptAsm.sameShape(t, o)) already = true;
				if (!already) throw new IllegalStateException("script " + id + " instr " + i + " (" + what + "): context " + (hits == 0 ? "not found" : "ambiguous") + " in 241; add a HAND_PORTS entry");
				notes.add("instr " + i + " " + what + " (241 already has it)");
			}
			byte[] ported = at.encode(true);
			if (!Arrays.equals(ported, tBytes)) written.put(id, ported);
			edits.add("    \"" + id + "\": \"ported onto 241: " + String.join("; ", notes) + "\"");
		}

		// ---- enum edits from hand ports (config group 8)
		if (!enumEdits.isEmpty()) {
			Map<Integer, byte[]> enums = new TreeMap<>(RoundTripDefs.files(out, 8));
			for (var e : enumEdits.entrySet()) {
				EnumCodec.Enum en = EnumCodec.decode(enums.get(e.getKey()));
				e.getValue().accept(en);
				byte[] nb = EnumCodec.encode(en);
				enums.put(e.getKey(), nb);
				edits.add("    \"enum " + e.getKey() + "\": \"" + en.toString().replace("\"", "'") + "\"");
			}
			writeConfigGroup(out, 8, enums);
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
		// enums: the codec must be lossless on all of vanilla 241, and the hand-ported entries must read back
		for (var e : RoundTripDefs.files(v241, 8).entrySet())
			if (!Arrays.equals(EnumCodec.encode(EnumCodec.decode(e.getValue())), e.getValue())) throw new IllegalStateException("EnumCodec not lossless on enum " + e.getKey());
		var checkEnums = RoundTripDefs.files(check, 8);
		var pristineEnums = RoundTripDefs.files(v241, 8);
		for (int en : enumEdits.keySet()) {
			EnumCodec.Enum want = EnumCodec.decode(pristineEnums.get(en));
			enumEdits.get(en).accept(want);
			if (!Arrays.equals(checkEnums.get(en), EnumCodec.encode(want))) throw new IllegalStateException("enum " + en + " differs after write");
		}		Files.writeString(Path.of(a[6]), "{\n  \"stage\": \"clientscripts\",\n  \"customScriptsWritten\": " + customs + ",\n  \"importedVanillaKept\": " + imported
			+ ",\n  \"vanillaEdits\": {\n" + String.join(",\n", edits) + "\n  },\n  \"iconstRemaps\": [\n" + String.join(",\n", iconstRemaps)
			+ "\n  ],\n  \"callsToScriptsMissingInLive231\": {\n" + String.join(",\n", missingCalls) + "\n  }\n}\n");
		System.out.println("RESULT scripts: custom " + customs + " written, imported vanilla kept " + imported + ", vanilla edits handled " + edits.size()
			+ ", ICONST remaps " + iconstRemaps.size() + ", groups written+verified " + written.size());
	}

	/** equal as code: instructions (incl. text and jump targets), args, locals and switch tables */
	static boolean sameScript(ScriptAsm x, ScriptAsm y) {
		if (x.code.size() != y.code.size() || x.intArgs != y.intArgs || x.objArgs != y.objArgs || x.longArgs != y.longArgs
			|| x.localInt != y.localInt || x.localObj != y.localObj || x.localLong != y.localLong || !x.switches.equals(y.switches)) return false;
		for (int i = 0; i < x.code.size(); i++) if (!x.code.get(i).same(y.code.get(i))) return false;
		return true;
	}

	/** instruction key that ignores local-variable slots and branch targets (both renumber when Jagex edits a script) */
	static String norm(ScriptAsm.Insn n) {
		boolean slot = n.op == 33 || n.op == 34 || n.op == 35 || n.op == 36 || n.op == 66 || n.op == 67;
		return n.op + ":" + (slot || ScriptAsm.isJump(n.op) || n.op == ScriptAsm.SWITCH ? "" : n.i + ":" + n.l + ":" + n.s);
	}

	/** ours[from, to) appears contiguously in 241 (normalised) */
	static void requireContained(int id, ScriptAsm ours, int from, int to, ScriptAsm at) {
		outer:
		for (int j = 0; j + (to - from) <= at.code.size(); j++) {
			for (int k = 0; k < to - from; k++) if (!norm(ours.code.get(from + k)).equals(norm(at.code.get(j + k)))) continue outer;
			return;
		}
		throw new IllegalStateException("script " + id + ": Zelus code [" + from + "," + to + ") not found in 241; the hand port is wrong");
	}

	interface PortAction { void apply(ScriptAsm ours, ScriptAsm base, ScriptAsm theirs, Map<Integer, java.util.function.Consumer<EnumCodec.Enum>> enumEdits); }

	record HandPort(String note, PortAction action) {
		void apply(ScriptAsm o, ScriptAsm b, ScriptAsm t, Map<Integer, java.util.function.Consumer<EnumCodec.Enum>> e) { action.apply(o, b, t, e); }
	}

	/** Zelus edits on scripts Jagex rewrote, resolved by hand; each one verifies its premise against 241. */
	static final Map<Integer, HandPort> HAND_PORTS = Map.of(
		2103, new HandPort("Zelus edit (get_varp 1683 + invoke 2628) is part of Jagex's 241 version", (o, b, t, e) -> requireContained(2103, o, 16, 21, t)),
		2820, new HandPort("Zelus edit (Remove-Placeholder op for placeholders) is part of Jagex's 241 version", (o, b, t, e) -> requireContained(2820, o, 279, 291, t)),
		5743, new HandPort("Zelus edit (transmit varp 68) is part of Jagex's 241 version, which transmits more varps", (o, b, t, e) -> {
			boolean has = false;
			for (ScriptAsm.Insn n : t.code) if (n.op == 0 && n.i == 68) has = true;
			if (!has) throw new IllegalStateException("script 5743: 241 does not transmit varp 68");
		}),
		907, new HandPort("Jagex moved these tooltips/icons into enums 836 (text) and 837 (graphic); Zelus's values ported there", (o, b, t, e) -> {
			requireEnumLookup(t, 836);
			requireEnumLookup(t, 837);
			e.put(836, en -> { EnumCodec.set(en.strings, 1, "Quest List", "Server Info"); EnumCodec.set(en.strings, 4, "Leagues", "Wilderness"); });
			e.put(837, en -> {
				EnumCodec.set(en.ints, 0, 2309, 1709); EnumCodec.set(en.ints, 1, 776, 908); EnumCodec.set(en.ints, 4, 4839, 168);
				if (en.defaultInt != 2309) throw new IllegalStateException("enum 837 default changed");
				en.defaultInt = 1709;
			});
		}));

	/** script 907 must still pick its tooltip/icon from the enum by varbit 8168 (iconst enumId; get_varbit 8168) */
	static void requireEnumLookup(ScriptAsm t, int enumId) {
		for (int j = 0; j + 1 < t.code.size(); j++)
			if (t.code.get(j).op == 0 && t.code.get(j).i == enumId && t.code.get(j + 1).op == 25 && t.code.get(j + 1).i == 8168) return;
		throw new IllegalStateException("script 907 no longer looks up enum " + enumId + " by varbit 8168");
	}

	static void writeConfigGroup(Store out, int group, Map<Integer, byte[]> files) throws Exception {
		Index index = out.findIndex(2);
		Archive archive = index.getArchive(group);
		FileData[] fd = new FileData[files.size()];
		List<byte[]> contents = new ArrayList<>();
		int i = 0;
		for (var f : new TreeMap<>(files).entrySet()) { fd[i] = new FileData(); fd[i].setId(f.getKey()); i++; contents.add(f.getValue()); }
		byte[] raw = SpliceItemOption.joinChunks(contents);
		Container c = new Container(archive.getCompression(), -1);
		c.compress(raw, null);
		out.getStorage().store(2, group, c.data);
		archive.setFileData(fd);
		archive.setCrc(c.crc);
		archive.setRevision(archive.getRevision() + 1);
		archive.setCompressedSize(c.data.length);
		archive.setDecompressedSize(raw.length);
		IndexData id = index.toIndexData();
		Container ic = new Container(index.getCompression(), -1);
		ic.compress(id.writeIndexData(), null);
		out.getStorage().store(255, 2, ic.data);
		index.setCrc(ic.crc);
	}

}

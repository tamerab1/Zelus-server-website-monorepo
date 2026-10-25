import net.runelite.cache.fs.*;
import net.runelite.cache.index.FileData;
import net.runelite.cache.index.IndexData;

import java.io.File;
import java.nio.file.*;
import java.util.*;

/**
 * Stage 1 of the rev 241 cache build: config definitions (index 2).
 * Writes into an existing copy of the vanilla rev 241 cache (outDir); never touches the inputs.
 *
 *  - custom definitions (ids absent from vanilla 231) go to their manifest id, rewritten so every id
 *    inside points at the remapped content; empty (single 0x00) padding entries are skipped
 *  - vanilla definitions Zelus edited are three-way merged (ConfigMerge); conflicts keep our value
 *    and are listed in the report
 *  - varp definitions are filled up to the highest remapped varp, since the rev 241 client sizes its
 *    varp arrays from them (our varbits' base varps 4829-4836 had no definition at all)
 *
 * args: localCache v231Cache outDir(rev 241 copy) manifest reportJson
 */
public class BuildConfigs241 {
	static final Map<String, Integer> TYPES = new LinkedHashMap<>(RoundTripDefs.GROUPS);

	/** custom (absent from vanilla 231) ids that are Jagex content vanilla 241 now has at the same id */
	static final Map<String, Set<Integer>> JAGEX_NATIVE_IN_241 = Map.of("npc", Set.of(16305, 16306, 16307, 16308));

	public static void main(String[] a) throws Exception {
		Store local = new Store(new File(a[0])); local.load();
		Store v231 = new Store(new File(a[1])); v231.load();
		Store out = new Store(new File(a[2])); out.load();
		Manifest manifest = new Manifest(Path.of(a[3]));

		Map<Integer, byte[]> paramDefs = new HashMap<>(RoundTripDefs.files(out, 11));
		RoundTripDefs.files(local, 11).forEach(paramDefs::putIfAbsent);
		DefRewriter.ParamKinds pk = DefRewriter.paramKinds(paramDefs);

		StringBuilder report = new StringBuilder("{\n  \"stage\": \"configs\",\n  \"types\": {\n");
		List<String> conflictLines = new ArrayList<>();
		int seqSoundFixes = 0, itemLinksReordered = 0, jagexNative = 0;
		Map<Integer, Map<Integer, byte[]>> newGroups = new LinkedHashMap<>();
		Map<Integer, Map<Integer, byte[]>> expectedCustom = new HashMap<>();

		int ti = 0;
		for (var t : TYPES.entrySet()) {
			String type = t.getKey();
			int group = t.getValue();
			var ours = RoundTripDefs.files(local, group);
			var base = RoundTripDefs.files(v231, group);
			var theirs = RoundTripDefs.files(out, group);
			TreeMap<Integer, byte[]> result = new TreeMap<>(theirs);
			Map<Integer, byte[]> expected = new HashMap<>();
			int customs = 0, emptySkipped = 0, merged = 0, conflicts = 0, artifactOnly = 0, importedKept = 0;
			for (var e : ours.entrySet()) {
				int id = e.getKey();
				byte[] def = e.getValue();
				if (!base.containsKey(id)) {
					if (def.length == 1 && def[0] == 0) { emptySkipped++; continue; }
					int newId = manifest.map(type, id);
					if (newId == id && theirs.containsKey(id) && manifest.isImported(type, id)) { importedKept++; continue; } // Jagex content imported early: keep 241's
					if (theirs.containsKey(newId)) throw new IllegalStateException(type + " " + id + " -> " + newId + " is a vanilla 241 id");
					byte[] bytes = DefRewriter.rewrite(type, def, manifest, pk);
					// Jagex content Zelus imported early from another cache, which vanilla 241 now ships at the same id:
					// write Jagex's own definition at our (moved) id, so it uses Jagex's native models and animations
					// instead of the moved duplicates of our import (Mad Angel 16305-16308 -> 22026-22029)
					if (JAGEX_NATIVE_IN_241.getOrDefault(type, Set.of()).contains(id) && theirs.containsKey(id)) { bytes = theirs.get(id); jagexNative++; }
					if (type.equals("seq")) {
						byte[] fixed = fixSwappedSounds(bytes);
						if (!Arrays.equals(fixed, bytes)) seqSoundFixes++;
						bytes = fixed;
					}
					if (result.put(newId, bytes) != null) throw new IllegalStateException(type + " " + newId + " written twice");
					expected.put(newId, bytes);
					customs++;
					continue;
				}
				if (ConfigMerge.sameFields(type, base.get(id), def, pk)) continue;
				ConfigMerge.Result r = ConfigMerge.merge(type, base.get(id), def, theirs.get(id), manifest, pk);
				if (r.oursChanged.isEmpty()) { artifactOnly++; continue; }
				result.put(id, r.bytes);
				merged++;
				if (!r.conflicts.isEmpty()) {
					conflicts++;
					conflictLines.add("    {\"type\": \"" + type + "\", \"id\": " + id + ", \"fields\": \"" + String.join(",", r.conflicts) + "\"}");
				}
			}
			if (type.equals("item")) {
				for (var f : result.entrySet()) {
					byte[] ordered = DefRewriter.orderItemLinks241(f.getValue(), pk);
					if (ordered != f.getValue()) {
						f.setValue(ordered);
						if (expected.containsKey(f.getKey())) expected.put(f.getKey(), ordered);
						itemLinksReordered++;
					}
				}
			}
			newGroups.put(group, result);
			expectedCustom.put(group, expected);
			report.append(String.format("    \"%s\": {\"group\": %d, \"vanilla241\": %d, \"customsAdded\": %d, \"importedVanillaKept\": %d, \"emptyCustomsSkipped\": %d, \"vanillaEditsMerged\": %d, \"conflictsOursKept\": %d, \"packerArtifactOnlySkipped\": %d, \"total\": %d}%s%n",
				type, group, theirs.size(), customs, importedKept, emptySkipped, merged, conflicts, artifactOnly, result.size(), ++ti < TYPES.size() ? "," : ","));
		}

		// varps: our custom varp(s) at their new ids + empty definitions up to the highest varp in use
		{
			var ours = RoundTripDefs.files(local, 16);
			var base = RoundTripDefs.files(v231, 16);
			var theirs = RoundTripDefs.files(out, 16);
			TreeMap<Integer, byte[]> result = new TreeMap<>(theirs);
			int customs = 0;
			for (var e : ours.entrySet()) {
				if (base.containsKey(e.getKey())) continue;
				result.put(manifest.map("varp", e.getKey()), e.getValue().clone());
				customs++;
			}
			int maxVarp = Math.max(result.lastKey(), manifest.maps.get("varp").values().stream().max(Integer::compare).orElse(0));
			int filled = 0;
			for (int id = 0; id <= maxVarp; id++) if (result.putIfAbsent(id, new byte[]{0}) == null) filled++;
			newGroups.put(16, result);
			report.append(String.format("    \"varp\": {\"group\": 16, \"vanilla241\": %d, \"customsAdded\": %d, \"emptyDefsFilled\": %d, \"maxVarp\": %d, \"total\": %d}%n",
				theirs.size(), customs, filled, maxVarp, result.size()));
		}
		report.append("  },\n  \"seqCustomSoundOrderFixed\": ").append(seqSoundFixes).append(",\n  \"conflicts\": [\n")
			.append(String.join(",\n", conflictLines)).append("\n  ]\n}\n");

		// ---- write
		Index index = out.getIndex(net.runelite.cache.IndexType.CONFIGS);
		for (var g : newGroups.entrySet()) {
			Archive archive = index.getArchive(g.getKey());
			TreeMap<Integer, byte[]> files = new TreeMap<>(g.getValue());
			FileData[] fd = new FileData[files.size()];
			List<byte[]> contents = new ArrayList<>();
			int i = 0;
			for (var f : files.entrySet()) {
				fd[i] = new FileData();
				fd[i].setId(f.getKey());
				i++;
				contents.add(f.getValue());
			}
			byte[] raw = SpliceItemOption.joinChunks(contents);
			Container c = new Container(archive.getCompression(), -1);
			c.compress(raw, null);
			out.getStorage().store(index.getId(), archive.getArchiveId(), c.data);
			archive.setFileData(fd);
			archive.setCrc(c.crc);
			archive.setRevision(archive.getRevision() + 1);
			archive.setCompressedSize(c.data.length);
			archive.setDecompressedSize(raw.length);
		}
		IndexData indexData = index.toIndexData();
		byte[] rawIndex = indexData.writeIndexData();
		Container ic = new Container(index.getCompression(), -1);
		ic.compress(rawIndex, null);
		out.getStorage().store(255, index.getId(), ic.data);
		index.setCrc(ic.crc);
		out.close();

		// ---- verify from disk
		Store check = new Store(new File(a[2])); check.load();
		int verified = 0;
		for (var g : newGroups.entrySet()) {
			String type = g.getKey() == 16 ? null : TYPES.entrySet().stream().filter(x -> x.getValue().equals(g.getKey())).findFirst().get().getKey();
			var onDisk = RoundTripDefs.files(check, g.getKey());
			if (!onDisk.keySet().equals(g.getValue().keySet())) throw new IllegalStateException("group " + g.getKey() + ": ids differ after write");
			for (var f : onDisk.entrySet()) {
				if (!Arrays.equals(f.getValue(), g.getValue().get(f.getKey()))) throw new IllegalStateException("group " + g.getKey() + " file " + f.getKey() + " differs after write");
				if (type != null) DefRewriter.rewrite(type, f.getValue(), DefRewriter.IDENTITY, pk); // grammar-valid
				verified++;
			}
			for (var ex : expectedCustom.getOrDefault(g.getKey(), Map.of()).entrySet())
				if (!Arrays.equals(onDisk.get(ex.getKey()), ex.getValue())) throw new IllegalStateException("custom " + ex.getKey() + " wrong");
		}
		Files.writeString(Path.of(a[4]), report.toString());
		System.out.println("RESULT configs written and verified: " + verified + " definitions across " + newGroups.size() + " groups; conflicts " + conflictLines.size() + "; seq sound fixes " + seqSoundFixes + "; item links reordered for 241 " + itemLinksReordered + "; Jagex-native defs used " + jagexNative);
	}

	/** Custom seq sounds written by the packer as (weight 1, loops 100): swap back to (100, 1). */
	static byte[] fixSwappedSounds(byte[] def) {
		List<byte[]> segs = DefRewriter.segments("seq", def, DefRewriter.IDENTITY, k -> null);
		java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
		for (byte[] s : segs) {
			if ((s[0] & 0xff) == 14) {
				s = s.clone();
				int n = ((s[1] & 0xff) << 8) | (s[2] & 0xff);
				for (int i = 0; i < n; i++) {
					int w = 3 + i * 8 + 4;
					if ((s[w] & 0xff) == 1 && (s[w + 1] & 0xff) == 100) { s[w] = 100; s[w + 1] = 1; }
				}
			}
			o.writeBytes(s);
		}
		o.write(0);
		return o.toByteArray();
	}
}

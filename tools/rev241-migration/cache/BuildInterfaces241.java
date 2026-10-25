import net.runelite.cache.fs.*;
import net.runelite.cache.index.FileData;
import net.runelite.cache.index.IndexData;

import java.io.File;
import java.nio.file.*;
import java.util.*;
import java.util.function.IntUnaryOperator;

/**
 * Stage 4 of the rev 241 cache build: interfaces (index 3).
 *
 *  custom interfaces (absent from vanilla 231): components converted to the rev 241 format
 *    (i32 model ids) and every id inside remapped.
 *  vanilla interfaces Zelus edited: component-level three-way merge (231 / ours / 241).
 *    - a component only one side changed takes that side
 *    - components we added whose id Jagex also used in 241 are renumbered to free ids; parents
 *      and hook arguments inside the interface follow; the move is listed in componentRemap
 *    - both sides changed: explicit resolutions below, otherwise ours (listed)
 *    - 119 and 873 were rebuilt by Zelus and take our version outright
 *    - components whose only difference is the 2 trailing bytes our packer dropped take 241
 *
 * args: localCache v231Cache pristineV241Cache outDir manifest report.json
 */
public class BuildInterfaces241 {
	static final Set<Integer> OURS_OUTRIGHT = Set.of(119, 873);

	/** "group:component" -> resolution when both sides changed a component */
	static final Map<String, String> RESOLUTIONS = new LinkedHashMap<>();
	static {
		// bank: Zelus turned this empty container into the "Presets" button; in 241 it is a resized
		// container of Jagex's new bank layout. Keep 241's and give our button a new component.
		RESOLUTIONS.put("12:120", "theirs+moveOurs");
		// orbs: we only hid it; Jagex re-laid it out
		RESOLUTIONS.put("160:49", "theirs+hidden");
		// fixed-mode frame icon: both replaced the sprite (cosmetic); keep 241's
		RESOLUTIONS.put("548:55", "theirs");
		// spellbook: our edit is the "Previous" option (slot 8); Jagex reworked the spellbook
		RESOLUTIONS.put("218:7", "theirs+action:8:Previous");
		RESOLUTIONS.put("218:103", "theirs+action:8:Previous");
		// our other spellbook "changes" are index shifts from one removed component
		for (int c : new int[]{0, 148, 193, 194, 195, 196, 197, 198, 199, 200, 201, 202, 203}) RESOLUTIONS.put("218:" + c, "theirs");
	}

	/**
	 * Components whose parent container was renumbered by Jagex: "group:newComponent" -> parent child index.
	 * The bank's Presets button lived in 231 container 12:79; the 241 equivalent is 12:61 (same shape
	 * and parent, hidden, the third of three identical containers, and exactly the same 23 children).
	 */
	static final Map<String, Integer> REPARENT = Map.of("12:158", 61);

	public static void main(String[] a) throws Exception {
		Store local = new Store(new File(a[0])); local.load();
		Store v231 = new Store(new File(a[1])); v231.load();
		Store v241 = new Store(new File(a[2])); v241.load();
		Store out = new Store(new File(a[3])); out.load();
		Manifest manifest = new Manifest(Path.of(a[4]));

		Set<Integer> movingIds = new HashSet<>();
		for (String k : List.of("item", "npc", "obj", "varbit", "seq", "spotanim"))
			manifest.maps.get(k).forEach((o, n) -> { if (!o.equals(n)) movingIds.add(o); });

		var L = RoundTripInterfaces.components(local);
		var B = RoundTripInterfaces.components(v231);
		var T = RoundTripInterfaces.components(v241);

		Map<Integer, TreeMap<Integer, byte[]>> outGroups = new TreeMap<>();
		List<String> remapLines = new ArrayList<>(), decisions = new ArrayList<>(), suspicious = new ArrayList<>();
		int customGroups = 0, customComps = 0;

		for (var g : L.entrySet()) {
			int gid = g.getKey();
			if (B.containsKey(gid)) continue;
			if (T.containsKey(gid)) throw new IllegalStateException("custom interface " + gid + " exists in vanilla 241");
			TreeMap<Integer, byte[]> res = new TreeMap<>();
			for (var c : g.getValue().entrySet()) {
				InterfaceRewriter r = InterfaceRewriter.run(c.getValue(), manifest, false, true, movingIds::contains);
				res.put(c.getKey(), r.bytes());
				for (String s : r.suspicious) suspicious.add("      \"" + gid + ":" + c.getKey() + " " + s + "\"");
				customComps++;
			}
			outGroups.put(gid, res);
			customGroups++;
		}

		int mergedGroups = 0;
		for (var g : L.entrySet()) {
			int gid = g.getKey();
			if (!B.containsKey(gid)) continue;
			var lb = g.getValue();
			var bb = B.get(gid);
			var tb = T.get(gid);
			// convert base to 241 format for comparison
			Map<Integer, byte[]> base = new TreeMap<>();
			for (var c : bb.entrySet()) base.put(c.getKey(), InterfaceRewriter.run(c.getValue(), DefRewriter.IDENTITY, false, true, v -> false).bytes());
			Map<Integer, byte[]> oursPlain = new TreeMap<>();
			for (var c : lb.entrySet()) oursPlain.put(c.getKey(), InterfaceRewriter.run(c.getValue(), manifest, false, true, v -> false).bytes());
			boolean edited = !oursPlain.keySet().equals(base.keySet());
			if (!edited) for (var e : base.entrySet()) if (!sameIgnoringDroppedTail(e.getValue(), oursPlain.get(e.getKey()))) { edited = true; break; }
			if (!edited) continue;
			mergedGroups++;

			if (OURS_OUTRIGHT.contains(gid)) {
				TreeMap<Integer, byte[]> res = new TreeMap<>();
				for (var c : lb.entrySet()) {
					InterfaceRewriter r = InterfaceRewriter.run(c.getValue(), manifest, false, true, movingIds::contains);
					res.put(c.getKey(), r.bytes());
					for (String s : r.suspicious) suspicious.add("      \"" + gid + ":" + c.getKey() + " " + s + "\"");
				}
				outGroups.put(gid, res);
				decisions.add("    \"" + gid + "\": \"ours outright (" + res.size() + " components; 241 had " + tb.size() + ")\"");
				continue;
			}

			// renumbering: our added components whose id Jagex also uses, plus explicit moves
			int next = Math.max(Collections.max(tb.keySet()), Collections.max(lb.keySet())) + 1;
			Map<Integer, Integer> moved = new TreeMap<>();
			for (int k : lb.keySet()) if (!base.containsKey(k) && tb.containsKey(k)) moved.put(k, next++);
			for (var res : RESOLUTIONS.entrySet())
				if (res.getKey().startsWith(gid + ":") && res.getValue().endsWith("moveOurs")) moved.put(Integer.parseInt(res.getKey().split(":")[1]), next++);
			IntUnaryOperator compMap = full -> {
				if ((full >>> 16) != gid) return full;
				Integer n = moved.get(full & 0xFFFF);
				return n == null ? full : (gid << 16) | n;
			};
			Map<Integer, byte[]> ours = new TreeMap<>();
			for (var c : lb.entrySet()) {
				InterfaceRewriter r = InterfaceRewriter.run(c.getValue(), manifest, false, true, movingIds::contains, gid, compMap);
				ours.put(c.getKey(), r.bytes());
				for (String s : r.suspicious) suspicious.add("      \"" + gid + ":" + c.getKey() + " " + s + "\"");
			}

			TreeMap<Integer, byte[]> res = new TreeMap<>();
			Set<Integer> keys = new TreeSet<>(base.keySet());
			keys.addAll(ours.keySet());
			keys.addAll(tb.keySet());
			List<String> both = new ArrayList<>();
			for (int k : keys) {
				byte[] b = base.get(k), o = ours.get(k), t = tb.get(k);
				if (moved.containsKey(k) && o != null) {
					res.put(moved.get(k), o);
					remapLines.add("    {\"from\": " + ((gid << 16) | k) + ", \"to\": " + ((gid << 16) | moved.get(k)) + ", \"name\": \"" + gid + ":" + k + " -> " + gid + ":" + moved.get(k) + "\"}");
					if (!base.containsKey(k)) { if (t != null) res.put(k, t); continue; } // our addition moved; keep 241's at k
				}
				boolean oc = b == null ? o != null : !sameIgnoringDroppedTail(b, o);
				boolean tc = !Arrays.equals(b, t);
				byte[] pick;
				if (!oc) pick = t;
				else if (!tc) pick = moved.containsKey(k) ? t : o;
				else {
					String rule = RESOLUTIONS.getOrDefault(gid + ":" + k, "ours");
					both.add(k + "=" + rule);
					if (rule.startsWith("theirs+action:")) {
						String[] p = rule.split(":");
						pick = InterfaceRewriter.withAction(t, Integer.parseInt(p[1]), p[2]);
					} else if (rule.equals("theirs+hidden")) pick = InterfaceRewriter.withHidden(t, true);
					else if (rule.startsWith("theirs")) pick = t;
					else pick = o;
				}
				if (pick != null && !res.containsKey(k)) res.put(k, pick);
			}
			for (var rp : REPARENT.entrySet()) {
				String[] p = rp.getKey().split(":");
				if (Integer.parseInt(p[0]) != gid || !res.containsKey(Integer.parseInt(p[1]))) continue;
				byte[] comp = res.get(Integer.parseInt(p[1])).clone();
				InterfaceRewriter r = InterfaceRewriter.run(comp, DefRewriter.IDENTITY, true, true, v -> false);
				comp[r.parentPos] = (byte) (rp.getValue() >> 8);
				comp[r.parentPos + 1] = (byte) (int) rp.getValue();
				res.put(Integer.parseInt(p[1]), comp);
			}
			outGroups.put(gid, res);
			decisions.add("    \"" + gid + "\": \"merged: " + res.size() + " components (base " + base.size() + ", ours " + lb.size() + ", 241 " + tb.size()
				+ "); moved " + moved + "; both-changed " + both + "\"");
		}

		// ---- write
		Index index = out.findIndex(3);
		for (var e : outGroups.entrySet()) {
			Archive ar = index.getArchive(e.getKey());
			if (ar == null) {
				ar = index.addArchive(e.getKey());
				ar.setCompression(net.runelite.cache.fs.jagex.CompressionType.GZ);
			}
			FileData[] fd = new FileData[e.getValue().size()];
			List<byte[]> contents = new ArrayList<>();
			int i = 0;
			for (var c : e.getValue().entrySet()) { fd[i] = new FileData(); fd[i].setId(c.getKey()); i++; contents.add(c.getValue()); }
			byte[] raw = contents.size() == 1 ? contents.get(0) : SpliceItemOption.joinChunks(contents);
			Container cont = new Container(ar.getCompression(), -1);
			cont.compress(raw, null);
			out.getStorage().store(3, e.getKey(), cont.data);
			ar.setFileData(fd);
			ar.setCrc(cont.crc);
			ar.setRevision(ar.getRevision() + 1);
			ar.setCompressedSize(cont.data.length);
			ar.setDecompressedSize(raw.length);
		}
		IndexData idx = index.toIndexData();
		byte[] rawIndex = idx.writeIndexData();
		Container ic = new Container(index.getCompression(), -1);
		ic.compress(rawIndex, null);
		out.getStorage().store(255, 3, ic.data);
		index.setCrc(ic.crc);
		out.close();

		// ---- verify
		Store check = new Store(new File(a[3]));
		check.load();
		var C = RoundTripInterfaces.components(check);
		int verified = 0;
		for (var e : outGroups.entrySet()) {
			var onDisk = C.get(e.getKey());
			if (!onDisk.keySet().equals(e.getValue().keySet())) throw new IllegalStateException("interface " + e.getKey() + " components differ after write");
			for (var c : onDisk.entrySet()) {
				if (!Arrays.equals(c.getValue(), e.getValue().get(c.getKey()))) throw new IllegalStateException("component " + e.getKey() + ":" + c.getKey() + " differs");
				InterfaceRewriter.run(c.getValue(), DefRewriter.IDENTITY, true, true, v -> false); // valid 241 grammar
				verified++;
			}
		}
		Files.writeString(Path.of(a[5]), "{\n  \"stage\": \"interfaces\",\n  \"customInterfaces\": " + customGroups + ",\n  \"customComponents\": " + customComps
			+ ",\n  \"vanillaInterfacesMerged\": " + mergedGroups + ",\n  \"decisions\": {\n" + String.join(",\n", decisions) + "\n  },\n  \"componentRemap\": [\n"
			+ String.join(",\n", remapLines) + "\n  ],\n  \"hookArgsEqualToMovingIds\": {\n    \"count\": " + suspicious.size() + ",\n    \"items\": [\n"
			+ String.join(",\n", suspicious.subList(0, Math.min(400, suspicious.size()))) + "\n    ]\n  }\n}\n");
		System.out.println("RESULT interfaces: custom " + customGroups + " (" + customComps + " components), vanilla merged " + mergedGroups
			+ ", components verified " + verified + ", component moves " + remapLines.size() + ", hook args equal to moving ids " + suspicious.size());
	}

	/** equal, or ours is base minus the 2 trailing bytes our packer dropped from a few IF3 model components */
	static boolean sameIgnoringDroppedTail(byte[] base, byte[] ours) {
		if (ours == null || base == null) return base == ours;
		if (Arrays.equals(base, ours)) return true;
		return base.length == ours.length + 2 && (base[0] & 0xff) == 0xff && (base[1] & 0xff) == 6
			&& Arrays.equals(Arrays.copyOf(base, ours.length), ours);
	}
}

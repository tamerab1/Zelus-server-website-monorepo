import com.google.gson.*;
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
 *  vanilla interfaces Zelus edited (AlignOurs231 says how our ids line up with vanilla 231):
 *    - "overwritten" (Zelus's own interface under a vanilla id, incl. 119/873): ours
 *    - "aligned": 241's components; each component we edited goes to the 241 position of the vanilla
 *      component it edits (Jagex renumbered many groups); if Jagex changed it too, the explicit
 *      resolutions below apply, otherwise ours (listed)
 *    - components we added whose id 241 uses are renumbered to free ids (listed in componentRemap)
 *    - components whose only difference is the 2 trailing bytes our packer dropped take 241
 *  every component reference inside our components (parents, hook args, in any group) follows
 *  our moves, the alignment and Jagex's renumbering.
 *
 * args: localCache v231Cache pristineV241Cache outDir manifest report.json ours-alignment-231.json component-map-241.json
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
		// spellbook: our edit is the "Previous" option (slot 8) on the four home teleports (ours 7/103/104/148
		// = standard/Zaros/Lunar/Arceuus); Jagex reworked the spellbook. Our edits to 0/193/202 are only the
		// references shifted by the component our cache lacks (vanilla 193), so 241's own stand.
		for (int c : new int[]{7, 103, 104, 148}) RESOLUTIONS.put("218:" + c, "theirs+action:8:Previous");
		for (int c : new int[]{0, 193, 202}) RESOLUTIONS.put("218:" + c, "theirs");
	}

	/** AlignOurs231 output for one group: ours -> vanilla 231 component ids */
	static final class Align {
		boolean overwritten;
		final Set<Integer> edited = new HashSet<>(), custom = new HashSet<>();
		final Map<Integer, Integer> align = new HashMap<>();
	}

	static final Map<Integer, Align> aligns = new HashMap<>();
	static final Map<Integer, Map<Integer, Integer>> vanillaMoved = new HashMap<>(), customMoves = new HashMap<>();
	static final Map<Integer, Set<Integer>> vanillaRemoved = new HashMap<>();
	static final int[] refChanges = {0};

	/** where vanilla 231 component group:c is in 241; null when Jagex removed it */
	static Integer vanillaTo(int group, int c) {
		if (vanillaRemoved.getOrDefault(group, Set.of()).contains(c)) return null;
		return vanillaMoved.getOrDefault(group, Map.of()).getOrDefault(c, c);
	}

	/**
	 * Loads AlignOurs231's alignment and Jagex's renumbering, works out where our own components in aligned
	 * groups go, and returns the operator every component reference made from our layout goes through (stage 4
	 * for parents and hook args, stage 5 for ICONSTs in our scripts).
	 */
	static IntUnaryOperator loadRefs(Path alignPath, Path vmapPath, Map<Integer, Map<Integer, byte[]>> L,
			Map<Integer, Map<Integer, byte[]>> B, Map<Integer, Map<Integer, byte[]>> T) throws java.io.IOException {
		aligns.clear(); vanillaMoved.clear(); vanillaRemoved.clear(); customMoves.clear();
		// ours -> vanilla 231 alignment (AlignOurs231) and Jagex's 231 -> 241 renumbering (gameval names)
		JsonObject alignJson = JsonParser.parseString(Files.readString(alignPath)).getAsJsonObject();
		JsonObject vmapJson = JsonParser.parseString(Files.readString(vmapPath)).getAsJsonObject();
		for (var e : alignJson.entrySet()) {
			JsonObject o = e.getValue().getAsJsonObject();
			Align al = new Align();
			al.overwritten = o.get("mode").getAsString().equals("overwritten");
			if (!al.overwritten) {
				o.getAsJsonArray("edited").forEach(x -> al.edited.add(x.getAsInt()));
				o.getAsJsonArray("custom").forEach(x -> al.custom.add(x.getAsInt()));
				o.getAsJsonObject("align").entrySet().forEach(x -> al.align.put(Integer.parseInt(x.getKey()), x.getValue().getAsInt()));
			}
			aligns.put(Integer.parseInt(e.getKey()), al);
		}
		for (var e : vmapJson.entrySet()) {
			JsonObject o = e.getValue().getAsJsonObject();
			if (o.has("groupRemoved")) continue;
			int gid = Integer.parseInt(e.getKey());
			Map<Integer, Integer> mv = new HashMap<>();
			o.getAsJsonObject("moved").entrySet().forEach(x -> mv.put(Integer.parseInt(x.getKey()), x.getValue().getAsInt()));
			Set<Integer> rm = new HashSet<>();
			o.getAsJsonObject("removed").keySet().forEach(x -> rm.add(Integer.parseInt(x)));
			vanillaMoved.put(gid, mv);
			vanillaRemoved.put(gid, rm);
		}
		// our own components in aligned vanilla groups: moved to fresh ids where 241 uses the id, plus explicit moves
		for (var e : aligns.entrySet()) {
			int gid = e.getKey();
			if (e.getValue().overwritten || !T.containsKey(gid)) continue;
			var tb = T.get(gid);
			int next = Math.max(Collections.max(tb.keySet()), Collections.max(L.get(gid).keySet())) + 1;
			Map<Integer, Integer> moved = new TreeMap<>();
			// children draw in component id order, so once one of ours collides every later one of ours moves
				// with it, in order: moving only the colliding ones put the Zelus Teleports orb's frame (160:55/56 ->
				// 60/61) above its icon (58, unmoved) and the orb drew as a blank circle
				var customs = new TreeSet<>(e.getValue().custom);
				Integer firstCollision = customs.stream().filter(tb::containsKey).findFirst().orElse(null);
				if (firstCollision != null) {
					// the block starts right after the highest id that stays (241's, or ours below the collision): a group
					// with a hole in its component ids NPEs the client when the interface opens (160 at 59-63 left 57/58
					// empty and broke login)
					next = Math.max(Collections.max(tb.keySet()), customs.headSet(firstCollision).stream().max(Integer::compare).orElse(-1)) + 1;
					for (int k : customs.tailSet(firstCollision)) moved.put(k, next++);
				}
			for (var r : RESOLUTIONS.entrySet())
				if (r.getKey().startsWith(gid + ":") && r.getValue().endsWith("moveOurs")) moved.put(Integer.parseInt(r.getKey().split(":")[1]), next++);
			customMoves.put(gid, moved);
		}
		// every component reference inside our components (parents, hook args) follows: our own moves, then the
		// alignment to vanilla 231, then Jagex's renumbering. Hook args below 0x10000 are plain ints, never ids.
		return full -> {
			int g = full >>> 16, c = full & 0xFFFF;
			if (g == 0) return full;
			Align al = aligns.get(g);
			int to;
			if (al != null) {
				if (al.overwritten) return full;
				Map<Integer, Integer> mv = customMoves.getOrDefault(g, Map.of());
				if (mv.containsKey(c)) to = mv.get(c);
				else if (al.custom.contains(c)) to = c;
				else { Integer t = vanillaTo(g, al.align.getOrDefault(c, c)); to = t == null ? c : t; }
			} else if (B.containsKey(g) && T.containsKey(g)) {
				Integer t = vanillaTo(g, c);
				to = t == null ? c : t;
			} else return full;
			if (to != c) refChanges[0]++;
			return g << 16 | to;
		};
	}

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

		IntUnaryOperator refs = loadRefs(Path.of(a[6]), Path.of(a[7]), L, B, T);

		Map<Integer, TreeMap<Integer, byte[]>> outGroups = new TreeMap<>();
		List<String> remapLines = new ArrayList<>(), decisions = new ArrayList<>(), suspicious = new ArrayList<>();
		int customGroups = 0, customComps = 0;

		for (var g : L.entrySet()) {
			int gid = g.getKey();
			if (B.containsKey(gid)) continue;
			if (T.containsKey(gid)) throw new IllegalStateException("custom interface " + gid + " exists in vanilla 241");
			TreeMap<Integer, byte[]> res = new TreeMap<>();
			for (var c : g.getValue().entrySet()) {
				InterfaceRewriter r = InterfaceRewriter.run(c.getValue(), manifest, false, true, movingIds::contains, gid, refs);
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
			var tb = T.get(gid);
			Align al = aligns.get(gid);
			if (al == null) continue; // ours identical to vanilla 231: 241 as-is
			mergedGroups++;

			if (al.overwritten) {
				// Zelus's own interface under a vanilla id (or rebuilt outright): ours, with references into other
				// renumbered groups following the renumbering
				TreeMap<Integer, byte[]> res = new TreeMap<>();
				for (var c : lb.entrySet()) {
					InterfaceRewriter r = InterfaceRewriter.run(c.getValue(), manifest, false, true, movingIds::contains, gid, refs);
					res.put(c.getKey(), r.bytes());
					for (String s : r.suspicious) suspicious.add("      \"" + gid + ":" + c.getKey() + " " + s + "\"");
				}
				outGroups.put(gid, res);
				decisions.add("    \"" + gid + "\": \"ours outright (" + res.size() + " components; 241 had " + tb.size() + ")\"");
				continue;
			}

			// aligned: 241's components, plus our edits at the 241 position of the vanilla component each one edits,
			// plus our own components (moved where 241 uses the id)
			Map<Integer, Integer> moved = customMoves.get(gid);
			TreeMap<Integer, byte[]> res = new TreeMap<>(tb);
			List<String> both = new ArrayList<>(), placed = new ArrayList<>();
			for (var c : lb.entrySet()) {
				int k = c.getKey();
				byte[] o = InterfaceRewriter.run(c.getValue(), manifest, false, true, movingIds::contains, gid, refs).bytes();
				if (al.custom.contains(k)) {
					int to = moved.getOrDefault(k, k);
					res.put(to, o);
					if (to != k) remapLines.add("    {\"from\": " + ((gid << 16) | k) + ", \"to\": " + ((gid << 16) | to) + ", \"name\": \"" + gid + ":" + k + " -> " + gid + ":" + to + "\"}");
					continue;
				}
				int b = al.align.getOrDefault(k, k);
				if (!al.edited.contains(k)) continue; // same as vanilla 231 b: 241's component stands
				Integer t = vanillaTo(gid, b);
				if (t == null) { both.add(k + "=lost(removed in 241)"); continue; }
				boolean theirsChanged = !Arrays.equals(InterfaceShift.sig(B.get(gid).get(b), false), InterfaceShift.sig(tb.get(t), true));
				String rule = RESOLUTIONS.get(gid + ":" + k);
				if (rule == null || !theirsChanged && !rule.endsWith("moveOurs")) rule = theirsChanged ? "ours" : null;
				if (theirsChanged) both.add(k + "@" + t + "=" + rule);
				if (rule == null) res.put(t, o);
				else if (rule.startsWith("theirs+action:")) {
					String[] p = rule.split(":");
					res.put(t, InterfaceRewriter.withAction(tb.get(t), Integer.parseInt(p[1]), p[2]));
				} else if (rule.equals("theirs+hidden")) res.put(t, InterfaceRewriter.withHidden(tb.get(t), true));
				else if (rule.equals("theirs+moveOurs")) {
					int to = moved.get(k);
					res.put(to, o);
					remapLines.add("    {\"from\": " + ((gid << 16) | k) + ", \"to\": " + ((gid << 16) | to) + ", \"name\": \"" + gid + ":" + k + " -> " + gid + ":" + to + "\"}");
					continue;
				} else if (!rule.startsWith("theirs")) res.put(t, o);
				if (t != k) placed.add(k + "->" + t);
			}
			outGroups.put(gid, res);
			decisions.add("    \"" + gid + "\": \"merged: " + res.size() + " components (base " + B.get(gid).size() + ", ours " + lb.size() + ", 241 " + tb.size()
				+ "); moved " + moved + "; edits placed " + placed + "; both-changed " + both + "\"");
		}
		decisions.add("    \"hookOrParentRefsRenumbered\": " + refChanges[0]);

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
			// the client NPEs opening an interface with a hole in its component ids
			if (Collections.max(onDisk.keySet()) != onDisk.size() - 1) {
				// a gap we introduced in a merged vanilla group fails the build (160 broke login); custom interfaces
				// with a gap carried over from live 231 (1089) are only listed
				if (T.containsKey(e.getKey())) throw new IllegalStateException("interface " + e.getKey() + " has a gap in its component ids: " + new TreeSet<>(onDisk.keySet()));
				System.out.println("WARN custom interface " + e.getKey() + " has a gap in its component ids (as in live 231): " + onDisk.size() + " components, max id " + Collections.max(onDisk.keySet()));
			}
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

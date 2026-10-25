import net.runelite.cache.definitions.loaders.VarbitLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.ArchiveFiles;
import net.runelite.cache.fs.FSFile;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;

/**
 * Builds remap-241.json: for every cache id type, which of our custom ids collide with vanilla
 * rev 241, and where each one moves. Read-only on all three caches.
 *
 * custom   = id present in our cache but absent from vanilla rev 231 (OpenRS2 2247)
 * collides = custom id that vanilla rev 241 (OpenRS2 2746) now uses
 *
 * Allocation, per type:
 *  1. constant shift: move every colliding id by one delta, so arithmetic between custom ids
 *     (bond tier blocks, placeholder +100, ...) keeps working. The block starts at
 *     241max + headroom, where headroom = 2x the vanilla growth seen from 231 to 241 (~20 revs),
 *     and every target must be free (not vanilla 241, not one of our staying ids) and <= limit.
 *  2. if no shift fits under the limit (models, objects): pack the colliding ids against the
 *     limit, top-down, keeping their order, so future vanilla growth uses the gap first.
 * Models whose bytes equal a vanilla 241 model map onto that model instead of taking a slot.
 * IMPORTED VANILLA: a colliding "custom" id whose content equals vanilla 241 at the same id is
 * Jagex content Zelus imported early from a newer cache (e.g. inv 935, 108 objects, 61 items).
 * It keeps its id (identity, listed under "imported"): moving it would duplicate it and split our
 * server/saves from Jagex's own scripts and content that use the original id.
 * Varps also include "phantom" varps: ids that only exist as the base varp of a varbit (no varp
 * definition in our cache). Our custom varbits 17796-17803 store their bits in varps 4829-4836.
 *
 * args: localCache v231Cache v241Cache outJson
 */
public class BuildRemapManifest {

	record Type(String key, String label, int index, int group, int limit, String refNote) {}

	// group -1 = the index's groups are the ids; otherwise the files inside that group are.
	static final List<Type> TYPES = List.of(
		new Type("item", "items (config 10)", 2, 10, 65534, "inventories, banks, saves, drops, shops, item_info.json, enums/params, placeholders/notes"),
		new Type("obj", "objects/locs (config 6)", 2, 6, 65535, "map locs, object spawn JSON, Java ids; LocAddChange sends 16 bits"),
		new Type("npc", "npcs (config 9)", 2, 9, 65534, "npc spawns, drops, combat JSON, Java ids, transforms (multinpc)"),
		new Type("seq", "animations/seq (config 12)", 2, 12, 65534, "npc/item/obj defs, Java ids, spotanims"),
		new Type("spotanim", "spotanims/gfx (config 13)", 2, 13, 65534, "Java gfx ids, projectiles"),
		new Type("varbit", "varbits (config 14)", 2, 14, 65534, "Java varbits, multinpc/multiloc, cs2, interfaces"),
		new Type("varp", "varps (config 16)", 2, 16, 65534, "varbit base varps, Java varps, cs2"),
		new Type("overlay", "floor overlays (config 4)", 2, 4, 32767, "map terrain (custom regions)"),
		new Type("inv", "inventory types (config 5)", 2, 5, 65534, "Java container ids, interfaces"),
		new Type("config18", "config group 18", 2, 18, 65534, "check usage"),
		new Type("model", "models (index 7)", 7, -1, 65535, "item/npc/obj/spotanim/kit defs, interfaces; JS5 group id is u16"),
		new Type("texture", "textures (index 9 files)", 9, 0, 1023, "model faces, 117HD materials (zelus-custom-materials.json)"),
		new Type("frames", "anim frames (index 0)", 0, -1, 65535, "seq frame ids (group << 16 | file)"),
		new Type("skeleton", "skeletons (index 1)", 1, -1, 65535, "frame groups' base ids"),
		new Type("sound", "sound effects (index 4)", 4, -1, 65535, "seq sound frames, Java sound ids"),
		new Type("clientscript", "clientscripts (index 12)", 12, -1, 65535, "interface hooks, cs2 calls, Java runClientScript ids")
	);

	static TreeSet<Integer> ids(Store s, Type t) {
		TreeSet<Integer> r = new TreeSet<>();
		Index ix = s.findIndex(t.index);
		if (ix == null) return r;
		if (t.group < 0) {
			for (Archive a : ix.getArchives()) r.add(a.getArchiveId());
		} else {
			Archive a = ix.getArchive(t.group);
			if (a != null) for (FileData f : a.getFileData()) r.add(f.getId());
		}
		return r;
	}

	// base varps of our varbits that have no varp definition of their own
	static Set<Integer> phantomVarps(Store local, Set<Integer> definedVarps) throws Exception {
		Archive ar = local.findIndex(2).getArchive(14);
		ArchiveFiles af = ar.getFiles(local.getStorage().loadArchive(ar));
		Set<Integer> r = new TreeSet<>();
		for (FSFile f : af.getFiles()) {
			int base = new VarbitLoader().load(f.getFileId(), f.getContents()).getIndex();
			if (!definedVarps.contains(base)) r.add(base);
		}
		return r;
	}

	static final Map<String, Integer> CONFIG_GROUP = Map.of("item", 10, "obj", 6, "npc", 9, "seq", 12, "spotanim", 13,
		"varbit", 14, "varp", 16, "overlay", 4, "inv", 5, "config18", 18);
	static final Map<String, Map<Integer, byte[]>> cfgCache = new HashMap<>();
	static DefRewriter.ParamKinds paramKinds;

	static Map<Integer, byte[]> cfg(Store s, String tag, int group) throws Exception {
		String k = tag + ":" + group;
		if (!cfgCache.containsKey(k)) cfgCache.put(k, RoundTripDefs.files(s, group));
		return cfgCache.get(k);
	}

	/**
	 * Older copies of Jagex content Zelus imported before 231 caught up: not byte-identical to 241 (Jagex
	 * kept editing them) but superseded by 241's version at the same id. Checked by hand.
	 */
	static final Map<String, Map<Integer, String>> SUPERSEDED_IMPORTS = Map.of(
		"clientscript", Map.of(2628, "imported vanilla (older version): all 24 instructions are in 241's 34, same args; only caller is Jagex's script 2103"));

	/** Same content as vanilla 241 at the same id (configs semantically, scripts after format conversion, rest by bytes). */
	static boolean sameAsVanilla241(Type t, int id, Store local, Store v241) throws Exception {
		// varp definitions are 1-3 bytes: equal bytes prove nothing, and our custom varps hold Zelus state.
		// (inv 935 stays: besides equal bytes, Jagex's own script 7932 -- imported verbatim -- uses it.)
		if (t.key.equals("varp")) return false;
		if (t.index == 2) {
			byte[] o = cfg(local, "l", t.group).get(id), v = cfg(v241, "v", t.group).get(id);
			if (o == null || v == null) return false;
			if (Arrays.equals(o, v)) return true;
			if (!RoundTripDefs.GROUPS.containsKey(t.key)) return false;
			if (paramKinds == null) {
				Map<Integer, byte[]> p = new HashMap<>(cfg(v241, "v", 11));
				cfg(local, "l", 11).forEach(p::putIfAbsent);
				paramKinds = DefRewriter.paramKinds(p);
			}
			return ConfigMerge.sameFields(t.key, o, v, paramKinds);
		}
		if (t.index == 9) {
			byte[] o = textureFiles(local).get(id), v = textureFiles(v241).get(id);
			return o != null && Arrays.equals(o, v);
		}
		Archive la = local.findIndex(t.index).getArchive(id), va = v241.findIndex(t.index).getArchive(id);
		if (la == null || va == null) return false;
		byte[] o = la.decompress(local.getStorage().loadArchive(la)), v = va.decompress(v241.getStorage().loadArchive(va));
		if (t.index == 12) {
			try { o = ScriptRewriter.run(o, DefRewriter.IDENTITY, false, true, (i, x) -> x).bytes(); } catch (Exception e) { return false; }
		}
		return Arrays.equals(o, v);
	}

	static final Map<Store, Map<Integer, byte[]>> texCache = new HashMap<>();
	static Map<Integer, byte[]> textureFiles(Store s) throws Exception {
		if (!texCache.containsKey(s)) {
			Archive ar = s.findIndex(9).getArchive(0);
			FileData[] fd = ar.getFileData();
			List<byte[]> c = SpliceItemOption.splitChunks(ar.decompress(s.getStorage().loadArchive(ar)), fd.length);
			Map<Integer, byte[]> m = new HashMap<>();
			for (int i = 0; i < fd.length; i++) m.put(fd[i].getId(), c.get(i));
			texCache.put(s, m);
		}
		return texCache.get(s);
	}

	static String sha(Store s, int index, int group) throws Exception {
		Archive a = s.findIndex(index).getArchive(group);
		byte[] b = a.decompress(s.getStorage().loadArchive(a));
		return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(b));
	}

	public static void main(String[] args) throws Exception {
		Store local = new Store(new File(args[0])); local.load();
		Store v231 = new Store(new File(args[1])); v231.load();
		Store v241 = new Store(new File(args[2])); v241.load();

		StringBuilder json = new StringBuilder("{\n");
		json.append("  \"generated\": \"").append(java.time.LocalDate.now()).append("\",\n");
		json.append("  \"sources\": {\"local\": ").append(q(args[0])).append(", \"vanilla231\": \"OpenRS2 2247\", \"vanilla241\": \"OpenRS2 2746\"},\n");
		json.append("  \"types\": {\n");

		List<String> summary = new ArrayList<>();
		for (int ti = 0; ti < TYPES.size(); ti++) {
			Type t = TYPES.get(ti);
			TreeSet<Integer> l = ids(local, t), a = ids(v231, t), b = ids(v241, t);
			if (t.key.equals("varp")) l.addAll(phantomVarps(local, l));
			TreeSet<Integer> custom = new TreeSet<>(), colliding = new TreeSet<>();
			for (int id : l) if (!a.contains(id)) { custom.add(id); if (b.contains(id)) colliding.add(id); }
			int max231 = a.isEmpty() ? -1 : a.last(), max241 = b.isEmpty() ? -1 : b.last();
			int growth = Math.max(0, max241 - max231);

			// occupied = everything that keeps its id: all of vanilla 241 + our ids that don't move
			TreeSet<Integer> occupied = new TreeSet<>(b);
			for (int id : l) if (!colliding.contains(id)) occupied.add(id);

			Map<Integer, Integer> map = new TreeMap<>();
			Map<Integer, String> reuse = new TreeMap<>();
			String strategy = "none";
			Integer delta = null;

			TreeSet<Integer> toPlace = new TreeSet<>(colliding);
			Set<Integer> imported = new TreeSet<>();
			for (int id : colliding) if (sameAsVanilla241(t, id, local, v241)) imported.add(id);
			for (int id : imported) { map.put(id, id); reuse.put(id, "imported vanilla: identical to rev 241 at the same id"); toPlace.remove(id); }
			for (var e : SUPERSEDED_IMPORTS.getOrDefault(t.key, Map.of()).entrySet()) {
				if (!colliding.contains(e.getKey())) throw new IllegalStateException(t.key + " " + e.getKey() + " is not a colliding custom id");
				imported.add(e.getKey()); map.put(e.getKey(), e.getKey()); reuse.put(e.getKey(), e.getValue()); toPlace.remove(e.getKey());
			}
			if (t.key.equals("model") && !colliding.isEmpty()) {
				// identical bytes to a vanilla 241 model -> point at that model, no slot needed
				Map<String, Integer> vanillaByHash = new HashMap<>();
				for (int id : b) vanillaByHash.putIfAbsent(sha(v241, 7, id), id);
				Map<String, Integer> firstCustomByHash = new HashMap<>();
				for (int id : colliding) {
					String h = sha(local, 7, id);
					if (vanillaByHash.containsKey(h)) {
						map.put(id, vanillaByHash.get(h));
						reuse.put(id, "same bytes as vanilla 241 model " + vanillaByHash.get(h));
						toPlace.remove(id);
					} else if (firstCustomByHash.containsKey(h)) {
						reuse.put(id, "duplicate of custom " + firstCustomByHash.get(h));
						toPlace.remove(id);
					} else {
						firstCustomByHash.put(h, id);
					}
				}
			}

			if (!toPlace.isEmpty()) {
				int lo = toPlace.first(), hi = toPlace.last();
				int desiredStart = max241 + 1 + 2 * growth;
				for (int start = desiredStart; start + (hi - lo) <= t.limit && delta == null; start++) {
					int d = start - lo;
					boolean ok = true;
					for (int id : toPlace) if (occupied.contains(id + d)) { ok = false; break; }
					if (ok) delta = d;
				}
				if (delta != null) {
					strategy = "constant-shift";
					for (int id : toPlace) map.put(id, id + delta);
				} else {
					// top-down pack below the limit, preserving ascending order
					strategy = "pack-below-limit";
					List<Integer> free = new ArrayList<>();
					for (int id = t.limit; id > max241 && free.size() < toPlace.size(); id--)
						if (!occupied.contains(id)) free.add(id);
					if (free.size() < toPlace.size()) {
						throw new IllegalStateException(t.key + ": only " + free.size() + " free ids for " + toPlace.size());
					}
					Collections.reverse(free);
					Iterator<Integer> it = free.iterator();
					for (int id : toPlace) map.put(id, it.next());
				}
			}
			// duplicates of another custom follow that custom's new id (keeps one copy)
			for (var e : reuse.entrySet())
				if (e.getValue().startsWith("duplicate of custom ")) {
					int first = Integer.parseInt(e.getValue().substring("duplicate of custom ".length()));
					map.put(e.getKey(), map.get(first));
				}

			// ---- validation
			Set<Integer> newIds = new HashSet<>();
			for (var e : map.entrySet()) {
				int n = e.getValue();
				boolean reused = reuse.containsKey(e.getKey()); // reuse of a vanilla model, duplicate, or imported identity
				if (n > t.limit) throw new IllegalStateException(t.key + " " + e.getKey() + " -> " + n + " over limit");
				if (!reused && occupied.contains(n)) throw new IllegalStateException(t.key + " " + n + " occupied");
				if (!reused && !newIds.add(n)) throw new IllegalStateException(t.key + " " + n + " assigned twice");
			}
			int lowestNew = map.values().stream().filter(v -> v > max241).min(Integer::compare).orElse(-1);
			int gap = lowestNew < 0 ? -1 : lowestNew - max241 - 1;

			long importedCount = reuse.values().stream().filter(v -> v.startsWith("imported")).count();
			summary.add(String.format("%-28s custom=%-5d collide=%-5d importedVanilla=%-4d strategy=%-16s %s gapAbove241=%s (~%s revs) limit=%d",
				t.label, custom.size(), colliding.size(), importedCount, strategy,
				delta != null ? "delta=" + delta : (map.isEmpty() ? "" : "newRange=" + Collections.min(map.values()) + ".." + Collections.max(map.values())),
				gap < 0 ? "-" : gap, growth == 0 || gap < 0 ? "-" : String.format("%.1f", gap * 10.0 / growth), t.limit));

			json.append("    ").append(q(t.key)).append(": {\n");
			json.append("      \"label\": ").append(q(t.label)).append(", \"index\": ").append(t.index).append(", \"group\": ").append(t.group)
				.append(", \"limit\": ").append(t.limit).append(",\n");
			json.append("      \"vanilla231Max\": ").append(max231).append(", \"vanilla241Max\": ").append(max241)
				.append(", \"customCount\": ").append(custom.size()).append(", \"collidingCount\": ").append(colliding.size()).append(",\n");
			json.append("      \"strategy\": ").append(q(strategy)).append(", \"delta\": ").append(delta).append(", \"gapAbove241\": ").append(gap).append(",\n");
			json.append("      \"references\": ").append(q(t.refNote)).append(",\n");
			json.append("      \"reuse\": {");
			int k = 0;
			for (var e : reuse.entrySet()) json.append(k++ == 0 ? "" : ", ").append(q(String.valueOf(e.getKey()))).append(": ").append(q(e.getValue()));
			json.append("},\n      \"map\": {");
			k = 0;
			for (var e : map.entrySet()) json.append(k++ % 12 == 0 ? "\n        " : " ").append(q(String.valueOf(e.getKey()))).append(": ").append(e.getValue()).append(",");
			if (!map.isEmpty()) json.setLength(json.length() - 1);
			json.append("\n      }\n    }").append(ti < TYPES.size() - 1 ? "," : "").append("\n");
		}
		json.append("  }\n}\n");
		Files.writeString(Path.of(args[3]), json.toString(), StandardCharsets.UTF_8);
		summary.forEach(s -> System.out.println("RESULT " + s));
	}

	static String q(String s) {
		return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
	}
}

import net.runelite.cache.fs.*;
import net.runelite.cache.index.FileData;

import java.io.File;
import java.nio.file.*;
import java.util.*;

/**
 * Whole-cache reference integrity check. Re-uses the rewriters in "record" mode (identity map
 * that remembers every id it is asked about) and checks each referenced id exists:
 *   config definitions -> models, seqs, items, npcs, objects, textures, sounds, varps, varbits,
 *                         frame groups, invs (and param values of those types)
 *   interface components -> models, seqs, textures, hook scripts, varp/inv triggers, parents
 *   clientscripts -> varps, varbits, invoked scripts
 *   map regions -> placed objects;  frame groups -> skeletons;  models -> face textures
 * Run on the built cache AND on pristine vanilla 241: only references dangling in the build but
 * not in vanilla (i.e. introduced by us) count as failures.
 *
 * args: see main()
 */
public class IntegrityCheck241 {
	static final class Refs {
		final Map<String, Set<Integer>> exist = new HashMap<>();
		final Map<String, Map<String, Integer>> dangling = new TreeMap<>(); // "fromKind->toKind" -> "toId" -> count
		final Map<String, String> example = new HashMap<>();
		void check(String from, String kind, int id, String where) {
			if (id < 0) return;
			Set<Integer> s = exist.get(kind);
			if (s == null || s.contains(id)) return;
			String key = from + "->" + kind;
			dangling.computeIfAbsent(key, k -> new TreeMap<>()).merge(String.valueOf(id), 1, Integer::sum);
			example.putIfAbsent(key + ":" + id, where);
		}
	}

	static Set<Integer> groups(Store s, int idx) {
		Set<Integer> r = new HashSet<>();
		for (Archive a : s.findIndex(idx).getArchives()) r.add(a.getArchiveId());
		return r;
	}

	static Refs scan(Store s, boolean r237) throws Exception {
		Refs r = new Refs();
		Map<String, Integer> cfg = Map.of("item", 10, "npc", 9, "obj", 6, "seq", 12, "spotanim", 13, "varbit", 14, "varp", 16, "inv", 5, "enum", 8, "struct", 34);
		Map<String, Map<Integer, byte[]>> defs = new HashMap<>();
		for (var e : cfg.entrySet()) { var f = RoundTripDefs.files(s, e.getValue()); defs.put(e.getKey(), f); r.exist.put(e.getKey(), f.keySet()); }
		r.exist.put("model", groups(s, 7));
		r.exist.put("frames", groups(s, 0));
		r.exist.put("skeleton", groups(s, 1));
		r.exist.put("sound", groups(s, 4));
		r.exist.put("clientscript", groups(s, 12));
		Archive tex = s.findIndex(9).getArchive(0);
		Set<Integer> texIds = new HashSet<>();
		for (FileData f : tex.getFileData()) texIds.add(f.getId());
		r.exist.put("texture", texIds);
		DefRewriter.ParamKinds pk = DefRewriter.paramKinds(RoundTripDefs.files(s, 11));

		// config definitions
		for (String type : List.of("item", "npc", "obj", "seq", "spotanim", "varbit", "enum", "struct", "inv")) {
			for (var e : defs.get(type).entrySet()) {
				String where = type + " " + e.getKey();
				DefRewriter.rewrite(type, e.getValue(), (kind, id) -> { r.check(type, kind, id, where); return id; }, pk);
			}
		}
		// interfaces
		for (var g : RoundTripInterfaces.components(s).entrySet()) {
			Set<Integer> comps = g.getValue().keySet();
			for (var c : g.getValue().entrySet()) {
				String where = "interface " + g.getKey() + ":" + c.getKey();
				InterfaceRewriter ir = InterfaceRewriter.run(c.getValue(), (kind, id) -> { r.check("interface", kind, id, where); return id; }, r237, r237, v -> false);
				if ((c.getValue()[0] & 0xff) == 0xff && ir.parentPos >= 0) {
					byte[] b = ir.bytes();
					int parent = ((b[ir.parentPos] & 0xff) << 8) | (b[ir.parentPos + 1] & 0xff);
					if (parent != 0xFFFF && !comps.contains(parent)) {
						String key = "interface->parent";
						r.dangling.computeIfAbsent(key, k -> new TreeMap<>()).merge(g.getKey() + ":" + parent, 1, Integer::sum);
						r.example.putIfAbsent(key + ":" + g.getKey() + ":" + parent, where);
					}
				}
			}
		}
		// clientscripts
		for (var e : RoundTripScripts.scripts(s).entrySet()) {
			if (e.getValue().length <= 2) continue;
			String where = "script " + e.getKey();
			ScriptRewriter.run(e.getValue(), (kind, id) -> { r.check("script", kind, id, where); return id; }, r237, r237, (i, v) -> v);
		}
		// maps
		for (Archive g : s.findIndex(5).getArchives()) {
			if (g.getFileData().length != 5) continue;
			ArchiveFiles f = g.getFiles(s.getStorage().loadArchive(g));
			for (var l : MapCodec.decodeLocs(f.findFile(1).getContents())) r.check("map", "obj", l.id, "region " + g.getArchiveId());
		}
		// frames -> skeletons
		for (Archive g : s.findIndex(0).getArchives()) {
			FileData[] fd = g.getFileData();
			byte[] raw = g.decompress(s.getStorage().loadArchive(g));
			List<byte[]> files = fd.length == 1 ? List.of(raw) : SpliceItemOption.splitChunks(raw, fd.length);
			for (byte[] f : files) r.check("frames", "skeleton", ((f[0] & 0xff) << 8) | (f[1] & 0xff), "frames " + g.getArchiveId());
		}
		// models -> textures
		for (Archive g : s.findIndex(7).getArchives()) {
			byte[] d = g.decompress(s.getStorage().loadArchive(g));
			try {
				ModelTexturePatcher.patch(d, t -> { r.check("model", "texture", t, "model " + g.getArchiveId()); return t; });
			} catch (Exception ignored) { }
		}
		return r;
	}

	/** "fromKind->toKind:id" for every reference dangling in a but not in its vanilla baseline b */
	static Map<String, String> introduced(Refs a, Refs b) {
		Map<String, String> r = new TreeMap<>();
		for (var e : a.dangling.entrySet()) {
			Map<String, Integer> base = b.dangling.getOrDefault(e.getKey(), Map.of());
			for (String id : e.getValue().keySet()) if (!base.containsKey(id)) r.put(e.getKey() + ":" + id, a.example.get(e.getKey() + ":" + id));
		}
		return r;
	}

	static String json(Map<String, String> m) {
		Map<String, List<String>> byKind = new TreeMap<>();
		m.forEach((k, where) -> byKind.computeIfAbsent(k.substring(0, k.lastIndexOf(':')), x -> new ArrayList<>()).add(k.substring(k.lastIndexOf(':') + 1) + " (" + where + ")"));
		List<String> lines = new ArrayList<>();
		byKind.forEach((k, v) -> lines.add("    \"" + k + "\": [\"" + String.join("\", \"", v) + "\"]"));
		return "{\n" + String.join(",\n", lines) + "\n  }";
	}

	/**
	 * args: built241 pristineV241 report.json [our231 vanilla231]
	 * With the 231 pair, dangling references the build shares with our live 231 cache are reported as
	 * carried over (pre-existing), and only the rest fail the check.
	 * args: our231 vanilla231 report.json 231  -> standalone check of a rev 231 cache
	 */
	public static void main(String[] a) throws Exception {
		if (a.length > 3 && a[3].equals("231")) {
			Store x = new Store(new File(a[0])); x.load(); Store y = new Store(new File(a[1])); y.load();
			Map<String, String> m = introduced(scan(x, false), scan(y, false));
			Files.writeString(Path.of(a[2]), "{\n  \"introducedByBuild\": " + json(m) + "\n}\n");
			System.out.println("RESULT dangling references (rev 231 check): " + m.size());
			return;
		}
		Store built = new Store(new File(a[0])); built.load();
		Store vanilla = new Store(new File(a[1])); vanilla.load();
		Map<String, String> build = introduced(scan(built, true), scan(vanilla, true));
		Map<String, String> ours231 = new TreeMap<>();
		if (a.length >= 5) {
			Store o = new Store(new File(a[3])); o.load(); Store v = new Store(new File(a[4])); v.load();
			ours231 = introduced(scan(o, false), scan(v, false));
		}
		Map<String, String> fresh = new TreeMap<>(), carried = new TreeMap<>(), resolving = new TreeMap<>();
		for (var e : build.entrySet()) (ours231.containsKey(e.getKey()) ? carried : fresh).put(e.getKey(), e.getValue());
		for (var e : ours231.entrySet()) if (!build.containsKey(e.getKey())) resolving.put(e.getKey(), e.getValue());
		Files.writeString(Path.of(a[2]), "{\n  \"introducedByMigration\": " + json(fresh)
			+ ",\n  \"carriedOverFromLive231\": " + json(carried)
			+ ",\n  \"danglingIn231NowResolvingIn241\": " + json(resolving) + "\n}\n");
		System.out.println("RESULT integrity: introduced by the migration " + fresh.size() + ", carried over from live 231 " + carried.size()
			+ ", dangling in 231 but resolving in 241 " + resolving.size() + " (review list in the report)");
		for (String k : fresh.keySet()) System.out.println("RESULT   NEW " + k + " (" + fresh.get(k) + ")");
		if (!fresh.isEmpty()) System.exit(1);
	}
}

import net.runelite.cache.fs.*;
import net.runelite.cache.index.FileData;
import net.runelite.cache.index.IndexData;

import java.io.File;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/**
 * Stage 3 of the rev 241 cache build: map regions (index 5).
 *
 * Rev 241 stores one group per region (group id = region id; file 0 terrain, 1 locs, 2 environment
 * preset, 3/4 constants), unencrypted; rev 231 used named mX_Y / lX_Y groups with XTEA locs.
 * For each region of our cache that differs from vanilla 231 (or doesn't exist there):
 *   terrain: per-tile three-way merge (231 / ours / 241); tiles we changed win; 241's extra layers kept
 *   locs   : multiset merge: 241 - (removed by us) + (added by us), our object ids remapped
 *   regions new to 241 get a group with the most common environment preset.
 * Regions we never touched keep vanilla 241. Group 25287 (a 37-colour palette) is left alone.
 *
 * args: localCache v231Cache outDir manifest localRegionKeys.json localXteas.json v231Keys(;-separated) report.json
 *       overrideRegions(comma-separated, may be "") pristineV241Cache
 * "Theirs" is always read from the pristine vanilla 241 cache, so the stage can be re-run.
 */
public class BuildMaps241 {
	static Map<Integer, int[]> loadKeys(String path, boolean openrs2, Map<Integer, int[]> into) throws Exception {
		String s = Files.readString(Path.of(path)).replaceAll("\\s", "");
		Pattern p = openrs2
			? Pattern.compile("\"mapsquare\":(\\d+),\"key\":\\[(-?\\d+),(-?\\d+),(-?\\d+),(-?\\d+)\\]")
			: Pattern.compile("\"(\\d+)\":\\[(-?\\d+),(-?\\d+),(-?\\d+),(-?\\d+)\\]");
		Matcher m = p.matcher(s);
		while (m.find()) into.putIfAbsent(Integer.parseInt(m.group(1)), new int[]{Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)), Integer.parseInt(m.group(4)), Integer.parseInt(m.group(5))});
		return into;
	}

	/**
	 * Unencrypted bytes of an old-style named group: no key first, then every candidate key for
	 * the region (OpenRS2 key files of several 231 builds, our own key files); a key only counts if
	 * the decrypted locs parse.
	 */
	static byte[] readNamed(Store s, String name, int region, List<Map<Integer, int[]>> keySets, boolean locs) {
		Archive a = s.findIndex(5).findArchiveByName(name);
		if (a == null) return null;
		try {
			byte[] raw = s.getStorage().loadArchive(a);
			if (!locs) return a.decompress(raw, null);
			try { byte[] b = a.decompress(raw, null); MapCodec.decodeLocs(b); return b; } catch (Exception ignored) { }
			for (Map<Integer, int[]> keys : keySets) {
				int[] k = keys.get(region);
				if (k == null) continue;
				try { byte[] b = a.decompress(raw, k); MapCodec.decodeLocs(b); return b; } catch (Exception ignored) { }
			}
			throw new NoKeyException(name);
		} catch (NoKeyException e) {
			throw e;
		} catch (Exception e) {
			throw new IllegalStateException(name + ": " + e.getMessage(), e);
		}
	}

	static final class NoKeyException extends RuntimeException {
		NoKeyException(String name) { super("no working key for " + name); }
	}

	static MapCodec.Terrain terrainAny(byte[] b) {
		try { return MapCodec.decodeTerrain(b, true); } catch (Exception e) { return MapCodec.decodeTerrain(b, false); }
	}

	static String tileKey(MapCodec.Tile t) {
		return t.height + "," + t.overlay + "," + t.shape + "," + t.rotation + "," + t.settings + "," + t.underlay;
	}

	static long locKey(MapCodec.Loc l) {
		return ((long) l.id << 24) | ((long) l.plane << 22) | ((long) l.x << 16) | ((long) l.y << 10) | (l.type << 2) | l.rotation;
	}

	static MapCodec.Loc locOf(long k) {
		return new MapCodec.Loc((int) (k >>> 24), (int) (k >> 22) & 3, (int) (k >> 16) & 63, (int) (k >> 10) & 63, (int) (k >> 2) & 255, (int) k & 3);
	}

	static TreeMap<Long, Integer> multiset(List<MapCodec.Loc> locs) {
		TreeMap<Long, Integer> m = new TreeMap<>();
		for (var l : locs) m.merge(locKey(l), 1, Integer::sum);
		return m;
	}

	static List<MapCodec.Loc> toSortedList(TreeMap<Long, Integer> m) {
		List<MapCodec.Loc> out = new ArrayList<>();
		// sorted by id, then plane/x/y (the encoder needs ids ascending and positions non-decreasing)
		for (var e : m.entrySet()) for (int i = 0; i < e.getValue(); i++) out.add(locOf(e.getKey()));
		out.sort(Comparator.comparingInt((MapCodec.Loc l) -> l.id).thenComparingInt(l -> l.plane << 12 | l.x << 6 | l.y).thenComparingInt(l -> l.type << 2 | l.rotation));
		return out;
	}

	public static void main(String[] a) throws Exception {
		Store local = new Store(new File(a[0])); local.load();
		Store v231 = new Store(new File(a[1])); v231.load();
		Store out = new Store(new File(a[2])); out.load();
		Manifest manifest = new Manifest(Path.of(a[3]));
		List<Map<Integer, int[]>> localKeys = List.of(loadKeys(a[4], true, new HashMap<>()), loadKeys(a[5], false, new HashMap<>()));
		List<Map<Integer, int[]>> baseKeys = new ArrayList<>();
		for (String f : a[6].split(";")) baseKeys.add(loadKeys(f, true, new HashMap<>()));
		baseKeys.addAll(localKeys); // vanilla regions we never re-encrypted share Jagex's key

		Store v241 = new Store(new File(a[9])); v241.load();
		Index vi = v241.findIndex(5);
		Set<Integer> overrides = new HashSet<>();
		if (a.length > 8 && !a[8].isBlank()) for (String x : a[8].split(",")) overrides.add(Integer.parseInt(x.trim()));
		Index oi = out.findIndex(5);
		// most common environment preset (file 2) for regions new to 241
		Map<String, Integer> presetCount = new HashMap<>();
		Map<String, byte[]> presetBytes = new HashMap<>();
		for (Archive g : vi.getArchives()) {
			if (g.getFileData().length != 5) continue;
			byte[] f2 = g.getFiles(v241.getStorage().loadArchive(g)).findFile(2).getContents();
			String h = HexFormat.of().formatHex(f2);
			presetCount.merge(h, 1, Integer::sum);
			presetBytes.put(h, f2);
		}
		byte[] defaultPreset = presetBytes.get(Collections.max(presetCount.entrySet(), Map.Entry.comparingByValue()).getKey());

		Set<Integer> objIds = new HashSet<>(RoundTripDefs.files(out, 6).keySet()); // objects in the built cache

		List<String> lines = new ArrayList<>();
		Map<Integer, byte[][]> written = new TreeMap<>();
		int untouched = 0, importedSame = 0;
		for (int r = 0; r < 32768; r++) {
			int rx = r >> 8, ry = r & 0xff;
			byte[] lt = readNamed(local, "m" + rx + "_" + ry, r, localKeys, false);
			if (lt == null) continue;
			byte[] ll = readNamed(local, "l" + rx + "_" + ry, r, localKeys, true);
			MapCodec.Terrain ours = terrainAny(lt);
			// our custom floor overlays moved by the manifest (e.g. 436 -> 1105, the Soul altar's floor, 2026-10-28):
			// a map stores overlay id + 1. Without this the tiles kept pointing at 241's vanilla overlay of the old id
			// (436 = Jagex's invisible magenta: a black square around the Reliquary of Souls).
			for (MapCodec.Tile[][] plane : ours.planes)
				for (MapCodec.Tile[] col : plane)
					for (MapCodec.Tile t : col)
						if (t != null && t.overlay > 0) {
							int mapped = manifest.map("overlay", t.overlay - 1);
							if (mapped != t.overlay - 1)
								t.overlay = mapped + 1;
						}
			List<MapCodec.Loc> oursLocs = ll == null ? new ArrayList<>() : MapCodec.decodeLocs(ll);

			byte[] bt = readNamed(v231, "m" + rx + "_" + ry, r, baseKeys, false);
			byte[] bl = null;
			boolean baseLocsUnknown = false;
			try { bl = readNamed(v231, "l" + rx + "_" + ry, r, baseKeys, true); } catch (NoKeyException e) { baseLocsUnknown = true; }
			MapCodec.Terrain base = bt == null ? null : MapCodec.decodeTerrain(bt, true);
			List<MapCodec.Loc> baseLocs = bl == null ? (bt == null || baseLocsUnknown ? null : new ArrayList<>()) : MapCodec.decodeLocs(bl);

			Archive tg = vi.getArchive(r);
			ArchiveFiles tf = tg == null ? null : tg.getFiles(v241.getStorage().loadArchive(tg));
			MapCodec.Terrain theirs = tf == null ? null : MapCodec.decodeTerrain(tf.findFile(0).getContents(), false);
			List<MapCodec.Loc> theirsLocs = tf == null ? null : MapCodec.decodeLocs(tf.findFile(1).getContents());

			// remap ids of our locs (only custom ids move; base/241 ids are never in the table)
			List<MapCodec.Loc> oursMapped = new ArrayList<>();
			int remapped = 0;
			for (var l : oursLocs) {
				int id = manifest.map("obj", l.id);
				if (id != l.id) remapped++;
				oursMapped.add(new MapCodec.Loc(id, l.plane, l.x, l.y, l.type, l.rotation));
			}

			boolean terrainSame, locsSame;
			if (base != null) {
				terrainSame = sameTiles(base, ours);
				locsSame = baseLocs != null ? multiset(baseLocs).equals(multiset(oursLocs))
					: theirsLocs != null && multiset(theirsLocs).equals(multiset(oursMapped));
			} else if (theirs != null) {
				// not in 231, present in 241: content we imported from a newer cache earlier
				terrainSame = sameTiles(theirs, ours) && ours.extraLayers.size() <= theirs.extraLayers.size();
				locsSame = multiset(theirsLocs).equals(multiset(oursMapped));
				if (terrainSame && locsSame) { importedSame++; continue; }
			} else {
				terrainSame = false;
				locsSame = false;
			}
			if (base != null && terrainSame && locsSame) { untouched++; continue; }

			// what Jagex changed in this region between 231 and 241 (what an override would drop)
			int tilesTheirs = 0, locsTheirsAdded = 0, locsTheirsRemoved = 0;
			if (theirs != null && base != null) {
				for (int z = 0; z < 4; z++) for (int x = 0; x < 64; x++) for (int y = 0; y < 64; y++)
					if (!tileKey(base.planes[z][x][y]).equals(tileKey(theirs.planes[z][x][y]))) tilesTheirs++;
				if (baseLocs != null) {
					TreeMap<Long, Integer> bm = multiset(baseLocs), tm = multiset(theirsLocs);
					Set<Long> ks = new TreeSet<>(bm.keySet());
					ks.addAll(tm.keySet());
					for (long k : ks) {
						int d = tm.getOrDefault(k, 0) - bm.getOrDefault(k, 0);
						if (d > 0) locsTheirsAdded += d; else locsTheirsRemoved -= d;
					}
				}
			}
			boolean override = overrides.contains(r);

			// ---- terrain
			MapCodec.Terrain merged;
			int tilesOurs = 0, tileConflicts = 0;
			if (theirs == null || override) {
				merged = ours;
				tilesOurs = theirs == null ? 16384 : countChanged(base != null ? base : theirs, ours);
			} else {
				merged = new MapCodec.Terrain();
				merged.planes = new MapCodec.Tile[4][64][64];
				MapCodec.Terrain ref = base != null ? base : theirs;
				for (int z = 0; z < 4; z++) for (int x = 0; x < 64; x++) for (int y = 0; y < 64; y++) {
					MapCodec.Tile b = ref.planes[z][x][y], o = ours.planes[z][x][y], t = theirs.planes[z][x][y];
					boolean oursChanged = !tileKey(b).equals(tileKey(o));
					if (oursChanged) {
						tilesOurs++;
						if (base != null && !tileKey(b).equals(tileKey(t)) && !tileKey(o).equals(tileKey(t))) tileConflicts++;
					}
					merged.planes[z][x][y] = oursChanged ? o : t;
				}
				merged.extraLayers.addAll(theirs.extraLayers.isEmpty() ? ours.extraLayers : theirs.extraLayers);
			}

			// ---- locs
			List<MapCodec.Loc> mergedLocs;
			int added = 0, removed = 0;
			if (theirsLocs == null || override) {
				mergedLocs = toSortedList(multiset(oursMapped));
				added = oursMapped.size();
			} else {
				TreeMap<Long, Integer> b = multiset(baseLocs != null ? baseLocs : theirsLocs), o = multiset(oursMapped), t = multiset(theirsLocs);
				TreeMap<Long, Integer> res = new TreeMap<>(t);
				Set<Long> keys = new TreeSet<>(b.keySet());
				keys.addAll(o.keySet());
				for (long k : keys) {
					int delta = o.getOrDefault(k, 0) - b.getOrDefault(k, 0);
					if (delta == 0) continue;
					if (delta > 0) added += delta; else removed -= delta;
					int n = Math.max(0, res.getOrDefault(k, 0) + delta);
					if (n == 0) res.remove(k); else res.put(k, n);
				}
				mergedLocs = toSortedList(res);
			}

			byte[] terrainBytes = MapCodec.encodeTerrain(merged, true);
			byte[] locBytes = MapCodec.encodeLocs(mergedLocs);
			byte[] f2 = tf != null ? tf.findFile(2).getContents() : defaultPreset;
			byte[] f3 = tf != null ? tf.findFile(3).getContents() : new byte[]{0, 0};
			byte[] f4 = tf != null ? tf.findFile(4).getContents() : new byte[]{0};
			written.put(r, new byte[][]{terrainBytes, locBytes, f2, f3, f4});

			Set<Integer> missingObjs = new TreeSet<>();
			for (var l : mergedLocs) if (!objIds.contains(l.id)) missingObjs.add(l.id);
			lines.add(String.format("    {\"region\": %d, \"name\": \"%d_%d\", \"baseLocsUnknown\": " + baseLocsUnknown + ", \"kind\": \"%s\", \"terrainTilesOurs\": %d, \"terrainTileConflicts\": %d, \"locsAdded\": %d, \"locsRemoved\": %d, \"locIdsRemapped\": %d, \"locs\": %d, \"extraLayers\": %d, \"objectsMissingInCache\": %s, \"jagex241TilesChanged\": " + tilesTheirs + ", \"jagex241LocsAdded\": " + locsTheirsAdded + ", \"jagex241LocsRemoved\": " + locsTheirsRemoved + ", \"override\": " + override + "}",
				r, rx, ry, base != null ? (theirs != null ? "edited vanilla" : "edited vanilla (gone in 241)") : theirs != null ? "differs from 241 (imported region)" : "new region",
				tilesOurs, tileConflicts, added, removed, remapped, mergedLocs.size(), merged.extraLayers.size(), missingObjs));
		}

		// ---- write
		for (var e : written.entrySet()) {
			int r = e.getKey();
			byte[][] files = e.getValue();
			Archive ar = oi.getArchive(r);
			if (ar == null) {
				ar = oi.addArchive(r);
				ar.setCompression(net.runelite.cache.fs.jagex.CompressionType.GZ);
			}
			FileData[] fd = new FileData[5];
			for (int i = 0; i < 5; i++) { fd[i] = new FileData(); fd[i].setId(i); }
			byte[] raw = SpliceItemOption.joinChunks(Arrays.asList(files));
			Container c = new Container(ar.getCompression(), -1);
			c.compress(raw, null);
			out.getStorage().store(5, r, c.data);
			ar.setFileData(fd);
			ar.setCrc(c.crc);
			ar.setRevision(ar.getRevision() + 1);
			ar.setCompressedSize(c.data.length);
			ar.setDecompressedSize(raw.length);
		}
		IndexData id = oi.toIndexData();
		byte[] rawIndex = id.writeIndexData();
		Container ic = new Container(oi.getCompression(), -1);
		ic.compress(rawIndex, null);
		out.getStorage().store(255, 5, ic.data);
		oi.setCrc(ic.crc);
		out.close();

		// ---- verify from disk
		Store check = new Store(new File(a[2]));
		check.load();
		for (var e : written.entrySet()) {
			Archive ar = check.findIndex(5).getArchive(e.getKey());
			ArchiveFiles f = ar.getFiles(check.getStorage().loadArchive(ar));
			for (int i = 0; i < 5; i++)
				if (!Arrays.equals(f.findFile(i).getContents(), e.getValue()[i])) throw new IllegalStateException("region " + e.getKey() + " file " + i + " differs after write");
			MapCodec.decodeTerrain(f.findFile(0).getContents(), false);
			MapCodec.decodeLocs(f.findFile(1).getContents());
		}
		Files.writeString(Path.of(a[7]), "{\n  \"stage\": \"maps\",\n  \"regionsWritten\": " + written.size() + ",\n  \"untouchedVanilla\": " + untouched
			+ ",\n  \"importedRegionsIdenticalTo241\": " + importedSame + ",\n  \"regions\": [\n" + String.join(",\n", lines) + "\n  ]\n}\n");
		System.out.println("RESULT maps: " + written.size() + " regions written and verified; untouched vanilla " + untouched + "; imported regions identical to 241: " + importedSame);
	}

	static int countChanged(MapCodec.Terrain x, MapCodec.Terrain y) {
		int n = 0;
		for (int z = 0; z < 4; z++) for (int i = 0; i < 64; i++) for (int j = 0; j < 64; j++)
			if (!tileKey(x.planes[z][i][j]).equals(tileKey(y.planes[z][i][j]))) n++;
		return n;
	}

	static boolean sameTiles(MapCodec.Terrain x, MapCodec.Terrain y) {
		for (int z = 0; z < 4; z++) for (int i = 0; i < 64; i++) for (int j = 0; j < 64; j++)
			if (!tileKey(x.planes[z][i][j]).equals(tileKey(y.planes[z][i][j]))) return false;
		return true;
	}
}

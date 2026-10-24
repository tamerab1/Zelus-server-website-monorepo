import net.runelite.cache.fs.*;
import net.runelite.cache.index.FileData;
import java.io.File;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

// Spike A, part 2: decode every region in OUR live (231) cache with MapCodec, find the custom
// regions (semantically different from vanilla 231), and prove each converts to a 241 group
// (terrain + 0x00 trailer, locs unencrypted) that decodes back to the same content.
// args: localCache v231 v241 localRegionKeys.json localXteas.json v231keys.json
public class ConvertLocal {
	static Map<Integer, int[]> keys = new HashMap<>();

	static void loadKeys(String path, boolean openrs2) throws Exception {
		String s = Files.readString(Path.of(path)).replaceAll("\\s", "");
		Pattern p = openrs2
				? Pattern.compile("\"mapsquare\":(\\d+),\"key\":\\[(-?\\d+),(-?\\d+),(-?\\d+),(-?\\d+)\\]")
				: Pattern.compile("\"(\\d+)\":\\[(-?\\d+),(-?\\d+),(-?\\d+),(-?\\d+)\\]");
		Matcher m = p.matcher(s);
		while (m.find()) keys.putIfAbsent(Integer.parseInt(m.group(1)), new int[]{Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)), Integer.parseInt(m.group(4)), Integer.parseInt(m.group(5))});
	}

	static byte[] read(Store s, String name, int region, boolean locs) {
		Archive a = s.findIndex(5).findArchiveByName(name);
		if (a == null) return null;
		try {
			byte[] raw = s.getStorage().loadArchive(a);
			if (!locs) return a.decompress(raw, null);
			try { byte[] b = a.decompress(raw, null); MapCodec.decodeLocs(b); return b; } catch (Exception ignored) { }
			int[] k = keys.get(region);
			if (k == null) return new byte[]{-1};
			return a.decompress(raw, k);
		} catch (Exception e) { return new byte[]{-1}; }
	}

	static String terrainSig(MapCodec.Tile[][][] t) {
		StringBuilder sb = new StringBuilder();
		for (var p : t) for (var c : p) for (var x : c) sb.append(x.height).append(',').append(x.overlay).append(',').append(x.shape).append(',').append(x.rotation).append(',').append(x.settings).append(',').append(x.underlay).append(';');
		return sb.toString();
	}

	static String locSig(List<MapCodec.Loc> l) {
		StringBuilder sb = new StringBuilder();
		for (var x : l) sb.append(x.id).append(',').append(x.plane).append(',').append(x.x).append(',').append(x.y).append(',').append(x.type).append(',').append(x.rotation).append(';');
		return sb.toString();
	}

	public static void main(String[] a) throws Exception {
		Store L = new Store(new File(a[0])); L.load();
		Store V1 = new Store(new File(a[1])); V1.load();
		Store V2 = new Store(new File(a[2])); V2.load();
		loadKeys(a[3], true); loadKeys(a[4], false);
		Map<Integer, int[]> localKeys = new HashMap<>(keys);
		keys.clear(); loadKeys(a[5], true);
		Map<Integer, int[]> v231Keys = new HashMap<>(keys);

		Set<Integer> v231Objs = new HashSet<>(), v241Objs = new HashSet<>();
		for (FileData f : V1.findIndex(2).getArchive(6).getFileData()) v231Objs.add(f.getId());
		for (FileData f : V2.findIndex(2).getArchive(6).getFileData()) v241Objs.add(f.getId());
		Set<Integer> v241Groups = new HashSet<>();
		for (Archive x : V2.findIndex(5).getArchives()) v241Groups.add(x.getArchiveId());

		int regions = 0, terrainFail = 0, locFail = 0, locNoKey = 0, convertFail = 0, same = 0, custom = 0, customNot241 = 0;
		Set<Integer> customLocIds = new TreeSet<>(), collidingLocIds = new TreeSet<>();
		List<String> customList = new ArrayList<>();
		for (int r = 0; r < 32768; r++) {
			int rx = r >> 8, ry = r & 0xff;
			keys = localKeys;
			byte[] t = read(L, "m" + rx + "_" + ry, r, false);
			if (t == null) continue;
			byte[] l = read(L, "l" + rx + "_" + ry, r, true);
			regions++;
			MapCodec.Tile[][][] tiles;
			List<MapCodec.Loc> locs;
			try { tiles = MapCodec.decodeTerrain(t); } catch (Exception e) { terrainFail++; byte[] vt0 = read(V1, "m" + rx + "_" + ry, r, false); System.out.println("RESULT terrainFail region " + r + " (" + rx + "_" + ry + ") len=" + t.length + " err=" + e + " vanilla231=" + (vt0 == null ? "absent" : (java.util.Arrays.equals(vt0, t) ? "IDENTICAL BYTES" : "differs")) + " in241=" + v241Groups.contains(r)); continue; }
			if (l == null) locs = new ArrayList<>();
			else if (l.length == 1 && l[0] == -1) { locNoKey++; continue; }
			else try { locs = MapCodec.decodeLocs(l); } catch (Exception e) { locFail++; continue; }

			// convert to a 241 group and decode back
			byte[] t241 = Arrays.copyOf(MapCodec.encodeTerrain(tiles), MapCodec.encodeTerrain(tiles).length + 1); // + 0x00 trailer
			byte[] l241 = MapCodec.encodeLocs(locs);
			try {
				MapCodec.Tile[][][] back = MapCodec.decodeTerrain(Arrays.copyOf(t241, t241.length - 1));
				if (t241[t241.length - 1] != 0 || !terrainSig(back).equals(terrainSig(tiles)) || !locSig(MapCodec.decodeLocs(l241)).equals(locSig(locs))) convertFail++;
			} catch (Exception e) { convertFail++; }

			// custom vs vanilla 231?
			keys = v231Keys;
			byte[] vt = read(V1, "m" + rx + "_" + ry, r, false), vl = read(V1, "l" + rx + "_" + ry, r, true);
			boolean isSame = false;
			try {
				isSame = vt != null && terrainSig(MapCodec.decodeTerrain(vt)).equals(terrainSig(tiles))
						&& locSig(vl == null ? new ArrayList<>() : MapCodec.decodeLocs(vl)).equals(locSig(locs));
			} catch (Exception ignored) { }
			if (isSame) { same++; continue; }
			custom++;
			boolean in241 = v241Groups.contains(r);
			if (!in241) customNot241++;
			int cl = 0, cc = 0;
			for (var x : locs) if (!v231Objs.contains(x.id)) { customLocIds.add(x.id); cl++; if (v241Objs.contains(x.id)) { collidingLocIds.add(x.id); cc++; } }
			customList.add(r + " (" + rx + "_" + ry + ")" + (vt == null ? " NEW-REGION" : " edited") + (in241 ? "" : " NOT-IN-241") + " locs=" + locs.size() + " customLocs=" + cl + " colliding=" + cc);
		}
		System.out.println("RESULT local regions=" + regions + " terrainDecodeFail=" + terrainFail + " locDecodeFail=" + locFail + " locNoKey=" + locNoKey + " convertRoundTripFail=" + convertFail);
		System.out.println("RESULT identical to vanilla 231=" + same + " custom=" + custom + " (custom region ids with no 241 group: " + customNotIn241(customNot241) + ")");
		System.out.println("RESULT custom object ids placed in maps=" + customLocIds.size() + " of which collide with 241=" + collidingLocIds.size());
		Files.write(Path.of("custom_regions.txt"), customList);
	}

	static String customNotIn241(int n) { return String.valueOf(n); }
}

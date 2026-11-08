import net.runelite.cache.fs.*;
import net.runelite.cache.index.FileData;
import net.runelite.cache.index.IndexData;

import java.io.File;
import java.util.*;

/**
 * Removes exact locs from a rev 241 map region (index 5, group = region id, file 1 = locs) in place, then
 * re-reads the region from disk to verify. Used for the Chamber of Ascension wall (2026-11-04): rev 241 replaced
 * the wall we had removed at 1631,4961,1 with loc 42650, and the stage-3 merge let it back in (BuildMaps241 now
 * drops such replacements on a rebuild; this patches an already-built cache without one).
 *
 * A leading + adds the loc instead (idempotent, 2026-11-08): map locs are built with the rest of the scene, so a wall
 * blends into its neighbours and the floor, which a server-spawned one doesn't.
 *
 *   PatchRemoveLoc <cache241Dir> <[+]id,worldX,worldY,plane,type,rotation> [more...]
 */
public class PatchRemoveLoc {
	public static void main(String[] a) throws Exception {
		Store store = new Store(new File(a[0]));
		store.load();
		Index maps = store.findIndex(5);
		Map<Integer, List<int[]>> byRegion = new TreeMap<>();
		for (int i = 1; i < a.length; i++) {
			boolean add = a[i].startsWith("+");
			int[] v = Arrays.copyOf(Arrays.stream(a[i].substring(add ? 1 : 0).split(",")).mapToInt(Integer::parseInt).toArray(), 7);
			v[6] = add ? 1 : 0;
			byRegion.computeIfAbsent((v[1] >> 6) << 8 | (v[2] >> 6), k -> new ArrayList<>()).add(v);
		}
		Map<Integer, byte[][]> written = new TreeMap<>();
		for (var e : byRegion.entrySet()) {
			int r = e.getKey();
			Archive ar = maps.getArchive(r);
			if (ar == null) throw new IllegalStateException("region " + r + " not in the cache");
			ArchiveFiles f = ar.getFiles(store.getStorage().loadArchive(ar));
			byte[][] files = new byte[ar.getFileData().length][];
			for (int i = 0; i < files.length; i++) files[i] = f.findFile(i).getContents();
			List<MapCodec.Loc> locs = MapCodec.decodeLocs(files[1]);
			for (int[] v : e.getValue()) {
				boolean exists = locs.stream().anyMatch(l -> l.id == v[0] && l.x == (v[1] & 63) && l.y == (v[2] & 63) && l.plane == v[3] && l.type == v[4] && l.rotation == v[5]);
				if (v[6] == 1) {
					if (!exists)
						locs.add(new MapCodec.Loc(v[0], v[3], v[1] & 63, v[2] & 63, v[4], v[5]));
					System.out.println("region " + r + ": " + (exists ? "already has" : "added") + " loc " + Arrays.toString(v));
					continue;
				}
				if (!exists) throw new IllegalStateException("loc " + Arrays.toString(v) + " not found in region " + r);
				locs.removeIf(l -> l.id == v[0] && l.x == (v[1] & 63) && l.y == (v[2] & 63) && l.plane == v[3] && l.type == v[4] && l.rotation == v[5]);
				System.out.println("region " + r + ": removed loc " + Arrays.toString(v));
			}
			locs.sort(Comparator.<MapCodec.Loc>comparingInt(l -> l.id).thenComparingInt(l -> l.plane << 12 | l.x << 6 | l.y)); // the format is delta-coded
			files[1] = MapCodec.encodeLocs(locs);
			written.put(r, files);
			Container c = new Container(ar.getCompression(), -1);
			byte[] raw = SpliceItemOption.joinChunks(Arrays.asList(files));
			c.compress(raw, null);
			store.getStorage().store(5, r, c.data);
			FileData[] fd = new FileData[files.length];
			for (int i = 0; i < fd.length; i++) { fd[i] = new FileData(); fd[i].setId(i); }
			ar.setFileData(fd);
			ar.setCrc(c.crc);
			ar.setRevision(ar.getRevision() + 1);
			ar.setCompressedSize(c.data.length);
			ar.setDecompressedSize(raw.length);
		}
		IndexData id = maps.toIndexData();
		Container ic = new Container(maps.getCompression(), -1);
		ic.compress(id.writeIndexData(), null);
		store.getStorage().store(255, 5, ic.data);
		maps.setCrc(ic.crc);
		store.close();

		Store check = new Store(new File(a[0]));
		check.load();
		for (var e : written.entrySet()) {
			Archive ar = check.findIndex(5).getArchive(e.getKey());
			ArchiveFiles f = ar.getFiles(check.getStorage().loadArchive(ar));
			for (int i = 0; i < e.getValue().length; i++)
				if (!Arrays.equals(f.findFile(i).getContents(), e.getValue()[i])) throw new IllegalStateException("region " + e.getKey() + " file " + i + " differs after write");
			MapCodec.decodeTerrain(f.findFile(0).getContents(), false);
			System.out.println("region " + e.getKey() + ": verified, " + MapCodec.decodeLocs(f.findFile(1).getContents()).size() + " locs");
		}
	}
}

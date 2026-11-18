import net.runelite.cache.fs.*;
import net.runelite.cache.index.FileData;
import net.runelite.cache.index.IndexData;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Replaces a whole region's map with an RSPSi export made on our rev 231 cache (2026-11-11: the home, region 12342,
 * assets/home_map/landnew.dat = terrain, objnew.dat = locs). Does what stage 3 does for an override region: the terrain
 * is read in the 231 grammar, our custom floor overlays are moved by the manifest (a map stores overlay id + 1, e.g.
 * 437 -> 1106) and it's written in the 241 grammar (no extra layers); loc ids go through the manifest's "obj" table.
 * The region's other files (lighting / environment) are kept. Re-reads the region afterwards to verify. Idempotent.
 *
 * Optional trailing {@code old:new} pairs swap objects afterwards (2026-11-11: the home's banners 39153 -> 65042 and the
 * standard 40944 -> 65043), each keeping its tile, type and rotation. {@code h:x,y,plane,height} sets a tile's terrain height
 * (world coordinates): the home's north wall moved onto row 3506 against the plaza bank, and a loc stands at the average
 * height of its tile's corners, so row 3507's slope lifted it; 3112-3117,3507 go to the floor's 34.
 *
 * {@code rm:id,x,y,plane} removes the loc with that (final) id whose origin is that world tile; it fails if there is none
 * (2026-11-11: the home's deposit box 39239 at 3113,3495). {@code u:x,y,plane} clears a tile's "blocked" floor flag
 * (settings bit 1), keeping its other flags: the RSPSi export left stray blocked tiles inside the home's shop room.
 * {@code fl:x0,y0,x1,y1,tx,ty} moves the overlays (id, shape, rotation) of a world box so its south-west corner is tx,ty; the
 * source tiles keep their underlay, the target tiles keep theirs (2026-11-11: the Soul Sanctuary's obsidian floor).
 * {@code mv:id,x,y,nx,ny} moves the loc with that (final) id from one origin tile to another (the Karn Teleports nexus).
 *
 *   PatchRegionMap apply &lt;cache241&gt; &lt;region&gt; &lt;terrain.dat&gt; &lt;locs.dat&gt; &lt;remap-241.json&gt; [old:new ...]
 */
public final class PatchRegionMap {

	private PatchRegionMap() {
	}

	private static MapCodec.Tile tile(MapCodec.Terrain terrain, int rx, int ry, int region, int x, int y) {
		if (x >> 6 != region >> 8 || y >> 6 != (region & 0xff))
			throw new IllegalStateException("tile " + x + "," + y + " is outside region " + region);
		return terrain.planes[0][x - rx][y - ry];
	}

	public static void main(String[] a) throws Exception {
		if (a.length < 6 || !a[0].equals("apply"))
			throw new IllegalArgumentException("apply <cache241> <region> <terrain.dat> <locs.dat> <remap-241.json> [old:new ...]");
		Map<Integer, Integer> swap = new HashMap<>();
		List<int[]> heights = new ArrayList<>(), removals = new ArrayList<>(), unblocks = new ArrayList<>(), floors = new ArrayList<>(),
				moves = new ArrayList<>();
		for (int i = 6; i < a.length; i++) {
			if (a[i].startsWith("fl:") || a[i].startsWith("mv:")) {
				(a[i].startsWith("fl:") ? floors : moves).add(Arrays.stream(a[i].substring(3).split(",")).mapToInt(Integer::parseInt).toArray());
				continue;
			}
			if (a[i].startsWith("u:")) {
				unblocks.add(Arrays.stream(a[i].substring(2).split(",")).mapToInt(Integer::parseInt).toArray());
				continue;
			}
			if (a[i].startsWith("rm:")) {
				removals.add(Arrays.stream(a[i].substring(3).split(",")).mapToInt(Integer::parseInt).toArray());
				continue;
			}
			if (a[i].startsWith("h:")) {
				heights.add(Arrays.stream(a[i].substring(2).split(",")).mapToInt(Integer::parseInt).toArray());
				continue;
			}
			String[] p = a[i].split(":");
			swap.put(Integer.parseInt(p[0]), Integer.parseInt(p[1]));
		}
		int region = Integer.parseInt(a[2]);
		Manifest manifest = new Manifest(Path.of(a[5]));

		MapCodec.Terrain terrain = MapCodec.decodeTerrain(Files.readAllBytes(Path.of(a[3])), true);
		int movedTiles = 0;
		for (MapCodec.Tile[][] plane : terrain.planes)
			for (MapCodec.Tile[] row : plane)
				for (MapCodec.Tile t : row)
					if (t.overlay > 0) {
						int mapped = manifest.map("overlay", t.overlay - 1);
						if (mapped != t.overlay - 1) {
							t.overlay = mapped + 1;
							movedTiles++;
						}
					}
		int rx = (region >> 8) << 6, ry = (region & 0xff) << 6;
		for (int[] h : heights) {
			if (h[0] >> 6 != region >> 8 || h[1] >> 6 != (region & 0xff))
				throw new IllegalStateException("height " + Arrays.toString(h) + " is outside region " + region);
			MapCodec.Tile t = terrain.planes[h[2]][h[0] - rx][h[1] - ry];
			t.height = h[3]; // an explicit height (opcode 1) ends the tile's stream, as RSPSi writes it
		}
		for (int[] u : unblocks) {
			if (u[0] >> 6 != region >> 8 || u[1] >> 6 != (region & 0xff))
				throw new IllegalStateException("unblock " + Arrays.toString(u) + " is outside region " + region);
			MapCodec.Tile t = terrain.planes[u[2]][u[0] - rx][u[1] - ry];
			if (t.settings <= 0 || (t.settings & 1) == 0)
				throw new IllegalStateException("tile " + Arrays.toString(u) + " is not blocked (settings " + t.settings + ")");
			t.settings &= ~1;
			if (t.settings == 0) { // there is no "settings 0" op (op 49 is an overlay shape): drop the op, or the tile loses its floor
				t.settings = -1;
				t.order.remove(Integer.valueOf('S'));
			}
		}
		for (int[] f : floors) {
			int w = f[2] - f[0] + 1, h = f[3] - f[1] + 1;
			MapCodec.Tile[][] copied = new MapCodec.Tile[w][h];
			for (int x = 0; x < w; x++)
				for (int y = 0; y < h; y++) { // lift the overlays out first, so overlapping boxes still work
					MapCodec.Tile t = tile(terrain, rx, ry, region, f[0] + x, f[1] + y);
					MapCodec.Tile c = new MapCodec.Tile();
					c.overlay = t.overlay;
					c.shape = t.shape;
					c.rotation = t.rotation;
					copied[x][y] = c;
					t.overlay = -1;
					t.order.remove(Integer.valueOf('O'));
				}
			for (int x = 0; x < w; x++)
				for (int y = 0; y < h; y++) {
					MapCodec.Tile c = copied[x][y], t = tile(terrain, rx, ry, region, f[4] + x, f[5] + y);
					t.order.remove(Integer.valueOf('O'));
					t.overlay = c.overlay;
					t.shape = c.shape;
					t.rotation = c.rotation;
					if (c.overlay > 0)
						t.order.add(0, (int) 'O');
				}
		}
		List<MapCodec.Loc> locs = MapCodec.decodeLocs(Files.readAllBytes(Path.of(a[4])));
		int movedLocs = 0;
		for (MapCodec.Loc l : locs) {
			int id = manifest.map("obj", l.id);
			if (id != l.id) {
				l.id = id;
				movedLocs++;
			}
		}
		int swapped = 0;
		for (MapCodec.Loc l : locs) {
			Integer to = swap.get(l.id);
			if (to != null) {
				l.id = to;
				swapped++;
			}
		}
		for (int[] rm : removals) {
			int before = locs.size();
			locs.removeIf(l -> l.id == rm[0] && rx + l.x == rm[1] && ry + l.y == rm[2] && l.plane == rm[3]);
			if (locs.size() == before)
				throw new IllegalStateException("no loc " + Arrays.toString(rm) + " to remove in region " + region);
		}
		for (int[] mv : moves) {
			MapCodec.Loc found = null;
			for (MapCodec.Loc l : locs)
				if (l.id == mv[0] && rx + l.x == mv[1] && ry + l.y == mv[2] && l.plane == 0)
					found = l;
			if (found == null)
				throw new IllegalStateException("no loc " + Arrays.toString(mv) + " to move in region " + region);
			found.x = mv[3] - rx;
			found.y = mv[4] - ry;
		}
		locs.sort(Comparator.<MapCodec.Loc>comparingInt(l -> l.id).thenComparingInt(l -> l.plane << 12 | l.x << 6 | l.y));
		byte[] terrainBytes = MapCodec.encodeTerrain(terrain, true), locBytes = MapCodec.encodeLocs(locs);

		Store store = new Store(new File(a[1]));
		store.load();
		Index maps = store.findIndex(5);
		Archive ar = maps.getArchive(region);
		if (ar == null)
			throw new IllegalStateException("region " + region + " not in the cache");
		ArchiveFiles f = ar.getFiles(store.getStorage().loadArchive(ar));
		byte[][] files = new byte[ar.getFileData().length][];
		for (int i = 0; i < files.length; i++)
			files[i] = f.findFile(i).getContents();
		if (Arrays.equals(files[0], terrainBytes) && Arrays.equals(files[1], locBytes)) {
			store.close();
			System.out.println("RESULT region map " + region + ": already applied");
			return;
		}
		files[0] = terrainBytes;
		files[1] = locBytes;
		Container c = new Container(ar.getCompression(), -1);
		byte[] raw = SpliceItemOption.joinChunks(Arrays.asList(files));
		c.compress(raw, null);
		store.getStorage().store(5, region, c.data);
		FileData[] fd = new FileData[files.length];
		for (int i = 0; i < fd.length; i++) {
			fd[i] = new FileData();
			fd[i].setId(i);
		}
		ar.setFileData(fd);
		ar.setCrc(c.crc);
		ar.setRevision(ar.getRevision() + 1);
		ar.setCompressedSize(c.data.length);
		ar.setDecompressedSize(raw.length);
		IndexData id = maps.toIndexData();
		Container ic = new Container(maps.getCompression(), -1);
		ic.compress(id.writeIndexData(), null);
		store.getStorage().store(255, 5, ic.data);
		maps.setCrc(ic.crc);
		store.close();

		Store check = new Store(new File(a[1]));
		check.load();
		Archive got = check.findIndex(5).getArchive(region);
		ArchiveFiles gf = got.getFiles(check.getStorage().loadArchive(got));
		for (int i = 0; i < files.length; i++)
			if (!Arrays.equals(gf.findFile(i).getContents(), files[i]))
				throw new IllegalStateException("region " + region + " file " + i + " differs after write");
		MapCodec.decodeTerrain(gf.findFile(0).getContents(), false);
		int n = MapCodec.decodeLocs(gf.findFile(1).getContents()).size();
		check.close();
		System.out.println("RESULT region map " + region + ": replaced and verified (" + n + " locs, " + movedTiles + " overlay tiles and "
				+ movedLocs + " locs moved by the manifest, " + swapped + " swapped, " + heights.size() + " heights set, " + removals.size() + " removed, " + unblocks.size() + " unblocked, " + floors.size() + " floors moved, " + moves.size() + " locs moved)");
	}
}

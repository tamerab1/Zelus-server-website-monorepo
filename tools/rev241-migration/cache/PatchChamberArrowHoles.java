import net.runelite.cache.fs.*;
import net.runelite.cache.index.FileData;
import net.runelite.cache.index.IndexData;

import java.io.File;
import java.util.*;

/**
 * Stage 14 of the rev 241 cache build (2026-11-06): fills the holes beside the Chamber of Ascension's arrow heads.
 * The red / blue / green / yellow paths (region 6477, plane 1, bridge tiles) end in arrow heads drawn with the
 * triangle overlay shape 10, but those 8 tiles had no underlay, so the other half of each tile drew nothing and the
 * plane-0 floor 47 height units below showed through: a hole that flickered as the camera moved. Each gets underlay
 * 246 (#6c6160, the nearest underlay to the flagstone texture 43 of the floor around them, avg #6a6a5d).
 * Idempotent: a tile that already has the underlay is left alone; anything unexpected stops the stage.
 * Usage: PatchChamberArrowHoles apply &lt;cache241&gt;
 */
public final class PatchChamberArrowHoles {

	private PatchChamberArrowHoles() {
	}

	static final int REGION = 6477, PLANE = 1, UNDERLAY = 246, SHAPE = 10;
	/** {local x, local y, map overlay value}: two tiles beside each arrow head */
	static final int[][] TILES = { { 30, 24, 18 }, { 32, 24, 18 }, { 30, 38, 72 }, { 32, 38, 72 }, { 26, 30, 97 }, { 26, 32, 97 },
			{ 36, 30, 190 }, { 36, 32, 190 } };

	public static void main(String[] a) throws Exception {
		if (a.length != 2 || !a[0].equals("apply"))
			throw new IllegalArgumentException("apply <cache241>");
		Store store = new Store(new File(a[1]));
		store.load();
		Index maps = store.findIndex(5);
		Archive ar = maps.getArchive(REGION);
		if (ar == null)
			throw new IllegalStateException("region " + REGION + " not in the cache");
		ArchiveFiles f = ar.getFiles(store.getStorage().loadArchive(ar));
		byte[][] files = new byte[ar.getFileData().length][];
		for (int i = 0; i < files.length; i++)
			files[i] = f.findFile(i).getContents();
		MapCodec.Terrain terrain = MapCodec.decodeTerrain(files[0], false);
		int changed = 0;
		for (int[] t : TILES) {
			MapCodec.Tile tile = terrain.planes[PLANE][t[0]][t[1]];
			if (tile.overlay != t[2] || tile.shape != SHAPE)
				throw new IllegalStateException("tile " + t[0] + "," + t[1] + " is overlay " + tile.overlay + " shape " + tile.shape
						+ ", expected " + t[2] + " shape " + SHAPE + ": the map changed, re-check before patching");
			if (tile.underlay == UNDERLAY)
				continue;
			if (tile.underlay > 0)
				throw new IllegalStateException("tile " + t[0] + "," + t[1] + " already has underlay " + tile.underlay);
			tile.underlay = UNDERLAY;
			if (!tile.order.contains((int) 'U'))
				tile.order.add((int) 'U');
			changed++;
		}
		if (changed > 0) {
			files[0] = MapCodec.encodeTerrain(terrain, true);
			Container c = new Container(ar.getCompression(), -1);
			byte[] raw = SpliceItemOption.joinChunks(Arrays.asList(files));
			c.compress(raw, null);
			store.getStorage().store(5, REGION, c.data);
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
		}
		store.close();

		Store check = new Store(new File(a[1]));
		check.load();
		Archive back = check.findIndex(5).getArchive(REGION);
		ArchiveFiles bf = back.getFiles(check.getStorage().loadArchive(back));
		MapCodec.Terrain t2 = MapCodec.decodeTerrain(bf.findFile(0).getContents(), false);
		for (int[] t : TILES)
			if (t2.planes[PLANE][t[0]][t[1]].underlay != UNDERLAY)
				throw new IllegalStateException("tile " + t[0] + "," + t[1] + " not patched after write");
		if (!Arrays.equals(bf.findFile(1).getContents(), files[1]))
			throw new IllegalStateException("locs changed");
		check.close();
		System.out.println("RESULT chamber arrow holes: " + changed + " tile(s) filled, " + TILES.length + " verified");
	}
}

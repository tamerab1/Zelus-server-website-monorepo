import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Container;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;
import net.runelite.cache.index.IndexData;

import java.io.File;
import java.util.List;

// Read source + Zelus. Imports the 4 real-world mapsquares immediately adjacent to the Fallen
// Cathedral's own (39,34) -- (38,34), (40,34), (39,33), (39,35) -- so the cathedral is no longer a
// single isolated mapsquare surrounded by void. Same technique as ImportCathedralMap.java (which
// this mirrors exactly): each source group has 5 sub-files, of which only 0 (terrain) and 1 (locs)
// matter to Zelus's own two-archive-by-name convention -- confirmed by checking the CATHEDRAL's own
// source group (10018), which ALSO has 5 sub-files (2/3/4 present, 228/2/1 bytes, IDENTICAL sizes in
// a neighbor group too -- boilerplate metadata Zelus's Region.java has no mechanism to read anyway,
// not something the already-working cathedral import silently lost).
//
// Destination archive ids are arbitrary (Region.java looks up by NAME HASH only, per
// ImportCathedralMap.java's own comment) -- picked as a fresh, verified-free block (20001-20008)
// rather than a same/adjacent-id-to-source scheme, because these 4 mapsquares' own real (x<<8)|z
// group ids are numerically adjacent to each other AND to 39,34's own 10018/10019 (39,33=10017,
// 39,35=10019 -- 10019 collides with l39_34's own existing archive id), so a naive "+1" offset
// would have collided immediately.
//
// mode: verify | apply
public class ImportWyrmscraigNeighborMaps {
    static final int[][] SQUARES = {{38, 34}, {40, 34}, {39, 33}, {39, 35}};
    static final int[] DST_TERRAIN_IDS = {20001, 20003, 20005, 20007};
    static final int[] DST_LOC_IDS =     {20002, 20004, 20006, 20008};

    public static void main(String[] args) throws Exception {
        String mode = args[0];
        String srcPath = args[1];
        String dstPath = args[2];
        boolean apply = mode.equals("apply");
        if (!apply && !mode.equals("verify")) throw new IllegalArgumentException("mode must be verify or apply");

        try (Store src = new Store(new File(srcPath)); Store dst = new Store(new File(dstPath))) {
            src.load();
            dst.load();
            Storage srcStorage = src.getStorage();
            Storage dstStorage = dst.getStorage();
            Index srcMaps = src.getIndex(IndexType.MAPS);
            Index dstMaps = dst.getIndex(IndexType.MAPS);

            byte[][] terrainBytes = new byte[SQUARES.length][];
            byte[][] locBytes = new byte[SQUARES.length][];
            boolean ok = true;

            for (int i = 0; i < SQUARES.length; i++) {
                int x = SQUARES[i][0], z = SQUARES[i][1];
                int groupId = (x << 8) | z;
                String terrainName = "m" + x + "_" + z;
                String locName = "l" + x + "_" + z;

                Archive srcGroup = srcMaps.getArchive(groupId);
                if (srcGroup == null) {
                    System.out.println("(" + x + "," + z + ") source group " + groupId + " NOT FOUND");
                    ok = false;
                    continue;
                }
                byte[] srcDecompressed = srcGroup.decompress(srcStorage.loadArchive(srcGroup));
                FileData[] srcFileData = srcGroup.getFileData();
                List<byte[]> srcContents = SpliceItemOption.splitChunks(srcDecompressed, srcFileData.length);

                for (int j = 0; j < srcFileData.length; j++) {
                    if (srcFileData[j].getId() == 0) terrainBytes[i] = srcContents.get(j);
                    if (srcFileData[j].getId() == 1) locBytes[i] = srcContents.get(j);
                }
                if (terrainBytes[i] == null || locBytes[i] == null) {
                    System.out.println("(" + x + "," + z + ") group " + groupId + " missing terrain or locs sub-file");
                    ok = false;
                    continue;
                }
                System.out.println("(" + x + "," + z + ") group " + groupId + ": terrain=" + terrainBytes[i].length
                        + " bytes, locs=" + locBytes[i].length + " bytes");

                ok &= checkFree(dstMaps, DST_TERRAIN_IDS[i], terrainName);
                ok &= checkFree(dstMaps, DST_LOC_IDS[i], locName);
            }

            System.out.println(ok ? "VERIFY: all 4 squares readable, all 8 target slots/names free."
                    : "VERIFY: FAILED -- see above.");
            if (!apply) {
                System.out.println("(dry run -- pass 'apply' to write)");
                return;
            }
            if (!ok) {
                System.out.println("ABORTING apply: verify failed above. Nothing written.");
                return;
            }

            for (int i = 0; i < SQUARES.length; i++) {
                int x = SQUARES[i][0], z = SQUARES[i][1];
                writeSingleFileArchive(dstStorage, dstMaps, DST_TERRAIN_IDS[i], "m" + x + "_" + z, terrainBytes[i]);
                writeSingleFileArchive(dstStorage, dstMaps, DST_LOC_IDS[i], "l" + x + "_" + z, locBytes[i]);
            }
            writeIndexReferenceTable(dstStorage, dstMaps);
            System.out.println("DONE. 4 mapsquares imported.");
        }
    }

    static boolean checkFree(Index dstMaps, int archiveId, String name) {
        boolean idFree = dstMaps.getArchive(archiveId) == null;
        int hash = javaHash(name);
        boolean nameFree = true;
        for (Archive a : dstMaps.getArchives()) {
            if (a.getNameHash() == hash) { nameFree = false; break; }
        }
        System.out.println("  target id " + archiveId + " (" + name + "): id=" + (idFree ? "free" : "OCCUPIED")
                + " nameHash=" + (nameFree ? "free" : "OCCUPIED"));
        return idFree && nameFree;
    }

    static int javaHash(String s) {
        int hash = 0;
        for (int i = 0; i < s.length(); i++) hash = (hash << 5) - hash + s.charAt(i);
        return hash;
    }

    static final int COMPRESSION_NONE = 0;

    static void writeSingleFileArchive(Storage storage, Index index, int archiveId, String name, byte[] content) throws Exception {
        Container container = new Container(COMPRESSION_NONE, -1);
        container.compress(content, null);

        Archive archive = index.addArchive(archiveId);
        archive.setNameHash(javaHash(name));
        archive.setCompression(COMPRESSION_NONE);
        archive.setRevision(1);
        archive.setCrc(container.crc);
        archive.setCompressedSize(container.data.length);
        archive.setDecompressedSize(content.length);

        FileData fd = new FileData();
        fd.setId(0);
        fd.setNameHash(-1);
        archive.setFileData(new FileData[]{fd});

        storage.store(index.getId(), archiveId, container.data);
        System.out.println("  wrote " + name + " -> archive " + archiveId + ": " + content.length + " bytes (raw), "
                + container.data.length + " bytes (compressed)");
    }

    static void writeIndexReferenceTable(Storage storage, Index index) throws Exception {
        IndexData indexData = index.toIndexData();
        byte[] rawIndex = indexData.writeIndexData();
        Container idxContainer = new Container(index.getCompression(), -1);
        idxContainer.compress(rawIndex, null);
        storage.store(255, index.getId(), idxContainer.data);
        index.setCrc(idxContainer.crc);
    }
}

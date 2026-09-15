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

// Read source + Zelus. Imports mapsquare (40,35) -- the diagonal (NE) neighbor of the Cathedral's
// own (39,34) -- needed to fully fill DynamicMap's 2x2 build quad (see DynamicMap.buildNw/buildSe/
// buildNe: the engine already supports building a 2x2 block of REAL mapsquares into one instance,
// MadAngel just never called the 3 extra methods). Same technique as
// ImportWyrmscraigNeighborMaps.java.
//
// mode: verify | apply
public class ImportWyrmscraigDiagonal {
    static final int GROUP_ID = 10275; // (40 << 8) | 35
    static final int DST_TERRAIN_ID = 20009;
    static final int DST_LOC_ID = 20010;
    static final String TERRAIN_NAME = "m40_35";
    static final String LOC_NAME = "l40_35";

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

            Archive srcGroup = srcMaps.getArchive(GROUP_ID);
            if (srcGroup == null) throw new IllegalStateException("source group " + GROUP_ID + " not found");
            byte[] srcDecompressed = srcGroup.decompress(srcStorage.loadArchive(srcGroup));
            FileData[] srcFileData = srcGroup.getFileData();
            List<byte[]> srcContents = SpliceItemOption.splitChunks(srcDecompressed, srcFileData.length);

            byte[] terrainBytes = null, locBytes = null;
            for (int i = 0; i < srcFileData.length; i++) {
                if (srcFileData[i].getId() == 0) terrainBytes = srcContents.get(i);
                if (srcFileData[i].getId() == 1) locBytes = srcContents.get(i);
            }
            if (terrainBytes == null || locBytes == null) {
                throw new IllegalStateException("source group missing terrain (file 0) or locs (file 1)");
            }
            System.out.println("source terrain: " + terrainBytes.length + " bytes, locs: " + locBytes.length + " bytes");

            boolean ok = true;
            ok &= checkFree(dstMaps, DST_TERRAIN_ID, TERRAIN_NAME);
            ok &= checkFree(dstMaps, DST_LOC_ID, LOC_NAME);
            System.out.println(ok ? "VERIFY: both target slots/names free." : "VERIFY: FAILED.");
            if (!apply) {
                System.out.println("(dry run -- pass 'apply' to write)");
                return;
            }
            if (!ok) {
                System.out.println("ABORTING apply: verify failed above. Nothing written.");
                return;
            }

            writeSingleFileArchive(dstStorage, dstMaps, DST_TERRAIN_ID, TERRAIN_NAME, terrainBytes);
            writeSingleFileArchive(dstStorage, dstMaps, DST_LOC_ID, LOC_NAME, locBytes);
            writeIndexReferenceTable(dstStorage, dstMaps);
            System.out.println("DONE. m40_35 -> archive " + DST_TERRAIN_ID + ", l40_35 -> archive " + DST_LOC_ID);
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

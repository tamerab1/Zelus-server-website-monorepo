import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Container;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;
import net.runelite.cache.index.IndexData;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

// Read source cache, write Zelus cache. Root-cause fix for the Mad Angel bomb-special client
// crash: her bomb spotanims (4014 GFX_BOMB_THROW, 4015 GFX_BOMB_TRAVEL, 4016 unused, 4017
// GFX_BOMB_HIT/REFLECT) each carry an INTERNAL animation reference (spotanim opcode 2) pointing at
// sequences 14444-14447 -- confirmed via DumpSpotAnimV2 against the live Zelus cache. Those 4
// sequences were never imported in the original PackMadAngel pass (which only handled sequences
// MadAngel.java calls directly via animate()), so every one of those spotanims points at nothing.
// The client crash (NullPointerException: Cannot load from int array because "this.ax.ae" is
// null) fires the instant the client tries to render whichever of these is used first -- confirmed
// by an extensive in-game bisection that ruled out every other candidate (npc.lock(), addEvent
// scheduling, Projectile.send, World.sendGraphics, scatterTile's collision search, the reflect
// hit direction) one at a time.
//
// Confirmed via CheckSeqOpcode against ALL FOUR of the source's own cache copies (js5/vanilla/
// game/enriched -- byte-identical in each): 14444 (88 bytes, opcode 1), 14445 (100 bytes, opcode
// 1), 14446 (64 bytes, opcode 1), 14447 (63 bytes, opcodes 1+14). All decode with the exact same
// opcode vocabulary already proven safe elsewhere in this exact sequence config (14443/14450/
// 14451 all also use opcode 1 and/or 14). Confirmed via CheckSeqOpcode against Zelus's live cache:
// all 4 target ids come back NOT FOUND (free, no collision) -- 1:1 copy, no remap needed.
//
// mode: verify | apply
public class ImportMissingBombSeqs {

    static final int[][] SEQS = {
            {14444, 14444}, {14445, 14445}, {14446, 14446}, {14447, 14447},
    };

    public static void main(String[] args) throws Exception {
        String mode = args[0];
        String srcPath = args[1];
        String dstPath = args[2];
        boolean apply = mode.equals("apply");
        if (!apply && !mode.equals("verify")) {
            throw new IllegalArgumentException("mode must be verify or apply");
        }

        try (Store src = new Store(new File(srcPath)); Store dst = new Store(new File(dstPath))) {
            src.load();
            dst.load();
            Storage srcStorage = src.getStorage();
            Storage dstStorage = dst.getStorage();

            boolean ok = verifyConfigPairs(dst, ConfigType.SEQUENCE.getId(), SEQS);
            ok &= verifySource(src, ConfigType.SEQUENCE.getId(), SEQS);
            System.out.println(ok ? "VERIFY: all target ids free, all source ids present." : "VERIFY: FAILED -- see above.");
            if (!apply) {
                System.out.println("(dry run -- pass 'apply' to write)");
                return;
            }
            if (!ok) {
                System.out.println("ABORTING apply: verify failed above. Nothing written.");
                return;
            }

            applyConfig(srcStorage, src.getIndex(IndexType.CONFIGS), dstStorage, dst.getIndex(IndexType.CONFIGS),
                    ConfigType.SEQUENCE.getId(), SEQS);

            System.out.println("=== DONE. All writes complete. ===");
        }
    }

    static boolean verifySource(Store src, int configTypeId, int[][] pairs) throws Exception {
        Index configs = src.getIndex(IndexType.CONFIGS);
        Archive archive = configs.getArchive(configTypeId);
        FileData[] fileData = archive.getFileData();
        boolean ok = true;
        for (int[] pair : pairs) {
            int srcId = pair[0];
            boolean present = false;
            for (FileData fd : fileData) if (fd.getId() == srcId) present = true;
            System.out.println("SOURCE config[" + configTypeId + "] id " + srcId + ": " + (present ? "present" : "MISSING -- CONFLICT"));
            ok &= present;
        }
        return ok;
    }

    static boolean verifyConfigPairs(Store dst, int configTypeId, int[][] pairs) throws Exception {
        Index configs = dst.getIndex(IndexType.CONFIGS);
        Archive archive = configs.getArchive(configTypeId);
        FileData[] fileData = archive.getFileData();
        boolean ok = true;
        for (int[] pair : pairs) {
            int targetId = pair[1];
            boolean occupied = false;
            for (FileData fd : fileData) if (fd.getId() == targetId) occupied = true;
            System.out.println("DEST config[" + configTypeId + "] target " + targetId + " (from source " + pair[0] + "): "
                    + (occupied ? "OCCUPIED -- CONFLICT" : "free"));
            ok &= !occupied;
        }
        return ok;
    }

    static void applyConfig(Storage srcStorage, Index srcIndex, Storage dstStorage, Index dstIndex,
                             int configTypeId, int[][] pairs) throws Exception {
        Archive srcArchive = srcIndex.getArchive(configTypeId);
        byte[] srcDecompressed = srcArchive.decompress(srcStorage.loadArchive(srcArchive));
        FileData[] srcFileData = srcArchive.getFileData();
        List<byte[]> srcContents = SpliceItemOption.splitChunks(srcDecompressed, srcFileData.length);

        List<Integer> newIds = new ArrayList<>();
        List<byte[]> newRaws = new ArrayList<>();
        for (int[] pair : pairs) {
            int srcId = pair[0], dstId = pair[1];
            int slot = -1;
            for (int j = 0; j < srcFileData.length; j++) if (srcFileData[j].getId() == srcId) slot = j;
            if (slot == -1) {
                throw new IllegalStateException("source id " + srcId + " not found in config[" + configTypeId + "] -- aborting mid-write.");
            }
            byte[] raw = srcContents.get(slot);
            newIds.add(dstId);
            newRaws.add(raw);
            System.out.println("  " + srcId + " -> " + dstId + ": " + raw.length + " bytes staged");
        }

        Archive dstArchive = dstIndex.getArchive(configTypeId);
        byte[] dstDecompressed = dstArchive.decompress(dstStorage.loadArchive(dstArchive));
        FileData[] dstFileData = dstArchive.getFileData();
        List<byte[]> dstContents = SpliceItemOption.splitChunks(dstDecompressed, dstFileData.length);

        for (int id : newIds) {
            for (FileData fd : dstFileData) {
                if (fd.getId() == id) {
                    throw new IllegalStateException("target id " + id + " now occupied in config[" + configTypeId
                            + "] -- changed since verify, aborting mid-write.");
                }
            }
        }

        List<Integer> sortOrder = new ArrayList<>();
        for (int i = 0; i < newIds.size(); i++) sortOrder.add(i);
        sortOrder.sort((a, b) -> Integer.compare(newIds.get(a), newIds.get(b)));

        List<FileData> mergedFileData = new ArrayList<>(dstFileData.length + newIds.size());
        List<byte[]> mergedContents = new ArrayList<>(dstFileData.length + newIds.size());
        int ni = 0;
        for (int i = 0; i < dstFileData.length; i++) {
            while (ni < sortOrder.size() && newIds.get(sortOrder.get(ni)) < dstFileData[i].getId()) {
                int idx = sortOrder.get(ni);
                FileData nfd = new FileData();
                nfd.setId(newIds.get(idx));
                nfd.setNameHash(-1);
                mergedFileData.add(nfd);
                mergedContents.add(newRaws.get(idx));
                ni++;
            }
            mergedFileData.add(dstFileData[i]);
            mergedContents.add(dstContents.get(i));
        }
        while (ni < sortOrder.size()) {
            int idx = sortOrder.get(ni);
            FileData nfd = new FileData();
            nfd.setId(newIds.get(idx));
            nfd.setNameHash(-1);
            mergedFileData.add(nfd);
            mergedContents.add(newRaws.get(idx));
            ni++;
        }

        dstArchive.setFileData(mergedFileData.toArray(new FileData[0]));
        byte[] newDecompressed = SpliceItemOption.joinChunks(mergedContents);
        Container container = new Container(dstArchive.getCompression(), -1);
        container.compress(newDecompressed, null);

        dstStorage.store(dstIndex.getId(), configTypeId, container.data);
        dstArchive.setCrc(container.crc);
        dstArchive.setRevision(dstArchive.getRevision() + 1);
        dstArchive.setCompressedSize(container.data.length);
        dstArchive.setDecompressedSize(newDecompressed.length);

        writeIndexReferenceTable(dstStorage, dstIndex);
        System.out.println("  config[" + configTypeId + "] revision now " + dstArchive.getRevision()
                + ", " + newIds.size() + " new entries.");
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

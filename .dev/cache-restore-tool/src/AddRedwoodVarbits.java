import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.VarbitDefinition;
import net.runelite.cache.definitions.loaders.VarbitLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Container;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;
import net.runelite.cache.index.IndexData;
import net.runelite.cache.io.OutputStream;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

// Adds 8 brand-new varbit definitions (ids 17796-17803, each a whole dedicated new
// varp 4829-4836, bits 0-31) to the live cache's VARBIT config archive, fixing the
// redwood farming patch bug: all 9 patch objects (34051-34059) currently share
// varbitID=7907 in their own object definitions (confirmed via direct cache dump),
// so growing one physically renders all 9 as the same stage. 34051 keeps 7907;
// this tool prepares the 8 NEW varbits that 34052-34059 will be repointed to by a
// separate object-def edit tool (RepointRedwoodObjects.java).
//
// Format verified directly from this project's own VarBitType.decode() (server
// source, not reverse-engineered): opcode 1 = varpId(u16) + leastSigBit(u8) +
// mostSigBit(u8), terminated by opcode 0.
//
// Both new varbit ids (17796-17803, current archive max is 17795) and new varp ids
// (4829-4836, current VARPLAYER archive max is 4828) confirmed free via
// FindFreeVarbits.java / FindMaxVarp.java against this exact cache, and grepped
// clean against this project's own VarPlayerRepository.java for same-codebase
// collisions.
//
// Usage: <cachePath> [apply]
public class AddRedwoodVarbits {
    static final int FIRST_NEW_VARBIT_ID = 17796;
    static final int FIRST_NEW_VARP_ID = 4829;
    static final int COUNT = 8;

    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        boolean apply = args.length > 1 && args[1].equals("apply");

        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Index index = store.getIndex(IndexType.CONFIGS);
            Archive archive = index.getArchive(ConfigType.VARBIT.getId());

            byte[] decompressed = archive.decompress(storage.loadArchive(archive));
            FileData[] fileData = archive.getFileData();
            System.out.println("VARBIT archive: " + fileData.length + " existing entries.");

            for (int i = 0; i < COUNT; i++) {
                int varbitId = FIRST_NEW_VARBIT_ID + i;
                int varpId = FIRST_NEW_VARP_ID + i;
                for (FileData fd : fileData) {
                    if (fd.getId() == varbitId) {
                        throw new IllegalStateException("varbit " + varbitId + " already exists -- ABORTING.");
                    }
                }
            }

            List<byte[]> fileContents = SpliceItemOption.splitChunks(decompressed, fileData.length);

            List<Integer> newIds = new ArrayList<>();
            List<byte[]> newRaws = new ArrayList<>();
            VarbitLoader loader = new VarbitLoader();

            for (int i = 0; i < COUNT; i++) {
                int varbitId = FIRST_NEW_VARBIT_ID + i;
                int varpId = FIRST_NEW_VARP_ID + i;

                OutputStream os = new OutputStream(8);
                os.writeByte(1);
                os.writeShort(varpId);
                os.writeByte(0);  // leastSignificantBit
                os.writeByte(31); // mostSignificantBit -- whole-varp, no packing
                os.writeByte(0);  // terminator
                byte[] raw = os.flip();

                VarbitDefinition def = loader.load(varbitId, raw);
                if (def.getIndex() != varpId || def.getLeastSignificantBit() != 0
                        || def.getMostSignificantBit() != 31) {
                    throw new IllegalStateException("round-trip verification FAILED for varbit " + varbitId
                            + " (got varpId=" + def.getIndex() + " lsb=" + def.getLeastSignificantBit()
                            + " msb=" + def.getMostSignificantBit() + ") -- ABORTING, nothing written.");
                }
                System.out.println("varbit " + varbitId + " -> varp " + varpId + " bits[0-31], verified OK ("
                        + raw.length + " bytes)");

                newIds.add(varbitId);
                newRaws.add(raw);
            }

            System.out.println("Prepared " + newIds.size() + " new varbit entries.");
            if (!apply) {
                System.out.println("DRY RUN -- not writing. Re-run with 'apply' to persist.");
                return;
            }

            // new ids (17796+) are all larger than every existing id (max 17795), so a plain
            // append at the end keeps IndexData.writeIndexData()'s required non-negative,
            // strictly-increasing delta-encoded id sequence intact -- no merge-insert needed.
            List<FileData> mergedFileData = new ArrayList<>(fileData.length + newIds.size());
            List<byte[]> mergedContents = new ArrayList<>(fileData.length + newIds.size());
            mergedFileData.addAll(java.util.Arrays.asList(fileData));
            mergedContents.addAll(fileContents);
            for (int i = 0; i < newIds.size(); i++) {
                FileData nfd = new FileData();
                nfd.setId(newIds.get(i));
                nfd.setNameHash(-1);
                mergedFileData.add(nfd);
                mergedContents.add(newRaws.get(i));
            }

            archive.setFileData(mergedFileData.toArray(new FileData[0]));

            byte[] newDecompressed = SpliceItemOption.joinChunks(mergedContents);
            Container container = new Container(archive.getCompression(), -1);
            container.compress(newDecompressed, null);

            storage.store(index.getId(), archive.getArchiveId(), container.data);
            archive.setCrc(container.crc);
            archive.setRevision(archive.getRevision() + 1);
            archive.setCompressedSize(container.data.length);
            archive.setDecompressedSize(newDecompressed.length);

            IndexData indexData = index.toIndexData();
            byte[] rawIndex = indexData.writeIndexData();
            Container idxContainer = new Container(index.getCompression(), -1);
            idxContainer.compress(rawIndex, null);
            storage.store(255, index.getId(), idxContainer.data);
            index.setCrc(idxContainer.crc);

            System.out.println("APPLY complete. VARBIT archive revision now " + archive.getRevision()
                    + ", " + newIds.size() + " new varbits added (ids " + FIRST_NEW_VARBIT_ID + "-"
                    + (FIRST_NEW_VARBIT_ID + COUNT - 1) + ").");
        }
    }
}

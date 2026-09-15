import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.loaders.ModelLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Container;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.IndexData;

import java.io.File;
import java.util.List;

// Fixes the 2026-09-15 "new customs invisible" bug: the 23 new custom item models were inserted
// as raw ripped OldFormat bytes (see InsertRawModel.java), which RuneLite's offline ModelLoader
// decodes fine but the live client's own renderer apparently cannot -- proven by live JS5 logging
// showing the client receives the exact bytes and still renders nothing (see memory
// project_newcustoms_invisible_models_20260915). This tool re-encodes each existing model archive
// in place through ModelType2Encoder (already used successfully for a prior asset batch) into the
// modern Type2 binary format, verifying a full field round-trip before writing anything.
//
// Usage: verify|apply <cachePath> <modelId1,modelId2,...>
public class ReencodeModelInPlace {
    public static void main(String[] args) throws Exception {
        String mode = args[0];
        String cachePath = args[1];
        List<Integer> modelIds = new java.util.ArrayList<>();
        for (String s : args[2].split(",")) modelIds.add(Integer.parseInt(s.trim()));

        if (!mode.equals("verify") && !mode.equals("apply")) {
            throw new IllegalArgumentException("mode must be 'verify' or 'apply', got: " + mode);
        }

        ModelLoader loader = new ModelLoader();
        boolean anyWritten = false;

        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Index models = store.getIndex(IndexType.MODELS);

            for (int modelId : modelIds) {
                Archive archive = models.getArchive(modelId);
                if (archive == null) {
                    System.out.println(modelId + ": ARCHIVE NULL -- skipping");
                    continue;
                }

                byte[] raw = archive.decompress(storage.loadArchive(archive));
                ModelDefinition orig;
                try {
                    orig = loader.load(modelId, raw.clone());
                } catch (Throwable t) {
                    System.out.println(modelId + ": DECODE EXCEPTION on existing bytes -- skipping: " + t);
                    continue;
                }

                byte[] reencoded;
                try {
                    reencoded = ModelType2Encoder.encode(orig);
                } catch (Throwable t) {
                    System.out.println(modelId + ": ENCODE EXCEPTION -- skipping: " + t);
                    t.printStackTrace();
                    continue;
                }

                ModelDefinition roundTrip;
                try {
                    roundTrip = loader.load(modelId, reencoded.clone());
                } catch (Throwable t) {
                    System.out.println(modelId + ": RE-DECODE EXCEPTION -- ABORTING this id: " + t);
                    t.printStackTrace();
                    continue;
                }

                List<String> diffs = RoundTripTestType2.compare(orig, roundTrip);
                if (!diffs.isEmpty()) {
                    System.out.println(modelId + ": FAIL - " + diffs.size() + " mismatches, NOT writing");
                    for (String d : diffs) System.out.println("    " + d);
                    continue;
                }

                System.out.println(modelId + ": round-trip OK (" + raw.length + " -> " + reencoded.length + " bytes)"
                        + (mode.equals("verify") ? " [verify only, not writing]" : ""));

                if (mode.equals("apply")) {
                    Container container = new Container(0, -1); // uncompressed, same convention as InsertRawModel
                    container.compress(reencoded, null);

                    storage.store(models.getId(), modelId, container.data);
                    archive.setCrc(container.crc);
                    archive.setRevision(archive.getRevision() + 1);
                    archive.setCompression(0);
                    archive.setCompressedSize(container.data.length);
                    archive.setDecompressedSize(reencoded.length);
                    anyWritten = true;
                }
            }

            if (mode.equals("apply") && anyWritten) {
                IndexData indexData = models.toIndexData();
                byte[] rawIndex = indexData.writeIndexData();
                Container idxContainer = new Container(models.getCompression(), -1);
                idxContainer.compress(rawIndex, null);
                storage.store(255, models.getId(), idxContainer.data);
                models.setCrc(idxContainer.crc);
                System.out.println("APPLY complete. idx7 reference table (idx255 entry) updated.");
            } else if (mode.equals("apply")) {
                System.out.println("APPLY mode but nothing passed verification -- nothing written.");
            }
        }
    }
}

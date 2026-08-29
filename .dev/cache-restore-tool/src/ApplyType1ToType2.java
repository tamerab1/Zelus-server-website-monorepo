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

// Re-encodes the given Type1-format model archives to Type2 in place (same model ids), using
// ModelType2Encoder. Verifies each model round-trips cleanly (decode -> encode -> decode, exact
// field match except the always-expected numTextureFaces/faceColors-on-textured-faces deltas --
// see RoundTripTestType2) BEFORE writing anything. Writes are all-or-nothing per run: if any model
// fails verification, nothing is written.
//
// Usage: <verify|apply> <cachePath> <modelId1,modelId2,...>
public class ApplyType1ToType2 {
    public static void main(String[] args) throws Exception {
        String mode = args[0];
        String cachePath = args[1];
        int[] modelIds = java.util.Arrays.stream(args[2].split(","))
                .mapToInt(s -> Integer.parseInt(s.trim())).toArray();
        if (args.length > 3 && args[3].equals("notexture")) {
            ModelType2Encoder.DISABLE_TEXTURING = true;
            System.out.println("DIAGNOSTIC MODE: texturing disabled, faces will render as plain colors.");
        }

        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Index modelsIndex = store.getIndex(IndexType.MODELS);

            java.util.Map<Integer, byte[]> newBytesByModelId = new java.util.LinkedHashMap<>();
            boolean anyFailed = false;

            for (int modelId : modelIds) {
                Archive archive = modelsIndex.getArchive(modelId);
                if (archive == null) {
                    System.out.println(modelId + ": ARCHIVE NULL - skipping");
                    anyFailed = true;
                    continue;
                }
                byte[] raw = archive.decompress(storage.loadArchive(archive));
                byte trailerA = raw[raw.length - 1];
                byte trailerB = raw[raw.length - 2];
                if (trailerA == -2 && trailerB == -1) {
                    System.out.println(modelId + ": already Type2 - skipping");
                    continue;
                }

                ModelDefinition orig = new ModelLoader().load(modelId, raw.clone());
                byte[] reencoded;
                try {
                    reencoded = ModelType2Encoder.encode(orig);
                } catch (Throwable t) {
                    System.out.println(modelId + ": ENCODE FAILED - " + t);
                    anyFailed = true;
                    continue;
                }

                ModelDefinition roundTrip;
                try {
                    roundTrip = new ModelLoader().load(modelId, reencoded.clone());
                } catch (Throwable t) {
                    System.out.println(modelId + ": RE-DECODE FAILED - " + t);
                    anyFailed = true;
                    continue;
                }

                java.util.List<String> diffs = RoundTripTestType2.compare(orig, roundTrip);
                if (!diffs.isEmpty()) {
                    System.out.println(modelId + ": VERIFY FAILED - " + diffs.size() + " mismatches");
                    for (String d : diffs) System.out.println("    " + d);
                    anyFailed = true;
                    continue;
                }

                System.out.println(modelId + ": verified OK (" + raw.length + " -> " + reencoded.length
                        + " bytes, Type1 -> Type2)");
                newBytesByModelId.put(modelId, reencoded);
            }

            if (mode.equals("verify")) {
                System.out.println(anyFailed ? "VERIFY: at least one model failed." : "VERIFY: all models OK.");
                return;
            }

            if (!mode.equals("apply")) {
                throw new IllegalArgumentException("mode must be 'verify' or 'apply', got: " + mode);
            }

            if (anyFailed) {
                System.out.println("ABORTING apply: at least one model failed verification above. Nothing written.");
                return;
            }

            for (var entry : newBytesByModelId.entrySet()) {
                int modelId = entry.getKey();
                byte[] newDecompressed = entry.getValue();
                Archive archive = modelsIndex.getArchive(modelId);

                Container container = new Container(archive.getCompression(), -1);
                container.compress(newDecompressed, null);

                // Container.compress() never sets container.crc (that field is only populated by
                // Container.decompress(), reading an EXISTING container) -- it silently stays 0.
                // Using it here would write a wrong CRC into the reference table, causing every
                // consumer that validates archive.getCrc() against the real content (the JS5
                // client download path, Archive.decompress()) to reject this archive as corrupt.
                // Compute it ourselves the same way decompress() does: CRC32 over the full
                // container bytes (revision is -1 here, so there's no trailing revision suffix to
                // exclude).
                java.util.zip.CRC32 crc32 = new java.util.zip.CRC32();
                crc32.update(container.data);
                int realCrc = (int) crc32.getValue();

                storage.store(modelsIndex.getId(), modelId, container.data);
                archive.setCrc(realCrc);
                archive.setRevision(archive.getRevision() + 1);
                archive.setCompressedSize(container.data.length);
                archive.setDecompressedSize(newDecompressed.length);

                System.out.println(modelId + ": WRITTEN (revision " + archive.getRevision() + ")");
            }

            IndexData indexData = modelsIndex.toIndexData();
            byte[] rawIndex = indexData.writeIndexData();
            Container idxContainer = new Container(modelsIndex.getCompression(), -1);
            idxContainer.compress(rawIndex, null);
            storage.store(255, modelsIndex.getId(), idxContainer.data);
            java.util.zip.CRC32 idxCrc32 = new java.util.zip.CRC32();
            idxCrc32.update(idxContainer.data);
            modelsIndex.setCrc((int) idxCrc32.getValue());

            System.out.println("APPLY complete. " + newBytesByModelId.size() + " model archive(s) converted to Type2.");
        }
    }
}

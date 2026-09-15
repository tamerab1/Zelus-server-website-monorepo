import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.loaders.ModelLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Container;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;
import net.runelite.cache.index.IndexData;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

// The boss teleport interface's review-window preview renders a RAW model id directly via
// clientscript 10623 (IF_SETMODEL-style) -- it does NOT go through an NPC definition's own
// recolorToFind/recolorToReplace overlay (that's an NPC-level opcode-40/41 override, applied only
// when the client renders an actual NPC instance in the world, not a bare model id). Confirmed via
// Cindermaw: its teleport entry already correctly references its own model id (36160, matching
// its live npc def exactly) -- the preview just shows that model's UNRECOLORED base face colours,
// because the recolor genuinely never touches the model's own bytes anywhere in this pipeline.
//
// Fix: bake the same find/replace pairs directly into a NEW copy of the model's faceColors array
// (an exact-match short remap, not a rehue) and insert it as a brand-new model archive (fd.id=0,
// per the fd-id-zero fix -- see reference_model_archive_fileid_zero). Point the teleport entry's
// modelId at this new baked id instead of the raw one.
//
// Usage: verify|apply <cachePath> <sourceModelId> <newModelId> <f1,r1,f2,r2,...>
public class BakeModelRecolor {
    public static void main(String[] args) throws Exception {
        String mode = args[0];
        String cachePath = args[1];
        int sourceModelId = Integer.parseInt(args[2]);
        int newModelId = Integer.parseInt(args[3]);
        String[] pairsStr = args[4].split(",");
        Map<Short, Short> remap = new HashMap<>();
        for (int i = 0; i < pairsStr.length; i += 2) {
            short find = (short) Integer.parseInt(pairsStr[i].trim());
            short replace = (short) Integer.parseInt(pairsStr[i + 1].trim());
            remap.put(find, replace);
        }

        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Index models = store.getIndex(IndexType.MODELS);

            if (models.getArchive(newModelId) != null) {
                throw new IllegalStateException("model id " + newModelId + " already exists -- refusing to overwrite.");
            }
            Archive sourceArchive = models.getArchive(sourceModelId);
            if (sourceArchive == null) {
                throw new IllegalStateException("source model " + sourceModelId + " not found");
            }

            byte[] raw = sourceArchive.decompress(storage.loadArchive(sourceArchive));
            ModelLoader loader = new ModelLoader();
            ModelDefinition before = loader.load(sourceModelId, raw.clone());

            if (before.faceColors == null) {
                throw new IllegalStateException("source model has no faceColors -- ABORTING");
            }
            short[] newColors = before.faceColors.clone();
            int changed = 0;
            for (int i = 0; i < newColors.length; i++) {
                Short r = remap.get(newColors[i]);
                if (r != null) {
                    newColors[i] = r;
                    changed++;
                }
            }
            System.out.println("faceColors touched: " + changed + " / " + newColors.length);

            // Rebuild the raw bytes with the new faceColors baked in, keeping everything else
            // byte-identical -- easiest safe way is to re-encode via ModelType2Encoder (already
            // proven round-trip-safe this session) with just faceColors swapped on the decoded def.
            before.faceColors = newColors;
            byte[] reencoded = ModelType2Encoder.encode(before);

            ModelDefinition after = loader.load(newModelId, reencoded.clone());
            if (after.vertexCount != before.vertexCount || after.faceCount != before.faceCount) {
                throw new IllegalStateException("vertex/face count mismatch after re-encode -- ABORTING");
            }
            int mismatches = 0;
            for (int i = 0; i < newColors.length; i++) {
                if (after.faceColors[i] != newColors[i]) mismatches++;
            }
            if (mismatches > 0) {
                throw new IllegalStateException(mismatches + " faceColors did not round-trip correctly -- ABORTING");
            }
            System.out.println("Round-trip verified: " + after.vertexCount + " verts, " + after.faceCount + " faces, faceColors match.");

            if (mode.equals("verify")) {
                System.out.println("VERIFY mode -- not writing. Re-run with 'apply' to persist.");
                return;
            }
            if (!mode.equals("apply")) throw new IllegalArgumentException("mode must be verify or apply");

            Archive newArchive = models.addArchive(newModelId);
            newArchive.setNameHash(-1);
            newArchive.setCompression(0);
            newArchive.setRevision(1);

            Container container = new Container(0, -1);
            container.compress(reencoded, null);

            storage.store(models.getId(), newModelId, container.data);
            newArchive.setCrc(container.crc);
            newArchive.setCompressedSize(container.data.length);
            newArchive.setDecompressedSize(reencoded.length);

            FileData fd = new FileData();
            fd.setId(0); // see reference_model_archive_fileid_zero -- must be 0, not newModelId
            newArchive.setFileData(new FileData[]{fd});

            IndexData indexData = models.toIndexData();
            byte[] rawIndex = indexData.writeIndexData();
            Container idxContainer = new Container(models.getCompression(), -1);
            idxContainer.compress(rawIndex, null);
            storage.store(255, models.getId(), idxContainer.data);
            models.setCrc(idxContainer.crc);

            System.out.println("APPLY complete. New baked-recolor model id " + newModelId + " written.");
        }
    }
}

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
import java.util.zip.CRC32;

// Merges several models (all authored/rendered together at the same local origin -- the standard
// composite-NPC convention where a single NPC's opcode-1 "models" list is several parts stacked
// at once, e.g. Wagchin = 6 separate clothing/body-piece meshes) into ONE new model archive, so it
// can be used as a single item's inventoryModel (which only accepts one model id, unlike an NPC
// def's model LIST). None of the source models here use texturing (verified via DumpModelInfo --
// texturedFaces=0 on all of them), so texture fields are intentionally left unmerged/null; and
// vertex-group animation data is dropped since a static item icon render never animates.
//
// Usage: verify|apply <cachePath> <newModelId> <sourceId1,sourceId2,...>
public class MergeModels {
    public static void main(String[] args) throws Exception {
        String mode = args[0];
        String cachePath = args[1];
        int newModelId = Integer.parseInt(args[2]);
        String[] sourceIdStrs = args[3].split(",");

        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Index models = store.getIndex(IndexType.MODELS);

            if (models.getArchive(newModelId) != null) {
                throw new IllegalStateException("model id " + newModelId + " already exists -- refusing to overwrite.");
            }

            ModelLoader loader = new ModelLoader();
            ModelDefinition[] parts = new ModelDefinition[sourceIdStrs.length];
            int totalVertices = 0, totalFaces = 0;
            for (int i = 0; i < sourceIdStrs.length; i++) {
                int id = Integer.parseInt(sourceIdStrs[i].trim());
                Archive a = models.getArchive(id);
                if (a == null) throw new IllegalStateException("source model " + id + " not found");
                byte[] data = a.decompress(storage.loadArchive(a));
                ModelDefinition md = loader.load(id, data);
                parts[i] = md;
                totalVertices += md.vertexCount;
                totalFaces += md.faceCount;
                System.out.println("part " + id + ": vertices=" + md.vertexCount + " faces=" + md.faceCount);
            }

            ModelDefinition merged = new ModelDefinition();
            merged.id = newModelId;
            merged.vertexCount = totalVertices;
            merged.vertexX = new int[totalVertices];
            merged.vertexY = new int[totalVertices];
            merged.vertexZ = new int[totalVertices];
            merged.faceCount = totalFaces;
            merged.faceIndices1 = new int[totalFaces];
            merged.faceIndices2 = new int[totalFaces];
            merged.faceIndices3 = new int[totalFaces];
            merged.faceColors = new short[totalFaces];

            boolean anyTransparency = false, anyPriority = false, anyRenderType = false;
            for (ModelDefinition md : parts) {
                if (md.faceTransparencies != null) anyTransparency = true;
                if (md.faceRenderPriorities != null) anyPriority = true;
                if (md.faceRenderTypes != null) anyRenderType = true;
            }
            merged.faceTransparencies = anyTransparency ? new byte[totalFaces] : null;
            merged.faceRenderPriorities = anyPriority ? new byte[totalFaces] : null;
            merged.faceRenderTypes = anyRenderType ? new byte[totalFaces] : null;

            int vOffset = 0, fOffset = 0;
            for (ModelDefinition md : parts) {
                System.arraycopy(md.vertexX, 0, merged.vertexX, vOffset, md.vertexCount);
                System.arraycopy(md.vertexY, 0, merged.vertexY, vOffset, md.vertexCount);
                System.arraycopy(md.vertexZ, 0, merged.vertexZ, vOffset, md.vertexCount);

                for (int f = 0; f < md.faceCount; f++) {
                    merged.faceIndices1[fOffset + f] = md.faceIndices1[f] + vOffset;
                    merged.faceIndices2[fOffset + f] = md.faceIndices2[f] + vOffset;
                    merged.faceIndices3[fOffset + f] = md.faceIndices3[f] + vOffset;
                    merged.faceColors[fOffset + f] = md.faceColors[f];
                    if (anyTransparency) {
                        merged.faceTransparencies[fOffset + f] = md.faceTransparencies != null ? md.faceTransparencies[f] : 0;
                    }
                    if (anyPriority) {
                        merged.faceRenderPriorities[fOffset + f] = md.faceRenderPriorities != null ? md.faceRenderPriorities[f] : md.priority;
                    }
                    if (anyRenderType) {
                        merged.faceRenderTypes[fOffset + f] = md.faceRenderTypes != null ? md.faceRenderTypes[f] : 0;
                    }
                }

                vOffset += md.vertexCount;
                fOffset += md.faceCount;
            }

            byte[] encoded = ModelType3Encoder.encode(merged);

            // Round-trip check before writing anything.
            ModelDefinition reloaded = loader.load(newModelId, encoded);
            System.out.println("Merged model: vertices=" + merged.vertexCount + " faces=" + merged.faceCount);
            System.out.println("Round-trip reload: vertices=" + reloaded.vertexCount + " faces=" + reloaded.faceCount);
            if (reloaded.vertexCount != merged.vertexCount || reloaded.faceCount != merged.faceCount) {
                throw new IllegalStateException("round-trip mismatch -- ABORTING, nothing written");
            }

            if (mode.equals("verify")) {
                System.out.println("VERIFY mode -- not writing. Re-run with 'apply' to persist.");
                return;
            }
            if (!mode.equals("apply")) {
                throw new IllegalArgumentException("mode must be 'verify' or 'apply', got: " + mode);
            }

            Archive newArchive = models.addArchive(newModelId);
            newArchive.setNameHash(-1);
            newArchive.setCompression(0); // uncompressed, simplest valid encoding
            newArchive.setRevision(1);

            Container container = new Container(0, -1);
            container.compress(encoded, null);

            CRC32 crc32 = new CRC32();
            crc32.update(container.data);
            int realCrc = (int) crc32.getValue();

            storage.store(models.getId(), newModelId, container.data);
            newArchive.setCrc(realCrc);
            newArchive.setCompressedSize(container.data.length);
            newArchive.setDecompressedSize(encoded.length);

            FileData fd = new FileData();
            fd.setId(newModelId);
            newArchive.setFileData(new FileData[]{fd});

            IndexData indexData = models.toIndexData();
            byte[] rawIndex = indexData.writeIndexData();
            Container idxContainer = new Container(models.getCompression(), -1);
            idxContainer.compress(rawIndex, null);
            storage.store(255, models.getId(), idxContainer.data);
            CRC32 idxCrc32 = new CRC32();
            idxCrc32.update(idxContainer.data);
            models.setCrc((int) idxCrc32.getValue());

            System.out.println("APPLY complete. New merged model id " + newModelId + " written.");
        }
    }
}

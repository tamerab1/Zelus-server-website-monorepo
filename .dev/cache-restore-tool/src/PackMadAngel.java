import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Container;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;
import net.runelite.cache.index.IndexData;
import net.runelite.cache.io.InputStream;
import net.runelite.cache.io.OutputStream;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;

// Read source cache, write Zelus cache. Every id was individually collision-checked (see
// CollisionCheck / CollisionCheck2) before this was written. Two safety layers:
//   1. mode "verify": read-only, re-checks every target id is still free right now, writes nothing.
//   2. mode "apply": re-checks again (never trusts an earlier run), aborts the WHOLE operation if
//      anything has changed, otherwise writes everything in one pass.
// All content is copied byte-for-byte from source (no re-encoding) except spotanim 4013, whose
// embedded model reference (48289, occupied in Zelus) is patched to 61848 post-remap.
public class PackMadAngel {

    // ---- id plan -------------------------------------------------------------------------

    // Models: sourceId -> targetId. Everything 1:1 except 48289 (occupied) -> 61848 (free).
    static final int[][] MODELS = {
            {61845, 61845}, {61847, 61847}, {61786, 61786},
            {61842, 61842}, {60639, 60639}, {48289, 61848}, {60632, 60632},
    };

    static final int SKELETON_ID = 2675; // shared by every frame archive below, 1:1

    static final int[] FRAME_ARCHIVES = {
            14542, 14543, 14544, 14545, 14546, 14549, 14550, 14551, 14552,
            14553, 14554, 14555, 14556, 14557, 14560,
    };

    static final int[] NPC_IDS = {16305, 16306, 16307, 16308}; // all free, 1:1

    // Sequences: sourceId -> targetId. 4 remapped (collided with pre-existing unrelated content),
    // the rest 1:1 (frameIDs reference frame-archive numbers, which are unchanged either way, so
    // remapping a sequence's OWN id needs no byte changes to its content).
    static final int[][] SEQS = {
            {3321, 14449}, {4589, 14450}, {4590, 14451}, {8543, 14452},
            {14443, 14443}, {14448, 14448},
            {14429, 14429}, {14430, 14430}, {14431, 14431}, {14432, 14432},
            {14433, 14433}, {14434, 14434}, {14435, 14435}, {14436, 14436},
            {14437, 14437}, {14438, 14438}, {14439, 14439}, {14440, 14440},
            {14441, 14441}, {14442, 14442},
    };

    static final int[] SPOTANIM_IDS = {4010, 4011, 4012, 4013, 4014, 4015, 4016, 4017}; // all free, 1:1
    static final int SPOTANIM_MODEL_PATCH_ID = 4013;
    static final int SPOTANIM_MODEL_OLD = 48289;
    static final int SPOTANIM_MODEL_NEW = 61848;

    public static void main(String[] args) throws Exception {
        String mode = args[0]; // verify | apply
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

            boolean ok = true;
            ok &= verifyModels(dst);
            ok &= verifySingle(dst, IndexType.SKELETONS, new int[]{SKELETON_ID});
            ok &= verifySingle(dst, IndexType.ANIMATIONS, FRAME_ARCHIVES);
            ok &= verifyConfig(dst, ConfigType.NPC.getId(), NPC_IDS);
            ok &= verifyConfigPairs(dst, ConfigType.SEQUENCE.getId(), SEQS);
            ok &= verifySingleTarget(dst, ConfigType.SPOTANIM.getId(), SPOTANIM_IDS);

            System.out.println(ok ? "VERIFY: all target ids still free." : "VERIFY: FAILED -- see above.");
            if (!apply) {
                System.out.println("(dry run -- pass 'apply' to write)");
                return;
            }
            if (!ok) {
                System.out.println("ABORTING apply: verify failed above. Nothing written.");
                return;
            }

            System.out.println("=== APPLY: models ===");
            applyModels(srcStorage, src.getIndex(IndexType.MODELS), dstStorage, dst.getIndex(IndexType.MODELS));

            System.out.println("=== APPLY: skeleton ===");
            applySingle(srcStorage, src.getIndex(IndexType.SKELETONS), dstStorage, dst.getIndex(IndexType.SKELETONS),
                    new int[][]{{SKELETON_ID, SKELETON_ID}});

            System.out.println("=== APPLY: frame archives ===");
            int[][] framePairs = new int[FRAME_ARCHIVES.length][2];
            for (int i = 0; i < FRAME_ARCHIVES.length; i++) framePairs[i] = new int[]{FRAME_ARCHIVES[i], FRAME_ARCHIVES[i]};
            applySingle(srcStorage, src.getIndex(IndexType.ANIMATIONS), dstStorage, dst.getIndex(IndexType.ANIMATIONS),
                    framePairs);

            System.out.println("=== APPLY: npc definitions ===");
            int[][] npcPairs = new int[NPC_IDS.length][2];
            for (int i = 0; i < NPC_IDS.length; i++) npcPairs[i] = new int[]{NPC_IDS[i], NPC_IDS[i]};
            applyConfig(srcStorage, src.getIndex(IndexType.CONFIGS), dstStorage, dst.getIndex(IndexType.CONFIGS),
                    ConfigType.NPC.getId(), npcPairs, null);

            System.out.println("=== APPLY: sequences (4 remapped, 16 unchanged) ===");
            applyConfig(srcStorage, src.getIndex(IndexType.CONFIGS), dstStorage, dst.getIndex(IndexType.CONFIGS),
                    ConfigType.SEQUENCE.getId(), SEQS, null);

            System.out.println("=== APPLY: spotanims (4013 model-patched 48289->61848) ===");
            int[][] spotPairs = new int[SPOTANIM_IDS.length][2];
            for (int i = 0; i < SPOTANIM_IDS.length; i++) spotPairs[i] = new int[]{SPOTANIM_IDS[i], SPOTANIM_IDS[i]};
            applyConfig(srcStorage, src.getIndex(IndexType.CONFIGS), dstStorage, dst.getIndex(IndexType.CONFIGS),
                    ConfigType.SPOTANIM.getId(), spotPairs, PackMadAngel::maybePatchSpotanim);

            System.out.println("=== DONE. All writes complete. ===");
        }
    }

    interface RawPatcher {
        byte[] patch(int id, byte[] raw);
    }

    static byte[] maybePatchSpotanim(int id, byte[] raw) {
        if (id != SPOTANIM_MODEL_PATCH_ID) return raw;
        byte[] patched = patchSpotanimModel(raw, SPOTANIM_MODEL_OLD, SPOTANIM_MODEL_NEW);
        System.out.println("  spotanim " + id + ": patched model " + SPOTANIM_MODEL_OLD + " -> " + SPOTANIM_MODEL_NEW);
        return patched;
    }

    // Walks the spotanim opcode stream (same shape as DumpSpotAnimV2's decoder) and rewrites it
    // byte-for-byte, substituting the model value at whichever opcode carries it (1 = narrow
    // u-short, 3 = wide int) if and only if it equals oldModel. Every other opcode/byte is copied
    // through unchanged.
    static byte[] patchSpotanimModel(byte[] raw, int oldModel, int newModel) {
        InputStream is = new InputStream(raw);
        OutputStream os = new OutputStream(raw.length + 4);
        while (true) {
            int opcode = is.readUnsignedByte();
            os.writeByte(opcode);
            if (opcode == 0) break;
            if (opcode == 1) {
                int model = is.readUnsignedShort();
                os.writeShort(model == oldModel ? newModel : model);
            } else if (opcode == 3) {
                int model = is.readInt();
                os.writeInt(model == oldModel ? newModel : model);
            } else if (opcode == 2 || opcode == 4 || opcode == 5 || opcode == 6) {
                os.writeShort(is.readUnsignedShort());
            } else if (opcode == 7 || opcode == 8) {
                os.writeByte(is.readUnsignedByte());
            } else if (opcode == 9) {
                os.writeString(is.readString());
            } else if (opcode == 10) {
                // no payload
            } else if (opcode == 40 || opcode == 41) {
                int count = is.readUnsignedByte();
                os.writeByte(count);
                for (int i = 0; i < count; i++) {
                    os.writeShort(is.readUnsignedShort());
                    os.writeShort(is.readUnsignedShort());
                }
            } else {
                throw new RuntimeException("patchSpotanimModel: unexpected opcode " + opcode + " for id -- update this patcher");
            }
        }
        return os.flip();
    }

    // ---- verify helpers --------------------------------------------------------------------

    static boolean verifyModels(Store dst) throws Exception {
        Index models = dst.getIndex(IndexType.MODELS);
        boolean ok = true;
        for (int[] pair : MODELS) {
            boolean free = models.getArchive(pair[1]) == null;
            System.out.println("model target " + pair[1] + " (from source " + pair[0] + "): " + (free ? "free" : "OCCUPIED -- CONFLICT"));
            ok &= free;
        }
        return ok;
    }

    static boolean verifySingle(Store dst, IndexType type, int[] ids) throws Exception {
        Index index = dst.getIndex(type);
        boolean ok = true;
        for (int id : ids) {
            boolean free = index.getArchive(id) == null;
            System.out.println(type + " target " + id + ": " + (free ? "free" : "OCCUPIED -- CONFLICT"));
            ok &= free;
        }
        return ok;
    }

    static boolean verifySingleTarget(Store dst, int configTypeId, int[] ids) throws Exception {
        Index configs = dst.getIndex(IndexType.CONFIGS);
        Archive archive = configs.getArchive(configTypeId);
        FileData[] fileData = archive.getFileData();
        boolean ok = true;
        for (int id : ids) {
            boolean occupied = false;
            for (FileData fd : fileData) if (fd.getId() == id) occupied = true;
            System.out.println("config[" + configTypeId + "] target " + id + ": " + (occupied ? "OCCUPIED -- CONFLICT" : "free"));
            ok &= !occupied;
        }
        return ok;
    }

    static boolean verifyConfig(Store dst, int configTypeId, int[] ids) throws Exception {
        int[][] pairs = new int[ids.length][2];
        for (int i = 0; i < ids.length; i++) pairs[i] = new int[]{ids[i], ids[i]};
        return verifyConfigPairs(dst, configTypeId, pairs);
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
            System.out.println("config[" + configTypeId + "] target " + targetId + " (from source " + pair[0] + "): "
                    + (occupied ? "OCCUPIED -- CONFLICT" : "free"));
            ok &= !occupied;
        }
        return ok;
    }

    // ---- apply: flat single-archive-per-id indices (models, skeletons, frame archives) --------

    static void applyModels(Storage srcStorage, Index srcIndex, Storage dstStorage, Index dstIndex) throws Exception {
        int[][] pairs = MODELS;
        applySingle(srcStorage, srcIndex, dstStorage, dstIndex, pairs);
    }

    static void applySingle(Storage srcStorage, Index srcIndex, Storage dstStorage, Index dstIndex, int[][] pairs)
            throws Exception {
        for (int[] pair : pairs) {
            int srcId = pair[0], dstId = pair[1];
            Archive srcArchive = srcIndex.getArchive(srcId);
            if (srcArchive == null) {
                throw new IllegalStateException("source archive " + srcId + " not found in " + srcIndex.getId() + " -- aborting mid-write.");
            }
            if (dstIndex.getArchive(dstId) != null) {
                throw new IllegalStateException("target archive " + dstId + " now occupied in " + dstIndex.getId()
                        + " -- changed since verify, aborting mid-write.");
            }

            byte[] rawContainer = srcStorage.loadArchive(srcArchive); // on-disk bytes, still compressed
            Container c = Container.decompress(rawContainer, null);   // for its own self-check + to confirm readable

            Archive dstArchive = dstIndex.addArchive(dstId);
            dstArchive.setNameHash(-1);
            dstArchive.setCompression(srcArchive.getCompression());
            dstArchive.setRevision(1);
            dstArchive.setCrc(c.crc);
            dstArchive.setCompressedSize(rawContainer.length);
            dstArchive.setDecompressedSize(c.data.length);

            FileData[] srcFileData = srcArchive.getFileData();
            FileData[] dstFileData = new FileData[srcFileData.length];
            for (int i = 0; i < srcFileData.length; i++) {
                FileData fd = new FileData();
                fd.setId(srcFileData[i].getId());
                fd.setNameHash(-1);
                dstFileData[i] = fd;
            }
            dstArchive.setFileData(dstFileData);

            dstStorage.store(dstIndex.getId(), dstId, rawContainer);
            System.out.println("  " + srcId + " -> " + dstId + ": " + rawContainer.length
                    + " bytes written (" + srcFileData.length + " sub-file(s))");
        }

        writeIndexReferenceTable(dstStorage, dstIndex);
    }

    // ---- apply: multi-file config archives (npc / sequence / spotanim) -----------------------

    static void applyConfig(Storage srcStorage, Index srcIndex, Storage dstStorage, Index dstIndex,
                             int configTypeId, int[][] pairs, RawPatcher patcher) throws Exception {
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
            if (patcher != null) raw = patcher.patch(dstId, raw);
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

        // Same sorted merge-insert discipline as AddNewContent.addConfigEntries: IndexData's delta
        // encoding requires ids to stay in strictly-increasing positional order.
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

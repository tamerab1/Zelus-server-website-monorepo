import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;
import net.runelite.cache.io.InputStream;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

// Read-only. Decodes a SEQUENCE config record's real frame-archive references (opcode 1 = list of
// primary frame ids, opcode 2 = looping/reset frame ids) and cross-checks whether each referenced
// frame ARCHIVE actually exists in this cache's ANIMATIONS index. A sequence config entry existing
// (as checked earlier via CheckConfigExists SEQUENCE) does NOT prove its underlying frame data was
// ever imported -- this is the actual test the user asked for: if a sequence points to frame
// archives that are missing, the client renders a T-pose freeze for that animation, exactly
// matching "frozen like a rock" rather than a normal missing-walk-cycle symptom.
public class CheckSeqFrames {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        int seqId = Integer.parseInt(args[1]);

        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Archive archive = store.getIndex(IndexType.CONFIGS).getArchive(ConfigType.SEQUENCE.getId());
            byte[] decompressed = archive.decompress(storage.loadArchive(archive));
            FileData[] fileData = archive.getFileData();
            List<byte[]> contents = SpliceItemOption.splitChunks(decompressed, fileData.length);

            int slot = -1;
            for (int j = 0; j < fileData.length; j++) if (fileData[j].getId() == seqId) slot = j;
            if (slot == -1) { System.out.println("sequence " + seqId + " NOT FOUND"); return; }

            List<Integer> frameIds = decodeFrameIds(contents.get(slot));
            System.out.println("sequence " + seqId + " frame ids: " + frameIds);

            var animIndex = store.getIndex(IndexType.ANIMATIONS);
            List<Integer> missingArchives = new ArrayList<>();
            for (int frameId : frameIds) {
                int archiveId = frameId >> 16;
                boolean exists = animIndex.getArchive(archiveId) != null;
                if (!exists && !missingArchives.contains(archiveId)) missingArchives.add(archiveId);
            }
            if (missingArchives.isEmpty()) {
                System.out.println("all referenced frame ARCHIVES exist in this cache's ANIMATIONS index.");
            } else {
                System.out.println("MISSING frame archives (present in sequence, absent from ANIMATIONS index): " + missingArchives);
            }
        }
    }

    /** opcode 1 = primary frame list (2-byte archive<<16|frame per entry, preceded by count as
     * ushort); this is the field that actually drives what the client renders each animation tick. */
    static List<Integer> decodeFrameIds(byte[] raw) {
        InputStream in = new InputStream(raw);
        List<Integer> frameIds = new ArrayList<>();
        while (true) {
            int code = in.readUnsignedByte();
            if (code == 0) break;
            if (code == 1) {
                int count = in.readUnsignedShort();
                int[] lengths = new int[count];
                for (int i = 0; i < count; i++) lengths[i] = in.readUnsignedShort();
                for (int i = 0; i < count; i++) {
                    int frameArchive = in.readUnsignedShort();
                    int frameId = in.readUnsignedShort();
                    frameIds.add((frameArchive << 16) | frameId);
                }
            } else {
                skip(in, code);
            }
        }
        return frameIds;
    }

    static void skip(InputStream in, int code) {
        switch (code) {
            case 2: { // frame ids IDK list, ushort count then ushorts
                int count = in.readUnsignedShort();
                for (int i = 0; i < count; i++) in.readUnsignedShort();
                break;
            }
            case 3: break; // stop on loop end (no payload historically, but be safe)
            case 5: in.readByte(); break;
            case 6: in.readUnsignedShort(); break;
            case 7: in.readUnsignedShort(); break;
            case 8: in.readByte(); break;
            case 9: in.readByte(); break;
            default:
                throw new RuntimeException("CheckSeqFrames: unrecognized seq opcode " + code + " -- extend this tool's skip table");
        }
    }
}

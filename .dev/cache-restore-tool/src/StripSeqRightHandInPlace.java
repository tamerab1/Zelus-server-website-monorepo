import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.SequenceDefinition;
import net.runelite.cache.definitions.loaders.SequenceLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.ArchiveFiles;
import net.runelite.cache.fs.Container;
import net.runelite.cache.fs.FSFile;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.io.InputStream;

import java.io.File;

// Removes opcode 7 (rightHandItem) entirely from a sequence -- the DEFAULT (opcode absent) value is
// -1, meaning "no override, render whatever's actually equipped normally". Locates opcode 7's exact
// byte span using ONLY the already-validated opcodes 1-6 skip logic (same as
// CloneSeqWithHandItem.skipKnown), then splices those 3 bytes out and copies EVERYTHING else
// (before and after) through completely unexamined -- no need to understand opcodes 8+ at all,
// whatever they are.
// Usage: <cachePath> <seqId>
public class StripSeqRightHandInPlace {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        int seqId = Integer.parseInt(args[1]);

        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Index configs = store.getIndex(IndexType.CONFIGS);
            Archive archive = configs.getArchive(ConfigType.SEQUENCE.getId());
            ArchiveFiles files = archive.getFiles(storage.loadArchive(archive));

            FSFile target = files.findFile(seqId);
            if (target == null) throw new IllegalStateException("seq " + seqId + " not found");

            byte[] before = target.getContents();
            byte[] after = stripOpcode7(before);
            target.setContents(after);

            SequenceLoader loader = new SequenceLoader();
            SequenceDefinition def = loader.load(seqId, after);
            System.out.println("seq " + seqId + " AFTER: rightHandItem=" + def.rightHandItem
                    + " leftHandItem=" + def.leftHandItem + " frames=" + (def.frameIDs == null ? 0 : def.frameIDs.length)
                    + " (" + before.length + " -> " + after.length + " bytes)");
            if (def.rightHandItem != -1) throw new IllegalStateException("strip failed -- ABORTING write");

            byte[] newDecompressed = files.saveContents();
            Container container = new Container(archive.getCompression(), -1);
            container.compress(newDecompressed, null);
            storage.store(configs.getId(), archive.getArchiveId(), container.data);
            archive.setCrc(container.crc);
            archive.setRevision(archive.getRevision() + 1);
            archive.setCompressedSize(container.data.length);
            archive.setDecompressedSize(newDecompressed.length);

            PackMadAngel.writeIndexReferenceTable(storage, configs);
            System.out.println("APPLY complete. config[" + ConfigType.SEQUENCE.getId() + "] revision now "
                    + archive.getRevision());
        }
    }

    // Only understands opcodes 1-6 (same validated table as CloneSeqWithHandItem.skipKnown) --
    // enough to reliably locate opcode 7's span. Everything before and after is byte-copied as-is.
    static byte[] stripOpcode7(byte[] raw) {
        InputStream is = new InputStream(raw);
        while (true) {
            int opStart = is.getOffset();
            int opcode = is.readUnsignedByte();
            if (opcode == 7) {
                int payloadStart = is.getOffset();
                is.readUnsignedShort();
                int opEnd = is.getOffset();
                byte[] out = new byte[raw.length - (opEnd - opStart)];
                System.arraycopy(raw, 0, out, 0, opStart);
                System.arraycopy(raw, opEnd, out, opStart, raw.length - opEnd);
                return out;
            }
            if (opcode == 0) throw new IllegalStateException("no opcode 7 found before terminator");
            CloneSeqWithHandItem.skipKnown(is, opcode);
        }
    }
}

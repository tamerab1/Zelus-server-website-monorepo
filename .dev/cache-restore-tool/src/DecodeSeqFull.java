import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.ArchiveFiles;
import net.runelite.cache.fs.FSFile;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.io.InputStream;

import java.io.File;

// Read-only. Full sequence decoder (correct opcode table, cross-referenced against
// RS-Realm-Server-Package's SeqTypeDecoder.kt) -- reports frame count, priority, maxLoops, and
// walkMerge/duplicateBehaviour, to check for anything that would visually interrupt or override
// her attack animations mid-play.
public class DecodeSeqFull {
    static class Def {
        int id;
        int frameCount = 0;
        int loops = -1;
        int priority = -1;
        int maxLoops = -1;
        int preanimMove = -1;
        int postanimMove = -1;
        int duplicateBehaviour = -1;
    }

    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Index configs = store.getIndex(IndexType.CONFIGS);
            Archive archive = configs.getArchive(ConfigType.SEQUENCE.getId());
            ArchiveFiles files = archive.getFiles(storage.loadArchive(archive));

            for (int i = 1; i < args.length; i++) {
                int id = Integer.parseInt(args[i]);
                FSFile file = files.findFile(id);
                if (file == null) { System.out.println(id + " NOT FOUND"); continue; }
                Def def = decode(id, file.getContents());
                System.out.println(id + ": frames=" + def.frameCount + " loops=" + def.loops
                        + " priority=" + def.priority + " maxLoops=" + def.maxLoops
                        + " preanimMove=" + def.preanimMove + " postanimMove=" + def.postanimMove
                        + " duplicateBehaviour=" + def.duplicateBehaviour);
            }
        }
    }

    static Def decode(int id, byte[] b) {
        Def def = new Def();
        def.id = id;
        InputStream is = new InputStream(b);
        while (true) {
            int opcode = is.readUnsignedByte();
            if (opcode == 0) break;
            if (opcode == 1) {
                int count = is.readUnsignedShort();
                def.frameCount = count;
                for (int i = 0; i < count; i++) is.readUnsignedShort();
                for (int i = 0; i < count; i++) is.readUnsignedShort();
                for (int i = 0; i < count; i++) is.readUnsignedShort();
            } else if (opcode == 2) {
                def.loops = is.readUnsignedShort();
            } else if (opcode == 3) {
                int count = is.readUnsignedByte();
                for (int i = 0; i < count; i++) is.readUnsignedByte();
            } else if (opcode == 4) {
                // stretches, no payload
            } else if (opcode == 5) {
                def.priority = is.readUnsignedByte();
            } else if (opcode == 6 || opcode == 7) {
                is.readUnsignedShort();
            } else if (opcode == 8) {
                def.maxLoops = is.readUnsignedByte();
            } else if (opcode == 9) {
                def.preanimMove = is.readUnsignedByte();
            } else if (opcode == 10) {
                def.postanimMove = is.readUnsignedByte();
            } else if (opcode == 11) {
                def.duplicateBehaviour = is.readUnsignedByte();
            } else if (opcode == 12) {
                int count = is.readUnsignedByte();
                for (int i = 0; i < count; i++) is.readUnsignedShort();
                for (int i = 0; i < count; i++) is.readUnsignedShort();
            } else if (opcode == 13) {
                is.readInt();
            } else if (opcode == 14) {
                int count = is.readUnsignedShort();
                for (int i = 0; i < count; i++) {
                    is.readUnsignedShort(); is.readUnsignedShort();
                    is.readUnsignedByte(); is.readUnsignedByte(); is.readUnsignedByte(); is.readUnsignedByte();
                }
            } else if (opcode == 15) {
                is.readUnsignedShort(); is.readUnsignedShort();
            } else if (opcode == 16) {
                is.readByte();
            } else if (opcode == 17) {
                int count = is.readUnsignedByte();
                for (int i = 0; i < count; i++) is.readUnsignedByte();
            } else if (opcode == 18) {
                is.readString();
            } else if (opcode == 19) {
                // no payload
            } else {
                throw new RuntimeException("unknown seq opcode " + opcode);
            }
        }
        return def;
    }
}

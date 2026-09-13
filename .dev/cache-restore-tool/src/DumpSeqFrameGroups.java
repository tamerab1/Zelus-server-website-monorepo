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
import java.util.LinkedHashSet;

// Read-only. Decodes opcode 1's frameGroup[] array (the actual frame-archive ids a sequence's
// frames live in) for each given sequence id, so its referenced frame archives can be checked
// against what is/isn't imported into a given cache.
public class DumpSeqFrameGroups {
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
                byte[] b = file.getContents();
                InputStream is = new InputStream(b);
                LinkedHashSet<Integer> secondArray = new LinkedHashSet<>();
                LinkedHashSet<Integer> thirdArray = new LinkedHashSet<>();
                while (true) {
                    int opcode = is.readUnsignedByte();
                    if (opcode == 0) break;
                    if (opcode == 1) {
                        int count = is.readUnsignedShort();
                        int[] delay = new int[count];
                        for (int j = 0; j < count; j++) delay[j] = is.readUnsignedShort();
                        for (int j = 0; j < count; j++) secondArray.add(is.readUnsignedShort());
                        for (int j = 0; j < count; j++) thirdArray.add(is.readUnsignedShort());
                    } else if (opcode == 2) is.readUnsignedShort();
                    else if (opcode == 3) { int c = is.readUnsignedByte(); for (int j = 0; j < c; j++) is.readUnsignedByte(); }
                    else if (opcode == 4) { }
                    else if (opcode == 5) is.readUnsignedByte();
                    else if (opcode == 6 || opcode == 7) is.readUnsignedShort();
                    else if (opcode == 8) is.readUnsignedByte();
                    else if (opcode == 9 || opcode == 10 || opcode == 11) is.readUnsignedByte();
                    else if (opcode == 12) { int c = is.readUnsignedByte(); for (int j = 0; j < c; j++) { is.readUnsignedShort(); is.readUnsignedShort(); } }
                    else if (opcode == 13) is.readInt();
                    else if (opcode == 14) { int c = is.readUnsignedShort(); for (int j = 0; j < c; j++) { is.readUnsignedShort(); is.readUnsignedShort(); is.readUnsignedByte(); is.readUnsignedByte(); is.readUnsignedByte(); is.readUnsignedByte(); } }
                    else if (opcode == 15) { is.readUnsignedShort(); is.readUnsignedShort(); }
                    else if (opcode == 16) is.readByte();
                    else if (opcode == 17) { int c = is.readUnsignedByte(); for (int j = 0; j < c; j++) is.readUnsignedByte(); }
                    else if (opcode == 18) is.readString();
                    else if (opcode == 19) { }
                    else throw new RuntimeException("unknown seq opcode " + opcode + " for id " + id);
                }
                System.out.println(id + ": secondArray=" + secondArray + " thirdArray=" + thirdArray);
            }
        }
    }
}

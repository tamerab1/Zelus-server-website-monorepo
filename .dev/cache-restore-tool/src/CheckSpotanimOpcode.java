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

public class CheckSpotanimOpcode {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Index configs = store.getIndex(IndexType.CONFIGS);
            Archive archive = configs.getArchive(ConfigType.SPOTANIM.getId());
            ArchiveFiles files = archive.getFiles(storage.loadArchive(archive));

            for (int i = 1; i < args.length; i++) {
                int id = Integer.parseInt(args[i]);
                FSFile file = files.findFile(id);
                if (file == null) { System.out.println(id + " NOT FOUND"); continue; }
                byte[] b = file.getContents();
                InputStream is = new InputStream(b);
                StringBuilder sb = new StringBuilder();
                sb.append(id).append(": opcodes=[");
                while (true) {
                    int opcode = is.readUnsignedByte();
                    if (opcode == 0) break;
                    sb.append(opcode).append(",");
                    if (opcode == 1) is.readUnsignedShort();
                    else if (opcode == 2) is.readUnsignedShort();
                    else if (opcode == 3) is.readInt();
                    else if (opcode == 4) is.readUnsignedShort();
                    else if (opcode == 5) is.readUnsignedShort();
                    else if (opcode == 6) is.readUnsignedShort();
                    else if (opcode == 7) is.readUnsignedByte();
                    else if (opcode == 8) is.readUnsignedByte();
                    else if (opcode == 9) is.readString();
                    else if (opcode == 10) { }
                    else if (opcode == 40 || opcode == 41) {
                        int count = is.readUnsignedByte();
                        for (int j = 0; j < count; j++) { is.readUnsignedShort(); is.readUnsignedShort(); }
                    } else throw new RuntimeException("unknown " + opcode);
                }
                sb.append("]");
                System.out.println(sb);
            }
        }
    }
}

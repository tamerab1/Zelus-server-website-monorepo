import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;
import net.runelite.cache.io.InputStream;

import java.io.File;
import java.util.Arrays;
import java.util.List;

public class DumpObjectActions {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Index configs = store.getIndex(IndexType.CONFIGS);
            Archive archive = configs.getArchive(ConfigType.OBJECT.getId());
            byte[] decompressed = archive.decompress(storage.loadArchive(archive));
            FileData[] fileData = archive.getFileData();
            List<byte[]> contents = SpliceItemOption.splitChunks(decompressed, fileData.length);

            for (int i = 1; i < args.length; i++) {
                int id = Integer.parseInt(args[i]);
                int slot = -1;
                for (int j = 0; j < fileData.length; j++) if (fileData[j].getId() == id) slot = j;
                if (slot == -1) { System.out.println(id + " NOT FOUND"); continue; }
                byte[] b = contents.get(slot);
                InputStream is = new InputStream(b);
                String[] actions = new String[5];
                String name = null;
                while (true) {
                    int opcode = is.readUnsignedByte();
                    if (opcode == 0) break;
                    if (opcode == 1) {
                        int count = is.readUnsignedByte();
                        for (int j = 0; j < count; j++) { is.readUnsignedShort(); is.readUnsignedByte(); }
                    } else if (opcode == 2) {
                        name = is.readString();
                    } else if (opcode == 3) {
                        is.readString();
                    } else if (opcode == 5) {
                        int count = is.readUnsignedByte();
                        for (int j = 0; j < count; j++) is.readUnsignedShort();
                    } else if (opcode == 14 || opcode == 15) {
                        is.readUnsignedByte();
                    } else if (opcode == 17 || opcode == 18) {
                    } else if (opcode == 19) {
                        is.readUnsignedByte();
                    } else if (opcode == 21 || opcode == 22 || opcode == 23) {
                    } else if (opcode == 24) {
                        is.readUnsignedShort();
                    } else if (opcode == 27) {
                    } else if (opcode == 28) {
                        is.readUnsignedByte();
                    } else if (opcode == 29) {
                        is.readByte();
                    } else if (opcode >= 30 && opcode < 35) {
                        actions[opcode - 30] = is.readString();
                    } else if (opcode == 39) {
                        is.readByte();
                    } else if (opcode == 40 || opcode == 41) {
                        int count = is.readUnsignedByte();
                        for (int j = 0; j < count; j++) { is.readUnsignedShort(); is.readUnsignedShort(); }
                    } else if (opcode == 61) {
                        is.readUnsignedShort();
                    } else if (opcode == 62 || opcode == 64) {
                    } else if (opcode == 65 || opcode == 66 || opcode == 67) {
                        is.readUnsignedShort();
                    } else if (opcode == 68) {
                        is.readUnsignedShort();
                    } else if (opcode == 69) {
                        is.readUnsignedByte();
                    } else if (opcode == 70 || opcode == 71 || opcode == 72) {
                        is.readShort();
                    } else if (opcode == 73 || opcode == 74) {
                    } else if (opcode == 75) {
                        is.readUnsignedByte();
                    } else if (opcode == 77 || opcode == 92) {
                        is.readUnsignedShort(); is.readUnsignedShort();
                        if (opcode == 92) is.readUnsignedShort();
                        int count = is.readUnsignedByte();
                        for (int j = 0; j <= count; j++) is.readUnsignedShort();
                    } else if (opcode == 78) {
                        is.readUnsignedShort(); is.readUnsignedByte(); is.readUnsignedByte();
                    } else if (opcode == 79) {
                        is.readUnsignedShort(); is.readUnsignedShort(); is.readUnsignedByte(); is.readUnsignedByte();
                        int count = is.readUnsignedByte();
                        for (int j = 0; j < count; j++) is.readUnsignedShort();
                    } else if (opcode == 81) {
                        is.readUnsignedByte();
                    } else if (opcode == 82) {
                        is.readUnsignedShort();
                    } else if (opcode == 89 || opcode == 90) {
                    } else if (opcode == 91) {
                        is.readUnsignedByte();
                    } else if (opcode == 93) {
                        is.readUnsignedByte(); is.readUnsignedShort(); is.readUnsignedByte(); is.readUnsignedShort();
                    } else if (opcode == 94) {
                    } else if (opcode == 95) {
                        is.readUnsignedByte();
                    } else if (opcode == 96) {
                        is.readUnsignedByte();
                    } else if (opcode == 100) {
                        is.readUnsignedByte(); is.readUnsignedByte(); is.readString();
                    } else if (opcode == 101) {
                        is.readUnsignedByte(); is.readUnsignedShort(); is.readUnsignedShort(); is.readInt(); is.readInt(); is.readString();
                    } else if (opcode == 102) {
                        is.readUnsignedByte(); is.readUnsignedShort(); is.readUnsignedShort(); is.readUnsignedShort(); is.readInt(); is.readInt(); is.readString();
                    } else if (opcode == 200) {
                        is.readUnsignedShort();
                    } else if (opcode == 249) {
                        int length = is.readUnsignedByte();
                        for (int j = 0; j < length; j++) {
                            boolean isString = is.readUnsignedByte() == 1;
                            is.read24BitInt();
                            if (isString) is.readString(); else is.readInt();
                        }
                    } else {
                        throw new RuntimeException("unknown opcode " + opcode);
                    }
                }
                System.out.println(id + " name=" + name + " actions=" + Arrays.toString(actions));
            }
        }
    }
}

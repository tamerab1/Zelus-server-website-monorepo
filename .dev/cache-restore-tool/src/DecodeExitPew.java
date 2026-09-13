import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;
import net.runelite.cache.io.InputStream;

import java.io.File;
import java.util.*;

// Read-only, one-off. Full decode (name + all 5 option-menu strings + model/anim refs) of a
// specific object id from the source cache, needed to verify 62251 ("Exit"/"Quick-exit" pew)
// before importing it -- DecodeCathedralObjects.java discards option text, this keeps it.
public class DecodeExitPew {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        int[] ids = new int[args.length - 1];
        for (int i = 1; i < args.length; i++) ids[i - 1] = Integer.parseInt(args[i]);

        try (Store store = new Store(new File(cachePath))) {
            store.load();
            var storage = store.getStorage();
            var configs = store.getIndex(IndexType.CONFIGS);
            Archive archive = configs.getArchive(ConfigType.OBJECT.getId());
            byte[] decompressed = archive.decompress(storage.loadArchive(archive));
            FileData[] fileData = archive.getFileData();
            List<byte[]> contents = SpliceItemOption.splitChunks(decompressed, fileData.length);

            for (int id : ids) {
                int slot = -1;
                for (int j = 0; j < fileData.length; j++) if (fileData[j].getId() == id) slot = j;
                if (slot == -1) {
                    System.out.println(id + " -- NOT FOUND in source");
                    continue;
                }
                decode(id, contents.get(slot));
            }
        }
    }

    static void decode(int id, byte[] b) {
        InputStream is = new InputStream(b);
        String name = null;
        String[] options = new String[5];
        TreeSet<Long> models = new TreeSet<>();
        Integer anim = null;
        while (true) {
            int opcode = is.readUnsignedByte();
            if (opcode == 0) break;
            if (opcode == 1) {
                int count = is.readUnsignedByte();
                for (int i = 0; i < count; i++) { models.add((long) is.readUnsignedShort()); is.readUnsignedByte(); }
            } else if (opcode == 2) {
                name = is.readString();
            } else if (opcode == 3) {
                is.readString();
            } else if (opcode == 5) {
                int count = is.readUnsignedByte();
                for (int i = 0; i < count; i++) models.add((long) is.readUnsignedShort());
            } else if (opcode == 6) {
                int count = is.readUnsignedByte();
                for (int i = 0; i < count; i++) { models.add((long) is.readInt()); is.readUnsignedByte(); }
            } else if (opcode == 7) {
                int count = is.readUnsignedByte();
                for (int i = 0; i < count; i++) models.add((long) is.readInt());
            } else if (opcode == 14 || opcode == 15) {
                is.readUnsignedByte();
            } else if (opcode == 17 || opcode == 18) {
            } else if (opcode == 19) {
                is.readUnsignedByte();
            } else if (opcode == 21 || opcode == 22 || opcode == 23) {
            } else if (opcode == 24) {
                int v = is.readUnsignedShort();
                anim = v == 0xFFFF ? null : v;
            } else if (opcode == 27) {
            } else if (opcode == 28) {
                is.readUnsignedByte();
            } else if (opcode == 29) {
                is.readByte();
            } else if (opcode >= 30 && opcode < 35) {
                options[opcode - 30] = is.readString();
            } else if (opcode == 39) {
                is.readByte();
            } else if (opcode == 40 || opcode == 41) {
                int count = is.readUnsignedByte();
                for (int i = 0; i < count; i++) { is.readUnsignedShort(); is.readUnsignedShort(); }
            } else if (opcode == 61) {
                is.readUnsignedShort();
            } else if (opcode == 62) {
            } else if (opcode == 64) {
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
                for (int i = 0; i <= count; i++) is.readUnsignedShort();
            } else if (opcode == 78) {
                is.readUnsignedShort(); is.readUnsignedByte(); is.readUnsignedByte();
            } else if (opcode == 79) {
                is.readUnsignedShort(); is.readUnsignedShort(); is.readUnsignedByte(); is.readUnsignedByte();
                int count = is.readUnsignedByte();
                for (int i = 0; i < count; i++) is.readUnsignedShort();
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
                for (int i = 0; i < length; i++) {
                    boolean isString = is.readUnsignedByte() == 1;
                    is.read24BitInt();
                    if (isString) is.readString(); else is.readInt();
                }
            } else {
                throw new RuntimeException("unrecognized loc opcode " + opcode + " for object " + id);
            }
        }
        System.out.println(id + " name=" + name + " options=" + Arrays.toString(options) + " models=" + models + " anim=" + anim);
    }
}

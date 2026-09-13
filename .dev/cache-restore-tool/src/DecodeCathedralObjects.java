import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;
import net.runelite.cache.io.InputStream;

import java.io.File;
import java.util.*;

// Read-only. Full-fidelity LocType (OBJECT) decoder, opcodes translated 1:1 from
// RS-Realm-Server-Package's own LocTypeDecoder.kt (same cross-reference approach already used for
// npcs/spotanims this session). For each given object id, decodes its source record, throws on any
// truly-unrecognized opcode (never silently desyncs), and collects every model id (opcodes 1/5/6/7)
// and animation sequence id (opcode 24) it references -- exactly the sub-resources that would also
// need importing/collision-checking before these 113 missing Cathedral objects can be added.
public class DecodeCathedralObjects {
    static class Def {
        int id;
        String name;
        TreeSet<Integer> models = new TreeSet<>();
        Integer anim;
    }

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

            TreeSet<Integer> allModels = new TreeSet<>();
            TreeSet<Integer> allAnims = new TreeSet<>();
            int failures = 0;
            for (int id : ids) {
                int slot = -1;
                for (int j = 0; j < fileData.length; j++) if (fileData[j].getId() == id) slot = j;
                if (slot == -1) {
                    System.out.println(id + " -- NOT FOUND in source");
                    failures++;
                    continue;
                }
                try {
                    Def def = decode(id, contents.get(slot));
                    System.out.println(id + " name=" + def.name + " models=" + def.models + " anim=" + def.anim);
                    allModels.addAll(def.models);
                    if (def.anim != null) allAnims.add(def.anim);
                } catch (Exception e) {
                    System.out.println(id + " -- DECODE FAILED: " + e.getMessage());
                    failures++;
                }
            }
            System.out.println();
            System.out.println("=== SUMMARY ===");
            System.out.println("objects requested: " + ids.length + ", failures: " + failures);
            System.out.println("distinct models referenced: " + allModels.size());
            System.out.println(allModels);
            System.out.println("distinct anims referenced: " + allAnims.size());
            System.out.println(allAnims);
        }
    }

    static Def decode(int id, byte[] b) {
        Def def = new Def();
        def.id = id;
        InputStream is = new InputStream(b);
        while (true) {
            int opcode = is.readUnsignedByte();
            if (opcode == 0) break;
            decodeOne(opcode, def, is);
        }
        return def;
    }

    static void decodeOne(int opcode, Def def, InputStream is) {
        if (opcode == 1) {
            int count = is.readUnsignedByte();
            for (int i = 0; i < count; i++) { def.models.add(is.readUnsignedShort()); is.readUnsignedByte(); }
        } else if (opcode == 2) {
            def.name = is.readString();
        } else if (opcode == 3) {
            is.readString();
        } else if (opcode == 5) {
            int count = is.readUnsignedByte();
            for (int i = 0; i < count; i++) def.models.add(is.readUnsignedShort());
        } else if (opcode == 6) {
            int count = is.readUnsignedByte();
            for (int i = 0; i < count; i++) { def.models.add(is.readInt()); is.readUnsignedByte(); }
        } else if (opcode == 7) {
            int count = is.readUnsignedByte();
            for (int i = 0; i < count; i++) def.models.add(is.readInt());
        } else if (opcode == 14 || opcode == 15) {
            is.readUnsignedByte();
        } else if (opcode == 17 || opcode == 18) {
            // no payload
        } else if (opcode == 19) {
            is.readUnsignedByte();
        } else if (opcode == 21 || opcode == 22 || opcode == 23) {
            // no payload
        } else if (opcode == 24) {
            int v = is.readUnsignedShort();
            def.anim = v == 0xFFFF ? null : v;
        } else if (opcode == 27) {
            // no payload
        } else if (opcode == 28) {
            is.readUnsignedByte();
        } else if (opcode == 29) {
            is.readByte();
        } else if (opcode >= 30 && opcode < 35) {
            is.readString();
        } else if (opcode == 39) {
            is.readByte();
        } else if (opcode == 40 || opcode == 41) {
            int count = is.readUnsignedByte();
            for (int i = 0; i < count; i++) { is.readUnsignedShort(); is.readUnsignedShort(); }
        } else if (opcode == 61) {
            is.readUnsignedShort();
        } else if (opcode == 62) {
            // no payload
        } else if (opcode == 64) {
            // no payload
        } else if (opcode == 65 || opcode == 66 || opcode == 67) {
            is.readUnsignedShort();
        } else if (opcode == 68) {
            is.readUnsignedShort();
        } else if (opcode == 69) {
            is.readUnsignedByte();
        } else if (opcode == 70 || opcode == 71 || opcode == 72) {
            is.readShort();
        } else if (opcode == 73 || opcode == 74) {
            // no payload
        } else if (opcode == 75) {
            is.readUnsignedByte();
        } else if (opcode == 77 || opcode == 92) {
            readNullableShort(is);
            readNullableShort(is);
            if (opcode == 92) readNullableShort(is);
            int count = is.readUnsignedByte();
            for (int i = 0; i <= count; i++) readNullableShort(is);
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
            // no payload
        } else if (opcode == 91) {
            is.readUnsignedByte();
        } else if (opcode == 93) {
            is.readUnsignedByte(); is.readUnsignedShort(); is.readUnsignedByte(); is.readUnsignedShort();
        } else if (opcode == 94) {
            // no payload
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
            throw new RuntimeException("unrecognized loc opcode " + opcode + " for object " + def.id);
        }
    }

    static Integer readNullableShort(InputStream is) {
        int v = is.readUnsignedShort();
        return v == 0xFFFF ? null : v;
    }
}

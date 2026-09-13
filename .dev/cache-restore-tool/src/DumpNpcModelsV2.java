import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;
import net.runelite.cache.io.InputStream;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// Read-only. NpcLoader (net.runelite.cache, copyright 2016-2017) does not understand every opcode
// used by a 2026-era npc record -- confirmed live: opcode 61 desyncs it on BOTH Mad Angel (16305)
// and Kree'arra (3162). This is a corrected decoder, opcodes translated 1:1 from
// RS-Realm-Server-Package's own NpcTypeDecoder.kt (the modern, currently-in-production decoder),
// so every field's byte width is right and the stream never desyncs. Only the fields this
// investigation cares about are actually stored; everything else is read-and-discarded to keep
// position correct. No writes anywhere.
public class DumpNpcModelsV2 {
    static class Def {
        int id;
        String name;
        String desc;
        int[] models;
        int[] chatheadModels;
        int combatLevel = -1;
        String[] actions = new String[5];
        List<Integer> unrecognizedOpcodes = new ArrayList<>();
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
            int[] models = new int[count];
            for (int i = 0; i < count; i++) models[i] = is.readUnsignedShort();
            def.models = models;
        } else if (opcode == 2) {
            def.name = is.readString();
        } else if (opcode == 3) {
            def.desc = is.readString();
        } else if (opcode == 12) {
            is.readUnsignedByte();
        } else if (opcode == 13 || opcode == 14 || opcode == 15 || opcode == 16) {
            is.readUnsignedShort();
        } else if (opcode == 17) {
            is.readUnsignedShort(); is.readUnsignedShort(); is.readUnsignedShort(); is.readUnsignedShort();
        } else if (opcode == 18) {
            is.readUnsignedShort();
        } else if (opcode >= 30 && opcode < 35) {
            String action = is.readString();
            def.actions[opcode - 30] = action.equalsIgnoreCase("hidden") ? null : action;
        } else if (opcode == 40 || opcode == 41) {
            int count = is.readUnsignedByte();
            for (int i = 0; i < count; i++) { is.readUnsignedShort(); is.readUnsignedShort(); }
        } else if (opcode == 60) {
            int count = is.readUnsignedByte();
            int[] models = new int[count];
            for (int i = 0; i < count; i++) models[i] = is.readUnsignedShort();
            def.chatheadModels = models;
        } else if (opcode == 61) {
            int count = is.readUnsignedByte();
            int[] models = new int[count];
            for (int i = 0; i < count; i++) models[i] = is.readInt();
            def.models = models;
        } else if (opcode == 62) {
            int count = is.readUnsignedByte();
            int[] models = new int[count];
            for (int i = 0; i < count; i++) models[i] = is.readInt();
            def.chatheadModels = models;
        } else if (opcode >= 74 && opcode <= 79) {
            is.readUnsignedShort();
        } else if (opcode == 93) {
            // no payload
        } else if (opcode == 95) {
            def.combatLevel = is.readUnsignedShort();
        } else if (opcode == 97 || opcode == 98) {
            is.readUnsignedShort();
        } else if (opcode == 99) {
            // no payload
        } else if (opcode == 100 || opcode == 101) {
            is.readByte();
        } else if (opcode == 102) {
            int enabledFlags = is.readUnsignedByte();
            int count = Integer.bitCount(enabledFlags);
            for (int i = 0; i < count; i++) {
                // large-smart (group id) + unsigned-small-smart-plus-one (file id)
                readNullableLargeSmart(is);
                readUnsignedSmallSmartPlusOne(is);
            }
        } else if (opcode == 103) {
            is.readUnsignedShort();
        } else if (opcode == 106 || opcode == 118) {
            is.readUnsignedShort(); // varbit
            is.readUnsignedShort(); // varp
            if (opcode == 118) is.readUnsignedShort(); // default npc
            int count = is.readUnsignedByte();
            for (int i = 0; i <= count; i++) is.readUnsignedShort();
        } else if (opcode == 107 || opcode == 109 || opcode == 111) {
            // no payload
        } else if (opcode == 114 || opcode == 116) {
            is.readUnsignedShort();
        } else if (opcode == 115 || opcode == 117) {
            is.readUnsignedShort(); is.readUnsignedShort(); is.readUnsignedShort(); is.readUnsignedShort();
        } else if (opcode == 122 || opcode == 123) {
            // no payload
        } else if (opcode == 124 || opcode == 126) {
            is.readUnsignedShort();
        } else if (opcode == 129 || opcode == 130 || opcode == 145) {
            // no payload
        } else if (opcode == 146) {
            is.readUnsignedShort();
        } else if (opcode == 200 || opcode == 201 || opcode == 202) {
            is.readUnsignedByte();
        } else if (opcode == 203) {
            int count = is.readUnsignedByte() + 1;
            for (int i = 0; i < count; i++) {
                is.readInt(); // coordgrid, packed as one int in this port's util -- see note below
                is.readUnsignedByte();
            }
        } else if (opcode == 204) {
            is.readUnsignedShort();
        } else if (opcode == 205 || opcode == 206 || opcode == 207 || opcode == 208 || opcode == 209) {
            is.readUnsignedByte();
        } else if (opcode == 210) {
            // no payload
        } else if (opcode == 211) {
            is.readUnsignedShort();
        } else if (opcode == 212) {
            is.readUnsignedByte();
        } else if (opcode == 213 || opcode == 214 || opcode == 215 || opcode == 216) {
            is.readUnsignedShort();
        } else if (opcode == 147) {
            // no payload
        } else if (opcode == 249) {
            int length = is.readUnsignedByte();
            for (int i = 0; i < length; i++) {
                boolean isString = is.readUnsignedByte() == 1;
                is.read24BitInt();
                if (isString) is.readString(); else is.readInt();
            }
        } else if (opcode == 252) {
            for (int i = 0; i < 14; i++) is.readUnsignedByte();
        } else {
            def.unrecognizedOpcodes.add(opcode);
            throw new RuntimeException("TRULY unrecognized opcode " + opcode + " for npc " + def.id
                    + " -- stopping here rather than silently desyncing.");
        }
    }

    // OSRS "large smart": if the next byte's high bit is set, read a 4-byte value (top bit
    // masked); otherwise read a 2-byte value. Sentinel (all-1s for the given width) means null.
    static Integer readNullableLargeSmart(InputStream is) {
        // InputStream has no peek, so this mirrors it via readBigSmart2, which implements the
        // same large-smart convention already (see net.runelite.cache.io.InputStream).
        int v = is.readBigSmart2();
        return v;
    }

    static int readUnsignedSmallSmartPlusOne(InputStream is) {
        return is.readUnsignedShortSmartMinusOne() + 1;
    }

    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Index index = store.getIndex(IndexType.CONFIGS);
            Archive archive = index.getArchive(ConfigType.NPC.getId());
            byte[] decompressed = archive.decompress(storage.loadArchive(archive));
            FileData[] fileData = archive.getFileData();
            List<byte[]> fileContents = SpliceItemOption.splitChunks(decompressed, fileData.length);
            for (int i = 1; i < args.length; i++) {
                int id = Integer.parseInt(args[i]);
                int slot = -1;
                for (int j = 0; j < fileData.length; j++) if (fileData[j].getId() == id) slot = j;
                if (slot == -1) {
                    System.out.println(id + " -- NOT FOUND");
                    continue;
                }
                try {
                    Def def = decode(id, fileContents.get(slot));
                    System.out.println(id + " name=" + def.name
                            + " desc=" + def.desc
                            + " combatLevel=" + def.combatLevel
                            + " actions=" + Arrays.toString(def.actions)
                            + " models=" + Arrays.toString(def.models)
                            + " chatheadModels=" + Arrays.toString(def.chatheadModels));
                } catch (Exception e) {
                    System.out.println(id + " -- DECODE FAILED: " + e.getMessage());
                }
            }
        }
    }
}

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
import java.util.List;

// Read-only. Same corrected opcode table as DumpNpcModelsV2, but this one actually KEEPS opcodes
// 12 (standAnim/idle), 13 (walkAnim), 14/15/16 (turn anims), and 17 (the 4-part directional walk
// set) instead of discarding them -- those are exactly the animation ids that drive an npc's
// idle/walking pose between explicit npc.animate() calls, and are baked into the npc DEFINITION
// itself rather than anything MadAngel.java touches. If one of these still points at a raw SOURCE
// sequence id that got remapped during the pack pass (see MadAngelIds' javadoc for the 4 remapped
// ids: 3321->14449, 4589->14450, 4590->14451, 8543->14452), or at a source id that was never
// remapped at all, the client will either render nothing/garbage or silently reuse whatever
// leftover animation happens to sit at that raw id in Zelus's cache for her idle pose.
public class DumpNpcAnimIds {
    static class Def {
        int id;
        String name;
        int size = -1;
        int readyAnim = -1;
        int walkAnim = -1;
        int turnLeftAnim = -1;
        int turnRightAnim = -1;
        int turnBackAnim = -1;
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
            for (int i = 0; i < count; i++) is.readUnsignedShort();
        } else if (opcode == 2 || opcode == 3) {
            String s = is.readString();
            if (opcode == 2) def.name = s;
        } else if (opcode == 12) {
            def.size = is.readUnsignedByte();
        } else if (opcode == 13) {
            def.readyAnim = is.readUnsignedShort();
        } else if (opcode == 14) {
            def.walkAnim = is.readUnsignedShort();
        } else if (opcode == 15) {
            def.turnLeftAnim = is.readUnsignedShort();
        } else if (opcode == 16) {
            def.turnRightAnim = is.readUnsignedShort();
        } else if (opcode == 17) {
            def.walkAnim = is.readUnsignedShort();
            def.turnBackAnim = is.readUnsignedShort();
            def.turnLeftAnim = is.readUnsignedShort();
            def.turnRightAnim = is.readUnsignedShort();
        } else if (opcode == 18) {
            is.readUnsignedShort();
        } else if (opcode >= 30 && opcode < 35) {
            is.readString();
        } else if (opcode == 40 || opcode == 41) {
            int count = is.readUnsignedByte();
            for (int i = 0; i < count; i++) { is.readUnsignedShort(); is.readUnsignedShort(); }
        } else if (opcode == 60) {
            int count = is.readUnsignedByte();
            for (int i = 0; i < count; i++) is.readUnsignedShort();
        } else if (opcode == 61) {
            int count = is.readUnsignedByte();
            for (int i = 0; i < count; i++) is.readInt();
        } else if (opcode == 62) {
            int count = is.readUnsignedByte();
            for (int i = 0; i < count; i++) is.readInt();
        } else if (opcode >= 74 && opcode <= 79) {
            is.readUnsignedShort();
        } else if (opcode == 93) {
            // no payload
        } else if (opcode == 95) {
            is.readUnsignedShort();
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
                is.readBigSmart2();
                is.readUnsignedShortSmartMinusOne();
            }
        } else if (opcode == 103) {
            is.readUnsignedShort();
        } else if (opcode == 106 || opcode == 118) {
            is.readUnsignedShort();
            is.readUnsignedShort();
            if (opcode == 118) is.readUnsignedShort();
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
            for (int i = 0; i < count; i++) { is.readInt(); is.readUnsignedByte(); }
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
            throw new RuntimeException("unrecognized opcode " + opcode + " for npc " + def.id);
        }
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
                    StringBuilder sb = new StringBuilder();
                    sb.append(id).append(" name=").append(def.name)
                            .append(" size=").append(def.size)
                            .append(" readyAnim=").append(def.readyAnim)
                            .append(" walkAnim=").append(def.walkAnim)
                            .append(" turnLeftAnim=").append(def.turnLeftAnim)
                            .append(" turnRightAnim=").append(def.turnRightAnim)
                            .append(" turnBackAnim=").append(def.turnBackAnim);
                    System.out.println(sb);
                } catch (Exception e) {
                    System.out.println(id + " -- DECODE FAILED: " + e.getMessage());
                }
            }
        }
    }
}

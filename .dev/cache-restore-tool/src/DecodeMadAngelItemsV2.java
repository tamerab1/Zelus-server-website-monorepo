import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;
import net.runelite.cache.io.InputStream;

import java.io.File;
import java.util.List;
import java.util.TreeSet;

// Read-only. Full decode of the 5 Mad Angel unique items using the REAL modern opcode table --
// translated 1:1 from RS-Realm-Server-Package's own ObjTypeDecoder.kt (same cross-reference
// technique already used for LocType/NPCType/spotanim this session). The stock net.runelite.cache
// ItemLoader silently mis-decoded these (DecodeMadAngelItems.java's own output: cost/zoom/resize
// came through but name never did) because it doesn't know opcodes 44-54, the wide (4-byte) model
// id variants this modern cache uses -- reading the wrong byte count there desyncs everything after.
public class DecodeMadAngelItemsV2 {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        int[] ids = args.length > 1 ? parseIds(args) : new int[]{34032, 34033, 34027, 34030, 34042};

        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Archive archive = store.getIndex(IndexType.CONFIGS).getArchive(ConfigType.ITEM.getId());
            byte[] decompressed = archive.decompress(storage.loadArchive(archive));
            FileData[] fileData = archive.getFileData();
            List<byte[]> contents = SpliceItemOption.splitChunks(decompressed, fileData.length);

            TreeSet<Integer> allModels = new TreeSet<>();
            for (int id : ids) {
                int slot = -1;
                for (int j = 0; j < fileData.length; j++) if (fileData[j].getId() == id) slot = j;
                if (slot == -1) {
                    System.out.println(id + " -- NOT FOUND");
                    continue;
                }
                Def def = decode(id, contents.get(slot));
                System.out.println("=== " + id + " \"" + def.name + "\" ===");
                System.out.println("  model=" + def.model + " zoom2d=" + def.zoom2d + " xan2d=" + def.xan2d
                        + " yan2d=" + def.yan2d + " xof2d=" + def.xof2d + " yof2d=" + def.yof2d);
                System.out.println("  stackable=" + def.stackable + " tradeable=" + def.tradeable + " members=" + def.members
                        + " cost=" + def.cost);
                System.out.println("  wearpos1=" + def.wearpos1 + " wearpos2=" + def.wearpos2 + " wearpos3=" + def.wearpos3);
                if (def.manwear != -1 || def.manwear2 != -1 || def.manwear3 != -1)
                    System.out.println("  manwear=" + def.manwear + " manwear2=" + def.manwear2 + " manwear3=" + def.manwear3);
                if (def.womanwear != -1 || def.womanwear2 != -1 || def.womanwear3 != -1)
                    System.out.println("  womanwear=" + def.womanwear + " womanwear2=" + def.womanwear2 + " womanwear3=" + def.womanwear3);
                if (def.manhead != -1 || def.manhead2 != -1)
                    System.out.println("  manhead=" + def.manhead + " manhead2=" + def.manhead2);
                if (def.womanhead != -1 || def.womanhead2 != -1)
                    System.out.println("  womanhead=" + def.womanhead + " womanhead2=" + def.womanhead2);
                System.out.println("  resize=(" + def.resizeX + "," + def.resizeY + "," + def.resizeZ + ") ambient="
                        + def.ambient + " contrast=" + def.contrast);
                System.out.println("  ops=" + java.util.Arrays.toString(def.op) + " iops=" + java.util.Arrays.toString(def.iop));
                if (def.certlink != -1) System.out.println("  certlink=" + def.certlink + " certtemplate=" + def.certtemplate);
                if (def.placeholderlink != -1) System.out.println("  placeholderlink=" + def.placeholderlink);
                allModels.addAll(def.allModelIds());
            }
            System.out.println();
            System.out.println("=== all referenced model ids (for collision checking) ===");
            System.out.println(allModels);
        }
    }

    static int[] parseIds(String[] args) {
        int[] ids = new int[args.length - 1];
        for (int i = 1; i < args.length; i++) ids[i - 1] = Integer.parseInt(args[i]);
        return ids;
    }

    static class Def {
        int id;
        String name = "";
        int model = -1;
        int zoom2d = 2000, xan2d = 0, yan2d = 0, xof2d = 0, yof2d = 0;
        boolean stackable = false, tradeable = true, members = false;
        int cost = 1;
        int wearpos1 = -1, wearpos2 = -1, wearpos3 = -1;
        int manwear = -1, manwearOff = 0, manwear2 = -1, manwear3 = -1;
        int womanwear = -1, womanwearOff = 0, womanwear2 = -1, womanwear3 = -1;
        int manhead = -1, manhead2 = -1, womanhead = -1, womanhead2 = -1;
        int resizeX = 128, resizeY = 128, resizeZ = 128;
        int ambient = 0, contrast = 0, team = 0;
        int weight = 0;
        String[] op = new String[5];
        String[] iop = new String[5];
        int certlink = -1, certtemplate = -1;
        int boughtlink = -1, boughttemplate = -1;
        int placeholderlink = -1, placeholdertemplate = -1;
        int transformlink = -1, transformtemplate = -1;

        List<Integer> allModelIds() {
            List<Integer> l = new java.util.ArrayList<>();
            for (int m : new int[]{model, manwear, manwear2, manwear3, womanwear, womanwear2, womanwear3, manhead, manhead2, womanhead, womanhead2}) {
                if (m > 0) l.add(m);
            }
            return l;
        }
    }

    static Def decode(int id, byte[] b) {
        Def d = new Def();
        d.id = id;
        InputStream in = new InputStream(b);
        while (true) {
            int code = in.readUnsignedByte();
            if (code == 0) break;
            switch (code) {
                case 1: d.model = in.readUnsignedShort(); break;
                case 2: d.name = in.readString(); break;
                case 3: in.readString(); break; // desc
                case 4: d.zoom2d = in.readUnsignedShort(); break;
                case 5: d.xan2d = in.readUnsignedShort(); break;
                case 6: d.yan2d = in.readUnsignedShort(); break;
                case 7: d.xof2d = in.readShort(); break;
                case 8: d.yof2d = in.readShort(); break;
                case 9: in.readString(); break;
                case 11: d.stackable = true; break;
                case 12: d.cost = in.readInt(); break;
                case 13: d.wearpos1 = in.readByte(); break;
                case 14: d.wearpos2 = in.readByte(); break;
                case 15: d.tradeable = false; break;
                case 16: d.members = true; break;
                case 23: d.manwear = in.readUnsignedShort(); d.manwearOff = in.readUnsignedByte(); break;
                case 24: d.manwear2 = in.readUnsignedShort(); break;
                case 25: d.womanwear = in.readUnsignedShort(); d.womanwearOff = in.readUnsignedByte(); break;
                case 26: d.womanwear2 = in.readUnsignedShort(); break;
                case 27: d.wearpos3 = in.readByte(); break;
                case 30: case 31: case 32: case 33: case 34: d.op[code - 30] = in.readString(); break;
                case 35: case 36: case 37: case 38: case 39: d.iop[code - 35] = in.readString(); break;
                case 40: case 41: {
                    int count = in.readUnsignedByte();
                    for (int i = 0; i < count; i++) { in.readUnsignedShort(); in.readUnsignedShort(); }
                    break;
                }
                case 42: in.readByte(); break;
                case 43: {
                    in.readUnsignedByte(); // op index
                    int subop = in.readUnsignedByte();
                    while (subop != 0) {
                        in.readString();
                        subop = in.readUnsignedByte();
                    }
                    break;
                }
                case 44: d.model = in.readInt(); break;
                case 45: d.manwear = in.readInt(); d.manwearOff = in.readUnsignedByte(); break;
                case 46: d.manwear2 = in.readInt(); break;
                case 47: d.manwear3 = in.readInt(); break;
                case 48: d.womanwear = in.readInt(); d.womanwearOff = in.readUnsignedByte(); break;
                case 49: d.womanwear2 = in.readInt(); break;
                case 50: d.womanwear3 = in.readInt(); break;
                case 51: d.manhead = in.readInt(); break;
                case 52: d.manhead2 = in.readInt(); break;
                case 53: d.womanhead = in.readInt(); break;
                case 54: d.womanhead2 = in.readInt(); break;
                case 65: break; // stockmarket flag
                case 75: d.weight = in.readShort(); break;
                case 78: d.manwear3 = in.readUnsignedShort(); break;
                case 79: d.womanwear3 = in.readUnsignedShort(); break;
                case 90: d.manhead = in.readUnsignedShort(); break;
                case 91: d.womanhead = in.readUnsignedShort(); break;
                case 92: d.manhead2 = in.readUnsignedShort(); break;
                case 93: d.womanhead2 = in.readUnsignedShort(); break;
                case 94: in.readUnsignedShort(); break; // category
                case 95: in.readUnsignedShort(); break; // zan2d
                case 97: d.certlink = in.readUnsignedShort(); break;
                case 98: d.certtemplate = in.readUnsignedShort(); break;
                case 100: case 101: case 102: case 103: case 104:
                case 105: case 106: case 107: case 108: case 109:
                    in.readUnsignedShort(); in.readUnsignedShort(); break;
                case 110: d.resizeX = in.readUnsignedShort(); break;
                case 111: d.resizeY = in.readUnsignedShort(); break;
                case 112: d.resizeZ = in.readUnsignedShort(); break;
                case 113: d.ambient = in.readByte(); break;
                case 114: d.contrast = in.readByte(); break;
                case 115: d.team = in.readByte(); break;
                case 139: d.boughtlink = in.readUnsignedShort(); break;
                case 140: d.boughttemplate = in.readUnsignedShort(); break;
                case 148: d.placeholderlink = in.readUnsignedShort(); break;
                case 149: d.placeholdertemplate = in.readUnsignedShort(); break;
                case 160: break;
                case 200: {
                    int count = in.readUnsignedByte();
                    for (int i = 0; i < count; i++) in.readUnsignedShort();
                    break;
                }
                case 201: case 202: case 203: in.readInt(); break;
                case 204: case 205: in.readUnsignedShort(); break;
                case 206: d.tradeable = false; break;
                case 207: in.readUnsignedShort(); break;
                case 208: in.readByte(); break;
                case 209: in.readUnsignedShort(); break;
                case 210: d.transformlink = in.readUnsignedShort(); break;
                case 211: d.transformtemplate = in.readUnsignedShort(); break;
                case 212: in.readUnsignedByte(); break;
                case 249: {
                    int length = in.readUnsignedByte();
                    for (int i = 0; i < length; i++) {
                        boolean isString = in.readUnsignedByte() == 1;
                        in.read24BitInt();
                        if (isString) in.readString(); else in.readInt();
                    }
                    break;
                }
                default:
                    throw new RuntimeException("unrecognized obj opcode " + code + " for item " + id);
            }
        }
        return d;
    }
}

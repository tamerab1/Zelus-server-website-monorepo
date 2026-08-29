import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;

import java.io.File;
import java.util.Arrays;

public class FullAudit {
    static Store store;
    static Storage storage;
    static int missing = 0;

    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        try (Store s = new Store(new File(cachePath))) {
            store = s;
            store.load();
            storage = store.getStorage();

            System.out.println("=== ITEMS ===");
            checkMultiFile(ConfigType.ITEM.getId(), new int[]{
                33041,33089,33123,33140,33175,33273,33274,33391,33392,33427,33428,33496,33575,33697,33784,
                34026,34040,34044,34047,34302,34303,34304,60224,60225,60226,60228,60229,60230,60231,60232,
                60233,60234});

            System.out.println("=== NPCS ===");
            checkMultiFile(ConfigType.NPC.getId(), new int[]{
                30326,30332,30355,30383,30390,30440,30531,30532,30533,30535});

            System.out.println("=== SEQUENCES ===");
            checkMultiFile(ConfigType.SEQUENCE.getId(), new int[]{
                16014,16016,16017,16024,16025,16062,16063,16135,16136,16153,16154,16157,16158,16162,16163});

            System.out.println("=== MODELS ===");
            checkSingleFile(IndexType.MODELS, new int[]{
                62285,62286,62289,62290,62291,62292,62355,62401,62414,62415,62450,62451,62482,62488,62489,
                62708,62709,62710,62711,62712,62713,62714,62715,62716,63169,63170,63171,63178,63179,63180,
                63190,63191,63192,63193,63194,63230,63231,63603,63606,63820,63821,63975,63976,64055,64176,
                64177,64279,64280,64297,64312,64423,64428,64429,64430,64431,64432,64433,64482,64484,64496,
                64497,64498,64499,64500,64501,64685,64719,64720,64721,64919,64920,64921,64982,64983,64984,
                65003,65008,65009,65024,65025,65055,65150,65151,65152,65162,65163});

            System.out.println("=== SPRITES (main 25) ===");
            checkSingleFile(IndexType.SPRITES, new int[]{
                20357,20382,20393,20394,20395,20396,20397,20399,20400,20401,20402,20406,20411,20412,20416,
                20421,20425,20441,20445,20452,20453,20520,20534,20539,20557});

            System.out.println("=== SPRITES (remapped 900/901 -> 90000/90001) ===");
            checkSingleFile(IndexType.SPRITES, new int[]{90000, 90001});

            System.out.println("=== ANIMATIONS (frame archives) ===");
            checkSingleFile(IndexType.ANIMATIONS, new int[]{16004,16005,16011,16035,16048,16073,16075,16076});

            System.out.println("=== SKELETONS ===");
            checkSingleFile(IndexType.SKELETONS, new int[]{16004,16005,16011,16035,16048,16073,16075,16076});

            System.out.println("=== INTERFACE 5900 ===");
            checkSingleFile(IndexType.INTERFACES, new int[]{5900});

            System.out.println("=== TEXTURES (25 remapped to 129-153) ===");
            checkTextures(new int[]{129,130,131,132,133,134,135,136,137,138,139,140,141,142,143,144,145,
                146,147,148,149,150,151,152,153});

            System.out.println();
            System.out.println(missing == 0 ? "ALL CHECKS PASSED" : (missing + " MISSING ENTRIES FOUND"));
        }
    }

    static void checkMultiFile(int configId, int[] ids) throws Exception {
        Index index = store.getIndex(IndexType.CONFIGS);
        Archive archive = index.getArchive(configId);
        FileData[] fileData = archive.getFileData();
        java.util.Set<Integer> present = new java.util.HashSet<>();
        for (FileData fd : fileData) present.add(fd.getId());
        for (int id : ids) {
            if (!present.contains(id)) {
                System.out.println("  MISSING: " + id);
                missing++;
            }
        }
        System.out.println("  checked " + ids.length + " ids, archive has " + fileData.length + " total entries");
    }

    static void checkSingleFile(IndexType type, int[] ids) throws Exception {
        Index index = store.getIndex(type);
        for (int id : ids) {
            Archive a = index.getArchive(id);
            if (a == null) {
                System.out.println("  MISSING: " + id);
                missing++;
            }
        }
        System.out.println("  checked " + ids.length + " ids");
    }

    static void checkTextures(int[] ids) throws Exception {
        Index index = store.getIndex(IndexType.TEXTURES);
        Archive archive = index.getArchive(0);
        FileData[] fileData = archive.getFileData();
        java.util.Set<Integer> present = new java.util.HashSet<>();
        for (FileData fd : fileData) present.add(fd.getId());
        for (int id : ids) {
            if (!present.contains(id)) {
                System.out.println("  MISSING: " + id);
                missing++;
            }
        }
        System.out.println("  checked " + ids.length + " ids, archive has " + fileData.length + " total entries");
    }
}

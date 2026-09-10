import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.InterfaceDefinition;
import net.runelite.cache.definitions.loaders.InterfaceLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.ArchiveFiles;
import net.runelite.cache.fs.Container;
import net.runelite.cache.fs.FSFile;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.IndexData;

import java.io.File;
import java.lang.reflect.Field;

// Fixes the achievement popup's (interface group 849) reward-text overlap: components
// 142 (xp reward), 143 (achievement points reward), 144 (Zelus points reward) are stacked
// at originalY 275/286/297, each 15px tall -- an 11px step against a 15px-tall row, so
// consecutive rows overlap by 4px whenever the text wraps to more than one visual line.
//
// Confirmed via DiffInterfaceComponents.java that components 142/143/144 are byte-identical
// except a single byte at offset 7 (19/30/41 respectively == originalY - 256), so this is a
// pure single-byte Y-position edit -- no opcode/format guessing needed. Widens the step from
// 11 to 15 (row height, i.e. zero overlap, minimal footprint growth): 143 -> Y=290, 144 -> Y=305
// (142 stays at 275). Re-decodes with the real InterfaceLoader afterward and verifies every
// other field is unchanged before writing.
//
// Usage: <cachePath> [apply]
public class FixAchievementOverlap {
    static final int GROUP = 849;
    static final int[] CHILDREN = {143, 144};
    static final int[] NEW_Y = {290, 305};
    static final int Y_BYTE_OFFSET = 7;

    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        boolean apply = args.length > 1 && args[1].equals("apply");

        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Index index = store.getIndex(IndexType.INTERFACES);
            Archive archive = index.getArchive(GROUP);
            byte[] archiveData = storage.loadArchive(archive);
            ArchiveFiles files = archive.getFiles(archiveData);

            InterfaceLoader loader = new InterfaceLoader();
            int changed = 0;

            for (int i = 0; i < CHILDREN.length; i++) {
                int child = CHILDREN[i];
                int newY = NEW_Y[i];

                FSFile file = files.findFile(child);
                if (file == null) {
                    throw new IllegalStateException("component " + child + " not found -- ABORTING.");
                }

                byte[] before = file.getContents();
                int widgetId = (GROUP << 16) + child;
                InterfaceDefinition beforeDef = loader.load(widgetId, before);
                int expectedByte = beforeDef.originalY - 256;
                int actualByte = before[Y_BYTE_OFFSET] & 0xFF;
                if (expectedByte != actualByte) {
                    throw new IllegalStateException("component " + child + ": byte-offset assumption invalid "
                            + "(decoded originalY=" + beforeDef.originalY + ", expected byte " + expectedByte
                            + " at offset " + Y_BYTE_OFFSET + ", got " + actualByte + ") -- ABORTING.");
                }

                byte[] after = before.clone();
                after[Y_BYTE_OFFSET] = (byte) (newY - 256);

                InterfaceDefinition afterDef = loader.load(widgetId, after);
                if (afterDef.originalY != newY) {
                    throw new IllegalStateException("component " + child + ": originalY is " + afterDef.originalY
                            + " after edit, expected " + newY + " -- ABORTING.");
                }
                if (!allFieldsMatchExceptY(beforeDef, afterDef)) {
                    throw new IllegalStateException("component " + child + ": edit changed a field other than "
                            + "originalY -- ABORTING.");
                }
                if (after.length != before.length) {
                    throw new IllegalStateException("component " + child + ": byte length changed -- ABORTING.");
                }

                System.out.println("component " + child + ": originalY " + beforeDef.originalY + " -> "
                        + afterDef.originalY + ", all other fields verified unchanged.");

                file.setContents(after);
                changed++;
            }

            System.out.println("Prepared " + changed + " component edit(s).");
            if (!apply) {
                System.out.println("DRY RUN -- not writing. Re-run with 'apply' to persist.");
                return;
            }

            byte[] newArchiveData = files.saveContents();
            Container container = new Container(archive.getCompression(), -1);
            container.compress(newArchiveData, null);

            storage.store(index.getId(), archive.getArchiveId(), container.data);
            archive.setCrc(container.crc);
            archive.setRevision(archive.getRevision() + 1);
            archive.setCompressedSize(container.data.length);
            archive.setDecompressedSize(newArchiveData.length);

            IndexData indexData = index.toIndexData();
            byte[] rawIndex = indexData.writeIndexData();
            Container idxContainer = new Container(index.getCompression(), -1);
            idxContainer.compress(rawIndex, null);
            storage.store(255, index.getId(), idxContainer.data);
            index.setCrc(idxContainer.crc);

            System.out.println("APPLY complete. Interface group " + GROUP + " revision now "
                    + archive.getRevision() + ".");
        }
    }

    // Compares every public field on InterfaceDefinition reflectively except originalY.
    static boolean allFieldsMatchExceptY(InterfaceDefinition a, InterfaceDefinition b) throws Exception {
        for (Field f : InterfaceDefinition.class.getFields()) {
            if (f.getName().equals("originalY")) continue;
            Object va = f.get(a);
            Object vb = f.get(b);
            boolean eq;
            if (va == null || vb == null) {
                eq = va == vb;
            } else if (va.getClass().isArray()) {
                eq = arraysDeepEqual(va, vb);
            } else {
                eq = va.equals(vb);
            }
            if (!eq) {
                System.out.println("  field mismatch: " + f.getName() + " before=" + describe(va) + " after=" + describe(vb));
                return false;
            }
        }
        return true;
    }

    static boolean arraysDeepEqual(Object a, Object b) {
        if (a instanceof int[] && b instanceof int[]) return java.util.Arrays.equals((int[]) a, (int[]) b);
        if (a instanceof Object[] && b instanceof Object[]) return java.util.Arrays.deepEquals((Object[]) a, (Object[]) b);
        return java.util.Objects.equals(a, b);
    }

    static String describe(Object o) {
        if (o == null) return "null";
        if (o.getClass().isArray()) {
            if (o instanceof int[]) return java.util.Arrays.toString((int[]) o);
            if (o instanceof Object[]) return java.util.Arrays.toString((Object[]) o);
        }
        return o.toString();
    }
}

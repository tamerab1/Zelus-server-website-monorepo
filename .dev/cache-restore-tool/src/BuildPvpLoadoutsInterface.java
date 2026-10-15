import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.InterfaceDefinition;
import net.runelite.cache.definitions.loaders.InterfaceLoader;
import net.runelite.cache.fs.*;
import net.runelite.cache.index.FileData;
import net.runelite.cache.index.IndexData;

import java.io.File;
import java.lang.reflect.Field;
import java.util.*;

// Builds interface group 1156, "PvP Loadouts" (GearLoadoutInterface / PvpLoadoutView, server): a copy of the regular
// Equipment Presets screen 5900 (same layout and component ids) in a combat theme -- charcoal steel panels, crimson
// lines and buttons, crimson-orange headers -- with the PvP additions:
//  - the 7 combat level lines (20-26) are clickable ("Edit level"), for each loadout's own levels;
//  - a "Battle Presets" switch button next to the close button (appended: 132 box, 133 label).
// The resizable 1157 is then made with ScaleInterface (1156 -> 1157, as 1142 is made from 5900).
// Untouched components keep their original bytes; edited ones must read back identical. Usage: verify|apply|rebuild <cache>
public class BuildPvpLoadoutsInterface {
    static final int SRC = 5900, GROUP = 1156;
    /** rect / text colour remap (old RGB -> new RGB). */
    static final Map<Integer, Integer> COLOURS = Map.of(
            0x10161d, 0x141214,   // window background
            0x17212b, 0x1e1a1b,   // side columns
            0x130a20, 0x1d1416,   // preview column
            0x101820, 0x161213,   // equipment panel
            0x3e3529, 0x3c2525,   // buttons -> crimson-tinted steel
            0x5d4f3e, 0x8c2323,   // divider / outlines -> crimson
            0xe6b450, 0xff6a4a);  // gold headers -> crimson-orange
    static final int TITLE = 5, FIRST_LEVEL_LINE = 20, LEVEL_LINES = 7;
    static final int BOX = 0x3c2525, BOX_HOVER = 0x5a3030;

    public static void main(String[] args) throws Exception {
        boolean rebuild = args[0].equals("rebuild");
        boolean apply = args[0].equals("apply") || rebuild;
        try (Store store = new Store(new File(args[1]))) {
            store.load();
            Storage storage = store.getStorage();
            Index index = store.getIndex(IndexType.INTERFACES);
            if (index.getArchive(GROUP) != null && !rebuild)
                throw new IllegalStateException("interface " + GROUP + " already exists (use rebuild)");
            Archive srcArchive = index.getArchive(SRC);
            ArchiveFiles src = srcArchive.getFiles(storage.loadArchive(srcArchive));
            InterfaceLoader loader = new InterfaceLoader();

            List<byte[]> comps = new ArrayList<>();
            int max = 0;
            for (FileData fd : srcArchive.getFileData())
                max = Math.max(max, fd.getId());
            int edited = 0;
            InterfaceDefinition textTemplate = null, rectTemplate = null;
            for (int c = 0; c <= max; c++) {
                FSFile file = src.findFile(c);
                if (file == null)
                    throw new IllegalStateException("5900 has a gap at " + c);
                byte[] bytes = file.getContents();
                InterfaceDefinition d = loader.load((GROUP << 16) | c, bytes);
                // parent ids are absolute (group << 16 | child): re-point them at the new group
                boolean changed = d.parentId != -1;
                if (changed)
                    d.parentId = (GROUP << 16) | (d.parentId & 0xffff);
                if ((d.type == 3 || d.type == 4) && COLOURS.containsKey(d.textColor & 0xffffff)) {
                    d.textColor = COLOURS.get(d.textColor & 0xffffff);
                    changed = true;
                }
                if (c == TITLE) {
                    d.text = "PvP Loadouts";
                    changed = true;
                }
                if (c >= FIRST_LEVEL_LINE && c < FIRST_LEVEL_LINE + LEVEL_LINES) {
                    d.clickMask = 2;
                    d.actions = new String[]{"Edit level"};
                    changed = true;
                }
                if (d.type == 4 && textTemplate == null)
                    textTemplate = d;
                if (d.type == 3 && d.filled && rectTemplate == null)
                    rectTemplate = d;
                if (changed) {
                    if (!d.isIf3)
                        throw new IllegalStateException("5900:" + c + " is not IF3");
                    byte[] encoded = BuildBondsInterface.encodeIf3(d);
                    InterfaceDefinition back = loader.load(d.id, encoded);
                    for (Field f : InterfaceDefinition.class.getFields()) {
                        if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                        if (!Objects.deepEquals(f.get(back), f.get(d)))
                            throw new IllegalStateException("5900:" + c + " " + f.getName() + " does not round-trip");
                    }
                    bytes = encoded;
                    edited++;
                }
                comps.add(bytes);
            }

            // appended: the "Battle Presets" switch (box + label), left of the close button
            InterfaceDefinition box = BuildBondsInterface.copy(rectTemplate);
            place(box, 392, 7, 92, 16);
            box.textColor = BOX;
            box.filled = true;
            box.opacity = 0;
            box.clickMask = 2;
            box.actions = new String[]{"Battle Presets"};
            box.onMouseOverListener = new Object[]{45, -2147483645, BOX_HOVER};
            box.onMouseLeaveListener = new Object[]{45, -2147483645, BOX};
            box.hasListener = true;
            box.id = (GROUP << 16) | comps.size();
            comps.add(BuildBondsInterface.encodeIf3(box));
            InterfaceDefinition label = BuildBondsInterface.copy(textTemplate);
            place(label, 392, 7, 92, 16);
            label.text = "Battle Presets";
            label.textColor = 0xff6a4a;
            label.fontId = 494;
            label.xTextAlignment = 1;
            label.yTextAlignment = 1;
            label.id = (GROUP << 16) | comps.size();
            comps.add(BuildBondsInterface.encodeIf3(label));
            System.out.println("copied 5900 -> " + GROUP + ": " + comps.size() + " components, " + edited + " restyled, battle presets = "
                    + (comps.size() - 2) + "/" + (comps.size() - 1));

            if (!apply) {
                System.out.println("VERIFY OK -- nothing written.");
                return;
            }
            BuildSpinnerInterface.write(storage, index, srcArchive, comps, GROUP);
            System.out.println("APPLY complete -- interface " + GROUP + ".");
        }
    }

    static void place(InterfaceDefinition d, int x, int y, int w, int h) {
        d.parentId = (GROUP << 16);
        d.originalX = x;
        d.originalY = y;
        d.originalWidth = w;
        d.originalHeight = h;
        d.widthMode = d.heightMode = d.xPositionMode = d.yPositionMode = 0;
        d.isHidden = false;
        d.clickMask = 0;
        d.actions = null;
        d.name = "";
        d.targetVerb = "";
        d.onMouseOverListener = d.onMouseLeaveListener = null;
        d.hasListener = false;
    }
}

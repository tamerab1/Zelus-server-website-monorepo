import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.InterfaceDefinition;
import net.runelite.cache.definitions.loaders.InterfaceLoader;
import net.runelite.cache.fs.*;

import java.io.File;
import java.lang.reflect.Field;
import java.util.*;

// Builds interface group 1159, the Wilderness Loot Key window (LootKeyInterface.java, server): what a key holds, its
// total value, and Bank All Loot / Take to Inventory. Same frame / warm-stone theme as the Relic Forge (1158):
//  - sub-header: "Total Loot Value: N gp";
//  - one panel with 50 item cells (10 x 5: fill, border, item with Examine + a hover name from clientscript 10752);
//  - an info line, then two framed buttons.
// Encoder/verification shared with BuildWorkbenchInterface. Prints "CONST name = id" lines. Usage: verify|apply|rebuild <cachePath>
public class BuildLootKeyInterface {
    static final int GROUP = 1159, SRC = 1002;
    static final int TEXT = 0xd8d0c0, GREY = 0x9a9a9a;
    static final int TEXT_FONT = 494, TITLE_Y = 14, SUB_Y = 45;
    static final int SLOT_FILL = 0x2a2520, SLOT_BORDER = 0x5a4d3a;
    static final int COLS = 10, ROWS = 5;
    static final int COMPONENTS = 165;

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
            List<InterfaceDefinition> want = new ArrayList<>();
            Map<String, Integer> ids = new LinkedHashMap<>();
            for (int c = 0; c <= 2; c++) {
                InterfaceDefinition d = loader.load((GROUP << 16) | c, src.findFile(c).getContents());
                if (d.parentId != -1)
                    d.parentId = (GROUP << 16) | (d.parentId & 0xffff);
                BuildRelicForgeInterface.add(comps, want, d);
            }
            InterfaceDefinition title = loader.load((GROUP << 16) | 3, src.findFile(3).getContents());
            title.parentId = (GROUP << 16) | (title.parentId & 0xffff);
            title.originalY = TITLE_Y;
            BuildRelicForgeInterface.add(comps, want, title);
            InterfaceDefinition layerT = loader.load(0, src.findFile(153).getContents());
            InterfaceDefinition[] rectT = {loader.load(0, src.findFile(159).getContents()),
                    loader.load(0, src.findFile(160).getContents()), loader.load(0, src.findFile(161).getContents())};
            InterfaceDefinition textT = loader.load(0, src.findFile(144).getContents());
            InterfaceDefinition buttonT = loader.load(0, src.findFile(169).getContents());
            InterfaceDefinition labelT = loader.load(0, src.findFile(170).getContents());
            InterfaceDefinition rect = rectT[1];

            ids.put("SUB_HEADER", comps.size());
            InterfaceDefinition sub = text(textT, 0, 21, SUB_Y, 473, 13, TEXT, "");
            sub.xTextAlignment = 1;
            BuildRelicForgeInterface.add(comps, want, sub);

            // item panel: 10 x 5 cells, 46 x 40 pitch
            int panel = BuildWorkbenchInterface.panel(comps, want, layerT, rectT, 0, 24, 62, 464, 205);
            fix(want, comps, panel);
            ids.put("FIRST_CELL", comps.size());
            for (int r = 0; r < ROWS; r++) {
                for (int c = 0; c < COLS; c++) {
                    int x = 4 + c * 46, y = 3 + r * 40;
                    BuildRelicForgeInterface.add(comps, want, place(BuildRelicForgeInterface.solid(rect, 0, 0, 0, 0, 0, SLOT_FILL), panel, x + 1, y + 1, 42, 36));
                    InterfaceDefinition border = BuildRelicForgeInterface.outline(rect, 0, 0, 0, 0, 0, SLOT_BORDER, false);
                    BuildRelicForgeInterface.add(comps, want, place(border, panel, x + 1, y + 1, 42, 36));
                    InterfaceDefinition item = BuildRelicForgeInterface.itemSlot(buttonT, 0, 0, 0, 0, 0, "Examine");
                    BuildRelicForgeInterface.add(comps, want, place(item, panel, x + 4, y + 3, 36, 32));
                }
            }
            ids.put("INFO", comps.size());
            InterfaceDefinition info = text(textT, 0, 24, 270, 464, 13, GREY, "");
            info.xTextAlignment = 1;
            BuildRelicForgeInterface.add(comps, want, info);
            ids.put("BANK_ALL", comps.size());
            button(comps, want, buttonT, labelT, 66, 285, 176, 25, "Bank All Loot");
            ids.put("TAKE", comps.size());
            button(comps, want, buttonT, labelT, 270, 285, 176, 25, "Take to Inventory");

            if (comps.size() != COMPONENTS)
                throw new IllegalStateException("component count " + comps.size() + " != " + COMPONENTS);
            for (Map.Entry<String, Integer> e : ids.entrySet())
                System.out.println("CONST " + e.getKey() + " = " + e.getValue());
            for (int c = 0; c < comps.size(); c++) {
                InterfaceDefinition got = loader.load((GROUP << 16) | c, comps.get(c));
                InterfaceDefinition exp = want.get(c);
                exp.id = (GROUP << 16) | c;
                for (Field f : InterfaceDefinition.class.getFields()) {
                    if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                    if (!Objects.deepEquals(f.get(got), f.get(exp)))
                        throw new IllegalStateException("component " + c + ": " + f.getName() + " " + f.get(got) + " != " + f.get(exp));
                }
            }
            if (!apply) {
                System.out.println("VERIFY OK -- " + comps.size() + " components, nothing written.");
                return;
            }
            BuildSpinnerInterface.write(storage, index, srcArchive, comps, GROUP);
            System.out.println("APPLY complete -- interface " + GROUP + " with " + comps.size() + " components.");
        }
    }

    /** BuildWorkbenchInterface.panel places its 5 comps under group 1153: re-point them at this group. */
    static void fix(List<InterfaceDefinition> want, List<byte[]> comps, int first) {
        for (int c = first; c < first + 5; c++) {
            InterfaceDefinition d = want.get(c);
            d.parentId = (GROUP << 16) | (d.parentId & 0xffff);
            comps.set(c, BuildBondsInterface.encodeIf3(d));
        }
    }

    /** BuildRelicForgeInterface's helpers place under group 1158: re-place under this group. */
    static InterfaceDefinition place(InterfaceDefinition d, int parent, int x, int y, int w, int h) {
        d.parentId = (GROUP << 16) | parent;
        d.originalX = x;
        d.originalY = y;
        d.originalWidth = w;
        d.originalHeight = h;
        return d;
    }

    static InterfaceDefinition text(InterfaceDefinition textT, int parent, int x, int y, int w, int h, int colour, String s) {
        InterfaceDefinition d = BuildRelicForgeInterface.text(textT, 0, 0, 0, 0, 0, TEXT_FONT, colour, s);
        return place(d, parent, x, y, w, h);
    }

    static void button(List<byte[]> comps, List<InterfaceDefinition> want, InterfaceDefinition buttonT, InterfaceDefinition labelT,
                       int x, int y, int w, int h, String action) {
        InterfaceDefinition b = BuildRelicForgeInterface.copy(buttonT);
        BuildRelicForgeInterface.place(b, 0, 0, 0, 0, 0);
        place(b, 0, x, y, w, h);
        b.clickMask = 2;
        b.actions = new String[]{action};
        b.onMouseOverListener = new Object[]{44, -2147483645, 24470};
        b.onMouseLeaveListener = new Object[]{44, -2147483645, 4525};
        b.hasListener = true;
        BuildRelicForgeInterface.add(comps, want, b);
        InterfaceDefinition l = BuildRelicForgeInterface.copy(labelT);
        BuildRelicForgeInterface.place(l, 0, 0, 0, 0, 0);
        place(l, 0, x + 1, y + 2, w - 2, h - 4);
        l.text = action;
        BuildRelicForgeInterface.add(comps, want, l);
    }
}

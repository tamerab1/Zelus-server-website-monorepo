import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.InterfaceDefinition;
import net.runelite.cache.definitions.loaders.InterfaceLoader;
import net.runelite.cache.fs.*;
import net.runelite.cache.index.FileData;

import java.io.File;
import java.util.*;

// Zelus Shops (interface 891), 2026-10-21: the active category tab becomes a sunken stone slot instead of the 1px gold
// frame (AddShopTabFrames 114-129, which the server now leaves hidden). The tab icons (42-47, 112, 113) are drawn after
// the tab background and anything appended is drawn after them, so each tab gets a complete hidden "pressed" overlay:
//   +0 recess fill 0x2D271E (opaque: it covers the raised background and the original icon)
//   +1 inner backdrop 0x383023 (3..33)
//   +2..+9 the OSRS pressed stone frame (sprites 929-936: corners TL/TR/BL/BR, edges L/T/R/B, as IfKit.stoneFrame)
//   +10 a copy of the tab's icon (same sprite and position), back on top
// Appended after the Deals button (0-131 kept byte-identical): tab t -> 132 + 11t. The hover highlight (170 -> 179)
// stays on the raised tabs. Usage: verify|apply|rebuild <cachePath>
public class AddShopTabRecess {
    static final int GROUP = 891, KEEP = 132, TABS = 8, FIRST_TAB = 16, PER_TAB = 11;
    static final int[] ICONS = {42, 43, 44, 45, 46, 47, 112, 113};
    static final int[] PRESSED = {929, 930, 931, 932, 933, 934, 935, 936};
    static final int RECESS = 0x2D271E, BACKDROP = 0x383023, SIZE = 36;

    public static void main(String[] args) throws Exception {
        boolean rebuild = args[0].equals("rebuild");
        boolean apply = args[0].equals("apply") || rebuild;
        try (Store store = new Store(new File(args[1]))) {
            store.load();
            Storage storage = store.getStorage();
            Index index = store.getIndex(IndexType.INTERFACES);
            Archive archive = index.getArchive(GROUP);
            ArchiveFiles files = archive.getFiles(storage.loadArchive(archive));
            InterfaceLoader loader = new InterfaceLoader();
            int max = 0;
            for (FileData fd : archive.getFileData())
                max = Math.max(max, fd.getId());
            if (max >= KEEP && !rebuild)
                throw new IllegalStateException(GROUP + " already has " + (max + 1) + " components (use rebuild)");
            if (max < KEEP - 1)
                throw new IllegalStateException(GROUP + " has only " + (max + 1) + " components: the Deals button (130/131) is missing");
            List<byte[]> comps = new ArrayList<>();
            for (int c = 0; c < KEEP; c++) {
                FSFile f = files.findFile(c);
                if (f == null)
                    throw new IllegalStateException(GROUP + " has a gap at " + c);
                comps.add(f.getContents());
            }
            InterfaceDefinition rectT = loader.load((GROUP << 16) | 114, comps.get(114));
            InterfaceDefinition spriteT = loader.load((GROUP << 16) | 17, comps.get(17));
            check(rectT.type == 3 && spriteT.type == 5, "templates 114 / 17 are not a rect / sprite");
            for (int t = 0; t < TABS; t++) {
                int tab = FIRST_TAB + 2 * t;
                InterfaceDefinition icon = loader.load((GROUP << 16) | ICONS[t], comps.get(ICONS[t]));
                check(icon.type == 5 && (icon.parentId & 0xffff) == tab, "icon " + ICONS[t] + " is not tab " + tab + "'s sprite");
                add(comps, loader, rect(rectT, tab, 0, 0, SIZE, SIZE, RECESS));
                add(comps, loader, rect(rectT, tab, 3, 3, SIZE - 6, SIZE - 6, BACKDROP));
                int[][] pieces = {{0, 0, 9, 9}, {SIZE - 9, 0, 9, 9}, {0, SIZE - 9, 9, 9}, {SIZE - 9, SIZE - 9, 9, 9},
                    {0, 9, 9, SIZE - 18}, {9, 0, SIZE - 18, 9}, {SIZE - 9, 9, 9, SIZE - 18}, {9, SIZE - 9, SIZE - 18, 9}};
                for (int p = 0; p < 8; p++)
                    add(comps, loader, sprite(spriteT, tab, pieces[p], PRESSED[p], p >= 4));
                InterfaceDefinition copy = BuildBondsInterface.copy(icon);
                copy.isHidden = true;
                copy.clickMask = 0;
                copy.actions = null;
                copy.onMouseOverListener = copy.onMouseLeaveListener = null;
                copy.hasListener = false;
                add(comps, loader, copy);
            }
            System.out.println(GROUP + ": " + comps.size() + " components (tab recesses " + KEEP + "-" + (comps.size() - 1)
                + ", tab t -> " + KEEP + " + " + PER_TAB + "t)");
            if (!apply) {
                System.out.println("VERIFY OK -- nothing written.");
                return;
            }
            BuildSpinnerInterface.write(storage, index, archive, comps, GROUP);
            System.out.println("APPLY complete -- interface " + GROUP + ".");
        }
    }

    static InterfaceDefinition rect(InterfaceDefinition t, int parent, int x, int y, int w, int h, int colour) {
        InterfaceDefinition r = base(t, parent, x, y, w, h);
        r.filled = true;
        r.textColor = colour;
        r.opacity = 0;
        return r;
    }

    static InterfaceDefinition sprite(InterfaceDefinition t, int parent, int[] box, int spriteId, boolean tiled) {
        InterfaceDefinition s = base(t, parent, box[0], box[1], box[2], box[3]);
        s.spriteId = spriteId;
        s.spriteTiling = tiled;
        s.borderType = 0;
        s.shadowColor = 0;
        return s;
    }

    static InterfaceDefinition base(InterfaceDefinition t, int parent, int x, int y, int w, int h) {
        InterfaceDefinition d = BuildBondsInterface.copy(t);
        d.parentId = (GROUP << 16) | parent;
        d.originalX = x;
        d.originalY = y;
        d.originalWidth = w;
        d.originalHeight = h;
        d.widthMode = d.heightMode = d.xPositionMode = d.yPositionMode = 0;
        d.isHidden = true;
        d.clickMask = 0;
        d.actions = null;
        d.onMouseOverListener = d.onMouseLeaveListener = null;
        d.hasListener = false;
        return d;
    }

    static void add(List<byte[]> comps, InterfaceLoader loader, InterfaceDefinition d) {
        add(comps, loader, d, true);
    }

    /** Appends d as the next component (also used by AddShopTabStone, whose raised frames are visible). */
    static void add(List<byte[]> comps, InterfaceLoader loader, InterfaceDefinition d, boolean hidden) {
        d.id = (GROUP << 16) | comps.size();
        byte[] encoded = BuildBondsInterface.encodeIf3(d);
        InterfaceDefinition back = loader.load(d.id, encoded);
        check(back.parentId == d.parentId && back.isHidden == hidden && d.isHidden == hidden && back.originalX == d.originalX && back.originalY == d.originalY
            && back.originalWidth == d.originalWidth && back.originalHeight == d.originalHeight && back.spriteId == d.spriteId
            && back.textColor == d.textColor, "component " + comps.size() + " does not round-trip");
        comps.add(encoded);
    }

    static void check(boolean ok, String msg) {
        if (!ok) throw new IllegalStateException(msg);
    }
}

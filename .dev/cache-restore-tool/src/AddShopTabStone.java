import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.InterfaceDefinition;
import net.runelite.cache.definitions.loaders.InterfaceLoader;
import net.runelite.cache.fs.*;
import net.runelite.cache.index.FileData;

import java.io.File;
import java.util.*;

// Zelus Shops (interface 891), 2026-10-21 round 2: every category tab gets the riveted OSRS stone-button frame (the
// look the user liked on the pressed tab), not just the active one. The legacy 36x36 bezel background (17, 19 .. 31)
// and icon sit under it; anything appended draws after them, so each tab gets a complete raised overlay, shown while
// the tab is NOT selected:
//   +0 stone fill 0x524b40 (hover lifts it to 0x5f5749)
//   +1..+8 the raised stone frame (sprites 913-920, as IfKit.stoneFrame)
//   +9 a copy of the tab's icon on top
//   +10 hidden soft amber bevel line (0xb08d4a, 2px in), shown on the SELECTED tab over its sunken slot
// The sunken slot itself is AddShopTabRecess (132 + 11t); appended after it here: tab t -> 220 + 11t.
// Keeps 0-219 byte-identical. Usage: verify|apply|rebuild <cachePath>
public class AddShopTabStone {
    static final int GROUP = 891, KEEP = 220, TABS = 8, FIRST_TAB = 16, PER_TAB = 11, SIZE = 36;
    static final int[] ICONS = {42, 43, 44, 45, 46, 47, 112, 113};
    static final int[] RAISED = {913, 914, 915, 916, 917, 918, 919, 920};
    static final int FILL = 0x524b40, HOVER = 0x5f5749, AMBER = 0xb08d4a;

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
            AddShopTabRecess.check(max >= KEEP - 1, GROUP + " has only " + (max + 1) + " components: run AddShopTabRecess first");
            List<byte[]> comps = new ArrayList<>();
            for (int c = 0; c < KEEP; c++) {
                FSFile f = files.findFile(c);
                AddShopTabRecess.check(f != null, GROUP + " has a gap at " + c);
                comps.add(f.getContents());
            }
            InterfaceDefinition rectT = loader.load((GROUP << 16) | 114, comps.get(114));
            InterfaceDefinition spriteT = loader.load((GROUP << 16) | 17, comps.get(17));
            int[][] pieces = {{0, 0, 9, 9}, {SIZE - 9, 0, 9, 9}, {0, SIZE - 9, 9, 9}, {SIZE - 9, SIZE - 9, 9, 9},
                {0, 9, 9, SIZE - 18}, {9, 0, SIZE - 18, 9}, {SIZE - 9, 9, 9, SIZE - 18}, {9, SIZE - 9, SIZE - 18, 9}};
            for (int t = 0; t < TABS; t++) {
                int tab = FIRST_TAB + 2 * t;
                InterfaceDefinition fill = AddShopTabRecess.rect(rectT, tab, 0, 0, SIZE, SIZE, FILL);
                fill.isHidden = false;
                fill.onMouseOverListener = new Object[]{45, -2147483645, HOVER};
                fill.onMouseLeaveListener = new Object[]{45, -2147483645, FILL};
                fill.hasListener = true;
                AddShopTabRecess.add(comps, loader, fill, false);
                for (int p = 0; p < 8; p++) {
                    InterfaceDefinition s = AddShopTabRecess.sprite(spriteT, tab, pieces[p], RAISED[p], p >= 4);
                    s.isHidden = false;
                    AddShopTabRecess.add(comps, loader, s, false);
                }
                InterfaceDefinition icon = loader.load((GROUP << 16) | ICONS[t], comps.get(ICONS[t]));
                AddShopTabRecess.check(icon.type == 5 && (icon.parentId & 0xffff) == tab, "icon " + ICONS[t] + " is not tab " + tab + "'s sprite");
                InterfaceDefinition copy = BuildBondsInterface.copy(icon);
                copy.isHidden = false;
                copy.clickMask = 0;
                copy.actions = null;
                copy.onMouseOverListener = copy.onMouseLeaveListener = null;
                copy.hasListener = false;
                AddShopTabRecess.add(comps, loader, copy, false);
                InterfaceDefinition trim = AddShopTabRecess.base(rectT, tab, 2, 2, SIZE - 4, SIZE - 4);
                trim.filled = false;
                trim.textColor = AMBER;
                trim.opacity = 0;
                AddShopTabRecess.add(comps, loader, trim, true);
            }
            System.out.println(GROUP + ": " + comps.size() + " components (raised stone tabs " + KEEP + "-" + (comps.size() - 1)
                + ", tab t -> " + KEEP + " + " + PER_TAB + "t)");
            if (!apply) {
                System.out.println("VERIFY OK -- nothing written.");
                return;
            }
            BuildSpinnerInterface.write(storage, index, archive, comps, GROUP);
            System.out.println("APPLY complete -- interface " + GROUP + ".");
        }
    }
}

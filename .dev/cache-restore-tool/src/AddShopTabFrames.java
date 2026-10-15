import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.InterfaceDefinition;
import net.runelite.cache.definitions.loaders.InterfaceLoader;
import net.runelite.cache.fs.*;
import net.runelite.cache.index.FileData;

import java.io.File;
import java.util.*;

// Zelus Shops (interface 891): a persistent "selected" frame on the shop category tabs. The tab backgrounds (17, 19 ..
// 31) swap sprite 170 -> 179 on hover and their leave listener always puts 170 back, so the active tab can't be marked
// through its background. This appends a hidden 2px gold frame (outer 36x36 + inner 34x34 rect) inside each of the 8
// tab layers (16, 18 .. 30), drawn over the icon; CustomShopInterface2 shows the active tab's frame.
// Appended ids: tab t -> 114 + 2t (outer), 115 + 2t (inner). Usage: verify|apply|rebuild <cachePath>
public class AddShopTabFrames {
    static final int GROUP = 891, FIRST_NEW = 114, TABS = 8, FIRST_TAB = 16, GOLD = 0xffb83f;

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
            if (max >= FIRST_NEW && !rebuild)
                throw new IllegalStateException(GROUP + " already has " + (max + 1) + " components (use rebuild)");
            List<byte[]> comps = new ArrayList<>();
            for (int c = 0; c < FIRST_NEW; c++) {
                FSFile f = files.findFile(c);
                if (f == null)
                    throw new IllegalStateException(GROUP + " has a gap at " + c);
                comps.add(f.getContents());
            }
            InterfaceDefinition rectT = loader.load((GROUP << 16) | 40, files.findFile(40).getContents());
            if (rectT.type != 3)
                throw new IllegalStateException("891:40 is not a rect");
            for (int t = 0; t < TABS; t++) {
                int tab = FIRST_TAB + 2 * t;
                for (int k = 0; k < 2; k++) {
                    InterfaceDefinition r = BuildBondsInterface.copy(rectT);
                    r.id = (GROUP << 16) | comps.size();
                    r.parentId = (GROUP << 16) | tab;
                    r.originalX = k;
                    r.originalY = k;
                    r.originalWidth = 36 - 2 * k;
                    r.originalHeight = 36 - 2 * k;
                    r.widthMode = r.heightMode = r.xPositionMode = r.yPositionMode = 0;
                    r.filled = false;
                    r.textColor = GOLD;
                    r.opacity = 0;
                    r.isHidden = true;
                    r.clickMask = 0;
                    r.actions = null;
                    r.onMouseOverListener = r.onMouseLeaveListener = null;
                    r.hasListener = false;
                    byte[] encoded = BuildBondsInterface.encodeIf3(r);
                    InterfaceDefinition back = loader.load(r.id, encoded);
                    if (back.parentId != r.parentId || back.textColor != GOLD || !back.isHidden || back.filled)
                        throw new IllegalStateException("frame " + comps.size() + " does not round-trip");
                    comps.add(encoded);
                }
            }
            System.out.println(GROUP + ": " + comps.size() + " components (tab frames " + FIRST_NEW + "-" + (comps.size() - 1) + ")");
            if (!apply) {
                System.out.println("VERIFY OK -- nothing written.");
                return;
            }
            BuildSpinnerInterface.write(storage, index, archive, comps, GROUP);
            System.out.println("APPLY complete -- interface " + GROUP + ".");
        }
    }
}

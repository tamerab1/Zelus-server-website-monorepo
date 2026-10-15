import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.InterfaceDefinition;
import net.runelite.cache.definitions.loaders.InterfaceLoader;
import net.runelite.cache.fs.*;
import net.runelite.cache.index.FileData;

import java.io.File;
import java.util.*;

// Zelus Shops (interface 891): appends a hidden "Deals" bezel button (130) + its label (131) in the title row, right
// of the title and left of the close button. CustomShopInterface2 shows it while the Donation Shop tab is active and
// it opens the Donator Deals screen (1166). Templates: 1002:169 (button) / 1002:170 (label), same as the builders.
// Keeps 0-129 byte-identical. Usage: verify|apply|rebuild <cachePath>
public class AddShopDealsButton {
    static final int GROUP = 891, FIRST_NEW = 130, X = 392, Y = 14, W = 70, H = 24;

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
            if (max < FIRST_NEW - 1)
                throw new IllegalStateException(GROUP + " has only " + (max + 1) + " components -- run AddShopTabFrames first");
            List<byte[]> comps = new ArrayList<>();
            for (int c = 0; c < FIRST_NEW; c++) {
                FSFile f = files.findFile(c);
                if (f == null)
                    throw new IllegalStateException(GROUP + " has a gap at " + c);
                comps.add(f.getContents());
            }
            Archive src = index.getArchive(1002);
            ArchiveFiles srcFiles = src.getFiles(storage.loadArchive(src));
            InterfaceDefinition title = loader.load((GROUP << 16) | 14, files.findFile(14).getContents());

            InterfaceDefinition button = BuildBondsInterface.copy(loader.load(0, srcFiles.findFile(169).getContents()));
            place(button, title.parentId, X, Y, W, H);
            button.clickMask = 2;
            button.actions = new String[]{"View deals"};
            button.onMouseOverListener = new Object[]{44, -2147483645, 24470};
            button.onMouseLeaveListener = new Object[]{44, -2147483645, 4525};
            button.hasListener = true;
            InterfaceDefinition label = BuildBondsInterface.copy(loader.load(0, srcFiles.findFile(170).getContents()));
            place(label, title.parentId, X + 1, Y + 2, W - 2, H - 4);
            label.text = "Deals";
            label.textColor = 0xffc35a;

            for (InterfaceDefinition d : new InterfaceDefinition[]{button, label}) {
                d.id = (GROUP << 16) | comps.size();
                byte[] encoded = BuildBondsInterface.encodeIf3(d);
                InterfaceDefinition back = loader.load(d.id, encoded);
                if (back.parentId != d.parentId || !back.isHidden || back.originalX != d.originalX)
                    throw new IllegalStateException("component " + comps.size() + " does not round-trip");
                comps.add(encoded);
            }
            System.out.println(GROUP + ": " + comps.size() + " components (Deals button " + FIRST_NEW + ", label " + (FIRST_NEW + 1) + ")");
            if (!apply) {
                System.out.println("VERIFY OK -- nothing written.");
                return;
            }
            BuildSpinnerInterface.write(storage, index, archive, comps, GROUP);
            System.out.println("APPLY complete -- interface " + GROUP + ".");
        }
    }

    static void place(InterfaceDefinition d, int parentId, int x, int y, int w, int h) {
        d.parentId = parentId;
        d.originalX = x;
        d.originalY = y;
        d.originalWidth = w;
        d.originalHeight = h;
        d.widthMode = d.heightMode = d.xPositionMode = d.yPositionMode = 0;
        d.isHidden = true;
        d.clickMask = 0;
        d.actions = null;
        d.name = "";
        d.targetVerb = "";
        d.onMouseOverListener = d.onMouseLeaveListener = null;
        d.hasListener = false;
    }
}

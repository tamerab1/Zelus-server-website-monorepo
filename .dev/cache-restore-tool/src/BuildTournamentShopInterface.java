import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.InterfaceDefinition;
import net.runelite.cache.definitions.loaders.InterfaceLoader;
import net.runelite.cache.fs.*;
import net.runelite.cache.index.FileData;
import net.runelite.cache.index.IndexData;

import java.io.File;
import java.lang.reflect.Field;
import java.util.*;

// Builds interface group 1149, the Pit Tournament Shop (TournamentShop.java, server): Wilderness Forge frame
// (1002:0-3), a points line under the title, and a framed, scrolling grid of 24 item cards (4 x 6, 2 rows visible). Each card: a layer with an
// inset 3-rect frame, a centred item model (ops Value / Buy 1 / Buy 5 / Examine), the name, the cost and a Buy
// button (ops Buy 1 / Buy 5 / Buy 10). Same encoder/verification as BuildBondsInterface.
// Usage: verify|apply|rebuild <cachePath>
public class BuildTournamentShopInterface {
    static final int GROUP = 1149, SRC = 1002;
    static final int GOLD = 0xffc35a, TEXT = 0xd8d0c0, TEXT_FONT = 494;
    static final int GX = 24, GY = 64, GW = 464, GH = 241, TITLE_Y = 12;
    static final int CARDS = 24, COLS = 4, CW = 107, CH = 115, GAP = 4;
    /** Scroll container inside the grid frame (2 rows visible); the scrollbar strip sits to its right. */
    static final int SW = 440, SH = 235;

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
            for (int c = 0; c <= 2; c++) {
                byte[] b = src.findFile(c).getContents();
                comps.add(b);
                want.add(loader.load((GROUP << 16) | c, b));
            }
            // 3: the title, raised 4px to make room for the line under it
            InterfaceDefinition title = loader.load((GROUP << 16) | 3, src.findFile(3).getContents());
            title.originalY = TITLE_Y;
            add(comps, want, title);
            InterfaceDefinition layerT = loader.load(0, src.findFile(153).getContents());
            InterfaceDefinition[] rectT = {loader.load(0, src.findFile(159).getContents()),
                    loader.load(0, src.findFile(160).getContents()), loader.load(0, src.findFile(161).getContents())};
            InterfaceDefinition textT = loader.load(0, src.findFile(144).getContents());
            InterfaceDefinition buttonT = loader.load(0, src.findFile(169).getContents());
            InterfaceDefinition labelT = loader.load(0, src.findFile(170).getContents());
            Archive a84 = index.getArchive(84);
            InterfaceDefinition modelT = loader.load(0, a84.getFiles(storage.loadArchive(a84)).findFile(4).getContents());

            // 4: points line under the title
            InterfaceDefinition points = text(textT, 0, 21, 46, 473, 13, TEXT_FONT, TEXT, "");
            points.xTextAlignment = 1;
            add(comps, want, points);
            // 5-8: grid panel
            add(comps, want, layer(layerT, 0, GX, GY, GW, GH));
            frame(comps, want, rectT, 5, GW, GH);
            // 9: scroll container, 10: scrollbar strip (both sized by clientscript 10532 from the server)
            add(comps, want, layer(layerT, 5, 3, 3, SW, SH));
            add(comps, want, layer(layerT, 5, 3 + SW + 2, 3, 16, SH));
            // 11..226: 24 cards x 9 components, 4 per row
            for (int i = 0; i < CARDS; i++) {
                int x = (i % COLS) * (CW + GAP), y = (i / COLS) * (CH + GAP);
                int card = comps.size();
                add(comps, want, layer(layerT, 9, x, y, CW, CH));
                frame(comps, want, rectT, card, CW, CH);
                InterfaceDefinition m = copy(modelT);
                m.contentType = 0; // 84:4 is contentType 328 (always the local player) -- must be cleared
                place(m, card, 5, 4, CW - 10, 40);
                m.modelId = -1;
                m.clickMask = 2 | 4 | 8 | 16;
                m.actions = new String[]{"Value", "Buy 1", "Buy 5", "Examine"};
                add(comps, want, m);
                InterfaceDefinition name = text(textT, card, 3, 46, CW - 6, 24, TEXT_FONT, GOLD, "");
                name.xTextAlignment = 1;
                add(comps, want, name);
                InterfaceDefinition cost = text(textT, card, 3, 70, CW - 6, 13, TEXT_FONT, 0x00ff00, "");
                cost.xTextAlignment = 1;
                add(comps, want, cost);
                InterfaceDefinition b = copy(buttonT);
                place(b, card, 10, 88, CW - 20, 24);
                b.clickMask = 2 | 4 | 8;
                b.actions = new String[]{"Buy 1", "Buy 5", "Buy 10"};
                b.onMouseOverListener = new Object[]{44, -2147483645, 24470};
                b.onMouseLeaveListener = new Object[]{44, -2147483645, 4525};
                add(comps, want, b);
                InterfaceDefinition l = copy(labelT);
                place(l, card, 11, 90, CW - 22, 20);
                l.text = "Buy";
                add(comps, want, l);
            }
            if (comps.size() != 227)
                throw new IllegalStateException("component count " + comps.size() + " != 227 (TournamentShop ids)");

            for (int c = 0; c < comps.size(); c++) {
                InterfaceDefinition got = loader.load((GROUP << 16) | c, comps.get(c));
                InterfaceDefinition exp = want.get(c);
                exp.id = (GROUP << 16) | c;
                for (Field f : InterfaceDefinition.class.getFields()) {
                    if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                    if (!Objects.deepEquals(f.get(got), f.get(exp)))
                        throw new IllegalStateException("component " + c + ": " + f.getName() + " " + f.get(got) + " != " + f.get(exp));
                }
                if (c < 18)
                    System.out.printf("%3d type=%d x=%d y=%d w=%d h=%d parent=%d %s%n", c, got.type, got.originalX, got.originalY,
                            got.originalWidth, got.originalHeight, got.parentId == -1 ? -1 : got.parentId & 0xFFFF,
                            got.actions == null ? "" : Arrays.toString(got.actions));
            }
            if (!apply) {
                System.out.println("VERIFY OK -- " + comps.size() + " components, nothing written.");
                return;
            }

            Archive archive = index.getArchive(GROUP) != null ? index.getArchive(GROUP) : index.addArchive(GROUP);
            archive.setNameHash(-1);
            archive.setCompression(srcArchive.getCompression());
            archive.setRevision(archive.getRevision() + 1);
            FileData[] fd = new FileData[comps.size()];
            ArchiveFiles files = new ArchiveFiles();
            for (int c = 0; c < comps.size(); c++) {
                fd[c] = new FileData();
                fd[c].setId(c);
                FSFile f = new FSFile(c);
                f.setContents(comps.get(c));
                files.addFile(f);
            }
            archive.setFileData(fd);
            byte[] data = files.saveContents();
            Container container = new Container(archive.getCompression(), -1);
            container.compress(data, null);
            storage.store(index.getId(), GROUP, container.data);
            archive.setCrc(container.crc);
            archive.setCompressedSize(container.data.length);
            archive.setDecompressedSize(data.length);
            IndexData indexData = index.toIndexData();
            Container ic = new Container(index.getCompression(), -1);
            ic.compress(indexData.writeIndexData(), null);
            storage.store(255, index.getId(), ic.data);
            index.setCrc(ic.crc);
            System.out.println("APPLY complete -- interface " + GROUP + " with " + comps.size() + " components.");
        }
    }

    static void add(List<byte[]> comps, List<InterfaceDefinition> want, InterfaceDefinition d) {
        comps.add(BuildBondsInterface.encodeIf3(d));
        want.add(d);
    }

    static void frame(List<byte[]> comps, List<InterfaceDefinition> want, InterfaceDefinition[] rectT, int parent, int w, int h) {
        for (int k = 0; k < 3; k++) {
            InterfaceDefinition r = copy(rectT[k]);
            if (k == 2) // the frame's filled inner rect (1002:161, 312b23) is the visible panel colour: warm stone
                r.textColor = 0x36302a;
            place(r, parent, k, k, w - 2 * k, h - 2 * k);
            add(comps, want, r);
        }
    }

    static InterfaceDefinition layer(InterfaceDefinition t, int parent, int x, int y, int w, int h) {
        InterfaceDefinition d = copy(t);
        place(d, parent, x, y, w, h);
        d.scrollWidth = 0;
        d.scrollHeight = 0;
        return d;
    }

    static InterfaceDefinition text(InterfaceDefinition t, int parent, int x, int y, int w, int h, int font, int colour, String s) {
        InterfaceDefinition d = copy(t);
        place(d, parent, x, y, w, h);
        d.fontId = font;
        d.textColor = colour;
        d.text = s;
        d.xTextAlignment = 0;
        d.yTextAlignment = 1;
        d.textShadowed = true;
        return d;
    }

    static void place(InterfaceDefinition d, int parent, int x, int y, int w, int h) {
        d.parentId = (GROUP << 16) | parent;
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
    }

    static InterfaceDefinition copy(InterfaceDefinition s) {
        return BuildBondsInterface.copy(s);
    }
}

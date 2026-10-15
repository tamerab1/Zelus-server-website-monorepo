import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.InterfaceDefinition;
import net.runelite.cache.definitions.loaders.InterfaceLoader;
import net.runelite.cache.fs.*;
import net.runelite.cache.index.FileData;
import net.runelite.cache.index.IndexData;

import java.io.File;
import java.lang.reflect.Field;
import java.util.*;

// Builds interface group 1154, the "Zelus Spinner" mystery box roulette (MysteryBoxSpinner.java, server):
//  - top: the spinner track: a 450px window (9 slots) onto a 40-slot strip (fill, rarity border, item) that the
//    client scrolls smoothly (clientscripts 10750/10751, AddSpinnerScripts), with a gold needle and a gold frame on the
//    middle (winning) slot;
//  - action bar: box count, a framed Spin! button and a Quick open toggle;
//  - bottom left: a scrolling grid of up to 60 possible rewards (rarity border, item, chance tag);
//  - bottom right: recent pulls (8 lines).
// Borders are recoloured by the server (clientscript 45). Prints "CONST name = id" lines for MysteryBoxSpinner.java.
// Usage: verify|apply|rebuild <cachePath>
public class BuildSpinnerInterface {
    static final int GROUP = 1154, SRC = 1002;
    static final int GOLD = 0xffc35a, TEXT = 0xd8d0c0, GREY = 0x9a9a9a;
    static final int HEADING_FONT = 496, TEXT_FONT = 494;
    static final int TITLE_Y = 14, SUB_Y = 45;
    static final int PANEL_FILL = 0x36302a, BOX = 0x433b31, BOX_HOVER = 0x524737, BORDER = 0x5a4d3a;
    static final int SLOT_FILL = 0x2a2520, NEEDLE = 0xffb000;
    static final int STRIP = 40, SLOT_PITCH = 50, GRID = 60, GRID_COLS = 6, FEED = 8;
    static final int COMPONENTS = 402;

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
                byte[] b = src.findFile(c).getContents();
                comps.add(b);
                want.add(loader.load((GROUP << 16) | c, b));
            }
            InterfaceDefinition title = loader.load((GROUP << 16) | 3, src.findFile(3).getContents());
            title.originalY = TITLE_Y;
            add(comps, want, title);
            InterfaceDefinition layerT = loader.load(0, src.findFile(153).getContents());
            InterfaceDefinition[] rectT = {loader.load(0, src.findFile(159).getContents()),
                    loader.load(0, src.findFile(160).getContents()), loader.load(0, src.findFile(161).getContents())};
            InterfaceDefinition textT = loader.load(0, src.findFile(144).getContents());
            InterfaceDefinition buttonT = loader.load(0, src.findFile(169).getContents());
            InterfaceDefinition labelT = loader.load(0, src.findFile(170).getContents());
            InterfaceDefinition rect = rectT[1];

            InterfaceDefinition sub = text(textT, 0, 21, SUB_Y, 473, 13, TEXT_FONT, TEXT, "");
            sub.xTextAlignment = 1;
            add(comps, want, sub);

            // ---- spinner track ----
            int track = panel(comps, want, layerT, rectT, 0, 24, 62, 464, 74);
            // the strip: a scrollable layer 9 slots wide holding all 40 slots side by side
            ids.put("TRACK_LAYER", comps.size());
            InterfaceDefinition strip = layer(layerT, track, 10, 17, 9 * SLOT_PITCH, 40);
            strip.scrollWidth = STRIP * SLOT_PITCH;
            add(comps, want, strip);
            int stripId = ids.get("TRACK_LAYER");
            ids.put("FIRST_TRACK", comps.size());
            for (int k = 0; k < STRIP; k++) {
                int x = k * SLOT_PITCH;
                add(comps, want, solid(rect, stripId, x, 0, 44, 40, SLOT_FILL));
                add(comps, want, outline(rect, stripId, x, 0, 44, 40, BORDER));
                add(comps, want, item(buttonT, stripId, x + 4, 4, false));
            }
            // the winning slot (k = 4): gold frame and needle above / below
            int cx = 10 + 4 * 50;
            add(comps, want, outline(rect, track, cx - 2, 15, 48, 44, NEEDLE));
            add(comps, want, outline(rect, track, cx - 1, 16, 46, 42, NEEDLE));
            add(comps, want, solid(rect, track, cx + 20, 3, 4, 11, NEEDLE));
            add(comps, want, solid(rect, track, cx + 20, 60, 4, 11, NEEDLE));

            // ---- action bar ----
            ids.put("BOX_COUNT", comps.size());
            add(comps, want, text(textT, 0, 30, 143, 150, 20, TEXT_FONT, TEXT, ""));
            ids.put("SPIN", comps.size());
            InterfaceDefinition spin = copy(buttonT);
            place(spin, 0, 186, 140, 140, 26);
            spin.clickMask = 2;
            spin.actions = new String[]{"Spin!"};
            spin.onMouseOverListener = new Object[]{44, -2147483645, 24470};
            spin.onMouseLeaveListener = new Object[]{44, -2147483645, 4525};
            add(comps, want, spin);
            InterfaceDefinition spinLabel = copy(labelT);
            place(spinLabel, 0, 187, 142, 138, 22);
            spinLabel.text = "Spin!";
            add(comps, want, spinLabel);
            ids.put("QUICK", comps.size());
            InterfaceDefinition quick = solid(rect, 0, 344, 143, 140, 20, BOX);
            quick.clickMask = 2;
            quick.actions = new String[]{"Toggle"};
            hover(quick, BOX, BOX_HOVER);
            add(comps, want, quick);
            InterfaceDefinition quickLabel = text(textT, 0, 344, 143, 140, 20, TEXT_FONT, TEXT, "");
            quickLabel.xTextAlignment = 1;
            add(comps, want, quickLabel);

            // ---- possible rewards ----
            int rewards = panel(comps, want, layerT, rectT, 0, 24, 170, 300, 135);
            add(comps, want, text(textT, rewards, 6, 3, 288, 14, TEXT_FONT, GOLD, "Possible rewards"));
            ids.put("GRID_SCROLL", comps.size());
            add(comps, want, layer(layerT, rewards, 4, 18, 276, 113));
            add(comps, want, layer(layerT, rewards, 282, 18, 16, 113));
            int gridScroll = ids.get("GRID_SCROLL");
            ids.put("FIRST_CELL", comps.size());
            for (int c = 0; c < GRID; c++) {
                int x = (c % GRID_COLS) * 46, y = (c / GRID_COLS) * 44;
                add(comps, want, solid(rect, gridScroll, x, y, 44, 42, SLOT_FILL));
                add(comps, want, outline(rect, gridScroll, x, y, 44, 42, BORDER));
                InterfaceDefinition it = item(buttonT, gridScroll, x + 4, y, true);
                add(comps, want, it);
                InterfaceDefinition tag = text(textT, gridScroll, x, y + 30, 44, 12, TEXT_FONT, TEXT, "");
                tag.xTextAlignment = 1;
                add(comps, want, tag);
            }

            // ---- recent pulls ----
            int feed = panel(comps, want, layerT, rectT, 0, 328, 170, 160, 135);
            add(comps, want, text(textT, feed, 6, 3, 148, 14, TEXT_FONT, GOLD, "Recent pulls"));
            ids.put("FIRST_FEED", comps.size());
            for (int l = 0; l < FEED; l++)
                add(comps, want, text(textT, feed, 6, 18 + l * 14, 148, 14, TEXT_FONT, TEXT, ""));

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
            write(storage, index, srcArchive, comps);
            System.out.println("APPLY complete -- interface " + GROUP + " with " + comps.size() + " components.");
        }
    }

    /** A graphic component holding an item: drawn like an inventory slot (icon, outline, shadow, stack number). */
    static InterfaceDefinition item(InterfaceDefinition buttonT, int parent, int x, int y, boolean examine) {
        InterfaceDefinition s = copy(buttonT);
        place(s, parent, x, y, 36, 32);
        s.spriteId = -1;
        s.spriteTiling = false;
        s.onMouseOverListener = null;
        s.onMouseLeaveListener = null;
        s.hasListener = false;
        s.borderType = 1;
        s.shadowColor = 0x302020;
        if (examine) {
            s.clickMask = 2;
            s.actions = new String[]{"Examine"};
        }
        return s;
    }

    static void write(Storage storage, Index index, Archive srcArchive, List<byte[]> comps) throws Exception {
        write(storage, index, srcArchive, comps, GROUP);
    }

    /** Writes a whole interface group (shared with the other builders). */
    static void write(Storage storage, Index index, Archive srcArchive, List<byte[]> comps, int group) throws Exception {
        Archive archive = index.getArchive(group) != null ? index.getArchive(group) : index.addArchive(group);
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
        storage.store(index.getId(), group, container.data);
        archive.setCrc(container.crc);
        archive.setCompressedSize(container.data.length);
        archive.setDecompressedSize(data.length);
        IndexData indexData = index.toIndexData();
        Container ic = new Container(index.getCompression(), -1);
        ic.compress(indexData.writeIndexData(), null);
        storage.store(255, index.getId(), ic.data);
        index.setCrc(ic.crc);
    }

    static int panel(List<byte[]> comps, List<InterfaceDefinition> want, InterfaceDefinition layerT, InterfaceDefinition[] rectT,
                     int parent, int x, int y, int w, int h) {
        int id = comps.size();
        add(comps, want, layer(layerT, parent, x, y, w, h));
        add(comps, want, solid(rectT[1], id, 0, 0, w, h, PANEL_FILL));
        for (int k = 0; k < 3; k++) {
            InterfaceDefinition r = copy(rectT[k]);
            if (k == 2) // the frame's filled inner rect (1002:161, 312b23) is the visible panel colour: warm stone
                r.textColor = 0x36302a;
            place(r, id, k, k, w - 2 * k, h - 2 * k);
            add(comps, want, r);
        }
        return id;
    }

    static InterfaceDefinition outline(InterfaceDefinition rect, int parent, int x, int y, int w, int h, int colour) {
        InterfaceDefinition o = copy(rect);
        place(o, parent, x, y, w, h);
        o.filled = false;
        o.textColor = colour;
        return o;
    }

    static InterfaceDefinition solid(InterfaceDefinition rect, int parent, int x, int y, int w, int h, int colour) {
        InterfaceDefinition d = copy(rect);
        place(d, parent, x, y, w, h);
        d.filled = true;
        d.textColor = colour;
        d.opacity = 0;
        return d;
    }

    static void hover(InterfaceDefinition d, int normal, int hovered) {
        d.onMouseOverListener = new Object[]{45, -2147483645, hovered};
        d.onMouseLeaveListener = new Object[]{45, -2147483645, normal};
        d.hasListener = true;
    }

    static void add(List<byte[]> comps, List<InterfaceDefinition> want, InterfaceDefinition d) {
        comps.add(BuildBondsInterface.encodeIf3(d));
        want.add(d);
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

import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.InterfaceDefinition;
import net.runelite.cache.definitions.loaders.InterfaceLoader;
import net.runelite.cache.fs.*;
import net.runelite.cache.index.FileData;
import net.runelite.cache.index.IndexData;

import java.io.File;
import java.lang.reflect.Field;
import java.util.*;

// Builds interface group 1148, the Pit Championships hub (TournamentHub.java, server), in the Bonds window's
// style: Wilderness Forge frame (1002:0-3), a status line beside the title, 3 side tabs + a "Your record" box,
// and a content panel holding two layers: MAIN (banner, requirements, prize showcase with 3 item models,
// Register / Teleport buttons) and LIST (a scrolling 60-row list for the Schedule and Hall of Fame tabs).
// Encoder/verification shared with BuildBondsInterface. Component ids: TournamentHub.java.
// Usage: verify|apply|rebuild <cachePath>
public class BuildTournamentInterface {
    static final int GROUP = 1148, SRC = 1002;
    static final int GOLD = 0xffc35a, TEXT = 0xd8d0c0;
    static final int HEADING_FONT = 496, TEXT_FONT = 494;
    static final int PW = 306, PH = 246, PY = 62, TITLE_Y = 14;
    static final int REQ_ROWS = 5, LIST_ROWS = 60, INFO_ROWS = 7;
    static final int LIST_W = 276, LIST_H = 240;

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
            // 0-3: root, background, close button, title (verbatim copies).
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

            // 4: status line (countdown / registration state), right of the title
            InterfaceDefinition status = text(textT, 0, 21, STATUS_Y, 473, 13, TEXT_FONT, TEXT, "");
            status.xTextAlignment = 1;
            add(comps, want, status);
            // 5-8: content panel (layer + 3 border rects)
            add(comps, want, layer(layerT, 0, 182, PY, PW, PH));
            frame(comps, want, rectT, 5, PW, PH);

            // 9: MAIN layer
            add(comps, want, layer(layerT, 5, 0, 0, PW, PH));
            // 10: format heading, 11: status badge, 12-13: info lines
            InterfaceDefinition heading = text(textT, 9, 32, 4, 242, 18, HEADING_FONT, GOLD, "");
            heading.xTextAlignment = 1;
            add(comps, want, heading);
            InterfaceDefinition badge = text(textT, 9, 32, 22, 242, 12, TEXT_FONT, TEXT, "");
            badge.xTextAlignment = 1;
            add(comps, want, badge);
            add(comps, want, text(textT, 9, 8, 38, 290, 13, TEXT_FONT, TEXT, ""));
            add(comps, want, text(textT, 9, 8, 51, 290, 13, TEXT_FONT, TEXT, ""));
            // 14: divider, 15: heading, 16-20: requirement rows
            add(comps, want, divider(rectT[1], 9, 67));
            add(comps, want, text(textT, 9, 8, 70, 290, 12, TEXT_FONT, GOLD, "Requirements & entry"));
            for (int r = 0; r < REQ_ROWS; r++)
                add(comps, want, text(textT, 9, 8, 83 + 11 * r, 290, 11, TEXT_FONT, TEXT, ""));
            // 21: divider, 22: heading, 23-25: prize models, 26-34: prize text (3 lines per place)
            add(comps, want, divider(rectT[1], 9, 141));
            add(comps, want, text(textT, 9, 8, 143, 290, 12, TEXT_FONT, GOLD, "Prize pool"));
            for (int p = 0; p < 3; p++)
                add(comps, want, model(modelT, 9, 8 + 100 * p, 168, 90, 22));
            for (int p = 0; p < 3; p++)
                for (int l = 0; l < 3; l++) {
                    InterfaceDefinition t = text(textT, 9, 4 + 100 * p, l == 0 ? 155 : 185 + 12 * l, 98, 12, TEXT_FONT, l == 0 ? GOLD : TEXT, "");
                    t.xTextAlignment = 1;
                    add(comps, want, t);
                }
            // 35-36: Register button + label, 37-38: Teleport button + label
            button(comps, want, buttonT, labelT, 9, 6, 222, 184, 22, "Register");
            button(comps, want, buttonT, labelT, 9, 194, 222, 106, 22, "Enter Lobby");

            // 39: LIST layer (hidden), 40: scroll container, 41: scrollbar strip, 42-101: rows
            InterfaceDefinition list = layer(layerT, 5, 0, 0, PW, PH);
            list.isHidden = true;
            add(comps, want, list);
            add(comps, want, layer(layerT, 39, 6, 4, LIST_W, LIST_H));
            add(comps, want, layer(layerT, 39, 6 + LIST_W + 2, 4, 16, LIST_H));
            for (int r = 0; r < LIST_ROWS; r++)
                add(comps, want, text(textT, 40, 4, 2 + 12 * r, LIST_W - 8, 12, TEXT_FONT, TEXT, ""));

            // 102-107: 3 tabs (button + label)
            String[] tabs = {"Tournament", "Schedule & rules", "Hall of Fame"};
            for (int t = 0; t < tabs.length; t++) {
                InterfaceDefinition b = copy(buttonT);
                place(b, 0, 24, PY + 46 * t, 150, 43);
                b.clickMask = 2;
                b.actions = new String[]{"View"};
                b.onMouseOverListener = new Object[]{44, -2147483645, 24470};
                b.onMouseLeaveListener = new Object[]{44, -2147483645, 4525};
                add(comps, want, b);
                InterfaceDefinition l = copy(labelT);
                place(l, 0, 25, PY + 2 + 46 * t, 148, 38);
                l.text = tabs[t];
                add(comps, want, l);
            }
            // 108-111: "Your record" box (layer + rects), 112-118: its rows
            int box = comps.size();
            add(comps, want, layer(layerT, 0, 24, PY + 142, 150, 102));
            frame(comps, want, rectT, box, 150, 102);
            for (int r = 0; r < INFO_ROWS; r++)
                add(comps, want, text(textT, box, 7, 4 + 14 * r, 138, 14, TEXT_FONT, TEXT, ""));
            // 119-122: format browser arrows (button + label) either side of the format heading
            arrow(comps, want, rectT[1], textT, 8, "<lt>", "Previous format");
            arrow(comps, want, rectT[1], textT, PW - 28, "<gt>", "Next format");
            // 123-124: gold outlines over the two arrow boxes
            for (int x : new int[]{8, PW - 28}) {
                InterfaceDefinition o = copy(rectT[1]);
                place(o, 9, x, 4, 20, 18);
                o.filled = false;
                o.textColor = 0x6b5a3e;
                hover(o, 0x6b5a3e, GOLD);
                add(comps, want, o);
            }
            if (comps.size() != 125)
                throw new IllegalStateException("component count " + comps.size() + " != 125 (TournamentHub ids)");

            for (int c = 0; c < comps.size(); c++) {
                InterfaceDefinition got = loader.load((GROUP << 16) | c, comps.get(c));
                InterfaceDefinition exp = want.get(c);
                exp.id = (GROUP << 16) | c;
                for (Field f : InterfaceDefinition.class.getFields()) {
                    if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                    if (!Objects.deepEquals(f.get(got), f.get(exp)))
                        throw new IllegalStateException("component " + c + ": " + f.getName() + " " + f.get(got) + " != " + f.get(exp));
                }
                System.out.printf("%3d type=%d x=%d y=%d w=%d h=%d parent=%d hidden=%b %s%n", c, got.type, got.originalX, got.originalY,
                        got.originalWidth, got.originalHeight, got.parentId == -1 ? -1 : got.parentId & 0xFFFF, got.isHidden,
                        got.text != null && !got.text.isEmpty() ? "\"" + got.text + "\"" : "");
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

    static final int STATUS_Y = 45;

    static void button(List<byte[]> comps, List<InterfaceDefinition> want, InterfaceDefinition buttonT, InterfaceDefinition labelT,
                       int parent, int x, int y, int w, int h, String action) {
        InterfaceDefinition b = copy(buttonT);
        place(b, parent, x, y, w, h);
        b.clickMask = 2;
        b.actions = new String[]{action};
        b.onMouseOverListener = new Object[]{44, -2147483645, 24470};
        b.onMouseLeaveListener = new Object[]{44, -2147483645, 4525};
        add(comps, want, b);
        InterfaceDefinition l = copy(labelT);
        place(l, parent, x + 1, y + 2, w - 2, h - 4);
        l.text = action;
        add(comps, want, l);
    }

    /** A 20x18 clickable dark box with a centred gold glyph (2 components). */
    static void arrow(List<byte[]> comps, List<InterfaceDefinition> want, InterfaceDefinition rect, InterfaceDefinition textT,
                      int x, String glyph, String action) {
        InterfaceDefinition box = copy(rect);
        place(box, 9, x, 4, 20, 18);
        box.filled = true;
        box.textColor = 0x1e1813;
        box.opacity = 60;
        box.clickMask = 2;
        box.actions = new String[]{action};
        hover(box, 0x1e1813, 0x3d3022);
        add(comps, want, box);
        InterfaceDefinition t = text(textT, 9, x, 4, 20, 18, HEADING_FONT, GOLD, glyph);
        t.xTextAlignment = 1;
        hover(t, GOLD, 0xffffff);
        add(comps, want, t);
    }

    /** Hover colour via clientscript 45 (sets the event component's colour), as used by ~800 cache components. */
    static void hover(InterfaceDefinition d, int normal, int hovered) {
        d.onMouseOverListener = new Object[]{45, -2147483645, hovered};
        d.onMouseLeaveListener = new Object[]{45, -2147483645, normal};
        d.hasListener = true;
    }

    static InterfaceDefinition divider(InterfaceDefinition rect, int parent, int y) {
        InterfaceDefinition d = copy(rect);
        place(d, parent, 6, y, PW - 12, 1);
        d.filled = true;
        return d;
    }

    static InterfaceDefinition model(InterfaceDefinition modelT, int parent, int x, int y, int w, int h) {
        InterfaceDefinition m = copy(modelT);
        m.contentType = 0; // 84:4 is contentType 328 (always the local player) -- must be cleared
        place(m, parent, x, y, w, h);
        m.modelId = -1;
        return m;
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

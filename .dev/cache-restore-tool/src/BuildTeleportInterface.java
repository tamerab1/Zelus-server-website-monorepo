import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.InterfaceDefinition;
import net.runelite.cache.definitions.loaders.InterfaceLoader;
import net.runelite.cache.fs.*;
import net.runelite.cache.index.FileData;
import net.runelite.cache.index.IndexData;

import java.io.File;
import java.lang.reflect.Field;
import java.util.*;

// Builds interface group 1152, the Zelus Teleport Nexus (TeleportNexus.java, server) that replaces the old teleport
// interface 851. Three panes in the Pit hub style (Wilderness Forge frame 1002:0-3, warm stone panel fills):
//  - left: 7 category buttons (icon + bold label, hover, gold selected outline);
//  - middle: a Search button and a scrolling list of 80 destination cards (name + favourite star on top; danger tag /
//    note and a framed Teleport button below);
//  - right: the selected destination's preview: name, tag line, an animated model (6 model components at the old
//    interface's zoom presets, one shown at a time), description, 4 drop slots, requirement, Teleport + star.
// Encoder/verification shared with BuildBondsInterface. Prints "CONST name = id" lines for TeleportNexus.java.
// Usage: verify|apply|rebuild <cachePath>
public class BuildTeleportInterface {
    static final int GROUP = 1152, SRC = 1002;
    static final int GOLD = 0xffc35a, TEXT = 0xd8d0c0;
    static final int HEADING_FONT = 496, BIG_FONT = 495, TEXT_FONT = 494;
    static final int TITLE_Y = 14, SUB_Y = 45;
    static final int PANEL_FILL = 0x36302a, BOX = 0x433b31, BOX_HOVER = 0x524737, BORDER = 0x5a4d3a;
    static final int SLOT_FILL = 0x2a2520, SLOT_BORDER = 0x5a4d3a;
    static final int CATEGORIES = 7, ROWS = 80, ROW_H = 38, ROW_PITCH = 40, DROPS = 4;
    /** Favourite stars: gold (favourited) and grey (not), from the old teleport interface's sprite set. */
    static final int STAR_GOLD = 4576, STAR_GREY = 4573;
    /** The old interface's model components (851:60,61,62,63,65,66): zoom and vertical offset per preset. */
    static final int[][] MODEL_PRESETS = {{2000, 240}, {1000, 100}, {3000, 300}, {500, 40}, {1500, 150}, {3500, 300}};
    /** The preview is 80px tall where the old one was 112: zoom out by the same ratio so models keep their framing. */
    static final double MODEL_ZOOM_SCALE = 112D / 80D;
    static final int COMPONENTS = 973;

    public static void main(String[] args) throws Exception {
        boolean rebuild = args[0].equals("rebuild");
        boolean apply = args[0].equals("apply") || rebuild;
        try (Store store = new Store(new File(args[1]))) {
            store.load();
            Storage storage = store.getStorage();
            Index index = store.getIndex(IndexType.INTERFACES);
            if (index.getArchive(GROUP) != null && apply && !rebuild)
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
            Archive a84 = index.getArchive(84);
            InterfaceDefinition modelT = loader.load(0, a84.getFiles(storage.loadArchive(a84)).findFile(4).getContents());
            InterfaceDefinition rect = rectT[1];

            InterfaceDefinition sub = text(textT, 0, 21, SUB_Y, 473, 13, TEXT_FONT, TEXT, "");
            sub.xTextAlignment = 1;
            add(comps, want, sub);

            // ---- left: categories x (box, border, gold outline x2, icon, label) ----
            int left = panel(comps, want, layerT, rectT, 0, 24, 62, 116, 243);
            ids.put("FIRST_CATEGORY", comps.size());
            for (int c = 0; c < CATEGORIES; c++) {
                int y = 5 + c * 33;
                selectable(comps, want, rect, left, 5, y, 106, 32, "View");
                InterfaceDefinition icon = sprite(buttonT, left, 6, y, 36, 32, c == 0 ? STAR_GOLD : -1, 0);
                if (c == 0) {
                    icon.originalX = 14;
                    icon.originalY = y + 7;
                    icon.originalWidth = 19;
                    icon.originalHeight = 18;
                }
                add(comps, want, icon);
                // regular 12pt over 66px: "Wilderness" in bold ran past the stone (2026-10-15)
                add(comps, want, text(textT, left, 42, y + 2, 66, 30, BIG_FONT, TEXT, ""));
            }

            // ---- middle: search + destination cards ----
            int middle = panel(comps, want, layerT, rectT, 0, 144, 62, 190, 243);
            ids.put("SEARCH", comps.size());
            InterfaceDefinition search = solid(rect, middle, 5, 5, 180, 20, BOX);
            search.clickMask = 2;
            search.actions = new String[]{"Search"};
            hover(search, BOX, BOX_HOVER);
            add(comps, want, search);
            add(comps, want, text(textT, middle, 10, 5, 172, 20, TEXT_FONT, 0x9a9a9a, ""));
            ids.put("SCROLL", comps.size());
            ids.put("SCROLLBAR", comps.size() + 1);
            add(comps, want, layer(layerT, middle, 4, 29, 166, 210));
            add(comps, want, layer(layerT, middle, 171, 29, 16, 210));
            int scroll = ids.get("SCROLL");
            ids.put("FIRST_ROW", comps.size());
            for (int r = 0; r < ROWS; r++) {
                int row = comps.size();
                add(comps, want, layer(layerT, scroll, 0, r * ROW_PITCH, 166, ROW_H));
                selectable(comps, want, rect, row, 0, 0, 166, ROW_H, "Select");
                add(comps, want, text(textT, row, 5, 2, 138, 15, TEXT_FONT, GOLD, ""));
                add(comps, want, text(textT, row, 5, 19, 100, 15, TEXT_FONT, TEXT, ""));
                InterfaceDefinition go = copy(buttonT);
                place(go, row, 106, 19, 57, 17);
                go.clickMask = 2;
                go.actions = new String[]{"Teleport"};
                go.onMouseOverListener = new Object[]{44, -2147483645, 24470};
                go.onMouseLeaveListener = new Object[]{44, -2147483645, 4525};
                add(comps, want, go);
                InterfaceDefinition goLabel = text(textT, row, 106, 19, 57, 17, TEXT_FONT, 0x00ff00, "Teleport");
                goLabel.xTextAlignment = 1;
                add(comps, want, goLabel);
                InterfaceDefinition on = sprite(buttonT, row, 146, 1, 19, 18, STAR_GOLD, 0);
                on.clickMask = 2;
                on.actions = new String[]{"Unfavourite"};
                on.isHidden = true;
                add(comps, want, on);
                InterfaceDefinition off = sprite(buttonT, row, 146, 1, 19, 18, STAR_GREY, 120);
                off.clickMask = 2;
                off.actions = new String[]{"Favourite"};
                add(comps, want, off);
            }

            // ---- right: preview ----
            int right = panel(comps, want, layerT, rectT, 0, 338, 62, 150, 243);
            ids.put("PREVIEW_EMPTY", comps.size());
            InterfaceDefinition empty = text(textT, right, 8, 80, 134, 60, TEXT_FONT, 0x9a9a9a,
                    "Select a destination to see it here.");
            empty.xTextAlignment = 1;
            add(comps, want, empty);
            ids.put("PREVIEW_NAME", comps.size());
            InterfaceDefinition name = text(textT, right, 5, 4, 140, 16, HEADING_FONT, GOLD, "");
            name.xTextAlignment = 1;
            add(comps, want, name);
            InterfaceDefinition tagLine = text(textT, right, 5, 20, 140, 13, TEXT_FONT, TEXT, "");
            tagLine.xTextAlignment = 1;
            add(comps, want, tagLine);
            ids.put("FIRST_MODEL", comps.size());
            for (int[] preset : MODEL_PRESETS) {
                InterfaceDefinition m = copy(modelT);
                m.contentType = 0; // 84:4 is contentType 328 (always the local player) -- must be cleared
                place(m, right, 3, 34, 144, 80);
                m.modelType = 1;
                m.modelId = -1;
                m.modelZoom = (int) Math.round(preset[0] * MODEL_ZOOM_SCALE);
                // offsetY2d 0 (2026-10-15): the IF3 renderer then centres the model itself; TeleportNexus sets the zoom
                // per preview from the model's bounds (teleport_previews.json) instead of these fixed presets
                m.offsetY2d = 0;
                m.rotationX = 0;
                m.rotationY = 0;
                m.rotationZ = 0;
                m.isHidden = true;
                add(comps, want, m);
            }
            ids.put("PREVIEW_DESC", comps.size());
            InterfaceDefinition desc = text(textT, right, 5, 120, 140, 50, TEXT_FONT, TEXT, "");
            desc.xTextAlignment = 1;
            desc.yTextAlignment = 0;
            desc.lineHeight = 12;
            add(comps, want, desc);
            ids.put("FIRST_DROP", comps.size());
            for (int k = 0; k < DROPS; k++) {
                int x = 3 + k * 37;
                add(comps, want, solid(rect, right, x, 172, 36, 32, SLOT_FILL));
                add(comps, want, outlineRect(rect, right, x, 172, 36, 32, SLOT_BORDER, false));
                InterfaceDefinition item = sprite(buttonT, right, x, 172, 36, 32, -1, 0);
                item.borderType = 1;
                item.shadowColor = 0x302020;
                item.clickMask = 2;
                item.actions = new String[]{"Examine"};
                add(comps, want, item);
            }
            ids.put("PREVIEW_REQ", comps.size());
            InterfaceDefinition req = text(textT, right, 5, 206, 140, 13, TEXT_FONT, TEXT, "");
            req.xTextAlignment = 1;
            add(comps, want, req);
            ids.put("PREVIEW_TELEPORT", comps.size());
            InterfaceDefinition tele = copy(buttonT);
            place(tele, right, 5, 221, 112, 19);
            tele.clickMask = 2;
            tele.actions = new String[]{"Teleport"};
            tele.onMouseOverListener = new Object[]{44, -2147483645, 24470};
            tele.onMouseLeaveListener = new Object[]{44, -2147483645, 4525};
            add(comps, want, tele);
            InterfaceDefinition teleLabel = copy(labelT);
            place(teleLabel, right, 6, 222, 110, 16);
            teleLabel.text = "Teleport";
            add(comps, want, teleLabel);
            ids.put("PREVIEW_STAR_ON", comps.size());
            InterfaceDefinition on = sprite(buttonT, right, 124, 221, 19, 18, STAR_GOLD, 0);
            on.clickMask = 2;
            on.actions = new String[]{"Unfavourite"};
            on.isHidden = true;
            add(comps, want, on);
            InterfaceDefinition off = sprite(buttonT, right, 124, 221, 19, 18, STAR_GREY, 120);
            off.clickMask = 2;
            off.actions = new String[]{"Favourite"};
            add(comps, want, off);

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

    /** A framed panel (layer + warm fill + 3 border rects); returns the layer's id. */
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

    /**
     * 4 components: clickable fill with hover wash, border, then the selected state the server shows (+2/+3): a recessed
     * dark slate fill inside the border and a soft gold 1px highlight. (Was a 2px neon gold outline, rejected 2026-10-15.)
     */
    static void selectable(List<byte[]> comps, List<InterfaceDefinition> want, InterfaceDefinition rect, int parent,
                           int x, int y, int w, int h, String action) {
        InterfaceDefinition b = solid(rect, parent, x, y, w, h, BOX);
        b.clickMask = 2;
        b.actions = new String[]{action};
        hover(b, BOX, BOX_HOVER);
        add(comps, want, b);
        add(comps, want, outlineRect(rect, parent, x, y, w, h, BORDER, false));
        InterfaceDefinition recess = solid(rect, parent, x + 1, y + 1, w - 2, h - 2, 0x1c1814);
        recess.isHidden = true;
        add(comps, want, recess);
        add(comps, want, outlineRect(rect, parent, x, y, w, h, 0x15120f, true)); // dark pressed-stone edge: any lighter trim read as a yellow box
    }

    static InterfaceDefinition sprite(InterfaceDefinition buttonT, int parent, int x, int y, int w, int h, int spriteId, int opacity) {
        InterfaceDefinition s = copy(buttonT);
        place(s, parent, x, y, w, h);
        s.spriteId = spriteId;
        s.spriteTiling = false;
        s.opacity = opacity;
        s.onMouseOverListener = null;
        s.onMouseLeaveListener = null;
        s.hasListener = false;
        return s;
    }

    static InterfaceDefinition outlineRect(InterfaceDefinition rect, int parent, int x, int y, int w, int h, int colour, boolean hidden) {
        InterfaceDefinition o = copy(rect);
        place(o, parent, x, y, w, h);
        o.filled = false;
        o.textColor = colour;
        o.isHidden = hidden;
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

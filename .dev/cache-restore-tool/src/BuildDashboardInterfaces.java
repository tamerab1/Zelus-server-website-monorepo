import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.InterfaceDefinition;
import net.runelite.cache.definitions.loaders.InterfaceLoader;
import net.runelite.cache.fs.*;
import net.runelite.cache.index.FileData;
import net.runelite.cache.index.IndexData;

import java.io.File;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.util.*;

// Builds the three journal side-panel dashboards (opened into 629:28 by the server):
//   1143 Character Summary, 1144 Server Info, 1145 Wilderness.
// Each: a dark background filling the panel, a centred title, a divider, and a scroll container
// (clientscript 10532 builds the bar in the strip on the right) holding boxed sections -- a header
// strip plus label/value rows, or a 2-column grid of buttons.
// The layout lives ONLY here: the tool also writes kronos-server's DashboardLayout.java with every
// component id, so the server can never drift from the cache.
// Same encoder/verification as BuildItemStatsInterface.
// Usage: verify|apply|rebuild <cachePath> <DashboardLayout.java output path>
public class BuildDashboardInterfaces {

    /** barColour != 0 adds a progress bar (track + fill + gloss) under the rows; the fill width is set at runtime. */
    record Section(String title, int rows, int buttons, int barColour) {}
    /** icon = sprite shown left of the title. */
    record Group(int id, String name, String title, int icon, Section... sections) {}

    static final Group[] GROUPS = {
            new Group(1143, "CHARACTER", "Character Summary", 1709,
                    new Section("Profile", 6, 0, 0),
                    new Section("Points", 9, 0, 0),
                    new Section("Progress", 5, 0, 0),
                    new Section("Active boosts", 7, 0, 0)),
            new Group(1144, "SERVER", "Server Info", 908,
                    new Section("Server", 5, 0, 0),
                    new Section("Events", 4, 0, 0),
                    new Section("Quick tools", 0, 10, 0)),
            new Group(1145, "WILDERNESS", "Wilderness", 168,
                    new Section("Hotspot", 2, 0, 0),
                    new Section("Boss status", 4, 0, 0),
                    new Section("PK record", 6, 0, 0)),
            // Account Management side tab, repurposed (opened into ToplevelComponent.ACCOUNT_MANAGEMENT_TAB_AREA)
            new Group(1146, "PK_HUB", "PK Hub", 439,
                    new Section("Combat profile", 6, 0, 0),
                    new Section("Rivals", 1, 1, 0),
                    new Section("Wilderness", 5, 0, 0),
                    new Section("PvP presets", 1, 1, 0),
                    new Section("Teleports", 0, 5, 0)),
            // Achievement Diaries journal tab (replaces the old 1086 achievement page)
            new Group(1147, "ACHIEVEMENTS", "Achievements", 1299,
                    new Section("Easy", 1, 0, 0x3fb950),
                    new Section("Medium", 1, 0, 0x2fb3e0),
                    new Section("Hard", 1, 0, 0xe0952f),
                    new Section("Elite", 1, 0, 0xc05ae0),
                    new Section("All achievements", 1, 1, 0)),
    };

    static final Charset CP1252 = Charset.forName("windows-1252");
    static final int W = 164;              // scroll content width (side panel 184 minus the 16px bar, 4px gutter)
    static final int BAR_H = 10;           // clientscript 10533 sets width + a fixed height of 10
    static final int ROW_H = 17, HEADER_H = 17, BUTTON_H = 28, GAP = 6;
    static final int BG = 0x2e2924, HEADER_BG = 0x3a2e1f, BOX = 0x4a4033, BUTTON_BG = 0x463b2d;
    /** Button hover: a brighter warm brown (clientscript 45 on the button itself). */
    static final int BUTTON_HOVER = 0x63523a;
    static final int HEADER_LINE = 0x3e3226, INSET = 0x3e362c, TRACK = 0x14100c, GLOSS = 0xffffff; // dark bronze (8a6d3b read as yellow)
    static final int GOLD = 0xffc35a, HEADER_GOLD = 0xff981f, LABEL = 0xc8bfae, VALUE = 0xffffff;

    static InterfaceDefinition layerT, rectT, textT, graphicT, buttonT;
    static int group;

    public static void main(String[] args) throws Exception {
        boolean rebuild = args[0].equals("rebuild"), apply = args[0].equals("apply") || rebuild;
        try (Store store = new Store(new File(args[1]))) {
            store.load();
            Storage storage = store.getStorage();
            Index index = store.getIndex(IndexType.INTERFACES);
            InterfaceLoader loader = new InterfaceLoader();
            Archive forge = index.getArchive(1002);
            ArchiveFiles ff = forge.getFiles(storage.loadArchive(forge));
            layerT = loader.load(0, ff.findFile(153).getContents());
            rectT = loader.load(0, ff.findFile(161).getContents()); // filled rect
            textT = loader.load(0, ff.findFile(144).getContents());
            graphicT = loader.load(0, ff.findFile(2).getContents()); // the forge close button (plain IF3 graphic)
            graphicT.actions = null;
            buttonT = loader.load(0, ff.findFile(169).getContents()); // OSRS bezel button (sprite 4525, hover 24470)

            StringBuilder code = new StringBuilder();
            for (Group g : GROUPS) {
                group = g.id;
                if (index.getArchive(g.id) != null && !rebuild && apply)
                    throw new IllegalStateException("interface " + g.id + " exists (use rebuild)");
                List<InterfaceDefinition> comps = new ArrayList<>();
                // 0 root (fills 629:28), 1 background, 2 title, 3 divider, 4 scroll container, 5 scrollbar strip
                InterfaceDefinition root = layer(-1, 0, 0, 0, 0);
                root.widthMode = root.heightMode = 1;
                comps.add(root);
                InterfaceDefinition bg = rect(0, 0, 0, 0, 0, BG, true);
                bg.widthMode = bg.heightMode = 1;
                comps.add(bg);
                InterfaceDefinition title = text(0, 28, 2, 32, 16, 496, GOLD, g.title, 0); // left of it: the icon
                title.widthMode = 1;
                comps.add(title);
                InterfaceDefinition divider = rect(0, 4, 19, 8, 1, BOX, true);
                divider.widthMode = 1;
                comps.add(divider);
                InterfaceDefinition scroll = layer(0, 0, 22, 16, 22);
                scroll.widthMode = scroll.heightMode = 1;
                comps.add(scroll);
                InterfaceDefinition bar = layer(0, 0, 22, 16, 22);
                bar.xPositionMode = 2;
                bar.heightMode = 1;
                comps.add(bar);

                List<Integer> headers = new ArrayList<>();
                List<int[]> labels = new ArrayList<>(), values = new ArrayList<>();
                List<Integer> buttons = new ArrayList<>(), buttonTexts = new ArrayList<>(), buttonIcons = new ArrayList<>();
                int y = 4;
                List<Integer> bars = new ArrayList<>();
                for (Section s : g.sections) {
                    // header strip with a gold outline
                    comps.add(rect(4, 2, y, W - 4, HEADER_H, HEADER_BG, true));
                    comps.add(rect(4, 2, y, W - 4, HEADER_H, HEADER_LINE, false));
                    headers.add(comps.size());
                    comps.add(text(4, 2, y, W - 4, HEADER_H, 496, HEADER_GOLD, s.title, 1));
                    int rowsH = s.rows > 0 ? s.rows * ROW_H + 4 : 0;
                    int barH = s.barColour != 0 ? BAR_H + 6 : 0;
                    int buttonsH = s.buttons > 0 ? ((s.buttons + 1) / 2) * (BUTTON_H + 3) + 3 : 0;
                    int boxH = rowsH + barH + buttonsH;
                    // box: outer line + 1px inset line
                    comps.add(rect(4, 2, y + HEADER_H, W - 4, boxH, BOX, false));
                    comps.add(rect(4, 3, y + HEADER_H + 1, W - 6, boxH - 2, INSET, false));
                    int[] l = new int[s.rows], v = new int[s.rows];
                    for (int r = 0; r < s.rows; r++) {
                        int ry = y + HEADER_H + 2 + r * ROW_H;
                        InterfaceDefinition lab = text(4, 8, ry, W - 16, ROW_H, 494, LABEL, "", 0);
                        lab.clickMask = 2;
                        lab.actions = new String[]{"Select"};
                        l[r] = comps.size();
                        comps.add(lab);
                        v[r] = comps.size();
                        comps.add(text(4, 8, ry, W - 16, ROW_H, 494, VALUE, "", 2));
                    }
                    labels.add(l);
                    values.add(v);
                    if (s.barColour != 0) {
                        int by = y + HEADER_H + rowsH + 2;
                        comps.add(rect(4, 7, by - 1, W - 14, BAR_H + 2, BOX, false));   // frame
                        comps.add(rect(4, 8, by, W - 16, BAR_H, TRACK, true));          // track
                        bars.add(comps.size());
                        comps.add(rect(4, 8, by, 0, BAR_H, s.barColour, true));        // fill (width set at runtime)
                        InterfaceDefinition gloss = rect(4, 8, by, W - 16, 4, GLOSS, true);
                        gloss.opacity = 215;                                            // soft highlight on the top
                        comps.add(gloss);
                    } else {
                        bars.add(-1);
                    }
                    for (int b = 0; b < s.buttons; b++) {
                        int bx = 6 + (b % 2) * ((W - 12) / 2 + 2), by = y + HEADER_H + rowsH + barH + 3 + (b / 2) * (BUTTON_H + 3);
                        int bw = (W - 12) / 2 - 2;
                        if (s.buttons == 1) // a lone button spans the box
                            bw = W - 12;
                        // a real OSRS bezel button (2026-10-15; was a flat brown strip) with a small item icon and its label
                        InterfaceDefinition btn = BuildBondsInterface.copy(buttonT);
                        placeAt(btn, 4, bx, by, bw, BUTTON_H);
                        btn.clickMask = 2;
                        btn.actions = new String[]{"Open"};
                        btn.onMouseOverListener = new Object[]{44, -2147483645, 24470};
                        btn.onMouseLeaveListener = new Object[]{44, -2147483645, 4525};
                        btn.hasListener = true;
                        buttons.add(comps.size());
                        comps.add(btn);
                        InterfaceDefinition icon = BuildBondsInterface.copy(buttonT);
                        placeAt(icon, 4, bx + 5, by + (BUTTON_H - 16) / 2, 18, 16);
                        icon.spriteId = -1;
                        icon.spriteTiling = false;
                        icon.borderType = 0;
                        icon.shadowColor = 0;
                        buttonIcons.add(comps.size());
                        comps.add(icon);
                        buttonTexts.add(comps.size());
                        // label right of the icon, centred; 2-line labels ("Collection / log") get 12px line spacing
                        InterfaceDefinition label = text(4, bx + 25, by, bw - 27, BUTTON_H, 494, 0xff981f, "", 1);
                        label.lineHeight = 12;
                        comps.add(label);
                    }
                    y += HEADER_H + boxH + GAP;
                }
                int contentHeight = y;
                // title icon, left of the centred title
                comps.add(iconBox(store, g.icon, 15, 10, 18));

                // encode + verify
                List<byte[]> enc = new ArrayList<>();
                for (int c = 0; c < comps.size(); c++) {
                    InterfaceDefinition d = comps.get(c);
                    d.id = (g.id << 16) | c;
                    byte[] b = BuildItemStatsInterface.encodeIf3(d);
                    InterfaceDefinition back = loader.load(d.id, b);
                    for (Field f : InterfaceDefinition.class.getFields()) {
                        if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                        if (!Objects.deepEquals(f.get(back), f.get(d)))
                            throw new IllegalStateException(g.id + ":" + c + " " + f.getName() + " mismatch");
                    }
                    enc.add(b);
                }
                System.out.println(g.id + " " + g.title + ": " + comps.size() + " components, content height " + contentHeight);

                code.append("\tpublic static final Layout ").append(g.name).append(" = new Layout(").append(g.id)
                        .append(", ").append(contentHeight).append(",\n\t\t\tnew int[]{").append(join(headers)).append("},\n\t\t\tnew int[][]{")
                        .append(join2(labels)).append("},\n\t\t\tnew int[][]{").append(join2(values)).append("},\n\t\t\tnew int[]{")
                        .append(join(buttons)).append("},\n\t\t\tnew int[]{").append(join(buttonTexts)).append("},\n\t\t\tnew int[]{")
                        .append(join(buttonIcons)).append("},\n\t\t\tnew int[]{")
                        .append(join(bars)).append("});\n\n");

                if (apply)
                    write(storage, index, ff, forge, g.id, enc);
            }

            String out = "package io.ruin.model.inter.questtab.dashboard;\n\n"
                    + "/**\n * GENERATED by .dev/cache-restore-tool/src/BuildDashboardInterfaces.java -- do not edit by hand.\n"
                    + " * Component ids of the journal side-panel dashboards; rebuild the tool to change the layout.\n */\n"
                    + "public final class DashboardLayout {\n\n"
                    + "\tpublic static final int TITLE = 2, SCROLL = 4, SCROLLBAR = 5;\n"
                    + "\t/** Full width of a progress-bar fill (bars[section] = fill component, -1 = no bar). */\n"
                    + "\tpublic static final int BAR_WIDTH = " + (W - 16) + ";\n\n"
                    + "\t/** headers[section]; labels/values[section][row]; buttons/buttonTexts/buttonIcons in order; bars[section]. */\n"
                    + "\tpublic record Layout(int id, int contentHeight, int[] headers, int[][] labels, int[][] values, int[] buttons,\n"
                    + "\t\t\tint[] buttonTexts, int[] buttonIcons, int[] bars) {\n\t}\n\n" + code + "\tprivate DashboardLayout() {\n\t}\n}\n";
            if (apply || args[0].equals("verify")) {
                try (PrintWriter pw = new PrintWriter(new File(args[2]), "UTF-8")) {
                    pw.print(out);
                }
                System.out.println("wrote " + args[2]);
            }
            if (!apply)
                System.out.println("VERIFY OK -- nothing written to the cache.");
        }
    }

    /** Positions a template copy under parent (static, no actions or listeners until the caller sets them). */
    static void placeAt(InterfaceDefinition d, int parent, int x, int y, int w, int h) {
        d.parentId = (group << 16) | parent;
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

    static void write(Storage storage, Index index, ArchiveFiles ff, Archive template, int id, List<byte[]> comps) throws Exception {
        Archive archive = index.getArchive(id) != null ? index.getArchive(id) : index.addArchive(id);
        archive.setNameHash(-1);
        archive.setCompression(template.getCompression());
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
        storage.store(index.getId(), id, container.data);
        archive.setCrc(container.crc);
        archive.setCompressedSize(container.data.length);
        archive.setDecompressedSize(data.length);
        IndexData indexData = index.toIndexData();
        Container ic = new Container(index.getCompression(), -1);
        ic.compress(indexData.writeIndexData(), null);
        storage.store(255, index.getId(), ic.data);
        index.setCrc(ic.crc);
        System.out.println("APPLY complete -- interface " + id);
    }

    static InterfaceDefinition base(InterfaceDefinition t, int parent, int x, int y, int w, int h) {
        InterfaceDefinition d = BuildItemStatsInterface.copy(t);
        d.parentId = parent < 0 ? -1 : (group << 16) | parent;
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
        return d;
    }

    static InterfaceDefinition layer(int parent, int x, int y, int w, int h) {
        InterfaceDefinition d = base(layerT, parent, x, y, w, h);
        d.scrollWidth = 0;
        d.scrollHeight = 0;
        return d;
    }

    static InterfaceDefinition rect(int parent, int x, int y, int w, int h, int colour, boolean filled) {
        InterfaceDefinition d = base(rectT, parent, x, y, w, h);
        d.textColor = colour;
        d.filled = filled;
        d.opacity = 0;
        return d;
    }

    /**
     * Side-stone sprites sit on a larger canvas (e.g. 28x28 drawn at offset 3,5 of a 33x36 canvas) and IF3
     * graphics scale the whole canvas to the component. This sizes/places the component so the drawing itself
     * comes out {@code glyph} px and centred on (cx, cy).
     */
    static InterfaceDefinition iconBox(Store store, int sprite, int cx, int cy, int glyph) {
        net.runelite.cache.definitions.SpriteDefinition d = BuildTierPets.sprites(store).provide(sprite, 0);
        double scale = (double) glyph / Math.max(d.getWidth(), d.getHeight());
        int canvasW = Math.max(d.getMaxWidth(), d.getWidth()), canvasH = Math.max(d.getMaxHeight(), d.getHeight());
        int w = (int) Math.round(canvasW * scale), h = (int) Math.round(canvasH * scale);
        int x = (int) Math.round(cx - (d.getOffsetX() + d.getWidth() / 2D) * scale);
        int y = (int) Math.round(cy - (d.getOffsetY() + d.getHeight() / 2D) * scale);
        return graphic(0, x, y, w, h, sprite);
    }

    static InterfaceDefinition graphic(int parent, int x, int y, int w, int h, int sprite) {
        InterfaceDefinition d = base(graphicT, parent, x, y, w, h);
        d.spriteId = sprite;
        // the template is the forge close button: drop its hover scripts
        d.onLoadListener = d.onMouseOverListener = d.onMouseLeaveListener = d.onOpListener = d.onClickListener = null;
        d.onReleaseListener = d.onHoldListener = null;
        d.hasListener = false;
        return d;
    }

    static InterfaceDefinition text(int parent, int x, int y, int w, int h, int font, int colour, String s, int align) {
        InterfaceDefinition d = base(textT, parent, x, y, w, h);
        d.fontId = font;
        d.textColor = colour;
        d.text = s;
        d.xTextAlignment = align;
        d.yTextAlignment = 1;
        d.textShadowed = true;
        return d;
    }

    static String join(List<Integer> l) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < l.size(); i++) b.append(i > 0 ? ", " : "").append(l.get(i));
        return b.toString();
    }

    static String join2(List<int[]> l) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < l.size(); i++) {
            int[] a = l.get(i);
            b.append(i > 0 ? ", " : "").append("{");
            for (int j = 0; j < a.length; j++) b.append(j > 0 ? ", " : "").append(a[j]);
            b.append("}");
        }
        return b.toString();
    }
}

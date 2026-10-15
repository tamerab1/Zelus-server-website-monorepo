import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.InterfaceDefinition;
import net.runelite.cache.definitions.loaders.InterfaceLoader;
import net.runelite.cache.fs.*;

import java.io.File;
import java.lang.reflect.Field;
import java.util.*;

// Pit Championship Ladder (2026-10-15), server: activities/tournament/ChampionLadder.java.
//  - Interface 1169, opened by the Championship Board's "View": the Relic Forge frame (1002's 0-3) in the warm-stone theme
//    of the Scoreboard (1163). A subtitle, two recessed panels of 10 ranks each (1-10 left, 11-20 right; columns # /
//    Player / Pts / Wins / Played, alternating rows with hover), an inset "your rank" strip, the next-tournament
//    countdown and a "Tournament Hub" bezel button.
//  - Interface 1170: never opened. Ten hidden text components that hold the live top 5 (name, points) for the client's
//    Championship Board plugin, which reads them and draws the names onto the board in the world. A group that is never
//    opened is never unloaded, so the values stay put.
// Prints "CONST name = id" lines. Usage: verify|apply|rebuild <cachePath>
public class BuildChampionLadderInterface {
    static final int GROUP = 1169, DATA_GROUP = 1170, SRC = 1002;
    static final int GOLD = 0xffc35a, TEXT = 0xd8d0c0, GREY = 0x9a9a9a, WHITE = 0xffffff;
    static final int HEADING_FONT = 496, TEXT_FONT = 494, ROW_FONT = 495, TITLE_Y = 15;
    // 2026-10-15 polish: darker stone zebra rows with a 1px separator, numeric columns centred under their headers
    static final int PANEL_FILL = 0x36302a, HEAD_FILL = 0x2a2520, ROW_A = 0x1c1815, ROW_B = 0x151210, ROW_HOVER = 0x2c251d,
            ROW_LINE = 0x2a2219;
    static final int TAB_ACTIVE = 0x241f1a, SHADOW = 0x15120f, TRIM = 0x8a6a3a;
    static final int ROWS = 10, ROW_H = 19, PANEL_W = 228, PANEL_H = 213, PANEL_Y = 56;
    // 2026-10-15 r2: 19px rows fill the panels; subtitle y35 clear of the title bar; strip 271, footer row 291 (frame ends ~309)
    static int group = GROUP;

    public static void main(String[] args) throws Exception {
        boolean rebuild = args[0].equals("rebuild");
        boolean apply = args[0].equals("apply") || rebuild;
        try (Store store = new Store(new File(args[1]))) {
            store.load();
            Storage storage = store.getStorage();
            Index index = store.getIndex(IndexType.INTERFACES);
            for (int g : new int[]{GROUP, DATA_GROUP})
                if (index.getArchive(g) != null && apply && !rebuild)
                    throw new IllegalStateException("interface " + g + " already exists (use rebuild)");
            Archive srcArchive = index.getArchive(SRC);
            ArchiveFiles src = srcArchive.getFiles(storage.loadArchive(srcArchive));
            InterfaceLoader loader = new InterfaceLoader();

            // ---------------- 1169: the ladder window ----------------
            List<byte[]> comps = new ArrayList<>();
            List<InterfaceDefinition> want = new ArrayList<>();
            Map<String, Integer> ids = new LinkedHashMap<>();
            for (int c = 0; c <= 3; c++) {
                InterfaceDefinition d = loader.load((GROUP << 16) | c, src.findFile(c).getContents());
                if (d.parentId != -1)
                    d.parentId = (GROUP << 16) | (d.parentId & 0xffff);
                if (c == 3)
                    d.originalY = TITLE_Y;
                add(comps, want, d);
            }
            InterfaceDefinition layerT = loader.load(0, src.findFile(153).getContents());
            InterfaceDefinition[] rectT = {loader.load(0, src.findFile(159).getContents()),
                    loader.load(0, src.findFile(160).getContents()), loader.load(0, src.findFile(161).getContents())};
            InterfaceDefinition textT = loader.load(0, src.findFile(144).getContents());
            InterfaceDefinition rect = rectT[1];
            InterfaceDefinition buttonT = loader.load(0, src.findFile(169).getContents());

            ids.put("SUBTITLE", comps.size());
            InterfaceDefinition subtitle = text(textT, 0, 24, 35, 464, 14, TEXT_FONT, 0xdcdcdc, ""); // clear of the title bar
            subtitle.xTextAlignment = 1;
            add(comps, want, subtitle);

            int[] panelX = {24, 260};
            for (int side = 0; side < 2; side++) {
                int panel = panel(comps, want, layerT, rectT, 0, panelX[side], PANEL_Y, PANEL_W, PANEL_H);
                add(comps, want, solid(rect, panel, 4, 3, PANEL_W - 8, 17, HEAD_FILL));
                add(comps, want, solid(rect, panel, 4, 19, PANEL_W - 8, 1, SHADOW));
                add(comps, want, aligned(text(textT, panel, 6, 3, 22, 17, TEXT_FONT, GOLD, "#"), 1));
                add(comps, want, text(textT, panel, 32, 3, 88, 17, TEXT_FONT, GOLD, "Player"));
                add(comps, want, aligned(text(textT, panel, 120, 3, 34, 17, TEXT_FONT, GOLD, "Pts"), 1));
                add(comps, want, aligned(text(textT, panel, 155, 3, 30, 17, TEXT_FONT, GOLD, "Wins"), 1));
                add(comps, want, aligned(text(textT, panel, 186, 3, 36, 17, TEXT_FONT, GOLD, "Played"), 1));
                ids.put(side == 0 ? "FIRST_ROW_LEFT" : "FIRST_ROW_RIGHT", comps.size());
                for (int r = 0; r < ROWS; r++) {
                    int y = 21 + r * ROW_H;
                    int fill = r % 2 == 0 ? ROW_A : ROW_B;
                    InterfaceDefinition bg = solid(rect, panel, 4, y, PANEL_W - 8, ROW_H, fill);    // +0
                    hover(bg, fill, ROW_HOVER);
                    add(comps, want, bg);
                    add(comps, want, aligned(text(textT, panel, 6, y, 22, ROW_H, ROW_FONT, TEXT, ""), 1)); // +1 rank
                    add(comps, want, text(textT, panel, 32, y, 88, ROW_H, ROW_FONT, WHITE, ""));   // +2 name
                    add(comps, want, aligned(text(textT, panel, 120, y, 34, ROW_H, ROW_FONT, 0x00ff00, ""), 1)); // +3 pts
                    add(comps, want, aligned(text(textT, panel, 155, y, 30, ROW_H, ROW_FONT, WHITE, ""), 1));    // +4 wins
                    add(comps, want, aligned(text(textT, panel, 186, y, 36, ROW_H, ROW_FONT, 0xa0a0a0, ""), 1)); // +5 played
                    add(comps, want, solid(rect, panel, 4, y + ROW_H - 1, PANEL_W - 8, 1, ROW_LINE));            // +6 separator
                }
            }

            // personal strip: inset, dark bronze trim (no bright outline)
            add(comps, want, solid(rect, 0, 24, 271, 464, 18, TAB_ACTIVE));
            add(comps, want, solid(rect, 0, 24, 271, 464, 1, SHADOW));
            add(comps, want, outline(rect, 0, 24, 271, 464, 18, TRIM));
            ids.put("PERSONAL", comps.size());
            InterfaceDefinition personal = text(textT, 0, 28, 271, 456, 18, TEXT_FONT, WHITE, "");
            personal.xTextAlignment = 1;
            add(comps, want, personal);
            ids.put("COUNTDOWN", comps.size());
            add(comps, want, text(textT, 0, 26, 292, 340, 14, TEXT_FONT, 0xff981f, "")); // under the strip
            // "Tournament Hub" bezel button (OSRS button sprite + hover script 44)
            ids.put("HUB_BUTTON", comps.size());
            InterfaceDefinition hub = BuildBondsInterface.copy(buttonT);
            place(hub, 0, 376, 290, 112, 17);
            hub.clickMask = 2;
            hub.actions = new String[]{"Open"};
            hub.onMouseOverListener = new Object[]{44, -2147483645, 24470};
            hub.onMouseLeaveListener = new Object[]{44, -2147483645, 4525};
            hub.hasListener = true;
            add(comps, want, hub);
            InterfaceDefinition hubLabel = text(textT, 0, 376, 290, 112, 17, TEXT_FONT, 0xff981f, "Tournament Hub");
            hubLabel.xTextAlignment = 1;
            add(comps, want, hubLabel);
            // full-width centred footer (Division Rivals; the Pit ladder uses COUNTDOWN + the Hub button instead)
            ids.put("FOOTER", comps.size());
            InterfaceDefinition footer = text(textT, 0, 24, 292, 464, 14, TEXT_FONT, 0xa0a0a0, "");
            footer.xTextAlignment = 1;
            add(comps, want, footer);

            for (Map.Entry<String, Integer> e : ids.entrySet())
                System.out.println("CONST " + e.getKey() + " = " + e.getValue());
            check(loader, comps, want, GROUP);

            // ---------------- 1170: hidden data for the live board ----------------
            group = DATA_GROUP;
            List<byte[]> data = new ArrayList<>();
            List<InterfaceDefinition> dataWant = new ArrayList<>();
            for (int c = 0; c < 10; c++) {
                InterfaceDefinition d = text(textT, -1, 0, 0, 1, 1, TEXT_FONT, WHITE, "");
                d.parentId = -1;
                d.isHidden = true;
                add(data, dataWant, d);
            }
            check(loader, data, dataWant, DATA_GROUP);
            System.out.println("CONST DATA: names 0-4, points 5-9 in group " + DATA_GROUP);

            if (!apply) {
                System.out.println("VERIFY OK -- " + comps.size() + " + " + data.size() + " components, nothing written.");
                return;
            }
            BuildSpinnerInterface.write(storage, index, srcArchive, comps, GROUP);
            BuildSpinnerInterface.write(storage, index, srcArchive, data, DATA_GROUP);
            System.out.println("APPLY complete -- interface " + GROUP + " (" + comps.size() + ") and " + DATA_GROUP + " (" + data.size() + ").");
        }
    }

    static void check(InterfaceLoader loader, List<byte[]> comps, List<InterfaceDefinition> want, int g) throws Exception {
        for (int c = 0; c < comps.size(); c++) {
            InterfaceDefinition got = loader.load((g << 16) | c, comps.get(c));
            InterfaceDefinition exp = want.get(c);
            exp.id = (g << 16) | c;
            for (Field f : InterfaceDefinition.class.getFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                if (!Objects.deepEquals(f.get(got), f.get(exp)))
                    throw new IllegalStateException(g + ":" + c + " " + f.getName() + " " + f.get(got) + " != " + f.get(exp));
            }
        }
    }

    static InterfaceDefinition aligned(InterfaceDefinition d, int x) {
        d.xTextAlignment = x;
        return d;
    }

    static void hover(InterfaceDefinition d, int normal, int hovered) {
        d.onMouseOverListener = new Object[]{45, -2147483645, hovered};
        d.onMouseLeaveListener = new Object[]{45, -2147483645, normal};
        d.hasListener = true;
    }

    static int panel(List<byte[]> comps, List<InterfaceDefinition> want, InterfaceDefinition layerT, InterfaceDefinition[] rectT,
                     int parent, int x, int y, int w, int h) {
        int id = comps.size();
        InterfaceDefinition l = BuildBondsInterface.copy(layerT);
        place(l, parent, x, y, w, h);
        l.scrollWidth = 0;
        l.scrollHeight = 0;
        add(comps, want, l);
        add(comps, want, solid(rectT[1], id, 0, 0, w, h, PANEL_FILL));
        for (int k = 0; k < 3; k++) {
            InterfaceDefinition r = BuildBondsInterface.copy(rectT[k]);
            if (k == 2)
                r.textColor = 0x36302a;
            place(r, id, k, k, w - 2 * k, h - 2 * k);
            add(comps, want, r);
        }
        return id;
    }

    static InterfaceDefinition outline(InterfaceDefinition rect, int parent, int x, int y, int w, int h, int colour) {
        InterfaceDefinition o = BuildBondsInterface.copy(rect);
        place(o, parent, x, y, w, h);
        o.filled = false;
        o.textColor = colour;
        return o;
    }

    static InterfaceDefinition solid(InterfaceDefinition rect, int parent, int x, int y, int w, int h, int colour) {
        InterfaceDefinition d = BuildBondsInterface.copy(rect);
        place(d, parent, x, y, w, h);
        d.filled = true;
        d.textColor = colour;
        d.opacity = 0;
        return d;
    }

    static InterfaceDefinition text(InterfaceDefinition t, int parent, int x, int y, int w, int h, int font, int colour, String s) {
        InterfaceDefinition d = BuildBondsInterface.copy(t);
        place(d, parent, x, y, w, h);
        d.fontId = font;
        d.textColor = colour;
        d.text = s;
        d.xTextAlignment = 0;
        d.yTextAlignment = 1;
        d.textShadowed = true;
        return d;
    }

    static void add(List<byte[]> comps, List<InterfaceDefinition> want, InterfaceDefinition d) {
        comps.add(BuildBondsInterface.encodeIf3(d));
        want.add(d);
    }

    static void place(InterfaceDefinition d, int parent, int x, int y, int w, int h) {
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
}

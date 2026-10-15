import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.InterfaceDefinition;
import net.runelite.cache.definitions.loaders.InterfaceLoader;
import net.runelite.cache.fs.*;

import java.io.File;
import java.lang.reflect.Field;
import java.util.*;

// Builds interface group 1162, Edgeville PvP Rewards (activities/wilderness/EdgevillePvpRewards.java, server), layout
// round 3, in the Relic Forge frame and warm-stone theme:
//  - header stack: title (y12), rule sub-header (y32), round timer top-right (y48), Overview | Full Leaderboard tabs
//    (y66; flat stone, the active one has a 2px orange underline);
//  - Overview (y96+): Mains leaderboard (3 clickable rank rows with a Blood Money icon on the prize, a framed pot row),
//    the round milestone (crate on a warm pedestal, inset orange-gold progress bar with its text inside, progress /
//    rule text or a Claim button, next tier, loot preview overlay), Pures leaderboard;
//  - Full Leaderboard: top 10 of each division (clickable rows);
//  - a status strip (division / score / kills / rank) and the anti-boost footnote.
// Prints "CONST name = id" lines. Usage: verify|apply|rebuild <cachePath>
public class BuildPvpRewardsInterface {
    static final int GROUP = 1162, SRC = 1002;
    static final int GOLD = 0xffc35a, ORANGE = 0xff981f, TEXT = 0xd8d0c0, GREY = 0x8f8f8f, WHITE = 0xffffff;
    static final int HEADING_FONT = 496, BIG_FONT = 495, TEXT_FONT = 494, TITLE_Y = 12;
    static final int PANEL_FILL = 0x36302a, BORDER = 0x5a4d3a;
    static final int ROW_FILL = 0x2e2924, ROW_HOVER = 0x3a332b;
    static final int TAB_FILL = 0x2e2924, TAB_HOVER = 0x3a332b, SHADOW = 0x15120f, TRIM = 0x8a6a3a;
    static final int WELL = 0x1a1714, EDGE = 0x4a4033, FILL = 0xe0a030, FILL_SHINE = 0xffc860;
    static final int CONTENT_Y = 96, CONTENT_H = 168, FULL_ROWS = 10;

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
            InterfaceDefinition buttonT = loader.load(0, src.findFile(169).getContents());
            InterfaceDefinition labelT = loader.load(0, src.findFile(170).getContents());
            InterfaceDefinition rect = rectT[1];

            // ---- header stack ----
            ids.put("SUB_HEADER", comps.size());
            add(comps, want, centred(text(textT, 0, 24, 32, 464, 14, TEXT_FONT, GREY, "")));
            ids.put("TIMER", comps.size());
            InterfaceDefinition timer = text(textT, 0, 248, 48, 240, 15, TEXT_FONT, WHITE, "");
            timer.xTextAlignment = 2;
            add(comps, want, timer);
            ids.put("TAB_OVERVIEW", comps.size());
            tab(comps, want, rect, textT, 24, 66, 110, 22);
            ids.put("TAB_LEADERBOARD", comps.size());
            tab(comps, want, rect, textT, 136, 66, 130, 22);
            add(comps, want, solid(rect, 0, 24, 88, 464, 1, BORDER)); // a hairline under the tab row

            // ---- Overview ----
            ids.put("OVERVIEW", comps.size());
            int overview = comps.size();
            add(comps, want, layer(layerT, 0, 24, CONTENT_Y, 464, CONTENT_H));
            ids.put("MAINS", comps.size());
            leaderboard(comps, want, layerT, rectT, rect, textT, buttonT, overview, 0);
            ids.put("PURES", comps.size());
            leaderboard(comps, want, layerT, rectT, rect, textT, buttonT, overview, 314);

            int centre = panel(comps, want, layerT, rectT, overview, 156, 0, 152, CONTENT_H);
            add(comps, want, centred(text(textT, centre, 0, 3, 152, 16, HEADING_FONT, GOLD, "Round Milestone")));
            ids.put("MILESTONE_NAME", comps.size());
            add(comps, want, centred(text(textT, centre, 2, 18, 148, 13, TEXT_FONT, ORANGE, "")));
            // warm pedestal: stepped glow behind the socket
            add(comps, want, solid(rect, centre, 40, 32, 72, 46, 0x3a2614));
            add(comps, want, solid(rect, centre, 45, 34, 62, 42, 0x553416));
            add(comps, want, solid(rect, centre, 50, 36, 52, 38, 0x6e4418));
            add(comps, want, solid(rect, centre, 54, 37, 44, 36, 0x2a2018));
            add(comps, want, outline(rect, centre, 54, 37, 44, 36, ORANGE, false));
            ids.put("MILESTONE_ITEM", comps.size());
            InterfaceDefinition crate = copy(buttonT);
            place(crate, centre, 58, 39, 36, 32);
            crate.spriteId = -1;
            crate.spriteTiling = false;
            crate.borderType = 1;
            crate.shadowColor = 0x302020;
            crate.clickMask = 2;
            crate.actions = new String[]{"Inspect"};
            add(comps, want, crate);
            // inset progress bar: well, inner shadow top + left, light edge bottom + right, fill + highlight, text inside
            add(comps, want, solid(rect, centre, 10, 82, 132, 15, WELL));
            add(comps, want, solid(rect, centre, 10, 82, 132, 1, SHADOW));
            add(comps, want, solid(rect, centre, 10, 82, 1, 15, SHADOW));
            add(comps, want, solid(rect, centre, 10, 96, 132, 1, EDGE));
            add(comps, want, solid(rect, centre, 141, 82, 1, 15, EDGE));
            ids.put("BAR_FILL", comps.size());
            add(comps, want, solid(rect, centre, 11, 83, 130, 13, FILL));
            ids.put("BAR_SHINE", comps.size());
            add(comps, want, solid(rect, centre, 11, 83, 130, 4, FILL_SHINE));
            ids.put("BAR_TEXT", comps.size());
            add(comps, want, centred(text(textT, centre, 10, 82, 132, 15, TEXT_FONT, WHITE, "")));
            ids.put("PROGRESS", comps.size());
            InterfaceDefinition progress = centred(text(textT, centre, 4, 99, 144, 24, TEXT_FONT, WHITE, ""));
            progress.yTextAlignment = 0;
            progress.lineHeight = 12;
            add(comps, want, progress);
            ids.put("RULE", comps.size());
            InterfaceDefinition rule = centred(text(textT, centre, 4, 124, 144, 26, TEXT_FONT, 0x9a9a9a, ""));
            rule.yTextAlignment = 0;
            rule.lineHeight = 12;
            add(comps, want, rule);
            ids.put("CLAIM", comps.size());
            int claim = comps.size();
            InterfaceDefinition claimLayer = layer(layerT, centre, 10, 122, 132, 28);
            claimLayer.isHidden = true;
            add(comps, want, claimLayer);
            add(comps, want, outline(rect, claim, 0, 0, 132, 28, GOLD, false));
            add(comps, want, outline(rect, claim, 1, 1, 130, 26, ORANGE, false));
            ids.put("CLAIM_BUTTON", comps.size());
            bezel(comps, want, buttonT, labelT, claim, 2, 2, 128, 24, "Claim");
            ids.put("NEXT_TIER", comps.size());
            add(comps, want, centred(text(textT, centre, 4, 151, 144, 14, TEXT_FONT, ORANGE, "")));
            // loot preview overlay (hidden), covering the centre panel
            ids.put("LOOT", comps.size());
            int loot = comps.size();
            InterfaceDefinition lootLayer = layer(layerT, centre, 2, 2, 148, CONTENT_H - 4);
            lootLayer.isHidden = true;
            add(comps, want, lootLayer);
            add(comps, want, solid(rect, loot, 0, 0, 148, CONTENT_H - 4, 0x241f1a));
            add(comps, want, outline(rect, loot, 0, 0, 148, CONTENT_H - 4, TRIM, false));
            add(comps, want, centred(text(textT, loot, 0, 3, 148, 16, HEADING_FONT, GOLD, "Bounty Crate")));
            ids.put("LOOT_TEXT", comps.size());
            InterfaceDefinition lootText = text(textT, loot, 6, 21, 138, 112, TEXT_FONT, TEXT, "");
            lootText.yTextAlignment = 0;
            lootText.lineHeight = 12;
            add(comps, want, lootText);
            ids.put("LOOT_BACK", comps.size());
            bezel(comps, want, buttonT, labelT, loot, 34, 138, 80, 22, "Back");

            // ---- Full Leaderboard (hidden) ----
            ids.put("FULL", comps.size());
            int full = comps.size();
            InterfaceDefinition fullLayer = layer(layerT, 0, 24, CONTENT_Y, 464, CONTENT_H);
            fullLayer.isHidden = true;
            add(comps, want, fullLayer);
            ids.put("FULL_MAINS", comps.size());
            fullBoard(comps, want, layerT, rectT, rect, textT, full, 0);
            ids.put("FULL_PURES", comps.size());
            fullBoard(comps, want, layerT, rectT, rect, textT, full, 235);

            // ---- footer: status strip + footnote ----
            add(comps, want, solid(rect, 0, 24, 268, 464, 20, ROW_FILL));
            add(comps, want, solid(rect, 0, 24, 268, 464, 1, SHADOW));
            add(comps, want, outline(rect, 0, 24, 268, 464, 20, TRIM, false));
            ids.put("STATUS", comps.size());
            add(comps, want, centred(text(textT, 0, 28, 268, 456, 20, TEXT_FONT, WHITE, "")));
            ids.put("FOOTNOTE", comps.size());
            add(comps, want, centred(text(textT, 0, 24, 292, 464, 13, TEXT_FONT, GREY, "")));

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
            BuildSpinnerInterface.write(storage, index, srcArchive, comps, GROUP);
            System.out.println("APPLY complete -- interface " + GROUP + " with " + comps.size() + " components.");
        }
    }

    /**
     * Overview division column (5 + 2 + 3 x 7 + 3 = 31 comps): panel, heading (+5), sub (+6), 3 rank rows at +7 + 7r
     * (box [click: View profile, hover], border, rank, name, kills/score, BM icon, prize), pot frame fill + border + text
     * (+28..+30).
     */
    static void leaderboard(List<byte[]> comps, List<InterfaceDefinition> want, InterfaceDefinition layerT,
                            InterfaceDefinition[] rectT, InterfaceDefinition rect, InterfaceDefinition textT,
                            InterfaceDefinition buttonT, int parent, int x) {
        int p = panel(comps, want, layerT, rectT, parent, x, 0, 150, CONTENT_H);
        add(comps, want, centred(text(textT, p, 0, 3, 150, 16, HEADING_FONT, GOLD, "")));
        add(comps, want, centred(text(textT, p, 0, 17, 150, 12, TEXT_FONT, 0x9a9a9a, "")));
        for (int r = 0; r < 3; r++) {
            int y = 30 + r * 40;
            InterfaceDefinition box = solid(rect, p, 6, y, 138, 37, ROW_FILL);
            box.clickMask = 2;
            box.actions = new String[]{"View profile"};
            hover(box, ROW_FILL, ROW_HOVER);
            add(comps, want, box);
            add(comps, want, outline(rect, p, 6, y, 138, 37, BORDER, false));
            add(comps, want, centred(text(textT, p, 8, y, 26, 37, HEADING_FONT, GOLD, "")));
            add(comps, want, text(textT, p, 36, y + 1, 106, 12, TEXT_FONT, WHITE, ""));
            add(comps, want, text(textT, p, 36, y + 12, 106, 12, TEXT_FONT, 0x9a9a9a, ""));
            InterfaceDefinition bm = copy(buttonT);
            place(bm, p, 35, y + 23, 16, 14);
            bm.spriteId = -1;
            bm.spriteTiling = false;
            bm.borderType = 0;
            bm.shadowColor = 0;
            add(comps, want, bm);
            add(comps, want, text(textT, p, 53, y + 24, 89, 12, TEXT_FONT, 0x00ff00, ""));
        }
        add(comps, want, solid(rect, p, 6, 151, 138, 14, 0x241f1a));
        add(comps, want, outline(rect, p, 6, 151, 138, 14, TRIM, false));
        add(comps, want, centred(text(textT, p, 6, 151, 138, 14, TEXT_FONT, WHITE, "")));
    }

    /**
     * Full standings column (5 + 2 + 10 x 5 = 57 comps): panel, heading (+5), column header (+6), 10 rows at
     * +7 + 5r (box [click: View profile, hover], rank, name, kills, score).
     */
    static void fullBoard(List<byte[]> comps, List<InterfaceDefinition> want, InterfaceDefinition layerT,
                          InterfaceDefinition[] rectT, InterfaceDefinition rect, InterfaceDefinition textT, int parent, int x) {
        int p = panel(comps, want, layerT, rectT, parent, x, 0, 229, CONTENT_H);
        add(comps, want, centred(text(textT, p, 0, 3, 229, 16, HEADING_FONT, GOLD, "")));
        add(comps, want, text(textT, p, 8, 19, 213, 13, TEXT_FONT, 0x9a9a9a, "#     Name                         Kills    Score"));
        for (int r = 0; r < FULL_ROWS; r++) {
            int y = 33 + r * 13;
            InterfaceDefinition box = solid(rect, p, 5, y, 219, 13, r % 2 == 0 ? ROW_FILL : PANEL_FILL);
            box.clickMask = 2;
            box.actions = new String[]{"View profile"};
            hover(box, box.textColor, ROW_HOVER);
            add(comps, want, box);
            add(comps, want, text(textT, p, 8, y, 20, 13, TEXT_FONT, GOLD, ""));
            add(comps, want, text(textT, p, 30, y, 120, 13, TEXT_FONT, WHITE, ""));
            InterfaceDefinition kills = text(textT, p, 150, y, 30, 13, TEXT_FONT, TEXT, "");
            kills.xTextAlignment = 2;
            add(comps, want, kills);
            InterfaceDefinition score = text(textT, p, 184, y, 36, 13, TEXT_FONT, 0x00ff00, "");
            score.xTextAlignment = 2;
            add(comps, want, score);
        }
    }

    /** 3 comps: a flat stone sub-tab (click + hover), its 2px orange underline (shown when active), label. */
    static void tab(List<byte[]> comps, List<InterfaceDefinition> want, InterfaceDefinition rect, InterfaceDefinition textT,
                    int x, int y, int w, int h) {
        InterfaceDefinition b = solid(rect, 0, x, y, w, h, TAB_FILL);
        b.clickMask = 2;
        b.actions = new String[]{"View"};
        hover(b, TAB_FILL, TAB_HOVER);
        add(comps, want, b);
        InterfaceDefinition underline = solid(rect, 0, x, y + h - 2, w, 2, ORANGE);
        underline.isHidden = true;
        add(comps, want, underline);
        add(comps, want, centred(text(textT, 0, x, y, w, h - 2, TEXT_FONT, TEXT, "")));
    }

    static void bezel(List<byte[]> comps, List<InterfaceDefinition> want, InterfaceDefinition buttonT, InterfaceDefinition labelT,
                      int parent, int x, int y, int w, int h, String action) {
        InterfaceDefinition b = copy(buttonT);
        place(b, parent, x, y, w, h);
        b.clickMask = 2;
        b.actions = new String[]{action};
        b.onMouseOverListener = new Object[]{44, -2147483645, 24470};
        b.onMouseLeaveListener = new Object[]{44, -2147483645, 4525};
        b.hasListener = true;
        add(comps, want, b);
        InterfaceDefinition l = copy(labelT);
        place(l, parent, x + 1, y + 2, w - 2, h - 4);
        l.text = "";
        add(comps, want, l);
    }

    static void hover(InterfaceDefinition d, int normal, int hovered) {
        d.onMouseOverListener = new Object[]{45, -2147483645, hovered};
        d.onMouseLeaveListener = new Object[]{45, -2147483645, normal};
        d.hasListener = true;
    }

    static InterfaceDefinition centred(InterfaceDefinition d) {
        d.xTextAlignment = 1;
        return d;
    }

    static int panel(List<byte[]> comps, List<InterfaceDefinition> want, InterfaceDefinition layerT, InterfaceDefinition[] rectT,
                     int parent, int x, int y, int w, int h) {
        int id = comps.size();
        add(comps, want, layer(layerT, parent, x, y, w, h));
        add(comps, want, solid(rectT[1], id, 0, 0, w, h, PANEL_FILL));
        for (int k = 0; k < 3; k++) {
            InterfaceDefinition r = copy(rectT[k]);
            if (k == 2)
                r.textColor = 0x36302a;
            place(r, id, k, k, w - 2 * k, h - 2 * k);
            add(comps, want, r);
        }
        return id;
    }

    static InterfaceDefinition outline(InterfaceDefinition rect, int parent, int x, int y, int w, int h, int colour, boolean hidden) {
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

    static void add(List<byte[]> comps, List<InterfaceDefinition> want, InterfaceDefinition d) {
        comps.add(BuildBondsInterface.encodeIf3(d));
        want.add(d);
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
        d.onMouseOverListener = d.onMouseLeaveListener = null;
        d.hasListener = false;
    }

    static InterfaceDefinition copy(InterfaceDefinition s) {
        return BuildBondsInterface.copy(s);
    }
}

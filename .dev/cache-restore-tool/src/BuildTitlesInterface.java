import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.InterfaceDefinition;
import net.runelite.cache.definitions.loaders.InterfaceLoader;
import net.runelite.cache.fs.*;

import java.io.File;
import java.lang.reflect.Field;
import java.util.*;

// Builds interface group 1160, Zelus Titles (LoyaltyTitleInterface.java, server): the title list and its details in one
// window (replacing the 5105 list + the 5106 chatbox details), in the Relic Forge's frame and warm-stone theme.
//  - title row: owned count (top left), "ZELUS TITLES", close;
//  - 9 category tabs (hover + gold selected frame);
//  - 12 title cards, 2 columns x 6 rows: hover + gold selected frame, a status square (fill + border + glyph), the
//    title in its own colour and the requirement under it;
//  - action row: Prev, page text, Next, Clear, Apply (framed bezel buttons);
//  - a gold-framed details panel: header + equipped, preview, requirement (left); category, status, cost (right).
// Every section has its own box. Prints "CONST name = id" lines. Usage: verify|apply|rebuild <cachePath>
public class BuildTitlesInterface {
    static final int GROUP = 1160, SRC = 1002;
    static final int GOLD = 0xffc35a, TEXT = 0xd8d0c0, GREY = 0x9a9a9a;
    static final int HEADING_FONT = 496, BIG_FONT = 495, TEXT_FONT = 494, TITLE_Y = 14;
    static final int BOX = 0x433b31, BOX_HOVER = 0x524737, BORDER = 0x5a4d3a, CARD = 0x2e2924, CARD_HOVER = 0x38322a;
    static final int SQUARE_BORDER = 0x5a4d3a;
    static final int TABS = 9, CARDS = 12, LIST_TOP = 8;
    static final int COMPONENTS = 239;
    /** Selected card / active tab (2026-10-15 restyle, no neon 1px outlines): recessed fill + soft gold trim. */
    static final int ACTIVE_FILL = 0x1c1814, ACTIVE_TRIM = 0x4a3a24, ACTIVE_GLOW = 0x2e2418, TRIM = 0x5a4630; // dark bronze: b08d4a still read as yellow
    /** Tab icons (the old titles window's sprites): all, owned, locked, general, elite, pvm, pvp, skilling; donor = -1 ($ in the label). */
    static final int[] TAB_ICONS = {14705, 14697, 14698, 14699, 14700, 14701, 14702, 14703, -1};

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
            InterfaceDefinition rectT = loader.load(0, src.findFile(160).getContents());
            InterfaceDefinition innerT = loader.load(0, src.findFile(161).getContents());
            InterfaceDefinition textT = loader.load(0, src.findFile(144).getContents());
            InterfaceDefinition buttonT = loader.load(0, src.findFile(169).getContents());
            InterfaceDefinition labelT = loader.load(0, src.findFile(170).getContents());

            // 4: owned count, top left of the title row
            ids.put("OWNED", comps.size());
            add(comps, want, text(textT, 0, 24, 17, 120, 14, TEXT_FONT, TEXT, ""));

            // ---- 9 category tabs: box (hover), border, gold frame x2, icon, label ----
            ids.put("FIRST_TAB", comps.size());
            for (int t = 0; t < TABS; t++) {
                int x = 22 + t * 52;
                bezelTab(comps, want, rectT, buttonT, 0, x, 36, 50, 26, "View");
                // icon: 12x12 at 4px in, vertically centred (Donor has no sprite: a red "$" text icon)
                if (TAB_ICONS[t] != -1) {
                    InterfaceDefinition icon = copy(buttonT);
                    place(icon, 0, x + 5, 43, 12, 12);
                    icon.spriteId = TAB_ICONS[t];
                    icon.spriteTiling = false;
                    add(comps, want, icon);
                } else {
                    InterfaceDefinition dollar = text(textT, 0, x + 4, 40, 14, 18, BIG_FONT, 0xff3030, "$");
                    dollar.xTextAlignment = 1;
                    add(comps, want, dollar);
                }
                // label: from 18px in to 2px before the right edge, centred
                InterfaceDefinition l = text(textT, 0, x + 20, 36, 29, 26, TEXT_FONT, TEXT, "");
                l.xTextAlignment = 1;
                add(comps, want, l);
            }

            // ---- title list: a scrolling 2 x 6 grid of 218 x 48 cards (52px pitch) ----
            ids.put("SCROLL", comps.size());
            ids.put("SCROLLBAR", comps.size() + 1);
            add(comps, want, layer(layerT, 0, 24, 66, 448, 146));
            add(comps, want, layer(layerT, 0, 478, 66, 16, 146));
            int scroll = ids.get("SCROLL");
            ids.put("FIRST_CARD", comps.size());
            for (int c = 0; c < CARDS; c++) {
                int x = (c % 2) * 224, y = LIST_TOP + (c / 2) * 52; // 8px top padding: row 1 sits fully below the tabs
                int card = comps.size();
                add(comps, want, layer(layerT, scroll, x, y, 218, 48));
                InterfaceDefinition box = solid(rectT, card, 0, 0, 218, 48, CARD);
                box.clickMask = 2;
                box.actions = new String[]{"Select"};
                box.onMouseOverListener = new Object[]{45, -2147483645, CARD_HOVER};
                box.onMouseLeaveListener = new Object[]{45, -2147483645, CARD};
                box.hasListener = true;
                add(comps, want, box);
                add(comps, want, outline(rectT, card, 0, 0, 218, 48, BORDER, false));
                // selected (server shows +3..+5): recessed fill inside the border, soft gold trim, a dim inner glow
                InterfaceDefinition recess = solid(rectT, card, 1, 1, 216, 46, ACTIVE_FILL);
                recess.isHidden = true;
                add(comps, want, recess);
                add(comps, want, outline(rectT, card, 0, 0, 218, 48, ACTIVE_TRIM, true));
                add(comps, want, outline(rectT, card, 1, 1, 216, 46, ACTIVE_GLOW, true));
                // price icon, top right: an item (coins) or a red "$" text (donator points); hidden otherwise
                // coins: the pile (no stack number) with the price in its own text under it; donator: a red "$"
                InterfaceDefinition price = copy(buttonT);
                place(price, card, 188, 3, 28, 24);
                price.spriteId = -1;
                price.spriteTiling = false;
                add(comps, want, price);
                InterfaceDefinition glyph = text(textT, card, 190, 13, 22, 22, HEADING_FONT, 0xff3030, "");
                glyph.xTextAlignment = 1;
                add(comps, want, glyph);
                InterfaceDefinition priceText = text(textT, card, 170, 28, 46, 16, TEXT_FONT, 0xff981f, "");
                priceText.xTextAlignment = 1;
                add(comps, want, priceText);
                add(comps, want, text(textT, card, 9, 5, 176, 17, HEADING_FONT, TEXT, ""));
                InterfaceDefinition req = text(textT, card, 9, 24, 200, 21, TEXT_FONT, GREY, "");
                req.yTextAlignment = 0;
                req.lineHeight = 10;
                add(comps, want, req);
            }

            // ---- footer: Prev, page, Next, then Clear and Apply with gold outlines ----
            ids.put("PREV", comps.size());
            bezel(comps, want, buttonT, labelT, 24, 216, 64, 22);
            ids.put("PAGE", comps.size());
            InterfaceDefinition page = text(textT, 0, 90, 216, 70, 22, TEXT_FONT, TEXT, "");
            page.xTextAlignment = 1;
            add(comps, want, page);
            ids.put("NEXT", comps.size());
            bezel(comps, want, buttonT, labelT, 162, 216, 64, 22);
            ids.put("CLEAR", comps.size());
            bezel(comps, want, buttonT, labelT, 314, 216, 72, 22);
            ids.put("APPLY", comps.size());
            bezel(comps, want, buttonT, labelT, 390, 216, 98, 22);
            add(comps, want, outline(rectT, 0, 313, 215, 74, 24, TRIM, false));
            add(comps, want, outline(rectT, 0, 389, 215, 100, 24, ACTIVE_TRIM, false));

            // ---- details panel (gold framed) ----
            int panel = comps.size();
            add(comps, want, layer(layerT, 0, 24, 242, 464, 68));
            add(comps, want, solid(rectT, panel, 0, 0, 464, 68, 0x2e2924));
            add(comps, want, outline(rectT, panel, 0, 0, 464, 68, TRIM, false));
            add(comps, want, outline(rectT, panel, 1, 1, 462, 66, BORDER, false));
            add(comps, want, solid(rectT, panel, 290, 6, 1, 56, BORDER)); // column divider
            ids.put("DETAILS_HEADER", comps.size());
            add(comps, want, text(textT, panel, 10, 4, 276, 14, TEXT_FONT, GOLD, ""));
            ids.put("DETAILS_PREVIEW", comps.size());
            add(comps, want, text(textT, panel, 10, 19, 276, 15, BIG_FONT, TEXT, ""));
            ids.put("DETAILS_REQUIREMENT", comps.size());
            InterfaceDefinition req = text(textT, panel, 10, 35, 276, 31, TEXT_FONT, TEXT, "");
            req.yTextAlignment = 0;
            req.lineHeight = 10;
            add(comps, want, req);
            ids.put("DETAILS_CATEGORY", comps.size());
            add(comps, want, text(textT, panel, 298, 4, 160, 14, TEXT_FONT, GREY, ""));
            ids.put("DETAILS_STATUS", comps.size());
            InterfaceDefinition status = text(textT, panel, 298, 19, 160, 28, TEXT_FONT, GREY, "");
            status.yTextAlignment = 0;
            status.lineHeight = 11;
            add(comps, want, status);
            ids.put("DETAILS_COST", comps.size());
            add(comps, want, text(textT, panel, 298, 49, 160, 14, TEXT_FONT, GREY, ""));

            // inner bevel on every title card (appended so the ids above don't move): top highlight, bottom shadow
            ids.put("FIRST_BEVEL", comps.size());
            for (int c = 0; c < CARDS; c++) {
                int card = ids.get("FIRST_CARD") + c * 11;
                add(comps, want, solid(rectT, card, 1, 1, 216, 1, 0x4a4033));
                add(comps, want, solid(rectT, card, 1, 46, 216, 1, 0x1a1612));
            }

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
            BuildSpinnerInterface.write(storage, index, srcArchive, comps, GROUP);
            System.out.println("APPLY complete -- interface " + GROUP + " with " + comps.size() + " components.");
        }
    }

    /** 4 comps: fill (click + hover), border, 2px gold selected frame (hidden). */
    static void selectable(List<byte[]> comps, List<InterfaceDefinition> want, InterfaceDefinition rect, int parent,
                           int x, int y, int w, int h, int fill, int hover, String action) {
        InterfaceDefinition b = solid(rect, parent, x, y, w, h, fill);
        b.clickMask = 2;
        b.actions = new String[]{action};
        b.onMouseOverListener = new Object[]{45, -2147483645, hover};
        b.onMouseLeaveListener = new Object[]{45, -2147483645, fill};
        b.hasListener = true;
        add(comps, want, b);
        add(comps, want, outline(rect, parent, x, y, w, h, BORDER, false));
        add(comps, want, outline(rect, parent, x, y, w, h, GOLD, true));
        add(comps, want, outline(rect, parent, x + 1, y + 1, w - 2, h - 2, GOLD, true));
    }

    /**
     * Category tab, 4 comps in selectable()'s layout so the server's TAB_FRAME (+2/+3) still works: +0 real OSRS bezel
     * button (1002:169, hover script 44), +1 permanently hidden spacer, +2 dark recess (opacity 110), +3 soft gold trim.
     */
    static void bezelTab(List<byte[]> comps, List<InterfaceDefinition> want, InterfaceDefinition rect, InterfaceDefinition buttonT,
                         int parent, int x, int y, int w, int h, String action) {
        InterfaceDefinition b = copy(buttonT);
        place(b, parent, x, y, w, h);
        b.clickMask = 2;
        b.actions = new String[]{action};
        b.onMouseOverListener = new Object[]{44, -2147483645, 24470};
        b.onMouseLeaveListener = new Object[]{44, -2147483645, 4525};
        b.hasListener = true;
        add(comps, want, b);
        InterfaceDefinition spacer = solid(rect, parent, x, y, 1, 1, 0);
        spacer.isHidden = true;
        add(comps, want, spacer);
        InterfaceDefinition recess = solid(rect, parent, x + 2, y + 2, w - 4, h - 4, 0x120f0c);
        recess.opacity = 70; // deeper pressed-stone recess
        recess.isHidden = true;
        add(comps, want, recess);
        add(comps, want, outline(rect, parent, x + 1, y + 1, w - 2, h - 2, ACTIVE_TRIM, true));
    }

    /** 2 comps: the OSRS bezel button (hover script 44) and its label (sent by the server). */
    static void bezel(List<byte[]> comps, List<InterfaceDefinition> want, InterfaceDefinition buttonT, InterfaceDefinition labelT,
                      int x, int y, int w, int h) {
        InterfaceDefinition b = copy(buttonT);
        place(b, 0, x, y, w, h);
        b.clickMask = 2;
        b.actions = new String[]{"Select"};
        b.onMouseOverListener = new Object[]{44, -2147483645, 24470};
        b.onMouseLeaveListener = new Object[]{44, -2147483645, 4525};
        b.hasListener = true;
        add(comps, want, b);
        InterfaceDefinition l = copy(labelT);
        place(l, 0, x + 1, y + 2, w - 2, h - 4);
        l.text = "";
        add(comps, want, l);
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

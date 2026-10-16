import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.InterfaceDefinition;
import net.runelite.cache.definitions.loaders.InterfaceLoader;
import net.runelite.cache.fs.*;

import java.io.File;
import java.lang.reflect.Field;
import java.util.*;

// Builds interface group 1161 (layout round 2: recessed OSRS tabs, segmented Items|Players, headed + inset list,
// footer pager), the Zelus Market (tradepost/inter/ZelusMarket.java, server), replacing the Reason trade
// post screens 1131/1132 (the 1133 inventory side panel is kept for "click an item to sell"). Relic Forge frame and
// warm-stone theme:
//  - title row: coffer total (left), coins icon + "Zelus Market" (centre), live listing count (right);
//  - 5 tabs: Browse / Featured / My Shop / History / Lookup (hover + gold selected frame);
//  - filter bar: recessed search box (magnifier, Clear), Items / Players toggles, sort cycle, page < n/m >;
//    in My Shop the filter bar is swapped for an action bar (Sell item, Buy offer, Collect coins, Collect items);
//  - a framed, scrolling list of 40 listing rows: item, name, "N from Seller · Placed 3h ago", a stock bar
//    (track + fill + "x/y left"), price each + total, and a bezel action button (Buy / Cancel / Collect / View);
//  - the buy modal (hidden): item, name, price/stock/coins lines, Buy 1 / Buy 5 / Buy X / Buy All, Cancel.
// Prints "CONST name = id" lines for ZelusMarket.java. Usage: verify|apply|rebuild <cachePath>
public class BuildMarketInterface {
    static final int GROUP = 1161, SRC = 1002;
    static final int GOLD = 0xffc35a, TEXT = 0xd8d0c0, GREY = 0x9a9a9a;
    static final int HEADING_FONT = 496, BIG_FONT = 495, TEXT_FONT = 494, TITLE_Y = 14;
    static final int PANEL_FILL = 0x36302a, BOX = 0x433b31, BOX_HOVER = 0x524737, BORDER = 0x5a4d3a;
    static final int ROW = 0x2e2924, ROW_HOVER = 0x3a332b, SEARCH_FILL = 0x25201b, SEARCH_HOVER = 0x2f2924;
    static final int BAR_TRACK = 0x181512, BAR_FILL = 0x2e7d32, BAR_SHINE = 0x5ccf5c, BAR_DEPTH = 0x1f7a2a, BAR_EDGE = 0x0e0c0a;
    static final int TABS = 5, ROWS = 40, ROW_H = 46, ROW_PITCH = 48, ROW_W = 418, LIST_H = 176;
    /** OSRS tab stone: raised (hover lighter); active = recessed dark well, inner shadow and a bronze trim. */
    static final int TAB_FILL = 0x38322a, TAB_HOVER = 0x453d33, TAB_ACTIVE = 0x241f1a, SHADOW = 0x15120f, TRIM = 0x8a6a3a;
    static final int HEADER_FILL = 0x2a2520, WELL = 0x221d18, EDGE = 0x4a4033, FEATURED = 0xc8a050;

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
            // 0-3: root, background, close, title (title moved up to TITLE_Y)
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
            BUTTON = buttonT;
            InterfaceDefinition labelT = loader.load(0, src.findFile(170).getContents());
            InterfaceDefinition rect = rectT[1];

            // ---- title row: coffer (left), the centred title (comp 3), listing count (right) ----
            ids.put("COFFER", comps.size());
            add(comps, want, text(textT, 0, 26, 17, 170, 14, TEXT_FONT, TEXT, ""));
            ids.put("STATS", comps.size());
            InterfaceDefinition stats = text(textT, 0, 270, 17, 180, 14, TEXT_FONT, TEXT, ""); // ends 38px left of the close button
            stats.xTextAlignment = 2;
            add(comps, want, stats);

            // ---- 5 OSRS-style tabs: raised stone (hover); the active one is recessed (dark fill, inner shadow, bronze trim) ----
            ids.put("FIRST_TAB", comps.size());
            for (int t = 0; t < TABS; t++)
                tab(comps, want, rect, textT, 0, 24 + t * 93, 36, 90, 20);

            // ---- filter bar (a layer, hidden in My Shop) ----
            ids.put("FILTER_BAR", comps.size());
            int filter = comps.size();
            add(comps, want, layer(layerT, 0, 24, 61, 464, 18));
            // search: box (click), text, border, magnifier, Clear box + label
            ids.put("SEARCH", comps.size());
            // a recessed slate field (dark fill, near-black rim) on the same 18px baseline as the toggles and sort
            InterfaceDefinition box = solid(rect, filter, 0, 0, 232, 18, 0x1a1612);
            box.clickMask = 2;
            box.actions = new String[]{"Search"};
            hover(box, 0x1a1612, 0x221d18);
            add(comps, want, box);
            ids.put("SEARCH_TEXT", comps.size());
            add(comps, want, text(textT, filter, 20, 0, 164, 18, TEXT_FONT, GREY, ""));
            add(comps, want, outline(rect, filter, 0, 0, 232, 18, 0x0d0b09, false));
            InterfaceDefinition glass = itemSlot(buttonT, filter, 3, 2, 14, 14, null);
            glass.borderType = 0;
            glass.shadowColor = 0;
            glass.spriteId = 1043;
            add(comps, want, glass);
            ids.put("SEARCH_CLEAR", comps.size());
            InterfaceDefinition clear = solid(rect, filter, 188, 1, 43, 16, TAB_FILL);
            clear.clickMask = 2;
            clear.actions = new String[]{"Clear search"};
            hover(clear, TAB_FILL, TAB_HOVER);
            add(comps, want, clear);
            InterfaceDefinition clearLabel = text(textT, filter, 188, 1, 43, 16, TEXT_FONT, GREY, "Clear");
            clearLabel.xTextAlignment = 1;
            add(comps, want, clearLabel);
            // Items | Players: one segmented control (two halves, one frame, a divider)
            ids.put("TOGGLE_ITEMS", comps.size());
            segment(comps, want, rect, textT, filter, 240, 0, 56, 18, "Items");
            ids.put("TOGGLE_PLAYERS", comps.size());
            segment(comps, want, rect, textT, filter, 298, 0, 56, 18, "Players");
            // sort cycle, flush with the right edge
            ids.put("SORT", comps.size());
            button(comps, want, rect, textT, filter, 360, 0, 104, 18, "");

            // ---- My Shop action bar (a layer, hidden outside My Shop): 4 bezel buttons ----
            ids.put("ACTION_BAR", comps.size());
            int actions = comps.size();
            InterfaceDefinition actionLayer = layer(layerT, 0, 24, 60, 464, 20);
            actionLayer.isHidden = true;
            add(comps, want, actionLayer);
            ids.put("ACTION_SELL", comps.size());
            // 4 equal 111px bezel buttons, 6px apart (0 / 117 / 234 / 351 .. 462)
            bezel(comps, want, buttonT, labelT, actions, 0, 0, 111, 21, "Select");
            ids.put("ACTION_BUY", comps.size());
            bezel(comps, want, buttonT, labelT, actions, 117, 0, 111, 21, "Select");
            ids.put("ACTION_COINS", comps.size());
            bezel(comps, want, buttonT, labelT, actions, 234, 0, 111, 21, "Collect");
            ids.put("ACTION_ITEMS", comps.size());
            bezel(comps, want, buttonT, labelT, actions, 351, 0, 111, 21, "Collect");

            // ---- the listing panel: column headers, a recessed list inset, and a footer with the pager ----
            int panel = panel(comps, want, layerT, rectT, 0, 24, 83, 464, 223);
            add(comps, want, solid(rect, panel, 5, 5, 454, 15, HEADER_FILL));
            add(comps, want, text(textT, panel, 52, 5, 150, 15, TEXT_FONT, GOLD, "Item / Seller"));
            InterfaceDefinition stockHead = text(textT, panel, 218, 5, 80, 15, TEXT_FONT, GOLD, "Stock");
            stockHead.xTextAlignment = 1;
            add(comps, want, stockHead);
            InterfaceDefinition priceHead = text(textT, panel, 284, 5, 64, 15, TEXT_FONT, GOLD, "Price");
            priceHead.xTextAlignment = 2;
            add(comps, want, priceHead);
            // inset: dark well, 1px inner shadow top + left, light edge bottom + right
            add(comps, want, solid(rect, panel, 5, 22, 454, LIST_H + 4, 0x262019));
            add(comps, want, solid(rect, panel, 5, 22, 454, 1, SHADOW));
            add(comps, want, solid(rect, panel, 5, 22, 1, LIST_H + 4, SHADOW));
            add(comps, want, solid(rect, panel, 5, 25 + LIST_H, 454, 1, EDGE));
            add(comps, want, solid(rect, panel, 458, 22, 1, LIST_H + 4, EDGE));
            ids.put("SCROLL", comps.size());
            ids.put("SCROLLBAR", comps.size() + 1);
            add(comps, want, layer(layerT, panel, 8, 24, ROW_W, LIST_H));
            add(comps, want, layer(layerT, panel, 441, 24, 16, LIST_H));
            int scroll = ids.get("SCROLL");
            ids.put("FIRST_ROW", comps.size());
            for (int r = 0; r < ROWS; r++) {
                int row = comps.size();
                add(comps, want, layer(layerT, scroll, 0, r * ROW_PITCH, ROW_W, ROW_H));      // +0
                // alternating stone rows, light enough that dark items (a Zamorak godsword) keep their silhouette
                int zebra = r % 2 == 0 ? 0x2e261e : 0x28221b;
                InterfaceDefinition fill = solid(rect, row, 0, 0, ROW_W, ROW_H, zebra);       // +1
                fill.clickMask = 2;
                fill.actions = new String[]{"Select"};
                hover(fill, zebra, 0x3a3128);
                add(comps, want, fill);
                add(comps, want, outline(rect, row, 0, 0, ROW_W, ROW_H, 0x15120f, false));     // +2 dark bevel divider
                add(comps, want, outline(rect, row, 0, 0, ROW_W, ROW_H, TRIM, true));          // +3 featured trim
                InterfaceDefinition stripe = solid(rect, row, 1, 1, 3, ROW_H - 2, FEATURED);   // +4 featured stripe
                stripe.isHidden = true;
                add(comps, want, stripe);
                add(comps, want, solid(rect, row, 5, 6, 38, 34, 0x3b3329));                   // +5 item slot (mid stone)
                add(comps, want, outline(rect, row, 5, 6, 38, 34, 0x5a4d3a, false));          // +6 its rim
                add(comps, want, itemSlot(buttonT, row, 6, 7, 36, 32, "Examine"));            // +7
                add(comps, want, text(textT, row, 48, 6, 158, 15, TEXT_FONT, 0xff981f, ""));   // +8 name
                add(comps, want, text(textT, row, 48, 24, 158, 15, TEXT_FONT, 0x8f8f8f, ""));  // +9 seller line
                // stock: "x / y left" above a slim 8px meter (2026-10-16 r2: the old 12px bar was stretched to 18px by
                // clientscript 10606 and covered the "(total)" line). Only the fill is resized, by 10754 = 10606
                // with height 6. The highlight / shade lines are kept (hidden, 0 size) so the row offsets don't move.
                add(comps, want, solid(rect, row, 210, 21, 68, 8, BAR_TRACK));               // +10 track
                add(comps, want, solid(rect, row, 211, 22, 66, 6, BAR_FILL));                 // +11 fill
                InterfaceDefinition shine = solid(rect, row, 211, 22, 0, 0, BAR_SHINE);
                shine.isHidden = true;
                add(comps, want, shine);                                                      // +12 (unused)
                InterfaceDefinition depth = solid(rect, row, 211, 27, 0, 0, BAR_DEPTH);
                depth.isHidden = true;
                add(comps, want, depth);                                                      // +13 (unused)
                add(comps, want, outline(rect, row, 210, 21, 68, 8, BAR_EDGE, false));        // +14 border
                InterfaceDefinition barText = text(textT, row, 206, 5, 76, 14, TEXT_FONT, 0xffffff, "");
                barText.xTextAlignment = 1;
                add(comps, want, barText);                                                    // +15 "x / y left" above the bar
                InterfaceDefinition price = text(textT, row, 280, 6, 60, 15, TEXT_FONT, 0x00ff00, "");
                price.xTextAlignment = 2;
                add(comps, want, price);                                                      // +16
                InterfaceDefinition total = text(textT, row, 264, 30, 76, 15, TEXT_FONT, 0xa0a0a0, "");
                total.xTextAlignment = 2;
                add(comps, want, total);                                                      // +17
                bezel(comps, want, buttonT, labelT, row, 346, 11, 52, 24, "Select");          // +18, +19 (16px clear of the scrollbar)
            }
            ids.put("EMPTY", comps.size());
            InterfaceDefinition empty = text(textT, panel, 12, 70, 440, 60, BIG_FONT, GREY, "");
            empty.xTextAlignment = 1;
            empty.lineHeight = 16;
            empty.isHidden = true;
            add(comps, want, empty);
            // footer: "Showing 1-40 of N" (left), pager < n/m > flush right
            // footer: a 16px strip, 3px clear of the list above and of the frame below
            add(comps, want, solid(rect, panel, 5, 203, 454, 16, HEADER_FILL));
            ids.put("SHOWING", comps.size());
            add(comps, want, text(textT, panel, 10, 203, 240, 16, TEXT_FONT, GREY, ""));
            ids.put("PREV", comps.size());
            button(comps, want, rect, textT, panel, 373, 203, 22, 16, "");
            ids.put("PAGE", comps.size());
            InterfaceDefinition page = text(textT, panel, 397, 203, 42, 16, TEXT_FONT, TEXT, "");
            page.xTextAlignment = 1;
            add(comps, want, page);
            ids.put("NEXT", comps.size());
            button(comps, want, rect, textT, panel, 437, 203, 22, 16, "");

            // ---- buy modal (hidden) ----
            ids.put("MODAL", comps.size());
            int modal = comps.size();
            InterfaceDefinition modalLayer = layer(layerT, 0, 126, 104, 260, 150);
            modalLayer.isHidden = true;
            add(comps, want, modalLayer);
            add(comps, want, solid(rect, modal, 0, 0, 260, 150, 0x2a2520));
            add(comps, want, outline(rect, modal, 0, 0, 260, 150, TRIM, false));
            add(comps, want, outline(rect, modal, 1, 1, 258, 148, SHADOW, false));
            add(comps, want, outline(rect, modal, 2, 2, 256, 146, BORDER, false));
            add(comps, want, solid(rect, modal, 10, 10, 40, 36, 0x1a1612));
            ids.put("MODAL_ITEM", comps.size());
            add(comps, want, itemSlot(buttonT, modal, 12, 12, 36, 32, "Examine"));
            ids.put("MODAL_TITLE", comps.size());
            add(comps, want, text(textT, modal, 58, 8, 194, 17, HEADING_FONT, GOLD, ""));
            ids.put("MODAL_INFO", comps.size());
            InterfaceDefinition info = text(textT, modal, 58, 26, 194, 50, TEXT_FONT, TEXT, "");
            info.yTextAlignment = 0;
            info.lineHeight = 12;
            add(comps, want, info);
            ids.put("MODAL_BUY_1", comps.size());
            bezel(comps, want, buttonT, labelT, modal, 10, 86, 56, 24, "Buy");
            ids.put("MODAL_BUY_5", comps.size());
            bezel(comps, want, buttonT, labelT, modal, 71, 86, 56, 24, "Buy");
            ids.put("MODAL_BUY_X", comps.size());
            bezel(comps, want, buttonT, labelT, modal, 132, 86, 56, 24, "Buy");
            ids.put("MODAL_BUY_ALL", comps.size());
            bezel(comps, want, buttonT, labelT, modal, 193, 86, 57, 24, "Buy");
            ids.put("MODAL_CANCEL", comps.size());
            bezel(comps, want, buttonT, labelT, modal, 90, 118, 80, 22, "Cancel");

            // pager arrows (BuildBrandIcons 24528 / 24529 gold, 24530 / 24531 dim): appended so they draw over the buttons
            ids.put("PREV_ARROW", comps.size());
            InterfaceDefinition prevArrow = itemSlot(buttonT, panel, 375, 203, 18, 16, null);
            prevArrow.borderType = 0;
            prevArrow.shadowColor = 0;
            prevArrow.spriteId = 24528;
            add(comps, want, prevArrow);
            ids.put("NEXT_ARROW", comps.size());
            InterfaceDefinition nextArrow = itemSlot(buttonT, panel, 439, 203, 18, 16, null);
            nextArrow.borderType = 0;
            nextArrow.shadowColor = 0;
            nextArrow.spriteId = 24529;
            add(comps, want, nextArrow);

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

    /** The OSRS bezel button sprite component (1002:169), set in main. */
    static InterfaceDefinition BUTTON;
    /** Pressed stone (2026-10-15): a deeper recess and a dark edge; the soft gold trim kept reading as a yellow box. */
    static final int ACTIVE_SHADE = 0x0d0b09, ACTIVE_TRIM = 0x2b231a;

    /**
     * 4 comps: a raised OSRS bezel button (sprite, hover script 44) as a tab: the button (click), the active state
     * (hidden): a dark translucent recess + a soft gold trim, then the label (bold when {@code bold}).
     * Server: active parts +1..+2, label +3.
     */
    static void bezelTab(List<byte[]> comps, List<InterfaceDefinition> want, InterfaceDefinition rect, InterfaceDefinition textT,
                         int parent, int x, int y, int w, int h, String action, boolean bold, String label) {
        InterfaceDefinition b = copy(BUTTON);
        place(b, parent, x, y, w, h);
        b.clickMask = 2;
        b.actions = new String[]{action};
        b.onMouseOverListener = new Object[]{44, -2147483645, 24470};
        b.onMouseLeaveListener = new Object[]{44, -2147483645, 4525};
        b.hasListener = true;
        add(comps, want, b);
        // pressed: the recess covers the whole button (the sprite's light bevel edge read as a yellow frame)
        InterfaceDefinition recess = solid(rect, parent, x, y, w, h, ACTIVE_SHADE);
        recess.opacity = 60;
        recess.isHidden = true;
        add(comps, want, recess);
        add(comps, want, outline(rect, parent, x, y, w, h, ACTIVE_TRIM, true));
        InterfaceDefinition l = text(textT, parent, x, y, w, h, bold ? HEADING_FONT : TEXT_FONT, TEXT, label);
        l.xTextAlignment = 1;
        add(comps, want, l);
    }

    static void tab(List<byte[]> comps, List<InterfaceDefinition> want, InterfaceDefinition rect, InterfaceDefinition textT,
                    int parent, int x, int y, int w, int h) {
        bezelTab(comps, want, rect, textT, parent, x, y, w, h, "View", true, "");
    }

    static void segment(List<byte[]> comps, List<InterfaceDefinition> want, InterfaceDefinition rect, InterfaceDefinition textT,
                        int parent, int x, int y, int w, int h, String label) {
        bezelTab(comps, want, rect, textT, parent, x, y, w, h, "Select", false, label);
    }

    static void button(List<byte[]> comps, List<InterfaceDefinition> want, InterfaceDefinition rect, InterfaceDefinition textT,
                       int parent, int x, int y, int w, int h, String label) {
        bezelTab(comps, want, rect, textT, parent, x, y, w, h, "Select", false, label);
    }

    /** 5 comps: layer, fill, and the 1002 frame's 3 rects (the filled inner one is the visible warm-stone colour). */
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

    /** 4 comps: fill (click + hover), border, 2px gold selected frame (hidden). */
    static void selectable(List<byte[]> comps, List<InterfaceDefinition> want, InterfaceDefinition rect, int parent,
                           int x, int y, int w, int h, int fill, int hover, String action) {
        InterfaceDefinition b = solid(rect, parent, x, y, w, h, fill);
        b.clickMask = 2;
        b.actions = new String[]{action};
        hover(b, fill, hover);
        add(comps, want, b);
        add(comps, want, outline(rect, parent, x, y, w, h, BORDER, false));
        add(comps, want, outline(rect, parent, x, y, w, h, GOLD, true));
        add(comps, want, outline(rect, parent, x + 1, y + 1, w - 2, h - 2, GOLD, true));
    }

    /** 2 comps: the OSRS bezel button (hover script 44) and its label (sent by the server). */
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

    /** A graphic component the server fills with an item (if_setobject), optionally with an op. */
    static InterfaceDefinition itemSlot(InterfaceDefinition buttonT, int parent, int x, int y, int w, int h, String op) {
        InterfaceDefinition s = copy(buttonT);
        place(s, parent, x, y, w, h);
        s.spriteId = -1;
        s.spriteTiling = false;
        s.borderType = 1;
        s.shadowColor = 0x302020;
        if (op != null) {
            s.clickMask = 2;
            s.actions = new String[]{op};
        }
        return s;
    }

    static void hover(InterfaceDefinition d, int normal, int hovered) {
        d.onMouseOverListener = new Object[]{45, -2147483645, hovered};
        d.onMouseLeaveListener = new Object[]{45, -2147483645, normal};
        d.hasListener = true;
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

import net.runelite.cache.definitions.InterfaceDefinition;
import net.runelite.cache.fs.Store;

import java.io.File;

// Builds interface group 1161 as the Zelus Trading Post (2026-10-20 redesign after the classic OSRS Trading Post
// reference, Desktop/marketex.jpg; replaces BuildMarketInterface's tabbed Zelus Market in the same group). Server:
// tradepost/inter/ZelusMarket.java (ids from the CONST lines). Every button is the OSRS stone button (IfKit.stoneButton:
// rivet corners 913-920, hover lightens the stone, the pressed pieces mark the active view).
//  - left column: "Search for": Item | Player, Recent Listings, View History; "Collection box": Collect Items
//    (pack icon, "(n items)") and Collect Earnings (money sack, "(n coins)");
//  - right column: a heading ("My Offers" + "(used/max)", or the view's name, a Sort cycle and Back), a recessed list
//    with an OSRS scrollbar (40 rows: item, name, detail line, featured trim, action button), a footer strip inside the
//    inset (range + page arrows, or a hint), and Buy Offer (green crate) / Sell Offer (orange crate) under it;
//  - the buy modal (hidden) over the list: item, name, price / stock / seller / coins, Buy 1 / 5 / X / All, Cancel.
// Usage: verify|rebuild <cachePath>
public class BuildTradingPostInterface {
    static final int GROUP = 1161, ROWS = 40, ROW_H = 40, ROW_PITCH = 42, ROW_W = 249;
    static final int LIST_X = 3, LIST_Y = 3, LIST_H = 150, INSET_W = 272, INSET_H = 174;
    static final int INSET_FILL = 0x3d362d, INSET_EDGE = 0x1d1915, INSET_RIM = 0x2b2620, ROW_A = 0x463f34, ROW_B = 0x423b31,
            ROW_HOVER = 0x4f4739, FOOTER = 0x342e26;

    public static void main(String[] args) throws Exception {
        boolean apply = args[0].equals("rebuild");
        try (Store store = new Store(new File(args[1]))) {
            store.load();
            IfKit k = new IfKit(GROUP, store, true);

            // ---- left column (x 26..206) ----
            k.add(IfKit.centred(k.text(0, 26, 48, 180, 18, IfKit.HEADING_FONT, IfKit.ORANGE, "Search for")));
            k.mark("SEARCH_ITEM");
            k.stoneButton(0, 26, 69, 86, 26, "Search items", IfKit.BIG_FONT, "Item");
            k.mark("SEARCH_PLAYER");
            k.stoneButton(0, 120, 69, 86, 26, "Search players", IfKit.BIG_FONT, "Player");
            k.mark("RECENT");
            k.stoneButton(0, 26, 101, 180, 26, "View", IfKit.BIG_FONT, "Recent Listings");
            k.mark("HISTORY");
            k.stoneButton(0, 26, 133, 180, 26, "View", IfKit.BIG_FONT, "View History");

            k.add(IfKit.centred(k.text(0, 26, 172, 180, 18, IfKit.HEADING_FONT, IfKit.ORANGE, "Collection box")));
            k.mark("COLLECT_ITEMS");
            k.stoneButton(0, 26, 193, 180, 46, "Collect", IfKit.BIG_FONT, "");
            k.add(k.sprite(0, 34, 202, 26, 28, 777, false));
            k.mark("COLLECT_EARNINGS");
            k.stoneButton(0, 26, 246, 180, 46, "Collect", IfKit.BIG_FONT, "");
            k.add(k.sprite(0, 35, 253, 25, 31, 1090, false));

            // ---- right column (x 218..490): heading row ----
            k.mark("HEADING");
            k.add(k.text(0, 218, 48, 110, 18, IfKit.HEADING_FONT, IfKit.ORANGE, "My Offers")); // ends 2px before Sort
            k.mark("COUNT");
            k.add(IfKit.right(k.text(0, 400, 48, 90, 18, IfKit.BIG_FONT, IfKit.ORANGE, "")));
            k.mark("SORT");
            int sortLayer = k.add(IfKit.hidden(k.layer(0, 330, 47, 104, 20)));
            k.mark("SORT_BUTTON");
            k.stoneButton(sortLayer, 0, 0, 104, 20, "Sort", IfKit.TEXT_FONT, "");
            k.mark("BACK");
            int backLayer = k.add(IfKit.hidden(k.layer(0, 438, 47, 52, 20)));
            k.mark("BACK_BUTTON");
            k.stoneButton(backLayer, 0, 0, 52, 20, "Back", IfKit.TEXT_FONT, "Back");

            // ---- the recessed list (x 218, y 69, 272x174): two-tone rim, scroll container + OSRS scrollbar, footer ----
            int inset = k.add(k.layer(0, 218, 69, INSET_W, INSET_H));
            k.add(k.solid(inset, 0, 0, INSET_W, INSET_H, INSET_FILL));
            k.add(k.outline(inset, 0, 0, INSET_W, INSET_H, INSET_EDGE, false));
            k.add(k.outline(inset, 1, 1, INSET_W - 2, INSET_H - 2, INSET_RIM, false));
            k.mark("SCROLL");
            int scroll = k.add(k.layer(inset, LIST_X, LIST_Y, ROW_W, LIST_H));
            k.mark("SCROLLBAR");
            k.add(k.layer(inset, LIST_X + ROW_W + 2, LIST_Y, 16, LIST_H));
            k.mark("FIRST_ROW");
            for (int r = 0; r < ROWS; r++) {
                int row = k.add(k.layer(scroll, 0, r * ROW_PITCH, ROW_W, ROW_H));            // +0
                int zebra = r % 2 == 0 ? ROW_A : ROW_B;
                InterfaceDefinition fill = k.solid(row, 0, 0, ROW_W, ROW_H, zebra);          // +1 (click: select)
                fill.clickMask = 2;
                fill.actions = new String[]{"Select"};
                fill.onMouseOverListener = new Object[]{45, -2147483645, ROW_HOVER};
                fill.onMouseLeaveListener = new Object[]{45, -2147483645, zebra};
                fill.hasListener = true;
                k.add(fill);
                k.add(k.outline(row, 0, 0, ROW_W, ROW_H, INSET_RIM, false));                // +2 divider
                k.add(k.outline(row, 0, 0, ROW_W, ROW_H, 0xb08d4a, true));                  // +3 featured trim
                k.add(k.solid(row, 4, 4, 36, 32, 0x2c2721));                               // +4 item well
                k.add(k.outline(row, 4, 4, 36, 32, INSET_EDGE, false));                     // +5 its rim
                k.add(k.itemSlot(row, 4, 4, 36, 32, "Examine"));                            // +6 item
                k.add(k.text(row, 45, 4, 146, 15, IfKit.TEXT_FONT, IfKit.ORANGE, ""));      // +7 name
                k.add(k.text(row, 45, 21, 146, 15, IfKit.TEXT_FONT, 0xb0a898, ""));         // +8 detail
                k.add(IfKit.hidden(k.layer(row, 194, 9, 51, 22)));                              // +9 button layer
                k.stoneButton(row + 9, 0, 0, 51, 22, "Select", IfKit.TEXT_FONT, "");        // +10 .. +29
            }
            k.mark("EMPTY");
            k.add(IfKit.hidden(IfKit.centred(k.text(inset, 6, 10, INSET_W - 30, 50, IfKit.BIG_FONT, IfKit.ORANGE, ""))));
            // footer strip inside the inset: range / hint (left), page arrows + n/m (right)
            k.add(k.solid(inset, 2, INSET_H - 19, INSET_W - 4, 17, FOOTER));
            k.add(k.solid(inset, 2, INSET_H - 20, INSET_W - 4, 1, INSET_RIM));
            k.mark("FOOTER");
            k.add(k.text(inset, 6, INSET_H - 19, INSET_W - 12, 17, IfKit.TEXT_FONT, 0xb0a898, "")); // the pager, when shown, sits over its right end
            k.mark("PAGER");
            int pager = k.add(IfKit.hidden(k.layer(inset, INSET_W - 92, INSET_H - 19, 90, 17)));
            // the arrows are the buttons (gold 24528 / 24529 when the page can turn, dim 24530 / 24531 otherwise)
            k.mark("PREV");
            k.add(clickable(k.sprite(pager, 2, 0, 18, 16, 24528, false), "Previous page"));
            k.mark("PAGE");
            k.add(IfKit.centred(k.text(pager, 21, 0, 48, 17, IfKit.TEXT_FONT, IfKit.TEXT, "")));
            k.mark("NEXT");
            k.add(clickable(k.sprite(pager, 70, 0, 18, 16, 24529, false), "Next page"));

            // ---- Buy Offer / Sell Offer (y 249..295) ----
            k.mark("BUY_OFFER");
            k.stoneButton(0, 218, 249, 132, 46, "Buy offer", IfKit.HEADING_FONT, "");
            k.add(k.sprite(0, 228, 254, 26, 35, 1108, false));
            k.add(IfKit.centred(k.text(0, 256, 250, 90, 44, IfKit.HEADING_FONT, IfKit.ORANGE, "Buy Offer")));
            k.mark("SELL_OFFER");
            k.stoneButton(0, 358, 249, 132, 46, "Sell offer", IfKit.HEADING_FONT, "");
            k.add(k.sprite(0, 368, 254, 26, 35, 1106, false));
            k.add(IfKit.centred(k.text(0, 396, 250, 90, 44, IfKit.HEADING_FONT, IfKit.ORANGE, "Sell Offer")));

            // ---- collect button labels (two lines, drawn over the stone, right of the icon) ----
            k.mark("COLLECT_ITEMS_LABEL");
            InterfaceDefinition itemsLabel = IfKit.centred(k.text(0, 66, 196, 136, 40, IfKit.BIG_FONT, IfKit.ORANGE, ""));
            itemsLabel.lineHeight = 16;
            k.add(itemsLabel);
            k.mark("COLLECT_EARNINGS_LABEL");
            InterfaceDefinition coinsLabel = IfKit.centred(k.text(0, 66, 249, 136, 40, IfKit.BIG_FONT, IfKit.ORANGE, ""));
            coinsLabel.lineHeight = 16;
            k.add(coinsLabel);

            // ---- buy modal over the inset (hidden) ----
            k.mark("MODAL");
            int modal = k.add(IfKit.hidden(k.layer(0, 218, 69, INSET_W, INSET_H)));
            k.add(k.solid(modal, 0, 0, INSET_W, INSET_H, 0x2f2a23));
            k.add(k.outline(modal, 0, 0, INSET_W, INSET_H, INSET_EDGE, false));
            k.add(k.outline(modal, 1, 1, INSET_W - 2, INSET_H - 2, 0x8a6a3a, false));
            k.add(k.solid(modal, 12, 12, 40, 36, 0x2c2721));
            k.add(k.outline(modal, 12, 12, 40, 36, INSET_EDGE, false));
            k.mark("MODAL_ITEM");
            k.add(k.itemSlot(modal, 14, 14, 36, 32, "Examine"));
            k.mark("MODAL_TITLE");
            k.add(k.text(modal, 60, 10, 200, 18, IfKit.HEADING_FONT, IfKit.ORANGE, ""));
            k.mark("MODAL_INFO");
            InterfaceDefinition info = k.block(modal, 60, 30, 200, 64, IfKit.TEXT, 15, false);
            k.add(info);
            k.mark("MODAL_BUY_1");
            k.stoneButton(modal, 12, 104, 58, 26, "Buy", IfKit.BIG_FONT, "Buy 1");
            k.mark("MODAL_BUY_5");
            k.stoneButton(modal, 76, 104, 58, 26, "Buy", IfKit.BIG_FONT, "Buy 5");
            k.mark("MODAL_BUY_X");
            k.stoneButton(modal, 140, 104, 58, 26, "Buy", IfKit.BIG_FONT, "Buy X");
            k.mark("MODAL_BUY_ALL");
            k.stoneButton(modal, 204, 104, 56, 26, "Buy", IfKit.BIG_FONT, "Buy All");
            k.mark("MODAL_CANCEL");
            k.stoneButton(modal, 96, 138, 80, 24, "Cancel", IfKit.BIG_FONT, "Cancel");

            k.finish(apply);
        }
    }

    static InterfaceDefinition clickable(InterfaceDefinition d, String action) {
        d.clickMask = 2;
        d.actions = new String[]{action};
        return d;
    }
}

import net.runelite.cache.definitions.InterfaceDefinition;
import net.runelite.cache.fs.Store;

import java.io.File;

// Builds interface group 1179, the Scoreboard Exchange (2026-10-21): the Score Points (SbP) shop of the Scoreboard
// Quartermaster. Server: kronos-server io.ruin.model.activities.scoreboard.ScoreboardExchange (ids from the CONST lines).
// Its own layout (not the Tournament / Rivals card catalogue), on IfKit's OSRS stone:
//  - title row: "Scoreboard Exchange" (comp 3);
//  - header: a recessed balance plaque ("Score Points: X") and 5 stone category tabs (the active one pressed);
//    round 2 (2026-10-21): 5 tabs instead of 3, 36 cards instead of 18 (Ankou sets, partyhats, whips, Souly Wings);
//  - left: a recessed showcase with an OSRS scrollbar, 3 cards a row (raised stone card: inset item well, name, SbP
//    price pill; hover lifts it, the selected card is pressed with a soft gold trim; ops Select / Value / Buy / Examine);
//  - right: the item panel: name, item well, examine, worn stats, the "Cost: X Score Points" pill and the Buy button.
// Usage: verify|apply|rebuild <cachePath>
public class BuildScoreboardExchangeInterface {
    static final int GROUP = 1179, CARDS = 36, PER_ROW = 3;
    /** Category tabs (All / Wildstalker / Clown / Ankou / Rares), sized to their labels: x and width. */
    static final int[][] TAB = {{200, 40}, {244, 76}, {324, 52}, {380, 52}, {436, 52}};
    static final int CARD_W = 88, CARD_H = 104, CARD_GAP = 6, CARD_PITCH = 110;
    static final int GRID_W = 276, GRID_H = 226;
    static final int INSET_FILL = 0x3d362d, INSET_EDGE = 0x1d1915, INSET_RIM = 0x2b2620;
    static final int CARD_FILL = 0x4a4237, CARD_HOVER = 0x564d40, CARD_BORDER = 0x5a4d3a, CARD_SELECTED = 0x2c2620;
    static final int PILL_FILL = 0x23301d, PILL_EDGE = 0x5b7a3a, WELL_FILL = 0x2a2520;

    public static void main(String[] args) throws Exception {
        boolean write = !args[0].equals("verify");
        try (Store store = new Store(new File(args[1]))) {
            store.load();
            IfKit k = new IfKit(GROUP, store, !args[0].equals("apply"));

            // ---- header (y 42): balance plaque + category tabs ----
            k.inset(0, 24, 42, 170, 26);
            k.mark("BALANCE");
            k.add(k.text(0, 30, 42, 160, 26, IfKit.TEXT_FONT, IfKit.GOLD, ""));
            k.mark("FIRST_TAB");
            for (int[] t : TAB)
                k.stoneButton(0, t[0], 42, t[1], 26, "View", IfKit.TEXT_FONT, "");

            // ---- left: the showcase (x 24, y 74, 300 x 232) ----
            int grid = k.add(k.layer(0, 24, 74, 300, 232));
            k.add(k.solid(grid, 0, 0, 300, 232, INSET_FILL));
            k.add(k.outline(grid, 0, 0, 300, 232, INSET_EDGE, false));
            k.add(k.outline(grid, 1, 1, 298, 230, INSET_RIM, false));
            k.mark("SCROLL");
            int scroll = k.add(k.layer(grid, 3, 3, GRID_W, GRID_H));
            k.mark("SCROLLBAR");
            k.add(k.layer(grid, 3 + GRID_W + 2, 3, 16, GRID_H));
            k.mark("FIRST_CARD");
            for (int c = 0; c < CARDS; c++) {
                int x = (c % PER_ROW) * (CARD_W + CARD_GAP), y = (c / PER_ROW) * CARD_PITCH;
                int card = k.add(k.layer(scroll, x, y, CARD_W, CARD_H));                       // +0
                InterfaceDefinition box = k.solid(card, 0, 0, CARD_W, CARD_H, CARD_FILL);       // +1 click
                box.clickMask = 2 | 4 | 8 | 16; // one bit per op (2 alone only ever sent "Select")
                box.actions = new String[]{"Select", "Value", "Buy", "Examine"};
                box.onMouseOverListener = new Object[]{45, -2147483645, CARD_HOVER};
                box.onMouseLeaveListener = new Object[]{45, -2147483645, CARD_FILL};
                box.hasListener = true;
                k.add(box);
                k.add(k.outline(card, 0, 0, CARD_W, CARD_H, CARD_BORDER, false));                // +2
                k.add(IfKit.hidden(k.solid(card, 1, 1, CARD_W - 2, CARD_H - 2, CARD_SELECTED))); // +3 selected recess
                k.add(IfKit.hidden(k.solid(card, 1, 1, CARD_W - 2, 1, IfKit.SHADOW)));          // +4 its top shadow
                k.add(IfKit.hidden(k.solid(card, 1, 1, 1, CARD_H - 2, IfKit.SHADOW)));          // +5 its left shadow
                k.add(k.outline(card, 0, 0, CARD_W, CARD_H, 0xb08d4a, true));                    // +6 soft gold trim
                k.add(k.solid(card, 22, 6, 44, 38, WELL_FILL));                                   // +7 item well
                k.add(k.solid(card, 22, 6, 44, 1, IfKit.SHADOW));                                 // +8
                k.add(k.outline(card, 22, 6, 44, 38, IfKit.TRIM, false));                         // +9
                k.add(k.itemSlot(card, 26, 9, 36, 32, null));                                     // +10 item
                k.add(IfKit.centred(k.text(card, 3, 46, CARD_W - 6, 28, IfKit.TEXT_FONT, IfKit.ORANGE, ""))); // +11 name
                k.add(k.solid(card, 10, 78, CARD_W - 20, 18, PILL_FILL));                         // +12 price pill
                k.add(k.outline(card, 10, 78, CARD_W - 20, 18, PILL_EDGE, false));                // +13
                k.add(IfKit.centred(k.text(card, 10, 78, CARD_W - 20, 18, IfKit.TEXT_FONT, IfKit.GOLD, ""))); // +14 price
            }
            k.mark("EMPTY");
            k.add(IfKit.hidden(IfKit.centred(k.text(grid, 10, 90, 280, 40, IfKit.BIG_FONT, IfKit.ORANGE, ""))));

            // ---- right: the item panel (x 332, y 74, 156 x 232) ----
            int panel = k.add(k.layer(0, 332, 74, 156, 232));
            k.add(k.solid(panel, 0, 0, 156, 232, INSET_FILL));
            k.add(k.outline(panel, 0, 0, 156, 232, INSET_EDGE, false));
            k.add(k.outline(panel, 1, 1, 154, 230, INSET_RIM, false));
            k.mark("ITEM_NAME");
            k.add(IfKit.centred(k.text(panel, 6, 3, 144, 18, IfKit.HEADING_FONT, IfKit.ORANGE, "")));
            k.add(k.solid(panel, 54, 23, 48, 42, WELL_FILL));
            k.add(k.solid(panel, 54, 23, 48, 1, IfKit.SHADOW));
            k.add(k.solid(panel, 54, 23, 1, 42, IfKit.SHADOW));
            k.add(k.outline(panel, 54, 23, 48, 42, IfKit.TRIM, false));
            k.mark("ITEM_SLOT");
            k.add(k.itemSlot(panel, 60, 28, 36, 32, "Examine"));
            k.mark("ITEM_EXAMINE");
            k.add(IfKit.centred(k.block(panel, 6, 68, 144, 40, 0xb0a898, 13, true)));
            k.add(k.solid(panel, 6, 110, 144, 1, INSET_RIM));
            k.mark("ITEM_STATS");
            k.add(k.block(panel, 8, 114, 140, 54, IfKit.TEXT, 13, false));
            k.add(k.solid(panel, 8, 174, 140, 20, PILL_FILL));
            k.add(k.outline(panel, 8, 174, 140, 20, PILL_EDGE, false));
            k.mark("ITEM_COST");
            k.add(IfKit.centred(k.text(panel, 8, 174, 140, 20, IfKit.TEXT_FONT, IfKit.GOLD, "")));
            k.mark("BUY");
            k.stoneButton(panel, 18, 202, 120, 24, "Buy", IfKit.BIG_FONT, "Buy", 0x6b5a3a);

            k.finish(write);
        }
    }
}

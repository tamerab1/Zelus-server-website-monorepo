import net.runelite.cache.definitions.InterfaceDefinition;
import net.runelite.cache.fs.Store;

import java.io.File;

// Builds interface group 1180, the Slayer Master Codex (2026-10-21): one window replacing the legacy rewards (426) and
// the split Slayer shop. Server: kronos-server io.ruin.model.skills.slayer.SlayerCodex (ids from the CONST lines).
//  - title row: "Slayer Master Codex" (comp 3) and the points plaque (left, clear of the close button);
//  - 4 riveted stone tabs: Unlocks & Perks / Task Management / Slayer Supplies / Prestige Gear (the active one pressed);
//  - body (x 24, y 74, 464 x 232), one of three views shown at a time:
//      UNLOCKS: 2-column scrolling perk cards (item well, gold title, 2-line description, price / ACTIVE pill; an
//               unlocked card is sunken with a green trim);
//      SHOP:    5-column scrolling item cards (item, name, price pill; Value (left-click) / Buy 1 / 5 / 10 / 50 / X / Examine),
//               shared by Supplies and Prestige;
//      TASK:    the current assignment (name, remaining, master), Cancel / Block stone buttons, and the blocked list
//               (7 rows, each with an Unblock button).
// Usage: verify|apply|rebuild <cachePath>
public class BuildSlayerCodexInterface {
    static final int GROUP = 1180, TABS = 4;
    static final int UNLOCK_CARDS = 60, UL_W = 214, UL_H = 76, UL_PITCH = 82, UL_GAP = 8;
    static final int SHOP_CARDS = 50, SH_W = 84, SH_H = 98, SH_PITCH = 104, SH_GAP = 5, SH_COLS = 5;
    static final int VIEW_W = 440, VIEW_H = 226, BLOCK_ROWS = 7;
    static final int INSET_FILL = 0x3d362d, INSET_EDGE = 0x1d1915, INSET_RIM = 0x2b2620;
    static final int CARD_FILL = 0x4a4237, CARD_HOVER = 0x564d40, CARD_BORDER = 0x5a4d3a, CARD_SUNK = 0x2c2620;
    static final int PILL_FILL = 0x23301d, PILL_EDGE = 0x5b7a3a, WELL_FILL = 0x2a2520, ACTIVE_TRIM = 0x3f9a3a;

    public static void main(String[] args) throws Exception {
        boolean write = !args[0].equals("verify");
        try (Store store = new Store(new File(args[1]))) {
            store.load();
            IfKit k = new IfKit(GROUP, store, !args[0].equals("apply"));

            k.mark("POINTS");
            k.add(k.text(0, 28, 14, 170, 24, IfKit.TEXT_FONT, IfKit.GOLD, ""));

            k.mark("FIRST_TAB");
            String[] labels = {"Unlocks & Perks", "Task Management", "Slayer Supplies", "Prestige Gear"};
            for (int t = 0; t < TABS; t++)
                k.stoneButton(0, 24 + t * 117, 44, 113, 26, "View", IfKit.TEXT_FONT, labels[t]);

            // ---- body frame ----
            int body = k.add(k.layer(0, 24, 76, 464, 230));
            k.add(k.solid(body, 0, 0, 464, 230, INSET_FILL));
            k.add(k.outline(body, 0, 0, 464, 230, INSET_EDGE, false));
            k.add(k.outline(body, 1, 1, 462, 228, INSET_RIM, false));

            // ---- UNLOCKS view ----
            k.mark("UNLOCK_VIEW");
            int ulView = k.add(k.layer(body, 0, 0, 464, 230));
            k.mark("UNLOCK_SCROLL");
            int ulScroll = k.add(k.layer(ulView, 3, 2, VIEW_W, VIEW_H));
            k.mark("UNLOCK_SCROLLBAR");
            k.add(k.layer(ulView, 3 + VIEW_W + 2, 2, 16, VIEW_H));
            k.mark("FIRST_UNLOCK");
            for (int c = 0; c < UNLOCK_CARDS; c++) {
                int x = (c % 2) * (UL_W + UL_GAP) + 2, y = (c / 2) * UL_PITCH + 2;
                int card = k.add(k.layer(ulScroll, x, y, UL_W, UL_H));                         // +0
                InterfaceDefinition box = k.solid(card, 0, 0, UL_W, UL_H, CARD_FILL);          // +1 click
                box.clickMask = 2 | 4; // one bit per op (bit n = op n): 2 alone left "Disable" dead
                box.actions = new String[]{"Unlock", "Disable"};
                box.onMouseOverListener = new Object[]{45, -2147483645, CARD_HOVER};
                box.onMouseLeaveListener = new Object[]{45, -2147483645, CARD_FILL};
                box.hasListener = true;
                k.add(box);
                k.add(k.outline(card, 0, 0, UL_W, UL_H, CARD_BORDER, false));                    // +2
                k.add(IfKit.hidden(k.solid(card, 1, 1, UL_W - 2, UL_H - 2, CARD_SUNK)));       // +3 sunken (unlocked)
                k.add(IfKit.hidden(k.solid(card, 1, 1, UL_W - 2, 1, IfKit.SHADOW)));          // +4 its shadow
                k.add(k.outline(card, 0, 0, UL_W, UL_H, ACTIVE_TRIM, true));                    // +5 green trim
                k.add(k.solid(card, 6, 18, 40, 36, WELL_FILL));                                  // +6 item well
                k.add(k.outline(card, 6, 18, 40, 36, IfKit.TRIM, false));                        // +7
                k.add(k.itemSlot(card, 8, 20, 36, 32, null));                                    // +8 item
                k.add(k.text(card, 52, 3, UL_W - 56, 16, IfKit.TEXT_FONT, IfKit.ORANGE, ""));    // +9 title
                k.add(k.block(card, 52, 20, UL_W - 56, 30, IfKit.TEXT, 13, false));              // +10 description (2 lines)
                k.add(k.solid(card, UL_W - 96, UL_H - 18, 92, 15, PILL_FILL));                   // +11 pill
                k.add(k.outline(card, UL_W - 96, UL_H - 18, 92, 15, PILL_EDGE, false));          // +12
                k.add(IfKit.centred(k.text(card, UL_W - 96, UL_H - 18, 92, 15, IfKit.TEXT_FONT, IfKit.GOLD, ""))); // +13
            }

            // ---- SHOP view ----
            k.mark("SHOP_VIEW");
            int shView = k.add(IfKit.hidden(k.layer(body, 0, 0, 464, 230)));
            k.mark("SHOP_SCROLL");
            int shScroll = k.add(k.layer(shView, 3, 2, VIEW_W, VIEW_H));
            k.mark("SHOP_SCROLLBAR");
            k.add(k.layer(shView, 3 + VIEW_W + 2, 2, 16, VIEW_H));
            k.mark("FIRST_SHOP");
            for (int c = 0; c < SHOP_CARDS; c++) {
                int x = (c % SH_COLS) * (SH_W + SH_GAP) + 2, y = (c / SH_COLS) * SH_PITCH + 2;
                int card = k.add(k.layer(shScroll, x, y, SH_W, SH_H));                         // +0
                InterfaceDefinition box = k.solid(card, 0, 0, SH_W, SH_H, CARD_FILL);          // +1 click
                box.clickMask = 2 | 4 | 8 | 16 | 32 | 64 | 128; // ops 1-7 (2 alone: only "Value" ever reached the server)
                box.actions = new String[]{"Value", "Buy 1", "Buy 5", "Buy 10", "Buy 50", "Buy X", "Examine"}; // OSRS shop order: left-click = Value
                box.onMouseOverListener = new Object[]{45, -2147483645, CARD_HOVER};
                box.onMouseLeaveListener = new Object[]{45, -2147483645, CARD_FILL};
                box.hasListener = true;
                k.add(box);
                k.add(k.outline(card, 0, 0, SH_W, SH_H, CARD_BORDER, false));                    // +2
                k.add(k.solid(card, 20, 6, 44, 38, WELL_FILL));                                  // +3 item well
                k.add(k.outline(card, 20, 6, 44, 38, IfKit.TRIM, false));                        // +4
                k.add(k.itemSlot(card, 24, 9, 36, 32, null));                                    // +5 item
                k.add(IfKit.centred(k.text(card, 3, 46, SH_W - 6, 28, IfKit.TEXT_FONT, IfKit.ORANGE, ""))); // +6 name
                k.add(k.solid(card, 6, 77, SH_W - 12, 16, PILL_FILL));                           // +7 pill
                k.add(k.outline(card, 6, 77, SH_W - 12, 16, PILL_EDGE, false));                  // +8
                k.add(IfKit.centred(k.text(card, 6, 77, SH_W - 12, 16, IfKit.TEXT_FONT, IfKit.GOLD, ""))); // +9 price
            }

            // ---- TASK view ----
            k.mark("TASK_VIEW");
            int taskView = k.add(IfKit.hidden(k.layer(body, 0, 0, 464, 230)));
            // current assignment card (left)
            k.inset(taskView, 8, 8, 216, 140);
            k.add(k.text(taskView, 16, 12, 200, 16, IfKit.BIG_FONT, IfKit.ORANGE, "Current Assignment"));
            k.add(k.solid(taskView, 14, 30, 204, 1, INSET_RIM));
            k.mark("TASK_ITEM");
            k.add(k.solid(taskView, 16, 38, 44, 38, WELL_FILL));
            k.add(k.outline(taskView, 16, 38, 44, 38, IfKit.TRIM, false));
            k.add(k.itemSlot(taskView, 20, 41, 36, 32, null));
            k.mark("TASK_NAME");
            k.add(k.text(taskView, 68, 36, 150, 20, IfKit.HEADING_FONT, IfKit.WHITE, ""));
            k.mark("TASK_LEFT");
            k.add(k.text(taskView, 68, 56, 150, 18, IfKit.TEXT_FONT, IfKit.TEXT, ""));
            k.mark("TASK_MASTER");
            k.add(k.block(taskView, 16, 84, 200, 58, IfKit.TEXT, 13, false));
            k.mark("CANCEL");
            k.stoneButton(taskView, 8, 156, 216, 30, "Cancel task", IfKit.BIG_FONT, "");
            k.mark("BLOCK");
            k.stoneButton(taskView, 8, 192, 216, 30, "Block task", IfKit.BIG_FONT, "");
            // blocked list (right)
            k.inset(taskView, 232, 8, 224, 214);
            k.mark("BLOCK_TITLE");
            k.add(k.text(taskView, 240, 12, 210, 16, IfKit.BIG_FONT, IfKit.ORANGE, ""));
            k.add(k.solid(taskView, 238, 30, 212, 1, INSET_RIM));
            k.mark("FIRST_BLOCKED");
            for (int r = 0; r < BLOCK_ROWS; r++) {
                int y = 36 + r * 26;
                k.add(k.solid(taskView, 238, y, 212, 24, r % 2 == 0 ? 0x463f34 : 0x423b31));    // +0
                k.add(k.text(taskView, 244, y, 130, 24, IfKit.TEXT_FONT, IfKit.TEXT, ""));       // +1 name
                k.stoneButton(taskView, 378, y + 2, 68, 20, "Unblock", IfKit.TEXT_FONT, "Unblock"); // +2 .. +21
            }

            k.finish(write);
        }
    }
}

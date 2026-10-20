import net.runelite.cache.definitions.InterfaceDefinition;
import net.runelite.cache.fs.Store;

import java.io.File;

// Builds interface group 1178, the Zelus Codex (2026-10-20: replaces the Reason "Achievements Interface" 849, whose
// fixed 26-row tiers could not take new tasks and clipped its names into the buttons). Server:
// kronos-server io.ruin.model.content.codex.ZelusCodex (ids from the CONST lines). OSRS stone buttons throughout.
//  - title row: "Zelus Codex" (comp 3) and the overall count (right);
//  - 6 tier tabs: Beginner / Easy / Medium / Hard / Elite / Zelus (the active one pressed);
//  - left: a recessed task list with an OSRS scrollbar (40 rows: status pip + name, one line, never under the bar);
//  - right: the task card: name, tier line, wrapped description, progress bar + count, rewards (text + 4 item slots),
//    and the Claim Reward button (its label doubles as the status: Claimed / In progress).
// Usage: verify|rebuild <cachePath>
public class BuildCodexInterface {
    static final int GROUP = 1178, TABS = 6, ROWS = 40, ROW_H = 21, ROW_PITCH = 22;
    static final int LIST_W = 194, LIST_H = 208;
    static final int INSET_FILL = 0x3d362d, INSET_EDGE = 0x1d1915, INSET_RIM = 0x2b2620, ROW_A = 0x463f34, ROW_B = 0x423b31,
            ROW_HOVER = 0x4f4739, ROW_SELECTED = 0x2c2620;

    public static void main(String[] args) throws Exception {
        boolean apply = args[0].equals("rebuild");
        try (Store store = new Store(new File(args[1]))) {
            store.load();
            IfKit k = new IfKit(GROUP, store, true);

            k.mark("SUMMARY");
            k.add(k.text(0, 28, 16, 150, 22, IfKit.TEXT_FONT, IfKit.TEXT, "")); // left of the centred title, clear of the close button

            // ---- tier tabs (y 44, 6 x 74 + 5 x 4 = 464) ----
            k.mark("FIRST_TAB");
            for (int t = 0; t < TABS; t++)
                k.stoneButton(0, 24 + t * 78, 44, 74, 24, "View", IfKit.BIG_FONT, "");

            // ---- left: task list inset (x 24, y 74, 218 x 232) ----
            int inset = k.add(k.layer(0, 24, 74, 218, 232));
            k.add(k.solid(inset, 0, 0, 218, 232, INSET_FILL));
            k.add(k.outline(inset, 0, 0, 218, 232, INSET_EDGE, false));
            k.add(k.outline(inset, 1, 1, 216, 230, INSET_RIM, false));
            k.mark("LIST_TITLE");
            k.add(k.text(inset, 6, 3, 206, 16, IfKit.BIG_FONT, IfKit.ORANGE, ""));
            k.add(k.solid(inset, 2, 20, 214, 1, INSET_RIM));
            k.mark("SCROLL");
            int scroll = k.add(k.layer(inset, 3, 22, LIST_W, LIST_H));
            k.mark("SCROLLBAR");
            k.add(k.layer(inset, 3 + LIST_W + 2, 22, 16, LIST_H));
            k.mark("FIRST_ROW");
            for (int r = 0; r < ROWS; r++) {
                int row = k.add(k.layer(scroll, 0, r * ROW_PITCH, LIST_W, ROW_H));   // +0
                int zebra = r % 2 == 0 ? ROW_A : ROW_B;
                InterfaceDefinition fill = k.solid(row, 0, 0, LIST_W, ROW_H, zebra);  // +1 click
                fill.clickMask = 2;
                fill.actions = new String[]{"View"};
                fill.onMouseOverListener = new Object[]{45, -2147483645, ROW_HOVER};
                fill.onMouseLeaveListener = new Object[]{45, -2147483645, zebra};
                fill.hasListener = true;
                k.add(fill);
                k.add(IfKit.hidden(k.solid(row, 0, 0, LIST_W, ROW_H, ROW_SELECTED)));  // +2 selected recess
                k.add(IfKit.hidden(k.solid(row, 0, 0, LIST_W, 1, IfKit.SHADOW)));       // +3 its top shadow
                k.add(k.solid(row, 6, 7, 7, 7, 0x6f6f6f));                             // +4 status pip
                k.add(k.outline(row, 5, 6, 9, 9, INSET_EDGE, false));                  // +5 its rim
                k.add(k.text(row, 19, 0, LIST_W - 23, ROW_H, IfKit.TEXT_FONT, IfKit.TEXT, "")); // +6 name
            }
            k.mark("EMPTY");
            k.add(IfKit.hidden(IfKit.centred(k.text(inset, 6, 60, 206, 40, IfKit.BIG_FONT, IfKit.ORANGE, ""))));

            // ---- right: the task card (x 250, y 74, 238 x 232) ----
            int card = k.add(k.layer(0, 250, 74, 238, 232));
            k.add(k.solid(card, 0, 0, 238, 232, INSET_FILL));
            k.add(k.outline(card, 0, 0, 238, 232, INSET_EDGE, false));
            k.add(k.outline(card, 1, 1, 236, 230, INSET_RIM, false));
            k.mark("TASK_NAME");
            k.add(k.text(card, 8, 4, 222, 18, IfKit.HEADING_FONT, IfKit.ORANGE, ""));
            k.mark("TASK_TIER");
            k.add(k.text(card, 8, 22, 222, 14, IfKit.TEXT_FONT, 0xb0a898, ""));
            k.add(k.solid(card, 6, 38, 226, 1, INSET_RIM));
            k.mark("TASK_DESC");
            k.add(k.block(card, 8, 42, 222, 44, IfKit.TEXT, 14, false));
            // progress: label + count (left / right) over a slim OSRS meter (clientscript 10754 sizes the 6px fill)
            k.add(k.text(card, 8, 88, 110, 14, IfKit.TEXT_FONT, IfKit.TEXT, "Progress"));
            k.mark("PROGRESS_TEXT");
            k.add(IfKit.right(k.text(card, 118, 88, 112, 14, IfKit.TEXT_FONT, IfKit.WHITE, "")));
            k.add(k.solid(card, 8, 104, 222, 8, 0x1a1612));
            k.mark("PROGRESS_FILL");
            k.add(k.solid(card, 9, 105, 220, 6, 0x2e7d32));
            k.add(k.outline(card, 8, 104, 222, 8, INSET_EDGE, false));
            k.add(k.solid(card, 6, 116, 226, 1, INSET_RIM));
            k.add(k.text(card, 8, 119, 222, 15, IfKit.BIG_FONT, IfKit.ORANGE, "Rewards"));
            k.mark("REWARDS");
            k.add(k.block(card, 8, 135, 222, 38, IfKit.TEXT, 13, false));
            k.mark("FIRST_REWARD_ITEM");
            for (int i = 0; i < 5; i++)
                k.slot(card, 8 + i * 44, 173, "Examine");
            k.mark("CLAIM");
            k.stoneButton(card, 59, 208, 120, 22, "Claim", IfKit.BIG_FONT, "Claim Reward");

            k.finish(apply);
        }
    }
}

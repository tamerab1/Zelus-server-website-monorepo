import net.runelite.cache.definitions.InterfaceDefinition;
import net.runelite.cache.fs.Store;

import java.io.File;

// Zelus Commands, interface 1177 (2026-10-18; round 2 the same day): the ::commands / ::help guide, READ-ONLY (no row
// does anything when clicked -- teleports live in the Teleport menu, this is a reference). On the 1002 frame
// (488 x 334), everything inside the frame's inner border:
//   - a framed header strip under the title bar: the guide's one-line description (left) and the command count (right)
//   - a stone sidebar: 4 category tabs (bezelTab, the server recesses the active one), a tip block, a Search button
//   - a content panel: category title + blurb, a divider, a two-column table (command 104 px / description 186 px, both measured in the game font: no wrapping) of ROWS
//     plain rows in a scroll container, its scrollbar strip inside the panel border (clientscript 10532 via the server)
// The server (io.ruin.model.inter.handlers.CommandCodex) owns the command list and counts.
// Prints "CONST name = id" lines. Usage: verify|apply|rebuild <cachePath>
public class BuildCommandCodexInterface {
    static final int GROUP = 1177, TABS = 4, ROWS = 32, ROW_H = 26, TAB_H = 28, TAB_STEP = 33;
    /** The tab names are also the buttons' menu option, so the mouse tooltip reads e.g. "Features & Perks". */
    static final String[] TAB_NAMES = {"General & Account", "Features & Perks", "Competitive & PvP", "Settings & Utility"};
    // the 1002 frame's background is drawn at x 14..501, y 9..315 (its inner edge ~x 20..495, y 38..309): every panel
    // stays inside that (round 3: x 12 / bottom 326 overlapped the left and bottom borders and left the right side empty)
    static final int TOP = 66, BOTTOM = 307, SIDE_X = 22, SIDE_W = 128, CONTENT_X = 156, CONTENT_W = 337;
    static final int SCROLL_X = 8, SCROLL_Y = 44, SCROLL_W = 303, SCROLLBAR_W = 16; // scrollbar ends 16 px inside the frame
    static final int ROW_A = 0x2f2a25, ROW_B = 0x35302a, MUTED = 0xa89c88, SILVER = 0xcfcac2, YELLOW = 0xffd84a;

    public static void main(String[] args) throws Exception {
        boolean rebuild = args[0].equals("rebuild");
        boolean apply = args[0].equals("apply") || rebuild;
        try (Store store = new Store(new File(args[1]))) {
            store.load();
            IfKit k = new IfKit(GROUP, store, rebuild || !apply);
            int h = BOTTOM - TOP;

            // ---- header strip, below the title bar's border ----
            int head = k.panel(0, SIDE_X, 40, CONTENT_X + CONTENT_W - SIDE_X, 22);
            k.mark("SUBTITLE");
            k.add(k.text(head, 8, 0, 300, 22, IfKit.TEXT_FONT, SILVER, ""));
            k.mark("COUNT");
            k.add(IfKit.right(k.text(head, 300, 0, CONTENT_X + CONTENT_W - SIDE_X - 308, 22, IfKit.TEXT_FONT, IfKit.GOLD, "")));

            // ---- sidebar: 4 tabs, a tip, search ----
            int side = k.panel(0, SIDE_X, TOP, SIDE_W, h);
            k.mark("FIRST_TAB");
            for (int t = 0; t < TABS; t++)
                k.bezelTab(side, 6, 8 + t * TAB_STEP, SIDE_W - 12, TAB_H, TAB_NAMES[t], false, TAB_NAMES[t]);
            k.mark("TIP");
            InterfaceDefinition tip = k.block(side, 8, 8 + TABS * TAB_STEP + 6, SIDE_W - 16, 48, MUTED, 12, true);
            k.add(tip);
            k.mark("SEARCH");
            k.bezel(side, 6, h - 32, SIDE_W - 12, 24, "Search");

            // ---- content ----
            int content = k.panel(0, CONTENT_X, TOP, CONTENT_W, h);
            k.mark("CAT_TITLE");
            k.add(k.text(content, 10, 5, CONTENT_W - 20, 16, IfKit.HEADING_FONT, IfKit.GOLD, ""));
            k.mark("CAT_BLURB");
            k.add(k.text(content, 10, 21, CONTENT_W - 20, 14, IfKit.TEXT_FONT, MUTED, ""));
            k.add(k.solid(content, 8, 38, CONTENT_W - 16, 1, IfKit.TRIM));
            int scrollH = h - SCROLL_Y - 8;
            k.mark("SCROLL");
            int scroll = k.add(k.layer(content, SCROLL_X, SCROLL_Y, SCROLL_W, scrollH));
            k.mark("SCROLLBAR");
            k.add(k.layer(content, SCROLL_X + SCROLL_W + 2, SCROLL_Y, SCROLLBAR_W, scrollH));
            k.mark("EMPTY");
            k.add(IfKit.hidden(IfKit.centred(k.text(content, 10, 110, CONTENT_W - 20, 30, IfKit.TEXT_FONT, MUTED, ""))));

            // two-column rows: command 104 px (35%), description 186 px on ONE line; plain strips, no click
            // measured in font 494: the widest command (::changesecuritypin) is 97 px, every description <= 186 px
            int cmdW = 104, descX = cmdW + 4;
            k.mark("FIRST_ROW");
            for (int r = 0; r < ROWS; r++) {
                int y = r * ROW_H;
                k.add(IfKit.hidden(k.solid(scroll, 0, y, SCROLL_W, ROW_H - 2, r % 2 == 0 ? ROW_A : ROW_B)));
                k.add(IfKit.hidden(k.text(scroll, 6, y, cmdW - 6, ROW_H - 2, IfKit.TEXT_FONT, YELLOW, "")));
                InterfaceDefinition desc = IfKit.hidden(k.text(scroll, descX, y, SCROLL_W - descX - 4, ROW_H - 2, IfKit.TEXT_FONT, SILVER, ""));
                desc.lineHeight = 11;
                k.add(desc);
            }
            k.finish(apply);
        }
    }
}

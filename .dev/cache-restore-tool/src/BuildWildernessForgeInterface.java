import net.runelite.cache.fs.Store;

import java.io.File;

// Builds interface group 1168, the Wilderness Forge (content/wildernessforge/WildernessForge.java), replacing its use
// of 1002 (2026-10-15). 1002 stays untouched: it is the template source for every IfKit / Build*Interface builder.
//  - 5 category bezel tabs;
//  - left: "Upgradeable Items", a scrolling 4 x 15 grid of recessed 36x34 slots (hover wash, bronze trim on the
//    selected one, item with Select / Examine);
//  - right: the required-items tray (6 recessed slots), a gilded connector down to two matching boxes: Upgrade Result
//    (socket + name) and Success Rate (big % + the failure rule), then the Upgrade Now! bezel button.
// Slot s of the grid starts at FIRST_SLOT + s * SLOT_STRIDE (well +0, border +1, trim +2, item +3).
// Prints "CONST name = id" lines. Usage: verify|apply|rebuild <cachePath>
public class BuildWildernessForgeInterface {
    static final int GROUP = 1168, TABS = 5, COLS = 4, ROWS = 15, PITCH = 38, REQUIRED = 6, REQ_PITCH = 42;
    static final int SLOT_FILL = 0x2a2520, SLOT_HOVER = 0x3a332a;

    public static void main(String[] args) throws Exception {
        boolean rebuild = args[0].equals("rebuild");
        boolean apply = args[0].equals("apply") || rebuild;
        try (Store store = new Store(new File(args[1]))) {
            store.load();
            IfKit k = new IfKit(GROUP, store, rebuild || !apply);

            k.mark("FIRST_TAB");
            for (int t = 0; t < TABS; t++)
                k.bezelTab(0, 24 + t * 93, 36, 88, 24, "View", true, "");

            // ---- left: the item grid ----
            int left = k.panel(0, 24, 64, 180, 242);
            k.add(IfKit.centred(k.text(left, 0, 4, 180, 16, IfKit.HEADING_FONT, IfKit.GOLD, "Upgradeable Items")));
            k.mark("SCROLL");
            int scroll = k.add(k.layer(left, 6, 24, 154, 212));
            k.mark("SCROLLBAR");
            k.add(k.layer(left, 160, 24, 16, 212));
            k.mark("FIRST_SLOT");
            for (int s = 0; s < COLS * ROWS; s++) {
                int x = 1 + (s % COLS) * PITCH, y = 1 + (s / COLS) * PITCH;
                if (s == 1)
                    System.out.println("CONST SLOT_STRIDE = " + (k.next() - k.ids.get("FIRST_SLOT")));
                var well = k.solid(scroll, x, y, 36, 34, SLOT_FILL);
                well.onMouseOverListener = new Object[]{45, -2147483645, SLOT_HOVER};
                well.onMouseLeaveListener = new Object[]{45, -2147483645, SLOT_FILL};
                well.hasListener = true;
                k.add(well);
                k.add(k.outline(scroll, x, y, 36, 34, IfKit.BORDER, false));
                k.add(k.outline(scroll, x, y, 36, 34, 0x4a3a24, true));
                var item = k.itemSlot(scroll, x, y + 1, 36, 32, null);
                item.clickMask = 2 | 4;
                item.actions = new String[]{"Select", "Examine"};
                k.add(item);
            }

            // ---- right: requirements, result, success, forge ----
            int right = k.panel(0, 210, 64, 278, 242);
            k.inset(right, 4, 6, 270, 76);
            k.mark("REQUIRED_TEXT");
            k.add(IfKit.centred(k.text(right, 6, 9, 266, 16, IfKit.TEXT_FONT, IfKit.ORANGE, "")));
            k.mark("FIRST_REQUIRED");
            // 6 recessed 38x34 slots, 4px apart, each item 36x32 inside its own box (round 1 used 8 at a 33px pitch, so
            // icons and stack numbers ran into the next slot)
            for (int r = 0; r < REQUIRED; r++) {
                int x = 14 + r * REQ_PITCH;
                k.add(k.solid(right, x, 30, 38, 34, 0x1f1b17));
                k.add(k.outline(right, x, 30, 38, 34, IfKit.BORDER, false));
                var slot = k.itemSlot(right, x + 1, 31, 36, 32, null);
                slot.clickMask = 2;
                slot.actions = new String[]{"Examine"};
                k.add(slot);
            }
            // gilded connector from the tray down between the two boxes
            k.add(k.solid(right, 138, 82, 2, 12, 0x7a5c32));
            k.add(k.solid(right, 74, 93, 130, 2, 0x7a5c32));
            k.add(k.solid(right, 74, 93, 2, 6, 0x7a5c32));
            k.add(k.solid(right, 202, 93, 2, 6, 0x7a5c32));

            // Upgrade Result (left box)
            k.inset(right, 6, 99, 130, 102);
            k.add(IfKit.centred(k.text(right, 6, 102, 130, 15, IfKit.TEXT_FONT, IfKit.GOLD, "Upgrade Result")));
            k.add(k.solid(right, 49, 120, 44, 40, 0x3a2e14));
            k.add(k.outline(right, 49, 120, 44, 40, 0x7a5c32, false));
            k.mark("RESULT_ITEM");
            var result = k.itemSlot(right, 53, 124, 36, 32, null);
            result.clickMask = 2;
            result.actions = new String[]{"Examine"};
            k.add(result);
            k.mark("RESULT_NAME");
            k.add(k.block(right, 9, 164, 124, 34, IfKit.TEXT, 11, true));

            // Success Rate (right box)
            k.inset(right, 142, 99, 130, 102);
            k.add(IfKit.centred(k.text(right, 142, 102, 130, 15, IfKit.TEXT_FONT, IfKit.GOLD, "Success Rate")));
            k.mark("SUCCESS_TEXT");
            k.add(IfKit.centred(k.text(right, 142, 124, 130, 30, IfKit.HEADING_FONT, IfKit.WHITE, "")));
            k.mark("SUCCESS_NOTE");
            k.add(k.block(right, 146, 158, 122, 40, 0x9a9a9a, 11, true));

            k.mark("UPGRADE");
            k.bezel(right, 59, 206, 160, 30, "Upgrade");

            k.finish(apply);
        }
    }
}

import net.runelite.cache.fs.Store;

import java.io.File;

// Builds interface group 1167, the Zelus Cape Forge (content/capeforge/CapeForge.java, server):
//  - left: selected cape name, a large model preview (if_setobject + if_setangle), description, and a 3-slot
//    requirements row (item + "have/need" in green or red);
//  - right: "Choose a style" 3x3 grid of selectable cards (cape icon + 2-line label), a status line and Infuse.
// Card g starts at FIRST_CARD + g * CARD_STRIDE (selectable 6 comps, item +6, label +7).
// Prints "CONST name = id" lines. Usage: verify|apply|rebuild <cachePath>
public class BuildCapeForgeInterface {
    static final int GROUP = 1167, CARDS = 9, REQS = 3;

    public static void main(String[] args) throws Exception {
        boolean rebuild = args[0].equals("rebuild");
        boolean apply = args[0].equals("apply") || rebuild;
        try (Store store = new Store(new File(args[1]))) {
            store.load();
            IfKit k = new IfKit(GROUP, store, rebuild || !apply);

            // ---- left: preview ----
            int left = k.panel(0, 24, 38, 206, 268);
            k.mark("PREVIEW_NAME");
            k.add(IfKit.centred(k.text(left, 0, 4, 206, 18, IfKit.HEADING_FONT, IfKit.GOLD, "")));
            k.inset(left, 8, 24, 190, 132);
            k.mark("MODEL");
            k.add(k.model(left, 8, 24, 190, 132));
            k.mark("DESC");
            k.add(k.block(left, 8, 160, 190, 38, IfKit.TEXT, 12, true));
            k.add(k.solid(left, 8, 200, 190, 1, IfKit.BORDER));
            k.add(IfKit.centred(k.text(left, 0, 203, 206, 15, IfKit.HEADING_FONT, IfKit.GOLD, "Requirements")));
            k.mark("FIRST_REQ");
            for (int r = 0; r < REQS; r++) {
                int x = 21 + r * 62;
                k.slot(left, x, 219, "Examine");
                k.add(IfKit.centred(k.text(left, x - 13, 252, 62, 14, IfKit.TEXT_FONT, IfKit.GREEN, "")));
            }

            // ---- right: style grid ----
            int right = k.panel(0, 236, 38, 252, 268);
            k.add(IfKit.centred(k.text(right, 0, 4, 252, 16, IfKit.HEADING_FONT, IfKit.GOLD, "Choose a style")));
            k.mark("FIRST_CARD");
            for (int g = 0; g < CARDS; g++) {
                int x = 6 + (g % 3) * 82, y = 22 + (g / 3) * 58;
                if (g == 1)
                    System.out.println("CONST CARD_STRIDE = " + (k.next() - k.ids.get("FIRST_CARD")));
                k.selectable(right, x, y, 78, 54, "Select");
                k.add(k.itemSlot(right, x + 21, y + 2, 36, 32, null));
                k.add(k.block(right, x + 1, y + 33, 76, 22, IfKit.TEXT, 11, true));
            }
            k.mark("STATUS");
            k.add(k.block(right, 6, 198, 240, 28, IfKit.TEXT, 12, true));
            k.mark("INFUSE");
            k.bezel(right, 36, 230, 180, 30, "Infuse");

            k.finish(apply);
        }
    }
}

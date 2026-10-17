import net.runelite.cache.fs.Store;

import java.io.File;

// Builds interface group 1173, the Statue of Fortune (content/fortune/FortuneInterface.java, server). Warm-stone IfKit
// theme in the 1002 frame:
//  - header: active-blessings counter (left) and the next expiry (right);
//  - left: the 8 blessings as selectable rows, name + status badge (ACTIVE mm:ss / nn% / --);
//  - centre: name, the statue model (spun by its own loc seq), effect lines, a 3-segment tier bar (track, fill sized
//    by script 10606, outline, cost label), totals, time left and the extension line;
//  - right: Pool / All-time tabs, top-5 benefactors (name + amount), your contribution and next donor rank;
//  - bottom: 1M / 5M / 25M / Custom / All cash and the broadcast toggle.
// Rows live in a scroll container (SCROLL, strip SCROLLBAR; clientscript 10532 sizes it). Row r starts at FIRST_ROW + r * ROW_STRIDE (selectable 6 comps, name +6, badge +7). Segment s at FIRST_SEG + s * 4
// (track, fill +1, outline +2, label +3). Board row i at FIRST_BOARD + i * 2 (name, amount +1).
// Prints "CONST name = id" lines. Usage: verify|apply|rebuild <cachePath>
public class BuildFortuneInterface {
    static final int GROUP = 1173, ROWS = 8, BOARD = 5;
    static final int AMBER = 0xff9a1f, TRACK = 0x120f0c, MUTED = 0x8f8573;

    public static void main(String[] args) throws Exception {
        boolean rebuild = args[0].equals("rebuild");
        boolean apply = args[0].equals("apply") || rebuild;
        try (Store store = new Store(new File(args[1]))) {
            store.load();
            IfKit k = new IfKit(GROUP, store, rebuild || !apply);

            // ---- header ----
            k.mark("ACTIVE");
            k.add(k.text(0, 28, 49, 240, 13, IfKit.HEADING_FONT, IfKit.GREEN, ""));
            k.mark("NEXT_EXPIRY");
            k.add(IfKit.right(k.text(0, 248, 49, 236, 13, IfKit.TEXT_FONT, IfKit.TEXT, "")));

            // ---- left: blessings, a scrolling list of 40px rows ----
            int left = k.panel(0, 24, 62, 150, 210);
            k.mark("SCROLL");
            int scroll = k.add(k.layer(left, 3, 3, 126, 204));
            k.mark("SCROLLBAR");
            k.add(k.layer(left, 131, 3, 16, 204));
            k.mark("FIRST_ROW");
            for (int r = 0; r < ROWS; r++) {
                int y = r * 40;
                if (r == 1)
                    System.out.println("CONST ROW_STRIDE = " + (k.next() - k.ids.get("FIRST_ROW")));
                k.selectable(scroll, 0, y, 124, 38, "Select");
                k.add(k.text(scroll, 7, y + 3, 114, 16, IfKit.HEADING_FONT, IfKit.TEXT, ""));
                k.add(k.text(scroll, 7, y + 20, 114, 14, IfKit.TEXT_FONT, MUTED, ""));
            }

            // ---- centre: selected blessing ----
            int mid = k.panel(0, 178, 62, 170, 210);
            k.mark("NAME");
            k.add(IfKit.centred(k.text(mid, 0, 4, 170, 16, IfKit.HEADING_FONT, IfKit.GOLD, "")));
            k.inset(mid, 8, 24, 154, 74);
            // a model widget draws the model's base at its centre and grows upward: shift it down so the statue sits
            // centred in the box with its base near the bottom, instead of poking up into the title
            k.mark("MODEL");
            k.add(k.model(mid, 8, 51, 154, 74));
            k.mark("EFFECT");
            k.add(k.block(mid, 6, 100, 158, 26, IfKit.TEXT, 13, true));
            // bevelled tier bars: dark frame, a clipping layer holding the fill (highlight / base / shade strips) and a
            // track-coloured cover the server slides right by the fill width, then the trim border and the cost label
            k.mark("FIRST_SEG");
            for (int s = 0; s < 3; s++) {
                int x = 8 + s * 52, y = 126, w = 46, h = 14;
                if (s == 1)
                    System.out.println("CONST SEG_STRIDE = " + (k.next() - k.ids.get("FIRST_SEG")));
                k.add(k.solid(mid, x - 1, y - 1, w + 2, h + 2, 0x0e0c0a));
                int clip = k.add(k.layer(mid, x, y, w, h));
                k.add(k.solid(clip, 0, 0, w, h, AMBER));
                k.add(k.solid(clip, 0, 0, w, 4, 0xffc463));
                k.add(k.solid(clip, 0, h - 4, w, 4, 0x9a5a12));
                k.add(k.solid(clip, 0, 0, w, h, TRACK));
                k.add(k.outline(mid, x - 1, y - 1, w + 2, h + 2, IfKit.TRIM, false));
                k.add(IfKit.centred(k.text(mid, x - 4, y + 16, 54, 12, IfKit.TEXT_FONT, MUTED, "")));
            }
            k.mark("TOTALS");
            k.add(IfKit.centred(k.text(mid, 0, 156, 170, 14, IfKit.TEXT_FONT, IfKit.TEXT, "")));
            k.mark("TIME");
            k.add(IfKit.centred(k.text(mid, 0, 170, 170, 14, IfKit.TEXT_FONT, IfKit.GREEN, "")));
            k.mark("EXTEND");
            k.add(IfKit.centred(k.text(mid, 0, 184, 170, 14, IfKit.TEXT_FONT, MUTED, "")));

            // ---- right: benefactors ----
            int right = k.panel(0, 352, 62, 136, 210);
            k.mark("TAB_POOL");
            k.bezelTab(right, 6, 5, 60, 18, "View", false, "This pool");
            k.mark("TAB_ALL");
            k.bezelTab(right, 70, 5, 60, 18, "View", false, "All-time");
            k.mark("FIRST_BOARD");
            for (int i = 0; i < BOARD; i++) {
                int y = 27 + i * 15;
                k.add(k.text(right, 5, y, 86, 14, IfKit.BIG_FONT, IfKit.TEXT, ""));
                k.add(IfKit.right(k.text(right, 68, y, 63, 14, IfKit.BIG_FONT, IfKit.GOLD, "")));
            }
            k.add(k.solid(right, 6, 106, 124, 1, IfKit.BORDER));
            k.add(IfKit.centred(k.text(right, 0, 110, 136, 14, IfKit.HEADING_FONT, IfKit.GOLD, "Your sacrifice")));
            k.mark("YOU_POOL");
            k.add(IfKit.centred(k.text(right, 0, 127, 136, 15, IfKit.BIG_FONT, IfKit.TEXT, "")));
            k.mark("YOU_ALL");
            k.add(IfKit.centred(k.text(right, 0, 143, 136, 15, IfKit.BIG_FONT, IfKit.TEXT, "")));
            k.mark("YOU_NEXT");
            k.add(k.block(right, 5, 160, 126, 50, MUTED, 12, true));

            // ---- action bar ----
            k.mark("FIRST_QUICK");
            String[] quick = {"Sacrifice 1M", "Sacrifice 5M", "Sacrifice 25M", "Sacrifice", "Sacrifice all"};
            for (int q = 0; q < quick.length; q++)
                k.bezel(0, 24 + q * 72, 276, 68, 28, quick[q]);
            k.mark("BROADCAST");
            k.bezel(0, 384, 276, 104, 28, "Toggle");

            k.finish(apply);
        }
    }
}

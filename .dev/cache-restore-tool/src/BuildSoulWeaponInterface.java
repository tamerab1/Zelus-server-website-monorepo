import net.runelite.cache.definitions.InterfaceDefinition;
import net.runelite.cache.fs.Store;

import java.io.File;

// Builds interface group 1174, the Soulbound weapon window (content/soulweapons/SoulWeaponInterface.java, server),
// 2026-10-18. Warm-stone IfKit theme in the 1002 frame with soul-teal accents:
//  - header: tier + look (left), souls harvested (right);
//  - left: weapon name, the weapon model in a recessed box, its look, the 3-look evolution path (item slots with a
//    hidden gold highlight + tier-range label), the unspent Soul Points and the Soul Tree bezel (opens 1176);
//  - right: the 10-pip tier track (track, fill +1 recoloured by script 45, outline +2, number +3), the bevelled Soul XP
//    bar (fill strips + a cover the server slides right by the fill width, label on top), totals / last kill, the
//    stat rows (label + value) and the how-to-earn footer.
// Prints "CONST name = id" lines. Usage: verify|apply|rebuild <cachePath>
public class BuildSoulWeaponInterface {
    static final int GROUP = 1174, PIPS = 10, LOOKS = 3, ROWS = 5;
    static final int TEAL = 0x2fd2d8, DEEP = 0x14595e, TRACK = 0x0f1416, MUTED = 0x8f8573;

    public static void main(String[] args) throws Exception {
        boolean rebuild = args[0].equals("rebuild");
        boolean apply = args[0].equals("apply") || rebuild;
        try (Store store = new Store(new File(args[1]))) {
            store.load();
            IfKit k = new IfKit(GROUP, store, rebuild || !apply);

            // ---- header ----
            k.mark("HEADER_TIER");
            k.add(k.text(0, 28, 47, 260, 13, IfKit.HEADING_FONT, TEAL, ""));
            k.mark("HEADER_SOULS");
            k.add(IfKit.right(k.text(0, 248, 47, 236, 13, IfKit.TEXT_FONT, IfKit.TEXT, "")));

            // ---- left: the weapon ----
            int left = k.panel(0, 24, 62, 168, 240);
            // name: up to 2 centred lines (the server breaks long names) inside the panel's 6px margins
            k.mark("NAME");
            InterfaceDefinition name = IfKit.centred(k.text(left, 6, 3, 156, 28, IfKit.BIG_FONT, IfKit.GOLD, ""));
            name.lineHeight = 13;
            k.add(name);
            k.inset(left, 8, 32, 152, 92);
            // slate backdrop (lighter at the top) so the dark soul-steel reads; a clipping layer keeps the mesh in the box
            k.add(k.solid(left, 9, 33, 150, 90, 0x33383e));
            k.add(k.solid(left, 9, 33, 150, 30, 0x3d434a));
            k.add(k.outline(left, 9, 33, 150, 90, 0x0d0b09, false));
            int modelClip = k.add(k.layer(left, 10, 34, 148, 88));
            k.mark("MODEL");
            k.add(k.model(modelClip, 0, 0, 148, 88));
            k.mark("LOOK");
            k.add(IfKit.centred(k.text(left, 0, 126, 168, 14, IfKit.TEXT_FONT, TEAL, "")));
            k.add(k.solid(left, 10, 142, 148, 1, IfKit.BORDER));
            k.add(IfKit.centred(k.text(left, 0, 145, 168, 13, IfKit.TEXT_FONT, MUTED, "Evolution path")));
            // look l: soft soul glow (hidden layer: 3 teal rings fading outward), slot well + trim + item, label
            k.mark("FIRST_LOOK");
            for (int l = 0; l < LOOKS; l++) {
                int x = 16 + l * 50, y = 162;
                if (l == 1)
                    System.out.println("CONST LOOK_STRIDE = " + (k.next() - k.ids.get("FIRST_LOOK")));
                int glow = k.add(IfKit.hidden(k.layer(left, x - 3, y - 3, 42, 38)));
                int[] alpha = {200, 140, 70};
                for (int g = 0; g < 3; g++) {
                    InterfaceDefinition ring = k.outline(glow, g, g, 42 - 2 * g, 38 - 2 * g, TEAL, false);
                    ring.opacity = alpha[g];
                    k.add(ring);
                }
                k.add(k.solid(left, x, y, 36, 32, 0x1b3236)); // well
                k.add(k.outline(left, x, y, 36, 32, IfKit.BORDER, false));
                k.add(k.itemSlot(left, x, y, 36, 32, "Preview")); // click: preview this look in the box above
                k.add(IfKit.centred(k.text(left, x - 7, y + 35, 50, 12, IfKit.TEXT_FONT, MUTED, "")));
            }
            k.add(k.solid(left, 10, 213, 148, 1, IfKit.BORDER));
            k.mark("POINTS");
            k.add(k.text(left, 8, 217, 84, 15, IfKit.TEXT_FONT, IfKit.TEXT, "")); // the Soul Tree bezel sits to its right

            // ---- right: progression ----
            int right = k.panel(0, 196, 62, 292, 240);
            k.add(k.text(right, 10, 4, 200, 14, IfKit.HEADING_FONT, IfKit.GOLD, "Tier progression"));
            k.mark("FIRST_PIP");
            for (int i = 0; i < PIPS; i++) {
                int x = 11 + i * 28, y = 22;
                if (i == 1)
                    System.out.println("CONST PIP_STRIDE = " + (k.next() - k.ids.get("FIRST_PIP")));
                k.add(k.solid(right, x, y, 24, 11, TRACK));
                k.add(IfKit.hidden(k.solid(right, x + 1, y + 1, 22, 9, TEAL)));
                k.add(k.outline(right, x, y, 24, 11, IfKit.TRIM, false));
                k.add(IfKit.centred(k.text(right, x - 2, y + 12, 28, 11, IfKit.TEXT_FONT, MUTED, String.valueOf(i + 1))));
            }
            k.mark("PROGRESS_LEFT");
            k.add(k.text(right, 10, 49, 180, 13, IfKit.TEXT_FONT, IfKit.TEXT, ""));
            k.mark("PROGRESS_RIGHT");
            k.add(IfKit.right(k.text(right, 182, 49, 100, 13, IfKit.HEADING_FONT, TEAL, "")));
            // bevelled bar: dark frame, clipping layer with the fill strips and the sliding cover, trim, label
            int bx = 10, by = 64, bw = 272, bh = 16;
            k.add(k.solid(right, bx - 1, by - 1, bw + 2, bh + 2, 0x0a0d0e));
            k.mark("BAR_CLIP");
            int clip = k.add(k.layer(right, bx, by, bw, bh));
            k.mark("BAR_BASE");
            k.add(k.solid(clip, 0, 0, bw, bh, 0x16808a));
            k.add(k.solid(clip, 0, 0, bw, 4, 0x2fb4be));
            k.add(k.solid(clip, 0, bh - 4, bw, 4, 0x0c4c52));
            k.mark("BAR_COVER");
            k.add(k.solid(clip, 0, 0, bw, bh, TRACK));
            k.add(k.outline(right, bx - 1, by - 1, bw + 2, bh + 2, IfKit.TRIM, false));
            k.mark("BAR_LABEL");
            k.add(IfKit.centred(k.text(right, bx, by + 1, bw, bh - 1, IfKit.TEXT_FONT, IfKit.WHITE, "")));
            k.mark("TOTAL");
            k.add(k.text(right, 10, 83, 150, 13, IfKit.TEXT_FONT, MUTED, ""));
            k.mark("LAST_KILL");
            k.add(IfKit.right(k.text(right, 130, 83, 152, 13, IfKit.TEXT_FONT, IfKit.GREEN, "")));
            k.add(k.solid(right, 10, 100, 272, 1, IfKit.BORDER));
            // stat rows: label + value on alternating strips
            k.mark("FIRST_ROW");
            for (int r = 0; r < ROWS; r++) {
                int y = 104 + r * 16;
                if (r == 1)
                    System.out.println("CONST ROW_STRIDE = " + (k.next() - k.ids.get("FIRST_ROW")));
                k.add(k.solid(right, 6, y, 280, 15, r % 2 == 0 ? IfKit.ROW_A : IfKit.ROW_B));
                k.add(k.text(right, 12, y + 1, 92, 13, IfKit.TEXT_FONT, MUTED, ""));
                k.add(k.text(right, 106, y + 1, 176, 13, IfKit.TEXT_FONT, IfKit.TEXT, ""));
            }
            k.add(k.solid(right, 10, 187, 272, 1, IfKit.BORDER));
            k.mark("FOOTER");
            k.add(k.block(right, 8, 191, 276, 28, MUTED, 13, true)); // ends 21px above the panel's bottom

            // added last (2026-10-18, Soul Tree) so every id above stays put: opens interface 1176
            k.mark("TREE_BUTTON");
            k.bezel(left, 92, 215, 68, 20, "Open");

            k.finish(apply);
        }
    }
}

import net.runelite.cache.definitions.InterfaceDefinition;
import net.runelite.cache.fs.Store;

import java.io.File;

// Builds interface group 1158, The Zelus Relic Forge (RelicForge.java, server), the item upgrader. 2026-10-20 redesign
// on IfKit (the Trading Post's OSRS stone buttons) after "cramped, flat buttons, crude dashed line":
//  - sub-header row: the line under the title (left) and the recipe search box + Clear (right);
//  - left (170): Weapons / Armour / Utility stone tabs (active = pressed), then a scrolling catalogue of recipe
//    cards: an inset item well, the name (2 lines), a tier pill and the success rate; selected = recessed stone;
//  - centre (150), the Transmutation Altar: Base Item socket (the item, or its translucent silhouette while it is
//    missing), an ornamental gold divider with a down marker, the gold Outcome socket, and the stat changes in their
//    own inset panel;
//  - right (136): 6 reagent slots (inventory size, owned/required in the slot's bottom corner), the success rate over
//    a smooth meter (clientscript 10754 sizes the fill), its breakdown, the failure rule, and the Forge Upgrade stone
//    button (its pressed state = not ready).
// Prints "CONST name = id" lines for RelicForge.java. Usage: verify|rebuild <cachePath>
public class BuildRelicForgeInterface {
    static final int GROUP = 1158, TABS = 3, CARDS = 40, CARD_H = 36, CARD_PITCH = 40, REAGENTS = 6, SEGMENTS = 20;
    static final int PANEL_EDGE = 0x1d1915, PANEL_RIM = 0x2b2620, PANEL_FILL = 0x3d362d, WELL = 0x2c2721;
    static final int CARD_A = 0x463f34, CARD_B = 0x423b31, CARD_HOVER = 0x4f4739, CARD_SELECTED = 0x2b251d;
    static final int GOLD_LINE = 0x8a6a3a, OUTCOME_WELL = 0x3a2e14;

    public static void main(String[] args) throws Exception {
        boolean apply = args[0].equals("rebuild");
        try (Store store = new Store(new File(args[1]))) {
            store.load();
            IfKit k = new IfKit(GROUP, store, true);

            k.mark("SUB_HEADER");
            k.add(k.text(0, 26, 44, 300, 16, IfKit.TEXT_FONT, 0xb0a898, ""));
            k.mark("SEARCH");
            searchBar(k, 330, 42, 158, 20);

            // ---- left: tabs + catalogue ----
            int left = framed(k, 24, 64, 170, 242);
            k.mark("FIRST_TAB");
            for (int t = 0; t < TABS; t++)
                k.stoneButton(left, 4 + t * 54, 4, 52, 22, "View", IfKit.TEXT_FONT, "");
            k.mark("SCROLL");
            int scroll = k.add(k.layer(left, 4, 30, 146, 208));
            k.mark("SCROLLBAR");
            k.add(k.layer(left, 152, 30, 16, 208));
            k.mark("FIRST_CARD");
            for (int c = 0; c < CARDS; c++) {
                int card = k.add(k.layer(scroll, 0, c * CARD_PITCH, 146, CARD_H));           // +0
                int zebra = c % 2 == 0 ? CARD_A : CARD_B;
                InterfaceDefinition fill = k.solid(card, 0, 0, 146, CARD_H, zebra);           // +1 click
                fill.clickMask = 2;
                fill.actions = new String[]{"Select"};
                fill.onMouseOverListener = new Object[]{45, -2147483645, CARD_HOVER};
                fill.onMouseLeaveListener = new Object[]{45, -2147483645, zebra};
                fill.hasListener = true;
                k.add(fill);
                k.add(IfKit.hidden(k.solid(card, 0, 0, 146, CARD_H, CARD_SELECTED)));          // +2 selected recess
                k.add(IfKit.hidden(k.outline(card, 0, 0, 146, CARD_H, IfKit.SHADOW, true)));   // +3 its inner shadow
                k.add(k.outline(card, 0, 0, 146, CARD_H, PANEL_RIM, false));                   // +4 divider
                k.add(k.solid(card, 2, 2, 36, 32, WELL));                                     // +5 item well
                k.add(k.itemSlot(card, 2, 2, 36, 32, null));                                  // +6 item
                InterfaceDefinition name = k.text(card, 42, 2, 102, 22, IfKit.TEXT_FONT, IfKit.ORANGE, ""); // +7
                name.yTextAlignment = 0;
                name.lineHeight = 11;
                k.add(name);
                k.add(k.solid(card, 42, 24, 44, 11, 0x221c16));                              // +8 tier pill
                k.add(IfKit.centred(k.text(card, 42, 24, 44, 11, IfKit.TEXT_FONT, IfKit.GOLD, ""))); // +9 tier
                k.add(IfKit.right(k.text(card, 88, 24, 56, 11, IfKit.TEXT_FONT, IfKit.TEXT, "")));  // +10 rate
            }

            // ---- centre: the Transmutation Altar ----
            int centre = framed(k, 198, 64, 150, 242);
            k.add(IfKit.centred(k.text(centre, 4, 3, 142, 16, IfKit.HEADING_FONT, IfKit.ORANGE, "Transmutation Altar")));
            k.add(IfKit.centred(k.text(centre, 4, 20, 142, 12, IfKit.TEXT_FONT, IfKit.WHITE, "Base Item")));
            int sx = (150 - 44) / 2;
            socket(k, centre, sx, 33, WELL, PANEL_EDGE);
            k.mark("BASE_ITEM");
            k.add(k.itemSlot(centre, sx + 4, 37, 36, 32, "Examine"));
            k.mark("BASE_GHOST");
            InterfaceDefinition ghost = k.itemSlot(centre, sx + 4, 37, 36, 32, "Examine");
            ghost.opacity = 150;   // the missing base: a translucent silhouette
            k.add(IfKit.hidden(ghost));
            k.mark("BASE_NAME");
            InterfaceDefinition baseName = IfKit.centred(k.text(centre, 4, 75, 142, 22, IfKit.TEXT_FONT, IfKit.TEXT, ""));
            baseName.yTextAlignment = 0;
            baseName.lineHeight = 11;
            k.add(baseName);
            // ornamental divider: two gold rules either side of a gold down marker
            k.add(k.solid(centre, 14, 103, 50, 1, GOLD_LINE));
            k.add(k.solid(centre, 86, 103, 50, 1, GOLD_LINE));
            k.add(k.solid(centre, 14, 104, 50, 1, 0x15120f));
            k.add(k.solid(centre, 86, 104, 50, 1, 0x15120f));
            k.add(k.sprite(centre, 68, 100, 14, 7, 1114, false));
            k.add(IfKit.centred(k.text(centre, 4, 106, 142, 12, IfKit.TEXT_FONT, IfKit.ORANGE, "Outcome")));
            socket(k, centre, sx, 118, OUTCOME_WELL, GOLD_LINE);
            k.mark("OUTCOME_ITEM");
            k.add(k.itemSlot(centre, sx + 4, 122, 36, 32, "Examine"));
            k.mark("OUTCOME_NAME");
            k.add(IfKit.centred(k.text(centre, 4, 159, 142, 12, IfKit.TEXT_FONT, IfKit.GOLD, "")));
            // stat changes: an inset panel of their own, one stat per line (4 lines + a bottom margin)
            k.add(k.solid(centre, 4, 172, 142, 66, WELL));
            k.add(k.outline(centre, 4, 172, 142, 66, PANEL_EDGE, false));
            k.add(k.text(centre, 9, 173, 132, 13, IfKit.TEXT_FONT, IfKit.GOLD, "Stat changes"));
            k.mark("STAT_DIFF");
            k.add(k.block(centre, 9, 187, 132, 47, IfKit.TEXT, 11, false));

            // ---- right: reagents, success meter, forge ----
            int right = framed(k, 352, 64, 136, 242);
            k.add(k.text(right, 6, 3, 124, 14, IfKit.TEXT_FONT, IfKit.GOLD, "Required Reagents"));
            k.mark("FIRST_REAGENT");
            for (int r = 0; r < REAGENTS; r++) {
                int x = 6 + (r % 3) * 42, y = 20 + (r / 3) * 50;
                k.add(k.solid(right, x, y, 38, 36, WELL));                                     // +0 well
                k.add(k.outline(right, x, y, 38, 36, PANEL_EDGE, false));                     // +1 rim
                k.add(k.itemSlot(right, x + 1, y + 2, 36, 32, "Examine"));                    // +2 item (its own stack number)
                // +3 owned / required, BELOW the slot (the item's stack number sits inside it)
                k.add(IfKit.centred(k.text(right, x - 3, y + 37, 44, 12, IfKit.TEXT_FONT, IfKit.TEXT, "")));
            }
            k.mark("RATE");
            k.add(k.text(right, 6, 122, 124, 14, IfKit.TEXT_FONT, IfKit.WHITE, ""));
            // the segmented meter: 20 blocks red -> amber -> green over a dark track (the server lights them)
            k.add(k.solid(right, 6, 137, 124, 10, 0x1a1612));
            k.add(k.outline(right, 6, 137, 124, 10, PANEL_EDGE, false));
            k.mark("FIRST_SEGMENT");
            for (int s = 0; s < SEGMENTS; s++)
                k.add(IfKit.hidden(k.solid(right, 8 + s * 6, 139, 5, 6, segmentColour(s))));
            k.mark("BREAKDOWN");
            k.add(k.block(right, 6, 150, 124, 24, 0xb0a898, 11, false));
            k.mark("FAILURE");
            k.add(k.block(right, 6, 176, 124, 24, IfKit.ORANGE, 11, false));
            // the primary action: raised stone, a warm gold lift on hover, a bold label
            k.mark("FORGE");
            k.stoneButton(right, 4, 204, 128, 34, "Forge Upgrade", IfKit.HEADING_FONT, "", 0x6b5a33);

            k.finish(apply);
        }
    }

    /** The meter's block colours: red, amber, then green as the rate climbs. */
    static int segmentColour(int s) {
        if (s < 6) return 0xb33a2a;
        if (s < 12) return 0xc69e38;
        return 0x3fa34d;
    }

    /** A framed column: layer, fill, a dark edge and an inner rim. Returns the layer. */
    static int framed(IfKit k, int x, int y, int w, int h) {
        int id = k.add(k.layer(0, x, y, w, h));
        k.add(k.solid(id, 0, 0, w, h, PANEL_FILL));
        k.add(k.outline(id, 0, 0, w, h, PANEL_EDGE, false));
        k.add(k.outline(id, 1, 1, w - 2, h - 2, PANEL_RIM, false));
        return id;
    }

    /** A 44x40 item socket: well, top + left inner shadow, rim. */
    static void socket(IfKit k, int parent, int x, int y, int fill, int rim) {
        k.add(k.solid(parent, x, y, 44, 40, fill));
        k.add(k.solid(parent, x, y, 44, 2, 0x15120f));
        k.add(k.solid(parent, x, y, 2, 40, 0x15120f));
        k.add(k.outline(parent, x, y, 44, 40, rim, false));
    }

    /**
     * 6 comps + bevel: input box (click = search), its text, rim, magnifier, Clear box + label, then the inset bevel
     * (shadow top + left, light edge bottom + right) and the Clear button's rim, so the whole bar reads as one recess.
     */
    static void searchBar(IfKit k, int x, int y, int w, int h) {
        InterfaceDefinition box = k.solid(0, x, y, w, h, 0x221c16);
        box.clickMask = 2;
        box.actions = new String[]{"Search"};
        box.onMouseOverListener = new Object[]{45, -2147483645, 0x2c251d};
        box.onMouseLeaveListener = new Object[]{45, -2147483645, 0x221c16};
        box.hasListener = true;
        k.add(box);
        k.add(k.text(0, x + 20, y, w - 68, h, IfKit.TEXT_FONT, IfKit.GREY, ""));
        k.add(k.outline(0, x, y, w, h, PANEL_EDGE, false));
        k.add(k.sprite(0, x + 4, y + 3, 14, 14, 1043, false));
        InterfaceDefinition clear = k.solid(0, x + w - 45, y + 3, 42, h - 6, IfKit.STONE_FILL);
        clear.clickMask = 2;
        clear.actions = new String[]{"Clear search"};
        clear.onMouseOverListener = new Object[]{45, -2147483645, IfKit.STONE_HOVER};
        clear.onMouseLeaveListener = new Object[]{45, -2147483645, IfKit.STONE_FILL};
        clear.hasListener = true;
        k.add(clear);
        k.add(IfKit.centred(k.text(0, x + w - 45, y + 3, 42, h - 6, IfKit.TEXT_FONT, IfKit.GREY, "Clear")));
        k.add(k.solid(0, x + 1, y + 1, w - 2, 1, IfKit.SHADOW));
        k.add(k.solid(0, x + 1, y + 1, 1, h - 2, IfKit.SHADOW));
        k.add(k.solid(0, x + 1, y + h - 1, w - 1, 1, 0x5a4d3a));
        k.add(k.solid(0, x + w - 1, y + 1, 1, h - 1, 0x5a4d3a));
        k.add(k.outline(0, x + w - 45, y + 3, 42, h - 6, 0x2b2620, false));
    }
}

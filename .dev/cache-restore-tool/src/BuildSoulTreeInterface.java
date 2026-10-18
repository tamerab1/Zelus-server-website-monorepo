import net.runelite.cache.definitions.InterfaceDefinition;
import net.runelite.cache.fs.Store;

import java.io.File;

// Builds interface group 1176, the Soul Tree (content/soulweapons/SoulTreeInterface.java, server), 2026-10-18 round 3:
// the talent tree on the left, a talent inspector on the right, the aura strip below. BuildSoulTreeSprites sprites.
//  - header: subtitle (left), the points gem + "Available Points" (right);
//  - tree (294 wide; a dark void the sprites are blended onto): the wielded weapon floating in a soul glow (model in a
//    clip layer + hidden particles the client plugin animates), soul veins (trunk, left / right branch, three drops;
//    recoloured lit / dim by script 45) down to three nodes;
//  - a node ONLY shows: rank track + 5 hidden arcs, a hidden cyan "selected" ring and gold "mastered" halo, three
//    stacked sockets (bronze / soul-lit / gold; script 44 hover; op "Inspect"), the icon, a dim disc, the level badge
//    over the ring's foot, the title, and the "+ Rank Up" rune (lit / dim stacked, label on top); plus a hidden tooltip
//    text the plugin draws on hover;
//  - inspector: socket + icon (server-set graphics), name, tagline, rank, the mechanics note, current / next rank
//    effects, and its own Rank Up rune with the point cost;
//  - aura strip: three cards (clickable, hover-lit) with orb / silhouette / lock, name, state pill + label and a hidden
//    tooltip; the Reset bezel and its price;
//  - round 4: the Rank Up confirmation modal (body, Confirm / Cancel) appended last; inspector padded to 10px with the
//    status line centred above a centred rune; the selection ring drawn over the mastered halo;
//  - round 5: the evolution preview (Preview bezel + milestone text), inserted before the modal so it stays on top.
// Prints "CONST name = id" lines. Usage: verify|apply|rebuild <cachePath>
public class BuildSoulTreeInterface {
    static final int GROUP = 1176, NODES = 3, ARCS = 5, AURAS = 3, PARTICLES = 10;
    static final int TEAL = 0x2fd2d8, MUTED = 0x8f8573, VEIN_DIM = 0x2a2620;
    static final int BG_TREE = 0x14120f, CARD = 0x1d1913, CARD_HOVER = 0x2a241c;
    static final int SET_GRAPHIC = 44, SET_COLOUR = 45, SELF = -2147483645;
    static final int ICON = 24582, SOCKET = 24591, TRACK = 24597, ARC = 24598, HALO = 24603, GLOW = 24607, GEM = 24608,
            ORB = 24609, SILHOUETTE = 24612, LOCK = 24613, PILL = 24614, DIM_DISC = 24617, SELECT = 24618, RUNE = 24619;

    static IfKit k;

    public static void main(String[] args) throws Exception {
        boolean rebuild = args[0].equals("rebuild");
        boolean apply = args[0].equals("apply") || rebuild;
        try (Store store = new Store(new File(args[1]))) {
            store.load();
            k = new IfKit(GROUP, store, rebuild || !apply);

            // ---- header ----
            k.mark("SUBTITLE");
            k.add(k.text(0, 28, 46, 280, 13, IfKit.TEXT_FONT, MUTED, ""));
            k.mark("POINTS");
            k.add(IfKit.right(k.text(0, 250, 46, 214, 13, IfKit.HEADING_FONT, IfKit.GOLD, "")));
            k.add(sprite(0, 468, 45, 16, 16, GEM));

            // ---- tree ----
            int tw = 294, th = 187, mid = tw / 2;
            int tree = k.panel(0, 24, 63, tw, th);
            k.add(k.solid(tree, 3, 3, tw - 6, th - 6, BG_TREE));
            k.mark("GLOW");
            k.add(sprite(tree, mid - 80, 2, 160, 60, GLOW));
            int clip = k.add(k.layer(tree, mid - 60, 3, 120, 56));
            k.mark("MODEL");
            k.add(k.model(clip, 0, 0, 120, 56));
            k.mark("FIRST_PARTICLE");
            for (int p = 0; p < PARTICLES; p++)
                k.add(IfKit.hidden(k.solid(clip, 14 + p * 10, 50, 2, 2, p % 3 == 0 ? 0xc8ffff : TEAL)));
            int[] cx = {mid - 98, mid, mid + 98};
            k.mark("VEIN_TRUNK");
            k.add(k.solid(tree, mid - 1, 59, 2, 9, VEIN_DIM));
            k.mark("VEIN_LEFT");
            k.add(k.solid(tree, cx[0], 67, mid - cx[0], 2, VEIN_DIM));
            k.mark("VEIN_RIGHT");
            k.add(k.solid(tree, mid, 67, cx[2] - mid + 1, 2, VEIN_DIM));
            k.mark("FIRST_DROP");
            for (int n = 0; n < NODES; n++)
                k.add(k.solid(tree, cx[n] - 1, 68, 2, 9, VEIN_DIM));

            k.mark("FIRST_NODE");
            for (int n = 0; n < NODES; n++) {
                int c = cx[n], base = k.next();
                if (n == 1)
                    System.out.println("CONST NODE_STRIDE = " + (base - k.ids.get("FIRST_NODE")));
                k.add(sprite(tree, c - 26, 76, 52, 52, TRACK));
                for (int a = 0; a < ARCS; a++)
                    k.add(IfKit.hidden(sprite(tree, c - 26, 76, 52, 52, ARC + a)));
                int halo = k.add(IfKit.hidden(sprite(tree, c - 30, 72, 60, 60, HALO)));
                int select = k.add(IfKit.hidden(sprite(tree, c - 30, 72, 60, 60, SELECT))); // over the halo: always readable
                int socket = k.next();
                for (int s = 0; s < 3; s++) {
                    InterfaceDefinition d = sprite(tree, c - 22, 80, 44, 44, SOCKET + s * 2);
                    d.isHidden = s != 0;
                    d.clickMask = 2;
                    d.actions = new String[]{"Inspect"};
                    d.onMouseOverListener = new Object[]{SET_GRAPHIC, SELF, SOCKET + s * 2 + 1};
                    d.onMouseLeaveListener = new Object[]{SET_GRAPHIC, SELF, SOCKET + s * 2};
                    d.hasListener = true;
                    k.add(d);
                }
                int icon = k.add(sprite(tree, c - 16, 86, 32, 32, ICON));
                InterfaceDefinition disc = sprite(tree, c - 17, 85, 34, 34, DIM_DISC);
                disc.opacity = 110;
                int dim = k.add(disc);
                // level badge over the ring's foot
                k.add(k.solid(tree, c - 15, 120, 30, 13, 0x0b0a08));
                k.add(k.outline(tree, c - 15, 120, 30, 13, 0x5a4d3a, false));
                int badge = k.add(IfKit.centred(k.text(tree, c - 15, 120, 30, 13, IfKit.TEXT_FONT, IfKit.GOLD, "")));
                int name = k.add(IfKit.centred(k.text(tree, c - 49, 136, 98, 13, IfKit.HEADING_FONT, IfKit.GOLD, "")));
                int rune = runeButton(tree, c - 31, 153);
                int tip = k.add(IfKit.hidden(k.text(tree, c - 49, 0, 98, 12, IfKit.TEXT_FONT, IfKit.TEXT, "")));
                if (n == 0) {
                    System.out.println("CONST NODE_TRACK = 0, NODE_ARC = 1 (x5)");
                    System.out.println("CONST NODE_SELECT = " + (select - base) + ", NODE_HALO = " + (halo - base));
                    System.out.println("CONST NODE_SOCKET = " + (socket - base) + " (bronze, lit, gold)");
                    System.out.println("CONST NODE_ICON = " + (icon - base) + ", NODE_DIM = " + (dim - base));
                    System.out.println("CONST NODE_BADGE = " + (badge - base) + ", NODE_NAME = " + (name - base));
                    System.out.println("CONST NODE_RUNE = " + (rune - base) + " (dim +1, label +2), NODE_TIP = " + (tip - base));
                }
            }

            // ---- inspector ----
            int insp = k.panel(0, 322, 63, 166, th);
            k.add(k.solid(insp, 3, 3, 160, th - 6, BG_TREE));
            k.mark("INSP_SOCKET");
            k.add(sprite(insp, 10, 8, 44, 44, SOCKET));
            k.mark("INSP_ICON");
            k.add(sprite(insp, 16, 14, 32, 32, ICON));
            k.mark("INSP_NAME");
            k.add(k.text(insp, 60, 9, 96, 14, IfKit.HEADING_FONT, IfKit.GOLD, ""));
            k.mark("INSP_TAGLINE");
            k.add(k.text(insp, 60, 23, 96, 12, IfKit.TEXT_FONT, MUTED, ""));
            k.mark("INSP_RANK");
            k.add(k.text(insp, 60, 37, 96, 12, IfKit.TEXT_FONT, TEAL, ""));
            k.add(k.solid(insp, 10, 56, 146, 1, IfKit.BORDER));
            k.mark("INSP_NOTE");
            k.add(k.block(insp, 10, 59, 146, 37, IfKit.TEXT, 12, false));
            k.add(k.solid(insp, 10, 97, 146, 1, 0x2a2620));
            k.mark("INSP_NOW");
            k.add(k.block(insp, 10, 100, 146, 25, IfKit.TEXT, 12, false));
            k.mark("INSP_NEXT");
            k.add(k.block(insp, 10, 126, 146, 25, IfKit.TEXT, 12, false));
            // the rune and its status line, centred: the status sits ABOVE the rune, inside the card's padding
            k.mark("INSP_RUNE");
            runeButton(insp, 52, 166);
            k.mark("INSP_COST");
            k.add(IfKit.centred(k.text(insp, 10, 153, 146, 12, IfKit.TEXT_FONT, MUTED, "")));

            // ---- aura strip ----
            int strip = k.panel(0, 24, 252, 464, 50);
            k.mark("FIRST_AURA");
            for (int a = 0; a < AURAS; a++) {
                int x = 6 + a * 110, y = 4, base = k.next();
                if (a == 1)
                    System.out.println("CONST AURA_STRIDE = " + (base - k.ids.get("FIRST_AURA")));
                InterfaceDefinition card = k.solid(strip, x, y, 106, 42, CARD);
                card.clickMask = 2;
                card.actions = new String[]{"Toggle"};
                card.onMouseOverListener = new Object[]{SET_COLOUR, SELF, CARD_HOVER};
                card.onMouseLeaveListener = new Object[]{SET_COLOUR, SELF, CARD};
                card.hasListener = true;
                k.add(card);
                k.add(k.outline(strip, x, y, 106, 42, 0x5a4d3a, false));
                int orb = k.add(IfKit.hidden(sprite(strip, x + 5, y + 7, 28, 28, ORB + a)));
                k.add(sprite(strip, x + 5, y + 7, 28, 28, SILHOUETTE));
                k.add(sprite(strip, x + 14, y + 15, 10, 12, LOCK));
                int name = k.add(k.block(strip, x + 37, y + 2, 66, 22, IfKit.GOLD, 11, false));
                int pill = k.next();
                for (int s = 0; s < 3; s++)
                    k.add(IfKit.hidden(sprite(strip, x + 38, y + 25, 62, 14, PILL + s)));
                int label = k.add(IfKit.centred(k.text(strip, x + 38, y + 25, 62, 14, IfKit.TEXT_FONT, IfKit.WHITE, "")));
                int tip = k.add(IfKit.hidden(k.text(strip, x, 0, 106, 12, IfKit.TEXT_FONT, IfKit.TEXT, "")));
                if (a == 0)
                    System.out.println("CONST AURA_ORB = " + (orb - base) + " (silhouette +1, lock +2), AURA_NAME = " + (name - base)
                            + ", AURA_PILL = " + (pill - base) + ", AURA_LABEL = " + (label - base) + ", AURA_TIP = " + (tip - base));
            }
            // Reset: centred in the space right of the cards, its price under it
            k.mark("RESET");
            k.bezel(strip, 340, 7, 118, 20, "Reset");
            k.mark("RESET_PRICE");
            k.add(IfKit.centred(k.text(strip, 336, 30, 126, 12, IfKit.TEXT_FONT, MUTED, "")));

            // ---- evolution preview (round 5): a Preview bezel top-right of the tree, the form + milestones top-left ----
            // round 6: the button sits under the caption in the top-left corner (it floated loose right of the viewport)
            k.mark("PREVIEW_BUTTON");
            k.bezel(tree, 7, 44, 74, 18, "Preview");
            k.mark("PREVIEW_INFO");
            k.add(k.block(tree, 7, 5, 82, 38, IfKit.TEXT, 12, false));

            // ---- Rank Up confirmation (hidden modal over everything) ----
            k.mark("MODAL");
            int modal = k.add(IfKit.hidden(k.layer(0, 24, 44, 464, 258)));
            InterfaceDefinition wash = k.solid(modal, 0, 0, 464, 258, 0x000000);
            wash.opacity = 110;
            k.add(wash);
            int box = k.panel(modal, 102, 66, 260, 122);
            int ring = k.add(k.layer(box, 0, 0, 260, 122));
            int[] alpha = {60, 130};
            for (int g = 0; g < 2; g++) {
                InterfaceDefinition r = k.outline(ring, g, g, 260 - 2 * g, 122 - 2 * g, TEAL, false);
                r.opacity = alpha[g];
                k.add(r);
            }
            k.add(IfKit.centred(k.text(box, 0, 6, 260, 16, IfKit.HEADING_FONT, IfKit.GOLD, "Confirm Rank Up")));
            k.add(k.solid(box, 14, 24, 232, 1, IfKit.BORDER));
            k.mark("MODAL_BODY");
            k.add(k.block(box, 12, 30, 236, 52, IfKit.TEXT, 13, true));
            k.mark("CONFIRM");
            k.bezel(box, 18, 88, 104, 24, "Confirm");
            k.mark("CANCEL");
            k.bezel(box, 138, 88, 104, 24, "Cancel");

            k.finish(apply);
        }
    }

    /** The "+ Rank Up" rune, 62x18: lit (op, hover-swaps to its bright copy) over dim, dark label on top. Returns lit. */
    static int runeButton(int parent, int x, int y) {
        InterfaceDefinition lit = sprite(parent, x, y, 62, 18, RUNE);
        lit.clickMask = 2;
        lit.actions = new String[]{"Rank Up"};
        lit.onMouseOverListener = new Object[]{SET_GRAPHIC, SELF, RUNE + 1};
        lit.onMouseLeaveListener = new Object[]{SET_GRAPHIC, SELF, RUNE};
        lit.hasListener = true;
        int id = k.add(lit);
        k.add(IfKit.hidden(sprite(parent, x, y, 62, 18, RUNE + 2)));
        InterfaceDefinition label = IfKit.centred(k.text(parent, x, y, 62, 18, IfKit.TEXT_FONT, 0x2a1a06, ""));
        label.textShadowed = false; // dark ink engraved on the gold rune
        k.add(label);
        return id;
    }

    /** A plain graphic component showing one sprite (the bezel template, cleared of its border and shadow). */
    static InterfaceDefinition sprite(int parent, int x, int y, int w, int h, int spriteId) {
        InterfaceDefinition s = IfKit.copy(k.buttonT);
        k.place(s, parent, x, y, w, h);
        s.spriteId = spriteId;
        s.spriteTiling = false;
        s.borderType = 0;
        s.shadowColor = 0;
        s.opacity = 0;
        return s;
    }
}

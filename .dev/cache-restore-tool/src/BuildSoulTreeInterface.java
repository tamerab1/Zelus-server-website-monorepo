import net.runelite.cache.definitions.InterfaceDefinition;
import net.runelite.cache.fs.Store;

import java.io.File;

// Builds interface group 1176, the Soul Tree (content/soulweapons/SoulTreeInterface.java, server). Round 9 (2026-10-29):
// two pages behind two tabs instead of three cramped cards (round 8 squeezed the stage, the grid and the inspector side
// by side: the weapon came out a speck and the inspector's text ran into its edges). Sprites: BuildSoulTreeSprites
// (round 2/3 from 24582, round 7 from 24700).
//  - header: subtitle (left), the points gem + "Available Points" (right); the Weapon / Talents tabs under it;
//  - Weapon page: the stage (280 wide: the look's name, the model in a 260x118 clip over a wide dais, soul particles --
//    the client plugin animates them and spins the model --, the < > selector with the line's name) and the Codex of the
//    weapon (180 wide: title, the form / tier / evolution text, the Forms bezel);
//  - Talents page: the constellation (228 wide: 3 rows x 2 node cards, 104x48, ring on the left with the icon
//    concentric in it, name and rank badge on the right; soul links between the rows) and the inspector (232 wide, 14 px
//    padding on both sides: socket + icon, name / tagline / rank beside it, the mechanics note, current / next rank and a
//    full-width Rank Up bezel over a dim plate);
//  - the aura dock (three cards) and Reset with its price, on both pages;
//  - the confirmation modal (title, body, Confirm / Cancel) for Rank Up and for Reset, last so it stays on top.
// Prints "CONST name = id" lines. Usage: verify|apply|rebuild <cachePath>
public class BuildSoulTreeInterface {
    static final int GROUP = 1176, ROWS = 3, COLS = 2, ARCS = 5, AURAS = 3, PARTICLES = 8;
    static final int TEAL = 0x2fd2d8, MUTED = 0x8f8573, LINK_DIM = 0x2a2620;
    static final int BG_TREE = 0x14120f, CARD = 0x1d1913, CARD_HOVER = 0x2a241c, NODE_CARD = 0x1a1612, NODE_EDGE = 0x3a3128;
    static final int SET_GRAPHIC = 44, SET_COLOUR = 45, SELF = -2147483645;
    static final int GEM = 24608, ORB = 24609, SILHOUETTE = 24612, LOCK = 24613, PILL = 24614, SOCKET_BIG = 24591, ICON_BIG = 24582;
    static final int ICON = 24700, TRACK = 24712, ARC = 24713, HALO = 24718, SELECT = 24719, SOCKET = 24720, DIM_DISC = 24726,
            PEDESTAL = 24727, ARROW_LEFT = 24728, ARROW_RIGHT = 24730;
    /** The pages' area, under the tabs and above the dock. */
    static final int PAGE_X = 24, PAGE_Y = 84, PAGE_W = 464, PAGE_H = 166;

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
            k.mark("TAB_WEAPON");
            k.bezelTab(0, 24, 62, 112, 20, "Weapon", true, "Weapon");
            k.mark("TAB_TALENTS");
            k.bezelTab(0, 140, 62, 112, 20, "Talents", true, "Talents");

            // ================= the Weapon page =================
            k.mark("PAGE_WEAPON");
            int pw = k.add(k.layer(0, PAGE_X, PAGE_Y, PAGE_W, PAGE_H));
            // the stage
            int stage = k.panel(pw, 0, 0, 280, PAGE_H);
            k.add(k.solid(stage, 3, 3, 274, PAGE_H - 6, BG_TREE));
            k.mark("STAGE_NAME");
            k.add(IfKit.centred(k.text(stage, 4, 6, 272, 14, IfKit.HEADING_FONT, IfKit.GOLD, "")));
            k.mark("PEDESTAL");
            k.add(sprite(stage, 60, 110, 160, 34, PEDESTAL));
            int clip = k.add(k.layer(stage, 10, 20, 260, 118));
            k.mark("MODEL");
            k.add(k.model(clip, 0, 0, 260, 118));
            k.mark("FIRST_PARTICLE");
            for (int p = 0; p < PARTICLES; p++)
                k.add(IfKit.hidden(k.solid(clip, 76 + p * 15, 108, 2, 2, p % 3 == 0 ? 0xc8ffff : TEAL)));
            k.mark("ARROW_LEFT");
            k.add(arrow(stage, 14, 141, ARROW_LEFT, "Previous weapon"));
            k.mark("ARROW_RIGHT");
            k.add(arrow(stage, 248, 141, ARROW_RIGHT, "Next weapon"));
            k.mark("STAGE_LINE");
            k.add(IfKit.centred(k.text(stage, 40, 144, 200, 14, IfKit.TEXT_FONT, TEAL, "")));
            // the weapon's codex
            int info = k.panel(pw, 284, 0, 180, PAGE_H);
            k.add(k.solid(info, 3, 3, 174, PAGE_H - 6, BG_TREE));
            k.mark("INFO_TITLE");
            k.add(IfKit.centred(k.text(info, 14, 8, 152, 14, IfKit.HEADING_FONT, IfKit.GOLD, "")));
            k.add(k.solid(info, 14, 25, 152, 1, IfKit.BORDER));
            k.mark("INFO_BODY");
            k.add(k.block(info, 14, 30, 152, 104, IfKit.TEXT, 13, false));
            k.mark("PREVIEW_BUTTON");
            k.bezel(info, 14, 140, 152, 18, "Forms");

            // ================= the Talents page =================
            k.mark("PAGE_TALENTS");
            int pt = k.add(IfKit.hidden(k.layer(0, PAGE_X, PAGE_Y, PAGE_W, PAGE_H)));
            int tree = k.panel(pt, 0, 0, 228, PAGE_H);
            k.add(k.solid(tree, 3, 3, 222, PAGE_H - 6, BG_TREE));
            int[] nx = {6, 118};      // node cards' left edges (104 wide)
            int[] ry = {7, 59, 111};  // node cards' tops (48 tall, 4 px links between)
            k.mark("FIRST_LINK");
            for (int r = 0; r < ROWS - 1; r++)
                for (int c = 0; c < COLS; c++)
                    k.add(k.solid(tree, nx[c] + 23, ry[r] + 48, 2, 4, LINK_DIM)); // link r: row r -> row r + 1, col c
            k.mark("FIRST_NODE");
            for (int r = 0; r < ROWS; r++)
                for (int c = 0; c < COLS; c++) {
                    // the ring's centre (x0 + 23.5, y0 + 23.5): ring 39 at +4, halo 45 at +1, socket 33 at +7, icon 24
                    // at +12 (concentric to the half pixel), dim disc 26 at +11
                    int n = r * COLS + c, x0 = nx[c], y0 = ry[r], base = k.next();
                    if (n == 1)
                        System.out.println("CONST NODE_STRIDE = " + (base - k.ids.get("FIRST_NODE")));
                    k.add(k.solid(tree, x0, y0, 104, 48, NODE_CARD));
                    k.add(k.outline(tree, x0, y0, 104, 48, NODE_EDGE, false));
                    int track = k.add(sprite(tree, x0 + 4, y0 + 4, 39, 39, TRACK));
                    for (int a = 0; a < ARCS; a++)
                        k.add(IfKit.hidden(sprite(tree, x0 + 4, y0 + 4, 39, 39, ARC + a)));
                    int halo = k.add(IfKit.hidden(sprite(tree, x0 + 1, y0 + 1, 45, 45, HALO)));
                    int select = k.add(IfKit.hidden(sprite(tree, x0 + 1, y0 + 1, 45, 45, SELECT)));
                    int socket = k.next();
                    for (int s = 0; s < 3; s++) {
                        InterfaceDefinition d = sprite(tree, x0 + 7, y0 + 7, 33, 33, SOCKET + s * 2);
                        d.isHidden = s != 0;
                        d.clickMask = 2;
                        d.actions = new String[]{"Inspect"};
                        d.onMouseOverListener = new Object[]{SET_GRAPHIC, SELF, SOCKET + s * 2 + 1};
                        d.onMouseLeaveListener = new Object[]{SET_GRAPHIC, SELF, SOCKET + s * 2};
                        d.hasListener = true;
                        k.add(d);
                    }
                    int icon = k.add(sprite(tree, x0 + 12, y0 + 12, 24, 24, ICON));
                    InterfaceDefinition disc = sprite(tree, x0 + 11, y0 + 11, 26, 26, DIM_DISC);
                    disc.opacity = 110;
                    int dim = k.add(disc);
                    int name = k.add(k.block(tree, x0 + 46, y0 + 4, 56, 26, IfKit.GOLD, 12, false));
                    k.add(k.solid(tree, x0 + 46, y0 + 33, 30, 11, 0x0b0a08));
                    k.add(k.outline(tree, x0 + 46, y0 + 33, 30, 11, 0x5a4d3a, false));
                    int badge = k.add(IfKit.centred(k.text(tree, x0 + 46, y0 + 33, 30, 11, IfKit.TEXT_FONT, IfKit.GOLD, "")));
                    int tip = k.add(IfKit.hidden(k.text(tree, x0, 0, 104, 12, IfKit.TEXT_FONT, IfKit.TEXT, "")));
                    if (n == 0) {
                        System.out.println("CONST NODE_TRACK = " + (track - base) + ", NODE_ARC = " + (track - base + 1) + " (x5)");
                        System.out.println("CONST NODE_HALO = " + (halo - base) + ", NODE_SELECT = " + (select - base));
                        System.out.println("CONST NODE_SOCKET = " + (socket - base) + " (bronze, lit, gold)");
                        System.out.println("CONST NODE_ICON = " + (icon - base) + ", NODE_DIM = " + (dim - base));
                        System.out.println("CONST NODE_NAME = " + (name - base) + ", NODE_BADGE = " + (badge - base) + ", NODE_TIP = " + (tip - base));
                    }
                }

            // the inspector: 14 px padding left and right (text 204 wide)
            int insp = k.panel(pt, 232, 0, 232, PAGE_H);
            k.add(k.solid(insp, 3, 3, 226, PAGE_H - 6, BG_TREE));
            k.mark("INSP_SOCKET");
            k.add(sprite(insp, 14, 8, 44, 44, SOCKET_BIG));
            k.mark("INSP_ICON");
            k.add(sprite(insp, 24, 18, 24, 24, ICON_BIG)); // concentric with the socket
            k.mark("INSP_NAME");
            k.add(k.text(insp, 64, 9, 154, 15, IfKit.HEADING_FONT, IfKit.GOLD, ""));
            k.mark("INSP_TAGLINE");
            k.add(k.text(insp, 64, 25, 154, 12, IfKit.TEXT_FONT, MUTED, ""));
            k.mark("INSP_RANK");
            k.add(k.text(insp, 64, 38, 154, 12, IfKit.TEXT_FONT, TEAL, ""));
            k.add(k.solid(insp, 14, 56, 204, 1, IfKit.BORDER));
            k.mark("INSP_NOTE");
            k.add(k.block(insp, 14, 60, 204, 26, IfKit.TEXT, 12, false));
            k.mark("INSP_NOW");
            k.add(k.block(insp, 14, 88, 204, 26, IfKit.TEXT, 12, false));
            k.mark("INSP_NEXT");
            k.add(k.block(insp, 14, 116, 204, 26, IfKit.TEXT, 12, false));
            // the action: a dim plate always there, the bezel over it only when a rank can be bought; label on top
            k.add(k.solid(insp, 14, 144, 204, 16, 0x0b0a08));
            k.add(k.outline(insp, 14, 144, 204, 16, 0x3a3128, false));
            k.mark("INSP_RUNE");
            k.bezel(insp, 14, 144, 204, 16, "Rank Up");

            // ---- the aura dock and Reset (both pages) ----
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
            k.mark("RESET");
            k.bezel(strip, 342, 6, 112, 22, "Reset");
            k.mark("RESET_PRICE");
            k.add(IfKit.centred(k.text(strip, 336, 31, 124, 12, IfKit.TEXT_FONT, MUTED, "")));

            // ---- the confirmation modal (Rank Up / Reset), hidden, over everything ----
            k.mark("MODAL");
            int modal = k.add(IfKit.hidden(k.layer(0, 24, 44, 464, 258)));
            InterfaceDefinition wash = k.solid(modal, 0, 0, 464, 258, 0x000000);
            wash.opacity = 110;
            k.add(wash);
            int box = k.panel(modal, 102, 62, 260, 130);
            int ring = k.add(k.layer(box, 0, 0, 260, 130));
            int[] alpha = {60, 130};
            for (int g = 0; g < 2; g++) {
                InterfaceDefinition r = k.outline(ring, g, g, 260 - 2 * g, 130 - 2 * g, TEAL, false);
                r.opacity = alpha[g];
                k.add(r);
            }
            k.mark("MODAL_TITLE");
            k.add(IfKit.centred(k.text(box, 0, 6, 260, 16, IfKit.HEADING_FONT, IfKit.GOLD, "")));
            k.add(k.solid(box, 14, 24, 232, 1, IfKit.BORDER));
            k.mark("MODAL_BODY");
            k.add(k.block(box, 14, 30, 232, 60, IfKit.TEXT, 13, true));
            k.mark("CONFIRM");
            k.bezel(box, 18, 96, 104, 24, "Confirm");
            k.mark("CANCEL");
            k.bezel(box, 138, 96, 104, 24, "Cancel");

            k.finish(apply);
        }
    }

    /** A selector arrow (18x20): the stud sprite, clickable, swapping to its bright copy on hover. */
    static InterfaceDefinition arrow(int parent, int x, int y, int spriteId, String action) {
        InterfaceDefinition d = sprite(parent, x, y, 18, 20, spriteId);
        d.clickMask = 2;
        d.actions = new String[]{action};
        d.onMouseOverListener = new Object[]{SET_GRAPHIC, SELF, spriteId + 1};
        d.onMouseLeaveListener = new Object[]{SET_GRAPHIC, SELF, spriteId};
        d.hasListener = true;
        return d;
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

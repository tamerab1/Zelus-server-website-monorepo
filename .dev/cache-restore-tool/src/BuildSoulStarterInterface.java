import net.runelite.cache.definitions.InterfaceDefinition;
import net.runelite.cache.fs.Store;

import java.io.File;

// Builds interface group 1175, the Soulbound weapon selector (content/soulweapons/SoulStarterInterface.java, server),
// 2026-10-18, round 2. Warm-stone IfKit theme in the 1002 frame:
//  - subtitle banner under the title;
//  - 3 cards (blade / recurve / spire): stone panel, a hidden soul-cyan glow (3 rings) for the picked card, name,
//    archetype in its colour (melee red / ranged green / magic cyan), a tall recessed preview (spun by the client's
//    zelussoulweapon plugin), role + style, three talent rows (pixel diamond in the archetype colour, name column,
//    description column) and a stone "Select" bezel whose label turns white on hover (script 45) and a hidden
//    pressed-in overlay for the picked card;
//  - "Why Soulbound?" strip: dark inset with a gold hairline, three columns (cyan diamond + parchment text);
//  - the confirmation modal: a hidden layer over everything (dim wash, stone panel, heading, body, Bind / Back).
// Fonts have no diamond glyph, so each diamond is 5 solid rows (1/3/5/3/1 px). Prints "CONST name = id" lines.
// Usage: verify|apply|rebuild <cachePath>
public class BuildSoulStarterInterface {
    static final int GROUP = 1175, CARDS = 3, TALENTS = 3;
    static final int CYAN = 0x2fe8f0, YELLOW = 0xffff00, PARCHMENT = 0xffeed4, LABEL = 0xff981f;
    static final int[] ARCHETYPE = {0xff4a3a, 0x3ddc5a, 0x2fd2f0};
    static final int SET_COLOUR = 45, SELF = -2147483645;
    // Karn theme (2026-11-11): the window is the Karn art (24843), the gem close, gold glows instead of soul cyan, warm
    // preview wells, Karn button faces (Select 134x19 = 24848, Bind / Back 112x24 = 24850); SoulStarterInterface matches
    static final int ART = 24843, CLOSE = 24818, CLOSE_HOVER = 24819, SET_GRAPHIC = 44, GLOW = 0xffc35a, EMBER = 0xf06a4a,
            LABEL_KARN = 0xffd27a;

    public static void main(String[] args) throws Exception {
        boolean rebuild = args[0].equals("rebuild");
        boolean apply = args[0].equals("apply") || rebuild;
        try (Store store = new Store(new File(args[1]))) {
            store.load();
            IfKit k = new IfKit(GROUP, store, rebuild || !apply);
            k.karn = true;
            k.karnButtons = new int[][]{{134, 19, 24848, 24849}, {112, 24, 24850, 24851}};
            InterfaceDefinition window = k.want.get(1);
            window.spriteId = ART;
            k.comps.set(1, BuildBondsInterface.encodeIf3(window));
            InterfaceDefinition close = k.want.get(2);
            close.spriteId = CLOSE;
            close.onMouseOverListener = new Object[]{SET_GRAPHIC, SELF, CLOSE_HOVER};
            close.onMouseLeaveListener = new Object[]{SET_GRAPHIC, SELF, CLOSE};
            close.hasListener = true;
            k.comps.set(2, BuildBondsInterface.encodeIf3(close));
            InterfaceDefinition title = k.want.get(3);
            title.textColor = IfKit.GOLD;
            title.originalY = 12; // above the art's divider (root y 51)
            title.originalHeight = 20;
            k.comps.set(3, BuildBondsInterface.encodeIf3(title));
            // ---- subtitle: one line between the title and the divider ----
            k.mark("SUBTITLE");
            k.add(k.block(0, 28, 33, 456, 13, 0xe8d8b8, 12, true));

            // ---- cards ----
            k.mark("FIRST_CARD");
            for (int c = 0; c < CARDS; c++) {
                int x = 24 + c * 157, y = 58, w = 150, h = 196, colour = ARCHETYPE[c];
                int base = k.next();
                if (c == 1)
                    System.out.println("CONST CARD_STRIDE = " + (base - k.ids.get("FIRST_CARD")));
                int card = k.panel(0, x, y, w, h);
                int glow = k.add(IfKit.hidden(k.layer(card, 0, 0, w, h)));
                if (c == 0) System.out.println("CONST CARD_GLOW = " + (glow - base));
                int[] alpha = {40, 120, 190};
                for (int g = 0; g < 3; g++) {
                    InterfaceDefinition ring = k.outline(glow, g, g, w - 2 * g, h - 2 * g, GLOW, false);
                    ring.opacity = alpha[g];
                    k.add(ring);
                }
                int name = k.add(IfKit.centred(k.text(card, 0, 3, w, 14, IfKit.HEADING_FONT, IfKit.GOLD, "")));
                int arch = k.add(IfKit.centred(k.text(card, 0, 17, w, 12, IfKit.TEXT_FONT, colour, "")));
                // tall recessed preview: well, inner shadows, a second darker inner frame, trim
                k.inset(card, 7, 31, w - 14, 70);
                // slate backdrop (lighter at the top) so the dark soul-steel meshes read against it
                k.add(k.solid(card, 8, 32, w - 16, 68, 0x5a3c40)); // warm well, lighter at the top
                k.add(k.solid(card, 8, 32, w - 16, 22, 0x6a484c));
                k.add(k.outline(card, 8, 32, w - 16, 68, 0x9a7840, false));
                // model widgets don't clip: a layer of the box's size does, so no part of the mesh leaves the box
                int clip = k.add(k.layer(card, 8, 32, w - 16, 68));
                int model = k.add(k.model(clip, 0, 0, w - 16, 68));
                int role = k.add(k.block(card, 6, 103, w - 12, 26, IfKit.TEXT, 12, true));
                k.add(k.solid(card, 12, 131, w - 24, 1, 0x9a7840));
                int firstTalent = k.next();
                for (int t = 0; t < TALENTS; t++) {
                    int ty = 134 + t * 12;
                    // fixed columns on every card: diamond +10, name +18 (70px), description +89 (57px, ends at +146)
                    diamond(k, card, 10, ty + 3, colour);
                    k.add(k.text(card, 18, ty, 70, 12, IfKit.TEXT_FONT, colour, ""));
                    k.add(k.text(card, 89, ty, 57, 12, IfKit.TEXT_FONT, IfKit.WHITE, ""));
                }
                int select = k.bezel(card, 8, 171, w - 16, 19, "Select");
                InterfaceDefinition label = k.want.get(select + 1);
                label.textColor = LABEL_KARN;
                label.onMouseOverListener = new Object[]{SET_COLOUR, SELF, 0xffffff};
                label.onMouseLeaveListener = new Object[]{SET_COLOUR, SELF, LABEL_KARN};
                label.hasListener = true;
                k.comps.set(select + 1, BuildBondsInterface.encodeIf3(label));
                // pressed-in look for the picked card: a light dark wash + 2px top / left inner shadow
                int pressed = k.add(IfKit.hidden(k.layer(card, 8, 171, w - 16, 19)));
                InterfaceDefinition wash = k.solid(pressed, 0, 0, w - 16, 19, 0x2a080a);
                wash.opacity = 120;
                k.add(wash);
                k.add(k.solid(pressed, 0, 0, w - 16, 2, 0xc9a24a));
                k.add(k.solid(pressed, 0, 0, 2, 19, 0xc9a24a));
                if (c == 0) {
                    System.out.println("CONST CARD_NAME = " + (name - base));
                    System.out.println("CONST CARD_ARCHETYPE = " + (arch - base));
                    System.out.println("CONST CARD_MODEL = " + (model - base));
                    System.out.println("CONST CARD_ROLE = " + (role - base));
                    System.out.println("CONST CARD_TALENT = " + (firstTalent - base) + " (stride 7: name +5, desc +6)");
                    System.out.println("CONST CARD_SELECT = " + (select - base));
                    System.out.println("CONST CARD_PRESSED = " + (pressed - base));
                }
            }

            // ---- "Why Soulbound?" strip ----
            k.add(k.solid(0, 24, 261, 464, 30, 0x3b2226));
            k.add(k.solid(0, 24, 261, 464, 1, 0x6a1e22));
            k.add(k.outline(0, 24, 261, 464, 30, 0xb08a4a, false));
            k.mark("FIRST_WHY");
            for (int i = 0; i < 3; i++) {
                int x = 30 + i * 155;
                diamond(k, 0, x, 267, EMBER);
                k.add(k.block(0, x + 8, 264, 146, 25, PARCHMENT, 12, false)); // 2 explicit lines
            }

            // ---- confirmation modal (hidden) ----
            k.mark("MODAL");
            int modal = k.add(IfKit.hidden(k.layer(0, 24, 52, 464, 250)));
            InterfaceDefinition dim = k.solid(modal, 0, 0, 464, 250, 0x000000);
            dim.opacity = 110;
            k.add(dim);
            int box = k.panel(modal, 92, 56, 280, 140);
            int ring = k.add(k.layer(box, 0, 0, 280, 140));
            int[] alpha = {60, 130};
            for (int g = 0; g < 2; g++) {
                InterfaceDefinition r = k.outline(ring, g, g, 280 - 2 * g, 140 - 2 * g, GLOW, false);
                r.opacity = alpha[g];
                k.add(r);
            }
            k.add(IfKit.centred(k.text(box, 0, 6, 280, 16, IfKit.HEADING_FONT, IfKit.GOLD, "Confirm Weapon Binding")));
            k.add(k.solid(box, 14, 24, 252, 1, IfKit.BORDER));
            k.mark("MODAL_BODY");
            k.add(k.block(box, 12, 30, 256, 72, IfKit.TEXT, 13, true));
            k.mark("BIND");
            k.bezel(box, 22, 106, 112, 24, "Bind");
            k.mark("BACK");
            k.bezel(box, 146, 106, 112, 24, "Back");

            k.finish(apply);
            if (args[0].equals("export")) { // rev 231 component files for cache stage 30 (PatchKarnUi, 2026-11-11)
                File out = new File(args[2]);
                out.mkdirs();
                for (File f : out.listFiles())
                    f.delete();
                for (int c = 0; c < k.comps.size(); c++)
                    java.nio.file.Files.write(new File(out, c + ".dat").toPath(), k.comps.get(c));
                System.out.println("EXPORTED " + k.comps.size() + " rev 231 components to " + out);
            }
        }
    }

    /** A 5x5 pixel diamond (rows 1/3/5/3/1 px wide) with its top-left at x, y: 5 comps. */
    static void diamond(IfKit k, int parent, int x, int y, int colour) {
        int[] widths = {1, 3, 5, 3, 1};
        for (int r = 0; r < 5; r++)
            k.add(k.solid(parent, x + (5 - widths[r]) / 2, y + r, widths[r], 1, colour));
    }
}

import java.io.File;
import java.nio.file.Files;

import net.runelite.cache.definitions.InterfaceDefinition;
import net.runelite.cache.fs.Store;

// Builds interface group 1184, the Ember Crucible -- the egg-slot icon forge (server: activities/colosseum/EggForge),
// 2026-11-08. Deliberately not the stone 1002 frame: its art is one painted obsidian-and-lava slab (sprite 24776, drawn by
// assets/colosseum/MakeForgeArt) at 12,10, with the current icon and its next tier on two pedestals, the five-gem tier track
// under them, a carousel of the player's icons along the bottom, and a confirmation panel over everything (hidden until
// Forge is pressed). Art origin 12,10; the content stays 8 px or more inside the rim (x 28-484, y 46-308).
// rev 231 build: "export" on the 231 cache; cache stage 21 converts to rev 241 and writes the group.
// Usage: verify <cache231> | export <cache231> <outDir>
public class BuildEggForgeInterface {
    static final int GROUP = 1184, ART = 24776, FORGE = 24777, FORGE_OFF = 24778, SMALL = 24779, SMALL_OFF = 24780,
            GEM_LIT = 24781, GEM_NEXT = 24782, RING = 24783, PANEL = 24784, LEFT = 24785, RIGHT = 24786, COIN = 24774, CLOSE_ART = 24787, FORGE_HOVER = 24788, SMALL_HOVER = 24789, SMALL_OFF_HOVER = 24790, CLOSE_HOVER = 24791;
    static final int HOT = 0xffc24a, EMBER = 0xff8a2a, PALE = 0xf0e0c0, MUTED = 0xb8a890;

    public static void main(String[] args) throws Exception {
        try (Store store = new Store(new File(args[1]))) {
            store.load();
            IfKit k = new IfKit(GROUP, store, true);
            // the 1002 frame's background, close and title stay in the group, hidden: this window paints its own
            for (int c = 1; c <= 3; c++) {
                InterfaceDefinition d = k.want.get(c);
                d.isHidden = true;
                k.comps.set(c, BuildBondsInterface.encodeIf3(d));
            }
            k.add(k.sprite(0, 12, 10, 488, 308, ART, false));
            k.mark("TITLE");
            k.add(IfKit.centred(k.text(0, 136, 17, 240, 26, IfKit.HEADING_FONT, HOT, "The Ember Crucible")));
            k.mark("CLOSE");
            k.add(swaps(hoverable(k, k.sprite(0, 452, 20, 20, 20, CLOSE_ART, false), "Close"), -2147483645, CLOSE_HOVER, CLOSE_ART));

            // balance, top left
            k.add(k.sprite(0, 44, 21, 18, 18, COIN, false));
            k.mark("BALANCE");
            k.add(k.text(0, 67, 23, 62, 14, IfKit.TEXT_FONT, HOT, ""));

            // ---- the stage ----
            k.mark("FROM_NAME");
            k.add(IfKit.centred(k.text(0, 34, 54, 184, 14, IfKit.BIG_FONT, PALE, "")));
            k.mark("TO_NAME");
            k.add(IfKit.centred(k.text(0, 294, 54, 184, 14, IfKit.BIG_FONT, HOT, "")));
            int ped = 70; // the wells: 70 square around the pedestal centres (126,120) and (386,120)
            int clip = k.add(k.layer(0, 126 - ped / 2, 120 - ped / 2, ped, ped));
            k.mark("FROM_MODEL");
            k.add(k.model(clip, 0, 0, ped, ped));
            clip = k.add(k.layer(0, 386 - ped / 2, 120 - ped / 2, ped, ped));
            k.mark("TO_MODEL");
            k.add(k.model(clip, 0, 0, ped, ped));

            // cost, centre
            k.add(IfKit.centred(k.text(0, 186, 66, 140, 14, IfKit.BIG_FONT, MUTED, "Forge cost")));
            k.mark("COST");
            k.add(IfKit.centred(k.text(0, 186, 83, 140, 18, IfKit.HEADING_FONT, HOT, "")));
            k.mark("STATUS");
            k.add(IfKit.centred(k.text(0, 186, 104, 140, 14, IfKit.TEXT_FONT, MUTED, "")));
            k.add(k.sprite(0, 186, 128, 140, 36, FORGE_OFF, false));
            k.mark("FORGE_LIT");
            int forgeArt = k.add(k.sprite(0, 186, 128, 140, 36, FORGE, false));
            k.mark("FORGE");
            k.add(swaps(click(k, IfKit.centred(k.text(0, 186, 128, 140, 36, IfKit.HEADING_FONT, PALE, "Forge")), "Forge", PALE, PALE), GROUP << 16 | forgeArt, FORGE_HOVER, FORGE));

            // ---- the tier track: gem centres x 144 + i * 56, y 190 on the root ----
            k.mark("FIRST_GEM");
            for (int i = 0; i < 5; i++) {
                int gx = 144 + i * 56 - 13, gy = 190 - 13;
                k.add(IfKit.hidden(k.sprite(0, gx, gy, 26, 26, GEM_LIT, false)));
                k.add(IfKit.hidden(k.sprite(0, gx, gy, 26, 26, GEM_NEXT, false)));
                k.add(IfKit.centred(k.text(0, gx, gy, 26, 26, IfKit.TEXT_FONT, PALE, String.valueOf(i + 1))));
            }
            System.out.println("CONST GEM_STRIDE = 3");

            // ---- the carousel: sockets at 99 + i * 54, y 228, 44 across ----
            k.mark("PREV");
            k.add(hoverable(k, k.sprite(0, 56, 232, 26, 36, LEFT, false), "Previous"));
            k.mark("NEXT");
            k.add(hoverable(k, k.sprite(0, 430, 232, 26, 36, RIGHT, false), "Next"));
            k.mark("FIRST_SOCKET");
            for (int i = 0; i < 6; i++) {
                int x = 99 + i * 54;
                k.add(IfKit.hidden(k.sprite(0, x - 3, 225, 50, 50, RING, false)));
                k.add(k.itemSlot(0, x + 4, 234, 36, 32, "Select"));
                k.add(IfKit.centred(k.text(0, x - 5, 276, 54, 14, IfKit.TEXT_FONT, MUTED, "")));
            }
            System.out.println("CONST SOCKET_STRIDE = 3");
            k.mark("EMPTY");
            k.add(IfKit.hidden(IfKit.centred(k.text(0, 99, 276, 314, 14, IfKit.TEXT_FONT, MUTED, ""))));
            k.mark("PAGE");
            k.add(IfKit.centred(k.text(0, 420, 276, 46, 14, IfKit.TEXT_FONT, MUTED, "")));

            // ---- the confirmation: covers the whole window and takes every click under it ----
            InterfaceDefinition cover = k.hidden(k.layer(0, 0, 0, 512, 334));
            cover.noClickThrough = true;
            k.mark("CONFIRM");
            int c = k.add(cover);
            InterfaceDefinition dim = k.solid(c, 12, 10, 488, 308, 0x050304);
            dim.opacity = 110;
            k.add(dim);
            k.add(k.sprite(c, 106, 82, 300, 160, PANEL, false));
            k.add(IfKit.centred(k.text(c, 136, 92, 240, 20, IfKit.HEADING_FONT, HOT, "Feed the Crucible?")));
            k.mark("CONFIRM_TEXT");
            k.add(k.block(c, 126, 124, 260, 62, PALE, 15, true));
            int yesArt = k.add(k.sprite(c, 142, 200, 100, 28, SMALL, false));
            k.mark("CONFIRM_YES");
            k.add(swaps(click(k, IfKit.centred(k.text(c, 142, 200, 100, 28, IfKit.BIG_FONT, PALE, "Forge it")), "Forge it", PALE, PALE), GROUP << 16 | yesArt, SMALL_HOVER, SMALL));
            int noArt = k.add(k.sprite(c, 270, 200, 100, 28, SMALL_OFF, false));
            k.mark("CONFIRM_NO");
            k.add(swaps(click(k, IfKit.centred(k.text(c, 270, 200, 100, 28, IfKit.BIG_FONT, PALE, "Cancel")), "Cancel", PALE, PALE), GROUP << 16 | noArt, SMALL_OFF_HOVER, SMALL_OFF));

            k.finish(false);
            if (args[0].equals("export")) {
                File out = new File(args[2]);
                out.mkdirs();
                for (File f : out.listFiles())
                    f.delete();
                for (int i = 0; i < k.comps.size(); i++)
                    Files.write(new File(out, i + ".dat").toPath(), k.comps.get(i));
                System.out.println("EXPORTED " + k.comps.size() + " rev 231 components to " + out);
            }
        }
    }

    /** a text that is also a button: the hover brightens it (clientscript 45, as IfKit's stone buttons) */
    static InterfaceDefinition click(IfKit k, InterfaceDefinition d, String op, int colour, int hover) {
        d.clickMask = 2;
        d.actions = new String[]{op};
        d.onMouseOverListener = new Object[]{45, -2147483645, hover};
        d.onMouseLeaveListener = new Object[]{45, -2147483645, colour};
        d.hasListener = true;
        return d;
    }

    /** hovering d swaps the sprite of target (clientscript 44: set a component's graphic) */
    static InterfaceDefinition swaps(InterfaceDefinition d, int target, int hover, int normal) {
        d.onMouseOverListener = new Object[]{44, target, hover};
        d.onMouseLeaveListener = new Object[]{44, target, normal};
        d.hasListener = true;
        return d;
    }

    static InterfaceDefinition hoverable(IfKit k, InterfaceDefinition d, String op) {
        d.clickMask = 2;
        d.actions = new String[]{op};
        return d;
    }
}

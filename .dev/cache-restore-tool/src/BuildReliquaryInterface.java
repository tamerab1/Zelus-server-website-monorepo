import java.io.File;
import java.nio.file.Files;

import net.runelite.cache.definitions.InterfaceDefinition;
import net.runelite.cache.fs.Store;

// Builds interface group 1183, the Arbiter's Reliquary -- the Colosseum of Trials' reward shop (server:
// activities/colosseum/ReliquaryInterface), redrawn 2026-11-08 as its own window: not the stone 1002 frame but a painted violet
// vault door (sprite 24792, assets/colosseum/MakeColosseumArt) at 12,10, three tabs and one scrolling grid of item cards, two
// columns. The window's content stays 8 px or more inside the rim (x 30-482, y 54-300).
// rev 231 build: "export" on the 231 cache; cache stage 21 converts to rev 241 and writes the group.
// Usage: verify <cache231> | export <cache231> <outDir>
public class BuildReliquaryInterface {
    static final int GROUP = 1183, ART = 24792, TAB = 24793, TAB_HOVER = 24794, TAB_ACTIVE = 24795, CARD = 24796, CARD_HOVER = 24797, BUY = 24798,
            BUY_HOVER = 24799, COIN = 24774, CLOSE = 24787, CLOSE_HOVER = 24791, CARDS = 40;
    static final String[] TABS = {"Icons", "Gear", "Egg Forge"};
    static final int GOLD = 0xf4dc9a, PALE = 0xe8e0f0, EVENT_COM = -2147483645;

    public static void main(String[] args) throws Exception {
        try (Store store = new Store(new File(args[1]))) {
            store.load();
            IfKit k = new IfKit(GROUP, store, true);
            for (int c = 1; c <= 3; c++) { // the 1002 frame's background, close and title stay in the group, hidden
                InterfaceDefinition d = k.want.get(c);
                d.isHidden = true;
                k.comps.set(c, BuildBondsInterface.encodeIf3(d));
            }
            k.add(k.sprite(0, 12, 10, 488, 308, ART, false));
            k.mark("TITLE");
            k.add(IfKit.centred(k.text(0, 170, 19, 172, 26, IfKit.HEADING_FONT, GOLD, "Arbiter's Reliquary")));
            k.mark("CLOSE");
            InterfaceDefinition close = k.sprite(0, 452, 26, 20, 20, CLOSE, false);
            k.add(swaps(click(close, "Close"), EVENT_COM, CLOSE_HOVER, CLOSE));
            k.add(k.sprite(0, 44, 27, 18, 18, COIN, false));
            k.mark("BALANCE");
            k.add(k.text(0, 67, 29, 76, 14, IfKit.TEXT_FONT, GOLD, ""));

            // ---- tabs ----
            k.mark("FIRST_TAB");
            for (int t = 0; t < TABS.length; t++) {
                int x = 56 + t * 136;
                int art = k.add(k.sprite(0, x, 56, 128, 26, TAB, false));
                k.add(IfKit.hidden(k.sprite(0, x, 56, 128, 26, TAB_ACTIVE, false)));
                k.add(swaps(click(IfKit.centred(k.text(0, x, 56, 128, 26, IfKit.BIG_FONT, PALE, TABS[t])), "View"), GROUP << 16 | art, TAB_HOVER, TAB));
            }
            System.out.println("CONST TAB_STRIDE = 3");

            // ---- the grid ----
            int gx = 32, gy = 92, gridW = 432, gridH = 208, cardW = 210, cardH = 48, pitch = 54;
            k.mark("SCROLL");
            int scroll = k.add(k.layer(0, gx, gy, gridW, gridH));
            k.mark("BAR");
            k.add(k.layer(0, gx + gridW + 2, gy, 16, gridH));
            k.mark("FIRST_CARD");
            for (int c = 0; c < CARDS; c++) {
                int base = k.next();
                if (c == 1)
                    System.out.println("CONST CARD_STRIDE = " + (base - k.ids.get("FIRST_CARD")));
                int card = k.add(IfKit.hidden(k.layer(scroll, (c % 2) * (cardW + 12), (c / 2) * pitch, cardW, cardH)));
                int bg = k.add(swaps(click(k.sprite(card, 0, 0, cardW, cardH, CARD, false), "Examine"), EVENT_COM, CARD_HOVER, CARD));
                int item = k.add(k.itemSlot(card, 8, 8, 36, 32, "Examine"));
                int name = k.add(k.text(card, 52, 7, 152, 14, IfKit.TEXT_FONT, PALE, ""));
                int price = k.add(k.text(card, 52, 26, 94, 14, IfKit.TEXT_FONT, GOLD, ""));
                int buyArt = k.add(k.sprite(card, 152, 24, 52, 20, BUY, false));
                int buy = k.add(swaps(click(IfKit.centred(k.text(card, 152, 24, 52, 20, IfKit.TEXT_FONT, PALE, "Buy")), "Buy"), GROUP << 16 | buyArt, BUY_HOVER, BUY));
                if (c == 0) {
                    System.out.println("CONST CARD_BG = " + (bg - base));
                    System.out.println("CONST CARD_ITEM = " + (item - base));
                    System.out.println("CONST CARD_NAME = " + (name - base));
                    System.out.println("CONST CARD_PRICE = " + (price - base));
                    System.out.println("CONST CARD_BUY = " + (buy - base));
                }
            }

            k.finish(false);
            if (args[0].equals("export")) {
                File out = new File(args[2]);
                out.mkdirs();
                for (File f : out.listFiles())
                    f.delete();
                for (int c = 0; c < k.comps.size(); c++)
                    Files.write(new File(out, c + ".dat").toPath(), k.comps.get(c));
                System.out.println("EXPORTED " + k.comps.size() + " rev 231 components to " + out);
            }
        }
    }

    static InterfaceDefinition click(InterfaceDefinition d, String op) {
        d.clickMask = 2;
        d.actions = new String[]{op};
        return d;
    }

    /** hovering d swaps the sprite of target (clientscript 44: set a component's graphic) */
    static InterfaceDefinition swaps(InterfaceDefinition d, int target, int hover, int normal) {
        d.onMouseOverListener = new Object[]{44, target, hover};
        d.onMouseLeaveListener = new Object[]{44, target, normal};
        d.hasListener = true;
        return d;
    }
}

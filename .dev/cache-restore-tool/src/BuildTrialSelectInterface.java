import java.io.File;
import java.nio.file.Files;

import net.runelite.cache.definitions.InterfaceDefinition;
import net.runelite.cache.fs.Store;

// Builds interface group 1182, the Colosseum of Trials' trial picker (server: activities/colosseum/TrialSelectInterface), redrawn
// 2026-11-08 as its own window: not the stone 1002 frame but a painted crimson arena gate with battlements (sprite 24802,
// assets/colosseum/MakeColosseumArt) at 12,10. Left: the trials by tier. Right: the picked trial's name, its boss standing on
// the arena floor, the facts, the line-up and the two entry buttons. Everything stays 8 px or more inside its panel.
// rev 231 build: "export" on the 231 cache; cache stage 21 converts to rev 241 and writes the group.
// The stage's model box is 126 x 106 and the thumbnails' 36 x 36: the zooms in assets/colosseum/cameras.txt are for those sizes.
// Usage: verify <cache231> | export <cache231> <outDir>
public class BuildTrialSelectInterface {
    static final int GROUP = 1182, THUMBS = 5, ART = 24802, ROW = 24803, ROW_HOVER = 24804, ROW_PICKED = 24805, THUMB = 24806, THUMB_PICKED = 24811,
            SOLO = 24807, SOLO_HOVER = 24808, DUO = 24809, DUO_HOVER = 24810, COIN = 24774, CLOSE = 24787, CLOSE_HOVER = 24791;
    static final int GOLD = 0xecc67a, PALE = 0xf0e6d8, MUTED = 0xb8a890, EVENT_COM = -2147483645;
    /** rows per tier, in Trial's declared order */
    static final int[] TIER_ROWS = {3, 2, 3};

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
            k.add(IfKit.centred(k.text(0, 136, 18, 240, 26, IfKit.HEADING_FONT, GOLD, "The Colosseum of Trials")));
            k.mark("CLOSE");
            k.add(swaps(click(k.sprite(0, 452, 26, 20, 20, CLOSE, false), "Close"), EVENT_COM, CLOSE_HOVER, CLOSE));
            k.add(k.sprite(0, 52, 27, 18, 18, COIN, false));
            k.mark("BALANCE");
            k.add(k.text(0, 75, 29, 46, 14, IfKit.TEXT_FONT, GOLD, ""));

            // ---- left: the trials by tier ----
            int y = 58, row = 0;
            for (int t = 0; t < TIER_ROWS.length; t++) {
                k.mark("TIER_" + (t + 1));
                k.add(IfKit.centred(k.text(0, 33, y, 160, 14, IfKit.BIG_FONT, GOLD, "")));
                y += 16;
                for (int r = 0; r < TIER_ROWS[t]; r++, row++) {
                    k.mark("ROW_" + row);
                    int art = k.add(k.sprite(0, 33, y, 160, 19, ROW, false));
                    k.add(IfKit.hidden(k.sprite(0, 33, y, 160, 19, ROW_PICKED, false)));
                    k.add(swaps(click(IfKit.centred(k.text(0, 33, y, 160, 19, IfKit.TEXT_FONT, PALE, "")), "Select"), GROUP << 16 | art, ROW_HOVER, ROW));
                    y += 22;
                }
                y += 6;
            }
            System.out.println("LIST ends at y " + y);

            // ---- right: the showcase ----
            k.mark("NAME");
            k.add(IfKit.centred(k.text(0, 206, 52, 278, 18, IfKit.HEADING_FONT, GOLD, "")));
            int clip = k.add(k.layer(0, 211, 77, 126, 106));
            k.mark("STAGE_MODEL");
            k.add(k.model(clip, 0, 0, 126, 106));
            k.mark("FACTS");
            k.add(k.block(0, 360, 82, 116, 96, PALE, 15, false));
            k.mark("LINEUP");
            k.add(k.text(0, 206, 196, 120, 14, IfKit.BIG_FONT, GOLD, "Line-up"));
            k.mark("FIRST_THUMB");
            for (int i = 0; i < THUMBS; i++) {
                int x = 206 + i * 44, base = k.next();
                if (i == 1)
                    System.out.println("CONST THUMB_STRIDE = " + (base - k.ids.get("FIRST_THUMB")));
                k.add(click(k.sprite(0, x, 212, 38, 38, THUMB, false), "View"));
                k.add(IfKit.hidden(k.sprite(0, x, 212, 38, 38, THUMB_PICKED, false)));
                int tclip = k.add(k.layer(0, x + 1, 213, 36, 36));
                int model = k.add(k.model(tclip, 0, 0, 36, 36));
                if (i == 0)
                    System.out.println("CONST THUMB_MODEL = " + (model - base));
            }
            k.mark("NOTE");
            k.add(IfKit.centred(k.text(0, 206, 254, 278, 14, IfKit.TEXT_FONT, MUTED, "")));

            int soloArt = k.add(k.sprite(0, 206, 272, 134, 28, SOLO, false));
            k.mark("SOLO");
            k.add(swaps(click(IfKit.centred(k.text(0, 206, 272, 134, 28, IfKit.BIG_FONT, PALE, "Enter solo")), "Enter solo"), GROUP << 16 | soloArt, SOLO_HOVER, SOLO));
            int duoArt = k.add(k.sprite(0, 350, 272, 134, 28, DUO, false));
            k.mark("DUO");
            k.add(swaps(click(IfKit.centred(k.text(0, 350, 272, 134, 28, IfKit.BIG_FONT, PALE, "Form duo")), "Form duo"), GROUP << 16 | duoArt, DUO_HOVER, DUO));

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

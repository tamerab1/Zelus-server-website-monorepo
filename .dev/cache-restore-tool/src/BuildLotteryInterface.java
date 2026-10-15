import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.InterfaceDefinition;
import net.runelite.cache.definitions.loaders.InterfaceLoader;
import net.runelite.cache.fs.*;

import java.io.File;
import java.lang.reflect.Field;
import java.util.*;

// Builds interface group 1164, the Zelus Lottery (activities/lottery/Lottery.java, server), in the Relic Forge frame
// and warm-stone theme:
//  - left: rules, the inset TOTAL POT box, your chance / portion, the draw countdown, Add Items + Add PK Points
//    (bezel buttons) and the last winner;
//  - right: TOP 8 ENTRANTS (name, PKP, win % to 3 decimals) and a YOUR ENTRY card.
// Prints "CONST name = id" lines. Usage: verify|apply|rebuild <cachePath>
public class BuildLotteryInterface {
    static final int GROUP = 1164, SRC = 1002;
    static final int GOLD = 0xffc35a, ORANGE = 0xff981f, TEXT = 0xd8d0c0, GREY = 0x9a9a9a, WHITE = 0xffffff, GREEN = 0x00ff00;
    static final int HEADING_FONT = 496, BIG_FONT = 495, TEXT_FONT = 494, TITLE_Y = 14;
    static final int PANEL_FILL = 0x36302a, BORDER = 0x5a4d3a, ROW_A = 0x2e2924, ROW_B = 0x332c26;
    static final int WELL = 0x241f1a, SHADOW = 0x15120f, TRIM = 0x8a6a3a;
    static final int ROWS = 8;

    public static void main(String[] args) throws Exception {
        boolean rebuild = args[0].equals("rebuild");
        boolean apply = args[0].equals("apply") || rebuild;
        try (Store store = new Store(new File(args[1]))) {
            store.load();
            Storage storage = store.getStorage();
            Index index = store.getIndex(IndexType.INTERFACES);
            if (index.getArchive(GROUP) != null && apply && !rebuild)
                throw new IllegalStateException("interface " + GROUP + " already exists (use rebuild)");
            Archive srcArchive = index.getArchive(SRC);
            ArchiveFiles src = srcArchive.getFiles(storage.loadArchive(srcArchive));
            InterfaceLoader loader = new InterfaceLoader();

            List<byte[]> comps = new ArrayList<>();
            List<InterfaceDefinition> want = new ArrayList<>();
            Map<String, Integer> ids = new LinkedHashMap<>();
            for (int c = 0; c <= 3; c++) {
                InterfaceDefinition d = loader.load((GROUP << 16) | c, src.findFile(c).getContents());
                if (d.parentId != -1)
                    d.parentId = (GROUP << 16) | (d.parentId & 0xffff);
                if (c == 3)
                    d.originalY = TITLE_Y;
                add(comps, want, d);
            }
            InterfaceDefinition layerT = loader.load(0, src.findFile(153).getContents());
            InterfaceDefinition[] rectT = {loader.load(0, src.findFile(159).getContents()),
                    loader.load(0, src.findFile(160).getContents()), loader.load(0, src.findFile(161).getContents())};
            InterfaceDefinition textT = loader.load(0, src.findFile(144).getContents());
            InterfaceDefinition buttonT = loader.load(0, src.findFile(169).getContents());
            InterfaceDefinition labelT = loader.load(0, src.findFile(170).getContents());
            InterfaceDefinition rect = rectT[1];

            // ---- left column ----
            ids.put("LEFT", comps.size());
            int left = panel(comps, want, layerT, rectT, 0, 24, 38, 226, 268);
            add(comps, want, centred(text(textT, left, 0, 4, 226, 16, HEADING_FONT, GOLD, "How it works")));
            ids.put("RULES", comps.size());
            InterfaceDefinition rules = centred(text(textT, left, 8, 21, 210, 38, TEXT_FONT, TEXT, ""));
            rules.yTextAlignment = 0;
            rules.lineHeight = 12;
            add(comps, want, rules);
            // pot: inset box
            add(comps, want, solid(rect, left, 8, 62, 210, 48, WELL));
            add(comps, want, solid(rect, left, 8, 62, 210, 1, SHADOW));
            add(comps, want, solid(rect, left, 8, 62, 1, 48, SHADOW));
            add(comps, want, outline(rect, left, 8, 62, 210, 48, TRIM, false));
            ids.put("POT", comps.size());
            add(comps, want, centred(text(textT, left, 10, 64, 206, 20, HEADING_FONT, GREEN, "")));
            ids.put("POT_LABEL", comps.size());
            add(comps, want, centred(text(textT, left, 10, 82, 206, 13, TEXT_FONT, GREEN, "")));
            ids.put("POT_ITEMS", comps.size());
            add(comps, want, centred(text(textT, left, 10, 95, 206, 13, TEXT_FONT, GREY, "")));
            ids.put("CHANCE", comps.size());
            InterfaceDefinition chance = centred(text(textT, left, 6, 114, 214, 26, TEXT_FONT, WHITE, ""));
            chance.yTextAlignment = 0;
            chance.lineHeight = 12;
            add(comps, want, chance);
            ids.put("TIMER", comps.size());
            add(comps, want, centred(text(textT, left, 6, 142, 214, 17, BIG_FONT, WHITE, "")));
            ids.put("DEPOSIT_PKP", comps.size());
            bezel(comps, want, buttonT, labelT, left, 10, 164, 100, 26, "Deposit");
            ids.put("DEPOSIT_PVMP", comps.size());
            bezel(comps, want, buttonT, labelT, left, 116, 164, 100, 26, "Deposit");
            ids.put("NOTE", comps.size());
            InterfaceDefinition note = centred(text(textT, left, 6, 194, 214, 36, TEXT_FONT, GREY, ""));
            note.yTextAlignment = 0;
            note.lineHeight = 12;
            add(comps, want, note);
            ids.put("LAST_WINNER", comps.size());
            InterfaceDefinition last = centred(text(textT, left, 6, 232, 214, 30, TEXT_FONT, GOLD, ""));
            last.yTextAlignment = 0;
            last.lineHeight = 12;
            add(comps, want, last);

            // ---- right column ----
            ids.put("RIGHT", comps.size());
            int right = panel(comps, want, layerT, rectT, 0, 256, 38, 232, 268);
            add(comps, want, centred(text(textT, right, 0, 4, 232, 16, HEADING_FONT, GOLD, "TOP 8 ENTRANTS")));
            add(comps, want, text(textT, right, 8, 21, 100, 14, TEXT_FONT, GREY, "Name"));
            InterfaceDefinition pkpHead = text(textT, right, 110, 21, 56, 14, TEXT_FONT, GREY, "PKP");
            pkpHead.xTextAlignment = 2;
            add(comps, want, pkpHead);
            InterfaceDefinition pctHead = text(textT, right, 168, 21, 56, 14, TEXT_FONT, GREY, "Win %");
            pctHead.xTextAlignment = 2;
            add(comps, want, pctHead);
            ids.put("FIRST_ROW", comps.size());
            for (int r = 0; r < ROWS; r++) {
                int y = 36 + r * 18;
                add(comps, want, solid(rect, right, 5, y, 222, 17, r % 2 == 0 ? ROW_A : ROW_B));
                add(comps, want, text(textT, right, 8, y, 102, 17, TEXT_FONT, WHITE, ""));
                InterfaceDefinition pkp = text(textT, right, 110, y, 56, 17, TEXT_FONT, GREEN, "");
                pkp.xTextAlignment = 2;
                add(comps, want, pkp);
                InterfaceDefinition pct = text(textT, right, 168, y, 56, 17, TEXT_FONT, ORANGE, "");
                pct.xTextAlignment = 2;
                add(comps, want, pct);
            }
            // YOUR ENTRY card
            add(comps, want, solid(rect, right, 6, 186, 220, 76, WELL));
            add(comps, want, solid(rect, right, 6, 186, 220, 1, SHADOW));
            add(comps, want, outline(rect, right, 6, 186, 220, 76, TRIM, false));
            add(comps, want, centred(text(textT, right, 6, 188, 220, 16, HEADING_FONT, GOLD, "YOUR ENTRY")));
            ids.put("YOUR_ENTRY", comps.size());
            InterfaceDefinition entry = centred(text(textT, right, 10, 205, 212, 54, TEXT_FONT, WHITE, ""));
            entry.yTextAlignment = 0;
            entry.lineHeight = 13;
            add(comps, want, entry);

            // ---- deposit window (hidden): title, balance, 5 quick amounts, chosen amount, live stats, confirm / cancel ----
            ids.put("MODAL", comps.size());
            int modal = comps.size();
            InterfaceDefinition modalLayer = layer(layerT, 0, 66, 54, 380, 236);
            modalLayer.isHidden = true;
            add(comps, want, modalLayer);
            add(comps, want, solid(rect, modal, 0, 0, 380, 236, 0x2a2520));
            add(comps, want, outline(rect, modal, 0, 0, 380, 236, TRIM, false));
            add(comps, want, outline(rect, modal, 1, 1, 378, 234, SHADOW, false));
            add(comps, want, solid(rect, modal, 2, 2, 376, 22, 0x221d18));
            ids.put("MODAL_TITLE", comps.size());
            add(comps, want, centred(text(textT, modal, 4, 2, 372, 22, HEADING_FONT, ORANGE, "")));
            ids.put("MODAL_BALANCE", comps.size());
            add(comps, want, centred(text(textT, modal, 8, 30, 364, 15, TEXT_FONT, WHITE, "")));
            ids.put("FIRST_AMOUNT", comps.size());
            for (int k = 0; k < 5; k++)
                amountButton(comps, want, rect, textT, modal, 10 + k * 72, 52, 68, 24);
            ids.put("MODAL_CHOSEN", comps.size());
            add(comps, want, centred(text(textT, modal, 8, 82, 364, 17, BIG_FONT, WHITE, "")));
            // stats strip: inset
            add(comps, want, solid(rect, modal, 10, 104, 360, 44, WELL));
            add(comps, want, solid(rect, modal, 10, 104, 360, 1, SHADOW));
            add(comps, want, outline(rect, modal, 10, 104, 360, 44, TRIM, false));
            ids.put("MODAL_STATS", comps.size());
            InterfaceDefinition stats = centred(text(textT, modal, 14, 107, 352, 38, TEXT_FONT, WHITE, ""));
            stats.yTextAlignment = 0;
            stats.lineHeight = 13;
            add(comps, want, stats);
            ids.put("CONFIRM", comps.size());
            bezel(comps, want, buttonT, labelT, modal, 40, 158, 140, 30, "Confirm Deposit");
            ids.put("CANCEL", comps.size());
            bezel(comps, want, buttonT, labelT, modal, 200, 158, 140, 30, "Cancel");
            ids.put("MODAL_NOTE", comps.size());
            InterfaceDefinition footer = centred(text(textT, modal, 8, 196, 364, 34, TEXT_FONT, 0x8f8f8f, ""));
            footer.yTextAlignment = 0;
            footer.lineHeight = 12;
            add(comps, want, footer);

            for (Map.Entry<String, Integer> e : ids.entrySet())
                System.out.println("CONST " + e.getKey() + " = " + e.getValue());
            for (int c = 0; c < comps.size(); c++) {
                InterfaceDefinition got = loader.load((GROUP << 16) | c, comps.get(c));
                InterfaceDefinition exp = want.get(c);
                exp.id = (GROUP << 16) | c;
                for (Field f : InterfaceDefinition.class.getFields()) {
                    if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                    if (!Objects.deepEquals(f.get(got), f.get(exp)))
                        throw new IllegalStateException("component " + c + ": " + f.getName() + " " + f.get(got) + " != " + f.get(exp));
                }
            }
            if (!apply) {
                System.out.println("VERIFY OK -- " + comps.size() + " components, nothing written.");
                return;
            }
            BuildSpinnerInterface.write(storage, index, srcArchive, comps, GROUP);
            System.out.println("APPLY complete -- interface " + GROUP + " with " + comps.size() + " components.");
        }
    }

    /** 7 comps: raised stone box (click + hover), border, selected parts (recessed fill, shadow x2, bronze trim), label. */
    static void amountButton(List<byte[]> comps, List<InterfaceDefinition> want, InterfaceDefinition rect,
                             InterfaceDefinition textT, int parent, int x, int y, int w, int h) {
        InterfaceDefinition b = solid(rect, parent, x, y, w, h, 0x38322a);
        b.clickMask = 2;
        b.actions = new String[]{"Select"};
        b.onMouseOverListener = new Object[]{45, -2147483645, 0x453d33};
        b.onMouseLeaveListener = new Object[]{45, -2147483645, 0x38322a};
        b.hasListener = true;
        add(comps, want, b);
        add(comps, want, outline(rect, parent, x, y, w, h, BORDER, false));
        InterfaceDefinition active = solid(rect, parent, x + 1, y + 1, w - 2, h - 1, WELL);
        active.isHidden = true;
        add(comps, want, active);
        InterfaceDefinition top = solid(rect, parent, x + 1, y + 1, w - 2, 1, SHADOW);
        top.isHidden = true;
        add(comps, want, top);
        InterfaceDefinition left = solid(rect, parent, x + 1, y + 1, 1, h - 1, SHADOW);
        left.isHidden = true;
        add(comps, want, left);
        add(comps, want, outline(rect, parent, x, y, w, h, TRIM, true));
        add(comps, want, centred(text(textT, parent, x, y, w, h, TEXT_FONT, TEXT, "")));
    }

    static void bezel(List<byte[]> comps, List<InterfaceDefinition> want, InterfaceDefinition buttonT, InterfaceDefinition labelT,
                      int parent, int x, int y, int w, int h, String action) {
        InterfaceDefinition b = copy(buttonT);
        place(b, parent, x, y, w, h);
        b.clickMask = 2;
        b.actions = new String[]{action};
        b.onMouseOverListener = new Object[]{44, -2147483645, 24470};
        b.onMouseLeaveListener = new Object[]{44, -2147483645, 4525};
        b.hasListener = true;
        add(comps, want, b);
        InterfaceDefinition l = copy(labelT);
        place(l, parent, x + 1, y + 2, w - 2, h - 4);
        l.text = "";
        add(comps, want, l);
    }

    static InterfaceDefinition centred(InterfaceDefinition d) {
        d.xTextAlignment = 1;
        return d;
    }

    static int panel(List<byte[]> comps, List<InterfaceDefinition> want, InterfaceDefinition layerT, InterfaceDefinition[] rectT,
                     int parent, int x, int y, int w, int h) {
        int id = comps.size();
        add(comps, want, layer(layerT, parent, x, y, w, h));
        add(comps, want, solid(rectT[1], id, 0, 0, w, h, PANEL_FILL));
        for (int k = 0; k < 3; k++) {
            InterfaceDefinition r = copy(rectT[k]);
            if (k == 2)
                r.textColor = 0x36302a;
            place(r, id, k, k, w - 2 * k, h - 2 * k);
            add(comps, want, r);
        }
        return id;
    }

    static InterfaceDefinition outline(InterfaceDefinition rect, int parent, int x, int y, int w, int h, int colour, boolean hidden) {
        InterfaceDefinition o = copy(rect);
        place(o, parent, x, y, w, h);
        o.filled = false;
        o.textColor = colour;
        o.isHidden = hidden;
        return o;
    }

    static InterfaceDefinition solid(InterfaceDefinition rect, int parent, int x, int y, int w, int h, int colour) {
        InterfaceDefinition d = copy(rect);
        place(d, parent, x, y, w, h);
        d.filled = true;
        d.textColor = colour;
        d.opacity = 0;
        return d;
    }

    static InterfaceDefinition layer(InterfaceDefinition t, int parent, int x, int y, int w, int h) {
        InterfaceDefinition d = copy(t);
        place(d, parent, x, y, w, h);
        d.scrollWidth = 0;
        d.scrollHeight = 0;
        return d;
    }

    static InterfaceDefinition text(InterfaceDefinition t, int parent, int x, int y, int w, int h, int font, int colour, String s) {
        InterfaceDefinition d = copy(t);
        place(d, parent, x, y, w, h);
        d.fontId = font;
        d.textColor = colour;
        d.text = s;
        d.xTextAlignment = 0;
        d.yTextAlignment = 1;
        d.textShadowed = true;
        return d;
    }

    static void add(List<byte[]> comps, List<InterfaceDefinition> want, InterfaceDefinition d) {
        comps.add(BuildBondsInterface.encodeIf3(d));
        want.add(d);
    }

    static void place(InterfaceDefinition d, int parent, int x, int y, int w, int h) {
        d.parentId = (GROUP << 16) | parent;
        d.originalX = x;
        d.originalY = y;
        d.originalWidth = w;
        d.originalHeight = h;
        d.widthMode = d.heightMode = d.xPositionMode = d.yPositionMode = 0;
        d.isHidden = false;
        d.clickMask = 0;
        d.actions = null;
        d.name = "";
        d.targetVerb = "";
        d.onMouseOverListener = d.onMouseLeaveListener = null;
        d.hasListener = false;
    }

    static InterfaceDefinition copy(InterfaceDefinition s) {
        return BuildBondsInterface.copy(s);
    }
}

import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.InterfaceDefinition;
import net.runelite.cache.definitions.loaders.InterfaceLoader;
import net.runelite.cache.fs.*;

import java.io.File;
import java.util.*;

// Division Rivals matchmaking window (2026-10-15), interface 1172; server: content/rivals/RivalsMatchmakingInterface.
// Relic Forge frame (1002 0-3) in the warm-stone theme, with the Rivals package's own art (badges 24200-24210, primary
// buttons 24227-24229, secondary 24220-24222):
//  - profile strip: division badge, division / SR / record / streak / weekly / points lines;
//  - "Choose your bracket": four selectable cards in a 2 x 2 grid (2026-10-15 r2; name + "N searching", tagline, 5 kit
//    item previews), same 16 components per card as the first two-card version;
//  - status line, then the main button (Find match / Cancel search) or, on a found match, Accept + Decline;
//  - footer: Rewards Shop and Leaderboard text buttons.
// Prints "CONST name = id" lines. Usage: verify|apply|rebuild <cachePath>
public class BuildRivalsMatchmakingInterface {
    static final int GROUP = 1172, SRC = 1002;
    static final int GOLD = 0xffc35a, TEXT = 0xd8d0c0, GREY = 0x9a9a9a, WHITE = 0xffffff;
    static final int HEADING_FONT = 496, BIG_FONT = 495, TEXT_FONT = 494, TITLE_Y = 14;
    static final int PANEL_FILL = 0x36302a, CARD_FILL = 0x2a2520, CARD_HOVER = 0x342d26, SHADE = 0x0d0b09, TRIM = 0x4a3a24;
    static final int PRIMARY = 24227, PRIMARY_HOVER = 24228, SECONDARY = 24220, SECONDARY_HOVER = 24221;

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
            InterfaceDefinition spriteT = loader.load(0, src.findFile(169).getContents());
            Archive a84 = index.getArchive(84);
            InterfaceDefinition modelT = loader.load(0, a84.getFiles(storage.loadArchive(a84)).findFile(4).getContents());
            InterfaceDefinition rect = rectT[1];

            // ---- profile strip ----
            int profile = panel(comps, want, layerT, rectT, 0, 24, 34, 464, 68);
            ids.put("BADGE", comps.size());
            add(comps, want, sprite(spriteT, profile, 4, 4, 60, 60, 24200));
            ids.put("DIVISION", comps.size());
            add(comps, want, text(textT, profile, 72, 3, 380, 18, HEADING_FONT, GOLD, ""));
            ids.put("LINE_RECORD", comps.size());
            add(comps, want, text(textT, profile, 72, 22, 380, 14, TEXT_FONT, TEXT, ""));
            ids.put("LINE_WEEKLY", comps.size());
            add(comps, want, text(textT, profile, 72, 36, 380, 14, TEXT_FONT, TEXT, ""));
            ids.put("LINE_POINTS", comps.size());
            add(comps, want, text(textT, profile, 72, 50, 380, 14, TEXT_FONT, GREY, ""));

            InterfaceDefinition section = text(textT, 0, 24, 105, 464, 16, BIG_FONT, GOLD, "Choose your bracket");
            section.xTextAlignment = 1;
            add(comps, want, section);

            // ---- mode cards ----
            int[] cardX = {24, 260, 24, 260}, cardY = {123, 123, 187, 187};
            for (int k = 0; k < 4; k++) {
                ids.put("CARD" + k, comps.size());
                int card = panel(comps, want, layerT, rectT, 0, cardX[k], cardY[k], 228, 61);        // +0..+4
                InterfaceDefinition bg = solid(rect, card, 3, 3, 222, 55, CARD_FILL);               // +5 click
                bg.clickMask = 2 | 4; // one bit per option: with only 2, "Configure layout" never showed
                bg.actions = new String[]{"Select", "Configure layout"}; // option 2: RivalsLayout
                hover(bg, CARD_FILL, CARD_HOVER);
                add(comps, want, bg);
                InterfaceDefinition recess = solid(rect, card, 3, 3, 222, 55, SHADE);               // +6 selected
                recess.opacity = 150;
                recess.isHidden = true;
                add(comps, want, recess);
                InterfaceDefinition trim = outline(rect, card, 2, 2, 224, 57, TRIM);                // +7 selected
                trim.isHidden = true;
                add(comps, want, trim);
                add(comps, want, text(textT, card, 8, 3, 150, 16, HEADING_FONT, GOLD, ""));        // +8 name
                add(comps, want, text(textT, card, 8, 18, 214, 13, TEXT_FONT, GREY, ""));           // +9 tagline
                for (int i = 0; i < 5; i++) {                                                         // +10..+14
                    InterfaceDefinition m = BuildBondsInterface.copy(modelT);
                    m.contentType = 0;
                    place(m, card, 8 + i * 44, 31, 40, 27);
                    m.modelId = -1;
                    add(comps, want, m);
                }
                InterfaceDefinition searching = text(textT, card, 150, 4, 72, 14, TEXT_FONT, 0x40ac3b, ""); // +15
                searching.xTextAlignment = 2;
                add(comps, want, searching);
            }

            // ---- status + buttons ----
            ids.put("STATUS", comps.size());
            InterfaceDefinition status = text(textT, 0, 24, 252, 464, 16, TEXT_FONT, WHITE, "");
            status.xTextAlignment = 1;
            add(comps, want, status);
            ids.put("MAIN_BUTTON", comps.size());
            add(comps, want, button(spriteT, 0, 172, 272, 144, 30, PRIMARY, PRIMARY_HOVER, "Find match"));
            ids.put("MAIN_LABEL", comps.size());
            add(comps, want, label(textT, 172, 272, 144, 30, "Find match"));
            ids.put("ACCEPT", comps.size());
            InterfaceDefinition accept = button(spriteT, 0, 88, 272, 144, 30, PRIMARY, PRIMARY_HOVER, "Accept");
            accept.isHidden = true;
            add(comps, want, accept);
            InterfaceDefinition acceptLabel = label(textT, 88, 272, 144, 30, "<col=7dff7d>Accept</col>");
            acceptLabel.isHidden = true;
            add(comps, want, acceptLabel);
            ids.put("DECLINE", comps.size());
            InterfaceDefinition decline = button(spriteT, 0, 256, 272, 144, 30, SECONDARY, SECONDARY_HOVER, "Decline");
            decline.isHidden = true;
            add(comps, want, decline);
            InterfaceDefinition declineLabel = label(textT, 256, 272, 144, 30, "<col=ff6a6a>Decline</col>");
            declineLabel.isHidden = true;
            add(comps, want, declineLabel);
            // footer text buttons
            ids.put("SHOP", comps.size());
            InterfaceDefinition shop = text(textT, 0, 26, 284, 130, 16, TEXT_FONT, 0xff981f, "Rewards Shop");
            shop.clickMask = 2;
            shop.actions = new String[]{"Open"};
            hover(shop, 0xff981f, 0xffc35a);
            add(comps, want, shop);
            ids.put("LEADERBOARD", comps.size());
            InterfaceDefinition board = text(textT, 0, 356, 284, 130, 16, TEXT_FONT, 0xff981f, "Leaderboard");
            board.xTextAlignment = 2;
            board.clickMask = 2;
            board.actions = new String[]{"Open"};
            hover(board, 0xff981f, 0xffc35a);
            add(comps, want, board);

            for (Map.Entry<String, Integer> e : ids.entrySet())
                System.out.println("CONST " + e.getKey() + " = " + e.getValue());
            BuildChampionLadderInterface.check(loader, comps, want, GROUP);
            if (!apply) {
                System.out.println("VERIFY OK -- " + comps.size() + " components, nothing written.");
                return;
            }
            BuildSpinnerInterface.write(storage, index, srcArchive, comps, GROUP);
            System.out.println("APPLY complete -- interface " + GROUP + " with " + comps.size() + " components.");
        }
    }

    static InterfaceDefinition sprite(InterfaceDefinition t, int parent, int x, int y, int w, int h, int spriteId) {
        InterfaceDefinition d = BuildBondsInterface.copy(t);
        place(d, parent, x, y, w, h);
        d.spriteId = spriteId;
        d.spriteTiling = false;
        d.borderType = 0;
        d.shadowColor = 0;
        return d;
    }

    static InterfaceDefinition button(InterfaceDefinition t, int parent, int x, int y, int w, int h, int normal, int hovered, String action) {
        InterfaceDefinition b = sprite(t, parent, x, y, w, h, normal);
        b.clickMask = 2;
        b.actions = new String[]{action};
        b.onMouseOverListener = new Object[]{44, -2147483645, hovered};
        b.onMouseLeaveListener = new Object[]{44, -2147483645, normal};
        b.hasListener = true;
        return b;
    }

    static InterfaceDefinition label(InterfaceDefinition textT, int x, int y, int w, int h, String s) {
        InterfaceDefinition l = text(textT, 0, x, y, w, h, BIG_FONT, 0xff981f, s);
        l.xTextAlignment = 1;
        return l;
    }

    static void hover(InterfaceDefinition d, int normal, int hovered) {
        d.onMouseOverListener = new Object[]{45, -2147483645, hovered};
        d.onMouseLeaveListener = new Object[]{45, -2147483645, normal};
        d.hasListener = true;
    }

    static int panel(List<byte[]> comps, List<InterfaceDefinition> want, InterfaceDefinition layerT, InterfaceDefinition[] rectT,
                     int parent, int x, int y, int w, int h) {
        int id = comps.size();
        InterfaceDefinition l = BuildBondsInterface.copy(layerT);
        place(l, parent, x, y, w, h);
        l.scrollWidth = 0;
        l.scrollHeight = 0;
        add(comps, want, l);
        add(comps, want, solid(rectT[1], id, 0, 0, w, h, PANEL_FILL));
        for (int k = 0; k < 3; k++) {
            InterfaceDefinition r = BuildBondsInterface.copy(rectT[k]);
            if (k == 2)
                r.textColor = 0x36302a;
            place(r, id, k, k, w - 2 * k, h - 2 * k);
            add(comps, want, r);
        }
        return id;
    }

    static InterfaceDefinition outline(InterfaceDefinition rect, int parent, int x, int y, int w, int h, int colour) {
        InterfaceDefinition o = BuildBondsInterface.copy(rect);
        place(o, parent, x, y, w, h);
        o.filled = false;
        o.textColor = colour;
        return o;
    }

    static InterfaceDefinition solid(InterfaceDefinition rect, int parent, int x, int y, int w, int h, int colour) {
        InterfaceDefinition d = BuildBondsInterface.copy(rect);
        place(d, parent, x, y, w, h);
        d.filled = true;
        d.textColor = colour;
        d.opacity = 0;
        return d;
    }

    static InterfaceDefinition text(InterfaceDefinition t, int parent, int x, int y, int w, int h, int font, int colour, String s) {
        InterfaceDefinition d = BuildBondsInterface.copy(t);
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
}

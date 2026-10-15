import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.InterfaceDefinition;
import net.runelite.cache.definitions.loaders.InterfaceLoader;
import net.runelite.cache.fs.*;

import java.io.File;
import java.lang.reflect.Field;
import java.util.*;

// Builds interface group 1163, the Zelus Scoreboard (activities/scoreboard/Scoreboard.java, server), in the Relic Forge
// frame and warm-stone theme:
//  - 4 category tabs (PvP / PvM / Skilling / Misc), recessed OSRS style;
//  - User | Clan and Daily | Weekly segmented toggles, and the selected board's title (right);
//  - left: up to 7 stat buttons for the category; right: the top 10 (rank, name, score, reward);
//  - an inset "your rank" pane and the day / week countdown + payout line.
// Prints "CONST name = id" lines. Usage: verify|apply|rebuild <cachePath>
public class BuildScoreboardInterface {
    static final int GROUP = 1163, SRC = 1002;
    static final int GOLD = 0xffc35a, TEXT = 0xd8d0c0, GREY = 0x9a9a9a, WHITE = 0xffffff;
    static final int HEADING_FONT = 496, BIG_FONT = 495, TEXT_FONT = 494, TITLE_Y = 14;
    static final int PANEL_FILL = 0x36302a, BORDER = 0x5a4d3a, ROW_A = 0x2e2924, ROW_B = 0x332c26;
    static final int TAB_FILL = 0x38322a, TAB_HOVER = 0x453d33, TAB_ACTIVE = 0x241f1a, SHADOW = 0x15120f, TRIM = 0x8a6a3a;
    static final int TABS = 4, STATS = 7, ROWS = 10;

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
            InterfaceDefinition rect = rectT[1];
            InterfaceDefinition buttonT = loader.load(0, src.findFile(169).getContents());
            BUTTON = buttonT;

            ids.put("FIRST_TAB", comps.size());
            for (int t = 0; t < TABS; t++)
                bezelTab(comps, want, rect, textT, 0, 24 + t * 117, 34, 113, 22, "View", true);

            // toggles: User | Clan, Daily | Weekly (segment halves: box, active fill, shadow, trim, label)
            // Daily | Weekly on the left (x 30..180); the board title owns x 196..488 (players only: no clan boards)
            ids.put("DAILY", comps.size());
            bezelTab(comps, want, rect, textT, 0, 30, 60, 72, 20, "Select", false);
            ids.put("WEEKLY", comps.size());
            bezelTab(comps, want, rect, textT, 0, 106, 60, 74, 20, "Select", false);
            ids.put("BOARD_TITLE", comps.size());
            InterfaceDefinition boardTitle = text(textT, 0, 196, 57, 292, 26, TEXT_FONT, GOLD, "");
            boardTitle.lineHeight = 12;
            boardTitle.xTextAlignment = 2;
            add(comps, want, boardTitle);

            // left: stat buttons
            int left = panel(comps, want, layerT, rectT, 0, 24, 82, 164, 174);
            ids.put("FIRST_STAT", comps.size());
            for (int s = 0; s < STATS; s++) {
                bezelTab(comps, want, rect, textT, left, 6, 6 + s * 23, 152, 21, "Select", false);
                // the entry's item icon (scaled into the row), then the label moved right of it
                InterfaceDefinition icon = copy(buttonT);
                place(icon, left, 10, 8 + s * 23, 21, 17); // centred in the 21px row
                icon.spriteId = -1;
                icon.spriteTiling = false;
                icon.borderType = 0;
                icon.shadowColor = 0;
                add(comps, want, icon);
                InterfaceDefinition label = want.get(want.size() - 2);
                label.originalX = 34;
                label.originalWidth = 122;
                label.xTextAlignment = 0;
                comps.set(comps.size() - 2, BuildBondsInterface.encodeIf3(label));
            }

            // right: top 10
            // the table spans the panel's full inner width: Rank 34 | User 124 | Score 62 | Reward 58 (+ gutters)
            int right = panel(comps, want, layerT, rectT, 0, 194, 82, 294, 174);
            add(comps, want, solid(rect, right, 5, 3, 284, 17, 0x2a2520));
            add(comps, want, text(textT, right, 9, 3, 34, 17, TEXT_FONT, GOLD, "Rank"));
            add(comps, want, text(textT, right, 45, 3, 124, 17, TEXT_FONT, GOLD, "User"));
            ids.put("SCORE_HEAD", comps.size());
            InterfaceDefinition scoreHead = text(textT, right, 163, 3, 62, 17, TEXT_FONT, GOLD, "Score");
            scoreHead.xTextAlignment = 2;
            add(comps, want, scoreHead);
            InterfaceDefinition rewardHead = text(textT, right, 227, 3, 58, 17, TEXT_FONT, GOLD, "Reward");
            rewardHead.xTextAlignment = 2;
            add(comps, want, rewardHead);
            ids.put("FIRST_ROW", comps.size());
            for (int r = 0; r < ROWS; r++) {
                int y = 20 + r * 15;
                add(comps, want, solid(rect, right, 5, y, 284, 15, r % 2 == 0 ? ROW_A : ROW_B));
                add(comps, want, text(textT, right, 9, y, 34, 15, TEXT_FONT, GOLD, ""));
                add(comps, want, text(textT, right, 45, y, 124, 15, TEXT_FONT, WHITE, ""));
                InterfaceDefinition score = text(textT, right, 163, y, 62, 15, TEXT_FONT, 0x40ac3b, "");
                score.xTextAlignment = 2;
                add(comps, want, score);
                InterfaceDefinition reward = text(textT, right, 227, y, 58, 15, TEXT_FONT, TEXT, "");
                reward.xTextAlignment = 2;
                add(comps, want, reward);
            }

            // personal pane: inset strip
            // footer: 8px clear of the bottom rim (2026-10-15: the payout line sat on it)
            add(comps, want, solid(rect, 0, 24, 260, 464, 17, TAB_ACTIVE));
            add(comps, want, solid(rect, 0, 24, 260, 464, 1, SHADOW));
            add(comps, want, outline(rect, 0, 24, 260, 464, 17, TRIM, false));
            ids.put("PERSONAL", comps.size());
            InterfaceDefinition personal = text(textT, 0, 28, 260, 456, 17, TEXT_FONT, WHITE, "");
            personal.xTextAlignment = 1;
            add(comps, want, personal);
            ids.put("COUNTDOWN", comps.size());
            InterfaceDefinition countdown = text(textT, 0, 24, 280, 464, 11, TEXT_FONT, 0xff981f, "");
            countdown.xTextAlignment = 1;
            add(comps, want, countdown);
            ids.put("PAYOUT", comps.size());
            InterfaceDefinition payout = text(textT, 0, 24, 292, 464, 11, TEXT_FONT, GREY, "");
            payout.xTextAlignment = 1;
            add(comps, want, payout);

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

    /** The OSRS bezel button sprite component (1002:169), set in main. */
    static InterfaceDefinition BUTTON;
    // 2026-10-15: the soft gold trim (b08d4a) still read as a yellow box: pressed stone = deeper recess + dark bronze
    static final int ACTIVE_SHADE = 0x0d0b09, ACTIVE_TRIM = 0x4a3a24, ACTIVE_OPACITY = 70; // pressed stone: lighter trims read as yellow

    /**
     * 4 comps: a raised OSRS bezel button (sprite, hover script 44): the button (click), the active state (hidden): a
     * dark translucent recess + a soft gold trim, then the label. Server: active parts +1..+2, label +3.
     */
    static void bezelTab(List<byte[]> comps, List<InterfaceDefinition> want, InterfaceDefinition rect, InterfaceDefinition textT,
                         int parent, int x, int y, int w, int h, String action, boolean bold) {
        InterfaceDefinition b = copy(BUTTON);
        place(b, parent, x, y, w, h);
        b.clickMask = 2;
        b.actions = new String[]{action};
        b.onMouseOverListener = new Object[]{44, -2147483645, 24470};
        b.onMouseLeaveListener = new Object[]{44, -2147483645, 4525};
        b.hasListener = true;
        add(comps, want, b);
        InterfaceDefinition recess = solid(rect, parent, x + 2, y + 2, w - 4, h - 4, ACTIVE_SHADE);
        recess.opacity = ACTIVE_OPACITY;
        recess.isHidden = true;
        add(comps, want, recess);
        add(comps, want, outline(rect, parent, x + 1, y + 1, w - 2, h - 2, ACTIVE_TRIM, true));
        InterfaceDefinition l = text(textT, parent, x, y, w, h, bold ? HEADING_FONT : TEXT_FONT, TEXT, "");
        l.xTextAlignment = 1;
        add(comps, want, l);
    }

    static void hover(InterfaceDefinition d, int normal, int hovered) {
        d.onMouseOverListener = new Object[]{45, -2147483645, hovered};
        d.onMouseLeaveListener = new Object[]{45, -2147483645, normal};
        d.hasListener = true;
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

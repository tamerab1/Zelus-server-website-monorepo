import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.InterfaceDefinition;
import net.runelite.cache.definitions.loaders.InterfaceLoader;
import net.runelite.cache.fs.*;

import java.io.File;
import java.lang.reflect.Field;
import java.util.*;

// Builds interface group 1158, The Zelus Relic Forge (RelicForge.java, server), replacing the legacy item upgrader 1110
// (::upgrade > Item Upgrade, the Zelus Upgrader object). Same frame / warm-stone theme as the Workbench (1153):
//  - left (150): Weapons / Armour / Utility tabs, then a scrolling catalogue of recipe cards (outcome icon, name,
//    tier + rate line) with hover and a gold selected frame;
//  - centre (170), the Transmutation Altar: "Base Item" in a recessed socket, a gold energy line, the "Outcome" in a
//    gold socket, then the stat changes;
//  - right (136): 6 reagent slots (item + owned/required), a 20-segment success gauge with its breakdown, the failure
//    rule, and the Forge Upgrade button.
// Every section has its own box (no overlap). Encoder/verification shared with BuildWorkbenchInterface.
// Prints "CONST name = id" lines for RelicForge.java. Usage: verify|apply|rebuild <cachePath>
public class BuildRelicForgeInterface {
    static final int GROUP = 1158, SRC = 1002;
    static final int GOLD = 0xffc35a, ORANGE = 0xff981f, TEXT = 0xd8d0c0, GREY = 0x9a9a9a, WHITE = 0xffffff;
    static final int HEADING_FONT = 496, TEXT_FONT = 494;
    static final int TITLE_Y = 14, SUB_Y = 45;
    static final int BOX = 0x433b31, BOX_HOVER = 0x524737, BORDER = 0x5a4d3a;
    static final int SLOT_FILL = 0x2a2520, SLOT_SHADOW = 0x1a1612, SLOT_BORDER = 0x5a4d3a;
    static final int OUTCOME_FILL = 0x3a2e14, GAUGE_BACK = 0x1a1612;
    /** Selected card / active tab: recessed fill + soft gold trim (feedback: no neon 1px outlines). */
    static final int ACTIVE_FILL = 0x1c1814, ACTIVE_TRIM = 0x4a3a24; // dark bronze: b08d4a still read as yellow
    static final int TABS = 3, CARDS = 40, CARD_H = 34, CARD_PITCH = 40, REAGENTS = 6, SEGMENTS = 20;
    static final int COMPONENTS = 472;

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
            // 0-3: root, background, close, title (title moved up to TITLE_Y)
            for (int c = 0; c <= 2; c++) {
                InterfaceDefinition d = loader.load((GROUP << 16) | c, src.findFile(c).getContents());
                if (d.parentId != -1)
                    d.parentId = (GROUP << 16) | (d.parentId & 0xffff);
                add(comps, want, d);
            }
            InterfaceDefinition title = loader.load((GROUP << 16) | 3, src.findFile(3).getContents());
            title.parentId = (GROUP << 16) | (title.parentId & 0xffff);
            title.originalY = TITLE_Y;
            add(comps, want, title);
            InterfaceDefinition layerT = loader.load(0, src.findFile(153).getContents());
            InterfaceDefinition[] rectT = {loader.load(0, src.findFile(159).getContents()),
                    loader.load(0, src.findFile(160).getContents()), loader.load(0, src.findFile(161).getContents())};
            InterfaceDefinition textT = loader.load(0, src.findFile(144).getContents());
            InterfaceDefinition buttonT = loader.load(0, src.findFile(169).getContents());
            InterfaceDefinition labelT = loader.load(0, src.findFile(170).getContents());
            InterfaceDefinition rect = rectT[1];

            // 4: the sub-bar under the title
            ids.put("SUB_HEADER", comps.size());
            InterfaceDefinition sub = text(textT, 0, 26, SUB_Y, 300, 13, TEXT_FONT, 0xa0a0a0, "");
            sub.xTextAlignment = 0;
            add(comps, want, sub);

            // ---- left: category tabs + recipe catalogue ----
            int left = BuildWorkbenchInterface.panel(comps, want, layerT, rectT, 0, 24, 62, 160, 243);
            fix(want, comps, left);
            ids.put("FIRST_TAB", comps.size());
            for (int t = 0; t < TABS; t++) {
                int x = 4 + t * 51;
                bezelTab(comps, want, rect, buttonT, left, x, 4, 50, 24, "View");
                InterfaceDefinition l = text(textT, left, x, 4, 50, 24, TEXT_FONT, TEXT, "");
                l.xTextAlignment = 1;
                add(comps, want, l);
            }
            ids.put("SCROLL", comps.size());
            ids.put("SCROLLBAR", comps.size() + 1);
            add(comps, want, layer(layerT, left, 4, 32, 136, 207));
            add(comps, want, layer(layerT, left, 142, 32, 16, 207));
            int scroll = ids.get("SCROLL");
            ids.put("FIRST_CARD", comps.size());
            for (int c = 0; c < CARDS; c++) {
                int card = comps.size();
                add(comps, want, layer(layerT, scroll, 0, c * CARD_PITCH, 136, CARD_H));
                selectable(comps, want, rect, card, 0, 0, 136, CARD_H, "Select");
                add(comps, want, itemSlot(buttonT, card, 0, 1, 36, 32, null));
                // name wraps to 2 lines (long names used to clip into the scrollbar); tier + rate share the 3rd line
                InterfaceDefinition cardName = text(textT, card, 41, 1, 94, 22, TEXT_FONT, ORANGE, "");
                cardName.yTextAlignment = 0;
                cardName.lineHeight = 11;
                add(comps, want, cardName);
                add(comps, want, text(textT, card, 41, 23, 46, 11, TEXT_FONT, TEXT, ""));
                InterfaceDefinition cardRate = text(textT, card, 87, 23, 48, 11, TEXT_FONT, TEXT, "");
                cardRate.xTextAlignment = 2;
                add(comps, want, cardRate);
            }

            // ---- centre: the Transmutation Altar ----
            int centre = BuildWorkbenchInterface.panel(comps, want, layerT, rectT, 0, 188, 62, 160, 243);
            fix(want, comps, centre);
            InterfaceDefinition altar = text(textT, centre, 5, 2, 150, 15, HEADING_FONT, GOLD, "Transmutation Altar");
            altar.xTextAlignment = 1;
            add(comps, want, altar);
            // base socket: label, recessed 44x40 socket (fill, top + left shadow, border), item, name
            InterfaceDefinition baseLabel = text(textT, centre, 5, 17, 150, 12, TEXT_FONT, WHITE, "Base Item");
            baseLabel.xTextAlignment = 1;
            add(comps, want, baseLabel);
            int sx = (160 - 44) / 2;
            add(comps, want, solid(rect, centre, sx, 29, 44, 40, SLOT_FILL));
            add(comps, want, solid(rect, centre, sx, 29, 44, 2, SLOT_SHADOW));
            add(comps, want, solid(rect, centre, sx, 29, 2, 40, SLOT_SHADOW));
            add(comps, want, outline(rect, centre, sx, 29, 44, 40, SLOT_BORDER, false));
            ids.put("BASE_ITEM", comps.size());
            add(comps, want, itemSlot(buttonT, centre, sx + 4, 33, 36, 32, "Examine"));
            ids.put("BASE_NAME", comps.size());
            // 2 lines under the socket: the name, then (owned)/(missing)
            InterfaceDefinition baseName = text(textT, centre, 3, 69, 154, 24, TEXT_FONT, TEXT, "");
            baseName.xTextAlignment = 1;
            baseName.yTextAlignment = 0;
            baseName.lineHeight = 11;
            add(comps, want, baseName);
            // energy flow: a short gold line between the sockets, with a caption
            add(comps, want, solid(rect, centre, 79, 94, 2, 8, GOLD));
            InterfaceDefinition flow = text(textT, centre, 85, 92, 70, 11, TEXT_FONT, GREY, "transmutes");
            add(comps, want, flow);
            // outcome socket: label, gold-framed 44x40 socket (fill, 2 gold outlines), item, name
            InterfaceDefinition outLabel = text(textT, centre, 5, 103, 150, 12, TEXT_FONT, ORANGE, "Outcome");
            outLabel.xTextAlignment = 1;
            add(comps, want, outLabel);
            add(comps, want, solid(rect, centre, sx, 115, 44, 40, OUTCOME_FILL));
            add(comps, want, outline(rect, centre, sx, 115, 44, 40, ACTIVE_TRIM, false));
            add(comps, want, outline(rect, centre, sx + 1, 116, 42, 38, 0x6b5330, false));
            ids.put("OUTCOME_ITEM", comps.size());
            add(comps, want, itemSlot(buttonT, centre, sx + 4, 119, 36, 32, "Examine"));
            ids.put("OUTCOME_NAME", comps.size());
            InterfaceDefinition outName = text(textT, centre, 3, 156, 154, 22, TEXT_FONT, GOLD, "");
            outName.xTextAlignment = 1;
            outName.yTextAlignment = 0;
            outName.lineHeight = 11;
            add(comps, want, outName);
            // stat changes
            add(comps, want, text(textT, centre, 6, 179, 148, 13, TEXT_FONT, GOLD, "Stat changes"));
            ids.put("STAT_DIFF", comps.size());
            InterfaceDefinition diff = text(textT, centre, 6, 192, 148, 49, TEXT_FONT, TEXT, "");
            diff.yTextAlignment = 0;
            diff.lineHeight = 11;
            add(comps, want, diff);

            // ---- right: reagents, success gauge, forge ----
            int right = BuildWorkbenchInterface.panel(comps, want, layerT, rectT, 0, 352, 62, 136, 243);
            fix(want, comps, right);
            add(comps, want, text(textT, right, 6, 3, 124, 14, TEXT_FONT, GOLD, "Required Reagents"));
            // 6 reagents, 3 per row: fill, border, item (Examine), owned / required
            ids.put("FIRST_REAGENT", comps.size());
            for (int k = 0; k < REAGENTS; k++) {
                int cx = 2 + (k % 3) * 44, y = 19 + (k / 3) * 46;
                add(comps, want, solid(rect, right, cx + 4, y, 36, 32, SLOT_FILL));
                add(comps, want, outline(rect, right, cx + 4, y, 36, 32, SLOT_BORDER, false));
                add(comps, want, itemSlot(buttonT, right, cx + 4, y, 36, 32, "Examine"));
                InterfaceDefinition count = text(textT, right, cx, y + 33, 44, 11, TEXT_FONT, TEXT, "");
                count.xTextAlignment = 1;
                add(comps, want, count);
            }
            // success rate line + 20-segment gauge (red -> amber -> green) over a dark track
            ids.put("RATE", comps.size());
            add(comps, want, text(textT, right, 6, 112, 124, 13, TEXT_FONT, WHITE, ""));
            add(comps, want, solid(rect, right, 6, 127, 123, 10, GAUGE_BACK));
            ids.put("FIRST_SEGMENT", comps.size());
            for (int s = 0; s < SEGMENTS; s++) {
                InterfaceDefinition seg = solid(rect, right, 8 + s * 6, 129, 5, 6, segmentColour(s));
                seg.isHidden = true;
                add(comps, want, seg);
            }
            ids.put("BREAKDOWN", comps.size());
            InterfaceDefinition breakdown = text(textT, right, 6, 139, 124, 24, TEXT_FONT, GREY, "");
            breakdown.yTextAlignment = 0;
            breakdown.lineHeight = 11;
            add(comps, want, breakdown);
            ids.put("FAILURE", comps.size());
            InterfaceDefinition failure = text(textT, right, 6, 165, 124, 38, TEXT_FONT, ORANGE, "");
            failure.yTextAlignment = 0;
            failure.lineHeight = 11;
            add(comps, want, failure);
            ids.put("FORGE", comps.size());
            InterfaceDefinition forge = copy(buttonT);
            place(forge, right, 4, 207, 128, 31);
            forge.clickMask = 2;
            forge.actions = new String[]{"Forge Upgrade"};
            forge.onMouseOverListener = new Object[]{44, -2147483645, 24470};
            forge.onMouseLeaveListener = new Object[]{44, -2147483645, 4525};
            forge.hasListener = true;
            add(comps, want, forge);
            InterfaceDefinition forgeLabel = copy(labelT);
            place(forgeLabel, right, 5, 209, 126, 27);
            forgeLabel.text = "";
            add(comps, want, forgeLabel);

            ids.put("SEARCH", comps.size());
            searchBar(comps, want, rect, textT, buttonT);

            if (comps.size() != COMPONENTS)
                throw new IllegalStateException("component count " + comps.size() + " != " + COMPONENTS);
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

    /**
     * The search bar (6 comps) at the right of the sub-header row: a recessed stone input box (click = search), its
     * text (placeholder / query), a border, a magnifier, and an inset Clear button (box + label) the server lights up
     * while a search is active.
     */
    static void searchBar(List<byte[]> comps, List<InterfaceDefinition> want, InterfaceDefinition rect, InterfaceDefinition textT,
                          InterfaceDefinition buttonT) {
        int x = 330, y = 43, w = 158, h = 16;
        InterfaceDefinition box = solid(rect, 0, x, y, w, h, 0x25201b);
        box.clickMask = 2;
        box.actions = new String[]{"Search"};
        box.onMouseOverListener = new Object[]{45, -2147483645, 0x2f2924};
        box.onMouseLeaveListener = new Object[]{45, -2147483645, 0x25201b};
        box.hasListener = true;
        add(comps, want, box);
        add(comps, want, text(textT, 0, x + 19, y, w - 64, h, TEXT_FONT, GREY, ""));
        add(comps, want, outline(rect, 0, x, y, w, h, SLOT_BORDER, false));
        InterfaceDefinition glass = itemSlot(buttonT, 0, x + 3, y + 1, 14, 14, null);
        glass.borderType = 0;
        glass.shadowColor = 0;
        glass.spriteId = 1043;
        add(comps, want, glass);
        InterfaceDefinition clear = solid(rect, 0, x + w - 42, y + 2, 40, h - 4, BOX);
        clear.clickMask = 2;
        clear.actions = new String[]{"Clear search"};
        clear.onMouseOverListener = new Object[]{45, -2147483645, BOX_HOVER};
        clear.onMouseLeaveListener = new Object[]{45, -2147483645, BOX};
        clear.hasListener = true;
        add(comps, want, clear);
        InterfaceDefinition clearLabel = text(textT, 0, x + w - 42, y + 2, 40, h - 4, TEXT_FONT, GREY, "Clear");
        clearLabel.xTextAlignment = 1;
        add(comps, want, clearLabel);
    }

    /** BuildWorkbenchInterface.panel places its comps under group 1153: re-point the 5 it just added at this group. */
    static void fix(List<InterfaceDefinition> want, List<byte[]> comps, int first) {
        for (int c = first; c < first + 5; c++) {
            InterfaceDefinition d = want.get(c);
            int parent = d.parentId & 0xffff;
            d.parentId = (GROUP << 16) | parent;
            comps.set(c, BuildBondsInterface.encodeIf3(d));
        }
    }

    static int segmentColour(int s) {
        if (s < 7)
            return 0xc83c28;
        if (s < 13)
            return 0xe0a030;
        return 0x3cc83c;
    }

    /** A graphic component the server fills with an item (if_setobject), optionally with an op. */
    static InterfaceDefinition itemSlot(InterfaceDefinition buttonT, int parent, int x, int y, int w, int h, String op) {
        InterfaceDefinition s = copy(buttonT);
        place(s, parent, x, y, w, h);
        s.spriteId = -1;
        s.spriteTiling = false;
        s.onMouseOverListener = null;
        s.onMouseLeaveListener = null;
        s.hasListener = false;
        s.borderType = 1;
        s.shadowColor = 0x302020;
        if (op != null) {
            s.clickMask = 2;
            s.actions = new String[]{op};
        }
        return s;
    }

    static void selectable(List<byte[]> comps, List<InterfaceDefinition> want, InterfaceDefinition rect, int parent,
                           int x, int y, int w, int h, String action) {
        InterfaceDefinition b = solid(rect, parent, x, y, w, h, BOX);
        b.clickMask = 2;
        b.actions = new String[]{action};
        b.onMouseOverListener = new Object[]{45, -2147483645, BOX_HOVER};
        b.onMouseLeaveListener = new Object[]{45, -2147483645, BOX};
        b.hasListener = true;
        add(comps, want, b);
        add(comps, want, outline(rect, parent, x, y, w, h, BORDER, false));
        // selected (shown by the server, +2/+3): a recessed dark fill inside the border + a soft gold trim. These were
        // two neon gold 1px outlines (rejected 2026-10-15: "harsh yellow wireframe boxes").
        InterfaceDefinition recess = solid(rect, parent, x + 1, y + 1, w - 2, h - 2, ACTIVE_FILL);
        recess.isHidden = true;
        add(comps, want, recess);
        add(comps, want, outline(rect, parent, x, y, w, h, ACTIVE_TRIM, true));
    }

    /**
     * Category tab, same 4-comp layout as selectable() so RelicForge's ids stay put: +0 real OSRS bezel button (sprite
     * 1002:169, hover script 44), +1 a permanently hidden spacer, +2 dark recess (opacity 110) and +3 soft gold trim,
     * both shown on the active tab.
     */
    static void bezelTab(List<byte[]> comps, List<InterfaceDefinition> want, InterfaceDefinition rect, InterfaceDefinition buttonT,
                         int parent, int x, int y, int w, int h, String action) {
        InterfaceDefinition b = copy(buttonT);
        place(b, parent, x, y, w, h);
        b.clickMask = 2;
        b.actions = new String[]{action};
        b.onMouseOverListener = new Object[]{44, -2147483645, 24470};
        b.onMouseLeaveListener = new Object[]{44, -2147483645, 4525};
        b.hasListener = true;
        add(comps, want, b);
        InterfaceDefinition spacer = solid(rect, parent, x, y, 1, 1, 0);
        spacer.isHidden = true;
        add(comps, want, spacer);
        InterfaceDefinition recess = solid(rect, parent, x + 2, y + 2, w - 4, h - 4, 0x120f0c);
        recess.opacity = 70; // deeper pressed-stone recess
        recess.isHidden = true;
        add(comps, want, recess);
        add(comps, want, outline(rect, parent, x + 1, y + 1, w - 2, h - 2, ACTIVE_TRIM, true));
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

    static void add(List<byte[]> comps, List<InterfaceDefinition> want, InterfaceDefinition d) {
        comps.add(BuildBondsInterface.encodeIf3(d));
        want.add(d);
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
    }

    static InterfaceDefinition copy(InterfaceDefinition s) {
        return BuildBondsInterface.copy(s);
    }
}

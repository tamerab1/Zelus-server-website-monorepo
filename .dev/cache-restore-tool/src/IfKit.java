import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.InterfaceDefinition;
import net.runelite.cache.definitions.loaders.InterfaceLoader;
import net.runelite.cache.fs.*;

import java.lang.reflect.Field;
import java.util.*;

// Shared component kit for the 2026-10-15 builders (BuildReferralInterface 1165, BuildDonatorDealsInterface 1166,
// BuildCapeForgeInterface 1167). Same templates and warm-stone theme as BuildLotteryInterface: the 1002 frame
// (root / background / close / title), 1002:153 layer, 159-161 frame rects, 144 text, 169/170 bezel button + label,
// 84:4 model. Unlike the older builders the group is an instance field, so the helpers aren't copied per builder.
public class IfKit {
    static final int SRC = 1002;
    static final int GOLD = 0xffc35a, ORANGE = 0xff981f, TEXT = 0xd8d0c0, GREY = 0x9a9a9a, WHITE = 0xffffff,
            GREEN = 0x00ff00, RED = 0xff3333, CYAN = 0x4fe0e8;
    static final int HEADING_FONT = 496, BIG_FONT = 495, TEXT_FONT = 494, TITLE_Y = 14;
    static final int PANEL_FILL = 0x36302a, BORDER = 0x5a4d3a, ROW_A = 0x2e2924, ROW_B = 0x332c26;
    static final int WELL = 0x241f1a, SHADOW = 0x15120f, TRIM = 0x8a6a3a, BOX = 0x38322a, BOX_HOVER = 0x453d33;

    final int group;
    final Store store;
    final Storage storage;
    final Index index;
    final Archive srcArchive;
    final List<byte[]> comps = new ArrayList<>();
    final List<InterfaceDefinition> want = new ArrayList<>();
    final Map<String, Integer> ids = new LinkedHashMap<>();
    final InterfaceDefinition layerT, textT, buttonT, labelT, rect, modelT;
    final InterfaceDefinition[] rectT;

    /** Loads the templates and adds comps 0-3 (root, background, close, title moved up to TITLE_Y). */
    /** allowExisting: verify, or rebuild in place; a plain apply refuses to overwrite an existing group. */
    IfKit(int group, Store store, boolean allowExisting) throws Exception {
        this.group = group;
        this.store = store;
        this.storage = store.getStorage();
        this.index = store.getIndex(IndexType.INTERFACES);
        if (index.getArchive(group) != null && !allowExisting)
            throw new IllegalStateException("interface " + group + " already exists (use rebuild)");
        srcArchive = index.getArchive(SRC);
        ArchiveFiles src = srcArchive.getFiles(storage.loadArchive(srcArchive));
        InterfaceLoader loader = new InterfaceLoader();
        for (int c = 0; c <= 3; c++) {
            InterfaceDefinition d = loader.load((group << 16) | c, src.findFile(c).getContents());
            if (d.parentId != -1)
                d.parentId = (group << 16) | (d.parentId & 0xffff);
            if (c == 3)
                d.originalY = TITLE_Y;
            add(d);
        }
        layerT = loader.load(0, src.findFile(153).getContents());
        rectT = new InterfaceDefinition[]{loader.load(0, src.findFile(159).getContents()),
                loader.load(0, src.findFile(160).getContents()), loader.load(0, src.findFile(161).getContents())};
        textT = loader.load(0, src.findFile(144).getContents());
        buttonT = loader.load(0, src.findFile(169).getContents());
        labelT = loader.load(0, src.findFile(170).getContents());
        rect = rectT[1];
        Archive a84 = index.getArchive(84);
        modelT = loader.load(0, a84.getFiles(storage.loadArchive(a84)).findFile(4).getContents());
    }

    /** Records the id the next component will get under this name. */
    int mark(String name) {
        ids.put(name, comps.size());
        return comps.size();
    }

    int next() {
        return comps.size();
    }

    int add(InterfaceDefinition d) {
        comps.add(BuildBondsInterface.encodeIf3(d));
        want.add(d);
        return comps.size() - 1;
    }

    /** Stone panel: layer + fill + the 3 frame rects. Returns the layer id. */
    int panel(int parent, int x, int y, int w, int h) {
        int id = add(layer(parent, x, y, w, h));
        add(solid(id, 0, 0, w, h, PANEL_FILL));
        for (int k = 0; k < 3; k++) {
            InterfaceDefinition r = copy(rectT[k]);
            if (k == 2)
                r.textColor = PANEL_FILL;
            place(r, id, k, k, w - 2 * k, h - 2 * k);
            add(r);
        }
        return id;
    }

    /** Recessed inset: well fill, top + left inner shadow, bronze trim. */
    void inset(int parent, int x, int y, int w, int h) {
        add(solid(parent, x, y, w, h, WELL));
        add(solid(parent, x, y, w, 1, SHADOW));
        add(solid(parent, x, y, 1, h, SHADOW));
        add(outline(parent, x, y, w, h, TRIM, false));
    }

    /** 2 comps: OSRS bezel button (click + hover) and its label (server fills the label). Returns the button id. */
    int bezel(int parent, int x, int y, int w, int h, String action) {
        InterfaceDefinition b = copy(buttonT);
        place(b, parent, x, y, w, h);
        b.clickMask = 2;
        b.actions = new String[]{action};
        b.onMouseOverListener = new Object[]{44, -2147483645, 24470};
        b.onMouseLeaveListener = new Object[]{44, -2147483645, 4525};
        b.hasListener = true;
        int id = add(b);
        InterfaceDefinition l = copy(labelT);
        place(l, parent, x + 1, y + 2, w - 2, h - 4);
        l.text = "";
        add(l);
        return id;
    }

    /**
     * 4 comps, a real OSRS bezel tab (see BuildMarketInterface.bezelTab): button (click + hover), +1 hidden dark recess
     * (opacity 110), +2 hidden soft gold trim, +3 label. The server shows +1/+2 on the active tab. Returns the button id.
     */
    int bezelTab(int parent, int x, int y, int w, int h, String action, boolean bold, String label) {
        InterfaceDefinition b = copy(buttonT);
        place(b, parent, x, y, w, h);
        b.clickMask = 2;
        b.actions = new String[]{action};
        b.onMouseOverListener = new Object[]{44, -2147483645, 24470};
        b.onMouseLeaveListener = new Object[]{44, -2147483645, 4525};
        b.hasListener = true;
        int id = add(b);
        InterfaceDefinition recess = solid(parent, x + 2, y + 2, w - 4, h - 4, 0x120f0c);
        recess.opacity = 70; // pressed stone
        recess.isHidden = true;
        add(recess);
        add(outline(parent, x + 1, y + 1, w - 2, h - 2, 0x4a3a24, true));
        add(centred(text(parent, x, y, w, h, bold ? HEADING_FONT : TEXT_FONT, TEXT, label)));
        return id;
    }

    /**
     * 6 comps, OSRS tab / selectable card: raised stone box (click + hover), border, then the hidden "selected" parts
     * (+2 recessed fill, +3 top shadow, +4 left shadow, +5 soft gold trim) the server toggles. Returns the box id.
     */
    int selectable(int parent, int x, int y, int w, int h, String action) {
        InterfaceDefinition b = solid(parent, x, y, w, h, BOX);
        b.clickMask = 2;
        b.actions = new String[]{action};
        b.onMouseOverListener = new Object[]{45, -2147483645, BOX_HOVER};
        b.onMouseLeaveListener = new Object[]{45, -2147483645, BOX};
        b.hasListener = true;
        int id = add(b);
        add(outline(parent, x, y, w, h, BORDER, false));
        InterfaceDefinition active = solid(parent, x + 1, y + 1, w - 2, h - 2, WELL);
        active.isHidden = true;
        add(active);
        InterfaceDefinition top = solid(parent, x + 1, y + 1, w - 2, 1, SHADOW);
        top.isHidden = true;
        add(top);
        InterfaceDefinition left = solid(parent, x + 1, y + 1, 1, h - 2, SHADOW);
        left.isHidden = true;
        add(left);
        add(outline(parent, x, y, w, h, 0x4a3a24, true));
        return id;
    }

    /** OSRS stone button pieces (2026-10-20, Trading Post redesign): rivet corners TL/TR/BL/BR, edges L/T/R/B. */
    static final int[] STONE = {913, 914, 915, 916, 917, 918, 919, 920}, STONE_PRESSED = {929, 930, 931, 932, 933, 934, 935, 936};
    static final int STONE_FILL = 0x524b40, STONE_HOVER = 0x5f5749, STONE_PRESSED_FILL = 0x3b352d, STONE_LABEL = 0xff981f;
    /** Comps per stoneButton: fill, 8 pieces, pressed layer + its fill + 8 pieces, label. */
    static final int STONE_COMPS = 20, STONE_PRESSED_LAYER = 9, STONE_LABEL_OFFSET = 19;

    /** A sprite graphic (no ops), tiled or stretched. */
    InterfaceDefinition sprite(int parent, int x, int y, int w, int h, int spriteId, boolean tiled) {
        InterfaceDefinition s = copy(buttonT);
        place(s, parent, x, y, w, h);
        s.spriteId = spriteId;
        s.spriteTiling = tiled;
        s.borderType = 0;
        s.shadowColor = 0;
        return s;
    }

    /** The 8 border pieces of a stone button around (x, y, w, h). */
    void stoneFrame(int parent, int x, int y, int w, int h, int[] pieces) {
        add(sprite(parent, x, y, 9, 9, pieces[0], false));
        add(sprite(parent, x + w - 9, y, 9, 9, pieces[1], false));
        add(sprite(parent, x, y + h - 9, 9, 9, pieces[2], false));
        add(sprite(parent, x + w - 9, y + h - 9, 9, 9, pieces[3], false));
        // the edges are 3px lines on a 9x9 canvas (right / bottom at offset 6): the client tiles by the canvas, so each
        // edge component is 9px thick and sits flush with its side
        add(sprite(parent, x, y + 9, 9, h - 18, pieces[4], true));
        add(sprite(parent, x + 9, y, w - 18, 9, pieces[5], true));
        add(sprite(parent, x + w - 9, y + 9, 9, h - 18, pieces[6], true));
        add(sprite(parent, x + 9, y + h - 9, w - 18, 9, pieces[7], true));
    }

    /**
     * {@link #STONE_COMPS} comps: the OSRS stone button (the Trading Post / settings button with rivet corners, sprites
     * 913-920): fill (click; hover brightens it), the 8 border pieces, a hidden pressed layer (+9: darker fill and the
     * 929-936 pieces, shown by the server for an active tab / toggle), and the label (+19). Returns the fill's id.
     */
    int stoneButton(int parent, int x, int y, int w, int h, String action, int font, String label) {
        return stoneButton(parent, x, y, w, h, action, font, label, STONE_HOVER);
    }

    /** {@link #stoneButton} with its own hover colour (e.g. a warm gold lift on a primary action). */
    int stoneButton(int parent, int x, int y, int w, int h, String action, int font, String label, int hover) {
        InterfaceDefinition fill = solid(parent, x, y, w, h, STONE_FILL);
        if (action != null) {
            fill.clickMask = 2;
            fill.actions = new String[]{action};
            fill.onMouseOverListener = new Object[]{45, -2147483645, hover};
            fill.onMouseLeaveListener = new Object[]{45, -2147483645, STONE_FILL};
            fill.hasListener = true;
        }
        int id = add(fill);
        stoneFrame(parent, x, y, w, h, STONE);
        int pressed = add(hidden(layer(parent, x, y, w, h)));
        add(solid(pressed, 0, 0, w, h, STONE_PRESSED_FILL));
        stoneFrame(pressed, 0, 0, w, h, STONE_PRESSED);
        add(centred(text(parent, x + 3, y + 1, w - 6, h - 2, font, STONE_LABEL, label)));
        return id;
    }

    /** A graphic component the server fills with an item (if_setobject), optionally with an op. */
    InterfaceDefinition itemSlot(int parent, int x, int y, int w, int h, String op) {
        InterfaceDefinition s = copy(buttonT);
        place(s, parent, x, y, w, h);
        s.spriteId = -1;
        s.spriteTiling = false;
        s.borderType = 1;
        s.shadowColor = 0x302020;
        if (op != null) {
            s.clickMask = 2;
            s.actions = new String[]{op};
        }
        return s;
    }

    /** 3 comps: recessed 36x32 slot (well + trim) and the item graphic on top. Returns the item comp id. */
    int slot(int parent, int x, int y, String op) {
        add(solid(parent, x, y, 36, 32, 0x2a2520));
        add(outline(parent, x, y, 36, 32, BORDER, false));
        return add(itemSlot(parent, x, y, 36, 32, op));
    }

    InterfaceDefinition model(int parent, int x, int y, int w, int h) {
        InterfaceDefinition m = copy(modelT);
        m.contentType = 0; // 84:4 is contentType 328 (always the local player) -- must be cleared
        place(m, parent, x, y, w, h);
        m.modelId = -1;
        return m;
    }

    InterfaceDefinition layer(int parent, int x, int y, int w, int h) {
        InterfaceDefinition d = copy(layerT);
        place(d, parent, x, y, w, h);
        d.scrollWidth = 0;
        d.scrollHeight = 0;
        return d;
    }

    InterfaceDefinition solid(int parent, int x, int y, int w, int h, int colour) {
        InterfaceDefinition d = copy(rect);
        place(d, parent, x, y, w, h);
        d.filled = true;
        d.textColor = colour;
        d.opacity = 0;
        return d;
    }

    InterfaceDefinition outline(int parent, int x, int y, int w, int h, int colour, boolean hidden) {
        InterfaceDefinition o = copy(rect);
        place(o, parent, x, y, w, h);
        o.filled = false;
        o.textColor = colour;
        o.isHidden = hidden;
        return o;
    }

    InterfaceDefinition text(int parent, int x, int y, int w, int h, int font, int colour, String s) {
        InterfaceDefinition d = copy(textT);
        place(d, parent, x, y, w, h);
        d.fontId = font;
        d.textColor = colour;
        d.text = s;
        d.xTextAlignment = 0;
        d.yTextAlignment = 1;
        d.textShadowed = true;
        return d;
    }

    /** Multi-line text block, top aligned. */
    InterfaceDefinition block(int parent, int x, int y, int w, int h, int colour, int lineHeight, boolean centre) {
        InterfaceDefinition d = text(parent, x, y, w, h, TEXT_FONT, colour, "");
        d.yTextAlignment = 0;
        d.lineHeight = lineHeight;
        if (centre)
            d.xTextAlignment = 1;
        return d;
    }

    static InterfaceDefinition centred(InterfaceDefinition d) {
        d.xTextAlignment = 1;
        return d;
    }

    static InterfaceDefinition right(InterfaceDefinition d) {
        d.xTextAlignment = 2;
        return d;
    }

    static InterfaceDefinition hidden(InterfaceDefinition d) {
        d.isHidden = true;
        return d;
    }

    void place(InterfaceDefinition d, int parent, int x, int y, int w, int h) {
        d.parentId = (group << 16) | parent;
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

    /** Prints the CONST lines, round-trips every component through the decoder, then writes when applying. */
    void finish(boolean apply) throws Exception {
        for (Map.Entry<String, Integer> e : ids.entrySet())
            System.out.println("CONST " + e.getKey() + " = " + e.getValue());
        InterfaceLoader loader = new InterfaceLoader();
        for (int c = 0; c < comps.size(); c++) {
            InterfaceDefinition got = loader.load((group << 16) | c, comps.get(c));
            InterfaceDefinition exp = want.get(c);
            exp.id = (group << 16) | c;
            for (Field f : InterfaceDefinition.class.getFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                if (!Objects.deepEquals(f.get(got), f.get(exp)))
                    throw new IllegalStateException("component " + c + ": " + f.getName() + " " + f.get(got) + " != " + f.get(exp));
            }
        }
        if (!apply) {
            System.out.println("VERIFY OK -- interface " + group + ", " + comps.size() + " components, nothing written.");
            return;
        }
        BuildSpinnerInterface.write(storage, index, srcArchive, comps, group);
        System.out.println("APPLY complete -- interface " + group + " with " + comps.size() + " components.");
    }
}

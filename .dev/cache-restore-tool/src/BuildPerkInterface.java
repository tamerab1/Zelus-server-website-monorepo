import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.InterfaceDefinition;
import net.runelite.cache.definitions.loaders.InterfaceLoader;
import net.runelite.cache.fs.*;
import net.runelite.cache.index.FileData;
import net.runelite.cache.index.IndexData;

import java.io.File;
import java.lang.reflect.Field;
import java.util.*;

// Builds interface group 1151, the unified Zelus Perks screen (PerkHub.java, server) that replaces the four old perk
// screens 1094-1097. Pit hub style: Wilderness Forge frame (1002:0-3) + sub-header; left column = Browse tabs (All /
// Combat / Skilling / Utility / Owned / Sets), Active perks (5 recessed slots, locks drawn by the server) and Loadouts
// (5 rows: Load + Clear, and a Save button); right column = a scrolling list of 52 perk rows (icon, name, one-line
// effect, status badge) and a Selected-perk detail panel with two action buttons. Selection outlines start hidden.
// Encoder/verification shared with BuildBondsInterface. Component ids are mirrored in PerkHub.java (printed below).
// Usage: verify|apply|rebuild <cachePath>
public class BuildPerkInterface {
    static final int GROUP = 1151, SRC = 1002;
    static final int GOLD = 0xffc35a, TEXT = 0xd8d0c0, GREY = 0x9a9a9a;
    static final int HEADING_FONT = 496, TEXT_FONT = 494;
    static final int TITLE_Y = 14, SUB_Y = 45;
    /** Raised stone (the Trading Post's stone button colours) and its subtle hover lift. */
    static final int BOX = 0x4b443a, BOX_HOVER = 0x575045, BORDER = 0x2b2620;
    /** The loadout delete button: red stone. */
    static final int DANGER = 0x5c2a22, DANGER_HOVER = 0x74352b;
    /** Warm stone fill behind every panel (instead of the bare dark frame background). */
    static final int PANEL_FILL = 0x36302a;
    /** Search bar: recessed stone inset, its hover, and the magnifier sprite. */
    static final int SEARCH_FILL = 0x25201b, SEARCH_HOVER = 0x2f2924, MAGNIFIER = 1043;
    static final int SLOT_FILL = 0x2a2520, SLOT_SHADOW = 0x1a1612, SLOT_BORDER = 0x5a4d3a;
    static final int TABS = 6, SLOTS = 5, LOADOUTS = 5, ROWS = 52;
    static final int ROW_H = 36, ROW_PITCH = 38;
    static final int COMPONENTS = 865;
    static final int COMBOS = 4, COMBO_PITCH = 86, CARD_FILL = 0x433b31;
    /** Padlock sprite (29x22) from the old perk home's locked slots. */
    static final int LOCK_SPRITE = 1342;

    public static void main(String[] args) throws Exception {
        boolean rebuild = args[0].equals("rebuild");
        boolean apply = args[0].equals("apply") || rebuild;
        try (Store store = new Store(new File(args[1]))) {
            store.load();
            Storage storage = store.getStorage();
            Index index = store.getIndex(IndexType.INTERFACES);
            if (index.getArchive(GROUP) != null && !rebuild)
                throw new IllegalStateException("interface " + GROUP + " already exists (use rebuild)");
            Archive srcArchive = index.getArchive(SRC);
            ArchiveFiles src = srcArchive.getFiles(storage.loadArchive(srcArchive));
            InterfaceLoader loader = new InterfaceLoader();

            List<byte[]> comps = new ArrayList<>();
            List<InterfaceDefinition> want = new ArrayList<>();
            Map<String, Integer> ids = new LinkedHashMap<>();
            for (int c = 0; c <= 2; c++) {
                byte[] b = src.findFile(c).getContents();
                comps.add(b);
                want.add(loader.load((GROUP << 16) | c, b));
            }
            InterfaceDefinition title = loader.load((GROUP << 16) | 3, src.findFile(3).getContents());
            title.originalY = TITLE_Y;
            add(comps, want, title);
            InterfaceDefinition layerT = loader.load(0, src.findFile(153).getContents());
            InterfaceDefinition[] rectT = {loader.load(0, src.findFile(159).getContents()),
                    loader.load(0, src.findFile(160).getContents()), loader.load(0, src.findFile(161).getContents())};
            InterfaceDefinition textT = loader.load(0, src.findFile(144).getContents());
            InterfaceDefinition buttonT = loader.load(0, src.findFile(169).getContents());
            InterfaceDefinition labelT = loader.load(0, src.findFile(170).getContents());
            InterfaceDefinition rect = rectT[1];

            // 4: sub-header (perk points, tree level, active slots)
            InterfaceDefinition sub = text(textT, 0, 26, SUB_Y, 300, 13, TEXT_FONT, TEXT, "");
            sub.xTextAlignment = 0;
            add(comps, want, sub);

            // ---- Browse tabs ----
            int browse = panel(comps, want, layerT, rectT, 0, 24, 62, 196, 60);
            ids.put("PANEL_BROWSE", browse);
            ids.put("FIRST_TAB", comps.size());
            for (int t = 0; t < TABS; t++) {
                int x = 6 + (t % 3) * 62, y = t < 3 ? 5 : 31;
                selectable(comps, want, rect, browse, x, y, 58, 23, "View");
                InterfaceDefinition l = text(textT, browse, x, y, 58, 23, TEXT_FONT, TEXT, "");
                l.xTextAlignment = 1;
                add(comps, want, l);
            }

            // ---- Active perks: 5 recessed slots x (fill, shadow top, shadow left, border, icon, level, lock) ----
            int active = panel(comps, want, layerT, rectT, 0, 24, 126, 196, 62);
            ids.put("PANEL_ACTIVE", active);
            add(comps, want, text(textT, active, 8, 3, 120, 14, TEXT_FONT, GOLD, "Active perks"));
            ids.put("ACTIVE_COUNT", comps.size());
            InterfaceDefinition count = text(textT, active, 100, 3, 88, 14, TEXT_FONT, GREY, "");
            count.xTextAlignment = 2;
            add(comps, want, count);
            ids.put("FIRST_SLOT", comps.size());
            for (int k = 0; k < SLOTS; k++) {
                int x = 6 + k * 37, y = 20;
                add(comps, want, solid(rect, active, x, y, 34, 34, SLOT_FILL));
                add(comps, want, solid(rect, active, x, y, 34, 2, SLOT_SHADOW));
                add(comps, want, solid(rect, active, x, y, 2, 34, SLOT_SHADOW));
                add(comps, want, outlineRect(rect, active, x, y, 34, 34, SLOT_BORDER, false));
                InterfaceDefinition icon = sprite(buttonT, active, x + 1, y + 1, 32, 32);
                icon.clickMask = 2;
                icon.actions = new String[]{"Unequip"};
                add(comps, want, icon);
                // level ("Lv 3") or, on a locked slot, the unlock level ("Lv 50") under the padlock
                InterfaceDefinition lvl = text(textT, active, x, y + 21, 34, 12, TEXT_FONT, 0xffff00, "");
                lvl.xTextAlignment = 1;
                add(comps, want, lvl);
                // the padlock the old perk home (1094:56) drew over locked slots, hidden until the server shows it
                InterfaceDefinition lock = sprite(buttonT, active, x + 2, y + 1, 29, 22);
                lock.spriteId = LOCK_SPRITE;
                lock.isHidden = true;
                add(comps, want, lock);
            }

            // ---- Loadouts: Save button + 5 rows x (load box, label, clear box, "x") ----
            int loadouts = panel(comps, want, layerT, rectT, 0, 24, 192, 196, 113);
            ids.put("PANEL_LOADOUTS", loadouts);
            add(comps, want, text(textT, loadouts, 8, 3, 100, 14, TEXT_FONT, GOLD, "Loadouts"));
            ids.put("SAVE", comps.size());
            button(comps, want, rect, textT, loadouts, 118, 3, 72, 15, "Save current", "Save");
            ids.put("FIRST_LOADOUT", comps.size());
            for (int r = 0; r < LOADOUTS; r++) {
                int y = 21 + r * 18;
                button(comps, want, rect, textT, loadouts, 6, y, 152, 16, "Preview", "");
                button(comps, want, rect, textT, loadouts, 160, y, 30, 16, "Clear", "x");
            }

            // ---- Perk list: scroll container + scrollbar + 52 rows ----
            int list = panel(comps, want, layerT, rectT, 0, 226, 62, 262, 128);
            ids.put("PANEL_LIST", list);
            ids.put("SCROLL", comps.size());
            ids.put("SCROLLBAR", comps.size() + 1);
            add(comps, want, layer(layerT, list, 4, 4, 236, 120));
            add(comps, want, layer(layerT, list, 242, 4, 16, 120));
            int scroll = ids.get("SCROLL");
            ids.put("FIRST_ROW", comps.size());
            for (int r = 0; r < ROWS; r++) {
                int row = comps.size();
                add(comps, want, layer(layerT, scroll, 0, r * ROW_PITCH, 236, ROW_H));
                selectable(comps, want, rect, row, 0, 0, 236, ROW_H, "Select");
                add(comps, want, sprite(buttonT, row, 2, 2, 32, 32));
                add(comps, want, text(textT, row, 40, 2, 122, 15, TEXT_FONT, GOLD, ""));
                add(comps, want, text(textT, row, 40, 18, 194, 16, TEXT_FONT, TEXT, ""));
                // the status / cost tag: an inset stone pill (gold text from the server)
                InterfaceDefinition badge = solid(rect, row, 166, 3, 66, 14, 0x221c16);
                badge.clickMask = 2;
                badge.actions = new String[]{"Buy"};
                hover(badge, 0x221c16, 0x2e271f);
                add(comps, want, badge);
                InterfaceDefinition badgeText = text(textT, row, 166, 3, 66, 14, TEXT_FONT, TEXT, "");
                badgeText.xTextAlignment = 1;
                add(comps, want, badgeText);
            }

            // ---- Selected perk detail ----
            int detail = panel(comps, want, layerT, rectT, 0, 226, 194, 262, 111);
            ids.put("PANEL_DETAIL", detail);
            ids.put("DETAIL_ICON_FILL", comps.size());
            ids.put("DETAIL_ICON_BORDER", comps.size() + 1);
            ids.put("DETAIL_ICON", comps.size() + 2);
            add(comps, want, solid(rect, detail, 6, 6, 34, 34, SLOT_FILL));
            add(comps, want, outlineRect(rect, detail, 6, 6, 34, 34, SLOT_BORDER, false));
            add(comps, want, sprite(buttonT, detail, 7, 7, 32, 32));
            ids.put("DETAIL_NAME", comps.size());
            ids.put("DETAIL_LEVEL", comps.size() + 1);
            ids.put("DETAIL_NOW", comps.size() + 2);
            ids.put("DETAIL_NEXT", comps.size() + 3);
            add(comps, want, text(textT, detail, 46, 5, 210, 16, HEADING_FONT, GOLD, ""));
            add(comps, want, text(textT, detail, 46, 22, 210, 14, TEXT_FONT, TEXT, ""));
            InterfaceDefinition now = text(textT, detail, 6, 37, 250, 34, TEXT_FONT, TEXT, "");
            now.yTextAlignment = 0;
            now.lineHeight = 11;
            add(comps, want, now);
            add(comps, want, text(textT, detail, 6, 71, 250, 13, TEXT_FONT, GREY, ""));
            // action buttons: the standard OSRS button sprite (1002:169) with its hover script, and its label (1002:170)
            ids.put("BUTTON_A", comps.size());
            osrsButton(comps, want, buttonT, labelT, detail, 6, 85, 78, 21);
            ids.put("BUTTON_B", comps.size());
            osrsButton(comps, want, buttonT, labelT, detail, 86, 85, 94, 21);

            // loadout preview (appended so earlier ids stay put): a hidden row of 5 mini perk slots under the heading,
            // each = fill, border, icon, level badge
            ids.put("LOADOUT_ROW", comps.size());
            ids.put("FIRST_MINI", comps.size() + 1);
            InterfaceDefinition mini = layer(layerT, detail, 6, 40, 250, 44);
            mini.isHidden = true;
            add(comps, want, mini);
            int miniRow = ids.get("LOADOUT_ROW");
            for (int k = 0; k < SLOTS; k++) {
                int x = k * 40;
                add(comps, want, solid(rect, miniRow, x, 0, 32, 32, SLOT_FILL));
                add(comps, want, outlineRect(rect, miniRow, x, 0, 32, 32, SLOT_BORDER, false));
                add(comps, want, sprite(buttonT, miniRow, x, 0, 32, 32));
                InterfaceDefinition lv = text(textT, miniRow, x - 4, 32, 40, 12, TEXT_FONT, 0xffff00, "");
                lv.xTextAlignment = 1;
                add(comps, want, lv);
            }

            // third detail button (Save here / Combos), shown by the server only where it applies
            ids.put("BUTTON_C", comps.size());
            osrsButton(comps, want, buttonT, labelT, detail, 182, 85, 74, 21);

            // ---- Set Overview: a page over the whole content area (the server hides the 5 panels underneath while it is
            // open, so nothing below can be clicked). Every section has its own box, top to bottom:
            // title + Back (4-20), status (22-34), effect (36-144), "Required perks" (146-160),
            // 5 perk columns: icon (162-196), name (197-209), state (209-221), buttons (225-246).
            ids.put("SET_VIEW", comps.size());
            InterfaceDefinition view = layer(layerT, 0, 24, 62, 464, 250);
            view.isHidden = true;
            add(comps, want, view);
            int setView = ids.get("SET_VIEW");
            panel(comps, want, layerT, rectT, setView, 0, 0, 464, 250);
            ids.put("SET_TITLE", comps.size());
            add(comps, want, text(textT, setView, 10, 4, 330, 16, HEADING_FONT, 0xff981f, ""));
            ids.put("SET_BACK", comps.size());
            // a real bezel button (same 2 ids as before: box + label); the server sends its label
            osrsButton(comps, want, buttonT, labelT, setView, 334, 2, 122, 20);
            ids.put("SET_STATUS", comps.size());
            add(comps, want, text(textT, setView, 10, 22, 446, 12, TEXT_FONT, TEXT, ""));
            ids.put("SET_EFFECT", comps.size());
            InterfaceDefinition effect = text(textT, setView, 10, 36, 446, 108, TEXT_FONT, TEXT, "");
            effect.yTextAlignment = 0;
            effect.lineHeight = 12;
            add(comps, want, effect);
            ids.put("SET_REQUIRED", comps.size());
            add(comps, want, text(textT, setView, 10, 146, 446, 14, TEXT_FONT, GOLD, ""));
            // 5 required perks, one 92px column each: fill, border, icon, name, owned / missing state
            ids.put("FIRST_SET_PERK", comps.size());
            for (int k = 0; k < SLOTS; k++) {
                int col = 2 + k * 92, x = col + 29, y = 162;
                add(comps, want, solid(rect, setView, x, y, 34, 34, SLOT_FILL));
                add(comps, want, outlineRect(rect, setView, x, y, 34, 34, SLOT_BORDER, false));
                add(comps, want, sprite(buttonT, setView, x + 1, y + 1, 32, 32));
                InterfaceDefinition name = text(textT, setView, col, 197, 92, 12, TEXT_FONT, TEXT, "");
                name.xTextAlignment = 1;
                add(comps, want, name);
                InterfaceDefinition state = text(textT, setView, col, 209, 92, 12, TEXT_FONT, TEXT, "");
                state.xTextAlignment = 1;
                add(comps, want, state);
            }
            ids.put("SET_EQUIP", comps.size());
            osrsButton(comps, want, buttonT, labelT, setView, 62, 225, 160, 21);
            ids.put("SET_COMBOS", comps.size());
            osrsButton(comps, want, buttonT, labelT, setView, 242, 225, 160, 21);

            // ---- Combos Browser: every combination of the set as a card, over the same area as the Set Overview ----
            // header (title + Back to Set), a sub line, then a scrolling list of 4 cards (86px pitch):
            // card = layer, fill, border, title, Equip This Combo button (2), 5 perks x (fill, border, icon, name, state)
            ids.put("COMBO_VIEW", comps.size());
            InterfaceDefinition comboView = layer(layerT, 0, 24, 62, 464, 250);
            comboView.isHidden = true;
            add(comps, want, comboView);
            int combo = ids.get("COMBO_VIEW");
            panel(comps, want, layerT, rectT, combo, 0, 0, 464, 250);
            ids.put("COMBO_TITLE", comps.size());
            add(comps, want, text(textT, combo, 10, 4, 320, 16, HEADING_FONT, 0xff981f, ""));
            ids.put("COMBO_BACK", comps.size());
            osrsButton(comps, want, buttonT, labelT, combo, 334, 2, 122, 20);
            ids.put("COMBO_SUB", comps.size());
            add(comps, want, text(textT, combo, 10, 22, 446, 12, TEXT_FONT, GREY, ""));
            ids.put("COMBO_SCROLL", comps.size());
            ids.put("COMBO_SCROLLBAR", comps.size() + 1);
            add(comps, want, layer(layerT, combo, 6, 38, 434, 206));
            add(comps, want, layer(layerT, combo, 442, 38, 16, 206));
            int comboScroll = ids.get("COMBO_SCROLL");
            ids.put("FIRST_COMBO", comps.size());
            for (int c = 0; c < COMBOS; c++) {
                int card = comps.size();
                add(comps, want, layer(layerT, comboScroll, 0, c * COMBO_PITCH, 434, COMBO_PITCH - 4));
                add(comps, want, solid(rect, card, 0, 0, 434, COMBO_PITCH - 4, CARD_FILL));
                add(comps, want, outlineRect(rect, card, 0, 0, 434, COMBO_PITCH - 4, BORDER, false));
                add(comps, want, text(textT, card, 8, 3, 300, 15, TEXT_FONT, GOLD, ""));
                osrsButton(comps, want, buttonT, labelT, card, 312, 2, 116, 18);
                for (int k = 0; k < SLOTS; k++) {
                    int col = 2 + k * 86, x = col + 26, y = 21;
                    add(comps, want, solid(rect, card, x, y, 34, 34, SLOT_FILL));
                    add(comps, want, outlineRect(rect, card, x, y, 34, 34, SLOT_BORDER, false));
                    add(comps, want, sprite(buttonT, card, x + 1, y + 1, 32, 32));
                    InterfaceDefinition name = text(textT, card, col, 56, 86, 12, TEXT_FONT, TEXT, "");
                    name.xTextAlignment = 1;
                    add(comps, want, name);
                    InterfaceDefinition state = text(textT, card, col, 68, 86, 12, TEXT_FONT, TEXT, "");
                    state.xTextAlignment = 1;
                    add(comps, want, state);
                }
            }

            // a wide, centred detail button for single-action states (Buy / Unlock (Free Lv 5)): the 78px button A
            // clipped those labels
            ids.put("BUTTON_WIDE", comps.size());
            osrsButton(comps, want, buttonT, labelT, detail, 51, 85, 160, 21);

            ids.put("SEARCH", comps.size());
            searchBar(comps, want, rect, textT, buttonT);
            // two full-width bezel buttons for an owned perk: Equip / Unequip and Upgrade
            ids.put("BUTTON_LEFT", comps.size());
            osrsButton(comps, want, buttonT, labelT, detail, 6, 85, 124, 21);
            ids.put("BUTTON_RIGHT", comps.size());
            osrsButton(comps, want, buttonT, labelT, detail, 132, 85, 124, 21);

            if (comps.size() != COMPONENTS)
                throw new IllegalStateException("component count " + comps.size() + " != " + COMPONENTS);
            System.out.println("ids: " + ids);
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

            Archive archive = index.getArchive(GROUP) != null ? index.getArchive(GROUP) : index.addArchive(GROUP);
            archive.setNameHash(-1);
            archive.setCompression(srcArchive.getCompression());
            archive.setRevision(archive.getRevision() + 1);
            FileData[] fd = new FileData[comps.size()];
            ArchiveFiles files = new ArchiveFiles();
            for (int c = 0; c < comps.size(); c++) {
                fd[c] = new FileData();
                fd[c].setId(c);
                FSFile f = new FSFile(c);
                f.setContents(comps.get(c));
                files.addFile(f);
            }
            archive.setFileData(fd);
            byte[] data = files.saveContents();
            Container container = new Container(archive.getCompression(), -1);
            container.compress(data, null);
            storage.store(index.getId(), GROUP, container.data);
            archive.setCrc(container.crc);
            archive.setCompressedSize(container.data.length);
            archive.setDecompressedSize(data.length);
            IndexData indexData = index.toIndexData();
            Container ic = new Container(index.getCompression(), -1);
            ic.compress(indexData.writeIndexData(), null);
            storage.store(255, index.getId(), ic.data);
            index.setCrc(ic.crc);
            System.out.println("APPLY complete -- interface " + GROUP + " with " + comps.size() + " components.");
        }
    }

    /** A framed panel (layer + warm fill + 3 border rects); returns the layer's id. */
    static int panel(List<byte[]> comps, List<InterfaceDefinition> want, InterfaceDefinition layerT, InterfaceDefinition[] rectT,
                     int parent, int x, int y, int w, int h) {
        int id = comps.size();
        add(comps, want, layer(layerT, parent, x, y, w, h));
        InterfaceDefinition fill = copy(rectT[1]);
        place(fill, id, 0, 0, w, h);
        fill.filled = true;
        fill.textColor = PANEL_FILL;
        fill.opacity = 0;
        add(comps, want, fill);
        for (int k = 0; k < 3; k++) {
            InterfaceDefinition r = copy(rectT[k]);
            if (k == 2) // the frame's filled inner rect (1002:161, 312b23) is the visible panel colour: warm stone
                r.textColor = 0x36302a;
            place(r, id, k, k, w - 2 * k, h - 2 * k);
            add(comps, want, r);
        }
        return id;
    }

    /** Selected stone (2026-10-20, no yellow wireframe): a recessed darker fill and a 1px inner shadow. */
    static final int SELECTED_FILL = 0x2b251d, SELECTED_SHADOW = 0x15120f;

    /**
     * 4 components: raised stone fill (click, hover brightens it), border, then the selected state the server shows
     * (+2 recessed fill, +3 inner shadow; the label / icon are drawn after it, on top).
     */
    static void selectable(List<byte[]> comps, List<InterfaceDefinition> want, InterfaceDefinition rect, int parent,
                           int x, int y, int w, int h, String action) {
        InterfaceDefinition b = solid(rect, parent, x, y, w, h, BOX);
        b.clickMask = 2;
        b.actions = new String[]{action};
        hover(b, BOX, BOX_HOVER);
        add(comps, want, b);
        add(comps, want, outlineRect(rect, parent, x, y, w, h, BORDER, false));
        InterfaceDefinition recess = solid(rect, parent, x + 1, y + 1, w - 2, h - 2, SELECTED_FILL);
        recess.isHidden = true;
        add(comps, want, recess);
        add(comps, want, outlineRect(rect, parent, x + 1, y + 1, w - 2, h - 2, SELECTED_SHADOW, true));
    }

    /**
     * The search bar (6 comps) at the right of the sub-header row: a recessed stone input box (click = search), its
     * text (placeholder / query), a border, a magnifier, and an inset Clear button (box + label) the server lights up
     * while a search is active.
     */
    static void searchBar(List<byte[]> comps, List<InterfaceDefinition> want, InterfaceDefinition rect, InterfaceDefinition textT,
                          InterfaceDefinition buttonT) {
        int x = 330, y = 43, w = 158, h = 16;
        InterfaceDefinition box = solid(rect, 0, x, y, w, h, SEARCH_FILL);
        box.clickMask = 2;
        box.actions = new String[]{"Search"};
        hover(box, SEARCH_FILL, SEARCH_HOVER);
        add(comps, want, box);
        add(comps, want, text(textT, 0, x + 19, y, w - 64, h, TEXT_FONT, GREY, ""));
        add(comps, want, outlineRect(rect, 0, x, y, w, h, SLOT_BORDER, false));
        InterfaceDefinition glass = sprite(buttonT, 0, x + 3, y + 1, 14, 14);
        glass.spriteId = MAGNIFIER;
        add(comps, want, glass);
        InterfaceDefinition clear = solid(rect, 0, x + w - 42, y + 2, 40, h - 4, BOX);
        clear.clickMask = 2;
        clear.actions = new String[]{"Clear search"};
        hover(clear, BOX, BOX_HOVER);
        add(comps, want, clear);
        InterfaceDefinition clearLabel = text(textT, 0, x + w - 42, y + 2, 40, h - 4, TEXT_FONT, GREY, "Clear");
        clearLabel.xTextAlignment = 1;
        add(comps, want, clearLabel);
    }

    /** 2 components: a clickable stone box with a hover lift and a centred label ("x" = the red delete button). */
    static void button(List<byte[]> comps, List<InterfaceDefinition> want, InterfaceDefinition rect, InterfaceDefinition textT,
                       int parent, int x, int y, int w, int h, String action, String label) {
        boolean danger = "x".equals(label);
        InterfaceDefinition b = solid(rect, parent, x, y, w, h, danger ? DANGER : BOX);
        b.clickMask = 2;
        b.actions = new String[]{danger ? "Delete" : action};
        hover(b, danger ? DANGER : BOX, danger ? DANGER_HOVER : BOX_HOVER);
        add(comps, want, b);
        InterfaceDefinition t = text(textT, parent, x, y, w, h, TEXT_FONT, TEXT, label);
        t.xTextAlignment = 1;
        add(comps, want, t);
    }

    static void osrsButton(List<byte[]> comps, List<InterfaceDefinition> want, InterfaceDefinition buttonT, InterfaceDefinition labelT,
                           int parent, int x, int y, int w, int h) {
        InterfaceDefinition b = copy(buttonT);
        place(b, parent, x, y, w, h);
        b.clickMask = 2;
        b.actions = new String[]{"Select"};
        b.onMouseOverListener = new Object[]{44, -2147483645, 24470};
        b.onMouseLeaveListener = new Object[]{44, -2147483645, 4525};
        add(comps, want, b);
        InterfaceDefinition l = copy(labelT);
        place(l, parent, x + 1, y + 2, w - 2, h - 4);
        l.text = "";
        add(comps, want, l);
    }

    static InterfaceDefinition outlineRect(InterfaceDefinition rect, int parent, int x, int y, int w, int h, int colour, boolean hidden) {
        InterfaceDefinition o = copy(rect);
        place(o, parent, x, y, w, h);
        o.filled = false;
        o.textColor = colour;
        o.isHidden = hidden;
        return o;
    }

    /** A graphic component for a perk icon (the perk's clientscript sets its sprite). */
    static InterfaceDefinition sprite(InterfaceDefinition buttonT, int parent, int x, int y, int w, int h) {
        InterfaceDefinition s = copy(buttonT);
        place(s, parent, x, y, w, h);
        s.spriteId = -1;
        s.spriteTiling = false;
        s.onMouseOverListener = null;
        s.onMouseLeaveListener = null;
        s.hasListener = false;
        return s;
    }

    static InterfaceDefinition solid(InterfaceDefinition rect, int parent, int x, int y, int w, int h, int colour) {
        InterfaceDefinition d = copy(rect);
        place(d, parent, x, y, w, h);
        d.filled = true;
        d.textColor = colour;
        d.opacity = 0;
        return d;
    }

    static void hover(InterfaceDefinition d, int normal, int hovered) {
        d.onMouseOverListener = new Object[]{45, -2147483645, hovered};
        d.onMouseLeaveListener = new Object[]{45, -2147483645, normal};
        d.hasListener = true;
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

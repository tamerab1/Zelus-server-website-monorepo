import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.InterfaceDefinition;
import net.runelite.cache.definitions.loaders.InterfaceLoader;
import net.runelite.cache.fs.*;

import java.io.File;
import java.lang.reflect.Field;
import java.util.*;

// Builds interface group 1155, "Zelus Battle Presets" (BattlePresets.java, server), opened from the PK Hub's single
// "Open Battle Presets" button. A 2 x 3 grid of preset cards: a combat level badge on the left, the preset name, an
// archetype line (spellbook + Atk/Str/Def) and 3 key gear icons; the whole card is clickable (Load / Preview) and its
// inset border lights up gold on hover. Bottom bar: My Custom Loadouts and Spawn Supplies (framed OSRS buttons).
// Encoder/verification shared with BuildBondsInterface. Prints "CONST name = id" lines for BattlePresets.java.
// Usage: verify|apply|rebuild <cachePath>
public class BuildBattlePresetsInterface {
    static final int GROUP = 1155, SRC = 1002;
    static final int GOLD = 0xffc35a, TEXT = 0xd8d0c0, GREY = 0x9a9a9a;
    static final int HEADING_FONT = 496, TEXT_FONT = 494;
    static final int TITLE_Y = 14, SUB_Y = 45;
    static final int PANEL_FILL = 0x36302a, CARD = 0x433b31, CARD_EDGE = 0x5a4d3a, BADGE = 0x1b1510;
    static final int CARDS = 6, CARD_W = 225, CARD_H = 58, GEAR = 3;
    static final int COMPONENTS = 82;

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

            InterfaceDefinition sub = text(textT, 0, 21, SUB_Y, 473, 13, TEXT_FONT, TEXT, "");
            sub.xTextAlignment = 1;
            add(comps, want, sub);

            // ---- the grid: 6 cards x (fill, clickable inset border, badge, level, "Combat", name, archetype, 3 gear) ----
            int grid = panel(comps, want, layerT, rectT, 0, 24, 62, 464, 190);
            ids.put("FIRST_CARD", comps.size());
            for (int c = 0; c < CARDS; c++) {
                int x = 6 + (c % 2) * 230, y = 6 + (c / 2) * 60;
                add(comps, want, solid(rect, grid, x, y, CARD_W, CARD_H, CARD));
                InterfaceDefinition edge = copy(rect);
                place(edge, grid, x, y, CARD_W, CARD_H);
                edge.filled = false;
                edge.textColor = CARD_EDGE;
                edge.clickMask = 2 | 4;
                edge.actions = new String[]{"Load", "Preview"};
                edge.onMouseOverListener = new Object[]{45, -2147483645, 0x7a5c32}; // muted bronze hover (gold read as a yellow box)
                edge.onMouseLeaveListener = new Object[]{45, -2147483645, CARD_EDGE};
                edge.hasListener = true;
                add(comps, want, edge);
                add(comps, want, solid(rect, grid, x + 5, y + 5, 42, 48, BADGE));
                InterfaceDefinition level = text(textT, grid, x + 5, y + 5, 42, 26, HEADING_FONT, 0xffff00, "");
                level.xTextAlignment = 1;
                add(comps, want, level);
                InterfaceDefinition cb = text(textT, grid, x + 5, y + 32, 42, 14, TEXT_FONT, GREY, "Combat");
                cb.xTextAlignment = 1;
                add(comps, want, cb);
                add(comps, want, text(textT, grid, x + 53, y + 3, 166, 15, HEADING_FONT, GOLD, ""));
                add(comps, want, text(textT, grid, x + 53, y + 16, 166, 12, TEXT_FONT, GREY, ""));
                for (int g = 0; g < GEAR; g++) {
                    InterfaceDefinition item = copy(buttonT);
                    place(item, grid, x + 52 + g * 40, y + 27, 36, 30);
                    item.spriteId = -1;
                    item.spriteTiling = false;
                    item.onMouseOverListener = null;
                    item.onMouseLeaveListener = null;
                    item.hasListener = false;
                    item.borderType = 1;
                    item.shadowColor = 0x302020;
                    add(comps, want, item);
                }
            }

            // ---- bottom bar ----
            ids.put("MY_LOADOUTS", comps.size());
            osrsButton(comps, want, buttonT, labelT, 24, 279, 228, 26, "My Custom Loadouts");
            ids.put("SPAWN", comps.size());
            osrsButton(comps, want, buttonT, labelT, 260, 279, 228, 26, "Spawn Supplies");

            // ---- free supply row (appended so the ids above don't move): Food / Potions / Veng / Barrage ----
            ids.put("FIRST_SUPPLY", comps.size());
            String[] supplies = {"FOOD FREE", "POTIONS FREE", "VENG FREE", "BARRAGE FREE"};
            for (int k = 0; k < supplies.length; k++)
                osrsButton(comps, want, buttonT, labelT, 24 + k * 117, 255, 113, 21, "<col=ff981f>" + supplies[k] + "</col>");

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

    static void osrsButton(List<byte[]> comps, List<InterfaceDefinition> want, InterfaceDefinition buttonT, InterfaceDefinition labelT,
                           int x, int y, int w, int h, String label) {
        InterfaceDefinition b = copy(buttonT);
        place(b, 0, x, y, w, h);
        b.clickMask = 2;
        b.actions = new String[]{"Open"};
        b.onMouseOverListener = new Object[]{44, -2147483645, 24470};
        b.onMouseLeaveListener = new Object[]{44, -2147483645, 4525};
        add(comps, want, b);
        InterfaceDefinition l = copy(labelT);
        place(l, 0, x + 1, y + 2, w - 2, h - 4);
        l.text = label;
        add(comps, want, l);
    }

    static int panel(List<byte[]> comps, List<InterfaceDefinition> want, InterfaceDefinition layerT, InterfaceDefinition[] rectT,
                     int parent, int x, int y, int w, int h) {
        int id = comps.size();
        add(comps, want, layer(layerT, parent, x, y, w, h));
        add(comps, want, solid(rectT[1], id, 0, 0, w, h, PANEL_FILL));
        for (int k = 0; k < 3; k++) {
            InterfaceDefinition r = copy(rectT[k]);
            if (k == 2) // the frame's filled inner rect (1002:161, 312b23) is the visible panel colour: warm stone
                r.textColor = 0x36302a;
            place(r, id, k, k, w - 2 * k, h - 2 * k);
            add(comps, want, r);
        }
        return id;
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

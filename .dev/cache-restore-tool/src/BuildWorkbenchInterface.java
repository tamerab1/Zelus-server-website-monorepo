import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.InterfaceDefinition;
import net.runelite.cache.definitions.loaders.InterfaceLoader;
import net.runelite.cache.fs.*;
import net.runelite.cache.index.FileData;
import net.runelite.cache.index.IndexData;

import java.io.File;
import java.lang.reflect.Field;
import java.util.*;

// Builds interface group 1153, the Zelus Forge item workbench (WorkbenchHub.java, server) that replaces the old
// perk-attachment screen 1126 (the side inventory 1128 stays). Three panes in the Pit hub style:
//  - left: a scrolling catalogue of 19 perk cards (icon, name, tier / slot line), hover + gold selected outline;
//  - centre: the selected perk's mechanics (description, the keep-your-level rule) and its Lv 1-5 roll odds;
//  - right: the target item slot (click to change), its perks, the required and added material slots,
//    an "Add minimum" button and a framed "Upgrade Item" button.
// Encoder/verification shared with BuildBondsInterface. Prints "CONST name = id" lines for WorkbenchHub.java.
// Usage: verify|apply|rebuild <cachePath>
public class BuildWorkbenchInterface {
    static final int GROUP = 1153, SRC = 1002;
    static final int GOLD = 0xffc35a, TEXT = 0xd8d0c0, GREY = 0x9a9a9a;
    static final int HEADING_FONT = 496, TEXT_FONT = 494;
    static final int TITLE_Y = 14, SUB_Y = 45;
    static final int PANEL_FILL = 0x36302a, BOX = 0x433b31, BOX_HOVER = 0x524737, BORDER = 0x5a4d3a;
    static final int SLOT_FILL = 0x2a2520, SLOT_BORDER = 0x5a4d3a;
    static final int CARDS = 19, CARD_H = 34, CARD_PITCH = 36, MATERIAL_SLOTS = 4, ODDS_LINES = 5;
    static final int COMPONENTS = 220;

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

            // ---- left: perk catalogue ----
            int left = panel(comps, want, layerT, rectT, 0, 24, 62, 156, 243);
            ids.put("SCROLL", comps.size());
            ids.put("SCROLLBAR", comps.size() + 1);
            add(comps, want, layer(layerT, left, 4, 4, 132, 235));
            add(comps, want, layer(layerT, left, 138, 4, 16, 235));
            int scroll = ids.get("SCROLL");
            ids.put("FIRST_CARD", comps.size());
            for (int c = 0; c < CARDS; c++) {
                int card = comps.size();
                add(comps, want, layer(layerT, scroll, 0, c * CARD_PITCH, 132, CARD_H));
                selectable(comps, want, rect, card, 0, 0, 132, CARD_H, "Select");
                add(comps, want, sprite(buttonT, card, 2, 2, 30, 30, -1));
                add(comps, want, text(textT, card, 36, 2, 94, 15, TEXT_FONT, GOLD, ""));
                add(comps, want, text(textT, card, 36, 17, 94, 15, TEXT_FONT, GREY, ""));
            }

            // ---- centre: mechanics and odds ----
            int centre = panel(comps, want, layerT, rectT, 0, 184, 62, 150, 243);
            ids.put("PERK_NAME", comps.size());
            InterfaceDefinition name = text(textT, centre, 5, 4, 140, 16, HEADING_FONT, GOLD, "");
            name.xTextAlignment = 1;
            add(comps, want, name);
            InterfaceDefinition info = text(textT, centre, 5, 20, 140, 13, TEXT_FONT, GREY, "");
            info.xTextAlignment = 1;
            add(comps, want, info);
            InterfaceDefinition desc = text(textT, centre, 6, 37, 138, 100, TEXT_FONT, TEXT, "");
            desc.yTextAlignment = 0;
            desc.lineHeight = 11;
            add(comps, want, desc);
            add(comps, want, text(textT, centre, 6, 150, 138, 14, TEXT_FONT, GOLD, ""));
            ids.put("FIRST_ODDS", comps.size());
            for (int l = 0; l < ODDS_LINES; l++)
                add(comps, want, text(textT, centre, 6, 166 + l * 14, 138, 14, TEXT_FONT, TEXT, ""));

            // ---- right: target, materials, actions ----
            int right = panel(comps, want, layerT, rectT, 0, 338, 62, 150, 243);
            add(comps, want, text(textT, right, 6, 3, 138, 14, TEXT_FONT, GOLD, "Target item"));
            ids.put("TARGET", comps.size() + 2);
            add(comps, want, solid(rect, right, 6, 18, 36, 32, SLOT_FILL));
            add(comps, want, outline(rect, right, 6, 18, 36, 32, SLOT_BORDER, false));
            InterfaceDefinition target = sprite(buttonT, right, 6, 18, 36, 32, -1);
            target.borderType = 1;
            target.shadowColor = 0x302020;
            target.clickMask = 2;
            target.actions = new String[]{"Change item"};
            add(comps, want, target);
            ids.put("TARGET_NAME", comps.size());
            add(comps, want, text(textT, right, 46, 18, 100, 16, TEXT_FONT, TEXT, ""));
            add(comps, want, text(textT, right, 46, 34, 100, 14, TEXT_FONT, GREY, ""));
            InterfaceDefinition perks = text(textT, right, 6, 52, 138, 26, TEXT_FONT, TEXT, "");
            perks.yTextAlignment = 0;
            perks.lineHeight = 12;
            add(comps, want, perks);
            add(comps, want, text(textT, right, 6, 80, 138, 14, TEXT_FONT, GOLD, "Required materials"));
            ids.put("FIRST_COST", comps.size());
            materialRow(comps, want, rect, buttonT, right, 95);
            ids.put("ADDED_LABEL", comps.size());
            add(comps, want, text(textT, right, 6, 130, 138, 14, TEXT_FONT, GOLD, ""));
            ids.put("FIRST_ADDED", comps.size());
            materialRow(comps, want, rect, buttonT, right, 145);
            ids.put("ADD_MINIMUM", comps.size());
            // compact OSRS bezel button (2026-10-15; was a flat stone strip that read as floating text), centred
            // between the added-materials row (ends y177) and Upgrade Item (y207)
            InterfaceDefinition addMin = copy(buttonT);
            place(addMin, right, 20, 180, 110, 23);
            addMin.clickMask = 2;
            addMin.actions = new String[]{"Add minimum"};
            addMin.onMouseOverListener = new Object[]{44, -2147483645, 24470};
            addMin.onMouseLeaveListener = new Object[]{44, -2147483645, 4525};
            addMin.hasListener = true;
            add(comps, want, addMin);
            InterfaceDefinition addLabel = copy(labelT);
            place(addLabel, right, 21, 182, 108, 19);
            addLabel.text = "";
            add(comps, want, addLabel);
            ids.put("UPGRADE", comps.size());
            InterfaceDefinition upgrade = copy(buttonT);
            place(upgrade, right, 6, 207, 138, 30);
            upgrade.clickMask = 2;
            upgrade.actions = new String[]{"Upgrade Item"};
            upgrade.onMouseOverListener = new Object[]{44, -2147483645, 24470};
            upgrade.onMouseLeaveListener = new Object[]{44, -2147483645, 4525};
            add(comps, want, upgrade);
            InterfaceDefinition upgradeLabel = copy(labelT);
            place(upgradeLabel, right, 7, 209, 136, 26);
            upgradeLabel.text = "Upgrade Item";
            add(comps, want, upgradeLabel);

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
            write(store, storage, index, srcArchive, comps);
            System.out.println("APPLY complete -- interface " + GROUP + " with " + comps.size() + " components.");
        }
    }

    /** 4 material slots (fill, border, item graphic with Examine) in a row at y. */
    static void materialRow(List<byte[]> comps, List<InterfaceDefinition> want, InterfaceDefinition rect, InterfaceDefinition buttonT,
                            int parent, int y) {
        for (int k = 0; k < MATERIAL_SLOTS; k++) {
            int x = 4 + k * 36;
            add(comps, want, solid(rect, parent, x, y, 34, 32, SLOT_FILL));
            add(comps, want, outline(rect, parent, x, y, 34, 32, SLOT_BORDER, false));
            InterfaceDefinition item = sprite(buttonT, parent, x - 1, y, 36, 32, -1);
            item.borderType = 1;
            item.shadowColor = 0x302020;
            item.clickMask = 2;
            item.actions = new String[]{"Examine"};
            add(comps, want, item);
        }
    }

    static void write(Store store, Storage storage, Index index, Archive srcArchive, List<byte[]> comps) throws Exception {
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

    static void selectable(List<byte[]> comps, List<InterfaceDefinition> want, InterfaceDefinition rect, int parent,
                           int x, int y, int w, int h, String action) {
        InterfaceDefinition b = solid(rect, parent, x, y, w, h, BOX);
        b.clickMask = 2;
        b.actions = new String[]{action};
        hover(b, BOX, BOX_HOVER);
        add(comps, want, b);
        add(comps, want, outline(rect, parent, x, y, w, h, BORDER, false));
        add(comps, want, outline(rect, parent, x, y, w, h, GOLD, true));
        add(comps, want, outline(rect, parent, x + 1, y + 1, w - 2, h - 2, GOLD, true));
    }

    static InterfaceDefinition sprite(InterfaceDefinition buttonT, int parent, int x, int y, int w, int h, int spriteId) {
        InterfaceDefinition s = copy(buttonT);
        place(s, parent, x, y, w, h);
        s.spriteId = spriteId;
        s.spriteTiling = false;
        s.onMouseOverListener = null;
        s.onMouseLeaveListener = null;
        s.hasListener = false;
        return s;
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

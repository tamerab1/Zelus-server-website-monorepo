import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.InterfaceDefinition;
import net.runelite.cache.fs.*;
import net.runelite.cache.index.FileData;
import net.runelite.cache.index.IndexData;

import java.io.File;
import java.util.*;

// Builds interface group 1153, the Zelus Forge item workbench (WorkbenchHub.java, server). 2026-10-20 redesign on
// IfKit (the Trading Post's OSRS stone buttons), after "1px yellow stroke, flat grey buttons, clipped description":
//  - left (160): a scrolling catalogue of 19 perk cards (icon in an inset well, name, tier / slot line); hover lifts the
//    stone, the selected card is recessed (no outline);
//  - centre (150): the perk's name, tier / slot line, the full description in a scroll pane (wrapped, nothing cut off), and the roll odds
//    as 5 coloured tier pills, each with its own value line;
//  - right (146): the target item in a larger inset socket (an "Item" hint while empty), its upgrades and perks, the
//    required and added material slots, Add minimum (stone utility button) and Upgrade Item (the primary stone button,
//    pressed / greyed until it can be used).
// Prints "CONST name = id" lines for WorkbenchHub.java. Usage: verify|rebuild <cachePath>
public class BuildWorkbenchInterface {
    static final int GROUP = 1153, CARDS = 19, CARD_H = 36, CARD_PITCH = 38, MATERIAL_SLOTS = 4, ODDS_LINES = 5;
    static final int PANEL_EDGE = 0x1d1915, PANEL_RIM = 0x2b2620, PANEL_FILL = 0x3d362d, WELL = 0x2c2721;
    static final int CARD_A = 0x463f34, CARD_B = 0x423b31, CARD_HOVER = 0x4f4739, CARD_SELECTED = 0x2b251d;
    /** Tier pill tints, Lv 5 (gold) .. Lv 1 (grey), matching WorkbenchHub.levelColour. */
    static final int[] PILLS = {0x4a3a14, 0x3a2147, 0x1c3550, 0x1f4020, 0x3a3a3a};

    public static void main(String[] args) throws Exception {
        boolean apply = args[0].equals("rebuild");
        try (Store store = new Store(new File(args[1]))) {
            store.load();
            IfKit k = new IfKit(GROUP, store, true);

            k.mark("SUB_HEADER");
            k.add(IfKit.centred(k.text(0, 21, 44, 473, 16, IfKit.TEXT_FONT, IfKit.TEXT, "")));

            // ---- left: perk catalogue ----
            int left = framed(k, 24, 64, 160, 242);
            k.mark("SCROLL");
            int scroll = k.add(k.layer(left, 4, 4, 136, 234));
            k.mark("SCROLLBAR");
            k.add(k.layer(left, 142, 4, 16, 234));
            k.mark("FIRST_CARD");
            for (int c = 0; c < CARDS; c++) {
                int card = k.add(k.layer(scroll, 0, c * CARD_PITCH, 136, CARD_H));             // +0
                int zebra = c % 2 == 0 ? CARD_A : CARD_B;
                InterfaceDefinition fill = k.solid(card, 0, 0, 136, CARD_H, zebra);             // +1 click
                fill.clickMask = 2;
                fill.actions = new String[]{"Select"};
                fill.onMouseOverListener = new Object[]{45, -2147483645, CARD_HOVER};
                fill.onMouseLeaveListener = new Object[]{45, -2147483645, zebra};
                fill.hasListener = true;
                k.add(fill);
                k.add(IfKit.hidden(k.solid(card, 0, 0, 136, CARD_H, CARD_SELECTED)));            // +2 selected recess
                k.add(IfKit.hidden(k.outline(card, 0, 0, 136, CARD_H, IfKit.SHADOW, true)));     // +3 its inner shadow
                k.add(k.outline(card, 0, 0, 136, CARD_H, PANEL_RIM, false));                     // +4 divider
                k.add(k.solid(card, 2, 2, 32, 32, WELL));                                       // +5 icon well
                k.add(k.sprite(card, 3, 3, 30, 30, -1, false));                                 // +6 icon
                k.add(k.text(card, 38, 3, 96, 15, IfKit.TEXT_FONT, IfKit.GOLD, ""));             // +7 name
                k.add(k.text(card, 38, 18, 96, 15, IfKit.TEXT_FONT, 0xb0a898, ""));              // +8 tier / slot line
            }

            // ---- centre: mechanics and odds ----
            int centre = framed(k, 188, 64, 150, 242);
            k.mark("PERK_NAME");
            k.add(IfKit.centred(k.text(centre, 4, 3, 142, 17, IfKit.HEADING_FONT, IfKit.ORANGE, "")));
            k.mark("PERK_INFO");
            k.add(IfKit.centred(k.text(centre, 4, 20, 142, 13, IfKit.TEXT_FONT, 0xb0a898, "")));
            k.add(k.solid(centre, 6, 35, 138, 1, PANEL_RIM));
            // the full description (up to ~400 characters) in its own scroll pane: nothing is cut off
            k.mark("DESC_SCROLL");
            int desc = k.add(k.layer(centre, 6, 38, 120, 100));
            k.mark("DESC_SCROLLBAR");
            k.add(k.layer(centre, 128, 38, 16, 100));
            k.mark("PERK_DESC");
            k.add(k.block(desc, 1, 0, 118, 420, IfKit.TEXT, 12, false));
            // roll odds: an inset with 5 tier pills (static "Lv n") and the server's value line beside each
            k.add(k.solid(centre, 4, 141, 142, 97, WELL));
            k.add(k.outline(centre, 4, 141, 142, 97, PANEL_EDGE, false));
            k.mark("ODDS_HEADER");
            k.add(k.text(centre, 9, 143, 132, 14, IfKit.TEXT_FONT, IfKit.GOLD, ""));
            k.mark("FIRST_ODDS");
            for (int l = 0; l < ODDS_LINES; l++) {
                int y = 159 + l * 16;
                k.add(k.text(centre, 46, y, 96, 14, IfKit.TEXT_FONT, IfKit.TEXT, ""));           // the value line
            }
            for (int l = 0; l < ODDS_LINES; l++) {
                int y = 159 + l * 16;
                k.add(k.solid(centre, 9, y, 34, 13, PILLS[l]));
                k.add(IfKit.centred(k.text(centre, 9, y, 34, 13, IfKit.TEXT_FONT, IfKit.WHITE, "Lv " + (5 - l))));
            }

            // ---- right: target, materials, actions ----
            int right = framed(k, 342, 64, 146, 242);
            k.add(k.text(right, 6, 3, 134, 14, IfKit.TEXT_FONT, IfKit.GOLD, "Target item"));
            k.add(k.solid(right, 6, 18, 42, 40, WELL));
            k.add(k.solid(right, 6, 18, 42, 2, IfKit.SHADOW));
            k.add(k.solid(right, 6, 18, 2, 40, IfKit.SHADOW));
            k.add(k.outline(right, 6, 18, 42, 40, PANEL_EDGE, false));
            k.mark("TARGET_HINT");
            k.add(IfKit.centred(k.text(right, 6, 18, 42, 40, IfKit.TEXT_FONT, 0x6f6658, "Item")));
            k.mark("TARGET");
            k.add(k.itemSlot(right, 9, 22, 36, 32, "Change item"));
            k.mark("TARGET_NAME");
            k.add(k.text(right, 52, 18, 90, 20, IfKit.TEXT_FONT, IfKit.TEXT, ""));
            k.mark("TARGET_UPGRADES");
            k.add(k.text(right, 52, 38, 90, 18, IfKit.TEXT_FONT, 0xb0a898, ""));
            k.mark("TARGET_PERKS");
            k.add(k.block(right, 6, 60, 134, 24, IfKit.TEXT, 12, false));
            k.add(k.text(right, 6, 86, 134, 13, IfKit.TEXT_FONT, IfKit.GOLD, "Required materials"));
            k.mark("FIRST_COST");
            materialRow(k, right, 100);
            k.mark("ADDED_LABEL");
            k.add(k.text(right, 6, 137, 134, 13, IfKit.TEXT_FONT, IfKit.GOLD, ""));
            k.mark("FIRST_ADDED");
            materialRow(k, right, 151);
            k.mark("ADD_MINIMUM");
            k.stoneButton(right, 6, 189, 134, 22, "Add minimum", IfKit.TEXT_FONT, "");
            k.mark("UPGRADE");
            k.stoneButton(right, 4, 214, 138, 24, "Upgrade Item", IfKit.HEADING_FONT, "");

            k.finish(apply);
        }
    }

    /** A framed column: layer, fill, a dark edge and an inner rim. Returns the layer. */
    static int framed(IfKit k, int x, int y, int w, int h) {
        int id = k.add(k.layer(0, x, y, w, h));
        k.add(k.solid(id, 0, 0, w, h, PANEL_FILL));
        k.add(k.outline(id, 0, 0, w, h, PANEL_EDGE, false));
        k.add(k.outline(id, 1, 1, w - 2, h - 2, PANEL_RIM, false));
        return id;
    }

    /** 4 material slots (well, rim, item with Examine) at y, 35px apart. */
    static void materialRow(IfKit k, int parent, int y) {
        for (int s = 0; s < MATERIAL_SLOTS; s++) {
            int x = 4 + s * 35;
            k.add(k.solid(parent, x, y, 34, 34, WELL));
            k.add(k.outline(parent, x, y, 34, 34, PANEL_EDGE, false));
            k.add(k.itemSlot(parent, x - 1, y + 1, 36, 32, "Examine"));
        }
    }

    /** Kept for older builders that wrote their groups through this one. */
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
}

import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.InterfaceDefinition;
import net.runelite.cache.definitions.loaders.InterfaceLoader;
import net.runelite.cache.fs.*;
import net.runelite.cache.index.FileData;
import net.runelite.cache.index.IndexData;

import java.io.File;
import java.lang.reflect.Field;
import java.util.*;

// Builds interface group 1150, the new-player "Select your path" screen (StarterSelectInterface.java, server), in the
// Pit hub's style: Wilderness Forge frame (1002:0-3), a sub-header, an account-type list (7 selectable rows with a helm
// icon), a row of 4 framed difficulty buttons, a summary panel (heading, rates strip, bullet lines, a scrolling grid of
// inventory-style starter-kit slots) and a Confirm button. Selected states are hidden here and toggled by the server.
// Encoder/verification shared with BuildBondsInterface. Usage: verify|apply|rebuild <cachePath>
public class BuildStarterInterface {
    static final int GROUP = 1150, SRC = 1002;
    static final int GOLD = 0xffc35a, TEXT = 0xd8d0c0;
    static final int HEADING_FONT = 496, TEXT_FONT = 494;
    static final int TITLE_Y = 14, SUB_Y = 45;
    /** Row / button fill, its hover wash (a subtle brightness shift), and the always-on border. */
    static final int BOX = 0x433b31, BOX_HOVER = 0x4a4137, BORDER = 0x5a4d3a;
    /**
     * Selected state (2026-10-20: no more 2px gold wireframe, see the Zelus Market tabs): account rows sink into a
     * darker recessed stone with an inner shadow edge; difficulty buttons are OSRS bezel buttons (1002:169) that press
     * into a dark recess with a soft trim -- the Market's bezelTab look.
     */
    static final int ROW_RECESS = 0x2b251d, ROW_SHADOW = 0x15120f, BEZEL_RECESS = 0x0d0b09, BEZEL_TRIM = 0x2b231a;
    /** Kit slots: a lighter recessed square with an inner shadow so dark items (iron armour) stay visible. */
    static final int SLOT_FILL = 0x2a2520, SLOT_SHADOW = 0x1a1612, SLOT_BORDER = 0x5a4d3a;
    static final int SLOT_W = 36, SLOT_H = 32, SLOT_GAP = 4;
    /** Kit grid: 30 slots (the biggest kit is 26 items), 6 per row, 2 rows visible. */
    static final int KIT_SLOTS = 30, KIT_COLS = 6, KIT_VIEW_H = 2 * SLOT_H + SLOT_GAP;
    static final int ACCOUNT_ROWS = 7, DIFFICULTIES = 4;
    /** Expected component count; ids are mirrored in StarterSelectInterface. */
    static final int COMPONENTS = 271;

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
            // 0-2: root, background, close button (verbatim copies); 3: the title
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
            Archive a84 = index.getArchive(84);
            InterfaceDefinition modelT = loader.load(0, a84.getFiles(storage.loadArchive(a84)).findFile(4).getContents());
            InterfaceDefinition rect = rectT[1];

            // 4: sub-header
            InterfaceDefinition sub = text(textT, 0, 21, SUB_Y, 473, 13, TEXT_FONT, TEXT, "");
            sub.xTextAlignment = 1;
            add(comps, want, sub);

            // 5-8: account-type panel; 9: heading; 10: note; 11-52: 7 rows x (box, border, recess, inner shadow, icon, name)
            add(comps, want, layer(layerT, 0, 24, 62, 196, 218));
            frame(comps, want, rectT, 5, 196, 218);
            add(comps, want, text(textT, 5, 8, 4, 180, 14, TEXT_FONT, GOLD, "Account type"));
            InterfaceDefinition note = text(textT, 5, 100, 4, 88, 14, TEXT_FONT, 0x9a9a9a, "");
            note.xTextAlignment = 2;
            add(comps, want, note);
            for (int r = 0; r < ACCOUNT_ROWS; r++) {
                int y = 20 + r * 28;
                selectable(comps, want, rect, 5, 6, y, 184, 26);
                add(comps, want, model(modelT, 5, 8, y, 32, 26));
                add(comps, want, text(textT, 5, 44, y, 144, 26, TEXT_FONT, TEXT, ""));
            }

            // 53-56: difficulty panel; 57-76: 4 buttons x (bezel button, placeholder, recess, trim, name + rate)
            add(comps, want, layer(layerT, 0, 226, 62, 262, 38));
            frame(comps, want, rectT, 53, 262, 38);
            for (int d = 0; d < DIFFICULTIES; d++) {
                int x = 5 + d * 63;
                bezelSelectable(comps, want, rect, buttonT, 53, x, 4, 60, 30);
                InterfaceDefinition t = text(textT, 53, x, 4, 60, 30, TEXT_FONT, TEXT, "");
                t.xTextAlignment = 1;
                add(comps, want, t);
            }

            // 77-80: summary panel (full height of the right column); 81 heading, 82 rates strip, 83 rates text,
            // 84 bullets, 85 divider, 86 kit header, 87 kit scroll container, 88 its scrollbar,
            // 89-268: 30 kit slots x (slot layer, fill, shadow top, shadow left, border, item)
            add(comps, want, layer(layerT, 0, 226, 104, 262, 201));
            frame(comps, want, rectT, 77, 262, 201);
            add(comps, want, text(textT, 77, 8, 4, 246, 18, HEADING_FONT, GOLD, ""));
            add(comps, want, solid(rect, 77, 6, 24, 250, 17, 0x0e0b08));
            InterfaceDefinition rates = text(textT, 77, 6, 24, 250, 17, TEXT_FONT, TEXT, "");
            rates.xTextAlignment = 1;
            add(comps, want, rates);
            InterfaceDefinition bullets = text(textT, 77, 8, 44, 246, 64, TEXT_FONT, TEXT, "");
            bullets.yTextAlignment = 0;
            bullets.lineHeight = 16;
            add(comps, want, bullets);
            add(comps, want, solid(rect, 77, 6, 110, 250, 1, rect.textColor));
            add(comps, want, text(textT, 77, 8, 112, 246, 14, TEXT_FONT, TEXT, ""));
            // Kit grid: 6 inventory-style slots per row (36x32, 4px apart), 2 rows visible, scrolled by clientscript
            // 10532 (sized by the server to the kit). Each slot sits in its own layer so the server hides unused ones.
            int gridW = KIT_COLS * SLOT_W + (KIT_COLS - 1) * SLOT_GAP;
            int gridX = 3 + (256 - (gridW + 2 + 16)) / 2;
            add(comps, want, layer(layerT, 77, gridX, 129, gridW, KIT_VIEW_H));
            add(comps, want, layer(layerT, 77, gridX + gridW + 2, 129, 16, KIT_VIEW_H));
            for (int k = 0; k < KIT_SLOTS; k++) {
                int x = (k % KIT_COLS) * (SLOT_W + SLOT_GAP), y = (k / KIT_COLS) * (SLOT_H + SLOT_GAP);
                int slot = comps.size();
                add(comps, want, layer(layerT, 87, x, y, SLOT_W, SLOT_H));
                add(comps, want, solid(rect, slot, 0, 0, SLOT_W, SLOT_H, SLOT_FILL));
                add(comps, want, solid(rect, slot, 0, 0, SLOT_W, 2, SLOT_SHADOW));
                add(comps, want, solid(rect, slot, 0, 0, 2, SLOT_H, SLOT_SHADOW));
                InterfaceDefinition border = copy(rect);
                place(border, slot, 0, 0, SLOT_W, SLOT_H);
                border.filled = false;
                border.textColor = SLOT_BORDER;
                add(comps, want, border);
                // a graphic component holding an item: the client draws it exactly like an inventory slot (36x32
                // item sprite with its outline, shadow and yellow stack number), so coins fit and show "500K"
                InterfaceDefinition item = copy(buttonT);
                place(item, slot, 0, 0, SLOT_W, SLOT_H);
                item.spriteId = -1;
                item.spriteTiling = false;
                item.borderType = 1;
                item.shadowColor = 0x302020;
                item.onMouseOverListener = null;
                item.onMouseLeaveListener = null;
                item.hasListener = false;
                item.clickMask = 2;
                item.actions = new String[]{"Examine"};
                add(comps, want, item);
            }

            // 269-270: Confirm button + label, under the account list
            InterfaceDefinition confirm = copy(buttonT);
            place(confirm, 0, 24, 284, 196, 21);
            confirm.clickMask = 2;
            confirm.actions = new String[]{"Confirm"};
            confirm.onMouseOverListener = new Object[]{44, -2147483645, 24470};
            confirm.onMouseLeaveListener = new Object[]{44, -2147483645, 4525};
            add(comps, want, confirm);
            InterfaceDefinition label = copy(labelT);
            place(label, 0, 25, 285, 194, 19);
            label.text = "Confirm & Begin Adventure";
            add(comps, want, label);

            if (comps.size() != COMPONENTS)
                throw new IllegalStateException("component count " + comps.size() + " != " + COMPONENTS + " (StarterSelectInterface ids)");

            for (int c = 0; c < comps.size(); c++) {
                InterfaceDefinition got = loader.load((GROUP << 16) | c, comps.get(c));
                InterfaceDefinition exp = want.get(c);
                exp.id = (GROUP << 16) | c;
                for (Field f : InterfaceDefinition.class.getFields()) {
                    if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                    if (!Objects.deepEquals(f.get(got), f.get(exp)))
                        throw new IllegalStateException("component " + c + ": " + f.getName() + " " + f.get(got) + " != " + f.get(exp));
                }
                System.out.printf("%3d type=%d x=%d y=%d w=%d h=%d parent=%d hidden=%b %s%n", c, got.type, got.originalX, got.originalY,
                        got.originalWidth, got.originalHeight, got.parentId == -1 ? -1 : got.parentId & 0xFFFF, got.isHidden,
                        got.actions == null ? "" : Arrays.toString(got.actions));
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

    /**
     * A selectable account row (4 components): a solid fill that washes slightly lighter on hover (clickable, op
     * "Select"), an always-on border, and the selected state the server shows: a recessed darker stone fill and a 1px
     * inner-shadow edge (the row's icon and name are drawn after, over it).
     */
    static void selectable(List<byte[]> comps, List<InterfaceDefinition> want, InterfaceDefinition rect, int parent,
                           int x, int y, int w, int h) {
        InterfaceDefinition b = solid(rect, parent, x, y, w, h, BOX);
        b.clickMask = 2;
        b.actions = new String[]{"Select"};
        hover(b, BOX, BOX_HOVER);
        add(comps, want, b);
        InterfaceDefinition border = copy(rect);
        place(border, parent, x, y, w, h);
        border.filled = false;
        border.textColor = BORDER;
        add(comps, want, border);
        InterfaceDefinition recess = solid(rect, parent, x + 1, y + 1, w - 2, h - 2, ROW_RECESS);
        recess.isHidden = true;
        add(comps, want, recess);
        InterfaceDefinition shadow = copy(rect);
        place(shadow, parent, x + 1, y + 1, w - 2, h - 2);
        shadow.filled = false;
        shadow.textColor = ROW_SHADOW;
        shadow.isHidden = true;
        add(comps, want, shadow);
    }

    /**
     * A selectable difficulty button (4 components, same ids as {@link #selectable}): an OSRS bezel button (1002:169,
     * hover script 44 brightens the sprite), a zero-size placeholder where the flat border was, and the pressed state the
     * server shows: a dark translucent recess over the whole button plus a soft dark trim.
     */
    static void bezelSelectable(List<byte[]> comps, List<InterfaceDefinition> want, InterfaceDefinition rect,
                                InterfaceDefinition buttonT, int parent, int x, int y, int w, int h) {
        InterfaceDefinition b = copy(buttonT);
        place(b, parent, x, y, w, h);
        b.clickMask = 2;
        b.actions = new String[]{"Select"};
        b.onMouseOverListener = new Object[]{44, -2147483645, 24470};
        b.onMouseLeaveListener = new Object[]{44, -2147483645, 4525};
        b.hasListener = true;
        add(comps, want, b);
        InterfaceDefinition none = copy(rect);
        place(none, parent, x, y, 0, 0);
        none.filled = false;
        add(comps, want, none);
        InterfaceDefinition recess = solid(rect, parent, x, y, w, h, BEZEL_RECESS);
        recess.opacity = 60;
        recess.isHidden = true;
        add(comps, want, recess);
        InterfaceDefinition trim = copy(rect);
        place(trim, parent, x, y, w, h);
        trim.filled = false;
        trim.textColor = BEZEL_TRIM;
        trim.isHidden = true;
        add(comps, want, trim);
    }

    /** An opaque filled rect. */
    static InterfaceDefinition solid(InterfaceDefinition rect, int parent, int x, int y, int w, int h, int colour) {
        InterfaceDefinition d = copy(rect);
        place(d, parent, x, y, w, h);
        d.filled = true;
        d.textColor = colour;
        d.opacity = 0;
        return d;
    }

    /** Hover colour via clientscript 45 (sets the event component's colour). */
    static void hover(InterfaceDefinition d, int normal, int hovered) {
        d.onMouseOverListener = new Object[]{45, -2147483645, hovered};
        d.onMouseLeaveListener = new Object[]{45, -2147483645, normal};
        d.hasListener = true;
    }

    static InterfaceDefinition model(InterfaceDefinition modelT, int parent, int x, int y, int w, int h) {
        InterfaceDefinition m = copy(modelT);
        m.contentType = 0; // 84:4 is contentType 328 (always the local player) -- must be cleared
        place(m, parent, x, y, w, h);
        m.modelId = -1;
        return m;
    }

    static void add(List<byte[]> comps, List<InterfaceDefinition> want, InterfaceDefinition d) {
        comps.add(BuildBondsInterface.encodeIf3(d));
        want.add(d);
    }

    static void frame(List<byte[]> comps, List<InterfaceDefinition> want, InterfaceDefinition[] rectT, int parent, int w, int h) {
        for (int k = 0; k < 3; k++) {
            InterfaceDefinition r = copy(rectT[k]);
            if (k == 2) // the frame's filled inner rect (1002:161, 312b23) is the visible panel colour: warm stone
                r.textColor = 0x36302a;
            place(r, parent, k, k, w - 2 * k, h - 2 * k);
            add(comps, want, r);
        }
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

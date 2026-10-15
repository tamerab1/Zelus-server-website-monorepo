import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.InterfaceDefinition;
import net.runelite.cache.definitions.loaders.InterfaceLoader;
import net.runelite.cache.fs.*;
import net.runelite.cache.index.FileData;
import net.runelite.cache.index.IndexData;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.util.*;

// Builds interface group 1139, the Ascended Bestiary (combat pet guide): the Wilderness Forge frame
// (root, background, close button, title copied byte-for-byte from 1002:0-3), a column of 4 tab
// buttons (forge "Upgrade Now!" button + label, 1002:169/170, hover sprite included), a framed
// active-pet summary box under the tabs, and a framed content panel with a section title, a
// divider, a scroll container of 40 text rows (with a scrollbar strip; the server runs clientscript
// 10532 per tab) and a hidden pet model box (68) for the pet page.
// Same encoder/verification as BuildItemStatsInterface. Component ids: AscendedBestiary.java (server).
// Usage: verify|apply|rebuild <cachePath>
public class BuildBestiaryInterface {
    static final int GROUP = 1139, SRC = 1002;
    static final Charset CP1252 = Charset.forName("windows-1252");
    static final int TITLE_COLOUR = 0xffc35a, TEXT_COLOUR = 0xd8d0c0;
    static final int HEADING_FONT = 496, TEXT_FONT = 494;
    static final int ROWS = 40, INFO_ROWS = 4;
    static final int SCROLL_W = 276, SCROLL_H = 218;

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
            // 0-3: root, background, close button, title (verbatim copies; parents are child indices).
            for (int c = 0; c <= 3; c++) {
                byte[] b = src.findFile(c).getContents();
                comps.add(b);
                want.add(loader.load((GROUP << 16) | c, b));
            }
            InterfaceDefinition layerT = loader.load(0, src.findFile(153).getContents());
            InterfaceDefinition[] rectT = {loader.load(0, src.findFile(159).getContents()),
                    loader.load(0, src.findFile(160).getContents()), loader.load(0, src.findFile(161).getContents())};
            InterfaceDefinition textT = loader.load(0, src.findFile(144).getContents());
            InterfaceDefinition buttonT = loader.load(0, src.findFile(169).getContents());
            InterfaceDefinition labelT = loader.load(0, src.findFile(170).getContents());

            // 4-7: content panel (layer + 3 border rects)
            int PW = 306, PH = 254;
            add(comps, want, layer(layerT, 0, 182, 48, PW, PH));
            frame(comps, want, rectT, 4, PW, PH);
            // 8: section title, 9: divider
            add(comps, want, text(textT, 4, 10, 6, PW - 20, 16, HEADING_FONT, TITLE_COLOUR, ""));
            InterfaceDefinition divider = copy(rectT[1]);
            place(divider, 4, 10, 25, PW - 20, 1);
            divider.filled = true;
            add(comps, want, divider);
            // 10: scroll container for the rows, 11: scrollbar strip (clientscript 10532 -> 31 builds the bar)
            add(comps, want, layer(layerT, 4, 6, 30, SCROLL_W, SCROLL_H));
            add(comps, want, layer(layerT, 4, 6 + SCROLL_W + 2, 30, 16, SCROLL_H));
            // 12-51: rows (children of the scroll container)
            for (int r = 0; r < ROWS; r++)
                add(comps, want, text(textT, 10, 4, 2 + 12 * r, SCROLL_W - 8, 12, TEXT_FONT, TEXT_COLOUR, ""));
            // 52-59: 4 tabs (button + label)
            String[] tabs = {"How it works", "Tiers & upgrades", "Pet roster", "Active pet"};
            for (int t = 0; t < tabs.length; t++) {
                InterfaceDefinition button = copy(buttonT);
                place(button, 0, 24, 48 + 46 * t, 150, 43);
                button.clickMask = 2;
                button.actions = new String[]{"View"};
                button.onMouseOverListener = new Object[]{44, -2147483645, 24470};
                button.onMouseLeaveListener = new Object[]{44, -2147483645, 4525};
                add(comps, want, button);
                InterfaceDefinition label = copy(labelT);
                place(label, 0, 25, 50 + 46 * t, 148, 38);
                label.text = tabs[t];
                add(comps, want, label);
            }
            // 60-63: active pet box (layer + rects), 64-67: its rows
            int box = comps.size();
            add(comps, want, layer(layerT, 0, 24, 236, 150, 66));
            frame(comps, want, rectT, box, 150, 66);
            for (int r = 0; r < INFO_ROWS; r++)
                add(comps, want, text(textT, box, 7, 4 + 14 * r, 138, 14, TEXT_FONT, TEXT_COLOUR, ""));
            // 68: pet model (child of the content panel, over the right of the rows, left of the scrollbar;
            // shown only on the pet page, whose rows beside it are kept short)
            Archive a84 = index.getArchive(84);
            InterfaceDefinition model = copy(loader.load(0, a84.getFiles(storage.loadArchive(a84)).findFile(4).getContents()));
            model.contentType = 0; // 84:4 is contentType 328 (always the local player) -- must be cleared
            place(model, 4, 160, 30, 118, 140);
            model.modelId = -1;
            model.isHidden = true;
            add(comps, want, model);
            if (comps.size() != 69)
                throw new IllegalStateException("component count " + comps.size() + " != 69 (AscendedBestiary ids)");

            // Verify every component round-trips to exactly the intended definition.
            for (int c = 0; c < comps.size(); c++) {
                InterfaceDefinition got = loader.load((GROUP << 16) | c, comps.get(c));
                InterfaceDefinition exp = want.get(c);
                exp.id = (GROUP << 16) | c;
                for (Field f : InterfaceDefinition.class.getFields()) {
                    if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                    if (!Objects.deepEquals(f.get(got), f.get(exp)))
                        throw new IllegalStateException("component " + c + ": " + f.getName() + " " + render(f.get(got)) + " != " + render(f.get(exp)));
                }
                System.out.printf("%2d type=%d x=%d y=%d w=%d h=%d parent=%d %s%n", c, got.type, got.originalX, got.originalY,
                        got.originalWidth, got.originalHeight, got.parentId == -1 ? -1 : got.parentId & 0xFFFF,
                        got.text != null && !got.text.isEmpty() ? "\"" + got.text + "\"" : "");
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

    static void add(List<byte[]> comps, List<InterfaceDefinition> want, InterfaceDefinition d) {
        comps.add(encodeIf3(d));
        want.add(d);
    }

    static void frame(List<byte[]> comps, List<InterfaceDefinition> want, InterfaceDefinition[] rectT, int parent, int w, int h) {
        for (int k = 0; k < 3; k++) {
            InterfaceDefinition r = copy(rectT[k]);
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
        try {
            InterfaceDefinition d = new InterfaceDefinition();
            for (Field f : InterfaceDefinition.class.getFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                Object v = f.get(s);
                if (v instanceof Object[]) v = ((Object[]) v).clone();
                else if (v instanceof int[]) v = ((int[]) v).clone();
                f.set(d, v);
            }
            return d;
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    // Exact inverse of InterfaceLoader.decodeIf3 (client zelus/client/cache/.../InterfaceLoader.java).
    static byte[] encodeIf3(InterfaceDefinition d) {
        Out o = new Out();
        o.u8(255);
        o.u8(d.type);
        o.u16(d.contentType);
        o.u16(d.originalX);
        o.u16(d.originalY);
        o.u16(d.originalWidth);
        o.u16(d.originalHeight);
        o.u8(d.widthMode);
        o.u8(d.heightMode);
        o.u8(d.xPositionMode);
        o.u8(d.yPositionMode);
        o.u16(d.parentId == -1 ? 0xFFFF : d.parentId & 0xFFFF);
        o.u8(d.isHidden ? 1 : 0);
        if (d.type == 0) {
            o.u16(d.scrollWidth);
            o.u16(d.scrollHeight);
            o.u8(d.noClickThrough ? 1 : 0);
        }
        if (d.type == 5) {
            o.i32(d.spriteId);
            o.u16(d.textureId);
            o.u8(d.spriteTiling ? 1 : 0);
            o.u8(d.opacity);
            o.u8(d.borderType);
            o.i32(d.shadowColor);
            o.u8(d.flippedVertically ? 1 : 0);
            o.u8(d.flippedHorizontally ? 1 : 0);
        }
        if (d.type == 6) {
            o.u16(d.modelId == -1 ? 0xFFFF : d.modelId);
            o.u16(d.offsetX2d);
            o.u16(d.offsetY2d);
            o.u16(d.rotationX);
            o.u16(d.rotationZ);
            o.u16(d.rotationY);
            o.u16(d.modelZoom);
            o.u16(d.animation == -1 ? 0xFFFF : d.animation);
            o.u8(d.orthogonal ? 1 : 0);
            o.u16(0);
            if (d.widthMode != 0) o.u16(d.modelHeightOverride);
            if (d.heightMode != 0) o.u16(0);
        }
        if (d.type == 4) {
            o.u16(d.fontId == -1 ? 0xFFFF : d.fontId);
            o.str(d.text);
            o.u8(d.lineHeight);
            o.u8(d.xTextAlignment);
            o.u8(d.yTextAlignment);
            o.u8(d.textShadowed ? 1 : 0);
            o.i32(d.textColor);
        }
        if (d.type == 3) {
            o.i32(d.textColor);
            o.u8(d.filled ? 1 : 0);
            o.u8(d.opacity);
        }
        if (d.type == 9) {
            o.u8(d.lineWidth);
            o.i32(d.textColor);
            o.u8(d.lineDirection ? 1 : 0);
        }
        o.u24(d.clickMask);
        o.str(d.name);
        String[] acts = d.actions == null ? new String[0] : d.actions;
        o.u8(acts.length);
        for (String s : acts) o.str(s);
        o.u8(d.dragDeadZone);
        o.u8(d.dragDeadTime);
        o.u8(d.dragRenderBehavior ? 1 : 0);
        o.str(d.targetVerb);
        for (Object[] l : new Object[][]{d.onLoadListener, d.onMouseOverListener, d.onMouseLeaveListener, d.onTargetLeaveListener,
                d.onTargetEnterListener, d.onVarTransmitListener, d.onInvTransmitListener, d.onStatTransmitListener, d.onTimerListener,
                d.onOpListener, d.onMouseRepeatListener, d.onClickListener, d.onClickRepeatListener, d.onReleaseListener,
                d.onHoldListener, d.onDragListener, d.onDragCompleteListener, d.onScrollWheelListener}) {
            if (l == null) {
                o.u8(0);
                continue;
            }
            o.u8(l.length);
            for (Object v : l) {
                if (v instanceof Integer) {
                    o.u8(0);
                    o.i32((Integer) v);
                } else {
                    o.u8(1);
                    o.str((String) v);
                }
            }
        }
        for (int[] tr : new int[][]{d.varTransmitTriggers, d.invTransmitTriggers, d.statTransmitTriggers}) {
            if (tr == null) {
                o.u8(0);
                continue;
            }
            o.u8(tr.length);
            for (int v : tr) o.i32(v);
        }
        return o.toByteArray();
    }

    static class Out extends ByteArrayOutputStream {
        void u8(int v) { write(v & 0xFF); }
        void u16(int v) { u8(v >> 8); u8(v); }
        void u24(int v) { u8(v >> 16); u16(v); }
        void i32(int v) { u16(v >>> 16); u16(v); }
        void str(String s) {
            byte[] b = (s == null ? "" : s).getBytes(CP1252);
            write(b, 0, b.length);
            u8(0);
        }
    }

    static String render(Object v) {
        if (v == null) return "null";
        if (v instanceof Object[]) return Arrays.deepToString((Object[]) v);
        if (v instanceof int[]) return Arrays.toString((int[]) v);
        return v.toString();
    }
}

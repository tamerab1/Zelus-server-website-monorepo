import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.InterfaceDefinition;
import net.runelite.cache.definitions.SpriteDefinition;
import net.runelite.cache.definitions.loaders.InterfaceLoader;
import net.runelite.cache.definitions.loaders.SpriteLoader;
import net.runelite.cache.fs.*;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.util.List;

// Read-only layout mock of an if3 interface group (2026-10-18, Soul Tree round 2): draws rectangles, sprites, text
// (Java fonts standing in for the RS ones, <col=..> and <br> honoured) and model boxes at their absolute positions,
// after applying a state file that stands in for the server's packets:
//   text <comp> <string...> | hide <comp> 0|1 | colour <comp> <hex> | sprite <comp> <id>
// Only absolute positioning / fixed sizes are handled (what IfKit builds); the 1002 frame is drawn as a stone box.
// Usage: <cachePath> <group> <stateFile|-> <out.png> [scale]
public class MockInterface {
    public static void main(String[] args) throws Exception {
        int group = Integer.parseInt(args[1]);
        int scale = args.length > 4 ? Integer.parseInt(args[4]) : 2;
        try (Store store = new Store(new File(args[0]))) {
            store.load();
            Storage storage = store.getStorage();
            Index ifs = store.getIndex(IndexType.INTERFACES);
            Archive arch = ifs.getArchive(group);
            ArchiveFiles files = arch.getFiles(storage.loadArchive(arch));
            List<InterfaceDefinition> comps = new ArrayList<>();
            for (FSFile f : files.getFiles())
                comps.add(new InterfaceLoader().load(group << 16 | f.getFileId(), f.getContents()));
            comps.sort(Comparator.comparingInt(d -> d.id & 0xffff));
            if (!args[2].equals("-"))
                for (String line : Files.readAllLines(new File(args[2]).toPath())) {
                    if (line.isBlank() || line.startsWith("#")) continue;
                    String[] p = line.split(" ", 3);
                    InterfaceDefinition d = comps.get(Integer.parseInt(p[1]));
                    switch (p[0]) {
                        case "text" -> d.text = p.length > 2 ? p[2] : "";
                        case "hide" -> d.isHidden = p[2].equals("1");
                        case "colour" -> d.textColor = Integer.parseInt(p[2], 16);
                        case "sprite" -> d.spriteId = Integer.parseInt(p[2]);
                    }
                }
            Index sprites = store.getIndex(IndexType.SPRITES);
            BufferedImage img = new BufferedImage(488 * scale, 334 * scale, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.scale(scale, scale);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
            g.setColor(new Color(0x2b261f));
            g.fillRect(0, 0, 488, 334);
            g.setColor(new Color(0x5a4d3a));
            g.drawRect(2, 2, 483, 329);
            for (InterfaceDefinition d : comps) {
                if (hidden(d, comps) || (d.id & 0xffff) <= 1)
                    continue;
                int[] pos = abs(d, comps);
                int x = pos[0], y = pos[1], w = d.originalWidth, h = d.originalHeight;
                float alpha = 1 - d.opacity / 255f;
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0, Math.min(1, alpha))));
                Shape oldClip = g.getClip();
                int[] clip = clipOf(d, comps);
                if (clip != null) g.clipRect(clip[0], clip[1], clip[2], clip[3]);
                switch (d.type) {
                    case 3 -> {
                        g.setColor(new Color(d.textColor));
                        if (d.filled) g.fillRect(x, y, w, h); else g.drawRect(x, y, w - 1, h - 1);
                    }
                    case 5 -> {
                        if (d.spriteId >= 0 && sprites.getArchive(d.spriteId) != null) {
                            Archive a = sprites.getArchive(d.spriteId);
                            SpriteDefinition sd = new SpriteLoader().load(d.spriteId, a.decompress(storage.loadArchive(a)))[0];
                            BufferedImage s = new BufferedImage(sd.getWidth(), sd.getHeight(), BufferedImage.TYPE_INT_ARGB);
                            int[] px = sd.getPixels();
                            for (int i = 0; i < px.length; i++)
                                if (px[i] != 0) s.setRGB(i % sd.getWidth(), i / sd.getWidth(), 0xff000000 | px[i]);
                            if (d.spriteTiling) {
                                for (int ty = 0; ty < h; ty += sd.getHeight())
                                    for (int tx = 0; tx < w; tx += sd.getWidth())
                                        g.drawImage(s, x + tx + sd.getOffsetX(), y + ty + sd.getOffsetY(), null);
                            } else
                                g.drawImage(s, x + sd.getOffsetX(), y + sd.getOffsetY(), null);
                        }
                    }
                    case 4 -> text(g, d, x, y, w, h);
                    case 6 -> {
                        g.setColor(new Color(0x6a7a80));
                        g.drawLine(x + w / 2 - 30, y + h - 8, x + w / 2 + 30, y + 8);
                        g.drawLine(x + w / 2 - 29, y + h - 8, x + w / 2 + 31, y + 8);
                        g.drawString("model", x + w / 2 - 12, y + h / 2);
                    }
                }
                g.setClip(oldClip);
                g.setComposite(AlphaComposite.SrcOver);
            }
            g.dispose();
            ImageIO.write(img, "png", new File(args[3]));
            System.out.println("wrote " + args[3]);
        }
    }

    static boolean hidden(InterfaceDefinition d, List<InterfaceDefinition> comps) {
        for (InterfaceDefinition c = d; c != null; c = parent(c, comps))
            if (c.isHidden) return true;
        return false;
    }

    static InterfaceDefinition parent(InterfaceDefinition d, List<InterfaceDefinition> comps) {
        return d.parentId == -1 || (d.parentId >>> 16) != (d.id >>> 16) ? null : comps.get(d.parentId & 0xffff);
    }

    static int[] abs(InterfaceDefinition d, List<InterfaceDefinition> comps) {
        int x = 0, y = 0;
        for (InterfaceDefinition c = d; c != null; c = parent(c, comps)) {
            if ((c.id & 0xffff) == 0) break;
            x += c.originalX;
            y += c.originalY;
        }
        return new int[]{x, y};
    }

    /** The nearest layer ancestor's box (layers clip their children). */
    static int[] clipOf(InterfaceDefinition d, List<InterfaceDefinition> comps) {
        for (InterfaceDefinition c = parent(d, comps); c != null; c = parent(c, comps)) {
            if ((c.id & 0xffff) == 0) return null;
            if (c.type == 0) {
                int[] p = abs(c, comps);
                return new int[]{p[0], p[1], c.originalWidth, c.originalHeight};
            }
        }
        return null;
    }

    /** Greedy word wrap for multi-line blocks (as the client does), carrying the open colour tag onto the next line. */
    static List<String> wrap(String line, FontMetrics fm, int w, boolean enabled) {
        List<String> out = new ArrayList<>();
        if (!enabled || fm.stringWidth(line.replaceAll("<[^>]*>", "")) <= w) {
            out.add(line);
            return out;
        }
        StringBuilder cur = new StringBuilder();
        String openCol = "";
        for (String word : line.split(" ")) {
            String trial = cur.length() == 0 ? word : cur + " " + word;
            if (fm.stringWidth(trial.replaceAll("<[^>]*>", "")) > w && cur.length() > 0) {
                out.add(cur.toString());
                cur = new StringBuilder(openCol + word);
            } else
                cur = new StringBuilder(trial);
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("<(/?col)(=[0-9a-fA-F]+)?>").matcher(word);
            while (m.find())
                openCol = m.group(1).equals("col") ? m.group() : "";
        }
        out.add(cur.toString());
        return out;
    }

    static void text(Graphics2D g, InterfaceDefinition d, int x, int y, int w, int h) {
        if (d.text == null || d.text.isEmpty()) return;
        boolean big = d.fontId == 496 || d.fontId == 495;
        g.setFont(new Font("Dialog", big ? Font.BOLD : Font.PLAIN, big ? 11 : 10));
        FontMetrics fm = g.getFontMetrics();
        List<String> wrapped = new ArrayList<>();
        for (String raw : d.text.split("<br>"))
            wrapped.addAll(wrap(raw, fm, w, d.yTextAlignment == 0 && d.lineHeight > 0));
        String[] lines = wrapped.toArray(new String[0]);
        int lh = d.lineHeight > 0 ? d.lineHeight : fm.getHeight();
        int total = lines.length * lh;
        int ty = d.yTextAlignment == 1 ? y + (h - total) / 2 : d.yTextAlignment == 2 ? y + h - total : y;
        for (String line : lines) {
            List<Object[]> runs = new ArrayList<>();
            int colour = d.textColor;
            String rest = line;
            while (!rest.isEmpty()) {
                int lt = rest.indexOf('<');
                if (lt != 0) {
                    String s = lt < 0 ? rest : rest.substring(0, lt);
                    runs.add(new Object[]{s, colour});
                    rest = lt < 0 ? "" : rest.substring(lt);
                    continue;
                }
                int gt = rest.indexOf('>');
                String tag = rest.substring(1, gt);
                if (tag.startsWith("col=")) colour = Integer.parseInt(tag.substring(4), 16);
                else if (tag.equals("/col")) colour = d.textColor;
                rest = rest.substring(gt + 1);
            }
            int width = 0;
            for (Object[] r : runs) width += fm.stringWidth((String) r[0]);
            int tx = d.xTextAlignment == 1 ? x + (w - width) / 2 : d.xTextAlignment == 2 ? x + w - width : x;
            int baseline = ty + (lh + fm.getAscent() - fm.getDescent()) / 2;
            for (Object[] r : runs) {
                if (d.textShadowed) {
                    g.setColor(Color.BLACK);
                    g.drawString((String) r[0], tx + 1, baseline + 1);
                }
                g.setColor(new Color((int) r[1]));
                g.drawString((String) r[0], tx, baseline);
                tx += fm.stringWidth((String) r[0]);
            }
            if (width > w) {
                g.setColor(Color.MAGENTA);
                g.drawRect(x, ty, w - 1, lh - 1); // overflow marker
            }
            ty += lh;
        }
    }
}

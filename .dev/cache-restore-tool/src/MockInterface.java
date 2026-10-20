import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.FontDefinition;
import net.runelite.cache.definitions.InterfaceDefinition;
import net.runelite.cache.definitions.SpriteDefinition;
import net.runelite.cache.definitions.loaders.InterfaceLoader;
import net.runelite.cache.definitions.loaders.SpriteLoader;
import net.runelite.cache.fs.*;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.*;
import java.util.List;

// Read-only layout mock of an if3 interface group (2026-10-18, Soul Tree round 2; 2026-10-20 rewrite for the interface
// redesign): draws rectangles, sprites and text at their absolute positions the way the client does, after applying a
// state file that stands in for the server's packets:
//   text <comp> <string...> | hide <comp> 0|1 | colour <comp> <hex> | sprite <comp> <id>
// Text uses the cache's own fonts (fontmetrics advances + glyph sprites, the client's word wrap at the component width
// and its vertical alignment), <col=>, <br>, <shad> honoured. Non-tiled sprites are stretched to the component like the
// client (the whole canvas incl. offsets). Model components are a grey box. Overflow is marked in magenta: text whose
// wrapped lines don't fit the component's height, and components that stick out of their parent layer.
// Only absolute positioning / fixed sizes are handled (what IfKit builds).
// Usage: <cachePath> <group> <stateFile|-> <out.png> [scale]
public class MockInterface {
    static final Charset CP1252 = Charset.forName("windows-1252");
    static final Map<Integer, Glyphs> FONTS = new HashMap<>();
    static Store store;
    static Index sprites;
    static final List<int[]> overflow = new ArrayList<>();

    record Glyphs(FontDefinition metrics, SpriteDefinition[] glyphs, int maxDescent) {
        int width(String s) {
            int w = 0;
            for (byte b : s.getBytes(CP1252)) w += metrics.advances[b & 0xff];
            return w;
        }
    }

    public static void main(String[] args) throws Exception {
        int group = Integer.parseInt(args[1]);
        int scale = args.length > 4 ? Integer.parseInt(args[4]) : 2;
        try (Store s = new Store(new File(args[0]))) {
            store = s;
            store.load();
            Storage storage = store.getStorage();
            sprites = store.getIndex(IndexType.SPRITES);
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
                    String[] p = (line + " ").split(" ", 3);
                    InterfaceDefinition d = comps.get(Integer.parseInt(p[1]));
                    switch (p[0]) {
                        case "text" -> d.text = p[2].endsWith(" ") ? p[2].substring(0, p[2].length() - 1) : p[2];
                        case "hide" -> d.isHidden = p[2].trim().equals("1");
                        case "colour" -> d.textColor = Integer.parseInt(p[2].trim(), 16);
                        case "sprite" -> d.spriteId = Integer.parseInt(p[2].trim());
                        case "scroll" -> d.scrollHeight = 1; // a scroll container: its children may run past it
                    }
                }
            BufferedImage img = new BufferedImage(512, 334, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setColor(new Color(0x1c1814));
            g.fillRect(0, 0, 512, 334);
            for (InterfaceDefinition d : comps) {
                if (hidden(d, comps) || (d.id & 0xffff) == 0)
                    continue;
                int[] pos = abs(d, comps);
                int x = pos[0], y = pos[1], w = d.originalWidth, h = d.originalHeight;
                float alpha = 1 - d.opacity / 255f;
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0, Math.min(1, alpha))));
                Shape oldClip = g.getClip();
                int[] clip = clipOf(d, comps);
                if (clip != null) {
                    g.clipRect(clip[0], clip[1], clip[2], clip[3]);
                    if (d.type != 0 && (x < clip[0] || y < clip[1] || x + w > clip[0] + clip[2] || y + h > clip[1] + clip[3])
                            && !scrolls(d, comps))
                        overflow.add(new int[]{x, y, w, h, d.id & 0xffff});
                }
                switch (d.type) {
                    case 3 -> {
                        g.setColor(new Color(d.textColor));
                        if (d.filled) g.fillRect(x, y, w, h); else g.drawRect(x, y, w - 1, h - 1);
                    }
                    case 5 -> sprite(g, d, x, y, w, h);
                    case 4 -> text(g, d, x, y, w, h);
                    case 6 -> {
                        g.setColor(new Color(0x55606a));
                        g.drawRect(x, y, w - 1, h - 1);
                    }
                }
                g.setClip(oldClip);
                g.setComposite(AlphaComposite.SrcOver);
            }
            g.setColor(Color.MAGENTA);
            for (int[] o : overflow) {
                g.drawRect(o[0], o[1], Math.max(1, o[2] - 1), Math.max(1, o[3] - 1));
                System.out.println("OVERFLOW comp " + o[4] + " at " + o[0] + "," + o[1] + " " + o[2] + "x" + o[3]);
            }
            g.dispose();
            BufferedImage out = new BufferedImage(512 * scale, 334 * scale, BufferedImage.TYPE_INT_ARGB);
            Graphics2D og = out.createGraphics();
            og.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            og.drawImage(img, 0, 0, 512 * scale, 334 * scale, null);
            og.dispose();
            ImageIO.write(out, "png", new File(args[3]));
            System.out.println("wrote " + args[3] + " (" + overflow.size() + " overflow marks)");
        }
    }

    /** A layer with a scroll height (its children legitimately run past its box). */
    static boolean scrolls(InterfaceDefinition d, List<InterfaceDefinition> comps) {
        for (InterfaceDefinition c = parent(d, comps); c != null; c = parent(c, comps))
            if (c.type == 0 && c.scrollHeight > 0) return true;
        return false;
    }

    static SpriteDefinition[] load(int id) {
        try {
            Archive a = sprites.getArchive(id);
            if (a == null) return null;
            return new SpriteLoader().load(id, a.decompress(store.getStorage().loadArchive(a)));
        } catch (Exception e) {
            return null;
        }
    }

    /** The sprite on its full canvas (offsets applied), as the client sees it. */
    static BufferedImage canvas(SpriteDefinition sd) {
        int cw = Math.max(sd.getMaxWidth(), sd.getOffsetX() + sd.getWidth()), ch = Math.max(sd.getMaxHeight(), sd.getOffsetY() + sd.getHeight());
        BufferedImage s = new BufferedImage(Math.max(1, cw), Math.max(1, ch), BufferedImage.TYPE_INT_ARGB);
        int[] px = sd.getPixels();
        for (int i = 0; i < px.length; i++)
            if (px[i] != 0) s.setRGB(sd.getOffsetX() + i % sd.getWidth(), sd.getOffsetY() + i / sd.getWidth(), 0xff000000 | px[i]);
        return s;
    }

    static void sprite(Graphics2D g, InterfaceDefinition d, int x, int y, int w, int h) {
        if (d.spriteId < 0) return;
        SpriteDefinition[] defs = load(d.spriteId);
        if (defs == null || defs.length == 0) return;
        BufferedImage s = canvas(defs[0]);
        if (d.spriteTiling) {
            for (int ty = 0; ty < h; ty += s.getHeight())
                for (int tx = 0; tx < w; tx += s.getWidth())
                    g.drawImage(s, x + tx, y + ty, null);
        } else if (s.getWidth() == w && s.getHeight() == h) {
            g.drawImage(s, x, y, null);
        } else {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(s, x, y, w, h, null);
        }
    }

    static Glyphs font(int id) {
        return FONTS.computeIfAbsent(id, k -> {
            try {
                Archive fa = store.getIndex(IndexType.FONTS).getArchive(k);
                if (fa == null) return null;
                ArchiveFiles ff = fa.getFiles(store.getStorage().loadArchive(fa));
                FontDefinition metrics = new net.runelite.cache.definitions.loaders.FontLoader().load(ff.getFiles().iterator().next().getContents());
                SpriteDefinition[] glyphs = load(k);
                if (metrics == null || glyphs == null) return null;
                int descent = 0;
                for (SpriteDefinition sd : glyphs)
                    descent = Math.max(descent, sd.getOffsetY() + sd.getHeight() - metrics.ascent);
                return new Glyphs(metrics, glyphs, descent);
            } catch (Exception e) {
                return null;
            }
        });
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

    /** The intersection of every layer ancestor's box (each layer clips its children). */
    static int[] clipOf(InterfaceDefinition d, List<InterfaceDefinition> comps) {
        int[] r = null;
        for (InterfaceDefinition c = parent(d, comps); c != null; c = parent(c, comps)) {
            if ((c.id & 0xffff) == 0) break;
            if (c.type == 0) {
                int[] p = abs(c, comps);
                int x1 = p[0], y1 = p[1], x2 = p[0] + c.originalWidth, y2 = p[1] + c.originalHeight;
                if (r != null) {
                    x1 = Math.max(x1, r[0]);
                    y1 = Math.max(y1, r[1]);
                    x2 = Math.min(x2, r[0] + r[2]);
                    y2 = Math.min(y2, r[1] + r[3]);
                }
                r = new int[]{x1, y1, Math.max(0, x2 - x1), Math.max(0, y2 - y1)};
            }
        }
        return r;
    }

    /** The client's line breaking: <br> always, then greedy word wrap at the component width (tags take no space). */
    static List<String> wrap(String text, Glyphs f, int w) {
        List<String> out = new ArrayList<>();
        for (String raw : text.split("<br>", -1)) {
            if (w <= 0 || f.width(strip(raw)) <= w) {
                out.add(raw);
                continue;
            }
            StringBuilder cur = new StringBuilder();
            String openCol = "";
            for (String word : raw.split(" ")) {
                String trial = cur.length() == 0 ? word : cur + " " + word;
                if (f.width(strip(trial)) > w && cur.length() > 0) {
                    out.add(cur.toString());
                    cur = new StringBuilder(openCol + word);
                } else
                    cur = new StringBuilder(trial);
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("<(/?col)(=[0-9a-fA-F]+)?>").matcher(word);
                while (m.find())
                    openCol = m.group(1).equals("col") ? m.group() : "";
            }
            out.add(cur.toString());
        }
        return out;
    }

    static String strip(String s) {
        return s.replaceAll("<[^>]*>", "");
    }

    static void text(Graphics2D g, InterfaceDefinition d, int x, int y, int w, int h) {
        if (d.text == null || d.text.isEmpty()) return;
        Glyphs f = font(d.fontId);
        if (f == null) return;
        List<String> lines = wrap(d.text, f, w);
        int ascent = f.metrics.ascent;
        int lh = d.lineHeight > 0 ? d.lineHeight : ascent;
        int n = lines.size();
        int base = d.yTextAlignment == 0 ? y + ascent
                : d.yTextAlignment == 1 ? y + (h - ascent - f.maxDescent - lh * (n - 1)) / 2 + ascent
                : y + h - f.maxDescent - lh * (n - 1);
        int top = base - ascent, bottom = base + lh * (n - 1) + f.maxDescent;
        boolean tooTall = top < y - 1 || bottom > y + h + 1;
        boolean tooWide = false;
        for (String line : lines) {
            int width = f.width(strip(line));
            tooWide |= width > w;
            int tx = d.xTextAlignment == 1 ? x + (w - width) / 2 : d.xTextAlignment == 2 ? x + w - width : x;
            int colour = d.textColor;
            boolean shadow = d.textShadowed;
            String rest = line;
            while (!rest.isEmpty()) {
                int lt = rest.indexOf('<');
                if (lt != 0) {
                    String run = lt < 0 ? rest : rest.substring(0, lt);
                    tx = draw(g, f, run, tx, base, colour, shadow);
                    rest = lt < 0 ? "" : rest.substring(lt);
                    continue;
                }
                int gt = rest.indexOf('>');
                if (gt < 0) break;
                String tag = rest.substring(1, gt);
                if (tag.startsWith("col=")) colour = Integer.parseInt(tag.substring(4), 16);
                else if (tag.equals("/col")) colour = d.textColor;
                else if (tag.startsWith("shad")) shadow = true;
                else if (tag.equals("/shad")) shadow = d.textShadowed;
                rest = rest.substring(gt + 1);
            }
            base += lh;
        }
        if (tooTall || tooWide)
            overflow.add(new int[]{x, y, w, h, d.id & 0xffff});
    }

    static int draw(Graphics2D g, Glyphs f, String s, int x, int base, int colour, boolean shadow) {
        for (byte b : s.getBytes(CP1252)) {
            int c = b & 0xff;
            if (c < f.glyphs.length && c != ' ') {
                SpriteDefinition gl = f.glyphs[c];
                int gx = x + gl.getOffsetX(), gy = base - f.metrics.ascent + gl.getOffsetY();
                if (shadow) glyph(g, gl, gx + 1, gy + 1, 0);
                glyph(g, gl, gx, gy, colour);
            }
            x += f.metrics.advances[c];
        }
        return x;
    }

    static void glyph(Graphics2D g, SpriteDefinition gl, int x, int y, int colour) {
        int[] px = gl.getPixels();
        g.setColor(new Color(colour));
        for (int i = 0; i < px.length; i++)
            if (px[i] != 0) g.fillRect(x + i % gl.getWidth(), y + i / gl.getWidth(), 1, 1);
    }
}

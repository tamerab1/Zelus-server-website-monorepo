import net.runelite.cache.FontManager;
import net.runelite.cache.SpriteManager;
import net.runelite.cache.definitions.FontDefinition;
import net.runelite.cache.definitions.InterfaceDefinition;
import net.runelite.cache.definitions.SpriteDefinition;
import net.runelite.cache.definitions.loaders.InterfaceLoader;
import net.runelite.cache.fs.Store;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.util.List;

/**
 * Pixel preview of an exported rev 231 interface (component .dat files) with the real cache sprites and the real OSRS
 * bitmap fonts (p11 494, p12 495, b12 496, q8 497): <col=>, <br>, <lt>/<gt>, shadows, alignment, line height; rectangles
 * with opacity, tiled / stretched sprites with opacity, models as a dashed box, items from item_icons when present.
 * Sprites are taken from the override dirs first (art not in the cache yet), then the cache.
 * Hidden components are skipped unless listed in show=; scroll layers clip their children.
 * Usage: IfPreview <cache241> <dir> <group> <out.png> [scale=2] [png=dir;dir] [show=1,2] [hide=1,2]
 *        [text=id:string|id:string] [item=id:itemId|...] [icons=dir] [w=512] [h=334]
 */
public class IfPreview {
	static Map<Integer, Map<Integer, SpriteDefinition>> sprites = new HashMap<>();
	static Map<Integer, BufferedImage> overrides = new HashMap<>();
	static Map<Integer, FontDefinition> fonts = new HashMap<>();
	static File iconDir;

	static boolean loaded;

	public static void main(String[] a) throws Exception {
		String[] opts = Arrays.copyOfRange(a, 4, a.length);
		loadOnce(a[0], opts);
		render(a[1], Integer.parseInt(a[2]), a[3], opts);
	}

	static Map<String, String> parse(String[] o) {
		Map<String, String> opt = new HashMap<>();
		for (String s : o) {
			int e = s.indexOf('=');
			if (e > 0) opt.put(s.substring(0, e), s.substring(e + 1));
		}
		return opt;
	}

	static void loadOnce(String cache, String[] o) throws Exception {
		if (loaded) return;
		loaded = true;
		Map<String, String> opt = parse(o);
		if (opt.containsKey("icons")) iconDir = new File(opt.get("icons"));
		if (opt.containsKey("png"))
			for (String d : opt.get("png").split(";"))
				for (File f : new File(d).listFiles())
					if (f.getName().matches("\\d+\\.png"))
						overrides.putIfAbsent(Integer.parseInt(f.getName().replace(".png", "")), ImageIO.read(f));
		Store store = new Store(new File(cache));
		store.load();
		SpriteManager sm = new SpriteManager(store);
		sm.load();
		for (SpriteDefinition s : sm.getSprites())
			sprites.computeIfAbsent(s.getId(), k -> new HashMap<>()).put(s.getFrame(), s);
		FontManager fm = new FontManager(store);
		fm.load();
		String[][] names = { { "494", "p11_full" }, { "495", "p12_full" }, { "496", "b12_full" }, { "497", "q8_full" }, { "646", "quill_caps_large" }, { "1446", "verdana_13pt_bold" }, { "1447", "verdana_15pt_regular" } };
		for (String[] n : names) {
			FontDefinition f = fm.findFontByName(n[1]);
			if (f != null) fonts.put(Integer.parseInt(n[0]), f);
		}
		store.close();
	}

	/** Re-reads the override pngs (art regenerated between renders). */
	static void reloadOverrides(String dirs) throws Exception {
		for (String d : dirs.split(";"))
			for (File f : new File(d).listFiles())
				if (f.getName().matches("\\d+\\.png"))
					overrides.put(Integer.parseInt(f.getName().replace(".png", "")), ImageIO.read(f));
	}

	static void render(String dirName, int group, String outFile, String[] o) throws Exception {
		Map<String, String> opt = parse(o);
		String[] a = { null, dirName, String.valueOf(group), outFile };
		int scale = Integer.parseInt(opt.getOrDefault("scale", "2"));
		int W = Integer.parseInt(opt.getOrDefault("w", "512")), H = Integer.parseInt(opt.getOrDefault("h", "334"));
		Set<Integer> show = ids(opt.get("show")), hide = ids(opt.get("hide"));
		Map<Integer, String> texts = new HashMap<>();
		if (opt.containsKey("text"))
			for (String s : opt.get("text").split("\\|")) {
				int c = s.indexOf(':');
				texts.put(Integer.parseInt(s.substring(0, c)), s.substring(c + 1));
			}
		Map<Integer, Integer> items = new HashMap<>();
		if (opt.containsKey("item"))
			for (String s : opt.get("item").split("\\|")) {
				String[] p = s.split(":");
				items.put(Integer.parseInt(p[0]), Integer.parseInt(p[1]));
			}
		File dir = new File(a[1]);
		TreeMap<Integer, InterfaceDefinition> comps = new TreeMap<>();
		for (File f : dir.listFiles())
			if (f.getName().endsWith(".dat"))
				comps.put(Integer.parseInt(f.getName().replace(".dat", "")), new InterfaceLoader().load(group << 16 | Integer.parseInt(f.getName().replace(".dat", "")), Files.readAllBytes(f.toPath())));

		BufferedImage im = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = im.createGraphics();
		g.setColor(new Color(0x2b2b2b));
		g.fillRect(0, 0, W, H);
		// children in tree order: draw a component, then its children (the client's order)
		Map<Integer, List<Integer>> kids = new TreeMap<>();
		List<Integer> roots = new ArrayList<>();
		for (var e : comps.entrySet()) {
			int p = e.getValue().parentId;
			if (p == -1 || (p & 0xffff) == e.getKey()) roots.add(e.getKey());
			else kids.computeIfAbsent(p & 0xffff, k -> new ArrayList<>()).add(e.getKey());
		}
		for (int r : roots)
			draw(g, comps, kids, r, 0, 0, W, H, new Rectangle(0, 0, W, H), show, hide, texts, items);
		g.dispose();
		BufferedImage out = new BufferedImage(W * scale, H * scale, BufferedImage.TYPE_INT_RGB);
		Graphics2D og = out.createGraphics();
		og.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		og.drawImage(im, 0, 0, W * scale, H * scale, null);
		og.dispose();
		ImageIO.write(out, "png", new File(a[3]));
	}

	static Set<Integer> ids(String s) {
		Set<Integer> r = new HashSet<>();
		if (s != null && !s.isEmpty())
			for (String x : s.split(",")) r.add(Integer.parseInt(x.trim()));
		return r;
	}

	static void draw(Graphics2D g, Map<Integer, InterfaceDefinition> comps, Map<Integer, List<Integer>> kids, int id, int px, int py,
			int pw, int ph, Rectangle clip, Set<Integer> show, Set<Integer> hide, Map<Integer, String> texts, Map<Integer, Integer> items) {
		InterfaceDefinition d = comps.get(id);
		if ((d.isHidden && !show.contains(id)) || hide.contains(id))
			return;
		int w = d.widthMode == 1 ? pw - d.originalWidth : d.widthMode == 2 ? pw * d.originalWidth >> 14 : d.originalWidth;
		int h = d.heightMode == 1 ? ph - d.originalHeight : d.heightMode == 2 ? ph * d.originalHeight >> 14 : d.originalHeight;
		int x = d.xPositionMode == 1 ? px + (pw - w) / 2 + d.originalX : d.xPositionMode == 2 ? px + pw - w - d.originalX
				: d.xPositionMode == 3 ? px + (pw * d.originalX >> 14) : px + d.originalX;
		int y = d.yPositionMode == 1 ? py + (ph - h) / 2 + d.originalY : d.yPositionMode == 2 ? py + ph - h - d.originalY
				: d.yPositionMode == 3 ? py + (ph * d.originalY >> 14) : py + d.originalY;
		Shape old = g.getClip();
		g.setClip(clip);
		int alpha = 255 - (d.opacity & 0xff);
		if (d.type == 3) {
			g.setColor(new Color(d.textColor >> 16 & 0xff, d.textColor >> 8 & 0xff, d.textColor & 0xff, alpha));
			if (d.filled) g.fillRect(x, y, w, h);
			else g.drawRect(x, y, w - 1, h - 1);
		} else if (d.type == 9) {
			g.setColor(new Color(d.textColor >> 16 & 0xff, d.textColor >> 8 & 0xff, d.textColor & 0xff, alpha));
			g.setStroke(new BasicStroke(Math.max(1, d.lineWidth)));
			if (d.lineDirection) g.drawLine(x, y + h, x + w, y);
			else g.drawLine(x, y, x + w, y + h);
		} else if (d.type == 5) {
			Integer item = items.get(id);
			if (item != null) drawItem(g, item, x, y, w, h);
			else if (d.spriteId >= 0) drawSprite(g, d, x, y, w, h, alpha);
		} else if (d.type == 6) {
			g.setColor(new Color(90, 160, 255, 70));
			g.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1, new float[] { 2, 2 }, 0));
			g.drawRect(x, y, w - 1, h - 1);
			g.setStroke(new BasicStroke());
		} else if (d.type == 4) {
			String s = texts.containsKey(id) ? texts.get(id) : d.text;
			if (s != null && !s.isEmpty()) drawText(g, d, s, x, y, w, h);
		}
		g.setClip(old);
		if (d.type == 0 && kids.containsKey(id)) {
			Rectangle c = clip.intersection(new Rectangle(x, y, w, h));
			if (d.scrollHeight <= 0 && d.scrollWidth <= 0 && !(d.type == 0)) c = clip;
			for (int k : kids.get(id))
				draw(g, comps, kids, k, x, y, w, h, c, show, hide, texts, items);
		}
	}

	static BufferedImage spriteImage(int id, int frame) {
		if (frame == 0 && overrides.containsKey(id)) return overrides.get(id);
		Map<Integer, SpriteDefinition> fs = sprites.get(id);
		if (fs == null || fs.get(frame) == null) return null;
		SpriteDefinition s = fs.get(frame);
		if (s.getWidth() <= 0 || s.getHeight() <= 0) return null;
		BufferedImage b = new BufferedImage(s.getMaxWidth(), s.getMaxHeight(), BufferedImage.TYPE_INT_ARGB);
		int[] px = s.getPixels();
		for (int yy = 0; yy < s.getHeight(); yy++)
			for (int xx = 0; xx < s.getWidth(); xx++) {
				int p = px[yy * s.getWidth() + xx];
				if (p != 0) b.setRGB(xx + s.getOffsetX(), yy + s.getOffsetY(), 0xff000000 | p);
			}
		return b;
	}

	static void drawSprite(Graphics2D g, InterfaceDefinition d, int x, int y, int w, int h, int alpha) {
		BufferedImage b = spriteImage(d.spriteId, 0);
		if (b == null) {
			g.setColor(new Color(255, 0, 255, 90));
			g.fillRect(x, y, w, h);
			return;
		}
		if (d.flippedHorizontally || d.flippedVertically) {
			BufferedImage f = new BufferedImage(b.getWidth(), b.getHeight(), BufferedImage.TYPE_INT_ARGB);
			for (int yy = 0; yy < b.getHeight(); yy++)
				for (int xx = 0; xx < b.getWidth(); xx++)
					f.setRGB(d.flippedHorizontally ? b.getWidth() - 1 - xx : xx, d.flippedVertically ? b.getHeight() - 1 - yy : yy, b.getRGB(xx, yy));
			b = f;
		}
		Composite oc = g.getComposite();
		if (alpha < 255) g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha / 255f));
		if (d.spriteTiling) {
			Shape old = g.getClip();
			g.clip(new Rectangle(x, y, w, h));
			for (int yy = y; yy < y + h; yy += b.getHeight())
				for (int xx = x; xx < x + w; xx += b.getWidth())
					g.drawImage(b, xx, yy, null);
			g.setClip(old);
		} else
			g.drawImage(b, x, y, w, h, null);
		g.setComposite(oc);
	}

	static void drawItem(Graphics2D g, int item, int x, int y, int w, int h) {
		try {
			File f = iconDir == null ? null : new File(iconDir, item + ".png");
			if (f != null && f.isFile()) {
				BufferedImage b = ImageIO.read(f);
				g.drawImage(b, x + (w - b.getWidth()) / 2, y + (h - b.getHeight()) / 2, null);
				return;
			}
		} catch (Exception ignored) {
		}
		g.setColor(new Color(200, 170, 90, 120));
		g.fillOval(x + 6, y + 4, Math.max(4, w - 12), Math.max(4, h - 8));
	}

	/** One line of OSRS text: glyphs from the font's sprite frames, advances from the font file. */
	static int glyphRun(Graphics2D g, int font, String s, int x, int baseline, int colour, boolean shadow, boolean draw) {
		FontDefinition fd = fonts.get(font);
		int cx = x;
		int col = colour;
		boolean shad = shadow;
		for (int i = 0; i < s.length(); i++) {
			char ch = s.charAt(i);
			if (ch == '<') {
				int e = s.indexOf('>', i);
				if (e > 0) {
					String tag = s.substring(i + 1, e);
					i = e;
					if (tag.startsWith("col=")) { try { col = Integer.parseInt(tag.substring(4), 16); } catch (Exception ex) { } continue; }
					if (tag.equals("/col")) { col = colour; continue; }
					if (tag.startsWith("shad")) { shad = true; continue; }
					if (tag.equals("/shad")) { shad = shadow; continue; }
					if (tag.equals("lt")) ch = '<';
					else if (tag.equals("gt")) ch = '>';
					else if (tag.startsWith("img=")) { cx += 13; continue; }
					else continue;
				}
			}
			int c = ch & 0xff;
			int adv = fd == null ? 6 : fd.advances[c];
			if (draw && ch != ' ') {
				BufferedImage b = spriteImage(font, c);
				if (b != null) {
					int top = baseline - (fd == null ? 10 : fd.ascent);
					if (shad) g.drawImage(tint(b, 0), cx + 1, top + 1, null);
					g.drawImage(tint(b, col), cx, top, null);
				}
			}
			cx += adv;
		}
		return cx - x;
	}

	static BufferedImage tint(BufferedImage b, int rgb) {
		BufferedImage t = new BufferedImage(b.getWidth(), b.getHeight(), BufferedImage.TYPE_INT_ARGB);
		for (int yy = 0; yy < b.getHeight(); yy++)
			for (int xx = 0; xx < b.getWidth(); xx++)
				if ((b.getRGB(xx, yy) >>> 24) != 0) t.setRGB(xx, yy, 0xff000000 | rgb);
		return t;
	}

	static void drawText(Graphics2D g, InterfaceDefinition d, String s, int x, int y, int w, int h) {
		FontDefinition fd = fonts.get(d.fontId);
		int asc = fd == null ? 10 : fd.ascent;
		int lh = d.lineHeight > 0 ? d.lineHeight : asc + 3;
		// word wrap on <br> and width, like the client
		List<String> lines = new ArrayList<>();
		for (String para : s.split("<br>", -1)) {
			StringBuilder cur = new StringBuilder();
			for (String word : para.split(" ")) {
				String trial = cur.length() == 0 ? word : cur + " " + word;
				if (cur.length() > 0 && glyphRun(null, d.fontId, trial, 0, 0, 0, false, false) > w) {
					lines.add(cur.toString());
					cur = new StringBuilder(word);
				} else cur = new StringBuilder(trial);
			}
			lines.add(cur.toString());
		}
		int total = (lines.size() - 1) * lh + asc;
		int baseline = d.yTextAlignment == 1 ? y + (h - total) / 2 + asc : d.yTextAlignment == 2 ? y + h - total + asc - 2 : y + asc;
		for (String ln : lines) {
			int tw = glyphRun(null, d.fontId, ln, 0, 0, 0, false, false);
			int tx = d.xTextAlignment == 1 ? x + (w - tw) / 2 : d.xTextAlignment == 2 ? x + w - tw : x;
			glyphRun(g, d.fontId, ln, tx, baseline, d.textColor, d.textShadowed, true);
			baseline += lh;
		}
	}
}

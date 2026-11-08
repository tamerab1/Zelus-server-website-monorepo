import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;

/**
 * Stage 16 of the rev 241 cache build (2026-11-06): the egg / relic equipment slot, beside the helmet in the worn
 * equipment tab (interface 387), as the user's reference (Desktop egg.jpg). The server owns it: Equipment.SLOT_EGG = 15
 * (container 94 grew to 16 slots); Equipment.sendEggSlot shows the item with IF_SETOBJECT and hides the silhouette,
 * TabEquipment handles the clicks. No client script changes: three new IF3 graphic components, cloned from vanilla
 * component 9 (a plain sprite on layer 0) with their position / size / sprite patched:
 * <ul>
 * <li>31: the stone slot frame (sprite 170) at 118,4, 36x36 (the spot right of the helmet; vanilla shows Dizana's
 * quiver slot there only while the quiver is worn, under these);</li>
 * <li>32: the egg silhouette, sprite {@link #EGG_SPRITE} drawn here in the style of the vanilla slot silhouettes
 * 156-166 (one colour, #2e2b23, on a 32x32 canvas), at 120,6;</li>
 * <li>33: the item (sprite -1, filled by the server), ops "Remove" (op 1) and "Examine" (op 10).</li>
 * </ul>
 * The bank gear presets screen (5900 and its 1.35x copy 1142) gets a frame, the silhouette and an item layer at 132-134.
 * The same three go on the equipment stats screen (84:57-59, at 112,62, under its layer 1; 2026-11-06).
 * Idempotent: components that already hold these bytes are left alone. Usage: PatchEggSlot apply &lt;cache241&gt;
 */
public final class PatchEggSlot {

	private PatchEggSlot() {
	}

	static final int GROUP = 387, TEMPLATE = 9, FRAME = 31, SILHOUETTE = 32, ITEM = 33;
	static final int FRAME_SPRITE = 170, EGG_SPRITE = 24766, SILHOUETTE_RGB = 0x2e2b23;
	static final int X = 118, Y = 4;
	/** the equipment stats screen 84: its slots are 387's moved by (-6, +58); template 5 is a graphic on its layer 1 */
	/** presets 5900 / 1142 (same component ids): vanilla-built 0-131, ours 132 frame, 133 silhouette, 134 item layer */
	static final int PRESET_LAST = 131, PRESET_FIRST = 132;
	static final int STATS_GROUP = 84, STATS_TEMPLATE = 5, STATS_FRAME = 57, STATS_X = X - 6, STATS_Y = Y + 58;

	public static void main(String[] a) throws Exception {
		if (a.length != 2 || !a[0].equals("apply"))
			throw new IllegalArgumentException("apply <cache241>");
		Store dst = new Store(new File(a[1]));
		dst.load();
		int changed = 0;

		// the silhouette sprite
		byte[] sprite = eggSprite();
		changed += ownOrFree(dst, sprite) && PatchSolarmane.putSingle(dst, 8, EGG_SPRITE, sprite) ? 1 : 0;

		// the components: the worn-equipment tab and the equipment stats screen (same layout, shifted -6, +58)
		int comps = slot(dst, GROUP, TEMPLATE, 30, FRAME, X, Y) + slot(dst, STATS_GROUP, STATS_TEMPLATE, 56, STATS_FRAME, STATS_X, STATS_Y);
		// the bank gear presets screen 5900 and its 1.35x copy 1142: the empty grid cell right of the helmet
		comps += preset(dst, 5900, 429, 47, 36, 36, 431, 49);
		comps += preset(dst, 1142, 579, 63, 49, 49, 587, 72);
		dst.close();
		System.out.println("RESULT egg slot: sprite " + EGG_SPRITE + ", " + comps + " component(s) written (387:" + FRAME + "-" + ITEM
				+ ", 84:" + STATS_FRAME + "-" + (STATS_FRAME + 2) + ")" + (changed > 0 ? ", sprite written" : ""));
	}

	/**
	 * Adds frame / silhouette / item at first, first + 1, first + 2 to the group, cloned from its graphic component
	 * template (same parent). vanillaLast: the group's last vanilla component (the new ones must follow it directly).
	 */
	static int slot(Store dst, int group, int templateId, int vanillaLast, int first, int x, int y) throws Exception {
		return slot(dst, group, templateId, vanillaLast, first, x, y, EGG_SPRITE, null);
	}

	/** As above, with the slot's silhouette sprite and an optional second op on the item. */
	static int slot(Store dst, int group, int templateId, int vanillaLast, int first, int x, int y, int silhouette, String op2) throws Exception {
		Archive ar = dst.findIndex(3).getArchive(group);
		FileData[] fd = ar.getFileData();
		List<byte[]> chunks = SpliceItemOption.splitChunks(ar.decompress(dst.getStorage().loadArchive(ar)), fd.length);
		TreeMap<Integer, byte[]> files = new TreeMap<>();
		for (int i = 0; i < fd.length; i++)
			files.put(fd[i].getId(), chunks.get(i));
		byte[] template = files.get(templateId);
		if (template == null || (template[0] & 0xff) != 0xff || template[1] != 5)
			throw new IllegalStateException(group + ":" + templateId + " is not an IF3 graphic");
		int last = files.lastKey();
		if (last != vanillaLast && last != first + 2 || first != vanillaLast + 1)
			throw new IllegalStateException(group + " has components up to " + last + ": expected vanilla 0-" + vanillaLast + " (or this stage's)");
		Map<Integer, byte[]> want = new TreeMap<>();
		want.put(first, graphic(template, x, y, 36, 36, FRAME_SPRITE));
		want.put(first + 1, graphic(template, x + 2, y + 2, 32, 32, silhouette));
		byte[] item = graphic(template, x + 2, y + 2, 32, 32, -1);
		item[27] = 1; // item outline, and the inventory's item shadow (as interface 1158's item sockets)
		item[28] = 0;
		item[29] = 0x30;
		item[30] = 0x20;
		item[31] = 0x20;
		item = InterfaceRewriter.withAction(item, 0, "Remove");
		if (op2 != null)
			item = InterfaceRewriter.withAction(item, 1, op2);
		item = InterfaceRewriter.withAction(item, 9, "Examine");
		want.put(first + 2, item);
		int comps = 0;
		for (var e : want.entrySet()) {
			byte[] have = files.get(e.getKey());
			if (have != null && Arrays.equals(have, e.getValue()))
				continue;
			// past the vanilla components (checked above), so this stage's own: an earlier build of them is replaced
			InterfaceRewriter.run(e.getValue(), DefRewriter.IDENTITY, true, true, v -> false); // parses as a rev 241 component
			files.put(e.getKey(), e.getValue());
			comps++;
		}
		if (comps > 0)
			PatchCustoms27.writeGroup(dst, 3, group, files);
		return comps;
	}

	/** presets: frame (clone of 51), silhouette (clone of 63, the hat's) and item layer (clone of 70) at 132-134 */
	static int preset(Store dst, int group, int fx, int fy, int fw, int fh, int ix, int iy) throws Exception {
		Archive ar = dst.findIndex(3).getArchive(group);
		FileData[] fd = ar.getFileData();
		List<byte[]> chunks = SpliceItemOption.splitChunks(ar.decompress(dst.getStorage().loadArchive(ar)), fd.length);
		TreeMap<Integer, byte[]> files = new TreeMap<>();
		for (int i = 0; i < fd.length; i++)
			files.put(fd[i].getId(), chunks.get(i));
		int last = files.lastKey();
		if (last != PRESET_LAST && last != PRESET_FIRST + 2)
			throw new IllegalStateException(group + " has components up to " + last + ": expected 0-" + PRESET_LAST + " (or this stage's)");
		Map<Integer, byte[]> want = new TreeMap<>();
		want.put(PRESET_FIRST, at(files.get(51), fx, fy, fw, fh, null));
		want.put(PRESET_FIRST + 1, at(files.get(63), ix, iy, 32, 32, EGG_SPRITE));
		want.put(PRESET_FIRST + 2, at(files.get(70), ix, iy, 32, 32, null));
		int comps = 0;
		for (var e : want.entrySet()) {
			if (Arrays.equals(files.get(e.getKey()), e.getValue()))
				continue;
			InterfaceRewriter.run(e.getValue(), DefRewriter.IDENTITY, true, true, v -> false);
			files.put(e.getKey(), e.getValue());
			comps++;
		}
		if (comps > 0)
			PatchCustoms27.writeGroup(dst, 3, group, files);
		return comps;
	}

	/** a component's bytes moved / resized (and for a graphic, its sprite replaced) */
	static byte[] at(byte[] template, int x, int y, int w, int h, Integer sprite) {
		if (template == null || (template[0] & 0xff) != 0xff)
			throw new IllegalStateException("preset template is not IF3");
		byte[] c = template.clone();
		put16(c, 4, x);
		put16(c, 6, y);
		put16(c, 8, w);
		put16(c, 10, h);
		if (sprite != null) {
			if (c[1] != 5)
				throw new IllegalStateException("not a graphic");
			c[19] = (byte) (sprite >> 24);
			c[20] = (byte) (sprite >> 16);
			c[21] = (byte) (sprite >> 8);
			c[22] = (byte) (int) sprite;
		}
		return c;
	}

	/** true to write: the group is free, or holds an earlier egg drawing (one 32x32 frame in the silhouette colour) */
	static boolean ownOrFree(Store dst, byte[] sprite) throws Exception {
		Archive ar = dst.findIndex(8).getArchive(EGG_SPRITE);
		if (ar == null)
			return true;
		byte[] have = ar.decompress(dst.getStorage().loadArchive(ar));
		if (Arrays.equals(have, sprite))
			return false;
		SpriteCodec.Group g = SpriteCodec.decode(have);
		if (g.frames.size() == 1 && g.maxWidth == 32 && g.maxHeight == 32 && g.palette.length == 2 && (g.palette[1] & 0xffffff) == SILHOUETTE_RGB)
			return true;
		throw new IllegalStateException("sprite " + EGG_SPRITE + " is taken");
	}

	/** template with x / y / width / height / sprite replaced (IF3 graphic offsets, see InterfaceRewriter.if3) */
	static byte[] graphic(byte[] template, int x, int y, int w, int h, int sprite) {
		byte[] c = template.clone();
		put16(c, 4, x);
		put16(c, 6, y);
		put16(c, 8, w);
		put16(c, 10, h);
		c[19] = (byte) (sprite >> 24);
		c[20] = (byte) (sprite >> 16);
		c[21] = (byte) (sprite >> 8);
		c[22] = (byte) sprite;
		c[25] = 0; // not tiled: template 9 is a tiled connector, and a tiled graphic doesn't draw an item (2026-11-06)
		return c;
	}

	static void put16(byte[] b, int pos, int v) {
		b[pos] = (byte) (v >> 8);
		b[pos + 1] = (byte) v;
	}

	/** an egg outline (wider at the bottom), 2 px, one colour, centred on a 32x32 canvas like sprites 156-166 */
	static byte[] eggSprite() {
		BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setColor(new Color(SILHOUETTE_RGB));
		g.setStroke(new BasicStroke(2f));
		// an egg curve: an ellipse whose half-width shrinks toward the top (sin t > 0 = upper half)
		double cx = 15.5, cy = 16.5, halfW = 9.5, halfH = 11.5;
		Path2D.Double egg = new Path2D.Double();
		for (int i = 0; i <= 72; i++) {
			double t = Math.PI * 2 * i / 72, x = cx + halfW * Math.cos(t) * (1 - 0.22 * Math.sin(t)), y = cy - halfH * Math.sin(t);
			if (i == 0)
				egg.moveTo(x, y);
			else
				egg.lineTo(x, y);
		}
		g.draw(egg);
		g.dispose();
		return silhouette(img);
	}

	/** Encodes a 32x32 drawing as a one-colour slot silhouette, trimmed to its opaque pixels like sprites 156-166. */
	static byte[] silhouette(BufferedImage img) {
		int minX = 32, minY = 32, maxX = -1, maxY = -1;
		for (int y = 0; y < 32; y++)
			for (int x = 0; x < 32; x++)
				if ((img.getRGB(x, y) >>> 24) >= 128) {
					minX = Math.min(minX, x);
					minY = Math.min(minY, y);
					maxX = Math.max(maxX, x);
					maxY = Math.max(maxY, y);
				}
		SpriteCodec.Frame f = new SpriteCodec.Frame();
		f.offsetX = minX;
		f.offsetY = minY;
		f.width = maxX - minX + 1;
		f.height = maxY - minY + 1;
		f.index = new byte[f.width * f.height];
		for (int y = 0; y < f.height; y++)
			for (int x = 0; x < f.width; x++)
				f.index[y * f.width + x] = (byte) ((img.getRGB(minX + x, minY + y) >>> 24) >= 128 ? 1 : 0);
		return SpriteCodec.encode(SpriteCodec.assemble(32, 32, List.of(f), List.of(new int[] { 0, SILHOUETTE_RGB })));
	}
}

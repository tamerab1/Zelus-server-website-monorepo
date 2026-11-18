import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.InterfaceDefinition;
import net.runelite.cache.definitions.loaders.InterfaceLoader;
import net.runelite.cache.fs.*;

import java.io.File;
import java.nio.file.Files;
import java.util.*;

/**
 * Round 2 of the Karn interfaces (2026-11-12): restyles the live groups component by component -- every component keeps
 * its id (the server's constants hold), the plain panels the new art paints are hidden, everything else is moved, resized
 * and re-skinned onto the art (MakeKarnUi2). Reads the rev 231 groups from our 231 cache, writes rev 231 component files to
 * assets/karn_ui/interface_&lt;group&gt;/ for cache stage 30 (PatchKarnUi converts them to rev 241). if1 components are
 * copied byte for byte. Usage: RestyleKarnUi2 &lt;cache231&gt; &lt;assets/karn_ui&gt; [group ...]
 */
public class RestyleKarnUi2 {

	/** One group being restyled. */
	static final class G {
		final int group;
		final TreeMap<Integer, InterfaceDefinition> c = new TreeMap<>();
		final Map<Integer, byte[]> raw = new HashMap<>();

		G(int group) {
			this.group = group;
		}

		InterfaceDefinition d(int id) {
			InterfaceDefinition x = c.get(id);
			if (x == null)
				throw new IllegalStateException(group + ":" + id + " missing");
			return x;
		}

		G pos(int id, int x, int y, int w, int h) {
			InterfaceDefinition d = d(id);
			d.originalX = x;
			d.originalY = y;
			d.originalWidth = w;
			d.originalHeight = h;
			return this;
		}

		G xy(int id, int x, int y) {
			d(id).originalX = x;
			d(id).originalY = y;
			return this;
		}

		G size(int id, int w, int h) {
			d(id).originalWidth = w;
			d(id).originalHeight = h;
			return this;
		}

		/** A sprite (no tiling), with a hover swap (clientscript 44 on itself) when hover >= 0. */
		G sprite(int id, int sprite, int hover) {
			InterfaceDefinition d = d(id);
			d.spriteId = sprite;
			d.spriteTiling = false;
			if (hover >= 0) {
				d.onMouseOverListener = new Object[] { 44, -2147483645, hover };
				d.onMouseLeaveListener = new Object[] { 44, -2147483645, sprite };
				d.hasListener = true;
			}
			return this;
		}

		G colour(int id, int rgb) {
			d(id).textColor = rgb;
			return this;
		}

		/** A rectangle's hover colours (clientscript 45 on itself). */
		G hoverColour(int id, int normal, int over) {
			InterfaceDefinition d = d(id);
			d.textColor = normal;
			d.onMouseOverListener = new Object[] { 45, -2147483645, over };
			d.onMouseLeaveListener = new Object[] { 45, -2147483645, normal };
			d.hasListener = true;
			return this;
		}

		G opacity(int id, int op) {
			d(id).opacity = op;
			return this;
		}

		G filled(int id, boolean f) {
			d(id).filled = f;
			return this;
		}

		G hide(int... ids) {
			for (int id : ids)
				d(id).isHidden = true;
			return this;
		}

		G show(int... ids) {
			for (int id : ids)
				d(id).isHidden = false;
			return this;
		}

		G font(int id, int font) {
			d(id).fontId = font;
			return this;
		}

		G align(int id, int ax, int ay) {
			d(id).xTextAlignment = ax;
			d(id).yTextAlignment = ay;
			return this;
		}

		G text(int id, String s) {
			d(id).text = s;
			return this;
		}

		G shadow(int id, boolean s) {
			d(id).textShadowed = s;
			return this;
		}

		/**
		 * Maps the old stone palette onto a new one, everywhere: rectangle / text / line colours and the colours in the
		 * hover scripts (clientscript 45's last argument). Colours not in the map are left alone.
		 */
		G remap(Map<Integer, Integer> m) {
			for (InterfaceDefinition d : c.values()) {
				if (raw.containsKey(d.id & 0xffff))
					continue;
				if (d.type == 3 || d.type == 4 || d.type == 9) {
					Integer to = m.get(d.textColor & 0xffffff);
					if (to != null)
						d.textColor = to;
				}
				for (Object[] l : new Object[][] { d.onMouseOverListener, d.onMouseLeaveListener })
					if (l != null && l.length == 3 && Integer.valueOf(45).equals(l[0]) && l[2] instanceof Integer)
						l[2] = m.getOrDefault(((Integer) l[2]) & 0xffffff, (Integer) l[2]);
			}
			return this;
		}
	}

	public static void main(String[] a) throws Exception {
		File assets = new File(a[1]);
		Set<String> only = new HashSet<>(Arrays.asList(a).subList(2, a.length));
		try (Store store = new Store(new File(a[0]))) {
			store.load();
			if (only.isEmpty() || only.contains("1160"))
				write(titles(load(store, 1160)), assets);
			if (only.isEmpty() || only.contains("1109"))
				write(RestyleStreaks.apply(load(store, 1109)), assets);
			if (only.isEmpty() || only.contains("1158"))
				write(RestyleForge.apply(load(store, 1158)), assets);
			if (only.isEmpty() || only.contains("1151"))
				write(RestylePerks.apply(load(store, 1151)), assets);
			if (only.isEmpty() || only.contains("1153"))
				write(RestyleWorkbench.apply(load(store, 1153)), assets);
			// the round 1 Karn windows start from their round 1 exports (base_<group>/), so restyling never compounds
			if (only.isEmpty() || only.contains("1152"))
				write(RestyleTeleports.apply(loadDir(new File(assets, "base_1152"), 1152)), assets);
			if (only.isEmpty() || only.contains("1176"))
				write(RestyleSoulTree.apply(loadDir(new File(assets, "base_1176"), 1176)), assets);
			if (only.isEmpty() || only.contains("1150"))
				write(RestyleStarter.path(loadDir(new File(assets, "base_1150"), 1150)), assets);
			if (only.isEmpty() || only.contains("1175"))
				write(RestyleStarter.weapon(loadDir(new File(assets, "base_1175"), 1175)), assets);
		}
	}

	static G load(Store store, int group) throws Exception {
		Index index = store.getIndex(IndexType.INTERFACES);
		Archive ar = index.getArchive(group);
		ArchiveFiles files = ar.getFiles(store.getStorage().loadArchive(ar));
		G g = new G(group);
		for (FSFile f : files.getFiles()) {
			InterfaceDefinition d = new InterfaceLoader().load(group << 16 | f.getFileId(), f.getContents());
			g.c.put(f.getFileId(), d);
			if (!d.isIf3)
				g.raw.put(f.getFileId(), f.getContents());
		}
		return g;
	}

	static G loadDir(File dir, int group) throws Exception {
		G g = new G(group);
		for (File f : dir.listFiles()) {
			if (!f.getName().endsWith(".dat"))
				continue;
			int id = Integer.parseInt(f.getName().replace(".dat", ""));
			byte[] b = Files.readAllBytes(f.toPath());
			InterfaceDefinition d = new InterfaceLoader().load(group << 16 | id, b);
			g.c.put(id, d);
			if (!d.isIf3)
				g.raw.put(id, b);
		}
		return g;
	}

	static void write(G g, File assets) throws Exception {
		File out = new File(assets, "interface_" + g.group);
		out.mkdirs();
		for (File f : out.listFiles())
			f.delete();
		for (var e : g.c.entrySet()) {
			byte[] b = g.raw.containsKey(e.getKey()) ? g.raw.get(e.getKey()) : BuildBondsInterface.encodeIf3(e.getValue());
			Files.write(new File(out, e.getKey() + ".dat").toPath(), b);
		}
		System.out.println("RESTYLED " + g.group + ": " + g.c.size() + " components -> " + out);
	}

	// ---------------------------------------------------------------- Titles (1160): "Hall of Titles"

	static final int CLOSE = 24861, CLOSE_HOVER = 24862;

	static G titles(G g) {
		// window + close; the heading is painted (comp 3 keeps its place for the server string, which is now empty)
		g.sprite(1, 24860, -1).pos(1, 14, 9, 487, 306);
		g.sprite(2, CLOSE, CLOSE_HOVER).pos(2, 472, 15, 18, 18);
		g.pos(3, 160, 14, 194, 20).hide(3);
		// owned count: header left
		g.pos(4, 28, 17, 130, 16).font(4, 494).align(4, 0, 1);
		// nine tabs: 50 x 32 at x 24 + 52t, y 46; active = crimson veil + gold underline; label under the baked icon
		for (int t = 0; t < 9; t++) {
			int c = 5 + t * 6, x = 24 + t * 52, y = 46;
			g.sprite(c, 24863 + t * 2, 24864 + t * 2).pos(c, x, y, 50, 32);
			g.pos(c + 2, x + 1, y + 1, 48, 30).colour(c + 2, 0x8e1820).opacity(c + 2, 150);
			g.pos(c + 3, x + 10, y + 29, 30, 2).filled(c + 3, true).colour(c + 3, 0xd9a94f).opacity(c + 3, 0);
			g.hide(c + 4); // the old 12px icon: baked into the tab sprite now
			int label = t == 8 ? 58 : c + 5;
			g.pos(label, x, y + 16, 50, 12).font(label, 494).align(label, 1, 1);
		}
		g.hide(57); // the old red "$" donor glyph (the donor tab has a gem icon now)
		// title list: scroll layer and scrollbar inside the well (root 22,82 470x152)
		g.pos(59, 26, 85, 446, 138);
		g.pos(60, 476, 85, 16, 138);
		for (int i = 0; i < 12; i++) {
			int card = 61 + i * 11;
			g.pos(card, (i % 2) * 226, 8 + (i / 2) * 52, 220, 48);
			g.hoverColour(card + 1, 0x1d1519, 0x2a1c22).size(card + 1, 220, 48);
			g.colour(card + 2, 0x3b2a30).size(card + 2, 220, 48);
			// selected: deep crimson fill, gold-dark border, crimson inner line
			g.colour(card + 3, 0x2a0d12).size(card + 3, 218, 46);
			g.colour(card + 4, 0x9a7434).size(card + 4, 220, 48);
			g.colour(card + 5, 0x7e1a20).size(card + 5, 218, 46);
			g.pos(card + 6, 190, 3, 28, 24);                  // coins icon
			g.colour(card + 7, 0xd9a94f).pos(card + 7, 192, 13, 22, 22); // "$"
			g.pos(card + 8, 172, 29, 46, 16);                 // price under the coins
			g.pos(card + 9, 13, 6, 176, 17).colour(card + 9, 0xeee3d2);
			g.pos(card + 10, 13, 24, 172, 21).colour(card + 10, 0xa79a8e);
			// status stripe (the server colours it: owned / for sale / locked) and the bottom shadow line
			int stripe = 215 + i * 2;
			g.pos(stripe, 1, 1, 3, 46).colour(stripe, 0x7e1a20);
			g.colour(stripe + 1, 0x0c0809).size(stripe + 1, 218, 1).xy(stripe + 1, 1, 46);
		}
		// page arrows + page text in the header, left of the close button
		g.sprite(193, 24881, 24882).pos(193, 396, 17, 16, 16).hide(194);
		g.pos(195, 412, 17, 40, 16).font(195, 494).align(195, 1, 1).colour(195, 0xa79a8e);
		g.sprite(196, 24883, 24884).pos(196, 452, 17, 16, 16).hide(197);
		// details plaque (root 22,238 470x70): the layer's own boxes are painted now
		g.pos(204, 22, 230, 470, 78);
		g.hide(205, 206, 207, 208);
		g.pos(209, 12, 7, 262, 14);   // header + equipped
		g.pos(210, 12, 24, 262, 15);  // preview (p12)
		g.pos(211, 12, 41, 262, 30);  // requirement (2 lines)
		g.pos(212, 290, 7, 172, 13);  // category
		g.pos(213, 290, 21, 172, 13); // status
		g.pos(214, 290, 35, 172, 13); // cost
		// Clear / Apply bottom right of the plaque (root coordinates)
		g.sprite(198, 24885, 24886).pos(198, 312, 285, 76, 20);
		g.pos(199, 312, 286, 76, 18).colour(199, 0xeee3d2);
		g.sprite(200, 24887, 24888).pos(200, 392, 285, 90, 20);
		g.pos(201, 392, 286, 90, 18).colour(201, 0xeee3d2);
		g.hide(202, 203); // the old trims around Clear / Apply
		return g;
	}
}

import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Store;

import java.awt.Color;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.TreeMap;

/**
 * Stage 30 of the rev 241 cache build (2026-11-11, round 2 2026-11-12): the Karn look for Teleports (1152), the Soul
 * Tree (1176), the starter (1150) and soul weapon pick (1175), Titles (1160), the streak calendar (1109), the Relic Forge
 * (1158), Perks (1151) and the Karn Forge workbench (1153), from assets/karn_ui:
 * <ul>
 * <li>sprites 24816-24833 (png_q/, painted by MakeKarnUiArt, quantized by quantize.py): the two window backgrounds, the
 * close button, the crimson buttons;</li>
 * <li>the Soul Tree's own sprites (24582-24621, 24700-24731, BuildSoulTreeSprites) recoloured: every teal palette entry
 * (hue 150-215) turned to ember, the rest (gold, violet, the icons' own colours) kept; already-ember sprites are left;</li>
 * <li>the two groups: rev 231 components exported by BuildTeleportInterface / BuildSoulTreeInterface (Karn theme;
 * interface_1152, interface_1176), converted to rev 241 the way stage 4 converts them (InterfaceRewriter with the
 * manifest; checked against the live groups: an unthemed export converts to them byte for byte).</li>
 * </ul>
 * Component ids are unchanged, so the server's constants hold. Idempotent.
 * Usage: PatchKarnUi apply &lt;cache241&gt; &lt;assets/karn_ui&gt; &lt;remap-241.json&gt;
 */
public final class PatchKarnUi {

	private PatchKarnUi() {
	}

	static final int[][] SOUL_SPRITES = { { 24582, 24621 }, { 24700, 24731 } };
	/**
	 * + the starter and the soul weapon pick (2026-11-11); round 2 (2026-11-12, RestyleKarnUi2 + MakeKarnUi2): every one
	 * of these restyled, plus Titles 1160, the login / vote streak calendar 1109, the Relic Forge 1158, Perks 1151 and the
	 * Karn Forge workbench 1153 (checked: their untouched 231 groups convert to the live 241 bytes exactly).
	 */
	// + 1144 (2026-11-15): the Server dashboard rebuilt with 12 Quick tools (BuildDashboardInterfaces; + Battle Pass, Best in Slot)
	static final int[] GROUPS = { 1152, 1176, 1150, 1175, 1160, 1109, 1158, 1151, 1153, 1144 };
	/** ember, the Karn accent (0xf06a4a) */
	static final float EMBER_HUE = 10f / 360f;

	public static void main(String[] a) throws Exception {
		if (a.length != 4 || !a[0].equals("apply"))
			throw new IllegalArgumentException("apply <cache241> <assets/karn_ui> <remap-241.json>");
		File dir = new File(a[2]);
		Manifest manifest = new Manifest(Path.of(a[3]));
		Store dst = new Store(new File(a[1]));
		dst.load();
		int changed = 0, recoloured = 0;
		for (File f : new File(dir, "png_q").listFiles())
			changed += PatchSolarmane.putSingle(dst, 8, Integer.parseInt(f.getName().replace(".png", "")), BuildBinary241.pngSprite(f.toPath())) ? 1 : 0;

		Index sprites = dst.findIndex(8);
		for (int[] range : SOUL_SPRITES)
			for (int id = range[0]; id <= range[1]; id++) {
				Archive ar = sprites.getArchive(id);
				if (ar == null)
					continue;
				byte[] raw = ar.decompress(dst.getStorage().loadArchive(ar));
				SpriteCodec.Group g = SpriteCodec.decode(raw);
				boolean any = false;
				for (int i = 1; i < g.palette.length; i++) {
					int c = ember(g.palette[i]);
					if (c != g.palette[i]) {
						g.palette[i] = c;
						any = true;
					}
				}
				if (any && PatchSolarmane.putSingle(dst, 8, id, SpriteCodec.encode(g))) {
					recoloured++;
					changed++;
				}
			}

		Index ifaces = dst.findIndex(3);
		for (int group : GROUPS) {
			TreeMap<Integer, byte[]> comps = new TreeMap<>();
			for (File f : new File(dir, "interface_" + group).listFiles())
				comps.put(Integer.parseInt(f.getName().replace(".dat", "")),
						InterfaceRewriter.run(Files.readAllBytes(f.toPath()), manifest, false, true, v -> false).bytes());
			if (!comps.firstKey().equals(0) || comps.lastKey() != comps.size() - 1)
				throw new IllegalStateException("interface " + group + " components aren't 0.." + (comps.size() - 1));
			Archive ar = ifaces.getArchive(group);
			if (ar == null)
				throw new IllegalStateException("interface " + group + " missing (stage 4 builds it)");
			if (!PatchEggIcons.sameFiles(BuildBinary241.files(dst, ar), comps)) {
				PatchCustoms27.writeGroup(dst, 3, group, comps);
				changed++;
			}
		}
		dst.close();
		System.out.println("RESULT karn ui: " + changed + " change(s), " + recoloured + " Soul Tree sprites recoloured");
	}

	/** A teal colour as ember (same saturation and brightness); anything else unchanged. */
	static int ember(int rgb) {
		float[] hsb = Color.RGBtoHSB((rgb >> 16) & 0xff, (rgb >> 8) & 0xff, rgb & 0xff, null);
		float hue = hsb[0] * 360f;
		if (hsb[1] < 0.15f || hue < 150f || hue > 215f)
			return rgb;
		return Color.HSBtoRGB(EMBER_HUE, Math.min(1f, hsb[1] * 1.05f), hsb[2]) & 0xffffff;
	}
}

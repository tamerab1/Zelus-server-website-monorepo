import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Store;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;

/**
 * Stage 22 of the rev 241 cache build (2026-11-08): the bond slot, left of the helmet, built like the egg slot
 * (PatchEggSlot, which must run first). The server owns it: Equipment.SLOT_BOND = 16, Equipment.sendBondSlot,
 * TabEquipment.
 * <ul>
 * <li>worn equipment 387:34-36 at 36,4 (cape column, 5 px left of the helmet; 387:29 under it is the empty Deadman sigil layer);</li>
 * <li>equipment stats 84:60-62 at 30,62 (387's position moved by -6, +58).</li>
 * </ul>
 * Item ops: Remove (op 1), Overview (op 2), Examine (op 10). Sprite {@link #SPRITE}: two linked rings, in the vanilla
 * slot-silhouette colour. Idempotent. Usage: PatchBondSlot apply &lt;cache241&gt;
 */
public final class PatchBondSlot {

	private PatchBondSlot() {
	}

	static final int SPRITE = 24775;

	public static void main(String[] a) throws Exception {
		if (a.length != 2 || !a[0].equals("apply"))
			throw new IllegalArgumentException("apply <cache241>");
		Store dst = new Store(new File(a[1]));
		dst.load();
		byte[] sprite = ringsSprite();
		boolean wrote = writable(dst, sprite) && PatchSolarmane.putSingle(dst, 8, SPRITE, sprite);
		int comps = PatchEggSlot.slot(dst, PatchEggSlot.GROUP, PatchEggSlot.TEMPLATE, 33, 34, 36, 4, SPRITE, "Overview")
				+ PatchEggSlot.slot(dst, PatchEggSlot.STATS_GROUP, PatchEggSlot.STATS_TEMPLATE, 59, 60, 30, 62, SPRITE, "Overview");
		dst.close();
		System.out.println("RESULT bond slot: " + comps + " component(s) written (387:34-36, 84:60-62)" + (wrote ? ", sprite " + SPRITE + " written" : ""));
	}

	/** true to write: the group is free, or holds an earlier drawing of this stage's (one 32x32 frame in the silhouette colour) */
	private static boolean writable(Store dst, byte[] sprite) throws Exception {
		Archive ar = dst.findIndex(8).getArchive(SPRITE);
		if (ar == null)
			return true;
		byte[] have = ar.decompress(dst.getStorage().loadArchive(ar));
		if (Arrays.equals(have, sprite))
			return false;
		SpriteCodec.Group g = SpriteCodec.decode(have);
		if (g.frames.size() == 1 && g.maxWidth == 32 && g.maxHeight == 32 && g.palette.length == 2 && (g.palette[1] & 0xffffff) == PatchEggSlot.SILHOUETTE_RGB)
			return true;
		throw new IllegalStateException("sprite " + SPRITE + " is taken");
	}

	/** Two interlinked rings, each 2 px thick, centred on a 32x32 canvas. */
	static byte[] ringsSprite() {
		BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(new Color(PatchEggSlot.SILHOUETTE_RGB));
		g.setStroke(new BasicStroke(2f));
		g.draw(new Ellipse2D.Double(5, 9, 14, 14));
		g.draw(new Ellipse2D.Double(13, 9, 14, 14));
		g.dispose();
		return PatchEggSlot.silhouette(img);
	}
}

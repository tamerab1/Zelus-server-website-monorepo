import net.runelite.cache.fs.Store;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;

/**
 * Stage 23 of the rev 241 cache build (2026-11-08): Morvath the Shadowblade one size up, from 1 tile at scale 100 to
 * {@link #SIZE} tiles at scale {@link #SCALE}, on the boss 21940 and its Colosseum host 21950 (stage 21 copies the boss,
 * so this runs after it). Idempotent. Usage: PatchMorvathSize apply &lt;cache241&gt;
 */
public final class PatchMorvathSize {

	private PatchMorvathSize() {
	}

	static final int[] NPCS = { 21940, 21950 };
	static final int SIZE = 2, SCALE = 140;

	public static void main(String[] a) throws Exception {
		if (a.length != 2 || !a[0].equals("apply"))
			throw new IllegalArgumentException("apply <cache241>");
		Store dst = new Store(new File(a[1]));
		dst.load();
		Map<Integer, byte[]> npcs = RoundTripDefs.files(dst, PatchColosseum.NPC_GROUP);
		Map<Integer, byte[]> put = new TreeMap<>();
		for (int id : NPCS) {
			byte[] def = npcs.get(id);
			if (def == null)
				throw new IllegalStateException("npc " + id + " missing");
			byte[] sized = resized(def);
			if (!Arrays.equals(def, sized))
				put.put(id, sized);
		}
		int changed = put.isEmpty() ? 0 : PatchCustoms27.putConfigs(dst, PatchColosseum.NPC_GROUP, put);
		dst.close();
		System.out.println("RESULT morvath size: " + changed + " change(s), size " + SIZE + " scale " + SCALE);
	}

	/** The def with size (op 12) and scale (ops 97, 98) replaced in place; the rest byte for byte. */
	static byte[] resized(byte[] def) {
		ByteArrayOutputStream b = new ByteArrayOutputStream();
		boolean size = false, width = false, height = false;
		for (byte[] seg : DefRewriter.segments("npc", def, DefRewriter.IDENTITY, k -> null)) {
			int op = seg[0] & 0xff;
			if (op == 0)
				break;
			if (op == 12) {
				b.write(12);
				b.write(SIZE);
				size = true;
			} else if (op == 97 || op == 98) {
				b.write(op);
				b.write(SCALE >> 8);
				b.write(SCALE & 0xff);
				width |= op == 97;
				height |= op == 98;
			} else
				b.writeBytes(seg);
		}
		if (!size || !width || !height)
			throw new IllegalStateException("expected ops 12, 97 and 98 (size " + size + ", width " + width + ", height " + height + ")");
		b.write(0);
		return b.toByteArray();
	}
}

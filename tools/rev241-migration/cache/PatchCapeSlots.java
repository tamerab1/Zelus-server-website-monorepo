import net.runelite.cache.fs.Store;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.*;

/**
 * Stage 41 of the rev 241 cache build (user, 2026-11-14: "the character looks broken" in the Attack / Karn master cape).
 * The custom master capes were cloned onto reused item slots and kept those slots' wear positions: the Attack master cape
 * (30232, the Scarecrow shirt's slot) and the Karn master cape copied from it said wearpos torso + wearpos2 arms, so the
 * client dropped the arms kit; sixteen others said head + hair + jaw (hair and beard vanished); the Fire / Infernal ranged
 * capes said weapon. All become wearpos cape (op 13 = 1) with no wearpos2 / wearpos3 (ops 14 / 27 dropped). Run after
 * stage 37 (the Karn master cape) and the stages that add the others. Idempotent. Usage: PatchCapeSlots apply &lt;cache241&gt;
 */
public final class PatchCapeSlots {

	private PatchCapeSlots() {
	}

	static final int[] CAPES = { 30232, 30242, 30244, 30246, 30248, 30250, 30252, 30254, 30256, 30258, 30260, 30262, 30264, 30266,
		30268, 30270, 30272, 30274, 30599, 30613, 42766 };

	public static void main(String[] a) throws Exception {
		if (a.length != 2 || !a[0].equals("apply"))
			throw new IllegalArgumentException("apply <cache241>");
		Store dst = new Store(new File(a[1]));
		dst.load();
		Map<Integer, byte[]> items = RoundTripDefs.files(dst, 10);
		DefRewriter.ParamKinds pk = DefRewriter.paramKinds(RoundTripDefs.files(dst, 11));
		Map<Integer, byte[]> put = new TreeMap<>();
		for (int id : CAPES) {
			byte[] def = items.get(id);
			if (def == null)
				throw new IllegalStateException("item " + id + " missing");
			ByteArrayOutputStream b = new ByteArrayOutputStream();
			boolean slot = false;
			for (byte[] seg : DefRewriter.segments("item", def, DefRewriter.IDENTITY, pk)) {
				int op = seg[0] & 0xff;
				if (op == 0)
					break;
				if (op == 14 || op == 27)
					continue;
				if (op == 13) {
					b.write(13);
					b.write(1);
					slot = true;
					continue;
				}
				b.writeBytes(seg);
			}
			if (!slot) {
				b.write(13);
				b.write(1);
			}
			b.write(0);
			byte[] out = b.toByteArray();
			if (!Arrays.equals(out, def))
				put.put(id, out);
		}
		int changed = put.isEmpty() ? 0 : PatchCustoms27.putConfigs(dst, 10, put);
		dst.close();
		System.out.println("RESULT cape slots: " + changed + " change(s) of " + CAPES.length + " capes (wearpos cape, no wearpos2/3)");
	}
}

import net.runelite.cache.fs.Store;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Map;
import java.util.TreeMap;

/**
 * Stage 43 of the rev 241 cache build (user 2026-11-15: "why is there a donator weapon box as a drop in royal titans?").
 * Four official Royal Titans rewards sit on ids that our older custom items still occupy in our cache (rev 231 ids kept
 * by the migration), so the server was handing out the custom item instead:
 * <pre>
 *   30622 Bran (pet)                  -> ours: Snake tail
 *   30626 Deadeye prayer scroll       -> ours: (unnamed)
 *   30627 Mystic vigour prayer scroll -> ours: Donator Armour Box
 *   30628 Ice element staff crown     -> ours: Donator Weapon Box
 *   30624 Ric (pet, Bran metamorph)    -> ours: Corrupted slayer helm kit
 *   30105 Tooth half of key (Hueycoatl) -> ours: Corrupt logs
 * </pre>
 * Players may hold our old items, so those ids stay as they are; the official records are copied from vanilla rev 241
 * onto free ids instead ({@link #COPIES}), without their note / placeholder links (ops 97 / 98 / 148 / 149). Their
 * models are vanilla's and already in our cache. The server uses the new ids. Idempotent.
 * Usage: PatchTitanRewards apply &lt;cache241&gt; &lt;vanilla241 cache&gt;
 */
public final class PatchTitanRewards {

	private PatchTitanRewards() {
	}

	/** vanilla id -> our id */
	static final int[][] COPIES = { { 30622, 42768 }, { 30626, 42769 }, { 30627, 42770 }, { 30628, 42771 }, { 30624, 42772 }, { 30105, 42774 } };

	public static void main(String[] a) throws Exception {
		if (a.length != 3 || !a[0].equals("apply"))
			throw new IllegalArgumentException("apply <cache241> <vanilla241>");
		Store van = new Store(new File(a[2]));
		van.load();
		Map<Integer, byte[]> vItems = RoundTripDefs.files(van, 10);
		DefRewriter.ParamKinds pk = DefRewriter.paramKinds(RoundTripDefs.files(van, 11));
		TreeMap<Integer, byte[]> out = new TreeMap<>();
		for (int[] c : COPIES) {
			byte[] src = vItems.get(c[0]);
			if (src == null)
				throw new IllegalStateException("vanilla item " + c[0] + " missing");
			ByteArrayOutputStream b = new ByteArrayOutputStream();
			for (byte[] seg : DefRewriter.segments("item", src, DefRewriter.IDENTITY, pk)) {
				int op = seg[0] & 0xff;
				if (op == 0)
					break;
				if (op == 97 || op == 98 || op == 148 || op == 149)
					continue;
				b.writeBytes(seg);
			}
			b.write(0);
			out.put(c[1], b.toByteArray());
		}
		van.close();
		Store dst = new Store(new File(a[1]));
		dst.load();
		Map<Integer, byte[]> have = RoundTripDefs.files(dst, 10);
		for (int[] c : COPIES) {
			byte[] h = have.get(c[1]);
			if (h != null && !java.util.Arrays.equals(h, out.get(c[1])))
				throw new IllegalStateException("item " + c[1] + " is taken by something else");
		}
		int changed = PatchCustoms27.putConfigs(dst, 10, out);
		dst.close();
		System.out.println("RESULT titan rewards: " + changed + " change(s); 30622->42768 30626->42769 30627->42770 30628->42771 30624->42772 30105->42774");
	}
}

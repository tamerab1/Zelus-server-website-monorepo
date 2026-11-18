import net.runelite.cache.fs.Store;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;

/**
 * Item renames (and an optional first inventory option) for Karn items whose cache name is a placeholder.
 * <ul>
 * <li>40092 "Donator mystery box (u)" -> "Battle pass box" with "Open" (user 2026-11-16: the Battle Pass box opens in
 * the Karn spinner; the old def had no inventory option at all).</li>
 * </ul>
 * usage: PatchItemNames apply &lt;cache&gt;. Idempotent.
 */
public class PatchItemNames {

	/** item id -> {name, op1 (null keeps the item's own)} */
	static final Map<Integer, String[]> RENAMES = Map.of(40092, new String[] { "Battle pass box", "Open" });

	public static void main(String[] a) throws Exception {
		Store dst = new Store(new File(a[1]));
		dst.load();
		Map<Integer, byte[]> items = RoundTripDefs.files(dst, 10);
		DefRewriter.ParamKinds pk = DefRewriter.paramKinds(RoundTripDefs.files(dst, 11));
		TreeMap<Integer, byte[]> out = new TreeMap<>();
		for (Map.Entry<Integer, String[]> e : RENAMES.entrySet()) {
			int item = e.getKey();
			String name = e.getValue()[0], op1 = e.getValue()[1];
			byte[] def = items.get(item);
			if (def == null)
				throw new IllegalStateException("item " + item + " missing");
			ByteArrayOutputStream b = new ByteArrayOutputStream();
			boolean wroteOp1 = false;
			for (byte[] seg : DefRewriter.segments("item", def, DefRewriter.IDENTITY, pk)) {
				int op = seg[0] & 0xff;
				if (op == 0)
					break;
				if (op == 2) {
					b.write(2);
					b.writeBytes(name.getBytes(StandardCharsets.ISO_8859_1));
					b.write(0);
					if (op1 != null) {
						b.write(35);
						b.writeBytes(op1.getBytes(StandardCharsets.ISO_8859_1));
						b.write(0);
						wroteOp1 = true;
					}
				} else if (op == 35 && wroteOp1) {
					// replaced above
				} else
					b.writeBytes(seg);
			}
			b.write(0);
			if (!Arrays.equals(b.toByteArray(), def))
				out.put(item, b.toByteArray());
		}
		int changed = out.isEmpty() ? 0 : PatchCustoms27.putConfigs(dst, 10, out);
		dst.close();
		System.out.println("RESULT item names: " + changed + " change(s) " + RENAMES.keySet());
	}
}

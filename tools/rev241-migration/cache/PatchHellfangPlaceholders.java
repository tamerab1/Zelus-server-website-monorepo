import net.runelite.cache.fs.Store;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;

/**
 * Stage 59 (user 2026-11-17: "hellfang set using cannonballs as placeholders in bank"): the Hellfang helm / platebody /
 * platelegs / crossbow (42706-42712) kept their rev 231 placeholder ids (op 148 = 31907 / 31909 / 31911 / 31913), and in
 * rev 241 those are the cannonball placeholders (their op 148 points back at 31906 Bronze cannonball ...). Each item gets
 * its own placeholder at id + 1 (42707 / 42709 / 42711 / 42713), a copy of the Duskwarden helm's working placeholder 42697
 * (no name, model 0, op 149 = 14401) whose op 148 points back at the Hellfang item, and the item's op 148 is repointed at
 * it. Idempotent. Usage: PatchHellfangPlaceholders apply &lt;cache241&gt;
 */
public final class PatchHellfangPlaceholders {

	private PatchHellfangPlaceholders() {
	}

	static final int[] ITEMS = { 42706, 42708, 42710, 42712 };
	static final int TEMPLATE_PLACEHOLDER = 42697;

	public static void main(String[] a) throws Exception {
		if (a.length != 2 || !a[0].equals("apply"))
			throw new IllegalArgumentException("apply <cache241>");
		Store dst = new Store(new File(a[1]));
		dst.load();
		DefRewriter.ParamKinds pk = DefRewriter.paramKinds(RoundTripDefs.files(dst, 11));
		Map<Integer, byte[]> items = RoundTripDefs.files(dst, 10);
		byte[] template = items.get(TEMPLATE_PLACEHOLDER);
		if (template == null || op148(template, pk) != 42696)
			throw new IllegalStateException("template placeholder " + TEMPLATE_PLACEHOLDER + " isn't the Duskwarden helm's");
		Map<Integer, byte[]> put = new TreeMap<>();
		for (int item : ITEMS) {
			int ph = item + 1;
			byte[] have = items.get(ph);
			if (have != null && op148(have, pk) != item)
				throw new IllegalStateException("item " + ph + " is taken by something else");
			put.put(ph, with148(template, item, pk));
			put.put(item, with148(items.get(item), ph, pk));
		}
		put.entrySet().removeIf(e -> Arrays.equals(e.getValue(), items.get(e.getKey())));
		int changed = put.isEmpty() ? 0 : PatchCustoms27.putConfigs(dst, 10, put);
		dst.close();
		System.out.println("RESULT hellfang placeholders: " + changed + " change(s); placeholders 42707/42709/42711/42713");
	}

	private static int op148(byte[] def, DefRewriter.ParamKinds pk) {
		for (byte[] seg : DefRewriter.segments("item", def, DefRewriter.IDENTITY, pk)) {
			int op = seg[0] & 0xff;
			if (op == 0)
				break;
			if (op == 148)
				return (seg[1] & 0xff) << 8 | seg[2] & 0xff;
		}
		return -1;
	}

	/** the def with op 148 set to {@code target} (added before the end if it had none) */
	private static byte[] with148(byte[] def, int target, DefRewriter.ParamKinds pk) {
		ByteArrayOutputStream b = new ByteArrayOutputStream();
		boolean done = false;
		for (byte[] seg : DefRewriter.segments("item", def, DefRewriter.IDENTITY, pk)) {
			int op = seg[0] & 0xff;
			if (op == 0)
				break;
			if (op == 148) {
				b.write(148);
				b.write(target >> 8);
				b.write(target & 0xff);
				done = true;
			} else
				b.writeBytes(seg);
		}
		if (!done) {
			b.write(148);
			b.write(target >> 8);
			b.write(target & 0xff);
		}
		b.write(0);
		return b.toByteArray();
	}
}

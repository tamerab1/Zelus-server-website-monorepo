import net.runelite.cache.fs.Store;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.*;

/**
 * Stage 17 of the rev 241 cache build (2026-11-06): bank placeholders for our new items. A real item's op 148 points at
 * its placeholder; the placeholder record is exactly what vanilla's are (whip 4151 -> 14032: "148 4151, 149 14401", nothing
 * else): op 148 back to the real item, op 149 = 14401, the shared placeholder template.
 * <ul>
 * <li>the model-bank pack's 21 placeholders pointed at the pack's own copy of the template (40002), not 14401: rewritten
 * to the vanilla form;</li>
 * <li>the other 43 pack items and the Solarmane cub / set (60407-60410) had none: one is minted for each, in the order of
 * {@link #ITEMS}, at {@link #BASE}+ (40095-40141), and the item's op 148 set;</li>
 * <li>stage 20's 53 new egg-slot icons (40200-40252): minted after those, at 40142-40194.</li>
 * </ul>
 * Idempotent. Usage: PatchPlaceholders apply &lt;cache241&gt;
 */
public final class PatchPlaceholders {

	private PatchPlaceholders() {
	}

	static final int TEMPLATE = 14401, BASE = 40095, OP_PLACEHOLDER = 148, OP_TEMPLATE = 149;
	/** the real items: the pack's selected items (40001-40094 minus its notes / placeholders / templates) and Solarmane's */
	static final int[] ITEMS;

	static {
		List<Integer> l = new ArrayList<>();
		for (int i = 40001; i <= 40094; i++)
			l.add(i);
		for (int i = 60407; i <= 60410; i++)
			l.add(i);
		for (int i = 40200; i <= 40252; i++) // stage 20's egg-slot icons
			l.add(i);
		ITEMS = l.stream().mapToInt(Integer::intValue).toArray();
	}

	public static void main(String[] a) throws Exception {
		if (a.length != 2 || !a[0].equals("apply"))
			throw new IllegalArgumentException("apply <cache241>");
		Store dst = new Store(new File(a[1]));
		dst.load();
		Map<Integer, byte[]> items = RoundTripDefs.files(dst, 10);
		Map<Integer, byte[]> put = new TreeMap<>();
		int next = BASE, rewritten = 0, minted = 0;
		for (int id : ITEMS) {
			byte[] rec = items.get(id);
			if (rec == null)
				throw new IllegalStateException("item " + id + " missing");
			Map<Integer, Integer> ops = u16Ops(rec);
			if (ops.containsKey(OP_TEMPLATE) || ops.containsKey(97 + 1) /* 98 noted template */ || !hasName(rec))
				continue; // a placeholder, note or template record, not a real item
			Integer ph = ops.get(OP_PLACEHOLDER);
			if (ph != null && ph < BASE) { // the pack's own placeholder: make it the vanilla form
				byte[] want = placeholder(id);
				byte[] have = items.get(ph);
				if (have == null)
					throw new IllegalStateException("item " + id + ": placeholder " + ph + " missing");
				Map<Integer, Integer> hp = u16Ops(have);
				if (!Objects.equals(hp.get(OP_PLACEHOLDER), id))
					throw new IllegalStateException("placeholder " + ph + " doesn't point back at " + id);
				if (!Arrays.equals(have, want)) {
					put.put(ph, want);
					rewritten++;
				}
				continue;
			}
			int target = next++;
			byte[] want = placeholder(id);
			byte[] have = items.get(target);
			if (have != null && !Arrays.equals(have, want))
				throw new IllegalStateException("item " + target + " is taken");
			if (have == null) {
				put.put(target, want);
				minted++;
			}
			if (ph == null || ph != target) {
				put.put(id, withPlaceholder(rec, target));
			}
		}
		int changed = put.isEmpty() ? 0 : PatchCustoms27.putConfigs(dst, 10, put);
		dst.close();
		System.out.println("RESULT placeholders: " + rewritten + " rewritten to template " + TEMPLATE + ", " + minted + " minted ("
				+ BASE + "-" + (next - 1) + "), " + changed + " record(s) written");
	}

	static byte[] placeholder(int realId) {
		return new byte[] { (byte) OP_PLACEHOLDER, (byte) (realId >> 8), (byte) realId, (byte) OP_TEMPLATE, (byte) (TEMPLATE >> 8),
				(byte) TEMPLATE, 0 };
	}

	/** the record with its op 148 set to placeholder (replaced if present, else appended before the terminator) */
	static byte[] withPlaceholder(byte[] rec, int placeholder) {
		ByteArrayOutputStream o = new ByteArrayOutputStream();
		for (byte[] seg : DefRewriter.segments("item", rec, DefRewriter.IDENTITY, k -> null))
			if ((seg[0] & 0xff) != OP_PLACEHOLDER)
				o.writeBytes(seg);
		o.write(OP_PLACEHOLDER);
		o.write(placeholder >> 8);
		o.write(placeholder);
		o.write(0);
		byte[] out = o.toByteArray();
		DefRewriter.rewrite("item", out, DefRewriter.IDENTITY, k -> null); // parses
		return out;
	}

	/** u16 operands of the ops that carry one item id (97, 98, 148, 149) */
	static Map<Integer, Integer> u16Ops(byte[] rec) {
		Map<Integer, Integer> m = new HashMap<>();
		for (byte[] seg : DefRewriter.segments("item", rec, DefRewriter.IDENTITY, k -> null)) {
			int op = seg[0] & 0xff;
			if (op == 97 || op == 98 || op == 148 || op == 149)
				m.put(op, (seg[1] & 0xff) << 8 | (seg[2] & 0xff));
		}
		return m;
	}

	static boolean hasName(byte[] rec) {
		for (byte[] seg : DefRewriter.segments("item", rec, DefRewriter.IDENTITY, k -> null))
			if ((seg[0] & 0xff) == 2)
				return !new String(seg, 1, seg.length - 2, java.nio.charset.StandardCharsets.ISO_8859_1).equals("null");
		return false;
	}
}

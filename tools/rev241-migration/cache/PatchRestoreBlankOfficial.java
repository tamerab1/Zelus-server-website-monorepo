import net.runelite.cache.fs.Store;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;

/**
 * Stage 45 of the rev 241 cache build (2026-11-15, the boss audit): official rev 241 items whose ids are BLANK in our
 * cache. The migration laid our rev 231 item records over vanilla 241's, and where ours had an empty slot in 30000-30999
 * the official item became a nameless blank -- Hueycoatl's whole reward set (hide, hide armour, Dragon hunter wand,
 * Soiled page, Tome of Earth, Huasca seed / herb / potion, Redberry antelope, Loop half of key), the Varlamore potions,
 * Graceful recolours, Helmet of the moon... 139 items. Nothing of ours uses those slots, so each official record goes
 * back on its own id, without its note / placeholder links (ops 97 / 98 / 148 / 149: the linked ids may be our customs).
 * Ids where we HAVE an item of our own are left alone (those are copied elsewhere: stage 43). Idempotent.
 * Usage: PatchRestoreBlankOfficial apply &lt;cache241&gt; &lt;vanilla241 cache&gt;
 */
public final class PatchRestoreBlankOfficial {

	private PatchRestoreBlankOfficial() {
	}

	static final int FROM = 30000, TO = 31000;

	/** the obj's name (op 2), or null */
	static String name(byte[] def, DefRewriter.ParamKinds pk) {
		for (byte[] seg : DefRewriter.segments("item", def, DefRewriter.IDENTITY, pk)) {
			int op = seg[0] & 0xff;
			if (op == 0)
				break;
			if (op == 2)
				return new String(seg, 1, seg.length - 2, StandardCharsets.ISO_8859_1);
		}
		return null;
	}

	public static void main(String[] a) throws Exception {
		if (a.length != 3 || !a[0].equals("apply"))
			throw new IllegalArgumentException("apply <cache241> <vanilla241>");
		Store van = new Store(new File(a[2]));
		van.load();
		Map<Integer, byte[]> vItems = RoundTripDefs.files(van, 10);
		DefRewriter.ParamKinds vpk = DefRewriter.paramKinds(RoundTripDefs.files(van, 11));
		Store dst = new Store(new File(a[1]));
		dst.load();
		Map<Integer, byte[]> ours = RoundTripDefs.files(dst, 10);
		DefRewriter.ParamKinds pk = DefRewriter.paramKinds(RoundTripDefs.files(dst, 11));
		TreeMap<Integer, byte[]> out = new TreeMap<>();
		for (int id = FROM; id < TO; id++) {
			byte[] v = vItems.get(id);
			if (v == null)
				continue;
			String vn = name(v, vpk);
			if (vn == null || vn.equalsIgnoreCase("null"))
				continue;
			byte[] o = ours.get(id);
			String on = o == null ? null : name(o, pk);
			if (on != null && !on.equals("null"))
				continue; // ours: keep
			ByteArrayOutputStream b = new ByteArrayOutputStream();
			for (byte[] seg : DefRewriter.segments("item", v, DefRewriter.IDENTITY, vpk)) {
				int op = seg[0] & 0xff;
				if (op == 0)
					break;
				if (op == 97 || op == 98 || op == 148 || op == 149)
					continue;
				b.writeBytes(seg);
			}
			b.write(0);
			out.put(id, b.toByteArray());
		}
		van.close();
		int changed = out.isEmpty() ? 0 : PatchCustoms27.putConfigs(dst, 10, out);
		dst.close();
		System.out.println("RESULT restore blank official: " + out.size() + " item(s) considered, " + changed + " change(s)");
	}
}

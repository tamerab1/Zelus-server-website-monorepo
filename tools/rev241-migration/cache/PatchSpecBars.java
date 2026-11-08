import net.runelite.cache.fs.Store;

import java.io.File;
import java.util.*;

/**
 * Stage 19 of the rev 241 cache build (2026-11-06): spec bars for our special-attack weapons that have no cache entry.
 * The combat tab shows the spec bar only for items in enum 906 (cost x10) and describes it from enum 1739 (the tooltip),
 * as PatchCustoms27 does for the Tier 15 weapons. The server side is the Special class matched by item name.
 * Idempotent. Usage: PatchSpecBars apply &lt;cache241&gt;
 */
public final class PatchSpecBars {

	private PatchSpecBars() {
	}

	/** {item, cost x10, tooltip} */
	static final Object[][] SPECS = {
			{ 40077, 550, "Shatterquake: A crushing blow with 30% increased accuracy and 25% increased damage that lowers your target's Defence by 35%. An ethereal aftershock follows for 40% of the damage, also striking adjacent monsters in multi-combat." },
			{ 40079, 500, "Phantom Rampart: Slam with 25% increased accuracy and 20% increased damage, striking up to 11 nearby monsters in multi-combat. Each hit lowers the target's Defence by 10%, and you heal 10% of the damage dealt." },
			{ 40015, 500, "Heartbreaker: Lash with 30% increased accuracy and 10% increased damage. If it lands, heal for half the damage dealt and restore 5 Prayer, and leave your target lovestruck." },
	};

	public static void main(String[] a) throws Exception {
		if (a.length != 2 || !a[0].equals("apply"))
			throw new IllegalArgumentException("apply <cache241>");
		Store dst = new Store(new File(a[1]));
		dst.load();
		Map<Integer, byte[]> enums = RoundTripDefs.files(dst, 8);
		byte[] e906 = enums.get(906), e1739 = enums.get(1739);
		for (Object[] sp : SPECS) {
			e906 = PatchCustoms27.enumPut(e906, (Integer) sp[0], (Integer) sp[1], null);
			e1739 = PatchCustoms27.enumPut(e1739, (Integer) sp[0], 0, (String) sp[2]);
		}
		Map<Integer, byte[]> put = new TreeMap<>();
		if (!Arrays.equals(e906, enums.get(906)))
			put.put(906, e906);
		if (!Arrays.equals(e1739, enums.get(1739)))
			put.put(1739, e1739);
		int changed = put.isEmpty() ? 0 : PatchCustoms27.putConfigs(dst, 8, put);
		dst.close();
		System.out.println("RESULT spec bars: " + changed + " enum(s) written, " + SPECS.length + " item(s)");
	}
}

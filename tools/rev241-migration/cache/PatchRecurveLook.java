import net.runelite.cache.fs.Store;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.*;

/**
 * Stage 36 of the rev 241 cache build (user, 2026-11-14): the starter Soulbound recurve (item 42630, the base look of
 * tiers 1-3) wore the 3rd age bow's model; it now wears the Antler Bow's (model bank item 40004, was 55401; stage 15 remaps its models,
 * see PatchModelBankPack). Only the look is copied (models, 2d framing, recolours / retextures, resize, lighting); the name
 * "Soulbound recurve", its options and params stay. Idempotent. Usage: PatchRecurveLook apply &lt;cache241&gt;
 */
public final class PatchRecurveLook {

	private PatchRecurveLook() {
	}

	static final int TARGET = 42630, SOURCE = 40004;
	/** obj ops that make up the look */
	static final Set<Integer> LOOK = Set.of(1, 4, 5, 6, 7, 8, 23, 24, 25, 26, 40, 41, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53, 54, 55, 78, 79, 90, 91, 92, 93, 95, 110, 111, 112, 113, 114); // rev 241 models: 44 inventory, 45-55 worn / head (4-byte ids)

	public static void main(String[] a) throws Exception {
		if (a.length != 2 || !a[0].equals("apply"))
			throw new IllegalArgumentException("apply <cache241>");
		Store dst = new Store(new File(a[1]));
		dst.load();
		Map<Integer, byte[]> items = RoundTripDefs.files(dst, 10);
		DefRewriter.ParamKinds pk = DefRewriter.paramKinds(RoundTripDefs.files(dst, 11));
		byte[] target = items.get(TARGET), source = items.get(SOURCE);
		if (target == null || source == null)
			throw new IllegalStateException("item " + (target == null ? TARGET : SOURCE) + " missing");
		ByteArrayOutputStream b = new ByteArrayOutputStream();
		List<Integer> copied = new ArrayList<>();
		for (byte[] seg : DefRewriter.segments("item", target, DefRewriter.IDENTITY, pk)) {
			int op = seg[0] & 0xff;
			if (op == 0)
				break;
			if (!LOOK.contains(op))
				b.writeBytes(seg);
		}
		for (byte[] seg : DefRewriter.segments("item", source, DefRewriter.IDENTITY, pk)) {
			int op = seg[0] & 0xff;
			if (op == 0)
				break;
			if (LOOK.contains(op)) {
				b.writeBytes(seg);
				copied.add(op);
			}
		}
		b.write(0);
		byte[] out = b.toByteArray();
		int changed = 0;
		if (!Arrays.equals(out, target)) {
			changed = PatchCustoms27.putConfigs(dst, 10, new TreeMap<>(Map.of(TARGET, out)));
		}
		dst.close();
		System.out.println("RESULT recurve look: " + changed + " change(s); item " + TARGET + " <- look ops " + copied + " of " + SOURCE);
	}
}

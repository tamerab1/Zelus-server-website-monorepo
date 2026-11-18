import net.runelite.cache.fs.Store;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/**
 * Stage 46 of the rev 241 cache build (user 2026-11-15: "nice custom textures, moving red colour, clean, unique"):
 * the Karn sword (42773, after stage 44) and the Karn bow (40076, the Volcanic bow renamed) get animated textures.
 * <ul>
 * <li>sprites 25262 / 25263 (assets/karn_weapons/png, make_textures.py) and textures 270 (the sword's crimson veins,
 * scrolling direction 1 speed 2) / 271 (the bow's molten ribbons, direction 4 speed 2);</li>
 * <li>models 62821-62824 (MakeKarnWeapons: the glow faces textured, worn vertex groups kept);</li>
 * <li>the two items' model ops (44 inventory, 45 / 48 worn) switched to them; the bow renamed "Karn bow".</li>
 * </ul>
 * Run after stage 44. Idempotent. Usage: PatchKarnWeapons apply &lt;cache241&gt; &lt;assets/karn_weapons&gt;
 */
public final class PatchKarnWeapons {

	private PatchKarnWeapons() {
	}

	static final int SWORD = 42773, BOW = 40076;
	/** old model -> textured model */
	static final Map<Integer, Integer> MODELS = Map.of(64619, 62821, 64620, 62822, 63359, 62823, 63408, 62824);
	/** texture id -> {sprite, average colour (hsl), direction, speed} */
	static final int[][] TEXTURES = { { 270, 25262, 0 << 10 | 7 << 7 | 22, 1, 2 }, { 271, 25263, 1 << 10 | 6 << 7 | 40, 4, 2 } };
	static final String BOW_NAME = "Karn bow";

	public static void main(String[] a) throws Exception {
		if (a.length != 3 || !a[0].equals("apply"))
			throw new IllegalArgumentException("apply <cache241> <assets/karn_weapons>");
		File dir = new File(a[2]);
		Store dst = new Store(new File(a[1]));
		dst.load();
		int changed = 0;
		for (int[] t : TEXTURES)
			changed += PatchSolarmane.putSingle(dst, 8, t[1], BuildBinary241.pngSprite(new File(dir, "png/" + t[1] + ".png").toPath())) ? 1 : 0;
		Map<Integer, byte[]> have = PatchSolarmane.textures(dst), put = new TreeMap<>();
		for (int[] t : TEXTURES) {
			byte[] rec = { (byte) (t[1] >> 8), (byte) t[1], (byte) (t[2] >> 8), (byte) t[2], 0, (byte) t[3], (byte) t[4] };
			if (!Arrays.equals(have.get(t[0]), rec))
				put.put(t[0], rec);
		}
		if (!put.isEmpty())
			changed += PatchCustoms27.putTextures(dst, put);
		for (int model : MODELS.values())
			changed += PatchSolarmane.putSingle(dst, 7, model, Files.readAllBytes(new File(dir, "models/" + model + ".dat").toPath())) ? 1 : 0;

		Map<Integer, byte[]> items = RoundTripDefs.files(dst, 10);
		DefRewriter.ParamKinds pk = DefRewriter.paramKinds(RoundTripDefs.files(dst, 11));
		TreeMap<Integer, byte[]> out = new TreeMap<>();
		for (int item : new int[] { SWORD, BOW }) {
			byte[] def = items.get(item);
			if (def == null)
				throw new IllegalStateException("item " + item + " missing (run stage 44 first)");
			ByteArrayOutputStream b = new ByteArrayOutputStream();
			for (byte[] seg : DefRewriter.segments("item", def, DefRewriter.IDENTITY, pk)) {
				int op = seg[0] & 0xff;
				if (op == 0)
					break;
				// 44 inventory: int model; 45 / 48 worn (male / female): int model + u8 offset (6 bytes: the round-1 check
				// wanted 5, so the worn models were never switched and the held weapons showed no texture)
				if ((op == 44 && seg.length == 5) || ((op == 45 || op == 48) && seg.length == 6)) {
					int m = (seg[1] & 0xff) << 24 | (seg[2] & 0xff) << 16 | (seg[3] & 0xff) << 8 | (seg[4] & 0xff);
					int to = MODELS.getOrDefault(m, m);
					b.write(op);
					b.write(to >>> 24);
					b.write(to >>> 16 & 0xff);
					b.write(to >>> 8 & 0xff);
					b.write(to & 0xff);
					if (seg.length == 6)
						b.write(seg[5]);
				} else if (op == 2 && item == BOW) {
					b.write(2);
					b.writeBytes(BOW_NAME.getBytes(StandardCharsets.ISO_8859_1));
					b.write(0);
				} else
					b.writeBytes(seg);
			}
			b.write(0);
			if (!Arrays.equals(b.toByteArray(), def))
				out.put(item, b.toByteArray());
		}
		if (!out.isEmpty())
			changed += PatchCustoms27.putConfigs(dst, 10, out);
		dst.close();
		System.out.println("RESULT karn weapons: " + changed + " change(s); textures 270/271 (the GPU plugin loads ids < 512 only: 514/515 never drew), models 62821-62824, items " + SWORD + " / " + BOW);
	}
}

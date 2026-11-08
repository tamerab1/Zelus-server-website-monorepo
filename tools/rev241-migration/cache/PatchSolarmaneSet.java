import net.runelite.cache.fs.Store;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;

/**
 * Stage 13 of the rev 241 cache build (2026-11-04): the Solarmane set, Solarmane's unique drops. Three items cloned from
 * the kyatt hunter gear with the SAME models (every worn model kept, including the top's arms and the hat's chatheads)
 * re-made as textured copies (2026-11-06, user: "the texture colours of the boss, not gilded"): every kyatt model is
 * copied to 62803-62815 with Solarmane's own textures on the fur, as the boss model 62802: 478 (sunfire gold) on the
 * grey / white fur, 479 (royal crimson) on the bright ridge colour 32889. Built by assets/solarmane_set/MakeSetModels
 * (rev 241 library: skins, geometry and colours verified against the originals). The untextured stripes and
 * highlights keep an opcode-40 recolour to crimson / pale sun; black eyes and nose stay.
 * <ul>
 * <li>60408 Solarmane hood (from 10039 Kyatt hat)</li>
 * <li>60409 Solarmane robe top (from 10037 Kyatt top)</li>
 * <li>60410 Solarmane robe bottoms (from 10035 Kyatt legs)</li>
 * </ul>
 * Dropped from the templates: name, examine, value (replaced), note / placeholder links (97, 98, 148, 149) and the
 * kyatt's params (249). Stats live in item_info.json (the server reads none from the cache). Idempotent.
 * Usage: PatchSolarmaneSet apply &lt;cache241&gt; &lt;assets/solarmane_set&gt;
 */
public final class PatchSolarmaneSet {

	private PatchSolarmaneSet() {
	}

	/** {new id, template} */
	static final int[][] ITEMS = { { 60408, 10039 }, { 60409, 10037 }, { 60410, 10035 } };
	static final String[] NAMES = { "Solarmane hood", "Solarmane robe top", "Solarmane robe bottoms" };
	static final String[] EXAMINES = {
			"Woven from the mane of the sun-lion. It still feels warm.",
			"The sun-lion's pelt, burning gold and royal crimson.",
			"Robes of the sun-lion's hide, alive with sunfire."
	};
	static final int VALUE = 1_500_000;

	/** kyatt model -> its Solarmane-textured copy (assets/solarmane_set/model_&lt;id&gt;.dat) */
	static final Map<Integer, Integer> MODELS = Map.ofEntries(
			Map.entry(19452, 62803), Map.entry(54276, 62804), Map.entry(271, 62805), Map.entry(19800, 62806),
			Map.entry(19804, 62807), Map.entry(19830, 62808), Map.entry(19809, 62809), Map.entry(19870, 62810),
			Map.entry(19855, 62811), Map.entry(19454, 62812), Map.entry(19825, 62813), Map.entry(19865, 62814),
			Map.entry(19453, 62815));

	/** kyatt colour (hsl16) -> Solarmane colour; HSL16 = hue 6 bits, saturation 3, lightness 7. */
	static final int[] RECOLOURS = { // the fur colours (32889, 39029, 38003, 41069) are textured, so not recoloured
			8423, 9964, // pale highlights -> pale sun (h9 s5 l108)
			8427, 9968,
			10475, 9960,
			4550, 948, // brown stripes -> royal crimson (h0 s7 l52)
			4661, 928, // dark stripes -> maroon (l32)
			1944, 3004, // dark red trim -> ember (h2 s7 l60)
	};

	public static void main(String[] a) throws Exception {
		if (a.length != 3 || !a[0].equals("apply"))
			throw new IllegalArgumentException("apply <cache241> <assets/solarmane_set>");
		Store dst = new Store(new File(a[1]));
		dst.load();
		int models = 0;
		for (int id : new TreeMap<>(MODELS).values())
			models += PatchSolarmane.putSingle(dst, 7, id,
					java.nio.file.Files.readAllBytes(new File(a[2], "model_" + id + ".dat").toPath())) ? 1 : 0;
		Map<Integer, byte[]> items = RoundTripDefs.files(dst, 10);
		Map<Integer, byte[]> put = new TreeMap<>();
		for (int i = 0; i < ITEMS.length; i++) {
			int id = ITEMS[i][0], template = ITEMS[i][1];
			byte[] cur = items.get(id);
			if (cur != null && !new String(cur, StandardCharsets.ISO_8859_1).contains(NAMES[i]))
				throw new IllegalStateException("item " + id + " is taken");
			byte[] t = items.get(template);
			if (t == null)
				throw new IllegalStateException("template " + template + " missing");
			put.put(id, clone(t, NAMES[i], EXAMINES[i]));
		}
		int changed = PatchCustoms27.putConfigs(dst, 10, put);
		dst.close();
		System.out.println("RESULT solarmane set: " + changed + " item change(s), " + models + " model(s)");
	}

	static byte[] clone(byte[] template, String name, String examine) {
		ByteArrayOutputStream b = new ByteArrayOutputStream();
		b.write(40);
		b.write(RECOLOURS.length / 2);
		for (int v : RECOLOURS)
			PatchCustoms27.u16(b, v);
		DefRewriter.Ids textured = (kind, v) -> kind.equals("model") ? MODELS.getOrDefault(v, v) : v;
		for (byte[] seg : DefRewriter.segments("item", template, textured, k -> null)) {
			int op = seg[0] & 0xff;
			switch (op) {
				case 2 -> str(b, 2, name);
				case 3 -> str(b, 3, examine);
				case 12 -> { b.write(12); PatchCustoms27.i32(b, VALUE); }
				case 40, 41 -> { } // replaced by the recolour above (the kyatt gear has none)
				case 97, 98, 139, 140, 148, 149 -> { } // note / bought / placeholder links belong to the template
				case 249 -> { } // the kyatt's params
				default -> b.writeBytes(seg);
			}
		}
		b.write(0);
		byte[] out = b.toByteArray();
		DefRewriter.rewrite("item", out, DefRewriter.IDENTITY, k -> null); // throws if the record doesn't parse
		return out;
	}

	private static void str(ByteArrayOutputStream b, int op, String s) {
		b.write(op);
		b.writeBytes(s.getBytes(StandardCharsets.ISO_8859_1));
		b.write(0);
	}
}

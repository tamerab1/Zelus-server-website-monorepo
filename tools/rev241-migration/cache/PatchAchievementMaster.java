import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.loaders.ModelLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Store;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/**
 * Stage 39 of the rev 241 cache build (user, 2026-11-14): NPC {@link #NPC} "Achievement Master", who takes over from Zezu
 * (30007, left as it was) at the home. Dressed in the worn models of Valkyrie helm / body / legs (40066-40068), the Karn
 * master cape (42766, stage 37: run after it), the Radiant Soulblade (42762), Bonecrown icon (t3) (40230) and Devout boots
 * (22954), plus the hands kit (model 176; the Valkyrie body has sleeves, the helm a face mask). The items' own recolours
 * are merged into one NPC recolour, checked so that no find colour is in a model that didn't ask for it and no
 * replacement is re-recoloured by a later pair. Chathead: model 62820 (assets/achievement_master: the helm scaled up).
 * Stand 809 / walk 819-822 (the Radiant Soulblade's stance), op Talk-to. Idempotent.
 * Usage: PatchAchievementMaster apply &lt;cache241&gt; &lt;assets/achievement_master&gt;
 */
public final class PatchAchievementMaster {

	private PatchAchievementMaster() {
	}

	static final int NPC = 30004, HEAD = 62820, HANDS_KIT = 176;
	static final int[] ITEMS = { 40066, 40067, 40068, 42766, 42762, 40230, 22954 };
	static final String NAME = "Achievement Master";

	public static void main(String[] a) throws Exception {
		if (a.length != 3 || !a[0].equals("apply"))
			throw new IllegalArgumentException("apply <cache241> <assets/achievement_master>");
		Store dst = new Store(new File(a[1]));
		dst.load();
		int changed = PatchSolarmane.putSingle(dst, 7, HEAD, Files.readAllBytes(new File(a[2], "models/" + HEAD + ".dat").toPath())) ? 1 : 0;
		Map<Integer, byte[]> items = RoundTripDefs.files(dst, 10), npcs = RoundTripDefs.files(dst, 9);
		DefRewriter.ParamKinds pk = DefRewriter.paramKinds(RoundTripDefs.files(dst, 11));

		List<Integer> models = new ArrayList<>();
		Map<Integer, Set<Integer>> colours = new LinkedHashMap<>(); // model -> its colours
		Map<Integer, Integer> recolour = new LinkedHashMap<>();     // find -> replace
		Map<Integer, Integer> askedBy = new HashMap<>();            // find -> model
		for (int item : ITEMS) {
			byte[] def = items.get(item);
			if (def == null)
				throw new IllegalStateException("item " + item + " missing");
			int model = -1;
			List<int[]> pairs = new ArrayList<>();
			for (byte[] seg : DefRewriter.segments("item", def, DefRewriter.IDENTITY, pk)) {
				int op = seg[0] & 0xff;
				if (op == 45)
					model = (seg[1] & 0xff) << 24 | (seg[2] & 0xff) << 16 | (seg[3] & 0xff) << 8 | seg[4] & 0xff;
				else if (op == 40)
					for (int k = 0; k < (seg[1] & 0xff); k++)
						pairs.add(new int[] { (seg[2 + 4 * k] & 0xff) << 8 | seg[3 + 4 * k] & 0xff, (seg[4 + 4 * k] & 0xff) << 8 | seg[5 + 4 * k] & 0xff });
				else if (op == 41 && (seg[1] & 0xff) != 0)
					throw new IllegalStateException("item " + item + " retextures: bake it instead");
			}
			if (model < 0)
				throw new IllegalStateException("item " + item + " has no worn model");
			models.add(model);
			colours.put(model, colours(dst, model));
			for (int[] p : pairs) {
				if (p[0] == p[1] || !colours.get(model).contains(p[0]))
					continue; // no-op, or a colour this mesh doesn't have
				Integer had = recolour.put(p[0], p[1]);
				if (had != null && had != p[1])
					throw new IllegalStateException("colour " + p[0] + " recoloured two ways");
				askedBy.put(p[0], model);
			}
		}
		models.add(HANDS_KIT);
		colours.put(HANDS_KIT, colours(dst, HANDS_KIT));
		for (var e : recolour.entrySet()) {
			for (var m : colours.entrySet())
				if (m.getValue().contains(e.getKey()) && !m.getKey().equals(askedBy.get(e.getKey())) && !wants(dst, items, pk, m.getKey(), e.getKey(), e.getValue()))
					throw new IllegalStateException("colour " + e.getKey() + " is also in model " + m.getKey() + ", which keeps it");
			if (recolour.containsKey(e.getValue()))
				throw new IllegalStateException("replacement " + e.getValue() + " would be recoloured again");
		}

		ByteArrayOutputStream b = new ByteArrayOutputStream();
		b.write(61);
		b.write(models.size());
		for (int m : models)
			int32(b, m);
		b.write(62);
		b.write(1);
		int32(b, HEAD);
		b.write(2);
		b.writeBytes(NAME.getBytes(StandardCharsets.ISO_8859_1));
		b.write(0);
		b.write(12);
		b.write(1);
		b.write(13);
		int16(b, 809);
		b.write(17);
		for (int s : new int[] { 819, 820, 821, 822 })
			int16(b, s);
		b.write(30);
		b.writeBytes("Talk-to".getBytes(StandardCharsets.ISO_8859_1));
		b.write(0);
		if (recolour.size() > 255)
			throw new IllegalStateException("too many colours");
		b.write(40);
		b.write(recolour.size());
		for (var e : recolour.entrySet()) {
			int16(b, e.getKey());
			int16(b, e.getValue());
		}
		b.write(0);
		byte[] def = b.toByteArray(), have = npcs.get(NPC);
		if (have != null && !new String(have, StandardCharsets.ISO_8859_1).contains(NAME))
			throw new IllegalStateException("npc " + NPC + " is taken by something else");
		changed += PatchCustoms27.putConfigs(dst, 9, new TreeMap<>(Map.of(NPC, def)));
		dst.close();
		System.out.println("RESULT achievement master: " + changed + " change(s); npc " + NPC + " \"" + NAME + "\", models " + models + ", " + recolour.size()
				+ " colours, chathead " + HEAD);
	}

	/** whether item model {@code model} recolours {@code find} to the same {@code replace} itself */
	static boolean wants(Store s, Map<Integer, byte[]> items, DefRewriter.ParamKinds pk, int model, int find, int replace) {
		for (int item : ITEMS) {
			int m = -1;
			boolean same = false;
			for (byte[] seg : DefRewriter.segments("item", items.get(item), DefRewriter.IDENTITY, pk)) {
				int op = seg[0] & 0xff;
				if (op == 45)
					m = (seg[1] & 0xff) << 24 | (seg[2] & 0xff) << 16 | (seg[3] & 0xff) << 8 | seg[4] & 0xff;
				else if (op == 40)
					for (int k = 0; k < (seg[1] & 0xff); k++)
						if (((seg[2 + 4 * k] & 0xff) << 8 | seg[3 + 4 * k] & 0xff) == find && ((seg[4 + 4 * k] & 0xff) << 8 | seg[5 + 4 * k] & 0xff) == replace)
							same = true;
			}
			if (m == model)
				return same;
		}
		return false;
	}

	static Set<Integer> colours(Store s, int model) throws Exception {
		Archive ar = s.findIndex(7).getArchive(model);
		if (ar == null)
			throw new IllegalStateException("model " + model + " missing");
		ModelDefinition md = new ModelLoader().load(model, ar.decompress(s.getStorage().loadArchive(ar)));
		Set<Integer> c = new TreeSet<>();
		for (int f = 0; f < md.faceCount; f++)
			c.add(md.faceColors[f] & 0xffff);
		return c;
	}

	static void int32(ByteArrayOutputStream b, int v) {
		b.write(v >>> 24);
		b.write(v >> 16 & 0xff);
		b.write(v >> 8 & 0xff);
		b.write(v & 0xff);
	}

	static void int16(ByteArrayOutputStream b, int v) {
		b.write(v >> 8 & 0xff);
		b.write(v & 0xff);
	}
}

import net.runelite.cache.fs.Store;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.IntUnaryOperator;

/**
 * Stage 63 (user 2026-11-18: The Five Seals reward shop sells only things no other source gives). New items, all
 * recolours of existing models:
 * <ul>
 * <li>42803 Duskwarden ornament kit, 42804 Hellfang ornament kit (the Dragon platebody ornament kit 22236, recoloured);</li>
 * <li>42807-42811 Duskwarden helm / platebody / platelegs / blade / kiteshield (or): Karn crimson and gold;</li>
 * <li>42812-42815 Hellfang helm / platebody / platelegs / crossbow (or): gilded (gold over charcoal);</li>
 * <li>42805 Sealbreaker cape (Cape of Legends 1052 in crimson and gold);</li>
 * <li>42806 Gilded Lil' Vorgath + its npc 30268 (Lil' Vorgath 42782 / 30267: gold hide, crimson trim).</li>
 * </ul>
 * The (or) pieces copy their base item's def (models, framing, params) with a new name, a Dismantle option and the
 * recolour; no placeholders or notes here: stage 61 runs after this one and gives each its own placeholder. Stats and
 * equip slots are server data (item_info.json). Idempotent. Usage: PatchSealsRewards apply &lt;cache241&gt;
 */
public final class PatchSealsRewards {

	private PatchSealsRewards() {
	}

	static final int KIT_TEMPLATE = 22236, CAPE_TEMPLATE = 1052, PET_ITEM = 42782, PET_NPC = 30267;
	static final int GILDED_PET_ITEM = 42806, GILDED_PET_NPC = 30268;

	/** {new id, base id, name, look (0 crimson, 1 gilded), dismantle option} */
	static final Object[][] ITEMS = {
		{ 42803, KIT_TEMPLATE, "Duskwarden ornament kit", 0, false },
		{ 42804, KIT_TEMPLATE, "Hellfang ornament kit", 1, false },
		{ 42805, CAPE_TEMPLATE, "Sealbreaker cape", 0, false },
		{ 42807, 42696, "Duskwarden helm (or)", 0, true },
		{ 42808, 42698, "Duskwarden platebody (or)", 0, true },
		{ 42809, 42700, "Duskwarden platelegs (or)", 0, true },
		{ 42810, 42702, "Duskwarden blade (or)", 0, true },
		{ 42811, 42704, "Duskwarden kiteshield (or)", 0, true },
		{ 42812, 42706, "Hellfang helm (or)", 1, true },
		{ 42813, 42708, "Hellfang platebody (or)", 1, true },
		{ 42814, 42710, "Hellfang platelegs (or)", 1, true },
		{ 42815, 42712, "Hellfang crossbow (or)", 1, true },
		{ GILDED_PET_ITEM, PET_ITEM, "Gilded Lil' Vorgath", 2, false },
	};

	/** dark tones -> crimson, light trims -> gold */
	static int crimson(int c) {
		int l = c & 127;
		return l >= 75 ? PatchFiveSealsArt.hsl(8, 6, Math.min(110, l)) : PatchFiveSealsArt.hsl(0, 7, Math.max(4, l * 7 / 10));
	}

	/** mid / light tones -> gold, darks -> charcoal */
	static int gilded(int c) {
		int l = c & 127;
		return l >= 40 ? PatchFiveSealsArt.hsl(8, 6, Math.min(115, l + 10)) : PatchFiveSealsArt.hsl(0, 0, Math.max(3, l * 6 / 10));
	}

	/**
	 * Lil' Vorgath is already black and gold, so "gilded" turns him inside out (user 2026-11-18: "no difference between the
	 * gilded and normal"): the near-black hide (h44 l8) -> bright gold, the gold trim (h8 l53) -> deep crimson, the brown
	 * (h6 l30) -> dark gold; white stays.
	 */
	static int gildedPet(int c) {
		int h = c >> 10, l = c & 127;
		if (l >= 120)
			return c;
		if (h == 8 && l >= 40)
			return PatchFiveSealsArt.hsl(0, 7, 34);
		if (l < 25)
			return PatchFiveSealsArt.hsl(8, 6, Math.min(80, 58 + l));
		return PatchFiveSealsArt.hsl(7, 5, 40);
	}

	public static void main(String[] a) throws Exception {
		if (a.length != 2 || !a[0].equals("apply"))
			throw new IllegalArgumentException("apply <cache241>");
		Store dst = new Store(new File(a[1]));
		dst.load();
		DefRewriter.ParamKinds pk = DefRewriter.paramKinds(RoundTripDefs.files(dst, 11));
		Map<Integer, byte[]> items = RoundTripDefs.files(dst, 10), npcs = RoundTripDefs.files(dst, 9);

		Map<Integer, byte[]> putItems = new TreeMap<>();
		for (Object[] it : ITEMS) {
			int id = (Integer) it[0], base = (Integer) it[1];
			String name = (String) it[2];
			byte[] have = items.get(id);
			if (have != null && !latin(have).contains(name))
				throw new IllegalStateException("item " + id + " is taken");
			byte[] src = items.get(base);
			if (src == null)
				throw new IllegalStateException("base item " + base + " missing");
			int lk = (Integer) it[3];
			IntUnaryOperator look = lk == 0 ? PatchSealsRewards::crimson : lk == 1 ? PatchSealsRewards::gilded : PatchSealsRewards::gildedPet;
			putItems.put(id, item(dst, src, pk, name, look, (Boolean) it[4], have));
		}

		// the gilded pet npc
		byte[] haveNpc = npcs.get(GILDED_PET_NPC);
		if (haveNpc != null && !latin(haveNpc).contains("Gilded Lil' Vorgath"))
			throw new IllegalStateException("npc " + GILDED_PET_NPC + " is taken");
		byte[] petNpc = npc(dst, npcs.get(PET_NPC), pk, "Gilded Lil' Vorgath", PatchSealsRewards::gildedPet);

		int changed = PatchCustoms27.putConfigs(dst, 10, putItems);
		changed += PatchCustoms27.putConfigs(dst, 9, new TreeMap<>(Map.of(GILDED_PET_NPC, petNpc)));
		dst.close();
		System.out.println("RESULT seals rewards: " + changed + " change(s); items 42803-42815, npc " + GILDED_PET_NPC);
	}

	static byte[] item(Store dst, byte[] src, DefRewriter.ParamKinds pk, String name, IntUnaryOperator look, boolean dismantle, byte[] have) throws Exception {
		List<Integer> models = new ArrayList<>();
		Map<Integer, Integer> baseRecolour = new HashMap<>();
		for (byte[] s : DefRewriter.segments("item", src, DefRewriter.IDENTITY, pk)) {
			int op = s[0] & 0xff;
			if (op >= 44 && op <= 54 && op != 51 && op != 52 && op != 53 && op != 54) // body models; not the chathead ones
				models.add(PatchFiveSealsArt.i32(s, 1));
			if (op == 40)
				for (int i = 0; i < (s[1] & 0xff); i++)
					baseRecolour.put(u16(s, 2 + i * 4), u16(s, 4 + i * 4));
		}
		List<int[]> pairs = pairs(dst, models, baseRecolour, look);
		ByteArrayOutputStream b = new ByteArrayOutputStream();
		for (byte[] s : DefRewriter.segments("item", src, DefRewriter.IDENTITY, pk)) {
			int op = s[0] & 0xff;
			if (op == 0)
				break;
			if (op == 2 || op == 40 || op == 97 || op == 98 || op == 139 || op == 140 || op == 148 || op == 149)
				continue;
			if (dismantle && op == 37)
				continue;
			b.writeBytes(s);
		}
		string(b, 2, name);
		if (dismantle)
			string(b, 37, "Dismantle");
		PatchFiveSealsArt.recolour(b, pairs);
		if (have != null) // keep the placeholder stage 61 gave it on an earlier run
			for (byte[] s : DefRewriter.segments("item", have, DefRewriter.IDENTITY, pk))
				if ((s[0] & 0xff) == 148)
					b.writeBytes(s);
		b.write(0);
		byte[] out = b.toByteArray();
		DefRewriter.rewrite("item", out, DefRewriter.IDENTITY, pk); // parses back
		return out;
	}

	static byte[] npc(Store dst, byte[] src, DefRewriter.ParamKinds pk, String name, IntUnaryOperator look) throws Exception {
		List<Integer> models = new ArrayList<>();
		for (byte[] s : DefRewriter.segments("npc", src, DefRewriter.IDENTITY, pk))
			if ((s[0] & 0xff) == 61)
				for (int k = 0; k < (s[1] & 0xff); k++)
					models.add(PatchFiveSealsArt.i32(s, 2 + k * 4));
		List<int[]> pairs = pairs(dst, models, Map.of(), look);
		ByteArrayOutputStream b = new ByteArrayOutputStream();
		for (byte[] s : DefRewriter.segments("npc", src, DefRewriter.IDENTITY, pk)) {
			int op = s[0] & 0xff;
			if (op == 0)
				break;
			if (op == 2 || op == 40)
				continue;
			b.writeBytes(s);
		}
		string(b, 2, name);
		PatchFiveSealsArt.recolour(b, pairs);
		b.write(0);
		byte[] out = b.toByteArray();
		DefRewriter.rewrite("npc", out, DefRewriter.IDENTITY, pk);
		return out;
	}

	/** every model colour (as the base recolour shows it) through {@code look} */
	static List<int[]> pairs(Store dst, List<Integer> models, Map<Integer, Integer> base, IntUnaryOperator look) throws Exception {
		Set<Integer> own = PatchFiveSealsArt.colours(dst, models);
		List<int[]> out = new ArrayList<>();
		for (int c : own) {
			int shown = base.getOrDefault(c, c);
			int k = look.applyAsInt(shown);
			if (k == c)
				continue;
			out.add(new int[] { c, k });
		}
		if (out.size() > 255)
			throw new IllegalStateException("too many colours: " + out.size());
		return out;
	}

	static int u16(byte[] s, int o) {
		return (s[o] & 0xff) << 8 | s[o + 1] & 0xff;
	}

	static String latin(byte[] b) {
		return new String(b, StandardCharsets.ISO_8859_1);
	}

	static void string(ByteArrayOutputStream b, int op, String s) {
		b.write(op);
		b.writeBytes(s.getBytes(StandardCharsets.ISO_8859_1));
		b.write(0);
	}
}

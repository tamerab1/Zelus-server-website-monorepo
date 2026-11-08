import net.runelite.cache.fs.Store;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Stage 26: the Drak'thor hatchling, the Colosseum of Trials' shop pet (user, 2026-11-08). Npc 21958 is Drak'thor (npc 21927,
 * model 65410) two sizes smaller, a follower with Talk-to / Pick-up; item 60411 is its pet item, cloned from Aggy's. The server
 * side is Pet.DRAKTHOR_HATCHLING (+10% Trial Marks while following) and ReliquaryInterface.
 */
public final class PatchDrakthorPet {

	private PatchDrakthorPet() {
	}

	static final int NPC = 21958, ITEM = 60411, ITEM_TEMPLATE = 44842, MODEL = 65410, STAND = 16218, WALK = 16219, SIZE = 1, SCALE = 64;
	static final String NAME = "Drak'thor hatchling", EXAMINE = "An ashen wyrm, small enough to follow you home.";
	/** {zoom2d, xan2d, yan2d, zan2d, xOffset2d, yOffset2d} */
	static final int[] FRAMING = { 2700, 120, 1700, 0, 0, 10 };

	public static void main(String[] a) throws Exception {
		if (a.length != 2 || !a[0].equals("apply"))
			throw new IllegalArgumentException("apply <cache241>");
		Store dst = new Store(new File(a[1]));
		dst.load();
		Map<Integer, byte[]> npcs = RoundTripDefs.files(dst, 9), items = RoundTripDefs.files(dst, 10);
		if (npcs.get(NPC) != null && !latin(npcs.get(NPC)).contains(NAME))
			throw new IllegalStateException("npc " + NPC + " is taken");
		if (items.get(ITEM) != null && !latin(items.get(ITEM)).contains(NAME))
			throw new IllegalStateException("item " + ITEM + " is taken");
		if (items.get(ITEM_TEMPLATE) == null)
			throw new IllegalStateException("pet item template " + ITEM_TEMPLATE + " missing");
		int changed = PatchCustoms27.putConfigs(dst, 9, Map.of(NPC, npc()));
		changed += PatchCustoms27.putConfigs(dst, 10, Map.of(ITEM, item(items.get(ITEM_TEMPLATE))));
		dst.close();
		System.out.println("RESULT drakthor pet: " + changed + " change(s)");
	}

	private static String latin(byte[] b) {
		return new String(b, StandardCharsets.ISO_8859_1);
	}

	private static void text(ByteArrayOutputStream b, int op, String s) {
		b.write(op);
		b.writeBytes(s.getBytes(StandardCharsets.ISO_8859_1));
		b.write(0);
	}

	/** As PatchSolarmane's pet: Talk-to / Pick-up, level 0, no minimap dot. */
	static byte[] npc() {
		ByteArrayOutputStream b = new ByteArrayOutputStream();
		b.write(61);
		b.write(1);
		PatchCustoms27.i32(b, MODEL);
		text(b, 2, NAME);
		b.write(12);
		b.write(SIZE);
		b.write(13);
		PatchCustoms27.u16(b, STAND);
		b.write(14);
		PatchCustoms27.u16(b, WALK);
		text(b, 30, "Talk-to");
		text(b, 32, "Pick-up");
		b.write(93);
		b.write(95);
		PatchCustoms27.u16(b, 0);
		b.write(97);
		PatchCustoms27.u16(b, SCALE);
		b.write(98);
		PatchCustoms27.u16(b, SCALE);
		b.write(0);
		byte[] out = b.toByteArray();
		DefRewriter.rewrite("npc", out, DefRewriter.IDENTITY, k -> null);
		return out;
	}

	/** Aggy's item with our model, name, examine and framing, and without Aggy's bank placeholder (op 148/149). */
	static byte[] item(byte[] template) {
		byte[] base = PatchCustoms27.item(template, MODEL, MODEL, NAME, FRAMING, null);
		ByteArrayOutputStream b = new ByteArrayOutputStream();
		for (byte[] seg : DefRewriter.segments("item", base, DefRewriter.IDENTITY, k -> null)) {
			int op = seg[0] & 0xff;
			if (op == 148 || op == 149)
				continue;
			if (op == 3)
				text(b, 3, EXAMINE);
			else
				b.writeBytes(seg);
		}
		b.write(0);
		byte[] out = b.toByteArray();
		DefRewriter.rewrite("item", out, DefRewriter.IDENTITY, k -> null);
		return out;
	}
}

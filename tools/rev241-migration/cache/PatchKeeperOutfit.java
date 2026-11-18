import net.runelite.cache.fs.Store;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.*;

/**
 * Stage 62 (user 2026-11-18: "for the keeper of the seals take a man npc and wear it duskwarden legs, body, helm and
 * starweave boots"). NPC 30003 (stage 50's Taala copy) becomes a man in armour: the Duskwarden helm / platebody /
 * platelegs (42696/42698/42700, male worn models 65456/65458/65460) and the Starweave boots (42396, model 65273 with the
 * item's own recolour), plus the Man's (3106) kit hands 177 - the full helm hides hair and jaw, the platebody has its own
 * sleeves (x +-60). Player stance: stand 808, walk 819-822 (Man 3106). Chathead = the helm's male head model. Name and
 * options stay stage 50's. The boots' recolour is applied at NPC level, so the patch refuses to run if any of its
 * source colours also appears in the other models. Idempotent. Usage: PatchKeeperOutfit apply &lt;cache241&gt;
 */
public final class PatchKeeperOutfit {

	private PatchKeeperOutfit() {
	}

	static final int NPC = 30003, MAN = 3106;
	static final int HELM = 42696, BODY = 42698, LEGS = 42700, BOOTS = 42396;
	static final int HANDS = 177;

	public static void main(String[] a) throws Exception {
		if (a.length != 2 || !a[0].equals("apply"))
			throw new IllegalArgumentException("apply <cache241>");
		Store dst = new Store(new File(a[1]));
		dst.load();
		DefRewriter.ParamKinds pk = DefRewriter.paramKinds(RoundTripDefs.files(dst, 11));
		Map<Integer, byte[]> items = RoundTripDefs.files(dst, 10), npcs = RoundTripDefs.files(dst, 9);

		int helm = model(items.get(HELM), pk, 45), body = model(items.get(BODY), pk, 45), legs = model(items.get(LEGS), pk, 45);
		int boots = model(items.get(BOOTS), pk, 45), head = model(items.get(HELM), pk, 51);
		byte[] bootsRecolour = seg("item", items.get(BOOTS), pk, 40);
		List<Integer> models = List.of(helm, body, HANDS, legs, boots);

		// the boots' recolour must not touch the rest of the outfit
		if (bootsRecolour != null) {
			Set<Integer> others = PatchFiveSealsArt.colours(dst, List.of(helm, body, HANDS, legs));
			int n = bootsRecolour[1] & 0xff;
			for (int i = 0; i < n; i++) {
				int from = (bootsRecolour[2 + i * 4] & 0xff) << 8 | bootsRecolour[3 + i * 4] & 0xff;
				if (others.contains(from))
					throw new IllegalStateException("the boots' recolour source " + from + " is also in the helm / body / hands / legs: bake the boots instead");
			}
		}

		byte[] man = npcs.get(MAN), cur = npcs.get(NPC);
		if (man == null || cur == null)
			throw new IllegalStateException("npc " + MAN + " or " + NPC + " missing");
		byte[] stand = seg("npc", man, pk, 13), walk = seg("npc", man, pk, 17);

		ByteArrayOutputStream b = new ByteArrayOutputStream();
		for (byte[] s : DefRewriter.segments("npc", cur, DefRewriter.IDENTITY, pk)) {
			int op = s[0] & 0xff;
			if (op == 0)
				break;
			if (op == 61 || op == 62 || op == 40 || op == 41 || op == 13 || op == 14 || op == 15 || op == 16 || op == 17)
				continue; // replaced below
			b.writeBytes(s);
		}
		b.write(61);
		b.write(models.size());
		for (int m : models)
			i32(b, m);
		b.write(62);
		b.write(1);
		i32(b, head);
		if (stand != null) b.writeBytes(stand);
		if (walk != null) b.writeBytes(walk);
		if (bootsRecolour != null) b.writeBytes(bootsRecolour);
		b.write(0);
		byte[] def = b.toByteArray();

		int changed = Arrays.equals(def, cur) ? 0 : PatchCustoms27.putConfigs(dst, 9, new TreeMap<>(Map.of(NPC, def)));
		dst.close();
		System.out.println("RESULT keeper outfit: " + changed + " change(s); models " + models + ", chathead " + head);
	}

	/** an item's model opcode (44-54, 32-bit id) */
	static int model(byte[] def, DefRewriter.ParamKinds pk, int op) {
		byte[] s = seg("item", def, pk, op);
		if (s == null)
			throw new IllegalStateException("model opcode " + op + " missing");
		return PatchFiveSealsArt.i32(s, 1);
	}

	static byte[] seg(String type, byte[] def, DefRewriter.ParamKinds pk, int op) {
		for (byte[] s : DefRewriter.segments(type, def, DefRewriter.IDENTITY, pk))
			if ((s[0] & 0xff) == op)
				return s;
		return null;
	}

	static void i32(ByteArrayOutputStream b, int v) {
		b.write(v >>> 24);
		b.write(v >>> 16 & 0xff);
		b.write(v >>> 8 & 0xff);
		b.write(v & 0xff);
	}
}

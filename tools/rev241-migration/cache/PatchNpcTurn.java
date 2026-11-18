import net.runelite.cache.fs.Store;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;

/**
 * Stage 31 of the rev 241 cache build (2026-11-11): npcs that can't turn. Op 103 is an npc's turn speed; 0 means it never
 * rotates, so a spawn direction is ignored and it keeps facing south. Osman (1810) and the Title Manager (1867) at home
 * had 0 (the Summer Elemental next to them has the default 32 and faces its spawn direction); this sets the default.
 * Idempotent. Usage: PatchNpcTurn apply &lt;cache241&gt; &lt;npc&gt; [npc ...]
 */
public final class PatchNpcTurn {

	private PatchNpcTurn() {
	}

	static final int TURN_SPEED = 32;

	public static void main(String[] a) throws Exception {
		if (a.length < 3 || !a[0].equals("apply"))
			throw new IllegalArgumentException("apply <cache241> <npc> [npc ...]");
		Store dst = new Store(new File(a[1]));
		dst.load();
		Map<Integer, byte[]> npcs = RoundTripDefs.files(dst, PatchColosseum.NPC_GROUP), put = new TreeMap<>();
		for (int i = 2; i < a.length; i++) {
			int id = Integer.parseInt(a[i]);
			byte[] def = npcs.get(id);
			if (def == null)
				throw new IllegalStateException("npc " + id + " missing");
			byte[] turned = turning(def);
			if (!Arrays.equals(def, turned))
				put.put(id, turned);
		}
		int changed = put.isEmpty() ? 0 : PatchCustoms27.putConfigs(dst, PatchColosseum.NPC_GROUP, put);
		dst.close();
		System.out.println("RESULT npc turn speed: " + changed + " change(s) for " + (a.length - 2) + " npc(s)");
	}

	/** The def with its turn speed (op 103) at the default; without an op 103 it already turns. */
	static byte[] turning(byte[] def) {
		ByteArrayOutputStream b = new ByteArrayOutputStream();
		for (byte[] seg : DefRewriter.segments("npc", def, DefRewriter.IDENTITY, k -> null)) {
			int op = seg[0] & 0xff;
			if (op == 0)
				break;
			if (op == 103) {
				b.write(103);
				b.write(TURN_SPEED >> 8);
				b.write(TURN_SPEED & 0xff);
			} else
				b.writeBytes(seg);
		}
		b.write(0);
		return b.toByteArray();
	}
}

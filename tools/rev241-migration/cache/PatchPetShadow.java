import net.runelite.cache.definitions.loaders.ItemLoader;
import net.runelite.cache.definitions.loaders.NpcLoader;
import net.runelite.cache.fs.*;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Stage 11 (2026-10-30): the Pet Recolour Broker and the Shadow pets.
 * <ul>
 * <li>npc 30068 (Pet Recolour Broker) was a clone of the Zelus Point Store (5214) and looked just like the shops beside
 * it: it becomes Jagex's Zoo keeper (3302), every opcode kept, only the name swapped back to "Pet Recolour Broker".</li>
 * <li>Shadow pets (theme "Shadow" in pets/pet_recolor_exchange.json, npc + item): BatchRecolorAll kept each colour's hue
 * at saturation &lt;= 2 and 40% lightness, so pets just went darker and kept a tint (purple pets came out purple). Their
 * last opcode-40 table is recomputed to plain charcoal greys: hue and saturation 0, lightness 4 + 0.36 * l (white ->
 * 50, near-black stays). The find colours and the table length don't change, so the bytes are patched in place.</li>
 * </ul>
 * Rev 241 obj definitions don't parse with DefRewriter (231 grammar), so the recolour table is located by decoding the
 * definition with the rev 241 cache library and finding its exact byte pattern (opcode 40, count, pairs), which must
 * occur exactly once. Usage: PatchPetShadow check|apply &lt;cache241&gt; &lt;pet_recolor_exchange.json&gt;. Idempotent.
 * Needs client rev241/cache/build/libs/cache-1.13.4.jar ahead of the old cache library on the classpath.
 */
public final class PatchPetShadow {

	private PatchPetShadow() {
	}

	static final int BROKER = 30068, ZOO_KEEPER = 3302;
	static final String BROKER_NAME = "Pet Recolour Broker";

	static short shadow(short hsl) {
		int l = hsl & 127;
		int nl = l <= 3 ? l : (int) Math.round(4 + l * 0.36);
		return (short) nl; // hue 0, saturation 0
	}

	public static void main(String[] a) throws Exception {
		if (a.length != 3 || !(a[0].equals("apply") || a[0].equals("check")))
			throw new IllegalArgumentException("check|apply <cache241> <pet_recolor_exchange.json>");
		boolean apply = a[0].equals("apply");
		List<int[]> shadows = shadowIds(Files.readString(Path.of(a[2])));
		Store dst = new Store(new File(a[1]));
		dst.load();
		Map<Integer, byte[]> npcs = RoundTripDefs.files(dst, 9), items = RoundTripDefs.files(dst, 10);
		Map<Integer, byte[]> paramDefs = new HashMap<>(RoundTripDefs.files(dst, 11));
		DefRewriter.ParamKinds pk = DefRewriter.paramKinds(paramDefs);

		Map<Integer, byte[]> npcPut = new TreeMap<>(), itemPut = new TreeMap<>();
		npcPut.put(BROKER, broker(npcs.get(ZOO_KEEPER), pk));

		int pets = 0;
		for (int[] s : shadows) {
			int npc = s[0], item = s[1];
			byte[] n = npcs.get(npc), it = items.get(item);
			if (n == null || it == null)
				throw new IllegalStateException("shadow pet npc " + npc + " / item " + item + " missing");
			var nd = new NpcLoader().load(npc, n);
			var id = new ItemLoader().load(item, it);
			npcPut.put(npc, recolour("npc " + npc, n, nd.recolorToFind, nd.recolorToReplace));
			itemPut.put(item, recolour("item " + item, it, id.colorFind, id.colorReplace));
			pets++;
		}

		if (!apply) {
			int nc = 0, ic = 0;
			for (var e : npcPut.entrySet()) if (!Arrays.equals(e.getValue(), npcs.get(e.getKey()))) nc++;
			for (var e : itemPut.entrySet()) if (!Arrays.equals(e.getValue(), items.get(e.getKey()))) ic++;
			System.out.println("CHECK pet shadow: " + pets + " shadow pets, would change " + nc + " npc(s) and " + ic + " item(s)");
			dst.close();
			return;
		}
		int changed = PatchCustoms27.putConfigs(dst, 9, npcPut) + PatchCustoms27.putConfigs(dst, 10, itemPut);
		dst.close();
		System.out.println("RESULT pet shadow: " + pets + " shadow pets + broker, " + changed + " change(s)");
	}

	/** {npcId, itemId} of every Shadow variant */
	static List<int[]> shadowIds(String json) {
		List<int[]> out = new ArrayList<>();
		Matcher m = Pattern.compile("\\{[^{}]*\"theme\"\\s*:\\s*\"Shadow\"[^{}]*\\}").matcher(json);
		Pattern item = Pattern.compile("\"itemId\"\\s*:\\s*(\\d+)"), npc = Pattern.compile("\"npcId\"\\s*:\\s*(\\d+)");
		while (m.find()) {
			Matcher i = item.matcher(m.group()), n = npc.matcher(m.group());
			if (!i.find() || !n.find())
				throw new IllegalStateException("shadow entry without ids: " + m.group());
			out.add(new int[]{Integer.parseInt(n.group(1)), Integer.parseInt(i.group(1))});
		}
		if (out.isEmpty())
			throw new IllegalStateException("no Shadow variants in the json");
		return out;
	}

	/** the Zoo keeper's definition under the broker's name */
	static byte[] broker(byte[] zooKeeper, DefRewriter.ParamKinds pk) {
		if (zooKeeper == null)
			throw new IllegalStateException("npc " + ZOO_KEEPER + " missing");
		ByteArrayOutputStream b = new ByteArrayOutputStream();
		boolean named = false;
		for (byte[] seg : DefRewriter.segments("npc", zooKeeper, DefRewriter.IDENTITY, pk)) {
			if ((seg[0] & 0xff) == 2) {
				b.write(2);
				b.writeBytes(BROKER_NAME.getBytes(StandardCharsets.ISO_8859_1));
				b.write(0);
				named = true;
			} else
				b.writeBytes(seg);
		}
		if (!named)
			throw new IllegalStateException("npc " + ZOO_KEEPER + " has no name opcode");
		b.write(0);
		return b.toByteArray();
	}

	/** the definition with its (last) opcode-40 table's replacements recomputed by {@link #shadow} */
	static byte[] recolour(String what, byte[] def, short[] find, short[] replace) {
		if (find == null || replace == null || find.length == 0 || find.length > 255)
			throw new IllegalStateException(what + ": no recolour table");
		byte[] block = table(find, replace);
		int at = -1;
		for (int i = 0; i + block.length <= def.length; i++) {
			if (Arrays.equals(def, i, i + block.length, block, 0, block.length)) {
				if (at >= 0)
					throw new IllegalStateException(what + ": recolour table found twice");
				at = i;
			}
		}
		if (at < 0)
			throw new IllegalStateException(what + ": recolour table not found in the raw definition");
		short[] shadowed = new short[find.length];
		for (int i = 0; i < find.length; i++)
			shadowed[i] = shadow(find[i]);
		byte[] out = def.clone();
		System.arraycopy(table(find, shadowed), 0, out, at, block.length);
		return out;
	}

	static byte[] table(short[] find, short[] replace) {
		byte[] b = new byte[2 + find.length * 4];
		b[0] = 40;
		b[1] = (byte) find.length;
		for (int i = 0; i < find.length; i++) {
			b[2 + i * 4] = (byte) (find[i] >> 8);
			b[3 + i * 4] = (byte) find[i];
			b[4 + i * 4] = (byte) (replace[i] >> 8);
			b[5 + i * 4] = (byte) replace[i];
		}
		return b;
	}
}

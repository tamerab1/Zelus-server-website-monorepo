import net.runelite.cache.fs.*;

import net.runelite.cache.index.FileData;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Stage 8: the Elite Void Knights of the Elite Void island (2026-10-29), three attackable npcs built on Jagex's own
 * Elite Void Knight (5513, the Pest Control exchange npc) outfit, each with its style's void helm and weapon:
 * <ul>
 * <li>5513's parts (model bounds): head 393, jaw 437, elite top 29169 + arms 29168, gloves 14572, robe 29167,
 * feet 358, void knight mace 14579 (left hand); its recolours 7073/4626/4550 -> 8724/-22008/6936 stay.</li>
 * <li>melee: + Void melee helm 19820 (a full helm: the jaw goes), the mace swapped for an Abyssal whip 5409;</li>
 * <li>ranger: + Void ranger helm 27150, a Magic shortbow 512 (its item recolour 6674 -> 31516 kept here);</li>
 * <li>mage: + Void mage helm 27151, the void mace kept (a magic weapon).</li>
 * </ul>
 * Also the Teleports preview: the mage knight merged into one model (BuildTeleportPreviews.merge: parts, recolours and
 * skins baked in), assets/elitevoid/preview_62801.dat, written at model 62801 (free in 241; teleport_previews.json).
 * Usage: PatchEliteVoid apply &lt;cache241Dir&gt; &lt;assets/elitevoid&gt;. Idempotent.
 */
public final class PatchEliteVoid {

	private PatchEliteVoid() {
	}

	static final int[] BASE = { 393, 29169, 29168, 14572, 29167, 358 };
	static final int[] RECOLOURS = { 7073, 8724, 4626, -22008, 4550, 6936 };

	/** {npc, level, stand, walk, rot180, rotLeft, rotRight, weapon, helm, keepJaw, extra recolour find, replace}. */
	static final int[][] KNIGHTS = {
			{ 20002, 200, 808, 1660, 820, 821, 822, 5409, 19820, 0, 0, 0 },     // melee
			{ 20003, 200, 808, 819, 820, 821, 822, 512, 27150, 1, 6674, 31516 }, // ranger
			{ 20004, 200, 813, 1205, 1206, 1207, 1208, 14579, 27151, 1, 0, 0 },  // mage
	};
	static final String NAME = "Elite Void Knight";

	public static void main(String[] a) throws Exception {
		if (a.length != 3 || !a[0].equals("apply"))
			throw new IllegalArgumentException("apply <cache241> <assets/elitevoid>");
		Store dst = new Store(new File(a[1]));
		dst.load();
		Map<Integer, byte[]> existing = RoundTripDefs.files(dst, 9);
		Map<Integer, byte[]> put = new TreeMap<>();
		for (int[] k : KNIGHTS) {
			byte[] def = npc(k);
			byte[] old = existing.get(k[0]);
			if (old != null && !Arrays.equals(old, def) && !new String(old, StandardCharsets.ISO_8859_1).contains(NAME))
				throw new IllegalStateException("npc " + k[0] + " is already something else in this cache");
			put.put(k[0], def);
		}
		int changed = PatchCustoms27.putConfigs(dst, 9, put);
		changed += putModel(dst, PREVIEW_MODEL, java.nio.file.Files.readAllBytes(new File(a[2], "preview_62801.dat").toPath()));
		dst.close();
		System.out.println("RESULT elite void: " + changed + " change(s)");
	}

	static final int PREVIEW_MODEL = 62801;

	/** A single-file model group (file id 0), added, or replaced when an older copy of ours is there (id free in 241). */
	static int putModel(Store dst, int id, byte[] raw) throws Exception {
		Index index = dst.findIndex(7);
		Archive ar = index.getArchive(id);
		if (ar != null) {
			byte[] old = ar.decompress(dst.getStorage().loadArchive(ar));
			if (Arrays.equals(old, raw))
				return 0;
		} else {
			ar = index.addArchive(id);
			ar.setCompression(net.runelite.cache.fs.jagex.CompressionType.GZ);
		}
		FileData f = new FileData();
		f.setId(0);
		Container c = new Container(ar.getCompression(), -1);
		c.compress(raw, null);
		dst.getStorage().store(7, id, c.data);
		ar.setFileData(new FileData[] { f });
		ar.setCrc(c.crc);
		ar.setRevision(ar.getRevision() + 1);
		ar.setCompressedSize(c.data.length);
		ar.setDecompressedSize(raw.length);
		PatchCustoms27.writeIndex(dst, 7);
		System.out.println("  model " + id + ": " + raw.length + " bytes");
		return 1;
	}

	static byte[] npc(int[] k) {
		List<Integer> models = new ArrayList<>();
		for (int m : BASE)
			models.add(m);
		if (k[9] == 1)
			models.add(437); // the jaw, under an open helm
		models.add(k[7]);
		models.add(k[8]);
		ByteArrayOutputStream b = new ByteArrayOutputStream();
		b.write(61);
		b.write(models.size());
		for (int m : models)
			PatchCustoms27.i32(b, m);
		b.write(2);
		b.writeBytes(NAME.getBytes(StandardCharsets.ISO_8859_1));
		b.write(0);
		b.write(12);
		b.write(1);
		b.write(13);
		PatchCustoms27.u16(b, k[2]);
		b.write(17); // walk, rotate 180, rotate left, rotate right
		PatchCustoms27.u16(b, k[3]);
		PatchCustoms27.u16(b, k[4]);
		PatchCustoms27.u16(b, k[5]);
		PatchCustoms27.u16(b, k[6]);
		b.write(31); // op2
		b.writeBytes("Attack".getBytes(StandardCharsets.ISO_8859_1));
		b.write(0);
		int pairs = RECOLOURS.length / 2 + (k[10] != 0 ? 1 : 0);
		b.write(40);
		b.write(pairs);
		for (int v : RECOLOURS)
			PatchCustoms27.u16(b, v & 0xffff);
		if (k[10] != 0) {
			PatchCustoms27.u16(b, k[10] & 0xffff);
			PatchCustoms27.u16(b, k[11] & 0xffff);
		}
		b.write(95);
		PatchCustoms27.u16(b, k[1]);
		b.write(0);
		byte[] out = b.toByteArray();
		DefRewriter.rewrite("npc", out, DefRewriter.IDENTITY, x -> null); // decodes cleanly with the 241 reader
		return out;
	}
}

import net.runelite.cache.fs.*;
import net.runelite.cache.fs.jagex.DiskStorage;
import net.runelite.cache.index.FileData;
import net.runelite.cache.index.IndexData;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Stage 7 of the rev 241 cache build: the customs27 batch (2026-10-27), built and rigged on a rev 231 scratch cache with
 * the .dev/cache-restore-tool tools (InsertRawModels, DecimateModel, BossRig, CreateRecoloredTextures,
 * TextureModelFaces), then carried over here. Idempotent: a target that already holds exactly the same bytes is skipped.
 *
 *  - models 62776 Vorgath (49027, decimated to 5600 faces, rigged), 62777 Baelgor (97431, decimated, wing bones),
 *    62783 Radiant Soulblade (50228 + radiant gold texture on the guard), 62784/62785 Radiant Soulspire worn/inventory
 *    (91266/91267 + arcane violet head), 62786/62787 Radiant Soulbow worn/inventory (139736/139735 + crimson runes);
 *  - skeletons / frame groups 19000 (Vorgath) and 19001 (Baelgor); seqs 19000-19004 and 19010-19015;
 *  - sprites 24100-24102 and textures 474-476 (the rev 241 texture record: sprite u16, average colour u16, 1,
 *    animation direction, animation speed);
 *  - items 42762-42764: the Tier 15 looks, cloned from the Ascended items (42628 / 42634 / 42640) with a new name and
 *    models; the template's recolours, retextures and note / placeholder links are dropped;
 *  - npcs 20000 (Vorgath the Shadowrider) and 20001 (Baelgor the Felreaver), written from scratch;
 *  - spec bars for the three Tier 15 items: enum 906 (cost) and 1739 (description).
 *
 * args: export <scratch231Dir> <assetsDir> | apply <assetsDir> <cache241Dir> (assets: tools/rev241-migration/assets/customs27)
 */
public class PatchCustoms27 {

	static final int[][] ARCHIVES = { // {index, archive}
			{ 7, 62776 }, { 7, 62777 }, { 7, 62783 }, { 7, 62788 }, { 7, 62784 }, { 7, 62785 }, { 7, 62786 }, { 7, 62787 }, { 7, 62789 }, { 7, 62790 }, { 7, 62791 }, { 7, 62792 }, { 7, 62793 }, { 7, 62794 }, { 7, 62795 },
			{ 7, 62796 }, { 7, 62797 }, { 7, 62798 }, { 7, 62799 }, { 7, 62800 },
			{ 1, 19000 }, { 1, 19001 }, { 0, 19000 }, { 0, 19001 },
			{ 8, 24100 }, { 8, 24101 }, { 8, 24102 }, { 8, 24103 },
	};
	static final int[] SEQS = { 19000, 19001, 19002, 19003, 19004, 19010, 19011, 19012, 19013, 19014, 19015 };
	static final int[] TEXTURES = { 474, 475, 476, 477 };

	/** {newItem, template, inventoryModel, wornModel} + name. */
	static final Object[][] ITEMS = {
			{ 42762, 42628, 62783, 62788, "Radiant Soulblade" }, // worn 62788: the grip moved for the one-hand stance
			{ 42763, 42634, 62787, 62786, "Radiant Soulbow" },
			{ 42764, 42640, 62785, 62784, "Radiant Soulspire" },
	};

	/**
	 * Icon framing {zoom2d, xan2d, yan2d, zan2d, xOffset2d, yOffset2d} from AutoFrameItems (geometric best angle, 1px
	 * margin) for looks whose template's camera doesn't suit the new mesh; others keep the template's.
	 */
	/**
	 * Item recolours {find, replace, ...} (HSL16): the Radiant Soulblade's guard / hilt to radiant gold and its blade to
	 * cool platinum steel (2026-10-28 rework; the faces were the lava-swirl gold texture 474 / warm grey).
	 */
	static final Map<Integer, int[]> RECOLOURS = Map.of(
			42762, new int[] { 7503, 9030, 7645, 9050, 7347, 38076, 7365, 38096 });

	static final Map<Integer, int[]> FRAMING = Map.of(
			42763, new int[] { 2594, 896, 1600, 1472, -15, -13 },
			42764, new int[] { 3670, 1024, 1344, 1408, 5, -8 });

	/** {item, spec cost x10, description} for the combat tab's spec bar. */
	static final Object[][] SPECS = {
			{ 42762, 500, "Radiant Sever: A swift radiant lunge with 25% more accuracy and 20% more damage that returns a quarter of the damage dealt as Prayer points." },
			{ 42763, 550, "Crimson Eclipse: Fire an arrow into the sky that rains down as four burning arrows, each a sure 60% hit, scorching monsters beside the target." },
			{ 42764, 500, "Arcane Cataclysm: A violet starburst that hits up to 60% harder with doubled accuracy and splashes half its damage onto foes around the target." },
	};

	/** {npc, model, size, scale, level, stand, walk} + name. */
	static final Object[][] NPCS = {
			{ 20000, 62776, 4, 200, 780, 19000, 19001, "Vorgath the Shadowrider" },
			{ 20001, 62777, 4, 187, 820, 19010, 19011, "Baelgor the Felreaver" },
	};

	/**
	 * export <scratch231Dir> <assetsDir>: saves every piece the stage needs from the rigging cache, so the build
	 * never depends on it again. apply <assetsDir> <cache241Dir>: writes them (the build's stage 7).
	 */
	public static void main(String[] a) throws Exception {
		if (a[0].equals("export"))
			export(a[1], new File(a[2]));
		else if (a[0].equals("apply"))
			apply(new File(a[1]), a[2]);
		else
			throw new IllegalArgumentException("export <scratch231> <assets> | apply <assets> <cache241>");
	}

	static void export(String scratch, File dir) throws Exception {
		dir.mkdirs();
		Store src = new Store(new File(scratch));
		src.load();
		for (int[] x : ARCHIVES) {
			Archive s = src.findIndex(x[0]).getArchive(x[1]);
			if (s == null)
				throw new IllegalStateException("scratch cache has no archive " + x[0] + "/" + x[1]);
			java.nio.file.Files.write(new File(dir, "arc_" + x[0] + "_" + x[1] + ".bin").toPath(), src.getStorage().loadArchive(s));
			StringBuilder meta = new StringBuilder();
			meta.append(s.getNameHash()).append(' ').append(s.getCompression()).append(' ').append(s.getCrc()).append(' ')
					.append(s.getCompressedSize()).append(' ').append(s.getDecompressedSize());
			for (FileData f : s.getFileData())
				meta.append(' ').append(f.getId()).append(':').append(f.getNameHash());
			java.nio.file.Files.writeString(new File(dir, "arc_" + x[0] + "_" + x[1] + ".meta").toPath(), meta.toString());
		}
		Map<Integer, byte[]> srcSeqs = RoundTripDefs.files(src, 12);
		for (int id : SEQS) {
			byte[] b = srcSeqs.get(id);
			if (b == null)
				throw new IllegalStateException("seq " + id + " missing from the scratch cache");
			java.nio.file.Files.write(new File(dir, "seq_" + id + ".bin").toPath(), b);
		}
		Archive srcTex = src.getIndex(net.runelite.cache.IndexType.TEXTURES).getArchive(0);
		ArchiveFiles srcTexFiles = srcTex.getFiles(src.getStorage().loadArchive(srcTex));
		for (int id : TEXTURES) {
			byte[] t = srcTexFiles.findFile(id).getContents();
			if (t.length != 12)
				throw new IllegalStateException("texture " + id + ": unexpected rev 231 record length " + t.length);
			// rev 231 record (avg u16, ?, count, sprite u16, ..., dir, speed) -> rev 241 (sprite, avg, 1, dir, speed)
			java.nio.file.Files.write(new File(dir, "tex_" + id + ".bin").toPath(), new byte[] { t[4], t[5], t[0], t[1], 1, t[10], t[11] });
		}
		src.close();
		System.out.println("RESULT customs27 exported to " + dir);
	}

	static void apply(File dir, String cache241) throws Exception {
		Store dst = new Store(new File(cache241));
		dst.load();
		int changed = 0;

		Set<Integer> touched = new TreeSet<>();
		for (int[] x : ARCHIVES)
			if (copyArchive(dir, dst, x[0], x[1])) {
				touched.add(x[0]);
				changed++;
			}
		for (int index : touched)
			writeIndex(dst, index);

		// seqs: rev 231 and 241 share the frame-sequence grammar (DefRewriter), so the bytes carry over as they are
		Map<Integer, byte[]> seqs = new TreeMap<>();
		for (int id : SEQS) {
			byte[] b = java.nio.file.Files.readAllBytes(new File(dir, "seq_" + id + ".bin").toPath());
			DefRewriter.rewrite("seq", b, DefRewriter.IDENTITY, k -> null); // parses under the 241 grammar
			seqs.put(id, b);
		}
		changed += putConfigs(dst, 12, seqs);

		Map<Integer, byte[]> textures = new TreeMap<>();
		for (int id : TEXTURES)
			textures.put(id, java.nio.file.Files.readAllBytes(new File(dir, "tex_" + id + ".bin").toPath()));
		changed += putTextures(dst, textures);

		Map<Integer, byte[]> items = RoundTripDefs.files(dst, 10);
		Map<Integer, byte[]> newItems = new TreeMap<>();
		for (Object[] it : ITEMS) {
			byte[] template = items.get((Integer) it[1]);
			if (template == null)
				throw new IllegalStateException("template item " + it[1] + " missing");
			newItems.put((Integer) it[0], item(template, (Integer) it[2], (Integer) it[3], (String) it[4], FRAMING.get((Integer) it[0]),
					RECOLOURS.get((Integer) it[0])));
		}
		changed += putConfigs(dst, 10, newItems);

		// spec bars: enum 906 (cost x10) and 1739 (the tooltip), keyed by item
		Map<Integer, byte[]> enums = RoundTripDefs.files(dst, 8);
		Map<Integer, byte[]> newEnums = new TreeMap<>();
		byte[] e906 = enums.get(906), e1739 = enums.get(1739);
		for (Object[] sp : SPECS) {
			e906 = enumPut(e906, (Integer) sp[0], (Integer) sp[1], null);
			e1739 = enumPut(e1739, (Integer) sp[0], 0, (String) sp[2]);
		}
		newEnums.put(906, e906);
		newEnums.put(1739, e1739);
		changed += putConfigs(dst, 8, newEnums);

		Map<Integer, byte[]> newNpcs = new TreeMap<>();
		for (Object[] n : NPCS)
			newNpcs.put((Integer) n[0], npc((Integer) n[1], (Integer) n[2], (Integer) n[3], (Integer) n[4], (Integer) n[5], (Integer) n[6], (String) n[7]));
		changed += putConfigs(dst, 9, newNpcs);

		dst.close();
		System.out.println("RESULT customs27: " + changed + " change(s)");
	}

	/* ---------------------------------------------------------------- definitions */

	static byte[] item(byte[] template, int inv, int worn, String name, int[] framing, int[] recolours) {
		ByteArrayOutputStream b = new ByteArrayOutputStream();
		if (recolours != null) { // opcode 40: count, then {find u16, replace u16}
			b.write(40);
			b.write(recolours.length / 2);
			for (int v : recolours)
				u16(b, v & 0xffff);
		}
		if (framing != null) {
			int[] ops = { 4, 5, 6, 95, 7, 8 };
			for (int i = 0; i < ops.length; i++) {
				b.write(ops[i]);
				u16(b, framing[i] & 0xffff);
			}
		}
		for (byte[] seg : DefRewriter.segments("item", template, DefRewriter.IDENTITY, k -> null)) {
			int op = seg[0] & 0xff;
			if (framing != null && (op == 4 || op == 5 || op == 6 || op == 7 || op == 8 || op == 95))
				continue; // replaced by the framing above
			switch (op) {
				case 2 -> { // name
					b.write(2);
					b.writeBytes(name.getBytes(StandardCharsets.ISO_8859_1));
					b.write(0);
				}
				case 44 -> { b.write(44); i32(b, inv); }
				case 45, 48 -> { b.write(op); i32(b, worn); b.write(seg[5]); } // worn model + offset byte
				case 46, 47, 49, 50, 51, 52, 53, 54 -> { } // the template's second/third worn and chathead models
				case 40, 41 -> { } // the template's recolours / retextures: the new models carry their own colours
				case 97, 98, 139, 140, 148, 149 -> { } // note / bought / placeholder links belong to the template
				default -> b.writeBytes(seg);
			}
		}
		b.write(0);
		byte[] out = b.toByteArray();
		DefRewriter.rewrite("item", out, DefRewriter.IDENTITY, k -> null);
		return out;
	}

	static byte[] npc(int model, int size, int scale, int level, int stand, int walk, String name) {
		ByteArrayOutputStream b = new ByteArrayOutputStream();
		b.write(61);
		b.write(1);
		i32(b, model);
		b.write(2);
		b.writeBytes(name.getBytes(StandardCharsets.ISO_8859_1));
		b.write(0);
		b.write(12);
		b.write(size);
		b.write(13);
		u16(b, stand);
		b.write(14);
		u16(b, walk);
		b.write(31); // op2
		b.writeBytes("Attack".getBytes(StandardCharsets.ISO_8859_1));
		b.write(0);
		b.write(95);
		u16(b, level);
		b.write(97);
		u16(b, scale);
		b.write(98);
		u16(b, scale);
		b.write(0);
		byte[] out = b.toByteArray();
		DefRewriter.rewrite("npc", out, DefRewriter.IDENTITY, k -> null);
		return out;
	}

	/** Adds or replaces one entry of an int map (opcode 6) or, with a text, a string map (opcode 5). */
	static byte[] enumPut(byte[] def, int key, int value, String text) {
		ByteArrayOutputStream b = new ByteArrayOutputStream();
		int mapOp = text == null ? 6 : 5;
		boolean done = false;
		for (byte[] seg : DefRewriter.segments("enum", def, DefRewriter.IDENTITY, k -> null)) {
			int op = seg[0] & 0xff;
			if (op != mapOp) {
				b.writeBytes(seg);
				continue;
			}
			java.nio.ByteBuffer in = java.nio.ByteBuffer.wrap(seg, 1, seg.length - 1);
			int n = in.getShort() & 0xffff;
			LinkedHashMap<Integer, Object> map = new LinkedHashMap<>();
			for (int i = 0; i < n; i++) {
				int k = in.getInt();
				if (mapOp == 6)
					map.put(k, in.getInt());
				else {
					ByteArrayOutputStream str = new ByteArrayOutputStream();
					for (int c; (c = in.get() & 0xff) != 0; )
						str.write(c);
					map.put(k, str.toString(StandardCharsets.ISO_8859_1));
				}
			}
			map.put(key, mapOp == 6 ? (Object) value : text);
			b.write(mapOp);
			u16(b, map.size());
			for (var e : map.entrySet()) {
				i32(b, e.getKey());
				if (mapOp == 6)
					i32(b, (Integer) e.getValue());
				else {
					b.writeBytes(((String) e.getValue()).getBytes(StandardCharsets.ISO_8859_1));
					b.write(0);
				}
			}
			done = true;
		}
		if (!done)
			throw new IllegalStateException("enum has no opcode " + mapOp + " map");
		b.write(0);
		byte[] out = b.toByteArray();
		DefRewriter.rewrite("enum", out, DefRewriter.IDENTITY, k -> null);
		return out;
	}

	static void i32(ByteArrayOutputStream b, int v) {
		b.write(v >>> 24);
		b.write(v >>> 16);
		b.write(v >>> 8);
		b.write(v);
	}

	static void u16(ByteArrayOutputStream b, int v) {
		b.write(v >>> 8);
		b.write(v);
	}

	/* ---------------------------------------------------------------- cache writing */

	static boolean copyArchive(File dir, Store dst, int index, int id) throws Exception {
		byte[] data = java.nio.file.Files.readAllBytes(new File(dir, "arc_" + index + "_" + id + ".bin").toPath());
		String[] meta = java.nio.file.Files.readString(new File(dir, "arc_" + index + "_" + id + ".meta").toPath()).trim().split(" ");
		Index di = dst.findIndex(index);
		Archive d = di.getArchive(id);
		if (d != null) {
			byte[] existing = dst.getStorage().loadArchive(d);
			if (Arrays.equals(existing, data))
				return false;
			d.setRevision(d.getRevision() + 1);
		} else {
			d = di.addArchive(id);
			d.setRevision(1);
		}
		d.setNameHash(Integer.parseInt(meta[0]));
		d.setCompression(Integer.parseInt(meta[1]));
		d.setCrc(Integer.parseInt(meta[2]));
		d.setCompressedSize(Integer.parseInt(meta[3]));
		d.setDecompressedSize(Integer.parseInt(meta[4]));
		FileData[] df = new FileData[meta.length - 5];
		for (int i = 5; i < meta.length; i++) {
			String[] f = meta[i].split(":");
			df[i - 5] = new FileData();
			df[i - 5].setId(Integer.parseInt(f[0]));
			df[i - 5].setNameHash(Integer.parseInt(f[1]));
		}
		d.setFileData(df);
		dst.getStorage().store(index, id, data);
		System.out.println("  archive " + index + "/" + id + ": " + data.length + " bytes");
		return true;
	}

	static void writeIndex(Store dst, int index) throws Exception {
		Index i = dst.findIndex(index);
		IndexData indexData = i.toIndexData();
		Container ic = new Container(i.getCompression(), -1);
		ic.compress(indexData.writeIndexData(), null);
		dst.getStorage().store(255, index, ic.data);
		i.setCrc(ic.crc);
	}

	/** Adds or replaces files of a config group (index 2); returns how many changed. */
	static int putConfigs(Store dst, int group, Map<Integer, byte[]> put) throws Exception {
		TreeMap<Integer, byte[]> files = new TreeMap<>(RoundTripDefs.files(dst, group));
		int changed = 0;
		for (var e : put.entrySet()) {
			if (!Arrays.equals(files.get(e.getKey()), e.getValue())) {
				files.put(e.getKey(), e.getValue());
				changed++;
			}
		}
		if (changed > 0)
			writeGroup(dst, 2, group, files);
		System.out.println("  config " + group + ": " + changed + " written");
		return changed;
	}

	static int putTextures(Store dst, Map<Integer, byte[]> put) throws Exception {
		Archive ar = dst.findIndex(9).getArchive(0);
		FileData[] fd = ar.getFileData();
		List<byte[]> chunks = SpliceItemOption.splitChunks(ar.decompress(dst.getStorage().loadArchive(ar)), fd.length);
		TreeMap<Integer, byte[]> files = new TreeMap<>();
		for (int i = 0; i < fd.length; i++)
			files.put(fd[i].getId(), chunks.get(i));
		int changed = 0;
		for (var e : put.entrySet()) {
			if (!Arrays.equals(files.get(e.getKey()), e.getValue())) {
				files.put(e.getKey(), e.getValue());
				changed++;
			}
		}
		if (changed > 0)
			writeGroup(dst, 9, 0, files);
		System.out.println("  textures: " + changed + " written");
		return changed;
	}

	static void writeGroup(Store dst, int index, int group, TreeMap<Integer, byte[]> files) throws Exception {
		Index idx = dst.findIndex(index);
		Archive archive = idx.getArchive(group);
		FileData[] fd = new FileData[files.size()];
		List<byte[]> contents = new ArrayList<>();
		int i = 0;
		for (var f : files.entrySet()) {
			fd[i] = new FileData();
			fd[i].setId(f.getKey());
			i++;
			contents.add(f.getValue());
		}
		byte[] raw = SpliceItemOption.joinChunks(contents);
		Container c = new Container(archive.getCompression(), -1);
		c.compress(raw, null);
		dst.getStorage().store(index, group, c.data);
		archive.setFileData(fd);
		archive.setCrc(c.crc);
		archive.setRevision(archive.getRevision() + 1);
		archive.setCompressedSize(c.data.length);
		archive.setDecompressedSize(raw.length);
		writeIndex(dst, index);
	}
}

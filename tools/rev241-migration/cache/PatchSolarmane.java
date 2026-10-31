import net.runelite.cache.fs.*;
import net.runelite.cache.index.FileData;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/**
 * Stage 12 (2026-10-31): Solarmane, a lion boss made from the Sabre-toothed kyatt (npc 2907), and its pet.
 * <ul>
 * <li>textures 478 "sunfire gold" and 479 "royal crimson" (sprites 24104 / 24105): the lava texture 31's sprite with its
 * palette remapped along a gradient by "heat" (as CreateRecoloredTextures), so the flow pattern is kept and only the
 * colours change; rev 241 record = sprite u16, average colour u16, 1, animation direction 1, speed 1 (a slow shimmer);</li>
 * <li>model 62802: the kyatt model with 478 on its body and 479 on its back ridge (assets/solarmane/model_62802.dat,
 * built by assets/solarmane/MakeSolarmaneModel; the kyatt's skins are kept, so its animations drive it);</li>
 * <li>npc 30611 Solarmane (size 4, scale 256) and npc 30612, its pet (size 1, scale 64, Talk-to / Pick-up), both on
 * 62802 with the kyatt's stand / walk;</li>
 * <li>item 60407, the pet item, cloned from Aggy's pet item (44842) with Solarmane's model, name, examine and framing.</li>
 * </ul>
 * Usage: PatchSolarmane apply &lt;assets/solarmane&gt; &lt;cache241&gt;. Idempotent.
 */
public final class PatchSolarmane {

	private PatchSolarmane() {
	}

	static final int BOSS = 30611, PET = 30612, PET_ITEM = 60407, PET_ITEM_TEMPLATE = 44842;
	static final int MODEL = 62802, SOURCE_MODEL = 19375, SOURCE_TEXTURE = 31;
	static final int STAND = 5225, WALK = 5226;
	static final String BOSS_NAME = "Solarmane", PET_NAME = "Solarmane cub";
	static final String PET_EXAMINE = "A pride king in the making.";
	/** {texture, sprite} + its gradient (heat 0..1 -> rgb). */
	static final Object[][] TEXTURES = {
			{ 478, 24104, "0:4a2400,0.45:b87a0c,0.8:f2c23c,1:fff2b8" }, // sunfire gold: bronze -> gold -> pale sun
			{ 479, 24105, "0:240004,0.5:6e0a10,0.85:c02a1a,1:ff7436" }, // royal crimson: maroon -> crimson -> ember
	};
	/** Pet item icon {zoom2d, xan2d, yan2d, zan2d, xOffset2d, yOffset2d}. */
	static final int[] PET_FRAMING = { 2300, 120, 1700, 0, 0, 10 };

	public static void main(String[] a) throws Exception {
		if (a.length != 3 || !a[0].equals("apply"))
			throw new IllegalArgumentException("apply <assets/solarmane> <cache241>");
		File dir = new File(a[1]);
		Store dst = new Store(new File(a[2]));
		dst.load();
		int changed = 0;

		// textures + sprites
		byte[] srcTex = textures(dst).get(SOURCE_TEXTURE);
		check(srcTex != null && srcTex.length == 7, "texture " + SOURCE_TEXTURE + " missing or not a rev 241 record");
		int srcSprite = (srcTex[0] & 0xff) << 8 | (srcTex[1] & 0xff);
		byte[] lava = archive(dst, 8, srcSprite);
		Map<Integer, byte[]> texPut = new TreeMap<>();
		for (Object[] t : TEXTURES) {
			int tex = (int) t[0], sprite = (int) t[1];
			int[] avg = new int[1];
			byte[] spr = remap(lava, parseStops((String) t[2]), avg);
			changed += putSingle(dst, 8, sprite, spr) ? 1 : 0;
			texPut.put(tex, new byte[] { (byte) (sprite >> 8), (byte) sprite, (byte) (avg[0] >> 8), (byte) avg[0], 1, 1, 1 });
		}
		changed += PatchCustoms27.putTextures(dst, texPut);

		// model
		changed += putSingle(dst, 7, MODEL, Files.readAllBytes(new File(dir, "model_" + MODEL + ".dat").toPath())) ? 1 : 0;

		// npcs + pet item
		Map<Integer, byte[]> npcs = RoundTripDefs.files(dst, 9), items = RoundTripDefs.files(dst, 10);
		for (int id : new int[] { BOSS, PET })
			check(npcs.get(id) == null || new String(npcs.get(id), StandardCharsets.ISO_8859_1).contains(BOSS_NAME), "npc " + id + " is taken");
		check(items.get(PET_ITEM) == null || new String(items.get(PET_ITEM), StandardCharsets.ISO_8859_1).contains(PET_NAME), "item " + PET_ITEM + " is taken");
		Map<Integer, byte[]> npcPut = new TreeMap<>();
		npcPut.put(BOSS, PatchCustoms27.npc(MODEL, 4, 256, 328, STAND, WALK, BOSS_NAME));
		npcPut.put(PET, pet());
		changed += PatchCustoms27.putConfigs(dst, 9, npcPut);
		changed += PatchCustoms27.putConfigs(dst, 10, Map.of(PET_ITEM, petItem(items.get(PET_ITEM_TEMPLATE))));

		dst.close();
		System.out.println("RESULT solarmane: " + changed + " change(s)");
	}

	/** The pet follower: as Aggy's (22038): scale 64, Talk-to / Pick-up, level 0, no minimap dot. */
	static byte[] pet() {
		ByteArrayOutputStream b = new ByteArrayOutputStream();
		b.write(61);
		b.write(1);
		PatchCustoms27.i32(b, MODEL);
		b.write(2);
		b.writeBytes(PET_NAME.getBytes(StandardCharsets.ISO_8859_1));
		b.write(0);
		b.write(13);
		PatchCustoms27.u16(b, STAND);
		b.write(14);
		PatchCustoms27.u16(b, WALK);
		b.write(30);
		b.writeBytes("Talk-to".getBytes(StandardCharsets.ISO_8859_1));
		b.write(0);
		b.write(32);
		b.writeBytes("Pick-up".getBytes(StandardCharsets.ISO_8859_1));
		b.write(0);
		b.write(93); // no minimap dot
		b.write(95);
		PatchCustoms27.u16(b, 0);
		b.write(97);
		PatchCustoms27.u16(b, 64);
		b.write(98);
		PatchCustoms27.u16(b, 64);
		b.write(0);
		byte[] out = b.toByteArray();
		DefRewriter.rewrite("npc", out, DefRewriter.IDENTITY, k -> null);
		return out;
	}

	/** Aggy's pet item with Solarmane's model, name, examine and icon framing. */
	static byte[] petItem(byte[] template) {
		check(template != null, "pet item template " + PET_ITEM_TEMPLATE + " missing");
		byte[] base = PatchCustoms27.item(template, MODEL, MODEL, PET_NAME, PET_FRAMING, null);
		ByteArrayOutputStream b = new ByteArrayOutputStream();
		for (byte[] seg : DefRewriter.segments("item", base, DefRewriter.IDENTITY, k -> null)) {
			if ((seg[0] & 0xff) == 3) { // examine
				b.write(3);
				b.writeBytes(PET_EXAMINE.getBytes(StandardCharsets.ISO_8859_1));
				b.write(0);
			} else
				b.writeBytes(seg);
		}
		b.write(0);
		byte[] out = b.toByteArray();
		DefRewriter.rewrite("item", out, DefRewriter.IDENTITY, k -> null);
		return out;
	}

	/** The sprite with every palette colour replaced by the gradient at its heat; avg[0] = the mean colour as HSL16. */
	static byte[] remap(byte[] spr, double[][] stops, int[] avg) {
		int frames = (spr[spr.length - 2] & 0xff) << 8 | (spr[spr.length - 1] & 0xff);
		check(frames == 1, "texture sprite must have one frame");
		int hdr = spr.length - 7 - frames * 8;
		int palLen = (spr[hdr + 4] & 0xff) + 1;
		int pal = hdr - (palLen - 1) * 3;
		double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
		for (int i = 0; i < palLen - 1; i++) {
			double h = heat(rgbAt(spr, pal + i * 3));
			lo = Math.min(lo, h);
			hi = Math.max(hi, h);
		}
		byte[] out = spr.clone();
		int[] newPal = new int[palLen];
		for (int i = 0; i < palLen - 1; i++) {
			int n = gradient(stops, (heat(rgbAt(spr, pal + i * 3)) - lo) / (hi - lo));
			if (n == 0)
				n = 0x010101; // 0 is the transparent colour
			newPal[i + 1] = n;
			out[pal + i * 3] = (byte) (n >> 16);
			out[pal + i * 3 + 1] = (byte) (n >> 8);
			out[pal + i * 3 + 2] = (byte) n;
		}
		// the average colour (the client's flat-shade fallback): the mean of the new palette
		long r = 0, g = 0, bl = 0, n = 0;
		for (int i = 1; i < palLen; i++) {
			r += newPal[i] >> 16 & 0xff;
			g += newPal[i] >> 8 & 0xff;
			bl += newPal[i] & 0xff;
			n++;
		}
		avg[0] = rgbToHsl16((int) (r / n) << 16 | (int) (g / n) << 8 | (int) (bl / n));
		return out;
	}

	static Map<Integer, byte[]> textures(Store s) throws Exception {
		Archive ar = s.findIndex(9).getArchive(0);
		FileData[] fd = ar.getFileData();
		List<byte[]> chunks = SpliceItemOption.splitChunks(ar.decompress(s.getStorage().loadArchive(ar)), fd.length);
		Map<Integer, byte[]> m = new HashMap<>();
		for (int i = 0; i < fd.length; i++)
			m.put(fd[i].getId(), chunks.get(i));
		return m;
	}

	static byte[] archive(Store s, int index, int id) throws Exception {
		Archive ar = s.findIndex(index).getArchive(id);
		check(ar != null, index + "/" + id + " missing");
		return ar.decompress(s.getStorage().loadArchive(ar));
	}

	/**
	 * Writes a single-file archive (file id 0, as models and sprites must be); false if it already holds these bytes.
	 * Only this stage's own ids are ever passed, so an older version of them is replaced (revision bumped).
	 */
	static boolean putSingle(Store dst, int index, int id, byte[] raw) throws Exception {
		check(id < 65536, index + "/" + id + ": group ids are served as 16 bits");
		Index idx = dst.findIndex(index);
		Archive ar = idx.getArchive(id);
		int revision = 1;
		if (ar != null) {
			if (Arrays.equals(ar.decompress(dst.getStorage().loadArchive(ar)), raw))
				return false;
			revision = ar.getRevision() + 1;
		} else {
			ar = idx.addArchive(id);
			ar.setCompression(idx.getArchives().isEmpty() ? 2 : idx.getArchives().get(0).getCompression());
		}
		Container c = new Container(ar.getCompression(), -1);
		c.compress(raw, null);
		dst.getStorage().store(index, id, c.data);
		FileData fd = new FileData();
		fd.setId(0);
		ar.setFileData(new FileData[] { fd });
		ar.setCrc(c.crc);
		ar.setRevision(revision);
		ar.setCompressedSize(c.data.length);
		ar.setDecompressedSize(raw.length);
		PatchCustoms27.writeIndex(dst, index);
		System.out.println("  archive " + index + "/" + id + ": " + raw.length + " bytes");
		return true;
	}

	static double heat(int rgb) {
		return 0.45 * (rgb >> 16 & 0xff) + 0.55 * (rgb >> 8 & 0xff);
	}

	static double[][] parseStops(String s) {
		String[] parts = s.split(",");
		double[][] r = new double[parts.length][];
		for (int i = 0; i < parts.length; i++) {
			String[] kv = parts[i].split(":");
			r[i] = new double[] { Double.parseDouble(kv[0]), Integer.parseInt(kv[1], 16) };
		}
		return r;
	}

	static int gradient(double[][] stops, double t) {
		t = Math.max(0, Math.min(1, t));
		for (int i = 1; i < stops.length; i++)
			if (t <= stops[i][0]) {
				double f = (t - stops[i - 1][0]) / (stops[i][0] - stops[i - 1][0]);
				int a = (int) stops[i - 1][1], b = (int) stops[i][1];
				return lerp(a >> 16 & 0xff, b >> 16 & 0xff, f) << 16 | lerp(a >> 8 & 0xff, b >> 8 & 0xff, f) << 8 | lerp(a & 0xff, b & 0xff, f);
			}
		return (int) stops[stops.length - 1][1];
	}

	static int lerp(int a, int b, double f) {
		return (int) Math.round(a + (b - a) * f);
	}

	/** Jagex 16-bit HSL: hue 6 bits, saturation 3, lightness 7. */
	static int rgbToHsl16(int rgb) {
		double r = (rgb >> 16 & 0xff) / 255.0, g = (rgb >> 8 & 0xff) / 255.0, b = (rgb & 0xff) / 255.0;
		double max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
		double l = (max + min) / 2, h = 0, s = 0;
		if (max != min) {
			double d = max - min;
			s = l > 0.5 ? d / (2 - max - min) : d / (max + min);
			if (max == r)
				h = ((g - b) / d + (g < b ? 6 : 0)) / 6;
			else if (max == g)
				h = ((b - r) / d + 2) / 6;
			else
				h = ((r - g) / d + 4) / 6;
		}
		return Math.min(63, (int) (h * 64)) << 10 | Math.min(7, (int) (s * 8)) << 7 | Math.min(127, (int) (l * 128));
	}

	static int rgbAt(byte[] b, int off) {
		return (b[off] & 0xff) << 16 | (b[off + 1] & 0xff) << 8 | (b[off + 2] & 0xff);
	}

	static void check(boolean ok, String msg) {
		if (!ok)
			throw new IllegalStateException("solarmane: " + msg);
	}
}

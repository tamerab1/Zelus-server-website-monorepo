import net.runelite.cache.fs.Store;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/**
 * Stage 37 of the rev 241 cache build (user, 2026-11-14): the Karn master cape, item {@link #ITEM}, a copy of the Attack
 * master cape (30232) whose attack emblem is the Karn K (texture 509, stage 28: run after it). Models 62818 (inventory) /
 * 62819 (worn) come from assets/karn_cape/MakeKarnMasterCape. The copy keeps 30232's framing, recolours, options and params;
 * the name and description change and the bank placeholder link (ops 148 / 149) is dropped (it would borrow 30232's placeholder).
 * Idempotent. Usage: PatchKarnMasterCape apply &lt;cache241&gt; &lt;assets/karn_cape&gt;
 */
public final class PatchKarnMasterCape {

	private PatchKarnMasterCape() {
	}

	static final int SOURCE = 30232, ITEM = 42766, INV_MODEL = 62818, WORN_MODEL = 62819;
	/** round 2: the brightened K emblem (assets/karn_cape/png/k_emblem_bright.png) as sprite 24890 / texture 513 */
	static final int SPRITE = 24890, TEXTURE = 513;
	static final String NAME = "Karn master cape", DESC = "A cape worn by the masters of Karn.";

	public static void main(String[] a) throws Exception {
		if (a.length != 3 || !a[0].equals("apply"))
			throw new IllegalArgumentException("apply <cache241> <assets/karn_cape>");
		Store dst = new Store(new File(a[1]));
		dst.load();
		int changed = 0;
		java.nio.file.Path png = new File(a[2], "png/k_emblem_bright.png").toPath();
		changed += PatchSolarmane.putSingle(dst, 8, SPRITE, BuildBinary241.pngSprite(png)) ? 1 : 0;
		int avg = averageHsl(javax.imageio.ImageIO.read(png.toFile()));
		byte[] rec = { (byte) (SPRITE >> 8), (byte) SPRITE, (byte) (avg >> 8), (byte) avg, 0, 0, 0 };
		if (!Arrays.equals(PatchSolarmane.textures(dst).get(TEXTURE), rec))
			changed += PatchCustoms27.putTextures(dst, new TreeMap<>(Map.of(TEXTURE, rec)));
		for (int id : new int[] { INV_MODEL, WORN_MODEL })
			changed += PatchSolarmane.putSingle(dst, 7, id, Files.readAllBytes(new File(a[2], "models/" + id + ".dat").toPath())) ? 1 : 0;
		Map<Integer, byte[]> items = RoundTripDefs.files(dst, 10);
		DefRewriter.ParamKinds pk = DefRewriter.paramKinds(RoundTripDefs.files(dst, 11));
		byte[] source = items.get(SOURCE);
		if (source == null)
			throw new IllegalStateException("item " + SOURCE + " missing");
		byte[] have = items.get(ITEM);
		if (have != null && !new String(have, StandardCharsets.ISO_8859_1).contains(NAME))
			throw new IllegalStateException("item " + ITEM + " is taken by something else");
		ByteArrayOutputStream b = new ByteArrayOutputStream();
		for (byte[] seg : DefRewriter.segments("item", source, DefRewriter.IDENTITY, pk)) {
			int op = seg[0] & 0xff;
			if (op == 0)
				break;
			switch (op) {
				case 148, 149 -> {
				}
				case 2 -> {
					b.write(2);
					b.writeBytes(NAME.getBytes(StandardCharsets.ISO_8859_1));
					b.write(0);
				}
				case 3 -> { // desc: 30232 kept the Scarecrow shirt's (the slot it reused)
					b.write(3);
					b.writeBytes(DESC.getBytes(StandardCharsets.ISO_8859_1));
					b.write(0);
				}
				case 44 -> b.writeBytes(model(op, INV_MODEL, null));
				case 45, 48 -> b.writeBytes(model(op, WORN_MODEL, seg[5])); // 4-byte model + the offset byte
				default -> b.writeBytes(seg);
			}
		}
		b.write(0);
		changed += PatchCustoms27.putConfigs(dst, 10, new TreeMap<>(Map.of(ITEM, b.toByteArray())));
		dst.close();
		System.out.println("RESULT karn master cape: " + changed + " change(s); item " + ITEM + " \"" + NAME + "\", models " + INV_MODEL + " / " + WORN_MODEL);
	}

	/** a texture's average colour as packed HSL (as MakeKarnBanners) */
	static int averageHsl(java.awt.image.BufferedImage im) {
		long r = 0, g = 0, bl = 0, n = 0;
		for (int y = 0; y < im.getHeight(); y++)
			for (int x = 0; x < im.getWidth(); x++) {
				int p = im.getRGB(x, y);
				if ((p >>> 24) < 128)
					continue;
				r += (p >> 16) & 0xff;
				g += (p >> 8) & 0xff;
				bl += p & 0xff;
				n++;
			}
		if (n == 0)
			return 0;
		double rd = r / (double) n / 255, gd = g / (double) n / 255, bd = bl / (double) n / 255;
		double max = Math.max(rd, Math.max(gd, bd)), min = Math.min(rd, Math.min(gd, bd)), l = (max + min) / 2, h = 0, s = 0;
		if (max != min) {
			double d = max - min;
			s = l > 0.5 ? d / (2 - max - min) : d / (max + min);
			h = max == rd ? (gd - bd) / d + (gd < bd ? 6 : 0) : max == gd ? (bd - rd) / d + 2 : (rd - gd) / d + 4;
			h /= 6;
		}
		return ((int) (h * 63) & 63) << 10 | ((int) (s * 7) & 7) << 7 | ((int) (l * 127) & 127);
	}

	static byte[] model(int op, int id, Byte offset) {
		ByteArrayOutputStream b = new ByteArrayOutputStream();
		b.write(op);
		b.write(id >>> 24);
		b.write(id >> 16 & 0xff);
		b.write(id >> 8 & 0xff);
		b.write(id & 0xff);
		if (offset != null)
			b.write(offset);
		return b.toByteArray();
	}
}

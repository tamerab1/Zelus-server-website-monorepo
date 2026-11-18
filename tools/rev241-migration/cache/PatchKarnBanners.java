import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.runelite.cache.fs.Store;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/**
 * Stage 28 of the rev 241 cache build (2026-11-11): the Karn banners, from assets/banners (made by its MakeKarnBanners):
 * sprites 24812-24815, textures 509-512 (records as in PatchBeastRecolour), models 62816-62817, and two objects cloned
 * from the standing banner 39153 with those models: {@link #K_BANNER} "Karn banner" (the K emblem) and
 * {@link #LOGO_BANNER} "Karn standard" (the full logo on a wider cloth, drawn {@link #LOGO_SCALE}/128 the size).
 * Idempotent. Usage: PatchKarnBanners apply &lt;cache241&gt; &lt;assets/banners&gt;
 */
public final class PatchKarnBanners {

	private PatchKarnBanners() {
	}

	static final int SOURCE_LOC = 39153, SOURCE_MODEL = 40355, K_BANNER = 65042, LOGO_BANNER = 65043, K_MODEL = 62816, LOGO_MODEL = 62817,
			LOGO_SCALE = 154;

	public static void main(String[] a) throws Exception {
		if (a.length != 3 || !a[0].equals("apply"))
			throw new IllegalArgumentException("apply <cache241> <assets/banners>");
		File dir = new File(a[2]);
		Store dst = new Store(new File(a[1]));
		dst.load();
		int changed = 0;
		for (File f : new File(dir, "sprites").listFiles())
			changed += PatchSolarmane.putSingle(dst, 8, id(f), Files.readAllBytes(f.toPath())) ? 1 : 0;
		Map<Integer, byte[]> have = PatchSolarmane.textures(dst), put = new TreeMap<>();
		for (JsonElement e : JsonParser.parseString(Files.readString(new File(dir, "textures.json").toPath())).getAsJsonArray()) {
			JsonObject t = e.getAsJsonObject();
			int sprite = t.get("sprite").getAsInt(), avg = t.get("average").getAsInt();
			byte[] rec = { (byte) (sprite >> 8), (byte) sprite, (byte) (avg >> 8), (byte) avg, t.get("opaque").getAsByte(), t.get("direction").getAsByte(),
					t.get("speed").getAsByte() };
			if (!Arrays.equals(have.get(t.get("id").getAsInt()), rec))
				put.put(t.get("id").getAsInt(), rec);
		}
		if (!put.isEmpty())
			changed += PatchCustoms27.putTextures(dst, put);
		for (File f : new File(dir, "models").listFiles())
			changed += PatchSolarmane.putSingle(dst, 7, id(f), Files.readAllBytes(f.toPath())) ? 1 : 0;

		Map<Integer, byte[]> locs = RoundTripDefs.files(dst, 6);
		byte[] source = locs.get(SOURCE_LOC);
		if (source == null)
			throw new IllegalStateException("object " + SOURCE_LOC + " missing");
		DefRewriter.ParamKinds pk = DefRewriter.paramKinds(RoundTripDefs.files(dst, 11));
		Map<Integer, byte[]> defs = new TreeMap<>();
		defs.put(K_BANNER, clone(source, K_MODEL, "Karn banner", 0, pk));
		defs.put(LOGO_BANNER, clone(source, LOGO_MODEL, "Karn standard", LOGO_SCALE, pk));
		for (var e : defs.entrySet())
			if (locs.containsKey(e.getKey()) && !Arrays.equals(locs.get(e.getKey()), e.getValue()) && !isOurs(locs.get(e.getKey())))
				throw new IllegalStateException("object " + e.getKey() + " is taken by something else");
		changed += PatchCustoms27.putConfigs(dst, 6, defs);
		dst.close();
		System.out.println("RESULT karn banners: " + changed + " change(s); objects " + K_BANNER + " (K) and " + LOGO_BANNER + " (KARN)");
	}

	/** Object 39153's definition with our model, our name and, when scale > 0, a uniform model scale (ops 65-67). */
	static byte[] clone(byte[] source, int model, String name, int scale, DefRewriter.ParamKinds pk) {
		byte[] withModel = DefRewriter.rewrite("obj", source, (kind, id) -> kind.equals("model") && id == SOURCE_MODEL ? model : id, pk);
		ByteArrayOutputStream b = new ByteArrayOutputStream();
		b.write(2);
		b.writeBytes(name.getBytes(StandardCharsets.ISO_8859_1));
		b.write(0);
		if (scale > 0)
			for (int op = 65; op <= 67; op++) {
				b.write(op);
				b.write(scale >> 8);
				b.write(scale & 0xff);
			}
		for (byte[] seg : DefRewriter.segments("obj", withModel, DefRewriter.IDENTITY, pk)) {
			int op = seg[0] & 0xff;
			if (op == 0)
				break;
			if (op == 2 || (scale > 0 && op >= 65 && op <= 67))
				continue; // replaced above
			b.writeBytes(seg);
		}
		b.write(0);
		return b.toByteArray();
	}

	static boolean isOurs(byte[] def) {
		String s = new String(def, StandardCharsets.ISO_8859_1);
		return s.contains("Karn banner") || s.contains("Karn standard");
	}

	private static int id(File f) {
		return Integer.parseInt(f.getName().replace(".dat", ""));
	}
}

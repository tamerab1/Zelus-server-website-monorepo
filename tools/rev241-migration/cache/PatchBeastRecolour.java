import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.runelite.cache.fs.Store;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;

/**
 * Stage 25 of the rev 241 cache build (2026-11-08), after stages 15 and 20: the Beast icon in crimson, from assets/beast_recolour
 * (made by its MakeBeastRecolour): sprites 24800-24801, textures 507-508 (record as in PatchEggIcons) and Beast's six models.
 * Idempotent. Usage: PatchBeastRecolour apply &lt;cache241&gt; &lt;assets/beast_recolour&gt;
 */
public final class PatchBeastRecolour {

	private PatchBeastRecolour() {
	}

	public static void main(String[] a) throws Exception {
		if (a.length != 3 || !a[0].equals("apply"))
			throw new IllegalArgumentException("apply <cache241> <assets/beast_recolour>");
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
		dst.close();
		System.out.println("RESULT beast recolour: " + changed + " change(s)");
	}

	private static int id(File f) {
		return Integer.parseInt(f.getName().replace(".dat", ""));
	}
}

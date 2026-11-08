import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.loaders.ModelLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Store;

import java.awt.Color;
import java.io.File;
import java.nio.file.Files;

/**
 * Beast icon recolour (2026-11-08, the user: Beast and Shade looked the same). Beast shares its textures 484 / 492 with other
 * model-bank items, so it gets its own crimson copies, 507 / 508 (sprites 24800 / 24801), and its six models (worn t1, inventory,
 * worn t2-t5) point at them. Run with the rev 241 cache library on a cache that has stages 15 and 20. Writes sprites/, models/
 * and textures.json for cache stage 25 PatchBeastRecolour. Usage: MakeBeastRecolour &lt;cache241&gt; &lt;assets/beast_recolour&gt;
 */
public final class MakeBeastRecolour {

	static final int[][] TEXTURES = { { 484, 507, 24753, 24800 }, { 492, 508, 24761, 24801 } }; // old texture, new texture, old sprite, new sprite
	static final int[] MODELS = { 63271, 63272, 63409, 63410, 63411, 63412 };
	/** crimson, as a Color hue and as the 6 bits of a packed HSL colour */
	static final float HUE = 0.975f;
	static final int HSL_HUE = 62;

	public static void main(String[] a) throws Exception {
		File out = new File(a[1]);
		new File(out, "sprites").mkdirs();
		new File(out, "models").mkdirs();
		JsonArray tex = new JsonArray();
		try (Store s = new Store(new File(a[0]))) {
			s.load();
			var textures = PatchSolarmane.textures(s);
			for (int[] t : TEXTURES) {
				Archive ar = s.findIndex(8).getArchive(t[2]);
				SpriteCodec.Group g = SpriteCodec.decode(ar.decompress(s.getStorage().loadArchive(ar)));
				for (int i = 1; i < g.palette.length; i++)
					g.palette[i] = crimson(g.palette[i]);
				byte[] sprite = SpriteCodec.encode(g);
				if (!BuildBinary241.validTextureSprite(t[3], sprite))
					throw new IllegalStateException("bad sprite " + t[3]);
				Files.write(new File(out, "sprites/" + t[3] + ".dat").toPath(), sprite);
				byte[] old = textures.get(t[0]);
				int average = ((old[2] & 0xff) << 8 | old[3] & 0xff) & 0x3ff | HSL_HUE << 10;
				JsonObject j = new JsonObject();
				j.addProperty("id", t[1]);
				j.addProperty("sprite", t[3]);
				j.addProperty("average", average);
				j.addProperty("opaque", old[4]);
				j.addProperty("direction", old[5]);
				j.addProperty("speed", old[6]);
				tex.add(j);
			}
			for (int id : MODELS) {
				Archive ar = s.findIndex(7).getArchive(id);
				ModelDefinition m = new ModelLoader().load(id, ar.decompress(s.getStorage().loadArchive(ar)));
				int swapped = 0;
				for (int f = 0; f < m.faceCount; f++)
					for (int[] t : TEXTURES)
						if (m.faceTextures[f] == t[0]) {
							m.faceTextures[f] = (short) t[1];
							swapped++;
						}
				if (swapped != m.faceCount && !alreadyDone(m))
					throw new IllegalStateException("model " + id + ": only " + swapped + " of " + m.faceCount + " faces use Beast's textures");
				Files.write(new File(out, "models/" + id + ".dat").toPath(), ModelType3Encoder.encode(m));
			}
		}
		Files.writeString(new File(out, "textures.json").toPath(), new GsonBuilder().setPrettyPrinting().create().toJson(tex) + "\n");
		System.out.println("beast recolour: 2 textures, " + MODELS.length + " models");
	}

	static boolean alreadyDone(ModelDefinition m) {
		for (int f = 0; f < m.faceCount; f++)
			if (m.faceTextures[f] != 507 && m.faceTextures[f] != 508)
				return false;
		return true;
	}

	/** the same light and dark, in crimson; greys stay grey */
	static int crimson(int rgb) {
		float[] hsb = Color.RGBtoHSB(rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, null);
		if (hsb[1] < 0.12f)
			return rgb & 0xffffff;
		return Color.HSBtoRGB(HUE, Math.max(hsb[1], 0.7f), hsb[2]) & 0xffffff;
	}
}

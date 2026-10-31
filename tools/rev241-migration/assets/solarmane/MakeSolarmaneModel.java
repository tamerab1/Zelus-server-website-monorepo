import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.loaders.ModelLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Store;

import java.io.File;
import java.nio.file.Files;
import java.util.*;

/**
 * Builds model 62802, Solarmane (2026-10-31): the Sabre-toothed kyatt (model 19375, unchanged between rev 231 and 241)
 * with two custom textures, as TextureModelFaces but with a texture per colour group: 478 (sunfire gold) on the body
 * (38003 flank, 41069 belly / legs) and 479 (royal crimson) on the back ridge (32889), read as a mane stripe. The
 * head's eyes, mouth and sabre fangs stay plain colour. The vertex and face skins come from kyatt_skins.txt (step 1,
 * DumpKyattSkins, the rev 241 decode), so the kyatt's animations still drive it; step 3, VerifySolarmaneModel, checks
 * the result with the rev 241 library.
 * Not part of the cache build (it needs the .dev ModelType3Encoder): its output, model_62802.dat, is the asset that
 * PatchSolarmane writes. Usage: MakeSolarmaneModel &lt;cache with model 19375&gt; &lt;assets/solarmane&gt;
 */
public class MakeSolarmaneModel {

	static final int SRC = 19375, DST = 62802;
	static final Map<Integer, Short> TEXTURE_BY_COLOUR = Map.of(
			38003, (short) 478, 41069, (short) 478,
			32889, (short) 479);

	public static void main(String[] a) throws Exception {
		try (Store store = new Store(new File(a[0]))) {
			store.load();
			Archive ar = store.findIndex(7).getArchive(SRC);
			byte[] raw = ar.decompress(store.getStorage().loadArchive(ar));
			ModelDefinition m = new ModelLoader().load(SRC, raw), orig = new ModelLoader().load(SRC, raw);
			short[] ft = new short[m.faceCount];
			Arrays.fill(ft, (short) -1);
			Map<Short, Integer> count = new TreeMap<>();
			for (int f = 0; f < m.faceCount; f++) {
				Short t = TEXTURE_BY_COLOUR.get(m.faceColors[f] & 0xffff);
				if (t != null) {
					ft[f] = t;
					count.merge(t, 1, Integer::sum);
				}
			}
			m.faceTextures = ft;
			m.textureCoords = null;
			// the skins as the rev 241 client reads them (DumpKyattSkins): this library decodes the kyatt's format into
			// other labels, and a model encoded with those crumples out of the kyatt's animations
			List<String> skins = Files.readAllLines(new File(a[1], "kyatt_skins.txt").toPath());
			int[] labels = Arrays.stream(skins.get(0).split(",")).mapToInt(Integer::parseInt).toArray();
			check(labels.length == m.vertexCount, "kyatt_skins.txt has " + labels.length + " labels for " + m.vertexCount + " vertices");
			m.packedVertexGroups = labels;
			if (skins.size() > 1 && !skins.get(1).isEmpty()) {
				int[] faceSkins = Arrays.stream(skins.get(1).split(",")).mapToInt(Integer::parseInt).toArray();
				check(faceSkins.length == m.faceCount, "kyatt_skins.txt has " + faceSkins.length + " face skins for " + m.faceCount + " faces");
				m.packedTransparencyVertexGroups = faceSkins;
			}
			byte[] enc = ModelType3Encoder.encode(m);
			ModelDefinition back = new ModelLoader().load(DST, enc);
			check(back.vertexCount == orig.vertexCount && back.faceCount == orig.faceCount, "counts changed");
			check(Arrays.equals(back.vertexX, orig.vertexX) && Arrays.equals(back.vertexY, orig.vertexY)
					&& Arrays.equals(back.vertexZ, orig.vertexZ), "vertices changed");
			check(Arrays.equals(back.faceIndices1, orig.faceIndices1) && Arrays.equals(back.faceIndices2, orig.faceIndices2)
					&& Arrays.equals(back.faceIndices3, orig.faceIndices3), "faces changed");
			check(Arrays.equals(back.faceColors, orig.faceColors), "colours changed");
			check(Arrays.equals(back.faceTextures, ft), "face textures differ after encode");
			// (the skins are checked against the rev 241 decode of model 19375 by VerifySolarmaneModel)
			File out = new File(a[1], "model_" + DST + ".dat");
			Files.write(out.toPath(), enc);
			System.out.println("model " + SRC + " -> " + DST + ": textured faces " + count + " of " + m.faceCount + ", "
					+ enc.length + " bytes, skin groups " + (orig.getVertexGroups() == null ? 0 : orig.getVertexGroups().length));
		}
	}

	static void check(boolean ok, String msg) {
		if (!ok)
			throw new IllegalStateException("model " + DST + ": " + msg);
	}
}

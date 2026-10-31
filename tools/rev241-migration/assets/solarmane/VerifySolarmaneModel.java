import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.loaders.ModelLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Store;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;

/**
 * Step 3 of the Solarmane model (run with the rev 241 cache library): decodes model_62802.dat the way the rev 241
 * client does and checks it against the kyatt (19375) in that same decode: vertices, faces, colours, vertex skin
 * groups and face skins identical, and textures 478 / 479 on 848 faces. Usage: VerifySolarmaneModel &lt;cache241&gt;
 * &lt;assets/solarmane&gt;
 */
public class VerifySolarmaneModel {

	public static void main(String[] a) throws Exception {
		try (Store store = new Store(new File(a[0]))) {
			store.load();
			Archive ar = store.findIndex(7).getArchive(19375);
			ModelDefinition o = new ModelLoader().load(19375, ar.decompress(store.getStorage().loadArchive(ar)));
			ModelDefinition n = new ModelLoader().load(62802, Files.readAllBytes(new File(a[1], "model_62802.dat").toPath()));
			check(Arrays.equals(o.vertexX, n.vertexX) && Arrays.equals(o.vertexY, n.vertexY) && Arrays.equals(o.vertexZ, n.vertexZ), "vertices");
			check(Arrays.equals(o.faceIndices1, n.faceIndices1) && Arrays.equals(o.faceIndices2, n.faceIndices2)
					&& Arrays.equals(o.faceIndices3, n.faceIndices3), "faces");
			check(Arrays.equals(o.faceColors, n.faceColors), "colours");
			check(Arrays.equals(o.faceRenderPriorities, n.faceRenderPriorities), "priorities");
			check(Arrays.deepEquals(o.getVertexGroups(), n.getVertexGroups()), "vertex skin groups");
			check(Arrays.equals(o.packedTransparencyVertexGroups, n.packedTransparencyVertexGroups), "face skins");
			int textured = 0;
			for (short t : n.faceTextures)
				if (t == 478 || t == 479)
					textured++;
			check(textured == 848, "textured faces " + textured);
			System.out.println("model 62802 OK in the rev 241 decode: geometry, colours and skins match 19375 ("
					+ o.getVertexGroups().length + " skin groups), 848 textured faces");
		}
	}

	static void check(boolean ok, String what) {
		if (!ok)
			throw new IllegalStateException("model 62802: " + what + " differ from 19375 in the rev 241 decode");
	}
}

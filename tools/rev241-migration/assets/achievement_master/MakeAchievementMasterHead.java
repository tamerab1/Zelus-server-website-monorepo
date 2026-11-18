import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.loaders.ModelLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Store;

import java.io.File;
import java.nio.file.Files;

/**
 * The Achievement Master's chathead (user, 2026-11-14), run with the rev 241 cache library like MakeKarnMasterCape: the
 * Valkyrie helm's worn model (63392, a full helm with a face mask; the item's own head model 63346 is broken, +-1144 wide)
 * moved to the origin and scaled 2.5x, the size of the kit chatheads (head 63: x +-44, y -72..54). Writes models/62820.dat.
 * Usage: MakeAchievementMasterHead &lt;cache241&gt; &lt;assets/achievement_master&gt;
 */
public class MakeAchievementMasterHead {
	static final int SOURCE = 63392, TARGET = 62820, CENTRE_Y = -200, CENTRE_Z = 3;
	static final double SCALE = 2.5;

	public static void main(String[] a) throws Exception {
		File dir = new File(a[1], "models");
		dir.mkdirs();
		try (Store s = new Store(new File(a[0]))) {
			s.load();
			Archive ar = s.findIndex(7).getArchive(SOURCE);
			ModelDefinition m = new ModelLoader().load(TARGET, ar.decompress(s.getStorage().loadArchive(ar)));
			for (int v = 0; v < m.vertexCount; v++) {
				m.vertexX[v] = (int) Math.round(m.vertexX[v] * SCALE);
				m.vertexY[v] = (int) Math.round((m.vertexY[v] - CENTRE_Y) * SCALE);
				m.vertexZ[v] = (int) Math.round((m.vertexZ[v] - CENTRE_Z) * SCALE);
			}
			byte[] enc = ModelType3Encoder.encode(m);
			ModelDefinition back = new ModelLoader().load(TARGET, enc);
			if (back.faceCount != m.faceCount || back.vertexCount != m.vertexCount)
				throw new IllegalStateException("encode/decode mismatch");
			Files.write(new File(dir, TARGET + ".dat").toPath(), enc);
			System.out.println("model " + TARGET + " (helm " + SOURCE + " x" + SCALE + "): " + m.vertexCount + " vertices, " + m.faceCount + " faces");
		}
	}
}

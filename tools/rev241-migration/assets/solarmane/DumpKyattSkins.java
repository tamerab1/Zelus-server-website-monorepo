import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.loaders.ModelLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Store;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Step 1 of the Solarmane model (run with the rev 241 cache library, client rev241/cache/build/libs/cache-1.13.4.jar):
 * writes kyatt_skins.txt, the per-vertex skin labels and per-face skins of model 19375 as the rev 241 client decodes
 * them. The old cache library MakeSolarmaneModel encodes with reads this model's format (footer -2) into different
 * labels (74 groups instead of 87, face skins garbled), which left the first Solarmane crumpled out of its animations
 * (2026-10-31); MakeSolarmaneModel now takes the skins from this file.
 * Usage: DumpKyattSkins &lt;cache241&gt; &lt;outDir&gt;
 */
public class DumpKyattSkins {

	public static void main(String[] a) throws Exception {
		try (Store store = new Store(new File(a[0]))) {
			store.load();
			Archive ar = store.findIndex(7).getArchive(MakeSolarmaneModelIds.SRC);
			ModelDefinition m = new ModelLoader().load(MakeSolarmaneModelIds.SRC, ar.decompress(store.getStorage().loadArchive(ar)));
			int[] labels = new int[m.vertexCount];
			Arrays.fill(labels, -1);
			int[][] groups = m.getVertexGroups();
			for (int g = 0; g < groups.length; g++)
				for (int v : groups[g]) {
					if (labels[v] != -1)
						throw new IllegalStateException("vertex " + v + " in two skin groups");
					labels[v] = g;
				}
			for (int v = 0; v < labels.length; v++)
				if (labels[v] == -1)
					throw new IllegalStateException("vertex " + v + " has no skin group");
			String faces = m.packedTransparencyVertexGroups == null ? ""
					: Arrays.stream(m.packedTransparencyVertexGroups).mapToObj(String::valueOf).collect(Collectors.joining(","));
			Files.writeString(new File(a[1], "kyatt_skins.txt").toPath(),
					Arrays.stream(labels).mapToObj(String::valueOf).collect(Collectors.joining(",")) + "\n" + faces + "\n");
			System.out.println("kyatt skins: " + labels.length + " vertex labels (" + groups.length + " groups), "
					+ (m.packedTransparencyVertexGroups == null ? 0 : m.packedTransparencyVertexGroups.length) + " face skins");
		}
	}
}

/** The source model id, shared without depending on the other step's library. */
final class MakeSolarmaneModelIds {
	static final int SRC = 19375;
}

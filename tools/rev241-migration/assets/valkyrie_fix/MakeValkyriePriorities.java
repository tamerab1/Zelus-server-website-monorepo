import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.loaders.ModelLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Store;

import java.io.File;
import java.nio.file.Files;
import java.util.*;

/**
 * Face render priorities for the Valkyrie set (user, 2026-11-14: a master cape "broken" over the Valkyrie body). The
 * model bank pack ripped every face of helm / body / legs onto priority 10, which the client interleaves by depth with
 * everything else, so a cape's side panel drew over the torso. Real gear layers by body part (Torva: torso 3, arms 10,
 * legs 1, helm 6): body faces more than {@link #ARM_X} out to the side (arms, pauldrons) stay 10, the rest 3; legs 1;
 * helm 6. Re-encoded with vertex skins re-packed and checked on a full decode (vertices, faces, colours, skins).
 * Writes models/*.dat for stage 40 (PatchModelFiles). Run with the rev 241 cache library.
 * Usage: MakeValkyriePriorities &lt;cache241&gt; &lt;assets/valkyrie_fix&gt;
 */
public class MakeValkyriePriorities {
	static final int[] HELMS = { 63392, 63393 }, BODIES = { 63394, 63395 }, LEGS = { 63396, 63397 };
	static final int ARM_X = 20;

	public static void main(String[] a) throws Exception {
		File dir = new File(a[1], "models");
		dir.mkdirs();
		try (Store s = new Store(new File(a[0]))) {
			s.load();
			for (int id : HELMS)
				fix(s, dir, id, 0);
			for (int id : BODIES)
				fix(s, dir, id, 1);
			for (int id : LEGS)
				fix(s, dir, id, 2);
		}
	}

	static void fix(Store s, File dir, int id, int kind) throws Exception {
		Archive ar = s.findIndex(7).getArchive(id);
		byte[] raw = ar.decompress(s.getStorage().loadArchive(ar));
		ModelDefinition orig = new ModelLoader().load(id, raw), m = new ModelLoader().load(id, raw);
		byte[] pri = new byte[m.faceCount];
		Map<Integer, Integer> hist = new TreeMap<>();
		for (int f = 0; f < m.faceCount; f++) {
			int p;
			if (kind == 0)
				p = 6;
			else if (kind == 2)
				p = 1;
			else {
				double cx = (Math.abs(m.vertexX[m.faceIndices1[f]]) + Math.abs(m.vertexX[m.faceIndices2[f]]) + Math.abs(m.vertexX[m.faceIndices3[f]])) / 3.0;
				p = cx > ARM_X ? 10 : 3;
			}
			pri[f] = (byte) p;
			hist.merge(p, 1, Integer::sum);
		}
		m.faceRenderPriorities = pri;
		m.priority = 0;
		int[][] groups = m.getVertexGroups();
		if (groups != null) {
			int[] packed = new int[m.vertexCount];
			for (int g = 0; g < groups.length; g++)
				if (groups[g] != null)
					for (int v : groups[g])
						packed[v] = g;
			m.packedVertexGroups = packed;
		}
		byte[] enc = ModelType3Encoder.encode(m);
		ModelDefinition back = new ModelLoader().load(id, enc);
		check(back.vertexCount == orig.vertexCount && back.faceCount == orig.faceCount, id + ": counts");
		check(Arrays.equals(back.vertexX, orig.vertexX) && Arrays.equals(back.vertexY, orig.vertexY) && Arrays.equals(back.vertexZ, orig.vertexZ), id + ": vertices");
		check(Arrays.equals(back.faceIndices1, orig.faceIndices1) && Arrays.equals(back.faceIndices2, orig.faceIndices2)
				&& Arrays.equals(back.faceIndices3, orig.faceIndices3), id + ": faces");
		check(Arrays.equals(back.faceColors, orig.faceColors), id + ": colours");
		check(Arrays.equals(back.faceTextures, orig.faceTextures), id + ": textures");
		check(Arrays.deepEquals(back.getVertexGroups(), orig.getVertexGroups()), id + ": vertex skins");
		check(Arrays.equals(back.packedTransparencyVertexGroups, orig.packedTransparencyVertexGroups), id + ": face skins");
		check(Arrays.equals(back.faceRenderPriorities, pri), id + ": priorities");
		Files.write(new File(dir, id + ".dat").toPath(), enc);
		System.out.println("model " + id + ": priorities " + hist + " (all 10 before), skins kept");
	}

	static void check(boolean ok, String what) {
		if (!ok)
			throw new IllegalStateException("round-trip failed: " + what);
	}
}

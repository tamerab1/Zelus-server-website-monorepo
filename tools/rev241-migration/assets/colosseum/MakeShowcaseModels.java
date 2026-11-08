import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.loaders.ModelLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Store;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;

/**
 * The Colosseum trial picker's showcase models (2026-11-07), run with the rev 241 cache library. A model widget draws a
 * model around its origin and has no offsets, and the bosses' models have it at their feet, so in the picker they stood
 * above the box's centre and the tall ones were cut off. These are copies with the bounding box centred on the origin
 * (vertex skins and face skins kept, so the stand animations still play), written to showcase/&lt;id&gt;.dat, and
 * cameras.txt: per boss the copy and a zoom for each box size (screen size ~ 512 * size / zoom, the near half of the
 * depth added for perspective). Usage: MakeShowcaseModels &lt;cache241&gt; &lt;assets/colosseum&gt;
 */
public class MakeShowcaseModels {
	static final int BASE = 63472;
	/** npc, model, in the picker's order */
	static final int[][] BOSSES = { { 21941, 65424 }, { 21923, 65406 }, { 21924, 65407 }, { 21921, 65404 }, { 21925, 65408 },
			{ 21928, 65411 }, { 21933, 65416 }, { 21926, 65409 }, { 21939, 65422 }, { 21929, 65412 }, { 21938, 65421 },
			{ 21942, 65425 }, { 21943, 65426 }, { 21944, 65427 }, { 21940, 65423 }, { 21934, 65417 }, { 21946, 65429 },
			{ 21945, 65428 } };
	/** the boxes (inner px, a margin left): showcase stage, line-up thumbnail */
	static final int[][] BOXES = { { 126, 106 }, { 36, 36 } }; // round 5: the stage is 136 x 116 (126 x 106 inside)

	public static void main(String[] a) throws Exception {
		File out = new File(a[1], "showcase");
		out.mkdirs();
		StringBuilder cams = new StringBuilder("# npc model showcaseModel zoomStage zoomThumb (MakeShowcaseModels)\n");
		try (Store s = new Store(new File(a[0]))) {
			s.load();
			for (int i = 0; i < BOSSES.length; i++) {
				int npc = BOSSES[i][0], src = BOSSES[i][1], dst = BASE + i;
				Archive ar = s.findIndex(7).getArchive(src);
				byte[] raw = ar.decompress(s.getStorage().loadArchive(ar));
				ModelDefinition m = new ModelLoader().load(src, raw), o = new ModelLoader().load(src, raw);
				int[] min = { Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE }, max = { Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE };
				for (int v = 0; v < m.vertexCount; v++) {
					int[] p = { m.vertexX[v], m.vertexY[v], m.vertexZ[v] };
					for (int k = 0; k < 3; k++) {
						min[k] = Math.min(min[k], p[k]);
						max[k] = Math.max(max[k], p[k]);
					}
				}
				int cx = (min[0] + max[0]) / 2, cy = (min[1] + max[1]) / 2, cz = (min[2] + max[2]) / 2;
				for (int v = 0; v < m.vertexCount; v++) {
					m.vertexX[v] -= cx;
					m.vertexY[v] -= cy;
					m.vertexZ[v] -= cz;
				}
				// the loader hands skins back as per-label lists and drops the packed form: repack for the encoder
				int[][] g = m.getVertexGroups();
				if (g != null) {
					int[] packed = new int[m.vertexCount];
					Arrays.fill(packed, -1);
					for (int k = 0; k < g.length; k++)
						for (int v : g[k])
							packed[v] = k;
					m.packedVertexGroups = packed;
				}
				byte[] enc = ModelType3Encoder.encode(m);
				ModelDefinition b = new ModelLoader().load(dst, enc);
				for (int v = 0; v < m.vertexCount; v++)
					chk(b.vertexX[v] == o.vertexX[v] - cx && b.vertexY[v] == o.vertexY[v] - cy && b.vertexZ[v] == o.vertexZ[v] - cz, dst, "vertices");
				chk(Arrays.equals(b.faceIndices1, o.faceIndices1) && Arrays.equals(b.faceIndices2, o.faceIndices2)
						&& Arrays.equals(b.faceIndices3, o.faceIndices3), dst, "faces");
				chk(Arrays.equals(b.faceColors, o.faceColors), dst, "colours");
				chk(Arrays.equals(b.faceTextures, o.faceTextures), dst, "textures");
				chk(Arrays.equals(b.faceTransparencies, o.faceTransparencies), dst, "alpha");
				chk(Arrays.deepEquals(b.getVertexGroups(), o.getVertexGroups()), dst, "vertex skins");
				chk(Arrays.equals(b.packedTransparencyVertexGroups, o.packedTransparencyVertexGroups), dst, "face skins");
				Files.write(new File(out, dst + ".dat").toPath(), enc);
				int h = max[1] - min[1], w = max[0] - min[0], d = max[2] - min[2];
				StringBuilder line = new StringBuilder(npc + " " + src + " " + dst);
				for (int[] box : BOXES) {
					// the widest it gets at any yaw (the box's width) and its height, near half of the depth for perspective
					int across = Math.max(w, d);
					int zoom = Math.max(512 * h / box[1], 512 * across / box[0]) + Math.max(w, d) / 2;
					line.append(' ').append(zoom);
				}
				cams.append(line).append('\n');
				System.out.println(line + "  (h " + h + ", w " + w + ", d " + d + ", skins " + (g == null ? 0 : g.length) + ")");
			}
		}
		Files.writeString(new File(a[1], "cameras.txt").toPath(), cams.toString());
	}

	static void chk(boolean ok, int id, String w) {
		if (!ok)
			throw new IllegalStateException(id + ": " + w + " changed");
	}
}

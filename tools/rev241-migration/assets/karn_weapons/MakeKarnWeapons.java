import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.loaders.ModelLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Store;

import java.io.File;
import java.nio.file.Files;
import java.util.function.IntUnaryOperator;

/**
 * The Karn weapons' moving textures (user 2026-11-15), run with the rev 241 cache library (+ .dev ModelType3Encoder).
 * The meshes carry no texture coordinates, so the chosen faces take the animated texture with their own corners as the
 * mapping (texture coords -1): each face shows the flow across itself, which the texture's scroll animates.
 * <ul>
 * <li>Karn sword (Fleshrender 64619 inventory / 64620 worn): its red accent faces (colour 64421) -> texture 270;
 * writes 62821 / 62822.</li>
 * <li>Karn bow (Volcanic bow 63359 inventory / 63408 worn): its red glow faces (saturation 6-7) -> texture 271;
 * writes 62823 / 62824.</li>
 * </ul>
 * Worn models keep their vertex groups (ModelLoader drops the packed table; an encoded model without it can't follow the
 * player's animations). Usage: MakeKarnWeapons &lt;cache241&gt; &lt;assets/karn_weapons&gt;
 */
public class MakeKarnWeapons {

	public static void main(String[] a) throws Exception {
		File dir = new File(a[1], "models");
		dir.mkdirs();
		try (Store s = new Store(new File(a[0]))) {
			s.load();
			// round 2 (user: "there is no textures yet"): the whole blade flows, not only the accents. Sword: the red accent
			// and the blade body (h0 s1-3 below lightness 70, recoloured crimson-black by stage 44) -> 270; the pale
			// highlights (gold) and the hilt stay solid. Bow: the molten glow (h0 s6-7) -> 271, the black limbs (h0 s0) -> 270.
			IntUnaryOperator sword = c -> c == 64421 || ((c >> 10) == 0 && ((c >> 7) & 7) >= 1 && ((c >> 7) & 7) <= 3 && (c & 127) < 70) ? 270 : -1;
			IntUnaryOperator bow = c -> (c >> 10) != 0 ? -1 : ((c >> 7) & 7) >= 6 ? 271 : ((c >> 7) & 7) == 0 ? 270 : -1;
			make(s, 64619, 62821, sword, dir);
			make(s, 64620, 62822, sword, dir);
			make(s, 63359, 62823, bow, dir);
			make(s, 63408, 62824, bow, dir);
		}
	}

	static void make(Store s, int from, int to, IntUnaryOperator texOf, File dir) throws Exception {
		Archive ar = s.findIndex(7).getArchive(from);
		ModelDefinition m = new ModelLoader().load(from, ar.decompress(s.getStorage().loadArchive(ar)));
		int[] group = null;
		if (m.getVertexGroups() != null) {
			group = new int[m.vertexCount];
			int[][] groups = m.getVertexGroups();
			for (int i = 0; i < groups.length; i++)
				if (groups[i] != null)
					for (int v : groups[i])
						group[v] = i;
			m.packedVertexGroups = group;
		}
		short[] tex = new short[m.faceCount];
		byte[] coords = new byte[m.faceCount];
		int n = 0;
		for (int f = 0; f < m.faceCount; f++) {
			int t = texOf.applyAsInt(m.faceColors[f] & 0xffff);
			tex[f] = (short) t;
			coords[f] = -1;
			if (t >= 0)
				n++;
		}
		m.faceTextures = tex;
		m.textureCoords = coords;
		byte[] enc = ModelType3Encoder.encode(m);
		ModelDefinition back = new ModelLoader().load(to, enc);
		if (back.faceCount != m.faceCount || back.vertexCount != m.vertexCount)
			throw new IllegalStateException(to + ": encode/decode mismatch");
		if (group != null) {
			int[][] bg = back.getVertexGroups();
			int[] got = new int[back.vertexCount];
			for (int i = 0; bg != null && i < bg.length; i++)
				if (bg[i] != null)
					for (int v : bg[i])
						got[v] = i;
			for (int v = 0; v < m.vertexCount; v++)
				if (got[v] != group[v])
					throw new IllegalStateException(to + ": vertex groups lost at " + v);
		}
		int textured = 0;
		for (int f = 0; f < back.faceCount; f++)
			if (back.faceTextures != null && back.faceTextures[f] == tex[f] && tex[f] >= 0)
				textured++;
		if (textured != n)
			throw new IllegalStateException(to + ": " + textured + " textured faces after decode, expected " + n);
		Files.write(new File(dir, to + ".dat").toPath(), enc);
		System.out.println("model " + to + " (from " + from + "): " + n + " of " + m.faceCount + " faces textured (270 / 271)"
			+ (group != null ? ", vertex groups kept" : ""));
	}
}

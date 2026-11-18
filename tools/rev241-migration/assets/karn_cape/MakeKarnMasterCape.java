import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.loaders.ModelLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Store;

import java.io.File;
import java.nio.file.Files;
import java.util.Set;

/**
 * The Karn master cape (user, 2026-11-14), run with the rev 241 cache library like MakeKarnBanners. From the Attack master
 * cape's models (item 30232: inventory 63982, worn 63983, the same mesh): the attack emblem (24 faces, colours 102 / 50 /
 * 6562 / 8011 / 11095, priority 5, on the cape's back at x -7..7, y -110..-85, z 28..30) goes, and a 22 x 22 quad showing
 * texture 509 (the Karn K emblem, stage 28) takes its place, following the cloth's slope (z 28 at the top, 30 at the
 * bottom, one unit off the cloth like the old emblem).
 * <p>
 * Player model space: +z is behind the player, so the back is seen looking towards -z and +x shows on the LEFT: the
 * texture's u runs from +x to -x so the K isn't mirrored. Writes models/62818.dat (inventory) and 62819.dat (worn).
 * Usage: MakeKarnMasterCape &lt;cache241&gt; &lt;assets/karn_cape&gt;
 */
public class MakeKarnMasterCape {
	static final Set<Integer> EMBLEM = Set.of(102, 50, 6562, 8011, 11095);
	/* round 2 (user: "the logo is too small, almost can't see it"): 36 x 36 (was 22 x 22), texture 513 = the K emblem
	 * brightened (png/k_emblem_bright.png, stage 37), a 5 x 5 grid laid on the cloth's outer surface (+LIFT) so the bigger
	 * badge follows the cape's curve instead of sinking into it */
	static final int TEXTURE = 513, LEFT = -18, RIGHT = 18, TOP = -118, BOTTOM = -82, GRID = 5, LIFT = 2; // 36 x 36: the cape is only +-20 wide this high

	public static void main(String[] a) throws Exception {
		File dir = new File(a[1], "models");
		dir.mkdirs();
		try (Store s = new Store(new File(a[0]))) {
			s.load();
			ModelDefinition worn = load(s, 63983), inv = load(s, 63982);
			if (worn.vertexCount != inv.vertexCount || worn.faceCount != inv.faceCount)
				throw new IllegalStateException("inventory / worn meshes differ");
			int origCount = worn.vertexCount;
			int[] origGroup = groupOf(worn);
			ModelDefinition out = build(worn);
			// vertex skins: ModelLoader drops the packed table, and an encoded model without it can't animate (the cape
			// froze). Old vertices keep their group; the badge's take their nearest cloth vertex's, so it sways with it.
			int[] packed = new int[out.vertexCount];
			for (int v = 0; v < out.vertexCount; v++)
				packed[v] = v < origCount ? origGroup[v] : origGroup[nearest(out, origCount, v)];
			out.packedVertexGroups = packed;
			write(dir, 62819, out, origGroup, origCount);
			// inventory: the same faces on the inventory pose (63982 is the worn mesh laid flat and squashed): old vertices
			// take their inventory position, each badge vertex the same blend of its cloth triangle's inventory corners,
			// lifted towards the viewer (-y in that pose)
			for (int v = 0; v < out.vertexCount; v++) {
				if (v < origCount) {
					out.vertexX[v] = inv.vertexX[v];
					out.vertexY[v] = inv.vertexY[v];
					out.vertexZ[v] = inv.vertexZ[v];
				} else {
					double[] smp = SAMPLES.get(v - origCount);
					int f = (int) smp[0], va = inv.faceIndices1[f], vb = inv.faceIndices2[f], vc = inv.faceIndices3[f];
					out.vertexX[v] = (int) Math.round(smp[1] * inv.vertexX[va] + smp[2] * inv.vertexX[vb] + smp[3] * inv.vertexX[vc]);
					out.vertexY[v] = (int) Math.round(smp[1] * inv.vertexY[va] + smp[2] * inv.vertexY[vb] + smp[3] * inv.vertexY[vc]) - LIFT;
					out.vertexZ[v] = (int) Math.round(smp[1] * inv.vertexZ[va] + smp[2] * inv.vertexZ[vb] + smp[3] * inv.vertexZ[vc]);
				}
			}
			write(dir, 62818, out, null, origCount);
		}
	}

	static ModelDefinition load(Store s, int id) throws Exception {
		Archive ar = s.findIndex(7).getArchive(id);
		return new ModelLoader().load(id, ar.decompress(s.getStorage().loadArchive(ar)));
	}

	static int[] groupOf(ModelDefinition m) {
		int[] g = new int[m.vertexCount];
		int[][] groups = m.getVertexGroups();
		if (groups == null)
			throw new IllegalStateException("the worn cape has no vertex skins");
		for (int i = 0; i < groups.length; i++)
			if (groups[i] != null)
				for (int v : groups[i])
					g[v] = i;
		return g;
	}

	static int nearest(ModelDefinition m, int origCount, int v) {
		long best = Long.MAX_VALUE;
		int at = 0;
		for (int o = 0; o < origCount; o++) {
			long dx = m.vertexX[o] - m.vertexX[v], dy = m.vertexY[o] - m.vertexY[v], dz = m.vertexZ[o] - m.vertexZ[v], d = dx * dx + dy * dy + dz * dz;
			if (d < best) {
				best = d;
				at = o;
			}
		}
		return at;
	}

	static void write(File dir, int id, ModelDefinition out, int[] origGroup, int origCount) throws Exception {
		byte[] enc = ModelType3Encoder.encode(out);
		ModelDefinition back = new ModelLoader().load(id, enc);
		if (back.faceCount != out.faceCount || back.vertexCount != out.vertexCount || back.numTextureFaces != out.numTextureFaces)
			throw new IllegalStateException(id + ": encode/decode mismatch");
		if (origGroup != null) {
			int[] got = groupOf(back);
			for (int v = 0; v < origCount; v++)
				if (got[v] != origGroup[v])
					throw new IllegalStateException(id + ": vertex skins lost (vertex " + v + ")");
		}
		Files.write(new File(dir, id + ".dat").toPath(), enc);
		System.out.println("model " + id + ": " + out.vertexCount + " vertices, " + out.faceCount + " faces, " + out.numTextureFaces + " textured"
				+ (origGroup != null ? ", vertex skins kept" : ""));
	}

	static ModelDefinition build(ModelDefinition m) {
		int sign = 0, priority = 5, emblem = 0, faceSkin = 0;
		for (int f = 0; f < m.faceCount; f++)
			if (EMBLEM.contains(m.faceColors[f] & 0xffff)) {
				emblem++;
				if (m.faceRenderPriorities != null)
					priority = m.faceRenderPriorities[f];
				if (m.packedTransparencyVertexGroups != null)
					faceSkin = m.packedTransparencyVertexGroups[f];
				int a = m.faceIndices1[f], b = m.faceIndices2[f], c = m.faceIndices3[f];
				long cross = (long) (m.vertexX[b] - m.vertexX[a]) * (m.vertexY[c] - m.vertexY[a])
						- (long) (m.vertexY[b] - m.vertexY[a]) * (m.vertexX[c] - m.vertexX[a]);
				if (cross != 0 && sign == 0)
					sign = cross > 0 ? 1 : -1;
			}
		if (emblem != 24 || sign == 0)
			throw new IllegalStateException("expected the 24 attack emblem faces, found " + emblem);
		int side = GRID + 1, nv = m.vertexCount + side * side, keep = m.faceCount - emblem, nf = keep + GRID * GRID * 2;
		int[] vx = java.util.Arrays.copyOf(m.vertexX, nv), vy = java.util.Arrays.copyOf(m.vertexY, nv), vz = java.util.Arrays.copyOf(m.vertexZ, nv);
		int base = m.vertexCount;
		for (int j = 0; j < side; j++)
			for (int i = 0; i < side; i++) {
				int v = base + j * side + i;
				vx[v] = LEFT + (RIGHT - LEFT) * i / GRID;
				vy[v] = TOP + (BOTTOM - TOP) * j / GRID;
				vz[v] = surfaceZ(m, vx[v], vy[v]) + LIFT;
			}
		int tl = base, tr = base + GRID, br = base + GRID * side + GRID;
		int[] fa = new int[nf], fb = new int[nf], fc = new int[nf];
		short[] col = new short[nf], tex = new short[nf];
		byte[] coords = new byte[nf];
		byte[] pri = m.faceRenderPriorities != null ? new byte[nf] : null, rt = m.faceRenderTypes != null ? new byte[nf] : null;
		byte[] alpha = m.faceTransparencies != null ? new byte[nf] : null, zoff = m.faceZOffsets != null ? new byte[nf] : null;
		int[] fskin = m.packedTransparencyVertexGroups != null ? new int[nf] : null;
		int o = 0;
		for (int f = 0; f < m.faceCount; f++) {
			if (EMBLEM.contains(m.faceColors[f] & 0xffff))
				continue;
			fa[o] = m.faceIndices1[f];
			fb[o] = m.faceIndices2[f];
			fc[o] = m.faceIndices3[f];
			col[o] = m.faceColors[f];
			tex[o] = m.faceTextures != null ? m.faceTextures[f] : -1;
			coords[o] = m.textureCoords != null ? m.textureCoords[f] : -1;
			if (pri != null) pri[o] = m.faceRenderPriorities[f];
			if (rt != null) rt[o] = m.faceRenderTypes[f];
			if (alpha != null) alpha[o] = m.faceTransparencies[f];
			if (zoff != null) zoff[o] = m.faceZOffsets[f];
			if (fskin != null) fskin[o] = m.packedTransparencyVertexGroups[f];
			o++;
		}
		java.util.List<int[]> tris = new java.util.ArrayList<>();
		for (int j = 0; j < GRID; j++)
			for (int i = 0; i < GRID; i++) {
				int q = base + j * side + i, qr = q + 1, qb = q + side, qbr = qb + 1;
				tris.add(wind(vx, vy, sign, q, qr, qb));
				tris.add(wind(vx, vy, sign, qr, qbr, qb));
			}
		for (int[] t : tris) {
			fa[o] = t[0];
			fb[o] = t[1];
			fc[o] = t[2];
			col[o] = 127; // a textured face's colour is its light level
			tex[o] = (short) TEXTURE;
			coords[o] = 0;
			if (pri != null) pri[o] = (byte) priority;
			if (fskin != null) fskin[o] = faceSkin;
			o++;
		}
		m.vertexCount = nv;
		m.vertexX = vx;
		m.vertexY = vy;
		m.vertexZ = vz;
		m.faceCount = nf;
		m.faceIndices1 = fa;
		m.faceIndices2 = fb;
		m.faceIndices3 = fc;
		m.faceColors = col;
		m.faceTextures = tex;
		m.textureCoords = coords;
		m.faceRenderPriorities = pri;
		m.faceRenderTypes = rt;
		m.faceTransparencies = alpha;
		m.faceZOffsets = zoff;
		m.packedTransparencyVertexGroups = fskin;
		// texture triangle: P = top-right (u0 v0), M = top-left (u = 1), N = bottom-right (v = 1): u runs +x -> -x
		m.numTextureFaces = 1;
		m.texIndices1 = new short[] { (short) tr };
		m.texIndices2 = new short[] { (short) tl };
		m.texIndices3 = new short[] { (short) br };
		m.textureRenderTypes = new byte[] { 0 };
		return m;
	}

	/** the cape's outer (back, +z) surface under (x, y): the highest z of the faces covering it, from the original faces */
	/** per badge vertex, in order: the cloth face it sits on and its barycentric weights (for the inventory pose) */
	static final java.util.List<double[]> SAMPLES = new java.util.ArrayList<>();

	static int surfaceZ(ModelDefinition m, int x, int y) {
		double best = Double.NEGATIVE_INFINITY;
		double[] hit = null;
		for (int f = 0; f < m.faceCount; f++) {
			if (EMBLEM.contains(m.faceColors[f] & 0xffff))
				continue;
			int a = m.faceIndices1[f], b = m.faceIndices2[f], c = m.faceIndices3[f];
			double x1 = m.vertexX[a], y1 = m.vertexY[a], x2 = m.vertexX[b], y2 = m.vertexY[b], x3 = m.vertexX[c], y3 = m.vertexY[c];
			double d = (y2 - y3) * (x1 - x3) + (x3 - x2) * (y1 - y3);
			if (Math.abs(d) < 1e-9)
				continue;
			double l1 = ((y2 - y3) * (x - x3) + (x3 - x2) * (y - y3)) / d, l2 = ((y3 - y1) * (x - x3) + (x1 - x3) * (y - y3)) / d, l3 = 1 - l1 - l2;
			if (l1 < -1e-6 || l2 < -1e-6 || l3 < -1e-6)
				continue;
			double z = l1 * m.vertexZ[a] + l2 * m.vertexZ[b] + l3 * m.vertexZ[c];
			if (z > best) {
				best = z;
				hit = new double[] { f, l1, l2, l3 };
			}
		}
		if (best == Double.NEGATIVE_INFINITY) // a corner just off the cloth's edge (it narrows towards the shoulders): step in
			return x == 0 ? surfaceZ0(y) : surfaceZ(m, x - Integer.signum(x), y);
		SAMPLES.add(hit);
		return (int) Math.ceil(best);
	}

	static int surfaceZ0(int y) {
		throw new IllegalStateException("no cape surface on the centre line at y " + y);
	}

	/** the same winding as the old emblem faces, so the quad faces the same way (backwards, +z) */
	static int[] wind(int[] vx, int[] vy, int sign, int a, int b, int c) {
		long cross = (long) (vx[b] - vx[a]) * (vy[c] - vy[a]) - (long) (vy[b] - vy[a]) * (vx[c] - vx[a]);
		return (cross > 0 ? 1 : -1) == sign ? new int[] { a, b, c } : new int[] { a, c, b };
	}
}

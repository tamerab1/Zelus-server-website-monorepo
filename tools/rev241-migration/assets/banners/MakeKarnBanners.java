import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.loaders.ModelLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Store;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * The Karn banners (2026-11-11), run with the rev 241 cache library (like MakeShowcaseModels). From the standing banner
 * model 40355 (object 39153; its emblem is 158 light-grey faces, colour 86, on the cloth's front at z = -15):
 * <ul>
 * <li>62816, the K banner: the emblem faces go, a square textured quad takes their place showing texture 509 (the K
 * emblem, png/k_emblem.png, the client splash art).</li>
 * <li>62817, the KARN banner: the same banner with the cloth and crossbar stretched {@link #WIDEN}x sideways (the pole and
 * the base untouched), the full logo across it as three quads with textures 510-512 (png/logo_0..2.png, the login logo
 * split in three; each quad is a third of the logo, so the 128x128 tiles get their aspect back on the model).</li>
 * </ul>
 * Writes sprites/24812-24815.dat, textures.json (opaque 0: transparent pixels show the cloth) and models/*.dat.
 * Usage: MakeKarnBanners &lt;cache241&gt; &lt;assets/banners&gt;
 */
public class MakeKarnBanners {
	static final int SOURCE = 40355, EMBLEM = 86, K_MODEL = 62816, LOGO_MODEL = 62817;
	static final int[] SPRITES = { 24812, 24813, 24814, 24815 }, TEXTURES = { 509, 510, 511, 512 };
	static final String[] PNG = { "k_emblem", "logo_0", "logo_1", "logo_2" };
	static final double WIDEN = 2.2, LOGO_ASPECT = 2.786;
	/** the emblem's place on the cloth (from its faces): centre y, front z */
	static final int CENTRE_Y = -222, FRONT_Z = -15;

	public static void main(String[] a) throws Exception {
		File dir = new File(a[1]);
		new File(dir, "sprites").mkdirs();
		new File(dir, "models").mkdirs();
		StringBuilder json = new StringBuilder("[\n");
		for (int i = 0; i < PNG.length; i++) {
			File png = new File(dir, "png/" + PNG[i] + ".png");
			Files.write(new File(dir, "sprites/" + SPRITES[i] + ".dat").toPath(), BuildBinary241.pngSprite(png.toPath()));
			json.append(String.format("  {\"id\": %d, \"sprite\": %d, \"average\": %d, \"opaque\": 0, \"direction\": 0, \"speed\": 0}%s%n",
					TEXTURES[i], SPRITES[i], averageHsl(ImageIO.read(png)), i < PNG.length - 1 ? "," : ""));
		}
		Files.writeString(new File(dir, "textures.json").toPath(), json.append("]\n").toString());

		try (Store s = new Store(new File(a[0]))) {
			s.load();
			Archive ar = s.findIndex(7).getArchive(SOURCE);
			byte[] raw = ar.decompress(s.getStorage().loadArchive(ar));

			// the K banner: one 104 x 104 quad where the emblem was
			ModelDefinition k = new ModelLoader().load(K_MODEL, raw);
			Builder b = new Builder(k);
			b.quad(-52, CENTRE_Y - 52, 104, 104, TEXTURES[0]);
			write(dir, K_MODEL, b.finish());

			// the KARN banner: cloth and crossbar widened, the logo in three quads
			ModelDefinition w = new ModelLoader().load(LOGO_MODEL, raw);
			for (int v = 0; v < w.vertexCount; v++)
				if (w.vertexY[v] < -105 && Math.abs(w.vertexX[v]) > 10)
					w.vertexX[v] = (int) Math.round(w.vertexX[v] * WIDEN);
			b = new Builder(w);
			int width = (int) Math.round(104 * WIDEN), height = (int) Math.round(width / LOGO_ASPECT), tile = width / 3;
			for (int i = 0; i < 3; i++)
				b.quad(-width / 2 + i * tile, CENTRE_Y - height / 2, tile, height, TEXTURES[1 + i]);
			write(dir, LOGO_MODEL, b.finish());
		}
	}

	static void write(File dir, int id, ModelDefinition m) throws Exception {
		byte[] enc = ModelType3Encoder.encode(m);
		ModelDefinition back = new ModelLoader().load(id, enc);
		if (back.faceCount != m.faceCount || back.vertexCount != m.vertexCount || back.numTextureFaces != m.numTextureFaces)
			throw new IllegalStateException(id + ": encode/decode mismatch");
		Files.write(new File(dir, "models/" + id + ".dat").toPath(), enc);
		System.out.println("model " + id + ": " + m.vertexCount + " vertices, " + m.faceCount + " faces, " + m.numTextureFaces + " textured quads");
	}

	/** Drops the emblem faces and appends textured quads (each its own texture triangle), keeping every per-face array in step. */
	static final class Builder {
		final ModelDefinition m;
		final List<int[]> verts = new ArrayList<>();
		final List<int[]> faces = new ArrayList<>(); // a, b, c, texture, texCoord
		final List<int[]> texTris = new ArrayList<>();
		int priority, renderType, sign;

		Builder(ModelDefinition m) {
			this.m = m;
			for (int f = 0; f < m.faceCount; f++)
				if ((m.faceColors[f] & 0xffff) == EMBLEM) {
					priority = m.faceRenderPriorities != null ? m.faceRenderPriorities[f] : 0;
					renderType = m.faceRenderTypes != null ? m.faceRenderTypes[f] : 0;
					int a = m.faceIndices1[f], b = m.faceIndices2[f], c = m.faceIndices3[f];
					long cross = (long) (m.vertexX[b] - m.vertexX[a]) * (m.vertexY[c] - m.vertexY[a])
							- (long) (m.vertexY[b] - m.vertexY[a]) * (m.vertexX[c] - m.vertexX[a]);
					if (cross != 0 && sign == 0)
						sign = cross > 0 ? 1 : -1;
				}
		}

		/** A quad on the cloth's front: x, y = its top-left corner, w x h, showing {@code texture} upright. */
		void quad(int x, int y, int w, int h, int texture) {
			int base = m.vertexCount + verts.size();
			verts.add(new int[]{x, y, FRONT_Z});          // top-left
			verts.add(new int[]{x + w, y, FRONT_Z});      // top-right
			verts.add(new int[]{x, y + h, FRONT_Z});      // bottom-left
			verts.add(new int[]{x + w, y + h, FRONT_Z});  // bottom-right
			int tl = base, tr = base + 1, bl = base + 2, br = base + 3, t = texTris.size();
			texTris.add(new int[]{tl, tr, bl}); // P = u0 v0, M = u1, N = v1
			// the same winding as the emblem faces had, so the quad faces the same way
			int[] f1 = winding(tl, tr, bl), f2 = winding(tr, br, bl);
			faces.add(new int[]{f1[0], f1[1], f1[2], texture, t});
			faces.add(new int[]{f2[0], f2[1], f2[2], texture, t});
		}

		int[] winding(int a, int b, int c) {
			int[] pa = vert(a), pb = vert(b), pc = vert(c);
			long cross = (long) (pb[0] - pa[0]) * (pc[1] - pa[1]) - (long) (pb[1] - pa[1]) * (pc[0] - pa[0]);
			return (cross > 0 ? 1 : -1) == sign || sign == 0 ? new int[]{a, b, c} : new int[]{a, c, b};
		}

		int[] vert(int i) {
			return i < m.vertexCount ? new int[]{m.vertexX[i], m.vertexY[i], m.vertexZ[i]} : verts.get(i - m.vertexCount);
		}

		ModelDefinition finish() {
			int keep = 0;
			for (int f = 0; f < m.faceCount; f++)
				if ((m.faceColors[f] & 0xffff) != EMBLEM)
					keep++;
			int nf = keep + faces.size(), nv = m.vertexCount + verts.size();
			int[] vx = grow(m.vertexX, nv), vy = grow(m.vertexY, nv), vz = grow(m.vertexZ, nv);
			for (int i = 0; i < verts.size(); i++) {
				vx[m.vertexCount + i] = verts.get(i)[0];
				vy[m.vertexCount + i] = verts.get(i)[1];
				vz[m.vertexCount + i] = verts.get(i)[2];
			}
			int[] fa = new int[nf], fb = new int[nf], fc = new int[nf];
			short[] col = new short[nf], tex = new short[nf];
			byte[] pri = m.faceRenderPriorities != null ? new byte[nf] : null, rt = m.faceRenderTypes != null ? new byte[nf] : null;
			byte[] alpha = m.faceTransparencies != null ? new byte[nf] : null, zoff = m.faceZOffsets != null ? new byte[nf] : null;
			byte[] coords = new byte[nf];
			int o = 0;
			for (int f = 0; f < m.faceCount; f++) {
				if ((m.faceColors[f] & 0xffff) == EMBLEM)
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
				o++;
			}
			for (int[] f : faces) {
				fa[o] = f[0];
				fb[o] = f[1];
				fc[o] = f[2];
				col[o] = 127; // a textured face's colour is its light level
				tex[o] = (short) f[3];
				coords[o] = (byte) f[4];
				if (pri != null) pri[o] = (byte) priority;
				if (rt != null) rt[o] = (byte) renderType;
				o++;
			}
			int nt = texTris.size();
			short[] t1 = new short[nt], t2 = new short[nt], t3 = new short[nt];
			byte[] types = new byte[nt];
			for (int i = 0; i < nt; i++) {
				t1[i] = (short) texTris.get(i)[0];
				t2[i] = (short) texTris.get(i)[1];
				t3[i] = (short) texTris.get(i)[2];
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
			m.numTextureFaces = nt;
			m.texIndices1 = t1;
			m.texIndices2 = t2;
			m.texIndices3 = t3;
			m.textureRenderTypes = types;
			return m;
		}

		static int[] grow(int[] a, int n) {
			int[] b = new int[n];
			System.arraycopy(a, 0, b, 0, a.length);
			return b;
		}
	}

	/** The average opaque colour as the engine's packed HSL (6 bits hue, 3 saturation, 7 lightness). */
	static int averageHsl(BufferedImage im) {
		long r = 0, g = 0, b = 0, n = 0;
		for (int y = 0; y < im.getHeight(); y++)
			for (int x = 0; x < im.getWidth(); x++) {
				int p = im.getRGB(x, y);
				if ((p >>> 24) < 128) continue;
				r += (p >> 16) & 0xff;
				g += (p >> 8) & 0xff;
				b += p & 0xff;
				n++;
			}
		if (n == 0) return 0;
		double rd = r / (double) n / 255, gd = g / (double) n / 255, bd = b / (double) n / 255;
		double max = Math.max(rd, Math.max(gd, bd)), min = Math.min(rd, Math.min(gd, bd)), l = (max + min) / 2, h = 0, s = 0;
		if (max != min) {
			double d = max - min;
			s = l > 0.5 ? d / (2 - max - min) : d / (max + min);
			h = max == rd ? (gd - bd) / d + (gd < bd ? 6 : 0) : max == gd ? (bd - rd) / d + 2 : (rd - gd) / d + 4;
			h /= 6;
		}
		return ((int) (h * 63) & 63) << 10 | ((int) (s * 7) & 7) << 7 | ((int) (l * 127) & 127);
	}
}

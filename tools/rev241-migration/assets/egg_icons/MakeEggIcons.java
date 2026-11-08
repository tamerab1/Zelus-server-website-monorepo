import com.google.gson.*;
import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.loaders.ModelLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Store;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.util.List;

/**
 * Egg-slot icons (2026-11-07), run with the rev 241 cache library: 12 designs x 5 tiers. The 7 model-bank-pack icons
 * keep their look as t1 of their design (Beast's old t2 look is now its own design, Shade); t2-t5 are uniform scales
 * (1.15 / 1.3 / 1.45 / 1.6) of the same model. 5 new designs are built here from geometry + textures drawn here
 * (skulls, spectral flame, frost sigil, void eye, sun). Writes into the given asset folder: png/ (the textures, for
 * viewing), sprites/, models/, textures.json, items.json, item_info_rows.json. PatchEggIcons writes them to the cache.
 * Usage: MakeEggIcons &lt;cache241&gt; &lt;assets/egg_icons&gt;
 */
public class MakeEggIcons {
	static final int MODEL_BASE = 63409, ITEM_BASE = 40200, TEX_BASE = 497, SPRITE_BASE = 24767;
	static final double[] SCALE = { 1.0, 1.15, 1.3, 1.45, 1.6 };
	static final int[] INV_ZOOM = { 1400, 1270, 1150, 1040, 940 };
	static final short LIGHT = 127; // textured faces: full-light colour, as the pack's icons

	/** the drawn images, one sprite each (SPRITE_BASE + index) */
	static final String[] PNG = { "skull", "ghostskull", "soulflame", "frostsigil", "voideye", "voidcircle", "sun" };

	/**
	 * a texture: its image and scroll (GPU TextureManager: direction 1 = v-, 3 = v+ (up a wall whose image top is its
	 * top), 2 = u-, 4 = u+; speed n = n/128 of the image per client cycle). A ring of identical tiles scrolled along u
	 * looks like the ring turning.
	 */
	record Tex(String name, String png, int direction, int speed) {}

	static final Tex[] TEX = { new Tex("skull", "skull", 0, 0), new Tex("ghostskull", "ghostskull", 0, 0), new Tex("soulflame", "soulflame", 3, 3),
			new Tex("frostsigil", "frostsigil", 0, 0), new Tex("voideye", "voideye", 0, 0), new Tex("voidcircle", "voidcircle", 0, 0),
			new Tex("sun", "sun", 0, 0), new Tex("skull_ring", "skull", 2, 1), new Tex("ghostskull_ring", "ghostskull", 4, 1),
			new Tex("sun_ring", "sun", 2, 1) };

	static int tex(String n) {
		for (int i = 0; i < TEX.length; i++)
			if (TEX[i].name.equals(n))
				return TEX_BASE + i;
		throw new IllegalArgumentException(n);
	}

	record Old(String name, int worn, int inv, int resize, int zoom, int xan, int yan, String examine) {}

	static final Old[] OLD = {
			new Old("Beast", 63271, 63272, 110, 1114, 553, 0, "A smouldering beast aura."),
			new Old("Shade", 63269, 63270, 110, 1114, 553, 0, "Shadow smoke and violet shards."),
			new Old("Easter", 63276, 63277, 128, 1114, 553, 0, "A cheerful spring aura."),
			new Old("Amberglow", 63252, 63253, 128, 1114, 553, 0, "It glows like warm amber."),
			new Old("Infernoglint", 63250, 63251, 128, 1114, 553, 0, "Embers dance around your feet."),
			new Old("Challenge", 63244, 63245, 128, 1049, 526, 1010, "Proof you rose to the challenge."),
			new Old("Midnight spring", 63242, 63243, 128, 1114, 553, 0, "Petals drift through the midnight air."), };
	/** existing item ids that become t1 (and Beast t2) of their design */
	static final Map<String, int[]> EXISTING = Map.of("Beast", new int[] { 40080, 40081 }, "Easter", new int[] { 40087 },
			"Amberglow", new int[] { 40090 }, "Infernoglint", new int[] { 40091 }, "Challenge", new int[] { 40093 },
			"Midnight spring", new int[] { 40094 });

	record New(String name, String invTex, String examine) {}

	static final New[] NEW = {
			new New("Bonecrown", "skull", "A crown of bone and grinning skulls."),
			new New("Soulflame", "ghostskull", "Green soulfire that never burns out."),
			new New("Frost sigil", "frostsigil", "A frozen sigil hums beneath you."),
			new New("Void eye", "voideye", "Something in the void is watching."),
			new New("Sunburst", "sun", "The sun itself bows to you."), };

	static File out;
	static Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	public static void main(String[] a) throws Exception {
		out = new File(a[1]);
		for (String d : new String[] { "png", "sprites", "models" })
			new File(out, d).mkdirs();

		// ---- textures
		JsonArray texJson = new JsonArray();
		Map<String, Integer> average = new HashMap<>();
		for (int i = 0; i < PNG.length; i++) {
			BufferedImage im = draw(PNG[i]);
			File png = new File(out, "png/" + PNG[i] + ".png");
			javax.imageio.ImageIO.write(im, "png", png);
			byte[] sprite = BuildBinary241.pngSprite(png.toPath());
			if (!BuildBinary241.validTextureSprite(SPRITE_BASE + i, sprite))
				throw new IllegalStateException("bad sprite " + PNG[i]);
			Files.write(new File(out, "sprites/" + (SPRITE_BASE + i) + ".dat").toPath(), sprite);
			average.put(PNG[i], averageHsl(im));
		}
		for (int i = 0; i < TEX.length; i++) {
			JsonObject t = new JsonObject();
			t.addProperty("id", TEX_BASE + i);
			t.addProperty("name", TEX[i].name);
			t.addProperty("sprite", SPRITE_BASE + Arrays.asList(PNG).indexOf(TEX[i].png));
			t.addProperty("average", average.get(TEX[i].png));
			// the rev 241 flag is "opaque": every vanilla texture with cut-out (0) pixels has 0, every one with 1 has none.
			// 1 here (2026-11-07 first cut) made the software renderer paint the cut-outs black (inventory icons)
			t.addProperty("transparent", 0);
			t.addProperty("direction", TEX[i].direction);
			t.addProperty("speed", TEX[i].speed);
			texJson.add(t);
		}
		Files.writeString(new File(out, "textures.json").toPath(), gson.toJson(texJson) + "\n");

		// ---- models + items
		int nextModel = MODEL_BASE, nextItem = ITEM_BASE;
		JsonArray items = new JsonArray(), rows = new JsonArray();
		try (Store store = new Store(new File(a[0]))) {
			store.load();
			for (Old o : OLD) {
				ModelDefinition base = load(store, o.worn);
				int[] have = EXISTING.getOrDefault(o.name, new int[0]);
				for (int t = 0; t < 5; t++) {
					int worn;
					if (t == 0)
						worn = o.worn;
					else {
						worn = nextModel++;
						write(worn, scaled(base, SCALE[t]), base, SCALE[t]);
					}
					int id = t < have.length ? have[t] : nextItem++;
					items.add(item(id, o.name + " icon (t" + (t + 1) + ")", o.inv, worn, o.resize, zoomFor(o.zoom, t), o.xan, o.yan));
					rows.add(row(id, o.name + " icon (t" + (t + 1) + ")", o.examine));
				}
			}
		}
		for (New n : NEW) {
			int inv = nextModel++;
			Mesh im = new Mesh();
			im.floorQuad(0, 0, 60, 0, tex(n.invTex), 0);
			write(inv, im.build(1.0), null, 1);
			Mesh m = design(n.name);
			for (int t = 0; t < 5; t++) {
				int worn = nextModel++;
				write(worn, m.build(SCALE[t]), null, 1);
				int id = nextItem++;
				items.add(item(id, n.name + " icon (t" + (t + 1) + ")", inv, worn, 128, zoomFor(1114, t), 553, 0));
				rows.add(row(id, n.name + " icon (t" + (t + 1) + ")", n.examine));
			}
		}
		nextModel = writeMotion(nextModel);
		if (nextModel > 63602)
			throw new IllegalStateException("models past the free run (63409-63602)");
		Files.writeString(new File(out, "items.json").toPath(), gson.toJson(items) + "\n");
		Files.writeString(new File(out, "item_info_rows.json").toPath(), gson.toJson(rows) + "\n");
		System.out.println("egg icons: " + items.size() + " items (new " + ITEM_BASE + "-" + (nextItem - 1) + "), models " + MODEL_BASE + "-"
				+ (nextModel - 1) + ", textures " + TEX_BASE + "-" + (TEX_BASE + TEX.length - 1) + ", sprites " + SPRITE_BASE + "-"
				+ (SPRITE_BASE + PNG.length - 1) + ", spotanims " + SPOT_BASE + "-" + (SPOT_BASE + NEW.length * 5 - 1));
	}

	static int zoomFor(int base, int t) {
		return base * INV_ZOOM[t] / 1114;
	}

	static JsonObject item(int id, String name, int inv, int worn, int resize, int zoom, int xan, int yan) {
		JsonObject d = new JsonObject();
		d.addProperty("id", id);
		d.addProperty("name", name);
		d.addProperty("inventoryModel", inv);
		d.addProperty("maleModel0", worn);
		d.addProperty("femaleModel0", worn);
		d.addProperty("zoom2d", zoom);
		d.addProperty("xan2d", xan);
		d.addProperty("yan2d", yan);
		d.addProperty("resizeX", resize);
		d.addProperty("resizeY", resize);
		d.addProperty("resizeZ", resize);
		d.addProperty("wearPos1", 9); // as the pack's icons; the server equips them in the egg slot (item_info equip_slot 15)
		d.addProperty("members", true);
		d.addProperty("cost", 1);
		JsonObject ground = new JsonObject();
		JsonArray gops = new JsonArray();
		gops.add(JsonNull.INSTANCE);
		gops.add(JsonNull.INSTANCE);
		JsonObject take = new JsonObject();
		take.addProperty("text", "Take");
		gops.add(take);
		ground.add("ops", gops);
		d.add("groundOps", ground);
		JsonArray ops = new JsonArray();
		ops.add(JsonNull.INSTANCE);
		ops.add("Wear");
		ops.add(JsonNull.INSTANCE);
		ops.add(JsonNull.INSTANCE);
		ops.add("Drop");
		d.add("interfaceOptions", ops);
		return d;
	}

	static JsonObject row(int id, String name, String examine) {
		JsonObject r = new JsonObject();
		r.addProperty("name", name);
		r.addProperty("id", id);
		r.addProperty("tradeable", true);
		r.addProperty("examine", examine);
		r.addProperty("weight", 0.1);
		r.addProperty("equip_slot", 15);
		return r;
	}

	// ------------------------------------------------------------------ models

	static ModelDefinition load(Store s, int id) throws Exception {
		Archive ar = s.findIndex(7).getArchive(id);
		return new ModelLoader().load(id, ar.decompress(s.getStorage().loadArchive(ar)));
	}

	static ModelDefinition scaled(ModelDefinition b, double s) {
		ModelDefinition m = copy(b);
		for (int v = 0; v < m.vertexCount; v++) {
			m.vertexX[v] = (int) Math.round(b.vertexX[v] * s);
			m.vertexY[v] = (int) Math.round(b.vertexY[v] * s);
			m.vertexZ[v] = (int) Math.round(b.vertexZ[v] * s);
		}
		return m;
	}

	static ModelDefinition copy(ModelDefinition b) {
		ModelDefinition m = new ModelDefinition();
		m.vertexCount = b.vertexCount;
		m.vertexX = b.vertexX.clone();
		m.vertexY = b.vertexY.clone();
		m.vertexZ = b.vertexZ.clone();
		m.faceCount = b.faceCount;
		m.faceIndices1 = b.faceIndices1.clone();
		m.faceIndices2 = b.faceIndices2.clone();
		m.faceIndices3 = b.faceIndices3.clone();
		m.faceColors = b.faceColors.clone();
		m.faceTransparencies = b.faceTransparencies == null ? null : b.faceTransparencies.clone();
		m.faceRenderTypes = b.faceRenderTypes == null ? null : b.faceRenderTypes.clone();
		m.faceRenderPriorities = b.faceRenderPriorities == null ? null : b.faceRenderPriorities.clone();
		m.faceTextures = b.faceTextures == null ? null : b.faceTextures.clone();
		m.textureCoords = b.textureCoords == null ? null : b.textureCoords.clone();
		m.numTextureFaces = b.numTextureFaces;
		m.texIndices1 = b.texIndices1 == null ? null : b.texIndices1.clone();
		m.texIndices2 = b.texIndices2 == null ? null : b.texIndices2.clone();
		m.texIndices3 = b.texIndices3 == null ? null : b.texIndices3.clone();
		m.textureRenderTypes = b.textureRenderTypes == null ? null : b.textureRenderTypes.clone();
		m.priority = b.priority;
				// vertex skins would be a rigged model; the face alpha group (4 on every face of these icons) is kept as is
		if (used(b.packedVertexGroups) || b.animayaGroups != null)
			throw new IllegalStateException("icon model has vertex skins");
		m.packedTransparencyVertexGroups = b.packedTransparencyVertexGroups == null ? null : b.packedTransparencyVertexGroups.clone();
		return m;
	}

	static boolean used(int[] g) {
		if (g != null)
			for (int x : g)
				if (x > 0)
					return true;
		return false;
	}

	/** encodes, re-decodes and checks geometry / faces / textures / alpha survived */
	static void write(int id, ModelDefinition m, ModelDefinition base, double s) throws Exception {
		byte[] enc = ModelType3Encoder.encode(m);
		ModelDefinition b = new ModelLoader().load(id, enc);
		chk(Arrays.equals(b.vertexX, m.vertexX) && Arrays.equals(b.vertexY, m.vertexY) && Arrays.equals(b.vertexZ, m.vertexZ), id, "vertices");
		chk(Arrays.equals(b.faceIndices1, m.faceIndices1) && Arrays.equals(b.faceIndices2, m.faceIndices2)
				&& Arrays.equals(b.faceIndices3, m.faceIndices3), id, "faces");
		chk(Arrays.equals(b.faceColors, m.faceColors), id, "colours");
		chk(Arrays.equals(b.faceTextures, m.faceTextures), id, "textures");
		chk(Arrays.equals(b.faceTransparencies, m.faceTransparencies), id, "alpha");
		chk(b.numTextureFaces == m.numTextureFaces, id, "texture triangles");
		chk(Arrays.equals(b.packedTransparencyVertexGroups, m.packedTransparencyVertexGroups), id, "face alpha groups");
		if (m.packedVertexGroups != null) { // the decoder hands labels back as per-label vertex lists
			int[][] g = b.getVertexGroups();
			int max = Arrays.stream(m.packedVertexGroups).max().getAsInt();
			chk(g != null && g.length == max + 1, id, "vertex label count");
			for (int k = 0; k <= max; k++) {
				final int label = k;
				int[] want = java.util.stream.IntStream.range(0, m.vertexCount).filter(v -> m.packedVertexGroups[v] == label).toArray();
				chk(Arrays.equals(g[k], want), id, "vertex label " + k);
			}
		}
		if (m.numTextureFaces > 0) {
			for (int i = 0; i < m.faceCount; i++) // stored for textured faces only
				chk(m.faceTextures[i] == -1 || b.textureCoords[i] == m.textureCoords[i], id, "texture coords");
			chk(Arrays.equals(b.texIndices1, m.texIndices1) && Arrays.equals(b.texIndices2, m.texIndices2)
					&& Arrays.equals(b.texIndices3, m.texIndices3), id, "texture triangle vertices");
		}
		if (base != null)
			chk(Arrays.equals(b.faceTextures, base.faceTextures) && b.faceCount == base.faceCount, id, "differs from its t1 beyond scale");
		Files.write(new File(out, "models/" + id + ".dat").toPath(), enc);
	}

	static void chk(boolean ok, int id, String w) {
		if (!ok)
			throw new IllegalStateException("model " + id + ": " + w);
	}

	/** a model under construction; every face gets its own vertices (no shared normals across sides) */
	static final class Mesh {
		final List<double[]> v = new ArrayList<>(); // x, y, z, fixedY(0/1)
		final List<int[]> f = new ArrayList<>(); // a, b, c, colour, alpha, texture, texTri
		final List<int[]> tt = new ArrayList<>(); // P, M, N

		/** the vertex label (animation group) new vertices get; 0 = not animated */
		int label;

		int vert(double x, double y, double z, boolean fixedY) {
			v.add(new double[] { x, y, z, fixedY ? 1 : 0, label });
			return v.size() - 1;
		}

		/** a textured quad, both sides; corners in texture order: top-left, top-right, bottom-right, bottom-left */
		void quad(double[] tl, double[] tr, double[] br, double[] bl, int texture, int alpha, boolean fixedY) {
			// front: texture (0,0) at tl, u to tr, v to bl
			int a = vert(tl[0], tl[1], tl[2], fixedY), b = vert(tr[0], tr[1], tr[2], fixedY), c = vert(br[0], br[1], br[2], fixedY),
					d = vert(bl[0], bl[1], bl[2], fixedY);
			tt.add(new int[] { a, b, d });
			int t = tt.size() - 1;
			f.add(new int[] { a, b, c, LIGHT, alpha, texture, t });
			f.add(new int[] { a, c, d, LIGHT, alpha, texture, t });
			// back: its own vertices, mirrored mapping so it reads the right way round from behind
			a = vert(tr[0], tr[1], tr[2], fixedY);
			b = vert(tl[0], tl[1], tl[2], fixedY);
			c = vert(bl[0], bl[1], bl[2], fixedY);
			d = vert(br[0], br[1], br[2], fixedY);
			tt.add(new int[] { a, b, d });
			t = tt.size() - 1;
			f.add(new int[] { a, b, c, LIGHT, alpha, texture, t });
			f.add(new int[] { a, c, d, LIGHT, alpha, texture, t });
		}

		/** a plain-coloured triangle, both sides */
		void tri(double[] p, double[] q, double[] r, int hsl, int alpha, boolean fixedY) {
			int a = vert(p[0], p[1], p[2], fixedY), b = vert(q[0], q[1], q[2], fixedY), c = vert(r[0], r[1], r[2], fixedY);
			f.add(new int[] { a, b, c, hsl, alpha, -1, -1 });
			a = vert(p[0], p[1], p[2], fixedY);
			b = vert(r[0], r[1], r[2], fixedY);
			c = vert(q[0], q[1], q[2], fixedY);
			f.add(new int[] { a, b, c, hsl, alpha, -1, -1 });
		}

		/** a flat textured square on the ground, centred (cx, cz), half-size h, at height y */
		void floorQuad(double cx, double cz, double h, double y, int texture, int alpha) {
			quad(new double[] { cx - h, y, cz + h }, new double[] { cx + h, y, cz + h }, new double[] { cx + h, y, cz - h },
					new double[] { cx - h, y, cz - h }, texture, alpha, false);
		}

		/** a flat ring of n textured segments (one texture tile each, top of the image pointing outwards) */
		void ring(double rIn, double rOut, double y, int n, double phase, int texture, int alpha, boolean fixedY) {
			for (int i = 0; i < n; i++) {
				double a0 = phase + 2 * Math.PI * i / n, a1 = phase + 2 * Math.PI * (i + 1) / n;
				quad(p(rOut, a0, y), p(rOut, a1, y), p(rIn, a1, y), p(rIn, a0, y), texture, alpha, fixedY);
			}
		}

		/** a plain-coloured flat ring */
		void plainRing(double rIn, double rOut, double y, int n, int hsl, int alpha, boolean fixedY) {
			for (int i = 0; i < n; i++) {
				double a0 = 2 * Math.PI * i / n, a1 = 2 * Math.PI * (i + 1) / n;
				tri(p(rOut, a0, y), p(rOut, a1, y), p(rIn, a1, y), hsl, alpha, fixedY);
				tri(p(rOut, a0, y), p(rIn, a1, y), p(rIn, a0, y), hsl, alpha, fixedY);
			}
		}

		/** a cylinder wall of n textured segments from y0 (bottom) to y1 (top) */
		void wall(double r, double y0, double y1, int n, int texture, int alpha) {
			for (int i = 0; i < n; i++) {
				double a0 = 2 * Math.PI * i / n, a1 = 2 * Math.PI * (i + 1) / n;
				quad(p(r, a0, y1), p(r, a1, y1), p(r, a1, y0), p(r, a0, y0), texture, alpha, false);
			}
		}

		/** an upright textured square facing outwards at angle a, distance r, centre height y, half-size h */
		void billboard(double r, double a, double y, double h, int texture, int alpha) {
			double cx = r * Math.cos(a), cz = r * Math.sin(a), tx = -Math.sin(a), tz = Math.cos(a);
			quad(new double[] { cx - tx * h, y - h, cz - tz * h }, new double[] { cx + tx * h, y - h, cz + tz * h },
					new double[] { cx + tx * h, y + h, cz + tz * h }, new double[] { cx - tx * h, y + h, cz - tz * h }, texture, alpha, false);
		}

		ModelDefinition build(double s) {
			ModelDefinition m = new ModelDefinition();
			m.vertexCount = v.size();
			m.vertexX = new int[v.size()];
			m.vertexY = new int[v.size()];
			m.vertexZ = new int[v.size()];
			for (int i = 0; i < v.size(); i++) {
				double[] q = v.get(i);
				m.vertexX[i] = (int) Math.round(q[0] * s);
				m.vertexY[i] = (int) Math.round(q[3] == 1 ? q[1] : q[1] * s);
				m.vertexZ[i] = (int) Math.round(q[2] * s);
			}
			m.faceCount = f.size();
			m.faceIndices1 = new int[f.size()];
			m.faceIndices2 = new int[f.size()];
			m.faceIndices3 = new int[f.size()];
			m.faceColors = new short[f.size()];
			m.faceTransparencies = new byte[f.size()];
			m.faceTextures = new short[f.size()];
			m.textureCoords = new byte[f.size()];
			for (int i = 0; i < f.size(); i++) {
				int[] q = f.get(i);
				m.faceIndices1[i] = q[0];
				m.faceIndices2[i] = q[1];
				m.faceIndices3[i] = q[2];
				m.faceColors[i] = (short) q[3];
				m.faceTransparencies[i] = (byte) q[4];
				m.faceTextures[i] = (short) q[5];
				m.textureCoords[i] = (byte) q[6];
			}
			if (tt.size() > 255)
				throw new IllegalStateException("more than 255 texture triangles");
			m.numTextureFaces = tt.size();
			m.texIndices1 = new short[tt.size()];
			m.texIndices2 = new short[tt.size()];
			m.texIndices3 = new short[tt.size()];
			m.textureRenderTypes = new byte[tt.size()];
			for (int i = 0; i < tt.size(); i++) {
				m.texIndices1[i] = (short) tt.get(i)[0];
				m.texIndices2[i] = (short) tt.get(i)[1];
				m.texIndices3[i] = (short) tt.get(i)[2];
			}
			if (tt.isEmpty()) {
				m.textureCoords = null;
				m.texIndices1 = m.texIndices2 = m.texIndices3 = null;
				m.textureRenderTypes = null;
			}
			if (v.stream().anyMatch(q -> q[4] != 0)) {
				m.packedVertexGroups = new int[v.size()];
				for (int i = 0; i < v.size(); i++)
					m.packedVertexGroups[i] = (int) v.get(i)[4];
			}
			m.priority = 1;
			return m;
		}
	}

	static double[] p(double r, double a, double y) {
		return new double[] { r * Math.cos(a), y, r * Math.sin(a) };
	}

	static int hsl(int h, int s, int l) {
		return h << 10 | s << 7 | l;
	}

	static Mesh design(String name) {
		Mesh m = new Mesh();
		switch (name) {
			case "Bonecrown" -> {
				m.ring(34, 62, -4, 12, 0, tex("skull_ring"), 0, false); // turns (texture scroll)
				int bone = hsl(6, 2, 96), boneDark = hsl(6, 3, 70);
				for (int i = 0; i < 8; i++) { // bone spikes leaning in, two crossed blades each
					double a = 2 * Math.PI * (i + 0.5) / 8, w = 0.09;
					m.tri(p(58, a - w, -4), p(58, a + w, -4), p(48, a, -66), bone, 0, false);
					m.tri(p(52, a, -4), p(64, a, -4), p(48, a, -66), boneDark, 0, false);
				}
			}
			case "Soulflame" -> {
				m.wall(48, -2, -80, 12, tex("soulflame"), 40);
				m.ring(28, 60, -3, 10, 0, tex("ghostskull_ring"), 30, false); // turns the other way
			}
			case "Frost sigil" -> {
				int ice = hsl(36, 4, 108), iceDark = hsl(37, 5, 80);
				for (int i = 0; i < 6; i++) { // crystal shards: a 4-sided bipyramid leaning outwards
					double a = 2 * Math.PI * i / 6, r = 66, cx = r * Math.cos(a), cz = r * Math.sin(a), ox = 9 * Math.cos(a), oz = 9 * Math.sin(a);
					double[] bottom = { cx, -2, cz }, top = { cx + ox, -50, cz + oz };
					double[][] mid = new double[4][];
					for (int k = 0; k < 4; k++) {
						double b = a + Math.PI / 4 + k * Math.PI / 2;
						mid[k] = new double[] { cx + ox * 0.3 + 8 * Math.cos(b), -18, cz + oz * 0.3 + 8 * Math.sin(b) };
					}
					for (int k = 0; k < 4; k++) {
						double[] m0 = mid[k], m1 = mid[(k + 1) % 4];
						m.tri(top, m0, m1, k % 2 == 0 ? ice : iceDark, 50, false);
						m.tri(bottom, m1, m0, iceDark, 50, false);
					}
				}
			}
			case "Void eye" -> {
				m.plainRing(17, 25, -222, 16, hsl(47, 7, 74), 60, true); // halo, stays over the head at every tier
			}
			case "Sunburst" -> {
				m.ring(34, 58, -4, 10, 0, tex("sun_ring"), 0, false); // turns (texture scroll)
			}
			default -> throw new IllegalArgumentException(name);
		}
		return m;
	}

	// ------------------------------------------------------------------ motion (looping spot-anims)

	/** one loop = {@link #LOOP_TICKS} game ticks; the server replays it that often, a tick of overrun hides the gap */
	static final int LOOP_TICKS = 8, LOOP_CYCLES = LOOP_TICKS * 30, FRAME_CYCLES = 2, FRAMES = (LOOP_CYCLES + 30) / FRAME_CYCLES;
	static final int SPOT_BASE = 6841, SEQ_BASE = 20900, FRAME_BASE = 19002;

	/**
	 * a framemap (transform types: 0 origin, 1 translate, 2 rotate; each over vertex labels) and each transform's value
	 * at loop phase f (0..1, running on a little past 1): {x, y, z} or null for unused. Every motion repeats each loop
	 * (rotations by a multiple of the model's own symmetry), so the replay joins without a jump.
	 */
	record Anim(int[] types, int[][] labels, java.util.function.BiFunction<Double, Integer, int[]> value) {}

	/** rotation, in the client's 256-per-turn units, for degrees per loop */
	static int rot(double f, double degreesPerLoop) {
		return (int) Math.round(f * degreesPerLoop / 360 * 256);
	}

	static int bob(double f, double amp, double phase) {
		return (int) Math.round(-amp * Math.sin(2 * Math.PI * f + phase));
	}

	record Spot(Mesh mesh, Anim anim) {}

	static Spot spot(String name) {
		Mesh m = new Mesh();
		Anim a;
		switch (name) {
			case "Bonecrown" -> { // four skulls orbit (a quarter turn a loop) bobbing together
				m.label = 1;
				for (int i = 0; i < 4; i++)
					m.billboard(68, 2 * Math.PI * i / 4 + Math.PI / 4, -42, 15, tex("skull"), 0);
				a = new Anim(new int[] { 0, 1, 2 }, new int[][] { { 1 }, { 1 }, { 1 } },
						(f, t) -> t == 1 ? new int[] { 0, bob(f, 7, 0), 0 } : t == 2 ? new int[] { 0, rot(f, 90), 0 } : null);
			}
			case "Soulflame" -> { // three ghost skulls circle the other way, rising and falling in a wave
				for (int i = 0; i < 3; i++) {
					m.label = i + 1;
					m.billboard(58, 2 * Math.PI * i / 3, -50, 13, tex("ghostskull"), 0);
				}
				a = new Anim(new int[] { 0, 1, 1, 1, 2 }, new int[][] { { 1, 2, 3 }, { 1 }, { 2 }, { 3 }, { 1, 2, 3 } },
						(f, t) -> t >= 1 && t <= 3 ? new int[] { 0, bob(f, 16, 2 * Math.PI * (t - 1) / 3), 0 }
								: t == 4 ? new int[] { 0, rot(f, -120), 0 } : null);
			}
			case "Frost sigil" -> { // the sigil turns under the player (a sixth of a turn a loop)
				m.label = 1;
				m.floorQuad(0, 0, 72, -3, tex("frostsigil"), 20);
				a = new Anim(new int[] { 0, 2 }, new int[][] { { 1 }, { 1 } }, (f, t) -> t == 1 ? new int[] { 0, rot(f, 60), 0 } : null);
			}
			case "Void eye" -> { // the eyes orbit at chest height, the star circle turns against them
				m.label = 1;
				for (int i = 0; i < 3; i++)
					m.billboard(64, Math.PI / 3 + 2 * Math.PI * i / 3, -110, 17, tex("voideye"), 0);
				m.label = 2;
				m.floorQuad(0, 0, 70, -3, tex("voidcircle"), 20);
				a = new Anim(new int[] { 0, 1, 2, 0, 2 }, new int[][] { { 1 }, { 1 }, { 1 }, { 2 }, { 2 } },
						(f, t) -> t == 1 ? new int[] { 0, bob(f, 5, 0), 0 } : t == 2 ? new int[] { 0, rot(f, 120), 0 }
								: t == 4 ? new int[] { 0, rot(f, -90), 0 } : null);
			}
			case "Sunburst" -> { // the sun crown spins over the head, the floor rays turn against it
				m.label = 1;
				m.floorQuad(0, 0, 20, -224, tex("sun"), 0);
				m.label = 2;
				int gold = hsl(8, 7, 88), orange = hsl(5, 7, 70);
				for (int i = 0; i < 16; i++) {
					double r = 2 * Math.PI * i / 16, w = 0.12, tip = i % 2 == 0 ? 100 : 82;
					m.tri(p(58, r - w, -3), p(58, r + w, -3), p(tip, r, -3), i % 2 == 0 ? gold : orange, 30, false);
				}
				a = new Anim(new int[] { 0, 2, 0, 2 }, new int[][] { { 1 }, { 1 }, { 2 }, { 2 } },
						(f, t) -> t == 1 ? new int[] { 0, rot(f, 180), 0 } : t == 3 ? new int[] { 0, rot(f, -45), 0 } : null);
			}
			default -> throw new IllegalArgumentException(name);
		}
		return new Spot(m, a);
	}

	static byte[] framemap(Anim a) {
		java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
		o.write(a.types.length);
		for (int t : a.types)
			o.write(t);
		for (int[] l : a.labels)
			o.write(l.length);
		for (int[] l : a.labels)
			for (int x : l)
				o.write(x);
		return o.toByteArray();
	}

	/** u16 framemap, u8 count, a flag per transform, then the used x / y / z as short smarts */
	static byte[] frame(int framemap, Anim a, double f) {
		java.io.ByteArrayOutputStream head = new java.io.ByteArrayOutputStream(), data = new java.io.ByteArrayOutputStream();
		head.write(framemap >> 8);
		head.write(framemap & 0xff);
		head.write(a.types.length);
		for (int t = 0; t < a.types.length; t++) {
			int[] v = a.value.apply(f, t);
			int flag = 0;
			if (v != null)
				for (int k = 0; k < 3; k++)
					if (v[k] != 0) {
						flag |= 1 << k;
						smart(data, v[k]);
					}
			head.write(flag);
		}
		head.writeBytes(data.toByteArray());
		return head.toByteArray();
	}

	static void smart(java.io.ByteArrayOutputStream o, int v) {
		if (v >= -64 && v < 64)
			o.write(v + 64);
		else {
			if (v < -16384 || v >= 16384)
				throw new IllegalArgumentException("smart " + v);
			int w = v + 0xc000;
			o.write(w >> 8);
			o.write(w & 0xff);
		}
	}

	static byte[] sequence(int group) {
		java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
		o.write(1);
		u16(o, FRAMES);
		for (int i = 0; i < FRAMES; i++)
			u16(o, FRAME_CYCLES);
		for (int i = 0; i < FRAMES; i++)
			u16(o, i);
		for (int i = 0; i < FRAMES; i++)
			u16(o, group);
		o.write(0);
		return o.toByteArray();
	}

	/** rev 241 spot-anim: 3 model (int), 2 sequence, 4 width (128 = as is); never scaled up, so the crown stays at head height */
	static byte[] spotanim(int model, int seq, int width) {
		java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
		o.write(3);
		o.write(model >>> 24);
		o.write(model >> 16 & 0xff);
		o.write(model >> 8 & 0xff);
		o.write(model & 0xff);
		o.write(2);
		u16(o, seq);
		if (width != 128) {
			o.write(4);
			u16(o, width);
		}
		o.write(0);
		return o.toByteArray();
	}

	static void u16(java.io.ByteArrayOutputStream o, int v) {
		o.write(v >> 8 & 0xff);
		o.write(v & 0xff);
	}

	/** builds every design's spot-anim model, framemap, frames, sequence and its 5 tier spot-anims; checked by the rev 241 loaders */
	static int writeMotion(int nextModel) throws Exception {
		for (String d : new String[] { "frames", "framemaps", "seqs", "spotanims" })
			new File(out, d).mkdirs();
		for (int n = 0; n < NEW.length; n++) {
			Spot s = spot(NEW[n].name);
			int model = nextModel++, group = FRAME_BASE + n, seq = SEQ_BASE + n;
			write(model, s.mesh.build(1.0), null, 1);
			byte[] fm = framemap(s.anim);
			var fmd = new net.runelite.cache.definitions.loaders.FramemapLoader().load(group, fm);
			chk(Arrays.equals(fmd.types, s.anim.types) && Arrays.deepEquals(fmd.frameMaps, s.anim.labels), model, "framemap");
			Files.write(new File(out, "framemaps/" + group + ".dat").toPath(), fm);
			File fd = new File(out, "frames/" + group);
			fd.mkdirs();
			for (int i = 0; i < FRAMES; i++) {
				byte[] fr = frame(group, s.anim, (double) i * FRAME_CYCLES / LOOP_CYCLES);
				var def = new net.runelite.cache.definitions.loaders.FrameLoader().load(fmd, i, fr);
				chk(def.framemap == fmd, model, "frame " + i);
				Files.write(new File(fd, i + ".dat").toPath(), fr);
			}
			byte[] sq = sequence(group);
			var sd = new net.runelite.cache.definitions.loaders.SequenceLoader().configureForRevision(241).load(seq, sq);
			chk(sd.frameIDs.length == FRAMES && sd.frameIDs[FRAMES - 1] == (group << 16 | FRAMES - 1), model, "sequence");
			Files.write(new File(out, "seqs/" + seq + ".dat").toPath(), sq);
			for (int t = 0; t < 5; t++) {
				int id = SPOT_BASE + n * 5 + t;
				byte[] sa = spotanim(model, seq, (int) Math.round(128 * SCALE[t]));
				var spd = new net.runelite.cache.definitions.loaders.SpotAnimLoader().load(id, sa);
				chk(spd.animationId == seq, model, "spot-anim " + id);
				Files.write(new File(out, "spotanims/" + id + ".dat").toPath(), sa);
			}
		}
		return nextModel;
	}

	// ------------------------------------------------------------------ textures (128x128, transparent background)

	static BufferedImage draw(String n) {
		BufferedImage im = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = im.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
		switch (n) {
			case "skull" -> skull(g, new Color(0xEDE3C8), new Color(0xB9A988), new Color(0x2A0A0A), new Color(0xFF2A1A), new Color(0x4A3A28));
			case "ghostskull" -> {
				wisps(g, new Color(0x2FCB5A), new Color(0x9CFFB0));
				skull(g, new Color(0xB8FFC8), new Color(0x5FD884), new Color(0x053A16), new Color(0xE8FF60), new Color(0x0E5A26));
			}
			case "soulflame" -> flames(g);
			case "frostsigil" -> frostSigil(g);
			case "voideye" -> eye(g);
			case "voidcircle" -> voidCircle(g);
			case "sun" -> sun(g);
			default -> throw new IllegalArgumentException(n);
		}
		g.dispose();
		return quantize(im);
	}

	static void skull(Graphics2D g, Color bone, Color shade, Color socket, Color glow, Color line) {
		Area head = new Area(new Ellipse2D.Double(22, 10, 84, 78));
		head.add(new Area(new RoundRectangle2D.Double(38, 66, 52, 44, 18, 18)));
		g.setColor(line);
		g.fill(head.createTransformedArea(AffineTransform.getScaleInstance(1, 1)));
		Area inner = new Area(new Ellipse2D.Double(25, 13, 78, 72));
		inner.add(new Area(new RoundRectangle2D.Double(41, 68, 46, 39, 16, 16)));
		g.setColor(shade);
		g.fill(inner);
		g.setColor(bone);
		g.fill(new Ellipse2D.Double(28, 14, 70, 60));
		g.fill(new RoundRectangle2D.Double(44, 70, 40, 22, 12, 12));
		// eye sockets + glow
		g.setColor(socket);
		g.fill(new Ellipse2D.Double(34, 42, 25, 23));
		g.fill(new Ellipse2D.Double(69, 42, 25, 23));
		g.setColor(glow);
		g.fill(new Ellipse2D.Double(42, 49, 9, 9));
		g.fill(new Ellipse2D.Double(77, 49, 9, 9));
		// nose
		g.setColor(socket);
		g.fill(new Polygon(new int[] { 64, 57, 71 }, new int[] { 66, 79, 79 }, 3));
		// teeth
		g.setColor(line);
		g.fillRect(44, 92, 40, 3);
		for (int x = 50; x <= 78; x += 7)
			g.fillRect(x, 86, 2, 20);
		// crack
		g.setStroke(new BasicStroke(2));
		g.drawPolyline(new int[] { 70, 76, 72, 80 }, new int[] { 15, 24, 30, 38 }, 4);
	}

	static void wisps(Graphics2D g, Color c, Color light) {
		g.setStroke(new BasicStroke(3));
		for (int i = 0; i < 6; i++) {
			double x = 12 + i * 21;
			Path2D p = new Path2D.Double();
			p.moveTo(x, 124);
			p.curveTo(x - 10, 100, x + 10, 80, x - 2, 60);
			g.setColor(i % 2 == 0 ? c : light);
			g.draw(p);
		}
	}

	/** tongues of green fire, tiled vertically (the texture scrolls) */
	static void flames(Graphics2D g) {
		Color[] shades = { new Color(0x0B5A20), new Color(0x1E9B3C), new Color(0x4CE06A), new Color(0xB4FFB8), new Color(0xF0FFE8) };
		Random r = new Random(7);
		for (int pass = 0; pass < 2; pass++)
			for (int col = 0; col < 8; col++) {
				double cx = col * 16 + 8 + (pass == 1 ? 8 : 0);
				for (int rep = 0; rep < 2; rep++) {
					double base = (rep * 64 + pass * 32 + r.nextInt(12)) % 128 + 56;
					double h = 44 + r.nextInt(18);
					for (int s = 0; s < shades.length; s++) {
						double w = 9 - s * 1.6, hh = h * (1 - s * 0.16);
						for (int dy = -128; dy <= 0; dy += 128) {
							Path2D p = new Path2D.Double();
							double b = base + dy;
							p.moveTo(cx - w, b);
							p.curveTo(cx - w, b - hh * 0.5, cx + w * 0.6, b - hh * 0.7, cx + (s % 2 == 0 ? 2 : -2), b - hh);
							p.curveTo(cx + w * 0.2, b - hh * 0.6, cx + w, b - hh * 0.4, cx + w, b);
							p.closePath();
							g.setColor(shades[s]);
							g.fill(p);
							g.fill(p.createTransformedShape(AffineTransform.getTranslateInstance(128, 0)));
							g.fill(p.createTransformedShape(AffineTransform.getTranslateInstance(-128, 0)));
							g.fill(p.createTransformedShape(AffineTransform.getTranslateInstance(0, 128)));
						}
					}
				}
			}
	}

	static void frostSigil(Graphics2D g) {
		Color glow = new Color(0x3FA8E8), line = new Color(0xBFF4FF), white = new Color(0xF4FFFF);
		g.setStroke(new BasicStroke(4));
		g.setColor(glow);
		g.draw(new Ellipse2D.Double(5, 5, 118, 118));
		g.setStroke(new BasicStroke(2));
		g.setColor(line);
		g.draw(new Ellipse2D.Double(6, 6, 116, 116));
		g.draw(new Ellipse2D.Double(18, 18, 92, 92));
		// runes between the rings
		for (int i = 0; i < 12; i++) {
			double a = i * Math.PI / 6;
			int x = (int) (64 + 52 * Math.cos(a)), y = (int) (64 + 52 * Math.sin(a));
			g.setColor(i % 2 == 0 ? white : line);
			g.drawLine(x - 3, y - 3, x + 3, y + 3);
			g.drawLine(x - 3, y + 3, x + 3, y - 3);
			g.drawLine(x, y - 4, x, y + 4);
		}
		// hexagram
		g.setColor(line);
		for (int t = 0; t < 2; t++) {
			Polygon p = new Polygon();
			for (int k = 0; k < 3; k++) {
				double a = -Math.PI / 2 + t * Math.PI + k * 2 * Math.PI / 3;
				p.addPoint((int) (64 + 45 * Math.cos(a)), (int) (64 + 45 * Math.sin(a)));
			}
			g.draw(p);
		}
		// snowflake
		g.setColor(white);
		g.setStroke(new BasicStroke(3));
		for (int k = 0; k < 6; k++) {
			double a = k * Math.PI / 3;
			int x = (int) (64 + 20 * Math.cos(a)), y = (int) (64 + 20 * Math.sin(a));
			g.drawLine(64, 64, x, y);
			double b1 = a + 0.6, b2 = a - 0.6;
			int mx = (int) (64 + 12 * Math.cos(a)), my = (int) (64 + 12 * Math.sin(a));
			g.drawLine(mx, my, (int) (mx + 7 * Math.cos(b1)), (int) (my + 7 * Math.sin(b1)));
			g.drawLine(mx, my, (int) (mx + 7 * Math.cos(b2)), (int) (my + 7 * Math.sin(b2)));
		}
	}

	static void eye(Graphics2D g) {
		Path2D lid = new Path2D.Double();
		lid.moveTo(6, 64);
		lid.quadTo(64, 8, 122, 64);
		lid.quadTo(64, 120, 6, 64);
		lid.closePath();
		g.setColor(new Color(0xFF4FD8));
		g.setStroke(new BasicStroke(6));
		g.draw(lid);
		g.setColor(new Color(0xE9D8FF));
		g.fill(lid);
		g.setColor(new Color(0x4B0F8A));
		g.fill(new Ellipse2D.Double(38, 38, 52, 52));
		g.setColor(new Color(0x9B3BF0));
		g.fill(new Ellipse2D.Double(43, 43, 42, 42));
		g.setColor(new Color(0xD08CFF));
		g.setStroke(new BasicStroke(2));
		g.draw(new Ellipse2D.Double(50, 50, 28, 28));
		g.setColor(new Color(0x0A0012));
		g.fill(new Ellipse2D.Double(58, 42, 12, 44));
		g.setColor(Color.WHITE);
		g.fill(new Ellipse2D.Double(72, 48, 7, 7));
	}

	static void voidCircle(Graphics2D g) {
		Color deep = new Color(0x5A1E9C), mid = new Color(0xA45CFF), light = new Color(0xE2C2FF);
		g.setStroke(new BasicStroke(5));
		g.setColor(deep);
		g.draw(new Ellipse2D.Double(5, 5, 118, 118));
		g.setStroke(new BasicStroke(2));
		g.setColor(mid);
		g.draw(new Ellipse2D.Double(7, 7, 114, 114));
		g.draw(new Ellipse2D.Double(24, 24, 80, 80));
		// eight-point star
		Polygon star = new Polygon();
		for (int k = 0; k < 16; k++) {
			double a = -Math.PI / 2 + k * Math.PI / 8, r = k % 2 == 0 ? 52 : 22;
			star.addPoint((int) (64 + r * Math.cos(a)), (int) (64 + r * Math.sin(a)));
		}
		g.setColor(light);
		g.draw(star);
		// four small eyes on the ring
		for (int k = 0; k < 4; k++) {
			double a = Math.PI / 4 + k * Math.PI / 2;
			int x = (int) (64 + 50 * Math.cos(a)), y = (int) (64 + 50 * Math.sin(a));
			g.setColor(light);
			g.fill(new Ellipse2D.Double(x - 7, y - 4, 14, 8));
			g.setColor(deep);
			g.fill(new Ellipse2D.Double(x - 2, y - 3, 4, 6));
		}
		g.setColor(mid);
		g.fill(new Ellipse2D.Double(54, 54, 20, 20));
		g.setColor(new Color(0x12001F));
		g.fill(new Ellipse2D.Double(61, 55, 6, 18));
	}

	static void sun(Graphics2D g) {
		for (int k = 0; k < 16; k++) {
			double a = k * Math.PI / 8, r = k % 2 == 0 ? 62 : 48, w = 0.17;
			Polygon ray = new Polygon();
			ray.addPoint((int) (64 + 30 * Math.cos(a - w)), (int) (64 + 30 * Math.sin(a - w)));
			ray.addPoint((int) (64 + r * Math.cos(a)), (int) (64 + r * Math.sin(a)));
			ray.addPoint((int) (64 + 30 * Math.cos(a + w)), (int) (64 + 30 * Math.sin(a + w)));
			g.setColor(k % 2 == 0 ? new Color(0xFFB000) : new Color(0xFF7A00));
			g.fill(ray);
		}
		g.setColor(new Color(0xC86400));
		g.fill(new Ellipse2D.Double(28, 28, 72, 72));
		g.setColor(new Color(0xFFD24A));
		g.fill(new Ellipse2D.Double(32, 32, 64, 64));
		g.setColor(new Color(0xFFF2A8));
		g.fill(new Ellipse2D.Double(42, 40, 34, 30));
		g.setColor(new Color(0xE08A00));
		g.setStroke(new BasicStroke(2));
		g.draw(new Ellipse2D.Double(38, 38, 52, 52));
	}

	/** alpha to on/off and colours to 4 bits per channel (a cache sprite holds <= 255 colours) */
	static BufferedImage quantize(BufferedImage im) {
		BufferedImage o = new BufferedImage(im.getWidth(), im.getHeight(), BufferedImage.TYPE_INT_ARGB);
		Set<Integer> seen = new HashSet<>();
		for (int y = 0; y < im.getHeight(); y++)
			for (int x = 0; x < im.getWidth(); x++) {
				int p = im.getRGB(x, y);
				if ((p >>> 24) < 128)
					continue;
				int r = (p >> 16 & 0xff) & 0xf0 | 8, gg = (p >> 8 & 0xff) & 0xf0 | 8, b = (p & 0xff) & 0xf0 | 8;
				int c = 0xff000000 | r << 16 | gg << 8 | b;
				seen.add(c);
				o.setRGB(x, y, c);
			}
		if (seen.size() > 255)
			throw new IllegalStateException(seen.size() + " colours");
		return o;
	}

	static int averageHsl(BufferedImage im) {
		long r = 0, g = 0, b = 0, n = 0;
		for (int y = 0; y < im.getHeight(); y++)
			for (int x = 0; x < im.getWidth(); x++) {
				int p = im.getRGB(x, y);
				if ((p >>> 24) == 0)
					continue;
				r += p >> 16 & 0xff;
				g += p >> 8 & 0xff;
				b += p & 0xff;
				n++;
			}
		float[] hsb = Color.RGBtoHSB((int) (r / n), (int) (g / n), (int) (b / n), null);
		// OSRS 16-bit colour: hue 6 bits, saturation 3 bits, lightness 7 bits
		int h = (int) (hsb[0] * 63), s = (int) (hsb[1] * 7), l = (int) (hsb[2] * 127);
		return h << 10 | s << 7 | l;
	}
}

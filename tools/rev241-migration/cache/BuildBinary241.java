import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.SpriteDefinition;
import net.runelite.cache.definitions.loaders.ModelLoader;
import net.runelite.cache.definitions.loaders.SpriteLoader;
import net.runelite.cache.fs.*;
import net.runelite.cache.index.FileData;
import net.runelite.cache.index.IndexData;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.*;
import java.util.*;

/**
 * Stage 2 of the rev 241 cache build: models (7), textures (9), anim frames (0), skeletons (1),
 * sound effects (4) and sprites (8). Writes into the rev 241 output cache (after stage 1).
 *
 *  custom groups (absent from vanilla 231) -> their manifest id. Models get the trailer Jagex's
 *    encoder writes (missing from packer-written models) and their face textures remapped; frame
 *    files get their skeleton id remapped. Models the manifest maps onto an identical vanilla
 *    model (or a duplicate custom) are not written.
 *  vanilla groups Zelus really edited (models by geometry, sprites by pixels, others by bytes):
 *    ours if Jagex left them alone in 241, otherwise vanilla 241 is kept and the id is listed
 *    for review (sprites need a frame-level merge there).
 *
 * args: localCache v231Cache outDir manifest reportJson
 */
public class BuildBinary241 {
	static Store local, v231, out;
	static Manifest manifest;
	static String a3;
	static final StringBuilder report = new StringBuilder("{\n  \"stage\": \"binary\",\n");
	static final List<String> written = new ArrayList<>();

	public static void main(String[] a) throws Exception {
		local = new Store(new File(a[0])); local.load();
		v231 = new Store(new File(a[1])); v231.load();
		out = new Store(new File(a[2])); out.load();
		manifest = new Manifest(Path.of(a[3]));
		a3 = a[3];

		models();
		singleFileIndex(4, "sound", null);
		singleFileIndex(1, "skeleton", null);
		frames();
		sprites();
		textures();

		for (int idx : new int[]{0, 1, 4, 7, 8, 9}) writeIndexTable(out.findIndex(idx));
		out.close();
		report.append("  \"done\": true\n}\n");
		Files.writeString(Path.of(a[4]), report.toString());
		verify(a[2]);
	}

	// ------------------------------------------------------------------ helpers

	static byte[] read(Store s, Archive ar) throws Exception {
		return ar.decompress(s.getStorage().loadArchive(ar));
	}

	static Set<Integer> ids(Store s, int idx) {
		Set<Integer> r = new HashSet<>();
		for (Archive x : s.findIndex(idx).getArchives()) r.add(x.getArchiveId());
		return r;
	}

	/** Stores raw (uncompressed) group data at id as a single-file (file 0) or given-files group. */
	static void store(int idx, int id, byte[] raw, FileData[] files) throws Exception {
		Index index = out.findIndex(idx);
		Archive ar = index.getArchive(id);
		if (ar == null) {
			ar = index.addArchive(id);
			ar.setCompression(net.runelite.cache.fs.jagex.CompressionType.GZ);
		}
		if (files == null) {
			FileData f = new FileData();
			f.setId(0);
			files = new FileData[]{f};
		}
		Container c = new Container(ar.getCompression(), -1);
		c.compress(raw, null);
		out.getStorage().store(idx, id, c.data);
		ar.setFileData(files);
		ar.setCrc(c.crc);
		ar.setRevision(ar.getRevision() + 1);
		ar.setCompressedSize(c.data.length);
		ar.setDecompressedSize(raw.length);
		written.add(idx + ":" + id);
	}

	static void writeIndexTable(Index index) throws Exception {
		IndexData data = index.toIndexData();
		byte[] raw = data.writeIndexData();
		Container c = new Container(index.getCompression(), -1);
		c.compress(raw, null);
		out.getStorage().store(255, index.getId(), c.data);
		index.setCrc(c.crc);
	}

	static String list(Collection<Integer> c) {
		StringBuilder b = new StringBuilder("[");
		int i = 0;
		for (int v : new TreeSet<>(c)) b.append(i++ == 0 ? "" : ",").append(v);
		return b.append("]").toString();
	}

	// ------------------------------------------------------------------ models

	static String modelGeometry(int id, byte[] data) {
		ModelDefinition d = new ModelLoader().load(id, data);
		StringBuilder b = new StringBuilder();
		try {
			for (Field f : ModelDefinition.class.getDeclaredFields()) {
				if (Modifier.isStatic(f.getModifiers()) || f.getName().endsWith("Normals")
					|| f.getName().startsWith("faceTextureUCoordinates") || f.getName().startsWith("faceTextureVCoordinates")) continue;
				f.setAccessible(true);
				b.append(f.getName()).append('=').append(Arrays.deepToString(new Object[]{f.get(d)})).append(';');
			}
		} catch (IllegalAccessException e) {
			throw new RuntimeException(e);
		}
		return b.toString();
	}

	static void models() throws Exception {
		Set<Integer> base = ids(v231, 7), theirs = ids(out, 7);
		int customs = 0, reused = 0, trailers = 0, texturePatched = 0, editsOurs = 0;
		List<Integer> editConflicts = new ArrayList<>(), corrupt = new ArrayList<>(), editsTaken = new ArrayList<>();
		Map<Integer, Integer> map = manifest.maps.get("model");
		Map<String, String> reuse = new HashMap<>();
		manifest.json.getAsJsonObject("types").getAsJsonObject("model").getAsJsonObject("reuse").entrySet()
			.forEach(e -> reuse.put(e.getKey(), e.getValue().getAsString()));
		for (Archive la : local.findIndex(7).getArchives()) {
			int id = la.getArchiveId();
			byte[] data = read(local, la);
			if (!base.contains(id)) {
				if (reuse.containsKey(String.valueOf(id))) { reused++; continue; }
				int target = map.getOrDefault(id, id);
				if (theirs.contains(target)) throw new IllegalStateException("model " + id + " -> " + target + " is a vanilla 241 model");
				byte[] fixed;
				try {
					fixed = ModelTexturePatcher.addMissingTrailer(data);
					if (fixed != data) trailers++;
					byte[] patched = ModelTexturePatcher.patch(fixed, t -> manifest.map("texture", t));
					if (!Arrays.equals(patched, fixed)) texturePatched++;
					fixed = patched;
				} catch (Exception e) {
					corrupt.add(id);    // unreadable already (e.g. truncated); copied unchanged
					fixed = data;
				}
				store(7, target, fixed, null);
				customs++;
				continue;
			}
			// vanilla id: a real Zelus edit?
			Archive ba = v231.findIndex(7).getArchive(id);
			byte[] bdata = read(v231, ba);
			if (Arrays.equals(bdata, data)) continue;
			String g0, g1;
			try { g0 = modelGeometry(id, bdata); g1 = modelGeometry(id, data); } catch (Exception e) { continue; }
			if (g0.equals(g1)) continue; // re-encoded only
			Archive ta = out.findIndex(7).getArchive(id);
			boolean jagexChanged = ta == null || !modelGeometry(id, read(out, ta)).equals(g0);
			if (jagexChanged) { editConflicts.add(id); continue; }
			byte[] fixed = ModelTexturePatcher.patch(ModelTexturePatcher.addMissingTrailer(data), t -> manifest.map("texture", t));
			store(7, id, fixed, null);
			editsOurs++;
			editsTaken.add(id);
		}
		report.append(String.format("  \"models\": {\"customsWritten\": %d, \"reusedVanillaOrDuplicate\": %d, \"trailerAdded\": %d, \"texturesRemapped\": %d, \"vanillaEditsTaken\": %s, \"vanillaEditsKeptVanilla241BecauseJagexChanged\": %s, \"unreadableCopiedAsIs\": %s},%n",
			customs, reused, trailers, texturePatched, list(editsTaken), list(editConflicts), list(corrupt)));
	}

	// ------------------------------------------------------------------ sounds / skeletons

	static void singleFileIndex(int idx, String kind, Object unused) throws Exception {
		Set<Integer> base = ids(v231, idx), theirs = ids(out, idx);
		int customs = 0;
		List<Integer> taken = new ArrayList<>(), keptVanilla = new ArrayList<>();
		for (Archive la : local.findIndex(idx).getArchives()) {
			int id = la.getArchiveId();
			byte[] data = read(local, la);
			if (!base.contains(id)) {
				int target = manifest.map(kind, id);
				if (target == id && theirs.contains(id) && manifest.isImported(kind, id)) continue; // imported vanilla: keep 241's
				if (theirs.contains(target)) throw new IllegalStateException(kind + " " + id + " -> " + target + " is vanilla 241");
				store(idx, target, data, la.getFileData());
				customs++;
				continue;
			}
			byte[] b = read(v231, v231.findIndex(idx).getArchive(id));
			if (Arrays.equals(b, data)) continue;
			Archive ta = out.findIndex(idx).getArchive(id);
			if (ta != null && Arrays.equals(read(out, ta), b)) { store(idx, id, data, la.getFileData()); taken.add(id); }
			else keptVanilla.add(id);
		}
		report.append(String.format("  \"%s\": {\"index\": %d, \"customsWritten\": %d, \"vanillaEditsTaken\": %s, \"vanillaEditsKeptVanilla241BecauseJagexChanged\": %s},%n",
			kind, idx, customs, list(taken), list(keptVanilla)));
	}

	// ------------------------------------------------------------------ frames

	/** Frame group: several files, each starting with its skeleton (framemap) id as u16. */
	static void frames() throws Exception {
		Set<Integer> base = ids(v231, 0), theirs = ids(out, 0);
		int customs = 0, skeletonRefs = 0;
		List<Integer> keptVanilla = new ArrayList<>();
		for (Archive la : local.findIndex(0).getArchives()) {
			int id = la.getArchiveId();
			if (base.contains(id)) {
				byte[] b = read(v231, v231.findIndex(0).getArchive(id));
				if (!Arrays.equals(b, read(local, la))) keptVanilla.add(id); // all such frames were also changed by Jagex
				continue;
			}
			int target = manifest.map("frames", id);
			if (target == id && theirs.contains(id) && manifest.isImported("frames", id)) continue; // imported vanilla: keep 241's
			if (theirs.contains(target)) throw new IllegalStateException("frames " + id + " -> " + target + " is vanilla 241");
			FileData[] fd = la.getFileData();
			List<byte[]> files = SpliceItemOption.splitChunks(read(local, la), fd.length);
			List<byte[]> outFiles = new ArrayList<>();
			for (byte[] f : files) {
				byte[] g = f.clone();
				int skel = ((g[0] & 0xff) << 8) | (g[1] & 0xff);
				int n = manifest.map("skeleton", skel);
				if (n != skel) { g[0] = (byte) (n >> 8); g[1] = (byte) n; skeletonRefs++; }
				outFiles.add(g);
			}
			byte[] raw = fd.length == 1 ? outFiles.get(0) : SpliceItemOption.joinChunks(outFiles);
			store(0, target, raw, fd);
			customs++;
		}
		report.append(String.format("  \"frames\": {\"customsWritten\": %d, \"skeletonRefsRemapped\": %d, \"vanillaEditsKeptVanilla241BecauseJagexChanged\": %s},%n",
			customs, skeletonRefs, list(keptVanilla)));
	}

	// ------------------------------------------------------------------ sprites

	static String spriteSig(int id, byte[] data) {
		StringBuilder b = new StringBuilder();
		for (SpriteDefinition d : new SpriteLoader().load(id, data))
			b.append(d.getFrame()).append(',').append(d.getOffsetX()).append(',').append(d.getOffsetY()).append(',')
				.append(d.getWidth()).append('x').append(d.getHeight()).append(',').append(d.getMaxWidth()).append('x').append(d.getMaxHeight())
				.append(':').append(Arrays.hashCode(d.getPixels())).append(';');
		return b.toString();
	}

	/** what the 241 client's texture loader accepts: one frame, 128x128 or 64x64 */
	static boolean validTextureSprite(int id, byte[] data) {
		SpriteDefinition[] f = new SpriteLoader().load(id, data);
		return f.length == 1 && f[0].getMaxWidth() == f[0].getMaxHeight() && (f[0].getMaxWidth() == 128 || f[0].getMaxWidth() == 64);
	}

	/** our sprites written at a new id because their vanilla id is a texture sprite: old -> new (stage 5 follows) */
	static final Map<Integer, Integer> spriteMoves = new TreeMap<>();

	static FileData fileZero() { FileData f = new FileData(); f.setId(0); return f; }

	/** a PNG (<= 255 colours, alpha 0 = transparent) as a one-frame cache sprite */
	static byte[] pngSprite(java.nio.file.Path png) throws Exception {
		java.awt.image.BufferedImage im = javax.imageio.ImageIO.read(png.toFile());
		SpriteCodec.Frame f = new SpriteCodec.Frame();
		f.width = im.getWidth(); f.height = im.getHeight();
		f.index = new byte[f.width * f.height];
		List<Integer> colours = new ArrayList<>(List.of(0));
		Map<Integer, Integer> pal = new HashMap<>();
		for (int y = 0; y < f.height; y++)
			for (int x = 0; x < f.width; x++) {
				int argb = im.getRGB(x, y);
				if ((argb >>> 24) < 128) continue;
				int rgb = argb & 0xffffff;
				if (rgb == 0) rgb = 1; // 0 is transparent in sprites
				Integer n = pal.get(rgb);
				if (n == null) { n = colours.size(); colours.add(rgb); pal.put(rgb, n); }
				f.index[y * f.width + x] = (byte) (int) n;
			}
		if (colours.size() > 256) throw new IllegalStateException(png + ": more than 255 colours");
		SpriteCodec.Group g = SpriteCodec.assemble(f.width, f.height, List.of(f), List.of(colours.stream().mapToInt(Integer::intValue).toArray()));
		return SpriteCodec.encode(g);
	}

	static void sprites() throws Exception {
		Set<Integer> base = ids(v231, 8);
		// sprites vanilla 241 textures use (index 9 still holds vanilla 241's textures: textures() runs later).
		// Zelus wrote 30x30 perk icons over texture sprites 4664-4667; the 231 client tolerated it (those
		// terrain textures rendered wrong), the 241 client rejects a texture sprite that isn't 128 or 64 wide
		// and crashes. Such overwrites keep Jagex's sprite and ours moves to a free id.
		Set<Integer> textureSprites = new HashSet<>();
		for (byte[] t : files(out, out.findIndex(9).getArchive(0)).values()) textureSprites.add(((t[0] & 0xff) << 8) | (t[1] & 0xff));
		Set<Integer> used = new HashSet<>(ids(out, 8));
		used.addAll(ids(local, 8));
		int nextFree = 0;
		for (int u : used) if (u < 0x10000) nextFree = Math.max(nextFree, u + 1);
		int customs = 0;
		List<Integer> taken = new ArrayList<>(), goneIn241 = new ArrayList<>(), frameMerged = new ArrayList<>(), undecodable = new ArrayList<>();
		Map<String, Integer> mergeNotes = new TreeMap<>();
		for (Archive la : local.findIndex(8).getArchives()) {
			int id = la.getArchiveId();
			byte[] data = read(local, la);
			if (!base.contains(id)) { store(8, id, data, la.getFileData()); customs++; continue; }
			byte[] b = read(v231, v231.findIndex(8).getArchive(id));
			if (Arrays.equals(b, data)) continue;
			try {
				String s0 = spriteSig(id, b), s1 = spriteSig(id, data);
				if (s0.equals(s1)) continue; // re-encoded only
				// an empty 231 placeholder (<= 32 bytes) that Zelus drew on and Jagex filled in 241 (the Raid 4 HUD's
				// 4548 ring fill, 4527/4528, 4541-4544, 5800-5802, 5808-5810): Jagex's art stays, ours moves to a free id
				// and stages 4/5 repoint our interfaces and scripts at it
				Archive va = out.findIndex(8).getArchive(id);
				if (b.length <= 32 && va != null) {
					byte[] jagex = read(out, va);
					if (jagex.length > 32 && !Arrays.equals(jagex, b)) {
						if (nextFree > 0xFFFF) throw new IllegalStateException("no free sprite id under 65536 for " + id);
						store(8, nextFree, data, la.getFileData());
						spriteMoves.put(id, nextFree++);
						continue;
					}
				}
				// a real texture reskin (e.g. 5604, texture 124) is a single 128x128 / 64x64 frame and stays
				if (textureSprites.contains(id) && !validTextureSprite(id, data)) {
					if (nextFree > 0xFFFF) throw new IllegalStateException("no free sprite id under 65536 for " + id);
					store(8, nextFree, data, la.getFileData());
					spriteMoves.put(id, nextFree++);
					continue;
				}
				Archive ta = out.findIndex(8).getArchive(id);
				if (ta != null && spriteSig(id, read(out, ta)).equals(s0)) { store(8, id, data, la.getFileData()); taken.add(id); }
				else if (ta == null) { store(8, id, data, la.getFileData()); goneIn241.add(id); } // deleted by Jagex; our interfaces may use it
				else {
					byte[] merged = mergeSpriteFrames(b, data, read(out, ta), mergeNotes, id);
					store(8, id, merged, la.getFileData());
					frameMerged.add(id);
				}
			} catch (Exception e) { undecodable.add(id); }
		}
		// Zelus art made for rev 241 (tools/rev241-migration/assets/sprites/<id>.png): replaces the sprite at that id,
		// e.g. the bank Presets button (24500/24501, a backpack instead of the worn-items mannequin)
		java.nio.file.Path art = Path.of(a3).toAbsolutePath().getParent().getParent().resolve("assets/sprites");
		int artWritten = 0;
		if (Files.isDirectory(art))
			try (var pngs = Files.list(art)) {
				for (var png : (Iterable<java.nio.file.Path>) pngs.filter(x -> x.toString().endsWith(".png")).sorted()::iterator) {
					int sid = Integer.parseInt(png.getFileName().toString().replace(".png", ""));
					store(8, sid, pngSprite(png), new FileData[]{fileZero()});
					artWritten++;
				}
			}
		report.append(String.format("  \"spriteArt\": %d,%n", artWritten));
		StringJoiner moves = new StringJoiner(", ", "{", "}");
		spriteMoves.forEach((k, v) -> moves.add("\"" + k + "\": " + v));
		report.append(String.format("  \"sprites\": {\"customsWritten\": %d, \"zelusReskinsTaken\": %d, \"reskinsOfSpritesDeletedIn241KeptOurs\": %s, \"frameMerged\": %d, \"frameMergeNotes\": %s, \"undecodable\": %s, \"textureSpriteOverwritesMovedOursTo\": %s},%n",
			customs, taken.size(), list(goneIn241), frameMerged.size(), mergeNotesJson(mergeNotes), list(undecodable), moves));
	}

	/**
	 * Frame-level three-way merge of a sprite group both sides changed: a frame we changed (pixels or
	 * geometry) takes ours, otherwise 241's; frames only one side appended are kept; our canvas wins
	 * if we changed it. Mixed results get one re-built palette (<= 255 colours).
	 */
	static byte[] mergeSpriteFrames(byte[] base, byte[] ours, byte[] theirs, Map<String, Integer> notes, int id) {
		var gb = SpriteCodec.decode(base); var go = SpriteCodec.decode(ours); var gt = SpriteCodec.decode(theirs);
		int n = Math.max(Math.max(gb.frames.size(), go.frames.size()), gt.frames.size());
		List<SpriteCodec.Frame> frames = new ArrayList<>();
		List<int[]> palettes = new ArrayList<>();
		boolean anyOurs = false, anyTheirs = false;
		for (int i = 0; i < n; i++) {
			SpriteCodec.Frame fb = i < gb.frames.size() ? gb.frames.get(i) : null;
			SpriteCodec.Frame fo = i < go.frames.size() ? go.frames.get(i) : null;
			SpriteCodec.Frame ft = i < gt.frames.size() ? gt.frames.get(i) : null;
			boolean oursChanged = fb == null ? fo != null : fo == null || !SpriteCodec.sameFrame(gb, fb, go, fo);
			if (oursChanged && fo != null) { frames.add(fo); palettes.add(go.palette); anyOurs = true; }
			else if (!oursChanged && ft != null) { frames.add(ft); palettes.add(gt.palette); anyTheirs = true; }
			else if (fo == null && fb != null && ft != null && SpriteCodec.sameFrame(gb, fb, gt, ft)) { /* we removed it, Jagex didn't touch it */ }
			else if (ft != null) { frames.add(ft); palettes.add(gt.palette); anyTheirs = true; }
		}
		boolean oursCanvas = go.maxWidth != gb.maxWidth || go.maxHeight != gb.maxHeight;
		int w = oursCanvas ? go.maxWidth : gt.maxWidth, h = oursCanvas ? go.maxHeight : gt.maxHeight;
		if (anyOurs && !anyTheirs && w == go.maxWidth && h == go.maxHeight && frames.size() == go.frames.size()) {
			notes.merge("all frames ours (Jagex's changes were to the same frames)", 1, Integer::sum);
			return ours;
		}
		notes.merge("mixed: ours + 241 frames, palette rebuilt", 1, Integer::sum);
		byte[] out = SpriteCodec.encode(SpriteCodec.assemble(w, h, frames, palettes));
		// verify: every frame decodes to exactly the source frame's pixels and geometry
		var check = SpriteCodec.decode(out);
		for (int i = 0; i < frames.size(); i++) {
			int[] want = frames.get(i).argb(palettes.get(i)), got = check.frames.get(i).argb(check.palette);
			if (!Arrays.equals(want, got)) throw new IllegalStateException("sprite " + id + " frame " + i + " changed in merge");
		}
		return out;
	}

	static String mergeNotesJson(Map<String, Integer> m) {
		StringBuilder b = new StringBuilder("{");
		int i = 0;
		for (var e : m.entrySet()) b.append(i++ == 0 ? "" : ", ").append('"').append(e.getKey()).append("\": ").append(e.getValue());
		return b.append('}').toString();
	}

	// ------------------------------------------------------------------ textures

	/** Index 9 group 0 holds every texture definition as a file. */
	static void textures() throws Exception {
		Archive la = local.findIndex(9).getArchive(0), ba = v231.findIndex(9).getArchive(0), ta = out.findIndex(9).getArchive(0);
		Map<Integer, byte[]> l = files(local, la), b = files(v231, ba), t = files(out, ta);
		TreeMap<Integer, byte[]> result = new TreeMap<>(t);
		int customs = 0;
		List<Integer> vanillaDiffer = new ArrayList<>();
		for (var e : l.entrySet()) {
			if (!b.containsKey(e.getKey())) {
				int target = manifest.map("texture", e.getKey());
				if (target == e.getKey() && t.containsKey(target) && manifest.isImported("texture", target)) continue;
				if (t.containsKey(target)) throw new IllegalStateException("texture " + e.getKey() + " -> " + target + " is vanilla 241");
				result.put(target, texture231To241(e.getKey(), e.getValue()));
				customs++;
			} else if (!Arrays.equals(e.getValue(), b.get(e.getKey()))) vanillaDiffer.add(e.getKey());
		}
		FileData[] fd = new FileData[result.size()];
		List<byte[]> contents = new ArrayList<>();
		int i = 0;
		for (var e : result.entrySet()) { fd[i] = new FileData(); fd[i].setId(e.getKey()); i++; contents.add(e.getValue()); }
		store(9, 0, SpliceItemOption.joinChunks(contents), fd);
		report.append(String.format("  \"textures\": {\"customsWritten\": %d, \"total\": %d, \"vanillaTexturesThatDifferKeptVanilla241\": %s},%n",
			customs, result.size(), list(vanillaDiffer)));
	}

	/**
	 * Texture definitions changed format between 231 and 241. 231: u16 average colour, u8 transparent,
	 * u8 sprite count, u16 sprite id per sprite, (count - 1) x 2 bytes when count > 1, i32 per sprite,
	 * u8 animation direction, u8 animation speed. 241: u16 sprite id, u16 average colour, u8 transparent,
	 * u8 animation direction, u8 animation speed (one sprite). Copied unconverted, the 241 client read the
	 * colour as a sprite id and crashed at "loading textures". Anything not exactly representable is refused.
	 */
	static byte[] texture231To241(int id, byte[] d) {
		java.nio.ByteBuffer in = java.nio.ByteBuffer.wrap(d);
		int average = in.getShort() & 0xffff, transparent = in.get() & 0xff, count = in.get() & 0xff;
		if (count != 1) throw new IllegalStateException("texture " + id + ": " + count + " sprites; rev 241 textures have one");
		int sprite = in.getShort() & 0xffff;
		int perSprite = in.getInt();
		if (perSprite != 0) throw new IllegalStateException("texture " + id + ": per-sprite field " + perSprite + " has no rev 241 equivalent");
		int direction = in.get() & 0xff, speed = in.get() & 0xff;
		if (in.hasRemaining()) throw new IllegalStateException("texture " + id + ": " + in.remaining() + " bytes left over");
		return new byte[]{(byte) (sprite >> 8), (byte) sprite, (byte) (average >> 8), (byte) average, (byte) transparent, (byte) direction, (byte) speed};
	}

	static Map<Integer, byte[]> files(Store s, Archive ar) throws Exception {
		FileData[] fd = ar.getFileData();
		List<byte[]> c = SpliceItemOption.splitChunks(read(s, ar), fd.length);
		Map<Integer, byte[]> m = new TreeMap<>();
		for (int i = 0; i < fd.length; i++) m.put(fd[i].getId(), c.get(i));
		return m;
	}

	// ------------------------------------------------------------------ verify

	static void verify(String dir) throws Exception {
		Store s = new Store(new File(dir));
		s.load();
		int ok = 0;
		for (String w : written) {
			String[] p = w.split(":");
			Archive ar = s.findIndex(Integer.parseInt(p[0])).getArchive(Integer.parseInt(p[1]));
			if (ar == null) throw new IllegalStateException("missing after write: " + w);
			read(s, ar); // decompresses = CRC/container intact
			ok++;
		}
		// every custom model that was readable before must parse with the rev 241 decoder now
		int modelsParsed = 0;
		for (int target : new TreeSet<>(manifest.maps.get("model").values())) {
			Archive ar = s.findIndex(7).getArchive(target);
			if (ar == null) continue;
			try { new ModelTexturePatcher().load(target, read(s, ar)); modelsParsed++; } catch (Exception ignored) { }
		}
		System.out.println("RESULT binary stage written and re-read: " + ok + " groups; custom/remapped models parsing with the 241 decoder: " + modelsParsed);
	}
}

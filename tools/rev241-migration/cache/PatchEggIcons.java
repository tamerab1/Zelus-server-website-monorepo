import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.runelite.cache.fs.Store;

import java.io.File;
import java.nio.file.Files;
import java.util.*;

/**
 * Stage 20 of the rev 241 cache build (2026-11-07), run after stage 15 and before stage 17: the egg-slot icons from
 * assets/egg_icons (made by its MakeEggIcons): 12 designs x 5 tiers. Writes the textures' sprites (24767-24773), the
 * textures (497-503, under the GPU plugin's 512), the models (63409-63466: scaled t2-t5 copies of the pack's icons and
 * the 5 new designs) and 60 item records: the pack's 7 icons (40080, 40081, 40087, 40090, 40091, 40093, 40094) are
 * rewritten as t1 (Beast t2 for 40081) of their design, the rest are new at 40200-40252. Stage 17 then gives the new
 * items placeholders. The 5 new designs also move: a looping spot-anim each (frames, framemap, sequence; 5 tier
 * widths), replayed by the server's EggIconMotion while worn. These id blocks were free in the rev 241 cache when made, so this stage owns them and may
 * replace its own earlier output there. Idempotent. Usage: PatchEggIcons apply &lt;cache241&gt; &lt;assets/egg_icons&gt;
 */
public final class PatchEggIcons {

	private PatchEggIcons() {
	}

	static final int SPRITE_LO = 24767, SPRITE_HI = 24773, TEX_LO = 497, TEX_HI = 506, MODEL_LO = 63409, MODEL_HI = 63471;
	/** the motion: frame groups (index 0) and framemaps (index 1) 19002-19006, sequences 20900-20904, spot-anims 6841-6865 */
	static final int ANIM_LO = 19002, ANIM_HI = 19006, SEQ_LO = 20900, SEQ_HI = 20904, SPOT_LO = 6841, SPOT_HI = 6865;
	static final Set<Integer> PACK_ICONS = Set.of(40080, 40081, 40087, 40090, 40091, 40093, 40094);
	static final int ITEM_LO = 40200, ITEM_HI = 40252;

	public static void main(String[] a) throws Exception {
		if (a.length != 3 || !a[0].equals("apply"))
			throw new IllegalArgumentException("apply <cache241> <assets/egg_icons>");
		File dir = new File(a[2]);
		Store dst = new Store(new File(a[1]));
		dst.load();
		int changed = 0;

		// sprites (index 8)
		File[] sprites = new File(dir, "sprites").listFiles();
		for (File f : sprites) {
			int id = Integer.parseInt(f.getName().replace(".dat", ""));
			own(id, SPRITE_LO, SPRITE_HI, "sprite");
			byte[] raw = Files.readAllBytes(f.toPath());
			if (!BuildBinary241.validTextureSprite(id, raw))
				throw new IllegalStateException("sprite " + id + " is not a valid rev 241 texture sprite");
			changed += PatchSolarmane.putSingle(dst, 8, id, raw) ? 1 : 0;
		}
		// textures (index 9 group 0): u16 sprite, u16 average colour, u8 opaque (0 for cut-outs), u8 direction, u8 speed
		Map<Integer, byte[]> have = PatchSolarmane.textures(dst), texPut = new TreeMap<>();
		for (JsonElement e : json(new File(dir, "textures.json")).getAsJsonArray()) {
			JsonObject t = e.getAsJsonObject();
			int id = t.get("id").getAsInt(), sprite = t.get("sprite").getAsInt(), avg = t.get("average").getAsInt();
			own(id, TEX_LO, TEX_HI, "texture");
			own(sprite, SPRITE_LO, SPRITE_HI, "texture sprite");
			byte[] rec = { (byte) (sprite >> 8), (byte) sprite, (byte) (avg >> 8), (byte) avg, (byte) t.get("transparent").getAsInt(),
					(byte) t.get("direction").getAsInt(), (byte) t.get("speed").getAsInt() };
			if (!Arrays.equals(have.get(id), rec))
				texPut.put(id, rec);
		}
		if (!texPut.isEmpty())
			changed += PatchCustoms27.putTextures(dst, texPut);
		// models (index 7)
		Set<Integer> models = new TreeSet<>();
		for (File f : new File(dir, "models").listFiles()) {
			int id = Integer.parseInt(f.getName().replace(".dat", ""));
			own(id, MODEL_LO, MODEL_HI, "model");
			models.add(id);
			changed += PatchSolarmane.putSingle(dst, 7, id, Files.readAllBytes(f.toPath())) ? 1 : 0;
		}
		// items (index 2 group 10), through the model-bank pack's 231 record + rewrite to 241
		Map<Integer, byte[]> existing = RoundTripDefs.files(dst, 10);
		Map<Integer, byte[]> put = new TreeMap<>();
		Map<Integer, Integer> identity = new HashMap<>();
		for (var ar : dst.findIndex(7).getArchives())
			identity.put(ar.getArchiveId(), ar.getArchiveId());
		int n = 0;
		for (JsonElement e : json(new File(dir, "items.json")).getAsJsonArray()) {
			JsonObject d = e.getAsJsonObject();
			int id = d.get("id").getAsInt();
			if (!PACK_ICONS.contains(id))
				own(id, ITEM_LO, ITEM_HI, "item");
			byte[] rec241 = DefRewriter.rewrite("item", PatchModelBankPack.encode231(d, identity, Map.of()), DefRewriter.IDENTITY, k -> null);
			byte[] old = existing.get(id);
			// keep a placeholder link stage 17 already set (it re-adds it anyway on a full build)
			if (old != null) {
				Integer ph = PatchPlaceholders.u16Ops(old).get(PatchPlaceholders.OP_PLACEHOLDER);
				if (ph != null)
					rec241 = PatchPlaceholders.withPlaceholder(rec241, ph);
			}
			if (!Arrays.equals(old, rec241))
				put.put(id, rec241);
			n++;
		}
		if (!put.isEmpty())
			changed += PatchCustoms27.putConfigs(dst, 10, put);

		// motion: framemaps (index 1, one file each), frame groups (index 0, a file per frame), sequences, spot-anims
		for (File f : new File(dir, "framemaps").listFiles()) {
			int id = Integer.parseInt(f.getName().replace(".dat", ""));
			own(id, ANIM_LO, ANIM_HI, "framemap");
			changed += PatchSolarmane.putSingle(dst, 1, id, Files.readAllBytes(f.toPath())) ? 1 : 0;
		}
		for (File g : new File(dir, "frames").listFiles()) {
			int id = Integer.parseInt(g.getName());
			own(id, ANIM_LO, ANIM_HI, "frame group");
			TreeMap<Integer, byte[]> frames = new TreeMap<>();
			for (File f : g.listFiles())
				frames.put(Integer.parseInt(f.getName().replace(".dat", "")), Files.readAllBytes(f.toPath()));
			if (!frames.firstKey().equals(0) || frames.lastKey() != frames.size() - 1)
				throw new IllegalStateException("frame group " + id + " is not 0.." + (frames.size() - 1));
			var idx = dst.findIndex(0);
			var ar = idx.getArchive(id);
			if (ar == null) {
				ar = idx.addArchive(id);
				ar.setCompression(idx.getArchives().get(0).getCompression());
			} else if (sameFiles(BuildBinary241.files(dst, ar), frames))
				continue;
			PatchCustoms27.writeGroup(dst, 0, id, frames);
			System.out.println("  frame group 0/" + id + ": " + frames.size() + " frames");
			changed++;
		}
		Map<Integer, byte[]> seqs = new TreeMap<>(), spots = new TreeMap<>();
		for (File f : new File(dir, "seqs").listFiles()) {
			int id = Integer.parseInt(f.getName().replace(".dat", ""));
			own(id, SEQ_LO, SEQ_HI, "sequence");
			seqs.put(id, Files.readAllBytes(f.toPath()));
		}
		for (File f : new File(dir, "spotanims").listFiles()) {
			int id = Integer.parseInt(f.getName().replace(".dat", ""));
			own(id, SPOT_LO, SPOT_HI, "spot-anim");
			spots.put(id, Files.readAllBytes(f.toPath()));
		}
		changed += PatchCustoms27.putConfigs(dst, 12, seqs);
		changed += PatchCustoms27.putConfigs(dst, 13, spots);
		dst.close();
		System.out.println("RESULT egg icons: " + changed + " change(s); " + n + " items (" + put.size() + " written), " + models.size()
				+ " models, " + sprites.length + " sprites, textures " + TEX_LO + "-" + TEX_HI + ", " + seqs.size() + " sequences, " + spots.size()
				+ " spot-anims");
	}

	static boolean sameFiles(Map<Integer, byte[]> a, Map<Integer, byte[]> b) {
		if (!a.keySet().equals(b.keySet()))
			return false;
		for (var e : a.entrySet())
			if (!Arrays.equals(e.getValue(), b.get(e.getKey())))
				return false;
		return true;
	}

	static void own(int id, int lo, int hi, String what) {
		if (id < lo || id > hi)
			throw new IllegalStateException(what + " " + id + " is outside this stage's block " + lo + "-" + hi);
	}

	static JsonElement json(File f) throws Exception {
		return JsonParser.parseString(Files.readString(f.toPath()));
	}
}

import com.google.gson.*;
import net.runelite.cache.fs.Store;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.*;

/**
 * Stage 15 of the rev 241 cache build (2026-11-06): the "model bank" item pack (assets/model_bank_pack, from the
 * partner's revision-231 transfer pack): 64 chosen items + 30 linked note / placeholder / template records, 177
 * models, 17 textures and 17 texture sprites. The pack's own importer writes revision-231 records and must NOT be run
 * on a rev 241 cache; this stage does the same job the rev 241 way:
 * <ul>
 * <li>ids are fixed blocks, assigned in ascending source-id order: items 40001+, models 63232+, sprites 24749+,
 * textures 480+ (under the GPU plugin's 512 texture limit). Each target must be free or already hold exactly what
 * this stage writes, so a re-run changes nothing and a clash stops the build;</li>
 * <li>models: the trailer rev 241 reads is added where missing and their face textures are re-pointed at the new
 * texture ids in place (ModelTexturePatcher; re-encoding a decoded model is lossy);</li>
 * <li>textures: converted to the rev 241 record (BuildBinary241.texture231To241) with the sprite id remapped;</li>
 * <li>items: encoded as revision-231 records from items/*.json with every model / item link remapped (the pack's
 * Item231Codec rules), then upgraded to rev 241 by DefRewriter. Names lose other servers' colour codes ("@gre@",
 * "&lt;col=..&gt;"), the mystery boxes / voting package lose their "Wield" option and equip slot, and the source
 * params are dropped (their meaning on the donor servers is unknown).</li>
 * </ul>
 * Repairs (2026-11-06, after the first in-game look), fixed/&lt;model id&gt;.dat, made by FixPackModels (rev 241 library) from this
 * stage's output and written over it: the Conquest bows' worn models (63389-63391, 63398, 63407, 63408) were rigged for
 * another skeleton (label 161, held at +x): mirrored onto the twisted bow's side and moved to its label 50, so they sit
 * in the bow hand and follow the TWISTED_BOW animations; the Valkyrie helm / body / legs / wings and Forsaken bow
 * inventory models (63347-63349, 63357, 63358) sat hundreds of units off centre, so their icons showed a corner.
 * The Valkyrie helm / body / legs / wings and Forsaken bow worn models were 1300-2900 faces each: a full set with
 * the bow passed the GPU plugin's 6144-face player cap (GpuPlugin.MAX_TRIANGLE) and the player vanished, so they were
 * decimated (DecimateModel, rev 241 library: 550 / 1200 / 700 / 600 / 800 faces).
 * Decimation is a bow too (TWISTED_BOW); the SpawnPK icons go in the egg / relic slot (equip_slot 15).
 * Also writes assets/model_bank_pack/id-map.json and item_info_rows.json (the server rows to merge).
 * Usage: PatchModelBankPack apply &lt;cache241&gt; &lt;assets/model_bank_pack&gt;
 */
public final class PatchModelBankPack {

	private PatchModelBankPack() {
	}

	static final int ITEM_BASE = 40001, MODEL_BASE = 63232, SPRITE_BASE = 24749, TEXTURE_BASE = 480;
	static final Charset CP1252 = Charset.forName("windows-1252");
	/** source items that are boxes / packages, not gear (the donors put them in the weapon slot) */
	static final Set<Integer> NOT_WEARABLE = Set.of(58702, 63198, 55662, 57843);
	/**
	 * Icon angles {xan, yan, zan, zoom}: the Valkyrie body / legs inventory models are their standing worn models (4x), so
	 * the donor's xan 520 (looking down, as for vanilla's flat-lying inventory models) showed only their tops.
	 */
	static final Map<Integer, int[]> ICON = Map.of(59471, new int[] { 40, 0, 0, 8200 }, 59472, new int[] { 0, 0, 0, 6900 });
	/** the SpawnPK "icon" items: worn in the egg / relic slot (Equipment.SLOT_EGG), not on the hands / ring */
	static final Set<Integer> EGG_SLOT = Set.of(62437, 61214, 63145, 63409, 61213, 63151, 63363);
	/** weapon_type per weapon (source id); bows use the twisted bow's animations */
	static final Map<Integer, String> WEAPON_TYPE = Map.ofEntries(
			Map.entry(60023, "TWISTED_BOW"), Map.entry(59402, "TWISTED_BOW"), Map.entry(59632, "TWISTED_BOW"),
			Map.entry(60017, "TWISTED_BOW"), Map.entry(59459, "TWISTED_BOW"), Map.entry(57706, "TWISTED_BOW"),
			Map.entry(56066, "TWISTED_BOW"), Map.entry(56575, "TWISTED_BOW"), Map.entry(55402, "TWISTED_BOW"),
			Map.entry(59348, "TWISTED_BOW"), Map.entry(56064, "CROSSBOW"),
			Map.entry(57888, "WHIP"), Map.entry(55899, "WHIP"),
			Map.entry(55518, "MAGIC_STAFF"), Map.entry(60609, "MAGIC_STAFF"),
			Map.entry(60572, "ELDER_MAUL"), Map.entry(60665, "BULWARK"));

	public static void main(String[] a) throws Exception {
		if (a.length != 3 || !a[0].equals("apply"))
			throw new IllegalArgumentException("apply <cache241> <assets/model_bank_pack>");
		File dir = new File(a[2]);
		JsonObject manifest = read(new File(dir, "manifest.json"));
		TreeSet<Integer> required = ids(manifest.getAsJsonArray("requiredItems")), selected = ids(manifest.getAsJsonArray("selectedItems"));
		TreeSet<Integer> modelIds = new TreeSet<>(), textureIds = new TreeSet<>(), spriteIds = ids(manifest.getAsJsonArray("sprites"));
		for (JsonElement r : manifest.getAsJsonArray("models"))
			modelIds.add(r.getAsJsonObject().get("id").getAsInt());
		for (JsonElement r : manifest.getAsJsonArray("textures"))
			textureIds.add(r.getAsJsonObject().get("id").getAsInt());
		Map<Integer, Integer> items = block(required, ITEM_BASE), models = block(modelIds, MODEL_BASE),
				sprites = block(spriteIds, SPRITE_BASE), textures = block(textureIds, TEXTURE_BASE);
		if (TEXTURE_BASE + textures.size() > 512)
			throw new IllegalStateException("textures past the GPU plugin's 512 limit");

		Store dst = new Store(new File(a[1]));
		dst.load();
		int changed = 0;

		// sprites (index 8), as they are: one 128x128 / 64x64 frame each, or the rev 241 client rejects them
		for (var e : sprites.entrySet()) {
			byte[] raw = Files.readAllBytes(new File(dir, "sprites/" + e.getKey() + ".dat").toPath());
			if (!BuildBinary241.validTextureSprite(e.getKey(), raw))
				throw new IllegalStateException("sprite " + e.getKey() + " is not a valid rev 241 texture sprite");
			changed += free(dst, 8, e.getValue(), raw) && PatchSolarmane.putSingle(dst, 8, e.getValue(), raw) ? 1 : 0;
		}
		// textures (index 9 group 0)
		Map<Integer, byte[]> existingTex = PatchSolarmane.textures(dst);
		Map<Integer, byte[]> texPut = new TreeMap<>();
		for (var e : textures.entrySet()) {
			byte[] t241 = BuildBinary241.texture231To241(e.getKey(), Files.readAllBytes(new File(dir, "textures/" + e.getKey() + ".dat").toPath()));
			int sprite = (t241[0] & 0xff) << 8 | (t241[1] & 0xff);
			Integer to = sprites.get(sprite);
			if (to == null)
				throw new IllegalStateException("texture " + e.getKey() + " uses sprite " + sprite + ", not in the pack");
			t241[0] = (byte) (to >> 8);
			t241[1] = (byte) (int) to;
			byte[] have = existingTex.get(e.getValue());
			if (have != null && !Arrays.equals(have, t241))
				throw new IllegalStateException("texture " + e.getValue() + " is taken");
			texPut.put(e.getValue(), t241);
		}
		changed += PatchCustoms27.putTextures(dst, texPut);
		// models (index 7): trailer + texture ids patched in place
		for (var e : models.entrySet()) {
			byte[] raw = Files.readAllBytes(new File(dir, "models/" + e.getKey() + ".dat").toPath());
			byte[] fixed = ModelTexturePatcher.patch(ModelTexturePatcher.addMissingTrailer(raw), t -> textures.getOrDefault(t, t));
			File repair = new File(dir, "fixed/" + e.getValue() + ".dat");
			if (repair.exists()) { // a hand-repaired version of this model (see the class comment); replaces only our own import
				byte[] want = Files.readAllBytes(repair.toPath());
				var ar = dst.findIndex(7).getArchive(e.getValue());
				byte[] have = ar == null ? null : ar.decompress(dst.getStorage().loadArchive(ar));
				// the whole model block is this pack's (checked free when first imported), so an earlier repair may be replaced
				if (have != null && (e.getValue() < MODEL_BASE || e.getValue() >= MODEL_BASE + models.size()))
					throw new IllegalStateException("7/" + e.getValue() + " is taken");
				changed += PatchSolarmane.putSingle(dst, 7, e.getValue(), want) ? 1 : 0;
				continue;
			}
			changed += free(dst, 7, e.getValue(), fixed) && PatchSolarmane.putSingle(dst, 7, e.getValue(), fixed) ? 1 : 0;
		}
		// items (index 2 group 10)
		Map<Integer, byte[]> existing = RoundTripDefs.files(dst, 10);
		Map<Integer, byte[]> put = new TreeMap<>();
		for (int src : required) {
			JsonObject d = read(new File(dir, "items/" + src + ".json"));
			if (NOT_WEARABLE.contains(src))
				unwear(d);
			if (src == 60665) { // Ethereal dinh's bulwark: its eye (colour 7, the donor's recolour list matched nothing) in red
				JsonArray f = new JsonArray(), r = new JsonArray();
				f.add(7);
				r.add(950);
				d.add("colorFind", f);
				d.add("colorReplace", r);
			}
			// August Partyhat sits on the head and Ethereal mask is only a face plate: hiding the hair kit (which carries
			// the head and face) left a blank or invisible head
			if (src == 56962 || src == 61250)
				d.addProperty("wearPos2", -1);
			int[] icon = ICON.get(src);
			if (icon != null) {
				d.addProperty("xan2d", icon[0]);
				d.addProperty("yan2d", icon[1]);
				d.addProperty("zan2d", icon[2]);
				d.addProperty("zoom2d", icon[3]);
				d.addProperty("xOffset2d", 0);
				d.addProperty("yOffset2d", 0);
			}
			byte[] rec231 = encode231(d, models, items);
			byte[] rec241 = DefRewriter.rewrite("item", rec231, DefRewriter.IDENTITY, k -> null);
			int to = items.get(src);
			// the item block is this pack's (checked free when first imported); stage 17 (PatchPlaceholders) re-adds the
			// placeholder links this rewrite drops
			if (!Arrays.equals(existing.get(to), rec241))
				put.put(to, rec241);
		}
		changed += PatchCustoms27.putConfigs(dst, 10, put);
		dst.close();

		writeOutputs(dir, selected, items, models, textures, sprites);
		System.out.println("RESULT model bank pack: " + changed + " change(s); items " + ITEM_BASE + "-" + (ITEM_BASE + items.size() - 1)
				+ ", models " + MODEL_BASE + "-" + (MODEL_BASE + models.size() - 1) + ", sprites " + SPRITE_BASE + "-"
				+ (SPRITE_BASE + sprites.size() - 1) + ", textures " + TEXTURE_BASE + "-" + (TEXTURE_BASE + textures.size() - 1));
	}

	/** true if the group is free or already holds these bytes (then putSingle skips it); throws if it holds other data */
	static boolean free(Store dst, int index, int id, byte[] raw) throws Exception {
		var ar = dst.findIndex(index).getArchive(id);
		if (ar == null)
			return true;
		if (Arrays.equals(ar.decompress(dst.getStorage().loadArchive(ar)), raw))
			return false;
		throw new IllegalStateException(index + "/" + id + " is taken");
	}

	static Map<Integer, Integer> block(SortedSet<Integer> sources, int base) {
		Map<Integer, Integer> m = new TreeMap<>();
		int next = base;
		for (int s : sources)
			m.put(s, next++);
		return m;
	}

	static void unwear(JsonObject d) {
		d.addProperty("wearPos1", -1);
		d.addProperty("wearPos2", -1);
		d.addProperty("wearPos3", -1);
		JsonArray ops = d.getAsJsonArray("interfaceOptions");
		for (int i = 0; i < ops.size() - 1; i++)
			ops.set(i, JsonNull.INSTANCE);
	}

	static String clean(String name) {
		return name.replaceAll("@[a-z]{3}@", "").replaceAll("</?col(=[0-9a-fA-F]{6})?>", "").trim();
	}

	// ---- revision-231 item record, the pack's Item231Codec rules (no params, no retextures: models are remapped)

	static byte[] encode231(JsonObject it, Map<Integer, Integer> models, Map<Integer, Integer> items) throws Exception {
		ByteArrayOutputStream buf = new ByteArrayOutputStream();
		DataOutputStream out = new DataOutputStream(buf);
		model(out, it, 1, "inventoryModel", null, models);
		string(out, 2, clean(it.get("name").getAsString()));
		scalar(out, it, 4, "zoom2d", 2000, false);
		scalar(out, it, 5, "xan2d", 0, false);
		scalar(out, it, 6, "yan2d", 0, false);
		scalar(out, it, 7, "xOffset2d", 0, false);
		scalar(out, it, 8, "yOffset2d", 0, false);
		if (n(it, "stackable", 0) == 1)
			out.writeByte(11);
		if (n(it, "cost", 1) != 1) {
			out.writeByte(12);
			out.writeInt(n(it, "cost", 1));
		}
		scalar(out, it, 13, "wearPos1", -1, true);
		scalar(out, it, 14, "wearPos2", -1, true);
		if (it.has("members") && it.get("members").getAsBoolean())
			out.writeByte(16);
		model(out, it, 23, "maleModel0", "maleOffset", models);
		model(out, it, 24, "maleModel1", null, models);
		model(out, it, 25, "femaleModel0", "femaleOffset", models);
		model(out, it, 26, "femaleModel1", null, models);
		scalar(out, it, 27, "wearPos3", -1, true);
		JsonArray ground = it.getAsJsonObject("groundOps").getAsJsonArray("ops");
		for (int i = 0; i < Math.min(5, ground.size()); i++)
			if (!ground.get(i).isJsonNull())
				string(out, 30 + i, ground.get(i).getAsJsonObject().get("text").getAsString());
		JsonArray ops = it.getAsJsonArray("interfaceOptions");
		for (int i = 0; i < ops.size(); i++)
			if (!ops.get(i).isJsonNull() && !ops.get(i).getAsString().isEmpty())
				string(out, 35 + i, ops.get(i).getAsString());
		if (it.has("colorFind")) {
			JsonArray f = it.getAsJsonArray("colorFind"), r = it.getAsJsonArray("colorReplace");
			if (f.size() > 255 || f.size() != r.size())
				throw new IllegalStateException("bad recolours");
			out.writeByte(40);
			out.writeByte(f.size());
			for (int i = 0; i < f.size(); i++) {
				out.writeShort(f.get(i).getAsInt());
				out.writeShort(r.get(i).getAsInt());
			}
		}
		scalar(out, it, 42, "shiftClickDropIndex", -2, true);
		if (it.has("geTradeable") && it.get("geTradeable").getAsBoolean())
			out.writeByte(65);
		scalar(out, it, 75, "weight", 0, false);
		model(out, it, 78, "maleModel2", null, models);
		model(out, it, 79, "femaleModel2", null, models);
		model(out, it, 90, "maleHeadModel", null, models);
		model(out, it, 91, "femaleHeadModel", null, models);
		model(out, it, 92, "maleHeadModel2", null, models);
		model(out, it, 93, "femaleHeadModel2", null, models);
		scalar(out, it, 94, "category", 0, false);
		scalar(out, it, 95, "zan2d", 0, false);
		link(out, it, 97, "notedID", items);
		link(out, it, 98, "notedTemplate", items);
		scalar(out, it, 110, "resizeX", 128, false);
		scalar(out, it, 111, "resizeY", 128, false);
		scalar(out, it, 112, "resizeZ", 128, false);
		scalar(out, it, 113, "ambient", 0, true);
		scalar(out, it, 114, "contrast", 0, true);
		scalar(out, it, 115, "team", 0, true);
		link(out, it, 139, "boughtId", items);
		link(out, it, 140, "boughtTemplateId", items);
		link(out, it, 148, "placeholderId", items);
		link(out, it, 149, "placeholderTemplateId", items);
		out.writeByte(0);
		return buf.toByteArray();
	}

	static int n(JsonObject it, String k, int fallback) {
		return it.has(k) && !it.get(k).isJsonNull() ? it.get(k).getAsInt() : fallback;
	}

	static void string(DataOutputStream out, int op, String s) throws Exception {
		if (s.indexOf('\0') >= 0 || !CP1252.newEncoder().canEncode(s))
			throw new IllegalArgumentException("text not CP1252: " + s);
		out.writeByte(op);
		out.write(s.getBytes(CP1252));
		out.writeByte(0);
	}

	static void scalar(DataOutputStream out, JsonObject it, int op, String k, int fallback, boolean u8) throws Exception {
		int v = n(it, k, fallback);
		if (v == fallback)
			return;
		out.writeByte(op);
		if (u8)
			out.writeByte(v);
		else
			out.writeShort(v);
	}

	static void model(DataOutputStream out, JsonObject it, int op, String k, String offset, Map<Integer, Integer> models) throws Exception {
		int v = n(it, k, -1);
		if (v < 0)
			return;
		Integer to = models.get(v);
		if (to == null)
			throw new IllegalStateException("item " + it.get("id") + ": model " + v + " not in the pack");
		out.writeByte(op);
		out.writeShort(to);
		if (offset != null)
			out.writeByte(n(it, offset, 0));
	}

	static void link(DataOutputStream out, JsonObject it, int op, String k, Map<Integer, Integer> items) throws Exception {
		int v = n(it, k, -1);
		if (v < 0)
			return;
		Integer to = items.get(v);
		if (to == null)
			throw new IllegalStateException("item " + it.get("id") + ": link " + k + " " + v + " not in the pack");
		out.writeByte(op);
		out.writeShort(to);
	}

	// ---- outputs: id map + the server rows

	static void writeOutputs(File dir, Set<Integer> selected, Map<Integer, Integer> items, Map<Integer, Integer> models,
			Map<Integer, Integer> textures, Map<Integer, Integer> sprites) throws Exception {
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		Map<String, Object> idMap = new LinkedHashMap<>();
		idMap.put("items", items);
		idMap.put("models", models);
		idMap.put("textures", textures);
		idMap.put("sprites", sprites);
		Files.writeString(new File(dir, "id-map.json").toPath(), gson.toJson(idMap) + "\n");
		JsonArray rows = new JsonArray();
		for (int src : selected) {
			JsonObject d = read(new File(dir, "items/" + src + ".json"));
			JsonObject row = new JsonObject();
			row.addProperty("name", clean(d.get("name").getAsString()));
			row.addProperty("id", items.get(src));
			row.addProperty("tradeable", true);
			row.addProperty("examine", "A " + clean(d.get("name").getAsString()).toLowerCase() + ".");
			row.addProperty("weight", 1.0);
			if (!NOT_WEARABLE.contains(src)) {
				int slot = EGG_SLOT.contains(src) ? 15 : n(d, "wearPos1", -1), p2 = src == 56962 || src == 61250 ? -1 : n(d, "wearPos2", -1), p3 = n(d, "wearPos3", -1);
				if (slot >= 0) {
					row.addProperty("equip_slot", slot);
					if (p2 == 6 || p3 == 6)
						row.addProperty("hide_arms", true);
					if (p2 == 8 || p3 == 8)
						row.addProperty("hide_hair", true);
					if (p2 == 11 || p3 == 11)
						row.addProperty("hide_beard", true);
					if (slot == 3) {
						String type = WEAPON_TYPE.get(src);
						if (type == null)
							throw new IllegalStateException("weapon " + src + " has no weapon_type");
						row.addProperty("weapon_type", type);
						// the ammo they fire (PlayerCombat: "Unhandled ranged weapon" without it): bows arrows, crossbows bolts
						if (type.equals("TWISTED_BOW")) {
							row.addProperty("ranged_weapon", "TWISTED_BOW");
							row.addProperty("ranged_level", 75);
						} else if (type.equals("CROSSBOW")) {
							row.addProperty("ranged_weapon", "CROSSBOW");
							row.addProperty("ranged_level", 65);
						}
						if (p2 == 5 || type.equals("TWISTED_BOW") || type.equals("ELDER_MAUL") || type.equals("BULWARK"))
							row.addProperty("two_handed", true);
					}
				}
			}
			rows.add(row);
		}
		Files.writeString(new File(dir, "item_info_rows.json").toPath(), gson.toJson(rows) + "\n");
	}

	static JsonObject read(File f) throws Exception {
		return JsonParser.parseString(Files.readString(f.toPath())).getAsJsonObject();
	}

	static TreeSet<Integer> ids(JsonArray a) {
		TreeSet<Integer> s = new TreeSet<>();
		for (JsonElement e : a)
			s.add(e.getAsInt());
		return s;
	}
}

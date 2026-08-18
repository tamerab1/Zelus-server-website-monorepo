import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.ItemDefinition;
import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.NpcDefinition;
import net.runelite.cache.definitions.loaders.ItemLoader;
import net.runelite.cache.definitions.loaders.ModelLoader;
import net.runelite.cache.definitions.loaders.NpcLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Container;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;
import net.runelite.cache.index.IndexData;
import net.runelite.cache.io.OutputStream;

// Batch-produces Shadow/Golden/Blood recolor variants for a whole list of boss pets in a single
// Store session (open once, apply every splice in memory, save archives 9+10 once at the end).
// Reuses the same rename+recolor byte-splicing technique as SpliceRecolorCopy.java and the same
// deterministic per-tier palette transform as GenPaletteShift.java, just looped across many bosses
// instead of one at a time -- doing this as 64 separate process invocations would be far slower
// and riskier (each one re-reads/re-writes the whole archive from disk).
//
// Usage: <verify|apply> <cachePath> <startItemId> <startNpcId> <outDir>
public class BatchRecolorAll {

	static final class Boss {
		final String enumName;
		final int itemId;
		final int npcId;
		final int dropAverage;

		Boss(String enumName, int itemId, int npcId, int dropAverage) {
			this.enumName = enumName;
			this.itemId = itemId;
			this.npcId = npcId;
			this.dropAverage = dropAverage;
		}
	}

	static final Boss[] BOSSES = {
		new Boss("ABYSSAL_ORPHAN", 13262, 5884, 1000),
		new Boss("BABY_MOLE", 12646, 6635, 1000),
		new Boss("CALLISTO_CUB", 13178, 5558, 1000),
		new Boss("HELLPUPPY", 13247, 3099, 1000),
		new Boss("KALPHITE_PRINCESS", 12647, 6638, 1000),
		new Boss("DAGANNOTH_PRIME", 12644, 6629, 2000),
		new Boss("DAGANNOTH_REX", 12645, 6630, 2000),
		new Boss("DAGGANOTH_SUPREME", 12643, 6628, 2000),
		new Boss("GUARDIAN_MUMMY_PET", 30138, 30013, 1000),
		new Boss("MIMIC_JR", 30425, 30012, 1000),
		new Boss("LIL_CRYSTAL", 59989, 30046, 1000),
		new Boss("GAUNTLET_MINION", 30194, 30047, 1000),
		new Boss("VOIDFANG", 59991, 30048, 1000),
		new Boss("LIL_WYVERN", 60008, 30051, 1000),
		new Boss("LIL_FROSTWYRM", 60009, 30052, 1000),
		new Boss("JAL_NIB_REK", 21291, 7675, 1000),
		new Boss("KRAKEN", 12655, 6640, 1000),
		new Boss("SMOKE_DEVIL", 12648, 6639, 1000),
		new Boss("SNAKELING_GREEN", 12921, 2130, 1000),
		new Boss("ZILYANA", 12651, 6633, 1000),
		new Boss("TZREK_JAD", 13225, 5893, 100),
		new Boss("VETION_JR_PURPLE", 13179, 5559, 1000),
		new Boss("SKOTOS", 21273, 7671, 50),
		new Boss("LIL_ZIK", 22473, 8337, 500),
		new Boss("LIL_MAIDEN", 25748, 10870, 500),
		new Boss("LIL_BLOAT", 25749, 10871, 500),
		new Boss("LIL_NYLO", 25750, 10872, 500),
		new Boss("TZREK_ZUK", 22319, 8009, 100),
		new Boss("CORPOREAL_CRITTER", 22318, 8010, 1000),
		new Boss("IKKLE_HYDRA_GREEN", 22746, 8492, 1000),
		new Boss("BALANCE_ELEMENTAL_RANGED", 33005, 17017, 1800),
		new Boss("BALANCE_ELEMENTAL_MAGE", 33004, 17015, 1800),
		new Boss("BALANCE_ELEMENTAL_MELEE", 33006, 17016, 1800),
		new Boss("SRARACHA", 23495, 2143, 1000),
		new Boss("TINY_TEMPOR", 25602, 10637, 1000),
		new Boss("NEXLING", 26348, 11276, 1000),
		new Boss("BABY_GALVEK", 30417, 30005, 1000),
		new Boss("LEVIATHAN", 28252, 12160, 1000),
		new Boss("DUKE", 28250, 12159, 1000),
		new Boss("SMOL_HEREDIT", 28960, 12857, 1000),
		new Boss("NID", 29836, 13681, 1000),
		new Boss("SCURRY", 28801, 7616, 1000),
		new Boss("BUTCH", 28248, 12158, 1000),
		new Boss("WISP", 28246, 12157, 1000),
		new Boss("PHANTOM_MUSPAH", 27590, 12014, 1000),
		new Boss("TUMEKENS_GUARDIAN", 27352, 11812, 1000),
		new Boss("ELIDINIS_GUARDIAN", 27354, 11813, 1000),
		new Boss("DOM", 31130, 14785, 2000),
		new Boss("YAMI_SHINY", 33030, 17029, 1000),
		new Boss("YAMI", 30888, 14204, 3000),
		new Boss("TINY_TORMENTED", 33031, 17030, 2000),
		new Boss("AKKHITO", 27382, 11846, 1000),
		new Boss("KEPHRITI", 27384, 11848, 1000),
		new Boss("ZEBO", 27385, 11849, 1000),
		new Boss("TUMEKEN_DAMAGED", 27386, 11844, 1000),
		new Boss("OPHI_THE_HEALER", 30615, 16001, 1000),
		new Boss("OPHI_THE_SOLDIER", 30616, 16002, 1000),
		new Boss("OPHI_THE_BRUTE", 30617, 16003, 1800),
		new Boss("SUMMER_DRAGON", 33009, 17018, 1000),
		new Boss("SUMMER_SPIRIT", 33008, 17019, 1000),
		new Boss("LITTLE_NIGHTMARE", 24491, 9398, 2000),
		new Boss("PHOENIX", 20693, 7370, 2000),
		new Boss("PENANCE_QUEEN", 12703, 6674, 1000),
		new Boss("PRINCE_BLACK_DRAGON", 12653, 6636, 1000),
	};

	static final String[] TIERS = {"Shadow", "Golden", "Blood"};

	public static void main(String[] args) throws Exception {
		String mode = args[0];
		String cachePath = args[1];
		int nextItemId = Integer.parseInt(args[2]);
		int nextNpcId = Integer.parseInt(args[3]);
		String outDir = args[4];

		try (Store store = new Store(new File(cachePath))) {
			store.load();
			Storage storage = store.getStorage();
			Index configIndex = store.getIndex(IndexType.CONFIGS);
			Archive itemArchive = configIndex.getArchive(ConfigType.ITEM.getId());
			Archive npcArchive = configIndex.getArchive(ConfigType.NPC.getId());

			byte[] itemDecompressed = itemArchive.decompress(storage.loadArchive(itemArchive));
			FileData[] itemFileDataArr = itemArchive.getFileData();
			List<byte[]> itemContents = SpliceItemOption.splitChunks(itemDecompressed, itemFileDataArr.length);
			List<FileData> itemFileData = new ArrayList<>(Arrays.asList(itemFileDataArr));

			byte[] npcDecompressed = npcArchive.decompress(storage.loadArchive(npcArchive));
			FileData[] npcFileDataArr = npcArchive.getFileData();
			List<byte[]> npcContents = SpliceItemOption.splitChunks(npcDecompressed, npcFileDataArr.length);
			List<FileData> npcFileData = new ArrayList<>(Arrays.asList(npcFileDataArr));

			Index modelsIndex = store.getIndex(IndexType.MODELS);

			List<String> jsonEntries = new ArrayList<>();
			List<String> enumLines = new ArrayList<>();
			List<String> itemInfoLines = new ArrayList<>();
			int processed = 0, skipped = 0;

			for (Boss boss : BOSSES) {
				int itemSlot = findLastSlot(itemFileData, boss.itemId);
				int npcSlot = findLastSlot(npcFileData, boss.npcId);
				if (itemSlot == -1 || npcSlot == -1) {
					System.out.println("SKIP " + boss.enumName + " -- item found=" + (itemSlot != -1) + " npc found=" + (npcSlot != -1));
					skipped++;
					continue;
				}

				byte[] itemSourceRaw = itemContents.get(itemSlot);
				byte[] npcSourceRaw = npcContents.get(npcSlot);

				ItemDefinition itemDef = new ItemLoader().load(boss.itemId, itemSourceRaw);
				NpcDefinition npcDef = new NpcLoader().load(boss.npcId, npcSourceRaw);

				int[] itemModelIds = {itemDef.inventoryModel, itemDef.maleModel0, itemDef.maleModel1, itemDef.maleModel2};
				int[] npcModelIds = npcDef.models == null ? new int[0] : npcDef.models;

				List<Short> itemColors = collectColors(storage, modelsIndex, itemModelIds);
				List<Short> npcColors = collectColors(storage, modelsIndex, npcModelIds);

				String cleanItemBase = stripPetPrefix(itemDef.name).toLowerCase();
				String cleanNpcBase = npcDef.name.toLowerCase();

				StringBuilder jsonVariants = new StringBuilder();
				for (int t = 0; t < TIERS.length; t++) {
					String tier = TIERS[t];
					int newItemId = nextItemId++;
					int newNpcId = nextNpcId++;
					String itemVariantName = tier + " " + cleanItemBase;
					String npcVariantName = tier + " " + cleanNpcBase;

					short[] itemFind = new short[itemColors.size()];
					short[] itemReplace = new short[itemColors.size()];
					fillPairs(itemColors, tier, itemFind, itemReplace);

					short[] npcFind = new short[npcColors.size()];
					short[] npcReplace = new short[npcColors.size()];
					fillPairs(npcColors, tier, npcFind, npcReplace);

					byte[] newItemRaw = buildRenamedRecolored(itemSourceRaw, "item", itemVariantName, itemFind, itemReplace);
					byte[] newNpcRaw = buildRenamedRecolored(npcSourceRaw, "npc", npcVariantName, npcFind, npcReplace);

					ItemDefinition afterItem = new ItemLoader().load(newItemId, newItemRaw);
					if (!afterItem.name.equals(itemVariantName) || afterItem.inventoryModel != itemDef.inventoryModel)
						throw new IllegalStateException("item self-verify failed for " + boss.enumName + " " + tier);
					NpcDefinition afterNpc = new NpcLoader().load(newNpcId, newNpcRaw);
					if (!afterNpc.name.equals(npcVariantName) || !Arrays.equals(afterNpc.models, npcDef.models))
						throw new IllegalStateException("npc self-verify failed for " + boss.enumName + " " + tier);

					itemContents.add(newItemRaw);
					FileData newItemFd = new FileData();
					newItemFd.setId(newItemId);
					newItemFd.setNameHash(-1);
					itemFileData.add(newItemFd);

					npcContents.add(newNpcRaw);
					FileData newNpcFd = new FileData();
					newNpcFd.setId(newNpcId);
					newNpcFd.setNameHash(-1);
					npcFileData.add(newNpcFd);

					int dpCost, vpCost;
					switch (tier) {
						case "Shadow": dpCost = 100; vpCost = 10; break;
						case "Golden": dpCost = 500; vpCost = 40; break;
						case "Blood": dpCost = 350; vpCost = 25; break;
						default: throw new IllegalArgumentException("tier must be Shadow|Golden|Blood");
					}

					if (jsonVariants.length() > 0) jsonVariants.append(",\n");
					jsonVariants.append(String.format(
						"      { \"theme\": \"%s\", \"itemId\": %d, \"npcId\": %d, \"name\": \"%s\", \"dpCost\": %d, \"vpCost\": %d }",
						tier, newItemId, newNpcId, itemVariantName, dpCost, vpCost));

					enumLines.add(String.format("\t%s_%s(%d, %d, false),", tier.toUpperCase(), boss.enumName, newItemId, newNpcId));

					itemInfoLines.add(String.format(
						"  { \"name\": \"%s\", \"id\": %d, \"tradeable\": false, \"examine\": \"%s.\", \"weight\": 1.0, \"protect_value\": 0 },",
						itemVariantName, newItemId, itemVariantName));
				}

				jsonEntries.add(String.format(
					"  {\n    \"basePet\": \"%s\",\n    \"baseItemId\": %d,\n    \"variants\": [\n%s\n    ]\n  }",
					boss.enumName, boss.itemId, jsonVariants));

				System.out.println("Processed " + boss.enumName + " -> items " + (nextItemId - 3) + "-" + (nextItemId - 1)
					+ ", npcs " + (nextNpcId - 3) + "-" + (nextNpcId - 1));
				processed++;
			}

			new File(outDir).mkdirs();
			Files.writeString(Paths.get(outDir, "batch_config.json"), "[\n" + String.join(",\n", jsonEntries) + "\n]\n");
			Files.writeString(Paths.get(outDir, "batch_enum.txt"), String.join("\n", enumLines) + "\n");
			Files.writeString(Paths.get(outDir, "batch_item_info.txt"), String.join("\n", itemInfoLines) + "\n");

			System.out.println("=== " + processed + " bosses processed, " + skipped + " skipped. nextItemId=" + nextItemId + " nextNpcId=" + nextNpcId + " ===");

			if (mode.equals("verify")) {
				System.out.println("VERIFY mode -- not writing to cache. Output files written to " + outDir);
				return;
			}

			itemArchive.setFileData(itemFileData.toArray(new FileData[0]));
			byte[] newItemDecompressed = SpliceItemOption.joinChunks(itemContents);
			Container itemContainer = new Container(itemArchive.getCompression(), -1);
			itemContainer.compress(newItemDecompressed, null);
			storage.store(configIndex.getId(), itemArchive.getArchiveId(), itemContainer.data);
			itemArchive.setCrc(itemContainer.crc);
			itemArchive.setRevision(itemArchive.getRevision() + 1);
			itemArchive.setCompressedSize(itemContainer.data.length);
			itemArchive.setDecompressedSize(newItemDecompressed.length);

			npcArchive.setFileData(npcFileData.toArray(new FileData[0]));
			byte[] newNpcDecompressed = SpliceItemOption.joinChunks(npcContents);
			Container npcContainer = new Container(npcArchive.getCompression(), -1);
			npcContainer.compress(newNpcDecompressed, null);
			storage.store(configIndex.getId(), npcArchive.getArchiveId(), npcContainer.data);
			npcArchive.setCrc(npcContainer.crc);
			npcArchive.setRevision(npcArchive.getRevision() + 1);
			npcArchive.setCompressedSize(npcContainer.data.length);
			npcArchive.setDecompressedSize(newNpcDecompressed.length);

			IndexData indexData = configIndex.toIndexData();
			byte[] rawIndex = indexData.writeIndexData();
			Container idxContainer = new Container(configIndex.getCompression(), -1);
			idxContainer.compress(rawIndex, null);
			storage.store(255, configIndex.getId(), idxContainer.data);
			configIndex.setCrc(idxContainer.crc);

			System.out.println("APPLY complete. Item archive revision " + itemArchive.getRevision()
				+ ", npc archive revision " + npcArchive.getRevision() + ".");
		}
	}

	static int findLastSlot(List<FileData> fileData, int id) {
		int slot = -1;
		for (int i = 0; i < fileData.size(); i++) {
			if (fileData.get(i).getId() == id) slot = i;
		}
		return slot;
	}

	static String stripPetPrefix(String name) {
		return name.startsWith("Pet ") ? name.substring(4) : name;
	}

	static List<Short> collectColors(Storage storage, Index modelsIndex, int[] modelIds) throws Exception {
		Set<Short> set = new LinkedHashSet<>();
		for (int modelId : modelIds) {
			if (modelId <= 0) continue;
			Archive modelArchive = modelsIndex.getArchive(modelId);
			if (modelArchive == null) continue;
			byte[] data = modelArchive.decompress(storage.loadArchive(modelArchive));
			ModelDefinition model = new ModelLoader().load(modelId, data);
			if (model.faceColors == null) continue;
			for (short c : model.faceColors) set.add(c);
		}
		return new ArrayList<>(set);
	}

	static void fillPairs(List<Short> colors, String tier, short[] find, short[] replace) {
		for (int i = 0; i < colors.size(); i++) {
			short hsl = colors.get(i);
			int h = (hsl >> 10) & 63;
			int s = (hsl >> 7) & 7;
			int l = hsl & 127;
			int nh, ns, nl;
			switch (tier) {
				case "Shadow":
					nh = h;
					ns = Math.min(s, 2);
					nl = l <= 3 ? l : Math.max(3, (int) Math.round(l * 0.4));
					break;
				case "Golden":
					nh = 8;
					ns = l <= 3 ? 3 : 6;
					nl = l <= 3 ? Math.max(4, l + 3) : clamp((int) Math.round(l * 0.9 + 15), 20, 100);
					break;
				case "Blood":
					nh = 0;
					ns = 7;
					nl = l <= 3 ? Math.max(2, l + 2) : clamp((int) Math.round(l * 0.6 + 10), 15, 55);
					break;
				default:
					throw new IllegalArgumentException("tier must be Shadow|Golden|Blood");
			}
			find[i] = hsl;
			replace[i] = (short) (((nh & 63) << 10) | ((ns & 7) << 7) | (nl & 127));
		}
	}

	static byte[] buildRenamedRecolored(byte[] sourceRaw, String kind, String newName, short[] find, short[] replace) throws Exception {
		int[] nameSpan = SpliceRecolorCopy.findOpcode2Span(sourceRaw, kind);
		if (nameSpan == null)
			throw new IllegalStateException("source has no opcode-2 (name)");
		byte[] nameBytes = newName.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
		byte[] newNameField = new byte[1 + nameBytes.length + 1];
		newNameField[0] = 2;
		System.arraycopy(nameBytes, 0, newNameField, 1, nameBytes.length);
		newNameField[newNameField.length - 1] = 0;

		byte[] renamed = new byte[sourceRaw.length - (nameSpan[1] - nameSpan[0]) + newNameField.length];
		System.arraycopy(sourceRaw, 0, renamed, 0, nameSpan[0]);
		System.arraycopy(newNameField, 0, renamed, nameSpan[0], newNameField.length);
		System.arraycopy(sourceRaw, nameSpan[1], renamed, nameSpan[0] + newNameField.length,
			sourceRaw.length - nameSpan[1]);

		int insertAt = SpliceRecolorCopy.findTerminatorOffset(renamed, kind);

		OutputStream block = new OutputStream();
		if (find.length > 0) {
			block.writeByte(40);
			block.writeByte(find.length);
			for (int i = 0; i < find.length; i++) {
				block.writeShort(find[i] & 0xFFFF);
				block.writeShort(replace[i] & 0xFFFF);
			}
		}
		byte[] blockBytes = block.flip();

		byte[] newRaw = new byte[renamed.length + blockBytes.length];
		System.arraycopy(renamed, 0, newRaw, 0, insertAt);
		System.arraycopy(blockBytes, 0, newRaw, insertAt, blockBytes.length);
		System.arraycopy(renamed, insertAt, newRaw, insertAt + blockBytes.length, renamed.length - insertAt);
		return newRaw;
	}

	static int clamp(int v, int lo, int hi) {
		return Math.max(lo, Math.min(hi, v));
	}
}

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
import net.runelite.cache.io.InputStream;
import net.runelite.cache.io.OutputStream;
import org.json.JSONArray;
import org.json.JSONObject;

// Re-splices ALREADY-EXISTING pet recolor variants (same item/npc ids, produced by
// BatchRecolorAll.java) with a SELECTIVE colour map instead of the original "recolor every
// distinct colour" approach: the single most-frequent colour (the body's dominant tone) is left
// untouched, and only the remaining accent colours (eyes, trim, glow details, etc.) get remapped
// into the tier theme. This fixes variants reading as flat single-colour blobs.
//
// Reads variant item/npc ids straight from batch_config.json (same file BatchRecolorAll.java
// produced) so there's no risk of guessing wrong ids. Base (pre-recolor) colours are read from
// the ORIGINAL base npc/item, not from the already-recoloured variant.
//
// Usage: <verify|apply> <cachePath> <batchConfigPath> <bossEnumName1,bossEnumName2,...>
public class FixRecolorSelective {

    public static void main(String[] args) throws Exception {
        String mode = args[0];
        String cachePath = args[1];
        String batchConfigPath = args[2];
        var wantedBosses = new java.util.HashSet<>(java.util.Arrays.asList(args[3].split(",")));

        var configJson = new JSONArray(new String(java.nio.file.Files.readAllBytes(
                java.nio.file.Paths.get(batchConfigPath)), StandardCharsets.UTF_8));

        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Index configIndex = store.getIndex(IndexType.CONFIGS);
            Archive itemArchive = configIndex.getArchive(ConfigType.ITEM.getId());
            Archive npcArchive = configIndex.getArchive(ConfigType.NPC.getId());
            Index modelsIndex = store.getIndex(IndexType.MODELS);

            byte[] itemDecompressed = itemArchive.decompress(storage.loadArchive(itemArchive));
            FileData[] itemFileData = itemArchive.getFileData();
            List<byte[]> itemContents = SpliceItemOption.splitChunks(itemDecompressed, itemFileData.length);

            byte[] npcDecompressed = npcArchive.decompress(storage.loadArchive(npcArchive));
            FileData[] npcFileData = npcArchive.getFileData();
            List<byte[]> npcContents = SpliceItemOption.splitChunks(npcDecompressed, npcFileData.length);

            int fixedItems = 0, fixedNpcs = 0;

            for (int b = 0; b < configJson.length(); b++) {
                JSONObject bossEntry = configJson.getJSONObject(b);
                String basePet = bossEntry.getString("basePet");
                if (!wantedBosses.contains(basePet)) continue;
                int baseItemId = bossEntry.getInt("baseItemId");

                int baseItemSlot = findLastSlot(itemFileData, baseItemId);
                if (baseItemSlot == -1) {
                    System.out.println("SKIP " + basePet + " -- base item " + baseItemId + " not found");
                    continue;
                }
                ItemDefinition baseItemDef = new ItemLoader().load(baseItemId, itemContents.get(baseItemSlot));
                int baseNpcId = findBaseNpcId(bossEntry, npcFileData, npcContents);
                if (baseNpcId == -1) {
                    System.out.println("SKIP " + basePet + " -- could not resolve base npc id");
                    continue;
                }
                int baseNpcSlot = findLastSlot(npcFileData, baseNpcId);
                NpcDefinition baseNpcDef = new NpcLoader().load(baseNpcId, npcContents.get(baseNpcSlot));

                int[] itemModelIds = {baseItemDef.inventoryModel, baseItemDef.maleModel0,
                        baseItemDef.maleModel1, baseItemDef.maleModel2};
                int[] npcModelIds = baseNpcDef.models == null ? new int[0] : baseNpcDef.models;

                Map<Short, Integer> itemColorCounts = collectColorCounts(storage, modelsIndex, itemModelIds);
                Map<Short, Integer> npcColorCounts = collectColorCounts(storage, modelsIndex, npcModelIds);

                JSONArray variants = bossEntry.getJSONArray("variants");
                for (int v = 0; v < variants.length(); v++) {
                    JSONObject variant = variants.getJSONObject(v);
                    String tier = variant.getString("theme");
                    int itemId = variant.getInt("itemId");
                    int npcId = variant.getInt("npcId");

                    short[][] itemPairs = buildSelectivePairs(itemColorCounts, tier);
                    short[][] npcPairs = buildSelectivePairs(npcColorCounts, tier);

                    int itemSlot = findLastSlot(itemFileData, itemId);
                    if (itemSlot != -1) {
                        byte[] newRaw = replaceOpcode40(itemContents.get(itemSlot), "item", itemPairs[0], itemPairs[1]);
                        ItemDefinition after = new ItemLoader().load(itemId, newRaw);
                        if (after.colorFind == null || after.colorFind.length != itemPairs[0].length)
                            throw new IllegalStateException("item " + itemId + " self-verify failed (" + tier + ")");
                        itemContents.set(itemSlot, newRaw);
                        fixedItems++;
                    } else {
                        System.out.println("  WARN: item " + itemId + " (" + tier + " " + basePet + ") not found, skipped");
                    }

                    int npcSlot = findLastSlot(npcFileData, npcId);
                    if (npcSlot != -1) {
                        byte[] newRaw = replaceOpcode40(npcContents.get(npcSlot), "npc", npcPairs[0], npcPairs[1]);
                        NpcDefinition after = new NpcLoader().load(npcId, newRaw);
                        if (after.recolorToFind == null || after.recolorToFind.length != npcPairs[0].length)
                            throw new IllegalStateException("npc " + npcId + " self-verify failed (" + tier + ")");
                        npcContents.set(npcSlot, newRaw);
                        fixedNpcs++;
                    } else {
                        System.out.println("  WARN: npc " + npcId + " (" + tier + " " + basePet + ") not found, skipped");
                    }

                    System.out.println(basePet + " " + tier + ": preserved primary colour, remapped "
                            + npcPairs[0].length + "/" + npcColorCounts.size() + " npc colours, "
                            + itemPairs[0].length + "/" + itemColorCounts.size() + " item colours");
                }
            }

            System.out.println("=== " + fixedItems + " items, " + fixedNpcs + " npcs re-spliced ===");

            if (mode.equals("verify")) {
                System.out.println("VERIFY mode -- not writing to cache.");
                return;
            }

            itemArchive.setFileData(itemFileData);
            byte[] newItemDecompressed = SpliceItemOption.joinChunks(itemContents);
            Container itemContainer = new Container(itemArchive.getCompression(), -1);
            itemContainer.compress(newItemDecompressed, null);
            storage.store(configIndex.getId(), itemArchive.getArchiveId(), itemContainer.data);
            itemArchive.setCrc(itemContainer.crc);
            itemArchive.setRevision(itemArchive.getRevision() + 1);
            itemArchive.setCompressedSize(itemContainer.data.length);
            itemArchive.setDecompressedSize(newItemDecompressed.length);

            npcArchive.setFileData(npcFileData);
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

    // batch_config.json only stores baseItemId, not baseNpcId -- recover it from the base item's
    // own linked NPC model isn't reliable, so instead we resolve it the same way BatchRecolorAll
    // originally did: base npc id = variant npc id minus its position in the BOSSES table is not
    // recoverable here either. Simplest reliable path: base pet ITEM def carries no npc link, but
    // every base pet NPC in this game already exists as a normal followable pet with a matching
    // (case-insensitive, "Pet " stripped) name to the item -- resolve by name instead of id math.
    // A few base pets' real cache name doesn't match the stripped variant name closely enough
    // for the name-match fallback below (verified via LookupNpcNames): "Zilyana Jr." vs
    // "zilyana", "Guardian Mummy" vs "guardian mummy pet", "Mimic Jr" vs "mimic jr pet".
    static final Map<String, Integer> BASE_NPC_ID_OVERRIDES = Map.of(
            "ZILYANA", 6633,
            "GUARDIAN_MUMMY_PET", 30013,
            "MIMIC_JR", 30012);

    static int findBaseNpcId(JSONObject bossEntry, FileData[] npcFileData, List<byte[]> npcContents) {
        String basePet = bossEntry.getString("basePet");
        if (BASE_NPC_ID_OVERRIDES.containsKey(basePet)) {
            return BASE_NPC_ID_OVERRIDES.get(basePet);
        }
        String firstVariantName = bossEntry.getJSONArray("variants").getJSONObject(0).getString("name");
        // Strip the "Shadow "/"Golden "/"Blood " tier prefix to get the base pet's own name.
        String baseName = firstVariantName.replaceFirst("^(Shadow|Golden|Blood) ", "");
        NpcLoader loader = new NpcLoader();
        for (int i = npcFileData.length - 1; i >= 0; i--) {
            try {
                NpcDefinition def = loader.load(npcFileData[i].getId(), npcContents.get(i));
                if (def.name != null && def.name.equalsIgnoreCase(baseName)) {
                    return npcFileData[i].getId();
                }
            } catch (Exception ignored) {
            }
        }
        return -1;
    }

    static int findLastSlot(FileData[] fileData, int id) {
        int slot = -1;
        for (int i = 0; i < fileData.length; i++) {
            if (fileData[i].getId() == id) slot = i;
        }
        return slot;
    }

    static Map<Short, Integer> collectColorCounts(Storage storage, Index modelsIndex, int[] modelIds) throws Exception {
        Map<Short, Integer> counts = new LinkedHashMap<>();
        for (int modelId : modelIds) {
            if (modelId <= 0) continue;
            Archive modelArchive = modelsIndex.getArchive(modelId);
            if (modelArchive == null) continue;
            byte[] data = modelArchive.decompress(storage.loadArchive(modelArchive));
            ModelDefinition model = new ModelLoader().load(modelId, data);
            if (model.faceColors == null) continue;
            for (short c : model.faceColors) {
                counts.merge(c, 1, Integer::sum);
            }
        }
        return counts;
    }

    // Returns {find[], replace[]} covering every colour EXCEPT the single most-frequent one
    // (the body's dominant/primary tone, left untouched to preserve the pet's identity).
    static short[][] buildSelectivePairs(Map<Short, Integer> colorCounts, String tier) {
        if (colorCounts.isEmpty()) {
            return new short[][]{new short[0], new short[0]};
        }
        short primary = colorCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .get().getKey();

        List<Short> accents = new ArrayList<>();
        for (short c : colorCounts.keySet()) {
            if (c != primary) accents.add(c);
        }

        short[] find = new short[accents.size()];
        short[] replace = new short[accents.size()];
        for (int i = 0; i < accents.size(); i++) {
            short hsl = accents.get(i);
            find[i] = hsl;
            replace[i] = accentColor(hsl, tier);
        }
        return new short[][]{find, replace};
    }

    static short accentColor(short hsl, String tier) {
        int h = (hsl >> 10) & 63;
        int s = (hsl >> 7) & 7;
        int l = hsl & 127;
        boolean veryDark = l <= 12; // likely an eye/void/outline colour, not a lit surface
        int nh, ns, nl;
        switch (tier) {
            case "Shadow":
                // Shift accents toward purple/void instead of just darkening the original hue.
                nh = 48;
                ns = 6;
                nl = veryDark ? clamp(l + 10, 12, 30) : clamp((int) Math.round(l * 0.5), 8, 45);
                break;
            case "Golden":
                nh = 8;
                ns = veryDark ? 5 : 6;
                nl = veryDark ? clamp(l + 20, 25, 55) : clamp((int) Math.round(l * 0.9 + 20), 30, 110);
                break;
            case "Blood":
                nh = 0;
                ns = 7;
                nl = veryDark ? clamp(l + 12, 15, 35) : clamp((int) Math.round(l * 0.65 + 12), 18, 60);
                break;
            default:
                throw new IllegalArgumentException("tier must be Shadow|Golden|Blood");
        }
        return (short) (((nh & 63) << 10) | ((ns & 7) << 7) | (nl & 127));
    }

    static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    // Removes any EXISTING opcode-40 (and opcode-41, if present) block from raw, then appends a
    // fresh opcode-40 block built from find/replace right before the terminator, in the same
    // position an original splice would have put it. Leaves everything else byte-identical.
    static byte[] replaceOpcode40(byte[] raw, String kind, short[] find, short[] replace) {
        byte[] stripped = stripOpcodes(raw, kind, 40, 41);
        int insertAt = SpliceRecolorCopy.findTerminatorOffset(stripped, kind);

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

        byte[] newRaw = new byte[stripped.length + blockBytes.length];
        System.arraycopy(stripped, 0, newRaw, 0, insertAt);
        System.arraycopy(blockBytes, 0, newRaw, insertAt, blockBytes.length);
        System.arraycopy(stripped, insertAt, newRaw, insertAt + blockBytes.length, stripped.length - insertAt);
        return newRaw;
    }

    static byte[] stripOpcodes(byte[] b, String kind, int... targetOpcodes) {
        InputStream is = new InputStream(b);
        List<int[]> spans = new ArrayList<>(); // [start, end)
        while (true) {
            int start = is.getOffset();
            int opcode = is.readUnsignedByte();
            if (opcode == 0) break;
            if (kind.equals("item")) {
                SpliceItemOption.skipOpcodePayload(opcode, is);
            } else {
                InsertNPCActions.skipOpcodePayload(opcode, is);
            }
            for (int target : targetOpcodes) {
                if (opcode == target) {
                    spans.add(new int[]{start, is.getOffset()});
                    break;
                }
            }
        }
        if (spans.isEmpty()) return b;

        byte[] out = new byte[b.length - spans.stream().mapToInt(sp -> sp[1] - sp[0]).sum()];
        int outPos = 0, inPos = 0;
        for (int[] span : spans) {
            System.arraycopy(b, inPos, out, outPos, span[0] - inPos);
            outPos += span[0] - inPos;
            inPos = span[1];
        }
        System.arraycopy(b, inPos, out, outPos, b.length - inPos);
        return out;
    }
}

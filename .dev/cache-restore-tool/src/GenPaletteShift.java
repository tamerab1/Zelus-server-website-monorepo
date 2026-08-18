import java.io.File;
import java.util.LinkedHashSet;
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
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;
import net.runelite.cache.models.JagexColor;

// Deterministically recolors every distinct face colour used by an item/npc's model(s) toward
// one of three fixed "tier" palettes (shadow/golden/infernal), and prints the resulting
// find/replace pair list in the exact format SpliceRecolorCopy expects. This exists because
// individual boss pets can have 10-30 distinct face colours -- hand-picking a replacement for
// each is not practical across ~65 bosses, so instead every colour is passed through the same
// deterministic hue/sat/lum transform for a given tier, preserving relative light/dark contrast.
// First-pass palettes only -- meant to be visually checked in-game per pet, same as every other
// recolor added to this project so far.
//
// Usage: <cachePath> <item|npc> <id> <shadow|golden|infernal>
public class GenPaletteShift {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        String kind = args[1];
        int targetId = Integer.parseInt(args[2]);
        String tier = args[3];

        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();

            int[] modelIds;
            if (kind.equals("item")) {
                Index index = store.getIndex(IndexType.CONFIGS);
                Archive archive = index.getArchive(ConfigType.ITEM.getId());
                byte[] decompressed = archive.decompress(storage.loadArchive(archive));
                FileData[] fileData = archive.getFileData();
                var fileContents = SpliceItemOption.splitChunks(decompressed, fileData.length);
                ItemLoader loader = new ItemLoader();
                ItemDefinition def = null;
                for (int i = 0; i < fileData.length; i++) {
                    if (fileData[i].getId() == targetId) {
                        def = loader.load(targetId, fileContents.get(i));
                    }
                }
                if (def == null) {
                    System.out.println("NOT FOUND");
                    return;
                }
                modelIds = new int[]{def.inventoryModel, def.maleModel0, def.maleModel1, def.maleModel2};
            } else {
                Index index = store.getIndex(IndexType.CONFIGS);
                Archive archive = index.getArchive(ConfigType.NPC.getId());
                byte[] decompressed = archive.decompress(storage.loadArchive(archive));
                FileData[] fileData = archive.getFileData();
                var fileContents = SpliceItemOption.splitChunks(decompressed, fileData.length);
                NpcLoader loader = new NpcLoader();
                NpcDefinition def = null;
                for (int i = 0; i < fileData.length; i++) {
                    if (fileData[i].getId() == targetId) {
                        def = loader.load(targetId, fileContents.get(i));
                    }
                }
                if (def == null) {
                    System.out.println("NOT FOUND");
                    return;
                }
                modelIds = def.models == null ? new int[0] : def.models;
            }

            Index models = store.getIndex(IndexType.MODELS);
            Set<Short> colors = new LinkedHashSet<>();
            for (int modelId : modelIds) {
                if (modelId <= 0) continue;
                Archive modelArchive = models.getArchive(modelId);
                if (modelArchive == null) continue;
                byte[] data = modelArchive.decompress(storage.loadArchive(modelArchive));
                ModelDefinition model = new ModelLoader().load(modelId, data);
                if (model.faceColors == null) continue;
                for (short c : model.faceColors) colors.add(c);
            }

            StringBuilder sb = new StringBuilder();
            boolean first = true;
            for (short hsl : colors) {
                int h = JagexColor.unpackHue(hsl);
                int s = JagexColor.unpackSaturation(hsl);
                int l = JagexColor.unpackLuminance(hsl);
                int nh, ns, nl;
                switch (tier) {
                    case "shadow":
                        nh = h;
                        ns = Math.min(s, 2);
                        nl = l <= 3 ? l : Math.max(3, (int) Math.round(l * 0.4));
                        break;
                    case "golden":
                        nh = 8;
                        ns = l <= 3 ? 3 : 6;
                        nl = l <= 3 ? Math.max(4, l + 3) : clamp((int) Math.round(l * 0.9 + 15), 20, 100);
                        break;
                    case "infernal":
                        nh = 0;
                        ns = 7;
                        nl = l <= 3 ? Math.max(2, l + 2) : clamp((int) Math.round(l * 0.6 + 10), 15, 55);
                        break;
                    default:
                        throw new IllegalArgumentException("tier must be shadow|golden|infernal");
                }
                short replace = JagexColor.packHSL(nh, ns, nl);
                if (!first) sb.append(",");
                sb.append(hsl).append(",").append(replace);
                first = false;
            }
            System.out.println(sb);
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}

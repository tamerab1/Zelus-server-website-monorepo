import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.NpcDefinition;
import net.runelite.cache.definitions.loaders.NpcLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;
import java.io.File;
import java.util.List;

public class DumpNpcFull {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Index index = store.getIndex(IndexType.CONFIGS);
            Archive archive = index.getArchive(ConfigType.NPC.getId());
            byte[] decompressed = archive.decompress(storage.loadArchive(archive));
            FileData[] fileData = archive.getFileData();
            List<byte[]> contents = SpliceItemOption.splitChunks(decompressed, fileData.length);
            NpcLoader loader = new NpcLoader();
            for (String idStr : args[1].split(",")) {
                int targetId = Integer.parseInt(idStr.trim());
                for (int i = 0; i < fileData.length; i++) {
                    if (fileData[i].getId() == targetId) {
                        NpcDefinition d = loader.load(targetId, contents.get(i));
                        System.out.println("=== " + targetId + " \"" + d.name + "\" ===");
                        System.out.println("  models=" + java.util.Arrays.toString(d.models));
                        System.out.println("  size=" + d.size + " widthScale=" + d.widthScale + " heightScale=" + d.heightScale);
                        System.out.println("  standingAnimation=" + d.standingAnimation + " walkingAnimation=" + d.walkingAnimation);
                        System.out.println("  recolorToFind=" + java.util.Arrays.toString(d.recolorToFind));
                        System.out.println("  recolorToReplace=" + java.util.Arrays.toString(d.recolorToReplace));
                        System.out.println("  retextureToFind=" + java.util.Arrays.toString(d.retextureToFind));
                        System.out.println("  isMinimapVisible=" + d.isMinimapVisible + " hasRenderPriority=" + d.hasRenderPriority);
                        System.out.println("  combatLevel=" + d.combatLevel + " isInteractable=" + d.isInteractable);
                        System.out.println("  actions=" + java.util.Arrays.toString(d.actions));
                    }
                }
            }
        }
    }
}

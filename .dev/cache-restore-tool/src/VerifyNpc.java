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
import java.util.Arrays;

public class VerifyNpc {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Index index = store.getIndex(IndexType.CONFIGS);
            Archive archive = index.getArchive(ConfigType.NPC.getId());
            byte[] decompressed = archive.decompress(storage.loadArchive(archive));
            FileData[] fileData = archive.getFileData();
            java.util.List<byte[]> fileContents = SpliceItemOption.splitChunks(decompressed, fileData.length);
            NpcLoader loader = new NpcLoader();
            for (int i = 1; i < args.length; i++) {
                int id = Integer.parseInt(args[i]);
                int slot = -1;
                for (int j = 0; j < fileData.length; j++) if (fileData[j].getId() == id) slot = j;
                NpcDefinition def = loader.load(id, fileContents.get(slot));
                System.out.println(id + " name=" + def.name + " combatLevel=" + def.combatLevel + " actions=" + Arrays.toString(def.actions) + " width=" + def.widthScale + " height=" + def.heightScale);
            }
        }
    }
}

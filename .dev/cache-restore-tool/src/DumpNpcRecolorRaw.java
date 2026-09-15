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

public class DumpNpcRecolorRaw {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        int npcId = Integer.parseInt(args[1]);
        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Index index = store.getIndex(IndexType.CONFIGS);
            Archive archive = index.getArchive(ConfigType.NPC.getId());
            byte[] decompressed = archive.decompress(store.getStorage().loadArchive(archive));
            FileData[] fileData = archive.getFileData();
            List<byte[]> fileContents = SpliceItemOption.splitChunks(decompressed, fileData.length);
            NpcLoader loader = new NpcLoader();
            NpcDefinition def = null;
            for (int i = 0; i < fileData.length; i++) {
                if (fileData[i].getId() == npcId) def = loader.load(npcId, fileContents.get(i));
            }
            if (def == null) { System.out.println("NOT FOUND"); return; }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < def.recolorToFind.length; i++) {
                if (i > 0) sb.append(",");
                sb.append(def.recolorToFind[i] & 0xFFFF).append(",").append(def.recolorToReplace[i] & 0xFFFF);
            }
            System.out.println(sb);
        }
    }
}

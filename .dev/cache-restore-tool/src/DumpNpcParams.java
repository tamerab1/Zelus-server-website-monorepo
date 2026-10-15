import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.NpcDefinition;
import net.runelite.cache.definitions.loaders.NpcLoader;
import net.runelite.cache.fs.*;
import net.runelite.cache.index.FileData;
import java.io.File;
import java.util.List;

public class DumpNpcParams {
    public static void main(String[] args) throws Exception {
        try (Store store = new Store(new File(args[0]))) {
            store.load();
            Archive archive = store.getIndex(IndexType.CONFIGS).getArchive(ConfigType.NPC.getId());
            byte[] dec = archive.decompress(store.getStorage().loadArchive(archive));
            FileData[] fd = archive.getFileData();
            List<byte[]> contents = SpliceItemOption.splitChunks(dec, fd.length);
            NpcLoader loader = new NpcLoader();
            for (String s : args[1].split(",")) {
                int id = Integer.parseInt(s.trim());
                for (int i = 0; i < fd.length; i++) if (fd[i].getId() == id) {
                    NpcDefinition d = loader.load(id, contents.get(i));
                    System.out.println(id + " \"" + d.name + "\" params=" + d.params);
                }
            }
        }
    }
}

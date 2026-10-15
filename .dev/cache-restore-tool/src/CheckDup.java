import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;
import java.io.File;
public class CheckDup {
    public static void main(String[] args) throws Exception {
        try (Store store = new Store(new File(args[0]))) {
            store.load();
            Index index = store.getIndex(IndexType.CONFIGS);
            Archive archive = index.getArchive(ConfigType.NPC.getId());
            FileData[] fd = archive.getFileData();
            int[] ids = {30560,30561,30562,30563,30564,30565,30566,30567};
            for (int id : ids) {
                int count = 0;
                for (FileData f : fd) if (f.getId() == id) count++;
                System.out.println("npc " + id + ": " + count + " slot(s)");
            }
        }
    }
}

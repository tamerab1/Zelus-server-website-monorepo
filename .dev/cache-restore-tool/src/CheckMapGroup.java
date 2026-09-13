import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;
import java.io.File;
public class CheckMapGroup {
    public static void main(String[] args) throws Exception {
        try (Store store = new Store(new File(args[0]))) {
            store.load();
            Index maps = store.getIndex(IndexType.MAPS);
            for (int i = 1; i < args.length; i++) {
                int groupId = Integer.parseInt(args[i]);
                Archive a = maps.getArchive(groupId);
                if (a == null) {
                    System.out.println("group " + groupId + ": NOT FOUND");
                    continue;
                }
                FileData[] fd = a.getFileData();
                StringBuilder sb = new StringBuilder();
                for (FileData f : fd) sb.append(f.getId()).append(",");
                System.out.println("group " + groupId + ": nameHash=" + a.getNameHash() + " subfiles=[" + sb + "]");
            }
        }
    }
}

import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;
import java.io.File;
import java.util.*;

public class ScanDupObjIds {
    public static void main(String[] args) throws Exception {
        try (Store store = new Store(new File(args[0]))) {
            store.load();
            Index index = store.getIndex(IndexType.CONFIGS);
            Archive archive = index.getArchive(ConfigType.OBJECT.getId());
            FileData[] fds = archive.getFileData();
            System.out.println("total: " + fds.length);
            Map<Integer, List<Integer>> byId = new TreeMap<>();
            for (int i = 0; i < fds.length; i++) {
                byId.computeIfAbsent(fds[i].getId(), k -> new ArrayList<>()).add(i);
            }
            int dupCount = 0;
            for (Map.Entry<Integer, List<Integer>> e : byId.entrySet()) {
                if (e.getValue().size() > 1) {
                    dupCount++;
                    if (dupCount <= 20) System.out.println("DUP id=" + e.getKey() + " at slots " + e.getValue());
                }
            }
            System.out.println("total distinct duplicated ids: " + dupCount);
            // check sortedness
            int outOfOrder = 0;
            for (int i = 1; i < fds.length; i++) {
                if (fds[i].getId() < fds[i-1].getId()) {
                    outOfOrder++;
                    if (outOfOrder <= 20) System.out.println("OUT OF ORDER at slot " + i + ": " + fds[i-1].getId() + " -> " + fds[i].getId());
                }
            }
            System.out.println("total out-of-order transitions: " + outOfOrder);
            // check near our target ids
            for (int i = 0; i < fds.length; i++) {
                int id = fds[i].getId();
                if (id > 62000 && id < 63000) System.out.println("near62388 slot=" + i + " id=" + id);
                if (id > 71000 && id < 73000) System.out.println("near72000 slot=" + i + " id=" + id);
                if (id > 139000 && id < 141000) System.out.println("near139780 slot=" + i + " id=" + id);
            }
        }
    }
}

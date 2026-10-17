import net.runelite.cache.IndexType;
import net.runelite.cache.fs.*;
import net.runelite.cache.index.FileData;
import java.io.File;
import java.util.*;

// Read-only. Which ids of a config group exist in [from, to], plus the group's max id. Usage: <cachePath> <group> <from> <to>
public class ListConfigIdsInRange {
    public static void main(String[] args) throws Exception {
        try (Store store = new Store(new File(args[0]))) {
            store.load();
            Archive a = store.getIndex(IndexType.CONFIGS).getArchive(Integer.parseInt(args[1]));
            int from = Integer.parseInt(args[2]), to = Integer.parseInt(args[3]), max = 0;
            TreeSet<Integer> used = new TreeSet<>();
            for (FileData f : a.getFileData()) { max = Math.max(max, f.getId()); if (f.getId() >= from && f.getId() <= to) used.add(f.getId()); }
            System.out.println("group " + args[1] + " max=" + max + " used in range: " + (used.size() > 40 ? used.size() + " ids, last " + used.last() : used));
        }
    }
}

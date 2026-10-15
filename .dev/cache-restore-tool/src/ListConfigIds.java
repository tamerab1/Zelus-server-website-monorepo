import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;
import java.io.File;

// Read-only: prints FileData ids (in archive order, with slot) within [lo, hi] for the item or npc
// config archive, plus any out-of-order transitions anywhere. Usage: <cachePath> <item|npc> <lo> <hi>
public class ListConfigIds {
    public static void main(String[] args) throws Exception {
        try (Store store = new Store(new File(args[0]))) {
            store.load();
            Archive a = store.getIndex(IndexType.CONFIGS).getArchive(args[1].equals("npc") ? ConfigType.NPC.getId() : ConfigType.ITEM.getId());
            store.getStorage().loadArchive(a);
            FileData[] fd = a.getFileData();
            int lo = Integer.parseInt(args[2]), hi = Integer.parseInt(args[3]);
            System.out.println("files=" + fd.length);
            for (int i = 0; i < fd.length; i++) {
                if (fd[i].getId() >= lo && fd[i].getId() <= hi) System.out.println("slot " + i + " id " + fd[i].getId());
                if (i > 0 && fd[i].getId() < fd[i - 1].getId()) System.out.println("OUT OF ORDER at slot " + i + ": " + fd[i - 1].getId() + " -> " + fd[i].getId());
            }
        }
    }
}

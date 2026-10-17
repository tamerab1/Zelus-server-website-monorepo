import net.runelite.cache.IndexType;
import net.runelite.cache.fs.*;
import java.io.File;

// Read-only. Which interface groups exist in [from, to]. Usage: <cachePath> <from> <to>
public class ListInterfaceIds {
    public static void main(String[] args) throws Exception {
        try (Store store = new Store(new File(args[0]))) {
            store.load();
            Index idx = store.getIndex(IndexType.INTERFACES);
            StringBuilder sb = new StringBuilder();
            for (int g = Integer.parseInt(args[1]); g <= Integer.parseInt(args[2]); g++)
                sb.append(g).append(idx.getArchive(g) != null ? ":used " : ":free ");
            System.out.println(sb);
        }
    }
}

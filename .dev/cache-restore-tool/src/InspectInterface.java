import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.InterfaceDefinition;
import net.runelite.cache.definitions.loaders.InterfaceLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.ArchiveFiles;
import net.runelite.cache.fs.FSFile;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;

import java.io.File;
import java.lang.reflect.Field;

// Dumps every decoded widget property (via RuneLite's own real InterfaceDefinition
// parser -- the same code path the client's Widget Inspector reads from) for one or
// more (groupId, childId) components, so they can be diff'd side by side without
// needing a live client session. Loads only the specific archives requested (not the
// whole interface index via InterfaceManager.load(), which throws on unrelated
// archive corruption elsewhere in this live cache). Usage:
//   java InspectInterface <cachePath> <group1:child1> [<group2:child2> ...]
public class InspectInterface {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Index index = store.getIndex(IndexType.INTERFACES);
            InterfaceLoader loader = new InterfaceLoader();

            for (int i = 1; i < args.length; i++) {
                String[] parts = args[i].split(":");
                int group = Integer.parseInt(parts[0]);
                int child = Integer.parseInt(parts[1]);
                System.out.println("========== interface " + group + " component " + child + " ==========");
                Archive archive = index.getArchive(group);
                if (archive == null) {
                    System.out.println("  <no archive for group " + group + ">");
                    continue;
                }
                byte[] archiveData = storage.loadArchive(archive);
                ArchiveFiles files = archive.getFiles(archiveData);
                FSFile match = null;
                for (FSFile f : files.getFiles()) {
                    if (f.getFileId() == child) {
                        match = f;
                        break;
                    }
                }
                if (match == null) {
                    System.out.println("  <no file " + child + " in archive " + group + ">");
                    continue;
                }
                int widgetId = (group << 16) + child;
                InterfaceDefinition def;
                try {
                    def = loader.load(widgetId, match.getContents());
                } catch (Exception e) {
                    System.out.println("  ERROR decoding: " + e);
                    continue;
                }
                dump(def);
                System.out.println();
            }
        }
    }

    static void dump(InterfaceDefinition def) throws Exception {
        for (Field f : InterfaceDefinition.class.getFields()) {
            Object val = f.get(def);
            String s;
            if (val == null) {
                s = "null";
            } else if (val.getClass().isArray()) {
                s = arrayToString(val);
            } else {
                s = val.toString();
            }
            System.out.println("  " + f.getName() + " = " + s);
        }
    }

    static String arrayToString(Object arr) {
        if (arr instanceof int[]) return java.util.Arrays.toString((int[]) arr);
        if (arr instanceof Object[]) {
            Object[] oa = (Object[]) arr;
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < oa.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append(oa[i] == null ? "null" : oa[i].toString());
            }
            return sb.append("]").toString();
        }
        return String.valueOf(arr);
    }
}

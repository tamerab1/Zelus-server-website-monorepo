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

// Dumps a quick summary (type, text, spriteId, actions) of every child component in
// one interface group, so a whole widget can be surveyed without knowing child ids
// up front. Usage: java DumpInterfaceGroup <cachePath> <groupId>
public class DumpInterfaceGroup {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        int group = Integer.parseInt(args[1]);
        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Index index = store.getIndex(IndexType.INTERFACES);
            InterfaceLoader loader = new InterfaceLoader();

            Archive archive = index.getArchive(group);
            if (archive == null) {
                System.out.println("<no archive for group " + group + ">");
                return;
            }
            byte[] archiveData = storage.loadArchive(archive);
            ArchiveFiles files = archive.getFiles(archiveData);
            System.out.println("group " + group + " has " + files.getFiles().size() + " components");
            for (FSFile f : files.getFiles()) {
                int child = f.getFileId();
                int widgetId = (group << 16) + child;
                InterfaceDefinition def;
                try {
                    def = loader.load(widgetId, f.getContents());
                } catch (Exception e) {
                    System.out.println("  [" + child + "] ERROR decoding: " + e);
                    continue;
                }
                StringBuilder sb = new StringBuilder();
                sb.append("  [").append(child).append("] type=").append(def.type);
                if (def.text != null && !def.text.isEmpty()) sb.append(" text=\"").append(def.text).append('"');
                if (def.spriteId != -1) sb.append(" spriteId=").append(def.spriteId);
                if (def.name != null && !def.name.isEmpty()) sb.append(" name=\"").append(def.name).append('"');
                if (def.actions != null) {
                    for (String a : def.actions) {
                        if (a != null && !a.isEmpty()) sb.append(" action=\"").append(a).append('"');
                    }
                }
                if (def.originalWidth != 0 || def.originalHeight != 0) sb.append(" w=").append(def.originalWidth).append(" h=").append(def.originalHeight);
                System.out.println(sb);
            }
        }
    }
}

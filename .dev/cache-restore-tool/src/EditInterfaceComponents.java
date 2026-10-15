import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.InterfaceDefinition;
import net.runelite.cache.definitions.loaders.InterfaceLoader;
import net.runelite.cache.fs.*;
import net.runelite.cache.index.IndexData;

import java.io.File;
import java.lang.reflect.Field;
import java.util.*;

// In-place edits of IF3 components of an existing interface group, in one write. Edits:
//   <comp>:x=<n>  <comp>:y=<n>  <comp>:w=<n>  <comp>:h=<n>  <comp>:sprite=<n>  <comp>:actions=<a|b|c>  <comp>:text=<s>
// Safety: every component of the group must re-encode byte-for-byte with BuildItemStatsInterface.encodeIf3
// before anything changes (so fields the encoder doesn't model can't be lost), and every edited component
// must decode back to exactly the intended definition.
// Usage: verify|apply <cachePath> <group> <edit> [<edit> ...]
public class EditInterfaceComponents {
    public static void main(String[] args) throws Exception {
        boolean apply = args[0].equals("apply");
        int group = Integer.parseInt(args[2]);
        try (Store store = new Store(new File(args[1]))) {
            store.load();
            Storage storage = store.getStorage();
            Index index = store.getIndex(IndexType.INTERFACES);
            Archive archive = index.getArchive(group);
            ArchiveFiles files = archive.getFiles(storage.loadArchive(archive));
            InterfaceLoader loader = new InterfaceLoader();
            Map<Integer, InterfaceDefinition> defs = new TreeMap<>();
            for (FSFile f : files.getFiles()) {
                InterfaceDefinition d = loader.load((group << 16) | f.getFileId(), f.getContents());
                if (!d.isIf3 || !Arrays.equals(BuildItemStatsInterface.encodeIf3(d), f.getContents()))
                    throw new IllegalStateException("component " + f.getFileId() + " does not re-encode exactly -- ABORTING");
                defs.put(f.getFileId(), d);
            }
            Set<Integer> touched = new TreeSet<>();
            for (int i = 3; i < args.length; i++) {
                String[] p = args[i].split(":", 2);
                int c = Integer.parseInt(p[0]);
                String[] kv = p[1].split("=", 2);
                InterfaceDefinition d = defs.get(c);
                if (d == null) throw new IllegalStateException("no component " + c);
                switch (kv[0]) {
                    case "x" -> d.originalX = Integer.parseInt(kv[1]);
                    case "y" -> d.originalY = Integer.parseInt(kv[1]);
                    case "w" -> d.originalWidth = Integer.parseInt(kv[1]);
                    case "h" -> d.originalHeight = Integer.parseInt(kv[1]);
                    case "sprite" -> d.spriteId = Integer.parseInt(kv[1]);
                    case "actions" -> d.actions = kv[1].split("\\|");
                    case "text" -> d.text = kv[1];
                    default -> throw new IllegalArgumentException(kv[0]);
                }
                touched.add(c);
            }
            for (int c : touched) {
                InterfaceDefinition d = defs.get(c);
                byte[] enc = BuildItemStatsInterface.encodeIf3(d);
                InterfaceDefinition back = loader.load(d.id, enc);
                for (Field f : InterfaceDefinition.class.getFields()) {
                    if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                    if (!Objects.deepEquals(f.get(back), f.get(d)))
                        throw new IllegalStateException("component " + c + ": " + f.getName() + " mismatch");
                }
                files.findFile(c).setContents(enc);
                System.out.println(group + ":" + c + " x=" + d.originalX + " y=" + d.originalY + " w=" + d.originalWidth + " h=" + d.originalHeight + " sprite=" + d.spriteId + " actions=" + Arrays.toString(d.actions));
            }
            if (!apply) {
                System.out.println("VERIFY OK -- nothing written.");
                return;
            }
            byte[] data = files.saveContents();
            Container container = new Container(archive.getCompression(), -1);
            container.compress(data, null);
            storage.store(index.getId(), group, container.data);
            archive.setCrc(container.crc);
            archive.setRevision(archive.getRevision() + 1);
            archive.setCompressedSize(container.data.length);
            archive.setDecompressedSize(data.length);
            IndexData indexData = index.toIndexData();
            Container ic = new Container(index.getCompression(), -1);
            ic.compress(indexData.writeIndexData(), null);
            storage.store(255, index.getId(), ic.data);
            index.setCrc(ic.crc);
            System.out.println("APPLY complete -- " + touched.size() + " components of " + group + ".");
        }
    }
}

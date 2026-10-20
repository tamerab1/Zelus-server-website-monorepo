import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.ItemDefinition;
import net.runelite.cache.definitions.loaders.ItemLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Container;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;
import net.runelite.cache.index.IndexData;

import java.io.File;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Objects;

// Sets an existing item's value (cost, opcode 12 + a 4-byte int) in place (2026-10-20: the Sunstone crystal, 750 ->
// 2000 so its high-alch value reads 1.2K). The item's alch values derive from this on the server (item_info.loadMisc)
// and in the client. Surgical: the 5-byte [12, old cost] run must occur exactly once in the record; every other field
// is compared before / after through the real ItemLoader, and nothing is written unless only cost changed.
// Usage: verify|apply <cachePath> <itemId> <newCost>
public class SetItemCost {
    public static void main(String[] args) throws Exception {
        boolean apply = args[0].equals("apply");
        int itemId = Integer.parseInt(args[2]), cost = Integer.parseInt(args[3]);
        try (Store store = new Store(new File(args[1]))) {
            store.load();
            Storage storage = store.getStorage();
            Index index = store.getIndex(IndexType.CONFIGS);
            Archive archive = index.getArchive(ConfigType.ITEM.getId());
            byte[] decompressed = archive.decompress(storage.loadArchive(archive));
            FileData[] fileData = archive.getFileData();
            List<byte[]> files = SpliceItemOption.splitChunks(decompressed, fileData.length);
            int slot = -1;
            for (int i = 0; i < fileData.length; i++) if (fileData[i].getId() == itemId) slot = i;
            if (slot == -1) throw new IllegalStateException("item " + itemId + " not found");

            ItemLoader loader = new ItemLoader();
            byte[] before = files.get(slot);
            ItemDefinition old = loader.load(itemId, before);
            byte[] pattern = {12, (byte) (old.cost >>> 24), (byte) (old.cost >>> 16), (byte) (old.cost >>> 8), (byte) old.cost};
            int at = -1, hits = 0;
            for (int i = 0; i + pattern.length <= before.length; i++) {
                boolean m = true;
                for (int k = 0; k < pattern.length && m; k++) m = before[i + k] == pattern[k];
                if (m) { at = i; hits++; }
            }
            if (hits != 1) throw new IllegalStateException("opcode 12 run found " + hits + " times in " + itemId + " (need exactly 1)");
            byte[] after = before.clone();
            after[at + 1] = (byte) (cost >>> 24);
            after[at + 2] = (byte) (cost >>> 16);
            after[at + 3] = (byte) (cost >>> 8);
            after[at + 4] = (byte) cost;
            ItemDefinition neu = loader.load(itemId, after);
            if (neu.cost != cost) throw new IllegalStateException("cost decodes as " + neu.cost);
            for (Field f : ItemDefinition.class.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) || f.getName().equals("cost")) continue;
                f.setAccessible(true);
                if (!Objects.deepEquals(f.get(old), f.get(neu)))
                    throw new IllegalStateException("field " + f.getName() + " changed: " + f.get(old) + " -> " + f.get(neu));
            }
            System.out.println(itemId + " \"" + old.name + "\": cost " + old.cost + " -> " + cost + " (high alch "
                    + old.cost * 3 / 5 + " -> " + cost * 3 / 5 + "), every other field unchanged");
            if (!apply) {
                System.out.println("VERIFY OK -- nothing written.");
                return;
            }
            files.set(slot, after);
            byte[] data = SpliceItemOption.joinChunks(files);
            Container container = new Container(archive.getCompression(), -1);
            container.compress(data, null);
            storage.store(index.getId(), archive.getArchiveId(), container.data);
            archive.setCrc(container.crc);
            archive.setRevision(archive.getRevision() + 1);
            archive.setCompressedSize(container.data.length);
            archive.setDecompressedSize(data.length);
            IndexData indexData = index.toIndexData();
            Container ic = new Container(index.getCompression(), -1);
            ic.compress(indexData.writeIndexData(), null);
            storage.store(255, index.getId(), ic.data);
            index.setCrc(ic.crc);
            System.out.println("APPLY complete -- archive revision " + archive.getRevision());
        }
    }
}

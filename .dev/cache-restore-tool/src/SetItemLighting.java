import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.ItemDefinition;
import net.runelite.cache.definitions.loaders.ItemLoader;
import net.runelite.cache.fs.*;
import net.runelite.cache.index.FileData;
import net.runelite.cache.index.IndexData;

import java.io.File;
import java.nio.file.Files;
import java.util.*;

// Item model lighting in place (2026-10-18, the dark Soulbound looks): opcode 113 = ambient, 114 = contrast (signed
// bytes; the inventory model is lit with ambient + 64 and contrast + 768). Raises how bright the item's own model is
// wherever it is drawn on its own (inventory icon, interface model widgets, the ground) -- worn gear is lit with the
// player. Every other decoded field must be unchanged. Spec lines:   item <id> <ambient> <contrast>
// Usage: verify|apply <cachePath> <specFile>
public class SetItemLighting {
    public static void main(String[] args) throws Exception {
        boolean apply = args[0].equals("apply");
        try (Store store = new Store(new File(args[1]))) {
            store.load();
            Storage storage = store.getStorage();
            Index configs = store.getIndex(IndexType.CONFIGS);
            Archive items = configs.getArchive(ConfigType.ITEM.getId());
            FileData[] fd = items.getFileData();
            List<byte[]> contents = SpliceItemOption.splitChunks(items.decompress(storage.loadArchive(items)), fd.length);
            ItemLoader loader = new ItemLoader();
            int done = 0;
            for (String line : Files.readAllLines(new File(args[2]).toPath())) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] p = line.split("\\s+");
                int id = Integer.parseInt(p[1]), amb = Integer.parseInt(p[2]), con = Integer.parseInt(p[3]);
                int slot = -1;
                for (int i = 0; i < fd.length; i++) if (fd[i].getId() == id) slot = i;
                if (slot < 0) throw new IllegalStateException("item " + id + " missing");
                ItemDefinition before = loader.load(id, contents.get(slot));
                byte[] w = contents.get(slot);
                w = SpliceNewPetItem.replaceOrInsertItemOpcode(w, 113, new byte[]{(byte) amb});
                w = SpliceNewPetItem.replaceOrInsertItemOpcode(w, 114, new byte[]{(byte) con});
                ItemDefinition after = loader.load(id, w);
                if (after.ambient != amb || after.contrast != con || !Objects.equals(after.name, before.name)
                        || after.inventoryModel != before.inventoryModel || after.zoom2d != before.zoom2d || after.maleModel0 != before.maleModel0)
                    throw new IllegalStateException("lighting round-trip failed for " + id);
                contents.set(slot, w);
                System.out.printf("item %d \"%s\": ambient %d -> %d, contrast %d -> %d%n", id, before.name, before.ambient, amb, before.contrast, con);
                done++;
            }
            if (!apply) {
                System.out.println("VERIFY OK -- nothing written.");
                return;
            }
            byte[] joined = SpliceItemOption.joinChunks(contents);
            Container c = new Container(items.getCompression(), -1);
            c.compress(joined, null);
            storage.store(configs.getId(), items.getArchiveId(), c.data);
            items.setCrc(c.crc);
            items.setRevision(items.getRevision() + 1);
            items.setCompressedSize(c.data.length);
            items.setDecompressedSize(joined.length);
            IndexData indexData = configs.toIndexData();
            Container ic = new Container(configs.getCompression(), -1);
            ic.compress(indexData.writeIndexData(), null);
            storage.store(255, configs.getId(), ic.data);
            configs.setCrc(ic.crc);
            System.out.println("APPLY complete -- " + done + " item(s).");
        }
    }
}

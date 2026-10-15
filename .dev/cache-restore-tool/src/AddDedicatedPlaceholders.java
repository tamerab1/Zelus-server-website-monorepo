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
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

// Gives existing items their own dedicated bank placeholder, in ONE archive write
// (see reference_bank_placeholder_chain). For each <itemId>:<newPlaceholderId>:
//   - new placeholder = <templatePlaceholderId>'s bytes with op148 repointed at itemId
//   - item's own op148 repointed at the new placeholder (every other field must stay identical)
// Usage: verify|apply <cachePath> <templatePlaceholderId> <itemId>:<newPlaceholderId> [...]
public class AddDedicatedPlaceholders {
    public static void main(String[] args) throws Exception {
        String mode = args[0];
        int templateId = Integer.parseInt(args[2]);
        try (Store store = new Store(new File(args[1]))) {
            store.load();
            Storage storage = store.getStorage();
            Index index = store.getIndex(IndexType.CONFIGS);
            Archive archive = index.getArchive(ConfigType.ITEM.getId());
            byte[] decompressed = archive.decompress(storage.loadArchive(archive));
            FileData[] fileData = archive.getFileData();
            List<byte[]> contents = SpliceItemOption.splitChunks(decompressed, fileData.length);
            ItemLoader loader = new ItemLoader();

            byte[] templateRaw = contents.get(CreateRecoloredItemSet.requireSlot(fileData, templateId));
            ItemDefinition template = loader.load(templateId, templateRaw);
            CreateRecoloredItemSet.check(template.placeholderTemplateId != -1 && template.inventoryModel == 0,
                    "template " + templateId + " is not a proper placeholder");

            TreeMap<Integer, byte[]> additions = new TreeMap<>();
            for (int a = 3; a < args.length; a++) {
                String[] p = args[a].split(":");
                int itemId = Integer.parseInt(p[0]), phId = Integer.parseInt(p[1]);
                CreateRecoloredItemSet.check(CreateRecoloredItemSet.slotOf(fileData, phId) == -1 && !additions.containsKey(phId),
                        "placeholder id " + phId + " already exists");

                byte[] phRaw = SpliceNewPetItem.replaceOrInsertItemOpcode(templateRaw, 148, SpliceNewPetItem.ushort(itemId));
                ItemDefinition ph = loader.load(phId, phRaw);
                CreateRecoloredItemSet.check(ph.placeholderId == itemId && ph.placeholderTemplateId == template.placeholderTemplateId
                        && ph.inventoryModel == 0, "new placeholder " + phId);
                additions.put(phId, phRaw);

                int slot = CreateRecoloredItemSet.requireSlot(fileData, itemId);
                byte[] oldRaw = contents.get(slot);
                byte[] newRaw = SpliceNewPetItem.replaceOrInsertItemOpcode(oldRaw, 148, SpliceNewPetItem.ushort(phId));
                ItemDefinition before = loader.load(itemId, oldRaw), after = loader.load(itemId, newRaw);
                CreateRecoloredItemSet.check(after.placeholderId == phId, "item " + itemId + " op148");
                int oldPh = before.placeholderId;
                after.placeholderId = before.placeholderId;
                CreateRecoloredItemSet.check(UpdateDefsInPlace.sameFields(before, after), "other fields changed on item " + itemId);
                contents.set(slot, newRaw);
                System.out.println(itemId + " \"" + before.name + "\": placeholder " + oldPh + " -> " + phId
                        + " (new, back-references " + itemId + ", template " + ph.placeholderTemplateId + ")");
            }

            if (mode.equals("verify")) {
                System.out.println("VERIFY OK -- nothing written.");
                return;
            }
            if (!mode.equals("apply")) throw new IllegalArgumentException("mode must be verify or apply");

            List<FileData> fdList = new ArrayList<>(java.util.Arrays.asList(fileData));
            for (var e : additions.entrySet()) {
                int pos = fdList.size();
                for (int i = 0; i < fdList.size(); i++) {
                    if (fdList.get(i).getId() > e.getKey()) { pos = i; break; }
                }
                FileData fd = new FileData();
                fd.setId(e.getKey());
                fd.setNameHash(-1);
                fdList.add(pos, fd);
                contents.add(pos, e.getValue());
            }
            archive.setFileData(fdList.toArray(new FileData[0]));

            byte[] newDecompressed = SpliceItemOption.joinChunks(contents);
            Container container = new Container(archive.getCompression(), -1);
            container.compress(newDecompressed, null);
            storage.store(index.getId(), archive.getArchiveId(), container.data);
            archive.setCrc(container.crc);
            archive.setRevision(archive.getRevision() + 1);
            archive.setCompressedSize(container.data.length);
            archive.setDecompressedSize(newDecompressed.length);

            IndexData indexData = index.toIndexData();
            Container idxContainer = new Container(index.getCompression(), -1);
            idxContainer.compress(indexData.writeIndexData(), null);
            storage.store(255, index.getId(), idxContainer.data);
            index.setCrc(idxContainer.crc);
            System.out.println("APPLY complete -- " + additions.size() + " placeholders added, archive revision " + archive.getRevision());
        }
    }
}

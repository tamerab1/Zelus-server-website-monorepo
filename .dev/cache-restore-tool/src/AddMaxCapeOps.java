import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.ItemDefinition;
import net.runelite.cache.definitions.loaders.ItemLoader;
import net.runelite.cache.fs.*;
import net.runelite.cache.index.FileData;
import net.runelite.cache.index.IndexData;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.*;

// Gives every infused max cape variant (MaxCape.MaxCapes newCapeId) the plain max cape's (13342)
// menu options. In this cache the variants only carried Wear/Drop (+ their own special op), so
// CapePerks.registerMaxCapeActions() had nothing to attach to -- item actions bind by the exact
// menu text in the cache. Inventory ops go into free (null) slots only; worn ops (params 451-458)
// are appended after the cape's own existing worn ops.
//
// Usage: <cachePath> [--dry]
public class AddMaxCapeOps {
    static final String[] WORN = {"Home", "Crafting Guild", "Guild Teleports", "Skilling Areas", "POH Portals", "Spellbook", "Features"};

    public static void main(String[] args) throws Exception {
        boolean dry = args.length > 1 && args[1].equals("--dry");
        // id -> {inventory slot -> text}
        Map<Integer, Map<Integer, String>> inv = new LinkedHashMap<>();
        for (int id : new int[]{13329, 13331, 21776, 13333, 21780, 13335, 21784, 21285})
            inv.put(id, Map.of(2, "Teleports", 3, "Features"));
        inv.put(13337, Map.of(2, "Teleports"));
        inv.put(21898, Map.of(2, "Teleports"));
        inv.put(28902, Map.of());
        inv.put(20760, Map.of());
        inv.put(33021, Map.of(3, "Teleports"));

        try (Store store = new Store(new File(args[0]))) {
            store.load();
            Storage storage = store.getStorage();
            Index index = store.getIndex(IndexType.CONFIGS);
            Archive archive = index.getArchive(ConfigType.ITEM.getId());
            byte[] dec = archive.decompress(storage.loadArchive(archive));
            FileData[] fd = archive.getFileData();
            List<byte[]> contents = SpliceItemOption.splitChunks(dec, fd.length);
            ItemLoader loader = new ItemLoader();

            int updated = 0;
            for (int i = 0; i < fd.length; i++) {
                int id = fd[i].getId();
                if (!inv.containsKey(id)) continue;
                byte[] work = contents.get(i);
                ItemDefinition before = loader.load(id, work);

                for (Map.Entry<Integer, String> e : inv.get(id).entrySet()) {
                    if (before.interfaceOptions[e.getKey()] != null)
                        throw new IllegalStateException(id + ": inventory slot " + e.getKey() + " not free -- ABORTING");
                    work = SpliceNewPetItem.replaceOrInsertItemOpcode(work, 35 + e.getKey(), cstr(e.getValue()));
                }

                Map<Integer, Object> params = new TreeMap<>(before.params == null ? Map.of() : before.params);
                int next = 451;
                while (params.containsKey(next)) next++;
                List<String> worn = new ArrayList<>(Arrays.asList(WORN));
                if (next + worn.size() - 1 > 458) worn.remove("Crafting Guild"); // also inside Guild Teleports
                if (next + worn.size() - 1 > 458) throw new IllegalStateException(id + ": too many worn ops -- ABORTING");
                for (String w : worn) params.put(next++, w);
                work = SpliceNewPetItem.replaceOrInsertItemOpcode(work, 249, encodeParams(params));

                ItemDefinition after = loader.load(id, work);
                if (!Objects.equals(before.name, after.name) || before.inventoryModel != after.inventoryModel
                        || before.maleModel0 != after.maleModel0 || before.femaleModel0 != after.femaleModel0
                        || before.zoom2d != after.zoom2d || before.cost != after.cost || before.wearPos1 != after.wearPos1
                        || before.placeholderId != after.placeholderId || before.notedID != after.notedID
                        || !params.equals(new TreeMap<>(after.params)))
                    throw new IllegalStateException(id + ": verification failed -- ABORTING, nothing written");
                for (int s = 0; s < 5; s++) {
                    String want = inv.get(id).getOrDefault(s, before.interfaceOptions[s]);
                    if (!Objects.equals(want, after.interfaceOptions[s]))
                        throw new IllegalStateException(id + ": inventory slot " + s + " wrong -- ABORTING");
                }
                List<String> wornAfter = new ArrayList<>();
                for (int k = 451; k <= 458; k++) if (after.params.containsKey(k)) wornAfter.add(String.valueOf(after.params.get(k)));
                System.out.println(id + " \"" + after.name + "\" inv=" + Arrays.toString(after.interfaceOptions) + " worn=" + wornAfter);
                contents.set(i, work);
                updated++;
            }
            if (updated != inv.size()) throw new IllegalStateException("only found " + updated + "/" + inv.size() + " -- ABORTING");
            if (dry) { System.out.println("dry run, nothing written"); return; }

            byte[] newDec = SpliceItemOption.joinChunks(contents);
            Container c = new Container(archive.getCompression(), -1);
            c.compress(newDec, null);
            storage.store(index.getId(), archive.getArchiveId(), c.data);
            archive.setCrc(c.crc);
            archive.setRevision(archive.getRevision() + 1);
            archive.setCompressedSize(c.data.length);
            archive.setDecompressedSize(newDec.length);
            IndexData indexData = index.toIndexData();
            Container ic = new Container(index.getCompression(), -1);
            ic.compress(indexData.writeIndexData(), null);
            storage.store(255, index.getId(), ic.data);
            index.setCrc(ic.crc);
            System.out.println("Wrote " + updated + " item def(s). Archive revision now " + archive.getRevision());
        }
    }

    static byte[] cstr(String s) {
        byte[] b = s.getBytes(StandardCharsets.ISO_8859_1);
        return Arrays.copyOf(b, b.length + 1);
    }

    static byte[] encodeParams(Map<Integer, Object> params) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.write(params.size());
        for (Map.Entry<Integer, Object> e : params.entrySet()) {
            int k = e.getKey();
            boolean str = e.getValue() instanceof String;
            o.write(str ? 1 : 0);
            o.write(k >> 16); o.write(k >> 8); o.write(k);
            if (str) o.writeBytes(cstr((String) e.getValue()));
            else { int v = (Integer) e.getValue(); o.write(v >> 24); o.write(v >> 16); o.write(v >> 8); o.write(v); }
        }
        return o.toByteArray();
    }
}

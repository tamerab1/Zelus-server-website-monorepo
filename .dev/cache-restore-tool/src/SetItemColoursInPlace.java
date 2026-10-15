import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.ItemDefinition;
import net.runelite.cache.definitions.loaders.ItemLoader;
import net.runelite.cache.fs.*;
import net.runelite.cache.index.FileData;
import net.runelite.cache.index.IndexData;
import net.runelite.cache.io.OutputStream;

import java.io.File;
import java.nio.file.Files;
import java.util.*;

// Replaces the recolour (op40) and/or retexture (op41) table of EXISTING items in place (same ids, every file slot
// with the id), all in one archive write. Written for the Fire cape themes (2026-10-15, fire_cape_themes.txt):
// 59983-59985 had only a trim recolour and kept the orange lava texture 40.
//   item <id> recolor <find>:<replace>,...     (16-bit HSL)
//   item <id> retexture <find>:<replace>,...   (texture ids)
// Everything but those two tables (name, models, ops) must decode identical before anything is written.
// Usage: verify|apply <cachePath> <spec>
public class SetItemColoursInPlace {
    public static void main(String[] args) throws Exception {
        boolean apply = args[0].equals("apply");
        Map<Integer, Map<String, int[][]>> spec = new LinkedHashMap<>();
        for (String line : Files.readAllLines(new File(args[2]).toPath())) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#"))
                continue;
            String[] p = line.split("\\s+");
            if (p.length != 4 || !p[0].equals("item") || !(p[2].equals("recolor") || p[2].equals("retexture")))
                throw new IllegalArgumentException("bad line: " + line);
            String[] pairs = p[3].split(",");
            int[][] map = new int[pairs.length][2];
            for (int i = 0; i < pairs.length; i++) {
                String[] fr = pairs[i].split(":");
                map[i][0] = Integer.parseInt(fr[0]);
                map[i][1] = Integer.parseInt(fr[1]);
            }
            spec.computeIfAbsent(Integer.parseInt(p[1]), k -> new LinkedHashMap<>()).put(p[2], map);
        }
        try (Store store = new Store(new File(args[1]))) {
            store.load();
            Storage storage = store.getStorage();
            Index index = store.getIndex(IndexType.CONFIGS);
            Archive archive = index.getArchive(ConfigType.ITEM.getId());
            byte[] dec = archive.decompress(storage.loadArchive(archive));
            FileData[] fd = archive.getFileData();
            List<byte[]> files = SpliceItemOption.splitChunks(dec, fd.length);
            ItemLoader loader = new ItemLoader();
            for (var e : spec.entrySet()) {
                int id = e.getKey();
                int edited = 0;
                for (int slot = 0; slot < fd.length; slot++) {
                    if (fd[slot].getId() != id)
                        continue;
                    byte[] work = files.get(slot);
                    ItemDefinition before = loader.load(id, work);
                    for (var t : e.getValue().entrySet()) {
                        int[][] map = t.getValue();
                        OutputStream block = new OutputStream();
                        block.writeByte(map.length);
                        for (int[] fr : map) {
                            block.writeShort(fr[0] & 0xffff);
                            block.writeShort(fr[1] & 0xffff);
                        }
                        work = SpliceNewPetItem.replaceOrInsertItemOpcode(work, t.getKey().equals("recolor") ? 40 : 41, block.flip());
                    }
                    ItemDefinition after = loader.load(id, work);
                    if (!Objects.equals(before.name, after.name) || before.inventoryModel != after.inventoryModel
                            || before.maleModel0 != after.maleModel0 || before.femaleModel0 != after.femaleModel0
                            || !Arrays.equals(before.interfaceOptions, after.interfaceOptions)
                            || before.zoom2d != after.zoom2d || before.placeholderId != after.placeholderId)
                        throw new IllegalStateException(id + ": other fields changed -- ABORTING");
                    check(id, "recolor", e.getValue().get("recolor"), after.colorFind, after.colorReplace);
                    check(id, "retexture", e.getValue().get("retexture"), after.textureFind, after.textureReplace);
                    System.out.println(id + " \"" + after.name + "\" slot " + slot + ": colours " + Arrays.toString(after.colorFind)
                            + " -> " + Arrays.toString(after.colorReplace) + ", textures " + Arrays.toString(after.textureFind)
                            + " -> " + Arrays.toString(after.textureReplace));
                    files.set(slot, work);
                    edited++;
                }
                if (edited == 0)
                    throw new IllegalStateException("item " + id + " not found");
            }
            if (!apply) {
                System.out.println("VERIFY OK -- nothing written.");
                return;
            }
            byte[] joined = SpliceItemOption.joinChunks(files);
            Container c = new Container(archive.getCompression(), -1);
            c.compress(joined, null);
            storage.store(index.getId(), archive.getArchiveId(), c.data);
            archive.setCrc(c.crc);
            archive.setRevision(archive.getRevision() + 1);
            archive.setCompressedSize(c.data.length);
            archive.setDecompressedSize(joined.length);
            IndexData indexData = index.toIndexData();
            Container ic = new Container(index.getCompression(), -1);
            ic.compress(indexData.writeIndexData(), null);
            storage.store(255, index.getId(), ic.data);
            index.setCrc(ic.crc);
            System.out.println("APPLY complete. Item archive revision now " + archive.getRevision());
        }
    }

    static void check(int id, String what, int[][] want, short[] find, short[] replace) {
        if (want == null)
            return;
        if (find == null || find.length != want.length)
            throw new IllegalStateException(id + ": " + what + " not applied -- ABORTING");
        for (int i = 0; i < want.length; i++)
            if ((find[i] & 0xffff) != want[i][0] || (replace[i] & 0xffff) != want[i][1])
                throw new IllegalStateException(id + ": " + what + " pair " + i + " did not round-trip -- ABORTING");
    }
}

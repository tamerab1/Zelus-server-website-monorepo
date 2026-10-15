import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.loaders.ModelLoader;
import net.runelite.cache.fs.*;
import net.runelite.cache.index.FileData;
import net.runelite.cache.index.IndexData;

import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.util.zip.CRC32;

// Batch version of InsertRawModel: inserts every <modelId>.dat in a directory as a new
// single-file model archive (uncompressed, file id 0 -- see InsertRawModel for why fd.id must be 0)
// and rewrites the MODELS index once at the end. Refuses any id that already exists and checks
// every file decodes before writing anything.
// replace-verify|replace: the opposite -- every id must ALREADY exist; its archive is overwritten in place with a
// bumped revision (clients refetch it through the crc), e.g. RemapTextureUVs output.
// Usage: verify|apply|replace-verify|replace <cachePath> <dir>
public class InsertRawModels {
    public static void main(String[] args) throws Exception {
        String mode = args[0];
        boolean replace = mode.startsWith("replace");
        File[] files = new File(args[2]).listFiles((d, n) -> n.matches("\\d+\\.dat"));
        Arrays.sort(files);
        try (Store store = new Store(new File(args[1]))) {
            store.load();
            Storage storage = store.getStorage();
            Index models = store.getIndex(IndexType.MODELS);
            Map<Integer, byte[]> todo = new TreeMap<>();
            for (File f : files) {
                int id = Integer.parseInt(f.getName().replace(".dat", ""));
                if (id <= 0 || id >= 65536) throw new IllegalStateException("model id out of u16 range: " + id);
                if (!replace && models.getArchive(id) != null) throw new IllegalStateException("model " + id + " already exists");
                if (replace && models.getArchive(id) == null) throw new IllegalStateException("model " + id + " does not exist (replace)");
                byte[] raw = Files.readAllBytes(f.toPath());
                ModelDefinition d = new ModelLoader().load(id, raw.clone());
                if (d.faceCount <= 0 || d.vertexCount <= 0) throw new IllegalStateException("model " + id + " decodes empty");
                todo.put(id, raw);
            }
            System.out.println(todo.size() + " models ready: " + todo.keySet());
            if (mode.equals("verify") || mode.equals("replace-verify")) {
                System.out.println("VERIFY OK -- nothing written.");
                return;
            }
            if (!mode.equals("apply") && !mode.equals("replace")) throw new IllegalArgumentException("mode must be verify, apply, replace-verify or replace");
            for (Map.Entry<Integer, byte[]> e : todo.entrySet()) {
                Archive a;
                if (replace) {
                    a = models.getArchive(e.getKey());
                    a.setRevision(a.getRevision() + 1);
                } else {
                    a = models.addArchive(e.getKey());
                    a.setNameHash(-1);
                    a.setRevision(1);
                }
                a.setCompression(0);
                Container c = new Container(0, -1);
                c.compress(e.getValue(), null);
                CRC32 crc = new CRC32();
                crc.update(c.data);
                storage.store(models.getId(), e.getKey(), c.data);
                a.setCrc((int) crc.getValue());
                a.setCompressedSize(c.data.length);
                a.setDecompressedSize(e.getValue().length);
                FileData fd = new FileData();
                fd.setId(0);
                a.setFileData(new FileData[]{fd});
            }
            IndexData indexData = models.toIndexData();
            Container ic = new Container(models.getCompression(), -1);
            ic.compress(indexData.writeIndexData(), null);
            storage.store(255, models.getId(), ic.data);
            CRC32 icrc = new CRC32();
            icrc.update(ic.data);
            models.setCrc((int) icrc.getValue());
            System.out.println((replace ? "REPLACE" : "APPLY") + " complete -- " + todo.size() + " models written.");
        }
    }
}

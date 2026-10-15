import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.ObjectDefinition;
import net.runelite.cache.definitions.loaders.ModelLoader;
import net.runelite.cache.definitions.loaders.ObjectLoader;
import net.runelite.cache.fs.*;
import net.runelite.cache.index.FileData;
import net.runelite.cache.index.IndexData;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.util.*;

// Writes an explicit recolour (opcode 40) onto existing objects, from a spec file, in place (same ids, so map
// placements and server actions keep working). Written for the Zelus home reskin (2026-10-15, zelus_home_reskin.txt):
// Ornate pool of Rejuvenation 29241 and Portal Nexus 33410.
//   object <id> <find>:<replace>,<find>:<replace>,...      (packed 16-bit HSL face colours)
// Guards: every find colour must exist on the object's models; the object must have no recolour yet (rebuild mode
// replaces one we wrote); no replacement may equal a find colour (the client applies pairs one after another, so that
// would chain). Applies to every file slot with the id (the object archive has duplicate ids).
// Usage: verify|apply|rebuild <cachePath> <spec>
public class RecolorObjectSpec {
    public static void main(String[] args) throws Exception {
        boolean rebuild = args[0].equals("rebuild");
        boolean apply = args[0].equals("apply") || rebuild;
        Map<Integer, int[][]> spec = new LinkedHashMap<>();
        for (String line : Files.readAllLines(new File(args[2]).toPath())) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#"))
                continue;
            String[] p = line.split("\\s+");
            if (!p[0].equals("object") || p.length != 3)
                throw new IllegalArgumentException("bad line: " + line);
            String[] pairs = p[2].split(",");
            int[][] map = new int[pairs.length][2];
            for (int i = 0; i < pairs.length; i++) {
                String[] fr = pairs[i].split(":");
                map[i][0] = Integer.parseInt(fr[0]);
                map[i][1] = Integer.parseInt(fr[1]);
            }
            spec.put(Integer.parseInt(p[1]), map);
        }
        try (Store store = new Store(new File(args[1]))) {
            store.load();
            Storage storage = store.getStorage();
            Index configs = store.getIndex(IndexType.CONFIGS);
            Archive archive = configs.getArchive(ConfigType.OBJECT.getId());
            byte[] dec = archive.decompress(storage.loadArchive(archive));
            FileData[] fd = archive.getFileData();
            List<byte[]> contents = SpliceItemOption.splitChunks(dec, fd.length);
            ObjectLoader loader = new ObjectLoader();
            for (Map.Entry<Integer, int[][]> e : spec.entrySet()) {
                int objId = e.getKey();
                int[][] map = e.getValue();
                Set<Integer> finds = new HashSet<>();
                for (int[] fr : map)
                    if (!finds.add(fr[0]))
                        throw new IllegalStateException(objId + ": colour " + fr[0] + " listed twice");
                for (int[] fr : map)
                    if (finds.contains(fr[1]))
                        throw new IllegalStateException(objId + ": replacement " + fr[1] + " is also a find colour (would chain)");
                int edited = 0;
                for (int slot = 0; slot < fd.length; slot++) {
                    if (fd[slot].getId() != objId)
                        continue;
                    byte[] raw = contents.get(slot);
                    ObjectDefinition before = loader.load(objId, raw);
                    if (before.getRecolorToFind() != null && !rebuild)
                        throw new IllegalStateException(objId + " (slot " + slot + ") already has a recolour -- use rebuild");
                    Set<Integer> present = new TreeSet<>();
                    for (int modelId : before.getObjectModels()) {
                        Archive ma = store.getIndex(IndexType.MODELS).getArchive(modelId);
                        ModelDefinition m = new ModelLoader().load(modelId, ma.decompress(storage.loadArchive(ma)));
                        for (short c : m.faceColors)
                            present.add(c & 0xffff);
                    }
                    for (int f : finds)
                        if (!present.contains(f))
                            throw new IllegalStateException(objId + ": find colour " + f + " is not on its models " + present);
                    ByteArrayOutputStream p = new ByteArrayOutputStream();
                    p.write(map.length);
                    for (int[] fr : map) {
                        p.write(fr[0] >> 8);
                        p.write(fr[0]);
                        p.write(fr[1] >> 8);
                        p.write(fr[1]);
                    }
                    byte[] work = SpliceNewObject.replaceOpcode(raw, 40, p.toByteArray());
                    ObjectDefinition after = loader.load(objId, work);
                    if (after.getRecolorToFind() == null || after.getRecolorToFind().length != map.length
                            || !Arrays.equals(before.getObjectModels(), after.getObjectModels())
                            || !Arrays.equals(before.getActions(), after.getActions())
                            || !Objects.equals(before.getName(), after.getName())
                            || before.getAnimationID() != after.getAnimationID())
                        throw new IllegalStateException(objId + ": verification failed -- ABORTING, nothing written");
                    for (int i = 0; i < map.length; i++)
                        if ((after.getRecolorToFind()[i] & 0xffff) != map[i][0] || (after.getRecolorToReplace()[i] & 0xffff) != map[i][1])
                            throw new IllegalStateException(objId + ": pair " + i + " did not round-trip");
                    contents.set(slot, work);
                    edited++;
                    System.out.println(objId + " slot " + slot + " \"" + after.getName() + "\": " + map.length + " colour pairs OK");
                }
                if (edited == 0)
                    throw new IllegalStateException("object " + objId + " not found");
            }
            if (!apply) {
                System.out.println("VERIFY OK -- nothing written.");
                return;
            }
            byte[] newDec = SpliceItemOption.joinChunks(contents);
            Container c = new Container(archive.getCompression(), -1);
            c.compress(newDec, null);
            storage.store(configs.getId(), archive.getArchiveId(), c.data);
            archive.setCrc(c.crc);
            archive.setRevision(archive.getRevision() + 1);
            archive.setCompressedSize(c.data.length);
            archive.setDecompressedSize(newDec.length);
            IndexData indexData = configs.toIndexData();
            Container ic = new Container(configs.getCompression(), -1);
            ic.compress(indexData.writeIndexData(), null);
            storage.store(255, configs.getId(), ic.data);
            configs.setCrc(ic.crc);
            System.out.println("APPLY complete. Object archive revision now " + archive.getRevision());
        }
    }
}

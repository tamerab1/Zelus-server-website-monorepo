import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.ObjectDefinition;
import net.runelite.cache.definitions.loaders.ObjectLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;

import java.io.File;
import java.nio.file.Files;
import java.util.*;

// New decorative objects from ripped models (2026-10-18, the "new customs" batch: Discord Statue, Donator Platform).
// Each is a clone of a template object with its model list (opcode 5 AND 1 -- see the opcode-5-wins pitfall), recolours
// (40/41) and actions (30-34) dropped, then: model (opcode 1, type 10), name, size (14/15), solid or walk-through
// (17 = not solid, 18 = not projectile-blocking) and an optional action 1. Sorted insert (the archive must stay in
// ascending id order; it has a pre-existing duplicate id, so the check is <=). Ids must stay below 65536 (spawn packet).
// Spec lines:   object <templateId> <newId> <modelId> <sizeX> <sizeY> <solid|walk> <action|-> <name...>
// Usage: verify|apply <cachePath> <specFile>
public class CreateDecorObjects {
    public static void main(String[] args) throws Exception {
        boolean apply = args[0].equals("apply");
        List<String[]> spec = new ArrayList<>();
        for (String line : Files.readAllLines(new File(args[2]).toPath())) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            spec.add(line.split("\\s+", 9));
        }
        try (Store store = new Store(new File(args[1]))) {
            store.load();
            Storage storage = store.getStorage();
            Index configs = store.getIndex(IndexType.CONFIGS);
            Archive arch = configs.getArchive(ConfigType.OBJECT.getId());
            FileData[] fd = arch.getFileData();
            List<byte[]> contents = SpliceItemOption.splitChunks(arch.decompress(storage.loadArchive(arch)), fd.length);
            List<FileData> ofd = new ArrayList<>(Arrays.asList(fd));
            ObjectLoader loader = new ObjectLoader();
            for (String[] s : spec) {
                int template = Integer.parseInt(s[1]), id = Integer.parseInt(s[2]), model = Integer.parseInt(s[3]);
                int sx = Integer.parseInt(s[4]), sy = Integer.parseInt(s[5]);
                boolean solid = s[6].equals("solid");
                String action = s[7].equals("-") ? null : s[7].replace('_', ' ');
                String name = s[8];
                BuildChampionshipBoard.check(id < 65536, "object " + id + " must be below 65536");
                BuildChampionshipBoard.check(store.getIndex(IndexType.MODELS).getArchive(model) != null, "model " + model + " missing");
                int src = -1;
                for (int k = 0; k < ofd.size(); k++) {
                    BuildChampionshipBoard.check(ofd.get(k).getId() != id, "object " + id + " already exists");
                    if (ofd.get(k).getId() == template) src = k;
                }
                BuildChampionshipBoard.check(src != -1, "template " + template + " not found");
                byte[] w = contents.get(src);
                for (int op : new int[]{5, 40, 41, 30, 31, 32, 33, 34, 17, 18, 14, 15})
                    w = SpliceNewObject.removeOpcode(w, op);
                w = SpliceNewObject.replaceOpcode(w, 1, new byte[]{1, (byte) (model >> 8), (byte) model, 10});
                w = SpliceNewObject.replaceOpcode(w, 2, SpliceNewObject.withTerminator(name));
                w = SpliceNewObject.replaceOpcode(w, 14, new byte[]{(byte) sx});
                w = SpliceNewObject.replaceOpcode(w, 15, new byte[]{(byte) sy});
                if (!solid) {
                    w = SpliceNewObject.replaceOpcode(w, 17, new byte[0]);
                    w = SpliceNewObject.replaceOpcode(w, 18, new byte[0]);
                }
                if (action != null)
                    w = SpliceNewObject.replaceOpcode(w, 30, SpliceNewObject.withTerminator(action));
                ObjectDefinition d = loader.load(id, w);
                BuildChampionshipBoard.check(name.equals(d.getName()) && Arrays.equals(d.getObjectModels(), new int[]{model})
                        && Arrays.equals(d.getObjectTypes(), new int[]{10}) && d.getSizeX() == sx && d.getSizeY() == sy
                        && (solid ? d.getInteractType() != 0 : d.getInteractType() == 0)
                        && Objects.equals(action, d.getActions()[0]) && d.getRecolorToFind() == null, "object " + id);
                System.out.printf("object %d \"%s\" model %d %dx%d %s action=%s (template %d)%n", id, name, model, sx, sy,
                        solid ? "solid" : "walk-through", action, template);
                int at = 0;
                while (at < ofd.size() && ofd.get(at).getId() < id)
                    at++;
                FileData nf = new FileData();
                nf.setId(id);
                nf.setNameHash(-1);
                ofd.add(at, nf);
                contents.add(at, w);
            }
            for (int i = 1; i < ofd.size(); i++)
                BuildChampionshipBoard.check(ofd.get(i - 1).getId() <= ofd.get(i).getId(), "object archive out of order at " + i);
            if (!apply) {
                System.out.println("VERIFY OK -- nothing written.");
                return;
            }
            arch.setFileData(ofd.toArray(new FileData[0]));
            BuildChampionshipBoard.rewrite(storage, configs, arch, SpliceItemOption.joinChunks(contents));
            BuildChampionshipBoard.storeIndex(storage, configs);
            System.out.println("APPLY complete -- " + spec.size() + " object(s).");
        }
    }
}

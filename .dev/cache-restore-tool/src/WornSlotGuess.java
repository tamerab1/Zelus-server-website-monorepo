import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.ItemDefinition;
import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.loaders.ItemLoader;
import net.runelite.cache.definitions.loaders.ModelLoader;
import net.runelite.cache.fs.*;
import net.runelite.cache.index.FileData;
import java.io.File;
import java.nio.file.Files;
import java.util.*;

// Read-only (2026-10-20, armor batch): guesses the equip slot of ripped worn models from their player-skeleton vertex
// labels -- each model's label histogram is compared (weighted overlap) with the male worn models of real items of known
// slots. Inventory models (no vertex skins) are paired to worn ones of the same vertex count.
// Usage: WornSlotGuess <cache> <folder>...
public class WornSlotGuess {
    static final Object[][] REFS = {
        {"head", new int[]{26382, 21018, 12931, 10828}},   // torva helm, (bandos helm? skip), ancestral hat, serp, neitiznot
        {"body", new int[]{26384, 11832, 21021, 4720, 11828}},          // torva body, bandos chest, ancestral top, dh body, arma top
        {"legs", new int[]{26386, 11834, 21024, 4722, 11830}},          // torva legs, tassets, ancestral bottom, dh legs, arma skirt
        {"hands", new int[]{7462, 22981, 19544}},                       // barrows gloves, ferocious, tormented
        {"feet", new int[]{11840, 13239, 21733}},                       // d boots, primordial, guardian
        {"cape", new int[]{6570, 21295, 13280}},                        // fire cape, infernal, max cape
        {"weapon", new int[]{4151, 21006, 11802, 12926, 20997, 22325}}, // whip, kodai, ags, blowpipe, tbow, scythe
        {"shield", new int[]{12954, 12817, 21000}},                     // defender, elysian, twisted buckler
    };

    public static void main(String[] a) throws Exception {
        Map<String, List<double[]>> ref = new LinkedHashMap<>();
        try (Store store = new Store(new File(a[0]))) {
            store.load();
            Storage storage = store.getStorage();
            Index models = store.getIndex(IndexType.MODELS);
            Archive archive = store.getIndex(IndexType.CONFIGS).getArchive(ConfigType.ITEM.getId());
            FileData[] fd = archive.getFileData();
            List<byte[]> contents = SpliceItemOption.splitChunks(archive.decompress(storage.loadArchive(archive)), fd.length);
            Map<Integer, byte[]> byId = new HashMap<>();
            for (int i = 0; i < fd.length; i++) byId.put(fd[i].getId(), contents.get(i));
            for (Object[] r : REFS) {
                List<double[]> hs = new ArrayList<>();
                for (int item : (int[]) r[1]) {
                    if (!byId.containsKey(item)) continue;
                    ItemDefinition d = new ItemLoader().load(item, byId.get(item));
                    for (int mid : new int[]{d.maleModel0, d.maleModel1}) {
                        if (mid <= 0 || models.getArchive(mid) == null) continue;
                        Archive ma = models.getArchive(mid);
                        ModelDefinition m = new ModelLoader().load(mid, ma.decompress(storage.loadArchive(ma)));
                        double[] h = hist(m);
                        if (h != null) hs.add(h);
                    }
                }
                ref.put((String) r[0], hs);
            }
        }
        for (int k = 1; k < a.length; k++) {
            File dir = new File(a[k]);
            System.out.println("=== " + dir.getName());
            File[] files = dir.listFiles((x, n) -> n.endsWith(".dat"));
            Arrays.sort(files);
            Map<Integer, List<String>> byVerts = new TreeMap<>();
            List<Object[]> worn = new ArrayList<>();
            for (File f : files) {
                ModelDefinition m = new ModelLoader().load(0, Files.readAllBytes(f.toPath()));
                byVerts.computeIfAbsent(m.vertexCount, x -> new ArrayList<>()).add(f.getName());
                double[] h = hist(m);
                if (h == null) continue;
                List<String> ranked = new ArrayList<>();
                List<double[]> sc = new ArrayList<>();
                List<String> names = new ArrayList<>(ref.keySet());
                for (int s = 0; s < names.size(); s++) {
                    double best = 0;
                    for (double[] r : ref.get(names.get(s))) best = Math.max(best, overlap(h, r));
                    sc.add(new double[]{best, s});
                }
                sc.sort((x, y) -> Double.compare(y[0], x[0]));
                for (int s = 0; s < 3; s++) ranked.add(String.format("%s %.2f", names.get((int) sc.get(s)[1]), sc.get(s)[0]));
                worn.add(new Object[]{f.getName(), m.vertexCount, ranked});
            }
            for (Object[] w : worn) {
                List<String> same = new ArrayList<>(byVerts.get((int) w[1]));
                same.remove((String) w[0]);
                System.out.printf("  worn %-10s v=%-5d %-40s inv pair: %s%n", w[0], w[1], w[2], same.isEmpty() ? "-" : same);
            }
        }
    }

    static double[] hist(ModelDefinition m) {
        int[][] g = m.getVertexGroups();
        if (g == null) return null;
        double[] h = new double[256];
        double t = 0;
        for (int i = 0; i < g.length && i < 256; i++) if (g[i] != null) { h[i] = g[i].length; t += g[i].length; }
        if (t == 0) return null;
        for (int i = 0; i < 256; i++) h[i] /= t;
        return h;
    }

    static double overlap(double[] a, double[] b) {
        double s = 0;
        for (int i = 0; i < 256; i++) s += Math.min(a[i], b[i]);
        return s;
    }
}

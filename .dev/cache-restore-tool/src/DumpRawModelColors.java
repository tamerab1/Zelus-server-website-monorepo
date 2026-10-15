import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.loaders.ModelLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.models.JagexColor;
import java.io.File;
import java.util.Map;
import java.util.TreeMap;

public class DumpRawModelColors {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            for (int i = 1; i < args.length; i++) {
                int modelId = Integer.parseInt(args[i]);
                Archive archive = store.getIndex(IndexType.MODELS).getArchive(modelId);
                byte[] raw = archive.decompress(storage.loadArchive(archive));
                ModelDefinition md = new ModelLoader().load(modelId, raw);
                Map<Short, Integer> counts = new TreeMap<>();
                for (int f = 0; f < md.faceCount; f++) {
                    short c = md.faceColors[f];
                    counts.merge(c, 1, Integer::sum);
                }
                System.out.println("=== model " + modelId + " (" + md.faceCount + " faces, " + counts.size() + " distinct colors) ===");
                counts.entrySet().stream()
                        .sorted((a, b) -> b.getValue() - a.getValue())
                        .limit(25)
                        .forEach(e -> {
                            short c = e.getKey();
                            int hue = JagexColor.unpackHue(c);
                            int sat = JagexColor.unpackSaturation(c);
                            int lum = JagexColor.unpackLuminance(c);
                            System.out.println("  hsl16=" + c + " (h=" + hue + " s=" + sat + " l=" + lum + ") faces=" + e.getValue());
                        });
            }
        }
    }
}

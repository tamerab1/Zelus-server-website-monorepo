import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.ObjectDefinition;
import net.runelite.cache.definitions.loaders.ObjectLoader;
import net.runelite.cache.fs.*;
import net.runelite.cache.index.FileData;

import java.io.File;
import java.util.*;

// Read-only. Objects whose name matches a regex (case-insensitive): id, size, models with vertex/face counts and height.
// Usage: SearchObjectByName <cachePath> <regex>
public class SearchObjectByName {
    public static void main(String[] args) throws Exception {
        try (Store store = new Store(new File(args[0]))) {
            store.load();
            Archive a = store.getIndex(IndexType.CONFIGS).getArchive(ConfigType.OBJECT.getId());
            byte[] dec = a.decompress(store.getStorage().loadArchive(a));
            FileData[] fd = a.getFileData();
            List<byte[]> chunks = SpliceItemOption.splitChunks(dec, fd.length);
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(args[1], java.util.regex.Pattern.CASE_INSENSITIVE);
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < fd.length; i++) {
                try {
                    ObjectDefinition d = new ObjectLoader().load(fd[i].getId(), chunks.get(i));
                    if (d.getName() == null || !p.matcher(d.getName()).matches() || d.getObjectModels() == null) continue;
                    String key = d.getName() + Arrays.toString(d.getObjectModels());
                    if (!seen.add(key)) continue;
                    StringBuilder sb = new StringBuilder();
                    for (int mid : d.getObjectModels()) {
                        try {
                            ModelDefinition m = BuildTierPets.model(store, mid);
                            int ymin = 0; for (int v = 0; v < m.vertexCount; v++) ymin = Math.min(ymin, m.vertexY[v]);
                            sb.append(mid).append("(").append(m.vertexCount).append("v,h").append(-ymin).append(") ");
                        } catch (Exception e) { sb.append(mid).append("(?) "); }
                    }
                    System.out.println(fd[i].getId() + " " + d.getName() + " " + d.getSizeX() + "x" + d.getSizeY() + " " + sb);
                } catch (Exception ignored) {}
            }
        }
    }
}

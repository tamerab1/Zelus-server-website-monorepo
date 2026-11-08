import net.runelite.cache.*;
import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.loaders.ModelLoader;
import net.runelite.cache.fs.*;
import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.*;
/** rev 241 decode: per-model skins file + a colour histogram; also checks which model ids are free. */
public class DumpSkins {
  public static void main(String[] a) throws Exception {
    try (Store s = new Store(new File(a[0]))) { s.load();
      Index ix = s.getIndex(IndexType.MODELS);
      for (int i = 2; i < a.length; i++) { int id = Integer.parseInt(a[i]);
        Archive ar = ix.getArchive(id);
        byte[] raw = ar.decompress(s.getStorage().loadArchive(ar));
        ModelDefinition m = new ModelLoader().load(id, raw);
        int[] labels = new int[m.vertexCount]; Arrays.fill(labels, 0);
        int[][] g = m.getVertexGroups();
        if (g != null) for (int k = 0; k < g.length; k++) for (int v : g[k]) labels[v] = k;
        String faces = m.packedTransparencyVertexGroups == null ? "" : Arrays.stream(m.packedTransparencyVertexGroups).mapToObj(String::valueOf).collect(Collectors.joining(","));
        Files.writeString(new File(a[1], "skins_" + id + ".txt").toPath(), (g == null ? "" : Arrays.stream(labels).mapToObj(String::valueOf).collect(Collectors.joining(","))) + "\n" + faces + "\n");
        Files.write(new File(a[1], "raw_" + id + ".dat").toPath(), raw);
        Map<Integer,Integer> h = new TreeMap<>();
        for (int f = 0; f < m.faceCount; f++) h.merge(m.faceColors[f] & 0xffff, 1, Integer::sum);
        System.out.println(id + " v=" + m.vertexCount + " f=" + m.faceCount + " groups=" + (g == null ? 0 : g.length) + " tex=" + (m.faceTextures != null) + " footer=" + (raw[raw.length-2]&0xff) + "," + (raw[raw.length-1]&0xff) + " colours=" + h);
      }
      StringBuilder free = new StringBuilder();
      for (int id = 62803; id < 65536 && free.length() < 200; id++) if (ix.getArchive(id) == null) free.append(id).append(' ');
      System.out.println("free model ids: " + free);
    }
  }
}

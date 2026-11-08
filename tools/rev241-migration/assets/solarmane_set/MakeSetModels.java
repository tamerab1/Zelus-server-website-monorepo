import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.loaders.ModelLoader;
import java.io.File;
import java.nio.file.Files;
import java.util.*;
/** rev 241 lib: raw_<src>.dat -> model_<dst>.dat with Solarmane's textures on the fur colours, verified by re-decode. */
public class MakeSetModels {
  static final Map<Integer, Short> TEX = Map.of(38003, (short) 478, 41069, (short) 478, 39029, (short) 478, 32889, (short) 479);
  public static void main(String[] a) throws Exception {
    File dir = new File(a[0]);
    for (int i = 1; i < a.length; i++) {
      String[] p = a[i].split(":"); int src = Integer.parseInt(p[0]), dst = Integer.parseInt(p[1]);
      byte[] raw = Files.readAllBytes(new File(dir, "raw_" + src + ".dat").toPath());
      ModelDefinition m = new ModelLoader().load(src, raw), o = new ModelLoader().load(src, raw);
      int[][] g = m.getVertexGroups();
      if (g != null) { int[] packed = new int[m.vertexCount]; for (int k = 0; k < g.length; k++) for (int v : g[k]) packed[v] = k; m.packedVertexGroups = packed; }
      short[] ft = new short[m.faceCount]; Arrays.fill(ft, (short) -1); int n = 0;
      for (int f = 0; f < m.faceCount; f++) { Short t = TEX.get(m.faceColors[f] & 0xffff); if (t != null) { ft[f] = t; n++; } }
      if (m.faceTextures != null) throw new IllegalStateException(src + " already textured");
      m.faceTextures = ft; m.textureCoords = null;
      byte[] enc = ModelType3Encoder.encode(m);
      ModelDefinition b = new ModelLoader().load(dst, enc);
      chk(Arrays.equals(b.vertexX, o.vertexX) && Arrays.equals(b.vertexY, o.vertexY) && Arrays.equals(b.vertexZ, o.vertexZ), src, "vertices");
      chk(Arrays.equals(b.faceIndices1, o.faceIndices1) && Arrays.equals(b.faceIndices2, o.faceIndices2) && Arrays.equals(b.faceIndices3, o.faceIndices3), src, "faces");
      chk(Arrays.equals(b.faceColors, o.faceColors), src, "colours");
      chk(Arrays.equals(b.faceRenderPriorities, o.faceRenderPriorities) && b.priority == o.priority, src, "priorities");
      chk(Arrays.equals(b.faceTransparencies, o.faceTransparencies), src, "alpha");
      chk(Arrays.deepEquals(b.getVertexGroups(), o.getVertexGroups()), src, "vertex skins");
      chk(Arrays.equals(b.packedTransparencyVertexGroups, o.packedTransparencyVertexGroups), src, "face skins");
      chk(Arrays.equals(b.faceTextures, ft), src, "textures");
      Files.write(new File(dir, "model_" + dst + ".dat").toPath(), enc);
      System.out.println(src + " -> " + dst + ": " + n + "/" + m.faceCount + " faces textured, skins " + (g == null ? 0 : g.length) + " groups, " + enc.length + " bytes OK");
    }
  }
  static void chk(boolean ok, int id, String w) { if (!ok) throw new IllegalStateException(id + ": " + w + " changed"); }
}

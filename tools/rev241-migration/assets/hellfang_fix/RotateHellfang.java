import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.loaders.ModelLoader;
import net.runelite.cache.fs.*;
import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
/**
 * One-off (2026-11-17): the Hellfang crossbow's worn model 65472 was the old Red CLAWS model, lying flat in the x-z plane,
 * so with the Armadyl crossbow animations it was held sideways (user screenshot). Rotated 90 degrees about x,
 * (y, z) -> (-z, y) (a proper rotation: face winding unchanged), bolt tip down like the ACB's limbs, then moved so its
 * limbs / shaft / thickness centre sit where the ACB's (19839) do. Skin labels kept (all 50 = right hand) and verified.
 */
public class RotateHellfang {
  public static void main(String[] a) throws Exception {
    Store s = new Store(new File(a[0])); s.load();
    int id = 65472;
    Archive ar = s.findIndex(7).getArchive(id);
    ModelDefinition m = new ModelLoader().load(id, ar.decompress(s.getStorage().loadArchive(ar)));
    int[][] groupsBefore = m.getVertexGroups();
    if (m.vertexY[0] > 0 || Arrays.stream(m.vertexZ).min().getAsInt() != -91) throw new IllegalStateException("not the original flat model (already rotated?)");
    for (int k = 0; k < m.vertexCount; k++) {
      int y = m.vertexY[k], z = m.vertexZ[k];
      m.vertexX[k] = m.vertexX[k] + 3;
      m.vertexY[k] = -z - 110;
      m.vertexZ[k] = y + 86;
    }
    m.packedVertexGroups = TextureModelFaces.packVertexGroups(groupsBefore, m.vertexCount);
    byte[] enc = ModelType3Encoder.encode(m);
    ModelDefinition back = new ModelLoader().load(id, enc);
    if (back.faceCount != m.faceCount || back.vertexCount != m.vertexCount || !Arrays.equals(back.faceColors, m.faceColors)
        || !Arrays.equals(back.vertexX, m.vertexX) || !Arrays.equals(back.vertexY, m.vertexY) || !Arrays.equals(back.vertexZ, m.vertexZ)
        || !Arrays.deepEquals(back.getVertexGroups(), groupsBefore))
      throw new IllegalStateException("round trip failed");
    File out = new File(a[1], "models/" + id + ".dat"); out.getParentFile().mkdirs();
    Files.write(out.toPath(), enc);
    System.out.println("wrote " + out + " (" + enc.length + " bytes), verts " + back.vertexCount + ", faces " + back.faceCount + ", skins kept");
  }
}

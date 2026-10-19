import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.loaders.ModelLoader;

import java.io.File;
import java.nio.file.Files;
import java.util.*;

// Re-rigs a worn model onto a chain of player skin labels by distance from an anchor (2026-10-19, Vermilion Dreadwings).
// A worn item can't play its own animation -- it only moves with the player's stand / walk / run frames, which swing the
// cape's labels (Fire cape 9638: back 8, then 9 -> 16 -> 14 -> 13 -> 15 -> 12 at the hem, the hem moving most). Wings rigged
// rigid on the torso label never move; graded along that chain (roots on the back, tips on the hem labels) they breathe
// when standing, beat when walking and stream when running.
// Bands: "<fraction of the max distance>:<label>,..." in increasing order; the last band covers the rest.
// Usage: RigByDistance <in.dat> <out.dat> <anchorX,anchorY,anchorZ> <bands>
public class RigByDistance {
    public static void main(String[] args) throws Exception {
        ModelDefinition m = new ModelLoader().load(0, Files.readAllBytes(new File(args[0]).toPath()));
        String[] a = args[2].split(",");
        double ax = Double.parseDouble(a[0]), ay = Double.parseDouble(a[1]), az = Double.parseDouble(a[2]);
        List<double[]> bands = new ArrayList<>();
        for (String b : args[3].split(",")) {
            String[] p = b.split(":");
            bands.add(new double[]{Double.parseDouble(p[0]), Integer.parseInt(p[1])});
        }
        double max = 0;
        double[] d = new double[m.vertexCount];
        for (int v = 0; v < m.vertexCount; v++) {
            d[v] = Math.sqrt(sq(m.vertexX[v] - ax) + sq(m.vertexY[v] - ay) + sq(m.vertexZ[v] - az));
            max = Math.max(max, d[v]);
        }
        int[] lab = new int[m.vertexCount];
        Map<Integer, Integer> count = new TreeMap<>();
        for (int v = 0; v < m.vertexCount; v++) {
            double f = d[v] / max;
            int l = (int) bands.get(bands.size() - 1)[1];
            for (double[] b : bands)
                if (f < b[0]) { l = (int) b[1]; break; }
            lab[v] = l;
            count.merge(l, 1, Integer::sum);
        }
        m.packedVertexGroups = lab;
        byte[] enc = ModelType3Encoder.encode(m);
        ModelDefinition back = new ModelLoader().load(0, enc);
        if (back.vertexCount != m.vertexCount || back.faceCount != m.faceCount || !Arrays.equals(back.faceColors, m.faceColors))
            throw new IllegalStateException("round trip");
        for (int v = 0; v < m.vertexCount; v++)
            if (!contains(back.getVertexGroups()[lab[v]], v)) throw new IllegalStateException("labels round trip");
        Files.write(new File(args[1]).toPath(), enc);
        System.out.printf("max distance %.0f, vertices per label %s -> %s%n", max, count, args[1]);
    }

    static boolean contains(int[] a, int v) {
        if (a == null) return false;
        for (int x : a) if (x == v) return true;
        return false;
    }

    static double sq(double v) { return v * v; }
}

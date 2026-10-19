import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.fs.Store;

import java.io.File;
import java.util.*;

// Read-only (2026-10-19, Vermilion Dreadwings): for a cache model, how far each vertex skin label's centroid travels
// (max over the sequence's frames, and the dominant axis) under player sequences -- to find which labels a worn item can
// ride to move while the player stands / walks / runs. Usage: LabelMotion <cache> <modelId> <seq>...
public class LabelMotion {
    public static void main(String[] args) throws Exception {
        try (Store store = new Store(new File(args[0]))) {
            store.load();
            FindBossRig.storage = store.getStorage();
            FindBossRig.frameIndex = store.getIndex(net.runelite.cache.IndexType.ANIMATIONS);
            FindBossRig.skeletonIndex = store.getIndex(net.runelite.cache.IndexType.SKELETONS);
            FindBossRig.modelIndex = store.getIndex(net.runelite.cache.IndexType.MODELS);
            var seqArc = store.getIndex(net.runelite.cache.IndexType.CONFIGS).getArchive(net.runelite.cache.ConfigType.SEQUENCE.getId());
            var fd = seqArc.getFileData();
            var files = SpliceItemOption.splitChunks(seqArc.decompress(store.getStorage().loadArchive(seqArc)), fd.length);
            for (int i = 0; i < fd.length; i++)
                FindBossRig.seqs.put(fd[i].getId(), new net.runelite.cache.definitions.loaders.SequenceLoader().load(fd[i].getId(), files.get(i)));
            ModelDefinition m = BuildTierPets.model(store, Integer.parseInt(args[1]));
            int[][] g = m.getVertexGroups();
            for (int a = 2; a < args.length; a++) {
                int seq = Integer.parseInt(args[a]);
                var s = FindBossRig.seqs.get(seq);
                double[][] rest = new double[g.length][];
                for (int l = 0; l < g.length; l++) rest[l] = centroid(m, g[l]);
                double[][] maxd = new double[g.length][3];
                for (int fid : s.frameIDs) {
                    if (!FindBossRig.apply(m, fid)) continue;
                    for (int l = 0; l < g.length; l++) {
                        if (g[l] == null || g[l].length == 0) continue;
                        double[] c = centroid(m, g[l]);
                        for (int k = 0; k < 3; k++) maxd[l][k] = Math.max(maxd[l][k], Math.abs(c[k] - rest[l][k]));
                    }
                    m.resetAnim();
                }
                StringBuilder sb = new StringBuilder("seq " + seq + ":");
                for (int l = 0; l < g.length; l++)
                    if (g[l] != null && g[l].length > 0)
                        sb.append(String.format(" L%d(%d v, y%.0f z%.0f: dx%.0f dy%.0f dz%.0f)", l, g[l].length, rest[l][1], rest[l][2], maxd[l][0], maxd[l][1], maxd[l][2]));
                System.out.println(sb);
            }
        }
    }

    static double[] centroid(ModelDefinition m, int[] vs) {
        double[] c = new double[3];
        if (vs == null || vs.length == 0) return c;
        for (int v : vs) { c[0] += m.vertexX[v]; c[1] += m.vertexY[v]; c[2] += m.vertexZ[v]; }
        for (int k = 0; k < 3; k++) c[k] /= vs.length;
        return c;
    }
}

import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.loaders.ModelLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import java.io.File;
import java.util.*;

public class CheckRenderTypesImp {
    public static void main(String[] args) throws Exception {
        try (Store store = new Store(new File(args[0]))) {
            store.load();
            Index models = store.getIndex(IndexType.MODELS);
            Archive a = models.getArchive(26374);
            byte[] data = a.decompress(store.getStorage().loadArchive(a));
            ModelDefinition md = new ModelLoader().load(26374, data);
            System.out.println("faceRenderTypes=" + (md.faceRenderTypes==null?"null":Arrays.toString(md.faceRenderTypes)));
            System.out.println("priority=" + md.priority);
            System.out.println("faceColors sample=" + Arrays.toString(Arrays.copyOf(md.faceColors, 20)));
            // print y-range of vertices used by opaque (transparency==0) faces only
            int minY=Integer.MAX_VALUE, maxY=Integer.MIN_VALUE;
            for (int f=0; f<md.faceCount; f++) {
                int t = md.faceTransparencies==null?0:(md.faceTransparencies[f]&0xFF);
                if (t > 200) continue; // skip near-fully-transparent
                int[] idx = {md.faceIndices1[f], md.faceIndices2[f], md.faceIndices3[f]};
                for (int vi : idx) {
                    minY = Math.min(minY, md.vertexY[vi]);
                    maxY = Math.max(maxY, md.vertexY[vi]);
                }
            }
            System.out.println("opaque-ish faces Y range=[" + minY + "," + maxY + "]");
        }
    }
}

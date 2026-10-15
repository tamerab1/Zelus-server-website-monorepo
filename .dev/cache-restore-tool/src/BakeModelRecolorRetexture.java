import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.loaders.ModelLoader;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Store;

import java.io.File;
import java.nio.file.Files;
import java.util.*;

// Bakes an item's recolour AND retexture pairs into a copy of its worn model, so the model can be used directly on an
// NPC whose other models share those colours (an NPC-level recolour would hit them all). Exact-match remaps of
// faceColors and faceTextures; re-encoded with ModelType3Encoder via BuildTierPets.model (vertex skins preserved,
// round-trip + skins checked). Writes <newId>.dat into outDir for InsertRawModels; also prints the first free model
// id >= a floor below 65536 when asked. Usage:
//   free <cachePath> <floor>
//   bake <cachePath> <srcModel> <newModel> <outDir> <find,replace,...|-> <texFind,texReplace,...|->
public class BakeModelRecolorRetexture {
    public static void main(String[] args) throws Exception {
        try (Store store = new Store(new File(args[1]))) {
            store.load();
            Index models = store.getIndex(IndexType.MODELS);
            if (args[0].equals("free")) {
                for (int id = Integer.parseInt(args[2]); id < 65536; id++)
                    if (models.getArchive(id) == null) {
                        System.out.println("FREE " + id);
                        return;
                    }
                throw new IllegalStateException("no free model id below 65536");
            }
            int src = Integer.parseInt(args[2]), dst = Integer.parseInt(args[3]);
            if (dst >= 65536) throw new IllegalArgumentException("npc model lists are u16: id must be < 65536");
            // "rebake": dst may already exist (the output then goes in via InsertRawModels replace)
            if (models.getArchive(dst) != null && !args[0].equals("rebake")) throw new IllegalStateException("model " + dst + " already exists");
            Map<Short, Short> colours = pairs(args[5]), textures = pairs(args[6]);
            ModelDefinition m = BuildTierPets.model(store, src);
            int recoloured = 0, retextured = 0;
            for (int f = 0; f < m.faceCount; f++) {
                if (m.faceColors != null && colours.containsKey(m.faceColors[f])) {
                    m.faceColors[f] = colours.get(m.faceColors[f]);
                    recoloured++;
                }
                if (m.faceTextures != null && textures.containsKey(m.faceTextures[f])) {
                    m.faceTextures[f] = textures.get(m.faceTextures[f]);
                    retextured++;
                }
            }
            byte[] enc = ModelType3Encoder.encode(m);
            ModelDefinition back = new ModelLoader().load(dst, enc);
            if (back.vertexCount != m.vertexCount || back.faceCount != m.faceCount)
                throw new IllegalStateException("round trip changed the geometry");
            int[] backSkins = back.packedVertexGroups != null ? back.packedVertexGroups
                    : back.getVertexGroups() == null ? null : TextureModelFaces.packVertexGroups(back.getVertexGroups(), back.vertexCount);
            if (m.packedVertexGroups != null && !Arrays.equals(backSkins, m.packedVertexGroups))
                throw new IllegalStateException("vertex skins lost");
            for (int f = 0; f < back.faceCount; f++) {
                if (back.faceColors[f] != m.faceColors[f]) throw new IllegalStateException("colour " + f + " not kept");
                if (m.faceTextures != null && back.faceTextures[f] != m.faceTextures[f]) throw new IllegalStateException("texture " + f + " not kept");
            }
            File dir = new File(args[4]);
            dir.mkdirs();
            Files.write(new File(dir, dst + ".dat").toPath(), enc);
            System.out.println("model " + src + " -> " + dst + ": " + recoloured + " faces recoloured, " + retextured
                    + " retextured, " + m.vertexCount + " vertices (skins kept)");
        }
    }

    static Map<Short, Short> pairs(String csv) {
        Map<Short, Short> map = new HashMap<>();
        if (csv.equals("-")) return map;
        String[] p = csv.split(",");
        if (p.length % 2 != 0) throw new IllegalArgumentException("pairs must be find,replace,...");
        for (int i = 0; i < p.length; i += 2) map.put((short) Integer.parseInt(p[i]), (short) Integer.parseInt(p[i + 1]));
        return map;
    }
}

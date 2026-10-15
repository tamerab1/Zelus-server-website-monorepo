import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.FrameDefinition;
import net.runelite.cache.definitions.FramemapDefinition;
import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.SequenceDefinition;
import net.runelite.cache.definitions.loaders.FrameLoader;
import net.runelite.cache.definitions.loaders.FramemapLoader;
import net.runelite.cache.definitions.loaders.SequenceLoader;
import net.runelite.cache.definitions.NpcDefinition;
import net.runelite.cache.definitions.loaders.ModelLoader;
import net.runelite.cache.definitions.loaders.NpcLoader;
import net.runelite.cache.fs.*;
import net.runelite.cache.index.FileData;

import java.io.File;
import java.nio.file.Files;
import java.util.*;

// Builds the Zelus Teleports (1152) preview models + their bounds (2026-10-15).
// The preview is ONE interface model (clientscript 10623 = a single model id, no NPC-level recolours), so before this
// multi-part NPCs showed only models[0] (a head or a torso), baked-recolour copies had lost their animation skins (frozen
// Cindermaw / Sylvaroth) and ~140 teleports had no preview at all. For every teleport the spec names either a model +
// animation, or an NPC: an NPC with several parts and/or recolours gets ONE merged model (all parts, recolours and
// retextures baked in, vertex skins + animaya kept so its stand animation plays), written to a free model id.
// Every preview's bounding box goes to a JSON file the server reads to pick a zoom that fits the 144x80 preview box.
//
// Spec lines:
//   ids <from>-<to>[,<from>-<to>...]          free model ids for merged models (InsertRawModels needs < 65536)
//   <TELEPORT> model <modelId> <animId>       use this model and animation as they are
//   <TELEPORT> npc <npcId> [animId]           the NPC (merged when needed) and its stand animation (or the given one)
//   either may end with zoom=<n> (a fixed preview zoom instead of the bounds fit) and cy=<y> (the model y to centre on)
// Usage: verify|build|json <cachePath> <spec> <modelOutDir> <jsonOut>   (json: models already inserted, rewrite bounds only)
//   build writes <id>.dat files (insert them with InsertRawModels apply) and the JSON; verify writes nothing.
public class BuildTeleportPreviews {

    record Preview(String teleport, int model, int anim, int[] bounds, boolean skins, String source, int zoom, int centerY) {
    }

    public static void main(String[] args) throws Exception {
        boolean build = args[0].equals("build");
        // json: the merged models are already in the cache (same spec, same id order): rewrite only the bounds file,
        // after checking each existing merged model is byte-identical to a fresh merge
        boolean jsonOnly = args[0].equals("json");
        File outDir = new File(args[3]);
        Deque<Integer> free = new ArrayDeque<>();
        List<String[]> lines = new ArrayList<>();
        for (String line : Files.readAllLines(new File(args[2]).toPath())) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#"))
                continue;
            String[] p = line.split("\\s+");
            if (p[0].equals("ids")) {
                for (String range : p[1].split(",")) {
                    String[] ab = range.split("-");
                    for (int id = Integer.parseInt(ab[0]); id <= Integer.parseInt(ab[1]); id++)
                        free.add(id);
                }
            } else {
                // optional trailing "zoom=N": a fixed preview zoom when the bounds mislead (Galvek's flying pose)
                lines.add(p);
            }
        }
        try (Store store = new Store(new File(args[1]))) {
            store.load();
            Storage storage = store.getStorage();
            Index models = store.getIndex(IndexType.MODELS);
            Archive npcArchive = store.getIndex(IndexType.CONFIGS).getArchive(ConfigType.NPC.getId());
            FileData[] npcFd = npcArchive.getFileData();
            List<byte[]> npcFiles = SpliceItemOption.splitChunks(npcArchive.decompress(storage.loadArchive(npcArchive)), npcFd.length);

            Archive seqArchive = store.getIndex(IndexType.CONFIGS).getArchive(ConfigType.SEQUENCE.getId());
            seqFd = seqArchive.getFileData();
            seqFiles = SpliceItemOption.splitChunks(seqArchive.decompress(storage.loadArchive(seqArchive)), seqFd.length);
            frameIndex = store.getIndex(IndexType.ANIMATIONS);
            skeletonIndex = store.getIndex(IndexType.SKELETONS);
            Map<Integer, Integer> mergedByNpc = new HashMap<>();
            Map<Integer, byte[]> newModels = new TreeMap<>();
            List<Preview> previews = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (String[] line : lines) {
                int zoom = 0, centerY = 0;
                List<String> tokens = new ArrayList<>();
                for (String t : line) {
                    if (t.startsWith("zoom="))
                        zoom = Integer.parseInt(t.substring(5));
                    else if (t.startsWith("cy="))
                        centerY = Integer.parseInt(t.substring(3));
                    else
                        tokens.add(t);
                }
                String[] p = tokens.toArray(new String[0]);
                String teleport = p[0];
                if (!seen.add(teleport))
                    throw new IllegalStateException("teleport listed twice: " + teleport);
                if (p[1].equals("model")) {
                    int modelId = Integer.parseInt(p[2]), anim = Integer.parseInt(p[3]);
                    ModelDefinition m = load(models, storage, modelId);
                    previews.add(new Preview(teleport, modelId, anim, animatedBounds(storage, m, anim), m.getVertexGroups() != null || m.animayaGroups != null, "model", zoom, centerY));
                    continue;
                }
                int npcId = Integer.parseInt(p[2]);
                NpcDefinition def = null;
                for (int i = 0; i < npcFd.length; i++) // last slot wins (duplicate ids, as the client loads them)
                    if (npcFd[i].getId() == npcId)
                        def = new NpcLoader().load(npcId, npcFiles.get(i));
                if (def == null || def.models == null || def.models.length == 0)
                    throw new IllegalStateException(teleport + ": npc " + npcId + " has no models");
                int anim = p.length > 3 ? Integer.parseInt(p[3]) : def.standingAnimation;
                boolean recoloured = def.recolorToFind != null || def.retextureToFind != null;
                if (def.models.length == 1 && !recoloured) {
                    ModelDefinition m = load(models, storage, def.models[0]);
                    previews.add(new Preview(teleport, def.models[0], anim, animatedBounds(storage, m, anim), m.getVertexGroups() != null || m.animayaGroups != null,
                            "npc " + npcId, zoom, centerY));
                    continue;
                }
                Integer merged = mergedByNpc.get(npcId);
                if (merged == null) {
                    if (free.isEmpty())
                        throw new IllegalStateException("out of free model ids at " + teleport);
                    merged = free.poll();
                    byte[] enc = merge(models, storage, def, merged);
                    if (models.getArchive(merged) != null) {
                        if (!jsonOnly)
                            throw new IllegalStateException("model id " + merged + " is not free");
                        Archive existing = models.getArchive(merged);
                        if (!Arrays.equals(existing.decompress(storage.loadArchive(existing)), enc))
                            throw new IllegalStateException("model " + merged + " in the cache differs from the merge of npc " + npcId);
                    }
                    newModels.put(merged, enc);
                    mergedByNpc.put(npcId, merged);
                }
                ModelDefinition m = new ModelLoader().load(merged, newModels.get(merged).clone());
                previews.add(new Preview(teleport, merged, anim, animatedBounds(storage, m, anim), m.getVertexGroups() != null || m.animayaGroups != null,
                        "npc " + npcId + " merged " + def.models.length + " part(s)" + (recoloured ? " + recolours" : ""), zoom, centerY));
            }

            for (Preview pv : previews)
                System.out.printf("%-34s model %-6d anim %-6d h=%-5d w=%-5d p-h=%-5d p-w=%-5d skins=%-5s %s%n", pv.teleport, pv.model, pv.anim,
                        pv.bounds[3] - pv.bounds[2], pv.bounds[1] - pv.bounds[0], pv.bounds[9] - pv.bounds[8], pv.bounds[7] - pv.bounds[6],
                        pv.skins, pv.source);
            System.out.println(previews.size() + " previews, " + newModels.size() + " merged models " + newModels.keySet());

            StringBuilder json = new StringBuilder("{\n");
            for (int i = 0; i < previews.size(); i++) {
                Preview pv = previews.get(i);
                int[] b = pv.bounds;
                json.append(String.format("  \"%s\": {\"model\": %d, \"anim\": %d, \"minX\": %d, \"maxX\": %d, \"minY\": %d, \"maxY\": %d, \"minZ\": %d, \"maxZ\": %d, \"pMinX\": %d, \"pMaxX\": %d, \"pMinY\": %d, \"pMaxY\": %d, \"zoom\": %d, \"centerY\": %d}%s%n",
                        pv.teleport, pv.model, pv.anim, b[0], b[1], b[2], b[3], b[4], b[5], b[6], b[7], b[8], b[9], pv.zoom, pv.centerY, i + 1 < previews.size() ? "," : ""));
            }
            json.append("}\n");
            if (jsonOnly) {
                Files.writeString(new File(args[4]).toPath(), json.toString());
                System.out.println("JSON written to " + args[4] + " (models already in the cache, unchanged)");
                return;
            }
            if (!build) {
                System.out.println("VERIFY OK -- nothing written.");
                return;
            }
            outDir.mkdirs();
            for (Map.Entry<Integer, byte[]> e : newModels.entrySet())
                Files.write(new File(outDir, e.getKey() + ".dat").toPath(), e.getValue());
            Files.writeString(new File(args[4]).toPath(), json.toString());
            System.out.println("BUILD complete -- " + newModels.size() + " model files in " + outDir + ", bounds in " + args[4]);
        }
    }

    static FileData[] seqFd;
    static List<byte[]> seqFiles;
    static Index frameIndex, skeletonIndex;
    static final Map<Integer, ArchiveFiles> frameGroups = new HashMap<>();

    /**
     * The preview plays the stand animation, which can lift or drop a model well away from its rest pose (Zulrah rises,
     * a Vyrewatch hovers): bounds are taken over up to 12 frames of it (percentile box unioned across frames, full box
     * unioned too). Skeletal (animaya) sequences and missing frames fall back to the rest pose.
     */
    static int[] animatedBounds(Storage storage, ModelDefinition m, int animId) throws Exception {
        int[] rest = bounds(m);
        if (animId <= 0 || m.getVertexGroups() == null)
            return rest;
        SequenceDefinition seq = null;
        for (int i = 0; i < seqFd.length; i++)
            if (seqFd[i].getId() == animId)
                seq = new SequenceLoader().load(animId, seqFiles.get(i));
        if (seq == null || seq.frameIDs == null || seq.frameIDs.length == 0)
            return rest;
        int[] acc = rest.clone();
        int step = Math.max(1, seq.frameIDs.length / 12), used = 0;
        for (int k = 0; k < seq.frameIDs.length; k += step) {
            int group = seq.frameIDs[k] >>> 16, file = seq.frameIDs[k] & 0xffff;
            ArchiveFiles files = frameGroups.get(group);
            if (files == null) {
                Archive a = frameIndex.getArchive(group);
                if (a == null)
                    continue;
                try {
                    files = a.getFiles(storage.loadArchive(a));
                } catch (Exception e) {
                    continue;
                }
                frameGroups.put(group, files);
            }
            FSFile f = files.findFile(file);
            if (f == null)
                continue;
            byte[] c = f.getContents();
            int mapId = (c[0] & 0xff) << 8 | c[1] & 0xff;
            Archive mapArchive = skeletonIndex.getArchive(mapId);
            if (mapArchive == null)
                continue;
            FramemapDefinition map = new FramemapLoader().load(mapId, mapArchive.decompress(storage.loadArchive(mapArchive)));
            FrameDefinition frame = new FrameLoader().load(map, file, c);
            m.resetAnim();
            try {
                for (int i = 0; i < frame.translatorCount; i++) {
                    int idx = frame.indexFrameIds[i];
                    m.animate(map.types[idx], map.frameMaps[idx], frame.translator_x[i], frame.translator_y[i], frame.translator_z[i]);
                }
            } catch (RuntimeException e) {
                continue; // a frame for another skeleton: ignore
            }
            int[] b = bounds(m);
            for (int q = 0; q < 10; q++)
                acc[q] = (q % 2 == 0) ? Math.min(acc[q], b[q]) : Math.max(acc[q], b[q]);
            used++;
        }
        m.resetAnim();
        return used == 0 ? rest : acc;
    }

    static ModelDefinition load(Index models, Storage storage, int id) throws Exception {
        Archive a = models.getArchive(id);
        if (a == null)
            throw new IllegalStateException("model " + id + " does not exist");
        ModelDefinition m = new ModelLoader().load(id, a.decompress(storage.loadArchive(a)));
        // ModelLoader nulls packedVertexGroups after building the skin table: rebuild it so the skins survive encoding
        if (m.packedVertexGroups == null && m.getVertexGroups() != null)
            m.packedVertexGroups = TextureModelFaces.packVertexGroups(m.getVertexGroups(), m.vertexCount);
        return m;
    }

    /**
     * minX, maxX, minY, maxY, minZ, maxZ (model units; y grows downwards, feet near 0), then the 2nd / 98th percentile
     * of x and y: a few far-off vertices (Galvek) must not shrink the whole model in the preview.
     */
    static int[] bounds(ModelDefinition m) {
        int[] b = {Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE,
                0, 0, 0, 0};
        for (int v = 0; v < m.vertexCount; v++) {
            b[0] = Math.min(b[0], m.vertexX[v]);
            b[1] = Math.max(b[1], m.vertexX[v]);
            b[2] = Math.min(b[2], m.vertexY[v]);
            b[3] = Math.max(b[3], m.vertexY[v]);
            b[4] = Math.min(b[4], m.vertexZ[v]);
            b[5] = Math.max(b[5], m.vertexZ[v]);
        }
        int[] xs = Arrays.copyOf(m.vertexX, m.vertexCount), ys = Arrays.copyOf(m.vertexY, m.vertexCount);
        Arrays.sort(xs);
        Arrays.sort(ys);
        int lo = (int) (m.vertexCount * 0.02), hi = Math.max(lo, (int) Math.ceil(m.vertexCount * 0.98) - 1);
        b[6] = xs[lo];
        b[7] = xs[hi];
        b[8] = ys[lo];
        b[9] = ys[hi];
        return b;
    }

    /** All of the NPC's parts in one model, with its recolours / retextures baked in and every skin kept. */
    static byte[] merge(Index models, Storage storage, NpcDefinition def, int newId) throws Exception {
        List<ModelDefinition> parts = new ArrayList<>();
        for (int id : def.models)
            parts.add(load(models, storage, id));
        int vertices = 0, faces = 0;
        boolean skins = false, faceSkins = false, animaya = false, textured = false;
        for (ModelDefinition m : parts) {
            vertices += m.vertexCount;
            faces += m.faceCount;
            skins |= m.packedVertexGroups != null;
            faceSkins |= m.packedTransparencyVertexGroups != null;
            animaya |= m.animayaGroups != null;
            textured |= m.faceTextures != null;
        }
        if (def.retextureToFind != null)
            textured = true;
        ModelDefinition out = new ModelDefinition();
        out.id = newId;
        out.vertexCount = vertices;
        out.faceCount = faces;
        out.vertexX = new int[vertices];
        out.vertexY = new int[vertices];
        out.vertexZ = new int[vertices];
        out.packedVertexGroups = skins ? new int[vertices] : null;
        out.animayaGroups = animaya ? new int[vertices][] : null;
        out.animayaScales = animaya ? new int[vertices][] : null;
        out.faceIndices1 = new int[faces];
        out.faceIndices2 = new int[faces];
        out.faceIndices3 = new int[faces];
        out.faceColors = new short[faces];
        out.faceTransparencies = new byte[faces];
        out.faceRenderPriorities = new byte[faces];
        out.faceRenderTypes = new byte[faces];
        out.packedTransparencyVertexGroups = faceSkins ? new int[faces] : null;
        out.faceTextures = textured ? new short[faces] : null;
        out.textureCoords = textured ? new byte[faces] : null;
        List<short[]> triangles = new ArrayList<>();
        int v0 = 0, f0 = 0;
        for (ModelDefinition m : parts) {
            for (int v = 0; v < m.vertexCount; v++) {
                out.vertexX[v0 + v] = m.vertexX[v];
                out.vertexY[v0 + v] = m.vertexY[v];
                out.vertexZ[v0 + v] = m.vertexZ[v];
                if (skins)
                    out.packedVertexGroups[v0 + v] = m.packedVertexGroups != null ? m.packedVertexGroups[v] : 0;
                if (animaya) {
                    out.animayaGroups[v0 + v] = m.animayaGroups != null && m.animayaGroups[v] != null ? m.animayaGroups[v] : new int[0];
                    out.animayaScales[v0 + v] = m.animayaScales != null && m.animayaScales[v] != null ? m.animayaScales[v] : new int[0];
                }
            }
            // this part's texture triangles: only render type 0 can be encoded, and the format holds at most 255
            int[] triangleMap = new int[m.numTextureFaces];
            Arrays.fill(triangleMap, -1);
            for (int t = 0; t < m.numTextureFaces; t++) {
                int type = m.textureRenderTypes != null ? m.textureRenderTypes[t] & 0xff : 0;
                if (type != 0 || triangles.size() >= 255 || m.texIndices1 == null)
                    continue;
                triangleMap[t] = triangles.size();
                triangles.add(new short[]{(short) (m.texIndices1[t] + v0), (short) (m.texIndices2[t] + v0), (short) (m.texIndices3[t] + v0)});
            }
            for (int f = 0; f < m.faceCount; f++) {
                int o = f0 + f;
                out.faceIndices1[o] = m.faceIndices1[f] + v0;
                out.faceIndices2[o] = m.faceIndices2[f] + v0;
                out.faceIndices3[o] = m.faceIndices3[f] + v0;
                out.faceColors[o] = recolour(def, m.faceColors[f]);
                out.faceTransparencies[o] = m.faceTransparencies != null ? m.faceTransparencies[f] : 0;
                out.faceRenderPriorities[o] = m.faceRenderPriorities != null ? m.faceRenderPriorities[f] : m.priority;
                out.faceRenderTypes[o] = m.faceRenderTypes != null ? m.faceRenderTypes[f] : 0;
                if (faceSkins)
                    out.packedTransparencyVertexGroups[o] = m.packedTransparencyVertexGroups != null ? m.packedTransparencyVertexGroups[f] : 0;
                if (textured) {
                    short tex = m.faceTextures != null ? m.faceTextures[f] : -1;
                    tex = retexture(def, tex);
                    out.faceTextures[o] = tex;
                    int coord = m.textureCoords != null ? m.textureCoords[f] & 0xff : 0xff;
                    out.textureCoords[o] = (byte) (tex == -1 || coord == 0xff || coord >= triangleMap.length || triangleMap[coord] < 0
                            ? -1 : triangleMap[coord]);
                }
            }
            v0 += m.vertexCount;
            f0 += m.faceCount;
        }
        out.numTextureFaces = triangles.size();
        if (!triangles.isEmpty()) {
            out.texIndices1 = new short[triangles.size()];
            out.texIndices2 = new short[triangles.size()];
            out.texIndices3 = new short[triangles.size()];
            out.textureRenderTypes = new byte[triangles.size()];
            for (int t = 0; t < triangles.size(); t++) {
                out.texIndices1[t] = triangles.get(t)[0];
                out.texIndices2[t] = triangles.get(t)[1];
                out.texIndices3[t] = triangles.get(t)[2];
            }
        }
        out.priority = 0;
        byte[] enc = ModelType3Encoder.encode(out);

        ModelDefinition back = new ModelLoader().load(newId, enc.clone());
        check(back.vertexCount == vertices && back.faceCount == faces, newId + ": counts changed");
        check(Arrays.equals(back.vertexX, out.vertexX) && Arrays.equals(back.vertexY, out.vertexY)
                && Arrays.equals(back.vertexZ, out.vertexZ), newId + ": vertices changed");
        check(Arrays.equals(back.faceIndices1, out.faceIndices1) && Arrays.equals(back.faceIndices2, out.faceIndices2)
                && Arrays.equals(back.faceIndices3, out.faceIndices3), newId + ": faces changed");
        check(Arrays.equals(back.faceColors, out.faceColors), newId + ": colours changed");
        if (skins) {
            int[] packed = TextureModelFaces.packVertexGroups(back.getVertexGroups(), back.vertexCount);
            check(Arrays.equals(packed, out.packedVertexGroups), newId + ": vertex skins changed");
        }
        check(Arrays.equals(back.packedTransparencyVertexGroups, out.packedTransparencyVertexGroups), newId + ": face skins changed");
        return enc;
    }

    static short recolour(NpcDefinition def, short colour) {
        if (def.recolorToFind != null)
            for (int i = 0; i < def.recolorToFind.length; i++)
                if (def.recolorToFind[i] == colour)
                    return def.recolorToReplace[i];
        return colour;
    }

    static short retexture(NpcDefinition def, short texture) {
        if (texture != -1 && def.retextureToFind != null)
            for (int i = 0; i < def.retextureToFind.length; i++)
                if (def.retextureToFind[i] == texture)
                    return def.retextureToReplace[i];
        return texture;
    }

    static void check(boolean ok, String msg) {
        if (!ok)
            throw new IllegalStateException(msg);
    }
}

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.ItemDefinition;
import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.loaders.ItemLoader;
import net.runelite.cache.definitions.loaders.ModelLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;

import java.io.File;
import java.io.StringReader;
import java.nio.file.Files;
import java.util.*;

// System-wide worn-model audit (2026-10-18, after the Crimson Warden and Draconic sets cut the player's head off).
// For every wearable in item_info.json (equip_slot set) it reads the item's male + female worn models from the cache and
// checks the rules that keep a fully geared player whole under BOTH renderers:
//   1. face budget: the client merges a player's kits + gear into one model and the GPU plugin draws at most 6144 faces
//      of it (GpuPlugin.MAX_TRIANGLE) -- the head / hands / feet kits are merged LAST, so they are what gets cut. Each
//      slot gets a budget (BUDGET) that keeps a full set under the cap.
//   2. body layering: a body's torso / neck faces (skin groups 2, 4, 5, 8) must be on priority <= 3 (Torva: 2-3) or they
//      paint over the head and neck in the GPU plugin (117HD's depth buffer hides it), and its arm faces (17-26) on 10 so
//      they depth-sort with the held weapon instead of being painted over by it. Held items (weapon / shield) on 10.
//   3. hide_arms on a body only if its model has arm geometry (skin groups 17-26), or the player's arms vanish.
//   4. hide_hair / hide_beard only on head-slot items (this server sends no other body-part hiding: no wearpos opcodes).
// fix: custom models (id >= CUSTOM_MODEL) over budget are decimated (DecimateModel: per skin group, colours /
// priorities / textures kept) and body torso faces re-layered to 3, written to <outDir>/<id>.dat for InsertRawModels
// replace. Flag problems (rules 3/4) are reported for item_info.json. Real OSRS models are reported, never changed.
// Usage: report|fix <cachePath> <item_info.json> <outDir>
public class AuditWornModels {
    /** A torso face at this priority or above paints over the head (the Crimson Warden body was 10 everywhere). */
    /**
     * Hand-built worn models the audit must leave alone (2026-10-19): the Vermilion Dreadwings 61279 are double-sided
     * with back-facing faces on 10 and their twins on 1 (DoubleSided) -- the cape rule (5 / 1) would undo that.
     */
    static final java.util.Set<Integer> HAND_BUILT = java.util.Set.of(61279);
    static final int CUSTOM_MODEL = 57320, TORSO_PRIORITY = 3, TORSO_TOO_HIGH = 8, ARM_PRIORITY = 10, HELD_PRIORITY = 10;
    static final Map<Integer, Integer> BUDGET = Map.ofEntries(Map.entry(0, 700), Map.entry(1, 900), Map.entry(2, 300),
            Map.entry(3, 800), Map.entry(4, 1000), Map.entry(5, 600), Map.entry(7, 900), Map.entry(9, 300), Map.entry(10, 350),
            Map.entry(12, 200), Map.entry(13, 200));
    static final String[] SLOT = {"head", "cape", "amulet", "weapon", "body", "shield", "?", "legs", "?", "hands", "feet", "?", "ring", "ammo"};
    static final Set<Integer> TORSO_GROUPS = Set.of(2, 4, 5, 8);
    /**
     * Rigging references per slot (real OSRS worn models, same player space): Torva full helm / platebody / platelegs,
     * Ferocious gloves, Primordial boots, the Fire cape. A custom worn model with NO vertex skins (it would stand frozen while the player
     * moves -- the Drakonbone set) takes each vertex's label from the nearest reference vertex, and each face the
     * reference's usual priority for that body part. A weapon / shield without skins goes on the hand (label 50).
     */
    static final Map<Integer, Integer> RIG_REFERENCE = Map.of(0, 49446, 1, 9638, 4, 49450, 7, 49448, 9, 36325, 10, 29250);
    static final int HAND_LABEL = 50, MAX_PRIORITY = 11;

    public static void main(String[] args) throws Exception {
        boolean fix = args[0].equals("fix");
        String json = new String(Files.readAllBytes(new File(args[2]).toPath()), "UTF-8").replaceAll("#[^\\n]*", "");
        JsonReader reader = new JsonReader(new StringReader(json));
        reader.setLenient(true);
        JsonArray items = JsonParser.parseReader(reader).getAsJsonArray();
        File out = new File(args[3]);
        out.mkdirs();
        try (Store store = new Store(new File(args[1]))) {
            store.load();
            Archive arc = store.getIndex(IndexType.CONFIGS).getArchive(ConfigType.ITEM.getId());
            FileData[] fd = arc.getFileData();
            List<byte[]> contents = SpliceItemOption.splitChunks(arc.decompress(store.getStorage().loadArchive(arc)), fd.length);
            Map<Integer, Integer> slotOf = new HashMap<>();
            for (int i = 0; i < fd.length; i++) slotOf.put(fd[i].getId(), i);

            Map<Integer, Integer> modelBudget = new TreeMap<>();      // model -> tightest budget
            Map<Integer, Boolean> modelIsBody = new HashMap<>(), modelIsHeld = new HashMap<>();
            Map<Integer, Integer> modelSlot = new HashMap<>();
            Map<Integer, List<String>> modelUsers = new HashMap<>();
            List<String> flagProblems = new ArrayList<>();
            ItemLoader loader = new ItemLoader();
            for (JsonElement el : items) {
                JsonObject o = el.getAsJsonObject();
                if (!o.has("equip_slot") || o.get("equip_slot").isJsonNull()) continue;
                int id = o.get("id").getAsInt(), slot = o.get("equip_slot").getAsInt();
                Integer s = slotOf.get(id);
                if (s == null) continue;
                ItemDefinition d = loader.load(id, contents.get(s));
                String name = d.name;
                boolean hideArms = o.has("hide_arms") && o.get("hide_arms").getAsBoolean();
                boolean hideHead = (o.has("hide_hair") && o.get("hide_hair").getAsBoolean()) || (o.has("hide_beard") && o.get("hide_beard").getAsBoolean());
                if (hideArms && slot != 4)
                    flagProblems.add(String.format("item %d %s (%s): hide_arms on a non-body item", id, name, SLOT[slot]));
                if (hideHead && slot != 0)
                    flagProblems.add(String.format("item %d %s (%s): hide_hair/hide_beard on a non-head item", id, name, SLOT[slot]));
                // rule 3 per item: arms may live in any of its worn models (Graceful: torso + separate arm model)
                if (slot == 4 && hideArms) {
                    boolean arms = false, any = false;
                    for (int m : new int[]{d.maleModel0, d.maleModel1, d.maleModel2})
                        if (m > 0 && store.getIndex(IndexType.MODELS).getArchive(m) != null) {
                            any = true;
                            arms |= hasArms(model(store, m));
                        }
                    if (any && !arms && Math.max(d.maleModel0, Math.max(d.maleModel1, d.maleModel2)) >= CUSTOM_MODEL)
                        flagProblems.add(String.format("item %d %s: hide_arms but its worn models have no arm geometry", id, name));
                }
                // rule 3b: a custom body with full sleeves must hide the arm kit, or the player's own arms are drawn
                // inside the sleeves and poke through them (Draconic platebody)
                if (slot == 4 && !hideArms && d.maleModel0 >= CUSTOM_MODEL && store.getIndex(IndexType.MODELS).getArchive(d.maleModel0) != null) {
                    ModelDefinition bm = model(store, d.maleModel0);
                    int[] l = labels(bm);
                    int armFaces = 0;
                    for (int f = 0; f < bm.faceCount; f++) if (l[bm.faceIndices1[f]] >= 17 && l[bm.faceIndices1[f]] <= 26) armFaces++;
                    if (armFaces * 4 >= bm.faceCount)
                        flagProblems.add(String.format("item %d %s: full sleeves (%d of %d faces on the arms) but no hide_arms",
                                id, name, armFaces, bm.faceCount));
                }
                for (int m : new int[]{d.maleModel0, d.maleModel1, d.maleModel2, d.femaleModel0, d.femaleModel1, d.femaleModel2}) {
                    if (m <= 0) continue;
                    modelBudget.merge(m, BUDGET.getOrDefault(slot, 600), Math::min);
                    if (slot == 4) modelIsBody.put(m, true);
                    if (slot == 3 || slot == 5) modelIsHeld.put(m, true);
                    modelSlot.putIfAbsent(m, slot);
                    modelUsers.computeIfAbsent(m, k -> new ArrayList<>()).add(id + " " + name);
                }
            }
            int over = 0, layered = 0, written = 0, osrs = 0;
            for (Map.Entry<Integer, Integer> e : modelBudget.entrySet()) {
                int id = e.getKey(), budget = e.getValue();
                if (store.getIndex(IndexType.MODELS).getArchive(id) == null || HAND_BUILT.contains(id)) continue;
                ModelDefinition m = model(store, id);
                boolean overBudget = m.faceCount > budget;
                // the layering rules are for ripped exports that put every face on ONE layer (Draconic body all 4,
                // Crimson Warden all 10); real gear and its recolours layer per face on purpose and render fine
                boolean flat = uniformPriority(m);
                boolean noSkins = custom(id) && !hasSkins(m);
                boolean badPriority = custom(id) && maxPriority(m) > MAX_PRIORITY;
                // flat-layered custom legs take Torva platelegs' layer per body part
                // (Draconic legs were all on 4, over the re-layered body's torso on 3: their red band showed through it)
                int slotOf0 = modelSlot.getOrDefault(id, -1);
                // legs only: a body's torso sits on 3, so flat legs above Torva's 1 paint over its lower plates
                boolean flatPart = flat && custom(id) && !noSkins && slotOf0 == 7 && maxPriority(m) > 1;
                boolean badCape = custom(id) && !noSkins && slotOf0 == 1 && capeMislayered(m);
                boolean badTorso = flat && modelIsBody.getOrDefault(id, false) && (torsoTooHigh(m) || armsTooLow(m));
                // every custom weapon / shield face on the top layer (recolours of real weapons included: a face left on
                // a low layer is painted over by the body or wings whatever its depth)
                boolean badHeld = custom(id) && modelIsHeld.getOrDefault(id, false) && heldTooLow(m);
                if (!overBudget && !badTorso && !badHeld && !noSkins && !badPriority && !flatPart && !badCape) continue;
                boolean custom = id >= CUSTOM_MODEL;
                if (!custom) { // real OSRS gear renders fine in game: counted, never changed
                    osrs++;
                    if (System.getProperty("listSkipped") != null)
                        System.out.printf("skipped model %d: %d faces (budget %d)%s -- %s%n", id, m.faceCount, budget, badTorso ? " TORSO" : "", modelUsers.get(id));
                    continue;
                }
                System.out.printf("%s model %d: %d faces (budget %d)%s%s -- %s%n", custom ? "CUSTOM" : "osrs  ", id, m.faceCount, budget,
                        overBudget ? " OVER" : "", (badTorso ? " BODY-LAYERS" : "") + (badHeld ? " HELD<10" : "") + (noSkins ? " NO-SKINS" : "")
                                + (badPriority ? " PRIORITY>11" : "") + (flatPart ? " FLAT-LAYER" : "") + (badCape ? " CAPE-LAYERS" : ""), modelUsers.get(id));
                if (overBudget) over++;
                if (badTorso || badHeld || noSkins || badPriority || flatPart || badCape) layered++;
                if (!fix || !custom) continue;
                ModelDefinition w = model(store, id);
                int slot = modelSlot.getOrDefault(id, -1);
                if (noSkins || badPriority) {
                    Integer ref = RIG_REFERENCE.get(slot);
                    if (ref != null)
                        rig(w, model(store, ref));
                    else if (slot == 3 || slot == 5)
                        rigHeld(w);
                    else
                        clampPriority(w);
                }
                if (flatPart && !noSkins) layerLike(w, model(store, RIG_REFERENCE.get(slot)));
                if (badCape) relayerCape(w);
                if (badTorso) relayerTorso(w);
                if (badHeld) raiseHeld(w);
                if (overBudget) w = DecimateModel.decimate(w, budget);
                if (w.packedVertexGroups == null && w.getVertexGroups() != null)
                    w.packedVertexGroups = TextureModelFaces.packVertexGroups(w.getVertexGroups(), w.vertexCount);
                byte[] enc = ModelType3Encoder.encode(w);
                ModelDefinition back = new ModelLoader().load(id, enc);
                if (back.faceCount != w.faceCount || !Arrays.equals(back.faceColors, w.faceColors)
                        || !Arrays.equals(back.faceTextures, w.faceTextures))
                    throw new IllegalStateException("model " + id + " round trip");
                Files.write(new File(out, id + ".dat").toPath(), enc);
                written++;
            }
            System.out.println("--- flag problems (item_info.json) ---");
            for (String f : flagProblems) System.out.println(f);
            System.out.printf("CUSTOM models over budget: %d, custom bodies / held items mis-layered: %d, flag problems: %d,"
                    + " real OSRS models skipped: %d%s%n", over, layered, flagProblems.size(), osrs, fix ? ", custom models fixed: " + written + " (in " + out + ")" : "");
        }
    }

    static ModelDefinition model(Store store, int id) throws Exception {
        Archive a = store.getIndex(IndexType.MODELS).getArchive(id);
        return new ModelLoader().load(id, a.decompress(store.getStorage().loadArchive(a)));
    }

    static int[] labels(ModelDefinition m) {
        int[] l = new int[m.vertexCount];
        Arrays.fill(l, -1);
        int[][] g = m.getVertexGroups();
        for (int i = 0; g != null && i < g.length; i++)
            if (g[i] != null) for (int v : g[i]) l[v] = i;
        return l;
    }

    static boolean hasArms(ModelDefinition m) {
        for (int l : labels(m)) if (l >= 17 && l <= 26) return true;
        return false;
    }

    static boolean torsoTooHigh(ModelDefinition m) {
        int[] l = labels(m);
        for (int f = 0; f < m.faceCount; f++) {
            int p = m.faceRenderPriorities == null ? m.priority : m.faceRenderPriorities[f];
            if (TORSO_GROUPS.contains(l[m.faceIndices1[f]]) && p >= TORSO_TOO_HIGH) return true;
        }
        return false;
    }

    static boolean custom(int modelId) {
        return modelId >= CUSTOM_MODEL;
    }

    static boolean hasSkins(ModelDefinition m) {
        for (int l : labels(m)) if (l >= 0) return true;
        return false;
    }

    static int maxPriority(ModelDefinition m) {
        if (m.faceRenderPriorities == null) return m.priority;
        int max = 0;
        for (byte p : m.faceRenderPriorities) max = Math.max(max, p);
        return max;
    }

    /** Labels from the nearest reference vertex; face priorities from the reference's most common one per label. */
    static void rig(ModelDefinition m, ModelDefinition ref) {
        int[] lab = nearestLabels(m, ref);
        m.faceRenderPriorities = votePriorities(m, ref, lab);
        m.packedVertexGroups = lab;
        m.priority = 0;
        refreshGroups(m);
    }

    /** Keeps the model's own skins; each face takes the reference's layer for the body part it sits on. */
    static void layerLike(ModelDefinition m, ModelDefinition ref) {
        m.faceRenderPriorities = votePriorities(m, ref, nearestLabels(m, ref));
        m.priority = 0;
    }

    static int[] nearestLabels(ModelDefinition m, ModelDefinition ref) {
        int[] refLabel = labels(ref);
        int[] lab = new int[m.vertexCount];
        for (int v = 0; v < m.vertexCount; v++) {
            long best = Long.MAX_VALUE;
            int bl = -1;
            for (int r = 0; r < ref.vertexCount; r++) {
                if (refLabel[r] < 0) continue;
                long dx = m.vertexX[v] - ref.vertexX[r], dy = m.vertexY[v] - ref.vertexY[r], dz = m.vertexZ[v] - ref.vertexZ[r];
                long d = dx * dx + dy * dy + dz * dz;
                if (d < best) { best = d; bl = refLabel[r]; }
            }
            lab[v] = bl;
        }
        return lab;
    }

    static byte[] votePriorities(ModelDefinition m, ModelDefinition ref, int[] lab) {
        int[] refLabel = labels(ref);
        Map<Integer, Map<Integer, Integer>> votes = new HashMap<>();
        for (int f = 0; f < ref.faceCount; f++) {
            int p = ref.faceRenderPriorities == null ? ref.priority : ref.faceRenderPriorities[f];
            votes.computeIfAbsent(refLabel[ref.faceIndices1[f]], k -> new HashMap<>()).merge(p, 1, Integer::sum);
        }
        int fallback = ref.faceRenderPriorities == null ? ref.priority : ref.faceRenderPriorities[0];
        byte[] pri = new byte[m.faceCount];
        for (int f = 0; f < m.faceCount; f++) {
            Map<Integer, Integer> v = votes.get(lab[m.faceIndices1[f]]);
            pri[f] = (byte) (v == null ? fallback : Collections.max(v.entrySet(), Map.Entry.comparingByValue()).getKey());
        }
        return pri;
    }

    /** A held item with no skins: every vertex on the hand, every face on the top layer. */
    static void rigHeld(ModelDefinition m) {
        int[] lab = new int[m.vertexCount];
        Arrays.fill(lab, HAND_LABEL);
        m.packedVertexGroups = lab;
        byte[] pri = new byte[m.faceCount];
        Arrays.fill(pri, (byte) HELD_PRIORITY);
        m.faceRenderPriorities = pri;
        m.priority = 0;
        refreshGroups(m);
    }

    static void clampPriority(ModelDefinition m) {
        byte[] pri = new byte[m.faceCount];
        for (int f = 0; f < m.faceCount; f++)
            pri[f] = (byte) Math.min(MAX_PRIORITY, m.faceRenderPriorities == null ? m.priority : m.faceRenderPriorities[f]);
        m.faceRenderPriorities = pri;
        m.priority = 0;
    }

    /** The skin table (getVertexGroups, read by DecimateModel) rebuilt from the new packed labels. */
    static void refreshGroups(ModelDefinition m) {
        m.computeAnimationTables();
    }

    /**
     * Rule 5, capes / wings: real capes (Fire, Infernal, Max) put the outer surface -- normals pointing back, away from
     * the body (+z) -- on CAPE_OUTER and the inner lining (normals toward the body) on CAPE_INNER, under the torso. From
     * the front the lining faces the camera and is drawn first, so the torso covers it; from behind the outer surface
     * is drawn over the torso's back. Ripped capes / wings model both sides on ONE layer: all high and the lining paints
     * over the chest (Draconic wings), all low and the torso's back paints over the cape (Owner cape). Real capes keep a
     * few lining faces high on purpose (collars, under 20%), so only a model with 30%+ of its lining high, or its outer
     * surface low, is re-layered.
     */
    static final int CAPE_OUTER = 5, CAPE_INNER = 1;

    /** The unit face normal, (b-a) x (c-a). */
    static double[] normal(ModelDefinition m, int f) {
        int a = m.faceIndices1[f], b = m.faceIndices2[f], c = m.faceIndices3[f];
        double ux = m.vertexX[b] - m.vertexX[a], uy = m.vertexY[b] - m.vertexY[a], uz = m.vertexZ[b] - m.vertexZ[a];
        double vx = m.vertexX[c] - m.vertexX[a], vy = m.vertexY[c] - m.vertexY[a], vz = m.vertexZ[c] - m.vertexZ[a];
        double nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
        double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
        return len == 0 ? new double[3] : new double[]{nx / len, ny / len, nz / len};
    }

    /**
     * The cape layer of one face: the outer surface (facing back, +z) and the collar / shoulder tops (facing up, -y)
     * over the torso; everything else -- the lining and the sideways edges of raised letters, trims and the hem -- under
     * it. Round 8 left the sideways edges on the outer layer: seen from the front they drew over the torso and legs (the
     * YouTube cape's letters showed mirrored on the stomach, the KBD cape's hem over the legs).
     */
    static int capeLayer(ModelDefinition m, int f) {
        double[] n = normal(m, f);
        if (n[2] > 0.3) return CAPE_OUTER;
        // an up-facing face is the collar only in the cape's top 12%: lower down it is the top edge of a raised letter
        // or trim, which a camera above sees from the front -- over the stomach if it were on the outer layer
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (int v = 0; v < m.vertexCount; v++) { minY = Math.min(minY, m.vertexY[v]); maxY = Math.max(maxY, m.vertexY[v]); }
        double y = (m.vertexY[m.faceIndices1[f]] + m.vertexY[m.faceIndices2[f]] + m.vertexY[m.faceIndices3[f]]) / 3.0;
        return n[1] < -0.6 && y < minY + 0.12 * (maxY - minY) ? CAPE_OUTER : CAPE_INNER;
    }

    static boolean capeMislayered(ModelDefinition m) {
        int wrong = 0;
        for (int f = 0; f < m.faceCount; f++)
            if ((m.faceRenderPriorities == null ? m.priority : m.faceRenderPriorities[f]) != capeLayer(m, f)) wrong++;
        return wrong * 20 > m.faceCount; // more than 5% off: real capes keep a few collar faces their own way
    }

    static void relayerCape(ModelDefinition m) {
        byte[] pri = new byte[m.faceCount];
        for (int f = 0; f < m.faceCount; f++) pri[f] = (byte) capeLayer(m, f);
        m.faceRenderPriorities = pri;
        m.priority = 0;
    }

    static boolean uniformPriority(ModelDefinition m) {
        if (m.faceRenderPriorities == null) return true;
        for (byte p : m.faceRenderPriorities) if (p != m.faceRenderPriorities[0]) return false;
        return true;
    }

    /** A held weapon / shield face below the top layer is painted over by a body's arms in the GPU plugin. */
    static boolean heldTooLow(ModelDefinition m) {
        for (int f = 0; f < m.faceCount; f++)
            if ((m.faceRenderPriorities == null ? m.priority : m.faceRenderPriorities[f]) < HELD_PRIORITY) return true;
        return false;
    }

    static void raiseHeld(ModelDefinition m) {
        byte[] pri = new byte[m.faceCount];
        for (int f = 0; f < m.faceCount; f++)
            pri[f] = (byte) Math.max(HELD_PRIORITY, m.faceRenderPriorities == null ? m.priority : m.faceRenderPriorities[f]);
        m.faceRenderPriorities = pri;
        m.priority = 0;
    }

    /** A body's arm faces (skin groups 17-26) must be on the top layer like Torva's, so they depth-sort with the weapon. */
    static boolean armsTooLow(ModelDefinition m) {
        int[] l = labels(m);
        for (int f = 0; f < m.faceCount; f++) {
            int g = l[m.faceIndices1[f]], p = m.faceRenderPriorities == null ? m.priority : m.faceRenderPriorities[f];
            if (g >= 17 && g <= 26 && p < ARM_PRIORITY) return true;
        }
        return false;
    }

    /** Torso / neck faces down to TORSO_PRIORITY, arm faces up to ARM_PRIORITY (Torva's layering); the rest keep theirs. */
    static void relayerTorso(ModelDefinition m) {
        int[] l = labels(m);
        byte[] pri = new byte[m.faceCount];
        for (int f = 0; f < m.faceCount; f++) {
            int p = m.faceRenderPriorities == null ? m.priority : m.faceRenderPriorities[f];
            int g = l[m.faceIndices1[f]];
            pri[f] = (byte) (TORSO_GROUPS.contains(g) ? Math.min(p, TORSO_PRIORITY) : g >= 17 && g <= 26 ? Math.max(p, ARM_PRIORITY) : p);
        }
        m.faceRenderPriorities = pri;
        m.priority = 0;
    }
}

import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.FrameDefinition;
import net.runelite.cache.definitions.FramemapDefinition;
import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.NpcDefinition;
import net.runelite.cache.definitions.SequenceDefinition;
import net.runelite.cache.definitions.loaders.FrameLoader;
import net.runelite.cache.definitions.loaders.FramemapLoader;
import net.runelite.cache.definitions.loaders.ModelLoader;
import net.runelite.cache.definitions.loaders.NpcLoader;
import net.runelite.cache.definitions.loaders.SequenceLoader;
import net.runelite.cache.fs.*;
import net.runelite.cache.index.FileData;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Read-only (2026-10-19, the custom boss batch): finds which existing OSRS rig a ripped, vertex-skinned NPC model was
// skinned for. Ripped models keep the label numbering of the NPC they were rigged from, so every cache NPC is scored by
// how much of the model's vertex weight sits on labels its own models use. The best NPC per skeleton is then checked
// for real: its stand / walk / attack / death frames are applied to the ripped mesh and the edge stretch measured
// (a matching rig moves whole limbs rigidly; a wrong one tears faces apart). Writes a frame strip per model.
// Attack / death animations come from the server's npcs/combat/*.json.
// Usage: FindBossRig <cache> <combatDir> <outDir> <top> <model.dat>...
public class FindBossRig {
    static Storage storage;
    static Index frameIndex, skeletonIndex, modelIndex;
    static Map<Integer, SequenceDefinition> seqs = new HashMap<>();
    static Map<Integer, Integer> groupSkeleton = new HashMap<>();
    static Map<Integer, ArchiveFiles> groupFiles = new HashMap<>();
    static Map<Integer, FramemapDefinition> maps = new HashMap<>();
    static Map<Integer, int[]> modelLabels = new HashMap<>();
    static Map<Integer, int[]> combatAnims = new HashMap<>(); // npc -> attack, defend, death
    static Set<Integer> cands = new LinkedHashSet<>(); // candidate mode: only these npcs, ranked by distortion

    public static void main(String[] args) throws Exception {
        File out = new File(args[2]);
        out.mkdirs();
        int top = args[3].matches("[0-9]+") ? Integer.parseInt(args[3]) : 3;
        if (args[3].startsWith("cands="))
            for (String c : args[3].substring(6).split(",")) cands.add(Integer.parseInt(c.trim()));
        loadCombat(new File(args[1]));
        try (Store store = new Store(new File(args[0]))) {
            store.load();
            storage = store.getStorage();
            frameIndex = store.getIndex(IndexType.ANIMATIONS);
            skeletonIndex = store.getIndex(IndexType.SKELETONS);
            modelIndex = store.getIndex(IndexType.MODELS);
            Archive seqArchive = store.getIndex(IndexType.CONFIGS).getArchive(ConfigType.SEQUENCE.getId());
            FileData[] seqFd = seqArchive.getFileData();
            List<byte[]> seqFiles = SpliceItemOption.splitChunks(seqArchive.decompress(storage.loadArchive(seqArchive)), seqFd.length);
            for (int i = 0; i < seqFd.length; i++)
                seqs.put(seqFd[i].getId(), new SequenceLoader().load(seqFd[i].getId(), seqFiles.get(i)));
            Archive npcArchive = store.getIndex(IndexType.CONFIGS).getArchive(ConfigType.NPC.getId());
            FileData[] npcFd = npcArchive.getFileData();
            List<byte[]> npcFiles = SpliceItemOption.splitChunks(npcArchive.decompress(storage.loadArchive(npcArchive)), npcFd.length);
            List<NpcDefinition> npcs = new ArrayList<>();
            for (int i = 0; i < npcFd.length; i++) {
                NpcDefinition d = new NpcLoader().load(npcFd[i].getId(), npcFiles.get(i));
                if (d.models != null && d.standingAnimation > 0 && skeletonOf(d.standingAnimation) >= 0)
                    npcs.add(d);
            }
            System.out.println("npcs with frame-based stand anims: " + npcs.size());
            if (args[3].startsWith("skel=")) {
                // every sequence on these skeletons, measured on the model: frames, stretch, movement
                for (String k : args[3].substring(5).split(",")) {
                    int skel = Integer.parseInt(k);
                    ModelDefinition m = new ModelLoader().load(0, Files.readAllBytes(new File(args[4]).toPath()));
                    for (int id : new TreeSet<>(seqs.keySet())) {
                        if (skeletonOf(id) != skel) continue;
                        double[] st = stretch(m, id, skel);
                        if (st != null)
                            System.out.printf("skel %d seq %-6d frames %-3d len %-4d str %.3f mov %.0f%n", skel, id, seqs.get(id).frameIDs.length,
                                    Arrays.stream(seqs.get(id).frameLengths).sum(), st[0], st[1]);
                    }
                }
                return;
            }
            if (args[3].startsWith("seqs=")) {
                // chosen sets on one model (2026-10-20): seqs=label:a,b,c;label2:d,e -> stretch per seq + one strip row per set
                File file = new File(args[4]);
                ModelDefinition m = new ModelLoader().load(0, Files.readAllBytes(file.toPath()));
                int[] rest = bounds(m);
                BufferedImage strip = null;
                for (String set : args[3].substring(5).split(";")) {
                    String[] p = set.split(":");
                    int[] anims = Arrays.stream(p[1].split(",")).mapToInt(Integer::parseInt).toArray();
                    StringBuilder sb = new StringBuilder();
                    for (int a : anims) {
                        double[] s = stretch(m, a, skeletonOf(a));
                        sb.append(s == null ? " " + a + "(missing)" : String.format(" %d(skel %d str %.3f mov %.0f)", a, skeletonOf(a), s[0], s[1]));
                    }
                    System.out.printf("%s %s:%s%n", file.getName(), p[0], sb);
                    BufferedImage row = renderRow(m, rest, anims, file.getName() + " " + p[0]);
                    strip = strip == null ? row : stack(strip, row);
                }
                ImageIO.write(strip, "png", new File(out, file.getName().replace(".dat", "") + "_seqs.png"));
                return;
            }
            for (int a = 4; a < args.length; a++)
                analyse(new File(args[a]), npcs, out, top);
        }
    }

    static void analyse(File file, List<NpcDefinition> npcs, File out, int top) throws Exception {
        ModelDefinition m = new ModelLoader().load(0, Files.readAllBytes(file.toPath()));
        int[] own = labels(m);
        int total = 0, used = 0;
        for (int c : own) { total += c; if (c > 0) used++; }
        System.out.printf("%n== %s: %d verts on %d labels (max label %d)%n", file.getName(), total, used, own.length - 1);
        // score every npc, keep the best per skeleton
        Map<Integer, double[]> bestPerSkel = new HashMap<>(); // skel -> score, npcId
        Map<Integer, NpcDefinition> byId = new HashMap<>();
        for (NpcDefinition d : npcs) {
            int[] theirs = new int[256];
            boolean ok = true;
            for (int mid : d.models) {
                int[] l = cachedLabels(mid);
                if (l == null) { ok = false; break; }
                for (int i = 0; i < l.length; i++) theirs[i] += l[i];
            }
            if (!ok) continue;
            double covered = 0;
            int theirUsed = 0, extra = 0;
            for (int i = 0; i < 256; i++) {
                if (theirs[i] > 0) { theirUsed++; if (i >= own.length || own[i] == 0) extra++; }
                if (i < own.length && own[i] > 0 && theirs[i] > 0) covered += own[i];
            }
            if (theirUsed == 0) continue;
            double score = covered / total - 0.5 * extra / theirUsed;
            if (!cands.isEmpty()) score = cands.contains(d.id) ? 1 : -1;
            int skel = skeletonOf(d.standingAnimation);
            double[] cur = bestPerSkel.get(skel);
            if (cur == null || score > cur[0]) bestPerSkel.put(skel, new double[]{score, d.id});
            byId.put(d.id, d);
        }
        List<Map.Entry<Integer, double[]>> ranked = new ArrayList<>(bestPerSkel.entrySet());
        ranked.sort((x, y) -> Double.compare(y.getValue()[0], x.getValue()[0]));
        if (m.getVertexGroups() == null) {
            System.out.println("  no vertex skins: cannot animate");
            return;
        }
        // re-rank the best-overlapping skeletons by the stand animation's real distortion (needs some movement)
        int[] rb0 = bounds(m);
        double height = Math.max(1, rb0[3] - rb0[2]);
        List<Object[]> scored = new ArrayList<>();
        for (int r = 0; r < ranked.size() && ranked.get(r).getValue()[0] > (cands.isEmpty() ? 0.4 : 0); r++) {
            int skel = ranked.get(r).getKey();
            NpcDefinition d = byId.get((int) ranked.get(r).getValue()[1]);
            double[] s = stretch(m, d.standingAnimation, skel);
            if (s == null || (cands.isEmpty() && s[0] > 0.15)) continue;
            int[] ca = combatAnims.getOrDefault(d.id, new int[]{-1, -1, -1});
            double worst = s[0], moved = s[1];
            int n = 0;
            for (int a : new int[]{d.walkingAnimation, ca[0], ca[2]}) {
                double[] t = a == d.standingAnimation ? null : stretch(m, a, skel);
                if (t == null) continue;
                worst = Math.max(worst, t[0]);
                moved = Math.max(moved, t[1]);
                n++;
            }
            if (moved < 0.15 * height && cands.isEmpty()) continue;
            if (moved < 0.10 * height) worst += 1; // barely moves: no evidence either way
            // fewer checked animations = less evidence: a small penalty
            scored.add(new Object[]{worst + 0.02 * (3 - n), ranked.get(r)});
        }
        scored.sort((x, y) -> Double.compare((double) x[0], (double) y[0]));
        ranked = new ArrayList<>();
        for (Object[] o : scored) ranked.add((Map.Entry<Integer, double[]>) o[1]);
        int[] restBounds = bounds(m);
        BufferedImage strip = null;
        double bestStretch = Double.MAX_VALUE;
        for (int r = 0; r < Math.min(top, ranked.size()); r++) {
            int skel = ranked.get(r).getKey();
            NpcDefinition d = byId.get((int) ranked.get(r).getValue()[1]);
            int[] ca = combatAnims.getOrDefault(d.id, new int[]{-1, -1, -1});
            int[] anims = {d.standingAnimation, d.walkingAnimation, ca[0], ca[2]};
            StringBuilder sb = new StringBuilder();
            double worst = 0, motion = 0;
            for (int k = 0; k < anims.length; k++) {
                double[] s = stretch(m, anims[k], skel);
                if (s == null) { sb.append(" -"); continue; }
                sb.append(String.format(" %d(str %.3f mov %.0f)", anims[k], s[0], s[1]));
                worst = Math.max(worst, s[0]);
                motion = Math.max(motion, s[1]);
            }
            System.out.printf("  #%d skel %-5d score %.3f npc %-6d %-28s size %d scale %d/%d anims[stand walk atk death]%s%n", r + 1, skel,
                    ranked.get(r).getValue()[0], d.id, d.name, d.size, d.widthScale, d.heightScale, sb);
            if (r < 3) {
                BufferedImage row = renderRow(m, restBounds, anims, d.id + " " + d.name);
                strip = strip == null ? row : stack(strip, row);
            }
        }
        if (strip != null) {
            File f = new File(out, file.getName().replace(".dat", "") + "_rig.png");
            ImageIO.write(strip, "png", f);
        }
    }

    // mean relative edge stretch (worst frame) and max vertex movement over the sequence's frames, or null when the
    // sequence is missing / for another skeleton
    static double[] stretch(ModelDefinition m, int anim, int skel) throws Exception {
        if (anim <= 0 || skeletonOf(anim) != skel) return null;
        SequenceDefinition s = seqs.get(anim);
        int n = m.vertexCount;
        int[] rx = m.vertexX.clone(), ry = m.vertexY.clone(), rz = m.vertexZ.clone();
        double worst = 0, move = 0;
        int step = Math.max(1, s.frameIDs.length / 10);
        for (int k = 0; k < s.frameIDs.length; k += step) {
            if (!apply(m, s.frameIDs[k])) continue;
            double sum = 0;
            int cnt = 0;
            for (int f = 0; f < m.faceCount; f++) {
                int[] v = {m.faceIndices1[f], m.faceIndices2[f], m.faceIndices3[f]};
                for (int e = 0; e < 3; e++) {
                    int a = v[e], b = v[(e + 1) % 3];
                    double r0 = dist(rx[a] - rx[b], ry[a] - ry[b], rz[a] - rz[b]);
                    double r1 = dist(m.vertexX[a] - m.vertexX[b], m.vertexY[a] - m.vertexY[b], m.vertexZ[a] - m.vertexZ[b]);
                    sum += Math.abs(r1 - r0) / Math.max(r0, 6);
                    cnt++;
                }
            }
            worst = Math.max(worst, sum / Math.max(1, cnt));
            for (int i = 0; i < n; i++)
                move = Math.max(move, dist(m.vertexX[i] - rx[i], m.vertexY[i] - ry[i], m.vertexZ[i] - rz[i]));
            m.resetAnim();
        }
        m.resetAnim();
        return new double[]{worst, move};
    }

    static double dist(double x, double y, double z) { return Math.sqrt(x * x + y * y + z * z); }

    static boolean apply(ModelDefinition m, int frameId) throws Exception {
        int group = frameId >>> 16, file = frameId & 0xffff;
        ArchiveFiles files = files(group);
        if (files == null) return false;
        FSFile f = files.findFile(file);
        if (f == null) return false;
        byte[] c = f.getContents();
        FramemapDefinition map = map((c[0] & 0xff) << 8 | c[1] & 0xff);
        if (map == null) return false;
        FrameDefinition frame = new FrameLoader().load(map, file, c);
        m.resetAnim();
        for (int i = 0; i < frame.translatorCount; i++) {
            int idx = frame.indexFrameIds[i];
            m.animate(map.types[idx], map.frameMaps[idx], frame.translator_x[i], frame.translator_y[i], frame.translator_z[i]);
        }
        return true;
    }

    static BufferedImage renderRow(ModelDefinition m, int[] rb, int[] anims, String title) throws Exception {
        int cw = 260, ch = 280;
        List<int[]> frames = new ArrayList<>(); // anim, frame index
        frames.add(new int[]{-1, 0});
        for (int a : anims) {
            if (a <= 0 || !seqs.containsKey(a) || seqs.get(a).frameIDs == null || seqs.get(a).frameIDs.length == 0) continue;
            int len = seqs.get(a).frameIDs.length;
            for (int q = 0; q < 3; q++) frames.add(new int[]{a, (len - 1) * q / 2});
        }
        BufferedImage row = new BufferedImage(cw * frames.size(), ch, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = row.createGraphics();
        double ext = Math.max(Math.max(rb[1] - rb[0], rb[3] - rb[2]), rb[5] - rb[4]) * 1.15;
        double s = 420.0 / Math.max(1, ext);
        int cx = (rb[0] + rb[1]) / 2, cy = (rb[2] + rb[3]) / 2, cz = (rb[4] + rb[5]) / 2;
        for (int i = 0; i < frames.size(); i++) {
            int[] fr = frames.get(i);
            if (fr[0] > 0) apply(m, seqs.get(fr[0]).frameIDs[fr[1]]);
            ModelDefinition c = copy(m);
            m.resetAnim();
            for (int v = 0; v < c.vertexCount; v++) {
                c.vertexX[v] = (int) ((c.vertexX[v] - cx) * s);
                c.vertexY[v] = (int) ((c.vertexY[v] - cy) * s) - 210;
                c.vertexZ[v] = (int) ((c.vertexZ[v] - cz) * s);
            }
            c.faceTextures = null;
            BufferedImage img = BuildChampionshipBoard.preview(c, new HashMap<>(), 35, 10);
            g.drawImage(img, i * cw, 14, cw, ch - 14, null);
            g.setColor(Color.WHITE);
            g.drawString(fr[0] < 0 ? "rest" : fr[0] + ":" + fr[1], i * cw + 3, 12);
        }
        g.setColor(Color.YELLOW);
        g.drawString(title, 3, ch - 4);
        g.dispose();
        return row;
    }

    static ModelDefinition copy(ModelDefinition m) {
        ModelDefinition c = new ModelDefinition();
        c.vertexCount = m.vertexCount;
        c.vertexX = m.vertexX.clone();
        c.vertexY = m.vertexY.clone();
        c.vertexZ = m.vertexZ.clone();
        c.faceCount = m.faceCount;
        c.faceIndices1 = m.faceIndices1;
        c.faceIndices2 = m.faceIndices2;
        c.faceIndices3 = m.faceIndices3;
        c.faceColors = m.faceColors;
        c.faceTransparencies = m.faceTransparencies;
        c.faceRenderTypes = m.faceRenderTypes;
        c.faceRenderPriorities = m.faceRenderPriorities;
        return c;
    }

    static BufferedImage stack(BufferedImage a, BufferedImage b) {
        BufferedImage s = new BufferedImage(Math.max(a.getWidth(), b.getWidth()), a.getHeight() + b.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = s.createGraphics();
        g.drawImage(a, 0, 0, null);
        g.drawImage(b, 0, a.getHeight(), null);
        g.dispose();
        return s;
    }

    static int[] bounds(ModelDefinition m) {
        int[] b = {Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE};
        for (int v = 0; v < m.vertexCount; v++) {
            b[0] = Math.min(b[0], m.vertexX[v]); b[1] = Math.max(b[1], m.vertexX[v]);
            b[2] = Math.min(b[2], m.vertexY[v]); b[3] = Math.max(b[3], m.vertexY[v]);
            b[4] = Math.min(b[4], m.vertexZ[v]); b[5] = Math.max(b[5], m.vertexZ[v]);
        }
        return b;
    }

    static int[] labels(ModelDefinition m) {
        int[][] g = m.getVertexGroups();
        int[] l = new int[g == null ? 1 : g.length];
        if (g != null) for (int i = 0; i < g.length; i++) l[i] = g[i] == null ? 0 : g[i].length;
        return l;
    }

    static int[] cachedLabels(int id) {
        if (modelLabels.containsKey(id)) return modelLabels.get(id);
        int[] l = null;
        try {
            Archive a = modelIndex.getArchive(id);
            if (a != null) {
                ModelDefinition m = new ModelLoader().load(id, a.decompress(storage.loadArchive(a)));
                if (m.getVertexGroups() != null) l = labels(m);
            }
        } catch (Exception ignored) {
        }
        modelLabels.put(id, l);
        return l;
    }

    static ArchiveFiles files(int group) {
        if (groupFiles.containsKey(group)) return groupFiles.get(group);
        ArchiveFiles files = null;
        try {
            Archive a = frameIndex.getArchive(group);
            if (a != null) files = a.getFiles(storage.loadArchive(a));
        } catch (Exception ignored) {
        }
        groupFiles.put(group, files);
        return files;
    }

    static FramemapDefinition map(int id) {
        if (maps.containsKey(id)) return maps.get(id);
        FramemapDefinition d = null;
        try {
            Archive a = skeletonIndex.getArchive(id);
            if (a != null) d = new FramemapLoader().load(id, a.decompress(storage.loadArchive(a)));
        } catch (Exception ignored) {
        }
        maps.put(id, d);
        return d;
    }

    static int skeletonOf(int anim) {
        SequenceDefinition s = seqs.get(anim);
        if (s == null || s.frameIDs == null || s.frameIDs.length == 0) return -1;
        int group = s.frameIDs[0] >>> 16;
        Integer k = groupSkeleton.get(group);
        if (k != null) return k;
        int skel = -1;
        ArchiveFiles f = files(group);
        if (f != null && !f.getFiles().isEmpty()) {
            byte[] c = f.getFiles().iterator().next().getContents();
            skel = (c[0] & 0xff) << 8 | c[1] & 0xff;
        }
        groupSkeleton.put(group, skel);
        return skel;
    }

    static void loadCombat(File dir) throws Exception {
        Pattern ids = Pattern.compile("\"ids\"\\s*:\\s*\\[([^\\]]*)\\]");
        for (File f : Objects.requireNonNull(dir.listFiles((d, n) -> n.endsWith(".json")))) {
            String t = new String(Files.readAllBytes(f.toPath()));
            Matcher mi = ids.matcher(t);
            List<int[]> starts = new ArrayList<>();
            while (mi.find()) starts.add(new int[]{mi.start(), mi.end()});
            for (int i = 0; i < starts.size(); i++) {
                String body = t.substring(starts.get(i)[1], i + 1 < starts.size() ? starts.get(i + 1)[0] : t.length());
                int[] a = {num(body, "attack_animation"), num(body, "defend_animation"), num(body, "death_animation")};
                Matcher mm = ids.matcher(t);
                mm.find(starts.get(i)[0]);
                for (String s : mm.group(1).split(",")) {
                    s = s.trim();
                    if (!s.isEmpty() && s.matches("\\d+")) combatAnims.putIfAbsent(Integer.parseInt(s), a);
                }
            }
        }
    }

    static int num(String body, String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*(-?\\d+)").matcher(body);
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }
}


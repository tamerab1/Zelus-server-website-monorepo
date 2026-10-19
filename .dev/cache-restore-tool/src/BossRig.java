import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.FrameDefinition;
import net.runelite.cache.definitions.FramemapDefinition;
import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.SequenceDefinition;
import net.runelite.cache.definitions.loaders.FrameLoader;
import net.runelite.cache.definitions.loaders.FramemapLoader;
import net.runelite.cache.definitions.loaders.ModelLoader;
import net.runelite.cache.definitions.loaders.SequenceLoader;
import net.runelite.cache.fs.*;
import net.runelite.cache.index.FileData;
import net.runelite.cache.io.OutputStream;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.util.List;

// Hand-authored animations for the custom bosses (2026-10-19). The ripped boss meshes either have no rig that matches an
// OSRS skeleton (dragon, wyvern, pincer beast, serpent) or a player rig whose wings never move (Astraeus, Malakor). This
// tool re-labels a model into parts and writes a NEW skeleton + frame group + sequences for it.
//
// Rig spec (one file per rig):
//   model <modelId>                      the cache model to rework (it is replaced in place)
//   ids <skeleton=frameGroup> <firstSeq> new skeleton / frame group id and the first sequence id (they must be free)
//   shade <colours> <x|y|z> <from> <to> <l from> <l to> [bands <axis> <period> <delta>] [hue h] [sat s]
//                                        re-lights flat colour areas before anything else (gradient + optional bands)
//   extend <baseSkeleton>                optional: keep the model's own rig and layer the bones below on top of the base
//                                        skeleton's frames (anims then name a base sequence)
//   part <label> all | box x0 x1 y0 y1 z0 z1 | labels a,b,.. | sphere x y z r | plane nx ny nz d   ('*' = open bound)
//        rules apply in order (a later rule overrides); "labels" matches the ORIGINAL label; "box"/"plane" can be
//        narrowed with "and labels a,b" at the end
//   bone <label> parent <label|root> pivot x y z [labels a,b,..]   one bone per part label; children move with their
//                                        parent; with "labels" the bone moves those original labels instead (no relabel)
//   anim <seqId-offset> <name> frames <n> len <cycles> [hold <cycles>] [base <baseSeq>] [speed <percent>] [subdiv <n>]
//     <bone|root> <rx|ry|rz|tx|ty|tz> sin <amp> <cycles> <phase>     amp in degrees / model units, phase in turns
//     <bone|root> <rx|ry|rz|tx|ty|tz> keys t:v t:v ...                smoothstep between keys, t in 0..1
//   end
// Model space: x right, y DOWN (negative y is up), +z = behind the npc. Rotation order is the client's: z, then x, then y.
// Every bone gets a pivot vertex of its own (faceless, own label) so its origin transform's centroid IS the pivot; bones
// are written child-first so each rotates in its rest pose before its parent carries it (an exact hierarchy).
// Modes:  view <cache> <spec> <out.png>             the segmentation (RigView)
//         preview <cache> <spec> <outDir>           every anim played like the client, a frame strip per anim
//         verify|apply <cache> <spec>               writes the model (replace), skeleton, frames, sequences
public class BossRig {
    static class Rule { int label; String kind; double[] v; Set<Integer> labels; Set<Integer> andLabels; }
    static class Bone { int label, parent = -1, pivotLabel; double[] pivot; Set<Integer> own; List<Integer> children = new ArrayList<>(); }
    static class Track { int bone; String chan; String kind; double[] a; double[][] keys; }
    static class Anim { int offset; String name; int frames, len, hold, speed = 100, subdiv = 1; int base = -1; List<Track> tracks = new ArrayList<>(); }
    static class Shade { Set<Integer> colours; int axis; double from, to, lFrom, lTo; int bandAxis = -1; double period, bandDelta; int hue = -1, sat = -1; }
    static class Spec {
        int model, skeleton, firstSeq, extend = -1;
        String mapFile;
        List<Shade> shades = new ArrayList<>();
        List<Rule> rules = new ArrayList<>();
        Map<Integer, Bone> bones = new LinkedHashMap<>();
        Bone root;
        List<Anim> anims = new ArrayList<>();
    }

    static final int ROOT = -1;

    public static void main(String[] args) throws Exception {
        String mode = args[0];
        Spec s = parse(new File(args[2]));
        try (Store store = new Store(new File(args[1]))) {
            store.load();
            ModelDefinition m = BuildTierPets.model(store, s.model);
            int[] orig = RigView.labels(m);
            int[] lab = relabel(m, orig, s.rules);
            if (mode.equals("view")) {
                RigView.render(m, lab, new File(args[3]));
                return;
            }
            Built b = build(store, s, m, orig, lab);
            if (mode.equals("preview")) {
                File dir = new File(args[3]);
                dir.mkdirs();
                preview(b, dir, s);
                return;
            }
            if (!mode.equals("verify") && !mode.equals("apply")) throw new IllegalArgumentException(mode);
            check(store, s, b);
            if (mode.equals("verify")) { System.out.println("VERIFY OK"); return; }
            write(store, s, b);
        }
    }

    // ------------------------------------------------------------------ spec

    static Spec parse(File f) throws Exception {
        Spec s = new Spec();
        Anim cur = null;
        for (String raw : Files.readAllLines(f.toPath())) {
            String line = raw.replaceAll("#.*", "").trim();
            if (line.isEmpty()) continue;
            String[] p = line.split("\\s+");
            switch (p[0]) {
                case "model" -> s.model = Integer.parseInt(p[1]);
                case "ids" -> { s.skeleton = Integer.parseInt(p[1]); s.firstSeq = Integer.parseInt(p[2]); }
                case "extend" -> s.extend = Integer.parseInt(p[1]);
                case "mapfile" -> s.mapFile = p[1]; // apply writes "<base seq> <new seq> <name>" per built anim here
                case "shade" -> {
                    // shade <colours> <axis x|y|z> <from> <to> <lightness at from> <lightness at to> [bands <axis> <period> <delta>] [hue h] [sat s]
                    Shade sh = new Shade();
                    sh.colours = ints(p[1]);
                    sh.axis = "xyz".indexOf(p[2]);
                    sh.from = Double.parseDouble(p[3]); sh.to = Double.parseDouble(p[4]);
                    sh.lFrom = Double.parseDouble(p[5]); sh.lTo = Double.parseDouble(p[6]);
                    for (int i = 7; i < p.length; i++) {
                        switch (p[i]) {
                            case "bands" -> { sh.bandAxis = "xyz".indexOf(p[i + 1]); sh.period = Double.parseDouble(p[i + 2]); sh.bandDelta = Double.parseDouble(p[i + 3]); i += 3; }
                            case "hue" -> sh.hue = Integer.parseInt(p[++i]);
                            case "sat" -> sh.sat = Integer.parseInt(p[++i]);
                            default -> throw new IllegalArgumentException("shade key " + p[i]);
                        }
                    }
                    s.shades.add(sh);
                }
                case "part" -> {
                    Rule r = new Rule();
                    r.label = Integer.parseInt(p[1]);
                    r.kind = p[2];
                    int end = p.length;
                    for (int i = 3; i < p.length; i++)
                        if (p[i].equals("and")) { r.andLabels = ints(p[i + 2]); end = i; break; }
                    if (r.kind.equals("labels")) r.labels = ints(p[3]);
                    else if (!r.kind.equals("all")) {
                        r.v = new double[end - 3];
                        for (int i = 3; i < end; i++) r.v[i - 3] = p[i].equals("*") ? Double.NaN : Double.parseDouble(p[i]);
                    }
                    s.rules.add(r);
                }
                case "bone" -> {
                    Bone b = new Bone();
                    b.label = p[1].equals("root") ? ROOT : Integer.parseInt(p[1]);
                    b.parent = p[3].equals("root") ? ROOT : Integer.parseInt(p[3]);
                    b.pivot = new double[]{Double.parseDouble(p[5]), Double.parseDouble(p[6]), Double.parseDouble(p[7])};
                    // "labels a,b,.." : the bone moves these ORIGINAL labels (no relabelling, so the model keeps working
                    // with its own skeleton's sequences); otherwise it moves the part label equal to its id
                    b.own = p.length > 9 && p[8].equals("labels") ? ints(p[9]) : new HashSet<>(Set.of(b.label));
                    if (b.label == ROOT) s.root = b; else s.bones.put(b.label, b);
                }
                case "anim" -> {
                    cur = new Anim();
                    cur.offset = Integer.parseInt(p[1]);
                    cur.name = p[2];
                    for (int i = 3; i + 1 < p.length; i += 2) {
                        int v = Integer.parseInt(p[i + 1]);
                        switch (p[i]) {
                            case "frames" -> cur.frames = v;
                            case "len" -> cur.len = v;
                            case "hold" -> cur.hold = v;
                            case "base" -> cur.base = v;
                            case "speed" -> cur.speed = v; // percent: base frame lengths scaled by 100 / speed
                            case "subdiv" -> cur.subdiv = v; // each base frame split in v (the layered bones move between base poses)
                            default -> throw new IllegalArgumentException("anim key " + p[i]);
                        }
                    }
                    s.anims.add(cur);
                }
                case "end" -> cur = null;
                default -> {
                    if (cur == null) throw new IllegalArgumentException("track outside an anim: " + line);
                    Track t = new Track();
                    t.bone = p[0].equals("root") ? ROOT : Integer.parseInt(p[0]);
                    t.chan = p[1];
                    t.kind = p[2];
                    if (t.kind.equals("sin")) {
                        t.a = new double[]{Double.parseDouble(p[3]), Double.parseDouble(p[4]), Double.parseDouble(p[5]),
                                p.length > 6 ? Double.parseDouble(p[6]) : 0};
                    } else if (t.kind.equals("keys")) {
                        t.keys = new double[p.length - 3][];
                        for (int i = 3; i < p.length; i++) {
                            String[] kv = p[i].split(":");
                            t.keys[i - 3] = new double[]{Double.parseDouble(kv[0]), Double.parseDouble(kv[1])};
                        }
                    } else throw new IllegalArgumentException("track kind " + t.kind);
                    cur.tracks.add(t);
                }
            }
        }
        if (s.root == null) throw new IllegalArgumentException("a 'bone root parent root pivot x y z' line is required");
        return s;
    }

    static Set<Integer> ints(String csv) {
        Set<Integer> r = new HashSet<>();
        for (String x : csv.split(",")) {
            if (x.contains("-")) {
                String[] ab = x.split("-");
                for (int i = Integer.parseInt(ab[0]); i <= Integer.parseInt(ab[1]); i++) r.add(i);
            } else r.add(Integer.parseInt(x));
        }
        return r;
    }

    static int[] relabel(ModelDefinition m, int[] orig, List<Rule> rules) {
        int[] lab = orig.clone();
        for (Rule r : rules)
            for (int v = 0; v < m.vertexCount; v++) {
                if (r.andLabels != null && !r.andLabels.contains(orig[v])) continue;
                double x = m.vertexX[v], y = m.vertexY[v], z = m.vertexZ[v];
                boolean hit = switch (r.kind) {
                    case "all" -> true;
                    case "labels" -> r.labels.contains(orig[v]);
                    case "box" -> in(x, r.v[0], r.v[1]) && in(y, r.v[2], r.v[3]) && in(z, r.v[4], r.v[5]);
                    case "sphere" -> sq(x - r.v[0]) + sq(y - r.v[1]) + sq(z - r.v[2]) <= sq(r.v[3]);
                    case "plane" -> r.v[0] * x + r.v[1] * y + r.v[2] * z > r.v[3];
                    default -> throw new IllegalArgumentException("part rule " + r.kind);
                };
                if (hit) lab[v] = r.label;
            }
        return lab;
    }

    static boolean in(double v, double lo, double hi) {
        return (Double.isNaN(lo) || v >= lo) && (Double.isNaN(hi) || v <= hi);
    }

    static double sq(double v) { return v * v; }

    // ------------------------------------------------------------------ build

    static class Built {
        ModelDefinition model;              // relabelled, pivot vertices added, vertex groups computed
        byte[] skeleton;
        FramemapDefinition framemap;
        List<byte[]> frames = new ArrayList<>();
        Map<Integer, byte[]> seqs = new TreeMap<>();
        Map<Integer, int[]> seqFrames = new TreeMap<>(); // seq -> frame file ids (1-based)
        Map<Integer, String> baseOf = new TreeMap<>();      // seq -> "<base seq> <name>" (extend anims)
    }

    static class Xf { int type; int[] labels; int bone; String role; }

    static Built build(Store store, Spec s, ModelDefinition m, int[] orig, int[] lab) throws Exception {
        Built out = new Built();
        shade(m, s.shades);
        // pivot vertices: one per bone + root, labels from 255 downwards
        int next = 255;
        Set<Integer> used = new TreeSet<>();
        for (int l : lab) used.add(l);
        List<Bone> all = new ArrayList<>(s.bones.values());
        all.add(s.root);
        for (Bone b : all) {
            while (used.contains(next)) next--;
            b.pivotLabel = next;
            used.add(next);
        }
        for (Bone b : s.bones.values()) {
            if (b.parent != ROOT) {
                Bone p = s.bones.get(b.parent);
                if (p == null) throw new IllegalArgumentException("bone " + b.label + ": parent " + b.parent + " is not a bone");
                p.children.add(b.label);
            }
            boolean any = false;
            for (int l : lab) if (b.own.contains(l)) { any = true; break; }
            if (!any) throw new IllegalArgumentException("bone " + b.label + " has no vertices");
        }
        int n0 = m.vertexCount, n = n0 + all.size();
        m.vertexX = Arrays.copyOf(m.vertexX, n);
        m.vertexY = Arrays.copyOf(m.vertexY, n);
        m.vertexZ = Arrays.copyOf(m.vertexZ, n);
        int[] packed = Arrays.copyOf(lab, n);
        for (int i = 0; i < all.size(); i++) {
            Bone b = all.get(i);
            m.vertexX[n0 + i] = (int) Math.round(b.pivot[0]);
            m.vertexY[n0 + i] = (int) Math.round(b.pivot[1]);
            m.vertexZ[n0 + i] = (int) Math.round(b.pivot[2]);
            packed[n0 + i] = b.pivotLabel;
        }
        m.vertexCount = n;
        m.packedVertexGroups = packed;
        m.computeAnimationTables();
        m.packedVertexGroups = packed.clone();
        out.model = m;

        // transforms: bones child-first (post-order), [base skeleton], root last
        List<Xf> pre = new ArrayList<>();
        for (Bone b : s.bones.values()) if (b.parent == ROOT) postOrder(s, b, pre);
        List<Xf> post = new ArrayList<>();
        Set<Integer> everything = new TreeSet<>(used);
        everything.removeIf(l -> isPivot(all, l));
        addBone(post, s.root, everything);

        FramemapDefinition baseMap = null;
        byte[] baseRaw = null;
        if (s.extend >= 0) {
            Archive a = store.getIndex(IndexType.SKELETONS).getArchive(s.extend);
            baseRaw = a.decompress(store.getStorage().loadArchive(a));
            baseMap = new FramemapLoader().load(s.extend, baseRaw);
        }
        // skeleton bytes
        List<int[]> labelsOut = new ArrayList<>();
        List<Integer> typesOut = new ArrayList<>();
        for (Xf x : pre) { typesOut.add(x.type); labelsOut.add(x.labels); }
        if (baseMap != null) {
            // a new part made from vertices of original label L rides on every base transform (not origins) that moves L
            Map<Integer, Set<Integer>> riders = new HashMap<>();
            for (int v = 0; v < n0; v++)
                if (lab[v] != orig[v]) riders.computeIfAbsent(orig[v], k -> new TreeSet<>()).add(lab[v]);
            for (int i = 0; i < baseMap.length; i++) {
                Set<Integer> ls = new TreeSet<>();
                for (int l : baseMap.frameMaps[i]) ls.add(l);
                if (baseMap.types[i] != 0)
                    for (int l : baseMap.frameMaps[i]) ls.addAll(riders.getOrDefault(l, Set.of()));
                typesOut.add(baseMap.types[i]);
                labelsOut.add(ls.stream().mapToInt(Integer::intValue).toArray());
            }
        }
        for (Xf x : post) { typesOut.add(x.type); labelsOut.add(x.labels); }
        if (typesOut.size() > 255) throw new IllegalStateException("too many transforms: " + typesOut.size());
        OutputStream sk = new OutputStream();
        sk.writeByte(typesOut.size());
        for (int t : typesOut) sk.writeByte(t);
        for (int[] l : labelsOut) { if (l.length > 255) throw new IllegalStateException("label list too long"); sk.writeByte(l.length); }
        for (int[] l : labelsOut) for (int x : l) sk.writeByte(x);
        out.skeleton = sk.flip();
        out.framemap = new FramemapLoader().load(s.skeleton, out.skeleton);

        // frames
        Map<Integer, SequenceDefinition> seqDefs = new HashMap<>();
        Archive seqArc = store.getIndex(IndexType.CONFIGS).getArchive(ConfigType.SEQUENCE.getId());
        FileData[] sfd = seqArc.getFileData();
        List<byte[]> sfiles = SpliceItemOption.splitChunks(seqArc.decompress(store.getStorage().loadArchive(seqArc)), sfd.length);
        for (Anim a : s.anims) {
            int[] lens;
            List<byte[]> baseFrames = new ArrayList<>();
            if (a.base >= 0) {
                if (baseMap == null) throw new IllegalArgumentException("anim " + a.name + " has a base but the rig has no 'extend'");
                SequenceDefinition bs = null;
                for (int i = 0; i < sfd.length; i++) if (sfd[i].getId() == a.base) bs = new SequenceLoader().load(a.base, sfiles.get(i));
                if (bs == null || bs.frameIDs == null || bs.frameIDs.length == 0 || !onSkeleton(store, bs, s.extend)) {
                    // e.g. a weapon whose stance is a skeletal (animaya) sequence: no classic frames to layer onto
                    System.out.println("skip " + a.name + ": base seq " + a.base + " has no frames on skeleton " + s.extend);
                    continue;
                }
                lens = bs.frameLengths.clone();
                for (int fid : bs.frameIDs) {
                    Archive fa = store.getIndex(IndexType.ANIMATIONS).getArchive(fid >>> 16);
                    FSFile ff = fa.getFiles(store.getStorage().loadArchive(fa)).findFile(fid & 0xffff);
                    byte[] c = ff.getContents();
                    if (((c[0] & 0xff) << 8 | c[1] & 0xff) != s.extend) throw new IllegalArgumentException("base seq " + a.base + " is not on skeleton " + s.extend);
                    baseFrames.add(c);
                }
                if (a.hold > 0) lens[lens.length - 1] = a.hold; // before splitting: a 20000-cycle death pose must not be halved
                if (a.subdiv > 1) { // e.g. the player stand holds each pose 21 cycles: split so wings / hover update smoothly
                    int[] l2 = new int[lens.length * a.subdiv];
                    List<byte[]> b2 = new ArrayList<>();
                    for (int f = 0; f < lens.length; f++)
                        for (int k = 0; k < a.subdiv; k++) {
                            int share = lens[f] / a.subdiv + (k < lens[f] % a.subdiv ? 1 : 0);
                            l2[f * a.subdiv + k] = Math.max(1, share);
                            b2.add(baseFrames.get(f));
                        }
                    lens = l2;
                    baseFrames = b2;
                }
                a.frames = lens.length;
            } else {
                lens = new int[a.frames];
                Arrays.fill(lens, a.len);
            }
            for (int f = 0; f < lens.length; f++) lens[f] = Math.max(1, (int) Math.round(lens[f] * 100.0 / a.speed));
            if (a.hold > 0) lens[lens.length - 1] = a.hold;
            int total = Arrays.stream(lens).sum(), acc = 0;
            int[] ids = new int[a.frames];
            for (int f = 0; f < a.frames; f++) {
                double t = (double) acc / total; // phase of this frame in the loop
                if (a.hold > 0) t = a.frames == 1 ? 1 : (double) f / (a.frames - 1); // one-shots end exactly on their last key
                acc += lens[f];
                OutputStream o = new OutputStream();
                o.writeShort(s.skeleton);
                o.writeByte(typesOut.size());
                OutputStream data = new OutputStream();
                List<int[]> vals = new ArrayList<>();
                for (Xf x : pre) vals.add(value(a, x, t));
                for (int[] v : vals) o.writeByte(flags(v));
                byte[] baseData = new byte[0];
                if (baseMap != null) {
                    byte[] c = a.base >= 0 ? baseFrames.get(f) : null;
                    if (c != null) {
                        int cnt = c[2] & 0xff; // a frame may list fewer transforms than its skeleton: the rest are unset
                        if (cnt > baseMap.length) throw new IllegalStateException("base frame has more transforms than its skeleton");
                        for (int i = 0; i < baseMap.length; i++) o.writeByte(i < cnt ? c[3 + i] : 0);
                        baseData = Arrays.copyOfRange(c, 3 + cnt, c.length);
                    } else for (int i = 0; i < baseMap.length; i++) o.writeByte(0);
                }
                List<int[]> pvals = new ArrayList<>();
                for (Xf x : post) pvals.add(value(a, x, t));
                for (int[] v : pvals) o.writeByte(flags(v));
                for (int[] v : vals) writeVals(o, v);
                o.writeBytes(baseData);
                for (int[] v : pvals) writeVals(o, v);
                out.frames.add(o.flip());
                ids[f] = out.frames.size();
            }
            int seqId = s.firstSeq + a.offset;
            OutputStream q = new OutputStream();
            q.writeByte(1);
            q.writeShort(a.frames);
            for (int f = 0; f < a.frames; f++) q.writeShort(lens[f]);
            for (int f = 0; f < a.frames; f++) q.writeShort(ids[f]);
            for (int f = 0; f < a.frames; f++) q.writeShort(s.skeleton);
            q.writeByte(0);
            out.seqs.put(seqId, q.flip());
            out.seqFrames.put(seqId, ids);
            if (a.base >= 0) out.baseOf.put(seqId, a.base + " " + a.name);
            SequenceDefinition sd = new SequenceLoader().load(seqId, out.seqs.get(seqId));
            if (sd.frameIDs.length != a.frames) throw new IllegalStateException("seq " + seqId + " decodes wrong");
        }
        // every frame must decode against the new skeleton
        for (int i = 0; i < out.frames.size(); i++) new FrameLoader().load(out.framemap, i + 1, out.frames.get(i));
        return out;
    }

    /** Re-lights flat colour areas: lightness follows a gradient along one axis, optionally alternating bands (folds, ribs). */
    static void shade(ModelDefinition m, List<Shade> shades) {
        int[][] P = {m.vertexX, m.vertexY, m.vertexZ};
        int changed = 0;
        for (int f = 0; f < m.faceCount; f++) {
            int c = m.faceColors[f] & 0xffff;
            for (Shade sh : shades) {
                if (!sh.colours.contains(c)) continue;
                int a = m.faceIndices1[f], b = m.faceIndices2[f], d = m.faceIndices3[f];
                double pos = (P[sh.axis][a] + P[sh.axis][b] + P[sh.axis][d]) / 3.0;
                double u = Math.max(0, Math.min(1, (pos - sh.from) / (sh.to - sh.from)));
                double l = sh.lFrom + (sh.lTo - sh.lFrom) * u;
                if (sh.bandAxis >= 0) {
                    double bp = (P[sh.bandAxis][a] + P[sh.bandAxis][b] + P[sh.bandAxis][d]) / 3.0;
                    if (Math.floorMod((int) Math.floor(bp / sh.period), 2) == 0) l += sh.bandDelta;
                }
                int hue = sh.hue >= 0 ? sh.hue : c >> 10 & 63, sat = sh.sat >= 0 ? sh.sat : c >> 7 & 7;
                int li = (int) Math.max(2, Math.min(126, Math.round(l)));
                m.faceColors[f] = (short) (hue << 10 | sat << 7 | li);
                changed++;
                break;
            }
        }
        if (!shades.isEmpty()) System.out.println("shaded " + changed + " faces");
    }

    static boolean onSkeleton(Store store, SequenceDefinition seq, int skeleton) {
        try {
            for (int fid : seq.frameIDs) {
                Archive fa = store.getIndex(IndexType.ANIMATIONS).getArchive(fid >>> 16);
                if (fa == null) return false;
                FSFile ff = fa.getFiles(store.getStorage().loadArchive(fa)).findFile(fid & 0xffff);
                if (ff == null) return false;
                byte[] c = ff.getContents();
                if (((c[0] & 0xff) << 8 | c[1] & 0xff) != skeleton) return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static boolean isPivot(List<Bone> all, int l) {
        for (Bone b : all) if (b.pivotLabel == l) return true;
        return false;
    }

    static void postOrder(Spec s, Bone b, List<Xf> out) {
        for (int c : b.children) postOrder(s, s.bones.get(c), out);
        Set<Integer> sub = new TreeSet<>();
        subtree(s, b, sub);
        addBone(out, b, sub);
    }

    static void subtree(Spec s, Bone b, Set<Integer> acc) {
        acc.addAll(b.own);
        for (int c : b.children) { acc.add(s.bones.get(c).pivotLabel); subtree(s, s.bones.get(c), acc); }
    }

    static void addBone(List<Xf> out, Bone b, Set<Integer> labels) {
        int[] ls = labels.stream().mapToInt(Integer::intValue).toArray();
        Xf o = new Xf(); o.type = 0; o.labels = new int[]{b.pivotLabel}; o.bone = b.label; o.role = "origin"; out.add(o);
        Xf r = new Xf(); r.type = 2; r.labels = ls; r.bone = b.label; r.role = "rot"; out.add(r);
        Xf t = new Xf(); t.type = 1; t.labels = ls; t.bone = b.label; t.role = "trans"; out.add(t);
    }

    static int[] value(Anim a, Xf x, double t) {
        if (x.role.equals("origin")) return new int[]{0, 0, 0};
        String[] ch = x.role.equals("rot") ? new String[]{"rx", "ry", "rz"} : new String[]{"tx", "ty", "tz"};
        int[] v = new int[3];
        for (int i = 0; i < 3; i++) {
            double sum = 0;
            for (Track tr : a.tracks)
                if (tr.bone == x.bone && tr.chan.equals(ch[i])) sum += eval(tr, t);
            v[i] = x.role.equals("rot") ? (int) Math.round(sum * 256 / 360.0) : (int) Math.round(sum);
        }
        if (x.role.equals("rot"))
            for (int i = 0; i < 3; i++) { // the client reads an angle & 255: keep it in -128..127 so it encodes in one byte
                v[i] = ((v[i] % 256) + 256) % 256;
                if (v[i] >= 128) v[i] -= 256;
            }
        return v;
    }

    static double eval(Track tr, double t) {
        if (tr.kind.equals("sin")) return tr.a[0] * Math.sin(2 * Math.PI * (t * tr.a[1] + tr.a[2])) + tr.a[3];
        double[][] k = tr.keys;
        if (t <= k[0][0]) return k[0][1];
        for (int i = 1; i < k.length; i++)
            if (t <= k[i][0]) {
                double u = (t - k[i - 1][0]) / Math.max(1e-9, k[i][0] - k[i - 1][0]);
                u = u * u * (3 - 2 * u);
                return k[i - 1][1] + (k[i][1] - k[i - 1][1]) * u;
            }
        return k[k.length - 1][1];
    }

    static int flags(int[] v) { return (v[0] != 0 ? 1 : 0) | (v[1] != 0 ? 2 : 0) | (v[2] != 0 ? 4 : 0); }

    static void writeVals(OutputStream o, int[] v) {
        for (int x : v) if (x != 0) BuildLocSpinAnims.smart(o, x);
    }

    // ------------------------------------------------------------------ preview / write

    static void play(ModelDefinition m, FramemapDefinition fm, byte[] frame, int id) {
        FrameDefinition f = new FrameLoader().load(fm, id, frame);
        m.resetAnim();
        for (int i = 0; i < f.translatorCount; i++) {
            int idx = f.indexFrameIds[i];
            m.animate(fm.types[idx], fm.frameMaps[idx], f.translator_x[i], f.translator_y[i], f.translator_z[i]);
        }
    }

    static void preview(Built b, File dir, Spec s) throws Exception {
        ModelDefinition m = b.model;
        int[] rb = FindBossRig.bounds(m);
        for (Anim a : s.anims) {
            int seq = s.firstSeq + a.offset;
            int[] ids = b.seqFrames.get(seq);
            int shots = Math.min(8, ids.length);
            int cw = 300, ch = 300;
            BufferedImage strip = new BufferedImage(cw * (shots + 1), ch * 2, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = strip.createGraphics();
            double ext = Math.max(Math.max(rb[1] - rb[0], rb[3] - rb[2]), rb[5] - rb[4]) * 1.45;
            double sc = 420.0 / Math.max(1, ext);
            int cx = (rb[0] + rb[1]) / 2, cy = (rb[2] + rb[3]) / 2, cz = (rb[4] + rb[5]) / 2;
            for (int k = 0; k <= shots; k++) {
                int fi = k == 0 ? -1 : (ids.length - 1) * (k - 1) / Math.max(1, shots - 1);
                if (fi >= 0) play(m, b.framemap, b.frames.get(ids[fi] - 1), ids[fi]);
                for (int view = 0; view < 2; view++) {
                    ModelDefinition c = FindBossRig.copy(m);
                    for (int v = 0; v < c.vertexCount; v++) {
                        c.vertexX[v] = (int) ((c.vertexX[v] - cx) * sc);
                        c.vertexY[v] = (int) ((c.vertexY[v] - cy) * sc) - 210;
                        c.vertexZ[v] = (int) ((c.vertexZ[v] - cz) * sc);
                    }
                    c.faceTextures = null;
                    BufferedImage img = BuildChampionshipBoard.preview(c, new HashMap<>(), view == 0 ? 205 : 115, 12);
                    g.drawImage(img, k * cw, view * ch, cw, ch, null);
                }
                m.resetAnim();
                g.setColor(Color.WHITE);
                g.drawString(fi < 0 ? "rest" : a.name + " " + fi + "/" + (ids.length - 1), k * cw + 4, 14);
            }
            g.dispose();
            File f = new File(dir, s.model + "_" + a.name + ".png");
            ImageIO.write(strip, "png", f);
            System.out.println("wrote " + f);
        }
    }

    static void check(Store store, Spec s, Built b) {
        Index skels = store.getIndex(IndexType.SKELETONS), frames = store.getIndex(IndexType.ANIMATIONS);
        if (skels.getArchive(s.skeleton) != null) throw new IllegalStateException("skeleton " + s.skeleton + " is taken");
        if (frames.getArchive(s.skeleton) != null) throw new IllegalStateException("frame group " + s.skeleton + " is taken");
        if (store.getIndex(IndexType.MODELS).getArchive(s.model) == null) throw new IllegalStateException("model " + s.model + " missing");
        System.out.printf("model %d: %d vertices (+%d pivots), skeleton %d: %d transforms, %d frames, seqs %s%n", s.model,
                b.model.vertexCount, s.bones.size() + 1, s.skeleton, b.framemap.length, b.frames.size(), b.seqs.keySet());
    }

    static void write(Store store, Spec s, Built b) throws Exception {
        Storage storage = store.getStorage();
        // model: re-encoded with the new labels + pivot vertices, replacing the archive in place
        ModelDefinition m = b.model;
        m.resetAnim();
        byte[] enc = ModelType3Encoder.encode(m);
        ModelDefinition back = new ModelLoader().load(s.model, enc);
        if (back.vertexCount != m.vertexCount || back.faceCount != m.faceCount) throw new IllegalStateException("model round trip");
        Index models = store.getIndex(IndexType.MODELS);
        Archive ma = models.getArchive(s.model);
        net.runelite.cache.fs.Container mc = new net.runelite.cache.fs.Container(0, -1);
        mc.compress(enc, null);
        storage.store(models.getId(), s.model, mc.data);
        ma.setCrc(mc.crc);
        ma.setRevision(ma.getRevision() + 1);
        ma.setCompressedSize(mc.data.length);
        ma.setDecompressedSize(enc.length);
        BuildLocSpinAnims.writeRefTable(storage, models);
        Index skels = store.getIndex(IndexType.SKELETONS), frames = store.getIndex(IndexType.ANIMATIONS), cfg = store.getIndex(IndexType.CONFIGS);
        BuildLocSpinAnims.addArchive(storage, skels, s.skeleton, List.of(b.skeleton), new int[]{0}, 0);
        int[] fids = new int[b.frames.size()];
        for (int i = 0; i < fids.length; i++) fids[i] = i + 1;
        BuildLocSpinAnims.addArchive(storage, frames, s.skeleton, b.frames, fids, 2);
        BuildLocSpinAnims.writeRefTable(storage, skels);
        BuildLocSpinAnims.writeRefTable(storage, frames);
        BuildLocSpinAnims.writeConfig(storage, cfg, cfg.getArchive(ConfigType.SEQUENCE.getId()), b.seqs, true);
        BuildLocSpinAnims.writeRefTable(storage, cfg);
        if (s.mapFile != null) {
            StringBuilder sb = new StringBuilder();
            for (var e : b.baseOf.entrySet()) sb.append(e.getValue().split(" ")[0]).append(' ').append(e.getKey()).append(' ').append(e.getValue().split(" ")[1]).append(System.lineSeparator());
            Files.writeString(new File(s.mapFile).toPath(), sb.toString());
            System.out.println("wrote " + b.baseOf.size() + " base -> seq mappings to " + s.mapFile);
        }
        System.out.println("APPLY OK: model " + s.model + ", skeleton/frames " + s.skeleton + ", seqs " + b.seqs.keySet());
    }
}

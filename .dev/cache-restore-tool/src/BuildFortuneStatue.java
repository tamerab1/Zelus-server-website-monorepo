import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.*;
import net.runelite.cache.definitions.loaders.*;
import net.runelite.cache.fs.*;
import net.runelite.cache.index.FileData;
import net.runelite.cache.io.OutputStream;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import java.util.List;

// The Statue of Fortune (2026-10-17): an obsidian reliquary shrine built from code, replacing the camel statue (loc 10961).
//   plinth   two octagonal obsidian tiers with lava seams + a small third tier, 2x2 footprint
//   spires   four obsidian claws on the diagonals, gold collars, leaning in over the centre
//   column   octagonal obsidian column with a gold capital
//   chalice  floating gold chalice + flame tongues            skin label 1: yaw spin + bob
//   shards   ring of 8 floating rune shards                    skin label 2: counter-rotation
// Three states, each its own model / sequence / loc (the server swaps the loc):
//   dormant  62393  model 61136  seq 16203  dull seams, tarnished chalice, no flame, still (2-unit bob)
//   charged  62394  model 61137  seq 16204  orange seams, small flame, 1 turn per loop, shards -1
//   blessed  62395  model 61138  seq 16205  bright seams, full flame, 2 turns per loop, shards -1
// Shared skeleton 16102 [0 {1}, 2 {1}, 1 {1}, 0 {2}, 2 {2}]; frame groups 16102-16104 (128 frames, 2 cycles each = 5.12 s).
// Loc records are written from scratch: model (opcode 1, type 10), name, 2x2, interact flag copied from 10961, anim,
// ops Sacrifice / Blessings / Benefactors / Inspect. Sorted insert into the loc group (ids are below its max).
// verify writes previews and checks everything decodes and animates; apply also writes. Usage: verify|apply <cache> <previewDir>
public class BuildFortuneStatue {
    enum State {
        DORMANT(62393, 61136, 16203, 16102, 1, -1, 3),
        CHARGED(62394, 61137, 16204, 16103, 1, -1, 4),
        BLESSED(62395, 61138, 16205, 16104, 2, -1, 6);
        final int loc, model, seq, frameGroup, chaliceTurns, shardTurns, bob;
        State(int loc, int model, int seq, int frameGroup, int chaliceTurns, int shardTurns, int bob) {
            this.loc = loc; this.model = model; this.seq = seq; this.frameGroup = frameGroup;
            this.chaliceTurns = chaliceTurns; this.shardTurns = shardTurns; this.bob = bob;
        }
    }

    static final int SKELETON = 16102, FRAMES = 128, FRAME_LEN = 2, CAMEL = 10961;
    /** 3x3 footprint: exactly the home knight statue's old tiles (south-west 3104,3506); the geometry below is laid out for 2x2 and scaled by SCALE. */
    static final int SIZE = 3;
    static final double SCALE = 1.26;
    static final String NAME = "Statue of Fortune";
    static final String[] OPS = {"Sacrifice", "Blessings", "Benefactors", "Inspect", null};

    static short hsl(int h, int s, int l) { return (short) (h << 10 | s << 7 | l); }

    static final short OBSIDIAN = hsl(51, 2, 13), OBSIDIAN_L = hsl(51, 2, 20), OBSIDIAN_D = hsl(51, 2, 8);

    // ---- builder ----
    static final List<int[]> verts = new ArrayList<>();   // x, y, z, label
    static final List<int[]> faces = new ArrayList<>();   // a, b, c, colour, renderType, priority
    static int label = 0, prio = 0, shading = 1;

    static void reset() { verts.clear(); faces.clear(); label = 0; prio = 0; shading = 1; }

    static int v(double x, double y, double z) {
        verts.add(new int[]{(int) Math.round(x), (int) Math.round(y), (int) Math.round(z), label});
        return verts.size() - 1;
    }

    /** Triangle turned so its normal points away from (cx, cy, cz): every primitive here is convex. */
    static void tri(int a, int b, int c, short colour, double cx, double cy, double cz) {
        int[] A = verts.get(a), B = verts.get(b), C = verts.get(c);
        double ux = B[0] - A[0], uy = B[1] - A[1], uz = B[2] - A[2], vx = C[0] - A[0], vy = C[1] - A[1], vz = C[2] - A[2];
        double nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
        double ox = (A[0] + B[0] + C[0]) / 3.0 - cx, oy = (A[1] + B[1] + C[1]) / 3.0 - cy, oz = (A[2] + B[2] + C[2]) / 3.0 - cz;
        if (nx * ox + ny * oy + nz * oz < 0) { int t = b; b = c; c = t; }
        faces.add(new int[]{a, b, c, colour, shading, prio});
    }

    static int[] ring(int n, double r, double y, double phase, double cx, double cz) {
        int[] out = new int[n];
        for (int i = 0; i < n; i++) {
            double t = phase + 2 * Math.PI * i / n;
            out[i] = v(cx + r * Math.cos(t), y, cz + r * Math.sin(t));
        }
        return out;
    }

    /**
     * n-sided frustum from (r0 at y0) to (r1 at y1). Sides share their ring vertices (smooth around the ring); each cap
     * gets its own copy of the ring, so the side/cap edge stays a hard edge under smooth shading. capLo/capHi = 0 skips.
     */
    static void frustum(int n, double r0, double y0, double r1, double y1, short side, short capLo, short capHi, double cx, double cz) {
        double phase = Math.PI / n;
        int[] lo = ring(n, r0, y0, phase, cx, cz), hi = ring(n, r1, y1, phase, cx, cz);
        double my = (y0 + y1) / 2;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            tri(lo[i], lo[j], hi[j], side, cx, my, cz);
            tri(lo[i], hi[j], hi[i], side, cx, my, cz);
        }
        int keep = shading;
        shading = 1;
        if (capLo != 0) {
            int[] c = ring(n, r0, y0, phase, cx, cz);
            for (int i = 1; i < n - 1; i++) tri(c[0], c[i], c[i + 1], capLo, cx, my, cz);
        }
        if (capHi != 0) {
            int[] c = ring(n, r1, y1, phase, cx, cz);
            for (int i = 1; i < n - 1; i++) tri(c[0], c[i], c[i + 1], capHi, cx, my, cz);
        }
        shading = keep;
    }

    static void frustum(int n, double r0, double y0, double r1, double y1, short side, short cap, double cx, double cz) {
        frustum(n, r0, y0, r1, y1, side, cap, cap, cx, cz);
    }

    /** A plinth tier: obsidian sides, a lava stripe as the top band of the same side (no overlap), lighter top cap. */
    static void tier(double rBottom, double y0, double rTop, double y1, double stripe, short body, short seam, short top) {
        double t = (Math.abs(y1 - y0) - stripe) / Math.abs(y1 - y0);
        double rMid = rBottom + (rTop - rBottom) * t, yMid = y0 + (y1 - y0) * t;
        frustum(8, rBottom, y0, rMid, yMid, body, body, (short) 0, 0, 0);
        frustum(8, rMid, yMid, rTop, y1, seam, (short) 0, top, 0, 0);
    }

    /** Convex prism between two squares (centre, half-size, y), e.g. a leaning spire segment. */
    static void taper(double x0, double z0, double h0, double y0, double x1, double z1, double h1, double y1, short side, short cap) {
        int[] lo = {v(x0 - h0, y0, z0 - h0), v(x0 + h0, y0, z0 - h0), v(x0 + h0, y0, z0 + h0), v(x0 - h0, y0, z0 + h0)};
        int[] hi = {v(x1 - h1, y1, z1 - h1), v(x1 + h1, y1, z1 - h1), v(x1 + h1, y1, z1 + h1), v(x1 - h1, y1, z1 + h1)};
        double cx = (x0 + x1) / 2, cy = (y0 + y1) / 2, cz = (z0 + z1) / 2;
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) % 4;
            tri(lo[i], lo[j], hi[j], side, cx, cy, cz);
            tri(lo[i], hi[j], hi[i], side, cx, cy, cz);
        }
        tri(lo[0], lo[1], lo[2], cap, cx, cy, cz); tri(lo[0], lo[2], lo[3], cap, cx, cy, cz);
        if (h1 > 0) { tri(hi[0], hi[1], hi[2], cap, cx, cy, cz); tri(hi[0], hi[2], hi[3], cap, cx, cy, cz); }
    }

    /** A tapered square shaft cut into colours.length stacked sections, colour ramping from the first to the last. */
    static void ramp(double x0, double z0, double h0, double y0, double x1, double z1, double h1, double y1, short[] colours) {
        int n = colours.length;
        for (int i = 0; i < n; i++) {
            double t0 = (double) i / n, t1 = (double) (i + 1) / n;
            taper(x0 + (x1 - x0) * t0, z0 + (z1 - z0) * t0, h0 + (h1 - h0) * t0, y0 + (y1 - y0) * t0,
                    x0 + (x1 - x0) * t1, z0 + (z1 - z0) * t1, h0 + (h1 - h0) * t1, y0 + (y1 - y0) * t1, colours[i], colours[i]);
        }
    }

    /** A pyramid from a square base (half-size h at y0) to a tip, for flame tongues. */
    static void tongue(double bx, double bz, double h, double y0, double tx, double ty, double tz, short colour) {
        int[] b = {v(bx - h, y0, bz - h), v(bx + h, y0, bz - h), v(bx + h, y0, bz + h), v(bx - h, y0, bz + h)};
        int t = v(tx, ty, tz);
        double cx = (4 * bx + tx) / 5, cy = (4 * y0 + ty) / 5, cz = (4 * bz + tz) / 5;
        for (int i = 0; i < 4; i++) tri(b[i], b[(i + 1) % 4], t, colour, cx, cy, cz);
        tri(b[0], b[1], b[2], colour, cx, cy, cz); tri(b[0], b[2], b[3], colour, cx, cy, cz);
    }

    static void octahedron(double x, double y, double z, double w, double h, short colour) {
        int top = v(x, y - h, z), bot = v(x, y + h, z);
        int[] m = {v(x + w, y, z), v(x, y, z + w), v(x - w, y, z), v(x, y, z - w)};
        for (int i = 0; i < 4; i++) {
            tri(m[i], m[(i + 1) % 4], top, colour, x, y, z);
            tri(m[i], m[(i + 1) % 4], bot, colour, x, y, z);
        }
    }

    static ModelDefinition build(State s) {
        reset();
        shading = 0;
        // Portal Nexus palette (zelus_home_reskin.txt): white marble, gilded gold, deep teal core, bright cyan glow
        short seam = s == State.DORMANT ? hsl(31, 7, 30) : s == State.CHARGED ? hsl(30, 6, 62) : hsl(30, 5, 92);
        short seamD = s == State.DORMANT ? hsl(31, 7, 22) : s == State.CHARGED ? hsl(31, 7, 46) : hsl(30, 6, 74);
        short gold = s == State.DORMANT ? hsl(8, 4, 54) : hsl(8, 5, 62), goldL = s == State.DORMANT ? hsl(8, 4, 74) : hsl(9, 4, 88);
        short shard = s == State.DORMANT ? hsl(31, 7, 36) : s == State.CHARGED ? hsl(30, 6, 70) : hsl(30, 5, 100);
        short body = hsl(20, 1, 88), top = hsl(20, 1, 104), dark = hsl(31, 7, 28), spire = hsl(31, 7, 30), spireCap = hsl(31, 6, 44);

        // plinth, inside the 2x2 footprint (+-128): three tiers, each with a lava stripe along its top edge
        tier(124, 0, 118, -18, 4, body, seam, top);
        tier(96, -18, 90, -38, 4, body, seam, top);
        tier(60, -38, 54, -50, 3, dark, seamD, top);

        // column + gold capital
        frustum(8, 24, -50, 21, -132, body, (short) 0, (short) 0, 0, 0);
        frustum(8, 23, -84, 23, -90, seam, (short) 0, (short) 0, 0, 0);
        frustum(8, 22, -132, 40, -146, gold, goldL, 0, 0);

        // four teal crystal claw spires on the diagonals, bent in over the centre: smooth-shaded, each in stacked
        // sections ramping from deep teal at the root to bright cyan at the tip, with a forged gold collar
        shading = 0;
        short[] shaft = {hsl(31, 7, 12), hsl(31, 7, 18), hsl(31, 7, 25), hsl(30, 7, 33)};
        short[] claw = {hsl(30, 7, 40), hsl(30, 6, 52), hsl(30, 6, 66), hsl(30, 5, 86)};
        short goldD = s == State.DORMANT ? hsl(8, 4, 40) : hsl(8, 5, 46);
        for (int k = 0; k < 4; k++) {
            double a = Math.PI / 4 + k * Math.PI / 2, c = Math.cos(a), sn = Math.sin(a);
            ramp(82 * c, 82 * sn, 13, -38, 78 * c, 78 * sn, 9.5, -184, shaft);
            taper(78 * c, 78 * sn, 12, -180, 78 * c, 78 * sn, 12, -188, goldD, goldD);   // forged band
            taper(78 * c, 78 * sn, 12, -188, 78 * c, 78 * sn, 9, -194, goldL, goldL);    // bright chamfer
            ramp(78 * c, 78 * sn, 9, -194, 50 * c, 50 * sn, 2, -286, claw);
        }

        // chalice + flame (label 1)
        label = 1;
        shading = 0;
        frustum(12, 19, -166, 14, -171, gold, goldL, 0, 0);
        frustum(8, 5, -171, 5, -198, gold, goldL, 0, 0);
        frustum(8, 9, -182, 9, -187, goldL, goldL, 0, 0);
        frustum(12, 9, -198, 30, -232, gold, gold, s == State.DORMANT ? dark : seam, 0, 0);
        frustum(12, 32, -231, 32, -236, goldL, goldL, 0, 0);
        shading = 1;
        if (s != State.DORMANT) {
            double scale = s == State.CHARGED ? 0.55 : 1.0;
            short outer = s == State.CHARGED ? hsl(31, 7, 52) : hsl(30, 6, 70), core = s == State.CHARGED ? hsl(30, 5, 84) : hsl(30, 3, 112);
            tongue(0, 0, 14, -234, 0, -234 - 86 * scale, 0, core);
            for (int k = 0; k < 4; k++) {
                double a = k * Math.PI / 2 + Math.PI / 4;
                tongue(12 * Math.cos(a), 12 * Math.sin(a), 8, -234, 23 * Math.cos(a), -234 - 52 * scale, 23 * Math.sin(a), outer);
            }
        }

        // rune shards (label 2)
        label = 2;
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4;
            octahedron(66 * Math.cos(a), -210 + (k % 2 == 0 ? -7 : 7), 66 * Math.sin(a), 7, 14, shard);
        }
        label = 0;
        return toModel(s.model);
    }

    static ModelDefinition toModel(int id) {
        ModelDefinition d = new ModelDefinition();
        d.id = id;
        d.vertexCount = verts.size();
        d.vertexX = new int[d.vertexCount]; d.vertexY = new int[d.vertexCount]; d.vertexZ = new int[d.vertexCount];
        d.packedVertexGroups = new int[d.vertexCount];
        for (int i = 0; i < d.vertexCount; i++) {
            int[] p = verts.get(i);
            d.vertexX[i] = (int) Math.round(p[0] * SCALE); d.vertexY[i] = (int) Math.round(p[1] * SCALE);
            d.vertexZ[i] = (int) Math.round(p[2] * SCALE); d.packedVertexGroups[i] = p[3];
        }
        d.faceCount = faces.size();
        d.faceIndices1 = new int[d.faceCount]; d.faceIndices2 = new int[d.faceCount]; d.faceIndices3 = new int[d.faceCount];
        d.faceColors = new short[d.faceCount];
        d.faceRenderTypes = new byte[d.faceCount];
        d.faceRenderPriorities = new byte[d.faceCount];
        for (int i = 0; i < d.faceCount; i++) {
            int[] f = faces.get(i);
            d.faceIndices1[i] = f[0]; d.faceIndices2[i] = f[1]; d.faceIndices3[i] = f[2];
            d.faceColors[i] = (short) f[3]; d.faceRenderTypes[i] = (byte) f[4]; d.faceRenderPriorities[i] = (byte) f[5];
        }
        d.priority = 0;
        return d;
    }

    // ---- animation ----
    static byte[] skeleton() {
        OutputStream o = new OutputStream();
        int[] types = {0, 2, 1, 0, 2}, labels = {1, 1, 1, 2, 2};
        o.writeByte(types.length);
        for (int t : types) o.writeByte(t);
        for (int t : types) o.writeByte(1);
        for (int l : labels) o.writeByte(l);
        return o.flip();
    }

    static byte[] frame(State s, int i) {
        int cy = (i * 256 * s.chaliceTurns / FRAMES) & 255, sy = (i * 256 * s.shardTurns / FRAMES) & 255;
        int dy = (int) Math.round(-s.bob * Math.sin(2 * Math.PI * i / FRAMES));
        OutputStream o = new OutputStream();
        o.writeShort(SKELETON);
        o.writeByte(5);
        o.writeByte(0);
        o.writeByte(cy != 0 ? 2 : 0);
        o.writeByte(dy != 0 ? 2 : 0);
        o.writeByte(0);
        o.writeByte(sy != 0 ? 2 : 0);
        if (cy != 0) BuildLocSpinAnims.smart(o, cy);
        if (dy != 0) BuildLocSpinAnims.smart(o, dy);
        if (sy != 0) BuildLocSpinAnims.smart(o, sy);
        return o.flip();
    }

    static byte[] sequence(State s) {
        OutputStream o = new OutputStream();
        o.writeByte(1);
        o.writeShort(FRAMES);
        for (int i = 0; i < FRAMES; i++) o.writeShort(FRAME_LEN);
        for (int i = 0; i < FRAMES; i++) o.writeShort(i + 1);
        for (int i = 0; i < FRAMES; i++) o.writeShort(s.frameGroup);
        o.writeByte(2);
        o.writeShort(FRAMES);
        o.writeByte(0);
        return o.flip();
    }

    static byte[] loc(State s, int interact) {
        OutputStream o = new OutputStream();
        o.writeByte(1); o.writeByte(1); o.writeShort(s.model); o.writeByte(10);
        o.writeByte(2); o.writeString(NAME);
        o.writeByte(14); o.writeByte(SIZE);
        o.writeByte(15); o.writeByte(SIZE);
        o.writeByte(19); o.writeByte(interact);
        o.writeByte(24); o.writeShort(s.seq);
        for (int i = 0; i < OPS.length; i++) if (OPS[i] != null) { o.writeByte(30 + i); o.writeString(OPS[i]); }
        o.writeByte(0);
        return o.flip();
    }

    public static void main(String[] args) throws Exception {
        boolean rebuild = args[0].startsWith("rebuild");
        boolean apply = args[0].equals("apply") || args[0].equals("rebuild");
        if (!List.of("verify", "apply", "rebuild-verify", "rebuild").contains(args[0])) throw new IllegalArgumentException("verify|apply|rebuild-verify|rebuild");
        File previewDir = new File(args[2]);
        previewDir.mkdirs();
        try (Store store = new Store(new File(args[1]))) {
            store.load();
            Storage storage = store.getStorage();
            Index models = store.getIndex(IndexType.MODELS), frames = store.getIndex(IndexType.ANIMATIONS),
                    skels = store.getIndex(IndexType.SKELETONS), cfg = store.getIndex(IndexType.CONFIGS);
            Archive seqArc = cfg.getArchive(ConfigType.SEQUENCE.getId()), locArc = cfg.getArchive(ConfigType.OBJECT.getId());
            Set<Integer> seqIds = BuildLocSpinAnims.ids(seqArc), locIds = BuildLocSpinAnims.ids(locArc);

            // the camel's interact flag (opcode 19), read from its raw record
            Map<Integer, byte[]> locRaw = ScanLocAnims.files(store, locArc);
            int interact = -1;
            byte[] camel = locRaw.get(CAMEL);
            ObjectDefinition camelDef = new ObjectLoader().load(CAMEL, camel);
            interact = camelDef.getInteractType();
            System.out.println("camel 10961: interactType=" + interact + " blocksProjectile=" + camelDef.isBlocksProjectile() + " solid=" + camelDef.getInteractType());

            if (!rebuild && skels.getArchive(SKELETON) != null) throw new IllegalStateException("skeleton " + SKELETON + " taken");
            byte[] sk = skeleton();
            FramemapDefinition fm = new FramemapLoader().load(SKELETON, sk);

            Map<Integer, byte[]> modelBytes = new TreeMap<>(), seqBytes = new TreeMap<>(), locBytes = new TreeMap<>();
            Map<Integer, List<byte[]>> frameBytes = new TreeMap<>();
            for (State s : State.values()) {
                boolean exists = models.getArchive(s.model) != null;
                if (exists != rebuild || (frames.getArchive(s.frameGroup) != null) != rebuild || seqIds.contains(s.seq) != rebuild || locIds.contains(s.loc) != rebuild)
                    throw new IllegalStateException(s + ": ids must " + (rebuild ? "already exist (rebuild)" : "be free (apply)"));

                ModelDefinition m = build(s);
                byte[] enc = ModelType3Encoder.encode(m);
                ModelDefinition back = new ModelLoader().load(s.model, enc);
                int[] backSkins = back.packedVertexGroups != null ? back.packedVertexGroups
                        : TextureModelFaces.packVertexGroups(back.getVertexGroups(), back.vertexCount);
                if (back.vertexCount != m.vertexCount || back.faceCount != m.faceCount || !Arrays.equals(backSkins, m.packedVertexGroups))
                    throw new IllegalStateException("model " + s.model + " round trip mismatch");
                int ymin = 0;
                for (int y : m.vertexY) ymin = Math.min(ymin, y);
                System.out.printf("%s: model %d %d verts %d faces, height %d%n", s, s.model, m.vertexCount, m.faceCount, -ymin);
                modelBytes.put(s.model, enc);

                List<byte[]> fs = new ArrayList<>();
                for (int i = 0; i < FRAMES; i++) {
                    byte[] f = frame(s, i);
                    FrameDefinition fd = new FrameLoader().load(fm, i + 1, f);
                    if (i == FRAMES / 8) simulate(s, back, backSkins, fm, fd);
                    fs.add(f);
                }
                frameBytes.put(s.frameGroup, fs);
                byte[] seq = sequence(s);
                SequenceDefinition sd = new SequenceLoader().load(s.seq, seq);
                if (sd.frameStep != FRAMES || (sd.frameIDs[5] >>> 16) != s.frameGroup) throw new IllegalStateException("seq " + s.seq);
                seqBytes.put(s.seq, seq);
                byte[] lb = loc(s, interact);
                ObjectDefinition od = new ObjectLoader().load(s.loc, lb);
                if (!NAME.equals(od.getName()) || od.getAnimationID() != s.seq || od.getObjectModels()[0] != s.model || od.getSizeX() != SIZE)
                    throw new IllegalStateException("loc " + s.loc + " decodes wrong");
                System.out.println("  loc " + s.loc + " ops=" + Arrays.toString(od.getActions()) + " anim=" + od.getAnimationID());
                locBytes.put(s.loc, lb);

                for (double yaw : new double[]{30, 120})
                    ImageIO.write(BuildChampionshipBoard.preview(back, new HashMap<>(), yaw, 14), "png",
                            new File(previewDir, String.format("fortune_%s_%d.png", s.name().toLowerCase(), (int) yaw)));
            }
            if (!apply) { System.out.println("VERIFY OK (dry run), previews in " + previewDir); return; }
            if (rebuild) {
                for (Map.Entry<Integer, byte[]> e : modelBytes.entrySet())
                    replaceArchive(storage, models, e.getKey(), List.of(e.getValue()), new int[]{0}, 0);
                for (Map.Entry<Integer, List<byte[]>> e : frameBytes.entrySet()) {
                    int[] fids = new int[e.getValue().size()];
                    for (int i = 0; i < fids.length; i++) fids[i] = i + 1;
                    replaceArchive(storage, frames, e.getKey(), e.getValue(), fids, 2);
                }
                BuildLocSpinAnims.writeRefTable(storage, models);
                BuildLocSpinAnims.writeRefTable(storage, frames);
                replaceConfig(storage, cfg, seqArc, seqBytes);
                replaceConfig(storage, cfg, locArc, locBytes);
                BuildLocSpinAnims.writeRefTable(storage, cfg);
                System.out.println("REBUILD OK");
                return;
            }

            for (Map.Entry<Integer, byte[]> e : modelBytes.entrySet())
                BuildLocSpinAnims.addArchive(storage, models, e.getKey(), List.of(e.getValue()), new int[]{0}, 0);
            BuildLocSpinAnims.addArchive(storage, skels, SKELETON, List.of(sk), new int[]{0}, 0);
            for (Map.Entry<Integer, List<byte[]>> e : frameBytes.entrySet()) {
                int[] fids = new int[e.getValue().size()];
                for (int i = 0; i < fids.length; i++) fids[i] = i + 1;
                BuildLocSpinAnims.addArchive(storage, frames, e.getKey(), e.getValue(), fids, 2);
            }
            BuildLocSpinAnims.writeRefTable(storage, models);
            BuildLocSpinAnims.writeRefTable(storage, skels);
            BuildLocSpinAnims.writeRefTable(storage, frames);
            BuildLocSpinAnims.writeConfig(storage, cfg, seqArc, seqBytes, true);
            insertSorted(storage, cfg, locArc, locBytes);
            BuildLocSpinAnims.writeRefTable(storage, cfg);
            System.out.println("APPLY OK");
        }
    }

    static void simulate(State s, ModelDefinition base, int[] lab, FramemapDefinition fm, FrameDefinition f) {
        ModelDefinition m = copy(base);
        m.packedVertexGroups = lab.clone();
        m.computeAnimationTables();
        for (int t = 0; t < f.translatorCount; t++) {
            int ti = f.indexFrameIds[t];
            m.animate(fm.types[ti], fm.frameMaps[ti], f.translator_x[t], f.translator_y[t], f.translator_z[t]);
        }
        int moved0 = 0;
        for (int v = 0; v < m.vertexCount; v++)
            if (lab[v] == 0 && (m.vertexX[v] != base.vertexX[v] || m.vertexY[v] != base.vertexY[v] || m.vertexZ[v] != base.vertexZ[v])) moved0++;
        if (moved0 > 0) throw new IllegalStateException(s + ": " + moved0 + " static vertices moved");
        double[] c = new double[2];
        int n = 0;
        for (int v = 0; v < m.vertexCount; v++) if (lab[v] == 1) { c[0] += m.vertexX[v]; c[1] += m.vertexZ[v]; n++; }
        System.out.printf("  %s frame %d: static parts untouched, chalice centre x/z drift %.2f/%.2f%n", s, FRAMES / 8, c[0] / n, c[1] / n);
    }

    static ModelDefinition copy(ModelDefinition b) {
        ModelDefinition m = new ModelLoader().load(b.id, ModelType3Encoder.encode(b));
        return m;
    }

    /** Merges new loc records into the group in id order (the ids sit below the group's max). */
    static void insertSorted(Storage storage, Index cfg, Archive arc, Map<Integer, byte[]> entries) throws Exception {
        byte[] dec = arc.decompress(storage.loadArchive(arc));
        FileData[] fd = arc.getFileData();
        List<byte[]> chunks = SpliceItemOption.splitChunks(dec, fd.length);
        List<FileData> outFd = new ArrayList<>();
        List<byte[]> outChunks = new ArrayList<>();
        Iterator<Map.Entry<Integer, byte[]>> it = entries.entrySet().iterator();
        Map.Entry<Integer, byte[]> next = it.hasNext() ? it.next() : null;
        for (int i = 0; i < fd.length; i++) {
            while (next != null && next.getKey() < fd[i].getId()) {
                FileData n = new FileData(); n.setId(next.getKey()); n.setNameHash(-1);
                outFd.add(n); outChunks.add(next.getValue());
                next = it.hasNext() ? it.next() : null;
            }
            if (next != null && next.getKey() == fd[i].getId()) throw new IllegalStateException("loc " + next.getKey() + " exists");
            outFd.add(fd[i]); outChunks.add(chunks.get(i));
        }
        while (next != null) {
            FileData n = new FileData(); n.setId(next.getKey()); n.setNameHash(-1);
            outFd.add(n); outChunks.add(next.getValue());
            next = it.hasNext() ? it.next() : null;
        }
        for (int i = 1; i < outFd.size(); i++)
            if (outFd.get(i).getId() < outFd.get(i - 1).getId()) throw new IllegalStateException("loc ids out of order at " + i);
        arc.setFileData(outFd.toArray(new FileData[0]));
        byte[] data = SpliceItemOption.joinChunks(outChunks);
        Container c = new Container(arc.getCompression(), -1);
        c.compress(data, null);
        storage.store(cfg.getId(), arc.getArchiveId(), c.data);
        arc.setCrc(c.crc);
        arc.setRevision(arc.getRevision() + 1);
        arc.setCompressedSize(c.data.length);
        arc.setDecompressedSize(data.length);
        System.out.println("  config " + arc.getArchiveId() + ": " + entries.size() + " locs inserted in order");
    }

    /** Overwrites an existing archive in place with a bumped revision (clients refetch it through the crc). */
    static void replaceArchive(Storage storage, Index idx, int id, List<byte[]> files, int[] fileIds, int compression) throws Exception {
        Archive a = idx.getArchive(id);
        if (a == null) throw new IllegalStateException("index " + idx.getId() + " archive " + id + " missing");
        byte[] data = files.size() == 1 ? files.get(0) : SpliceItemOption.joinChunks(files);
        Container c = new Container(compression, -1);
        c.compress(data, null);
        a.setCompression(compression);
        a.setRevision(a.getRevision() + 1);
        a.setCrc(c.crc);
        a.setCompressedSize(c.data.length);
        a.setDecompressedSize(data.length);
        FileData[] fd = new FileData[fileIds.length];
        for (int i = 0; i < fd.length; i++) { fd[i] = new FileData(); fd[i].setId(fileIds[i]); fd[i].setNameHash(-1); }
        a.setFileData(fd);
        storage.store(idx.getId(), id, c.data);
        System.out.println("  index " + idx.getId() + " archive " + id + " replaced (rev " + a.getRevision() + ")");
    }

    /** Replaces existing config records by id (every slot holding that id). */
    static void replaceConfig(Storage storage, Index cfg, Archive arc, Map<Integer, byte[]> byId) throws Exception {
        FileData[] fd = arc.getFileData();
        Map<Integer, byte[]> bySlot = new TreeMap<>();
        for (int i = 0; i < fd.length; i++) if (byId.containsKey(fd[i].getId())) bySlot.put(i, byId.get(fd[i].getId()));
        if (new HashSet<>(byId.keySet()).size() > bySlot.size()) throw new IllegalStateException("config ids missing");
        BuildLocSpinAnims.writeConfig(storage, cfg, arc, bySlot, false);
    }
}

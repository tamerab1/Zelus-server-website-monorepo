import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.*;
import net.runelite.cache.definitions.loaders.*;
import net.runelite.cache.fs.*;
import net.runelite.cache.index.FileData;
import net.runelite.cache.io.OutputStream;

import java.io.File;
import java.lang.reflect.Field;
import java.util.*;

// Looping idle animations for the two home upgrade stations (2026-10-17):
//   Zelus Upgrader  loc 60015 (model 59817): the floating ankh (skin label 1) spins + bobs; the pedestal (label 100) stays put.
//   Zelus Workbench loc 60016 (model 59818): the 3-shell purple orb (label 3) turns slowly + bobs; table/legs (1, 2, 99) stay put.
// Both models already carry these labels, so no model edit is needed. Per loc this adds:
//   skeleton (index 1)  [type 0 origin {L}, type 2 rotate {L}, type 1 translate {L}]
//   frames   (index 0)  N files (ids 1..N, like vanilla); each sets only the rotate yaw and the translate y. The decoder inserts
//                       the type-0 origin with offset 0 in front of them, i.e. the pivot is the label group's own centroid
//                       (x=0, z=0 for both parts), so the part spins on its own axis with no drift.
//   sequence (config 12) frameStep = N (how 841 of the cache's 1079 looping loc seqs loop), frame length 2 client cycles.
//   loc opcode 24        appended before the terminator of every 60015/60016 record.
// verify: builds everything, decodes it back with the real loaders, plays every frame on the real model and checks the base never
// moves, the part's centroid never drifts in x/z, and a quarter of the loop turns it 90 degrees. apply: same, then writes.
// Usage: BuildLocSpinAnims verify|apply <cachePath> [loc,model,label,skeleton,frameGroup,seq,frames,turns,bob ...]
//        (2026-10-18: with spin arguments only those are built, e.g. the Discord Statue 62443)
public class BuildLocSpinAnims {
    record Spin(int loc, int model, int label, int skeleton, int frameGroup, int seq, int frames, int turns, int bob) {}

    static Spin[] SPINS = {
            new Spin(60015, 59817, 1, 16100, 16100, 16201, 128, 2, 6),
            new Spin(60016, 59818, 3, 16101, 16101, 16202, 128, 1, 5),
    };
    static final int FRAME_LEN = 2; // client cycles (20 ms) per frame: 128 frames = 5.12 s loop

    public static void main(String[] args) throws Exception {
        boolean apply = args[0].equals("apply");
        if (!apply && !args[0].equals("verify")) throw new IllegalArgumentException("verify|apply");
        if (args.length > 2) { // 2026-10-18: spins given on the command line instead of the two upgrade stations
            SPINS = new Spin[args.length - 2];
            for (int i = 2; i < args.length; i++) {
                int[] v = java.util.Arrays.stream(args[i].split(",")).mapToInt(Integer::parseInt).toArray();
                SPINS[i - 2] = new Spin(v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7], v[8]);
            }
        }
        try (Store store = new Store(new File(args[1]))) {
            store.load();
            Storage storage = store.getStorage();
            Index frames = store.getIndex(IndexType.ANIMATIONS), skels = store.getIndex(IndexType.SKELETONS), cfg = store.getIndex(IndexType.CONFIGS);

            Archive vf = frames.getArchive(8543);
            byte[] vfd = SpliceItemOption.splitChunks(vf.decompress(storage.loadArchive(vf)), vf.getFileData().length).get(0);
            int vsk = ((vfd[0] & 0xFF) << 8) | (vfd[1] & 0xFF);
            Archive vs = skels.getArchive(vsk);
            System.out.print("vanilla frames 8543 -> skeleton " + vsk + ": ");
            System.out.println("vanilla skeleton 8543 files=" + Arrays.stream(vs.getFileData()).map(f -> f.getId()).toList() + " compression=" + vs.getCompression());

            Map<Integer, byte[]> seqBytes = new TreeMap<>();
            Map<Integer, byte[]> locPatch = new TreeMap<>();
            Map<Integer, byte[]> skelBytes = new TreeMap<>();
            Map<Integer, List<byte[]>> frameBytes = new TreeMap<>();
            Archive seqArc = cfg.getArchive(ConfigType.SEQUENCE.getId()), locArc = cfg.getArchive(ConfigType.OBJECT.getId());
            Set<Integer> seqIds = ids(seqArc);

            for (Spin s : SPINS) {
                if (frames.getArchive(s.frameGroup) != null) throw new IllegalStateException("frame group " + s.frameGroup + " taken");
                if (skels.getArchive(s.skeleton) != null) throw new IllegalStateException("skeleton " + s.skeleton + " taken");
                if (seqIds.contains(s.seq)) throw new IllegalStateException("seq " + s.seq + " taken");

                byte[] sk = skeleton(s.label);
                FramemapDefinition fm = new FramemapLoader().load(s.skeleton, sk);
                List<byte[]> fs = new ArrayList<>();
                List<FrameDefinition> decoded = new ArrayList<>();
                for (int i = 0; i < s.frames; i++) {
                    byte[] f = frame(s, i);
                    fs.add(f);
                    decoded.add(new FrameLoader().load(fm, i + 1, f));
                }
                byte[] seq = sequence(s);
                SequenceDefinition sd = new SequenceLoader().load(s.seq, seq);
                if (sd.frameIDs.length != s.frames || sd.frameStep != s.frames || (sd.frameIDs[0] >>> 16) != s.frameGroup || (sd.frameIDs[0] & 0xFFFF) != 1)
                    throw new IllegalStateException("seq " + s.seq + " decodes wrong");
                simulate(store, s, fm, decoded);

                skelBytes.put(s.skeleton, sk);
                frameBytes.put(s.frameGroup, fs);
                seqBytes.put(s.seq, seq);
            }
            patchLocs(store, locArc, locPatch);

            if (!apply) { System.out.println("VERIFY OK (dry run)"); return; }

            for (Spin s : SPINS) {
                addArchive(storage, skels, s.skeleton, List.of(sk(skelBytes, s)), new int[]{0}, 0);
                List<byte[]> fs = frameBytes.get(s.frameGroup);
                int[] fids = new int[fs.size()];
                for (int i = 0; i < fids.length; i++) fids[i] = i + 1;
                addArchive(storage, frames, s.frameGroup, fs, fids, 2);
            }
            writeRefTable(storage, skels);
            writeRefTable(storage, frames);
            writeConfig(storage, cfg, seqArc, seqBytes, true);
            writeConfig(storage, cfg, locArc, locPatch, false);
            writeRefTable(storage, cfg);
            System.out.println("APPLY OK");
        }
    }

    static byte[] sk(Map<Integer, byte[]> m, Spin s) { return m.get(s.skeleton); }

    static byte[] skeleton(int label) {
        OutputStream o = new OutputStream();
        o.writeByte(3);
        o.writeByte(0); o.writeByte(2); o.writeByte(1);   // origin, rotate, translate
        o.writeByte(1); o.writeByte(1); o.writeByte(1);
        o.writeByte(label); o.writeByte(label); o.writeByte(label);
        return o.flip();
    }

    static int yaw(Spin s, int i) { return (i * 256 * s.turns / s.frames) & 255; }

    static int bob(Spin s, int i) { return (int) Math.round(-s.bob * Math.sin(2 * Math.PI * i / s.frames)); } // y grows downward

    static byte[] frame(Spin s, int i) {
        int yaw = yaw(s, i), dy = bob(s, i);
        OutputStream o = new OutputStream();
        o.writeShort(s.skeleton);
        o.writeByte(3);
        o.writeByte(0);
        o.writeByte(yaw != 0 ? 2 : 0);
        o.writeByte(dy != 0 ? 2 : 0);
        if (yaw != 0) smart(o, yaw);
        if (dy != 0) smart(o, dy);
        return o.flip();
    }

    static void smart(OutputStream o, int v) {
        if (v >= -64 && v < 64) o.writeByte(v + 64);
        else { if (v < -16384 || v >= 16384) throw new IllegalArgumentException("smart " + v); o.writeShort(v + 0xC000); }
    }

    static byte[] sequence(Spin s) {
        OutputStream o = new OutputStream();
        o.writeByte(1);
        o.writeShort(s.frames);
        for (int i = 0; i < s.frames; i++) o.writeShort(FRAME_LEN);
        for (int i = 0; i < s.frames; i++) o.writeShort(i + 1);
        for (int i = 0; i < s.frames; i++) o.writeShort(s.frameGroup);
        o.writeByte(2);
        o.writeShort(s.frames);
        o.writeByte(0);
        return o.flip();
    }

    /** Plays every frame on a fresh copy of the model the way the client does and checks the motion. */
    static void simulate(Store store, Spin s, FramemapDefinition fm, List<FrameDefinition> frames) throws Exception {
        ModelDefinition base = BuildTierPets.model(store, s.model);
        int part = 0, rest = 0;
        for (int v = 0; v < base.vertexCount; v++) if (base.packedVertexGroups[v] == s.label) part++; else rest++;
        if (part == 0) throw new IllegalStateException("model " + s.model + " has no label " + s.label);
        int[] lab = base.packedVertexGroups.clone();
        double[] c0 = centroid(base, lab, s.label);
        double maxDrift = 0, maxBob = 0;
        int[] spanAt0 = null;
        for (int i = 0; i < frames.size(); i++) {
            ModelDefinition m = BuildTierPets.model(store, s.model);
            m.computeAnimationTables();
            FrameDefinition f = frames.get(i);
            for (int t = 0; t < f.translatorCount; t++) {
                int ti = f.indexFrameIds[t];
                m.animate(fm.types[ti], fm.frameMaps[ti], f.translator_x[t], f.translator_y[t], f.translator_z[t]);
            }
            for (int v = 0; v < m.vertexCount; v++)
                if (lab[v] != s.label && (m.vertexX[v] != base.vertexX[v] || m.vertexY[v] != base.vertexY[v] || m.vertexZ[v] != base.vertexZ[v]))
                    throw new IllegalStateException("frame " + i + ": base vertex " + v + " moved");
            double[] c = centroid(m, lab, s.label);
            maxDrift = Math.max(maxDrift, Math.hypot(c[0] - c0[0], c[2] - c0[2]));
            maxBob = Math.max(maxBob, Math.abs(c[1] - c0[1]));
            int[] span = span(m, lab, s.label);
            if (i == 0) spanAt0 = span;
            if (i == s.frames / (4 * s.turns))
                System.out.printf("  loc %d frame %d (quarter turn): x/z span %d/%d -> %d/%d%n", s.loc, i, spanAt0[0], spanAt0[1], span[0], span[1]);
        }
        System.out.printf("loc %d: %d animated + %d static verts, %d frames, base untouched, centroid x/z drift %.2f, bob %.1f%n",
                s.loc, part, rest, frames.size(), maxDrift, maxBob);
        if (maxDrift > 2) throw new IllegalStateException("pivot drifts");
    }

    static double[] centroid(ModelDefinition m, int[] lab, int label) {
        double x = 0, y = 0, z = 0; int n = 0;
        for (int v = 0; v < m.vertexCount; v++) if (lab[v] == label) { x += m.vertexX[v]; y += m.vertexY[v]; z += m.vertexZ[v]; n++; }
        return new double[]{x / n, y / n, z / n};
    }

    static int[] span(ModelDefinition m, int[] lab, int label) {
        int x0 = 1 << 30, x1 = -x0, z0 = x0, z1 = -x0;
        for (int v = 0; v < m.vertexCount; v++) if (lab[v] == label) {
            x0 = Math.min(x0, m.vertexX[v]); x1 = Math.max(x1, m.vertexX[v]); z0 = Math.min(z0, m.vertexZ[v]); z1 = Math.max(z1, m.vertexZ[v]);
        }
        return new int[]{x1 - x0, z1 - z0};
    }

    /** Appends opcode 24 to every record of each loc (dupes included); verifies every other decoded field is unchanged. */
    static void patchLocs(Store store, Archive arc, Map<Integer, byte[]> out) throws Exception {
        byte[] dec = arc.decompress(store.getStorage().loadArchive(arc));
        FileData[] fd = arc.getFileData();
        List<byte[]> chunks = SpliceItemOption.splitChunks(dec, fd.length);
        for (Spin s : SPINS) {
            int hits = 0;
            for (int i = 0; i < fd.length; i++) {
                if (fd[i].getId() != s.loc) continue;
                hits++;
                byte[] raw = chunks.get(i);
                ObjectDefinition before = new ObjectLoader().load(s.loc, raw);
                if (before.getAnimationID() != -1) throw new IllegalStateException("loc " + s.loc + " already animates " + before.getAnimationID());
                if (raw[raw.length - 1] != 0) throw new IllegalStateException("loc " + s.loc + " has no terminator");
                byte[] neu = Arrays.copyOf(raw, raw.length + 3);
                neu[raw.length - 1] = 24;
                neu[raw.length] = (byte) (s.seq >> 8);
                neu[raw.length + 1] = (byte) s.seq;
                neu[raw.length + 2] = 0;
                ObjectDefinition after = new ObjectLoader().load(s.loc, neu);
                if (after.getAnimationID() != s.seq) throw new IllegalStateException("loc " + s.loc + " anim not set");
                after.setAnimationID(-1);
                for (Field f : ObjectDefinition.class.getDeclaredFields()) {
                    f.setAccessible(true);
                    if (!Objects.deepEquals(f.get(before), f.get(after))) throw new IllegalStateException("loc " + s.loc + " field " + f.getName() + " changed");
                }
                chunks.set(i, neu);
                out.put(i, neu);
            }
            if (hits == 0) throw new IllegalStateException("loc " + s.loc + " missing");
            System.out.println("loc " + s.loc + ": opcode 24 -> seq " + s.seq + " on " + hits + " record(s)");
        }
    }

    static void writeRefTable(Storage storage, Index index) throws Exception {
        byte[] raw = index.toIndexData().writeIndexData();
        Container c = new Container(index.getCompression(), -1);
        c.compress(raw, null);
        storage.store(255, index.getId(), c.data);
        index.setCrc(c.crc);
    }

    static Set<Integer> ids(Archive a) {
        Set<Integer> s = new HashSet<>();
        for (FileData f : a.getFileData()) s.add(f.getId());
        return s;
    }

    static void addArchive(Storage storage, Index idx, int id, List<byte[]> files, int[] fileIds, int compression) throws Exception {
        byte[] data = files.size() == 1 ? files.get(0) : SpliceItemOption.joinChunks(files);
        Container c = new Container(compression, -1);
        c.compress(data, null);
        Archive a = idx.addArchive(id);
        a.setNameHash(-1);
        a.setCompression(compression);
        a.setRevision(1);
        a.setCrc(c.crc);
        a.setCompressedSize(c.data.length);
        a.setDecompressedSize(data.length);
        FileData[] fd = new FileData[fileIds.length];
        for (int i = 0; i < fd.length; i++) { fd[i] = new FileData(); fd[i].setId(fileIds[i]); fd[i].setNameHash(-1); }
        a.setFileData(fd);
        storage.store(idx.getId(), id, c.data);
        System.out.println("  index " + idx.getId() + " archive " + id + ": " + files.size() + " file(s), " + c.data.length + " bytes");
    }

    /** append=true: new ids appended after the archive's max (must be larger). append=false: slot index -> replacement bytes. */
    static void writeConfig(Storage storage, Index cfg, Archive arc, Map<Integer, byte[]> entries, boolean append) throws Exception {
        byte[] dec = arc.decompress(storage.loadArchive(arc));
        FileData[] fd = arc.getFileData();
        List<byte[]> chunks = new ArrayList<>(SpliceItemOption.splitChunks(dec, fd.length));
        List<FileData> fds = new ArrayList<>(Arrays.asList(fd));
        if (append) {
            int max = fds.get(fds.size() - 1).getId();
            for (Map.Entry<Integer, byte[]> e : entries.entrySet()) {
                if (e.getKey() <= max) throw new IllegalStateException("config id " + e.getKey() + " not above max " + max);
                FileData n = new FileData(); n.setId(e.getKey()); n.setNameHash(-1);
                fds.add(n); chunks.add(e.getValue()); max = e.getKey();
            }
        } else for (Map.Entry<Integer, byte[]> e : entries.entrySet()) chunks.set(e.getKey(), e.getValue());
        arc.setFileData(fds.toArray(new FileData[0]));
        byte[] data = SpliceItemOption.joinChunks(chunks);
        Container c = new Container(arc.getCompression(), -1);
        c.compress(data, null);
        storage.store(cfg.getId(), arc.getArchiveId(), c.data);
        arc.setCrc(c.crc);
        arc.setRevision(arc.getRevision() + 1);
        arc.setCompressedSize(c.data.length);
        arc.setDecompressedSize(data.length);
        System.out.println("  config " + arc.getArchiveId() + ": " + entries.size() + " entr" + (entries.size() == 1 ? "y" : "ies") + " written");
    }
}

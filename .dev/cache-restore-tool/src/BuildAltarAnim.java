import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.FrameDefinition;
import net.runelite.cache.definitions.FramemapDefinition;
import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.SequenceDefinition;
import net.runelite.cache.definitions.loaders.FrameLoader;
import net.runelite.cache.definitions.loaders.FramemapLoader;
import net.runelite.cache.definitions.loaders.SequenceLoader;
import net.runelite.cache.fs.*;
import net.runelite.cache.index.FileData;
import net.runelite.cache.io.OutputStream;

import java.io.File;
import java.util.*;

// The Reliquary of Souls loop, rebuilt (2026-10-18 round 12). Its BuildLocSpinAnims loop (skeleton / frames 16109, seq
// 16216) turned label 1 about the group's own centroid -- but that group held the crystal AND the three orbiting weapons,
// whose centroid is off the altar's axis, so the crystal swung around the pedestal through its claws. Now:
//   label 1 (the orbiting weapons, trails, wisps): one turn per loop about the altar's axis EXACTLY -- the type-0 origin
//           gets a per-frame offset of minus the group's integer centroid (the client adds the offset to the centroid),
//           and a small bob
//   label 2 (the soul crystal, model built centred on x = z = 0): no turn at all, only y = -AMP * sin(2 pi t), x/z fixed
// Skeleton 16109 and frame group 16109 are replaced in place (same ids, same 192 frames), so seq 16216 and loc 62445's
// opcode 24 stay as they are. verify plays every frame on the real model like the client and checks: base never moves, the
// crystal never moves in x/z and bobs AMP, every weapon vertex keeps its distance from the axis (a true orbit).
// Usage: verify|apply <cachePath>
public class BuildAltarAnim {
    static final int MODEL = 61240, SKELETON = 16109, FRAMES = 192, SEQ = 16216, TURNS = 1, ORBIT_BOB = 5, AMP = 10;

    public static void main(String[] args) throws Exception {
        boolean apply = args[0].equals("apply");
        try (Store store = new Store(new File(args[1]))) {
            store.load();
            Storage storage = store.getStorage();
            Index frames = store.getIndex(IndexType.ANIMATIONS), skels = store.getIndex(IndexType.SKELETONS), cfg = store.getIndex(IndexType.CONFIGS);
            BuildSanctumMap.check(frames.getArchive(SKELETON) != null && skels.getArchive(SKELETON) != null, "16109 missing");
            SequenceDefinition seq = null;
            Archive seqArc = cfg.getArchive(ConfigType.SEQUENCE.getId());
            byte[] sdec = seqArc.decompress(storage.loadArchive(seqArc));
            FileData[] sfd = seqArc.getFileData();
            List<byte[]> schunks = SpliceItemOption.splitChunks(sdec, sfd.length);
            for (int i = 0; i < sfd.length; i++) if (sfd[i].getId() == SEQ) seq = new SequenceLoader().load(SEQ, schunks.get(i));
            BuildSanctumMap.check(seq != null && seq.frameIDs.length == FRAMES && (seq.frameIDs[0] >>> 16) == SKELETON, "seq " + SEQ);

            ModelDefinition base = BuildTierPets.model(store, MODEL);
            int[] lab = base.packedVertexGroups.clone();
            long sx = 0, sz = 0; int n1 = 0, n2 = 0;
            for (int v = 0; v < base.vertexCount; v++) {
                if (lab[v] == 1) { sx += base.vertexX[v]; sz += base.vertexZ[v]; n1++; }
                if (lab[v] == 2) n2++;
            }
            BuildSanctumMap.check(n1 > 0 && n2 > 0, "model " + MODEL + " needs labels 1 (orbit) and 2 (crystal): rebuild it first");
            int ox = (int) -(sx / n1), oz = (int) -(sz / n1); // integer division, as the client does
            System.out.printf("orbit group: %d verts, centroid x/z %d/%d -> pivot offset %d/%d; crystal: %d verts%n", n1, -ox, -oz, ox, oz, n2);

            byte[] sk = skeleton();
            FramemapDefinition fm = new FramemapLoader().load(SKELETON, sk);
            List<byte[]> fs = new ArrayList<>();
            List<FrameDefinition> decoded = new ArrayList<>();
            for (int i = 0; i < FRAMES; i++) {
                byte[] f = frame(i, ox, oz);
                fs.add(f);
                decoded.add(new FrameLoader().load(fm, i + 1, f));
            }
            simulate(store, fm, decoded, lab);
            if (!apply) { System.out.println("VERIFY OK (dry run)"); return; }
            int[] fids = new int[FRAMES];
            for (int i = 0; i < FRAMES; i++) fids[i] = i + 1;
            replace(storage, skels, SKELETON, List.of(sk), new int[]{0});
            replace(storage, frames, SKELETON, fs, fids);
            BuildLocSpinAnims.writeRefTable(storage, skels);
            BuildLocSpinAnims.writeRefTable(storage, frames);
            System.out.println("APPLY OK -- skeleton + frames " + SKELETON + " replaced; seq " + SEQ + " unchanged");
        }
    }

    /** [0] origin {1}, [1] rotate {1}, [2] translate {1}, [3] translate {2}. */
    static byte[] skeleton() {
        OutputStream o = new OutputStream();
        o.writeByte(4);
        o.writeByte(0); o.writeByte(2); o.writeByte(1); o.writeByte(1);
        o.writeByte(1); o.writeByte(1); o.writeByte(1); o.writeByte(1);
        o.writeByte(1); o.writeByte(1); o.writeByte(1); o.writeByte(2);
        return o.flip();
    }

    static byte[] frame(int i, int ox, int oz) {
        double t = 2 * Math.PI * i / FRAMES;
        int yaw = (i * 256 * TURNS / FRAMES) & 255;
        int orbitDy = (int) Math.round(-ORBIT_BOB * Math.sin(t)), crystalDy = (int) Math.round(-AMP * Math.sin(t)); // y grows down
        OutputStream o = new OutputStream();
        o.writeShort(SKELETON);
        o.writeByte(4);
        o.writeByte((ox != 0 ? 1 : 0) | (oz != 0 ? 4 : 0));
        o.writeByte(yaw != 0 ? 2 : 0);
        o.writeByte(orbitDy != 0 ? 2 : 0);
        o.writeByte(crystalDy != 0 ? 2 : 0);
        if (ox != 0) BuildLocSpinAnims.smart(o, ox);
        if (oz != 0) BuildLocSpinAnims.smart(o, oz);
        if (yaw != 0) BuildLocSpinAnims.smart(o, yaw);
        if (orbitDy != 0) BuildLocSpinAnims.smart(o, orbitDy);
        if (crystalDy != 0) BuildLocSpinAnims.smart(o, crystalDy);
        return o.flip();
    }

    static void simulate(Store store, FramemapDefinition fm, List<FrameDefinition> frames, int[] lab) throws Exception {
        ModelDefinition base = BuildTierPets.model(store, MODEL);
        double maxCrystalXZ = 0, maxRadiusErr = 0, minDy = 0, maxDy = 0;
        for (int i = 0; i < frames.size(); i++) {
            ModelDefinition m = BuildTierPets.model(store, MODEL);
            m.computeAnimationTables();
            FrameDefinition f = frames.get(i);
            for (int t = 0; t < f.translatorCount; t++) {
                int ti = f.indexFrameIds[t];
                m.animate(fm.types[ti], fm.frameMaps[ti], f.translator_x[t], f.translator_y[t], f.translator_z[t]);
            }
            for (int v = 0; v < m.vertexCount; v++) {
                int dx = m.vertexX[v] - base.vertexX[v], dy = m.vertexY[v] - base.vertexY[v], dz = m.vertexZ[v] - base.vertexZ[v];
                if (lab[v] == 0 && (dx != 0 || dy != 0 || dz != 0)) throw new IllegalStateException("frame " + i + ": base vertex moved");
                if (lab[v] == 2) {
                    maxCrystalXZ = Math.max(maxCrystalXZ, Math.hypot(dx, dz));
                    minDy = Math.min(minDy, dy); maxDy = Math.max(maxDy, dy);
                }
                if (lab[v] == 1) {
                    double r0 = Math.hypot(base.vertexX[v], base.vertexZ[v]), r1 = Math.hypot(m.vertexX[v], m.vertexZ[v]);
                    maxRadiusErr = Math.max(maxRadiusErr, Math.abs(r1 - r0));
                }
            }
        }
        System.out.printf("%d frames: base still; crystal x/z drift %.1f, bob %.0f..%.0f; orbit radius error %.1f%n",
                frames.size(), maxCrystalXZ, minDy, maxDy, maxRadiusErr);
        BuildSanctumMap.check(maxCrystalXZ == 0, "crystal drifts in x/z");
        BuildSanctumMap.check(maxRadiusErr <= 3, "the orbit is not about the axis");
    }

    static void replace(Storage storage, Index idx, int id, List<byte[]> files, int[] fileIds) throws Exception {
        Archive a = idx.getArchive(id);
        byte[] data = files.size() == 1 ? files.get(0) : SpliceItemOption.joinChunks(files);
        Container c = new Container(a.getCompression(), -1);
        c.compress(data, null);
        a.setCrc(c.crc);
        a.setRevision(a.getRevision() + 1);
        a.setCompressedSize(c.data.length);
        a.setDecompressedSize(data.length);
        FileData[] fd = new FileData[fileIds.length];
        for (int i = 0; i < fd.length; i++) { fd[i] = new FileData(); fd[i].setId(fileIds[i]); fd[i].setNameHash(-1); }
        a.setFileData(fd);
        storage.store(idx.getId(), id, c.data);
        System.out.println("  index " + idx.getId() + " archive " + id + " replaced: " + files.size() + " file(s), " + c.data.length + " bytes");
    }
}

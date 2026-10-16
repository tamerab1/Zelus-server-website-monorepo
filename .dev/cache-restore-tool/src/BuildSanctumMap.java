import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.LocationsDefinition;
import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.ObjectDefinition;
import net.runelite.cache.definitions.loaders.LocationsLoader;
import net.runelite.cache.definitions.loaders.ModelLoader;
import net.runelite.cache.definitions.loaders.ObjectLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Container;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;
import net.runelite.cache.region.HeightCalc;
import net.runelite.cache.region.Location;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.*;

// The Sanctum map (2026-10-16): a de-snowed copy of the God Wars Dungeon (mapsquares 44_82, 44_83, 45_82, 45_83) written to
// the empty mapsquares 30_170, 30_171, 31_170, 31_171 (regions 7850/7851/8106/8107), which the server's SanctumInstance
// then instances per player. The live God Wars Dungeon is not touched.
//  - every area has its boss's colours (Theme): Bandos wing crimson (Harrowlord), Zamorak wing void purple (Gloomfang),
//    Armadyl wing toxic green (Venomtongue), the central hall + Saradomin wing obsidian/azure
//  - terrain: snow underlays 57/58 -> the theme's dark underlay / charcoal 56; the ice overlay 103 -> the theme's overlay
//    (crimson = lava overlay 18, texture 31); plane-0 tiles get an explicit height if they had none
//  - locs: the snow specks (26396-26398) and the wind-sound emitters (16399, 16431, 18405, 21804) are dropped; every other
//    non-interactive object with snowy colours is replaced by a themed recoloured copy (one copy per object per theme, new
//    object ids from 60024): snow white -> the theme's dark stone, its brightest highlights -> the theme's embers, icy
//    blue -> dark stone. Objects with options (doors, altars...) are kept as they are, so server actions keep working.
// Everything is decoded back and checked before anything is written.
// Usage: verify|apply <cachePath>
public class BuildSanctumMap {

    static final int[][] SRC = {{44, 82}, {44, 83}, {45, 82}, {45, 83}};
    static final int[][] DST = {{30, 170}, {30, 171}, {31, 170}, {31, 171}};
    static final Set<Integer> REMOVE = Set.of(26396, 26397, 26398, 16399, 16431, 18405, 21804);
    /** Per-area palette. Floor ids are the stored values (id + 1). */
    enum Theme {
        CRIMSON(0, 1, 0, 7, 167, 19),   // snow -> underlay 166 #30120e, ice -> lava overlay 18
        VOID(52, 2, 50, 7, 46, 103),    // snow -> underlay 45 #1b1222, ice -> overlay 102 #501840
        TOXIC(20, 2, 22, 7, 103, 366),  // snow -> underlay 102 #111e1a, ice -> overlay 365 #0a5b2b
        AZURE(38, 1, 36, 6, 47, 233);   // snow -> underlay 46 #1b2233, ice -> overlay 232 #212a3e
        final int stoneHue, stoneSat, emberHue, emberSat, snowUnderlay, iceOverlay;
        Theme(int stoneHue, int stoneSat, int emberHue, int emberSat, int snowUnderlay, int iceOverlay) {
            this.stoneHue = stoneHue; this.stoneSat = stoneSat; this.emberHue = emberHue; this.emberSat = emberSat;
            this.snowUnderlay = snowUnderlay; this.iceOverlay = iceOverlay;
        }
    }
    static final int PALE_SNOW_STORED = 59, SNOW_STORED = 58, ICE_STORED = 104, CHARCOAL_STORED = 57;

    /** Area of a God Wars coordinate: the wing layout as the Sanctum uses it. */
    static Theme themeAt(int x, int y) {
        if (x < 2880 && y >= 5336) return Theme.CRIMSON;                             // Bandos wing + room
        if (x >= 2899 && y >= 5312) return Theme.VOID;                                // Zamorak wing + room
        if ((x < 2880 && y <= 5294) || (x <= 2843 && y <= 5310)) return Theme.TOXIC; // Armadyl wing + room
        return Theme.AZURE;                                                           // central hall, Saradomin
    }

    static final int[][] OBJECT_ID_RANGES = {{60024, 60435}, {60455, 61179}};

    public static void main(String[] args) throws Exception {
        boolean apply = args[0].equals("apply");
        try (Store store = new Store(new File(args[1]))) {
            store.load();
            Storage storage = store.getStorage();
            Index maps = store.getIndex(IndexType.MAPS);
            Index configs = store.getIndex(IndexType.CONFIGS);
            Index models = store.getIndex(IndexType.MODELS);

            // ---- objects ----
            Archive objArchive = configs.getArchive(ConfigType.OBJECT.getId());
            byte[] objDec = objArchive.decompress(storage.loadArchive(objArchive));
            FileData[] objFd = objArchive.getFileData();
            List<byte[]> objChunks = new ArrayList<>(InsertObjectActions.splitChunks(objDec, objFd.length));
            Map<Integer, Integer> slotOf = new HashMap<>();
            for (int i = 0; i < objFd.length; i++) slotOf.putIfAbsent(objFd[i].getId(), i);
            Set<Integer> usedIds = new HashSet<>();
            for (FileData f : objFd) usedIds.add(f.getId());

            List<Location>[] srcLocs = new List[4];
            byte[][] srcTerrain = new byte[4][];
            TreeSet<Integer> objectIds = new TreeSet<>();
            for (int r = 0; r < 4; r++) {
                Archive m = maps.findArchiveByName("m" + SRC[r][0] + "_" + SRC[r][1]);
                Archive l = maps.findArchiveByName("l" + SRC[r][0] + "_" + SRC[r][1]);
                srcTerrain[r] = m.decompress(storage.loadArchive(m));
                LocationsDefinition ld = new LocationsLoader().load(SRC[r][0], SRC[r][1], l.decompress(storage.loadArchive(l)));
                srcLocs[r] = ld.getLocations();
                for (Location loc : srcLocs[r]) objectIds.add(loc.getId());
                check(maps.findArchiveByName("m" + DST[r][0] + "_" + DST[r][1]) == null, "target m" + DST[r][0] + "_" + DST[r][1] + " exists");
                check(maps.findArchiveByName("l" + DST[r][0] + "_" + DST[r][1]) == null, "target l" + DST[r][0] + "_" + DST[r][1] + " exists");
            }

            Map<Long, Integer> replace = new TreeMap<>(); // (old object id, theme) -> recoloured copy
            Map<Integer, byte[]> newDefs = new TreeMap<>();
            Map<Integer, EnumSet<Theme>> themesOf = new HashMap<>(); // themes each object appears in
            for (int r = 0; r < 4; r++)
                for (Location loc : srcLocs[r]) {
                    int gx = SRC[r][0] * 64 + (loc.getPosition().getX() & 63), gy = SRC[r][1] * 64 + (loc.getPosition().getY() & 63);
                    themesOf.computeIfAbsent(loc.getId(), k -> EnumSet.noneOf(Theme.class)).add(themeAt(gx, gy));
                }
            int[] nextId = {OBJECT_ID_RANGES[0][0]};
            for (int id : objectIds) for (Theme theme : themesOf.get(id)) {
                if (REMOVE.contains(id) || !slotOf.containsKey(id)) continue;
                byte[] raw = objChunks.get(slotOf.get(id));
                ObjectDefinition def = new ObjectLoader().load(id, raw);
                if (hasActions(def) || def.getObjectModels() == null) continue;
                Map<Integer, Integer> existing = new LinkedHashMap<>();
                if (def.getRecolorToFind() != null)
                    for (int k = 0; k < def.getRecolorToFind().length; k++)
                        existing.put(def.getRecolorToFind()[k] & 0xFFFF, def.getRecolorToReplace()[k] & 0xFFFF);
                TreeSet<Integer> rawColours = new TreeSet<>(existing.keySet());
                for (int modelId : def.getObjectModels()) {
                    Archive ma = models.getArchive(modelId);
                    if (ma == null) continue;
                    ModelDefinition md = new ModelLoader().load(modelId, ma.decompress(storage.loadArchive(ma)));
                    if (md.faceColors != null) for (short c : md.faceColors) rawColours.add(c & 0xFFFF);
                }
                Map<Integer, Integer> pairs = new LinkedHashMap<>();
                boolean snowy = false;
                for (int c : rawColours) {
                    int eff = existing.getOrDefault(c, c);
                    int nc = desnow(eff, theme);
                    if (nc != eff) snowy = true;
                    if (nc != c) pairs.put(c, nc);
                }
                if (!snowy) continue;
                // the client applies pairs in order: a replacement must never be another pair's find colour
                Set<Integer> finds = pairs.keySet();
                for (var e : pairs.entrySet()) {
                    int v = e.getValue();
                    while (finds.contains(v)) v = (v & ~127) | Math.max(2, (v & 127) - 1);
                    e.setValue(v);
                }
                check(pairs.size() <= 255, "too many recolours on " + id);
                int newId = allocate(nextId, usedIds);
                byte[] copy = withRecolor(raw, pairs);
                ObjectDefinition back = new ObjectLoader().load(newId, copy);
                check(Arrays.equals(back.getObjectModels(), def.getObjectModels()) && back.getSizeX() == def.getSizeX()
                        && back.getSizeY() == def.getSizeY() && back.getInteractType() == def.getInteractType()
                        && back.getRecolorToFind() != null && back.getRecolorToFind().length == pairs.size(), "object copy " + id);
                replace.put(key(id, theme), newId);
                newDefs.put(newId, copy);
                usedIds.add(newId);
            }
            System.out.println("recoloured object copies: " + replace.size() + " (ids " + OBJECT_ID_RANGES[0][0] + "-" + (nextId[0] - 1) + ")");

            // ---- maps ----
            byte[][] dstTerrain = new byte[4][], dstLocs = new byte[4][];
            for (int r = 0; r < 4; r++) {
                dstTerrain[r] = rewriteTerrain(srcTerrain[r], SRC[r][0] * 64, SRC[r][1] * 64);
                int sbx = SRC[r][0] * 64, sby = SRC[r][1] * 64;
                List<int[]> kept = new ArrayList<>(); // id, type, orientation, z, localX, localY
                int removed = 0, swapped = 0;
                for (Location loc : srcLocs[r]) {
                    if (REMOVE.contains(loc.getId())) { removed++; continue; }
                    int gx = sbx + (loc.getPosition().getX() & 63), gy = sby + (loc.getPosition().getY() & 63);
                    int id = replace.getOrDefault(key(loc.getId(), themeAt(gx, gy)), loc.getId());
                    if (id != loc.getId()) swapped++;
                    kept.add(new int[]{id, loc.getType(), loc.getOrientation(), loc.getPosition().getZ(),
                            loc.getPosition().getX() & 63, loc.getPosition().getY() & 63});
                }
                dstLocs[r] = encodeLocs(kept);
                // verify by decoding at the destination
                List<Location> back = new LocationsLoader().load(DST[r][0], DST[r][1], dstLocs[r]).getLocations();
                check(back.size() == kept.size(), "loc count " + r);
                Set<String> want = new HashSet<>(), got = new HashSet<>();
                for (int[] k : kept) want.add(k[0] + ":" + k[1] + ":" + k[2] + ":" + k[3] + ":" + k[4] + ":" + k[5]);
                for (Location b : back) got.add(b.getId() + ":" + b.getType() + ":" + b.getOrientation() + ":" + b.getPosition().getZ()
                        + ":" + (b.getPosition().getX() & 63) + ":" + (b.getPosition().getY() & 63));
                check(want.equals(got), "loc round trip " + r);
                verifyTerrain(srcTerrain[r], dstTerrain[r]);
                System.out.printf("m%d_%d -> m%d_%d: terrain %d -> %d bytes; locs %d kept (%d recoloured), %d removed%n",
                        SRC[r][0], SRC[r][1], DST[r][0], DST[r][1], srcTerrain[r].length, dstTerrain[r].length, kept.size(), swapped, removed);
            }

            if (!apply) {
                System.out.println("VERIFY OK -- nothing written.");
                return;
            }
            // objects: sorted insert of the copies
            List<FileData> fdList = new ArrayList<>(Arrays.asList(objFd));
            for (var e : newDefs.entrySet()) {
                int at = fdList.size();
                for (int i = 0; i < fdList.size(); i++) if (fdList.get(i).getId() > e.getKey()) { at = i; break; }
                FileData nf = new FileData();
                nf.setId(e.getKey());
                nf.setNameHash(-1);
                fdList.add(at, nf);
                objChunks.add(at, e.getValue());
            }
            objArchive.setFileData(fdList.toArray(new FileData[0]));
            byte[] objOut = InsertObjectActions.joinChunks(objChunks);
            Container oc = new Container(objArchive.getCompression(), -1);
            oc.compress(objOut, null);
            storage.store(configs.getId(), objArchive.getArchiveId(), oc.data);
            objArchive.setCrc(oc.crc);
            objArchive.setRevision(objArchive.getRevision() + 1);
            objArchive.setCompressedSize(oc.data.length);
            objArchive.setDecompressedSize(objOut.length);
            ImportCathedralMap.writeIndexReferenceTable(storage, configs);

            int nextArchive = 0;
            for (Archive a : maps.getArchives()) nextArchive = Math.max(nextArchive, a.getArchiveId());
            for (int r = 0; r < 4; r++) {
                ImportCathedralMap.writeSingleFileArchive(storage, maps, ++nextArchive, "m" + DST[r][0] + "_" + DST[r][1], dstTerrain[r]);
                ImportCathedralMap.writeSingleFileArchive(storage, maps, ++nextArchive, "l" + DST[r][0] + "_" + DST[r][1], dstLocs[r]);
            }
            ImportCathedralMap.writeIndexReferenceTable(storage, maps);
            System.out.println("APPLY complete -- " + newDefs.size() + " objects, 8 map archives.");
        }
    }

    static long key(int id, Theme theme) {
        return (long) id * 8 + theme.ordinal();
    }

    /** Next free object id from OBJECT_ID_RANGES (nextId[0] advances). */
    static int allocate(int[] nextId, Set<Integer> used) {
        for (; ; ) {
            int id = nextId[0]++;
            if (id > OBJECT_ID_RANGES[0][1] && id < OBJECT_ID_RANGES[1][0]) {
                nextId[0] = OBJECT_ID_RANGES[1][0];
                continue;
            }
            check(id <= OBJECT_ID_RANGES[1][1], "out of object ids");
            if (!used.contains(id)) return id;
        }
    }

    static boolean hasActions(ObjectDefinition d) {
        if (d.getActions() == null) return false;
        for (String a : d.getActions()) if (a != null && !a.equalsIgnoreCase("hidden")) return true;
        return false;
    }

    /** Snow white -> the theme's dark stone (brightest -> its embers); icy blue -> dark stone; anything else untouched. */
    static int desnow(int hsl, Theme t) {
        int h = (hsl >> 10) & 63, s = (hsl >> 7) & 7, l = hsl & 127;
        if (hsl == 0) return hsl;
        if (s <= 2 && l >= 55) {
            if (l >= 108) return t.emberHue << 10 | t.emberSat << 7 | Math.min(60, 30 + (l - 108));
            return t.stoneHue << 10 | t.stoneSat << 7 | Math.max(4, (int) Math.round(6 + l * 0.26));
        }
        if (h >= 30 && h <= 44 && s >= 2 && l >= 45)
            return t.stoneHue << 10 | Math.min(7, t.stoneSat + 1) << 7 | Math.max(4, (int) Math.round(l * 0.30));
        return hsl;
    }

    /** Copies an object def with its recolour (opcode 40) replaced. */
    static byte[] withRecolor(byte[] raw, Map<Integer, Integer> pairs) {
        net.runelite.cache.io.InputStream in = new net.runelite.cache.io.InputStream(raw);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (; ; ) {
            int start = in.getOffset();
            check(start < raw.length, "object def ran out");
            int op = in.readUnsignedByte();
            if (op == 0) break;
            if (op == 40) {
                int n = in.readUnsignedByte();
                in.skip(n * 4);
                continue;
            }
            InsertObjectActions.skipOpcodePayload(op, in);
            out.write(raw, start, in.getOffset() - start);
        }
        out.write(40);
        out.write(pairs.size());
        for (var e : pairs.entrySet()) {
            out.write(e.getKey() >> 8); out.write(e.getKey());
            out.write(e.getValue() >> 8); out.write(e.getValue());
        }
        out.write(0);
        return out.toByteArray();
    }

    /** Floor remap + baked plane-0 heights. Same per-tile opcode stream as io.ruin.model.map.Region (u16 opcodes). */
    static byte[] rewriteTerrain(byte[] in, int baseX, int baseY) {
        int p = 0;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int z = 0; z < 4; z++) for (int x = 0; x < 64; x++) for (int y = 0; y < 64; y++) {
            for (; ; ) {
                int rawOp = ((in[p] & 0xFF) << 8) | (in[p + 1] & 0xFF);
                p += 2;
                int op = rawOp & 0x7FFF;
                if (op == 0) {
                    if (z == 0) { // bake the procedural height of the ORIGINAL position
                        int h = HeightCalc.calculate(baseX + x + 0xe3b7b, baseY + y + 0x87cce);
                        check(h >= 0 && h <= 255, "height " + h);
                        out.write(0); out.write(1); out.write(h);
                    } else {
                        out.write(0); out.write(0);
                    }
                    break;
                }
                if (op == 1) {
                    out.write(rawOp >> 8); out.write(rawOp);
                    out.write(in[p++]);
                    break;
                }
                if (op <= 49) {
                    int v = ((in[p] & 0xFF) << 8) | (in[p + 1] & 0xFF);
                    p += 2;
                    if (v == ICE_STORED) v = themeAt(baseX + x, baseY + y).iceOverlay;
                    out.write(rawOp >> 8); out.write(rawOp);
                    out.write(v >> 8); out.write(v);
                } else if (op <= 81) {
                    out.write(rawOp >> 8); out.write(rawOp);
                } else {
                    int u = op - 81;
                    int nu = u == SNOW_STORED ? themeAt(baseX + x, baseY + y).snowUnderlay : u == PALE_SNOW_STORED ? CHARCOAL_STORED : u;
                    int nOp = (rawOp & 0x8000) | (nu + 81);
                    out.write(nOp >> 8); out.write(nOp);
                }
            }
        }
        check(p == in.length, "terrain leftover " + (in.length - p));
        return out.toByteArray();
    }

    /** Re-walks both streams: same tiles, only floor ids / baked heights differ. */
    static void verifyTerrain(byte[] src, byte[] dst) {
        int ps = 0, pd = 0, remapped = 0;
        for (int t = 0; t < 4 * 64 * 64; t++) {
            for (; ; ) {
                int a = (((src[ps] & 0xFF) << 8) | (src[ps + 1] & 0xFF)) & 0x7FFF; ps += 2;
                int b = (((dst[pd] & 0xFF) << 8) | (dst[pd + 1] & 0xFF)) & 0x7FFF; pd += 2;
                if (a == 0) { if (b == 1) pd++; else check(b == 0, "terrain end"); break; }
                if (a == 1) { check(b == 1 && src[ps] == dst[pd], "height"); ps++; pd++; break; }
                if (a <= 49) { check(a == b, "overlay op"); ps += 2; pd += 2; continue; }
                if (a <= 81) { check(a == b, "settings"); continue; }
                if (a != b) remapped++;
                check(b > 81, "underlay op");
            }
        }
        check(ps == src.length && pd == dst.length, "terrain length");
    }

    static byte[] encodeLocs(List<int[]> locs) {
        locs.sort(Comparator.<int[]>comparingInt(l -> l[0]).thenComparingInt(l -> pos(l)));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int prevId = -1, i = 0;
        while (i < locs.size()) {
            int id = locs.get(i)[0];
            writeIdSmart(out, id - prevId);
            prevId = id;
            int prevPos = 0;
            for (; i < locs.size() && locs.get(i)[0] == id; i++) {
                int[] l = locs.get(i);
                int pos = pos(l);
                writeShortSmart(out, pos - prevPos + 1);
                prevPos = pos;
                out.write((l[1] << 2) | (l[2] & 3));
            }
            writeShortSmart(out, 0);
        }
        writeIdSmart(out, 0);
        return out.toByteArray();
    }

    static int pos(int[] l) {
        return (l[3] << 12) | (l[4] << 6) | l[5];
    }

    static void writeShortSmart(ByteArrayOutputStream out, int v) {
        check(v >= 0 && v < 32768, "smart " + v);
        if (v < 128) out.write(v);
        else { out.write((v + 0x8000) >> 8); out.write(v + 0x8000); }
    }

    static void writeIdSmart(ByteArrayOutputStream out, int v) {
        while (v >= 32767) { writeShortSmart(out, 32767); v -= 32767; }
        writeShortSmart(out, v);
    }

    static void check(boolean ok, String what) {
        if (!ok) throw new IllegalStateException("CHECK FAILED: " + what);
    }
}

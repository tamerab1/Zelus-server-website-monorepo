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
import net.runelite.cache.region.Location;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.*;

// The Sanctum hub (2026-10-16): the ToB Bloat room (mapsquare 51_69, region 13125) copied to the empty mapsquare 30_172
// (region 7852), so the real Bloat room used by Theatre of Blood stays untouched.
//  - the central tank ("Chamber" 32957 + "Operating table" 32958), its pillar ring, the chains and the hanging hands are
//    removed; the freed centre (3293-3298, 4445-4450 in Bloat-room coordinates) gets a plain charcoal floor (underlay 56,
//    no overlay, no blocking settings) on plane 0 and nothing above it
//  - every other non-interactive object is recoloured to obsidian & blood crimson (ToB grey stone -> charcoal,
//    gold/brass trims -> crimson) as new object copies (ids from BuildSanctumMap.OBJECT_ID_RANGES, after those in use)
// Run after BuildSanctumMap (it only takes ids that are still free). Usage: verify|apply <cachePath>
public class BuildSanctumHub {

    static final int[] SRC = {51, 69}, DST = {30, 172};
    static final int CX1 = 3293, CY1 = 4445, CX2 = 3298, CY2 = 4450; // the cleared centre
    // + the ToB "Barrier" 32755 (its global "Pass" handler belongs to live ToB parties) and the "Formidable passage" exits 33113
    static final Set<Integer> REMOVE = Set.of(32957, 32958, 32955, 32959, 32960, 33084, 32949, 32950, 32951, 32952, 32953, 32954, 32970,
            32755, 33113);
    static final int CHARCOAL_STORED = 57; // underlay 56 + 1

    public static void main(String[] args) throws Exception {
        boolean apply = args[0].equals("apply");
        try (Store store = new Store(new File(args[1]))) {
            store.load();
            Storage storage = store.getStorage();
            Index maps = store.getIndex(IndexType.MAPS), configs = store.getIndex(IndexType.CONFIGS), models = store.getIndex(IndexType.MODELS);
            Archive objArchive = configs.getArchive(ConfigType.OBJECT.getId());
            byte[] objDec = objArchive.decompress(storage.loadArchive(objArchive));
            FileData[] objFd = objArchive.getFileData();
            List<byte[]> objChunks = new ArrayList<>(InsertObjectActions.splitChunks(objDec, objFd.length));
            Map<Integer, Integer> slotOf = new HashMap<>();
            for (int i = 0; i < objFd.length; i++) slotOf.putIfAbsent(objFd[i].getId(), i);
            Set<Integer> used = new HashSet<>();
            for (FileData f : objFd) used.add(f.getId());

            Archive m = maps.findArchiveByName("m" + SRC[0] + "_" + SRC[1]);
            Archive l = maps.findArchiveByName("l" + SRC[0] + "_" + SRC[1]);
            BuildSanctumMap.check(maps.findArchiveByName("m" + DST[0] + "_" + DST[1]) == null, "target m exists");
            BuildSanctumMap.check(maps.findArchiveByName("l" + DST[0] + "_" + DST[1]) == null, "target l exists");
            byte[] terrain = m.decompress(storage.loadArchive(m));
            List<Location> locs = new LocationsLoader().load(SRC[0], SRC[1], l.decompress(storage.loadArchive(l))).getLocations();
            int bx = SRC[0] * 64, by = SRC[1] * 64;

            // ---- recoloured object copies ----
            Map<Integer, Integer> replace = new TreeMap<>();
            Map<Integer, byte[]> newDefs = new TreeMap<>();
            int[] nextId = {BuildSanctumMap.OBJECT_ID_RANGES[0][0]};
            TreeSet<Integer> ids = new TreeSet<>();
            for (Location loc : locs) ids.add(loc.getId());
            for (int id : ids) {
                if (REMOVE.contains(id) || !slotOf.containsKey(id)) continue;
                byte[] raw = objChunks.get(slotOf.get(id));
                ObjectDefinition def = new ObjectLoader().load(id, raw);
                if (BuildSanctumMap.hasActions(def) || def.getObjectModels() == null) continue;
                Map<Integer, Integer> existing = new LinkedHashMap<>();
                if (def.getRecolorToFind() != null)
                    for (int k = 0; k < def.getRecolorToFind().length; k++)
                        existing.put(def.getRecolorToFind()[k] & 0xFFFF, def.getRecolorToReplace()[k] & 0xFFFF);
                TreeSet<Integer> raws = new TreeSet<>(existing.keySet());
                for (int modelId : def.getObjectModels()) {
                    Archive ma = models.getArchive(modelId);
                    if (ma == null) continue;
                    ModelDefinition md = new ModelLoader().load(modelId, ma.decompress(storage.loadArchive(ma)));
                    if (md.faceColors != null) for (short c : md.faceColors) raws.add(c & 0xFFFF);
                }
                Map<Integer, Integer> pairs = new LinkedHashMap<>();
                boolean changed = false;
                for (int c : raws) {
                    int eff = existing.getOrDefault(c, c);
                    int nc = obsidian(eff);
                    if (nc != eff) changed = true;
                    if (nc != c) pairs.put(c, nc);
                }
                if (!changed) continue;
                Set<Integer> finds = pairs.keySet();
                for (var e : pairs.entrySet()) {
                    int v = e.getValue();
                    while (finds.contains(v)) v = (v & ~127) | Math.max(2, (v & 127) - 1);
                    e.setValue(v);
                }
                BuildSanctumMap.check(pairs.size() <= 255, "too many recolours on " + id);
                int newId = BuildSanctumMap.allocate(nextId, used);
                byte[] copy = BuildSanctumMap.withRecolor(raw, pairs);
                ObjectDefinition back = new ObjectLoader().load(newId, copy);
                BuildSanctumMap.check(Arrays.equals(back.getObjectModels(), def.getObjectModels()) && back.getSizeX() == def.getSizeX()
                        && back.getSizeY() == def.getSizeY() && back.getInteractType() == def.getInteractType(), "object copy " + id);
                replace.put(id, newId);
                newDefs.put(newId, copy);
                used.add(newId);
            }

            // ---- locs ----
            List<int[]> kept = new ArrayList<>();
            int removed = 0;
            for (Location loc : locs) {
                int lx = loc.getPosition().getX() & 63, ly = loc.getPosition().getY() & 63, z = loc.getPosition().getZ();
                int ax = bx + lx, ay = by + ly;
                boolean centre = ax >= CX1 && ax <= CX2 && ay >= CY1 && ay <= CY2;
                if (REMOVE.contains(loc.getId()) || centre) { removed++; continue; }
                kept.add(new int[]{replace.getOrDefault(loc.getId(), loc.getId()), loc.getType(), loc.getOrientation(), z, lx, ly});
            }
            byte[] locBytes = BuildSanctumMap.encodeLocs(kept);
            List<Location> back = new LocationsLoader().load(DST[0], DST[1], locBytes).getLocations();
            BuildSanctumMap.check(back.size() == kept.size(), "loc count");

            // ---- terrain ----
            byte[] newTerrain = rewriteTerrain(terrain, bx, by);
            System.out.printf("hub m%d_%d -> m%d_%d: terrain %d -> %d bytes, locs %d kept, %d removed, %d recoloured object copies%n",
                    SRC[0], SRC[1], DST[0], DST[1], terrain.length, newTerrain.length, kept.size(), removed, newDefs.size());
            if (!apply) {
                System.out.println("VERIFY OK -- nothing written.");
                return;
            }
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
            ImportCathedralMap.writeSingleFileArchive(storage, maps, ++nextArchive, "m" + DST[0] + "_" + DST[1], newTerrain);
            ImportCathedralMap.writeSingleFileArchive(storage, maps, ++nextArchive, "l" + DST[0] + "_" + DST[1], locBytes);
            ImportCathedralMap.writeIndexReferenceTable(storage, maps);
            System.out.println("APPLY complete.");
        }
    }

    /** ToB grey stone -> charcoal; gold/brass/orange trims -> blood crimson; reds and bone whites stay. */
    static int obsidian(int hsl) {
        int h = (hsl >> 10) & 63, s = (hsl >> 7) & 7, l = hsl & 127;
        if (hsl == 0) return hsl;
        if (s <= 2 && l >= 20 && l <= 105) return 1 << 7 | Math.max(4, (int) Math.round(4 + l * 0.32));
        if (h >= 4 && h <= 12 && s >= 3) return 7 << 7 | Math.max(10, Math.min(50, (int) Math.round(l * 0.6)));
        return hsl;
    }

    /** Copies the terrain; tiles in the cleared centre become charcoal floor (plane 0) / empty (above). */
    static byte[] rewriteTerrain(byte[] in, int baseX, int baseY) {
        int p = 0;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int z = 0; z < 4; z++) for (int x = 0; x < 64; x++) for (int y = 0; y < 64; y++) {
            boolean centre = baseX + x >= CX1 && baseX + x <= CX2 && baseY + y >= CY1 && baseY + y <= CY2;
            int height = -1; // explicit height byte, if any
            ByteArrayOutputStream tile = new ByteArrayOutputStream();
            for (; ; ) {
                int rawOp = ((in[p] & 0xFF) << 8) | (in[p + 1] & 0xFF);
                p += 2;
                int op = rawOp & 0x7FFF;
                if (op == 0) break;
                if (op == 1) { height = in[p++] & 0xFF; break; }
                if (op <= 49) {
                    if (!centre) { tile.write(rawOp >> 8); tile.write(rawOp); tile.write(in[p]); tile.write(in[p + 1]); }
                    p += 2;
                } else if (!centre) {
                    tile.write(rawOp >> 8); tile.write(rawOp);
                }
            }
            if (centre && z == 0) { int u = CHARCOAL_STORED + 81; tile.write(u >> 8); tile.write(u); }
            out.write(tile.toByteArray(), 0, tile.size());
            if (height < 0 && z == 0) // bake the procedural height of the original position (it depends on it)
                height = net.runelite.cache.region.HeightCalc.calculate(baseX + x + 0xe3b7b, baseY + y + 0x87cce);
            if (height >= 0) { out.write(0); out.write(1); out.write(height); }
            else { out.write(0); out.write(0); }
        }
        BuildSanctumMap.check(p == in.length, "terrain leftover " + (in.length - p));
        return out.toByteArray();
    }
}

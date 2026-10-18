import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.loaders.LocationsLoader;
import net.runelite.cache.fs.*;
import net.runelite.cache.fs.jagex.CompressionType;
import net.runelite.cache.region.Location;

import java.io.File;
import java.util.*;

// Removes specific map locs (the static objects baked into a region's map file) for good, by rewriting the region's
// unencrypted loc file ("l<x>_<y>") in place: decode, drop exactly the named locs, re-encode (BuildSanctumMap.encodeLocs),
// round-trip check, store with a bumped revision. 2026-10-18, first for the two Mounted Max Capes behind the Zelus
// Blackjack Table: removing them at RUNTIME (GameObject.remove) was wrong there -- on the client a type-10 loc and the
// alcove's type-9 diagonal wall share one scene layer, so the LocDel took the wall corner instead of the cape, and on
// the server their removal cleared the clipping of the table's own tiles. Gone from the map file, neither happens.
// Each spec is <id>@<x>,<y>,<z>; every one must match exactly one loc or nothing is written.
// Usage: verify|apply <cachePath> <id@x,y,z> [...]
public class RemoveMapLocs {
    public static void main(String[] args) throws Exception {
        boolean apply = args[0].equals("apply");
        List<int[]> specs = new ArrayList<>(); // id, x, y, z
        for (int i = 2; i < args.length; i++) {
            String[] p = args[i].split("[@,]");
            specs.add(new int[]{Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3])});
        }
        int rx = specs.get(0)[1] >> 6, ry = specs.get(0)[2] >> 6;
        for (int[] s : specs)
            BuildSanctumMap.check((s[1] >> 6) == rx && (s[2] >> 6) == ry, "all locs must be in one mapsquare");
        try (Store store = new Store(new File(args[1]))) {
            store.load();
            Storage storage = store.getStorage();
            Index maps = store.getIndex(IndexType.MAPS);
            Archive l = maps.findArchiveByName("l" + rx + "_" + ry);
            BuildSanctumMap.check(l != null, "no loc file l" + rx + "_" + ry);
            byte[] raw = l.decompress(storage.loadArchive(l));
            List<Location> locs = new LocationsLoader().load(rx, ry, raw).getLocations();
            List<int[]> kept = new ArrayList<>();
            int[] hits = new int[specs.size()];
            String[] firstSig = new String[specs.size()];
            for (Location loc : locs) {
                // the loader gives positions local to the mapsquare
                int x = (rx << 6) + (loc.getPosition().getX() & 63), y = (ry << 6) + (loc.getPosition().getY() & 63), z = loc.getPosition().getZ();
                int match = -1;
                for (int i = 0; i < specs.size(); i++) {
                    int[] s = specs.get(i);
                    if (s[0] == loc.getId() && s[1] == x && s[2] == y && s[3] == z) match = i;
                }
                if (match >= 0) {
                    String sig = loc.getType() + "/" + loc.getOrientation();
                    if (hits[match] > 0 && !sig.equals(firstSig[match]))
                        throw new IllegalStateException("spec " + Arrays.toString(specs.get(match)) + " matched two DIFFERENT locs");
                    firstSig[match] = sig;
                    hits[match]++;
                    System.out.printf("remove %d @ %d,%d,%d (type %d, rot %d)%n", loc.getId(), x, y, z, loc.getType(), loc.getOrientation());
                    continue;
                }
                kept.add(new int[]{loc.getId(), loc.getType(), loc.getOrientation(), z, x & 63, y & 63});
            }
            // a spec may match an exact duplicate (same id, tile, type, rotation -- the map has some): both go
            int removed = 0;
            for (int i = 0; i < hits.length; i++) {
                BuildSanctumMap.check(hits[i] >= 1, "spec " + Arrays.toString(specs.get(i)) + " matched " + hits[i] + " locs");
                removed += hits[i];
            }
            byte[] out = BuildSanctumMap.encodeLocs(kept);
            List<Location> back = new LocationsLoader().load(rx, ry, out).getLocations();
            BuildSanctumMap.check(back.size() == locs.size() - removed, "round-trip count " + back.size());
            // every surviving loc must come back identical (id, position, type, rotation)
            Map<String, Integer> want = new HashMap<>(), got = new HashMap<>();
            for (int[] k : kept)
                want.merge(k[0] + "@" + k[3] + "," + k[4] + "," + k[5] + "/" + k[1] + "/" + k[2], 1, Integer::sum);
            for (Location loc : back)
                got.merge(loc.getId() + "@" + loc.getPosition().getZ() + "," + (loc.getPosition().getX() & 63) + ","
                        + (loc.getPosition().getY() & 63) + "/" + loc.getType() + "/" + loc.getOrientation(), 1, Integer::sum);
            BuildSanctumMap.check(want.equals(got), "round-trip contents");
            System.out.printf("l%d_%d: %d locs -> %d (%d removed), %d -> %d bytes%n", rx, ry, locs.size(), back.size(),
                    removed, raw.length, out.length);
            if (!apply) {
                System.out.println("VERIFY OK -- nothing written.");
                return;
            }
            Container c = new Container(l.getCompression(), -1);
            c.compress(out, null);
            storage.store(maps.getId(), l.getArchiveId(), c.data);
            l.setCrc(c.crc);
            l.setRevision(l.getRevision() + 1);
            l.setCompressedSize(c.data.length);
            l.setDecompressedSize(out.length);
            ImportCathedralMap.writeIndexReferenceTable(storage, maps);
            // read it back from the store
            Archive l2 = maps.findArchiveByName("l" + rx + "_" + ry);
            List<Location> stored = new LocationsLoader().load(rx, ry, l2.decompress(storage.loadArchive(l2))).getLocations();
            BuildSanctumMap.check(stored.size() == back.size(), "stored loc count");
            System.out.println("APPLY complete -- l" + rx + "_" + ry + " revision " + l.getRevision() + ".");
        }
    }
}

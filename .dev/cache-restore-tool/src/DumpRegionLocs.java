import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.*;
import net.runelite.cache.definitions.loaders.*;
import net.runelite.cache.fs.*;
import net.runelite.cache.index.FileData;
import net.runelite.cache.region.Location;

import java.io.File;
import java.util.*;

// Read-only. Lists map locs (with names, type, rotation) inside a world box. Unencrypted regions only (key 0).
// Usage: DumpRegionLocs <cachePath> <x0> <y0> <x1> <y1> [plane]
public class DumpRegionLocs {
    public static void main(String[] args) throws Exception {
        int x0 = Integer.parseInt(args[1]), y0 = Integer.parseInt(args[2]), x1 = Integer.parseInt(args[3]), y1 = Integer.parseInt(args[4]);
        int plane = args.length > 5 ? Integer.parseInt(args[5]) : 0;
        try (Store store = new Store(new File(args[0]))) {
            store.load();
            Archive oa = store.getIndex(IndexType.CONFIGS).getArchive(ConfigType.OBJECT.getId());
            byte[] od = oa.decompress(store.getStorage().loadArchive(oa));
            FileData[] ofd = oa.getFileData();
            List<byte[]> oc = SpliceItemOption.splitChunks(od, ofd.length);
            Map<Integer, byte[]> objs = new HashMap<>();
            for (int i = 0; i < ofd.length; i++) objs.put(ofd[i].getId(), oc.get(i));
            Set<Integer> regions = new TreeSet<>();
            for (int x = x0 >> 6; x <= x1 >> 6; x++) for (int y = y0 >> 6; y <= y1 >> 6; y++) regions.add(x << 8 | y);
            for (int r : regions) {
                int rx = r >> 8, ry = r & 0xFF;
                Index maps = store.getIndex(IndexType.MAPS);
                Archive a = maps.findArchiveByName("l" + rx + "_" + ry);
                if (a == null) { System.out.println("no locs archive for " + r); continue; }
                byte[] data = a.decompress(store.getStorage().loadArchive(a));
                LocationsDefinition ld = new LocationsLoader().load(rx, ry, data);
                System.out.println("region " + r + ": " + ld.getLocations().size() + " locs, first " + (ld.getLocations().isEmpty() ? "-" : ld.getLocations().get(0).getPosition()));
                for (Location l : ld.getLocations()) {
                    int x = (rx << 6) + l.getPosition().getX(), y = (ry << 6) + l.getPosition().getY();
                    if (x < x0 || x > x1 || y < y0 || y > y1 || l.getPosition().getZ() != plane) continue;
                    byte[] b = objs.get(l.getId());
                    ObjectDefinition d = b == null ? null : new ObjectLoader().load(l.getId(), b);
                    System.out.println(x + "," + y + " id=" + l.getId() + " type=" + l.getType() + " rot=" + l.getOrientation()
                            + " " + (d == null ? "?" : d.getName() + " size=" + d.getSizeX() + "x" + d.getSizeY() + " models=" + Arrays.toString(d.getObjectModels())));
                }
            }
        }
    }
}

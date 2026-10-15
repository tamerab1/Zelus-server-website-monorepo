import net.runelite.cache.IndexType;
import net.runelite.cache.ConfigType;
import net.runelite.cache.definitions.MapDefinition;
import net.runelite.cache.definitions.loaders.MapLoader;
import net.runelite.cache.definitions.LocationsDefinition;
import net.runelite.cache.definitions.loaders.LocationsLoader;
import net.runelite.cache.definitions.ObjectDefinition;
import net.runelite.cache.definitions.loaders.ObjectLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;
import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class VerifyRegionParses {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();

            Archive terrainArchive = store.getIndex(IndexType.MAPS).getArchive(3567);
            byte[] terrainRaw = terrainArchive.decompress(storage.loadArchive(terrainArchive));
            MapDefinition map = new MapLoader().load(52, 52, terrainRaw);
            System.out.println("terrain OK: regionX=" + map.getRegionX() + " regionY=" + map.getRegionY());

            Archive locsArchive = store.getIndex(IndexType.MAPS).getArchive(3566);
            byte[] locsRaw = locsArchive.decompress(storage.loadArchive(locsArchive));
            LocationsDefinition locs = new LocationsLoader().load(52, 52, locsRaw);
            System.out.println("locs OK: " + locs.getLocations().size() + " locations parsed");

            Index objIndex = store.getIndex(IndexType.CONFIGS);
            Archive objArchive = objIndex.getArchive(ConfigType.OBJECT.getId());
            byte[] objRaw = objArchive.decompress(storage.loadArchive(objArchive));
            FileData[] objFd = objArchive.getFileData();
            Set<Integer> validIds = new HashSet<>();
            for (FileData fd : objFd) validIds.add(fd.getId());

            Set<Integer> referenced = new HashSet<>();
            for (var loc : locs.getLocations()) referenced.add(loc.getId());
            int missing = 0;
            for (int id : referenced) {
                if (!validIds.contains(id)) {
                    System.out.println("MISSING OBJECT ID: " + id);
                    missing++;
                }
            }
            System.out.println("distinct object ids referenced: " + referenced.size() + ", missing: " + missing);
        }
    }
}

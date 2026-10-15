import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.ObjectDefinition;
import net.runelite.cache.definitions.loaders.ObjectLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;
import java.io.File;
import java.util.List;

public class DumpObjectFull {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        int targetId = Integer.parseInt(args[1]);
        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Index index = store.getIndex(IndexType.CONFIGS);
            Archive archive = index.getArchive(ConfigType.OBJECT.getId());
            byte[] decompressed = archive.decompress(storage.loadArchive(archive));
            FileData[] fileData = archive.getFileData();
            List<byte[]> contents = SpliceItemOption.splitChunks(decompressed, fileData.length);
            ObjectLoader loader = new ObjectLoader();
            for (int i = 0; i < fileData.length; i++) {
                if (fileData[i].getId() == targetId) {
                    ObjectDefinition d = loader.load(targetId, contents.get(i));
                    System.out.println("name=" + d.getName());
                    System.out.println("models=" + java.util.Arrays.toString(d.getObjectModels()));
                    System.out.println("objectTypes=" + java.util.Arrays.toString(d.getObjectTypes()));
                    System.out.println("actions=" + java.util.Arrays.toString(d.getActions()));
                    System.out.println("sizeX=" + d.getSizeX() + " sizeY=" + d.getSizeY());
                    System.out.println("recolorToFind=" + java.util.Arrays.toString(d.getRecolorToFind()));
                    System.out.println("recolorToReplace=" + java.util.Arrays.toString(d.getRecolorToReplace()));
                }
            }
        }
    }
}

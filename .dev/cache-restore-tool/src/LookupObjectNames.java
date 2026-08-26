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

// Read-only: same idea as LookupNpcNames/LookupItemNames but for the object (loc) config archive.
// Usage:
//   lookup <cachePath> <id1,id2,...>
public class LookupObjectNames {
    public static void main(String[] args) throws Exception {
        String mode = args[0];
        String cachePath = args[1];

        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Index index = store.getIndex(IndexType.CONFIGS);
            Archive archive = index.getArchive(ConfigType.OBJECT.getId());

            byte[] decompressed = archive.decompress(storage.loadArchive(archive));
            FileData[] fileData = archive.getFileData();
            List<byte[]> fileContents = SpliceItemOption.splitChunks(decompressed, fileData.length);

            ObjectLoader loader = new ObjectLoader();

            if (mode.equals("lookup")) {
                String[] idsStr = args[2].split(",");
                for (String idStr : idsStr) {
                    int targetId = Integer.parseInt(idStr.trim());
                    boolean found = false;
                    for (int i = 0; i < fileData.length; i++) {
                        if (fileData[i].getId() == targetId) {
                            found = true;
                            try {
                                ObjectDefinition def = loader.load(targetId, fileContents.get(i));
                                System.out.println("id=" + targetId + " slot=" + i + " name=\"" + def.getName()
                                        + "\" objectModels=" + java.util.Arrays.toString(def.getObjectModels())
                                        + " objectTypes=" + java.util.Arrays.toString(def.getObjectTypes())
                                        + " animationID=" + def.getAnimationID()
                                        + " sizeX=" + def.getSizeX() + " sizeY=" + def.getSizeY()
                                        + " category=" + def.getCategory()
                                        + " isHollow=" + def.isHollow()
                                        + " contouredGround=" + def.getContouredGround()
                                        + " mergeNormals=" + def.isMergeNormals()
                                        + " shadow=" + def.isShadow()
                                        + " obstructsGround=" + def.isObstructsGround()
                                        + " ambient=" + def.getAmbient() + " contrast=" + def.getContrast()
                                        + " actions=" + java.util.Arrays.toString(def.getActions()));
                            } catch (Exception e) {
                                System.out.println("id=" + targetId + " slot=" + i + " -- FAILED TO DECODE: " + e);
                            }
                        }
                    }
                    if (!found) {
                        System.out.println("id=" + targetId + " -- NOT FOUND in archive");
                    }
                }
            }
        }
    }
}

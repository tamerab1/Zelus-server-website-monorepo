import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;
import java.io.File;

public class FindMaxIds {
    public static void main(String[] args) throws Exception {
        try (Store store = new Store(new File(args[0]))) {
            store.load();
            Index models = store.getIndex(IndexType.MODELS);
            int maxModel = -1;
            for (Archive a : models.getArchives()) maxModel = Math.max(maxModel, a.getArchiveId());
            System.out.println("max model id: " + maxModel);

            Index configs = store.getIndex(IndexType.CONFIGS);
            Archive seqArchive = configs.getArchive(ConfigType.SEQUENCE.getId());
            int maxSeq = -1;
            for (FileData fd : seqArchive.getFileData()) maxSeq = Math.max(maxSeq, fd.getId());
            System.out.println("max sequence id: " + maxSeq);
        }
    }
}

import io.ruin.api.filestore.FileStore;
import io.ruin.api.filestore.IndexFile;

public class TestItemFileExists {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        FileStore fs = new FileStore(cachePath);
        IndexFile configs = fs.get(2); // CONFIGS
        System.out.println("configs index loaded: " + (configs != null));
        boolean archiveExists = configs.archiveExists(10);
        System.out.println("archive 10 (items) exists: " + archiveExists);
        System.out.println("archive 10 lastFileId: " + configs.getLastFileId(10));
        System.out.println("archive 10 validFilesCount: " + configs.getValidFilesCount(10));
        for (int i = 1; i < args.length; i++) {
            int itemId = Integer.parseInt(args[i]);
            boolean exists = configs.fileExists(10, itemId);
            System.out.println("item " + itemId + ": fileExists=" + exists);
            if (exists) {
                byte[] data = configs.getFile(10, itemId);
                System.out.println("    getFile() length=" + (data == null ? "NULL" : data.length));
            }
        }
    }
}

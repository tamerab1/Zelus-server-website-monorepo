import io.ruin.api.filestore.FileStore;
import io.ruin.api.filestore.IndexFile;

public class TestServerFileStore {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        FileStore fs = new FileStore(cachePath);
        IndexFile models = fs.get(7);
        System.out.println("models index loaded: " + (models != null));
        if (models != null) {
            System.out.println("getLastArchiveId() = " + models.getLastArchiveId());
            for (int i = 1; i < args.length; i++) {
                int id = Integer.parseInt(args[i]);
                boolean exists = models.archiveExists(id);
                System.out.println(id + ": archiveExists=" + exists);
                if (exists) {
                    byte[] data = models.getArchiveData(id);
                    System.out.println("    getArchiveData() length=" + (data == null ? "NULL" : data.length));
                }
            }
        }
    }
}

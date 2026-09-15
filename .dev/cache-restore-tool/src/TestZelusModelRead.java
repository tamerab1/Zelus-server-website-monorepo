import io.ruin.api.filestore.FileStore;

// Same direct-empirical technique as TestZelusItemRead.java, for the MODELS index instead --
// checking whether the 6 imported/remapped models are actually readable via Zelus's own real
// cache-reading code (io.ruin.api.filestore), the same code path the live server itself uses.
public class TestZelusModelRead {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        FileStore store = new FileStore(cachePath);
        // Models in this classic-RS2-style cache are index 7 (single-file archives, one per model
        // id) -- confirmed by main_file_cache.idx7 being the largest/most-recently-touched index
        // file in the client's own local cache after a fresh js5 sync.
        int modelsIndex = 7;
        int[] ids = {61703, 61706, 61824, 61825, 70009, 70010, 28441};
        System.out.println("index " + modelsIndex + " archive count: " + store.get(modelsIndex).getArchivesCount());
        for (int id : ids) {
            boolean exists = store.get(modelsIndex).fileExists(id, 0);
            byte[] data = store.get(modelsIndex).getFile(id, 0);
            System.out.println("model " + id + ": fileExists=" + exists + " getFile=" + (data == null ? "null" : data.length + " bytes"));
        }
    }
}

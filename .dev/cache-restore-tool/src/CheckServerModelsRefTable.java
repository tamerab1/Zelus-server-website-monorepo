import io.ruin.api.filestore.FileStore;
import io.ruin.api.filestore.IndexFile;
import io.ruin.api.filestore.Archive;

// Uses kronos-api's OWN FileStore/IndexFile/ReferenceTable reader (io.ruin.api.filestore) --
// the exact hand-rolled reader the LIVE SERVER uses at runtime, which is a completely separate,
// much stricter implementation from RuneLite's net.runelite.cache library used to WRITE our new
// model/item archives. This checks whether the server's own reference-table decode of index 7
// (MODELS) actually resolves our new model ids correctly, or whether a non-monotonic insertion
// (new ids in the 60300s inserted while existing model ids go up to ~70010) corrupted the
// server's own strict unsigned-delta-chain decode.
public class CheckServerModelsRefTable {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        FileStore store = new FileStore(cachePath);
        IndexFile models = store.get(7);

        System.out.println("MODELS index -- lastArchiveId=" + models.getLastArchiveId());

        for (int i = 1; i < args.length; i++) {
            int id = Integer.parseInt(args[i]);
            boolean exists = models.archiveExists(id);
            Archive archive = models.getArchive(id);
            byte[] data = null;
            Exception readErr = null;
            try {
                data = models.getFile(id);
            } catch (Exception e) {
                readErr = e;
            }
            System.out.println("model " + id + ": archiveExists=" + exists
                    + " archiveObj=" + (archive == null ? "null" : "present")
                    + " getFile=" + (readErr != null ? ("THREW " + readErr) : (data == null ? "null" : data.length + " bytes")));
        }
    }
}

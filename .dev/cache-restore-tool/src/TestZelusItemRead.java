import io.ruin.api.filestore.FileStore;

// Uses Zelus's OWN real cache-reading classes directly (io.ruin.api.filestore.FileStore/IndexFile)
// -- the EXACT code path the live server itself uses -- to settle empirically whether the new Mad
// Angel item ids are actually visible to the server, rather than trusting either RuneLite's own
// reader (used to write them) or theorizing about format differences between the two libraries.
public class TestZelusItemRead {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        FileStore store = new FileStore(cachePath);
        int[] ids = {34027, 34028, 34029, 34030, 34031, 34032, 34033, 34034, 34042, 34043};
        System.out.println("index 2 archive count: " + store.get(2).getArchivesCount());
        System.out.println("archive 10 exists: " + store.get(2).archiveExists(10));
        System.out.println("archive 10 lastFileId: " + store.get(2).getLastFileId(10));
        System.out.println("archive 10 validFilesCount: " + store.get(2).getValidFilesCount(10));
        for (int id : ids) {
            boolean exists = store.get(2).fileExists(10, id);
            byte[] data = store.get(2).getFile(10, id);
            System.out.println("item " + id + ": fileExists=" + exists + " getFile=" + (data == null ? "null" : data.length + " bytes"));
        }
    }
}

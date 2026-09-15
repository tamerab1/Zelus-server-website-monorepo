import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Store;
import java.io.File;

public class CheckArchiveCompression {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        try (Store store = new Store(new File(cachePath))) {
            store.load();
            var modelsIdx = store.getIndex(IndexType.MODELS);
            for (int i = 1; i < args.length; i++) {
                int id = Integer.parseInt(args[i]);
                Archive a = modelsIdx.getArchive(id);
                byte[] raw = store.getStorage().loadArchive(a);
                System.out.println("model " + id + " -> compression=" + a.getCompression()
                        + " compressedSize=" + a.getCompressedSize()
                        + " decompressedSize=" + a.getDecompressedSize()
                        + " revision=" + a.getRevision()
                        + " crc=" + a.getCrc()
                        + " rawStoredLen=" + (raw == null ? "null" : raw.length)
                        + " rawFirst5Bytes=" + (raw == null ? "n/a" : dump(raw, 5)));
            }
        }
    }

    static String dump(byte[] b, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(n, b.length); i++) sb.append(b[i] & 0xFF).append(" ");
        return sb.toString();
    }
}

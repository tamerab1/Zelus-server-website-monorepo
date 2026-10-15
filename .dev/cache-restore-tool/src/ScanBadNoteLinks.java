import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.fs.*;
import net.runelite.cache.index.FileData;
import net.runelite.cache.io.InputStream;
import java.io.File;
import java.util.*;

// Read-only. Flags items whose opcode 97 (notedID) / 98 (notedTemplate) values don't look like real
// note links -- the signature of SetIconFraming.java having written icon offsets into 97/98.
public class ScanBadNoteLinks {
    public static void main(String[] args) throws Exception {
        try (Store store = new Store(new File(args[0]))) {
            store.load();
            Archive archive = store.getIndex(IndexType.CONFIGS).getArchive(ConfigType.ITEM.getId());
            byte[] dec = archive.decompress(store.getStorage().loadArchive(archive));
            FileData[] fd = archive.getFileData();
            List<byte[]> contents = SpliceItemOption.splitChunks(dec, fd.length);
            Set<Integer> ids = new HashSet<>();
            for (FileData f : fd) ids.add(f.getId());
            int flagged = 0;
            for (int i = 0; i < fd.length; i++) {
                byte[] b = contents.get(i);
                InputStream is = new InputStream(b);
                Integer n97 = null, n98 = null;
                try { while (true) {
                    int op = is.readUnsignedByte();
                    if (op == 0) break;
                    int at = is.getOffset();
                    SpliceItemOption.skipOpcodePayload(op, is);
                    if (op == 97) n97 = ((b[at] & 0xff) << 8) | (b[at + 1] & 0xff);
                    if (op == 98) n98 = ((b[at] & 0xff) << 8) | (b[at + 1] & 0xff);
                } } catch (IllegalStateException e) { System.out.println("id " + fd[i].getId() + " skipped (unparsed opcode)"); continue; }
                boolean bad = (n98 != null && n98 != 799) || (n97 != null && !ids.contains(n97));
                if (bad) { flagged++; System.out.println("id " + fd[i].getId() + " op97=" + n97 + " op98=" + n98); }
            }
            System.out.println("flagged: " + flagged);
        }
    }
}

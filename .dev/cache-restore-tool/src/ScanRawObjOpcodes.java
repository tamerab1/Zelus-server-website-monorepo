import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;
import net.runelite.cache.io.InputStream;
import java.io.File;
import java.util.List;

public class ScanRawObjOpcodes {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        int targetId = Integer.parseInt(args[1]);
        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Index index = store.getIndex(IndexType.CONFIGS);
            Archive archive = index.getArchive(ConfigType.OBJECT.getId());
            byte[] decompressed = archive.decompress(storage.loadArchive(archive));
            FileData[] fds = archive.getFileData();
            List<byte[]> contents = SpliceItemOption.splitChunks(decompressed, fds.length);
            int slot = -1;
            for (int i = 0; i < fds.length; i++) if (fds[i].getId() == targetId) slot = i;
            if (slot == -1) { System.out.println("not found"); return; }
            byte[] raw = contents.get(slot);
            System.out.println("id=" + targetId + " bytes=" + raw.length);
            InputStream is = new InputStream(raw);
            while (true) {
                int pos = is.getOffset();
                int opcode = is.readUnsignedByte();
                if (opcode == 0) { System.out.println("  [end at " + pos + "]"); break; }
                int payloadStart = is.getOffset();
                if (opcode == 1) {
                    int count = is.readUnsignedByte();
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < count; i++) {
                        int mid = is.readUnsignedShort();
                        int mtype = is.readUnsignedByte();
                        sb.append("[m=").append(mid).append(",t=").append(mtype).append("]");
                    }
                    System.out.println("  opcode 1 @ " + pos + ": count=" + count + " " + sb);
                } else {
                    InsertObjectActions.skipOpcodePayload(opcode, is);
                    System.out.println("  opcode " + opcode + " @ " + pos + " (payload " + payloadStart + ".." + is.getOffset() + ")");
                }
            }
        }
    }
}

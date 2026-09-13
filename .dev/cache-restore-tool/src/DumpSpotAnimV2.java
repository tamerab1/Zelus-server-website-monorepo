import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.ArchiveFiles;
import net.runelite.cache.fs.FSFile;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.io.InputStream;

import java.io.File;

// Read-only. Corrected spotanim decoder, opcodes translated 1:1 from RS-Realm-Server-Package's
// SpotanimTypeDecoder.kt (adds opcode 3 = wide 4-byte model id, opcode 10 = no-payload flag, both
// missing from net.runelite.cache's SpotAnimLoader). Throws on anything still unrecognized rather
// than silently desyncing.
public class DumpSpotAnimV2 {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Index configs = store.getIndex(IndexType.CONFIGS);
            Archive archive = configs.getArchive(ConfigType.SPOTANIM.getId());
            ArchiveFiles files = archive.getFiles(storage.loadArchive(archive));

            for (int i = 1; i < args.length; i++) {
                int id = Integer.parseInt(args[i]);
                FSFile file = files.findFile(id);
                if (file == null || file.getContents() == null) {
                    System.out.println("spotanim " + id + " -- NOT FOUND");
                    continue;
                }
                try {
                    System.out.println("spotanim " + id + ": " + decode(id, file.getContents()));
                } catch (Exception e) {
                    System.out.println("spotanim " + id + " -- DECODE FAILED: " + e.getMessage());
                }
            }
        }
    }

    static String decode(int id, byte[] b) {
        InputStream is = new InputStream(b);
        int model = -1;
        int anim = -1;
        int resizeH = 128, resizeV = 128, rotation = 0, ambient = 0, contrast = 0;
        while (true) {
            int opcode = is.readUnsignedByte();
            if (opcode == 0) break;
            if (opcode == 1) model = is.readUnsignedShort();
            else if (opcode == 2) anim = is.readUnsignedShort();
            else if (opcode == 3) model = is.readInt();
            else if (opcode == 4) resizeH = is.readUnsignedShort();
            else if (opcode == 5) resizeV = is.readUnsignedShort();
            else if (opcode == 6) rotation = is.readUnsignedShort();
            else if (opcode == 7) ambient = is.readUnsignedByte();
            else if (opcode == 8) contrast = is.readUnsignedByte();
            else if (opcode == 9) is.readString();
            else if (opcode == 10) { /* no payload */ }
            else if (opcode == 40 || opcode == 41) {
                int count = is.readUnsignedByte();
                for (int i = 0; i < count; i++) { is.readUnsignedShort(); is.readUnsignedShort(); }
            } else {
                throw new RuntimeException("TRULY unrecognized spotanim opcode " + opcode + " for id " + id);
            }
        }
        return "model=" + model + " anim=" + anim + " resizeH=" + resizeH + " resizeV=" + resizeV
                + " rotation=" + rotation + " ambient=" + ambient + " contrast=" + contrast;
    }
}

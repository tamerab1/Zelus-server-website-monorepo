import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;
import net.runelite.cache.io.InputStream;

import java.io.File;
import java.nio.file.Files;
import java.util.TreeSet;

// Read-only. Decodes the Cathedral's loc-list bytes (same smart-encoding as
// Zelus's own Region.java: readUnsignedIntSmartShortCompat for both the id-delta and
// position-delta loops) to extract every distinct object id it places, then checks each against
// Zelus's OBJECT config archive (ConfigType.OBJECT) to see which ones don't exist there -- an
// unknown object id is a prime suspect for crashing any client subsystem that walks all loaded map
// locs and looks up each one's ObjectComposition (e.g. a worldmap/minimap indexer), independent of
// whether Mad Angel herself is ever spawned.
public class DecodeCathedralLocs {
    public static void main(String[] args) throws Exception {
        byte[] data = Files.readAllBytes(new File(args[0]).toPath());
        InputStream in = new InputStream(data);

        TreeSet<Integer> objectIds = new TreeSet<>();
        int objectId = -1;
        for (;;) {
            int idOffset = readUnsignedIntSmartShortCompat(in);
            if (idOffset == 0) break;
            objectId += idOffset;
            objectIds.add(objectId);

            for (;;) {
                int positionOffset = readUnsignedIntSmartShortCompat(in);
                if (positionOffset == 0) break;
                in.readUnsignedByte(); // attributes byte (type<<2 | direction)
            }
        }

        System.out.println("distinct object ids referenced: " + objectIds.size());
        System.out.println(objectIds);

        if (args.length > 1) {
            try (Store store = new Store(new File(args[1]))) {
                store.load();
                Index configs = store.getIndex(IndexType.CONFIGS);
                Archive archive = configs.getArchive(ConfigType.OBJECT.getId());
                FileData[] fileData = archive.getFileData();
                TreeSet<Integer> known = new TreeSet<>();
                for (FileData fd : fileData) known.add(fd.getId());

                System.out.println("\nMISSING from Zelus's OBJECT config:");
                for (int id : objectIds) {
                    if (!known.contains(id)) System.out.println("  MISSING: " + id);
                }
            }
        }
    }

    static int readUnsignedIntSmartShortCompat(InputStream in) {
        int value = 0;
        int part;
        do {
            part = readUSmart(in);
            value += part;
        } while (part == 32767);
        return value;
    }

    static int readUSmart(InputStream in) {
        int peek = in.peek() & 0xFF;
        return peek < 128 ? in.readUnsignedByte() : in.readUnsignedShort() - 0x8000;
    }
}

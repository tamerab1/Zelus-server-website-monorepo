import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Store;

import java.io.File;

// Read-only. Scans a candidate range of MODEL ids for free (unoccupied) slots under 65536, so the
// two model collisions currently remapped to 70009/70010 (beyond the 16-bit ceiling opcode-1 can
// address) can instead be remapped to a low, classic-format-safe id.
public class FindFreeModelIds {
    public static void main(String[] args) throws Exception {
        String path = args[0];
        int rangeStart = Integer.parseInt(args[1]);
        int rangeEnd = Integer.parseInt(args[2]);
        int need = args.length > 3 ? Integer.parseInt(args[3]) : 2;

        try (Store store = new Store(new File(path))) {
            store.load();
            var index = store.getIndex(IndexType.MODELS);
            int found = 0;
            for (int id = rangeStart; id <= rangeEnd && found < need; id++) {
                if (index.getArchive(id) == null) {
                    System.out.println("free model id: " + id);
                    found++;
                }
            }
            System.out.println("found " + found + " free ids in [" + rangeStart + "," + rangeEnd + "]");
        }
    }
}

import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.ScriptDefinition;
import net.runelite.cache.definitions.loaders.ScriptLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Container;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.IndexData;

import java.io.File;

// Changes ONE int constant in a clientscript in place (e.g. a hard-coded colour pushed by
// `iconst`). The old value must appear exactly once as a big-endian int in the script bytes AND
// exactly once among the decoded int operands; after the patch the real ScriptLoader must decode
// identical opcodes and operands except that one operand.
// Usage: verify|apply <cachePath> <scriptId> <oldInt> <newInt>
public class PatchClientScriptInt {
    public static void main(String[] args) throws Exception {
        String mode = args[0];
        int scriptId = Integer.parseInt(args[2]);
        int oldVal = Integer.parseInt(args[3]), newVal = Integer.parseInt(args[4]);
        try (Store store = new Store(new File(args[1]))) {
            store.load();
            Storage storage = store.getStorage();
            Index index = store.getIndex(IndexType.CLIENTSCRIPT);
            Archive archive = index.getArchive(scriptId);
            if (archive == null) throw new IllegalStateException("no script " + scriptId);
            byte[] data = archive.decompress(storage.loadArchive(archive));

            int hits = 0, at = -1;
            for (int i = 0; i + 4 <= data.length; i++) {
                int v = ((data[i] & 0xFF) << 24) | ((data[i + 1] & 0xFF) << 16) | ((data[i + 2] & 0xFF) << 8) | (data[i + 3] & 0xFF);
                if (v == oldVal) { hits++; at = i; }
            }
            if (hits != 1) throw new IllegalStateException("old value found " + hits + " times in the bytes -- ABORTING");

            ScriptLoader loader = new ScriptLoader();
            ScriptDefinition before = loader.load(scriptId, data);
            int operandHits = 0;
            for (int v : before.getIntOperands()) if (v == oldVal) operandHits++;
            if (operandHits != 1) throw new IllegalStateException("old value is " + operandHits + " operands -- ABORTING");

            byte[] patched = data.clone();
            patched[at] = (byte) (newVal >> 24);
            patched[at + 1] = (byte) (newVal >> 16);
            patched[at + 2] = (byte) (newVal >> 8);
            patched[at + 3] = (byte) newVal;
            ScriptDefinition after = loader.load(scriptId, patched);

            if (!java.util.Arrays.equals(before.getInstructions(), after.getInstructions()))
                throw new IllegalStateException("instructions changed -- ABORTING");
            int[] b = before.getIntOperands(), a = after.getIntOperands();
            int diffs = 0;
            for (int i = 0; i < b.length; i++) {
                if (b[i] != a[i]) {
                    diffs++;
                    if (b[i] != oldVal || a[i] != newVal) throw new IllegalStateException("unexpected operand change -- ABORTING");
                }
            }
            if (diffs != 1 || !java.util.Arrays.deepEquals(before.getStringOperands(), after.getStringOperands()))
                throw new IllegalStateException("operand check failed -- ABORTING");
            System.out.println("script " + scriptId + ": int operand " + oldVal + " -> " + newVal + " at byte " + at + " (1 operand changed, rest identical)");

            if (mode.equals("verify")) {
                System.out.println("VERIFY OK -- nothing written.");
                return;
            }
            if (!mode.equals("apply")) throw new IllegalArgumentException("mode must be verify or apply");

            Container container = new Container(archive.getCompression(), -1);
            container.compress(patched, null);
            storage.store(index.getId(), archive.getArchiveId(), container.data);
            archive.setCrc(container.crc);
            archive.setRevision(archive.getRevision() + 1);
            archive.setCompressedSize(container.data.length);
            archive.setDecompressedSize(patched.length);

            IndexData indexData = index.toIndexData();
            Container idxContainer = new Container(index.getCompression(), -1);
            idxContainer.compress(indexData.writeIndexData(), null);
            storage.store(255, index.getId(), idxContainer.data);
            index.setCrc(idxContainer.crc);
            System.out.println("APPLY complete, script archive revision " + archive.getRevision());
        }
    }
}

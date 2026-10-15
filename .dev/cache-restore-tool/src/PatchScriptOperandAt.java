import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.ScriptDefinition;
import net.runelite.cache.definitions.loaders.ScriptLoader;
import net.runelite.cache.definitions.savers.ScriptSaver;
import net.runelite.cache.fs.*;
import net.runelite.cache.index.IndexData;

import java.io.File;
import java.util.Arrays;

// Changes ONE int operand of ONE instruction, chosen by position: the first instruction with
// <opcodeName>'s int operand == <old> that directly follows an instruction whose int operand is <anchor>
// (e.g. the `iconst 3` right after `invoke 1138`). Use when the value occurs several times, so
// PatchClientScript's replace-every-occurrence isn't safe.
// Safety: ScriptSaver must reproduce the ORIGINAL bytes exactly first; afterwards only that one operand
// may differ.
// Usage: verify|apply <cachePath> <scriptId> <anchorOperand> <old> <new>
public class PatchScriptOperandAt {
    public static void main(String[] args) throws Exception {
        boolean apply = args[0].equals("apply");
        int scriptId = Integer.parseInt(args[2]), anchor = Integer.parseInt(args[3]);
        int old = Integer.parseInt(args[4]), nu = Integer.parseInt(args[5]);
        try (Store store = new Store(new File(args[1]))) {
            store.load();
            Storage storage = store.getStorage();
            Index index = store.getIndex(IndexType.CLIENTSCRIPT);
            Archive archive = index.getArchive(scriptId);
            byte[] data = archive.decompress(storage.loadArchive(archive));
            ScriptDefinition def = new ScriptLoader().load(scriptId, data);
            byte[] resaved = new ScriptSaver().save(def);
            if (!Arrays.equals(resaved, data))
                throw new IllegalStateException("ScriptSaver does not reproduce script " + scriptId + " exactly -- ABORTING");

            int[] ops = def.getIntOperands();
            int at = -1;
            for (int i = 1; i < ops.length; i++)
                if (ops[i - 1] == anchor && ops[i] == old) {
                    if (at >= 0) throw new IllegalStateException("anchor matched twice -- ambiguous");
                    at = i;
                }
            if (at < 0) throw new IllegalStateException("no operand " + old + " right after anchor " + anchor);
            int[] before = ops.clone();
            ops[at] = nu;
            byte[] patched = new ScriptSaver().save(def);
            ScriptDefinition after = new ScriptLoader().load(scriptId, patched);
            if (!Arrays.equals(after.getInstructions(), def.getInstructions())) throw new IllegalStateException("instructions changed");
            for (int i = 0; i < before.length; i++)
                if (after.getIntOperands()[i] != (i == at ? nu : before[i]))
                    throw new IllegalStateException("operand " + i + " changed unexpectedly");
            System.out.println("script " + scriptId + ": instruction " + at + " operand " + old + " -> " + nu + " (after anchor " + anchor + ")");
            if (!apply) {
                System.out.println("VERIFY OK -- nothing written.");
                return;
            }
            Container c = new Container(archive.getCompression(), -1);
            c.compress(patched, null);
            storage.store(index.getId(), archive.getArchiveId(), c.data);
            archive.setCrc(c.crc);
            archive.setRevision(archive.getRevision() + 1);
            archive.setCompressedSize(c.data.length);
            archive.setDecompressedSize(patched.length);
            IndexData indexData = index.toIndexData();
            Container ic = new Container(index.getCompression(), -1);
            ic.compress(indexData.writeIndexData(), null);
            storage.store(255, index.getId(), ic.data);
            index.setCrc(ic.crc);
            System.out.println("APPLY complete.");
        }
    }
}

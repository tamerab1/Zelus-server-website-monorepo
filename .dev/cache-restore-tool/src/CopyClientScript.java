import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.ScriptDefinition;
import net.runelite.cache.definitions.loaders.ScriptLoader;
import net.runelite.cache.fs.*;

import java.io.File;
import java.util.*;

// Copies a clientscript to a NEW id with chosen int operands changed (PatchClientScript's byte-exact edit + decode
// check), for when the original is shared and must not change. 2026-10-16: 10606 = if_setsize(width, 18, 0, 0) is used
// by six screens; the Zelus Market's slim stock bar needs the same with height 6 -> copy to a new id with 18 -> 6.
// Edits: int:<old>:<new>. Usage: verify|apply <cachePath> <srcScriptId> <newScriptId> <edit> [<edit> ...]
public class CopyClientScript {
    public static void main(String[] args) throws Exception {
        boolean apply = args[0].equals("apply");
        int src = Integer.parseInt(args[2]), dst = Integer.parseInt(args[3]);
        try (Store store = new Store(new File(args[1]))) {
            store.load();
            Storage storage = store.getStorage();
            Index index = store.getIndex(IndexType.CLIENTSCRIPT);
            Archive srcArchive = index.getArchive(src);
            if (srcArchive == null) throw new IllegalStateException("no script " + src);
            if (index.getArchive(dst) != null) throw new IllegalStateException("script " + dst + " already exists -- ABORTING");
            byte[] data = srcArchive.decompress(storage.loadArchive(srcArchive));
            ScriptLoader loader = new ScriptLoader();
            ScriptDefinition before = loader.load(src, data);
            Map<Integer, Integer> ints = new HashMap<>();
            byte[] patched = data;
            for (int a = 4; a < args.length; a++) {
                String[] p = args[a].split(":", 3);
                if (!p[0].equals("int")) throw new IllegalArgumentException(args[a]);
                int o = Integer.parseInt(p[1]), n = Integer.parseInt(p[2]);
                long operands = Arrays.stream(before.getIntOperands()).filter(v -> v == o).count();
                patched = PatchClientScript.replaceAll(patched, PatchClientScript.intBytes(o), PatchClientScript.intBytes(n), operands, args[a]);
                ints.put(o, n);
            }
            ScriptDefinition after = loader.load(dst, patched);
            if (!Arrays.equals(before.getInstructions(), after.getInstructions())) throw new IllegalStateException("instructions changed");
            int[] bi = before.getIntOperands(), ai = after.getIntOperands();
            for (int i = 0; i < bi.length; i++)
                if (ai[i] != ints.getOrDefault(bi[i], bi[i])) throw new IllegalStateException("int operand " + i + " wrong");
            if (after.getIntArgCount() != before.getIntArgCount() || after.getObjArgCount() != before.getObjArgCount())
                throw new IllegalStateException("argument counts changed");
            System.out.println("script " + src + " -> " + dst + ": " + String.join(", ", Arrays.copyOfRange(args, 4, args.length))
                + " (instructions identical, " + after.getIntArgCount() + " int args)");
            if (!apply) {
                System.out.println("VERIFY OK -- nothing written.");
                return;
            }
            BuildChampionshipBoard.storeArchive(storage, index, dst, srcArchive.getCompression(), patched);
            BuildChampionshipBoard.storeIndex(storage, index);
            System.out.println("APPLY complete -- script " + dst + " written.");
        }
    }
}

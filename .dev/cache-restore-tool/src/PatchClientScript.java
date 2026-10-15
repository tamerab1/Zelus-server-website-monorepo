import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.ScriptDefinition;
import net.runelite.cache.definitions.loaders.ScriptLoader;
import net.runelite.cache.fs.*;
import net.runelite.cache.index.IndexData;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.Charset;
import java.util.*;

// Multi-edit version of PatchClientScriptInt: replaces EVERY occurrence of chosen int operands and/or
// string operands in one clientscript. Each edit's byte-pattern hit count must equal its decoded
// operand count (so no unrelated bytes are touched); afterwards the real ScriptLoader must decode the
// same instructions with only the named operands changed.
// Edits: int:<old>:<new>   str:<old>:<new>   (strings may not contain ':')
// Usage: verify|apply <cachePath> <scriptId> <edit> [<edit> ...]
public class PatchClientScript {
    static final Charset CP1252 = Charset.forName("windows-1252");

    public static void main(String[] args) throws Exception {
        String mode = args[0];
        int scriptId = Integer.parseInt(args[2]);
        try (Store store = new Store(new File(args[1]))) {
            store.load();
            Storage storage = store.getStorage();
            Index index = store.getIndex(IndexType.CLIENTSCRIPT);
            Archive archive = index.getArchive(scriptId);
            if (archive == null) throw new IllegalStateException("no script " + scriptId);
            byte[] data = archive.decompress(storage.loadArchive(archive));
            ScriptLoader loader = new ScriptLoader();
            ScriptDefinition before = loader.load(scriptId, data);

            Map<Integer, Integer> ints = new HashMap<>();
            Map<String, String> strs = new HashMap<>();
            byte[] patched = data;
            for (int a = 3; a < args.length; a++) {
                String[] p = args[a].split(":", 3);
                if (p[0].equals("int")) {
                    int o = Integer.parseInt(p[1]), n = Integer.parseInt(p[2]);
                    long operands = Arrays.stream(before.getIntOperands()).filter(v -> v == o).count();
                    patched = replaceAll(patched, intBytes(o), intBytes(n), operands, args[a]);
                    ints.put(o, n);
                } else if (p[0].equals("str")) {
                    long operands = Arrays.stream(before.getStringOperands()).filter(s -> p[1].equals(s)).count();
                    patched = replaceAll(patched, strBytes(p[1]), strBytes(p[2]), operands, args[a]);
                    strs.put(p[1], p[2]);
                } else throw new IllegalArgumentException(args[a]);
            }

            ScriptDefinition after = loader.load(scriptId, patched);
            if (!Arrays.equals(before.getInstructions(), after.getInstructions())) throw new IllegalStateException("instructions changed");
            int[] bi = before.getIntOperands(), ai = after.getIntOperands();
            String[] bs = before.getStringOperands(), as = after.getStringOperands();
            for (int i = 0; i < bi.length; i++) {
                int want = ints.getOrDefault(bi[i], bi[i]);
                if (ai[i] != want) throw new IllegalStateException("int operand " + i + ": " + ai[i] + " != " + want);
                String ws = bs[i] == null ? null : strs.getOrDefault(bs[i], bs[i]);
                if (!Objects.equals(as[i], ws)) throw new IllegalStateException("string operand " + i + ": " + as[i] + " != " + ws);
            }
            System.out.println("script " + scriptId + ": " + String.join(", ", Arrays.copyOfRange(args, 3, args.length))
                    + " -- instructions identical, only these operands changed");
            if (mode.equals("verify")) {
                System.out.println("VERIFY OK -- nothing written.");
                return;
            }
            if (!mode.equals("apply")) throw new IllegalArgumentException("mode must be verify or apply");
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
            System.out.println("APPLY complete, script archive revision " + archive.getRevision());
        }
    }

    static byte[] replaceAll(byte[] b, byte[] from, byte[] to, long expected, String what) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        int hits = 0;
        for (int i = 0; i < b.length; ) {
            if (i + from.length <= b.length && Arrays.equals(Arrays.copyOfRange(b, i, i + from.length), from)) {
                o.write(to, 0, to.length);
                i += from.length;
                hits++;
            } else {
                o.write(b[i++]);
            }
        }
        if (hits != expected || hits == 0)
            throw new IllegalStateException(what + ": " + hits + " byte matches but " + expected + " decoded operands -- ABORTING");
        return o.toByteArray();
    }

    static byte[] intBytes(int v) {
        return new byte[]{(byte) (v >> 24), (byte) (v >> 16), (byte) (v >> 8), (byte) v};
    }

    static byte[] strBytes(String s) {
        byte[] b = s.getBytes(CP1252);
        return Arrays.copyOf(b, b.length + 1);
    }
}

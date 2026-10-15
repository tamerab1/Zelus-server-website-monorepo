import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.EnumDefinition;
import net.runelite.cache.definitions.loaders.EnumLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Container;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;
import net.runelite.cache.index.IndexData;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.*;

// Appends key/value entries to existing enum definitions (e.g. enum 906 = weapon -> special
// attack energy cost, which the combat tab script reads to decide whether to show the spec bar;
// enum 1739 = weapon -> special attack description). Enum format (verified against
// EnumLoader bytecode): op1 keyType u8, op2 valType u8, op3 default string, op4 default int,
// op5 u16 count + (int key, string val)*, op6 u16 count + (int key, int val)*, 0 = end.
// Spec lines:
//   int <enumId> <key> <intVal>
//   str <enumId> <key> <string value...>
// Aborts if a key already exists. Every edited enum is re-decoded and checked (old entries
// unchanged, new entries present) before anything is written.
// Usage: verify|apply <cachePath> <specFile>
public class AppendEnumEntries {
    static final Charset CP1252 = Charset.forName("windows-1252");

    public static void main(String[] args) throws Exception {
        String mode = args[0];
        Map<Integer, List<Object[]>> adds = new TreeMap<>();
        for (String raw : Files.readAllLines(new File(args[2]).toPath())) {
            String l = raw.trim();
            if (l.isEmpty() || l.startsWith("#")) continue;
            String[] p = l.split("\\s+", 4);
            int enumId = Integer.parseInt(p[1]);
            int key = Integer.parseInt(p[2]);
            Object val = p[0].equals("int") ? (Object) Integer.parseInt(p[3]) : p[3];
            if (!p[0].equals("int") && !p[0].equals("str")) throw new IllegalArgumentException(l);
            adds.computeIfAbsent(enumId, k -> new ArrayList<>()).add(new Object[]{key, val});
        }

        try (Store store = new Store(new File(args[1]))) {
            store.load();
            Storage storage = store.getStorage();
            Index index = store.getIndex(IndexType.CONFIGS);
            Archive archive = index.getArchive(ConfigType.ENUM.getId());
            byte[] decompressed = archive.decompress(storage.loadArchive(archive));
            FileData[] fileData = archive.getFileData();
            List<byte[]> contents = SpliceItemOption.splitChunks(decompressed, fileData.length);

            for (var e : adds.entrySet()) {
                int enumId = e.getKey();
                int slot = -1;
                for (int i = 0; i < fileData.length; i++) if (fileData[i].getId() == enumId) slot = i;
                if (slot < 0) throw new IllegalStateException("enum " + enumId + " not found");
                byte[] old = contents.get(slot);
                EnumDefinition before = new EnumLoader().load(enumId, old);
                byte[] edited = append(old, e.getValue());
                EnumDefinition after = new EnumLoader().load(enumId, edited);

                int n = before.getSize();
                if (after.getSize() != n + e.getValue().size()) throw new IllegalStateException("size mismatch " + enumId);
                if (after.getKeyType() != before.getKeyType() || after.getValType() != before.getValType()
                    || after.getDefaultInt() != before.getDefaultInt()
                    || !Objects.equals(after.getDefaultString(), before.getDefaultString()))
                    throw new IllegalStateException("header changed " + enumId);
                for (int i = 0; i < n; i++) {
                    if (after.getKeys()[i] != before.getKeys()[i]) throw new IllegalStateException("key changed " + enumId);
                    if (before.getIntVals() != null && after.getIntVals()[i] != before.getIntVals()[i]) throw new IllegalStateException("val changed " + enumId);
                    if (before.getStringVals() != null && !after.getStringVals()[i].equals(before.getStringVals()[i])) throw new IllegalStateException("str changed " + enumId);
                }
                for (int j = 0; j < e.getValue().size(); j++) {
                    Object[] kv = e.getValue().get(j);
                    int k = (Integer) kv[0];
                    for (int i = 0; i < n; i++) if (before.getKeys()[i] == k) throw new IllegalStateException("enum " + enumId + " already has key " + k);
                    if (after.getKeys()[n + j] != k) throw new IllegalStateException("new key missing " + k);
                    Object got = kv[1] instanceof Integer ? (Object) after.getIntVals()[n + j] : after.getStringVals()[n + j];
                    if (!got.equals(kv[1])) throw new IllegalStateException("new val wrong " + k + ": " + got);
                    System.out.println("enum " + enumId + " +[" + k + " -> " + got + "]");
                }
                System.out.println("enum " + enumId + ": " + n + " -> " + after.getSize() + " entries, verified");
                contents.set(slot, edited);
            }

            if (mode.equals("verify")) {
                System.out.println("VERIFY OK -- nothing written.");
                return;
            }
            if (!mode.equals("apply")) throw new IllegalArgumentException("mode must be verify or apply");

            byte[] newDecompressed = SpliceItemOption.joinChunks(contents);
            Container container = new Container(archive.getCompression(), -1);
            container.compress(newDecompressed, null);
            storage.store(index.getId(), archive.getArchiveId(), container.data);
            archive.setCrc(container.crc);
            archive.setRevision(archive.getRevision() + 1);
            archive.setCompressedSize(container.data.length);
            archive.setDecompressedSize(newDecompressed.length);

            IndexData indexData = index.toIndexData();
            Container idxContainer = new Container(index.getCompression(), -1);
            idxContainer.compress(indexData.writeIndexData(), null);
            storage.store(255, index.getId(), idxContainer.data);
            index.setCrc(idxContainer.crc);
            System.out.println("APPLY complete -- archive revision " + archive.getRevision());
        }
    }

    // Walks the opcode stream and rewrites the op5/op6 entry block with the new pairs appended.
    static byte[] append(byte[] b, List<Object[]> extra) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int pos = 0;
        boolean done = false;
        while (true) {
            int op = b[pos++] & 0xff;
            out.write(op);
            if (op == 0) break;
            switch (op) {
                case 1: case 2: out.write(b[pos++]); break;
                case 3: { int s = pos; while (b[pos] != 0) pos++; pos++; out.write(b, s, pos - s); break; }
                case 4: out.write(b, pos, 4); pos += 4; break;
                case 5: case 6: {
                    int count = ((b[pos] & 0xff) << 8) | (b[pos + 1] & 0xff);
                    int start = pos + 2;
                    pos = start;
                    for (int i = 0; i < count; i++) {
                        pos += 4;
                        if (op == 5) { while (b[pos] != 0) pos++; pos++; } else pos += 4;
                    }
                    int newCount = count + extra.size();
                    if (newCount > 65535) throw new IllegalStateException("too many entries");
                    out.write(newCount >> 8);
                    out.write(newCount);
                    out.write(b, start, pos - start);
                    for (Object[] kv : extra) {
                        writeInt(out, (Integer) kv[0]);
                        if (op == 5) {
                            if (!(kv[1] instanceof String)) throw new IllegalStateException("enum has string values");
                            byte[] s = ((String) kv[1]).getBytes(CP1252);
                            out.write(s, 0, s.length);
                            out.write(0);
                        } else {
                            if (!(kv[1] instanceof Integer)) throw new IllegalStateException("enum has int values");
                            writeInt(out, (Integer) kv[1]);
                        }
                    }
                    done = true;
                    break;
                }
                default: throw new IllegalStateException("unknown enum opcode " + op);
            }
        }
        if (pos != b.length) throw new IllegalStateException("trailing bytes");
        if (!done) throw new IllegalStateException("no entry block");
        return out.toByteArray();
    }

    static void writeInt(ByteArrayOutputStream out, int v) {
        out.write(v >>> 24); out.write(v >>> 16); out.write(v >>> 8); out.write(v);
    }
}

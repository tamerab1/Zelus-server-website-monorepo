import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.ItemDefinition;
import net.runelite.cache.definitions.loaders.ItemLoader;
import net.runelite.cache.fs.*;
import net.runelite.cache.fs.Container;
import net.runelite.cache.index.FileData;
import net.runelite.cache.index.IndexData;

import java.io.File;
import java.nio.file.Files;
import java.util.*;

// Sets an item's inventory options (op30-34 slots 0-4 via opcodes 35-39) and its worn / equipment-tab options (params
// 451-458), REPLACING whatever worn options it had -- e.g. the Rivals capes (2026-10-15) are Max cape clones and had
// inherited "Home" / "Crafting Guild" / "Guild Teleports"... which do nothing on them. Spec lines:
//   item <id> inv=<op0>|<op1>|<op2>|<op3>|<op4> worn=<op>|<op>...      ("-" = no option; worn= may be empty)
// Every other field / param is checked unchanged after a full re-decode. Built on AddMaxCapeOps' param encoder.
// Usage: verify|apply <cachePath> <spec>
public class SetItemWornOps {
    /** Removes one item opcode (+ payload) if present, using the item opcode table (SpliceItemOption). */
    static byte[] removeItemOpcode(byte[] b, int target) {
        net.runelite.cache.io.InputStream is = new net.runelite.cache.io.InputStream(b);
        while (true) {
            int before = is.getOffset();
            int opcode = is.readUnsignedByte();
            if (opcode == 0)
                return b;
            SpliceItemOption.skipOpcodePayload(opcode, is);
            if (opcode == target) {
                int after = is.getOffset();
                byte[] out = new byte[b.length - (after - before)];
                System.arraycopy(b, 0, out, 0, before);
                System.arraycopy(b, after, out, before, b.length - after);
                return out;
            }
        }
    }

    public static void main(String[] args) throws Exception {
        boolean apply = args[0].equals("apply");
        Map<Integer, String[]> inv = new LinkedHashMap<>();
        Map<Integer, List<String>> worn = new LinkedHashMap<>();
        for (String raw : Files.readAllLines(new File(args[2]).toPath())) {
            String l = raw.trim();
            if (l.isEmpty() || l.startsWith("#")) continue;
            String[] p = l.split("\\s+(?=inv=|worn=)");
            if (!p[0].startsWith("item ")) throw new IllegalArgumentException("bad line: " + l);
            int id = Integer.parseInt(p[0].substring(5).trim());
            for (int k = 1; k < p.length; k++) {
                if (p[k].startsWith("inv=")) {
                    String[] ops = p[k].substring(4).split("\\|", -1);
                    if (ops.length != 5) throw new IllegalArgumentException("inv needs 5 ops: " + l);
                    inv.put(id, ops);
                } else if (p[k].startsWith("worn=")) {
                    String v = p[k].substring(5).trim();
                    worn.put(id, v.isEmpty() ? List.of() : Arrays.asList(v.split("\\|")));
                }
            }
        }
        Set<Integer> ids = new LinkedHashSet<>(inv.keySet());
        ids.addAll(worn.keySet());
        try (Store store = new Store(new File(args[1]))) {
            store.load();
            Storage storage = store.getStorage();
            Index index = store.getIndex(IndexType.CONFIGS);
            Archive archive = index.getArchive(ConfigType.ITEM.getId());
            byte[] dec = archive.decompress(storage.loadArchive(archive));
            FileData[] fd = archive.getFileData();
            List<byte[]> contents = SpliceItemOption.splitChunks(dec, fd.length);
            ItemLoader loader = new ItemLoader();
            int updated = 0;
            for (int i = 0; i < fd.length; i++) {
                int id = fd[i].getId();
                if (!ids.contains(id)) continue;
                byte[] work = contents.get(i);
                ItemDefinition before = loader.load(id, work);
                String[] ops = inv.get(id);
                if (ops != null)
                    for (int s = 0; s < 5; s++) {
                        String o = ops[s].equals("-") ? null : ops[s];
                        work = o == null ? removeItemOpcode(work, 35 + s)
                            : SpliceNewPetItem.replaceOrInsertItemOpcode(work, 35 + s, AddMaxCapeOps.cstr(o));
                    }
                Map<Integer, Object> params = new TreeMap<>(before.params == null ? Map.of() : before.params);
                if (worn.containsKey(id)) {
                    for (int k = 451; k <= 458; k++) params.remove(k);
                    int k = 451;
                    for (String w : worn.get(id)) params.put(k++, w);
                    work = SpliceNewPetItem.replaceOrInsertItemOpcode(work, 249, AddMaxCapeOps.encodeParams(params));
                }
                ItemDefinition after = loader.load(id, work);
                if (!Objects.equals(before.name, after.name) || before.inventoryModel != after.inventoryModel
                        || before.maleModel0 != after.maleModel0 || before.zoom2d != after.zoom2d || before.cost != after.cost
                        || before.wearPos1 != after.wearPos1 || before.placeholderId != after.placeholderId
                        || !Arrays.equals(before.colorFind, after.colorFind) || !Arrays.equals(before.colorReplace, after.colorReplace)
                        || !params.equals(new TreeMap<>(after.params == null ? Map.of() : after.params)))
                    throw new IllegalStateException(id + ": verification failed -- ABORTING, nothing written");
                if (ops != null)
                    for (int s = 0; s < 5; s++)
                        if (!Objects.equals(ops[s].equals("-") ? null : ops[s], after.interfaceOptions[s]))
                            throw new IllegalStateException(id + ": inventory slot " + s + " wrong -- ABORTING");
                List<String> wornAfter = new ArrayList<>();
                for (int k = 451; k <= 458; k++)
                    if (after.params != null && after.params.containsKey(k)) wornAfter.add(String.valueOf(after.params.get(k)));
                System.out.println(id + " \"" + after.name + "\" inv=" + Arrays.toString(after.interfaceOptions) + " worn=" + wornAfter);
                contents.set(i, work);
                updated++;
            }
            if (updated != ids.size()) throw new IllegalStateException("only found " + updated + "/" + ids.size() + " -- ABORTING");
            if (!apply) {
                System.out.println("VERIFY OK -- nothing written.");
                return;
            }
            byte[] newDec = SpliceItemOption.joinChunks(contents);
            Container c = new Container(archive.getCompression(), -1);
            c.compress(newDec, null);
            storage.store(index.getId(), archive.getArchiveId(), c.data);
            archive.setCrc(c.crc);
            archive.setRevision(archive.getRevision() + 1);
            archive.setCompressedSize(c.data.length);
            archive.setDecompressedSize(newDec.length);
            IndexData indexData = index.toIndexData();
            Container ic = new Container(index.getCompression(), -1);
            ic.compress(indexData.writeIndexData(), null);
            storage.store(255, index.getId(), ic.data);
            index.setCrc(ic.crc);
            System.out.println("APPLY complete -- " + updated + " item(s), archive revision " + archive.getRevision());
        }
    }
}

import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.fs.*;
import net.runelite.cache.index.FileData;
import net.runelite.cache.index.IndexData;
import java.io.File;
import java.util.*;

// Copies the inventory-icon framing opcodes (4 zoom2d, 5 xan2d, 6 yan2d, 95 zan2d, 7 xOffset2d,
// 8 yOffset2d) as RAW payload bytes from a source item to a target item, touching nothing else.
// An opcode the source lacks is removed from the target (absent = client default). Raw copy on
// purpose: the RuneLite ItemLoader in this repo reads an extra short after opcode 8, which the
// server's ObjType (and SpliceItemOption.skipOpcodePayload) do not -- so no decoded values are
// trusted here, only byte spans from the server-consistent walker.
// NOTE: SetIconFraming.java writes offsets to opcodes 97/98, which are notedID/notedTemplate --
// do not use it for offsets.
//
// Usage: <cachePath> <srcId:dstId>... [--dry]
public class CopyItemFraming {
    static final int[] OPS = {4, 5, 6, 95, 7, 8};

    public static void main(String[] args) throws Exception {
        boolean dry = Arrays.asList(args).contains("--dry");
        try (Store store = new Store(new File(args[0]))) {
            store.load();
            Storage storage = store.getStorage();
            Index index = store.getIndex(IndexType.CONFIGS);
            Archive archive = index.getArchive(ConfigType.ITEM.getId());
            byte[] dec = archive.decompress(storage.loadArchive(archive));
            FileData[] fd = archive.getFileData();
            List<byte[]> contents = SpliceItemOption.splitChunks(dec, fd.length);
            Map<Integer, Integer> slot = new HashMap<>();
            for (int i = 0; i < fd.length; i++) slot.put(fd[i].getId(), i);

            for (int a = 1; a < args.length; a++) {
                if (args[a].startsWith("--")) continue;
                String[] p = args[a].split(":");
                int src = Integer.parseInt(p[0]), dst = Integer.parseInt(p[1]);
                byte[] s = contents.get(slot.get(src));
                byte[] d = contents.get(slot.get(dst));
                int beforeLen = d.length;
                for (int op : OPS) {
                    int[] span = SpliceNewPetItem.findItemOpcodeSpan(s, op);
                    if (span != null) {
                        byte[] payload = Arrays.copyOfRange(s, span[0] + 1, span[1]);
                        d = SpliceNewPetItem.replaceOrInsertItemOpcode(d, op, payload);
                        System.out.println(src + "->" + dst + " op" + op + " = " + hex(payload));
                    } else {
                        d = EditNpcInPlaceItem.remove(d, op);
                        System.out.println(src + "->" + dst + " op" + op + " absent in source -> removed from target");
                    }
                }
                // sanity: target still walks cleanly to its terminator and every non-framing opcode is byte-identical
                if (!nonFraming(contents.get(slot.get(dst))).equals(nonFraming(d)))
                    throw new IllegalStateException(dst + ": a non-framing opcode changed -- ABORTING");
                System.out.println(dst + ": " + beforeLen + " -> " + d.length + " bytes, non-framing opcodes unchanged");
                contents.set(slot.get(dst), d);
            }
            if (dry) { System.out.println("dry run, nothing written"); return; }
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
            System.out.println("Wrote. Item archive revision now " + archive.getRevision());
        }
    }

    static String nonFraming(byte[] b) {
        StringBuilder sb = new StringBuilder();
        net.runelite.cache.io.InputStream is = new net.runelite.cache.io.InputStream(b);
        while (true) {
            int start = is.getOffset();
            int op = is.readUnsignedByte();
            if (op == 0) break;
            SpliceItemOption.skipOpcodePayload(op, is);
            boolean framing = false;
            for (int f : OPS) if (f == op) framing = true;
            if (!framing) sb.append(hex(Arrays.copyOfRange(b, start, is.getOffset()))).append('|');
        }
        return sb.toString();
    }

    static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }
}

class EditNpcInPlaceItem {
    static byte[] remove(byte[] b, int op) {
        int[] span = SpliceNewPetItem.findItemOpcodeSpan(b, op);
        if (span == null) return b;
        byte[] out = new byte[b.length - (span[1] - span[0])];
        System.arraycopy(b, 0, out, 0, span[0]);
        System.arraycopy(b, span[1], out, span[0], b.length - span[1]);
        return out;
    }
}

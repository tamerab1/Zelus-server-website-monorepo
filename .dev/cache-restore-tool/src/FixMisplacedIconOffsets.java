import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.fs.*;
import net.runelite.cache.index.FileData;
import net.runelite.cache.index.IndexData;
import java.io.File;
import java.util.*;

// Repairs items damaged by SetIconFraming.java, which wrote icon offsets into opcodes 97/98
// (notedID/notedTemplate) instead of 7/8 (xOffset2d/yOffset2d). Moves each raw 97/98 payload to
// 7/8 and removes 97/98, leaving every other opcode byte-identical. Refuses any item whose op98 is
// 799 (a genuine note template).
//
// Usage: <cachePath> <itemId,...> [--dry]
public class FixMisplacedIconOffsets {
    public static void main(String[] args) throws Exception {
        boolean dry = args.length > 2 && args[2].equals("--dry");
        Set<Integer> targets = new HashSet<>();
        for (String s : args[1].split(",")) targets.add(Integer.parseInt(s.trim()));
        try (Store store = new Store(new File(args[0]))) {
            store.load();
            Storage storage = store.getStorage();
            Index index = store.getIndex(IndexType.CONFIGS);
            Archive archive = index.getArchive(ConfigType.ITEM.getId());
            byte[] dec = archive.decompress(storage.loadArchive(archive));
            FileData[] fd = archive.getFileData();
            List<byte[]> contents = SpliceItemOption.splitChunks(dec, fd.length);
            int done = 0;
            for (int i = 0; i < fd.length; i++) {
                int id = fd[i].getId();
                if (!targets.contains(id)) continue;
                byte[] b = contents.get(i);
                int[] s97 = SpliceNewPetItem.findItemOpcodeSpan(b, 97), s98 = SpliceNewPetItem.findItemOpcodeSpan(b, 98);
                if (s97 == null || s98 == null) throw new IllegalStateException(id + ": missing op97/98 -- ABORTING");
                byte[] x = Arrays.copyOfRange(b, s97[0] + 1, s97[1]), y = Arrays.copyOfRange(b, s98[0] + 1, s98[1]);
                if ((((y[0] & 0xff) << 8) | (y[1] & 0xff)) == 799) throw new IllegalStateException(id + ": op98 is 799, a real note -- ABORTING");
                String before = CopyItemFraming.nonFraming(b);
                byte[] w = EditNpcInPlaceItem.remove(b, 97);
                w = EditNpcInPlaceItem.remove(w, 98);
                w = SpliceNewPetItem.replaceOrInsertItemOpcode(w, 7, x);
                w = SpliceNewPetItem.replaceOrInsertItemOpcode(w, 8, y);
                String after = CopyItemFraming.nonFraming(w);
                // only difference allowed in the non-framing view: the two 97/98 entries disappearing
                String expected = before.replace("61" + CopyItemFraming.hex(x) + "|", "").replace("62" + CopyItemFraming.hex(y) + "|", "");
                if (!expected.equals(after)) throw new IllegalStateException(id + ": unexpected change -- ABORTING");
                System.out.println(id + ": op97/98 (" + CopyItemFraming.hex(x) + "/" + CopyItemFraming.hex(y) + ") -> op7/op8, removed 97/98");
                contents.set(i, w);
                done++;
            }
            if (done != targets.size()) throw new IllegalStateException("found " + done + "/" + targets.size() + " -- ABORTING");
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
}

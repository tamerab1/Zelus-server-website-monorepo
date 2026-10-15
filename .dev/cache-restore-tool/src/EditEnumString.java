import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.EnumDefinition;
import net.runelite.cache.definitions.loaders.EnumLoader;
import net.runelite.cache.fs.*;
import net.runelite.cache.index.FileData;
import net.runelite.cache.index.IndexData;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

// Replaces one string value of an enum in place (e.g. enum 1141 key 8 "Account Management" -> "PK Hub",
// the side-stone tab tooltip). The old value + its terminator must occur exactly once in the record;
// afterwards the decoded enum must differ only in that value.
// Usage: verify|apply <cachePath> <enumId> <key> <newValue>
public class EditEnumString {
    static final Charset CP1252 = Charset.forName("windows-1252");

    public static void main(String[] args) throws Exception {
        boolean apply = args[0].equals("apply");
        int enumId = Integer.parseInt(args[2]), key = Integer.parseInt(args[3]);
        String nu = args[4];
        try (Store store = new Store(new File(args[1]))) {
            store.load();
            Storage storage = store.getStorage();
            Index index = store.getIndex(IndexType.CONFIGS);
            Archive archive = index.getArchive(ConfigType.ENUM.getId());
            FileData[] fd = archive.getFileData();
            List<byte[]> contents = SpliceItemOption.splitChunks(archive.decompress(storage.loadArchive(archive)), fd.length);
            int slot = -1;
            for (int k = 0; k < fd.length; k++) if (fd[k].getId() == enumId) slot = k;
            if (slot < 0) throw new IllegalStateException("no enum " + enumId);
            byte[] raw = contents.get(slot);
            EnumDefinition before = new EnumLoader().load(enumId, raw);
            int idx = -1;
            for (int i = 0; i < before.getKeys().length; i++) if (before.getKeys()[i] == key) idx = i;
            if (idx < 0 || before.getStringVals() == null) throw new IllegalStateException("no string key " + key);
            String old = before.getStringVals()[idx];

            byte[] oldBytes = (old + "\0").getBytes(CP1252), newBytes = (nu + "\0").getBytes(CP1252);
            int at = -1, hits = 0;
            for (int i = 0; i + oldBytes.length <= raw.length; i++)
                if (Arrays.equals(Arrays.copyOfRange(raw, i, i + oldBytes.length), oldBytes)) { at = i; hits++; }
            if (hits != 1) throw new IllegalStateException("\"" + old + "\" occurs " + hits + " times -- ABORTING");
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            o.write(raw, 0, at);
            o.write(newBytes);
            o.write(raw, at + oldBytes.length, raw.length - at - oldBytes.length);
            byte[] patched = o.toByteArray();

            EnumDefinition after = new EnumLoader().load(enumId, patched);
            for (int i = 0; i < before.getKeys().length; i++) {
                String want = i == idx ? nu : before.getStringVals()[i];
                if (after.getKeys()[i] != before.getKeys()[i] || !Objects.equals(after.getStringVals()[i], want))
                    throw new IllegalStateException("entry " + i + " changed unexpectedly");
            }
            System.out.println("enum " + enumId + " key " + key + ": \"" + old + "\" -> \"" + nu + "\"");
            if (!apply) {
                System.out.println("VERIFY OK -- nothing written.");
                return;
            }
            contents.set(slot, patched);
            byte[] data = SpliceItemOption.joinChunks(contents);
            Container container = new Container(archive.getCompression(), -1);
            container.compress(data, null);
            storage.store(index.getId(), archive.getArchiveId(), container.data);
            archive.setCrc(container.crc);
            archive.setRevision(archive.getRevision() + 1);
            archive.setCompressedSize(container.data.length);
            archive.setDecompressedSize(data.length);
            IndexData indexData = index.toIndexData();
            Container ic = new Container(index.getCompression(), -1);
            ic.compress(indexData.writeIndexData(), null);
            storage.store(255, index.getId(), ic.data);
            index.setCrc(ic.crc);
            System.out.println("APPLY complete.");
        }
    }
}

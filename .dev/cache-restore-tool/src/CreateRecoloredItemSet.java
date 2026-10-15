import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.ItemDefinition;
import net.runelite.cache.definitions.loaders.ItemLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Container;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;
import net.runelite.cache.index.IndexData;
import net.runelite.cache.io.InputStream;
import net.runelite.cache.io.OutputStream;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

// Creates a whole recoloured item set in ONE archive write. For each spec line
// "item <sourceId> <newId> <newPlaceholderId> <name>":
//   - new item  = source bytes with name (op2) replaced, noted link (op97/op98) stripped so it
//     can't turn into a noted SOURCE item, placeholder (op148) repointed at a dedicated new
//     placeholder, and the spec's op40 recolour block (replacing any existing op40).
//   - new placeholder = the source's own placeholder item's bytes with op148 repointed back at
//     the new item (see reference_bank_placeholder_chain).
// Every new record is re-decoded with the real ItemLoader and compared against the source
// before anything is written. All new ids are merge-inserted in sorted order.
//
// Usage: verify|apply <cachePath> <specFile>
public class CreateRecoloredItemSet {

    static class Spec {
        int sourceId, newId, placeholderId;
        String name;
    }

    public static void main(String[] args) throws Exception {
        String mode = args[0];
        String cachePath = args[1];
        List<String> lines = Files.readAllLines(new File(args[2]).toPath());

        short[] find = null, replace = null;
        List<Spec> specs = new ArrayList<>();
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] p = line.split("\\s+", 5);
            if (p[0].equals("recolor")) {
                String[] pairs = p[1].split(",");
                find = new short[pairs.length / 2];
                replace = new short[pairs.length / 2];
                for (int i = 0; i < find.length; i++) {
                    find[i] = (short) Integer.parseInt(pairs[i * 2].trim());
                    replace[i] = (short) Integer.parseInt(pairs[i * 2 + 1].trim());
                }
            } else if (p[0].equals("item")) {
                Spec s = new Spec();
                s.sourceId = Integer.parseInt(p[1]);
                s.newId = Integer.parseInt(p[2]);
                s.placeholderId = Integer.parseInt(p[3]);
                s.name = p[4];
                specs.add(s);
            } else {
                throw new IllegalArgumentException("bad spec line: " + line);
            }
        }
        if (find == null || find.length == 0 || find.length > 255) throw new IllegalStateException("need 1-255 recolor pairs");

        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Index index = store.getIndex(IndexType.CONFIGS);
            Archive archive = index.getArchive(ConfigType.ITEM.getId());

            byte[] decompressed = archive.decompress(storage.loadArchive(archive));
            FileData[] fileData = archive.getFileData();
            List<byte[]> fileContents = SpliceItemOption.splitChunks(decompressed, fileData.length);
            ItemLoader loader = new ItemLoader();

            TreeMap<Integer, byte[]> additions = new TreeMap<>();
            for (Spec s : specs) {
                for (int id : new int[]{s.newId, s.placeholderId}) {
                    if (slotOf(fileData, id) != -1 || additions.containsKey(id)) {
                        throw new IllegalStateException("id " + id + " already exists -- ABORTING");
                    }
                }
                byte[] srcRaw = fileContents.get(requireSlot(fileData, s.sourceId));
                ItemDefinition src = loader.load(s.sourceId, srcRaw);

                // --- the real item ---
                List<int[]> spans = opcodeSpans(srcRaw);
                ByteArrayOutputStream item = new ByteArrayOutputStream();
                boolean wroteName = false, wrotePlaceholder = false;
                for (int[] sp : spans) {
                    int op = srcRaw[sp[0]] & 0xFF;
                    if (op == 97 || op == 98 || op == 40) continue;
                    if (op == 2) {
                        writeName(item, s.name);
                        wroteName = true;
                    } else if (op == 148) {
                        item.write(148);
                        item.write(ushort(s.placeholderId));
                        wrotePlaceholder = true;
                    } else {
                        item.write(srcRaw, sp[0], sp[1] - sp[0]);
                    }
                }
                if (!wroteName) writeName(item, s.name);
                if (!wrotePlaceholder) {
                    item.write(148);
                    item.write(ushort(s.placeholderId));
                }
                OutputStream rc = new OutputStream();
                rc.writeByte(40);
                rc.writeByte(find.length);
                for (int i = 0; i < find.length; i++) {
                    rc.writeShort(find[i] & 0xFFFF);
                    rc.writeShort(replace[i] & 0xFFFF);
                }
                item.write(rc.flip());
                item.write(0);
                byte[] newRaw = item.toByteArray();

                ItemDefinition after = loader.load(s.newId, newRaw);
                check(s.name.equals(after.name), "name");
                check(after.notedID == -1 && after.notedTemplate == -1, "noted link not stripped");
                check(after.placeholderId == s.placeholderId, "placeholderId");
                check(java.util.Arrays.equals(after.colorFind, find) && java.util.Arrays.equals(after.colorReplace, replace), "recolor");
                check(after.inventoryModel == src.inventoryModel && after.maleModel0 == src.maleModel0
                        && after.femaleModel0 == src.femaleModel0 && after.maleModel1 == src.maleModel1
                        && after.femaleModel1 == src.femaleModel1, "models changed");
                check(after.wearPos1 == src.wearPos1 && after.wearPos2 == src.wearPos2 && after.wearPos3 == src.wearPos3, "wearPos");
                check(after.zoom2d == src.zoom2d && after.xan2d == src.xan2d && after.yan2d == src.yan2d
                        && after.zan2d == src.zan2d && after.xOffset2d == src.xOffset2d && after.yOffset2d == src.yOffset2d, "framing");
                check(java.util.Arrays.equals(after.interfaceOptions, src.interfaceOptions)
                        && java.util.Arrays.equals(after.options, src.options), "options");
                check(after.cost == src.cost && after.isTradeable == src.isTradeable && after.stackable == src.stackable
                        && java.util.Objects.equals(after.params, src.params), "cost/tradeable/params");
                additions.put(s.newId, newRaw);

                // --- its dedicated placeholder ---
                check(src.placeholderId != -1, "source " + s.sourceId + " has no placeholder to clone");
                byte[] phSrcRaw = fileContents.get(requireSlot(fileData, src.placeholderId));
                ItemDefinition phSrc = loader.load(src.placeholderId, phSrcRaw);
                check(phSrc.placeholderTemplateId != -1 && phSrc.placeholderId == s.sourceId,
                        "source placeholder " + src.placeholderId + " isn't a proper back-referencing placeholder");
                byte[] phRaw = SpliceNewPetItem.replaceOrInsertItemOpcode(phSrcRaw, 148, SpliceNewPetItem.ushort(s.newId));
                ItemDefinition ph = loader.load(s.placeholderId, phRaw);
                check(ph.placeholderId == s.newId && ph.placeholderTemplateId == phSrc.placeholderTemplateId
                        && ph.inventoryModel == phSrc.inventoryModel, "placeholder");
                additions.put(s.placeholderId, phRaw);

                System.out.println(s.newId + " \"" + after.name + "\" (from " + s.sourceId + " \"" + src.name + "\") inv=" + after.inventoryModel
                        + " male0=" + after.maleModel0 + " female0=" + after.femaleModel0 + " placeholder=" + s.placeholderId
                        + " (cloned from " + src.placeholderId + ", template=" + ph.placeholderTemplateId + ") recolors=" + find.length);
            }

            if (mode.equals("verify")) {
                System.out.println("VERIFY OK -- " + additions.size() + " new records, nothing written.");
                return;
            }
            if (!mode.equals("apply")) throw new IllegalArgumentException("mode must be verify or apply");

            List<FileData> fdList = new ArrayList<>(java.util.Arrays.asList(fileData));
            for (var e : additions.entrySet()) {
                int pos = fdList.size();
                for (int i = 0; i < fdList.size(); i++) {
                    if (fdList.get(i).getId() > e.getKey()) { pos = i; break; }
                }
                FileData fd = new FileData();
                fd.setId(e.getKey());
                fd.setNameHash(-1);
                fdList.add(pos, fd);
                fileContents.add(pos, e.getValue());
            }
            archive.setFileData(fdList.toArray(new FileData[0]));

            byte[] newDecompressed = SpliceItemOption.joinChunks(fileContents);
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

            System.out.println("APPLY complete -- " + additions.size() + " records added, archive revision " + archive.getRevision());
        }
    }

    static List<int[]> opcodeSpans(byte[] b) {
        List<int[]> out = new ArrayList<>();
        InputStream is = new InputStream(b);
        while (true) {
            int start = is.getOffset();
            int op = is.readUnsignedByte();
            if (op == 0) return out;
            SpliceItemOption.skipOpcodePayload(op, is);
            out.add(new int[]{start, is.getOffset()});
        }
    }

    static void writeName(ByteArrayOutputStream o, String name) {
        o.write(2);
        o.writeBytes(name.getBytes(StandardCharsets.US_ASCII));
        o.write(0);
    }

    static byte[] ushort(int v) {
        return new byte[]{(byte) (v >> 8), (byte) v};
    }

    static int slotOf(FileData[] fd, int id) {
        int slot = -1;
        for (int i = 0; i < fd.length; i++) if (fd[i].getId() == id) slot = i; // last duplicate wins
        return slot;
    }

    static int requireSlot(FileData[] fd, int id) {
        int s = slotOf(fd, id);
        if (s == -1) throw new IllegalStateException("id " + id + " not found");
        return s;
    }

    static void check(boolean ok, String what) {
        if (!ok) throw new IllegalStateException("verification failed: " + what + " -- ABORTING, nothing written");
    }
}

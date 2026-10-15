import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.ItemDefinition;
import net.runelite.cache.definitions.NpcDefinition;
import net.runelite.cache.definitions.loaders.ItemLoader;
import net.runelite.cache.definitions.loaders.NpcLoader;
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
import java.util.Map;
import java.util.Objects;

// In-place edits of EXISTING defs (same ids, no new FileData entries), one archive write per run.
//
//   verify|apply <cachePath> itemrecolor <specFile>
//       Re-applies a CreateRecoloredItemSet spec's "recolor" line to every item's <newId>:
//       replaces that item's op40 block. Every other decoded field must stay identical.
//   verify|apply <cachePath> npcname <id>=<name> [<id>=<name> ...]
//   verify|apply <cachePath> itemname <id>=<name> [<id>=<name> ...]
//       Replaces the npc's / item's op2 name. Every other decoded field must stay identical.
//   verify|apply <cachePath> itemop <id>=<slot 0-4>:<text> [...]
//       Sets an item's inventory option text (opcode 35+slot). Every other field must stay identical.
public class UpdateDefsInPlace {
    public static void main(String[] args) throws Exception {
        String mode = args[0];
        String kind = args[2];
        boolean items = kind.equals("itemrecolor") || kind.equals("itemname") || kind.equals("itemop");
        if (!items && !kind.equals("npcname")) throw new IllegalArgumentException("kind must be itemrecolor, itemname or npcname");

        try (Store store = new Store(new File(args[1]))) {
            store.load();
            Storage storage = store.getStorage();
            Index index = store.getIndex(IndexType.CONFIGS);
            Archive archive = index.getArchive(items ? ConfigType.ITEM.getId() : ConfigType.NPC.getId());
            byte[] decompressed = archive.decompress(storage.loadArchive(archive));
            FileData[] fileData = archive.getFileData();
            List<byte[]> contents = SpliceItemOption.splitChunks(decompressed, fileData.length);

            if (kind.equals("itemop")) {
                ItemLoader loader = new ItemLoader();
                for (int a = 3; a < args.length; a++) {
                    String[] kv = args[a].split("=", 2);
                    int id = Integer.parseInt(kv[0]);
                    int optSlot = Integer.parseInt(kv[1].substring(0, kv[1].indexOf(':')));
                    String text = kv[1].substring(kv[1].indexOf(':') + 1);
                    CreateRecoloredItemSet.check(optSlot >= 0 && optSlot <= 4, "slot");
                    int slot = CreateRecoloredItemSet.requireSlot(fileData, id);
                    byte[] oldRaw = contents.get(slot);
                    byte[] tb = text.getBytes(StandardCharsets.US_ASCII);
                    byte[] payload = java.util.Arrays.copyOf(tb, tb.length + 1);
                    byte[] newRaw = SpliceNewPetItem.replaceOrInsertItemOpcode(oldRaw, 35 + optSlot, payload);
                    ItemDefinition before = loader.load(id, oldRaw), after = loader.load(id, newRaw);
                    CreateRecoloredItemSet.check(text.equals(after.interfaceOptions[optSlot]), "option on item " + id);
                    String old = before.interfaceOptions[optSlot];
                    after.interfaceOptions[optSlot] = old;
                    CreateRecoloredItemSet.check(sameFields(before, after), "other fields changed on item " + id);
                    contents.set(slot, newRaw);
                    System.out.println("item " + id + " \"" + after.name + "\": option " + optSlot + " \"" + old + "\" -> \"" + text + "\"");
                }
            } else if (kind.equals("itemname")) {
                ItemLoader loader = new ItemLoader();
                for (int a = 3; a < args.length; a++) {
                    String[] kv = args[a].split("=", 2);
                    int id = Integer.parseInt(kv[0]);
                    int slot = CreateRecoloredItemSet.requireSlot(fileData, id);
                    byte[] oldRaw = contents.get(slot);
                    int[] span = SpliceRecolorCopy.findOpcode2Span(oldRaw, "item");
                    CreateRecoloredItemSet.check(span != null, "item " + id + " has no name opcode");
                    ByteArrayOutputStream o = new ByteArrayOutputStream();
                    o.write(oldRaw, 0, span[0]);
                    o.write(2);
                    o.writeBytes(kv[1].getBytes(StandardCharsets.US_ASCII));
                    o.write(0);
                    o.write(oldRaw, span[1], oldRaw.length - span[1]);
                    byte[] newRaw = o.toByteArray();
                    ItemDefinition before = loader.load(id, oldRaw), after = loader.load(id, newRaw);
                    CreateRecoloredItemSet.check(kv[1].equals(after.name), "name on item " + id);
                    String oldName = before.name;
                    after.name = before.name;
                    CreateRecoloredItemSet.check(sameFields(before, after), "other fields changed on item " + id);
                    contents.set(slot, newRaw);
                    System.out.println("item " + id + ": \"" + oldName + "\" -> \"" + kv[1] + "\"");
                }
            } else if (items) {
                short[] find = null, replace = null;
                List<Integer> ids = new ArrayList<>();
                for (String raw : Files.readAllLines(new File(args[3]).toPath())) {
                    String l = raw.trim();
                    if (l.isEmpty() || l.startsWith("#")) continue;
                    String[] p = l.split("\\s+");
                    if (p[0].equals("recolor")) {
                        String[] pairs = p[1].split(",");
                        find = new short[pairs.length / 2];
                        replace = new short[pairs.length / 2];
                        for (int i = 0; i < find.length; i++) {
                            find[i] = (short) Integer.parseInt(pairs[i * 2]);
                            replace[i] = (short) Integer.parseInt(pairs[i * 2 + 1]);
                        }
                    } else if (p[0].equals("item")) {
                        ids.add(Integer.parseInt(p[2]));
                    }
                }
                ItemLoader loader = new ItemLoader();
                for (int id : ids) {
                    int slot = CreateRecoloredItemSet.requireSlot(fileData, id);
                    byte[] oldRaw = contents.get(slot);
                    ByteArrayOutputStream o = new ByteArrayOutputStream();
                    for (int[] sp : CreateRecoloredItemSet.opcodeSpans(oldRaw)) {
                        if ((oldRaw[sp[0]] & 0xFF) != 40) o.write(oldRaw, sp[0], sp[1] - sp[0]);
                    }
                    OutputStream rc = new OutputStream();
                    rc.writeByte(40);
                    rc.writeByte(find.length);
                    for (int i = 0; i < find.length; i++) {
                        rc.writeShort(find[i] & 0xFFFF);
                        rc.writeShort(replace[i] & 0xFFFF);
                    }
                    o.write(rc.flip());
                    o.write(0);
                    byte[] newRaw = o.toByteArray();
                    ItemDefinition before = loader.load(id, oldRaw), after = loader.load(id, newRaw);
                    CreateRecoloredItemSet.check(java.util.Arrays.equals(after.colorFind, find)
                            && java.util.Arrays.equals(after.colorReplace, replace), "recolor on " + id);
                    after.colorFind = before.colorFind;
                    after.colorReplace = before.colorReplace;
                    CreateRecoloredItemSet.check(sameFields(before, after), "other fields changed on item " + id);
                    contents.set(slot, newRaw);
                    System.out.println("item " + id + " \"" + after.name + "\": recolor replaced (" + find.length + " pairs)");
                }
            } else {
                NpcLoader loader = new NpcLoader();
                for (int a = 3; a < args.length; a++) {
                    String[] kv = args[a].split("=", 2);
                    int id = Integer.parseInt(kv[0]);
                    int slot = CreateRecoloredItemSet.requireSlot(fileData, id);
                    byte[] oldRaw = contents.get(slot);
                    int[] span = SpliceRecolorCopy.findOpcode2Span(oldRaw, "npc");
                    CreateRecoloredItemSet.check(span != null, "npc " + id + " has no name opcode");
                    ByteArrayOutputStream o = new ByteArrayOutputStream();
                    o.write(oldRaw, 0, span[0]);
                    o.write(2);
                    o.writeBytes(kv[1].getBytes(StandardCharsets.US_ASCII));
                    o.write(0);
                    o.write(oldRaw, span[1], oldRaw.length - span[1]);
                    byte[] newRaw = o.toByteArray();
                    NpcDefinition before = loader.load(id, oldRaw), after = loader.load(id, newRaw);
                    CreateRecoloredItemSet.check(kv[1].equals(after.name), "name on npc " + id);
                    String oldName = before.name;
                    after.name = before.name;
                    CreateRecoloredItemSet.check(sameFields(before, after), "other fields changed on npc " + id);
                    contents.set(slot, newRaw);
                    System.out.println("npc " + id + ": \"" + oldName + "\" -> \"" + kv[1] + "\"");
                }
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
            System.out.println("APPLY complete, archive revision " + archive.getRevision());
        }
    }

    // Reflective field-by-field equality over every public instance field (arrays by content).
    static boolean sameFields(Object a, Object b) throws IllegalAccessException {
        for (java.lang.reflect.Field f : a.getClass().getFields()) {
            if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
            Object x = f.get(a), y = f.get(b);
            boolean eq = (x != null && x.getClass().isArray())
                    ? java.util.Objects.deepEquals(x, y)
                    : (x instanceof Map ? Objects.equals(x, y) : Objects.equals(x, y));
            if (!eq) {
                System.out.println("  field differs: " + f.getName());
                return false;
            }
        }
        return true;
    }
}

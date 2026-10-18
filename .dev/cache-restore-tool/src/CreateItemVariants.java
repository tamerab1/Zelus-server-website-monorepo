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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

// Creates same-model variants of an existing item (e.g. per-tier / attuned copies), each with its
// own dedicated bank placeholder, in ONE archive write. Spec lines:
//   variant <sourceId> <newId> <newPlaceholderId> <tradeable 0|1> <op0|op1|op2|op3|op4> <name...>
// ("-" in the option list = no option). New item = source bytes with: name (op2) replaced,
// inventory options (op35-39) replaced, noted link (op97/98) stripped, tradeable flag (op65)
// set/stripped, placeholder (op148) repointed. Placeholder = the source's own placeholder bytes
// with op148 back-referencing the new item. Everything is re-decoded and checked before writing.
// Optional extra lines give a variant its own recolour (op40) / retexture (op41) table, replacing
// any the source had -- e.g. themed copies of a cape whose models bake in texture 59:
//   recolor <newId> <find>:<replace>[,<find>:<replace>...]     (16-bit HSL colours)
//   retexture <newId> <find>:<replace>[,<find>:<replace>...]   (texture ids)
//   placeholderfrom <newId> <placeholderItemId>   build the new placeholder from this (proper)
//                                                placeholder record instead of the source's own
//   strip <newId> <op>[,<op>...]                drops those source opcodes (e.g. 100-109 = stack-size models)
//   models <newId> <oldModel>:<newModel>[,...]   repoints that variant's model ids (inventory, worn,
//                                                and head models) in the same write
// Usage: verify|apply|verify-replace|apply-replace <cachePath> <specFile>
// The -replace modes rebuild variants whose ids already exist (e.g. a new name/recolour for a
// previously created variant), overwriting their records in place instead of refusing.
public class CreateItemVariants {
    public static void main(String[] args) throws Exception {
        String mode = args[0];
        List<String[]> specs = new ArrayList<>();
        java.util.Map<Integer, int[][]> recolors = new java.util.HashMap<>(), retextures = new java.util.HashMap<>();
        java.util.Map<Integer, java.util.Map<Integer, Integer>> modelRemaps = new java.util.HashMap<>();
        java.util.Map<Integer, Integer> placeholderFrom = new java.util.HashMap<>();
        java.util.Map<Integer, java.util.Set<Integer>> strips = new java.util.HashMap<>();
        for (String raw : Files.readAllLines(new File(args[2]).toPath())) {
            String l = raw.trim();
            if (l.isEmpty() || l.startsWith("#")) continue;
            if (l.startsWith("recolor ") || l.startsWith("retexture ")) {
                String[] p = l.split("\\s+");
                if (p.length != 3) throw new IllegalArgumentException("bad line: " + l);
                (p[0].equals("recolor") ? recolors : retextures).put(Integer.parseInt(p[1]), parsePairs(p[2]));
                continue;
            }
            if (l.startsWith("strip ")) {
                String[] p = l.split("\s+");
                java.util.Set<Integer> ops = new java.util.HashSet<>();
                for (String op : p[2].split(",")) ops.add(Integer.parseInt(op));
                strips.put(Integer.parseInt(p[1]), ops);
                continue;
            }
            if (l.startsWith("placeholderfrom ")) {
                String[] p = l.split("\s+");
                placeholderFrom.put(Integer.parseInt(p[1]), Integer.parseInt(p[2]));
                continue;
            }
            if (l.startsWith("models ")) {
                String[] p = l.split("\\s+");
                java.util.Map<Integer, Integer> m = new java.util.HashMap<>();
                for (String pair : p[2].split(",")) {
                    String[] kv = pair.split(":");
                    m.put(Integer.parseInt(kv[0]), Integer.parseInt(kv[1]));
                }
                modelRemaps.put(Integer.parseInt(p[1]), m);
                continue;
            }
            String[] p = l.split("\\s+", 7);
            if (!p[0].equals("variant") || p.length != 7) throw new IllegalArgumentException("bad line: " + l);
            specs.add(p);
        }

        try (Store store = new Store(new File(args[1]))) {
            store.load();
            Storage storage = store.getStorage();
            Index index = store.getIndex(IndexType.CONFIGS);
            Archive archive = index.getArchive(ConfigType.ITEM.getId());
            byte[] decompressed = archive.decompress(storage.loadArchive(archive));
            FileData[] fileData = archive.getFileData();
            List<byte[]> contents = SpliceItemOption.splitChunks(decompressed, fileData.length);
            ItemLoader loader = new ItemLoader();
            TreeMap<Integer, byte[]> additions = new TreeMap<>();
            boolean replace = mode.endsWith("-replace");
            mode = mode.replace("-replace", "");

            for (String[] p : specs) {
                int sourceId = Integer.parseInt(p[1]), newId = Integer.parseInt(p[2]), phId = Integer.parseInt(p[3]);
                boolean tradeable = p[4].equals("1");
                String[] ops = p[5].split("\\|", -1);
                if (ops.length != 5) throw new IllegalArgumentException("need 5 options: " + p[5]);
                String name = p[6];
                for (int id : new int[]{newId, phId}) {
                    CreateRecoloredItemSet.check((replace || CreateRecoloredItemSet.slotOf(fileData, id) == -1) && !additions.containsKey(id),
                            "id " + id + " already exists");
                }
                byte[] src = contents.get(CreateRecoloredItemSet.requireSlot(fileData, sourceId));
                ItemDefinition s = loader.load(sourceId, src);

                ByteArrayOutputStream o = new ByteArrayOutputStream();
                for (int[] sp : CreateRecoloredItemSet.opcodeSpans(src)) {
                    int op = src[sp[0]] & 0xFF;
                    if (op == 2 || (op >= 35 && op <= 39) || op == 97 || op == 98 || op == 65 || op == 148) continue;
                    if ((op == 40 && recolors.containsKey(newId)) || (op == 41 && retextures.containsKey(newId))) continue;
                    if (strips.getOrDefault(newId, java.util.Set.of()).contains(op)) continue;
                    java.util.Map<Integer, Integer> remap = modelRemaps.get(newId);
                    if (remap != null && MODEL_OPS.contains(op)) {
                        byte[] span = java.util.Arrays.copyOfRange(src, sp[0], sp[1]);
                        int old = (span[1] & 0xFF) << 8 | (span[2] & 0xFF);
                        Integer nu = remap.get(old);
                        if (nu != null) {
                            span[1] = (byte) (nu >> 8);
                            span[2] = (byte) (int) nu;
                        }
                        o.write(span, 0, span.length);
                        continue;
                    }
                    o.write(src, sp[0], sp[1] - sp[0]);
                }
                CreateRecoloredItemSet.writeName(o, name);
                for (int i = 0; i < 5; i++) {
                    if (ops[i].equals("-")) continue;
                    o.write(35 + i);
                    o.writeBytes(ops[i].getBytes(StandardCharsets.US_ASCII));
                    o.write(0);
                }
                if (recolors.containsKey(newId)) writePairs(o, 40, recolors.get(newId));
                if (retextures.containsKey(newId)) writePairs(o, 41, retextures.get(newId));
                if (tradeable) o.write(65);
                o.write(148);
                o.write(CreateRecoloredItemSet.ushort(phId));
                o.write(0);
                byte[] newRaw = o.toByteArray();

                ItemDefinition n = loader.load(newId, newRaw);
                CreateRecoloredItemSet.check(name.equals(n.name), "name " + newId);
                for (int i = 0; i < 5; i++) {
                    String want = ops[i].equals("-") ? (i == 4 ? "Drop" : null) : ops[i];
                    // ItemLoader defaults slot 4 to "Drop" when op39 is absent.
                    if (ops[i].equals("-") && i == 4) want = "Drop";
                    CreateRecoloredItemSet.check(java.util.Objects.equals(want, n.interfaceOptions[i]), "option " + i + " on " + newId);
                }
                CreateRecoloredItemSet.check(n.isTradeable == tradeable, "tradeable " + newId);
                CreateRecoloredItemSet.check(n.notedID == -1 && n.notedTemplate == -1 && n.placeholderId == phId, "noted/placeholder " + newId);
                java.util.Map<Integer, Integer> rm = modelRemaps.getOrDefault(newId, java.util.Map.of());
                java.util.function.IntUnaryOperator mapped = v -> rm.getOrDefault(v, v);
                CreateRecoloredItemSet.check(n.maleModel1 == mapped.applyAsInt(s.maleModel1) && n.femaleModel1 == mapped.applyAsInt(s.femaleModel1)
                        && n.maleModel2 == mapped.applyAsInt(s.maleModel2) && n.femaleModel2 == mapped.applyAsInt(s.femaleModel2)
                        && n.maleHeadModel == mapped.applyAsInt(s.maleHeadModel) && n.femaleHeadModel == mapped.applyAsInt(s.femaleHeadModel), "remapped models " + newId);
                for (int v : rm.keySet())
                    CreateRecoloredItemSet.check(v == s.inventoryModel || v == s.maleModel0 || v == s.maleModel1 || v == s.maleModel2
                            || v == s.femaleModel0 || v == s.femaleModel1 || v == s.femaleModel2 || v == s.maleHeadModel || v == s.femaleHeadModel,
                            "model " + v + " is not used by source " + sourceId);
                CreateRecoloredItemSet.check(n.inventoryModel == mapped.applyAsInt(s.inventoryModel) && n.zoom2d == s.zoom2d && n.xan2d == s.xan2d
                        && n.yan2d == s.yan2d && n.cost == s.cost && n.stackable == s.stackable
                        && java.util.Objects.equals(n.params, s.params) && java.util.Arrays.equals(n.options, s.options)
                        && n.maleModel0 == mapped.applyAsInt(s.maleModel0) && n.femaleModel0 == mapped.applyAsInt(s.femaleModel0) && n.wearPos1 == s.wearPos1, "copied fields " + newId);
                // "strip <id> 40,41" (2026-10-18, soulbound v2): the variant must carry no recolour / retexture table at all
                boolean noRecolor = strips.containsKey(newId) && strips.get(newId).contains(40) && !recolors.containsKey(newId);
                boolean noRetexture = strips.containsKey(newId) && strips.get(newId).contains(41) && !retextures.containsKey(newId);
                CreateRecoloredItemSet.check(noRecolor ? n.colorFind == null : recolors.containsKey(newId) ? pairsMatch(recolors.get(newId), n.colorFind, n.colorReplace)
                        : java.util.Arrays.equals(n.colorFind, s.colorFind) && java.util.Arrays.equals(n.colorReplace, s.colorReplace), "recolor " + newId);
                CreateRecoloredItemSet.check(noRetexture ? n.textureFind == null : retextures.containsKey(newId) ? pairsMatch(retextures.get(newId), n.textureFind, n.textureReplace)
                        : java.util.Arrays.equals(n.textureFind, s.textureFind) && java.util.Arrays.equals(n.textureReplace, s.textureReplace), "retexture " + newId);
                if (strips.containsKey(newId) && strips.get(newId).contains(100))
                    CreateRecoloredItemSet.check(n.countObj == null, "count models still present on " + newId);
                additions.put(newId, newRaw);

                int phTemplateItem = placeholderFrom.getOrDefault(newId, s.placeholderId);
                CreateRecoloredItemSet.check(phTemplateItem != -1, "source " + sourceId + " has no placeholder");
                byte[] phSrc = contents.get(CreateRecoloredItemSet.requireSlot(fileData, phTemplateItem));
                ItemDefinition phs = loader.load(phTemplateItem, phSrc);
                CreateRecoloredItemSet.check(phs.placeholderTemplateId != -1 && phs.inventoryModel == 0, "source placeholder not proper");
                byte[] phRaw = SpliceNewPetItem.replaceOrInsertItemOpcode(phSrc, 148, SpliceNewPetItem.ushort(newId));
                ItemDefinition ph = loader.load(phId, phRaw);
                CreateRecoloredItemSet.check(ph.placeholderId == newId && ph.placeholderTemplateId == phs.placeholderTemplateId, "placeholder " + phId);
                additions.put(phId, phRaw);

                System.out.println(newId + " \"" + n.name + "\" tradeable=" + n.isTradeable + " ops=" + java.util.Arrays.toString(n.interfaceOptions)
                        + " placeholder=" + phId + (n.colorFind != null ? " recolors=" + n.colorFind.length : "")
                        + (n.textureFind != null ? " retexture=" + java.util.Arrays.toString(n.textureFind) + "->" + java.util.Arrays.toString(n.textureReplace) : ""));
            }

            if (mode.equals("verify")) {
                System.out.println("VERIFY OK -- " + additions.size() + " new records, nothing written.");
                return;
            }
            if (!mode.equals("apply")) throw new IllegalArgumentException("mode must be verify or apply");

            List<FileData> fdList = new ArrayList<>(java.util.Arrays.asList(fileData));
            for (var e : additions.entrySet()) {
                int existing = CreateRecoloredItemSet.slotOf(fileData, e.getKey());
                if (existing != -1) {
                    contents.set(fdList.indexOf(fileData[existing]), e.getValue());
                    continue;
                }
                int pos = fdList.size();
                for (int i = 0; i < fdList.size(); i++) {
                    if (fdList.get(i).getId() > e.getKey()) { pos = i; break; }
                }
                FileData fd = new FileData();
                fd.setId(e.getKey());
                fd.setNameHash(-1);
                fdList.add(pos, fd);
                contents.add(pos, e.getValue());
            }
            archive.setFileData(fdList.toArray(new FileData[0]));

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
            System.out.println("APPLY complete -- " + additions.size() + " records added, archive revision " + archive.getRevision());
        }
    }

    // Item opcodes holding a u16 model id: inventory, male/female worn 0-2, male/female head models.
    static final java.util.Set<Integer> MODEL_OPS = java.util.Set.of(1, 23, 24, 25, 26, 78, 79, 90, 91, 92, 93);

    static int[][] parsePairs(String s) {
        String[] parts = s.split(",");
        if (parts.length > 255) throw new IllegalArgumentException("too many pairs: " + s);
        int[][] r = new int[parts.length][];
        for (int i = 0; i < parts.length; i++) {
            String[] kv = parts[i].split(":");
            r[i] = new int[]{Integer.parseInt(kv[0]), Integer.parseInt(kv[1])};
            for (int v : r[i]) if (v < 0 || v > 0xFFFF) throw new IllegalArgumentException("not a u16: " + parts[i]);
        }
        return r;
    }

    // op40/op41 payload: u8 count, then count x (u16 find, u16 replace).
    static void writePairs(ByteArrayOutputStream o, int opcode, int[][] pairs) {
        o.write(opcode);
        o.write(pairs.length);
        for (int[] p : pairs) {
            o.writeBytes(CreateRecoloredItemSet.ushort(p[0]));
            o.writeBytes(CreateRecoloredItemSet.ushort(p[1]));
        }
    }

    static boolean pairsMatch(int[][] pairs, short[] find, short[] replace) {
        if (find == null || replace == null || find.length != pairs.length || replace.length != pairs.length) return false;
        for (int i = 0; i < pairs.length; i++) {
            if ((find[i] & 0xFFFF) != pairs[i][0] || (replace[i] & 0xFFFF) != pairs[i][1]) return false;
        }
        return true;
    }
}

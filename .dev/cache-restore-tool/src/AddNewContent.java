import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.ItemDefinition;
import net.runelite.cache.definitions.NpcDefinition;
import net.runelite.cache.definitions.SequenceDefinition;
import net.runelite.cache.definitions.SpriteDefinition;
import net.runelite.cache.definitions.loaders.ItemLoader;
import net.runelite.cache.definitions.loaders.NpcLoader;
import net.runelite.cache.definitions.loaders.SequenceLoader;
import net.runelite.cache.definitions.loaders.SpriteLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Container;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.fs.jagex.CompressionType;
import net.runelite.cache.index.FileData;
import net.runelite.cache.index.IndexData;
import net.runelite.cache.io.OutputStream;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Adds brand-new items/npcs/sequences/models/sprites/frame-archives/skeletons to the live cache,
// built fresh from a directory of "cache-overlay"-style toml/bin/png source files (see
// PORTABLE-MANIFEST.json format from the drag-drop-for-other-server package). Every new content
// id is verified free (via CheckCollisions/LookupItemNames/LookupNpcNames) BEFORE this tool ever
// runs -- this tool refuses to overwrite any id that already has a file slot / archive.
//
// Multi-file archives (items, npcs, sequences -- all live in one archive per ConfigType inside
// the CONFIGS index) use the same List-backed positional splitter/joiner as SpliceItemOption.java
// to tolerate this archive's known pre-existing duplicate-file-id entries, and append new
// FileData/content entries exactly like SpliceNewPetItem.java's already-validated pattern.
//
// Single-file archives (models, sprites, frame archives, skeletons) use Index.addArchive(id),
// the library's own official "add a new archive" API.
//
// Modes (dry-run unless "apply" is passed as the last argument):
//   additems      <cachePath> <overlayDir> [apply]
//   addnpcs       <cachePath> <overlayDir> [apply]
//   addseqs       <cachePath> <overlayDir> [apply]
//   addmodels     <cachePath> <overlayDir> [apply]
//   addsprites    <cachePath> <overlayDir> [apply]
//   addframes     <cachePath> <overlayDir> [apply]
//   addskeletons  <cachePath> <overlayDir> [apply]
public class AddNewContent {

    public static void main(String[] args) throws Exception {
        String mode = args[0];
        String cachePath = args[1];
        String overlayDir = args[2];
        boolean apply = args.length > 3 && args[3].equals("apply");

        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();

            switch (mode) {
                case "additems":
                    addConfigEntries(store, storage, ConfigType.ITEM.getId(), new File(overlayDir, "item"), apply,
                            AddNewContent::parseItemToml, AddNewContent::encodeItem,
                            (id, bytes) -> describeItem(id, bytes));
                    break;
                case "addnpcs":
                    addConfigEntries(store, storage, ConfigType.NPC.getId(), new File(overlayDir, "npc"), apply,
                            AddNewContent::parseNpcToml, AddNewContent::encodeNpc,
                            (id, bytes) -> describeNpc(id, bytes));
                    break;
                case "addseqs":
                    addConfigEntries(store, storage, ConfigType.SEQUENCE.getId(), new File(overlayDir, "seq"), apply,
                            AddNewContent::parseSeqToml, AddNewContent::encodeSeq,
                            (id, bytes) -> describeSeq(id, bytes));
                    break;
                case "addmodels":
                    addSingleFileArchives(store, storage, IndexType.MODELS, new File(overlayDir, "model"), apply,
                            true);
                    break;
                case "addsprites":
                    addSprites(store, storage, new File(overlayDir, "sprite"), apply);
                    break;
                case "addframes":
                    addSingleFileArchives(store, storage, IndexType.ANIMATIONS, new File(overlayDir, "binary/0"),
                            apply, false);
                    break;
                case "addskeletons":
                    addSingleFileArchives(store, storage, IndexType.SKELETONS, new File(overlayDir, "binary/1"),
                            apply, false);
                    break;
                case "addinterface": {
                    int newInterfaceId = Integer.parseInt(args[3]);
                    boolean applyIface = args.length > 4 && args[4].equals("apply");
                    addInterface(store, storage, new File(overlayDir, "interface"), newInterfaceId, applyIface);
                    break;
                }
                case "fixbankbutton": {
                    boolean applyFix = args.length > 3 && args[3].equals("apply");
                    fixBankPresetButton(store, storage, applyFix);
                    break;
                }
                case "addtextures":
                    addTextures(store, storage, new File(overlayDir, "selected-textures.toml"), apply);
                    break;
                case "fixframemeta": {
                    boolean applyFix = args.length > 3 && args[3].equals("apply");
                    fixFrameMeta(store, storage, IndexType.ANIMATIONS, new File(overlayDir, "binary/0"), applyFix);
                    fixFrameMeta(store, storage, IndexType.SKELETONS, new File(overlayDir, "binary/1"), applyFix);
                    break;
                }
                case "fixinterfacesprite": {
                    boolean applyFix = args.length > 3 && args[3].equals("apply");
                    Map<Integer, Integer> spriteRemap = Map.of(900, 90000, 901, 90001);
                    fixInterfaceSprite(store, storage, new File(overlayDir, "interface"), 5900, 86,
                            spriteRemap, applyFix);
                    break;
                }
                case "fixtextureids": {
                    boolean applyFix = args.length > 3 && args[3].equals("apply");
                    fixTextureIds(store, storage, new File(overlayDir, "selected-textures.toml"),
                            new File(overlayDir, "model"), applyFix);
                    break;
                }
                default:
                    throw new IllegalArgumentException("unknown mode " + mode);
            }
        }
    }

    interface TomlParser {
        Map<String, Object> parse(String text);
    }

    interface Encoder {
        byte[] encode(int id, Map<String, Object> fields);
    }

    interface Describer {
        String describe(int id, byte[] bytes);
    }

    // ============================== multi-file config archives ==============================

    static void addConfigEntries(Store store, Storage storage, int configTypeId, File dir, boolean apply,
                                  TomlParser parser, Encoder encoder, Describer describer) throws Exception {
        Index index = store.getIndex(IndexType.CONFIGS);
        Archive archive = index.getArchive(configTypeId);

        byte[] compressed = storage.loadArchive(archive);
        byte[] decompressed = archive.decompress(compressed);
        FileData[] fileData = archive.getFileData();
        System.out.println("Archive " + configTypeId + ": " + fileData.length + " existing file slots.");

        List<byte[]> fileContents = SpliceItemOption.splitChunks(decompressed, fileData.length);

        File[] files = dir.listFiles((d, name) -> name.endsWith(".toml"));
        if (files == null) {
            throw new IllegalStateException("no toml files found in " + dir);
        }
        java.util.Arrays.sort(files);

        List<Integer> newIds = new ArrayList<>();
        List<byte[]> newRaws = new ArrayList<>();

        for (File f : files) {
            String text = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            Map<String, Object> fields = parser.parse(text);
            int id = ((Number) fields.get("id")).intValue();

            for (FileData fd : fileData) {
                if (fd.getId() == id) {
                    throw new IllegalStateException(f + ": id " + id + " already exists in archive "
                            + configTypeId + " -- ABORTING, refusing to overwrite.");
                }
            }
            for (int existing : newIds) {
                if (existing == id) {
                    throw new IllegalStateException(f + ": id " + id + " duplicated within this batch -- ABORTING.");
                }
            }

            byte[] raw = encoder.encode(id, fields);
            System.out.println(f.getName() + " -> id " + id + ": " + describer.describe(id, raw)
                    + " (" + raw.length + " bytes)");

            newIds.add(id);
            newRaws.add(raw);
        }

        System.out.println("Prepared " + newIds.size() + " new entries for archive " + configTypeId + ".");

        if (!apply) {
            System.out.println("DRY RUN -- not writing. Re-run with 'apply' to persist.");
            return;
        }

        // IndexData.writeIndexData() delta-encodes file ids as (id[i] - id[i-1]) and requires a
        // non-negative, strictly-increasing sequence across the WHOLE positional array (confirmed
        // via CheckMonotonic: this archive's existing 0 violations depend on that staying true) --
        // a blind append at the end would insert small new ids after a much larger last existing
        // id, go negative, and silently corrupt every id from that point on. Merge-insert instead,
        // preserving sorted order exactly like a merge step of merge-sort.
        List<Integer> sortOrder = new ArrayList<>();
        for (int i = 0; i < newIds.size(); i++) sortOrder.add(i);
        sortOrder.sort((a, b) -> Integer.compare(newIds.get(a), newIds.get(b)));

        List<FileData> mergedFileData = new ArrayList<>(fileData.length + newIds.size());
        List<byte[]> mergedContents = new ArrayList<>(fileData.length + newIds.size());
        int ni = 0;
        for (int i = 0; i < fileData.length; i++) {
            while (ni < sortOrder.size() && newIds.get(sortOrder.get(ni)) < fileData[i].getId()) {
                int idx = sortOrder.get(ni);
                FileData nfd = new FileData();
                nfd.setId(newIds.get(idx));
                nfd.setNameHash(-1);
                mergedFileData.add(nfd);
                mergedContents.add(newRaws.get(idx));
                ni++;
            }
            mergedFileData.add(fileData[i]);
            mergedContents.add(fileContents.get(i));
        }
        while (ni < sortOrder.size()) {
            int idx = sortOrder.get(ni);
            FileData nfd = new FileData();
            nfd.setId(newIds.get(idx));
            nfd.setNameHash(-1);
            mergedFileData.add(nfd);
            mergedContents.add(newRaws.get(idx));
            ni++;
        }

        archive.setFileData(mergedFileData.toArray(new FileData[0]));
        fileContents = mergedContents;

        byte[] newDecompressed = SpliceItemOption.joinChunks(fileContents);
        Container container = new Container(archive.getCompression(), -1);
        container.compress(newDecompressed, null);

        storage.store(index.getId(), archive.getArchiveId(), container.data);
        archive.setCrc(container.crc);
        archive.setRevision(archive.getRevision() + 1);
        archive.setCompressedSize(container.data.length);
        archive.setDecompressedSize(newDecompressed.length);

        writeIndexReferenceTable(storage, index);

        System.out.println("APPLY complete. Archive " + configTypeId + " revision now " + archive.getRevision()
                + ", " + newIds.size() + " new entries added.");
    }

    static void writeIndexReferenceTable(Storage storage, Index index) throws Exception {
        IndexData indexData = index.toIndexData();
        byte[] rawIndex = indexData.writeIndexData();
        Container idxContainer = new Container(index.getCompression(), -1);
        idxContainer.compress(rawIndex, null);
        storage.store(255, index.getId(), idxContainer.data);
        index.setCrc(idxContainer.crc);
    }

    // ============================== single-file archives ==============================

    // decompressedInput: true if the source .bin is raw decompressed content that this tool must
    // wrap via Container.compress() (models); false if the source .bin is already a complete,
    // pre-wrapped container (compression byte + length + stream) ready for storage.store() as-is
    // (frame archives / skeletons -- confirmed via Container.decompress() round-tripping cleanly).
    static void addSingleFileArchives(Store store, Storage storage, IndexType indexType, File dir, boolean apply,
                                       boolean decompressedInput) throws Exception {
        Index index = store.getIndex(indexType);
        File[] files = dir.listFiles((d, name) -> name.endsWith(".bin"));
        if (files == null) {
            throw new IllegalStateException("no .bin files found in " + dir);
        }
        java.util.Arrays.sort(files);

        List<Integer> ids = new ArrayList<>();
        List<byte[]> containerBytes = new ArrayList<>();
        List<Container> parsed = new ArrayList<>();

        List<int[]> fileIdLists = new ArrayList<>();
        for (File f : files) {
            int id = Integer.parseInt(f.getName().replace(".bin", ""));
            if (index.getArchive(id) != null) {
                throw new IllegalStateException(f + ": archive " + id + " already exists in " + indexType
                        + " -- ABORTING, refusing to overwrite.");
            }
            byte[] raw = Files.readAllBytes(f.toPath());

            byte[] toStore;
            Container c;
            if (decompressedInput) {
                // pick a compression matching this index's existing convention: reuse whatever
                // compression an already-loaded archive in this index uses, default GZ if index empty.
                int compression = defaultCompressionFor(index);
                c = new Container(compression, -1);
                c.compress(raw, null);
                toStore = c.data;
            } else {
                c = Container.decompress(raw, null);
                toStore = raw;
            }

            // Frame archives / skeletons ship a sidecar .meta with the REAL sub-file ids (a frame
            // archive packs many individual frame poses as separate files, e.g. 236-247 -- a
            // single placeholder FileData entry using the archive's own id would leave the
            // reference table claiming it has only one wrongly-numbered file, even though the
            // stored bytes' own internal chunk structure is already correct). Models have no
            // .meta sidecar and are read as a whole archive, so they keep the single-placeholder
            // fallback.
            File metaFile = new File(f.getParentFile(), f.getName().replace(".bin", ".meta"));
            int[] fileIds;
            if (metaFile.exists()) {
                fileIds = parseMetaFileIds(metaFile);
                System.out.println(f.getName() + " -> archive " + id + ": " + fileIds.length
                        + " sub-files from .meta: " + java.util.Arrays.toString(fileIds));
            } else {
                fileIds = new int[]{id};
            }

            System.out.println(f.getName() + " -> archive " + id + ": compression=" + c.compression
                    + " decompressedLen=" + c.data.length + " storedLen=" + toStore.length);

            ids.add(id);
            containerBytes.add(toStore);
            parsed.add(c);
            fileIdLists.add(fileIds);
        }

        System.out.println("Prepared " + ids.size() + " new single-file archives for " + indexType + ".");
        if (!apply) {
            System.out.println("DRY RUN -- not writing. Re-run with 'apply' to persist.");
            return;
        }

        for (int i = 0; i < ids.size(); i++) {
            int id = ids.get(i);
            Archive archive = index.addArchive(id);
            archive.setNameHash(-1);
            archive.setCompression(parsed.get(i).compression);
            archive.setCrc(parsed.get(i).crc);
            archive.setRevision(1);
            archive.setCompressedSize(containerBytes.get(i).length);
            archive.setDecompressedSize(parsed.get(i).data.length);
            int[] fileIds = fileIdLists.get(i);
            FileData[] fds = new FileData[fileIds.length];
            for (int j = 0; j < fileIds.length; j++) {
                fds[j] = singleFileData(fileIds[j]);
            }
            archive.setFileData(fds);

            storage.store(index.getId(), id, containerBytes.get(i));
        }

        writeIndexReferenceTable(storage, index);
        System.out.println("APPLY complete. " + ids.size() + " new archives added to " + indexType + ".");
    }

    // Corrects the FileData already stored for the frame/skeleton archives added by an earlier
    // addframes/addskeletons run: only the reference-table metadata was wrong (a single
    // placeholder entry keyed by the archive's own id), the actual stored bytes never needed to
    // change (their internal chunk structure already matches these real sub-file ids).
    static void fixFrameMeta(Store store, Storage storage, IndexType indexType, File dir, boolean apply)
            throws Exception {
        Index index = store.getIndex(indexType);
        File[] files = dir.listFiles((d, name) -> name.endsWith(".bin"));
        java.util.Arrays.sort(files);

        for (File f : files) {
            int archiveId = Integer.parseInt(f.getName().replace(".bin", ""));
            Archive archive = index.getArchive(archiveId);
            if (archive == null) {
                throw new IllegalStateException("archive " + archiveId + " not found in " + indexType
                        + " -- run addframes/addskeletons first.");
            }
            File metaFile = new File(f.getParentFile(), f.getName().replace(".bin", ".meta"));
            int[] fileIds = parseMetaFileIds(metaFile);
            FileData[] current = archive.getFileData();
            System.out.println(indexType + " archive " + archiveId + ": currently " + current.length
                    + " file(s) " + java.util.Arrays.toString(java.util.Arrays.stream(current)
                    .mapToInt(FileData::getId).toArray())
                    + " -> should be " + fileIds.length + " file(s) " + java.util.Arrays.toString(fileIds));

            if (!apply) continue;
            FileData[] fds = new FileData[fileIds.length];
            for (int j = 0; j < fileIds.length; j++) {
                fds[j] = singleFileData(fileIds[j]);
            }
            archive.setFileData(fds);
        }

        if (!apply) {
            System.out.println("DRY RUN -- not writing. Re-run with 'apply' to persist.");
            return;
        }
        writeIndexReferenceTable(storage, index);
        System.out.println("APPLY complete for " + indexType + ".");
    }

    static int defaultCompressionFor(Index index) {
        for (Archive a : index.getArchives()) {
            return a.getCompression();
        }
        return CompressionType.GZ;
    }

    static FileData singleFileData(int id) {
        FileData fd = new FileData();
        fd.setId(id);
        fd.setNameHash(-1);
        return fd;
    }

    // Parses the sub-file ids out of a "[[files]]\nid = N" repeated-block .meta.toml sidecar --
    // deliberately ignores the single "id = N" line under the leading "[meta]" table (that's the
    // archive's OWN id, not a sub-file id).
    static int[] parseMetaFileIds(File metaFile) throws Exception {
        String text = new String(Files.readAllBytes(metaFile.toPath()), StandardCharsets.UTF_8);
        List<Integer> ids = new ArrayList<>();
        boolean inFilesBlock = false;
        for (String rawLine : text.split("\n")) {
            String line = rawLine.trim();
            if (line.equals("[[files]]")) {
                inFilesBlock = true;
                continue;
            }
            if (line.startsWith("[") && !line.equals("[[files]]")) {
                inFilesBlock = false;
                continue;
            }
            if (inFilesBlock && line.startsWith("id")) {
                int eq = line.indexOf('=');
                if (eq >= 0) {
                    ids.add(Integer.parseInt(line.substring(eq + 1).trim()));
                }
            }
        }
        if (ids.isEmpty()) {
            throw new IllegalStateException("no [[files]] entries found in " + metaFile);
        }
        return ids.stream().mapToInt(Integer::intValue).toArray();
    }

    // ============================== sprites (PNG -> indexed sprite archive) ==============================

    static void addSprites(Store store, Storage storage, File spriteDir, boolean apply) throws Exception {
        Index index = store.getIndex(IndexType.SPRITES);
        File[] dirs = spriteDir.listFiles(File::isDirectory);
        if (dirs == null) {
            throw new IllegalStateException("no sprite subfolders found in " + spriteDir);
        }
        java.util.Arrays.sort(dirs);

        List<Integer> ids = new ArrayList<>();
        List<byte[]> containerBytes = new ArrayList<>();
        List<Container> parsed = new ArrayList<>();

        for (File d : dirs) {
            int id = Integer.parseInt(d.getName());
            if (index.getArchive(id) != null) {
                throw new IllegalStateException(d + ": sprite archive " + id + " already exists -- ABORTING.");
            }
            File png = new File(d, "0.png");
            BufferedImage img = ImageIO.read(png);
            byte[] raw = encodeSprite(img);

            int compression = defaultCompressionFor(index);
            Container c = new Container(compression, -1);
            c.compress(raw, null);

            // self-verify: decode what we just built with the real SpriteLoader and confirm pixels
            // round-trip before trusting it.
            SpriteDefinition[] roundTrip = new SpriteLoader().load(id, raw);
            if (roundTrip.length != 1 || roundTrip[0].getWidth() != img.getWidth()
                    || roundTrip[0].getHeight() != img.getHeight()) {
                throw new IllegalStateException(d + ": sprite round-trip verification FAILED -- ABORTING.");
            }
            for (int y = 0; y < img.getHeight(); y++) {
                for (int x = 0; x < img.getWidth(); x++) {
                    int expected = img.getRGB(x, y);
                    int actual = roundTrip[0].getPixels()[y * img.getWidth() + x];
                    if (!argbCloseEnough(expected, actual)) {
                        throw new IllegalStateException(d + ": pixel mismatch at (" + x + "," + y + ") expected="
                                + Integer.toHexString(expected) + " actual=" + Integer.toHexString(actual)
                                + " -- ABORTING.");
                    }
                }
            }

            System.out.println(d.getName() + " -> sprite " + id + ": " + img.getWidth() + "x" + img.getHeight()
                    + " verified OK (" + c.data.length + " bytes)");

            ids.add(id);
            containerBytes.add(c.data);
            parsed.add(c);
        }

        System.out.println("Prepared " + ids.size() + " new sprite archives.");
        if (!apply) {
            System.out.println("DRY RUN -- not writing. Re-run with 'apply' to persist.");
            return;
        }

        for (int i = 0; i < ids.size(); i++) {
            int id = ids.get(i);
            Archive archive = index.addArchive(id);
            archive.setNameHash(-1);
            archive.setCompression(parsed.get(i).compression);
            archive.setCrc(parsed.get(i).crc);
            archive.setRevision(1);
            archive.setCompressedSize(containerBytes.get(i).length);
            archive.setDecompressedSize(parsed.get(i).data.length);
            archive.setFileData(new FileData[]{singleFileData(0)});

            storage.store(index.getId(), id, containerBytes.get(i));
        }

        writeIndexReferenceTable(storage, index);
        System.out.println("APPLY complete. " + ids.size() + " new sprite archives added.");
    }

    // alpha channel: transparent pixels (alpha==0) are allowed to differ in RGB (they're not
    // rendered), everything else must match on alpha and on RGB whenever alpha > 0.
    static boolean argbCloseEnough(int expected, int actual) {
        int ea = (expected >>> 24) & 0xFF;
        int aa = (actual >>> 24) & 0xFF;
        if (ea == 0 && aa == 0) return true;
        if (expected == actual) return true;
        // encodeSprite() deliberately nudges a literal opaque-black (rgb==0) pixel to rgb==1 to
        // avoid colliding with the format's reserved transparent-palette-index 0 -- tolerate that
        // specific, known, single-bit round-trip artifact.
        return ea == aa && (expected & 0xFFFFFF) == 0 && (actual & 0xFFFFFF) == 1;
    }

    static byte[] encodeSprite(BufferedImage img) {
        int width = img.getWidth();
        int height = img.getHeight();
        int dimension = width * height;

        // build palette from unique opaque colors (index 0 reserved as "transparent" per format)
        Map<Integer, Integer> colorToIndex = new LinkedHashMap<>();
        byte[] pixelIdx = new byte[dimension];
        byte[] alphas = new byte[dimension];
        boolean hasPartialAlpha = false;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = img.getRGB(x, y);
                int a = (argb >>> 24) & 0xFF;
                int rgb = argb & 0xFFFFFF;
                int pos = y * width + x;
                alphas[pos] = (byte) a;
                if (a != 0 && a != 255) hasPartialAlpha = true;

                if (a == 0) {
                    pixelIdx[pos] = 0;
                    continue;
                }
                if (rgb == 0) rgb = 1; // loader remaps a literal-0 palette entry to 1, avoid collision
                Integer idx = colorToIndex.get(rgb);
                if (idx == null) {
                    idx = colorToIndex.size() + 1; // 1-based, 0 is transparent
                    if (idx > 255) {
                        throw new IllegalStateException("sprite has more than 255 unique opaque colors ("
                                + "unsupported by this indexed format)");
                    }
                    colorToIndex.put(rgb, idx);
                }
                pixelIdx[pos] = (byte) (int) idx;
            }
        }

        boolean useAlphaFlag = hasPartialAlpha;
        int flags = useAlphaFlag ? SpriteLoader.FLAG_ALPHA : 0; // horizontal (no FLAG_VERTICAL)

        OutputStream os = new OutputStream(dimension * 2 + colorToIndex.size() * 3 + 32);
        os.writeByte(flags);
        os.writeBytes(pixelIdx);
        if (useAlphaFlag) {
            os.writeBytes(alphas);
        }

        int paletteLength = colorToIndex.size() + 1; // including transparent slot 0
        int[] paletteRgb = new int[paletteLength];
        for (Map.Entry<Integer, Integer> e : colorToIndex.entrySet()) {
            paletteRgb[e.getValue()] = e.getKey();
        }
        for (int i = 1; i < paletteLength; i++) {
            os.write24BitInt(paletteRgb[i]);
        }

        os.writeShort(width);   // max width
        os.writeShort(height);  // max height
        os.writeByte(paletteLength - 1);

        os.writeShort(0); // offsetX (single sprite, no crop)
        os.writeShort(0); // offsetY
        os.writeShort(width);  // width
        os.writeShort(height); // height

        os.writeShort(1); // spriteCount

        return os.flip();
    }

    // ============================== item encoding ==============================

    static byte[] encodeItem(int id, Map<String, Object> f) {
        OutputStream os = new OutputStream(256);

        writeStr(os, 2, str(f, "name"));
        writeUShortOpt(os, 1, f, "inventory_model");
        writeUShortOpt(os, 4, f, "zoom2d");
        writeUShortOpt(os, 5, f, "xan2d");
        writeUShortOpt(os, 6, f, "yan2d");
        writeSShortOpt(os, 7, f, "x_offset2d");
        writeSShortOpt(os, 8, f, "y_offset2d");
        if (bool(f, "stackable")) {
            os.writeByte(11);
        }
        writeIntOpt(os, 12, f, "cost");
        writeByteOpt(os, 13, f, "wear_pos_0");
        writeByteOpt(os, 14, f, "wear_pos_1");
        if (bool(f, "members")) {
            os.writeByte(16);
        }
        Integer maleModel0 = intOrNull(f, "male_model_0");
        if (maleModel0 != null) {
            os.writeByte(23);
            os.writeShort(maleModel0);
            os.writeByte(intOr(f, "male_offset", 0));
        }
        writeUShortOpt(os, 24, f, "male_model_1");
        Integer femaleModel0 = intOrNull(f, "female_model_0");
        if (femaleModel0 != null) {
            os.writeByte(25);
            os.writeShort(femaleModel0);
            os.writeByte(intOr(f, "female_offset", 0));
        }
        writeUShortOpt(os, 26, f, "female_model_1");
        writeByteOpt(os, 27, f, "wear_pos_2");

        @SuppressWarnings("unchecked")
        List<String> options = (List<String>) f.get("options");
        if (options != null) {
            for (int i = 0; i < options.size() && i < 5; i++) {
                String v = options.get(i);
                if (v != null && !v.isEmpty()) {
                    writeStr(os, 30 + i, v);
                }
            }
        }
        @SuppressWarnings("unchecked")
        List<String> interfaceOptions = (List<String>) f.get("interface_options");
        if (interfaceOptions != null) {
            for (int i = 0; i < interfaceOptions.size() && i < 5; i++) {
                String v = interfaceOptions.get(i);
                if (v != null && !v.isEmpty()) {
                    writeStr(os, 35 + i, v);
                }
            }
        }

        @SuppressWarnings("unchecked")
        List<int[]> replaceColor = (List<int[]>) f.get("replace_color");
        if (replaceColor != null && !replaceColor.isEmpty()) {
            os.writeByte(40);
            os.writeByte(replaceColor.size());
            for (int[] pair : replaceColor) {
                os.writeShort(pair[0]);
                os.writeShort(pair[1]);
            }
        }

        writeUShortOpt(os, 90, f, "male_head_model_0");
        writeUShortOpt(os, 91, f, "female_head_model_0");
        writeUShortOpt(os, 95, f, "zan2d");
        writeByteOpt(os, 113, f, "ambient");
        // this project's ObjType.decode() applies "contrast = readByte() * 5" on load (confirmed
        // in kronos-server's ObjType.java, opcode 114 -- a real project-specific convention, not
        // present in RuneLite's generic loader) -- the toml's contrast values are round multiples
        // of 5 (75, 20, ...), i.e. the intended POST-multiply value, so divide before writing.
        writeByteOpt(os, 114, f, "contrast", 5);
        if (bool(f, "tradeable")) {
            os.writeByte(65);
        }

        os.writeByte(0); // terminator
        return os.flip();
    }

    static String describeItem(int id, byte[] bytes) {
        ItemDefinition def = new ItemLoader().load(id, bytes);
        return "name=\"" + def.name + "\" inventoryModel=" + def.inventoryModel + " wearPos1=" + def.wearPos1;
    }

    // ============================== npc encoding ==============================

    static byte[] encodeNpc(int id, Map<String, Object> f) {
        OutputStream os = new OutputStream(256);

        @SuppressWarnings("unchecked")
        List<Integer> models = (List<Integer>) f.get("models");
        if (models != null && !models.isEmpty()) {
            os.writeByte(1);
            os.writeByte(models.size());
            for (int m : models) {
                os.writeShort(m);
            }
        }

        writeStr(os, 2, str(f, "name"));
        writeByteOpt(os, 12, f, "size");

        Integer seqIdle = intOrNull(f, "seq_idle");
        if (seqIdle != null) {
            os.writeByte(13);
            os.writeShort(seqIdle);
        }

        Integer seqWalk = intOrNull(f, "seq_walk");
        if (seqWalk != null) {
            // opcode 17 sets walk + rotate180 + rotateLeft + rotateRight together; this content's
            // seq_info block always has all four equal to seq_walk (confirmed for every npc in this
            // batch), so a single opcode-17 covers it without needing separate 15/16 opcodes.
            os.writeByte(17);
            os.writeShort(seqWalk);
            os.writeShort(seqWalk);
            os.writeShort(seqWalk);
            os.writeShort(seqWalk);
        }

        @SuppressWarnings("unchecked")
        List<String> actions = (List<String>) f.get("actions");
        if (actions != null) {
            for (int i = 0; i < actions.size() && i < 5; i++) {
                String v = actions.get(i);
                if (v != null && !v.isEmpty()) {
                    writeStr(os, 30 + i, v);
                }
            }
        }

        @SuppressWarnings("unchecked")
        List<Integer> chatHeads = (List<Integer>) f.get("chat_heads");
        if (chatHeads != null && !chatHeads.isEmpty()) {
            os.writeByte(60);
            os.writeByte(chatHeads.size());
            for (int m : chatHeads) {
                os.writeShort(m);
            }
        }

        if (f.containsKey("draw_map_dot") && !bool(f, "draw_map_dot")) {
            os.writeByte(93);
        }

        writeUShortOpt(os, 95, f, "combat_level");
        writeUShortOpt(os, 97, f, "scale_width");
        writeUShortOpt(os, 98, f, "scale_height");
        writeByteOpt(os, 100, f, "ambient");
        // see the item encoder's identical note: NPCType.decode() also does "contrast = readByte()
        // * 5" (opcode 101), so divide the toml's post-multiply value before writing the raw byte.
        writeByteOpt(os, 101, f, "contrast", 5);

        if (bool(f, "follower")) {
            os.writeByte(122);
        }
        if (bool(f, "follower_low_priority_ops")) {
            os.writeByte(123);
        }

        os.writeByte(0); // terminator
        return os.flip();
    }

    static String describeNpc(int id, byte[] bytes) {
        NpcDefinition def = new NpcLoader().load(id, bytes);
        return "name=\"" + def.name + "\" models=" + java.util.Arrays.toString(def.models)
                + " isFollower=" + def.isFollower;
    }

    // ============================== sequence encoding ==============================

    static byte[] encodeSeq(int id, Map<String, Object> f) {
        @SuppressWarnings("unchecked")
        List<int[]> frameIds = (List<int[]>) f.get("frame_ids");
        if (frameIds == null || frameIds.isEmpty()) {
            throw new IllegalStateException("seq " + id + ": no frame_ids");
        }

        OutputStream os = new OutputStream(frameIds.size() * 6 + 8);
        os.writeByte(1);
        os.writeShort(frameIds.size());
        for (int[] t : frameIds) {
            os.writeShort(t[2]); // frame length
        }
        for (int[] t : frameIds) {
            os.writeShort(t[1] & 0xFFFF); // frameId low 16 bits (file id within archive)
        }
        for (int[] t : frameIds) {
            os.writeShort((t[0] >>> 16) & 0xFFFF); // frameId high 16 bits (frame archive id)
        }
        os.writeByte(0); // terminator
        return os.flip();
    }

    static String describeSeq(int id, byte[] bytes) {
        SequenceDefinition def = new SequenceLoader().load(id, bytes);
        return "frames=" + (def.frameIDs == null ? 0 : def.frameIDs.length);
    }

    // ============================== tiny TOML readers (format-specific, not general-purpose) ==============================

    static Map<String, Object> parseItemToml(String text) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (String line : text.split("\n")) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int eq = line.indexOf('=');
            if (eq < 0) continue;
            String key = line.substring(0, eq).trim();
            if (key.equals("replace_color")) continue; // handled below, single- or multi-line
            String rest = line.substring(eq + 1).trim();
            if (rest.startsWith("[") && !rest.endsWith("]")) {
                // multi-line array of scalars (not used by any current key, but stay defensive)
                continue;
            }
            out.put(key, parseScalarOrArray(rest));
        }
        // replace_color is `[[a, b], [c, d], ...]`, either on one line or spread across several --
        // depth-aware bracket match so both layouts work the same way.
        int keyIdx = text.indexOf("replace_color");
        if (keyIdx >= 0) {
            int bracketStart = text.indexOf('[', keyIdx);
            String inner = extractBracketed(text, bracketStart);
            List<int[]> pairs = new ArrayList<>();
            java.util.regex.Matcher pm = java.util.regex.Pattern.compile("\\[\\s*(\\d+)\\s*,\\s*(\\d+)\\s*]")
                    .matcher(inner);
            while (pm.find()) {
                pairs.add(new int[]{Integer.parseInt(pm.group(1)), Integer.parseInt(pm.group(2))});
            }
            out.put("replace_color", pairs);
        }
        return out;
    }

    // Given the index of an opening '[', returns the content between it and its matching ']'
    // (bracket-depth aware, so nested arrays don't confuse it).
    static String extractBracketed(String text, int openIdx) {
        int depth = 0;
        for (int i = openIdx; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '[') depth++;
            else if (c == ']') {
                depth--;
                if (depth == 0) {
                    return text.substring(openIdx + 1, i);
                }
            }
        }
        throw new IllegalStateException("unbalanced brackets starting at " + openIdx);
    }

    static Map<String, Object> parseNpcToml(String text) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (String line : text.split("\n")) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("seq_info")) continue;
            int eq = line.indexOf('=');
            if (eq < 0) continue;
            String key = line.substring(0, eq).trim();
            String rest = line.substring(eq + 1).trim();
            out.put(key, parseScalarOrArray(rest));
        }
        return out;
    }

    static Map<String, Object> parseSeqToml(String text) {
        Map<String, Object> out = new LinkedHashMap<>();
        java.util.regex.Matcher idm = java.util.regex.Pattern.compile("id\\s*=\\s*(\\d+)").matcher(text);
        if (idm.find()) out.put("id", Integer.parseInt(idm.group(1)));

        List<int[]> triples = new ArrayList<>();
        java.util.regex.Matcher tm = java.util.regex.Pattern
                .compile("\\[\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)\\s*]").matcher(text);
        while (tm.find()) {
            triples.add(new int[]{Integer.parseInt(tm.group(1)), Integer.parseInt(tm.group(2)),
                    Integer.parseInt(tm.group(3))});
        }
        out.put("frame_ids", triples);
        return out;
    }

    static Object parseScalarOrArray(String rest) {
        if (rest.startsWith("\"")) {
            return rest.substring(1, rest.lastIndexOf('"'));
        }
        if (rest.equals("true")) return Boolean.TRUE;
        if (rest.equals("false")) return Boolean.FALSE;
        if (rest.startsWith("[") && rest.endsWith("]")) {
            String inner = rest.substring(1, rest.length() - 1).trim();
            List<Object> list = new ArrayList<>();
            if (!inner.isEmpty()) {
                for (String part : splitTopLevel(inner)) {
                    part = part.trim();
                    if (part.isEmpty()) continue;
                    if (part.startsWith("\"")) {
                        list.add(part.substring(1, part.lastIndexOf('"')));
                    } else {
                        list.add(Integer.parseInt(part));
                    }
                }
            }
            return list;
        }
        try {
            return Integer.parseInt(rest);
        } catch (NumberFormatException e) {
            return rest;
        }
    }

    static List<String> splitTopLevel(String s) {
        List<String> parts = new ArrayList<>();
        int depth = 0, start = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '[') depth++;
            else if (c == ']') depth--;
            else if (c == ',' && depth == 0) {
                parts.add(s.substring(start, i));
                start = i + 1;
            }
        }
        parts.add(s.substring(start));
        return parts;
    }

    // ============================== small helpers ==============================

    static String str(Map<String, Object> f, String key) {
        Object v = f.get(key);
        return v == null ? "" : v.toString();
    }

    static boolean bool(Map<String, Object> f, String key) {
        Object v = f.get(key);
        return Boolean.TRUE.equals(v);
    }

    static Integer intOrNull(Map<String, Object> f, String key) {
        Object v = f.get(key);
        return v instanceof Number ? ((Number) v).intValue() : null;
    }

    static int intOr(Map<String, Object> f, String key, int def) {
        Integer v = intOrNull(f, key);
        return v == null ? def : v;
    }

    static void writeStr(OutputStream os, int opcode, String value) {
        if (value == null || value.isEmpty()) return;
        os.writeByte(opcode);
        os.writeString(value);
    }

    static void writeUShortOpt(OutputStream os, int opcode, Map<String, Object> f, String key) {
        Integer v = intOrNull(f, key);
        if (v == null) return;
        os.writeByte(opcode);
        os.writeShort(v);
    }

    static void writeSShortOpt(OutputStream os, int opcode, Map<String, Object> f, String key) {
        Integer v = intOrNull(f, key);
        if (v == null) return;
        os.writeByte(opcode);
        os.writeShort(v & 0xFFFF);
    }

    static void writeByteOpt(OutputStream os, int opcode, Map<String, Object> f, String key) {
        writeByteOpt(os, opcode, f, key, 1);
    }

    static void writeByteOpt(OutputStream os, int opcode, Map<String, Object> f, String key, int divisor) {
        Integer v = intOrNull(f, key);
        if (v == null) return;
        if (v % divisor != 0) {
            throw new IllegalStateException(key + "=" + v + " is not a clean multiple of " + divisor
                    + " -- refusing to guess, this needs a human decision.");
        }
        os.writeByte(opcode);
        os.writeByte(v / divisor);
    }

    static void writeIntOpt(OutputStream os, int opcode, Map<String, Object> f, String key) {
        Integer v = intOrNull(f, key);
        if (v == null) return;
        os.writeByte(opcode);
        os.writeInt(v);
    }

    // ============================== textures ==============================

    // Textures live as a single MULTI-FILE archive (archive id 0) inside the TEXTURES index --
    // confirmed against TextureManager.load()'s real read path (index.getArchive(0), then one
    // "file" per texture id within it), the same shape as items/npcs/sequences. Reuses the same
    // sorted-merge-insert discipline as addConfigEntries (existing ids must stay in ascending
    // positional order for the reference table's delta encoding to round-trip correctly).
    static void addTextures(Store store, Storage storage, File tomlFile, boolean apply) throws Exception {
        Index index = store.getIndex(IndexType.TEXTURES);
        Archive archive = index.getArchive(0);

        byte[] compressed = storage.loadArchive(archive);
        byte[] decompressed = archive.decompress(compressed);
        FileData[] fileData = archive.getFileData();
        System.out.println("Texture archive: " + fileData.length + " existing entries.");

        List<byte[]> fileContents = SpliceItemOption.splitChunks(decompressed, fileData.length);

        String text = new String(Files.readAllBytes(tomlFile.toPath()), StandardCharsets.UTF_8);
        Map<Integer, Map<String, Object>> textures = parseTexturesToml(text);

        List<Integer> newIds = new ArrayList<>();
        List<byte[]> newRaws = new ArrayList<>();

        for (Map.Entry<Integer, Map<String, Object>> e : textures.entrySet()) {
            int id = e.getKey();
            for (FileData fd : fileData) {
                if (fd.getId() == id) {
                    throw new IllegalStateException("texture " + id + " already exists -- ABORTING, refusing to overwrite.");
                }
            }
            byte[] raw = encodeTexture(e.getValue());

            var loader = new net.runelite.cache.definitions.loaders.TextureLoader();
            var def = loader.load(id, raw);
            System.out.println("texture " + id + " -> sprites=" + java.util.Arrays.toString(def.getFileIds())
                    + " animDir=" + def.animationDirection + " animSpeed=" + def.animationSpeed
                    + " (" + raw.length + " bytes)");

            newIds.add(id);
            newRaws.add(raw);
        }

        System.out.println("Prepared " + newIds.size() + " new textures.");
        if (!apply) {
            System.out.println("DRY RUN -- not writing. Re-run with 'apply' to persist.");
            return;
        }

        List<Integer> sortOrder = new ArrayList<>();
        for (int i = 0; i < newIds.size(); i++) sortOrder.add(i);
        sortOrder.sort((a, b) -> Integer.compare(newIds.get(a), newIds.get(b)));

        List<FileData> mergedFileData = new ArrayList<>(fileData.length + newIds.size());
        List<byte[]> mergedContents = new ArrayList<>(fileData.length + newIds.size());
        int ni = 0;
        for (int i = 0; i < fileData.length; i++) {
            while (ni < sortOrder.size() && newIds.get(sortOrder.get(ni)) < fileData[i].getId()) {
                int idx = sortOrder.get(ni);
                FileData nfd = new FileData();
                nfd.setId(newIds.get(idx));
                nfd.setNameHash(-1);
                mergedFileData.add(nfd);
                mergedContents.add(newRaws.get(idx));
                ni++;
            }
            mergedFileData.add(fileData[i]);
            mergedContents.add(fileContents.get(i));
        }
        while (ni < sortOrder.size()) {
            int idx = sortOrder.get(ni);
            FileData nfd = new FileData();
            nfd.setId(newIds.get(idx));
            nfd.setNameHash(-1);
            mergedFileData.add(nfd);
            mergedContents.add(newRaws.get(idx));
            ni++;
        }

        archive.setFileData(mergedFileData.toArray(new FileData[0]));

        byte[] newDecompressed = SpliceItemOption.joinChunks(mergedContents);
        Container container = new Container(archive.getCompression(), -1);
        container.compress(newDecompressed, null);

        storage.store(index.getId(), archive.getArchiveId(), container.data);
        archive.setCrc(container.crc);
        archive.setRevision(archive.getRevision() + 1);
        archive.setCompressedSize(container.data.length);
        archive.setDecompressedSize(newDecompressed.length);

        writeIndexReferenceTable(storage, index);
        System.out.println("APPLY complete. Texture archive revision now " + archive.getRevision()
                + ", " + newIds.size() + " new textures added.");
    }

    @SuppressWarnings("unchecked")
    static byte[] encodeTexture(Map<String, Object> f) {
        OutputStream os = new OutputStream(32);
        os.writeShort(intOr(f, "idk_0", 0));  // field1777
        os.writeByte(intOr(f, "idk_1", 0));   // field1778 (boolean, readByte() != 0)

        List<Integer> sprites = (List<Integer>) f.get("sprites");
        os.writeByte(sprites.size());
        for (int s : sprites) {
            os.writeShort(s);
        }
        if (sprites.size() > 1) {
            // field1780/field1781 (only read when count > 1) -- every texture in this batch has
            // exactly one sprite, so refuse to guess at a shape that's never actually exercised.
            throw new IllegalStateException("multi-sprite texture not supported by this encoder "
                    + "(field1780/field1781 unimplemented) -- needs a human decision.");
        }

        List<Integer> idk4 = (List<Integer>) f.get("idk_4");
        for (int v : idk4) {
            os.writeInt(v); // field1786, one int per sprite/file
        }

        os.writeByte(intOr(f, "animation_direction", 0));
        os.writeByte(intOr(f, "animation_speed", 0));
        return os.flip();
    }

    static Map<Integer, Map<String, Object>> parseTexturesToml(String text) {
        Map<Integer, Map<String, Object>> out = new java.util.TreeMap<>();
        for (String rawLine : text.split("\n")) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int eq = line.indexOf('=');
            if (eq < 0) continue;
            int id;
            try {
                id = Integer.parseInt(line.substring(0, eq).trim());
            } catch (NumberFormatException e) {
                continue;
            }
            int braceStart = line.indexOf('{', eq);
            String inner = extractBracedInline(line, braceStart);
            Map<String, Object> fields = new LinkedHashMap<>();
            for (String part : splitTopLevel(inner)) {
                part = part.trim();
                if (part.isEmpty()) continue;
                int peq = part.indexOf('=');
                String key = part.substring(0, peq).trim();
                String rest = part.substring(peq + 1).trim();
                fields.put(key, parseScalarOrArray(rest));
            }
            out.put(id, fields);
        }
        return out;
    }

    // Like extractBracketed but for a { ... } inline table -- depth-aware over braces.
    static String extractBracedInline(String text, int openIdx) {
        int depth = 0;
        for (int i = openIdx; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return text.substring(openIdx + 1, i);
                }
            }
        }
        throw new IllegalStateException("unbalanced braces starting at " + openIdx);
    }

    // ============================== texture id remap (129-256 GPU limit fix) ==============================

    // net.runelite.client.plugins.gpu.TextureManager sizes its GPU texture array as
    // (max texture id in the cache + 1) and hard-caps at 256 -- confirmed live: "texture limit
    // exceeded: 2218 > 256" once the 25 new textures landed at Kaede's own ids (2017-2217), which
    // broke texture rendering for the WHOLE GAME (ground/terrain render solid black), not just the
    // new items. This project's own existing textures only span 0-128. Remaps the 25 new textures
    // down into the free 129-255 range and patches every model that references the old ids.
    static final int[] OLD_TEXTURE_IDS = {
            2017, 2042, 2053, 2054, 2055, 2056, 2057, 2059, 2060, 2061, 2062, 2066, 2071, 2072,
            2076, 2081, 2085, 2101, 2105, 2112, 2113, 2180, 2194, 2199, 2217
    };
    static final int NEW_TEXTURE_BASE = 129; // -> 129..153, all confirmed free and under the 256 cap

    static void fixTextureIds(Store store, Storage storage, File textureToml, File modelDir, boolean apply)
            throws Exception {
        Map<Integer, Integer> remap = new LinkedHashMap<>();
        for (int i = 0; i < OLD_TEXTURE_IDS.length; i++) {
            remap.put(OLD_TEXTURE_IDS[i], NEW_TEXTURE_BASE + i);
        }
        Set<Integer> oldIdSet = remap.keySet();

        // ---- 1. remove the old (2017-2217) texture entries, add new ones at 129-153 ----
        Index texIndex = store.getIndex(IndexType.TEXTURES);
        Archive texArchive = texIndex.getArchive(0);
        byte[] texDecompressed = texArchive.decompress(storage.loadArchive(texArchive));
        FileData[] texFileData = texArchive.getFileData();
        List<byte[]> texContents = SpliceItemOption.splitChunks(texDecompressed, texFileData.length);

        List<FileData> keptFileData = new ArrayList<>();
        List<byte[]> keptContents = new ArrayList<>();
        int removed = 0;
        for (int i = 0; i < texFileData.length; i++) {
            if (oldIdSet.contains(texFileData[i].getId())) {
                removed++;
                continue;
            }
            keptFileData.add(texFileData[i]);
            keptContents.add(texContents.get(i));
        }
        System.out.println("Removing " + removed + " old-id texture entries, " + keptFileData.size()
                + " remain (should be " + (texFileData.length - OLD_TEXTURE_IDS.length) + ").");
        for (int newId : remap.values()) {
            for (FileData fd : keptFileData) {
                if (fd.getId() == newId) {
                    throw new IllegalStateException("new texture id " + newId + " already exists -- ABORTING.");
                }
            }
        }

        String text = new String(Files.readAllBytes(textureToml.toPath()), StandardCharsets.UTF_8);
        Map<Integer, Map<String, Object>> textureFields = parseTexturesToml(text);

        List<Integer> newIds = new ArrayList<>();
        List<byte[]> newRaws = new ArrayList<>();
        for (int oldId : OLD_TEXTURE_IDS) {
            int newId = remap.get(oldId);
            byte[] raw = encodeTexture(textureFields.get(oldId));
            var loader = new net.runelite.cache.definitions.loaders.TextureLoader();
            var def = loader.load(newId, raw);
            System.out.println("texture " + oldId + " -> " + newId + ": sprites="
                    + java.util.Arrays.toString(def.getFileIds()));
            newIds.add(newId);
            newRaws.add(raw);
        }

        // merge-insert (newIds 129..153 are already ascending, matching OLD_TEXTURE_IDS' own order)
        List<FileData> mergedFileData = new ArrayList<>(keptFileData.size() + newIds.size());
        List<byte[]> mergedContents = new ArrayList<>(keptFileData.size() + newIds.size());
        int ni = 0;
        for (int i = 0; i < keptFileData.size(); i++) {
            while (ni < newIds.size() && newIds.get(ni) < keptFileData.get(i).getId()) {
                FileData nfd = new FileData();
                nfd.setId(newIds.get(ni));
                nfd.setNameHash(-1);
                mergedFileData.add(nfd);
                mergedContents.add(newRaws.get(ni));
                ni++;
            }
            mergedFileData.add(keptFileData.get(i));
            mergedContents.add(keptContents.get(i));
        }
        while (ni < newIds.size()) {
            FileData nfd = new FileData();
            nfd.setId(newIds.get(ni));
            nfd.setNameHash(-1);
            mergedFileData.add(nfd);
            mergedContents.add(newRaws.get(ni));
            ni++;
        }

        // ---- 2. patch every model referencing an old id ----
        File[] modelFiles = modelDir.listFiles((d, n) -> n.endsWith(".bin"));
        java.util.Arrays.sort(modelFiles);
        Index modelIndex = store.getIndex(IndexType.MODELS);
        List<Integer> patchedModelIds = new ArrayList<>();
        List<byte[]> patchedModelBytes = new ArrayList<>();
        var modelLoader = new net.runelite.cache.definitions.loaders.ModelLoader();

        for (File mf : modelFiles) {
            int modelId = Integer.parseInt(mf.getName().replace(".bin", ""));
            Archive modelArchive = modelIndex.getArchive(modelId);
            if (modelArchive == null) {
                throw new IllegalStateException("model " + modelId + " not found -- ABORTING.");
            }
            byte[] raw = modelArchive.decompress(storage.loadArchive(modelArchive));

            var before = modelLoader.load(modelId, raw.clone());
            byte[] patched = patchModelTextureIds(raw, remap);
            if (patched == null) {
                continue; // no faceTextures block, or none of our ids referenced
            }
            var after = modelLoader.load(modelId, patched.clone());

            if (!modelFieldsMatchExceptTextures(before, after)) {
                throw new IllegalStateException("model " + modelId
                        + ": patch changed something other than faceTextures -- ABORTING, no write performed.");
            }
            for (int i = 0; i < after.faceTextures.length; i++) {
                int oldVal = before.faceTextures[i];
                int newVal = after.faceTextures[i];
                if (oldIdSet.contains((int) oldVal) && newVal != remap.get((int) oldVal)) {
                    throw new IllegalStateException("model " + modelId + " face " + i
                            + ": texture id not remapped correctly -- ABORTING.");
                }
                if (!oldIdSet.contains((int) oldVal) && oldVal != newVal) {
                    throw new IllegalStateException("model " + modelId + " face " + i
                            + ": an untouched texture id changed -- ABORTING.");
                }
            }

            System.out.println("model " + modelId + ": patched (verified byte-identical except remapped "
                    + "texture ids).");
            patchedModelIds.add(modelId);
            patchedModelBytes.add(patched);
        }

        System.out.println("Prepared: " + newIds.size() + " remapped textures, " + patchedModelIds.size()
                + " models to patch.");
        if (!apply) {
            System.out.println("DRY RUN -- not writing. Re-run with 'apply' to persist.");
            return;
        }

        // write textures
        texArchive.setFileData(mergedFileData.toArray(new FileData[0]));
        byte[] newTexDecompressed = SpliceItemOption.joinChunks(mergedContents);
        Container texContainer = new Container(texArchive.getCompression(), -1);
        texContainer.compress(newTexDecompressed, null);
        storage.store(texIndex.getId(), texArchive.getArchiveId(), texContainer.data);
        texArchive.setCrc(texContainer.crc);
        texArchive.setRevision(texArchive.getRevision() + 1);
        texArchive.setCompressedSize(texContainer.data.length);
        texArchive.setDecompressedSize(newTexDecompressed.length);
        writeIndexReferenceTable(storage, texIndex);
        System.out.println("Texture archive rewritten: revision now " + texArchive.getRevision()
                + ", max id now " + (NEW_TEXTURE_BASE + OLD_TEXTURE_IDS.length - 1) + ".");

        // write patched models
        for (int i = 0; i < patchedModelIds.size(); i++) {
            int modelId = patchedModelIds.get(i);
            byte[] patched = patchedModelBytes.get(i);
            Archive modelArchive = modelIndex.getArchive(modelId);
            Container modelContainer = new Container(modelArchive.getCompression(), -1);
            modelContainer.compress(patched, null);
            storage.store(modelIndex.getId(), modelId, modelContainer.data);
            modelArchive.setCrc(modelContainer.crc);
            modelArchive.setRevision(modelArchive.getRevision() + 1);
            modelArchive.setCompressedSize(modelContainer.data.length);
            modelArchive.setDecompressedSize(patched.length);
        }
        writeIndexReferenceTable(storage, modelIndex);
        System.out.println("APPLY complete. " + patchedModelIds.size() + " models patched, texture ids "
                + "remapped to " + NEW_TEXTURE_BASE + "-" + (NEW_TEXTURE_BASE + OLD_TEXTURE_IDS.length - 1) + ".");
    }

    static boolean modelFieldsMatchExceptTextures(net.runelite.cache.definitions.ModelDefinition a,
                                                    net.runelite.cache.definitions.ModelDefinition b) {
        if (a.vertexCount != b.vertexCount) return false;
        if (a.faceCount != b.faceCount) return false;
        if (!java.util.Arrays.equals(a.vertexX, b.vertexX)) return false;
        if (!java.util.Arrays.equals(a.vertexY, b.vertexY)) return false;
        if (!java.util.Arrays.equals(a.vertexZ, b.vertexZ)) return false;
        if (!java.util.Arrays.equals(a.faceIndices1, b.faceIndices1)) return false;
        if (!java.util.Arrays.equals(a.faceIndices2, b.faceIndices2)) return false;
        if (!java.util.Arrays.equals(a.faceIndices3, b.faceIndices3)) return false;
        if (!java.util.Arrays.equals(a.faceColors, b.faceColors)) return false;
        if (!java.util.Arrays.equals(a.faceRenderPriorities, b.faceRenderPriorities)) return false;
        if (!java.util.Arrays.equals(a.faceTransparencies, b.faceTransparencies)) return false;
        if ((a.faceTextures == null) != (b.faceTextures == null)) return false;
        if (a.faceTextures != null && a.faceTextures.length != b.faceTextures.length) return false;
        return true;
    }

    // Mirrors ModelLoader.decodeType1()'s exact offset arithmetic (verified against its real
    // source -- confirmed live via CheckModelFormats that all 86 models in this batch are Type1,
    // not Type3 as first assumed) just far enough to locate the faceTextures block: a plain
    // contiguous array of per-face unsigned shorts (value = textureId + 1, 0 = no texture)
    // starting at decodeType1's "var34" (the cursor it assigns to var7, which reads faceTextures)
    // and spanning faceCount*2 bytes. Returns null if this model isn't Type1, has no faceTextures
    // block at all (hasFaceTextures != 1), or references none of the remapped ids.
    static byte[] patchModelTextureIds(byte[] raw, Map<Integer, Integer> remap) {
        if (raw.length < 23 || !(raw[raw.length - 1] == -1 && raw[raw.length - 2] == -1)) {
            return null; // not Type1 -- none of this batch's models should hit this, but don't guess
        }
        var header = new net.runelite.cache.io.InputStream(raw);
        header.setOffset(raw.length - 23);
        int vertexCount = header.readUnsignedShort();
        int faceCount = header.readUnsignedShort();
        int numTextureFaces = header.readUnsignedByte();
        int hasFaceRenderTypes = header.readUnsignedByte();
        int priorityMarker = header.readUnsignedByte();
        int hasFaceTransparencies = header.readUnsignedByte();
        int hasPackedTransparencyVertexGroups = header.readUnsignedByte();
        int hasFaceTextures = header.readUnsignedByte();
        int hasPackedVertexGroups = header.readUnsignedByte();
        header.readUnsignedShort(); // var18 -- unused for this offset chain
        header.readUnsignedShort(); // var19 -- unused for this offset chain
        header.readUnsignedShort(); // var20 -- unused for this offset chain
        int var21 = header.readUnsignedShort();

        if (hasFaceTextures != 1) {
            return null;
        }

        // Mirrors decodeType1's exact chain up to "var34" (the faceTextures cursor, var7):
        // offset = var11+var9; [+=var10 if var12==1]; +=var10 (unconditional, faceColors);
        // [+=var10 if var13==255]; [+=var10 if var15==1]; [+=var9 if var17==1];
        // [+=var10 if var14==1]; +=var21 --> var34.
        int offset = numTextureFaces + vertexCount;
        if (hasFaceRenderTypes == 1) offset += faceCount;
        offset += faceCount; // faceColors, unconditional
        if (priorityMarker == 255) offset += faceCount;
        if (hasPackedTransparencyVertexGroups == 1) offset += faceCount;
        if (hasPackedVertexGroups == 1) offset += vertexCount;
        if (hasFaceTransparencies == 1) offset += faceCount;
        offset += var21;
        int var34 = offset; // faceTextures block start

        boolean anyChange = false;
        byte[] out = raw.clone();
        for (int i = 0; i < faceCount; i++) {
            int pos = var34 + i * 2;
            int value = ((out[pos] & 0xFF) << 8) | (out[pos + 1] & 0xFF);
            if (value == 0) continue; // -1 sentinel (no texture)
            int textureId = value - 1;
            Integer newId = remap.get(textureId);
            if (newId == null) continue;
            int newValue = newId + 1;
            out[pos] = (byte) ((newValue >> 8) & 0xFF);
            out[pos + 1] = (byte) (newValue & 0xFF);
            anyChange = true;
        }
        return anyChange ? out : null;
    }

    // ============================== bank interface component-120 fix ==============================

    // The bank interface (id 12, component 120) that GearPresetInterface.open() is wired to from
    // BankInterface.java was left as an empty, unconfigured placeholder -- clickMask=0, actions=
    // null, no sprite/text, so it's both invisible and unclickable; the server-side handler never
    // fires because the client never sends a click for it. Confirmed against two other real,
    // working bank buttons in this same interface (44 "Deposit inventory", 19 "Swap") that the
    // working pattern is just clickMask=2 + a populated `actions` array + actual visible content
    // -- no onOpListener needed (component 19 has none and works). Replaces ONLY component 120's
    // bytes in interface 12's archive; every other component's raw bytes are verified unchanged.
    static void fixBankPresetButton(Store store, Storage storage, boolean apply) throws Exception {
        int interfaceId = 12; // Interface.BANK
        int componentIndex = 120;

        Index index = store.getIndex(IndexType.INTERFACES);
        Archive archive = index.getArchive(interfaceId);
        byte[] compressed = storage.loadArchive(archive);
        byte[] decompressed = archive.decompress(compressed);
        FileData[] fileData = archive.getFileData();

        List<byte[]> fileContents = SpliceItemOption.splitChunks(decompressed, fileData.length);

        int slot = -1;
        for (int i = 0; i < fileData.length; i++) {
            if (fileData[i].getId() == componentIndex) {
                slot = i;
                break;
            }
        }
        if (slot == -1) {
            throw new IllegalStateException("component " + componentIndex + " not found in interface "
                    + interfaceId);
        }

        byte[] before = fileContents.get(slot);
        var loader = new net.runelite.cache.definitions.loaders.InterfaceLoader();
        int widgetId = (interfaceId << 16) + componentIndex;
        var beforeDef = loader.load(widgetId, before);
        System.out.println("BEFORE: type=" + beforeDef.type + " clickMask=" + beforeDef.clickMask
                + " actions=" + java.util.Arrays.toString(beforeDef.actions) + " spriteId=" + beforeDef.spriteId
                + " text=\"" + beforeDef.text + "\"");

        // Keep the existing position/size/parent (49,128, 70x26, parent=component 79) -- only turn
        // it from an invisible Layer into a visible, clickable Text button.
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("_type", "Text");
        fields.put("x", (int) beforeDef.originalX);
        fields.put("y", (int) beforeDef.originalY);
        fields.put("width", beforeDef.originalWidth);
        fields.put("height", beforeDef.originalHeight);
        fields.put("content", beforeDef.contentType);
        fields.put("parent", beforeDef.parentId == -1 ? -1 : beforeDef.parentId - (interfaceId << 16));
        fields.put("font", 496);
        fields.put("text", "Presets");
        fields.put("line_height", 0);
        fields.put("alignment_x", "Center");
        fields.put("alignment_y", "Center");
        fields.put("shadowed", true);
        fields.put("color", 16750623); // matches the existing bank UI's gold text color (component 118/119)
        fields.put("click_mask", 2);
        fields.put("actions", List.<Object>of("Open"));

        byte[] after = encodeInterfaceComponent(fields);
        var afterDef = loader.load(widgetId, after);
        if (afterDef.type != 4 || afterDef.clickMask != 2 || afterDef.actions == null
                || !"Open".equals(afterDef.actions[0]) || !"Presets".equals(afterDef.text)) {
            throw new IllegalStateException("round-trip verification of the new component 120 FAILED -- ABORTING.");
        }
        System.out.println("AFTER:  type=" + afterDef.type + " clickMask=" + afterDef.clickMask
                + " actions=" + java.util.Arrays.toString(afterDef.actions) + " text=\"" + afterDef.text + "\"");

        fileContents.set(slot, after);

        // Safety: every OTHER slot's raw bytes must be byte-identical to before this edit.
        byte[] rejoined = SpliceItemOption.joinChunks(fileContents);
        List<byte[]> reSplit = SpliceItemOption.splitChunks(rejoined, fileData.length);
        for (int i = 0; i < fileData.length; i++) {
            if (i == slot) continue;
            if (!java.util.Arrays.equals(reSplit.get(i), SpliceItemOption.splitChunks(decompressed, fileData.length).get(i))) {
                throw new IllegalStateException("slot " + i + " changed unexpectedly -- ABORTING, no write performed.");
            }
        }
        System.out.println("Verified: all " + (fileData.length - 1) + " other components unchanged.");

        if (!apply) {
            System.out.println("DRY RUN -- not writing. Re-run with 'apply' to persist.");
            return;
        }

        Container container = new Container(archive.getCompression(), -1);
        container.compress(rejoined, null);
        storage.store(index.getId(), archive.getArchiveId(), container.data);
        archive.setCrc(container.crc);
        archive.setRevision(archive.getRevision() + 1);
        archive.setCompressedSize(container.data.length);
        archive.setDecompressedSize(rejoined.length);

        writeIndexReferenceTable(storage, index);
        System.out.println("APPLY complete. Interface " + interfaceId + " component " + componentIndex
                + " is now a visible, clickable \"Presets\" button. Archive revision now " + archive.getRevision() + ".");
    }

    // ============================== interface sprite-id remap ==============================

    // Component 86 of interface 5900 (the ported bank-preset interface, source toml 832.toml)
    // references sprite = 900 -- one of two custom background sprites that were originally
    // skipped when adding new sprites (ids 900/901 already had unrelated content in this cache),
    // so addInterface() baked in the OLD, un-remapped sprite id. addsprites already re-added that
    // art at the new safe ids 90000/90001; this rewrites component 86's `sprite` field to point at
    // the new id. Only component 86's bytes change -- every other component in the archive is
    // verified byte-identical before/after.
    static void fixInterfaceSprite(Store store, Storage storage, File interfaceDir, int interfaceId,
            int componentIndex, Map<Integer, Integer> spriteRemap, boolean apply) throws Exception {
        File[] tomls = interfaceDir.listFiles((d, name) -> name.endsWith(".toml"));
        if (tomls == null || tomls.length != 1) {
            throw new IllegalStateException("expected exactly one interface toml in " + interfaceDir);
        }
        String text = new String(Files.readAllBytes(tomls[0].toPath()), StandardCharsets.UTF_8);
        Map<Integer, Map<String, Object>> components = parseInterfaceToml(text);
        Map<String, Object> fields = components.get(componentIndex);
        if (fields == null) {
            throw new IllegalStateException("component " + componentIndex + " not found in " + tomls[0]);
        }
        Object oldSpriteObj = fields.get("sprite");
        if (!(oldSpriteObj instanceof Number)) {
            throw new IllegalStateException("component " + componentIndex + " has no numeric sprite field");
        }
        int oldSprite = ((Number) oldSpriteObj).intValue();
        Integer newSprite = spriteRemap.get(oldSprite);
        if (newSprite == null) {
            throw new IllegalStateException("no remap entry for sprite " + oldSprite);
        }
        fields.put("sprite", newSprite);

        Index index = store.getIndex(IndexType.INTERFACES);
        Archive archive = index.getArchive(interfaceId);
        byte[] decompressed = archive.decompress(storage.loadArchive(archive));
        FileData[] fileData = archive.getFileData();
        List<byte[]> fileContents = SpliceItemOption.splitChunks(decompressed, fileData.length);

        int slot = -1;
        for (int i = 0; i < fileData.length; i++) {
            if (fileData[i].getId() == componentIndex) {
                slot = i;
                break;
            }
        }
        if (slot == -1) {
            throw new IllegalStateException("component " + componentIndex + " not found in interface " + interfaceId);
        }

        var loader = new net.runelite.cache.definitions.loaders.InterfaceLoader();
        int widgetId = (interfaceId << 16) + componentIndex;
        var beforeDef = loader.load(widgetId, fileContents.get(slot));
        System.out.println("BEFORE: type=" + beforeDef.type + " spriteId=" + beforeDef.spriteId);

        byte[] after = encodeInterfaceComponent(fields);
        var afterDef = loader.load(widgetId, after);
        if (afterDef.type != beforeDef.type || afterDef.spriteId != newSprite) {
            throw new IllegalStateException("round-trip verification FAILED -- ABORTING.");
        }
        System.out.println("AFTER:  type=" + afterDef.type + " spriteId=" + afterDef.spriteId);

        fileContents.set(slot, after);

        byte[] rejoined = SpliceItemOption.joinChunks(fileContents);
        List<byte[]> reSplit = SpliceItemOption.splitChunks(rejoined, fileData.length);
        List<byte[]> origSplit = SpliceItemOption.splitChunks(decompressed, fileData.length);
        for (int i = 0; i < fileData.length; i++) {
            if (i == slot) continue;
            if (!java.util.Arrays.equals(reSplit.get(i), origSplit.get(i))) {
                throw new IllegalStateException("slot " + i + " changed unexpectedly -- ABORTING, no write performed.");
            }
        }
        System.out.println("Verified: all " + (fileData.length - 1) + " other components unchanged.");

        if (!apply) {
            System.out.println("DRY RUN -- not writing. Re-run with 'apply' to persist.");
            return;
        }

        Container container = new Container(archive.getCompression(), -1);
        container.compress(rejoined, null);
        storage.store(index.getId(), archive.getArchiveId(), container.data);
        archive.setCrc(container.crc);
        archive.setRevision(archive.getRevision() + 1);
        archive.setCompressedSize(container.data.length);
        archive.setDecompressedSize(rejoined.length);

        writeIndexReferenceTable(storage, index);
        System.out.println("APPLY complete. Interface " + interfaceId + " component " + componentIndex
                + " sprite remapped " + oldSprite + " -> " + newSprite + ". Archive revision now "
                + archive.getRevision() + ".");
    }

    // ============================== interface (IF3 widget format) ==============================

    // Every component in this specific toml uses align_x=Min, align_y=Min, align_width=Abs,
    // align_height=Abs uniformly (confirmed by grepping the whole file) -- so widthMode/heightMode/
    // xPositionMode/yPositionMode are always 0, and the widthMode/heightMode-conditional extra
    // Model fields (modelHeightOverride + a discarded read) never apply. Cosmetic hover listeners
    // (on_mouse_over/on_mouse_leave, a custom "167(832:37, 6115134)" script-call notation this
    // bundle's own export tool uses) are intentionally NOT encoded -- they only drive a hover
    // color/sprite swap, not actual click handling (that's server-side Java keyed by componentId +
    // the `actions` menu-option labels, which ARE encoded). on_op IS encoded since it's the one
    // real listener present (always the simple "29()" no-arg script-id form in this file).
    static void addInterface(Store store, Storage storage, File interfaceDir, int newInterfaceId, boolean apply)
            throws Exception {
        Index index = store.getIndex(IndexType.INTERFACES);
        if (index.getArchive(newInterfaceId) != null) {
            throw new IllegalStateException("interface " + newInterfaceId + " already exists -- ABORTING.");
        }

        File[] tomls = interfaceDir.listFiles((d, name) -> name.endsWith(".toml"));
        if (tomls == null || tomls.length != 1) {
            throw new IllegalStateException("expected exactly one interface toml in " + interfaceDir);
        }
        String text = new String(Files.readAllBytes(tomls[0].toPath()), StandardCharsets.UTF_8);
        Map<Integer, Map<String, Object>> components = parseInterfaceToml(text);

        List<byte[]> fileContents = new ArrayList<>();
        List<FileData> fileDataList = new ArrayList<>();
        int maxIndex = components.keySet().stream().max(Integer::compareTo).orElseThrow();
        for (int i = 0; i <= maxIndex; i++) {
            Map<String, Object> comp = components.get(i);
            if (comp == null) {
                throw new IllegalStateException("component index " + i + " missing from toml (non-contiguous)");
            }
            byte[] encoded = encodeInterfaceComponent(comp);
            fileContents.add(encoded);
            FileData fd = new FileData();
            fd.setId(i);
            fd.setNameHash(-1);
            fileDataList.add(fd);

            // self-verify: decode what we just built and confirm the structural fields round-trip
            int widgetId = (newInterfaceId << 16) + i;
            var def = new net.runelite.cache.definitions.loaders.InterfaceLoader().load(widgetId, encoded);
            int expectedType = typeNumber((String) comp.get("_type"));
            if (def.type != expectedType || !def.isIf3) {
                throw new IllegalStateException("component " + i + ": round-trip type mismatch -- ABORTING.");
            }
        }

        System.out.println("Prepared interface " + newInterfaceId + " with " + fileContents.size()
                + " components.");
        if (!apply) {
            System.out.println("DRY RUN -- not writing. Re-run with 'apply' to persist.");
            return;
        }

        Archive archive = index.addArchive(newInterfaceId);
        archive.setNameHash(-1);
        int compression = defaultCompressionFor(index);
        archive.setCompression(compression);
        archive.setFileData(fileDataList.toArray(new FileData[0]));

        byte[] joined = SpliceItemOption.joinChunks(fileContents);
        Container container = new Container(compression, -1);
        container.compress(joined, null);
        storage.store(index.getId(), newInterfaceId, container.data);
        archive.setCrc(container.crc);
        archive.setRevision(1);
        archive.setCompressedSize(container.data.length);
        archive.setDecompressedSize(joined.length);

        writeIndexReferenceTable(storage, index);
        System.out.println("APPLY complete. Interface " + newInterfaceId + " added with "
                + fileContents.size() + " components.");
    }

    static int typeNumber(String typeName) {
        switch (typeName) {
            case "Layer": return 0;
            case "Rectangle": return 3;
            case "Text": return 4;
            case "Sprite": return 5;
            case "Model": return 6;
            default: throw new IllegalStateException("unknown component type " + typeName);
        }
    }

    @SuppressWarnings("unchecked")
    static byte[] encodeInterfaceComponent(Map<String, Object> f) {
        int type = typeNumber((String) f.get("_type"));
        OutputStream os = new OutputStream(64);

        os.writeByte(255); // IF3 marker (re-consumed as decodeIf3's own first read)
        os.writeByte(type);
        os.writeShort(intOr(f, "content", 0));
        os.writeShort(intOr(f, "x", 0) & 0xFFFF);
        os.writeShort(intOr(f, "y", 0) & 0xFFFF);
        os.writeShort(intOr(f, "width", 0));
        os.writeShort(intOr(f, "height", 0)); // type 9 (Line) not used in this content
        os.writeByte(0); // widthMode
        os.writeByte(0); // heightMode
        os.writeByte(0); // xPositionMode
        os.writeByte(0); // yPositionMode
        int parent = intOr(f, "parent", -1);
        os.writeShort(parent == -1 ? 0xFFFF : parent);
        os.writeByte(bool(f, "hidden") ? 1 : 0);

        if (type == 0) { // Layer
            os.writeShort(intOr(f, "scroll_width", 0));
            os.writeShort(intOr(f, "scroll_height", 0));
            os.writeByte(bool(f, "no_click_through") ? 1 : 0);
        }
        if (type == 5) { // Sprite
            os.writeInt(intOr(f, "sprite", -1));
            os.writeShort(intOr(f, "texture", 0));
            os.writeByte(bool(f, "tilling") ? 1 : 0);
            os.writeByte(intOr(f, "opacity", 0));
            os.writeByte(intOr(f, "border_kind", 0));
            os.writeInt(intOr(f, "shadow_color", 0));
            os.writeByte(bool(f, "flipped_vertically") ? 1 : 0);
            os.writeByte(bool(f, "flipped_horizontally") ? 1 : 0);
        }
        if (type == 6) { // Model
            int modelId = intOr(f, "id", -1);
            os.writeShort(modelId == -1 || modelId == 0xFFFF ? 0xFFFF : modelId);
            os.writeShort(intOr(f, "offset_x2d", 0) & 0xFFFF);
            os.writeShort(intOr(f, "offset_y2d", 0) & 0xFFFF);
            os.writeShort(intOr(f, "rotation_x", 0));
            os.writeShort(intOr(f, "rotation_z", 0));
            os.writeShort(intOr(f, "rotation_y", 0));
            os.writeShort(intOr(f, "zoom", 0));
            int animation = intOr(f, "animation", -1);
            os.writeShort(animation == -1 || animation == 0xFFFF ? 0xFFFF : animation);
            os.writeByte(bool(f, "orthogonal") ? 1 : 0);
            os.writeShort(intOr(f, "idk", 0)); // discarded on read, but must still be present
            // widthMode/heightMode are always 0 in this content, so the two conditional fields
            // decodeIf3 reads when either mode != 0 (modelHeightOverride + a discarded short)
            // never apply here.
        }
        if (type == 4) { // Text
            int font = intOr(f, "font", -1);
            os.writeShort(font == -1 || font == 0xFFFF ? 0xFFFF : font);
            os.writeString(str(f, "text"));
            os.writeByte(intOr(f, "line_height", 0));
            os.writeByte(textAlign((String) f.get("alignment_x")));
            os.writeByte(textAlign((String) f.get("alignment_y")));
            os.writeByte(bool(f, "shadowed") ? 1 : 0);
            os.writeInt(intOr(f, "color", 0));
        }
        if (type == 3) { // Rectangle
            os.writeInt(intOr(f, "color", 0));
            os.writeByte(bool(f, "fill") ? 1 : 0);
            os.writeByte(intOr(f, "opacity", 0));
        }

        os.write24BitInt(intOr(f, "click_mask", 0));
        os.writeString(""); // name -- never populated in this content
        List<Object> actions = (List<Object>) f.get("actions");
        if (actions == null) actions = List.of();
        os.writeByte(actions.size());
        for (Object a : actions) {
            os.writeString((String) a);
        }

        os.writeByte(0); // dragDeadZone
        os.writeByte(0); // dragDeadTime
        os.writeByte(0); // dragRenderBehavior
        os.writeString(""); // targetVerb -- never populated in this content

        // 18 listeners in decodeIf3's exact order. Only onOp ever has real content in this file
        // (always the simple "29()" no-arg form); every other slot -- including the cosmetic
        // on_mouse_over/on_mouse_leave hover scripts -- is intentionally written as empty/null.
        String[] listenerKeysInOrder = {
            null, null, null, null, null, null, null, null, null, // onLoad..onTimer
            "on_op",
            null, null, null, null, null, null, null, null // onMouseRepeat..onScrollWheel
        };
        for (String key : listenerKeysInOrder) {
            Object raw = key == null ? null : f.get(key);
            if (raw == null) {
                os.writeByte(0);
            } else {
                writeOnOpListener(os, (String) raw);
            }
        }

        os.writeByte(0); // varTransmitTriggers
        os.writeByte(0); // invTransmitTriggers
        os.writeByte(0); // statTransmitTriggers

        return os.flip();
    }

    // Handles this content's only real listener value shape: "SCRIPTID()" (no arguments) -- the
    // one on_op value ("29()") found anywhere in this interface. Refuses to guess at any other
    // shape rather than silently mis-encoding it.
    static void writeOnOpListener(OutputStream os, String raw) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(\\d+)\\(\\)$").matcher(raw.trim());
        if (!m.matches()) {
            throw new IllegalStateException("unsupported listener notation \"" + raw
                    + "\" -- only bare no-arg \"N()\" is implemented, refusing to guess.");
        }
        int scriptId = Integer.parseInt(m.group(1));
        os.writeByte(1); // one entry
        os.writeByte(0); // type 0 = int
        os.writeInt(scriptId);
    }

    static int textAlign(String value) {
        if (value == null) return 0;
        switch (value) {
            case "Min": return 0;
            case "Center": return 1;
            case "Max": return 2;
            default: throw new IllegalStateException("unknown text alignment \"" + value + "\"");
        }
    }

    static Map<Integer, Map<String, Object>> parseInterfaceToml(String text) {
        Map<Integer, Map<String, Object>> components = new java.util.TreeMap<>();
        Map<String, Object> current = null;
        for (String rawLine : text.split("\n")) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            if (line.startsWith("[") && line.endsWith("]")) {
                String header = line.substring(1, line.length() - 1);
                String[] parts = header.split("\\.");
                Integer idx;
                try {
                    idx = Integer.parseInt(parts[0]);
                } catch (NumberFormatException e) {
                    current = null; // e.g. a stray non-component header, ignore
                    continue;
                }
                current = components.computeIfAbsent(idx, k -> new LinkedHashMap<>());
                if (parts.length == 3) {
                    current.put("_type", parts[2]);
                }
                continue;
            }
            if (current == null) continue; // e.g. the leading "root = 832" line, no section yet
            int eq = line.indexOf('=');
            if (eq < 0) continue;
            String key = line.substring(0, eq).trim();
            String rest = line.substring(eq + 1).trim();
            current.put(key, parseScalarOrArray(rest));
        }
        return components;
    }
}

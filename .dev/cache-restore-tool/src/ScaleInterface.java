import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.InterfaceDefinition;
import net.runelite.cache.definitions.loaders.InterfaceLoader;
import net.runelite.cache.fs.*;
import net.runelite.cache.index.FileData;
import net.runelite.cache.index.IndexData;

import java.io.File;
import java.lang.reflect.Field;
import java.util.*;

// Copies an IF3 interface group to a new group id scaled by a factor (same component ids, so the
// server can drive both with the same code). Used for the resizable-only large Equipment Presets
// window: 5900 -> 1142 at 1.35x, opened in the full-viewport world-map slot.
//  - root (component 0): scaled size, centred in its slot (x/yPositionMode 1);
//  - every other component: x/y/width/height scaled;
//  - "keep" components (item layers filled by clientscript 149, silhouettes, small icons) keep their real
//    size and are centred in their scaled box, so item icons and sprites don't stretch;
//  - text fonts go up one step (494 -> 495 -> 496); model components get their zoom scaled too.
// Safety: first re-encodes every ORIGINAL component with BuildItemStatsInterface.encodeIf3 and requires
// it to match the cache bytes exactly (so nothing the encoder doesn't know about is lost), then verifies
// every scaled component decodes back to exactly the intended definition.
// Usage: verify|apply|rebuild <cachePath> <srcGroup> <dstGroup> <factor> <keepIds e.g. 7,63-69,70-86>
public class ScaleInterface {
    public static void main(String[] args) throws Exception {
        String mode = args[0];
        int src = Integer.parseInt(args[2]), dst = Integer.parseInt(args[3]);
        double f = Double.parseDouble(args[4]);
        Set<Integer> keep = new HashSet<>();
        for (String part : args[5].split(",")) {
            String[] r = part.split("-");
            for (int i = Integer.parseInt(r[0]); i <= Integer.parseInt(r[r.length - 1]); i++) keep.add(i);
        }
        Map<Integer, Integer> fonts = Map.of(494, 495, 495, 496);

        try (Store store = new Store(new File(args[1]))) {
            store.load();
            Storage storage = store.getStorage();
            Index index = store.getIndex(IndexType.INTERFACES);
            if (index.getArchive(dst) != null && !mode.equals("rebuild") && !mode.equals("verify"))
                throw new IllegalStateException("group " + dst + " already exists (use rebuild)");
            Archive srcArchive = index.getArchive(src);
            ArchiveFiles files = srcArchive.getFiles(storage.loadArchive(srcArchive));
            InterfaceLoader loader = new InterfaceLoader();
            List<FSFile> list = new ArrayList<>(files.getFiles());
            list.sort(Comparator.comparingInt(FSFile::getFileId));

            List<byte[]> out = new ArrayList<>();
            for (FSFile file : list) {
                int c = file.getFileId();
                if (c != out.size()) throw new IllegalStateException("non-contiguous component ids at " + c);
                byte[] raw = file.getContents();
                InterfaceDefinition d = loader.load((src << 16) | c, raw);
                if (!d.isIf3) throw new IllegalStateException("component " + c + " is IF1 -- not supported");
                if (!Arrays.equals(BuildItemStatsInterface.encodeIf3(d), raw))
                    throw new IllegalStateException("encoder does not reproduce component " + c + " -- ABORTING");
                if (d.widthMode != 0 || d.heightMode != 0 || d.xPositionMode != 0 || d.yPositionMode != 0)
                    throw new IllegalStateException("component " + c + " uses relative layout modes -- not supported");

                if (c == 0) {
                    d.originalWidth = (int) Math.round(d.originalWidth * f);
                    d.originalHeight = (int) Math.round(d.originalHeight * f);
                    d.originalX = d.originalY = 0;
                    d.xPositionMode = d.yPositionMode = 1; // centred in the slot
                } else if (keep.contains(c)) {
                    d.originalX = (int) Math.round(d.originalX * f + (d.originalWidth * f - d.originalWidth) / 2);
                    d.originalY = (int) Math.round(d.originalY * f + (d.originalHeight * f - d.originalHeight) / 2);
                } else {
                    int x2 = (int) Math.round((d.originalX + d.originalWidth) * f), y2 = (int) Math.round((d.originalY + d.originalHeight) * f);
                    d.originalX = (int) Math.round(d.originalX * f);
                    d.originalY = (int) Math.round(d.originalY * f);
                    d.originalWidth = x2 - d.originalX; // edges scaled, so neighbours stay flush
                    d.originalHeight = y2 - d.originalY;
                    if (d.type == 0) {
                        d.scrollWidth = (int) Math.round(d.scrollWidth * f);
                        d.scrollHeight = (int) Math.round(d.scrollHeight * f);
                    }
                    if (d.type == 6 && d.modelZoom > 0)
                        d.modelZoom = (int) Math.round(d.modelZoom / f);
                }
                if (d.parentId != -1)
                    d.parentId = (dst << 16) | (d.parentId & 0xFFFF);
                if (d.type == 4 && fonts.containsKey(d.fontId))
                    d.fontId = fonts.get(d.fontId);

                byte[] enc = BuildItemStatsInterface.encodeIf3(d);
                InterfaceDefinition back = loader.load((dst << 16) | c, enc);
                d.id = (dst << 16) | c;
                for (Field fld : InterfaceDefinition.class.getFields()) {
                    if (java.lang.reflect.Modifier.isStatic(fld.getModifiers())) continue;
                    if (!Objects.deepEquals(fld.get(back), fld.get(d)))
                        throw new IllegalStateException("component " + c + ": " + fld.getName() + " mismatch after scaling");
                }
                out.add(enc);
            }
            System.out.println(out.size() + " components scaled " + src + " -> " + dst + " x" + f + " (" + keep.size() + " kept at real size)");
            if (mode.equals("verify")) {
                System.out.println("VERIFY OK -- nothing written.");
                return;
            }

            Archive archive = index.getArchive(dst) != null ? index.getArchive(dst) : index.addArchive(dst);
            archive.setNameHash(-1);
            archive.setCompression(srcArchive.getCompression());
            archive.setRevision(archive.getRevision() + 1);
            FileData[] fd = new FileData[out.size()];
            ArchiveFiles af = new ArchiveFiles();
            for (int c = 0; c < out.size(); c++) {
                fd[c] = new FileData();
                fd[c].setId(c);
                FSFile file = new FSFile(c);
                file.setContents(out.get(c));
                af.addFile(file);
            }
            archive.setFileData(fd);
            byte[] data = af.saveContents();
            Container container = new Container(archive.getCompression(), -1);
            container.compress(data, null);
            storage.store(index.getId(), dst, container.data);
            archive.setCrc(container.crc);
            archive.setCompressedSize(container.data.length);
            archive.setDecompressedSize(data.length);
            IndexData indexData = index.toIndexData();
            Container ic = new Container(index.getCompression(), -1);
            ic.compress(indexData.writeIndexData(), null);
            storage.store(255, index.getId(), ic.data);
            index.setCrc(ic.crc);
            System.out.println("APPLY complete -- interface " + dst + ".");
        }
    }
}

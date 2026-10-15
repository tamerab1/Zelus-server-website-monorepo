import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.ObjectDefinition;
import net.runelite.cache.definitions.loaders.ModelLoader;
import net.runelite.cache.definitions.loaders.ObjectLoader;
import net.runelite.cache.fs.*;
import net.runelite.cache.index.FileData;
import net.runelite.cache.index.IndexData;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.*;

// Renames an object and recolors every distinct face color of its model into the Draco
// minigame's dark-red/gold palette via recolorToFind/Replace (opcode 40), preserving each
// color's lightness so shading/detail survives. Warm hues (reds/oranges/yellows/magentas) -> gold,
// cool hues (greens/blues/purples) -> dark red, greys -> dark red-brown. Written for 62389
// ("Prismatic Chest" -> "Draco Chest", model 57351, 97 rainbow colors).
//
// Usage: verify|apply <cachePath> <objectId> <newName>
public class RecolorObjectDracoPalette {
    public static void main(String[] args) throws Exception {
        boolean apply = args[0].equals("apply");
        int objId = Integer.parseInt(args[2]);
        String newName = args[3];
        try (Store store = new Store(new File(args[1]))) {
            store.load();
            Storage storage = store.getStorage();
            Index configs = store.getIndex(IndexType.CONFIGS);
            Archive archive = configs.getArchive(ConfigType.OBJECT.getId());
            byte[] dec = archive.decompress(storage.loadArchive(archive));
            FileData[] fd = archive.getFileData();
            List<byte[]> contents = SpliceItemOption.splitChunks(dec, fd.length);
            int slot = -1;
            for (int i = 0; i < fd.length; i++) if (fd[i].getId() == objId) slot = i;
            if (slot < 0) throw new IllegalStateException("object " + objId + " not found");
            byte[] raw = contents.get(slot);
            ObjectLoader loader = new ObjectLoader();
            ObjectDefinition before = loader.load(objId, raw);
            if (before.getRecolorToFind() != null) throw new IllegalStateException("object already has a recolor -- ABORTING");

            TreeSet<Integer> colors = new TreeSet<>();
            for (int modelId : before.getObjectModels()) {
                Archive ma = store.getIndex(IndexType.MODELS).getArchive(modelId);
                ModelDefinition m = new ModelLoader().load(modelId, ma.decompress(storage.loadArchive(ma)));
                for (short c : m.faceColors) colors.add(c & 0xFFFF);
            }
            if (colors.size() > 255) throw new IllegalStateException("too many colors for opcode 40 -- ABORTING");

            ByteArrayOutputStream p = new ByteArrayOutputStream();
            p.write(colors.size());
            int gold = 0, red = 0;
            for (int c : colors) {
                int h = (c >> 10) & 63, s = (c >> 7) & 7, l = c & 127;
                int nh, ns, nl;
                if (s == 0) { nh = 2; ns = 3; nl = Math.min(l, 40); }                       // grey -> dark red-brown
                else if (h <= 15 || h >= 50) { nh = 8; ns = 6; nl = Math.min(l + 12, 115); gold++; } // warm -> gold
                else { nh = 0; ns = 7; nl = Math.max(4, l - 4); red++; }                     // cool -> dark red
                int r = (nh << 10) | (ns << 7) | nl;
                p.write(c >> 8); p.write(c);
                p.write(r >> 8); p.write(r);
            }
            byte[] work = SpliceNewObject.replaceOpcode(raw, 40, p.toByteArray());
            work = SpliceNewObject.replaceOpcode(work, 2, SpliceNewObject.withTerminator(newName));
            ObjectDefinition after = loader.load(objId, work);
            if (!newName.equals(after.getName()) || after.getRecolorToFind() == null
                    || after.getRecolorToFind().length != colors.size()
                    || !Arrays.equals(before.getObjectModels(), after.getObjectModels())
                    || !Arrays.equals(before.getActions(), after.getActions()))
                throw new IllegalStateException("verification failed -- ABORTING, nothing written");
            System.out.println(objId + ": name \"" + before.getName() + "\" -> \"" + after.getName() + "\", recolor "
                + colors.size() + " colors (" + gold + " gold, " + red + " red, " + (colors.size() - gold - red) + " grey)");
            if (!apply) { System.out.println("VERIFY mode -- not writing."); return; }

            contents.set(slot, work);
            byte[] newDec = SpliceItemOption.joinChunks(contents);
            Container c = new Container(archive.getCompression(), -1);
            c.compress(newDec, null);
            storage.store(configs.getId(), archive.getArchiveId(), c.data);
            archive.setCrc(c.crc);
            archive.setRevision(archive.getRevision() + 1);
            archive.setCompressedSize(c.data.length);
            archive.setDecompressedSize(newDec.length);
            IndexData indexData = configs.toIndexData();
            Container ic = new Container(configs.getCompression(), -1);
            ic.compress(indexData.writeIndexData(), null);
            storage.store(255, configs.getId(), ic.data);
            configs.setCrc(ic.crc);
            System.out.println("APPLY complete. Object archive revision now " + archive.getRevision());
        }
    }
}

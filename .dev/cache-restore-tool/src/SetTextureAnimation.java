import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.TextureDefinition;
import net.runelite.cache.definitions.loaders.TextureLoader;
import net.runelite.cache.fs.*;
import net.runelite.cache.index.FileData;
import net.runelite.cache.index.IndexData;

import java.io.File;
import java.util.*;

// Changes an existing texture's animation direction/speed in place. Texture records here are 12
// bytes for single-sprite textures (same layout CreateRecoloredTextures writes): byte 10 =
// animation direction, byte 11 = animation speed. Re-decodes with TextureLoader and checks that
// only those two fields changed before writing.
// Usage: verify|apply <cachePath> <texId>:<dir>:<speed> [<texId>:<dir>:<speed> ...]
public class SetTextureAnimation {
    public static void main(String[] args) throws Exception {
        String mode = args[0];
        try (Store store = new Store(new File(args[1]))) {
            store.load();
            Storage storage = store.getStorage();
            Index textures = store.getIndex(IndexType.TEXTURES);
            Archive archive = textures.getArchive(0);
            byte[] dec = archive.decompress(storage.loadArchive(archive));
            FileData[] fd = archive.getFileData();
            List<byte[]> contents = SpliceItemOption.splitChunks(dec, fd.length);

            for (int a = 2; a < args.length; a++) {
                String[] p = args[a].split(":");
                int tex = Integer.parseInt(p[0]), dir = Integer.parseInt(p[1]), speed = Integer.parseInt(p[2]);
                int slot = -1;
                for (int i = 0; i < fd.length; i++) if (fd[i].getId() == tex) slot = i;
                if (slot < 0) throw new IllegalStateException("texture " + tex + " not found");
                byte[] b = contents.get(slot);
                if (b.length != 12 || (b[3] & 0xFF) != 1) throw new IllegalStateException("texture " + tex + ": unexpected layout");
                TextureDefinition before = new TextureLoader().load(tex, b);
                byte[] n = b.clone();
                n[10] = (byte) dir;
                n[11] = (byte) speed;
                TextureDefinition after = new TextureLoader().load(tex, n);
                if (after.animationDirection != dir || after.animationSpeed != speed
                        || !Arrays.equals(after.getFileIds(), before.getFileIds()) || after.field1777 != before.field1777
                        || after.field1778 != before.field1778 || !Arrays.equals(after.field1780, before.field1780)
                        || !Arrays.equals(after.field1781, before.field1781) || !Arrays.equals(after.field1786, before.field1786))
                    throw new IllegalStateException("texture " + tex + ": re-decode mismatch");
                System.out.printf("texture %d: dir %d -> %d, speed %d -> %d%n", tex, before.animationDirection, dir, before.animationSpeed, speed);
                contents.set(slot, n);
            }
            if (mode.equals("verify")) {
                System.out.println("VERIFY OK -- nothing written.");
                return;
            }
            if (!mode.equals("apply")) throw new IllegalArgumentException("mode must be verify or apply");
            byte[] joined = SpliceItemOption.joinChunks(contents);
            Container c = new Container(archive.getCompression(), -1);
            c.compress(joined, null);
            storage.store(textures.getId(), archive.getArchiveId(), c.data);
            archive.setCrc(c.crc);
            archive.setRevision(archive.getRevision() + 1);
            archive.setCompressedSize(c.data.length);
            archive.setDecompressedSize(joined.length);
            IndexData indexData = textures.toIndexData();
            Container ic = new Container(textures.getCompression(), -1);
            ic.compress(indexData.writeIndexData(), null);
            storage.store(255, textures.getId(), ic.data);
            textures.setCrc(ic.crc);
            System.out.println("APPLY complete -- texture archive revision " + archive.getRevision());
        }
    }
}

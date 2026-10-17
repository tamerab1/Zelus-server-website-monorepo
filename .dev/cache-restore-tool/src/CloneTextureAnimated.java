import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.TextureDefinition;
import net.runelite.cache.definitions.loaders.TextureLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Container;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// Appends an exact copy of an existing (single-sprite) texture under a new id with only the animation direction /
// speed changed: same sprite, same average colour, so the copy looks identical but flows. For a stock texture that
// other content may use (Morveth (or) 2026-10-17: texture 60 is static, the base set's 56 flows at dir 1 speed 2),
// so the original stays untouched and only the items retextured onto the copy change.
// Usage: verify|apply <cachePath> <srcTexId> <newTexId> <dir> <speed>
public class CloneTextureAnimated {
    public static void main(String[] args) throws Exception {
        String mode = args[0];
        int srcTex = Integer.parseInt(args[2]), newTex = Integer.parseInt(args[3]);
        int dir = Integer.parseInt(args[4]), speed = Integer.parseInt(args[5]);
        try (Store store = new Store(new File(args[1]))) {
            store.load();
            Storage storage = store.getStorage();
            Index textures = store.getIndex(IndexType.TEXTURES);
            Archive texArchive = textures.getArchive(0);
            byte[] texDecompressed = texArchive.decompress(storage.loadArchive(texArchive));
            FileData[] texFd = texArchive.getFileData();
            List<byte[]> texContents = SpliceItemOption.splitChunks(texDecompressed, texFd.length);

            CreateRecoloredTextures.check(CreateRecoloredTextures.slotOf(texFd, newTex) == -1, "texture " + newTex + " already exists");
            CreateRecoloredTextures.check(newTex > texFd[texFd.length - 1].getId(), "new texture ids must be appended above " + texFd[texFd.length - 1].getId());
            byte[] src = texContents.get(CreateRecoloredTextures.requireSlot(texFd, srcTex));
            TextureDefinition st = new TextureLoader().load(srcTex, src);
            CreateRecoloredTextures.check(src.length == 12 && (src[3] & 0xFF) == 1, "unexpected texture layout");

            byte[] nt = src.clone();
            nt[10] = (byte) dir;
            nt[11] = (byte) speed;
            TextureDefinition t = new TextureLoader().load(newTex, nt);
            CreateRecoloredTextures.check(Arrays.equals(t.getFileIds(), st.getFileIds()) && t.animationDirection == dir
                    && t.animationSpeed == speed && t.field1777 == st.field1777 && t.field1778 == st.field1778
                    && Arrays.equals(t.field1780, st.field1780) && Arrays.equals(t.field1781, st.field1781)
                    && Arrays.equals(t.field1786, st.field1786), "texture " + newTex + " re-decode mismatch");
            System.out.printf("texture %d -> %d  sprite %s  dir %d -> %d  speed %d -> %d%n", srcTex, newTex,
                    Arrays.toString(st.getFileIds()), st.animationDirection, dir, st.animationSpeed, speed);
            if (mode.equals("verify")) {
                System.out.println("VERIFY OK -- nothing written.");
                return;
            }
            if (!mode.equals("apply")) throw new IllegalArgumentException("mode must be verify or apply");

            List<FileData> fdList = new ArrayList<>(Arrays.asList(texFd));
            FileData fd = new FileData();
            fd.setId(newTex);
            fd.setNameHash(-1);
            fdList.add(fd);
            texContents.add(nt);
            texArchive.setFileData(fdList.toArray(new FileData[0]));
            byte[] joined = SpliceItemOption.joinChunks(texContents);
            Container tc = new Container(texArchive.getCompression(), -1);
            tc.compress(joined, null);
            storage.store(textures.getId(), texArchive.getArchiveId(), tc.data);
            texArchive.setCrc(tc.crc);
            texArchive.setRevision(texArchive.getRevision() + 1);
            texArchive.setCompressedSize(tc.data.length);
            texArchive.setDecompressedSize(joined.length);
            CreateRecoloredTextures.storeIndex(storage, textures);
            System.out.println("APPLY complete -- texture " + newTex + " written.");
        }
    }
}

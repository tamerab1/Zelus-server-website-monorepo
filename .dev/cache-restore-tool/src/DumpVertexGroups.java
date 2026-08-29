import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.io.InputStream;

import java.io.File;

public class DumpVertexGroups {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Index models = store.getIndex(IndexType.MODELS);
            for (int a = 1; a < args.length; a++) {
                int modelId = Integer.parseInt(args[a]);
                Archive archive = models.getArchive(modelId);
                byte[] raw = archive.decompress(storage.loadArchive(archive));

                InputStream header = new InputStream(raw);
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

                if (hasPackedVertexGroups != 1) {
                    System.out.println(modelId + ": no packed vertex groups");
                    continue;
                }

                int var31 = numTextureFaces + vertexCount;
                if (hasFaceRenderTypes == 1) var31 += faceCount;
                var31 += faceCount; // faceColors
                if (priorityMarker == 255) var31 += faceCount;
                if (hasPackedTransparencyVertexGroups == 1) var31 += faceCount;

                int[] groups = new int[vertexCount];
                for (int i = 0; i < vertexCount; i++) {
                    groups[i] = raw[var31 + i] & 0xFF;
                }
                int max = 0;
                for (int g : groups) max = Math.max(max, g);
                System.out.println(modelId + ": vertexCount=" + vertexCount + " maxGroup=" + max
                        + " groups=" + java.util.Arrays.toString(groups));
            }
        }
    }
}

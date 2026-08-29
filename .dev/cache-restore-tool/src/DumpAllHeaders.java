import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.io.InputStream;

import java.io.File;

public class DumpAllHeaders {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Index models = store.getIndex(IndexType.MODELS);

            for (int i = 1; i < args.length; i++) {
                int modelId = Integer.parseInt(args[i]);
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

                System.out.println("model " + modelId + ": len=" + raw.length + " vtx=" + vertexCount
                        + " faces=" + faceCount + " numTexFaces=" + numTextureFaces
                        + " faceRenderTypes=" + hasFaceRenderTypes + " priority=" + priorityMarker
                        + " faceTrans=" + hasFaceTransparencies + " packedTransVG=" + hasPackedTransparencyVertexGroups
                        + " faceTex=" + hasFaceTextures + " packedVG=" + hasPackedVertexGroups);
            }
        }
    }
}

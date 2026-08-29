import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.loaders.ModelLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.io.InputStream;

import java.io.File;

public class DebugModelOffset {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        int modelId = Integer.parseInt(args[1]);
        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Index models = store.getIndex(IndexType.MODELS);
            Archive archive = models.getArchive(modelId);
            byte[] raw = archive.decompress(storage.loadArchive(archive));

            System.out.println("length=" + raw.length + " marker=[" + raw[raw.length-2] + "," + raw[raw.length-1] + "]");

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
            int var18 = header.readUnsignedShort();
            int var19 = header.readUnsignedShort();
            int var20 = header.readUnsignedShort();
            int var21 = header.readUnsignedShort();

            System.out.println("vertexCount=" + vertexCount + " faceCount=" + faceCount
                    + " numTextureFaces=" + numTextureFaces + " hasFaceRenderTypes=" + hasFaceRenderTypes
                    + " priorityMarker=" + priorityMarker + " hasFaceTransparencies=" + hasFaceTransparencies
                    + " hasPackedTransparencyVertexGroups=" + hasPackedTransparencyVertexGroups
                    + " hasFaceTextures=" + hasFaceTextures + " hasPackedVertexGroups=" + hasPackedVertexGroups
                    + " var18=" + var18 + " var19=" + var19 + " var20=" + var20 + " var21=" + var21);

            int offset = numTextureFaces + vertexCount;
            if (hasFaceRenderTypes == 1) offset += faceCount;
            offset += faceCount;
            if (priorityMarker == 255) offset += faceCount;
            if (hasPackedTransparencyVertexGroups == 1) offset += faceCount;
            if (hasPackedVertexGroups == 1) offset += vertexCount;
            if (hasFaceTransparencies == 1) offset += faceCount;
            offset += var21;
            System.out.println("computed var34=" + offset);

            for (int i = 0; i < Math.min(faceCount, 10); i++) {
                int pos = offset + i * 2;
                int value = ((raw[pos] & 0xFF) << 8) | (raw[pos + 1] & 0xFF);
                System.out.println("  face " + i + " raw@ " + pos + " = " + value + " (textureId=" + (value-1) + ")");
            }

            ModelLoader loader = new ModelLoader();
            ModelDefinition def = loader.load(modelId, raw.clone());
            System.out.println("real decoder faceTextures[0..9]=" + java.util.Arrays.toString(
                    java.util.Arrays.copyOf(def.faceTextures, Math.min(10, def.faceTextures.length))));
        }
    }
}

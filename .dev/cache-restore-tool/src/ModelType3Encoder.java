import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.io.OutputStream;

// Encodes an already-decoded ModelDefinition into "Type3" binary model bytes -- the exact inverse
// of RuneLite's ModelLoader.decodeType3(). Type3 is the modern model format this client revision's
// player-equipment-compositing path (id.hl/jj.ap in the obfuscated client) actually accepts;
// Type1/Type2 models load and render fine standalone (ground/inventory/bank icon) but the
// composited-onto-player lookup silently fails for them, which is what was causing the
// jj.ax(short,short) NullPointerException crash under 117HD (and silent invisibility otherwise)
// for the drag-drop-bundle headwear items.
//
// Unlike Type2, Type3 stores faceTextures/textureCoords directly per-face (no 6-bit/64-entry
// texCoord packing limit) -- so this encoder is structurally closer to a straight Type1 rewrite
// than ModelType2Encoder was. The one thing Type3 adds over Type1 is an optional per-vertex
// "animaya" skin-weight block; these are all static, non-skeletal item models, so it's always
// written empty (hasAnimaya=false), which is exactly what a real Type1-sourced model implies.
//
// Simplification vs. the original game encoder: face vertex-index connectivity is ALWAYS encoded
// as connectivity code 1 (three independently-delta-encoded indices) rather than picking the
// most space-efficient strip-sharing code (2/3/4) -- this produces a valid, correctly-decoding
// model that is a little larger than an official client-produced one, never an incorrect one.
public class ModelType3Encoder {

    // Diagnostic escape hatch: when true, faceTextures/texturing is dropped entirely (every face
    // renders with its plain faceColors value).
    public static boolean DISABLE_TEXTURING = false;

    public static byte[] encode(ModelDefinition def) {
        int vertexCount = def.vertexCount;
        int faceCount = def.faceCount;

        boolean hasFacePriorities = def.faceRenderPriorities != null;
        boolean hasFaceTransparencies = def.faceTransparencies != null;
        boolean hasPackedTransparencyVertexGroups = def.packedTransparencyVertexGroups != null;
        boolean hasPackedVertexGroups = def.packedVertexGroups != null;
        boolean hasAnimaya = def.animayaGroups != null;
        boolean hasFaceTextures = !DISABLE_TEXTURING && def.faceTextures != null;

        int var12 = def.faceRenderTypes != null ? 1 : 0;
        int var13 = hasFacePriorities ? 255 : (def.priority & 0xFF);
        int var14 = hasFaceTransparencies ? 1 : 0;
        int var15 = hasPackedTransparencyVertexGroups ? 1 : 0;
        int var16 = hasFaceTextures ? 1 : 0;
        int var17 = hasPackedVertexGroups ? 1 : 0;
        int var18 = hasAnimaya ? 1 : 0;

        // ---- vertex flag bytes + dx/dy/dz delta streams, interleaved with packedVertexGroups ----
        OutputStream vertexFlags = new OutputStream();
        OutputStream dxStream = new OutputStream();
        OutputStream dyStream = new OutputStream();
        OutputStream dzStream = new OutputStream();
        OutputStream vertexGroupsAnimaya = new OutputStream();
        int prevX = 0, prevY = 0, prevZ = 0;
        for (int i = 0; i < vertexCount; i++) {
            int dx = def.vertexX[i] - prevX;
            int dy = def.vertexY[i] - prevY;
            int dz = def.vertexZ[i] - prevZ;
            prevX = def.vertexX[i];
            prevY = def.vertexY[i];
            prevZ = def.vertexZ[i];
            int flag = 0;
            if (dx != 0) flag |= 1;
            if (dy != 0) flag |= 2;
            if (dz != 0) flag |= 4;
            vertexFlags.writeByte(flag);
            if (dx != 0) writeSignedSmart(dxStream, dx);
            if (dy != 0) writeSignedSmart(dyStream, dy);
            if (dz != 0) writeSignedSmart(dzStream, dz);
            if (hasPackedVertexGroups) {
                vertexGroupsAnimaya.writeByte(def.packedVertexGroups[i]);
            }
        }
        if (hasAnimaya) {
            for (int i = 0; i < vertexCount; i++) {
                int[] groups = def.animayaGroups[i];
                int[] scales = def.animayaScales[i];
                int n = groups == null ? 0 : groups.length;
                vertexGroupsAnimaya.writeByte(n);
                for (int j = 0; j < n; j++) {
                    vertexGroupsAnimaya.writeByte(groups[j]);
                    vertexGroupsAnimaya.writeByte(scales[j]);
                }
            }
        }

        // ---- per-face streams ----
        OutputStream faceConnCodes = new OutputStream();      // segment B (always var10 bytes)
        OutputStream facePriorities = new OutputStream();     // segment C (if var13==255)
        OutputStream faceTransVertGroups = new OutputStream();// segment D (if var15)
        OutputStream faceTransparencies = new OutputStream(); // segment F (if var14)
        OutputStream faceRenderTypesOut = new OutputStream(); // segment A (if var12)
        OutputStream faceConnDeltas = new OutputStream();     // segment G (always, variable length)
        OutputStream faceTexturesOut = new OutputStream();    // segment H (if var16, var10*2 bytes)
        OutputStream textureCoordsOut = new OutputStream();   // segment I (variable length)
        OutputStream faceColorsOut = new OutputStream();      // segment J (always, var10*2 bytes)
        int connBase = 0;
        for (int i = 0; i < faceCount; i++) {
            faceConnCodes.writeByte(1); // always the "3 brand-new indices" connectivity code
            int a = def.faceIndices1[i];
            int b = def.faceIndices2[i];
            int c = def.faceIndices3[i];
            writeSignedSmart(faceConnDeltas, a - connBase);
            writeSignedSmart(faceConnDeltas, b - a);
            writeSignedSmart(faceConnDeltas, c - b);
            connBase = c;

            if (var12 == 1) {
                faceRenderTypesOut.writeByte(def.faceRenderTypes[i]);
            }
            if (hasFacePriorities) {
                facePriorities.writeByte(def.faceRenderPriorities[i]);
            }
            if (hasFaceTransparencies) {
                faceTransparencies.writeByte(def.faceTransparencies[i]);
            }
            if (hasPackedTransparencyVertexGroups) {
                faceTransVertGroups.writeByte(def.packedTransparencyVertexGroups[i]);
            }

            faceColorsOut.writeShort(def.faceColors[i] & 0xFFFF);

            if (var16 == 1) {
                short texId = hasFaceTextures ? def.faceTextures[i] : -1;
                faceTexturesOut.writeShort((texId + 1) & 0xFFFF);
                if (texId != -1) {
                    int coord = def.textureCoords != null ? (def.textureCoords[i] & 0xFF) : -1;
                    textureCoordsOut.writeByte((coord + 1) & 0xFF);
                }
            }
        }

        // ---- texture face index triples: texIndices1/2/3 interleaved per texture-render-type-0
        // entry (matches decodeType3's single-stream sequential read of all three arrays) ----
        OutputStream texIndices = new OutputStream();
        int numTextureFaces = def.numTextureFaces;
        if (numTextureFaces > 0 && def.texIndices1 != null) {
            for (int i = 0; i < numTextureFaces; i++) {
                int type = def.textureRenderTypes != null ? (def.textureRenderTypes[i] & 0xFF) : 0;
                if (type == 0) {
                    texIndices.writeShort(def.texIndices1[i] & 0xFFFF);
                    texIndices.writeShort(def.texIndices2[i] & 0xFFFF);
                    texIndices.writeShort(def.texIndices3[i] & 0xFFFF);
                }
            }
        }

        byte[] texRenderTypesB = (numTextureFaces > 0 && def.textureRenderTypes != null)
                ? def.textureRenderTypes : new byte[0];
        byte[] vertexFlagsB = vertexFlags.flip();
        byte[] faceRenderTypesB = faceRenderTypesOut.flip();
        byte[] faceConnCodesB = faceConnCodes.flip();
        byte[] facePrioritiesB = facePriorities.flip();
        byte[] faceTransVertGroupsB = faceTransVertGroups.flip();
        byte[] vertexGroupsAnimayaB = vertexGroupsAnimaya.flip();
        byte[] faceTransparenciesB = faceTransparencies.flip();
        byte[] faceConnDeltasB = faceConnDeltas.flip();
        byte[] faceTexturesB = faceTexturesOut.flip();
        byte[] textureCoordsB = textureCoordsOut.flip();
        byte[] faceColorsB = faceColorsOut.flip();
        byte[] texIndicesB = texIndices.flip();
        byte[] dxB = dxStream.flip();
        byte[] dyB = dyStream.flip();
        byte[] dzB = dzStream.flip();

        int var19 = dxB.length;
        int var20 = dyB.length;
        int var21 = dzB.length;
        int var22 = faceConnDeltasB.length;
        int var23 = textureCoordsB.length;
        int var24 = vertexGroupsAnimayaB.length;

        OutputStream out = new OutputStream();
        out.writeBytes(texRenderTypesB);
        out.writeBytes(vertexFlagsB);
        if (var12 == 1) out.writeBytes(faceRenderTypesB);
        out.writeBytes(faceConnCodesB);
        if (hasFacePriorities) out.writeBytes(facePrioritiesB);
        if (hasPackedTransparencyVertexGroups) out.writeBytes(faceTransVertGroupsB);
        out.writeBytes(vertexGroupsAnimayaB);
        if (hasFaceTransparencies) out.writeBytes(faceTransparenciesB);
        out.writeBytes(faceConnDeltasB);
        if (var16 == 1) out.writeBytes(faceTexturesB);
        out.writeBytes(textureCoordsB);
        out.writeBytes(faceColorsB);
        out.writeBytes(dxB);
        out.writeBytes(dyB);
        out.writeBytes(dzB);
        out.writeBytes(texIndicesB);

        // header (24 bytes) + trailer marker (2 bytes) = 26-byte tail, per decodeType3's
        // `var2.setOffset(var1.length - 26)`.
        out.writeShort(vertexCount);
        out.writeShort(faceCount);
        out.writeByte(numTextureFaces);
        out.writeByte(var12);
        out.writeByte(var13);
        out.writeByte(var14);
        out.writeByte(var15);
        out.writeByte(var16);
        out.writeByte(var17);
        out.writeByte(var18);
        out.writeShort(var19);
        out.writeShort(var20);
        out.writeShort(var21);
        out.writeShort(var22);
        out.writeShort(var23);
        out.writeShort(var24);
        out.writeByte(0xFF); // trailer[-2] == -1
        out.writeByte(0xFD); // trailer[-1] == -3  (Type3 marker)

        return out.flip();
    }

    // Mirrors InputStream.readShortSmart(): 1 byte (value+64) for value in [-64,63],
    // else 2 bytes (value+0xc000) as an unsigned short.
    static void writeSignedSmart(OutputStream out, int value) {
        if (value >= -64 && value <= 63) {
            out.writeByte(value + 64);
        } else {
            out.writeShort(value + 0xc000);
        }
    }
}

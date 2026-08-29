import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.io.OutputStream;

// Encodes an already-decoded ModelDefinition (regardless of which trailer format it was
// originally decoded from -- Type1/Type2/Type3/OldFormat) into "Type2" binary model bytes, i.e.
// the exact inverse of RuneLite's ModelLoader.decodeType2(). Used to upgrade older-format custom
// models (Type1) to the format this server's client actually renders correctly.
//
// Simplification vs. the original game encoder: face vertex-index connectivity is ALWAYS encoded
// as connectivity code 1 (three independently-delta-encoded indices) rather than picking the
// most space-efficient strip-sharing code (2/3/4) -- this produces a valid, correctly-decoding
// model that is a little larger than an official client-produced one, never an incorrect one.
// Per-vertex/per-face optional flag bits are likewise always set to match whatever arrays are
// actually present on the source ModelDefinition (never selectively omitted for size).
public class ModelType2Encoder {

    // Diagnostic escape hatch: when true, faceTextures/texturing is dropped entirely (every face
    // renders with its plain faceColors value) while faceRenderTypes is still preserved. Used to
    // isolate whether the synthesized shared-reference-triangle texCoord table (see texIndices
    // synthesis below) is what's causing real-client invisibility/crashes, independent of
    // everything else this encoder does.
    public static boolean DISABLE_TEXTURING = false;

    public static byte[] encode(ModelDefinition def) {
        int vertexCount = def.vertexCount;
        int faceCount = def.faceCount;
        int numTextureFaces = def.numTextureFaces;

        boolean hasFacePriorities = def.faceRenderPriorities != null;
        boolean hasFaceTransparencies = def.faceTransparencies != null;
        boolean hasPackedTransparencyVertexGroups = def.packedTransparencyVertexGroups != null;
        boolean hasPackedVertexGroups = def.packedVertexGroups != null;
        boolean hasAnimaya = def.animayaGroups != null;
        boolean hasFaceInfo = def.faceRenderTypes != null || def.faceTextures != null;

        int var12 = hasFaceInfo ? 1 : 0;
        int var13 = hasFacePriorities ? 255 : (def.priority & 0xFF);
        int var14 = hasFaceTransparencies ? 1 : 0;
        int var15 = hasPackedTransparencyVertexGroups ? 1 : 0;
        int var16 = hasPackedVertexGroups ? 1 : 0;
        int var17 = hasAnimaya ? 1 : 0;

        // ---- vertex flag bytes + dx/dy/dz delta streams ----
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

        // ---- synthesize a texIndices table for textured faces ----
        // Type2 packs each face's texture-coordinate index into 6 bits (0-63) of the faceInfo
        // byte, so we can't give every face its own reference triangle once faceCount > 64.
        // These source models never carry a real per-face UV override (textureCoords is either
        // null, i.e. Type1, or all -1), so the exact reference triangle chosen doesn't matter for
        // the -1/default UV path -- RuneLite's computeTextureUVCoordinates only special-cases
        // textureCoordinate==-1 with a hardcoded default UV; any face that keeps textureCoords==-1
        // (bit1=0, i.e. not textured at all) is unaffected. For genuinely textured faces we must
        // set bit1=1 (to preserve faceTextures/the texture image), so we give each distinct
        // texture id one shared reference triangle (first face using it) rather than one per face.
        java.util.Map<Short, Integer> textureIdToCoordIndex = new java.util.LinkedHashMap<>();
        java.util.List<int[]> synthTexIndices = new java.util.ArrayList<>();
        if (def.faceTextures != null && !DISABLE_TEXTURING) {
            for (int i = 0; i < faceCount; i++) {
                short texId = def.faceTextures[i];
                if (texId == -1) continue;
                if (!textureIdToCoordIndex.containsKey(texId)) {
                    if (synthTexIndices.size() >= 64) {
                        throw new IllegalStateException("More than 64 distinct texture ids on model "
                                + def.id + " - Type2's 6-bit texCoord field can't represent this");
                    }
                    textureIdToCoordIndex.put(texId, synthTexIndices.size());
                    synthTexIndices.add(new int[]{def.faceIndices1[i], def.faceIndices2[i], def.faceIndices3[i]});
                }
            }
        }

        // ---- per-face streams ----
        OutputStream faceConnCodes = new OutputStream();
        OutputStream facePriorities = new OutputStream();
        OutputStream faceTransVertGroups = new OutputStream();
        OutputStream faceInfo = new OutputStream();
        OutputStream faceTransparencies = new OutputStream();
        OutputStream faceConnDeltas = new OutputStream();
        OutputStream faceColorsOrTex = new OutputStream();
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

            if (hasFacePriorities) {
                facePriorities.writeByte(def.faceRenderPriorities[i]);
            }
            if (hasPackedTransparencyVertexGroups) {
                faceTransVertGroups.writeByte(def.packedTransparencyVertexGroups[i]);
            }
            if (hasFaceInfo) {
                boolean renderTypeBit = def.faceRenderTypes != null && def.faceRenderTypes[i] != 0;
                boolean textured = !DISABLE_TEXTURING && def.faceTextures != null && def.faceTextures[i] != -1;
                int b0 = renderTypeBit ? 1 : 0;
                int colorOrTex;
                if (textured) {
                    int texCoord = textureIdToCoordIndex.get(def.faceTextures[i]);
                    b0 |= 2 | (texCoord << 2);
                    colorOrTex = def.faceTextures[i] & 0xFFFF;
                } else {
                    colorOrTex = def.faceColors[i] & 0xFFFF;
                }
                faceInfo.writeByte(b0);
                faceColorsOrTex.writeShort(colorOrTex);
            } else {
                faceColorsOrTex.writeShort(def.faceColors[i] & 0xFFFF);
            }
            if (hasFaceTransparencies) {
                faceTransparencies.writeByte(def.faceTransparencies[i]);
            }
        }

        // ---- texture face index triples (synthesized, see above) ----
        numTextureFaces = synthTexIndices.size();
        OutputStream texIndices = new OutputStream();
        for (int[] tri : synthTexIndices) {
            texIndices.writeShort(tri[0] & 0xFFFF);
            texIndices.writeShort(tri[1] & 0xFFFF);
            texIndices.writeShort(tri[2] & 0xFFFF);
        }

        byte[] vertexFlagsB = vertexFlags.flip();
        byte[] faceConnCodesB = faceConnCodes.flip();
        byte[] facePrioritiesB = facePriorities.flip();
        byte[] faceTransVertGroupsB = faceTransVertGroups.flip();
        byte[] faceInfoB = faceInfo.flip();
        byte[] vertexGroupsAnimayaB = vertexGroupsAnimaya.flip();
        byte[] faceTransparenciesB = faceTransparencies.flip();
        byte[] faceConnDeltasB = faceConnDeltas.flip();
        byte[] faceColorsOrTexB = faceColorsOrTex.flip();
        byte[] texIndicesB = texIndices.flip();
        byte[] dxB = dxStream.flip();
        byte[] dyB = dyStream.flip();
        byte[] dzB = dzStream.flip();

        int var18 = dxB.length;
        int var19 = dyB.length;
        int var20 = dzB.length;
        int var21 = faceConnDeltasB.length;
        int var22 = vertexGroupsAnimayaB.length;

        OutputStream out = new OutputStream();
        out.writeBytes(vertexFlagsB);
        out.writeBytes(faceConnCodesB);
        if (hasFacePriorities) out.writeBytes(facePrioritiesB);
        if (hasPackedTransparencyVertexGroups) out.writeBytes(faceTransVertGroupsB);
        if (hasFaceInfo) out.writeBytes(faceInfoB);
        out.writeBytes(vertexGroupsAnimayaB);
        if (hasFaceTransparencies) out.writeBytes(faceTransparenciesB);
        out.writeBytes(faceConnDeltasB);
        out.writeBytes(faceColorsOrTexB);
        out.writeBytes(texIndicesB);
        out.writeBytes(dxB);
        out.writeBytes(dyB);
        out.writeBytes(dzB);

        // header (21 bytes) + trailer marker (2 bytes) = 23-byte tail, per decodeType2's
        // `var4.setOffset(var1.length - 23)`.
        out.writeShort(vertexCount);
        out.writeShort(faceCount);
        out.writeByte(numTextureFaces);
        out.writeByte(var12);
        out.writeByte(var13);
        out.writeByte(var14);
        out.writeByte(var15);
        out.writeByte(var16);
        out.writeByte(var17);
        out.writeShort(var18);
        out.writeShort(var19);
        out.writeShort(var20);
        out.writeShort(var21);
        out.writeShort(var22);
        out.writeByte(0xFF); // trailer[-2] == -1
        out.writeByte(0xFE); // trailer[-1] == -2  (Type2 marker)

        return out.flip();
    }

    // Mirrors InputStream.readShortSmart(): 1 byte (value+64) for value in [-64,63],
    // else 2 bytes (value+0xc000) as an unsigned short. NOT the same bias as the library's
    // own writeShortSmart/readUnsignedShortSmart (which are unsigned, +0/+0x8000).
    static void writeSignedSmart(OutputStream out, int value) {
        if (value >= -64 && value <= 63) {
            out.writeByte(value + 64);
        } else {
            out.writeShort(value + 0xc000);
        }
    }
}

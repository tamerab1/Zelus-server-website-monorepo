import net.runelite.cache.definitions.MapDefinition;
import net.runelite.cache.definitions.loaders.MapLoader;
import net.runelite.cache.io.OutputStream;
import java.io.File;
import java.nio.file.Files;

// Transcodes a classic (RSPSi-export) terrain .dat into real modern-cache terrain bytes.
// Format confirmed byte-for-byte equivalent to net.runelite.cache's real MapLoader.loadTerrain()
// (decompiled from the project's own cache library, not guessed) -- same opcode scheme (0=end
// tile, 1=height, 2-49=overlay, 50-81=settings, 82+=underlay), classic just uses 1-byte
// opcodes/fields throughout where modern uses 2-byte (short) ones. Validated 2026-09-21 by
// decoding land.dat with this same opcode table and confirming EXACT match (0/16384 mismatches)
// against the real MapLoader's decode of the already-live, already-transcoded archive 3567.
public class TranscodeClassicTerrainToModern {
    public static byte[] encode(DecodeClassicTerrain.Tile[][][] tiles) {
        OutputStream out = new OutputStream();
        for (int z = 0; z < 4; z++) {
            for (int x = 0; x < 64; x++) {
                for (int y = 0; y < 64; y++) {
                    DecodeClassicTerrain.Tile t = tiles[z][x][y];
                    if (t.attrOpcode != 0) {
                        out.writeShort(t.attrOpcode);
                        out.writeShort(t.overlayId);
                    }
                    if (t.settings != 0) {
                        out.writeShort(t.settings + 49);
                    }
                    if (t.underlayId != 0) {
                        out.writeShort(t.underlayId + 81);
                    }
                    if (t.heightPresent) {
                        out.writeShort(1);
                        out.writeByte(t.height);
                    } else {
                        out.writeShort(0);
                    }
                }
            }
        }
        return out.flip();
    }

    public static void main(String[] args) throws Exception {
        String mode = args[0]; // "verify" (round-trip check only) or "apply" (write output file)
        File classicFile = new File(args[1]);
        File outFile = args.length > 2 ? new File(args[2]) : null;

        byte[] classicBytes = Files.readAllBytes(classicFile.toPath());
        DecodeClassicTerrain.Tile[][][] classicTiles = DecodeClassicTerrain.decode(classicBytes);
        byte[] modernBytes = encode(classicTiles);
        System.out.println("encoded " + modernBytes.length + " modern bytes from " + classicBytes.length + " classic bytes");

        // Round-trip: decode our own encoded output with the REAL MapLoader and diff tile-by-tile
        // against the classic decode -- must be a perfect match before this is trusted.
        MapDefinition modernDef = new MapLoader().load(0, 0, modernBytes);
        MapDefinition.Tile[][][] modernTiles = modernDef.getTiles();
        int mismatches = 0;
        for (int z = 0; z < 4; z++) for (int x = 0; x < 64; x++) for (int y = 0; y < 64; y++) {
            DecodeClassicTerrain.Tile c = classicTiles[z][x][y];
            MapDefinition.Tile m = modernTiles[z][x][y];
            int mHeight = m.height == null ? 0 : m.height;
            boolean ok = c.height == mHeight && c.underlayId == m.underlayId && c.settings == m.settings
                    && c.overlayId == m.overlayId && c.overlayPath == m.overlayPath
                    && c.overlayRotation == m.overlayRotation && (c.attrOpcode != 0) == (m.attrOpcode != 0);
            if (!ok) {
                mismatches++;
                if (mismatches <= 10) {
                    System.out.println("ROUNDTRIP MISMATCH z="+z+" x="+x+" y="+y);
                }
            }
        }
        System.out.println("round-trip check: " + mismatches + " mismatches out of 16384 tiles");
        if (mismatches != 0) {
            throw new IllegalStateException("round-trip validation FAILED -- refusing to write output. " + mismatches + " mismatches.");
        }
        System.out.println("ROUND-TRIP CLEAN -- encoded modern bytes are decode-equivalent to the classic source.");

        if (mode.equals("verify")) {
            System.out.println("VERIFY mode -- not writing output file.");
            return;
        }
        if (!mode.equals("apply")) throw new IllegalArgumentException("mode must be verify or apply");
        Files.write(outFile.toPath(), modernBytes);
        System.out.println("APPLY complete. Wrote " + modernBytes.length + " bytes to " + outFile);
    }
}

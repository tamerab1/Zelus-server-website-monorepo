import net.runelite.cache.io.InputStream;
import java.io.File;
import java.nio.file.Files;

// Reverse-engineered classic (RSPSi-export) terrain format: same opcode scheme as the real
// modern net.runelite.cache.definitions.loaders.MapLoader (0=end tile, 1=height follows,
// 2-49=overlay, 50-81=settings, 82-255=underlay) but with 1-BYTE opcodes/fields throughout
// instead of modern's 2-byte (short) ones -- confirmed by hand-decoding the actual byte stream
// against plausible terrain data (see land.dat hex dump analysis).
public class DecodeClassicTerrain {
    public static class Tile {
        boolean heightPresent = false;
        int height = 0;
        int attrOpcode = 0;
        int overlayId = 0, overlayPath = 0, overlayRotation = 0;
        int settings = 0;
        int underlayId = 0;
    }

    public static Tile[][][] decode(byte[] buf) {
        Tile[][][] tiles = new Tile[4][64][64];
        InputStream in = new InputStream(buf);
        for (int z = 0; z < 4; z++) {
            for (int x = 0; x < 64; x++) {
                for (int y = 0; y < 64; y++) {
                    Tile tile = tiles[z][x][y] = new Tile();
                    while (true) {
                        int opcode = in.readUnsignedByte();
                        if (opcode == 0) break;
                        if (opcode == 1) {
                            tile.height = in.readUnsignedByte();
                            tile.heightPresent = true;
                            break;
                        }
                        if (opcode <= 49) {
                            tile.attrOpcode = opcode;
                            tile.overlayId = in.readUnsignedByte();
                            tile.overlayPath = (opcode - 2) / 4;
                            tile.overlayRotation = (opcode - 2) & 3;
                        } else if (opcode <= 81) {
                            tile.settings = opcode - 49;
                        } else {
                            tile.underlayId = opcode - 81;
                        }
                    }
                }
            }
        }
        System.out.println("consumed " + in.getOffset() + " of " + buf.length + " bytes (leftover=" + (buf.length - in.getOffset()) + ")");
        return tiles;
    }

    public static void main(String[] args) throws Exception {
        byte[] b = Files.readAllBytes(new File(args[0]).toPath());
        Tile[][][] tiles = decode(b);
        int nonDefault = 0;
        for (int z = 0; z < 4; z++) for (int x = 0; x < 64; x++) for (int y = 0; y < 64; y++) {
            Tile t = tiles[z][x][y];
            if (t.height != 0 || t.overlayId != 0 || t.settings != 0 || t.underlayId != 0) nonDefault++;
        }
        System.out.println("non-default tiles: " + nonDefault + " / 16384");
        // print first 10 non-default tiles for manual sanity check
        int shown = 0;
        for (int z = 0; z < 4 && shown < 10; z++) for (int x = 0; x < 64 && shown < 10; x++) for (int y = 0; y < 64 && shown < 10; y++) {
            Tile t = tiles[z][x][y];
            if (t.height != 0 || t.overlayId != 0 || t.settings != 0 || t.underlayId != 0) {
                System.out.println("z="+z+" x="+x+" y="+y+" height="+t.height+" overlay="+t.overlayId+"/"+t.overlayPath+"/"+t.overlayRotation+" settings="+t.settings+" underlay="+t.underlayId);
                shown++;
            }
        }
    }
}

import java.io.File;
import java.nio.file.Files;

// Loads a classic (RSPSi-export) terrain file, sets the tile settings byte on specific tiles, and
// writes MODERN-format bytes (via TranscodeClassicTerrainToModern.encode). Written for region 6477,
// where 2 tiles came out of the editor missing the bridge flag (settings bit 2) that every
// surrounding tile carries, rendering black/unwalkable in-game.
//
// Usage: <classicIn> <modernOut> <regionBaseX> <regionBaseY> <z:absX:absY:settings>...
public class PatchTerrainSettings {
    public static void main(String[] args) throws Exception {
        DecodeClassicTerrain.Tile[][][] tiles = DecodeClassicTerrain.decode(Files.readAllBytes(new File(args[0]).toPath()));
        int baseX = Integer.parseInt(args[2]), baseY = Integer.parseInt(args[3]);
        for (int i = 4; i < args.length; i++) {
            String[] p = args[i].split(":");
            int z = Integer.parseInt(p[0]), x = Integer.parseInt(p[1]) - baseX, y = Integer.parseInt(p[2]) - baseY;
            DecodeClassicTerrain.Tile t = tiles[z][x][y];
            System.out.println("z=" + z + " " + p[1] + "," + p[2] + " settings " + t.settings + " -> " + p[3]);
            t.settings = Integer.parseInt(p[3]);
        }
        Files.write(new File(args[1]).toPath(), TranscodeClassicTerrainToModern.encode(tiles));
        System.out.println("wrote " + args[1]);
    }
}

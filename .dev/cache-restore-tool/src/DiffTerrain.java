import net.runelite.cache.definitions.MapDefinition;
import net.runelite.cache.definitions.loaders.MapLoader;
import java.io.File;
import java.nio.file.Files;

// Diffs two modern-format terrain files tile by tile. Usage: <a.dat> <b.dat> <regionX> <regionY>
public class DiffTerrain {
    public static void main(String[] args) throws Exception {
        int rx = Integer.parseInt(args[2]), ry = Integer.parseInt(args[3]);
        MapDefinition a = new MapLoader().load(rx, ry, Files.readAllBytes(new File(args[0]).toPath()));
        MapDefinition b = new MapLoader().load(rx, ry, Files.readAllBytes(new File(args[1]).toPath()));
        int n = 0;
        for (int z = 0; z < 4; z++) for (int x = 0; x < 64; x++) for (int y = 0; y < 64; y++) {
            String sa = s(a.getTiles()[z][x][y]), sb = s(b.getTiles()[z][x][y]);
            if (!sa.equals(sb)) {
                n++;
                System.out.println("z=" + z + " abs=" + (rx * 64 + x) + "," + (ry * 64 + y) + "  OLD " + sa + "  NEW " + sb);
            }
        }
        System.out.println("changed tiles: " + n);
    }
    static String s(MapDefinition.Tile t) {
        return "h=" + t.height + " ov=" + t.overlayId + "/" + t.overlayPath + "/" + t.overlayRotation + " set=" + t.settings + " un=" + t.underlayId;
    }
}

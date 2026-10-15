import net.runelite.cache.definitions.MapDefinition;
import net.runelite.cache.definitions.loaders.MapLoader;
import java.io.File;
import java.nio.file.Files;

public class CompareClassicVsModernTerrain {
    public static void main(String[] args) throws Exception {
        byte[] classicBytes = Files.readAllBytes(new File(args[0]).toPath());
        byte[] modernBytes = Files.readAllBytes(new File(args[1]).toPath());

        DecodeClassicTerrain.Tile[][][] classic = DecodeClassicTerrain.decode(classicBytes);
        MapDefinition modernDef = new MapLoader().load(0, 0, modernBytes);
        MapDefinition.Tile[][][] modern = modernDef.getTiles();

        int mismatches = 0, checked = 0;
        for (int z = 0; z < 4; z++) for (int x = 0; x < 64; x++) for (int y = 0; y < 64; y++) {
            DecodeClassicTerrain.Tile c = classic[z][x][y];
            MapDefinition.Tile m = modern[z][x][y];
            checked++;
            int mHeight = m.height == null ? 0 : m.height;
            boolean heightMatch = c.height == mHeight;
            boolean underlayMatch = c.underlayId == m.underlayId;
            boolean settingsMatch = c.settings == m.settings;
            boolean overlayIdMatch = c.overlayId == m.overlayId;
            boolean overlayPathMatch = c.overlayPath == m.overlayPath;
            boolean overlayRotMatch = c.overlayRotation == m.overlayRotation;
            boolean overlayPresentMatch = (c.attrOpcode != 0) == (m.attrOpcode != 0);
            if (!heightMatch || !underlayMatch || !settingsMatch || !overlayIdMatch || !overlayPathMatch || !overlayRotMatch || !overlayPresentMatch) {
                mismatches++;
                if (mismatches <= 15) {
                    System.out.println("MISMATCH z="+z+" x="+x+" y="+y
                        + " classic[h="+c.height+",u="+c.underlayId+",s="+c.settings+",oId="+c.overlayId+",oP="+c.overlayPath+",oR="+c.overlayRotation+",attrOp="+c.attrOpcode+"]"
                        + " modern[h="+mHeight+",u="+m.underlayId+",s="+m.settings+",oId="+m.overlayId+",oP="+m.overlayPath+",oR="+m.overlayRotation+",attrOp="+m.attrOpcode+"]");
                }
            }
        }
        System.out.println("checked=" + checked + " mismatches=" + mismatches);

        // Separately: print classic overlayId vs modern overlayId for all tiles that have an overlay,
        // to figure out the id mapping (identity or not).
        System.out.println("--- overlay id mapping sample ---");
        int shown = 0;
        for (int z = 0; z < 4 && shown < 20; z++) for (int x = 0; x < 64 && shown < 20; x++) for (int y = 0; y < 64 && shown < 20; y++) {
            DecodeClassicTerrain.Tile c = classic[z][x][y];
            MapDefinition.Tile m = modern[z][x][y];
            if (c.overlayId != 0) {
                System.out.println("z="+z+" x="+x+" y="+y+" classicOverlayId="+c.overlayId+" modernOverlayId="+m.overlayId+" modernAttrOp="+m.attrOpcode);
                shown++;
            }
        }
    }
}

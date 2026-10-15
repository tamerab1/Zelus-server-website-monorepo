import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Store;
import net.runelite.cache.definitions.SpriteDefinition;
import net.runelite.cache.definitions.loaders.SpriteLoader;
import java.io.File;

// Read-only: for a font sprite group, prints the glyph size for the given character codes
// (cp1252 index) so we know which symbols the in-game font can actually draw.
// Usage: <cachePath> <fontSpriteId> <code>[,<code>...]
public class FontGlyphCheck {
    public static void main(String[] args) throws Exception {
        try (Store store = new Store(new File(args[0]))) {
            store.load();
            Archive a = store.getIndex(IndexType.SPRITES).getArchive(Integer.parseInt(args[1]));
            SpriteDefinition[] glyphs = new SpriteLoader().load(a.getArchiveId(), a.decompress(store.getStorage().loadArchive(a)));
            System.out.println("font " + args[1] + " glyphs=" + glyphs.length);
            for (String s : args[2].split(",")) {
                int c = Integer.parseInt(s);
                SpriteDefinition g = glyphs[c];
                int lit = 0;
                if (g.getPixelIdx() != null) for (byte p : g.getPixelIdx()) if (p != 0) lit++;
                System.out.println("char " + c + ": " + g.getWidth() + "x" + g.getHeight() + " litPixels=" + lit);
            }
        }
    }
}

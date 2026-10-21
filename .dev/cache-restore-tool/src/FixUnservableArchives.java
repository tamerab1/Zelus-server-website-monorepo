import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.SpriteDefinition;
import net.runelite.cache.definitions.loaders.SpriteLoader;
import net.runelite.cache.fs.*;

import java.awt.image.BufferedImage;
import java.io.File;

// Archives the JS5 server could never answer (2026-10-21, players stuck loading / full cache downloads stalling): the
// prod log showed "Invalid Sector! index=255 archiveId=16" and "index=8 archiveId=24464 / 24465". The server drops a
// request it has no data for and the client waits on it forever (and re-asks after every js5 reconnect).
//   - sprites 24464 / 24465: listed in the sprite index (crc, revision 1) but no data was ever stored -> a valid 1x1
//     transparent sprite (nothing references them: no texture or interface uses either id);
//   - sprites 17014 / 17015 (1,026,075 bytes): over the CLIENT's 1,000,000-byte store limit -> scaled down (see below).
// Index 16's missing reference table is deliberately left alone (see below). Only archives that are missing / empty are touched. Usage: verify|apply <cachePath>
public class FixUnservableArchives {
    static final int[] EMPTY_SPRITES = {24464, 24465}, OVERSIZED_SPRITES = {17014, 17015};
    /** The client refuses to store an archive bigger than this. */
    static final int CLIENT_MAX = 1_000_000;

    public static void main(String[] args) throws Exception {
        boolean apply = args[0].equals("apply");
        try (Store store = new Store(new File(args[1]))) {
            store.load();
            Storage storage = store.getStorage();
            Index sprites = store.getIndex(IndexType.SPRITES);
            boolean spritesChanged = false, spritesChanged2 = false;
            for (int id : EMPTY_SPRITES) {
                Archive a = sprites.getArchive(id);
                if (a == null) { System.out.println("sprite " + id + ": not listed, nothing to do"); continue; }
                byte[] stored = storage.loadArchive(a);
                if (stored != null && stored.length > 0) { System.out.println("sprite " + id + ": has data (" + stored.length + " bytes), left alone"); continue; }
                BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
                byte[] raw = AddNewContent.encodeSprite(img);
                SpriteDefinition[] back = new SpriteLoader().load(id, raw);
                BuildSanctumMap.check(back.length == 1 && back[0].getWidth() == 1 && back[0].getHeight() == 1, "sprite " + id + " round trip");
                Container c = new Container(a.getCompression(), -1);
                c.compress(raw, null);
                System.out.println("sprite " + id + ": empty -> 1x1 transparent (" + c.data.length + " bytes)");
                if (!apply) continue;
                storage.store(sprites.getId(), id, c.data);
                a.setCrc(c.crc);
                a.setRevision(a.getRevision() + 1);
                a.setCompressedSize(c.data.length);
                a.setDecompressedSize(raw.length);
                spritesChanged = true;
            }
            if (spritesChanged)
                ImportCathedralMap.writeIndexReferenceTable(storage, sprites);

            // Oversized sprites: the client refuses to store any archive over 1,000,000 bytes (IllegalArgumentException
            // "8,17014,1026075" in its cache thread), so raising only the server's cap was not enough. 17014 / 17015 are
            // a 2048x2104 Zelus "Z" logo (fake checkerboard baked in) that no texture or interface uses: scaled to 512 wide.
            for (int id : OVERSIZED_SPRITES) {
                Archive a = sprites.getArchive(id);
                if (a == null) continue;
                byte[] stored = storage.loadArchive(a);
                if (stored == null || stored.length <= CLIENT_MAX) { System.out.println("sprite " + id + ": " + (stored == null ? "missing" : stored.length + " bytes") + ", left alone"); continue; }
                SpriteDefinition src = new SpriteLoader().load(id, a.decompress(stored))[0];
                BufferedImage full = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
                full.setRGB(0, 0, src.getWidth(), src.getHeight(), src.getPixels(), 0, src.getWidth());
                int w = 512, h = (int) Math.round(src.getHeight() * 512.0 / src.getWidth());
                BufferedImage small = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                java.awt.Graphics2D g = small.createGraphics();
                g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR); // keeps the source palette (sprites hold <= 255 colours)
                g.drawImage(full, 0, 0, w, h, null);
                g.dispose();
                byte[] raw = AddNewContent.encodeSprite(small);
                SpriteDefinition[] back = new SpriteLoader().load(id, raw);
                BuildSanctumMap.check(back.length == 1 && back[0].getWidth() == w && back[0].getHeight() == h, "sprite " + id + " round trip");
                Container c = new Container(a.getCompression(), -1);
                c.compress(raw, null);
                BuildSanctumMap.check(c.data.length <= CLIENT_MAX, "sprite " + id + " still " + c.data.length + " bytes");
                System.out.println("sprite " + id + ": " + stored.length + " bytes (" + src.getWidth() + "x" + src.getHeight() + ") -> "
                    + c.data.length + " bytes (" + w + "x" + h + ")");
                if (!apply) continue;
                storage.store(sprites.getId(), id, c.data);
                a.setCrc(c.crc);
                a.setRevision(a.getRevision() + 1);
                a.setCompressedSize(c.data.length);
                a.setDecompressedSize(raw.length);
                spritesChanged2 = true;
            }
            if (spritesChanged2)
                ImportCathedralMap.writeIndexReferenceTable(storage, sprites);
            // NOT index 16 (reverted 2026-10-21): this client has no index 16, so it logs in with crc 0 for it; giving 16 a
            // reference table changed the server's master index and every login got "RuneScape has been updated!".
            if (!apply) { System.out.println("VERIFY OK -- nothing written."); return; }
            System.out.println("APPLY complete.");
        }
    }
}

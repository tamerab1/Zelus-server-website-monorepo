import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.SpriteDefinition;
import net.runelite.cache.definitions.loaders.SpriteLoader;
import net.runelite.cache.fs.*;

import java.io.File;
import java.util.Arrays;

// js5crc crash, 2026-10-21 (v1.1.109): JS5 requests carry a 16-bit group id (rsprot UrgentRequestDecoder: g1 archive,
// g2 group), so the client's request for sprite 90000 / 90001 (bank-preset backgrounds: interfaces 1142, 1156, 1157,
// 5900 component 86) reaches the server as sprite 24464 / 24465 (id & 0xffff). Those two slots were listed-but-empty,
// so the request was dropped and the background never drew. FixUnservableArchives then filled them with a 1x1 sprite,
// which the client checked against sprite 90000's crc -> 4 mismatches -> error_game_js5crc on opening a preset screen.
// Fix: store the EXACT container bytes of 90000 / 90001 in 24464 / 24465 with the same crc / revision / sizes, so the
// wrapped request receives precisely the bytes the client expects. Usage: verify|apply <cachePath>
public class FixWrappedSpriteIds {
    static final int[][] WRAPPED = {{90000, 24464}, {90001, 24465}};

    public static void main(String[] args) throws Exception {
        boolean apply = args[0].equals("apply");
        try (Store store = new Store(new File(args[1]))) {
            store.load();
            Storage storage = store.getStorage();
            Index sprites = store.getIndex(IndexType.SPRITES);
            boolean changed = false;
            for (int[] pair : WRAPPED) {
                int real = pair[0], slot = pair[1];
                BuildSanctumMap.check((real & 0xffff) == slot, real + " does not wrap to " + slot);
                Archive src = sprites.getArchive(real), dst = sprites.getArchive(slot);
                BuildSanctumMap.check(src != null && dst != null, "sprite " + real + " or " + slot + " not listed");
                byte[] bytes = storage.loadArchive(src);
                BuildSanctumMap.check(bytes != null && bytes.length > 0, "sprite " + real + " has no data");
                SpriteDefinition[] img = new SpriteLoader().load(real, src.decompress(bytes));
                System.out.println("sprite " + real + " (" + img[0].getWidth() + "x" + img[0].getHeight() + ", " + bytes.length
                    + " bytes, crc " + Integer.toHexString(src.getCrc()) + ") -> slot " + slot + " (crc was " + Integer.toHexString(dst.getCrc()) + ")");
                if (!apply) continue;
                storage.store(sprites.getId(), slot, bytes.clone());
                dst.setCrc(src.getCrc());
                dst.setRevision(src.getRevision());
                dst.setCompressedSize(src.getCompressedSize());
                dst.setDecompressedSize(src.getDecompressedSize());
                changed = true;
            }
            if (!apply) { System.out.println("VERIFY OK -- nothing written."); return; }
            if (changed)
                ImportCathedralMap.writeIndexReferenceTable(storage, sprites);
        }
        // Re-open from disk and confirm each slot now holds byte-identical data and the same crc as its real sprite.
        try (Store store = new Store(new File(args[1]))) {
            store.load();
            Index sprites = store.getIndex(IndexType.SPRITES);
            for (int[] pair : WRAPPED) {
                Archive src = sprites.getArchive(pair[0]), dst = sprites.getArchive(pair[1]);
                byte[] a = store.getStorage().loadArchive(src), b = store.getStorage().loadArchive(dst);
                BuildSanctumMap.check(Arrays.equals(a, b) && src.getCrc() == dst.getCrc(), "slot " + pair[1] + " does not match " + pair[0]);
            }
        }
        System.out.println("APPLY complete, re-read verified.");
    }
}

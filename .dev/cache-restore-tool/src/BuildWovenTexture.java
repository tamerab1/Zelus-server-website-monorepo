import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.SpriteDefinition;
import net.runelite.cache.definitions.TextureDefinition;
import net.runelite.cache.definitions.loaders.SpriteLoader;
import net.runelite.cache.definitions.loaders.TextureLoader;
import net.runelite.cache.fs.*;
import net.runelite.cache.index.FileData;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import java.util.List;

// The Rivals Challenger cape's own texture (2026-10-15 r6): a painted, seamlessly tiling 128 x 128 woven cloth --
// obsidian base, a crimson basket weave with shaded over/under threads, and sparse gold threads -- instead of the
// Fire cape's lava. Stored as a NEW sprite + texture (appended: the texture must be the next free id), animated by the
// texture's own scroll (direction / speed), so the weave slowly shimmers across the cape like silk.
// Sprites: encoded with BuildBrandIcons.encode after the board builder's 254-colour quantize, round-trip checked.
// Usage: verify|apply <cachePath> <textureId> <spriteId> <direction> <speed> <previewPng>
public class BuildWovenTexture {
    static final int N = 128, CELL = 16; // 8 x 8 weave cells: tiles seamlessly

    public static void main(String[] args) throws Exception {
        boolean apply = args[0].equals("apply");
        int texId = Integer.parseInt(args[2]), spriteId = Integer.parseInt(args[3]);
        int dir = Integer.parseInt(args[4]), speed = Integer.parseInt(args[5]);
        BufferedImage img = paint();
        int[] q = BuildChampionshipBoard.quantize(img.getRGB(0, 0, N, N, null, 0, N));
        BufferedImage out = new BufferedImage(N, N, BufferedImage.TYPE_INT_RGB);
        out.setRGB(0, 0, N, N, q, 0, N);
        BufferedImage big = new BufferedImage(N * 3, N * 3, BufferedImage.TYPE_INT_RGB); // 3 x 3 tiles: seam check
        Graphics2D bg = big.createGraphics();
        for (int y = 0; y < 3; y++)
            for (int x = 0; x < 3; x++)
                bg.drawImage(out, x * N, y * N, null);
        bg.dispose();
        ImageIO.write(big, "png", new File(args[6]));

        try (Store store = new Store(new File(args[1]))) {
            store.load();
            Storage storage = store.getStorage();
            Index textures = store.getIndex(IndexType.TEXTURES), sprites = store.getIndex(IndexType.SPRITES);
            Archive texArchive = textures.getArchive(0);
            byte[] texDec = texArchive.decompress(storage.loadArchive(texArchive));
            FileData[] texFd = texArchive.getFileData();
            List<byte[]> texContents = SpliceItemOption.splitChunks(texDec, texFd.length);
            int maxTex = 0;
            for (FileData f : texFd) maxTex = Math.max(maxTex, f.getId());
            BuildChampionshipBoard.check(maxTex + 1 == texId, "texture " + texId + " is not the next free id (" + (maxTex + 1) + ")");
            BuildChampionshipBoard.check(sprites.getArchive(spriteId) == null, "sprite " + spriteId + " already exists");
            byte[] template = null;
            for (int i = 0; i < texFd.length && template == null; i++) {
                byte[] b = texContents.get(i);
                if (b.length == 12 && (b[3] & 0xff) == 1)
                    template = b;
            }
            BuildChampionshipBoard.check(template != null, "no single-sprite texture to copy");

            byte[] enc = BuildBrandIcons.encode(q, N, N);
            SpriteDefinition back = new SpriteLoader().load(spriteId, enc)[0];
            BuildChampionshipBoard.check(back.getWidth() == N && back.getHeight() == N, "sprite size");
            int[] got = back.getPixels();
            long sr = 0, sg = 0, sb = 0;
            for (int i = 0; i < got.length; i++) {
                BuildChampionshipBoard.check((got[i] & 0xffffff) == q[i], "sprite pixel " + i + " does not round-trip");
                sr += q[i] >> 16 & 0xff;
                sg += q[i] >> 8 & 0xff;
                sb += q[i] & 0xff;
            }
            int avg = (int) (sr / q.length) << 16 | (int) (sg / q.length) << 8 | (int) (sb / q.length);
            int avgHsl = CreateRecoloredTextures.rgbToHsl16(avg);
            byte[] tb = template.clone();
            tb[0] = (byte) (avgHsl >> 8);
            tb[1] = (byte) avgHsl;
            tb[4] = (byte) (spriteId >> 8);
            tb[5] = (byte) spriteId;
            tb[10] = (byte) dir;
            tb[11] = (byte) speed;
            TextureDefinition td = new TextureLoader().load(texId, tb);
            BuildChampionshipBoard.check(Arrays.equals(td.getFileIds(), new int[]{spriteId}) && td.animationDirection == dir
                && td.animationSpeed == speed, "texture re-decode");
            System.out.printf("texture %d sprite %d avg #%06x dir %d speed %d (%d colours)%n", texId, spriteId, avg, dir, speed,
                Arrays.stream(q).distinct().count());
            if (!apply) {
                System.out.println("VERIFY OK -- preview " + args[6] + ", nothing written.");
                return;
            }
            BuildChampionshipBoard.storeArchive(storage, sprites, spriteId, sprites.getArchive(1043).getCompression(), enc);
            BuildChampionshipBoard.storeIndex(storage, sprites);
            List<FileData> fdList = new ArrayList<>(Arrays.asList(texFd));
            FileData fd = new FileData();
            fd.setId(texId);
            fd.setNameHash(-1);
            fdList.add(fd);
            texContents.add(tb);
            texArchive.setFileData(fdList.toArray(new FileData[0]));
            BuildChampionshipBoard.rewrite(storage, textures, texArchive, SpliceItemOption.joinChunks(texContents));
            BuildChampionshipBoard.storeIndex(storage, textures);
            System.out.println("APPLY complete -- sprite " + spriteId + ", texture " + texId + ".");
        }
    }

    /** Basket weave: each cell holds a crimson thread band, horizontal or vertical in a checkerboard (over / under). */
    static BufferedImage paint() {
        BufferedImage img = new BufferedImage(N, N, BufferedImage.TYPE_INT_RGB);
        Random rnd = new Random(31780);
        for (int y = 0; y < N; y++)
            for (int x = 0; x < N; x++) {
                int cx = x / CELL, cy = y / CELL, lx = x % CELL, ly = y % CELL;
                boolean horizontal = ((cx + cy) & 1) == 0;
                // position across the band (0 edge .. 1 centre) and along it
                int across = horizontal ? ly : lx, along = horizontal ? lx : ly;
                double t = 1 - Math.abs((across + 0.5) / CELL * 2 - 1);  // 0 at the cell edges, 1 in the middle
                double r, g, b;
                if (t < 0.26) {                                         // gap between bands: obsidian
                    r = 10; g = 6; b = 10;
                } else {
                    double shade = 0.45 + 0.55 * Math.sqrt((t - 0.26) / 0.74);     // rounded thread
                    double dip = 0.8 + 0.2 * Math.sin(Math.PI * (along + 0.5) / CELL); // dips under the crossing band
                    double fibre = 0.9 + 0.1 * Math.sin((along * 2.1 + across * 0.7));  // fine fibre lines
                    double k = shade * dip * fibre;
                    r = 104 * k + 12; g = 12 * k + 3; b = 22 * k + 8;   // deep, dark crimson
                }
                img.setRGB(x, y, rgb(r, g, b));
            }
        // sparse gold threads along some band centres (whole cells, so the tile still repeats seamlessly)
        for (int cy = 0; cy < N / CELL; cy++)
            for (int cx = 0; cx < N / CELL; cx++) {
                if (rnd.nextInt(7) != 0) continue;
                boolean horizontal = ((cx + cy) & 1) == 0;
                for (int k = 2; k < CELL - 2; k++) {
                    int x = cx * CELL + (horizontal ? k : CELL / 2), y = cy * CELL + (horizontal ? CELL / 2 : k);
                    double glint = 0.75 + 0.25 * Math.sin(Math.PI * k / CELL);
                    img.setRGB(x, y, rgb(232 * glint, 176 * glint, 64 * glint));
                }
            }
        return img;
    }

    static int rgb(double r, double g, double b) {
        return (int) Math.max(0, Math.min(255, r)) << 16 | (int) Math.max(0, Math.min(255, g)) << 8 | (int) Math.max(0, Math.min(255, b));
    }
}

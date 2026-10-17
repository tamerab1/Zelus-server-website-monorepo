import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.ObjectDefinition;
import net.runelite.cache.definitions.SpriteDefinition;
import net.runelite.cache.definitions.TextureDefinition;
import net.runelite.cache.definitions.loaders.ModelLoader;
import net.runelite.cache.definitions.loaders.ObjectLoader;
import net.runelite.cache.definitions.loaders.SpriteLoader;
import net.runelite.cache.definitions.loaders.TextureLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.GeneralPath;
import java.awt.RadialGradientPaint;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import java.util.List;

// Division Rivals Arena Rug (2026-10-17, the user's mock-up Desktop/new.jpg): a round, 7-tile royal-blue rug with a
// navy hem, a gold ring and a thin inner gold ring, "Division / Rivals" in gold with wings on both sides and a gold
// V-chevron, centred in the Rivals lobby's clearing.
//   - Face painted with Java2D at 3x, resampled to 512x512 and split into a 4x4 grid of 128x128 textures (16 sprites,
//     16 non-animated textures appended above the current max). Each cell is the board's P/M/N mapping (P = top-left,
//     M = top-right, N = bottom-left of the cell); the cell square is clipped to a 24-sided circle and fan-triangulated,
//     every piece keeping its cell's texture triangle, so the edge is round and the art never stretches.
//   - Orientation (user, 2026-10-17): the logo faces SOUTH: image top = north (+z), image right = east (+x), so the text
//     reads upright from the south path with the default north-facing camera. Baked into the geometry: spawn direction 0.
//   - ONE PIECE PER TILE (2026-10-17): a floor decoration is drawn together with its own tile, so a single 7-tile model
//     anchored on the centre tile was painted over by the sand of every tile nearer the camera (staircase cut-offs while
//     rotating). The rug is cut along the tile grid instead: each covered tile gets its own model (cell x tile x circle,
//     every piece keeping its cell's texture triangle, so the art is seamless) and its own object, spawned as type 22 on
//     that tile. Players always draw over floor decorations, and each piece replaces the pebble decor on its tile.
//   - Objects = clones of the vanilla Rug 6761 (floor decoration type 22, non-solid opcode 17), renamed, the "Remove"
//     action dropped, the model list pointed at the piece, plus contoured ground (opcode 21) so they follow the floor.
//     The pieces float 10 units above the ground (vanilla rugs use 8).
// Usage: verify|apply <cachePath> <previewDir> <firstObjectId> <firstModelId> <firstSpriteId> <centreX> <centreY>
//   writes <previewDir>/rug_spawns.json (the type-22 spawn lines for custom_objects.json)
public class BuildRivalsRug {

    static final int GRID = 4, TILE_PX = 128, SIDE = GRID * TILE_PX, SCALE = 3, HALF = 448, LIFT = -10, SIDES = 24;
    static final int TEMPLATE_OBJECT = 6761;
    static final String NAME = "Division Rivals Rug";

    public static void main(String[] args) throws Exception {
        boolean apply = args[0].equals("apply");
        File previewDir = new File(args[2]);
        previewDir.mkdirs();
        int firstObject = Integer.parseInt(args[3]), firstModel = Integer.parseInt(args[4]), firstSprite = Integer.parseInt(args[5]);
        int centreX = Integer.parseInt(args[6]), centreY = Integer.parseInt(args[7]);
        try (Store store = new Store(new File(args[1]))) {
            store.load();
            Storage storage = store.getStorage();
            Index textures = store.getIndex(IndexType.TEXTURES), sprites = store.getIndex(IndexType.SPRITES);
            Index models = store.getIndex(IndexType.MODELS), configs = store.getIndex(IndexType.CONFIGS);

            // ---- face -> 9 tiles ----
            BufferedImage face = paintFace();
            ImageIO.write(face, "png", new File(previewDir, "rug_face_4x.png"));
            Image scaled = face.getScaledInstance(SIDE, SIDE, Image.SCALE_AREA_AVERAGING);
            BufferedImage small = new BufferedImage(SIDE, SIDE, BufferedImage.TYPE_INT_RGB);
            Graphics2D sg = small.createGraphics();
            sg.drawImage(scaled, 0, 0, null);
            sg.dispose();
            ImageIO.write(small, "png", new File(previewDir, "rug_face_ingame_res.png"));

            // ---- textures (template: a non-animated single-sprite texture) ----
            Archive texArchive = textures.getArchive(0);
            byte[] texDec = texArchive.decompress(storage.loadArchive(texArchive));
            FileData[] texFd = texArchive.getFileData();
            List<byte[]> texContents = SpliceItemOption.splitChunks(texDec, texFd.length);
            int maxTex = 0;
            for (FileData f : texFd) maxTex = Math.max(maxTex, f.getId());
            byte[] template = null;
            for (int i = 0; i < texFd.length && template == null; i++) {
                byte[] b = texContents.get(i);
                if (b.length == 12 && (b[3] & 0xff) == 1 && new TextureLoader().load(texFd[i].getId(), b).animationSpeed == 0)
                    template = b;
            }
            BuildChampionshipBoard.check(template != null, "no non-animated single-sprite texture to copy");
            int n = GRID * GRID;
            int[] texIds = new int[n];
            List<byte[]> spriteBytes = new ArrayList<>(), textureBytes = new ArrayList<>();
            Map<Integer, int[]> texPixels = new HashMap<>();
            int spriteCompression = sprites.getArchive(1043).getCompression();
            for (int t = 0; t < n; t++) {
                texIds[t] = maxTex + 1 + t;
                int spriteId = firstSprite + t;
                BuildChampionshipBoard.check(sprites.getArchive(spriteId) == null, "sprite " + spriteId + " already exists");
                int ox = (t % GRID) * TILE_PX, oy = (t / GRID) * TILE_PX;
                int[] q = BuildChampionshipBoard.quantize(small.getRGB(ox, oy, TILE_PX, TILE_PX, null, 0, TILE_PX));
                byte[] enc = BuildBrandIcons.encode(q, TILE_PX, TILE_PX);
                SpriteDefinition back = new SpriteLoader().load(spriteId, enc)[0];
                BuildChampionshipBoard.check(back.getWidth() == TILE_PX && back.getHeight() == TILE_PX, "sprite size");
                int[] got = back.getPixels();
                long sr = 0, sgr = 0, sb = 0;
                for (int i = 0; i < got.length; i++) {
                    BuildChampionshipBoard.check((got[i] & 0xffffff) == q[i], "sprite " + spriteId + " pixel " + i + " round-trip");
                    sr += q[i] >> 16 & 0xff;
                    sgr += q[i] >> 8 & 0xff;
                    sb += q[i] & 0xff;
                }
                int avg = (int) (sr / q.length) << 16 | (int) (sgr / q.length) << 8 | (int) (sb / q.length);
                int avgHsl = CreateRecoloredTextures.rgbToHsl16(avg);
                byte[] tb = template.clone();
                tb[0] = (byte) (avgHsl >> 8);
                tb[1] = (byte) avgHsl;
                tb[4] = (byte) (spriteId >> 8);
                tb[5] = (byte) spriteId;
                tb[10] = 0;
                tb[11] = 0;
                TextureDefinition td = new TextureLoader().load(texIds[t], tb);
                BuildChampionshipBoard.check(Arrays.equals(td.getFileIds(), new int[]{spriteId}) && td.animationSpeed == 0,
                        "texture " + texIds[t] + " re-decode");
                spriteBytes.add(enc);
                textureBytes.add(tb);
                texPixels.put(texIds[t], q);
            }

            // ---- the whole rug (for the previews only), then one piece per tile ----
            buildPiece(firstModel, texIds, null, 0, 0);
            ModelDefinition whole = new ModelLoader().load(firstModel, ModelType3Encoder.encode(BuildChampionshipBoard.toDefinition()));
            for (double[] view : new double[][]{{0, -89}, {0, -40}, {35, -35}})
                ImageIO.write(BuildChampionshipBoard.preview(whole, texPixels, view[0], view[1]), "png",
                        new File(previewDir, String.format("rug_preview_%+d_%+d.png", (int) view[0], (int) view[1])));
            List<int[]> pieces = new ArrayList<>();      // {dx, dy}
            List<byte[]> pieceModels = new ArrayList<>();
            int reach = (HALF + 63) / 128;
            int faces = 0;
            for (int dy = reach; dy >= -reach; dy--)
                for (int dx = -reach; dx <= reach; dx++) {
                    // the tile in image space (u = x, v = -z relative to the rug centre)
                    double tu0 = dx * 128 - 64, tu1 = dx * 128 + 64, tv0 = -(dy * 128 + 64), tv1 = -(dy * 128 - 64);
                    double[][] tile = {{tu0, tv0}, {tu1, tv0}, {tu1, tv1}, {tu0, tv1}};
                    int modelId = firstModel + pieces.size();
                    if (!buildPiece(modelId, texIds, tile, dx, dy))
                        continue;
                    ModelDefinition def = BuildChampionshipBoard.toDefinition();
                    byte[] bytes = ModelType3Encoder.encode(def);
                    ModelDefinition back = new ModelLoader().load(modelId, bytes);
                    BuildChampionshipBoard.check(back.vertexCount == def.vertexCount && back.faceCount == def.faceCount
                            && back.numTextureFaces == def.numTextureFaces, "model " + modelId + " counts did not round-trip");
                    for (int i = 0; i < def.faceCount; i++)
                        BuildChampionshipBoard.check(back.faceTextures != null && back.faceTextures[i] == def.faceTextures[i]
                                && (back.textureCoords[i] & 0xff) == (def.textureCoords[i] & 0xff), "model " + modelId + " face " + i);
                    BuildChampionshipBoard.check(models.getArchive(modelId) == null, "model " + modelId + " already exists");
                    pieces.add(new int[]{dx, dy});
                    pieceModels.add(bytes);
                    faces += def.faceCount;
                }
            System.out.printf("%d pieces (models %d-%d, %d faces); textures %d-%d, sprites %d-%d%n", pieces.size(), firstModel,
                    firstModel + pieces.size() - 1, faces, texIds[0], texIds[n - 1], firstSprite, firstSprite + n - 1);

            // ---- objects: clones of Rug 6761, one per piece ----
            Archive objArchive = configs.getArchive(ConfigType.OBJECT.getId());
            byte[] objDec = objArchive.decompress(storage.loadArchive(objArchive));
            FileData[] objFd = objArchive.getFileData();
            List<byte[]> objContents = SpliceItemOption.splitChunks(objDec, objFd.length);
            ObjectLoader loader = new ObjectLoader();
            int lastObject = firstObject + pieces.size() - 1;
            BuildChampionshipBoard.check(lastObject <= 65535, "spawned object ids must fit 16 bits");
            int src = -1;
            for (int slot = 0; slot < objFd.length; slot++) {
                int id = objFd[slot].getId();
                BuildChampionshipBoard.check(id < firstObject || id > lastObject, "object " + id + " already exists");
                if (id == TEMPLATE_OBJECT && src == -1) src = slot;
            }
            BuildChampionshipBoard.check(src != -1, "template object " + TEMPLATE_OBJECT + " not found");
            ObjectDefinition before = loader.load(TEMPLATE_OBJECT, objContents.get(src));
            List<byte[]> objBytes = new ArrayList<>();
            StringBuilder spawns = new StringBuilder();
            for (int i = 0; i < pieces.size(); i++) {
                int objectId = firstObject + i, modelId = firstModel + i;
                byte[] work = objContents.get(src);
                for (int op = 34; op >= 30; op--)
                    work = SpliceNewObject.removeOpcode(work, op);
                work = SpliceNewObject.replaceOpcode(work, 2, SpliceNewObject.withTerminator(NAME));
                work = SpliceNewObject.replaceOpcode(work, 1, new byte[]{1, (byte) (modelId >> 8), (byte) modelId, 22});
                work = SpliceNewObject.replaceOpcode(work, 21, new byte[0]); // contoured ground: follow the floor
                ObjectDefinition after = loader.load(objectId, work);
                BuildChampionshipBoard.check(NAME.equals(after.getName()) && Arrays.equals(after.getObjectModels(), new int[]{modelId})
                        && Arrays.equals(after.getObjectTypes(), new int[]{22}) && Arrays.stream(after.getActions()).allMatch(Objects::isNull)
                        && after.getSizeX() == 1 && after.getSizeY() == 1 && after.getInteractType() == before.getInteractType()
                        && after.getWallOrDoor() == before.getWallOrDoor(), "object " + objectId + " clone");
                objBytes.add(work);
                spawns.append(String.format("\t{\"id\": %d, \"x\": %d, \"y\": %d, \"z\": 0, \"type\": 22, \"direction\": 0},%n",
                        objectId, centreX + pieces.get(i)[0], centreY + pieces.get(i)[1]));
            }
            java.nio.file.Files.writeString(new File(previewDir, "rug_spawns.json").toPath(), spawns.toString());
            System.out.printf("objects %d-%d \"%s\" (type 22, no actions, contoured), spawns -> %s%n", firstObject, lastObject, NAME,
                    new File(previewDir, "rug_spawns.json"));

            if (!apply) {
                System.out.println("VERIFY OK -- previews in " + previewDir + ", nothing written.");
                return;
            }
            for (int t = 0; t < n; t++)
                BuildChampionshipBoard.storeArchive(storage, sprites, firstSprite + t, spriteCompression, spriteBytes.get(t));
            BuildChampionshipBoard.storeIndex(storage, sprites);
            List<FileData> fdList = new ArrayList<>(Arrays.asList(texFd));
            for (int t = 0; t < n; t++) {
                FileData fd = new FileData();
                fd.setId(texIds[t]);
                fd.setNameHash(-1);
                fdList.add(fd);
                texContents.add(textureBytes.get(t));
            }
            texArchive.setFileData(fdList.toArray(new FileData[0]));
            BuildChampionshipBoard.rewrite(storage, textures, texArchive, SpliceItemOption.joinChunks(texContents));
            BuildChampionshipBoard.storeIndex(storage, textures);
            int compression = models.getArchive(3293).getCompression();
            for (int i = 0; i < pieces.size(); i++)
                BuildChampionshipBoard.storeArchive(storage, models, firstModel + i, compression, pieceModels.get(i));
            BuildChampionshipBoard.storeIndex(storage, models);
            List<FileData> ofd = new ArrayList<>(Arrays.asList(objFd));
            int at = 0;
            while (at < ofd.size() && ofd.get(at).getId() < firstObject)
                at++; // sorted insert: the archive must stay in ascending id order
            for (int i = 0; i < pieces.size(); i++) {
                FileData fd = new FileData();
                fd.setId(firstObject + i);
                fd.setNameHash(-1);
                ofd.add(at + i, fd);
                objContents.add(at + i, objBytes.get(i));
            }
            for (int i = 1; i < ofd.size(); i++)
                BuildChampionshipBoard.check(ofd.get(i - 1).getId() <= ofd.get(i).getId(), "object archive out of order at " + i);
            objArchive.setFileData(ofd.toArray(new FileData[0]));
            BuildChampionshipBoard.rewrite(storage, configs, objArchive, SpliceItemOption.joinChunks(objContents));
            BuildChampionshipBoard.storeIndex(storage, configs);
            System.out.println("APPLY complete -- " + n + " sprites, " + n + " textures, " + pieces.size() + " models + objects.");
        }
    }

    /**
     * Builds one model into BuildChampionshipBoard's lists: every texture cell clipped to {@code tile} (image space, null =
     * the whole rug) and to the circle, fan-triangulated, vertices relative to the tile (dx, dy) centre. False if empty.
     */
    static boolean buildPiece(int modelId, int[] texIds, double[][] tile, int dx, int dy) {
        BuildChampionshipBoard.verts.clear();
        BuildChampionshipBoard.faces.clear();
        BuildChampionshipBoard.texTris.clear();
        BuildChampionshipBoard.MODEL_ID = modelId;
        BuildChampionshipBoard.facing = new double[]{0, -1, 0}; // y up is negative: faces point up
        double[][] circle = new double[SIDES][];
        for (int i = 0; i < SIDES; i++) {
            double ang = 2 * Math.PI * (i + 0.5) / SIDES;
            circle[i] = new double[]{HALF * Math.cos(ang), HALF * Math.sin(ang)};
        }
        Map<Long, Integer> shared = new HashMap<>();
        double step = 2.0 * HALF / GRID;
        for (int r = 0; r < GRID; r++)
            for (int c = 0; c < GRID; c++) {
                double u0 = -HALF + c * step, u1 = u0 + step, v0 = -HALF + r * step, v1 = v0 + step;
                List<double[]> poly = List.of(new double[]{u0, v0}, new double[]{u1, v0}, new double[]{u1, v1}, new double[]{u0, v1});
                if (tile != null)
                    poly = clip(poly, tile);
                poly = clip(poly, circle);
                if (poly.size() < 3)
                    continue;
                int first = vert(shared, poly.get(0)[0], poly.get(0)[1], dx, dy);
                List<int[]> tris = new ArrayList<>();
                for (int k = 1; k + 1 < poly.size(); k++) {
                    int b2 = vert(shared, poly.get(k)[0], poly.get(k)[1], dx, dy), c2 = vert(shared, poly.get(k + 1)[0], poly.get(k + 1)[1], dx, dy);
                    if (b2 != first && c2 != first && b2 != c2)
                        tris.add(new int[]{first, b2, c2});
                }
                if (tris.isEmpty())
                    continue; // only a sliver of this cell touches the tile
                int p = vert(shared, u0, v0, dx, dy), m = vert(shared, u1, v0, dx, dy), nn = vert(shared, u0, v1, dx, dy);
                BuildChampionshipBoard.texTris.add(new int[]{p, m, nn});
                int coord = BuildChampionshipBoard.texTris.size() - 1;
                int tex = texIds[r * GRID + c];
                for (int[] t : tris)
                    BuildChampionshipBoard.faces.add(BuildChampionshipBoard.oriented(t[0], t[1], t[2],
                            new int[]{t[0], t[1], t[2], 0, 0, tex, coord, 0}));
            }
        return !BuildChampionshipBoard.faces.isEmpty();
    }

    /**
     * Image space (u right, v down, rug centre 0) -> a vertex of the piece on tile (dx, dy): x = u (image right = east),
     * z = -v (image top = north), minus the tile's offset from the rug centre. Shared within a piece.
     */
    static int vert(Map<Long, Integer> shared, double u, double v, int dx, int dy) {
        int x = (int) Math.round(u) - dx * 128, z = (int) Math.round(-v) - dy * 128;
        long key = ((long) x << 32) ^ (z & 0xffffffffL);
        return shared.computeIfAbsent(key, k -> BuildChampionshipBoard.v(x, LIFT, z));
    }

    /** Sutherland-Hodgman: a convex polygon clipped to the convex polygon {@code clipper} (either winding). */
    static List<double[]> clip(List<double[]> subject, double[][] clipper) {
        double orient = Math.signum(side(clipper[0], clipper[1], clipper[2]));
        List<double[]> out = new ArrayList<>(subject);
        for (int i = 0; i < clipper.length && !out.isEmpty(); i++) {
            double[] a = clipper[i], b = clipper[(i + 1) % clipper.length];
            List<double[]> in = out;
            out = new ArrayList<>();
            for (int j = 0; j < in.size(); j++) {
                double[] cur = in.get(j), prev = in.get((j + in.size() - 1) % in.size());
                boolean curIn = side(a, b, cur) * orient >= 0, prevIn = side(a, b, prev) * orient >= 0;
                if (curIn) {
                    if (!prevIn) out.add(cross(prev, cur, a, b));
                    out.add(cur);
                } else if (prevIn) {
                    out.add(cross(prev, cur, a, b));
                }
            }
        }
        return out;
    }

    static double side(double[] a, double[] b, double[] p) {
        return (b[0] - a[0]) * (p[1] - a[1]) - (b[1] - a[1]) * (p[0] - a[0]);
    }

    static double[] cross(double[] p, double[] q, double[] a, double[] b) {
        double sp = side(a, b, p), sq = side(a, b, q), t = sp / (sp - sq);
        return new double[]{p[0] + t * (q[0] - p[0]), p[1] + t * (q[1] - p[1])};
    }

    // ---- the painted face (3x) ----
    static final Color NAVY_D = new Color(0x0f1d4a), NAVY = new Color(0x1d3384), ROYAL = new Color(0x2a46ad), ROYAL_L = new Color(0x3657c4);
    static final Color GOLD = new Color(0xe3b33c), GOLD_L = new Color(0xffd970), GOLD_D = new Color(0x9c6f12), INK = new Color(0x1a1206);

    static BufferedImage paintFace() {
        int W = SIDE * SCALE, cx = W / 2;
        BufferedImage img = new BufferedImage(W, W, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(NAVY_D);
        g.fillRect(0, 0, W, W);
        int rOuter = W / 2; // the 24-gon edge; only the circle renders in game
        ring(g, cx, rOuter, NAVY_D);                              // navy hem
        ring(g, cx, rOuter - W * 4 / 100, GOLD_D);                // gold ring with a bevel
        ring(g, cx, rOuter - W * 4 / 100 - W / 220, GOLD);
        ring(g, cx, rOuter - W * 7 / 100, NAVY);                  // navy band
        ring(g, cx, rOuter - W * 9 / 100, GOLD);                  // thin inner gold ring
        int field = rOuter - W * 9 / 100 - W / 170;
        g.setPaint(new RadialGradientPaint(cx, cx, field, new float[]{0f, 1f}, new Color[]{ROYAL_L, ROYAL}));
        g.fillOval(cx - field, cx - field, field * 2, field * 2);
        Shape fieldClip = new java.awt.geom.Ellipse2D.Double(cx - field, cx - field, field * 2, field * 2);
        g.setClip(fieldClip); // woven threads inside the field
        g.setColor(new Color(255, 255, 255, 9));
        for (int i = 0; i < W; i += 9)
            g.fillRect(0, i, W, 2);
        g.setColor(new Color(0, 0, 0, 13));
        for (int i = 0; i < W; i += 9)
            g.fillRect(i, 0, 2, W);
        g.setClip(null);

        Font font = new Font("Georgia", Font.BOLD, W / 9);
        drawGoldText(g, "Division", font, cx, W * 44 / 100);
        drawGoldText(g, "Rivals", font, cx, W * 57 / 100);
        // wings fanning out on both sides of the text, and the outlined V under it
        wing(g, W * 26 / 100, W * 52 / 100, W / 6, true);
        wing(g, W * 74 / 100, W * 52 / 100, W / 6, false);
        chevron(g, cx, W * 61 / 100, W * 20 / 100, W * 12 / 100);
        g.dispose();
        return img;
    }

    static void ring(Graphics2D g, int c, int r, Color colour) {
        g.setColor(colour);
        g.fillOval(c - r, c - r, 2 * r, 2 * r);
    }

    static void bevelRect(Graphics2D g, int x, int y, int w, int h, int t) {
        g.setColor(GOLD_D);
        g.fillRect(x, y, w, h);
        g.setColor(GOLD);
        g.fillRect(x + t / 5, y + t / 5, w - 2 * (t / 5), h - 2 * (t / 5));
        g.setColor(GOLD_L);
        g.fillRect(x + t / 5, y + t / 5, w - 2 * (t / 5), Math.max(2, t / 4));
    }

    static void drawGoldText(Graphics2D g, String text, Font font, int cx, int baseline) {
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();
        int x = cx - fm.stringWidth(text) / 2;
        Shape outline = font.createGlyphVector(g.getFontRenderContext(), text).getOutline(x, baseline);
        g.setStroke(new BasicStroke(font.getSize() / 7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(INK);
        g.translate(font.getSize() / 30, font.getSize() / 22);
        g.draw(outline);
        g.fill(outline);
        g.translate(-font.getSize() / 30, -font.getSize() / 22);
        g.draw(outline);
        Rectangle b = outline.getBounds();
        g.setPaint(new GradientPaint(0, b.y, GOLD_L, 0, b.y + b.height, GOLD_D));
        g.fill(outline);
        g.setStroke(new BasicStroke(font.getSize() / 40f));
        g.setColor(new Color(255, 236, 170, 120));
        g.draw(outline);
    }

    /** Three feathers fanning out and up from a root at (x, y): the left wing sweeps up-left, the right up-right. */
    static void wing(Graphics2D g, int x, int y, int s, boolean left) {
        int dir = left ? -1 : 1;
        for (int i = 0; i < 3; i++) {
            double ang = Math.toRadians(58 - i * 16); // top feather steepest
            double len = s * (1.0 - i * 0.18);
            double rx = x - dir * i * s / 10.0, ry = y + i * s / 8.0;
            double tx = rx + dir * Math.cos(ang) * len, ty = ry - Math.sin(ang) * len;
            double w = s / 9.0;
            GeneralPath p = new GeneralPath();
            p.moveTo(rx, ry);
            p.lineTo(tx, ty);
            p.lineTo(tx + dir * Math.sin(ang) * w * 0.3, ty + Math.cos(ang) * w * 1.4);
            p.lineTo(rx + dir * w * 0.6, ry + w);
            p.closePath();
            fillGold(g, p, s / 2);
        }
    }

    /** An outlined V (gold band, navy inside, apex down) centred on (cx, y), with flat arms reaching outwards. */
    static void chevron(Graphics2D g, int cx, int y, int halfW, int h) {
        int t = Math.max(6, h / 4);
        GeneralPath v = new GeneralPath();
        v.moveTo(cx - halfW, y);
        v.lineTo(cx, y + h);
        v.lineTo(cx + halfW, y);
        v.lineTo(cx + halfW - t * 1.4, y);
        v.lineTo(cx, y + h - t * 2);
        v.lineTo(cx - halfW + t * 1.4, y);
        v.closePath();
        fillGold(g, v, h);
        GeneralPath arm = new GeneralPath();
        arm.moveTo(cx - halfW * 1.45, y - t / 2.0);
        arm.lineTo(cx - halfW + t / 2.0, y - t / 2.0);
        arm.lineTo(cx - halfW + t * 1.6, y + t / 2.0);
        arm.lineTo(cx - halfW * 1.40, y + t / 2.0);
        arm.closePath();
        fillGold(g, arm, h);
        java.awt.geom.AffineTransform flip = java.awt.geom.AffineTransform.getTranslateInstance(2 * cx, 0);
        flip.scale(-1, 1);
        fillGold(g, flip.createTransformedShape(arm), h);
    }

    static void fillGold(Graphics2D g, Shape s, int size) {
        g.setStroke(new BasicStroke(Math.max(3, size / 14f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(INK);
        g.draw(s);
        Rectangle b = s.getBounds();
        g.setPaint(new GradientPaint(0, b.y, GOLD_L, 0, b.y + b.height, GOLD_D));
        g.fill(s);
    }
}

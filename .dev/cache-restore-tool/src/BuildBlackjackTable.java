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
import net.runelite.cache.fs.*;
import net.runelite.cache.index.FileData;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import java.util.*;

// The Zelus Blackjack Table (2026-10-18): a new 3x2 casino object built from code, option 1 "Play Blackjack" (the server
// hooks it to the existing BlackjackInterface.open, nothing else of Blackjack is touched).
//   - Shape: a "D" table. The dealer's straight side runs along the north edge (+z), the players' curve bulges south.
//   - Felt: painted with Java2D at 4x, resampled to 384x256 and split into a 3x2 grid of 128x128 textures (6 sprites,
//     6 non-animated textures appended above the current max). Navy felt (0a1428 -> 101c38), a gold boundary line along
//     the curve, five gold betting spots on an arc, the gold ZELUS crest with a diamond emblem, and "BLACKJACK PAYS 3 TO
//     2" / "DEALER STANDS ON ALL 17s". Each cell keeps its P/M/N texture triangle and is clipped to the D outline (as
//     BuildRivalsRug), so the curve is smooth and the art never stretches. Image right = east, image top = north.
//   - Rim: a raised black-leather armrest along the curve with a gold trim band, a mahogany apron below it, a mahogany
//     dealer bar along the straight side with a gold inlay, and a closed underside.
//   - Brass pedestals: three gold columns on wider gold foot plates.
//   - Props: chip tray with eight coloured chip stacks (dealer side, centre), a dealing shoe with a gold lid and a card
//     peeking out (east), a discard holder with a card stack (west).
//   - Object: a clone of the 3x2 Banquet table 12315 (solid, blocks projectiles like it), renamed, its recolours dropped,
//     its model list (opcode 5) replaced by the new model as type 10, action 1 "Play Blackjack".
// Usage: verify|apply <cachePath> <previewDir> <objectId> <modelId> <firstSpriteId>
public class BuildBlackjackTable {
    static final String NAME = "Zelus Blackjack Table", ACTION = "Play Blackjack";
    static final int TEMPLATE_OBJECT = 12315, GX = 3, GZ = 2, TILE_PX = 128, CELL = 120;
    /** Felt image space: u = x from -180, v = -z from -110 (image top = north). */
    static final int U0 = -180, V0 = -110;
    static final int TOP = -96, RIM_H = 9, APRON = 22;
    /** The D's straight side (centre of the ellipses) and the felt / rim-outer half-ellipse radii. */
    static final int C = 104, FA = 166, FB = 212, OA = 184, OB = 230, SEG = 40;
    static final int S = 4; // paint scale per texture pixel

    static short hsl(int h, int s, int l) {
        return BuildChampionshipBoard.hsl(h, s, l);
    }

    static final short LEATHER = hsl(6, 1, 11), LEATHER_L = hsl(6, 1, 15), MAHOGANY = hsl(3, 5, 22), MAHOGANY_D = hsl(3, 5, 14),
            GOLD = BuildChampionshipBoard.GOLD, GOLD_D = BuildChampionshipBoard.GOLD_D, GOLD_L = BuildChampionshipBoard.GOLD_L,
            WHITE = hsl(0, 0, 120), SMOKE = hsl(40, 2, 26), BLACK = hsl(0, 0, 8), CARD_BACK = hsl(0, 6, 30);
    static final short[] CHIPS = {hsl(0, 7, 34), hsl(0, 0, 110), hsl(22, 6, 30), hsl(0, 0, 14), hsl(50, 5, 32),
            hsl(8, 6, 58), hsl(42, 6, 34), hsl(5, 7, 46)};

    public static void main(String[] args) throws Exception {
        boolean apply = args[0].equals("apply");
        File previewDir = new File(args[2]);
        previewDir.mkdirs();
        int objectId = Integer.parseInt(args[3]), modelId = Integer.parseInt(args[4]), firstSprite = Integer.parseInt(args[5]);
        BuildChampionshipBoard.check(objectId <= 65535, "spawned object ids must fit 16 bits");
        try (Store store = new Store(new File(args[1]))) {
            store.load();
            Storage storage = store.getStorage();
            Index textures = store.getIndex(IndexType.TEXTURES), sprites = store.getIndex(IndexType.SPRITES);
            Index models = store.getIndex(IndexType.MODELS), configs = store.getIndex(IndexType.CONFIGS);

            // ---- felt -> 6 textures ----
            int wpx = GX * TILE_PX, hpx = GZ * TILE_PX;
            BufferedImage felt = paintFelt(wpx * S, hpx * S);
            ImageIO.write(felt, "png", new File(previewDir, "blackjack_felt_4x.png"));
            Image scaled = felt.getScaledInstance(wpx, hpx, Image.SCALE_AREA_AVERAGING);
            BufferedImage small = new BufferedImage(wpx, hpx, BufferedImage.TYPE_INT_RGB);
            Graphics2D sg = small.createGraphics();
            sg.drawImage(scaled, 0, 0, null);
            sg.dispose();

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
            int n = GX * GZ;
            int[] texIds = new int[n];
            List<byte[]> spriteBytes = new ArrayList<>(), textureBytes = new ArrayList<>();
            Map<Integer, int[]> texPixels = new HashMap<>();
            int spriteCompression = sprites.getArchive(1043).getCompression();
            for (int t = 0; t < n; t++) {
                texIds[t] = maxTex + 1 + t;
                int spriteId = firstSprite + t;
                BuildChampionshipBoard.check(sprites.getArchive(spriteId) == null, "sprite " + spriteId + " already exists");
                int ox = (t % GX) * TILE_PX, oy = (t / GX) * TILE_PX;
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

            // ---- model ----
            buildModel(modelId, texIds);
            ModelDefinition def = BuildChampionshipBoard.toDefinition();
            byte[] modelBytes = ModelType3Encoder.encode(def);
            ModelDefinition back = new ModelLoader().load(modelId, modelBytes);
            BuildChampionshipBoard.check(back.vertexCount == def.vertexCount && back.faceCount == def.faceCount
                    && back.numTextureFaces == def.numTextureFaces, "model counts did not round-trip");
            for (int i = 0; i < def.faceCount; i++)
                BuildChampionshipBoard.check((back.faceTextures == null ? -1 : back.faceTextures[i]) == def.faceTextures[i],
                        "model face " + i + " texture");
            BuildChampionshipBoard.check(models.getArchive(modelId) == null, "model " + modelId + " already exists");
            for (double[] view : new double[][]{{0, -35}, {35, -30}, {180, -30}, {90, -20}, {0, -85}})
                ImageIO.write(BuildChampionshipBoard.preview(back, texPixels, view[0], view[1]), "png",
                        new File(previewDir, String.format("blackjack_%+d_%+d.png", (int) view[0], (int) view[1])));
            System.out.printf("model %d: %d verts, %d faces (%d textured tris); textures %d-%d, sprites %d-%d%n", modelId,
                    def.vertexCount, def.faceCount, def.numTextureFaces, texIds[0], texIds[n - 1], firstSprite, firstSprite + n - 1);

            // ---- object: clone of Banquet table 12315 ----
            Archive objArchive = configs.getArchive(ConfigType.OBJECT.getId());
            byte[] objDec = objArchive.decompress(storage.loadArchive(objArchive));
            FileData[] objFd = objArchive.getFileData();
            List<byte[]> objContents = SpliceItemOption.splitChunks(objDec, objFd.length);
            ObjectLoader loader = new ObjectLoader();
            int src = -1;
            for (int slot = 0; slot < objFd.length; slot++) {
                int id = objFd[slot].getId();
                BuildChampionshipBoard.check(id != objectId, "object " + objectId + " already exists");
                if (id == TEMPLATE_OBJECT) src = slot;
            }
            BuildChampionshipBoard.check(src != -1, "template object " + TEMPLATE_OBJECT + " not found");
            ObjectDefinition before = loader.load(TEMPLATE_OBJECT, objContents.get(src));
            byte[] work = objContents.get(src);
            for (int op : new int[]{5, 40, 41, 30, 31, 32, 33, 34})
                work = SpliceNewObject.removeOpcode(work, op);
            work = SpliceNewObject.replaceOpcode(work, 1, new byte[]{1, (byte) (modelId >> 8), (byte) modelId, 10});
            work = SpliceNewObject.replaceOpcode(work, 2, SpliceNewObject.withTerminator(NAME));
            work = SpliceNewObject.replaceOpcode(work, 30, SpliceNewObject.withTerminator(ACTION));
            ObjectDefinition after = loader.load(objectId, work);
            BuildChampionshipBoard.check(NAME.equals(after.getName()) && Arrays.equals(after.getObjectModels(), new int[]{modelId})
                    && Arrays.equals(after.getObjectTypes(), new int[]{10}) && ACTION.equals(after.getActions()[0])
                    && after.getSizeX() == 3 && after.getSizeY() == 2 && after.getInteractType() == before.getInteractType()
                    && after.getRecolorToFind() == null, "object clone");
            System.out.printf("object %d \"%s\" %dx%d interactType=%d, action 1 \"%s\"%n", objectId, NAME, after.getSizeX(),
                    after.getSizeY(), after.getInteractType(), ACTION);

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
            BuildChampionshipBoard.storeArchive(storage, models, modelId, models.getArchive(3293).getCompression(), modelBytes);
            BuildChampionshipBoard.storeIndex(storage, models);
            List<FileData> ofd = new ArrayList<>(Arrays.asList(objFd));
            int at = 0;
            while (at < ofd.size() && ofd.get(at).getId() < objectId)
                at++; // sorted insert: the archive must stay in ascending id order
            FileData fd = new FileData();
            fd.setId(objectId);
            fd.setNameHash(-1);
            ofd.add(at, fd);
            objContents.add(at, work);
            for (int i = 1; i < ofd.size(); i++)
                BuildChampionshipBoard.check(ofd.get(i - 1).getId() <= ofd.get(i).getId(), "object archive out of order at " + i); // as BuildRivalsRug: the archive has a pre-existing duplicate id
            objArchive.setFileData(ofd.toArray(new FileData[0]));
            BuildChampionshipBoard.rewrite(storage, configs, objArchive, SpliceItemOption.joinChunks(objContents));
            BuildChampionshipBoard.storeIndex(storage, configs);
            System.out.println("APPLY complete -- " + n + " sprites + textures, model " + modelId + ", object " + objectId + ".");
        }
    }

    // ---- geometry ----

    static int v(double x, double y, double z) {
        return BuildChampionshipBoard.v((int) Math.round(x), (int) Math.round(y), (int) Math.round(z));
    }

    /** A point of the half-ellipse (radii a, b) at parameter t in [0, pi]: t = 0 east end, pi/2 south tip, pi west end. */
    static double[] ell(double a, double b, double t) {
        return new double[]{a * Math.cos(t), C - b * Math.sin(t)};
    }

    static void face(double[] dir) {
        BuildChampionshipBoard.facing = dir;
    }

    static void buildModel(int modelId, int[] texIds) {
        BuildChampionshipBoard.verts.clear();
        BuildChampionshipBoard.faces.clear();
        BuildChampionshipBoard.texTris.clear();
        BuildChampionshipBoard.prio = 0;
        BuildChampionshipBoard.MODEL_ID = modelId;

        // felt: every texture cell clipped to the D (straight side at z = C), fan-triangulated, faces up
        double[][] d = new double[SEG + 1][];
        for (int k = 0; k <= SEG; k++) {
            double[] p = ell(FA, FB, Math.PI * k / SEG);
            d[k] = new double[]{p[0], -p[1]}; // image space (u, v)
        }
        face(new double[]{0, -1, 0});
        Map<Long, Integer> shared = new HashMap<>();
        for (int r = 0; r < GZ; r++)
            for (int c = 0; c < GX; c++) {
                double u0 = U0 + c * CELL, u1 = u0 + CELL, v0 = V0 + r * CELL, v1 = v0 + CELL;
                List<double[]> poly = BuildRivalsRug.clip(List.of(new double[]{u0, v0}, new double[]{u1, v0},
                        new double[]{u1, v1}, new double[]{u0, v1}), d);
                if (poly.size() < 3)
                    continue;
                int first = feltVert(shared, poly.get(0));
                List<int[]> tris = new ArrayList<>();
                for (int k = 1; k + 1 < poly.size(); k++) {
                    int b2 = feltVert(shared, poly.get(k)), c2 = feltVert(shared, poly.get(k + 1));
                    if (b2 != first && c2 != first && b2 != c2)
                        tris.add(new int[]{first, b2, c2});
                }
                if (tris.isEmpty())
                    continue;
                int p = feltVert(shared, new double[]{u0, v0}), m = feltVert(shared, new double[]{u1, v0}),
                        nn = feltVert(shared, new double[]{u0, v1});
                BuildChampionshipBoard.texTris.add(new int[]{p, m, nn});
                int coord = BuildChampionshipBoard.texTris.size() - 1;
                for (int[] t : tris)
                    BuildChampionshipBoard.faces.add(BuildChampionshipBoard.oriented(t[0], t[1], t[2],
                            new int[]{t[0], t[1], t[2], 0, 0, texIds[r * GX + c], coord, 0}));
            }

        // armrest + apron: one shared vertex ring per profile point, Gouraud faces -> smooth shading along the curve
        int[][] ring = new int[PROFILE.length][SEG + 1];
        for (int r = 0; r < PROFILE.length; r++)
            for (int k = 0; k <= SEG; k++) {
                double[] p = ell(FA + PROFILE[r][0], FB + PROFILE[r][0], Math.PI * k / SEG);
                ring[r][k] = v(p[0], PROFILE[r][1], p[1]);
            }
        short[] partColour = {LEATHER, GOLD, MAHOGANY, MAHOGANY_D};
        for (int r = 0; r + 1 < PROFILE.length; r++) {
            double dr = PROFILE[r + 1][0] - PROFILE[r][0], dy = PROFILE[r + 1][1] - PROFILE[r][1];
            double nr = dy, ny = -dr; // the profile's outward normal in (radial, y): the tangent turned by -90 degrees
            for (int k = 0; k < SEG; k++) {
                double[] mid = ell(FA, FB, Math.PI * (k + 0.5) / SEG);
                double rx = mid[0], rz = mid[1] - C, len = Math.hypot(rx, rz);
                face(new double[]{nr * rx / len, ny, nr * rz / len});
                short colour = partColour[PROFILE_PART[r]];
                smooth(ring[r][k], ring[r][k + 1], ring[r + 1][k + 1], colour);
                smooth(ring[r][k], ring[r + 1][k + 1], ring[r + 1][k], colour);
            }
        }
        // underside: a fan from the D's centre to the apron's lower edge
        face(new double[]{0, 1, 0});
        int last = PROFILE.length - 1, centre = v(0, TOP + APRON, C);
        for (int k = 0; k < SEG; k++)
            BuildChampionshipBoard.tri(centre, ring[last][k], ring[last][k + 1], MAHOGANY_D);

        // dealer bar along the straight side (it caps the armrest's ends), a brass edge facing the players
        int barHalf = FA + 19;
        BuildChampionshipBoard.box(-barHalf, barHalf, TOP - 12, TOP + APRON, C, C + 20, MAHOGANY);
        BuildChampionshipBoard.box(-barHalf + 2, barHalf - 2, TOP - 13, TOP - 12, C + 2, C + 18, MAHOGANY_D);
        BuildChampionshipBoard.box(-barHalf, barHalf, TOP - 12, TOP - 9, C - 2, C, GOLD);

        // brass pedestals: slim columns on foot plates
        for (int[] leg : new int[][]{{-90, 40}, {90, 40}, {0, -46}}) {
            BuildChampionshipBoard.box(leg[0] - 7, leg[0] + 7, TOP + APRON, -8, leg[1] - 7, leg[1] + 7, GOLD_D);
            BuildChampionshipBoard.box(leg[0] - 10, leg[0] + 10, TOP + APRON, TOP + APRON + 5, leg[1] - 10, leg[1] + 10, GOLD);
            BuildChampionshipBoard.box(leg[0] - 18, leg[0] + 18, -8, 0, leg[1] - 18, leg[1] + 18, GOLD);
        }

        // props resting on the felt get a higher render priority: the client sorts faces by depth only within a priority,
        // and the big felt triangles otherwise sort in front of these low pieces and hide them
        BuildChampionshipBoard.prio = 1;
        // brass chip tray against the dealer bar: a base plate, a front lip and 9 dividers making 8 compartments
        int trayX0 = -72, comp = 18, trayZ0 = C - 30, trayZ1 = C - 3;
        BuildChampionshipBoard.box(trayX0 - 2, -trayX0 + 2, TOP - 2, TOP, trayZ0 - 2, trayZ1, GOLD_D);
        BuildChampionshipBoard.box(trayX0 - 2, -trayX0 + 2, TOP - 7, TOP - 2, trayZ0 - 2, trayZ0, GOLD);
        for (int i = 0; i <= 8; i++) {
            int x = trayX0 + i * comp;
            BuildChampionshipBoard.box(x - 1, x + 1, TOP - 9, TOP - 2, trayZ0, trayZ1, GOLD);
        }
        // in each compartment a row of round chips lying on their edges (a cylinder along z, banded chip by chip)
        BuildChampionshipBoard.prio = 2; // the chips lie in the tray: drawn after its plate
        for (int i = 0; i < 8; i++) {
            double cx = trayX0 + i * comp + comp / 2.0;
            cylinder(cx, TOP - 2 - CHIP_R, (trayZ0 + trayZ1) / 2.0, CHIP_R, trayZ1 - trayZ0 - 2, 2, TRAY_CHIPS[i]);
        }
        // loose betting chips beside the tray (vertical stacks of different heights) and two more by the shoe
        double[][] stacks = {{84, C - 22, 7, 0}, {97, C - 25, 5, 1}, {89, C - 11, 4, 2}, {150, C - 52, 5, 3}, {139, C - 60, 3, 4}};
        for (double[] s : stacks)
            cylinder(s[0], TOP, s[1], CHIP_R - 1, s[2] * 2, 1, LOOSE_CHIPS[(int) s[3]]);
        BuildChampionshipBoard.prio = 1;
        // dealing shoe (east) with a card deck inside, and a brass discard tray (west)
        shoe(106, 140, C - 40, C - 8);
        discardTray(-138, -106, C - 30, C - 10);
        BuildChampionshipBoard.prio = 0;
    }

    static final double CHIP_R = 7;
    static final short GOLD_CHIP = hsl(10, 7, 74), GOLD_CHIP_L = hsl(10, 4, 100), RED = hsl(0, 7, 34), RED_L = hsl(0, 5, 70),
            BLUE = hsl(42, 6, 36), BLUE_L = hsl(42, 3, 80), NAVY = hsl(42, 5, 20), NAVY_L = hsl(42, 3, 50),
            PURPLE = hsl(50, 6, 32), PURPLE_L = hsl(50, 3, 72), GREEN = hsl(22, 6, 30), GREEN_L = hsl(0, 0, 112),
            EMERALD = hsl(20, 7, 36), EMERALD_L = hsl(20, 4, 70), ORANGE = hsl(5, 7, 46), ORANGE_L = hsl(5, 5, 78),
            SLATE = hsl(0, 0, 54), SLATE_L = hsl(0, 0, 96), ONYX = hsl(0, 0, 14), ONYX_L = hsl(0, 0, 70);
    /** Per tray compartment: {chip colour, the edge-spot colour between chips}, as the mock-up's left-to-right row. */
    static final short[][] TRAY_CHIPS = {{GOLD_CHIP, GOLD_CHIP_L}, {RED, RED_L}, {BLUE, BLUE_L}, {NAVY, NAVY_L},
            {PURPLE, PURPLE_L}, {GREEN, GREEN_L}, {EMERALD, EMERALD_L}, {ORANGE, ORANGE_L}};
    static final short[][] LOOSE_CHIPS = {{GOLD_CHIP, GOLD_CHIP_L}, {RED, RED_L}, {BLUE, BLUE_L}, {SLATE, SLATE_L}, {ONYX, ONYX_L}};

    /**
     * A low-poly chip cylinder: 8 sides with shared, Gouraud-shaded side vertices (smooth and round) and flat caps.
     * axis 1 = vertical stack standing on y = cy (one chip per 2 units of length), axis 2 = a row lying along z centred
     * on cz (one chip per ~3 units). Bands alternate the chip colour with its edge-spot colour so single chips read.
     */
    static void cylinder(double cx, double cy, double cz, double r, double len, int axis, short[] colours) {
        int sides = 8, bands = Math.max(2, (int) Math.round(len / (axis == 1 ? 3 : 5)));
        int[][] ring = new int[bands + 1][sides];
        for (int b = 0; b <= bands; b++) {
            double along = len * b / bands;
            for (int k = 0; k < sides; k++) {
                double a = 2 * Math.PI * k / sides, ca = r * Math.cos(a), sa = r * Math.sin(a);
                ring[b][k] = axis == 1 ? v(cx + ca, cy - along, cz + sa) : v(cx + ca, cy + sa, cz - len / 2 + along);
            }
        }
        for (int b = 0; b < bands; b++) {
            short colour = colours[b % 2 == 0 ? 0 : 1];
            for (int k = 0; k < sides; k++) {
                int k2 = (k + 1) % sides;
                double am = 2 * Math.PI * (k + 0.5) / sides;
                face(axis == 1 ? new double[]{Math.cos(am), 0, Math.sin(am)} : new double[]{Math.cos(am), Math.sin(am), 0});
                smooth(ring[b][k], ring[b][k2], ring[b + 1][k2], colour);
                smooth(ring[b][k], ring[b + 1][k2], ring[b + 1][k], colour);
            }
        }
        // caps: their own vertices, so the flat faces don't smooth into the sides
        for (int end : axis == 1 ? new int[]{bands} : new int[]{0, bands}) {
            double along = len * end / bands;
            int[] cap = new int[sides];
            for (int k = 0; k < sides; k++) {
                double a = 2 * Math.PI * k / sides, ca = r * Math.cos(a), sa = r * Math.sin(a);
                cap[k] = axis == 1 ? v(cx + ca, cy - along, cz + sa) : v(cx + ca, cy + sa, cz - len / 2 + along);
            }
            face(axis == 1 ? new double[]{0, -1, 0} : new double[]{0, 0, end == 0 ? -1 : 1});
            for (int k = 1; k + 1 < sides; k++)
                BuildChampionshipBoard.tri(cap[0], cap[k], cap[k + 1], colours[0]);
        }
    }

    /** A low brass discard tray: base, back and side walls, open toward the players. */
    static void discardTray(int x0, int x1, int z0, int z1) {
        BuildChampionshipBoard.box(x0, x1, TOP - 2, TOP, z0, z1, GOLD_D);
        BuildChampionshipBoard.box(x0, x1, TOP - 8, TOP - 2, z1 - 2, z1, GOLD);
        BuildChampionshipBoard.box(x0, x0 + 2, TOP - 6, TOP - 2, z0, z1 - 2, GOLD);
        BuildChampionshipBoard.box(x1 - 2, x1, TOP - 6, TOP - 2, z0, z1 - 2, GOLD);
    }

    /** A Gouraud-shaded (render type 0) triangle facing `facing`: shared vertices give it a smooth, rounded look. */
    static void smooth(int a, int b, int c, short colour) {
        BuildChampionshipBoard.faces.add(BuildChampionshipBoard.oriented(a, b, c,
                new int[]{a, b, c, colour, 0, -1, -1, BuildChampionshipBoard.prio}));
    }

    /**
     * The armrest's cross-section from the felt edge outward: {offset from the felt ellipse, y}. A padded leather roll
     * (rises, rounds over, rolls down), a gold band, then the mahogany apron tucking under.
     */
    static final double[][] PROFILE = {{0, TOP}, {1, TOP - 5}, {4, TOP - 9}, {9, TOP - 11}, {14, TOP - 9}, {17, TOP - 5},
            {18, TOP}, {18, TOP + 3}, {18, TOP + 6}, {17, TOP + 14}, {14, TOP + APRON}};
    /** Per band between profile points: 0 leather, 1 gold, 2 mahogany, 3 the lowest band in a darker mahogany (baked shade). */
    static final int[] PROFILE_PART = {0, 0, 0, 0, 0, 0, 0, 1, 2, 3};

    /**
     * The dealing shoe: an open mahogany box sloping down toward the players (walls 12 high at the front, 24 at the
     * back) with the white deck visible inside and a brass front plate where the cards come out.
     */
    static void shoe(int x0, int x1, int z0, int z1) {
        BuildChampionshipBoard.box(x0, x1, TOP - 3, TOP, z0, z1, MAHOGANY_D);                    // base
        wedge(x0, x0 + 3, z0 + 6, z1, TOP - 12, TOP - 24, MAHOGANY, MAHOGANY, MAHOGANY);          // west wall
        wedge(x1 - 3, x1, z0 + 6, z1, TOP - 12, TOP - 24, MAHOGANY, MAHOGANY, MAHOGANY);          // east wall
        BuildChampionshipBoard.box(x0 + 3, x1 - 3, TOP - 24, TOP - 3, z1 - 3, z1, MAHOGANY);     // back wall
        wedge(x0 + 3, x1 - 3, z0 + 8, z1 - 3, TOP - 9, TOP - 20, WHITE, WHITE, WHITE);             // the deck
        wedge(x0, x1, z0, z0 + 6, TOP - 6, TOP - 12, GOLD, GOLD_L, GOLD);                           // brass front plate
    }

    /**
     * A wedge sitting on the felt: x0..x1, z0 (players' side, `lo` high) .. z1 (dealer's side, `hi` high), its sloped top,
     * front face (toward the players) and the other sides in their own colours.
     */
    static void wedge(int x0, int x1, int z0, int z1, int lo, int hi, short top, short front, short side) {
        int a = v(x0, TOP, z0), b = v(x1, TOP, z0), c = v(x1, TOP, z1), dd = v(x0, TOP, z1);
        int e = v(x0, lo, z0), f = v(x1, lo, z0), g = v(x1, hi, z1), h = v(x0, hi, z1);
        face(new double[]{0, -1, -0.5});
        BuildChampionshipBoard.quad(e, f, g, h, top);
        face(new double[]{0, 0, -1});
        BuildChampionshipBoard.quad(a, b, f, e, front);
        face(new double[]{0, 0, 1});
        BuildChampionshipBoard.quad(dd, c, g, h, side);
        face(new double[]{1, 0, 0});
        BuildChampionshipBoard.quad(b, c, g, f, side);
        face(new double[]{-1, 0, 0});
        BuildChampionshipBoard.quad(a, dd, h, e, side);
    }

    static int feltVert(Map<Long, Integer> shared, double[] uv) {
        int x = (int) Math.round(uv[0]), z = (int) Math.round(-uv[1]);
        long key = ((long) x << 32) ^ (z & 0xffffffffL);
        return shared.computeIfAbsent(key, k -> BuildChampionshipBoard.v(x, TOP, z));
    }

    // ---- the felt ----

    /** Royal Zelus sapphire velvet: a soft radial light over deep sapphire, warm gold markings. */
    static final Color FELT_EDGE = new Color(0x0b1d48), FELT_MID = new Color(0x12306e), FELT_HI = new Color(0x1d48a0);
    static final Color GOLD_C = new Color(0xeab84a), GOLD_LC = new Color(0xffdc7a), GOLD_DC = new Color(0xa8761a);

    /** Image px for felt image-space (u, v). */
    static double px(double u, int w) {
        return (u - U0) * w / (GX * CELL);
    }

    static double py(double v, int h) {
        return (v - V0) * h / (GZ * CELL);
    }

    static BufferedImage paintFelt(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        double k = (double) w / (GX * CELL); // px per world unit
        // felt: radial navy, a fine weave
        g.setPaint(new RadialGradientPaint((float) px(0, w), (float) py(0, h), (float) (200 * k),
                new float[]{0f, 0.55f, 1f}, new Color[]{FELT_HI, FELT_MID, FELT_EDGE}));
        g.fillRect(0, 0, w, h);
        Random rnd = new Random(21);
        for (int i = 0; i < w * h / 40; i++) {
            int x = rnd.nextInt(w), y = rnd.nextInt(h);
            g.setColor(rnd.nextBoolean() ? new Color(255, 255, 255, 7) : new Color(0, 0, 0, 14));
            g.fillRect(x, y, 2, 2);
        }
        // the ellipse centre (straight side) sits at v = -C
        double cx = px(0, w), cy = py(-C, h);
        // gold boundary line along the curve and a thinner one inside it, thick enough to stay clean in game
        g.setColor(GOLD_C);
        g.setStroke(new BasicStroke((float) (3.2 * k)));
        g.draw(new Ellipse2D.Double(cx - 154 * k, cy - 200 * k, 308 * k, 400 * k));
        g.setStroke(new BasicStroke((float) (1.6 * k)));
        g.setColor(GOLD_DC);
        g.draw(new Ellipse2D.Double(cx - 146 * k, cy - 192 * k, 292 * k, 384 * k));
        // five betting circles, evenly spaced and symmetric on one arc toward the players
        for (int s = 0; s < 5; s++) {
            double t = Math.PI * (s + 1) / 6;
            double u = 122 * Math.cos(t), v = 160 * Math.sin(t) - C;
            double sx = px(u, w), sy = py(v, h), r = 16 * k;
            g.setColor(new Color(0x0e2659));
            g.fill(new Ellipse2D.Double(sx - r, sy - r, 2 * r, 2 * r));
            g.setColor(GOLD_C);
            g.setStroke(new BasicStroke((float) (3.0 * k)));
            g.draw(new Ellipse2D.Double(sx - r, sy - r, 2 * r, 2 * r));
            g.setStroke(new BasicStroke((float) (1.4 * k)));
            g.setColor(GOLD_DC);
            double r2 = r - 5 * k;
            g.draw(new Ellipse2D.Double(sx - r2, sy - r2, 2 * r2, 2 * r2));
        }
        // the crest only: a large gold diamond emblem and ZELUS. No small print: it never renders cleanly on felt, and
        // the rules live in the Blackjack window
        double ex = px(0, w);
        diamond(g, ex, py(-50, h), 15 * k);
        BuildRivalsRug.drawGoldText(g, "ZELUS", new Font("Serif", Font.BOLD, (int) (36 * k)), (int) ex, (int) py(-4, h));
        g.dispose();
        return img;
    }

    static void diamond(Graphics2D g, double cx, double cy, double r) {
        Polygon outer = new Polygon(new int[]{(int) cx, (int) (cx + r), (int) cx, (int) (cx - r)},
                new int[]{(int) (cy - r), (int) cy, (int) (cy + r), (int) cy}, 4);
        g.setPaint(new GradientPaint(0, (float) (cy - r), GOLD_LC, 0, (float) (cy + r), GOLD_DC));
        g.fill(outer);
        g.setColor(new Color(0x2a1d06));
        g.setStroke(new BasicStroke((float) (r / 8)));
        g.draw(outer);
        double r2 = r * 0.45;
        g.setColor(new Color(0x0e1a36));
        g.fill(new Polygon(new int[]{(int) cx, (int) (cx + r2), (int) cx, (int) (cx - r2)},
                new int[]{(int) (cy - r2), (int) cy, (int) (cy + r2), (int) cy}, 4));
    }

    static void textCentred(Graphics2D g, String s, Font f, Color c, double cx, double baseline) {
        g.setFont(f);
        int x = (int) (cx - g.getFontMetrics().stringWidth(s) / 2.0);
        g.setColor(new Color(0, 0, 0, 160));
        g.drawString(s, x + 2, (int) baseline + 2);
        g.setColor(c);
        g.drawString(s, x, (int) baseline);
    }
}

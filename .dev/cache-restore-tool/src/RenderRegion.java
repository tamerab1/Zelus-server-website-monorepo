import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.*;
import net.runelite.cache.definitions.loaders.*;
import net.runelite.cache.fs.*;
import net.runelite.cache.region.Location;
import net.runelite.cache.region.HeightCalc;
import net.runelite.cache.index.FileData;

import javax.imageio.ImageIO;
import java.io.File;
import java.util.*;

// Read-only (2026-10-20, region design): renders a map square (terrain + locs) from the cache through SceneRenderer,
// so a region can be judged without the client. Terrain: the u16 tile opcode stream (as io.ruin.model.map.Region:
// 1 = height, 2-49 = overlay + shape / rotation, 50-81 = settings, 82+ = underlay), heights from the explicit byte or
// the procedural height on plane 0; tile colour = overlay (rgb or its texture's average) over the underlay. Locs: the
// object's models for the loc type, scaled / offset / recoloured / retextured, at the tile (centre of its footprint).
// Usage: RenderRegion <cache> <regionId|mx_my> <out.png> [view=iso|top] [yawDeg] [plane] [WxH] [focusX focusY distTiles pitchDeg]
public class RenderRegion {
    public static void main(String[] a) throws Exception {
        int mx, my;
        if (a[1].contains("_")) {
            String[] p = a[1].split("_");
            mx = Integer.parseInt(p[0]);
            my = Integer.parseInt(p[1]);
        } else {
            int r = Integer.parseInt(a[1]);
            mx = r >> 8;
            my = r & 255;
        }
        String view = a.length > 3 ? a[3] : "iso";
        double yaw = Math.toRadians(a.length > 4 ? Double.parseDouble(a[4]) : 35);
        int maxPlane = a.length > 5 ? Integer.parseInt(a[5]) : 0;
        int W = 1800, H = 1300;
        if (a.length > 6) {
            String[] s = a[6].split("x");
            W = Integer.parseInt(s[0]);
            H = Integer.parseInt(s[1]);
        }
        try (Store store = new Store(new File(a[0]))) {
            store.load();
            Scene scene = Scene.load(store, mx, my);
            SceneRenderer r = new SceneRenderer(W, H, 0x1a1d24);
            double tfx = a.length > 7 ? Double.parseDouble(a[7]) : 32, tfy = a.length > 8 ? Double.parseDouble(a[8]) : 32;
            double fx = tfx * 128, fz = tfy * 128, fy = scene.height(0, tfx, tfy);
            double zoom = a.length > 9 ? Double.parseDouble(a[9]) : 64 * 1.05, pitchDeg = a.length > 10 ? Double.parseDouble(a[10]) : 42;
            if (view.equals("top")) {
                r.fov = 0.9;
                r.camera(fx, fy, fz, 0, Math.toRadians(89.9), 64 * 128 * 0.5 / Math.tan(0.45) * 1.04);
            } else {
                r.camera(fx, fy, fz, yaw, Math.toRadians(pitchDeg), zoom * 128);
            }
            scene.draw(r, maxPlane);
            java.awt.image.BufferedImage img = r.image();
            if (view.equals("top") || view.equals("grid")) grid(r, img, scene);
            ImageIO.write(img, "png", new File(a[2]));
            System.out.println("wrote " + a[2] + " (" + scene.locCount + " locs, region " + ((mx << 8) | my) + ")");
        }
    }

    /** Tile grid every 4 tiles (labelled every 8, local coordinates) projected onto the image. */
    static void grid(SceneRenderer r, java.awt.image.BufferedImage img, Scene s) {
        java.awt.Graphics2D g = img.createGraphics();
        g.setFont(new java.awt.Font("Dialog", java.awt.Font.BOLD, 12));
        for (int i = 0; i <= 64; i += 4) {
            double[] a1 = r.project(r.view(i * 128, s.height(0, i, 0), 0)), b1 = r.project(r.view(i * 128, s.height(0, i, 63), 64 * 128));
            double[] a2 = r.project(r.view(0, s.height(0, 0, i), i * 128)), b2 = r.project(r.view(64 * 128, s.height(0, 63, i), i * 128));
            g.setColor(new java.awt.Color(255, 255, 255, i % 8 == 0 ? 110 : 45));
            g.drawLine((int) a1[0], (int) a1[1], (int) b1[0], (int) b1[1]);
            g.drawLine((int) a2[0], (int) a2[1], (int) b2[0], (int) b2[1]);
            if (i % 8 == 0 && i < 64) {
                g.setColor(java.awt.Color.YELLOW);
                g.drawString("x" + i, (int) a1[0] + 2, (int) a1[1] - 4);
                g.drawString("y" + i, (int) a2[0] + 4, (int) a2[1] - 2);
            }
        }
        g.dispose();
    }

    /** A decoded map square plus the definitions it needs. */
    static class Scene {
        final int[][][] heights = new int[4][65][65];
        final int[][][] underlay = new int[4][64][64], overlay = new int[4][64][64], shape = new int[4][64][64];
        final List<Location> locs = new ArrayList<>();
        int locCount;
        Store store;
        Map<Integer, UnderlayDefinition> underlays = new HashMap<>();
        Map<Integer, OverlayDefinition> overlays = new HashMap<>();
        Map<Integer, Integer> textureColour = new HashMap<>();
        Map<Integer, byte[]> objectBytes = new HashMap<>();

        static Scene load(Store store, int mx, int my) throws Exception {
            Scene s = new Scene();
            s.store = store;
            Storage st = store.getStorage();
            Index maps = store.getIndex(IndexType.MAPS);
            Archive m = maps.findArchiveByName("m" + mx + "_" + my);
            Archive l = maps.findArchiveByName("l" + mx + "_" + my);
            if (m != null) s.decodeTerrain(m.decompress(st.loadArchive(m)), mx * 64, my * 64);
            if (l != null) {
                byte[] raw = l.decompress(st.loadArchive(l));
                s.locs.addAll(new LocationsLoader().load(mx, my, raw).getLocations());
                s.locCount = s.locs.size();
            }
            s.loadDefs();
            return s;
        }

        static Scene empty(Store store) throws Exception {
            Scene s = new Scene();
            s.store = store;
            s.loadDefs();
            return s;
        }

        void loadDefs() throws Exception {
            Storage st = store.getStorage();
            Index configs = store.getIndex(IndexType.CONFIGS);
            Archive ua = configs.getArchive(ConfigType.UNDERLAY.getId());
            for (FSFile f : ua.getFiles(st.loadArchive(ua)).getFiles())
                underlays.put(f.getFileId(), new UnderlayLoader().load(f.getFileId(), f.getContents()));
            Archive oa = configs.getArchive(ConfigType.OVERLAY.getId());
            for (FSFile f : oa.getFiles(st.loadArchive(oa)).getFiles())
                overlays.put(f.getFileId(), new OverlayLoader().load(f.getFileId(), f.getContents()));
            Archive ob = configs.getArchive(ConfigType.OBJECT.getId());
            FileData[] fd = ob.getFileData();
            List<byte[]> chunks = InsertObjectActions.splitChunks(ob.decompress(st.loadArchive(ob)), fd.length);
            for (int i = 0; i < fd.length; i++) objectBytes.putIfAbsent(fd[i].getId(), chunks.get(i));
            // texture average colours from their sprites
            Index tex = store.getIndex(IndexType.TEXTURES);
            Index sprites = store.getIndex(IndexType.SPRITES);
            if (tex != null && tex.getArchive(0) != null) {
                Archive ta = tex.getArchive(0);
                for (FSFile f : ta.getFiles(st.loadArchive(ta)).getFiles()) {
                    try {
                        TextureDefinition td = new TextureLoader().load(f.getFileId(), f.getContents());
                        if (td.getFileIds() == null || td.getFileIds().length == 0) continue;
                        Archive sa = sprites.getArchive(td.getFileIds()[0]);
                        if (sa == null) continue;
                        SpriteDefinition sd = new SpriteLoader().load(td.getFileIds()[0], sa.decompress(st.loadArchive(sa)))[0];
                        long rr = 0, gg = 0, bb = 0, n = 0;
                        for (int px : sd.getPixels()) {
                            if (px == 0) continue;
                            rr += (px >> 16) & 255; gg += (px >> 8) & 255; bb += px & 255; n++;
                        }
                        if (n > 0) textureColour.put(f.getFileId(), (int) (rr / n) << 16 | (int) (gg / n) << 8 | (int) (bb / n));
                    } catch (Exception ignored) {
                    }
                }
            }
        }

        void decodeTerrain(byte[] in, int baseX, int baseY) {
            int p = 0;
            for (int z = 0; z < 4; z++) for (int x = 0; x < 64; x++) for (int y = 0; y < 64; y++) {
                Integer explicit = null;
                for (; ; ) {
                    int op = (((in[p] & 0xFF) << 8) | (in[p + 1] & 0xFF)) & 0x7FFF;
                    p += 2;
                    if (op == 0) break;
                    if (op == 1) {
                        explicit = in[p++] & 0xFF;
                        break;
                    }
                    if (op <= 49) {
                        overlay[z][x][y] = ((in[p] & 0xFF) << 8) | (in[p + 1] & 0xFF);
                        p += 2;
                        shape[z][x][y] = (op - 2);
                    } else if (op <= 81) {
                        // settings
                    } else {
                        underlay[z][x][y] = op - 81;
                    }
                }
                int below = z == 0 ? 0 : heights[z - 1][x][y];
                if (explicit != null) {
                    int hh = explicit == 1 ? 0 : explicit;
                    heights[z][x][y] = z == 0 ? -hh * 8 : below - hh * 8;
                } else {
                    heights[z][x][y] = z == 0 ? -HeightCalc.calculate(baseX + x + 0xe3b7b, baseY + y + 0x87cce) * 8 : below - 240;
                }
            }
            for (int z = 0; z < 4; z++) {
                for (int i = 0; i < 64; i++) {
                    heights[z][64][i] = heights[z][63][i];
                    heights[z][i][64] = heights[z][i][63];
                }
                heights[z][64][64] = heights[z][63][63];
            }
        }

        double height(int z, double tx, double ty) {
            int x = Math.max(0, Math.min(63, (int) tx)), y = Math.max(0, Math.min(63, (int) ty));
            return heights[z][x][y];
        }

        int tileColour(int z, int x, int y) {
            int ov = overlay[z][x][y];
            if (ov > 0) {
                OverlayDefinition od = overlays.get(ov - 1);
                if (od != null) {
                    if (od.getTexture() >= 0 && textureColour.containsKey(od.getTexture())) return textureColour.get(od.getTexture());
                    if (od.getRgbColor() != 0xFF00FF) return od.getRgbColor();
                }
            }
            int un = underlay[z][x][y];
            if (un > 0) {
                UnderlayDefinition ud = underlays.get(un - 1);
                if (ud != null) return ud.getColor();
            }
            return z == 0 ? 0x2c3a24 : -1;
        }

        void draw(SceneRenderer r, int maxPlane) throws Exception {
            for (int z = 0; z <= maxPlane; z++)
                for (int x = 0; x < 64; x++) for (int y = 0; y < 64; y++) {
                    int c = tileColour(z, x, y);
                    if (c < 0) continue;
                    if (z > 0 && overlay[z][x][y] == 0 && underlay[z][x][y] == 0) continue;
                    double x0 = x * 128, x1 = x0 + 128, z0 = y * 128, z1 = z0 + 128;
                    double[] sw = {x0, heights[z][x][y], z0}, se = {x1, heights[z][x + 1][y], z0},
                            ne = {x1, heights[z][x + 1][y + 1], z1}, nw = {x0, heights[z][x][y + 1], z1};
                    r.triangle(sw, nw, ne, c, 0, true);
                    r.triangle(sw, ne, se, c, 0, true);
                }
            for (Location loc : locs) {
                int z = loc.getPosition().getZ();
                if (z > maxPlane) continue;
                drawLoc(r, loc.getId(), loc.getType(), loc.getOrientation(), z, loc.getPosition().getX() & 63, loc.getPosition().getY() & 63);
            }
        }

        void drawLoc(SceneRenderer r, int id, int type, int orientation, int z, int x, int y) throws Exception {
            byte[] raw = objectBytes.get(id);
            if (raw == null) return;
            ObjectDefinition d = new ObjectLoader().load(id, raw);
            int[] models = d.getObjectModels();
            if (models == null) return;
            List<Integer> use = new ArrayList<>();
            int[] types = d.getObjectTypes();
            int wantType = type == 11 ? 10 : type;
            if (types == null) {
                for (int mId : models) use.add(mId);
            } else {
                for (int i = 0; i < types.length; i++) if (types[i] == wantType) use.add(models[i]);
                if (use.isEmpty() && (type == 11 || type == 10)) for (int i = 0; i < types.length; i++) if (types[i] == 10) use.add(models[i]);
            }
            if (use.isEmpty()) return;
            Map<Integer, Integer> recolour = new HashMap<>();
            if (d.getRecolorToFind() != null)
                for (int k = 0; k < d.getRecolorToFind().length; k++)
                    recolour.put(d.getRecolorToFind()[k] & 0xffff, d.getRecolorToReplace()[k] & 0xffff);
            int sx = d.getSizeX(), sy = d.getSizeY();
            if ((orientation & 1) == 1) { int t = sx; sx = sy; sy = t; }
            double cxw = type >= 0 && type <= 9 ? x * 128 + 64 : x * 128 + sx * 64;
            double czw = type >= 0 && type <= 9 ? y * 128 + 64 : y * 128 + sy * 64;
            double base = height(z, x + (type <= 9 ? 0.5 : sx / 2.0), y + (type <= 9 ? 0.5 : sy / 2.0));
            boolean mirror = d.isRotated() ^ (orientation > 3);
            for (int mId : use) {
                Archive ma = store.getIndex(IndexType.MODELS).getArchive(mId);
                if (ma == null) continue;
                ModelDefinition md;
                try {
                    md = BuildTierPets.model(store, mId);
                } catch (Exception e) {
                    continue;
                }
                if (md == null) continue;
                // orientation n turns the model so a west-edge wall (0) lands on N (1) / E (2) / S (3) -- checked
                // against a straight wall per orientation seen from above
                int rot = orientation & 3;
                r.model(md, cxw, base, czw, d.getModelSizeX(), d.getModelSizeHeight(), d.getModelSizeY(), rot, mirror,
                        recolour, textureColour, d.getOffsetX(), d.getOffsetHeight(), d.getOffsetY());
                if (type == 2) // wall corner: the second leg, one edge clockwise (0 = west + north)
                    r.model(md, cxw, base, czw, d.getModelSizeX(), d.getModelSizeHeight(), d.getModelSizeY(), (rot + 1) & 3, mirror,
                            recolour, textureColour, d.getOffsetX(), d.getOffsetHeight(), d.getOffsetY());
            }
        }
    }
}

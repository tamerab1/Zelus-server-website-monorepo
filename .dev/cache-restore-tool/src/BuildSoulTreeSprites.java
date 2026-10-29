import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.SpriteDefinition;
import net.runelite.cache.definitions.loaders.SpriteLoader;
import net.runelite.cache.fs.*;
import net.runelite.cache.fs.Container;
import net.runelite.cache.index.FileData;
import net.runelite.cache.index.IndexData;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.zip.CRC32;

// Draws the Soul Tree (interface 1176) sprites, 2026-10-18 round 2: 36 new single-frame sprites from 24582.
// Each is painted with Java2D at 4x, box-downsampled, and pixels under half coverage dropped (OSRS sprites have no
// per-pixel alpha: index 0 = transparent). Partly covered edge pixels and the glows are pre-blended onto the colour
// the sprite will sit on (BG_TREE for the tree area, WELL inside a socket, CARD in the aura cards), so they look soft
// in game. Colours are quantised to the 255-entry palette. Encoded with BuildBrandIcons.encode and decoded back
// (real SpriteLoader) to the exact pixels before anything is written; identical existing sprites are skipped.
//   24582-24590  talent icons 32x32 (SoulTalent order)    24591-24596  sockets 44x44: bronze/lit/gold + hover each
//   24597 rank track ring 52x52   24598-24602 rank arcs 1..5   24603 maxed halo 60x60 (pulsed by the client plugin)
//   24604-24606 Rank Up rune 46x18 lit/hover/dim   24607 weapon glow 160x60   24608 points gem 16x16
//   24609-24611 aura orbs 28x28 (teal/gold/violet)   24612 orb silhouette   24613 lock 10x12
//   24614-24616 aura buttons 62x14 activate/active/locked   24617 dim disc 34x34 (unlearned talent)
//   round 3: 24618 selected-node ring 60x60 (cyan)   24619-24621 wide Rank Up rune 62x18 lit/hover/dim
//   round 7 (2026-10-28, the 3-row tree; "verify2|apply2", from 24700): 24700-24711 talent icons 24x24 (SoulTalent order,
//   incl. Soul Precision / Soul Harvest / Soul Resonance), 24712 compact track 39, 24713-24717 arcs, 24718 halo 45,
//   24719 select ring 45, 24720-24725 sockets 33 (bronze/lit/gold + hover), 24726 dim disc 26, 24727 pedestal 92x22,
//   24728-24731 stage arrows 18x20 (left, left hover, right, right hover)
// Usage: verify|apply|verify2|apply2 <cachePath> [previewPng]
public class BuildSoulTreeSprites {
    static final int BASE = 24582, BASE2 = 24700;
    static int base = BASE;
    /** What each sprite sits on: the tree area's void, a socket's well, an aura card, the stone panel. */
    static final int BG_TREE = 0x14120f, WELL = 0x0e1214, CARD = 0x1d1913, STONE = 0x36302a;

    record Sprite(int id, String name, int w, int h, int bg, double minCover, int[] px) {}

    static final List<Sprite> SPRITES = new ArrayList<>();

    static void add(String name, int w, int h, int bg, double minCover, Consumer<Graphics2D> paint) {
        SPRITES.add(new Sprite(base + SPRITES.size(), name, w, h, bg, minCover, render(w, h, bg, minCover, paint)));
    }

    public static void main(String[] args) throws Exception {
        boolean apply = args[0].startsWith("apply");
        if (args[0].endsWith("2")) {
            base = BASE2;
            defineRound7();
        } else
            defineAll();
        if (args.length > 2)
            preview(new File(args[2]));
        try (Store store = new Store(new File(args[1]))) {
            store.load();
            Storage storage = store.getStorage();
            Index index = store.getIndex(IndexType.SPRITES);
            int compression = index.getArchive(1043).getCompression();
            Map<Integer, byte[]> out = new LinkedHashMap<>();
            for (Sprite s : SPRITES) {
                byte[] enc = BuildBrandIcons.encode(s.px, s.w, s.h);
                if (index.getArchive(s.id) != null) {
                    Archive existing = index.getArchive(s.id);
                    if (!Arrays.equals(existing.decompress(storage.loadArchive(existing)), enc))
                        throw new IllegalStateException("sprite " + s.id + " already exists with other pixels");
                    System.out.println("sprite " + s.id + " already in the cache (identical), skipped");
                    continue;
                }
                SpriteDefinition back = new SpriteLoader().load(s.id, enc)[0];
                int[] got = back.getPixels();
                for (int i = 0; i < got.length; i++)
                    if ((got[i] & 0xffffff) != (s.px[i] & 0xffffff))
                        throw new IllegalStateException("sprite " + s.id + " pixel " + i + " does not round-trip");
                out.put(s.id, enc);
                System.out.println("sprite " + s.id + " " + s.name + " " + s.w + "x" + s.h + " (" + enc.length + " bytes, OK)");
            }
            if (!apply) {
                System.out.println("VERIFY OK -- " + out.size() + " sprites, nothing written.");
                return;
            }
            for (Map.Entry<Integer, byte[]> e : out.entrySet()) {
                int id = e.getKey();
                byte[] raw = e.getValue();
                Archive a = index.addArchive(id);
                a.setNameHash(-1);
                a.setCompression(compression);
                a.setRevision(1);
                Container c = new Container(compression, -1);
                c.compress(raw, null);
                storage.store(index.getId(), id, c.data);
                CRC32 crc = new CRC32();
                crc.update(c.data);
                a.setCrc((int) crc.getValue());
                a.setCompressedSize(c.data.length);
                a.setDecompressedSize(raw.length);
                FileData fd = new FileData();
                fd.setId(0);
                a.setFileData(new FileData[]{fd});
            }
            IndexData indexData = index.toIndexData();
            Container c = new Container(index.getCompression(), -1);
            c.compress(indexData.writeIndexData(), null);
            storage.store(255, index.getId(), c.data);
            index.setCrc(c.crc);
            System.out.println("APPLY complete -- " + out.size() + " sprites written.");
        }
    }

    // ------------------------------------------------------------------ the sprites

    static void defineAll() {
        // talent icons (sit on a socket's well)
        add("devastation", 32, 32, WELL, 0.5, BuildSoulTreeSprites::devastation);
        add("cleave", 32, 32, WELL, 0.5, BuildSoulTreeSprites::cleave);
        add("lifesteal", 32, 32, WELL, 0.5, BuildSoulTreeSprites::lifesteal);
        add("tempest", 32, 32, WELL, 0.5, BuildSoulTreeSprites::tempest);
        add("ricochet", 32, 32, WELL, 0.5, BuildSoulTreeSprites::ricochet);
        add("executioner", 32, 32, WELL, 0.5, BuildSoulTreeSprites::executioner);
        add("siphon", 32, 32, WELL, 0.5, BuildSoulTreeSprites::siphon);
        add("surge", 32, 32, WELL, 0.5, BuildSoulTreeSprites::surge);
        add("ward", 32, 32, WELL, 0.5, BuildSoulTreeSprites::ward);
        // sockets: bronze (unlearned), soul-lit (ranks 1-4), gold (mastered); + a brighter hover each
        int[][] metals = {{0x5c3a1c, 0x9a6634, 0xd29a5c}, {0x0d4f55, 0x1fa7b5, 0x8ff6fa}, {0x7a5212, 0xd9a521, 0xfff0b0}};
        String[] names = {"bronze", "lit", "gold"};
        for (int m = 0; m < 3; m++) {
            int[] metal = metals[m];
            add("socket_" + names[m], 44, 44, BG_TREE, 0.5, g -> socket(g, metal, false));
            add("socket_" + names[m] + "_hover", 44, 44, BG_TREE, 0.5, g -> socket(g, metal, true));
        }
        add("rank_track", 52, 52, BG_TREE, 0.5, g -> arc(g, 0, 360, new Color(0x2a2620), new Color(0x0a0908), false));
        for (int r = 0; r < 5; r++) {
            int seg = r;
            add("rank_arc_" + (r + 1), 52, 52, BG_TREE, 0.5,
                    g -> arc(g, 90 - seg * 72 - 4, -64, new Color(0x2fe8f0), new Color(0x0c5a60), true));
        }
        add("max_halo", 60, 60, BG_TREE, 0.04, BuildSoulTreeSprites::maxHalo);
        add("rankup_lit", 46, 18, BG_TREE, 0.5, g -> rune(g, new Color(0xffc35a), new Color(0x7a5212), new Color(0xfff0b0), false));
        add("rankup_hover", 46, 18, BG_TREE, 0.5, g -> rune(g, new Color(0xffd98a), new Color(0x9a6a1a), new Color(0xffffff), true));
        add("rankup_dim", 46, 18, BG_TREE, 0.5, g -> rune(g, new Color(0x3a332b), new Color(0x1d1913), new Color(0x4a4238), false));
        add("weapon_glow", 160, 60, BG_TREE, 0.03, BuildSoulTreeSprites::weaponGlow);
        add("points_gem", 16, 16, STONE, 0.5, BuildSoulTreeSprites::gem);
        int[][] orbs = {{0x0d5a60, 0x2fd2d8, 0xc8ffff}, {0x7a5212, 0xffc35a, 0xfff6d0}, {0x3a0f6a, 0x9a4ae8, 0xf0d8ff}};
        for (int o = 0; o < 3; o++) {
            int[] c = orbs[o];
            add("aura_orb_" + o, 28, 28, CARD, 0.5, g -> orb(g, c, true));
        }
        add("aura_silhouette", 28, 28, CARD, 0.5, g -> orb(g, new int[]{0x111111, 0x2c2a28, 0x3c3a36}, false));
        add("lock", 10, 12, CARD, 0.5, BuildSoulTreeSprites::lock);
        add("aura_btn_activate", 62, 14, CARD, 0.5, g -> pill(g, new Color(0x1fa7b5), new Color(0x0b4a50), new Color(0x8ff6fa)));
        add("aura_btn_active", 62, 14, CARD, 0.5, g -> pill(g, new Color(0x2f9e46), new Color(0x0f4a1a), new Color(0xa8f5b4)));
        add("aura_btn_locked", 62, 14, CARD, 0.5, g -> pill(g, new Color(0x2c2722), new Color(0x15120f), new Color(0x3c3630)));
        add("dim_disc", 34, 34, WELL, 0.5, g -> {
            g.setColor(new Color(0x050505));
            g.fill(new Ellipse2D.Double(0, 0, 34, 34));
        });
        // round 3 (inspector layout): the selected node's soul-cyan ring, and wider runes for "+ Rank Up"
        add("select_ring", 60, 60, BG_TREE, 0.04, g -> {
            g.setPaint(new RadialGradientPaint(new Point2D.Double(30, 30), 30, new float[]{0.70f, 0.82f, 0.9f, 1f},
                    new Color[]{new Color(47, 232, 240, 0), new Color(120, 245, 250, 230), new Color(47, 210, 216, 110),
                            new Color(47, 210, 216, 0)}));
            g.fill(new Ellipse2D.Double(0, 0, 60, 60));
        });
        add("rankup_wide_lit", 62, 18, BG_TREE, 0.5, g -> rune(g, 62, new Color(0xffc35a), new Color(0x7a5212), new Color(0xfff0b0), false));
        add("rankup_wide_hover", 62, 18, BG_TREE, 0.5, g -> rune(g, 62, new Color(0xffd98a), new Color(0x9a6a1a), new Color(0xffffff), true));
        add("rankup_wide_dim", 62, 18, BG_TREE, 0.5, g -> rune(g, 62, new Color(0x3a332b), new Color(0x1d1913), new Color(0x4a4238), false));
    }

    /** Paints at a fraction of the original size (the compact 3-row tree's nodes). */
    static Consumer<Graphics2D> scaled(double f, Consumer<Graphics2D> paint) {
        return g -> {
            g.scale(f, f);
            paint.accept(g);
        };
    }

    @SuppressWarnings("unchecked")
    static void defineRound7() {
        double f = 0.75;
        Consumer<Graphics2D>[] icons = new Consumer[]{(Consumer<Graphics2D>) BuildSoulTreeSprites::devastation,
                (Consumer<Graphics2D>) BuildSoulTreeSprites::cleave, (Consumer<Graphics2D>) BuildSoulTreeSprites::lifesteal,
                (Consumer<Graphics2D>) BuildSoulTreeSprites::tempest, (Consumer<Graphics2D>) BuildSoulTreeSprites::ricochet,
                (Consumer<Graphics2D>) BuildSoulTreeSprites::executioner, (Consumer<Graphics2D>) BuildSoulTreeSprites::siphon,
                (Consumer<Graphics2D>) BuildSoulTreeSprites::surge, (Consumer<Graphics2D>) BuildSoulTreeSprites::ward,
                (Consumer<Graphics2D>) BuildSoulTreeSprites::precision, (Consumer<Graphics2D>) BuildSoulTreeSprites::harvest,
                (Consumer<Graphics2D>) BuildSoulTreeSprites::resonance};
        String[] names = {"devastation", "cleave", "lifesteal", "tempest", "ricochet", "executioner", "siphon", "surge", "ward",
                "precision", "harvest", "resonance"};
        for (int i = 0; i < icons.length; i++)
            add(names[i] + "_24", 24, 24, WELL, 0.5, scaled(f, icons[i]));
        add("rank_track_39", 39, 39, BG_TREE, 0.5, scaled(f, g -> arc(g, 0, 360, new Color(0x2a2620), new Color(0x0a0908), false)));
        for (int r = 0; r < 5; r++) {
            int seg = r;
            add("rank_arc_39_" + (r + 1), 39, 39, BG_TREE, 0.5,
                    scaled(f, g -> arc(g, 90 - seg * 72 - 4, -64, new Color(0x2fe8f0), new Color(0x0c5a60), true)));
        }
        add("max_halo_45", 45, 45, BG_TREE, 0.04, scaled(f, BuildSoulTreeSprites::maxHalo));
        add("select_ring_45", 45, 45, BG_TREE, 0.04, scaled(f, g -> {
            g.setPaint(new RadialGradientPaint(new Point2D.Double(30, 30), 30, new float[]{0.70f, 0.82f, 0.9f, 1f},
                    new Color[]{new Color(47, 232, 240, 0), new Color(120, 245, 250, 230), new Color(47, 210, 216, 110),
                            new Color(47, 210, 216, 0)}));
            g.fill(new Ellipse2D.Double(0, 0, 60, 60));
        }));
        int[][] metals = {{0x5c3a1c, 0x9a6634, 0xd29a5c}, {0x0d4f55, 0x1fa7b5, 0x8ff6fa}, {0x7a5212, 0xd9a521, 0xfff0b0}};
        String[] metalNames = {"bronze", "lit", "gold"};
        for (int m = 0; m < 3; m++) {
            int[] metal = metals[m];
            add("socket_33_" + metalNames[m], 33, 33, BG_TREE, 0.5, scaled(f, g -> socket(g, metal, false)));
            add("socket_33_" + metalNames[m] + "_hover", 33, 33, BG_TREE, 0.5, scaled(f, g -> socket(g, metal, true)));
        }
        add("dim_disc_26", 26, 26, WELL, 0.5, g -> {
            g.setColor(new Color(0x050505));
            g.fill(new Ellipse2D.Double(0, 0, 26, 26));
        });
        add("pedestal", 92, 22, BG_TREE, 0.04, BuildSoulTreeSprites::pedestal);
        add("arrow_left", 18, 20, BG_TREE, 0.5, g -> stageArrow(g, true, false));
        add("arrow_left_hover", 18, 20, BG_TREE, 0.5, g -> stageArrow(g, true, true));
        add("arrow_right", 18, 20, BG_TREE, 0.5, g -> stageArrow(g, false, false));
        add("arrow_right_hover", 18, 20, BG_TREE, 0.5, g -> stageArrow(g, false, true));
    }

    /** Soul Precision: a gold crosshair over a soul-cyan ring. */
    static void precision(Graphics2D g) {
        g.setStroke(new BasicStroke(2.2f));
        g.setColor(new Color(0x1fa7b5));
        g.draw(new Ellipse2D.Double(4, 4, 24, 24));
        g.setStroke(new BasicStroke(1.2f));
        g.setColor(new Color(0x8ff6fa));
        g.draw(new Ellipse2D.Double(9, 9, 14, 14));
        g.setColor(new Color(0xffc35a));
        g.fill(new Rectangle2D.Double(15, 1, 2, 9));
        g.fill(new Rectangle2D.Double(15, 22, 2, 9));
        g.fill(new Rectangle2D.Double(1, 15, 9, 2));
        g.fill(new Rectangle2D.Double(22, 15, 9, 2));
        Path2D d = new Path2D.Double();
        d.moveTo(16, 12.5);
        d.lineTo(19.5, 16);
        d.lineTo(16, 19.5);
        d.lineTo(12.5, 16);
        d.closePath();
        g.setColor(new Color(0xfff0b0));
        g.fill(d);
    }

    /** Soul Harvest: a reaping crescent with a freed soul rising from it. */
    static void harvest(Graphics2D g) {
        Area crescent = new Area(new Ellipse2D.Double(3, 5, 26, 26));
        crescent.subtract(new Area(new Ellipse2D.Double(8, 1, 26, 26)));
        g.setPaint(new GradientPaint(3, 30, new Color(0x3a0f6a), 20, 6, new Color(0xd8b8ff)));
        g.fill(crescent);
        g.setStroke(new BasicStroke(0.9f));
        g.setColor(new Color(0x14062a));
        g.draw(crescent);
        g.setPaint(new RadialGradientPaint(new Point2D.Double(21, 12), 7, new float[]{0f, 1f},
                new Color[]{new Color(0xffffff), new Color(0x2fd2d8)}));
        g.fill(new Ellipse2D.Double(16, 7, 10, 10));
        g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(0x8ff6fa));
        g.draw(new Arc2D.Double(18, 1, 8, 8, 200, 140, Arc2D.OPEN));
    }

    /** Soul Resonance: a gold core sending out three widening soul waves. */
    static void resonance(Graphics2D g) {
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Color[] waves = {new Color(0x8ff6fa), new Color(0x2fd2d8), new Color(0x1f8a95)};
        for (int i = 0; i < 3; i++) {
            double rr = 7 + i * 4.5;
            g.setColor(waves[i]);
            g.draw(new Arc2D.Double(16 - rr, 16 - rr, 2 * rr, 2 * rr, -50, 100, Arc2D.OPEN));
            g.draw(new Arc2D.Double(16 - rr, 16 - rr, 2 * rr, 2 * rr, 130, 100, Arc2D.OPEN));
        }
        g.setPaint(new RadialGradientPaint(new Point2D.Double(16, 16), 6, new float[]{0f, 1f},
                new Color[]{new Color(0xfff6d0), new Color(0xd9a521)}));
        g.fill(new Ellipse2D.Double(11, 11, 10, 10));
    }

    /** The weapon dais: a dark stone disc seen from above with a soft soul-teal rim light and a shadow under it. */
    static void pedestal(Graphics2D g) {
        g.setPaint(new RadialGradientPaint(new Point2D.Double(46, 14), 46, new float[]{0f, 1f},
                new Color[]{new Color(0, 0, 0, 150), new Color(0, 0, 0, 0)}));
        g.fill(new Ellipse2D.Double(0, 6, 92, 16));
        g.setPaint(new GradientPaint(0, 8, new Color(0x2a2f34), 0, 18, new Color(0x121518)));
        g.fill(new Ellipse2D.Double(8, 6, 76, 12));
        g.setPaint(new GradientPaint(0, 3, new Color(0x3a4148), 0, 13, new Color(0x1e2328)));
        g.fill(new Ellipse2D.Double(8, 3, 76, 11));
        g.setStroke(new BasicStroke(0.9f));
        g.setColor(new Color(47, 210, 216, 200));
        g.draw(new Arc2D.Double(8.5, 3.5, 75, 10, 15, 150, Arc2D.OPEN));
        g.setColor(new Color(47, 210, 216, 90));
        g.draw(new Arc2D.Double(8.5, 3.5, 75, 10, 180, 180, Arc2D.OPEN));
    }

    /** A stage arrow: a gold chevron on a dark round stud, brighter on hover. */
    static void stageArrow(Graphics2D g, boolean left, boolean hover) {
        g.setPaint(new RadialGradientPaint(new Point2D.Double(9, 9), 10, new float[]{0f, 1f},
                new Color[]{new Color(hover ? 0x3a3428 : 0x2a251d), new Color(0x0d0b09)}));
        g.fill(new Ellipse2D.Double(0.5, 1.5, 17, 17));
        g.setStroke(new BasicStroke(1f));
        g.setColor(new Color(hover ? 0xd9a521 : 0x7a5212));
        g.draw(new Ellipse2D.Double(0.5, 1.5, 17, 17));
        Path2D c = new Path2D.Double();
        double sg = left ? -1 : 1;
        c.moveTo(9 - sg * 2.5, 5.5);
        c.lineTo(9 + sg * 3, 10);
        c.lineTo(9 - sg * 2.5, 14.5);
        g.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(hover ? 0xfff0b0 : 0xffc35a));
        g.draw(c);
    }

    // ---- talent icons (32x32) ----

    static void devastation(Graphics2D g) {
        burst(g, 16, 16, 15, 6.5, 8, new Color(0xffd27a), new Color(0xc0281e));
        sword(g, 16, 16, 45);
        sword(g, 16, 16, -45);
    }

    static void cleave(Graphics2D g) {
        // a broad crescent slash (white edge, red trail) with the splash flung off its tip
        Area crescent = new Area(new Ellipse2D.Double(2, 2, 28, 28));
        crescent.subtract(new Area(new Ellipse2D.Double(-3, -6, 30, 30)));
        g.setPaint(new GradientPaint(4, 30, new Color(0x7a120c), 30, 8, new Color(0xfff6f0)));
        g.fill(crescent);
        g.setStroke(new BasicStroke(0.9f));
        g.setColor(new Color(0x2a0604));
        g.draw(crescent);
        g.setColor(new Color(0xe0402a));
        g.fill(new Ellipse2D.Double(25, 4, 4.5, 4.5));
        g.fill(new Ellipse2D.Double(28, 11, 3.2, 3.2));
        g.fill(new Ellipse2D.Double(20, 1, 3, 3));
        g.setColor(new Color(0xffd2c8));
        g.fill(new Ellipse2D.Double(26, 5, 1.6, 1.6));
    }

    static void lifesteal(Graphics2D g) {
        Path2D drop = new Path2D.Double();
        drop.moveTo(16, 2);
        drop.curveTo(20, 10, 27, 15, 27, 21);
        drop.curveTo(27, 27.5, 22, 31, 16, 31);
        drop.curveTo(10, 31, 5, 27.5, 5, 21);
        drop.curveTo(5, 15, 12, 10, 16, 2);
        g.setPaint(new RadialGradientPaint(new Point2D.Double(13, 18), 15, new float[]{0f, 0.6f, 1f},
                new Color[]{new Color(0xff6a5a), new Color(0xc0281e), new Color(0x5a0c08)}));
        g.fill(drop);
        g.setStroke(new BasicStroke(1.2f));
        g.setColor(new Color(0x2a0604));
        g.draw(drop);
        // heart of light inside
        Path2D heart = heart(16, 22, 8);
        g.setColor(new Color(0xffd2c8));
        g.fill(heart);
        g.setColor(new Color(0xffffff));
        g.fill(new Ellipse2D.Double(10, 13, 3, 4));
    }

    static void tempest(Graphics2D g) {
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Color[] wind = {new Color(0x3ddc5a), new Color(0x2fd2d8), new Color(0x9af5b0)};
        for (int i = 0; i < 3; i++) {
            g.setColor(wind[i]);
            g.draw(new Arc2D.Double(2 + i * 3, 9 + i * 5, 14, 9, 90, 220, Arc2D.OPEN));
        }
        arrow(g, 5, 27, 29, 3, new Color(0xe8e4d8), new Color(0x3ddc5a));
    }

    static void ricochet(Graphics2D g) {
        g.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D path = new Path2D.Double();
        path.moveTo(2, 28);
        path.lineTo(11, 9);
        path.lineTo(20, 24);
        path.lineTo(28, 6);
        g.setColor(new Color(0x1f8a3a));
        g.draw(path);
        g.setStroke(new BasicStroke(1f));
        g.setColor(new Color(0xb8ffc8));
        g.draw(path);
        spark(g, 11, 9, new Color(0xfff2a0));
        spark(g, 20, 24, new Color(0xfff2a0));
        arrowHead(g, 20, 24, 28, 6, new Color(0xe8e4d8));
    }

    static void executioner(Graphics2D g) {
        g.setStroke(new BasicStroke(2f));
        g.setColor(new Color(0xc0281e));
        g.draw(new Ellipse2D.Double(3, 3, 26, 26));
        g.fill(new Rectangle2D.Double(15, 0, 2, 7));
        g.fill(new Rectangle2D.Double(15, 25, 2, 7));
        g.fill(new Rectangle2D.Double(0, 15, 7, 2));
        g.fill(new Rectangle2D.Double(25, 15, 7, 2));
        skull(g, 16, 16, 0.75, new Color(0xe8e0cc), new Color(0x1a0a08));
    }

    static void siphon(Graphics2D g) {
        // the draining vortex: arms spiralling in, purple outside to cyan at the core
        for (int arm = 0; arm < 3; arm++) {
            Path2D p = new Path2D.Double();
            for (int t = 0; t <= 40; t++) {
                double a = Math.toRadians(arm * 120 + t * 9), r = 15 - t * 0.3;
                double x = 16 + Math.cos(a) * r, y = 16 + Math.sin(a) * r;
                if (t == 0) p.moveTo(x, y); else p.lineTo(x, y);
            }
            g.setStroke(new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setPaint(new RadialGradientPaint(new Point2D.Double(16, 16), 16, new float[]{0f, 1f},
                    new Color[]{new Color(0x8ff6fa), new Color(0x7a2ad8)}));
            g.draw(p);
        }
        g.setPaint(new RadialGradientPaint(new Point2D.Double(16, 16), 8, new float[]{0f, 1f},
                new Color[]{new Color(0x2fd2d8), new Color(0x0e1214)}));
        g.fill(new Ellipse2D.Double(8, 8, 16, 16));
        skull(g, 16, 16, 0.45, new Color(0xc8ffff), new Color(0x0d3a40));
    }

    static void surge(Graphics2D g) {
        g.setStroke(new BasicStroke(1.6f));
        g.setColor(new Color(0x5a2ad8));
        g.draw(new Ellipse2D.Double(1, 1, 30, 30));
        g.setColor(new Color(0x3a8aff));
        g.draw(new Ellipse2D.Double(5, 5, 22, 22));
        burst(g, 16, 16, 13, 4, 6, new Color(0xffffff), new Color(0x4a6aff));
        g.setColor(new Color(0xd8b8ff));
        for (int i = 0; i < 6; i++) {
            double a = Math.toRadians(30 + i * 60);
            double x = 16 + Math.cos(a) * 12.5, y = 16 + Math.sin(a) * 12.5;
            g.fill(new Rectangle2D.Double(x - 1, y - 1, 2.2, 2.2));
        }
        g.setColor(new Color(0xffffff));
        g.fill(new Ellipse2D.Double(13.5, 13.5, 5, 5));
    }

    static void ward(Graphics2D g) {
        // wings
        for (int side = -1; side <= 1; side += 2) {
            for (int f = 0; f < 3; f++) {
                Path2D wing = new Path2D.Double();
                double bx = 16 + side * 7, by = 12 + f * 4;
                wing.moveTo(bx, by);
                wing.curveTo(bx + side * 6, by - 6, bx + side * 9, by - 4, bx + side * 9.5, by - 1 - f);
                wing.curveTo(bx + side * 6, by + 1, bx + side * 3, by + 3, bx, by + 3);
                wing.closePath();
                g.setColor(f == 0 ? new Color(0xfff6d0) : f == 1 ? new Color(0xd8f8ff) : new Color(0x9ad8e0));
                g.fill(wing);
            }
        }
        Path2D shield = new Path2D.Double();
        shield.moveTo(16, 4);
        shield.lineTo(25, 7.5);
        shield.curveTo(25, 18, 22, 25, 16, 30);
        shield.curveTo(10, 25, 7, 18, 7, 7.5);
        shield.closePath();
        g.setPaint(new GradientPaint(7, 4, new Color(0x2fd2d8), 25, 30, new Color(0x0b4a50)));
        g.fill(shield);
        g.setStroke(new BasicStroke(1.8f));
        g.setColor(new Color(0xffc35a));
        g.draw(shield);
        // the aegis rune
        g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(0xfff6d0));
        g.draw(new Line2D.Double(16, 9, 16, 24));
        g.draw(new Line2D.Double(12, 13.5, 20, 13.5));
        g.draw(new Arc2D.Double(12.5, 16, 7, 6, 180, 180, Arc2D.OPEN));
    }

    // ---- frames & ui ----

    /** A metal ring socket (44x44): bevelled ring, dark well; hover adds a soft outer halo and brightens the metal. */
    static void socket(Graphics2D g, int[] metal, boolean hover) {
        if (hover) {
            g.setPaint(new RadialGradientPaint(new Point2D.Double(22, 22), 22, new float[]{0.72f, 0.86f, 1f},
                    new Color[]{new Color(metal[2]), new Color(metal[1]), new Color(0, 0, 0, 0)}));
            g.fill(new Ellipse2D.Double(0, 0, 44, 44));
        }
        Color dark = new Color(metal[0]), mid = new Color(metal[1]), light = new Color(metal[2]);
        if (hover) {
            dark = mid;
            mid = light;
        }
        g.setPaint(new GradientPaint(6, 4, light, 38, 40, dark));
        g.fill(new Ellipse2D.Double(2, 2, 40, 40));
        g.setPaint(new GradientPaint(6, 4, dark, 38, 40, mid));
        g.fill(new Ellipse2D.Double(4.5, 4.5, 35, 35));
        g.setColor(new Color(WELL));
        g.fill(new Ellipse2D.Double(6, 6, 32, 32));
        g.setStroke(new BasicStroke(0.8f));
        g.setColor(new Color(0x050505));
        g.draw(new Ellipse2D.Double(2, 2, 40, 40));
        // four rivets
        g.setColor(light);
        for (int i = 0; i < 4; i++) {
            double a = Math.toRadians(45 + i * 90);
            g.fill(new Ellipse2D.Double(22 + Math.cos(a) * 18.5 - 1.2, 22 + Math.sin(a) * 18.5 - 1.2, 2.4, 2.4));
        }
    }

    /** A ring arc in the 52x52 rank meter (radius 23..26.5), from startDeg spanning extentDeg. */
    static void arc(Graphics2D g, double start, double extent, Color core, Color edge, boolean glow) {
        Area ring = new Area(new Ellipse2D.Double(0.5, 0.5, 51, 51));
        ring.subtract(new Area(new Ellipse2D.Double(5, 5, 42, 42)));
        Area slice = new Area(new Arc2D.Double(-10, -10, 72, 72, start, extent, Arc2D.PIE));
        if (Math.abs(extent) < 360)
            ring.intersect(slice);
        g.setColor(edge);
        g.fill(ring);
        Area inner = new Area(new Ellipse2D.Double(1.8, 1.8, 48.4, 48.4));
        inner.subtract(new Area(new Ellipse2D.Double(3.7, 3.7, 44.6, 44.6)));
        if (Math.abs(extent) < 360)
            inner.intersect(slice);
        g.setColor(core);
        g.fill(inner);
        if (glow) {
            Area hot = new Area(new Ellipse2D.Double(2.4, 2.4, 47.2, 47.2));
            hot.subtract(new Area(new Ellipse2D.Double(3.1, 3.1, 45.8, 45.8)));
            hot.intersect(slice);
            g.setColor(new Color(0xe8ffff));
            g.fill(hot);
        }
    }

    static void maxHalo(Graphics2D g) {
        g.setPaint(new RadialGradientPaint(new Point2D.Double(30, 30), 30, new float[]{0.70f, 0.82f, 0.9f, 1f},
                new Color[]{new Color(255, 195, 90, 0), new Color(255, 215, 120, 230), new Color(255, 170, 60, 120),
                        new Color(255, 150, 40, 0)}));
        g.fill(new Ellipse2D.Double(0, 0, 60, 60));
    }

    /** The Rank Up rune stone: a bevelled tablet with chamfered corners and a bright rim. */
    static void rune(Graphics2D g, Color face, Color dark, Color light, boolean hover) {
        rune(g, 46, face, dark, light, hover);
    }

    /** The rune stone at any width (46 = the original 24604-24606; 62 = the round 3 "+ Rank Up" runes). */
    static void rune(Graphics2D g, int w, Color face, Color dark, Color light, boolean hover) {
        Path2D p = new Path2D.Double();
        p.moveTo(4, 0.5);
        p.lineTo(w - 4, 0.5);
        p.lineTo(w - 0.5, 4);
        p.lineTo(w - 0.5, 14);
        p.lineTo(w - 4, 17.5);
        p.lineTo(4, 17.5);
        p.lineTo(0.5, 14);
        p.lineTo(0.5, 4);
        p.closePath();
        g.setPaint(new GradientPaint(0, 0, face, 0, 18, dark));
        g.fill(p);
        g.setStroke(new BasicStroke(1f));
        g.setColor(light);
        g.draw(p);
        g.setColor(new Color(dark.getRed() / 2, dark.getGreen() / 2, dark.getBlue() / 2));
        g.draw(new Rectangle2D.Double(3, 3, w - 6, 12));
        g.setPaint(new GradientPaint(0, 3, face, 0, 15, new Color(face.getRed() * 7 / 10, face.getGreen() * 7 / 10,
                face.getBlue() * 7 / 10)));
        g.fill(new Rectangle2D.Double(3.5, 3.5, w - 7, 11));
        if (hover) {
            g.setColor(new Color(255, 255, 255, 70));
            g.fill(new Rectangle2D.Double(3.5, 3.5, w - 7, 4));
        }
    }

    static void weaponGlow(Graphics2D g) {
        g.setPaint(new RadialGradientPaint(new Rectangle2D.Double(0, 0, 160, 60), new float[]{0f, 0.35f, 0.7f, 1f},
                new Color[]{new Color(47, 210, 216, 190), new Color(31, 167, 181, 120), new Color(20, 90, 110, 50),
                        new Color(0, 0, 0, 0)}, MultipleGradientPaint.CycleMethod.NO_CYCLE));
        g.fill(new Ellipse2D.Double(0, 0, 160, 60));
        // a pedestal of light the weapon floats over
        g.setPaint(new RadialGradientPaint(new Rectangle2D.Double(40, 46, 80, 12), new float[]{0f, 1f},
                new Color[]{new Color(200, 255, 255, 230), new Color(0, 0, 0, 0)}, MultipleGradientPaint.CycleMethod.NO_CYCLE));
        g.fill(new Ellipse2D.Double(40, 46, 80, 12));
    }

    static void gem(Graphics2D g) {
        Path2D d = new Path2D.Double();
        d.moveTo(8, 0.5);
        d.lineTo(15.5, 6);
        d.lineTo(8, 15.5);
        d.lineTo(0.5, 6);
        d.closePath();
        g.setPaint(new GradientPaint(0, 0, new Color(0x8ff6fa), 16, 16, new Color(0x0b4a50)));
        g.fill(d);
        g.setColor(new Color(0x2fd2d8));
        Path2D facet = new Path2D.Double();
        facet.moveTo(4, 6);
        facet.lineTo(12, 6);
        facet.lineTo(8, 14);
        facet.closePath();
        g.fill(facet);
        g.setColor(new Color(0xe8ffff));
        Path2D top = new Path2D.Double();
        top.moveTo(8, 1.5);
        top.lineTo(12, 6);
        top.lineTo(4, 6);
        top.closePath();
        g.fill(top);
        g.setStroke(new BasicStroke(0.8f));
        g.setColor(new Color(0x05282c));
        g.draw(d);
    }

    /** An aura orb: a lit sphere with swirling wisps (or a dark, flat silhouette). */
    static void orb(Graphics2D g, int[] c, boolean lit) {
        g.setPaint(new RadialGradientPaint(new Point2D.Double(11, 10), 15, new float[]{0f, 0.45f, 1f},
                new Color[]{new Color(c[2]), new Color(c[1]), new Color(c[0])}));
        g.fill(new Ellipse2D.Double(1, 1, 26, 26));
        if (lit) {
            g.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(c[2]));
            g.draw(new Arc2D.Double(5, 6, 18, 16, 200, 140, Arc2D.OPEN));
            g.draw(new Arc2D.Double(8, 9, 14, 12, 20, 140, Arc2D.OPEN));
            g.setColor(Color.WHITE);
            g.fill(new Ellipse2D.Double(7, 6, 4, 3));
        }
        g.setStroke(new BasicStroke(1f));
        g.setColor(new Color(lit ? c[0] : 0x0a0a0a));
        g.draw(new Ellipse2D.Double(1, 1, 26, 26));
    }

    static void lock(Graphics2D g) {
        g.setStroke(new BasicStroke(1.6f));
        g.setColor(new Color(0xb8b0a0));
        g.draw(new Arc2D.Double(2.5, 0.8, 5, 7, 0, 180, Arc2D.OPEN));
        g.draw(new Line2D.Double(2.5, 4.3, 2.5, 6));
        g.draw(new Line2D.Double(7.5, 4.3, 7.5, 6));
        g.setPaint(new GradientPaint(0, 5, new Color(0xffc35a), 0, 12, new Color(0x7a5212)));
        g.fill(new RoundRectangle2D.Double(0.5, 5.5, 9, 6, 2, 2));
        g.setColor(new Color(0x2a1a06));
        g.fill(new Rectangle2D.Double(4.4, 7.4, 1.4, 2.6));
    }

    static void pill(Graphics2D g, Color face, Color dark, Color light) {
        RoundRectangle2D r = new RoundRectangle2D.Double(0.5, 0.5, 61, 13, 7, 7);
        g.setPaint(new GradientPaint(0, 0, face, 0, 14, dark));
        g.fill(r);
        g.setStroke(new BasicStroke(1f));
        g.setColor(light);
        g.draw(r);
    }

    // ---- drawing helpers ----

    static void burst(Graphics2D g, double cx, double cy, double outer, double inner, int points, Color hot, Color cold) {
        Path2D star = new Path2D.Double();
        for (int i = 0; i < points * 2; i++) {
            double a = Math.PI * i / points - Math.PI / 2, r = i % 2 == 0 ? outer : inner;
            double x = cx + Math.cos(a) * r, y = cy + Math.sin(a) * r;
            if (i == 0) star.moveTo(x, y); else star.lineTo(x, y);
        }
        star.closePath();
        g.setPaint(new RadialGradientPaint(new Point2D.Double(cx, cy), (float) outer, new float[]{0f, 1f}, new Color[]{hot, cold}));
        g.fill(star);
    }

    static void sword(Graphics2D g, double cx, double cy, double deg) {
        AffineTransform old = g.getTransform();
        g.rotate(Math.toRadians(deg), cx, cy);
        Path2D blade = new Path2D.Double();
        blade.moveTo(cx, cy - 14);
        blade.lineTo(cx + 2, cy - 11);
        blade.lineTo(cx + 2, cy + 6);
        blade.lineTo(cx - 2, cy + 6);
        blade.lineTo(cx - 2, cy - 11);
        blade.closePath();
        g.setPaint(new GradientPaint((float) cx - 2, 0, new Color(0xffffff), (float) cx + 2, 0, new Color(0x8a8f96)));
        g.fill(blade);
        g.setStroke(new BasicStroke(0.8f));
        g.setColor(new Color(0x202428));
        g.draw(blade);
        g.setColor(new Color(0xffc35a));
        g.fill(new Rectangle2D.Double(cx - 5, cy + 6, 10, 2));
        g.setColor(new Color(0x5a3a1c));
        g.fill(new Rectangle2D.Double(cx - 1.2, cy + 8, 2.4, 5));
        g.setColor(new Color(0xffc35a));
        g.fill(new Ellipse2D.Double(cx - 1.8, cy + 12.5, 3.6, 3.6));
        g.setTransform(old);
    }

    static Path2D heart(double cx, double cy, double s) {
        Path2D h = new Path2D.Double();
        h.moveTo(cx, cy + s * 0.45);
        h.curveTo(cx - s * 0.9, cy - s * 0.1, cx - s * 0.45, cy - s * 0.75, cx, cy - s * 0.3);
        h.curveTo(cx + s * 0.45, cy - s * 0.75, cx + s * 0.9, cy - s * 0.1, cx, cy + s * 0.45);
        h.closePath();
        return h;
    }

    static void arrow(Graphics2D g, double x1, double y1, double x2, double y2, Color shaft, Color fletch) {
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(0x6a4a2a));
        g.draw(new Line2D.Double(x1, y1, x2, y2));
        double a = Math.atan2(y2 - y1, x2 - x1);
        g.setColor(fletch);
        for (int s = -1; s <= 1; s += 2) {
            Path2D f = new Path2D.Double();
            double bx = x1 + Math.cos(a) * 3, by = y1 + Math.sin(a) * 3;
            f.moveTo(bx, by);
            f.lineTo(bx + Math.cos(a + s * 2.4) * 5, by + Math.sin(a + s * 2.4) * 5);
            f.lineTo(bx + Math.cos(a) * 5, by + Math.sin(a) * 5);
            f.closePath();
            g.fill(f);
        }
        arrowHead(g, x1, y1, x2, y2, shaft);
    }

    static void arrowHead(Graphics2D g, double x1, double y1, double x2, double y2, Color c) {
        double a = Math.atan2(y2 - y1, x2 - x1);
        Path2D head = new Path2D.Double();
        head.moveTo(x2 + Math.cos(a) * 2, y2 + Math.sin(a) * 2);
        head.lineTo(x2 + Math.cos(a + 2.6) * 7, y2 + Math.sin(a + 2.6) * 7);
        head.lineTo(x2 + Math.cos(a - 2.6) * 7, y2 + Math.sin(a - 2.6) * 7);
        head.closePath();
        g.setColor(c);
        g.fill(head);
        g.setStroke(new BasicStroke(0.7f));
        g.setColor(new Color(0x202428));
        g.draw(head);
    }

    static void spark(Graphics2D g, double x, double y, Color c) {
        burst(g, x, y, 4.5, 1.4, 4, Color.WHITE, c);
    }

    static void skull(Graphics2D g, double cx, double cy, double s, Color bone, Color hole) {
        AffineTransform old = g.getTransform();
        g.translate(cx, cy);
        g.scale(s, s);
        Area head = new Area(new Ellipse2D.Double(-11, -13, 22, 20));
        head.add(new Area(new RoundRectangle2D.Double(-7, 2, 14, 10, 4, 4)));
        g.setColor(bone);
        g.fill(head);
        g.setColor(hole);
        g.fill(new Ellipse2D.Double(-8, -6, 6.5, 7));
        g.fill(new Ellipse2D.Double(1.5, -6, 6.5, 7));
        Path2D nose = new Path2D.Double();
        nose.moveTo(0, 1);
        nose.lineTo(-2, 4.5);
        nose.lineTo(2, 4.5);
        nose.closePath();
        g.fill(nose);
        for (int t = -1; t <= 1; t++)
            g.fill(new Rectangle2D.Double(t * 3.2 - 0.8, 7, 1.6, 4));
        g.setTransform(old);
    }

    // ------------------------------------------------------------------ raster

    /** Paints at 4x, box-downsamples, drops pixels under minCover, blends the rest onto bg, quantises to 255 colours. */
    static int[] render(int w, int h, int bg, double minCover, Consumer<Graphics2D> paint) {
        int k = 4;
        BufferedImage big = new BufferedImage(w * k, h * k, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = big.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.scale(k, k);
        paint.accept(g);
        g.dispose();
        int br = bg >> 16 & 255, bgG = bg >> 8 & 255, bb = bg & 255;
        int[] px = new int[w * h];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                double a = 0, r = 0, gg = 0, b = 0;
                for (int dy = 0; dy < k; dy++)
                    for (int dx = 0; dx < k; dx++) {
                        int c = big.getRGB(x * k + dx, y * k + dy);
                        double ca = (c >>> 24) / 255.0;
                        a += ca;
                        r += (c >> 16 & 255) * ca;
                        gg += (c >> 8 & 255) * ca;
                        b += (c & 255) * ca;
                    }
                a /= k * k;
                r /= k * k;
                gg /= k * k;
                b /= k * k;
                if (a < minCover)
                    continue;
                int cr = (int) Math.round(r + br * (1 - a)), cg = (int) Math.round(gg + bgG * (1 - a)),
                        cb = (int) Math.round(b + bb * (1 - a));
                px[y * w + x] = nonZero(clamp(cr) << 16 | clamp(cg) << 8 | clamp(cb));
            }
        return quantise(px);
    }

    static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    /** 0 is the transparent index: real black becomes 0x010101. */
    static int nonZero(int c) {
        return c == 0 ? 0x010101 : c;
    }

    static int[] quantise(int[] px) {
        for (int bits = 0; bits <= 5; bits++) {
            int mask = (0xff << bits) & 0xff;
            int m = mask << 16 | mask << 8 | mask;
            Set<Integer> colours = new HashSet<>();
            int[] out = new int[px.length];
            for (int i = 0; i < px.length; i++) {
                if (px[i] == 0)
                    continue;
                out[i] = nonZero(px[i] & m);
                colours.add(out[i]);
            }
            if (colours.size() <= 255)
                return out;
        }
        throw new IllegalStateException("too many colours");
    }

    /** A sheet of every sprite at 3x on its own background, with its id. */
    static void preview(File out) throws Exception {
        int cell = 200, cols = 6, rows = (SPRITES.size() + cols - 1) / cols;
        BufferedImage img = new BufferedImage(cols * cell, rows * cell, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(0x2a2a2a));
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        for (int i = 0; i < SPRITES.size(); i++) {
            Sprite s = SPRITES.get(i);
            int ox = (i % cols) * cell, oy = (i / cols) * cell;
            int scale = Math.max(1, Math.min(3, (cell - 30) / Math.max(s.w, s.h)));
            g.setColor(new Color(s.bg));
            g.fillRect(ox + 4, oy + 18, cell - 8, cell - 22);
            for (int y = 0; y < s.h; y++)
                for (int x = 0; x < s.w; x++) {
                    int c = s.px[y * s.w + x];
                    if (c == 0)
                        continue;
                    g.setColor(new Color(c));
                    g.fillRect(ox + 10 + x * scale, oy + 24 + y * scale, scale, scale);
                }
            g.setColor(Color.WHITE);
            g.drawString(s.id + " " + s.name, ox + 4, oy + 14);
        }
        g.dispose();
        ImageIO.write(img, "png", out);
        System.out.println("preview " + out);
    }
}

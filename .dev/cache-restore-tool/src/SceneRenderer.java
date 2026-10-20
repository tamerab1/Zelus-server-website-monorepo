import net.runelite.cache.definitions.ModelDefinition;

import java.awt.image.BufferedImage;
import java.util.Map;

// Read-only helper (2026-10-20, region design): a small z-buffered software rasterizer for previewing map scenes --
// terrain quads and loc models in world units (128 per tile, y up is negative as in the client), perspective camera
// at a yaw / pitch / distance around a focus point, directional light, flat shading per face. Textured faces use the
// texture's average colour (good enough to judge a layout and its palette). Not the client's renderer: no fog,
// no per-vertex lighting, no alpha priorities; translucent faces (alpha) are blended over what is behind them.
public class SceneRenderer {
    final int w, h;
    final int[] rgb;
    final float[] depth;
    // camera
    double cx, cy, cz, yaw, pitch, dist, fov = 1.15;
    double[] light = {-0.45, -0.75, -0.48};

    SceneRenderer(int w, int h, int sky) {
        this.w = w;
        this.h = h;
        rgb = new int[w * h];
        depth = new float[w * h];
        java.util.Arrays.fill(rgb, sky);
        java.util.Arrays.fill(depth, Float.MAX_VALUE);
        double l = Math.sqrt(light[0] * light[0] + light[1] * light[1] + light[2] * light[2]);
        for (int i = 0; i < 3; i++) light[i] /= l;
    }

    /** Camera looking at (fx, fy, fz) from yaw (radians, 0 = from the south), pitch (radians down) and distance. */
    void camera(double fx, double fy, double fz, double yawRad, double pitchRad, double distance) {
        cx = fx;
        cy = fy;
        cz = fz;
        yaw = yawRad;
        pitch = pitchRad;
        dist = distance;
    }

    /** World -> camera space (x right, y down, z forward). */
    double[] view(double x, double y, double z) {
        double dx = x - cx, dy = y - cy, dz = z - cz;
        double cosY = Math.cos(yaw), sinY = Math.sin(yaw);
        double x1 = dx * cosY - dz * sinY, z1 = dx * sinY + dz * cosY;
        double cosP = Math.cos(pitch), sinP = Math.sin(pitch);
        double y2 = dy * cosP - z1 * sinP, z2 = dy * sinP + z1 * cosP;
        return new double[]{x1, y2, z2 + dist};
    }

    double[] project(double[] v) {
        double f = (w / 2.0) / Math.tan(fov / 2);
        return new double[]{w / 2.0 + v[0] * f / v[2], h / 2.0 + v[1] * f / v[2], v[2]};
    }

    /** One world-space triangle with a base colour (0xRRGGBB) and alpha (0 opaque .. 255 invisible). */
    void triangle(double[] a, double[] b, double[] c, int colour, int alpha, boolean twoSided) {
        double[] va = view(a[0], a[1], a[2]), vb = view(b[0], b[1], b[2]), vc = view(c[0], c[1], c[2]);
        if (va[2] < 20 || vb[2] < 20 || vc[2] < 20) return;
        // face normal in world space for lighting / culling
        double ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2], wx = c[0] - a[0], wy = c[1] - a[1], wz = c[2] - a[2];
        double nx = uy * wz - uz * wy, ny = uz * wx - ux * wz, nz = ux * wy - uy * wx;
        double nl = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (nl == 0) return;
        nx /= nl; ny /= nl; nz /= nl;
        double[] pa = project(va), pb = project(vb), pc = project(vc);
        double area = (pb[0] - pa[0]) * (pc[1] - pa[1]) - (pb[1] - pa[1]) * (pc[0] - pa[0]);
        if (area == 0) return;
        if (!twoSided && area > 0) return; // client winding: back faces dropped
        double lambert = -(nx * light[0] + ny * light[1] + nz * light[2]);
        if (twoSided) lambert = Math.abs(lambert);
        double shade = 0.62 + 0.55 * Math.max(0, lambert);
        int r = (int) Math.min(255, ((colour >> 16) & 255) * shade), g = (int) Math.min(255, ((colour >> 8) & 255) * shade),
                bl = (int) Math.min(255, (colour & 255) * shade);
        int minX = (int) Math.max(0, Math.floor(Math.min(pa[0], Math.min(pb[0], pc[0]))));
        int maxX = (int) Math.min(w - 1, Math.ceil(Math.max(pa[0], Math.max(pb[0], pc[0]))));
        int minY = (int) Math.max(0, Math.floor(Math.min(pa[1], Math.min(pb[1], pc[1]))));
        int maxY = (int) Math.min(h - 1, Math.ceil(Math.max(pa[1], Math.max(pb[1], pc[1]))));
        double inv = 1.0 / area;
        float a8 = alpha / 255f;
        for (int py = minY; py <= maxY; py++)
            for (int px = minX; px <= maxX; px++) {
                double x = px + 0.5, y = py + 0.5;
                double w0 = ((pb[0] - x) * (pc[1] - y) - (pb[1] - y) * (pc[0] - x)) * inv;
                double w1 = ((pc[0] - x) * (pa[1] - y) - (pc[1] - y) * (pa[0] - x)) * inv;
                double w2 = 1 - w0 - w1;
                if (w0 < 0 || w1 < 0 || w2 < 0) continue;
                float z = (float) (1 / (w0 / pa[2] + w1 / pb[2] + w2 / pc[2]));
                int i = py * w + px;
                if (z >= depth[i]) continue;
                if (alpha > 0) {
                    int o = rgb[i];
                    int rr = (int) (r * (1 - a8) + ((o >> 16) & 255) * a8), gg = (int) (g * (1 - a8) + ((o >> 8) & 255) * a8),
                            bb = (int) (bl * (1 - a8) + (o & 255) * a8);
                    rgb[i] = rr << 16 | gg << 8 | bb;
                    if (alpha < 128) depth[i] = z;
                } else {
                    rgb[i] = r << 16 | g << 8 | bl;
                    depth[i] = z;
                }
            }
    }

    /**
     * A model at world (wx, wy, wz): scaled (128 = 1x) per axis, turned by orientation (0-3, client loc rotation:
     * 90 degrees each, clockwise seen from above), offset in model units, recoloured, textures as average colours.
     */
    void model(ModelDefinition m, double wx, double wy, double wz, int scaleX, int scaleY, int scaleZ, int orientation,
               boolean mirror, Map<Integer, Integer> recolour, Map<Integer, Integer> textureColour, int offX, int offY, int offZ) {
        int n = m.vertexCount;
        double[][] v = new double[n][];
        for (int i = 0; i < n; i++) {
            double x = m.vertexX[i] * scaleX / 128.0, y = m.vertexY[i] * scaleY / 128.0, z = m.vertexZ[i] * scaleZ / 128.0;
            if (mirror) z = -z;
            double rx = x, rz = z;
            switch (orientation & 3) {
                case 1 -> { rx = z; rz = -x; }
                case 2 -> { rx = -x; rz = -z; }
                case 3 -> { rx = -z; rz = x; }
            }
            v[i] = new double[]{wx + rx + offX, wy + y - offY, wz + rz + offZ};
        }
        for (int f = 0; f < m.faceCount; f++) {
            int alpha = m.faceTransparencies == null ? 0 : m.faceTransparencies[f] & 255;
            if (alpha >= 254) continue;
            // render type 2 = a hidden face (roof caps / occluders the client never draws)
            if (m.faceRenderTypes != null && (m.faceRenderTypes[f] & 3) == 2) continue;
            int colour;
            int tex = m.faceTextures != null ? m.faceTextures[f] : -1;
            if (tex >= 0) {
                colour = textureColour != null && textureColour.containsKey(tex) ? textureColour.get(tex) : 0x808080;
            } else {
                int hsl = m.faceColors[f] & 0xffff;
                if (recolour != null && recolour.containsKey(hsl)) hsl = recolour.get(hsl);
                colour = hslToRgb(hsl);
            }
            int a = m.faceIndices1[f], b = m.faceIndices2[f], c = m.faceIndices3[f];
            if (mirror) { int t = b; b = c; c = t; }
            triangle(v[a], v[b], v[c], colour, alpha, false);
        }
    }

    BufferedImage image() {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        img.setRGB(0, 0, w, h, rgb, 0, w);
        return img;
    }

    /** The client's 16-bit HSL (6 hue, 3 saturation, 7 lightness) to RGB, brightness 0.8. */
    static int hslToRgb(int hsl) {
        double hue = ((hsl >> 10) & 63) / 64.0 + 0.0078125, sat = ((hsl >> 7) & 7) / 8.0 + 0.0625, lum = (hsl & 127) / 128.0;
        double r = lum, g = lum, b = lum;
        if (sat != 0) {
            double q = lum < 0.5 ? lum * (1 + sat) : lum + sat - lum * sat, p = 2 * lum - q;
            r = hue2(p, q, hue + 1 / 3.0);
            g = hue2(p, q, hue);
            b = hue2(p, q, hue - 1 / 3.0);
        }
        int ri = (int) (Math.pow(r, 0.8) * 255), gi = (int) (Math.pow(g, 0.8) * 255), bi = (int) (Math.pow(b, 0.8) * 255);
        return Math.min(255, ri) << 16 | Math.min(255, gi) << 8 | Math.min(255, bi);
    }

    static double hue2(double p, double q, double t) {
        if (t < 0) t += 1;
        if (t > 1) t -= 1;
        if (6 * t < 1) return p + (q - p) * 6 * t;
        if (2 * t < 1) return q;
        if (3 * t < 2) return p + (q - p) * (2 / 3.0 - t) * 6;
        return p;
    }
}

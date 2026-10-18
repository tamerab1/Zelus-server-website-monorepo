import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.loaders.ModelLoader;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

// Read-only (2026-10-18, the "new customs" batch): every raw .dat model under a folder, decoded with the real
// ModelLoader. Prints format (trailer bytes), vertices / faces, textured faces, vertex + face skins (a worn model needs
// vertex skins to animate), priority, the bounding box; renders a labelled contact sheet (two yaws per model, scaled to
// fit), so ripped assets can be identified and checked before they go into the cache.
// Usage: InspectRawModelDir <folder> <out.png>
public class InspectRawModelDir {
    public static void main(String[] args) throws Exception {
        List<File> files;
        try (var walk = Files.walk(new File(args[0]).toPath())) {
            files = walk.filter(p -> p.toString().endsWith(".dat")).map(java.nio.file.Path::toFile).sorted().collect(Collectors.toList());
        }
        int cw = 220, ch = 220, cols = 4;
        BufferedImage sheet = new BufferedImage(cols * cw, ((files.size() + cols - 1) / cols) * ch, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        g.setColor(new Color(0x2a2a2a));
        g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        for (int i = 0; i < files.size(); i++) {
            File f = files.get(i);
            byte[] raw = Files.readAllBytes(f.toPath());
            int t1 = raw[raw.length - 2] & 0xff, t2 = raw[raw.length - 1] & 0xff;
            String fmt = t2 == 0xff ? (t1 == 0xfd ? "type3" : t1 == 0xfe ? "type2" : t1 == 0xff ? "type1" : "new?" + t1) : "old";
            ModelDefinition m = new ModelLoader().load(0, raw);
            int tex = 0;
            if (m.faceTextures != null) for (short t : m.faceTextures) if (t != -1) tex++;
            boolean vskins = m.getVertexGroups() != null && Arrays.stream(m.getVertexGroups()).anyMatch(a -> a != null && a.length > 0);
            int minX = 0, maxX = 0, minY = 0, maxY = 0, minZ = 0, maxZ = 0;
            for (int v = 0; v < m.vertexCount; v++) {
                minX = Math.min(minX, m.vertexX[v]); maxX = Math.max(maxX, m.vertexX[v]);
                minY = Math.min(minY, m.vertexY[v]); maxY = Math.max(maxY, m.vertexY[v]);
                minZ = Math.min(minZ, m.vertexZ[v]); maxZ = Math.max(maxZ, m.vertexZ[v]);
            }
            String rel = new File(args[0]).toPath().relativize(f.toPath()).toString().replace('\\', '/');
            System.out.printf("%-48s %-6s v=%-5d f=%-5d tex=%-4d vskins=%-5s fskins=%-5s x[%d,%d] y[%d,%d] z[%d,%d]%n", rel, fmt,
                    m.vertexCount, m.faceCount, tex, vskins, m.packedTransparencyVertexGroups != null, minX, maxX, minY, maxY, minZ, maxZ);
            double ext = Math.max(Math.max(maxX - minX, maxY - minY), maxZ - minZ);
            double s = 420.0 / Math.max(1, ext);
            int cx = (minX + maxX) / 2, cy = (minY + maxY) / 2, cz = (minZ + maxZ) / 2;
            for (int v = 0; v < m.vertexCount; v++) {
                m.vertexX[v] = (int) ((m.vertexX[v] - cx) * s);
                m.vertexY[v] = (int) ((m.vertexY[v] - cy) * s) - 210;
                m.vertexZ[v] = (int) ((m.vertexZ[v] - cz) * s);
            }
            m.faceTextures = null; // the flat preview renderer has no texture path: textured faces show their colour
            BufferedImage a = BuildChampionshipBoard.preview(m, new HashMap<>(), 0.6, 10);
            int ox = (i % cols) * cw, oy = (i / cols) * ch;
            g.drawImage(a, ox, oy + 14, cw, ch - 14, null);
            g.setColor(Color.WHITE);
            g.drawString(rel, ox + 4, oy + 12);
        }
        g.dispose();
        ImageIO.write(sheet, "png", new File(args[1]));
        System.out.println("wrote " + args[1]);
    }
}

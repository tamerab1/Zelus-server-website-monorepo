import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.fs.Store;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;

// Read-only. Renders models side by side into one contact sheet (BuildChampionshipBoard's flat preview renderer,
// models scaled to a common height, labelled with their id). Usage: PreviewModels <cachePath> <out.png> <yaw> <model,...>
public class PreviewModels {
    public static void main(String[] args) throws Exception {
        String[] ids = args[3].split(",");
        double yaw = Double.parseDouble(args[2]);
        int cols = Math.min(4, ids.length), rows = (ids.length + cols - 1) / cols, cw = 300, ch = 300;
        BufferedImage sheet = new BufferedImage(cols * cw, rows * ch, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        try (Store store = new Store(new File(args[0]))) {
            store.load();
            for (int i = 0; i < ids.length; i++) {
                ModelDefinition m = BuildTierPets.model(store, Integer.parseInt(ids[i]));
                int ymin = 0;
                for (int v = 0; v < m.vertexCount; v++) ymin = Math.min(ymin, m.vertexY[v]);
                double s = 420.0 / Math.max(1, -ymin);
                for (int v = 0; v < m.vertexCount; v++) { m.vertexX[v] *= s; m.vertexY[v] *= s; m.vertexZ[v] *= s; }
                BufferedImage img = BuildChampionshipBoard.preview(m, new HashMap<>(), yaw, 10);
                g.drawImage(img, (i % cols) * cw, (i / cols) * ch, cw, ch, null);
                g.setColor(Color.WHITE);
                g.drawString(ids[i], (i % cols) * cw + 6, (i / cols) * ch + 16);
            }
        }
        ImageIO.write(sheet, "png", new File(args[1]));
    }
}

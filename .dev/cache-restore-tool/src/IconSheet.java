import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

// Read-only helper (2026-10-18): lays RenderItemIcon PNGs out on one sheet, 4x nearest-neighbour on the inventory's
// brown with each icon's 36x32 frame outlined, labelled. Usage: IconSheet <out.png> <label=file.png> [...]
public class IconSheet {
    public static void main(String[] args) throws Exception {
        int scale = 4, cw = 36 * scale + 20, ch = 32 * scale + 30, cols = Math.min(5, args.length - 1);
        int rows = (args.length - 1 + cols - 1) / cols;
        BufferedImage sheet = new BufferedImage(cols * cw, rows * ch, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        g.setColor(new Color(0x3e3529));
        g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        for (int i = 1; i < args.length; i++) {
            String[] p = args[i].split("=", 2);
            BufferedImage icon = ImageIO.read(new File(p[1]));
            int x = ((i - 1) % cols) * cw + 10, y = ((i - 1) / cols) * ch + 18;
            g.drawImage(icon, x, y, icon.getWidth() * scale, icon.getHeight() * scale, null);
            g.setColor(new Color(0x6a5a40));
            g.drawRect(x, y, 36 * scale, 32 * scale);
            g.setColor(Color.WHITE);
            g.drawString(p[0], x, y - 4);
        }
        g.dispose();
        ImageIO.write(sheet, "png", new File(args[0]));
        System.out.println("wrote " + args[0]);
    }
}

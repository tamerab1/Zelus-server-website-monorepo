import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

// One-off: nearest-neighbor upscale + checkerboard-under-alpha + red frame-boundary box,
// so tiny 32x36 item icons can actually be inspected visually.
public class UpscaleGrid {
    public static void main(String[] args) throws Exception {
        int scale = 12;
        for (String path : args) {
            File f = new File(path);
            BufferedImage src = ImageIO.read(f);
            int w = src.getWidth(), h = src.getHeight();
            BufferedImage out = new BufferedImage(w * scale, h * scale, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int argb = src.getRGB(x, y);
                    int a = (argb >>> 24);
                    int rgb;
                    boolean checker = ((x / 1) + (y / 1)) % 2 == 0;
                    if (a < 255) {
                        int bg = checker ? 0xFFDDDDDD : 0xFFBBBBBB;
                        float alpha = a / 255f;
                        int sr = (argb >> 16) & 0xFF, sg = (argb >> 8) & 0xFF, sb = argb & 0xFF;
                        int br = (bg >> 16) & 0xFF, bg2 = (bg >> 8) & 0xFF, bb = bg & 0xFF;
                        int r = (int) (sr * alpha + br * (1 - alpha));
                        int g = (int) (sg * alpha + bg2 * (1 - alpha));
                        int b = (int) (sb * alpha + bb * (1 - alpha));
                        rgb = 0xFF000000 | (r << 16) | (g << 8) | b;
                    } else {
                        rgb = 0xFF000000 | (argb & 0xFFFFFF);
                    }
                    for (int dy = 0; dy < scale; dy++)
                        for (int dx = 0; dx < scale; dx++)
                            out.setRGB(x * scale + dx, y * scale + dy, rgb);
                }
            }
            String outPath = path.replace(".png", "_big.png");
            ImageIO.write(out, "PNG", new File(outPath));
            System.out.println(path + " -> " + outPath + " (" + w + "x" + h + " orig)");
        }
    }
}

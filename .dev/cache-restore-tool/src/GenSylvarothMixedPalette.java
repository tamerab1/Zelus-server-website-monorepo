import net.runelite.cache.models.JagexColor;

// One-off: builds a genuinely MULTI-hue recolor (several different target hues, not one wash)
// for Sylvaroth's Ent-based model, preserving each original color's saturation/luminance
// (so shading/contrast is unchanged) while remapping hue per original-color group.
public class GenSylvarothMixedPalette {
    public static void main(String[] args) {
        // {origHue, origSat, origLum, newHue}
        int[][] rows = {
            {16, 3, 28, 40}, // main green -> blue
            {16, 4, 24, 40}, // dark green -> blue
            {14, 3, 28, 40}, // olive green -> blue
            {7, 1, 37, 50},  // bark highlight -> purple
            {7, 1, 30, 50},  // bark -> purple
            {7, 1, 53, 50},  // light bark -> purple
            {6, 1, 43, 5},   // tan -> orange
            {6, 1, 47, 5},   // light tan -> orange
            {6, 2, 39, 5},   // tan -> orange
            {2, 6, 22, 30},  // reddish brown -> teal
            {2, 4, 30, 30},  // brown -> teal
            {9, 2, 84, 58},  // cream highlight -> pink
            {9, 2, 80, 58},  // cream -> pink
        };
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (int[] r : rows) {
            short find = JagexColor.packHSL(r[0], r[1], r[2]);
            short replace = JagexColor.packHSL(r[3], r[1], r[2]);
            if (!first) sb.append(",");
            sb.append(find & 0xFFFF).append(",").append(replace & 0xFFFF);
            first = false;
        }
        System.out.println(sb);
    }
}

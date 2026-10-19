import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.loaders.ModelLoader;

import java.io.File;
import java.nio.file.Files;
import java.util.*;

// Read-only (2026-10-19): face colour histogram (16-bit HSL split into hue / sat / lightness), render types, priorities and
// transparency of a raw model -- to see why a mesh looks flat. Usage: RawColourStats <model.dat>
public class RawColourStats {
    public static void main(String[] args) throws Exception {
        ModelDefinition m = new ModelLoader().load(0, Files.readAllBytes(new File(args[0]).toPath()));
        Map<Integer, Integer> col = new TreeMap<>(), type = new TreeMap<>(), pri = new TreeMap<>();
        for (int f = 0; f < m.faceCount; f++) {
            col.merge(m.faceColors[f] & 0xffff, 1, Integer::sum);
            type.merge(m.faceRenderTypes == null ? 0 : m.faceRenderTypes[f] & 0xff, 1, Integer::sum);
            pri.merge(m.faceRenderPriorities == null ? (int) m.priority : m.faceRenderPriorities[f], 1, Integer::sum);
        }
        System.out.println("faces " + m.faceCount + ", distinct colours " + col.size() + ", render types " + type + ", priorities " + pri
                + ", alpha " + (m.faceTransparencies != null));
        col.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(25).forEach(e -> {
            int c = e.getKey();
            System.out.printf("  %5d  h%2d s%d l%3d  x%d%n", c, c >> 10 & 63, c >> 7 & 7, c & 127, e.getValue());
        });
    }
}

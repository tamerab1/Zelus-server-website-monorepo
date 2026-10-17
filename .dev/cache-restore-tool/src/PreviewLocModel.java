import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.fs.Store;
import javax.imageio.ImageIO;
import java.io.File;
import java.util.HashMap;

// Read-only. Renders a cache model (or the BuildFortuneStatue output for a state) at a given yaw/pitch.
// Usage: PreviewLocModel <cachePath> <out.png> <yaw> <pitch> statue:<DORMANT|CHARGED|BLESSED> | <modelId>
public class PreviewLocModel {
    public static void main(String[] args) throws Exception {
        try (Store store = new Store(new File(args[0]))) {
            store.load();
            ModelDefinition m = args[4].startsWith("statue:")
                    ? BuildFortuneStatue.build(BuildFortuneStatue.State.valueOf(args[4].substring(7)))
                    : BuildTierPets.model(store, Integer.parseInt(args[4]));
            ImageIO.write(BuildChampionshipBoard.preview(m, new HashMap<>(), Double.parseDouble(args[2]), Double.parseDouble(args[3])), "png", new File(args[1]));
        }
    }
}

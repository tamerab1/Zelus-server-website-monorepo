import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.fs.*;

import java.io.File;
import java.util.*;

// Sets the width/height scale (opcodes 4/5, 128 = 100%) of existing spotanims in place, e.g. to turn the stardust
// confetti (4293/4294) into fine dust. Everything else in the record stays byte-identical; checked before writing.
// Usage: verify|apply <cachePath> <spotanim:scale>[,<spotanim:scale>...]
public class SetSpotanimScale {
    public static void main(String[] args) throws Exception {
        boolean apply = args[0].equals("apply");
        try (Store store = new Store(new File(args[1]))) {
            store.load();
            Index index = store.getIndex(IndexType.CONFIGS);
            Archive archive = index.getArchive(ConfigType.SPOTANIM.getId());
            List<byte[]> contents = new ArrayList<>(BuildBossVariants.spotContents(store));
            for (String pair : args[2].split(",")) {
                int id = Integer.parseInt(pair.split(":")[0]), scale = Integer.parseInt(pair.split(":")[1]);
                int slot = BuildBossVariants.spotSlot(store, id);
                byte[] before = contents.get(slot);
                var sd = BuildTierPets.spot(before, id);
                byte[] w = BuildTierPets.spotPut(before, 4, SplicePetify.twoBytes(scale));
                w = BuildTierPets.spotPut(w, 5, SplicePetify.twoBytes(scale));
                var after = BuildTierPets.spot(w, id);
                BuildTierPets.check(after.resizeX == scale && after.resizeY == scale && after.modelId == sd.modelId
                        && after.animationId == sd.animationId && Arrays.equals(after.recolorToReplace, sd.recolorToReplace), "spotanim " + id);
                contents.set(slot, w);
                System.out.printf("spotanim %d: scale %d/%d -> %d/%d%n", id, sd.resizeX, sd.resizeY, scale, scale);
            }
            if (!apply) { System.out.println("VERIFY mode -- not writing."); return; }
            BuildTierPets.writeArchive(store, index, archive, SpliceItemOption.joinChunks(contents));
            System.out.println("APPLY complete. Spotanim archive revision now " + archive.getRevision() + ".");
        }
    }
}

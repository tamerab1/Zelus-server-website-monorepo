import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.NpcDefinition;
import net.runelite.cache.definitions.loaders.NpcLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;
import java.io.File;
import java.util.List;

public class DiffNpcFull {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        int idA = Integer.parseInt(args[1]);
        int idB = Integer.parseInt(args[2]);
        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Archive archive = store.getIndex(IndexType.CONFIGS).getArchive(ConfigType.NPC.getId());
            byte[] decompressed = archive.decompress(storage.loadArchive(archive));
            FileData[] fileData = archive.getFileData();
            List<byte[]> contents = SpliceItemOption.splitChunks(decompressed, fileData.length);
            NpcLoader loader = new NpcLoader();
            int slotA = -1, slotB = -1;
            for (int i = 0; i < fileData.length; i++) {
                if (fileData[i].getId() == idA) slotA = i;
                if (fileData[i].getId() == idB) slotB = i;
            }
            NpcDefinition a = loader.load(idA, contents.get(slotA));
            NpcDefinition b = loader.load(idB, contents.get(slotB));

            System.out.println("=== raw byte length: " + idA + "=" + contents.get(slotA).length + "  " + idB + "=" + contents.get(slotB).length + " ===");

            diff("name", a.name, b.name);
            diff("size", a.size, b.size);
            diff("models", java.util.Arrays.toString(a.models), java.util.Arrays.toString(b.models));
            diff("chatheadModels", java.util.Arrays.toString(a.chatheadModels), java.util.Arrays.toString(b.chatheadModels));
            diff("standingAnimation", a.standingAnimation, b.standingAnimation);
            diff("idleRotateLeftAnimation", a.idleRotateLeftAnimation, b.idleRotateLeftAnimation);
            diff("idleRotateRightAnimation", a.idleRotateRightAnimation, b.idleRotateRightAnimation);
            diff("walkingAnimation", a.walkingAnimation, b.walkingAnimation);
            diff("rotate180Animation", a.rotate180Animation, b.rotate180Animation);
            diff("rotateLeftAnimation", a.rotateLeftAnimation, b.rotateLeftAnimation);
            diff("rotateRightAnimation", a.rotateRightAnimation, b.rotateRightAnimation);
            diff("runAnimation", a.runAnimation, b.runAnimation);
            diff("recolorToFind", java.util.Arrays.toString(a.recolorToFind), java.util.Arrays.toString(b.recolorToFind));
            diff("recolorToReplace", java.util.Arrays.toString(a.recolorToReplace), java.util.Arrays.toString(b.recolorToReplace));
            diff("retextureToFind", java.util.Arrays.toString(a.retextureToFind), java.util.Arrays.toString(b.retextureToFind));
            diff("retextureToReplace", java.util.Arrays.toString(a.retextureToReplace), java.util.Arrays.toString(b.retextureToReplace));
            diff("actions", java.util.Arrays.toString(a.actions), java.util.Arrays.toString(b.actions));
            diff("isMinimapVisible", a.isMinimapVisible, b.isMinimapVisible);
            diff("combatLevel", a.combatLevel, b.combatLevel);
            diff("widthScale", a.widthScale, b.widthScale);
            diff("heightScale", a.heightScale, b.heightScale);
            diff("hasRenderPriority", a.hasRenderPriority, b.hasRenderPriority);
            diff("ambient", a.ambient, b.ambient);
            diff("contrast", a.contrast, b.contrast);
            diff("headIconArchiveIds", java.util.Arrays.toString(a.headIconArchiveIds), java.util.Arrays.toString(b.headIconArchiveIds));
            diff("headIconSpriteIndex", java.util.Arrays.toString(a.headIconSpriteIndex), java.util.Arrays.toString(b.headIconSpriteIndex));
            diff("rotationSpeed", a.rotationSpeed, b.rotationSpeed);
            diff("configs", java.util.Arrays.toString(a.configs), java.util.Arrays.toString(b.configs));
            diff("varbitId", a.varbitId, b.varbitId);
            diff("varpIndex", a.varpIndex, b.varpIndex);
            diff("isInteractable", a.isInteractable, b.isInteractable);
            diff("rotationFlag", a.rotationFlag, b.rotationFlag);
            diff("isFollower", a.isFollower, b.isFollower);
            diff("lowPriorityFollowerOps", a.lowPriorityFollowerOps, b.lowPriorityFollowerOps);
            diff("params", String.valueOf(a.params), String.valueOf(b.params));
            diff("category", a.category, b.category);
            diff("height", a.height, b.height);
            diff("stats", java.util.Arrays.toString(a.stats), java.util.Arrays.toString(b.stats));
            diff("footprintSize", a.footprintSize, b.footprintSize);
        }
    }

    static void diff(String field, Object a, Object b) {
        boolean same = java.util.Objects.equals(a, b);
        System.out.println((same ? "  " : "**DIFF** ") + field + ": A=" + a + (same ? "" : "  B=" + b));
    }
}

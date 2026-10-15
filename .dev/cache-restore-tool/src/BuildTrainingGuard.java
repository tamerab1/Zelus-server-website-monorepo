import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.NpcDefinition;
import net.runelite.cache.definitions.loaders.NpcLoader;
import net.runelite.cache.fs.*;
import net.runelite.cache.fs.Container;
import net.runelite.cache.index.FileData;
import net.runelite.cache.index.IndexData;

import java.io.File;
import java.util.*;

// The ::train area's "Training Guard" (2026-10-15): a clone of Bandit 690 (humanoid rig, stand 808 / walk 819) in one
// pass, then sorted-inserted as a NEW npc id:
//   name, model list (opcode 1: Bandit's head kits 215 / 248 + the gear's worn models, recoloured ones baked as
//   60142-60147), NO npc recolour table (opcode 40 removed: Bandit's would hit the gear), actions "Attack" only, and
//   the combat level shown in menus (opcode 95).
// Everything else (size, animations, scales) must decode unchanged from 690. Combat, drops, respawn: server data.
// Usage: verify|apply <cachePath> <newId> <name> <combatLevel> <model,model,...>
public class BuildTrainingGuard {
    static final int SOURCE = 690;

    public static void main(String[] args) throws Exception {
        boolean apply = args[0].equals("apply");
        int newId = Integer.parseInt(args[2]), level = Integer.parseInt(args[4]);
        String name = args[3];
        int[] models = Arrays.stream(args[5].split(",")).mapToInt(Integer::parseInt).toArray();
        if (newId >= 65536) throw new IllegalArgumentException("keep npc ids below 65536");
        try (Store store = new Store(new File(args[1]))) {
            store.load();
            Storage storage = store.getStorage();
            Index index = store.getIndex(IndexType.CONFIGS);
            Archive archive = index.getArchive(ConfigType.NPC.getId());
            byte[] dec = archive.decompress(storage.loadArchive(archive));
            FileData[] fd = archive.getFileData();
            List<byte[]> contents = SpliceItemOption.splitChunks(dec, fd.length);
            int src = -1;
            for (int i = 0; i < fd.length; i++) {
                if (fd[i].getId() == newId) throw new IllegalStateException("npc " + newId + " already exists -- ABORTING");
                if (fd[i].getId() == SOURCE && src == -1) src = i;
            }
            if (src == -1) throw new IllegalStateException("source npc " + SOURCE + " not found");
            NpcLoader loader = new NpcLoader();
            byte[] work = contents.get(src);
            NpcDefinition before = loader.load(SOURCE, work);
            if (!"Bandit".equals(before.name)) throw new IllegalStateException("npc 690 is \"" + before.name + "\", expected Bandit");

            work = EditConfigNameActions.replace(true, work, 2, EditConfigNameActions.string(name));
            byte[] m = new byte[1 + models.length * 2];
            m[0] = (byte) models.length;
            for (int i = 0; i < models.length; i++) {
                m[1 + i * 2] = (byte) (models[i] >> 8);
                m[2 + i * 2] = (byte) models[i];
            }
            work = EditConfigNameActions.replace(true, work, 1, m);
            work = EditConfigNameActions.remove(true, work, 40);
            for (int slot = 0; slot < 5; slot++)
                work = slot == 1 ? EditConfigNameActions.replace(true, work, 31, EditConfigNameActions.string("Attack"))
                    : EditConfigNameActions.remove(true, work, 30 + slot);
            work = EditConfigNameActions.replace(true, work, 95, new byte[]{(byte) (level >> 8), (byte) level});

            NpcDefinition after = loader.load(newId, work);
            check(name.equals(after.name), "name");
            check(Arrays.equals(after.models, models), "models");
            check(after.recolorToFind == null, "recolour table removed");
            check(Arrays.equals(after.actions, new String[]{null, "Attack", null, null, null}), "actions " + Arrays.toString(after.actions));
            check(after.combatLevel == level, "combat level");
            check(after.size == before.size && after.standingAnimation == before.standingAnimation
                && after.walkingAnimation == before.walkingAnimation && after.widthScale == before.widthScale
                && after.heightScale == before.heightScale && after.isMinimapVisible == before.isMinimapVisible, "other fields");
            System.out.printf("npc %d \"%s\" level %d models %s stand %d walk %d actions %s (clone of %d)%n", newId, after.name,
                after.combatLevel, Arrays.toString(after.models), after.standingAnimation, after.walkingAnimation,
                Arrays.toString(after.actions), SOURCE);
            if (!apply) {
                System.out.println("VERIFY OK -- nothing written.");
                return;
            }
            int at = 0;
            while (at < fd.length && fd[at].getId() < newId) at++; // the archive must stay in ascending id order
            List<FileData> list = new ArrayList<>(Arrays.asList(fd));
            FileData nf = new FileData();
            nf.setId(newId);
            nf.setNameHash(-1);
            list.add(at, nf);
            contents.add(at, work);
            archive.setFileData(list.toArray(new FileData[0]));
            byte[] joined = SpliceItemOption.joinChunks(contents);
            Container c = new Container(archive.getCompression(), -1);
            c.compress(joined, null);
            storage.store(index.getId(), archive.getArchiveId(), c.data);
            archive.setCrc(c.crc);
            archive.setRevision(archive.getRevision() + 1);
            archive.setCompressedSize(c.data.length);
            archive.setDecompressedSize(joined.length);
            IndexData indexData = index.toIndexData();
            Container ic = new Container(index.getCompression(), -1);
            ic.compress(indexData.writeIndexData(), null);
            storage.store(255, index.getId(), ic.data);
            index.setCrc(ic.crc);
            System.out.println("APPLY complete -- npc " + newId + " inserted at slot " + at + ".");
        }
    }

    static void check(boolean ok, String what) {
        if (!ok) throw new IllegalStateException("CHECK FAILED: " + what + " -- nothing written");
    }
}

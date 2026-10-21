import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.InterfaceDefinition;
import net.runelite.cache.definitions.loaders.InterfaceLoader;
import net.runelite.cache.fs.*;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

// Sets one if3 component's originalY in place (2026-10-21: the PK Hub tab icon 164:45 in the resizable-modern frame is
// y-centred (yPositionMode 1) but kept the +8 offset that 161:51 / 548:55 need in absolute mode, so it sat 8px low).
// Patches the 2 bytes after marker / type / contentType / x, re-decodes and checks that only originalY changed, then
// rewrites the group (file ids must be 0..n-1). Usage: verify|apply <cachePath> <group> <component> <newY>
public class SetIfComponentY {
    public static void main(String[] args) throws Exception {
        boolean apply = args[0].equals("apply");
        int group = Integer.parseInt(args[2]), comp = Integer.parseInt(args[3]), newY = Integer.parseInt(args[4]);
        try (Store store = new Store(new File(args[1]))) {
            store.load();
            Storage storage = store.getStorage();
            Index index = store.getIndex(IndexType.INTERFACES);
            Archive archive = index.getArchive(group);
            ArchiveFiles files = archive.getFiles(storage.loadArchive(archive));
            List<byte[]> comps = new ArrayList<>();
            for (FSFile f : files.getFiles()) {
                if (f.getFileId() != comps.size())
                    throw new IllegalStateException("group " + group + " file ids are not contiguous at " + f.getFileId());
                comps.add(f.getContents());
            }
            byte[] old = comps.get(comp);
            if ((old[0] & 0xff) != 0xff)
                throw new IllegalStateException("component " + comp + " is not if3");
            InterfaceLoader loader = new InterfaceLoader();
            InterfaceDefinition before = loader.load((group << 16) | comp, old);
            int y = (short) (((old[6] & 0xff) << 8) | (old[7] & 0xff));
            if (y != before.originalY)
                throw new IllegalStateException("offset check failed: bytes say " + y + ", decoder " + before.originalY);
            byte[] patched = old.clone();
            patched[6] = (byte) (newY >> 8);
            patched[7] = (byte) newY;
            InterfaceDefinition after = loader.load((group << 16) | comp, patched);
            for (Field f : InterfaceDefinition.class.getFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) || f.getName().equals("originalY")) continue;
                if (!Objects.deepEquals(f.get(before), f.get(after)))
                    throw new IllegalStateException("field " + f.getName() + " changed");
            }
            if (after.originalY != newY)
                throw new IllegalStateException("originalY is " + after.originalY);
            System.out.println(group + ":" + comp + " originalY " + before.originalY + " -> " + newY
                + " (yPositionMode " + before.yPositionMode + ", sprite " + before.spriteId + ")");
            if (!apply) {
                System.out.println("VERIFY OK -- nothing written.");
                return;
            }
            comps.set(comp, patched);
            BuildSpinnerInterface.write(storage, index, archive, comps, group);
            System.out.println("APPLY complete -- group " + group + " rewritten (" + comps.size() + " components).");
        }
    }
}

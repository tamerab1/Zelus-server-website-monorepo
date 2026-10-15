import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.InterfaceDefinition;
import net.runelite.cache.definitions.loaders.InterfaceLoader;
import net.runelite.cache.fs.*;
import net.runelite.cache.index.FileData;

import java.io.File;
import java.lang.reflect.Field;
import java.util.*;

// Hover wash for "layer buttons" (a type-0 layer with an op, whose first filled rect child is its face), as used by the
// Equipment Presets screen 5900 and everything made from it (1142 large presets, 1156/1157 PvP Loadouts). Over: the
// face lightens; leave: back to its cache colour (script 45 = set colour of the given component). Listeners always
// point at THIS group's component, so it is safe to re-run on a copy that inherited another group's hovers.
// Usage: verify|apply <cachePath> <group> [group...]
public class AddButtonHovers {
    static final int LIGHTEN = 0x1c;

    public static void main(String[] args) throws Exception {
        boolean apply = args[0].equals("apply");
        try (Store store = new Store(new File(args[1]))) {
            store.load();
            Storage storage = store.getStorage();
            Index index = store.getIndex(IndexType.INTERFACES);
            InterfaceLoader loader = new InterfaceLoader();
            for (int a = 2; a < args.length; a++) {
                int group = Integer.parseInt(args[a]);
                Archive archive = index.getArchive(group);
                ArchiveFiles files = archive.getFiles(storage.loadArchive(archive));
                int max = 0;
                for (FileData fd : archive.getFileData())
                    max = Math.max(max, fd.getId());
                InterfaceDefinition[] defs = new InterfaceDefinition[max + 1];
                List<byte[]> comps = new ArrayList<>();
                for (int c = 0; c <= max; c++) {
                    FSFile f = files.findFile(c);
                    if (f == null)
                        throw new IllegalStateException(group + " has a gap at " + c);
                    comps.add(f.getContents());
                    defs[c] = loader.load((group << 16) | c, f.getContents());
                }
                int edited = 0;
                for (int c = 0; c <= max; c++) {
                    InterfaceDefinition d = defs[c];
                    if (d.type != 0 || d.clickMask == 0 || d.actions == null)
                        continue;
                    int face = -1;
                    for (int k = 0; k <= max && face == -1; k++)
                        if (defs[k].type == 3 && defs[k].filled && (defs[k].parentId & 0xffff) == c && (defs[k].parentId >>> 16) == group)
                            face = k;
                    if (face == -1)
                        continue;
                    int normal = defs[face].textColor & 0xffffff;
                    int target = (group << 16) | face;
                    d.onMouseOverListener = new Object[]{45, target, lighten(normal)};
                    d.onMouseLeaveListener = new Object[]{45, target, normal};
                    d.hasListener = true;
                    if (!d.isIf3)
                        throw new IllegalStateException(group + ":" + c + " is not IF3");
                    byte[] encoded = BuildBondsInterface.encodeIf3(d);
                    InterfaceDefinition back = loader.load(d.id, encoded);
                    for (Field f : InterfaceDefinition.class.getFields()) {
                        if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                        if (!Objects.deepEquals(f.get(back), f.get(d)))
                            throw new IllegalStateException(group + ":" + c + " " + f.getName() + " does not round-trip");
                    }
                    comps.set(c, encoded);
                    edited++;
                    System.out.printf("%d:%d %s face %d  %06x -> %06x%n", group, c, Arrays.toString(d.actions), face, normal, lighten(normal));
                }
                System.out.println(group + ": " + edited + " button hovers");
                if (apply && edited > 0)
                    BuildSpinnerInterface.write(storage, index, archive, comps, group);
            }
            System.out.println(apply ? "APPLY complete." : "VERIFY OK -- nothing written.");
        }
    }

    static int lighten(int rgb) {
        int r = Math.min(255, (rgb >> 16 & 0xff) + LIGHTEN), g = Math.min(255, (rgb >> 8 & 0xff) + LIGHTEN), b = Math.min(255, (rgb & 0xff) + LIGHTEN);
        return r << 16 | g << 8 | b;
    }
}

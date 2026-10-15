import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.InterfaceDefinition;
import net.runelite.cache.definitions.loaders.InterfaceLoader;
import net.runelite.cache.fs.*;
import net.runelite.cache.index.FileData;
import net.runelite.cache.index.IndexData;

import java.io.File;
import java.lang.reflect.Field;
import java.util.*;

// In-place restyles of existing interfaces (no component added or removed, so every server id stays valid):
//  - 160:49, the minimap wiki banner container: hidden (also hides the RuneLite wiki button the client puts in it).
//  - 706, the PvP Gear Loadouts ("Custom PK Presets"): a combat theme. Charcoal steel fills, crimson borders and row
//    dividers, a crimson-orange title "ZELUS PVP LOADOUTS" and light preset names.
// Each changed IF3 component is re-encoded with BuildBondsInterface.encodeIf3 and must read back identical; untouched
// components keep their original bytes. Usage: verify|apply <cachePath>
public class RestyleInterfaces {
    /** 706 rect colour remap (old ARGB from the cache -> new RGB). */
    static final Map<Integer, Integer> LOADOUT_RECTS = Map.of(
            0xff726250, 0x8c2323,   // bronze inner lines -> crimson
            0xff3e3529, 0x2d2c30,   // button fills -> charcoal steel
            0xff2a231e, 0x4a1818,   // preset row dividers -> dark blood
            0xff212121, 0x1e1d20,   // stat panel -> deep charcoal
            0xffff981f, 0xd23c3c);  // selector outlines -> bright crimson
    static final int LOADOUT_TITLE = 14, TITLE_COLOUR = 0xff5a3c, PRESET_TEXT = 0xe6804d, PRESET_TEXT_NEW = 0xe8d6c0;

    public static void main(String[] args) throws Exception {
        boolean apply = args[0].equals("apply");
        try (Store store = new Store(new File(args[1]))) {
            store.load();
            Storage storage = store.getStorage();
            Index index = store.getIndex(IndexType.INTERFACES);

            restyle(storage, index, 160, apply, d -> {
                if ((d.id & 0xffff) != 49)
                    return false;
                d.isHidden = true;
                return true;
            });
            restyle(storage, index, 706, apply, d -> {
                boolean changed = false;
                if (d.type == 3) {
                    Integer to = LOADOUT_RECTS.get(d.textColor | 0xff000000);
                    if (to != null) {
                        d.textColor = to;
                        changed = true;
                    }
                } else if (d.type == 4) {
                    if ((d.id & 0xffff) == LOADOUT_TITLE) {
                        d.text = "ZELUS PVP LOADOUTS";
                        d.textColor = TITLE_COLOUR;
                        changed = true;
                    } else if ((d.textColor & 0xffffff) == PRESET_TEXT) {
                        d.textColor = PRESET_TEXT_NEW;
                        changed = true;
                    }
                }
                return changed;
            });
            if (!apply) {
                System.out.println("VERIFY OK -- nothing written.");
                return;
            }
            IndexData indexData = index.toIndexData();
            Container ic = new Container(index.getCompression(), -1);
            ic.compress(indexData.writeIndexData(), null);
            storage.store(255, index.getId(), ic.data);
            index.setCrc(ic.crc);
            System.out.println("APPLY complete.");
        }
    }

    interface Edit {
        boolean apply(InterfaceDefinition d);
    }

    static void restyle(Storage storage, Index index, int group, boolean apply, Edit edit) throws Exception {
        Archive archive = index.getArchive(group);
        ArchiveFiles files = archive.getFiles(storage.loadArchive(archive));
        InterfaceLoader loader = new InterfaceLoader();
        ArchiveFiles out = new ArchiveFiles();
        int changed = 0;
        for (FileData fd : archive.getFileData()) {
            byte[] bytes = files.findFile(fd.getId()).getContents();
            InterfaceDefinition d = loader.load((group << 16) | fd.getId(), bytes);
            if (d.isIf3 && edit.apply(d)) {
                byte[] encoded = BuildBondsInterface.encodeIf3(d);
                InterfaceDefinition back = loader.load(d.id, encoded);
                for (Field f : InterfaceDefinition.class.getFields()) {
                    if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                    if (!Objects.deepEquals(f.get(back), f.get(d)))
                        throw new IllegalStateException(group + ":" + fd.getId() + " " + f.getName() + " does not round-trip");
                }
                bytes = encoded;
                changed++;
            }
            FSFile f = new FSFile(fd.getId());
            f.setNameHash(fd.getNameHash());
            f.setContents(bytes);
            out.addFile(f);
        }
        System.out.println(group + ": " + changed + " components restyled");
        if (!apply)
            return;
        archive.setRevision(archive.getRevision() + 1);
        byte[] data = out.saveContents();
        Container c = new Container(archive.getCompression(), -1);
        c.compress(data, null);
        storage.store(index.getId(), group, c.data);
        archive.setCrc(c.crc);
        archive.setCompressedSize(c.data.length);
        archive.setDecompressedSize(data.length);
    }
}

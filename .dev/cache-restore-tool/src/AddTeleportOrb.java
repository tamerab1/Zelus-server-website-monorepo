import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.InterfaceDefinition;
import net.runelite.cache.definitions.loaders.InterfaceLoader;
import net.runelite.cache.fs.*;
import net.runelite.cache.index.FileData;
import net.runelite.cache.index.IndexData;

import java.io.File;
import java.lang.reflect.Field;
import java.util.*;

// Appends a "Teleports" orb to the minimap orbs interface 160 (components 54-58), cloned from the store/bond orb
// (160:42-46): container, orb frame (sprite 2138), orb background (1609), click layer (op "Teleports") and the Home
// Teleport rune icon (sprite 356). Every existing component of 160 is written back byte-for-byte. The server handles
// the click in MainFrame (160:57). Usage: verify|apply|rebuild <cachePath> (rebuild replaces an existing 54-58)
public class AddTeleportOrb {
    static final int GROUP = 160, FIRST_NEW = 54;
    /**
     * Directly left of the world map orb (160:48: right/bottom aligned at 0,30, 30x30) beside the wiki banner: the same
     * right/bottom alignment, 31px further left.
     */
    static final int ORB_X = 31, ORB_Y = 28, ORB_ALIGN = 2;
    /** The purple teleport vortex spell icon. */
    static final int ICON_SPRITE = 359;
    /** Orb frame sprites: normal and hovered (from script 2396). */
    static final int FRAME = 2138, FRAME_HOVER = 3517;

    public static void main(String[] args) throws Exception {
        boolean rebuild = args[0].equals("rebuild");
        boolean apply = args[0].equals("apply") || rebuild;
        try (Store store = new Store(new File(args[1]))) {
            store.load();
            Storage storage = store.getStorage();
            Index index = store.getIndex(IndexType.INTERFACES);
            Archive archive = index.getArchive(GROUP);
            ArchiveFiles files = archive.getFiles(storage.loadArchive(archive));
            if (files.findFile(FIRST_NEW) != null && !rebuild)
                throw new IllegalStateException("160:" + FIRST_NEW + " already exists (use rebuild)");
            InterfaceLoader loader = new InterfaceLoader();

            List<InterfaceDefinition> added = new ArrayList<>();
            InterfaceDefinition container = loader.load((GROUP << 16) | 42, files.findFile(42).getContents());
            container.originalX = ORB_X;
            container.originalY = ORB_Y;
            container.xPositionMode = ORB_ALIGN;
            container.yPositionMode = ORB_ALIGN;
            // the store orb's onLoad (script 2396) sets up the STORE orb (its op "Open Store", its hover, its hiding)
            container.onLoadListener = null;
            container.hasListener = false;
            added.add(container);
            InterfaceDefinition frame = loader.load((GROUP << 16) | 43, files.findFile(43).getContents());
            frame.parentId = (GROUP << 16) | FIRST_NEW;
            added.add(frame);
            InterfaceDefinition background = loader.load((GROUP << 16) | 44, files.findFile(44).getContents());
            background.parentId = (GROUP << 16) | FIRST_NEW;
            added.add(background);
            InterfaceDefinition click = loader.load((GROUP << 16) | 45, files.findFile(45).getContents());
            click.parentId = (GROUP << 16) | FIRST_NEW;
            click.actions = new String[]{"Teleports"};
            click.name = "<col=ff9040>Zelus Teleports</col>";
            // drop the store orb's own listeners: they target the store orb's components
            click.onMouseOverListener = null;
            click.onMouseLeaveListener = null;
            click.onMouseRepeatListener = null;
            click.onOpListener = null;
            // hover: swap the orb frame to the highlighted one, as script 2396 does for the store orb (2138 -> 3517)
            click.onMouseOverListener = new Object[]{44, (GROUP << 16) | (FIRST_NEW + 1), FRAME_HOVER};
            click.onMouseLeaveListener = new Object[]{44, (GROUP << 16) | (FIRST_NEW + 1), FRAME};
            click.hasListener = true;
            added.add(click);
            InterfaceDefinition icon = loader.load((GROUP << 16) | 46, files.findFile(46).getContents());
            icon.parentId = (GROUP << 16) | FIRST_NEW;
            icon.spriteId = ICON_SPRITE;
            icon.originalWidth = 20;
            icon.originalHeight = 20;
            added.add(icon);

            for (int i = 0; i < added.size(); i++) {
                InterfaceDefinition exp = added.get(i);
                byte[] bytes = BuildBondsInterface.encodeIf3(exp);
                InterfaceDefinition got = loader.load((GROUP << 16) | (FIRST_NEW + i), bytes);
                exp.id = (GROUP << 16) | (FIRST_NEW + i);
                for (Field f : InterfaceDefinition.class.getFields()) {
                    if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                    if (!Objects.deepEquals(f.get(got), f.get(exp)))
                        throw new IllegalStateException("component " + (FIRST_NEW + i) + ": " + f.getName() + " " + f.get(got) + " != " + f.get(exp));
                }
                System.out.printf("160:%d type=%d x=%d y=%d w=%d h=%d parent=%d sprite=%d ops=%s%n", FIRST_NEW + i, got.type,
                        got.originalX, got.originalY, got.originalWidth, got.originalHeight, got.parentId & 0xffff, got.spriteId,
                        got.actions == null ? "" : Arrays.toString(got.actions));
            }
            if (!apply) {
                System.out.println("VERIFY OK -- nothing written.");
                return;
            }

            // existing files unchanged, then the 5 new ones
            ArchiveFiles out = new ArchiveFiles();
            List<FileData> fd = new ArrayList<>();
            for (FileData old : archive.getFileData()) {
                if (old.getId() >= FIRST_NEW && old.getId() < FIRST_NEW + added.size())
                    continue; // rebuild: replaced below
                FSFile f = new FSFile(old.getId());
                f.setNameHash(old.getNameHash());
                f.setContents(files.findFile(old.getId()).getContents());
                out.addFile(f);
                fd.add(old);
            }
            for (int i = 0; i < added.size(); i++) {
                int id = FIRST_NEW + i;
                FSFile f = new FSFile(id);
                f.setContents(BuildBondsInterface.encodeIf3(added.get(i)));
                out.addFile(f);
                FileData d = new FileData();
                d.setId(id);
                fd.add(d);
            }
            archive.setFileData(fd.toArray(new FileData[0]));
            archive.setRevision(archive.getRevision() + 1);
            byte[] data = out.saveContents();
            Container c = new Container(archive.getCompression(), -1);
            c.compress(data, null);
            storage.store(index.getId(), GROUP, c.data);
            archive.setCrc(c.crc);
            archive.setCompressedSize(c.data.length);
            archive.setDecompressedSize(data.length);
            IndexData indexData = index.toIndexData();
            Container ic = new Container(index.getCompression(), -1);
            ic.compress(indexData.writeIndexData(), null);
            storage.store(255, index.getId(), ic.data);
            index.setCrc(ic.crc);
            System.out.println("APPLY complete -- 160:" + FIRST_NEW + "-" + (FIRST_NEW + added.size() - 1) + " added.");
        }
    }
}

import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.ScriptDefinition;
import net.runelite.cache.definitions.loaders.ScriptLoader;
import net.runelite.cache.definitions.savers.ScriptSaver;
import net.runelite.cache.fs.*;
import net.runelite.cache.index.IndexData;
import net.runelite.cache.script.Instructions;
import net.runelite.cache.script.assembler.Assembler;
import net.runelite.cache.script.disassembler.Disassembler;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Map;

// Custom clientscript 10752 set_opbase(component, name): if_setopbase on a component, so hovering it shows the native
// mouse-over text "<op> <name>" (e.g. "Examine Blood torva platebody (x1 required)"). The server sends it for item
// slots it fills at runtime (Relic Forge reagents, base and outcome), whose names aren't known when the cache is built.
// Assembled from rs2asm with RuneLite's assembler like AddSpinnerScripts. Usage: verify|apply|rebuild <cachePath>
public class AddOpBaseScript {
    static final int OPBASE = 10752, NONUM = 10753;

    // 10753 set_object_nonum(component, item, amount): if_setobject_nonum -- the item's icon without the stack number
    // the client otherwise draws on stackables (Zelus Titles' coin pile).
    static final String NONUM_ASM = String.join("\n",
            ".id                 " + NONUM,
            ".int_arg_count      3",
            ".obj_arg_count      0",
            "   iload            1",
            "   iload            2",
            "   iload            0",
            "   if_setobject_nonum",
            "   return",
            "");

    static final String OPBASE_ASM = String.join("\n",
            ".id                 " + OPBASE,
            ".int_arg_count      1",
            ".obj_arg_count      1",
            "   oload            0",
            "   iload            0",
            "   if_setopbase",
            "   return",
            "");

    public static void main(String[] args) throws Exception {
        boolean rebuild = args[0].equals("rebuild");
        boolean apply = args[0].equals("apply") || rebuild;
        Instructions instructions = new Instructions();
        instructions.init();
        Assembler assembler = new Assembler(instructions, Map.of());
        Disassembler disassembler = new Disassembler(Map.of());
        try (Store store = new Store(new File(args[1]))) {
            store.load();
            Storage storage = store.getStorage();
            Index index = store.getIndex(IndexType.CLIENTSCRIPT);
            for (String asm : new String[]{OPBASE_ASM, NONUM_ASM}) {
                ScriptDefinition script = assembler.assemble(new ByteArrayInputStream(asm.getBytes(StandardCharsets.UTF_8)));
                byte[] data = new ScriptSaver().save(script);
                ScriptDefinition back = new ScriptLoader().load(script.getId(), data);
                String text = disassembler.disassemble(back);
                System.out.println(text);
                System.out.println("locals: int=" + back.getLocalIntCount() + " obj=" + back.getLocalObjCount()
                        + " args: int=" + back.getIntArgCount() + " obj=" + back.getObjArgCount());
                if (back.getLocalIntCount() < back.getIntArgCount())
                    throw new IllegalStateException("local int count below the argument count");
                if (index.getArchive(script.getId()) != null && !rebuild)
                    throw new IllegalStateException("script " + script.getId() + " already exists (use rebuild)");
                if (!apply)
                    continue;
                Archive archive = index.getArchive(script.getId()) != null ? index.getArchive(script.getId()) : index.addArchive(script.getId());
                archive.setRevision(archive.getRevision() + 1);
                Container container = new Container(0, -1); // clientscripts are stored uncompressed
                container.compress(data, null);
                storage.store(index.getId(), script.getId(), container.data);
                archive.setCrc(container.crc);
                archive.setCompression(0);
                archive.setCompressedSize(container.data.length);
                archive.setDecompressedSize(data.length);
                net.runelite.cache.index.FileData fd = new net.runelite.cache.index.FileData();
                fd.setId(0);
                archive.setFileData(new net.runelite.cache.index.FileData[]{fd});
            }
            if (!apply) {
                System.out.println("VERIFY OK -- nothing written.");
                return;
            }
            IndexData indexData = index.toIndexData();
            Container ic = new Container(index.getCompression(), -1);
            ic.compress(indexData.writeIndexData(), null);
            storage.store(255, index.getId(), ic.data);
            index.setCrc(ic.crc);
            System.out.println("APPLY complete -- scripts " + OPBASE + " and " + NONUM + ".");
        }
    }
}

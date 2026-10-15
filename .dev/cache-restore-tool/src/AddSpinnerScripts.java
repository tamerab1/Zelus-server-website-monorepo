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

// Adds the Zelus Spinner's client-side animation scripts (MysteryBoxSpinner.java, interface 1154):
//  10751 spinner_start(track component, target scroll x): scroll the track to 0 and attach 10750 as its onTimer.
//  10750 spinner_tick(track component, target scroll x): runs every client cycle (20ms); moves the track's scroll x a
//        1/36 of the remaining distance (at least 1px), an exponential ease-out that settles on the target after about
//        3.5s, then snaps to the target and removes itself.
// The scripts are assembled from rs2asm with RuneLite's assembler, round-tripped through the loader/disassembler, and
// stored as new CLIENTSCRIPT archives. Usage: verify|apply|rebuild <cachePath>
public class AddSpinnerScripts {
    static final int TICK = 10750, START = 10751;

    static final String TICK_ASM = String.join("\n",
            ".id                 " + TICK,
            ".int_arg_count      2",
            ".obj_arg_count      0",
            "   iload            0",
            "   if_getscrollx",
            "   istore           2",
            "   iload            1",
            "   iload            2",
            "   sub",
            "   istore           3",
            "   iload            3",
            "   iconst           1",
            "   if_icmplt        LABEL_DONE",
            "   iload            3",
            "   iconst           36",
            "   div",
            "   istore           4",
            "   iload            4",
            "   iconst           1",
            "   if_icmpge        LABEL_STEP",
            "   iconst           1",
            "   istore           4",
            "LABEL_STEP:",
            "   iload            2",
            "   iload            4",
            "   add",
            "   iconst           0",
            "   iload            0",
            "   if_setscrollpos",
            "   return",
            "LABEL_DONE:",
            "   iload            1",
            "   iconst           0",
            "   iload            0",
            "   if_setscrollpos",
            "   iconst           -1",
            "   sconst           \"\"",
            "   iload            0",
            "   if_setontimer",
            "   return",
            "");

    static final String START_ASM = String.join("\n",
            ".id                 " + START,
            ".int_arg_count      2",
            ".obj_arg_count      0",
            "   iconst           0",
            "   iconst           0",
            "   iload            0",
            "   if_setscrollpos",
            "   iconst           " + TICK,
            "   iload            0",
            "   iload            1",
            "   sconst           \"ii\"",
            "   iload            0",
            "   if_setontimer",
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
            for (String asm : new String[]{TICK_ASM, START_ASM}) {
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
            System.out.println("APPLY complete -- scripts " + TICK + " and " + START + ".");
        }
    }
}

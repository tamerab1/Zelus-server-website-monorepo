import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.ScriptDefinition;
import net.runelite.cache.definitions.loaders.ScriptLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.script.disassembler.Disassembler;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class DumpClientScript {
    public static void main(String[] args) throws Exception {
        String cachePath = args[0];
        try (Store store = new Store(new File(cachePath))) {
            store.load();
            Storage storage = store.getStorage();
            Index scripts = store.getIndex(IndexType.CLIENTSCRIPT);
            ScriptLoader loader = new ScriptLoader();
            Disassembler disassembler = new Disassembler();

            for (int i = 1; i < args.length; i++) {
                int scriptId = Integer.parseInt(args[i]);
                Archive archive = scripts.getArchive(scriptId);
                if (archive == null) {
                    System.out.println("Script " + scriptId + " not found (no such archive).");
                    continue;
                }
                byte[] contents = archive.decompress(storage.loadArchive(archive));
                if (contents == null) {
                    System.out.println("Script " + scriptId + " archive is empty/null.");
                    continue;
                }
                ScriptDefinition def = loader.load(archive.getArchiveId(), contents);
                String out = disassembler.disassemble(def);
                File outFile = new File(scriptId + ".rs2asm");
                Files.write(outFile.toPath(), out.getBytes(StandardCharsets.UTF_8));
                System.out.println("Dumped script " + scriptId + " -> " + outFile.getAbsolutePath());
            }
        }
    }
}

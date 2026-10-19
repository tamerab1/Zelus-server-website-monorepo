import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.SequenceDefinition;
import net.runelite.cache.definitions.loaders.SequenceLoader;
import net.runelite.cache.fs.*;
import net.runelite.cache.index.FileData;

import java.io.File;
import java.util.Arrays;
import java.util.List;

// Read-only (2026-10-19): frame lengths and play options of sequences. Usage: SeqInfo <cache> <seq>...
public class SeqInfo {
    public static void main(String[] args) throws Exception {
        try (Store store = new Store(new File(args[0]))) {
            store.load();
            Archive a = store.getIndex(IndexType.CONFIGS).getArchive(ConfigType.SEQUENCE.getId());
            FileData[] fd = a.getFileData();
            List<byte[]> files = SpliceItemOption.splitChunks(a.decompress(store.getStorage().loadArchive(a)), fd.length);
            for (int i = 1; i < args.length; i++) {
                int id = Integer.parseInt(args[i]);
                for (int k = 0; k < fd.length; k++)
                    if (fd[k].getId() == id) {
                        SequenceDefinition s = new SequenceLoader().load(id, files.get(k));
                        System.out.printf("seq %d: %d frames, lengths %s (total %d), frameStep %d, maxLoops %d, priority %d, precedence %d, replyMode %d, forcedPriority %d%n",
                                id, s.frameIDs == null ? 0 : s.frameIDs.length, Arrays.toString(s.frameLengths),
                                s.frameLengths == null ? 0 : Arrays.stream(s.frameLengths).sum(), s.frameStep, s.maxLoops, s.priority,
                                s.precedenceAnimating, s.replyMode, s.forcedPriority);
                    }
            }
        }
    }
}

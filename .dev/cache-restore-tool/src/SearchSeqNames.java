import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.SequenceDefinition;
import net.runelite.cache.definitions.loaders.SequenceLoader;
import net.runelite.cache.fs.*;

import java.io.File;
import java.util.Map;

// Read-only. Sequences whose debug name (opcode 18) matches a regex, with frame count, first frame group and its skeleton.
// Usage: SearchSeqNames <cachePath> <regex>
public class SearchSeqNames {
    public static void main(String[] args) throws Exception {
        try (Store store = new Store(new File(args[0]))) {
            store.load();
            Archive a = store.getIndex(IndexType.CONFIGS).getArchive(ConfigType.SEQUENCE.getId());
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(args[1], java.util.regex.Pattern.CASE_INSENSITIVE);
            int named = 0;
            for (Map.Entry<Integer, byte[]> e : ScanLocAnims.files(store, a).entrySet()) {
                SequenceDefinition s;
                try { s = new SequenceLoader().load(e.getKey(), e.getValue()); } catch (Exception ex) { continue; }
                if (s.debugName != null) named++;
                if (s.debugName == null || !p.matcher(s.debugName).find()) continue;
                System.out.println(e.getKey() + " " + s.debugName + " frames=" + (s.frameIDs == null ? 0 : s.frameIDs.length)
                        + " group=" + (s.frameIDs == null ? -1 : s.frameIDs[0] >>> 16) + " maya=" + s.animMayaID);
            }
            System.out.println("named seqs: " + named);
        }
    }
}

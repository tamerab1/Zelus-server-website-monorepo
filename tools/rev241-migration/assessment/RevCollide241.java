import net.runelite.cache.fs.*;
import net.runelite.cache.index.FileData;
import java.io.File;
import java.util.*;
// args: local 231van 241van
public class RevCollide241 {
  static Map<Integer, byte[]> cfg(Store s, int group) throws Exception {
    Map<Integer, byte[]> m = new HashMap<>();
    Index ix = s.findIndex(2); Archive ar = ix.getArchive(group);
    byte[] data = s.getStorage().loadArchive(ar);
    byte[] raw = ar.decompress(data, null);
    FileData[] fd = ar.getFileData();
    ArchiveFiles af = new ArchiveFiles();
    for (int i = 0; i < fd.length; i++) af.addFile(new FSFile(i));
    af.loadContents(raw);
    for (FSFile f : af.getFiles()) m.put(fd[f.getFileId()].getId(), f.getContents());
    return m;
  }
  static Map<Integer, Integer> crcs(Store s, int idx) {
    Map<Integer, Integer> m = new HashMap<>();
    for (Archive a : s.findIndex(idx).getArchives()) m.put(a.getArchiveId(), a.getCrc());
    return m;
  }
  public static void main(String[] a) throws Exception {
    Store L = new Store(new File(a[0])); L.load();
    Store V1 = new Store(new File(a[1])); V1.load();
    Store V2 = new Store(new File(a[2])); V2.load();
    int[] groups = {6, 9, 10, 12, 13, 14, 16};
    String[] names = {"obj", "npc", "item", "seq", "spotanim", "varbit", "varp"};
    for (int g = 0; g < groups.length; g++) {
      var l = cfg(L, groups[g]); var v1 = cfg(V1, groups[g]); var v2 = cfg(V2, groups[g]);
      int added = 0, modified = 0, addedCollide = 0, modOverwrittenBy241 = 0, max231 = Collections.max(v1.keySet());
      int minAdded = Integer.MAX_VALUE;
      for (var e : l.entrySet()) {
        int id = e.getKey();
        if (!v1.containsKey(id)) { added++; minAdded = Math.min(minAdded, id); if (v2.containsKey(id)) addedCollide++; }
        else if (!Arrays.equals(v1.get(id), e.getValue())) { modified++; if (v2.containsKey(id) && !Arrays.equals(v1.get(id), v2.get(id))) modOverwrittenBy241++; }
      }
      System.out.printf("%-8s 231max=%d 241max=%d | custom-new=%d (lowest id %d) of which COLLIDE with new 241 ids=%d | vanilla-edited=%d of which Jagex also changed in 241=%d%n",
        names[g], max231, Collections.max(v2.keySet()), added, minAdded == Integer.MAX_VALUE ? -1 : minAdded, addedCollide, modified, modOverwrittenBy241);
    }
    for (int idx : new int[]{7, 0, 1, 8}) {
      var l = crcs(L, idx); var v1 = crcs(V1, idx); var v2 = crcs(V2, idx);
      int added = 0, collide = 0, modified = 0, minAdded = Integer.MAX_VALUE;
      for (var e : l.entrySet()) {
        int id = e.getKey();
        if (!v1.containsKey(id)) { added++; minAdded = Math.min(minAdded, id); if (v2.containsKey(id)) collide++; }
        else if (!v1.get(id).equals(e.getValue())) modified++;
      }
      System.out.printf("index%-3d 231max=%d 241max=%d | custom-new=%d (lowest %d) COLLIDE=%d | vanilla-edited=%d%n",
        idx, Collections.max(v1.keySet()), Collections.max(v2.keySet()), added, minAdded, collide, modified);
    }
  }
}

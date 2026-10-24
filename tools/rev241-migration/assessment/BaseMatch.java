import net.runelite.cache.fs.*; import net.runelite.cache.index.*; import java.io.File; import java.nio.file.*; import java.util.*;
// args: localCache baseDir build...
public class BaseMatch {
  static Map<Integer, byte[]> split(byte[] raw, FileData[] fd) throws Exception {
    List<byte[]> c = SpliceItemOption.splitChunks(raw, fd.length); Map<Integer, byte[]> m = new HashMap<>();
    for (int i = 0; i < fd.length; i++) m.put(fd[i].getId(), c.get(i)); return m; }
  public static void main(String[] a) throws Exception {
    Store L = new Store(new File(a[0])); L.load(); Index li = L.findIndex(2);
    int[] groups = {6, 9, 10, 12, 13, 14, 16}; String[] names = {"obj","npc","item","seq","spot","varbit","varp"};
    Map<Integer, Map<Integer, byte[]>> local = new HashMap<>();
    for (int g : groups) { Archive ar = li.getArchive(g); local.put(g, split(ar.decompress(L.getStorage().loadArchive(ar)), ar.getFileData())); }
    for (int b = 2; b < a.length; b++) {
      String dir = a[1] + "/" + a[b]; IndexData idx = new IndexData(); idx.load(Container.decompress(Files.readAllBytes(Paths.get(dir, "255_2.dat")), null).data);
      StringBuilder sb = new StringBuilder("RESULT " + a[b] + ":"); long totalDiff = 0;
      for (int k = 0; k < groups.length; k++) { Path p = Paths.get(dir, "2_" + groups[k] + ".dat"); if (!Files.exists(p) || Files.size(p) == 0) { sb.append(" " + names[k] + "=n/a"); continue; }
        ArchiveData ad = null; for (ArchiveData x : idx.getArchives()) if (x.getId() == groups[k]) ad = x;
        Map<Integer, byte[]> base = split(Container.decompress(Files.readAllBytes(p), null).data, ad.getFiles());
        int eq = 0, diff = 0; for (var e : base.entrySet()) { byte[] l = local.get(groups[k]).get(e.getKey()); if (l == null) continue; if (Arrays.equals(l, e.getValue())) eq++; else diff++; }
        totalDiff += diff; sb.append(" " + names[k] + "=" + diff + "/" + (eq + diff) + "(max" + Collections.max(base.keySet()) + ")"); }
      System.out.println(sb + "  TOTAL_DIFF=" + totalDiff); } } }

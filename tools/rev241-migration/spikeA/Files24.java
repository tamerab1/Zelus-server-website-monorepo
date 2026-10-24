import net.runelite.cache.fs.*; import java.io.File; import java.nio.file.*; import java.util.*;
public class Files24 { public static void main(String[] a) throws Exception { Store n = new Store(new File(a[0])); n.load(); Map<String,List<Integer>> f2 = new HashMap<>();
  for (Archive na : n.findIndex(5).getArchives()) { ArchiveFiles af = na.getFiles(n.getStorage().loadArchive(na));
    if (af.getFiles().size() != 5) { System.out.print("RESULT group " + na.getArchiveId() + " files:"); for (FSFile f : af.getFiles()) System.out.print(" " + f.getFileId() + ":" + f.getContents().length); System.out.println(); FSFile f0 = af.getFiles().iterator().next(); Files.write(Path.of("g25287_first.bin"), f0.getContents()); continue; }
    f2.computeIfAbsent(HexFormat.of().formatHex(af.findFile(2).getContents()), k -> new ArrayList<>()).add(na.getArchiveId()); }
  System.out.println("RESULT distinct file2 contents=" + f2.size());
  f2.entrySet().stream().sorted((x, y) -> y.getValue().size() - x.getValue().size()).limit(4).forEach(e -> System.out.println("RESULT  x" + e.getValue().size() + " e.g. region " + e.getValue().get(0) + ": " + e.getKey().substring(0, Math.min(140, e.getKey().length())))); } }

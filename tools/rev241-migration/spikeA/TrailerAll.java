import net.runelite.cache.fs.*; import java.io.File; import java.nio.file.*; import java.util.*;
public class TrailerAll { public static void main(String[] a) throws Exception { Store n = new Store(new File(a[0])); n.load(); Map<String,Integer> hist = new TreeMap<>(); List<Integer> odd = new ArrayList<>();
  for (Archive na : n.findIndex(5).getArchives()) { ArchiveFiles af = na.getFiles(n.getStorage().loadArchive(na)); FSFile f0 = af.findFile(0); if (f0 == null) continue; byte[] b = f0.getContents(); MapCodec.Buf in = new MapCodec.Buf(b);
    try { for (int z = 0; z < 4; z++) for (int x = 0; x < 64; x++) for (int y = 0; y < 64; y++) while (true) { int op = in.u16(); if (op == 0) break; if (op == 1) { in.u8(); break; } if (op <= 49) in.u16(); } } catch (Exception e) { hist.merge("EXC", 1, Integer::sum); continue; }
    int left = b.length - in.pos; String key = left <= 2 ? "len" + left + ":" + (left > 0 ? String.format("%02x", b[in.pos]) : "") : "len" + left + " first=" + String.format("%02x", b[in.pos]);
    hist.merge(left > 2 ? "len>2 first=" + String.format("%02x", b[in.pos]) : key, 1, Integer::sum); if (left != 1 || b[in.pos] != 0) { if (odd.size() < 10) { odd.add(na.getArchiveId()); Files.write(Path.of("odd_" + na.getArchiveId() + "_f0.bin"), b); } } }
  System.out.println("RESULT trailer histogram=" + hist + " oddRegions=" + odd); } }

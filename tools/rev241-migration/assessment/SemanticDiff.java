import net.runelite.cache.fs.*; import net.runelite.cache.index.*; import net.runelite.cache.definitions.loaders.*; import java.io.File; import java.lang.reflect.*; import java.util.*;
// args: localCache baseCache  -> semantic diff of item/npc/obj defs + model content diff
public class SemanticDiff {
  static Map<Integer, byte[]> cfg(Store s, int g) throws Exception { Archive ar = s.findIndex(2).getArchive(g); FileData[] fd = ar.getFileData();
    List<byte[]> c = SpliceItemOption.splitChunks(ar.decompress(s.getStorage().loadArchive(ar)), fd.length); Map<Integer, byte[]> m = new HashMap<>(); for (int i = 0; i < fd.length; i++) m.put(fd[i].getId(), c.get(i)); return m; }
  static Object decode(int g, int id, byte[] b) { try { switch (g) { case 10: return new ItemLoader().load(id, b); case 9: return new NpcLoader().load(id, b); default: return new ObjectLoader().load(id, b); } } catch (Throwable t) { return null; } }
  static List<String> diffFields(Object x, Object y) throws Exception { List<String> out = new ArrayList<>();
    for (Class<?> c = x.getClass(); c != Object.class; c = c.getSuperclass()) for (Field f : c.getDeclaredFields()) { if (Modifier.isStatic(f.getModifiers())) continue; f.setAccessible(true);
      if (!Objects.deepEquals(f.get(x), f.get(y))) out.add(f.getName()); } return out; }
  public static void main(String[] a) throws Exception { Store L = new Store(new File(a[0])); L.load(); Store B = new Store(new File(a[1])); B.load();
    for (int g : new int[]{10, 9, 6}) { var l = cfg(L, g); var b = cfg(B, g); int same = 0, edited = 0, undecodable = 0; Map<String,Integer> fieldHist = new TreeMap<>(); List<Integer> sample = new ArrayList<>();
      for (var e : b.entrySet()) { byte[] lb = l.get(e.getKey()); if (lb == null) continue; Object x = decode(g, e.getKey(), lb), y = decode(g, e.getKey(), e.getValue());
        if (x == null || y == null) { undecodable++; continue; } List<String> d = diffFields(x, y); if (d.isEmpty()) same++; else { edited++; if (sample.size() < 15) sample.add(e.getKey()); for (String f : d) fieldHist.merge(f, 1, Integer::sum); } }
      System.out.println("RESULT group " + g + ": semantically same=" + same + " edited=" + edited + " undecodable=" + undecodable + " fields=" + fieldHist + " sample=" + sample); }
    Index lm = L.findIndex(7), bm = B.findIndex(7); int same = 0, diff = 0; List<Integer> ds = new ArrayList<>();
    for (Archive ba : bm.getArchives()) { Archive la = lm.getArchive(ba.getArchiveId()); if (la == null) continue;
      byte[] x = la.decompress(L.getStorage().loadArchive(la)), y = ba.decompress(B.getStorage().loadArchive(ba)); if (Arrays.equals(x, y)) same++; else { diff++; if (ds.size() < 20) ds.add(ba.getArchiveId()); } }
    System.out.println("RESULT models: content same=" + same + " differ=" + diff + " sample=" + ds); } }

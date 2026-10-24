import net.runelite.cache.fs.*; import net.runelite.cache.definitions.*; import net.runelite.cache.definitions.loaders.ModelLoader; import java.io.File; import java.lang.reflect.*; import java.util.*;
public class ModelSem { public static void main(String[] a) throws Exception { Store L = new Store(new File(a[0])); L.load(); Store B = new Store(new File(a[1])); B.load();
  Index lm = L.findIndex(7), bm = B.findIndex(7); int byteSame = 0, semSame = 0, semDiff = 0, err = 0; Map<String,Integer> fh = new TreeMap<>(); Map<String,Integer> tails = new TreeMap<>(); List<Integer> realDiff = new ArrayList<>();
  for (Archive ba : bm.getArchives()) { Archive la = lm.getArchive(ba.getArchiveId()); if (la == null) continue;
    byte[] x = la.decompress(L.getStorage().loadArchive(la)), y = ba.decompress(B.getStorage().loadArchive(ba)); if (Arrays.equals(x, y)) { byteSame++; continue; }
    tails.merge("L" + (x[x.length-2]&0xff) + "," + (x[x.length-1]&0xff) + " B" + (y[y.length-2]&0xff) + "," + (y[y.length-1]&0xff), 1, Integer::sum);
    try { ModelDefinition p = new ModelLoader().load(ba.getArchiveId(), x), q = new ModelLoader().load(ba.getArchiveId(), y); List<String> d = new ArrayList<>();
      for (Field f : ModelDefinition.class.getDeclaredFields()) { if (Modifier.isStatic(f.getModifiers()) || f.getName().endsWith("Normals")) continue; f.setAccessible(true); if (!Objects.deepEquals(f.get(p), f.get(q))) d.add(f.getName()); }
      if (d.isEmpty()) semSame++; else { semDiff++; if (realDiff.size() < 80) realDiff.add(ba.getArchiveId()); for (String s : d) fh.merge(s, 1, Integer::sum); } } catch (Throwable t) { err++; } }
  System.out.println("RESULT byteSame=" + byteSame + " reencodedButSame=" + semSame + " realDiff=" + semDiff + " decodeErr=" + err);
  System.out.println("RESULT fields=" + fh); System.out.println("RESULT sampleRealDiff=" + realDiff);
  System.out.println("RESULT trailer formats (top)=" + tails.entrySet().stream().sorted((m, n) -> n.getValue() - m.getValue()).limit(6).toList()); } }

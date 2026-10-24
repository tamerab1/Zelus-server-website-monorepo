import net.runelite.cache.fs.*; import net.runelite.cache.index.FileData; import net.runelite.cache.definitions.*; import net.runelite.cache.definitions.loaders.*;
import java.io.File; import java.nio.file.*; import java.security.MessageDigest; import java.util.*;
// args: local v231 v241 outDir
public class ModelBudget {
  static Map<Integer, byte[]> cfg(Store s, int g) throws Exception { Archive ar = s.findIndex(2).getArchive(g); FileData[] fd = ar.getFileData();
    List<byte[]> c = SpliceItemOption.splitChunks(ar.decompress(s.getStorage().loadArchive(ar)), fd.length); Map<Integer, byte[]> m = new HashMap<>(); for (int i = 0; i < fd.length; i++) m.put(fd[i].getId(), c.get(i)); return m; }
  static Map<Integer, String> refs = new HashMap<>();
  static void ref(int model, String who) { if (model >= 0) refs.merge(model, who, (x, y) -> x.length() > 60 ? x : x + "," + y); }
  static String hash(byte[] b) throws Exception { return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(b)); }
  static Map<Integer, String> modelHashes(Store s, Set<Integer> only) throws Exception { Map<Integer, String> m = new HashMap<>();
    for (Archive a : s.findIndex(7).getArchives()) if (only == null || only.contains(a.getArchiveId())) m.put(a.getArchiveId(), hash(a.decompress(s.getStorage().loadArchive(a)))); return m; }
  public static void main(String[] a) throws Exception {
    Store L = new Store(new File(a[0])); L.load(); Store V1 = new Store(new File(a[1])); V1.load(); Store V2 = new Store(new File(a[2])); V2.load();
    Set<Integer> l = new TreeSet<>(), v1 = new HashSet<>(), v2 = new TreeSet<>();
    for (Archive x : L.findIndex(7).getArchives()) l.add(x.getArchiveId()); for (Archive x : V1.findIndex(7).getArchives()) v1.add(x.getArchiveId()); for (Archive x : V2.findIndex(7).getArchives()) v2.add(x.getArchiveId());
    Set<Integer> custom = new TreeSet<>(); for (int id : l) if (!v1.contains(id)) custom.add(id);
    int errs = 0;
    for (var e : cfg(L, 10).entrySet()) try { ItemDefinition d = new ItemLoader().load(e.getKey(), e.getValue()); String w = "item" + e.getKey();
      for (int m : new int[]{d.inventoryModel, d.maleModel0, d.maleModel1, d.maleModel2, d.maleHeadModel, d.maleHeadModel2, d.femaleModel0, d.femaleModel1, d.femaleModel2, d.femaleHeadModel, d.femaleHeadModel2}) ref(m, w); } catch (Throwable t) { errs++; }
    for (var e : cfg(L, 9).entrySet()) try { NpcDefinition d = new NpcLoader().load(e.getKey(), e.getValue()); if (d.models != null) for (int m : d.models) ref(m, "npc" + e.getKey()); if (d.chatheadModels != null) for (int m : d.chatheadModels) ref(m, "npc" + e.getKey()); } catch (Throwable t) { errs++; }
    for (var e : cfg(L, 6).entrySet()) try { ObjectDefinition d = new ObjectLoader().load(e.getKey(), e.getValue()); if (d.getObjectModels() != null) for (int m : d.getObjectModels()) ref(m, "obj" + e.getKey()); } catch (Throwable t) { errs++; }
    for (var e : cfg(L, 13).entrySet()) try { ref(new SpotAnimLoader().load(e.getKey(), e.getValue()).modelId, "spot" + e.getKey()); } catch (Throwable t) { errs++; }
    for (var e : cfg(L, 3).entrySet()) try { KitDefinition d = new KitLoader().load(e.getKey(), e.getValue()); if (d.models != null) for (int m : d.models) ref(m, "kit" + e.getKey()); if (d.chatheadModels != null) for (int m : d.chatheadModels) ref(m, "kit" + e.getKey()); } catch (Throwable t) { errs++; }
    int ifErr = 0; for (Archive ar : L.findIndex(3).getArchives()) try { FileData[] fd = ar.getFileData(); List<byte[]> c = SpliceItemOption.splitChunks(ar.decompress(L.getStorage().loadArchive(ar)), fd.length);
      for (int i = 0; i < fd.length; i++) try { InterfaceDefinition d = new InterfaceLoader().load((ar.getArchiveId() << 16) | fd[i].getId(), c.get(i)); if (d.modelType == 1) ref(d.modelId, "if" + ar.getArchiveId()); if (d.alternateModelId >= 0) ref(d.alternateModelId, "if" + ar.getArchiveId()); } catch (Throwable t) { ifErr++; } } catch (Throwable t) { ifErr++; }
    Set<Integer> referenced = new TreeSet<>(), unreferenced = new TreeSet<>(); for (int id : custom) (refs.containsKey(id) ? referenced : unreferenced).add(id);
    // dedupe
    Map<Integer, String> ch = modelHashes(L, custom); Map<String, Integer> v241ByHash = new HashMap<>(); for (var e : modelHashes(V2, null).entrySet()) v241ByHash.putIfAbsent(e.getValue(), e.getKey());
    Map<String, List<Integer>> groups = new LinkedHashMap<>(); for (int id : referenced) groups.computeIfAbsent(ch.get(id), k -> new ArrayList<>()).add(id);
    int sameAs241 = 0, uniqueNeeded = 0, dupExtra = 0; for (var e : groups.entrySet()) { if (v241ByHash.containsKey(e.getKey())) sameAs241++; else uniqueNeeded++; dupExtra += e.getValue().size() - 1; }
    int collideKeep = 0, freeKeep = 0; for (int id : referenced) if (v2.contains(id)) collideKeep++; else freeKeep++;
    int max241 = Collections.max(v2); int topFree = 65535 - max241; int gaps = 0; for (int i = 0; i <= max241; i++) if (!v2.contains(i)) gaps++;
    int customAboveMax = 0; for (int id : custom) if (id > max241 && id <= 65535) customAboveMax++;
    System.out.println("RESULT custom models (absent from vanilla 231): " + custom.size() + " range " + ((TreeSet<Integer>) custom).first() + ".." + ((TreeSet<Integer>) custom).last() + "  (decode errors cfg=" + errs + " iface=" + ifErr + ")");
    System.out.println("RESULT   referenced by cache defs: " + referenced.size() + "   unreferenced: " + unreferenced.size());
    System.out.println("RESULT   referenced, content groups: " + groups.size() + " (" + dupExtra + " exact duplicates collapse) of which identical to a vanilla 241 model: " + sameAs241);
    System.out.println("RESULT   => unique custom models that NEED a slot: " + uniqueNeeded);
    System.out.println("RESULT 241 vanilla: max=" + max241 + " count=" + v2.size() + " gaps<=max=" + gaps + " | free slots " + (max241 + 1) + "..65535 = " + topFree);
    System.out.println("RESULT headroom after migration (top block only) = " + (topFree - uniqueNeeded) + " models; vanilla growth 231->241 = " + (max241 - Collections.max(v1)) + " over 10 revs (~" + (max241 - Collections.max(v1)) / 10 + "/rev)");
    Files.write(Paths.get(a[3], "unreferenced_custom_models.txt"), unreferenced.stream().map(String::valueOf).toList());
    List<String> lines = new ArrayList<>(); for (var e : groups.entrySet()) lines.add(e.getValue() + (v241ByHash.containsKey(e.getKey()) ? " == vanilla241 " + v241ByHash.get(e.getKey()) : "") + "  refs: " + refs.get(e.getValue().get(0)));
    Files.write(Paths.get(a[3], "referenced_custom_model_groups.txt"), lines);
  } }

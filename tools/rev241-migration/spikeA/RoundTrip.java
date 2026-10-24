import net.runelite.cache.fs.*; import java.io.File; import java.nio.file.*; import java.util.*; import java.util.regex.*;
// args: v231 v241 keys231.json
public class RoundTrip { public static void main(String[] a) throws Exception {
  Store o = new Store(new File(a[0])); o.load(); Store n = new Store(new File(a[1])); n.load();
  Map<Integer, int[]> keys = new HashMap<>(); Matcher m = Pattern.compile("\"mapsquare\":(\\d+),\"key\":\\[(-?\\d+),(-?\\d+),(-?\\d+),(-?\\d+)").matcher(Files.readString(Path.of(a[2])).replaceAll("\\s", ""));
  while (m.find()) keys.put(Integer.parseInt(m.group(1)), new int[]{Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)), Integer.parseInt(m.group(4)), Integer.parseInt(m.group(5))});
  int tOk = 0, tFail = 0, lOk = 0, lFail = 0, lEq231 = 0, lDiff231 = 0, tEq231 = 0, tDiff231 = 0, no231l = 0, other = 0; Map<String,Integer> fails = new TreeMap<>(); Map<String,Integer> f2 = new TreeMap<>();
  for (Archive na : n.findIndex(5).getArchives()) { int r = na.getArchiveId(); ArchiveFiles af = na.getFiles(n.getStorage().loadArchive(na));
    if (af.getFiles().size() != 5) { other++; System.out.println("RESULT odd group " + r + " files=" + af.getFiles().size()); continue; }
    byte[] t = af.findFile(0).getContents(), l = af.findFile(1).getContents();
    f2.merge("f2len=" + af.findFile(2).getContents().length + " f3=" + Arrays.toString(af.findFile(3).getContents()) + " f4=" + Arrays.toString(af.findFile(4).getContents()), 1, Integer::sum);
    try { if (Arrays.equals(t, MapCodec.encodeTerrain(MapCodec.decodeTerrain(t)))) tOk++; else { tFail++; fails.merge("terrain mismatch", 1, Integer::sum); } } catch (Exception e) { tFail++; fails.merge("terrain " + e.getMessage().replaceAll("[0-9]+", "N"), 1, Integer::sum); }
    try { if (Arrays.equals(l, MapCodec.encodeLocs(MapCodec.decodeLocs(l)))) lOk++; else { lFail++; fails.merge("locs mismatch", 1, Integer::sum); } } catch (Exception e) { lFail++; fails.merge("locs " + e.getClass().getSimpleName() + " " + String.valueOf(e.getMessage()).replaceAll("[0-9]+", "N"), 1, Integer::sum); }
    Archive om = o.findIndex(5).findArchiveByName("m" + (r >> 8) + "_" + (r & 0xff)), ol = o.findIndex(5).findArchiveByName("l" + (r >> 8) + "_" + (r & 0xff));
    if (om != null) { if (Arrays.equals(om.decompress(o.getStorage().loadArchive(om), null), t)) tEq231++; else tDiff231++; }
    if (ol != null && keys.containsKey(r)) { try { byte[] ob = ol.decompress(o.getStorage().loadArchive(ol), keys.get(r)); if (Arrays.equals(ob, l)) lEq231++; else lDiff231++; } catch (Exception e) { no231l++; } } else no231l++; }
  System.out.println("RESULT terrain round-trip ok=" + tOk + " fail=" + tFail + " | locs round-trip ok=" + lOk + " fail=" + lFail + " | odd groups=" + other);
  System.out.println("RESULT vs 231 bytes: terrain equal=" + tEq231 + " differ=" + tDiff231 + " | locs equal=" + lEq231 + " differ=" + lDiff231 + " no231/nokey=" + no231l);
  System.out.println("RESULT failures=" + fails); System.out.println("RESULT file2-4 shapes (top)=" + f2.entrySet().stream().sorted((x, y) -> y.getValue() - x.getValue()).limit(8).toList()); } }

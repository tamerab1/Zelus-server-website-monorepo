import net.runelite.cache.fs.*; import java.io.File; import java.nio.file.Path; import java.util.*;
public class VerifyMapSemantics {
  static String name(Map<Integer, byte[]> objs, int id) { byte[] d = objs.get(id); if (d == null) return null; for (byte[] s : DefRewriter.segments("obj", d, DefRewriter.IDENTITY, k -> null)) if ((s[0]&0xff) == 2) return new String(s, 1, s.length - 2, java.nio.charset.StandardCharsets.ISO_8859_1); return ""; }
  public static void main(String[] a) throws Exception { Store l = new Store(new File(a[0])); l.load(); Store o = new Store(new File(a[1])); o.load(); Manifest m = new Manifest(Path.of(a[2]));
    var lk = new ArrayList<Map<Integer,int[]>>(); lk.add(BuildMaps241.loadKeys(a[3], true, new HashMap<>())); lk.add(BuildMaps241.loadKeys(a[4], false, new HashMap<>()));
    var lObjs = RoundTripDefs.files(l, 6); var oObjs = RoundTripDefs.files(o, 6);
    int regions = 0, tilesOk = 0, locsOk = 0, nameChecks = 0, nameMismatch = 0;
    for (String rs : a[5].split(",")) { int r = Integer.parseInt(rs); regions++;
      var lt = BuildMaps241.terrainAny(BuildMaps241.readNamed(l, "m" + (r>>8) + "_" + (r&0xff), r, lk, false)); var ll = MapCodec.decodeLocs(BuildMaps241.readNamed(l, "l" + (r>>8) + "_" + (r&0xff), r, lk, true));
      Archive g = o.findIndex(5).getArchive(r); ArchiveFiles f = g.getFiles(o.getStorage().loadArchive(g)); var ot = MapCodec.decodeTerrain(f.findFile(0).getContents(), false); var ol = MapCodec.decodeLocs(f.findFile(1).getContents());
      if (BuildMaps241.sameTiles(lt, ot)) tilesOk++; else System.out.println("RESULT terrain differs in " + r);
      List<MapCodec.Loc> mapped = new ArrayList<>(); for (var x : ll) { int n = m.map("obj", x.id); if (n != x.id) { nameChecks++; if (!Objects.equals(name(lObjs, x.id), name(oObjs, n))) { nameMismatch++; if (nameMismatch < 5) System.out.println("RESULT name mismatch " + x.id + "->" + n + " '" + name(lObjs, x.id) + "' vs '" + name(oObjs, n) + "'"); } } mapped.add(new MapCodec.Loc(n, x.plane, x.x, x.y, x.type, x.rotation)); }
      if (BuildMaps241.multiset(mapped).equals(BuildMaps241.multiset(ol))) locsOk++; else System.out.println("RESULT locs differ in " + r); }
    System.out.println("RESULT regions=" + regions + " terrain identical=" + tilesOk + " locs identical (after remap)=" + locsOk + " | remapped loc name checks=" + nameChecks + " mismatches=" + nameMismatch); } }

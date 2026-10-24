import net.runelite.cache.fs.*; import net.runelite.cache.definitions.MapDefinition; import net.runelite.cache.definitions.loaders.MapLoader; import java.io.File; import java.util.*;
// args: v231 v241 local
public class FinalCheck { public static void main(String[] a) throws Exception {
  Store V1 = new Store(new File(a[0])); V1.load(); Store V2 = new Store(new File(a[1])); V2.load(); Store L = new Store(new File(a[2])); L.load();
  int t241 = 0, t241f = 0, l241 = 0, l241f = 0, rlAgree = 0, rlDisagree = 0, extra = 0;
  for (Archive na : V2.findIndex(5).getArchives()) { ArchiveFiles af = na.getFiles(V2.getStorage().loadArchive(na)); if (af.getFiles().size() != 5) continue; int r = na.getArchiveId();
    byte[] t = af.findFile(0).getContents(), l = af.findFile(1).getContents();
    try { MapCodec.Terrain d = MapCodec.decodeTerrain(t, false); extra += d.extraLayers.size(); if (Arrays.equals(t, MapCodec.encodeTerrain(d, true))) t241++; else t241f++;
      MapDefinition.Tile[][][] rl = new MapLoader().load(r >> 8, r & 0xff, t).getTiles(); boolean ok = true;
      for (int z = 0; z < 4 && ok; z++) for (int x = 0; x < 64 && ok; x++) for (int y = 0; y < 64 && ok; y++) { var p = d.planes[z][x][y]; var q = rl[z][x][y];
        int qh = q.height == null ? -1 : q.height; if (p.height != qh || (p.overlay == -1 ? 0 : p.overlay) != (q.overlayId & 0xffff) || (p.underlay == -1 ? 0 : p.underlay) != (q.underlayId & 0xffff)) ok = false; }
      if (ok) rlAgree++; else rlDisagree++; } catch (Exception e) { t241f++; }
    try { if (Arrays.equals(l, MapCodec.encodeLocs(MapCodec.decodeLocs(l)))) l241++; else l241f++; } catch (Exception e) { l241f++; } }
  int t231 = 0, t231f = 0; for (int r = 0; r < 32768; r++) { Archive m = V1.findIndex(5).findArchiveByName("m" + (r >> 8) + "_" + (r & 0xff)); if (m == null) continue; byte[] t = m.decompress(V1.getStorage().loadArchive(m), null);
    try { if (Arrays.equals(t, MapCodec.encodeTerrain(MapCodec.decodeTerrain(t, true), false))) t231++; else t231f++; } catch (Exception e) { t231f++; } }
  int loc231 = 0, loc241 = 0, locf = 0; for (int r = 0; r < 32768; r++) { Archive m = L.findIndex(5).findArchiveByName("m" + (r >> 8) + "_" + (r & 0xff)); if (m == null) continue; byte[] t = m.decompress(L.getStorage().loadArchive(m), null);
    MapCodec.Terrain d; boolean was241; try { d = MapCodec.decodeTerrain(t, true); was241 = false; } catch (Exception e) { try { d = MapCodec.decodeTerrain(t, false); was241 = true; } catch (Exception e2) { locf++; continue; } }
    byte[] conv = MapCodec.encodeTerrain(d, true); MapCodec.Terrain back = MapCodec.decodeTerrain(conv, false);
    if (Arrays.equals(MapCodec.encodeTerrain(back, false), MapCodec.encodeTerrain(d, false)) && (was241 ? Arrays.equals(conv, t) : Arrays.equals(MapCodec.encodeTerrain(d, false), t))) { if (was241) loc241++; else loc231++; } else locf++; }
  System.out.println("RESULT vanilla 241 terrain byte round-trip ok=" + t241 + " fail=" + t241f + " (extra layers seen=" + extra + ")  | independent RuneLite MapLoader agrees on planes=" + rlAgree + " disagrees=" + rlDisagree);
  System.out.println("RESULT vanilla 241 locs byte round-trip ok=" + l241 + " fail=" + l241f);
  System.out.println("RESULT vanilla 231 terrain byte round-trip (231 grammar) ok=" + t231 + " fail=" + t231f);
  System.out.println("RESULT local regions -> 241 conversion exact: from231=" + loc231 + " alreadyNewFormat=" + loc241 + " fail=" + locf); } }

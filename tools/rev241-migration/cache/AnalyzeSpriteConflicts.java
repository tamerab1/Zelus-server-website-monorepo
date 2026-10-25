import net.runelite.cache.fs.*;
import java.io.File;
import java.util.*;

/** Round-trips every sprite group of each cache through SpriteCodec, then classifies the reskin conflicts. args: local v231 v241 conflictIds(json report) */
public class AnalyzeSpriteConflicts {
	static Map<Integer, byte[]> sprites(Store s) throws Exception {
		Map<Integer, byte[]> m = new TreeMap<>();
		for (Archive ar : s.findIndex(8).getArchives()) m.put(ar.getArchiveId(), ar.decompress(s.getStorage().loadArchive(ar)));
		return m;
	}

	public static void main(String[] a) throws Exception {
		Store l = new Store(new File(a[0])); l.load();
		Store b = new Store(new File(a[1])); b.load();
		Store t = new Store(new File(a[2])); t.load();
		var L = sprites(l); var B = sprites(b); var T = sprites(t);
		for (var e : List.of(Map.entry("ours", L), Map.entry("231", B), Map.entry("241", T))) {
			int ok = 0, fail = 0;
			for (var x : e.getValue().entrySet()) {
				byte[] d = x.getValue();
				boolean good;
				try { good = Arrays.equals(SpriteCodec.encode(SpriteCodec.decode(d)), d); } catch (Exception ex) { good = false; }
				if (!good) System.out.println("RESULT   " + e.getKey() + " sprite " + x.getKey() + " does not round-trip (" + d.length + " bytes)");
			}
			for (byte[] d : e.getValue().values()) {
				try { if (Arrays.equals(SpriteCodec.encode(SpriteCodec.decode(d)), d)) ok++; else { fail++; } } catch (Exception ex) { fail++; }
			}
			System.out.println("RESULT round trip " + e.getKey() + ": ok=" + ok + " fail=" + fail);
		}
		var rep = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(java.nio.file.Path.of(a[3]))).getAsJsonObject().getAsJsonObject("sprites").getAsJsonArray("reskinsPendingFrameMergeKeptVanilla241");
		Map<String, Integer> cats = new TreeMap<>();
		for (var je : rep) {
			int id = je.getAsInt();
			if (B.get(id) == null || L.get(id) == null || T.get(id) == null) { cats.merge("missing in " + (B.get(id) == null ? "231 " : "") + (T.get(id) == null ? "241 " : "") + (L.get(id) == null ? "ours" : ""), 1, Integer::sum); continue; }
			var gb = SpriteCodec.decode(B.get(id)); var go = SpriteCodec.decode(L.get(id)); var gt = SpriteCodec.decode(T.get(id));
			int nb = gb.frames.size(), no = go.frames.size(), nt = gt.frames.size();
			int oursOnly = 0, theirsOnly = 0, both = 0;
			for (int i = 0; i < nb; i++) {
				boolean oc = i >= no || !SpriteCodec.sameFrame(gb, gb.frames.get(i), go, go.frames.get(i));
				boolean tc = i >= nt || !SpriteCodec.sameFrame(gb, gb.frames.get(i), gt, gt.frames.get(i));
				if (oc && tc) both++; else if (oc) oursOnly++; else if (tc) theirsOnly++;
			}
			String canvas = (go.maxWidth == gt.maxWidth && go.maxHeight == gt.maxHeight) ? "sameCanvas" : (gb.maxWidth == gt.maxWidth && gb.maxHeight == gt.maxHeight ? "oursCanvasChanged" : "jagexCanvasChanged");
			String cat = "frames base/ours/241 " + (no == nb ? "=" : no > nb ? "ours+" : "ours-") + "/" + (nt == nb ? "=" : nt > nb ? "241+" : "241-")
				+ " | " + (both > 0 ? "sameFrameBothChanged" : "disjoint") + " | " + canvas;
			cats.merge(cat, 1, Integer::sum);
		}
		cats.forEach((k, v) -> System.out.println("RESULT " + v + "  " + k));
	}
}

import net.runelite.cache.fs.*;
import java.io.File;
import java.util.*;

/** Identity round trip of ScriptRewriter over every script; plus 231->241->231 conversion. args: cache:rev237 ... */
public class RoundTripScripts {
	static Map<Integer, byte[]> scripts(Store s) throws Exception {
		Map<Integer, byte[]> m = new TreeMap<>();
		for (Archive ar : s.findIndex(12).getArchives()) m.put(ar.getArchiveId(), ar.decompress(s.getStorage().loadArchive(ar)));
		return m;
	}

	public static void main(String[] a) throws Exception {
		for (String arg : a) {
			String path = arg.substring(0, arg.lastIndexOf(':'));
			boolean r237 = arg.endsWith(":true");
			Store s = new Store(new File(path));
			s.load();
			int ok = 0, fail = 0, convOk = 0, convFail = 0;
			Map<String, Integer> errs = new TreeMap<>();
			for (var e : scripts(s).entrySet()) {
				try {
					byte[] o = ScriptRewriter.run(e.getValue(), DefRewriter.IDENTITY, r237, r237, (i, v) -> v).bytes();
					if (Arrays.equals(o, e.getValue())) ok++; else { fail++; errs.merge("mismatch", 1, Integer::sum); }
					if (!r237) {
						byte[] up = ScriptRewriter.run(e.getValue(), DefRewriter.IDENTITY, false, true, (i, v) -> v).bytes();
						byte[] down = ScriptRewriter.run(up, DefRewriter.IDENTITY, true, false, (i, v) -> v).bytes();
						if (Arrays.equals(down, e.getValue())) convOk++; else convFail++;
					}
				} catch (Exception ex) {
					fail++;
					errs.merge(String.valueOf(ex.getMessage()).replaceAll("[0-9]+", "N"), 1, Integer::sum);
				}
			}
			System.out.println("RESULT " + path.replaceAll(".*(openrs2_[0-9]+|rev241/data/cache).*", "$1") + " (rev237=" + r237 + "): scripts ok=" + ok + " fail=" + fail
				+ (r237 ? "" : " | 231->241->231 ok=" + convOk + " fail=" + convFail) + (errs.isEmpty() ? "" : " " + errs));
		}
	}
}

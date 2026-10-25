import net.runelite.cache.fs.*;
import net.runelite.cache.index.FileData;
import java.io.File;
import java.util.*;

/** Identity round trip of InterfaceRewriter over all components; plus 231->241->231 model conversion. args: cache:wide ... */
public class RoundTripInterfaces {
	static Map<Integer, Map<Integer, byte[]>> components(Store s) throws Exception {
		Map<Integer, Map<Integer, byte[]>> m = new TreeMap<>();
		for (Archive ar : s.findIndex(3).getArchives()) {
			FileData[] fd = ar.getFileData();
			byte[] raw = ar.decompress(s.getStorage().loadArchive(ar));
			List<byte[]> c = fd.length == 1 ? List.of(raw) : SpliceItemOption.splitChunks(raw, fd.length);
			Map<Integer, byte[]> g = new TreeMap<>();
			for (int i = 0; i < fd.length; i++) g.put(fd[i].getId(), c.get(i));
			m.put(ar.getArchiveId(), g);
		}
		return m;
	}

	public static void main(String[] a) throws Exception {
		for (String arg : a) {
			String path = arg.substring(0, arg.lastIndexOf(':'));
			boolean wide = arg.endsWith(":true");
			Store s = new Store(new File(path));
			s.load();
			int ok = 0, fail = 0, if1 = 0, if3 = 0, convOk = 0, convFail = 0;
			Map<String, Integer> errs = new TreeMap<>();
			for (var g : components(s).entrySet()) {
				for (var c : g.getValue().entrySet()) {
					byte[] b = c.getValue();
					if ((b[0] & 0xff) == 0xff) if3++; else if1++;
					try {
						byte[] o = InterfaceRewriter.run(b, DefRewriter.IDENTITY, wide, wide, v -> false).bytes();
						if (Arrays.equals(o, b)) ok++; else { fail++; errs.merge("mismatch", 1, Integer::sum); }
						if (!wide) {
							byte[] up = InterfaceRewriter.run(b, DefRewriter.IDENTITY, false, true, v -> false).bytes();
							byte[] down = InterfaceRewriter.run(up, DefRewriter.IDENTITY, true, false, v -> false).bytes();
							if (Arrays.equals(down, b)) convOk++; else convFail++;
						}
					} catch (Exception e) {
						fail++;
						errs.merge(String.valueOf(e.getMessage()).replaceAll("[0-9]+ bytes", "N bytes"), 1, Integer::sum);
					}
				}
			}
			System.out.println("RESULT " + path.replaceAll(".*(openrs2_[0-9]+|rev241/data/cache).*", "$1") + " (wide=" + wide + "): components ok=" + ok + " fail=" + fail + " IF3=" + if3 + " IF1=" + if1
				+ (wide ? "" : " | 231->241->231 conversion ok=" + convOk + " fail=" + convFail) + (errs.isEmpty() ? "" : " " + errs));
		}
	}
}

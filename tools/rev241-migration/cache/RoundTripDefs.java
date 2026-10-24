import net.runelite.cache.fs.*;
import net.runelite.cache.index.FileData;
import java.io.File;
import java.util.*;

/** Identity round trip of DefRewriter over every definition of each supported config type. args: cache... */
public class RoundTripDefs {
	static final Map<String, Integer> GROUPS = new LinkedHashMap<>();
	static {
		GROUPS.put("item", 10); GROUPS.put("npc", 9); GROUPS.put("obj", 6); GROUPS.put("seq", 12);
		GROUPS.put("spotanim", 13); GROUPS.put("varbit", 14); GROUPS.put("enum", 8); GROUPS.put("struct", 34);
		GROUPS.put("param", 11); GROUPS.put("inv", 5); GROUPS.put("overlay", 4); GROUPS.put("kit", 3);
	}

	static Map<Integer, byte[]> files(Store s, int group) throws Exception {
		Archive ar = s.findIndex(2).getArchive(group);
		FileData[] fd = ar.getFileData();
		List<byte[]> c = SpliceItemOption.splitChunks(ar.decompress(s.getStorage().loadArchive(ar)), fd.length);
		Map<Integer, byte[]> m = new TreeMap<>();
		for (int i = 0; i < fd.length; i++) m.put(fd[i].getId(), c.get(i));
		return m;
	}

	public static void main(String[] args) throws Exception {
		for (String path : args) {
			Store s = new Store(new File(path));
			s.load();
			DefRewriter.ParamKinds pk = DefRewriter.paramKinds(files(s, 11));
			StringBuilder line = new StringBuilder("RESULT " + new File(path).getParentFile().getName() + "/" + new File(path).getName() + ":");
			for (var g : GROUPS.entrySet()) {
				int ok = 0, fail = 0;
				Map<String, Integer> errs = new TreeMap<>();
				for (var e : files(s, g.getValue()).entrySet()) {
					try {
						byte[] o = DefRewriter.rewrite(g.getKey(), e.getValue(), DefRewriter.IDENTITY, pk);
						if (Arrays.equals(o, e.getValue())) ok++;
						else { fail++; errs.merge("mismatch", 1, Integer::sum); }
					} catch (Exception ex) {
						fail++;
						errs.merge(String.valueOf(ex.getMessage()).replaceAll("[0-9]+ bytes", "N bytes"), 1, Integer::sum);
					}
				}
				line.append(" ").append(g.getKey()).append("=").append(ok).append(fail == 0 ? "" : "/FAIL" + fail + errs);
			}
			System.out.println(line);
		}
	}
}

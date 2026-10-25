import net.runelite.cache.fs.*;
import java.io.File;
import java.util.*;

/**
 * Prints the right-click ops (and op base name) of interface components, to see what a renamed or
 * redesigned component does. args: cache wide(true|false) group:comp[,comp...] ...
 */
public class ComponentOps {
	public static void main(String[] a) throws Exception {
		Store s = new Store(new File(a[0]));
		s.load();
		boolean wide = Boolean.parseBoolean(a[1]);
		var all = RoundTripInterfaces.components(s);
		for (int k = 2; k < a.length; k++) {
			String[] gc = a[k].split(":");
			int g = Integer.parseInt(gc[0]);
			for (String c : gc[1].split(",")) {
				byte[] comp = all.get(g).get(Integer.parseInt(c));
				if (comp == null) { System.out.println(g + ":" + c + " missing"); continue; }
				InterfaceRewriter r = InterfaceRewriter.run(comp, DefRewriter.IDENTITY, wide, wide, v -> false);
				List<String> ops = new ArrayList<>();
				if (r.actionsStart >= 0) {
					int p = r.actionsStart;
					int n = comp[p++] & 0xff;
					for (int i = 0; i < n; i++) {
						StringBuilder sb = new StringBuilder();
						while (comp[p] != 0) sb.append((char) (comp[p++] & 0xff));
						p++;
						ops.add(sb.toString());
					}
				}
				System.out.println(g + ":" + c + " ops=" + ops);
			}
		}
	}
}

import net.runelite.cache.fs.*;
import java.io.*;
import java.util.*;
/** Writes the defined config ids per type of a cache as JSON. args: cache out.json */
public class DumpIds {
	public static void main(String[] a) throws Exception {
		Store s = new Store(new File(a[0])); s.load();
		StringBuilder sb = new StringBuilder("{");
		boolean first = true;
		for (var g : RoundTripDefs.GROUPS.entrySet()) {
			if (!first) sb.append(','); first = false;
			sb.append('"').append(g.getKey()).append("\":").append(RoundTripDefs.files(s, g.getValue()).keySet());
		}
		sb.append('}');
		try (Writer w = new FileWriter(a[1])) { w.write(sb.toString()); }
	}
}

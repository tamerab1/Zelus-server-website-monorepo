import net.runelite.cache.fs.Store;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.StringJoiner;

/**
 * Varbits whose (varp, lsb, msb) differ between two caches, as JSON for the save migration's varp relayout:
 * {"id": [oldVarp, oldLsb, oldMsb, newVarp, newLsb, newMsb], ...}. Only varbits defined in both caches.
 *
 * args: ourCache231 cache241 out.json
 */
public class VarbitMoves {
	public static void main(String[] a) throws Exception {
		Store x = new Store(new File(a[0])); x.load();
		Store y = new Store(new File(a[1])); y.load();
		var A = RoundTripDefs.files(x, 14);
		var B = RoundTripDefs.files(y, 14);
		StringJoiner json = new StringJoiner(",\n", "{\n", "\n}\n");
		int n = 0;
		for (var e : A.entrySet()) {
			byte[] p = e.getValue(), q = B.get(e.getKey());
			if (q == null || p.length < 5 || q.length < 5 || p[0] != 1 || q[0] != 1)
				continue;
			int vp = ((p[1] & 0xff) << 8) | (p[2] & 0xff), vq = ((q[1] & 0xff) << 8) | (q[2] & 0xff);
			if (vp == vq && p[3] == q[3] && p[4] == q[4])
				continue;
			json.add("\"" + e.getKey() + "\": [" + vp + ", " + (p[3] & 0xff) + ", " + (p[4] & 0xff) + ", " + vq + ", " + (q[3] & 0xff) + ", " + (q[4] & 0xff) + "]");
			n++;
		}
		Files.writeString(Path.of(a[2]), json.toString());
		System.out.println("moved/resized varbits: " + n + " -> " + a[2]);
	}
}

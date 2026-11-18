import net.runelite.cache.fs.Store;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;

/**
 * Stage 40 of the rev 241 cache build (2026-11-14): writes every models/&lt;id&gt;.dat of an asset folder over the cache's
 * model of that id (used for assets/valkyrie_fix: the Valkyrie set's face priorities, MakeValkyriePriorities; run after
 * stage 15, which adds those models). Idempotent. Usage: PatchModelFiles apply &lt;cache241&gt; &lt;assetDir&gt;
 */
public final class PatchModelFiles {

	private PatchModelFiles() {
	}

	public static void main(String[] a) throws Exception {
		if (a.length != 3 || !a[0].equals("apply"))
			throw new IllegalArgumentException("apply <cache241> <assetDir>");
		Store dst = new Store(new File(a[1]));
		dst.load();
		File[] files = new File(a[2], "models").listFiles((d, n) -> n.endsWith(".dat"));
		if (files == null || files.length == 0)
			throw new IllegalStateException("no models in " + a[2]);
		Arrays.sort(files);
		int changed = 0;
		for (File f : files) {
			int id = Integer.parseInt(f.getName().replace(".dat", ""));
			if (dst.findIndex(7).getArchive(id) == null)
				throw new IllegalStateException("model " + id + " isn't in the cache: run after the stage that adds it");
			changed += PatchSolarmane.putSingle(dst, 7, id, Files.readAllBytes(f.toPath())) ? 1 : 0;
		}
		dst.close();
		System.out.println("RESULT model files: " + changed + " change(s) of " + files.length + " from " + a[2]);
	}
}

import net.runelite.cache.fs.*;

import java.io.File;
import java.nio.file.Files;
import java.util.*;

/**
 * Stage 35: merged Teleports (1152) preview models for the PvM overhaul bosses (2026-11-13): the Moons of Peril
 * (Blue Moon vis parts 52534 + 56306 -> 62778) and The Hueycoatl (head parts 54462 + 54461 -> 62779), written by
 * .dev/cache-restore-tool BuildTeleportPreviews "parts" lines (assets/teleport_previews_pvm/spec.txt). Every
 * &lt;id&gt;.dat in the assets folder becomes a single-file model group at that id (free in 241). Their bounds are in
 * data/teleport_previews.json. Usage: PatchTeleportPreviews apply &lt;cache241Dir&gt; &lt;assets/teleport_previews_pvm&gt;. Idempotent.
 */
public final class PatchTeleportPreviews {

	private PatchTeleportPreviews() {
	}

	public static void main(String[] a) throws Exception {
		if (a.length != 3 || !a[0].equals("apply"))
			throw new IllegalArgumentException("apply <cache241> <assets/teleport_previews_pvm>");
		File[] files = new File(a[2]).listFiles((d, n) -> n.matches("[0-9]+[.]dat"));
		Arrays.sort(files);
		Store dst = new Store(new File(a[1]));
		dst.load();
		int changed = 0;
		for (File f : files)
			changed += PatchEliteVoid.putModel(dst, Integer.parseInt(f.getName().replace(".dat", "")), Files.readAllBytes(f.toPath()));
		dst.close();
		System.out.println("RESULT teleport previews: " + changed + " change(s)");
	}
}

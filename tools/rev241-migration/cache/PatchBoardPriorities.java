import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Store;

import java.io.File;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;

/**
 * Stage 18 of the rev 241 cache build (2026-11-06): GPU-safe face priorities for the code-built boards (Pit Championship
 * 65043, Zelus Scoreboard 65044, Rivals Leaderboard 65048; objects 44930 / 65471 / 65472). The GPU plugin draws face
 * priorities 0-9 in order and depth-sorts only within one, so trims, plates and painted panels lying a few units in front
 * of a larger face at the same priority swapped behind it as the camera turned (the "boards break when rotating" report,
 * worst under GPU). 2026-11-06 round 2 (screenshots: the crest's red field / swords / star flickered gold, split
 * diagonally, changed with zoom = z-fighting on depth-buffered renderers): the layers were only 1 unit apart, so every
 * layer face is also pushed out along its normal to 4x its offset (SpreadLayers: 1 -> 4, 2 -> 8, 3 -> 12).
 * assets/board_priorities/&lt;id&gt;.dat (FixBoardPriorities, rev 241 library: every such face one priority
 * above the face it lies on; geometry, paint and UVs unchanged) replaces the model only if the cache holds the original
 * (sha256 in originals.txt) or already this. Usage: PatchBoardPriorities apply &lt;cache241&gt; &lt;assets/board_priorities&gt;
 */
public final class PatchBoardPriorities {

	private PatchBoardPriorities() {
	}

	public static void main(String[] a) throws Exception {
		if (a.length != 3 || !a[0].equals("apply"))
			throw new IllegalArgumentException("apply <cache241> <assets/board_priorities>");
		File dir = new File(a[2]);
		// id -> the sha256s it may be replaced from: the original board, then earlier versions of this fix
		Map<Integer, Set<String>> originals = new TreeMap<>();
		for (String line : Files.readAllLines(new File(dir, "originals.txt").toPath()))
			if (!line.isBlank())
				originals.computeIfAbsent(Integer.parseInt(line.split(" ")[0]), k -> new HashSet<>()).add(line.split(" ")[1]);
		Store dst = new Store(new File(a[1]));
		dst.load();
		int changed = 0;
		for (var e : originals.entrySet()) {
			byte[] want = Files.readAllBytes(new File(dir, e.getKey() + ".dat").toPath());
			Archive ar = dst.findIndex(7).getArchive(e.getKey());
			if (ar == null)
				throw new IllegalStateException("model " + e.getKey() + " missing");
			byte[] have = ar.decompress(dst.getStorage().loadArchive(ar));
			if (Arrays.equals(have, want))
				continue;
			String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(have));
			if (!e.getValue().contains(sha))
				throw new IllegalStateException("model " + e.getKey() + " is neither the original board nor the fix: rebuild the fix");
			changed += PatchSolarmane.putSingle(dst, 7, e.getKey(), want) ? 1 : 0;
		}
		dst.close();
		System.out.println("RESULT board priorities: " + changed + " model(s) written of " + originals.size());
	}
}

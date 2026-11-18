import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Store;

import java.io.File;
import java.nio.file.Files;
import java.util.TreeMap;

/**
 * Stage 42 of the rev 241 cache build (user 2026-11-15): the Karn hub windows from the user's mock-ups --
 * 1185 the Welcome screen (every login), 1186 the Battle Pass, 1187 Best in Slot, 1188 Daily Tasks (all free in rev 241).
 * <ul>
 * <li>sprites 25220-25259 (assets/karn_hub/png, build_hub.py: backgrounds, buttons, tabs, rows, overlays);</li>
 * <li>the groups: rev 231 components exported by .dev/cache-restore-tool BuildKarnHub (assets/karn_hub/interface_&lt;g&gt;),
 * converted to rev 241 the way stage 21 converts the Colosseum's (InterfaceRewriter, identity defs).</li>
 * </ul>
 * Idempotent. Usage: PatchKarnHub apply &lt;cache241&gt; &lt;assets/karn_hub&gt;
 */
public final class PatchKarnHub {

	private PatchKarnHub() {
	}

	static final int[] GROUPS = { 1185, 1186, 1187, 1188, 1189, 1190, 1191, 1192 }; // 1189-1192: The Five Seals HUD / Party Board / Hall / Token Shop (2026-11-16)

	public static void main(String[] a) throws Exception {
		if (a.length != 3 || !a[0].equals("apply"))
			throw new IllegalArgumentException("apply <cache241> <assets/karn_hub>");
		File dir = new File(a[2]);
		Store dst = new Store(new File(a[1]));
		dst.load();
		int changed = 0;
		for (File f : new File(dir, "png").listFiles())
			if (f.getName().matches("\\d+\\.png"))
				changed += PatchSolarmane.putSingle(dst, 8, Integer.parseInt(f.getName().replace(".png", "")), BuildBinary241.pngSprite(f.toPath())) ? 1 : 0;

		// 1-slot inventories for the item grids (clientscript 149) of the Battle Pass (3100-3109) and Daily Tasks
		// (3200-3210): the client sizes a grid from the inv config, and these ids had none, so nothing showed
		Archive invs = dst.findIndex(2).getArchive(5);
		TreeMap<Integer, byte[]> invFiles = new TreeMap<>(BuildBinary241.files(dst, invs));
		boolean invChanged = false;
		for (int[] range : new int[][] { { 3100, 3109 }, { 3200, 3210 } })
			for (int id = range[0]; id <= range[1]; id++)
				if (!invFiles.containsKey(id)) {
					invFiles.put(id, new byte[] { 2, 0, 1, 0 }); // opcode 2: size 1; end
					invChanged = true;
				}
		if (invChanged) {
			PatchCustoms27.writeGroup(dst, 2, 5, invFiles); // TreeMap = sorted file ids
			changed++;
		}

		// the minimap Battle Pass button (BuildKarnHub exportBattlePassOrb): the store orb's parts 160:43-47 replaced; no
		// gap allowed (a hole in a group's component ids NPEs the client); identity conversion (the parts already use 241 ids)
		File orbDir = new File(dir, "orb_160");
		if (orbDir.isDirectory()) {
			Archive orbs = dst.findIndex(3).getArchive(160);
			TreeMap<Integer, byte[]> files = new TreeMap<>(BuildBinary241.files(dst, orbs));
			// the first version appended its own orb at 62-66 (2026-11-15, replaced the same day by the store orb): drop it
			TreeMap<Integer, byte[]> merged = new TreeMap<>(files.headMap(62));
			for (File f : orbDir.listFiles())
				merged.put(Integer.parseInt(f.getName().replace(".dat", "")),
						InterfaceRewriter.run(Files.readAllBytes(f.toPath()), DefRewriter.IDENTITY, false, true, v -> false).bytes());
			if (!merged.firstKey().equals(0) || merged.lastKey() != merged.size() - 1)
				throw new IllegalStateException("160 would have a hole in its component ids: " + merged.keySet());
			if (!PatchEggIcons.sameFiles(files, merged)) {
				PatchCustoms27.writeGroup(dst, 3, 160, merged);
				changed++;
			}
		}

		Index idx = dst.findIndex(3);
		StringBuilder sizes = new StringBuilder();
		for (int group : GROUPS) {
			TreeMap<Integer, byte[]> comps = new TreeMap<>();
			for (File f : new File(dir, "interface_" + group).listFiles())
				comps.put(Integer.parseInt(f.getName().replace(".dat", "")),
						InterfaceRewriter.run(Files.readAllBytes(f.toPath()), DefRewriter.IDENTITY, false, true, v -> false).bytes());
			if (!comps.firstKey().equals(0) || comps.lastKey() != comps.size() - 1)
				throw new IllegalStateException("interface " + group + " components aren't 0.." + (comps.size() - 1));
			Archive ar = idx.getArchive(group);
			if (ar == null) {
				ar = idx.addArchive(group);
				ar.setCompression(idx.getArchive(1002).getCompression());
				PatchCustoms27.writeGroup(dst, 3, group, comps);
				changed++;
			} else if (!PatchEggIcons.sameFiles(BuildBinary241.files(dst, ar), comps)) {
				PatchCustoms27.writeGroup(dst, 3, group, comps);
				changed++;
			}
			sizes.append(" ").append(group).append(" (").append(comps.size()).append(")");
		}
		dst.close();
		System.out.println("RESULT karn hub: " + changed + " change(s); interfaces" + sizes);
	}
}

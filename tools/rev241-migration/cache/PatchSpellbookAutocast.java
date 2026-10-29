import net.runelite.cache.fs.*;
import net.runelite.cache.index.FileData;

import java.io.File;
import java.util.*;

/**
 * Stage 9 (2026-10-29): an "Autocast" option (op 9, slot 8, as the home teleports' "Previous") on every autocastable
 * combat spell of the spellbook (218), so autocast can be set from the spellbook instead of the combat tab's staff
 * screen. The server (TabMagic) handles op 9 on these components. Components = SpellBook.component(i) of every spell
 * with an autocast slot (modern 10 + i, ancient 84 + i; checked against gameval MagicSpellbook).
 * Usage: PatchSpellbookAutocast apply &lt;cache241Dir&gt;. Idempotent.
 */
public final class PatchSpellbookAutocast {

	private PatchSpellbookAutocast() {
	}

	static final int GROUP = 218, SLOT = 8;
	static final String TEXT = "Autocast";
	static final int[] COMPONENTS = {
			// modern: strikes, bolts, crumble undead, blasts, Iban blast, magic dart, god spells, waves, surges
			11, 14, 17, 19, 21, 25, 28, 31, 33, 35, 38, 41, 43, 45, 51, 54, 55, 56, 58, 61, 65, 68, 72, 74, 79, 81,
			// ancients: rush / blitz / burst / barrage of ice, blood, smoke, shadow
			84, 85, 86, 87, 88, 89, 90, 91, 92, 93, 94, 95, 96, 97, 98, 99,
	};

	public static void main(String[] a) throws Exception {
		if (a.length != 2 || !a[0].equals("apply"))
			throw new IllegalArgumentException("apply <cache241>");
		Store dst = new Store(new File(a[1]));
		dst.load();
		Index index = dst.findIndex(3);
		Archive ar = index.getArchive(GROUP);
		byte[] raw = ar.decompress(dst.getStorage().loadArchive(ar));
		FileData[] fd = ar.getFileData();
		List<byte[]> chunks = SpliceItemOption.splitChunks(raw, fd.length);
		TreeMap<Integer, byte[]> files = new TreeMap<>();
		for (int i = 0; i < fd.length; i++)
			files.put(fd[i].getId(), chunks.get(i));
		int changed = 0;
		for (int c : COMPONENTS) {
			byte[] comp = files.get(c);
			if (comp == null)
				throw new IllegalStateException("218:" + c + " is missing");
			byte[] out = InterfaceRewriter.withAction(comp, SLOT, TEXT);
			if (!Arrays.equals(out, comp)) {
				files.put(c, out);
				changed++;
			}
		}
		if (changed > 0)
			PatchCustoms27.writeGroup(dst, 3, GROUP, files);
		dst.close();
		System.out.println("RESULT spellbook autocast: " + changed + " component(s)");
	}
}

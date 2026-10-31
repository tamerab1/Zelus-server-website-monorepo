import net.runelite.cache.fs.*;
import net.runelite.cache.index.FileData;

import java.io.File;
import java.util.*;

/**
 * Stage 9 (2026-10-29): an "Autocast" option (op 9, slot 8, as the home teleports' "Previous") on every autocastable
 * combat spell of the spellbook (218), so autocast can be set from the spellbook instead of the combat tab's staff
 * screen. The server (TabMagic) handles op 9 on these components. Components = SpellBook.component(i) of every spell
 * with an autocast slot (modern 10 + i, ancient 84 + i; checked against gameval MagicSpellbook).
 * Also (2026-10-31) an "Animation" option (op 2, slot 1) on the four home teleports (6/108/109/153 = standard/Zaros/
 * Lunar/Arceuus): TabMagic opens the teleport animation picker (ModernTeleport.chooseStyle).
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

	static final int HOME_SLOT = 1;
	static final String HOME_TEXT = "Animation";
	static final int[] HOME_TELEPORTS = {6, 108, 109, 153};

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
		for (int c : COMPONENTS)
			changed += addAction(files, c, SLOT, TEXT);
		for (int c : HOME_TELEPORTS)
			changed += addAction(files, c, HOME_SLOT, HOME_TEXT);
		if (changed > 0)
			PatchCustoms27.writeGroup(dst, 3, GROUP, files);
		dst.close();
		System.out.println("RESULT spellbook autocast + home animation: " + changed + " component(s)");
	}

	private static int addAction(TreeMap<Integer, byte[]> files, int c, int slot, String text) {
		byte[] comp = files.get(c);
		if (comp == null)
			throw new IllegalStateException("218:" + c + " is missing");
		byte[] out = InterfaceRewriter.withAction(comp, slot, text);
		if (Arrays.equals(out, comp))
			return 0;
		files.put(c, out);
		return 1;
	}
}

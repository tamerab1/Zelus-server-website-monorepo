import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Store;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;

/**
 * Stage 56 of the rev 241 cache build (user 2026-11-17: "when players open the rewards chest after finishing room 5 can we
 * make it opening the rewards in an interface like the other raids?"): interface {@link #GROUP} "The Sovereign Vault", a
 * copy of the raid chest window tfa_chests ({@link #SOURCE}: 6-slot grid + Bank-all / Take-all / Discard-all) whose
 * steelborder title (component 1's onload script 227) reads {@link #TITLE} instead of "The Fractured Archive". Any
 * 973:child component hash inside the copy is moved to 1193. Idempotent. Usage: PatchSealsVaultIf apply &lt;cache241&gt;
 */
public final class PatchSealsVaultIf {

	private PatchSealsVaultIf() {
	}

	static final int SOURCE = 973, GROUP = 1193;
	static final String OLD_TITLE = "The Fractured Archive", TITLE = "The Sovereign Vault";

	public static void main(String[] a) throws Exception {
		if (a.length != 2 || !a[0].equals("apply"))
			throw new IllegalArgumentException("apply <cache241>");
		Store dst = new Store(new File(a[1]));
		dst.load();
		Index idx = dst.findIndex(3);
		Map<Integer, byte[]> src = BuildBinary241.files(dst, idx.getArchive(SOURCE));
		TreeMap<Integer, byte[]> comps = new TreeMap<>();
		int titles = 0, hashes = 0;
		for (Map.Entry<Integer, byte[]> e : src.entrySet()) {
			byte[] b = e.getValue();
			byte[] old = OLD_TITLE.getBytes(StandardCharsets.ISO_8859_1);
			int at = indexOf(b, old);
			if (at >= 0) {
				byte[] t = TITLE.getBytes(StandardCharsets.ISO_8859_1);
				byte[] n = new byte[b.length - old.length + t.length];
				System.arraycopy(b, 0, n, 0, at);
				System.arraycopy(t, 0, n, at, t.length);
				System.arraycopy(b, at + old.length, n, at + t.length, b.length - at - old.length);
				b = n;
				titles++;
			}
			for (int i = 0; i + 3 < b.length; i++) // a 973:child hash (u16 group, u16 child < the group's size)
				if ((b[i] & 0xff) == (SOURCE >> 8) && (b[i + 1] & 0xff) == (SOURCE & 0xff) && b[i + 2] == 0 && (b[i + 3] & 0xff) < src.size()) {
					b[i] = (byte) (GROUP >> 8);
					b[i + 1] = (byte) GROUP;
					hashes++;
				}
			comps.put(e.getKey(), b);
		}
		if (titles != 1)
			throw new IllegalStateException(SOURCE + ": expected the title once, found it " + titles + " times");
		if (!comps.firstKey().equals(0) || comps.lastKey() != comps.size() - 1)
			throw new IllegalStateException(SOURCE + " components aren't 0.." + (comps.size() - 1));
		int changed = 0;
		Archive ar = idx.getArchive(GROUP);
		if (ar == null) {
			ar = idx.addArchive(GROUP);
			ar.setCompression(idx.getArchive(1002).getCompression());
			PatchCustoms27.writeGroup(dst, 3, GROUP, comps);
			changed++;
		} else if (!PatchEggIcons.sameFiles(BuildBinary241.files(dst, ar), comps)) {
			PatchCustoms27.writeGroup(dst, 3, GROUP, comps);
			changed++;
		}
		dst.close();
		System.out.println("RESULT seals vault interface: " + changed + " change(s); " + GROUP + " = " + SOURCE + " (" + comps.size()
			+ " components, " + hashes + " self-reference(s) moved) titled '" + TITLE + "'");
	}

	private static int indexOf(byte[] b, byte[] s) {
		outer:
		for (int i = 0; i + s.length <= b.length; i++) {
			for (int k = 0; k < s.length; k++)
				if (b[i + k] != s[k])
					continue outer;
			return i;
		}
		return -1;
	}
}

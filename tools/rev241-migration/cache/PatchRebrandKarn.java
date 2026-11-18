import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Container;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;

import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * Stage 27 of the rev 241 cache build (2026-11-11): the Zelus -> Karn rebrand. Every string in the configs (index 2:
 * item / npc / loc names and ops, params, enums, structs, db rows), the interfaces (index 3: text, ops, tooltips) and
 * the clientscripts (index 12: string constants) that contains "zelus" in any case becomes "karn" in the same case
 * (Zelus -> Karn, ZELUS -> KARN, zelus -> karn). The domain zelusrsps is left alone (it changes with the new domain).
 *
 * <p>All three formats store strings inline and null-terminated with no length prefix, and nothing addresses them by
 * byte offset (script jumps count instructions; the script trailer is read from the end), so a string may shrink in
 * place. A match only counts when it sits in a run of printable bytes that ends in a 0 terminator, so a byte pattern
 * inside model-like or numeric data can never be rewritten. Idempotent (a second run finds nothing).
 *
 * <p>Usage: PatchRebrandKarn scan|apply &lt;cache241&gt; [report.txt]
 */
public final class PatchRebrandKarn {

	private PatchRebrandKarn() {
	}

	static final int[] INDEXES = { 2, 3, 12 };
	static final byte[] FROM = "zelus".getBytes(StandardCharsets.ISO_8859_1);
	static final byte[] SKIP_AFTER = "rsps".getBytes(StandardCharsets.ISO_8859_1);
	static final int MAX_STRING = 4096;

	public static void main(String[] a) throws Exception {
		if (a.length < 2 || !(a[0].equals("scan") || a[0].equals("apply")))
			throw new IllegalArgumentException("scan|apply <cache241> [report.txt]");
		boolean apply = a[0].equals("apply");
		Store dst = new Store(new File(a[1]));
		dst.load();
		List<String> report = new ArrayList<>();
		int groups = 0, strings = 0;
		for (int index : INDEXES) {
			Index idx = dst.findIndex(index);
			boolean indexChanged = false;
			for (Archive ar : idx.getArchives()) {
				byte[] packed = dst.getStorage().loadArchive(ar);
				if (packed == null)
					continue;
				FileData[] fd = ar.getFileData();
				List<byte[]> chunks = SpliceItemOption.splitChunks(ar.decompress(packed), fd.length);
				TreeMap<Integer, byte[]> files = new TreeMap<>();
				boolean changed = false;
				for (int i = 0; i < fd.length; i++) {
					byte[] in = chunks.get(i);
					List<String> found = new ArrayList<>();
					byte[] out = rewrite(in, found);
					for (String s : found)
						report.add(index + "/" + ar.getArchiveId() + "/" + fd[i].getId() + ": " + s);
					strings += found.size();
					changed |= out != in;
					files.put(fd[i].getId(), out);
				}
				if (!changed)
					continue;
				groups++;
				if (apply) {
					store(dst, index, ar, files);
					indexChanged = true;
				}
			}
			if (indexChanged)
				PatchCustoms27.writeIndex(dst, index);
		}
		dst.close();
		if (a.length > 2) {
			try (PrintWriter w = new PrintWriter(a[2], StandardCharsets.UTF_8)) {
				report.forEach(w::println);
			}
		} else
			report.forEach(System.out::println);
		System.out.println("RESULT rebrand karn (" + a[0] + "): " + strings + " string(s) in " + groups + " group(s)");
	}

	/** The file with every qualifying match replaced; the same array when nothing matched. */
	static byte[] rewrite(byte[] in, List<String> found) {
		java.io.ByteArrayOutputStream b = null;
		int copied = 0;
		for (int i = 0; i + FROM.length <= in.length; i++) {
			if (!matches(in, i) || followedBy(in, i + FROM.length, SKIP_AFTER) || !inTerminatedString(in, i))
				continue;
			if (b == null)
				b = new java.io.ByteArrayOutputStream(in.length);
			found.add(describe(in, i));
			b.write(in, copied, i - copied);
			b.writeBytes(karn(in, i));
			copied = i + FROM.length;
			i = copied - 1;
		}
		if (b == null)
			return in;
		b.write(in, copied, in.length - copied);
		return b.toByteArray();
	}

	static boolean matches(byte[] d, int at) {
		for (int k = 0; k < FROM.length; k++)
			if (Character.toLowerCase((char) (d[at + k] & 0xff)) != FROM[k])
				return false;
		return true;
	}

	static boolean followedBy(byte[] d, int at, byte[] what) {
		if (at + what.length > d.length)
			return false;
		for (int k = 0; k < what.length; k++)
			if (Character.toLowerCase((char) (d[at + k] & 0xff)) != what[k])
				return false;
		return true;
	}

	/** True when the bytes from {@code at} up to the next 0 are all printable and that 0 exists. */
	static boolean inTerminatedString(byte[] d, int at) {
		for (int j = at; j < d.length && j < at + MAX_STRING; j++) {
			int c = d[j] & 0xff;
			if (c == 0)
				return true;
			if (c < 0x20 && c != '\n')
				return false;
		}
		return false;
	}

	/** "Karn" in the case of the match: ZELUS -> KARN, Zelus -> Karn, zelus -> karn. */
	static byte[] karn(byte[] d, int at) {
		boolean upper0 = Character.isUpperCase((char) (d[at] & 0xff));
		boolean upper1 = Character.isUpperCase((char) (d[at + 1] & 0xff));
		String s = upper0 && upper1 ? "KARN" : upper0 ? "Karn" : "karn";
		return s.getBytes(StandardCharsets.ISO_8859_1);
	}

	/** The whole string around the match, for the report. */
	static String describe(byte[] d, int at) {
		int s = at;
		while (s > 0 && (d[s - 1] & 0xff) >= 0x20 && at - s < 200)
			s--;
		int e = at;
		while (e < d.length && d[e] != 0)
			e++;
		return new String(d, s, e - s, StandardCharsets.ISO_8859_1);
	}

	static void store(Store dst, int index, Archive archive, TreeMap<Integer, byte[]> files) throws Exception {
		List<byte[]> contents = new ArrayList<>(files.values());
		byte[] raw = SpliceItemOption.joinChunks(contents);
		Container c = new Container(archive.getCompression(), -1);
		c.compress(raw, null);
		dst.getStorage().store(index, archive.getArchiveId(), c.data);
		archive.setCrc(c.crc);
		archive.setRevision(archive.getRevision() + 1);
		archive.setCompressedSize(c.data.length);
		archive.setDecompressedSize(raw.length);
	}
}

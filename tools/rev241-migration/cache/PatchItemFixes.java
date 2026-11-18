import net.runelite.cache.fs.Store;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.*;

/**
 * Stage 61 (user 2026-11-18: "check all the latest custom items ... correct placeholder, and most of them now have 2
 * wield option not only 1"). Two item-def repairs:
 * <ol>
 * <li><b>Duplicate equip options.</b> The 400xx customs carried "Wield"/"Wear" in BOTH iop 1 and iop 2 (some Ethereal
 * items had "Drop" in iop 2 and iop 5). iop 1 is removed and iop 2 becomes the single equip option ("Wield" for weapon /
 * shield slots, "Wear" otherwise). The server equips on any equip word (TabInventory.isEquipWord), so nothing breaks.</li>
 * <li><b>Bank placeholders.</b> An item's op 148 must name a placeholder (op 149 = 14401) whose op 148 points back at it.
 * Rev 231 customs kept placeholder ids that are real items in rev 241 (Founder's robe top -> Aga paste, master capes ->
 * pumpkins, ...), clones borrowed their template's placeholder, some official items lost theirs and new customs had
 * none. Official items get their vanilla link back when that placeholder is still intact; every other item gets its own
 * placeholder from 64000 up (a copy of a vanilla placeholder def). The id each item gets is kept in
 * assets/item_placeholders.txt ("item placeholder" per line), so a rebuild hands out the same ids and placeholders
 * already sitting in player banks keep meaning the same item.</li>
 * </ol>
 * Idempotent. Usage: PatchItemFixes apply &lt;cache241&gt; &lt;vanilla241&gt; &lt;assets/item_placeholders.txt&gt;
 */
public final class PatchItemFixes {

	private PatchItemFixes() {
	}

	static final Charset CP = Charset.forName("windows-1252");
	static final int PH_TEMPLATE = 14401, FIRST_NEW = 64000, LAST_NEW = 65535;
	static final Set<String> EQUIP = Set.of("wield", "wear", "equip");

	public static void main(String[] a) throws Exception {
		if (a.length != 4 || !a[0].equals("apply"))
			throw new IllegalArgumentException("apply <cache241> <vanilla241> <item_placeholders.txt>");
		Store dst = new Store(new File(a[1]));
		dst.load();
		Store van = new Store(new File(a[2]));
		van.load();
		File mapFile = new File(a[3]);
		DefRewriter.ParamKinds pk = DefRewriter.paramKinds(RoundTripDefs.files(dst, 11));
		DefRewriter.ParamKinds vpk = DefRewriter.paramKinds(RoundTripDefs.files(van, 11));
		Map<Integer, byte[]> items = RoundTripDefs.files(dst, 10), vitems = RoundTripDefs.files(van, 10);
		vanillaPh = new HashMap<>();
		for (var e : vitems.entrySet()) {
			Info vi = info(e.getValue(), vpk);
			if (vi.ph >= 0 && vi.phTemplate < 0) vanillaPh.put(e.getKey(), vi.ph);
			if (vi.phTemplate >= 0 || vi.noteTemplate >= 0) vanillaSlot.add(e.getKey()); // a custom here took an official placeholder / note id
		}

		// the assigned placeholder ids, item -> placeholder
		TreeMap<Integer, Integer> assigned = new TreeMap<>();
		if (mapFile.exists())
			for (String line : Files.readAllLines(mapFile.toPath())) {
				line = line.trim();
				if (line.isEmpty() || line.startsWith("#")) continue;
				String[] p = line.split("\\s+");
				assigned.put(Integer.parseInt(p[0]), Integer.parseInt(p[1]));
			}
		Set<Integer> taken = new HashSet<>(assigned.values());

		// a vanilla placeholder def to copy (Abyssal whip's)
		int whipPh = info(items.get(4151), pk).ph;
		byte[] phTemplate = items.get(whipPh);
		if (phTemplate == null || info(phTemplate, pk).phTemplate != PH_TEMPLATE)
			throw new IllegalStateException("Abyssal whip's placeholder " + whipPh + " isn't a placeholder");

		Map<Integer, byte[]> put = new TreeMap<>();
		int opsFixed = 0, restored = 0, created = 0;
		for (var e : new TreeMap<>(items).entrySet()) {
			int id = e.getKey();
			byte[] def = e.getValue();
			Info x = info(def, pk);
			if (x.phTemplate >= 0 || x.noteTemplate >= 0 || x.name == null || x.name.equals("null") || x.name.startsWith("groundproxy"))
				continue;

			// 1. duplicate equip options
			long equips = Arrays.stream(x.iop).filter(o -> o != null && EQUIP.contains(o.toLowerCase())).count();
			boolean dropTwice = x.iop[0] != null && EQUIP.contains(x.iop[0].toLowerCase()) && "drop".equalsIgnoreCase(x.iop[1]) && "drop".equalsIgnoreCase(x.iop[4]);
			if (equips > 1 || dropTwice) {
				String word = x.wearpos == 3 || x.wearpos == 5 ? "Wield" : "Wear";
				def = withOps(def, pk, word);
				opsFixed++;
			}

			// 2. placeholder
			// broken links, lost official links, and new customs without one (official items that never had one stay so)
			boolean wantsPh = x.ph >= 0 || vanillaPh.containsKey(id) || !vitems.containsKey(id) || vanillaSlot.contains(id);
			if (id < 65536 && wantsPh && !valid(items, pk, id, x.ph)) {
				Integer van241 = vanillaPh.get(id);
				int target;
				if (van241 != null && pointsBack(items, pk, van241, id)) {
					target = van241; // the official placeholder is intact, only the link was lost
					restored++;
				} else {
					Integer have = assigned.get(id);
					if (have == null) {
						int n = FIRST_NEW;
						while (taken.contains(n) || items.containsKey(n) && !ownsPh(items, pk, n, id)) n++;
						if (n > LAST_NEW) throw new IllegalStateException("out of placeholder ids");
						have = n;
						assigned.put(id, have);
						taken.add(have);
					}
					target = have;
					byte[] ph = with148(phTemplate, id, pk);
					if (!Arrays.equals(items.get(target), ph)) put.put(target, ph);
					created++;
				}
				def = with148(def, target, pk);
			}
			if (!Arrays.equals(def, e.getValue())) put.put(id, def);
		}
		int changed = put.isEmpty() ? 0 : PatchCustoms27.putConfigs(dst, 10, put);
		dst.close();

		StringBuilder sb = new StringBuilder("# item placeholder -- written by PatchItemFixes (stage 61); never renumber, banks hold these ids\n");
		for (var e : assigned.entrySet()) sb.append(e.getKey()).append(' ').append(e.getValue()).append('\n');
		Files.writeString(mapFile.toPath(), sb.toString());
		System.out.println("RESULT item fixes: " + changed + " def(s) written; equip options fixed " + opsFixed + ", vanilla placeholders restored "
			+ restored + ", own placeholders " + created + " (" + assigned.size() + " in " + mapFile.getName() + ")");
	}

	static Map<Integer, Integer> vanillaPh;
	static final Set<Integer> vanillaSlot = new HashSet<>();

	/** item's op 148 names a placeholder that points back at it */
	static boolean valid(Map<Integer, byte[]> items, DefRewriter.ParamKinds pk, int id, int ph) {
		return ph >= 0 && pointsBack(items, pk, ph, id);
	}

	static boolean pointsBack(Map<Integer, byte[]> items, DefRewriter.ParamKinds pk, int ph, int id) {
		byte[] p = items.get(ph);
		if (p == null) return false;
		Info i = info(p, pk);
		return i.phTemplate == PH_TEMPLATE && i.ph == id;
	}

	/** id n already holds this item's placeholder (re-run) */
	static boolean ownsPh(Map<Integer, byte[]> items, DefRewriter.ParamKinds pk, int n, int id) {
		return pointsBack(items, pk, n, id);
	}

	static final class Info {
		String name;
		String[] iop = new String[5];
		int ph = -1, phTemplate = -1, noteTemplate = -1, wearpos = -1;
	}

	static Info info(byte[] def, DefRewriter.ParamKinds pk) {
		Info r = new Info();
		for (byte[] seg : DefRewriter.segments("item", def, DefRewriter.IDENTITY, pk)) {
			int op = seg[0] & 0xff;
			if (op == 0) break;
			if (op == 2) r.name = str(seg);
			else if (op >= 35 && op <= 39) r.iop[op - 35] = str(seg);
			else if (op == 148) r.ph = (seg[1] & 0xff) << 8 | seg[2] & 0xff;
			else if (op == 149) r.phTemplate = (seg[1] & 0xff) << 8 | seg[2] & 0xff;
			else if (op == 98) r.noteTemplate = (seg[1] & 0xff) << 8 | seg[2] & 0xff;
			else if (op == 13) r.wearpos = seg[1] & 0xff;
		}
		return r;
	}

	static String str(byte[] s) {
		int e = 1;
		while (e < s.length && s[e] != 0) e++;
		return new String(s, 1, e - 1, CP);
	}

	/** iop 1 removed, iop 2 = word */
	static byte[] withOps(byte[] def, DefRewriter.ParamKinds pk, String word) {
		ByteArrayOutputStream b = new ByteArrayOutputStream();
		for (byte[] seg : DefRewriter.segments("item", def, DefRewriter.IDENTITY, pk)) {
			int op = seg[0] & 0xff;
			if (op == 0) break;
			if (op == 35 || op == 36) continue;
			b.writeBytes(seg);
		}
		b.write(36);
		b.writeBytes(word.getBytes(CP));
		b.write(0);
		b.write(0);
		return b.toByteArray();
	}

	/** the def with op 148 set to {@code target} (added before the end if it had none) */
	static byte[] with148(byte[] def, int target, DefRewriter.ParamKinds pk) {
		ByteArrayOutputStream b = new ByteArrayOutputStream();
		boolean done = false;
		for (byte[] seg : DefRewriter.segments("item", def, DefRewriter.IDENTITY, pk)) {
			int op = seg[0] & 0xff;
			if (op == 0) break;
			if (op == 148) {
				b.write(148);
				b.write(target >> 8);
				b.write(target & 0xff);
				done = true;
			} else
				b.writeBytes(seg);
		}
		if (!done) {
			b.write(148);
			b.write(target >> 8);
			b.write(target & 0xff);
		}
		b.write(0);
		return b.toByteArray();
	}
}

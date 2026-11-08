import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Store;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.util.*;

/**
 * Stage 21 of the rev 241 cache build (2026-11-07): the Colosseum of Trials (region 12891).
 * <ul>
 * <li>Object 29559 (vanilla's "Death" figure in his armchair, placed only here, at 3230,5869) becomes The Grand Arbiter:
 * ops Trade (the Reliquary shop), Trials (the trial picker), Check-marks; recoloured to obsidian robes, a spectral
 * skeleton and a dark stone chair (see {@link #RECOLOUR}); its seated idle animation is kept.</li>
 * <li>Interface 1182, the trial picker (8 trial cards with boss models, the wave line-up, Enter solo / Form duo): built
 * as rev 231 components by .dev/cache-restore-tool BuildTrialSelectInterface (assets/colosseum/interface_1182) and
 * converted here to rev 241 (InterfaceRewriter: model ids become ints).</li>
 * <li>Interface 1183, the Reliquary (BuildReliquaryInterface, the same way) and its Trial Mark coin, sprite 24774
 * (assets/colosseum/trial_mark.png).</li>
 * <li>Npcs 21950-21957, the trial hosts in the hall: each trial's headline boss with Talk-to instead of its combat
 * ops and no combat level (the same models, size and animations), so players talk to it about its trial.</li>
 * <li>Models 63472-63489: the picker's showcase copies of the 18 trial bosses, bounding box centred on the origin
 * (assets/colosseum/MakeShowcaseModels): a model widget draws around the origin and has no offsets, and the bosses'
 * own models have it at their feet, so they stood above their boxes and were cut off.</li>
 * </ul>
 * Idempotent. Usage: PatchColosseum apply &lt;cache241&gt; &lt;assets/colosseum&gt;
 */
public final class PatchColosseum {

	private PatchColosseum() {
	}

	static final int RELIQUARY = 29559, LOC_GROUP = 6, SHOWCASE_LO = 63472, SHOWCASE_HI = 63489, MARK_SPRITE = 24774;
	static final int[] IFACES = { 1182, 1183, 1184 };
	/** the Ember Crucible's art, assets/colosseum/forge/&lt;id&gt;.png (MakeForgeArt) */
	static final int FORGE_ART_LO = 24776, FORGE_ART_HI = 24791;
	/** the trial hosts: talk-only copies of each trial's headline boss, host id -> boss (Colosseum.TrialHosts) */
	static final int[][] HOSTS = { { 21950, 21940 }, { 21951, 21934 }, { 21952, 21946 }, { 21953, 21926 }, { 21954, 21944 },
			{ 21955, 21925 }, { 21956, 21942 }, { 21957, 21945 } };
	static final int NPC_GROUP = 9, HOST_LO = 21950, HOST_HI = 21957;
	static final String NAME = "The Grand Arbiter";
	static final String[] OPS = { "Trade", "Trials", "Check-marks", "Forge" };
	/**
	 * find -> replace, round 2 (2026-11-07: the first crimson / gold / near-white chair read as a costume, and the chair's
	 * near-white came out flat white on GPU and gold metal in 117HD): robes obsidian charcoal with a faint void purple
	 * (hue 48), the skeleton a pale spectral violet, the armchair dark stone (hue 40) with battle-worn gold trim. All
	 * dark, low-saturation vertex colours, so the plain renderer, GPU and 117HD draw it alike.
	 */
	static final int[][] RECOLOUR = {
			{ 43026, 49292 }, { 43030, 49296 }, { 43034, 49300 }, { 43038, 49304 }, { 43043, 49309 }, { 43146, 49416 },
			{ 10304, 47696 }, { 10295, 47688 }, { 10291, 47684 },
			{ 43270, 41106 }, { 43276, 41112 }, { 5388, 7590 }, { 8070, 7728 } };

	public static void main(String[] a) throws Exception {
		if (a.length != 3 || !a[0].equals("apply"))
			throw new IllegalArgumentException("apply <cache241> <assets/colosseum>");
		Store dst = new Store(new File(a[1]));
		dst.load();
		int changed = 0;

		// ---- the Reliquary object
		Map<Integer, byte[]> locs = RoundTripDefs.files(dst, LOC_GROUP);
		byte[] old = locs.get(RELIQUARY);
		if (old == null)
			throw new IllegalStateException("object " + RELIQUARY + " missing");
		byte[] want = reliquary(old);
		if (!Arrays.equals(old, want))
			changed += PatchCustoms27.putConfigs(dst, LOC_GROUP, Map.of(RELIQUARY, want));

		// ---- showcase models (this stage's block, free in rev 241 when made)
		for (File f : new File(a[2], "showcase").listFiles()) {
			int id = Integer.parseInt(f.getName().replace(".dat", ""));
			if (id < SHOWCASE_LO || id > SHOWCASE_HI)
				throw new IllegalStateException("showcase model " + id + " outside " + SHOWCASE_LO + "-" + SHOWCASE_HI);
			changed += PatchSolarmane.putSingle(dst, 7, id, Files.readAllBytes(f.toPath())) ? 1 : 0;
		}

		// ---- the trial hosts
		Map<Integer, byte[]> npcs = RoundTripDefs.files(dst, NPC_GROUP);
		Map<Integer, byte[]> hostPut = new TreeMap<>();
		for (int[] h : HOSTS) {
			byte[] src = npcs.get(h[1]);
			if (src == null)
				throw new IllegalStateException("npc " + h[1] + " missing");
			if (h[0] < HOST_LO || h[0] > HOST_HI)
				throw new IllegalStateException("host " + h[0] + " outside " + HOST_LO + "-" + HOST_HI);
			byte[] hostDef = host(src);
			if (!Arrays.equals(npcs.get(h[0]), hostDef))
				hostPut.put(h[0], hostDef);
		}
		if (!hostPut.isEmpty())
			changed += PatchCustoms27.putConfigs(dst, NPC_GROUP, hostPut);

		// ---- the Trial Mark coin (the Reliquary's header icon)
		byte[] coin = BuildBinary241.pngSprite(new File(a[2], "trial_mark.png").toPath());
		changed += PatchSolarmane.putSingle(dst, 8, MARK_SPRITE, coin) ? 1 : 0;

		// ---- the Ember Crucible's art (interface 1184)
		for (int id = FORGE_ART_LO; id <= FORGE_ART_HI; id++)
			changed += PatchSolarmane.putSingle(dst, 8, id, BuildBinary241.pngSprite(new File(a[2], "forge/" + id + ".png").toPath())) ? 1 : 0;
		// ---- the Reliquary's and the Trial picker's art (MakeColosseumArt)
		for (File f : new File(a[2], "art").listFiles())
			changed += PatchSolarmane.putSingle(dst, 8, Integer.parseInt(f.getName().replace(".png", "")), BuildBinary241.pngSprite(f.toPath())) ? 1 : 0;

		// ---- interfaces 1182 (trial picker) and 1183 (the Reliquary)
		StringBuilder sizes = new StringBuilder();
		for (int iface : IFACES) {
			File dir = new File(a[2], "interface_" + iface);
			TreeMap<Integer, byte[]> comps = new TreeMap<>();
			for (File f : dir.listFiles()) {
				byte[] c231 = Files.readAllBytes(f.toPath());
				byte[] c241 = InterfaceRewriter.run(c231, DefRewriter.IDENTITY, false, true, v -> false).bytes();
				comps.put(Integer.parseInt(f.getName().replace(".dat", "")), c241);
			}
			if (!comps.firstKey().equals(0) || comps.lastKey() != comps.size() - 1)
				throw new IllegalStateException("interface " + iface + " components aren't 0.." + (comps.size() - 1));
			Index idx = dst.findIndex(3);
			Archive ar = idx.getArchive(iface);
			if (ar == null) {
				ar = idx.addArchive(iface);
				ar.setCompression(idx.getArchive(1002).getCompression());
				PatchCustoms27.writeGroup(dst, 3, iface, comps);
				changed++;
			} else if (!PatchEggIcons.sameFiles(BuildBinary241.files(dst, ar), comps)) {
				// ours (made by this stage; both groups were free in rev 241): replaced
				PatchCustoms27.writeGroup(dst, 3, iface, comps);
				changed++;
			}
			sizes.append(" ").append(iface).append(" (").append(comps.size()).append(" components)");
		}
		dst.close();
		System.out.println("RESULT colosseum: " + changed + " change(s); object " + RELIQUARY + " = " + NAME + ", interfaces" + sizes
				+ ", coin sprite " + MARK_SPRITE);
	}

	/** a boss's segments without its ops (30-34, sub / conditional ops) and combat level (95), then Talk-to */
	static byte[] host(byte[] def) {
		ByteArrayOutputStream b = new ByteArrayOutputStream();
		for (byte[] seg : DefRewriter.segments("npc", def, DefRewriter.IDENTITY, k -> null)) {
			int op = seg[0] & 0xff;
			if ((op >= 30 && op <= 34) || op == 95 || op == 251 || op == 252 || op == 253)
				continue;
			b.writeBytes(seg);
		}
		b.write(30);
		b.writeBytes("Talk-to".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
		b.write(0);
		b.write(0);
		return b.toByteArray();
	}

	/** the object's own segments without name / ops / recolours, then ours */
	static byte[] reliquary(byte[] def) {
		ByteArrayOutputStream b = new ByteArrayOutputStream();
		for (byte[] seg : DefRewriter.segments("obj", def, DefRewriter.IDENTITY, k -> null)) {
			int op = seg[0] & 0xff;
			if (op == 2 || (op >= 30 && op <= 34) || op == 40)
				continue;
			b.writeBytes(seg);
		}
		b.write(2);
		b.writeBytes(NAME.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
		b.write(0);
		for (int i = 0; i < OPS.length; i++) {
			b.write(30 + i);
			b.writeBytes(OPS[i].getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
			b.write(0);
		}
		b.write(40);
		b.write(RECOLOUR.length);
		for (int[] r : RECOLOUR) {
			b.write(r[0] >> 8);
			b.write(r[0] & 0xff);
			b.write(r[1] >> 8);
			b.write(r[1] & 0xff);
		}
		b.write(0);
		return b.toByteArray();
	}
}

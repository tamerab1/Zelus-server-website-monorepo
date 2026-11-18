import net.runelite.cache.fs.Store;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/**
 * Stage 54 of the rev 241 cache build (user 2026-11-16: "design a new scoreboard, custom like the tournaments and rivals
 * ones, for this minigame"): object {@link #OBJECT} "Hall of Seals", The Five Seals' leaderboard in the lobby's north
 * wing. Made like the Pit Championship / Karn Scoreboard / Rivals boards (BuildChampionshipBoard, variant "seals",
 * mode "export" -> assets/seals_board): model {@link #MODEL} (charcoal posts with gold caps, blood-red banners carrying
 * gold seal sigils, a crimson crest), sprites 25310-25313 / textures 274-277 for the painted face (THE FIVE SEALS, the five
 * seal medallions, FASTEST SOLO / FASTEST GROUP, the weekly strip). The object record copies the Rivals Leaderboard
 * (65472: 2x3, View) with our name and model. Idempotent. Usage: PatchSealsBoard apply &lt;cache241&gt; &lt;assets/seals_board&gt;
 */
public final class PatchSealsBoard {

	private PatchSealsBoard() {
	}

	static final int OBJECT = 63318, TEMPLATE = 65472, MODEL = 62825, SCALE = 100;
	static final String NAME = "Hall of Seals";

	public static void main(String[] a) throws Exception {
		if (a.length != 3 || !a[0].equals("apply"))
			throw new IllegalArgumentException("apply <cache241> <assets/seals_board>");
		File dir = new File(a[2]);
		Store dst = new Store(new File(a[1]));
		dst.load();
		int changed = 0;

		/* the face: sprites + textures */
		Map<Integer, byte[]> have = PatchSolarmane.textures(dst), put = new TreeMap<>();
		for (String line : Files.readAllLines(new File(dir, "textures.txt").toPath())) {
			if (line.isBlank())
				continue;
			String[] p = line.trim().split(" ");
			int tex = Integer.parseInt(p[0]), sprite = Integer.parseInt(p[1]), avg = Integer.parseInt(p[2]);
			changed += PatchSolarmane.putSingle(dst, 8, sprite, BuildBinary241.pngSprite(new File(dir, "png/" + sprite + ".png").toPath())) ? 1 : 0;
			byte[] rec = { (byte) (sprite >> 8), (byte) sprite, (byte) (avg >> 8), (byte) avg, 0, 0, 0 };
			byte[] old = have.get(tex);
			if (old != null && (old[0] != rec[0] || old[1] != rec[1]))
				throw new IllegalStateException("texture " + tex + " is taken by something else");
			if (!Arrays.equals(old, rec))
				put.put(tex, rec);
		}
		if (!put.isEmpty())
			changed += PatchCustoms27.putTextures(dst, put);

		/* the model */
		changed += PatchSolarmane.putSingle(dst, 7, MODEL, Files.readAllBytes(new File(dir, "model_" + MODEL + ".dat").toPath())) ? 1 : 0;

		/* the object: the Rivals Leaderboard's record with our name, model and scale */
		Map<Integer, byte[]> locs = RoundTripDefs.files(dst, PatchColosseum.LOC_GROUP);
		DefRewriter.ParamKinds pk = DefRewriter.paramKinds(RoundTripDefs.files(dst, 11));
		byte[] src = locs.get(TEMPLATE);
		if (src == null || !new String(src, StandardCharsets.ISO_8859_1).contains("Rivals Leaderboard"))
			throw new IllegalStateException("object " + TEMPLATE + " is not the Rivals Leaderboard");
		byte[] cur = locs.get(OBJECT);
		if (cur != null && !new String(cur, StandardCharsets.ISO_8859_1).contains(NAME))
			throw new IllegalStateException("object " + OBJECT + " is taken by something else");
		ByteArrayOutputStream b = new ByteArrayOutputStream();
		for (byte[] seg : DefRewriter.segments("obj", src, DefRewriter.IDENTITY, pk)) {
			int op = seg[0] & 0xff;
			if (op == 0)
				break;
			switch (op) {
				case 2 -> {
					b.write(2);
					b.writeBytes(NAME.getBytes(StandardCharsets.ISO_8859_1));
					b.write(0);
				}
				case 7 -> b.writeBytes(new byte[] { 7, 1, (byte) (MODEL >>> 24), (byte) (MODEL >>> 16), (byte) (MODEL >>> 8), (byte) MODEL });
				case 65, 66, 67 -> b.writeBytes(new byte[] { (byte) op, (byte) (SCALE >> 8), (byte) SCALE });
				default -> b.writeBytes(seg);
			}
		}
		b.write(0);
		if (!Arrays.equals(b.toByteArray(), cur))
			changed += PatchCustoms27.putConfigs(dst, PatchColosseum.LOC_GROUP, new TreeMap<>(Map.of(OBJECT, b.toByteArray())));
		dst.close();
		System.out.println("RESULT seals board: " + changed + " change(s); object " + OBJECT + " \"" + NAME + "\", model " + MODEL
			+ ", sprites 25310-25313, textures 274-277");
	}
}

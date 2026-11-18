import net.runelite.cache.fs.Store;

import java.io.File;

/**
 * Stage 47 of the rev 241 cache build (user 2026-11-16: "blackjack uses Zelus cards, can we make Karn logo cards?"):
 * the blackjack card back, sprite 5830 (interface 998: the dealer's hole card and the three-card deck), redrawn as a
 * Karn card (assets/karn_cards/make_card.py: crimson, gold border, the K emblem and the KARN wordmark, in the old
 * card's exact silhouette). Idempotent. Usage: PatchKarnCards apply &lt;cache241&gt; &lt;assets/karn_cards&gt;
 */
public final class PatchKarnCards {

	private PatchKarnCards() {
	}

	static final int CARD_BACK = 5830;

	public static void main(String[] a) throws Exception {
		if (a.length != 3 || !a[0].equals("apply"))
			throw new IllegalArgumentException("apply <cache241> <assets/karn_cards>");
		Store dst = new Store(new File(a[1]));
		dst.load();
		boolean changed = PatchSolarmane.putSingle(dst, 8, CARD_BACK,
			BuildBinary241.pngSprite(new File(a[2], "png/" + CARD_BACK + ".png").toPath()));
		dst.close();
		System.out.println("RESULT karn cards: " + (changed ? 1 : 0) + " change(s); sprite " + CARD_BACK);
	}
}

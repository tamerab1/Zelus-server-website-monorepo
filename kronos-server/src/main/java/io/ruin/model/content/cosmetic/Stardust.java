package io.ruin.model.content.cosmetic;

import io.ruin.model.entity.player.Player;

/**
 * Stardust sparkles (spotanims 4293 gold / 4294 crimson: themed copies of the 1388 confetti burst, scaled down to
 * fine dust; built by .dev/cache-restore-tool/stardust_gfx.txt + SetSpotanimScale).
 * <ul>
 *     <li>Twisted bow (celestial) {@link #CELESTIAL_TWISTED_BOW}: its sparkles are drawn CLIENT-side
 *     (net.runelite.client.plugins.zelusweaponsparkles), emitted from the bow's vertices in the animated player model,
 *     because a server graphic can only play at the player's origin. The id is also used for tbow damage scaling.</li>
 *     <li>{@link #celebrate}: a one-off gold burst for successful forges, lottery wins and preset / supply spawns.</li>
 * </ul>
 */
public final class Stardust {

	public static final int GOLD = 6700, CRIMSON = 6701;
	public static final int CELESTIAL_TWISTED_BOW = 42578;

	private Stardust() {
	}

	/** A one-off gold burst around the player. */
	public static void celebrate(Player player) {
		if (player != null && player.isOnline())
			player.graphics(GOLD);
	}
}

package io.ruin.model.content.rivals;

import io.ruin.cache.Icon;
import io.ruin.model.World;
import io.ruin.model.entity.player.Player;
import io.ruin.model.map.Position;
import io.ruin.model.map.object.actions.ObjectAction;
import io.ruin.utility.Broadcast;

import java.util.HashMap;
import java.util.Map;

/**
 * The Challenge Bell in the Rivals lobby (2026-10-17): Ring sends a server-wide "looking for a fight" call and tolls
 * for everyone within 15 tiles. Players answer with ::rivals (teleports to the lobby; chat lines can't be clicked).
 * <p>
 * The object is The Challenge Bell (62396, .dev/cache-restore-tool build_challenge_bell.sh): The Cloister Bell's framed
 * bell scaled to 42/128 (its mesh is 545 tall / 507 wide on a 1x1 object) and recoloured dark bronze and weathered
 * wood. Spawned in custom_objects.json at 3355,3231 (user's spot, beside the bank chest at 3355,3233); only that spawn is hooked.
 * <p>
 * Cooldowns live in server memory by user id, so relogging doesn't reset them: 15 minutes per player, plus a short
 * world-wide gap so several players can't stack broadcasts back to back.
 */
public final class ChallengeBell {

	public static final int BELL = 62396;
	public static final Position SPOT = new Position(3355, 3231, 0);

	private static final long PLAYER_COOLDOWN_MS = 15 * 60_000L, GLOBAL_COOLDOWN_MS = 2 * 60_000L;
	private static final int TOLL_RANGE = 15;
	/** Placeholder: the Rivals FIGHT sound (unverified like the other Rivals sounds) until a real bell id is picked. */
	private static final int SOUND_TOLL = 2276;
	private static final int ANIM_RING = 832;

	private static final Map<Integer, Long> lastRing = new HashMap<>();
	private static long lastGlobalRing;

	private ChallengeBell() {
	}

	static void register() {
		ObjectAction.register(BELL, SPOT.getX(), SPOT.getY(), SPOT.getZ(), "Ring", (player, obj) -> ring(player));
	}

	private static void ring(Player player) {
		if (RivalsSnapshot.active(player) || RivalsQueueManager.inMatch(player)) {
			player.sendMessage("You can't ring the bell right now.");
			return;
		}
		long now = System.currentTimeMillis();
		long wait = Math.max(lastRing.getOrDefault(player.getUserId(), 0L) + PLAYER_COOLDOWN_MS,
			lastGlobalRing + GLOBAL_COOLDOWN_MS) - now;
		if (wait > 0) {
			long minutes = (wait + 59_999) / 60_000;
			player.sendMessage("The bell has recently tolled. You can ring it again in " + minutes
				+ (minutes == 1 ? " minute." : " minutes."));
			return;
		}
		lastRing.put(player.getUserId(), now);
		lastGlobalRing = now;

		player.animate(ANIM_RING);
		for (Player p : World.players())
			if (p.getPosition().getZ() == SPOT.getZ() && p.getPosition().isWithinDistance(SPOT, TOLL_RANGE))
				p.privateSound(SOUND_TOLL);
		// high contrast on the parchment chatbox (2026-10-17, user's format): deep red header, sapphire name, rust call to action
		// <img=56> rendered as a blank gap; the crossed swords (Icon.PK_SWORDS, 31) is the icon the first version used
		Broadcast.WORLD.sendPlain("[" + Icon.PK_SWORDS.tag() + "<col=800000> RIVALS CHALLENGE</col>] <col=000080>" + player.getName()
			+ "</col> has rung the Challenge Bell at the Rivals Arena and is looking for a fight! <col=9e2d00>Type ::rivals to answer!</col>");
	}
}

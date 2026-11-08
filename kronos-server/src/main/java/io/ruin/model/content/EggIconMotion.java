package io.ruin.model.content;

import io.ruin.model.World;
import io.ruin.model.entity.player.Player;
import io.ruin.model.item.Item;
import io.ruin.model.item.containers.Equipment;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * The moving part of the egg-slot icons (2026-11-07). The worn model is the static part; the 5 new designs
 * (Bonecrown, Soulflame, Frost sigil, Void eye, Sunburst; items 40228-40252, 5 tiers each) also play a looping
 * spot-anim (cache stage 20, PatchEggIcons: 6841-6865, one per item, the tier's width) on their own slot: orbiting
 * skulls, a wave of ghost skulls, a turning sigil, orbiting eyes, a spinning sun crown. Each loop is
 * {@link #LOOP_TICKS} ticks and ends in the pose it starts in; it's sent with the loop flag and re-sent in step with
 * its loop so players who arrive later see it too. Cleared the tick the icon comes off.
 */
public final class EggIconMotion {

	private EggIconMotion() {
	}

	static final int FIRST_ITEM = 40228, LAST_ITEM = 40252, FIRST_SPOT = 6841;
	/** our own spot-anim slot (0 = plain graphics, 3 = the Soulbound crown) */
	static final int SLOT = 6;
	static final int LOOP_TICKS = 8;

	/** what each player is playing: {spot-anim, tick it started} */
	private static final Map<Player, int[]> PLAYING = new WeakHashMap<>();

	/** the spot-anim an egg-slot item plays, or -1 */
	public static int spotFor(int itemId) {
		return itemId >= FIRST_ITEM && itemId <= LAST_ITEM ? FIRST_SPOT + (itemId - FIRST_ITEM) : -1;
	}

	public static void register() {
		World.startEvent(e -> {
			int tick = 0;
			while (true) {
				e.delay(1);
				tick++;
				for (Player player : World.players()) {
					try {
						update(player, tick);
					} catch (Exception ex) {
						ex.printStackTrace(); // one bad player must not stop everyone's icons
					}
				}
			}
		});
	}

	private static void update(Player player, int tick) {
		if (player.avatarExtended() == null)
			return;
		Item egg = player.getEquipment().get(Equipment.SLOT_EGG);
		int want = egg == null ? -1 : spotFor(egg.getId());
		int[] now = PLAYING.get(player);
		if (want == -1) {
			if (now != null) {
				PLAYING.remove(player);
				player.avatarExtended().setSpotAnim(SLOT, -1, 0, 0, false);
			}
			return;
		}
		if (now == null || now[0] != want) {
			PLAYING.put(player, new int[] { want, tick });
			player.avatarExtended().setSpotAnim(SLOT, want, 0, 0, true);
		} else if ((tick - now[1]) % LOOP_TICKS == 0) {
			player.avatarExtended().setSpotAnim(SLOT, want, 0, 0, true);
		}
	}
}

package io.ruin.model.content;

import io.ruin.model.entity.player.Player;
import io.ruin.model.item.Item;
import io.ruin.model.stat.StatType;

/**
 * The Cloud Ankou set (40021-40025, the Reliquary's armour tab, 2026-11-08): no combat stats, but +2% experience per piece worn
 * in non-combat skills, and +15% for all five.
 */
public final class CloudAnkouSet {

	private CloudAnkouSet() {
	}

	private static final int FIRST = 40021, LAST = 40025, PIECES = LAST - FIRST + 1;
	private static final double PER_PIECE = 0.02, FULL_SET = 0.15;

	public static double xpMultiplier(Player player, StatType type) {
		if (type.isCombat())
			return 1;
		int worn = 0;
		for (Item item : player.getEquipment().getItems())
			if (item != null && item.getId() >= FIRST && item.getId() <= LAST)
				worn++;
		return worn == PIECES ? 1 + FULL_SET : 1 + worn * PER_PIECE;
	}
}

package io.ruin.model.activities.bosses.globalboss;

import io.ruin.cache.Icon;
import io.ruin.model.World;
import io.ruin.model.entity.player.Player;
import io.ruin.model.item.Item;
import io.ruin.model.item.loot.LootTable;
import io.ruin.model.map.ground.GroundItem;
import io.ruin.utility.Broadcast;

import java.util.Set;
import java.util.function.Consumer;

/**
 * Shared global boss payout (2026-10-21, "every player who dealt damage should get something, never missing"):
 * <ul>
 * <li>every ONLINE account that damaged the boss is paid, wherever it is (Stone Poltergeist and Nidhogg used to pay only
 * the damagers still on screen -- anyone who stepped out of view got nothing);</li>
 * <li>a roll that comes back empty ({@link LootTable#rollItem()} can return null on a float rounding edge, which used
 * to throw and cost that player every roll after it) is rolled again;</li>
 * <li>each item goes straight into the inventory; only when it is full does it drop at the player's feet, owned and
 * flagged as loot, instead of every reward lying on the floor where a player who left already would never see it.</li>
 * </ul>
 * One player's failure never stops the others.
 */
public final class GlobalBossRewards {

	private static final int REROLLS = 10;

	private GlobalBossRewards() {
	}

	/** Pays every online damager: {@code extra} first (kill counts, perk points, messages), then {@code rolls} items. */
	public static void payDamagers(String bossName, Set<Integer> damagers, LootTable table, int minRolls, int maxRolls,
								   Consumer<Player> extra) {
		for (Player p : World.players()) {
			if (!damagers.contains(p.getUserId()))
				continue;
			try {
				extra.accept(p);
				int rolls = io.ruin.api.utils.Random.get(minRolls, maxRolls);
				int toFloor = 0;
				for (int i = 0; i < rolls; i++) {
					Item reward = roll(table);
					if (reward == null)
						continue;
					if (!give(p, reward))
						toFloor++;
					if (reward.lootBroadcast != null)
						Broadcast.WORLD.sendNewsDropMessage(p, Icon.RARE_DROP, "<col=000000>" + p.getName(),
							" just received " + reward.getDef().descriptiveName + " from " + bossName + "!");
				}
				p.sendMessage("<col=ff0000>" + bossName + ": " + rolls + " reward" + (rolls == 1 ? "" : "s")
					+ (toFloor == 0 ? " added to your inventory." : ", " + toFloor + " dropped at your feet (inventory full).") + "</col>");
			} catch (Exception e) {
				System.err.println("[" + bossName + "] reward failed for " + p.getName() + ": " + e);
				e.printStackTrace();
			}
		}
	}

	private static Item roll(LootTable table) {
		for (int i = 0; i < REROLLS; i++) {
			Item item = table.rollItem();
			if (item != null && item.getId() > 0 && item.getAmount() > 0)
				return item;
		}
		return null;
	}

	/** Into the inventory; false = it was dropped at the player's feet instead. */
	private static boolean give(Player player, Item item) {
		if (player.getInventory().hasRoomFor(item.getId(), item.getAmount()) && player.getInventory().add(item.getId(), item.getAmount()) > 0)
			return true;
		new GroundItem(item).owner(player).position(player.getPosition()).loot().spawn();
		return false;
	}
}

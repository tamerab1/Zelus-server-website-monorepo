package io.ruin.model.activities.tempevents.summerevent;

import io.ruin.cache.Icon;
import io.ruin.model.World;
import io.ruin.model.entity.npc.NPC;
import io.ruin.model.entity.player.Player;
import io.ruin.model.item.Item;
import io.ruin.model.item.loot.LootItem;
import io.ruin.model.item.loot.LootTable;
import io.ruin.utility.Broadcast;
import io.ruin.utility.Random;

public class SummerEventHandler {


	public static LootTable summerFrogLootTable = new LootTable()
			.addTable(100,
				new LootItem(995, 1500000, 2000000, 10),
				new LootItem(7478, 1, 10),
				new LootItem(30629, 1, 10),
				new LootItem(30458, 1, 10),
				new LootItem(30459, 1, 10),
				new LootItem(30460, 1, 10),
				new LootItem(2528, 1, 1).broadcast(Broadcast.GLOBAL)
				)
		.addTable(1,
			new LootItem(33016, 1, 1).broadcast(Broadcast.GLOBAL)
			);

	public static LootTable summerImpLootTable = new LootTable()
		.addTable(100,
			new LootItem(995, 1500000, 2000000, 10),
			new LootItem(7478, 1, 10),
			new LootItem(30629, 1, 10),
			new LootItem(30458, 1, 10),
			new LootItem(30459, 1, 10),
			new LootItem(30460, 1, 10),
			new LootItem(2528, 1, 1).broadcast(Broadcast.GLOBAL)
		)
		.addTable(1,
			new LootItem(33015, 1, 1).broadcast(Broadcast.GLOBAL)
		);




	/*
	Spawns a random summer frog or summer imp when killing any npc at a random chance based on hitpoints and combat level.
	 */
	public static void rollForSummerNPCSpawn(Player player, NPC npc) {
		if(SummerEvent.disabled) {
			return;
		}
		int npcHitpoints = npc.getMaxHp();
		int npcCombatLevel = npc.getDef().combatLevel;
		int baseRate = 1000;
		double scalingFactor = 0.05;
		double d = 1 + scalingFactor * npcCombatLevel + scalingFactor * npcHitpoints;
		int chance = (int) (baseRate / d);
		// Any NPC with combat level + hitpoints summing above ~380 hit the old floor of 50 and
		// sat at a flat 1/50 (2%) chance per kill no matter how much tougher the content got --
		// exactly the mid-to-endgame content players kill fastest at (cannons/AoE/high DPS),
		// which is what made the event feel constant rather than occasional. Raised to 150
		// (~0.67%, roughly a 3x cut in that plateau) for the same reason the floor exists in
		// the first place (a kill rate should never fully zero out the event), just less
		// aggressively -- content below this floor is unaffected, it was already rarer.
		if(chance < 150)
			chance = 150;
		if(Random.get(chance) == 0) {
			NPC boss = new NPC(Random.get(1) == 0 ? 17021 : 17022);
			boss.spawn(npc.getPosition(), 3);
			player.getPacketSender().sendHintIcon(boss);
			boss.ownerId = player.getUserId();
			player.sendMessage("<col=ff0000>A summer creature has appeared...</col>");

			//event to remove summer npc after 1min
			World.startEvent(e -> {
				e.setCancelCondition(() -> boss.getHp() < 1 || boss.isRemoved());
				e.delay(100);
				boss.remove();
			});
			boss.deathEndListener = (e, killer, hit) -> {
				int reasonPointReward = Random.get(1500, 5000);
				int summerPointReward = Random.get(25, 50) / 4;
				int perkPointReward = Random.get(1, 3);
				player.summerPoints += summerPointReward;
				player.perkPoints += perkPointReward;
				player.reasonPoints += reasonPointReward;
				player.sendFilteredMessage("You receive <col=000000><shad=29F1FE>" + reasonPointReward
					+ " Zelus points<col=000000></shad> for killing a " + npc.getDef().name + ".");
				player.sendFilteredMessage("You receive <col=000000><shad=29F1FE>" + summerPointReward
					+ " Summer points<col=000000></shad> for killing a " + npc.getDef().name + ".");
				player.sendFilteredMessage("You receive <col=000000><shad=29F1FE>" + perkPointReward
					+ " Perk points<col=000000></shad> for killing a " + npc.getDef().name + ".");
				if (boss.getId() == 17021) {
					Item item = summerImpLootTable.rollItem();
					if (item != null) {
						player.getInventory().addOrDrop(item.getId(), item.getAmount());
						if (item.lootBroadcast != null)
							Broadcast.WORLD.sendNewsDropMessage(player, Icon.ADMINISTRATOR, "<col=000000>" + player.getName(),
								" received " + item.getDef().descriptiveName + " from a " + boss.getDef().name + "!");
					}
				} else if (boss.getId() == 17022) {
					Item item = summerFrogLootTable.rollItem();
					if (item != null) {
						player.getInventory().addOrDrop(item.getId(), item.getAmount());
						if (item.lootBroadcast != null)
							Broadcast.WORLD.sendNewsDropMessage(player, Icon.ADMINISTRATOR, "<col=000000>" + player.getName(),
								" received " + item.getDef().descriptiveName + " from a " + boss.getDef().name + "!");
					}
				}
				boss.remove();
			};
		}
	}

}

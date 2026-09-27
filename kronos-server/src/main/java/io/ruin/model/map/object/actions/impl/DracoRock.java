package io.ruin.model.map.object.actions.impl;

import io.ruin.api.utils.Random;
import io.ruin.model.activities.minigame.MiningMinigameInstance;
import io.ruin.model.entity.npc.NPC;
import io.ruin.model.entity.player.Player;
import io.ruin.model.map.object.actions.ObjectAction;
import io.ruin.model.skills.mining.Pickaxe;
import io.ruin.model.stat.StatType;

// Standalone mining rock for the David npc minigame (entry gate at 3346,3347). Cloned from the
// shooting-star rock (41223) with a dark-red/gold recolor -- see SpliceCrimsonStar.java in
// .dev/cache-restore-tool -- but NOT part of the ShootingStars random-spawn event; this is its
// own object id (62390) with its own "Mine" handler. Real minigame mechanics/rewards are still
// TBD -- for now a successful mine just grants Mining XP and Dracodust (the resource this
// minigame will eventually spend), same currency-drop shape as ShootingStars.mine()'s Stardust.
public class DracoRock {

	private static final int DRACO_ROCK_ID = 62390;
	private static final int REQUIRED_MINING_LEVEL = 82;
	private static final double MINING_XP = 500;
	private static final int DRACODUST_ID = 60389;
	private static final int DRACO_WHELP_ID = 30567;

	public static void register() {
		ObjectAction.register(DRACO_ROCK_ID, "Mine", (player, obj) -> {
			if (player.getStats().get(StatType.Mining).currentLevel < REQUIRED_MINING_LEVEL) {
				player.sendMessage("You need a Mining level of " + REQUIRED_MINING_LEVEL + " to mine this.");
				return;
			}
			Pickaxe pickaxe = Pickaxe.find(player);
			if (pickaxe == null) {
				player.sendMessage("You do not have a pickaxe which you have the Mining level to use.");
				return;
			}
			player.startEvent(event -> {
				// regularAnimationID, NOT crystalAnimationID -- the latter is only for actual
				// crystal/Prifddinas-style ore, which always shows a fixed overlay pickaxe
				// regardless of tier by design; this is a normal rock.
				player.animate(pickaxe.regularAnimationID);
				event.delay(3);
				player.getStats().addXp(StatType.Mining, MINING_XP, true);
				int dracodust = dracodustYield(pickaxe);
				player.getInventory().addOrDrop(DRACODUST_ID, dracodust);
				player.sendMessage("You strike the Draco Rock and receive " + dracodust + " Dracodust.");
				if (MiningMinigameInstance.recordDracodustAndCheckWhelp(player, dracodust)) {
					spawnWhelp(player);
				}
			});
		});
	}

	private static int dracodustYield(Pickaxe pickaxe) {
		switch (pickaxe) {
			case RUNE: return 1;
			case DRAGON: return 2;
			case INFERNAL: return 3;
			case DRACONIC: return 5;
			case DRACO_PICKAXE: return 6;
			default: return 1;
		}
	}

	// Spawns exactly 1 Draco Whelp (npc 30567) next to the miner and sends it straight at them.
	// Triggered by MiningMinigameInstance.recordDracodustAndCheckWhelp's Dracodust-total thresholds.
	//
	// Uses the same targetPlayer + attackTargetPlayer pattern as the Barrows brothers rather than
	// the generic aggro scan -- that scan (NPCCombat.canAggro) skips players flagged isIdle (1000
	// ticks without moving, i.e. anyone standing at the rock mining for ~10 minutes), only looks 4
	// tiles out, and in single-combat won't let a second whelp in while another npc is on the
	// player, so whelps only sometimes attacked. The whelp despawns if the miner leaves or logs out
	// (DracoWhelp.json's respawn_ticks is -1, which attackTargetPlayer requires).
	private static void spawnWhelp(Player player) {
		int x = player.getPosition().getX() + Random.get(-2, 2);
		int y = player.getPosition().getY() + Random.get(-2, 2);
		NPC whelp = new NPC(DRACO_WHELP_ID).spawn(x, y, player.getPosition().getZ(), 5).targetPlayer(player, false);
		whelp.attackTargetPlayer(() -> !player.isOnline()
				|| !player.getPosition().isWithinDistance(whelp.getPosition()));
	}
}

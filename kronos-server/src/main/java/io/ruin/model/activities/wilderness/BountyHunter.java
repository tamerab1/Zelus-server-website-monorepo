package io.ruin.model.activities.wilderness;

import com.google.gson.annotations.Expose;
import io.ruin.api.utils.Random;
import io.ruin.model.World;
import io.ruin.model.combat.KillingSpree;
import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.Interface;
import io.ruin.model.inter.ToplevelComponent;
import io.ruin.model.inter.dialogue.MessageDialogue;
import io.ruin.model.inter.dialogue.OptionsDialogue;
import io.ruin.model.var.VarPlayerRepository;
import io.ruin.model.inter.utils.Option;
import io.ruin.model.item.Item;
import io.ruin.model.item.ItemContainer;
import io.ruin.model.map.Bounds;
import io.ruin.model.map.ground.GroundItem;
import io.ruin.utility.Misc;
import io.ruin.utility.TimedList;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

public class BountyHunter {

	public enum Targeting {

		ALL("All Wilderness", p -> true),
		DISABLED("Disabled", p -> false),
		EDGEVILLE("Edgeville Only", p -> p.getPosition().inBounds(EDGEVILLE_BOUNDS));

		private static boolean match(Player player, Player searching) {
			Targeting t1 = player.getBountyHunter().targeting;
			if (!t1.isActive(player))
				return false;
			Targeting t2 = searching.getBountyHunter().targeting;
			if (t1 == t2)
				return true;
			if (t1 == ALL)
				return t2.isActive(player);
			if (t2 == ALL)
				return t1.isActive(searching);
			return false;
		}

		public final String name;

		private final Predicate<Player> active;

		Targeting(String name, Predicate<Player> active) {
			this.name = name;
			this.active = active;
		}

		public boolean isActive(Player player) {
			return active.test(player);
		}

	}

	private static final Bounds EDGEVILLE_BOUNDS = new Bounds(2993, 3523, 3124, 3597, -1);

	/**
	 * Register Event & Interface.
	 */

	public static void register() {
		List<Player> potentialTargets = new ArrayList<>(500);
		World.startEvent(e -> {
			while (true) {
				long ms = System.currentTimeMillis();
				for (Player p1 : Wilderness.players) {
					if (!p1.getBountyHunter().searchForTarget(ms))
						continue;
					for (Player p2 : Wilderness.players) {
						if (!p2.getBountyHunter().allowAsTarget(p1, ms))
							continue;
						potentialTargets.add(p2);
					}
					if (!potentialTargets.isEmpty()) {
						Player p2 = Random.get(potentialTargets);
						p2.getBountyHunter().target(p1, ms);
						p1.getBountyHunter().target(p2, ms);
						potentialTargets.clear();
						continue;
					}
					p1.getBountyHunter().clearTargetInfo();
				}
				e.delay(20);
			}
		});
		//  InterfaceHandler.register(Interface.WILDERNESS_OVERLAY, h -> h.actions[56] = (SimpleAction) p -> p.getBountyHunter().skip(true));
	}

	/**
	 * Misc
	 */

	private static long countWealth(ItemContainer container) {
		long wealth = 0;
		for (Item item : container.getItems()) {
			if (item != null)
				wealth += (item.getAmount() * item.getDef().protectValue);
		}
		return wealth;
	}

	// -----------------------------------------------------------------------
	// Emblem IDs  (antique emblems T1–T10)
	// -----------------------------------------------------------------------
	/** Ordered array: index 0 = T1, index 9 = T10. */
	private static final int[] EMBLEM_IDS = {
		24565, 24567, 24569, 24571, 24573, 24575, 24577, 24579, 24581, 24583
	};

	/** Probability of upgrading to the next tier on a target kill (index = tier - 1). */
	private static final int[] UPGRADE_CHANCE = { 70, 60, 50, 45, 40, 38, 36, 34, 32, 30 };

	/** Base BH points awarded per target kill. */
	private static final int BASE_BH_POINTS = 3;

	private static int emblemTier(int itemId) {
		for (int i = 0; i < EMBLEM_IDS.length; i++) {
			if (EMBLEM_IDS[i] == itemId) return i; // 0-indexed tier
		}
		return -1;
	}

	private static Item findHighestEmblem(Player player) {
		Item highest = null;
		int highestTier = -1;
		for (Item item : player.getInventory().getItems()) {
			if (item == null) continue;
			int tier = emblemTier(item.getId());
			if (tier >= 0 && tier > highestTier) {
				highest = item;
				highestTier = tier;
			}
		}
		return highest;
	}

	/**
	 * Separator
	 */

	private transient Player player;
	public transient boolean interfaceHidden;
	public transient Player target;
	protected transient int returnTicks;
	private transient int returnTimeoutTicks;

	public Targeting targeting = Targeting.ALL;
	private TimedList recentTargets = new TimedList();
	private int skips;
	private long skipsResetAt;
	private long penaltyEnd;

	public void init(Player player) {
		this.player = player;
	}

	/**
	 * Skipping
	 */

	public void skip(boolean warning) {
		if (player.getCombat().isDefending(17)) {
			player.sendMessage("You need to be out of combat for 10 seconds before skipping a bounty target.");
			return;
		}
		if (warning) {
			String message = "<col=ff0000>Warning:</col> Skipping too many targets in a short period of time can<br>cause you to incur a target restriction penalty. You should not use<br>this too frequently.";
			int lineHeight;
			if (skips == 0) {
				lineHeight = 24;
			} else if (skips == 1) {
				message += " You have abandoned your target once recently.";
				lineHeight = 24;
			} else {
				message += " You have abandoned your target " + skips + " times<br>recently.";
				lineHeight = 17;
			}
			player.dialogue(
				new MessageDialogue(message).lineHeight(lineHeight),
				new OptionsDialogue(
					new Option("Yes.", () -> {
						if (target != null)
							skip(false);
						player.closeDialogue();
					}),
					new Option("No.", player::closeDialogue)
				)
			);
		} else {
			skips++;

			Player currentTarget = target;
			if (currentTarget != null)
				currentTarget.getBountyHunter().removeTarget();
			removeTarget();

			if (skips == 1)
				player.sendMessage("<col=ff0000>You have abandoned your target once in the last 30 minutes.");
			else if (skips < 10)
				player.sendMessage("<col=ff0000>You have abandoned your target " + skips + " times in the last 30 minutes.");
			else
				player.sendMessage("<col=ff0000>You have abandoned your target 10 times in 30 minutes. You can't have another one for 30 minutes.");
		}
	}

	/**
	 * Logging out
	 */

	public void loggedOut() {
		if (target != null) {
			target.sendMessage("Your target has logged out, you will be assigned a new one shortly.");
			skip(false);
		}
	}

	/**
	 * Death
	 */

	public boolean deathByTarget(Player pKiller) {
		if (target != null) {
			Player target = this.target;
			target.getBountyHunter().removeTarget();
			removeTarget();
			if (pKiller != null && pKiller.getUserId() == target.getUserId()) {
				long ms = System.currentTimeMillis();
				player.getBountyHunter().penaltyEnd = ms + 360000; //6 minutes (same as rs)
				pKiller.getBountyHunter().penaltyEnd = ms + 60000; //1 minute

				// --- Killstreak & BH points ---
				pKiller.bhKillstreak++;
				pKiller.bhTargetKills++;
				// Reset killed player's killstreak
				player.bhKillstreak = 0;

				int totalPoints = BASE_BH_POINTS + Math.min(pKiller.bhKillstreak - 1, 7); // +0 to +7 streak bonus
				pKiller.UpdateBountyPoints(totalPoints);

				pKiller.sendMessage("<col=ffd700>[Bounty Hunter]</col> <col=ffffff>You killed your target and earned <col=ffd700>"
					+ totalPoints + "</col> <col=ffffff>BH points. Killstreak: <col=ff8c00>" + pKiller.bhKillstreak
					+ "</col> <col=ffffff>— Total: <col=ffd700>" + pKiller.GetBountyPoints());
				pKiller.sendFilteredMessage("Next target in: <col=ff0000>1 minute");

				// --- Upgrade killer's emblem (probability-based) ---
				Item killerEmblem = findHighestEmblem(pKiller);
				if (killerEmblem != null) {
					int tier = emblemTier(killerEmblem.getId());
					if (tier >= 0 && tier < 9) { // T1–T9 can upgrade
						if (Random.get(100) < UPGRADE_CHANCE[tier]) {
							killerEmblem.setId(EMBLEM_IDS[tier + 1]);
							pKiller.sendFilteredMessage("<col=ffd700>[BH]</col> Your emblem upgraded to T" + (tier + 2) + "!");
						}
					}
				} else {
					// Drop a T1 emblem at the kill location
					new GroundItem(EMBLEM_IDS[0], 1).owner(pKiller).position(player.getPosition()).spawn();
					pKiller.sendFilteredMessage("<col=ffd700>[BH]</col> An antique emblem appeared at the kill spot.");
				}

				// --- Downgrade victim's emblem ---
				Item victimEmblem = findHighestEmblem(player);
				if (victimEmblem != null) {
					int tier = emblemTier(victimEmblem.getId());
					if (tier == 0) {
						// T1 emblem destroyed
						player.getInventory().remove(victimEmblem.getId(), 1);
						player.sendFilteredMessage("<col=ff4444>[BH]</col> Your T1 emblem was destroyed on death.");
					} else if (tier > 0) {
						victimEmblem.setId(EMBLEM_IDS[tier - 1]);
						player.sendFilteredMessage("<col=ff4444>[BH]</col> Your emblem was downgraded to T" + tier + ".");
					}
				}

				// --- Kill records ---
				int kills = VarPlayerRepository.BOUNTY_HUNTER_TARGET_KILLS.get(pKiller) + 1;
				int record = VarPlayerRepository.BOUNTY_HUNTER_TARGET_RECORD.get(pKiller);
				if (kills > record)
					VarPlayerRepository.BOUNTY_HUNTER_TARGET_RECORD.set(pKiller, kills);
				VarPlayerRepository.BOUNTY_HUNTER_TARGET_KILLS.set(pKiller, kills);
				return true;
			}
			target.sendMessage("Your target has died, you will be assigned a new one shortly.");
		}
		if (pKiller != null && player.wildernessLevel > 0) {
			int kills = VarPlayerRepository.BOUNTY_HUNTER_ROGUE_KILLS.get(pKiller) + 1;
			int record = VarPlayerRepository.BOUNTY_HUNTER_ROGUE_RECORD.get(pKiller);
			if (kills > record)
				VarPlayerRepository.BOUNTY_HUNTER_ROGUE_RECORD.set(pKiller, kills);
			VarPlayerRepository.BOUNTY_HUNTER_ROGUE_KILLS.set(pKiller, kills);
		}
		return false;
	}

	protected void checkActive() {
		if (targeting == Targeting.DISABLED && target != null) {
			//If player disables their targeting and they have a target, auto skip.
			skip(false);
		}
		if (player.wildernessLevel > 0) {
			if (returnTicks > 0 && ++returnTimeoutTicks >= 60)
				returnTicks = 0;
		} else {
			if (returnTicks == 0) {
				if (target != null) {
					returnTicks = 200;
					VarPlayerRepository.WILDERNESS_TIMER.set(player, returnTicks);
					player.sendMessage("<col=ff0000>You have 2 minutes to return to the Wilderness before you lose your target.");
				}
			} else if (returnTicks > 0) {
				if (--returnTicks == 0) {
					player.closeInterface(ToplevelComponent.WILDERNESS_OVERLAY);
					if (target != null) {
						target.sendMessage("Your target has fled, you will be assigned a new one shortly.");
						skip(false);
					}
				} else {
					if (returnTicks == 100)
						player.sendMessage("<col=ff0000>You have one minute to return to the Wilderness before you lose your target.");
					if (returnTimeoutTicks > 0) {
						VarPlayerRepository.WILDERNESS_TIMER.set(player, returnTicks + 1); //+ 1 for slightly better accuracy...
					}
				}
			}
			returnTimeoutTicks = 0;
		}
	}

	/**
	 * Targeting
	 */

	private boolean searchForTarget(long ms) {
		if (player.getCombat().isDead())
			return false;
		if (target != null) {
			updateTargetInfo();
			return false;
		}
		if (!targeting.isActive(player))
			return false;
		if (hasPenalty(ms)) {
			updatePenaltyInfo(Math.max(1L, TimeUnit.MILLISECONDS.toMinutes(penaltyEnd - ms)));
			return false;
		}
		return true;
	}

	private boolean allowAsTarget(Player searching, long ms) {
		if (player.getCombat().isDead())
			return false;
		if (player.getUserId() == searching.getUserId())
			return false;
		if (player.getIpInt() == searching.getIpInt())
			return false;
		if (Math.abs(player.getCombat().getLevel() - searching.getCombat().getLevel()) > 5)
			return false;
		if (target != null)
			return false;
		if (!Targeting.match(player, searching))
			return false;
		if (hasPenalty(ms))
			return false;
		return !recentTargets.contains(searching.getUserId(), ms, 10L);
	}

	private void target(Player pTarget, long ms) {
		target = pTarget;
		recentTargets.add(pTarget.getUserId(), ms);
		player.getPacketSender().sendHintIcon(target);
		player.sendMessage("<col=ff0000>You've been assigned a target: " + pTarget.getName());
		updateTargetInfo();
	}

	private void removeTarget() {
		target = null;
		player.getPacketSender().resetHintIcon(false);
		if (player.wildernessLevel == 0)
			player.closeInterface(ToplevelComponent.WILDERNESS_OVERLAY);
		returnTicks = returnTimeoutTicks = 0;
		clearTargetInfo();
	}

	private boolean hasPenalty(long ms) {
		if (skips >= 10) {
			skipsResetAt = skips = 0;
			penaltyEnd = ms + 1800000; //30 minutes
			return true;
		}
		if (skips > 0) {
			if (skipsResetAt == 0)
				skipsResetAt = ms + 1800000; //30 minutes
			else if (ms >= skipsResetAt)
				skipsResetAt = skips = 0;
		}
		return ms < penaltyEnd;
	}

	/**
	 * Info (Interface)
	 */

	private void updateTargetInfo() {
		/**
		 * Target info
		 */
		String skullImg = target.getCombat().isSkulled() ? "<img=" + KillingSpree.imgId(target) + "> " : "";
		int distance = Misc.getDistance(player.getPosition(), target.getPosition());
		String distanceColor;
		if (distance <= 20)
			distanceColor = "<col=009900>";
		else if (distance <= 40)
			distanceColor = "<col=996600>";
		else if (distance <= 60)
			distanceColor = "<col=990000>";
		else if (distance <= 80)
			distanceColor = "<col=df0101>";
		else
			distanceColor = "<col=0080ff>";
		String wild;
		if (target.wildernessLevel > 0) {
			int base = (int) Math.floor(target.wildernessLevel / 5) * 5;
			wild = "Lvl " + Math.max(1, base) + "-" + (base + 4);
		} else {
			wild = "Safe";
		}
		player.getPacketSender().sendString(Interface.WILDERNESS_OVERLAY, 47, skullImg + target.getName());
		player.getPacketSender().sendString(Interface.WILDERNESS_OVERLAY, 48, distanceColor + wild + ", Cmb " + target.getCombat().getLevel());

		/**
		 * Target wealth
		 */
		long targetWealth = countWealth(target.getInventory()) + countWealth(target.getEquipment());

		if (target.getInventory().hasId(11941))
			targetWealth += countWealth(target.getLootingBag());
		if (target.getInventory().hasId(12791))
			targetWealth += countWealth(target.getRunePouch());
		if (target.getInventory().hasId(27281))
			targetWealth += countWealth(target.DivinerunePouch);

		if (targetWealth >= 2500000)
			VarPlayerRepository.BOUNTY_HUNTER_RISK.set(player, 5); //red
		else if (targetWealth >= 1100000)
			VarPlayerRepository.BOUNTY_HUNTER_RISK.set(player, 4); //blue
		else if (targetWealth >= 500000)
			VarPlayerRepository.BOUNTY_HUNTER_RISK.set(player, 3); //green
		else if (targetWealth >= 100000)
			VarPlayerRepository.BOUNTY_HUNTER_RISK.set(player, 2); //silver
		else
			VarPlayerRepository.BOUNTY_HUNTER_RISK.set(player, 1); //brown

		/**
		 * Target emblems
		 */
		Item highestEmblem = findHighestEmblem(target);
		VarPlayerRepository.BOUNTY_HUNTER_EMBLEM.set(player, highestEmblem == null ? 0 : emblemTier(highestEmblem.getId()) + 1);
	}

	private void clearTargetInfo() {
		player.getPacketSender().sendString(90, 47, "None");
		player.getPacketSender().sendString(90, 48, "Level: -----");
		VarPlayerRepository.BOUNTY_HUNTER_RISK.set(player, 0);
		VarPlayerRepository.BOUNTY_HUNTER_EMBLEM.set(player, 0);
	}

	private void updatePenaltyInfo(long minutes) {
		player.getPacketSender().sendString(90, 47, "<col=ff0000>---</col>");
		player.getPacketSender().sendString(90, 48, minutes + " " + (minutes > 1 ? "mins" : "min"));
		VarPlayerRepository.BOUNTY_HUNTER_RISK.set(player, 0);
		VarPlayerRepository.BOUNTY_HUNTER_EMBLEM.set(player, 0);
	}

}

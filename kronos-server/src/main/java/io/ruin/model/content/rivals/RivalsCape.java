package io.ruin.model.content.rivals;

import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.dialogue.MessageDialogue;
import io.ruin.model.inter.dialogue.OptionsDialogue;
import io.ruin.model.inter.utils.Option;
import io.ruin.model.item.actions.ItemAction;
import io.ruin.model.skills.magic.spells.modern.ModernTeleport;
import io.ruin.model.stat.Stat;
import io.ruin.model.stat.StatType;

import java.util.Locale;

/**
 * The Division Rivals capes' options (cache: rivals_capes_ops.txt).
 * <ul>
 * <li>Both capes: Teleport (inventory and worn) to the Rivals lobby.</li>
 * <li>Champion cape (Elite, 3,500 points): Features (its perks + your career Rivals stats) and Rejuvenate (worn, or from
 * Features): run energy to 100% and every boosted / drained stat back to its base level, outside the Wilderness, out of
 * combat and outside a match, once every 3 minutes. Owning the cape (worn, carried or banked) gives +15% Rivals Points
 * on wins (RivalsProfile). Its combat bonuses are in item_info.json.</li>
 * </ul>
 */
public final class RivalsCape {

	private static final long REJUVENATE_COOLDOWN_MS = 3 * 60_000L;
	private static final String REJUVENATE_KEY = "rivals-cape-rejuvenated";
	private static final String TAG = "<col=990000><shad=000000>[Rivals]</shad></col> ";

	private RivalsCape() {
	}

	static void register() {
		for (int cape : new int[]{RivalsShop.CHALLENGER_CAPE, RivalsShop.CHAMPION_CAPE}) {
			ItemAction.registerInventory(cape, "teleport", (player, item) -> teleport(player));
			ItemAction.registerEquipment(cape, "teleport", (player, item) -> teleport(player));
		}
		ItemAction.registerInventory(RivalsShop.CHAMPION_CAPE, "features", (player, item) -> features(player));
		ItemAction.registerEquipment(RivalsShop.CHAMPION_CAPE, "features", (player, item) -> features(player));
		ItemAction.registerEquipment(RivalsShop.CHAMPION_CAPE, "rejuvenate", (player, item) -> rejuvenate(player));
	}

	/** Worn, carried or banked: the +15% points perk counts ownership, not just wearing it. */
	static boolean ownsChampionCape(Player p) {
		return p.getEquipment().contains(RivalsShop.CHAMPION_CAPE) || p.getInventory().contains(RivalsShop.CHAMPION_CAPE)
			|| p.getBank().contains(RivalsShop.CHAMPION_CAPE);
	}

	private static void teleport(Player p) {
		if (RivalsQueueManager.inMatch(p) || RivalsSnapshot.active(p)) {
			p.sendMessage("You can't teleport out of a Rivals match.");
			return;
		}
		ModernTeleport.teleport(p, DivisionRivalsInterfaceManager.LOBBY.getX(), DivisionRivalsInterfaceManager.LOBBY.getY(),
			DivisionRivalsInterfaceManager.LOBBY.getZ());
	}

	private static void features(Player p) {
		String stats = String.format(Locale.ROOT, "%,d SR - %s - %dW %dL - best streak %d - %,d points",
			p.rivalsRating, p.rivalsDivision == 0 ? "Elite" : RivalsProfile.name(p.rivalsDivision) + " (D" + p.rivalsDivision + ")",
			p.rivalsWins, p.rivalsLosses, p.rivalsBestStreak, p.rivalsPoints);
		p.dialogue(
			new MessageDialogue("<col=990000>Rivals Champion cape</col><br>+15% Rivals Points on every win (while you own it)<br>"
				+ "Rejuvenate: full run energy and stats reset, every 3 minutes<br>Teleport: straight to the Rivals lobby"),
			new MessageDialogue("<col=990000>Your Rivals career</col><br>" + stats),
			new OptionsDialogue("Champion cape",
				new Option("Rejuvenate", () -> rejuvenate(p)),
				new Option("Teleport to the Rivals lobby", () -> teleport(p)),
				new Option("Close", p::closeDialogue)));
	}

	private static void rejuvenate(Player p) {
		p.closeDialogue();
		if (RivalsQueueManager.inMatch(p) || RivalsSnapshot.active(p)) {
			p.sendMessage("The cape's power can't be used during a Rivals match.");
			return;
		}
		if (p.wildernessLevel > 0 || p.pvpAttackZone || p.getCombat().isAttacking(16) || p.getCombat().isDefending(16)) {
			p.sendMessage("You need to be somewhere safe and out of combat to rejuvenate.");
			return;
		}
		Object last = p.temp.get(REJUVENATE_KEY);
		long now = System.currentTimeMillis();
		if (last instanceof Long at && now - at < REJUVENATE_COOLDOWN_MS) {
			long s = (REJUVENATE_COOLDOWN_MS - (now - at)) / 1000;
			p.sendMessage("The cape can rejuvenate you again in " + (s / 60) + "m " + (s % 60) + "s.");
			return;
		}
		p.temp.put(REJUVENATE_KEY, now);
		p.getMovement().restoreEnergy(100);
		for (StatType type : StatType.values()) {
			Stat stat = p.getStats().get(type);
			if (stat.currentLevel != stat.fixedLevel) {
				stat.currentLevel = stat.fixedLevel;
				stat.updated = true;
			}
		}
		p.setHp(p.getStats().get(StatType.Hitpoints).fixedLevel);
		p.graphics(io.ruin.model.content.cosmetic.Stardust.GOLD);
		p.sendMessage(TAG + "The Champion cape rejuvenates you: full run energy, and your stats are back to normal.");
	}
}

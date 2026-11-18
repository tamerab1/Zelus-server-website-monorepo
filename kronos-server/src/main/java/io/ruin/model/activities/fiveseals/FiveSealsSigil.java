package io.ruin.model.activities.fiveseals;

import io.ruin.cache.Color;
import io.ruin.model.entity.player.Player;
import io.ruin.model.item.actions.ItemAction;

/**
 * The Ascension sigil (user 2026-11-17: "remove the Karn partyhat from the Five Seals rewards and swap with something like
 * a boost or something that can be upgraded"): items 42777-42781, tiers 1-5 (cache stage 55 PatchAscensionSigil),
 * untradeable.
 * <ul>
 * <li>Owning one (inventory or bank: the highest tier counts) raises everything a Five Seals run pays by
 * {@link #BONUS_PER_TIER} per tier: shards, perk points, coins and the Item Upgrader materials.</li>
 * <li>The Token Shop sells tier 1 and upgrades it one tier at a time ({@link FiveSealsShop#SIGIL_PRICES}): the old sigil
 * is taken and the next tier given.</li>
 * <li>The rare full-clear drop (where the Karn partyhat was) upgrades it one tier for free, or gives tier 1 if you have
 * none; a tier 5 owner gets {@link #MAXED_SHARDS} shards instead.</li>
 * </ul>
 */
public final class FiveSealsSigil {

	private FiveSealsSigil() {
	}

	static final int FIRST = 42777, TIERS = 5, MAXED_SHARDS = 5_000;
	static final double BONUS_PER_TIER = 0.05;

	static int item(int tier) {
		return FIRST + tier - 1;
	}

	static void register() {
		for (int t = 1; t <= TIERS; t++) {
			int tier = t;
			ItemAction.registerInventory(item(t), 1, (p, it) -> {
				int owned = tier(p);
				p.sendMessage(Color.PURPLE.wrap("Ascension sigil, tier " + tier + " of " + TIERS + ": The Five Seals pay you +"
					+ percent(tier) + "% more (shards, perk points, coins and upgrade materials).")
					+ (owned > tier ? " You own a tier " + owned + " sigil, which counts instead." : "")
					+ (tier < TIERS ? " Upgrade it at the Five Seals Token Shop." : ""));
			});
		}
	}

	/** the highest tier the player owns (inventory or bank), 0 = none */
	static int tier(Player p) {
		for (int t = TIERS; t >= 1; t--)
			if (p.getInventory().getAmount(item(t)) > 0 || p.getBank().getAmount(item(t)) > 0)
				return t;
		return 0;
	}

	/** x(1 + bonus) on a run's pay */
	static double multiplier(Player p) {
		return 1 + BONUS_PER_TIER * tier(p);
	}

	static int percent(int tier) {
		return (int) Math.round(BONUS_PER_TIER * 100 * tier);
	}

	/**
	 * Takes the owned sigil (if any) and gives the next tier, in the inventory if there's room, else the bank.
	 * @return the new tier, or -1 if already at tier 5 or the old sigil could not be taken
	 */
	static int upgrade(Player p) {
		int tier = tier(p);
		if (tier >= TIERS)
			return -1;
		if (tier > 0) {
			int old = item(tier);
			boolean taken = p.getInventory().getAmount(old) > 0 ? p.getInventory().remove(old, 1) == 1 : p.getBank().remove(old, 1) == 1;
			if (!taken)
				return -1;
		}
		int next = item(tier + 1);
		if (p.getInventory().hasRoomFor(next, 1))
			p.getInventory().add(next, 1);
		else {
			p.getBank().add(next, 1, null);
			p.sendMessage("Your Ascension sigil was sent to your bank.");
		}
		return tier + 1;
	}

	/** the rare full-clear drop */
	static void rareDrop(Player p) {
		int tier = upgrade(p);
		if (tier > 0) {
			p.sendMessage(Color.RAID_PURPLE.wrap("The Sovereign Vault empowers your Ascension sigil: it is now tier " + tier
				+ " (+" + percent(tier) + "% Five Seals pay)!"));
			return;
		}
		p.getInventory().addOrDrop(FiveSealsShop.SHARDS, MAXED_SHARDS);
		p.sendMessage(Color.RAID_PURPLE.wrap("Your Ascension sigil is already radiant: the Sovereign Vault pays you "
			+ String.format("%,d", MAXED_SHARDS) + " Ascension shards instead."));
	}
}

package io.ruin.model.content.bonds;

import io.ruin.model.entity.player.Player;
import io.ruin.model.item.Item;

import java.util.HashMap;
import java.util.Map;

/**
 * The six Custom Bonds. A bond's tier and attuned state are part of its item id, so the
 * client's right-click menu (Attune vs Sever) and tradeability follow the id:
 * <ul>
 *   <li>T1 unattuned: the original bond ids (real + ground display proxy), tradeable.</li>
 *   <li>T2-T5 unattuned and all attuned ids: untradeable.</li>
 * </ul>
 * A bond's perks are active only while an ATTUNED copy is in the player's inventory -- see
 * {@link #tier(Player)} and {@link Player#getBondTier(BondType)}. Banking, dropping or severing it
 * turns them off immediately.
 *
 * <p>Id layout per bond block {@code b} (cache spec: .dev/cache-restore-tool/bond_tier_variants.txt):
 * b+0..b+3 = unattuned T2-T5, b+4..b+8 = attuned T1-T5, bank placeholders at id + 100.</p>
 */
public enum BondType {

	// Perk text describes what each tier grants in total (it's what Inspect and ::bonds show), and must
	// match what BondPerks actually implements. Combat perks apply against NPCs only. Each tier is a
	// few short lines separated by \n (max ~42 chars each -- the ::bonds scroll doesn't wrap).
	MELEE("Bond of the Blood Titan", "Melee", 60277, 31153, 31340,
			"Heal 0.5% of melee damage dealt\n2% chance to save spec energy",
			"Heal 1% of melee damage dealt\n4% spec save | +2 melee strength",
			"Heal 1.5% of melee damage dealt\n6% spec save | +2 melee strength\n5% cleave to 2 adjacent monsters\n(cleave only in multi-combat)",
			"Heal 2% of melee damage dealt\n8% spec save | +4 melee strength\n10% cleave to 2 adjacent monsters",
			"Heal 2.5% of melee damage dealt\n10% spec save | +4 melee strength\n20% cleave to 2 adjacent monsters\nMonster stat drains halved"),

	SLAYER("Bond of the Slayer King", "Slayer & Bossing", 60278, 31154, 31350,
			"+10% Slayer points per task",
			"+10% Slayer points per task\nTask skip & block costs halved",
			"+15% Slayer points | half skip costs\nSuperiors appear 25% more often",
			"+20% Slayer points | half skip costs\nSuperiors appear 25% more often\n+5% accuracy & damage vs your task",
			"Everything from T4\nRare drops (1/100+) 5% more likely"),

	MAGIC("Bond of the Astral Archmage", "Magic", 60279, 31155, 31360,
			"Free Air runes\n3% chance for Spell Echo (50% dmg)",
			"Free Air & Water runes\n6% Spell Echo | +2% magic damage",
			"Free Air, Water & Earth runes\n9% Spell Echo | +4% magic damage",
			"Free Air, Water, Earth & Fire runes\n12% Spell Echo | +6% magic damage",
			"All basic elemental runes free\n15% Spell Echo | +8% magic damage\nMagic hits 40+ restore 2 Prayer"),

	UTILITY("Bond of the Sovereign Monarch", "Utility", 60280, 31156, 31370,
			"Infinite run energy",
			"Infinite run energy\n2x HP & spec regen near banks/home",
			"Infinite run | 2x safe-zone regen\nDrops go straight to your inventory",
			"Everything from T3\nPocket Bank: ::bank every 10 mins\n(not in Wilderness or PvP areas)\nPotion & food boosts last 10% longer",
			"Everything from T4\nDrops that don't fit go to your bank\n(toggle with ::vaultloot)"),

	SKILLING("Bond of the Artisan", "Skilling", 60281, 31157, 31380,
			"+5% non-combat skill XP",
			"+5% skilling XP\n10% double resources\n(Mining, Woodcutting, Fishing)",
			"+5% skilling XP | 10% double\n15% chance to save bars, leather\nor herbs when making items",
			"+5% skilling XP | 10% double\n15% material saving\n25% of resources go to your bank",
			"+15% skilling XP | 35% double\n15% material saving | 25% auto-bank\n10% chance: resources become coins"),

	RANGED("Bond of the Void Deadeye", "Ranged", 60282, 31158, 31390,
			"20% chance to save ammo\nIgnore 3% of target's defence",
			"40% ammo saving | +2 ranged str\nIgnore 6% of target's defence",
			"60% ammo saving | +2 ranged str\nIgnore 9% of target's defence\nHit streak: +5% accuracy\n(streak = 2+ hits in a row)",
			"80% ammo saving | +2 ranged str\nIgnore 12% of target's defence\nHit streak: +10% accuracy, +1 max hit",
			"Ammo is never used up\n+2 ranged str\nIgnore 15% of target's defence\nHit streak: +15% accuracy, +2 max hit");

	public static final int MAX_TIER = 5;
	public static final BondType[] VALUES = values();

	/** What one bond item id means. */
	public record Variant(BondType type, int tier, boolean attuned) {
	}

	private static final Map<Integer, Variant> BY_ID = new HashMap<>();

	public final String bondName;
	public final String category;
	/** The tradeable T1 bond, and its low-id ground display proxy (same bond). */
	public final int baseItemId, baseProxyItemId;
	private final int block;
	private final String[] perks;

	BondType(String bondName, String category, int baseItemId, int baseProxyItemId, int block, String... perks) {
		if (perks.length != MAX_TIER)
			throw new IllegalArgumentException("need exactly " + MAX_TIER + " tier descriptions");
		this.bondName = bondName;
		this.category = category;
		this.baseItemId = baseItemId;
		this.baseProxyItemId = baseProxyItemId;
		this.block = block;
		this.perks = perks;
	}

	static {
		for (BondType type : values()) {
			BY_ID.put(type.baseItemId, new Variant(type, 1, false));
			BY_ID.put(type.baseProxyItemId, new Variant(type, 1, false));
			for (int tier = 1; tier <= MAX_TIER; tier++) {
				if (tier > 1)
					BY_ID.put(type.itemId(tier, false), new Variant(type, tier, false));
				BY_ID.put(type.itemId(tier, true), new Variant(type, tier, true));
			}
		}
	}

	/** Item id for a tier/state. Unattuned T1 returns the real base item (not the proxy). */
	public int itemId(int tier, boolean attuned) {
		if (tier < 1 || tier > MAX_TIER)
			throw new IllegalArgumentException("tier " + tier);
		if (attuned)
			return block + 4 + (tier - 1);
		return tier == 1 ? baseItemId : block + (tier - 2);
	}

	/** Perk description for a tier (1-5), as one sentence for dialogues. */
	public String perks(int tier) {
		return String.join(", ", perkLines(tier));
	}

	/** Perk description for a tier (1-5), as short lines (each fits the ::bonds scroll). */
	public String[] perkLines(int tier) {
		return perks[tier - 1].split("\n");
	}

	/** Display name including tier, e.g. "Bond of the Blood Titan (T3)"; tier 5 shows "(MAX)". */
	public String displayName(int tier) {
		return bondName + " (" + tierLabel(tier) + ")";
	}

	/** "T1".."T4", or "MAX" for tier 5 -- matches the item names in the cache. */
	public static String tierLabel(int tier) {
		return tier >= MAX_TIER ? "MAX" : "T" + tier;
	}

	/**
	 * Extra unattuned T1 bonds of the same type needed to go from {@code tier} to {@code tier + 1}:
	 * T1->T2 costs 1, T2->T3 costs 2, T3->T4 costs 3, T4->T5 costs 4.
	 */
	public static int upgradeCost(int tier) {
		return tier;
	}

	public static Variant variant(int itemId) {
		return BY_ID.get(itemId);
	}

	/**
	 * The tier of this bond's attuned copy in the player's inventory, or 0 if none is attuned there.
	 * Gameplay hooks should call this (via {@link Player#getBondTier}) every time instead of caching.
	 */
	public int tier(Player player) {
		int best = 0;
		for (Item item : player.getInventory().getItems()) {
			if (item == null)
				continue;
			Variant v = BY_ID.get(item.getId());
			if (v != null && v.attuned() && v.type() == this && v.tier() > best)
				best = v.tier();
		}
		return best;
	}
}

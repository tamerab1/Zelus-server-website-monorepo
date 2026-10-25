package io.ruin.model.activities.tournament;

import io.ruin.cache.ObjType;
import io.ruin.model.entity.player.Player;
import io.ruin.model.item.Item;
import io.ruin.model.item.containers.Equipment;
import io.ruin.model.stat.StatType;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * The Pit Championship formats, played in rotation. Combat stats are set to the format's levels for the whole
 * tournament (restored afterwards). Players bring their own setup; anything they can't use at the format's levels
 * (or a weapon the format bans) is moved to their bank when the tournament starts. Boxing strips everything except
 * one pair of boxing gloves and turns off prayer, magic, food and drinks.
 */
public enum TournamentFormat {

	PURE_NH("Pure NH", "1 Def, 60 Atk, 99 Str/Range/Mage", "1 Def / 60 Atk",
		new int[]{60, 1, 99, 99, 99, 52, 99}, null, null, false),
	TRIBRID("Tribrid Max Main", "99 in every combat stat", "none",
		new int[]{99, 99, 99, 99, 99, 99, 99}, null, null, false),
	DDS_WHIP("DDS / Whip", "99 melee, 1 Range/Mage", "1 Range / 1 Mage",
		new int[]{99, 99, 99, 99, 1, 99, 1},
		"Only whips and dragon daggers allowed",
		def -> {
			String name = def.name.toLowerCase();
			return name.contains("whip") || name.contains("dragon dagger");
		}, false),
	MAIN_NH("Main NH", "75 Atk/Def, 99 Str/Range/Mage", "75 Atk / 75 Def",
		new int[]{75, 75, 99, 99, 99, 77, 99}, null, null, false),
	/** DH PK: Dharok's set with a Dharok's greataxe / whip / dragon scimitar and a DDS for specs; Vengeance on (99 Magic). */
	DHAROKS("Dharok's", "99 melee/Magic, Vengeance, 1 Range", "1 Range",
		new int[]{99, 99, 99, 99, 1, 99, 99},
		"Only Dharok's axe, whip, d scim and DDS",
		def -> {
			String name = def.name.toLowerCase();
			return name.contains("dharok's greataxe") || name.contains("whip") || name.contains("dragon scimitar")
				|| name.contains("dragon dagger");
		}, false),
	BOXING("Boxing", "Gloves only: no prayer, magic or food", "gloves only",
		new int[]{99, 99, 99, 99, 1, 1, 1},
		"Boxing gloves only", def -> isBoxingGloves(def.id), true);

	/** Stat order of {@link #levels}. */
	public static final StatType[] COMBAT_STATS = {
		StatType.Attack, StatType.Defence, StatType.Strength,
		StatType.Hitpoints, StatType.Ranged, StatType.Prayer, StatType.Magic
	};

	public static final TournamentFormat[] VALUES = values();

	/** Boxing gloves, weakest first: Red, Blue (T1), then the Pit glove tiers T2-T5. */
	public static final int RED_GLOVES = 7671, BLUE_GLOVES = 7673;
	public static final int[] BOXING_GLOVES = {RED_GLOVES, BLUE_GLOVES, 42550, 42552, 42554, 42556};

	public final String name;
	public final String style;
	/** Short level caps for messages, e.g. "1 Def / 60 Atk". */
	public final String caps;
	public final int[] levels;
	/** Shown in the rules; null when every weapon is allowed. */
	public final String weaponRule;
	private final Predicate<ObjType> weaponAllowed;
	/** Gloves only; prayer, magic, food and drinks are off for the whole tournament. */
	public final boolean boxing;

	TournamentFormat(String name, String style, String caps, int[] levels, String weaponRule, Predicate<ObjType> weaponAllowed, boolean boxing) {
		this.name = name;
		this.style = style;
		this.caps = caps;
		this.levels = levels;
		this.weaponRule = weaponRule;
		this.weaponAllowed = weaponAllowed;
		this.boxing = boxing;
	}

	public static boolean isBoxingGloves(int id) {
		for (int glove : BOXING_GLOVES)
			if (glove == id)
				return true;
		return false;
	}

	/** Higher = better glove; -1 when not boxing gloves. */
	public static int gloveTier(int id) {
		for (int i = 0; i < BOXING_GLOVES.length; i++)
			if (BOXING_GLOVES[i] == id)
				return Math.max(0, i - 1);
		return -1;
	}

	public int level(StatType type) {
		for (int i = 0; i < COMBAT_STATS.length; i++)
			if (COMBAT_STATS[i] == type)
				return levels[i];
		return 1;
	}

	/**
	 * Worn items this format doesn't allow; these are taken off at the start (inventory first, then bank). In boxing
	 * that is everything except the gloves the player will fight in ({@code keep}). Carried items are left alone:
	 * TournamentHandler.mayEquip stops them being put on mid-tournament.
	 */
	public List<Item> restrictedItems(Player player, Item keep) {
		List<Item> list = new ArrayList<>();
		for (Item item : player.getEquipment().getItems())
			if (item != null && item != keep && !allowsEquip(item.getDef()))
				list.add(item);
		return list;
	}

	/** Whether this format lets the item be worn (boxing: gloves only; else level caps and the weapon rule). */
	public boolean allowsEquip(ObjType def) {
		if (boxing)
			return def != null && isBoxingGloves(def.id);
		if (def == null)
			return true;
		if (def.equipReqs != null) {
			for (int req : def.equipReqs) {
				StatType type = StatType.values()[req >> 8];
				if ((req & 0xFF) > level(type))
					return false;
			}
		}
		return weaponAllowed == null || def.equipSlot != Equipment.SLOT_WEAPON || weaponAllowed.test(def);
	}
}

package io.ruin.model.content.soulweapons;

/**
 * The three Soulbound weapon lines (2026-10-18). Each has three looks on three different meshes, swapped in place as the
 * weapon's tier rises: base (tiers 1-3), Empowered (4-7) and Ascended (8-10, with a special attack). Each look also has
 * its own attack style (item_info weapon_type; the Spire's spell in PlayerCombat). Cache: build_soulbound.sh.
 */
public enum SoulWeaponType {
	MELEE("Soulbound blade", 42624, 42626, 42628),
	RANGED("Soulbound recurve", 42630, 42632, 42634),
	MAGIC("Soulbound spire", 42636, 42638, 42640);

	public final String name;
	/** Item ids: [0] base, [1] Empowered, [2] Ascended. */
	private final int[] looks;

	SoulWeaponType(String name, int base, int empowered, int ascended) {
		this.name = name;
		this.looks = new int[]{base, empowered, ascended};
	}

	/** The item id this line uses at a tier. */
	public int itemFor(int tier) {
		return looks[band(tier)];
	}

	/** 0 = base (tiers 1-3), 1 = Empowered (4-7), 2 = Ascended (8-10). */
	public static int band(int tier) {
		return tier >= 8 ? 2 : tier >= 4 ? 1 : 0;
	}

	/** The look (band) of one of this line's item ids, or -1. */
	public int bandOf(int itemId) {
		for (int i = 0; i < looks.length; i++)
			if (looks[i] == itemId)
				return i;
		return -1;
	}

	public boolean isLook(int itemId) {
		for (int id : looks)
			if (id == itemId)
				return true;
		return false;
	}

	public int[] looks() {
		return looks.clone();
	}

	/** The line an item id belongs to, or null. */
	public static SoulWeaponType of(int itemId) {
		for (SoulWeaponType t : values())
			if (t.isLook(itemId))
				return t;
		return null;
	}
}

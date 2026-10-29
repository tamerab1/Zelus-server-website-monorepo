package io.ruin.model.content.soulweapons;

/**
 * The three Soulbound weapon lines (2026-10-18). Each has four looks on four different meshes, swapped in place as the
 * weapon's tier rises: base (tiers 1-3), Empowered (4-7), Ascended (8-14, with a special attack) and Radiant (tier 15,
 * customs27 2026-10-27: new meshes, their own animated textures and a stronger special). Each look also has its own
 * attack style (item_info weapon_type; the Spire's spell in PlayerCombat). Cache: build_soulbound.sh + stage 7 of the
 * rev 241 build (PatchCustoms27).
 */
public enum SoulWeaponType {
	MELEE("Soulbound blade", 42624, 42626, 42628, 42762),
	RANGED("Soulbound recurve", 42630, 42632, 42634, 42763),
	MAGIC("Soulbound spire", 42636, 42638, 42640, 42764);

	public final String name;
	/** Item ids: [0] base, [1] Empowered, [2] Ascended, [3] Radiant. */
	private final int[] looks;

	SoulWeaponType(String name, int base, int empowered, int ascended, int radiant) {
		this.name = name;
		this.looks = new int[]{base, empowered, ascended, radiant};
	}

	/** The item id this line uses at a tier. */
	public int itemFor(int tier) {
		return looks[band(tier)];
	}

	public static final int RADIANT_TIER = 15;

	/** 0 = base (tiers 1-3), 1 = Empowered (4-7), 2 = Ascended (8-14), 3 = Radiant (15). */
	public static int band(int tier) {
		return tier >= RADIANT_TIER ? 3 : tier >= 8 ? 2 : tier >= 4 ? 1 : 0;
	}

	/** The first tier of a look. */
	public static int firstTier(int band) {
		return new int[]{1, 4, 8, RADIANT_TIER}[band];
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

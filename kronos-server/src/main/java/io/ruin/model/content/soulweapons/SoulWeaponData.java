package io.ruin.model.content.soulweapons;

import java.util.HashMap;
import java.util.Map;

/**
 * A player's Soulbound weapon (2026-10-18), saved with the player (PlayerAttributes.soulWeapon; Gson, so plain fields
 * and a no-arg constructor). Created once, when the weapon is chosen; the item itself only carries the look, every bit
 * of progression lives here so a lost or destroyed weapon can be recovered with nothing reset.
 */
public class SoulWeaponData {

	public SoulWeaponType weaponType;
	/** 1..10. */
	public int tier = 1;
	/** Total Soul XP ever gained; the tier follows from it (SoulWeapons.tierFor). */
	public long soulXp;
	public int availableSoulPoints;
	/** SoulTalent name -> rank (1..5); points spent = the sum. */
	public Map<String, Integer> allocatedTalents = new HashMap<>();
	/** Soul Tree resets so far: the first is free (SoulTreeInterface). */
	public int talentResets;
	/** Death Ward's next ready time (epoch ms), saved so logging out can't skip the cooldown. */
	public long deathWardReadyAt;
	/** SoulAura name worn, or null. */
	public String activeCosmeticAura;
	/** Monsters slain while wielding the weapon. */
	public long soulsHarvested;

	public SoulWeaponData() {
	}

	SoulWeaponData(SoulWeaponType type) {
		this.weaponType = type;
	}
}

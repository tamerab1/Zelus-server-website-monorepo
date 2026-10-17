package io.ruin.model.content.rivals;

import io.ruin.model.content.bonds.BondType;
import io.ruin.model.entity.Entity;
import io.ruin.model.entity.player.Player;
import io.ruin.model.item.actions.impl.pet.perk.PetPerkHandler;

/**
 * Division Rivals is unbuffed: inside a match only the kit, the bracket's levels and native OSRS mechanics count
 * (2026-10-17: spec-saving perks made specs cost a fraction of their 25%/50% -- the Rivals instance is wilderness
 * level 0, and every "wildernessLevel < 1" discount applied).
 * <p>
 * {@link Player#rivalsFight} is set by RivalsSnapshot when the kit goes on and cleared at the top of
 * RivalsSnapshot.restore, which every way out runs through (result, forfeit, logout / technical loss, login recovery
 * after a crash). It is transient, so it can never outlive the snapshot. Nothing is taken off the player: the hooks
 * below just read "nothing active" while the flag is up, so the real perks / pet / bonds are all there afterwards.
 * <p>
 * Gated hooks:
 * <ul>
 * <li>Perk Tree: PlayerPerkHandler.getActivePerks / getActivePerkSets / getActivePerkSetLevel return nothing (every
 * perk effect in combat, specials, prayer and drains reads through these). Perk-set evaluation reads the real list so a
 * mid-match update can't switch sets off.</li>
 * <li>Pet perks: PetPerkHandler.getActivePetPerk is null (accuracy, damage, defence, spec regen, prayer drain).</li>
 * <li>Custom Bonds: Player.getBondTier / hasAttunedBond are 0 (spec save, spec regen, strength bonuses).</li>
 * <li>Special energy: PlayerCombat.useSpecialEnergy and handleSpecial skip The Special Attacker, the forge
 * SPECIAL_ENERGY_LOWERER attribute and liquid adrenaline, so specs cost exactly the weapon's native drain.</li>
 * <li>Carried-in timers: {@link #enter} ends berserker rage (halved attack speed) and liquid adrenaline.</li>
 * </ul>
 * Not gated because they can't reach a match: weapon/armour attributes and custom set effects (the kit replaces all
 * gear with plain phantom items), combat pets (never attack players), Statue of Fortune blessings (no combat
 * blessings). Native set effects in a kit (e.g. Dharok's) are part of the bracket and stay.
 */
public final class RivalsFairPlay {

	private RivalsFairPlay() {
	}

	public static boolean active(Player player) {
		return player != null && player.inRivalsFight();
	}

	public static boolean active(Entity entity) {
		return entity != null && entity.player != null && entity.player.inRivalsFight();
	}

	/** The kit is on: raise the flag and end timed buffs started before the match. */
	static void enter(Player player) {
		player.rivalsFight = true;
		player.berserkerRageActive = false;
		player.liquidAdrenalineDelay.reset();
	}

	static void leave(Player player) {
		player.rivalsFight = false;
	}

	/** ::rivalsfair -- what the combat code sees for this player right now (dev check, works in and out of a match). */
	static void report(Player player) {
		int bonds = 0;
		for (BondType type : BondType.values())
			bonds += player.getBondTier(type);
		player.sendMessage("<col=990000>[Rivals fair play]</col> in fight: " + active(player)
			+ " | perks seen: " + player.getPlayerPerkHandler().getActivePerks(player).size() + "/" + player.activePerksList.size()
			+ " | perk sets seen: " + player.getPlayerPerkHandler().getActivePerkSets(player).size() + "/" + player.activePerkSetsList.size()
			+ " | pet perk: " + (PetPerkHandler.getActivePetPerk(player) != null)
			+ " | bond tiers: " + bonds);
	}
}

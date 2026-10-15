package io.ruin.model.entity.npc.combatpet.tiered;

import io.ruin.model.combat.AttackStyle;
import io.ruin.model.entity.npc.NPC;
import io.ruin.model.entity.npc.PetNPC;
import io.ruin.model.entity.npc.combatpet.PetTier;
import io.ruin.model.entity.npc.combatpet.TieredCombatPetProfile;
import io.ruin.model.entity.player.Player;
import io.ruin.model.map.Projectile;

/**
 * Jal-Xil (the Inferno ranger, npc 7698) -- thrown obsidian spear every 4 ticks.
 * Special "Magma Piercer": a 1.25x shot that ignores 50% of the target's Ranged defence (65% at T3).
 */
public class JalXilProfile extends TieredCombatPetProfile {

	private static final int THROW_ANIM = 7605; // Jal-Xil ranged attack
	private static final Projectile SPEAR = new Projectile(1377, 30, 31, 30, 40, 8, 16, 0);

	private final Projectile spear;

	public JalXilProfile(PetTier tier) {
		super(tier, AttackStyle.RANGED, 4, 7, 5, 9, 13);
		spear = fx(SPEAR);
	}

	@Override
	public void basicAttack(PetNPC pet, Player owner, NPC target) {
		pet.animate(THROW_ANIM);
		hitWithProjectile(pet, target, spear, roll(owner, target));
	}

	@Override
	public void specialAttack(PetNPC pet, Player owner, NPC target) {
		pet.animate(THROW_ANIM);
		double ignored = tier == PetTier.T3 ? 0.65 : 0.5;
		hitWithProjectile(pet, target, spear, rollPiercing(owner, target, style, specialMax(1.25), 1D - ignored));
	}

	@Override
	public String specialName() {
		return "Magma Piercer";
	}

	@Override
	public String specialEffect() {
		return "1.25x shot, ignores " + (tier == PetTier.T3 ? "65%" : "50%") + " Ranged defence";
	}

}

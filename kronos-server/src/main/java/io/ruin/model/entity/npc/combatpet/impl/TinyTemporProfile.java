package io.ruin.model.entity.npc.combatpet.impl;

import io.ruin.model.combat.AttackStyle;
import io.ruin.model.combat.Hit;
import io.ruin.model.entity.npc.NPC;
import io.ruin.model.entity.npc.PetNPC;
import io.ruin.model.entity.npc.combatpet.CombatPetProfile;
import io.ruin.model.entity.player.Player;
import io.ruin.model.map.Projectile;

/**
 * Tiny Tempor -- sustain. Water magic every 3 ticks (max 4).
 * 15% "Tidal Splash": 20% of the damage dealt (min 1 on a hit) goes back to the owner, as Prayer when
 * their Prayer is proportionally lower than their Hitpoints, otherwise as Hitpoints.
 */
public class TinyTemporProfile extends CombatPetProfile {

	private static final int CAST_ANIM = 8900;  // Tempoross attack (skeleton 780)
	private static final Projectile WATER_PROJECTILE = new Projectile(162, 20, 31, 30, 56, 10, 16, 64); // water wave
	private static final int WATER_HIT_GFX = 163;
	private static final int SPLASH_HEAL_GFX = 163;

	private static final double HEAL_RATIO = 0.20;

	public TinyTemporProfile() {
		super(AttackStyle.MAGIC, 3, 4, 7, accuracy(75, 50), 0.15);
	}

	@Override
	public void basicAttack(PetNPC pet, Player owner, NPC target) {
		cast(pet, owner, target, roll(owner, target));
	}

	@Override
	public void specialAttack(PetNPC pet, Player owner, NPC target) {
		Hit hit = roll(owner, target);
		if (hit != null) {
			hit.postDamage(t -> {
				if (hit.damage > 0)
					sustain(owner, Math.max(1, (int) Math.round(hit.damage * HEAL_RATIO)), SPLASH_HEAL_GFX);
			});
		}
		cast(pet, owner, target, hit);
	}

	private void cast(PetNPC pet, Player owner, NPC target, Hit hit) {
		pet.animate(CAST_ANIM);
		int duration = hitWithProjectile(pet, target, WATER_PROJECTILE, hit);
		target.graphics(WATER_HIT_GFX, 124, duration);
	}

	@Override
	public String specialName() {
		return "Tidal Splash";
	}

	@Override
	public String specialEffect() {
		return "15%: 20% of damage heals HP or Prayer";
	}

}

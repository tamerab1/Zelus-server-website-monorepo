package io.ruin.model.entity.npc.combatpet.tiered;

import io.ruin.model.combat.AttackStyle;
import io.ruin.model.combat.Hit;
import io.ruin.model.entity.npc.NPC;
import io.ruin.model.entity.npc.PetNPC;
import io.ruin.model.entity.npc.combatpet.PetTier;
import io.ruin.model.entity.npc.combatpet.TieredCombatPetProfile;
import io.ruin.model.entity.player.Player;
import io.ruin.model.map.Projectile;

/**
 * Revenant maledictus -- spectral magic every 4 ticks.
 * Special "Soul Siphon": a 1.4x blood bolt; 25% of the damage (37.5% at T3) heals the owner's Hitpoints
 * or restores Prayer, whichever is proportionally lower.
 */
public class MaledictusProfile extends TieredCombatPetProfile {

	private static final int ATTACK_ANIM = 9282;   // Maledictus standard attack
	private static final int SIPHON_ANIM = 9279;   // Maledictus blood attack
	private static final Projectile SPECTRAL_PROJECTILE = new Projectile(2010, 20, 31, 20, 46, 8, 16, 64);
	private static final Projectile BLOOD_PROJECTILE = new Projectile(2002, 20, 31, 20, 46, 8, 16, 64);
	private static final int SIPHON_GFX = 2005;

	private final Projectile spectral, blood;

	public MaledictusProfile(PetTier tier) {
		super(tier, AttackStyle.MAGIC, 4, 7, 5, 9, 13);
		spectral = fx(SPECTRAL_PROJECTILE);
		blood = fx(BLOOD_PROJECTILE);
	}

	@Override
	public void basicAttack(PetNPC pet, Player owner, NPC target) {
		pet.animate(ATTACK_ANIM);
		hitWithProjectile(pet, target, spectral, roll(owner, target));
	}

	@Override
	public void specialAttack(PetNPC pet, Player owner, NPC target) {
		pet.animate(SIPHON_ANIM);
		Hit hit = roll(owner, target, style, specialMax(1.4));
		if (hit != null) {
			double ratio = scaled(0.25);
			hit.postDamage(t -> {
				if (hit.damage > 0)
					sustain(owner, Math.max(1, (int) Math.round(hit.damage * ratio)), -1);
			});
		}
		int duration = hitWithProjectile(pet, target, blood, hit);
		target.graphics(fx(SIPHON_GFX), 0, duration);
	}

	@Override
	public String specialName() {
		return "Soul Siphon";
	}

	@Override
	public String specialEffect() {
		return "1.4x blood bolt, heals you " + percent(0.25) + " of it";
	}

}

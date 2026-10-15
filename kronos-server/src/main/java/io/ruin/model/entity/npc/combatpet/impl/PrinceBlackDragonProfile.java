package io.ruin.model.entity.npc.combatpet.impl;

import io.ruin.api.utils.Random;
import io.ruin.model.combat.AttackStyle;
import io.ruin.model.combat.Hit;
import io.ruin.model.entity.npc.NPC;
import io.ruin.model.entity.npc.PetNPC;
import io.ruin.model.entity.npc.combatpet.CombatPetProfile;
import io.ruin.model.entity.player.Player;
import io.ruin.model.map.Projectile;

/**
 * Prince Black Dragon -- tri-blast. Dragonfire breath every 4 ticks (max 7).
 * 8% "Triple Breath": fire, ice and poison breaths in quick succession, each rolled separately;
 * the ice breath has a small freeze chance and the poison breath a small poison chance.
 */
public class PrinceBlackDragonProfile extends CombatPetProfile {

	private static final int BREATH_ANIM = 81; // KBD dragonfire (skeleton 1080)

	// KBD breath gfx: 393 fire, 394 poison, 396 freeze
	private static final Projectile FIRE = new Projectile(393, 25, 31, 30, 56, 10, 15, 64);
	private static final Projectile ICE = new Projectile(396, 25, 31, 40, 66, 10, 15, 64);
	private static final Projectile POISON = new Projectile(394, 25, 31, 50, 76, 10, 15, 64);

	private static final double FREEZE_CHANCE = 0.20;
	private static final int FREEZE_SECONDS = 3;
	private static final double POISON_CHANCE = 0.20;
	private static final int POISON_DAMAGE = 4;

	public PrinceBlackDragonProfile() {
		super(AttackStyle.DRAGONFIRE, 4, 7, 7, accuracy(70, 40), 0.08);
	}

	@Override
	public void basicAttack(PetNPC pet, Player owner, NPC target) {
		pet.animate(BREATH_ANIM);
		hitWithProjectile(pet, target, FIRE, roll(owner, target));
	}

	@Override
	public void specialAttack(PetNPC pet, Player owner, NPC target) {
		pet.animate(BREATH_ANIM);
		hitWithProjectile(pet, target, FIRE, roll(owner, target));

		Hit ice = roll(owner, target);
		if (ice != null) {
			ice.postDamage(t -> {
				if (ice.damage > 0 && Random.get() < FREEZE_CHANCE && !t.isFrozen() && !t.hasFreezeImmunity())
					t.freeze(FREEZE_SECONDS, owner);
			});
		}
		hitWithProjectile(pet, target, ICE, ice);

		Hit poison = roll(owner, target);
		if (poison != null) {
			poison.postDamage(t -> {
				if (poison.damage > 0 && Random.get() < POISON_CHANCE)
					t.poison(POISON_DAMAGE);
			});
		}
		hitWithProjectile(pet, target, POISON, poison);
	}

	@Override
	public String specialName() {
		return "Triple Breath";
	}

	@Override
	public String specialEffect() {
		return "8%: fire, ice and poison breaths";
	}

}

package io.ruin.model.entity.npc.combatpet.impl;

import io.ruin.api.utils.Random;
import io.ruin.model.World;
import io.ruin.model.combat.AttackStyle;
import io.ruin.model.entity.npc.NPC;
import io.ruin.model.entity.npc.PetNPC;
import io.ruin.model.entity.npc.combatpet.CombatPetProfile;
import io.ruin.model.entity.player.Player;
import io.ruin.model.map.Projectile;
import io.ruin.model.stat.StatType;

/**
 * TzRek-Jad -- DoT/debuffer. Randomly magic or ranged every 4 ticks (max 8), like its big brother.
 * 10% "Burn": a magic attack that also burns the target for 2 damage every 3 seconds for 15 seconds and
 * lowers its Attack by 10% for the same duration (neither stacks).
 */
public class TzRekJadProfile extends CombatPetProfile {

	private static final int MAGIC_ANIM = 2656;   // Jad magic (skeleton 159)
	private static final int RANGED_ANIM = 2652;  // Jad ranged
	private static final Projectile MAGIC_PROJECTILE = new Projectile(448, 40, 31, 30, 56, 10, 16, 64);
	private static final int MAGIC_HIT_GFX = 157;
	private static final int RANGED_FALL_GFX = 451; // Jad's falling-rock ranged gfx
	private static final int BURN_GFX = 101;        // fire strike impact, small fire burst

	private static final int BURN_DAMAGE = 2;
	private static final int BURN_INTERVAL_TICKS = 5; // 3 seconds
	private static final int BURN_PULSES = 5;         // 15 seconds
	private static final String BURN_KEY = "combat_pet_burn";
	private static final String BURN_ATTACK_KEY = "combat_pet_burn_atk";

	public TzRekJadProfile() {
		super(AttackStyle.MAGIC, 4, 8, 7, accuracy(80, 60), 0.10);
	}

	@Override
	public void basicAttack(PetNPC pet, Player owner, NPC target) {
		if (Random.get(1) == 0)
			magic(pet, owner, target);
		else
			ranged(pet, owner, target);
	}

	@Override
	public void specialAttack(PetNPC pet, Player owner, NPC target) {
		int duration = magic(pet, owner, target);
		target.graphics(BURN_GFX, 96, duration);
		damageOverTime(owner, target, AttackStyle.MAGIC, BURN_DAMAGE, BURN_INTERVAL_TICKS, BURN_PULSES, BURN_KEY);
		timedDrain(target, StatType.Attack, 0.10, BURN_INTERVAL_TICKS * BURN_PULSES, BURN_ATTACK_KEY);
	}

	private int magic(PetNPC pet, Player owner, NPC target) {
		pet.animate(MAGIC_ANIM);
		int duration = hitWithProjectile(pet, target, MAGIC_PROJECTILE, roll(owner, target, AttackStyle.MAGIC, maxHit));
		target.graphics(MAGIC_HIT_GFX, 124, duration);
		return duration;
	}

	private void ranged(PetNPC pet, Player owner, NPC target) {
		pet.animate(RANGED_ANIM);
		World.sendGraphics(RANGED_FALL_GFX, 0, 0, target.getPosition());
		var hit = roll(owner, target, AttackStyle.RANGED, maxHit);
		if (hit != null)
			hitNow(target, hit.delay(2));
	}

	@Override
	public String specialName() {
		return "Burn";
	}

	@Override
	public String specialEffect() {
		return "10%: burns 2 per 3s for 15s, Attack -10%";
	}

}

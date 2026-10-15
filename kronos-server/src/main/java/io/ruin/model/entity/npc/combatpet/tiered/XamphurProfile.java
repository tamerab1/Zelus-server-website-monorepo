package io.ruin.model.entity.npc.combatpet.tiered;

import io.ruin.api.utils.Random;
import io.ruin.model.World;
import io.ruin.model.combat.AttackStyle;
import io.ruin.model.entity.npc.NPC;
import io.ruin.model.entity.npc.PetNPC;
import io.ruin.model.entity.npc.combatpet.PetTier;
import io.ruin.model.entity.npc.combatpet.TieredCombatPetProfile;
import io.ruin.model.entity.player.Player;
import io.ruin.model.map.Projectile;

/**
 * Xamphur -- uses the boss's own attacks: mostly its magic orb (anim 9068, projectile 1907 / hit 1908),
 * sometimes its claw swipe (anim 9064, gfx 1914), every 4 ticks.
 * Special "Astral Cataclysm": the summoning gesture (9066) calls an astral strike down on the target for
 * guaranteed 60-100% of max damage, and freezes it for 2 seconds (3 at T3).
 */
public class XamphurProfile extends TieredCombatPetProfile {

	private static final int MAGE_ANIM = 9068;
	private static final int CLAW_ANIM = 9064;
	private static final int SUMMON_ANIM = 9066;
	private static final Projectile ORB = new Projectile(1907, 30, 31, 25, 56, 10, 15, 220);
	private static final int ORB_HIT_GFX = 1908;
	private static final int CLAW_GFX = 1914;
	private static final int ASTRAL_STRIKE_GFX = 1919;

	private final Projectile orb;

	public XamphurProfile(PetTier tier) {
		super(tier, AttackStyle.MAGIC, 4, 7, 6, 10, 15);
		orb = fx(ORB);
	}

	@Override
	public void basicAttack(PetNPC pet, Player owner, NPC target) {
		if (Random.get(3) == 0) {
			pet.animate(CLAW_ANIM);
			target.graphics(fx(CLAW_GFX), 0, 0);
			hitNow(target, roll(owner, target));
			return;
		}
		pet.animate(MAGE_ANIM);
		int duration = hitWithProjectile(pet, target, orb, roll(owner, target));
		target.graphics(fx(ORB_HIT_GFX), 100, duration);
	}

	@Override
	public void specialAttack(PetNPC pet, Player owner, NPC target) {
		pet.animate(SUMMON_ANIM);
		World.sendGraphics(fx(ASTRAL_STRIKE_GFX), 0, 0, target.getPosition());
		target.graphics(fx(CLAW_GFX), 0, 30);
		int freezeSeconds = (int) Math.round(scaled(2));
		var hit = guaranteed(owner, target, style, (int) Math.round(maxHit * 0.6), maxHit).delay(1);
		hit.postDamage(t -> {
			if (!t.isFrozen() && !t.hasFreezeImmunity())
				t.freeze(freezeSeconds, owner);
		});
		hitNow(target, hit);
	}

	@Override
	public String specialName() {
		return "Astral Cataclysm";
	}

	@Override
	public String specialEffect() {
		return "60-100% max hit, never misses, " + Math.round(scaled(2)) + "s freeze";
	}

}

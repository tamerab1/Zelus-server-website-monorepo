package io.ruin.model.entity.npc.combatpet.tiered;

import io.ruin.model.combat.AttackStyle;
import io.ruin.model.entity.npc.NPC;
import io.ruin.model.entity.npc.PetNPC;
import io.ruin.model.entity.npc.combatpet.PetTier;
import io.ruin.model.entity.npc.combatpet.TieredCombatPetProfile;
import io.ruin.model.entity.player.Player;
import io.ruin.model.map.Projectile;
import io.ruin.model.stat.StatType;

/**
 * A Doubt -- shadow magic every 4 ticks.
 * Special "Mind Shatter": a psychic burst for 1.4x damage that lowers the target's Magic by 5% (7.5% at T3)
 * for 6 seconds. (NPCs have no prayer points, so only Magic is drained.)
 */
public class DoubtProfile extends TieredCombatPetProfile {

	// Skeleton 1443 only has 6310-6315 (stand 6311, walk 6312, death 6315). 6310/6313 are the likely
	// attacks -- check in-game with ::petanim and swap if needed.
	private static final int ATTACK_ANIM = 6310;
	private static final int SPECIAL_ANIM = 6313;

	private static final Projectile SHADOW_PROJECTILE = new Projectile(380, 30, 31, 30, 56, 10, 16, 64); // shadow blitz
	private static final int SHADOW_HIT_GFX = 381;
	private static final int MIND_SHATTER_GFX = 383; // shadow barrage burst

	private static final int DRAIN_TICKS = 10;
	private static final String DRAIN_KEY = "combat_pet_mind_shatter";

	private final Projectile shadow;

	public DoubtProfile(PetTier tier) {
		super(tier, AttackStyle.MAGIC, 4, 7, 5, 8, 13);
		shadow = fx(SHADOW_PROJECTILE);
	}

	@Override
	public void basicAttack(PetNPC pet, Player owner, NPC target) {
		pet.animate(ATTACK_ANIM);
		int duration = hitWithProjectile(pet, target, shadow, roll(owner, target));
		target.graphics(fx(SHADOW_HIT_GFX), 0, duration);
	}

	@Override
	public void specialAttack(PetNPC pet, Player owner, NPC target) {
		pet.animate(SPECIAL_ANIM);
		int duration = hitWithProjectile(pet, target, shadow, roll(owner, target, style, specialMax(1.4)));
		target.graphics(fx(MIND_SHATTER_GFX), 0, duration);
		timedDrain(target, StatType.Magic, scaled(0.05), DRAIN_TICKS, DRAIN_KEY);
	}

	@Override
	public String specialName() {
		return "Mind Shatter";
	}

	@Override
	public String specialEffect() {
		return "1.4x psychic burst, Magic -" + percent(0.05) + " (6s)";
	}

}

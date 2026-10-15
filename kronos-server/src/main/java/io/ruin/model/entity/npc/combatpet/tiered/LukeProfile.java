package io.ruin.model.entity.npc.combatpet.tiered;

import io.ruin.model.World;
import io.ruin.model.combat.AttackStyle;
import io.ruin.model.entity.npc.NPC;
import io.ruin.model.entity.npc.PetNPC;
import io.ruin.model.entity.npc.combatpet.PetTier;
import io.ruin.model.entity.npc.combatpet.TieredCombatPetProfile;
import io.ruin.model.entity.player.Player;
import io.ruin.model.stat.StatType;

/**
 * 50% Luke -- rapid melee swipe every 4 ticks.
 * Special "Brawler's Flurry": a heavy 1.5x hit that lowers the target's Defence by 10% (15% at T3) for 6 seconds.
 */
public class LukeProfile extends TieredCombatPetProfile {

	// Skeleton 1304 has no combat data on this server; 5641/5643 are the short one-shot candidates --
	// check in-game with ::petanim and swap if needed.
	private static final int ATTACK_ANIM = 5641;
	private static final int SPECIAL_ANIM = 5643;

	private static final int IMPACT_GFX = 1028; // tremor, tier-tinted

	private static final int DEBUFF_TICKS = 10;
	private static final String DEBUFF_KEY = "combat_pet_flurry_def";

	public LukeProfile(PetTier tier) {
		super(tier, AttackStyle.SLASH, 4, 1, 6, 9, 14);
	}

	@Override
	public void basicAttack(PetNPC pet, Player owner, NPC target) {
		pet.animate(ATTACK_ANIM);
		hitNow(target, roll(owner, target));
	}

	@Override
	public void specialAttack(PetNPC pet, Player owner, NPC target) {
		pet.animate(SPECIAL_ANIM);
		World.sendGraphics(fx(IMPACT_GFX), 0, 0, target.getPosition());
		hitNow(target, roll(owner, target, style, specialMax(1.5)));
		if (!target.getCombat().isDead())
			timedDrain(target, StatType.Defence, scaled(0.10), DEBUFF_TICKS, DEBUFF_KEY);
	}

	@Override
	public String specialName() {
		return "Brawler's Flurry";
	}

	@Override
	public String specialEffect() {
		return "1.5x heavy hit, Defence -" + percent(0.10) + " (6s)";
	}

}

package io.ruin.model.entity.npc.combatpet.tiered;

import io.ruin.model.World;
import io.ruin.model.combat.AttackStyle;
import io.ruin.model.entity.npc.NPC;
import io.ruin.model.entity.npc.PetNPC;
import io.ruin.model.entity.npc.combatpet.PetTier;
import io.ruin.model.entity.npc.combatpet.TieredCombatPetProfile;
import io.ruin.model.entity.player.Player;

/**
 * Reanimated TzHaar -- obsidian club smash every 4 ticks.
 * Special "Volcanic Impact": a 1.25x lava smash on the target plus a tremor hit on up to 3 NPCs touching it
 * (5 at T3, multi-combat only).
 */
public class ReanimatedTzHaarProfile extends TieredCombatPetProfile {

	private static final int ATTACK_ANIM = 2610;    // Reanimated TzHaar attack (combat json)
	private static final int TREMOR_GFX = 1028;     // Tarn earthquake

	public ReanimatedTzHaarProfile(PetTier tier) {
		super(tier, AttackStyle.CRUSH, 4, 1, 6, 9, 14);
	}

	@Override
	public void basicAttack(PetNPC pet, Player owner, NPC target) {
		pet.animate(ATTACK_ANIM);
		hitNow(target, roll(owner, target));
	}

	@Override
	public void specialAttack(PetNPC pet, Player owner, NPC target) {
		pet.animate(ATTACK_ANIM);
		World.sendGraphics(fx(TREMOR_GFX), 0, 0, target.getPosition());
		hitNow(target, roll(owner, target, style, specialMax(1.25)));
		int maxTargets = (int) Math.round(scaled(3));
		splashAdjacent(owner, target, maxTargets, npc -> {
			World.sendGraphics(fx(TREMOR_GFX), 0, 0, npc.getPosition());
			hitNow(npc, roll(owner, npc));
		});
	}

	@Override
	public String specialName() {
		return "Volcanic Impact";
	}

	@Override
	public String specialEffect() {
		return "1.25x smash + tremor on " + Math.round(scaled(3)) + " nearby (multi)";
	}

}

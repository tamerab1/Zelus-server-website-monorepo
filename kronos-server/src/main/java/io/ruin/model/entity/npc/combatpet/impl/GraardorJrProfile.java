package io.ruin.model.entity.npc.combatpet.impl;

import io.ruin.model.World;
import io.ruin.model.combat.AttackStyle;
import io.ruin.model.entity.npc.NPC;
import io.ruin.model.entity.npc.PetNPC;
import io.ruin.model.entity.npc.combatpet.CombatPetProfile;
import io.ruin.model.entity.player.Player;
import io.ruin.model.map.Position;
import io.ruin.model.stat.StatType;

/**
 * General Graardor Jr. -- tank/AoE. Melee crush every 4 ticks (max 6).
 * 10% "Ground Slam": hits the target plus up to 4 NPCs adjacent to it (multi-combat only) and lowers
 * each one's Defence by 10% for 5 seconds.
 */
public class GraardorJrProfile extends CombatPetProfile {

	private static final int MELEE_ANIM = 7018;   // Graardor melee (skeleton 1003)
	private static final int SLAM_ANIM = 7021;    // Graardor ground-slam ranged attack

	// Sunlight spear shield-slam dust ring (N, S, E, W, SW, SE, NW, NE)
	private static final int[] DUST_GFX = { 3082, 3080, 3083, 3081, 3085, 3084, 3086, 3087 };
	private static final int[][] DUST_OFFSETS = { { 0, 1 }, { 0, -1 }, { 1, 0 }, { -1, 0 }, { -1, -1 }, { 1, -1 }, { -1, 1 }, { 1, 1 } };

	private static final int MAX_AOE_TARGETS = 4;
	private static final int DEBUFF_TICKS = 8; // ~5 seconds
	private static final String DEBUFF_KEY = "combat_pet_slam_def";

	public GraardorJrProfile() {
		super(AttackStyle.CRUSH, 4, 6, 1, accuracy(80, 60), 0.10);
	}

	@Override
	public void basicAttack(PetNPC pet, Player owner, NPC target) {
		pet.animate(MELEE_ANIM);
		hitNow(target, roll(owner, target));
	}

	@Override
	public void specialAttack(PetNPC pet, Player owner, NPC target) {
		pet.animate(SLAM_ANIM);
		dustRing(target);
		slam(owner, target);

		splashAdjacent(owner, target, MAX_AOE_TARGETS, npc -> slam(owner, npc));
	}

	private void slam(Player owner, NPC npc) {
		hitNow(npc, roll(owner, npc));
		if (!npc.getCombat().isDead())
			timedDrain(npc, StatType.Defence, 0.10, DEBUFF_TICKS, DEBUFF_KEY);
	}

	private static void dustRing(NPC target) {
		int cx = target.getAbsX() + target.getSize() / 2;
		int cy = target.getAbsY() + target.getSize() / 2;
		int reach = Math.max(1, (target.getSize() + 1) / 2);
		for (int i = 0; i < DUST_GFX.length; i++) {
			World.sendGraphics(DUST_GFX[i], 0, 0,
					Position.of(cx + DUST_OFFSETS[i][0] * reach, cy + DUST_OFFSETS[i][1] * reach, target.getHeight()));
		}
	}

	@Override
	public String specialName() {
		return "Ground Slam";
	}

	@Override
	public String specialEffect() {
		return "10%: AoE slam, Defence -10% for 5s";
	}

}

package io.ruin.model.activities.bosses.solarmane;

import io.ruin.model.entity.npc.NPCCombat;

/**
 * The Solarmane cub healers (npc 30613, 2026-11-04): called by {@link SolarmaneCombat} at 40% hitpoints. They never
 * fight back or chase anyone; the event in {@link SolarmaneCombat} walks them to the lion and heals it until they die.
 */
public class SolarmaneCubCombat extends NPCCombat {

	@Override
	public void init() {
	}

	@Override
	public void process() {
	}

	@Override
	public void follow() {
	}

	@Override
	public boolean attack() {
		return false;
	}
}

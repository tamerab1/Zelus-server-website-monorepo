package io.ruin.model.entity.npc;

/**
 * Combat handler for attackable scenery-like NPCs (boss adds driven entirely by their boss's script): they can be hit
 * but never move, retaliate or attack on their own.
 */
public class InertCombat extends NPCCombat {

	@Override
	public void init() {
	}

	@Override
	public void follow() {
	}

	@Override
	public boolean attack() {
		return false;
	}

	@Override
	public void process() {
	}
}

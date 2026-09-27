package io.ruin.model.activities.minigame;

import io.ruin.model.entity.Entity;
import io.ruin.model.entity.npc.NPCCombat;

// Aggressive minion spawned by DracoRock.java while a player mines the rock -- basic melee mob,
// same minimal shape as AbyssalDemon.java (io.ruin.model.activities.miscpvm.slayer).
public class DracoWhelp extends NPCCombat {

	@Override
	public void init() {
	}

	@Override
	public void follow() {
		follow(1);
	}

	@Override
	public boolean attack() {
		if (!withinDistance(1))
			return false;
		basicAttack();
		return true;
	}

	@Override
	public void process() {
	}

	// Every whelp is spawned for (and locked onto) one specific miner -- let them all pile on
	// even outside multi-combat, instead of the single-combat rule blocking whelp #2/#3.
	@Override
	public boolean multiCheck(Entity target) {
		return true;
	}
}

package io.ruin.model.activities.bosses.globalboss;

import io.ruin.model.entity.npc.NPC;
import io.ruin.model.map.Position;

public enum GlobalBosses {
	// YOWLING_YETI(new NPC(853), new Position(2641, 3612, 0)),
	// all at the event boss cavern (EventBossArea, 2026-10-20; was 2912, 3614)
	STONE_POLTEGEIST(new NPC(11896), io.ruin.model.activities.bosses.EventBossArea.spawnFor(11896)),
	BLOOD_REAPER(new NPC(11895), io.ruin.model.activities.bosses.EventBossArea.spawnFor(11895)),
	// 2026-10-19: Nidhogg the Abyssal Serpent (GlobalNidhogg), rooted in the middle of the arena
	NIDHOGG(new NPC(16215), io.ruin.model.activities.bosses.EventBossArea.spawnFor(16215));

	NPC boss;
	Position spawnPosition;

	GlobalBosses(NPC boss, Position spawnPosition) {
		this.boss = boss;
		this.spawnPosition = spawnPosition;
	}
}

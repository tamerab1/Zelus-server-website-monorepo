package io.ruin.model.entity;

import io.ruin.model.entity.player.Player;
import io.ruin.model.var.VarPlayerRepository;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Keeps OSRS's boss health bar (interface 303, see {@link HealthHud}) in sync for everyone fighting a boss: opens it
 * for players who arrive, switches it when the boss changes form (another npc id / max, e.g. Zalcano's armour and her
 * real HP), updates the value every call and closes it for players who leave.
 */
public class BossHudTracker {

	private final Set<Player> shown = new HashSet<>();

	/** Call every tick (or on every change) with the players currently in the fight. */
	public void update(Collection<Player> present, int npcId, int current, int max) {
		shown.removeIf(p -> {
			if (present.contains(p) && p.isOnline())
				return false;
			if (p.isOnline() && p.getHealthHud().isOpened())
				p.getHealthHud().close();
			return true;
		});
		for (Player p : present) {
			HealthHud hud = p.getHealthHud();
			if (!hud.isOpened() || VarPlayerRepository.HEALTH_HUD_NPC.get(p) != npcId) {
				hud.open(true, npcId, max);
				shown.add(p);
			}
			hud.updateMaxValue(max);
			hud.updateValue(Math.max(0, Math.min(current, max)));
		}
	}

	/** The fight is over: close the bar for everyone it was shown to. */
	public void closeAll() {
		for (Player p : shown)
			if (p.isOnline() && p.getHealthHud().isOpened())
				p.getHealthHud().close();
		shown.clear();
	}
}

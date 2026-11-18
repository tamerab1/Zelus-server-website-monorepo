package io.ruin.model.activities.fiveseals;

import io.ruin.model.World;
import io.ruin.model.activities.miscpvm.BasicCombat;
import io.ruin.model.combat.Hit;
import io.ruin.model.entity.npc.NPC;
import io.ruin.model.entity.player.Player;
import io.ruin.model.entity.shared.listeners.HitListener;
import io.ruin.model.map.Position;

/**
 * Combat for every Five Seals creature: the npc's own stats and attack (BasicCombat), but its death is the run's
 * business. No drop table, no pet, no kill count or Slayer credit (the run pays out at the altars), and it never
 * respawns: the run's onDeath callback counts the kill, then the npc is removed.
 */
public class SealCombat extends BasicCombat {

	/** called once, when the npc dies (after its death animation) */
	Runnable onDeath;

	/** Spawns {@code npcId} in a run's instance and swaps its combat for this class (or a subclass). */
	static <C extends SealCombat> NPC spawn(FiveSealsRun run, int npcId, Position pos, C combat, Runnable onDeath) {
		NPC npc = new NPC(npcId).spawn(pos.getX(), pos.getY(), pos.getZ(), 3);
		combat.init(npc, npc.getDef().combatInfo);
		combat.onDeath = onDeath;
		npc.setCombat(combat);
		// the Seal of Awakening's reward: +10% accuracy against every creature of the run
		if (npc.hitListener == null)
			npc.hitListener = new HitListener();
		npc.hitListener.preDefend(hit -> {
			if (run.buff && hit.attacker != null && hit.attacker.player != null)
				hit.boostAttack(0.10);
		});
		run.track(npc);
		return npc;
	}

	static NPC spawn(FiveSealsRun run, int npcId, Position pos, Runnable onDeath) {
		return spawn(run, npcId, pos, new SealCombat(), onDeath);
	}

	/** aggro on a player of the run */
	void hunt(Player p) {
		if (p == null || npc.isRemoved())
			return;
		npc.targetPlayer(p, false);
		setTarget(p);
	}

	@Override
	public void handleNewDrop(Player player, int npcID, Position dropPosition) {
		// the run pays out at the altars
	}

	@Override
	public void startDeath(Hit killHit) {
		if (isDead())
			return;
		setDead(true);
		if (target != null)
			reset();
		if (info != null && info.death_animation > 0)
			npc.animate(info.death_animation);
		int ticks = info == null ? 2 : Math.max(1, info.death_ticks);
		World.startEvent(e -> {
			e.delay(ticks);
			try {
				if (onDeath != null)
					onDeath.run();
			} finally {
				if (!npc.isRemoved())
					npc.remove();
			}
		});
	}
}

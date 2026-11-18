package io.ruin.model.activities.fiveseals;

import io.ruin.cache.Color;
import io.ruin.model.World;
import io.ruin.model.entity.player.Player;

/**
 * Seal 4's shadow stalkers hunt rather than fight. Next to its target a stalker LUNGES: a 2-tick telegraph, then, if it is
 * still beside them and wasn't staggered, it "catches" them (FiveSealsRun.caught: 20% of max hitpoints; solo players are
 * thrown back down the purple walkway, group members snared until a teammate pulls them free). Any damaging hit staggers a
 * stalker for 4 ticks and cancels its lunge, so fighting one is safe; standing still to channel a node is the danger.
 * A stalker rests 6 ticks after a lunge. (Round 2, user 2026-11-16: it used to catch on mere adjacency, so every melee
 * swing at it threw the player back to the walkway.)
 */
public class StalkerCombat extends SealCombat {

	private static final int LUNGE_TICKS = 2, STAGGER_TICKS = 4;

	private final FiveSealsRun run;
	private long staggeredUntil;

	StalkerCombat(FiveSealsRun run) {
		this.run = run;
	}

	/** a damaging hit landed on the stalker */
	void stagger() {
		staggeredUntil = io.ruin.Server.currentTick() + STAGGER_TICKS;
	}

	private boolean staggered() {
		return io.ruin.Server.currentTick() < staggeredUntil;
	}

	private boolean beside(Player p) {
		return p.getHeight() == npc.getHeight()
			&& Math.max(Math.abs(p.getAbsX() - npc.getAbsX()), Math.abs(p.getAbsY() - npc.getAbsY())) <= 1;
	}

	@Override
	public boolean attack() {
		if (target == null || target.player == null || !withinDistance(1) || staggered())
			return false;
		Player p = target.player;
		if (info != null && info.attack_animation > 0)
			npc.animate(info.attack_animation);
		p.sendFilteredMessage(Color.RED.wrap("A shadow stalker lunges at you - strike it or step away!"));
		World.startEvent(e -> {
			e.delay(LUNGE_TICKS);
			if (npc.isRemoved() || isDead() || staggered() || !p.isOnline() || !beside(p))
				return;
			run.caught(p);
		});
		return true;
	}

	@Override
	protected void afterAttack() {
		updateLastAttack(6);
	}
}

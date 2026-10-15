package io.ruin.model.activities.bosses.variants;

import io.ruin.api.utils.Random;
import io.ruin.model.World;
import io.ruin.model.combat.AttackStyle;
import io.ruin.model.combat.Hit;
import io.ruin.model.entity.Entity;
import io.ruin.model.entity.npc.NPCCombat;
import io.ruin.model.entity.player.Player;
import io.ruin.model.map.Graphic;
import io.ruin.model.map.Position;
import io.ruin.model.map.Projectile;
import io.ruin.utility.Misc;
import io.ruin.utility.TickDelay;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared base for the boss variants (2026-10-13): recoloured + textured clones of existing monsters, each with its
 * own attack style and special attack. Cache side: .dev/cache-restore-tool/boss_variants.txt (npcs 30594-30600,
 * spotanims 4275-4290 -- the GFX_* constants in the subclasses must match it).
 * <p>
 * Every {@link #specialEvery()} attacks the boss uses its special instead of a normal attack, and in a group fight
 * it periodically switches to another player who is attacking it, so one player doesn't tank the whole fight.
 */
public abstract class BossVariant extends NPCCombat {

	private static final int RETARGET_MIN_TICKS = 6, RETARGET_MAX_TICKS = 10;

	private final TickDelay retargetTimer = new TickDelay();
	private int attacks;

	/** Normal attack; return false when out of range (nothing happened). */
	protected abstract boolean normalAttack();

	/** The special attack; return false when it can't be used right now (falls back to a normal attack). */
	protected abstract boolean specialAttack();

	/** A special every this many attacks. */
	protected abstract int specialEvery();

	/** The furthest distance this boss attacks from (1 = melee). */
	protected abstract int attackRange();

	@Override
	public void init() {
	}

	@Override
	public void process() {
	}

	/**
	 * Safety net: the generic death routine removes the npc once the death animation has played, but these death
	 * animations hold their last (lying) frame for minutes, so a body that is never removed stays on the ground.
	 * Make sure it goes, and log if the normal path didn't do it.
	 */
	@Override
	public void startDeath(Hit killHit) {
		super.startDeath(killHit);
		final int wait = Math.max(1, info.death_ticks) + 3;
		World.startEvent(e -> {
			e.delay(wait);
			if (!npc.isRemoved()) {
				org.slf4j.LoggerFactory.getLogger(BossVariant.class)
						.warn("Boss variant {} was not removed {} ticks after death -- removing it", npc.getId(), wait);
				npc.remove();
			}
		});
	}

	@Override
	public void follow() {
		follow(attackRange());
	}

	@Override
	public boolean attack() {
		if (target == null)
			return false;
		if (!retargetTimer.isDelayed())
			rotateTarget();
		if (++attacks % specialEvery() == 0 && specialAttack())
			return true;
		return normalAttack();
	}

	/* ---------------- helpers ---------------- */

	/** Explicit adjacency (withinDistance(1) is not one with custom stepping -- see the Perker fix). */
	protected boolean inMeleeRange() {
		if (target == null)
			return false;
		Position closest = Misc.getClosestPosition(npc, target);
		int dx = Math.abs(closest.getX() - target.getAbsX());
		int dy = Math.abs(closest.getY() - target.getAbsY());
		return Math.max(dx, dy) == 1 && npc.getHeight() == target.getHeight();
	}

	/** Distance from the closest tile of the boss to the target. */
	protected int distanceTo(Entity e) {
		Position closest = Misc.getClosestPosition(npc, e);
		return Misc.getDistance(closest, e.getPosition());
	}

	protected static Projectile projectile(int gfx) {
		return new Projectile(gfx, 43, 31, 40, 50, 6, 16, 64);
	}

	protected static Graphic gfx(int id) {
		return Graphic.builder().id(id).height(0).delay(0).soundId(-1).build();
	}

	protected Hit cast(int projectileGfx, int impactGfx, int anim, AttackStyle style, int maxDamage) {
		return projectileAttack(projectile(projectileGfx), anim, gfx(impactGfx), style, maxDamage);
	}

	/** Players near the boss who can be hit (for the multi-target attacks). */
	protected List<Player> playersWithin(int radius) {
		List<Player> list = new ArrayList<>();
		for (Player p : npc.localPlayers()) {
			if (p.getHeight() == npc.getHeight() && distanceTo(p) <= radius && canAttack(p))
				list.add(p);
		}
		return list;
	}

	protected void announce(String message) {
		for (Player p : npc.localPlayers())
			p.sendMessage(message);
	}

	private void rotateTarget() {
		retargetTimer.delay(Random.get(RETARGET_MIN_TICKS, RETARGET_MAX_TICKS));
		List<Player> attackers = new ArrayList<>();
		for (Player p : npc.localPlayers()) {
			if (p.getCombat().getTarget() == npc && canAttack(p))
				attackers.add(p);
		}
		if (attackers.size() > 1) {
			Player next = attackers.get(Random.get(attackers.size() - 1));
			if (next != target) {
				setTarget(next);
				faceTarget();
			}
		}
	}
}

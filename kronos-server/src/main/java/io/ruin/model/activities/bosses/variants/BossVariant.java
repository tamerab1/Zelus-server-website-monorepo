package io.ruin.model.activities.bosses.variants;

import io.ruin.api.utils.Random;
import io.ruin.model.World;
import io.ruin.model.combat.AttackStyle;
import io.ruin.model.combat.Hit;
import io.ruin.model.entity.Entity;
import io.ruin.model.entity.npc.NPC;
import io.ruin.model.entity.npc.NPCCombat;
import io.ruin.model.entity.player.Player;
import io.ruin.model.entity.shared.listeners.HitListener;
import io.ruin.model.map.Graphic;
import io.ruin.model.map.Position;
import io.ruin.model.map.Projectile;
import io.ruin.utility.Misc;
import io.ruin.utility.TickDelay;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Shared base for the boss variants (2026-10-13): recoloured + textured clones of existing monsters, each with its
 * own attack style and special attack. Cache side: .dev/cache-restore-tool/boss_variants.txt (npcs 30594-30600,
 * spotanims 4275-4290 -- the GFX_* constants in the subclasses must match it).
 * <p>
 * Every {@link #specialEvery()} attacks the boss uses its special instead of a normal attack, and in a group fight
 * it periodically switches to another player who is attacking it, so one player doesn't tank the whole fight.
 * <p>
 * A boss with a {@link #protectionPrayer()} keeps that overhead prayer up for the whole fight and takes no
 * damage from the matching style (same mechanism as Cindermaw / Kree'arra: a preDamage block).
 * <p>
 * A boss with a {@link #reinforcementId()} calls that guardian in once, when it first drops below half health
 * (Sanctum main bosses). The guardian never respawns and goes when the boss dies.
 */
public abstract class BossVariant extends NPCCombat {

	private static final int RETARGET_MIN_TICKS = 6, RETARGET_MAX_TICKS = 10;

	private final TickDelay retargetTimer = new TickDelay();
	private int attacks;
	private boolean prayerShown;
	private NPC reinforcement;
	private boolean reinforced;
	/** Sanctum: the boss stays within leashRadius of leashCentre and only fights targets the engage check accepts. */
	private Position leashCentre;
	private int leashRadius;
	private Predicate<Entity> engageCheck;

	/** Normal attack; return false when out of range (nothing happened). */
	protected abstract boolean normalAttack();

	/** The special attack; return false when it can't be used right now (falls back to a normal attack). */
	protected abstract boolean specialAttack();

	/** A special every this many attacks. */
	protected abstract int specialEvery();

	/** The furthest distance this boss attacks from (1 = melee). */
	protected abstract int attackRange();

	/** Overhead protection prayer kept up all fight (fully blocks that style), or null for none. */
	protected NPC.DefaultHeadIconIndex protectionPrayer() {
		return null;
	}

	/**
	 * Keeps the boss around {@code centre} (it won't chase further than {@code radius} tiles from it) and makes it drop
	 * any target {@code engage} rejects -- e.g. a player who left its room, so it can't keep hitting through the door.
	 */
	public void leash(Position centre, int radius, Predicate<Entity> engage) {
		this.leashCentre = centre;
		this.leashRadius = radius;
		this.engageCheck = engage;
	}

	private boolean mayEngage() {
		if (engageCheck == null || target == null || engageCheck.test(target))
			return true;
		reset();
		return false;
	}

	/** Guardian npc called in below half health, or -1 for none. */
	protected int reinforcementId() {
		return -1;
	}

	@Override
	public void init() {
		NPC.DefaultHeadIconIndex prayer = protectionPrayer();
		if (prayer == null && reinforcementId() == -1)
			return;
		npc.hitListener = new HitListener()
				.preDamage(hit -> {
					if (prayer != null && blocks(prayer, hit.attackStyle))
						hit.block();
				})
				.postDamage(hit -> {
					if (!reinforced && reinforcementId() != -1 && npc.getHp() > 0 && npc.getHp() * 2 < npc.getMaxHp())
						reinforce();
				});
	}

	private void reinforce() {
		reinforced = true;
		Position at = npc.getPosition();
		NPC guardian = new NPC(reinforcementId()).spawn(at.getX(), at.getY(), at.getZ(), 4);
		guardian.getCombat().setAllowRespawn(false);
		reinforcement = guardian;
		// instance npcs are torn down with their map
		if (target instanceof Player p && p.currentDynamicMap != null)
			p.currentDynamicMap.addNpc(guardian);
		guardian.graphics(86);
		npc.forceText("Guardian, to me!");
		if (target != null)
			guardian.getCombat().setTarget(target);
	}

	@Override
	public void process() {
		// the icon can't be set from init(): the npc's avatar doesn't exist yet there (it would silently no-op)
		NPC.DefaultHeadIconIndex prayer = protectionPrayer();
		if (prayer != null && !prayerShown && !npc.isRemoved()) {
			npc.setHeadIcon(prayer);
			prayerShown = true;
		}
	}

	private static boolean blocks(NPC.DefaultHeadIconIndex prayer, AttackStyle style) {
		if (style == null)
			return false;
		return switch (prayer) {
			case ProtectFromMelee -> style.isMelee();
			case ProtectFromRanged -> style.isRanged();
			case ProtectFromMagic -> style.isMagic();
			default -> false;
		};
	}

	/**
	 * Safety net: the generic death routine removes the npc once the death animation has played, but these death
	 * animations hold their last (lying) frame for minutes, so a body that is never removed stays on the ground.
	 * Make sure it goes, and log if the normal path didn't do it.
	 */
	@Override
	public void startDeath(Hit killHit) {
		if (reinforcement != null && !reinforcement.isRemoved())
			reinforcement.remove();
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
		if (!mayEngage())
			return;
		if (leashCentre != null) {
			int own = npc.getPosition().distance(leashCentre);
			if (own >= leashRadius && target.getPosition().distance(leashCentre) > own)
				return; // at the edge of its leash: hold position rather than chase
		}
		follow(attackRange());
	}

	@Override
	public boolean attack() {
		if (target == null || !mayEngage())
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

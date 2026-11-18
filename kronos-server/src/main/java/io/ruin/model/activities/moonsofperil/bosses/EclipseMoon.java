package io.ruin.model.activities.moonsofperil.bosses;

import io.ruin.model.combat.CombatUtils;
import io.ruin.model.combat.Hit;
import io.ruin.model.entity.npc.NPC;
import io.ruin.model.entity.player.Player;
import io.ruin.model.entity.shared.StepType;
import io.ruin.model.map.Position;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Eclipse Moon (npc 13018, 500 HP, flat armour 6 / 4 during the Clone special, weak to stab).
 * <ul>
 * <li>Curse of the Moon (blindness): each standard attack blinds you further; your hits then have a curse% chance to
 * glance off her shield for 12 less (8 during clones).</li>
 * <li>Searing Rays: a moon shield appears and shoves you behind it, then circles the Moon. Rays fire every 3 ticks:
 * stay behind the shield. A ray that hits you does minor damage and deepens the blindness.</li>
 * <li>Clones: you're bound at the centre while 15 clones (five cycles of three) strike from around you. Face each
 * clone (click towards it) before it strikes to parry: the parry is an undelayed melee hit back at the Moon.</li>
 * </ul>
 */
public class EclipseMoon extends MoonBoss {

	public static final int ID = 13018; // pmoon_boss_eclipse_moon (multivar -> 13012 vis)
	public static final int SPAWN_X = 1486, SPAWN_Y = 9630, ENTRY_X = 1488, ENTRY_Y = 9637;

	private static final int[][] SIGILS = { { 1489, 9635 }, { 1491, 9633 }, { 1491, 9630 }, { 1486, 9628 }, { 1484, 9630 },
		{ 1484, 9633 } };

	private static final int COMBO_ANIM = 11023, CLONE_SPAWN_ANIM = 11019, CLONE_MELEE_ANIM = 11022, DISAPPEAR_ANIM = 11021,
		APPEAR_ANIM = 11020, RAYS_OPEN_ANIM = 11017, RAYS_CAST_ANIM = 11018, SHIELD_SPAWN_ANIM = 10973, SHIELD_DESPAWN_ANIM = 10975;
	private static final int RAYS_GFX = 2784, STUN_GFX = 245;
	public static final int CLONE = 13019, SHIELD = 13020;

	private static final int CENTRE_X = 1488, CENTRE_Y = 9632;
	/* clone 5x5 SW offsets from the centre: NE, E, SE, S, SW, W, NW, N */
	private static final int[][] CLONE_RING = { { 1, 1 }, { 1, -2 }, { 1, -5 }, { -2, -5 }, { -5, -5 }, { -5, -2 }, { -5, 1 },
		{ -2, 1 } };
	private static final int[][] PERIMETER = { { 1484, 9628 }, { 1484, 9635 }, { 1491, 9635 }, { 1491, 9628 } };

	private static final String BOUND_KEY = "ECLIPSE_BOUND";

	private int curse = 0;

	@Override
	protected int condition() {
		return curse;
	}

	/* clones */
	private boolean clones;
	private int cloneTicks, clonesSpawned, nextCloneIn, cycleCount;
	private final List<Object[]> liveClones = new ArrayList<>(); // {npc, ticksToStrike, dirX, dirY}
	private int faceX = 0, faceY = 0;

	/* searing rays */
	private boolean rays;
	private int raysTicks, sweepIn, castIn, corner, shieldPause;
	private NPC shield;
	private boolean sweepDone;

	@Override
	protected int[][] sigils() {
		return SIGILS;
	}

	@Override
	protected int comboAnim() {
		return COMBO_ANIM;
	}

	@Override
	protected int flatArmour() {
		return clones ? 4 : 6;
	}

	@Override
	protected boolean allowHitsDuringSpecial() {
		return false;
	}

	@Override
	protected void onComboHit(int index, int damage) {
		if (damage <= 0)
			return;
		blind(index == 0 ? 1 : index == 1 ? 2 : 5); // wiki: each hit of the series applies 1, 2 and 5 stacks
	}

	@Override
	protected void offGlyphCurse() {
		blind(1);
	}

	/** wiki: the curse caps at 127 (glancing reaches 100% at 100 stacks) and decays 2 every 5 ticks */
	private void blind(int amount) {
		curse = Math.min(127, curse + amount);
	}

	@Override
	protected void standardTick() {
		if (curse > 0 && io.ruin.Server.currentTick() % 5 == 0)
			curse = Math.max(0, curse - 2);
	}

	@Override
	protected void modifyPlayerHit(Hit hit) {
		if (curse > 0 && hit.damage > 0 && rand(1, 100) <= Math.min(100, curse)) {
			hit.damage = Math.max(0, hit.damage - (clones ? 8 : 12));
			player.sendFilteredMessage("<col=b25000>Blinded by the Eclipse Moon's light, your attack glances off its shield!</col>");
		}
	}

	@Override
	protected void startSpecial(boolean a) {
		if (a)
			startRays();
		else
			startClones();
	}

	@Override
	protected boolean specialTick() {
		if (rays)
			return raysTick();
		if (clones)
			return clonesTick();
		return false;
	}

	/** Damage the Moon from a special (parry / dodged ray); never below 1 HP for rays. */
	private void burnMoon(int dmg, boolean canKill) {
		if (!canKill)
			dmg = Math.min(dmg, npc.getHp() - 1);
		if (dmg <= 0)
			return;
		npc.hit(new Hit().fixedDamage(dmg));
	}

	/* ------------------------------------------------------------------ searing rays */

	private void startRays() {
		rays = true;
		raysTicks = 60;
		sweepIn = 10;
		castIn = 12;
		corner = 0;
		shieldPause = 0;
		sweepDone = false;
		npc.animate(RAYS_OPEN_ANIM);
		shield = spawnAdd(SHIELD, 1491, 9628);
		shield.animate(SHIELD_SPAWN_ANIM);
		player.getMovement().teleport(mx(1491), my(9627), 0);
		player.graphics(STUN_GFX, 124, 0);
		player.stun(1, true);
	}

	private boolean raysTick() {
		if (shield == null)
			return false;
		if (sweepIn > 0) {
			sweepIn--;
		} else if (!sweepDone) {
			int[] c = PERIMETER[corner];
			int tx = mx(c[0]), ty = my(c[1]);
			if (shield.getAbsX() == tx && shield.getAbsY() == ty) {
				if (++shieldPause >= 2) {
					shieldPause = 0;
					if (++corner >= PERIMETER.length)
						sweepDone = true;
				}
			} else {
				shield.stepAbs(shield.getAbsX() + Integer.signum(tx - shield.getAbsX()), shield.getAbsY() + Integer.signum(ty - shield.getAbsY()),
					StepType.WALK);
			}
		}
		if (--castIn <= 0 && !sweepDone) {
			castIn = 3;
			npc.animate(RAYS_CAST_ANIM);
			npc.graphics(RAYS_GFX);
			if (!behindShield()) { // wiki: minor damage and a large increase in the blindness; a dodged ray just misses
				damagePlayer(1, 5);
				blind(10);
			}
		}
		if (sweepDone || --raysTicks <= 0) {
			endRays();
			return false;
		}
		return true;
	}

	private boolean behindShield() {
		Position c = centre();
		int px = player.getAbsX(), py = player.getAbsY(), sx = shield.getAbsX(), sy = shield.getAbsY();
		if (Math.max(Math.abs(px - sx), Math.abs(py - sy)) > 1)
			return false;
		int pd = Math.max(Math.abs(px - c.getX()), Math.abs(py - c.getY()));
		int sd = Math.max(Math.abs(sx - c.getX()), Math.abs(sy - c.getY()));
		return pd > sd || (px == sx && py == sy);
	}

	private void endRays() {
		rays = false;
		if (shield != null) {
			NPC s = shield;
			shield = null;
			s.animate(SHIELD_DESPAWN_ANIM);
			npc.addEvent(e -> {
				e.delay(3);
				removeAdd(s);
			});
		}
	}

	/* ------------------------------------------------------------------ clones */

	private void startClones() {
		clones = true;
		cloneTicks = 0;
		clonesSpawned = 0;
		cycleCount = 0;
		nextCloneIn = 8;
		faceX = faceY = 0;
		npc.animate(DISAPPEAR_ANIM);
		npc.addEvent(e -> {
			e.delay(2);
			if (clones)
				hideMoon(true);
		});
		player.getMovement().teleport(mx(CENTRE_X), my(CENTRE_Y), 0);
		player.graphics(STUN_GFX, 124, 0);
		player.set(BOUND_KEY, this);
		player.freeze(60, npc);
	}

	private boolean clonesTick() {
		cloneTicks++;
		if (clonesSpawned < 15 && --nextCloneIn <= 0) {
			int k = rand(0, CLONE_RING.length - 1);
			int[] off = CLONE_RING[k];
			NPC c = spawnAdd(CLONE, CENTRE_X + off[0], CENTRE_Y + off[1]);
			c.animate(CLONE_SPAWN_ANIM);
			c.face(pos(CENTRE_X, CENTRE_Y));
			liveClones.add(new Object[] { c, 2, Integer.signum(off[0] + 2), Integer.signum(off[1] + 2) });
			clonesSpawned++;
			if (++cycleCount >= 3) {
				cycleCount = 0;
				nextCloneIn = 5;
			} else {
				nextCloneIn = 2;
			}
		}
		for (Iterator<Object[]> it = liveClones.iterator(); it.hasNext();) {
			Object[] c = it.next();
			NPC clone = (NPC) c[0];
			int t = (int) c[1] - 1;
			c[1] = t;
			if (t == 0) {
				clone.animate(CLONE_MELEE_ANIM);
				if (faceX == (int) c[2] && faceY == (int) c[3]) {
					parry();
				} else {
					damagePlayer(0, 16);
				}
			} else if (t <= -3) {
				removeAdd(clone);
				it.remove();
			}
		}
		if (clonesSpawned >= 15 && liveClones.isEmpty()) {
			endClones();
			return false;
		}
		if (cloneTicks > 120) { // safety net
			endClones();
			return false;
		}
		return true;
	}

	private void parry() {
		if (player.getCombat().weaponType != null)
			player.animate(player.getCombat().weaponType.attackAnimation);
		var style = player.getCombat().getAttackStyle();
		if (style == null || !style.isMelee()) {
			player.sendFilteredMessage("You parry the clone, but only a melee weapon can strike back at the Eclipse Moon.");
			return;
		}
		int max = CombatUtils.getMaxMeleeDamage(player, player.getCombat().getAttackType());
		int dmg = Math.max(0, rand(0, max) - flatArmour());
		burnMoon(dmg, true);
		player.sendFilteredMessage("You parry the clone and strike back at the Eclipse Moon!");
	}

	private void endClones() {
		clones = false;
		for (Object[] c : liveClones)
			removeAdd((NPC) c[0]);
		liveClones.clear();
		if (player != null) {
			player.remove(BOUND_KEY);
			player.resetFreeze();
			if (fighting())
				player.getMovement().teleport(mx(1488), my(9628), 0);
		}
		hideMoon(false);
		npc.animate(APPEAR_ANIM);
	}

	/**
	 * Walk-click hook: while bound by the clones, clicking a tile turns the player towards it instead of walking.
	 */
	public static boolean faceClick(Player player, int x, int y) {
		Object o = player.get(BOUND_KEY);
		if (!(o instanceof EclipseMoon m) || !m.clones)
			return false;
		int dx = Integer.signum(x - player.getAbsX()), dy = Integer.signum(y - player.getAbsY());
		if (dx == 0 && dy == 0)
			return true;
		m.faceX = dx;
		m.faceY = dy;
		player.face(player.getAbsX() + dx, player.getAbsY() + dy);
		return true;
	}

	@Override
	protected void cleanupSpecial() {
		if (rays)
			endRays();
		if (clones)
			endClones();
		curse = 0;
	}
}

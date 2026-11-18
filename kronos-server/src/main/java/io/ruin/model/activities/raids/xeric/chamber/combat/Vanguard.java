package io.ruin.model.activities.raids.xeric.chamber.combat;

import io.ruin.api.utils.Random;
import io.ruin.model.World;
import io.ruin.model.activities.raids.xeric.ChambersOfXeric;
import io.ruin.model.combat.AttackStyle;
import io.ruin.model.combat.Hit;
import io.ruin.model.entity.npc.NPCCombat;
import io.ruin.model.map.Position;
import io.ruin.model.map.Projectile;
import io.ruin.model.skills.prayer.Prayer;

/**
 * The Vanguards (melee 7527 / ranged 7528 / magic 7529), as recorded (RSProx, MRobertEvers docs/rsprox-analysis/cox/
 * vanguards.md, 2026-11-15):
 * <ul>
 * <li>They take a raider within 5 of their 3x3, swing every 4 ticks, and the ranged and magic ones never move while
 * fighting (RV-A1).</li>
 * <li>Melee: three hits on the swing tick (RV-A1).</li>
 * <li>Ranged volley: three rocks from its middle -- 1334 locked on the target (start 23, angle 10), 1333 to a tile exactly
 * 2 from the target (start 25, angle 16), 1332 to a tile exactly 4 away (start 26, angle 19), heights 100 -> 5, all
 * ending at 46 + 6 x the gap from its 3x3; the locked rock's 305 splash with it, the tile rocks' splash on landing
 * (RV-A2).</li>
 * <li>Magic volley: three 1331 orbs -- locked (start 45, angle 60), a tile exactly 2 away (start 30, angle 61), one exactly
 * 4 away (start 30, angle 62), heights 150 -> 5, ending at 46 + 25 x the gap; 659 where the tile orbs land (RV-A3).</li>
 * </ul>
 * Damage isn't in the recordings (every recorded hit was 0); the max hits and the prayer factor stay the wiki's.
 * The projectile "duration" here is the end cycle, measured from the 3x3's middle (distance = gap + 1).
 */
public class Vanguard extends NPCCombat {

	private static final Projectile ROCK_LOCKED = new Projectile(1334, 100, 5, 23, 40, 6, 10, 64);
	private static final Projectile ROCK_TWO = new Projectile(1333, 100, 5, 25, 40, 6, 16, 64);
	private static final Projectile ROCK_FOUR = new Projectile(1332, 100, 5, 26, 40, 6, 19, 64);
	private static final Projectile ORB_LOCKED = new Projectile(1331, 150, 5, 45, 21, 25, 60, 64);
	private static final Projectile ORB_TWO = new Projectile(1331, 150, 5, 30, 21, 25, 61, 64);
	private static final Projectile ORB_FOUR = new Projectile(1331, 150, 5, 30, 21, 25, 62, 64);

	@Override
	public void init() {
	}

	@Override
	public void follow() {
		// ranged and magic never move while fighting; the melee one walks in
		if (npc.getId() == 7527)
			follow(1);
	}

	@Override
	public boolean attack() {
		switch (npc.getId()) {
			case 7527:
				return meleeAttack();
			case 7528:
				return rangedAttack();
			case 7529:
				return magicAttack();
			default:
				return false;
		}
	}

	/** a random tile exactly n tiles (Chebyshev) from the target */
	private Position tileAt(Position from, int n) {
		int dx, dy;
		if (Random.get(1) == 0) {
			dx = Random.get(1) == 0 ? -n : n;
			dy = Random.get(-n, n);
		} else {
			dy = Random.get(1) == 0 ? -n : n;
			dx = Random.get(-n, n);
		}
		return from.copy().translate(dx, dy, 0);
	}

	/** players standing on a tile when a tile shot lands, after its flight (cycles) */
	private void landOn(Position pos, int endCycles, int spot, int spotDelay, AttackStyle style, int max, Prayer prayer) {
		int ticks = Math.max(1, endCycles / 30);
		npc.addEvent(event -> {
			event.delay(ticks);
			World.sendGraphics(spot, 0, spotDelay, pos);
			npc.localPlayers().stream().filter(p -> p.getPosition().equals(pos)).forEach(p -> {
				int dmg = p.getPrayer().isActive(prayer) ? max / 2 : max;
				p.hit(new Hit(npc, style).randDamage(dmg).ignoreDefence().ignorePrayer());
			});
		});
	}

	private boolean magicAttack() {
		if (!withinDistance(8))
			return false;
		npc.animate(getInfo().attack_animation);
		Position at = target.getPosition().copy();
		int end = ORB_LOCKED.send(npc, target);
		int dmg = target.player != null && target.player.getPrayer().isActive(Prayer.PROTECT_FROM_MAGIC) ? 11 : 22;
		target.hit(new Hit(npc, AttackStyle.MAGIC).randDamage(dmg).clientDelay(end).ignoreDefence().ignorePrayer());
		Position two = tileAt(at, 2), four = tileAt(at, 4);
		landOn(two, ORB_TWO.send(npc, two), 659, 10, AttackStyle.MAGIC, 22, Prayer.PROTECT_FROM_MAGIC);
		landOn(four, ORB_FOUR.send(npc, four), 659, 10, AttackStyle.MAGIC, 22, Prayer.PROTECT_FROM_MAGIC);
		return true;
	}

	private boolean rangedAttack() {
		if (!withinDistance(8))
			return false;
		npc.animate(getInfo().attack_animation);
		Position at = target.getPosition().copy();
		// protection reduces the damage, it never nullifies it
		int dmg = target.player != null && target.player.getPrayer().isActive(Prayer.PROTECT_FROM_MISSILES) ? getInfo().max_damage / 2 : getInfo().max_damage;
		int end = ROCK_LOCKED.send(npc, target);
		target.hit(new Hit(npc, AttackStyle.RANGED).randDamage(dmg).clientDelay(end).ignorePrayer());
		target.graphics(305, 0, end);
		Position two = tileAt(at, 2), four = tileAt(at, 4);
		landOn(two, ROCK_TWO.send(npc, two), 305, 0, AttackStyle.RANGED, 19, Prayer.PROTECT_FROM_MISSILES);
		landOn(four, ROCK_FOUR.send(npc, four), 305, 0, AttackStyle.RANGED, 19, Prayer.PROTECT_FROM_MISSILES);
		return true;
	}

	/** three hits on the swing tick */
	private boolean meleeAttack() {
		if (!withinDistance(1))
			return false;
		npc.animate(getInfo().attack_animation);
		int dmg = target.player != null && target.player.getPrayer().isActive(Prayer.PROTECT_FROM_MELEE) ? 7 : 18;
		for (int i = 0; i < 3; i++)
			target.hit(new Hit(npc, AttackStyle.CRUSH).randDamage(dmg).ignoreDefence().ignorePrayer());
		return true;
	}

	@Override
	public int getRandomDropCount() {
		return 1 + Math.min(2, (ChambersOfXeric.getPartySize(npc) / 5));
	}

	@Override
	public void process() {
	}

	/** within 5 of its 3x3 (measured from the south-west tile: 5 + its size - 1) */
	@Override
	public int getAggressionRange() {
		return 7;
	}

	@Override
	public int getAttackBoundsRange() {
		return 7;
	}
}

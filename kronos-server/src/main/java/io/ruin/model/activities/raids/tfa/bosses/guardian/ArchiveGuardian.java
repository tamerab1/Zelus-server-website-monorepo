package io.ruin.model.activities.raids.tfa.bosses.guardian;

import io.ruin.api.utils.Random;
import io.ruin.model.activities.raids.tfa.Raid4Fx;
import io.ruin.model.activities.raids.tfa.Raid4Scaling;
import io.ruin.model.activities.raids.tfa.Raid4Schedule;
import io.ruin.model.activities.raids.tfa.rooms.ArchiveGuardianRoom;
import io.ruin.model.combat.AttackStyle;
import io.ruin.model.combat.Hit;
import io.ruin.model.combat.HitType;
import io.ruin.model.entity.Entity;
import io.ruin.model.entity.npc.NPCCombat;
import io.ruin.model.entity.player.Player;
import io.ruin.model.entity.shared.listeners.HitListener;
import io.ruin.model.item.Item;
import io.ruin.model.item.containers.Equipment;
import io.ruin.model.map.Position;
import io.ruin.model.map.Tile;
import io.ruin.model.skills.prayer.Prayer;
import io.ruin.model.stat.Stat;
import io.ruin.model.stat.StatType;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.ruin.model.activities.raids.tfa.Raid4Ids.*;

/**
 * The Archive Guardian (16659 flying / 16660 grounded), rebuilt 2026-10-28 from the RSProx captures of every
 * Guardian fight on the index (docs/rsprox-analysis/guthixian_archive/guardian_plan.md). Section numbers (§) refer to
 * that plan.
 * <ul>
 * <li>§3 one 5-tick clock: four ranged then the special (mushrooms); the slam instead when a raider is under him;
 * a swoosh (no slot) when his target is 7+ from his 5x5, the next attack max(last + 5, swoosh + 3).</li>
 * <li>§4 ranged: seq 14751, spot 4199, projectile 4201 (start 48, end 116, angle 127, progress 0, heights 0/0) from his
 * middle, homing; lands +4 with 4200 on the raider; max 41, Protect from Missiles takes 75%.</li>
 * <li>§6 special: seq 14753 + spot 4205 (the fire from his mouth); two mushrooms a raider (the room).</li>
 * <li>§7 the shield at 0: down 3 ticks later; grounded 35, then 6 more each landing; vines from landing + 5 every 3.</li>
 * <li>§9 at a vine slot under 34% real health: no wave, lift 3 later and enrage (a battery overload every 20).</li>
 * <li>§10 the slam: 0..48 the tick after on whoever is still under him (Protect from Melee blocks it, unrecorded).</li>
 * </ul>
 */
@Slf4j
public class ArchiveGuardian extends NPCCombat {

	public enum Phase {
		SHIELDED, GROUNDED, ENRAGED
	}

	static final int CLOCK = 5, RANGED_MAX = 41, SLAM_MAX = 48, SWOOSH_RANGE = 7;
	static final int GROUND_FIRST = 35, GROUND_STEP = 6, VINE_START = 5, VINE_EVERY = 3;
	static final double ENRAGE_AT = 0.34, DEFENCE_FLOOR = 0.60, BATTERY_SHARE = 0.40;
	static final int OVERLOAD_EVERY = 20;
	static final int SPOT_SWORD_DROP_INSTANT = 4389;

	private ArchiveGuardianRoom room;
	private Phase phase = Phase.SHIELDED;
	private int maxShield, maxHp, hpRemaining, cycle = 1, landings, slotsSinceSpecial, specialAt = 5, overloads;
	private long nextAttack, groundedUntil, nextVine, nextOverload = Long.MAX_VALUE;
	private boolean breakPending, lifting;
	/** Enraged with the shield broken: the unshielded type in the air (guardian_plan §9), no landing. */
	private boolean exposed;
	private final Map<Player, Position> lastTiles = new HashMap<>();

	public void bind(ArchiveGuardianRoom room) {
		this.room = room;
		maxShield = Raid4Scaling.guardianShield(room.getScale());
		maxHp = Raid4Scaling.guardianHp(room.getScale());
		setHp(maxShield, maxShield);
		hpRemaining = maxHp;
		nextAttack = schedule().now() + 4;
		Player first = room.randomFighter();
		if (first != null)
			setTarget(first);
	}

	@Override
	public void init() {
		npc.hitListener = new HitListener()
				.preDefend(this::preDefend)
				.preDamage(this::preDamage)
				.postDamage(this::postDamage);
		npc.deathEndListener = (entity, killer, killHit) -> {
			if (room != null)
				room.onNpcDeath(npc);
		};
	}

	@Override
	public boolean isAggressive() {
		return false;
	}

	@Override
	public boolean allowRetaliate(Entity attacker) {
		return false;
	}

	/* Incoming damage */

	private void preDefend(Hit hit) {
		Player player = hit.attacker == null ? null : hit.attacker.player;
		if (player == null)
			return;
		if (hit.attackStyle != null && hit.attackStyle.isMelee() && isFlying() && !wieldingHalberd(player)) {
			hit.nullify();
			player.sendMessage("It's flying too high to hit using melee.");
		}
	}

	/** The shield never takes him to 0: whatever breaks it ends the shielded phase instead. */
	private void preDamage(Hit hit) {
		if (phase == Phase.GROUNDED || exposed || breakPending)
			return;
		int hp = npc.getHp();
		if (hit.damage >= hp) {
			hit.damage = Math.max(0, hp - 1);
			breakPending = true;
		}
	}

	private void postDamage(Hit hit) {
		if (room != null && hit.attacker != null && hit.attacker.player != null)
			room.recordDamage(hit.attacker.player, hit.damage);
	}

	private static boolean wieldingHalberd(Player player) {
		Item weapon = player.getEquipment().get(Equipment.SLOT_WEAPON);
		return weapon != null && weapon.getDef() != null && weapon.getDef().name != null
				&& weapon.getDef().name.toLowerCase().contains("halberd");
	}

	public boolean isFlying() {
		return phase != Phase.GROUNDED;
	}

	@Override
	public void process() {
		Stat def = getStat(StatType.Defence);
		int floor = (int) Math.ceil(def.fixedLevel * DEFENCE_FLOOR);
		if (def.currentLevel < floor)
			def.currentLevel = floor;
	}

	@Override
	public void follow() {
	}

	@Override
	public boolean attack() {
		return false;
	}

	/* ---------------------------------------------------------------- the room's tick */

	public void roomTick() {
		if (room == null || isDead())
			return;
		long now = schedule().now();
		if (!(target instanceof Player p) || !room.fighters().contains(p))
			setTarget(room.randomFighter());
		if (breakPending && phase != Phase.GROUNDED) {
			breakPending = false;
			// the enrage's shield, broken, leaves him unshielded where he flies; the cycle goes on (§9, 4294 [969])
			schedule().after(3, phase == Phase.ENRAGED ? this::breakInAir : this::land);
		}
		if (phase == Phase.GROUNDED) {
			if (now >= nextVine && !lifting) {
				nextVine = now + VINE_EVERY;
				long sinceLanding = now - (groundedUntil - groundTicks());
				// the slot 3 ticks before his time has no wave; from +11, a slot under 34% has none and he lifts early
				if (now + 3 >= groundedUntil || (sinceLanding >= 11 && npc.getHp() < maxHp * ENRAGE_AT)) {
					boolean enrage = npc.getHp() < maxHp * ENRAGE_AT;
					lifting = true;
					schedule().after(3, () -> lift(enrage));
				} else {
					room.vineWave(lastTiles);
				}
			}
		} else {
			if (phase == Phase.ENRAGED && now >= nextOverload)
				overloadCycle(now);
			if (now >= nextAttack && !lifting)
				attackSlot(now);
		}
		// the raiders' tiles as this tick began, for the next tick's rules
		lastTiles.clear();
		for (Player p : room.fighters())
			lastTiles.put(p, p.getPosition().copy());
	}

	private int groundTicks() {
		return GROUND_FIRST + GROUND_STEP * Math.max(0, landings - 1);
	}

	/* §3 the clock */

	private void attackSlot(long now) {
		if (!(target instanceof Player t))
			return;
		// §5 the swoosh: target 7+ from his 5x5 (as the tick began) -> a box 3 away; no slot
		Position tTile = lastTiles.getOrDefault(t, t.getPosition());
		if (distanceToBox(tTile) >= SWOOSH_RANGE) {
			swoosh(tTile);
			nextAttack = Math.max(nextAttack + CLOCK, now + 3);
			return;
		}
		nextAttack = now + CLOCK;
		// §10 the slam: any raider under him as the slot comes due
		List<Player> under = new ArrayList<>();
		for (Player p : room.fighters())
			if (distanceToBox(lastTiles.getOrDefault(p, p.getPosition())) == 0)
				under.add(p);
		if (!under.isEmpty()) {
			slam();
			return;
		}
		slotsSinceSpecial++;
		if (slotsSinceSpecial >= specialAt) {
			slotsSinceSpecial = 0;
			specialAt = Random.get(4) == 0 ? 6 : 5; // 4 ranged between (26 of 31), 5 between the rest
			special();
		} else {
			ranged(t);
		}
	}

	/** §4: one throw at his target. */
	private void ranged(Player t) {
		npc.face(t);
		npc.animate(SEQ_GUARDIAN_RANGED);
		Raid4Fx.spot(npc, 0, GFX_GUARDIAN_RANGED_CAST);
		Position mid = npc.getPosition().relative(2, 2);
		Raid4Fx.projectile(room.fighters(), npc, GFX_GUARDIAN_RANGED_PROJ, mid, t, t.getPosition(), 0, 0, 48, 116, 127, 0);
		schedule().after(4, () -> {
			if (isDead() || !room.fighters().contains(t))
				return;
			Raid4Fx.spot(t, 0, GFX_GUARDIAN_RANGED_HIT, 0, 0);
			int dmg = Random.get(RANGED_MAX);
			if (t.getPrayer().isActive(Prayer.PROTECT_FROM_MISSILES))
				dmg = dmg / 4;
			room.hitPlayer(t, new Hit(npc, AttackStyle.RANGED).fixedDamage(dmg).ignoreDefence().ignorePrayer());
		});
	}

	/** §6: the special, seq 14753 with 4205 on him; two mushrooms a raider round the tile of the tick before. */
	private void special() {
		npc.animate(SEQ_GUARDIAN_SPECIAL);
		Raid4Fx.spot(npc, 0, GFX_GUARDIAN_SPECIAL);
		room.spawnMushrooms(lastTiles);
	}

	/** §10: the slam, landing the tick after on whoever is still under him. */
	private void slam() {
		npc.animate(SEQ_GUARDIAN_MELEE);
		schedule().after(1, () -> {
			if (isDead())
				return;
			for (Player p : room.fighters())
				if (distanceToBox(p.getPosition()) == 0) {
					int dmg = p.getPrayer().isActive(Prayer.PROTECT_FROM_MELEE) ? 0 : Random.get(SLAM_MAX);
					room.hitPlayer(p, new Hit(npc, AttackStyle.CRUSH).fixedDamage(dmg).ignoreDefence().ignorePrayer());
				}
		});
	}

	/** §5: telejump to the first box (E, SE, S, SW, W, NW, N, NE) whose 25 tiles are open, 3 from the target. */
	private void swoosh(Position t) {
		int px = t.getX(), pz = t.getY();
		int[][] boxes = { { px + 3, pz - 2 }, { px + 3, pz - 7 }, { px - 2, pz - 7 }, { px - 7, pz - 7 }, { px - 7, pz - 2 },
				{ px - 7, pz + 3 }, { px - 2, pz + 3 }, { px + 3, pz + 3 } };
		Position from = npc.getPosition().copy();
		for (int[] b : boxes) {
			if (!open5x5(b[0], b[1], from.getZ()))
				continue;
			Position to = new Position(b[0], b[1], from.getZ());
			int travel = Math.max(Math.abs(to.getX() - from.getX()), Math.abs(to.getY() - from.getY()));
			int angle = Raid4Fx.angle(from.getX(), from.getY(), to.getX(), to.getY());
			npc.animate(SEQ_GUARDIAN_SWOOSH);
			Raid4Fx.slide(npc, to, 30 + 2 * travel, angle);
			Raid4Fx.faceAngle(npc, angle, false);
			return;
		}
	}

	private boolean open5x5(int x, int y, int z) {
		for (int dx = 0; dx < 5; dx++)
			for (int dy = 0; dy < 5; dy++) {
				Position p = new Position(x + dx, y + dy, z);
				if (!room.onArenaFloor(p))
					return false;
				Tile tile = Tile.get(p.getX(), p.getY(), z, false);
				if (tile != null && tile.clipping != 0 && !underMe(p))
					return false;
			}
		return true;
	}

	private boolean underMe(Position p) {
		return distanceToBox(p) == 0;
	}

	private int distanceToBox(Position p) {
		Position sw = npc.getPosition();
		int dx = Math.max(0, Math.max(sw.getX() - p.getX(), p.getX() - (sw.getX() + 4)));
		int dy = Math.max(0, Math.max(sw.getY() - p.getY(), p.getY() - (sw.getY() + 4)));
		return Math.max(dx, dy);
	}

	/* §7 the shield, the landing and the lift */

	/** A battery destroyed by the raiders (+3): 40% of the shield's max, capped at what is left. */
	public void onBatteryCharged(Battery battery) {
		if (phase == Phase.GROUNDED || exposed || isDead())
			return;
		int damage = Math.min(npc.getHp(), (int) Math.round(maxShield * BATTERY_SHARE));
		Raid4Fx.sound(room.fighters(), 12728, battery.tile.relative(1, 1), 0, 10);
		npc.hit(new Hit(HitType.DAMAGE).fixedDamage(damage).ignoreDefence().ignorePrayer());
	}

	private void land() {
		if (isDead() || phase == Phase.GROUNDED)
			return;
		landings++;
		phase = Phase.GROUNDED;
		npc.transform(NPC_GUARDIAN_UNSHIELDED);
		setHp(maxHp, Math.max(1, hpRemaining));
		npc.resetSteps();
		npc.animate(SEQ_GUARDIAN_IDLE_TO_GROUND);
		Raid4Fx.spot(npc, 0, GFX_GUARDIAN_TO_GROUND);
		Raid4Fx.mapAnim(SPOT_SWORD_DROP_INSTANT, npc.getPosition().relative(2, 2), 20, 0);
		room.blockGuardian(true);
		long now = schedule().now();
		groundedUntil = now + groundTicks();
		nextVine = now + VINE_START;
		schedule().after(1, () -> Raid4Fx.stance(npc, SEQ_GUARDIAN_GROUND_IDLE));
		room.message("The Archive Guardian's shield fails and it crashes to the ground!");
	}

	private void breakInAir() {
		if (isDead() || exposed)
			return;
		exposed = true;
		npc.transform(NPC_GUARDIAN_UNSHIELDED);
		setHp(maxHp, Math.max(1, hpRemaining));
		room.message("The Archive Guardian's shield shatters!");
	}

	private void lift(boolean enrage) {
		lifting = false;
		if (isDead())
			return;
		hpRemaining = npc.getHp();
		phase = enrage ? Phase.ENRAGED : Phase.SHIELDED;
		cycle++;
		npc.transform(NPC_GUARDIAN);
		setHp(maxShield, maxShield);
		Raid4Fx.resetStance(npc);
		npc.animate(SEQ_GUARDIAN_GROUND_TO_FLYING);
		Raid4Fx.spot(npc, 0, GFX_GUARDIAN_TO_FLYING);
		room.blockGuardian(false);
		long now = schedule().now();
		nextAttack = now + 5;
		slotsSinceSpecial = Random.get(1) == 0 ? 2 : 1; // after landing, the special the 3rd or 4th slot
		if (enrage && nextOverload == Long.MAX_VALUE)
			nextOverload = now + 5;
	}

	/* §9 the enrage: every 20 ticks, +5 the enraged seq, +10 a battery overloads */

	private void overloadCycle(long now) {
		nextOverload = now + OVERLOAD_EVERY;
		schedule().after(5, () -> {
			if (isDead())
				return;
			npc.animate(SEQ_GUARDIAN_ENRAGE);
			Raid4Fx.spot(npc, 0, GFX_GUARDIAN_ENRAGE);
		});
		schedule().after(10, () -> {
			if (isDead())
				return;
			Battery b = room.overloadNext();
			int raiders = Math.max(2, room.fighters().size());
			int total = (int) Math.round(64 * Math.pow(1.25, overloads));
			overloads++;
			int each = Math.max(1, total / raiders);
			for (Player p : room.fighters()) {
				int dmg = p.getPrayer().isActive(Prayer.PROTECT_FROM_MAGIC) ? (int) (each / 1.5) : each;
				room.hitPlayer(p, new Hit(npc, AttackStyle.MAGIC).fixedDamage(dmg).ignoreDefence().ignorePrayer());
			}
			if (b != null && phase != Phase.GROUNDED)
				onBatteryCharged(b);
		});
	}

	/* Helpers */

	private void setHp(int max, int current) {
		Stat hp = getStat(StatType.Hitpoints);
		hp.fixedLevel = max;
		hp.currentLevel = current;
	}

	private Raid4Schedule schedule() {
		return room.getSchedule();
	}

	public Phase getPhase() {
		return phase;
	}

	public int getCycle() {
		return cycle;
	}

	public int getRealHp() {
		return phase == Phase.GROUNDED || exposed ? npc.getHp() : hpRemaining;
	}

	/** Admin testing: break the shield / drop to the enrage line / kill. */
	public void debugAdvancePhase() {
		if (room == null || isDead())
			return;
		switch (phase) {
			case SHIELDED -> breakPending = true;
			case GROUNDED -> getStat(StatType.Hitpoints).currentLevel = (int) (maxHp * ENRAGE_AT) - 1;
			case ENRAGED -> npc.hit(new Hit(HitType.DAMAGE).fixedDamage(npc.getHp()).ignoreDefence().ignorePrayer());
		}
	}

	public void debugBreath() {
		if (room != null && !isDead())
			special();
	}
}

package io.ruin.model.activities.bosses.cindermaw;

import io.ruin.api.utils.Random;
import io.ruin.model.World;
import io.ruin.model.combat.AttackStyle;
import io.ruin.model.combat.Hit;
import io.ruin.model.entity.npc.NPC;
import io.ruin.model.entity.npc.NPCCombat;
import io.ruin.model.entity.shared.listeners.HitListener;
import io.ruin.model.map.Position;
import io.ruin.model.map.Projectile;
import io.ruin.utility.TickDelay;

/// Custom boss cloned from Drake (npc 8612) -- bigger, recoloured, renamed. Keeps Drake's base
/// melee/breath/AoE pattern and adds:
///  - "Ashfall": a borrowed/adapted version of BalanceElemental's PrayerDrainSpecial, reframed
///    as a ground-targeted ash cloud that drains prayer if the target doesn't move off it.
///  - An enrage phase below 50% hp (a real "unique" mechanic, not borrowed): damage output goes
///    up and the special attacks fire more often for the rest of the fight.
///  - A random anti-melee/anti-range switch, same mechanism Kree'arra uses (a preDefend
///    HitListener that nullifies the disallowed style) but toggling randomly over time instead
///    of being permanently anti-melee.
public class Cindermaw extends NPCCombat {

	private static final Projectile RANGED_PROJECTILE = new Projectile(1636, 25, 31, 40, 30, 8, 16, 96);
	private static final Projectile SPECIAL_PROJECTILE = new Projectile(1637, 25, 0, 40, 85, 0, 16, 96);

	private static final double ENRAGE_HP_THRESHOLD = 0.5;
	private static final double ENRAGE_DAMAGE_MULTIPLIER = 1.3;

	private enum DefenseMode {
		ANTI_MELEE, ANTI_RANGE
	}

	private static final int SWITCH_MIN_TICKS = 8;
	private static final int SWITCH_MAX_TICKS = 15;

	private final TickDelay switchTimer = new TickDelay();
	private DefenseMode mode;

	private int count = 0;
	private boolean enraged = false;

	@Override
	public void init() {
		npc.hitListener = new HitListener().preDamage(hit -> {
			if (hit.attackStyle == null || mode == null) {
				return;
			}
			boolean blocked = (mode == DefenseMode.ANTI_MELEE && hit.attackStyle.isMelee())
					|| (mode == DefenseMode.ANTI_RANGE && hit.attackStyle.isRanged());
			if (blocked) {
				hit.block();
			}
		});
		switchDefenseMode();
	}

	private void setDefenseIcon() {
		npc.setHeadIcon(mode == DefenseMode.ANTI_MELEE
				? NPC.DefaultHeadIconIndex.ProtectFromMelee
				: NPC.DefaultHeadIconIndex.ProtectFromRanged);
	}

	private void switchDefenseMode() {
		DefenseMode previous = mode;
		DefenseMode[] options = DefenseMode.values();
		do {
			mode = options[Random.get(0, options.length - 1)];
		} while (mode == previous && previous != null);

		String warning = mode == DefenseMode.ANTI_MELEE
				? "Cindermaw's ashen hide hardens against melee weapons!"
				: "Cindermaw's ashen hide hardens against ranged weapons!";
		npc.getPosition().getRegion().players.forEach(p -> p.sendMessage(warning));
		setDefenseIcon();

		switchTimer.delay(Random.get(SWITCH_MIN_TICKS, SWITCH_MAX_TICKS));
	}

	@Override
	public void follow() {
		follow(6);
	}

	@Override
	public boolean attack() {
		if (!withinDistance(6)) {
			return false;
		}

		if (!switchTimer.isDelayed()) {
			switchDefenseMode();
		}

		if (!enraged && npc.getHp() < info.hitpoints * ENRAGE_HP_THRESHOLD) {
			triggerEnrage();
		}

		int specialThreshold = enraged ? 3 : 6;
		int ashfallThreshold = enraged ? 8 : 12;

		if (withinDistance(1) && Random.rollDie(2, 1)) {
			basicAttack();
		} else if (count > 0 && count % ashfallThreshold == 0) {
			ashfallSpecial();
		} else if (count > specialThreshold) {
			breathSpecial();
			count = 0;
		} else {
			rangedAttack();
		}
		count++;
		return true;
	}

	@Override
	public void process() {
	}

	private void triggerEnrage() {
		enraged = true;
		World.sendGraphics(451, 0, 0, npc.getPosition());
	}

	private int applyEnrage(int damage) {
		return enraged ? (int) Math.round(damage * ENRAGE_DAMAGE_MULTIPLIER) : damage;
	}

	private void rangedAttack() {
		projectileAttack(RANGED_PROJECTILE, 8276, AttackStyle.RANGED, applyEnrage(info.max_damage));
	}

	private void breathSpecial() {
		npc.animate(8276);
		final Position targetPos = target.getPosition().copy();
		int delay = SPECIAL_PROJECTILE.send(npc, targetPos);
		World.sendGraphics(1638, 0, delay, targetPos);
		npc.addEvent(event -> {
			event.delay(2);
			if (target != null && target.getPosition().equals(targetPos)) {
				target.hit(new Hit(npc, AttackStyle.DRAGONFIRE).randDamage(applyEnrage(5), applyEnrage(15)));
			}
		});
	}

	/// Adapted from io.ruin.model.activities.bosses.balanceelemental.attacks.specialattacks
	/// .PrayerDrainSpecial -- single-target version of the same ground-marker mechanic.
	private void ashfallSpecial() {
		if (target == null) {
			return;
		}
		npc.animate(8276);
		var t = target;
		World.startEvent(e -> {
			e.setCancelCondition(() -> npc.getCombat().isDead() || t.getCombat().isDead());
			Position targetPosition = t.getPosition().copy();
			World.sendGraphics(2910, 0, 0, targetPosition);
			e.delay(2);
			World.sendGraphics(2911, 0, 0, targetPosition);
			e.delay(5);
			if (t.getPosition().distance(targetPosition) < 1) {
				t.graphics(1880);
				if (t.player != null) {
					t.player.sendMessage("The falling ash smothers your prayers!");
					t.player.getPrayer().drain(applyEnrage(30));
				}
			}
		});
	}

	@Override
	public void startDeath(Hit killHit) {
		setDead(true);
		if (target != null) {
			reset();
		}
		npc.addEvent(event -> {
			npc.animate(8277);
			event.delay(1);
			npc.animate(8278);
			super.startDeath(killHit);
		});
	}
}

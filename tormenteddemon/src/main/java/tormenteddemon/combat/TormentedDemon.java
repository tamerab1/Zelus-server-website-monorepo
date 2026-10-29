package tormenteddemon.combat;

import io.ruin.api.utils.Random;
import io.ruin.model.World;
import io.ruin.model.combat.AttackStyle;
import io.ruin.model.combat.Hit;
import io.ruin.model.entity.Entity;
import io.ruin.model.entity.npc.NPC;
import io.ruin.model.entity.npc.NPCCombat;
import io.ruin.model.entity.player.Player;
import io.ruin.model.entity.shared.listeners.HitListener;
import io.ruin.model.item.Item;
import io.ruin.model.item.containers.Equipment;
import io.ruin.model.map.Position;
import io.ruin.model.map.Projectile;
import io.ruin.model.map.Tile;
import io.ruin.model.var.VarPlayerRepository;
import tormenteddemon.combat.util.Overhead;
import tormenteddemon.combat.util.WeaponChecker;

import java.util.ArrayList;
import java.util.List;

/**
 * Tormented Demon, OSRS 2024 rework (wiki, Oct 2026), on the rev 241 cache's own animations and graphics
 * (LUC2_UNDEAD_DEMON_*):
 * <ul>
 * <li>600 hp, 6-tick attacks, max hit 31 (fire bomb 45). One combat style at a time: melee when it can reach its target,
 * otherwise a random magic (fireball 2853) or ranged (rib shard 2857) attack. Protection prayers block it fully.</li>
 * <li>It prays too: after every 150 damage since its last swap it switches its protection prayer to the style it was
 * last hit with, and holds its attacks for 6 ticks.</li>
 * <li>Fire bomb about every 60 ticks: binds the player and turns off run, then two bombs land: one on the player's tile,
 * one on a random tile next to it; the bind lets go with 2 ticks to step off. It always changes style afterwards.</li>
 * <li>Fire shield: up most of the time, taking 20% off hits from anything but demonbane/abyssal weapons. It drops after
 * the first attack and after every fire bomb, staying down until hit once; that hit gets the slow-weapon bonus
 * ((attack speed^2 - 16)%).</li>
 * <li>About 30 ticks into the fight its flames go out: defenceless (every hit lands) until the next fire bomb, then the
 * 30-tick timer starts again.</li>
 * <li>Unaggressive; forced out of combat it heals to full.</li>
 * </ul>
 * Form 13600 burns; 13601 is the extinguished (defenceless) look.
 */
public class TormentedDemon extends NPCCombat {

	static final int MAX_HIT = 31, BOMB_MAX = 45;
	static final int PRAYER_SWAP_DAMAGE = 150, PRAYER_SWAP_PAUSE = 6;
	static final int BOMB_EVERY = 60, DEFENCELESS_AFTER = 30;
	static final double SHIELD_REDUCTION = 0.20;
	static final int FORM_BURNING = 13600, FORM_EXTINGUISHED = 13601;

	static final int SEQ_MELEE = 11392, SEQ_MAGIC = 11388, SEQ_RANGED = 11389, SEQ_BOMB = 11387;
	static final int GFX_SHIELD = 2849, GFX_SHIELD_RESTORE = 2850, GFX_MELEE = 2851, GFX_BOMB_CAST = 2852;
	static final int GFX_FIREBALL = 2853, GFX_FIREBALL_HIT = 2854, GFX_BOMB_PROJ = 2855, GFX_BOMB_HIT = 2856, GFX_SHARD = 2857;
	static final int GFX_SHARD_HIT = 2858;

	private enum Style {
		MELEE, MAGIC, RANGED
	}

	private Style style = Style.MELEE;
	private Overhead overhead = Overhead.MELEE;
	private int damageSinceSwap;
	private long now, pausedUntil, nextBomb, defencelessAt;
	private boolean shieldUp = true, defenceless, inFight;

	@Override
	public void init() {
		npc.hitListener = new HitListener().preDefend(this::preDefend).preDamage(this::preDamage).postDamage(this::postDamage);
		npc.deathEndListener = (entity, killer, killHit) -> resetFight();
		style = Style.values()[Random.get(2)];
		overhead = Overhead.values()[Random.get(2)];
		overheadPending = true; // the avatar doesn't exist yet in init(): shown on the first tick
	}

	private boolean overheadPending;

	@Override
	public boolean isAggressive() {
		return false; // "unaggressive: they won't attack unless attacked first"
	}

	/* Incoming damage */

	private void preDefend(Hit hit) {
		if (defenceless)
			hit.ignoreDefence(); // "while defenceless, attacks have 100% accuracy"
	}

	private void preDamage(Hit hit) {
		if (hit.attackStyle == null || hit.attacker == null || hit.attacker.player == null)
			return;
		if ((hit.attackStyle.isMelee() && overhead == Overhead.MELEE) || (hit.attackStyle.isRanged() && overhead == Overhead.RANGE)
				|| (hit.attackStyle.isMagic() && overhead == Overhead.MAGIC)) {
			hit.block();
			return;
		}
		Item weapon = hit.attacker.player.getEquipment().get(Equipment.SLOT_WEAPON);
		if (!inFight)
			startFight();
		if (shieldUp) {
			if (weapon == null || !WeaponChecker.isDemonbaneOrAbyssal(weapon))
				hit.damage = (int) (hit.damage * (1 - SHIELD_REDUCTION));
		} else {
			// unshielded: the hit that brings the shield back up gets the slow-weapon bonus
			if (weapon != null && weapon.getDef().weaponType != null) {
				int speed = weapon.getDef().weaponType.attackTicks;
				double bonus = (speed * speed - 16) / 100.0;
				if (bonus > 0)
					hit.boostDamage(bonus);
			}
			raiseShield();
		}
	}

	private void postDamage(Hit hit) {
		if (hit.attackStyle == null || hit.damage <= 0)
			return;
		damageSinceSwap += hit.damage;
		if (damageSinceSwap >= PRAYER_SWAP_DAMAGE) {
			damageSinceSwap = 0;
			overhead = hit.attackStyle.isMelee() ? Overhead.MELEE : hit.attackStyle.isRanged() ? Overhead.RANGE : Overhead.MAGIC;
			showOverhead();
			pausedUntil = now + PRAYER_SWAP_PAUSE;
		}
	}

	private void startFight() {
		inFight = true;
		lowerShield(); // "the shield drops after the player's first attack"
		nextBomb = now + BOMB_EVERY;
		defencelessAt = now + DEFENCELESS_AFTER;
	}

	private void raiseShield() {
		shieldUp = true;
		npc.graphics(GFX_SHIELD_RESTORE);
	}

	private void lowerShield() {
		shieldUp = false;
		npc.graphics(GFX_SHIELD);
	}

	private void showOverhead() {
		switch (overhead) {
			case MAGIC -> npc.setHeadIcon(NPC.DefaultHeadIconIndex.ProtectFromMagic);
			case RANGE -> npc.setHeadIcon(NPC.DefaultHeadIconIndex.ProtectFromRanged);
			default -> npc.setHeadIcon(NPC.DefaultHeadIconIndex.ProtectFromMelee);
		}
	}

	/* Per tick */

	@Override
	public void process() {
		now++;
		if (overheadPending && npc.avatar() != null) {
			overheadPending = false;
			showOverhead();
		}
		if (!inFight)
			return;
		if (target == null || target.player == null || !target.player.isOnline()
				|| !target.getPosition().isWithinDistance(npc.getPosition(), 16)) {
			// forced out of combat: back to full health
			resetFight();
			restore();
			return;
		}
		if (!defenceless && now >= defencelessAt) {
			defenceless = true;
			npc.transform(FORM_EXTINGUISHED);
		}
	}

	private void resetFight() {
		inFight = false;
		defenceless = false;
		shieldUp = true;
		damageSinceSwap = 0;
		if (npc.getId() != FORM_BURNING)
			npc.transform(FORM_BURNING);
	}

	@Override
	public void follow() {
		follow(style == Style.MELEE ? 1 : 6);
	}

	@Override
	public boolean attack() {
		if (!(target instanceof Player player) || now < pausedUntil)
			return false;
		if (now >= nextBomb) {
			fireBomb(player);
			return true;
		}
		boolean inReach = withinDistance(1);
		Style use = style;
		if (use == Style.MELEE && !inReach)
			use = Random.get(1) == 0 ? Style.MAGIC : Style.RANGED; // "if they can't reach, random magic or ranged"
		switch (use) {
			case MELEE -> {
				npc.face(player);
				npc.animate(SEQ_MELEE);
				npc.graphics(GFX_MELEE);
				player.hit(new Hit(npc, AttackStyle.CRUSH).randDamage(MAX_HIT).delay(1));
			}
			case MAGIC -> {
				npc.face(player);
				npc.animate(SEQ_MAGIC);
				int duration = new Projectile(GFX_FIREBALL, 70, 31, 20, 40, 5, 12, 0).send(npc, player);
				Hit hit = new Hit(npc, AttackStyle.MAGIC).randDamage(MAX_HIT).clientDelay(duration);
				hit.postDamage(e -> player.graphics(GFX_FIREBALL_HIT));
				player.hit(hit);
			}
			case RANGED -> {
				npc.face(player);
				npc.animate(SEQ_RANGED);
				int duration = new Projectile(GFX_SHARD, 80, 31, 20, 40, 5, 12, 0).send(npc, player);
				Hit hit = new Hit(npc, AttackStyle.RANGED).randDamage(MAX_HIT).clientDelay(duration);
				hit.postDamage(e -> player.graphics(GFX_SHARD_HIT));
				player.hit(hit);
			}
		}
		return true;
	}

	/**
	 * Binds the player and turns off run; two bombs land, one where the player stood and one on a random walkable
	 * tile next to it, two ticks after the bind lets go. Then a new style, the shield drops and the flames relight.
	 */
	private void fireBomb(Player player) {
		nextBomb = now + BOMB_EVERY;
		npc.face(player);
		npc.animate(SEQ_BOMB);
		npc.graphics(GFX_BOMB_CAST);
		Position first = player.getPosition().copy();
		List<Position> around = new ArrayList<>();
		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				Position p = first.relative(dx, dy);
				Tile tile = Tile.get(p.getX(), p.getY(), p.getZ(), false);
				if ((dx != 0 || dy != 0) && (tile == null || tile.clipping == 0))
					around.add(p);
			}
		}
		Position second = around.isEmpty() ? first : around.get(Random.get(around.size() - 1));
		player.freeze(2, npc);
		VarPlayerRepository.RUNNING.set(player, 0);
		player.sendMessage("The demon's spell binds you!");
		int duration = new Projectile(GFX_BOMB_PROJ, 100, 0, 0, 120, 0, 40, 0).send(npc, first);
		new Projectile(GFX_BOMB_PROJ, 100, 0, 0, 120, 0, 40, 0).send(npc, second);
		int land = Math.max(4, (duration + 29) / 30); // bind (2 ticks) + 2 ticks to step off
		World.startEvent(e -> {
			e.delay(land);
			World.sendGraphics(GFX_BOMB_HIT, 0, 0, first);
			World.sendGraphics(GFX_BOMB_HIT, 0, 0, second);
			if (player.getPosition().equals(first) || player.getPosition().equals(second))
				player.hit(new Hit(npc).randDamage(BOMB_MAX).ignoreDefence().ignorePrayer());
		});
		// "after each fire bomb they always change combat style"; the shield drops; the flames come back
		Style old = style;
		do {
			style = Style.values()[Random.get(2)];
		} while (style == old);
		lowerShield();
		if (defenceless) {
			defenceless = false;
			npc.transform(FORM_BURNING);
		}
		defencelessAt = now + DEFENCELESS_AFTER;
	}

	@Override
	public boolean allowRetaliate(Entity attacker) {
		return true;
	}
}

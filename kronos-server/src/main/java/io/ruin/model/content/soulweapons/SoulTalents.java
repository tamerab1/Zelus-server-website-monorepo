package io.ruin.model.content.soulweapons;

import io.ruin.api.utils.Random;
import io.ruin.model.combat.AttackStyle;
import io.ruin.model.combat.Hit;
import io.ruin.model.combat.HitType;
import io.ruin.model.content.rivals.RivalsFairPlay;
import io.ruin.model.entity.Entity;
import io.ruin.model.entity.npc.NPC;
import io.ruin.model.entity.player.Player;
import io.ruin.model.item.Item;
import io.ruin.model.item.containers.Equipment;
import io.ruin.model.map.Projectile;
import io.ruin.model.stat.StatType;

/**
 * Soul Tree talent effects (2026-10-18), all 100% PvM: a talent only works while the player wields their OWN
 * Soulbound line, with that line's combat style, against a monster, and never in a Rivals match. Hooks:
 * <ul>
 * <li>{@link Hit#defend} -> {@link #onDefend}: Devastation crits, Executioner (after the accuracy roll, before the
 * damage is capped to the monster's hitpoints).</li>
 * <li>{@link Hit#finish} -> {@link #afterDamage}: Lifesteal, Cleave, Ricochet, Soul Siphon; and
 * {@link #deathWard} just before a monster's hit lands on the player.</li>
 * <li>PlayerCombat.updateLastAttack -> {@link #attackDelay}: Tempest.</li>
 * <li>SoulWeapons.onNpcKill -> {@link #onKill}: Arcane Surge.</li>
 * </ul>
 * The extra hits the talents make (splash, chain, blast) are flagged {@link Hit#soulEcho}, so they never trigger
 * talents themselves, and only reach monsters the player could attack anyway in a multi-combat area.
 */
public final class SoulTalents {

	/** Cleave / Ricochet / Arcane Surge: how far around the struck monster, and how many extra monsters at most. */
	private static final int CLEAVE_RANGE = 1, CLEAVE_TARGETS = 3, CHAIN_RANGE = 3, SURGE_RANGE = 2, SURGE_TARGETS = 4;
	private static final double CRIT_MULTIPLIER = 1.30, EXECUTE_BELOW = 0.35, CHAIN_DAMAGE = 0.50;
	private static final int GFX_CRIT = 6828, GFX_WARD = 6829, GFX_SURGE = 2127;
	private static final Projectile CHAIN_ARROW = new Projectile(1924, 30, 31, 0, 30, 6, 16, 11);

	private SoulTalents() {
	}

	// ---- per-rank values (also read by SoulTalent.describe) ----

	static int critChance(int rank) {
		return 3 * rank;
	}

	static int cleavePercent(int rank) {
		return 10 * rank;
	}

	static int lifestealPercent(int rank) {
		return 2 * rank;
	}

	static int tempestChance(int rank) {
		return 5 * rank;
	}

	static int ricochetChance(int rank) {
		return 6 * rank;
	}

	static int executionerPercent(int rank) {
		return 4 * rank;
	}

	static int siphonHpPercent(int rank) {
		return 2 * rank;
	}

	static int surgeChance(int rank) {
		return 10 * rank;
	}

	static int wardHpPercent(int rank) {
		return 10 + 4 * rank;
	}

	static int wardCooldownMinutes(int rank) {
		return 11 - rank;
	}

	// ---- gating ----

	/** The talent's rank, if it may act right now: own line wielded, no Rivals match. 0 otherwise. */
	static int rank(Player player, SoulTalent talent) {
		SoulWeaponData d = player.soulWeapon;
		if (d == null || d.weaponType != talent.line || RivalsFairPlay.active(player))
			return 0;
		Item weapon = player.getEquipment().get(Equipment.SLOT_WEAPON);
		if (weapon == null || !talent.line.isLook(weapon.getId()))
			return 0;
		Integer r = d.allocatedTalents.get(talent.name());
		return r == null ? 0 : Math.min(SoulTalent.MAX_RANK, r);
	}

	/** The style a line's talents answer to: a Blade swing, a Recurve shot, a Spire spell. */
	private static boolean lineStyle(SoulWeaponType line, AttackStyle style) {
		return switch (line) {
			case MELEE -> style.isMelee();
			case RANGED -> style.isRanged();
			case MAGIC -> style.isMagic();
		};
	}

	/** A player's own hit on a monster that talents may act on (no pets, cannon, echoes, poison or heals). */
	private static boolean eligible(Hit hit, Player player) {
		SoulWeaponData d = player.soulWeapon;
		return d != null && !hit.fromPet && !hit.soulEcho && hit.attackStyle != null && !hit.attackStyle.isCannon()
				&& (hit.type == HitType.DAMAGE || hit.type == HitType.MAX_HIT) && lineStyle(d.weaponType, hit.attackStyle);
	}

	private static boolean roll(int percent) {
		return percent > 0 && Random.get() * 100 < percent;
	}

	/** x rounded up with probability equal to its fraction, so small hits still heal on average. */
	private static int randomRound(double x) {
		int whole = (int) x;
		return whole + (Random.get() < x - whole ? 1 : 0);
	}

	// ---- hooks ----

	/** Hit.defend, after the accuracy roll: Devastation crits and Executioner raise a landed hit's damage. */
	public static void onDefend(Hit hit, Player player, NPC target) {
		if (hit.damage <= 0 || !eligible(hit, player))
			return;
		int crit = rank(player, SoulTalent.DEVASTATION);
		if (crit > 0 && roll(critChance(crit))) {
			hit.damage = (int) Math.ceil(hit.damage * CRIT_MULTIPLIER);
			target.graphics(GFX_CRIT, 60, 0);
		}
		int exec = rank(player, SoulTalent.EXECUTIONER);
		if (exec > 0 && target.getHp() < target.getMaxHp() * EXECUTE_BELOW)
			hit.damage = (int) Math.ceil(hit.damage * (1 + executionerPercent(exec) / 100.0));
	}

	/** Hit.finish, once the damage has landed: the on-hit talents. */
	public static void afterDamage(Hit hit, Player player, NPC target) {
		if (hit.damage <= 0 || !eligible(hit, player))
			return;
		int damage = hit.damage;
		int steal = rank(player, SoulTalent.LIFESTEAL);
		if (steal > 0)
			player.incrementHp(randomRound(damage * lifestealPercent(steal) / 100.0));
		int siphon = rank(player, SoulTalent.SOUL_SIPHON);
		if (siphon > 0) {
			player.incrementHp(randomRound(damage * siphonHpPercent(siphon) / 100.0));
			player.getStats().get(StatType.Prayer).restore(randomRound(damage * siphon / 100.0));
		}
		int cleave = rank(player, SoulTalent.CLEAVE);
		if (cleave > 0 && target.inMulti()) {
			int splash = randomRound(damage * cleavePercent(cleave) / 100.0);
			if (splash > 0) {
				int hits = 0;
				for (NPC other : nearby(player, target, CLEAVE_RANGE)) {
					other.hit(echo(player, hit.attackStyle, splash));
					if (++hits >= CLEAVE_TARGETS)
						break;
				}
			}
		}
		int chain = rank(player, SoulTalent.RICOCHET);
		if (chain > 0 && target.inMulti() && roll(ricochetChance(chain))) {
			int bounce = Math.max(1, (int) (damage * CHAIN_DAMAGE));
			for (NPC other : nearby(player, target, CHAIN_RANGE)) {
				CHAIN_ARROW.send(target, other);
				other.hit(echo(player, hit.attackStyle, bounce).delay(1));
				break;
			}
		}
	}

	/** PlayerCombat.updateLastAttack: Tempest sometimes takes a tick off a Recurve shot at a monster. */
	public static int attackDelay(Player player, Entity target, int ticks) {
		if (ticks < 3 || target == null || target.npc == null)
			return ticks;
		int tempest = rank(player, SoulTalent.TEMPEST);
		return tempest > 0 && roll(tempestChance(tempest)) ? ticks - 1 : ticks;
	}

	/** SoulWeapons.onNpcKill: Arcane Surge, a soul blast around the fallen monster. */
	static void onKill(Player player, NPC npc) {
		int surge = rank(player, SoulTalent.ARCANE_SURGE);
		if (surge <= 0 || !npc.inMulti() || !roll(surgeChance(surge)))
			return;
		int max = SoulWeapons.spireMaxHit(player);
		int hits = 0;
		for (NPC other : nearby(player, npc, SURGE_RANGE)) {
			other.graphics(GFX_SURGE, 60, 0);
			other.hit(echo(player, AttackStyle.MAGIC, Random.get(max / 2, max)).delay(1));
			if (++hits >= SURGE_TARGETS)
				break;
		}
	}

	/**
	 * Hit.finish, just before a monster's hit lands on a player: Death Ward. If the hit would kill a Spire wielder whose
	 * ward is ready, it leaves them on the ward's share of their hitpoints instead.
	 */
	public static void deathWard(Hit hit, Entity target) {
		Player player = target.player;
		if (player == null || hit.attacker == null || hit.attacker.npc == null || hit.damage < player.getHp())
			return;
		SoulWeaponData d = player.soulWeapon;
		int ward = rank(player, SoulTalent.DEATH_WARD);
		if (ward <= 0 || System.currentTimeMillis() < d.deathWardReadyAt)
			return;
		d.deathWardReadyAt = System.currentTimeMillis() + wardCooldownMinutes(ward) * 60_000L;
		// the player ends on exactly the ward's share of their hitpoints: the hit takes them down to it, or a heal up
		int saved = Math.max(1, player.getMaxHp() * wardHpPercent(ward) / 100);
		int hp = player.getHp();
		hit.damage = Math.max(0, hp - saved);
		if (hp < saved)
			player.incrementHp(saved - hp);
		player.graphics(GFX_WARD, 0, 0);
		player.sendMessage(SoulWeapons.TAG + SoulWeapons.HIGHLIGHT + "Death Ward flares and pulls your soul back from the brink!</col>"
				+ " It recharges in " + wardCooldownMinutes(ward) + " minutes.");
	}

	// ---- helpers ----

	/** Monsters within range of the struck one that the player may hit (nearest first is not needed: up to a cap). */
	private static java.util.List<NPC> nearby(Player player, NPC around, int range) {
		java.util.List<NPC> found = new java.util.ArrayList<>();
		for (NPC npc : around.localNpcs()) {
			if (npc == null || npc == around || npc.getDef().ignoreMultiCheck || npc.getHp() <= 0 || npc.dead())
				continue;
			if (!npc.getPosition().isWithinDistance(around.getPosition(), range))
				continue;
			if (!player.getCombat().canAttack(npc, false))
				continue;
			found.add(npc);
		}
		return found;
	}

	/** A talent's extra hit: fixed damage, no accuracy roll, and it never triggers talents itself. */
	private static Hit echo(Player player, AttackStyle style, int damage) {
		Hit hit = new Hit(player, style, null).fixedDamage(damage).ignoreDefence();
		hit.soulEcho = true;
		return hit;
	}
}

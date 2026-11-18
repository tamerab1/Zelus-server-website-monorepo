package io.ruin.model.combat.special.melee;

import io.ruin.Server;
import io.ruin.cache.ObjType;
import io.ruin.model.combat.AttackStyle;
import io.ruin.model.combat.AttackType;
import io.ruin.model.combat.Hit;
import io.ruin.model.combat.HitType;
import io.ruin.model.combat.special.Special;
import io.ruin.model.entity.Entity;
import io.ruin.model.entity.player.Player;

/**
 * Duskfall Ward (50%), the Duskwarden blade and its (or) version (user 2026-11-18: "add a special attack ... unique and
 * clean"): a shadow slash with +35% accuracy and +15% damage. Half of the damage it deals (at most 30) becomes a dusk ward
 * that soaks up the damage you take for the next 25 ticks (15 seconds); a new ward replaces the old one. The ward is
 * applied in PlayerCombat.postDefend through {@link #absorb}.
 */
public class DuskwardenBlade implements Special {

	public static final int BLADE = 42702, BLADE_OR = 42810;
	private static final int ANIM = 2890, GFX_SWING = 483, GFX_IMPACT = 383, WARD_TICKS = 25, WARD_CAP = 30;
	private static final String WARD = "DUSK_WARD", WARD_END = "DUSK_WARD_END";

	@Override
	public boolean accept(ObjType def, String name) {
		return def.id == BLADE || def.id == BLADE_OR;
	}

	@Override
	public boolean handle(Player player, Entity victim, AttackStyle attackStyle, AttackType attackType, int maxDamage) {
		player.animate(ANIM);
		player.graphics(GFX_SWING, 96, 0);
		Hit hit = new Hit(player, attackStyle, attackType).randDamage(maxDamage).boostAttack(0.35).boostDamage(0.15)
			.setAttackWeapon(ObjType.get(BLADE));
		hit.postDamage(t -> {
			t.graphics(GFX_IMPACT, 60, 0);
			if (hit.type != HitType.DAMAGE || hit.damage <= 0)
				return;
			int ward = Math.min(WARD_CAP, Math.max(1, hit.damage / 2));
			player.set(WARD, ward);
			player.set(WARD_END, Server.currentTick() + WARD_TICKS);
			player.sendFilteredMessage("<col=6a3fa0>A dusk ward gathers around you (" + ward + ").</col>");
		});
		victim.hit(hit);
		return true;
	}

	/** soaks {@code hit} with the player's dusk ward, if one is up */
	public static void absorb(Player player, Hit hit) {
		Integer ward = player.get(WARD);
		if (ward == null || ward <= 0 || hit.damage <= 0)
			return;
		Long end = player.get(WARD_END);
		if (end == null || Server.currentTick() > end) {
			player.remove(WARD);
			return;
		}
		int soaked = Math.min(ward, hit.damage);
		hit.damage -= soaked;
		int left = ward - soaked;
		if (left <= 0) {
			player.remove(WARD);
			player.sendFilteredMessage("<col=6a3fa0>Your dusk ward fades.</col>");
		} else
			player.set(WARD, left);
	}

	@Override
	public int getDrainAmount() {
		return 50;
	}
}

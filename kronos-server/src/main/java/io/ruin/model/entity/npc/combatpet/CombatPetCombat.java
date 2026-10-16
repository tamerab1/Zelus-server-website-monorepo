package io.ruin.model.entity.npc.combatpet;

import io.ruin.Server;
import io.ruin.api.utils.Random;
import io.ruin.model.combat.AttackStyle;
import io.ruin.model.combat.CombatUtils;
import io.ruin.model.combat.Hit;
import io.ruin.model.entity.Entity;
import io.ruin.model.entity.npc.NPC;
import io.ruin.model.entity.npc.PetNPC;
import io.ruin.model.entity.player.Player;
import io.ruin.model.map.route.routes.DumbRoute;
import io.ruin.model.map.Tile;
import io.ruin.model.map.Position;
import io.ruin.model.map.route.routes.ProjectileRoute;

/**
 * Attack loop for combat pets: range/approach, attack cooldown, special rolls, and building the
 * (capped, owner-credited) hits every profile uses.
 */
public final class CombatPetCombat {

	/** Hard ceiling on any single pet hit, whatever a profile asks for. */
	public static final int MAX_PET_HIT = 15;

	/** Pet accuracy is clamped so it never fully whiffs on high-defence bosses nor always hits trash. */
	private static final double MIN_ACCURACY = 0.15, MAX_ACCURACY = 0.85;

	private CombatPetCombat() {
	}

	static void tick(CombatPet ctx) {
		PetNPC pet = ctx.pet;
		NPC target = ctx.target;
		CombatPetProfile profile = ctx.profile;

		// stand within OWNER_REACH tiles of the owner, never on the owner's or the target's tiles
		if (!goodSpot(pet.getAbsX(), pet.getAbsY(), pet, ctx.owner, target, profile.attackRange)) {
			Position spot = fightingSpot(pet, ctx.owner, target, profile.attackRange);
			if (spot != null) {
				pet.getRouteFinder().routeAbsolute(spot.getX(), spot.getY());
				return;
			}
			// nowhere near the owner to hit from: stay beside the owner instead of walking onto anything
			if (gap(pet, ctx.owner) == 0 || gap(pet, ctx.owner) > OWNER_REACH)
				DumbRoute.step(pet, ctx.owner, 1);
			return;
		}
		pet.getMovement().reset();
		pet.face(target);

		// everything runs on the PET's own attack clock (its attack speed), never the owner's
		long now = Server.currentTick();
		if (now < ctx.nextAttackTick)
			return;
		ctx.nextAttackTick = now + profile.attackTicks;

		if (profile.specialIntervalTicks > 0 && ctx.ticksSinceLastSpecial >= profile.specialIntervalTicks) {
			ctx.ticksSinceLastSpecial = 0;
			profile.specialAttack(pet, ctx.owner, target);
			return;
		}
		ctx.ticksSinceLastSpecial += profile.attackTicks; // pet attack time since the last special
		if (profile.specialChance > 0 && Random.get() < profile.specialChance)
			profile.specialAttack(pet, ctx.owner, target);
		else
			profile.basicAttack(pet, ctx.owner, target);
	}

	/** How far from its owner a fighting pet may stand. */
	private static final int OWNER_REACH = 2;

	/** True when standing at (x, y) the pet is near its owner, overlaps nobody and can hit the target. */
	private static boolean goodSpot(int x, int y, PetNPC pet, Entity owner, NPC target, int range) {
		int size = pet.getSize(), z = pet.getHeight();
		int toOwner = gap(x, y, size, owner), toTarget = gap(x, y, size, target);
		if (toOwner == 0 || toOwner > OWNER_REACH || toTarget == 0)
			return false;
		if (range == 1)
			return toTarget == 1;
		return toTarget <= range && ProjectileRoute.allow(new Position(x, y, z), size, target);
	}

	/** The free tile near the owner the pet can fight from, closest to where the pet already is. */
	private static Position fightingSpot(PetNPC pet, Entity owner, NPC target, int range) {
		Position best = null;
		int bestCost = Integer.MAX_VALUE, z = pet.getHeight(), size = pet.getSize();
		for (int dx = -OWNER_REACH - size + 1; dx <= OWNER_REACH + owner.getSize() - 1; dx++) {
			for (int dy = -OWNER_REACH - size + 1; dy <= OWNER_REACH + owner.getSize() - 1; dy++) {
				int x = owner.getAbsX() + dx, y = owner.getAbsY() + dy;
				if (!goodSpot(x, y, pet, owner, target, range) || !free(x, y, z, size))
					continue;
				int cost = Math.max(Math.abs(x - pet.getAbsX()), Math.abs(y - pet.getAbsY())) * 4 + gap(x, y, size, owner);
				if (cost < bestCost) {
					bestCost = cost;
					best = new Position(x, y, z);
				}
			}
		}
		return best;
	}

	private static boolean free(int x, int y, int z, int size) {
		for (int i = 0; i < size; i++)
			for (int j = 0; j < size; j++) {
				Tile tile = Tile.get(x + i, y + j, z);
				if (tile != null && !tile.isFloorFree())
					return false;
			}
		return true;
	}

	/** gap() for a footprint at (x, y). */
	private static int gap(int x, int y, int size, Entity b) {
		int dx = Math.max(0, Math.max(x - (b.getAbsX() + b.getSize() - 1), b.getAbsX() - (x + size - 1)));
		int dy = Math.max(0, Math.max(y - (b.getAbsY() + b.getSize() - 1), b.getAbsY() - (y + size - 1)));
		return Math.max(dx, dy);
	}

	private static boolean inRange(PetNPC pet, NPC target, int range) {
		int gap = gap(pet, target);
		if (gap > range)
			return false;
		if (range == 1)
			return gap == 1;
		return ProjectileRoute.allow(pet, target);
	}

	/**
	 * Tiles between two entities' footprints (0 = overlapping, 1 = adjacent incl. diagonals).
	 */
	public static int gap(Entity a, Entity b) {
		int dx = Math.max(0, Math.max(a.getAbsX() - (b.getAbsX() + b.getSize() - 1), b.getAbsX() - (a.getAbsX() + a.getSize() - 1)));
		int dy = Math.max(0, Math.max(a.getAbsY() - (b.getAbsY() + b.getSize() - 1), b.getAbsY() - (a.getAbsY() + a.getSize() - 1)));
		return Math.max(dx, dy);
	}

	/**
	 * Rolls a pet hit against {@code target}: pet accuracy vs the target's real defence roll for
	 * {@code style}, then 0..maxHit (capped at {@link #MAX_PET_HIT}). Returns null for player targets.
	 */
	static Hit roll(Player owner, Entity target, AttackStyle style, int maxHit, int accuracyRoll, double defenceMultiplier) {
		if (target instanceof Player)
			return null;
		int max = Math.min(maxHit, MAX_PET_HIT);
		double defenceRoll = CombatUtils.getDefenceBonus(target, style) * defenceMultiplier;
		double chance = accuracyRoll > defenceRoll
				? 1D - (defenceRoll + 2D) / (2D * (accuracyRoll + 1D))
				: accuracyRoll / (2D * (defenceRoll + 1D));
		chance = Math.max(MIN_ACCURACY, Math.min(MAX_ACCURACY, chance));
		int damage = Random.get() < chance ? Random.get(1, max) : 0;
		return fixed(owner, style, damage);
	}

	/**
	 * A pre-rolled pet hit credited to the owner (kill credit/loot) but flagged {@link Hit#fromPet} so the
	 * owner's weapon effects, hit listeners and xp are skipped.
	 */
	static Hit fixed(Player owner, AttackStyle style, int damage) {
		return new Hit(owner, style)
				.fromPet()
				.ignoreDefence()
				.fixedDamage(Math.min(damage, MAX_PET_HIT));
	}

	/**
	 * Applies a pet hit. Single choke point that refuses anything but a live NPC.
	 */
	static int apply(Entity target, Hit hit) {
		if (hit == null || target instanceof Player || !(target instanceof NPC npc))
			return 0;
		if (npc instanceof PetNPC || npc.isRemoved() || npc.getCombat() == null || npc.getCombat().isDead())
			return 0;
		return npc.hit(hit);
	}

}

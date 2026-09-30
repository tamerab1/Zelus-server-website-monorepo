package io.ruin.model.activities.bosses.perkers;

import io.ruin.model.combat.AttackStyle;
import io.ruin.model.entity.npc.NPCCombat;
import io.ruin.model.map.Graphic;
import io.ruin.model.map.Position;
import io.ruin.model.map.Projectile;
import io.ruin.model.map.route.routes.DumbRoute;
import io.ruin.utility.Misc;

// Shared handler for the three Perkers (2x-scaled clones of the Arzinian Avatars 1233/1231/1228)
// and Aurelius the Gilded (3x-scaled gold recolor of the ToA Baboon Thrower 11713). Each one uses
// a single attack style, picked by npc id. Stats/animations live in Perkers.json.
public class Perker extends NPCCombat {

	private static final int MAGE_PERKER = 30568;
	private static final int RANGE_PERKER = 30569;
	private static final int MELEE_PERKER = 30570;
	private static final int AURELIUS = 30571;

	// Ahrim's fire wave + its impact splash.
	private static final Projectile FIRE_WAVE = new Projectile(156, 70, 31, 51, 56, 10, 16, 64);
	private static final Graphic FIRE_WAVE_HIT = Graphic.builder().id(157).height(100).build();
	// Karil's bolt, raised to leave from a size-2 npc's hands.
	private static final Projectile RANGE_BOLT = new Projectile(27, 70, 36, 41, 51, 5, 5, 11);
	// The real ToA Baboon Thrower projectile (TOA_BABOON_RANGED_TRAVEL_1).
	private static final Projectile BABOON_THROW = new Projectile(2242, 70, 31, 41, 56, 10, 16, 64);

	@Override
	public void init() {
	}

	@Override
	public void follow() {
		// Aurelius never moves -- it only throws at players within range of its spawn tile.
		if (npc.getId() == AURELIUS)
			return;
		// Perkers only chase a short way from where they spawned (melee 4 tiles, mage/range 2):
		// the chase destination is clamped into that box, so every step stays inside it.
		boolean melee = npc.getId() == MELEE_PERKER;
		int leash = melee ? 4 : 2;
		if (melee ? inMeleeRange() : withinDistance(8))
			return;
		Position spawn = npc.getSpawnPosition();
		int destX = Math.max(spawn.getX() - leash, Math.min(spawn.getX() + leash, target.getAbsX()));
		int destY = Math.max(spawn.getY() - leash, Math.min(spawn.getY() + leash, target.getAbsY()));
		if (npc.getAbsX() != destX || npc.getAbsY() != destY)
			DumbRoute.step(npc, destX, destY);
	}

	@Override
	public boolean attack() {
		switch (npc.getId()) {
			case MAGE_PERKER:
				if (!withinDistance(8))
					return false;
				projectileAttack(FIRE_WAVE, info.attack_animation, FIRE_WAVE_HIT, AttackStyle.MAGIC, info.max_damage);
				return true;
			case RANGE_PERKER:
				if (!withinDistance(8))
					return false;
				projectileAttack(RANGE_BOLT, info.attack_animation, AttackStyle.RANGED, info.max_damage);
				return true;
			case AURELIUS:
				if (!withinDistance(8))
					return false;
				projectileAttack(BABOON_THROW, info.attack_animation, AttackStyle.RANGED, info.max_damage);
				return true;
			default:
				if (!inMeleeRange())
					return false;
				basicAttack();
				return true;
		}
	}

	// withinDistance(1) can't be used here: its distance-1 check only verifies adjacency through
	// the route finder's routeEntity, which the leashed DumbRoute.step(x, y) above never sets, so it
	// passed from any range. Instead: the npc tile closest to the target must touch it (diagonals
	// included -- a size-2 npc stepping diagonally at a corner-adjacent player would overlap them).
	private boolean inMeleeRange() {
		if (target == null)
			return false;
		Position closest = Misc.getClosestPosition(npc, target);
		int dx = Math.abs(closest.getX() - target.getAbsX());
		int dy = Math.abs(closest.getY() - target.getAbsY());
		return Math.max(dx, dy) == 1 && npc.getHeight() == target.getHeight();
	}

	@Override
	public void process() {
	}
}

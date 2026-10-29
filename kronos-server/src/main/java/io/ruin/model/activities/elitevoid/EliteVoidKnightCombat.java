package io.ruin.model.activities.elitevoid;

import io.ruin.model.combat.AttackStyle;
import io.ruin.model.entity.npc.NPCCombat;
import io.ruin.model.map.Graphic;
import io.ruin.model.map.Projectile;

/**
 * An Elite Void Knight (Elite Void island): one style each, by id. The melee knight lashes with its whip, the ranger
 * fires dragon arrows from its magic shortbow, the mage casts Fire Wave through its void knight mace (Ahrim's spell
 * graphics). Stats and max hits: npcs/combat/EliteVoid.json.
 */
public class EliteVoidKnightCombat extends NPCCombat {

	private static final Projectile ARROW = new Projectile(1120, 40, 36, 41, 51, 5, 15, 11);
	private static final Projectile FIRE_WAVE = new Projectile(156, 43, 31, 51, 56, 10, 16, 64);
	private static final int WHIP = 1658, BOW = 426, CAST = 1167;

	@Override
	public void init() {
	}

	@Override
	public void follow() {
		follow(npc.getId() == EliteVoidIsland.MELEE ? 1 : 8);
	}

	@Override
	public boolean attack() {
		switch (npc.getId()) {
			case EliteVoidIsland.RANGER -> {
				if (!withinDistance(8))
					return false;
				npc.graphics(1111, 96, 0); // the dragon arrow's draw
				projectileAttack(ARROW, BOW, AttackStyle.RANGED, info.max_damage);
			}
			case EliteVoidIsland.MAGE -> {
				if (!withinDistance(8))
					return false;
				npc.graphics(155, 92, 0);
				projectileAttack(FIRE_WAVE, CAST, Graphic.builder().id(157).height(92).build(), AttackStyle.MAGIC, info.max_damage);
			}
			default -> {
				if (!withinDistance(1))
					return false;
				basicAttack(WHIP, AttackStyle.SLASH, info.max_damage);
			}
		}
		return true;
	}

	@Override
	public void process() {
	}
}

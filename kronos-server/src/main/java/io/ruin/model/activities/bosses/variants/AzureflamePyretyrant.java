package io.ruin.model.activities.bosses.variants;

import io.ruin.api.utils.Random;
import io.ruin.model.World;
import io.ruin.model.combat.AttackStyle;
import io.ruin.model.combat.Hit;
import io.ruin.model.entity.player.Player;
import io.ruin.model.map.Position;

import java.util.ArrayList;
import java.util.List;

/**
 * Azureflame Pyretyrant (Bloodthirsty Infernal pyrelord clone, blue flame). Heavy crush melee brawler.
 * Special "Azure Cinderfall": blue fireballs rain onto the target's tile and the tiles around it; anyone still
 * standing on one when it lands is burned, so players have to move off the marked tiles.
 */
public class AzureflamePyretyrant extends BossVariant {

	private static final int GFX_FIREBALL = 6685, GFX_BURST = 6686;
	private static final int ANIM_ATTACK = 1582;
	private static final int EXTRA_TILES = 4;

	@Override
	protected int attackRange() {
		return 1;
	}

	@Override
	protected int specialEvery() {
		return 6;
	}

	@Override
	protected boolean normalAttack() {
		if (!inMeleeRange())
			return false;
		basicAttack(ANIM_ATTACK, AttackStyle.CRUSH, info.max_damage);
		return true;
	}

	@Override
	protected boolean specialAttack() {
		if (distanceTo(target) > 10)
			return false;
		npc.animate(ANIM_ATTACK);
		npc.forceText("Burn in azure flame!");
		Position centre = target.getPosition().copy();
		List<Position> tiles = new ArrayList<>();
		tiles.add(centre);
		for (int i = 0; i < EXTRA_TILES; i++)
			tiles.add(centre.relative(Random.get(-2, 2), Random.get(-2, 2)));
		npc.addEvent(e -> {
			int longest = 0;
			for (Position tile : tiles)
				longest = Math.max(longest, projectile(GFX_FIREBALL).send(npc, tile));
			for (Position tile : tiles)
				World.sendGraphics(GFX_BURST, 0, longest, tile);
			e.delay(3);
			for (Player p : npc.localPlayers()) {
				for (Position tile : tiles) {
					if (p.getPosition().equals(tile)) {
						p.hit(new Hit(npc).randDamage(25, 40));
						break;
					}
				}
			}
		});
		return true;
	}
}

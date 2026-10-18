package io.ruin.model.content;

import io.ruin.model.combat.Hit;
import io.ruin.model.combat.HitType;
import io.ruin.model.entity.Entity;
import io.ruin.model.item.Item;
import io.ruin.model.item.containers.Equipment;

import java.util.Map;

/**
 * Azure Fang (item 31848, new customs 2026-10-18): every melee hit that lands throws a small cyan sparkle on the target
 * (spotanim 4423, a shrunk cyan copy of the Redemption sparkle: .dev/cache-restore-tool azure_fang_gfx.txt). The
 * sword's blade core carries the animated azure texture 261 (azure_fang_models.txt). Called from Hit.finish.
 * The Soulbound blade looks share it (2026-10-18 r8): the Ascended blade is the Fang's mesh and throws the same cyan
 * sparkle; the base blade a violet one (4426) and the Empowered blade a crimson one (4427), matching their animated
 * textures 262 / 263 (soulblade_*.txt).
 */
public final class AzureFang {
	public static final int ITEM = 31848;
	private static final int GFX_SPARKLE = 4423, GFX_HEIGHT = 60;
	private static final Map<Integer, Integer> SPARKLES = Map.of(
			ITEM, GFX_SPARKLE,
			31824, 4426, // Soulbound blade (base)
			31826, 4427, // Empowered soulbound blade
			31828, GFX_SPARKLE); // Ascended soulbound blade (the Fang's mesh)

	private AzureFang() {
	}

	public static void onHit(Hit hit, Entity target) {
		if (hit.fromPet || hit.damage <= 0 || hit.attacker == null || hit.attacker.player == null || hit.attackStyle == null
				|| !hit.attackStyle.isMelee() || (hit.type != HitType.DAMAGE && hit.type != HitType.MAX_HIT))
			return;
		Item weapon = hit.attacker.player.getEquipment().get(Equipment.SLOT_WEAPON);
		Integer gfx = weapon == null ? null : SPARKLES.get(weapon.getId());
		if (gfx != null)
			target.graphics(gfx, GFX_HEIGHT, 0);
	}
}

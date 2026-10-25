package io.ruin.model.combat.special;

import io.ruin.model.entity.player.Player;
import io.ruin.model.item.containers.Equipment;
import io.ruin.model.map.Projectile;

import java.util.Map;
import java.util.Set;

/**
 * Wilderness Forge weapons use obsidian/blood-crimson copies of their source weapon's special-attack effects
 * (spotanims 4400-4415, .dev/cache-restore-tool/pk_obsidian_gfx.txt).
 */
public final class ForgeSpecGfx {

	/** Bloodforged godsword .. Riftbreaker: every forge weapon with a special attack. */
	private static final Set<Integer> FORGE_WEAPONS = Set.of(42324, 42326, 42328, 42330, 42332, 42334, 42336, 42338,
			42374, 42376, 42378, 42380);

	private static final Map<Integer, Integer> CRIMSON = Map.ofEntries(
			Map.entry(1211, 6807), // AGS
			Map.entry(1212, 6808), // BGS
			Map.entry(1171, 6809), // dragon claws
			Map.entry(252, 6810), // dragon dagger
			Map.entry(347, 6811), // dragon scimitar
			Map.entry(1292, 6812), // dragon warhammer
			Map.entry(340, 6813), // granite maul
			Map.entry(2363, 6814), // voidwaker
			Map.entry(301, 6815), // armadyl crossbow bolt
			Map.entry(344, 6816), // ballista impact
			Map.entry(1099, 6817), // dark bow dragon heads
			Map.entry(1101, 6818), // dark bow smoke
			Map.entry(1100, 6819), // dark bow dragon impact
			Map.entry(1103, 6820), // dark bow smoke impact
			Map.entry(698, 6821), // dragon crossbow bolt
			Map.entry(1466, 6822)); // dragon crossbow blast

	private ForgeSpecGfx() {
	}

	public static boolean wieldsForgeWeapon(Player player) {
		return FORGE_WEAPONS.contains(player.getEquipment().getId(Equipment.SLOT_WEAPON));
	}

	/** The crimson copy of {@code gfx} while a forge weapon is wielded, otherwise {@code gfx}. */
	public static int of(Player player, int gfx) {
		return wieldsForgeWeapon(player) ? CRIMSON.getOrDefault(gfx, gfx) : gfx;
	}

	public static Projectile of(Player player, Projectile base) {
		int gfx = of(player, base.getGfxId());
		if (gfx == base.getGfxId())
			return base;
		return new Projectile(gfx, base.getStartHeight(), base.getEndHeight(), base.getDelay(),
				base.getDurationStart(), base.getDurationIncrement(), base.getCurve(), base.getIdk());
	}

}

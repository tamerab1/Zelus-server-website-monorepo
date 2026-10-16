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

	/** Tempest godsword .. Riftbreaker: every forge weapon with a special attack. */
	private static final Set<Integer> FORGE_WEAPONS = Set.of(31524, 31526, 31528, 31530, 31532, 31534, 31536, 31538,
			31574, 31576, 31578, 31580);

	private static final Map<Integer, Integer> CRIMSON = Map.ofEntries(
			Map.entry(1211, 4400), // AGS
			Map.entry(1212, 4401), // BGS
			Map.entry(1171, 4402), // dragon claws
			Map.entry(252, 4403), // dragon dagger
			Map.entry(347, 4404), // dragon scimitar
			Map.entry(1292, 4405), // dragon warhammer
			Map.entry(340, 4406), // granite maul
			Map.entry(2363, 4407), // voidwaker
			Map.entry(301, 4408), // armadyl crossbow bolt
			Map.entry(344, 4409), // ballista impact
			Map.entry(1099, 4410), // dark bow dragon heads
			Map.entry(1101, 4411), // dark bow smoke
			Map.entry(1100, 4412), // dark bow dragon impact
			Map.entry(1103, 4413), // dark bow smoke impact
			Map.entry(698, 4414), // dragon crossbow bolt
			Map.entry(1466, 4415)); // dragon crossbow blast

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

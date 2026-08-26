package economy.protection.pvm;

import io.ruin.HooksV2.Result;
import io.ruin.model.content.PvmPoints;
import io.ruin.model.entity.npc.NPC;
import io.ruin.model.entity.npc.NPCCombat;
import io.ruin.model.entity.player.Player;

/**
 * PVM Points engine: combat-level/tier based points for NPC kills, exclusive to PVM Mode
 * accounts (regular PVM + all ironman tiers) — PVP Mode accounts never earn PVM Points.
 *
 * <p>Named bosses already covered by the legacy {@link PvmPoints#addPoints} table (fired
 * separately at the same {@link NPCCombat.Hook.Death} site, see {@code NPCCombat.startDeath})
 * are skipped here to avoid double-awarding — this manager only covers the long tail of
 * generic/slayer-task monsters that were never added to that table.</p>
 *
 * <p>Kills inside the Wilderness get a flat +15% bonus, applied after tiering.</p>
 */
public final class PvmPointsManager {

	private PvmPointsManager() {
	}

	/** Multiplier applied to points earned from a kill inside the Wilderness. */
	static final double WILDERNESS_BONUS_MULTIPLIER = 1.15;

	public static void register() {
		NPCCombat.hooks.register(NPCCombat.Hook.Death.class, PvmPointsManager::onNpcDeath);
	}

	private static Result onNpcDeath(NPCCombat.Hook.Death ctx) {
		grantForKill(ctx.killer(), ctx.npc());
		return Result.Pass;
	}

	/** Grants tier-based PVM Points for a single NPC kill, if eligible. */
	public static void grantForKill(Player player, NPC npc) {
		int points = pointsForKill(player, npc);
		if (points <= 0) return;
		player.PvmPoints += points;
		player.sendMessage("<shad=000000><col=BA0000>[PVM Points]</shad> <col=0C2AC1>You earn <col=C10CB9>(+" + points + ") pvm points <col=0C2AC1>after killing <col=0C2AC1>"
			+ npc.getDef().name + "<col=0C2AC1>! You now have <col=0C2AC1>" + player.PvmPoints + " PVM Points<col=0C2AC1>!");
	}

	/**
	 * Computes the points a kill would award without applying them. Package-visible so tests
	 * can exercise the tiering/wilderness/gating math directly.
	 */
	static int pointsForKill(Player player, NPC npc) {
		if (player == null || npc == null || npc.getDef() == null) return 0;
		if (!player.isPvmMode()) return 0; // PVP Mode accounts never earn PVM Points
		if (PvmPoints.hasNamedPoints(npc)) return 0; // already covered by the legacy boss table

		int base = pointsForTier(npc.getDef().combatLevel);
		if (base <= 0) return 0;

		return applyWildernessBonus(base, player.wildernessLevel > 0);
	}

	/**
	 * Flat combat-level tiers for the generic (non-named-boss) point award.
	 * Anything under level 50 is treated as trivial and awards nothing.
	 */
	static int pointsForTier(int combatLevel) {
		if (combatLevel >= 350) return 15; // elite-tier (raid bosses, end-game uniques)
		if (combatLevel >= 200) return 8;  // hard-tier
		if (combatLevel >= 100) return 4;  // medium-tier
		if (combatLevel >= 50)  return 1;  // easy-tier / slayer trash with real HP
		return 0;
	}

	static int applyWildernessBonus(int points, boolean inWilderness) {
		if (!inWilderness) return points;
		return (int) Math.round(points * WILDERNESS_BONUS_MULTIPLIER);
	}
}

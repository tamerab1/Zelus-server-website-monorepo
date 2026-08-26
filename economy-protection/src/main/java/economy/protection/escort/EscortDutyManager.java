package economy.protection.escort;

import clanchat.Attributes;
import io.ruin.HooksV2.Result;
import io.ruin.model.World;
import io.ruin.model.entity.npc.NPC;
import io.ruin.model.entity.npc.NPCCombat;
import io.ruin.model.entity.player.Player;

/**
 * Protection / Escort Duty (experimental): a PVP Mode account standing watch near a PVM Mode
 * Friends-Chat-mate in the Wilderness earns passive PKP when that PVM Mode account kills a
 * Wilderness boss — reward for "zone protection" against opposing PKers.
 *
 * <p><b>Scope decision:</b> awards trigger on the wilderness-boss kill event
 * ({@link NPCCombat.Hook.Death}), not on each individual hit landed on the boss. There is no
 * existing per-damage hook anywhere in {@code NPCCombat}/{@code Combat} to tap safely, and
 * adding one is a materially larger, riskier core-combat change than this experimental feature
 * warrants in one pass. If true per-hit ticks are wanted later, that hook would need adding to
 * {@code NPCCombat} the same way {@link NPCCombat.Hook.Death} was.</p>
 *
 * <p><b>"Same Clan/Friends Chat/Party":</b> only Friends Chat membership is checked. This
 * codebase has no unified Clan Chat distinct from Friends Chat, and no cross-content Party —
 * raid parties (ToB/CoX/ToA) are raid-specific and unrelated to Wilderness activity.</p>
 */
public class EscortDutyManager {

	static final int ESCORT_RADIUS_TILES = 15;

	/** Anti-AFK/dual-box window: the escort must have attacked or been attacked within this many ticks. */
	static final int ACTIVITY_WINDOW_TICKS = 25; // ~15s at 600ms/tick

	static final int ESCORT_PKP_PER_PROTECTED_KILL = 5;

	private EscortDutyManager() {
	}

	public static void register() {
		NPCCombat.hooks.register(NPCCombat.Hook.Death.class, EscortDutyManager::onNpcDeath);
	}

	private static Result onNpcDeath(NPCCombat.Hook.Death ctx) {
		awardEscortsFor(ctx.killer(), ctx.npc());
		return Result.Pass;
	}

	/** Awards passive escort PKP to every eligible nearby PVP Mode escort of {@code pvmKiller}. */
	public static void awardEscortsFor(Player pvmKiller, NPC npc) {
		if (pvmKiller == null || npc == null || npc.getDef() == null) return;
		if (!pvmKiller.isPvmMode()) return;
		if (pvmKiller.wildernessLevel <= 0) return;

		for (Player nearby : World.players()) {
			if (nearby == pvmKiller) continue;
			if (isEligibleEscort(nearby, pvmKiller)) {
				nearby.updatePKPoints(ESCORT_PKP_PER_PROTECTED_KILL);
				nearby.sendMessage("[Escort Duty] +" + ESCORT_PKP_PER_PROTECTED_KILL
					+ " PKP for protecting " + pvmKiller.getName() + "'s kill of " + npc.getDef().name + ".");
			}
		}
	}

	/**
	 * True if {@code escort} currently qualifies to earn escort points for {@code pvmPlayer}:
	 * PVP Mode, same Friends Chat, both in the Wilderness, within {@link #ESCORT_RADIUS_TILES}
	 * tiles, and actively engaged in combat themselves (not AFK/dual-boxed).
	 */
	static boolean isEligibleEscort(Player escort, Player pvmPlayer) {
		if (escort == null || pvmPlayer == null) return false;
		if (!escort.isPvpMode()) return false;
		if (!pvmPlayer.isPvmMode()) return false;
		if (escort.wildernessLevel <= 0 || pvmPlayer.wildernessLevel <= 0) return false;
		if (!escort.getPosition().isWithinDistance(pvmPlayer.getPosition(), ESCORT_RADIUS_TILES)) return false;
		if (!sameFriendsChat(escort, pvmPlayer)) return false;
		return isActivelyEngaged(escort);
	}

	static boolean sameFriendsChat(Player a, Player b) {
		String fcA = Attributes.clan(a).joinedName;
		String fcB = Attributes.clan(b).joinedName;
		return fcA != null && !fcA.isEmpty() && fcA.equalsIgnoreCase(fcB);
	}

	/** Anti-AFK/dual-box gate: the escort must show their own recent combat activity. */
	static boolean isActivelyEngaged(Player player) {
		return player.getCombat().isAttacking(ACTIVITY_WINDOW_TICKS) || player.getCombat().isDefending(ACTIVITY_WINDOW_TICKS);
	}
}

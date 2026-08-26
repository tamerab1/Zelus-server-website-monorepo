package economy.protection.bounty;

import economy.protection.antifarm.IdentityGuard;
import economy.protection.audit.AuditLogger;
import io.ruin.HooksV2.Result;
import io.ruin.model.entity.npc.NPC;
import io.ruin.model.entity.npc.NPCCombat;
import io.ruin.model.entity.player.Player;
import io.ruin.network.incoming.handlers.CommandHandler;
import io.ruin.utility.Broadcast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Wilderness Boss Bounty Contracts: PVP Mode accounts fund a PKP bounty on one of the four
 * Wilderness bosses (via {@code ::bounty <boss> <pkp>}); the first PVM Mode account to kill
 * that boss while contracted converts the pooled PKP into PVM Points. Multiple players can
 * stack contributions onto the same boss — the whole pool pays out on the next kill.
 *
 * <p>Canonical boss names below are taken verbatim from {@code io.ruin.model.content.PvmPoints}'
 * BOSS_POINTS table (same codebase, same NPC-name-matching convention), since there is no
 * dedicated "Wilderness boss" tag/registry elsewhere in the codebase.</p>
 */
public class BountyContractsManager {

	/** command alias (no spaces/apostrophes) -> canonical lowercase NPC name. */
	private static final Map<String, String> BOSS_ALIASES = Map.of(
		"callisto", "callisto",
		"venenatis", "venenatis",
		"vetion", "vet'ion",
		"chaosfanatic", "chaos fanatic"
	);

	/** canonical lowercase NPC name -> display name, used for messages/broadcasts. */
	private static final Map<String, String> DISPLAY_NAMES = Map.of(
		"callisto", "Callisto",
		"venenatis", "Venenatis",
		"vet'ion", "Vet'ion",
		"chaos fanatic", "Chaos Fanatic"
	);

	/** Conversion rate applied to the pooled PKP bounty when paid out as PVM Points. */
	static final double PKP_TO_PVM_POINTS_RATE = 1.0;

	private static final Map<String, BountyContract> contracts = new ConcurrentHashMap<>();

	private BountyContractsManager() {
	}

	/** A pooled bounty on one boss, with an identity snapshot of every contributor for collusion checks at claim time. */
	static final class BountyContract {
		int pkpAmount;
		final List<String> contributorHwids = new ArrayList<>();
		final List<Set<String>> contributorHwidSets = new ArrayList<>();
		final List<Integer> contributorIps = new ArrayList<>();
	}

	public static void register() {
		NPCCombat.hooks.register(NPCCombat.Hook.Death.class, BountyContractsManager::onNpcDeath);
		CommandHandler.hooks.register(CommandHandler.Hook.Handle.class, BountyContractsManager::onCommand);
	}

	static Result onCommand(CommandHandler.Hook.Handle ctx) {
		if (!ctx.command().equalsIgnoreCase("bounty")) {
			return Result.Pass;
		}
		Player player = ctx.player();
		String[] args = ctx.args();
		if (args.length != 2) {
			player.sendMessage("Usage: ::bounty <boss> <pkp amount> — bosses: " + String.join(", ", BOSS_ALIASES.keySet()));
			return Result.Return;
		}
		Integer amount = tryParseInt(args[1]);
		if (amount == null) {
			player.sendMessage("Usage: ::bounty <boss> <pkp amount>");
			return Result.Return;
		}
		placeBounty(player, args[0], amount);
		return Result.Return;
	}

	/**
	 * Adds {@code pkpAmount} PKP (deducted from {@code setter} immediately) to the bounty pool
	 * on {@code bossAlias}. Only PVP Mode accounts may fund a contract.
	 *
	 * @return true if the bounty was placed
	 */
	public static boolean placeBounty(Player setter, String bossAlias, int pkpAmount) {
		if (!setter.isPvpMode()) {
			setter.sendMessage("Only PVP Mode accounts can place Wilderness boss bounties.");
			return false;
		}
		String bossName = resolveBossName(bossAlias);
		if (bossName == null) {
			setter.sendMessage("Unknown Wilderness boss. Choose from: " + String.join(", ", BOSS_ALIASES.keySet()));
			return false;
		}
		if (pkpAmount <= 0) {
			setter.sendMessage("Bounty amount must be positive.");
			return false;
		}
		if (setter.getPKPoints() < pkpAmount) {
			setter.sendMessage("You don't have enough PKP for that bounty.");
			return false;
		}

		setter.updatePKPoints(-pkpAmount);
		BountyContract contract = contracts.computeIfAbsent(bossName, k -> new BountyContract());
		contract.pkpAmount += pkpAmount;
		contract.contributorHwids.add(setter.hwid);
		contract.contributorHwidSets.add(setter.hwids == null ? Set.of() : new HashSet<>(setter.hwids));
		contract.contributorIps.add(setter.getIpInt());

		String display = DISPLAY_NAMES.get(bossName);
		setter.sendMessage("[Bounty] You added " + pkpAmount + " PKP to the bounty on " + display + " (pool: " + contract.pkpAmount + " PKP).");
		Broadcast.GLOBAL.sendPlain("[Bounty] A " + pkpAmount + " PKP bounty has been placed on " + display + "!");
		return true;
	}

	private static Result onNpcDeath(NPCCombat.Hook.Death ctx) {
		fulfillIfContracted(ctx.killer(), ctx.npc());
		return Result.Pass;
	}

	/**
	 * Pays out the bounty pool on {@code npc} to {@code killer} if one exists and {@code killer}
	 * is eligible (PVM Mode, in the Wilderness, not colluding with any contributor).
	 */
	public static void fulfillIfContracted(Player killer, NPC npc) {
		if (npc == null || npc.getDef() == null) return;
		fulfillContract(killer, npc.getDef().name);
	}

	/**
	 * Same as {@link #fulfillIfContracted}, but takes the boss's raw NPC name directly instead
	 * of an {@link NPC} instance — split out so this is testable without a cache-backed NPC
	 * (the lightweight test harness doesn't load {@code NPCType}).
	 */
	static void fulfillContract(Player killer, String npcName) {
		if (killer == null || npcName == null) return;
		if (!killer.isPvmMode()) return; // only PVM Mode accounts can fulfill a contract
		if (killer.wildernessLevel <= 0) return;

		String bossName = normalize(npcName);
		if (!DISPLAY_NAMES.containsKey(bossName)) return;

		BountyContract contract = contracts.remove(bossName);
		if (contract == null || contract.pkpAmount <= 0) return;

		if (isColluding(killer, contract)) {
			AuditLogger.logRejectedBounty(killer, npcName, contract.pkpAmount,
				"Killer shares identity (HWID/subnet) with a bounty contributor — contract voided, no payout.");
			return;
		}

		int pvmPoints = convertBountyToPvmPoints(contract.pkpAmount);
		if (pvmPoints <= 0) return;

		killer.PvmPoints += pvmPoints;
		killer.sendMessage("[Bounty] Contract fulfilled! +" + pvmPoints + " PVM Points from the " + npcName + " bounty.");
		Broadcast.GLOBAL.sendPlain("[Bounty] " + killer.getName() + " has fulfilled the bounty contract on " + npcName + "!");
	}

	/** True if any bounty contributor shares an HWID or /24 subnet with {@code killer} (self-funded farming). */
	static boolean isColluding(Player killer, BountyContract contract) {
		for (int i = 0; i < contract.contributorHwids.size(); i++) {
			if (IdentityGuard.sameHwid(contract.contributorHwids.get(i), contract.contributorHwidSets.get(i), killer.hwid, killer.hwids)) {
				return true;
			}
		}
		for (int ip : contract.contributorIps) {
			if (IdentityGuard.sameSubnet(ip, killer.getIpInt())) {
				return true;
			}
		}
		return false;
	}

	static int convertBountyToPvmPoints(int pooledPkp) {
		return (int) Math.round(pooledPkp * PKP_TO_PVM_POINTS_RATE);
	}

	/** True if {@code bossAlias} (a command alias like "vetion", or a canonical name like "vet'ion") is a supported Wilderness boss. */
	public static boolean isWildernessBoss(String bossAlias) {
		return resolveBossName(bossAlias) != null;
	}

	/** Current pooled PKP for a boss (0 if no active contract). Accepts the same aliases as {@link #placeBounty}. */
	public static int poolFor(String bossAlias) {
		String bossName = resolveBossName(bossAlias);
		if (bossName == null) return 0;
		BountyContract contract = contracts.get(bossName);
		return contract == null ? 0 : contract.pkpAmount;
	}

	/** Clears all active contracts — test-only reset hook (also useful for an admin reset command later). */
	public static void clear() {
		contracts.clear();
	}

	private static String resolveBossName(String aliasOrName) {
		String normalized = normalize(aliasOrName);
		if (DISPLAY_NAMES.containsKey(normalized)) {
			return normalized;
		}
		return BOSS_ALIASES.get(normalized);
	}

	private static String normalize(String value) {
		return value == null ? "" : value.toLowerCase();
	}

	private static Integer tryParseInt(String s) {
		try {
			return Integer.parseInt(s.trim());
		} catch (NumberFormatException e) {
			return null;
		}
	}
}

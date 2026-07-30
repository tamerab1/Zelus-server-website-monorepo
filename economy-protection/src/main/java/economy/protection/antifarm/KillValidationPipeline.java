package economy.protection.antifarm;

import economy.protection.audit.AuditLogger;
import economy.protection.config.EconomyConfig;
import io.ruin.HooksV2.Result;
import io.ruin.model.combat.Killer;
import io.ruin.model.entity.player.Player;
import io.ruin.model.stat.StatType;

import java.util.Collections;
import java.util.Set;

/**
 * Registers on {@link Killer.Hook.ValidateKill} to gate PKP/BH-target/killer-directed-drop
 * rewards behind identity and combat-authenticity checks. This runs before, and independently
 * of, the simpler same-IP/{@code bmIpLogs} cooldown check already inline in {@link Killer#reward}
 * — that existing check is left untouched; this pipeline only adds new checks.
 */
public class KillValidationPipeline {

	public static void register() {
		Killer.hooks.register(Killer.Hook.ValidateKill.class, KillValidationPipeline::handle);
	}

	static Result handle(Killer.Hook.ValidateKill ctx) {
		Killer killer = ctx.killer();
		Player pKiller = killer.player;
		Player pKilled = ctx.pKilled();
		if (pKiller == null) {
			return Result.Pass;
		}

		String reason = reject(killer, pKiller, pKilled);
		if (reason != null) {
			ctx.verdict().blocked = true;
			ctx.verdict().reason = reason;
			AuditLogger.logRejectedKill(killer, pKilled, reason);
		}
		return Result.Pass;
	}

	private static String reject(Killer killer, Player pKiller, Player pKilled) {
		if (sameHwid(pKiller, pKilled)) {
			return "Killer and victim share a HWID (current or historical).";
		}
		if (pKiller.getIpInt() == pKilled.getIpInt()) {
			return "Killer and victim share an IP address.";
		}
		if (sameSubnet(pKiller, pKilled)) {
			if (EconomyConfig.hardBlockSameSubnet) {
				return "Killer and victim are on the same /24 subnet.";
			}
			AuditLogger.logRejectedKill(killer, pKilled, "[flagged, not blocked] same /24 subnet as victim");
		}

		int maxHp = pKilled.getStats().get(StatType.Hitpoints).fixedLevel;
		if (maxHp > 0 && killer.damage < EconomyConfig.minDamageShare * maxHp) {
			return "Killer dealt insufficient damage share (" + killer.damage + "/" + maxHp + ").";
		}

		if (killer.firstDamageMs > 0) {
			long duration = System.currentTimeMillis() - killer.firstDamageMs;
			if (duration < EconomyConfig.minCombatDurationMs) {
				return "Combat duration too short (" + duration + "ms).";
			}
		}

		return null;
	}

	private static boolean sameHwid(Player pKiller, Player pKilled) {
		if (pKiller.hwid != null && pKiller.hwid.equals(pKilled.hwid)) {
			return true;
		}
		Set<String> killerHwids = pKiller.hwids == null ? Collections.emptySet() : pKiller.hwids;
		Set<String> victimHwids = pKilled.hwids == null ? Collections.emptySet() : pKilled.hwids;
		for (String hwid : killerHwids) {
			if (victimHwids.contains(hwid)) {
				return true;
			}
		}
		return false;
	}

	private static boolean sameSubnet(Player pKiller, Player pKilled) {
		return (pKiller.getIpInt() & 0xFFFFFF00) == (pKilled.getIpInt() & 0xFFFFFF00);
	}
}

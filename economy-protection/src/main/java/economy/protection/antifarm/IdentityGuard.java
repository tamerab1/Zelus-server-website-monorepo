package economy.protection.antifarm;

import io.ruin.model.entity.player.Player;

import java.util.Collections;
import java.util.Set;

/**
 * Shared "same real person controlling both accounts" checks (HWID overlap, /24 subnet).
 * Centralized here so every reward path that needs this — {@link KillValidationPipeline}
 * (player-vs-player kills) and {@code economy.protection.bounty.BountyContractsManager}
 * (bounty-contract self-funding/self-fulfilling) — uses identical logic instead of drifting
 * copies.
 *
 * <p>Primitive-based overloads exist because bounty contracts snapshot a contributor's
 * identity at contribution time (the contributor may be offline by the time the contract is
 * fulfilled, so there's no live {@link Player} to compare against).</p>
 */
public final class IdentityGuard {

	private IdentityGuard() {
	}

	public static boolean sameHwid(Player a, Player b) {
		return sameHwid(a.hwid, a.hwids, b.hwid, b.hwids);
	}

	public static boolean sameHwid(String hwidA, Set<String> hwidsA, String hwidB, Set<String> hwidsB) {
		if (hwidA != null && hwidA.equals(hwidB)) {
			return true;
		}
		Set<String> setA = hwidsA == null ? Collections.emptySet() : hwidsA;
		Set<String> setB = hwidsB == null ? Collections.emptySet() : hwidsB;
		for (String hwid : setA) {
			if (setB.contains(hwid)) {
				return true;
			}
		}
		return false;
	}

	public static boolean sameIp(Player a, Player b) {
		return a.getIpInt() == b.getIpInt();
	}

	public static boolean sameSubnet(Player a, Player b) {
		return sameSubnet(a.getIpInt(), b.getIpInt());
	}

	/** /24 subnet match on raw ints — lets callers compare against a snapshot, not just a live Player. */
	public static boolean sameSubnet(int ipA, int ipB) {
		return (ipA & 0xFFFFFF00) == (ipB & 0xFFFFFF00);
	}
}

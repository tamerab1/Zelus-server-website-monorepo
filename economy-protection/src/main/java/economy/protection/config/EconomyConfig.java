package economy.protection.config;

import io.ruin.api.utils.Random;

/**
 * Tunable economy-protection knobs, populated at boot by
 * {@code io.ruin.data.impl.economy.EconomyConfigLoader} from {@code economy/economy_config.json}.
 * Defaults here are used until/unless the config file overrides them, and as safe fallbacks
 * for unit tests that never trigger the loader.
 */
public final class EconomyConfig {

	private EconomyConfig() {
	}

	/** Divisor applied to accumulated spawnable-item PvP value to produce a PKP reward. */
	public static volatile long pkpConversionDivisor = 1000;

	/** Percentage (0-100) chance a HIGH_VALUE item destined for the killer is sunk (destroyed) instead of dropped. */
	public static volatile int highValueSinkPercent = 5;

	/** Minimum fraction (0.0-1.0) of the victim's max HP the killer must have dealt for a kill to count. */
	public static volatile double minDamageShare = 0.40;

	/** Minimum combat duration (ms) between first damage and death for a kill to count. */
	public static volatile long minCombatDurationMs = 15_000;

	/** If true, same-/24-subnet kills are hard-blocked instead of just flagged for audit. */
	public static volatile boolean hardBlockSameSubnet = false;

	public static int convertToPkp(long pvpValue) {
		if (pkpConversionDivisor <= 0) return 0;
		return (int) Math.max(0, pvpValue / pkpConversionDivisor);
	}

	public static boolean rollSink() {
		if (highValueSinkPercent <= 0) return false;
		if (highValueSinkPercent >= 100) return true;
		return Random.get(99) < highValueSinkPercent;
	}
}

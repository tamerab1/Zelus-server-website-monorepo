package economy.protection.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EconomyConfigTest {

	@AfterEach
	void restoreDefaults() {
		EconomyConfig.pkpConversionDivisor = 1000;
		EconomyConfig.highValueSinkPercent = 5;
		EconomyConfig.minDamageShare = 0.40;
		EconomyConfig.minCombatDurationMs = 15_000;
		EconomyConfig.hardBlockSameSubnet = false;
	}

	@Test
	void convertToPkp_dividesByConfiguredDivisor() {
		EconomyConfig.pkpConversionDivisor = 1000;
		assertEquals(5, EconomyConfig.convertToPkp(5000));
	}

	@Test
	void convertToPkp_roundsDownAndNeverNegative() {
		EconomyConfig.pkpConversionDivisor = 1000;
		assertEquals(0, EconomyConfig.convertToPkp(999));
		assertEquals(0, EconomyConfig.convertToPkp(0));
	}

	@Test
	void convertToPkp_zeroDivisorYieldsZero() {
		EconomyConfig.pkpConversionDivisor = 0;
		assertEquals(0, EconomyConfig.convertToPkp(5000));
	}

	@Test
	void rollSink_zeroPercentAlwaysFalse() {
		EconomyConfig.highValueSinkPercent = 0;
		for (int i = 0; i < 50; i++) {
			assertFalse(EconomyConfig.rollSink());
		}
	}

	@Test
	void rollSink_hundredPercentAlwaysTrue() {
		EconomyConfig.highValueSinkPercent = 100;
		for (int i = 0; i < 50; i++) {
			assertTrue(EconomyConfig.rollSink());
		}
	}
}

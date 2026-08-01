package economy.protection.antifarm;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Pure-logic coverage for the primitive-based overloads shared by KillValidationPipeline and BountyContractsManager. */
class IdentityGuardTest {

	@Test
	void sameCurrentHwidMatches() {
		assertTrue(IdentityGuard.sameHwid("hwid-a", Set.of(), "hwid-a", Set.of()));
	}

	@Test
	void historicalHwidOverlapMatches() {
		assertTrue(IdentityGuard.sameHwid("hwid-a", Set.of("old-1", "old-2"), "hwid-b", Set.of("old-2", "old-3")));
	}

	@Test
	void distinctHwidsAndHistoryDoNotMatch() {
		assertFalse(IdentityGuard.sameHwid("hwid-a", Set.of("old-1"), "hwid-b", Set.of("old-2")));
	}

	@Test
	void nullHwidSetsAreTreatedAsEmpty() {
		assertFalse(IdentityGuard.sameHwid("hwid-a", null, "hwid-b", null));
	}

	@Test
	void sameSubnetMatchesWithinTheSameSlash24() {
		int ipA = 0x0A000001; // 10.0.0.1
		int ipB = 0x0A0000FE; // 10.0.0.254
		assertTrue(IdentityGuard.sameSubnet(ipA, ipB));
	}

	@Test
	void differentSubnetDoesNotMatch() {
		int ipA = 0x0A000001; // 10.0.0.1
		int ipB = 0x0B000001; // 11.0.0.1
		assertFalse(IdentityGuard.sameSubnet(ipA, ipB));
	}
}

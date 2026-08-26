package economy.protection.pvm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure math coverage for the combat-level/tier point formula and the Wilderness bonus —
 * the two numeric rules from the PVM Points spec. Player/NPC-gated behaviour (mode check,
 * legacy-boss skip) is exercised indirectly via {@link economy.protection.gamemode.PlayModeTest}
 * and the NPCCombat.Hook.Death wiring in {@code Module}; constructing a real cache-backed NPC
 * isn't needed to validate this arithmetic.
 */
class PvmPointsManagerTest {

	@Test
	void combatLevelBelowFiftyEarnsNothing() {
		assertEquals(0, PvmPointsManager.pointsForTier(1));
		assertEquals(0, PvmPointsManager.pointsForTier(49));
	}

	@Test
	void tiersAreInclusiveOfTheirLowerBound() {
		assertEquals(1, PvmPointsManager.pointsForTier(50));
		assertEquals(4, PvmPointsManager.pointsForTier(100));
		assertEquals(8, PvmPointsManager.pointsForTier(200));
		assertEquals(15, PvmPointsManager.pointsForTier(350));
	}

	@Test
	void higherCombatLevelNeverEarnsFewerPointsThanALowerOne() {
		assertEquals(1, PvmPointsManager.pointsForTier(75));
		assertEquals(4, PvmPointsManager.pointsForTier(150));
		assertEquals(8, PvmPointsManager.pointsForTier(300));
		assertEquals(15, PvmPointsManager.pointsForTier(1000));
	}

	@Test
	void wildernessKillsGetAFifteenPercentBonus() {
		assertEquals(1, PvmPointsManager.applyWildernessBonus(1, true), "rounds 1.15 down to 1");
		assertEquals(5, PvmPointsManager.applyWildernessBonus(4, true), "rounds 4.6 up to 5");
		assertEquals(9, PvmPointsManager.applyWildernessBonus(8, true), "rounds 9.2 down to 9");
		assertEquals(17, PvmPointsManager.applyWildernessBonus(15, true), "rounds 17.25 down to 17");
	}

	@Test
	void nonWildernessKillsGetNoBonus() {
		assertEquals(8, PvmPointsManager.applyWildernessBonus(8, false));
	}
}

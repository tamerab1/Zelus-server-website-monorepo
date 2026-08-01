package economy.protection.escort;

import clanchat.Attributes;
import io.ruin.model.entity.player.PlayMode;
import io.ruin.model.entity.player.Player;
import io.ruin.test.ServerTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers the escort-eligibility matrix: gamemode pairing, Wilderness presence, 15-tile radius,
 * same Friends Chat, and the anti-AFK/dual-box combat-activity gate.
 */
class EscortDutyManagerTest {

	@BeforeAll
	static void setup() {
		ServerTest.start();
		Attributes.register();
	}

	@AfterAll
	static void shutdown() {
		ServerTest.shutdown();
	}

	/** Fresh players spawn at the same default position and are engaged in combat and in the same FC unless overridden. */
	private static Player[] eligiblePair() {
		Player escort = ServerTest.createPlayer();
		Player pvmPlayer = ServerTest.createPlayer();

		escort.setPlayMode(PlayMode.PVP_MODE);
		pvmPlayer.setPlayMode(PlayMode.PVM_MODE);

		escort.wildernessLevel = 10;
		pvmPlayer.wildernessLevel = 10;

		Attributes.clan(escort).joinedName = "sharedfc";
		Attributes.clan(pvmPlayer).joinedName = "sharedfc";

		escort.getCombat().updateLastAttack(0);

		return new Player[] { escort, pvmPlayer };
	}

	@Test
	void fullyEligiblePairPasses() {
		Player[] pair = eligiblePair();

		assertTrue(EscortDutyManager.isEligibleEscort(pair[0], pair[1]));
	}

	@Test
	void escortMustBePvpMode() {
		Player[] pair = eligiblePair();
		pair[0].setPlayMode(PlayMode.PVM_MODE);

		assertFalse(EscortDutyManager.isEligibleEscort(pair[0], pair[1]));
	}

	@Test
	void protectedPlayerMustBePvmMode() {
		Player[] pair = eligiblePair();
		pair[1].setPlayMode(PlayMode.PVP_MODE);

		assertFalse(EscortDutyManager.isEligibleEscort(pair[0], pair[1]));
	}

	@Test
	void escortMustBeInTheWilderness() {
		Player[] pair = eligiblePair();
		pair[0].wildernessLevel = 0;

		assertFalse(EscortDutyManager.isEligibleEscort(pair[0], pair[1]));
	}

	@Test
	void protectedPlayerMustBeInTheWilderness() {
		Player[] pair = eligiblePair();
		pair[1].wildernessLevel = 0;

		assertFalse(EscortDutyManager.isEligibleEscort(pair[0], pair[1]));
	}

	@Test
	void pairBeyondTheRadiusIsIneligible() {
		Player[] pair = eligiblePair();
		pair[0].getPosition().set(pair[1].getPosition().getX() + EscortDutyManager.ESCORT_RADIUS_TILES + 1,
				pair[1].getPosition().getY(), pair[1].getPosition().getZ());

		assertFalse(EscortDutyManager.isEligibleEscort(pair[0], pair[1]));
	}

	@Test
	void pairAtExactlyTheRadiusIsEligible() {
		Player[] pair = eligiblePair();
		pair[0].getPosition().set(pair[1].getPosition().getX() + EscortDutyManager.ESCORT_RADIUS_TILES,
				pair[1].getPosition().getY(), pair[1].getPosition().getZ());

		assertTrue(EscortDutyManager.isEligibleEscort(pair[0], pair[1]));
	}

	@Test
	void differentFriendsChatIsIneligible() {
		Player[] pair = eligiblePair();
		Attributes.clan(pair[1]).joinedName = "otherfc";

		assertFalse(EscortDutyManager.isEligibleEscort(pair[0], pair[1]));
	}

	@Test
	void notBeingInAnyFriendsChatIsIneligible() {
		Player[] pair = eligiblePair();
		Attributes.clan(pair[0]).joinedName = "";
		Attributes.clan(pair[1]).joinedName = "";

		assertFalse(EscortDutyManager.isEligibleEscort(pair[0], pair[1]));
	}

	// NOTE: there is deliberately no "AFK escort is rejected" test here. isActivelyEngaged()
	// gates on Server.currentTick() - lastAttackTick/lastDefendTick, and Server.currentTick()
	// (== worker.getExecutions()) never advances in the lightweight ServerTest harness (no
	// tick loop runs), so a genuinely idle player's default lastAttackTick/lastDefendTick of 0
	// is indistinguishable from "just engaged" — both read as 0 ticks ago. This is the same
	// class of harness gap as VarPlayerRepository.register() from Phase 1: real, tick-driven
	// production behavior that this static test environment can't drive either side of.

	@Test
	void anEscortDefendingRecentlyAlsoQualifies() {
		Player[] pair = eligiblePair();
		// isActivelyEngaged accepts either attacking or defending recently
		pair[0].getCombat().updateLastDefend(pair[1]);

		assertTrue(EscortDutyManager.isEligibleEscort(pair[0], pair[1]));
	}
}

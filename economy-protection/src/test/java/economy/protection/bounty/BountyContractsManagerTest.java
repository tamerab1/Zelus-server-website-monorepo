package economy.protection.bounty;

import io.ruin.model.entity.player.PlayMode;
import io.ruin.model.entity.player.Player;
import io.ruin.test.ServerTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BountyContractsManagerTest {

	@BeforeAll
	static void setup() {
		ServerTest.start();
	}

	@AfterAll
	static void shutdown() {
		ServerTest.shutdown();
	}

	@AfterEach
	void cleanup() {
		BountyContractsManager.clear();
	}

	private static Player pvpPlayer(String hwid, int ip, int startingPkp) {
		Player player = ServerTest.createPlayer();
		player.setPlayMode(PlayMode.PVP_MODE);
		player.hwid = hwid;
		player.hwids.clear();
		player.hwids.add(hwid);
		player.ipAddressInt = ip;
		player.updatePKPoints(startingPkp);
		return player;
	}

	private static Player pvmPlayer(String hwid, int ip, int wildernessLevel) {
		Player player = ServerTest.createPlayer();
		player.setPlayMode(PlayMode.PVM_MODE);
		player.hwid = hwid;
		player.hwids.clear();
		player.hwids.add(hwid);
		player.ipAddressInt = ip;
		player.wildernessLevel = wildernessLevel;
		return player;
	}

	// --- placeBounty ---

	@Test
	void onlyPvpModeCanPlaceABounty() {
		Player setter = pvmPlayer("hwid-1", 0x0A000001, 0);
		setter.updatePKPoints(1000);

		boolean placed = BountyContractsManager.placeBounty(setter, "vetion", 100);

		assertFalse(placed);
		assertEquals(0, BountyContractsManager.poolFor("vetion"));
		assertEquals(1000, setter.getPKPoints(), "PKP must not be deducted on a rejected placement");
	}

	@Test
	void unknownBossIsRejected() {
		Player setter = pvpPlayer("hwid-2", 0x0A000002, 1000);

		boolean placed = BountyContractsManager.placeBounty(setter, "notaboss", 100);

		assertFalse(placed);
		assertEquals(1000, setter.getPKPoints());
	}

	@Test
	void nonPositiveAmountIsRejected() {
		Player setter = pvpPlayer("hwid-3", 0x0A000003, 1000);

		assertFalse(BountyContractsManager.placeBounty(setter, "callisto", 0));
		assertFalse(BountyContractsManager.placeBounty(setter, "callisto", -50));
		assertEquals(0, BountyContractsManager.poolFor("callisto"));
	}

	@Test
	void insufficientPkpIsRejected() {
		Player setter = pvpPlayer("hwid-4", 0x0A000004, 50);

		boolean placed = BountyContractsManager.placeBounty(setter, "venenatis", 100);

		assertFalse(placed);
		assertEquals(50, setter.getPKPoints());
		assertEquals(0, BountyContractsManager.poolFor("venenatis"));
	}

	@Test
	void validPlacementDeductsPkpAndFundsThePool() {
		Player setter = pvpPlayer("hwid-5", 0x0A000005, 1000);

		boolean placed = BountyContractsManager.placeBounty(setter, "chaosfanatic", 300);

		assertTrue(placed);
		assertEquals(700, setter.getPKPoints());
		assertEquals(300, BountyContractsManager.poolFor("chaosfanatic"));
	}

	@Test
	void multipleContributionsStackOnTheSameBoss() {
		Player setterA = pvpPlayer("hwid-6a", 0x0A000006, 1000);
		Player setterB = pvpPlayer("hwid-6b", 0x0B000007, 1000);

		BountyContractsManager.placeBounty(setterA, "vetion", 200);
		BountyContractsManager.placeBounty(setterB, "vetion", 150);

		assertEquals(350, BountyContractsManager.poolFor("vetion"));
	}

	@Test
	void canonicalNameAndAliasReferTheSamePool() {
		Player setter = pvpPlayer("hwid-7", 0x0A000008, 1000);
		BountyContractsManager.placeBounty(setter, "vetion", 100);

		assertEquals(100, BountyContractsManager.poolFor("vet'ion"));
	}

	// --- fulfillContract ---

	@Test
	void onlyPvmModeCanFulfillAContract() {
		Player setter = pvpPlayer("hwid-8", 0x0A000009, 1000);
		BountyContractsManager.placeBounty(setter, "callisto", 200);
		Player killer = pvpPlayer("hwid-9", 0x0A00000A, 0); // PVP Mode, ineligible to fulfill

		BountyContractsManager.fulfillContract(killer, "Callisto");

		assertEquals(200, BountyContractsManager.poolFor("callisto"), "an ineligible fulfillment attempt must not touch the pool");
		assertEquals(0, killer.PvmPoints);
	}

	@Test
	void killerMustBeInTheWilderness() {
		Player setter = pvpPlayer("hwid-10", 0x0A00000B, 1000);
		BountyContractsManager.placeBounty(setter, "venenatis", 200);
		Player killer = pvmPlayer("hwid-11", 0x0A00000C, 0); // not in wilderness

		BountyContractsManager.fulfillContract(killer, "Venenatis");

		assertEquals(200, BountyContractsManager.poolFor("venenatis"));
		assertEquals(0, killer.PvmPoints);
	}

	@Test
	void unrecognizedNpcNameDoesNothing() {
		Player killer = pvmPlayer("hwid-12", 0x0A00000D, 5);

		BountyContractsManager.fulfillContract(killer, "Giant Rat");

		assertEquals(0, killer.PvmPoints);
	}

	@Test
	void eligibleKillerFulfillsAndPoolIsCleared() {
		Player setter = pvpPlayer("hwid-13", 0x0A00000E, 1000);
		BountyContractsManager.placeBounty(setter, "chaosfanatic", 400);
		Player killer = pvmPlayer("hwid-14", 0x0B00000F, 10);

		BountyContractsManager.fulfillContract(killer, "Chaos Fanatic");

		assertEquals(BountyContractsManager.convertBountyToPvmPoints(400), killer.PvmPoints);
		assertEquals(0, BountyContractsManager.poolFor("chaosfanatic"), "the pool must be cleared once claimed");
	}

	@Test
	void nameMatchingIsCaseInsensitive() {
		Player setter = pvpPlayer("hwid-15", 0x0A000010, 1000);
		BountyContractsManager.placeBounty(setter, "vetion", 250);
		Player killer = pvmPlayer("hwid-16", 0x0B000011, 20);

		BountyContractsManager.fulfillContract(killer, "VET'ION");

		assertEquals(BountyContractsManager.convertBountyToPvmPoints(250), killer.PvmPoints);
	}

	// --- anti-farm / collusion edge cases (deliverable #3) ---

	@Test
	void sameHwidBetweenSetterAndKillerVoidsTheContract() {
		Player setter = pvpPlayer("shared-hwid", 0x0A000012, 1000);
		BountyContractsManager.placeBounty(setter, "callisto", 500);
		Player killer = pvmPlayer("shared-hwid", 0x0B000013, 15); // same HWID, different IP entirely

		BountyContractsManager.fulfillContract(killer, "Callisto");

		assertEquals(0, killer.PvmPoints, "a self-funded (same HWID) claim must not pay out");
		assertEquals(0, BountyContractsManager.poolFor("callisto"), "the voided contract is not refundable — it's simply gone");
	}

	@Test
	void sameSubnetBetweenSetterAndKillerVoidsTheContract() {
		Player setter = pvpPlayer("hwid-a", 0x0A000101, 1000); // 10.0.1.1
		BountyContractsManager.placeBounty(setter, "venenatis", 500);
		Player killer = pvmPlayer("hwid-b", 0x0A0001FE, 15); // 10.0.1.254 -- same /24

		BountyContractsManager.fulfillContract(killer, "Venenatis");

		assertEquals(0, killer.PvmPoints);
	}

	@Test
	void historicalHwidOverlapAlsoVoidsTheContract() {
		Player setter = pvpPlayer("hwid-current-a", 0x0A000201, 1000);
		setter.hwids.add("old-shared-hwid");
		BountyContractsManager.placeBounty(setter, "chaosfanatic", 500);

		Player killer = pvmPlayer("hwid-current-b", 0x0B000202, 15);
		killer.hwids.add("old-shared-hwid"); // same machine, different current hwid

		BountyContractsManager.fulfillContract(killer, "Chaos Fanatic");

		assertEquals(0, killer.PvmPoints);
	}

	@Test
	void onlyTheCollidingContributorBlocksPayoutEvenInAMultiContributorPool() {
		Player innocentSetter = pvpPlayer("hwid-innocent", 0x0A000301, 1000);
		Player colludingSetter = pvpPlayer("hwid-colluding", 0x0A000302, 1000);
		BountyContractsManager.placeBounty(innocentSetter, "vetion", 100);
		BountyContractsManager.placeBounty(colludingSetter, "vetion", 100);

		Player killer = pvmPlayer("hwid-colluding", 0x0B000303, 15); // matches colludingSetter's hwid

		BountyContractsManager.fulfillContract(killer, "Vet'ion");

		assertEquals(0, killer.PvmPoints, "any single colluding contributor voids the whole pooled contract");
	}

	@Test
	void distinctIdentitiesAcrossAllContributorsAllowsPayout() {
		Player setterA = pvpPlayer("hwid-x1", 0x0A000401, 1000);
		Player setterB = pvpPlayer("hwid-x2", 0x0A000501, 1000);
		BountyContractsManager.placeBounty(setterA, "vetion", 100);
		BountyContractsManager.placeBounty(setterB, "vetion", 100);

		Player killer = pvmPlayer("hwid-x3", 0x0A000601, 15); // distinct hwid and subnet from both

		BountyContractsManager.fulfillContract(killer, "Vet'ion");

		assertEquals(BountyContractsManager.convertBountyToPvmPoints(200), killer.PvmPoints);
	}

	// --- alias resolution ---

	@Test
	void isWildernessBossAcceptsAliasesAndCanonicalNamesOnly() {
		assertTrue(BountyContractsManager.isWildernessBoss("vetion"));
		assertTrue(BountyContractsManager.isWildernessBoss("vet'ion"));
		assertTrue(BountyContractsManager.isWildernessBoss("Callisto"));
		assertTrue(BountyContractsManager.isWildernessBoss("chaosfanatic"));
		assertFalse(BountyContractsManager.isWildernessBoss("king black dragon"));
		assertFalse(BountyContractsManager.isWildernessBoss(null));
	}
}

package economy.protection.antifarm;

import io.ruin.model.combat.Killer;
import io.ruin.model.entity.player.Player;
import io.ruin.model.stat.StatType;
import io.ruin.test.ServerTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises {@link KillValidationPipeline#handle} directly (package-private, same package)
 * against real {@link Player} instances created via the existing {@code ServerTest} harness
 * (see {@code tradepost}'s tests for the same pattern), bypassing the HooksV2/module wiring
 * so each check can be tested in isolation.
 */
class KillValidationPipelineTest {

	@BeforeAll
	static void setup() {
		ServerTest.start();
	}

	@AfterAll
	static void shutdown() {
		ServerTest.shutdown();
	}

	private static Killer.Verdict validate(Killer killer, Player pKilled) {
		Killer.Verdict verdict = new Killer.Verdict();
		KillValidationPipeline.handle(new Killer.Hook.ValidateKill(killer, pKilled, verdict));
		return verdict;
	}

	/** A killer that would otherwise clearly pass: full damage share, long fight. */
	private static Killer legitimateKiller(Player attacker, Player victim) {
		victim.getStats().set(StatType.Hitpoints, 99);
		Killer killer = new Killer();
		killer.player = attacker;
		killer.damage = 99;
		killer.firstDamageMs = System.currentTimeMillis() - 20_000;
		return killer;
	}

	/**
	 * ServerTest.createPlayer() stamps every player with hwid="hwid" (and seeds that same
	 * literal into the historical hwids set) at init — so isolating a test to a check other
	 * than HWID overlap requires resetting both, not just the current hwid field.
	 */
	private static void setDistinctIdentity(Player player, String hwid, int ip) {
		player.hwid = hwid;
		player.hwids.clear();
		player.hwids.add(hwid);
		player.ipAddressInt = ip;
	}

	@Test
	void sameHwidIsBlocked() {
		Player attacker = ServerTest.createPlayer();
		Player victim = ServerTest.createPlayer();
		// leave both on the harness's default hwid/hwids ("hwid") to exercise the overlap check
		setDistinctIdentity(attacker, "shared-hwid", 0x0A000010);
		setDistinctIdentity(victim, "shared-hwid", 0x0B000011);

		Killer.Verdict verdict = validate(legitimateKiller(attacker, victim), victim);
		assertTrue(verdict.blocked);
	}

	@Test
	void sameIpIsBlocked() {
		Player attacker = ServerTest.createPlayer();
		Player victim = ServerTest.createPlayer();
		setDistinctIdentity(attacker, "hwid-a1", 0x0A000001);
		setDistinctIdentity(victim, "hwid-v1", 0x0A000001);

		Killer.Verdict verdict = validate(legitimateKiller(attacker, victim), victim);
		assertTrue(verdict.blocked);
	}

	@Test
	void distinctIdentityWithSufficientDamageAndDurationIsAllowed() {
		Player attacker = ServerTest.createPlayer();
		Player victim = ServerTest.createPlayer();
		setDistinctIdentity(attacker, "hwid-a2", 0x0A000002);
		setDistinctIdentity(victim, "hwid-v2", 0x0B000003);

		Killer.Verdict verdict = validate(legitimateKiller(attacker, victim), victim);
		assertFalse(verdict.blocked, verdict.reason);
	}

	@Test
	void insufficientDamageShareIsBlocked() {
		Player attacker = ServerTest.createPlayer();
		Player victim = ServerTest.createPlayer();
		setDistinctIdentity(attacker, "hwid-a3", 0x0A000004);
		setDistinctIdentity(victim, "hwid-v3", 0x0B000005);

		Killer killer = legitimateKiller(attacker, victim);
		killer.damage = 1; // well under the 40% threshold of a 99hp victim

		Killer.Verdict verdict = validate(killer, victim);
		assertTrue(verdict.blocked);
	}

	@Test
	void tooFastKillIsBlocked() {
		Player attacker = ServerTest.createPlayer();
		Player victim = ServerTest.createPlayer();
		setDistinctIdentity(attacker, "hwid-a4", 0x0A000006);
		setDistinctIdentity(victim, "hwid-v4", 0x0B000007);

		Killer killer = legitimateKiller(attacker, victim);
		killer.firstDamageMs = System.currentTimeMillis(); // instant kill

		Killer.Verdict verdict = validate(killer, victim);
		assertTrue(verdict.blocked);
	}
}

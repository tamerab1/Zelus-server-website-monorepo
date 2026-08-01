package economy.protection.gamemode;

import io.ruin.model.entity.player.GameMode;
import io.ruin.model.entity.player.PlayMode;
import io.ruin.model.entity.player.Player;
import io.ruin.model.var.VarPlayerRepository;
import io.ruin.test.ServerTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises {@link Player#getPlayMode()}/{@link Player#setPlayMode(PlayMode)} — the account
 * flag every PVP/PVM restriction in this feature (::spawn, PvpPresetManager, PvmPointStore,
 * PvmPointsManager) is gated on.
 */
class PlayModeTest {

	@BeforeAll
	static void setup() {
		ServerTest.start();
		// The lightweight test harness skips io.ruin.StaticInit, which is where the real
		// server boot populates VarPlayerRepository's bit-shift table; without this,
		// IRONMAN_MODE.set() silently no-ops on every varbit write.
		VarPlayerRepository.register();
	}

	@AfterAll
	static void shutdown() {
		ServerTest.shutdown();
	}

	@Test
	void freshStandardAccountDefaultsToPvmMode() {
		Player player = ServerTest.createPlayer();

		// No migration script needed: saves written before `playMode` existed come back
		// null and fall back to PlayMode.defaultFor(STANDARD) == PVM_MODE, preserving the
		// pre-feature behaviour where every standard account already had PvmPoints shop
		// access (PVP_MODE-only features like ::spawn are the newer, more niche addition).
		assertTrue(player.isPvmMode());
		assertFalse(player.isPvpMode());
	}

	@Test
	void standardAccountCanOptIntoPvpMode() {
		Player player = ServerTest.createPlayer();

		assertTrue(player.setPlayMode(PlayMode.PVP_MODE));

		assertTrue(player.isPvpMode());
		assertFalse(player.isPvmMode());
	}

	@Test
	void ironmanAccountIsAlwaysPvmModeRegardlessOfStoredValue() {
		Player player = ServerTest.createPlayer();
		VarPlayerRepository.IRONMAN_MODE.set(player, GameMode.IRONMAN.ordinal());

		// playMode field was never touched (still null/default), yet the ironman
		// tier alone must force PVM_MODE.
		assertEquals(PlayMode.PVM_MODE, player.getPlayMode());
		assertTrue(player.isPvmMode());
	}

	@Test
	void ironmanAccountCannotBeSwitchedToPvpMode() {
		Player player = ServerTest.createPlayer();
		VarPlayerRepository.IRONMAN_MODE.set(player, GameMode.HARDCORE_IRONMAN.ordinal());

		boolean applied = player.setPlayMode(PlayMode.PVP_MODE);

		assertFalse(applied, "an ironman account must never be switchable to PVP_MODE");
		assertTrue(player.isPvmMode());
	}
}

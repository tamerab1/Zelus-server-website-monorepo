package economy.protection.pvppreset;

import io.ruin.model.content.pvppreset.PvpPreset;
import io.ruin.model.content.pvppreset.PvpPresetManager;
import io.ruin.model.entity.player.PlayMode;
import io.ruin.model.entity.player.Player;
import io.ruin.test.ServerTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Confirms PVM Mode accounts can only load PvP presets they already own in their bank —
 * never the free-standing "rent a system preset with phantom gear" path.
 *
 * A freshly created {@link ServerTest#createPlayer()} owns nothing, so
 * {@code canLoadFromBank} always fails and every case here falls to the rental branch,
 * which is exactly the branch PVM Mode must be blocked from. The rental fee is parked in
 * the bank (not the inventory) because {@code activate()} requires an empty inventory
 * before it will even attempt the rental path.
 */
class PvpPresetManagerGateTest {

	private static final int COINS = 995;

	@BeforeAll
	static void setup() {
		ServerTest.start();
	}

	@AfterAll
	static void shutdown() {
		ServerTest.shutdown();
	}

	@Test
	void pvmModePlayerCannotRentASystemPresetEvenWithEnoughGp() {
		Player player = ServerTest.createPlayer();
		player.setPlayMode(PlayMode.PVM_MODE);
		player.getBank().add(COINS, PvpPreset.ZERKER.getRentalCost());

		PvpPresetManager.activate(player, PvpPreset.ZERKER);

		assertFalse(player.pvpPresetActive, "PVM Mode accounts must not be able to rent a system preset");
		assertEquals(PvpPreset.ZERKER.getRentalCost(), player.getBank().getAmount(COINS),
				"rental fee must not be charged when the rental is rejected");
	}

	@Test
	void pvpModePlayerCanRentASystemPreset() {
		Player player = ServerTest.createPlayer();
		player.setPlayMode(PlayMode.PVP_MODE);
		player.getBank().add(COINS, PvpPreset.ZERKER.getRentalCost());

		PvpPresetManager.activate(player, PvpPreset.ZERKER);

		assertTrue(player.pvpPresetActive, "PVP Mode accounts should still be able to rent system presets");
		assertFalse(player.pvpPresetBankMode);
	}

	@Test
	void pvmModePlayerWithoutEnoughGpStillGetsTheModeSpecificRejection() {
		Player player = ServerTest.createPlayer();
		player.setPlayMode(PlayMode.PVM_MODE);
		// no coins at all — if the mode gate weren't checked first, this would instead
		// fail on the (unrelated) insufficient-GP path.

		PvpPresetManager.activate(player, PvpPreset.ZERKER);

		assertFalse(player.pvpPresetActive);
	}
}

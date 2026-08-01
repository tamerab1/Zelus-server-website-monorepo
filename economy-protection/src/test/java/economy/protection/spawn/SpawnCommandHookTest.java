package economy.protection.spawn;

import economy.protection.classification.ClassifiedItem;
import economy.protection.classification.ItemClassification;
import economy.protection.classification.ItemFlag;
import io.ruin.model.entity.player.PlayMode;
import io.ruin.model.entity.player.Player;
import io.ruin.network.incoming.handlers.CommandHandler;
import io.ruin.test.ServerTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Confirms PVM Mode accounts cannot bypass ::spawn (regardless of item), and that PVP Mode's
 * whitelist still excludes HIGH_VALUE items — the two access rules from the gamemode spec.
 */
class SpawnCommandHookTest {

	/** Shark — a real cache item (see ItemClassificationLoaderRuntimeTest), used as a basic/whitelisted stand-in. */
	private static final int BASIC_ITEM_ID = 385;

	@BeforeAll
	static void setup() {
		ServerTest.start();
		Attributes.register();
	}

	@AfterAll
	static void shutdown() {
		ServerTest.shutdown();
	}

	@AfterEach
	void cleanup() {
		ItemClassification.clear();
	}

	private static void spawn(Player player, int id, int amount) {
		SpawnCommandHook.handle(new CommandHandler.Hook.Handle(player, "spawn", new String[] { String.valueOf(id), String.valueOf(amount) }));
	}

	@Test
	void pvmModePlayerIsBlockedFromSpawnEntirely() {
		ItemClassification.register(new ClassifiedItem(BASIC_ITEM_ID, EnumSet.of(ItemFlag.SPAWNABLE), 0, 0));
		Player player = ServerTest.createPlayer();
		player.setPlayMode(PlayMode.PVM_MODE);

		spawn(player, BASIC_ITEM_ID, 1);

		assertEquals(0, player.getInventory().getAmount(BASIC_ITEM_ID),
				"PVM Mode accounts must never receive items from ::spawn");
	}

	@Test
	void pvpModePlayerCanSpawnAWhitelistedBasicItem() {
		ItemClassification.register(new ClassifiedItem(BASIC_ITEM_ID, EnumSet.of(ItemFlag.SPAWNABLE), 0, 0));
		Player player = ServerTest.createPlayer();
		player.setPlayMode(PlayMode.PVP_MODE);

		spawn(player, BASIC_ITEM_ID, 1);

		assertEquals(1, player.getInventory().getAmount(BASIC_ITEM_ID));
	}

	@Test
	void pvpModePlayerCannotSpawnAHighValueItemEvenThoughSpawnable() {
		int highValueId = 11785; // arbitrary id — only the registered flags matter here
		ItemClassification.register(new ClassifiedItem(highValueId, EnumSet.of(ItemFlag.SPAWNABLE, ItemFlag.HIGH_VALUE), 0, 0));
		Player player = ServerTest.createPlayer();
		player.setPlayMode(PlayMode.PVP_MODE);

		spawn(player, highValueId, 1);

		assertEquals(0, player.getInventory().getAmount(highValueId),
				"HIGH_VALUE items must stay unspawnable even for PVP Mode accounts");
	}

	@Test
	void isSpawnAllowedRequiresSpawnableAndRejectsHighValue() {
		ItemClassification.register(new ClassifiedItem(1, EnumSet.of(ItemFlag.SPAWNABLE), 0, 0));
		ItemClassification.register(new ClassifiedItem(2, EnumSet.of(ItemFlag.SPAWNABLE, ItemFlag.HIGH_VALUE), 0, 0));
		ItemClassification.register(new ClassifiedItem(3, EnumSet.of(ItemFlag.HIGH_VALUE), 0, 0));

		assertTrue(SpawnCommandHook.isSpawnAllowed(1));
		assertFalse(SpawnCommandHook.isSpawnAllowed(2));
		assertFalse(SpawnCommandHook.isSpawnAllowed(3));
		assertFalse(SpawnCommandHook.isSpawnAllowed(999999));
	}
}

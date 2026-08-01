package inter.ikod.hook;

import economy.protection.classification.ClassifiedItem;
import economy.protection.classification.ItemClassification;
import economy.protection.classification.ItemFlag;
import economy.protection.config.EconomyConfig;
import io.ruin.model.combat.Killer;
import io.ruin.model.entity.player.Player;
import io.ruin.model.entity.player.PlayerCombat;
import io.ruin.model.item.Item;
import io.ruin.test.ServerTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 2 deliverable #1 ("Wilderness PK Death & Spawn Item Conversion Engine") turned out to
 * already be implemented here (see {@link PlayerCombatHook#handle}) rather than needing new
 * code: spawnable/dissolvable items never become a ground drop, their pooled
 * {@code ItemClassification.pvpValueOf} converts into PKP for the killer via
 * {@link EconomyConfig#convertToPkp}, and the whole thing is gated by {@code killer.rewardBlocked}
 * — which Phase 1's {@code KillValidationPipeline} sets via {@code Killer.hooks} before this
 * hook ever runs. These tests lock that existing behaviour in.
 */
class PlayerCombatHookSpawnConversionTest {

	/** An id not touched by any of IKOD's special-cased degrade/transform rules. */
	private static final int SPAWNABLE_ITEM_ID = 385;
	private static final long SPAWNABLE_PVP_VALUE = 3000; // -> 3 PKP at the default 1000 divisor

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
		ItemClassification.clear();
	}

	private static Player skulledVictimHolding(int itemId, int amount) {
		Player victim = ServerTest.createPlayer();
		victim.getCombat().skullDelay = 1000; // forces IKOD's keepCount to 0 -> every item is "lost"
		victim.getInventory().add(itemId, amount);
		return victim;
	}

	private static Killer killerOf(Player pKiller, boolean rewardBlocked) {
		Killer killer = new Killer();
		killer.player = pKiller;
		killer.rewardBlocked = rewardBlocked;
		return killer;
	}

	@Test
	void spawnableItemConvertsToPkpInsteadOfDropping() {
		ItemClassification.register(new ClassifiedItem(SPAWNABLE_ITEM_ID, EnumSet.of(ItemFlag.SPAWNABLE), SPAWNABLE_PVP_VALUE, 0));
		Player victim = skulledVictimHolding(SPAWNABLE_ITEM_ID, 1);
		Player pKiller = ServerTest.createPlayer();
		int pkpBefore = pKiller.getPKPoints();

		PlayerCombatHook.handle(new PlayerCombat.Hook.OnDeath(victim, killerOf(pKiller, false), pKiller));

		assertEquals(pkpBefore + EconomyConfig.convertToPkp(SPAWNABLE_PVP_VALUE), pKiller.getPKPoints(),
				"killer should receive the item's pooled pvpValue converted to PKP");
		assertEquals(0, victim.getInventory().getAmount(SPAWNABLE_ITEM_ID),
				"the spawnable item must be gone from the victim, not restored");
	}

	@Test
	void dissolvableItemAlsoConvertsInsteadOfDropping() {
		ItemClassification.register(new ClassifiedItem(SPAWNABLE_ITEM_ID, EnumSet.of(ItemFlag.DISSOLVABLE), SPAWNABLE_PVP_VALUE, 0));
		Player victim = skulledVictimHolding(SPAWNABLE_ITEM_ID, 1);
		Player pKiller = ServerTest.createPlayer();
		int pkpBefore = pKiller.getPKPoints();

		PlayerCombatHook.handle(new PlayerCombat.Hook.OnDeath(victim, killerOf(pKiller, false), pKiller));

		assertEquals(pkpBefore + EconomyConfig.convertToPkp(SPAWNABLE_PVP_VALUE), pKiller.getPKPoints());
	}

	@Test
	void multipleUnitsAccumulateBeforeConversion() {
		ItemClassification.register(new ClassifiedItem(SPAWNABLE_ITEM_ID, EnumSet.of(ItemFlag.SPAWNABLE), SPAWNABLE_PVP_VALUE, 0));
		Player victim = skulledVictimHolding(SPAWNABLE_ITEM_ID, 4);
		Player pKiller = ServerTest.createPlayer();
		int pkpBefore = pKiller.getPKPoints();

		PlayerCombatHook.handle(new PlayerCombat.Hook.OnDeath(victim, killerOf(pKiller, false), pKiller));

		assertEquals(pkpBefore + EconomyConfig.convertToPkp(SPAWNABLE_PVP_VALUE * 4), pKiller.getPKPoints());
	}

	@Test
	void antiFarmRejectionBlocksTheConversionReward() {
		ItemClassification.register(new ClassifiedItem(SPAWNABLE_ITEM_ID, EnumSet.of(ItemFlag.SPAWNABLE), SPAWNABLE_PVP_VALUE, 0));
		Player victim = skulledVictimHolding(SPAWNABLE_ITEM_ID, 1);
		Player pKiller = ServerTest.createPlayer();
		int pkpBefore = pKiller.getPKPoints();

		// Simulates Killer.reward() having already set rewardBlocked via KillValidationPipeline
		// (same-HWID/subnet/insufficient-damage-share/too-fast-kill) before this hook runs.
		PlayerCombatHook.handle(new PlayerCombat.Hook.OnDeath(victim, killerOf(pKiller, true), pKiller));

		assertEquals(pkpBefore, pKiller.getPKPoints(), "a rewardBlocked kill must not grant any PKP conversion");
	}

	@Test
	void unclassifiedItemIsNotConvertedToPkp() {
		Player victim = skulledVictimHolding(SPAWNABLE_ITEM_ID, 1); // never registered with ItemClassification
		Player pKiller = ServerTest.createPlayer();
		int pkpBefore = pKiller.getPKPoints();

		PlayerCombatHook.handle(new PlayerCombat.Hook.OnDeath(victim, killerOf(pKiller, false), pKiller));

		assertEquals(pkpBefore, pKiller.getPKPoints(), "a normal (unclassified) item must not grant a PKP conversion");
	}
}

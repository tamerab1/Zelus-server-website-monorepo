package economy.protection.classification;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.*;

class ItemClassificationTest {

	@AfterEach
	void cleanup() {
		ItemClassification.clear();
	}

	@Test
	void spawnableFlagIsRecognized() {
		ItemClassification.register(new ClassifiedItem(385, EnumSet.of(ItemFlag.SPAWNABLE), 1200, 200));

		assertTrue(ItemClassification.isSpawnable(385));
		assertFalse(ItemClassification.isHighValue(385));
		assertTrue(ItemClassification.vanishesOnGround(385));
		assertEquals(1200, ItemClassification.pvpValueOf(385));
		assertEquals(200, ItemClassification.dailyCapOf(385));
	}

	@Test
	void highValueFlagIsRecognizedAndDoesNotVanishOnGround() {
		ItemClassification.register(new ClassifiedItem(20997, EnumSet.of(ItemFlag.HIGH_VALUE, ItemFlag.TRADABLE), 0, 0));

		assertTrue(ItemClassification.isHighValue(20997));
		assertFalse(ItemClassification.isSpawnable(20997));
		assertFalse(ItemClassification.vanishesOnGround(20997));
	}

	@Test
	void dissolvableAloneVanishesWithoutSpawnableSideEffects() {
		ItemClassification.register(new ClassifiedItem(1, EnumSet.of(ItemFlag.DISSOLVABLE), 0, 0));

		assertTrue(ItemClassification.vanishesOnGround(1));
		assertFalse(ItemClassification.isSpawnable(1));
	}

	@Test
	void unknownItemDefaultsToUnclassified() {
		assertFalse(ItemClassification.isSpawnable(999999));
		assertFalse(ItemClassification.isHighValue(999999));
		assertFalse(ItemClassification.vanishesOnGround(999999));
		assertEquals(0, ItemClassification.pvpValueOf(999999));
		assertEquals(0, ItemClassification.dailyCapOf(999999));
	}
}

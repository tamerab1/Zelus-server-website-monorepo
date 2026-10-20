package io.ruin.model.content.codex;

import io.ruin.model.entity.player.Player;
import io.ruin.model.item.Item;

import java.util.HashMap;

/**
 * The Zelus Codex's own tasks (2026-10-20), for the server's custom content. Progress and the claimed flag live in
 * {@code PlayerAttributes.zelusCodex} (saved with the player) under the task's name and "claimed." + name. The old
 * Reason achievements keep their own counters (see {@link ZelusCodex}).
 */
public enum ZelusTask {

	AWAKENING_IN_ZELUS(ZelusCodex.Tier.BEGINNER, "Awakening in Zelus", "Getting started",
		"Choose your path, claim your starter kit (::zelus) and examine the Reliquary of Souls at home: "
			+ "your first steps into the realm.", 3, 1_000,
		new Item(30470), new Item(380, 500)),
	ABYSSAL_CONQUEROR(ZelusCodex.Tier.ZELUS, "Abyssal Conqueror", "Global boss",
		"Defeat Nidhogg the Abyssal Serpent (::gb) 5 times.", 5, 10_000, new Item(290)),
	EARTHSHAKERS_DOWNFALL(ZelusCodex.Tier.ZELUS, "Earthshaker's Downfall", "Vote boss",
		"Defeat Volcanus (::vb) 5 times.", 5, 10_000, new Item(290)),
	MASTER_OF_THE_FORGE(ZelusCodex.Tier.ZELUS, "Master of the Forge", "Item Upgrader",
		"Successfully upgrade any item at the Zelus Relic Forge (the Item Upgrader).", 1, 15_000, new Item(12934, 250)),
	PATH_OF_MASTERY(ZelusCodex.Tier.ZELUS, "Path of Mastery", "Soul Tree",
		"Unlock any node in the Soul Tree (::soultree).", 1, 5_000, new Item(30470)),
	THE_VAULTKEEPER(ZelusCodex.Tier.ZELUS, "The Vaultkeeper", "Trading Post",
		"List an item for sale or buy one on the Trading Post (::market).", 1, 2_500, new Item(995, 500_000));

	/** Awakening steps (bit flags, the progress is how many are done). */
	public static final int STEP_PATH = 1, STEP_KIT = 2, STEP_RELIQUARY = 4;

	public final ZelusCodex.Tier tier;
	public final String title, category, description;
	public final int goal, points;
	public final Item[] items;

	ZelusTask(ZelusCodex.Tier tier, String title, String category, String description, int goal, int points, Item... items) {
		this.tier = tier;
		this.title = title;
		this.category = category;
		this.description = description;
		this.goal = goal;
		this.points = points;
		this.items = items;
	}

	static HashMap<String, Integer> store(Player player) {
		if (player.zelusCodex == null) {
			player.zelusCodex = new HashMap<>();
		}
		return player.zelusCodex;
	}

	public int progress(Player player) {
		int value = store(player).getOrDefault(name(), 0);
		return this == AWAKENING_IN_ZELUS ? Integer.bitCount(value) : value;
	}

	public boolean claimed(Player player) {
		return store(player).getOrDefault("claimed." + name(), 0) == 1;
	}

	void markClaimed(Player player) {
		store(player).put("claimed." + name(), 1);
	}

	/** Adds to a counted task; tells the player the moment it completes. */
	public void increment(Player player, int amount) {
		if (progress(player) >= goal) {
			return;
		}
		store(player).merge(name(), amount, Integer::sum);
		if (progress(player) >= goal) {
			ZelusCodex.announceComplete(player, title);
		}
	}

	/** Sets one Awakening step. */
	public void step(Player player, int flag) {
		int before = store(player).getOrDefault(name(), 0);
		if ((before & flag) != 0) {
			return;
		}
		store(player).put(name(), before | flag);
		if (progress(player) >= goal) {
			ZelusCodex.announceComplete(player, title);
		}
	}
}

package io.ruin.model.content.gearloadouts;

import io.ruin.model.content.pvppreset.SpawnedItems;
import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.dialogue.OptionsDialogue;
import io.ruin.model.inter.questtab.dashboard.QuestDashboards;
import io.ruin.model.inter.utils.Option;
import io.ruin.model.item.Item;
import io.ruin.model.item.containers.Inventory;
import io.ruin.model.skills.magic.SpellBook;

import java.util.Map;

/**
 * The Battle Presets (1155) free supply buttons: Food, Potions, Veng runes and Barrage runes. Every item is a spawned
 * (phantom) item ({@link SpawnedItems}): usable in a fight, never bankable / tradeable / droppable / lootable.
 * Same gate as loading a free kit: PvP Mode accounts, outside the Wilderness, out of combat. Runes are topped up to
 * {@link #RUNES} rather than stacked, so spam-clicking can't build a pile.
 */
public final class FreeSupplies {

	public static final int FOOD = 0, POTIONS = 1, VENG = 2, BARRAGE = 3;
	private static final int COMBAT_LOCK_TICKS = 17, RUNES = 5_000;
	private static final int DARK_CRAB = 11936, SUPER_ATTACK = 2436, SUPER_STRENGTH = 2440, SUPER_DEFENCE = 2442,
		SARA_BREW = 6685, SANFEW = 10925;
	private static final int ASTRAL = 9075, DEATH = 560, EARTH = 557, BLOOD = 565, WATER = 555;
	private static final String TAG = "<col=ff981f>[Battle Presets]</col> ";

	private FreeSupplies() {
	}

	public static void give(Player player, int kind) {
		if (!QuestDashboards.requirePvpMode(player))
			return;
		if (player.wildernessLevel > 0) {
			player.sendMessage("You can't spawn supplies in the Wilderness.");
			return;
		}
		if (player.getCombat().isAttacking(COMBAT_LOCK_TICKS) || player.getCombat().isDefending(COMBAT_LOCK_TICKS)) {
			player.sendMessage("You can't spawn supplies while in combat.");
			return;
		}
		Inventory inv = player.getInventory();
		switch (kind) {
			case FOOD -> {
				int free = inv.getFreeSlots();
				if (free == 0) {
					player.sendMessage(TAG + "Your inventory is full.");
					return;
				}
				for (int i = 0; i < free; i++)
					inv.add(SpawnedItems.spawned(DARK_CRAB, 1));
				player.sendMessage(TAG + "Filled " + free + " free slot" + (free == 1 ? "" : "s") + " with Dark crabs.");
			}
			case POTIONS -> {
				int[] set = {SUPER_ATTACK, SUPER_STRENGTH, SUPER_DEFENCE, SARA_BREW, SANFEW};
				int given = 0;
				for (int id : set) {
					if (inv.getFreeSlots() == 0)
						break;
					inv.add(SpawnedItems.spawned(id, 1));
					given++;
				}
				player.sendMessage(given == 0 ? TAG + "Your inventory is full."
					: TAG + "Spawned " + given + " potion" + (given == 1 ? "" : "s")
					+ (given < set.length ? " (inventory full)."
					: ": Super attack, Super strength, Super defence, Saradomin brew and Sanfew serum."));
			}
			case VENG -> {
				if (topUp(player, ASTRAL, DEATH, EARTH))
					player.sendMessage(TAG + "Vengeance runes topped up (" + RUNES + " Astral / Death / Earth).");
				offerSpellbook(player, SpellBook.LUNAR, "Lunar");
			}
			case BARRAGE -> {
				if (topUp(player, BLOOD, DEATH, WATER))
					player.sendMessage(TAG + "Barrage runes topped up (" + RUNES + " Blood / Death / Water).");
				offerSpellbook(player, SpellBook.ANCIENT, "Ancient");
			}
			default -> {
				return;
			}
		}
		inv.sendUpdates();
		io.ruin.model.content.cosmetic.Stardust.celebrate(player);
	}

	/** Brings each rune's spawned stack up to RUNES. False if there wasn't room for a new stack. */
	private static boolean topUp(Player player, int... runes) {
		Inventory inv = player.getInventory();
		Map<String, String> flag = SpawnedItems.spawned(runes[0], 1).copyOfAttributes();
		boolean ok = true;
		for (int id : runes) {
			int have = 0;
			for (Item item : inv.getItems()) {
				if (item != null && item.getId() == id && SpawnedItems.isSpawned(item))
					have += item.getAmount();
			}
			if (have >= RUNES)
				continue;
			if (inv.add(id, RUNES - have, flag) == 0) {
				player.sendMessage(TAG + "You need a free inventory slot for the runes.");
				ok = false;
			}
		}
		return ok;
	}

	/** "Switch to the X Magic Book?" when the player is on another spellbook. */
	private static void offerSpellbook(Player player, SpellBook book, String name) {
		if (book.isActive(player))
			return;
		player.dialogue(new OptionsDialogue("Switch to " + name + " Magic Book?",
			new Option("Yes", () -> {
				book.setActive(player);
				player.sendMessage(TAG + "Your spellbook is now " + name + ".");
			}),
			new Option("No")));
	}
}

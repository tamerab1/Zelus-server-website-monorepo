package io.ruin.model.content.wildernessforge;

import io.ruin.api.utils.Random;
import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.InterfaceHandler;
import io.ruin.model.inter.ToplevelComponent;
import io.ruin.model.inter.actions.DefaultAction;
import io.ruin.model.inter.actions.SimpleAction;
import io.ruin.model.inter.handlers.ItemStatsViewer;
import io.ruin.model.inter.dialogue.OptionsDialogue;
import io.ruin.model.inter.utils.Option;
import io.ruin.model.item.Item;

import java.util.*;

/**
 * Wilderness Forge: PK item upgrades on interface 1168 (BuildWildernessForgeInterface, 2026-10-15; it used to borrow
 * 1002, which stays untouched as the builders' template source): 5 category bezel tabs, a scrolling 4 x 15 grid of
 * recessed item slots (Select / Examine), the required-items tray, then the Upgrade Result and Success Rate boxes and the
 * Upgrade Now! button.
 */
public class WildernessForge {

	private static final int INTERFACE = 1168;
	private static final String TITLE = "Wilderness Forge";

	private static final int CLOSE_BUTTON = 2;
	private static final int TITLE_TEXT = 3;
	/** Tabs: bezel button +0, active recess +1, trim +2, label +3. */
	private static final int FIRST_TAB = 4, TAB_STRIDE = 4;
	private static final String[] TAB_NAMES = {"Weapons", "Accessories", "Armour", "Jewellery", "Utility"};
	private static final int SCROLL = 30, SCROLLBAR = 31, SCROLL_HEIGHT = 212, SLOT_PITCH = 38;
	/** Grid slots: well +0, border +1, selected trim +2, item +3 (Select / Examine). */
	private static final int FIRST_SLOT = 32, SLOT_STRIDE = 4, ROWS = 15, COLUMNS = 4, SLOTS = ROWS * COLUMNS;
	private static final int REQUIRED_TEXT = 281;
	/** Required slots: fill +0, border +1, item +2 (Examine). */
	private static final int FIRST_REQUIRED = 282, REQUIRED_STRIDE = 3, REQUIRED_SLOTS = 6;
	private static final int RESULT_ITEM = 311, RESULT_NAME = 312, SUCCESS_TEXT = 318, SUCCESS_NOTE = 319, UPGRADE_BUTTON = 320;


	public enum Category {WEAPONS, ACCESSORIES, ARMOUR, JEWELLERY, UTILITY}

	/**
	 * One upgrade: the base item plus the extra materials become the result. On a failed roll the
	 * materials are consumed and the base item is kept.
	 */
	public static final class Recipe {
		final Category category;
		final int baseId;
		final int resultId;
		final Item[] materials;
		final int successChance; // percent, 1-100

		Recipe(Category category, int baseId, int resultId, int successChance, Item... materials) {
			if (materials.length > REQUIRED_SLOTS - 1)
				throw new IllegalArgumentException("max " + (REQUIRED_SLOTS - 1) + " materials");
			this.category = category;
			this.baseId = baseId;
			this.resultId = resultId;
			this.successChance = successChance;
			this.materials = materials;
		}

		Item[] required() {
			Item[] all = new Item[materials.length + 1];
			all[0] = new Item(baseId, 1);
			System.arraycopy(materials, 0, all, 1, materials.length);
			return all;
		}
	}

	/*
	 * Blood money prices by tier (2026-10-14), against ~50-100 BM per player kill (Killer.reward) plus daily tasks,
	 * reward boxes, revenants and Wanted bounties. On a failed roll the blood money is spent and the base item kept,
	 * so the expected cost of a success is price / chance:
	 *   A  entry gear (Shayzien 5, Trailblazer t3, dragonstone, obsidian, Dagon'hai, d scim/dagger, g maul, glory, torso)   750, 70%
	 *   B  mid gear (Barrows, dark bow, defender, barrows gloves, d boots, imbued cape, (i) rings, fury, occult ...)      1,500, 60%
	 *   C  high gear (Armadyl/Bandos pieces, whip, DWH, ballista, d crossbow, claws, tormented, boots, lightbearer ...)   3,000, 50%
	 *   D  best in slot (AGS, BGS, ACB, Vesta's, Voidwaker, torture, anguish, Virtus, Sunfire fanatic)                    5,000, 40%
	 */
	private static final int BLOOD_MONEY = 13307;
	private static final Item TIER_A = new Item(BLOOD_MONEY, 750), TIER_B = new Item(BLOOD_MONEY, 1_500),
		TIER_C = new Item(BLOOD_MONEY, 3_000), TIER_D = new Item(BLOOD_MONEY, 5_000);

	// The list shows 4 items per row: armour is ordered one full set per row (pieces, then the set weapon).
	private static final List<Recipe> RECIPES = List.of(
		// Armour row 1: Bloodrage Dharok's set
		new Recipe(Category.ARMOUR, 4716, 42300, 60, TIER_B), // Dharok's helm -> Bloodrage
		new Recipe(Category.ARMOUR, 4720, 42304, 60, TIER_B), // Dharok's platebody -> Bloodrage
		new Recipe(Category.ARMOUR, 4722, 42306, 60, TIER_B), // Dharok's platelegs -> Bloodrage
		new Recipe(Category.ARMOUR, 4718, 42302, 60, TIER_B), // Dharok's greataxe -> Bloodrage
		// Armour row 2: Sanguine Karil's set
		new Recipe(Category.ARMOUR, 4732, 42308, 60, TIER_B), // Karil's coif -> Sanguine
		new Recipe(Category.ARMOUR, 4736, 42312, 60, TIER_B), // Karil's leathertop -> Sanguine
		new Recipe(Category.ARMOUR, 4738, 42314, 60, TIER_B), // Karil's leatherskirt -> Sanguine
		new Recipe(Category.ARMOUR, 4734, 42310, 60, TIER_B), // Karil's crossbow -> Sanguine
		// Armour row 3: Arcanist Ahrim's set
		new Recipe(Category.ARMOUR, 4708, 42316, 60, TIER_B), // Ahrim's hood -> Arcanist
		new Recipe(Category.ARMOUR, 4712, 42320, 60, TIER_B), // Ahrim's robetop -> Arcanist
		new Recipe(Category.ARMOUR, 4714, 42322, 60, TIER_B), // Ahrim's robeskirt -> Arcanist
		new Recipe(Category.ARMOUR, 4710, 42318, 60, TIER_B), // Ahrim's staff -> Arcanist
		// Armour row 4: Gorewing Armadyl set
		new Recipe(Category.ARMOUR, 11826, 42362, 50, TIER_C), // Armadyl helmet -> Celestial
		new Recipe(Category.ARMOUR, 11828, 42364, 50, TIER_C), // Armadyl chestplate -> Celestial
		new Recipe(Category.ARMOUR, 11830, 42366, 50, TIER_C), // Armadyl chainskirt -> Celestial
		new Recipe(Category.ARMOUR, 11785, 42374, 40, TIER_D), // Armadyl crossbow -> Gorewing crossbow
		// Armour row 5: Warbringer Bandos set
		new Recipe(Category.ARMOUR, 11832, 42368, 50, TIER_C), // Bandos chestplate -> Warbringer
		new Recipe(Category.ARMOUR, 11834, 42370, 50, TIER_C), // Bandos tassets -> Warbringer
		new Recipe(Category.ARMOUR, 11836, 42372, 50, TIER_C), // Bandos boots -> Warbringer
		new Recipe(Category.ARMOUR, 11804, 42376, 40, TIER_D), // Bandos godsword -> Warbringer godsword
		// Armour row 6: Gladiator's torso
		new Recipe(Category.ARMOUR, 10551, 42360, 70, TIER_A), // Fighter torso -> Gladiator's torso
		rowBreak(Category.ARMOUR),
		// Armour: Nightweaver (Dagon'hai)
		new Recipe(Category.ARMOUR, 24288, 42426, 70, TIER_A), // Dagon'hai hat -> Nightweaver
		new Recipe(Category.ARMOUR, 24291, 42428, 70, TIER_A), // Dagon'hai robe top -> Nightweaver
		new Recipe(Category.ARMOUR, 24294, 42430, 70, TIER_A), // Dagon'hai robe bottom -> Nightweaver
		rowBreak(Category.ARMOUR),
		// Armour: Glacius (Virtus)
		new Recipe(Category.ARMOUR, 30521, 42452, 40, TIER_D), // Virtus mask -> Glacius
		new Recipe(Category.ARMOUR, 30522, 42454, 40, TIER_D), // Virtus robe top -> Glacius
		new Recipe(Category.ARMOUR, 30523, 42456, 40, TIER_D), // Virtus robe bottom -> Glacius
		rowBreak(Category.ARMOUR),
		// Armour: Magmacore (Obsidian)
		new Recipe(Category.ARMOUR, 21298, 42472, 70, TIER_A), // Obsidian helmet -> Magmacore
		new Recipe(Category.ARMOUR, 21301, 42474, 70, TIER_A), // Obsidian platebody -> Magmacore
		new Recipe(Category.ARMOUR, 21304, 42476, 70, TIER_A), // Obsidian platelegs -> Magmacore
		rowBreak(Category.ARMOUR),
		// Armour: Starheart (Dragonstone)
		new Recipe(Category.ARMOUR, 24034, 42478, 70, TIER_A), // Dragonstone full helm -> Starheart
		new Recipe(Category.ARMOUR, 24037, 42480, 70, TIER_A), // Dragonstone platebody -> Starheart
		new Recipe(Category.ARMOUR, 24040, 42482, 70, TIER_A), // Dragonstone platelegs -> Starheart
		new Recipe(Category.ARMOUR, 24043, 42484, 70, TIER_A), // Dragonstone boots -> Starheart
		new Recipe(Category.ARMOUR, 24046, 42486, 70, TIER_A), // Dragonstone gauntlets -> Starheart
		new Recipe(Category.WEAPONS, 11802, 42324, 40, TIER_D), // Armadyl godsword -> Bloodforged godsword
		new Recipe(Category.WEAPONS, 13652, 42326, 50, TIER_C), // Dragon claws -> Bloodfang claws
		new Recipe(Category.WEAPONS, 4587, 42328, 70, TIER_A), // Dragon scimitar -> Vampiric scimitar
		new Recipe(Category.WEAPONS, 5698, 42330, 70, TIER_A), // Dragon dagger(p++) -> Nightshade dagger(p++)
		new Recipe(Category.WEAPONS, 4153, 42332, 70, TIER_A), // Granite maul -> Obsidian maul
		new Recipe(Category.WEAPONS, 11235, 42334, 60, TIER_B), // Dark bow -> Eclipse bow
		new Recipe(Category.WEAPONS, 19481, 42336, 50, TIER_C), // Heavy ballista -> Dreadblood ballista
		new Recipe(Category.WEAPONS, 13576, 42338, 50, TIER_C), // Dragon warhammer -> Ravager warhammer
		new Recipe(Category.WEAPONS, 22613, 42340, 40, TIER_D), // Vesta's longsword -> Bloodletter longsword
		new Recipe(Category.WEAPONS, 21902, 42378, 50, TIER_C), // Dragon crossbow -> Hellfire crossbow
		new Recipe(Category.WEAPONS, 27690, 42380, 40, TIER_D), // Voidwaker -> Riftbreaker
		new Recipe(Category.WEAPONS, 4151, 42232, 50, TIER_C), // Abyssal whip -> Tidal whip
		new Recipe(Category.WEAPONS, 13111, 42442, 60, TIER_B), // Wilderness sword 4 -> Wildfang blade
		new Recipe(Category.WEAPONS, 23528, 42446, 60, TIER_B), // Sarachnis cudgel -> Broodmother cudgel
		new Recipe(Category.WEAPONS, 8872, 42448, 50, TIER_C), // Ursine chainmace -> Grizzlemaw chainmace
		new Recipe(Category.WEAPONS, 27610, 42450, 50, TIER_C), // Venator bow -> Skyhunter bow (no charges needed)
		new Recipe(Category.JEWELLERY, 6585, 42342, 60, TIER_B), // Amulet of fury -> Amulet of havoc
		new Recipe(Category.JEWELLERY, 12002, 42344, 60, TIER_B), // Occult necklace -> Eldritch necklace
		new Recipe(Category.JEWELLERY, 11773, 42346, 60, TIER_B), // Berserker ring (i) -> Warlord ring
		new Recipe(Category.JEWELLERY, 11771, 42348, 60, TIER_B), // Archers ring (i) -> Deadeye ring
		new Recipe(Category.JEWELLERY, 11770, 42350, 60, TIER_B), // Seers ring (i) -> Mystic eye ring
		new Recipe(Category.JEWELLERY, 19553, 42382, 40, TIER_D), // Amulet of torture -> Amulet of carnage
		new Recipe(Category.JEWELLERY, 19547, 42384, 40, TIER_D), // Necklace of anguish -> Necklace of agony
		new Recipe(Category.JEWELLERY, 19710, 42386, 50, TIER_C), // Ring of suffering (i) -> Ring of retribution
		new Recipe(Category.JEWELLERY, 25975, 42388, 50, TIER_C), // Lightbearer -> Sunbearer
		new Recipe(Category.JEWELLERY, 12853, 42444, 60, TIER_B), // Amulet of the damned -> Amulet of the forsaken
		new Recipe(Category.ACCESSORIES, 12954, 42352, 60, TIER_B), // Dragon defender -> Bulwark defender
		new Recipe(Category.ACCESSORIES, 7462, 42354, 60, TIER_B), // Barrows gloves -> Reaper gloves
		new Recipe(Category.ACCESSORIES, 11840, 42356, 60, TIER_B), // Dragon boots -> Hellstride boots
		new Recipe(Category.ACCESSORIES, 21795, 42358, 60, TIER_B), // Imbued zamorak cape -> Cape of chaos
		new Recipe(Category.ACCESSORIES, 19544, 42390, 50, TIER_C), // Tormented bracelet -> Wraithbound bracelet
		new Recipe(Category.ACCESSORIES, 13239, 42392, 50, TIER_C), // Primordial boots -> Ragefire boots
		new Recipe(Category.ACCESSORIES, 13237, 42394, 50, TIER_C), // Pegasian boots -> Bloodstrider boots
		new Recipe(Category.ACCESSORIES, 13235, 42396, 50, TIER_C), // Eternal boots -> Starweave boots
		new Recipe(Category.UTILITY, 12791, 27281, 75, new Item(BLOOD_MONEY, 2_500)), // Rune pouch -> Divine rune pouch
		new Recipe(Category.UTILITY, 1712, 19707, 70, TIER_A), // Amulet of glory(4) -> Amulet of eternal glory
		new Recipe(Category.UTILITY, 20724, 27641, 50, TIER_C), // Imbued heart -> Saturated heart
		new Recipe(Category.UTILITY, 23685, 42398, 100, new Item(23733, 1), new Item(BLOOD_MONEY, 200)) // Divine super combat(4) + divine ranging(4) -> Divine wrath potion(4)
	);

	private static final class State {
		Category category = Category.WEAPONS;
		Recipe selected;
	}

	private static final Map<Player, State> STATES = Collections.synchronizedMap(new WeakHashMap<>());

	private static State state(Player player) {
		return STATES.computeIfAbsent(player, p -> new State());
	}

	/** A list entry that ends the current row: the rest of the row is left empty. */
	private static Recipe rowBreak(Category category) {
		return new Recipe(category, -1, -1, 0);
	}

	/** The category's recipes in list order; null entries are empty slots left by row breaks. */
	private static List<Recipe> recipes(Category category) {
		List<Recipe> list = new ArrayList<>();
		for (Recipe recipe : RECIPES) {
			if (recipe.category != category)
				continue;
			if (recipe.baseId == -1) {
				while (list.size() % COLUMNS != 0)
					list.add(null);
				continue;
			}
			list.add(recipe);
		}
		return list;
	}

	public static void open(Player player) {
		if (!player.isVisibleInterface(INTERFACE)) {
			player.openInterface(ToplevelComponent.MAINMODAL, INTERFACE);
			player.getPacketSender().sendString(INTERFACE, TITLE_TEXT, TITLE);
			player.getPacketSender().sendString(INTERFACE, UPGRADE_BUTTON + 1, "Upgrade Now!");
			update(player, true);
		} else {
			update(player, false);
		}
	}

	/** listChanged: the category changed, so the grid is refilled and the scrollbar reset (that script scrolls to top). */
	private static void update(Player player, boolean listChanged) {
		var ps = player.getPacketSender();
		State state = state(player);
		for (int i = 0; i < TAB_NAMES.length; i++) {
			int tab = FIRST_TAB + i * TAB_STRIDE;
			boolean active = state.category.ordinal() == i;
			ps.setHidden(INTERFACE, tab + 1, !active);
			ps.setHidden(INTERFACE, tab + 2, !active);
			ps.sendString(INTERFACE, tab + 3, (active ? "<col=ffc35a>" : "<col=d8d0c0>") + TAB_NAMES[i]);
		}

		List<Recipe> list = recipes(state.category);
		for (int s = 0; s < SLOTS; s++) {
			int slot = FIRST_SLOT + s * SLOT_STRIDE;
			Recipe recipe = s < list.size() ? list.get(s) : null;
			boolean exists = s < list.size();
			if (listChanged) {
				ps.setHidden(INTERFACE, slot, !exists);
				ps.setHidden(INTERFACE, slot + 1, !exists);
				ps.setHidden(INTERFACE, slot + 3, recipe == null);
				if (recipe != null)
					ps.sendItem(INTERFACE, slot + 3, recipe.resultId, 1);
			}
			ps.setHidden(INTERFACE, slot + 2, recipe == null || recipe != state.selected);
		}
		if (listChanged) {
			int rows = (list.size() + COLUMNS - 1) / COLUMNS;
			int contentHeight = rows * SLOT_PITCH + 2;
			int count = contentHeight <= SCROLL_HEIGHT ? 0 : (int) Math.ceil((contentHeight - SCROLL_HEIGHT / 3D) / 15D);
			io.ruin.model.ScrollbarClientScript.create().interfaceId(INTERFACE).containerId(SCROLL).scrollbarChildId(SCROLLBAR)
				.childrenCount(count).withDarkGraphics().build().send(player);
		}

		Recipe selected = state.selected;
		Item[] required = selected == null ? new Item[0] : selected.required();
		for (int i = 0; i < REQUIRED_SLOTS; i++) {
			int item = FIRST_REQUIRED + i * REQUIRED_STRIDE + 2;
			boolean used = i < required.length;
			ps.setHidden(INTERFACE, item, !used);
			if (used)
				ps.sendItem(INTERFACE, item, required[i].getId(), required[i].getAmount());
		}
		ps.setHidden(INTERFACE, RESULT_ITEM, selected == null);
		if (selected == null) {
			ps.sendString(INTERFACE, REQUIRED_TEXT, "Select an item to upgrade.");
			ps.sendString(INTERFACE, RESULT_NAME, "<col=9a9a9a>Nothing selected</col>");
			ps.sendString(INTERFACE, SUCCESS_TEXT, "<col=9a9a9a>-</col>");
			ps.sendString(INTERFACE, SUCCESS_NOTE, "");
		} else {
			String name = new Item(selected.resultId, 1).getDef().name;
			ps.sendItem(INTERFACE, RESULT_ITEM, selected.resultId, 1);
			ps.sendString(INTERFACE, REQUIRED_TEXT, "Required items for " + name + ":");
			ps.sendString(INTERFACE, RESULT_NAME, "<col=ffc35a>" + name + "</col>");
			int chance = selected.successChance;
			ps.sendString(INTERFACE, SUCCESS_TEXT, (chance >= 75 ? "<col=40ac3b>" : chance >= 50 ? "<col=ff981f>" : "<col=ff5c4d>")
				+ chance + "%");
			ps.sendString(INTERFACE, SUCCESS_NOTE, chance >= 100 ? "Guaranteed."
				: "On a failure the materials are used up; your item is kept.");
		}
	}

	private static void selectCategory(Player player, Category category) {
		State state = state(player);
		boolean changed = state.category != category;
		state.category = category;
		state.selected = null;
		update(player, changed);
	}

	private static void selectRecipe(Player player, int slot) {
		List<Recipe> list = recipes(state(player).category);
		if (slot < 0 || slot >= list.size() || list.get(slot) == null)
			return;
		state(player).selected = list.get(slot);
		update(player, false);
	}

	private static void confirmUpgrade(Player player) {
		Recipe recipe = state(player).selected;
		if (recipe == null) {
			player.sendMessage("Select an item to upgrade first.");
			return;
		}
		if (!hasRequired(player, recipe)) {
			player.sendMessage("You don't have all the required items for this upgrade.");
			return;
		}
		String result = new Item(recipe.resultId, 1).getDef().name;
		player.dialogue(new OptionsDialogue("Upgrade to " + result + "? (" + recipe.successChance + "% success)",
			new Option("Yes, forge it.", () -> {
				forge(player, recipe);
				open(player);
			}),
			new Option("No.", () -> open(player))));
	}

	private static boolean hasRequired(Player player, Recipe recipe) {
		for (Item item : recipe.required()) {
			if (player.getInventory().getAmount(item.getId()) < item.getAmount())
				return false;
		}
		return true;
	}

	private static void forge(Player player, Recipe recipe) {
		// Re-check: the inventory may have changed while the dialogue was open.
		if (!hasRequired(player, recipe)) {
			player.sendMessage("You don't have all the required items for this upgrade.");
			return;
		}
		if (io.ruin.model.content.pvppreset.SpawnedItems.refuseIfSpawned(player, "forged",
				java.util.Arrays.stream(recipe.required()).mapToInt(Item::getId).toArray()))
			return;
		for (Item material : recipe.materials)
			player.getInventory().remove(material.getId(), material.getAmount());
		String result = new Item(recipe.resultId, 1).getDef().name;
		if (Random.get(1, 100) <= recipe.successChance) {
			player.getInventory().remove(recipe.baseId, 1);
			player.getInventory().add(recipe.resultId, 1);
			if (recipe.resultId == DIVINE_RUNE_POUCH)
				moveRunesToDivinePouch(player);
			player.sendMessage("<col=ff0000>The Wilderness Forge roars - you forged " + result + "!");
		} else {
			player.sendMessage("The forge sputters out. Your materials were consumed, but your item survived.");
		}
	}

	private static final int DIVINE_RUNE_POUCH = 27281;

	/** Item stats viewer; its Close button comes back to the forge (tab and selection are kept). */
	private static void examine(Player player, int itemId) {
		if (itemId <= 0)
			return;
		ItemStatsViewer.open(player, itemId, () -> open(player));
	}

	/** Rune pouch contents live on the player, not the item: carry them over to the new divine pouch. */
	private static void moveRunesToDivinePouch(Player player) {
		if (!player.DivinerunePouch.isEmpty()) {
			player.sendMessage("Your old rune pouch's runes stay in it, because your divine rune pouch already holds runes.");
			return;
		}
		for (Item rune : player.getRunePouch().getItems()) {
			if (rune != null)
				player.DivinerunePouch.add(rune.getId(), rune.getAmount());
		}
		player.getRunePouch().clear();
	}

	public static void register() {
		InterfaceHandler.register(INTERFACE, handler -> {
			handler.actions[CLOSE_BUTTON] = (SimpleAction) player -> player.closeInterface(ToplevelComponent.MAINMODAL);
			for (int i = 0; i < TAB_NAMES.length; i++) {
				Category category = Category.values()[i];
				handler.actions[FIRST_TAB + i * TAB_STRIDE] = (SimpleAction) player -> selectCategory(player, category);
			}
			for (int s = 0; s < SLOTS; s++) {
				int index = s;
				handler.actions[FIRST_SLOT + s * SLOT_STRIDE + 3] = (DefaultAction) (player, option, slot, itemId) -> {
					if (option == 2) { // Examine
						List<Recipe> list = recipes(state(player).category);
						if (index < list.size() && list.get(index) != null)
							examine(player, list.get(index).resultId);
						return;
					}
					selectRecipe(player, index);
				};
			}
			for (int i = 0; i < REQUIRED_SLOTS; i++) {
				int index = i;
				handler.actions[FIRST_REQUIRED + i * REQUIRED_STRIDE + 2] = (SimpleAction) player -> {
					Recipe recipe = state(player).selected;
					if (recipe != null && index < recipe.required().length)
						examine(player, recipe.required()[index].getId());
				};
			}
			handler.actions[RESULT_ITEM] = (SimpleAction) player -> {
				Recipe recipe = state(player).selected;
				if (recipe != null)
					examine(player, recipe.resultId);
			};
			handler.actions[UPGRADE_BUTTON] = (SimpleAction) WildernessForge::confirmUpgrade;
		});
	}
}

package io.ruin.model.content.upgradesystem;

import io.ruin.cache.ObjType;
import io.ruin.model.ScrollbarClientScript;
import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.InterfaceHandler;
import io.ruin.model.inter.ToplevelComponent;
import io.ruin.model.inter.actions.SimpleAction;
import io.ruin.model.inter.dialogue.YesNoDialogue;
import io.ruin.model.inter.handlers.ItemStatsViewer;
import io.ruin.model.item.Item;
import io.ruin.model.item.containers.Equipment;
import io.ruin.network.PacketSender;

import java.util.ArrayList;
import java.util.List;

/**
 * The Zelus Relic Forge (interface 1158, built by .dev/cache-restore-tool/src/BuildRelicForgeInterface.java), the item
 * upgrader that replaced the legacy screen 1110. It only draws and dispatches: recipes, success rates (base + donator
 * bonus + pity), the roll, what is consumed and the broadcasts all stay in {@link UpgradeItems}.
 * <ul>
 *     <li>left: Weapons / Armour / Utility tabs (from the outcome's equipment slot) and the recipe cards;</li>
 *     <li>centre: the base item (the first ingredient) -> the outcome, and their stat differences;</li>
 *     <li>right: the other ingredients and the catalyst (owned / required), the success gauge, the failure rule and
 *     Forge Upgrade.</li>
 * </ul>
 */
public final class RelicForge {

	public static final int INTERFACE = 1158;
	private static final int CLOSE = 2, TITLE = 3, SUB_HEADER = 4;
	/** Stone buttons (IfKit.stoneButton, 2026-10-20 redesign): fill (click), +9 pressed layer, +19 label. */
	private static final int PRESSED = 9, LABEL = 19;
	private static final int FIRST_TAB = 20, TAB_STRIDE = 20;
	private static final int SCROLL = 80, SCROLLBAR = 81, SCROLL_HEIGHT = 208;
	/** Cards: layer, fill (click), selected recess + shadow, divider, item well, item, name, tier pill + text, rate. */
	private static final int FIRST_CARD = 82, CARD_STRIDE = 11, CARD_BOX = 1, CARD_SELECTED = 2, CARD_ITEM = 6, CARD_NAME = 7,
		CARD_TIER = 9, CARD_RATE = 10, CARD_PITCH = 40, CARDS = 40;
	/** The base socket holds the item, or its translucent silhouette (BASE_GHOST) while the player lacks it. */
	private static final int BASE_ITEM = 532, BASE_GHOST = 533, BASE_NAME = 534, OUTCOME_ITEM = 545, OUTCOME_NAME = 546, STAT_DIFF = 550;
	/** Reagents: well, rim, item, owned/required (under the slot, clear of the item's stack number). */
	private static final int FIRST_REAGENT = 556, REAGENT_STRIDE = 4, REAGENT_ITEM = 2, REAGENT_COUNT = 3, REAGENTS = 6;
	/** The segmented success meter: 20 blocks, red -> amber -> green, lit by the server. */
	private static final int RATE = 580, FIRST_SEGMENT = 583, SEGMENTS = 20, BREAKDOWN = 603, FAILURE = 604, FORGE = 605;
	/** Search bar: input box (click = search), its text, rim, magnifier, Clear box + label. */
	private static final int SEARCH = 5, SEARCH_TEXT = 6, SEARCH_CLEAR = 9;

	private static final String[] CATEGORIES = {"Weapons", "Armour", "Utility"};
	private static final String[] BONUS_NAMES = {"Stab", "Slash", "Crush", "Magic", "Range", "Stab def", "Slash def",
		"Crush def", "Magic def", "Range def", "Str", "Rng str", "Mag dmg", "Prayer"};
	/** Clientscript 10752 set_opbase(component, name) (AddOpBaseScript): the slot's hover / right-click names the item. */
	private static final int SET_OPBASE = 10752;
	/** Played on a successful forge (the same burst the Pit champion gets). */
	private static final int SUCCESS_GFX = io.ruin.model.content.cosmetic.Stardust.GOLD; // gold stardust (was 199)

	private static final String GOLD = "<col=ffc35a>", YELLOW = "<col=ffff00>", GREY = "<col=9a9a9a>", WHITE = "<col=ffffff>",
		GREEN = "<col=00ff00>", RED = "<col=ff3333>", ORANGE = "<col=ff981f>", END = "</col>";

	private RelicForge() {
	}

	static void register() {
		InterfaceHandler.register(INTERFACE, h -> {
			h.actions[CLOSE] = (SimpleAction) Player::closeInterfaces;
			for (int t = 0; t < CATEGORIES.length; t++) {
				int category = t;
				h.actions[FIRST_TAB + t * TAB_STRIDE] = (SimpleAction) p -> {
					UpgradeSystemInterface s = p.getUpgradeSystemInterface();
					s.forgeCategory = category;
					s.forgeSearch = null;
					List<UpgradeItems> list = recipes(category);
					s.currentSection = list.isEmpty() ? null : list.get(0);
					refresh(p, true);
				};
			}
			for (int c = 0; c < CARDS; c++) {
				int index = c;
				h.actions[FIRST_CARD + c * CARD_STRIDE + CARD_BOX] = (SimpleAction) p -> {
					List<UpgradeItems> list = list(p);
					if (index < list.size()) {
						p.getUpgradeSystemInterface().currentSection = list.get(index);
						refresh(p, false);
					}
				};
			}
			h.actions[BASE_ITEM] = (SimpleAction) p -> examine(p, baseItem(p, selected(p)));
			h.actions[BASE_GHOST] = (SimpleAction) p -> examine(p, baseItem(p, selected(p)));
			h.actions[OUTCOME_ITEM] = (SimpleAction) p -> examine(p, selected(p) == null ? null : selected(p).outcome());
			for (int k = 0; k < REAGENTS; k++) {
				int slot = k;
				h.actions[FIRST_REAGENT + k * REAGENT_STRIDE + REAGENT_ITEM] = (SimpleAction) p -> {
					List<Item> reagents = reagents(p, selected(p));
					if (slot < reagents.size())
						examine(p, reagents.get(slot));
				};
			}
			h.actions[FORGE] = (SimpleAction) RelicForge::forge;
			h.actions[SEARCH_CLEAR] = (SimpleAction) p -> {
				UpgradeSystemInterface s = p.getUpgradeSystemInterface();
				if (s.forgeSearch != null) {
					s.forgeSearch = null;
					s.currentSection = null;
					open(p);
				}
			};
			h.actions[SEARCH] = (SimpleAction) p -> {
				UpgradeSystemInterface s = p.getUpgradeSystemInterface();
				p.stringInput("Search recipes (outcome or ingredient name):", text -> {
					String query = text == null ? "" : text.trim().toLowerCase();
					s.forgeSearch = query.isEmpty() ? null : query;
					List<UpgradeItems> found = list(p);
					if (s.forgeSearch != null && found.isEmpty())
						p.sendMessage("No forge recipes match \"" + query + "\".");
					s.currentSection = found.isEmpty() ? null : found.get(0);
					open(p); // the input box closed the screen
				});
			};
		});
	}

	public static void open(Player player) {
		UpgradeSystemInterface s = player.getUpgradeSystemInterface();
		List<UpgradeItems> shown = list(player);
		if (s.currentSection == null || !shown.contains(s.currentSection))
			s.currentSection = shown.isEmpty() ? null : shown.get(0);
		player.openInterface(ToplevelComponent.MAINMODAL, INTERFACE);
		PacketSender ps = player.getPacketSender();
		ps.sendString(INTERFACE, TITLE, ORANGE + "THE ZELUS RELIC FORGE · ITEM UPGRADER" + END);
		ps.sendString(INTERFACE, SUB_HEADER, "Upgrade weapons, armour and relics.");
		refresh(player, true);
	}

	private static UpgradeItems selected(Player player) {
		return player.getUpgradeSystemInterface().currentSection;
	}

	/** listChanged: the tab changed (cards + scrollbar resent; the scrollbar script resets the scroll position). */
	private static void refresh(Player player, boolean listChanged) {
		UpgradeSystemInterface s = player.getUpgradeSystemInterface();
		PacketSender ps = player.getPacketSender();
		for (int t = 0; t < CATEGORIES.length; t++) {
			int tab = FIRST_TAB + t * TAB_STRIDE;
			boolean active = t == s.forgeCategory && s.forgeSearch == null;
			ps.setHidden(INTERFACE, tab + PRESSED, !active);
			ps.sendString(INTERFACE, tab + LABEL, (active ? GOLD : ORANGE) + CATEGORIES[t] + END);
		}
		List<UpgradeItems> list = list(player);
		ps.sendString(INTERFACE, SEARCH_TEXT, s.forgeSearch != null ? WHITE + s.forgeSearch + END : GREY + "Search recipes..." + END);
		ps.sendString(INTERFACE, SEARCH_CLEAR + 1, s.forgeSearch != null ? ORANGE + "Clear" + END : GREY + "Clear" + END);
		for (int c = 0; c < CARDS; c++) {
			int card = FIRST_CARD + c * CARD_STRIDE;
			UpgradeItems recipe = c < list.size() ? list.get(c) : null;
			ps.setHidden(INTERFACE, card, recipe == null);
			if (recipe == null)
				continue;
			ps.setHidden(INTERFACE, card + CARD_SELECTED, recipe != s.currentSection);
			ps.setHidden(INTERFACE, card + CARD_SELECTED + 1, recipe != s.currentSection);
			if (listChanged) {
				ps.sendItem(INTERFACE, card + CARD_ITEM, recipe.outcome().getId(), 1);
				ps.sendString(INTERFACE, card + CARD_NAME, ORANGE + shorten(recipe.outcome().getDef().name, 40) + END); // wraps to 2 lines
				opBase(ps, card + CARD_BOX, ORANGE + recipe.outcome().getDef().name + END);
				ps.sendString(INTERFACE, card + CARD_TIER, tier(recipe));
			}
			ps.sendString(INTERFACE, card + CARD_RATE, "<col=40ac3b>" + rate(player, recipe) + "% rate" + END);
		}
		if (listChanged) {
			int contentHeight = list.size() * CARD_PITCH - 2;
			int count = contentHeight <= SCROLL_HEIGHT ? 0 : (int) Math.ceil((contentHeight - SCROLL_HEIGHT / 3D) / 15D);
			ScrollbarClientScript.create().interfaceId(INTERFACE).containerId(SCROLL).scrollbarChildId(SCROLLBAR)
				.childrenCount(count).withDarkGraphics().build().send(player);
		}
		sendRecipe(player, s.currentSection);
	}

	/** Centre and right columns for the selected recipe. */
	private static void sendRecipe(Player player, UpgradeItems recipe) {
		PacketSender ps = player.getPacketSender();
		ps.setHidden(INTERFACE, BASE_ITEM, recipe == null);
		ps.setHidden(INTERFACE, BASE_GHOST, true);
		ps.setHidden(INTERFACE, OUTCOME_ITEM, recipe == null);
		if (recipe == null) {
			ps.sendString(INTERFACE, BASE_NAME, GREY + "No recipes here" + END);
			ps.sendString(INTERFACE, OUTCOME_NAME, "");
			ps.sendString(INTERFACE, STAT_DIFF, "");
			for (int k = 0; k < REAGENTS; k++)
				hideReagent(ps, k);
			ps.sendString(INTERFACE, RATE, "");
			for (int seg = 0; seg < SEGMENTS; seg++)
				ps.setHidden(INTERFACE, FIRST_SEGMENT + seg, true);
			ps.sendString(INTERFACE, BREAKDOWN, "");
			ps.sendString(INTERFACE, FAILURE, "");
			ps.setHidden(INTERFACE, FORGE + PRESSED, true);
			ps.sendString(INTERFACE, FORGE + LABEL, GREY + "Forge Upgrade" + END);
			return;
		}
		// base -> outcome
		Item base = baseItem(player, recipe);
		boolean haveBase = UpgradeItems.ownedVariant(player, recipe.ingredientList()[0]) != null;
		// owned: the item; missing: its translucent silhouette (no more red text in the frame)
		ps.setHidden(INTERFACE, BASE_ITEM, !haveBase);
		ps.setHidden(INTERFACE, BASE_GHOST, haveBase);
		ps.sendItem(INTERFACE, haveBase ? BASE_ITEM : BASE_GHOST, base.getId(), base.getAmount());
		opBase(ps, BASE_ITEM, ORANGE + base.getDef().name + END + adminId(player, base.getId()));
		ps.sendString(INTERFACE, BASE_NAME, shorten(base.getDef().name, 28) + "<br>" + (haveBase ? GREEN + "In your inventory" + END : GREY + "Not in your inventory" + END));
		ps.sendItem(INTERFACE, OUTCOME_ITEM, recipe.outcome().getId(), 1);
		opBase(ps, OUTCOME_ITEM, ORANGE + recipe.outcome().getDef().name + END + adminId(player, recipe.outcome().getId()));
		ps.sendString(INTERFACE, OUTCOME_NAME, shorten(recipe.outcome().getDef().name, 44)); // wraps to 2 lines
		ps.sendString(INTERFACE, STAT_DIFF, statDiff(base, recipe));

		// reagents: the other ingredients, then the catalyst
		List<Item> reagents = reagents(player, recipe);
		for (int k = 0; k < REAGENTS; k++) {
			if (k >= reagents.size()) {
				hideReagent(ps, k);
				continue;
			}
			int slot = FIRST_REAGENT + k * REAGENT_STRIDE;
			Item item = reagents.get(k);
			int owned = player.getInventory().count(item.getId());
			boolean catalyst = k == reagents.size() - 1;
			boolean ok = catalyst ? player.getInventory().contains(item) : UpgradeItems.ownedVariant(player, recipe.ingredientList()[k + 1]) != null;
			for (int part = 0; part < REAGENT_STRIDE; part++)
				ps.setHidden(INTERFACE, slot + part, false);
			ps.sendItem(INTERFACE, slot + REAGENT_ITEM, item.getId(), item.getAmount());
			opBase(ps, slot + REAGENT_ITEM, ORANGE + item.getDef().name + END + " (x" + item.getAmount() + " required"
				+ (catalyst ? ", catalyst" : "") + ")");
			// owned/required under the slot, kept to one line in its 44px ("0/1,250", "12/25K")
			ps.sendString(INTERFACE, slot + REAGENT_COUNT, (ok ? GREEN : "<col=ff6b5a>") + compact(owned) + "/" + compact(item.getAmount()) + END);
		}

		// success gauge + breakdown
		double total = Math.min(100, recipe.getSuccessRate(player));
		int donator = recipe.donatorBonus(player);
		int pity = (int) Math.round(recipe.getSuccessRate(player)) - recipe.baseRate() - donator;
		ps.sendString(INTERFACE, RATE, "Success Rate: " + (total >= 75 ? GREEN : total >= 40 ? ORANGE : RED) + format(total) + "%" + END);
		int lit = (int) Math.round(total / (100D / SEGMENTS));
		for (int seg = 0; seg < SEGMENTS; seg++)
			ps.setHidden(INTERFACE, FIRST_SEGMENT + seg, seg >= lit);
		ps.sendString(INTERFACE, BREAKDOWN, "Base " + recipe.baseRate() + "%" + (donator > 0 ? " · Donator +" + donator + "%" : "")
			+ (pity > 0 ? "<br>Pity +" + pity + "% (from failed tries)" : ""));
		ps.sendString(INTERFACE, FAILURE, total >= 100 ? GREEN + "Guaranteed success." + END
			: "On failure: catalyst lost,<br>base and materials " + GREEN + "kept" + END + ".");
		// always raised stone (gold lift on hover); the label says whether it can run: bright green when ready, gold
		// "Missing reagents" otherwise (both high contrast on the stone)
		boolean ready = recipe.ready(player);
		ps.setHidden(INTERFACE, FORGE + PRESSED, true);
		ps.sendString(INTERFACE, FORGE + LABEL, ready ? "<col=7dff6a>Forge Upgrade" + END : GOLD + "Missing reagents" + END);
	}

	/** 950 / 1,250 / 12K / 1.5M: short enough for the reagent count line. */
	private static String compact(int n) {
		if (n < 10_000) return String.format("%,d", n);
		if (n < 1_000_000) return (n / 1000) + "K";
		return String.format("%.1fM", n / 1_000_000D).replace(".0M", "M");
	}

	private static void hideReagent(PacketSender ps, int k) {
		int slot = FIRST_REAGENT + k * REAGENT_STRIDE;
		for (int part = 0; part < REAGENT_STRIDE; part++)
			ps.setHidden(INTERFACE, slot + part, true);
	}

	/** Forge Upgrade: the same confirmation and UpgradeItems.createItem as the old screen, then back to the forge. */
	private static void forge(Player player) {
		UpgradeItems recipe = selected(player);
		if (recipe == null)
			return;
		if (!recipe.ready(player)) {
			// canUpgrade lists the missing ingredients; the catalyst is checked separately
			if (UpgradeItems.canUpgrade(player, recipe.ingredientList()))
				player.sendMessage("You need a " + recipe.catalyst().getDef().name + " as the catalyst for this upgrade.");
			return;
		}
		Runnable roll = () -> {
			// free spawned bases / ingredients / catalysts must never come out as a real upgrade (checked when it runs)
			java.util.List<Integer> ids = new java.util.ArrayList<>();
			for (UpgradeItems.Ingredient ingredient : recipe.ingredientList())
				for (Item variant : ingredient.variants())
					ids.add(variant.getId());
			if (recipe.catalyst() != null)
				ids.add(recipe.catalyst().getId());
			if (io.ruin.model.content.pvppreset.SpawnedItems.refuseIfSpawned(player, "forged",
					ids.stream().mapToInt(Integer::intValue).toArray()))
				return;
			int before = player.getInventory().count(recipe.outcome().getId());
			recipe.createItem(player);
			if (player.getInventory().count(recipe.outcome().getId()) > before) {
				player.graphics(SUCCESS_GFX);
				io.ruin.model.content.codex.ZelusTask.MASTER_OF_THE_FORGE.increment(player, 1);
			}
			open(player);
		};
		double rate = recipe.getSuccessRate(player);
		if (rate < 100) {
			player.dialogue(new YesNoDialogue("Forge " + recipe.outcome().getDef().name + "?",
				"You have a " + format(Math.min(100, rate)) + "% chance to succeed.<br>On failure your "
					+ recipe.catalyst().getDef().name + " is consumed.", recipe.outcome(), roll));
		} else {
			roll.run();
		}
	}

	/** Examine: the name (and examine text) in chat, then the item's stats viewer, which returns to the forge. */
	private static void examine(Player player, Item item) {
		if (item == null)
			return;
		ObjType def = item.getDef();
		player.sendMessage("<col=000080>" + def.name + "</col>: <col=000000>" + def.examineText() + "</col>");
		ItemStatsViewer.open(player, item.getId(), () -> open(player));
	}

	/** The item id in hover text is for staff testing only: admins see " (id N)", players just the name. */
	private static String adminId(Player player, int itemId) {
		return player.isAdmin() ? GREY + " (id " + itemId + ")" + END : "";
	}

	private static void opBase(PacketSender ps, int component, String text) {
		ps.sendClientScript(SET_OPBASE, "Is", INTERFACE << 16 | component, text);
	}

	/* ---------------- recipe views ---------------- */

	/** The cards shown: the search results (any category) while searching, else the category's recipes. */
	static List<UpgradeItems> list(Player player) {
		UpgradeSystemInterface s = player.getUpgradeSystemInterface();
		if (s.forgeSearch == null)
			return recipes(s.forgeCategory);
		List<UpgradeItems> list = new ArrayList<>();
		for (UpgradeItems recipe : UpgradeItems.VALUES) {
			boolean match = recipe.outcome().getDef().name.toLowerCase().contains(s.forgeSearch);
			for (UpgradeItems.Ingredient ingredient : recipe.ingredientList())
				for (Item variant : ingredient.variants())
					match |= variant.getDef() != null && variant.getDef().name.toLowerCase().contains(s.forgeSearch);
			if (match && list.size() < CARDS)
				list.add(recipe);
		}
		return list;
	}

	static List<UpgradeItems> recipes(int category) {
		List<UpgradeItems> list = new ArrayList<>();
		for (UpgradeItems recipe : UpgradeItems.VALUES)
			if (category(recipe) == category)
				list.add(recipe);
		return list;
	}

	/** 0 weapons (weapon slot), 1 armour (head/cape/body/shield/legs/hands/feet), 2 utility (jewellery, ammo, other). */
	static int category(UpgradeItems recipe) {
		ObjType def = recipe.outcome().getDef();
		int slot = def == null ? -1 : def.equipSlot;
		if (slot == Equipment.SLOT_WEAPON)
			return 0;
		if (slot == Equipment.SLOT_HAT || slot == Equipment.SLOT_CAPE || slot == Equipment.SLOT_CHEST
			|| slot == Equipment.SLOT_SHIELD || slot == Equipment.SLOT_LEGS || slot == Equipment.SLOT_HANDS
			|| slot == Equipment.SLOT_FEET)
			return 1;
		return 2;
	}

	/** The base item: the first ingredient (the variant the player holds, if any). */
	private static Item baseItem(Player player, UpgradeItems recipe) {
		if (recipe == null)
			return null;
		Item owned = UpgradeItems.ownedVariant(player, recipe.ingredientList()[0]);
		return owned != null ? owned : recipe.ingredientList()[0].variants()[0];
	}

	/** The other ingredients (held variant, else the first) followed by the catalyst. */
	private static List<Item> reagents(Player player, UpgradeItems recipe) {
		List<Item> list = new ArrayList<>();
		if (recipe == null)
			return list;
		UpgradeItems.Ingredient[] ingredients = recipe.ingredientList();
		for (int k = 1; k < ingredients.length && list.size() < REAGENTS - 1; k++) {
			Item owned = UpgradeItems.ownedVariant(player, ingredients[k]);
			list.add(owned != null ? owned : ingredients[k].variants()[0]);
		}
		list.add(recipe.catalyst());
		return list;
	}

	/** Harder recipes (lower base rate) read as higher tiers. */
	private static String tier(UpgradeItems recipe) {
		int base = recipe.baseRate();
		if (base >= 100)
			return "<col=c0c0c0>Tier I</col>";
		if (base >= 85)
			return "<col=3cb043>Tier II</col>";
		if (base >= 75)
			return "<col=00aaff>Tier III</col>";
		if (base >= 60)
			return "<col=ff981f>Tier IV</col>";
		return "<col=ff3333>Tier V</col>";
	}

	private static String rate(Player player, UpgradeItems recipe) {
		return format(Math.min(100, recipe.getSuccessRate(player)));
	}

	/** Outcome minus base equipment bonuses: the biggest changes first, e.g. "+12 Slash · +8 Str". */
	private static String statDiff(Item base, UpgradeItems recipe) {
		ObjType from = base.getDef(), to = recipe.outcome().getDef();
		int[] a = from == null ? null : from.equipBonuses, b = to == null ? null : to.equipBonuses;
		List<int[]> diffs = new ArrayList<>();
		if (b != null) {
			for (int i = 0; i < BONUS_NAMES.length && i < b.length; i++) {
				int d = b[i] - (a != null && i < a.length ? a[i] : 0);
				if (d != 0)
					diffs.add(new int[]{i, d});
			}
		}
		if (diffs.isEmpty())
			return GREY + sentences(recipe.description(), 90) + END;
		diffs.sort((x, y) -> Integer.compare(Math.abs(y[1]), Math.abs(x[1])));
		StringBuilder sb = new StringBuilder();
		// one stat per line, the 4 biggest (4 lines fit the stat panel with a bottom margin)
		for (int k = 0; k < Math.min(4, diffs.size()); k++) {
			int[] d = diffs.get(k);
			sb.append(k == 0 ? "" : "<br>")
				.append(d[1] > 0 ? GREEN + "+" : RED).append(d[1]).append(" ").append(BONUS_NAMES[d[0]]).append(END);
		}
		return sb.toString();
	}

	/** Whole sentences up to max characters (5 lines of the description box), never cut mid-word. */
	private static String sentences(String text, int max) {
		if (text == null || text.length() <= max)
			return text == null ? "" : text;
		int cut = text.lastIndexOf(". ", max);
		if (cut > 0)
			return text.substring(0, cut + 1);
		cut = text.lastIndexOf(' ', max - 2);
		return text.substring(0, cut > 0 ? cut : max - 2) + "..";
	}

	private static String format(double value) {
		return value == Math.floor(value) ? String.valueOf((int) value) : String.format("%.1f", value);
	}

	private static String shorten(String text, int max) {
		if (text == null)
			return "";
		return text.length() <= max ? text : text.substring(0, max - 2) + "..";
	}
}

package io.ruin.model.content.itembreaking;

import io.ruin.cache.ObjType;
import io.ruin.model.ScrollbarClientScript;
import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.InterfaceHandler;
import io.ruin.model.inter.ToplevelComponent;
import io.ruin.model.inter.actions.SimpleAction;
import io.ruin.model.item.Item;
import io.ruin.model.item.attributes.AttributeExtensions;
import io.ruin.model.item.containers.Equipment;
import io.ruin.network.PacketSender;

import java.util.List;

/**
 * The Zelus Forge item workbench (interface 1153, built by .dev/cache-restore-tool/src/BuildWorkbenchInterface.java),
 * replacing the old perk-attachment screen 1126. It only draws: all state and every rule (slot checks, attachment
 * limits, minimum materials, extra-material odds, donator material saves, the roll and the keep-your-level rule) stay in
 * {@link ItemUpgradeInterface} / {@link ItemBreakPerks}. Flow: pick a perk on the left, click the item in the side
 * inventory (1128), press Add minimum, click extra minerals / enhancers in the inventory to raise the odds, then
 * Upgrade Item (with the existing confirmation).
 */
public final class WorkbenchHub {

	public static final int INTERFACE = 1153;
	private static final int CLOSE = 2, TITLE = 3, SUB_HEADER = 4;
	private static final int SCROLL = 9, SCROLLBAR = 10;
	/** Cards (2026-10-20 redesign): layer, fill (click), selected recess + shadow, divider, icon well, icon, name, line. */
	private static final int FIRST_CARD = 11, CARD_STRIDE = 9, CARD_BOX = 1, CARD_SELECTED = 2, CARD_ICON = 6, CARD_NAME = 7,
		CARD_LINE = 8, CARD_PITCH = 44, SCROLL_HEIGHT = 233; // round 6 (RestyleWorkbench, 2026-11-15: 40px cards, p12)
	/** The description has its own scroll pane (100px tall, 12px lines, ~21 characters a line). */
	private static final int PERK_NAME = 186, PERK_INFO = 187, DESC_SCROLL = 189, DESC_SCROLLBAR = 190, PERK_DESC = 191,
		DESC_HEIGHT = 41, ODDS_HEADER = 194, FIRST_ODDS = 195;
	/** The target socket shows "Item" (TARGET_HINT) while empty. */
	private static final int TARGET_HINT = 219, TARGET = 220, TARGET_NAME = 221, TARGET_UPGRADES = 222, TARGET_PERKS = 223;
	/** Material slots: well, rim, item. */
	private static final int FIRST_COST = 225, ADDED_LABEL = 237, FIRST_ADDED = 238, SLOT_STRIDE = 3, SLOT_ITEM = 2, SLOTS = 4;
	/** Stone buttons: fill (click), +9 pressed layer, +19 label. */
	private static final int ADD_MINIMUM = 250, UPGRADE = 270, PRESSED = 9, LABEL = 19;

	/** The perk icons the old workbench (1126) used, in ItemBreakPerks order. */
	private static final int[] PERK_SPRITES = {5203, 4854, 4824, 5202, 5209, 5184, 5426, 5036, 4863, 5086, 4851, 5082, 4524,
		5207, 5434, 5092, 5438, 5201, 5423};

	/* round 5 (2026-11-14): the Karn Workbench is the user's dark reference art (gold / ivory text) */
	private static final String GOLD = "<col=f5c55a>", GREY = "<col=a89a88>", WHITE = "<col=e8dcc6>", GREEN = "<col=6fe07a>",
		ORANGE = "<col=f0a040>", RED = "<col=e04848>", END = "</col>";

	private WorkbenchHub() {
	}

	static void register() {
		InterfaceHandler.register(INTERFACE, h -> {
			h.actions[CLOSE] = (SimpleAction) p -> p.closeInterfaces();
			ItemBreakPerks[] perks = ItemBreakPerks.VALUES;
			for (int c = 0; c < perks.length; c++) {
				ItemBreakPerks perk = perks[c];
				h.actions[FIRST_CARD + c * CARD_STRIDE + CARD_BOX] = (SimpleAction) p -> p.getItemUpgradeInterface().selectPerk(p, perk);
			}
			h.actions[TARGET] = (SimpleAction) WorkbenchHub::clearItem;
			for (int k = 0; k < SLOTS; k++) {
				int slot = k;
				h.actions[FIRST_COST + k * SLOT_STRIDE + SLOT_ITEM] = (SimpleAction) p -> examine(p, costItem(p, slot));
				h.actions[FIRST_ADDED + k * SLOT_STRIDE + SLOT_ITEM] = (SimpleAction) p -> examine(p, addedItem(p, slot));
			}
			h.actions[ADD_MINIMUM] = (SimpleAction) p -> p.getItemUpgradeInterface().addMinimumIngredients(p);
			h.actions[UPGRADE] = (SimpleAction) p -> p.getItemUpgradeInterface().confirmUpgrade(p);
		});
	}

	/** Opens the frame (ItemUpgradeInterface.open opens the side inventory and calls refresh). */
	static void openFrame(Player player) {
		player.openInterface(ToplevelComponent.MAINMODAL, INTERFACE);
		PacketSender ps = player.getPacketSender();
		ps.sendString(INTERFACE, TITLE, ORANGE + "KARN FORGE · ITEM WORKBENCH" + END);
		ItemBreakPerks[] perks = ItemBreakPerks.VALUES;
		for (int c = 0; c < perks.length; c++) {
			int card = FIRST_CARD + c * CARD_STRIDE;
			if (c < PERK_SPRITES.length)
				ps.setGraphic(INTERFACE, card + CARD_ICON, PERK_SPRITES[c]);
			ps.sendString(INTERFACE, card + CARD_NAME, GOLD + perks[c].name + END);
		}
		int contentHeight = perks.length * CARD_PITCH - 2;
		int count = contentHeight <= SCROLL_HEIGHT ? 0 : (int) Math.ceil((contentHeight - SCROLL_HEIGHT / 3D) / 15D);
		ScrollbarClientScript.create().interfaceId(INTERFACE).containerId(SCROLL).scrollbarChildId(SCROLLBAR)
			.childrenCount(count).withOrnateGraphics().build().send(player);
	}

	static void refresh(Player player) {
		if (!player.isVisibleInterface(INTERFACE))
			return;
		ItemUpgradeInterface s = player.getItemUpgradeInterface();
		PacketSender ps = player.getPacketSender();
		ItemBreakPerks perk = s.perk;
		Item item = s.selectedItem;

		// round 6: the steps line is live p12 text in the top band (~300px): short steps
		ps.sendString(INTERFACE, SUB_HEADER, perk == null ? GOLD + "1." + END + " Pick a perk from the list"
			: item == null ? GOLD + "2." + END + " Click an item in your inventory"
			: !s.minimumIngredientsAdded ? GOLD + "3." + END + " Press " + GOLD + "Add min" + END + " to load materials"
			: GOLD + "4." + END + " Add extras for better odds, then " + GREEN + "Upgrade" + END);

		// catalogue: selected outline + tier / slot line (and the level the selected item already has)
		ItemBreakPerks[] perks = ItemBreakPerks.VALUES;
		for (int c = 0; c < perks.length; c++) {
			int card = FIRST_CARD + c * CARD_STRIDE;
			ps.setHidden(INTERFACE, card + CARD_SELECTED, perks[c] != perk);
			ps.setHidden(INTERFACE, card + CARD_SELECTED + 1, perks[c] != perk);
			String line = "Tier " + perks[c].tier + " · " + slots(perks[c]);
			if (item != null && AttributeExtensions.hasAttribute(item, perks[c].perk))
				line = GREEN + "Lv " + AttributeExtensions.getCharges(perks[c].perk, item) + END + " · " + line;
			ps.sendString(INTERFACE, card + CARD_LINE, line);
		}

		// centre: mechanics and odds
		if (perk != null) {
			ps.sendString(INTERFACE, PERK_NAME, perk.name);
			ps.sendString(INTERFACE, PERK_INFO, "Tier " + perk.tier + "  ·  " + slots(perk));
			String description = ItemBreakPerks.getDescription(perk).replaceAll("\\s+", " ").trim();
			// the whole description, in its scroll pane (the scrollbar is sized to the wrapped text)
			ps.sendString(INTERFACE, PERK_DESC, description + "<br>" + GREY + "Lower rolls keep your level." + END);
			int lines = description.length() / 19 + 3; // round 6: p12 in the 129 px pane
			int contentHeight = lines * 14;
			int count = contentHeight <= DESC_HEIGHT ? 0 : (int) Math.ceil((contentHeight - DESC_HEIGHT / 3D) / 15D);
			ScrollbarClientScript.create().interfaceId(INTERFACE).containerId(DESC_SCROLL).scrollbarChildId(DESC_SCROLLBAR)
				.childrenCount(count).withOrnateGraphics().build().send(player);
			boolean boosted = s.minimumIngredientsAdded;
			ps.sendString(INTERFACE, ODDS_HEADER, boosted ? "Roll odds  " + GREY + "(base > yours)" + END : "Roll odds (base)");
			int[] base = odds(perk.level2Chance, perk.level3Chance, perk.level4Chance, perk.level5Chance);
			int[] yours = boosted ? odds(
				ItemBreakPerks.getChance(player, perk.level2Chance, perk.cost, s.itemsInPerkAttachment),
				ItemBreakPerks.getChance(player, perk.level3Chance, perk.cost, s.itemsInPerkAttachment),
				ItemBreakPerks.getChance(player, perk.level4Chance, perk.cost, s.itemsInPerkAttachment),
				ItemBreakPerks.getChance(player, perk.level5Chance, perk.cost, s.itemsInPerkAttachment)) : null;
			for (int l = 0; l < 5; l++) {
				int level = 5 - l;
				// the tier pill beside it says "Lv n"
				String text = WHITE + base[level - 1] + "%" + END;
				if (yours != null)
					text += GREY + "  >  " + END + GREEN + yours[level - 1] + "%" + END;
				ps.sendString(INTERFACE, FIRST_ODDS + l, text);
			}
		}

		// right: target item
		ps.setHidden(INTERFACE, TARGET, item == null);
		ps.setHidden(INTERFACE, TARGET_HINT, true); // round 6: the old "Item" label sat beside the socket; the text below says it
		if (item != null) {
			ps.sendItem(INTERFACE, TARGET, item.getId(), item.getAmount());
			ps.sendString(INTERFACE, TARGET_NAME, shorten(item.getDef().name, 26));
			ps.sendString(INTERFACE, TARGET_UPGRADES, "Upgrades " + ItemUpgradeInterface.getTotalAttachments(item) + " / "
				+ ItemUpgradeInterface.getMaxAttachments(item));
			ps.sendString(INTERFACE, TARGET_PERKS, GREY + "Perks: " + END + shorten(String.valueOf(ItemUpgradeInterface.getItemAttachmentsComma(item)), 28));
		} else {
			ps.sendString(INTERFACE, TARGET_NAME, GREY + "No item" + END);
			ps.sendString(INTERFACE, TARGET_UPGRADES, "");
			ps.sendString(INTERFACE, TARGET_PERKS, GREY + "Click an item in your inventory" + END); // one p12 line
		}

		// materials: required (the perk's cost) and added (what will be consumed)
		List<Item> cost = perk == null ? List.of() : perk.cost;
		sendSlots(player, FIRST_COST, cost);
		ps.sendString(INTERFACE, ADDED_LABEL, !s.perkAttachmentActive ? GREY + "Added materials" + END
			: s.minimumIngredientsAdded ? GOLD + "Added" + END + GREY + " (click inventory for more)" + END : GOLD + "Added materials" + END);
		sendSlots(player, FIRST_ADDED, s.perkAttachmentActive ? s.itemsInPerkAttachment : List.of());
		ps.setHidden(INTERFACE, ADD_MINIMUM + PRESSED, s.perkAttachmentActive && !s.minimumIngredientsAdded);
		// round 5: the painted button is small (Karn Workbench art): short labels
		ps.sendString(INTERFACE, ADD_MINIMUM + LABEL, !s.perkAttachmentActive ? GREY + "Add min" + END
			: s.minimumIngredientsAdded ? GREEN + "Added" + END : ORANGE + "Add min" + END);
		// the primary action: raised with a bright label when it can run, pressed + grey otherwise
		boolean ready = s.perkAttachmentActive && s.minimumIngredientsAdded;
		ps.setHidden(INTERFACE, UPGRADE + PRESSED, ready);
		ps.sendString(INTERFACE, UPGRADE + LABEL, ready ? "<col=f5e6c8>Upgrade Item" + END : "<col=a89a88>Upgrade Item" + END);
	}

	private static void sendSlots(Player player, int first, List<Item> items) {
		PacketSender ps = player.getPacketSender();
		for (int k = 0; k < SLOTS; k++) {
			int slot = first + k * SLOT_STRIDE + SLOT_ITEM;
			boolean used = k < items.size() && items.get(k) != null;
			ps.setHidden(INTERFACE, slot, !used);
			if (used)
				ps.sendItem(INTERFACE, slot, items.get(k).getId(), items.get(k).getAmount());
		}
	}

	/** Chance of each final level (index 0 = Lv 1) from the cumulative thresholds the roll uses (roll 1-100 <= threshold). */
	private static int[] odds(int lv2, int lv3, int lv4, int lv5) {
		int p5 = clamp(lv5);
		int p4 = clamp(lv4 - lv5);
		int p3 = clamp(lv3 - lv4);
		int p2 = clamp(lv2 - lv3);
		int p1 = clamp(100 - lv2);
		return new int[]{p1, p2, p3, p4, p5};
	}

	private static int clamp(int value) {
		return Math.max(0, Math.min(100, value));
	}

	private static String levelColour(int level) {
		return switch (level) {
			case 5 -> "<col=f0a040>";
			case 4 -> "<col=c890ff>";
			case 3 -> "<col=7ab0ff>";
			case 2 -> GREEN;
			default -> WHITE;
		};
	}

	private static String slots(ItemBreakPerks perk) {
		if (!perk.specificSlot || perk.slots.length == 0)
			return "Any slot";
		StringBuilder sb = new StringBuilder();
		for (int slot : perk.slots)
			sb.append(sb.length() == 0 ? "" : "/").append(slotName(slot));
		return sb.toString();
	}

	private static String slotName(int slot) {
		return switch (slot) {
			case Equipment.SLOT_HAT -> "Head";
			case Equipment.SLOT_CAPE -> "Cape";
			case Equipment.SLOT_AMULET -> "Neck";
			case Equipment.SLOT_WEAPON -> "Weapon";
			case Equipment.SLOT_CHEST -> "Body";
			case Equipment.SLOT_SHIELD -> "Shield";
			case Equipment.SLOT_LEGS -> "Legs";
			case Equipment.SLOT_HANDS -> "Hands";
			case Equipment.SLOT_FEET -> "Feet";
			case Equipment.SLOT_RING -> "Ring";
			case Equipment.SLOT_AMMO -> "Ammo";
			default -> "Slot " + slot;
		};
	}

	private static String shorten(String text, int max) {
		return text.length() <= max ? text : text.substring(0, max - 3).trim() + "...";
	}

	/* ---------------- actions ---------------- */

	private static void clearItem(Player player) {
		ItemUpgradeInterface s = player.getItemUpgradeInterface();
		s.selectedItem = null;
		s.perkAttachmentActive = false;
		s.minimumIngredientsAdded = false;
		s.itemsInPerkAttachment.clear();
		refresh(player);
	}

	private static Item costItem(Player player, int slot) {
		ItemBreakPerks perk = player.getItemUpgradeInterface().perk;
		return perk == null || slot >= perk.cost.size() ? null : perk.cost.get(slot);
	}

	private static Item addedItem(Player player, int slot) {
		List<Item> added = player.getItemUpgradeInterface().itemsInPerkAttachment;
		return slot >= added.size() ? null : added.get(slot);
	}

	private static void examine(Player player, Item item) {
		if (item == null)
			return;
		ObjType def = item.getDef();
		player.sendMessage(GOLD + def.name + END + " x " + item.getAmount()
			+ (def.examine == null || def.examine.equals("null") ? "" : ": " + def.examine));
	}
}

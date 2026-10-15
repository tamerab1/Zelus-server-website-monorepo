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
	private static final int SCROLL = 10, SCROLLBAR = 11;
	/** Cards: layer, box, border, gold outline x2, icon, name, tier/slot line. */
	private static final int FIRST_CARD = 12, CARD_STRIDE = 8, CARD_BOX = 1, CARD_OUTLINE = 3, CARD_ICON = 5, CARD_NAME = 6,
		CARD_LINE = 7, CARD_PITCH = 36, SCROLL_HEIGHT = 235;
	private static final int PERK_NAME = 169, PERK_INFO = 170, PERK_DESC = 171, ODDS_HEADER = 172, FIRST_ODDS = 173;
	private static final int TARGET = 186, TARGET_NAME = 187, TARGET_UPGRADES = 188, TARGET_PERKS = 189;
	/** Material slots: fill, border, item. */
	private static final int FIRST_COST = 191, ADDED_LABEL = 203, FIRST_ADDED = 204, SLOT_STRIDE = 3, SLOT_ITEM = 2, SLOTS = 4;
	private static final int ADD_MINIMUM = 216, UPGRADE = 218;

	/** The perk icons the old workbench (1126) used, in ItemBreakPerks order. */
	private static final int[] PERK_SPRITES = {5203, 4854, 4824, 5202, 5209, 5184, 5426, 5036, 4863, 5086, 4851, 5082, 4524,
		5207, 5434, 5092, 5438, 5201, 5423};

	private static final String GOLD = "<col=ffc35a>", GREY = "<col=9a9a9a>", WHITE = "<col=ffffff>", GREEN = "<col=00ff00>",
		ORANGE = "<col=ff981f>", RED = "<col=ff5050>", END = "</col>";

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
		ps.sendString(INTERFACE, TITLE, ORANGE + "ZELUS FORGE · ITEM WORKBENCH" + END);
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
			.childrenCount(count).withDarkGraphics().build().send(player);
	}

	static void refresh(Player player) {
		if (!player.isVisibleInterface(INTERFACE))
			return;
		ItemUpgradeInterface s = player.getItemUpgradeInterface();
		PacketSender ps = player.getPacketSender();
		ItemBreakPerks perk = s.perk;
		Item item = s.selectedItem;

		ps.sendString(INTERFACE, SUB_HEADER, item == null
			? WHITE + "1." + END + " Pick a perk  " + GREY + "·" + END + "  " + WHITE + "2." + END + " Click an item in your inventory"
			: !s.minimumIngredientsAdded ? WHITE + "3." + END + " Press " + GOLD + "Add minimum" + END + " to load the required materials"
			: WHITE + "4." + END + " Click extra minerals / enhancers for better odds, then " + GREEN + "Upgrade" + END);

		// catalogue: selected outline + tier / slot line (and the level the selected item already has)
		ItemBreakPerks[] perks = ItemBreakPerks.VALUES;
		for (int c = 0; c < perks.length; c++) {
			int card = FIRST_CARD + c * CARD_STRIDE;
			ps.setHidden(INTERFACE, card + CARD_OUTLINE, perks[c] != perk);
			ps.setHidden(INTERFACE, card + CARD_OUTLINE + 1, perks[c] != perk);
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
			// 9 lines of 11px fit the 100px box, 13px above "Roll odds"
			ps.sendString(INTERFACE, PERK_DESC, shorten(description, 140) + "<br>" + GREY + "Lower rolls keep your level." + END);
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
				String text = levelColour(level) + "Lv " + level + END + ": " + WHITE + base[level - 1] + "%" + END;
				if (yours != null)
					text += GREY + "  >  " + END + GREEN + yours[level - 1] + "%" + END;
				ps.sendString(INTERFACE, FIRST_ODDS + l, text);
			}
		}

		// right: target item
		ps.setHidden(INTERFACE, TARGET, item == null);
		if (item != null) {
			ps.sendItem(INTERFACE, TARGET, item.getId(), item.getAmount());
			ps.sendString(INTERFACE, TARGET_NAME, shorten(item.getDef().name, 18));
			ps.sendString(INTERFACE, TARGET_UPGRADES, "Upgrades " + ItemUpgradeInterface.getTotalAttachments(item) + " / "
				+ ItemUpgradeInterface.getMaxAttachments(item));
			ps.sendString(INTERFACE, TARGET_PERKS, GREY + "Perks: " + END + ItemUpgradeInterface.getItemAttachmentsComma(item));
		} else {
			ps.sendString(INTERFACE, TARGET_NAME, GREY + "No item" + END);
			ps.sendString(INTERFACE, TARGET_UPGRADES, "");
			ps.sendString(INTERFACE, TARGET_PERKS, GREY + "Click an item in your inventory to choose it." + END); // full width, 2 lines
		}

		// materials: required (the perk's cost) and added (what will be consumed)
		List<Item> cost = perk == null ? List.of() : perk.cost;
		sendSlots(player, FIRST_COST, cost);
		ps.sendString(INTERFACE, ADDED_LABEL, !s.perkAttachmentActive ? GREY + "Added materials" + END
			: s.minimumIngredientsAdded ? GOLD + "Added" + END + GREY + " (click inventory for more)" + END : GOLD + "Added materials" + END);
		sendSlots(player, FIRST_ADDED, s.perkAttachmentActive ? s.itemsInPerkAttachment : List.of());
		ps.sendString(INTERFACE, ADD_MINIMUM + 1, !s.perkAttachmentActive ? GREY + "Add minimum" + END
			: s.minimumIngredientsAdded ? GREEN + "Minimum added" + END : WHITE + "Add minimum" + END);
		boolean ready = s.perkAttachmentActive && s.minimumIngredientsAdded;
		ps.sendString(INTERFACE, UPGRADE + 1, ready ? "Upgrade Item" : GREY + "Upgrade Item" + END);
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
			case 5 -> "<col=ff8c00>";
			case 4 -> "<col=c86fff>";
			case 3 -> "<col=4fa3ff>";
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

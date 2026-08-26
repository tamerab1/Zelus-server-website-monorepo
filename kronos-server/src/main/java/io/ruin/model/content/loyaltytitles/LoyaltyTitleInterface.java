package io.ruin.model.content.loyaltytitles;

import io.ruin.cache.Icon;
import io.ruin.cache.InterfaceDef;
import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.InterfaceHandler;
import io.ruin.model.inter.Subcomponent;
import io.ruin.model.inter.ToplevelComponent;
import io.ruin.model.inter.actions.SimpleAction;

import java.util.ArrayList;
import java.util.List;

public class LoyaltyTitleInterface {

	public static final int INTERFACE_ID = 5105;
	public static final int CHATBOX_ID = 5106;

	private static final int MIN_COMPONENT_COUNT = 118;
	private static final int MIN_CHATBOX_COMPONENT_COUNT = 12;

	private static final int CLOSE = 3;
	private static final int HEADING_TEXT = 4;
	private static final int SUMMARY_TEXT = 5;

	private static final int[] TAB_IDS = {9, 10, 11, 12, 13, 14, 15, 16, 115};
	private static final int[] TAB_ICON_IDS = {17, 18, 19, 20, 21, 22, 23, 24, 116};
	private static final int[] TAB_LABEL_IDS = {25, 26, 27, 28, 29, 30, 31, 32, 117};
	private static final String[] TAB_LABELS = {"All", "Owned", "Locked", "Gen", "Elite", "PvM", "PvP", "Skill", Icon.DONATOR_ICON.tag() + "Donor"};

	private static final int ROW_BG_START = 33;
	private static final int ROW_ICON_START = 45;
	private static final int ROW_TITLE_START = 57;
	private static final int ROW_META_START = 69;
	private static final int ROW_STATUS_START = 81;
	private static final int ROW_COIN_START = 93;
	private static final int ROWS_PER_PAGE = 12;

	private static final int PREVIEW_LABEL = 105;
	private static final int PREVIEW = 106;
	private static final int CLEAR = 107;
	private static final int PREV_PAGE = 108;
	private static final int PREV_PAGE_LABEL = 109;
	private static final int PAGE_TEXT = 110;
	private static final int NEXT_PAGE = 111;
	private static final int NEXT_PAGE_LABEL = 112;
	private static final int APPLY = 113;
	private static final int APPLY_LABEL = 114;

	private static final int DETAILS_TITLE = 3;
	private static final int DETAILS_PREVIEW = 4;
	private static final int DETAILS_CATEGORY = 5;
	private static final int DETAILS_STATUS = 6;
	private static final int DETAILS_REQUIREMENT_LABEL = 7;
	private static final int DETAILS_REQUIREMENT = 8;
	private static final int DETAILS_COST = 9;
	private static final int DETAILS_CURRENT = 10;
	private static final int DETAILS_HINT = 11;

	private static boolean registered;

	public static synchronized void register() {
		if (registered || !areInterfacesPacked())
			return;
		InterfaceHandler.register(INTERFACE_ID, h -> {
			h.simpleAction(CLOSE, LoyaltyTitleInterface::close);
			for (int i = 0; i < TAB_IDS.length; i++) {
				int filter = i;
				SimpleAction action = p -> {
					p.loyaltyTitleFilter = filter;
					p.loyaltyTitlePage = 0;
					p.loyaltyTitleSelectedId = null;
					openDetails(p);
					render(p);
				};
				h.simpleAction(TAB_IDS[i], action);
				h.simpleAction(TAB_ICON_IDS[i], action);
				h.simpleAction(TAB_LABEL_IDS[i], action);
			}
			for (int i = 0; i < ROWS_PER_PAGE; i++) {
				int rowIndex = i;
				SimpleAction action = p -> onRowSelect(p, rowIndex);
				h.simpleAction(ROW_BG_START + i, action);
				h.simpleAction(ROW_ICON_START + i, action);
				h.simpleAction(ROW_TITLE_START + i, action);
				h.simpleAction(ROW_META_START + i, action);
				h.simpleAction(ROW_STATUS_START + i, action);
				h.simpleAction(ROW_COIN_START + i, action);
			}
			h.simpleAction(CLEAR, LoyaltyTitleInterface::onClear);
			h.simpleAction(PREV_PAGE, p -> {
				if (p.loyaltyTitlePage > 0) {
					p.loyaltyTitlePage--;
					openDetails(p);
					render(p);
				}
			});
			h.simpleAction(PREV_PAGE_LABEL, p -> {
				if (p.loyaltyTitlePage > 0) {
					p.loyaltyTitlePage--;
					openDetails(p);
					render(p);
				}
			});
			h.simpleAction(NEXT_PAGE, p -> {
				if ((p.loyaltyTitlePage + 1) * ROWS_PER_PAGE < filteredTitles(p).size()) {
					p.loyaltyTitlePage++;
					openDetails(p);
					render(p);
				}
			});
			h.simpleAction(NEXT_PAGE_LABEL, p -> {
				if ((p.loyaltyTitlePage + 1) * ROWS_PER_PAGE < filteredTitles(p).size()) {
					p.loyaltyTitlePage++;
					openDetails(p);
					render(p);
				}
			});
			h.simpleAction(APPLY, LoyaltyTitleInterface::onApply);
			h.simpleAction(APPLY_LABEL, LoyaltyTitleInterface::onApply);
			h.closedAction = (p, i) -> {
				p.loyaltyTitleSelectedId = null;
				closeChatbox(p);
			};
		});
		InterfaceHandler.register(CHATBOX_ID, h -> {
			h.closedAction = (p, i) -> {
			};
		});
		registered = true;
	}

	// index into TAB_IDS/TAB_LABELS for the "Donor" tab (see filteredTitles()'s case 8 -> DONATOR)
	private static final int DONATOR_TAB = 8;

	public static void open(Player player) {
		open(player, 0);
	}

	public static void openDonatorTab(Player player) {
		open(player, DONATOR_TAB);
	}

	private static void open(Player player, int initialFilter) {
		if (!areInterfacesPacked()) {
			player.sendMessage("The titles interfaces are missing from the cache. Rebuild the cache after adding the 5105/5106 TOML patches.");
			return;
		}
		register();
		player.loyaltyTitleFilter = initialFilter;
		player.loyaltyTitlePage = 0;
		player.loyaltyTitleSelectedId = null;
		player.openInterface(ToplevelComponent.MAINMODAL, INTERFACE_ID);
		openDetails(player);
		render(player);
	}

	private static void close(Player player) {
		player.closeInterface(ToplevelComponent.MAINMODAL);
		closeChatbox(player);
	}

	private static void closeChatbox(Player player) {
		player.closeDialogue();
		player.getPacketSender().removeSubtoplevelInterface(ToplevelComponent.CHATBOX, Subcomponent.DIALOGUE);
		player.getPacketSender().removeSubtoplevelInterface(ToplevelComponent.CHATBOX, Subcomponent.YES_NO_DIALOGUE);
	}

	private static void openDetails(Player player) {
		player.closeDialogue();
		player.getPacketSender().sendToplevelSubInterface(CHATBOX_ID, Subcomponent.DIALOGUE, ToplevelComponent.CHATBOX);
	}

	private static boolean areInterfacesPacked() {
		return InterfaceDef.COUNTS != null
			&& INTERFACE_ID < InterfaceDef.COUNTS.length
			&& CHATBOX_ID < InterfaceDef.COUNTS.length
			&& INTERFACE_ID < InterfaceHandler.HANDLERS.length
			&& CHATBOX_ID < InterfaceHandler.HANDLERS.length
			&& InterfaceDef.COUNTS[INTERFACE_ID] >= MIN_COMPONENT_COUNT
			&& InterfaceDef.COUNTS[CHATBOX_ID] >= MIN_CHATBOX_COMPONENT_COUNT;
	}

	private static List<LoyaltyTitle> filteredTitles(Player player) {
		List<LoyaltyTitle> result = new ArrayList<>();
		for (LoyaltyTitle title : LoyaltyTitle.all()) {
			boolean unlocked = LoyaltyTitleManager.isUnlocked(player, title);
			switch (player.loyaltyTitleFilter) {
				case 1 -> { if (!unlocked) continue; }
				case 2 -> { if (unlocked) continue; }
				case 3 -> { if (title.category != LoyaltyTitle.Category.GENERAL) continue; }
				case 4 -> { if (title.category != LoyaltyTitle.Category.MASTERY) continue; }
				case 5 -> { if (title.category != LoyaltyTitle.Category.PVM) continue; }
				case 6 -> { if (title.category != LoyaltyTitle.Category.PVP) continue; }
				case 7 -> { if (title.category != LoyaltyTitle.Category.SKILLING) continue; }
				case 8 -> { if (title.category != LoyaltyTitle.Category.DONATOR) continue; }
				default -> { /* All */ }
			}
			result.add(title);
		}
		return result;
	}

	private static void render(Player player) {
		List<LoyaltyTitle> titles = filteredTitles(player);
		int pageCount = Math.max(1, (titles.size() + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
		if (player.loyaltyTitlePage >= pageCount)
			player.loyaltyTitlePage = pageCount - 1;

		renderChrome(player, titles.size(), pageCount);

		int start = player.loyaltyTitlePage * ROWS_PER_PAGE;
		for (int i = 0; i < ROWS_PER_PAGE; i++) {
			int index = start + i;
			if (index >= titles.size()) {
				setRowHidden(player, i, true);
				continue;
			}
			LoyaltyTitle title = titles.get(index);
			boolean unlocked = LoyaltyTitleManager.isUnlocked(player, title);
			boolean selected = player.loyaltyTitleSelectedId != null && player.loyaltyTitleSelectedId == title.id;

			setRowHidden(player, i, false);
			player.getPacketSender().sendString(INTERFACE_ID, ROW_STATUS_START + i, statusMarker(title, unlocked));
			player.getPacketSender().sendString(INTERFACE_ID, ROW_TITLE_START + i, shortPreview(title, player));
			player.getPacketSender().setHidden(INTERFACE_ID, ROW_COIN_START + i, true);

			if (unlocked) {
				player.getPacketSender().sendString(INTERFACE_ID, ROW_META_START + i, selected ? "<col=f2ce45>Selected</col>" : categoryName(title.category));
			} else if (title.isPurchasable()) {
				player.getPacketSender().sendString(INTERFACE_ID, ROW_META_START + i, selected ? "<col=f2ce45>Selected</col>" : formatAmount(title.cost) + " " + title.currency.label);
			} else {
				player.getPacketSender().sendString(INTERFACE_ID, ROW_META_START + i, selected ? "<col=f2ce45>Selected</col>" : "<col=9f9f9f>" + truncate(title.requirement, 27) + "</col>");
			}
		}
		renderPreview(player);
	}

	private static void renderChrome(Player player, int filteredCount, int pageCount) {
		player.getPacketSender().sendString(INTERFACE_ID, HEADING_TEXT, "Zelus Titles");
		player.getPacketSender().sendString(INTERFACE_ID, SUMMARY_TEXT, "<col=f2ce45>" + unlockedCount(player) + "</col><col=b9aa83>/" + LoyaltyTitle.all().size() + "</col>");
		for (int i = 0; i < TAB_IDS.length; i++) {
			String color = player.loyaltyTitleFilter == i ? "f2ce45" : "b9aa83";
			player.getPacketSender().sendString(INTERFACE_ID, TAB_LABEL_IDS[i], "<col=" + color + ">" + TAB_LABELS[i] + "</col>");
		}
		player.getPacketSender().setHidden(INTERFACE_ID, PREVIEW_LABEL, true);
		player.getPacketSender().setHidden(INTERFACE_ID, PREVIEW, true);
		player.getPacketSender().sendString(INTERFACE_ID, PREVIEW_LABEL, "");
		player.getPacketSender().sendString(INTERFACE_ID, PREVIEW, "");
		player.getPacketSender().sendString(INTERFACE_ID, CLEAR, "Clear");
		player.getPacketSender().sendString(INTERFACE_ID, PREV_PAGE_LABEL, "Prev");
		player.getPacketSender().sendString(INTERFACE_ID, NEXT_PAGE_LABEL, "Next");
		player.getPacketSender().sendString(INTERFACE_ID, APPLY_LABEL, "Apply");
		player.getPacketSender().sendString(INTERFACE_ID, PAGE_TEXT, filteredCount == 0 ? "0 / 0" : (player.loyaltyTitlePage + 1) + " / " + pageCount);
	}

	private static void setRowHidden(Player player, int rowIndex, boolean hidden) {
		player.getPacketSender().setHidden(INTERFACE_ID, ROW_BG_START + rowIndex, hidden);
		player.getPacketSender().setHidden(INTERFACE_ID, ROW_ICON_START + rowIndex, hidden);
		player.getPacketSender().setHidden(INTERFACE_ID, ROW_TITLE_START + rowIndex, hidden);
		player.getPacketSender().setHidden(INTERFACE_ID, ROW_META_START + rowIndex, hidden);
		player.getPacketSender().setHidden(INTERFACE_ID, ROW_STATUS_START + rowIndex, hidden);
		player.getPacketSender().setHidden(INTERFACE_ID, ROW_COIN_START + rowIndex, true);
	}

	private static void onRowSelect(Player player, int rowIndex) {
		List<LoyaltyTitle> titles = filteredTitles(player);
		int index = player.loyaltyTitlePage * ROWS_PER_PAGE + rowIndex;
		if (index >= titles.size())
			return;
		player.loyaltyTitleSelectedId = titles.get(index).id;
		openDetails(player);
		render(player);
	}

	private static void renderPreview(Player player) {
		LoyaltyTitle title = selectedOrEquipped(player);
		renderDetails(player, title);
	}

	private static void renderDetails(Player player, LoyaltyTitle title) {
		if (player.hasDialogue())
			return;
		player.getPacketSender().sendString(CHATBOX_ID, DETAILS_TITLE, "Title Details");
		player.getPacketSender().sendString(CHATBOX_ID, DETAILS_CURRENT, currentTitleText(player));
		player.getPacketSender().sendString(CHATBOX_ID, DETAILS_HINT, "");
		if (title == null) {
			player.getPacketSender().sendString(CHATBOX_ID, DETAILS_PREVIEW, "Preview: " + player.getName());
			player.getPacketSender().sendString(CHATBOX_ID, DETAILS_CATEGORY, "Category: None");
			player.getPacketSender().sendString(CHATBOX_ID, DETAILS_STATUS, "Status: Select a title");
			player.getPacketSender().sendString(CHATBOX_ID, DETAILS_REQUIREMENT_LABEL, "Requirement");
			player.getPacketSender().sendString(CHATBOX_ID, DETAILS_REQUIREMENT, "Choose a title from the list above.");
			player.getPacketSender().sendString(CHATBOX_ID, DETAILS_COST, "");
			return;
		}
		boolean unlocked = LoyaltyTitleManager.isUnlocked(player, title);
		boolean selected = player.loyaltyTitleSelectedId != null && player.loyaltyTitleSelectedId == title.id;
		player.getPacketSender().sendString(CHATBOX_ID, DETAILS_PREVIEW, "Preview: " + truncateTagged(title.preview(player.getName()), 56));
		player.getPacketSender().sendString(CHATBOX_ID, DETAILS_CATEGORY, "Category: " + categoryName(title.category));
		player.getPacketSender().sendString(CHATBOX_ID, DETAILS_STATUS, "Status: " + statusText(title, unlocked, selected));
		player.getPacketSender().sendString(CHATBOX_ID, DETAILS_REQUIREMENT_LABEL, "Requirement");
		player.getPacketSender().sendString(CHATBOX_ID, DETAILS_REQUIREMENT, wrapText(requirementText(title, unlocked), 66, 2));
		player.getPacketSender().sendString(CHATBOX_ID, DETAILS_COST, title.isPurchasable() ? "Cost: <col=f2ce45>" + formatAmount(title.cost) + " " + title.currency.label + "</col>" : "");
	}

	private static LoyaltyTitle selectedOrEquipped(Player player) {
		if (player.loyaltyTitleSelectedId != null) {
			LoyaltyTitle selected = LoyaltyTitle.get(player.loyaltyTitleSelectedId);
			if (selected != null)
				return selected;
			player.loyaltyTitleSelectedId = null;
		}
		return LoyaltyTitleManager.getEquipped(player);
	}

	private static String currentTitleText(Player player) {
		LoyaltyTitle equipped = LoyaltyTitleManager.getEquipped(player);
		if (equipped == null)
			return "Equipped: None";
		return "Equipped: " + truncateTagged(equipped.preview(player.getName()), 48);
	}

	private static void onApply(Player player) {
		if (player.loyaltyTitleSelectedId == null) {
			player.sendMessage("Select a title first.");
			return;
		}
		LoyaltyTitle selected = LoyaltyTitle.get(player.loyaltyTitleSelectedId);
		if (selected == null)
			return;
		LoyaltyTitleManager.select(player, selected);
		if (player.hasInterfaceOpen(INTERFACE_ID, ToplevelComponent.MAINMODAL))
			render(player);
	}

	private static void onClear(Player player) {
		if (LoyaltyTitleManager.getEquipped(player) == null) {
			player.sendMessage("You do not have a title equipped.");
			return;
		}
		LoyaltyTitleManager.clearEquipped(player);
		player.loyaltyTitleSelectedId = null;
		player.sendMessage("Your title has been cleared.");
		render(player);
	}

	private static int unlockedCount(Player player) {
		int count = 0;
		for (LoyaltyTitle title : LoyaltyTitle.all()) {
			if (LoyaltyTitleManager.isUnlocked(player, title))
				count++;
		}
		return count;
	}

	private static String categoryName(LoyaltyTitle.Category category) {
		return switch (category) {
			case GENERAL -> "General title";
			case PVM -> "PvM title";
			case PVP -> "PvP title";
			case MASTERY -> "Elite title";
			case SKILLING -> "Skilling title";
			case DONATOR -> "Donator title";
		};
	}

	private static String statusMarker(LoyaltyTitle title, boolean unlocked) {
		if (unlocked)
			return "<col=44d43a>X</col>";
		if (title.isPurchasable())
			return "<col=f2ce45>$</col>";
		return "<col=777777>X</col>";
	}

	private static String statusText(LoyaltyTitle title, boolean unlocked, boolean selected) {
		if (selected)
			return "<col=f2ce45>Selected</col>";
		if (unlocked)
			return "<col=44d43a>Owned</col>";
		if (title.isPurchasable())
			return "<col=f2ce45>Purchasable</col>";
		return "<col=e15c48>Locked</col>";
	}

	private static String requirementText(LoyaltyTitle title, boolean unlocked) {
		if (title.isPurchasable())
			return "Purchase this title with " + title.currency.label + ".";
		if (unlocked)
			return "Unlocked: " + title.requirement;
		return title.requirement;
	}

	private static String shortPreview(LoyaltyTitle title, Player player) {
		return truncateTagged(title.preview(player.getName()), 24);
	}

	private static String formatAmount(int amount) {
		if (amount >= 1_000_000_000) return (amount / 1_000_000_000) + "B";
		if (amount >= 1_000_000) return (amount / 1_000_000) + "M";
		if (amount >= 1_000) return (amount / 1_000) + "K";
		return String.valueOf(amount);
	}

	private static String truncate(String text, int maxLength) {
		if (text == null || text.length() <= maxLength)
			return text == null ? "" : text;
		return text.substring(0, maxLength - 3) + "...";
	}

	private static String truncateTagged(String text, int maxVisibleLength) {
		if (text == null)
			return "";
		if (visibleLength(text) <= maxVisibleLength)
			return text;
		int visible = 0;
		int limit = Math.max(0, maxVisibleLength - 3);
		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < text.length();) {
			char c = text.charAt(i);
			if (c == '<') {
				int end = text.indexOf('>', i);
				if (end != -1) {
					builder.append(text, i, end + 1);
					i = end + 1;
					continue;
				}
			}
			if (visible >= limit) {
				builder.append("...");
				return builder.toString();
			}
			builder.append(c);
			visible++;
			i++;
		}
		return builder.toString();
	}

	private static int visibleLength(String text) {
		int visible = 0;
		for (int i = 0; i < text.length();) {
			char c = text.charAt(i);
			if (c == '<') {
				int end = text.indexOf('>', i);
				if (end != -1) {
					i = end + 1;
					continue;
				}
			}
			visible++;
			i++;
		}
		return visible;
	}

	private static String wrapText(String text, int maxLineLength, int maxLines) {
		if (text == null || text.isEmpty())
			return "";
		StringBuilder builder = new StringBuilder();
		StringBuilder line = new StringBuilder();
		int lines = 1;
		for (String word : text.split(" ")) {
			if (line.length() > 0 && line.length() + word.length() + 1 > maxLineLength) {
				if (builder.length() > 0)
					builder.append("<br>");
				builder.append(line);
				lines++;
				if (lines > maxLines)
					return builder.append("...").toString();
				line.setLength(0);
			}
			if (line.length() > 0)
				line.append(' ');
			line.append(word);
		}
		if (line.length() > 0) {
			if (builder.length() > 0)
				builder.append("<br>");
			builder.append(line);
		}
		return builder.toString();
	}
}

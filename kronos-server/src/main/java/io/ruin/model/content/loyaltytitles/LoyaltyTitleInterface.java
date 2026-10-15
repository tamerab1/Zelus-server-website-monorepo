package io.ruin.model.content.loyaltytitles;

import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.InterfaceHandler;
import io.ruin.model.inter.ToplevelComponent;
import io.ruin.model.inter.actions.SimpleAction;
import io.ruin.network.PacketSender;

import java.util.ArrayList;
import java.util.List;

/**
 * Zelus Titles (interface 1160, built by .dev/cache-restore-tool/src/BuildTitlesInterface.java): the title list and the
 * selected title's details in one window. It replaced the old pair (5105 list in the main window + 5106 details in the
 * chatbox). Unlocking, buying and equipping are unchanged and stay in {@link LoyaltyTitleManager}.
 * <ul>
 *     <li>title row: owned count, "ZELUS TITLES", close; 9 category tabs (gold frame on the active one);</li>
 *     <li>12 cards (2 x 6): a status square (owned / donor / locked), the title in its colour, its requirement;</li>
 *     <li>Prev / page / Next, Clear, Apply (or Buy & Apply for a purchasable title);</li>
 *     <li>details: header + equipped, a live preview with the player's name, the full requirement; category, status, cost.</li>
 * </ul>
 */
public class LoyaltyTitleInterface {

	public static final int INTERFACE_ID = 1160;

	private static final int CLOSE = 2, TITLE = 3, OWNED = 4;
	/** Tabs: box (hover), border, gold frame x2, icon, label. */
	private static final int FIRST_TAB = 5, TAB_STRIDE = 6, TAB_FRAME = 2, TAB_LABEL = 5;
	/** The title list scrolls (12 cards of 48px in 2 columns). */
	private static final int SCROLL = 59, SCROLLBAR = 60, SCROLL_HEIGHT = 146, CARD_PITCH = 52, LIST_TOP = 8;
	/** Cards: layer, box (hover), border, selected frame x3, price icon (item), "$" glyph, price text, title, requirement. */
	private static final int FIRST_CARD = 61, CARD_STRIDE = 11, CARD_BOX = 1, CARD_FRAME = 3, CARD_FRAMES = 3,
		CARD_PRICE_ICON = 6, CARD_PRICE_GLYPH = 7, CARD_PRICE_TEXT = 8, CARD_TITLE = 9, CARD_REQUIREMENT = 10, ROWS_PER_PAGE = 12;
	private static final int PREV = 193, PAGE = 195, NEXT = 196, CLEAR = 198, APPLY = 200;
	private static final int DETAILS_HEADER = 209, DETAILS_PREVIEW = 210, DETAILS_REQUIREMENT = 211, DETAILS_CATEGORY = 212,
		DETAILS_STATUS = 213, DETAILS_COST = 214;
	/** Clientscript 10753 set_object_nonum(component, item, amount) (AddOpBaseScript): the item icon without a stack number. */
	private static final int SET_OBJECT_NONUM = 10753;
	private static final int COINS = 995;

	private static final String[] TAB_LABELS = {"All", "Owned", "Locked", "Gen", "Elite", "PvM", "PvP", "Skill", "Donor"};
	// index into TAB_LABELS for the "Donor" tab (see filteredTitles()'s case 8 -> DONATOR)
	private static final int DONATOR_TAB = 8;

	/** Status square fills: owned (green), donor / purchasable (gold), locked (dark red). */
	private static final int OWNED_FILL = 0x1f4a1c, DONOR_FILL = 0x5a4510, LOCKED_FILL = 0x4a1c1c;

	private static final String GOLD = "<col=ffc35a>", ORANGE = "<col=ff981f>", GREY = "<col=a0a0a0>", WHITE = "<col=ffffff>",
		GREEN = "<col=3ca433>", RED = "<col=c42b00>", END = "</col>";

	private static boolean registered;

	public static synchronized void register() {
		if (registered)
			return;
		InterfaceHandler.register(INTERFACE_ID, h -> {
			h.simpleAction(CLOSE, p -> p.closeInterface(ToplevelComponent.MAINMODAL));
			for (int i = 0; i < TAB_LABELS.length; i++) {
				int filter = i;
				h.simpleAction(FIRST_TAB + i * TAB_STRIDE, p -> {
					p.loyaltyTitleFilter = filter;
					p.loyaltyTitlePage = 0;
					p.loyaltyTitleSelectedId = null;
					render(p, true);
				});
			}
			for (int i = 0; i < ROWS_PER_PAGE; i++) {
				int rowIndex = i;
				h.simpleAction(FIRST_CARD + i * CARD_STRIDE + CARD_BOX, p -> onRowSelect(p, rowIndex));
			}
			h.simpleAction(PREV, p -> {
				if (p.loyaltyTitlePage > 0) {
					p.loyaltyTitlePage--;
					render(p, true);
				}
			});
			h.simpleAction(NEXT, p -> {
				if ((p.loyaltyTitlePage + 1) * ROWS_PER_PAGE < filteredTitles(p).size()) {
					p.loyaltyTitlePage++;
					render(p, true);
				}
			});
			h.simpleAction(CLEAR, LoyaltyTitleInterface::onClear);
			h.simpleAction(APPLY, LoyaltyTitleInterface::onApply);
			h.closedAction = (p, i) -> p.loyaltyTitleSelectedId = null;
		});
		registered = true;
	}

	public static void open(Player player) {
		open(player, 0);
	}

	public static void openDonatorTab(Player player) {
		open(player, DONATOR_TAB);
	}

	private static void open(Player player, int initialFilter) {
		register();
		player.loyaltyTitleFilter = initialFilter;
		player.loyaltyTitlePage = 0;
		player.loyaltyTitleSelectedId = null;
		player.openInterface(ToplevelComponent.MAINMODAL, INTERFACE_ID);
		player.getPacketSender().sendString(INTERFACE_ID, TITLE, ORANGE + "ZELUS TITLES" + END);
		render(player, true);
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
		render(player, false);
	}

	/** listChanged: open / tab / page -- only then is the scrollbar script sent (it resets the list's scroll to the top). */
	private static void render(Player player, boolean listChanged) {
		if (!player.isVisibleInterface(INTERFACE_ID))
			return;
		PacketSender ps = player.getPacketSender();
		List<LoyaltyTitle> titles = filteredTitles(player);
		int pageCount = Math.max(1, (titles.size() + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
		if (player.loyaltyTitlePage >= pageCount)
			player.loyaltyTitlePage = pageCount - 1;

		ps.sendString(INTERFACE_ID, OWNED, GOLD + unlockedCount(player) + END + GREY + " / " + LoyaltyTitle.all().size() + " owned" + END);
		for (int i = 0; i < TAB_LABELS.length; i++) {
			int tab = FIRST_TAB + i * TAB_STRIDE;
			boolean active = player.loyaltyTitleFilter == i;
			ps.setHidden(INTERFACE_ID, tab + TAB_FRAME, !active);
			ps.setHidden(INTERFACE_ID, tab + TAB_FRAME + 1, !active);
			ps.sendString(INTERFACE_ID, tab + TAB_LABEL, (active ? GOLD : WHITE) + TAB_LABELS[i] + END);
		}

		int start = player.loyaltyTitlePage * ROWS_PER_PAGE;
		for (int i = 0; i < ROWS_PER_PAGE; i++) {
			int card = FIRST_CARD + i * CARD_STRIDE;
			int index = start + i;
			LoyaltyTitle title = index < titles.size() ? titles.get(index) : null;
			ps.setHidden(INTERFACE_ID, card, title == null);
			if (title == null)
				continue;
			boolean unlocked = LoyaltyTitleManager.isUnlocked(player, title);
			boolean selected = player.loyaltyTitleSelectedId != null && player.loyaltyTitleSelectedId == title.id;
			for (int f = 0; f < CARD_FRAMES; f++)
				ps.setHidden(INTERFACE_ID, card + CARD_FRAME + f, !selected);
			boolean forSale = !unlocked && title.isPurchasable();
			// price icon: a red "$" only for donator points, a coins stack only for gold; nothing for other titles
			boolean dollar = forSale && title.currency == LoyaltyTitle.Currency.DONATOR_POINTS;
			boolean coins = forSale && title.currency == LoyaltyTitle.Currency.COINS;
			ps.sendString(INTERFACE_ID, card + CARD_PRICE_GLYPH, dollar ? "$" : "");
			ps.setHidden(INTERFACE_ID, card + CARD_PRICE_ICON, !coins);
			// coins: the real stack (its pile art follows the amount) drawn WITHOUT the stack number, and the price in
			// its own yellow text under it -- the number never sits on the sprite
			if (coins)
				ps.sendClientScript(SET_OBJECT_NONUM, "Iii", INTERFACE_ID << 16 | (card + CARD_PRICE_ICON), COINS, Math.max(1, title.cost));
			ps.sendString(INTERFACE_ID, card + CARD_PRICE_TEXT, coins ? "<shad=000000>" + formatAmount(title.cost) + "</shad>" : "");
			ps.sendString(INTERFACE_ID, card + CARD_TITLE, truncateTagged(displayText(title), 26));
			ps.sendString(INTERFACE_ID, card + CARD_REQUIREMENT, unlocked ? GREEN + "Owned" + END
				: forSale ? GOLD + formatAmount(title.cost) + " " + title.currency.label + END
				: RED + "Locked" + END + GREY + " - " + END + wrapText(title.requirement, 30, 2));
		}

		int shown = Math.min(ROWS_PER_PAGE, Math.max(0, titles.size() - start));
		int contentHeight = LIST_TOP + ((shown + 1) / 2) * CARD_PITCH - 4;
		int count = contentHeight <= SCROLL_HEIGHT ? 0 : (int) Math.ceil((contentHeight - SCROLL_HEIGHT / 3D) / 15D);
		if (listChanged)
			io.ruin.model.ScrollbarClientScript.create().interfaceId(INTERFACE_ID).containerId(SCROLL).scrollbarChildId(SCROLLBAR)
				.childrenCount(count).withDarkGraphics().build().send(player);
		ps.sendString(INTERFACE_ID, PREV + 1, player.loyaltyTitlePage > 0 ? "Prev" : GREY + "Prev" + END);
		ps.sendString(INTERFACE_ID, NEXT + 1, player.loyaltyTitlePage + 1 < pageCount ? "Next" : GREY + "Next" + END);
		ps.sendString(INTERFACE_ID, PAGE, titles.isEmpty() ? "0 / 0" : (player.loyaltyTitlePage + 1) + " / " + pageCount);
		ps.sendString(INTERFACE_ID, CLEAR + 1, LoyaltyTitleManager.getEquipped(player) != null ? "Clear" : GREY + "Clear" + END);
		renderDetails(player);
	}

	/** The details panel: the selected title (else the equipped one). */
	private static void renderDetails(Player player) {
		PacketSender ps = player.getPacketSender();
		LoyaltyTitle equipped = LoyaltyTitleManager.getEquipped(player);
		LoyaltyTitle title = selected(player);
		ps.sendString(INTERFACE_ID, DETAILS_HEADER, ORANGE + "Title Details" + END + "   " + GREY + "Equipped: "
			+ (equipped == null ? "None" : truncateTagged(displayText(equipped), 26)) + END);
		if (title == null) {
			ps.sendString(INTERFACE_ID, DETAILS_PREVIEW, WHITE + "Preview: " + END + GREY + "None" + END);
			ps.sendString(INTERFACE_ID, DETAILS_REQUIREMENT, ORANGE + "Requirement: " + END + GREY + "Select a title above." + END);
			ps.sendString(INTERFACE_ID, DETAILS_CATEGORY, GREY + "Category: -" + END);
			ps.sendString(INTERFACE_ID, DETAILS_STATUS, "");
			ps.sendString(INTERFACE_ID, DETAILS_COST, "");
			ps.sendString(INTERFACE_ID, APPLY + 1, GREY + "Apply" + END);
			return;
		}
		boolean unlocked = LoyaltyTitleManager.isUnlocked(player, title);
		boolean isEquipped = equipped != null && equipped.id == title.id;
		ps.sendString(INTERFACE_ID, DETAILS_PREVIEW, WHITE + "Preview: " + END + truncateTagged(title.preview(player.getName()), 40));
		ps.sendString(INTERFACE_ID, DETAILS_REQUIREMENT, ORANGE + "Requirement: " + END + wrapText(requirementText(title, unlocked), 52, 3));
		ps.sendString(INTERFACE_ID, DETAILS_CATEGORY, GREY + "Category: " + END + categoryName(title.category));
		ps.sendString(INTERFACE_ID, DETAILS_STATUS, GREY + "Status: " + END + (isEquipped ? GREEN + "Owned / Active" + END
			: unlocked ? GREEN + "Owned" + END : title.isPurchasable() ? GOLD + "Purchasable" + END : RED + "Locked" + END));
		ps.sendString(INTERFACE_ID, DETAILS_COST, title.isPurchasable() && !unlocked
			? GREY + "Cost: " + END + GOLD + formatAmount(title.cost) + " " + title.currency.label + END : "");
		String apply = isEquipped ? GREY + "Applied" + END : unlocked ? GREEN + "Apply" + END
			: title.isPurchasable() ? GOLD + "Buy & Apply" + END : GREY + "Apply" + END;
		ps.sendString(INTERFACE_ID, APPLY + 1, apply);
	}

	/**
	 * The title's own text for the list / header: suffix titles are authored with their joining ", " (", the Regal
	 * Ravager") so the in-game name reads "Name, the Regal Ravager" -- shown on its own, the leading comma is dropped.
	 */
	private static String displayText(LoyaltyTitle title) {
		return title.text.trim().replaceFirst("^((?:<[^>]+>)*)\\s*,\\s*", "$1");
	}

	private static void setColour(Player player, int component, int colour) {
		player.getPacketSender().sendClientScript(45, "", INTERFACE_ID << 16 | component, colour);
	}

	private static void onRowSelect(Player player, int rowIndex) {
		List<LoyaltyTitle> titles = filteredTitles(player);
		int index = player.loyaltyTitlePage * ROWS_PER_PAGE + rowIndex;
		if (index >= titles.size())
			return;
		player.loyaltyTitleSelectedId = titles.get(index).id;
		render(player);
	}

	private static LoyaltyTitle selected(Player player) {
		if (player.loyaltyTitleSelectedId != null) {
			LoyaltyTitle selected = LoyaltyTitle.get(player.loyaltyTitleSelectedId);
			if (selected != null)
				return selected;
			player.loyaltyTitleSelectedId = null;
		}
		return null;
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
			case GENERAL -> "General";
			case PVM -> "PvM";
			case PVP -> "PvP";
			case MASTERY -> "Elite";
			case SKILLING -> "Skilling";
			case DONATOR -> "Donator";
		};
	}

	private static String requirementText(LoyaltyTitle title, boolean unlocked) {
		if (title.isPurchasable())
			return "Purchase this title with " + title.currency.label + ".";
		if (unlocked)
			return "Unlocked: " + title.requirement;
		return title.requirement;
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

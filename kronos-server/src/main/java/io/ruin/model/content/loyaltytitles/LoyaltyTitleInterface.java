package io.ruin.model.content.loyaltytitles;

import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.InterfaceHandler;
import io.ruin.model.inter.ToplevelComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * Hand-authored interface (id 5105) inspired by nr-wicked's "Loyalty Titles" widget (their
 * cache id 2002). Not a binary clone -- their title list is bound to a client-side CS2
 * clientscript that couldn't be decoded/ported, so this renders rows server-side using the
 * same pattern as DropViewer.java (fixed row-component range, setHidden + sendString, no
 * clientscript dependency). See data zelus/data/cache/toml/1_patches/interface/5105.toml.
 */
public class LoyaltyTitleInterface {

	public static final int INTERFACE_ID = 5105;

	private static final int CLOSE = 3;

	private static final int[] TAB_IDS = {9, 10, 11, 12, 13, 14, 15, 16};
	// index into TAB_IDS -> filter: 0=All 1=Unlocked 2=Locked 3=General 4=Mastery 5=PvM 6=PvP 7=Skilling

	// 2-column x 6-row grid (12 slots/page), 34px stride so each row has real breathing room.
	// Ids 30-34 in the toml are the divider lines drawn between these rows -- not clickable,
	// not referenced here.
	private static final int[] ROW_IDS = {18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29};
	private static final int ROWS_PER_PAGE = ROW_IDS.length;

	private static final int PREVIEW = 37;
	// These target the clickable Sprite child of each button's Layer wrapper (the wrapper and
	// its Text label aren't referenced here -- see the "Prev / Next / Apply" block in 5105.toml).
	private static final int PREV_PAGE = 39;
	private static final int NEXT_PAGE = 42;
	private static final int APPLY = 45;

	public static void register() {
		InterfaceHandler.register(INTERFACE_ID, h -> {
			h.simpleAction(CLOSE, p -> p.closeInterface(ToplevelComponent.MAINMODAL));
			for (int i = 0; i < TAB_IDS.length; i++) {
				int filter = i;
				h.simpleAction(TAB_IDS[i], p -> {
					p.loyaltyTitleFilter = filter;
					p.loyaltyTitlePage = 0;
					render(p);
				});
			}
			for (int i = 0; i < ROW_IDS.length; i++) {
				int rowIndex = i;
				h.simpleAction(ROW_IDS[i], p -> onRowSelect(p, rowIndex));
			}
			h.simpleAction(PREV_PAGE, p -> {
				if (p.loyaltyTitlePage > 0) {
					p.loyaltyTitlePage--;
					render(p);
				}
			});
			h.simpleAction(NEXT_PAGE, p -> {
				if ((p.loyaltyTitlePage + 1) * ROWS_PER_PAGE < filteredTitles(p).size()) {
					p.loyaltyTitlePage++;
					render(p);
				}
			});
			h.simpleAction(APPLY, LoyaltyTitleInterface::onApply);
			h.closedAction = (p, i) -> p.loyaltyTitleSelectedId = null;
		});
	}

	public static void open(Player player) {
		player.loyaltyTitleFilter = 0;
		player.loyaltyTitlePage = 0;
		player.loyaltyTitleSelectedId = null;
		player.openInterface(ToplevelComponent.MAINMODAL, INTERFACE_ID);
		render(player);
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
				default -> { /* All */ }
			}
			result.add(title);
		}
		return result;
	}

	private static void render(Player player) {
		List<LoyaltyTitle> titles = filteredTitles(player);
		int start = player.loyaltyTitlePage * ROWS_PER_PAGE;
		for (int i = 0; i < ROW_IDS.length; i++) {
			int index = start + i;
			if (index >= titles.size()) {
				player.getPacketSender().setHidden(INTERFACE_ID, ROW_IDS[i], true);
				continue;
			}
			LoyaltyTitle title = titles.get(index);
			boolean unlocked = LoyaltyTitleManager.isUnlocked(player, title);
			// Always show the title's own real colour, even while locked, so players can see
			// what they're working toward -- lock state is already conveyed clearly by line2.
			String line1 = title.preview(player.getName());
			String line2;
			if (unlocked) {
				line2 = "<col=00b000>Unlocked</col>";
			} else if (title.isPurchasable()) {
				line2 = "<col=ffb000>" + formatAmount(title.cost) + " coins</col>";
			} else {
				line2 = "<col=808080>" + truncate(title.requirement, 34) + "</col>";
			}
			player.getPacketSender().setHidden(INTERFACE_ID, ROW_IDS[i], false);
			player.getPacketSender().sendString(INTERFACE_ID, ROW_IDS[i], line1 + "<br>" + line2);
		}
		renderPreview(player);
	}

	private static void onRowSelect(Player player, int rowIndex) {
		List<LoyaltyTitle> titles = filteredTitles(player);
		int index = player.loyaltyTitlePage * ROWS_PER_PAGE + rowIndex;
		if (index >= titles.size())
			return;
		player.loyaltyTitleSelectedId = titles.get(index).id;
		renderPreview(player);
	}

	private static void renderPreview(Player player) {
		if (player.loyaltyTitleSelectedId == null) {
			LoyaltyTitle equipped = LoyaltyTitleManager.getEquipped(player);
			String text = equipped == null ? player.getName() : equipped.preview(player.getName());
			player.getPacketSender().sendString(INTERFACE_ID, PREVIEW, text);
			return;
		}
		LoyaltyTitle selected = LoyaltyTitle.get(player.loyaltyTitleSelectedId);
		if (selected == null) {
			player.loyaltyTitleSelectedId = null;
			renderPreview(player);
			return;
		}
		player.getPacketSender().sendString(INTERFACE_ID, PREVIEW, selected.preview(player.getName()));
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

	private static String formatAmount(int amount) {
		if (amount >= 1_000_000_000) return (amount / 1_000_000_000) + "B";
		if (amount >= 1_000_000) return (amount / 1_000_000) + "M";
		if (amount >= 1_000) return (amount / 1_000) + "K";
		return String.valueOf(amount);
	}

	private static String truncate(String text, int maxLength) {
		if (text.length() <= maxLength)
			return text;
		return text.substring(0, maxLength - 3) + "...";
	}
}

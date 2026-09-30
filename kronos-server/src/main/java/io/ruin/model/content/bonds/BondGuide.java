package io.ruin.model.content.bonds;

import io.ruin.model.content.bonds.BondType.Variant;
import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.handlers.OptionScroll;
import io.ruin.model.inter.utils.Option;
import io.ruin.model.item.Item;

import java.util.ArrayList;
import java.util.List;

/**
 * The Bonds & Auras guide (::bonds / ::auras, or "Overview" on any bond): a clickable list of the
 * six bonds with the player's status, and a per-bond page with the full T1-MAX perk path and costs.
 */
public final class BondGuide {

	private BondGuide() {
	}

	// Palette: everything sits on a parchment background, so only dark, high-contrast colours.
	private static final String NAME = "<col=800000>", CATEGORY = "<col=4a2600>", HEADER = "<col=800000>",
			TIER = "<col=000080>", TEXT = "<col=1a1a1a>";
	private static final String ATTUNED = "<col=006600>", OWNED = "<col=333333>", NOT_OWNED = "<col=800000>";

	public static void open(Player player) {
		List<Option> options = new ArrayList<>();
		for (BondType type : BondType.VALUES) {
			if (!options.isEmpty())
				options.add(new Option(" ", p -> { })); // spacer row between bonds
			// "Bond of the " dropped so the row stays short, e.g. "Blood Titan (Melee) - Attuned (T3)".
			String shortName = type.bondName.replace("Bond of the ", "");
			options.add(new Option(NAME + shortName + "</col> " + CATEGORY + "(" + type.category + ")</col> - "
					+ status(player, type), p -> openDetail(p, type)));
		}
		// keepOpen, so clicking a spacer row does nothing instead of closing the list.
		OptionScroll.openKeepOpen(player, "Bonds & Auras", options);
	}

	// Every line here must stay short (~42 chars): the scroll interface doesn't wrap text.
	public static void openDetail(Player player, BondType type) {
		List<String> lines = new ArrayList<>();
		lines.add(CATEGORY + type.category + "</col> | " + status(player, type));
		lines.add("");
		lines.add(HEADER + "How bonds work</col>");
		lines.add(TEXT + "- Attune a bond to turn its perks on");
		lines.add(TEXT + "- Perks only work while the attuned");
		lines.add(TEXT + "  bond is in your inventory");
		lines.add(TEXT + "- One attuned bond of each type");
		lines.add(TEXT + "- Sever it to turn its perks off");
		lines.add(TEXT + "- Only unattuned T1 bonds can be traded");
		for (int tier = 1; tier <= BondType.MAX_TIER; tier++) {
			lines.add("");
			String cost = tier == 1 ? "The base bond"
					: "+" + BondType.upgradeCost(tier - 1) + " T1 bond" + (tier == 2 ? "" : "s") + " (Upgrade)";
			lines.add(TIER + BondType.tierLabel(tier) + " - " + cost + ":</col>");
			for (String perk : type.perkLines(tier))
				lines.add(TEXT + "- " + perk);
		}
		lines.add("");
		lines.add(HEADER + "Total to MAX:</col> " + TEXT + "11 T1 bonds");
		player.sendScroll(NAME + type.bondName, lines.toArray(new String[0]));
	}

	/** "Attuned (T3)", "Owned T2 (not attuned)", or "Not owned" -- looks through inventory and bank. */
	private static String status(Player player, BondType type) {
		int attuned = type.tier(player);
		if (attuned > 0)
			return ATTUNED + "Attuned (" + BondType.tierLabel(attuned) + ")</col>";
		int best = Math.max(highestOwned(player.getInventory().getItems(), type), highestOwned(player.getBank().getItems(), type));
		return best > 0 ? OWNED + "Owned " + BondType.tierLabel(best) + " (not attuned)</col>" : NOT_OWNED + "Not owned</col>";
	}

	private static int highestOwned(Item[] items, BondType type) {
		int best = 0;
		for (Item item : items) {
			if (item == null)
				continue;
			Variant v = BondType.variant(item.getId());
			if (v != null && v.type() == type && v.tier() > best)
				best = v.tier();
		}
		return best;
	}
}

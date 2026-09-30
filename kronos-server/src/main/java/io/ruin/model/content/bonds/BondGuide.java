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
	/** "<< " -- a literal '<' starts a text tag in OSRS, so it's escaped as <lt>. */
	public static final String BACK_PREFIX = "<lt><lt> ";
	/** ">> " (char 187) -- the in-game font (495) has no glyph for the Unicode bullet (char 149 is 0x0 px); 187 is a clear 5x5 glyph. */
	public static final String BULLET = "» ";

	public static void open(Player player) {
		List<Option> options = new ArrayList<>();
		for (BondType type : BondType.VALUES) {
			if (!options.isEmpty())
				options.add(Option.info(" ")); // spacer row between bonds
			// "Bond of the " dropped so the row stays short, e.g. "Blood Titan (Melee) - Attuned (T3)".
			String shortName = type.bondName.replace("Bond of the ", "");
			// Name/category deliberately have NO <col> tag: they use the row's own colour, which
			// clientscript 218 switches to blue on mouse-over (inline <col> tags would hide that).
			options.add(new Option(shortName + " (" + type.category + "): " + status(player, type),
					p -> openDetail(p, type)));
		}
		// keepOpen, so clicking a spacer row does nothing instead of closing the list.
		OptionScroll.openKeepOpen(player, "Bonds & Auras", options);
	}

	/**
	 * A detail page in the same clickable list interface as the main page (187), so it can have
	 * "Back" rows. Info rows are fully colour-tagged (no hover highlight, clicking does nothing);
	 * the Back rows are untagged so they light up on mouse-over. OptionScroll joins rows with '|',
	 * so that character must never appear inside a row.
	 */
	public static void openRows(Player player, String title, List<String> lines, String backLabel, java.util.function.Consumer<Player> back) {
		List<Option> options = new ArrayList<>();
		options.add(new Option(backLabel, back::accept));
		for (String line : lines)
			options.add(Option.info(line.isEmpty() ? " " : line.replace("|", "/")));
		options.add(Option.info(" "));
		options.add(new Option(backLabel, back::accept));
		OptionScroll.openKeepOpen(player, title, options);
	}

	// Every line here must stay short (~42 chars): the list interface doesn't wrap text.
	public static void openDetail(Player player, BondType type) {
		List<String> lines = new ArrayList<>();
		lines.add("");
		lines.add(CATEGORY + type.category + ":</col> " + status(player, type));
		lines.add("");
		lines.add(HEADER + "How bonds work</col>");
		lines.add(TEXT + BULLET + "Attune a bond to turn its perks on");
		lines.add(TEXT + BULLET + "Perks only work while the attuned");
		lines.add(TEXT + "   bond is in your inventory");
		lines.add(TEXT + BULLET + "One attuned bond of each type");
		lines.add(TEXT + BULLET + "Sever it to turn its perks off");
		lines.add(TEXT + BULLET + "Only unattuned T1 bonds can be traded");
		for (int tier = 1; tier <= BondType.MAX_TIER; tier++) {
			lines.add("");
			String cost = tier == 1 ? "The base bond"
					: "+" + BondType.upgradeCost(tier - 1) + " T1 bond" + (tier == 2 ? "" : "s") + " (Upgrade)";
			lines.add(TIER + BondType.tierLabel(tier) + ": " + cost + "</col>");
			for (String perk : type.perkLines(tier))
				lines.add(TEXT + BULLET + perk);
		}
		lines.add("");
		lines.add(HEADER + "Total to MAX:</col> " + TEXT + "11 T1 bonds</col>");
		openRows(player, type.bondName, lines, BACK_PREFIX + "Back to all bonds", BondGuide::open);
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

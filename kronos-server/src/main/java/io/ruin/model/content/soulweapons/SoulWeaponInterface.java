package io.ruin.model.content.soulweapons;

import io.ruin.api.utils.NumberUtils;
import io.ruin.cache.ObjType;
import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.InterfaceHandler;
import io.ruin.model.inter.ToplevelComponent;
import io.ruin.model.inter.actions.SimpleAction;
import io.ruin.network.PacketSender;

import java.util.HashMap;
import java.util.Map;

/**
 * The Soulbound weapon window, interface 1174 (.dev/cache-restore-tool BuildSoulWeaponInterface), opened by Check
 * (2026-10-18). Drawn from the player's {@link SoulWeaponData}; {@link SoulWeapons#addXp} redraws it on every Soul XP
 * gain while it's open, so the bar, tier track and last-kill line move live as the player fights.
 */
public class SoulWeaponInterface {
	public static final int INTERFACE = 1174;
	private static final int CLOSE = 2, TITLE = 3, HEADER_TIER = 4, HEADER_SOULS = 5, NAME = 11, MODEL = 20, LOOK = 21,
			FIRST_LOOK = 24, LOOK_STRIDE = 8, POINTS = 49, FIRST_PIP = 56, PIP_STRIDE = 4, PROGRESS_LEFT = 96,
			PROGRESS_RIGHT = 97, BAR_COVER = 103, BAR_LABEL = 105, TOTAL = 106, LAST_KILL = 107, FIRST_ROW = 109,
			ROW_STRIDE = 3, FOOTER = 125, TREE_BUTTON = 126;
	/** Look slot parts: soul glow layer, item, tier-range label. Pip parts: fill, number. Row parts: label, value. */
	private static final int LOOK_HIGHLIGHT = 0, LOOK_ITEM = 6, LOOK_LABEL = 7, PIP_FILL = 1, PIP_NUMBER = 3,
			ROW_LABEL = 1, ROW_VALUE = 2;
	private static final int BAR_WIDTH = 272, SET_COLOUR = 45;
	private static final int PIP_DONE = 0x1fa7b5, PIP_CURRENT = 0xffc35a;
	private static final String GOLD = "<col=ffc35a>", TEAL = "<col=2fd2d8>", GREEN = "<col=3ddc5a>",
			MUTED = "<col=8f8573>", WHITE = "<col=ffffff>", END = "</col>";
	private static final String[] LOOKS = {"Base", "Empowered", "Ascended"}, RANGES = {"T1-3", "T4-7", "T8-10"};

	/** The look being previewed in the open window (Preview on an evolution slot), per player; none = the active look. */
	private static final Map<Integer, Integer> preview = new HashMap<>();

	/** The last kill's gain per player, for the "Last kill" line: {xp, npc name}. */
	private static final Map<Integer, Object[]> lastKill = new HashMap<>();

	static void register() {
		InterfaceHandler.register(INTERFACE, h -> {
			h.actions[CLOSE] = (SimpleAction) Player::closeInterfaces;
			h.actions[TREE_BUTTON] = (SimpleAction) SoulTreeInterface::open;
			for (int l = 0; l < 3; l++) {
				int look = l;
				h.actions[FIRST_LOOK + l * LOOK_STRIDE + LOOK_ITEM] = (SimpleAction) p -> {
					SoulWeaponData d = p.soulWeapon;
					if (d == null)
						return;
					// clicking the active look (or the one already previewed) goes back to the equipped look
					if (look == SoulWeaponType.band(d.tier) || Integer.valueOf(look).equals(preview.get(p.getUserId())))
						preview.remove(p.getUserId());
					else
						preview.put(p.getUserId(), look);
					draw(p);
				};
			}
			h.closedAction = (p, i) -> preview.remove(p.getUserId()); // closing reverts to the equipped look
		});
	}

	static void recordKill(Player player, long xp, String npcName) {
		lastKill.put(player.getUserId(), new Object[]{xp, npcName});
	}

	public static void open(Player player) {
		preview.remove(player.getUserId());
		player.openInterface(ToplevelComponent.MAINMODAL, INTERFACE);
		draw(player);
	}

	static boolean isOpen(Player player) {
		return player.isVisibleInterface(INTERFACE);
	}

	static void draw(Player player) {
		SoulWeaponData d = player.soulWeapon;
		if (d == null)
			return;
		PacketSender ps = player.getPacketSender();
		int band = SoulWeaponType.band(d.tier);
		boolean max = d.tier >= SoulWeapons.MAX_TIER;

		ps.sendString(INTERFACE, TITLE, "Soulbound Weapon");
		ps.sendString(INTERFACE, HEADER_TIER, "Tier " + d.tier + " / " + SoulWeapons.MAX_TIER + "  " + MUTED + "-" + END + "  "
				+ (band == 2 ? GOLD : TEAL) + LOOKS[band] + " look" + END);
		ps.sendString(INTERFACE, HEADER_SOULS, "Souls harvested: " + GOLD + NumberUtils.formatNumber(d.soulsHarvested) + END);

		// left: the weapon (or the look being previewed) and its evolution path
		int shown = preview.getOrDefault(player.getUserId(), band);
		int shownId = d.weaponType.looks()[shown];
		ObjType def = ObjType.get(shownId);
		ps.sendString(INTERFACE, NAME, twoLines(def.name));
		SoulWeaponCamera.show(ps, INTERFACE, MODEL, SoulWeaponCamera.Box.CHECK, shownId);
		ps.sendString(INTERFACE, LOOK, shown == band ? GREEN + "Currently active" + END
				: (shown == 2 ? GOLD : TEAL) + "Previewing: " + LOOKS[shown] + END + MUTED + " (T" + (shown == 1 ? 4 : 8) + ")" + END);
		int[] looks = d.weaponType.looks();
		for (int l = 0; l < looks.length; l++) {
			int c = FIRST_LOOK + l * LOOK_STRIDE;
			ps.setHidden(INTERFACE, c + LOOK_HIGHLIGHT, l != shown);
			ps.sendItem(INTERFACE, c + LOOK_ITEM, looks[l], 1);
			ps.sendString(INTERFACE, c + LOOK_LABEL, (l == band ? GOLD : l < band ? GREEN : MUTED) + RANGES[l] + END);
		}
		ps.sendString(INTERFACE, POINTS, "Points: " + GOLD + d.availableSoulPoints + END);
		ps.sendString(INTERFACE, TREE_BUTTON + 1, d.availableSoulPoints > 0 ? GREEN + "Soul Tree" + END : "Soul Tree");

		// right: tier track
		for (int t = 1; t <= SoulWeapons.MAX_TIER; t++) {
			int c = FIRST_PIP + (t - 1) * PIP_STRIDE;
			ps.setHidden(INTERFACE, c + PIP_FILL, t > d.tier);
			if (t <= d.tier)
				ps.sendClientScript(SET_COLOUR, "Ii", INTERFACE << 16 | (c + PIP_FILL), t == d.tier ? PIP_CURRENT : PIP_DONE);
			String colour = t == d.tier ? GOLD : t < d.tier ? WHITE : t == 4 || t == 8 ? TEAL : MUTED;
			ps.sendString(INTERFACE, c + PIP_NUMBER, colour + t + END);
		}

		// right: Soul XP bar
		double fill;
		if (max) {
			fill = 1;
			ps.sendString(INTERFACE, PROGRESS_LEFT, GOLD + "Fully empowered" + END);
			ps.sendString(INTERFACE, PROGRESS_RIGHT, GOLD + "MAX" + END);
			ps.sendString(INTERFACE, BAR_LABEL, "Tier " + SoulWeapons.MAX_TIER + " reached");
		} else {
			long from = SoulWeapons.TIER_XP[d.tier], to = SoulWeapons.TIER_XP[d.tier + 1];
			fill = Math.min(1, (d.soulXp - from) / (double) Math.max(1, to - from));
			int next = d.tier + 1;
			ps.sendString(INTERFACE, PROGRESS_LEFT, "Progress to tier " + next
					+ (next == 4 || next == 8 ? "  " + TEAL + "(evolves)" + END : ""));
			ps.sendString(INTERFACE, PROGRESS_RIGHT, (int) Math.floor(fill * 100) + "%");
			ps.sendString(INTERFACE, BAR_LABEL, NumberUtils.formatNumber(d.soulXp - from) + " / "
					+ NumberUtils.formatNumber(to - from) + " Soul XP");
		}
		ps.setAlignment(INTERFACE, BAR_COVER, (int) Math.round(fill * BAR_WIDTH), 0);
		ps.sendString(INTERFACE, TOTAL, "Total Soul XP: " + WHITE + NumberUtils.formatNumber(d.soulXp) + END);
		Object[] last = lastKill.get(player.getUserId());
		ps.sendString(INTERFACE, LAST_KILL, last == null ? MUTED + "Slay a monster to gain souls" + END
				: "Last kill: +" + last[0] + MUTED + " (" + last[1] + ")" + END);

		// right: stat rows
		String[][] rows = {
				{"Attack style", attackStyle(d.weaponType, band)},
				{"Tier bonus", tierBonus(player, d)},
				{"Special attack", band == 2 ? GOLD + SoulWeapons.specialName(d.weaponType) + END + GREEN + "  unlocked" + END
						: SoulWeapons.specialName(d.weaponType) + MUTED + "  - unlocks at tier 8" + END},
				{"Next evolution", d.tier >= 8 ? GOLD + "Fully evolved" + END
						: LOOKS[band + 1] + " look" + MUTED + " at tier " + (band == 0 ? 4 : 8) + END},
				{"Soul Tree", talentSummary(d)},
		};
		for (int r = 0; r < rows.length; r++) {
			int c = FIRST_ROW + r * ROW_STRIDE;
			ps.sendString(INTERFACE, c + ROW_LABEL, rows[r][0]);
			ps.sendString(INTERFACE, c + ROW_VALUE, rows[r][1]);
		}
		ps.sendString(INTERFACE, FOOTER, "<col=2fe8f0>Soul XP per kill:</col> level / 4 | <col=ff981f>Bosses +50 to +250</col><br>"
				+ "<col=ff6666>Soul powers work against monsters only (PvM)</col>");
	}

	/**
	 * The talents learned ("Devastation 3, Lifesteal 2"), or "Ranks 3 / 2 / 1" when the names won't fit the row, plus
	 * the points still to spend.
	 */
	private static String talentSummary(SoulWeaponData d) {
		StringBuilder names = new StringBuilder(), ranks = new StringBuilder("Ranks ");
		SoulTalent[] talents = SoulTalent.of(d.weaponType);
		for (int i = 0; i < talents.length; i++) {
			int rank = d.allocatedTalents.getOrDefault(talents[i].name(), 0);
			ranks.append(i == 0 ? "" : " / ").append(rank);
			if (rank > 0)
				names.append(names.length() == 0 ? "" : ", ").append(talents[i].name).append(" ").append(rank);
		}
		String spare = d.availableSoulPoints > 0 ? GREEN + " +" + d.availableSoulPoints + " to spend" + END : "";
		if (names.length() == 0)
			return d.availableSoulPoints > 0 ? GREEN + d.availableSoulPoints + " point" + (d.availableSoulPoints == 1 ? "" : "s")
					+ " to spend" + END : MUTED + "earn points by tiering up" + END;
		return (names.length() <= 24 ? names : ranks) + spare;
	}

	/** Names longer than one line of the name box break at the space nearest the middle: two balanced lines. */
	private static String twoLines(String name) {
		if (name.length() <= 20)
			return name;
		int mid = name.length() / 2, best = -1;
		for (int i = 0; i < name.length(); i++)
			if (name.charAt(i) == ' ' && (best == -1 || Math.abs(i - mid) < Math.abs(best - mid)))
				best = i;
		return best == -1 ? name : name.substring(0, best) + "<br>" + name.substring(best + 1);
	}

	private static String attackStyle(SoulWeaponType type, int band) {
		return switch (type) {
			case MELEE -> new String[]{"Sword stab", "Scimitar slash", "Azure cleave"}[band];
			case RANGED -> new String[]{"Quick draw", "Scorching draw", "Heavy draw"}[band];
			case MAGIC -> new String[]{"Soul bolt", "Blood-bound bolt", "Shadow orb"}[band];
		};
	}

	private static String tierBonus(Player player, SoulWeaponData d) {
		if (d.weaponType == SoulWeaponType.MAGIC)
			return "Spell max hit " + GOLD + SoulWeapons.spireMaxHit(player) + END
					+ (d.tier > 1 ? MUTED + ", +" + SoulWeapons.tierTenBonus(d.weaponType)[3] * (d.tier - 1) / 9 + " magic" + END : "");
		int[] ten = SoulWeapons.tierTenBonus(d.weaponType);
		String[] labels = {"stab", "slash", "crush", "magic", "ranged", "", "", "", "", "", "str", "ranged str"};
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < labels.length; i++)
			if (ten[i] != 0)
				sb.append(sb.length() == 0 ? "" : ", ").append("+").append(ten[i] * (d.tier - 1) / 9).append(" ").append(labels[i]);
		return d.tier == 1 ? MUTED + "none yet (tier 1)" + END : sb.toString();
	}
}

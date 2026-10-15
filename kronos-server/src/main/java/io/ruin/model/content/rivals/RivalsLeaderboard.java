package io.ruin.model.content.rivals;

import io.ruin.model.activities.tournament.ChampionLadder;
import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.ToplevelComponent;
import io.ruin.network.PacketSender;

import java.util.List;
import java.util.Locale;

/**
 * The Division Rivals leaderboard: the top 20 by Skill Rating, shown in the ladder window 1169 (the Pit Championship
 * ladder's layout, .dev/cache-restore-tool BuildChampionLadderInterface) with its columns relabelled # / Player / SR /
 * Wins / Losses and the Tournament Hub button hidden. Data: RivalsProfile.RivalsLadder (runtime/saves/rivals_ladder.json).
 * Opened from the Rivals Leaderboard object (BOARD_OBJECT, lobby 3358,3237) and the PK tab.
 */
public final class RivalsLeaderboard {

	private static final int INTERFACE = ChampionLadder.INTERFACE;
	private static final int TITLE = 3, SUBTITLE = 4;
	private static final int[] FIRST_ROW = {17, 99};
	private static final int[][] HEADERS = {{14, 15, 16}, {96, 97, 98}};
	private static final int ROW_STRIDE = 7, RANK = 1, NAME = 2, COL1 = 3, COL2 = 4, COL3 = 5, ROWS_PER_PANEL = 10;
	private static final int PERSONAL = 172, COUNTDOWN = 173, HUB_BUTTON = 174, FOOTER = 176;
	/** The Rivals Leaderboard object (.dev/cache-restore-tool BuildChampionshipBoard "rivals"). */
	public static final int BOARD_OBJECT = 62392;
	private static final String[] MEDAL = {"<col=ffd700>", "<col=e0e0e0>", "<col=cd7f32>"};

	private RivalsLeaderboard() {
	}

	public static void open(Player p) {
		RivalsProfile.RivalsLadder.update(p); // older entries predate the wins / losses columns
		p.openInterface(ToplevelComponent.MAINMODAL, INTERFACE);
		PacketSender ps = p.getPacketSender();
		ps.sendString(INTERFACE, TITLE, "<col=ff981f><shad=000000>Division Rivals Leaderboard</shad></col>");
		ps.sendString(INTERFACE, SUBTITLE, "<col=dcdcdc><shad=000000>Ranked by Skill Rating (SR) · Top 20 Global Combatants</shad></col>");
		for (int[] h : HEADERS) {
			ps.sendString(INTERFACE, h[0], "SR");
			ps.sendString(INTERFACE, h[1], "Wins");
			ps.sendString(INTERFACE, h[2], "Losses");
		}
		List<RivalsProfile.RivalsLadder.Entry> top = RivalsProfile.RivalsLadder.top(ROWS_PER_PANEL * 2);
		for (int i = 0; i < ROWS_PER_PANEL * 2; i++) {
			int row = FIRST_ROW[i / ROWS_PER_PANEL] + (i % ROWS_PER_PANEL) * ROW_STRIDE;
			if (i >= top.size()) {
				ps.sendString(INTERFACE, row + RANK, "<col=9a9a9a>" + (i + 1) + "</col>");
				ps.sendString(INTERFACE, row + NAME, "<col=9a9a9a>-</col>");
				for (int k : new int[]{COL1, COL2, COL3})
					ps.sendString(INTERFACE, row + k, "");
				continue;
			}
			RivalsProfile.RivalsLadder.Entry e = top.get(i);
			boolean self = e.name.equalsIgnoreCase(p.getName());
			ps.sendString(INTERFACE, row + RANK, (i < 3 ? MEDAL[i] : "<col=ffc35a>") + (i + 1) + "</col>");
			ps.sendString(INTERFACE, row + NAME, (self ? "<col=40ff40>" : i < 3 ? MEDAL[i] : "<col=ffffff>") + e.name
				+ (e.division == 0 ? " <col=ffd700>*</col>" : "") + "</col>");
			ps.sendString(INTERFACE, row + COL1, "<col=00ff00>" + String.format(Locale.ROOT, "%,d", e.rating) + "</col>");
			ps.sendString(INTERFACE, row + COL2, "<col=ffffff>" + e.wins + "</col>");
			ps.sendString(INTERFACE, row + COL3, "<col=cf6666>" + e.losses + "</col>");
		}
		ps.sendString(INTERFACE, PERSONAL, "<col=ffc35a>Your Standing:</col> <col=ffffff>" + String.format(Locale.ROOT, "%,d", p.rivalsRating)
			+ " SR</col>  <col=5a4d3a>|</col>  <col=ffc35a>Tier:</col> <col=ffd700>" + (p.rivalsDivision == 0 ? "Elite #" + RivalsProfile.RivalsLadder.eliteRank(p)
			: RivalsProfile.name(p.rivalsDivision) + " (D" + p.rivalsDivision + ")") + "</col>  <col=5a4d3a>|</col>  <col=ffc35a>Record:</col> <col=00ff00>"
			+ p.rivalsWins + "W</col> <col=ff4444>" + p.rivalsLosses + "L</col>");
		ps.sendString(INTERFACE, COUNTDOWN, "");
		ps.sendString(INTERFACE, FOOTER, "<col=a0a0a0><shad=000000>Queue for ranked matches via <col=ffffff>::rivals</col> or speak to the "
			+ "<col=ffffff>Rivals Overseer</col>.  <col=ffd700>*</col> = Elite</shad></col>");
		ps.setHidden(INTERFACE, HUB_BUTTON, true);
		ps.setHidden(INTERFACE, HUB_BUTTON + 1, true);
	}
}

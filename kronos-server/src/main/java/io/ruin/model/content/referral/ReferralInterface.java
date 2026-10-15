package io.ruin.model.content.referral;

import io.ruin.Server;
import io.ruin.cache.ObjType;
import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.InterfaceHandler;
import io.ruin.model.inter.ToplevelComponent;
import io.ruin.model.inter.actions.SimpleAction;
import io.ruin.network.PacketSender;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Zelus Referrals (interface 1165, built by .dev/cache-restore-tool BuildReferralInterface): the "Who invited you?"
 * name box + Submit (same rules as ::referfriend, see {@link ReferralCommand#tryLink}), the rewards on both sides,
 * your referrals / rank, the Top 10 Referrers of the current promotion ({@link ReferralPromo}) and its prize.
 * Opened by ::referrals / ::referfriend with no name. Rewards stay milestone-gated ({@link ReferralSystem}).
 */
public final class ReferralInterface {

	public static final int INTERFACE = 1165;
	private static final int CLOSE = 2, TITLE = 3, PROMO_TIMER = 8, PROMO_BANNER = 9, INPUT = 16, INPUT_TEXT = 20,
		SUBMIT = 21, STATUS = 23, YOU_ITEM_1 = 30, YOU_ITEM_2 = 33, INVITER_ITEM = 36, INVITER_POINTS = 37,
		REWARD_NOTE = 38, MY_REFERRALS = 44, MY_RANK = 45, MY_NOTE = 46, FIRST_ROW = 56, ROW_STRIDE = 4, ROWS = 10,
		FIRST_PRIZE = 98, PRIZE_STRIDE = 3, PRIZES = 5, PRIZE_CAPTION = 113;

	private static final String ORANGE = "<col=ff981f>", WHITE = "<col=ffffff>", GREY = "<col=9a9a9a>",
		GREEN = "<col=00ff00>", RED = "<col=ff5050>", GOLD = "<col=ffc35a>", END = "</col>";

	/** Per-viewer state: the typed inviter name and the last Submit result. */
	private static final class View {
		String typed;
		String error;
	}

	private static final Map<String, View> views = new HashMap<>();

	public static void register() {
		InterfaceHandler.register(INTERFACE, h -> {
			h.actions[CLOSE] = (SimpleAction) Player::closeInterfaces;
			h.actions[INPUT] = (SimpleAction) ReferralInterface::enterName;
			h.actions[SUBMIT] = (SimpleAction) ReferralInterface::submit;
			h.actions[YOU_ITEM_1] = (SimpleAction) p -> examine(p, ReferralSystem.REFERRED_MYSTERY_BOX_ITEM_ID);
			h.actions[YOU_ITEM_2] = (SimpleAction) p -> examine(p, ReferralSystem.REFERRED_XP_SCROLL_ITEM_ID);
			h.actions[INVITER_ITEM] = (SimpleAction) p -> examine(p, ReferralSystem.REFERRER_BOX_ITEM_ID);
			for (int k = 0; k < PRIZES; k++) {
				int index = k;
				h.actions[FIRST_PRIZE + k * PRIZE_STRIDE + 2] = (SimpleAction) p -> {
					List<ReferralPromo.Prize> prizes = ReferralPromo.prizes();
					if (index < prizes.size())
						examine(p, prizes.get(index).id);
				};
			}
			// i = the interface replacing this one: a redraw (re-opening 1165 itself) keeps the typed name
			h.closedAction = (p, i) -> {
				if (i != INTERFACE)
					views.remove(p.uuid());
			};
		});
	}

	public static void open(Player player) {
		player.openInterface(ToplevelComponent.MAINMODAL, INTERFACE);
		draw(player);
	}

	private static View view(Player player) {
		return views.computeIfAbsent(player.uuid(), k -> new View());
	}

	/** The chatbox prompt leaves the screen open, so the result is drawn in place (re-opened only if it was closed). */
	/**
	 * Always opens the chatbox name prompt. It used to do nothing at all for accounts past the 2 hour window (or
	 * already linked), which looked like a dead box: now the reason is shown after the name is entered.
	 */
	private static void enterName(Player player) {
		player.nameInput("Enter the username of the player who invited you:", name -> {
			View v = view(player);
			v.typed = name == null ? null : name.trim();
			v.error = null;
			redraw(player);
		});
	}

	private static void submit(Player player) {
		View v = view(player);
		if (v.typed == null || v.typed.isEmpty()) {
			player.nameInput("Enter the username of the player who invited you:", name -> {
				View view = view(player);
				view.typed = name == null ? null : name.trim();
				link(player, view);
			});
			return;
		}
		link(player, v);
	}

	private static void link(Player player, View v) {
		v.error = ReferralCommand.tryLink(player, v.typed == null ? "" : v.typed);
		if (v.error != null)
			player.sendMessage("<col=7f0000>" + v.error + "</col>");
		redraw(player);
	}

	private static void redraw(Player player) {
		if (player.isVisibleInterface(INTERFACE))
			draw(player);
		else
			open(player);
	}

	private static void draw(Player player) {
		PacketSender ps = player.getPacketSender();
		View v = view(player);
		ps.sendString(INTERFACE, TITLE, "Zelus Referrals");

		long now = System.currentTimeMillis(), start = ReferralPromo.startMillis(), end = ReferralPromo.endMillis();
		ps.sendString(INTERFACE, PROMO_TIMER, now >= end ? GREY + "Promotion ended" + END
			: "Ends in " + WHITE + countdown(end - now) + END);
		ps.sendString(INTERFACE, PROMO_BANNER, ReferralPromo.banner());

		// ---- who invited you ----
		boolean linked = player.referredBy != null;
		if (linked) {
			ps.sendString(INTERFACE, INPUT_TEXT, WHITE + player.referredBy + END);
		} else if (v.typed != null && !v.typed.isEmpty()) {
			ps.sendString(INTERFACE, INPUT_TEXT, WHITE + v.typed + END);
		} else {
			ps.sendString(INTERFACE, INPUT_TEXT, GREY + "Enter Name" + END);
		}
		ps.sendString(INTERFACE, SUBMIT + 1, linked ? "Linked" : "Submit");
		ps.sendString(INTERFACE, STATUS, status(player, v));

		// ---- rewards ----
		ps.sendItem(INTERFACE, YOU_ITEM_1, ReferralSystem.REFERRED_MYSTERY_BOX_ITEM_ID, 1);
		ps.sendItem(INTERFACE, YOU_ITEM_2, ReferralSystem.REFERRED_XP_SCROLL_ITEM_ID, ReferralSystem.REFERRED_XP_SCROLL_AMOUNT);
		ps.sendItem(INTERFACE, INVITER_ITEM, ReferralSystem.REFERRER_BOX_ITEM_ID, 1);
		ps.sendString(INTERFACE, INVITER_POINTS, "+" + fmt(ReferralSystem.REFERRER_POINT_REWARD) + "<br>Donator pts");
		ps.sendString(INTERFACE, REWARD_NOTE, "Paid when the new player reaches " + ReferralSystem.formatMilestonePlaytime()
			+ " played or " + ReferralSystem.MILESTONE_TOTAL_LEVEL + " total level.");

		// ---- leaderboard ----
		List<ReferralLeaderboard.Row> ranking = ReferralLeaderboard.ranking(start, end);
		int myRank = 0, myPromo = 0;
		for (int r = 0; r < ranking.size(); r++) {
			if (ranking.get(r).name().equalsIgnoreCase(player.getName())) {
				myRank = r + 1;
				myPromo = ranking.get(r).count();
				break;
			}
		}
		for (int r = 0; r < ROWS; r++) {
			int row = FIRST_ROW + r * ROW_STRIDE;
			if (r < ranking.size()) {
				ReferralLeaderboard.Row e = ranking.get(r);
				boolean me = e.name().equalsIgnoreCase(player.getName());
				ps.sendString(INTERFACE, row + 1, (r < 3 ? GOLD : ORANGE) + (r + 1) + END);
				ps.sendString(INTERFACE, row + 2, (me ? GOLD : WHITE) + e.name() + END);
				ps.sendString(INTERFACE, row + 3, fmt(e.count()));
			} else {
				ps.sendString(INTERFACE, row + 1, GREY + (r + 1) + END);
				ps.sendString(INTERFACE, row + 2, GREY + "-- Vacant --" + END);
				ps.sendString(INTERFACE, row + 3, "");
			}
		}
		ps.sendString(INTERFACE, MY_REFERRALS, "Your referrals: " + GREEN + fmt(player.totalReferredPlayers) + END
			+ GREY + " (all time)" + END);
		ps.sendString(INTERFACE, MY_RANK, "Your rank: " + (myRank > 0 ? GOLD + "#" + myRank + END : GREY + "Unranked" + END));
		ps.sendString(INTERFACE, MY_NOTE, "This promotion: " + WHITE + fmt(myPromo) + END
			+ (myPromo == 1 ? " referral" : " referrals"));

		// ---- prize showcase ----
		List<ReferralPromo.Prize> prizes = ReferralPromo.prizes();
		for (int k = 0; k < PRIZES; k++) {
			int slot = FIRST_PRIZE + k * PRIZE_STRIDE;
			boolean used = k < prizes.size();
			for (int part = 0; part < PRIZE_STRIDE; part++)
				ps.setHidden(INTERFACE, slot + part, !used);
			if (used)
				ps.sendItem(INTERFACE, slot + 2, prizes.get(k).id, Math.max(1, prizes.get(k).amount));
		}
		ps.sendString(INTERFACE, PRIZE_CAPTION, ReferralPromo.prizeCaption());
	}

	private static String status(Player player, View v) {
		if (player.referredBy != null) {
			if (player.referralRewardClaimed)
				return GREEN + "Linked to " + player.referredBy + ". Rewards claimed!" + END;
			long leftTicks = Server.toTicks(ReferralSystem.MILESTONE_PLAYTIME_SECONDS)
				- (player.playTime - player.referralLinkedAtPlayTime);
			long leftMinutes = Math.max(1, leftTicks * Server.tickMs() / 60_000L);
			return GREEN + "Linked to " + player.referredBy + "." + END + "<br>" + GREY + "Rewards in " + leftMinutes
				+ " min played or at " + ReferralSystem.MILESTONE_TOTAL_LEVEL + " total level." + END;
		}
		if (v.error != null)
			return RED + v.error + END;
		if (player.playTime >= Server.toTicks(ReferralSystem.REFERRAL_ELIGIBLE_PLAYTIME_SECONDS))
			return GREY + "Only accounts in their first " + (ReferralSystem.REFERRAL_ELIGIBLE_PLAYTIME_SECONDS / 3600)
				+ " hours of play can be referred. Invite your friends!" + END;
		return "Click the box, type your inviter's name, then press Submit.";
	}

	private static void examine(Player player, int itemId) {
		ObjType def = ObjType.get(itemId);
		if (def != null)
			player.sendMessage(def.name + ": " + def.examineText());
	}

	private static String countdown(long millis) {
		long minutes = Math.max(0, millis / 60_000L);
		long days = minutes / (60 * 24), hours = minutes / 60 % 24;
		if (days > 0)
			return days + "d " + hours + "h";
		return hours + "h " + (minutes % 60) + "m";
	}

	private static String fmt(long value) {
		return String.format(Locale.ROOT, "%,d", value);
	}

	private ReferralInterface() {
	}
}

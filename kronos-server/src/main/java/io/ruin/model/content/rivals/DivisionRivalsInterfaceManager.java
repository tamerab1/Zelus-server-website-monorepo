package io.ruin.model.content.rivals;

import io.ruin.HooksV2;
import io.ruin.cache.InterfaceDef;
import io.ruin.model.World;
import io.ruin.model.entity.player.Player;
import io.ruin.model.entity.player.PlayerGroup;
import io.ruin.model.inter.InterfaceHandler;
import io.ruin.model.inter.ToplevelComponent;
import io.ruin.network.incoming.handlers.CommandHandler;

import java.util.Locale;
import java.util.Set;

/**
 * Native interface 5120 adapter.
 * <p>
 * 2026-10-15: the interface now shows the REAL ranked profile (RivalsProfile) to every player via ::rivals, and its
 * buttons drive the real queue (RivalsQueueManager) and weekly chest. The original demo (fixtures in transient
 * Player.temp) stays available to developers through ::rivalsdemo / ::rivalstest / ::rivalsrank etc.; while a demo
 * session exists the window shows the demo, and ::rivals returns to the real profile.
 * <p>
 * Original note: all demo state lives only in transient Player.temp.
 * TODO backend: replace supplyState with authorized ranked snapshots; validate outcomes,
 * persist divisions/checkpoints, schedule weekly resets, compute/distribute rewards,
 * add anti-boosting and disconnect/forfeit rules, connect authoritative matchmaking,
 * and supply real server-authoritative skill ratings/global leaderboard positions.
 * Those systems must never reuse these visual test commands as real game results.
 */
public final class DivisionRivalsInterfaceManager {

	public static final int INTERFACE_ID = 5120;
	public static final int COMPONENT_COUNT = 103;
	private static final int CLOSE = 4;
	private static final int BADGE = 10;
	private static final int TITLE = 11;
	private static final int PROGRESS = 12;
	private static final int STREAK = 14;
	private static final int TRACK_HEADING = 16;
	private static final int TRACK_SUMMARY = 17;
	private static final int TRACK_HINT = 18;
	private static final int NODE_START = 20;
	private static final int TRACK = 30;
	private static final int RESET = 31;
	private static final int WINS = 32;
	private static final int TIER1 = 34;
	private static final int ELIGIBILITY = 36;
	private static final int TIER2 = 37;
	private static final int TIER1_STATUS = 38;
	private static final int TIER2_STATUS = 39;
	private static final int NODE_LABEL_START = 40;
	private static final int QUEUE_READY = 50;
	private static final int QUEUE_ACTIVE = 53;
	private static final int QUEUE_DISABLED = 56;
	private static final int CLAIM_READY = 60;
	private static final int CLAIM_DISABLED = 63;
	private static final int CLAIM_COMPLETE = 66;
	private static final int QUEUE_STATUS = 70;
	private static final int STATUS = 71;
	private static final int TIER1_CHECK = 100;
	private static final int ELITE_RATING = 101;
	private static final int ELITE_RANK = 102;
	private static final int BADGE_SPRITE_START = 24200;
	private static final String SESSION_KEY = "division-rivals-demo-session";
	private static final Set<String> COMMANDS = Set.of("rivals", "rivalsdemo", "rivalsnext", "rivalsprev",
			"rivalswin", "rivalsloss", "rivalsreset", "rivalstest", "rivalsrank", "rivalshelp", "rivalsforce", "rivalsprofile", "rivalswatch", "rivalsui");
	private static boolean registered;

	private DivisionRivalsInterfaceManager() {}

	public static void register() {
		// Commands still give a useful cache warning when a server has not packed this group.
		CommandHandler.hooks.register(CommandHandler.Hook.Handle.class, DivisionRivalsInterfaceManager::handleCommand);
		registerInterface();
		// a snapshot still on the player at login = the server stopped mid-match: give their gear back
		io.ruin.model.entity.shared.listeners.LoginListener.register(player -> {
			RivalsSnapshot.recover(player);
			RivalsProfile.rollWeek(player);
		});
		RivalsQueueManager.start();
		RivalsShop.register();
		RivalsItems.register();
		RivalsMatchmakingInterface.register();
		io.ruin.model.entity.npc.NPCAction.register(OVERSEER, 1, (player, npc) -> RivalsMatchmakingInterface.open(player)); // Matchmaking
		io.ruin.model.entity.npc.NPCAction.register(OVERSEER, 2, (player, npc) -> openReal(player));        // Profile
		io.ruin.model.entity.npc.NPCAction.register(OVERSEER, 3, (player, npc) -> RivalsShop.open(player));  // Rewards Shop
		// the leaderboard is the Rivals Leaderboard object in the lobby (RivalsLeaderboard.BOARD_OBJECT)
		io.ruin.model.map.object.actions.ObjectAction.register(RivalsLeaderboard.BOARD_OBJECT, "View", (player, obj) -> RivalsLeaderboard.open(player));
		RivalsCape.register();
	}

	/** The Rivals lobby (::rivals) and the Rivals Overseer NPC (3351, spawned at 3360,3226 in duel_arena.json). */
	public static final io.ruin.model.map.Position LOBBY = new io.ruin.model.map.Position(3360, 3232, 0);
	public static final int OVERSEER = 3351;

	private static void registerInterface() {
		if (registered || !isPacked())
			return;
		InterfaceHandler.register(INTERFACE_ID, handler -> {
			handler.simpleAction(CLOSE, DivisionRivalsInterfaceManager::close);
			for (int component : new int[] {50, 51, 52})
				handler.simpleAction(component, player -> updateQueue(player, false));
			for (int component : new int[] {53, 54, 55})
				handler.simpleAction(component, player -> updateQueue(player, true));
			for (int component : new int[] {60, 61, 62})
				handler.simpleAction(component, DivisionRivalsInterfaceManager::claim);
			// No close cleanup: reopening must retain the session's demo queue/claim/progress.
			handler.openAction = DivisionRivalsInterfaceManager::refresh; // demo session -> demo, otherwise the real profile
		});
		registered = true;
	}

	private static boolean isPacked() {
		return InterfaceDef.COUNTS != null && INTERFACE_ID < InterfaceDef.COUNTS.length
				&& InterfaceDef.COUNTS[INTERFACE_ID] >= COMPONENT_COUNT;
	}

	private static boolean hasAccess(Player player) {
		return DivisionRivalsDemoAccess.allowed(player.isDev(), player.isGroup(PlayerGroup.BETA_TESTER), World.isDev());
	}

	public static HooksV2.Result handleCommand(CommandHandler.Hook.Handle command) {
		if (!COMMANDS.contains(command.command()))
			return HooksV2.Result.Pass;
		Player player = command.player();
		if (command.command().equals("rivalsui")) {
			if (isPacked())
				RivalsMatchmakingInterface.open(player);
			return HooksV2.Result.Return;
		}
		if (command.command().equals("rivals")) {
			if (!isPacked()) {
				player.sendMessage("Division Rivals requires packed interface 5120.");
				return HooksV2.Result.Return;
			}
			registerInterface();
			player.temp.remove(SESSION_KEY); // leave any developer demo: show the real profile
			if (RivalsQueueManager.inMatch(player) || RivalsSnapshot.active(player)) {
				openReal(player);
				return HooksV2.Result.Return;
			}
			// ::rivals = the Rivals lobby (the Overseer stands there: Matchmaking / Rewards Shop / Leaderboard)
			io.ruin.model.skills.magic.spells.modern.ModernTeleport.teleport(player, LOBBY.getX(), LOBBY.getY(), LOBBY.getZ());
			return HooksV2.Result.Return;
		}
		if (!hasAccess(player)) {
			player.sendMessage("Division Rivals demo is restricted to developers and development-world beta testers.");
			return HooksV2.Result.Return;
		}
		if (command.command().equals("rivalshelp")) {
			sendTestHelp(player);
			return HooksV2.Result.Return;
		}
		if (!isPacked()) {
			player.sendMessage("Division Rivals demo requires packed interface 5120. Build the game cache first.");
			return HooksV2.Result.Return;
		}
		registerInterface();
		if (command.command().equals("rivalsforce")) {
			forceMatch(player, command.args());
			return HooksV2.Result.Return;
		}
		if (command.command().equals("rivalswatch")) {
			watch(player, command.args());
			return HooksV2.Result.Return;
		}
		if (command.command().equals("rivalsprofile")) {
			player.sendMessage("Rivals: " + player.rivalsRating + " SR, division " + player.rivalsDivision + " step " + player.rivalsProgress
				+ " (checkpoint " + player.rivalsCheckpoint + "), " + player.rivalsWins + "W/" + player.rivalsLosses + "L, streak "
				+ player.rivalsStreak + ", weekly " + player.rivalsWeeklyWins + (player.rivalsWeeklyClaimed ? " (claimed)" : "")
				+ ", " + player.rivalsPoints + " pts.");
			return HooksV2.Result.Return;
		}
		DivisionRivalsDemoState state = session(player, true);
		switch (command.command()) {
			case "rivalsdemo" -> {
				if (command.args().length > 0 && !applyArguments(state, command.args())) {
					player.sendMessage("Usage: ::rivalsdemo [ready|disabled|eligible|complete|checkpoint|locked|elite|steps 1-10]");
					return HooksV2.Result.Return;
				}
				open(player);
			}
			case "rivalsnext" -> state.nextDivision();
			case "rivalsprev" -> state.previousDivision();
			case "rivalswin" -> state.win();
			case "rivalsloss" -> state.loss();
			case "rivalsreset" -> state.reset();
			case "rivalstest" -> {
				if (!applyTestArguments(state, command.args())) {
					player.sendMessage("Usage: ::rivalstest [name or 1-14]. Use ::rivalshelp to list states.");
					return HooksV2.Result.Return;
				}
				open(player);
				player.sendMessage("Rivals preview " + state.testPresetNumber() + "/"
						+ DivisionRivalsDemoState.TEST_PRESETS.size() + ": " + state.testPresetName()
						+ ". Use ::rivalstest for the next state or ::rivalshelp.");
			}
			case "rivalsrank" -> {
				if (!applyRankArguments(state, command.args())) {
					player.sendMessage("Usage: ::rivalsrank <10-1 or elite>.");
					return HooksV2.Result.Return;
				}
				open(player);
				player.sendMessage("Rivals preview rank: " + (state.snapshot().elite() ? "Elite"
						: "Division " + state.snapshot().division() + " - " + state.snapshot().divisionName()) + ".");
			}
		}
		refresh(player);
		if (!player.isVisibleInterface(INTERFACE_ID))
			player.sendMessage("Division Rivals demo updated. Use ::rivals to view your session.");
		return HooksV2.Result.Return;
	}

	private static void sendTestHelp(Player player) {
		player.sendMessage("::rivalstest cycles preview states; ::rivalstest <name or 1-14> selects one.");
		player.sendMessage("States 1-7: ready, queued, disabled, weekly, tier1, eligible, claimed.");
		player.sendMessage("States 8-14: locked, current, checkpoint, complete, elite, short, long.");
		player.sendMessage("::rivalsrank <10-1 or elite> shows a badge. ::rivalswin / ::rivalsloss test results.");
		player.sendMessage("::rivalsreset restores default. ::rivalsdemo steps <1-10> changes track length.");
	}

	static boolean applyTestArguments(DivisionRivalsDemoState state, String[] args) {
		if (args.length == 0) {
			state.nextTestPreset();
			return true;
		}
		return args.length == 1 && state.testPreset(args[0]);
	}

	static boolean applyRankArguments(DivisionRivalsDemoState state, String[] args) {
		if (args.length != 1)
			return false;
		try {
			int division = args[0].equalsIgnoreCase("elite") ? 0 : Integer.parseInt(args[0]);
			state.selectDivision(division);
			return true;
		} catch (IllegalArgumentException ignored) {
			return false;
		}
	}

	private static boolean applyArguments(DivisionRivalsDemoState state, String[] args) {
		if (args.length == 1)
			return state.preset(args[0].toLowerCase(Locale.ROOT));
		if (args.length == 2 && args[0].equalsIgnoreCase("steps")) {
			try {
				state.configureSteps(Integer.parseInt(args[1]));
				return true;
			} catch (IllegalArgumentException ignored) {
				return false;
			}
		}
		return false;
	}

	public static void open(Player player) {
		if (!hasAccess(player) || !isPacked())
			return;
		registerInterface();
		session(player, true);
		if (!player.isVisibleInterface(INTERFACE_ID))
			player.openInterface(ToplevelComponent.MAINMODAL, INTERFACE_ID);
		else
			refresh(player);
	}

	private static DivisionRivalsDemoState session(Player player, boolean create) {
		Object existing = player.temp.get(SESSION_KEY);
		if (existing instanceof DivisionRivalsDemoState state)
			return state;
		if (!create)
			return null;
		DivisionRivalsDemoState state = new DivisionRivalsDemoState();
		player.temp.put(SESSION_KEY, state);
		return state;
	}

	private static boolean validAction(Player player) {
		return DivisionRivalsDemoAccess.actionAllowed(hasAccess(player),
				isPacked() && player.isVisibleInterface(INTERFACE_ID), session(player, false) != null);
	}

	private static boolean demo(Player player) {
		return session(player, false) != null;
	}

	private static void close(Player player) {
		if (player.isVisibleInterface(INTERFACE_ID))
			player.closeInterface(ToplevelComponent.MAINMODAL);
	}

	private static void updateQueue(Player player, boolean queued) {
		if (!demo(player)) {
			if (!player.isVisibleInterface(INTERFACE_ID))
				return;
			if (queued)
				RivalsQueueManager.leave(player, "You left the Rivals queue.");
			else
				RivalsQueueManager.chooseMode(player);
			return;
		}
		if (!validAction(player))
			return;
		var state = session(player, false);
		var expected = queued ? DivisionRivalsDemoState.QueueStatus.QUEUED : DivisionRivalsDemoState.QueueStatus.NOT_QUEUED;
		// Repeated/stale/forged enter and leave packets must not reverse a completed action.
		if (state.snapshot().queueStatus() == expected && state.toggleQueue())
			refresh(player);
	}

	private static void claim(Player player) {
		if (!demo(player)) {
			if (!player.isVisibleInterface(INTERFACE_ID))
				return;
			RivalsProfile.claimWeekly(player);
			refreshIfOpen(player);
			return;
		}
		if (!validAction(player))
			return;
		boolean claimed = session(player, false).claimReward();
		player.sendMessage(claimed ? "Demo rewards claimed. No real items or currency were awarded."
				: "No demo reward is claimable. Reach 8 weekly demo wins, or use ::rivalsdemo eligible.");
		refresh(player);
	}

	/** Integration boundary: replace this supplier, then render only authorized authoritative state. */
	public static DivisionRivalsDemoState.InterfaceState supplyState(Player player) {
		DivisionRivalsDemoState state = session(player, false);
		return state == null ? null : state.snapshot();
	}

	public static void refresh(Player player) {
		if (!demo(player)) {
			if (player.isVisibleInterface(INTERFACE_ID))
				renderReal(player);
			return;
		}
		if (!validAction(player))
			return;
		render(supplyState(player), new PacketView(player));
	}

	/* ---------------- the real ranked profile ---------------- */

	public static void openReal(Player player) {
		if (!isPacked())
			return;
		registerInterface();
		if (!player.isVisibleInterface(INTERFACE_ID))
			player.openInterface(ToplevelComponent.MAINMODAL, INTERFACE_ID); // openAction renders it
		else
			renderReal(player);
	}

	/** Re-renders the real profile if the player has the window open (and isn't looking at the developer demo). */
	public static void refreshIfOpen(Player player) {
		if (player.isOnline() && !demo(player) && player.isVisibleInterface(INTERFACE_ID))
			renderReal(player);
		RivalsMatchmakingInterface.refreshIfOpen(player);
	}

	/** Just the searching timer line, for queued players with the window open. */
	static void refreshQueueLine(Player player) {
		RivalsMatchmakingInterface.refreshStatus(player);
		if (!demo(player) && player.isVisibleInterface(INTERFACE_ID)) {
			String line = RivalsQueueManager.searchLine(player);
			if (line != null)
				player.getPacketSender().sendString(INTERFACE_ID, QUEUE_STATUS, line);
		}
	}

	static DivisionRivalsDemoState.InterfaceState realState(Player p) {
		RivalsProfile.rollWeek(p);
		DivisionRivalsDemoState.QueueStatus queue = RivalsQueueManager.inMatch(p) || RivalsSnapshot.active(p)
				? DivisionRivalsDemoState.QueueStatus.DISABLED
				: RivalsQueueManager.isQueued(p) ? DivisionRivalsDemoState.QueueStatus.QUEUED
				: DivisionRivalsDemoState.QueueStatus.NOT_QUEUED;
		boolean elite = p.rivalsDivision == 0;
		return new DivisionRivalsDemoState.InterfaceState(p.rivalsDivision,
				RivalsProfile.name(p.rivalsDivision), elite ? 0 : p.rivalsProgress, RivalsProfile.STEPS,
				elite ? 0 : p.rivalsCheckpoint, p.rivalsStreak, p.rivalsWeeklyWins, RivalsProfile.untilWeeklyReset(), queue,
				p.rivalsWeeklyWins >= RivalsProfile.TIER1_WINS, p.rivalsWeeklyWins >= RivalsProfile.TIER2_WINS,
				RivalsProfile.claimable(p), p.rivalsWeeklyClaimed, p.rivalsRating,
				elite ? RivalsProfile.RivalsLadder.eliteRank(p) : 0);
	}

	private static void renderReal(Player player) {
		DivisionRivalsDemoState.InterfaceState state = realState(player);
		PacketView view = new PacketView(player);
		render(state, view);
		// the demo wording, replaced with live values
		String search = RivalsQueueManager.searchLine(player);
		view.text(QUEUE_STATUS, search != null ? search
				: state.queueStatus() == DivisionRivalsDemoState.QueueStatus.DISABLED ? "In a Rivals match"
				: "Ready - Find match to pick one of the 4 brackets");
		// the package's static sub-header read "Zelus / Ranked 1v1 / Developer preview"
		view.text(HEADER_SUBTITLE, "<col=a0a0a0>Zelus</col> <col=ff981f>Season 1</col> <col=a0a0a0>\u00b7 Ranked Matchmaking</col>");
		view.text(STATUS, String.format(Locale.ROOT, "%,d SR  |  %,d Rivals Points  |  %dW %dL  |  Best streak %d",
				player.rivalsRating, player.rivalsPoints, player.rivalsWins, player.rivalsLosses, player.rivalsBestStreak));
		boolean t1Done = player.rivalsWeeklyClaimedT1, t2Done = player.rivalsWeeklyClaimed;
		view.text(ELIGIBILITY, RivalsProfile.claimable(player) ? "<col=ffd56b>Ready to claim!</col>"
				: t2Done ? "<col=9ce891>Both chests claimed</col>"
				: t1Done ? "<col=9ce891>Tier 1 claimed</col> - tier 2 at 8"
				: "Tier 1 at 4 wins, tier 2 at 8");
		// the claim button is always clickable; it explains itself when nothing is ready
		view.hidden(CLAIM_READY, false);
		view.hidden(CLAIM_DISABLED, true);
		view.hidden(CLAIM_COMPLETE, true);
	}

	/** Staff: ::rivalswatch <player> watches their running match; with no name, lists the running matches. */
	private static void watch(Player player, String[] args) {
		if (args.length == 0) {
			if (RivalsQueueManager.matches().isEmpty())
				player.sendMessage("No Rivals matches are running.");
			for (RivalsArenaInstance m : RivalsQueueManager.matches())
				player.sendMessage("Rivals: " + m.description());
			return;
		}
		String name = String.join(" ", args).replace('_', ' ').trim();
		Player fighter = World.getPlayer(name);
		RivalsArenaInstance match = fighter == null ? null : RivalsQueueManager.matchOf(fighter);
		if (match == null) {
			player.sendMessage(name + " is not in a Rivals match.");
			return;
		}
		if (RivalsQueueManager.matchOf(player) != null || RivalsSnapshot.active(player)) {
			player.sendMessage("You can't watch while fighting.");
			return;
		}
		player.sendMessage(match.spectate(player) ? "Watching " + match.description() + ". You'll be sent back when it ends."
			: "That match is ending.");
	}

	/** Header sub-title text on 5120 (the package's static "Developer preview" line), replaced on every render. */
	private static final int HEADER_SUBTITLE = 5;

	/** Developer: ::rivalsforce <player name> [endgame|dharok|outlaw|pure] starts a ranked match with that player right away. */
	private static void forceMatch(Player player, String[] args) {
		if (args.length < 1) {
			player.sendMessage("Usage: ::rivalsforce <player name> [endgame|dharok|outlaw|pure]");
			return;
		}
		// the whole rest of the line is the name (names have spaces); an optional last word picks the mode
		int end = args.length;
		RivalsMode mode = RivalsMode.ENDGAME_PRISMATIC;
		RivalsMode picked = RivalsMode.byKey(args[end - 1]);
		if (end > 1 && picked != null) {
			mode = picked;
			end--;
		}
		String name = String.join(" ", java.util.Arrays.copyOfRange(args, 0, end)).replace('_', ' ').trim();
		Player other = World.getPlayer(name);
		if (other == null || other == player) {
			player.sendMessage("No other online player called " + name + ".");
			return;
		}
		for (Player p : new Player[]{player, other}) {
			String problem = RivalsQueueManager.isQueued(p) ? null : RivalsQueueManager.problem(p);
			if (problem != null) {
				player.sendMessage(p.getName() + ": " + problem);
				return;
			}
		}
		if (RivalsQueueManager.startMatch(player, other, mode, true))
			player.sendMessage("Forced a " + mode.label + " Rivals match with " + other.getName() + ".");
	}

	/** Small packet-output boundary also permits verification without a logged-in client. */
	interface View {
		void text(int component, String value);
		void hidden(int component, boolean hidden);
		void sprite(int component, int sprite);
		void position(int component, int x, int y);
	}

	private record PacketView(Player player) implements View {
		@Override public void text(int component, String value) {
			player.getPacketSender().sendString(INTERFACE_ID, component, value);
		}
		@Override public void hidden(int component, boolean hidden) {
			player.getPacketSender().setHidden(INTERFACE_ID, component, hidden);
		}
		@Override public void sprite(int component, int sprite) {
			player.getPacketSender().sendClientScript(44, "Ii", INTERFACE_ID << 16 | component, sprite);
		}
		@Override public void position(int component, int x, int y) {
			player.getPacketSender().setAlignment(INTERFACE_ID, component, x, y);
		}
	}

	static void render(DivisionRivalsDemoState.InterfaceState state, View view) {
		view.sprite(BADGE, BADGE_SPRITE_START + (state.elite() ? 10 : 10 - state.division()));
		view.text(TITLE, state.elite() ? "Elite<br>Max rank" : "Division " + state.division() + "<br>" + state.divisionName());
		view.text(PROGRESS, "Progress: " + state.divisionProgress() + " / " + state.divisionRequiredSteps());
		view.hidden(PROGRESS, state.elite());
		view.text(STREAK, state.winStreak() + "x win<br>streak");
		view.text(TRACK_HEADING, state.elite() ? "Elite leaderboard" : "Division progression");
		view.text(TRACK_SUMMARY, state.elite() ? "Elite - Maximum rank"
				: state.divisionProgress() + " / " + state.divisionRequiredSteps() + " steps | "
				+ (state.divisionProgress() == state.divisionRequiredSteps() ? "Division complete"
				: (state.divisionRequiredSteps() - state.divisionProgress()) + " wins to promotion"));
		view.text(TRACK_HINT, state.elite() ? "Elite is the highest division."
				: state.checkpointStep() == 0 ? "1 step to promote | No safety checkpoint"
				: "Checkpoint " + state.checkpointStep() + (state.checkpointProgress() > 0 ? " secured" : " locked")
				+ " | Promotion at " + state.divisionRequiredSteps());
		view.hidden(TRACK, state.elite());
		view.hidden(ELITE_RATING, !state.elite());
		view.hidden(ELITE_RANK, !state.elite());
		view.text(ELITE_RATING, String.format(Locale.ROOT, "Rating: %,d SR", state.skillRating()));
		view.text(ELITE_RANK, "Global rank: #" + state.globalLeaderboardRank());
		updateNodes(view, state);
		view.text(RESET, "Resets in: " + state.weeklyResetTime().toDays() + "d " + state.weeklyResetTime().toHoursPart() + "h");
		view.text(WINS, "Weekly wins: " + state.weeklyWins());
		view.text(TIER1, "Tier 1: " + Math.min(state.weeklyWins(), 4) + "/4");
		view.text(TIER2, "Tier 2: " + Math.min(state.weeklyWins(), 8) + "/8");
		view.text(TIER1_STATUS, state.tier1Completed() ? "<col=9ce891>Complete</col>" : "<col=aaa6a0>In progress</col>");
		view.hidden(TIER1_CHECK, !state.tier1Completed());
		view.text(TIER2_STATUS, state.tier2Completed() ? "<col=9ce891>Complete</col>" : "<col=aaa6a0>In progress</col>");
		view.text(ELIGIBILITY, state.rewardClaimed() ? "<col=9ce891>Demo claimed</col>"
				: state.rewardClaimable() ? "<col=ffd56b>Claimable</col>" : "Locked (8 wins)");
		view.sprite(51, 24227);
		view.sprite(54, 24229);
		view.sprite(61, 24220);
		view.sprite(CLOSE, 24223);
		view.hidden(QUEUE_READY, state.queueStatus() != DivisionRivalsDemoState.QueueStatus.NOT_QUEUED);
		view.hidden(QUEUE_ACTIVE, state.queueStatus() != DivisionRivalsDemoState.QueueStatus.QUEUED);
		view.hidden(QUEUE_DISABLED, state.queueStatus() != DivisionRivalsDemoState.QueueStatus.DISABLED);
		view.hidden(CLAIM_READY, !state.rewardClaimable());
		view.hidden(CLAIM_DISABLED, state.rewardClaimable() || state.rewardClaimed());
		view.hidden(CLAIM_COMPLETE, !state.rewardClaimed());
		view.text(QUEUE_STATUS, state.queueStatus() == DivisionRivalsDemoState.QueueStatus.QUEUED ? "Searching for opponent..."
				: state.queueStatus() == DivisionRivalsDemoState.QueueStatus.DISABLED ? "Queue unavailable in this demo state."
				: "Ready for a practice queue");
		view.text(STATUS, "Demo preview | Session only | No real rewards");
	}

	private static void updateNodes(View view, DivisionRivalsDemoState.InterfaceState state) {
		for (int i = 0; i < DivisionRivalsDemoState.MAX_STEPS; i++) {
			int node = NODE_START + i;
			int label = NODE_LABEL_START + i;
			boolean hidden = state.elite() || i >= state.divisionRequiredSteps();
			view.hidden(node, hidden);
			view.hidden(label, hidden);
			if (hidden)
				continue;
			int center = state.divisionRequiredSteps() == 1 ? 142 : 25 + (234 * i) / (state.divisionRequiredSteps() - 1);
			view.position(node, center - 12, 0);
			view.position(label, center - 25, 27);
			var nodeState = state.nodeState(i + 1);
			int sprite = switch (nodeState) {
				case INCOMPLETE -> 24216;
				case COMPLETE -> 24217;
				case CURRENT -> 24219;
				case CHECKPOINT_LOCKED -> 24214;
				case CHECKPOINT_UNLOCKED -> 24215;
				case PROMOTION -> 24218;
				case FINISHED -> 24226;
			};
			view.sprite(node, sprite);
			String value = switch (nodeState) {
				case CHECKPOINT_UNLOCKED -> "Safe";
				case CHECKPOINT_LOCKED -> "Locked";
				case PROMOTION -> "Up";
				case FINISHED -> "Done";
				case CURRENT -> Integer.toString(i + 1);
				default -> Integer.toString(i + 1);
			};
			view.text(label, value);
		}
	}
}

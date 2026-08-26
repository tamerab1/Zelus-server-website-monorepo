package io.ruin.model.activities.tournament;

import io.ruin.cache.Icon;
import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.dialogue.OptionsDialogue;
import io.ruin.model.inter.utils.Option;
import io.ruin.model.map.Bounds;
import io.ruin.model.map.MapListener;
import io.ruin.model.map.Position;
import io.ruin.model.map.object.GameObject;
import io.ruin.model.map.object.actions.ObjectAction;
import io.ruin.model.stat.Stat;
import io.ruin.model.stat.StatType;
import io.ruin.utility.Broadcast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * "The Pit Championship" - an 8-player single-elimination bracket fought
 * in the pit at home. Combat stats are equalized for the duration of the
 * tournament and restored afterwards.
 */
public class TournamentHandler {

	private static final String EVENT_NAME = "The Pit Championship";

	private static final int BRACKET_SIZE = 8;
	private static final int EQUALIZED_LEVEL = 99;

	private static final int CHAMPION_POINTS = 500;
	private static final int RUNNER_UP_POINTS = 250;
	private static final int SEMIFINALIST_POINTS = 100;

	/**
	 * Staging area between the blue barrier (registration gate) and the red
	 * barrier (the actual pit edge, see ARENA_BOUNDS below). An octagon
	 * (corners cut) bounded by x:[3073,3088], y:[3457,3472]. The four
	 * bounding-box corner tiles (e.g. 3073,3472) fall outside the actual
	 * walked area since the corners are clipped, but that's harmless for a
	 * bounds check - no player can ever stand there.
	 */
	public static final Bounds WAITING_BOUNDS = new Bounds(3073, 3457, 3088, 3472, -1);
	/** Center of the staging octagon - where queued/eliminated players wait. */
	public static final Position SIGNUP_POSITION = new Position(3080, 3465, 0);
	public static final Position WAITING_SPOT = SIGNUP_POSITION;

	/** The skull-and-crossbones fight floor, inside the red barrier. */
	public static final Bounds ARENA_BOUNDS = new Bounds(3075, 3459, 3086, 3470, -1);
	/** Two interior spots on opposite corners of the pit, inset from the walls. */
	public static final Position ARENA_SPAWN_A = new Position(3077, 3461, 0);
	public static final Position ARENA_SPAWN_B = new Position(3084, 3468, 0);

	private static final StatType[] COMBAT_STATS = {
		StatType.Attack, StatType.Defence, StatType.Strength,
		StatType.Hitpoints, StatType.Ranged, StatType.Prayer, StatType.Magic
	};

	private static final int MAX_PAST_CHAMPIONS = 10;

	private static final List<Player> queue = new ArrayList<>();
	private static final Map<Player, int[][]> savedStats = new HashMap<>();
	private static final LinkedList<String> pastChampions = new LinkedList<>();
	private static List<Player> bracket;
	private static TournamentMatch activeMatch;
	private static boolean running = false;

	/**
	 * The pit gate is one of these four "Blue Barrier" object ids (40437,
	 * 40452, 40453, 40454) - all visually identical and all placed via the
	 * RSPSi map edit, so which exact one is live at the pit couldn't be
	 * confirmed in-game. All four are wired to the same handler; whichever
	 * one is actually there will work.
	 */
	private static final int[] GATE_OBJECT_IDS = {40437, 40452, 40453, 40454};

	/** Registration board ("Pit Championship Board", patched from the base-game Scoreboard object). */
	private static final int BOARD_OBJECT_ID = 44930;
	/** Reward trophy ("Champion's Reward Trophy", patched from the base-game Clan Cup Trophy). */
	private static final int TROPHY_OBJECT_ID = 7127;

	private static final Set<Player> pendingRewards = new HashSet<>();

	public static void register() {
		MapListener.registerBounds(ARENA_BOUNDS);

		for (int gateId : GATE_OBJECT_IDS)
			ObjectAction.register(gateId, "Pass", (player, obj) ->
				player.sendMessage("You must register for " + EVENT_NAME + " at the Pit Championship Board first."));
		ObjectAction.register(BOARD_OBJECT_ID, "View", (player, obj) ->
			player.dialogue(new OptionsDialogue(
				new Option(queue.contains(player) ? "Leave the queue" : "Register for " + EVENT_NAME,
					() -> toggleQueue(player, obj)),
				new Option("View past champions", () -> showPastChampions(player))
			)));
		ObjectAction.register(TROPHY_OBJECT_ID, "Claim", (player, obj) -> claimReward(player));
	}

	private static void claimReward(Player player) {
		if (!pendingRewards.remove(player)) {
			player.sendMessage("You have no Champion reward to claim.");
			return;
		}
		player.tournamentPoints += CHAMPION_POINTS;
		player.sendMessage("You have claimed your " + CHAMPION_POINTS + " tournament points!");
	}

	private static void showPastChampions(Player player) {
		if (pastChampions.isEmpty()) {
			player.sendMessage("No " + EVENT_NAME + " has been completed yet.");
			return;
		}
		player.sendMessage("Recent " + EVENT_NAME + " champions: " + String.join(", ", pastChampions));
	}

	private static void toggleQueue(Player player, GameObject obj) {
		if (queue.contains(player))
			leave(player);
		else
			join(player, obj);
	}

	public static void join(Player player, GameObject gate) {
		if (running) {
			player.sendMessage(EVENT_NAME + " is already in progress. Please wait for it to finish.");
			return;
		}
		if (queue.contains(player)) {
			player.sendMessage("You are already queued for " + EVENT_NAME + ".");
			return;
		}
		queue.add(player);
		player.sendMessage("You have registered for " + EVENT_NAME + ".");
		player.teleportListener = p -> {
			leave(p);
			return true;
		};
		player.logoutListener = new io.ruin.model.entity.shared.listeners.LogoutListener().onLogout(TournamentHandler::leave);
		Broadcast.GLOBAL.sendNews(Icon.ADMINISTRATOR, EVENT_NAME,
			player.getName() + " has registered for " + EVENT_NAME + "! (" + queue.size() + "/" + BRACKET_SIZE + ")");
		broadcastQueue();
		if (queue.size() >= BRACKET_SIZE) {
			startTournament();
		}
	}

	/** Blocks a bracket player from teleporting away for the rest of the tournament. */
	private static void lockPlayer(Player player) {
		player.teleportListener = p -> {
			p.sendMessage("You can't leave " + EVENT_NAME + " once it has started.");
			return false;
		};
	}

	private static void unlockPlayer(Player player) {
		player.teleportListener = null;
	}

	public static void leave(Player player) {
		if (!queue.remove(player))
			return;
		player.teleportListener = null;
		player.logoutListener = null;
		player.sendMessage("You have left the " + EVENT_NAME + " queue.");
		broadcastQueue();
	}

	private static void broadcastQueue() {
		for (Player p : queue)
			p.sendMessage(EVENT_NAME + " queue: " + queue.size() + "/" + BRACKET_SIZE + " players.");
	}

	private static void startTournament() {
		running = true;
		bracket = new ArrayList<>(queue);
		queue.clear();
		Collections.shuffle(bracket);

		for (Player p : bracket) {
			p.logoutListener = null;
			equalizeStats(p);
			lockPlayer(p);
		}

		Broadcast.GLOBAL.sendNews(Icon.ADMINISTRATOR, EVENT_NAME,
			EVENT_NAME + " has begun with " +
				bracket.stream().map(Player::getName).collect(Collectors.joining(", ")) + "!");

		runNextMatch();
	}

	private static void runNextMatch() {
		if (bracket.size() == 1) {
			finishTournament(bracket.get(0));
			return;
		}

		Player p1 = bracket.remove(0);
		Player p2 = bracket.remove(0);
		for (Player waiting : bracket) {
			waiting.getMovement().teleport(WAITING_SPOT);
			lockPlayer(waiting);
		}

		activeMatch = new TournamentMatch(p1, p2);
		activeMatch.start();
	}

	static void onMatchComplete(Player winner, Player loser) {
		activeMatch = null;

		int remainingAfterLoss = bracket.size(); // players still waiting, not counting winner/loser
		int placementPoints = remainingAfterLoss <= 1 ? RUNNER_UP_POINTS : SEMIFINALIST_POINTS;
		loser.tournamentPoints += placementPoints;
		loser.sendMessage("You have been eliminated from " + EVENT_NAME + "! You received " + placementPoints + " tournament points.");
		restoreStats(loser);
		unlockPlayer(loser);
		loser.getMovement().teleport(SIGNUP_POSITION);

		bracket.add(0, winner);
		runNextMatch();
	}

	private static void finishTournament(Player champion) {
		running = false;
		champion.tournamentWins++;
		restoreStats(champion);
		unlockPlayer(champion);
		champion.getMovement().teleport(SIGNUP_POSITION);

		pendingRewards.add(champion);
		pastChampions.addFirst(champion.getName());
		while (pastChampions.size() > MAX_PAST_CHAMPIONS)
			pastChampions.removeLast();

		Broadcast.GLOBAL.sendNews(Icon.ADMINISTRATOR, EVENT_NAME,
			champion.getName() + " has won " + EVENT_NAME + "!");
		champion.sendMessage("Congratulations, you won " + EVENT_NAME + "! Please collect your reward from the Champion's Reward Trophy.");

		bracket = null;
	}

	private static void equalizeStats(Player player) {
		int[][] saved = new int[COMBAT_STATS.length][3];
		for (int i = 0; i < COMBAT_STATS.length; i++) {
			Stat stat = player.getStats().get(COMBAT_STATS[i]);
			saved[i][0] = stat.fixedLevel;
			saved[i][1] = stat.currentLevel;
			saved[i][2] = (int) stat.experience;
		}
		savedStats.put(player, saved);

		for (StatType type : COMBAT_STATS)
			player.getStats().set(type, EQUALIZED_LEVEL);
		player.setHp(EQUALIZED_LEVEL);
		player.getCombat().restoreSpecial(100);
	}

	private static void restoreStats(Player player) {
		int[][] saved = savedStats.remove(player);
		if (saved == null)
			return;
		for (int i = 0; i < COMBAT_STATS.length; i++) {
			player.getStats().set(COMBAT_STATS[i], saved[i][0], saved[i][2]);
			player.getStats().get(COMBAT_STATS[i]).currentLevel = saved[i][1];
			player.getPacketSender().sendStat(COMBAT_STATS[i].ordinal(), saved[i][1], saved[i][2]);
		}
		player.setHp(player.getStats().get(StatType.Hitpoints).currentLevel);
	}
}

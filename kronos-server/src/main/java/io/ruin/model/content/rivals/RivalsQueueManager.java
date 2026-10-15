package io.ruin.model.content.rivals;

import io.ruin.model.World;
import io.ruin.model.activities.tournament.TournamentHandler;
import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.dialogue.MessageDialogue;
import io.ruin.model.inter.dialogue.OptionsDialogue;
import io.ruin.model.inter.utils.Option;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Ranked Rivals queues, one per {@link RivalsMode}. "Find match" asks for a mode, checks the player may queue, and
 * adds them; every 2 ticks each queue pairs the two closest ratings whose gap fits the wider of the two players'
 * search windows (150 SR, +50 per 15 seconds waited, up to 1,000). Players who log out or stop being eligible
 * (combat, Wilderness, death...) are dropped with a message. Game thread only.
 * <p>
 * Ready check (2026-10-15): a pairing is not teleported straight in. Both players get an alert and an Accept / Decline
 * prompt with 15 seconds to answer; the match starts only when both accept. Whoever declines, times out, logs out or
 * stops being eligible leaves the queue; the other player goes back to the FRONT of the queue with their original
 * ticket (so their widened search window is kept).
 */
public final class RivalsQueueManager {

	private static final int BASE_WINDOW = 150, WINDOW_STEP = 50, WINDOW_STEP_MS = 15_000, MAX_WINDOW = 1000;
	private static final long ACCEPT_MS = 15_000;
	private static final int SOUND_MATCH_FOUND = 2276;
	private static final String TAG = "<col=990000><shad=000000>[Rivals]</shad></col>";

	private static final class Ticket {
		final Player player;
		final RivalsMode mode;
		final long joined = System.currentTimeMillis();

		Ticket(Player player, RivalsMode mode) {
			this.player = player;
			this.mode = mode;
		}

		int window() {
			long waited = System.currentTimeMillis() - joined;
			return (int) Math.min(MAX_WINDOW, BASE_WINDOW + (waited / WINDOW_STEP_MS) * WINDOW_STEP);
		}
	}

	private static final Map<RivalsMode, List<Ticket>> queues = new EnumMap<>(RivalsMode.class);
	private static final List<RivalsArenaInstance> matches = new ArrayList<>();

	/** A found pairing waiting for both players to accept. */
	private static final class Pending {
		final Ticket a, b;
		final long deadline = System.currentTimeMillis() + ACCEPT_MS;
		boolean acceptedA, acceptedB;
		int lastAnnounced = -1;

		Pending(Ticket a, Ticket b) {
			this.a = a;
			this.b = b;
		}

		boolean involves(Player p) {
			return a.player == p || b.player == p;
		}
	}

	private static final List<Pending> pending = new ArrayList<>();
	private static boolean started;

	static {
		for (RivalsMode m : RivalsMode.values())
			queues.put(m, new ArrayList<>());
	}

	private RivalsQueueManager() {
	}

	static void start() {
		if (started)
			return;
		started = true;
		World.startEvent(e -> {
			while (true) {
				e.delay(2);
				try {
					tick();
				} catch (Exception ex) {
					io.ruin.api.utils.ServerWrapper.logError("Rivals queue tick", ex);
				}
			}
		});
	}

	/* ---------------- queries ---------------- */

	static Ticket ticket(Player p) {
		for (List<Ticket> q : queues.values())
			for (Ticket t : q)
				if (t.player == p)
					return t;
		return null;
	}

	public static boolean isQueued(Player p) {
		return ticket(p) != null || pendingOf(p) != null;
	}

	private static Pending pendingOf(Player p) {
		for (Pending m : pending)
			if (m.involves(p))
				return m;
		return null;
	}

	/** True while a found match waits for this player's (or their opponent's) answer. */
	static boolean awaitingAccept(Player p) {
		return pendingOf(p) != null;
	}

	/** Players searching in a queue right now. */
	static int searching(RivalsMode mode) {
		return queues.get(mode).size();
	}

	public static boolean inMatch(Player p) {
		for (RivalsArenaInstance m : matches)
			if (m.involves(p))
				return true;
		return false;
	}

	/** "Searching for opponent... 0:42 | Main Tribrid NH", or null when not queued. */
	static String searchLine(Player p) {
		Pending m = pendingOf(p);
		if (m != null)
			return "Match found! Accept within " + secondsLeft(m) + "s  |  " + m.a.mode.label;
		Ticket t = ticket(p);
		if (t == null)
			return null;
		long s = (System.currentTimeMillis() - t.joined) / 1000;
		return "Searching for opponent... " + (s / 60) + ":" + String.format("%02d", s % 60) + "  |  " + t.mode.label;
	}

	/* ---------------- joining / leaving ---------------- */

	/** "Find match": pick a mode, then queue. */
	/** "Find match": the matchmaking window (queue choice, kit preview, search and ready check). */
	static void chooseMode(Player p) {
		RivalsMatchmakingInterface.open(p);
	}

	static void join(Player p, RivalsMode mode) {
		p.closeDialogue();
		String problem = problem(p);
		if (problem != null) {
			p.sendMessage("<col=990000><shad=000000>[Rivals]</shad></col> " + problem);
			return;
		}
		queues.get(mode).add(new Ticket(p, mode));
		p.sendMessage("<col=990000><shad=000000>[Rivals]</shad></col> Searching for a " + mode.label + " opponent (" + p.rivalsRating + " SR)...");
		DivisionRivalsInterfaceManager.refreshIfOpen(p);
	}

	static void leave(Player p, String why) {
		if (pendingOf(p) != null) {
			decline(p);
			return;
		}
		Ticket t = ticket(p);
		if (t == null)
			return;
		queues.get(t.mode).remove(t);
		if (why != null)
			p.sendMessage("<col=990000><shad=000000>[Rivals]</shad></col> " + why);
		DivisionRivalsInterfaceManager.refreshIfOpen(p);
	}

	/** Why the player can't queue right now, or null. */
	static String problem(Player p) {
		if (RivalsLayout.active(p))
			return "Save or cancel your Rivals layout first.";
		if (inMatch(p) || RivalsSnapshot.active(p))
			return "You are already in a Rivals match.";
		if (pendingOf(p) != null)
			return "You have a match waiting for your answer.";
		if (ticket(p) != null)
			return "You are already searching for a match.";
		if (p.getCombat().isDead())
			return "You can't queue while dead.";
		if (p.getCombat().isAttacking(16) || p.getCombat().isDefending(16))
			return "You can't queue while in combat.";
		if (p.wildernessLevel > 0)
			return "You can't queue from the Wilderness.";
		if (p.isLocked())
			return "You can't queue right now.";
		if (p.pvpPresetActive)
			return "Turn off your PvP preset before queueing (::pvpmode off).";
		if (p.getDuel().stage > 0)
			return "You can't queue during a duel.";
		if (TournamentHandler.isQueued(p) || TournamentHandler.inBracket(p))
			return "You can't queue for Rivals during a Pit Championship.";
		if (p.currentDynamicMap != null || p.inDynamicMap)
			return "You can't queue from inside an instance.";
		return null;
	}

	/* ---------------- matching ---------------- */

	private static void tick() {
		for (Map.Entry<RivalsMode, List<Ticket>> entry : queues.entrySet()) {
			List<Ticket> q = entry.getValue();
			Iterator<Ticket> it = q.iterator();
			while (it.hasNext()) {
				Ticket t = it.next();
				String problem = !t.player.isOnline() ? "offline" : stillEligible(t.player);
				if (problem != null) {
					it.remove();
					if (!problem.equals("offline")) {
						t.player.sendMessage("<col=990000><shad=000000>[Rivals]</shad></col> You left the queue: " + problem);
						DivisionRivalsInterfaceManager.refreshIfOpen(t.player);
					}
				}
			}
			pair(entry.getKey(), q);
		}
		resolvePending();
		for (List<Ticket> q : queues.values())
			for (Ticket t : q)
				DivisionRivalsInterfaceManager.refreshQueueLine(t.player);
	}

	/** Things that drop a queued player (they were checked on joining). */
	private static String stillEligible(Player p) {
		if (p.getCombat().isDead())
			return "you died.";
		if (p.wildernessLevel > 0)
			return "you entered the Wilderness.";
		if (p.getCombat().isAttacking(16) || p.getCombat().isDefending(16))
			return "you entered combat.";
		if (p.currentDynamicMap != null || p.inDynamicMap)
			return "you entered an instance.";
		if (TournamentHandler.inBracket(p))
			return "you are in a Pit Championship.";
		// states entered after queueing that must never be carried into the snapshot (stakes / rental gear)
		if (p.getDuel().stage > 0)
			return "you started a duel.";
		if (p.pvpPresetActive)
			return "you turned on a PvP preset.";
		return null;
	}

	private static void pair(RivalsMode mode, List<Ticket> q) {
		while (q.size() >= 2) {
			Ticket bestA = null, bestB = null;
			int bestGap = Integer.MAX_VALUE;
			for (int i = 0; i < q.size(); i++)
				for (int j = i + 1; j < q.size(); j++) {
					Ticket a = q.get(i), b = q.get(j);
					int gap = Math.abs(a.player.rivalsRating - b.player.rivalsRating);
					if (gap <= Math.max(a.window(), b.window()) && gap < bestGap) {
						bestGap = gap;
						bestA = a;
						bestB = b;
					}
				}
			if (bestA == null)
				return;
			q.remove(bestA);
			q.remove(bestB);
			propose(bestA, bestB);
		}
	}

	/* ---------------- ready check ---------------- */

	private static void propose(Ticket a, Ticket b) {
		Pending m = new Pending(a, b);
		pending.add(m);
		for (Ticket t : new Ticket[]{a, b})
			prompt(t.player, m);
	}

	private static void prompt(Player p, Pending m) {
		Player opponent = m.a.player == p ? m.b.player : m.a.player;
		p.privateSound(SOUND_MATCH_FOUND);
		p.sendMessage(TAG + " <col=ffffff>Match found!</col> " + m.a.mode.label + " vs " + opponent.getName() + " ("
			+ opponent.rivalsRating + " SR). Accept within <col=ffff00>" + secondsLeft(m) + "</col>s.");
		p.dialogue(new OptionsDialogue("<col=990000>Rivals Match Found!</col> " + m.a.mode.label + " - " + secondsLeft(m) + "s",
			new Option("<col=00c000>Accept</col>", () -> accept(p)),
			new Option("<col=e00000>Decline / Leave queue</col>", () -> decline(p))));
		DivisionRivalsInterfaceManager.refreshIfOpen(p);
	}

	private static int secondsLeft(Pending m) {
		return (int) Math.max(0, (m.deadline - System.currentTimeMillis() + 999) / 1000);
	}

	static void accept(Player p) {
		p.closeDialogue();
		Pending m = pendingOf(p);
		if (m == null)
			return;
		if (m.a.player == p)
			m.acceptedA = true;
		else
			m.acceptedB = true;
		boolean both = m.acceptedA && m.acceptedB;
		p.sendMessage(TAG + " Accepted" + (both ? " - starting!" : " - waiting for your opponent..."));
		if (both)
			resolvePending();
	}

	static void decline(Player p) {
		p.closeDialogue();
		Pending m = pendingOf(p);
		if (m == null)
			return;
		cancel(m, p, "You declined the match and left the queue.");
	}

	/** Ends a pending match because {@code out} declined / timed out / became ineligible; the other goes back to the front. */
	private static void cancel(Pending m, Player out, String outMessage) {
		pending.remove(m);
		for (Ticket t : new Ticket[]{m.a, m.b}) {
			Player p = t.player;
			if (p == out) {
				if (p.isOnline()) {
					p.closeDialogue();
					p.sendMessage(TAG + " " + outMessage);
					DivisionRivalsInterfaceManager.refreshIfOpen(p);
				}
				continue;
			}
			if (!p.isOnline() || stillEligible(p) != null)
				continue;
			p.closeDialogue();
			queues.get(t.mode).add(0, t); // front of the queue, same ticket: keeps its widened search window
			p.sendMessage(TAG + " Your opponent did not accept - you are back at the front of the queue.");
			DivisionRivalsInterfaceManager.refreshIfOpen(p);
		}
	}

	private static void resolvePending() {
		for (Pending m : new ArrayList<>(pending)) {
			for (Ticket t : new Ticket[]{m.a, m.b}) {
				String problem = !t.player.isOnline() ? "offline" : stillEligible(t.player);
				if (problem != null) {
					cancel(m, t.player, "You left the queue: " + problem);
					break;
				}
			}
			if (!pending.contains(m))
				continue;
			if (m.acceptedA && m.acceptedB) {
				pending.remove(m);
				startMatch(m.a.player, m.b.player, m.a.mode);
				continue;
			}
			if (System.currentTimeMillis() >= m.deadline) {
				if (!m.acceptedA && !m.acceptedB) {
					// neither answered: both are out
					pending.remove(m);
					for (Ticket t : new Ticket[]{m.a, m.b})
						if (t.player.isOnline()) {
							t.player.closeDialogue();
							t.player.sendMessage(TAG + " The match was not accepted in time - you left the queue.");
							DivisionRivalsInterfaceManager.refreshIfOpen(t.player);
						}
				} else {
					cancel(m, m.acceptedA ? m.b.player : m.a.player, "You did not accept in time and left the queue.");
				}
				continue;
			}
			int left = secondsLeft(m);
			if ((left == 10 || left == 5) && left != m.lastAnnounced) {
				m.lastAnnounced = left;
				for (Ticket t : new Ticket[]{m.a, m.b})
					if (!(t == m.a ? m.acceptedA : m.acceptedB))
						t.player.sendMessage(TAG + " Accept your match: <col=ffff00>" + left + "</col>s left.");
			}
		}
	}

	/** Also used by the developer ::rivalsforce command (no ready check). */
	static boolean startMatch(Player a, Player b, RivalsMode mode) {
		return startMatch(a, b, mode, false);
	}

	static boolean startMatch(Player a, Player b, RivalsMode mode, boolean forced) {
		for (Player p : new Player[]{a, b}) {
			Ticket t = ticket(p);
			if (t != null)
				queues.get(t.mode).remove(t);
			Pending m = pendingOf(p);
			if (m != null)
				pending.remove(m);
		}
		RivalsArenaInstance match = new RivalsArenaInstance(a, b, mode);
		match.forced = forced;
		matches.add(match);
		if (!match.start()) {
			matches.remove(match);
			for (Player p : new Player[]{a, b})
				p.sendMessage("<col=990000><shad=000000>[Rivals]</shad></col> No arena is free right now - please queue again in a moment.");
			return false;
		}
		for (Player p : new Player[]{a, b})
			p.dialogue(new MessageDialogue("<col=660000><shad=000000>Opponent found!</shad></col><br>" + mode.label + " vs " + (p == a ? b : a).getName()));
		return true;
	}

	/** The running match a player is fighting in, or null. */
	static RivalsArenaInstance matchOf(Player p) {
		for (RivalsArenaInstance m : matches)
			if (m.involves(p))
				return m;
		return null;
	}

	static List<RivalsArenaInstance> matches() {
		return matches;
	}

	static void onMatchEnd(RivalsArenaInstance match) {
		matches.remove(match);
	}
}

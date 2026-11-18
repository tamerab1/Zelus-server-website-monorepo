package io.ruin.model.activities.fiveseals;

import com.google.gson.reflect.TypeToken;
import io.ruin.api.utils.JsonUtils;
import io.ruin.api.utils.ServerWrapper;
import io.ruin.cache.Color;
import io.ruin.cache.LocType;
import io.ruin.model.World;
import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.dialogue.OptionsDialogue;
import io.ruin.model.inter.utils.Option;
import io.ruin.model.map.dynamic.DynamicMap;
import io.ruin.model.map.object.GameObject;
import io.ruin.model.map.object.actions.ObjectAction;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.IsoFields;
import java.util.*;

/**
 * The Five Seals (Trials of Ascension): Karn's instanced 5-room minigame in region 13128 (user 2026-11-16).
 * See {@link FiveSealsRun} for the map, the seals and the rewards.
 * <ul>
 * <li>{@code ::fiveseals} - start a run (solo, or with your party if you lead one)</li>
 * <li>{@code ::fiveseals invite <name>} / {@code ::fiveseals leave} - parties of up to 5</li>
 * <li>{@code ::fiveseals top} - this week's fastest full clears (solo and group)</li>
 * </ul>
 * The run's objects re-use cache locs that have handlers elsewhere (rune rock, magic tree, prayer altar); their
 * handlers are chained: inside a run the run handles the click, everywhere else the usual handler still runs.
 */
@Slf4j
public final class FiveSeals {

	private FiveSeals() {
	}

	static final int MAX_PARTY = 5;
	/** userId -> the run they're in */
	static final Map<Integer, FiveSealsRun> RUNS = new HashMap<>();
	/** leader userId -> party (leader first) */
	private static final Map<Integer, List<Player>> PARTIES = new HashMap<>();

	interface RunAction {
		boolean handle(FiveSealsRun run, Player p, GameObject obj);
	}

	static FiveSealsRun runOf(Player p) {
		FiveSealsRun run = RUNS.get(p.getUserId());
		return run != null && !run.ended && run.map.isIn(p) ? run : null;
	}

	/** Call last in StaticInit: chains onto the handlers the skills / prayer registered first. */
	public static void register() {
		chain(FiveSealsRun.SEAL_ROCK, "mine", (run, p, obj) -> run.gather(p, obj, true));
		chain(FiveSealsRun.SEAL_TREE, "chop down", (run, p, obj) -> run.gather(p, obj, false));
		for (int id : new int[] { FiveSealsRun.CONDUIT, FiveSealsRun.CONDUIT_LIT, FiveSealsRun.NODE, FiveSealsRun.NODE_DONE,
			FiveSealsRun.SUPPLY_ALTAR })
			chain(id, 1, FiveSealsRun::conduit);
		chain(FiveSealsRun.SEAL_ALTAR, 1, (run, p, obj) -> run.altar(p));
		chain(FiveSealsRun.VAULT, 1, (run, p, obj) -> run.vault(p));
		chain(24082, 1, (run, p, obj) -> run.ladder(p));
		for (int r = 1; r <= 5; r++) {
			int id = FiveSealsRun.BARRIER[r];
			chain(id, 1, (run, p, obj) -> run.barrier(p, id));
		}
		loadBoard();
		FiveSealsLobby.register();
		FiveSealsShop.register();
		FiveSealsOrnaments.register();
		FiveSealsVault.register();
		// the Crimson Ankou (cache stage 51) fights with the Ankou's stats
		for (int[] cb : FiveSealsRun.CREATURE_BASE) {
			io.ruin.cache.NPCType ours = io.ruin.cache.NPCType.get(cb[0]), base = io.ruin.cache.NPCType.get(cb[1]);
			if (ours != null && base != null && ours.combatInfo == null)
				ours.combatInfo = base.combatInfo;
		}
		// a player saved inside a run's instance logs back into a region with no map: put them on the lobby carpet
		io.ruin.model.entity.shared.listeners.LoginListener.register(p -> {
			io.ruin.model.map.Region r = io.ruin.model.map.Region.get(p.getAbsX(), p.getAbsY());
			int x = p.getAbsX(), y = p.getAbsY();
			boolean inSealsArea = x >= 3264 && x < 3328 && y >= 4608 && y < 4672;
			FiveSealsRun.removeResources(p);
			if (r == null || r.empty || inSealsArea) {
				FiveSealsShop.removeSupplies(p); // raid restoratives handed out by a run
				p.getMovement().teleport(FiveSealsLobby.CARPET);
				p.sendMessage("You find yourself back in the Five Seals lobby.");
			}
		});
	}

	private static void chain(int locId, String option, RunAction ours) {
		LocType def = LocType.get(locId);
		int op = def == null ? -1 : def.getOption(option);
		chain(locId, op == -1 ? 1 : op, ours);
	}

	private static void chain(int locId, int op, RunAction ours) {
		LocType def = LocType.get(locId);
		ObjectAction previous = def != null && def.defaultActions != null ? def.defaultActions[op - 1] : null;
		ObjectAction.register(locId, op, (p, obj) -> {
			FiveSealsRun run = runOf(p);
			if (run != null && ours.handle(run, p, obj))
				return;
			if (previous != null)
				previous.handle(p, obj);
			else if (run != null)
				p.sendMessage("Nothing interesting happens.");
		});
	}

	/* ------------------------------------------------------------------ command */

	public static void command(Player p, String[] args) {
		String sub = args == null || args.length == 0 ? "" : args[0].toLowerCase();
		switch (sub) {
			case "invite" -> invite(p, args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : "");
			case "leave" -> leaveParty(p);
			case "top", "leaderboard" -> showBoard(p);
			default -> start(p);
		}
	}

	static List<Player> partyOf(Player p) {
		for (List<Player> party : PARTIES.values())
			if (party.contains(p))
				return party;
		return null;
	}

	/* ------------------------------------------------------------------ the raid-style move: fade to black, move, fade back */

	/** overlay 174 + script 948 (alpha over 50 client cycles), as the Colosseum / raids use */
	private static final int FADE_OVERLAY = 174, SCRIPT_FADE = 948;

	/**
	 * Fades the player's screen to black, moves them to {@code to} in the dark, fades back in; {@code arrived} runs once
	 * the screen is clear (the run's HUD shares the overlay slot, so it's reopened there). The player is locked meanwhile.
	 */
	static void fadeMove(Player p, io.ruin.model.map.Position to, Runnable moved, Runnable arrived) {
		p.lock();
		p.getPacketSender().sendInterface(FADE_OVERLAY, io.ruin.model.inter.ToplevelComponent.OVERLAY);
		p.getPacketSender().sendClientScript(SCRIPT_FADE, "iiiii", 0, 255, 0, 0, 50);
		World.startEvent(e -> {
			e.delay(2);
			if (!p.isOnline()) {
				p.unlock();
				return;
			}
			p.getMovement().teleport(to);
			if (moved != null)
				moved.run();
			e.delay(1);
			p.getPacketSender().sendClientScript(SCRIPT_FADE, "iiiii", 0, 0, 0, 255, 50);
			e.delay(2);
			if (p.isOnline() && p.isVisibleInterface(FADE_OVERLAY))
				p.closeInterface(io.ruin.model.inter.ToplevelComponent.OVERLAY);
			p.unlock();
			if (arrived != null && p.isOnline())
				arrived.run();
		});
	}

	/** the Party Board's Create Party: a party of one, led by the player */
	static void createParty(Player p) {
		if (partyOf(p) != null) {
			p.sendMessage("You're already in a Five Seals party.");
			return;
		}
		PARTIES.put(p.getUserId(), new ArrayList<>(List.of(p)));
		p.sendMessage(Color.PURPLE.wrap("You create a Five Seals party. Invite up to " + (MAX_PARTY - 1) + " more players."));
	}

	static void invite(Player leader, String name) {
		Player target = World.getPlayer(name);
		if (target == null || target == leader) {
			leader.sendMessage("No player called '" + name + "' is online.");
			return;
		}
		List<Player> party = partyOf(leader);
		if (party != null && party.get(0) != leader) {
			leader.sendMessage("Only your party's leader can invite players.");
			return;
		}
		if (party != null && party.size() >= MAX_PARTY) {
			leader.sendMessage("A Five Seals party holds at most " + MAX_PARTY + " players.");
			return;
		}
		if (partyOf(target) != null) {
			leader.sendMessage(target.getName() + " is already in a Five Seals party.");
			return;
		}
		leader.sendMessage("You invite " + target.getName() + " to your Five Seals party.");
		target.dialogue(new OptionsDialogue("Join " + leader.getName() + "'s Five Seals party?",
			new Option("Yes, join the party.", () -> {
				List<Player> pt = PARTIES.computeIfAbsent(leader.getUserId(), k -> new ArrayList<>(List.of(leader)));
				pt.removeIf(m -> !m.isOnline());
				if (pt.size() >= MAX_PARTY || pt.contains(target) || partyOf(target) != null) {
					target.sendMessage("You couldn't join that party.");
					return;
				}
				pt.add(target);
				for (Player m : pt)
					m.sendMessage(Color.PURPLE.wrap(target.getName() + " joined the Five Seals party (" + pt.size() + "/" + MAX_PARTY + ")."));
				FiveSealsLobby.refreshParty(leader);
			}),
			new Option("No thanks.")));
	}

	static void leaveParty(Player p) {
		List<Player> party = partyOf(p);
		if (party == null) {
			p.sendMessage("You aren't in a Five Seals party.");
			return;
		}
		if (party.get(0) == p) {
			for (Player m : party)
				m.sendMessage(Color.PURPLE.wrap("The Five Seals party was disbanded."));
			PARTIES.values().remove(party);
			return;
		}
		party.remove(p);
		for (Player m : party)
			m.sendMessage(Color.PURPLE.wrap(p.getName() + " left the Five Seals party."));
		p.sendMessage("You leave the Five Seals party.");
	}

	private static void start(Player p) {
		List<Player> party = partyOf(p);
		if (party != null && party.get(0) != p) {
			p.sendMessage("Your party's leader starts the run.");
			return;
		}
		List<Player> runners = new ArrayList<>();
		for (Player m : party == null ? List.of(p) : party)
			if (m.isOnline())
				runners.add(m);
		startRun(p, runners);
	}

	/** the Minigame Manager's Start-Solo: a solo run, whatever party the player is in */
	static void startSolo(Player p) {
		startRun(p, List.of(p));
	}

	/** the Party Board's Start: the leader and every member standing on the gold carpet */
	static void startFromBoard(Player p) {
		List<Player> party = partyOf(p);
		if (party == null) {
			startSolo(p);
			return;
		}
		if (party.get(0) != p) {
			p.sendMessage("Only your party's leader can start the run.");
			return;
		}
		List<Player> runners = new ArrayList<>();
		runners.add(p);
		for (Player m : party)
			if (m != p && m.isOnline() && FiveSealsLobby.onCarpet(m))
				runners.add(m);
		if (runners.size() < party.size())
			p.sendMessage(Color.PURPLE.wrap((party.size() - runners.size()) + " party member(s) weren't on the gold carpet and stay behind."));
		startRun(p, runners);
	}

	/** the leader kicks the member in board slot {@code slot} (1-4) */
	static void kick(Player leader, int slot) {
		List<Player> party = partyOf(leader);
		if (party == null || party.get(0) != leader || slot >= party.size())
			return;
		Player out = party.remove(slot);
		out.sendMessage(Color.RED.wrap("You were removed from " + leader.getName() + "'s Five Seals party."));
		for (Player m : party)
			m.sendMessage(Color.PURPLE.wrap(out.getName() + " was removed from the party."));
		FiveSealsLobby.refreshParty(out);
	}

	static Board board() {
		rollWeek();
		return board;
	}

	private static void startRun(Player p, List<Player> runners) {
		for (Player m : runners) {
			String why = cannotEnter(m);
			if (why != null) {
				for (Player r : runners)
					r.sendMessage(Color.RED.wrap(m.getName() + " can't enter The Five Seals right now: " + why));
				return;
			}
		}
		DynamicMap map;
		try {
			map = new DynamicMap().build(FiveSealsRun.REGION, 1);
			// the whole arena is multi-combat: in the copied single-way area only ONE creature could fight a player at a
			// time (NPCCombat.multiCheck), so waves stood idle and rooms went easy (user 2026-11-16)
			for (io.ruin.model.map.Region region : map.getRegions())
				if (region != null)
					for (int x = 0; x < 64; x++)
						for (int y = 0; y < 64; y++)
							for (int z = 0; z <= 1; z++)
								region.getTile(region.baseX + x, region.baseY + y, z, true).multi = true;
		} catch (Exception e) {
			log.error("Unable to build a Five Seals instance", e);
			p.sendMessage("The seals refuse to open right now. Try again in a moment.");
			return;
		}
		FiveSealsRun run = new FiveSealsRun(map, runners);
		for (Player m : runners)
			RUNS.put(m.getUserId(), run);
		run.start();
	}

	private static String cannotEnter(Player p) {
		if (RUNS.containsKey(p.getUserId()) && RUNS.get(p.getUserId()) != null && !RUNS.get(p.getUserId()).ended)
			return "already in a run.";
		if (p.getCombat().isDead())
			return "dead.";
		if (p.wildernessLevel > 0)
			return "in the Wilderness.";
		if (p.inDynamicMap || p.boat != null)
			return "in an instance.";
		// beta audit 2026-11-18: ::fiveseals (and a leader's start) is a teleport -- never out of a fight, a PvP zone or a
		// Division Rivals match (its kit is phantom gear restored from a snapshot afterwards)
		if (p.pvpAttackZone)
			return "in a PvP area.";
		if (p.getCombat().isDefending(10) || p.getCombat().isAttacking(10))
			return "in combat.";
		if (io.ruin.model.content.rivals.RivalsSnapshot.active(p))
			return "in a Division Rivals match.";
		if (p.isLocked())
			return "busy.";
		return null;
	}

	/* ------------------------------------------------------------------ the weekly leaderboard */

	static final class Entry {
		String names;
		int ticks;

		Entry(String names, int ticks) {
			this.names = names;
			this.ticks = ticks;
		}
	}

	static final class Board {
		String week = "";
		List<Entry> solo = new ArrayList<>(), group = new ArrayList<>();
	}

	private static Board board = new Board();

	private static File boardFile() {
		return new File(ServerWrapper.dataFolder, "fiveseals_leaderboard.json");
	}

	private static String thisWeek() {
		LocalDate d = LocalDate.now(ZoneOffset.UTC);
		return d.get(IsoFields.WEEK_BASED_YEAR) + "-W" + d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
	}

	private static void loadBoard() {
		File f = boardFile();
		if (!f.exists())
			return;
		try (FileReader r = new FileReader(f)) {
			Board b = JsonUtils.GSON.fromJson(r, new TypeToken<Board>() {}.getType());
			if (b != null)
				board = b;
		} catch (Exception e) {
			log.error("Unable to load fiveseals_leaderboard.json", e);
		}
	}

	private static void rollWeek() {
		String week = thisWeek();
		if (!week.equals(board.week)) {
			board = new Board();
			board.week = week;
		}
	}

	static void recordTime(List<Player> party, boolean group, int ticks) {
		rollWeek();
		StringBuilder names = new StringBuilder();
		for (Player p : party)
			names.append(names.length() == 0 ? "" : ", ").append(p.getName());
		List<Entry> list = group ? board.group : board.solo;
		list.add(new Entry(names.toString(), ticks));
		list.sort(Comparator.comparingInt(e -> e.ticks));
		while (list.size() > 10)
			list.remove(list.size() - 1);
		try (FileWriter w = new FileWriter(boardFile())) {
			JsonUtils.GSON.toJson(board, w);
		} catch (Exception e) {
			log.error("Unable to save fiveseals_leaderboard.json", e);
		}
		if (!list.isEmpty() && list.get(0).ticks == ticks)
			io.ruin.utility.Broadcast.WORLD.sendNews(Color.PURPLE.wrap("[The Five Seals] " + names + " set this week's fastest " + (group ? "group" : "solo")
				+ " clear: " + time(ticks) + "!"));
	}

	private static void showBoard(Player p) {
		rollWeek();
		p.sendMessage(Color.PURPLE.wrap("The Five Seals - fastest full clears, week " + board.week));
		for (int g = 0; g < 2; g++) {
			List<Entry> list = g == 0 ? board.solo : board.group;
			p.sendMessage((g == 0 ? "Solo" : "Group") + ":" + (list.isEmpty() ? " no clears yet." : ""));
			for (int i = 0; i < Math.min(5, list.size()); i++)
				p.sendMessage("  " + (i + 1) + ". " + time(list.get(i).ticks) + " - " + list.get(i).names);
		}
	}

	static String time(int ticks) {
		int s = (int) Math.round(ticks * 0.6);
		return String.format("%02d:%02d", s / 60, s % 60);
	}
}

package io.ruin.model.activities.tournament;

import com.google.gson.reflect.TypeToken;
import io.ruin.api.utils.JsonUtils;
import io.ruin.api.utils.ServerWrapper;
import io.ruin.cache.Icon;
import io.ruin.cache.ItemID;
import io.ruin.model.World;
import io.ruin.model.activities.duelarena.DuelRule;
import io.ruin.model.inter.dialogue.MessageDialogue;
import io.ruin.model.inter.dialogue.OptionsDialogue;
import io.ruin.model.inter.utils.Option;
import io.ruin.model.item.Item;
import io.ruin.model.item.ItemContainer;
import io.ruin.model.item.containers.Equipment;
import io.ruin.model.entity.player.Player;
import io.ruin.model.entity.shared.listeners.LoginListener;
import io.ruin.model.entity.shared.listeners.LogoutListener;
import io.ruin.model.map.Bounds;
import io.ruin.model.map.MapListener;
import io.ruin.model.map.Position;
import io.ruin.model.map.object.GameObject;
import io.ruin.model.map.object.actions.ObjectAction;
import io.ruin.model.stat.Stat;
import io.ruin.model.stat.StatType;
import io.ruin.model.skills.magic.spells.modern.ModernTeleport;
import io.ruin.utility.Broadcast;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * "The Pit Championship" - scheduled single-elimination tournaments fought in the pit at home.
 * <p>
 * A tournament starts every {@link #INTERVAL_MINUTES} minutes, cycling through the {@link TournamentFormat}s.
 * Registration opens {@link #REGISTRATION_MINUTES} minutes before the start (up to {@link #bracketSize} players);
 * registered players must be in the pit lobby when it starts. Combat stats are set to the format's levels for the
 * duration and restored afterwards (also kept on the player save, so a crash or logout can't keep them). Rounds are
 * fought one match at a time in the pit; an odd player out gets a bye. Rewards are paid out automatically, and
 * finished tournaments are kept in the Hall of Fame. The hub interface is {@link TournamentHub}.
 */
public class TournamentHandler {

	static final String EVENT_NAME = "The Pit Championship";

	public static final int BRACKET_SIZE = 16, BOXING_BRACKET_SIZE = 8;
	/** A bracket that didn't fill needs at least half its size to start: 16 -> 8, 8 -> 4. */
	public static int minPlayers(TournamentFormat format) {
		return bracketSize(format) / 2;
	}
	/** Minutes between scheduled starts, and how long registration is open before each start. */
	public static final int INTERVAL_MINUTES = 30, REGISTRATION_MINUTES = 10;
	private static final long INTERVAL_MS = INTERVAL_MINUTES * 60_000L, REGISTRATION_MS = REGISTRATION_MINUTES * 60_000L;
	/** Ticks between one match ending and the next starting. */
	private static final int MATCH_GAP_TICKS = 5;

	public enum State {SCHEDULED, OPEN, RUNNING}

	public static final class Reward {
		public final String place;
		public final int points;

		Reward(String place, int points) {
			this.place = place;
			this.points = points;
		}
	}

	/**
	 * Tournament Points only: no coins, blood money or items. The champion also unlocks the "Champion" title
	 * (from tournamentWins).
	 */
	public static final Reward CHAMPION = new Reward("1st Place", 3);
	public static final Reward RUNNER_UP = new Reward("2nd Place", 2);
	public static final Reward SEMIFINALIST = new Reward("3rd / 4th", 1);
	public static final Reward PARTICIPANT = new Reward("Other entrants", 0);

	/** Anti-alt: total playtime needed to register (playTime counts game ticks of 0.6s). */
	private static final long MIN_PLAYTIME_TICKS = 30 * 100;
	/** Level-up fireworks, played on the champion. */
	private static final int CHAMPION_FIREWORKS_GFX = 199;

	/**
	 * Staging area between the blue barrier (registration gate) and the red
	 * barrier (the actual pit edge, see ARENA_BOUNDS below). An octagon
	 * (corners cut) bounded by x:[3073,3088], y:[3457,3472]. The four
	 * bounding-box corner tiles (e.g. 3073,3472) fall outside the actual
	 * walked area since the corners are clipped, but that's harmless for a
	 * bounds check - no player can ever stand there.
	 */
	public static final Bounds WAITING_BOUNDS = new Bounds(3073, 3457, 3088, 3472, -1);
	/**
	 * Where queued/waiting/eliminated players stand: the north strip of the staging octagon, between the
	 * fight floor (ARENA_BOUNDS ends at y 3470) and the octagon's edge (y 3472), near the board. The
	 * octagon's centre (3080, 3465) is ON the fight floor, since the two bounds are nested.
	 */
	public static final Position SIGNUP_POSITION = new Position(3080, 3471, 0);
	public static final Position WAITING_SPOT = SIGNUP_POSITION;

	/** The skull-and-crossbones fight floor, inside the red barrier. */
	public static final Bounds ARENA_BOUNDS = new Bounds(3075, 3459, 3086, 3470, -1);
	/** Two interior spots on opposite corners of the pit, inset from the walls. */
	public static final Position ARENA_SPAWN_A = new Position(3077, 3461, 0);
	public static final Position ARENA_SPAWN_B = new Position(3084, 3468, 0);

	private static final int MAX_HALL_OF_FAME = 25;

	/** Duel rules applied to Boxing players for the whole tournament (every existing rule check enforces them). */
	private static final EnumSet<DuelRule> BOXING_RULES = EnumSet.of(DuelRule.NO_FOOD, DuelRule.NO_DRINKS,
		DuelRule.NO_MAGIC, DuelRule.NO_RANGED, DuelRule.NO_PRAYER, DuelRule.NO_SPECIALS, DuelRule.NO_WEAPON_SWITCH);

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
	/** "Champion's Reward Trophy" (patched from the base-game Clan Cup Trophy): opens the Hall of Fame. */
	private static final int TROPHY_OBJECT_ID = 7127;

	private static State state = State.SCHEDULED;
	private static long nextStartAt;
	private static int formatIndex;
	/** Countdown reminders to registered players (seconds before the start), largest first. */
	private static final int[] REMINDERS = {300, 60, 30, 10};
	private static int nextReminder;

	private static final List<Player> queue = new ArrayList<>();
	/** Running tournament: players still to fight this round, and this round's winners (plus byes). */
	private static List<Player> pending = new ArrayList<>(), advanced = new ArrayList<>();
	private static int round;
	private static TournamentMatch activeMatch;
	private static TournamentRecord current;

	private static final LinkedList<TournamentRecord> hallOfFame = new LinkedList<>();

	public static void register() {
		MapListener.registerBounds(ARENA_BOUNDS);
		loadHallOfFame();

		for (int gateId : GATE_OBJECT_IDS)
			ObjectAction.register(gateId, "Pass", TournamentHandler::passGate);
		ObjectAction.register(BOARD_OBJECT_ID, "View", (player, obj) -> ChampionLadder.open(player));
		ObjectAction.register(BOARD_OBJECT_ID, 2, (player, obj) -> TournamentShop.open(player)); // "Rewards"
		ObjectAction.register(TROPHY_OBJECT_ID, "Claim", (player, obj) -> TournamentHub.open(player, TournamentHub.HALL_OF_FAME));
		TournamentHub.register();
		ChampionLadder.register();
		TournamentShop.register();
		BoxingMysteryChest.register();
		DuelRule.externalRules = (player, rule) -> format().boxing && inBracket(player) && BOXING_RULES.contains(rule);

		// Stats changed by a tournament the player never finished (crash, forced logout) are put back on login.
		LoginListener.register(player -> {
			if (player.pitSavedStats != null && !inBracket(player))
				restoreStats(player);
		});

		formatIndex = (int) ((System.currentTimeMillis() / INTERVAL_MS) % TournamentFormat.VALUES.length);
		nextStartAt = nextSlot();
		World.startEvent(e -> {
			while (true) {
				e.delay(1);
				try {
					tick();
				} catch (Exception ex) {
					ServerWrapper.logError("Pit Championship tick failed", ex);
				}
			}
		});
	}

	/* ---------------- schedule ---------------- */

	private static void tick() {
		long now = System.currentTimeMillis();
		if (state == State.SCHEDULED && now >= nextStartAt - REGISTRATION_MS) {
			openRegistration();
		} else if (state == State.OPEN) {
			long left = nextStartAt - now;
			if (left <= 0) {
				begin();
			} else if (nextReminder < REMINDERS.length && left <= REMINDERS[nextReminder] * 1000L) {
				// skip reminders that were already due when registration opened (e.g. a forced 2-minute start)
				while (nextReminder + 1 < REMINDERS.length && left <= REMINDERS[nextReminder + 1] * 1000L)
					nextReminder++;
				remindQueue(REMINDERS[nextReminder++]);
			}
		}
	}

	/** The next slot on the interval grid that leaves the full registration window. */
	private static long nextSlot() {
		long now = System.currentTimeMillis();
		long slot = (now / INTERVAL_MS + 1) * INTERVAL_MS;
		while (slot - now < REGISTRATION_MS)
			slot += INTERVAL_MS;
		return slot;
	}

	private static void openRegistration() {
		state = State.OPEN;
		nextReminder = 0;
		long minutes = Math.max(1, Math.round((nextStartAt - System.currentTimeMillis()) / 60_000D));
		Broadcast.GLOBAL.sendNews(Icon.PIT_TROPHY, EVENT_NAME, "Registration is open for " + EVENT_NAME + " ("
			+ format().name + ")! It starts in " + minutes + " minute" + (minutes == 1 ? "" : "s") + ". Type ::pit to join.");
		TournamentHub.refreshViewers();
	}

	private static void scheduleNext() {
		state = State.SCHEDULED;
		formatIndex = (formatIndex + 1) % TournamentFormat.VALUES.length;
		nextStartAt = nextSlot();
		TournamentHub.refreshViewers();
	}

	private static void remindQueue(int seconds) {
		String when = seconds >= 60 ? (seconds / 60) + " minute" + (seconds >= 120 ? "s" : "") : seconds + " seconds";
		for (Player p : queue) {
			if (!p.isOnline())
				continue;
			p.sendMessage(notice("The Championship starts in", when + "!"));
			if (!WAITING_BOUNDS.inBounds(p))
				p.sendMessage(warning("You're not in the pit lobby! Be inside when it starts (::pit > Enter Lobby)."));
			String storage = storageProblem(p, format());
			if (storage != null)
				p.sendMessage(warning(storage));
		}
	}

	/** High-contrast chat line (shadowed): red "[The Pit]" tag, white text, green highlight. */
	static String notice(String text, String highlight) {
		return "<shad=000000><col=ff0000>[The Pit]</col> <col=ffffff>" + text + "</col>"
			+ (highlight == null ? "" : " <col=00ff00>" + highlight + "</col>") + "</shad>";
	}

	/** Shadowed red warning line with the "[The Pit]" tag. */
	static String warning(String text) {
		return "<shad=000000><col=ff0000>[The Pit] " + text + "</col></shad>";
	}

	/* ---------------- registration ---------------- */

	public static void join(Player player) {
		if (state == State.RUNNING) {
			player.sendMessage(EVENT_NAME + " is already in progress. Please wait for the next one.");
			return;
		}
		if (state != State.OPEN) {
			player.sendMessage("Registration isn't open yet. It opens " + REGISTRATION_MINUTES + " minutes before the start.");
			return;
		}
		if (isQueued(player)) {
			player.sendMessage("You are already registered for " + EVENT_NAME + ".");
			return;
		}
		if (player.playTime < MIN_PLAYTIME_TICKS) {
			player.sendMessage("<col=ff0000>You need at least 30 minutes of total playtime on Zelus to enter tournaments.</col>");
			return;
		}
		queue.removeIf(p -> p.getName().equalsIgnoreCase(player.getName())); // stale entry from before a relog
		if (queue.size() >= bracketSize()) {
			player.sendMessage(EVENT_NAME + " is full (" + bracketSize() + " players).");
			return;
		}
		TournamentFormat format = format();
		if (format.boxing && findGloves(player) == null) {
			player.dialogue(
				new MessageDialogue("Boxing is fought in boxing gloves only. Pick your corner and take a free pair of gloves."),
				new OptionsDialogue("Pick your corner",
					new Option("Red corner (red gloves)", () -> takeGloves(player, TournamentFormat.RED_GLOVES)),
					new Option("Blue corner (blue gloves)", () -> takeGloves(player, TournamentFormat.BLUE_GLOVES)),
					new Option("Cancel", player::closeDialogue)));
			return;
		}
		queue.add(player);
		player.sendMessage("You have registered for " + EVENT_NAME + " (" + format.name + "). Be in the pit lobby when it starts.");
		String storage = storageProblem(player, format);
		if (storage != null) {
			player.dialogue(new MessageDialogue("You're registered, but: " + storage));
		} else if (format.boxing) {
			player.dialogue(new MessageDialogue("You're registered for Boxing. When it starts you fight in your best boxing gloves: "
				+ "other worn gear is taken off. Prayer, magic, food and drinks are off."));
		} else if (!format.restrictedItems(player, null).isEmpty()) {
			player.dialogue(new MessageDialogue("You're registered! Some of your worn gear is above this tournament's level caps ("
				+ format.caps + "). It will be taken off automatically when the tournament starts."));
		}
		Broadcast.GLOBAL.sendNews(Icon.PIT_TROPHY, EVENT_NAME,
			player.getName() + " has registered for " + EVENT_NAME + "! (" + queue.size() + "/" + bracketSize() + ")");
		TournamentHub.refreshViewers();
	}

	public static void leave(Player player) {
		if (!queue.remove(player))
			return;
		player.sendMessage("You have left the " + EVENT_NAME + " queue.");
		TournamentHub.refreshViewers();
	}

	private static void takeGloves(Player player, int gloves) {
		if (!player.getInventory().hasRoomFor(gloves)) {
			player.dialogue(new MessageDialogue("You need a free inventory slot for your gloves."));
			return;
		}
		player.getInventory().add(gloves, 1);
		player.sendMessage("You take a pair of " + (gloves == TournamentFormat.RED_GLOVES ? "red" : "blue") + " boxing gloves.");
		join(player);
	}

	/** The best boxing gloves the player is wearing or carrying, or null. */
	private static Item findGloves(Player player) {
		Item best = null;
		for (ItemContainer container : new ItemContainer[]{player.getEquipment(), player.getInventory()}) {
			for (Item item : container.getItems()) {
				if (item != null && TournamentFormat.gloveTier(item.getId()) >= 0
					&& (best == null || TournamentFormat.gloveTier(item.getId()) > TournamentFormat.gloveTier(best.getId())))
					best = item;
			}
		}
		return best;
	}

	/**
	 * Gets a player ready for the format at the start: boxing equips the best gloves first; then everything the
	 * format doesn't allow is moved to the bank. Returns why the player can't take part, or null.
	 */
	private static String prepareGear(Player player, TournamentFormat format) {
		Item keep = null;
		if (format.boxing) {
			keep = findGloves(player);
			if (keep == null)
				return "You had no boxing gloves when Boxing started, so you lost your place.";
			if (player.getEquipment().get(Equipment.SLOT_WEAPON) != keep) {
				player.getEquipment().equip(keep);
				keep = player.getEquipment().get(Equipment.SLOT_WEAPON);
				if (keep == null || TournamentFormat.gloveTier(keep.getId()) < 0)
					return "Your boxing gloves couldn't be equipped, so you lost your place.";
			}
		}
		List<Item> restricted = format.restrictedItems(player, keep);
		if (restricted.isEmpty())
			return null;
		int toInventory = 0, toBank = 0;
		for (Item item : restricted) {
			if (player.getInventory().freeSlot() != -1 && player.getEquipment().unequip(item)) {
				toInventory++;
				continue;
			}
			int amount = item.getAmount();
			if (player.getBank().deposit(item, amount, false, true) >= amount) {
				toBank++;
				continue;
			}
			return "Your inventory and bank were both full, so your " + item.getDef().name + " couldn't be taken off and you lost your place.";
		}
		player.sendMessage(notice("Gear " + format.name + " doesn't allow was taken off:", toInventory + " to your inventory"
			+ (toBank > 0 ? ", " + toBank + " to your bank" : "") + "."));
		return null;
	}

	/** Why the gear the format takes off wouldn't fit anywhere (inventory, then bank), or null. */
	private static String storageProblem(Player player, TournamentFormat format) {
		int worn = format.restrictedItems(player, format.boxing ? findGloves(player) : null).size();
		int room = player.getInventory().getFreeSlots() + player.getBank().getFreeSlots();
		if (worn <= room)
			return null;
		return "your inventory and bank are too full to take off the " + worn + " worn item" + (worn == 1 ? "" : "s") + " "
			+ format.name + " doesn't allow. Make " + (worn - room) + " free slot" + (worn - room == 1 ? "" : "s") + " before it starts.";
	}

	/** Equip guard for bracket players: only what the format allows can be put on mid-tournament. */
	public static boolean mayEquip(Player player, io.ruin.cache.ObjType def) {
		if (state != State.RUNNING || !inBracket(player) || format().allowsEquip(def))
			return true;
		player.sendMessage("You can't wear that in " + format().name + ".");
		return false;
	}

	/** Teleports a registered (or bracket) player into the pit lobby. */
	public static void teleportToLobby(Player player) {
		if (!isQueued(player) && !inBracket(player)) {
			player.sendMessage("Register first: the pit lobby is for registered players only.");
			return;
		}
		if (inLobby(player)) {
			player.sendMessage("You are already in the pit lobby.");
			return;
		}
		ModernTeleport.teleport(player, SIGNUP_POSITION);
	}

	/**
	 * The pit gate, both ways. Leaving is always allowed (registration is kept; be back inside when it starts),
	 * except for players in a running bracket. Entering needs a registration (the same queue the board and hub
	 * use) or a bracket place. The player is stepped through to the tile mirrored across the barrier.
	 */
	private static void passGate(Player player, GameObject gate) {
		boolean inside = WAITING_BOUNDS.inBounds(player);
		if (inside) {
			if (inBracket(player)) {
				player.sendMessage("You can't leave " + EVENT_NAME + " once it has started.");
				return;
			}
		} else if (!isQueued(player) && !inBracket(player)) {
			player.sendMessage("You must register for " + EVENT_NAME + " at the Pit Championship Board first.");
			return;
		}
		Position from = player.getPosition(), at = gate.getPosition();
		int dx = Integer.signum(at.getX() - from.getX()), dy = Integer.signum(at.getY() - from.getY());
		Position to = new Position(at.getX() + dx, at.getY() + dy, at.getZ());
		if (WAITING_BOUNDS.inBounds(to) == inside) {
			// mirroring didn't cross the barrier (odd approach angle): step away from / towards the octagon's centre
			int cx = Integer.signum(at.getX() - 3080), cy = Integer.signum(at.getY() - 3465);
			to = inside ? new Position(at.getX() + cx, at.getY() + cy, at.getZ()) : SIGNUP_POSITION;
		}
		player.getMovement().teleport(to);
		if (inside && isQueued(player))
			player.sendMessage("You left the pit lobby. You're still registered: be back inside when " + EVENT_NAME + " starts.");
	}

	/**
	 * True when an attack would cross the running Pit match: someone outside it hitting a fighter, or a fighter hitting
	 * anyone but their opponent. Checked target-side in PlayerCombat.canAttack (melee, ranged, spells and every AoE
	 * target), on top of the fighters' own attack listener.
	 */
	public static boolean blocksAttack(Player attacker, Player target) {
		TournamentMatch match = activeMatch;
		if (match == null || attacker == null || target == null)
			return false;
		return match.involves(attacker) != match.involves(target);
	}

	/** In the staging octagon but not on the fight floor. */
	public static boolean inLobby(Player player) {
		return WAITING_BOUNDS.inBounds(player) && !ARENA_BOUNDS.inBounds(player);
	}

	/* ---------------- tournament ---------------- */

	private static void begin() {
		TournamentFormat format = format();
		List<Player> entrants = new ArrayList<>();
		for (Player p : queue) {
			if (!p.isOnline())
				continue;
			String problem = !WAITING_BOUNDS.inBounds(p) ? "You weren't in the pit lobby when " + EVENT_NAME + " started, so you lost your place."
				: prepareGear(p, format);
			if (problem != null) {
				p.sendMessage(warning(problem));
				continue;
			}
			entrants.add(p);
		}
		queue.clear();

		int minPlayers = minPlayers(format);
		if (entrants.size() < minPlayers) {
			for (Player p : entrants) {
				p.sendMessage(EVENT_NAME + " was cancelled: not enough players.");
			}
			Broadcast.GLOBAL.sendNews(Icon.PIT_TROPHY, EVENT_NAME,
				EVENT_NAME + " (" + format.name + ") was cancelled: at least " + minPlayers + " of " + bracketSize(format)
					+ " players are needed (only " + entrants.size() + " were ready).");
			scheduleNext();
			return;
		}

		state = State.RUNNING;
		Collections.shuffle(entrants);
		pending = entrants;
		advanced = new ArrayList<>();
		round = 1;
		current = new TournamentRecord();
		current.format = format.name;
		current.entrants = entrants.size();
		current.rounds.add(new ArrayList<>());

		for (Player p : entrants) {
			equalizeStats(p, format);
			lockPlayer(p);
			if (format.boxing) {
				p.getPrayer().deactivateAll();
				p.allowPrayerListener = pl -> {
					pl.sendMessage("Prayer is turned off in Boxing.");
					return false;
				};
			}
			p.logoutListener = new LogoutListener().onLogout(TournamentHandler::forfeit);
		}

		Broadcast.GLOBAL.sendNews(Icon.PIT_TROPHY, EVENT_NAME, EVENT_NAME + " (" + format.name + ") has begun with "
			+ entrants.size() + " players: " + entrants.stream().map(Player::getName).collect(Collectors.joining(", ")) + "!");
		runNextMatch();
	}

	private static void runNextMatch() {
		// a player who dropped without a clean logout (no forfeit fired) would stall a match for its full 5 minutes:
		// they're out of the bracket (their stats are restored by the login check)
		pending.removeIf(p -> {
			if (p.isOnline())
				return false;
			currentRound().add(p.getName() + " (disconnected)");
			return true;
		});
		while (true) {
			if (pending.size() >= 2) {
				Player p1 = pending.remove(0);
				Player p2 = pending.remove(0);
				for (Player waiting : waitingPlayers())
					waiting.getMovement().teleport(WAITING_SPOT);
				activeMatch = new TournamentMatch(p1, p2);
				activeMatch.start();
				for (Player p : waitingPlayers())
					p.sendMessage("Round " + round + ": " + p1.getName() + " vs " + p2.getName() + " is underway.");
				TournamentHub.refreshViewers();
				return;
			}
			if (pending.size() == 1) {
				Player bye = pending.remove(0);
				advanced.add(bye);
				currentRound().add(bye.getName() + " (bye)");
				bye.sendMessage("You have a bye in round " + round + " and advance automatically.");
				continue;
			}
			if (advanced.size() <= 1) {
				if (advanced.isEmpty())
					abandon();
				else
					finish(advanced.get(0));
				return;
			}
			round++;
			pending = advanced;
			advanced = new ArrayList<>();
			current.rounds.add(new ArrayList<>());
			for (Player p : pending)
				p.sendMessage(notice("Round " + round + (pending.size() == 2 ? " (the final)" : ""), "is starting!"));
		}
	}

	static void onMatchComplete(Player winner, Player loser, boolean forfeited) {
		activeMatch = null;
		int aliveBefore = pending.size() + advanced.size() + 2;
		currentRound().add(winner.getName() + " def. " + loser.getName() + (forfeited ? " (forfeit)" : ""));
		ChampionLadder.recordMatch(winner);
		ChampionLadder.recordMatch(loser);

		Reward reward = aliveBefore == 2 ? RUNNER_UP : aliveBefore <= 4 ? SEMIFINALIST : PARTICIPANT;
		if (reward == RUNNER_UP)
			current.runnerUp = loser.getName();
		else if (reward == SEMIFINALIST)
			current.semifinalists.add(loser.getName());
		eliminate(loser, forfeited ? null : reward);

		advanced.add(winner);
		lockPlayer(winner);
		World.startEvent(e -> {
			e.delay(MATCH_GAP_TICKS);
			runNextMatch();
		});
		TournamentHub.refreshViewers();
	}

	/** Logging out during the tournament: forfeits the current match, or withdraws from the bracket. */
	private static void forfeit(Player player) {
		if (activeMatch != null && activeMatch.involves(player)) {
			activeMatch.forfeit(player);
			return;
		}
		if (pending.remove(player) || advanced.remove(player)) {
			currentRound().add(player.getName() + " (withdrew)");
			eliminate(player, null);
			TournamentHub.refreshViewers();
		}
	}

	private static void eliminate(Player player, Reward reward) {
		restoreStats(player);
		player.allowPrayerListener = null;
		unlockPlayer(player);
		player.logoutListener = null;
		if (reward == null)
			return;
		player.getMovement().teleport(SIGNUP_POSITION);
		payReward(player, reward);
		player.sendMessage("You have been eliminated from " + EVENT_NAME + " (" + reward.place + ").");
	}

	private static void finish(Player champion) {
		champion.tournamentWins++;
		ChampionLadder.recordWin(champion);
		io.ruin.model.activities.scoreboard.Scoreboard.record(champion, io.ruin.model.activities.scoreboard.Scoreboard.Stat.TOURNAMENT_WINS, 1);
		restoreStats(champion);
		champion.allowPrayerListener = null;
		unlockPlayer(champion);
		champion.logoutListener = null;
		champion.getMovement().teleport(SIGNUP_POSITION);
		payReward(champion, CHAMPION);
		champion.sendMessage(notice("Congratulations, you won", EVENT_NAME + "!"));
		champion.graphics(CHAMPION_FIREWORKS_GFX, 124, 0);

		current.champion = champion.getName();
		current.finishedAt = System.currentTimeMillis();
		hallOfFame.addFirst(current);
		while (hallOfFame.size() > MAX_HALL_OF_FAME)
			hallOfFame.removeLast();
		saveHallOfFame();

		Broadcast.GLOBAL.sendPlain(String.format(Icon.PIT_TROPHY.tag() + " <col=ff981f><shad=0>[The Pit]</col></shad> <col=ffffff><shad=0>%s</shad></col> "
			+ "<col=ff981f>has conquered the %s Championship!</col>", champion.getName(), current.format));
		resetBracket();
		scheduleNext();
	}

	/** Everyone left the bracket (all logged out): no champion. */
	private static void abandon() {
		Broadcast.GLOBAL.sendNews(Icon.PIT_TROPHY, EVENT_NAME, EVENT_NAME + " ended without a champion.");
		resetBracket();
		scheduleNext();
	}

	private static void resetBracket() {
		pending = new ArrayList<>();
		advanced = new ArrayList<>();
		activeMatch = null;
		current = null;
		round = 0;
	}

	private static void payReward(Player player, Reward reward) {
		if (reward.points <= 0)
			return;
		player.tournamentPoints += reward.points;
		ChampionLadder.recordPoints(player, reward.points);
		player.sendMessage(notice("Reward:", reward.points + " Tournament Point" + (reward.points == 1 ? "" : "s") + "."));
	}

	private static void give(Player player, int id, int amount) {
		if (amount <= 0)
			return;
		if (player.getInventory().hasRoomFor(id, amount)) {
			player.getInventory().add(id, amount);
		} else {
			player.getBank().add(id, amount, null);
			player.sendMessage("Your inventory was full, so it was sent to your bank.");
		}
	}

	private static List<String> currentRound() {
		return current.rounds.get(current.rounds.size() - 1);
	}

	private static List<Player> waitingPlayers() {
		List<Player> list = new ArrayList<>(pending);
		list.addAll(advanced);
		return list;
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

	/* ---------------- stats ---------------- */

	private static void equalizeStats(Player player, TournamentFormat format) {
		StatType[] stats = TournamentFormat.COMBAT_STATS;
		int[][] saved = new int[stats.length][3];
		for (int i = 0; i < stats.length; i++) {
			Stat stat = player.getStats().get(stats[i]);
			saved[i][0] = stat.fixedLevel;
			saved[i][1] = stat.currentLevel;
			saved[i][2] = (int) stat.experience;
		}
		if (player.pitSavedStats == null) // never overwrite the real stats with tournament ones
			player.pitSavedStats = saved;

		for (int i = 0; i < stats.length; i++)
			player.getStats().set(stats[i], format.levels[i]);
		player.setHp(format.level(StatType.Hitpoints));
		player.getCombat().restoreSpecial(100);
	}

	private static void restoreStats(Player player) {
		int[][] saved = player.pitSavedStats;
		if (saved == null)
			return;
		StatType[] stats = TournamentFormat.COMBAT_STATS;
		for (int i = 0; i < stats.length; i++) {
			player.getStats().set(stats[i], saved[i][0], saved[i][2]);
			player.getStats().get(stats[i]).currentLevel = saved[i][1];
			player.getPacketSender().sendStat(stats[i].ordinal(), saved[i][1], saved[i][2]);
		}
		player.setHp(player.getStats().get(StatType.Hitpoints).currentLevel);
		player.pitSavedStats = null;
	}

	/* ---------------- staff controls (::pitopen / ::pitstart / ::pitcancel / ::pitformat) ---------------- */

	public static String forceOpen() {
		if (state != State.SCHEDULED)
			return "Registration is already open or a tournament is running.";
		nextStartAt = System.currentTimeMillis() + 120_000;
		openRegistration();
		return "Registration opened; " + format().name + " starts in 2 minutes.";
	}

	public static String forceStart() {
		if (state != State.OPEN)
			return "Registration isn't open (use ::pitopen first).";
		nextStartAt = System.currentTimeMillis();
		return "Starting now.";
	}

	public static String cancel() {
		if (state != State.OPEN)
			return "Only an open registration can be cancelled.";
		for (Player p : queue)
			p.sendMessage(EVENT_NAME + " was cancelled by staff.");
		queue.clear();
		formatIndex = (formatIndex + TournamentFormat.VALUES.length - 1) % TournamentFormat.VALUES.length; // keep this format next
		scheduleNext();
		return "Cancelled; next start rescheduled.";
	}

	public static String setFormat(int index) {
		if (state != State.SCHEDULED)
			return "The format can only be changed before registration opens.";
		if (index < 0 || index >= TournamentFormat.VALUES.length)
			return "Formats: 0-" + (TournamentFormat.VALUES.length - 1) + ".";
		formatIndex = index;
		TournamentHub.refreshViewers();
		return "Next format: " + format().name + ".";
	}

	/* ---------------- queries (hub) ---------------- */

	public static State state() {
		return state;
	}

	/** Most players one tournament of this format takes: Boxing 8, the rest 16. */
	public static int bracketSize(TournamentFormat format) {
		return format.boxing ? BOXING_BRACKET_SIZE : BRACKET_SIZE;
	}

	public static int bracketSize() {
		return bracketSize(format());
	}

	public static TournamentFormat format() {
		return TournamentFormat.VALUES[formatIndex];
	}

	/** Format of the k-th tournament after the next/current one (k = 0 is the next/current). */
	public static TournamentFormat upcomingFormat(int k) {
		return TournamentFormat.VALUES[(formatIndex + k) % TournamentFormat.VALUES.length];
	}

	/** Approximate start time of the k-th upcoming tournament (k = 0: the next/current one). */
	public static long upcomingStart(int k) {
		if (state == State.RUNNING)
			return k == 0 ? -1 : nextSlot() + (k - 1) * INTERVAL_MS;
		return nextStartAt + k * INTERVAL_MS;
	}

	/** Start time of the next tournament of this format (-1 = it's the one running now). */
	public static long nextStartOf(TournamentFormat format) {
		for (int k = 0; k < TournamentFormat.VALUES.length; k++)
			if (upcomingFormat(k) == format)
				return upcomingStart(k);
		return 0;
	}

	public static long millisUntilStart() {
		return nextStartAt - System.currentTimeMillis();
	}

	public static long millisUntilRegistration() {
		return nextStartAt - REGISTRATION_MS - System.currentTimeMillis();
	}

	public static int queueSize() {
		return queue.size();
	}

	public static boolean isQueued(Player player) {
		return queue.contains(player);
	}

	public static boolean inBracket(Player player) {
		return state == State.RUNNING && (pending.contains(player) || advanced.contains(player)
			|| (activeMatch != null && activeMatch.involves(player)));
	}

	public static int round() {
		return round;
	}

	public static int playersLeft() {
		return pending.size() + advanced.size() + (activeMatch != null ? 2 : 0);
	}

	public static String currentMatch() {
		return activeMatch == null ? null : activeMatch.description();
	}

	public static int entrants() {
		return current == null ? 0 : current.entrants;
	}

	public static List<TournamentRecord> hallOfFame() {
		return hallOfFame;
	}

	/* ---------------- hall of fame storage ---------------- */

	private static File hallOfFameFile() {
		return new File(ServerWrapper.dataFolder, "runtime/saves/pit_championships.json");
	}

	private static void loadHallOfFame() {
		File file = hallOfFameFile();
		if (!file.exists())
			return;
		try (FileReader reader = new FileReader(file)) {
			List<TournamentRecord> list = JsonUtils.GSON.fromJson(reader, new TypeToken<List<TournamentRecord>>() {}.getType());
			if (list != null)
				hallOfFame.addAll(list);
		} catch (Exception e) {
			ServerWrapper.logError("Unable to load the Pit Championship hall of fame", e);
		}
	}

	private static void saveHallOfFame() {
		File file = hallOfFameFile();
		file.getParentFile().mkdirs();
		try (FileWriter writer = new FileWriter(file)) {
			JsonUtils.GSON_PRETTY.toJson(hallOfFame, writer);
		} catch (Exception e) {
			ServerWrapper.logError("Unable to save the Pit Championship hall of fame", e);
		}
	}
}

package io.ruin.model.activities.raids.tfa;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import io.ruin.api.utils.ServerWrapper;
import io.ruin.model.World;
import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.dialogue.MessageDialogue;
import io.ruin.model.inter.dialogue.OptionsDialogue;
import io.ruin.model.inter.utils.Option;
import io.ruin.model.map.MapListener;
import io.ruin.model.map.Position;
import io.ruin.model.map.object.actions.ObjectAction;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The lobby's other fixtures (region 16470), from the cache's loc list:
 * <ul>
 * <li>the strategy board (TFA_BOARD_GUTHIXIAN01_PLANNING01, 63083): spectate a raid in progress;</li>
 * <li>the Alliance records (TFA_OUT_OF_RAID_SCOREBOARD, 62773): fastest clears per party size and recent clears;</li>
 * <li>the rewards chest (TFA_REWARDS_CHEST_LOBBY, 63299): unclaimed vault loot;</li>
 * <li>the bank chests (63274/63275) and the way out (TFA_LOBBY_RAID_EXIT, 63009).</li>
 * </ul>
 * OSRS spectating is a camera view from the board; here the spectator is carried into the raid hidden and
 * unhittable, follows the party from room to room, and is returned to the lobby when the raid ends.
 */
@Slf4j
public final class Raid4Lobby {

	private Raid4Lobby() {
	}

	public static final int LOC_STRATEGY_BOARD = 63083, LOC_RECORDS = 62773, LOC_REWARDS_CHEST = 63299;
	public static final int LOC_BANK_CHEST_CLOSED = 63274, LOC_BANK_CHEST_OPEN = 63275, LOC_LOBBY_EXIT = 63009;

	public static void register() {
		for (int option = 1; option <= 5; option++) {
			ObjectAction.register(LOC_STRATEGY_BOARD, option, (player, obj) -> spectateMenu(player));
			// Alliance records: the first op opens the Alliance Log (979), the others the fastest-clears scroll
			if (option == 1)
				ObjectAction.register(LOC_RECORDS, option, (player, obj) -> Raid4Stats.open(player));
			else
				ObjectAction.register(LOC_RECORDS, option, (player, obj) -> showRecords(player));
			ObjectAction.register(LOC_REWARDS_CHEST, option, (player, obj) -> claim(player));
			ObjectAction.register(LOC_BANK_CHEST_CLOSED, option, (player, obj) -> player.getBank().open());
			ObjectAction.register(LOC_BANK_CHEST_OPEN, option, (player, obj) -> player.getBank().open());
			ObjectAction.register(Raid4Ids.LOC_LOBBY_EXIT_FLOOR2, option, (player, obj) -> {
				if (Raid4.controllerOf(player) != null && Raid4.controllerOf(player).isInside(player))
					return;
				player.getMovement().teleport(World.HOME);
				player.sendMessage("You leave the Archive's lobby.");
			});
			ObjectAction.register(LOC_LOBBY_EXIT, option, (player, obj) -> {
				if (Raid4.controllerOf(player) != null && Raid4.controllerOf(player).isInside(player))
					return;
				player.getMovement().teleport(World.HOME);
				player.sendMessage("You leave the Archive's lobby.");
			});
		}
		loadRecords();
		World.startEvent(event -> {
			while (true) {
				event.delay(1);
				try {
					tickSpectators();
				} catch (Exception e) {
					log.error("Raid 4: spectator tick failed", e);
				}
			}
		});
	}

	/* Rewards chest */

	static void claimUnclaimed(Player player) {
		claim(player);
	}

	private static void claim(Player player) {
		Raid4Loot.Reward reward = Raid4.unclaimed.remove(Raid4Party.key(player));
		if (reward == null)
			player.dialogue(new MessageDialogue("The chest holds nothing for you."));
		else
			Raid4VaultRoom.claim(player, reward);
	}

	/* Spectating */

	private static final Map<Player, Raid4Controller> SPECTATORS = new HashMap<>();

	private static void spectateMenu(Player player) {
		List<Raid4Controller> raids = new ArrayList<>(Raid4.active());
		raids.removeIf(r -> r.getState() != Raid4Controller.State.ACTIVE || r.getRoom() == null);
		if (raids.isEmpty()) {
			player.dialogue(new MessageDialogue("No parties are in the Archive right now."));
			return;
		}
		List<Option> options = new ArrayList<>();
		for (int i = 0; i < Math.min(4, raids.size()); i++) {
			Raid4Controller raid = raids.get(i);
			Player leader = raid.getParty().leaderPlayer();
			String label = (leader == null ? "A party" : leader.getName()) + " (" + raid.getParty().size() + ") - "
					+ raid.getRoom().type().displayName;
			options.add(new Option(label, () -> spectate(player, raid)));
		}
		options.add(new Option("Never mind", player::closeDialogue));
		player.dialogue(new OptionsDialogue("Spectate a raid", options));
	}

	private static void spectate(Player player, Raid4Controller raid) {
		player.closeDialogue();
		if (Raid4.partyOf(player) != null && Raid4.partyOf(player).hasStarted()) {
			player.sendMessage("You can't spectate while your own party is in the Archive.");
			return;
		}
		if (raid.getState() != Raid4Controller.State.ACTIVE || raid.getRoom() == null) {
			player.sendMessage("That raid has just ended.");
			return;
		}
		SPECTATORS.put(player, raid);
		player.setHidden(true);
		player.setInvincible(true);
		player.teleportListener = p -> {
			p.sendMessage("Use the strategy board's exit (::raid4 stop) to stop spectating.");
			return false;
		};
		Position at = raid.getRoom().spectatePosition();
		player.getMovement().teleport(at);
		player.registerMapListener(new MapListener(p -> SPECTATORS.containsKey(p))
				.onExit((p, logout) -> {
					if (logout)
						p.getMovement().teleport(Raid4Ids.LOBBY_RETURN_X, Raid4Ids.LOBBY_RETURN_Y, 0);
				}));
		player.sendMessage("<col=ef1020>You're now spectating. Type ::raid4 stop to return to the lobby.</col>");
	}

	public static boolean isSpectating(Player player) {
		return SPECTATORS.containsKey(player);
	}

	public static void stopSpectating(Player player) {
		if (SPECTATORS.remove(player) == null)
			return;
		player.teleportListener = null;
		player.setHidden(false);
		player.setInvincible(false);
		player.getMovement().teleport(Raid4Ids.LOBBY_RETURN_X, Raid4Ids.LOBBY_RETURN_Y, 0);
		player.sendMessage("You stop spectating.");
	}

	/** Spectators follow their raid from room to room and go home when it ends. */
	private static void tickSpectators() {
		for (Map.Entry<Player, Raid4Controller> e : new ArrayList<>(SPECTATORS.entrySet())) {
			Player player = e.getKey();
			Raid4Controller raid = e.getValue();
			if (!player.isOnline()) {
				SPECTATORS.remove(player);
				continue;
			}
			if (raid.getState() != Raid4Controller.State.ACTIVE || raid.getRoom() == null) {
				stopSpectating(player);
				player.sendMessage("The raid you were watching has ended.");
				continue;
			}
			Raid4RoomType room = raid.getRoom().type();
			if (raid.getInstance().roomAt(player.getPosition()) != room && raid.getInstance().isBuilt(room))
				player.getMovement().teleport(raid.getRoom().spectatePosition());
		}
	}

	/* Alliance records */

	public static class Record {
		public int ticks;
		public String names;
		public String date;
	}

	public static class Records {
		public Map<Integer, Record> best = new HashMap<>();
		public List<Record> recent = new ArrayList<>();
		public int totalClears;
	}

	private static Records records = new Records();
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static File recordsFile() {
		return new File(ServerWrapper.dataFolder, "runtime/saves/raid4_records.json");
	}

	private static void loadRecords() {
		File file = recordsFile();
		if (!file.exists())
			return;
		try (FileReader reader = new FileReader(file)) {
			Records loaded = GSON.fromJson(reader, new TypeToken<Records>() {
			}.getType());
			if (loaded != null)
				records = loaded;
		} catch (Exception e) {
			log.error("Raid 4: couldn't read the Alliance records", e);
		}
	}

	private static void saveRecords() {
		File file = recordsFile();
		file.getParentFile().mkdirs();
		try (FileWriter writer = new FileWriter(file)) {
			GSON.toJson(records, writer);
		} catch (Exception e) {
			log.error("Raid 4: couldn't save the Alliance records", e);
		}
	}

	/** A party reached the vault: log the clear, and a new best time for its size. */
	public static void recordClear(Raid4Controller raid, List<Player> players) {
		Record record = new Record();
		record.ticks = (int) raid.ticks();
		StringBuilder names = new StringBuilder();
		for (Player p : players) {
			if (names.length() > 0)
				names.append(", ");
			names.append(p.getName());
		}
		record.names = names.toString();
		record.date = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
		int size = Math.max(1, players.size());
		records.totalClears++;
		records.recent.add(0, record);
		while (records.recent.size() > 10)
			records.recent.remove(records.recent.size() - 1);
		// the records are kept quietly: the live game's only line is Ocellus's "Total completion time" (ocellus_plan)
		Record best = records.best.get(size);
		if (best == null || record.ticks < best.ticks)
			records.best.put(size, record);
		saveRecords();
	}

	private static void showRecords(Player player) {
		List<String> lines = new ArrayList<>();
		lines.add("<col=880000>Fastest clears by party size</col>");
		for (int size = 1; size <= Raid4Party.MAX_SIZE; size++) {
			Record r = records.best.get(size);
			lines.add(size + (size == 1 ? " player: " : " players: ") + (r == null ? "-" : formatTicks(r.ticks) + "  " + r.names + "  (" + r.date + ")"));
		}
		lines.add("");
		lines.add("<col=880000>Recent clears</col> (" + records.totalClears + " in total)");
		for (Record r : records.recent)
			lines.add(formatTicks(r.ticks) + "  " + r.names + "  (" + r.date + ")");
		lines.add("");
		lines.add("Your completions: " + player.fracturedArchiveKills.getKills());
		player.sendScroll("Alliance records", lines.toArray(new String[0]));
	}

	static String formatTicks(int ticks) {
		long ms = ticks * 600L;
		long m = ms / 60000, s = ms / 1000 % 60, cs = ms / 10 % 100;
		return String.format("%d:%02d.%02d", m, s, cs);
	}
}

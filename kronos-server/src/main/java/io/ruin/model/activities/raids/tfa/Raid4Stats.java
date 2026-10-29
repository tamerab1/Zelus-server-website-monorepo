package io.ruin.model.activities.raids.tfa;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.ruin.api.utils.ServerWrapper;
import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.InterfaceHandler;
import io.ruin.model.inter.ToplevelComponent;
import io.ruin.model.inter.actions.SimpleAction;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Alliance Log (tfa_stats, interface 979, 2026-10-29): the Fractured Archive's stats book, filled the way the cache's
 * scripts expect it.
 * <ul>
 * <li>Sections are rows of db table 264 (enum 6066: Overall, then a row per boss); each lists rows of table 265
 * (Attempts, Completions, Completions (Practice), Deaths, Wipes, Failures, Completion Time).</li>
 * <li>Opening: script 10102 (mode 0 = all stats), 10109 selects Overall (varc 1583), 10123 builds every section's
 * table so switching sections works client-side, 10121 hides all but the selected one; then 10125 per row:
 * (section, stat, party size, current, flag, personal, global, global today, world today). Completion Time
 * (table 265 c7) is a row per party size 1..8. Times are ticks ("-" when 0 or less); counts are plain.</li>
 * </ul>
 * Personal values are kept on the player ({@code tfaStats}), global and today's in runtime/saves/raid4_stats.json. One
 * world, so World Today is Global Today. The party tab (damage done / taken, MVPs) has no data yet: mode 0 hides it.
 */
@Slf4j
public final class Raid4Stats {

	public static final int INTERFACE = 979, CLOSE = 29;
	private static final int SCRIPT_OPEN = 10102, SCRIPT_SELECT = 10109, SCRIPT_INIT_TABLE = 10123,
			SCRIPT_SHOW_TABLE = 10121, SCRIPT_ROW = 10125;
	/**
	 * Party Stats (mode 1, the vault's Merit scroll): 10131(members) builds the table (name, Damage Done, Damage Taken,
	 * Deaths, Number of Bosses as MVP, Total), 10136(slot, done, taken, deaths, mvps, name) a member, 10137(done,
	 * taken, deaths) the Total row, 10128(name) the MVP banner ("Unavailable" when empty). The times panel (10139) is
	 * left empty: its data is not decoded yet.
	 */
	private static final int SCRIPT_PARTY_TABLE = 10131, SCRIPT_PARTY_ROW = 10136, SCRIPT_PARTY_TOTAL = 10137,
			SCRIPT_PARTY_MVP = 10128;

	public static final int OVERALL = 17133, GUARDIAN = 17134, BORK = 17135, DAGONHAI = 17136, EZEKIEL = 17137,
			MOIA = 17138, OCELLUS = 17139, DREADNOTH = 17140, XARTH = 17141;
	public static final int ATTEMPTS = 17142, COMPLETIONS = 17143, PRACTICE = 17144, DEATHS = 17145, WIPES = 17146,
			FAILURES = 17147, TIME = 17148;
	private static final int[] SECTIONS = { OVERALL, GUARDIAN, EZEKIEL, BORK, MOIA, DREADNOTH, DAGONHAI, XARTH, OCELLUS };
	private static final int[] OVERALL_ROWS = { ATTEMPTS, COMPLETIONS, DEATHS, WIPES, FAILURES, TIME };
	private static final int[] BOSS_ROWS = { ATTEMPTS, COMPLETIONS, PRACTICE, DEATHS, TIME };
	private static final int SIZES = 8;

	private static class Store {
		Map<String, Integer> global = new HashMap<>();
		Map<String, Integer> today = new HashMap<>();
		String date = "";
	}

	private static Store store = new Store();
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private Raid4Stats() {
	}

	public static void register() {
		load();
		InterfaceHandler.register(INTERFACE, h -> h.actions[CLOSE] = (SimpleAction) p -> p.closeInterface(ToplevelComponent.MAINMODAL));
	}

	/** The section of a boss room (the Guardian fights in the Nexus), or -1. */
	public static int section(Raid4RoomType type) {
		return switch (type) {
			case NEXUS, NEXUS_CLEARED -> GUARDIAN;
			case EZEKIEL -> EZEKIEL;
			case BORK -> BORK;
			case MOIA -> MOIA;
			case DREADNOTH -> DREADNOTH;
			case DAGONHAI -> DAGONHAI;
			case XARTH -> XARTH;
			case OCELLUS -> OCELLUS;
			default -> -1;
		};
	}

	/* ---------------------------------------------------------------- recording */

	/** Adds to a count for each player: their own value, the global and today's. */
	public static void count(Collection<Player> players, int section, int stat) {
		if (section < 0)
			return;
		String key = section + ":" + stat;
		rollDay();
		for (Player p : players) {
			p.tfaStats.merge(key, 1, Integer::sum);
			store.global.merge(key, 1, Integer::sum);
			store.today.merge(key, 1, Integer::sum);
		}
		save();
	}

	/** A completion time for a party of {@code size}: kept where it beats the best. */
	public static void time(Collection<Player> players, int section, int size, long ticks) {
		if (section < 0 || ticks <= 0)
			return;
		String key = section + ":" + TIME + ":" + Math.max(1, Math.min(SIZES, size));
		int t = (int) Math.min(Integer.MAX_VALUE, ticks);
		rollDay();
		for (Player p : players)
			p.tfaStats.merge(key, t, Math::min);
		store.global.merge(key, t, Math::min);
		store.today.merge(key, t, Math::min);
		save();
	}

	private static void rollDay() {
		String now = LocalDate.now().toString();
		if (!now.equals(store.date)) {
			store.date = now;
			store.today.clear();
		}
	}

	/* ---------------------------------------------------------------- the log */

	public static void open(Player player) {
		rollDay();
		var ps = player.getPacketSender();
		player.openInterface(ToplevelComponent.MAINMODAL, INTERFACE);
		ps.sendIfEvents(INTERFACE, CLOSE, -1, -1, 0x3FE);
		Raid4Controller raid = Raid4.controllerOf(player);
		boolean party = raid != null && raid.isInside(player);
		ps.sendClientScript(SCRIPT_OPEN, "i", party ? 1 : 0);
		ps.sendClientScript(SCRIPT_SELECT, "i", OVERALL); // also builds Overall's table
		for (int section : SECTIONS)
			if (section != OVERALL)
				ps.sendClientScript(SCRIPT_INIT_TABLE, "i", section);
		ps.sendClientScript(SCRIPT_SHOW_TABLE, "i", OVERALL);
		for (int section : SECTIONS)
			for (int stat : section == OVERALL ? OVERALL_ROWS : BOSS_ROWS) {
				if (stat == TIME) {
					for (int size = 1; size <= SIZES; size++) {
						String key = section + ":" + TIME + ":" + size;
						row(player, section, stat, size, best(player.tfaStats, key), best(store.global, key), best(store.today, key));
					}
				} else {
					String key = section + ":" + stat;
					row(player, section, stat, 0, player.tfaStats.getOrDefault(key, 0), store.global.getOrDefault(key, 0),
							store.today.getOrDefault(key, 0));
				}
			}
		partyIfInside(player);
	}

	private static void partyIfInside(Player player) {
		Raid4Controller raid = Raid4.controllerOf(player);
		if (raid != null && raid.isInside(player))
			sendParty(player, raid);
	}

	private static int best(Map<String, Integer> map, String key) {
		return map.getOrDefault(key, -1);
	}

	private static void row(Player player, int section, int stat, int size, int personal, int global, int today) {
		player.getPacketSender().sendClientScript(SCRIPT_ROW, "iiiiiiiii", section, stat, size, -1, 0, personal, global,
						today, today);
	}

	/** The Party Stats tab: this raid's members, their damage, deaths and MVPs. */
	private static void sendParty(Player player, Raid4Controller raid) {
		var ps = player.getPacketSender();
		List<Player> members = raid.getParty().online();
		int n = Math.min(8, members.size());
		ps.sendClientScript(SCRIPT_PARTY_TABLE, "i", n);
		int done = 0, taken = 0, deaths = 0, bestMvps = 0;
		String mvp = "";
		for (int i = 0; i < n; i++) {
			Player m = members.get(i);
			Raid4Points.Entry e = raid.getPoints().of(m);
			ps.sendClientScript(SCRIPT_PARTY_ROW, "iiiiis", i, e.damageDealt, e.damageTaken, e.deaths, e.mvps, m.getName());
			done += e.damageDealt;
			taken += e.damageTaken;
			deaths += e.deaths;
			if (e.mvps > bestMvps) {
				bestMvps = e.mvps;
				mvp = m.getName();
			}
		}
		ps.sendClientScript(SCRIPT_PARTY_TOTAL, "iii", done, taken, deaths);
		ps.sendClientScript(SCRIPT_PARTY_MVP, "s", mvp);
	}

	/* ---------------------------------------------------------------- storage */

	private static File file() {
		return new File(ServerWrapper.dataFolder, "runtime/saves/raid4_stats.json");
	}

	private static void load() {
		File f = file();
		if (!f.exists())
			return;
		try (FileReader r = new FileReader(f)) {
			Store loaded = GSON.fromJson(r, Store.class);
			if (loaded != null)
				store = loaded;
		} catch (Exception e) {
			log.error("Raid 4: couldn't read the Alliance Log stats", e);
		}
	}

	private static void save() {
		File f = file();
		f.getParentFile().mkdirs();
		try (FileWriter w = new FileWriter(f)) {
			GSON.toJson(store, w);
		} catch (Exception e) {
			log.error("Raid 4: couldn't save the Alliance Log stats", e);
		}
	}

	static List<Player> online(Raid4Controller raid) {
		return raid.getParty().online();
	}
}

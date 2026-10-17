package io.ruin.model.content.fortune;

import discord.webhooks.notifications.GlobalBroadcastHook;
import io.ruin.api.utils.JsonUtils;
import io.ruin.api.utils.NumberUtils;
import io.ruin.api.utils.Random;
import io.ruin.api.utils.ServerWrapper;
import io.ruin.cache.Icon;
import io.ruin.cache.ItemID;
import io.ruin.model.World;
import io.ruin.model.entity.player.Player;
import io.ruin.model.item.actions.ItemObjectAction;
import io.ruin.model.map.Tile;
import io.ruin.model.map.object.GameObject;
import io.ruin.model.map.object.actions.ObjectAction;
import io.ruin.utility.Broadcast;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.*;

/**
 * The Statue of Fortune (replaces the camel statue). Players sacrifice coins into one blessing's pool; the pool climbs
 * three tiers, and reaching a tier starts (or upgrades) a timed blessing. Once Tier III is reached, more coins extend the
 * timer up to {@link #CAP_MS}; coins sent past the cap go into the Reserve at 50%, which seeds the next cycle's pool when
 * the blessing fades. Everything is saved to runtime/saves/statue_of_fortune.json on every change, so restarts keep it.
 * <p>
 * The statue loc follows the state: Blessed while any blessing is active, Charged while any pool is 80%+ of its next
 * tier, Dormant otherwise. All game-thread: donations come from interface/input handlers, expiry from a world event.
 */
@Slf4j
public class StatueOfFortune {
	public static final int DORMANT = 62393, CHARGED = 62394, BLESSED = 62395;
	public static final int[] LOCS = {DORMANT, CHARGED, BLESSED};
	/** South-west tile of the 3x3 statue: exactly the home knight statue (38443)'s tiles. Ambient = the nexus cyan splash. */
	static final int X = 3104, Y = 3506, SIZE = 3, AMBIENT_GFX = 4295;

	static final long MINUTE = 60_000L, BLESSING_MS = 60 * MINUTE, CAP_MS = 180 * MINUTE;
	static final long SURGE_FULL = 5_000_000L, SURGE_MIN = 1_000_000L, SURGE_BONUS_CAP = 25_000_000L, SURGE_COOLDOWN = 10 * MINUTE;
	public static final long BROADCAST_MIN = 10_000_000L;
	static final long BROADCAST_GAP = MINUTE;
	public static final String[] TIER = {"", "I", "II", "III"};
	/** Chat styling on the parchment chatbox: dark red prefix, amber names, default (dark) text for the rest. */
	static final String PREFIX = "<col=800000>[Statue of Fortune]</col> ", NAME_COL = "<col=b25900>", END = "</col>";
	/** All-time donor ranks: threshold, name, colour. */
	static final Object[][] RANKS = {{10_000_000_000L, "Fortune's Chosen", "ff9a1f"}, {1_000_000_000L, "Patron", "ffb83f"}, {100_000_000L, "Benefactor", "e6d8b8"}};

	static final class Pool {
		int tier;
		long pool, reserve, endsAt;
		int cycle;
		Map<String, Long> contrib = new HashMap<>();
	}

	static final class SaveState {
		Map<String, Pool> pools = new HashMap<>();
		Map<String, Long> allTime = new HashMap<>();
		Map<String, String> names = new HashMap<>();
	}

	static SaveState state = new SaveState();
	static GameObject statue;
	static final Map<String, Long> lastSurge = new HashMap<>(), lastBroadcast = new HashMap<>();

	// ---------------------------------------------------------------- effects (read by the rest of the server)

	public static int tier(Blessing b) {
		Pool p = state.pools.get(b.name());
		return p == null ? 0 : p.tier;
	}

	public static boolean isActive(Blessing b) {
		return tier(b) > 0;
	}

	/** 1 + bonus for multiplier blessings (1.25 / 1.5 / 2.0); 1 when inactive. */
	public static double multiplier(Blessing b) {
		return 1 + b.value(tier(b));
	}

	/** Pet odds are "1 in N": N is multiplied by this (0.95 / 0.90 / 0.85; 1 when inactive). */
	public static double petChanceMultiplier() {
		return 1 - Blessing.PET_RATES.value(tier(Blessing.PET_RATES));
	}

	/** Rolls the Extra Drop Roll blessing for one kill. */
	public static boolean rollExtraDrop() {
		int t = tier(Blessing.EXTRA_DROP_ROLL);
		return t > 0 && Random.get() < Blessing.EXTRA_DROP_ROLL.value(t);
	}

	/** Added drop rate in percent (1.5 / 3 / 5). */
	public static float dropRateBonus() {
		return (float) Blessing.DROP_RATE.value(tier(Blessing.DROP_RATE));
	}

	public static int activeCount() {
		int n = 0;
		for (Blessing b : Blessing.VALUES)
			if (isActive(b)) n++;
		return n;
	}

	// ---------------------------------------------------------------- setup

	public static void register() {
		load();
		for (int loc : LOCS) {
			ObjectAction.register(loc, 1, (p, obj) -> FortuneInterface.open(p, null, false));
			ObjectAction.register(loc, 2, (p, obj) -> FortuneInterface.open(p, null, false));
			ObjectAction.register(loc, 3, (p, obj) -> FortuneInterface.openBenefactors(p));
			ObjectAction.register(loc, 4, (p, obj) -> inspect(p));
			ItemObjectAction.register(995, loc, (p, item, obj) -> FortuneInterface.sacrificeStack(p));
			ItemObjectAction.register(ItemID.PLATINUM_TOKEN, loc, (p, item, obj) -> FortuneInterface.sacrificeStack(p));
		}
		FortuneInterface.register();
		World.startEvent(e -> {
			e.delay(1); // after the maps are loaded: the old fountain/fence locs must exist to be removed
			placeStatue();
			for (int beat = 0; ; beat++) {
				tick();
				FortuneInterface.refreshTimers();
				if (beat % 3 == 0)
					ambient(beat / 3);
				e.delay(2);
			}
		});
	}

	/** Removes the knight statue (and keeps the old fountain + fence ring cleared) and spawns the statue (3x3 at 3104,3506). */
	static void placeStatue() {
		removeMapLoc(38443, X, Y, 10, 0); // knight statue: the Statue of Fortune takes its spot
		// the camel statue's old spot stays clear: the fountain and its fence ring are removed too
		removeMapLoc(879, 3094, 3464, 10, 3);
		int[][] fence = {
				{997, 3093, 3463, 0, 1}, {997, 3094, 3463, 0, 1}, {997, 3095, 3463, 0, 1},
				{997, 3093, 3466, 0, 3}, {997, 3094, 3466, 0, 3}, {997, 3095, 3466, 0, 3},
				{1602, 3092, 3464, 3, 2}, {1602, 3092, 3465, 3, 1}, {1602, 3096, 3463, 3, 0}, {1602, 3096, 3466, 3, 3},
		};
		for (int[] f : fence)
			removeMapLoc(f[0], f[1], f[2], f[3], f[4]);
		statue = GameObject.spawn(desiredLoc(), X, Y, 0, 10, 0);
	}

	/** Gold stardust at the statue's corners while a blessing is active, two opposite corners per beat. */
	static void ambient(int beat) {
		if (statue == null || statue.getId() != BLESSED)
			return;
		int[][] corners = beat % 2 == 0 ? new int[][]{{X - 1, Y - 1}, {X + SIZE, Y + SIZE}} : new int[][]{{X + SIZE, Y - 1}, {X - 1, Y + SIZE}};
		for (int[] c : corners)
			World.sendGraphics(AMBIENT_GFX, 0, 0, c[0], c[1], 0);
	}

	static void removeMapLoc(int id, int x, int y, int type, int rot) {
		GameObject o = Tile.getObject(id, x, y, 0, type, rot);
		if (o != null)
			o.remove();
		else
			log.warn("Statue of Fortune: map loc {} at {},{} (type {}, rot {}) not found", id, x, y, type, rot);
	}

	static int desiredLoc() {
		boolean charged = false;
		for (Blessing b : Blessing.VALUES) {
			Pool p = pool(b);
			if (p.tier > 0)
				return BLESSED;
			if (p.pool >= b.cost(1) * 8 / 10)
				charged = true;
		}
		return charged ? CHARGED : DORMANT;
	}

	static void refreshStatue() {
		if (statue == null)
			return;
		int want = desiredLoc();
		if (statue.getId() != want)
			statue.setId(want);
	}

	// ---------------------------------------------------------------- state

	static Pool pool(Blessing b) {
		return state.pools.computeIfAbsent(b.name(), k -> new Pool());
	}

	public static long poolOf(Blessing b) {
		return pool(b).pool;
	}

	public static long reserveOf(Blessing b) {
		return pool(b).reserve;
	}

	/** Millis left on an active blessing, 0 if inactive. */
	public static long timeLeft(Blessing b) {
		Pool p = pool(b);
		return p.tier == 0 ? 0 : Math.max(0, p.endsAt - System.currentTimeMillis());
	}

	public static long contributionOf(Player player, Blessing b) {
		return pool(b).contrib.getOrDefault(player.uuid(), 0L);
	}

	public static long allTimeOf(Player player) {
		return state.allTime.getOrDefault(player.uuid(), 0L);
	}

	/** Top n of this cycle's pool (b != null) or of all time (b == null): {uid, amount}. */
	static List<Map.Entry<String, Long>> top(Blessing b, int n) {
		Map<String, Long> src = b == null ? state.allTime : pool(b).contrib;
		List<Map.Entry<String, Long>> list = new ArrayList<>(src.entrySet());
		list.sort((x, y) -> Long.compare(y.getValue(), x.getValue()));
		return list.subList(0, Math.min(n, list.size()));
	}

	static String displayName(String uid) {
		return state.names.getOrDefault(uid, uid);
	}

	/** All-time rank of a donor: {name, colour} or null. */
	static String[] rank(long allTime) {
		for (Object[] r : RANKS)
			if (allTime >= (long) r[0])
				return new String[]{(String) r[1], (String) r[2]};
		return null;
	}

	/** Next all-time rank above this amount: {threshold, name} or null at the top. */
	static Object[] nextRank(long allTime) {
		Object[] next = null;
		for (Object[] r : RANKS)
			if (allTime < (long) r[0])
				next = r;
		return next;
	}

	// ---------------------------------------------------------------- donating

	/**
	 * Sacrifices `amount` GP worth of coins (then platinum tokens at 1,000 each) into a blessing. Returns false and tells
	 * the player why if nothing was taken.
	 */
	public static boolean sacrifice(Player player, Blessing b, long amount, boolean broadcast) {
		if (amount <= 0)
			return false;
		long coins = player.getInventory().getAmount(995);
		long tokens = player.getInventory().getAmount(ItemID.PLATINUM_TOKEN);
		if (coins + tokens * 1000 < amount) {
			player.sendMessage(PREFIX + "You don't have " + NumberUtils.formatNumber(amount) + " coins to sacrifice.");
			return false;
		}
		long fromCoins = Math.min(coins, amount);
		long fromTokens = (amount - fromCoins + 999) / 1000;
		if (fromCoins > 0)
			player.getInventory().remove(995, (int) fromCoins);
		if (fromTokens > 0)
			player.getInventory().remove(ItemID.PLATINUM_TOKEN, (int) fromTokens);
		long paid = fromCoins + fromTokens * 1000;

		String uid = player.uuid();
		state.names.put(uid, player.getName());
		long bonus = surge(player, paid);
		long credit = paid + bonus;
		Pool p = pool(b);
		p.contrib.merge(uid, credit, Long::sum);
		state.allTime.merge(uid, credit, Long::sum);

		player.sendMessage(PREFIX + "You sacrifice " + NumberUtils.formatNumber(paid) + " coins to the " + NAME_COL + b.title + END + " blessing.");
		if (broadcast && paid >= BROADCAST_MIN) {
			long now = System.currentTimeMillis();
			if (now - lastBroadcast.getOrDefault(uid, 0L) >= BROADCAST_GAP) {
				lastBroadcast.put(uid, now);
				Broadcast.GLOBAL.sendNews(Icon.SERVER_EVENT, "Statue of Fortune",
						player.getName() + " sacrificed " + shortGp(paid) + " to the " + b.title + " blessing!");
			}
		}
		credit(b, credit, false);
		return true;
	}

	/**
	 * Lucky Surge: 5% per sacrifice of 5M+, 1% per full 1M below that, once per player per 10 minutes. Doubles the
	 * contribution (the bonus counts for the pool and the leaderboard, capped at 25M). Returns the bonus.
	 */
	static long surge(Player player, long paid) {
		if (paid < SURGE_MIN)
			return 0;
		long now = System.currentTimeMillis();
		if (now - lastSurge.getOrDefault(player.uuid(), 0L) < SURGE_COOLDOWN)
			return 0;
		double chance = paid >= SURGE_FULL ? 0.05 : 0.01 * (paid / SURGE_MIN);
		if (Random.get() >= chance)
			return 0;
		lastSurge.put(player.uuid(), now);
		long bonus = Math.min(paid, SURGE_BONUS_CAP);
		player.sendMessage(PREFIX + NAME_COL + "Lucky Surge!" + END + " The statue doubles your sacrifice: +" + NumberUtils.formatNumber(bonus) + " coins.");
		return bonus;
	}

	/** Admin fill: counts for the pool only (no leaderboard, no surge). */
	public static void adminFill(Player player, Blessing b, long amount) {
		player.sendMessage("Added " + NumberUtils.formatNumber(amount) + " to " + b.title + ".");
		credit(b, amount, true);
	}

	/** Adds GP to a blessing: climbs tiers, then extends, then spills into the Reserve at 50%. */
	static void credit(Blessing b, long gp, boolean admin) {
		Pool p = pool(b);
		long now = System.currentTimeMillis();
		if (p.tier < 3) {
			p.pool += gp;
			gp = 0;
			int before = p.tier;
			while (p.tier < 3 && p.pool >= b.cost(p.tier + 1))
				p.tier++;
			if (p.tier > before) {
				p.endsAt = Math.max(p.endsAt, now + BLESSING_MS);
				announceTier(b, p.tier);
			}
			if (p.tier == 3 && p.pool > b.cost(3)) {
				gp = p.pool - b.cost(3); // overflow past Tier III extends below
				p.pool = b.cost(3);
			}
		}
		if (gp > 0 && p.tier == 3) {
			long room = now + CAP_MS - p.endsAt;
			long wantMs = gp * MINUTE / b.extendRate();
			long addMs = Math.max(0, Math.min(room, wantMs));
			p.endsAt += addMs;
			long spent = addMs * b.extendRate() / MINUTE;
			long spill = gp - spent;
			if (spill > 0)
				p.reserve += spill / 2;
		}
		save();
		refreshStatue();
		FortuneInterface.refreshAll();
	}

	static void announceTier(Blessing b, int tier) {
		Broadcast.GLOBAL.sendNews(Icon.SERVER_EVENT, "Statue of Fortune",
				"Tier " + TIER[tier] + " " + b.title + " blessing is active: " + b.effect(tier).toLowerCase() + "!");
		try {
			JSONArray perks = new JSONArray();
			perks.put(new JSONObject().put("perk_name", b.title + " (Tier " + TIER[tier] + ")").put("perk_description", b.effect(tier)));
			GlobalBroadcastHook.sendWellMessage(new JSONObject().put("enabled_perks", perks));
		} catch (Exception e) {
			log.warn("Statue of Fortune: discord broadcast failed", e);
		}
	}

	/** Expires blessings whose timer ran out; the Reserve seeds the next cycle (which can start a tier right away). */
	static void tick() {
		long now = System.currentTimeMillis();
		for (Blessing b : Blessing.VALUES) {
			Pool p = pool(b);
			if (p.tier == 0 || now < p.endsAt)
				continue;
			p.tier = 0;
			p.endsAt = 0;
			p.pool = 0;
			p.cycle++;
			p.contrib.clear();
			long seed = p.reserve;
			p.reserve = 0;
			Broadcast.GLOBAL.sendNews(Icon.SERVER_EVENT, "Statue of Fortune", "The " + b.title + " blessing has faded.");
			if (seed > 0)
				credit(b, seed, true);
			save();
			refreshStatue();
			FortuneInterface.refreshAll();
		}
	}

	static void inspect(Player player) {
		boolean any = false;
		for (Blessing b : Blessing.VALUES) {
			if (!isActive(b))
				continue;
			any = true;
			player.sendMessage(PREFIX + NAME_COL + b.title + " (Tier " + TIER[tier(b)] + ")" + END + ": " + b.effect(tier(b)) + ", " + clock(timeLeft(b)) + " left.");
		}
		if (!any)
			player.sendMessage(PREFIX + "No blessings are active.");
		Blessing closest = null;
		double best = -1;
		for (Blessing b : Blessing.VALUES) {
			int t = tier(b);
			if (t == 3) continue;
			double pct = (double) poolOf(b) / b.cost(t + 1);
			if (pct > best) { best = pct; closest = b; }
		}
		if (closest != null)
			player.sendMessage(PREFIX + "Closest: " + NAME_COL + closest.title + " Tier " + TIER[tier(closest) + 1] + END + " at " + (int) (best * 100) + "%.");
	}

	// ---------------------------------------------------------------- formatting

	public static String clock(long ms) {
		long s = ms / 1000;
		return s >= 3600 ? String.format("%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60) : String.format("%02d:%02d", s / 60, s % 60);
	}

	public static String shortGp(long gp) {
		if (gp >= 1_000_000_000L) return trim(gp / 1e9) + "B";
		if (gp >= 1_000_000L) return trim(gp / 1e6) + "M";
		if (gp >= 1_000L) return trim(gp / 1e3) + "K";
		return Long.toString(gp);
	}

	static String trim(double v) {
		String s = String.format(Locale.ROOT, "%.1f", v);
		return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
	}

	// ---------------------------------------------------------------- storage

	static File file() {
		return new File(ServerWrapper.dataFolder, "runtime/saves/statue_of_fortune.json");
	}

	static void load() {
		File file = file();
		if (!file.exists())
			return;
		try (FileReader reader = new FileReader(file)) {
			SaveState loaded = JsonUtils.GSON.fromJson(reader, SaveState.class);
			if (loaded != null) {
				if (loaded.pools == null) loaded.pools = new HashMap<>();
				if (loaded.allTime == null) loaded.allTime = new HashMap<>();
				if (loaded.names == null) loaded.names = new HashMap<>();
				loaded.pools.values().forEach(p -> { if (p.contrib == null) p.contrib = new HashMap<>(); });
				state = loaded;
			}
		} catch (Exception e) {
			ServerWrapper.logError("Unable to load the Statue of Fortune", e);
		}
	}

	static void save() {
		File file = file();
		file.getParentFile().mkdirs();
		try (FileWriter writer = new FileWriter(file)) {
			JsonUtils.GSON_PRETTY.toJson(state, writer);
		} catch (Exception e) {
			ServerWrapper.logError("Unable to save the Statue of Fortune", e);
		}
	}
}

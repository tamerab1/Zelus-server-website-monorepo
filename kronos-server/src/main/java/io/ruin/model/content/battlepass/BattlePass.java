package io.ruin.model.content.battlepass;

import com.google.gson.reflect.TypeToken;
import io.ruin.api.utils.ServerWrapper;
import io.ruin.api.utils.JsonUtils;
import io.ruin.cache.ObjType;
import io.ruin.model.entity.npc.NPC;
import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.InterfaceHandler;
import io.ruin.model.inter.ToplevelComponent;
import io.ruin.model.inter.actions.DefaultAction;
import io.ruin.model.inter.actions.SimpleAction;
import io.ruin.model.item.Item;
import io.ruin.model.inter.dialogue.OptionsDialogue;
import io.ruin.model.inter.dialogue.YesNoDialogue;
import io.ruin.model.inter.utils.Option;
import io.ruin.model.item.actions.impl.boxes.mystery.MysteryBoxSpinner;
import io.ruin.model.item.loot.LootItem;
import io.ruin.model.item.loot.LootTable;
import io.ruin.model.stat.StatType;
import io.ruin.network.PacketSender;
import io.ruin.api.utils.NumberUtils;

import java.io.File;
import java.io.FileReader;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * The Karn Battle Pass (user 2026-11-15; interface 1186 from the user's mock-up, .dev BuildKarnHub + cache stage 42).
 * <ul>
 * <li>A season runs {@code weeks} weeks from {@code start} (data/battle_pass.json, reloaded when the file changes):
 * {@code tiers.size()} tiers (50), {@code tierXp} (1,000) Battle Pass XP a tier, tier 1 at 0 XP.</li>
 * <li>Every tier has a free and a premium reward; Premium costs {@code premiumCost} (2,200) donator points, once a season.
 * A reached tier's rewards are claimed from the window (inventory, else the bank).</li>
 * <li>XP comes from challenges: {@code dailyCount} (3) daily ones from the daily pool and {@code weeklyCount} (5) weekly
 * ones from the weekly pool, the same for everyone (picked from the date / season week); progress from kills, XP and
 * Slayer tasks (hooks: NPCCombat kill credit, StatList.addXp, SlayerMaster).</li>
 * <li>A new season wipes the player's pass state on their next login / progress.</li>
 * </ul>
 * ::battlepass opens it.
 */
public final class BattlePass {

	private BattlePass() {
	}

	public static final int INTERFACE = 1186;
	// components (v2 2026-11-16, BuildKarnHub.battlePass2 prints them)
	private static final int CLOSE = 5, FIRST_TAB = 6, TAB_STRIDE = 4, SEASON = 18, ENDS = 19, RANK = 20, TIER = 24, TIER_XP = 25,
			FIRST_XP_SEG = 26, CARD_TITLE = 46, CARD_TEXT = 47, UPGRADE = 48, CLAIM_ALL = 51, REWARDS = 53, FIRST_HEADER = 54,
			HEADER_STRIDE = 4, FIRST_FREE = 74, FIRST_PREMIUM = 104, SLOT_STRIDE = 6, ELITE_LOCK = 134, PREV = 135, PAGE = 136,
			NEXT = 137, MISSIONS = 139, FIRST_SUBTAB = 141, SUBTAB_STRIDE = 3, RESET = 147, FIRST_CHALLENGE = 148,
			CHALLENGE_STRIDE = 23, CHALLENGE_ROWS = 5, PER_PAGE = 5, BAR_STEPS = 20, XP_SEGS = 20, INV_BASE = 3100;
	/** slot parts: +0 frame, +1 claimable glow, +2 item grid, +3 not-reached dim, +4 claimed tick, +5 Elite lock */
	private static final int SLOT_GLOW = 1, SLOT_ITEMS = 2, SLOT_DIM = 3, SLOT_TICK = 4, SLOT_LOCK = 5;
	private static final String PAGE_KEY = "bp_page", TAB_KEY = "bp_tab", VIEW_KEY = "bp_view";
	private static final String GOLD = "<col=f5d27a>", GREEN = "<col=2c7a34>", RED = "<col=9e1b24>", GREY = "<col=6a5a4c>",
			INK = "<col=1e1916>", END = "</col>";

	// ------------------------------------------------------------------ data

	public static final class Reward {
		public int id, amount;
	}

	public static final class Tier {
		public Reward free, premium;
	}

	public static final class Challenge {
		public String key, name, type, target;
		public int amount, xp;
	}

	public static final class Data {
		public int season = 1, weeks = 8, tierXp = 1000, premiumCost = 2200, dailyCount = 3, weeklyCount = 5;
		/** donator points a tier when skipping tiers (bundles are discounted, see tierPrice) */
		public int tierCost = 250;
		public String start = "2026-11-15";
		public List<Tier> tiers = new ArrayList<>();
		public List<Challenge> daily = new ArrayList<>(), weekly = new ArrayList<>();
	}

	private static Data data = new Data();
	private static long loadedModified = -1;

	static Data data() {
		File file = new File(ServerWrapper.dataFolder, "battle_pass.json");
		long modified = file.exists() ? file.lastModified() : 0;
		if (modified != loadedModified) {
			loadedModified = modified;
			if (file.exists()) {
				try (FileReader reader = new FileReader(file)) {
					Data read = JsonUtils.GSON.fromJson(reader, new TypeToken<Data>() {}.getType());
					if (read != null)
						data = read;
				} catch (Exception e) {
					ServerWrapper.logError("Unable to load battle_pass.json", e);
				}
			}
		}
		return data;
	}

	// ------------------------------------------------------------------ the season clock (UTC)

	private static LocalDate start(Data d) {
		return LocalDate.parse(d.start);
	}

	private static LocalDate today() {
		return LocalDate.now(ZoneOffset.UTC);
	}

	static boolean running(Data d) {
		LocalDate t = today();
		return !t.isBefore(start(d)) && t.isBefore(start(d).plusWeeks(d.weeks));
	}

	static int week(Data d) {
		return (int) (today().toEpochDay() - start(d).toEpochDay()) / 7;
	}

	static List<Challenge> dailies(Data d) {
		return pick(d.daily, d.dailyCount, today().toEpochDay() * 31 + d.season);
	}

	static List<Challenge> weeklies(Data d) {
		return pick(d.weekly, d.weeklyCount, (long) week(d) * 1009 + d.season * 7L);
	}

	private static List<Challenge> pick(List<Challenge> pool, int count, long seed) {
		List<Challenge> copy = new ArrayList<>(pool);
		Collections.shuffle(copy, new Random(seed));
		return copy.subList(0, Math.min(count, copy.size()));
	}

	private static String dailyKey(Challenge c) {
		return "d" + today().toEpochDay() + ":" + c.key;
	}

	private static String weeklyKey(Data d, Challenge c) {
		return "w" + week(d) + ":" + c.key;
	}

	/** a new season (or the first) clears the player's pass; stale challenge progress is dropped */
	private static void sync(Player p, Data d) {
		if (p.bpSeason != d.season) {
			p.bpSeason = d.season;
			p.bpXp = 0;
			p.bpChallengesDone = 0;
			p.bpPremium = false;
			p.bpClaimed.clear();
			p.bpProgress.clear();
		}
		String day = "d" + today().toEpochDay() + ":", wk = "w" + week(d) + ":";
		p.bpProgress.keySet().removeIf(k -> !k.startsWith(day) && !k.startsWith(wk));
	}

	static int tier(Player p, Data d) {
		return Math.min(d.tiers.size(), 1 + p.bpXp / Math.max(1, d.tierXp));
	}

	// ------------------------------------------------------------------ progress hooks

	public static void onKill(Player p, NPC npc) {
		if (p == null || npc == null || npc.getDef() == null)
			return;
		String name = npc.getDef().name == null ? "" : npc.getDef().name.toLowerCase();
		int level = npc.getDef().combatLevel;
		progress(p, c -> switch (c.type) {
			case "KILL" -> 1;
			case "KILL_LEVEL" -> level >= parse(c.target) ? 1 : 0;
			case "KILL_NAME" -> matches(name, c.target) ? 1 : 0;
			default -> 0;
		});
	}

	public static void onXp(Player p, StatType type, double baseAmount) {
		if (p == null || baseAmount <= 0)
			return;
		int amount = (int) Math.round(baseAmount);
		progress(p, c -> "XP".equals(c.type) && (c.target == null || c.target.isEmpty() || c.target.equalsIgnoreCase(type.name())) ? amount : 0);
	}

	public static void onSlayerTask(Player p) {
		progress(p, c -> "SLAYER_TASK".equals(c.type) ? 1 : 0);
	}

	private interface Gain {
		int of(Challenge c);
	}

	private static void progress(Player p, Gain gain) {
		Data d = data();
		if (!running(d))
			return;
		sync(p, d);
		boolean changed = false;
		for (Challenge c : dailies(d))
			changed |= advance(p, d, c, dailyKey(c), gain.of(c));
		for (Challenge c : weeklies(d))
			changed |= advance(p, d, c, weeklyKey(d, c), gain.of(c));
		if (changed && p.isVisibleInterface(INTERFACE))
			refresh(p);
	}

	private static boolean advance(Player p, Data d, Challenge c, String key, int gain) {
		if (gain <= 0)
			return false;
		int before = p.bpProgress.getOrDefault(key, 0);
		if (before >= c.amount)
			return false;
		int after = (int) Math.min(c.amount, (long) before + gain);
		p.bpProgress.put(key, after);
		if (after >= c.amount) {
			int tierBefore = tier(p, d);
			p.bpXp += c.xp;
			p.bpChallengesDone++;
			p.sendMessage("<col=9e1b24>Battle Pass:</col> challenge complete: " + c.name + " (+" + c.xp + " XP).");
			int tierAfter = tier(p, d);
			if (tierAfter > tierBefore)
				p.sendMessage("<col=9e1b24>Battle Pass:</col> you reached tier " + tierAfter + "! Open ::battlepass to claim your rewards.");
		}
		return true;
	}

	private static int parse(String s) {
		try {
			return Integer.parseInt(s.trim());
		} catch (Exception e) {
			return 0;
		}
	}

	private static boolean matches(String npcName, String names) {
		if (names == null)
			return false;
		for (String n : names.split("\\|"))
			if (!n.isBlank() && npcName.contains(n.trim().toLowerCase()))
				return true;
		return false;
	}

	// ------------------------------------------------------------------ the window

	// ------------------------------------------------------------------ the Battle Pass box (40092, cache stage 48)

	public static final int BOX = 40092;

	/**
	 * Mixed uniques (user 2026-11-16). Weights (of 1,500): supplies 26%, mid uniques 28%, cosmetics / rare gear 11%,
	 * partyhats + pets 4%, the Karn partyhat 0.7%.
	 */
	public static final LootTable BOX_TABLE = new LootTable().addTable(1,
		// supplies
		new LootItem(59960, 1, 2, 60),       // Z Golden Key
		new LootItem(42022, 100, 250, 60),   // Dracodust
		new LootItem(43820, 1, 3, 60),       // Perk exp lamp
		new LootItem(7968, 2, 4, 60),        // Perk task skip scroll
		new LootItem(990, 3, 6, 60),         // Crystal key (noted)
		new LootItem(23083, 5, 15, 60),      // Brimstone key
		new LootItem(23952, 1, 3, 30),       // Enhanced crystal key (noted)
		// mid uniques
		new LootItem(27690, 1, 30),          // Voidwaker
		new LootItem(11802, 1, 30),          // Armadyl godsword
		new LootItem(21003, 1, 25),          // Elder maul
		new LootItem(22978, 1, 25),          // Dragon hunter lance
		new LootItem(26374, 1, 25),          // Zaryte crossbow
		new LootItem(26219, 1, 25),          // Osmumten's fang
		new LootItem(42672, 1, 25),          // Celestial robe top
		new LootItem(42674, 1, 25),          // Celestial robe bottom
		new LootItem(40058, 1, 20),          // Nightrix robe top
		new LootItem(40059, 1, 20),          // Nightrix robe bottom
		new LootItem(40015, 1, 20),          // Love whip
		new LootItem(40094, 1, 20),          // Midnight spring icon (t1)
		new LootItem(40091, 1, 20),          // Infernoglint icon (t1)
		new LootItem(40243, 1, 20),          // Void eye icon (t1)
		new LootItem(42540, 1, 20),          // Starbound Invoker (T1)
		// cosmetics + rare gear
		new LootItem(1053, 1, 20),           // Green halloween mask
		new LootItem(1055, 1, 20),           // Blue halloween mask
		new LootItem(1057, 1, 20),           // Red halloween mask
		new LootItem(42500, 1, 20),          // Candy cane santa hat
		new LootItem(40079, 1, 20),          // Ethereal dinh's bulwark
		new LootItem(42566, 1, 20),          // Brimstone Wings
		new LootItem(42767, 1, 20),          // Ava's of Karn
		new LootItem(60282, 1, 20),          // Bond of the Void Deadeye (T1)
		// partyhats + pets
		new LootItem(1038, 1, 10),           // Red partyhat
		new LootItem(1042, 1, 10),           // Blue partyhat
		new LootItem(11862, 1, 10),          // Black partyhat
		new LootItem(42754, 1, 10),          // Starwoven partyhat
		new LootItem(59965, 1, 10),          // Cinder (pet)
		new LootItem(60250, 1, 10),          // SteelWill (pet)
		// the jackpot
		new LootItem(42594, 1, 10));         // Karn Partyhat

	/** Examine on the box shows its rewards: the Karn spinner window (every reward with its chance). */
	public static boolean examineBox(Player p, Item item) {
		if (item.getId() != BOX)
			return false;
		MysteryBoxSpinner.open(p, BOX);
		return true;
	}

	public static void register() {
		MysteryBoxSpinner.registerBox(BOX, "Battle Pass Box", BOX_TABLE);
		InterfaceHandler.register(INTERFACE, h -> {
			h.actions[CLOSE] = (SimpleAction) Player::closeInterfaces;
			h.actions[PREV] = (SimpleAction) p -> page(p, -1);
			h.actions[NEXT] = (SimpleAction) p -> page(p, 1);
			h.actions[RANK] = (SimpleAction) BattlePass::buyTiersMenu;
			h.actions[UPGRADE] = (SimpleAction) p -> {
				if (p.bpPremium)
					buyTiersMenu(p);
				else
					unlockPremium(p);
			};
			h.actions[CLAIM_ALL] = (SimpleAction) BattlePass::claimAll;
			// tabs: Rewards / Missions switch the view, Redeem claims every reached reward
			h.actions[FIRST_TAB] = (SimpleAction) p -> view(p, 0);
			h.actions[FIRST_TAB + TAB_STRIDE] = (SimpleAction) p -> view(p, 1);
			h.actions[FIRST_TAB + 2 * TAB_STRIDE] = (SimpleAction) BattlePass::claimAll;
			for (int t = 0; t < 2; t++) {
				int tab = t;
				h.actions[FIRST_SUBTAB + t * SUBTAB_STRIDE] = (SimpleAction) p -> {
					p.set(TAB_KEY, tab);
					refresh(p);
				};
			}
			// the reward slots are item grids (clientscript 149), so the menu reads "Claim <item>"; option 1 Claim, 10 Examine
			for (int i = 0; i < PER_PAGE; i++) {
				int slot = i;
				h.actions[FIRST_FREE + i * SLOT_STRIDE + SLOT_ITEMS] = (DefaultAction) (p, option, s, itemId) -> slotOp(p, slot, false, option, itemId);
				h.actions[FIRST_PREMIUM + i * SLOT_STRIDE + SLOT_ITEMS] = (DefaultAction) (p, option, s, itemId) -> slotOp(p, slot, true, option, itemId);
			}
		});
	}

	private static void view(Player p, int view) {
		p.set(VIEW_KEY, view);
		refresh(p);
	}

	public static void open(Player p) {
		Data d = data();
		sync(p, d);
		p.set(PAGE_KEY, (tier(p, d) - 1) / PER_PAGE);
		p.set(VIEW_KEY, 0);
		p.openInterface(ToplevelComponent.MAINMODAL, INTERFACE);
		refresh(p);
	}

	private static void page(Player p, int delta) {
		Data d = data();
		int pages = Math.max(1, (d.tiers.size() + PER_PAGE - 1) / PER_PAGE);
		int page = Math.max(0, Math.min(pages - 1, p.<Integer>get(PAGE_KEY, 0) + delta));
		p.set(PAGE_KEY, page);
		refresh(p);
	}

	static void refresh(Player p) {
		if (!p.isVisibleInterface(INTERFACE))
			return;
		Data d = data();
		sync(p, d);
		PacketSender ps = p.getPacketSender();
		int tier = tier(p, d), maxTier = d.tiers.size();
		boolean running = running(d);
		int view = p.<Integer>get(VIEW_KEY, 0);

		// header
		ps.sendString(INTERFACE, SEASON, "Season " + d.season);
		LocalDate end = start(d).plusWeeks(d.weeks);
		ps.sendString(INTERFACE, ENDS, !running && today().isBefore(start(d)) ? "Starts " + start(d)
				: running ? "Ends in: " + until(end.atStartOfDay(ZoneOffset.UTC)) : "Season over");
		for (int t = 0; t < 3; t++)
			ps.setHidden(INTERFACE, FIRST_TAB + t * TAB_STRIDE + 1, t != view);
		ps.sendString(INTERFACE, RANK + 1, "Purchase Rank");
		ps.sendString(INTERFACE, TIER, String.valueOf(tier));
		int into = p.bpXp % Math.max(1, d.tierXp);
		ps.sendString(INTERFACE, TIER_XP, tier >= maxTier ? "Max tier reached" : NumberUtils.formatNumber(into) + " / " + NumberUtils.formatNumber(d.tierXp) + " XP");
		int fill = tier >= maxTier ? XP_SEGS : (int) Math.floor((double) XP_SEGS * into / Math.max(1, d.tierXp));
		for (int sgm = 0; sgm < XP_SEGS; sgm++)
			ps.setHidden(INTERFACE, FIRST_XP_SEG + sgm, sgm >= fill);

		// the Elite card
		if (p.bpPremium) {
			ps.sendString(INTERFACE, CARD_TITLE, "Elite Battle Pass<br>active");
			ps.sendString(INTERFACE, CARD_TEXT, "Every Elite reward is<br>yours to claim. Skip<br>ahead with ranks.");
			ps.sendString(INTERFACE, UPGRADE + 1, "Purchase Rank");
		} else {
			ps.sendString(INTERFACE, CARD_TITLE, "Upgrade to Elite<br>Battle Pass");
			ps.sendString(INTERFACE, CARD_TEXT, "Unlock all " + maxTier + " Elite rewards<br>for " + NumberUtils.formatNumber(d.premiumCost)
					+ " donator points,<br>this whole season!");
			ps.sendString(INTERFACE, UPGRADE + 1, "Upgrade Pass");
		}
		ps.sendString(INTERFACE, CLAIM_ALL + 1, "Claim All (" + claimable(p, d).size() + ")");

		ps.setHidden(INTERFACE, REWARDS, view != 0);
		ps.setHidden(INTERFACE, MISSIONS, view != 1);
		if (view == 0) {
			int page = p.<Integer>get(PAGE_KEY, 0);
			int first = page * PER_PAGE + 1;
			ps.sendString(INTERFACE, PAGE, "Tiers " + first + " - " + Math.min(maxTier, first + PER_PAGE - 1));
			ps.setHidden(INTERFACE, ELITE_LOCK, p.bpPremium);
			for (int i = 0; i < PER_PAGE; i++) {
				int t = first + i;
				boolean exists = t <= maxTier, reached = exists && t <= tier, milestone = t % 5 == 0;
				int base = FIRST_HEADER + i * HEADER_STRIDE;
				ps.setHidden(INTERFACE, base, reached);
				ps.setHidden(INTERFACE, base + 1, !reached || milestone);
				ps.setHidden(INTERFACE, base + 2, !reached || !milestone);
				ps.sendString(INTERFACE, base + 3, exists ? String.valueOf(t) : "");
				sendSlot(p, d, FIRST_FREE + i * SLOT_STRIDE, t, false, tier);
				sendSlot(p, d, FIRST_PREMIUM + i * SLOT_STRIDE, t, true, tier);
			}
			return;
		}

		// missions
		int tab = p.<Integer>get(TAB_KEY, 0);
		for (int t = 0; t < 2; t++)
			ps.setHidden(INTERFACE, FIRST_SUBTAB + t * SUBTAB_STRIDE + 1, t != tab);
		ZonedDateTime reset = tab == 0 ? today().plusDays(1).atStartOfDay(ZoneOffset.UTC)
				: start(d).plusWeeks(week(d) + 1L).atStartOfDay(ZoneOffset.UTC);
		ps.sendString(INTERFACE, RESET, running ? "Resets in: " + until(reset) : "");
		List<Challenge> list = !running ? List.of() : tab == 0 ? dailies(d) : weeklies(d);
		for (int r = 0; r < CHALLENGE_ROWS; r++) {
			int base = FIRST_CHALLENGE + r * CHALLENGE_STRIDE;
			Challenge c = r < list.size() ? list.get(r) : null;
			if (c == null) {
				ps.sendString(INTERFACE, base, r == 0 && !running ? "No season is running." : "");
				for (int sgm = 0; sgm < BAR_STEPS; sgm++)
					ps.setHidden(INTERFACE, base + 1 + sgm, true);
				ps.sendString(INTERFACE, base + 21, "");
				ps.sendString(INTERFACE, base + 22, "");
				continue;
			}
			int done = p.bpProgress.getOrDefault(tab == 0 ? dailyKey(c) : weeklyKey(d, c), 0);
			boolean complete = done >= c.amount;
			ps.sendString(INTERFACE, base, complete ? GREY + c.name + END : c.name);
			// any progress shows at least one step
			int steps = done <= 0 ? 0 : Math.max(1, (int) Math.floor((double) BAR_STEPS * done / Math.max(1, c.amount)));
			for (int sgm = 0; sgm < BAR_STEPS; sgm++)
				ps.setHidden(INTERFACE, base + 1 + sgm, sgm >= steps);
			ps.sendString(INTERFACE, base + 21, complete ? "Done" : shortNumber(done) + "/" + shortNumber(c.amount));
			ps.sendString(INTERFACE, base + 22, "+" + c.xp + " XP");
		}
	}

	private static void sendSlot(Player p, Data d, int base, int t, boolean premium, int tier) {
		PacketSender ps = p.getPacketSender();
		Reward r = t <= d.tiers.size() ? (premium ? d.tiers.get(t - 1).premium : d.tiers.get(t - 1).free) : null;
		if (r == null || r.id <= 0) {
			for (int part = 1; part <= SLOT_LOCK; part++)
				ps.setHidden(INTERFACE, base + part, part != SLOT_DIM);
			return;
		}
		boolean claimed = p.bpClaimed.containsKey((premium ? "p" : "f") + t);
		boolean locked = premium && !p.bpPremium;
		boolean reached = t <= tier;
		ps.setHidden(INTERFACE, base + SLOT_ITEMS, false);
		int inv = INV_BASE + (premium ? PER_PAGE : 0) + (t - 1) % PER_PAGE;
		ps.sendClientScript(149, "IviiiIsssss", INTERFACE << 16 | (base + SLOT_ITEMS), inv, 2, 2, 0, -1, "Claim", "", "", "", ""); // 2x2: script 153 divides by (columns - 1)
		ps.sendIfEvents(INTERFACE, base + SLOT_ITEMS, 0, 0, 1026); // op 1 (Claim) + Examine
		ps.sendItems(-1, base + SLOT_ITEMS, inv, new Item(r.id, r.amount));
		ps.setHidden(INTERFACE, base + SLOT_GLOW, claimed || locked || !reached);
		ps.setHidden(INTERFACE, base + SLOT_DIM, reached);
		ps.setHidden(INTERFACE, base + SLOT_TICK, !claimed);
		ps.setHidden(INTERFACE, base + SLOT_LOCK, !locked || claimed);
	}

	private static String until(ZonedDateTime when) {
		Duration left = Duration.between(ZonedDateTime.now(ZoneOffset.UTC), when);
		if (left.isNegative())
			return "0m";
		long days = left.toDays(), hours = left.toHoursPart(), mins = left.toMinutesPart();
		return days > 0 ? days + "d " + hours + "h" : hours > 0 ? hours + "h " + mins + "m" : mins + "m";
	}

	private static String shortNumber(int n) {
		if (n >= 1_000_000)
			return (n % 1_000_000 == 0 ? String.valueOf(n / 1_000_000) : String.valueOf((n / 100_000) / 10.0)) + "m";
		if (n >= 1_000)
			return (n % 1_000 == 0 || n >= 100_000 ? String.valueOf(n / 1_000) : String.valueOf((n / 100) / 10.0)) + "k";
		return String.valueOf(n);
	}

	// ------------------------------------------------------------------ rewards

	private static List<int[]> claimable(Player p, Data d) {
		List<int[]> out = new ArrayList<>();
		int tier = tier(p, d);
		for (int t = 1; t <= Math.min(tier, d.tiers.size()); t++) {
			Tier row = d.tiers.get(t - 1);
			if (row.free != null && row.free.id > 0 && !p.bpClaimed.containsKey("f" + t))
				out.add(new int[]{t, 0});
			if (p.bpPremium && row.premium != null && row.premium.id > 0 && !p.bpClaimed.containsKey("p" + t))
				out.add(new int[]{t, 1});
		}
		return out;
	}

	private static void slotOp(Player p, int slot, boolean premium, int option, int itemId) {
		if (option == 10) {
			if (itemId > 0)
				p.sendMessage(ObjType.get(itemId).examineText());
			return;
		}
		claim(p, slot, premium);
	}

	private static void claim(Player p, int slot, boolean premium) {
		Data d = data();
		sync(p, d);
		int t = p.<Integer>get(PAGE_KEY, 0) * PER_PAGE + 1 + slot;
		if (t > d.tiers.size())
			return;
		Tier row = d.tiers.get(t - 1);
		Reward r = premium ? row.premium : row.free;
		if (r == null || r.id <= 0)
			return;
		String key = (premium ? "p" : "f") + t;
		if (p.bpClaimed.containsKey(key)) {
			p.sendMessage("You have already claimed this reward.");
			return;
		}
		if (premium && !p.bpPremium) {
			p.sendMessage("This reward is on the Elite track. Upgrade to Elite to claim it.");
			return;
		}
		if (t > tier(p, d)) {
			p.sendMessage("Reach tier " + t + " to claim this reward.");
			return;
		}
		give(p, key, r);
		refresh(p);
	}

	private static void claimAll(Player p) {
		Data d = data();
		sync(p, d);
		List<int[]> list = claimable(p, d);
		if (list.isEmpty()) {
			p.sendMessage("You have no Battle Pass rewards to claim.");
			return;
		}
		for (int[] c : list) {
			Tier row = d.tiers.get(c[0] - 1);
			give(p, (c[1] == 1 ? "p" : "f") + c[0], c[1] == 1 ? row.premium : row.free);
		}
		refresh(p);
	}

	private static void give(Player p, String key, Reward r) {
		p.bpClaimed.put(key, 1);
		String name = ObjType.get(r.id).name;
		if (p.getInventory().hasRoomFor(r.id, r.amount)) {
			p.getInventory().add(r.id, r.amount);
			p.sendMessage("<col=9e1b24>Battle Pass:</col> " + NumberUtils.formatNumber(r.amount) + " x " + name + ".");
		} else {
			p.getBank().add(r.id, r.amount, null);
			p.sendMessage("<col=9e1b24>Battle Pass:</col> " + NumberUtils.formatNumber(r.amount) + " x " + name + " (sent to your bank).");
		}
	}

	/** The Unlock button: Premium (until bought) and tier skips. */
	private static void unlock(Player p) {
		Data d = data();
		sync(p, d);
		List<Option> options = new ArrayList<>();
		if (!p.bpPremium)
			options.add(new Option("Upgrade to Elite (" + NumberUtils.formatNumber(d.premiumCost) + " DP)", () -> unlockPremium(p)));
		options.add(new Option("Buy tiers", () -> buyTiersMenu(p)));
		options.add(new Option("Cancel"));
		p.dialogue(new OptionsDialogue("Battle Pass - you have " + NumberUtils.formatNumber(p.getDonatorPoints()) + " DP", options));
	}

	/**
	 * Tier skips: {@code tierCost} (250, user 2026-11-16) DP a tier (about $2.90; Elite is about 9 tiers'
	 * worth), 10% off 5+, 15% off 10+, 20% off 25+. Bought tiers add a full tier of Battle Pass XP each (progress into
	 * the current tier is kept); the last tier is the cap.
	 */
	static int tierPrice(Data d, int tiers) {
		double off = tiers >= 25 ? 0.20 : tiers >= 10 ? 0.15 : tiers >= 5 ? 0.10 : 0;
		return (int) Math.round(tiers * d.tierCost * (1 - off));
	}

	private static void buyTiersMenu(Player p) {
		Data d = data();
		sync(p, d);
		if (!running(d)) {
			p.sendMessage("No Battle Pass season is running.");
			return;
		}
		int left = d.tiers.size() - tier(p, d);
		if (left <= 0) {
			p.sendMessage("You have already reached the last Battle Pass tier.");
			return;
		}
		List<Option> options = new ArrayList<>();
		for (int n : new int[] { 1, 5, 10, 25 })
			if (n < left)
				options.add(new Option(n + " tier" + (n == 1 ? "" : "s") + " (" + NumberUtils.formatNumber(tierPrice(d, n)) + " DP)", () -> buyTiers(p, n)));
		options.add(new Option("All " + left + " remaining (" + NumberUtils.formatNumber(tierPrice(d, left)) + " DP)", () -> buyTiers(p, left)));
		p.dialogue(new OptionsDialogue("Skip tiers - you have " + NumberUtils.formatNumber(p.getDonatorPoints()) + " DP", options));
	}

	private static void buyTiers(Player p, int wanted) {
		Data d = data();
		sync(p, d);
		int n = Math.min(wanted, d.tiers.size() - tier(p, d));
		if (n <= 0 || !running(d))
			return;
		int cost = tierPrice(d, n);
		if (p.getDonatorPoints() < cost) {
			p.sendMessage(n + " tier" + (n == 1 ? "" : "s") + " cost " + NumberUtils.formatNumber(cost) + " donator points. You have "
				+ NumberUtils.formatNumber(p.getDonatorPoints()) + ".");
			return;
		}
		p.updateDonatorPoints(-cost);
		int before = tier(p, d);
		p.bpXp += n * d.tierXp;
		int after = tier(p, d);
		p.sendMessage("<col=9e1b24>Battle Pass:</col> you skipped to tier " + after + " (" + (after - before) + " tier"
			+ (after - before == 1 ? "" : "s") + ", " + NumberUtils.formatNumber(cost) + " DP). Claim your rewards in the window.");
		open(p);
	}

	private static void unlockPremium(Player p) {
		Data d = data();
		sync(p, d);
		if (p.bpPremium) {
			p.sendMessage("Your Elite Battle Pass is already active this season.");
			return;
		}
		if (!running(d)) {
			p.sendMessage("No Battle Pass season is running.");
			return;
		}
		int cost = d.premiumCost;
		if (p.getDonatorPoints() < cost) {
			p.sendMessage("Elite costs " + NumberUtils.formatNumber(cost) + " donator points. You have "
					+ NumberUtils.formatNumber(p.getDonatorPoints()) + ".");
			return;
		}
		p.dialogue(new YesNoDialogue("Upgrade to the Elite Battle Pass?", "Spend " + NumberUtils.formatNumber(cost)
				+ " donator points on Elite for Season " + d.season + "?", 13190, 1, () -> {
			if (p.bpPremium || p.getDonatorPoints() < cost)
				return;
			p.updateDonatorPoints(-cost);
			p.bpPremium = true;
			p.sendMessage("<col=9e1b24>Battle Pass:</col> Elite unlocked for Season " + d.season + "!");
			open(p);
		}));
	}
}

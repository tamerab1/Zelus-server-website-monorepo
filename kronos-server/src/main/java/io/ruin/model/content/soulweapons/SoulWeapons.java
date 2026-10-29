package io.ruin.model.content.soulweapons;

import io.ruin.HooksV2;
import io.ruin.model.entity.npc.NPC;
import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.dialogue.MessageDialogue;
import io.ruin.model.inter.dialogue.OptionsDialogue;
import io.ruin.model.inter.handlers.EquipmentStats;
import io.ruin.model.inter.utils.Option;
import io.ruin.model.item.Item;
import io.ruin.model.item.containers.Equipment;
import io.ruin.model.item.containers.bank.BankItem;
import io.ruin.model.stat.StatType;
import io.ruin.network.incoming.handlers.CommandHandler;
import io.ruin.api.utils.NumberUtils;

/**
 * Soulbound Living Weapons (2026-10-18): one untradeable weapon per player (Blade / Recurve / Spire) that grows from
 * tier 1 to 15 (10 until customs27, 2026-10-27) on Soul XP from NPC kills made while wielding it. Each tier grants a Soul Point for the Soul Tree
 * ({@link SoulTalent}, {@link SoulTalents}, {@link SoulTreeInterface}); tiers 5, 8 and 10 unlock the cosmetic
 * {@link SoulAura}s. Everything is kept in {@link SoulWeaponData}.
 * <ul>
 * <li>Soul XP per kill: max(1, combat level / 4); NPCs with 400+ hitpoints count as bosses and add max hp / 10, clamped
 * to 50..250 (works for custom bosses too). Combat dummies give nothing.</li>
 * <li>Tiers: the XP to go from tier t to t+1 is 150 x 1.75^(t-1) up to tier 10 (about 30,600 Soul XP in all), then
 * each step 1.35x the last (tier 15 at about 207,000). Each new tier grants a Soul Point (14 by tier 15). Tiers 4, 8 and
 * 15 swap the weapon to its next look -- a new mesh and attack style -- in place (equipment, inventory or bank); the
 * Ascended look (8-14) has a special attack (SoulRend / SoulVolley / SoulNova), the Radiant look (15) a stronger one
 * (RadiantSever / CrimsonEclipse / ArcaneCataclysm).</li>
 * <li>Check (inventory or worn) opens the weapon's details, redrawn live on every kill while open. Every Soul XP gain
 * and every login sends a "[Soulbound] ... tier N, a / b (P%)" line that the client's zelussoulweapon plugin turns into
 * an on-screen progress box.</li>
 * <li>Stats: the item_info entries hold the tier-1 values (the same for every look of a line); tiers 2-10 each add a
 * ninth of the gap to the tier-10 target (about 90% of best-in-slot), tiers 11-15 each a fifth of the further gap to
 * the tier-15 target (a little past best-in-slot), via Equipment's bonus calculation, so the equipment stats screen shows
 * the real numbers. The Spire's built-in spell hits up to (Magic / 3 - 1) x 0.40 at tier 1, x 1.15 at tier 10 and
 * x 1.45 at tier 15 (about 45 at 99 Magic).</li>
 * </ul>
 * ::soulweapon claims the weapon once (or recovers a lost one, progress intact); ::soulweapon info shows progress;
 * ::soultree opens the Soul Tree (also the Check window's Soul Tree button).
 * Developers: ::soulweapon xp <n> (add Soul XP), ::soulweapon reset (clear the choice to test another line).
 * Only the player's own line gathers souls: wielding another line (e.g. a spawned one) says so, once a minute.
 * New players pick their line in the tutorial (SoulStarterInterface); others through the Zelus Guide or ::soulweapon.
 */
public final class SoulWeapons {

	/** Cumulative Soul XP needed to REACH each tier (index = tier). */
	static final long[] TIER_XP = new long[16];
	public static final int MAX_TIER = 15;
	/** The old cap: tiers above it follow the gentler curve and the tier-15 stat targets. */
	static final int ASCENDED_CAP = 10;
	private static final int BOSS_HP = 400, BOSS_MIN = 50, BOSS_MAX = 250;
	private static final int SOUND_TIER_UP = 101, GFX_TIER_UP = 199;
	static final String TAG = "<col=1fa7b5><shad=000000>[Soulbound]</shad></col> ";
	/** Highlight for tier-ups and evolutions: soul violet, readable on the chatbox's parchment and when transparent. */
	static final String HIGHLIGHT = "<col=7d1fd1>";

	static {
		long total = 0, step = 0;
		for (int t = 2; t <= MAX_TIER; t++) {
			step = t <= ASCENDED_CAP ? Math.round(150 * Math.pow(1.75, t - 2)) : Math.round(step * 1.35);
			total += step;
			TIER_XP[t] = total;
		}
	}

	/**
	 * Bonuses added at tier 10 over the tier-1 item stats, per bonus index ({@link EquipmentStats}). Tier t gets
	 * (t - 1) / 9 of it. Targets: Blade ~ Blade of saeldor x 0.9, Recurve ~ Bow of faerdhinen x 0.9.
	 */
	static int[] tierTenBonus(SoulWeaponType type) {
		int[] b = new int[EquipmentStats.SLAYER + 1];
		switch (type) {
			case MELEE -> {
				b[EquipmentStats.STAB_ATTACK] = 44;   // 6 -> 50
				b[EquipmentStats.SLASH_ATTACK] = 75;  // 10 -> 85
				b[EquipmentStats.MELEE_STRENGTH] = 71; // 9 -> 80
			}
			case RANGED -> {
				b[EquipmentStats.RANGE_ATTACK] = 103;    // 12 -> 115
				b[EquipmentStats.RANGED_STRENGTH] = 85;  // 10 -> 95
			}
			case MAGIC -> b[EquipmentStats.MAGIC_ATTACK] = 35; // 5 -> 40
		}
		return b;
	}

	/** The bonus a tier adds at one bonus index: ninths of the tier-10 target to tier 10, then fifths of the tier-15 extra. */
	static int bonusAt(SoulWeaponType type, int tier, int index) {
		int upTo10 = Math.min(tier, ASCENDED_CAP) - 1, past10 = Math.max(0, tier - ASCENDED_CAP);
		return tierTenBonus(type)[index] * upTo10 / 9 + tierFifteenExtra(type)[index] * past10 / 5;
	}

	/** Bonuses added on top of the tier-10 values by tier 15 (a fifth per tier 11-15): a little past best-in-slot. */
	static int[] tierFifteenExtra(SoulWeaponType type) {
		int[] b = new int[EquipmentStats.SLAYER + 1];
		switch (type) {
			case MELEE -> {
				b[EquipmentStats.STAB_ATTACK] = 25;   // 50 -> 75
				b[EquipmentStats.SLASH_ATTACK] = 40;  // 85 -> 125
				b[EquipmentStats.MELEE_STRENGTH] = 30; // 80 -> 110
			}
			case RANGED -> {
				b[EquipmentStats.RANGE_ATTACK] = 30;    // 115 -> 145
				b[EquipmentStats.RANGED_STRENGTH] = 25; // 95 -> 120
			}
			case MAGIC -> b[EquipmentStats.MAGIC_ATTACK] = 20; // 40 -> 60
		}
		return b;
	}

	private SoulWeapons() {
	}

	public static void register() {
		CommandHandler.hooks.register(CommandHandler.Hook.Handle.class, SoulWeapons::command);
		SoulWeaponInterface.register();
		SoulStarterInterface.register();
		SoulTreeInterface.register();
		SoulAura.register();
		SoulAltar.register(); // the Reliquary of Souls at home
		for (SoulWeaponType type : SoulWeaponType.values())
			for (int id : type.looks()) {
				io.ruin.model.item.actions.ItemAction.registerInventory(id, "check", (player, item) -> check(player, type));
				io.ruin.model.item.actions.ItemAction.registerEquipment(id, "check", (player, item) -> check(player, type));
			}
		io.ruin.model.entity.shared.listeners.LoginListener.register(player -> {
			if (player.soulWeapon != null)
				progressLine(player); // primes the client's progress box
		});
	}

	private static HooksV2.Result command(CommandHandler.Hook.Handle cmd) {
		if (cmd.command().equals("soultree")) {
			SoulTreeCommand.teleport(cmd.player());
			return HooksV2.Result.Return;
		}
		if (!cmd.command().equals("soulweapon"))
			return HooksV2.Result.Pass;
		Player player = cmd.player();
		if (cmd.args().length > 0 && cmd.args()[0].equalsIgnoreCase("info")) {
			info(player);
		} else if (cmd.args().length > 0 && cmd.args()[0].equalsIgnoreCase("reset") && player.isDev()) {
			player.soulWeapon = null; // developer: choose another line (the weapon items themselves are not removed)
			player.sendMessage(TAG + "Your Soulbound choice was reset. Type ::soulweapon to choose again.");
		} else if (cmd.args().length > 1 && cmd.args()[0].equalsIgnoreCase("xp") && player.isDev()) {
			try {
				addXp(player, Long.parseLong(cmd.args()[1])); // developer: test the tier-ups
			} catch (NumberFormatException e) {
				player.sendMessage("Usage: ::soulweapon xp <amount>");
			}
		} else {
			claim(player);
		}
		return HooksV2.Result.Return;
	}

	// ---- acquisition ----

	/** The one-time choice, or recovery of a lost weapon with all progress kept. */
	public static void claim(Player player) {
		SoulWeaponData data = player.soulWeapon;
		if (data != null) {
			if (owns(player, data.weaponType)) {
				player.sendMessage(TAG + "You already have your " + data.weaponType.name + ". Type ::soulweapon info for its progress.");
				return;
			}
			give(player, data.weaponType.itemFor(data.tier), "Your " + data.weaponType.name + " returns to you, its souls intact.");
			return;
		}
		SoulStarterInterface.open(player);
	}

	/** The selector's Bind (SoulStarterInterface, which has already set player.soulWeapon): the tier-1 weapon + welcome. */
	static void bindGive(Player player, SoulWeaponType type) {
		give(player, type.itemFor(1), "You have awakened the " + HIGHLIGHT + type.name + "</col>! Slay beasts across "
			+ "Gielinor to harvest their souls.");
		player.privateSound(SOUND_TIER_UP);
		player.graphics(GFX_TIER_UP);
	}

	private static void give(Player player, int itemId, String message) {
		if (player.getInventory().hasRoomFor(itemId))
			player.getInventory().add(itemId, 1);
		else
			player.getBank().add(itemId, 1, null);
		player.sendMessage(TAG + message + (player.getInventory().contains(itemId) ? "" : " It was placed in your bank."));
	}

	/** True if any look of this line is worn, carried or banked. */
	static boolean owns(Player player, SoulWeaponType type) {
		for (int id : type.looks())
			if (player.getEquipment().contains(id) || player.getInventory().contains(id) || player.getBank().contains(id))
				return true;
		return false;
	}

	// ---- progression ----

	/** Called from NPCCombat.startDeath for the player credited with the kill. */
	public static void onNpcKill(Player player, NPC npc) {
		SoulWeaponData data = player.soulWeapon;
		Item weapon = player.getEquipment().get(Equipment.SLOT_WEAPON);
		if (weapon == null)
			return;
		SoulWeaponType wielded = SoulWeaponType.of(weapon.getId());
		if (wielded == null)
			return;
		if (data == null || data.weaponType != wielded) {
			notBound(player, wielded); // a spawned / other line: say why no souls are gathered (2026-10-18)
			return;
		}
		String name = npc.getDef().name == null ? "" : npc.getDef().name.toLowerCase();
		if (name.contains("dummy"))
			return;
		int level = Math.max(0, npc.getDef().combatLevel);
		long xp = Math.max(1, level / 4);
		int maxHp = npc.getMaxHp();
		if (maxHp >= BOSS_HP)
			xp += Math.max(BOSS_MIN, Math.min(BOSS_MAX, maxHp / 10));
		data.soulsHarvested++;
		SoulWeaponInterface.recordKill(player, xp, npc.getDef().name);
		addXp(player, xp);
		SoulTalents.onKill(player, npc);
	}

	static void addXp(Player player, long xp) {
		SoulWeaponData data = player.soulWeapon;
		if (data == null || xp <= 0)
			return;
		data.soulXp += xp;
		int before = data.tier;
		while (data.tier < MAX_TIER && data.soulXp >= TIER_XP[data.tier + 1]) {
			data.tier++;
			data.availableSoulPoints++;
		}
		if (data.tier > before) {
			player.sendMessage(TAG + HIGHLIGHT + "Your " + data.weaponType.name + " has reached tier " + data.tier + "!</col> "
				+ "You have " + data.availableSoulPoints + " Soul Point" + (data.availableSoulPoints == 1 ? "" : "s") + " to spend.");
			player.privateSound(SOUND_TIER_UP);
			player.graphics(GFX_TIER_UP);
			for (int t = before + 1; t <= data.tier; t++) {
				SoulAura aura = SoulAura.unlockedAt(t);
				if (aura != null)
					player.sendMessage(TAG + HIGHLIGHT + "New aura unlocked: " + aura.name + "!</col> Wear it from your Soul Tree (::soultree).");
			}
			if (data.weaponType.itemFor(before) != data.weaponType.itemFor(data.tier))
				evolve(player, data.weaponType.itemFor(before), data.weaponType.itemFor(data.tier));
			player.getEquipment().sendAll = true; // the tier bonuses changed: re-total the stats
		}
		// at the max tier every kill would repeat "Tier 15 (max)": the XP is still counted, silently
		if (data.tier < MAX_TIER || data.tier > before)
			player.sendFilteredMessage(TAG + "<col=0b6f78>+" + xp + " Soul XP</col> | " + progressText(data));
		if (SoulWeaponInterface.isOpen(player))
			SoulWeaponInterface.draw(player);
		else if (SoulTreeInterface.isOpen(player) && data.tier > before)
			SoulTreeInterface.draw(player); // a new point to spend
	}

	/**
	 * "Tier N | a / b (P%)", or "Tier 10 (max)" -- also parsed by the client's zelussoulweapon plugin. Coloured for the
	 * parchment chatbox (the old "tier 8, 3,614 / ..." read as one number): the tier in soul violet, the numbers in the
	 * chatbox's own text colour, the percent in dark green.
	 */
	static String progressText(SoulWeaponData d) {
		String tier = HIGHLIGHT + "Tier " + d.tier + "</col>";
		if (d.tier >= MAX_TIER)
			return tier + " (max)";
		long from = TIER_XP[d.tier], to = TIER_XP[d.tier + 1];
		int pct = (int) Math.min(99, (d.soulXp - from) * 100 / Math.max(1, to - from));
		return tier + " | " + NumberUtils.formatNumber(d.soulXp - from) + " / " + NumberUtils.formatNumber(to - from)
			+ " (<col=0b7a2e>" + pct + "%</col>)";
	}

	private static void progressLine(Player player) {
		player.sendFilteredMessage(TAG + player.soulWeapon.weaponType.name + " | " + progressText(player.soulWeapon));
	}

	/** Check on a weapon of this line: its details if it's the player's own line, otherwise why it isn't. */
	private static void check(Player player, SoulWeaponType type) {
		SoulWeaponData d = player.soulWeapon;
		if (d != null && d.weaponType != type) {
			player.sendMessage(TAG + "This " + type.name + " isn't bound to your soul. Your Soulbound weapon is the "
				+ d.weaponType.name + ".");
			return;
		}
		details(player);
	}

	/** Last "not bound" notice per player (ms), so a fight doesn't spam it: at most once a minute. */
	private static final java.util.Map<Integer, Long> notBoundNotice = new java.util.HashMap<>();

	private static void notBound(Player player, SoulWeaponType wielded) {
		long now = System.currentTimeMillis();
		Long last = notBoundNotice.get(player.getUserId());
		if (last != null && now - last < 60_000)
			return;
		notBoundNotice.put(player.getUserId(), now);
		SoulWeaponData d = player.soulWeapon;
		player.sendMessage(TAG + "<col=c0281e>This " + wielded.name + " isn't bound to your soul, so it gathers no souls.</col> "
			+ (d == null ? "Type ::soulweapon to choose your own." : "Your Soulbound weapon is the " + d.weaponType.name + "."));
	}

	/** Check: the weapon's window (interface 1174), redrawn live by addXp while open. */
	public static void details(Player player) {
		if (player.soulWeapon == null) {
			player.sendMessage(TAG + "This weapon isn't bound to you. Type ::soulweapon to choose your own.");
			return;
		}
		SoulWeaponInterface.open(player);
	}

	static String specialName(SoulWeaponType type) {
		return specialName(type, 2);
	}

	/** The special of a look: the Ascended one (band 2) or the Radiant one (band 3). */
	static String specialName(SoulWeaponType type, int band) {
		if (band >= 3)
			return switch (type) {
				case MELEE -> "Radiant Sever";
				case RANGED -> "Crimson Eclipse";
				case MAGIC -> "Arcane Cataclysm";
			};
		return switch (type) {
			case MELEE -> "Soul Rend";
			case RANGED -> "Soul Volley";
			case MAGIC -> "Soul Nova";
		};
	}

	/** Swaps every copy of the old look for the new one, wherever it is: the item keeps its slot. */
	private static void evolve(Player player, int from, int to) {
		boolean done = false;
		for (Item item : player.getEquipment().getItems())
			if (item != null && item.getId() == from) {
				item.setId(to);
				done = true;
			}
		for (Item item : player.getInventory().getItems())
			if (item != null && item.getId() == from) {
				item.setId(to);
				done = true;
			}
		for (BankItem item : player.getBank().getItems())
			if (item != null && item.getId() == from) {
				item.setId(to);
				done = true;
			}
		if (done)
			player.sendMessage(TAG + HIGHLIGHT + "Your weapon's power surges: it is now the "
				+ io.ruin.cache.ObjType.get(to).name + "!</col>");
	}

	/** The tier reached at a total Soul XP. */
	static int tierFor(long soulXp) {
		int t = 1;
		while (t < MAX_TIER && soulXp >= TIER_XP[t + 1])
			t++;
		return t;
	}

	private static void info(Player player) {
		SoulWeaponData d = player.soulWeapon;
		if (d == null) {
			player.sendMessage(TAG + "You haven't chosen a Soulbound weapon yet. Type ::soulweapon to choose one.");
			return;
		}
		String next = d.tier >= MAX_TIER ? "max tier"
			: NumberUtils.formatNumber(d.soulXp) + " / " + NumberUtils.formatNumber(TIER_XP[d.tier + 1]) + " Soul XP to tier " + (d.tier + 1);
		player.sendMessage(TAG + d.weaponType.name + ": tier " + d.tier + " (" + next + "), " + d.availableSoulPoints
			+ " Soul Points unspent (" + SoulTreeInterface.spent(d) + " spent), " + NumberUtils.formatNumber(d.soulsHarvested)
			+ " souls harvested.");
	}

	// ---- combat ----

	/** Equipment bonus totalling: adds the tier bonuses of the player's own Soulbound weapon when it's wielded. */
	public static void addTierBonuses(Player player, Item weapon, int[] bonuses) {
		SoulWeaponData d = player.soulWeapon;
		if (d == null || weapon == null || !d.weaponType.isLook(weapon.getId()) || d.tier <= 1)
			return;
		int[] ten = tierTenBonus(d.weaponType);
		for (int i = 0; i < ten.length && i < bonuses.length; i++)
			bonuses[i] += bonusAt(d.weaponType, d.tier, i);
	}

	public static boolean isSpire(int itemId) {
		return SoulWeaponType.MAGIC.isLook(itemId);
	}

	/** The Spire's built-in spell max hit: (Magic / 3 - 1) x 0.40 at tier 1 .. x 1.15 at tier 10 .. x 1.45 at tier 15. */
	public static int spireMaxHit(Player player) {
		SoulWeaponData d = player.soulWeapon;
		int tier = d == null || d.weaponType != SoulWeaponType.MAGIC ? 1 : d.tier;
		int base = Math.max(1, player.getStats().get(StatType.Magic).currentLevel / 3 - 1);
		double mult = tier <= ASCENDED_CAP ? 0.40 + (1.15 - 0.40) * (tier - 1) / 9.0 : 1.15 + 0.06 * (tier - ASCENDED_CAP);
		return Math.max(1, (int) Math.round(base * mult));
	}
}

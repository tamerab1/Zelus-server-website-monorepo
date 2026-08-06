package io.ruin.model.content.loyaltytitles;

import io.ruin.Server;
import io.ruin.cache.ItemID;
import io.ruin.model.entity.player.KillCounter;
import io.ruin.model.entity.player.Player;
import io.ruin.model.entity.shared.listeners.LoginListener;
import io.ruin.model.inter.dialogue.MessageDialogue;
import io.ruin.model.inter.dialogue.YesNoDialogue;
import io.ruin.model.item.actions.impl.CompletionistCape;
import io.ruin.model.stat.StatType;
import io.ruin.model.var.VarPlayerRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

public class LoyaltyTitleManager {

	public static boolean isUnlocked(Player player, LoyaltyTitle title) {
		return player.unlockedLoyaltyTitles.contains(title.id);
	}

	public static void unlock(Player player, LoyaltyTitle title) {
		player.unlockedLoyaltyTitles.add(title.id);
	}

	public static void equip(Player player, LoyaltyTitle title) {
		player.equippedLoyaltyTitleId = title.id;
	}

	public static void clearEquipped(Player player) {
		player.equippedLoyaltyTitleId = -1;
	}

	public static LoyaltyTitle getEquipped(Player player) {
		if (player.equippedLoyaltyTitleId == -1)
			return null;
		return LoyaltyTitle.get(player.equippedLoyaltyTitleId);
	}

	/**
	 * Renders the player's equipped loyalty title wrapped around their name, or just their
	 * plain name if none is equipped.
	 */
	public static String getDisplayName(Player player) {
		LoyaltyTitle title = getEquipped(player);
		if (title == null)
			return player.getName();
		return title.preview(player.getName());
	}

	/**
	 * Attempts to select a title from the interface. Handles the locked/purchasable/unlocked
	 * branches exactly matching the source system's UX.
	 */
	public static void select(Player player, LoyaltyTitle title) {
		if (isUnlocked(player, title)) {
			equip(player, title);
			player.dialogue(new MessageDialogue("Your title has been updated."));
			return;
		}
		if (title.isPurchasable()) {
			int cost = title.cost;
			player.dialogue(new YesNoDialogue("Purchase Title", "Purchase the title \"" + stripTags(title.preview(player.getName())) + "\" for " + cost + " coins?",
				ItemID.COINS_995, cost, () -> {
					if (player.getInventory().getAmount(ItemID.COINS_995) < cost) {
						player.dialogue(new MessageDialogue("You don't have enough coins."));
						return;
					}
					player.getInventory().remove(ItemID.COINS_995, cost);
					unlock(player, title);
					equip(player, title);
					player.dialogue(new MessageDialogue("Your title has been updated."));
				}));
			return;
		}
		player.dialogue(new MessageDialogue("You have not yet unlocked this title.<br><br>Unlock condition: " + title.requirement));
	}

	private static String stripTags(String text) {
		return text.replaceAll("<[^>]*>", "");
	}

	// ------------------------------------------------------------------------------------
	// Achievement-based unlocking for non-purchasable titles.
	//
	// Purchasable titles (isPurchasable() == true) are handled entirely by select() above and
	// are never touched here. Every other title (id 30120 "the Gamebreaker" excepted - that one
	// is an explicit admin-only manual grant per its requirement text) gets a Predicate<Player>
	// below that mirrors its LoyaltyTitle.requirement string. Titles with no groundable source
	// of truth in the codebase (no tracked counter/flag exists for them) are intentionally left
	// out of CONDITIONS - see the loyalty-titles implementation report for the full list and why.
	// ------------------------------------------------------------------------------------

	/**
	 * Extension point set by the collectionlog module (which depends on kronos-server, so this
	 * class can't reference collectionlog types directly without a dependency cycle). Given a
	 * player and one or more collection-log names (LoyaltyTitle-adjacent collection logs, e.g.
	 * "Nex" or "K'ril Tsutsaroth"), returns true only if every named log is fully completed.
	 */
	public static BiPredicate<Player, String[]> collectionLogsComplete;

	private static boolean logComplete(Player player, String... logNames) {
		return collectionLogsComplete != null && collectionLogsComplete.test(player, logNames);
	}

	private static final long XP_200M = 200_000_000L;

	/** "Demonic Creatures" has no dedicated KillCounter category - grouped from the individual
	 *  demon-type NPCs KillCounter does track (demonic gorillas, jungle demons, tormented demons). */
	private static int demonicCreatureKills(Player player) {
		return player.demonicGorillaKills.getKills() + player.jungleDemonKills.getKills() + player.tormentedDemonKills.getKills();
	}

	/** "Abyssal Creatures" likewise has no dedicated category - grouped from the abyssal-type
	 *  NPCs KillCounter tracks (abyssal sire, abyssal demons). */
	private static int abyssalCreatureKills(Player player) {
		return player.abyssalSireKills.getKills() + player.abyssalDemonKills.getKills();
	}

	private static final Map<Integer, Predicate<Player>> CONDITIONS = buildConditions();

	private static Map<Integer, Predicate<Player>> buildConditions() {
		Map<Integer, Predicate<Player>> c = new HashMap<>();

		// --- A/A2. Boss kill counts (single + compound) -----------------------------------
		c.put(30033, p -> p.skotizoKills.getKills() >= 10);
		c.put(30044, p -> p.vetionKills.getKills() >= 250);
		c.put(30045, p -> p.abyssalSireKills.getKills() >= 250);
		c.put(30047, p -> p.zulrahKills.getKills() >= 500);
		c.put(30049, p -> p.zukKills.getKills() >= 1);
		c.put(30063, p -> p.corporealBeastKills.getKills() >= 250);
		c.put(30066, p -> p.alchemicalHydraKills.getKills() >= 250);
		c.put(30069, p -> p.thermonuclearSmokeDevilKills.getKills() >= 250);
		c.put(30071, p -> p.venenatisKills.getKills() >= 250);
		c.put(30072, p -> p.krakenKills.getKills() >= 250);
		c.put(30080, p -> p.callistoKills.getKills() >= 250);
		c.put(30081, p -> p.hesporiKills.getKills() >= 25);
		c.put(30102, p -> p.kingBlackDragonKills.getKills() >= 250);
		c.put(30110, p -> p.cerberusKills.getKills() >= 250);
		c.put(30115, p -> p.sarachnisKills.getKills() >= 250);
		c.put(30019, p -> p.kreeArraKills.getKills() >= 250);
		c.put(30023, p -> p.grotesqueGuardianKills.getKills() >= 100);
		c.put(30036, p -> p.zalcanoKills.getKills() >= 25);
		c.put(30012, p -> p.nexKills.getKills() >= 250);
		c.put(30086, p -> p.phantomMuspahKills.getKills() >= 250);
		c.put(30090, p -> p.giantMoleKills.getKills() >= 250);
		c.put(30099, p -> p.nightmareofAshihamaKills.getKills() >= 250);
		c.put(30142, p -> p.commanderZilyanaKills.getKills() >= 250);
		c.put(30143, p -> p.vorkathKills.getKills() >= 250);
		c.put(30105, p -> p.krilTsutsarothKills.getKills() >= 250);
		c.put(30169, p -> p.generalGraardorKills.getKills() >= 250);
		// Basilisk Knights has no separate counter from regular Basilisks: KillCounter binds
		// basiliskKills to any NPC whose name contains "basilisk" (substring match), which also
		// matches "Basilisk Knight". Using it is the closest available approximation.
		c.put(30043, p -> p.basiliskKills.getKills() >= 500);

		c.put(30005, p -> p.dagannothRexKills.getKills() >= 250 && p.dagannothPrimeKills.getKills() >= 250
			&& p.dagannothSupremeKills.getKills() >= 250);
		c.put(30046, p -> p.oborKills.getKills() >= 50 && p.bryophytaKills.getKills() >= 50);
		c.put(30062, p -> demonicCreatureKills(p) >= 1000);
		c.put(30155, p -> demonicCreatureKills(p) >= 2500);
		c.put(30060, p -> p.revenantKillcount >= 2500);
		c.put(30070, p -> abyssalCreatureKills(p) >= 1000);
		c.put(30104, p -> KillCounter.getTotalBossKills(p) >= 5000);
		// 30014 "Kill all Slayer Bosses 100 times" - skipped, no ungroundable "Slayer Bosses"
		// subset exists (see report).
		// 30091 "Kill the Mimic 25 times" - skipped, no Mimic kill tracking exists.
		// 30108 "Kill Phosani's Nightmare 250 times" - skipped, Phosani's Nightmare (hard mode)
		// isn't implemented in this codebase at all; nightmareKills/nightmareofAshihamaKills both
		// track the one regular Nightmare of Ashihama encounter (already used for 30099).
		// 30109 "Kill Vanstrom Klause 250 times" - skipped, no kill tracking exists.
		// 30159 "Kill 1000 Rats" - skipped, no generic "Rats" kill tracking exists.

		// --- B. Skill XP / level thresholds -------------------------------------------------
		c.put(30016, p -> p.getStats().get(StatType.Woodcutting).getCurrentLevel() >= 99);
		c.put(30017, p -> p.getStats().get(StatType.Farming).getExperience() >= XP_200M);
		c.put(30026, p -> p.getStats().get(StatType.Slayer).getExperience() >= XP_200M);
		c.put(30028, p -> p.getStats().get(StatType.Fletching).getCurrentLevel() >= 99);
		c.put(30034, p -> p.getStats().get(StatType.Slayer).getCurrentLevel() >= 99);
		c.put(30037, p -> p.getStats().get(StatType.Fishing).getExperience() >= XP_200M);
		c.put(30039, p -> p.getStats().get(StatType.Firemaking).getCurrentLevel() >= 99);
		c.put(30048, p -> p.getStats().get(StatType.Thieving).getCurrentLevel() >= 99);
		c.put(30057, p -> p.getStats().get(StatType.Firemaking).getExperience() >= XP_200M);
		c.put(30058, p -> p.getStats().get(StatType.Herblore).getExperience() >= XP_200M);
		c.put(30064, p -> p.getStats().get(StatType.Hunter).getCurrentLevel() >= 99);
		c.put(30068, p -> p.getStats().get(StatType.Farming).getCurrentLevel() >= 99);
		c.put(30085, p -> p.getStats().get(StatType.Cooking).getExperience() >= XP_200M);
		c.put(30087, p -> p.getStats().get(StatType.Thieving).getExperience() >= XP_200M);
		c.put(30089, p -> p.getStats().get(StatType.Fishing).getCurrentLevel() >= 99);
		c.put(30096, p -> p.getStats().get(StatType.Crafting).getExperience() >= XP_200M);
		c.put(30097, p -> p.getStats().get(StatType.Fletching).getExperience() >= XP_200M);
		c.put(30100, p -> p.getStats().get(StatType.Runecrafting).getExperience() >= XP_200M);
		c.put(30106, p -> p.getStats().get(StatType.Woodcutting).getExperience() >= XP_200M);
		c.put(30107, p -> p.getStats().get(StatType.Smithing).getExperience() >= XP_200M);
		c.put(30112, p -> p.getStats().get(StatType.Smithing).getCurrentLevel() >= 99);
		c.put(30126, p -> p.getStats().get(StatType.Agility).getExperience() >= 99_000_000L);
		c.put(30134, p -> p.getStats().get(StatType.Hunter).getExperience() >= XP_200M);
		c.put(30136, p -> p.getStats().get(StatType.Agility).getCurrentLevel() >= 99);
		c.put(30137, p -> p.getStats().get(StatType.Crafting).getCurrentLevel() >= 99);
		c.put(30145, p -> p.getStats().get(StatType.Mining).getExperience() >= XP_200M);
		c.put(30158, p -> p.getStats().get(StatType.Herblore).getCurrentLevel() >= 99);
		c.put(30161, p -> p.getStats().get(StatType.Cooking).getCurrentLevel() >= 99);
		c.put(30162, p -> p.getStats().get(StatType.Runecrafting).getCurrentLevel() >= 99);
		c.put(30164, p -> p.getStats().get(StatType.Mining).getCurrentLevel() >= 99);

		// --- C. Collection log completions --------------------------------------------------
		c.put(30008, p -> logComplete(p, "Dagannoth Kings"));
		c.put(30011, p -> logComplete(p, "Nex"));
		c.put(30021, p -> logComplete(p, "K'ril Tsutsaroth"));
		c.put(30024, p -> logComplete(p, "Kree'arra"));
		c.put(30027, p -> logComplete(p, "Chaos Elemental"));
		c.put(30056, p -> logComplete(p, "Kalphite Queen"));
		c.put(30077, p -> logComplete(p, "Cerberus"));
		c.put(30094, p -> logComplete(p, "Vorkath"));
		c.put(30117, p -> logComplete(p, "Bryophyta"));
		c.put(30122, p -> logComplete(p, "Phantom Muspah"));
		c.put(30123, p -> logComplete(p, "Commander Zilyana"));
		c.put(30125, p -> logComplete(p, "Giant Mole"));
		c.put(30128, p -> logComplete(p, "King Black Dragon"));
		c.put(30131, p -> logComplete(p, "Slayer"));
		c.put(30150, p -> logComplete(p, "General Graardor"));
		c.put(30165, p -> logComplete(p, "Obor"));
		c.put(30168, p -> logComplete(p, "Barrows Chests"));
		c.put(30054, p -> logComplete(p, "Kree'arra", "Commander Zilyana", "General Graardor", "K'ril Tsutsaroth"));
		c.put(30140, p -> logComplete(p, "Barrows Chests", "The Fight Caves", "The Gauntlet", "The Inferno", "Pest Control", "Wintertodt"));
		c.put(30141, p -> logComplete(p, "The Inferno", "The Fight Caves"));
		// "Obtain all pets" reuses the same "All Pets" collection log completion check.
		c.put(30172, p -> logComplete(p, "All Pets"));

		// --- D. Clue scroll counts -----------------------------------------------------------
		c.put(30153, p -> totalClues(p) >= 50);
		c.put(30050, p -> totalClues(p) >= 100);
		c.put(30052, p -> totalClues(p) >= 250);
		c.put(30092, p -> totalClues(p) >= 500);
		c.put(30149, p -> totalClues(p) >= 750);
		c.put(30067, p -> totalClues(p) >= 1000);
		c.put(30163, p -> totalClues(p) >= 1500);

		// --- E. PK / player kill counts -------------------------------------------------------
		c.put(30009, p -> VarPlayerRepository.PVP_KILLS.get(p) >= 250);
		c.put(30111, p -> VarPlayerRepository.PVP_KILLS.get(p) >= 100);
		c.put(30015, p -> VarPlayerRepository.PVP_KILLS.get(p) >= 750);
		c.put(30020, p -> VarPlayerRepository.PVP_KILLS.get(p) >= 1000);
		c.put(30074, p -> VarPlayerRepository.PVP_KILLS.get(p) >= 500);
		c.put(30084, p -> VarPlayerRepository.PVP_KILLS.get(p) >= 500);
		c.put(30088, p -> VarPlayerRepository.PVP_KILLS.get(p) >= 500);
		c.put(30113, p -> VarPlayerRepository.PVP_KILLS.get(p) >= 500);
		c.put(30114, p -> VarPlayerRepository.PVP_KILLS.get(p) >= 500);
		c.put(30130, p -> VarPlayerRepository.PVP_KILLS.get(p) >= 500);
		c.put(30133, p -> VarPlayerRepository.PVP_KILLS.get(p) >= 500);
		c.put(30138, p -> VarPlayerRepository.PVP_KILLS.get(p) >= 500);
		c.put(30139, p -> VarPlayerRepository.PVP_KILLS.get(p) >= 500);
		c.put(30166, p -> VarPlayerRepository.PVP_KILLS.get(p) >= 500);

		// --- F. Stakes / Duel Arena ------------------------------------------------------------
		c.put(30031, p -> p.duelWins >= 100);
		c.put(30167, p -> p.duelWins >= 1000);
		c.put(30083, p -> p.tournamentWins >= 10);

		// --- G. Slayer tasks ---------------------------------------------------------------------
		c.put(30075, p -> p.totalSlayerTasksCompleted >= 100);
		c.put(30013, p -> p.totalSlayerTasksCompleted >= 250);

		// --- H. Raid completions -------------------------------------------------------------
		c.put(30030, p -> p.chambersofXericKills.getKills() >= 50);
		c.put(30129, p -> p.theatreOfBloodKills.getKills() >= 100);
		c.put(30154, p -> p.theatreOfBloodKills.getKills() >= 25);
		c.put(30076, p -> p.theCorruptedGauntletKills.getKills() >= 100);
		c.put(30078, p -> p.theGauntletKills.getKills() >= 100);
		c.put(30132, p -> p.wintertodtKills.getKills() >= 25);
		// 30146 "Complete 50 Challenge Mode CoX" - skipped, Challenge Mode CoX isn't enabled in
		// this codebase ("Challenge mode is not currently available." in RecruitingBoard.java)
		// and there's no separate CM completion counter.

		// --- I. Ironman / Hardcore status ------------------------------------------------------
		c.put(30147, p -> p.getGameMode().isIronMan());
		c.put(30157, p -> p.getGameMode().isHardcoreIronman() || p.getGameMode().isHardcoreGroupIronman());
		c.put(30121, p -> p.getGameMode().isIronMan() && CompletionistCape.checkTotal99s(p));
		c.put(30040, p -> (p.getGameMode().isHardcoreIronman() || p.getGameMode().isHardcoreGroupIronman()) && CompletionistCape.checkTotal99s(p));

		// --- J. Playtime -----------------------------------------------------------------------
		c.put(30007, p -> TimeUnit.MILLISECONDS.toDays(p.playTime * (long) Server.tickMs()) >= 7);
		c.put(30152, p -> TimeUnit.MILLISECONDS.toDays(p.playTime * (long) Server.tickMs()) >= 14);

		// --- K. Mastery / completionist ----------------------------------------------------------
		c.put(30032, CompletionistCape::checkTotal99s);
		// 30022 "Tier 3 Completionist Cape" / 30038 "Tier 1 / Tier 2 Completionist Cape" -
		// skipped. This codebase's completionist cape system (CompletionistCape.java) has no
		// tier concept at all (one unified requirement set for every god-cape variant), so there's
		// no groundable way to tell these two titles apart.

		// --- L. Special one-offs -----------------------------------------------------------------
		c.put(30171, p -> p.uniqueDrops.getOrDefault(ItemID.INFERNAL_CAPE, 0) > 0);
		// 30006 "Login day 1 of launch" - skipped, no launch date / first-login tracking exists.
		// 30124 "3/1/2026" ("Veteran") - skipped, no account creation timestamp field exists.
		// 30059 "Entered 250 Abyss Rifts" - skipped, no abyss rift entry tracking exists.
		// 30148 "Gamble a Fire Cape" - skipped, no fire cape gamble tracking exists.
		// 30120 "the Gamebreaker" - intentionally excluded (admin-only manual grant, cost 0).

		return c;
	}

	private static int totalClues(Player player) {
		return player.beginnerClueCount + player.easyClueCount + player.medClueCount
			+ player.hardClueCount + player.eliteClueCount + player.masterClueCount;
	}

	/**
	 * Sweeps every non-purchasable, not-yet-unlocked title and unlocks any whose condition is
	 * now met, notifying the player. Cheap enough to call on every kill, login and logout - see
	 * the call sites in KillCounter#increment, Player#start (login) and Player#finish (logout/
	 * pre-save), which keep every category eventually-consistent without a dedicated hook in
	 * every subsystem that could move the underlying counters (XP gain, clue completion, etc).
	 */
	public static void checkAllUnlocks(Player player) {
		for (LoyaltyTitle title : LoyaltyTitle.all()) {
			if (title.isPurchasable() || isUnlocked(player, title))
				continue;
			Predicate<Player> condition = CONDITIONS.get(title.id);
			if (condition == null || !condition.test(player))
				continue;
			unlock(player, title);
			player.sendMessage("You've unlocked a new title: " + stripTags(title.preview(player.getName())) + "!");
		}
	}

	public static void register() {
		LoginListener.register(LoyaltyTitleManager::checkAllUnlocks);
	}
}

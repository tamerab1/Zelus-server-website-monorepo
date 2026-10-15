package io.ruin.model.inter.questtab.dashboard;

import io.ruin.Server;
import io.ruin.api.utils.NumberUtils;
import io.ruin.api.utils.TimeUtils;
import io.ruin.cache.ObjType;
import io.ruin.model.ScrollbarClientScript;
import io.ruin.model.World;
import io.ruin.model.activities.dailytasks.DailyTasksInterface;
import io.ruin.model.activities.tempevents.RevenantMaledictusManager;
import io.ruin.model.activities.wilderness.Hotspot;
import io.ruin.model.activities.wilderness.Wilderness;
import io.ruin.model.entity.npc.NPC;
import io.ruin.model.entity.npc.PetNPC;
import io.ruin.model.entity.npc.combatpet.CombatPets;
import io.ruin.model.entity.npc.combatpet.PetTier;
import io.ruin.model.entity.player.Player;
import io.ruin.model.content.gearloadouts.FreeGearLoadoutKits;
import io.ruin.model.content.gearloadouts.GearLoadoutHandler;
import io.ruin.model.content.gearloadouts.GearLoadoutInterface;
import io.ruin.model.content.gearloadouts.GearLoadoutPreset;
import io.ruin.model.inter.questtab.main.Achievements;
import io.ruin.model.inter.ClientInterfaceType;
import io.ruin.model.inter.ToplevelComponent;
import io.ruin.model.inter.dialogue.OptionsDialogue;
import io.ruin.model.inter.utils.Option;
import io.ruin.model.map.Position;
import io.ruin.model.skills.magic.spells.modern.ModernTeleport;
import io.ruin.model.inter.Interface;
import io.ruin.model.inter.InterfaceHandler;
import io.ruin.model.inter.actions.SimpleAction;
import io.ruin.model.inter.handlers.LootsTables;
import io.ruin.model.inter.questtab.JournalTab;
import io.ruin.model.inter.questtab.PlayersOnline;
import io.ruin.model.inter.questtab.dashboard.DashboardLayout.Layout;
import io.ruin.model.skills.slayer.SlayerUnlock;
import io.ruin.model.var.VarPlayerRepository;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The journal side-panel dashboards that replace the old 1086/848 summary pages (interfaces built by
 * .dev/cache-restore-tool/src/BuildDashboardInterfaces.java; component ids in {@link DashboardLayout}):
 * Character Summary (tab 1), Server Info (tab 2) and Wilderness (the old Adventure Paths slot, tab 4).
 * Achievement Diaries (tab 3) is unchanged. Values refresh every few ticks while a dashboard is shown.
 * <p>
 * The same machinery drives the PK Hub ({@link DashboardLayout#PK_HUB}), which replaces the vanilla Account
 * Management side tab (109) in {@link ToplevelComponent#ACCOUNT_MANAGEMENT_TAB_AREA}. Its stone icon and
 * tooltip come from the cache (game frames 161:51 / 164:45 / 548:55 -> sprite 912, enum 1141 key 8 -> "PK Hub").
 */
public final class QuestDashboards {

	/** Tab buttons on the journal container 629 (Achievement Diaries = 13 stays in JournalTab). */
	public static final int TAB_CHARACTER = 3, TAB_SERVER = 8, TAB_WILDERNESS = 23;
	/** varbit 8168 values that highlight the matching tab (script 2800 switch). */
	private static final int HIGHLIGHT_CHARACTER = 0, HIGHLIGHT_SERVER = 1, HIGHLIGHT_WILDERNESS = 4, HIGHLIGHT_ACHIEVEMENTS = 2;

	private static final int REFRESH_TICKS = 5;
	/** Side tabs stay loaded while other tabs are shown, so the PK Hub refreshes less often. */
	private static final int PK_HUB_REFRESH_TICKS = 10;
	private static final String PK_HUB_KEY = "pk_hub_refresh";
	/** Visible height of the scroll area: the 233px panel content minus the 22px title strip. */
	private static final int VISIBLE_HEIGHT = 211;
	private static final String SHOWN_KEY = "journal_dashboard";

	/** Filled by the economy-protection module: its ::spawn item search (whitelist included). */
	public static Consumer<Player> openSpawn = p -> p.sendMessage("Spawning is unavailable.");

	/** Filled by the collectionlog module (it depends on the server, not the other way round). */
	public static Consumer<Player> openCollectionLog = p -> p.sendMessage("The collection log is unavailable.");

	/** npc id -> server tick it respawns at (recorded by NPCCombat when it schedules a respawn). */
	public static final Map<Integer, Long> RESPAWN_AT = new ConcurrentHashMap<>();

	private static final int[] CALLISTO = {6503, 6609}, VETION = {6611, 6612}, VENENATIS = {6504, 6610};

	private static final String GREEN = "<col=50e050>", RED = "<col=ff5050>", GREY = "<col=9a9a9a>", GOLD = "<col=ffc35a>",
			END = "</col>";

	private QuestDashboards() {
	}

	/* ---------------- content: {label, value} per row, per section ---------------- */

	private static final List<List<Row>> CHARACTER_ROWS = List.of(
			List.of(row("Rank", QuestDashboards::rankTag),
					row("Donator", QuestDashboards::donatorTag),
					row("Game mode", p -> p.getGameMode().Name),
					row("Difficulty", p -> capitalise(p.getDifficulty().toString())),
					row("Combat pet", QuestDashboards::combatPet),
					row("Playtime", p -> TimeUtils.fromMsShort(p.playTime * Server.tickMs()))),
			List.of(row("Zelus points", p -> NumberUtils.formatNumber(p.getReasonPoints())),
					row("Perk points", p -> NumberUtils.formatNumber(p.perkPoints)),
					row("Vote points", p -> NumberUtils.formatNumber(p.getVotePoints())),
					row("Donator points", p -> NumberUtils.formatNumber(p.getDonatorPoints())),
					row("Achievement pts", p -> NumberUtils.formatNumber(p.getAchievementPoints())),
					row("PvM points", p -> NumberUtils.formatNumber(p.PvmPoints)),
					row("Boss points", p -> NumberUtils.formatNumber(p.bossPoints)),
					row("Slayer points", p -> NumberUtils.formatNumber(VarPlayerRepository.SLAYER_POINTS.get(p))),
					row("Tournament pts", p -> NumberUtils.formatNumber(p.tournamentPoints))),
			List.of(row("Drop rate", p -> GREEN + "+" + p.calculateDropRate() + "%" + END),
					row("Slayer task", QuestDashboards::slayerTask),
					row("Boss task", p -> p.bossSlayerName == null ? GREY + "None" + END
							: p.currentBossSlayerAmount + " " + p.bossSlayerName),
					row("Perk task", p -> p.currentPerkTask == null ? GREY + "None" + END : p.perkTaskCurrentAmount + " done"),
					row("Total donated", p -> "$" + NumberUtils.formatNumber(p.getTotalDonated()))),
			List.of(row("Double XP", p -> minutes(p.getDoubleExpRemaining() / 100)),
					row("Double drops", p -> minutes(p.doubleDropBonus.remaining() / 100)),
					row("Damage boost", p -> minutes(p.getDamageBoostRemaining() / 100)),
					row("Damage reduction", p -> minutes(p.getDamageReductionBoostRemaining() / 100)),
					row("Brew immunity", p -> minutes(p.getBrewImmunityRemaining() / 100)),
					row("Prayer drain -", p -> minutes(p.getPrayerBoostBonusRemaining() / 100)),
					row("5% drop rate", p -> minutes(p.getDropRateBoostRemaining() / 100))));

	private static final List<List<Row>> SERVER_ROWS = List.of(
			List.of(row("Players online", p -> GREEN + World.playerCount() + END),
					row("Staff online", p -> GREEN + World.staffCount() + END),
					row("Uptime", p -> Server.getUptime()),
					row("Server time", p -> Server.getTime()),
					row("Daily reset", p -> untilDailyReset())),
			List.of(row("Double XP", p -> onOff(World.doubleExpActive || World.xpMultiplier == 2)),
					row("Double PK points", p -> onOff(World.doublePkp)),
					row("Double slayer pts", p -> onOff(World.doubleSlayer)),
					row("Double pest pts", p -> onOff(World.doublePest))),
			List.of());

	private static final String[] SERVER_BUTTONS = {"Drop viewer", "Collection log", "Daily tasks", "Chest loot",
			"Auras", "Pet perks", "Vote", "Discord", "Store", "Website"};
	/** The small item icon on each dashboard button (same order as the buttons; 2026-10-15 bezel-button restyle). */
	/** Item ids; a negative value is a sprite id instead (BuildBrandIcons: 24526 Discord "Clyde", 24527 donator badge). */
	private static final int[] SERVER_ICONS = {526, 22711, 13069, 405, 13190, 13262, 2996, -24526, -24527, 11640};
	private static final int[] PK_HUB_ICONS = {4151, 11832, 8007, 2552, 12608, 10556, 22324};
	private static final int[] ACHIEVEMENT_ICONS = {13069};

	private static final List<List<Row>> WILDERNESS_ROWS = List.of(
			List.of(row("Location", p -> GOLD + Hotspot.ACTIVE.name + END),
					row("Counts for", p -> "Hotspot PK tasks")),
			List.of(row("Callisto", p -> bossStatus(CALLISTO)),
					row("Vet'ion", p -> bossStatus(VETION)),
					row("Venenatis", p -> bossStatus(VENENATIS)),
					row("Rev. Maledictus", p -> maledictusStatus())),
			List.of(row("Kills", p -> NumberUtils.formatNumber(VarPlayerRepository.PVP_KILLS.get(p))),
					row("Deaths", p -> NumberUtils.formatNumber(VarPlayerRepository.PVP_DEATHS.get(p))),
					row("K/D ratio", QuestDashboards::kdr),
					row("Current streak", p -> String.valueOf(p.currentKillSpree)),
					row("Best streak", p -> String.valueOf(p.highestKillSpree)),
					row("In wilderness now", p -> String.valueOf(Wilderness.players.size()))));

	private static final List<List<Row>> PK_HUB_ROWS = List.of(
			List.of(row("Combat level", p -> String.valueOf(p.getCombat().getLevel())),
					row("Kills", p -> NumberUtils.formatNumber(VarPlayerRepository.PVP_KILLS.get(p))),
					row("Deaths", p -> NumberUtils.formatNumber(VarPlayerRepository.PVP_DEATHS.get(p))),
					row("K/D ratio", QuestDashboards::kdr),
					row("Current streak", p -> String.valueOf(p.currentKillSpree)),
					row("Best streak", p -> String.valueOf(p.highestKillSpree))),
			// Division Rivals (io.ruin.model.content.rivals): the player's live rank
			List.of(row("Division", io.ruin.model.content.rivals.RivalsProfile::dashboardLine)),
			List.of(row("Hotspot", p -> GOLD + Hotspot.ACTIVE.name + END),
					row("Callisto", p -> bossStatus(CALLISTO)),
					row("Vet'ion", p -> bossStatus(VETION)),
					row("Venenatis", p -> bossStatus(VENENATIS)),
					row("Rev. Maledictus", p -> maledictusStatus())),
			List.of(row("PvP Mode", p -> p.isPvpMode() ? GREEN + "Yes" + END : RED + "No" + END)),
			List.of());

	/** PK hub buttons: [0] rivals, [1..4] free gear kits, [5] my loadouts, [6] spawn, [7..10] teleports. */
	private static final String PVP_MODE_REQUIRED = "You must be in PvP mode.";
	private static final int COMBAT_LOCK_TICKS = 16;

	private static final List<List<Row>> ACHIEVEMENT_ROWS = List.of(
			List.of(row("Completed", p -> tierProgress(p, Achievements.AchievementTypes.EASY))),
			List.of(row("Completed", p -> tierProgress(p, Achievements.AchievementTypes.MEDIUM))),
			List.of(row("Completed", p -> tierProgress(p, Achievements.AchievementTypes.HARD))),
			List.of(row("Completed", p -> tierProgress(p, Achievements.AchievementTypes.ELITE))),
			List.of(row("Total", p -> tierProgress(p, null))));
	private static final Achievements.AchievementTypes[] ACHIEVEMENT_TIERS = {Achievements.AchievementTypes.EASY,
			Achievements.AchievementTypes.MEDIUM, Achievements.AchievementTypes.HARD, Achievements.AchievementTypes.ELITE};

	private static final Position EDGEVILLE = new Position(3100, 3518, 0), FEROX = new Position(3130, 3631, 0),
			MAGE_BANK = new Position(2539, 4715, 0);

	/* ---------------- opening / switching ---------------- */

	public static void register() {
		// the 629 tab buttons themselves are wired in JournalTab.init (it owns that handler)
		// row clicks: Character rows are said out loud (as the old summary did); the task rows teleport to the task
		InterfaceHandler.register(DashboardLayout.CHARACTER.id(), h -> {
			int[][] labels = DashboardLayout.CHARACTER.labels();
			for (int s = 0; s < labels.length; s++)
				for (int r = 0; r < labels[s].length; r++) {
					Row row = CHARACTER_ROWS.get(s).get(r);
					h.actions[labels[s][r]] = (SimpleAction) p -> p.forceText("My " + row.label.toLowerCase() + ": "
							+ row.value.apply(p).replaceAll("<[^>]*>", ""));
				}
			h.actions[labels[2][1]] = (SimpleAction) JournalTab::teleportToSlayerTask;
			h.actions[labels[2][2]] = (SimpleAction) JournalTab::teleportToBossTask;
		});
		InterfaceHandler.register(DashboardLayout.SERVER.id(), h -> {
			int[][] labels = DashboardLayout.SERVER.labels();
			h.actions[labels[0][0]] = (SimpleAction) PlayersOnline::open;
			h.actions[labels[0][1]] = (SimpleAction) JournalTab::sendStaffOnline;
			int[] buttons = DashboardLayout.SERVER.buttons();
			h.actions[buttons[0]] = (SimpleAction) p -> p.getDropViewer().open(p);
			h.actions[buttons[1]] = (SimpleAction) p -> openCollectionLog.accept(p);
			h.actions[buttons[2]] = (SimpleAction) p -> new DailyTasksInterface().open(p);
			h.actions[buttons[3]] = (SimpleAction) p -> p.getLootsViewer().updateInterface(p, LootsTables.ADVANCED_MYSTERY_BOX);
			h.actions[buttons[4]] = (SimpleAction) io.ruin.model.content.bonds.BondInterface::open;
			h.actions[buttons[5]] = (SimpleAction) io.ruin.model.item.actions.impl.pet.perk.PetPerksInterface::open;
			h.actions[buttons[6]] = (SimpleAction) p -> p.openUrl("Vote", "https://zelusrsps.com/vote");
			h.actions[buttons[7]] = (SimpleAction) p -> p.openUrl("Discord", "https://discord.gg/sVrcatBdyG");
			h.actions[buttons[8]] = (SimpleAction) p -> p.openUrl("Donation", "https://zelusrsps.com/store");
			h.actions[buttons[9]] = (SimpleAction) p -> p.openUrl("Website", "https://zelusrsps.com");
		});
		io.ruin.model.content.gearloadouts.BattlePresets.register();
		InterfaceHandler.register(DashboardLayout.PK_HUB.id(), h -> {
			int[] b = DashboardLayout.PK_HUB.buttons();
			h.actions[b[0]] = (SimpleAction) io.ruin.model.content.rivals.DivisionRivalsInterfaceManager::openReal;
			// PvP presets: one button to the Battle Presets screen (kits, My loadouts, Spawn; PvP Mode rules inside)
			h.actions[b[1]] = (SimpleAction) io.ruin.model.content.gearloadouts.BattlePresets::open;
			h.actions[b[2]] = (SimpleAction) p -> ModernTeleport.teleport(p, EDGEVILLE);
			h.actions[b[3]] = (SimpleAction) p -> ModernTeleport.teleport(p, FEROX);
			h.actions[b[4]] = (SimpleAction) p -> ModernTeleport.teleport(p, MAGE_BANK);
			h.actions[b[5]] = (SimpleAction) p -> p.dialogue(new OptionsDialogue(
					"Teleport into the " + Hotspot.ACTIVE.name + " hotspot (Wilderness)?",
					new Option("Yes, take me there.", () -> ModernTeleport.teleport(p,
							Hotspot.ACTIVE.bounds.randomX(), Hotspot.ACTIVE.bounds.randomY(), 0)),
					new Option("No.")));
			// Fun PK: the same teleport as the Teleports screen's Wilderness > Fun PK
			h.actions[b[6]] = (SimpleAction) p -> {
				p.teleportInterface.currentTeleport = io.ruin.model.inter.handlers.ServerTeleports.FUN_PK;
				p.teleportInterface.startTeleport(p);
			};
			int[][] labels = DashboardLayout.PK_HUB.labels();
			h.actions[labels[2][0]] = (SimpleAction) p -> io.ruin.data.impl.Help.open(p, "hotspots");
		});
		InterfaceHandler.register(DashboardLayout.ACHIEVEMENTS.id(), h ->
				h.actions[DashboardLayout.ACHIEVEMENTS.buttons()[0]] = (SimpleAction) JournalTab::openAchievementInterface);
		InterfaceHandler.register(DashboardLayout.WILDERNESS.id(), h -> {
			int[][] labels = DashboardLayout.WILDERNESS.labels();
			h.actions[labels[0][0]] = (SimpleAction) p -> io.ruin.data.impl.Help.open(p, "hotspots");
		});
	}

	/**
	 * Opens the PK Hub in the Account Management side-tab slot (login / display-mode change) and keeps it
	 * fresh. Side tabs stay loaded, so this loops for as long as the player is online and the hub is open.
	 */
	public static void openPkHub(Player player) {
		Layout layout = DashboardLayout.PK_HUB;
		player.openInterface(ToplevelComponent.ACCOUNT_MANAGEMENT_TAB_AREA, layout.id());
		fill(player, layout, true);
		sendScrollbar(player, layout);
		int token = player.<Integer>get(PK_HUB_KEY, 0) + 1;
		player.set(PK_HUB_KEY, token);
		player.addEvent(e -> {
			while (true) {
				e.delay(PK_HUB_REFRESH_TICKS);
				if (!player.isOnline() || player.<Integer>get(PK_HUB_KEY, 0) != token || !player.isVisibleInterface(layout.id()))
					return;
				fill(player, layout, false);
			}
		});
	}

	/** Shown when the journal tab area opens (login / display-mode change). */
	public static void openDefault(Player player) {
		show(player, DashboardLayout.CHARACTER);
	}

	public static void show(Player player, Layout layout) {
		player.currentSection = JournalTab.Section.DASHBOARD; // stops JournalTab's 30-tick refresh reopening old pages
		VarPlayerRepository.QUEST_ACTIVE_TAB.set(player, highlight(layout));
		player.getPacketSender().openSubInterface(layout.id(), Interface.QUEST_TAB, 28, ClientInterfaceType.OVERLAY);
		player.set(SHOWN_KEY, layout.id());
		fill(player, layout, true);
		sendScrollbar(player, layout);

		// live values (timers, counts) while this dashboard stays the one shown
		player.addEvent(e -> {
			while (true) {
				e.delay(REFRESH_TICKS);
				if (!player.isOnline() || player.<Integer>get(SHOWN_KEY, -1) != layout.id()
						|| !player.isVisibleInterface(layout.id()))
					return;
				fill(player, layout, false);
			}
		});
	}

	private static void sendScrollbar(Player player, Layout layout) {
		int count = layout.contentHeight() <= VISIBLE_HEIGHT ? 0
				: (int) Math.ceil((layout.contentHeight() - VISIBLE_HEIGHT / 3D) / 15D);
		ScrollbarClientScript.create()
				.interfaceId(layout.id())
				.containerId(DashboardLayout.SCROLL)
				.scrollbarChildId(DashboardLayout.SCROLLBAR)
				.childrenCount(count)
				.withDarkGraphics()
				.build()
				.send(player);
	}

	private static int highlight(Layout layout) {
		if (layout == DashboardLayout.SERVER)
			return HIGHLIGHT_SERVER;
		if (layout == DashboardLayout.WILDERNESS)
			return HIGHLIGHT_WILDERNESS;
		if (layout == DashboardLayout.ACHIEVEMENTS)
			return HIGHLIGHT_ACHIEVEMENTS;
		return HIGHLIGHT_CHARACTER;
	}

	private static void fill(Player player, Layout layout, boolean labelsToo) {
		var ps = player.getPacketSender();
		List<List<Row>> rows = layout == DashboardLayout.SERVER ? SERVER_ROWS
				: layout == DashboardLayout.WILDERNESS ? WILDERNESS_ROWS
				: layout == DashboardLayout.PK_HUB ? PK_HUB_ROWS
				: layout == DashboardLayout.ACHIEVEMENTS ? ACHIEVEMENT_ROWS : CHARACTER_ROWS;
		for (int s = 0; s < layout.labels().length; s++) {
			for (int r = 0; r < layout.labels()[s].length; r++) {
				Row row = rows.get(s).get(r);
				if (labelsToo)
					ps.sendString(layout.id(), layout.labels()[s][r], row.label);
				String value;
				try {
					value = row.value.apply(player);
				} catch (Exception ex) {
					value = GREY + "-" + END;
				}
				ps.sendString(layout.id(), layout.values()[s][r], fit(row.label, value));
			}
		}
		String[] buttons = layout == DashboardLayout.SERVER ? SERVER_BUTTONS
				: layout == DashboardLayout.PK_HUB ? pkHubButtons(player)
				: layout == DashboardLayout.ACHIEVEMENTS ? new String[]{"View all achievements"} : null;
		if (buttons != null && (labelsToo || layout == DashboardLayout.PK_HUB))
			for (int b = 0; b < layout.buttonTexts().length; b++)
				ps.sendString(layout.id(), layout.buttonTexts()[b], buttons[b]);
		int[] icons = layout == DashboardLayout.SERVER ? SERVER_ICONS : layout == DashboardLayout.PK_HUB ? PK_HUB_ICONS
				: layout == DashboardLayout.ACHIEVEMENTS ? ACHIEVEMENT_ICONS : null;
		if (icons != null && labelsToo)
			for (int b = 0; b < layout.buttonIcons().length && b < icons.length; b++)
				if (icons[b] < 0)
					ps.setGraphic(layout.id(), layout.buttonIcons()[b], -icons[b]);
				else
					ps.sendItem(layout.id(), layout.buttonIcons()[b], icons[b], 1);
		if (layout == DashboardLayout.ACHIEVEMENTS)
			for (int t = 0; t < ACHIEVEMENT_TIERS.length; t++) {
				int[] done = achievementCounts(player, ACHIEVEMENT_TIERS[t]);
				int width = done[1] == 0 ? 0 : DashboardLayout.BAR_WIDTH * done[0] / done[1];
				ps.sendClientScript(10533, "Ii", layout.id() << 16 | layout.bars()[t], width);
			}
	}

	/* ---------------- value helpers ---------------- */

	private record Row(String label, Function<Player, String> value) {
	}

	private static Row row(String label, Function<Player, String> value) {
		return new Row(label, value);
	}

	/** Rows are ~30 characters wide: the value gets what the label leaves (tags don't count). */
	private static String fit(String label, String value) {
		int room = 29 - label.length();
		String plain = value.replaceAll("<[^>]*>", "");
		if (plain.length() <= room || room < 4)
			return value;
		return plain.substring(0, room - 2) + "..";
	}

	/* ---------------- PK hub presets ---------------- */

	private static String[] pkHubButtons(Player player) {
		String[] b = new String[7];
		b[0] = "Rivals ladder";
		b[1] = (player.isPvpMode() ? "<col=ff981f>" : GREY) + "Open Battle Presets";
		b[2] = "Edgeville";
		b[3] = "Ferox Enclave";
		b[4] = "Mage Bank";
		b[5] = "Hotspot";
		b[6] = "Fun PK";
		return b;
	}

	public static boolean requirePvpMode(Player player) {
		if (player.isPvpMode())
			return true;
		player.sendMessage(PVP_MODE_REQUIRED);
		return false;
	}

	public static void loadFreeKit(Player player, int index) {
		if (!requirePvpMode(player))
			return;
		List<GearLoadoutPreset> kits = FreeGearLoadoutKits.getKits();
		if (index >= kits.size()) {
			player.sendMessage("That preset slot is empty.");
			return;
		}
		// a side tab is always one click away -- never let it re-gear someone mid-fight
		if (player.wildernessLevel > 0) {
			player.sendMessage("You can't load a preset in the Wilderness.");
			return;
		}
		if (player.getCombat().isAttacking(COMBAT_LOCK_TICKS) || player.getCombat().isDefending(COMBAT_LOCK_TICKS)) {
			player.sendMessage("You can't load a preset while in combat.");
			return;
		}
		GearLoadoutHandler.spawnFreeKit(player, kits.get(index), false); // stay on the Battle Presets screen
	}

	private static String shorten(String s, int max) {
		if (s == null)
			return "-";
		return s.length() <= max ? s : s.substring(0, max - 2) + "..";
	}

	/* ---------------- achievements ---------------- */

	/** {completed, total} for a tier, or every tier when {@code tier} is null. */
	private static int[] achievementCounts(Player player, Achievements.AchievementTypes tier) {
		int done = 0, total = 0;
		for (Achievements a : Achievements.VALUES) {
			if (tier != null && a.getAchievementType() != tier)
				continue;
			total++;
			if (player.getAchievementCurrentProgress(a) >= a.getCompletionAmount())
				done++;
		}
		return new int[]{done, total};
	}

	private static String tierProgress(Player player, Achievements.AchievementTypes tier) {
		int[] c = achievementCounts(player, tier);
		int pct = c[1] == 0 ? 0 : c[0] * 100 / c[1];
		return (c[0] == c[1] && c[1] > 0 ? GREEN : "") + c[0] + " / " + c[1] + " (" + pct + "%)" + (c[0] == c[1] && c[1] > 0 ? END : "");
	}

	/* ---------------- rank colours (same as ::yell and the donator broadcasts) ---------------- */

	private static String rankTag(Player player) {
		io.ruin.model.entity.player.PlayerGroup g = player.getPrimaryGroup();
		String colour = switch (g) {
			case OWNER -> "ee82ee";
			case DEVELOPER -> "420420";
			case ADMINISTRATOR, COMMUNITY_ADMIN -> "800080";
			case HEAD_MODERATOR -> "e20b76";
			case MODERATOR -> "696969";
			case SUPPORT -> "0040ff";
			default -> null;
		};
		if (colour == null)
			return g.Name;
		return "<img=" + g.clientImgId + "><col=" + colour + ">" + g.Name + END;
	}

	private static String donatorTag(Player player) {
		io.ruin.model.entity.player.SecondaryGroup g = player.getSecondaryGroup();
		String colour = switch (g) {
			case SUPREME_DONATOR -> "fc7306";
			case LEGENDARY_DONATOR -> "e121f7";
			case PLATINUM_DONATOR -> "78757a";
			case GOLD_DONATOR -> "f7e521";
			case NOBLE_DONATOR -> "8d501b";
			case ELITE_DONATOR -> "27ae60";
			case SUPER_DONATOR -> "1e90ff";
			case DONATOR -> "db5b58";
			default -> null;
		};
		if (colour == null)
			return GREY + "None" + END;
		return g.tag() + "<col=" + colour + ">" + g.Name + END;
	}

	private static String combatPet(Player player) {
		if (!(player.familiarNPC instanceof PetNPC pet) || pet.isRemoved() || !CombatPets.isCombatPet(pet.petType))
			return GREY + "None" + END;
		PetTier tier = CombatPets.tierOf(pet.petType);
		String name = ObjType.get(pet.petType.itemId).name;
		return tier == null ? name : name.replaceAll("\\s*\\(T\\d\\)$", "") + " T" + tier.number();
	}

	private static String slayerTask(Player player) {
		int amount = VarPlayerRepository.SLAYER_TASK_AMOUNT.get(player);
		if (amount <= 0)
			return GREY + "None" + END;
		return amount + " " + SlayerUnlock.taskName(player, VarPlayerRepository.SLAYER_TASK.get(player));
	}

	private static String kdr(Player player) {
		int kills = VarPlayerRepository.PVP_KILLS.get(player), deaths = VarPlayerRepository.PVP_DEATHS.get(player);
		return deaths == 0 ? String.valueOf(kills) : String.format("%.2f", (double) kills / deaths);
	}

	private static String minutes(long mins) {
		return mins <= 0 ? GREY + "-" + END : GREEN + NumberUtils.formatNumber(mins) + "m" + END;
	}

	private static String onOff(boolean on) {
		return on ? GREEN + "On" + END : GREY + "Off" + END;
	}

	private static String capitalise(String s) {
		s = s.toLowerCase();
		return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}

	/** Same reset as DailyTasksInterface: the next local midnight. */
	private static String untilDailyReset() {
		ZoneId zone = ZoneId.systemDefault();
		Duration left = Duration.between(Instant.now(), LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant());
		return left.toHours() + "h " + left.toMinutesPart() + "m";
	}

	private static final Map<Integer, String> BOSS_STATUS = new ConcurrentHashMap<>();
	private static long bossStatusTick = -1;

	/** Cached per server tick: many players refresh at once, the NPC scan only needs to happen once. */
	private static synchronized String bossStatus(int[] ids) {
		long now = Server.currentTick();
		if (now != bossStatusTick) {
			BOSS_STATUS.clear();
			bossStatusTick = now;
		}
		return BOSS_STATUS.computeIfAbsent(ids[0], k -> computeBossStatus(ids));
	}

	private static String computeBossStatus(int[] ids) {
		for (NPC npc : World.npcsSlots()) {
			if (npc == null || npc.isRemoved())
				continue;
			for (int id : ids) {
				if (npc.getId() == id && npc.getCombat() != null && !npc.getCombat().isDead()) {
					int pct = (int) Math.round(npc.getHp() * 100D / Math.max(1, npc.getMaxHp()));
					return GREEN + "Alive" + END + " (" + pct + "%)";
				}
			}
		}
		long now = Server.currentTick();
		for (int id : ids) {
			Long at = RESPAWN_AT.get(id);
			if (at != null && at > now)
				return RED + "Respawn " + Math.max(1, (at - now) * Server.tickMs() / 1000) + "s" + END;
		}
		return GREY + "Not spawned" + END;
	}

	private static String maledictusStatus() {
		if (World.revenantMaledictusEvent instanceof RevenantMaledictusManager m && m.isBossAlive()) {
			String where = m.bossLocation();
			return GREEN + "Active" + END + (where != null ? " (" + where + ")" : "");
		}
		return GREY + "Not spawned" + END;
	}

}

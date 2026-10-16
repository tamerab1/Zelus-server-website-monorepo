package io.ruin.model.inter.handlers;

import io.ruin.cache.ItemID;
import io.ruin.cache.NPCType;
import io.ruin.cache.ObjType;
import io.ruin.model.ScrollbarClientScript;
import io.ruin.model.activities.wilderness.Wilderness;
import io.ruin.model.entity.player.Player;
import io.ruin.model.entity.shared.listeners.LoginListener;
import io.ruin.model.inter.InterfaceHandler;
import io.ruin.model.inter.ToplevelComponent;
import io.ruin.model.inter.actions.SimpleAction;
import io.ruin.model.item.Item;
import io.ruin.network.PacketSender;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static io.ruin.model.inter.handlers.ServerTeleports.*;

/**
 * The Zelus Teleport Nexus (interface 1152, built by .dev/cache-restore-tool/src/BuildTeleportInterface.java). It replaces
 * the old paged teleport interface 851 with three panes: categories on the left, a Search button and the destination
 * cards in the middle (name, favourite star, danger tag / note, Teleport button), and a preview of the selected
 * destination on the right (animated model at the old interface's zoom presets, description, notable drops,
 * requirement, Teleport, favourite). The destinations and their order are the ones the old pages listed; teleporting
 * still goes through {@link TeleInterface#startTeleport} (instanced bosses, the Galvek / Ophidia lair choice,
 * achievements, lastServerTeleport). Favourites (max 5) are saved with the account (teleportFavouriteNames).
 */
public final class TeleportNexus {

	public static final int INTERFACE = 1152;
	private static final int CLOSE = 2, TITLE = 3, SUB_HEADER = 4;
	/** Categories: box, border, gold outline x2, icon, label. */
	private static final int FIRST_CATEGORY = 10, CATEGORY_STRIDE = 6, CATEGORY_OUTLINE = 2, CATEGORY_ICON = 4, CATEGORY_LABEL = 5;
	private static final int SEARCH = 57, SEARCH_LABEL = 58, SCROLL = 59, SCROLLBAR = 60;
	/** Cards: layer, box, border, gold outline x2, name, tag/note, Teleport button, its label, star (gold), star (grey). */
	private static final int FIRST_ROW = 61, ROW_STRIDE = 11, ROW_BOX = 1, ROW_OUTLINE = 3, ROW_NAME = 5, ROW_LINE = 6,
		ROW_TELEPORT = 7, ROW_STAR_ON = 9, ROW_STAR_OFF = 10, ROWS = 80;
	private static final int ROW_PITCH = 40, SCROLL_HEIGHT = 210;
	/** Preview pane. */
	private static final int PREVIEW_EMPTY = 946, PREVIEW_NAME = 947, PREVIEW_TAG = 948, FIRST_MODEL = 949, MODELS = 6,
		PREVIEW_DESC = 955, FIRST_DROP = 956, DROP_STRIDE = 3, DROP_ITEM = 2, DROPS = 4, PREVIEW_REQ = 968,
		PREVIEW_TELEPORT = 969, PREVIEW_STAR_ON = 971, PREVIEW_STAR_OFF = 972;
	private static final int MAX_FAVOURITES = 5;

	private static final String GOLD = "<col=ffc35a>", GREY = "<col=9a9a9a>", WHITE = "<col=ffffff>", GREEN = "<col=00ff00>",
		RED = "<col=ff0000>", ORANGE = "<col=ff981f>", CYAN = "<col=4fd1ff>", END = "</col>";

	enum Category {
		FAVOURITES("Favorites", -1), MONSTERS("Monsters", ItemID.RUNE_SCIMITAR), BOSSES("Bosses", ItemID.VORKATHS_HEAD_21907),
		MINIGAMES("Minigames", ItemID.FIRE_CAPE), WILDERNESS("Wilderness", ItemID.SKULL), SKILLING("Skilling", ItemID.DRAGON_PICKAXE),
		CITIES("Cities", ItemID.VARROCK_TELEPORT), SEARCH("Search", -1);
		final String label;
		/** The item drawn as the category's icon (Favourites uses the gold star sprite from the cache). */
		final int iconItem;

		Category(String label, int iconItem) {
			this.label = label;
			this.iconItem = iconItem;
		}
	}

	/** The 7 category buttons; Search has its own button above the list. */
	private static final Category[] BUTTONS = {Category.FAVOURITES, Category.MONSTERS, Category.BOSSES, Category.MINIGAMES,
		Category.WILDERNESS, Category.SKILLING, Category.CITIES};
	private static final Category[] CATEGORIES = Category.values();

	/** A destination in a list, with the skill/section it belongs to (shown in its note) where that helps. */
	record Entry(ServerTeleports teleport, String section) {
	}

	/* The destinations of each category, in the order the old interface's pages listed them. */
	private static final List<Entry> MONSTERS_LIST = list(null, ROCK_CRABS, SAND_CRABS, AMMONITE_CRABS, YAKS, CHICKENS, CHAOS_DRUID,
		EXPERIMENTS, CATALYSTS, SHAMANS, VYREWATCH_SENTINELS, BASILISK_KNIGHTS, ARMOURED_ZOMBIES);
	private static final List<Entry> BOSSES_LIST = list(null, RELIQUARY, SYLVAROTH, CINDERMAW, MAD_ANGEL, GWD, VORKATH, ZULRAH, GALVEK, CORP, KQ, DKS, KRAKEN, HYDRA, CERBERUS,
		SMOKE_DEVIL, SIRE, VENENATIS, CALLISTO, VETION, KBD, GIANT_MOLE, SKOTIZO, DEMONIC_GORILLA, ARGENTAVIS, NEX, NIGHTMARE,
		SARACHNIS, OPHIDIA, PHANTOM_MUSPAH, LEVIATHAN, DUKE_SUCELLUS, VARDORVIS, WHISPERER, MALAKAR, SOL_HEREDIT,
		GROTESQUE_GUARDIANS, ARAXXOR, SCURRIUS, BALANCE_ELEMENTAL, JUDGE_OF_YAMA, DOOM_OF_MOKHAIOTL, TORMENTED_DEMON);
	private static final List<Entry> MINIGAMES_LIST = list(null, DRACO, CHAMBER_OF_ASCENSION, CHAMBERS_OF_XERIC, WARRIORS_GUILD, BARROWS, FIGHT_CAVES, INFERNO,
		PEST_CONTROL, DOMINION_OF_ECHOES, WINTERTODT, DUEL_ARENA, THE_GAUNTLET, THEATRE_OF_BLOOD, TOMBS_OF_AMASCUT);
	private static final List<Entry> WILDERNESS_LIST = list(null, EAST_DRAGONS, WEST_DRAGONS, MAGE_BANK, REVENANTS, SCORPIA,
		CHAOS_FANATIC, CRAZY_ARCH, CHAOS_ELE, WILDY_RESOURCE_AREA, CHAOS_ALTAR, FUN_PK);
	private static final List<Entry> CITIES_LIST = list(null, VARROCK, LUMBRIDGE, FALADOR, SEERS, DRAYNOR, ARDOUGNE, CANIFIS,
		MOS_LE_HARMLESS, SHILO_VILLAGE, RELLEKA, BURTHORPE, LUNAR_ISLE, POLLNIVNEACH, GNOME_STRONGHOLD, YANILLE, AL_KHARID,
		PORT_SARIM, BRIMHAVEN, APE_ATOLL, PRIFDDINAS);
	private static final List<Entry> SKILLING_LIST = new ArrayList<>();

	static {
		// the old Skilling > Slayer pages are monster-hunting dungeons: they live under Monsters here
		MONSTERS_LIST.addAll(list("Slayer", TAVERLEY_DUNGEON, SLAYER_TOWER, STRONGHOLD_CAVE, KOUREND_CATACOMBS, CAVE_HORRORS,
			FREMENNIK_SLAYER_CAVE, MOUNT_KARUULM, ANCIENT_CAVERN, FOSSIL_ISLAND_WYVERN_CAVE, SKELETAL_WYVERN, SMOKE_DEVILS_CAVE,
			BRIMHAVEN_DUNGEON, DARK_BEAST, LUMMY_DUNGEON, ARDY_DUNGEON, LITHKREN, ISLE_OF_SOULS, GIANTS_DEN, SOURHOG));
		SKILLING_LIST.addAll(list("Mining", MINING_GUILD, MOTHERLOAD_MINE, AMETHYST_MINE, AL_KHARID_MINE, VARROCK_EAST_MINE,
			VARROCK_WEST_MINE));
		SKILLING_LIST.addAll(list("Woodcutting", WOODCUTTING_GUILD, HARDWOOD_GROVE, SEERS));
		SKILLING_LIST.addAll(list("Fishing", CATHTERBY, FISHING_GUILD, OTTOS_GROTTO, PISCATORIS, ANGLERFISH_SPOT, KARAMBWAN_SPOT));
		SKILLING_LIST.addAll(list("Runecrafting", ABYSS, ZMI, ARCEUUS_ESSENCE_MINE, BLOOD_ALTAR, SOUL_ALTAR, WRATH_ALTAR));
		SKILLING_LIST.addAll(list("Agility", GNOME_STRONGHOLD_COURSE, DRAYNOR_ROOFTOP_COURSE, AL_KHARID_ROOFTOP_COURSE,
			VARROCK_ROOFTOP_COURSE, CANIFIS_ROOFTOP_COURSE, FALADOR_ROOFTOP_COURSE, SEERS_ROOFTOP_COURSE,
			POLLNIVNEACH_ROOFTOP_COURSE, RELLEKA_ROOFTOP_COURSE, ARDOUGNE_ROOFTOP_COURSE, WILDERNESS_AGILITY_COURSE));
		SKILLING_LIST.addAll(list("Thieving", EDGEVILLE_MARKETPLACE));
		SKILLING_LIST.addAll(list("Hunter", CRIMSON_SWIFTS, DESERT_HUNTING_GROUND, TROPICAL_WAGTAIL, SWAMP_LIZARDS, FALCONRY,
			RED_CHINCHOMPAS, BLACK_CHINCHOMPA, RED_SALAMANDER, BLACK_SALAMANDER, PURO_PURO, BIRDHOUSES));
		SKILLING_LIST.addAll(list("Farming", FARMING_GUILD, KOUREND_ALLOTMENT, ARDOUGNE_ALLOTMENT, CATHERBY_ALLOTMENT,
			FALADOR_ALLOTMENT, PHASMATYS_ALLOTMENT, CHAMPIONS_GUILD_BUSH, RIMMINGTON_BUSH, ARDOUGNE_BUSH, ETCETERIA_BUSH,
			HOSIDIUS_VINERY, SEAWEED_PATCH, LUMBRIDGE_TREE_PATCH, VARROCK_TREE_PATCH, FALADOR_TREE_PATCH, TAVERLEY_TREE_PATCH,
			GNOME_STRONGHOLD_TREE_PATCH, GNOME_STRONGHOLD_FRUIT_TREE_PATCH, CATHERBY_FRUIT_TREE_PATCH,
			TREE_GNOME_MAZE_FRUIT_TREE_PATCH, BRIMHAVEN_FRUIT_TREE_PATCH, LLETYA_FRUIT_TREE_PATCH, ETCETERIA_SPIRIT_TREE_PATCH,
			PORT_SARIM_SPIRIT_TREE_PATCH, BRIMHAVEN_SPIRIT_TREE_PATCH, AL_KHARID_CACTUS_PATCH, HARDWOOD_TREE_PATCHES,
			MUSHROOM_PATCH, LUMBRIDGE_HOPS, SEERS_HOPS, YANILLE_HOPS, ENTRANA_HOPS));
	}

	/**
	 * A representative NPC for destinations without a model of their own. Only single-model NPCs work here (an
	 * interface model shows one cache model), so e.g. Barrows shows Ahrim: Dharok is 6 armour models.
	 */
	private static final java.util.Map<ServerTeleports, Integer> FALLBACK_NPC = new java.util.EnumMap<>(java.util.Map.of(
		BARROWS, 1672,                 // Ahrim the Blighted
		TOMBS_OF_AMASCUT, 11753,       // Elidinis' Warden
		SLAYER_TOWER, 415,             // Abyssal demon
		FREMENNIK_SLAYER_CAVE, 410,    // Kurask
		MOUNT_KARUULM, 8612,           // Drake
		SMOKE_DEVILS_CAVE, 498,        // Smoke devil
		DARK_BEAST, 4005,              // Dark beast
		KOUREND_CATACOMBS, 7279        // Deviant spectre
	));

	/** Bosses whose teleport starts a private instance (TeleInterface.startTeleport) rather than a fixed spot. */
	private static final Set<ServerTeleports> INSTANCED = EnumSet.of(BALANCE_ELEMENTAL, VARDORVIS, LEVIATHAN, DUKE_SUCELLUS,
		PHANTOM_MUSPAH, SOL_HEREDIT, WHISPERER, GROTESQUE_GUARDIANS);

	private TeleportNexus() {
	}

	private static List<Entry> list(String section, ServerTeleports... teleports) {
		List<Entry> list = new ArrayList<>();
		for (ServerTeleports teleport : teleports)
			list.add(new Entry(teleport, section));
		return list;
	}

	public static void register() {
		// favourites are saved by name (teleportFavouriteNames); unknown names (a renamed teleport) are dropped
		LoginListener.register(p -> {
			if (p.teleportFavourites == null) // a transient list: not always initialised on a loaded player
				p.teleportFavourites = new ArrayList<>();
			p.teleportFavourites.clear();
			if (p.teleportFavouriteNames == null)
				return;
			for (String name : p.teleportFavouriteNames) {
				try {
					ServerTeleports teleport = ServerTeleports.valueOf(name);
					if (!p.teleportFavourites.contains(teleport) && p.teleportFavourites.size() < MAX_FAVOURITES)
						p.teleportFavourites.add(teleport);
				} catch (IllegalArgumentException ignored) {
				}
			}
		});
		InterfaceHandler.register(INTERFACE, h -> {
			h.actions[CLOSE] = (SimpleAction) p -> p.closeInterface(ToplevelComponent.MAINMODAL);
			for (int c = 0; c < BUTTONS.length; c++) {
				Category category = BUTTONS[c];
				h.actions[FIRST_CATEGORY + c * CATEGORY_STRIDE] = (SimpleAction) p -> {
					p.teleportInterface.nexusCategory = category.ordinal();
					refresh(p);
				};
			}
			h.actions[SEARCH] = (SimpleAction) p -> p.stringInput("Search teleports (name):", input -> {
				p.teleportInterface.nexusSearch = input == null ? "" : input.trim();
				p.teleportInterface.nexusCategory = Category.SEARCH.ordinal();
				open(p);
			});
			for (int r = 0; r < ROWS; r++) {
				int index = r;
				h.actions[FIRST_ROW + r * ROW_STRIDE + ROW_BOX] = (SimpleAction) p -> select(p, index);
				h.actions[FIRST_ROW + r * ROW_STRIDE + ROW_TELEPORT] = (SimpleAction) p -> {
					Entry entry = entry(p, index);
					if (entry != null)
						teleport(p, entry.teleport());
				};
				h.actions[FIRST_ROW + r * ROW_STRIDE + ROW_STAR_ON] = (SimpleAction) p -> toggleFavourite(p, entry(p, index));
				h.actions[FIRST_ROW + r * ROW_STRIDE + ROW_STAR_OFF] = (SimpleAction) p -> toggleFavourite(p, entry(p, index));
			}
			for (int k = 0; k < DROPS; k++) {
				int slot = k;
				h.actions[FIRST_DROP + k * DROP_STRIDE + DROP_ITEM] = (SimpleAction) p -> examineDrop(p, slot);
			}
			h.actions[PREVIEW_TELEPORT] = (SimpleAction) p -> {
				if (p.teleportInterface.nexusSelected != null)
					teleport(p, p.teleportInterface.nexusSelected);
			};
			SimpleAction previewStar = p -> {
				ServerTeleports selected = p.teleportInterface.nexusSelected;
				if (selected != null)
					toggleFavourite(p, new Entry(selected, null));
			};
			h.actions[PREVIEW_STAR_ON] = previewStar;
			h.actions[PREVIEW_STAR_OFF] = previewStar;
		});
	}

	/**
	 * The "anywhere" entry points (::t / ::teleport and the minimap Teleports orb): donators and staff only, exactly as
	 * the ::teleport command always was. The teleport itself still runs every normal teleport restriction.
	 */
	public static void openAnywhere(Player player) {
		if (!player.isDonator() && !player.isStaff()) {
			player.sendMessage("Teleporting from anywhere is for donators and staff only. Use the teleport nexus at home.");
			return;
		}
		player.teleportInterface.open(player);
	}

	public static void open(Player player) {
		player.openInterface(ToplevelComponent.MAINMODAL, INTERFACE);
		PacketSender ps = player.getPacketSender();
		ps.sendString(INTERFACE, TITLE, ORANGE + "ZELUS TELEPORTS" + END);
		for (int c = 0; c < BUTTONS.length; c++)
			if (BUTTONS[c].iconItem > 0)
				ps.sendItem(INTERFACE, FIRST_CATEGORY + c * CATEGORY_STRIDE + CATEGORY_ICON, BUTTONS[c].iconItem, 1);
		refresh(player);
	}

	/* ---------------- rendering ---------------- */

	/** Full redraw for a new list (category, search, open): rows and scrollbar, which puts the list back at the top. */
	private static void refresh(Player player) {
		refresh(player, true);
	}

	/**
	 * Redraws the screen. With listChanged false (a card was selected or a star toggled) the rows' text and the
	 * scrollbar script are left alone, so the list keeps its scroll position: only outlines, stars, the header and the
	 * preview change.
	 */
	private static void refresh(Player player, boolean listChanged) {
		TeleInterface state = player.teleportInterface;
		PacketSender ps = player.getPacketSender();
		Category category = CATEGORIES[state.nexusCategory];
		List<ServerTeleports> favourites = player.teleportFavourites;

		ps.sendString(INTERFACE, SUB_HEADER, WHITE + "Favourites:" + END + " " + GOLD + favourites.size() + " / " + MAX_FAVOURITES + END
			+ "  <col=7f7f7f>|</col>  " + WHITE + "Last teleport:" + END + " " + ORANGE
			+ (player.lastServerTeleport == null ? "none" : player.lastServerTeleport.name) + END);

		for (int c = 0; c < BUTTONS.length; c++) {
			int box = FIRST_CATEGORY + c * CATEGORY_STRIDE;
			boolean selected = BUTTONS[c] == category;
			ps.setHidden(INTERFACE, box + CATEGORY_OUTLINE, !selected);
			ps.setHidden(INTERFACE, box + CATEGORY_OUTLINE + 1, !selected);
			ps.sendString(INTERFACE, box + CATEGORY_LABEL, (selected ? GOLD : WHITE) + BUTTONS[c].label + END);
		}
		ps.sendString(INTERFACE, SEARCH_LABEL, category == Category.SEARCH && state.nexusSearch != null && !state.nexusSearch.isEmpty()
			? GOLD + "Search: " + END + WHITE + state.nexusSearch + END : GREY + "Search teleports..." + END);

		List<Entry> rows = listChanged || state.nexusRows == null ? rows(player, category) : state.nexusRows;
		state.nexusRows = rows;
		for (int r = 0; r < ROWS; r++) {
			int row = FIRST_ROW + r * ROW_STRIDE;
			boolean used = r < rows.size();
			if (listChanged)
				ps.setHidden(INTERFACE, row, !used);
			if (!used)
				continue;
			Entry entry = rows.get(r);
			ServerTeleports teleport = entry.teleport();
			boolean favourite = favourites.contains(teleport);
			ps.setHidden(INTERFACE, row + ROW_OUTLINE, teleport != state.nexusSelected);
			ps.setHidden(INTERFACE, row + ROW_OUTLINE + 1, teleport != state.nexusSelected);
			ps.setHidden(INTERFACE, row + ROW_STAR_ON, !favourite);
			ps.setHidden(INTERFACE, row + ROW_STAR_OFF, favourite);
			if (!listChanged)
				continue;
			ps.sendString(INTERFACE, row + ROW_NAME, GOLD + shorten(teleport.name, 22) + END);
			ps.sendString(INTERFACE, row + ROW_LINE, tag(teleport) + (entry.section() == null ? "" : " " + GREY + entry.section() + END));
		}
		if (listChanged) {
			int contentHeight = rows.size() * ROW_PITCH - 2;
			int count = contentHeight <= SCROLL_HEIGHT ? 0 : (int) Math.ceil((contentHeight - SCROLL_HEIGHT / 3D) / 15D);
			ScrollbarClientScript.create().interfaceId(INTERFACE).containerId(SCROLL).scrollbarChildId(SCROLLBAR)
				.childrenCount(count).withDarkGraphics().build().send(player);
		}

		sendPreview(player);
		if (listChanged && rows.isEmpty())
			player.sendMessage(category == Category.FAVOURITES ? "You have no favourite teleports yet: click the star on any destination."
				: category == Category.SEARCH ? "Your search found no teleports." : "There are no teleports here.");
	}

	private static void sendPreview(Player player) {
		PacketSender ps = player.getPacketSender();
		ServerTeleports teleport = player.teleportInterface.nexusSelected;
		boolean shown = teleport != null;
		ps.setHidden(INTERFACE, PREVIEW_EMPTY, shown);
		for (int m = 0; m < MODELS; m++)
			ps.setHidden(INTERFACE, FIRST_MODEL + m, true);
		for (int c : new int[]{PREVIEW_NAME, PREVIEW_TAG, PREVIEW_DESC, PREVIEW_REQ, PREVIEW_TELEPORT, PREVIEW_TELEPORT + 1})
			ps.setHidden(INTERFACE, c, !shown);
		for (int k = 0; k < DROPS; k++)
			for (int part = 0; part < DROP_STRIDE; part++)
				ps.setHidden(INTERFACE, FIRST_DROP + k * DROP_STRIDE + part, !shown);
		boolean favourite = shown && player.teleportFavourites.contains(teleport);
		ps.setHidden(INTERFACE, PREVIEW_STAR_ON, !shown || !favourite);
		ps.setHidden(INTERFACE, PREVIEW_STAR_OFF, !shown || favourite);
		if (!shown)
			return;

		ps.sendString(INTERFACE, PREVIEW_NAME, teleport.name);
		String difficulty = teleport.difficulty == null || teleport.difficulty.isEmpty() ? "" : GREY + "  ·  " + teleport.difficulty + END;
		ps.sendString(INTERFACE, PREVIEW_TAG, tag(teleport) + difficulty);
		ps.sendString(INTERFACE, PREVIEW_DESC, shorten(clean(teleport.description), 150)); // 4 lines max above the drops
		String requirement = requirement(teleport);
		ps.sendString(INTERFACE, PREVIEW_REQ, requirement == null ? GREEN + "No requirements" + END : ORANGE + requirementLine(requirement) + END);
		sendModel(player, teleport);

		Item[] drops = teleport.getItems();
		for (int k = 0; k < DROPS; k++) {
			int item = FIRST_DROP + k * DROP_STRIDE + DROP_ITEM;
			boolean used = drops != null && k < drops.length && drops[k] != null;
			ps.setHidden(INTERFACE, item, !used);
			if (used)
				ps.sendItem(INTERFACE, item, drops[k].getId(), drops[k].getAmount());
		}
	}

	/**
	 * The animated model, as the old interface showed it: the teleport's own model + animation, else the matching
	 * NPC's first model and stand animation; on the same zoom preset the old interface picked for it.
	 */
	private static void sendModel(Player player, ServerTeleports teleport) {
		PacketSender ps = player.getPacketSender();
		TeleportPreviews.Preview preview = TeleportPreviews.get(teleport);
		if (preview != null) {
			sendFittedModel(ps, preview);
			return;
		}
		int preset = modelPreset(teleport);
		int component = FIRST_MODEL + preset;
		int hash = INTERFACE << 16 | component;
		ps.sendModelInformation(INTERFACE, component, (int) Math.round(PRESET_ZOOM[preset] * MODEL_ZOOM_SCALE), 0, 0);
		if (teleport.modelId > 0 && teleport.animId > 0) {
			ps.sendClientScript(10623, "Ii", hash, teleport.modelId);
			ps.animateInterface(INTERFACE, component, teleport.animId);
			ps.setHidden(INTERFACE, component, false);
			return;
		}
		if (teleport == CHAOS_DRUID) { // as the old interface: the druid's chathead
			ps.sendNpcHead(INTERFACE, component, 520);
			ps.setHidden(INTERFACE, component, false);
			return;
		}
		Integer fallback = FALLBACK_NPC.get(teleport);
		int npcId = fallback != null ? fallback : TeleInterface.getDynamicNpcId(teleport);
		NPCType def = npcId > 0 ? NPCType.get(npcId) : null;
		if (def != null && def.models != null && def.models.length > 0) {
			ps.sendClientScript(10623, "Ii", hash, def.models[0]);
			if (def.standAnimation > 0)
				ps.animateInterface(INTERFACE, component, def.standAnimation);
			ps.setHidden(INTERFACE, component, false);
		}
	}

	/** BuildTeleportInterface.MODEL_PRESETS zooms, per preview component, and its 80px-vs-112px viewport scale. */
	private static final int[] PRESET_ZOOM = {2000, 1000, 3000, 500, 1500, 3500};
	private static final double MODEL_ZOOM_SCALE = 112D / 80D;

	/** The preview box (1152 model components: 144 x 80 at 3,34 in the right panel) and how much of it a model fills. */
	private static final int MODEL_X = 3, MODEL_Y = 34, FIT_HEIGHT = 64, FIT_WIDTH = 128;
	/** Zoom limits: closer than 1200 the camera ends up inside small models (the Sand crab "parchment"). */
	private static final int MIN_ZOOM = 1200, MAX_ZOOM = 9000;
	/**
	 * Interface model projection: on-screen size ~= model units * PROJECTION / zoom (rotation 0). Confirmed by the
	 * 2026-10-15 15:36 screenshots: a 217-unit guard at zoom 1644 drew 61px tall.
	 */
	private static final double PROJECTION = 500D;

	/**
	 * A preview from teleport_previews.json (BuildTeleportPreviews): the model (merged NPC parts with recolours baked in,
	 * skins kept), its stand animation, and its bounds. The zoom fits the model's 2nd-98th percentile box into 128 x 64
	 * (or a fixed zoom from the spec), and the component is moved so the model's MIDDLE sits on the box centre: the client
	 * draws the model origin (its feet) at the centre, which is why round 1 had every model standing on the title.
	 */
	private static void sendFittedModel(PacketSender ps, TeleportPreviews.Preview preview) {
		int component = FIRST_MODEL;
		boolean percentiles = preview.pMaxY > preview.pMinY;
		int minX = percentiles ? preview.pMinX : preview.minX(), maxX = percentiles ? preview.pMaxX : preview.maxX();
		int minY = percentiles ? preview.pMinY : preview.minY(), maxY = percentiles ? preview.pMaxY : preview.maxY();
		int height = Math.max(1, maxY - minY), width = Math.max(1, maxX - minX);
		int zoom = preview.zoom > 0 ? preview.zoom
			: (int) Math.round(Math.max(height * PROJECTION / FIT_HEIGHT, width * PROJECTION / FIT_WIDTH));
		zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom));
		double midY = preview.centerY != 0 ? preview.centerY : (minY + maxY) / 2D, midX = (minX + maxX) / 2D;
		int dy = (int) Math.round(-midY * PROJECTION / zoom), dx = (int) Math.round(-midX * PROJECTION / zoom);
		ps.sendClientScript(10623, "Ii", INTERFACE << 16 | component, preview.model());
		ps.sendModelInformation(INTERFACE, component, zoom, 0, 0);
		ps.setAlignment(INTERFACE, component, MODEL_X + dx, MODEL_Y + dy);
		if (preview.anim() > 0)
			ps.animateInterface(INTERFACE, component, preview.anim());
		ps.setHidden(INTERFACE, component, false);
	}

	/** The old interface's zoom choice (851 components 60-66), as an index into the preview's 6 model components. */
	private static int modelPreset(ServerTeleports teleport) {
		switch (teleport) {
			case ROCK_CRABS: case SAND_CRABS: case AMMONITE_CRABS: case EXPERIMENTS: case CATALYSTS: case GWD:
			case VENENATIS: case SARACHNIS: case BARROWS: case ZULRAH: case REVENANTS: case SCORPIA: case PEST_CONTROL: case YAKS:
			case SLAYER_TOWER: case FREMENNIK_SLAYER_CAVE: case SMOKE_DEVILS_CAVE: case DARK_BEAST: case KOUREND_CATACOMBS: case CHAOS_DRUID:
				return 1; // zoom 1000
			case SMOKE_DEVIL: case CERBERUS: case BASILISK_KNIGHTS: case ARMOURED_ZOMBIES: case KRAKEN: case THE_GAUNTLET:
			case CALLISTO: case DEMONIC_GORILLA: case CHAOS_ELE: case VETION: case NEX: case KQ: case OPHIDIA: case VYREWATCH_SENTINELS:
			case TOMBS_OF_AMASCUT: case MOUNT_KARUULM:
				return 0; // zoom 2000
			case GIANT_MOLE:
				return 3; // zoom 500
			case DKS: case CHICKENS: case WINTERTODT: case CHAMBERS_OF_XERIC: case BALANCE_ELEMENTAL:
				return 4; // zoom 1500
			case SKOTIZO: case HYDRA:
				return 5; // zoom 3500
			default:
				return 2; // zoom 3000, the old default
		}
	}

	private static List<Entry> rows(Player player, Category category) {
		switch (category) {
			case FAVOURITES: {
				List<Entry> list = new ArrayList<>();
				for (ServerTeleports teleport : player.teleportFavourites)
					list.add(new Entry(teleport, null));
				return list;
			}
			case MONSTERS:
				return MONSTERS_LIST;
			case BOSSES:
				return BOSSES_LIST;
			case MINIGAMES:
				return MINIGAMES_LIST;
			case WILDERNESS:
				return WILDERNESS_LIST;
			case SKILLING:
				return SKILLING_LIST;
			case CITIES:
				return CITIES_LIST;
			default: {
				String query = player.teleportInterface.nexusSearch == null ? "" : player.teleportInterface.nexusSearch.toLowerCase();
				List<Entry> list = new ArrayList<>();
				for (ServerTeleports teleport : ServerTeleports.VALUES)
					if (!query.isEmpty() && teleport.name.toLowerCase().contains(query) && list.size() < ROWS)
						list.add(new Entry(teleport, null));
				return list;
			}
		}
	}

	/** Danger tag: the wilderness level at the destination, a private instance, or safe. */
	private static String tag(ServerTeleports teleport) {
		if (INSTANCED.contains(teleport))
			return CYAN + "[Instance]" + END;
		int level = teleport.teleportPos == null ? 0 : Wilderness.getLevel(teleport.teleportPos);
		return level > 0 ? RED + "[Wildy Lv " + level + "]" + END : GREEN + "[Safe]" + END;
	}

	/** The requirement text, or null when the teleport has none. */
	/**
	 * The one-line requirement under the drops (about 26 characters fit): "Requires 82 Mining to mine the Draco Rock" is cut
	 * to "Requires 82 Mining"; sentences that aren't requirements ("Located in the Wilderness") are shown as they are.
	 */
	private static String requirementLine(String requirement) {
		String text = requirement;
		for (String cut : new String[]{" to ", " for ", ", ", "; "}) {
			int at = text.indexOf(cut);
			if (at > 0)
				text = text.substring(0, at);
		}
		boolean sentence = Character.isUpperCase(text.charAt(0)) && !Character.isDigit(text.charAt(0));
		String line = sentence ? text : "Requires " + text;
		return line.length() <= 26 ? line : line.substring(0, 24).trim() + "...";
	}

	private static String requirement(ServerTeleports teleport) {
		String requirement = teleport.requirements == null ? "" : teleport.requirements.trim();
		String lower = requirement.toLowerCase();
		if (requirement.isEmpty() || lower.startsWith("no requirement") || lower.startsWith("none"))
			return null;
		return requirement.replaceAll("\\.$", "");
	}

	private static String clean(String text) {
		return text == null ? "" : text.replace("<br>", " ").replaceAll(" {2,}", " ").trim();
	}

	private static String shorten(String text, int max) {
		return text.length() <= max ? text : text.substring(0, max - 3).trim() + "...";
	}

	/* ---------------- actions ---------------- */

	private static Entry entry(Player player, int index) {
		List<Entry> rows = player.teleportInterface.nexusRows;
		return rows == null || index >= rows.size() ? null : rows.get(index);
	}

	private static void select(Player player, int index) {
		Entry entry = entry(player, index);
		if (entry == null)
			return;
		player.teleportInterface.nexusSelected = entry.teleport();
		refresh(player, false);
	}

	private static void teleport(Player player, ServerTeleports teleport) {
		TeleInterface state = player.teleportInterface;
		state.currentTeleport = teleport;
		state.startTeleport(player);
	}

	private static void examineDrop(Player player, int slot) {
		ServerTeleports teleport = player.teleportInterface.nexusSelected;
		Item[] drops = teleport == null ? null : teleport.getItems();
		if (drops == null || slot >= drops.length || drops[slot] == null)
			return;
		ObjType def = ObjType.get(drops[slot].getId());
		if (def != null)
			player.sendMessage(GOLD + def.name + END + (def.examine == null || def.examine.equals("null") ? "" : ": " + def.examine));
	}

	private static void toggleFavourite(Player player, Entry entry) {
		if (entry == null)
			return;
		ServerTeleports teleport = entry.teleport();
		List<ServerTeleports> favourites = player.teleportFavourites;
		if (favourites.remove(teleport)) {
			player.sendMessage("You have removed " + teleport.name + " from your favourites.");
		} else if (favourites.size() >= MAX_FAVOURITES) {
			player.sendMessage("You already have the maximum of " + MAX_FAVOURITES + " favourite teleports.");
			return;
		} else {
			favourites.add(teleport);
			player.sendMessage("You have added " + teleport.name + " to your favourites.");
		}
		saveFavourites(player);
		// on the Favourites list a star changes the list itself; elsewhere only the stars change
		refresh(player, CATEGORIES[player.teleportInterface.nexusCategory] == Category.FAVOURITES);
	}

	/** Mirrors the favourites into the saved name list. */
	private static void saveFavourites(Player player) {
		List<String> names = new ArrayList<>();
		for (ServerTeleports teleport : player.teleportFavourites)
			names.add(teleport.name());
		player.teleportFavouriteNames = names;
	}
}

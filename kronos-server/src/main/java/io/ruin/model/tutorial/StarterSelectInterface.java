package io.ruin.model.tutorial;

import io.ruin.api.utils.NumberUtils;
import io.ruin.cache.ObjType;
import io.ruin.model.ScrollbarClientScript;
import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.InterfaceHandler;
import io.ruin.model.inter.ToplevelComponent;
import io.ruin.model.inter.actions.SimpleAction;
import io.ruin.model.item.Item;
import io.ruin.model.tutorial.GameModeInterface.AccountType;
import io.ruin.model.tutorial.GameModeInterface.DifficultyType;
import io.ruin.network.PacketSender;

import java.util.ArrayList;
import java.util.List;

/**
 * The new-player "Select your path" screen (interface 1150, built by
 * .dev/cache-restore-tool/src/BuildStarterInterface.java). It replaces the old two-step 1100 (account type) / 1101
 * (difficulty) flow with one screen: 7 account types, 4 difficulties, a summary and a Confirm button. Confirming
 * runs the unchanged {@link GameModeInterface#confirmAccountCreation} (game mode, ironman varp, forum group,
 * PlayMode, starter kit and tutorial). The choice is kept on the player's GameModeInterface.
 */
public final class StarterSelectInterface {

	public static final int INTERFACE = 1150;
	private static final int CLOSE = 2, TITLE = 3, SUB_HEADER = 4, ACCOUNT_NOTE = 10;
	/** Selectable boxes: box (click), border, gold outline x2, then their content. */
	private static final int OUTLINE = 2, OUTLINE_INNER = 3;
	private static final int FIRST_ACCOUNT = 11, ACCOUNT_STRIDE = 6, ACCOUNT_ICON = 4, ACCOUNT_NAME = 5;
	private static final int FIRST_DIFFICULTY = 57, DIFFICULTY_STRIDE = 5, DIFFICULTY_LABEL = 4;
	private static final int HEADING = 81, RATES = 83, BULLETS = 84, KIT_HEADER = 86;
	/**
	 * Kit grid: a scroll container (2 rows of 6 visible) and its scrollbar, then 30 slots of (slot layer, fill, inner
	 * shadow x2, border, item). The item is a graphic component, which the client draws like an inventory slot
	 * (icon, outline, shadow and stack number), so only the item and amount are sent.
	 */
	private static final int KIT_SCROLL = 87, KIT_SCROLLBAR = 88;
	private static final int FIRST_KIT = 89, KIT_STRIDE = 6, KIT_ITEM = 5, KIT_SLOTS = 30, KIT_COLS = 6;
	/** Slot rows are 32px + 4px gap; the scroll container shows 68px (2 rows). */
	private static final int KIT_ROW_PITCH = 36, KIT_VIEW_HEIGHT = 68;
	private static final int CONFIRM = 269, CONFIRM_LABEL = 270;

	/** Account icons sit in a 32x29 box. */
	private static final double ACCOUNT_ZOOM_SCALE = 1.05;

	private static final String GOLD = "<col=ffc35a>", GREY = "<col=9a9a9a>", WHITE = "<col=ffffff>",
		GREEN = "<col=00ff00>", ORANGE = "<col=ff981f>", END = "</col>";
	private static final String BULLET = GOLD + "»" + END + " ";

	private static final AccountType[] ACCOUNTS = AccountType.values();
	private static final DifficultyType[] DIFFICULTIES = DifficultyType.values();

	private StarterSelectInterface() {
	}

	static void register() {
		InterfaceHandler.register(INTERFACE, h -> {
			h.actions[CLOSE] = (SimpleAction) p -> p.sendMessage("Choose an account type and a difficulty, then press Confirm.");
			for (int i = 0; i < ACCOUNTS.length; i++) {
				AccountType type = ACCOUNTS[i];
				h.actions[FIRST_ACCOUNT + i * ACCOUNT_STRIDE] = (SimpleAction) p -> {
					GameModeInterface gmi = p.getGameModeInterface();
					gmi.currentAccountType = type;
					if (type == AccountType.PVP_MODE) // presets give instant stats: no drop-rate difficulty
						gmi.currentDifficultyType = DifficultyType.EASY;
					refresh(p);
				};
			}
			for (int i = 0; i < DIFFICULTIES.length; i++) {
				DifficultyType difficulty = DIFFICULTIES[i];
				h.actions[FIRST_DIFFICULTY + i * DIFFICULTY_STRIDE] = (SimpleAction) p -> {
					GameModeInterface gmi = p.getGameModeInterface();
					if (gmi.currentAccountType == AccountType.PVP_MODE && difficulty != DifficultyType.EASY) {
						p.sendMessage("<col=ff0000>PvP Mode always plays on the base rate: no drop rate bonus.</col>");
						return;
					}
					gmi.currentDifficultyType = difficulty;
					refresh(p);
				};
			}
			for (int k = 0; k < KIT_SLOTS; k++) {
				int slot = k;
				h.actions[FIRST_KIT + k * KIT_STRIDE + KIT_ITEM] = (SimpleAction) p -> examineKit(p, slot);
			}
			h.actions[CONFIRM] = (SimpleAction) StarterSelectInterface::confirm;
		});
	}

	public static void open(Player player) {
		GameModeInterface gmi = player.getGameModeInterface();
		gmi.currentAccountType = null;
		gmi.currentDifficultyType = null;
		gmi.stage = 1;
		player.openInterface(ToplevelComponent.MAINMODAL, INTERFACE);
		PacketSender ps = player.getPacketSender();
		ps.sendString(INTERFACE, TITLE, "WELCOME TO ZELUS - SELECT YOUR PATH");
		ps.sendString(INTERFACE, SUB_HEADER, ORANGE + "Step 1:" + END + " choose an account type  " + GREY + "·" + END
			+ "  " + ORANGE + "Step 2:" + END + " choose a difficulty");
		for (int i = 0; i < ACCOUNTS.length; i++) {
			int row = FIRST_ACCOUNT + i * ACCOUNT_STRIDE;
			sendIcon(ps, row + ACCOUNT_ICON, ACCOUNTS[i].helmet.getId(), 1, ACCOUNT_ZOOM_SCALE);
			ps.sendString(INTERFACE, row + ACCOUNT_NAME, WHITE + ACCOUNTS[i].name + END);
		}
		refresh(player);
	}

	private static void refresh(Player player) {
		GameModeInterface gmi = player.getGameModeInterface();
		AccountType type = gmi.currentAccountType;
		DifficultyType difficulty = gmi.currentDifficultyType;
		boolean pvp = type == AccountType.PVP_MODE;
		PacketSender ps = player.getPacketSender();
		for (int i = 0; i < ACCOUNTS.length; i++) {
			int row = FIRST_ACCOUNT + i * ACCOUNT_STRIDE;
			showOutline(ps, row, ACCOUNTS[i] == type);
			// selected: a gold name in the recessed row (as the Zelus Market's active tabs)
			ps.sendString(INTERFACE, row + ACCOUNT_NAME, (ACCOUNTS[i] == type ? GOLD : WHITE) + ACCOUNTS[i].name + END);
		}
		for (int i = 0; i < DIFFICULTIES.length; i++) {
			DifficultyType d = DIFFICULTIES[i];
			int button = FIRST_DIFFICULTY + i * DIFFICULTY_STRIDE;
			showOutline(ps, button, d == difficulty);
			// inactive: coloured name over a grey rate; active: the rate turns white; locked (PvP Mode): all grey
			boolean locked = pvp && d != DifficultyType.EASY;
			ps.sendString(INTERFACE, button + DIFFICULTY_LABEL, (locked ? GREY + plainLabel(d) + END : label(d)) + "<br>"
				+ (d == difficulty ? WHITE : GREY) + (locked ? "locked" : xpRate(d) + "x") + END);
		}
		ps.sendString(INTERFACE, ACCOUNT_NOTE, type == null ? "pick one" : "");

		ps.sendString(INTERFACE, HEADING, type == null && difficulty == null ? "Choose your path"
			: (type == null ? GREY + "Account type?" + END : type.name) + "  " + GREY + "[" + END + " "
			+ (difficulty == null ? GREY + "Difficulty?" + END : label(difficulty)) + " " + GREY + "]" + END);
		// compact so it fits the 250px strip (centred): "XP Rate: 100x | Drop Rate: +18%"
		String bar = "  <col=7f7f7f>|</col>  ";
		ps.sendString(INTERFACE, RATES, pvp
			? WHITE + "XP Rate:" + END + " " + GREEN + xpRate(DifficultyType.EASY) + "x" + END + bar
				+ WHITE + "Drop Rate:" + END + " <col=ffff00>Base (0%)</col>"
			: difficulty == null ? GREY + "Pick a difficulty to see its rates." + END
			: WHITE + "XP Rate:" + END + " " + GREEN + xpRate(difficulty) + "x" + END + bar
				+ WHITE + "Drop Rate:" + END + " " + GREEN + (dropBonus(difficulty) == 0 ? "Base (0%)" : "+" + dropBonus(difficulty) + "%") + END);
		ps.sendString(INTERFACE, BULLETS, type == null
			? BULLET + "Pick how you want to play on the left.<br>"
				+ BULLET + "Then pick a difficulty above it.<br>"
				+ BULLET + "Slower XP rates give better drop rates.<br>"
				+ BULLET + "You can't change this later, so choose well!"
			: bullets(type));
		sendKit(player, type, difficulty);

		boolean ready = type != null && difficulty != null;
		ps.sendString(INTERFACE, CONFIRM_LABEL, (ready ? GREEN : GREY) + "Confirm & Begin Adventure" + END);
	}

	private static void showOutline(PacketSender ps, int box, boolean selected) {
		ps.setHidden(INTERFACE, box + OUTLINE, !selected);
		ps.setHidden(INTERFACE, box + OUTLINE_INNER, !selected);
	}

	private static void sendKit(Player player, AccountType type, DifficultyType difficulty) {
		PacketSender ps = player.getPacketSender();
		List<Item> kit = type == null ? List.of() : starterKit(player, type, difficulty);
		ps.sendString(INTERFACE, KIT_HEADER, type == null ? GREY + "Pick an account type to see its starter kit." + END
			: ORANGE + "Starter Kit Highlights:" + END + GREY + "  (" + kit.size() + " items" + (kit.size() > 2 * KIT_COLS ? ", scroll for all" : "") + ")" + END);
		int shown = Math.min(KIT_SLOTS, kit.size());
		for (int k = 0; k < KIT_SLOTS; k++) {
			int slot = FIRST_KIT + k * KIT_STRIDE;
			boolean used = k < shown;
			ps.setHidden(INTERFACE, slot, !used);
			if (used)
				ps.sendItem(INTERFACE, slot + KIT_ITEM, kit.get(k).getId(), kit.get(k).getAmount());
		}
		// clientscript 10532 sizes the scroll area to count * 15 + height / 3
		int rows = (shown + KIT_COLS - 1) / KIT_COLS;
		int contentHeight = rows * KIT_ROW_PITCH - 4;
		int count = contentHeight <= KIT_VIEW_HEIGHT ? 0 : (int) Math.ceil((contentHeight - KIT_VIEW_HEIGHT / 3D) / 15D);
		ScrollbarClientScript.create()
			.interfaceId(INTERFACE)
			.containerId(KIT_SCROLL)
			.scrollbarChildId(KIT_SCROLLBAR)
			.childrenCount(count)
			.withDarkGraphics()
			.build()
			.send(player);
	}

	private static void examineKit(Player player, int slot) {
		GameModeInterface gmi = player.getGameModeInterface();
		if (gmi.currentAccountType == null)
			return;
		List<Item> kit = starterKit(player, gmi.currentAccountType, gmi.currentDifficultyType);
		if (slot >= kit.size())
			return;
		Item item = kit.get(slot);
		ObjType def = ObjType.get(item.getId());
		if (def == null)
			return;
		// blue shadowed tag + default (black) chat text: readable on the parchment chatbox
		player.sendMessage("<col=005f9e><shad=0>[Starter Kit]</shad></col> " + def.name
			+ (item.getAmount() > 1 ? " x " + NumberUtils.formatNumber(item.getAmount()) : "")
			+ (def.examine == null || def.examine.isEmpty() || def.examine.equals("null") ? "." : ": " + def.examine));
	}

	/**
	 * The kit the old difficulty screen previewed (GameModeInterface.getModeItems plus the trainer items), with runes
	 * and arrows moved to the end so the slots show gear first.
	 */
	private static List<Item> starterKit(Player player, AccountType type, DifficultyType difficulty) {
		GameModeInterface gmi = player.getGameModeInterface();
		AccountType previous = gmi.currentAccountType;
		gmi.currentAccountType = type;
		gmi.getModeItems();
		gmi.currentAccountType = previous;
		List<Item> items = new ArrayList<>(gmi.items);
		if (type != AccountType.PVP_MODE) {
			items.add(new Item(30477));
			items.add(new Item(30588));
			items.add(new Item(30589));
			if (difficulty == DifficultyType.EXTREME) {
				items.add(new Item(30478));
				items.add(new Item(30479));
				items.add(new Item(30480));
			}
		}
		List<Item> gearFirst = new ArrayList<>();
		List<Item> supplies = new ArrayList<>();
		for (Item item : items)
			(isRuneOrAmmo(item.getId()) ? supplies : gearFirst).add(item);
		gearFirst.addAll(supplies);
		return gearFirst;
	}

	private static boolean isRuneOrAmmo(int id) {
		return (id >= 554 && id <= 566) || id == 884;
	}

	private static void confirm(Player player) {
		GameModeInterface gmi = player.getGameModeInterface();
		if (!player.newPlayer) {
			// opened with ::starterui on an existing account: never re-run account creation
			player.sendMessage("Preview only: your account is already set up, so nothing was changed.");
			player.closeInterface(ToplevelComponent.MAINMODAL);
			return;
		}
		if (gmi.currentAccountType == null || gmi.currentDifficultyType == null) {
			player.sendMessage("Pick an account type and a difficulty first.");
			return;
		}
		gmi.confirmAccountCreation(player);
	}

	private static void sendIcon(PacketSender ps, int component, int itemId, int amount, double zoomScale) {
		ObjType def = ObjType.get(itemId);
		ps.sendItem(INTERFACE, component, itemId, amount);
		if (def != null)
			ps.sendModelInformation(INTERFACE, component, (int) (def.zoom2d * zoomScale), def.xan2d, def.yan2d);
	}

	/**
	 * The difficulty's coloured name for this screen. Intermediate is shown as "Medium" so it fits its 60px button
	 * (the DifficultyType / Difficulty enums and the saved value are unchanged).
	 */
	private static String label(DifficultyType d) {
		return d == DifficultyType.INTERMEDIATE ? io.ruin.cache.Color.YELLOW.wrap("Medium") : d.name;
	}

	private static String plainLabel(DifficultyType d) {
		return d == DifficultyType.INTERMEDIATE ? "Medium" : d.difficulty.Name;
	}

	private static int xpRate(DifficultyType d) {
		return Math.round(d.difficulty.GetExperienceBoost());
	}

	/** Difficulty.dropRateBonus multiplies the drop odds: 0.82 = 18% better than Easy. */
	private static int dropBonus(DifficultyType d) {
		return Math.round((1f - d.difficulty.GetDropRate()) * 100f);
	}

	private static String bullets(AccountType type) {
		String[] lines = switch (type) {
			case REGULAR -> new String[]{"No restrictions: trade freely with everyone.", "Earn PVM Points from bosses and Slayer.",
				"Spend them in the PVM Points shop.", "Enter the Pit Championship tournaments."};
			case PVP_MODE -> new String[]{"::spawn basic gear and use PvP presets.", "Jump straight into the Wilderness.",
				"No PVM Points shop on this mode.", "Enter the Pit Championship tournaments."};
			case IRONMAN -> new String[]{"No trading with other players.", "No outside help: beat it all on your own.",
				"Ironman armour in your starter kit.", "Enter the Pit Championship tournaments."};
			case ULTIMATE_IRONMAN -> new String[]{"No trading and no bank.", "Everything you own fits in your inventory.",
				"The hardest way to play.", "Enter the Pit Championship tournaments."};
			case HARDCORE_IRONMAN -> new String[]{"No trading, no outside help.", "Only 1 life: play carefully.",
				"Die once and you become a regular Ironman.", "Enter the Pit Championship tournaments."};
			case GROUP_IRONMAN -> new String[]{"Play in a group of up to 5 players.", "Trade only within your group.",
				"Shared Group Ironman storage.", "Enter the Pit Championship tournaments."};
			case HARDCORE_GROUP_IRONMAN -> new String[]{"Group Ironman with 5 shared lives.", "Trade only within your group.",
				"Lose every life: you become a regular group.", "Enter the Pit Championship tournaments."};
		};
		StringBuilder sb = new StringBuilder();
		for (String line : lines)
			sb.append(sb.length() == 0 ? "" : "<br>").append(BULLET).append(line);
		return sb.toString();
	}
}

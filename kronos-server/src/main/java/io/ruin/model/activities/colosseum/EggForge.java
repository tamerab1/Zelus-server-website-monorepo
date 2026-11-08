package io.ruin.model.activities.colosseum;

import io.ruin.cache.ObjType;
import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.InterfaceHandler;
import io.ruin.model.inter.ToplevelComponent;
import io.ruin.model.inter.actions.SimpleAction;
import io.ruin.model.item.Item;
import io.ruin.model.item.containers.Equipment;
import io.ruin.network.PacketSender;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Ember Crucible, interface 1184 (.dev/cache-restore-tool BuildEggForgeInterface, art assets/colosseum/MakeForgeArt, written
 * by cache stage 21), 2026-11-08: upgrades the Reliquary's egg-slot icons from T1 to T5 for Trial Marks. The carousel shows the
 * player's icons (the egg slot first, then the inventory); the pedestals show the selected one and what it becomes; Forge opens the confirmation panel, and only "Forge it" spends anything. The icon changes id where it sits, so it
 * can't be lost half way.
 */
public final class EggForge {

	private EggForge() {
	}

	public static final int INTERFACE = 1184;
	// component ids, as BuildEggForgeInterface prints them
	private static final int CLOSE = 6, BALANCE = 8, FROM_NAME = 9, TO_NAME = 10, FROM_MODEL = 12, TO_MODEL = 14, COST = 16, STATUS = 17,
			FORGE_LIT = 19, FORGE = 20, FIRST_GEM = 21, GEM_STRIDE = 3, GEM_NEXT = 1, PREV = 36, NEXT = 37, FIRST_SOCKET = 38,
			SOCKET_STRIDE = 3, SOCKET_ITEM = 1, SOCKET_TIER = 2, EMPTY = 56, PAGE = 57, CONFIRM = 58, CONFIRM_TEXT = 62,
			CONFIRM_YES = 64, CONFIRM_NO = 66;
	private static final int SOCKETS = 6, TIERS_COUNT = 5;
	private static final String CHAT_NAME = "<col=7f0000>", END = "</col>", READY = "<col=7fdc6a>", SHORT = "<col=ff8a70>", DISABLED = "<col=8a8078>";

	/** each design's item ids, T1 to T5; the first {@link #STATIC_DESIGNS} are the static ones */
	static final int[][] TIERS = { { 40080, 40081, 40200, 40201, 40202 }, { 40203, 40204, 40205, 40206, 40207 },
			{ 40087, 40208, 40209, 40210, 40211 }, { 40090, 40212, 40213, 40214, 40215 }, { 40091, 40216, 40217, 40218, 40219 },
			{ 40093, 40220, 40221, 40222, 40223 }, { 40094, 40224, 40225, 40226, 40227 }, { 40228, 40229, 40230, 40231, 40232 },
			{ 40233, 40234, 40235, 40236, 40237 }, { 40238, 40239, 40240, 40241, 40242 }, { 40243, 40244, 40245, 40246, 40247 },
			{ 40248, 40249, 40250, 40251, 40252 } };
	/** Trial Marks to reach T2, T3, T4, T5, as a percentage of the design's price in the Reliquary */
	private static final int[] COST_PERCENT = { 60, 90, 150, 200 };

	/** an icon the player has: its design and tier (0 = T1) */
	private record Icon(int id, int design, int tier) {
		boolean maxed() {
			return tier == TIERS_COUNT - 1;
		}

		int nextId() {
			return TIERS[design][tier + 1];
		}

		int cost() {
			return ReliquaryInterface.price(TIERS[design][0]) * COST_PERCENT[tier] / 100;
		}
	}

	private static final class View {
		int selected;
		boolean confirming;
	}

	private static final Map<Integer, View> VIEWS = new HashMap<>();

	static void register() {
		InterfaceHandler.register(INTERFACE, h -> {
			h.actions[CLOSE] = (SimpleAction) Player::closeInterfaces;
			h.actions[PREV] = (SimpleAction) p -> act(p, v -> v.selected = Math.max(0, (page(v) - 1) * SOCKETS));
			h.actions[NEXT] = (SimpleAction) p -> act(p, v -> v.selected = (page(v) + 1) * SOCKETS);
			for (int i = 0; i < SOCKETS; i++) {
				int socket = i;
				h.actions[FIRST_SOCKET + i * SOCKET_STRIDE + SOCKET_ITEM] = (SimpleAction) p -> act(p, v -> v.selected = page(v) * SOCKETS + socket);
			}
			h.actions[FORGE] = (SimpleAction) EggForge::askConfirm;
			h.actions[CONFIRM_YES] = (SimpleAction) EggForge::forge;
			h.actions[CONFIRM_NO] = (SimpleAction) p -> {
				View v = VIEWS.get(p.getUserId());
				if (v != null) {
					v.confirming = false;
					draw(p);
				}
			};
			h.closedAction = (p, i) -> VIEWS.remove(p.getUserId());
		});
	}

	public static void open(Player player) {
		View v = new View();
		VIEWS.put(player.getUserId(), v);
		player.openInterface(ToplevelComponent.MAINMODAL, INTERFACE);
		draw(player);
	}

	/** Frames the icon inside its 70 px well: 0.6 of the inventory zoom is about 53 px across. */
	private static void orient(Player player, int component, int itemId) {
		ObjType def = ObjType.get(itemId);
		if (def != null)
			player.getPacketSender().sendModelInformation(INTERFACE, component, Math.max(200, def.zoom2d * 60 / 100), def.xan2d, def.yan2d);
	}

	private static int page(View v) {
		return v.selected / SOCKETS;
	}

	/** Runs a selection change, unless the confirmation panel is up. */
	private static void act(Player player, java.util.function.Consumer<View> change) {
		View v = VIEWS.get(player.getUserId());
		if (v == null || v.confirming || !player.isVisibleInterface(INTERFACE))
			return;
		int before = v.selected;
		change.accept(v);
		if (v.selected >= icons(player).size())
			v.selected = before;
		draw(player);
	}

	/** Every icon on the player: the egg slot first, then the inventory. */
	private static List<Icon> icons(Player player) {
		List<Icon> list = new ArrayList<>();
		if (player.getEquipment().getItems().length > Equipment.SLOT_EGG)
			addIcon(list, player.getEquipment().get(Equipment.SLOT_EGG));
		for (Item item : player.getInventory().getItems())
			addIcon(list, item);
		return list;
	}

	private static void addIcon(List<Icon> list, Item item) {
		if (item == null)
			return;
		for (int d = 0; d < TIERS.length; d++)
			for (int t = 0; t < TIERS_COUNT; t++)
				if (TIERS[d][t] == item.getId()) {
					list.add(new Icon(item.getId(), d, t));
					return;
				}
	}

	private static Icon selected(Player player, View v) {
		List<Icon> list = icons(player);
		return v.selected >= 0 && v.selected < list.size() ? list.get(v.selected) : null;
	}

	private static void askConfirm(Player player) {
		View v = VIEWS.get(player.getUserId());
		if (v == null || v.confirming)
			return;
		Icon icon = selected(player, v);
		if (icon == null)
			return;
		if (icon.maxed()) {
			player.sendMessage("That icon is already fully forged.");
			return;
		}
		if (player.trialMarks < icon.cost()) {
			player.sendMessage("You need " + icon.cost() + " Trial Marks to forge this icon. You have " + player.trialMarks + ".");
			return;
		}
		v.confirming = true;
		draw(player);
	}

	/** The confirmed forge: everything is checked again, because the inventory can change while the panel is up. */
	private static void forge(Player player) {
		View v = VIEWS.get(player.getUserId());
		if (v == null || !v.confirming)
			return;
		v.confirming = false;
		Icon icon = selected(player, v);
		if (icon == null || icon.maxed() || player.trialMarks < icon.cost()) {
			player.sendMessage("The Crucible goes cold. Nothing was spent.");
			draw(player);
			return;
		}
		Item item = player.getEquipment().getItems().length > Equipment.SLOT_EGG && player.getEquipment().getId(Equipment.SLOT_EGG) == icon.id
				? player.getEquipment().get(Equipment.SLOT_EGG)
				: player.getInventory().findItem(icon.id);
		if (item == null) {
			player.sendMessage("The Crucible goes cold. Nothing was spent.");
			draw(player);
			return;
		}
		item.setId(icon.nextId());
		player.trialMarks -= icon.cost();
		player.sendMessage("The Crucible reshapes your icon into " + CHAT_NAME + ObjType.get(icon.nextId()).name + END + ". You have " + player.trialMarks + " Trial Marks left.");
		draw(player);
	}

	private static void draw(Player player) {
		View v = VIEWS.get(player.getUserId());
		if (v == null)
			return;
		PacketSender ps = player.getPacketSender();
		List<Icon> list = icons(player);
		if (v.selected >= list.size())
			v.selected = Math.max(0, list.size() - 1);
		ps.sendString(INTERFACE, BALANCE, String.valueOf(player.trialMarks));
		ps.setHidden(INTERFACE, EMPTY, !list.isEmpty());
		if (list.isEmpty())
			ps.sendString(INTERFACE, EMPTY, "Carry or wear an aura icon to forge it");

		// the carousel
		int page = page(v), pages = Math.max(1, (list.size() + SOCKETS - 1) / SOCKETS);
		for (int i = 0; i < SOCKETS; i++) {
			int socket = FIRST_SOCKET + i * SOCKET_STRIDE, idx = page * SOCKETS + i;
			Icon icon = idx < list.size() ? list.get(idx) : null;
			ps.setHidden(INTERFACE, socket, idx != v.selected || icon == null);
			ps.setHidden(INTERFACE, socket + SOCKET_ITEM, icon == null);
			if (icon != null)
				ps.sendItem(INTERFACE, socket + SOCKET_ITEM, icon.id, 1);
			ps.sendString(INTERFACE, socket + SOCKET_TIER, icon == null ? "" : "Tier " + (icon.tier + 1));
		}
		ps.setHidden(INTERFACE, PREV, page == 0);
		ps.setHidden(INTERFACE, NEXT, page >= pages - 1);
		ps.sendString(INTERFACE, PAGE, pages > 1 ? (page + 1) + " / " + pages : "");

		// the stage
		Icon icon = v.selected < list.size() ? list.get(v.selected) : null;
		ps.setHidden(INTERFACE, FROM_MODEL, icon == null);
		ps.setHidden(INTERFACE, TO_MODEL, icon == null || icon.maxed());
		boolean ready = icon != null && !icon.maxed() && player.trialMarks >= icon.cost();
		ps.setHidden(INTERFACE, FORGE_LIT, !ready);
		ps.sendString(INTERFACE, FORGE, ready ? "Forge" : DISABLED + "Forge" + END);
		if (icon == null) {
			ps.sendString(INTERFACE, FROM_NAME, "");
			ps.sendString(INTERFACE, TO_NAME, "");
			ps.sendString(INTERFACE, COST, "");
			ps.sendString(INTERFACE, STATUS, "No icon selected");
		} else {
			ps.sendString(INTERFACE, FROM_NAME, ObjType.get(icon.id).name);
			ps.sendItem(INTERFACE, FROM_MODEL, icon.id, 1);
			orient(player, FROM_MODEL, icon.id);
			if (icon.maxed()) {
				ps.sendString(INTERFACE, TO_NAME, "");
				ps.sendString(INTERFACE, COST, "");
				ps.sendString(INTERFACE, STATUS, "Fully forged");
			} else {
				ps.sendString(INTERFACE, TO_NAME, ObjType.get(icon.nextId()).name);
				ps.sendItem(INTERFACE, TO_MODEL, icon.nextId(), 1);
				orient(player, TO_MODEL, icon.nextId());
				ps.sendString(INTERFACE, COST, icon.cost() + " Marks");
				ps.sendString(INTERFACE, STATUS, ready ? READY + "Ready to forge" + END : SHORT + (icon.cost() - player.trialMarks) + " more needed" + END);
			}
		}
		for (int g = 0; g < TIERS_COUNT; g++) {
			int gem = FIRST_GEM + g * GEM_STRIDE;
			ps.setHidden(INTERFACE, gem, icon == null || g > icon.tier);
			ps.setHidden(INTERFACE, gem + GEM_NEXT, icon == null || g != icon.tier + 1);
		}

		// the confirmation
		ps.setHidden(INTERFACE, CONFIRM, !v.confirming);
		if (v.confirming && icon != null && !icon.maxed())
			ps.sendString(INTERFACE, CONFIRM_TEXT, ObjType.get(icon.id).name + " becomes<br>" + ObjType.get(icon.nextId()).name + ".<br>"
					+ icon.cost() + " Trial Marks will burn.<br>This cannot be undone.");
	}
}

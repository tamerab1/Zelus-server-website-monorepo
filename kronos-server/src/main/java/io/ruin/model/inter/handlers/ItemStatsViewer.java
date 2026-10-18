package io.ruin.model.inter.handlers;

import io.ruin.cache.ObjType;
import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.InterfaceHandler;
import io.ruin.model.inter.ToplevelComponent;
import io.ruin.model.inter.actions.SimpleAction;
import io.ruin.model.item.containers.Equipment;
import io.ruin.model.stat.StatType;

import io.ruin.model.inter.dialogue.OptionsDialogue;
import io.ruin.model.inter.utils.Option;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Examine -> item stats: a dedicated viewer (interface 1138, built by
 * .dev/cache-restore-tool/src/BuildItemStatsInterface.java) showing ONE wearable item: its model and
 * its own bonuses, requirements and attack speed. Nothing on it is tied to the player's worn gear.
 */
public class ItemStatsViewer {

	public static final int INTERFACE = 1138;

	private static final int CLOSE = 2, TITLE = 3, MODEL = 8;
	private static final int[] ATTACK_LINES = {14, 15, 16, 17, 18};
	private static final int[] DEFENCE_LINES = {20, 21, 22, 23, 24};
	private static final int MELEE_STRENGTH = 26, RANGED_STRENGTH = 27, MAGIC_DAMAGE = 28, PRAYER = 29;
	private static final int UNDEAD = 31, SLAYER = 32;
	private static final int REQUIREMENTS = 34, ATTACK_SPEED = 36, SEARCH = 37;
	private static final int MAX_RESULTS = 200;
	private static final String[] STYLES = {"Stab", "Slash", "Crush", "Magic", "Range"};

	// The model box is far bigger than an inventory icon, so the icon zoom is scaled down.
	private static final double MODEL_ZOOM_SCALE = 3.4;

	// Where the Close button returns to when the viewer was opened from another screen (forge, upgrade).
	private static final Map<Player, Runnable> RETURN_TO = Collections.synchronizedMap(new WeakHashMap<>());

	/** @return true if the viewer was opened (the item is wearable and has bonuses). */
	public static boolean open(Player player, int itemId) {
		if (io.ruin.model.entity.npc.combatpet.AscendedBestiary.examineItem(player, itemId))
			return true;
		// any Chamber of Ascension bond: open Bonds & Auras on it
		var bond = io.ruin.model.content.bonds.BondType.variant(itemId);
		if (bond != null) {
			io.ruin.model.content.bonds.BondInterface.open(player, io.ruin.model.content.bonds.BondInterface.PERKS, bond.type());
			return true;
		}
		return show(player, itemId);
	}

	/**
	 * Opens the viewer from another screen: its Close button runs {@code returnTo} (e.g. reopening that
	 * screen) instead of just closing. Non-wearable items get their examine text instead.
	 */
	public static boolean open(Player player, int itemId, Runnable returnTo) {
		boolean opened = show(player, itemId);
		if (opened && returnTo != null)
			RETURN_TO.put(player, returnTo);
		else if (!opened)
			io.ruin.model.item.Item.examine(player, itemId);
		return opened;
	}

	private static boolean show(Player player, int itemId) {
		ObjType def = ObjType.get(itemId);
		if (def == null || def.equipSlot == -1 || def.equipBonuses == null)
			return false;
		int[] b = def.equipBonuses.clone();
		// Soulbound weapons (2026-10-18): item_info holds the tier-1 stats; the player's own line adds its tier bonus,
		// exactly as Equipment.sendUpdates does, so this matches the worn stats screen
		io.ruin.model.content.soulweapons.SoulWeapons.addTierBonuses(player, new io.ruin.model.item.Item(itemId), b);
		var ps = player.getPacketSender();
		player.openInterface(ToplevelComponent.MAINMODAL, INTERFACE);
		RETURN_TO.remove(player);
		ps.sendString(INTERFACE, TITLE, def.name);
		// Sent as the ITEM (not its bare model) so the client applies the item's own recolour/retexture
		// tables -- upgraded variants share the base item's model. Zoom/angles are re-sent after it.
		ps.sendItem(INTERFACE, MODEL, itemId, 1);
		ps.sendModelInformation(INTERFACE, MODEL, (int) (def.zoom2d / MODEL_ZOOM_SCALE), def.xan2d, def.yan2d);

		for (int i = 0; i < 5; i++) {
			ps.sendString(INTERFACE, ATTACK_LINES[i], STYLES[i] + ": " + bonus(b, EquipmentStats.STAB_ATTACK + i, false));
			ps.sendString(INTERFACE, DEFENCE_LINES[i], STYLES[i] + ": " + bonus(b, EquipmentStats.STAB_DEFENCE + i, false));
		}
		ps.sendString(INTERFACE, MELEE_STRENGTH, "Melee strength: " + bonus(b, EquipmentStats.MELEE_STRENGTH, false));
		ps.sendString(INTERFACE, RANGED_STRENGTH, "Ranged strength: " + bonus(b, EquipmentStats.RANGED_STRENGTH, false));
		ps.sendString(INTERFACE, MAGIC_DAMAGE, "Magic damage: " + bonus(b, EquipmentStats.MAGIC_DAMAGE, true));
		ps.sendString(INTERFACE, PRAYER, "Prayer: " + bonus(b, EquipmentStats.PRAYER, false));
		ps.sendString(INTERFACE, UNDEAD, "Undead: " + bonus(b, EquipmentStats.UNDEAD, true));
		ps.sendString(INTERFACE, SLAYER, "Slayer: " + bonus(b, EquipmentStats.SLAYER, true));
		ps.sendString(INTERFACE, REQUIREMENTS, requirements(def));

		boolean weapon = def.equipSlot == Equipment.SLOT_WEAPON && def.weaponType != null;
		// bows fire a tick faster on Rapid (PlayerCombat: RAPID_RANGED = attackTicks - 1)
		boolean bow = weapon && def.rangedWeapon != null;
		ps.sendString(INTERFACE, ATTACK_SPEED, weapon
			? def.weaponType.attackTicks + " ticks (" + String.format("%.1f", def.weaponType.attackTicks * 0.6) + "s)"
				+ (bow ? ", " + (def.weaponType.attackTicks - 1) + " on Rapid" : "")
			: "Not a weapon");
		return true;
	}

	/**
	 * Opens the chatbox item search (clientscript 750, the same one ::spawn uses), which shows matching
	 * items with their models while the player types; picking one shows its stats.
	 */
	public static void search(Player player) {
		player.itemSearch("Search for an item to view its stats:", true, itemId -> {
			ObjType def = ObjType.get(itemId);
			if (def == null)
				return;
			if (def.notedTemplateId != -1 && def.notedId > 0)
				def = ObjType.get(def.notedId); // a noted copy -> the real item
			else if (def.placeholderTemplateId != -1 && def.placeholderMainId > 0)
				def = ObjType.get(def.placeholderMainId); // a bank placeholder -> the real item
			if (def == null || !show(player, def.id))
				player.sendMessage((def == null ? "That item" : def.name) + " can't be worn, so it has no stats to show.");
		});
	}

	/** Text search fallback: asks for a name and lists the wearable items that match it. */
	public static void textSearch(Player player) {
		player.stringInput("Search for an item to view its stats:", query -> {
			List<Integer> results = find(query);
			if (results.isEmpty()) {
				player.sendMessage("No wearable items found matching \"" + query + "\".");
				return;
			}
			showResults(player, query, results, 0);
		});
	}

	/** Wearable items whose name contains the query: exact matches first, then names starting with it. */
	static List<Integer> find(String query) {
		String q = query == null ? "" : query.trim().toLowerCase();
		if (q.isEmpty())
			return List.of();
		List<ObjType> hits = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		List<ObjType> defs = new ArrayList<>(ObjType.cached.values());
		defs.sort(Comparator.comparingInt(d -> d.id));
		for (ObjType def : defs) {
			if (def == null || def.name == null || def.equipSlot == -1 || def.equipBonuses == null)
				continue;
			if (def.notedTemplateId != -1 || def.placeholderTemplateId != -1)
				continue;
			String name = def.name.toLowerCase();
			// same-name duplicates (cache copies) with identical stats only show once
			if (name.contains(q) && seen.add(name + java.util.Arrays.toString(def.equipBonuses)))
				hits.add(def);
		}
		hits.sort(Comparator.comparingInt((ObjType d) -> d.name.equalsIgnoreCase(q) ? 0 : d.name.toLowerCase().startsWith(q) ? 1 : 2)
			.thenComparing(d -> d.name.toLowerCase()));
		List<Integer> ids = new ArrayList<>();
		for (int i = 0; i < hits.size() && i < MAX_RESULTS; i++)
			ids.add(hits.get(i).id);
		return ids;
	}

	private static void showResults(Player player, String query, List<Integer> results, int start) {
		if (results.size() == 1) {
			show(player, results.get(0));
			return;
		}
		int remaining = results.size() - start;
		int shown = remaining <= 5 ? remaining : 4;
		List<Option> options = new ArrayList<>();
		for (int i = start; i < start + shown; i++) {
			int id = results.get(i);
			options.add(new Option(ObjType.get(id).name, () -> show(player, id)));
		}
		if (remaining > shown) {
			int next = start + shown;
			options.add(new Option("More results...", () -> showResults(player, query, results, next)));
		}
		String title = "Results for \"" + query + "\" (" + (start + 1) + "-" + (start + shown) + " of " + results.size() + ")";
		player.dialogue(new OptionsDialogue(title, options));
	}

	private static String bonus(int[] bonuses, int index, boolean percent) {
		int value = index < bonuses.length ? bonuses[index] : 0;
		return EquipmentStats.asBonus(value, percent);
	}

	private static String requirements(ObjType def) {
		if (def.equipReqs == null || def.equipReqs.length == 0)
			return "None";
		StringBuilder sb = new StringBuilder();
		for (int req : def.equipReqs) {
			if (sb.length() > 0)
				sb.append(", ");
			sb.append(req & 0xFF).append(' ').append(StatType.VALUES[req >> 8].name());
		}
		return sb.toString();
	}

	public static void register() {
		InterfaceHandler.register(INTERFACE, h -> {
			h.actions[CLOSE] = (SimpleAction) player -> {
				Runnable back = RETURN_TO.remove(player);
				player.closeInterface(ToplevelComponent.MAINMODAL);
				if (back != null)
					back.run();
			};
			h.actions[SEARCH] = (SimpleAction) ItemStatsViewer::search;
			h.closedAction = (player, i) -> RETURN_TO.remove(player);
		});
	}
}

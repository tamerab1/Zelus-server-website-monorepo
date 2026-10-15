package io.ruin.model.content.gearloadouts;

import io.ruin.cache.ObjType;
import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.InterfaceHandler;
import io.ruin.model.inter.ToplevelComponent;
import io.ruin.model.inter.actions.DefaultAction;
import io.ruin.model.inter.actions.SimpleAction;
import io.ruin.model.inter.questtab.dashboard.QuestDashboards;
import io.ruin.model.item.Item;
import io.ruin.model.item.containers.Equipment;
import io.ruin.model.skills.magic.SpellBook;
import io.ruin.model.stat.StatType;
import io.ruin.network.PacketSender;

import java.util.List;

/**
 * "Zelus Battle Presets" (interface 1155, built by .dev/cache-restore-tool/src/BuildBattlePresetsInterface.java), opened
 * from the PK Hub's "Open Battle Presets" button. Shows the free kits (FreeGearLoadoutKits, up to 6) as cards: combat
 * level badge, name, spellbook + Atk/Str/Def, and weapon / body / legs icons. Load / Preview on a card, plus My Custom
 * Loadouts and Spawn Supplies. Loading goes through QuestDashboards.loadFreeKit, so every existing rule applies (PvP Mode
 * accounts only, not in the Wilderness, not in combat) and GearLoadoutHandler.spawnFreeKit does the actual work.
 */
public final class BattlePresets {

	public static final int INTERFACE = 1155;
	private static final int CLOSE = 2, TITLE = 3, SUB_HEADER = 4;
	/** Cards: fill, clickable border (Load / Preview), badge, level, "Combat", name, archetype, 3 gear icons. */
	private static final int FIRST_CARD = 10, CARD_STRIDE = 10, CARD_EDGE = 1, CARD_LEVEL = 3, CARD_COMBAT = 4, CARD_NAME = 5,
		CARD_LINE = 6, CARD_GEAR = 7, CARDS = 6;
	private static final int MY_LOADOUTS = 70, SPAWN = 72;
	/** Free supply row (bezel + label each): Food, Potions, Veng, Barrage. */
	private static final int FIRST_SUPPLY = 74, SUPPLY_STRIDE = 2, SUPPLIES = 4;
	/** The gear shown on a card. */
	private static final int[] GEAR_SLOTS = {Equipment.SLOT_WEAPON, Equipment.SLOT_CHEST, Equipment.SLOT_LEGS};

	private static final String GOLD = "<col=ffc35a>", GREY = "<col=9a9a9a>", WHITE = "<col=ffffff>", GREEN = "<col=00ff00>",
		ORANGE = "<col=ff981f>", RED = "<col=ff5050>", END = "</col>";

	private BattlePresets() {
	}

	public static void register() {
		InterfaceHandler.register(INTERFACE, h -> {
			h.actions[CLOSE] = (SimpleAction) p -> p.closeInterface(ToplevelComponent.MAINMODAL);
			for (int c = 0; c < CARDS; c++) {
				int kit = c;
				h.actions[FIRST_CARD + c * CARD_STRIDE + CARD_EDGE] = (DefaultAction) (player, option, slot, itemId) -> {
					if (option == 2)
						preview(player, kit);
					else
						load(player, kit);
				};
			}
			h.actions[MY_LOADOUTS] = (SimpleAction) p -> {
				if (QuestDashboards.requirePvpMode(p))
					GearLoadoutInterface.open(p);
			};
			for (int k = 0; k < SUPPLIES; k++) {
				int kind = k;
				h.actions[FIRST_SUPPLY + k * SUPPLY_STRIDE] = (SimpleAction) p -> FreeSupplies.give(p, kind);
			}
			h.actions[SPAWN] = (SimpleAction) p -> {
				if (QuestDashboards.requirePvpMode(p))
					QuestDashboards.openSpawn.accept(p);
			};
		});
	}

	public static void open(Player player) {
		List<GearLoadoutPreset> kits = FreeGearLoadoutKits.getKits();
		player.openInterface(ToplevelComponent.MAINMODAL, INTERFACE);
		PacketSender ps = player.getPacketSender();
		ps.sendString(INTERFACE, TITLE, ORANGE + "ZELUS BATTLE PRESETS" + END);
		ps.sendString(INTERFACE, SUB_HEADER, WHITE + "Quick Presets" + END + " " + GOLD + "(" + Math.min(kits.size(), CARDS) + " / " + CARDS + ")" + END
			+ "  <col=7f7f7f>|</col>  PvP Mode: " + (player.isPvpMode() ? GREEN + "Yes" + END : RED + "No" + END)
			+ "  <col=7f7f7f>|</col>  " + GREY + "Free items vanish on death & can't be traded" + END);
		for (int c = 0; c < CARDS; c++) {
			int card = FIRST_CARD + c * CARD_STRIDE;
			GearLoadoutPreset kit = c < kits.size() ? kits.get(c) : null;
			if (kit == null) {
				ps.sendString(INTERFACE, card + CARD_LEVEL, GREY + "-" + END);
				ps.sendString(INTERFACE, card + CARD_NAME, GREY + "Empty slot" + END);
				ps.sendString(INTERFACE, card + CARD_LINE, GREY + "No preset here yet" + END);
				for (int g = 0; g < GEAR_SLOTS.length; g++)
					ps.setHidden(INTERFACE, card + CARD_GEAR + g, true);
				continue;
			}
			ps.sendString(INTERFACE, card + CARD_LEVEL, "<col=ffff00>" + combatLevel(kit) + END);
			ps.sendString(INTERFACE, card + CARD_NAME, shorten(kit.presetName, 22));
			ps.sendString(INTERFACE, card + CARD_LINE, archetype(kit));
			for (int g = 0; g < GEAR_SLOTS.length; g++) {
				Item item = kit.getEquipmentItem(GEAR_SLOTS[g]);
				int comp = card + CARD_GEAR + g;
				ps.setHidden(INTERFACE, comp, item == null);
				if (item != null)
					ps.sendItem(INTERFACE, comp, item.getId(), item.getAmount());
			}
		}
	}

	private static void load(Player player, int kit) {
		// Division Rivals: mid-match the containers hold the kit and are wiped on restore
		if (io.ruin.model.content.rivals.RivalsSnapshot.refuse(player))
			return;
		QuestDashboards.loadFreeKit(player, kit); // PvP Mode, Wilderness and combat checks live there
	}

	/** Chat summary of a kit: stats, spellbook, worn gear. */
	private static void preview(Player player, int index) {
		List<GearLoadoutPreset> kits = FreeGearLoadoutKits.getKits();
		if (index >= kits.size()) {
			player.sendMessage("That preset slot is empty.");
			return;
		}
		GearLoadoutPreset kit = kits.get(index);
		player.sendMessage(ORANGE + "[Battle Presets]" + END + " " + GOLD + kit.presetName + END + " - Combat " + combatLevel(kit)
			+ ", " + archetype(kit));
		StringBuilder gear = new StringBuilder();
		for (Item item : kit.getEquipmentItems()) {
			if (item == null)
				continue;
			ObjType def = item.getDef();
			gear.append(gear.length() == 0 ? "" : ", ").append(def == null ? item.getId() : def.name);
		}
		player.sendMessage(GREY + "Worn: " + END + (gear.length() == 0 ? "nothing" : gear));
	}

	/** Spellbook and Atk / Str / Def, e.g. "Lunar · 99/99/99". */
	private static String archetype(GearLoadoutPreset kit) {
		return spellbook(kit) + " · " + kit.getCombatLevel(StatType.Attack) + "/" + kit.getCombatLevel(StatType.Strength) + "/"
			+ kit.getCombatLevel(StatType.Defence);
	}

	private static String spellbook(GearLoadoutPreset kit) {
		SpellBook book = kit.getSpellBook();
		String name = book == null ? "Modern" : book.name().toLowerCase();
		return Character.toUpperCase(name.charAt(0)) + name.substring(1);
	}

	/** The standard combat level formula on the kit's levels. */
	static int combatLevel(GearLoadoutPreset kit) {
		int attack = kit.getCombatLevel(StatType.Attack), strength = kit.getCombatLevel(StatType.Strength);
		int defence = kit.getCombatLevel(StatType.Defence), hitpoints = Math.max(10, kit.getCombatLevel(StatType.Hitpoints));
		int ranged = kit.getCombatLevel(StatType.Ranged), prayer = kit.getCombatLevel(StatType.Prayer);
		int magic = kit.getCombatLevel(StatType.Magic);
		double base = 0.25 * (defence + hitpoints + Math.floor(prayer / 2D));
		double melee = 0.325 * (attack + strength);
		double range = 0.325 * Math.floor(ranged * 1.5);
		double mage = 0.325 * Math.floor(magic * 1.5);
		return (int) Math.floor(base + Math.max(melee, Math.max(range, mage)));
	}

	private static String shorten(String text, int max) {
		if (text == null)
			return "-";
		return text.length() <= max ? text : text.substring(0, max - 2) + "..";
	}
}

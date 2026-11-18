package io.ruin.model.content.bestinslot;

import io.ruin.cache.ObjType;
import io.ruin.model.ScrollbarClientScript;
import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.InterfaceHandler;
import io.ruin.model.inter.ToplevelComponent;
import io.ruin.model.inter.actions.SimpleAction;
import io.ruin.model.item.Item;
import io.ruin.model.item.containers.Equipment;
import io.ruin.network.PacketSender;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Best in Slot (user 2026-11-15; interface 1187 from the user's mock-up, .dev BuildKarnHub + cache stage 42): pick an
 * equipment slot on the left and a style (Stab / Slash / Crush / Magic / Range attack bonus); the list ranks every
 * wearable item of that slot by that bonus (same-name copies counted once, top 40). Clicking a row shows its model, its
 * rank and its five attack bonuses against the item worn in that slot; Compare prints every bonus difference.
 * ::bis opens it.
 */
public final class BestInSlot {

	private BestInSlot() {
	}

	public static final int INTERFACE = 1187;
	private static final int CLOSE = 5, FIRST_CELL = 7, CELL_STRIDE = 2, WORN = 29, FIRST_STYLE = 32, STYLE_STRIDE = 3,
			HEAD_ITEM = 48, HEAD_VALUE = 49, SCROLL = 50, SCROLLBAR = 51, FIRST_ROW = 52, ROW_STRIDE = 18, ROWS = 40,
			ROW_PITCH = 36, SCROLL_HEIGHT = 173, MODEL = 772, NAME = 773, TYPE = 774, RANK = 775, FIRST_STAT = 776, COMPARE = 786,
			CMP = 787, CMP_BACK = 790, CMP_NAMES = 792, CMP_ITEM_A = 795, CMP_ITEM_B = 796, CMP_ROW = 798;
	/** the grid cells, in BuildKarnHub CELLS order */
	private static final int[] SLOTS = {Equipment.SLOT_HAT, Equipment.SLOT_CAPE, Equipment.SLOT_AMULET, Equipment.SLOT_AMMO,
			Equipment.SLOT_WEAPON, Equipment.SLOT_CHEST, Equipment.SLOT_SHIELD, Equipment.SLOT_HANDS, Equipment.SLOT_LEGS,
			Equipment.SLOT_FEET, Equipment.SLOT_RING};
	private static final String[] SLOT_NAMES = {"Head", "Cape", "Neck", "Ammo", "Weapon", "Body", "Shield", "Hands", "Legs", "Feet", "Ring"};
	private static final String[] STYLES = {"Stab", "Slash", "Crush", "Magic", "Range"};
	private static final String GREEN = "<col=5fe07a>", RED = "<col=ff6060>", GREY = "<col=b8a890>", END = "</col>";
	private static final String CELL_KEY = "bis_cell", STYLE_KEY = "bis_style", ROW_KEY = "bis_row", CMP_KEY = "bis_cmp";

	/** (cell, style) -> ranked item ids */
	private static final Map<Integer, List<Integer>> RANKS = new HashMap<>();
	/** every wearable item with bonuses, grouped by base name (variants, degrade steps, ornament / imbue tags merged) */
	private static Map<String, List<ObjType>> groups;

	/**
	 * Holiday / cosmetic / staff items are not gear (user 2026-11-15: "remove the partyhats, santas and hween").
	 */
	private static final java.util.regex.Pattern EXCLUDED = java.util.regex.Pattern.compile(
			"partyhat|santa|h'?ween|halloween|cracker|bunny|easter|christmas|xmas|pumpkin|jester|costume|cosmetic|ornament"
					+ "|\\badmin\\b|developer|\\bowner\\b|\\btest\\b|placeholder"
					// user 2026-11-15: not BiS gear on Karn
					+ "|hallowed cape|ranged cape|tainted wand|infernal cape \\(broken\\)", java.util.regex.Pattern.CASE_INSENSITIVE);
	private static final java.util.regex.Pattern TAGS = java.util.regex.Pattern.compile(
			"\\s*\\((?:i|or|g|t|h\\d|kt|l|cr|deg|degraded|uncharged|charged|broken|inactive|basic|attuned|perfected|full|e|u|nz|wrapped|corrupted|c)\\)",
			java.util.regex.Pattern.CASE_INSENSITIVE);

	/** "New crystal halberd full (i)", "Crystal halberd 610", "Dharok's helm 75" -> "crystal halberd", "dharok's helm" */
	static String base(String name) {
		String n = name.toLowerCase().trim().replaceFirst("^new\\s+", "");
		n = TAGS.matcher(n).replaceAll("");
		// round 2 (user: "still duplicates"): every bracketed variant tag goes ("(fire)", "(max)", "(beta)" ...), and
		// "'s" / punctuation, so "Inquisitor's great helm" and its variants are one entry
		n = n.replaceAll("\\([^)]*\\)", " ").replace("'s ", " ").replaceAll("[^a-z0-9 ]", " ");
		n = n.replaceAll("\\s+", " ").trim();
		for (int k = 0; k < 2; k++)
			n = n.replaceFirst("\\s+(?:full|\\d+)$", "");
		return n.trim();
	}

	/**
	 * Items with no source on Karn (no drop table, shop, box, recipe or code gives them; checked 2026-11-15 across the
	 * data and every server module): left out with all their variants (user: "if none of these have any source remove
	 * them from bis"). The Karn master cape stays on purpose (the user's best cape).
	 * Hellfang helm / platebody / platelegs / crossbow left the list 2026-11-17: they drop from The Five Seals (FiveSealsLoot).
	 */
	private static final int[] NO_SOURCE = {25413, 30621, 59617, 43827, 42686, 42432, 30300, 59570, 23603, 24234, 23607,
			24232, 23605, 24233, 44497, 44296, 43975, 43923, 42668, 27502, 22230, 22229, 22227, 22228, 30414, 59560,
			59561, 22335, 59569, 24617, 25404, 44191, 43828, 42688, 42434, 30303, 25389, 59594, 59572, 59618, 24186, 22284,
			22278, 22275, 22281, 22272, 25407, 42692, 42440, 25392, 25416, 44192, 43829, 42690, 42436, 25401, 59595, 59571,
			59619, 25410, 42694, 42438, 25395, 59625, 42642};

	private static synchronized Map<String, List<ObjType>> groups() {
		if (groups == null) {
			java.util.Set<String> noSource = new java.util.HashSet<>();
			for (int id : NO_SOURCE) {
				ObjType d = ObjType.get(id);
				if (d != null && d.name != null)
					noSource.add(base(d.name));
			}
			Map<String, List<ObjType>> g = new HashMap<>();
			ObjType.forEach(def -> {
				if (def == null || def.equipSlot < 0 || def.equipBonuses == null || def.name == null || def.name.isEmpty()
						|| def.name.equalsIgnoreCase("null") || def.isNote() || def.isPlaceholder() || EXCLUDED.matcher(def.name).find())
					return;
				String key = base(def.name);
				if (!noSource.contains(key))
					g.computeIfAbsent(key, k -> new ArrayList<>()).add(def);
			});
			groups = g;
		}
		return groups;
	}

	private static List<Integer> ranking(int cell, int style) {
		return RANKS.computeIfAbsent(cell * 10 + style, k -> {
			int slot = SLOTS[cell];
			List<ObjType> list = new ArrayList<>();
			for (List<ObjType> group : groups().values()) {
				// the group's slot is the original's (lowest id): a stray copy with a wrong slot can't move it
				ObjType original = group.stream().min(Comparator.comparingInt(d -> d.id)).orElse(null);
				if (original == null || original.equipSlot != slot)
					continue;
				ObjType best = group.stream().filter(d -> d.equipSlot == slot)
						.max(Comparator.<ObjType>comparingInt(d -> bonus(d, style)).thenComparingInt(d -> -d.id)).orElse(null);
				if (best != null && bonus(best, style) > 0)
					list.add(best);
			}
			list.sort(Comparator.<ObjType>comparingInt(d -> bonus(d, style)).reversed().thenComparing(d -> d.name));
			List<Integer> ids = new ArrayList<>();
			for (ObjType d : list)
				ids.add(d.id);
			return ids;
		});
	}

	public static void register() {
		InterfaceHandler.register(INTERFACE, h -> {
			h.actions[CLOSE] = (SimpleAction) Player::closeInterfaces;
			for (int c = 0; c < SLOTS.length; c++) {
				int cell = c;
				h.actions[FIRST_CELL + c * CELL_STRIDE + 1] = (SimpleAction) p -> {
					p.set(CELL_KEY, cell);
					p.set(ROW_KEY, 0);
					refresh(p, true);
				};
			}
			for (int s = 0; s < STYLES.length; s++) {
				int style = s;
				h.actions[FIRST_STYLE + s * STYLE_STRIDE] = (SimpleAction) p -> {
					p.set(STYLE_KEY, style);
					p.set(ROW_KEY, 0);
					refresh(p, true);
				};
			}
			for (int r = 0; r < ROWS; r++) {
				int row = r;
				h.actions[FIRST_ROW + r * ROW_STRIDE + 1] = (SimpleAction) p -> {
					p.set(ROW_KEY, row);
					refresh(p, false);
				};
			}
			h.actions[COMPARE] = (SimpleAction) p -> showCompare(p, !p.<Boolean>get(CMP_KEY, false));
			h.actions[CMP_BACK] = (SimpleAction) p -> showCompare(p, false);
		});
	}

	public static void open(Player p) {
		p.set(CMP_KEY, false);
		p.openInterface(ToplevelComponent.MAINMODAL, INTERFACE);
		refresh(p, true);
	}

	/** the compare page over the list (its tabs and rows are hidden underneath so they can't be clicked through it) */
	private static void showCompare(Player p, boolean on) {
		p.set(CMP_KEY, on);
		PacketSender ps = p.getPacketSender();
		ps.setHidden(INTERFACE, CMP, !on);
		ps.setHidden(INTERFACE, SCROLL, on);
		ps.setHidden(INTERFACE, SCROLLBAR, on);
		for (int s = 0; s < STYLES.length; s++)
			ps.setHidden(INTERFACE, FIRST_STYLE + s * STYLE_STRIDE, on);
		if (on)
			sendCompare(p);
	}

	private static String shorten(String s, int max) {
		return s.length() <= max ? s : s.substring(0, max - 2).trim() + "..";
	}

	private static String signed(int v) {
		return v > 0 ? GREEN + "+" + v + END : v < 0 ? RED + v + END : GREY + "0" + END;
	}

	private static void refresh(Player p, boolean listChanged) {
		PacketSender ps = p.getPacketSender();
		if (p.<Boolean>get(CMP_KEY, false))
			sendCompare(p);
		int cell = p.<Integer>get(CELL_KEY, 0), style = p.<Integer>get(STYLE_KEY, 0), sel = p.<Integer>get(ROW_KEY, 0);
		List<Integer> list = ranking(cell, style);
		int shown = Math.min(ROWS, list.size());
		if (sel >= shown)
			sel = 0;
		for (int c = 0; c < SLOTS.length; c++)
			ps.setHidden(INTERFACE, FIRST_CELL + c * CELL_STRIDE, c != cell);
		for (int s = 0; s < STYLES.length; s++)
			ps.setHidden(INTERFACE, FIRST_STYLE + s * STYLE_STRIDE + 1, s != style);
		ps.sendString(INTERFACE, HEAD_ITEM, "Item (" + SLOT_NAMES[cell] + ")");
		ps.sendString(INTERFACE, HEAD_VALUE, STYLES[style] + " Attack");

		int top = list.isEmpty() ? 1 : Math.max(1, ObjType.get(list.get(0)).equipBonuses[style]);
		for (int r = 0; r < ROWS; r++) {
			int base = FIRST_ROW + r * ROW_STRIDE;
			boolean has = r < shown;
			if (listChanged || r == sel || has)
				ps.setHidden(INTERFACE, base, !has);
			if (!has)
				continue;
			ObjType d = ObjType.get(list.get(r));
			ps.setHidden(INTERFACE, base + 2, r != sel);
			if (!listChanged)
				continue;
			int value = d.equipBonuses[style];
			ps.sendString(INTERFACE, base + 3, String.valueOf(r + 1));
			ps.sendItem(INTERFACE, base + 4, d.id, 1);
			ps.sendString(INTERFACE, base + 5, shorten(d.name, 16));
			int fill = (int) Math.ceil(10.0 * value / top);
			for (int s = 0; s < 10; s++)
				ps.setHidden(INTERFACE, base + 7 + s, s >= fill);
			ps.sendString(INTERFACE, base + 17, String.valueOf(value));
		}
		if (listChanged) {
			int contentHeight = shown * ROW_PITCH - 2;
			int count = contentHeight <= SCROLL_HEIGHT ? 0 : (int) Math.ceil((contentHeight - SCROLL_HEIGHT / 3D) / 15D);
			ScrollbarClientScript.create().interfaceId(INTERFACE).containerId(SCROLL).scrollbarChildId(SCROLLBAR)
					.childrenCount(count).withOrnateGraphics().build().send(p);
		}

		// what is worn in this slot
		Item worn = p.getEquipment().get(SLOTS[cell]);
		ObjType wornDef = worn == null ? null : worn.getDef();
		int wornRank = wornDef == null ? -1 : rankOf(list, wornDef);
		ps.sendString(INTERFACE, WORN, wornDef == null ? GREY + "Nothing worn" + END : shorten(wornDef.name, 16));
		ps.sendString(INTERFACE, WORN + 1, wornDef == null ? "" : STYLES[style] + ": " + bonus(wornDef, style));
		ps.sendString(INTERFACE, WORN + 2, wornDef == null ? "" : wornRank < 0 ? "Not ranked" : "Rank " + (wornRank + 1) + " of " + list.size());

		// the picked item
		if (shown == 0) {
			ps.setHidden(INTERFACE, MODEL, true);
			ps.sendString(INTERFACE, NAME, "No items");
			ps.sendString(INTERFACE, TYPE, SLOT_NAMES[cell] + " · " + STYLES[style]);
			ps.sendString(INTERFACE, RANK, "");
			for (int s = 0; s < STYLES.length; s++)
				ps.sendString(INTERFACE, FIRST_STAT + s * 2 + 1, "");
			return;
		}
		ObjType d = ObjType.get(list.get(sel));
		ps.setHidden(INTERFACE, MODEL, false);
		ps.sendItem(INTERFACE, MODEL, d.id, 1);
		// IfSetObject zooms to the box width (zoom2d * 32 / 116 = 3.6x an icon): ~1.6x an icon fits the 64px stage
		ps.sendModelInformation(INTERFACE, MODEL, Math.max(100, d.zoom2d * 62 / 100), d.xan2d, d.yan2d);
		ps.sendString(INTERFACE, NAME, shorten(d.name, 16));
		ps.sendString(INTERFACE, TYPE, SLOT_NAMES[cell] + " · " + STYLES[style]);
		ps.sendString(INTERFACE, RANK, sel == 0 ? "#1 Best in Slot" : "Rank " + (sel + 1) + " of " + list.size());
		for (int s = 0; s < STYLES.length; s++)
			ps.sendString(INTERFACE, FIRST_STAT + s * 2 + 1, signed(bonus(d, s) - (wornDef == null ? 0 : bonus(wornDef, s))));
	}

	private static int bonus(ObjType d, int i) {
		return d.equipBonuses == null || d.equipBonuses.length <= i ? 0 : d.equipBonuses[i];
	}

	private static int rankOf(List<Integer> list, ObjType d) {
		for (int i = 0; i < list.size(); i++)
			if (list.get(i) == d.id || base(ObjType.get(list.get(i)).name).equals(base(d.name)))
				return i;
		return -1;
	}

	/** fills the compare page: the picked item against what is worn in that slot, all 14 bonuses, the difference coloured */
	private static void sendCompare(Player p) {
		PacketSender ps = p.getPacketSender();
		int cell = p.<Integer>get(CELL_KEY, 0), style = p.<Integer>get(STYLE_KEY, 0), sel = p.<Integer>get(ROW_KEY, 0);
		List<Integer> list = ranking(cell, style);
		ObjType d = list.isEmpty() ? null : ObjType.get(list.get(Math.min(sel, list.size() - 1)));
		Item worn = p.getEquipment().get(SLOTS[cell]);
		ObjType w = worn == null ? null : worn.getDef();
		ps.sendString(INTERFACE, CMP_NAMES, (d == null ? "-" : shorten(d.name, 17)) + GREY + "  vs  " + END
				+ (w == null ? "nothing worn" : shorten(w.name, 17)));
		ps.setHidden(INTERFACE, CMP_ITEM_A, d == null);
		if (d != null)
			ps.sendItem(INTERFACE, CMP_ITEM_A, d.id, 1);
		ps.setHidden(INTERFACE, CMP_ITEM_B, w == null);
		if (w != null)
			ps.sendItem(INTERFACE, CMP_ITEM_B, w.id, 1);
		for (int i = 0; i < 14; i++) {
			int base = CMP_ROW + i * 4, a = d == null ? 0 : bonus(d, i), b = w == null ? 0 : bonus(w, i);
			String suffix = i == 12 ? "%" : "";
			ps.sendString(INTERFACE, base + 1, a + suffix);
			ps.sendString(INTERFACE, base + 2, w == null ? GREY + "-" + END : b + suffix);
			ps.sendString(INTERFACE, base + 3, signed(a - b));
		}
	}
}

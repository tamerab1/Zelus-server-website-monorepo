package io.ruin.model.item.actions.impl.pet.perk;

import io.ruin.cache.NPCType;
import io.ruin.cache.ObjType;
import io.ruin.model.ScrollbarClientScript;
import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.InterfaceHandler;
import io.ruin.model.inter.ToplevelComponent;
import io.ruin.model.inter.actions.SimpleAction;
import io.ruin.model.item.actions.impl.pet.Pet;

import java.util.ArrayList;
import java.util.List;

/**
 * The ::petperks window (interface 1141, built by .dev/cache-restore-tool/src/BuildPetPerksInterface.java):
 * one tab per perk category, the selected pet's rotating model with its perk lines, and a clickable list
 * of the category's pets.
 */
public final class PetPerksInterface {

	private static final int INTERFACE = 1141;
	private static final int CLOSE = 2, TITLE = 3, MODEL = 8, FIRST_HEADER = 9;
	/** Rows: 16 + 2r is the clickable bar (hover), 17 + 2r its text. */
	private static final int SCROLL = 14, SCROLLBAR = 15, FIRST_ROW = 16, ROW_STRIDE = 2, ROW_TEXT = 1, ROWS = 40;
	private static final int[] TAB_BUTTONS = {96, 98, 100, 102, 104}, TAB_LABELS = {97, 99, 101, 103, 105};
	private static final int SCROLL_HEIGHT = 160, ROW_HEIGHT = 12;

	private static final PerkType[] CATEGORIES = {PerkType.PET_MELEE_BOOST, PerkType.PET_MAGE_BOOST,
			PerkType.PET_RANGED_BOOST, PerkType.PET_UTILITY_BOOST, PerkType.PET_DROP_RATE_BOOST};
	private static final String[] TAB_NAMES = {"Melee pets", "Magic pets", "Ranged pets", "Utility pets", "Drop rate pets"};

	private static final double MODEL_ZOOM_SCALE = 2.0;

	/**
	 * Zoom at which each perk pet fits its 36x32 icon with a 2px margin, measured from the cache (FitZoom tool,
	 * 2026-10-12). Several pets' own icon zoom is cropped (Starlight 748 -> 3429, ToxiSkele 1163 -> 2349), so
	 * scaling from it overflowed the window. Pets missing here fall back to their icon zoom.
	 */
	private static final java.util.Map<Integer, Integer> FIT_ZOOM = java.util.Map.ofEntries(
			java.util.Map.entry(60236, 2373), java.util.Map.entry(60237, 3835), java.util.Map.entry(60238, 2811),
			java.util.Map.entry(60239, 4300), java.util.Map.entry(60240, 2373), java.util.Map.entry(60241, 1878),
			java.util.Map.entry(60242, 2912), java.util.Map.entry(60243, 1991), java.util.Map.entry(60244, 3429),
			java.util.Map.entry(60245, 1926), java.util.Map.entry(60246, 1771), java.util.Map.entry(60247, 2029),
			java.util.Map.entry(60248, 2867), java.util.Map.entry(60249, 2552), java.util.Map.entry(60250, 2923),
			java.util.Map.entry(60251, 2573), java.util.Map.entry(60252, 2900), java.util.Map.entry(60253, 3372),
			java.util.Map.entry(60254, 1702), java.util.Map.entry(60255, 2349));
	private static final int SPIN_STEP = 48;

	private static final String TAB_KEY = "petperks_tab", PET_KEY = "petperks_pet", SPIN_KEY = "petperks_spin",
			ROWS_KEY = "petperks_rows";

	private static final String GOLD = "<col=ffc35a>", GREY = "<col=9a9a9a>", WHITE = "<col=ffffff>",
			GREEN = "<col=50e050>", END = "</col>";

	private PetPerksInterface() {
	}

	public static void register() {
		InterfaceHandler.register(INTERFACE, h -> {
			h.actions[CLOSE] = (SimpleAction) player -> player.closeInterface(ToplevelComponent.MAINMODAL);
			for (int t = 0; t < TAB_BUTTONS.length; t++) {
				int tab = t;
				h.actions[TAB_BUTTONS[t]] = (SimpleAction) player -> show(player, tab, null);
			}
			for (int r = 0; r < ROWS; r++) {
				int row = r;
				h.actions[FIRST_ROW + r * ROW_STRIDE] = (SimpleAction) player -> {
					Pet[] rowPets = player.get(ROWS_KEY);
					if (rowPets != null && row < rowPets.length && rowPets[row] != null)
						show(player, player.<Integer>get(TAB_KEY, 0), rowPets[row]);
				};
			}
		});
	}

	/** Opens on the summoned pet's category (and that pet) when it has a perk, else on melee pets. */
	public static void open(Player player) {
		player.openInterface(ToplevelComponent.MAINMODAL, INTERFACE);
		player.getPacketSender().sendString(INTERFACE, TITLE, "Pet Perks");
		Pet active = player.pet;
		int tab = 0;
		if (active != null && active.perk != null)
			for (int t = 0; t < CATEGORIES.length; t++)
				if (CATEGORIES[t] == active.perk.type)
					tab = t;
		show(player, tab, active != null && active.perk != null ? active : null);
	}

	private static void show(Player player, int tab, Pet focus) {
		var ps = player.getPacketSender();
		player.set(TAB_KEY, tab);
		for (int t = 0; t < TAB_LABELS.length; t++)
			ps.sendString(INTERFACE, TAB_LABELS[t], t == tab ? WHITE + TAB_NAMES[t] + END : TAB_NAMES[t]);

		List<Pet> pets = petsOf(CATEGORIES[tab]);
		if (focus == null || !pets.contains(focus))
			focus = pets.isEmpty() ? null : pets.get(0);
		player.set(PET_KEY, focus);

		// header: selected pet
		List<String> header = new ArrayList<>();
		if (focus == null) {
			header.add("No pets yet");
			header.add(GREY + "This category has no perk pets." + END);
		} else {
			// name (green while summoned) + up to 3 perk lines
			header.add(player.pet == focus ? GREEN + petName(focus) + END : petName(focus));
			for (String line : focus.perk.describeLines())
				header.add(WHITE + line + END);
		}
		for (int r = 0; r < 4; r++)
			ps.sendString(INTERFACE, FIRST_HEADER + r, r < header.size() ? header.get(r) : "");
		showModel(player, focus);

		// rows: two per pet (name, compact perk); both select that pet
		List<String> rows = new ArrayList<>();
		Pet[] rowPets = new Pet[ROWS];
		rows.add(GREY + "A pet's perk works while it is summoned. Click a" + END);
		rows.add(GREY + "pet to view it." + END);
		for (Pet pet : pets) {
			// the perk summary wraps onto indented rows instead of running under the scrollbar ("+10% defe")
			List<String> perkRows = wrap(java.util.Arrays.asList(pet.perk.describeLines()), PERK_ROW_CHARS);
			if (rows.size() + 2 + perkRows.size() > ROWS)
				break;
			rows.add("");
			boolean selected = pet == focus, summoned = player.pet == pet;
			rowPets[rows.size()] = pet;
			rows.add((selected ? GOLD : WHITE) + petName(pet) + END + (summoned ? GREEN + "  (summoned)" + END : ""));
			for (int k = 0; k < perkRows.size(); k++) {
				rowPets[rows.size()] = pet;
				rows.add(GREY + (k == 0 ? "" : "   ") + perkRows.get(k) + END);
			}
		}
		player.set(ROWS_KEY, rowPets);
		for (int r = 0; r < ROWS; r++) {
			ps.sendString(INTERFACE, FIRST_ROW + r * ROW_STRIDE + ROW_TEXT, r < rows.size() ? rows.get(r) : "");
			// only a pet's rows get the clickable hover bar (not the info lines or the gaps)
			ps.setHidden(INTERFACE, FIRST_ROW + r * ROW_STRIDE, rowPets[r] == null);
		}
		updateScrollbar(player, rows.size());
	}

	private static void showModel(Player player, Pet pet) {
		var ps = player.getPacketSender();
		int spin = player.<Integer>get(SPIN_KEY, 0) + 1;
		player.set(SPIN_KEY, spin);
		if (pet == null) {
			ps.setHidden(INTERFACE, MODEL, true);
			return;
		}
		ObjType def = ObjType.get(pet.itemId);
		int zoom = (int) (FIT_ZOOM.getOrDefault(pet.itemId, def.zoom2d) / MODEL_ZOOM_SCALE);
		ps.setHidden(INTERFACE, MODEL, false);
		ps.sendItem(INTERFACE, MODEL, pet.itemId, 1);
		ps.sendModelInformation(INTERFACE, MODEL, zoom, def.xan2d, def.yan2d);
		NPCType npc = NPCType.get(pet.npcId);
		if (npc != null && npc.standAnimation != -1)
			ps.animateInterface(INTERFACE, MODEL, npc.standAnimation);
		player.addEvent(e -> {
			int yan = def.yan2d;
			while (true) {
				e.delay(1);
				if (!player.isOnline() || !player.isVisibleInterface(INTERFACE) || player.<Integer>get(SPIN_KEY, 0) != spin)
					return;
				yan = (yan + SPIN_STEP) & 2047;
				ps.sendModelInformation(INTERFACE, MODEL, zoom, def.xan2d, yan);
			}
		});
	}

	/** Characters of perk text that fit one list row in the 11px font (the row is ~200px wide next to the scrollbar). */
	private static final int PERK_ROW_CHARS = 42;

	/** Joins the perk lines with ", " and breaks them into rows of at most max characters, at ", " where possible. */
	private static List<String> wrap(List<String> parts, int max) {
		List<String> out = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (int i = 0; i < parts.size(); i++) {
			String piece = parts.get(i) + (i + 1 < parts.size() ? "," : "");
			if (line.length() > 0 && line.length() + 1 + piece.length() > max) {
				out.add(line.toString());
				line.setLength(0);
			}
			if (line.length() > 0)
				line.append(' ');
			line.append(piece);
		}
		if (line.length() > 0)
			out.add(line.toString());
		return out;
	}

	/** Sizes the scroll area to the rows in use (clientscript 10532: count * 15 + height / 3). */
	private static void updateScrollbar(Player player, int rowsUsed) {
		int contentHeight = rowsUsed * ROW_HEIGHT + 4;
		int count = contentHeight <= SCROLL_HEIGHT ? 0 : (int) Math.ceil((contentHeight - SCROLL_HEIGHT / 3D) / 15D);
		ScrollbarClientScript.create()
				.interfaceId(INTERFACE)
				.containerId(SCROLL)
				.scrollbarChildId(SCROLLBAR)
				.childrenCount(count)
				.withDarkGraphics()
				.build()
				.send(player);
	}

	private static List<Pet> petsOf(PerkType type) {
		List<Pet> pets = new ArrayList<>();
		for (Pet pet : Pet.VALUES)
			if (pet.perk != null && pet.perk.type == type)
				pets.add(pet);
		return pets;
	}

	private static String petName(Pet pet) {
		ObjType def = ObjType.get(pet.itemId);
		return def != null && def.name != null ? def.name : pet.name();
	}

}

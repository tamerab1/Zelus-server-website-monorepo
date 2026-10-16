package io.ruin.model.entity.npc.combatpet;

import io.ruin.cache.NPCType;
import io.ruin.model.ScrollbarClientScript;
import io.ruin.cache.ObjType;
import io.ruin.model.entity.npc.NPCAction;
import io.ruin.model.entity.npc.PetNPC;
import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.InterfaceHandler;
import io.ruin.model.inter.actions.SimpleAction;
import io.ruin.model.inter.ToplevelComponent;
import io.ruin.model.item.actions.ItemAction;
import io.ruin.model.item.actions.impl.pet.Pet;

import java.util.ArrayList;
import java.util.List;

/**
 * The Ascended Bestiary (item 31746, cosmic-textured Arena book -- .dev/cache-restore-tool/bestiary_item.txt):
 * the core item of the combat pet system.
 * <ul>
 * <li>Combat pets only fight as timed bursts summoned with the book's "Spawn" option ({@link BestiaryBurst}),
 * paid for with charges ({@link BestiaryCharges}); the empty book is 31814.</li>
 * <li>A bursting pet is sent back (inventory, else bank) as soon as the book leaves the inventory.</li>
 * <li>The older assist pets (Graardor Jr. etc.) can still be dropped as plain followers, but don't fight.</li>
 * <li>"Read" / "Inspect" open interface 1139 (BuildBestiaryInterface) on the guide / active-pet tab, and
 * examining any combat pet (item or follower) opens its own pet page there, model included.</li>
 * </ul>
 */
public final class AscendedBestiary {

	public static final int ID = 31746;

	public static final String SUMMON_HINT = "Combat pets are summoned with the Spawn option of a charged Ascended Bestiary.";

	/* interface 1139 components (see BuildBestiaryInterface) */
	private static final int INTERFACE = 1139;
	private static final int CLOSE = 2, TITLE = 3, SECTION = 8, SCROLL = 10, SCROLLBAR = 11, FIRST_ROW = 12, ROWS = 40;
	private static final int[] TAB_BUTTONS = {52, 54, 56, 58}, TAB_LABELS = {53, 55, 57, 59};
	private static final int FIRST_INFO_ROW = 64, INFO_ROWS = 4;
	private static final int MODEL = 68;
	/** Scroll container size (BuildBestiaryInterface SCROLL_H) and row height. */
	private static final int SCROLL_HEIGHT = 218, ROW_HEIGHT = 12;
	/** Model rotation per tick while the pet page is open (2048 = full turn, so ~26s per turn). */
	private static final int SPIN_STEP = 48;
	private static final String SPIN_KEY = "bestiary_spin";
	/** The model box (120x140) is much bigger than an inventory icon, so the icon zoom is scaled down. */
	private static final double MODEL_ZOOM_SCALE = 2.6;

	private static final int TAB_GUIDE = 0, TAB_TIERS = 1, TAB_ROSTER = 2, TAB_ACTIVE = 3;
	private static final String[] TAB_NAMES = {"How it works", "Tiers & upgrades", "Pet roster", "Active pet"};

	private static final String GOLD = "<col=ffc35a>", GREY = "<col=9a9a9a>", WHITE = "<col=ffffff>", END = "</col>";
	private static final String[] TIER_COLOURS = {"<col=d09a5a>", "<col=22e8ff>", "<col=ff2020>"};
	private static final String[] TIER_THEMES = {"Bronze", "Electric", "Cosmic"};
	private static final String BULLET = GOLD + "»" + END + " ";

	/** The Sanctum hub's T3 pet showcase (npcs 30601-30606: copies of the T3 pets with an "Inspect" option). */
	private static final int[] SHOWCASE_NPCS = {30601, 30602, 30603, 30604, 30605, 30606};
	private static final Pet[] SHOWCASE_PETS = {Pet.DOUBT_T3, Pet.LUKE_T3, Pet.MALEDICTUS_T3, Pet.REANIMATED_TZHAAR_T3,
			Pet.JAL_XIL_T3, Pet.XAMPHUR_T3};

	private AscendedBestiary() {
	}

	public static void register() {
		ItemAction.registerInventory(ID, "read", (player, item) -> open(player, TAB_GUIDE));
		ItemAction.registerInventory(ID, "inspect", (player, item) -> open(player, TAB_ACTIVE));
		ItemAction.registerInventory(BestiaryCharges.EMPTY_ID, "read", (player, item) -> open(player, TAB_GUIDE));
		BestiaryCharges.register();
		BestiaryBurst.register();
		for (int i = 0; i < SHOWCASE_NPCS.length; i++) {
			Pet pet = SHOWCASE_PETS[i];
			NPCAction.register(SHOWCASE_NPCS[i], "inspect", (player, npc) -> showcase(player, pet));
		}
		InterfaceHandler.register(INTERFACE, h -> {
			h.actions[CLOSE] = (SimpleAction) player -> player.closeInterface(ToplevelComponent.MAINMODAL);
			for (int t = 0; t < TAB_BUTTONS.length; t++) {
				int tab = t;
				h.actions[TAB_BUTTONS[t]] = (SimpleAction) player -> show(player, tab);
			}
		});
	}

	/**
	 * Either form of the book (a burst that just used the last charges keeps going with an empty book).
	 * findItemExact, not hasId: hasId only matches items WITHOUT attributes, and a charged book carries "bestiary_charges".
	 */
	public static boolean carries(Player player) {
		return player.getInventory().findItemExact(ID) != null
				|| player.getInventory().findItemExact(BestiaryCharges.EMPTY_ID) != null;
	}

	/** Pets that cannot be summoned / kept out without the book. */
	public static boolean required(Pet pet) {
		return CombatPets.tierOf(pet) != null;
	}

	/**
	 * Called every tick for a combat pet follower. Returns false when the pet must not fight; dismisses tiered
	 * pets when the book is gone.
	 */
	static boolean check(PetNPC pet) {
		Player owner = pet.owner;
		if (owner == null || carries(owner))
			return true;
		if (required(pet.petType) && owner.isOnline() && !pet.isRemoved()) {
			if (Pet.pickup(owner, pet, pet.petType, true))
				owner.sendMessage("Without the Ascended Bestiary your companion returns to you.");
		}
		return false;
	}

	/* ---------------- interface ---------------- */

	private static void open(Player player, int tab) {
		player.openInterface(ToplevelComponent.MAINMODAL, INTERFACE);
		player.getPacketSender().sendString(INTERFACE, TITLE, "Ascended Bestiary");
		show(player, tab);
	}

	/**
	 * Examine on a combat pet item: opens its pet page. Returns false for anything that isn't a combat pet.
	 */
	public static boolean examineItem(Player player, int itemId) {
		if (BestiaryCharges.isBook(itemId)) {
			String charges = BestiaryCharges.describeCarried(player, itemId);
			if (charges != null)
				player.sendMessage(BestiaryCharges.TAG + charges + ".");
			return false; // the normal examine still runs
		}
		Pet pet = Pet.getByItemId(itemId);
		if (!CombatPets.isCombatPet(pet))
			return false;
		openPet(player, pet);
		return true;
	}

	/** Examine on a combat pet npc (anyone's follower). */
	public static boolean examineNpc(Player player, int npcId) {
		for (Pet pet : Pet.VALUES) {
			if (pet.npcId == npcId && CombatPets.isCombatPet(pet)) {
				openPet(player, pet);
				return true;
			}
		}
		return false;
	}

	private static void openPet(Player player, Pet pet) {
		player.openInterface(ToplevelComponent.MAINMODAL, INTERFACE);
		player.getPacketSender().sendString(INTERFACE, TITLE, "Ascended Bestiary");
		CombatPet active = activePet(player);
		show(player, TAB_ROSTER, pet, active != null && active.pet.petType == pet ? active : null);
	}

	/** Showcase "Inspect": the pet's page plus how it is summoned and obtained. */
	private static void showcase(Player player, Pet pet) {
		player.openInterface(ToplevelComponent.MAINMODAL, INTERFACE);
		player.getPacketSender().sendString(INTERFACE, TITLE, "Ascended Bestiary");
		List<String> extra = new ArrayList<>();
		extra.add("");
		extra.add(GOLD + "Summoning" + END);
		extra.add(BULLET + "Spawn it from a charged Ascended Bestiary:");
		extra.add("   " + BestiaryBurst.seconds(BestiaryBurst.DURATION_TICKS) + " seconds for " + BestiaryBurst.COST + " charges.");
		extra.add(BULLET + "Charge the book with Combat Pet Shards.");
		extra.add("");
		extra.add(GOLD + "How to get it" + END);
		extra.add(BULLET + "Tier 1 pets drop from the Obsidian Reliquary's");
		extra.add("   bosses (Harrowlord, Gloomfang, Venomtongue).");
		extra.add(BULLET + "Ascend T1 -> T2 with " + spores(CombatPetUpgrades.SPORES[0]) + ", T2 -> T3");
		extra.add("   with " + spores(CombatPetUpgrades.SPORES[1]) + " (use them on the pet item).");
		show(player, TAB_ROSTER, pet, null, extra);
	}

	private static void show(Player player, int tab) {
		CombatPet active = tab == TAB_ACTIVE ? activePet(player) : null;
		show(player, tab, active == null ? null : active.pet.petType, active);
	}

	/** @param pet when set, the panel shows that pet's page (with its model) instead of the tab's text. */
	private static void show(Player player, int tab, Pet pet, CombatPet live) {
		show(player, tab, pet, live, List.of());
	}

	private static void show(Player player, int tab, Pet pet, CombatPet live, List<String> extraRows) {
		var ps = player.getPacketSender();
		for (int t = 0; t < TAB_LABELS.length; t++)
			ps.sendString(INTERFACE, TAB_LABELS[t], t == tab ? WHITE + TAB_NAMES[t] + END : TAB_NAMES[t]);
		List<String> rows = pet != null ? petPage(pet, live) : switch (tab) {
			case TAB_TIERS -> tiers();
			case TAB_ROSTER -> roster();
			case TAB_ACTIVE -> noActivePet();
			default -> guide();
		};
		if (!extraRows.isEmpty()) {
			rows = new ArrayList<>(rows);
			rows.addAll(extraRows);
		}
		showModel(player, pet);
		ps.sendString(INTERFACE, SECTION, pet != null ? familyName(pet) : TAB_NAMES[tab]);
		for (int r = 0; r < ROWS; r++)
			ps.sendString(INTERFACE, FIRST_ROW + r, r < rows.size() ? rows.get(r) : "");
		updateScrollbar(player, Math.min(rows.size(), ROWS));
		List<String> info = summary(player);
		for (int r = 0; r < INFO_ROWS; r++)
			ps.sendString(INTERFACE, FIRST_INFO_ROW + r, r < info.size() ? info.get(r) : "");
	}

	// Rows are written to fit the 286px panel in the small font (about 48 characters), never wrapping.

	private static List<String> guide() {
		List<String> r = new ArrayList<>();
		r.add(GOLD + "Charges" + END);
		r.add(BULLET + "Use Combat Pet Shards on the book: 1 shard is");
		r.add("   1 charge, up to " + BestiaryCharges.MAX_CHARGES + ".");
		r.add(BULLET + "An empty book awakens at " + BestiaryCharges.ACTIVATE_AT + " charges, and");
		r.add("   becomes empty again when it runs out.");
		r.add(BULLET + "Use a duplicate pet on the book to dismantle");
		r.add("   it into " + BestiaryCharges.DISMANTLE_SHARDS + " shards.");
		r.add("");
		r.add(GOLD + "Spawning" + END);
		r.add(BULLET + "Spawn: a pet from your inventory fights for");
		r.add("   " + BestiaryBurst.seconds(BestiaryBurst.DURATION_TICKS) + " seconds, for " + BestiaryBurst.COST + " charges.");
		r.add(BULLET + "It returns early only if you die or enter");
		r.add("   the Wilderness. Only one pet at a time.");
		r.add("");
		r.add(GOLD + "In combat" + END);
		r.add(BULLET + "Your pet attacks whatever monster you attack.");
		r.add(BULLET + "PvM only: it never targets players.");
		r.add(BULLET + "Stop fighting or move 10+ tiles away and it");
		r.add("   breaks off and returns to your side.");
		r.add(BULLET + "Its damage counts as yours for loot.");
		r.add("");
		r.add(GOLD + "Specials" + END);
		r.add(BULLET + "A guaranteed special every 8 / 7 / 6 ticks of");
		r.add("   the pet's own attacks at Tier 1 / 2 / 3.");
		r.add("");
		r.add(GOLD + "Ascending" + END);
		r.add(BULLET + "Use Astral Spores on a pet item to raise its tier.");
		return r;
	}

	private static List<String> tiers() {
		List<String> r = new ArrayList<>();
		for (PetTier t : PetTier.values()) {
			int[] hits = maxHitRange(t);
			r.add(TIER_COLOURS[t.ordinal()] + "Tier " + t.number() + "  -  " + TIER_THEMES[t.ordinal()] + END);
			r.add("Special every " + WHITE + t.specialIntervalTicks + " ticks" + END + "     Max hit " + WHITE
					+ hits[0] + "-" + hits[1] + END);
			if (t == PetTier.T1)
				r.add(GREY + "Starting tier" + END);
			else
				r.add("Ascend from T" + (t.number() - 1) + ": " + WHITE + spores(CombatPetUpgrades.SPORES[t.ordinal() - 1]) + END);
			if (t.effectScale > 1)
				r.add("Special effects " + Math.round(t.effectScale * 100 - 100) + "% stronger");
			r.add("");
		}
		r.add(GOLD + "How to ascend" + END);
		r.add(BULLET + "Have the pet item in your inventory.");
		r.add(BULLET + "Use Astral Spores on it. The pet keeps its");
		r.add("   name and takes on the new tier's look.");
		return r;
	}

	private static List<String> roster() {
		List<String> r = new ArrayList<>();
		for (Pet pet : CombatPets.tieredPets()) {
			if (CombatPets.tierOf(pet) != PetTier.T1)
				continue;
			Pet t2 = CombatPets.nextTier(pet), t3 = CombatPets.nextTier(t2);
			CombatPetProfile p1 = CombatPets.profileOf(pet);
			r.add(GOLD + familyName(pet) + END + "  " + GREY + styleName(p1) + END);
			r.add("Max hit " + WHITE + p1.maxHit + " / " + CombatPets.profileOf(t2).maxHit + " / "
					+ CombatPets.profileOf(t3).maxHit + END + "     " + p1.specialName());
			r.add(GREY + p1.specialEffect() + END);
		}
		return r;
	}

	private static List<String> noActivePet() {
		List<String> r = new ArrayList<>();
		r.add("No combat pet is following you.");
		r.add("");
		r.add(BULLET + "Use the book's Spawn option with a combat");
		r.add("   pet item in your inventory.");
		r.add(BULLET + "Examine any combat pet to see its page.");
		return r;
	}

	/**
	 * One pet's page. Rows 0-11 sit beside the model box (about 28 characters); the rest use the full width.
	 */
	private static List<String> petPage(Pet pet, CombatPet live) {
		List<String> r = new ArrayList<>();
		CombatPetProfile profile = CombatPets.profileOf(pet);
		PetTier tier = CombatPets.tierOf(pet);
		r.add(GOLD + name(pet) + END);
		r.add("Style: " + WHITE + styleName(profile) + END);
		r.add(tier == null ? "" : "Tier: " + TIER_COLOURS[tier.ordinal()] + tier.number() + " - " + TIER_THEMES[tier.ordinal()] + END);
		r.add("Max hit: " + WHITE + profile.maxHit + END);
		r.add("Speed: " + WHITE + profile.attackTicks + " ticks" + END);
		r.add(live == null ? "" : "Status: " + WHITE + (live.isEngaged() ? "Fighting" : "Idle") + END);
		r.add("");
		r.add(GOLD + "Special" + END);
		r.add(WHITE + profile.specialName() + END);
		r.add(profile.specialIntervalTicks > 0 ? "Every " + profile.specialIntervalTicks + " ticks of its attacks" : "Random chance");
		r.add(live != null && profile.specialIntervalTicks > 0 ? "Next: " + WHITE + specialReadyIn(live) + END : "");
		r.add("");
		r.add(GREY + profile.specialEffect() + END);
		if (tier != null) {
			r.add("");
			r.add(GOLD + "Ascension" + END);
			Pet next = CombatPets.nextTier(pet);
			if (next == null) {
				r.add("Fully ascended: this is its Tier 3 form.");
			} else {
				r.add("Next: " + WHITE + name(next) + END);
				r.add("Cost: " + WHITE + spores(CombatPetUpgrades.SPORES[tier.ordinal()]) + END + "  (use them on the pet item)");
			}
		}
		return r;
	}

	/**
	 * Sizes the scroll area to the rows in use (clientscript 10532 makes it count * 15 + height / 3) and
	 * scrolls back to the top; content that fits gets no scrolling.
	 */
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

	private static void showModel(Player player, Pet pet) {
		var ps = player.getPacketSender();
		int spin = player.<Integer>get(SPIN_KEY, 0) + 1;
		player.set(SPIN_KEY, spin); // stops the previous page's rotation loop
		if (pet == null) {
			ps.setHidden(INTERFACE, MODEL, true);
			return;
		}
		ObjType def = ObjType.get(pet.itemId);
		ps.setHidden(INTERFACE, MODEL, false);
		// Sent as the ITEM so the client applies the tier's recolour/retexture (same as ItemStatsViewer).
		ps.sendItem(INTERFACE, MODEL, pet.itemId, 1);
		ps.sendModelInformation(INTERFACE, MODEL, (int) (def.zoom2d / MODEL_ZOOM_SCALE), def.xan2d, def.yan2d);
		NPCType npc = NPCType.get(pet.npcId);
		if (npc != null && npc.standAnimation != -1)
			ps.animateInterface(INTERFACE, MODEL, npc.standAnimation);
		// slow turntable while this page stays open
		int zoom = (int) (def.zoom2d / MODEL_ZOOM_SCALE);
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

	private static String spores(int amount) {
		return amount + " Astral Spore" + (amount == 1 ? "" : "s");
	}

	/** Left-hand box under the tabs (about 25 characters per row). */
	private static List<String> summary(Player player) {
		List<String> r = new ArrayList<>();
		r.add(GOLD + "Active pet" + END);
		CombatPet active = activePet(player);
		if (active == null) {
			r.add(GREY + "None" + END);
			r.add(carries(player) ? "Charges: " + WHITE + carriedCharges(player) + "/" + BestiaryCharges.MAX_CHARGES + END
					: "<col=ff5050>Book not in inventory" + END);
			return r;
		}
		PetTier tier = CombatPets.tierOf(active.pet.petType);
		r.add(WHITE + name(active.pet.petType) + END);
		r.add((tier == null ? "" : TIER_COLOURS[tier.ordinal()] + "Tier " + tier.number() + END + " - ") + styleName(active.profile));
		r.add((active.isEngaged() ? "Fighting" : "Idle")
				+ (active.isBursting() ? " - " + WHITE + BestiaryBurst.seconds(active.getTicksRemaining()) + "s left" + END : ""));
		return r;
	}

	/** Charges of the first book in the inventory (active form first). */
	private static int carriedCharges(Player player) {
		var book = player.getInventory().findItemExact(ID);
		if (book == null)
			book = player.getInventory().findItemExact(BestiaryCharges.EMPTY_ID);
		return book == null ? 0 : BestiaryCharges.charges(book);
	}

	private static CombatPet activePet(Player player) {
		return player.familiarNPC instanceof PetNPC p && !p.isRemoved() ? p.combatPet : null;
	}

	private static String specialReadyIn(CombatPet active) {
		int left = Math.max(0, active.profile.specialIntervalTicks - active.ticksSinceLastSpecial);
		return left == 0 ? "next attack" : "in " + left + " attack ticks";
	}

	private static int[] maxHitRange(PetTier tier) {
		int lo = Integer.MAX_VALUE, hi = 0;
		for (Pet pet : CombatPets.tieredPets()) {
			if (CombatPets.tierOf(pet) != tier)
				continue;
			int max = CombatPets.profileOf(pet).maxHit;
			lo = Math.min(lo, max);
			hi = Math.max(hi, max);
		}
		return new int[]{lo, hi};
	}

	private static String name(Pet pet) {
		return ObjType.get(pet.itemId).name;
	}

	/** "Voidmind Hexer (T1)" -> "Voidmind Hexer". */
	private static String familyName(Pet pet) {
		return name(pet).replaceAll("\\s*\\(T\\d\\)$", "");
	}

	private static String styleName(CombatPetProfile profile) {
		return switch (profile.style) {
			case STAB, SLASH, CRUSH -> "Melee";
			case RANGED -> "Ranged";
			case DRAGONFIRE -> "Dragonfire";
			default -> "Magic";
		};
	}

}

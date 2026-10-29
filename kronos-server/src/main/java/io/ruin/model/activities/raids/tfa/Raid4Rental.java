package io.ruin.model.activities.raids.tfa;

import io.ruin.cache.ObjType;
import io.ruin.model.content.pvppreset.PvpPresetManager;
import io.ruin.model.entity.player.Player;
import io.ruin.model.entity.shared.listeners.LoginListener;
import io.ruin.model.inter.InterfaceHandler;
import io.ruin.model.inter.ToplevelComponent;
import io.ruin.model.inter.actions.SimpleAction;
import io.ruin.model.inter.dialogue.OptionsDialogue;
import io.ruin.model.inter.utils.Option;
import io.ruin.model.item.Item;
import io.ruin.model.item.ItemContainer;
import io.ruin.model.item.attributes.AttributeExtensions;
import io.ruin.model.item.containers.Equipment;
import io.ruin.model.map.MapListener;
import io.ruin.model.stat.StatType;
import io.ruin.network.PacketSender;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Raid 4 gear rental (roadmap phase 7, rev 2 2026-10-28): the Quartermaster in the lobby opens the Archive Armoury
 * (interface 1181, .dev/cache-restore-tool BuildArmouryInterface), which rents one of three full tribrid kits for
 * {@value #POINT_PRICE} PvM points (no coin option). Each kit wears one style's best-in-slot (the wiki strategy page's
 * top tier) and carries complete switches for the other two styles, a dragon warhammer for specs and the raid's
 * supplies, so a renter can play every room without owning the gear.
 * <ul>
 * <li>Renting saves the real inventory, equipment and rune pouch into persisted fields and hands out the kit as
 * phantom items (PvpPresetManager.PHANTOM_KEY): they can't be dropped, traded, sold, banked, alched, forged or
 * broken down (TabInventory refuses Uncharge / Dismantle on phantom items).</li>
 * <li>The rental lives only in the lobby and the raid: leaving the lobby any other way, dying in the raid, the raid
 * ending (clear, wipe or exit) and logging out all wipe it and put the real loadout back.</li>
 * <li>Anything real picked up while renting (e.g. supplies) goes to the bank on return, never lost.</li>
 * <li>A rental still saved at login (the server stopped mid-raid) is returned then.</li>
 * </ul>
 */
@Slf4j
public final class Raid4Rental {

	private Raid4Rental() {
	}

	/** The lobby's rental Quartermaster: the raid's own supplies NPC model, east of the raid entry. */
	public static final int NPC = Raid4Quartermaster.NPC_QUARTERMASTER;
	public static final int LOBBY_X = 4096 + 35, LOBBY_Y = 5504 + 52;
	public static final int POINT_PRICE = 750;

	/* Interface 1181 (BuildArmouryInterface's CONST lines) */
	public static final int INTERFACE = 1181;
	private static final int CLOSE = 2, TITLE = 3, SUBTITLE = 7, FIRST_KIT = 8, KIT_STRIDE = 16, KIT_RING = 8, KIT_NAME = 12,
			KIT_STYLE = 13, KIT_ROLE = 15, KIT_ACTIVE = 2, PREVIEW_TITLE = 62, FIRST_WORN = 63, FIRST_PACK = 96, SLOT_ITEM = 2,
			SLOT_STRIDE = 3, BALANCE = 181, PRICE = 182, STATUS = 183, RENT = 184, RENT_PRESSED = 9;
	/** The worn preview's order: the equipment screen read row by row. */
	private static final int[] WORN_ORDER = { Equipment.SLOT_HAT, Equipment.SLOT_CAPE, Equipment.SLOT_AMULET,
			Equipment.SLOT_WEAPON, Equipment.SLOT_CHEST, Equipment.SLOT_SHIELD, Equipment.SLOT_LEGS, Equipment.SLOT_HANDS,
			Equipment.SLOT_FEET, Equipment.SLOT_RING, Equipment.SLOT_AMMO };

	private static final int SCYTHE = 22325, SHADOW = 27275, DWH = 13576, DRAGON_ARROW = 11212, TREADS = 31097; // Avernic treads (max)
	private static final int ANGLERFISH = 13441, SARA_BREW = 6685, SUPER_RESTORE = 3024, DIVINE_SUPER_COMBAT = 23685,
			DIVINE_RANGING = 23733, DIVINE_MAGIC = 23745;

	/* The three styles' best-in-slot pieces: {slot, item}. Seven each, so a switch is exactly seven pack slots. */
	private static final int[][] MELEE = {
			{ Equipment.SLOT_HAT, 26382 }, // Torva full helm
			{ Equipment.SLOT_CAPE, 21295 }, // Infernal cape
			{ Equipment.SLOT_AMULET, 29801 }, // Amulet of rancour
			{ Equipment.SLOT_WEAPON, SCYTHE }, // Scythe of vitur (charged)
			{ Equipment.SLOT_CHEST, 26384 }, // Torva platebody
			{ Equipment.SLOT_LEGS, 26386 }, // Torva platelegs
			{ Equipment.SLOT_HANDS, 22981 }, // Ferocious gloves
	};
	private static final int[][] RANGED = {
			{ Equipment.SLOT_HAT, 27235 }, // Masori mask (f)
			{ Equipment.SLOT_CAPE, 22109 }, // Ava's assembler
			{ Equipment.SLOT_AMULET, 19547 }, // Necklace of anguish
			{ Equipment.SLOT_WEAPON, 20997 }, // Twisted bow
			{ Equipment.SLOT_CHEST, 27238 }, // Masori body (f)
			{ Equipment.SLOT_LEGS, 27241 }, // Masori chaps (f)
			{ Equipment.SLOT_HANDS, 26235 }, // Zaryte vambraces
	};
	private static final int[][] MAGIC = {
			{ Equipment.SLOT_HAT, 21018 }, // Ancestral hat
			{ Equipment.SLOT_CAPE, 21791 }, // Imbued saradomin cape
			{ Equipment.SLOT_AMULET, 12002 }, // Occult necklace
			{ Equipment.SLOT_WEAPON, SHADOW }, // Tumeken's shadow (charged)
			{ Equipment.SLOT_CHEST, 21021 }, // Ancestral robe top
			{ Equipment.SLOT_LEGS, 21024 }, // Ancestral robe bottom
			{ Equipment.SLOT_HANDS, 19544 }, // Tormented bracelet
	};

	public enum Setup {
		VANGUARD("Vanguard", "Melee", "Torva and scythe worn;<br>ranged + magic switches.", MELEE,
				new int[][] { { Equipment.SLOT_FEET, TREADS }, { Equipment.SLOT_RING, 30516 } }, RANGED, MAGIC, DIVINE_SUPER_COMBAT),
		MARKSMAN("Marksman", "Ranged", "Masori and twisted bow;<br>melee + magic switches.", RANGED,
				new int[][] { { Equipment.SLOT_FEET, TREADS }, { Equipment.SLOT_RING, 30517 } }, MELEE, MAGIC, DIVINE_RANGING),
		ARCANIST("Arcanist", "Magic", "Ancestral and shadow;<br>melee + ranged switches.", MAGIC,
				new int[][] { { Equipment.SLOT_FEET, TREADS }, { Equipment.SLOT_RING, 30518 } }, MELEE, RANGED, DIVINE_MAGIC);

		public final String label, style, role;
		final int[][] worn, shared, switchA, switchB;
		final int potion;

		Setup(String label, String style, String role, int[][] worn, int[][] shared, int[][] switchA, int[][] switchB, int potion) {
			this.label = label;
			this.style = style;
			this.role = role;
			this.worn = worn;
			this.shared = shared;
			this.switchA = switchA;
			this.switchB = switchB;
			this.potion = potion;
		}

		/** Slot -> item: the style's pieces, its boots and ring, and dragon arrows for the twisted bow worn or switched to. */
		Item[] equipment() {
			Item[] eq = new Item[14];
			for (int[] e : worn)
				eq[e[0]] = new Item(e[1], 1);
			for (int[] e : shared)
				eq[e[0]] = new Item(e[1], 1);
			eq[Equipment.SLOT_AMMO] = new Item(DRAGON_ARROW, 1000);
			return eq;
		}

		/**
		 * 28 slots: the other two styles' switches (7 + 7), the warhammer, 4 Saradomin brews, 4 super restores, the
		 * kit's own divine potion and a second one for its main switch, and anglerfish to fill.
		 */
		Item[] inventory() {
			List<Item> items = new ArrayList<>();
			for (int[] e : switchA)
				items.add(new Item(e[1], 1));
			for (int[] e : switchB)
				items.add(new Item(e[1], 1));
			items.add(new Item(DWH, 1));
			for (int i = 0; i < 4; i++)
				items.add(new Item(SARA_BREW, 1));
			for (int i = 0; i < 4; i++)
				items.add(new Item(SUPER_RESTORE, 1));
			items.add(new Item(potion, 1));
			items.add(new Item(potion == DIVINE_SUPER_COMBAT ? DIVINE_RANGING : DIVINE_SUPER_COMBAT, 1));
			while (items.size() < 28)
				items.add(new Item(ANGLERFISH, 1));
			return items.toArray(new Item[0]);
		}

		List<Integer> allItems() {
			List<Integer> ids = new ArrayList<>();
			for (Item i : equipment())
				if (i != null)
					ids.add(i.getId());
			for (Item i : inventory())
				ids.add(i.getId());
			return ids;
		}
	}

	private static final int CHARGES = 20_000;

	/** The kit picked in the open Armoury, per player. */
	private static final Map<Integer, Setup> picked = new HashMap<>();

	public static void register() {
		LoginListener.register(Raid4Rental::recover);
		InterfaceHandler.register(INTERFACE, h -> {
			h.actions[CLOSE] = (SimpleAction) Player::closeInterfaces;
			for (int k = 0; k < Setup.values().length; k++) {
				Setup setup = Setup.values()[k];
				h.actions[FIRST_KIT + k * KIT_STRIDE] = (SimpleAction) p -> {
					picked.put(p.getUserId(), setup);
					draw(p, null);
				};
			}
			h.actions[RENT] = (SimpleAction) Raid4Rental::rentPicked;
			h.closedAction = (p, i) -> picked.remove(p.getUserId());
		});
	}

	public static boolean active(Player player) {
		return player.raid4RentalInventory != null;
	}

	/**
	 * True (with a message) while renting: the bank, deposit box and preset loaders would move rented items into
	 * real storage or real items into containers that the return wipes.
	 */
	public static boolean refuse(Player player) {
		if (!active(player))
			return false;
		player.sendMessage("You can't do that while you're renting a raid kit. Return it to the Quartermaster first.");
		return true;
	}

	/* The Quartermaster and the Armoury */

	public static void talk(Player player) {
		if (active(player)) {
			player.dialogue(new OptionsDialogue("You're renting the " + label(player) + " kit.",
					new Option("Return the kit now", () -> {
						player.closeDialogue();
						release(player, "You hand the rented kit back to the Quartermaster.");
					}),
					new Option("Keep it", player::closeDialogue)));
			return;
		}
		open(player);
	}

	/** The Archive Armoury, the first kit picked. */
	public static void open(Player player) {
		picked.put(player.getUserId(), Setup.VANGUARD);
		player.openInterface(ToplevelComponent.MAINMODAL, INTERFACE);
		draw(player, null);
	}

	private static void draw(Player player, String status) {
		PacketSender ps = player.getPacketSender();
		Setup sel = picked.getOrDefault(player.getUserId(), Setup.VANGUARD);
		ps.sendString(INTERFACE, TITLE, "Archive Armoury");
		ps.sendString(INTERFACE, SUBTITLE, "Rent a full raid kit for " + POINT_PRICE + " PvM points.<br>"
				+ "It crumbles to dust when the raid ends, on death or on leaving.");
		for (int k = 0; k < Setup.values().length; k++) {
			Setup setup = Setup.values()[k];
			int base = FIRST_KIT + k * KIT_STRIDE;
			boolean on = setup == sel;
			ps.sendString(INTERFACE, base + KIT_NAME, setup.label);
			ps.sendString(INTERFACE, base + KIT_STYLE, setup.style);
			ps.sendString(INTERFACE, base + KIT_ROLE, setup.role);
			ps.setHidden(INTERFACE, base + KIT_RING, !on);
			for (int part = KIT_ACTIVE; part <= KIT_ACTIVE + 3; part++)
				ps.setHidden(INTERFACE, base + part, !on);
		}
		Item[] eq = sel.equipment();
		for (int s = 0; s < WORN_ORDER.length; s++) {
			Item item = eq[WORN_ORDER[s]];
			ps.sendItem(INTERFACE, FIRST_WORN + s * SLOT_STRIDE + SLOT_ITEM, item == null ? -1 : item.getId(), item == null ? 0 : item.getAmount());
		}
		Item[] inv = sel.inventory();
		for (int s = 0; s < 28; s++)
			ps.sendItem(INTERFACE, FIRST_PACK + s * SLOT_STRIDE + SLOT_ITEM, inv[s].getId(), inv[s].getAmount());
		ps.sendString(INTERFACE, PREVIEW_TITLE, sel.label + " kit - " + sel.style + " worn");
		boolean afford = player.PvmPoints >= POINT_PRICE;
		ps.sendString(INTERFACE, BALANCE, "PvM points: " + (afford ? "<col=3ddc5a>" : "<col=ff3333>")
				+ String.format("%,d", player.PvmPoints) + "</col>");
		ps.sendString(INTERFACE, PRICE, "Price: " + POINT_PRICE + " PvM points");
		String missing = missingRequirement(player, sel);
		if (status == null)
			status = missing != null ? "<col=ff3333>Requires " + missing + ".</col>"
					: !afford ? "<col=ff3333>Not enough PvM points.</col>" : "Ready to rent.";
		ps.sendString(INTERFACE, STATUS, status);
		// the pressed-in stone look when the kit can't be rented
		ps.setHidden(INTERFACE, RENT + RENT_PRESSED, missing == null && afford);
	}

	private static void rentPicked(Player player) {
		Setup setup = picked.get(player.getUserId());
		if (setup == null || !player.isVisibleInterface(INTERFACE))
			return;
		String refused = refusal(player, setup);
		if (refused != null) {
			draw(player, "<col=ff3333>" + refused + "</col>");
			return;
		}
		player.closeInterfaces();
		player.PvmPoints -= POINT_PRICE;
		take(player, setup);
		log.info("Raid 4 rental: {} rented {} for {} PvM points", player.getName(), setup, POINT_PRICE);
		player.sendMessage("<col=ef1020>You rent the " + setup.label + " kit. It is returned when you leave the lobby, "
				+ "die in the raid, the raid ends or you log out.</col>");
	}

	/** Why this player can't rent now, or null. */
	private static String refusal(Player player, Setup setup) {
		if (active(player))
			return "You are already renting a kit.";
		if (!inLobby(player))
			return "Kits are rented in the lobby only.";
		Raid4Party party = Raid4.partyOf(player);
		if (party != null && party.hasStarted())
			return "Your party is already in the Archive.";
		if (io.ruin.model.content.rivals.RivalsSnapshot.active(player) || player.pvpPresetActive)
			return "Return your other temporary gear first.";
		String missing = missingRequirement(player, setup);
		if (missing != null)
			return "Requires " + missing + ".";
		if (player.PvmPoints < POINT_PRICE)
			return "You need " + POINT_PRICE + " PvM points.";
		return null;
	}

	/** The first unmet wear requirement of any item in the kit, e.g. "75 Ranged", or null. */
	private static String missingRequirement(Player player, Setup setup) {
		for (int id : setup.allItems()) {
			int[] reqs = ObjType.get(id).equipReqs;
			if (reqs == null)
				continue;
			for (int req : reqs) {
				int stat = req >> 8, level = req & 0xff;
				if (player.getStats().get(stat).fixedLevel < level)
					return level + " " + StatType.get(stat).name();
			}
		}
		return null;
	}

	/* Taking and returning */

	private static void take(Player player, Setup setup) {
		player.raid4RentalInventory = copy(player.getInventory());
		player.raid4RentalEquipment = copy(player.getEquipment());
		player.raid4RentalRunePouch = copy(player.getRunePouch());
		player.raid4RentalSetup = setup.name();
		clear(player.getInventory());
		clear(player.getEquipment());
		clear(player.getRunePouch());
		Item[] eq = setup.equipment();
		for (int slot = 0; slot < eq.length; slot++)
			if (eq[slot] != null)
				player.getEquipment().set(slot, phantom(eq[slot].getId(), eq[slot].getAmount()));
		Item[] inv = setup.inventory();
		for (int i = 0; i < inv.length && i < 28; i++)
			player.getInventory().set(i, phantom(inv[i].getId(), inv[i].getAmount()));
		sendAll(player);
		player.getCombat().updateWeapon(false);
		player.getAppearance().update();
		player.registerMapListener(new MapListener(Raid4Rental::allowedHere).onExit((p, logout) ->
				release(p, logout ? null : "The Quartermaster's kit is bound to the Archive; it's been returned.")));
	}

	/** Lobby, or inside the player's own raid (incl. the tick they are being moved in). */
	private static boolean allowedHere(Player player) {
		if (!active(player))
			return false;
		if (inLobby(player))
			return true;
		Raid4Controller raid = Raid4.controllerOf(player);
		return raid != null && raid.getState() == Raid4Controller.State.ACTIVE
				&& (raid.isInside(player) || raid.getInstance().roomAt(player.getPosition()) != null);
	}

	private static boolean inLobby(Player player) {
		return player.getPosition().getRegion().id == Raid4Ids.LOBBY_REGION;
	}

	/**
	 * Wipes the rental and puts the real loadout back; safe to call when nothing is rented. Real items picked up while
	 * renting go to the bank.
	 */
	public static void release(Player player, String message) {
		if (!active(player))
			return;
		List<Item> extras = new ArrayList<>();
		collectReal(player.getInventory(), extras);
		collectReal(player.getEquipment(), extras);
		collectReal(player.getRunePouch(), extras);
		clear(player.getInventory());
		clear(player.getEquipment());
		clear(player.getRunePouch());
		put(player.getInventory(), player.raid4RentalInventory);
		put(player.getEquipment(), player.raid4RentalEquipment);
		put(player.getRunePouch(), player.raid4RentalRunePouch);
		player.raid4RentalInventory = null;
		player.raid4RentalEquipment = null;
		player.raid4RentalRunePouch = null;
		player.raid4RentalSetup = null;
		for (Item item : extras)
			player.getBank().add(item.getId(), item.getAmount(), item.copyOfAttributes());
		sendAll(player);
		player.getCombat().updateWeapon(false);
		player.getAppearance().update();
		if (!extras.isEmpty())
			player.sendMessage("Items you picked up while renting were sent to your bank.");
		if (message != null)
			player.sendMessage(message);
	}

	/** Login: a rental left behind by a server stop is returned. */
	private static void recover(Player player) {
		if (!active(player))
			return;
		release(player, "Your rented raid kit has been returned to the Quartermaster.");
		if (inLobby(player) || player.getPosition().getRegion().dynamicData != null)
			player.getMovement().teleport(Raid4Ids.LOBBY_RETURN_X, Raid4Ids.LOBBY_RETURN_Y, 0);
	}

	private static String label(Player player) {
		try {
			return Setup.valueOf(player.raid4RentalSetup).label;
		} catch (Exception e) {
			return "raid";
		}
	}

	private static Item phantom(int id, int amount) {
		Item item = new Item(id, amount);
		if (id == SCYTHE || id == SHADOW)
			AttributeExtensions.setCharges(item, CHARGES);
		item.putAttribute(PvpPresetManager.PHANTOM_KEY, "1");
		return item;
	}

	private static void collectReal(ItemContainer container, List<Item> out) {
		for (Item item : container.getItems())
			if (item != null && !PvpPresetManager.isPhantomItem(item))
				out.add(item.copy());
	}

	private static Item[] copy(ItemContainer container) {
		Item[] items = container.getItems();
		Item[] out = new Item[items.length];
		for (int i = 0; i < items.length; i++)
			out[i] = items[i] == null ? null : items[i].copy();
		return out;
	}

	private static void clear(ItemContainer container) {
		for (int i = 0; i < container.getItems().length; i++)
			container.set(i, null);
	}

	private static void put(ItemContainer container, Item[] saved) {
		if (saved == null)
			return;
		for (int i = 0; i < saved.length && i < container.getItems().length; i++)
			container.set(i, saved[i]);
	}

	private static void sendAll(Player player) {
		player.getInventory().sendUpdates();
		player.getEquipment().sendUpdates();
		player.getRunePouch().sendUpdates();
	}
}

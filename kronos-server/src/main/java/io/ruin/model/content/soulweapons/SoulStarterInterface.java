package io.ruin.model.content.soulweapons;

import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.InterfaceHandler;
import io.ruin.model.inter.ToplevelComponent;
import io.ruin.model.inter.actions.SimpleAction;
import io.ruin.network.PacketSender;

import java.util.HashMap;
import java.util.Map;

/**
 * The Soulbound weapon selector, interface 1175 (.dev/cache-restore-tool BuildSoulStarterInterface), 2026-10-18. Opened
 * by ::soulweapon and the Zelus Guide for players who haven't chosen yet: three cards (blade / recurve / spire) whose
 * Select opens a confirmation modal; only Bind there binds the weapon. Anti-dupe: the bind re-checks that the player
 * still has no Soulbound weapon, that the window is still open and that a card was picked in it, so a replayed or
 * late Bind click can never hand out a second weapon. The previews are spun by the client's zelussoulweapon plugin.
 */
public class SoulStarterInterface {
	public static final int INTERFACE = 1175;
	private static final int CLOSE = 2, TITLE = 3, SUBTITLE = 4, FIRST_CARD = 5, CARD_STRIDE = 49, FIRST_WHY = 155,
			WHY_STRIDE = 6, WHY_TEXT = 5, MODAL = 173, MODAL_BODY = 185, BIND = 186, BACK = 188;
	/** Card parts (offsets from its first component). Talent row t: name CARD_TALENT + t * 7 + 5, description + 6. */
	private static final int CARD_GLOW = 5, CARD_NAME = 9, CARD_ARCHETYPE = 10, CARD_MODEL = 19, CARD_ROLE = 20,
			CARD_TALENT = 22, TALENT_STRIDE = 7, CARD_SELECT = 43, CARD_PRESSED = 45;
	private static final String CYAN = "<col=2fe8f0>", MUTED = "<col=a89c86>", ORANGE = "<col=ff981f>", GREEN = "<col=3ddc5a>",
			RED = "<col=e0402a>", END = "</col>";
	private static final SoulWeaponType[] ORDER = {SoulWeaponType.MELEE, SoulWeaponType.RANGED, SoulWeaponType.MAGIC};

	/** The card picked in the open window (waiting in the confirmation modal), per player. */
	private static final Map<Integer, SoulWeaponType> picked = new HashMap<>();

	static void register() {
		InterfaceHandler.register(INTERFACE, h -> {
			h.actions[CLOSE] = (SimpleAction) Player::closeInterfaces;
			for (int c = 0; c < ORDER.length; c++) {
				SoulWeaponType type = ORDER[c];
				h.actions[FIRST_CARD + c * CARD_STRIDE + CARD_SELECT] = (SimpleAction) p -> pick(p, type);
			}
			h.actions[BIND] = (SimpleAction) SoulStarterInterface::bind;
			h.actions[BACK] = (SimpleAction) p -> {
				picked.remove(p.getUserId());
				draw(p);
			};
			h.closedAction = (p, i) -> picked.remove(p.getUserId());
		});
	}

	public static void open(Player player) {
		picked.remove(player.getUserId());
		player.openInterface(ToplevelComponent.MAINMODAL, INTERFACE);
		draw(player);
	}

	private static void pick(Player player, SoulWeaponType type) {
		if (picked.containsKey(player.getUserId()))
			return; // the modal is up: the cards underneath don't react
		if (player.soulWeapon != null) {
			player.closeInterfaces();
			SoulWeapons.claim(player); // already bound: recover instead
			return;
		}
		picked.put(player.getUserId(), type);
		draw(player);
	}

	private static void bind(Player player) {
		SoulWeaponType type = picked.remove(player.getUserId());
		if (type == null || !player.isVisibleInterface(INTERFACE))
			return;
		player.closeInterfaces();
		if (player.soulWeapon != null)
			return; // already bound (another window, a replayed click): never a second weapon
		player.soulWeapon = new SoulWeaponData(type);
		SoulWeapons.bindGive(player, type);
	}

	static void draw(Player player) {
		PacketSender ps = player.getPacketSender();
		SoulWeaponType sel = picked.get(player.getUserId());
		ps.sendString(INTERFACE, TITLE, "Choose Your Soulbound Weapon");
		ps.sendString(INTERFACE, SUBTITLE, "A living weapon that grows with you from Tier 1 to Tier 10.<br>"
				+ "Choose wisely - this choice is permanent!");
		for (int c = 0; c < ORDER.length; c++) {
			SoulWeaponType type = ORDER[c];
			int card = FIRST_CARD + c * CARD_STRIDE;
			int itemId = type.itemFor(1);
			ps.setHidden(INTERFACE, card + CARD_GLOW, type != sel);
			ps.setHidden(INTERFACE, card + CARD_PRESSED, type != sel);
			ps.sendString(INTERFACE, card + CARD_NAME, type.name);
			SoulWeaponCamera.show(ps, INTERFACE, card + CARD_MODEL, SoulWeaponCamera.Box.SELECT, itemId);
			String[] text = switch (type) {
				case MELEE -> new String[]{"Melee archetype", "Fast aggression & crits", "Stab / Slash",
						"Devastation", "130% crits", "Cleave", "splash hits", "Lifesteal", "heal on hit", "Blade"};
				case RANGED -> new String[]{"Ranged archetype", "High DPS & multi-target", "Rapid / Longrange",
						"Tempest", "fast shots", "Ricochet", "chain hits", "Executioner", "low-HP dmg", "Recurve"};
				case MAGIC -> new String[]{"Magic archetype", "Area damage & soul leech", "Soul spell",
						"Soul Siphon", "HP+Prayer", "Arcane Surge", "kill blasts", "Death Ward", "cheat death", "Spire"};
			};
			ps.sendString(INTERFACE, card + CARD_ARCHETYPE, text[0]);
			ps.sendString(INTERFACE, card + CARD_ROLE, text[1] + "<br>" + MUTED + "Style: " + END + text[2]);
			for (int t = 0; t < 3; t++) {
				int row = card + CARD_TALENT + t * TALENT_STRIDE;
				ps.sendString(INTERFACE, row + 5, text[3 + t * 2]);
				ps.sendString(INTERFACE, row + 6, text[4 + t * 2]);
			}
			// plain label text: the label's own colour turns white on hover (only "Selected" is tagged)
			ps.sendString(INTERFACE, card + CARD_SELECT + 1, type == sel ? CYAN + "Selected" + END : "Select " + text[9]);
		}
		// two short explicit lines per column (each under the 146px column width): the strip holds exactly two
		String[] why = {
				"Slay monsters for Soul XP<br>" + ORANGE + "Bosses grant huge bonuses" + END,
				"Evolves at Tier 4 & 8<br>" + ORANGE + "New looks & specials" + END,
				"Account-bound progression<br>" + ORANGE + "Untradeable PvM gear" + END};
		for (int i = 0; i < why.length; i++)
			ps.sendString(INTERFACE, FIRST_WHY + i * WHY_STRIDE + WHY_TEXT, why[i]);
		ps.setHidden(INTERFACE, MODAL, sel == null);
		if (sel != null) {
			ps.sendString(INTERFACE, MODAL_BODY, "Are you sure you wish to bind your soul to the " + CYAN + sel.name + END
					+ "?<br><br>Your other choices will be locked. This weapon will be placed in your inventory"
					+ " (or your bank if it is full).");
			ps.sendString(INTERFACE, BIND + 1, GREEN + "Bind Weapon" + END);
			ps.sendString(INTERFACE, BACK + 1, RED + "Back" + END);
		}
	}
}

package io.ruin.model.content.soulweapons;

import io.ruin.api.utils.NumberUtils;
import io.ruin.cache.ObjType;
import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.InterfaceHandler;
import io.ruin.model.inter.ToplevelComponent;
import io.ruin.model.inter.actions.SimpleAction;
import io.ruin.model.inter.dialogue.OptionsDialogue;
import io.ruin.model.inter.utils.Option;
import io.ruin.network.PacketSender;

/**
 * The Soul Tree, interface 1176 (.dev/cache-restore-tool BuildSoulTreeInterface + BuildSoulTreeSprites), 2026-10-18,
 * round 3. Left: the wielded look floating in a soul glow, soul veins branching down to the line's three
 * {@link SoulTalent} nodes; a node shows only its socket (bronze unlearned / soul-lit / gold mastered) with the icon,
 * a 5-arc rank meter and level badge, the title and the "+ Rank Up" rune. Right: the inspector for the selected node
 * (click a socket): icon, name, mechanics, current vs next rank and its own Rank Up with the cost. Below: the
 * {@link SoulAura} cards (click to wear, again to take off) and Reset (free once, then {@link #RESET_COST} coins from the
 * inventory). The client's zelussoulweapon plugin animates the weapon and the halos and draws the hover tooltips
 * (NODE_TIP / AURA_TIP, hidden texts) on its own opaque panel above the cursor.
 */
public class SoulTreeInterface {
	public static final int INTERFACE = 1176;
	private static final int CLOSE = 2, TITLE = 3, SUBTITLE = 4, POINTS = 5, MODEL = 15, VEIN_TRUNK = 26, VEIN_LEFT = 27,
			VEIN_RIGHT = 28, FIRST_DROP = 29, FIRST_NODE = 32, NODE_STRIDE = 21, FIRST_AURA = 120, AURA_STRIDE = 11,
			RESET = 153, RESET_PRICE = 155, PREVIEW_BUTTON = 156, PREVIEW_INFO = 158, MODAL = 159, MODAL_BODY = 171,
			CONFIRM = 172, CANCEL = 174;
	/** Node parts: arcs +1..+5, halo, selected ring (drawn over the halo), sockets (bronze / lit / gold), icon, dim disc, badge, rune (dim +1, label +2). */
	private static final int NODE_ARC = 1, NODE_HALO = 6, NODE_SELECT = 7, NODE_SOCKET = 8, NODE_ICON = 11, NODE_DIM = 12,
			NODE_BADGE = 15, NODE_NAME = 16, NODE_RUNE = 17, NODE_TIP = 20;
	/** Inspector parts. */
	private static final int INSP_SOCKET = 101, INSP_ICON = 102, INSP_NAME = 103, INSP_TAGLINE = 104, INSP_RANK = 105,
			INSP_NOTE = 107, INSP_NOW = 109, INSP_NEXT = 110, INSP_RUNE = 111, INSP_COST = 114;
	/** Aura card parts: orb, silhouette, lock, name, state pills (activate / active / locked), label, tooltip. */
	private static final int AURA_ORB = 2, AURA_SILHOUETTE = 3, AURA_LOCK = 4, AURA_NAME = 5, AURA_PILL = 6,
			AURA_LABEL = 9, AURA_TIP = 10;
	private static final int ICON_SPRITE = 24582, SOCKET_SPRITE = 24591, SET_COLOUR = 45, VEIN_LIT = 0x2fd2d8, VEIN_DIM = 0x2a2620;
	private static final int RESET_COST = 5_000_000, COINS = 995;
	private static final String GOLD = "<col=ffc35a>", TEAL = "<col=2fd2d8>", CYAN = "<col=00ffff>", ORANGE = "<col=ff981f>",
			GREEN = "<col=3ddc5a>", MUTED = "<col=8f8573>", WHITE = "<col=ffffff>", END = "</col>";

	/** The node shown in the inspector, per player (0..2); the first node until one is clicked. */
	private static final java.util.Map<Integer, Integer> selected = new java.util.HashMap<>();
	/** The look shown in the tree's viewport while previewing an evolution (band 0..2), per player; none = the active look. */
	private static final java.util.Map<Integer, Integer> previewing = new java.util.HashMap<>();
	private static final String[] FORMS = {"Base", "Empowered", "Ascended"};
	private static final int[] FORM_TIER = {1, 4, 8};

	/** The node waiting in the Rank Up confirmation, per player: a point is only spent by its Confirm. */
	private static final java.util.Map<Integer, Integer> confirming = new java.util.HashMap<>();
	private static final int SOUND_RANK_UP = 101, GFX_SOUL_BURST = 6827;

	static void register() {
		InterfaceHandler.register(INTERFACE, h -> {
			h.actions[CLOSE] = (SimpleAction) Player::closeInterfaces;
			for (int n = 0; n < 3; n++) {
				int node = n, base = FIRST_NODE + n * NODE_STRIDE;
				h.actions[base + NODE_RUNE] = (SimpleAction) p -> {
					selected.put(p.getUserId(), node);
					askInvest(p, node);
				};
				for (int s = 0; s < 3; s++)
					h.actions[base + NODE_SOCKET + s] = (SimpleAction) p -> select(p, node);
			}
			h.actions[INSP_RUNE] = (SimpleAction) p -> askInvest(p, selected.getOrDefault(p.getUserId(), 0));
			h.actions[CONFIRM] = (SimpleAction) p -> {
				Integer node = confirming.remove(p.getUserId());
				if (node != null)
					invest(p, node);
				else
					draw(p);
			};
			h.actions[CANCEL] = (SimpleAction) p -> {
				confirming.remove(p.getUserId());
				draw(p);
			};
			for (int a = 0; a < SoulAura.values().length; a++) {
				SoulAura aura = SoulAura.values()[a];
				h.actions[FIRST_AURA + a * AURA_STRIDE] = (SimpleAction) p -> toggleAura(p, aura);
			}
			h.actions[RESET] = (SimpleAction) SoulTreeInterface::confirmReset;
			h.actions[PREVIEW_BUTTON] = (SimpleAction) SoulTreeInterface::cyclePreview;
			h.closedAction = (p, i) -> {
				selected.remove(p.getUserId());
				confirming.remove(p.getUserId());
				previewing.remove(p.getUserId());
			};
		});
	}

	public static void open(Player player) {
		if (player.soulWeapon == null) {
			player.sendMessage(SoulWeapons.TAG + "You haven't chosen a Soulbound weapon yet. Type ::soulweapon to choose one.");
			return;
		}
		confirming.remove(player.getUserId());
		previewing.remove(player.getUserId());
		player.openInterface(ToplevelComponent.MAINMODAL, INTERFACE);
		draw(player);
	}

	/** Preview: the next form(s) the weapon will take, then back to the active one. */
	private static void cyclePreview(Player player) {
		SoulWeaponData d = player.soulWeapon;
		if (d == null || !isOpen(player))
			return;
		int band = SoulWeaponType.band(d.tier);
		int shown = previewing.getOrDefault(player.getUserId(), band) + 1;
		if (shown > 2 || band == 2)
			previewing.remove(player.getUserId());
		else
			previewing.put(player.getUserId(), shown);
		draw(player);
	}

	private static void select(Player player, int node) {
		if (player.soulWeapon == null || !isOpen(player))
			return;
		selected.put(player.getUserId(), node);
		draw(player);
	}

	static boolean isOpen(Player player) {
		return player.isVisibleInterface(INTERFACE);
	}

	/** Rank Up: nothing is spent yet, the confirmation modal asks first (a point can't go by a stray click). */
	private static void askInvest(Player player, int node) {
		SoulWeaponData d = player.soulWeapon;
		if (d == null || !isOpen(player) || confirming.containsKey(player.getUserId()))
			return;
		SoulTalent talent = SoulTalent.of(d.weaponType)[node];
		int rank = d.allocatedTalents.getOrDefault(talent.name(), 0);
		if (rank >= SoulTalent.MAX_RANK || d.availableSoulPoints <= 0) {
			invest(player, node); // explains why not
			return;
		}
		confirming.put(player.getUserId(), node);
		draw(player);
	}

	private static void invest(Player player, int node) {
		SoulWeaponData d = player.soulWeapon;
		if (d == null || !isOpen(player))
			return;
		SoulTalent talent = SoulTalent.of(d.weaponType)[node];
		int rank = d.allocatedTalents.getOrDefault(talent.name(), 0);
		if (rank >= SoulTalent.MAX_RANK) {
			player.sendMessage(SoulWeapons.TAG + talent.name + " is already mastered.");
			return;
		}
		if (d.availableSoulPoints <= 0) {
			player.sendMessage(SoulWeapons.TAG + "You have no Soul Points to spend. Each new tier grants one.");
			return;
		}
		d.availableSoulPoints--;
		d.allocatedTalents.put(talent.name(), rank + 1);
		io.ruin.model.content.codex.ZelusTask.PATH_OF_MASTERY.increment(player, 1);
		player.privateSound(SOUND_RANK_UP);
		player.graphics(GFX_SOUL_BURST, 0, 0);
		player.sendMessage(SoulWeapons.TAG + SoulWeapons.HIGHLIGHT + talent.name + " rank " + (rank + 1) + "</col>: "
				+ talent.describe(rank + 1) + ".");
		draw(player);
	}

	private static void toggleAura(Player player, SoulAura aura) {
		SoulWeaponData d = player.soulWeapon;
		if (d == null || !isOpen(player))
			return;
		if (!aura.unlocked(d)) {
			player.sendMessage(SoulWeapons.TAG + aura.name + " awakens when your weapon reaches tier " + aura.tier + ".");
			return;
		}
		boolean off = aura.name().equals(d.activeCosmeticAura);
		d.activeCosmeticAura = off ? null : aura.name();
		if (!off)
			player.graphics(aura.gfx, 0, 0); // a taste of it right away
		draw(player);
	}

	private static void confirmReset(Player player) {
		SoulWeaponData d = player.soulWeapon;
		if (d == null || !isOpen(player))
			return;
		int spent = spent(d);
		if (spent == 0) {
			player.sendMessage(SoulWeapons.TAG + "You haven't spent any Soul Points yet.");
			return;
		}
		boolean free = d.talentResets == 0;
		player.dialogue(new OptionsDialogue("Reset your Soul Tree? " + (free ? "(free, once)" : "(" + NumberUtils.formatNumber(RESET_COST) + " coins)"),
				new Option("Yes, refund my " + spent + " Soul Point" + (spent == 1 ? "" : "s") + ".", () -> reset(player)),
				new Option("No.", player::closeDialogue)));
	}

	private static void reset(Player player) {
		player.closeDialogue();
		SoulWeaponData d = player.soulWeapon;
		if (d == null || spent(d) == 0)
			return; // re-checked: a second click on an old dialogue does nothing
		if (d.talentResets > 0) {
			if (player.getInventory().getAmount(COINS) < RESET_COST) {
				player.sendMessage(SoulWeapons.TAG + "You need " + NumberUtils.formatNumber(RESET_COST)
						+ " coins in your inventory to reset your Soul Tree.");
				return;
			}
			player.getInventory().remove(COINS, RESET_COST);
		}
		int refund = spent(d);
		d.allocatedTalents.clear();
		d.availableSoulPoints += refund;
		d.talentResets++;
		player.sendMessage(SoulWeapons.TAG + "Your Soul Tree has been reset: " + pointsText(refund) + " refunded.");
		if (isOpen(player))
			draw(player);
	}

	private static String pointsText(int points) {
		return SoulWeapons.HIGHLIGHT + points + " Soul Point" + (points == 1 ? "" : "s") + "</col>";
	}

	static int spent(SoulWeaponData d) {
		int total = 0;
		for (int r : d.allocatedTalents.values())
			total += r;
		return total;
	}

	static void draw(Player player) {
		SoulWeaponData d = player.soulWeapon;
		if (d == null)
			return;
		PacketSender ps = player.getPacketSender();
		int lookId = d.weaponType.itemFor(d.tier);
		ObjType look = ObjType.get(lookId);
		int band = SoulWeaponType.band(d.tier);
		Integer preview = previewing.get(player.getUserId());
		ps.sendString(INTERFACE, TITLE, ORANGE + "Soul Tree" + END + " - " + CYAN + look.name + " (Tier " + d.tier + ")" + END);
		ps.sendString(INTERFACE, SUBTITLE, "Spend Soul Points to awaken your weapon's talents");
		ps.sendString(INTERFACE, POINTS, "Available Points: " + (d.availableSoulPoints > 0 ? WHITE : MUTED) + d.availableSoulPoints + END);
		SoulWeaponCamera.show(ps, INTERFACE, MODEL, SoulWeaponCamera.Box.TREE, preview == null ? lookId : d.weaponType.looks()[preview]);
		ps.sendString(INTERFACE, PREVIEW_BUTTON + 1, band == 2 ? MUTED + "Final form" + END
				: preview == null ? TEAL + "Preview" + END : GOLD + "Back" + END);
		ps.sendString(INTERFACE, PREVIEW_INFO, previewInfo(player, d, band, preview));

		SoulTalent[] talents = SoulTalent.of(d.weaponType);
		int[] ranks = new int[talents.length];
		for (int n = 0; n < talents.length; n++)
			ranks[n] = Math.min(SoulTalent.MAX_RANK, d.allocatedTalents.getOrDefault(talents[n].name(), 0));
		// soul veins: a branch lights once its talent holds a rank, the trunk once any does
		vein(ps, VEIN_TRUNK, ranks[0] + ranks[1] + ranks[2] > 0);
		vein(ps, VEIN_LEFT, ranks[0] > 0);
		vein(ps, VEIN_RIGHT, ranks[2] > 0);
		for (int n = 0; n < talents.length; n++)
			vein(ps, FIRST_DROP + n, ranks[n] > 0);

		int sel = selected.getOrDefault(player.getUserId(), 0);
		for (int n = 0; n < talents.length; n++) {
			SoulTalent t = talents[n];
			int node = FIRST_NODE + n * NODE_STRIDE, rank = ranks[n];
			boolean mastered = rank >= SoulTalent.MAX_RANK;
			for (int a = 0; a < SoulTalent.MAX_RANK; a++)
				ps.setHidden(INTERFACE, node + NODE_ARC + a, a >= rank);
			ps.setHidden(INTERFACE, node + NODE_SELECT, n != sel);
			ps.setHidden(INTERFACE, node + NODE_HALO, !mastered);
			int socket = socketState(rank);
			for (int s = 0; s < 3; s++)
				ps.setHidden(INTERFACE, node + NODE_SOCKET + s, s != socket);
			ps.setGraphic(INTERFACE, node + NODE_ICON, ICON_SPRITE + t.ordinal());
			ps.setHidden(INTERFACE, node + NODE_DIM, rank > 0);
			ps.sendString(INTERFACE, node + NODE_BADGE, (mastered ? GOLD : rank > 0 ? CYAN : MUTED) + rank + "/" + SoulTalent.MAX_RANK + END);
			ps.sendString(INTERFACE, node + NODE_NAME, n == sel ? WHITE + t.name + END : t.name);
			rune(ps, node + NODE_RUNE, rank, d.availableSoulPoints);
			ps.sendString(INTERFACE, node + NODE_TIP, tooltip(t, rank));
		}

		// inspector: the selected node in full
		SoulTalent it = talents[sel];
		int rank = ranks[sel];
		ps.setGraphic(INTERFACE, INSP_SOCKET, SOCKET_SPRITE + socketState(rank) * 2);
		ps.setGraphic(INTERFACE, INSP_ICON, ICON_SPRITE + it.ordinal());
		ps.sendString(INTERFACE, INSP_NAME, it.name);
		ps.sendString(INTERFACE, INSP_TAGLINE, it.tagline);
		ps.sendString(INTERFACE, INSP_RANK, rank >= SoulTalent.MAX_RANK ? GOLD + "Mastered (5/5)" + END
				: rank == 0 ? MUTED + "Unawakened (0/5)" + END : "Rank " + rank + " / " + SoulTalent.MAX_RANK);
		ps.sendString(INTERFACE, INSP_NOTE, it.note());
		ps.sendString(INTERFACE, INSP_NOW, rank == 0 ? CYAN + "Current:" + END + MUTED + " nothing yet" + END
				: CYAN + "Rank " + rank + ":" + END + " " + it.describe(rank));
		ps.sendString(INTERFACE, INSP_NEXT, rank >= SoulTalent.MAX_RANK ? GOLD + "Fully mastered" + END
				: ORANGE + "Next Rank:" + END + " " + it.describe(rank + 1));
		rune(ps, INSP_RUNE, rank, d.availableSoulPoints);
		ps.sendString(INTERFACE, INSP_COST, rank >= SoulTalent.MAX_RANK ? GOLD + "Maxed" + END
				: d.availableSoulPoints > 0 ? "Cost: " + WHITE + "1 Soul Point" + END : "<col=ff6666>Needs 1 Soul Point</col>");

		SoulAura active = SoulAura.active(d);
		for (int a = 0; a < SoulAura.values().length; a++) {
			SoulAura aura = SoulAura.values()[a];
			int card = FIRST_AURA + a * AURA_STRIDE;
			boolean unlocked = aura.unlocked(d);
			ps.setHidden(INTERFACE, card + AURA_ORB, !unlocked);
			ps.setHidden(INTERFACE, card + AURA_SILHOUETTE, unlocked);
			ps.setHidden(INTERFACE, card + AURA_LOCK, unlocked);
			// one word per line: "Ascendant / Radiance" fits the card
			String name = aura.name.replace(" ", "<br>");
			ps.sendString(INTERFACE, card + AURA_NAME, unlocked ? name : MUTED + name.replace("<br>", END + "<br>" + MUTED) + END);
			int pill = aura == active ? 1 : unlocked ? 0 : 2;
			for (int s = 0; s < 3; s++)
				ps.setHidden(INTERFACE, card + AURA_PILL + s, s != pill);
			ps.sendString(INTERFACE, card + AURA_LABEL, aura == active ? "Active" : unlocked ? "Activate" : MUTED + "Req: Tier " + aura.tier + END);
			ps.sendString(INTERFACE, card + AURA_TIP, GOLD + aura.name + END + "</br>"
					+ (aura == active ? GREEN + "Active" + END + " - click to take it off."
					: unlocked ? "Click to wear it." : "Awakens at weapon tier " + aura.tier + ".")
					+ "</br>" + MUTED + "Pulses around you out of combat, seen by everyone." + END);
		}
		Integer pending = confirming.get(player.getUserId());
		ps.setHidden(INTERFACE, MODAL, pending == null);
		if (pending != null) {
			SoulTalent pt = talents[pending];
			int from = ranks[pending];
			ps.sendString(INTERFACE, MODAL_BODY, "Spend " + WHITE + "1 Soul Point" + END + " on " + GOLD + pt.name + END
					+ "<br>(Rank " + from + " -> " + (from + 1) + ")?<br>" + CYAN + pt.brief(from + 1) + END);
			ps.sendString(INTERFACE, CONFIRM + 1, GREEN + "Confirm" + END);
			ps.sendString(INTERFACE, CANCEL + 1, "<col=e0402a>Cancel</col>");
		}
		ps.sendString(INTERFACE, RESET + 1, "Reset tree");
		ps.sendString(INTERFACE, RESET_PRICE, d.talentResets == 0 ? GREEN + "First reset free" + END
				: NumberUtils.formatNumber(RESET_COST) + " coins");
	}

	private static void vein(PacketSender ps, int comp, boolean lit) {
		ps.sendClientScript(SET_COLOUR, "Ii", INTERFACE << 16 | comp, lit ? VEIN_LIT : VEIN_DIM);
	}

	/** The viewport's caption: the form shown and what it brings, so the next evolution is something to chase. */
	private static String previewInfo(Player player, SoulWeaponData d, int band, Integer preview) {
		if (preview == null)
			return GOLD + FORMS[band] + " form" + END + "<br>" + (band == 2 ? MUTED + "Fully evolved" + END
					: MUTED + "Next: " + FORMS[band + 1] + "<br>at Tier " + FORM_TIER[band + 1] + END);
		int tier = FORM_TIER[preview];
		StringBuilder sb = new StringBuilder(TEAL + FORMS[preview] + END + MUTED + " (T" + tier + ")" + END);
		if (d.weaponType == SoulWeaponType.MAGIC)
			sb.append("<br>+").append(SoulWeapons.tierTenBonus(d.weaponType)[3] * (tier - 1) / 9).append(" magic");
		else {
			int[] ten = SoulWeapons.tierTenBonus(d.weaponType);
			int best = 0;
			for (int i = 0; i < ten.length; i++)
				if (ten[i] > ten[best])
					best = i;
			sb.append("<br>+").append(ten[best] * (tier - 1) / 9).append(d.weaponType == SoulWeaponType.MELEE ? " slash" : " ranged");
		}
		sb.append("<br>").append(preview == 2 ? GREEN + "Special attack" + END : GREEN + "New look" + END);
		return sb.toString();
	}

	/** 0 bronze (unlearned), 1 soul-lit, 2 gold (mastered): the socket sprite offset / 2. */
	private static int socketState(int rank) {
		return rank >= SoulTalent.MAX_RANK ? 2 : rank > 0 ? 1 : 0;
	}

	/** A "+ Rank Up" rune: lit and clickable with points to spend, otherwise the dim stone ("Maxed" / "No points"). */
	private static void rune(PacketSender ps, int comp, int rank, int points) {
		boolean canBuy = rank < SoulTalent.MAX_RANK && points > 0;
		ps.setHidden(INTERFACE, comp, !canBuy);
		ps.setHidden(INTERFACE, comp + 1, canBuy);
		ps.sendString(INTERFACE, comp + 2, canBuy ? "+ Rank Up" : rank >= SoulTalent.MAX_RANK ? GOLD + "Maxed" + END
				: MUTED + "No points" + END);
	}

	/** A node's hover tooltip, drawn by the client plugin ("</br>" = new line): the short version of the inspector. */
	private static String tooltip(SoulTalent t, int rank) {
		return GOLD + t.name + END + "  " + MUTED + "[" + rank + "/" + SoulTalent.MAX_RANK + "]" + END + "</br>"
				+ (rank == 0 ? MUTED + "Not yet awakened" + END : CYAN + "Rank " + rank + ":" + END + " " + t.brief(rank))
				+ (rank < SoulTalent.MAX_RANK ? "</br>" + ORANGE + "Next Rank:" + END + " " + t.brief(rank + 1) : "")
				+ "</br>" + MUTED + "Click to inspect" + END;
	}
}

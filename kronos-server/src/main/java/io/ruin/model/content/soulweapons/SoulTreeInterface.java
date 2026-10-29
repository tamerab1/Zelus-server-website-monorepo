package io.ruin.model.content.soulweapons;

import io.ruin.api.utils.NumberUtils;
import io.ruin.cache.ObjType;
import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.InterfaceHandler;
import io.ruin.model.inter.ToplevelComponent;
import io.ruin.model.inter.actions.SimpleAction;
import io.ruin.network.PacketSender;

import java.util.HashMap;
import java.util.Map;

/**
 * The Soul Tree, interface 1176 (.dev/cache-restore-tool BuildSoulTreeInterface + BuildSoulTreeSprites), round 7
 * (2026-10-28): a progression matrix.
 * <ul>
 * <li>Stage (left): the look turning on a dark dais (the client plugin spins it), its name, and the &lt; &gt; arrows that
 * preview every Soulbound line's tree (Soulblade / Soulbow / Soulspire; only the player's own can be ranked up); the
 * Forms bezel steps through the previewed line's evolutions.</li>
 * <li>Matrix: three rows of two {@link SoulTalent}s (the line's own talent left, a shared soul talent right). Row II opens
 * after {@link SoulTalent#ROW_UNLOCK} ranks in row I, row III after as many in row II; the soul links down each column
 * light as the path opens. Click a node to inspect it; hover shows its tooltip (the plugin draws NODE_TIP).</li>
 * <li>Inspector (right): the selected talent's current vs next rank and its Rank Up rune, or, with nothing selected, the
 * build overview: points, ranks spent per row and every active bonus.</li>
 * <li>Aura tray: the {@link SoulAura} cards and Reset (free once, then {@link #RESET_COST} PvM points), both confirmed in the
 * modal, which shows exactly what is spent or refunded.</li>
 * </ul>
 */
public class SoulTreeInterface {
	public static final int INTERFACE = 1176;
	// round 9 (2026-10-29): two pages behind the Weapon / Talents tabs, and the dock (BuildSoulTreeInterface prints these)
	private static final int CLOSE = 2, TITLE = 3, SUBTITLE = 4, POINTS = 5, TAB_WEAPON = 7, TAB_TALENTS = 11,
			PAGE_WEAPON = 15, STAGE_NAME = 22, MODEL = 25, ARROW_LEFT = 34, ARROW_RIGHT = 35, STAGE_LINE = 36, INFO_TITLE = 43,
			INFO_BODY = 45, PREVIEW_BUTTON = 46, PAGE_TALENTS = 48, FIRST_LINK = 55, FIRST_NODE = 59, NODE_STRIDE = 20,
			FIRST_AURA = 203, AURA_STRIDE = 11, RESET = 236, RESET_PRICE = 238, MODAL = 239, MODAL_TITLE = 249, MODAL_BODY = 251,
			CONFIRM = 252, CANCEL = 254;
	/** A tab: the button, +1 pressed recess, +2 gold trim (shown on the active tab), +3 label. */
	private static final int TAB_RECESS = 1, TAB_TRIM = 2, TAB_LABEL = 3;
	/** Node card parts: frame +0/+1, ring +2, arcs +3..+7, halo, selected ring, sockets (bronze / lit / gold), icon, dim disc, name, badge, tooltip. */
	private static final int NODE_ARC = 3, NODE_HALO = 8, NODE_SELECT = 9, NODE_SOCKET = 10, NODE_ICON = 13, NODE_DIM = 14,
			NODE_NAME = 15, NODE_BADGE = 18, NODE_TIP = 19;
	/** The inspector: socket, icon, name, tagline, rank line, note, now, next, the Rank Up bezel (+1 label). */
	private static final int INSP_SOCKET = 185, INSP_ICON = 186, INSP_NAME = 187, INSP_TAGLINE = 188, INSP_RANK = 189,
			INSP_NOTE = 191, INSP_NOW = 192, INSP_NEXT = 193, INSP_RUNE = 196;
	/** Aura card parts: orb, silhouette, lock, name, state pills (activate / active / locked), label, tooltip. */
	private static final int AURA_ORB = 2, AURA_SILHOUETTE = 3, AURA_LOCK = 4, AURA_NAME = 5, AURA_PILL = 6,
			AURA_LABEL = 9, AURA_TIP = 10;
	private static final int ICON_SPRITE = 24700, SOCKET_SPRITE = 24591, SET_COLOUR = 45, LINK_LIT = 0x2fd2d8,
			LINK_GOLD = 0xffc35a, LINK_DIM = 0x2a2620;
	private static final int RESET_COST = 500; // PvM points
	private static final String GOLD = "<col=ffc35a>", TEAL = "<col=2fd2d8>", CYAN = "<col=00ffff>", ORANGE = "<col=ff981f>",
			GREEN = "<col=3ddc5a>", MUTED = "<col=8f8573>", WHITE = "<col=ffffff>", RED = "<col=ff6666>", END = "</col>";
	private static final String[] ROMAN = {"I", "II", "III"};
	private static final String[] LINE_NAMES = {"Soulblade", "Soulbow", "Soulspire"};

	/** Per player: the page shown (the Weapon page unless they picked Talents). */
	private static final java.util.Set<Integer> talentsPage = new java.util.HashSet<>();
	/** Per player: the inspected node (0..5), -1 = the build overview. */
	private static final Map<Integer, Integer> selected = new HashMap<>();
	/** Per player: the line on the stage (the &lt; &gt; arrows); none = their own. */
	private static final Map<Integer, SoulWeaponType> viewing = new HashMap<>();
	/** Per player: the evolution form previewed on the stage (band 0..3); none = the active look. */
	private static final Map<Integer, Integer> previewing = new HashMap<>();
	private static final String[] FORMS = {"Base", "Empowered", "Ascended", "Radiant"};
	private static final int LAST_FORM = 3;

	/** The modal's pending action per player: a node index to rank up, or {@link #RESET_PENDING}. */
	private static final Map<Integer, Integer> confirming = new HashMap<>();
	private static final int RESET_PENDING = -2;
	private static final int SOUND_RANK_UP = 101, GFX_SOUL_BURST = 6827;

	static void register() {
		InterfaceHandler.register(INTERFACE, h -> {
			h.actions[CLOSE] = (SimpleAction) Player::closeInterfaces;
			for (int n = 0; n < SoulTalent.ROWS * SoulTalent.COLS; n++) {
				int node = n, base = FIRST_NODE + n * NODE_STRIDE;
				for (int s = 0; s < 3; s++)
					h.actions[base + NODE_SOCKET + s] = (SimpleAction) p -> select(p, node);
			}
			h.actions[INSP_RUNE] = (SimpleAction) p -> askInvest(p, selected.getOrDefault(p.getUserId(), -1));
			h.actions[CONFIRM] = (SimpleAction) p -> {
				Integer pending = confirming.remove(p.getUserId());
				if (pending == null)
					draw(p);
				else if (pending == RESET_PENDING)
					reset(p);
				else
					invest(p, pending);
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
			h.actions[ARROW_LEFT] = (SimpleAction) p -> switchLine(p, -1);
			h.actions[ARROW_RIGHT] = (SimpleAction) p -> switchLine(p, 1);
			h.actions[TAB_WEAPON] = (SimpleAction) p -> showPage(p, false);
			h.actions[TAB_TALENTS] = (SimpleAction) p -> showPage(p, true);
			h.closedAction = (p, i) -> {
				talentsPage.remove(p.getUserId());
				selected.remove(p.getUserId());
				confirming.remove(p.getUserId());
				previewing.remove(p.getUserId());
				viewing.remove(p.getUserId());
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
		viewing.remove(player.getUserId());
		player.openInterface(ToplevelComponent.MAINMODAL, INTERFACE);
		draw(player);
	}

	static boolean isOpen(Player player) {
		return player.isVisibleInterface(INTERFACE);
	}

	private static SoulWeaponType viewed(Player player) {
		return viewing.getOrDefault(player.getUserId(), player.soulWeapon.weaponType);
	}

	private static boolean viewingOwn(Player player) {
		return viewed(player) == player.soulWeapon.weaponType;
	}

	private static void showPage(Player player, boolean talents) {
		if (player.soulWeapon == null || !isOpen(player) || confirming.containsKey(player.getUserId()))
			return;
		if (talents)
			talentsPage.add(player.getUserId());
		else
			talentsPage.remove(player.getUserId());
		draw(player);
	}

	/** The &lt; &gt; arrows: the next / previous Soulbound line on the stage, its tree in the matrix. */
	private static void switchLine(Player player, int step) {
		if (player.soulWeapon == null || !isOpen(player) || confirming.containsKey(player.getUserId()))
			return;
		SoulWeaponType[] lines = SoulWeaponType.values();
		int next = Math.floorMod(viewed(player).ordinal() + step, lines.length);
		if (lines[next] == player.soulWeapon.weaponType)
			viewing.remove(player.getUserId());
		else
			viewing.put(player.getUserId(), lines[next]);
		previewing.remove(player.getUserId());
		selected.remove(player.getUserId());
		draw(player);
	}

	/**
	 * Forms: "Forms" opens the Base form, "Next form" steps through the evolutions (skipping the one the stage already
	 * shows without a preview), and "Back", on the last, returns to the real look (2026-10-29: it only ever read "Back"
	 * while stepping forward, and a tier-15 weapon's Forms did nothing).
	 */
	private static void cyclePreview(Player player) {
		SoulWeaponData d = player.soulWeapon;
		if (d == null || !isOpen(player))
			return;
		Integer next = nextForm(player, previewing.get(player.getUserId()));
		if (next == null)
			previewing.remove(player.getUserId());
		else
			previewing.put(player.getUserId(), next);
		draw(player);
	}

	/** The form after {@code current} (null = none previewed yet), or null when the preview should close. */
	private static Integer nextForm(Player player, Integer current) {
		SoulWeaponData d = player.soulWeapon;
		int resting = viewingOwn(player) ? SoulWeaponType.band(d.tier) : LAST_FORM; // the look shown without a preview
		for (int f = current == null ? 0 : current + 1; f <= LAST_FORM; f++)
			if (f != resting)
				return f;
		return null;
	}

	private static void select(Player player, int node) {
		if (player.soulWeapon == null || !isOpen(player))
			return;
		// clicking the inspected node again returns to the overview
		if (selected.getOrDefault(player.getUserId(), -1) == node)
			selected.remove(player.getUserId());
		else
			selected.put(player.getUserId(), node);
		draw(player);
	}

	private static int rankOf(SoulWeaponData d, SoulTalent t) {
		return Math.min(SoulTalent.MAX_RANK, d.allocatedTalents.getOrDefault(t.name(), 0));
	}

	/** Ranks spent in a row of the line's tree. */
	private static int rowSpent(SoulWeaponData d, SoulTalent[] talents, int row) {
		int sum = 0;
		for (int c = 0; c < SoulTalent.COLS; c++)
			sum += rankOf(d, talents[row * SoulTalent.COLS + c]);
		return sum;
	}

	/** Row 0 is always open; a later row once the row above holds {@link SoulTalent#ROW_UNLOCK} ranks. */
	private static boolean rowOpen(SoulWeaponData d, SoulTalent[] talents, int row) {
		return row == 0 || rowSpent(d, talents, row - 1) >= SoulTalent.ROW_UNLOCK;
	}

	/** Why a node can't take a rank right now, or null if it can. */
	private static String blocked(Player player, int node) {
		SoulWeaponData d = player.soulWeapon;
		if (!viewingOwn(player))
			return "This is a preview: your soul is bound to the " + LINE_NAMES[d.weaponType.ordinal()] + ".";
		SoulTalent[] talents = SoulTalent.of(d.weaponType);
		int row = node / SoulTalent.COLS;
		if (!rowOpen(d, talents, row))
			return "Row " + ROMAN[row] + " opens after " + SoulTalent.ROW_UNLOCK + " ranks in row " + ROMAN[row - 1] + ".";
		if (rankOf(d, talents[node]) >= SoulTalent.MAX_RANK)
			return talents[node].name + " is already mastered.";
		if (d.availableSoulPoints <= 0)
			return "You have no Soul Points to spend. Each new tier grants one.";
		return null;
	}

	/** Rank Up: nothing is spent yet, the modal asks first (a point can't go by a stray click). */
	private static void askInvest(Player player, int node) {
		SoulWeaponData d = player.soulWeapon;
		if (d == null || !isOpen(player) || node < 0 || confirming.containsKey(player.getUserId()))
			return;
		String why = blocked(player, node);
		if (why != null) {
			player.sendMessage(SoulWeapons.TAG + why);
			return;
		}
		confirming.put(player.getUserId(), node);
		draw(player);
	}

	private static void invest(Player player, int node) {
		SoulWeaponData d = player.soulWeapon;
		if (d == null || !isOpen(player))
			return;
		String why = blocked(player, node); // re-checked: the state may have changed under the modal
		if (why != null) {
			player.sendMessage(SoulWeapons.TAG + why);
			draw(player);
			return;
		}
		SoulTalent talent = SoulTalent.of(d.weaponType)[node];
		int rank = rankOf(d, talent);
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
			SoulAura.play(player, aura); // a taste of it right away
		draw(player);
	}

	/** Reset: the modal states the refund (and the price) before anything happens. */
	private static void confirmReset(Player player) {
		SoulWeaponData d = player.soulWeapon;
		if (d == null || !isOpen(player) || confirming.containsKey(player.getUserId()))
			return;
		if (spent(d) == 0) {
			player.sendMessage(SoulWeapons.TAG + "You haven't spent any Soul Points yet.");
			return;
		}
		confirming.put(player.getUserId(), RESET_PENDING);
		draw(player);
	}

	private static void reset(Player player) {
		SoulWeaponData d = player.soulWeapon;
		if (d == null || spent(d) == 0) {
			if (d != null && isOpen(player))
				draw(player);
			return;
		}
		if (d.talentResets > 0) {
			if (player.PvmPoints < RESET_COST) {
				player.sendMessage(SoulWeapons.TAG + "You need " + NumberUtils.formatNumber(RESET_COST)
						+ " PvM points to reset your Soul Tree.");
				draw(player);
				return;
			}
			player.PvmPoints -= RESET_COST;
		}
		int refund = spent(d);
		d.allocatedTalents.clear();
		d.availableSoulPoints += refund;
		d.talentResets++;
		selected.remove(player.getUserId());
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
		SoulWeaponType line = viewed(player);
		boolean own = line == d.weaponType;
		int band = own ? SoulWeaponType.band(d.tier) : LAST_FORM;
		Integer preview = previewing.get(player.getUserId());
		int shownLook = line.looks()[preview == null ? band : preview];
		ObjType ownLook = ObjType.get(d.weaponType.itemFor(d.tier));

		// the title follows the weapon on the stage: your own (with its tier) or the line you are previewing
		String viewedName = own ? ownLook.name : ObjType.get(line.looks()[LAST_FORM]).name;
		ps.sendString(INTERFACE, TITLE, ORANGE + "Soul Tree" + END + " - " + CYAN + viewedName
				+ (own ? " (Tier " + d.tier + ")" : " (Preview)") + END);
		ps.sendString(INTERFACE, SUBTITLE, own ? "Spend Soul Points to awaken your weapon's talents"
				: MUTED + "Previewing the " + viewedName + "'s tree" + END);
		ps.sendString(INTERFACE, POINTS, "Available Points: " + (d.availableSoulPoints > 0 ? WHITE : MUTED) + d.availableSoulPoints + END);

		// the pages and their tabs
		boolean talentsShown = talentsPage.contains(player.getUserId());
		ps.setHidden(INTERFACE, PAGE_WEAPON, talentsShown);
		ps.setHidden(INTERFACE, PAGE_TALENTS, !talentsShown);
		for (int tab : new int[]{TAB_WEAPON, TAB_TALENTS}) {
			boolean active = (tab == TAB_TALENTS) == talentsShown;
			ps.setHidden(INTERFACE, tab + TAB_RECESS, !active);
			ps.setHidden(INTERFACE, tab + TAB_TRIM, !active);
			String label = tab == TAB_WEAPON ? "Weapon" : "Talents" + (own && d.availableSoulPoints > 0 ? " (" + d.availableSoulPoints + ")" : "");
			ps.sendString(INTERFACE, tab + TAB_LABEL, (active ? GOLD : MUTED) + label + END);
		}

		// the Weapon page: the stage and the weapon's codex
		ps.sendString(INTERFACE, STAGE_NAME, ObjType.get(shownLook).name);
		SoulWeaponCamera.show(ps, INTERFACE, MODEL, SoulWeaponCamera.Box.TREE, shownLook);
		ps.sendString(INTERFACE, STAGE_LINE, (own ? TEAL : MUTED) + LINE_NAMES[line.ordinal()] + END);
		ps.sendString(INTERFACE, PREVIEW_BUTTON + 1, preview == null ? TEAL + "Forms" + END
				: nextForm(player, preview) == null ? GOLD + "Back" + END : TEAL + "Next form" + END);
		ps.sendString(INTERFACE, INFO_TITLE, own ? "Your " + LINE_NAMES[line.ordinal()] : LINE_NAMES[line.ordinal()] + " (preview)");
		ps.sendString(INTERFACE, INFO_BODY, weaponInfo(d, line, own, band, preview));

		SoulTalent[] talents = SoulTalent.of(line);
		int nodes = talents.length;
		int[] ranks = new int[nodes];
		for (int n = 0; n < nodes; n++)
			ranks[n] = own ? rankOf(d, talents[n]) : 0;
		boolean[] open = new boolean[SoulTalent.ROWS];
		for (int r = 0; r < SoulTalent.ROWS; r++)
			open[r] = own && rowOpen(d, talents, r);

		// the soul links: a column's link lights once its upper node holds a rank and the row below is open (gold once
		// that node is mastered)
		for (int r = 0; r < SoulTalent.ROWS - 1; r++)
			for (int c = 0; c < SoulTalent.COLS; c++) {
				int upper = ranks[r * SoulTalent.COLS + c];
				int colour = !open[r + 1] || upper == 0 ? LINK_DIM : upper >= SoulTalent.MAX_RANK ? LINK_GOLD : LINK_LIT;
				ps.sendClientScript(SET_COLOUR, "Ii", INTERFACE << 16 | (FIRST_LINK + r * SoulTalent.COLS + c), colour);
			}

		int sel = selected.getOrDefault(player.getUserId(), -1);
		for (int n = 0; n < nodes; n++) {
			SoulTalent t = talents[n];
			int node = FIRST_NODE + n * NODE_STRIDE, rank = ranks[n];
			boolean mastered = rank >= SoulTalent.MAX_RANK, rowOpen = open[n / SoulTalent.COLS];
			for (int a = 0; a < SoulTalent.MAX_RANK; a++)
				ps.setHidden(INTERFACE, node + NODE_ARC + a, a >= rank);
			ps.setHidden(INTERFACE, node + NODE_SELECT, n != sel);
			ps.setHidden(INTERFACE, node + NODE_HALO, !mastered);
			int socket = socketState(rank);
			for (int s = 0; s < 3; s++)
				ps.setHidden(INTERFACE, node + NODE_SOCKET + s, s != socket);
			ps.setGraphic(INTERFACE, node + NODE_ICON, ICON_SPRITE + t.ordinal());
			ps.setHidden(INTERFACE, node + NODE_DIM, rank > 0 || (rowOpen && own && d.availableSoulPoints > 0));
			ps.sendString(INTERFACE, node + NODE_BADGE, (mastered ? GOLD : rank > 0 ? CYAN : MUTED) + rank + "/" + SoulTalent.MAX_RANK + END);
			ps.sendString(INTERFACE, node + NODE_NAME, (n == sel ? WHITE : rowOpen ? GOLD : MUTED) + t.name + END);
			ps.sendString(INTERFACE, node + NODE_TIP, tooltip(t, rank, rowOpen || !own, own, n / SoulTalent.COLS));
		}

		if (sel < 0)
			drawOverview(ps, d, talents, ranks, own, line);
		else
			drawInspector(player, ps, d, talents[sel], ranks[sel], sel, own);

		SoulAura active = SoulAura.active(d);
		for (int a = 0; a < SoulAura.values().length; a++) {
			SoulAura aura = SoulAura.values()[a];
			int card = FIRST_AURA + a * AURA_STRIDE;
			boolean unlocked = aura.unlocked(d);
			ps.setHidden(INTERFACE, card + AURA_ORB, !unlocked);
			ps.setHidden(INTERFACE, card + AURA_SILHOUETTE, unlocked);
			ps.setHidden(INTERFACE, card + AURA_LOCK, unlocked);
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
		ps.sendString(INTERFACE, RESET + 1, "Reset tree");
		ps.sendString(INTERFACE, RESET_PRICE, d.talentResets == 0 ? GREEN + "First reset free" + END
				: NumberUtils.formatNumber(RESET_COST) + " PvM points");

		Integer pending = confirming.get(player.getUserId());
		ps.setHidden(INTERFACE, MODAL, pending == null);
		if (pending != null) {
			if (pending == RESET_PENDING) {
				int refund = spent(d);
				ps.sendString(INTERFACE, MODAL_TITLE, "Reset Soul Tree");
				ps.sendString(INTERFACE, MODAL_BODY, "Refund " + WHITE + refund + " Soul Point" + (refund == 1 ? "" : "s") + END
						+ " from every talent?<br>You will have " + GOLD + (d.availableSoulPoints + refund) + END + " to spend."
						+ "<br>" + (d.talentResets == 0 ? GREEN + "Your first reset is free." + END
						: "Costs " + ORANGE + NumberUtils.formatNumber(RESET_COST) + " PvM points" + END + "."));
			} else {
				SoulTalent pt = talents[pending];
				int from = ranks[pending];
				ps.sendString(INTERFACE, MODAL_TITLE, "Confirm Rank Up");
				ps.sendString(INTERFACE, MODAL_BODY, "Spend " + WHITE + "1 Soul Point" + END + " on " + GOLD + pt.name + END
						+ "<br>(Rank " + from + " -> " + (from + 1) + ")?<br>" + CYAN + pt.brief(from + 1) + END);
			}
			ps.sendString(INTERFACE, CONFIRM + 1, GREEN + "Confirm" + END);
			ps.sendString(INTERFACE, CANCEL + 1, "<col=e0402a>Cancel</col>");
		}
	}

	/** The inspector for one talent: current vs next rank and its Rank Up (or why not). */
	private static void drawInspector(Player player, PacketSender ps, SoulWeaponData d, SoulTalent it, int rank, int node,
									  boolean own) {
		ps.setHidden(INTERFACE, INSP_SOCKET, false);
		ps.setHidden(INTERFACE, INSP_ICON, false);
		ps.setGraphic(INTERFACE, INSP_SOCKET, SOCKET_SPRITE + socketState(rank) * 2);
		ps.setGraphic(INTERFACE, INSP_ICON, ICON_SPRITE + it.ordinal());
		ps.sendString(INTERFACE, INSP_NAME, it.name);
		ps.sendString(INTERFACE, INSP_TAGLINE, it.tagline);
		ps.sendString(INTERFACE, INSP_RANK, rank >= SoulTalent.MAX_RANK ? GOLD + "Mastered (5/5)" + END
				: rank == 0 ? MUTED + "Unawakened (0/5)" + END : "Rank " + rank + " / " + SoulTalent.MAX_RANK);
		ps.sendString(INTERFACE, INSP_NOTE, it.note() + (own && SoulTalents.empowered(d)
				? " " + GOLD + "Radiant: works as rank " + (int) Math.floor(rank * SoulTalents.RADIANT_EMPOWERMENT) + "." + END
				: rank > 0 ? " " + MUTED + "Radiant (T15): x1.5." + END : ""));
		ps.sendString(INTERFACE, INSP_NOW, CYAN + "Current Rank:" + END + " " + (rank == 0 ? MUTED + "none" + END : it.describe(rank)));
		ps.sendString(INTERFACE, INSP_NEXT, rank >= SoulTalent.MAX_RANK ? GOLD + "Fully mastered" + END
				: ORANGE + "Next Rank:" + END + " " + it.describe(rank + 1));
		String why = blocked(player, node);
		boolean canBuy = why == null;
		ps.setHidden(INTERFACE, INSP_RUNE, !canBuy);
		ps.sendString(INTERFACE, INSP_RUNE + 1, canBuy ? GREEN + "Rank Up" + END + " " + WHITE + "(1 pt)" + END
				: rank >= SoulTalent.MAX_RANK ? GOLD + "Mastered" + END : RED + shortReason(player, node) + END);
	}

	/** The overview (nothing selected): points, ranks per row and every bonus the build gives right now. */
	private static void drawOverview(PacketSender ps, SoulWeaponData d, SoulTalent[] talents, int[] ranks, boolean own,
									 SoulWeaponType line) {
		ps.setHidden(INTERFACE, INSP_SOCKET, true);
		ps.setHidden(INTERFACE, INSP_ICON, true);
		ps.sendString(INTERFACE, INSP_NAME, "Soul Overview");
		ps.sendString(INTERFACE, INSP_TAGLINE, own ? "Your build" : LINE_NAMES[line.ordinal()] + " (preview)");
		int spent = 0;
		for (int r : ranks)
			spent += r;
		ps.sendString(INTERFACE, INSP_RANK, WHITE + d.availableSoulPoints + END + " free, " + CYAN + spent + END + " spent");
		StringBuilder rows = new StringBuilder();
		for (int r = 0; r < SoulTalent.ROWS; r++) {
			int sum = 0;
			for (int c = 0; c < SoulTalent.COLS; c++)
				sum += ranks[r * SoulTalent.COLS + c];
			rows.append(r == 0 ? "" : ", ").append(GOLD).append(ROMAN[r]).append(END).append(" ").append(sum);
		}
		ps.sendString(INTERFACE, INSP_NOTE, "Ranks by row: " + rows);
		StringBuilder bonuses = new StringBuilder();
		int shown = 0;
		for (int n = 0; n < talents.length; n++)
			if (ranks[n] > 0) {
				if (shown == 2) {
					bonuses.append("<br>").append(MUTED).append("+ more: click a node").append(END);
					break;
				}
				bonuses.append(shown == 0 ? "" : "<br>").append(CYAN).append(talents[n].name).append(END).append(" ")
						.append(talents[n].brief(ranks[n]));
				shown++;
			}
		ps.sendString(INTERFACE, INSP_NOW, CYAN + "Active bonuses:" + END + (shown == 0 ? " " + MUTED + "none yet" + END : ""));
		ps.sendString(INTERFACE, INSP_NEXT, shown == 0 ? MUTED + "Click a talent to inspect it." + END : bonuses.toString());
		ps.setHidden(INTERFACE, INSP_RUNE, true);
		ps.sendString(INTERFACE, INSP_RUNE + 1, MUTED + "Pick a talent" + END);
	}

	private static String shortReason(Player player, int node) {
		SoulWeaponData d = player.soulWeapon;
		if (!viewingOwn(player))
			return "Preview only";
		int row = node / SoulTalent.COLS;
		if (!rowOpen(d, SoulTalent.of(d.weaponType), row))
			return "Needs " + SoulTalent.ROW_UNLOCK + " ranks in row " + ROMAN[row - 1];
		return "Needs 1 Soul Point";
	}

	/** The Weapon page's codex: tier and progress, the form on the stage, its special, and the forms still to come. */
	private static String weaponInfo(SoulWeaponData d, SoulWeaponType line, boolean own, int band, Integer preview) {
		StringBuilder b = new StringBuilder();
		if (own) {
			b.append(MUTED).append("Tier ").append(END).append(WHITE).append(d.tier).append(END).append(MUTED).append(" / ")
					.append(SoulWeapons.MAX_TIER).append(END);
			if (d.tier < SoulWeapons.MAX_TIER)
				b.append("<br>").append(MUTED).append("Soul XP ").append(END)
						.append(NumberUtils.formatNumber(d.soulXp)).append(MUTED).append(" / ").append(END)
						.append(NumberUtils.formatNumber(SoulWeapons.TIER_XP[d.tier + 1]));
			else
				b.append("<br>").append(GOLD).append("Fully awakened").append(END);
			b.append("<br>").append(MUTED).append("Souls harvested ").append(END).append(NumberUtils.formatNumber(d.soulsHarvested));
		} else
			b.append(MUTED).append("Bound to another soul:").append(END).append("<br>").append(MUTED)
					.append("its talents are a preview.").append(END);
		b.append("<br><br>").append(stageCaption(d, line, own, band, preview));
		int shownBand = preview == null ? band : preview;
		if (shownBand >= 2)
			b.append("<br>").append(CYAN).append("Special: ").append(END).append(SoulWeapons.specialName(line, shownBand));
		return b.toString();
	}

	/** The stage caption: the form shown and what the next evolution brings (or the preview's own bonuses). */
	private static String stageCaption(SoulWeaponData d, SoulWeaponType line, boolean own, int band, Integer preview) {
		if (preview == null)
			return own ? GOLD + FORMS[band] + " form" + END + "<br>" + (band == LAST_FORM ? MUTED + "Fully evolved" + END
					: MUTED + "Next: " + FORMS[band + 1] + " (T" + SoulWeaponType.firstTier(band + 1) + ")" + END)
					: GOLD + "Radiant form" + END + "<br>" + MUTED + "Not your line" + END;
		int tier = SoulWeaponType.firstTier(preview);
		return TEAL + FORMS[preview] + END + MUTED + " (T" + tier + ")" + END + "<br>" + (preview == LAST_FORM
				? GOLD + "Radiant special" + END : preview == 2 ? GREEN + "Special attack" + END : GREEN + "New look" + END);
	}

	/** 0 bronze (unlearned), 1 soul-lit, 2 gold (mastered): the socket sprite offset / 2. */
	private static int socketState(int rank) {
		return rank >= SoulTalent.MAX_RANK ? 2 : rank > 0 ? 1 : 0;
	}

	/** A node's hover tooltip, drawn by the client plugin ("</br>" = new line): current vs next rank. */
	private static String tooltip(SoulTalent t, int rank, boolean open, boolean own, int row) {
		return GOLD + t.name + END + "  " + MUTED + "[" + rank + "/" + SoulTalent.MAX_RANK + "]" + END + "</br>"
				+ CYAN + "Current Rank:" + END + " " + (rank == 0 ? MUTED + "none" + END : t.brief(rank))
				+ (rank < SoulTalent.MAX_RANK ? "</br>" + ORANGE + "Next Rank:" + END + " " + t.brief(rank + 1) : "")
				+ (!own ? "</br>" + MUTED + "Preview of another line" + END
				: !open ? "</br>" + RED + "Locked: " + SoulTalent.ROW_UNLOCK + " ranks in row " + ROMAN[row - 1] + END
				: "</br>" + MUTED + "Click to inspect" + END);
	}
}

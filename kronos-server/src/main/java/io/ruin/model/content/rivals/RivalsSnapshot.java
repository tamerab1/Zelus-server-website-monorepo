package io.ruin.model.content.rivals;

import io.ruin.model.World;
import io.ruin.model.content.pvppreset.PvpPresetManager;
import io.ruin.model.entity.player.Player;
import io.ruin.model.item.Item;
import io.ruin.model.item.ItemContainer;
import io.ruin.model.map.Position;
import io.ruin.model.skills.magic.SpellBook;
import io.ruin.model.stat.Stat;
import io.ruin.model.stat.StatType;
import io.ruin.model.var.VarPlayerRepository;

/**
 * Swaps a player's real loadout for a Rivals kit and back.
 * <ul>
 * <li>{@link #takeAndEquip} deep-copies (attributes included: charges, upgrades, perks) the inventory, equipment and
 * rune pouch, the 7 combat stats, spellbook, special energy and the spot to return to into PERSISTED player fields,
 * then clears everything and hands out the kit as phantom items (they can't be dropped, traded, sold or kept).</li>
 * <li>{@link #restore} wipes whatever is carried (only kit items can be) and puts the saved state back exactly.</li>
 * <li>{@link #recover} runs at login: a snapshot still present means the server stopped mid-match, so it is restored
 * then. The PvP preset system keeps its copy in transient fields only; this one survives a crash.</li>
 * </ul>
 */
public final class RivalsSnapshot {

	private static final StatType[] STATS = {StatType.Attack, StatType.Strength, StatType.Defence, StatType.Ranged,
		StatType.Prayer, StatType.Magic, StatType.Hitpoints};

	private RivalsSnapshot() {
	}

	public static boolean active(Player player) {
		return player.rivalsSavedInventory != null;
	}

	/**
	 * True (with a message) while a Rivals snapshot is active: presets / loadouts / the bank must not move items in or
	 * out mid-match -- the containers hold the kit and are wiped on restore, so real items would be lost (or the kit
	 * banked). Called by every preset system's load entry point and Bank.open().
	 */
	public static boolean refuse(Player player) {
		// Raid 4 rental gear is the same kind of swap (every preset loader already calls this)
		if (io.ruin.model.activities.raids.tfa.Raid4Rental.refuse(player))
			return true;
		if (!active(player))
			return false;
		player.sendMessage("You can't do that during a Rivals match.");
		return true;
	}

	/** Saves the real loadout, then equips the mode's kit. Never called twice without a restore in between. */
	static void takeAndEquip(Player player, RivalsMode mode) {
		take(player, mode, false);
	}

	/**
	 * Layout mode (RivalsLayout): the same persisted snapshot, but only the kit's 27 inventory items are handed out --
	 * nothing is equipped and no level / spellbook / energy changes -- so the kit is never worn in the world.
	 */
	static void takeForLayout(Player player, RivalsMode mode) {
		take(player, mode, true);
	}

	private static void take(Player player, RivalsMode mode, boolean layoutOnly) {
		if (active(player))
			throw new IllegalStateException(player.getName() + " already has a Rivals snapshot");
		player.rivalsSavedInventory = copy(player.getInventory());
		player.rivalsSavedEquipment = copy(player.getEquipment());
		player.rivalsSavedRunePouch = copy(player.getRunePouch());
		int[][] stats = new int[STATS.length][3];
		for (int i = 0; i < STATS.length; i++) {
			Stat s = player.getStats().get(STATS[i]);
			stats[i][0] = s.fixedLevel;
			stats[i][1] = s.currentLevel;
			stats[i][2] = (int) s.experience;
		}
		player.rivalsSavedStats = stats;
		player.rivalsSavedSpellbook = VarPlayerRepository.MAGIC_BOOK.get(player);
		player.rivalsSavedSpecial = VarPlayerRepository.SPECIAL_ENERGY.get(player);
		Position at = player.getPosition();
		player.rivalsReturnPosition = new int[]{at.getX(), at.getY(), at.getZ()};

		clear(player.getInventory());
		clear(player.getEquipment());
		clear(player.getRunePouch());
		Item[] inv = RivalsLayout.arrange(player, mode, mode.kit.kitInventory()); // the player's saved slot order
		for (int i = 0; i < inv.length && i < 28; i++)
			if (inv[i] != null)
				player.getInventory().set(i, phantom(inv[i]));
		if (layoutOnly) {
			sendAll(player);
			return;
		}
		Item[] eq = mode.kit.kitEquipment();
		for (int i = 0; i < eq.length; i++)
			if (eq[i] != null)
				player.getEquipment().set(i, phantom(eq[i]));
		// levels only: the presets' own {level, xp} pairs can be built before Stat.register() fills the XP table
		// (Rivals registers earlier in StaticInit), which left XP 0 -> bases of 1 and level-up spam (2026-10-15)
		int[][] levels = mode.kit.kitLevels();
		for (int i = 0; i < STATS.length; i++)
			if (levels[i] != null) {
				int level = levels[i][0];
				player.getStats().set(STATS[i], level, Stat.xpForLevel(level));
				player.getPacketSender().sendStat(STATS[i].ordinal(), level, Stat.xpForLevel(level));
			}
		player.setHp(player.getStats().get(StatType.Hitpoints).fixedLevel);
		if (mode.kit.kitSpellBook() != null)
			mode.kit.kitSpellBook().setActive(player);
		player.getCombat().restoreSpecial(100);
		player.getMovement().restoreEnergy(100);
		RivalsFairPlay.enter(player); // account buffs off until restore()
		sendAll(player);
	}

	/** Puts the saved loadout back; safe to call when there is nothing to restore. */
	static void restore(Player player) {
		RivalsFairPlay.leave(player); // every way out (result, forfeit, logout, login recovery) comes through here
		if (!active(player))
			return;
		clear(player.getInventory());
		clear(player.getEquipment());
		clear(player.getRunePouch());
		put(player.getInventory(), player.rivalsSavedInventory);
		put(player.getEquipment(), player.rivalsSavedEquipment);
		put(player.getRunePouch(), player.rivalsSavedRunePouch);
		int[][] stats = player.rivalsSavedStats;
		if (stats != null) {
			for (int i = 0; i < STATS.length && i < stats.length; i++) {
				player.getStats().set(STATS[i], stats[i][0], stats[i][2]);
				player.getStats().get(STATS[i]).currentLevel = stats[i][1];
				player.getPacketSender().sendStat(STATS[i].ordinal(), stats[i][1], stats[i][2]);
			}
			player.setHp(player.getStats().get(StatType.Hitpoints).currentLevel);
		}
		if (player.rivalsSavedSpellbook >= 0 && player.rivalsSavedSpellbook < SpellBook.VALUES.length)
			SpellBook.VALUES[player.rivalsSavedSpellbook].setActive(player);
		if (player.rivalsSavedSpecial >= 0)
			VarPlayerRepository.SPECIAL_ENERGY.set(player, player.rivalsSavedSpecial);
		player.rivalsSavedInventory = null;
		player.rivalsSavedEquipment = null;
		player.rivalsSavedRunePouch = null;
		player.rivalsSavedStats = null;
		player.rivalsSavedSpellbook = -1;
		player.rivalsSavedSpecial = -1;
		sendAll(player);
		player.getAppearance().update(); // always redraw: the kit's look must never outlive the kit
	}

	/** Where the player queued from (home if unknown). */
	static Position returnPosition(Player player) {
		int[] p = player.rivalsReturnPosition;
		return p == null || p.length < 3 ? World.HOME.copy() : new Position(p[0], p[1], p[2]);
	}

	/** Login: a snapshot left behind by a server stop mid-match is restored and the player sent back. */
	public static void recover(Player player) {
		if (!active(player))
			return;
		restore(player);
		player.getMovement().teleport(returnPosition(player));
		player.rivalsReturnPosition = null;
		player.sendMessage("<col=990000><shad=000000>[Rivals]</shad></col> Your Rivals match was interrupted; your gear and stats have been restored.");
	}

	private static Item phantom(Item item) {
		Item p = item.copy();
		p.putAttribute(PvpPresetManager.PHANTOM_KEY, "1");
		return p;
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

package io.ruin.model.content.rivals;

import io.ruin.model.World;
import io.ruin.model.entity.player.Player;
import io.ruin.model.entity.shared.listeners.LogoutListener;
import io.ruin.model.entity.shared.listeners.TeleportListener;
import io.ruin.model.inter.dialogue.OptionsDialogue;
import io.ruin.model.inter.utils.Option;
import io.ruin.model.item.Item;
import io.ruin.model.entity.shared.LockType;

import java.util.ArrayList;
import java.util.List;

/**
 * Per-bracket inventory layouts (2026-10-15). "Configure layout" on a matchmaking card puts the player in LOCKDOWN:
 * <ol>
 * <li>Their inventory, equipment, rune pouch, stats etc. go into the persisted, crash-safe snapshot a match uses
 * (RivalsSnapshot.takeForLayout) and only the kit's 27 inventory items are handed out (phantom; nothing worn).</li>
 * <li>They are locked (LockType.FULL_ALLOW_LOGOUT, re-asserted every tick): no walking, commands, objects, NPCs,
 * teleports, bank or presets; inventory clicks (eat / equip / drop) are refused (TabInventory). Dragging items in the
 * inventory is the one thing that works.</li>
 * <li>The Save / Reset / Cancel prompt can't be dismissed: it re-opens whenever it is closed.</li>
 * <li>Every exit -- Save, Reset, Cancel, 5 minutes (saves), logout / x-log / disconnect (saves, via the logout
 * listener, before the save to disk) -- restores the snapshot and unlocks. After a crash the snapshot is still in the
 * player's file and RivalsSnapshot.recover restores it at login.</li>
 * </ol>
 * <ul>
 * <li>Only the ORDER is saved: per slot, the item id that sits there (PlayerAttributes.rivalsLayouts, by bracket key).
 * {@link #arrange} lays the kit out by that map, taking each item from the kit itself, so nothing can be added or
 * removed; kit items the map doesn't place (e.g. after a kit change) fill the remaining slots in default order.</li>
 * </ul>
 */
public final class RivalsLayout {

	private static final String MODE_KEY = "rivals-layout-mode", BEFORE_KEY = "rivals-layout-listeners";
	private static final int TIMEOUT_TICKS = 500;
	private static final String TAG = "<col=990000><shad=000000>[Rivals]</shad></col> ";

	private RivalsLayout() {
	}

	public static boolean active(Player p) {
		return p.temp.get(MODE_KEY) instanceof RivalsMode;
	}

	/** The kit inventory in the player's saved order for this bracket (a fresh array; the default when none is saved). */
	static Item[] arrange(Player p, RivalsMode mode, Item[] kit) {
		int[] layout = layouts(p).get(mode.key);
		if (layout == null)
			return kit;
		List<Item> pool = new ArrayList<>();
		for (Item item : kit)
			if (item != null)
				pool.add(item);
		Item[] out = new Item[kit.length];
		for (int slot = 0; slot < out.length && slot < layout.length; slot++) {
			if (layout[slot] < 0)
				continue;
			for (int i = 0; i < pool.size(); i++)
				if (pool.get(i).getId() == layout[slot]) {
					out[slot] = pool.remove(i);
					break;
				}
		}
		int next = 0;
		for (Item left : pool) {
			while (next < out.length && out[next] != null)
				next++;
			if (next >= out.length)
				break; // can't happen: the kit never has more than 28 items
			out[next] = left;
		}
		return out;
	}

	static void start(Player p, RivalsMode mode) {
		p.closeInterfaces();
		String problem = RivalsQueueManager.problem(p);
		if (problem != null) {
			p.sendMessage(TAG + problem);
			return;
		}
		p.resetActions(true, true, true);
		RivalsSnapshot.takeForLayout(p, mode);
		p.temp.put(MODE_KEY, mode);
		p.lock(LockType.FULL_ALLOW_LOGOUT);
		TeleportListener teleportBefore = p.teleportListener;
		LogoutListener logoutBefore = p.logoutListener;
		TeleportListener noTeleport = pl -> {
			pl.sendMessage("Save or cancel your Rivals layout first.");
			return false;
		};
		LogoutListener saveOnLogout = new LogoutListener().onLogout(pl -> finish(pl, true));
		p.temp.put(BEFORE_KEY, new Object[]{teleportBefore, logoutBefore});
		p.teleportListener = noTeleport;
		p.logoutListener = saveOnLogout;
		p.sendMessage(TAG + "Arrange the <col=ffffff>" + mode.label + "</col> kit: drag the items in your inventory where you want them, "
			+ "then Save. Your own gear is safe and comes back either way.");
		prompt(p, mode);
		World.startEvent(e -> {
			int ticks = 0;
			while (p.isOnline() && p.temp.get(MODE_KEY) == mode) {
				if (p.teleportListener != noTeleport)
					p.teleportListener = noTeleport;   // region handlers (the Duel Arena lobby) may reset it
				if (p.logoutListener != saveOnLogout)
					p.logoutListener = saveOnLogout;
				if (!p.isLocked())
					p.lock(LockType.FULL_ALLOW_LOGOUT);
				if (!p.hasDialogue())
					prompt(p, mode);                   // the prompt can't be dismissed
				if (ticks++ >= TIMEOUT_TICKS) {
					p.sendMessage(TAG + "Layout mode ended after 5 minutes.");
					finish(p, true);
					return;
				}
				e.delay(1);
			}
		});
	}

	private static void prompt(Player p, RivalsMode mode) {
		p.dialogue(new OptionsDialogue("Rivals layout: " + mode.label,
			new Option("<col=00c000>Save layout</col>", () -> finish(p, true)),
			new Option("Reset to the default order", () -> {
				layouts(p).remove(mode.key);
				finish(p, false);
				p.sendMessage(TAG + mode.label + " is back to the default order.");
			}),
			new Option("Cancel (don't save)", () -> finish(p, false))));
	}

	/** Re-opens the Save / Reset / Cancel choice (the Overseer, or the card again while in layout mode). */
	static void reprompt(Player p) {
		if (p.temp.get(MODE_KEY) instanceof RivalsMode mode)
			prompt(p, mode);
	}

	/** Saves (optionally), ends layout mode and gives the player's own loadout back. Safe to call twice. */
	private static void finish(Player p, boolean save) {
		Object m = p.temp.remove(MODE_KEY);
		if (!(m instanceof RivalsMode mode))
			return;
		Object[] before = (Object[]) p.temp.remove(BEFORE_KEY);
		p.closeDialogue();
		if (save) {
			Item[] items = p.getInventory().getItems();
			int[] layout = new int[items.length];
			for (int i = 0; i < items.length; i++)
				layout[i] = items[i] == null ? -1 : items[i].getId();
			layouts(p).put(mode.key, layout);
			p.sendMessage(TAG + "Saved your <col=ffffff>" + mode.label + "</col> layout: matches use it from now on.");
		}
		RivalsSnapshot.restore(p);
		p.unlock();
		p.rivalsReturnPosition = null;
		// the listeners that were there before layout mode (e.g. the Duel Arena lobby's)
		p.teleportListener = before == null ? null : (TeleportListener) before[0];
		p.logoutListener = before == null ? null : (LogoutListener) before[1];
	}

	/** Saves made before layouts existed load the map as null. */
	private static java.util.Map<String, int[]> layouts(Player p) {
		if (p.rivalsLayouts == null)
			p.rivalsLayouts = new java.util.HashMap<>();
		return p.rivalsLayouts;
	}
}

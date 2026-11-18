package io.ruin.model.activities.fiveseals;

import io.ruin.cache.Color;
import io.ruin.cache.ObjType;
import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.InterfaceHandler;
import io.ruin.model.inter.ToplevelComponent;
import io.ruin.model.inter.actions.SimpleAction;
import io.ruin.model.item.Item;
import io.ruin.network.PacketSender;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The Sovereign Vault's loot window (user 2026-11-17: "when players open the rewards chest after finishing room 5 can we
 * make it opening the rewards in an interface like the other raids?"). Interface 1193 is a copy of the raid chest window
 * tfa_chests (973; cache stage 56 PatchSealsVaultIf) titled "The Sovereign Vault": a 6-slot grid over inv 1029 with
 * Bank-all / Take-all / Discard-all.
 * <p>
 * Each member's full-clear loot waits in {@link #PENDING} until they pick a button. Closing the window, logging out,
 * leaving or the run's time limit banks whatever is still in it: vault loot is never lost by accident.
 */
public final class FiveSealsVault {

	private FiveSealsVault() {
	}

	static final int GROUP = 1193, BANK = 5, TAKE = 7, DISCARD = 9, ITEMS = 11, INV = 1029;

	private static final Map<Player, List<Item>> PENDING = new WeakHashMap<>();

	static void register() {
		InterfaceHandler.register(GROUP, h -> {
			h.actions[BANK] = (SimpleAction) p -> resolve(p, 0);
			h.actions[TAKE] = (SimpleAction) p -> resolve(p, 1);
			h.actions[DISCARD] = (SimpleAction) p -> resolve(p, 2);
			// runs BEFORE the window closes, and also when it is re-opened over itself (next == GROUP): only a real close banks
			h.closedAction = (p, next) -> {
				if (next != GROUP && has(p))
					bank(p, "You close the vault; its loot was sent to your bank.", false); // it is closing already
			};
		});
	}

	static void put(Player p, List<Item> loot) {
		PENDING.put(p, loot);
	}

	static boolean has(Player p) {
		List<Item> loot = PENDING.get(p);
		return loot != null && !loot.isEmpty();
	}

	/** opens (or re-opens) the window over the player's waiting loot */
	static void open(Player p) {
		List<Item> loot = PENDING.get(p);
		if (loot == null || loot.isEmpty())
			return;
		PacketSender ps = p.getPacketSender();
		p.openInterface(ToplevelComponent.MAINMODAL, GROUP);
		ps.sendItems(INV, loot.toArray(new Item[0]));
		ps.sendClientScript(149, "IviiiIsssss", GROUP << 16 | ITEMS, INV, 2, 3, 0, -1, "", "", "", "", "");
		ps.sendIfEvents(GROUP, BANK, -1, -1, 1 << 6);
		ps.sendIfEvents(GROUP, TAKE, -1, -1, 1 << 1);
		ps.sendIfEvents(GROUP, DISCARD, -1, -1, 1 << 6);
	}

	/** 0 bank all, 1 take all (bank whatever doesn't fit), 2 discard all */
	private static void resolve(Player p, int action) {
		List<Item> loot = PENDING.remove(p);
		if (loot == null) {
			p.closeInterface(ToplevelComponent.MAINMODAL);
			return;
		}
		switch (action) {
			case 0 -> {
				for (Item item : loot)
					p.getBank().add(item.getId(), item.getAmount(), null);
				p.sendMessage(Color.DARK_GREEN.wrap("The vault's loot has been sent to your bank."));
			}
			case 1 -> {
				for (Item item : loot) {
					if (p.getInventory().hasRoomFor(item.getId(), item.getAmount()))
						p.getInventory().add(item.getId(), item.getAmount());
					else {
						p.getBank().add(item.getId(), item.getAmount(), null);
						p.sendMessage(item.getAmount() + " x " + ObjType.get(item.getId()).name + " was sent to your bank.");
					}
				}
			}
			default -> p.sendMessage("You discard the contents of the vault.");
		}
		p.getPacketSender().sendItems(INV);
		p.closeInterface(ToplevelComponent.MAINMODAL);
	}

	/** whatever is still waiting goes to the bank (logout, leaving, the time limit, closing the window) */
	static void bankAll(Player p, String message) {
		bank(p, message, true);
	}

	private static void bank(Player p, String message, boolean closeWindow) {
		List<Item> loot = PENDING.remove(p);
		if (loot == null || loot.isEmpty())
			return;
		for (Item item : loot)
			p.getBank().add(item.getId(), item.getAmount(), null);
		if (p.isOnline()) {
			if (closeWindow && p.isVisibleInterface(GROUP))
				p.closeInterface(ToplevelComponent.MAINMODAL);
			if (message != null)
				p.sendMessage(Color.DARK_GREEN.wrap(message));
		}
	}
}

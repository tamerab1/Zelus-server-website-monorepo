package io.ruin.model.activities.bosses.brutus;

import io.ruin.model.map.object.actions.ObjectAction;

/**
 * The Scar (OSRS wiki, 2026-11-14): reached through the Abyssal Rift in the Temple of the Eye, left the same way. The
 * Scar's rift (dt2_scar_rift 49204 at 2011,6432, op Enter) had no handler, so players who teleported in (ServerTeleports
 * THE_SCAR) had no way out. Entering it now takes them beside the Temple of the Eye's rift (gotr_abyssal_rift 43713 at
 * 3612,9526; the open floor south of it). The Scar's abyssal leeches: npcs/spawns/The_Scar.json.
 */
public final class TheScar {

	private TheScar() {
	}

	private static final int SCAR_RIFT = 49204;

	public static void register() {
		ObjectAction.register(SCAR_RIFT, 1, (p, obj) -> p.startEvent(e -> {
			p.lock();
			p.sendMessage("You step through the rift.");
			e.delay(1);
			p.getMovement().teleport(3615, 9519, 0);
			p.unlock();
		}));
	}
}

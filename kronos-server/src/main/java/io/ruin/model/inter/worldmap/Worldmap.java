package io.ruin.model.inter.worldmap;

import io.ruin.model.entity.player.Player;
import io.ruin.model.entity.player.PlayerDisplayMode;
import io.ruin.model.inter.ComponentID;
import io.ruin.model.inter.Interface;
import io.ruin.model.inter.InterfaceHandler;
import io.ruin.model.inter.ToplevelComponent;
import io.ruin.model.inter.ToplevelInterfaceType;
import io.ruin.model.inter.actions.SimpleAction;
import io.ruin.network.incoming.handlers.DisplayHandler;
import io.ruin.utility.CS2Script;

import static io.ruin.model.inter.InterfaceEventMask.ClickOp1;
import static io.ruin.model.inter.InterfaceEventMask.getMask;

public class Worldmap {

	public static void openWorldmapFull(Player p) {
		DisplayHandler.sendTopLevel(p, ToplevelInterfaceType.FULLSCREEN_INTERFACE);
		openWorldmap(p);
	}

	/**
	 * The player's "you are here" for the world map. Inside an instance (a DynamicMap region copy) that's the original
	 * area's coordinate, as on OSRS: the instance coordinates (e.g. 6879,9448 for the Royal Titans) aren't on the map,
	 * which then fell back to Lumbridge (user, 2026-11-14).
	 */
	public static int youAreHere(Player p) {
		// sailing: the player stands on the boat's deck instance; the map shows where the boat is on the sea
		if (p.boat != null)
			return p.boat.seaPosition().getTileHash();
		io.ruin.model.map.Position pos = p.getPosition();
		io.ruin.model.map.Region r = pos.getRegion();
		if (r != null && r.dynamicData != null && (r.dynamicRegionBaseX != 0 || r.dynamicRegionBaseY != 0)) {
			int x = r.dynamicRegionBaseX + (pos.getX() - r.baseX), y = r.dynamicRegionBaseY + (pos.getY() - r.baseY);
			return y + (x << 14) + (pos.getZ() << 28);
		}
		return pos.getTileHash();
	}

	public static void openWorldmap(Player p) {
		// clientscript 1749: arg 0 -> varc 188 worldmap_youarehere, arg 1 -> 1078 worldmap_clue, arg 2 -> 401 worldmap_gravestone
		// (the old order put the player's tile in the gravestone slot: a grave icon and no "you are here", 2026-11-16)
		CS2Script.WORLDMAP_TRANSMITDATA.sendScript(p, youAreHere(p), -1, -1);
		p.openInterface(ToplevelComponent.WORLD_MAP, Interface.WORLD_MAP);
		p.getPacketSender().sendIfEvents(595, 21, 0, 4, getMask(ClickOp1));
		//IfSetEvents(interfaceId = 595, componentId = 21, startIndex = 0, endIndex = 4, events = ClickOp1)
	}

	public static void register() {
		InterfaceHandler.register(Interface.WORLD_MAP, h -> h.actions[ComponentID.WorldMap.EXIT] = (SimpleAction) p -> {

			if (p.getToplevelType() == ToplevelInterfaceType.FULLSCREEN_INTERFACE) {
				p.getPacketSender().removeInterface(ToplevelInterfaceType.FULLSCREEN_INTERFACE, 37);//378);
				p.getPacketSender().removeInterface(ToplevelInterfaceType.FULLSCREEN_INTERFACE, 38);//378);
				PlayerDisplayMode.refresh(p);
			} else {
				p.closeInterface(ToplevelComponent.WORLD_MAP);
			}
		});
	}
}

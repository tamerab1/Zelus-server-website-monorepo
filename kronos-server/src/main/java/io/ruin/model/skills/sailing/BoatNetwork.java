package io.ruin.model.skills.sailing;

import io.ruin.model.entity.player.Player;
import io.ruin.model.map.Region;
import net.rsprot.protocol.game.outgoing.map.RebuildWorldEntityV4;
import net.rsprot.protocol.game.outgoing.map.util.RebuildRegionZone;
import net.rsprot.protocol.game.outgoing.zone.header.UpdateZoneFullFollows;
import net.rsprot.protocol.game.outgoing.zone.header.UpdateZonePartialFollows;
import net.rsprot.protocol.game.outgoing.zone.payload.LocAddChangeV2;
import net.rsprot.protocol.game.outgoing.zone.payload.LocAnim;

/**
 * The packets that describe a boat's deck to a client, sent inside that boat's active-world block: rebuild_worldentity
 * maps each instance zone back to its source zone (read from the DynamicMap's region data), then every deck zone is
 * reset and its locs added. Zone coordinates inside a world-entity block are relative to the entity's own build area,
 * whose single zone is (0,0).
 */
public final class BoatNetwork {

	private BoatNetwork() {
	}

	public static void describe(Player player, Boat boat) {
		if (player.rsprotSession == null)
			return;
		Region region = boat.map.swRegion;
		var provider = new RebuildWorldEntityV4.RebuildWorldEntityZoneProvider() {
			@Override
			public RebuildRegionZone provide(int zoneX, int zoneZ, int level) {
				int lx = zoneX - (region.baseX >> 3), ly = zoneZ - (region.baseY >> 3);
				if (region.dynamicData == null || lx < 0 || ly < 0 || lx > 7 || ly > 7)
					return null;
				int[] data = region.dynamicData[level][lx][ly];
				if (data == null || data[0] == 0)
					return null;
				int rotation = data[0] >>> 1 & 0x3;
				int srcY = data[0] >>> 3 & 0x7FF;
				int srcX = data[0] >>> 14 & 0x3FF;
				int srcZ = data[0] >>> 24 & 0x3;
				return new RebuildRegionZone(srcX, srcY, srcZ, rotation);
			}
		};
		player.rsprotSession.queue(new RebuildWorldEntityV4(boat.baseX, boat.baseY, 1, 1, provider));
		for (int level = 0; level < 4; level++) {
			player.rsprotSession.queue(new UpdateZoneFullFollows(0, 0, level));
			for (BoatType.Part part : boat.type.parts) {
				if (boat.levelOf(part) != level)
					continue;
				int x = boat.type.anchorX + part.offX(), y = boat.type.anchorY + part.offY();
				player.rsprotSession.queue(new LocAddChangeV2(boat.locFor(part), x & 7, y & 7, 10, part.rotation(), (byte) part.opFlags()));
			}
		}
	}

	/** This tick's deck loc animations (salvaging hook drop/active/reset, ...), inside the boat's active-world block. */
	public static void flushDeckAnims(Player player, Boat boat) {
		if (player.rsprotSession == null || boat.deckAnims.isEmpty())
			return;
		for (int[] a : boat.deckAnims) {
			player.rsprotSession.queue(new UpdateZonePartialFollows(0, 0, a[2]));
			player.rsprotSession.queue(new LocAnim(a[4], a[0] & 7, a[1] & 7, 10, a[3]));
		}
	}
}

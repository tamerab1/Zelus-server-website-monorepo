package io.ruin.model.entity.player;

import io.ruin.api.buffer.OutBuffer;
import io.ruin.model.map.Tile;

import java.util.ArrayDeque;
import java.util.Collection;

public class PlayerUpdater {

	/**
	 * How many active tiles (each may carry several game-object/ground-item packets) get flushed
	 * per tick while draining a region reveal. A teleport can reveal up to 9 regions at once, so
	 * sending every tile's contents in the same tick as before could spike into hundreds of
	 * packets in one go -- this spread it out so the client renders the area progressively
	 * instead of freezing on the burst. See Region#update.
	 */
	private static final int MAX_TILE_UPDATES_PER_TICK = 80;

	private Player player;
	public boolean updateRegion = false;
	private boolean justAdded = true;
	private final ArrayDeque<Tile> pendingTileUpdates = new ArrayDeque<>();

	public PlayerUpdater(Player player) {
		this.player = player;
	}

	public void init(OutBuffer out) {
	}

	public void process() {
		this.processMasks();
		if (updateRegion || player.updateRegion()) {
			player.getPacketSender().sendRegion(false);
			updateRegion = false;
		}
		drainTileUpdates();
	}

	/**
	 * Replaces whatever was still pending from a previous, not-yet-finished reveal -- a fresh
	 * rebuild resends everything relevant anyway, so any stale tiles left in the queue would be
	 * redundant (and may no longer even be in view).
	 */
	public void queueRegionTiles(Collection<Tile> tiles) {
		pendingTileUpdates.clear();
		pendingTileUpdates.addAll(tiles);
	}

	private void drainTileUpdates() {
		int sent = 0;
		while (sent < MAX_TILE_UPDATES_PER_TICK && !pendingTileUpdates.isEmpty()) {
			pendingTileUpdates.poll().update(player);
			sent++;
		}
	}

	private void processMasks() {
		for (var mask : player.getMasks()) {
			if (!mask.hasUpdate(this.justAdded)) {
				continue;
			}
			mask.send(this.player);
			mask.setSent(true);
		}
		this.justAdded = false;
	}
}

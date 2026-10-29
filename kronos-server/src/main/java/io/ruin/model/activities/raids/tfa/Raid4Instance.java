package io.ruin.model.activities.raids.tfa;

import io.ruin.model.map.Position;
import io.ruin.model.map.Region;
import io.ruin.model.map.Tile;
import io.ruin.model.map.dynamic.DynamicChunk;
import io.ruin.model.map.dynamic.DynamicMap;
import io.ruin.model.map.object.GameObject;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The party's copy of the raid map.
 * <p>
 * The raid spans 3x5 source regions, and a {@link DynamicMap} holds at most 2x2, so each room gets its own map
 * (the same split Tombs of Amascut uses). Rooms are built when the party first needs them and released once
 * they're done, which keeps the free-region pool from draining with several parties running.
 * {@link #dispose()} releases everything and is safe to call more than once.
 */
@Slf4j
public class Raid4Instance {

	private final Map<Raid4RoomType, DynamicMap> maps = new EnumMap<>(Raid4RoomType.class);
	private final List<GameObject> spawnedObjects = new ArrayList<>();
	private boolean disposed;

	public DynamicMap build(Raid4RoomType type) throws DynamicMap.DynamicMapBuildException {
		if (disposed)
			throw new DynamicMap.DynamicMapBuildException("Raid instance already disposed");
		DynamicMap existing = maps.get(type);
		if (existing != null)
			return existing;

		DynamicMap map = new DynamicMap();
		if (map.swRegion == null)
			throw new DynamicMap.DynamicMapBuildException("No free region for " + type);

		int sourceChunkX = type.sourceBaseX() >> 3;
		int sourceChunkY = type.sourceBaseY() >> 3;
		List<DynamicChunk> chunks = new ArrayList<>(64 * type.planes);
		for (int plane = 0; plane < type.planes; plane++) {
			for (int cx = 0; cx < 8; cx++) {
				for (int cy = 0; cy < 8; cy++) {
					chunks.add(new DynamicChunk(sourceChunkX + cx, sourceChunkY + cy, type.sourcePlane + plane)
							.pos(cx, cy, plane));
				}
			}
		}
		map.build(chunks);
		// build(List) doesn't record the source base, which convertX/convertY rely on
		map.swRegion.dynamicRegionBaseX = type.sourceBaseX();
		map.swRegion.dynamicRegionBaseY = type.sourceBaseY();
		map.makeDynamicMapMultiCombat();
		maps.put(type, map);
		return map;
	}

	public DynamicMap get(Raid4RoomType type) {
		return maps.get(type);
	}

	public boolean isBuilt(Raid4RoomType type) {
		return maps.containsKey(type);
	}

	/**
	 * Instance tile for a source-region local coordinate. Instance planes start at 0, whatever the room's
	 * source plane.
	 */
	public Position local(Raid4RoomType type, int localX, int localY, int plane) {
		DynamicMap map = maps.get(type);
		if (map == null)
			throw new IllegalStateException(type + " is not built");
		return new Position(map.swRegion.baseX + localX, map.swRegion.baseY + localY, plane);
	}

	/** Local coordinate (0-63) of an instance tile, or -1 if it isn't in this room. */
	public int localX(Raid4RoomType type, Position pos) {
		DynamicMap map = maps.get(type);
		return map == null ? -1 : pos.getX() - map.swRegion.baseX;
	}

	public int localY(Raid4RoomType type, Position pos) {
		DynamicMap map = maps.get(type);
		return map == null ? -1 : pos.getY() - map.swRegion.baseY;
	}

	/** The room the position is in, or null. */
	public Raid4RoomType roomAt(Position pos) {
		for (Map.Entry<Raid4RoomType, DynamicMap> entry : maps.entrySet()) {
			Region region = entry.getValue().swRegion;
			if (region != null && pos.getRegion() == region)
				return entry.getKey();
		}
		return null;
	}

	/**
	 * Nearest walkable tile to the local coordinate, searching outward up to {@code radius} tiles. Spawn and
	 * arrival tiles come from the loc layout, not a server capture, so this keeps a slightly-off guess from
	 * putting a player inside a wall.
	 */
	public Position walkable(Raid4RoomType type, int localX, int localY, int plane, int radius) {
		Position origin = local(type, localX, localY, plane);
		for (int r = 0; r <= radius; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dy = -r; dy <= r; dy++) {
					if (Math.max(Math.abs(dx), Math.abs(dy)) != r)
						continue;
					Tile tile = Tile.get(origin.getX() + dx, origin.getY() + dy, plane, false);
					if (tile != null && tile.clipping == 0)
						return new Position(origin.getX() + dx, origin.getY() + dy, plane);
				}
			}
		}
		return origin;
	}

	/** Spawns a temporary, non-blocking loc and tracks it so disposal can't leave it behind. */
	public GameObject spawnObject(int id, Position pos, int type, int direction) {
		GameObject obj = GameObject.spawnUnclipped(id, pos, type, direction);
		spawnedObjects.add(obj);
		return obj;
	}

	public void removeObject(GameObject obj) {
		if (obj == null)
			return;
		spawnedObjects.remove(obj);
		obj.remove();
	}

	public void release(Raid4RoomType type) {
		DynamicMap map = maps.remove(type);
		if (map == null)
			return;
		spawnedObjects.removeIf(obj -> {
			if (obj.tile != null && map.swRegion != null && obj.tile.region == map.swRegion) {
				obj.remove();
				return true;
			}
			return false;
		});
		map.destroy();
	}

	public boolean isDisposed() {
		return disposed;
	}

	public void dispose() {
		if (disposed)
			return;
		disposed = true;
		for (GameObject obj : spawnedObjects) {
			try {
				obj.remove();
			} catch (Exception e) {
				log.error("Raid 4: failed to remove spawned loc {}", obj.getId(), e);
			}
		}
		spawnedObjects.clear();
		for (DynamicMap map : maps.values()) {
			try {
				map.destroy();
			} catch (Exception e) {
				log.error("Raid 4: failed to destroy room map", e);
			}
		}
		maps.clear();
	}
}

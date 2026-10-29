package io.ruin.model.content.soulweapons;

import io.ruin.cache.ObjType;
import io.ruin.network.PacketSender;

import java.util.HashMap;
import java.util.Map;

/**
 * Showcase cameras for the Soulbound looks in the three windows (2026-10-18, Soulbound v2 meshes), computed per look and
 * per model box by .dev/cache-restore-tool WidgetModelFrame (soulbound_v2_cameras.txt): the broad face shown, from a
 * 25-31 degree showcase pitch, as large as fits while the client plugin's +-150 sway keeps it inside the box. A model widget has no
 * offsets, so the item's icon framing (offset) can't be reused. The looks' zan2d is 0 (AutoFrameItems flat): a model
 * widget draws the item with its own zan2d, so a tilted icon came out end-on and tiny in the windows (2026-10-18).
 */
final class SoulWeaponCamera {
	enum Box {
		CHECK, SELECT, TREE
	}

	/** {zoom, angle X, angle Y} per box and item id. */
	private static final Map<Box, Map<Integer, int[]>> CAMERAS = new HashMap<>();

	static {
		put(Box.CHECK, 42624, 400, 176, 1184);
		put(Box.CHECK, 42626, 622, 176, 1472);
		put(Box.CHECK, 42628, 1537, 176, 1600);
		put(Box.CHECK, 42630, 505, 176, 672);
		put(Box.CHECK, 42632, 941, 176, 384);
		put(Box.CHECK, 42634, 786, 176, 640);
		put(Box.CHECK, 42636, 1169, 176, 736);
		put(Box.CHECK, 42638, 892, 176, 672);
		put(Box.CHECK, 42640, 1141, 144, 384);
		put(Box.CHECK, 42762, 1600, 176, 1600); // Radiant looks (customs27): the meshes are about the Ascended size
		put(Box.CHECK, 42763, 820, 176, 640);
		put(Box.CHECK, 42764, 1180, 144, 384);
		put(Box.SELECT, 42624, 444, 176, 1184);
		put(Box.SELECT, 42630, 559, 176, 1664);
		put(Box.SELECT, 42636, 1286, 176, 800);
		put(Box.TREE, 42624, 501, 176, 1184);
		put(Box.TREE, 42626, 764, 176, 1376);
		put(Box.TREE, 42628, 1881, 176, 1632);
		put(Box.TREE, 42630, 633, 176, 1600);
		put(Box.TREE, 42632, 1158, 176, 352);
		put(Box.TREE, 42634, 969, 160, 768);
		put(Box.TREE, 42636, 1436, 160, 800);
		put(Box.TREE, 42638, 1101, 176, 768);
		put(Box.TREE, 42640, 1775, 144, 384);
		put(Box.TREE, 42762, 1950, 176, 1632);
		put(Box.TREE, 42763, 1000, 160, 768);
		put(Box.TREE, 42764, 1800, 144, 384);
	}

	private SoulWeaponCamera() {
	}

	private static void put(Box box, int itemId, int zoom, int xan, int yan) {
		CAMERAS.computeIfAbsent(box, b -> new HashMap<>()).put(itemId, new int[]{zoom, xan, yan});
	}

	/**
	 * The Soul Tree's weapon stage (round 9, 2026-10-29: 260x118): look -> {showcase model, zoom, angle X}. The showcase
	 * models (customs27 build: ShiftTextureModel center) are the looks' inventory models with their bounding box centred
	 * on the origin -- the stage spins the weapon a full turn around the mesh origin, and the blades, modelled at the
	 * grip, orbited off the dais as a speck. Zoom: the widest the mesh gets at any yaw fits 240x110 (screen size ~ 512 *
	 * size / zoom, plus half the mesh for the near side's perspective). The Radiant Soulblade stands on its edge, so it
	 * gets a low pitch; the flat looks keep the 31 degree showcase pitch so their face shows.
	 */
	private static final Map<Integer, int[]> STAGE = Map.ofEntries(
			Map.entry(42762, new int[]{62789, 491, 64}),
			Map.entry(42763, new int[]{62790, 702, 176}),
			Map.entry(42764, new int[]{62791, 1016, 176}),
			Map.entry(42624, new int[]{62792, 346, 176}),
			Map.entry(42626, new int[]{62793, 488, 176}),
			Map.entry(42628, new int[]{62794, 702, 176}),
			Map.entry(42630, new int[]{62795, 377, 176}),
			Map.entry(42632, new int[]{62796, 771, 176}),
			Map.entry(42634, new int[]{62797, 743, 176}),
			Map.entry(42636, new int[]{62798, 947, 176}),
			Map.entry(42638, new int[]{62799, 753, 176}),
			Map.entry(42640, new int[]{62800, 1220, 176}));

	/** Shows a look in a model widget with its showcase camera (the icon's own angles at half zoom if none is known). */
	static void show(PacketSender ps, int inter, int child, Box box, int itemId) {
		int[] stage = box == Box.TREE ? STAGE.get(itemId) : null;
		if (stage != null) {
			ps.sendModel(inter, child, stage[0]);
			ps.sendModelInformation(inter, child, stage[1], stage[2], 0);
			return;
		}
		ps.sendItem(inter, child, itemId, 1);
		Box cameraBox = box == Box.TREE ? Box.CHECK : box;
		int[] cam = CAMERAS.getOrDefault(cameraBox, Map.of()).get(itemId);
		if (cam == null) {
			ObjType def = ObjType.get(itemId);
			cam = new int[]{def.zoom2d / 2, def.xan2d, def.yan2d};
		}
		ps.sendModelInformation(inter, child, cam[0], cam[1], cam[2]);
	}
}

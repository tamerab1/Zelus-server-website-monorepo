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
		put(Box.CHECK, 31824, 400, 176, 1184);
		put(Box.CHECK, 31826, 622, 176, 1472);
		put(Box.CHECK, 31828, 1537, 176, 1600);
		put(Box.CHECK, 31830, 505, 176, 672);
		put(Box.CHECK, 31832, 941, 176, 384);
		put(Box.CHECK, 31834, 786, 176, 640);
		put(Box.CHECK, 31836, 1169, 176, 736);
		put(Box.CHECK, 31838, 892, 176, 672);
		put(Box.CHECK, 31840, 1141, 144, 384);
		put(Box.SELECT, 31824, 444, 176, 1184);
		put(Box.SELECT, 31830, 559, 176, 1664);
		put(Box.SELECT, 31836, 1286, 176, 800);
		put(Box.TREE, 31824, 501, 176, 1184);
		put(Box.TREE, 31826, 764, 176, 1376);
		put(Box.TREE, 31828, 1881, 176, 1632);
		put(Box.TREE, 31830, 633, 176, 1600);
		put(Box.TREE, 31832, 1158, 176, 352);
		put(Box.TREE, 31834, 969, 160, 768);
		put(Box.TREE, 31836, 1436, 160, 800);
		put(Box.TREE, 31838, 1101, 176, 768);
		put(Box.TREE, 31840, 1775, 144, 384);
	}

	private SoulWeaponCamera() {
	}

	private static void put(Box box, int itemId, int zoom, int xan, int yan) {
		CAMERAS.computeIfAbsent(box, b -> new HashMap<>()).put(itemId, new int[]{zoom, xan, yan});
	}

	/** Shows a look in a model widget with its showcase camera (the icon's own angles at half zoom if none is known). */
	static void show(PacketSender ps, int inter, int child, Box box, int itemId) {
		ps.sendItem(inter, child, itemId, 1);
		int[] cam = CAMERAS.getOrDefault(box, Map.of()).get(itemId);
		if (cam == null) {
			ObjType def = ObjType.get(itemId);
			cam = new int[]{def.zoom2d / 2, def.xan2d, def.yan2d};
		}
		ps.sendModelInformation(inter, child, cam[0], cam[1], cam[2]);
	}
}

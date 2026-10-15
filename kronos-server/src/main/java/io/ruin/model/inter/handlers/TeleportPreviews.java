package io.ruin.model.inter.handlers;

import com.google.gson.reflect.TypeToken;
import io.ruin.api.utils.JsonUtils;
import io.ruin.api.utils.ServerWrapper;

import java.io.File;
import java.io.FileReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * The Zelus Teleports (1152) preview per teleport, from teleport_previews.json, written by
 * .dev/cache-restore-tool BuildTeleportPreviews (spec teleport_previews.txt): the model to show (an NPC's parts merged
 * into one model with its recolours baked in and its animation skins kept), the stand animation, and the model's bounding
 * box, which TeleportNexus turns into a zoom that fits the preview box. Re-read whenever the file changes.
 */
final class TeleportPreviews {

	/** A plain class (not a record) so any Gson version can fill it. */
	static final class Preview {
		int model, anim, minX, maxX, minY, maxY, minZ, maxZ;
		/** 2nd / 98th percentile x and y (stray vertices ignored); zoom / centerY: optional fixed values from the spec. */
		int pMinX, pMaxX, pMinY, pMaxY, zoom, centerY;

		int model() {
			return model;
		}

		int anim() {
			return anim;
		}

		int minX() {
			return minX;
		}

		int maxX() {
			return maxX;
		}

		int minY() {
			return minY;
		}

		int maxY() {
			return maxY;
		}
	}

	private static Map<String, Preview> previews = Collections.emptyMap();
	private static long loadedModified = -1;

	static Preview get(ServerTeleports teleport) {
		File file = new File(ServerWrapper.dataFolder, "teleport_previews.json");
		long modified = file.exists() ? file.lastModified() : 0;
		if (modified != loadedModified) {
			loadedModified = modified;
			Map<String, Preview> loaded = new HashMap<>();
			if (file.exists()) {
				try (FileReader reader = new FileReader(file)) {
					Map<String, Preview> read = JsonUtils.GSON.fromJson(reader, new TypeToken<Map<String, Preview>>() {}.getType());
					if (read != null)
						loaded = read;
				} catch (Exception e) {
					ServerWrapper.logError("Unable to load teleport_previews.json", e);
				}
			}
			previews = loaded;
		}
		Preview preview = previews.get(teleport.name());
		return preview == null || preview.model() <= 0 ? null : preview;
	}

	private TeleportPreviews() {
	}
}

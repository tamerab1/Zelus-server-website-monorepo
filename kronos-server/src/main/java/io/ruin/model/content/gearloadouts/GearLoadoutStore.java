package io.ruin.model.content.gearloadouts;

import com.google.gson.reflect.TypeToken;
import io.ruin.api.utils.JsonUtils;
import io.ruin.api.utils.ServerWrapper;
import io.ruin.model.World;
import io.ruin.model.entity.player.Player;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GearLoadoutStore {

	private static final Type PRESET_LIST_TYPE = new TypeToken<ArrayList<GearLoadoutPreset>>() {}.getType();
	private static final Map<String, List<GearLoadoutPreset>> CACHE = new HashMap<>();

	public static synchronized List<GearLoadoutPreset> getPresets(Player player) {
		String key = key(player);
		return CACHE.computeIfAbsent(key, ignored -> load(player));
	}

	public static synchronized void save(Player player) {
		File file = file(player);
		File parent = file.getParentFile();
		if (parent != null && !parent.exists())
			parent.mkdirs();
		try (FileWriter writer = new FileWriter(file)) {
			JsonUtils.GSON_PRETTY.toJson(getPresets(player), writer);
		} catch (Exception e) {
			System.err.println("Unable to save gear loadouts for " + player.getName() + ": " + e.getMessage());
		}
	}

	private static List<GearLoadoutPreset> load(Player player) {
		File file = file(player);
		if (!file.exists())
			return new ArrayList<>();
		try (FileReader reader = new FileReader(file)) {
			ArrayList<GearLoadoutPreset> presets = JsonUtils.GSON.fromJson(reader, PRESET_LIST_TYPE);
			if (presets == null)
				return new ArrayList<>();
			for (GearLoadoutPreset preset : presets) {
				if (preset != null)
					preset.normalize();
			}
			return presets;
		} catch (Exception e) {
			System.err.println("Unable to load gear loadouts for " + player.getName() + ": " + e.getMessage());
			return new ArrayList<>();
		}
	}

	private static File file(Player player) {
		return new File(ServerWrapper.dataFolder, "runtime/saves/gear_loadouts/"
				+ World.stage.name().toLowerCase() + "/"
				+ World.type.name().toLowerCase() + "/"
				+ key(player) + ".json");
	}

	private static String key(Player player) {
		String name = player.getName() == null ? "unknown" : player.getName().trim().toLowerCase();
		return name.replaceAll("[^a-z0-9_-]", "_");
	}
}

package io.ruin.model.content.gearloadouts;

import io.ruin.api.utils.JsonUtils;
import io.ruin.api.utils.ServerWrapper;
import io.ruin.model.World;

import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FreeGearLoadoutKits {

	private static final String SOURCE_PLAYER = "333";
	private static final int KIT_LIMIT = 4;

	private static String cachedPath = "";
	private static long cachedLastModified = Long.MIN_VALUE;
	private static List<GearLoadoutPreset> cachedKits = Collections.emptyList();

	public static synchronized List<GearLoadoutPreset> getKits() {
		File file = sourceFile();
		long lastModified = file.exists() ? file.lastModified() : -1L;
		String path = file.getPath();
		if (path.equals(cachedPath) && lastModified == cachedLastModified)
			return cachedKits;

		cachedPath = path;
		cachedLastModified = lastModified;
		cachedKits = loadKits(file);
		return cachedKits;
	}

	private static List<GearLoadoutPreset> loadKits(File file) {
		if (!file.exists())
			return Collections.emptyList();
		try (FileReader reader = new FileReader(file)) {
			FreeKitProfile profile = JsonUtils.GSON.fromJson(reader, FreeKitProfile.class);
			if (profile == null || profile.gearPresets == null || profile.gearPresets.isEmpty())
				return Collections.emptyList();
			ArrayList<GearLoadoutPreset> kits = new ArrayList<>();
			for (int i = 0; i < Math.min(profile.gearPresets.size(), KIT_LIMIT); i++) {
				GearLoadoutPreset preset = profile.gearPresets.get(i);
				if (preset == null)
					continue;
				String name = preset.presetName == null || preset.presetName.isBlank() ? "Free Kit " + (i + 1) : preset.presetName;
				kits.add(new GearLoadoutPreset(name, preset.inventory, preset.equipment, preset.combatLevels, preset.combatCurrentLevels, preset.spellBook));
			}
			return Collections.unmodifiableList(kits);
		} catch (Exception e) {
			System.err.println("Unable to load free gear kits from " + file.getPath() + ": " + e.getMessage());
			return Collections.emptyList();
		}
	}

	private static File sourceFile() {
		File standaloneFile = new File(ServerWrapper.dataFolder, "gear_loadouts/free_kits.json");
		if (standaloneFile.exists())
			return standaloneFile;
		return sourcePlayerFile();
	}

	private static File sourcePlayerFile() {
		return new File(ServerWrapper.dataFolder, "runtime/saves/players/"
				+ World.stage.name().toLowerCase() + "/"
				+ World.type.name().toLowerCase() + "/"
				+ SOURCE_PLAYER + ".json");
	}

	private static class FreeKitProfile {
		private List<GearLoadoutPreset> gearPresets;
	}
}

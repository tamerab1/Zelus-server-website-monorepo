package io.ruin.model.content.referral;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import io.ruin.api.utils.ServerWrapper;
import io.ruin.model.World;

import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/**
 * Looks a player's save file up directly off disk so referral linking works even when the
 * target is offline. Player saves are plain fastjson2 JSON keyed by field name (see
 * PlayerDatabase/DatabaseFile) and named by {@code name.toLowerCase().trim()} (see
 * Player#uuid()), so a Gson-parsed subset containing only the handful of fields referral
 * checks need sidesteps ever needing to construct a full Player - which for the Mongo mirror
 * of this same data throws IllegalStateException("unimplemented") on read (see
 * MongoPlayerMirrorImpl's class comment).
 *
 * Only hwid/hwids are read for the shared-identity check (not IP) since ipAddress is saved
 * under a renamed JSON key ("lastIpAddress") that isn't safe to assume here.
 */
final class OfflineReferralLookup {

	private static final Gson GSON = new Gson();

	private static final Path SAVE_ROOT = Path.of(
			ServerWrapper.dataFolder.getAbsolutePath(),
			"runtime", "saves", "players",
			World.stage.name().toLowerCase(),
			World.type.name().toLowerCase()
	);

	private static final class Profile {
		String name;
		String referredBy;
		String hwid;
		Set<String> hwids;
	}

	record Record(String name, String referredBy, String hwid, Set<String> hwids) {
	}

	static Record find(String playerName) {
		Path file = SAVE_ROOT.resolve(playerName.toLowerCase().trim() + ".json");
		if (!Files.exists(file))
			return null;
		try (FileReader reader = new FileReader(file.toFile())) {
			Profile profile = GSON.fromJson(reader, Profile.class);
			if (profile == null || profile.name == null)
				return null;
			return new Record(profile.name, profile.referredBy, profile.hwid,
					profile.hwids == null ? Set.of() : profile.hwids);
		} catch (IOException | JsonSyntaxException e) {
			return null;
		}
	}

	private OfflineReferralLookup() {
	}

}

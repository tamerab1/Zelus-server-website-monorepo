package io.ruin.services;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * kronos-server can't depend on the player-mongo module directly (player-mongo already depends
 * on kronos-server for Player/Server, so the reverse edge would be circular) -- so player-mongo's
 * Module.start() registers an implementation of this into MongoPlayerMirrorRegistry instead,
 * mirroring the existing Player.hooks registration pattern. LaunchWipe uses this to back up/wipe
 * the mongo `players` mirror collection without a build-time dependency on player-mongo.
 */
public interface MongoPlayerMirror {

	/** Every stored player name, for dry-run counting/matching. */
	List<String> allNames();

	/** Dumps every document as JSON to dest, returns the count written. */
	int dumpAll(Path dest);

	/** Deletes every document whose name doesn't normalize to something in preservedNormalized. */
	int deleteAllExcept(Set<String> preservedNormalized, Function<String, String> normalize);
}

package io.ruin.services;

public final class MongoPlayerMirrorRegistry {

	private static volatile MongoPlayerMirror instance;

	public static void register(MongoPlayerMirror impl) {
		instance = impl;
	}

	/** Null if the player-mongo module hasn't registered yet (or isn't loaded) -- callers must handle this. */
	public static MongoPlayerMirror get() {
		return instance;
	}
}

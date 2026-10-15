package io.ruin.model.content.rivals;

/** Exact roles: developers/owners, or explicit beta testers in a development world. */
public final class DivisionRivalsDemoAccess {
	private DivisionRivalsDemoAccess() {}

	public static boolean allowed(boolean developer, boolean betaTester, boolean developmentWorld) {
		return developer || (betaTester && developmentWorld);
	}

	/** Packet callbacks require both access and the currently visible demo interface/session. */
	public static boolean actionAllowed(boolean access, boolean interfaceVisible, boolean sessionExists) {
		return access && interfaceVisible && sessionExists;
	}
}

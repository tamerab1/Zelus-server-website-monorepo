package io.ruin.model.entity.player;

/**
 * PvP vs PvM/Ironman account segregation.
 *
 * This is orthogonal to {@link GameMode} (ironman tier): any {@link GameMode} other than
 * {@code STANDARD} is always treated as {@link #PVM_MODE} (see {@link #defaultFor}), since
 * ironman accounts can't rely on trading or system PvP gear anyway. Regular ({@code STANDARD})
 * accounts choose between the two at creation.
 */
public enum PlayMode {
	PVP_MODE,
	PVM_MODE;

	public boolean isPvpMode() {
		return this == PVP_MODE;
	}

	public boolean isPvmMode() {
		return this == PVM_MODE;
	}

	/**
	 * Fallback used for saves written before this field existed (where the stored value is
	 * {@code null}) and to force ironman accounts into PVM_MODE regardless of what's stored.
	 *
	 * Defaults to PVM_MODE: pre-existing standard accounts already had PvmPoints shop access
	 * before this PVP/PVM split existed, so defaulting them into PVP_MODE (which blocks that
	 * shop) silently took away something they used to have. PVP_MODE-specific features
	 * (::spawn, preset rental) are comparatively niche, so PVM_MODE is the safer default.
	 */
	public static PlayMode defaultFor(GameMode gameMode) {
		return PVM_MODE; // gameMode kept for API stability; ironman is already handled by the caller
	}
}

package io.ruin.api.protocol.world;

public enum WorldType {
	ECO("Karn", "https://karnps.com/"),
	BETA("KarnBeta", "https://karnps.com/"),
	PVP("KarnPvP", "https://karnps.com/"),

	DEADMAN("KarnDMM", "https://karnps.com/"),
	DEV("KarnDev", "https://karnps.com/");

	WorldType(String worldName, String websiteUrl) {
		this.worldName = worldName;
		this.websiteUrl = websiteUrl;
	}

	public boolean isDeadman() {
		return this == DEADMAN;
	}

	private String worldName, websiteUrl;

	public String getWorldName() {
		return worldName;
	}

	public String getWebsiteUrl() {
		return websiteUrl;
	}
}
package io.ruin.api.protocol.world;

public enum WorldType {
	ECO("Zelus", "https://zelusrsps.com/"),
	BETA("ZelusBeta", "https://zelusrsps.com/"),
	PVP("ZelusPvP", "https://zelusrsps.com/"),

	DEADMAN("ZelusDMM", "https://zelusrsps.com/"),
	DEV("ZelusDev", "https://zelusrsps.com/");

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
package io.ruin.cache;

public enum Icon {
	YELLOW_KEY(111),
	RED_INFO_BADGE(83),
	YELLOW_INFO_BADGE(70),
	MYSTERY_BOX(72),
	BLUE_INFO_BADGE(15),
	GREEN_INFO_BADGE(86),
	CHRISTMAS_TREE(133),
	CHRISTMAS_ICON(368),
	INFERNO(297),
	FIRE_CAPE(296),
	MAX_CAPE(298),
	WILDERNESS(76),
	DONATOR_ICON(90),
	ELITE_DONATOR(91),
	GOLD_DONATOR(95),
	SUPER_DONATOR(98),
	LEGENDARY_DONATOR(94),
	SUPREME_DONATOR(97),
	NOBLE_DONATOR(96),
	PLATINUM_DONATOR(102),
	WILDY_SKULL(9),
	LINK(12),
	WOODCUTTING(16),
	MINING(17),
	FISHING(18),
	CHAT_BUBBLE(19),
	GRAVE(21),
	LEAGUES(22),
	BOND(23),
	SWORDS(31),
	YOUTUBE(69),
	CHRISTMAS(133),
	ANNOUNCEMENT(73),
	ANNOUNCEMENT2(74),
	HCIM_DEATH(10),
	DONATOR(23),
	OWNER(1),
	ADMINISTRATOR(1),
	IRON_MAN(2),
	UIM(3),
	DEVELOPER(80),
	MODERATOR(0),
	SUPPORT(74),
	// Same id as WILDERNESS(76) above -- kept as a separate constant for call-site clarity.
	// 46 and 36 were tried first and rendered as a donator rank icon / orange star respectively,
	// not a skull -- confirmed wrong in-game. 76 is the id already live in WantedManager's
	// WANTED bounty broadcasts and other wilderness news messages.
	PVP_MODE_SKULL(76),
	;

	public final int imgId;

	Icon(int imgId) {
		this.imgId = imgId;
	}


	public String tag() {
		return "<img=" + imgId + ">";
	}

}

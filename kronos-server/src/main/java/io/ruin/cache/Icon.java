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
	// Gold trophy appended to the chat icon sheet (sprite 423, frame 104) by AppendGoldTrophyIcon: The Pit's announcements.
	PIT_TROPHY(104),

	// One icon per kind of broadcast (chat icon sheet, sprite 423), so a message's icon always matches what it announces.
	RARE_DROP(63),          // ruby: rare boss / monster / raid drops
	COLLECTION_LOG(38),     // gold star
	PET_DROP(20),           // paw print
	PK_SWORDS(31),          // crossed swords: kill-streak shutdowns
	WANTED_SKULL(75),       // red skull: WANTED bounties, bounty contracts
	WILDY_KEY(39),          // gold key: wilderness keys
	SERVER_EVENT(73),       // scroll: server events
	SERVER_RATES(15),       // info: XP / PKP / blood money rate changes
	VOTE_STAR(36),          // orange star: votes
	UPGRADE_GEM(68),        // diamond: item upgrades
	SKILL_STAR(37),         // silver star: skill milestones
	NEWCOMER_SMILE(32),     // smiley: welcomes, referrals
	ACHIEVEMENT_TROPHY(30), // red trophy: Inferno and other completions
	;

	public final int imgId;

	Icon(int imgId) {
		this.imgId = imgId;
	}


	public String tag() {
		return "<img=" + imgId + ">";
	}

}

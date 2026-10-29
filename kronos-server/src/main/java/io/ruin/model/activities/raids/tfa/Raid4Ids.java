package io.ruin.model.activities.raids.tfa;

/**
 * Cache ids for The Fractured Archive (Jagex prefix "TFA"), all vanilla rev 241 content. Names follow the
 * rev 241 gamevals so each id can be checked against the cache.
 * <p>
 * Local coordinates are offsets inside a 64x64 source region, read from the region's loc list
 * (see docs/raid4-tfa-blueprint.md, "Map").
 */
public final class Raid4Ids {

	private Raid4Ids() {
	}

	/* Archive Guardian */
	public static final int NPC_GUARDIAN_START = 16657; // TFA_GUARDIAN_START, op1 "Touch"
	/** TFA_ROOM_NAVIGATOR "Alliance Navigator" (Follow / Quick-follow): spawns when a boss dies, leads back to the Nexus. */
	public static final int NPC_ALLIANCE_NAVIGATOR = 16700;
	/** TFA_BLADE_NPC_VAULT_ENTRY "The Blade" (Commune / Travel): appears once Ocellus is dead, the way to the vault. */
	public static final int NPC_BLADE_VAULT_ENTRY = 16683;
	public static final int NPC_GUARDIAN_DEAD = 16658;
	public static final int NPC_GUARDIAN = 16659; // shielded, flying; its hp pool is the shield
	public static final int NPC_GUARDIAN_UNSHIELDED = 16660;
	public static final int NPC_BATTERY_INACTIVE = 16661;
	public static final int NPC_BATTERY_ACTIVE = 16662;
	public static final int NPC_BATTERY_DESTROYED = 16663;
	public static final int NPC_GUARDIAN_VINE = 16664; // the grounded-phase tendril

	public static final int SEQ_GUARDIAN_IDLE = 14745;
	public static final int SEQ_GUARDIAN_GROUND_IDLE = 14746;
	public static final int SEQ_GUARDIAN_IDLE_TO_GROUND = 14747;
	public static final int SEQ_GUARDIAN_GROUND_TO_FLYING = 14748;
	public static final int SEQ_GUARDIAN_DEATH = 14749;
	public static final int SEQ_GUARDIAN_SWOOSH = 14750;
	public static final int SEQ_GUARDIAN_RANGED = 14751;
	public static final int SEQ_GUARDIAN_MELEE = 14752;
	public static final int SEQ_GUARDIAN_SPECIAL = 14753;
	public static final int SEQ_GUARDIAN_ENRAGE = 14754;
	public static final int SEQ_GUARDIAN_SPAWN = 14755;
	public static final int SEQ_TENTACLE_ATTACK = 14768;
	public static final int SEQ_TENTACLE_DESPAWN = 14769;
	public static final int SEQ_TENTACLE_SPAWN = 14771;
	public static final int SEQ_BATTERY_DESPAWN = 15025;
	public static final int SEQ_BATTERY_DESPAWN_IDLE = 15026;

	public static final int GFX_GUARDIAN_RANGED_CAST = 4199;
	public static final int GFX_GUARDIAN_RANGED_HIT = 4200;
	public static final int GFX_GUARDIAN_RANGED_PROJ = 4201;
	/** 4201 without its skeletal sequence, added by build stage 6 (PatchRaid4Spotanims): 4201 draws off its flight path. */
	public static final int GFX_GUARDIAN_ROCK_STATIC = 6838;
	public static final int GFX_GUARDIAN_TO_GROUND = 4202;
	public static final int GFX_GUARDIAN_TO_FLYING = 4203;
	public static final int GFX_GUARDIAN_ENRAGE = 4204;
	public static final int GFX_GUARDIAN_SPECIAL = 4205;
	public static final int GFX_GUARDIAN_SPAWN = 4206;
	public static final int GFX_GUARDIAN_SPAWN_IDLE = 4207; // the dormant guardian's idle aura
	public static final int GFX_TENTACLE_INTRO = 4208;

	/* Ezekiel (region 16214) */
	public static final int NPC_EZEKIEL = 16652;
	public static final int NPC_EZEKIEL_SWORD = 16653; // "Animated Sword", 3x3, 80 hp = its charge bar
	public static final int NPC_EZEKIEL_FENCE_SWORD = 16654; // enrage fence, 3x3, no ops
	public static final int SEQ_EZEKIEL_FINALE_SWORD_DROP = 14710, SEQ_EZEKIEL_FINALE_SWORD_TELEGRAPH = 14711;
	public static final int NPC_EZEKIEL_FINALE_SWORD = 16655; // enrage lightning sword, 1x1, no ops
	public static final int NPC_EZEKIEL_DEATH_SWORD = 16656;

	public static final int SEQ_EZEKIEL_MELEE = 14685;
	public static final int SEQ_EZEKIEL_MAGIC = 14686;
	public static final int SEQ_EZEKIEL_SPECIAL = 14687;
	public static final int SEQ_EZEKIEL_HEAL = 14688;
	public static final int SEQ_EZEKIEL_DEATH = 14689;
	public static final int SEQ_EZEKIEL_IDLE_TO_WEAKEN = 14691;
	public static final int SEQ_EZEKIEL_WEAKEN = 14692;
	public static final int SEQ_EZEKIEL_WEAKEN_TO_IDLE = 14693;
	public static final int SEQ_EZEKIEL_FINALE = 14694;
	public static final int SEQ_EZEKIEL_FINALE_LOOP = 14696;
	/** The player's perfect-dodge animations (TFA_EZEKIEL_ROLL / _STRAFE: player skeleton, no NPC_ prefix). */
	public static final int SEQ_DODGE_ROLL = 14705, SEQ_DODGE_STRAFE = 14706;

	public static final int GFX_EZEKIEL_MAGIC_PROJ = 4167;
	public static final int GFX_EZEKIEL_MAGIC_HIT = 4168;
	public static final int GFX_EZEKIEL_SPECIAL_CRACK = 4169;
	public static final int GFX_EZEKIEL_CRACK = 4169, GFX_EZEKIEL_STAR_SPAWN = 4170;
	public static final int GFX_EZEKIEL_STAR_MARKER = 4171;
	public static final int GFX_EZEKIEL_STAR_EXPLOSION = 4172;
	public static final int GFX_EZEKIEL_HEAL = 4174;
	public static final int GFX_EZEKIEL_FINALE_CAST = 4175;
	public static final int GFX_EZEKIEL_SWORD_DROP = 4180;
	public static final int GFX_EZEKIEL_SWORD_TELEGRAPH = 4182;
	public static final int GFX_EZEKIEL_SWORD_DESPAWN = 4183;
	public static final int GFX_EZEKIEL_FINALE_IMPACT_LARGE = 4196;
	public static final int GFX_EZEKIEL_FINALE_IMPACT_MEDIUM = 4197;

	/** Shared by every boss room: "Exit" back to the Nexus once the boss is dead. */
	public static final int LOC_ROOM_EXIT = 62765;

	/** Every boss room with a sealed arena has one: "Use" / "Quick-use" moves you into the arena. */
	public static final int LOC_BOSS_TELEPORTER = 63087;
	public static final int LOC_TELEPORTER_MOIA = 62610, LOC_TELEPORTER_JELLY = 63134;

	/*
	 * Ezekiel layout (region 16214, local coords). The party arrives in the octagonal hall's east side. The arena is
	 * the 9x9 pit x/y 27-35, ringed by terrain-blocked tiles at 26 and 36, inside the invisible walls at 23/39; the
	 * boss teleporter at 41,31 is the only way in.
	 */
	public static final int EZEKIEL_LOCAL_X = 30, EZEKIEL_LOCAL_Y = 30; // SW tile of the 3x3, arena centre
	public static final int EZEKIEL_ENTRY_LOCAL_X = 50, EZEKIEL_ENTRY_LOCAL_Y = 31; // east corridor
	public static final int EZEKIEL_ARENA_ENTRY_LOCAL_X = 34, EZEKIEL_ARENA_ENTRY_LOCAL_Y = 31; // where the teleporter drops you
	public static final int EZEKIEL_OUTSIDE_LOCAL_X = 43, EZEKIEL_OUTSIDE_LOCAL_Y = 31; // beside the teleporter, after the kill
	public static final int EZEKIEL_HALL_MIN_X = 27, EZEKIEL_HALL_MAX_X = 35;
	public static final int EZEKIEL_HALL_MIN_Y = 27, EZEKIEL_HALL_MAX_Y = 35;
	/** The enrage 3x3 in the middle of the pit (SW tile); Ezekiel moves to the north side, just above it. */
	public static final int EZEKIEL_GRID_LOCAL_X = 30, EZEKIEL_GRID_LOCAL_Y = 30;

	/* Mushrooms are locs: spawn (plays SEQ 14756) -> active -> despawn. */
	public static final int LOC_MUSHROOM_SPAWN = 62578;
	public static final int LOC_MUSHROOM_ACTIVE = 62579;
	public static final int LOC_MUSHROOM_DESPAWN = 62580;

	/* Lobby (real world, region 16470) */
	public static final int LOBBY_REGION = 16470;
	public static final int LOC_LOBBY_RAID_ENTRY = 62768;
	public static final int LOC_LOBBY_GROUPING_BOARD = 62767;

	/* Nexus doors: one west/east pair per path choice, south to north */
	public static final int LOC_SECTION1_DOOR_WEST = 63088;
	public static final int LOC_SECTION1_DOOR_EAST = 63091;
	public static final int LOC_SECTION2_DOOR_WEST = 63096;
	public static final int LOC_SECTION2_DOOR_EAST = 63099;
	public static final int LOC_SECTION3_DOOR_WEST = 63102;
	public static final int LOC_SECTION3_DOOR_EAST = 63105;

	/* Nexus layout (region 16471, local coords) */
	public static final int GUARDIAN_LOCAL_X = 29, GUARDIAN_LOCAL_Y = 29; // SW tile of the 5x5; corpse loc sits here
	public static final int NEXUS_ARRIVAL_LOCAL_X = 31, NEXUS_ARRIVAL_LOCAL_Y = 12;
	public static final int NEXUS_SPECTATE_LOCAL_X = 31, NEXUS_SPECTATE_LOCAL_Y = 6;

	/** Battery NPCs (2x2) on the four battery bases, as {localX, localY}. */
	public static final int[][] BATTERY_LOCAL = {
			{ 18, 24 }, // south-west
			{ 18, 37 }, // north-west
			{ 43, 24 }, // south-east
			{ 43, 37 }, // north-east, beam-only
	};

	/* Lobby layout (region 16470 is real world, base 4096,5504) */
	public static final int LOBBY_RETURN_X = 4096 + 34, LOBBY_RETURN_Y = 5504 + 55; // (4130,5559): guardian_plan 12, vault_plan 3
	public static final int JINGLE_RAID_FAILED = 90;

	/* Bork and the Smorks (slot 2 east, region 16727). Names are the rev 241 gamevals. */
	public static final int NPC_BORK = 16640; // TFA_BORK, 4x4
	public static final int NPC_SMORKS = 16641; // TFA_SMORK, 3x3, the goblins' cannon tower in the middle
	public static final int SEQ_BORK_WALK = 14539, SEQ_BORK_IDLE = 14540, SEQ_BORK_DEATH = 14541;
	public static final int SEQ_BORK_GONG_MELEE = 14542, SEQ_BORK_STUN = 14543;
	public static final int SEQ_BORK_STUN_12 = 14544, SEQ_BORK_STUN_14 = 14545;
	public static final int SEQ_BORK_ENRAGED_GONG_IN = 14546, SEQ_BORK_ENRAGED_GONG = 14547, SEQ_BORK_ENRAGED_GONG_OUT = 14548;
	public static final int SEQ_BORK_STOMP_IN = 14549, SEQ_BORK_STOMP = 14550, SEQ_BORK_STOMP_CYCLE_OUT = 14551, SEQ_BORK_STOMP_OUT = 14552;
	public static final int SEQ_BORK_IDLE_TO_STOMP_IN = 14556, SEQ_BORK_IDLE_TO_GONG_IN = 14557;
	public static final int SEQ_SMORK_IDLE = 14553, SEQ_SMORK_CANNON = 14554, SEQ_SMORK_BIG_CANNON = 14555;
	public static final int GFX_BORK_DEATH = 4036, GFX_BORK_GONG_MELEE = 4037, GFX_BORK_STUN = 4038, GFX_BORK_STOMP = 4039;
	public static final int GFX_BORK_STUN_12 = 4040, GFX_BORK_STUN_14 = 4041;
	public static final int GFX_SMORK_CANNON = 4042, GFX_SMORK_BIG_CANNON = 4043, GFX_SMORK_BIG_CANNON_FLASH = 4044;
	public static final int GFX_SMORK_NAPALM_PROJ = 4045, GFX_SMORK_NAPALM_FIRE = 4046;
	public static final int GFX_SMORK_NAPALM_SPLAT = 4047; // + 0..3 for 0/90/180/270 degrees
	public static final int GFX_SMORK_NAPALM_DESPAWN = 4051; // + 0..3
	public static final int GFX_SMORK_RANGE_PLAYER = 4055, GFX_SMORK_RANGE_PROJ = 4056; // bones (live capture: 4056)
	public static final int GFX_SMORK_MAGE_PROJ = 4057, GFX_SMORK_MAGE_CAST = 4058, GFX_SMORK_MAGE_HIT = 4059; // potions (live: 4057)
	public static final int GFX_SMORK_TOWER_DEATH = 4060, GFX_SMORK_GOBLINS_DEATH = 4061, GFX_SMORK_DEATH_EXPLOSION = 4062;
	public static final int GFX_NPC_STUNNED_4X4 = 4035;
	/** Arena: walled by invisible walls on x/y 23 and 39 (tiles 23..39 inside); the Smorks' tower blocks 30..32. */
	public static final int BORK_ARENA_MIN = 23, BORK_ARENA_MAX = 39;
	public static final int SMORKS_LOCAL_X = 30, SMORKS_LOCAL_Y = 30;
	public static final int BORK_LOCAL_X = 34, BORK_LOCAL_Y = 30;
	public static final int BORK_ENTRY_LOCAL_X = 8, BORK_ENTRY_LOCAL_Y = 30; // inside the Nexus door (3,29), by the exit (4,29)
	public static final int BORK_OUTSIDE_LOCAL_X = 18, BORK_OUTSIDE_LOCAL_Y = 31; // beside the teleporter (20,31)
	public static final int BORK_ARENA_ENTRY_LOCAL_X = 24, BORK_ARENA_ENTRY_LOCAL_Y = 31;

	/* Moia (slot 3 west, region 16216) */
	public static final int NPC_MOIA = 16667, NPC_MOIA_BINDER = 16670; // TFA_MOIA (3x3), TFA_MOIA_MINION
	public static final int SEQ_MOIA_IDLE = 14783, SEQ_MOIA_DEATH = 14784, SEQ_MOIA_MAGIC = 14785, SEQ_MOIA_DAGGER = 14786;
	public static final int SEQ_MOIA_CHANT = 14787, SEQ_MOIA_TELEPORT = 14788;
	public static final int SEQ_BINDER_IDLE = 14789, SEQ_BINDER_WALK = 14790, SEQ_BINDER_ATTACK = 14791, SEQ_BINDER_DEATH = 14792;
	public static final int GFX_MOIA_DAGGER_MAGIC = 4213, GFX_MOIA_DAGGER = 4214, GFX_MOIA_DAGGER_SPIN = 4215, GFX_MOIA_MAGIC_IMPACT = 4216;
	public static final int GFX_MOIA_INFECT = 4217, GFX_MOIA_INFECT_PROJ = 4218; // live capture: 4213/4214
	public static final int GFX_MOIA_TELEPORT_OUT = 4219, GFX_MOIA_TELEPORT_IN = 4220, GFX_MOIA_TELEPORT_DEATH = 4221;
	public static final int GFX_BINDER_EMPOWER_OUT = 4222, GFX_BINDER_EMPOWER = 4223, GFX_BINDER_EMPOWER_IN = 4224;
	public static final int GFX_MOIA_CHANT_EXPLODE = 4225;
	/**
	 * The fight is in the fenced 9x9 plaza (27..35, fully enclosed: 81 tiles, flood-filled from the cache's clipping),
	 * made of nine 3x3 blocks; the eight outer blocks are the binders' "pink tiles". Moia stands outside the fence
	 * on one of four sides, out of melee reach.
	 */
	public static final int MOIA_ARENA_MIN = 27, MOIA_ARENA_MAX = 35, MOIA_PLAZA_MIN = 27, MOIA_PLAZA_MAX = 35;
	public static final int MOIA_ENTRY_LOCAL_X = 51, MOIA_ENTRY_LOCAL_Y = 30; // inside the Nexus door (55,29), by the exit (54,29)
	public static final int MOIA_OUTSIDE_LOCAL_X = 44, MOIA_OUTSIDE_LOCAL_Y = 31; // beside the teleporter (42,31)
	public static final int MOIA_ARENA_ENTRY_LOCAL_X = 28, MOIA_ARENA_ENTRY_LOCAL_Y = 31; // the west block, left free of binders

	/* Dreadnoth the Jellied (slot 3 east, region 16728) */
	public static final int NPC_DREADNOTH = 16665, NPC_DREADNOTH_JELLY = 16666; // TFA_JELLY_BOSS (5x5), TFA_JELLY_MINION
	public static final int SEQ_JELLY_JUMP_3 = 14773, SEQ_JELLY_JUMP_1 = 14774, SEQ_JELLY_SPAWNING_IDLE = 14775;
	public static final int SEQ_DREADNOTH_IDLE = 14776, SEQ_DREADNOTH_JUMP = 14777, SEQ_DREADNOTH_SLIDE = 14778;
	public static final int SEQ_DREADNOTH_MELEE = 14779, SEQ_DREADNOTH_JUMP_2 = 14780, SEQ_DREADNOTH_RANGED_VFX = 14781, SEQ_DREADNOTH_DEATH = 14782;
	public static final int GFX_DREADNOTH_JUMP = 4209, GFX_JELLY_SPAWNING = 4210, GFX_DREADNOTH_RANGED = 4211, GFX_DREADNOTH_RANGED_2 = 4212;
	public static final int LOC_JELLY_DAMAGE_TILE = 62609; // TFA_JELLY_BOSS_DAMAGE_TILE
	/** The walkable pit is 24..38 (flood-filled from the cache's clipping); octagram in the middle. */
	public static final int DREADNOTH_ARENA_MIN = 24, DREADNOTH_ARENA_MAX = 38;
	public static final int DREADNOTH_LOCAL_X = 29, DREADNOTH_LOCAL_Y = 29;
	public static final int DREADNOTH_ENTRY_LOCAL_X = 11, DREADNOTH_ENTRY_LOCAL_Y = 30; // inside the Nexus door (8,29), by the exit (9,29)
	public static final int DREADNOTH_OUTSIDE_LOCAL_X = 15, DREADNOTH_OUTSIDE_LOCAL_Y = 31; // beside the teleporter (17,31)
	public static final int DREADNOTH_ARENA_ENTRY_LOCAL_X = 24, DREADNOTH_ARENA_ENTRY_LOCAL_Y = 31;

	/* The Dagon'hai (slot 4 west, region 16215) */
	public static final int NPC_DAGONHAI_ELDER = 16643, NPC_DAGONHAI_ELDER_WEAK = 16644; // 2x2; the weak forms have 7 hp
	public static final int NPC_DAGONHAI_MONK_1 = 16645, NPC_DAGONHAI_MONK_1_WEAK = 16646;
	public static final int NPC_DAGONHAI_MONK_2 = 16647, NPC_DAGONHAI_MONK_2_WEAK = 16648;
	public static final int NPC_DAGONHAI_CAULDRON = 16649, NPC_DAGONHAI_IMP = 16650, NPC_DAGONHAI_RIFT = 16651;
	/** Per npc (elder, monk 1, monk 2): idle, weak idle, walk, cauldron charge idle / end / interrupted, death. */
	public static final int[] SEQ_DAGONHAI_CHARGE = { 14607, 14612, 14617 }, SEQ_DAGONHAI_CHARGE_END = { 14609, 14614, 14619 };
	public static final int[] SEQ_DAGONHAI_CHARGE_INTERRUPTED = { 14611, 14616, 14621 }, SEQ_DAGONHAI_DEATH = { 14652, 14653, 14654 };
	public static final int SEQ_DAGONHAI_RANGED = 14593, SEQ_DAGONHAI_MAGIC = 14597, SEQ_DAGONHAI_MAGIC_GUTHIX = 14604;
	public static final int[] SEQ_DAGONHAI_SEARCH = { 14622, 14630 }, SEQ_DAGONHAI_SEARCH_END = { 14623, 14631 };
	/** Book throws per monk, {zamorak, bandos, saradomin}. */
	public static final int[][] SEQ_DAGONHAI_THROW = { { 14624, 14625, 14626 }, { 14632, 14633, 14634 } };
	public static final int SEQ_CAULDRON_IDLE = 14656, SEQ_CAULDRON_ZAMORAK = 14657, SEQ_CAULDRON_SARADOMIN = 14659, SEQ_CAULDRON_BANDOS = 14661;
	public static final int GFX_DAGONHAI_SARA_DAMAGE_TELEGRAPH = 4063, GFX_DAGONHAI_SARA_HEAL_TELEGRAPH = 4064;
	public static final int GFX_DAGONHAI_SARA_DAMAGE_LIGHTNING = 4068, GFX_DAGONHAI_SARA_HEAL_LIGHTNING = 4069, GFX_DAGONHAI_SARA_HEAL_IMPACT = 4066;
	public static final int GFX_DAGONHAI_RANGED_CAST = 4070, GFX_DAGONHAI_RANGED_PROJ = 4071, GFX_DAGONHAI_RANGED_HIT = 4072; // live: 4071
	public static final int GFX_DAGONHAI_MAGIC_CAST = 4073, GFX_DAGONHAI_SURGE_PROJ = 4078;
	/** Cauldron charge idle/end, per element {fire, water, air, earth}: base + 2 * element (+1 = end). */
	public static final int GFX_DAGONHAI_CHARGE_FIRE = 4079;
	public static final int GFX_DAGONHAI_ELEMENT_FIRE = 4103; // + element index: fire, water, air, earth
	public static final int GFX_DAGONHAI_BOOK_PROJ_ZAMORAK = 4113, GFX_DAGONHAI_BOOK_PROJ_SARADOMIN = 4114, GFX_DAGONHAI_BOOK_PROJ_BANDOS = 4115;
	public static final int GFX_DAGONHAI_BOOK_IMPACT_ZAMORAK = 4116, GFX_DAGONHAI_BOOK_IMPACT_SARADOMIN = 4117, GFX_DAGONHAI_BOOK_IMPACT_BANDOS = 4118;
	public static final int GFX_DAGONHAI_ZAMORAK_FIREBALL = 4144, GFX_DAGONHAI_ZAMORAK_AOE = 4149;
	public static final int GFX_DAGONHAI_BANDOS_WAVE = 4156, GFX_DAGONHAI_DEMON_BUFF = 4162, GFX_DAGONHAI_DEATH = 4163;
	public static final int GFX_DAGONHAI_CAULDRON_EXPLOSION = 4161, GFX_DAGONHAI_CAULDRON_AOE = 4165, GFX_WAVE_DUST_SHORT = 4395;
	/** Walkable floor 23..42 x 22..40 (flood-filled from the cache's clipping). */
	public static final int DAGONHAI_ARENA_MIN_X = 23, DAGONHAI_ARENA_MAX_X = 42, DAGONHAI_ARENA_MIN_Y = 22, DAGONHAI_ARENA_MAX_Y = 40;
	public static final int DAGONHAI_CAULDRON_LOCAL_X = 32, DAGONHAI_CAULDRON_LOCAL_Y = 30;
	public static final int DAGONHAI_ENTRY_LOCAL_X = 53, DAGONHAI_ENTRY_LOCAL_Y = 30; // inside the Nexus door (57,29), by the exit (56,29)
	public static final int DAGONHAI_OUTSIDE_LOCAL_X = 49, DAGONHAI_OUTSIDE_LOCAL_Y = 31; // beside the teleporter (47,31)
	public static final int DAGONHAI_ARENA_ENTRY_LOCAL_X = 41, DAGONHAI_ARENA_ENTRY_LOCAL_Y = 31;
	/** The seven research desks (where a monk stands to read, a tile in front of each table). */
	public static final int[][] DAGONHAI_DESKS = { { 26, 27 }, { 32, 24 }, { 38, 25 }, { 41, 31 }, { 27, 35 }, { 33, 38 }, { 39, 35 } };

	/* Xarth (slot 4 east, region 16726) */
	public static final int NPC_XARTH = 16684, NPC_XARTH_PILLAR = 16685, NPC_XARTH_PILLAR_DESTROYED = 16686; // pillars are 2x2 npcs
	public static final int NPC_XARTH_CORPSE = 16687, NPC_XARTH_CORPSE_DOUSED = 16688;
	public static final int SEQ_XARTH_IDLE = 14979, SEQ_XARTH_MELEE = 14983, SEQ_XARTH_MAGIC = 14984, SEQ_XARTH_RANGED = 14985;
	public static final int SEQ_XARTH_FLAME_BLAST = 14989, SEQ_XARTH_ROOT = 14991, SEQ_XARTH_AOE = 14992, SEQ_XARTH_DEATH = 14995;
	public static final int SEQ_XARTH_RUBBLE_COLLAPSE = 14997, SEQ_XARTH_SUMMON = 15017;
	public static final int SEQ_CORPSE_IDLE = 14999, SEQ_CORPSE_WALK = 15000, SEQ_CORPSE_DEATH = 15003, SEQ_CORPSE_ABSORBED = 15005;
	public static final int GFX_XARTH_RANGED_CAST = 4373, GFX_XARTH_RANGED_PROJ = 4374, GFX_XARTH_RANGED_HIT = 4375; // live: 4374
	public static final int GFX_XARTH_AOE = 4376, GFX_XARTH_STUN = 4377, GFX_XARTH_AOE_PROJ = 4378, GFX_XARTH_AOE_HIT = 4379;
	public static final int GFX_XARTH_FLAME_BLAST = 4380, GFX_XARTH_DEATH = 4381, GFX_XARTH_RUBBLE_COLLAPSE = 4382;
	public static final int GFX_CORPSE_WALK_FIRE = 4383, GFX_CORPSE_DOUSED = 4384, GFX_CORPSE_DEATH = 4385, GFX_CORPSE_ABSORBED = 4386;
	public static final int GFX_XARTH_ABSORB = 4387, GFX_XARTH_MAGIC_PROJ = 4034; // LUC2 fireball: live capture 4034
	public static final int LOC_XARTH_ARENA_FIRE = 62713;
	/** Arena walled at 23/39 (tiles 23..39 inside); corpse piles ring it outside. */
	public static final int XARTH_ARENA_MIN = 23, XARTH_ARENA_MAX = 39, XARTH_LOCAL_X = 30, XARTH_LOCAL_Y = 30;
	public static final int XARTH_ENTRY_LOCAL_X = 11, XARTH_ENTRY_LOCAL_Y = 30; // inside the Nexus door (8,29), by the exit (9,29)
	public static final int XARTH_OUTSIDE_LOCAL_X = 16, XARTH_OUTSIDE_LOCAL_Y = 31; // beside the teleporter (18,31)
	public static final int XARTH_ARENA_ENTRY_LOCAL_X = 24, XARTH_ARENA_ENTRY_LOCAL_Y = 31;
	/** Pillar npc spawns (south-west tiles). Not in the cache (npcs are server data): placed symmetrically; est. */
	public static final int[][] XARTH_PILLARS = { { 26, 26 }, { 34, 27 }, { 26, 34 }, { 34, 34 }, { 25, 30 }, { 35, 30 } };

	/* Ocellus (final, region 16472; the arena is the round chamber on source plane 1, around The Blade at 31,41) */
	public static final int NPC_OCELLUS_P1 = 16674, NPC_OCELLUS_P2 = 16675, NPC_OCELLUS_BLADE = 16676, NPC_OCELLUS_P3 = 16677;
	public static final int NPC_VOID_RIFT = 16678, NPC_VOID_SIPHON = 16679, NPC_VOID_AUGMENTER = 16680, NPC_VOID_VINE = 16681;
	public static final int SEQ_OCELLUS_P1_IDLE = 14804, SEQ_OCELLUS_P1_WALK = 14805, SEQ_OCELLUS_P1_PROJ_START = 14806;
	public static final int SEQ_OCELLUS_P1_RAILGUN = 14808, SEQ_OCELLUS_P1_CHARGE = 14809, SEQ_OCELLUS_P1_EXPEND = 14810, SEQ_OCELLUS_P1_EXPLODE = 14811;
	public static final int SEQ_OCELLUS_P1_TELEPORT_OUT = 14812, SEQ_OCELLUS_P1_TELEPORT_IN = 14813, SEQ_OCELLUS_P1_TRANSFORM = 14814;
	public static final int SEQ_OCELLUS_P2_TRANSFORM = 14837, SEQ_OCELLUS_P2_IDLE = 14839;
	public static final int SEQ_OCELLUS_P3_IDLE = 14840, SEQ_OCELLUS_P3_IDLE_HIGH = 14841, SEQ_OCELLUS_P3_MELEE = 14842;
	public static final int SEQ_OCELLUS_P3_MELEE_HYBRID = 14844, SEQ_OCELLUS_P3_MELEE_PRAYER = 14845, SEQ_OCELLUS_P3_SLASH = 14846;
	public static final int SEQ_OCELLUS_P3_SUPERIOR_SLASH = 14848, SEQ_OCELLUS_P3_PROJ = 14851, SEQ_OCELLUS_P3_SOUL_START = 14855;
	public static final int SEQ_OCELLUS_P3_SOUL_END = 14856, SEQ_OCELLUS_P3_UNLEASH_IN = 14857, SEQ_OCELLUS_P3_UNLEASH_LOOP = 14858;
	public static final int SEQ_OCELLUS_QUADRANT_DESTROY_IN = 14859, SEQ_OCELLUS_QUADRANT_DESTROY_LOOP = 14860, SEQ_OCELLUS_P3_UNLEASH_OUT = 14861;
	public static final int SEQ_OCELLUS_P3_DEFLECT_IN = 14863, SEQ_OCELLUS_P3_DEFLECT_LOOP = 14864, SEQ_OCELLUS_P3_DEFLECT_OUT = 14865;
	public static final int SEQ_OCELLUS_P3_PRAYER_IN = 14869, SEQ_OCELLUS_P3_PRAYER_LOOP = 14870, SEQ_OCELLUS_P3_PRAYER_OUT = 14871;
	public static final int SEQ_OCELLUS_DEATH = 14872, SEQ_OCELLUS_P2_TO_P3_1 = 14878, SEQ_OCELLUS_P2_TO_P3_2 = 14881;
	public static final int SEQ_CENSER_READY = 14833, SEQ_CENSER_INACTIVE = 14834, SEQ_CENSER_ACTIVATE = 14835;
	public static final int GFX_OCELLUS_WATER_CAST = 4228, GFX_OCELLUS_WATER_PROJ = 4229, GFX_OCELLUS_WATER_HIT = 4230; // live: 4229
	public static final int GFX_OCELLUS_FIRE_CAST = 4231, GFX_OCELLUS_FIRE_PROJ = 4232, GFX_OCELLUS_FIRE_HIT = 4233; // live: 4232
	public static final int GFX_OCELLUS_CHARGE_EXPEND_PROJ = 4234;
	public static final int GFX_OCELLUS_CRACK_START = 4236, GFX_OCELLUS_CRACK = 4240, GFX_OCELLUS_CRACK_END = 4241;
	public static final int GFX_OCELLUS_P1_CHARGE = 4260, GFX_OCELLUS_P1_EXPLODE_NPC = 4261, GFX_OCELLUS_P1_EXPLODE_FLOOR = 4262;
	public static final int GFX_OCELLUS_TELEPORT_OUT = 4266, GFX_OCELLUS_TELEPORT_IN = 4267, GFX_OCELLUS_P1_TO_P2 = 4268;
	public static final int GFX_OCELLUS_PORTAL_PROJ = 4269, GFX_OCELLUS_P2_AOE = 4270;
	public static final int GFX_OCELLUS_SAFE_PORTAL_SPAWN = 4271, GFX_OCELLUS_SAFE_PORTAL_IDLE = 4272, GFX_OCELLUS_SAFE_PORTAL_DESPAWN = 4273;
	public static final int GFX_OCELLUS_DEATH = 4274, GFX_OCELLUS_SWORD_DISCHARGE = 4305;
	public static final int GFX_OCELLUS_ENTANGLE_START = 4290, GFX_OCELLUS_ENTANGLE_IDLE = 4291, GFX_OCELLUS_ENTANGLE_END = 4292;
	public static final int GFX_OCELLUS_MAGIC_HIT = 4293, GFX_OCELLUS_MAGIC_PROJ = 4297, GFX_OCELLUS_VOIDFLARE_HEAL_PROJ = 4303;
	public static final int GFX_OCELLUS_SOUL_SPAWN = 4309, GFX_OCELLUS_SOUL_IDLE = 4310, GFX_OCELLUS_SOUL_DESPAWN = 4312;
	public static final int GFX_OCELLUS_SOUL_PLAYER_REGAIN = 4314, GFX_OCELLUS_UNLEASH_ARENA = 4317, GFX_OCELLUS_P3_UNLEASH = 4321;
	public static final int GFX_OCELLUS_DEFLECT_WIND = 4322, GFX_OCELLUS_SHADOW_SLASH = 4335, GFX_OCELLUS_SUPERIOR_SLASH = 4336;
	public static final int GFX_VOID_CHAIN_EXPLOSION = 4339, GFX_VOID_CHAIN_TELEGRAPH = 4340;
	public static final int GFX_ATTACKGLOW_ZAROS = 4341, GFX_ATTACKGLOW_BANDOS = 4342, GFX_ATTACKGLOW_SARADOMIN = 4343, GFX_ATTACKGLOW_ARMADYL = 4344;
	public static final int GFX_OCELLUS_ARENA_COLLAPSE = 4331, GFX_OCELLUS_FALLING_DEBRIS = 4346;
	public static final int GFX_DEFLECT_ARENA_RANGE = 4349, GFX_DEFLECT_ARENA_MELEE = 4352;
	public static final int LOC_CENSER_MELEE = 62693, LOC_CENSER_RANGED = 62695, LOC_CENSER_SPEC = 62697, LOC_CENSER_HEAL = 62699; // +1 = inactive
	public static final int LOC_VOID_FLOOR = 62692, LOC_VOID_DEGEN_AOE = 62702;
	public static final int OCELLUS_PLANE = 1, OCELLUS_CENTRE_X = 30, OCELLUS_CENTRE_Y = 40; // his 3x3's south-west tile on The Blade
	public static final int OCELLUS_ARENA_MIN_X = 19, OCELLUS_ARENA_MAX_X = 44, OCELLUS_ARENA_MIN_Y = 27, OCELLUS_ARENA_MAX_Y = 55;
	public static final int OCELLUS_ENTRY_LOCAL_X = 31, OCELLUS_ENTRY_LOCAL_Y = 30; // the south arch, where the bridge comes in
	/** The four quadrant corners he walks to in phase 1, and the four censers' tiles (est.: by the braziers). */
	public static final int[][] OCELLUS_CORNERS = { { 24, 46 }, { 36, 46 }, { 36, 34 }, { 24, 34 } };
	public static final int[][] OCELLUS_CENSERS = { { 23, 33 }, { 39, 33 }, { 39, 49 }, { 23, 49 } };

	/* The vault (region 16474, the void island; source plane 2) */
	public static final int NPC_VAULT_CHEST_BIG = 16730, NPC_VAULT_CHEST_BIG_OPEN = 16731; // the special (unique) chest, 3x3
	public static final int NPC_VAULT_CHEST_FIRST = 16736; // per member: 16736 + 2 * slot is the chest, + 1 its open form
	public static final int LOC_VAULT_EXIT_PORTAL = 62990; // TFA_ENTRANCE_GUTHIXLOOT01_PORTAL01
	/** tfa_vault_exit "Rift" (31,13) r0 shape 10, and tfa_vault_exit_small (npc, 3x3) at (30,36) after the first chest. */
	public static final int LOC_VAULT_EXIT = 63297, NPC_VAULT_EXIT_SMALL = 16690, LOC_MERIT_SCROLL = 62775;
	/** The Nexus's scoreboard (tfa_in_raid_scoreboard / _broken) and tfa_loot_wheelbarrow (nexus_plan §4). */
	public static final int LOC_IN_RAID_SCOREBOARD = 62774, LOC_IN_RAID_SCOREBOARD_BROKEN = 62776, LOC_LOOT_WHEELBARROW = 63302;
	/** The lobby's upper floor exit (TFA_LOBBY_RAID_EXIT_FLOOR2). */
	public static final int LOC_LOBBY_EXIT_FLOOR2 = 63010;
	/** The Nexus's people (nexus_plan 1, 4): armacus_selles_postguardian, eva_cashien_sword, tfa_ivy_sophista_noop,
	 * alliance_guardian_of_armadyl_1..6 (16718-16723). */
	public static final int NPC_ARMACUS_POSTGUARDIAN = 16724, NPC_EVA_SWORD = 16725, NPC_IVY_NOOP = 16726, NPC_ALLIANCE_GUARDIAN_FIRST = 16718;
	/** The Nexus's Ocellus door (nexus_plan 4: closed every visit, open after stage 4). */
	public static final int LOC_NEXUS_OCELLUS_DOOR_CLOSED = 63110, LOC_NEXUS_OCELLUS_DOOR_OPEN = 63111;
	public static final int VAULT_RIFT_X = 31, VAULT_RIFT_Y = 13, VAULT_SMALL_RIFT_X = 30, VAULT_SMALL_RIFT_Y = 36;
	public static final int SEQ_SMALL_CHEST_OPEN = 15103; // human_tfa_small_chest_open
	public static final int GFX_LOOT_LIGHT = 4407, GFX_CHEST_SPARKLES = 4406;
	/** Unique reveal effects, {item, gfx}. */
	public static final int[][] GFX_LOOT_UNIQUE = { { 34727, 4408 }, { 34717, 4410 }, { 34679, 4412 }, { 34681, 4414 }, { 34719, 4416 } };
	public static final int VAULT_PLANE = 2, VAULT_ARRIVAL_X = 31, VAULT_ARRIVAL_Y = 15;
	/** The eight member chests, slot 1..8, all placed every run and each facing (31,40) (vault_plan 1, 2/2). */
	public static final int[][] VAULT_CHESTS = { { 28, 33 }, { 34, 33 }, { 25, 36 }, { 37, 36 }, { 24, 40 }, { 38, 40 }, { 25, 43 }, { 37, 43 } };
	public static final int VAULT_CHEST_FACE_X = 31, VAULT_CHEST_FACE_Y = 40;
	/** vault_plan 1: five tfa_butterfly01 wandering; A+9 two tfa_brazier_guthixloot01_fire01 (shape 22). */
	public static final int NPC_VAULT_BUTTERFLY = 16689, LOC_VAULT_BRAZIER = 62972, LOC_VAULT_BRAZIER_UNIQUE = 62973;
	public static final int[][] VAULT_BUTTERFLIES = { { 35, 35 }, { 34, 40 }, { 27, 34 }, { 31, 37 }, { 21, 47 } };
	public static final int[][] VAULT_BRAZIERS = { { 35, 45, 0 }, { 27, 45, 2 } };
	public static final int SEQ_VAULT_CHEST_OPEN = 15101; // chest_guthixian02_open01
	public static final int VAULT_BIG_CHEST_X = 30, VAULT_BIG_CHEST_Y = 47;
}

package io.ruin.model.inter.handlers.shopinterface;

import io.ruin.cache.ItemID;
import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.Interface;
import io.ruin.model.inter.ToplevelComponent;
import io.ruin.model.item.Item;
import io.ruin.model.shop.Currency;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public enum CustomShop2 {
	MELEE_STORE(
		1,
		Currency.COINS,
		new ShopItem[]{
			new ShopItem(1323, 44),
			new ShopItem(1363, 72),
			new ShopItem(1153, 61),
			new ShopItem(1115, 224),
			new ShopItem(1067, 112),
			new ShopItem(4121, 33),
			new ShopItem(1191, 95),
			new ShopItem(1175, 67),
			new ShopItem(1101, 84),
			new ShopItem(1081, 112),
			new ShopItem(1325, 160),
			new ShopItem(1365, 260),
			new ShopItem(1157, 220),
			new ShopItem(1119, 800),
			new ShopItem(1069, 400),
			new ShopItem(4123, 120),
			new ShopItem(1193, 340),
			new ShopItem(1177, 240),
			new ShopItem(1105, 300),
			new ShopItem(1083, 400),
			new ShopItem(1329, 416),
			new ShopItem(1369, 676),
			new ShopItem(1159, 572),
			new ShopItem(1121, 2080),
			new ShopItem(1071, 1040),
			new ShopItem(4127, 312),
			new ShopItem(1197, 884),
			new ShopItem(1181, 624),
			new ShopItem(1109, 780),
			new ShopItem(1085, 1040),
			new ShopItem(1331, 1024),
			new ShopItem(1371, 1664),
			new ShopItem(1161, 1408),
			new ShopItem(1123, 6656),
			new ShopItem(1073, 2560),
			new ShopItem(4129, 768),
			new ShopItem(1199, 2176),
			new ShopItem(1183, 1536),
			new ShopItem(1111, 1920),
			new ShopItem(1091, 2560),
			new ShopItem(1540, 1000)
		}
	),
	RANGED_STORE(
		2,
		Currency.COINS,
		new ShopItem[]{
			new ShopItem(884, 3),
			new ShopItem(886, 14),
			new ShopItem(888, 37),
			new ShopItem(890, 82),
			new ShopItem(841, 50),
			new ShopItem(843, 120),
			new ShopItem(849, 200),
			new ShopItem(853, 400),
			new ShopItem(9140, 50),
			new ShopItem(9141, 100),
			new ShopItem(9142, 150),
			new ShopItem(9177, 100),
			new ShopItem(9179, 300),
			new ShopItem(9181, 700),
			new ShopItem(9183, 2100),
			new ShopItem(863, 55),
			new ShopItem(865, 110),
			new ShopItem(807, 150),
			new ShopItem(808, 750),
			new ShopItem(809, 1500),
			new ShopItem(1167, 9),
			new ShopItem(1129, 8),
			new ShopItem(1095, 8),
			new ShopItem(1063, 7),
			new ShopItem(1169, 80),
			new ShopItem(1133, 340),
			new ShopItem(1097, 300),
			new ShopItem(1135, 3120),
			new ShopItem(1099, 1560),
			new ShopItem(1065, 1000)
		}
	),
	MAGIC_STORE(
		3,
		Currency.COINS,
		new ShopItem[]{
			new ShopItem(558, 85),
			new ShopItem(556, 40),
			new ShopItem(557, 40),
			new ShopItem(559, 180),
			new ShopItem(555, 40),
			new ShopItem(554, 40),
			new ShopItem(562, 350),
			new ShopItem(560, 540),
			new ShopItem(565, 850),
			new ShopItem(561, 770),
			new ShopItem(563, 500),
			new ShopItem(564, 400),
			new ShopItem(9075, 500),
			new ShopItem(566, 300),
			new ShopItem(1381, 1500),
			new ShopItem(1383, 1500),
			new ShopItem(1385, 1500),
			new ShopItem(1387, 1500),
			new ShopItem(1391, 6820),
			new ShopItem(4675, 78000),
			new ShopItem(2415, 75000),
			new ShopItem(2416, 75000),
			new ShopItem(2417, 75000),
			new ShopItem(6109, 750),
			new ShopItem(6107, 750),
			new ShopItem(6108, 750),
			new ShopItem(6110, 750),
			new ShopItem(6106, 750),
			new ShopItem(6111, 450)
		}
	),
	IRONMAN_SHOP(
		4,
		Currency.COINS,
		new ShopItem[]{
			new ShopItem(314, 1),
			new ShopItem(590, 1),
			new ShopItem(2347, 1),
			new ShopItem(946, 1),
			new ShopItem(1755, 1),
			new ShopItem(1735, 1),
			new ShopItem(1733, 1),
			new ShopItem(1734, 3),
			new ShopItem(1785, 5),
			new ShopItem(1592, 10),
			new ShopItem(1595, 10),
			new ShopItem(1597, 10),
			new ShopItem(11065, 10),
			new ShopItem(5523, 175),
			new ShopItem(1267, 245),
			new ShopItem(1269, 2275),
			new ShopItem(1271, 5050),
			new ShopItem(1275, 32000),
			new ShopItem(1349, 98),
			new ShopItem(1353, 325),
			new ShopItem(1355, 900),
			new ShopItem(1357, 2260),
			new ShopItem(1359, 21000),

			new ShopItem(303, 1),
			new ShopItem(309, 1),
			new ShopItem(307, 1),
			new ShopItem(301, 1),
			new ShopItem(311, 1),

			new ShopItem(1540, 1000),
			new ShopItem(952, 1),
			new ShopItem(5341, 3),
			new ShopItem(5343, 3),
			new ShopItem(5325, 3),
			new ShopItem(5329, 3),

			new ShopItem(10006, 1),
			new ShopItem(10008, 1),
			new ShopItem(10012, 0),
			new ShopItem(11260, 0),
			new ShopItem(10010, 12),
			new ShopItem(954, 10),
			new ShopItem(227, 1),
			new ShopItem(233, 2),
			new ShopItem(2446, 216),
			new ShopItem(9419, 1),
			new ShopItem(10499, 600),
			new ShopItem(544, 52),
			new ShopItem(542, 39),
			new ShopItem(3105, 7),
			new ShopItem(8880, 1200),
			new ShopItem(8882, 1),
			new ShopItem(9672, 4800),
			new ShopItem(9674, 7200),
			new ShopItem(9676, 6000)
		}
	),
	PVM_POINT_SHOP(
		5,
		Currency.PVM_POINTS,
		new ShopItem[]{
			new ShopItem(10548, 275),
			new ShopItem(10547, 275),
			new ShopItem(10549, 275),
			new ShopItem(10550, 275),
			new ShopItem(10551, 375),
			new ShopItem(10555, 375),
			new ShopItem(10552, 100),
			new ShopItem(10553, 150),
			new ShopItem(7453, 25),
			new ShopItem(7454, 50),
			new ShopItem(7455, 75),
			new ShopItem(7456, 100),
			new ShopItem(7457, 125),
			new ShopItem(7458, 150),
			new ShopItem(7459, 175),
			new ShopItem(7460, 200),
			new ShopItem(7461, 225),
			new ShopItem(7462, 250),
			new ShopItem(3840, 250),
			new ShopItem(3842, 250),
			new ShopItem(3844, 250),
			new ShopItem(12608, 500),
			new ShopItem(12610, 500),
			new ShopItem(12612, 500),
			new ShopItem(12863, 1250),
			new ShopItem(1409, 500),
			new ShopItem(4081, 20),
			new ShopItem(30427, 10),
			new ShopItem(30104, 25),
			new ShopItem(23071, 75),
			new ShopItem(22114, 500),
			new ShopItem(7510, 500),
			new ShopItem(6758, 750),
			new ShopItem(11738, 5)
		}
	),
	// Switched from Currency.BLOOD_MONEY to Currency.BH_POINTS per user request -- BH points
	// were being earned (BountyHunter.deathByTarget()) but had no shop to spend them in.
	// Dropped the old self-referential "buy 1 Blood Money ticket for 1 Blood Money" entry
	// (item 59601) since it no longer makes sense once the shop's own currency changed.
	BLOOD_MONEY_STORE(
		6,
		Currency.BH_POINTS,
		new ShopItem[]{
			// Line 1
			new ShopItem(26219, 30000), // Osmumten's fang
			new ShopItem(21003, 20000), // Elder maul
			new ShopItem(22324, 15000), // Ghrazi rapier
			new ShopItem(21006, 10000), // Kodai wand
			new ShopItem(19564, 5000), // Royal seed pod
			new ShopItem(21012, 6000), // Dragon hunter crossbow
			new ShopItem(24553, 12000), // Blade of saeldor (c)
			new ShopItem(30634, 3000), // Twinflame staff
			new ShopItem(25985, 25000), // Elidinis' ward
			new ShopItem(24514, 30000), // Volatile orb
			// Line 2
			new ShopItem(30756, 50000), // Oathplate legs
			new ShopItem(30753, 50000), // Oathplate chest
			new ShopItem(30750, 30000), // Oathplate helm
			new ShopItem(21024, 30000), // Ancestral robe bottom
			new ShopItem(21021, 30000), // Ancestral robe top
			new ShopItem(21018, 15000), // Ancestral hat
			new ShopItem(29796, 80000), // Noxious halberd
			new ShopItem(25894, 50000), // Bow of faerdhinen (c)
			new ShopItem(29801, 50000), // Amulet of rancour
			new ShopItem(24511, 30000), // Harmonised orb
			// Line 3
			new ShopItem(27232, 12000), // Masori chaps
			new ShopItem(27229, 12000), // Masori body
			new ShopItem(27226, 5000), // Masori mask
			new ShopItem(22328, 12000), // Justiciar legguards
			new ShopItem(22327, 12000), // Justiciar chestguard
			new ShopItem(22326, 5000), // Justiciar faceguard
			new ShopItem(24421, 5000), // Inquisitor's plateskirt
			new ShopItem(24420, 5000), // Inquisitor's hauberk
			new ShopItem(24419, 2500), // Inquisitor's great helm
			new ShopItem(24517, 3000), // Eldritch orb
			// Line 4
			new ShopItem(6199, 1000), // Mystery box
			new ShopItem(30614, 10000), // Ali's cape
			new ShopItem(59601, 1) // BH Ticket
		}
	),
	MISC_STORE(
		7,
		Currency.COINS,
		new ShopItem[]{
			new ShopItem(2446, 340),
			new ShopItem(2428, 100),
			new ShopItem(113, 100),
			new ShopItem(2432, 100),
			new ShopItem(4417, 2250),
			new ShopItem(333, 49),
			new ShopItem(365, 414),
			new ShopItem(379, 287),
			new ShopItem(373, 596),
			new ShopItem(227, 8),
			new ShopItem(8013, 1237)

		}
	),
	SKILLING_SUPPLIES(
		8,
		Currency.COINS,
		new ShopItem[]{
			new ShopItem(1931, 1),
			new ShopItem(1935, 1),
			new ShopItem(20742, 210),
			new ShopItem(1925, 3),
			new ShopItem(1923, 6),
			new ShopItem(1887, 15),
			new ShopItem(314, 36),
			new ShopItem(590, 213),
			new ShopItem(2347, 110),
			new ShopItem(946, 34),
			new ShopItem(1755, 86),
			new ShopItem(1735, 27),
			new ShopItem(1733, 100),
			new ShopItem(1734, 9),
			new ShopItem(1785, 42),
			new ShopItem(1592, 10),
			new ShopItem(1595, 10),
			new ShopItem(1597, 10),
			new ShopItem(11065, 511),
			new ShopItem(5523, 175),
			new ShopItem(1267, 245),
			new ShopItem(1269, 2275),
			new ShopItem(1271, 5050),
			new ShopItem(1349, 98),
			new ShopItem(1353, 325),
			new ShopItem(1355, 900),
			new ShopItem(1357, 2260),
			new ShopItem(952, 1)
		}
	),
	DONATOR_STORE(
		9,
		Currency.DONATOR,
		new ShopItem[]{
			// Line 1
			new ShopItem(11862, 10000),  // Black partyhat
			new ShopItem(11847, 7500),   // Black h'ween mask
			new ShopItem(13343, 7500),   // Black santa hat
			new ShopItem(30469, 5000),   // Black bunny ears
			new ShopItem(1037, 2500),    // Bunny ears
			new ShopItem(21859, 7000),   // Wise old man's santa hat
			new ShopItem(11863, 5000),   // Rainbow partyhat
			new ShopItem(30601, 6000),   // Pink santa hat (substitute -- "Pink partyhat" doesn't exist)
			new ShopItem(13344, 6000),   // Inverted santa hat
			new ShopItem(30461, 750),    // Donator mystery box

			// Line 2
			new ShopItem(30221, 8000),   // Summer partyhat
			new ShopItem(30227, 8000),   // Summer halloween mask
			new ShopItem(30224, 8000),   // Summer santa hat
			new ShopItem(59575, 7000),   // Fallen angel wings
			new ShopItem(59574, 7000),   // Eagle wings
			new ShopItem(59573, 7000),   // Angel wings
			new ShopItem(59550, 5000),   // Infernal platelegs
			new ShopItem(59549, 5000),   // Infernal platebody
			new ShopItem(59551, 3500),   // Infernal wings
			new ShopItem(30462, 1100),   // Advanced donator mystery box

			// Line 3
			new ShopItem(1038, 300),     // Red partyhat
			new ShopItem(1042, 300),     // Blue partyhat
			new ShopItem(1044, 300),     // Green partyhat
			new ShopItem(1046, 300),     // Purple partyhat
			new ShopItem(1040, 300),     // Yellow partyhat
			new ShopItem(1048, 300),     // White partyhat
			new ShopItem(1057, 250),     // Red halloween mask
			new ShopItem(1055, 250),     // Blue halloween mask
			new ShopItem(1053, 250),     // Green halloween mask
			new ShopItem(1050, 250),     // Santa hat

			// Line 4
			new ShopItem(59546, 4000),   // Hellfire platelegs
			new ShopItem(59545, 4000),   // Hellfire platebody
			new ShopItem(59544, 3000),   // Hellfire fullhelm
			new ShopItem(59543, 1500),   // Hellfire boots
			new ShopItem(59547, 2500),   // Hellfire wings
			new ShopItem(59537, 5000),   // Fleshrender sword
			new ShopItem(11824, 1200),   // Zamorakian spear
			new ShopItem(11791, 1000),   // Staff of the dead
			new ShopItem(11785, 2500),   // Armadyl crossbow
			new ShopItem(4810, 2000),    // Donator mystery bag

			// Line 5
			new ShopItem(59607, 3500),   // Imperial robe
			new ShopItem(59606, 3500),   // Imperial top
			new ShopItem(59608, 2500),   // Imperial boots
			new ShopItem(59605, 10000),  // Imperial bow
			new ShopItem(59604, 6000),   // Imperial staff
			new ShopItem(59615, 4000),   // Frostwood platelegs
			new ShopItem(59614, 4000),   // Frostwood platebody
			new ShopItem(59613, 2500),   // Frostwood helm
			new ShopItem(59548, 5000),   // Icenier sword
			new ShopItem(30446, 3500),   // Donator mystery chest

			// Line 6
			new ShopItem(20997, 35000),  // Twisted bow
			new ShopItem(22325, 30000),  // Scythe of vitur
			new ShopItem(27275, 35000),  // Tumeken's shadow
			new ShopItem(59626, 50000),  // Nightfall bow
			new ShopItem(59568, 25000),  // Tempest bow
			new ShopItem(24391, 5000),   // Twisted trousers (t3)
			new ShopItem(24389, 5000),   // Twisted coat (t3)
			new ShopItem(24387, 4000),   // Twisted hat (t3)
			new ShopItem(24393, 2500),   // Twisted boots (t3)
			new ShopItem(30449, 4800),   // Advanced donator mystery chest

			// Line 7
			new ShopItem(12415, 50),     // Dragon platelegs (g)
			new ShopItem(22242, 50),     // Dragon platebody (g)
			new ShopItem(12417, 50),     // Dragon full helm (g)
			new ShopItem(22234, 40),     // Dragon boots (g)
			new ShopItem(22244, 40),     // Dragon kiteshield (g)
			new ShopItem(12416, 40),     // Dragon plateskirt (g)
			new ShopItem(12414, 40),     // Dragon chainbody (g)
			new ShopItem(12418, 40),     // Dragon sq shield (g)
			new ShopItem(20000, 50),     // Dragon scimitar (or)
			new ShopItem(19941, 100),    // Heavy casket

			// Line 8
			new ShopItem(19964, 200),    // Dark trousers
			new ShopItem(19958, 200),    // Dark tuxedo jacket
			new ShopItem(19970, 100),    // Dark bow tie
			new ShopItem(19967, 50),     // Dark tuxedo shoes
			new ShopItem(19961, 50),     // Dark tuxedo cuffs
			new ShopItem(19979, 200),    // Light trousers
			new ShopItem(19973, 200),    // Light tuxedo jacket
			new ShopItem(19985, 100),    // Light bow tie
			new ShopItem(19982, 50),     // Light tuxedo shoes
			new ShopItem(19976, 50),     // Light tuxedo cuffs

			// Line 9
			new ShopItem(23306, 30),     // Monk's robe (t)
			new ShopItem(23303, 30),     // Monk's robe top (t)
			new ShopItem(20240, 20),     // Crier coat
			new ShopItem(12319, 20),     // Crier hat
			new ShopItem(2528, 500),     // Donator lamp
			new ShopItem(33020, 750),    // Perk exp lamp
			new ShopItem(59600, 1),      // Donator ticket
			new ShopItem(59980, 5000),   // Verdant pet
			new ShopItem(59977, 7000),   // Frostbane pet
			new ShopItem(59972, 10000)   // Starfall pet
		}
	),
	VOTE_STORE(
		10,
		Currency.VOTE,
		new ShopItem[]{
			// Line 1
			new ShopItem(27690, 1000),  // Voidwaker
			new ShopItem(59559, 750),   // MP7A2 gun
			new ShopItem(59531, 750),   // AsVal gun
			new ShopItem(59541, 300),   // Frostbound platelegs
			new ShopItem(59540, 300),   // Frostbound platebody
			new ShopItem(59562, 250),   // Revenant shardbow
			new ShopItem(59612, 90),    // Crimson trousers
			new ShopItem(59611, 90),    // Crimson tailcoat
			new ShopItem(59609, 75),    // Crimson hat
			new ShopItem(59610, 50),    // Crimson staff

			// Line 2
			new ShopItem(12399, 500),   // Partyhat & specs
			new ShopItem(59556, 450),   // Judicator platelegs
			new ShopItem(59555, 450),   // Judicator platebody
			new ShopItem(59557, 300),   // Judicator sword
			new ShopItem(59552, 300),   // Judicator axe
			new ShopItem(59542, 200),   // Glacuis twinblade
			new ShopItem(59596, 100),   // Voltstrike boots
			new ShopItem(22114, 50),    // Mythical cape
			new ShopItem(22322, 400),   // Avernic defender
			new ShopItem(22981, 450),   // Ferocious gloves

			// Line 3
			new ShopItem(10346, 500),   // 3rd age platelegs
			new ShopItem(23242, 500),   // 3rd age plateskirt
			new ShopItem(10348, 500),   // 3rd age platebody
			new ShopItem(10350, 250),   // 3rd age full helm
			new ShopItem(10352, 200),   // 3rd age kiteshield
			new ShopItem(12437, 700),   // 3rd age cloak
			new ShopItem(10340, 500),   // 3rd age robe
			new ShopItem(10338, 600),   // 3rd age robe top
			new ShopItem(10342, 250),   // 3rd age mage hat
			new ShopItem(12422, 700),   // 3rd age wand

			// Line 4
			new ShopItem(10332, 500),   // 3rd age range legs
			new ShopItem(10330, 500),   // 3rd age range top
			new ShopItem(10334, 250),   // 3rd age range coif
			new ShopItem(12424, 700),   // 3rd age bow
			new ShopItem(10336, 200),   // 3rd age vambraces
			new ShopItem(23339, 700),   // 3rd age druidic robe bottoms
			new ShopItem(23336, 700),   // 3rd age druidic robe top
			new ShopItem(23342, 650),   // 3rd age druidic staff
			new ShopItem(12426, 600),   // 3rd age longsword
			new ShopItem(23345, 700),   // 3rd age druidic cloak

			// Line 5
			new ShopItem(20014, 1000),  // 3rd age pickaxe
			new ShopItem(20011, 900),   // 3rd age axe
			new ShopItem(13073, 200),   // Elite void robe
			new ShopItem(13072, 200),   // Elite void top
			new ShopItem(27281, 100),   // Divine rune pouch
			new ShopItem(19473, 2),     // Bag full of gems
			new ShopItem(59982, 150),   // Prismcore pet
			new ShopItem(59965, 75),    // Cinder pet
			new ShopItem(59966, 75),    // Glacian pet
			new ShopItem(7478, 2),      // Instance token
			new ShopItem(11942, 2),     // Ecumenical key
			new ShopItem(7968, 2),      // Perk task skip scroll
			new ShopItem(11738, 1),     // Herb box

			// Line 6
			new ShopItem(30046, 100),   // Primordial boots ornament kit
			new ShopItem(30501, 100),   // Pegasian boots ornament kit
			new ShopItem(30500, 100),   // Eternal boots ornament kit
			new ShopItem(20068, 30),    // Armadyl godsword ornament kit
			new ShopItem(20071, 25),    // Bandos godsword ornament kit
			new ShopItem(20074, 25),    // Saradomin godsword ornament kit
			new ShopItem(20077, 25),    // Zamorak godsword ornament kit
			new ShopItem(22236, 25),    // Dragon platebody ornament kit
			new ShopItem(22239, 25),    // Dragon kiteshield ornament kit
			new ShopItem(26707, 30),    // Dragon claws ornament kit

			// Line 7
			new ShopItem(26709, 25),    // Dragon warhammer ornament kit
			new ShopItem(26711, 25),    // Heavy ballista ornament kit
			new ShopItem(26713, 25),    // Armadyl armour ornament kit
			new ShopItem(26717, 25),    // Bandos armour ornament kit
			new ShopItem(27098, 30),    // Elder maul ornament kit
			new ShopItem(27113, 30),    // Elder chaos robes ornament kit
			new ShopItem(27121, 30),    // Dagon'hai robes ornament kit
			new ShopItem(12526, 15),    // Fury ornament kit
			new ShopItem(20062, 20),    // Torture ornament kit
			new ShopItem(22246, 20),    // Anguish ornament kit

			// Line 8
			new ShopItem(23348, 20),    // Tormented ornament kit
			new ShopItem(20065, 20),    // Occult ornament kit
			new ShopItem(22231, 7),     // Dragon boots ornament kit
			new ShopItem(20002, 7),     // Dragon scimitar ornament kit
			new ShopItem(20143, 10),    // Dragon defender ornament kit
			new ShopItem(23227, 5),     // Rune defender ornament kit
			new ShopItem(12536, 7),     // Dragon legs/skirt ornament kit
			new ShopItem(12534, 5),     // Dragon chainbody ornament kit
			new ShopItem(12532, 5),     // Dragon sq shield ornament kit
			new ShopItem(12538, 5),     // Dragon full helm ornament kit

			// Line 9
			new ShopItem(24670, 50),    // Twisted ancestral colour kit
			new ShopItem(25742, 40),    // Holy ornament kit
			new ShopItem(25744, 40),    // Sanguine ornament kit
			new ShopItem(10600, 15),     // Scratch Card
			new ShopItem(59602, 1)      // Vote ticket
		}
	),

	IDK_SHOP(
		12,
		Currency.COINS,
		new ShopItem[]{
			new ShopItem(314, 1),
			new ShopItem(590, 1),
			new ShopItem(2347, 1),
			new ShopItem(946, 1),
			new ShopItem(1755, 1),
			new ShopItem(1735, 1),
			new ShopItem(1733, 1),
			new ShopItem(1734, 3),
			new ShopItem(1785, 5),
			new ShopItem(1592, 10),
			new ShopItem(1595, 10),
			new ShopItem(1597, 10),
			new ShopItem(11065, 10),
			new ShopItem(5523, 175),
			new ShopItem(1267, 245),
			new ShopItem(1269, 2275),
			new ShopItem(1271, 5050),
			new ShopItem(1275, 32000),
			new ShopItem(1349, 98),
			new ShopItem(1353, 325),
			new ShopItem(1355, 900),
			new ShopItem(1357, 2260),
			new ShopItem(1359, 21000),

			new ShopItem(303, 1),
			new ShopItem(309, 1),
			new ShopItem(307, 1),
			new ShopItem(301, 1),
			new ShopItem(311, 1),

			new ShopItem(1540, 1000),
			new ShopItem(952, 1),
			new ShopItem(5341, 3),
			new ShopItem(5343, 3),
			new ShopItem(5325, 3),
			new ShopItem(5329, 3),

			new ShopItem(10006, 1),
			new ShopItem(10008, 1),
			new ShopItem(10012, 0),
			new ShopItem(11260, 0),
			new ShopItem(10010, 12),
			new ShopItem(954, 10),
			new ShopItem(227, 1),
			new ShopItem(233, 2),
			new ShopItem(2446, 216),
			new ShopItem(9419, 1),
			new ShopItem(10499, 600),
			new ShopItem(544, 52),
			new ShopItem(542, 39),
			new ShopItem(3105, 7),
			new ShopItem(8880, 1200),
			new ShopItem(8882, 1),
			new ShopItem(9672, 4800),
			new ShopItem(9674, 7200),
			new ShopItem(9676, 6000)
		}
	),

	PKP_SHOP(
		13,
		Currency.PK,
		new ShopItem[]{
			// Line 1
			new ShopItem(11802, 7000),   // Armadyl godsword
			new ShopItem(11804, 2500),   // Bandos godsword
			new ShopItem(11806, 2500),   // Saradomin godsword
			new ShopItem(11808, 2500),   // Zamorak godsword
			new ShopItem(13652, 20000),  // Dragon claws
			new ShopItem(24422, 3000),   // Nightmare staff
			new ShopItem(13265, 2000),   // Abyssal dagger
			new ShopItem(12954, 1000),   // Dragon defender
			new ShopItem(11889, 1500),   // Zamorakian hasta
			new ShopItem(31097, 90000),  // Avernic treads (max)

			// Line 2
			new ShopItem(11834, 5000),   // Bandos tassets
			new ShopItem(11832, 5000),   // Bandos chestplate
			new ShopItem(11836, 100),    // Bandos boots
			new ShopItem(12006, 2500),   // Abyssal tentacle
			new ShopItem(6570, 1500),    // Fire cape
			new ShopItem(10551, 1000),   // Fighter torso
			new ShopItem(10548, 300),    // Fighter hat
			new ShopItem(12791, 800),    // Rune pouch
			new ShopItem(30955, 3000),   // Arkan blade
			new ShopItem(13239, 5000),   // Primordial boots

			// Line 3
			new ShopItem(11830, 5000),   // Armadyl chainskirt
			new ShopItem(11828, 5000),   // Armadyl chestplate
			new ShopItem(11826, 2000),   // Armadyl helmet
			new ShopItem(11785, 4000),   // Armadyl crossbow
			new ShopItem(21902, 2500),   // Dragon crossbow
			new ShopItem(22109, 1500),   // Ava's assembler
			new ShopItem(11926, 2000),   // Odium ward
			new ShopItem(11235, 1500),   // Dark bow
			new ShopItem(21946, 1),      // Diamond dragon bolts (e)
			new ShopItem(13237, 5000),   // Pegasian boots

			// Line 4
			new ShopItem(29025, 5000),   // Blood moon tassets
			new ShopItem(29022, 5000),   // Blood moon chestplate
			new ShopItem(29028, 2000),   // Blood moon helm
			new ShopItem(28997, 5000),   // Dual macuahuitl (Blood moon macuahuitl)
			new ShopItem(4087, 50),      // Dragon platelegs
			new ShopItem(21892, 50),     // Dragon platebody
			new ShopItem(11335, 50),     // Dragon full helm
			new ShopItem(11840, 150),    // Dragon boots
			new ShopItem(13263, 2500),   // Abyssal bludgeon
			new ShopItem(13235, 5000),   // Eternal boots

			// Line 5
			new ShopItem(29016, 5000),   // Blue moon tassets
			new ShopItem(29013, 5000),   // Blue moon chestplate
			new ShopItem(29019, 2000),   // Blue moon helm
			new ShopItem(28988, 5000),   // Blue moon spear
			new ShopItem(4585, 40),      // Dragon plateskirt
			new ShopItem(2513, 40),      // Dragon chainbody
			new ShopItem(1187, 30),      // Dragon sq shield
			new ShopItem(1249, 200),     // Dragon spear
			new ShopItem(10887, 80),     // Barrelchest anchor
			new ShopItem(22951, 200),    // Boots of brimstone

			// Line 6
			new ShopItem(12877, 1200),   // Dharok's armour set
			new ShopItem(4722, 400),     // Dharok's platelegs
			new ShopItem(4720, 400),     // Dharok's platebody
			new ShopItem(4716, 200),     // Dharok's helm
			new ShopItem(4718, 200),     // Dharok's greataxe
			new ShopItem(12879, 1000),   // Torag's armour set
			new ShopItem(4751, 350),     // Torag's platelegs
			new ShopItem(4749, 350),     // Torag's platebody
			new ShopItem(4745, 150),     // Torag's helm
			new ShopItem(4747, 150),     // Torag's hammers

			// Line 7
			new ShopItem(12881, 1000),   // Ahrim's armour set
			new ShopItem(4714, 350),     // Ahrim's robeskirt
			new ShopItem(4712, 350),     // Ahrim's robetop
			new ShopItem(4708, 150),     // Ahrim's hood
			new ShopItem(4710, 150),     // Ahrim's staff
			new ShopItem(12873, 1000),   // Guthan's armour set
			new ShopItem(4730, 350),     // Guthan's chainskirt
			new ShopItem(4728, 350),     // Guthan's platebody
			new ShopItem(4724, 150),     // Guthan's helm
			new ShopItem(4726, 150),     // Guthan's warspear

			// Line 8
			new ShopItem(12883, 1000),   // Karil's armour set
			new ShopItem(4738, 350),     // Karil's leatherskirt
			new ShopItem(4736, 350),     // Karil's leathertop
			new ShopItem(4732, 150),     // Karil's coif
			new ShopItem(4734, 150),     // Karil's crossbow
			new ShopItem(12875, 1000),   // Verac's armour set
			new ShopItem(4759, 350),     // Verac's plateskirt
			new ShopItem(4757, 350),     // Verac's brassard
			new ShopItem(4753, 350),     // Verac's helm
			new ShopItem(4755, 350),     // Verac's flail

			// Line 9
			new ShopItem(25975, 12000),  // Lightbearer
			new ShopItem(11773, 1200),   // Berserker ring (i)
			new ShopItem(11770, 1200),   // Seers ring (i)
			new ShopItem(11771, 700),    // Archers ring (i)
			new ShopItem(11772, 700),    // Warrior ring (i)
			new ShopItem(19710, 8000),   // Ring of suffering (i)
			new ShopItem(6585, 1000),    // Amulet of fury
			new ShopItem(19553, 12000),  // Amulet of torture
			new ShopItem(19547, 12000),  // Necklace of anguish
			new ShopItem(22081, 1000),   // Locator orb

			// Line 10
			new ShopItem(23685, 10),     // Divine super combat potion(4)
			new ShopItem(12695, 5),      // Super combat potion(4)
			new ShopItem(23733, 7),      // Divine super range potion(4)
			new ShopItem(11722, 3),      // Super range potion(4)
			new ShopItem(13441, 2),      // Anglerfish
			new ShopItem(3144, 1),       // Cooked karambwan
			new ShopItem(59599, 1)       // PKP ticket
		}
	),
	// Mirrors the old, separate Zelus Point Store's item list/prices exactly -- see
	// io.ruin.model.activities.newshop.shops.ReasonPointStore ("Reason" = old server name,
	// same currency as Currency.REASON / "Zelus points"). Per user request, these items were
	// dropped from the copy: Elite void robe/top, Fighter hat/torso, Dragon defender, Ava's
	// assembler, Torag's/Karil's/Verac's/Ahrim's/Dharok's armour sets, Cooked karambwan.
	ZELUS_POINTS_SHOP(
		14,
		Currency.REASON,
		new ShopItem[]{

			// Equipment
			new ShopItem(8840, 60000),    // Void knight robe
			new ShopItem(8839, 60000),    // Void knight top
			new ShopItem(11664, 35000),   // Void ranger helm
			new ShopItem(11663, 35000),   // Void mage helm
			new ShopItem(11665, 35000),   // Void melee helm
			new ShopItem(8842, 45000),    // Void knight gloves
			new ShopItem(21793, 250000),  // Imbued guthix cape
			new ShopItem(21795, 250000),  // Imbued zamorak cape
			new ShopItem(21791, 250000),  // Imbued saradomin cape
			new ShopItem(30430, 1000000), // Ultra mystery box

			new ShopItem(6924, 60000),    // Infinity bottoms
			new ShopItem(6918, 35000),    // Infinity hat
			new ShopItem(6916, 60000),    // Infinity top
			new ShopItem(6920, 40000),    // Infinity boots
			new ShopItem(6922, 30000),    // Infinity gloves
			new ShopItem(6889, 70000),    // Mage's book
			new ShopItem(12610, 25000),   // Book of law
			new ShopItem(12612, 25000),   // Book of darkness
			new ShopItem(3842, 25000),    // Unholy book
			new ShopItem(12608, 25000),   // Book of war

			new ShopItem(26854, 30000), // Robe bottoms of the eye
			new ShopItem(26852, 30000), // Robe top of the eye
			new ShopItem(26850, 25000), // Hat of the eye
			new ShopItem(26856, 25000), // boots of the eye
			new ShopItem(775, 20000),    // Cooking gauntlets
			new ShopItem(776, 20000),    // Goldsmith gauntlets
			new ShopItem(1580, 20000),   // Ice gloves
			new ShopItem(10550, 35000),   // Ranger hat
			new ShopItem(10547, 35000),   // Healer hat
			new ShopItem(8850, 30000),    // Rune defender
			new ShopItem(19677, 5000),    // Ancient shard

			new ShopItem(13260, 30000),  // Angler waders
			new ShopItem(13259, 30000),  // Angler top
			new ShopItem(13258, 25000),  // Angler hat
			new ShopItem(13261, 25000),  // Angler boots
			new ShopItem(10588, 75000),   // Salve amulet(e)
			new ShopItem(12018, 200000),  // Salve amulet(ei)
			new ShopItem(20716, 250000),  // Tome of fire (empty)
			new ShopItem(20718, 15000),   // Burnt page
			new ShopItem(25576, 250000),  // Tome of water (empty)
			new ShopItem(25578, 15000),   // Elemental shield / tome-adjacent item

			new ShopItem(11941, 100000),  // Looting bag
			new ShopItem(12019, 50000),   // Coal bag
			new ShopItem(12020, 50000),   // Gem bag
			new ShopItem(26784, 75000),    // Colossal pouch
			new ShopItem(5514, 50000),    // Giant pouch
			new ShopItem(5512, 25000),    // Large pouch
			new ShopItem(5510, 17500),    // Medium pouch
			new ShopItem(5509, 10000),    // Small pouch
			new ShopItem(19677, 5000),    // Ancient shard
			new ShopItem(19685, 250000),  // Dark totem


			// Consumables
			new ShopItem(12695, 100),   // Super combat potion(4)
			new ShopItem(2436, 50),     // Super attack(4)
			new ShopItem(2440, 50),     // Super strength(4)
			new ShopItem(2442, 50),     // Super defence(4)
			new ShopItem(2444, 100),    // Ranging potion(4)
			new ShopItem(3040, 100),    // Magic potion(4)
			new ShopItem(6685, 100),    // Saradomin brew(4)
			new ShopItem(10925, 150),   // Sanfew serum(4)
			new ShopItem(3024, 125),    // Super restore(4)
			new ShopItem(2434, 125),    // Prayer potion(4)
			new ShopItem(12625, 125),   // Stamina potion(4)
			new ShopItem(2452, 75),     // Antifire potion(4)
			new ShopItem(21978, 125),   // Super antifire potion(4)
			new ShopItem(5952, 100),    // Antidote+(4)
			new ShopItem(12913, 175),   // Anti-venom+(4)
			new ShopItem(6914, 120000),   // Master wand
			new ShopItem(7462, 50000),    // Barrows gloves
			new ShopItem(19675, 100000),  // Arclight
			new ShopItem(1409, 35000),    // Iban's staff
			new ShopItem(385, 100),     // Shark
			new ShopItem(7946, 75),     // Monkfish

			// Skilling
			new ShopItem(22875, 10000),  // Hespori seed
			new ShopItem(7409, 20000),   // Magic secateurs
			new ShopItem(9625, 20000),   // Crystal saw
			new ShopItem(1585, 5000),    // Oily fishing rod

			// Utility
			new ShopItem(12863, 250000),  // Dwarf cannon set
			new ShopItem(2, 25),          // Cannonball
			new ShopItem(21726, 50),      // Granite dust
			new ShopItem(4, 30000),       // Ammo mould
			new ShopItem(27012, 100000),
			new ShopItem(19564, 50000),
			new ShopItem(26706, 100000),



		}
	),
	// Mirrors the old, separate Achievement Point Store's item list/prices exactly -- see
	// io.ruin.model.activities.newshop.shops.AchievementPointStore, which has 2 categories:
	// achievement-diary items (48) and equipment (29), 77 items total.
	ACHIEVEMENT_POINTS_SHOP(
		15,
		Currency.ACHIEVEMENT,
		new ShopItem[]{
			// Achievement diary items -- tier 1 (cost 3)
			new ShopItem(13137, 3),   // Kandarin headgear 1
			new ShopItem(13104, 3),   // Varrock armour 1
			new ShopItem(13112, 3),   // Morytania legs 1
			new ShopItem(13117, 3),   // Falador shield 1
			new ShopItem(11136, 3),   // Karamja gloves 1
			new ShopItem(13129, 3),   // Fremennik sea boots 1
			new ShopItem(13125, 3),   // Explorer's ring 1
			new ShopItem(22941, 3),   // Rada's blessing 1
			new ShopItem(13133, 3),   // Desert amulet 1
			new ShopItem(13121, 3),   // Ardougne cloak 1
			new ShopItem(13108, 3),   // Wilderness sword 1
			new ShopItem(13141, 3),   // Western banner 1

			// tier 2 (cost 5)
			new ShopItem(13138, 5),   // Kandarin headgear 2
			new ShopItem(13105, 5),   // Varrock armour 2
			new ShopItem(13113, 5),   // Morytania legs 2
			new ShopItem(13118, 5),   // Falador shield 2
			new ShopItem(11138, 5),   // Karamja gloves 2
			new ShopItem(13130, 5),   // Fremennik sea boots 2
			new ShopItem(13126, 5),   // Explorer's ring 2
			new ShopItem(22943, 5),   // Rada's blessing 2
			new ShopItem(13134, 5),   // Desert amulet 2
			new ShopItem(13122, 5),   // Ardougne cloak 2
			new ShopItem(13109, 5),   // Wilderness sword 2
			new ShopItem(13142, 5),   // Western banner 2

			// tier 3 (cost 7)
			new ShopItem(13139, 7),   // Kandarin headgear 3
			new ShopItem(13106, 7),   // Varrock armour 3
			new ShopItem(13114, 7),   // Morytania legs 3
			new ShopItem(13119, 7),   // Falador shield 3
			new ShopItem(11140, 7),   // Karamja gloves 3
			new ShopItem(13131, 7),   // Fremennik sea boots 3
			new ShopItem(13127, 7),   // Explorer's ring 3
			new ShopItem(22945, 7),   // Rada's blessing 3
			new ShopItem(13135, 7),   // Desert amulet 3
			new ShopItem(13123, 7),   // Ardougne cloak 3
			new ShopItem(13110, 7),   // Wilderness sword 3
			new ShopItem(13143, 7),   // Western banner 3

			// tier 4 (cost 8)
			new ShopItem(13140, 8),   // Kandarin headgear 4
			new ShopItem(13107, 8),   // Varrock armour 4
			new ShopItem(13115, 8),   // Morytania legs 4
			new ShopItem(13120, 8),   // Falador shield 4
			new ShopItem(13103, 8),   // Karamja gloves 4
			new ShopItem(13132, 8),   // Fremennik sea boots 4
			new ShopItem(13128, 8),   // Explorer's ring 4
			new ShopItem(22947, 8),   // Rada's blessing 4
			new ShopItem(13136, 8),   // Desert amulet 4
			new ShopItem(13124, 8),   // Ardougne cloak 4
			new ShopItem(13111, 8),   // Wilderness sword 4
			new ShopItem(13144, 8),   // Western banner 4

			// Equipment
			new ShopItem(23987, 12),  // Crystal halberd
			new ShopItem(11061, 8),   // Ancient mace
			new ShopItem(25644, 7),   // Zombie axe (wielded hammer)
			new ShopItem(7458, 3),    // Mithril gloves
			new ShopItem(7459, 6),    // Adamant gloves
			new ShopItem(7460, 9),    // Rune gloves
			new ShopItem(7461, 12),   // Dragon gloves
			new ShopItem(12791, 13),  // Rune pouch
			new ShopItem(13226, 9),   // Herb sack
			new ShopItem(5509, 3),    // Small pouch
			new ShopItem(5510, 5),    // Medium pouch
			new ShopItem(5512, 7),    // Large pouch
			new ShopItem(5514, 9),    // Giant pouch
			new ShopItem(26784, 12),  // Colossal pouch
			new ShopItem(13116, 18),  // Bonecrusher
			new ShopItem(6714, 6),    // Holy wrench
			new ShopItem(13639, 13),  // Seed box
			new ShopItem(19634, 15),  // Soul bearer
			new ShopItem(13379, 3),   // Shayzien helm (5)
			new ShopItem(13381, 3),   // Shayzien platebody (5)
			new ShopItem(13380, 3),   // Shayzien greaves (5)
			new ShopItem(13378, 3),   // Shayzien boots (5)
			new ShopItem(13377, 3),   // Shayzien gloves (5)
			new ShopItem(3839, 1),    // Damaged book
			new ShopItem(3841, 1),    // Damaged book
			new ShopItem(3843, 1),    // Damaged book
			new ShopItem(12607, 1),   // Damaged book
			new ShopItem(12609, 1),   // Damaged book
			new ShopItem(12611, 1)    // Damaged book
		}
	),
	// Mirrors the existing (separate) PvmPointStore item list/prices -- see
	// io.ruin.model.activities.newshop.shops.PvmPointStore, which already curates this exact
	// set for PVM Points. PVP_MODE accounts never earn or spend PVM Points, so this tab is
	// gated shut for them (see pvmOnly below).
	PVM_SHOP(
		16,
		Currency.PVM_POINTS,
		new ShopItem[]{
			// Line 1
			new ShopItem(59590, 100000), // Morveth platelegs
			new ShopItem(59589, 100000), // Morveth platebody
			new ShopItem(59588, 70000),  // Morveth fullhelm
			new ShopItem(26374, 70000),  // Zaryte crossbow
			new ShopItem(25739, 90000),  // Sanguine scythe of vitur
			new ShopItem(21295, 120000), // Infernal cape
			new ShopItem(28951, 100000), // Dizana's quiver
			new ShopItem(6199, 1000),    // Mystery box
			new ShopItem(32002, 50000),  // Summer mystery box
			new ShopItem(30570, 10000),  // Perk point scroll
			// Line 2
			new ShopItem(30460, 1000),   // Double exp scroll (30 min)
			new ShopItem(30459, 2000),   // Double drops scroll (30 min)
			new ShopItem(30458, 1000),   // Slayer task skip scroll
			new ShopItem(607, 400),      // Reroll dailies scroll
			new ShopItem(30457, 1200),   // Damage reduction scroll (30 min)
			new ShopItem(30456, 1200),   // Damage boost scroll (30 min)
			new ShopItem(30455, 800),    // Brew immunity scroll (30 min)
			new ShopItem(30453, 400),    // Prayer drain reduction scroll
			new ShopItem(30463, 1000),   // Scroll of revival
			new ShopItem(608, 3000),     // 5% drop rate scroll (1 hour)
			// Line 3
			new ShopItem(22092, 10000),  // Pet bonus token (24 hours)
			new ShopItem(29489, 25000),  // Rainbow cape
			new ShopItem(29507, 20000),  // Rainbow crown shirt
			new ShopItem(989, 50),       // Crystal key
			new ShopItem(23083, 70),     // Brimstone key
			new ShopItem(23951, 1500),    // Enhanced crystal key
			new ShopItem(59960, 10000),  // Z Golden key
			new ShopItem(59975, 50000),  // Ragnarok pet
			new ShopItem(59976, 75000)   // Toxic pet
		},
		true
	),
	// Sell-only -- players sell Antique emblems here for BH Points. No buy grid; the price on
	// each ShopItem below IS the buyback price (paid at 100%, not the usual 70% resale cut --
	// see sellAtListedPrice).
	SELL_BH_EMBLEMS(
		17,
		Currency.BH_POINTS,
		new ShopItem[]{
			new ShopItem(24565, 50),    // Antique emblem (tier 1)
			new ShopItem(24567, 100),   // Antique emblem (tier 2)
			new ShopItem(24569, 200),   // Antique emblem (tier 3)
			new ShopItem(24571, 400),   // Antique emblem (tier 4)
			new ShopItem(24573, 800),   // Antique emblem (tier 5)
			new ShopItem(24575, 1500),  // Antique emblem (tier 6)
			new ShopItem(24577, 2400),  // Antique emblem (tier 7)
			new ShopItem(24579, 3500),  // Antique emblem (tier 8)
			new ShopItem(24581, 5000),  // Antique emblem (tier 9)
			new ShopItem(24583, 6200)   // Antique emblem (tier 10)
		},
		false,
		true
	);

	private ShopItem[] shopItems;
	private Currency currency;
	private int shopId;
	@Getter
	private boolean pvmOnly;
	// When true, selling an item pays out its full listed price instead of the usual 70% --
	// for purpose-built sell-only shops (e.g. SELL_BH_EMBLEMS) where the listed price already
	// IS the buyback price, not a "buy" price to discount off of.
	@Getter
	private boolean sellAtListedPrice;
	@Getter
	private List<Player> playersInShop;

	CustomShop2(final int SHOP_ID, final Currency currency, ShopItem[] shopItems) {
		this(SHOP_ID, currency, shopItems, false, false);
	}

	CustomShop2(final int SHOP_ID, final Currency currency, ShopItem[] shopItems, boolean pvmOnly) {
		this(SHOP_ID, currency, shopItems, pvmOnly, false);
	}

	CustomShop2(final int SHOP_ID, final Currency currency, ShopItem[] shopItems, boolean pvmOnly, boolean sellAtListedPrice) {
		this.shopId = SHOP_ID;
		this.currency = currency;
		this.shopItems = shopItems;
		this.pvmOnly = pvmOnly;
		this.sellAtListedPrice = sellAtListedPrice;
		playersInShop = new ArrayList<>();
	}

	public static Item[] getItemsFromShop(Player player) {
		int shopId = player.getShopIdentifier();
		if (shopId < 0) {
			player.sendMessage("Something is wrong with this shop. Please contact a staff member.");
			return new Item[0];
		}
		CustomShop2 shop = Arrays.stream(CustomShop2.values())
			.filter(s -> s.shopId == shopId)
			.findFirst()
			.orElse(null);

		if (shop != null) {
			return toItemArray(shop.shopItems);
		}
		return new Item[0];
	}

	public ShopItem[] getShopItems() {
		return shopItems;
	}

	private static Item[] toItemArray(ShopItem[] shopItems) {
		Item[] items = new Item[shopItems.length];
		for (int index = 0; index < shopItems.length; index++) {
			ShopItem shopItem = shopItems[index];
			if (shopItem != null) {
				items[index] = new Item(shopItem.getItemId(), shopItem.getQuantity());
			}
		}
		return items;
	}

	public Item[] getItems() {
		Item[] items = new Item[shopItems.length];
		for (int index = 0; index < shopItems.length; index++) {
			ShopItem shopItem = shopItems[index];
			if (shopItem != null) {
				items[index] = new Item(shopItem.getItemId(), shopItem.getQuantity());
			}
		}
		return items;
	}

	public static CustomShop2 get(int shopId) {
		return Arrays.stream(CustomShop2.values())
			.filter(s -> s.shopId == shopId)
			.findFirst()
			.orElse(null);
	}

	public void addPlayerToShop(Player player) {
		if (!playersInShop.contains(player)) {
			playersInShop.add(player);
		}
	}

	public void removePlayerFromShop(Player player) {
		playersInShop.remove(player);
	}

	public Currency getCurrency() {
		return currency;
	}

	public ShopItem getShopItem(int itemId) {
		return Arrays.stream(shopItems)
			.filter(i -> i.getItemId() == itemId)
			.findFirst()
			.orElse(null);
	}

	public void refreshShop() {
		List<Player> playersToRemove = new ArrayList<>();
		Item[] items = getItems();
		boolean[] updatedSlots = new boolean[items.length];
		Arrays.fill(updatedSlots, true);
		for (Player player : playersInShop) {
			if (player != null) {
				if (!player.hasInterfaceOpen(Interface.CUSTOM_SHOP2, ToplevelComponent.MAINMODAL)) {
					playersToRemove.add(player);
					continue;
				}
				// Container updates sent outside the open()/configureBuyGrid() flow (e.g. after a
				// buy/sell here, same as a search) silently don't repaint unless the widget's grid
				// binding is reasserted first.
				CustomShopInterface2.configureBuyGrid(player);
				player.getPacketSender().updateItems(-1, 10005, items, updatedSlots, items.length);
				player.setShopIdentifier(player.getShopIdentifier());
				player.getPacketSender().sendClientScript(917, "ii", -1, -1);
			}
		}
		playersToRemove.stream().filter(Objects::nonNull).forEach(this::removePlayerFromShop);
	}

	public static void openCustomShopViaCmd(Player player) {
		player.getPacketSender().sendString(891, 14, "Melee Store");
		CustomShopInterface2.open(player, CustomShop2.get(1).getItems());
		CustomShopInterface2.handleEnteringShop(player, CustomShop2.MELEE_STORE);
		player.setShopIdentifier(1);
	}


	public static void openIronmanShop(Player player) {
		player.getPacketSender().sendString(891, 14, "Ironman Store");
		CustomShopInterface2.open(player, CustomShop2.get(12).getItems());
		CustomShopInterface2.handleEnteringShop(player, CustomShop2.IRONMAN_SHOP);
		player.setShopIdentifier(12);
	}

	public static void openSellBhEmblemsShop(Player player) {
		player.getPacketSender().sendString(891, 14, "Sell Bounty Hunter Emblems");
		CustomShopInterface2.open(player, CustomShop2.get(17).getItems());
		CustomShopInterface2.handleEnteringShop(player, CustomShop2.SELL_BH_EMBLEMS);
		player.setShopIdentifier(17);
	}

	// Direct openers for the interface-891 tabs, used to replace the old newshop-framework/
	// ShopManager entry points that used to open equivalent standalone shops.
	public static void openPkpShop(Player player) {
		player.getPacketSender().sendString(891, 14, "PKP Shop");
		CustomShopInterface2.open(player, CustomShop2.get(13).getItems());
		CustomShopInterface2.handleEnteringShop(player, CustomShop2.PKP_SHOP);
		player.setShopIdentifier(13);
	}

	public static void openBhPointsShop(Player player) {
		player.getPacketSender().sendString(891, 14, "Bounty Hunter Shop");
		CustomShopInterface2.open(player, CustomShop2.get(6).getItems());
		CustomShopInterface2.handleEnteringShop(player, CustomShop2.BLOOD_MONEY_STORE);
		player.setShopIdentifier(6);
	}

	public static void openZelusPointsShop(Player player) {
		player.getPacketSender().sendString(891, 14, "Zelus Points Shop");
		CustomShopInterface2.open(player, CustomShop2.get(14).getItems());
		CustomShopInterface2.handleEnteringShop(player, CustomShop2.ZELUS_POINTS_SHOP);
		player.setShopIdentifier(14);
	}

	public static void openAchievementPointsShop(Player player) {
		player.getPacketSender().sendString(891, 14, "Achievement Points Shop");
		CustomShopInterface2.open(player, CustomShop2.get(15).getItems());
		CustomShopInterface2.handleEnteringShop(player, CustomShop2.ACHIEVEMENT_POINTS_SHOP);
		player.setShopIdentifier(15);
	}

	public static void openDonatorStoreShop(Player player) {
		player.getPacketSender().sendString(891, 14, "Donation Shop");
		CustomShopInterface2.open(player, CustomShop2.get(9).getItems());
		CustomShopInterface2.handleEnteringShop(player, CustomShop2.DONATOR_STORE);
		player.setShopIdentifier(9);
	}

	public static void openVoteStoreShop(Player player) {
		player.getPacketSender().sendString(891, 14, "Voting Shop");
		CustomShopInterface2.open(player, CustomShop2.get(10).getItems());
		CustomShopInterface2.handleEnteringShop(player, CustomShop2.VOTE_STORE);
		player.setShopIdentifier(10);
	}

	public static void openPvmShop(Player player) {
		if (!player.isPvmMode()) {
			player.sendMessage("The PVM Point Shop is exclusive to PVM Mode accounts.");
			return;
		}
		player.getPacketSender().sendString(891, 14, "PVM Point Shop");
		CustomShopInterface2.open(player, CustomShop2.get(16).getItems());
		CustomShopInterface2.handleEnteringShop(player, CustomShop2.PVM_SHOP);
		player.setShopIdentifier(16);
	}

//    public static void register() {
//        NPCAction.register(2108, 1, (player, npc) -> {
//            player.getPacketSender().sendString(891, 14, "Vote Store");
//            CustomShopInterface2.open(player, CustomShop2.get(10).getItems());
//            CustomShopInterface2.handleEnteringShop(player, CustomShop2.VOTE_STORE);
//            player.setShopIdentifier(10);
//        });
	//}
}

package io.ruin.model.activities.raids.tfa;

import io.ruin.cache.NPCType;
import io.ruin.data.impl.npcs.npc_combat;
import io.ruin.model.combat.AttackStyle;
import lombok.extern.slf4j.Slf4j;

import static io.ruin.model.activities.raids.tfa.Raid4Ids.*;

/**
 * Combat info for the raid's NPCs, set in code like the Inferno's. Levels are the rev 241 cache values, which
 * match the wiki; bonuses are the wiki's. Hitpoints here are the 2-player base: rooms rescale them on spawn.
 */
@Slf4j
final class Raid4Npcs {

	private Raid4Npcs() {
	}

	static void register() {
		npc_combat.Info guardian = guardianInfo(550);
		set(NPC_GUARDIAN, guardian);
		set(NPC_GUARDIAN_UNSHIELDED, guardianInfo(1100));
		// It floats: flight clipping lets the 5x5 drift over the arena's base ring instead of snagging on it.
		// Read when the npc's route finder is built, so it has to be set before any spawn.
		for (int id : new int[] { NPC_GUARDIAN, NPC_GUARDIAN_UNSHIELDED }) {
			NPCType def = NPCType.get(id);
			if (def != null)
				def.flightClipping = true;
		}

		npc_combat.Info battery = new npc_combat.Info();
		battery.name = "Inert Guardian";
		battery.hitpoints = 180;
		battery.attack = 1;
		battery.strength = 1;
		battery.defence = 50;
		battery.magic = 50;
		battery.ranged = 1;
		battery.attack_ticks = 100;
		battery.death_ticks = 2;
		battery.respawn_ticks = Integer.MAX_VALUE;
		battery.defend_animation = -1;
		battery.death_animation = SEQ_BATTERY_DESPAWN;
		battery.spawn_animation = -1;
		battery.poison_immunity = true;
		battery.venom_immunity = true;
		set(NPC_BATTERY_ACTIVE, battery);

		npc_combat.Info ezekiel = new npc_combat.Info();
		ezekiel.name = "Ezekiel";
		ezekiel.hitpoints = 3000;
		ezekiel.attack = 250;
		ezekiel.strength = 150;
		ezekiel.defence = 130;
		ezekiel.magic = 200;
		ezekiel.ranged = 80;
		ezekiel.stab_defence = 150;
		ezekiel.slash_defence = 90;
		ezekiel.crush_defence = 35;
		ezekiel.magic_defence = 200;
		ezekiel.ranged_defence = 370; // standard; light +445, heavy +320
		ezekiel.max_damage = 35;
		ezekiel.attack_style = AttackStyle.SLASH;
		ezekiel.attack_ticks = 5;
		ezekiel.death_ticks = 11; // K..K+11 (ezekiel_plan §9)
		ezekiel.respawn_ticks = Integer.MAX_VALUE;
		ezekiel.attack_animation = SEQ_EZEKIEL_MELEE;
		ezekiel.defend_animation = -1;
		ezekiel.death_animation = 14697; // npc_ezekiel_finale_death01 (he always dies in the finale)
		ezekiel.spawn_animation = -1;
		ezekiel.poison_immunity = true;
		ezekiel.venom_immunity = true;
		ezekiel.aggressive_level = 0;
		set(NPC_EZEKIEL, ezekiel);

		// Cache stats: hp 80 (the charge bar), defence 115, magic 90.
		npc_combat.Info sword = new npc_combat.Info();
		sword.name = "Animated Sword";
		sword.hitpoints = 80;
		sword.attack = 1;
		sword.strength = 1;
		sword.defence = 115;
		sword.magic = 90;
		sword.ranged = 1;
		sword.attack_ticks = 100;
		sword.death_ticks = 1;
		sword.respawn_ticks = Integer.MAX_VALUE;
		sword.defend_animation = -1;
		sword.death_animation = -1;
		sword.spawn_animation = -1;
		sword.poison_immunity = true;
		sword.venom_immunity = true;
		set(NPC_EZEKIEL_SWORD, sword);

		// Bork (wiki infobox): hp 1500, att/str 200, def 300, mag 90, rng 200; crush, 7 ticks; +100 att, +40 str.
		npc_combat.Info bork = new npc_combat.Info();
		bork.name = "Bork";
		bork.hitpoints = 1500;
		bork.attack = 200;
		bork.strength = 200;
		bork.defence = 300;
		bork.magic = 90;
		bork.ranged = 200;
		bork.crush_attack = 100;
		bork.stab_defence = 140;
		bork.slash_defence = 130;
		bork.crush_defence = 10;
		bork.magic_defence = 300;
		bork.ranged_defence = 50; // standard; light +80, heavy +5
		bork.max_damage = 32;
		bork.attack_style = AttackStyle.CRUSH;
		bork.attack_ticks = 7;
		bork.death_ticks = 7; // +7 deleted (bork_plan §6)
		bork.respawn_ticks = Integer.MAX_VALUE;
		bork.attack_animation = SEQ_BORK_GONG_MELEE;
		bork.defend_animation = -1;
		bork.death_animation = SEQ_BORK_DEATH;
		bork.spawn_animation = -1;
		bork.poison_immunity = true;
		bork.venom_immunity = true;
		bork.aggressive_level = 0;
		set(NPC_BORK, bork);

		// The Smorks (wiki infobox): hp 600, def 220, mag/rng 185; +185 magic, +250 ranged; 7 ticks.
		npc_combat.Info smorks = new npc_combat.Info();
		smorks.name = "The Smorks";
		smorks.hitpoints = 600;
		smorks.attack = 1;
		smorks.strength = 1;
		smorks.defence = 220;
		smorks.magic = 185;
		smorks.ranged = 185;
		smorks.magic_attack = 185;
		smorks.ranged_attack = 250;
		smorks.stab_defence = 220;
		smorks.slash_defence = 170;
		smorks.crush_defence = 160;
		smorks.magic_defence = 400;
		smorks.ranged_defence = 150; // standard; light +80, heavy +80
		smorks.max_damage = 20;
		smorks.attack_style = AttackStyle.RANGED;
		smorks.attack_ticks = 7;
		smorks.death_ticks = 9; // D+9 the cart deleted (bork_plan §11)
		smorks.respawn_ticks = Integer.MAX_VALUE;
		smorks.attack_animation = -1;
		smorks.defend_animation = -1;
		smorks.death_animation = -1;
		smorks.spawn_animation = -1;
		smorks.poison_immunity = true;
		smorks.venom_immunity = true;
		smorks.aggressive_level = 0;
		set(NPC_SMORKS, smorks);

		// Moia: the cache's hp is 1900 (the wiki infobox says 785); def 195, mag 350, rng 200; air weakness 80%.
		npc_combat.Info moia = puppet("Moia", 1900, 1, 1, 195, 350, 200, SEQ_MOIA_DEATH, 6); // K+6 deleted (moia_plan §9)
		moia.stab_defence = 20;
		moia.slash_defence = 110;
		moia.crush_defence = 15;
		moia.magic_defence = 30;
		moia.ranged_defence = 120; // standard; light +60, heavy +140
		set(NPC_MOIA, moia);
		// Undead binder, cache stats: hp 95, def 225, str 170, mag 50.
		set(NPC_MOIA_BINDER, puppet("Undead binder", 95, 1, 170, 225, 50, 1, SEQ_BINDER_DEATH, 5)); // deleted 5 later

		// Dreadnoth (wiki infobox): hp 2100, att 280, str 285, def 125, mag 220, rng 250; fire weakness 85%.
		npc_combat.Info dread = puppet("Dreadnoth the Jellied", 2100, 280, 285, 125, 220, 250, SEQ_DREADNOTH_DEATH, 4); // K+4 deleted
		dread.crush_attack = 185;
		dread.ranged_attack = 180;
		dread.stab_defence = 15;
		dread.slash_defence = 20;
		dread.crush_defence = 5;
		dread.magic_defence = 20;
		dread.ranged_defence = 80; // standard; light +100, heavy +80
		set(NPC_DREADNOTH, dread);
		// Jelly, cache stats: hp 75, att 200, str 160, def 100, mag 100.
		set(NPC_DREADNOTH_JELLY, puppet("Jelly", 75, 200, 160, 100, 100, 1, 1587, 3)); // jelly_death, deleted +3

		// The Dagon'hai (cache + wiki infobox): hp 640 shared shield, def 100, mag 290, rng 260; the weak forms 7 hp.
		int[][] dagonhai = { { NPC_DAGONHAI_ELDER, NPC_DAGONHAI_ELDER_WEAK }, { NPC_DAGONHAI_MONK_1, NPC_DAGONHAI_MONK_1_WEAK },
				{ NPC_DAGONHAI_MONK_2, NPC_DAGONHAI_MONK_2_WEAK } };
		for (int i = 0; i < 3; i++) {
			String name = i == 0 ? "Dagon'hai Elder" : "Dagon'hai Monk";
			for (int form = 0; form < 2; form++) {
				npc_combat.Info info = puppet(name, form == 0 ? 640 : 7, 1, 1, 100, 290, 260, SEQ_DAGONHAI_DEATH[i], 3);
				info.stab_defence = info.slash_defence = info.crush_defence = 50;
				info.magic_defence = 100;
				info.ranged_defence = 80;
				set(dagonhai[i][form], info);
			}
		}
		set(NPC_DAGONHAI_CAULDRON, puppet("Cauldron", 1000, 1, 1, 1, 1, 1, -1, 1));
		set(NPC_DAGONHAI_IMP, puppet("Imp", 44, 250, 195, 50, 125, 66, -1, 2)); // cache stats
		set(NPC_DAGONHAI_RIFT, puppet("Demonic rift", 20, 1, 1, 1, 1, 1, -1, 1));

		// Xarth (wiki infobox): hp 2600, att 250, str 225, def 170, mag 350, rng 220; water weakness 85%.
		npc_combat.Info xarth = puppet("Xarth", 2600, 250, 225, 170, 350, 220, SEQ_XARTH_DEATH, 6); // K+6 line + deleted
		xarth.stab_defence = 115;
		xarth.slash_defence = 105;
		xarth.crush_defence = 20;
		xarth.magic_defence = 205;
		xarth.ranged_defence = 230; // standard; light +125, heavy +155
		set(NPC_XARTH, xarth);
		set(NPC_XARTH_PILLAR, puppet("Pillar", 1000, 1, 1, 1, 1, 1, -1, 1));
		// Corpse, cache stats: hp 62, att 50, str 40, def 100, mag 100.
		set(NPC_XARTH_CORPSE, puppet("Corpse", 62, 50, 40, 100, 100, 1, SEQ_CORPSE_DEATH, 4)); // deleted +4
		set(NPC_XARTH_CORPSE_DOUSED, puppet("Corpse", 62, 50, 40, 100, 100, 1, SEQ_CORPSE_DEATH, 2));

		// Ocellus, cache + wiki infobox: P1 hp 2500 (shield), def 215, mag 350, rng 250; P3 att 325, str 250, def 250,
		// hp 5250, mag/rng 300. The Blade (P2 hitbox) hp 1000, def 250, mag 220.
		npc_combat.Info p1 = puppet("Ocellus", 2500, 1, 1, 215, 350, 250, -1, 2);
		p1.stab_defence = 150;
		p1.slash_defence = 95;
		p1.crush_defence = 60;
		p1.magic_defence = 100;
		p1.ranged_defence = 135; // standard; light +50, heavy +50
		set(NPC_OCELLUS_P1, p1);
		set(16672, p1); // tfa_ocellus_intro_not_met ("Hooded Figure"), the room's add
		set(16673, p1); // tfa_ocellus_intro_met
		npc_combat.Info p2 = puppet("Ocellus", 5250, 325, 250, 250, 300, 300, -1, 2);
		set(NPC_OCELLUS_P2, p2);
		npc_combat.Info blade = puppet("The Blade", 1000, 1, 1, 250, 220, 1, -1, 1);
		blade.stab_defence = 150;
		blade.slash_defence = 120;
		blade.crush_defence = 10;
		blade.magic_defence = 100;
		blade.ranged_defence = 110;
		set(NPC_OCELLUS_BLADE, blade);
		npc_combat.Info p3 = puppet("Ocellus", 5250, 325, 250, 250, 300, 300, SEQ_OCELLUS_DEATH, 16); // K+16 deleted (ocellus_plan §6)
		p3.stab_defence = 150;
		p3.slash_defence = 100;
		p3.crush_defence = 30;
		p3.magic_defence = 200;
		p3.ranged_defence = 250;
		set(NPC_OCELLUS_P3, p3);
		// Void creatures, cache stats: hp 44, def 175, mag 300
		for (int[] v : new int[][] { { NPC_VOID_RIFT, 0 }, { NPC_VOID_SIPHON, 1 }, { NPC_VOID_AUGMENTER, 2 } }) {
			String name = v[1] == 0 ? "Void Rift" : v[1] == 1 ? "Void Siphon" : "Void Augmenter";
			npc_combat.Info info = puppet(name, 44, 1, 1, 175, 300, 1, -1, 2);
			info.magic_defence = v[1] == 2 ? 300 : 50;
			set(v[0], info);
		}
		set(NPC_VOID_VINE, puppet("Void Vine", 44, 1, 1, 1, 1, 1, -1, 1));
	}

	/** Combat info for a room-driven npc ({@code Raid4Puppet}): stats only, no auto attack. */
	static npc_combat.Info puppet(String name, int hp, int att, int str, int def, int mag, int rng, int deathAnim, int deathTicks) {
		npc_combat.Info info = new npc_combat.Info();
		info.name = name;
		info.hitpoints = hp;
		info.attack = att;
		info.strength = str;
		info.defence = def;
		info.magic = mag;
		info.ranged = rng;
		info.attack_ticks = 4;
		info.death_ticks = deathTicks;
		info.respawn_ticks = Integer.MAX_VALUE;
		info.attack_animation = -1;
		info.defend_animation = -1;
		info.death_animation = deathAnim;
		info.spawn_animation = -1;
		info.poison_immunity = true;
		info.venom_immunity = true;
		info.aggressive_level = 0;
		return info;
	}

	private static npc_combat.Info guardianInfo(int hitpoints) {
		npc_combat.Info info = new npc_combat.Info();
		info.name = "Archive Guardian";
		info.hitpoints = hitpoints;
		info.attack = 250;
		info.strength = 240;
		info.defence = 260;
		info.magic = 250;
		info.ranged = 280;
		// The wiki lists +350 and +55 aggressive bonuses without saying which; read here as accuracy / strength.
		info.crush_attack = 350;
		info.ranged_attack = 350;
		info.stab_defence = 120;
		info.slash_defence = 110;
		info.crush_defence = 20;
		info.magic_defence = 130;
		info.ranged_defence = 120; // standard; light is +75, heavy +50
		info.max_damage = 25;
		info.attack_style = AttackStyle.CRUSH;
		info.attack_ticks = 5;
		info.death_ticks = 6;
		info.respawn_ticks = Integer.MAX_VALUE;
		info.attack_animation = SEQ_GUARDIAN_MELEE;
		info.defend_animation = -1;
		info.death_animation = SEQ_GUARDIAN_DEATH;
		info.spawn_animation = -1;
		info.poison_immunity = true;
		info.venom_immunity = true;
		info.aggressive_level = 0;
		return info;
	}

	private static void set(int npcId, npc_combat.Info info) {
		NPCType def = NPCType.get(npcId);
		if (def == null) {
			log.error("Raid 4: npc {} missing from the cache", npcId);
			return;
		}
		info.ids = new int[] { npcId };
		def.combatInfo = info;
	}
}

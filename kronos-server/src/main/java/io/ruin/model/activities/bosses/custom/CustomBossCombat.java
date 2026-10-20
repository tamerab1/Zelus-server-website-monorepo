package io.ruin.model.activities.bosses.custom;

import io.ruin.api.utils.Random;
import io.ruin.model.World;
import io.ruin.model.combat.AttackStyle;
import io.ruin.model.combat.Hit;
import io.ruin.model.entity.Entity;
import io.ruin.model.entity.npc.NPCCombat;
import io.ruin.model.entity.player.Player;
import io.ruin.model.map.Position;
import io.ruin.model.map.Projectile;

import java.util.Map;

/**
 * Shared combat for the custom bosses 16200-16214 (2026-10-19, models from "new customs/bosses"; cache side
 * .dev/cache-restore-tool/custom_bosses.txt + boss_rigs/). Melee styles hit from one tile; ranged, magic and dragonfire
 * styles fire a projectile. Stats and animations are in npcs/combat/Custom_bosses.json.
 * <p>
 * Each boss's {@link Tune} lines its attack up with its animation: the projectile leaves at the animation's release
 * frame (delay, in client cycles of 20 ms) from the height of the hand / staff / jaws that casts it, with its own
 * flight speed, arc and impact effect, and heavy melee swings land their hit on the impact frame instead of at the
 * wind-up.
 * <p>
 * The Hulk fights three ways: a two-handed sweeping strike up close, a hurled boulder from up to
 * {@link #HULK_THROW_RANGE} tiles (and now and then up close), and every {@link #HULK_SMASH_EVERY}th attack its special,
 * the two-fisted Ground Smash -- a heavier hit that cracks the ground around the target and catches anyone next to them.
 */
public class CustomBossCombat extends NPCCombat {

	/**
	 * @param range     attack distance for projectile styles (melee is always 1)
	 * @param gfx       projectile spotanim (-1: by attack style)
	 * @param height    projectile start height
	 * @param release   client cycles into the attack animation before the projectile leaves
	 * @param hitDelay  extra ticks before a melee hit lands
	 * @param flight    projectile client cycles per tile (lower = faster)
	 * @param curve     projectile arc
	 * @param fromFront true: the projectile starts at the boss's front edge; false: from its centre (a caster whose
	 *                  mouth / hand is over its own tiles -- starting a tile ahead made it pop out of mid-air)
	 * @param impact    spotanim on the target when the hit lands (-1 none), at impactHeight
	 */
	private record Tune(int range, int gfx, int height, int release, int hitDelay, int flight, int curve, boolean fromFront,
			int impact, int impactHeight) {
		Tune(int range, int gfx, int height, int release, int hitDelay) {
			this(range, gfx, height, release, hitDelay, 10, 15, true, -1, 0);
		}
	}

	private static final int MAGIC_GFX = 2781, RANGED_GFX = 2881, DRAGONFIRE_GFX = 54;
	private static final int HULK = 16212, EARTHQUAKE_GFX = 1028;
	private static final int HULK_SMASH_ANIM = 16250, HULK_THROW_ANIM = 16251, HULK_THROW_RANGE = 6, HULK_SMASH_EVERY = 4;
	private static final int BOULDER_HIT_GFX = 305;
	/** The Gorilla boulder (856), heaved from shoulder height in a high arc, leaving the hands at the throw's release. */
	private static final Projectile BOULDER = new Projectile(856, 90, 31, 24, 54, 8, 28, 64);
	private static final Tune DEFAULT = new Tune(8, -1, 65, 0, 0);
	private static final Map<Integer, Tune> TUNES = Map.ofEntries(
			Map.entry(16200, new Tune(8, MAGIC_GFX, 60, 22, 0)),   // Astraeus: winged godsword strike, bolt at the swing
			Map.entry(16203, new Tune(8, MAGIC_GFX, 38, 24, 0)),   // Malik: from the floating gauntlet's punch
			Map.entry(16204, new Tune(8, MAGIC_GFX, 50, 18, 0)),   // Malakor: soul bolt as the wings snap forward
			Map.entry(16205, new Tune(1, -1, 0, 0, 1)),            // Volcanus: leaping strike lands on the impact
			Map.entry(16206, new Tune(1, -1, 0, 0, 0)),            // Drak'thor: aerial dive-bite (melee)
			Map.entry(16207, new Tune(1, -1, 0, 0, 1)),            // Gorgar: two-handed slam
			Map.entry(16208, new Tune(8, RANGED_GFX, 55, 18, 0)),  // Sylphira: arrow on the bow release
			// Vaelok: Vorkath's dragonflame (393), fast and flat, bursting into flames (157) on the target
			Map.entry(16210, new Tune(7, 393, 47, 18, 0, 6, 10, true, 157, 92)),
			Map.entry(16211, new Tune(1, -1, 0, 0, 1)),            // Kharaz: pincers snap shut
			Map.entry(HULK, new Tune(1, -1, 0, 0, 0)),             // Hulk: see hulkAttack
			Map.entry(16213, new Tune(1, -1, 0, 0, 1)),            // Terrax: two-hit strike
			// Nidhogg: Infernal Fire Blast (Fire Surge 1465 / 1466) from its jaws over its own tiles, arcing, on the strike
			Map.entry(16214, new Tune(4, 1465, 88, 22, 0, 8, 16, false, 1466, 124)),
			// Round 2 (2026-10-20, 16217-16225): the melee ones need no tune
			Map.entry(16218, new Tune(8, 1459, 60, 18, 0, 10, 15, true, 1460, 92)), // Kaelen: Water Surge from the staff thrust
			Map.entry(16220, new Tune(8, MAGIC_GFX, 55, 16, 0)),                    // Zephyros: bolt off the cast hand
			Map.entry(16223, new Tune(7, 1044, 70, 20, 0, 10, 24, true, -1, 0))     // Malakor: toxic flask lobbed at the toss
	);

	private Tune tune;
	private int hulkAttacks;

	@Override
	public void init() {
		tune = TUNES.getOrDefault(npc.getId(), DEFAULT);
	}

	@Override
	public void follow() {
		follow(info.attack_style.isMelee() ? 1 : tune.range());
	}

	@Override
	public boolean attack() {
		if (npc.getId() == HULK)
			return hulkAttack();
		AttackStyle style = info.attack_style;
		if (style.isMelee()) {
			if (!withinDistance(1))
				return false;
			if (tune.hitDelay() == 0) {
				basicAttack(info.attack_animation, style, info.max_damage);
				return true;
			}
			npc.animate(info.attack_animation);
			faceTarget();
			target.hit(new Hit(npc, style, null).randDamage(info.max_damage).delay(tune.hitDelay()));
			return true;
		}
		if (!withinDistance(tune.range()))
			return false;
		int gfx = tune.gfx() != -1 ? tune.gfx() : style.isDragonfire() ? DRAGONFIRE_GFX : style.isRanged() ? RANGED_GFX : MAGIC_GFX;
		Projectile projectile = new Projectile(gfx, tune.height(), 31, tune.release(), tune.release() + 2 * tune.flight(),
				tune.flight(), tune.curve(), 64);
		if (tune.fromFront())
			projectile.tileOffset(npc.getSize() / 2);
		npc.animate(info.attack_animation);
		faceTarget();
		Entity victim = target;
		int arrives = projectile.send(npc, victim);
		int delay = Math.max(1, (arrives + 15) / 30);
		Hit hit = new Hit(npc, style, null).randDamage(info.max_damage).delay(delay);
		if (tune.impact() != -1)
			hit.afterPostDamage(h -> victim.graphics(tune.impact(), tune.impactHeight(), 0));
		victim.hit(hit);
		return true;
	}

	/** Sweep up close, boulders at range (and sometimes up close), the Ground Smash special every few attacks. */
	private boolean hulkAttack() {
		boolean adjacent = withinDistance(1);
		if (!adjacent && !withinDistance(HULK_THROW_RANGE))
			return false;
		hulkAttacks++;
		if (adjacent && hulkAttacks % HULK_SMASH_EVERY == 0) {
			groundSmash();
			return true;
		}
		if (!adjacent || Random.rollDie(4)) {
			throwBoulder();
			return true;
		}
		basicAttack(info.attack_animation, AttackStyle.CRUSH, info.max_damage);
		return true;
	}

	private void throwBoulder() {
		npc.animate(HULK_THROW_ANIM);
		faceTarget();
		Entity victim = target;
		int arrives = BOULDER.send(npc, victim);
		Hit hit = new Hit(npc, AttackStyle.RANGED, null).randDamage(info.max_damage).delay(Math.max(1, (arrives + 15) / 30));
		hit.afterPostDamage(h -> victim.graphics(BOULDER_HIT_GFX, 0, 0));
		victim.hit(hit);
	}

	/**
	 * The special: both fists come down a tick after the wind-up. A heavier hit on the target, half on anyone standing
	 * next to it, and an earthquake burst on and around the spot.
	 */
	private void groundSmash() {
		npc.animate(HULK_SMASH_ANIM);
		faceTarget();
		npc.forceText("HULK SMASH!");
		Entity victim = target;
		Position at = victim.getPosition().copy();
		victim.hit(new Hit(npc, AttackStyle.CRUSH, null).randDamage(info.max_damage * 13 / 10).delay(1));
		for (Player p : npc.localPlayers())
			if (p != victim && !p.getCombat().isDead() && p.getPosition().distance(at) <= 1)
				p.hit(new Hit(npc, AttackStyle.CRUSH, null).randDamage(info.max_damage / 2).delay(1));
		npc.addEvent(e -> {
			e.delay(1);
			World.sendGraphics(EARTHQUAKE_GFX, 0, 0, at);
			for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}})
				World.sendGraphics(EARTHQUAKE_GFX, 0, 10, at.relative(d[0], d[1]));
		});
	}

	@Override
	public void process() {
	}
}
